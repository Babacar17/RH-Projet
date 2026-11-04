package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.ModeleDocument;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ModeleDocumentDao {

    private static final Logger LOG = LoggerFactory.getLogger(ModeleDocumentDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public ModeleDocument findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(ModeleDocument.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /**
     * Modèles d'une institution + modèles globaux (institution null).
     * CORRECTION : ModeleDocument stocke institutionId (Long), pas une relation objet.
     * La requête HQL utilisait m.institution.id qui n'existe pas → SemanticException.
     */
    public List<ModeleDocument> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ModeleDocument m " +
                "WHERE (m.institutionId = :iid OR m.institutionId IS NULL) " +
                "AND m.statut = 'ACTIF' " +
                "ORDER BY m.typeDocument ASC, m.titre ASC",
                ModeleDocument.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<ModeleDocument> findByTypeDocument(Long institutionId, String typeDocument) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ModeleDocument m " +
                "WHERE m.typeDocument = :type " +
                "AND (m.institutionId = :iid OR m.institutionId IS NULL) " +
                "AND m.statut = 'ACTIF' " +
                "ORDER BY m.titre ASC",
                ModeleDocument.class)
                .setParameter("type", typeDocument)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Modèles globaux (système, partagés toutes institutions). */
    public List<ModeleDocument> findGlobaux() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<ModeleDocument> cq = cb.createQuery(ModeleDocument.class);
            Root<ModeleDocument> root = cq.from(ModeleDocument.class);
            cq.select(root)
              .where(cb.and(
                  cb.isNull(root.get("institutionId")),
                  cb.equal(root.get("statut"), "ACTIF")
              ))
              .orderBy(cb.asc(root.get("typeDocument")), cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(ModeleDocument m) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(m);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(ModeleDocument m) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(m);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            ModeleDocument m = session.get(ModeleDocument.class, id);
            if (m != null) session.remove(m);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}