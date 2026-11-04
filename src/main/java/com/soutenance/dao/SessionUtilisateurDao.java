package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.SessionUtilisateur;
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
public class SessionUtilisateurDao {

    private static final Logger LOG = LoggerFactory.getLogger(SessionUtilisateurDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public SessionUtilisateur findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(SessionUtilisateur.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public SessionUtilisateur findByToken(String tokenJti) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<SessionUtilisateur> cq = cb.createQuery(SessionUtilisateur.class);
            Root<SessionUtilisateur> root = cq.from(SessionUtilisateur.class);
            cq.select(root).where(cb.equal(root.get("tokenJti"), tokenJti));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Sessions actives d'une institution (non révoquées et non expirées). */
    public List<SessionUtilisateur> findActivesParInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM SessionUtilisateur s JOIN s.user u " +
                "WHERE u.institution.id = :iid " +
                "AND s.revoquee = false " +
                "AND s.dateExpiration > :now " +
                "ORDER BY s.derniereActivite DESC",
                SessionUtilisateur.class)
                .setParameter("iid", institutionId)
                .setParameter("now", LocalDateTime.now())
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Sessions actives d'un utilisateur donné. */
    public List<SessionUtilisateur> findActivesParUser(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM SessionUtilisateur s WHERE s.user.id = :uid " +
                "AND s.revoquee = false AND s.dateExpiration > :now",
                SessionUtilisateur.class)
                .setParameter("uid", userId)
                .setParameter("now", LocalDateTime.now())
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Nom d'utilisateur pour compatibilité avec AdminParametrageService. */
    public String getUsername(SessionUtilisateur s) {
        return s.getUser() != null ? s.getUser().getUsername() : "—";
    }

    public void save(SessionUtilisateur s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(SessionUtilisateur s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            SessionUtilisateur s = session.get(SessionUtilisateur.class, id);
            if (s != null) session.remove(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}