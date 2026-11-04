package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.TypeConge;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class TypeCongeDao {

    private static final Logger LOG = LoggerFactory.getLogger(TypeCongeDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public TypeConge findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(TypeConge.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<TypeConge> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<TypeConge> cq = cb.createQuery(TypeConge.class);
            Root<TypeConge> root = cq.from(TypeConge.class);
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<TypeConge> findActifsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<TypeConge> cq = cb.createQuery(TypeConge.class);
            Root<TypeConge> root = cq.from(TypeConge.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("institution").get("id"), institutionId),
                  cb.isTrue(root.get("actif"))
              ))
              .orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Compte les usages d'un type de congé dans les demandes (garde avant suppression). */
    public long countUsages(Long typeCongeId) {
        try (Session session = sessionFactory.openSession()) {
            Long result = session.createQuery(
                "SELECT COUNT(d) FROM DemandeConge d WHERE d.typeConge.id = :tcid",
                Long.class)
                .setParameter("tcid", typeCongeId)
                .uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void save(TypeConge tc) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(tc);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(TypeConge tc) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(tc);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            TypeConge tc = session.get(TypeConge.class, id);
            if (tc != null) session.remove(tc);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}