package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de configuration de l'institution (accessible via l'onglet Configuration).
 *
 * Couvre :
 *  - Paramètres globaux (lecture / modification)
 *  - Types de congé par institution (CRUD, toggle actif)
 *  - Jours fériés par institution et par année (CRUD)
 *  - Workflows de validation (CRUD, toggle actif)
 *
 * Corrections appliquées :
 *  1. Parametre          → ParametreSysteme  (renommage entité)
 *  2. Workflow           → WorkflowValidation (renommage entité)
 *  3. workflow.getNom()  → workflow.getTypeObjet() (WorkflowValidation n'a pas de champ nom)
 *  4. setInstitutionId() → setInstitution()  sur TypeConge, JourFerie, WorkflowValidation
 *     (les entités exposent une relation @ManyToOne, pas un Long brut)
 */
@Service
@Transactional
public class ConfigurationService {

    @Autowired private ParametreDao      parametreDao;
    @Autowired private TypeCongeDao      typeCongeDao;
    @Autowired private JourFerieDao      jourFerieDao;
    @Autowired private WorkflowDao       workflowDao;
    @Autowired private LogActionDao      logActionDao;
    // CORRECTION 4 : nécessaire pour charger l'objet Institution avant setInstitution()
    @Autowired private InstitutionDao    institutionDao;

    // ══════════════════════════════════════════════════
    // PARAMÈTRES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    // CORRECTION 1 : List<Parametre> → List<ParametreSysteme>
    public List<ParametreSysteme> getTousParametres() {
        return parametreDao.findAll();
    }

    // CORRECTION 1 : Parametre → ParametreSysteme dans la signature
    public void modifierParametre(ParametreSysteme parametre, String operateur) {
        parametreDao.update(parametre);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_PARAMETRE",
            "Paramètre modifié : " + parametre.getCle() + " = " + parametre.getValeur()));
    }

    // ══════════════════════════════════════════════════
    // TYPES DE CONGÉ
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<TypeConge> getTousTypesConge(Long institutionId) {
        return typeCongeDao.findByInstitution(institutionId);
    }

    public void creerTypeConge(TypeConge typeConge, Long institutionId, String operateur) {
        // CORRECTION 4 : TypeConge.institution est un @ManyToOne — on charge l'objet avant de l'affecter
        Institution institution = institutionDao.findById(institutionId);
        typeConge.setInstitution(institution);
        typeCongeDao.save(typeConge);
        logActionDao.save(LogAction.of(operateur, "CREATION_TYPE_CONGE",
            "Type de congé créé : " + typeConge.getLibelle()));
    }

    public void modifierTypeConge(TypeConge typeConge, String operateur) {
        typeCongeDao.update(typeConge);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_TYPE_CONGE",
            "Type de congé modifié : " + typeConge.getLibelle()));
    }

    /**
     * Supprime un type de congé si aucune demande ne l'utilise.
     *
     * @return null si succès, message d'erreur sinon
     */
    public String supprimerTypeConge(Long id, String operateur) {
        long nbUsages = typeCongeDao.countUsages(id);
        if (nbUsages > 0) {
            return "Ce type de congé est utilisé dans " + nbUsages + " demande(s). Désactivez-le plutôt.";
        }
        typeCongeDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_TYPE_CONGE",
            "Type de congé supprimé : id=" + id));
        return null;
    }

    public void toggleActifTypeConge(Long id, String operateur) {
        TypeConge tc = typeCongeDao.findById(id);
        if (tc != null) {
            tc.setActif(!tc.isActif());
            typeCongeDao.update(tc);
            logActionDao.save(LogAction.of(operateur, "TOGGLE_TYPE_CONGE",
                "Type de congé " + (tc.isActif() ? "activé" : "désactivé") + " : " + tc.getLibelle()));
        }
    }

    // ══════════════════════════════════════════════════
    // JOURS FÉRIÉS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<JourFerie> getJoursFeries(int annee, Long institutionId) {
        return jourFerieDao.findByAnneeAndInstitution(annee, institutionId);
    }

    public void creerJourFerie(JourFerie jourFerie, Long institutionId, String operateur) {
        // CORRECTION 4 : JourFerie.institution est un @ManyToOne — même pattern que TypeConge
        Institution institution = institutionDao.findById(institutionId);
        jourFerie.setInstitution(institution);
        jourFerieDao.save(jourFerie);
        logActionDao.save(LogAction.of(operateur, "CREATION_JOUR_FERIE",
            "Jour férié créé : " + jourFerie.getLibelle() + " (" + jourFerie.getDate() + ")"));
    }

    public void modifierJourFerie(JourFerie jourFerie, String operateur) {
        jourFerieDao.update(jourFerie);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_JOUR_FERIE",
            "Jour férié modifié : " + jourFerie.getLibelle()));
    }

    public void supprimerJourFerie(Long id, String operateur) {
        JourFerie jf = jourFerieDao.findById(id);
        if (jf != null) {
            logActionDao.save(LogAction.of(operateur, "SUPPRESSION_JOUR_FERIE",
                "Jour férié supprimé : " + jf.getLibelle()));
            jourFerieDao.delete(id);
        }
    }

    // ══════════════════════════════════════════════════
    // WORKFLOWS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    // CORRECTION 2 : List<Workflow> → List<WorkflowValidation>
    public List<WorkflowValidation> getTousWorkflows(Long institutionId) {
        return workflowDao.findByInstitution(institutionId);
    }

    // CORRECTION 2 : Workflow → WorkflowValidation dans la signature
    public void creerWorkflow(WorkflowValidation workflow, Long institutionId, String operateur) {
        // CORRECTION 4 : WorkflowValidation.institution est un @ManyToOne
        Institution institution = institutionDao.findById(institutionId);
        workflow.setInstitution(institution);
        workflowDao.save(workflow);
        // CORRECTION 3 : getNom() remplacé par getTypeObjet() (WorkflowValidation n'a pas de champ nom)
        logActionDao.save(LogAction.of(operateur, "CREATION_WORKFLOW",
            "Workflow créé : " + workflow.getTypeObjet()));
    }

    // CORRECTION 2 : Workflow → WorkflowValidation dans la signature
    public void modifierWorkflow(WorkflowValidation workflow, String operateur) {
        workflowDao.update(workflow);
        // CORRECTION 3 : getNom() → getTypeObjet()
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_WORKFLOW",
            "Workflow modifié : " + workflow.getTypeObjet()));
    }

    public void toggleActifWorkflow(Long id, String operateur) {
        // CORRECTION 2 : Workflow → WorkflowValidation dans la variable locale
        WorkflowValidation w = workflowDao.findById(id);
        if (w != null) {
            w.setActif(!w.isActif());
            workflowDao.update(w);
            // CORRECTION 3 : getNom() → getTypeObjet()
            logActionDao.save(LogAction.of(operateur, "TOGGLE_WORKFLOW",
                "Workflow " + (w.isActif() ? "activé" : "désactivé") + " : " + w.getTypeObjet()));
        }
    }
}