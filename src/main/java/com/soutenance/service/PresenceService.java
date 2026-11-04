package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service RH de gestion des présences, absences et plannings.
 *
 * Couvre :
 *  - Validation / refus / annulation des demandes de congé
 *  - Plannings de présence multi-employés
 *  - Vue calendrier des congés approuvés
 *  - Soldes de congés par employé
 *  - Statistiques consolidées (taux présence, absentéisme)
 */
@Service
@Transactional
public class PresenceService {

    @Autowired private DemandeCongeDao      demandeCongeDao;

    // CORRECTION 1 : était "PresenceDao planningDao" — type incompatible avec PlanningPresence.
    // Remplacé par le DAO dédié PlanningPresenceDao.
    @Autowired private PlanningPresenceDao  planningDao;

    @Autowired private EmployeDao           employeDao;
    @Autowired private LogActionDao         logActionDao;

    // ══════════════════════════════════════════════════
    // DEMANDES DE CONGÉ
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<DemandeConge> getToutesDemandesConge(Long institutionId) {
        return demandeCongeDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public List<DemandeConge> getDemandesEnAttente(Long institutionId) {
        return demandeCongeDao.findByStatutAndInstitution("EN_ATTENTE", institutionId);
    }

    @Transactional(readOnly = true)
    public DemandeConge getDemandeById(Long id) {
        return demandeCongeDao.findById(id);
    }

    public void approuverConge(Long id, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d != null) {
            d.setStatut("APPROUVE");
            d.setTraitePar(operateur);
            d.setDateTraitement(LocalDateTime.now());
            demandeCongeDao.update(d);
            logActionDao.save(LogAction.of(operateur, "APPROBATION_CONGE",
                "Congé approuvé : " + d.getEmploye().getNomComplet()
                + " (" + d.getNombreJours() + "j)"));
        }
    }

    public void refuserConge(Long id, String motif, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d != null) {
            d.setStatut("REFUSE");
            d.setCommentaireRH(motif);
            d.setTraitePar(operateur);
            d.setDateTraitement(LocalDateTime.now());
            demandeCongeDao.update(d);
            logActionDao.save(LogAction.of(operateur, "REFUS_CONGE",
                "Congé refusé : " + d.getEmploye().getNomComplet() + " — " + motif));
        }
    }

    public void annulerCongeApprouve(Long id, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d != null && "APPROUVE".equals(d.getStatut())) {
            d.setStatut("ANNULE");
            d.setTraitePar(operateur);
            d.setDateTraitement(LocalDateTime.now());
            demandeCongeDao.update(d);
            logActionDao.save(LogAction.of(operateur, "ANNULATION_CONGE",
                "Congé annulé : " + d.getEmploye().getNomComplet()));
        }
    }

    // ══════════════════════════════════════════════════
    // SOLDES DE CONGÉS
    // ══════════════════════════════════════════════════

    /**
     * Retourne le solde de congés restant d'un employé (en jours).
     * Calcul simplifié : quota annuel (30j) - jours approuvés en cours d'année.
     *
     * CORRECTION 2 : countJoursApprouvesCetteAnnee() retourne un long.
     * Cast explicite en int après le calcul pour éviter le type mismatch.
     */
    @Transactional(readOnly = true)
    public int getSoldeConges(Long employeId) {
        int quotaAnnuel = 30;
        int joursUtilises = (int) demandeCongeDao.countJoursApprouvesCetteAnnee(employeId);
        return Math.max(0, quotaAnnuel - joursUtilises);
    }

    /** Jours de congé pris (approuvés) cette année pour l'employé */
    public int getJoursCongesPris(Long employeId) {
        if (employeId == null) return 0;
        try {
            return (int) demandeCongeDao.countJoursApprouvesCetteAnnee(employeId);
        } catch (Exception e) { return 0; }
    }

    /** Report de l'année N-1 (0 par défaut — à implémenter selon règles métier) */
    public int getReportAnnee(Long employeId) {
        // TODO : charger depuis une table de report si elle existe
        return 0;
    }

    // ══════════════════════════════════════════════════
    // CALENDRIER
    // ══════════════════════════════════════════════════

    /**
     * Construit une map date ISO -> liste de congés approuvés ce jour.
     */
    @Transactional(readOnly = true)
    public Map<String, List<DemandeConge>> getCalendrierConges(
            LocalDate debut, LocalDate fin, Long institutionId) {
        List<DemandeConge> conges = demandeCongeDao
            .findApprouvesParPeriodeAndInstitution(debut, fin, institutionId);
        Map<String, List<DemandeConge>> calendrier = new LinkedHashMap<>();
        LocalDate jour = debut;
        while (!jour.isAfter(fin)) {
            final LocalDate j = jour;
            List<DemandeConge> duJour = conges.stream()
                .filter(c -> c.getDateDebut() != null && c.getDateFin() != null
                    && !j.isBefore(c.getDateDebut()) && !j.isAfter(c.getDateFin()))
                .collect(Collectors.toList());
            if (!duJour.isEmpty()) {
                calendrier.put(j.toString(), duJour);
            }
            jour = jour.plusDays(1);
        }
        return calendrier;
    }

    // ══════════════════════════════════════════════════
    // PLANNINGS
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION 3 : planningDao est maintenant PlanningPresenceDao,
     * donc findByInstitution() retourne bien List<PlanningPresence>.
     */
    @Transactional(readOnly = true)
    public List<PlanningPresence> getPlannings(Long institutionId) {
        return planningDao.findByInstitution(institutionId);
    }

    public void creerPlanning(PlanningPresence planning, String operateur) {
        planning.setCreePar(operateur);
        planningDao.save(planning);
        logActionDao.save(LogAction.of(operateur, "CREATION_PLANNING",
            "Planning créé : " + planning.getDateDebut() + " → " + planning.getDateFin()));
    }

    public void modifierPlanning(PlanningPresence planning, String operateur) {
        planningDao.update(planning);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_PLANNING",
            "Planning modifié : id=" + planning.getId()));
    }

    public void supprimerPlanning(Long id, String operateur) {
        planningDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_PLANNING",
            "Planning supprimé : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // STATISTIQUES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public long countEmployesEnCongeAujourdhui(Long institutionId) {
        LocalDate today = LocalDate.now();
        return demandeCongeDao.findByInstitution(institutionId).stream()
            .filter(d -> "APPROUVE".equals(d.getStatut())
                && d.getDateDebut() != null && d.getDateFin() != null
                && !today.isBefore(d.getDateDebut()) && !today.isAfter(d.getDateFin()))
            .count();
    }

    @Transactional(readOnly = true)
    public double getMoyenneJoursConge(Long institutionId) {
        List<DemandeConge> approuvees = demandeCongeDao
            .findByStatutAndInstitution("APPROUVE", institutionId);
        if (approuvees.isEmpty()) return 0.0;
        double total = approuvees.stream()
            .mapToInt(d -> d.getNombreJours() != null ? d.getNombreJours() : 0)
            .sum();
        return total / approuvees.size();
    }

    // ══════════════════════════════════════════════════
    // EMPLOYÉS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Employe> getTousEmployes(Long institutionId) {
        return employeDao.findByInstitution(institutionId);
    }
}