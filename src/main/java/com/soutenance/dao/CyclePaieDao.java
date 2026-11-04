package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.CyclePaie;
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
public class CyclePaieDao {

    private static final Logger LOG = LoggerFactory.getLogger(CyclePaieDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public CyclePaie findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(CyclePaie.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<CyclePaie> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<CyclePaie> cq = cb.createQuery(CyclePaie.class);
            Root<CyclePaie> root = cq.from(CyclePaie.class);
            cq.select(root)
              .where(cb.equal(root.get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("annee")), cb.desc(root.get("mois")));
            List<CyclePaie> resultats = session.createQuery(cq).list();
            resultats.forEach(c -> Hibernate.initialize(c.getBulletins()));
            return resultats;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Filtre par statut et/ou année pour l'interface admin. */
    public List<CyclePaie> findByFiltre(Long institutionId, String statut, Integer annee) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<CyclePaie> cq = cb.createQuery(CyclePaie.class);
            Root<CyclePaie> root = cq.from(CyclePaie.class);

            Predicate pred = cb.equal(root.get("institution").get("id"), institutionId);
            if (statut != null && !statut.isBlank())
                pred = cb.and(pred, cb.equal(root.get("statut"), statut));
            if (annee != null)
                pred = cb.and(pred, cb.equal(root.get("annee"), annee));

            cq.select(root).where(pred)
              .orderBy(cb.desc(root.get("annee")), cb.desc(root.get("mois")));
            List<CyclePaie> resultats = session.createQuery(cq).list();
            resultats.forEach(c -> Hibernate.initialize(c.getBulletins()));
            return resultats;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Cycle en cours (statut OUVERT ou EN_CALCUL ou CALCULE) pour une institution. */
    public CyclePaie findCycleEnCours(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM CyclePaie c WHERE c.institution.id = :iid " +
                "AND c.statut IN ('OUVERT','EN_CALCUL','CALCULE') " +
                "ORDER BY c.annee DESC, c.mois DESC",
                CyclePaie.class)
                .setParameter("iid", institutionId)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /**
     * Cycles encore OUVERT dont le versement prévu tombe dans les `joursSeuil` prochains jours
     * (et n'a pas déjà généré d'alerte) — toutes institutions confondues, pour le scheduler.
     */
    public List<CyclePaie> findCyclesVersementProche(int joursSeuil) {
        try (Session session = sessionFactory.openSession()) {
            java.time.LocalDate aujourdHui = java.time.LocalDate.now();
            java.time.LocalDate limite = aujourdHui.plusDays(joursSeuil);
            return session.createQuery(
                "FROM CyclePaie c WHERE c.statut = 'OUVERT' " +
                "AND c.datePaiementPrevue IS NOT NULL " +
                "AND c.datePaiementPrevue BETWEEN :auj AND :limite " +
                "AND c.alerteVersementEnvoyee = false",
                CyclePaie.class)
                .setParameter("auj", aujourdHui)
                .setParameter("limite", limite)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Le cycle le plus récent (toute période confondue) d'une institution, ou null s'il n'y en a aucun. */
    public CyclePaie findDernierCycle(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM CyclePaie c WHERE c.institution.id = :iid " +
                "ORDER BY c.annee DESC, c.mois DESC",
                CyclePaie.class)
                .setParameter("iid", institutionId)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Vérifie l'unicité institution + année + mois avant ouverture. */
    public boolean existsByInstitutionAndPeriode(Long institutionId, int annee, int mois) {
        try (Session session = sessionFactory.openSession()) {
            Long count = session.createQuery(
                "SELECT COUNT(c) FROM CyclePaie c " +
                "WHERE c.institution.id = :iid AND c.annee = :annee AND c.mois = :mois",
                Long.class)
                .setParameter("iid", institutionId)
                .setParameter("annee", annee)
                .setParameter("mois", mois)
                .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return false; }
    }

    public void save(CyclePaie c) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(CyclePaie c) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            CyclePaie c = session.get(CyclePaie.class, id);
            if (c != null) session.remove(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}