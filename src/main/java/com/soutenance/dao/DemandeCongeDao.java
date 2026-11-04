package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.DemandeConge;
import com.soutenance.entity.Employe;
import jakarta.persistence.criteria.*;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DemandeCongeDao {

    private static final Logger LOG = LoggerFactory.getLogger(DemandeCongeDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public DemandeConge findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            DemandeConge d = session.get(DemandeConge.class, id);
            if (d != null && d.getEmploye() != null) Hibernate.initialize(d.getEmploye().getUser());
            return d;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /**
     * Join sur employe — utilisé dans les clauses WHERE des méthodes filtrées.
     * Pas besoin de fetch : employe est déjà FetchType.EAGER dans DemandeConge.
     * La LazyInitializationException sur serviceOrg.division est gérée
     * par le try/catch dans Employe.getDepartement().
     */
    private Join<DemandeConge, Employe> empJoin(Root<DemandeConge> root) {
        return root.join("employe", JoinType.LEFT);
    }

    private List<DemandeConge> initialiser(List<DemandeConge> demandes) {
        for (DemandeConge d : demandes) {
            if (d.getEmploye() != null) {
                Hibernate.initialize(d.getEmploye().getPoste());
                Hibernate.initialize(d.getEmploye().getInstitution());
            }
            if (d.getTypeConge() != null) Hibernate.initialize(d.getTypeConge());
        }
        return demandes;
    }

    public List<DemandeConge> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            cq.select(root).orderBy(cb.desc(root.get("dateDemande")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<DemandeConge> findByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), statut))
              .orderBy(cb.desc(root.get("dateDemande")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<DemandeConge> findByEmployeId(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            Join<DemandeConge, Employe> emp = empJoin(root);
            cq.select(root)
              .where(cb.equal(emp.get("id"), employeId))
              .orderBy(cb.desc(root.get("dateDemande")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<DemandeConge> findByEmployeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            Join<DemandeConge, Employe> emp = empJoin(root);
            cq.select(root)
              .where(emp.get("id").in(ids))
              .orderBy(cb.desc(root.get("dateDemande")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<DemandeConge> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            Join<DemandeConge, Employe> emp = empJoin(root);
            cq.select(root)
              .where(cb.equal(emp.get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateDemande")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<DemandeConge> findByStatutAndInstitution(String statut, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            Join<DemandeConge, Employe> emp = empJoin(root);
            cq.select(root)
              .where(
                  cb.equal(root.get("statut"), statut),
                  cb.equal(emp.get("institution").get("id"), institutionId))
              .orderBy(cb.desc(root.get("dateDemande")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            Long result = session.createQuery(cq).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    // ══════════════════════════════════════════════════
    // CORRECTION 1 : méthode manquante appelée par le service congés
    // ══════════════════════════════════════════════════

    /**
     * Compte le total de jours de congés approuvés pour un employé
     * sur l'année civile en cours.
     * Suppose que DemandeConge possède les champs :
     *   - statut        (String)
     *   - employe.id    (Long)
     *   - dateDebut     (LocalDate)
     *   - nombreJours   (Integer) — nombre de jours de la demande
     */
    public long countJoursApprouvesCetteAnnee(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            int annee = LocalDate.now().getYear();
            LocalDate debut = LocalDate.of(annee, 1, 1);
            LocalDate fin   = LocalDate.of(annee, 12, 31);
            Long result = session.createQuery(
                "SELECT COALESCE(SUM(d.nombreJours), 0) FROM DemandeConge d " +
                "WHERE d.employe.id = :empId " +
                "AND d.statut = 'APPROUVE' " +
                "AND d.dateDebut >= :debut " +
                "AND d.dateDebut <= :fin",
                Long.class)
                .setParameter("empId", employeId)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    // ══════════════════════════════════════════════════
    // CORRECTION 2 : méthode manquante appelée par le service congés
    // ══════════════════════════════════════════════════

    /**
     * Retourne les demandes de congé approuvées d'une institution
     * dont la période chevauche [debut, fin].
     * Suppose que DemandeConge possède les champs :
     *   - statut    (String)
     *   - dateDebut (LocalDate)
     *   - dateFin   (LocalDate)
     *   - employe.institution.id (Long)
     */
    public List<DemandeConge> findApprouvesParPeriodeAndInstitution(
            LocalDate debut, LocalDate fin, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DemandeConge> cq = cb.createQuery(DemandeConge.class);
            Root<DemandeConge> root = cq.from(DemandeConge.class);
            Join<DemandeConge, Employe> emp = empJoin(root);
            cq.select(root)
              .where(cb.and(
                  cb.equal(root.get("statut"), "APPROUVE"),
                  cb.equal(emp.get("institution").get("id"), institutionId),
                  cb.lessThanOrEqualTo(root.get("dateDebut"), fin),
                  cb.greaterThanOrEqualTo(root.get("dateFin"), debut)))
              .orderBy(cb.asc(root.get("dateDebut")));
            return initialiser(session.createQuery(cq).list());
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ══════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════

    public void save(DemandeConge d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(DemandeConge d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            DemandeConge d = session.get(DemandeConge.class, id);
            if (d != null) session.remove(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}