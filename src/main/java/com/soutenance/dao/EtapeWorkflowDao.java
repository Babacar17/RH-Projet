package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.EtapeValidation;
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
 * DAO pour les étapes de workflow (EtapeValidation).
 *
 * CORRECTION : findByWorkflow() utilisait l'ancienne entité Workflow.
 * Il utilise désormais WorkflowValidation, cohérent avec EtapeValidation.workflow.
 */
@Repository
public class EtapeWorkflowDao {

    private static final Logger LOG = LoggerFactory.getLogger(EtapeWorkflowDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public EtapeValidation findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(EtapeValidation.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /**
     * Retourne les étapes d'un workflow, triées par ordre croissant.
     *
     * CORRECTION : le paramètre était Long workflowId et la jointure
     * pointait vers l'entité Workflow. Elle pointe maintenant vers
     * WorkflowValidation via root.get("workflow").get("id").
     */
    public List<EtapeValidation> findByWorkflow(Long workflowId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<EtapeValidation> cq = cb.createQuery(EtapeValidation.class);
            Root<EtapeValidation> root = cq.from(EtapeValidation.class);
            cq.select(root)
              .where(cb.equal(root.get("workflow").get("id"), workflowId))
              .orderBy(cb.asc(root.get("ordre")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(EtapeValidation e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    public void update(EtapeValidation e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            EtapeValidation e = session.get(EtapeValidation.class, id);
            if (e != null) session.remove(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }
}