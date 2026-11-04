package com.soutenance.dao;

import com.soutenance.entity.EquipeService;
import com.soutenance.entity.ExceptionPlanning;
import com.soutenance.entity.MembreEquipe;
import com.soutenance.entity.PlageHoraireService;
import com.soutenance.entity.Service;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** DAO consolidé pour le régime de planning "par équipes" (plages, équipes, membres, exceptions). */
@Repository
public class PlanningEquipeDao {

    private static final Logger LOG = LoggerFactory.getLogger(PlanningEquipeDao.class);

    @Autowired private SessionFactory sessionFactory;

    // ── Services ──────────────────────────────────────────────────────

    public Service findServiceById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Service.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public void updateService(Service s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ── Plages ────────────────────────────────────────────────────────

    public List<PlageHoraireService> findPlagesByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM PlageHoraireService p WHERE p.service.id = :sid ORDER BY p.ordreRotation",
                PlageHoraireService.class)
                .setParameter("sid", serviceId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public PlageHoraireService findPlageById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(PlageHoraireService.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public void savePlage(PlageHoraireService p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            if (p.getId() == null) session.persist(p); else session.merge(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void deletePlage(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            PlageHoraireService p = session.get(PlageHoraireService.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ── Équipes ───────────────────────────────────────────────────────

    public List<EquipeService> findEquipesByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT e FROM EquipeService e JOIN FETCH e.service WHERE e.service.id = :sid ORDER BY e.ordreRotation",
                EquipeService.class)
                .setParameter("sid", serviceId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void saveEquipe(EquipeService e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            if (e.getId() == null) session.persist(e); else session.merge(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    public void deleteEquipe(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery("DELETE FROM MembreEquipe m WHERE m.equipe.id = :id")
                .setParameter("id", id).executeUpdate();
            EquipeService e = session.get(EquipeService.class, id);
            if (e != null) session.remove(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    // ── Membres ───────────────────────────────────────────────────────

    /** Tous les membres des équipes d'un service, évaluateur (employé) chargé. */
    public List<MembreEquipe> findMembresByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT m FROM MembreEquipe m JOIN FETCH m.employe e LEFT JOIN FETCH e.poste JOIN FETCH m.equipe " +
                "WHERE m.equipe.service.id = :sid",
                MembreEquipe.class)
                .setParameter("sid", serviceId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void saveMembre(MembreEquipe m) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(m);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    /** Retire l'employé de toutes les équipes du service (avant ré-affectation). */
    public void retirerEmployeDesEquipesDuService(Long employeId, Long serviceId) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                "DELETE FROM MembreEquipe m WHERE m.employe.id = :eid AND m.equipe.service.id = :sid")
                .setParameter("eid", employeId).setParameter("sid", serviceId)
                .executeUpdate();
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ── Exceptions ────────────────────────────────────────────────────

    /** Exceptions pour un ensemble d'employés (tous régimes) sur une période. */
    public List<ExceptionPlanning> findExceptionsByEmployesAndPeriode(List<Long> employeIds, LocalDate debut, LocalDate fin) {
        if (employeIds == null || employeIds.isEmpty()) return new ArrayList<>();
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT x FROM ExceptionPlanning x JOIN FETCH x.employe " +
                "LEFT JOIN FETCH x.plage " +
                "WHERE x.employe.id IN (:ids) AND x.date BETWEEN :d AND :f",
                ExceptionPlanning.class)
                .setParameter("ids", employeIds).setParameter("d", debut).setParameter("f", fin)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void saveException(ExceptionPlanning x) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            ExceptionPlanning existante = session.createQuery(
                "FROM ExceptionPlanning x WHERE x.employe.id = :eid AND x.date = :d",
                ExceptionPlanning.class)
                .setParameter("eid", x.getEmploye().getId()).setParameter("d", x.getDate())
                .setMaxResults(1).uniqueResult();
            if (existante != null) {
                existante.setType(x.getType());
                existante.setPlage(x.getPlage());
                existante.setCommentaire(x.getCommentaire());
                session.merge(existante);
            } else {
                session.persist(x);
            }
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void deleteException(Long employeId, LocalDate date) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery("DELETE FROM ExceptionPlanning x WHERE x.employe.id = :eid AND x.date = :d")
                .setParameter("eid", employeId).setParameter("d", date)
                .executeUpdate();
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}
