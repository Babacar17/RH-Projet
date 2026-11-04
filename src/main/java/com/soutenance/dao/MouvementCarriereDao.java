package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.MouvementCarriere;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MouvementCarriereDao {

    private static final Logger LOG = LoggerFactory.getLogger(MouvementCarriereDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public MouvementCarriere findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(MouvementCarriere.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<MouvementCarriere> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<MouvementCarriere> cq = cb.createQuery(MouvementCarriere.class);
            Root<MouvementCarriere> root = cq.from(MouvementCarriere.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<MouvementCarriere> findByEmploye(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<MouvementCarriere> cq = cb.createQuery(MouvementCarriere.class);
            Root<MouvementCarriere> root = cq.from(MouvementCarriere.class);
            cq.select(root)
              .where(cb.equal(root.get("employe").get("id"), employeId))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Alias de findByEmploye — pour compatibilite avec EmployeBean */
    public List<MouvementCarriere> findByEmployeId(Long employeId) {
        return findByEmploye(employeId);
    }

    public List<MouvementCarriere> findByEmployeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<MouvementCarriere> cq = cb.createQuery(MouvementCarriere.class);
            Root<MouvementCarriere> root = cq.from(MouvementCarriere.class);
            cq.select(root)
              .where(root.get("employe").get("id").in(ids))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<MouvementCarriere> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<MouvementCarriere> cq = cb.createQuery(MouvementCarriere.class);
            Root<MouvementCarriere> root = cq.from(MouvementCarriere.class);
            cq.select(root)
              .where(cb.equal(root.get("employe").get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(MouvementCarriere m) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(m);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(MouvementCarriere m) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(m);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}