package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.AdminParametrageService;
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
 * Bean de configuration & paramétrage — périmètre administrateur.
 *
 * Corrections appliquées :
 *  1. Parametre           → ParametreSysteme  (champs, types, instanciations)
 *  2. List<Parametre>     → List<ParametreSysteme>
 *  3. Workflow            → WorkflowValidation (champs, types, instanciations)
 *  4. List<Workflow>      → List<WorkflowValidation>
 *  5. workflow.getNom()   → workflow.getTypeObjet()
 *  6. workflow.getModule()→ workflow.getTypeObjet()
 *  7. changerStatutWorkflow(id, "ACTIF"/"INACTIF", op)
 *                         → changerStatutWorkflow(id, true/false, op)  (boolean, pas String)
 *  8. etape.setWorkflow() → supprimé : WorkflowValidation stocke ses étapes en List<String>,
 *                           pas en entités EtapeValidation liées par FK.
 *     Les méthodes d'étapes opèrent sur workflowSelectionne.getEtapes() (List<String>)
 *     et appellent le service via modifierWorkflow() pour persister.
 *  9. selectionnerWorkflow / sauvegarderEtape : adapté à List<String> (plus d'EtapeValidation).
 * 10. getGroupesDistincts() : Parametre::getGroupe → ParametreSysteme::getCategorie
 *     (ParametreSysteme n'a pas de champ "groupe", le champ équivalent est "categorie").
 * 11. getBadgeStatutWorkflow() : adapté au boolean actif de WorkflowValidation.
 */
@Component("adminParametrageBean")
@Scope("view")
public class AdminParametrageBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminParametrageBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminParametrageService parametrageService;
    @Autowired private SessionBean             sessionBean;

    // ── Paramètres ────────────────────────────────────────────────────────
    // CORRECTION 1 & 2 : Parametre → ParametreSysteme
    private List<ParametreSysteme> parametres       = new ArrayList<>();
    private ParametreSysteme       parametre        = new ParametreSysteme();
    private boolean                modeEditionParam = false;
    private String                 filtreGroupeParam;

    // ── Workflows ─────────────────────────────────────────────────────────
    // CORRECTION 3 & 4 : Workflow → WorkflowValidation
    private List<WorkflowValidation> workflows            = new ArrayList<>();
    private WorkflowValidation       workflow             = new WorkflowValidation();
    private WorkflowValidation       workflowSelectionne;
    private boolean                  modeEditionWf        = false;

    // ── Étapes de validation ──────────────────────────────────────────────
    // CORRECTION 8 : WorkflowValidation stocke les étapes en List<String>,
    // pas en entités EtapeValidation. On conserve la liste String en mémoire.
    private List<String> etapes          = new ArrayList<>();
    private String       etapeLibelle;          // champ de saisie pour une étape
    private int          etapeIndexEdition = -1; // -1 = création, ≥0 = édition

    // ── Types de congés ───────────────────────────────────────────────────
    private List<TypeConge> typesConge    = new ArrayList<>();
    private TypeConge       typeConge     = new TypeConge();
    private boolean         modeEditionTC = false;

    // ── Sessions utilisateurs ─────────────────────────────────────────────
    private List<SessionUtilisateur> sessions = new ArrayList<>();
    private SessionUtilisateur       sessionSelectionnee;
    private long                     nbSessionsActives;

    // ── Onglet actif ──────────────────────────────────────────────────────
    private String ongletActif = "parametres";

    @PostConstruct
    public void init() {
        chargerTout();
    }

    private void chargerTout() {
        try {
            Long instId = sessionBean.getInstitutionId();
            parametres  = parametrageService.getParametresByInstitution(instId);
            workflows   = parametrageService.getWorkflowsByInstitution(instId);
            typesConge  = parametrageService.getTypesCongeByInstitution(instId);
            sessions    = parametrageService.getSessionsActives(instId);
            nbSessionsActives = sessions.stream().filter(SessionUtilisateur::isActive).count();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            parametres = new ArrayList<>();
            workflows  = new ArrayList<>();
            typesConge = new ArrayList<>();
            sessions   = new ArrayList<>();
        }
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    // ══════════════════════════════════════════════════════════
    // PARAMÈTRES CLÉ/VALEUR
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauParametre() {
        // CORRECTION 1 : new Parametre() → new ParametreSysteme()
        parametre = new ParametreSysteme();
        modeEditionParam = false;
    }

    // CORRECTION 1 : Parametre → ParametreSysteme dans la signature
    public void preparerEditionParametre(ParametreSysteme p) {
        this.parametre = p;
        modeEditionParam = true;
    }

    public String sauvegarderParametre() {
        try {
            if (parametre.getCle() == null || parametre.getCle().isBlank()) {
                addError("La clé est obligatoire.");
                return null;
            }
            if (parametre.getValeur() == null || parametre.getValeur().isBlank()) {
                addError("La valeur est obligatoire.");
                return null;
            }

            Long instId = sessionBean.getInstitutionId();

            if (modeEditionParam) {
                // CORRECTION 1 : modifierParametre attend un ParametreSysteme — OK
                parametrageService.modifierParametre(parametre, getOp());
                addInfo("Paramètre mis à jour : " + parametre.getCle());
            } else {
                String erreur = parametrageService.creerParametre(parametre, instId, getOp());
                if (erreur != null) {
                    addError(erreur);
                    return null;
                }
                addInfo("Paramètre créé : " + parametre.getCle());
            }
            parametres = parametrageService.getParametresByInstitution(instId);
            // CORRECTION 1 : new Parametre() → new ParametreSysteme()
            parametre  = new ParametreSysteme();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void supprimerParametre(Long id) {
        try {
            parametrageService.supprimerParametre(id, getOp());
            parametres = parametrageService.getParametresByInstitution(sessionBean.getInstitutionId());
            addInfo("Paramètre supprimé.");
        } catch (Exception ex) {
            addError("Erreur suppression : " + ex.getMessage());
        }
    }

    public void filtrerParametresParGroupe() {
        try {
            Long instId = sessionBean.getInstitutionId();
            // CORRECTION 2 : List<Parametre> → List<ParametreSysteme>
            List<ParametreSysteme> tous = parametrageService.getParametresByInstitution(instId);
            if (filtreGroupeParam == null || filtreGroupeParam.isBlank()) {
                parametres = tous;
            } else {
                // CORRECTION 10 : getGroupe() → getCategorie() (ParametreSysteme n'a pas getGroupe())
                parametres = tous.stream()
                    .filter(p -> filtreGroupeParam.equals(p.getCategorie()))
                    .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception ex) {
            addError("Erreur filtre : " + ex.getMessage());
        }
    }

    /** Met à jour une valeur de paramètre directement (édition inline). */
    // CORRECTION 1 : Parametre → ParametreSysteme dans la signature
    public void mettreAJourValeur(ParametreSysteme p, String nouvelleValeur) {
        try {
            p.setValeur(nouvelleValeur);
            parametrageService.modifierParametre(p, getOp());
            addInfo("Paramètre mis à jour : " + p.getCle() + " = " + nouvelleValeur);
        } catch (Exception ex) {
            addError("Erreur mise à jour : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // WORKFLOWS
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauWorkflow() {
        // CORRECTION 3 : new Workflow() → new WorkflowValidation()
        workflow = new WorkflowValidation();
        modeEditionWf = false;
    }

    // CORRECTION 3 : Workflow → WorkflowValidation dans la signature
    public void preparerEditionWorkflow(WorkflowValidation wf) {
        this.workflow = parametrageService.getWorkflowById(wf.getId());
        if (this.workflow == null) this.workflow = wf;
        modeEditionWf = true;
    }

    public String sauvegarderWorkflow() {
        try {
            // CORRECTION 5 & 6 : getNom()/getModule() → getTypeObjet()
            if (workflow.getTypeObjet() == null || workflow.getTypeObjet().isBlank()) {
                addError("Le type d'objet du workflow est obligatoire.");
                return null;
            }

            Long instId = sessionBean.getInstitutionId();

            if (modeEditionWf) {
                parametrageService.modifierWorkflow(workflow, getOp());
                // CORRECTION 5 : getNom() → getTypeObjet()
                addInfo("Workflow modifié : " + workflow.getTypeObjet());
            } else {
                String erreur = parametrageService.creerWorkflow(workflow, instId, getOp());
                if (erreur != null) {
                    addError(erreur);
                    return null;
                }
                // CORRECTION 5 : getNom() → getTypeObjet()
                addInfo("Workflow créé : " + workflow.getTypeObjet());
            }
            workflows = parametrageService.getWorkflowsByInstitution(instId);
            // CORRECTION 3 : new Workflow() → new WorkflowValidation()
            workflow  = new WorkflowValidation();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void activerWorkflow(Long id) {
        try {
            // CORRECTION 7 : changerStatutWorkflow(id, "ACTIF", op) → (id, true, op)
            parametrageService.changerStatutWorkflow(id, true, getOp());
            workflows = parametrageService.getWorkflowsByInstitution(sessionBean.getInstitutionId());
            addInfo("Workflow activé.");
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
    }

    public void desactiverWorkflow(Long id) {
        try {
            // CORRECTION 7 : changerStatutWorkflow(id, "INACTIF", op) → (id, false, op)
            parametrageService.changerStatutWorkflow(id, false, getOp());
            workflows = parametrageService.getWorkflowsByInstitution(sessionBean.getInstitutionId());
            addInfo("Workflow désactivé.");
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
    }

    public void supprimerWorkflow(Long id) {
        try {
            parametrageService.supprimerWorkflow(id, getOp());
            workflows = parametrageService.getWorkflowsByInstitution(sessionBean.getInstitutionId());
            addInfo("Workflow supprimé.");
        } catch (Exception ex) {
            addError("Impossible de supprimer ce workflow.");
        }
    }

    // CORRECTION 3 & 8 : Workflow → WorkflowValidation ; étapes = List<String>
    public void selectionnerWorkflow(WorkflowValidation wf) {
        this.workflowSelectionne = parametrageService.getWorkflowById(wf.getId());
        if (this.workflowSelectionne == null) this.workflowSelectionne = wf;
        // CORRECTION 8 : getEtapes() renvoie List<String> dans WorkflowValidation
        this.etapes = workflowSelectionne.getEtapes() != null
            ? new ArrayList<>(workflowSelectionne.getEtapes()) : new ArrayList<>();
        this.etapeLibelle    = null;
        this.etapeIndexEdition = -1;
    }

    // ── Étapes d'un workflow ───────────────────────────────────────────────
    // CORRECTION 8 : les étapes sont de simples String dans WorkflowValidation.
    // On ne passe plus par EtapeValidation ni par etapeDao — on modifie la liste
    // en mémoire puis on persiste le workflow entier via modifierWorkflow().

    public void preparerNouvelleEtape() {
        if (workflowSelectionne == null) {
            addError("Sélectionnez un workflow d'abord.");
            return;
        }
        etapeLibelle     = null;
        etapeIndexEdition = -1;
    }

    public void preparerEditionEtape(int index) {
        if (etapes != null && index >= 0 && index < etapes.size()) {
            etapeLibelle     = etapes.get(index);
            etapeIndexEdition = index;
        }
    }

    public String sauvegarderEtape() {
        try {
            if (workflowSelectionne == null) {
                addError("Aucun workflow sélectionné.");
                return null;
            }
            if (etapeLibelle == null || etapeLibelle.isBlank()) {
                addError("Le libellé de l'étape est obligatoire.");
                return null;
            }

            // Synchronise la liste locale puis persiste le workflow
            if (etapeIndexEdition >= 0 && etapeIndexEdition < etapes.size()) {
                etapes.set(etapeIndexEdition, etapeLibelle);
                addInfo("Étape modifiée.");
            } else {
                etapes.add(etapeLibelle);
                addInfo("Étape ajoutée.");
            }

            workflowSelectionne.setEtapes(etapes);
            parametrageService.modifierWorkflow(workflowSelectionne, getOp());

            // Recharger pour confirmer la persistance
            workflowSelectionne = parametrageService.getWorkflowById(workflowSelectionne.getId());
            etapes = workflowSelectionne != null && workflowSelectionne.getEtapes() != null
                ? new ArrayList<>(workflowSelectionne.getEtapes()) : new ArrayList<>();
            etapeLibelle     = null;
            etapeIndexEdition = -1;
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void supprimerEtape(int index) {
        try {
            if (workflowSelectionne == null || etapes == null
                    || index < 0 || index >= etapes.size()) {
                addError("Étape introuvable.");
                return;
            }
            etapes.remove(index);
            workflowSelectionne.setEtapes(etapes);
            parametrageService.modifierWorkflow(workflowSelectionne, getOp());
            workflowSelectionne = parametrageService.getWorkflowById(workflowSelectionne.getId());
            etapes = workflowSelectionne != null && workflowSelectionne.getEtapes() != null
                ? new ArrayList<>(workflowSelectionne.getEtapes()) : new ArrayList<>();
            addInfo("Étape supprimée.");
        } catch (Exception ex) {
            addError("Erreur suppression étape : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // TYPES DE CONGÉS
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauTypeConge() {
        typeConge = new TypeConge();
        modeEditionTC = false;
    }

    public void preparerEditionTypeConge(TypeConge tc) {
        this.typeConge = tc;
        modeEditionTC  = true;
    }

    public String sauvegarderTypeConge() {
        try {
            if (typeConge.getCode() == null || typeConge.getCode().isBlank()) {
                addError("Le code est obligatoire.");
                return null;
            }
            if (typeConge.getLibelle() == null || typeConge.getLibelle().isBlank()) {
                addError("Le libellé est obligatoire.");
                return null;
            }

            Long instId = sessionBean.getInstitutionId();

            if (modeEditionTC) {
                parametrageService.modifierTypeConge(typeConge, getOp());
                addInfo("Type de congé modifié : " + typeConge.getLibelle());
            } else {
                String erreur = parametrageService.creerTypeConge(typeConge, instId, getOp());
                if (erreur != null) {
                    addError(erreur);
                    return null;
                }
                addInfo("Type de congé créé : " + typeConge.getLibelle());
            }
            typesConge = parametrageService.getTypesCongeByInstitution(instId);
            typeConge  = new TypeConge();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void supprimerTypeConge(Long id) {
        try {
            String erreur = parametrageService.supprimerTypeConge(id, getOp());
            if (erreur != null) {
                addError(erreur);
            } else {
                typesConge = parametrageService.getTypesCongeByInstitution(sessionBean.getInstitutionId());
                addInfo("Type de congé supprimé.");
            }
        } catch (Exception ex) {
            addError("Erreur suppression : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // SESSIONS UTILISATEURS — SÉCURITÉ (F9)
    // ══════════════════════════════════════════════════════════

    public void rafraichirSessions() {
        sessions = parametrageService.getSessionsActives(sessionBean.getInstitutionId());
        nbSessionsActives = sessions.stream().filter(SessionUtilisateur::isActive).count();
    }

    public void revoquerSession(Long sessionId) {
        try {
            parametrageService.revoquerSession(sessionId, "Révocation manuelle par admin", getOp());
            rafraichirSessions();
            addInfo("Session révoquée.");
        } catch (Exception ex) {
            addError("Erreur révocation : " + ex.getMessage());
        }
    }

    public void revoquerToutesSessionsUtilisateur(Long userId) {
        try {
            int nb = parametrageService.revoquerSessionsUtilisateur(userId, getOp());
            rafraichirSessions();
            addInfo(nb + " session(s) révoquée(s).");
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
    }

    public void selectionnerSession(SessionUtilisateur s) {
        this.sessionSelectionnee = s;
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS UI
    // ══════════════════════════════════════════════════════════

    // CORRECTION 11 : WorkflowValidation n'a pas de champ statut String — c'est un boolean actif.
    public String getBadgeStatutWorkflow(boolean actif) {
        return actif ? "badge-success" : "badge-secondary";
    }

    public String getBadgeTypeValeur(String type) {
        if (type == null) return "badge-secondary";
        return switch (type) {
            case "BOOLEAN"  -> "badge-warning";
            case "INTEGER"  -> "badge-info";
            case "DECIMAL"  -> "badge-primary";
            case "DATE"     -> "badge-success";
            default         -> "badge-secondary";
        };
    }

    public String getBadgeStatutSession(boolean active) {
        return active ? "badge-success" : "badge-secondary";
    }

    /** Catégories de paramètres distinctes pour le filtre. */
    public List<String> getGroupesDistincts() {
        if (parametres == null) return new ArrayList<>();
        // CORRECTION 10 : ParametreSysteme utilise getCategorie(), pas getGroupe()
        return parametres.stream()
            .map(ParametreSysteme::getCategorie)
            .filter(g -> g != null && !g.isBlank())
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
    }

    /** Types d'objet de workflow distincts. */
    public List<String> getModulesWorkflow() {
        return List.of("CONGE", "RECRUTEMENT", "EVALUATION", "MOUVEMENT", "PAIE");
    }

    /** Rôles validateurs disponibles. */
    public List<String> getRolesValidateurs() {
        return List.of("ADMIN", "RH", "MANAGER", "DIRECTEUR", "AUDITEUR");
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ══════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════════════

    // Paramètres — CORRECTION 1 & 2
    public List<ParametreSysteme> getParametres()                   { return parametres; }
    public ParametreSysteme       getParametre()                    { return parametre; }
    public void                   setParametre(ParametreSysteme p)  { this.parametre = p; }
    public boolean                isModeEditionParam()              { return modeEditionParam; }
    public String                 getFiltreGroupeParam()            { return filtreGroupeParam; }
    public void                   setFiltreGroupeParam(String v)    { this.filtreGroupeParam = v; }

    // Workflows — CORRECTION 3 & 4
    public List<WorkflowValidation> getWorkflows()                        { return workflows; }
    public WorkflowValidation       getWorkflow()                         { return workflow; }
    public void                     setWorkflow(WorkflowValidation w)     { this.workflow = w; }
    public WorkflowValidation       getWorkflowSelectionne()              { return workflowSelectionne; }
    public boolean                  isModeEditionWf()                     { return modeEditionWf; }

    // Étapes — CORRECTION 8 (List<String> au lieu de List<EtapeValidation>)
    public List<String> getEtapes()                  { return etapes; }
    public String       getEtapeLibelle()            { return etapeLibelle; }
    public void         setEtapeLibelle(String s)    { this.etapeLibelle = s; }
    public int          getEtapeIndexEdition()       { return etapeIndexEdition; }
    public void         setEtapeIndexEdition(int i)  { this.etapeIndexEdition = i; }

    // Types congé
    public List<TypeConge> getTypesConge()              { return typesConge; }
    public TypeConge       getTypeConge()               { return typeConge; }
    public void            setTypeConge(TypeConge tc)   { this.typeConge = tc; }
    public boolean         isModeEditionTC()            { return modeEditionTC; }

    // Sessions
    public List<SessionUtilisateur> getSessions()       { return sessions; }
    public SessionUtilisateur getSessionSelectionnee()  { return sessionSelectionnee; }
    public long               getNbSessionsActives()    { return nbSessionsActives; }

    // Onglet
    public String  getOngletActif()                     { return ongletActif; }
    public void    setOngletActif(String v)             { this.ongletActif = v; }
}