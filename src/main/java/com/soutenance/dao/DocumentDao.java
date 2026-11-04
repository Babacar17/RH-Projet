package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Document;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO — Gestion des documents GED.
 */
@Repository
public class DocumentDao {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    // ═══════════════════════════════════════════════════════
    // FIND BY ID
    // ═══════════════════════════════════════════════════════

    public Document findById(Long id) {

        try (Session session = sessionFactory.openSession()) {

            return session.get(Document.class, id);

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return null;
        }
    }

    // ═══════════════════════════════════════════════════════
    // FIND ALL
    // ═══════════════════════════════════════════════════════

    public List<Document> findAll() {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Document> cq =
                    cb.createQuery(Document.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(root)
              .orderBy(cb.desc(root.get("dateDepot")));

            return session.createQuery(cq).getResultList();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════
    // FIND BY INSTITUTION
    // ═══════════════════════════════════════════════════════

    public List<Document> findByInstitution(Long institutionId) {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Document> cq =
                    cb.createQuery(Document.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(root)
              .where(
                  cb.equal(
                      root.get("institutionId"),
                      institutionId
                  )
              )
              .orderBy(cb.desc(root.get("dateDepot")));

            return session.createQuery(cq).getResultList();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════
    // FIND BY EMPLOYE
    // ═══════════════════════════════════════════════════════

    public List<Document> findByEmploye(Long employeId) {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Document> cq =
                    cb.createQuery(Document.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(root)
              .where(
                  cb.equal(
                      root.get("employe").get("id"),
                      employeId
                  )
              )
              .orderBy(cb.desc(root.get("dateDepot")));

            return session.createQuery(cq).getResultList();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════
    // FIND BY STATUT
    // ═══════════════════════════════════════════════════════

    public List<Document> findByStatut(String statut) {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Document> cq =
                    cb.createQuery(Document.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(root)
              .where(
                  cb.equal(
                      root.get("statut"),
                      statut
                  )
              )
              .orderBy(cb.desc(root.get("dateDepot")));

            return session.createQuery(cq).getResultList();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════
    // FIND EXPIRES
    // ═══════════════════════════════════════════════════════

    public List<Document> findDocumentsExpires() {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Document> cq =
                    cb.createQuery(Document.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(root)
              .where(
                  cb.lessThan(
                      root.get("dateExpiration"),
                      LocalDate.now()
                  )
              );

            return session.createQuery(cq).getResultList();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return new ArrayList<>();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SAVE
    // ═══════════════════════════════════════════════════════

    public void save(Document document) {

        Transaction tx = null;

        try (Session session = sessionFactory.openSession()) {

            tx = session.beginTransaction();

            session.persist(document);

            tx.commit();

        } catch (Exception e) {

            if (tx != null) {

                tx.rollback();
            }

            LOG.error("Erreur inattendue", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════

    public void update(Document document) {

        Transaction tx = null;

        try (Session session = sessionFactory.openSession()) {

            tx = session.beginTransaction();

            session.merge(document);

            tx.commit();

        } catch (Exception e) {

            if (tx != null) {

                tx.rollback();
            }

            LOG.error("Erreur inattendue", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════

    public void delete(Long id) {

        Transaction tx = null;

        try (Session session = sessionFactory.openSession()) {

            tx = session.beginTransaction();

            Document document =
                    session.get(Document.class, id);

            if (document != null) {

                session.remove(document);
            }

            tx.commit();

        } catch (Exception e) {

            if (tx != null) {

                tx.rollback();
            }

            LOG.error("Erreur inattendue", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    // COUNT
    // ═══════════════════════════════════════════════════════

    public long countAll() {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Long> cq =
                    cb.createQuery(Long.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(cb.count(root));

            return session.createQuery(cq)
                    .getSingleResult();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return 0;
        }
    }

    public long countByInstitution(Long institutionId) {

        try (Session session = sessionFactory.openSession()) {

            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Long> cq =
                    cb.createQuery(Long.class);

            Root<Document> root =
                    cq.from(Document.class);

            cq.select(cb.count(root))
              .where(
                  cb.equal(
                      root.get("institutionId"),
                      institutionId
                  )
              );

            return session.createQuery(cq)
                    .getSingleResult();

        } catch (Exception e) {

            LOG.error("Erreur inattendue", e);

            return 0;
        }
    }
}