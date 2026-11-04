package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Institution;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class InstitutionDao {

    private static final Logger LOG = LoggerFactory.getLogger(InstitutionDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    // ── Lecture ────────────────────────────────────────────────────────────

    public Institution findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Institution.class, id);
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    public Institution findByCode(String code) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Institution> cq = cb.createQuery(Institution.class);
            Root<Institution> root = cq.from(Institution.class);
            cq.select(root).where(cb.equal(root.get("code"), code.toUpperCase()));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    /** Toutes les institutions, triées par nom. */
    public List<Institution> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Institution> cq = cb.createQuery(Institution.class);
            Root<Institution> root = cq.from(Institution.class);
            cq.select(root).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /** Uniquement les institutions actives (statut = ACTIVE). */
    public List<Institution> findActives() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Institution> cq = cb.createQuery(Institution.class);
            Root<Institution> root = cq.from(Institution.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), "ACTIVE"))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /** Recherche par nom ou code (insensible à la casse). */
    public List<Institution> search(String terme) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Institution> cq = cb.createQuery(Institution.class);
            Root<Institution> root = cq.from(Institution.class);
            String pattern = "%" + terme.toLowerCase() + "%";
            Predicate p = cb.or(
                cb.like(cb.lower(root.get("nom")),  pattern),
                cb.like(cb.lower(root.get("code")), pattern)
            );
            cq.select(root).where(p).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    // ── Compteurs ──────────────────────────────────────────────────────────

    public long countTotal() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            cq.select(cb.count(cq.from(Institution.class)));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Institution> root = cq.from(Institution.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    /**
     * Compte le nombre d'employés dans une institution.
     * Utilisé pour les statistiques du dashboard super-admin.
     */
    public long countEmployesByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            Long result = (Long) session.createQuery(
                "SELECT COUNT(e) FROM Employe e WHERE e.institution.id = :iid")
                .setParameter("iid", institutionId)
                .uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    /**
     * Compte le nombre d'utilisateurs dans une institution.
     */
    public long countUsersByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            Long result = (Long) session.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.institution.id = :iid")
                .setParameter("iid", institutionId)
                .uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    // ── Écriture ───────────────────────────────────────────────────────────

    public void save(Institution inst) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(inst);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void update(Institution inst) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(inst);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Institution inst = session.get(Institution.class, id);
            if (inst != null) session.remove(inst);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }
}