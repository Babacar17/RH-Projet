package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.BulletinPaie;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class BulletinPaieDao {

    private static final Logger LOG = LoggerFactory.getLogger(BulletinPaieDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public BulletinPaie findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM BulletinPaie b LEFT JOIN FETCH b.cyclePaie WHERE b.id = :id",
                BulletinPaie.class)
                .setParameter("id", id)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<BulletinPaie> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<BulletinPaie> cq = cb.createQuery(BulletinPaie.class);
            Root<BulletinPaie> root = cq.from(BulletinPaie.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Bulletins d'un cycle de paie donné. */
    public List<BulletinPaie> findByCycle(Long cycleId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<BulletinPaie> cq = cb.createQuery(BulletinPaie.class);
            Root<BulletinPaie> root = cq.from(BulletinPaie.class);
            cq.select(root)
              .where(cb.equal(root.get("cyclePaie").get("id"), cycleId))
              .orderBy(cb.asc(root.get("employe").get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Bulletins d'une institution (toutes périodes). */
    public List<BulletinPaie> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT b FROM BulletinPaie b " +
                "JOIN b.employe e " +
                "WHERE e.institution.id = :iid " +
                "ORDER BY b.dateCreation DESC",
                BulletinPaie.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Bulletins d'une institution pour un mois/année donnés. */
    public List<BulletinPaie> findByInstitutionAndPeriode(Long institutionId, int mois, int annee) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT b FROM BulletinPaie b " +
                "JOIN b.cyclePaie c JOIN b.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND c.mois = :mois AND c.annee = :annee " +
                "ORDER BY e.nom ASC",
                BulletinPaie.class)
                .setParameter("iid", institutionId)
                .setParameter("mois", mois)
                .setParameter("annee", annee)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Bulletins d'une période (mois/année) — toutes institutions. */
    public List<BulletinPaie> findByPeriode(int mois, int annee) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT b FROM BulletinPaie b JOIN b.cyclePaie c " +
                "WHERE c.mois = :mois AND c.annee = :annee " +
                "ORDER BY b.employe.nom ASC",
                BulletinPaie.class)
                .setParameter("mois", mois)
                .setParameter("annee", annee)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Bulletin d'un employé pour une période précise. */
    public BulletinPaie findByEmployeAndPeriode(Long employeId, int mois, int annee) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT b FROM BulletinPaie b JOIN b.cyclePaie c " +
                "WHERE b.employe.id = :eid AND c.mois = :mois AND c.annee = :annee",
                BulletinPaie.class)
                .setParameter("eid", employeId)
                .setParameter("mois", mois)
                .setParameter("annee", annee)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Bulletins d'un employé (historique complet). */
    public List<BulletinPaie> findByEmploye(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<BulletinPaie> cq = cb.createQuery(BulletinPaie.class);
            Root<BulletinPaie> root = cq.from(BulletinPaie.class);
            cq.select(root)
              .where(cb.equal(root.get("employe").get("id"), employeId))
              .orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Bulletins en arriéré d'une institution : validés mais pas encore payés,
     * alors que la date de versement prévue de leur cycle est déjà dépassée.
     */
    public List<BulletinPaie> findArrieresByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT b FROM BulletinPaie b " +
                "JOIN FETCH b.cyclePaie c JOIN b.employe e " +
                "WHERE e.institution.id = :iid " +
                "AND b.statut = 'VALIDE' " +
                "AND c.datePaiementPrevue IS NOT NULL " +
                "AND c.datePaiementPrevue < :aujourdHui " +
                "ORDER BY c.datePaiementPrevue ASC",
                BulletinPaie.class)
                .setParameter("iid", institutionId)
                .setParameter("aujourdHui", java.time.LocalDate.now())
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Compte les bulletins liés à une grille salariale (garde avant suppression). */
    public long countByGrille(Long grilleId) {
        try (Session session = sessionFactory.openSession()) {
            // La liaison grille ↔ bulletin passe par ElementSalaire
            Long result = session.createQuery(
                "SELECT COUNT(DISTINCT b) FROM BulletinPaie b " +
                "JOIN b.elements el " +
                "WHERE el.grilleSalariale.id = :gid",
                Long.class)
                .setParameter("gid", grilleId)
                .uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void save(BulletinPaie b) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(b);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(BulletinPaie b) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(b);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            BulletinPaie b = session.get(BulletinPaie.class, id);
            if (b != null) session.remove(b);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}