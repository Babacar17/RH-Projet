package com.soutenance.bean;

import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.TacheOnboardingDao;
import com.soutenance.entity.Employe;
import com.soutenance.entity.TacheOnboarding;
import com.soutenance.service.OnboardingService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean Onboarding RH : vue d'ensemble des intégrations en cours +
 * checklist par employé.
 */
@Component("onboardingBean")
@Scope("view")
public class OnboardingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private OnboardingService    onboardingService;
    @Autowired private TacheOnboardingDao   onboardingDao;
    @Autowired private EmployeDao           employeDao;
    @Autowired private SessionBean          sessionBean;

    private List<Employe>           employesEnIntegration = new ArrayList<>();
    private Employe                 employeSelectionne;
    private List<TacheOnboarding>   tachesSelection = new ArrayList<>();
    private String                  commentaireBloque;

    @PostConstruct
    public void init() { rafraichir(); }

    public void rafraichir() {
        try {
            Long instId = sessionBean.getInstitutionId();
            // Liste des employés ayant au moins une tâche d'onboarding non terminée
            List<TacheOnboarding> tachesEnCours = onboardingDao.findEnCoursByInstitution(instId);
            // Déduplication par employé
            java.util.Map<Long, Employe> map = new java.util.LinkedHashMap<>();
            for (TacheOnboarding t : tachesEnCours) {
                if (t.getEmploye() != null) map.put(t.getEmploye().getId(), t.getEmploye());
            }
            employesEnIntegration = new ArrayList<>(map.values());
        } catch (Exception e) {
            employesEnIntegration = new ArrayList<>();
        }
    }

    public void ouvrirChecklist(Employe e) {
        this.employeSelectionne = e;
        this.tachesSelection = onboardingService.getTachesByEmploye(e.getId());
    }

    public void marquerFait(Long tacheId) {
        try {
            onboardingService.marquerFait(tacheId, getOp());
            if (employeSelectionne != null) {
                tachesSelection = onboardingService.getTachesByEmploye(employeSelectionne.getId());
            }
            rafraichir();
            addMsg(FacesMessage.SEVERITY_INFO, "Tâche terminée", "Marquée comme faite.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void marquerBloque(Long tacheId) {
        try {
            onboardingService.marquerBloque(tacheId,
                commentaireBloque != null ? commentaireBloque : "Bloqué", getOp());
            commentaireBloque = null;
            if (employeSelectionne != null) {
                tachesSelection = onboardingService.getTachesByEmploye(employeSelectionne.getId());
            }
            addMsg(FacesMessage.SEVERITY_WARN, "Tâche bloquée", "Statut mis à jour.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Force la création d'une checklist pour un employé (utile si embauche manuelle). */
    public void creerChecklistPour(Employe e) {
        try {
            int n = onboardingService.creerChecklistStandard(e.getId(), getOp());
            rafraichir();
            if (n > 0) {
                addMsg(FacesMessage.SEVERITY_INFO, "Checklist créée",
                    n + " tâches d'intégration ajoutées.");
            } else {
                addMsg(FacesMessage.SEVERITY_WARN, "Existante",
                    "Une checklist existe déjà pour cet employé.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public int getProgression(Long employeId) {
        return onboardingService.getProgressionPourcent(employeId);
    }

    public String getCouleurStatut(String statut) {
        if (statut == null) return "#94a3b8";
        return switch (statut.toUpperCase()) {
            case "FAIT"     -> "#15803d";
            case "EN_COURS" -> "#2563eb";
            case "BLOQUE"   -> "#dc2626";
            default          -> "#f59e0b";
        };
    }

    public String getIconeCategorie(String categorie) {
        if (categorie == null) return "pi-circle";
        return switch (categorie.toUpperCase()) {
            case "LOGISTIQUE"    -> "pi-box";
            case "ACCES"         -> "pi-key";
            case "ADMINISTRATIF" -> "pi-file-edit";
            case "FORMATION"     -> "pi-book";
            case "INTEGRATION"   -> "pi-users";
            default               -> "pi-circle";
        };
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String t, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, t, d));
    }

    // Getters / Setters
    public List<Employe>         getEmployesEnIntegration() { return employesEnIntegration; }
    public Employe               getEmployeSelectionne()    { return employeSelectionne; }
    public List<TacheOnboarding> getTachesSelection()       { return tachesSelection; }
    public String                getCommentaireBloque()     { return commentaireBloque; }
    public void                  setCommentaireBloque(String c) { this.commentaireBloque = c; }
}
