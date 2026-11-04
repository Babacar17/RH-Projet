package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.LogAction;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LogActionDao {

    private static final Logger LOG = LoggerFactory.getLogger(LogActionDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public List<LogAction> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<LogAction> cq = cb.createQuery(LogAction.class);
            Root<LogAction> root = cq.from(LogAction.class);
            cq.select(root).orderBy(cb.desc(root.get("dateAction")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<LogAction> findByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<LogAction> cq = cb.createQuery(LogAction.class);
            Root<LogAction> root = cq.from(LogAction.class);
            cq.select(root)
              .where(cb.equal(root.get("username"), username))
              .orderBy(cb.desc(root.get("dateAction")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Historique d'un type d'action (ex. ANNONCE_DIFFUSION) pour une institution donnée. */
    public List<LogAction> findByActionAndInstitution(String action, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<LogAction> cq = cb.createQuery(LogAction.class);
            Root<LogAction> root = cq.from(LogAction.class);
            cq.select(root)
              .where(cb.equal(root.get("action"), action),
                     cb.equal(root.get("institutionId"), institutionId))
              .orderBy(cb.desc(root.get("dateAction")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<LogAction> findRecents(int limite) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<LogAction> cq = cb.createQuery(LogAction.class);
            Root<LogAction> root = cq.from(LogAction.class);
            cq.select(root).orderBy(cb.desc(root.get("dateAction")));
            return session.createQuery(cq).setMaxResults(limite).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(LogAction log) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(log);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    /**
     * Supprime tous les logs dont dateAction est strictement antérieure au seuil.
     *
     * @return nombre de lignes effectivement supprimées
     */
    public int purgerAvant(LocalDateTime seuil) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            int n = session.createMutationQuery(
                    "delete from LogAction l where l.dateAction < :seuil")
                .setParameter("seuil", seuil)
                .executeUpdate();
            tx.commit();
            return n;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
            return -1;
        }
    }

    /** Compte les logs antérieurs au seuil (pour prévisualiser une purge). */
    public long countAvant(LocalDateTime seuil) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "select count(l.id) from LogAction l where l.dateAction < :seuil",
                    Long.class)
                .setParameter("seuil", seuil)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return -1; }
    }
}