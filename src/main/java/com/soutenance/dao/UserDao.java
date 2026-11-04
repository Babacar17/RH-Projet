package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.User;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserDao {

    private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public User findByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            root.fetch("roles", JoinType.LEFT);
            cq.select(root).where(cb.equal(root.get("username"), username));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    public User findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            root.fetch("roles", JoinType.LEFT);
            cq.select(root).where(cb.equal(root.get("id"), id));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    /** Recherche un utilisateur par son email (pour les flows reset mdp). */
    public List<User> findActifsByRoleAndInstitution(String roleNom, Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            Join<User, Object> role = root.join("roles");
            cq.select(root).distinct(true)
              .where(
                  cb.equal(role.get("nom"), roleNom),
                  cb.isTrue(root.get("actif")),
                  cb.equal(root.get("institution").get("id"), institutionId));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            cq.select(root).where(cb.equal(cb.lower(root.get("email")), email.trim().toLowerCase()));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    public List<User> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            cq.select(root).orderBy(cb.desc(root.get("dateCreation")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<User> findActifs() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            cq.select(root).where(cb.isTrue(root.get("actif")));
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public List<User> rechercherParNom(String motCle) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<User> cq = cb.createQuery(User.class);
            Root<User> root = cq.from(User.class);
            String pattern = "%" + motCle.toLowerCase() + "%";

            // Concaténation prénom + " " + nom pour recherche combinée
            Expression<String> prenomNom = cb.lower(
                cb.concat(cb.concat(root.get("prenom"), " "), root.get("nom"))
            );
            Expression<String> nomPrenom = cb.lower(
                cb.concat(cb.concat(root.get("nom"), " "), root.get("prenom"))
            );

            Predicate p = cb.or(
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(cb.lower(root.get("email")),    pattern),
                cb.like(cb.lower(root.get("nom")),      pattern),
                cb.like(cb.lower(root.get("prenom")),   pattern),
                cb.like(prenomNom, pattern),   // "Moussa Diallo"
                cb.like(nomPrenom, pattern)    // "Diallo Moussa"
            );
            cq.select(root).where(p);
            return session.createQuery(cq).list();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            return new ArrayList<>();
        }
    }

    public long countTotal() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<User> root = cq.from(User.class);
            cq.select(cb.count(root));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countActifs() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<User> root = cq.from(User.class);
            cq.select(cb.count(root)).where(cb.isTrue(root.get("actif")));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countInactifs() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<User> root = cq.from(User.class);
            cq.select(cb.count(root)).where(cb.isFalse(root.get("actif")));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }
    public long countByInstitution(Long institutionId) {
        try (Session s = sessionFactory.openSession()) {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<User> r = cq.from(User.class);
            cq.select(cb.count(r)).where(cb.equal(r.get("institution").get("id"), institutionId));
            Long res = s.createQuery(cq).uniqueResult();
            return res != null ? res : 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }
 

    public void save(User user) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.persist(user);
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw new RuntimeException("Erreur lors de la sauvegarde de l'utilisateur", e);
            }
        }
    }

    public void update(User user) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.merge(user);
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw new RuntimeException("Erreur lors de la mise à jour de l'utilisateur", e);
            }
        }
    }

    public void delete(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                User user = session.get(User.class, id);
                if (user != null) session.remove(user);
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw new RuntimeException("Erreur lors de la suppression de l'utilisateur", e);
            }
        }
    }

    public void toggleActif(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                User user = session.get(User.class, id);
                if (user != null) {
                    user.setActif(!user.isActif());
                    session.merge(user);
                }
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw new RuntimeException("Erreur lors du toggle actif", e);
            }
        }
    }

    public void updateDerniereConnexion(String username) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaUpdate<User> cu = cb.createCriteriaUpdate(User.class);
                Root<User> root = cu.from(User.class);
                cu.set(root.get("derniereConnexion"), LocalDateTime.now())
                  .where(cb.equal(root.get("username"), username));
                session.createMutationQuery(cu).executeUpdate();
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw new RuntimeException("Erreur lors de la mise à jour de la dernière connexion", e);
            }
        }
    }
}