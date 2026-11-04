package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.*;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

/**
 * Bean JSF — Administration des employés.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. Passait directement par EmployeDao et StructureDao → passe désormais
 *    exclusivement par RHService et StructureService.
 *
 * 2. Gestion des mutations inter-institutions ajoutée :
 *    - formulaire de mutation avec sélection de l'institution de destination
 *    - appel RHService.enregistrerMouvement() → RHService.approuverMouvement()
 *    - lors de l'approbation, l'employé est réaffecté à la nouvelle institution
 *
 * 3. Propriétés manquantes pour employes.xhtml et structures.xhtml ajoutées.
 */
@Component("adminEmployeBean")
@Scope("view")
public class AdminEmployeBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminEmployeBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private RHService          rhService;
    @Autowired private StructureService   structureService;
    @Autowired private SessionBean        sessionBean;

    @Autowired(required = false) private InstitutionService institutionService;
    @Autowired(required = false) private AdminBean adminBean;

    /** Id institution actif : filtre super-admin OU institution de session. */
    private Long getInstitutionId() {
        if (adminBean != null && adminBean.getInstitutionFiltreId() != null)
            return adminBean.getInstitutionFiltreId();
        return sessionBean.getInstitutionId();
    }

    // ── Données principales ───────────────────────────────────────────────────
    private List<Employe>     employes;
    private List<Service>     tousServices;
    private List<Institution> toutesInstitutions;
    private Employe           employe     = new Employe();
    private Long              serviceId;
    // Rattachement direct (structure libre) : une seule valeur utilisée si pas de service
    private Long              directionOrgId;
    private Long              divisionOrgId;
    private Long              departementOrgId;
    private String            recherche;
    private boolean           modeEdition = false;
    /** Statut choisi dans les onglets de filtre ; null = aucun onglet choisi, tableau masqué. */
    private String            filtreStatutEmploye;

    // ── Mouvements de carrière ────────────────────────────────────────────────
    private List<MouvementCarriere> mouvements;
    private MouvementCarriere       mouvement   = new MouvementCarriere();
    private Long                    nouvelleInstitutionId;
    private boolean                 showFormMutation = false;

    // ── Statistiques ──────────────────────────────────────────────────────────
    private long totalEmployes;
    private long totalActifs;
    private long totalChefs;
    private long totalDirecteurs;

    @PostConstruct
    public void init() {
        charger();
        tousServices = structureService.getServicesByInstitution(getInstitutionId());
        if (institutionService != null) {
            toutesInstitutions = institutionService.getToutesLesInstitutions();
        }
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    private void charger() {
        Long instId = getInstitutionId();
        // CORRECTION : filtrer par institution sélectionnée
        employes = (instId != null)
            ? rhService.getEmployesByInstitution(instId)
            : rhService.getTousEmployes();
        totalEmployes   = employes.size();
        totalActifs     = employes.stream()
            .filter(e -> "ACTIF".equals(e.getStatut())).count();
        totalChefs      = employes.stream()
            .filter(e -> e.getNiveauHierarchique() != null
                && !e.getNiveauHierarchique().equals("EMPLOYE")
                && !e.getNiveauHierarchique().equals("DIRECTEUR"))
            .count();
        totalDirecteurs = employes.stream()
            .filter(e -> "DIRECTEUR".equals(e.getNiveauHierarchique()))
            .count();
    }

    // ── Préparation formulaires ───────────────────────────────────────────────

    public void preparerNouvel() {
        employe     = new Employe();
        serviceId   = null;
        directionOrgId = null; divisionOrgId = null; departementOrgId = null;
        modeEdition = false;
        tousServices = structureService.getServicesByInstitution(getInstitutionId());
    }

    public void preparerEdition(Employe e) {
        Employe frais = rhService.getEmployeById(e.getId());
        this.employe    = (frais != null) ? frais : e;
        this.serviceId  = (employe.getServiceOrg() != null)
            ? employe.getServiceOrg().getId() : null;
        this.directionOrgId   = (employe.getDirectionOrg()   != null) ? employe.getDirectionOrg().getId()   : null;
        this.divisionOrgId    = (employe.getDivisionOrg()    != null) ? employe.getDivisionOrg().getId()    : null;
        this.departementOrgId = (employe.getDepartementOrg() != null) ? employe.getDepartementOrg().getId() : null;
        this.modeEdition = true;
        tousServices = structureService.getServicesByInstitution(getInstitutionId());
    }

    /**
     * Prépare le formulaire de mutation pour un employé.
     * Pré-remplit l'ancien poste et l'ancien salaire à partir de l'employé.
     */
    public void preparerMutation(Employe e) {
        mouvement = new MouvementCarriere();
        mouvement.setEmploye(e);
        mouvement.setTypeMouvement("MUTATION");
        mouvement.setAncienPoste(e.getIntiutePoste());
        mouvement.setAncienSalaire(e.getSalaireBase());
        mouvement.setAncienGrade(e.getGradeEchelon());
        mouvement.setAncienDepartement(e.getNomService());
        nouvelleInstitutionId = null;
        showFormMutation = true;
    }

    // ── Actions CRUD ──────────────────────────────────────────────────────────

    public String sauvegarder() {
        try {
            if (serviceId != null) {
                Service srv = structureService.getServiceById(serviceId);
                employe.setServiceOrg(srv);
            }
            // Rattachement direct : un seul niveau conservé (département > division > direction)
            employe.setDirectionOrg(null);
            employe.setDivisionOrg(null);
            employe.setDepartementOrg(null);
            if (departementOrgId != null) {
                employe.setDepartementOrg(structureService.getDepartementById(departementOrgId));
            } else if (divisionOrgId != null) {
                employe.setDivisionOrg(structureService.getDivisionById(divisionOrgId));
            } else if (directionOrgId != null) {
                employe.setDirectionOrg(structureService.getDirectionById(directionOrgId));
            }
            String op = getOperateur();
            if (modeEdition) {
                rhService.modifierEmploye(employe, op);
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Fiche modifiée : " + employe.getNomComplet());
            } else {
                if (employe.getStatut() == null) employe.setStatut("ACTIF");
                rhService.creerEmploye(employe, op);
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Employé créé : " + employe.getNomComplet());
            }
            charger();
            employe     = new Employe();
            modeEdition = false;
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimer(Long id) {
        try {
            rhService.supprimerEmploye(id, getOperateur());
            charger();
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Employé supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Mutations de carrière ─────────────────────────────────────────────────

    /**
     * Enregistre une mutation (interne ou inter-institutions).
     *
     * Si nouvelleInstitutionId est renseigné et que le type est MUTATION/
     * MOBILITE/DETACHEMENT, la mutation est inter-institutions.
     * L'institution de destination est enregistrée dans mouvement.nouvelleInstitution.
     * L'employé sera réaffecté lors de l'approbation via approuverMutation().
     */
    public String enregistrerMutation() {
        try {
            if (mouvement.getEmploye() == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Employé obligatoire."); return null;
            }
            if (mouvement.getDateEffet() == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Date d'effet obligatoire."); return null;
            }

            // Résoudre l'institution de destination si mutation inter-institutions
            if (nouvelleInstitutionId != null && institutionService != null) {
                Institution dest = institutionService.getById(nouvelleInstitutionId);
                mouvement.setNouvelleInstitution(dest);
                if (dest != null) {
                    mouvement.setNouveauDepartement(dest.getNom());
                }
            }

            rhService.enregistrerMouvement(mouvement, getOperateur());
            charger();
            mouvement         = new MouvementCarriere();
            nouvelleInstitutionId = null;
            showFormMutation  = false;
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Mouvement enregistré en attente d'approbation.");
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    /**
     * Approuve une mutation.
     * Pour les mutations inter-institutions, RHService.approuverMouvement()
     * réaffecte automatiquement l'employé à la nouvelle institution.
     */
    public void approuverMutation(Long mouvementId) {
        try {
            rhService.approuverMouvement(mouvementId, getOperateur());
            chargerMouvements();
            charger();
            addMsg(FacesMessage.SEVERITY_INFO, "Approuvé", "Mouvement approuvé et appliqué.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void chargerMouvements() {
        if (employe != null && employe.getId() != null) {
            mouvements = rhService.getMouvementsByEmploye(employe.getId());
        } else {
            mouvements = rhService.getTousMouvements();
        }
    }

    public void ouvrirMouvementsEmploye(Employe e) {
        this.employe = e;
        chargerMouvements();
    }

    // ── Recherche ─────────────────────────────────────────────────────────────

    public void rechercher() {
        Long instId = getInstitutionId();
        if (recherche != null && !recherche.isBlank()) {
            employes = rhService.rechercherEmployes(recherche.trim()).stream()
                .filter(e -> instId == null || instId.equals(e.getInstitutionId()))
                .collect(java.util.stream.Collectors.toList());
        } else {
            charger();
        }
    }

    public void reinitialiser() {
        recherche = null;
        charger();
    }

    // ── Filtre par statut (onglets, tableau masqué tant qu'aucun n'est choisi) ──

    public List<Employe> getEmployesFiltres() {
        if (filtreStatutEmploye == null || employes == null) return new java.util.ArrayList<>();
        if ("TOUTES".equals(filtreStatutEmploye)) return employes;
        return employes.stream()
            .filter(e -> filtreStatutEmploye.equals(e.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    public long getNbEmployesParStatut(String statut) {
        if (employes == null) return 0;
        return employes.stream().filter(e -> statut.equals(e.getStatut())).count();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getOperateur() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    /** True si la mutation sélectionnée est inter-institutions. */
    public boolean isMutationInterInstitution() {
        return mouvement != null && mouvement.isMutationInterInstitution();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public List<Employe>          getEmployes()              { return employes; }
    public List<Service>          getTousServices()          { return tousServices; }
    public List<Institution>      getToutesInstitutions()    { return toutesInstitutions; }
    public List<MouvementCarriere> getMouvements()           { return mouvements; }

    public Employe            getEmploye()                   { return employe; }
    public void               setEmploye(Employe e)          { this.employe = e; }
    public MouvementCarriere  getMouvement()                 { return mouvement; }
    public void               setMouvement(MouvementCarriere m) { this.mouvement = m; }

    public Long    getDirectionOrgId()                       { return directionOrgId; }
    public void    setDirectionOrgId(Long id)                { this.directionOrgId = id; }
    public Long    getDivisionOrgId()                        { return divisionOrgId; }
    public void    setDivisionOrgId(Long id)                 { this.divisionOrgId = id; }
    public Long    getDepartementOrgId()                     { return departementOrgId; }
    public void    setDepartementOrgId(Long id)              { this.departementOrgId = id; }
    public List<com.soutenance.entity.Direction>   getToutesDirections()   { return structureService.getDirectionsByInstitution(getInstitutionId()); }
    public List<com.soutenance.entity.Division>    getToutesDivisions()    { return structureService.getDivisionsByInstitution(getInstitutionId()); }
    public List<com.soutenance.entity.Departement> getTousDepartements()   { return structureService.getDepartementsByInstitution(getInstitutionId()); }
    public Long    getServiceId()                            { return serviceId; }
    public void    setServiceId(Long id)                     { this.serviceId = id; }
    public Long    getNouvelleInstitutionId()                { return nouvelleInstitutionId; }
    public void    setNouvelleInstitutionId(Long id)         { this.nouvelleInstitutionId = id; }

    public String  getRecherche()                            { return recherche; }
    public void    setRecherche(String r)                    { this.recherche = r; }
    public String  getFiltreStatutEmploye()                  { return filtreStatutEmploye; }
    public void    setFiltreStatutEmploye(String s)          { this.filtreStatutEmploye = s; }
    public boolean isModeEdition()                           { return modeEdition; }
    public void    setModeEdition(boolean b)                 { this.modeEdition = b; }
    public boolean isShowFormMutation()                      { return showFormMutation; }
    public void    setShowFormMutation(boolean b)            { this.showFormMutation = b; }

    // ── Stats ─────────────────────────────────────────────────────────────────
    public long getTotalEmployes()   { return totalEmployes; }
    public long getTotalActifs()     { return totalActifs; }
    public long getTotalChefs()      { return totalChefs; }
    public long getTotalDirecteurs() { return totalDirecteurs; }
}