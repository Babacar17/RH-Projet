package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.SoldeConge;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class SoldeCongeDao {

    private static final Logger LOG = LoggerFactory.getLogger(SoldeCongeDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public SoldeConge findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(SoldeConge.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Soldes d'un employé pour un exercice donné. */
    public List<SoldeConge> findByEmployeAndExercice(Long employeId, String exercice) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<SoldeConge> cq = cb.createQuery(SoldeConge.class);
            Root<SoldeConge> root = cq.from(SoldeConge.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("employe").get("id"), employeId),
                  cb.equal(root.get("exercice"), exercice)
              ));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Solde d'un employé pour un type de congé et un exercice précis. */
    public SoldeConge findByEmployeTypeExercice(Long employeId, Long typeCongeId, String exercice) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM SoldeConge s WHERE s.employe.id = :eid " +
                "AND s.typeConge.id = :tcid AND s.exercice = :ex",
                SoldeConge.class)
                .setParameter("eid", employeId)
                .setParameter("tcid", typeCongeId)
                .setParameter("ex", exercice)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Tous les soldes d'un employé (tous exercices). */
    public List<SoldeConge> findByEmploye(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<SoldeConge> cq = cb.createQuery(SoldeConge.class);
            Root<SoldeConge> root = cq.from(SoldeConge.class);
            cq.select(root)
              .where(cb.equal(root.get("employe").get("id"), employeId))
              .orderBy(cb.desc(root.get("exercice")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Compte les jours approuvés cette année pour un employé (utilisé dans PresenceService). */
    public int countJoursApprouvesCetteAnnee(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            String exercice = String.valueOf(java.time.LocalDate.now().getYear());
            Double total = session.createQuery(
                "SELECT SUM(s.joursPris) FROM SoldeConge s " +
                "WHERE s.employe.id = :eid AND s.exercice = :ex",
                Double.class)
                .setParameter("eid", employeId)
                .setParameter("ex", exercice)
                .uniqueResult();
            return total != null ? (int) Math.round(total) : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void save(SoldeConge s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(SoldeConge s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}