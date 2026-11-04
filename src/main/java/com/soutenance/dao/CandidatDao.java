package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Candidature;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservé pour compatibilité avec les beans qui injectent CandidatDao.
 * Toutes les méthodes délèguent au même pattern openSession + CriteriaBuilder.
 * Préférer CandidatureDao pour les nouveaux développements.
 */
@Repository
public class CandidatDao {

    private static final Logger LOG = LoggerFactory.getLogger(CandidatDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Candidature findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Candidature.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Candidature> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCandidature")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Candidature> findByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root)
              .where(cb.equal(root.get("offre").get("id"), offreId))
              .orderBy(cb.desc(root.get("dateCandidature")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Candidature> findByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut))
              .orderBy(cb.desc(root.get("dateCandidature")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Candidature> findByUserId(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root)
              .where(cb.equal(root.get("candidatUser").get("id"), userId))
              .orderBy(cb.desc(root.get("dateCandidature")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public boolean existsByUserAndOffre(Long userId, Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(cb.count(root)).where(
                cb.equal(root.get("candidatUser").get("id"), userId),
                cb.equal(root.get("offre").get("id"), offreId)
            );
            Long count = session.createQuery(cq).uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return false; }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void save(Candidature c) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Candidature c) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Candidature c = session.get(Candidature.class, id);
            if (c != null) session.remove(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}