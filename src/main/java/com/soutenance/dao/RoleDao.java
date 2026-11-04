package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RoleDao {

    private static final Logger LOG = LoggerFactory.getLogger(RoleDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public List<Role> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Role> cq = cb.createQuery(Role.class);
            cq.from(Role.class);
            return session.createQuery(cq).list();
        }
    }

    public Role findByNom(String nom) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Role> cq = cb.createQuery(Role.class);
            Root<Role> root = cq.from(Role.class);
            cq.select(root).where(cb.equal(root.get("nom"), nom));
            return session.createQuery(cq).uniqueResult();
        }
    }

    public Role findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Role.class, id);
        }
    }

    public void save(Role role) {
        Session session = null;
        Transaction tx = null;
        try {
            session = sessionFactory.openSession();
            tx = session.beginTransaction();
            session.persist(role);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        } finally {
            if (session != null) session.close();
        }
    }

    public void update(Role role) {
        Session session = null;
        Transaction tx = null;
        try {
            session = sessionFactory.openSession();
            tx = session.beginTransaction();
            session.merge(role);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
        } finally {
            if (session != null) session.close();
        }
    }

    /**
     * Supprime un rôle s'il n'est utilisé par aucun utilisateur.
     * Retourne le nombre d'utilisateurs qui empêchent la suppression
     * (0 = suppression effectuée, >0 = blocage).
     */
    public int delete(Long id) {
        Session session = null;
        Transaction tx  = null;
        try {
            session = sessionFactory.openSession();
            tx = session.beginTransaction();

            // Vérifier qu'aucun User n'est lié à ce rôle
            Long nbUsers = session.createQuery(
                    "select count(u.id) from User u join u.roles r where r.id = :rid", Long.class)
                .setParameter("rid", id)
                .uniqueResult();

            if (nbUsers != null && nbUsers > 0) {
                tx.rollback();
                return nbUsers.intValue();
            }

            Role r = session.get(Role.class, id);
            if (r != null) session.remove(r);
            tx.commit();
            return 0;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            LOG.error("Erreur inattendue", e);
            return -1;
        } finally {
            if (session != null) session.close();
        }
    }

    /** Initialise les rôles par défaut si absents */
    public void initRolesParDefaut() {
        String[][] roles = {
            {"ADMIN",    "Administrateur système – accès total"},
            {"RH",       "Responsable Ressources Humaines"},
            {"MANAGER",  "Chef de structure / Manager d'équipe"},
            {"EMPLOYE",  "Employé standard"},
            {"AUDITEUR", "Auditeur – lecture seule"},
            {"CANDIDAT", "Candidat externe (recrutement)"}
        };
        for (String[] r : roles) {
            if (findByNom(r[0]) == null) {
                save(new Role(r[0], r[1]));
                System.out.println("✓ Rôle créé : " + r[0]);
            }
        }
    }
}