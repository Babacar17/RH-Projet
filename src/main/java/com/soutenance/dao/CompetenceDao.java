package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Competence;
import com.soutenance.entity.CompetenceEmploye;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CompetenceDao {

    private static final Logger LOG = LoggerFactory.getLogger(CompetenceDao.class);

    @Autowired private SessionFactory sessionFactory;

    // ── Compétences (référentiel) ───────────────────────────────────────────

    public Competence findById(Long id) {
        try (Session s = sessionFactory.openSession()) { return s.get(Competence.class, id); }
    }

    public List<Competence> findByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select c from Competence c where c.institution.id = :iid " +
                "order by c.categorie asc, c.ordreAffichage asc, c.libelle asc",
                Competence.class)
                .setParameter("iid", institutionId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Competence> findAll() {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select c from Competence c order by c.categorie, c.libelle",
                Competence.class).list();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void save(Competence c) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.persist(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Competence c) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.merge(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            Competence c = s.get(Competence.class, id);
            if (c != null) s.remove(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ── CompetenceEmploye ───────────────────────────────────────────────────

    public CompetenceEmploye findCompetenceEmploye(Long employeId, Long competenceId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select c from CompetenceEmploye c " +
                "where c.employe.id = :eid and c.competence.id = :cid",
                CompetenceEmploye.class)
                .setParameter("eid", employeId)
                .setParameter("cid", competenceId)
                .setMaxResults(1).uniqueResult();
        } catch (Exception e) { return null; }
    }

    public List<CompetenceEmploye> findByEmploye(Long employeId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select c from CompetenceEmploye c where c.employe.id = :eid",
                CompetenceEmploye.class)
                .setParameter("eid", employeId).list();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    /** Liens employé↔compétence de l'institution (renommée : dupliquait findByInstitution). */
    public List<CompetenceEmploye> findLiensByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select c from CompetenceEmploye c " +
                "where c.competence.institution.id = :iid",
                CompetenceEmploye.class)
                .setParameter("iid", institutionId).list();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void saveCompetenceEmploye(CompetenceEmploye c) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.persist(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void updateCompetenceEmploye(CompetenceEmploye c) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.merge(c);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}
