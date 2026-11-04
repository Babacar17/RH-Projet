package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Notification;
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
public class NotificationDao {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Notification findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Notification.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Toutes les notifications d'un utilisateur, triées par date décroissante. */
    public List<Notification> findByDestinataire(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Notification> cq = cb.createQuery(Notification.class);
            Root<Notification> root = cq.from(Notification.class);
            cq.select(root)
              .where(cb.equal(root.get("destinataire").get("id"), userId))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Notifications non lues d'un utilisateur. */
    public List<Notification> findNonLuesByDestinataire(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Notification> cq = cb.createQuery(Notification.class);
            Root<Notification> root = cq.from(Notification.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("destinataire").get("id"), userId),
                  cb.isFalse(root.get("estLue"))
              ))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Compte les notifications non lues d'un utilisateur. */
    public long countNonLues(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Notification> root = cq.from(Notification.class);
            cq.select(cb.count(root))
              .where(cb.and(
                  cb.equal(root.get("destinataire").get("id"), userId),
                  cb.isFalse(root.get("estLue"))
              ));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    /** Notifications d'un type donné pour un utilisateur. */
    public List<Notification> findByTypeAndDestinataire(String type, Long userId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Notification> cq = cb.createQuery(Notification.class);
            Root<Notification> root = cq.from(Notification.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("destinataire").get("id"), userId),
                  cb.equal(root.get("type"), type)
              ))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Notifications récentes (N dernières) pour un utilisateur. */
    public List<Notification> findRecentesByDestinataire(Long userId, int limite) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Notification> cq = cb.createQuery(Notification.class);
            Root<Notification> root = cq.from(Notification.class);
            cq.select(root)
              .where(cb.equal(root.get("destinataire").get("id"), userId))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).setMaxResults(limite).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Supprime toutes les notifications expirées (nettoyage planifié). */
    public int deleteExpirees() {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            int count = session.createMutationQuery(
                "DELETE FROM Notification n " +
                "WHERE n.dateExpiration IS NOT NULL AND n.dateExpiration < :now")
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();
            tx.commit();
            return count;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    /** Marque toutes les notifications d'un utilisateur comme lues. */
    public void marquerToutesLues(Long userId) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                "UPDATE Notification n SET n.estLue = true, n.dateLecture = :now " +
                "WHERE n.destinataire.id = :uid AND n.estLue = false")
                .setParameter("now", LocalDateTime.now())
                .setParameter("uid", userId)
                .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void save(Notification n) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(n);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Notification n) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(n);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Notification n = session.get(Notification.class, id);
            if (n != null) session.remove(n);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}