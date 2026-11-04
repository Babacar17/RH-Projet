package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.JourFerie;
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
public class JourFerieDao {

    private static final Logger LOG = LoggerFactory.getLogger(JourFerieDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public JourFerie findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(JourFerie.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Jours fériés d'une institution (inclut ceux sans institution = nationaux). */
    public List<JourFerie> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM JourFerie j WHERE j.institution.id = :iid OR j.institution IS NULL " +
                "ORDER BY j.date ASC",
                JourFerie.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Jours fériés d'une institution pour une année donnée. */
    public List<JourFerie> findByAnneeAndInstitution(int annee, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            LocalDate debut = LocalDate.of(annee, 1, 1);
            LocalDate fin   = LocalDate.of(annee, 12, 31);
            return session.createQuery(
                "FROM JourFerie j " +
                "WHERE j.date >= :debut AND j.date <= :fin " +
                "AND (j.institution.id = :iid OR j.institution IS NULL) " +
                "ORDER BY j.date ASC",
                JourFerie.class)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Jours fériés nationaux uniquement (institution null). */
    public List<JourFerie> findNationaux() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<JourFerie> cq = cb.createQuery(JourFerie.class);
            Root<JourFerie> root = cq.from(JourFerie.class);
            cq.select(root)
              .where(cb.isNull(root.get("institution")))
              .orderBy(cb.asc(root.get("date")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Jours fériés d'une institution sur une période donnée (inclut les nationaux). */
    public List<JourFerie> findByPeriodeAndInstitution(LocalDate debut, LocalDate fin, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM JourFerie j WHERE j.date BETWEEN :d AND :f " +
                "AND (j.institution.id = :iid OR j.institution IS NULL) " +
                "ORDER BY j.date ASC",
                JourFerie.class)
                .setParameter("d", debut).setParameter("f", fin).setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Vérifie si une date est un jour férié pour une institution. */
    public boolean isJourFerie(LocalDate date, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            Long count = session.createQuery(
                "SELECT COUNT(j) FROM JourFerie j " +
                "WHERE j.date = :date " +
                "AND (j.institution.id = :iid OR j.institution IS NULL)",
                Long.class)
                .setParameter("date", date)
                .setParameter("iid", institutionId)
                .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return false; }
    }

    public void save(JourFerie j) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(j);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(JourFerie j) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(j);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            JourFerie j = session.get(JourFerie.class, id);
            if (j != null) session.remove(j);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}