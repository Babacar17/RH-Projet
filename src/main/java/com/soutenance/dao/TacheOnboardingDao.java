package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.TacheOnboarding;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class TacheOnboardingDao {

    private static final Logger LOG = LoggerFactory.getLogger(TacheOnboardingDao.class);

    @Autowired private SessionFactory sessionFactory;

    public TacheOnboarding findById(Long id) {
        try (Session s = sessionFactory.openSession()) { return s.get(TacheOnboarding.class, id); }
    }

    public List<TacheOnboarding> findByEmployeId(Long employeId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select t from TacheOnboarding t where t.employe.id = :eid " +
                "order by t.ordre asc, t.id asc", TacheOnboarding.class)
                .setParameter("eid", employeId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<TacheOnboarding> findEnCoursByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "select t from TacheOnboarding t " +
                "where t.employe.institution.id = :iid and t.statut != 'FAIT' " +
                "order by t.dateLimite asc",
                TacheOnboarding.class)
                .setParameter("iid", institutionId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countNonFaitesByEmploye(Long employeId) {
        try (Session s = sessionFactory.openSession()) {
            Long n = s.createQuery(
                "select count(t.id) from TacheOnboarding t " +
                "where t.employe.id = :eid and t.statut != 'FAIT'", Long.class)
                .setParameter("eid", employeId).uniqueResult();
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    public void save(TacheOnboarding t) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.persist(t);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void update(TacheOnboarding t) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.merge(t);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            TacheOnboarding t = s.get(TacheOnboarding.class, id);
            if (t != null) s.remove(t);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        }
    }
}
