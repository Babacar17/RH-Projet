package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Permission;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class PermissionDao {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Permission findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Permission.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public Permission findByCode(String code) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Permission> cq = cb.createQuery(Permission.class);
            Root<Permission> root = cq.from(Permission.class);
            cq.select(root).where(cb.equal(root.get("code"), code));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Permission> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Permission> cq = cb.createQuery(Permission.class);
            Root<Permission> root = cq.from(Permission.class);
            cq.select(root).orderBy(cb.asc(root.get("module")), cb.asc(root.get("action")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Permissions d'un module fonctionnel (EMPLOYE, CONGE, PAIE…). */
    public List<Permission> findByModule(String module) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Permission> cq = cb.createQuery(Permission.class);
            Root<Permission> root = cq.from(Permission.class);
            cq.select(root)
              .where(cb.equal(root.get("module"), module))
              .orderBy(cb.asc(root.get("action")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Permissions associées à un rôle. */
    public List<Permission> findByRole(Long roleId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM Permission p JOIN p.roles r WHERE r.id = :rid " +
                "ORDER BY p.module ASC, p.action ASC",
                Permission.class)
                .setParameter("rid", roleId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Initialise les permissions par défaut si absentes. */
    public void initPermissionsParDefaut() {
        String[][] perms = {
            // Module EMPLOYE
            {"EMPLOYE_LIRE",      "Consulter les employés",          "EMPLOYE",     "LIRE"},
            {"EMPLOYE_CREER",     "Créer un employé",                "EMPLOYE",     "CREER"},
            {"EMPLOYE_MODIFIER",  "Modifier un employé",             "EMPLOYE",     "MODIFIER"},
            {"EMPLOYE_SUPPRIMER", "Supprimer un employé",            "EMPLOYE",     "SUPPRIMER"},
            // Module CONGE
            {"CONGE_LIRE",        "Consulter les congés",            "CONGE",       "LIRE"},
            {"CONGE_APPROUVER",   "Approuver une demande de congé",  "CONGE",       "APPROUVER"},
            {"CONGE_REFUSER",     "Refuser une demande de congé",    "CONGE",       "APPROUVER"},
            // Module PAIE
            {"PAIE_LIRE",         "Consulter la paie",               "PAIE",        "LIRE"},
            {"PAIE_VALIDER",      "Valider un bulletin de paie",     "PAIE",        "VALIDER"},
            {"PAIE_EXPORTER",     "Exporter les données de paie",    "PAIE",        "EXPORTER"},
            // Module RECRUTEMENT
            {"RECRUTEMENT_LIRE",     "Consulter les offres/candidatures", "RECRUTEMENT", "LIRE"},
            {"RECRUTEMENT_CREER",    "Créer une offre d'emploi",          "RECRUTEMENT", "CREER"},
            {"RECRUTEMENT_VALIDER",  "Valider une candidature",           "RECRUTEMENT", "VALIDER"},
            // Module EVALUATION
            {"EVALUATION_LIRE",    "Consulter les évaluations",      "EVALUATION",  "LIRE"},
            {"EVALUATION_CREER",   "Créer une évaluation",           "EVALUATION",  "CREER"},
            {"EVALUATION_VALIDER", "Valider une évaluation",         "EVALUATION",  "VALIDER"},
            // Module ADMIN
            {"ADMIN_UTILISATEURS", "Gérer les utilisateurs",         "ADMIN",       "MODIFIER"},
            {"ADMIN_ROLES",        "Gérer les rôles et permissions",  "ADMIN",       "MODIFIER"},
            {"ADMIN_PARAMETRAGE",  "Paramétrer l'application",       "ADMIN",       "MODIFIER"},
            // Module AUDIT
            {"AUDIT_LIRE",         "Consulter les logs d'audit",     "AUDIT",       "LIRE"},
            {"AUDIT_EXPORTER",     "Exporter les logs d'audit",      "AUDIT",       "EXPORTER"},
        };
        for (String[] p : perms) {
            if (findByCode(p[0]) == null) {
                save(new Permission(p[0], p[1], p[2], p[3]));
                System.out.println("✓ Permission créée : " + p[0]);
            }
        }
    }

    public void save(Permission p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void update(Permission p) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Permission p = session.get(Permission.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }
}