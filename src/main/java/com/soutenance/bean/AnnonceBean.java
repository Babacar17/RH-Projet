package com.soutenance.bean;

import com.soutenance.entity.Institution;
import com.soutenance.service.AnnonceService;
import com.soutenance.service.InstitutionService;
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
 * Bean d'envoi d'annonces par le super-admin.
 *
 * Trois ciblages : TOUS, par ROLE, par INSTITUTION.
 */
@Component("annonceBean")
@Scope("view")
public class AnnonceBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private AnnonceService     annonceService;
    @Autowired private InstitutionService institutionService;
    @Autowired private SessionBean        sessionBean;

    private List<Institution> toutesInstitutions = new ArrayList<>();

    // Formulaire
    private String cible    = "TOUS";        // TOUS / ROLE / INSTITUTION
    private String roleCible;                // ADMIN / RH / MANAGER / EMPLOYE / etc.
    private Long   institutionCibleId;
    private String titre;
    private String message;
    private String priorite = "NORMALE";     // BASSE / NORMALE / HAUTE / URGENTE
    private boolean avecEmail = false;

    @PostConstruct
    public void init() {
        toutesInstitutions = institutionService.getToutesLesInstitutions();
    }

    /** Vrai si on doit afficher le select rôle. */
    public boolean isCibleRole()        { return "ROLE".equals(cible); }
    /** Vrai si on doit afficher le select institution. */
    public boolean isCibleInstitution() { return "INSTITUTION".equals(cible); }

    public void diffuser() {
        if (titre == null || titre.isBlank()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Champ requis",
                "Saisissez un titre pour l'annonce.");
            return;
        }
        try {
            String parametre = null;
            switch (cible) {
                case "ROLE":        parametre = roleCible; break;
                case "INSTITUTION":
                    parametre = institutionCibleId != null
                        ? String.valueOf(institutionCibleId) : null;
                    break;
            }
            int n = annonceService.diffuserAnnonce(
                cible, parametre, titre, message, priorite, avecEmail, getOp());

            if (n == 0) {
                addMsg(FacesMessage.SEVERITY_WARN, "Aucun destinataire",
                    "La cible choisie n'a aucun utilisateur actif.");
            } else {
                addMsg(FacesMessage.SEVERITY_INFO, "Annonce diffusée",
                    n + " utilisateur(s) ont reçu cette annonce.");
                // Réinitialiser le formulaire
                titre = null;
                message = null;
                roleCible = null;
                institutionCibleId = null;
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    private void addMsg(FacesMessage.Severity sev, String t, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, t, d));
    }

    // Getters / Setters
    public List<Institution> getToutesInstitutions() { return toutesInstitutions; }
    public String  getCible()                  { return cible; }
    public void    setCible(String c)          { this.cible = c; }
    public String  getRoleCible()              { return roleCible; }
    public void    setRoleCible(String r)      { this.roleCible = r; }
    public Long    getInstitutionCibleId()     { return institutionCibleId; }
    public void    setInstitutionCibleId(Long id) { this.institutionCibleId = id; }
    public String  getTitre()                  { return titre; }
    public void    setTitre(String t)          { this.titre = t; }
    public String  getMessage()                { return message; }
    public void    setMessage(String m)        { this.message = m; }
    public String  getPriorite()               { return priorite; }
    public void    setPriorite(String p)       { this.priorite = p; }
    public boolean isAvecEmail()               { return avecEmail; }
    public void    setAvecEmail(boolean e)     { this.avecEmail = e; }
}
