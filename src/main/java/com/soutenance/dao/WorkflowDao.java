package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.WorkflowValidation;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour l'entité WorkflowValidation (anciennement Workflow).
 *
 * Modifications par rapport à l'original :
 *  - Toutes les occurrences de Workflow    → WorkflowValidation
 *  - Le champ "module" (Workflow)          → "typeObjet" (WorkflowValidation)
 *  - Le champ "nom"    (Workflow)          → "typeObjet" (WorkflowValidation, utilisé pour le tri)
 *  - Le champ "statut = 'ACTIF'"           → "actif = true" (boolean dans WorkflowValidation)
 */
@Repository
public class WorkflowDao {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public WorkflowValidation findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(WorkflowValidation.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /**
     * Retourne tous les workflows d'une institution
     * (globaux institution=null inclus pour cohérence avec ParametreDao).
     */
    public List<WorkflowValidation> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<WorkflowValidation> cq = cb.createQuery(WorkflowValidation.class);
            Root<WorkflowValidation> root = cq.from(WorkflowValidation.class);
            // "module"/"nom" → "typeObjet" dans WorkflowValidation
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.asc(root.get("typeObjet")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Workflow actif pour un type d'objet et une institution.
     * (anciennement findActifParModule)
     *
     * @param typeObjet  ex : "CONGE", "RECRUTEMENT", "EVALUATION"
     */
    public WorkflowValidation findActifParTypeObjet(String typeObjet, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM WorkflowValidation w " +
                "WHERE w.typeObjet = :typeObjet " +
                "AND w.institution.id = :iid " +
                "AND w.actif = true",   // "statut = 'ACTIF'" → "actif = true"
                WorkflowValidation.class)
                .setParameter("typeObjet", typeObjet)
                .setParameter("iid", institutionId)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public void save(WorkflowValidation w) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(w);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(WorkflowValidation w) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(w);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            WorkflowValidation w = session.get(WorkflowValidation.class, id);
            if (w != null) session.remove(w);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}