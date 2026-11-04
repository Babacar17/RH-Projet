package com.soutenance.bean;

import com.soutenance.entity.LogAction;
import com.soutenance.entity.User;
import com.soutenance.service.AdminService;
import com.soutenance.service.RgpdService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean JSF — Tab RGPD de la page audit.xhtml.
 *
 * Trois fonctionnalités :
 *   - Export JSON des données personnelles d'un utilisateur (droit d'accès)
 *   - Anonymisation d'un compte (droit à l'oubli)
 *   - Consultation du journal des accès aux données sensibles
 */
@Component("rgpdBean")
@Scope("view")
public class RgpdBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private RgpdService  rgpdService;
    @Autowired private AdminService adminService;
    @Autowired private SessionBean  sessionBean;

    private List<User>          utilisateursDisponibles = new ArrayList<>();
    private Long                userCibleId;
    private List<LogAction>     accesSensibles = new ArrayList<>();

    /** Charge les utilisateurs disponibles pour le picklist et les accès sensibles. */
    public void init() {
        utilisateursDisponibles = adminService.getTousLesUtilisateurs();
        accesSensibles          = rgpdService.findConsultationsDonneesSensibles(50);
    }

    public void rafraichir() { init(); }

    // ── Export RGPD ──────────────────────────────────────────────────────────

    /** Génère et télécharge le JSON des données personnelles d'un utilisateur. */
    public void exporterDonnees() {
        if (userCibleId == null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Sélection requise",
                "Choisissez d'abord un utilisateur dans la liste.");
            return;
        }
        try {
            String json = rgpdService.exporterDonneesPersonnelles(userCibleId, getOp());
            // Téléchargement
            FacesContext fc = FacesContext.getCurrentInstance();
            ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/json; charset=UTF-8");
            ec.setResponseHeader("Content-Disposition",
                "attachment; filename=\"export-rgpd-user-" + userCibleId + ".json\"");
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            ec.setResponseContentLength(data.length);
            ec.getResponseOutputStream().write(data);
            fc.responseComplete();
            init();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur téléchargement", ex.getMessage());
        }
    }

    // ── Anonymisation ────────────────────────────────────────────────────────

    public void anonymiser() {
        if (userCibleId == null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Sélection requise",
                "Choisissez d'abord un utilisateur dans la liste.");
            return;
        }
        try {
            String err = rgpdService.anonymiser(userCibleId, getOp());
            if (err != null) {
                addMsg(FacesMessage.SEVERITY_WARN, "Anonymisation refusée", err);
            } else {
                init();
                addMsg(FacesMessage.SEVERITY_INFO, "Anonymisation terminée",
                    "Les champs nominatifs ont été remplacés.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public List<User> getUtilisateursDisponibles() {
        if (utilisateursDisponibles == null || utilisateursDisponibles.isEmpty()) init();
        return utilisateursDisponibles;
    }
    public List<LogAction> getAccesSensibles() {
        if (accesSensibles == null || accesSensibles.isEmpty()) init();
        return accesSensibles;
    }
    public Long getUserCibleId()          { return userCibleId; }
    public void setUserCibleId(Long id)   { this.userCibleId = id; }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String msg) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) fc.addMessage(null, new FacesMessage(sev, titre, msg));
    }
}
