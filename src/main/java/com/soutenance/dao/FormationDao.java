package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Formation;
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
public class FormationDao {

    private static final Logger LOG = LoggerFactory.getLogger(FormationDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Formation findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Formation.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Formation> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Formation> cq = cb.createQuery(Formation.class);
            Root<Formation> root = cq.from(Formation.class);
            cq.select(root).orderBy(cb.desc(root.get("dateDebut")));
            List<Formation> formations = session.createQuery(cq).list();
            formations.forEach(f -> Hibernate.initialize(f.getParticipants()));
            return formations;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Formation> findByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Formation> cq = cb.createQuery(Formation.class);
            Root<Formation> root = cq.from(Formation.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut))
              .orderBy(cb.desc(root.get("dateDebut")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Formation> root = cq.from(Formation.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    /**
     * Retourne toutes les formations d'une institution.
     * Utilisé par ReportingService : countFormationsRealisees, countSalariesFormes.
     */
    public List<Formation> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Formation> cq = cb.createQuery(Formation.class);
            Root<Formation> root = cq.from(Formation.class);
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateDebut")));
            List<Formation> formations = session.createQuery(cq).list();
            // Formation.getNombreParticipants() lit cette collection après la
            // fermeture de la session (rendu JSF) — il faut l'initialiser ici.
            formations.forEach(f -> Hibernate.initialize(f.getParticipants()));
            return formations;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Formation> findByEmployeId(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Formation> cq = cb.createQuery(Formation.class);
            Root<Formation> root = cq.from(Formation.class);
            Join<Object, Object> participants = root.join("participants", JoinType.INNER);
            cq.select(root)
              .where(cb.equal(participants.get("id"), employeId))
              .orderBy(cb.desc(root.get("dateDebut")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }
    
    public void save(Formation f) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Formation f) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}