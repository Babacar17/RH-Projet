package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.ParametreSysteme;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ParametreDao {

    private static final Logger LOG = LoggerFactory.getLogger(ParametreDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public ParametreSysteme findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(ParametreSysteme.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<ParametreSysteme> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<ParametreSysteme> cq = cb.createQuery(ParametreSysteme.class);
            Root<ParametreSysteme> root = cq.from(ParametreSysteme.class);
            cq.select(root).orderBy(
                cb.asc(root.get("categorie")),
                cb.asc(root.get("cle"))
            );
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<ParametreSysteme> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ParametreSysteme p " +
                "WHERE p.institution.id = :iid OR p.institution IS NULL " +
                "ORDER BY p.categorie ASC, p.cle ASC",
                ParametreSysteme.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<ParametreSysteme> findByCategorieAndInstitution(String categorie, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ParametreSysteme p " +
                "WHERE p.categorie = :categorie " +
                "AND (p.institution.id = :iid OR p.institution IS NULL) " +
                "ORDER BY p.cle ASC",
                ParametreSysteme.class)
                .setParameter("categorie", categorie)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public ParametreSysteme findByCleAndInstitution(String cle, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            // Priorité 1 : paramètre spécifique à l'institution
            ParametreSysteme specific = session.createQuery(
                "FROM ParametreSysteme p WHERE p.cle = :cle AND p.institution.id = :iid",
                ParametreSysteme.class)
                .setParameter("cle", cle)
                .setParameter("iid", institutionId)
                .setMaxResults(1)
                .uniqueResult();
            if (specific != null) return specific;

            // Priorité 2 : paramètre global (institution NULL)
            return session.createQuery(
                "FROM ParametreSysteme p WHERE p.cle = :cle AND p.institution IS NULL",
                ParametreSysteme.class)
                .setParameter("cle", cle)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public boolean existsByCleAndInstitution(String cle, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            Long count = session.createQuery(
                "SELECT COUNT(p) FROM ParametreSysteme p " +
                "WHERE p.cle = :cle AND p.institution.id = :iid",
                Long.class)
                .setParameter("cle", cle)
                .setParameter("iid", institutionId)
                .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return false; }
    }

    public void save(ParametreSysteme p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(ParametreSysteme p) {
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
            ParametreSysteme p = session.get(ParametreSysteme.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}