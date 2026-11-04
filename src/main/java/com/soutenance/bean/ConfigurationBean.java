package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.JourFerie;

import com.soutenance.entity.ParametreSysteme;
import com.soutenance.entity.TypeConge;
import com.soutenance.entity.WorkflowValidation;
import com.soutenance.service.ConfigurationService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean JSF — Configuration & Paramétrage général du système.
 *
 * Fonctionnalités couvertes (point 8 du cahier des charges) :
 *  - Paramétrer les workflows de validation (congés, recrutements, évaluations)
 *  - Gérer les référentiels communs (types de congés, corps de métiers, diplômes…)
 *  - Configurer les notifications et alertes automatiques
 *  - Gérer le calendrier des jours fériés par institution/région
 *  - Personnaliser les formulaires et les champs
 *  - Politiques de mots de passe, durées de session, SSO / 2FA (paramètres)
 *
 * NOTE : Les entités attendues dans com.soutenance.entity :
 *   - ParametreSysteme  : id, cle (String unique), valeur, description, categorie, modifiablePar
 *   - TypeConge         : id, code, libelle, nbJoursMax, payable (bool), actif (bool)
 *   - JourFerie         : id, date, libelle, institution (nullable = global), region
 *   - WorkflowValidation: id, typeObjet (CONGE|RECRUTEMENT|EVALUATION), etapes (List<String>),
 *                         actif (bool), institution (nullable = global)
 */
@Component("configurationBean")
@Scope("view")
public class ConfigurationBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private ConfigurationService configService;
    @Autowired private SessionBean          sessionBean;

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<ParametreSysteme>  parametres;
    private List<TypeConge>         typesConge;
    private List<JourFerie>         joursFeries;
    private List<WorkflowValidation> workflows;

    // ── Entités en cours d'édition ───────────────────────────────────────────
    private ParametreSysteme  parametre   = new ParametreSysteme();
    private TypeConge         typeConge   = new TypeConge();
    private JourFerie         jourFerie   = new JourFerie();
    private WorkflowValidation workflow   = new WorkflowValidation();

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean modeEditionParam     = false;
    private boolean modeEditionTypeConge = false;
    private boolean modeEditionJourFerie = false;
    private boolean modeEditionWorkflow  = false;
    private String  categorieFiltre;

    // ── Année pour le calendrier des fériés ──────────────────────────────────
    private int anneeCalendrier = LocalDate.now().getYear();

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("ConfigurationBean init error: " + e.getMessage());
            parametres  = new ArrayList<>();
            typesConge  = new ArrayList<>();
            joursFeries = new ArrayList<>();
            workflows   = new ArrayList<>();
        }
    }

    private void chargerTout() {
        Long institutionId = sessionBean.getInstitutionId();
        parametres  = configService.getTousParametres();
        typesConge  = configService.getTousTypesConge(institutionId);
        joursFeries = configService.getJoursFeries(anneeCalendrier, institutionId);
        workflows   = configService.getTousWorkflows(institutionId);
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // PARAMÈTRES SYSTÈME
    // ════════════════════════════════════════════════════════════

    public void preparerEditionParametre(ParametreSysteme p) {
        this.parametre       = p;
        this.modeEditionParam = true;
    }

    /**
     * Sauvegarde la valeur d'un paramètre système.
     * Seule la valeur est modifiable — la clé et la catégorie sont en lecture seule.
     */
    public void sauvegarderParametre() {
        try {
            if (parametre.getValeur() == null || parametre.getValeur().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "La valeur du paramètre ne peut pas être vide.");
                return;
            }
            configService.modifierParametre(parametre, getOp());
            parametres = configService.getTousParametres();
            modeEditionParam = false;
            addMsg(FacesMessage.SEVERITY_INFO, "Paramètre sauvegardé",
                "« " + parametre.getCle() + " » mis à jour avec succès.");
            parametre = new ParametreSysteme();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Filtre les paramètres par catégorie (SECURITE, PAIE, CONGES, GENERAL…). */
    public List<ParametreSysteme> getParametresParCategorie(String categorie) {
        if (parametres == null) return new ArrayList<>();
        return parametres.stream()
            .filter(p -> categorie == null || categorie.equals(p.getCategorie()))
            .collect(java.util.stream.Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // TYPES DE CONGÉS
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauTypeConge() {
        typeConge            = new TypeConge();
        typeConge.setActif(true);
        typeConge.setPayable(true);
        typeConge.setNbJoursMax(30);
        modeEditionTypeConge = false;
    }

    public void preparerEditionTypeConge(TypeConge t) {
        this.typeConge        = t;
        modeEditionTypeConge  = true;
    }

    public String sauvegarderTypeConge() {
        try {
            if (typeConge.getCode() == null || typeConge.getCode().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le code du type de congé est obligatoire.");
                return null;
            }
            if (typeConge.getLibelle() == null || typeConge.getLibelle().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le libellé est obligatoire.");
                return null;
            }
            if (typeConge.getNbJoursMax() <= 0) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Le nombre de jours maximum doit être supérieur à 0.");
                return null;
            }
            typeConge.setCode(typeConge.getCode().toUpperCase().trim());
            if (modeEditionTypeConge) {
                configService.modifierTypeConge(typeConge, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Type de congé modifié.");
            } else {
                configService.creerTypeConge(typeConge, sessionBean.getInstitutionId(), getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Type de congé créé : " + typeConge.getLibelle());
            }
            typesConge = configService.getTousTypesConge(sessionBean.getInstitutionId());
            typeConge  = new TypeConge();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void toggleActifTypeConge(Long id) {
        try {
            configService.toggleActifTypeConge(id, getOp());
            typesConge = configService.getTousTypesConge(sessionBean.getInstitutionId());
            addMsg(FacesMessage.SEVERITY_INFO, "Statut modifié",
                "Le type de congé a été activé/désactivé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void supprimerTypeConge(Long id) {
        try {
            String erreur = configService.supprimerTypeConge(id, getOp());
            if (erreur != null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Suppression impossible", erreur);
                return;
            }
            typesConge = configService.getTousTypesConge(sessionBean.getInstitutionId());
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Type de congé supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // JOURS FÉRIÉS
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauJourFerie() {
        jourFerie            = new JourFerie();
        jourFerie.setDate(LocalDate.now());
        modeEditionJourFerie = false;
    }

    public void preparerEditionJourFerie(JourFerie j) {
        this.jourFerie        = j;
        modeEditionJourFerie  = true;
    }

    public String sauvegarderJourFerie() {
        try {
            if (jourFerie.getDate() == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "La date du jour férié est obligatoire.");
                return null;
            }
            if (jourFerie.getLibelle() == null || jourFerie.getLibelle().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le libellé est obligatoire.");
                return null;
            }
            if (modeEditionJourFerie) {
                configService.modifierJourFerie(jourFerie, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Jour férié modifié.");
            } else {
                configService.creerJourFerie(jourFerie, sessionBean.getInstitutionId(), getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Jour férié ajouté : " + jourFerie.getLibelle());
            }
            anneeCalendrier = jourFerie.getDate().getYear();
            joursFeries = configService.getJoursFeries(anneeCalendrier, sessionBean.getInstitutionId());
            jourFerie   = new JourFerie();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimerJourFerie(Long id) {
        try {
            configService.supprimerJourFerie(id, getOp());
            joursFeries = configService.getJoursFeries(anneeCalendrier, sessionBean.getInstitutionId());
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Jour férié supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Navigue vers l'année précédente dans le calendrier. */
    public void anneePreced() {
        anneeCalendrier--;
        joursFeries = configService.getJoursFeries(anneeCalendrier, sessionBean.getInstitutionId());
    }

    /** Navigue vers l'année suivante dans le calendrier. */
    public void anneeSuiv() {
        anneeCalendrier++;
        joursFeries = configService.getJoursFeries(anneeCalendrier, sessionBean.getInstitutionId());
    }

    // ════════════════════════════════════════════════════════════
    // WORKFLOWS DE VALIDATION
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauWorkflow() {
        workflow            = new WorkflowValidation();
        workflow.setActif(true);
        modeEditionWorkflow = false;
    }

    public void preparerEditionWorkflow(WorkflowValidation w) {
        this.workflow        = w;
        modeEditionWorkflow  = true;
    }

    public String sauvegarderWorkflow() {
        try {
            if (workflow.getTypeObjet() == null || workflow.getTypeObjet().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Le type d'objet du workflow est obligatoire (CONGE, RECRUTEMENT, EVALUATION).");
                return null;
            }
            if (modeEditionWorkflow) {
                configService.modifierWorkflow(workflow, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Workflow modifié",
                    "Le workflow a été mis à jour.");
            } else {
                configService.creerWorkflow(workflow, sessionBean.getInstitutionId(), getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Workflow créé",
                    "Workflow " + workflow.getTypeObjet() + " créé avec succès.");
            }
            workflows = configService.getTousWorkflows(sessionBean.getInstitutionId());
            workflow  = new WorkflowValidation();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void toggleActifWorkflow(Long id) {
        try {
            configService.toggleActifWorkflow(id, getOp());
            workflows = configService.getTousWorkflows(sessionBean.getInstitutionId());
            addMsg(FacesMessage.SEVERITY_INFO, "Statut modifié",
                "Le workflow a été activé/désactivé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    /** Libellé du type d'objet workflow. */
    public String getLabelTypeObjet(String typeObjet) {
        if (typeObjet == null) return "—";
        return switch (typeObjet) {
            case "CONGE"       -> "Congés & Absences";
            case "RECRUTEMENT" -> "Recrutement";
            case "EVALUATION"  -> "Évaluations";
            case "MOUVEMENT"   -> "Mouvements de carrière";
            case "PAIE"        -> "Validation de paie";
            default            -> typeObjet;
        };
    }

    /** Retourne l'icône Font-Awesome associée à une catégorie de paramètre. */
    public String getIconeCategorie(String categorie) {
        if (categorie == null) return "fa-cog";
        return switch (categorie) {
            case "SECURITE"  -> "fa-shield-alt";
            case "PAIE"      -> "fa-money-bill-wave";
            case "CONGES"    -> "fa-calendar-check";
            case "NOTIF"     -> "fa-bell";
            default          -> "fa-cog";
        };
    }

    /** Vérifie si une date est un jour férié. */
    public boolean isJourFerie(LocalDate date) {
        if (joursFeries == null || date == null) return false;
        return joursFeries.stream().anyMatch(j -> date.equals(j.getDate()));
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<ParametreSysteme>   getParametres()          { return parametres; }
    public List<TypeConge>          getTypesConge()          { return typesConge; }
    public List<JourFerie>          getJoursFeries()         { return joursFeries; }
    public List<WorkflowValidation> getWorkflows()           { return workflows; }

    public ParametreSysteme   getParametre()                 { return parametre; }
    public void setParametre(ParametreSysteme p)             { this.parametre = p; }
    public TypeConge          getTypeConge()                 { return typeConge; }
    public void setTypeConge(TypeConge t)                    { this.typeConge = t; }
    public JourFerie          getJourFerie()                 { return jourFerie; }
    public void setJourFerie(JourFerie j)                    { this.jourFerie = j; }
    public WorkflowValidation getWorkflow()                  { return workflow; }
    public void setWorkflow(WorkflowValidation w)            { this.workflow = w; }

    public boolean isModeEditionParam()                      { return modeEditionParam; }
    public boolean isModeEditionTypeConge()                  { return modeEditionTypeConge; }
    public boolean isModeEditionJourFerie()                  { return modeEditionJourFerie; }
    public boolean isModeEditionWorkflow()                   { return modeEditionWorkflow; }

    public String getCategorieFiltre()                       { return categorieFiltre; }
    public void   setCategorieFiltre(String c)               { this.categorieFiltre = c; }
    public int    getAnneeCalendrier()                       { return anneeCalendrier; }
    public void   setAnneeCalendrier(int a)                  { this.anneeCalendrier = a; }
}