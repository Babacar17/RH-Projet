package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Institution;
import com.soutenance.service.InstitutionService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

/**
 * Bean JSF — Gestion complète des institutions (super-admin uniquement).
 *
 * Fonctionnalités couvertes (point 1 du cahier des charges) :
 *  - Créer, modifier, archiver/désactiver des institutions
 *  - Configurer les informations (nom, code/sigle, adresse, contacts, logo)
 *  - Définir le type de hiérarchie organisationnelle
 *  - Activer / désactiver une institution
 *  - Supprimer (uniquement si aucun employé ni utilisateur rattaché)
 *  - Recherche et filtrage
 *  - Statistiques : total, actives, en configuration, effectifs
 *
 * SÉCURITÉ : toutes les actions sont tracées via InstitutionService → LogActionDao.
 * Ce bean ne doit être accessible qu'aux utilisateurs ayant le rôle ADMIN
 * sans institution assignée (super-admin global, isSuperAdmin() == true).
 */
@Component("institutionBean")
@Scope("view")
public class InstitutionBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(InstitutionBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private InstitutionService institutionService;
    @Autowired private SessionBean        sessionBean;

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<Institution> institutions;
    private List<Institution> institutionsFiltrees;

    // ── Entité en cours d'édition ────────────────────────────────────────────
    private Institution institution = new Institution();

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean modeEdition       = false;
    private String  termeFiltrage;
    private Institution institutionSelectionnee;

    // ── Statistiques dashboard super-admin ───────────────────────────────────
    private long totalInstitutions;
    private long institutionsActives;
    private long institutionsEnConfig;

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("InstitutionBean init error: " + e.getMessage());
            LOG.error("Erreur inattendue", e);
        }
    }

    private void chargerTout() {
        institutions         = institutionService.getToutesLesInstitutions();
        institutionsFiltrees = institutions;
        totalInstitutions    = institutionService.countTotal();
        institutionsActives  = institutionService.countActives();
        institutionsEnConfig = institutionService.countEnConfig();
    }

    private String getSuperAdmin() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "superadmin";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // CRUD INSTITUTIONS
    // ════════════════════════════════════════════════════════════

    /** Prépare le formulaire de création d'une nouvelle institution. */
    public void preparerNouvelleInstitution() {
        institution = new Institution();
        institution.setStatut("CONFIG"); // Par défaut en configuration
        institution.setPays("Sénégal");  // Valeur par défaut contextuelle
        institution.setTypeHierarchie("STANDARD");
        modeEdition = false;
    }

    /** Prépare le formulaire de modification d'une institution existante. */
    public void preparerEditionInstitution(Institution inst) {
        this.institution = institutionService.getById(inst.getId());
        if (this.institution == null) this.institution = inst;
        this.modeEdition = true;
    }

    /**
     * Crée ou modifie une institution selon le mode courant.
     * Délègue la validation métier au service.
     */
    public String sauvegarderInstitution() {
        try {
            String erreur;
            if (modeEdition) {
                erreur = institutionService.modifier(institution, getSuperAdmin());
                if (erreur != null) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur de validation", erreur);
                    return null;
                }
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Institution modifiée : " + institution.getNom());
            } else {
                erreur = institutionService.creer(institution, getSuperAdmin());
                if (erreur != null) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur de validation", erreur);
                    return null;
                }
                addMsg(FacesMessage.SEVERITY_INFO, "Institution créée",
                    institution.getNom() + " (" + institution.getCode() + ") ajoutée avec succès.");
            }
            chargerTout();
            institution = new Institution();
            modeEdition = false;
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur inattendue", ex.getMessage());
        }
        return null;
    }

    /** Active une institution (statut → ACTIVE). */
    public void activerInstitution(Long id) {
        try {
            institutionService.activerInstitution(id, getSuperAdmin());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Institution activée",
                "L'institution est maintenant active.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Désactive une institution (statut → INACTIVE). */
    public void desactiverInstitution(Long id) {
        try {
            institutionService.desactiverInstitution(id, getSuperAdmin());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Institution désactivée",
                "L'institution a été mise en veille.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /**
     * Supprime une institution.
     * Bloqué par le service si des employés ou utilisateurs y sont rattachés.
     */
    public void supprimerInstitution(Long id) {
        try {
            String erreur = institutionService.supprimer(id, getSuperAdmin());
            if (erreur != null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Suppression impossible", erreur);
                return;
            }
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Institution supprimée",
                "L'institution a été supprimée définitivement.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // RECHERCHE & FILTRAGE
    // ════════════════════════════════════════════════════════════

    /** Filtre la liste par nom, code ou ville. */
    public void filtrer() {
        if (termeFiltrage == null || termeFiltrage.isBlank()) {
            institutionsFiltrees = institutions;
        } else {
            institutionsFiltrees = institutionService.rechercher(termeFiltrage);
        }
    }

    /** Remet à zéro le filtre. */
    public void reinitialiserFiltres() {
        termeFiltrage        = null;
        institutionsFiltrees = institutions;
    }

    /** Sélectionne une institution pour afficher ses détails dans un dialog. */
    public void selectionnerInstitution(Institution inst) {
        this.institutionSelectionnee = institutionService.getById(inst.getId());
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    /** Classe CSS de badge selon le statut de l'institution. */
    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "ACTIVE"   -> "badge-success";
            case "INACTIVE" -> "badge-danger";
            case "CONFIG"   -> "badge-warning";
            default         -> "badge-secondary";
        };
    }

    /** Libellé lisible du statut. */
    public String getLabelStatut(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "ACTIVE"   -> "Active";
            case "INACTIVE" -> "Inactive";
            case "CONFIG"   -> "En configuration";
            default         -> statut;
        };
    }

    /** Icône Font-Awesome selon le type de hiérarchie. */
    public String getIconeHierarchie(String type) {
        if (type == null) return "fa-building";
        return switch (type) {
            case "MINISTERE"  -> "fa-landmark";
            case "DIRECTION"  -> "fa-sitemap";
            case "AGENCE"     -> "fa-briefcase";
            default           -> "fa-building";
        };
    }

    /** Nombre d'employés rattachés à une institution (pour affichage dans la liste). */
    public long getNbEmployes(Long institutionId) {
        try { return institutionService.countEmployesByInstitution(institutionId); }
        catch (Exception e) { return 0; }
    }

    /** Nombre d'utilisateurs rattachés à une institution. */
    public long getNbUtilisateurs(Long institutionId) {
        try { return institutionService.countUsersByInstitution(institutionId); }
        catch (Exception e) { return 0; }
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<Institution> getInstitutions()              { return institutions; }
    public List<Institution> getInstitutionsFiltrees()      { return institutionsFiltrees; }

    public Institution getInstitution()                     { return institution; }
    public void        setInstitution(Institution i)        { this.institution = i; }

    public Institution getInstitutionSelectionnee()         { return institutionSelectionnee; }
    public void setInstitutionSelectionnee(Institution i)   { this.institutionSelectionnee = i; }

    public boolean isModeEdition()                          { return modeEdition; }
    public void    setModeEdition(boolean b)                { this.modeEdition = b; }

    public String  getTermeFiltrage()                       { return termeFiltrage; }
    public void    setTermeFiltrage(String s)               { this.termeFiltrage = s; }

    public long getTotalInstitutions()                      { return totalInstitutions; }
    public long getInstitutionsActives()                    { return institutionsActives; }
    public long getInstitutionsEnConfig()                   { return institutionsEnConfig; }
}