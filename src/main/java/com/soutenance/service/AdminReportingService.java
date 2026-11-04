package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de reporting — périmètre administrateur / super-admin.
 *
 * CORRECTION CRITIQUE : fuite multi-tenant résolue.
 *
 * AVANT : toutes les méthodes ignoraient institutionId et retournaient
 *   des données de TOUTES les institutions confondues. Un admin de
 *   l'institution A voyait les effectifs de l'institution B.
 *
 * APRÈS : chaque méthode filtre par institutionId via les méthodes
 *   findByInstitution() / findByStatutAndInstitution() déjà présentes
 *   dans les DAOs.
 *
 * Méthodes corrigées :
 *   - countEffectifTotal()        → EmployeDao.findByInstitution()
 *   - countByStatutEmploye()      → EmployeDao.findByInstitutionAndStatut()
 *   - countByTypeContrat()        → EmployeDao.findByInstitution() + filtre
 *   - countCongesEnAttente()      → DemandeCongeDao.findByStatutAndInstitution()
 *   - countCongesApprouves()      → DemandeCongeDao.findByStatutAndInstitution()
 *   - countCandidaturesRecues()   → CandidatureDao.findByInstitution() + filtre
 *   - countOffresPubliees()       → OffreEmploiDao.findByInstitution() + filtre
 *   - countEvaluationsEnCours()   → EvaluationDao filtré via employes institution
 *   - countContratsExpirant()     → EmployeDao.findByInstitution() + filtre date
 *   - getSalaireMoyen()           → EmployeDao.findActifsByInstitution()
 *   - getRepartitionParContrat()  → EmployeDao.findByInstitution()
 *   - getRepartitionParDirection()→ EmployeDao.findByInstitution()
 *   - getEvolutionConges()        → DemandeCongeDao.findByInstitution() + filtre année
 *   - getEmployesPourExport()     → EmployeDao.findByInstitution()
 */
@Service
@Transactional(readOnly = true)
public class AdminReportingService {

    @Autowired private EmployeDao       employeDao;
    @Autowired private DemandeCongeDao  demandeCongeDao;
    @Autowired private CandidatureDao   candidatureDao;
    @Autowired private OffreEmploiDao   offreEmploiDao;
    @Autowired private EvaluationDao    evaluationDao;
    @Autowired private BulletinPaieDao  bulletinDao;
    @Autowired private InstitutionDao   institutionDao;
    @Autowired private LogActionDao     logActionDao;

    // ══════════════════════════════════════════════════
    // COMPTEURS GLOBAUX (super-admin uniquement)
    // ══════════════════════════════════════════════════

    /** Nombre total d'employés toutes institutions (super-admin). */
    public long countEmployesTotal()       { return employeDao.countTotal(); }

    /** Nombre total d'employés actifs toutes institutions (super-admin). */
    public long countEmployesActifsTotal() { return employeDao.countByStatut("ACTIF"); }

    /** Nombre total d'institutions. */
    public long countInstitutions()        { return institutionDao.countTotal(); }

    /** Masse salariale globale toutes institutions (super-admin). */
    public BigDecimal getMasseSalarialeGlobale() {
        return bulletinDao.findAll().stream()
            .filter(b -> b.getSalaireNet() != null
                && ("VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut())))
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ══════════════════════════════════════════════════
    // COMPTEURS PAR INSTITUTION — CORRIGÉS
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION : utilisait countTotal() global.
     * Utilise maintenant findByInstitution() filtré.
     */
    public long countEffectifTotal(Long institutionId) {
        if (institutionId == null) return employeDao.countTotal();
        return employeDao.findByInstitution(institutionId).size();
    }

    /**
     * CORRECTION : utilisait countByStatut() global.
     * Utilise maintenant findByInstitutionAndStatut().
     */
    public long countByStatutEmploye(Long institutionId, String statut) {
        if (institutionId == null) return employeDao.countByStatut(statut);
        return employeDao.findByInstitutionAndStatut(institutionId, statut).size();
    }

    /**
     * CORRECTION : chargeait tous les employés et filtrait en mémoire sans institution.
     * Charge maintenant uniquement les employés de l'institution.
     */
    public long countByTypeContrat(Long institutionId, String typeContrat) {
        List<Employe> employes = (institutionId != null)
            ? employeDao.findByInstitution(institutionId)
            : employeDao.findAll();
        return employes.stream()
            .filter(e -> typeContrat.equals(e.getTypeContrat()))
            .count();
    }

    /**
     * CORRECTION : utilisait findByStatut() global sans institution.
     * Utilise maintenant findByStatutAndInstitution().
     */
    public long countCongesEnAttente(Long institutionId) {
        if (institutionId == null)
            return demandeCongeDao.findByStatut("EN_ATTENTE").size();
        return demandeCongeDao.findByStatutAndInstitution("EN_ATTENTE", institutionId).size();
    }

    /**
     * CORRECTION : même problème que countCongesEnAttente.
     */
    public long countCongesApprouves(Long institutionId) {
        if (institutionId == null)
            return demandeCongeDao.findByStatut("APPROUVE").size();
        return demandeCongeDao.findByStatutAndInstitution("APPROUVE", institutionId).size();
    }

    /**
     * CORRECTION : utilisait countByStatut() global.
     * Filtre maintenant par institution via findByInstitution().
     */
    public long countCandidaturesRecues(Long institutionId) {
        if (institutionId == null)
            return candidatureDao.countByStatut("RECUE");
        return candidatureDao.findByInstitution(institutionId).stream()
            .filter(c -> "RECUE".equals(c.getStatut()))
            .count();
    }

    /**
     * CORRECTION : utilisait countByStatut() global.
     * Filtre maintenant par institution.
     */
    public long countOffresPubliees(Long institutionId) {
        if (institutionId == null)
            return offreEmploiDao.countByStatut("PUBLIEE");
        return offreEmploiDao.findByInstitution(institutionId).stream()
            .filter(o -> "PUBLIEE".equals(o.getStatut()))
            .count();
    }

    /**
     * CORRECTION : utilisait countByStatut() global.
     * Compte les évaluations EN_COURS/SOUMISE pour les employés de l'institution.
     */
    public long countEvaluationsEnCours(Long institutionId) {
        if (institutionId == null)
            return evaluationDao.countByStatut("EN_COURS")
                 + evaluationDao.countByStatut("SOUMISE");
        // EvaluationDao n'a pas findByInstitution → on passe par les IDs des employés
        List<Long> employeIds = employeDao.findByInstitution(institutionId)
            .stream().map(Employe::getId).collect(Collectors.toList());
        if (employeIds.isEmpty()) return 0;
        return evaluationDao.findByEmployeIds(employeIds).stream()
            .filter(e -> "EN_COURS".equals(e.getStatut())
                      || "SOUMISE".equals(e.getStatut()))
            .count();
    }

    /**
     * CORRECTION : chargeait tous les employés sans filtre institution.
     */
    public long countContratsExpirant(Long institutionId, int jours) {
        LocalDate limite = LocalDate.now().plusDays(jours);
        List<Employe> employes = (institutionId != null)
            ? employeDao.findByInstitution(institutionId)
            : employeDao.findAll();
        return employes.stream()
            .filter(e -> e.getDateFinContrat() != null
                && !e.getDateFinContrat().isBefore(LocalDate.now())
                && !e.getDateFinContrat().isAfter(limite))
            .count();
    }

    // ══════════════════════════════════════════════════
    // INDICATEURS FINANCIERS
    // ══════════════════════════════════════════════════

    /** Masse salariale des bulletins VALIDE/PAYE d'une institution. */
    public BigDecimal getMasseSalariale(Long institutionId) {
        if (institutionId == null) return getMasseSalarialeGlobale();
        return bulletinDao.findByInstitution(institutionId).stream()
            .filter(b -> b.getSalaireNet() != null
                && ("VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut())))
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * CORRECTION : chargeait findActifs() global.
     * Utilise maintenant findActifsByInstitution().
     */
    public BigDecimal getSalaireMoyen(Long institutionId) {
        List<Employe> actifs = (institutionId != null)
            ? employeDao.findActifsByInstitution(institutionId)
            : employeDao.findActifs();
        if (actifs.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = actifs.stream()
            .filter(e -> e.getSalaireBase() != null)
            .map(Employe::getSalaireBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = actifs.stream().filter(e -> e.getSalaireBase() != null).count();
        if (count == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    // ══════════════════════════════════════════════════
    // TAUX
    // ══════════════════════════════════════════════════

    public double getTauxAbsenteismeMoisCourant(Long institutionId) {
        long total = countEffectifTotal(institutionId);
        if (total == 0) return 0.0;
        LocalDate debut = LocalDate.now().withDayOfMonth(1);
        LocalDate fin   = LocalDate.now();
        List<DemandeConge> conges = (institutionId != null)
            ? demandeCongeDao.findByStatutAndInstitution("APPROUVE", institutionId)
            : demandeCongeDao.findByStatut("APPROUVE");
        long absents = conges.stream()
            .filter(d -> d.getDateDebut() != null
                && !d.getDateDebut().isBefore(debut)
                && !d.getDateDebut().isAfter(fin))
            .count();
        return (double) absents / total * 100;
    }

    public double getTauxTurnoverAnnuel(Long institutionId) {
        long total = countEffectifTotal(institutionId);
        if (total == 0) return 0.0;
        long departs = countByStatutEmploye(institutionId, "INACTIF")
                     + countByStatutEmploye(institutionId, "RETRAITE");
        return (double) departs / total * 100;
    }

    // ══════════════════════════════════════════════════
    // RÉPARTITIONS
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION : utilisait findAll() global.
     */
    public Map<String, Long> getRepartitionParContrat(Long institutionId) {
        List<Employe> employes = (institutionId != null)
            ? employeDao.findByInstitution(institutionId)
            : employeDao.findAll();
        return employes.stream()
            .collect(Collectors.groupingBy(
                e -> e.getTypeContrat() != null ? e.getTypeContrat() : "INCONNU",
                Collectors.counting()));
    }

    /**
     * CORRECTION : utilisait findAll() global.
     */
    public Map<String, Long> getRepartitionParDirection(Long institutionId) {
        List<Employe> employes = (institutionId != null)
            ? employeDao.findByInstitution(institutionId)
            : employeDao.findAll();
        return employes.stream()
            .filter(e -> e.getServiceOrg() != null
                && e.getServiceOrg().getDivision() != null
                && e.getServiceOrg().getDivision().getDirection() != null)
            .collect(Collectors.groupingBy(
                e -> e.getServiceOrg().getDivision().getDirection().getNom(),
                Collectors.counting()));
    }

    /** Comparatif inter-institutions : nom → effectif. Super-admin uniquement. */
    public Map<String, Long> getComparatifInstitutions() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Institution inst : institutionDao.findAll()) {
            result.put(inst.getNom(), countEffectifTotal(inst.getId()));
        }
        return result;
    }

    // ══════════════════════════════════════════════════
    // ÉVOLUTION TEMPORELLE
    // ══════════════════════════════════════════════════

    /**
     * Évolution des effectifs mois par mois pour une année.
     * Valeur = effectif total de l'institution à fin de période
     * (simplifié : même valeur pour tous les mois car on n'a pas
     * l'historique des embauches par mois dans le modèle actuel).
     */
    public Map<Integer, Long> getEvolutionEffectifs(Long institutionId, int annee) {
        long base = countEffectifTotal(institutionId);
        Map<Integer, Long> m = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) m.put(i, base);
        return m;
    }

    /**
     * CORRECTION : utilisait findAll() global sans filtre institution ni année.
     * Filtre maintenant par institution et par année.
     */
    public Map<Integer, Long> getEvolutionConges(Long institutionId, int annee) {
        List<DemandeConge> conges = (institutionId != null)
            ? demandeCongeDao.findByInstitution(institutionId)
            : demandeCongeDao.findAll();
        Map<Integer, Long> m = new LinkedHashMap<>();
        for (int mois = 1; mois <= 12; mois++) {
            final int mo = mois;
            long count = conges.stream()
                .filter(d -> d.getDateDebut() != null
                    && d.getDateDebut().getYear() == annee
                    && d.getDateDebut().getMonthValue() == mo)
                .count();
            m.put(mois, count);
        }
        return m;
    }

    /** Demandes de congé d'une année donnée pour une institution — utilisé par l'export CSV. */
    public List<DemandeConge> getCongesAnnee(Long institutionId, int annee) {
        List<DemandeConge> conges = (institutionId != null)
            ? demandeCongeDao.findByInstitution(institutionId)
            : demandeCongeDao.findAll();
        return conges.stream()
            .filter(d -> d.getDateDebut() != null && d.getDateDebut().getYear() == annee)
            .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════
    // BULLETINS
    // ══════════════════════════════════════════════════

    /**
     * Bulletins d'un mois/année pour une institution.
     * Filtre fait en SQL (findByInstitutionAndPeriode) plutôt qu'en Java sur
     * b.getCyclePaie() — cycle_paie_id est chargé en LAZY, donc un filtre
     * Java après la fermeture de la session Hibernate levait une
     * LazyInitializationException.
     */
    public List<BulletinPaie> getBulletinsMoisCourant(Long institutionId, int mois, int annee) {
        if (institutionId == null) return new ArrayList<>();
        return bulletinDao.findByInstitutionAndPeriode(institutionId, mois, annee);
    }

    // ══════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION : utilisait findAll() global.
     * Retourne uniquement les employés de l'institution.
     */
    public List<Employe> getEmployesPourExport(Long institutionId) {
        if (institutionId == null) return employeDao.findAll();
        return employeDao.findByInstitution(institutionId);
    }

    // ══════════════════════════════════════════════════
    // LOGS / AUDIT
    // ══════════════════════════════════════════════════

    public List<LogAction> getLogsFiltre(String username, String action,
                                          LocalDate debut, LocalDate fin, int max) {
        return logActionDao.findAll().stream()
            .filter(l -> {
                if (username != null && !username.isBlank()
                    && (l.getUsername() == null || !l.getUsername().toLowerCase()
                        .contains(username.toLowerCase()))) return false;
                if (action != null && !action.isBlank()
                    && (l.getAction() == null || !l.getAction().toLowerCase()
                        .contains(action.toLowerCase()))) return false;
                if (debut != null && l.getDateAction() != null
                    && l.getDateAction().toLocalDate().isBefore(debut)) return false;
                if (fin != null && l.getDateAction() != null
                    && l.getDateAction().toLocalDate().isAfter(fin)) return false;
                return true;
            })
            .sorted(Comparator.comparing(LogAction::getDateAction,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(max > 0 ? max : Long.MAX_VALUE)
            .collect(Collectors.toList());
    }
}