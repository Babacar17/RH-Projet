package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.FichePoste;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class FichePosteDao {

    private static final Logger LOG = LoggerFactory.getLogger(FichePosteDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public FichePoste findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(FichePoste.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<FichePoste> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<FichePoste> cq = cb.createQuery(FichePoste.class);
            Root<FichePoste> root = cq.from(FichePoste.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Retourne la fiche de poste liée à un poste donné (relation 1-1). */
    public FichePoste findByPoste(Long posteId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<FichePoste> cq = cb.createQuery(FichePoste.class);
            Root<FichePoste> root = cq.from(FichePoste.class);
            cq.select(root).where(cb.equal(root.get("poste").get("id"), posteId));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<FichePoste> findByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<FichePoste> cq = cb.createQuery(FichePoste.class);
            Root<FichePoste> root = cq.from(FichePoste.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Compte les fiches validées pour une institution via le poste associé. */
    public long countValideesParInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(f) FROM FichePoste f " +
                "WHERE f.statut = 'VALIDEE' AND f.poste.institution.id = :iid",
                Long.class)
                .setParameter("iid", institutionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    /** Compte les postes sans fiche de poste pour une institution. */
    public long countPostesSansFicheParInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(p) FROM Poste p " +
                "WHERE p.institution.id = :iid " +
                "AND p.statut = 'ACTIF' " +
                "AND NOT EXISTS (SELECT f FROM FichePoste f WHERE f.poste = p)",
                Long.class)
                .setParameter("iid", institutionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void save(FichePoste f) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(FichePoste f) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            FichePoste f = session.get(FichePoste.class, id);
            if (f != null) session.remove(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}