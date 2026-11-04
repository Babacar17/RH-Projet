package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import com.soutenance.notification.ModeleNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EmployeUserService {

    @Autowired
    private EmployeDao employeDao;

    @Autowired
    private DemandeCongeDao demandeCongeDao;

    @Autowired
    private MouvementCarriereDao mouvementCarriereDao;

    @Autowired
    private FormationDao formationDao;

    @Autowired
    private EvaluationDao evaluationDao;

    @Autowired
    private NotificationRHService notificationRHService;

    @Autowired
    private BulletinPaieDao bulletinPaieDao;

    @Autowired
    private BulletinPaiePdfService bulletinPaiePdfService;

    // ===================== EMPLOYE =====================

    /**
     * Trouve l'employé lié à un compte utilisateur (par username ou email)
     */
    @Transactional(readOnly = true)
    public Employe findEmployeByUsername(String username) {
        try {
            return employeDao.findByUsernameOrEmail(username, username);
        } catch (Exception e) {
            System.out.println("EmployeUserService.findEmployeByUsername error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Met à jour les informations personnelles modifiables par l'employé
     */
    public void mettreAJourProfil(Employe employe) {
        // Note : setDateModification retiré car non présent dans l'entité Employe
        // Si votre entité a ce champ, décommentez : employe.setDateModification(java.time.LocalDate.now());
        employeDao.update(employe);
    }

    // ===================== CONGÉS =====================

    @Transactional(readOnly = true)
    public List<DemandeConge> getDemandesCongeByEmploye(Long employeId) {
        return demandeCongeDao.findByEmployeId(employeId);
    }

    public void soumettreDemandeConge(DemandeConge demande) {
        demandeCongeDao.save(demande);
        Employe employe = demande.getEmploye();
        if (employe != null && employe.getInstitution() != null) {
            notificationRHService.notifierRH(employe.getInstitution().getId(), ModeleNotification.CONGE_DEMANDE,
                "DEMANDE_CONGE", demande.getId(), employe.getNomComplet(), demande.getNombreJours());
        }
    }

    public void notifierAnnulationConge(DemandeConge demande) {
        Employe employe = demande.getEmploye();
        if (employe != null && employe.getInstitution() != null) {
            notificationRHService.notifierRH(employe.getInstitution().getId(), ModeleNotification.CONGE_ANNULE,
                "DEMANDE_CONGE", demande.getId(), employe.getNomComplet(), demande.getNombreJours());
        }
    }

    public void mettreAJourDemandeConge(DemandeConge demande) {
        demandeCongeDao.update(demande);
    }

    // ===================== CARRIÈRE =====================

    @Transactional(readOnly = true)
    public List<MouvementCarriere> getMouvementsCarriereByEmploye(Long employeId) {
        return mouvementCarriereDao.findByEmployeId(employeId);
    }

    // ===================== FORMATIONS =====================

    @Transactional(readOnly = true)
    public List<Formation> getFormationsByEmploye(Long employeId) {
        return formationDao.findByEmployeId(employeId);
    }

    // ===================== ÉVALUATIONS =====================

    @Transactional(readOnly = true)
    public List<Evaluation> getEvaluationsByEmploye(Long employeId) {
        return evaluationDao.findByEmployeId(employeId);
    }

    // ===================== BULLETINS DE PAIE =====================

    /**
     * Bulletins de paie visibles par l'employé : uniquement ceux déjà validés
     * par le RH (VALIDE ou PAYE) — les brouillons/en cours de calcul restent internes.
     */
    @Transactional(readOnly = true)
    public List<BulletinPaie> getBulletinsByEmploye(Long employeId) {
        return bulletinPaieDao.findByEmploye(employeId).stream()
            .filter(b -> "VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut()))
            .toList();
    }

    /**
     * Génère le PDF d'un bulletin, uniquement si celui-ci appartient bien à
     * l'employé demandeur et qu'il a déjà été validé par le RH.
     */
    @Transactional(readOnly = true)
    public byte[] genererBulletinPdf(Long bulletinId, Long employeId) {
        BulletinPaie b = bulletinPaieDao.findById(bulletinId);
        if (b == null || b.getEmploye() == null || !b.getEmploye().getId().equals(employeId)) {
            return new byte[0];
        }
        if (!"VALIDE".equals(b.getStatut()) && !"PAYE".equals(b.getStatut())) {
            return new byte[0];
        }
        return bulletinPaiePdfService.genererBulletinPdf(b);
    }
}