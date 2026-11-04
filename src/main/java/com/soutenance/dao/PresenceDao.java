package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Presence;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PresenceDao {

    private static final Logger LOG = LoggerFactory.getLogger(PresenceDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Presence findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Presence.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Presence> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Presence> cq = cb.createQuery(Presence.class);
            Root<Presence> root = cq.from(Presence.class);
            cq.select(root).orderBy(cb.desc(root.get("datePresence")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Présences d'un employé sur une période donnée. */
    public List<Presence> findByEmployeAndPeriode(Long employeId, LocalDate debut, LocalDate fin) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Presence> cq = cb.createQuery(Presence.class);
            Root<Presence> root = cq.from(Presence.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("employe").get("id"), employeId),
                  cb.greaterThanOrEqualTo(root.get("datePresence"), debut),
                  cb.lessThanOrEqualTo(root.get("datePresence"), fin)
              ))
              .orderBy(cb.asc(root.get("datePresence")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Somme des heures supplémentaires validées d'un employé sur une période (pour la paie). */
    public double sumHeuresSupplementairesValidees(Long employeId, LocalDate debut, LocalDate fin) {
        try (Session session = sessionFactory.openSession()) {
            Double result = session.createQuery(
                "SELECT SUM(p.heuresSupplementaires) FROM Presence p " +
                "WHERE p.employe.id = :eid " +
                "AND p.datePresence >= :debut AND p.datePresence <= :fin " +
                "AND p.estValide = true " +
                "AND p.heuresSupplementaires IS NOT NULL",
                Double.class)
                .setParameter("eid", employeId)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .uniqueResult();
            return result != null ? result : 0.0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0.0; }
    }

    /** Toutes les présences d'une institution sur une période (via employé → institution). */
    public List<Presence> findByPeriodeAndInstitution(Long institutionId, LocalDate debut, LocalDate fin) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM Presence p " +
                "JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND p.datePresence >= :debut " +
                "AND p.datePresence <= :fin " +
                "ORDER BY p.datePresence DESC, e.nom ASC",
                Presence.class)
                .setParameter("iid", institutionId)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Présences pour une date précise et une institution (pour validation en masse). */
    public List<Presence> findByDateAndInstitution(Long institutionId, LocalDate date) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM Presence p JOIN p.employe e " +
                "WHERE e.institution.id = :iid AND p.datePresence = :date",
                Presence.class)
                .setParameter("iid", institutionId)
                .setParameter("date", date)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Présences non validées d'une institution sur une période. */
    public List<Presence> findNonValideesByInstitution(Long institutionId, LocalDate debut, LocalDate fin) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM Presence p JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND p.datePresence >= :debut AND p.datePresence <= :fin " +
                "AND p.estValide = false " +
                "ORDER BY p.datePresence ASC",
                Presence.class)
                .setParameter("iid", institutionId)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ══════════════════════════════════════════════════
    // CORRECTION 3 : méthode manquante appelée par le service présence
    // ══════════════════════════════════════════════════

    /**
     * Retourne toutes les présences (tous statuts, toutes dates)
     * des employés appartenant à une institution donnée.
     * Utilisé par PresenceService / AdminPresenceService.
     */
    public List<Presence> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM Presence p JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "ORDER BY p.datePresence DESC, e.nom ASC",
                Presence.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Compte les présents aujourd'hui pour une institution. */
    public long countPresentsAujourdhui(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(p) FROM Presence p JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND p.datePresence = :today " +
                "AND p.type = 'PRESENT'",
                Long.class)
                .setParameter("iid", institutionId)
                .setParameter("today", LocalDate.now())
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    /** Compte les absents aujourd'hui pour une institution. */
    public long countAbsentsAujourdhui(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(p) FROM Presence p JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND p.datePresence = :today " +
                "AND p.type = 'ABSENT'",
                Long.class)
                .setParameter("iid", institutionId)
                .setParameter("today", LocalDate.now())
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    /** Compte les retards sur une période pour une institution. */
    public long countRetardsParPeriode(Long institutionId, LocalDate debut, LocalDate fin) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(p) FROM Presence p JOIN p.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND p.datePresence >= :debut AND p.datePresence <= :fin " +
                "AND p.retardMinutes > 0",
                Long.class)
                .setParameter("iid", institutionId)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    /**
     * Marque une présence comme validée et persiste la modification.
     * Note : le validePar doit être défini sur l'entité AVANT d'appeler
     * cette méthode (presence.setValidePar(operateur)).
     */
    public void setDateValidation(Presence presence, LocalDate date) {
        presence.setEstValide(true);
        update(presence);
    }

    // ══════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════

    public void save(Presence p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Presence p) {
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
            Presence p = session.get(Presence.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}