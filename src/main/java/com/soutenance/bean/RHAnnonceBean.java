package com.soutenance.bean;

import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.Role;
import com.soutenance.service.AdminService;
import com.soutenance.service.AnnonceService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean d'envoi d'annonces par le Responsable RH.
 *
 * Contrairement à AnnonceBean (super-admin), la portée est verrouillée à
 * l'institution du RH connecté : deux ciblages seulement, TOUS (son
 * institution) ou ROLE (dans son institution). Jamais de portée globale
 * ni de choix d'une autre institution.
 */
@Component("rhAnnonceBean")
@Scope("view")
public class RHAnnonceBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> ROLES_CIBLABLES =
        List.of("RH", "MANAGER", "EMPLOYE", "CANDIDAT");

    @Autowired private AnnonceService annonceService;
    @Autowired private AdminService   adminService;
    @Autowired private SessionBean    sessionBean;
    @Autowired(required = false) private LogActionDao logActionDao;

    private List<Role> rolesDisponibles = new ArrayList<>();
    private List<LogAction> historique = new ArrayList<>();

    // Formulaire
    private String cible    = "TOUS";  // TOUS / ROLE (toujours dans l'institution du RH)
    private String roleCible;
    private String titre;
    private String message;
    private String priorite = "NORMALE";
    private boolean avecEmail = false;

    @PostConstruct
    public void init() {
        rolesDisponibles = adminService.getTousLesRoles().stream()
            .filter(r -> ROLES_CIBLABLES.contains(r.getNom()))
            .toList();
        chargerHistorique();
    }

    public void chargerHistorique() {
        Long institutionId = sessionBean.getInstitutionId();
        if (logActionDao == null || institutionId == null) {
            historique = new ArrayList<>();
            return;
        }
        try {
            historique = logActionDao.findByActionAndInstitution("ANNONCE_DIFFUSION", institutionId);
        } catch (Exception e) {
            historique = new ArrayList<>();
        }
    }

    public boolean isCibleRole() { return "ROLE".equals(cible); }

    public void diffuser() {
        if (titre == null || titre.isBlank()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Champ requis",
                "Saisissez un titre pour l'annonce.");
            return;
        }
        Long institutionId = sessionBean.getInstitutionId();
        if (institutionId == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Aucune institution associée à votre compte.");
            return;
        }
        try {
            String role = isCibleRole() ? roleCible : null;
            int n = annonceService.diffuserAnnonceInstitution(
                institutionId, role, titre, message, priorite, avecEmail, getOp());

            if (n == 0) {
                addMsg(FacesMessage.SEVERITY_WARN, "Aucun destinataire",
                    "La cible choisie n'a aucun utilisateur actif dans votre institution.");
            } else {
                addMsg(FacesMessage.SEVERITY_INFO, "Annonce diffusée",
                    n + " utilisateur(s) ont reçu cette annonce.");
                titre = null;
                message = null;
                roleCible = null;
                chargerHistorique();
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String s, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, s, d));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public List<Role> getRolesDisponibles() { return rolesDisponibles; }
    public List<LogAction> getHistorique()  { return historique; }

    public String  getCible()               { return cible; }
    public void    setCible(String cible)   { this.cible = cible; }
    public String  getRoleCible()           { return roleCible; }
    public void    setRoleCible(String r)   { this.roleCible = r; }
    public String  getTitre()               { return titre; }
    public void    setTitre(String t)       { this.titre = t; }
    public String  getMessage()             { return message; }
    public void    setMessage(String m)     { this.message = m; }
    public String  getPriorite()            { return priorite; }
    public void    setPriorite(String p)    { this.priorite = p; }
    public boolean isAvecEmail()            { return avecEmail; }
    public void    setAvecEmail(boolean b)  { this.avecEmail = b; }
}
