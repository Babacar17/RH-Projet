package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.GrilleSalariale;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class GrilleSalarialeDao {

    private static final Logger LOG = LoggerFactory.getLogger(GrilleSalarialeDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public GrilleSalariale findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(GrilleSalariale.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<GrilleSalariale> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<GrilleSalariale> cq = cb.createQuery(GrilleSalariale.class);
            Root<GrilleSalariale> root = cq.from(GrilleSalariale.class);
            cq.select(root).orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<GrilleSalariale> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<GrilleSalariale> cq = cb.createQuery(GrilleSalariale.class);
            Root<GrilleSalariale> root = cq.from(GrilleSalariale.class);
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<GrilleSalariale> findActivesParInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<GrilleSalariale> cq = cb.createQuery(GrilleSalariale.class);
            Root<GrilleSalariale> root = cq.from(GrilleSalariale.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("institution").get("id"), institutionId),
                  cb.equal(root.get("statut"), "ACTIVE")
              ))
              .orderBy(cb.asc(root.get("corps")), cb.asc(root.get("echelon")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(GrilleSalariale g) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(g);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(GrilleSalariale g) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(g);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            GrilleSalariale g = session.get(GrilleSalariale.class, id);
            if (g != null) session.remove(g);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}