package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.PlanningPresence;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO dédié à l'entité PlanningPresence.
 *
 * CORRECTION : le service appelait presenceDao.save(PlanningPresence) et
 * presenceDao.update(PlanningPresence), ce qui est un type incompatible car
 * PresenceDao ne gère que des Presence.
 * La solution propre est un DAO séparé — à injecter dans les services concernés
 * (PresenceService, AdminPresenceService) via @Autowired PlanningPresenceDao.
 */
@Repository
public class PlanningPresenceDao {

    private static final Logger LOG = LoggerFactory.getLogger(PlanningPresenceDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public PlanningPresence findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(PlanningPresence.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Tous les plannings, tous statuts. */
    public List<PlanningPresence> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PlanningPresence p ORDER BY p.dateDebut DESC",
                PlanningPresence.class).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Plannings d'un employé donné (tous statuts). */
    public List<PlanningPresence> findByEmploye(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PlanningPresence p " +
                "WHERE p.employe.id = :empId " +
                "ORDER BY p.dateDebut DESC",
                PlanningPresence.class)
                .setParameter("empId", employeId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ══════════════════════════════════════════════════
    // CORRECTION : méthode manquante appelée par PresenceService.getPlannings()
    // ══════════════════════════════════════════════════

    /**
     * Tous les plannings (tous statuts) des employés d'une institution.
     * Utilisé par PresenceService.getPlannings(Long institutionId).
     */
    public List<PlanningPresence> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PlanningPresence p " +
                "JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "ORDER BY p.dateDebut DESC",
                PlanningPresence.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Planning actif d'un employé à une date donnée.
     * Retourne le premier planning dont la période couvre la date cible
     * et dont le statut est ACTIF.
     */
    public PlanningPresence findActifByEmployeAndDate(Long employeId, LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PlanningPresence p " +
                "WHERE p.employe.id = :empId " +
                "AND p.statut = 'ACTIF' " +
                "AND p.dateDebut <= :date " +
                "AND p.dateFin   >= :date " +
                "ORDER BY p.dateDebut DESC",
                PlanningPresence.class)
                .setParameter("empId", employeId)
                .setParameter("date", date)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Tous les plannings actifs d'une institution sur une période. */
    public List<PlanningPresence> findActifsByInstitutionAndPeriode(
            Long institutionId, LocalDate debut, LocalDate fin) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PlanningPresence p " +
                "JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND p.statut = 'ACTIF' " +
                "AND p.dateDebut <= :fin " +
                "AND p.dateFin   >= :debut " +
                "ORDER BY p.dateDebut ASC",
                PlanningPresence.class)
                .setParameter("iid",   institutionId)
                .setParameter("debut", debut)
                .setParameter("fin",   fin)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ══════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════

    public void save(PlanningPresence p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(PlanningPresence p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            PlanningPresence p = session.get(PlanningPresence.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}