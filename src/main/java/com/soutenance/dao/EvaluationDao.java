package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Evaluation;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class EvaluationDao {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluationDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Evaluation findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Evaluation.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Evaluation> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Evaluation> cq = cb.createQuery(Evaluation.class);
            Root<Evaluation> root = cq.from(Evaluation.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Evaluation> findByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Evaluation> cq = cb.createQuery(Evaluation.class);
            Root<Evaluation> root = cq.from(Evaluation.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Evaluation> root = cq.from(Evaluation.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public List<Evaluation> findByEmployeId(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Evaluation> cq = cb.createQuery(Evaluation.class);
            Root<Evaluation> root = cq.from(Evaluation.class);
            cq.select(root)
              .where(cb.equal(root.get("employe").get("id"), employeId))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Evaluation> findByEmployeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Evaluation> cq = cb.createQuery(Evaluation.class);
            Root<Evaluation> root = cq.from(Evaluation.class);
            cq.select(root)
              .where(root.get("employe").get("id").in(ids))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(Evaluation e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    public void update(Evaluation e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }
}