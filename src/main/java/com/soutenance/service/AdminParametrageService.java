package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Corrections appliquées :
 *  1. Parametre           → ParametreSysteme  (renommage entité + import)
 *  2. List<Parametre>     → List<ParametreSysteme>
 *  3. existsByGleAndInstitution() → existsByCleAndInstitution() (typo corrigée dans ParametreDao)
 *  4. Workflow            → WorkflowValidation (renommage entité + import)
 *  5. List<Workflow>      → List<WorkflowValidation>
 *  6. workflow.getNom()   → workflow.getTypeObjet()  (WorkflowValidation n'a pas de champ nom)
 *  7. workflow.setCreePar()  → workflow.setCreePar() (présent dans WorkflowValidation ✓)
 *  8. w.setStatut(statut) → w.setActif(bool)  (WorkflowValidation utilise un boolean, pas un String statut)
 */
@Service
@Transactional
public class AdminParametrageService {

    @Autowired private ParametreDao          parametreDao;
    @Autowired private TypeCongeDao          typeCongeDao;
    @Autowired private WorkflowDao           workflowDao;
    @Autowired private EtapeWorkflowDao      etapeDao;
    @Autowired private SessionUtilisateurDao sessionDao;
    @Autowired private InstitutionDao        institutionDao;
    @Autowired private LogActionDao          logActionDao;

    // ══════════════════════════════════════════════════
    // PARAMÈTRES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    // CORRECTION 1 & 2 : Parametre → ParametreSysteme
    public List<ParametreSysteme> getParametresByInstitution(Long institutionId) {
        return parametreDao.findByInstitution(institutionId);
    }

    // CORRECTION 1 : Parametre → ParametreSysteme dans la signature
    public String creerParametre(ParametreSysteme parametre, Long institutionId, String operateur) {
        if (parametre.getCle() == null || parametre.getCle().isBlank()) {
            return "La clé du paramètre est obligatoire.";
        }
        // CORRECTION 3 : existsByGleAndInstitution (typo) → existsByCleAndInstitution
        if (parametreDao.existsByCleAndInstitution(parametre.getCle(), institutionId)) {
            return "Un paramètre avec la clé « " + parametre.getCle() + " » existe déjà.";
        }
        Institution institution = institutionDao.findById(institutionId);
        parametre.setInstitution(institution);
        parametreDao.save(parametre);
        logActionDao.save(LogAction.of(operateur, "CREATION_PARAMETRE",
            "Paramètre créé : " + parametre.getCle()));
        return null;
    }

    // CORRECTION 1 : Parametre → ParametreSysteme dans la signature
    public void modifierParametre(ParametreSysteme parametre, String operateur) {
        parametre.setModifiePar(operateur);
        parametreDao.update(parametre);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_PARAMETRE",
            "Paramètre modifié : " + parametre.getCle() + " = " + parametre.getValeur()));
    }

    public void supprimerParametre(Long id, String operateur) {
        parametreDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_PARAMETRE",
            "Paramètre supprimé : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // TYPES DE CONGÉ
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<TypeConge> getTypesCongeByInstitution(Long institutionId) {
        return typeCongeDao.findByInstitution(institutionId);
    }

    public String creerTypeConge(TypeConge typeConge, Long institutionId, String operateur) {
        if (typeConge.getLibelle() == null || typeConge.getLibelle().isBlank()) {
            return "Le libellé du type de congé est obligatoire.";
        }
        Institution institution = institutionDao.findById(institutionId);
        typeConge.setInstitution(institution);
        typeCongeDao.save(typeConge);
        logActionDao.save(LogAction.of(operateur, "CREATION_TYPE_CONGE",
            "Type de congé créé : " + typeConge.getLibelle()));
        return null;
    }

    public void modifierTypeConge(TypeConge typeConge, String operateur) {
        typeCongeDao.update(typeConge);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_TYPE_CONGE",
            "Type de congé modifié : " + typeConge.getLibelle()));
    }

    public String supprimerTypeConge(Long id, String operateur) {
        long nb = typeCongeDao.countUsages(id);
        if (nb > 0) {
            return "Ce type de congé est référencé dans " + nb + " demande(s). Désactivez-le plutôt.";
        }
        typeCongeDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_TYPE_CONGE",
            "Type de congé supprimé : id=" + id));
        return null;
    }

    // ══════════════════════════════════════════════════
    // WORKFLOWS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    // CORRECTION 4 & 5 : Workflow → WorkflowValidation
    public List<WorkflowValidation> getWorkflowsByInstitution(Long institutionId) {
        return workflowDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    // CORRECTION 4 : Workflow → WorkflowValidation
    public WorkflowValidation getWorkflowById(Long id) {
        return workflowDao.findById(id);
    }

    // CORRECTION 4 : Workflow → WorkflowValidation dans la signature
    public String creerWorkflow(WorkflowValidation workflow, Long institutionId, String operateur) {
        // CORRECTION 6 : getNom() → getTypeObjet() (WorkflowValidation n'a pas de champ nom)
        if (workflow.getTypeObjet() == null || workflow.getTypeObjet().isBlank()) {
            return "Le type d'objet du workflow est obligatoire.";
        }
        Institution institution = institutionDao.findById(institutionId);
        workflow.setInstitution(institution);
        workflow.setCreePar(operateur);
        workflowDao.save(workflow);
        logActionDao.save(LogAction.of(operateur, "CREATION_WORKFLOW",
            "Workflow créé : " + workflow.getTypeObjet()));   // CORRECTION 6
        return null;
    }

    // CORRECTION 4 : Workflow → WorkflowValidation dans la signature
    public void modifierWorkflow(WorkflowValidation workflow, String operateur) {
        workflowDao.update(workflow);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_WORKFLOW",
            "Workflow modifié : " + workflow.getTypeObjet())); // CORRECTION 6
    }

    // CORRECTION 8 : setStatut(String) → setActif(boolean)
    // WorkflowValidation n'a pas de champ statut String — il utilise un boolean actif.
    public void changerStatutWorkflow(Long id, boolean actif, String operateur) {
        WorkflowValidation w = workflowDao.findById(id);
        if (w != null) {
            w.setActif(actif);
            workflowDao.update(w);
            logActionDao.save(LogAction.of(operateur, "STATUT_WORKFLOW",
                "Workflow " + (actif ? "activé" : "désactivé") + " : " + w.getTypeObjet()));
        }
    }

    public void supprimerWorkflow(Long id, String operateur) {
        workflowDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_WORKFLOW",
            "Workflow supprimé : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // ÉTAPES DE WORKFLOW
    // ══════════════════════════════════════════════════

    public void creerEtape(EtapeValidation etape, String operateur) {
        etapeDao.save(etape);
        logActionDao.save(LogAction.of(operateur, "CREATION_ETAPE",
            "Étape créée : " + etape.getNom()));
    }

    public void modifierEtape(EtapeValidation etape, String operateur) {
        etapeDao.update(etape);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_ETAPE",
            "Étape modifiée : " + etape.getNom()));
    }

    public void supprimerEtape(Long id, String operateur) {
        etapeDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_ETAPE",
            "Étape supprimée : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // SESSIONS UTILISATEUR
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<SessionUtilisateur> getSessionsActives(Long institutionId) {
        return sessionDao.findActivesParInstitution(institutionId);
    }

    public void revoquerSession(Long sessionId, String motif, String operateur) {
        SessionUtilisateur s = sessionDao.findById(sessionId);
        if (s != null) {
            s.revoquer(motif);
            sessionDao.update(s);
            String username = s.getUser() != null ? s.getUser().getUsername() : "inconnu";
            logActionDao.save(LogAction.of(operateur, "REVOCATION_SESSION",
                "Session révoquée : " + username + " — " + motif));
        }
    }

    public int revoquerSessionsUtilisateur(Long userId, String operateur) {
        List<SessionUtilisateur> sessions = sessionDao.findActivesParUser(userId);
        for (SessionUtilisateur s : sessions) {
            s.revoquer("Révocation par admin");
            sessionDao.update(s);
        }
        if (!sessions.isEmpty()) {
            logActionDao.save(LogAction.of(operateur, "REVOCATION_SESSIONS_USER",
                sessions.size() + " session(s) révoquée(s) pour userId=" + userId));
        }
        return sessions.size();
    }
}