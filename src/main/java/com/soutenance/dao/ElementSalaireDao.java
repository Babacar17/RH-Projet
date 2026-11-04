package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.ElementSalaire;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ElementSalaireDao {

    private static final Logger LOG = LoggerFactory.getLogger(ElementSalaireDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public ElementSalaire findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(ElementSalaire.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<ElementSalaire> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<ElementSalaire> cq = cb.createQuery(ElementSalaire.class);
            Root<ElementSalaire> root = cq.from(ElementSalaire.class);
            cq.select(root).orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Éléments de salaire d'une institution (via grille salariale). */
    public List<ElementSalaire> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ElementSalaire el " +
                "JOIN FETCH el.grilleSalariale gs " +
                "WHERE gs.institution.id = :iid " +
                "AND el.statut = 'ACTIF' " +
                "ORDER BY el.type ASC, el.libelle ASC",
                ElementSalaire.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Éléments d'une grille salariale donnée. */
    public List<ElementSalaire> findByGrille(Long grilleId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<ElementSalaire> cq = cb.createQuery(ElementSalaire.class);
            Root<ElementSalaire> root = cq.from(ElementSalaire.class);
            cq.select(root)
              .where(cb.equal(root.get("grilleSalariale").get("id"), grilleId))
              .orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Éléments d'un bulletin de paie (primes/retenues ligne par ligne). */
    public List<ElementSalaire> findByBulletin(Long bulletinId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<ElementSalaire> cq = cb.createQuery(ElementSalaire.class);
            Root<ElementSalaire> root = cq.from(ElementSalaire.class);
            cq.select(root)
              .where(cb.equal(root.get("bulletinPaie").get("id"), bulletinId))
              .orderBy(cb.asc(root.get("type")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Éléments actifs par type (PRIME, RETENUE, COTISATION_SALARIALE…). */
    public List<ElementSalaire> findByType(String type) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<ElementSalaire> cq = cb.createQuery(ElementSalaire.class);
            Root<ElementSalaire> root = cq.from(ElementSalaire.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("type"), type),
                  cb.equal(root.get("statut"), "ACTIF")
              ))
              .orderBy(cb.asc(root.get("libelle")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(ElementSalaire el) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(el);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(ElementSalaire el) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(el);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            ElementSalaire el = session.get(ElementSalaire.class, id);
            if (el != null) session.remove(el);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}