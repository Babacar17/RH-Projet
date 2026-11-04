package com.soutenance.service;

import com.soutenance.dao.InstitutionDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.Institution;
import com.soutenance.entity.LogAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de gestion des institutions.
 *
 * Accessible uniquement au super-admin.
 * Toutes les opérations sont tracées dans les logs.
 */
@Service
@Transactional
public class InstitutionService {

    @Autowired private InstitutionDao institutionDao;
    @Autowired private LogActionDao   logActionDao;

    // ── Lecture ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Institution> getToutesLesInstitutions() {
        return institutionDao.findAll();
    }

    @Transactional(readOnly = true)
    public List<Institution> getInstitutionsActives() {
        return institutionDao.findActives();
    }

    @Transactional(readOnly = true)
    public List<Institution> rechercher(String terme) {
        if (terme == null || terme.isBlank()) return institutionDao.findAll();
        return institutionDao.search(terme.trim());
    }

    @Transactional(readOnly = true)
    public Institution getById(Long id) {
        return institutionDao.findById(id);
    }

    @Transactional(readOnly = true)
    public Institution getByCode(String code) {
        return institutionDao.findByCode(code);
    }

    // ── Statistiques dashboard super-admin ─────────────────────────────────

    @Transactional(readOnly = true)
    public long countTotal()         { return institutionDao.countTotal(); }

    @Transactional(readOnly = true)
    public long countActives()       { return institutionDao.countByStatut("ACTIVE"); }

    @Transactional(readOnly = true)
    public long countEnConfig()      { return institutionDao.countByStatut("CONFIG"); }

    @Transactional(readOnly = true)
    public long countEmployesByInstitution(Long id) {
        return institutionDao.countEmployesByInstitution(id);
    }

    @Transactional(readOnly = true)
    public long countUsersByInstitution(Long id) {
        return institutionDao.countUsersByInstitution(id);
    }

    // ── Création ───────────────────────────────────────────────────────────

    /**
     * Crée une nouvelle institution.
     *
     * @param institution   objet institution à persister
     * @param superAdmin    username du super-admin qui crée l'institution
     * @return null si succès, message d'erreur sinon
     */
    public String creer(Institution institution, String superAdmin) {
        if (institution.getNom() == null || institution.getNom().isBlank()) {
            return "Le nom de l'institution est obligatoire.";
        }
        if (institution.getCode() == null || institution.getCode().isBlank()) {
            return "Le code / sigle est obligatoire.";
        }
        institution.setCode(institution.getCode().toUpperCase());
        if (institutionDao.findByCode(institution.getCode()) != null) {
            return "Une institution avec le code « " + institution.getCode() + " » existe déjà.";
        }
        institution.setCreePar(superAdmin);
        institutionDao.save(institution);
        logActionDao.save(new LogAction(
            superAdmin,
            "CREATION_INSTITUTION",
            "Institution créée : " + institution.getNom() + " (" + institution.getCode() + ")"
        ));
        return null;
    }

    // ── Modification ───────────────────────────────────────────────────────

    /**
     * Modifie une institution existante.
     *
     * @return null si succès, message d'erreur sinon
     */
    public String modifier(Institution institution, String superAdmin) {
        Institution existante = institutionDao.findById(institution.getId());
        if (existante == null) {
            return "Institution introuvable.";
        }
        existante.setNom(institution.getNom());
        existante.setCode(institution.getCode().toUpperCase());
        existante.setDescription(institution.getDescription());
        existante.setAdresse(institution.getAdresse());
        existante.setVille(institution.getVille());
        existante.setPays(institution.getPays());
        existante.setTelephone(institution.getTelephone());
        existante.setEmail(institution.getEmail());
        existante.setSiteWeb(institution.getSiteWeb());
        existante.setTypeHierarchie(institution.getTypeHierarchie());
        existante.setStatut(institution.getStatut());
        institutionDao.update(existante);
        logActionDao.save(new LogAction(
            superAdmin,
            "MODIFICATION_INSTITUTION",
            "Institution modifiée : " + existante.getNom()
        ));
        return null;
    }

    // ── Activation / Désactivation ─────────────────────────────────────────

    public void activerInstitution(Long id, String superAdmin) {
        Institution inst = institutionDao.findById(id);
        if (inst != null) {
            inst.setStatut("ACTIVE");
            institutionDao.update(inst);
            logActionDao.save(new LogAction(superAdmin, "ACTIVATION_INSTITUTION",
                "Institution activée : " + inst.getNom()));
        }
    }

    public void desactiverInstitution(Long id, String superAdmin) {
        Institution inst = institutionDao.findById(id);
        if (inst != null) {
            inst.setStatut("INACTIVE");
            institutionDao.update(inst);
            logActionDao.save(new LogAction(superAdmin, "DESACTIVATION_INSTITUTION",
                "Institution désactivée : " + inst.getNom()));
        }
    }

    // ── Suppression ────────────────────────────────────────────────────────

    /**
     * Supprime une institution uniquement si elle ne contient aucun utilisateur
     * ni employé (sécurité métier).
     *
     * @return null si succès, message d'erreur sinon
     */
    public String supprimer(Long id, String superAdmin) {
        Institution inst = institutionDao.findById(id);
        if (inst == null) return "Institution introuvable.";

        long nbEmp = institutionDao.countEmployesByInstitution(id);
        long nbUsr = institutionDao.countUsersByInstitution(id);

        if (nbEmp > 0 || nbUsr > 0) {
            return "Impossible de supprimer une institution contenant des utilisateurs ou des employés. "
                 + "(" + nbEmp + " employés, " + nbUsr + " utilisateurs)";
        }

        String nom = inst.getNom();
        institutionDao.delete(id);
        logActionDao.save(new LogAction(superAdmin, "SUPPRESSION_INSTITUTION",
            "Institution supprimée : " + nom));
        return null;
    }
}