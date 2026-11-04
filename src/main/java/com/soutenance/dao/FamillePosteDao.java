package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.FamillePoste;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour l'entité FamillePoste.
 *
 * À enregistrer dans la configuration Hibernate :
 *   hibernate.cfg.xml ou persistence.xml → ajouter
 *   <mapping class="com.soutenance.entity.FamillePoste"/>
 */
@Repository
public class FamillePosteDao {

    private static final Logger LOG = LoggerFactory.getLogger(FamillePosteDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public FamillePoste findById(Long id) {
        try (Session s = sessionFactory.openSession()) {
            return s.get(FamillePoste.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<FamillePoste> findAll() {
        try (Session s = sessionFactory.openSession()) {
            return s.createQuery(
                "FROM FamillePoste f WHERE f.actif = true ORDER BY f.nom",
                FamillePoste.class).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(FamillePoste f) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.persist(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(FamillePoste f) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            s.merge(f);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session s = sessionFactory.openSession()) {
            tx = s.beginTransaction();
            FamillePoste f = s.get(FamillePoste.class, id);
            if (f != null) { f.setActif(false); s.merge(f); } // soft delete
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}