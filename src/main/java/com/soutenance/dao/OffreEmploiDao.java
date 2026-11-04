package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Candidature;
import com.soutenance.entity.OffreEmploi;
import jakarta.persistence.criteria.*;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO OffreEmploi.
 *
 * CORRECTION BUG #2 — Incohérence getCurrentSession / openSession.
 *
 * Situation originale :
 *   - findExpirees() utilisait sessionFactory.getCurrentSession()
 *     → requiert une transaction Spring active (hibernate.current_session_context_class=spring)
 *   - Toutes les autres méthodes utilisaient sessionFactory.openSession()
 *     → ouvrent leur propre session indépendante, ignorant @Transactional
 *
 * Choix de correction : UNIFORMISER sur openSession().
 *
 * Pourquoi openSession() et non getCurrentSession() ?
 *   Le projet n'a PAS de configuration hibernate.current_session_context_class
 *   dans hibernate.cfg.xml (aucun fichier Spring XML fourni ne le montre).
 *   Passer brutalement à getCurrentSession() partout casserait les appels
 *   hors-transaction (ex : @PostConstruct, scheduler sans @Transactional explicite).
 *
 *   La correction complète à long terme est :
 *     1. Ajouter dans hibernate.cfg.xml :
 *        <property name="hibernate.current_session_context_class">
 *            org.springframework.orm.hibernate5.SpringSessionContext
 *        </property>
 *     2. Migrer TOUS les DAOs vers getCurrentSession()
 *     3. S'assurer que TOUS les appels passent par un @Transactional
 *
 *   En attendant cette migration, findExpirees() utilise openSession()
 *   pour être cohérent avec le reste du DAO et éviter une
 *   HibernateException("No Session found for current thread") au démarrage
 *   du scheduler.
 */
@Repository
public class OffreEmploiDao {

    private static final Logger LOG = LoggerFactory.getLogger(OffreEmploiDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public OffreEmploi findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            OffreEmploi offre = session.createQuery(
                "FROM OffreEmploi o LEFT JOIN FETCH o.institution WHERE o.id = :id",
                OffreEmploi.class)
                .setParameter("id", id)
                .uniqueResult();
            if (offre != null) {
                Hibernate.initialize(offre.getDocumentsRequis());
                Hibernate.initialize(offre.getPoste());
                Hibernate.initialize(offre.getService());
                Hibernate.initialize(offre.getBureau());
            }
            return offre;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    public List<OffreEmploi> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<OffreEmploi> cq = cb.createQuery(OffreEmploi.class);
            Root<OffreEmploi> root = cq.from(OffreEmploi.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            List<OffreEmploi> resultats = session.createQuery(cq).list();
            resultats.forEach(o -> {
                Hibernate.initialize(o.getDocumentsRequis());
                Hibernate.initialize(o.getPoste());
                Hibernate.initialize(o.getService());
                Hibernate.initialize(o.getBureau());
            });
            return resultats;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /**
     * Retourne les offres PUBLIÉE dont la date limite est dépassée.
     *
     * FIX BUG #2 : Remplace sessionFactory.getCurrentSession() par openSession()
     * pour être cohérent avec tous les autres DAOs du projet.
     * L'ancienne version plantait avec HibernateException si le scheduler
     * était appelé sans contexte transactionnel Spring actif.
     */
    public List<OffreEmploi> findExpirees(LocalDateTime maintenant) {
        // FIX : était sessionFactory.getCurrentSession() → incompatible avec openSession() ailleurs
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM OffreEmploi o " +
                "WHERE o.statut = 'PUBLIEE' " +
                "  AND o.dateLimiteFermeture IS NOT NULL " +
                "  AND o.dateLimiteFermeture <= :now",
                OffreEmploi.class)
                .setParameter("now", maintenant)
                .getResultList();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<OffreEmploi> findByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<OffreEmploi> cq = cb.createQuery(OffreEmploi.class);
            Root<OffreEmploi> root = cq.from(OffreEmploi.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /** Offres d'un statut donné, restreintes à une institution (portail candidat). */
    public List<OffreEmploi> findByStatutAndInstitution(String statut, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<OffreEmploi> cq = cb.createQuery(OffreEmploi.class);
            Root<OffreEmploi> root = cq.from(OffreEmploi.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut),
                     cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateCreation")));
            List<OffreEmploi> resultats = session.createQuery(cq).list();
            resultats.forEach(o -> {
                Hibernate.initialize(o.getPoste());
                Hibernate.initialize(o.getService());
                Hibernate.initialize(o.getBureau());
            });
            return resultats;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /**
     * Retourne toutes les offres d'une institution.
     * Utilisé par ReportingService : countOffresPubliees.
     */
    public List<OffreEmploi> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<OffreEmploi> cq = cb.createQuery(OffreEmploi.class);
            Root<OffreEmploi> root = cq.from(OffreEmploi.class);
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateCreation")));
            List<OffreEmploi> resultats = session.createQuery(cq).list();
            resultats.forEach(o -> {
                Hibernate.initialize(o.getPoste());
                Hibernate.initialize(o.getService());
                Hibernate.initialize(o.getBureau());
                Hibernate.initialize(o.getReferentiel());
                Hibernate.initialize(o.getDocumentsRequis());
            });
            return resultats;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<OffreEmploi> root = cq.from(OffreEmploi.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    public long countCandidaturesParOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("offre").get("id"), offreId));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    public void save(OffreEmploi o) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(o);
            tx.commit();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            rollbackSilencieux(tx);
            throw new RuntimeException("Impossible d'enregistrer l'offre : " + e.getMessage(), e);
        }
    }

    public void update(OffreEmploi o) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(o);
            tx.commit();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            rollbackSilencieux(tx);
            throw new RuntimeException("Impossible de mettre à jour l'offre : " + e.getMessage(), e);
        }
    }

    /** Tente un rollback sans laisser un éventuel échec masquer l'exception d'origine déjà journalisée. */
    private void rollbackSilencieux(Transaction tx) {
        if (tx == null) return;
        try {
            tx.rollback();
        } catch (Exception rollbackEx) {
            LOG.error("Erreur lors du rollback", rollbackEx);
        }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            OffreEmploi o = session.get(OffreEmploi.class, id);
            if (o != null) session.remove(o);
            tx.commit();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            rollbackSilencieux(tx);
        }
    }
}