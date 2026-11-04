package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Evenement;
import com.soutenance.entity.ParticipationEvenement;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class EvenementDao {

    private static final Logger LOG = LoggerFactory.getLogger(EvenementDao.class);

    @Autowired private SessionFactory sessionFactory;

    public Evenement findById(Long id) {
        try (Session s = sessionFactory.openSession()) {
            Evenement e = s.get(Evenement.class, id);
            if (e != null) Hibernate.initialize(e.getParticipations());
            return e;
        }
    }

    public List<Evenement> findAll() {
        try (Session s = sessionFactory.openSession()) {
            List<Evenement> evenements = s.createQuery(
                "select distinct e from Evenement e " +
                "left join fetch e.institution " +
                "order by e.dateDebut desc", Evenement.class).list();
            // Evenement.getNombreParticipants() lit cette collection après la
            // fermeture de la session (rendu JSF) — à initialiser ici.
            evenements.forEach(e -> Hibernate.initialize(e.getParticipations()));
            return evenements;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Evenement> findByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            List<Evenement> evenements = s.createQuery(
                "select e from Evenement e where e.institution.id = :iid " +
                "order by e.dateDebut desc", Evenement.class)
                .setParameter("iid", institutionId).list();
            evenements.forEach(e -> Hibernate.initialize(e.getParticipations()));
            return evenements;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Evenement> findAVenirByInstitution(Long institutionId, int limit) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select e from Evenement e where e.institution.id = :iid " +
                "and e.dateDebut > :now and e.statut != 'ANNULE' " +
                "order by e.dateDebut asc", Evenement.class)
                .setParameter("iid", institutionId)
                .setParameter("now", LocalDateTime.now())
                .setMaxResults(limit).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Événements auxquels un employé est invité. */
    public List<Evenement> findByParticipant(Long employeId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select e from Evenement e join e.participations p " +
                "where p.employe.id = :eid order by e.dateDebut desc",
                Evenement.class)
                .setParameter("eid", employeId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Somme des indemnités dues à un employé pour les événements rémunérés
     * (ex. séminaires) auxquels il a été PRESENT, sur une période donnée.
     * Utilisé par le calcul de paie.
     */
    public java.math.BigDecimal sumIndemnitesParticipations(Long employeId, LocalDateTime debut, LocalDateTime fin) {
        try (Session s = sessionFactory.openSession()) {
            java.math.BigDecimal result = s.createQuery(
                "SELECT SUM(e.indemniteParticipant) FROM ParticipationEvenement p " +
                "JOIN p.evenement e " +
                "WHERE p.employe.id = :eid " +
                "AND p.statut = 'PRESENT' " +
                "AND e.presenceValidee = true " +
                "AND e.indemniteParticipant IS NOT NULL " +
                "AND e.dateDebut >= :debut AND e.dateDebut <= :fin",
                java.math.BigDecimal.class)
                .setParameter("eid", employeId)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .uniqueResult();
            return result != null ? result : java.math.BigDecimal.ZERO;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return java.math.BigDecimal.ZERO; }
    }

    public long countByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            Long n = s.createQuery(
                "select count(e.id) from Evenement e where e.institution.id = :iid",
                Long.class).setParameter("iid", institutionId).uniqueResult();
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    public long countAVenirByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            Long n = s.createQuery(
                "select count(e.id) from Evenement e where e.institution.id = :iid " +
                "and e.dateDebut > :now and e.statut != 'ANNULE'", Long.class)
                .setParameter("iid", institutionId)
                .setParameter("now", LocalDateTime.now()).uniqueResult();
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    public void save(Evenement e) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.persist(e);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", ex);
        }
    }

    public void update(Evenement e) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.merge(e);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", ex);
        }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            Evenement e = s.get(Evenement.class, id);
            if (e != null) s.remove(e);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", ex);
        }
    }

    // ── Participations ──────────────────────────────────────────────────────

    public ParticipationEvenement findParticipationById(Long id) {
        try (Session s = sessionFactory.openSession()) {
            ParticipationEvenement p = s.get(ParticipationEvenement.class, id);
            if (p != null && p.getEvenement() != null) {
                Hibernate.initialize(p.getEvenement());
                Hibernate.initialize(p.getEvenement().getParticipations());
            }
            return p;
        }
    }

    public ParticipationEvenement findParticipation(Long evenementId, Long employeId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select p from ParticipationEvenement p " +
                "where p.evenement.id = :eid and p.employe.id = :uid",
                ParticipationEvenement.class)
                .setParameter("eid", evenementId)
                .setParameter("uid", employeId)
                .setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public void saveParticipation(ParticipationEvenement p) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.persist(p);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", ex);
        }
    }

    public void updateParticipation(ParticipationEvenement p) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.merge(p);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", ex);
        }
    }

    public void deleteParticipation(Long id) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            ParticipationEvenement p = s.get(ParticipationEvenement.class, id);
            if (p != null) s.remove(p);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", ex);
        }
    }
}
