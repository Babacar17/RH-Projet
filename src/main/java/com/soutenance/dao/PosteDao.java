package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Poste;
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
public class PosteDao {

    private static final Logger LOG = LoggerFactory.getLogger(PosteDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Poste findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Poste.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Poste> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Poste> cq = cb.createQuery(Poste.class);
            Root<Poste> root = cq.from(Poste.class);
            cq.select(root).orderBy(cb.asc(root.get("intitule")));
            List<Poste> postes = session.createQuery(cq).list();
            // Poste.getEffectifActuel()/getNombreVacances() lisent cette collection
            // après la fermeture de la session (rendu JSF) — à initialiser ici.
            postes.forEach(p -> Hibernate.initialize(p.getEmployes()));
            return postes;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Poste> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Poste> cq = cb.createQuery(Poste.class);
            Root<Poste> root = cq.from(Poste.class);
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.asc(root.get("intitule")));
            List<Poste> postes = session.createQuery(cq).list();
            postes.forEach(p -> Hibernate.initialize(p.getEmployes()));
            return postes;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Poste> findByInstitutionAndStatut(Long institutionId, String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Poste> cq = cb.createQuery(Poste.class);
            Root<Poste> root = cq.from(Poste.class);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("institution").get("id"), institutionId),
                  cb.equal(root.get("statut"), statut)
              ))
              .orderBy(cb.asc(root.get("intitule")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public boolean existsByCodeAndInstitution(String code, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Poste> root = cq.from(Poste.class);
            cq.select(cb.count(root)).where(
                cb.equal(root.get("code"), code),
                cb.equal(root.get("institution").get("id"), institutionId)
            );
            Long count = session.createQuery(cq).uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return false; }
    }

    /** Compte les postes vacants (effectifCible > effectifActuel) pour une institution. */
    public long countVacantsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            // On charge les postes et compte ceux avec vacances > 0
            List<Poste> postes = findByInstitution(institutionId);
            return postes.stream().filter(Poste::isVacant).count();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Poste> root = cq.from(Poste.class);
            cq.select(cb.count(root))
              .where(cb.equal(root.get("institution").get("id"), institutionId));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }
	 // ══════════════════════════════════════════════════
	 // CORRECTION : méthode à ajouter dans PosteDao.java
	 // ══════════════════════════════════════════════════
	
	 /**
	  * Recherche un Poste par son intitulé exact (insensible à la casse).
	  * Utilisé par RHService.approuverMouvement() pour convertir le String
	  * stocké dans MouvementCarriere.nouveauPoste en objet Poste.
	  */
	 public Poste findByIntitule(String intitule) {
	     try (Session session = sessionFactory.openSession()) {
	         CriteriaBuilder cb = session.getCriteriaBuilder();
	         CriteriaQuery<Poste> cq = cb.createQuery(Poste.class);
	         Root<Poste> root = cq.from(Poste.class);
	         cq.select(root)
	           .where(cb.equal(cb.lower(root.get("intitule")),
	                           intitule.toLowerCase()));
	         return session.createQuery(cq).setMaxResults(1).uniqueResult();
	     } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
	 }

    public void save(Poste p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Poste p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Poste p = session.get(Poste.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}