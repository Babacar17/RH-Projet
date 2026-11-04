package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Candidature;
import jakarta.persistence.criteria.*;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CandidatureDao {

    private static final Logger LOG = LoggerFactory.getLogger(CandidatureDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Candidature findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Candidature c = session.get(Candidature.class, id);
            if (c != null && c.getOffre() != null) {
                Hibernate.initialize(c.getOffre().getPoste());
                Hibernate.initialize(c.getOffre().getService());
                Hibernate.initialize(c.getOffre().getBureau());
            }
            return c;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    public List<Candidature> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCandidature")));
            List<Candidature> resultats = session.createQuery(cq).list();
            resultats.forEach(c -> Hibernate.initialize(c.getDocumentsSoumis()));
            return resultats;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<Candidature> findByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root)
              .where(cb.equal(root.get("offre").get("id"), offreId))
              .orderBy(cb.desc(root.get("dateCandidature")));
            List<Candidature> resultats = session.createQuery(cq).list();
            resultats.forEach(c -> {
                Hibernate.initialize(c.getDocumentsSoumis());
                Hibernate.initialize(c.getDiplomes());
                Hibernate.initialize(c.getExperiences());
            });
            return resultats;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
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
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
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
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<Candidature> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Candidature> cq = cb.createQuery(Candidature.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(root)
              .where(cb.equal(root.get("offre").get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateCandidature")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
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
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return false;
        }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Candidature> root = cq.from(Candidature.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    public void save(Candidature c) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(c);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void update(Candidature c) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(c);
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
            Candidature c = session.get(Candidature.class, id);
            if (c != null) session.remove(c);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }
}