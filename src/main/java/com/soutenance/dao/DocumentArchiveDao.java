package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.DocumentArchive;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour DocumentArchive.
 * Correspond à l'entité Document (fichier dans la GED).
 * Nommé DocumentArchiveDao pour correspondre aux injections dans
 * AdminDocumentService et DocumentService.
 */
@Repository
public class DocumentArchiveDao {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentArchiveDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public DocumentArchive findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(DocumentArchive.class, id);
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    public List<DocumentArchive> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DocumentArchive> cq = cb.createQuery(DocumentArchive.class);
            Root<DocumentArchive> root = cq.from(DocumentArchive.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /**
     * Utilise root.get("institution").get("id") car DocumentArchive
     * possède désormais une relation @ManyToOne vers Institution.
     */
    public List<DocumentArchive> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DocumentArchive> cq = cb.createQuery(DocumentArchive.class);
            Root<DocumentArchive> root = cq.from(DocumentArchive.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("institution").get("id"), institutionId),
                  cb.notEqual(root.get("statut"), "SUPPRIME")
              ))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<DocumentArchive> findByEmploye(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DocumentArchive> cq = cb.createQuery(DocumentArchive.class);
            Root<DocumentArchive> root = cq.from(DocumentArchive.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("employe").get("id"), employeId),
                  cb.notEqual(root.get("statut"), "SUPPRIME")
              ))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<DocumentArchive> findByCategorie(Long institutionId, String categorie) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DocumentArchive> cq = cb.createQuery(DocumentArchive.class);
            Root<DocumentArchive> root = cq.from(DocumentArchive.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("institution").get("id"), institutionId),
                  cb.equal(root.get("categorie"), categorie),
                  cb.notEqual(root.get("statut"), "SUPPRIME")
              ))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    /** Documents en attente de signature. */
    public List<DocumentArchive> findEnAttenteSignature(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DocumentArchive> cq = cb.createQuery(DocumentArchive.class);
            Root<DocumentArchive> root = cq.from(DocumentArchive.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("institution").get("id"), institutionId),
                  cb.equal(root.get("statut"), "EN_SIGNATURE")
              ))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public long countByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            Long result = session.createQuery(
                "SELECT COUNT(d) FROM DocumentArchive d " +
                "WHERE d.institution.id = :iid AND d.statut != 'SUPPRIME'",
                Long.class)
                .setParameter("iid", institutionId)
                .uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return 0;
        }
    }

    public void save(DocumentArchive d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(d);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void update(DocumentArchive d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(d);
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
            DocumentArchive d = session.get(DocumentArchive.class, id);
            if (d != null) session.remove(d);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }
}