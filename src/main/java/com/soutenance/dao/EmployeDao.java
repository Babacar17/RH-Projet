package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Employe;
import jakarta.persistence.criteria.*;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class EmployeDao {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    public Employe findById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Employe e = session.get(Employe.class, id);
            if (e != null) {
                Hibernate.initialize(e.getPoste());
                Hibernate.initialize(e.getInstitution());
                Hibernate.initialize(e.getManager());
                Hibernate.initialize(e.getBureauOrg());
                if (e.getServiceOrg() != null) {
                    Hibernate.initialize(e.getServiceOrg());
                    if (e.getServiceOrg().getDivision() != null) {
                        Hibernate.initialize(e.getServiceOrg().getDivision());
                        Hibernate.initialize(e.getServiceOrg().getDivision().getDirection());
                    }
                }
            }
            return e;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Employe> findAll() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            Fetch<Employe, Object> svc = root.fetch("serviceOrg", JoinType.LEFT);
            Fetch<Object, Object> div = svc.fetch("division", JoinType.LEFT);
            div.fetch("direction", JoinType.LEFT);
            root.fetch("bureauOrg", JoinType.LEFT);
            root.fetch("poste", JoinType.LEFT);
            root.fetch("institution", JoinType.LEFT);
            root.fetch("manager", JoinType.LEFT);
            cq.select(root).distinct(true)
              .orderBy(cb.asc(root.get("nom")), cb.asc(root.get("prenom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Employe> findActifs() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            Fetch<Employe, Object> svc = root.fetch("serviceOrg", JoinType.LEFT);
            Fetch<Object, Object> div = svc.fetch("division", JoinType.LEFT);
            div.fetch("direction", JoinType.LEFT);
            root.fetch("bureauOrg", JoinType.LEFT);
            root.fetch("poste", JoinType.LEFT);
            root.fetch("grilleSalariale", JoinType.LEFT);
            cq.select(root).distinct(true)
              .where(cb.equal(root.get("statut"), "ACTIF"))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Retourne tous les employés actifs appartenant à une institution donnée.
     * Utilisé notamment dans DocumentService (getEmployesPourGed).
     */
    public List<Employe> findActifsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT e FROM Employe e " +
                "LEFT JOIN FETCH e.serviceOrg s " +
                "LEFT JOIN FETCH s.division d " +
                "LEFT JOIN FETCH d.direction " +
                "LEFT JOIN FETCH e.bureauOrg " +
                "LEFT JOIN FETCH e.poste " +
                "LEFT JOIN FETCH e.grilleSalariale " +
                "WHERE e.statut = 'ACTIF' " +
                "AND e.institution.id = :institutionId " +
                "ORDER BY e.nom, e.prenom",
                Employe.class)
                .setParameter("institutionId", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Retourne tous les employés (tous statuts) d'une institution.
     * Utilisé dans ReportingService (countTotalEmployes, getRepartitionStatuts…).
     */
    public List<Employe> findByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT e FROM Employe e " +
                "LEFT JOIN FETCH e.serviceOrg s " +
                "LEFT JOIN FETCH s.division d " +
                "LEFT JOIN FETCH d.direction " +
                "LEFT JOIN FETCH e.bureauOrg " +
                "LEFT JOIN FETCH e.poste " +
                "WHERE e.institution.id = :institutionId " +
                "ORDER BY e.nom, e.prenom",
                Employe.class)
                .setParameter("institutionId", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Retourne les employés d'une institution filtrés par statut.
     * Utilisé dans ReportingService (countEmployesActifs, getSalaireMoyen…).
     */
    public List<Employe> findByInstitutionAndStatut(Long institutionId, String statut) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT e FROM Employe e " +
                "LEFT JOIN FETCH e.serviceOrg s " +
                "LEFT JOIN FETCH s.division d " +
                "LEFT JOIN FETCH d.direction " +
                "LEFT JOIN FETCH e.bureauOrg " +
                "LEFT JOIN FETCH e.poste " +
                "WHERE e.institution.id = :institutionId " +
                "AND e.statut = :statut " +
                "ORDER BY e.nom, e.prenom",
                Employe.class)
                .setParameter("institutionId", institutionId)
                .setParameter("statut", statut)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Employe> search(String terme) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            root.fetch("serviceOrg", JoinType.LEFT).fetch("division", JoinType.LEFT)
                .fetch("direction", JoinType.LEFT);
            root.fetch("bureauOrg", JoinType.LEFT);
            root.fetch("poste", JoinType.LEFT);
            String pattern = "%" + terme.toLowerCase() + "%";
            Predicate p = cb.or(
                cb.like(cb.lower(root.get("nom")),       pattern),
                cb.like(cb.lower(root.get("prenom")),    pattern),
                cb.like(cb.lower(root.get("email")),     pattern),
                cb.like(cb.lower(root.get("matricule")), pattern)
            );
            cq.select(root).distinct(true).where(p).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Employe> findByManager(Long managerId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            root.fetch("serviceOrg", JoinType.LEFT).fetch("division", JoinType.LEFT)
                .fetch("direction", JoinType.LEFT);
            root.fetch("bureauOrg", JoinType.LEFT);
            root.fetch("poste", JoinType.LEFT);
            cq.select(root).distinct(true)
              .where(cb.and(
                  cb.equal(root.get("manager").get("id"), managerId),
                  cb.equal(root.get("statut"), "ACTIF")
              ))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public Employe findByEmail(String email) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            cq.select(root).where(cb.equal(root.get("email"), email));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Recherche l'employé lié à un compte utilisateur donné (User.id). */
    public Employe findByUserId(Long userId) {
        if (userId == null) return null;
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "select e from Employe e where e.user.id = :uid", Employe.class)
                .setParameter("uid", userId)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public Employe findByUsernameOrEmail(String username, String email) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();

            CriteriaQuery<Employe> cq1 = cb.createQuery(Employe.class);
            Root<Employe> root1 = cq1.from(Employe.class);
            root1.fetch("serviceOrg", JoinType.LEFT).fetch("division", JoinType.LEFT)
                 .fetch("direction", JoinType.LEFT);
            root1.fetch("bureauOrg", JoinType.LEFT);
            root1.fetch("poste", JoinType.LEFT);
            Join<Employe, Object> userJoin1 = root1.join("user", JoinType.INNER);
            cq1.select(root1).distinct(true)
               .where(cb.equal(userJoin1.get("username"), username));
            Employe byUsername = session.createQuery(cq1).setMaxResults(1).uniqueResult();
            if (byUsername != null) return byUsername;

            CriteriaQuery<Employe> cq2 = cb.createQuery(Employe.class);
            Root<Employe> root2 = cq2.from(Employe.class);
            root2.fetch("serviceOrg", JoinType.LEFT).fetch("division", JoinType.LEFT)
                 .fetch("direction", JoinType.LEFT);
            root2.fetch("bureauOrg", JoinType.LEFT);
            root2.fetch("poste", JoinType.LEFT);
            cq2.select(root2).distinct(true)
               .where(cb.equal(root2.get("email"), email));
            return session.createQuery(cq2).setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Employe> findByDirection(Long directionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            Fetch<Employe, Object> svcFetch = root.fetch("serviceOrg", JoinType.LEFT);
            Fetch<Object, Object> divFetch  = svcFetch.fetch("division", JoinType.LEFT);
            divFetch.fetch("direction", JoinType.LEFT);
            root.fetch("bureauOrg", JoinType.LEFT);
            root.fetch("poste", JoinType.LEFT);
            Join<Employe, Object> svcJoin = root.join("serviceOrg", JoinType.LEFT);
            Join<Object, Object> divJoin  = svcJoin.join("division", JoinType.LEFT);
            Join<Object, Object> dirJoin  = divJoin.join("direction", JoinType.LEFT);
            cq.select(root).distinct(true)
              .where(cb.equal(dirJoin.get("id"), directionId))
              .orderBy(cb.asc(root.get("niveauHierarchique")),
                       cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Employe> findByDivision(Long divisionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT e FROM Employe e " +
                "LEFT JOIN FETCH e.serviceOrg s " +
                "LEFT JOIN FETCH s.division d " +
                "LEFT JOIN FETCH d.direction " +
                "LEFT JOIN FETCH e.bureauOrg " +
                "LEFT JOIN FETCH e.poste " +
                "WHERE d.id = :divId " +
                "ORDER BY e.niveauHierarchique, e.nom, e.prenom",
                Employe.class)
                .setParameter("divId", divisionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Employe> findByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT e FROM Employe e " +
                "LEFT JOIN FETCH e.serviceOrg s " +
                "LEFT JOIN FETCH s.division d " +
                "LEFT JOIN FETCH d.direction " +
                "LEFT JOIN FETCH e.bureauOrg " +
                "LEFT JOIN FETCH e.poste " +
                "WHERE s.id = :srvId " +
                "ORDER BY e.niveauHierarchique, e.nom, e.prenom",
                Employe.class)
                .setParameter("srvId", serviceId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Employe> findByBureau(Long bureauId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT e FROM Employe e " +
                "LEFT JOIN FETCH e.serviceOrg s " +
                "LEFT JOIN FETCH s.division d " +
                "LEFT JOIN FETCH d.direction " +
                "LEFT JOIN FETCH e.bureauOrg b " +
                "LEFT JOIN FETCH e.poste " +
                "WHERE b.id = :burId " +
                "ORDER BY e.nom, e.prenom",
                Employe.class)
                .setParameter("burId", bureauId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public Employe findByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Employe> cq = cb.createQuery(Employe.class);
            Root<Employe> root = cq.from(Employe.class);
            root.fetch("serviceOrg", JoinType.LEFT).fetch("division", JoinType.LEFT)
                .fetch("direction", JoinType.LEFT);
            root.fetch("bureauOrg", JoinType.LEFT);
            root.fetch("poste", JoinType.LEFT);
            Join<Employe, Object> userJoin = root.join("user", JoinType.INNER);
            cq.select(root).distinct(true)
              .where(cb.equal(userJoin.get("username"), username));
            return session.createQuery(cq).setMaxResults(1).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    // ══════════════════════════════════════════════════
    // CORRECTION : méthode manquante appelée par PosteService
    // ══════════════════════════════════════════════════

    /**
     * Retourne l'employé actuellement affecté à un poste donné.
     * Suppose que l'entité Employe possède un champ @ManyToOne "poste".
     */
    public Employe findByPoste(Long posteId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT e FROM Employe e WHERE e.poste.id = :posteId",
                Employe.class)
                .setParameter("posteId", posteId)
                .setMaxResults(1)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    // ══════════════════════════════════════════════════
    // COMPTAGES
    // ══════════════════════════════════════════════════

    public long countTotal() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            cq.select(cb.count(cq.from(Employe.class)));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countByStatut(String statut) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Employe> root = cq.from(Employe.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("statut"), statut));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    // ══════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════

    public void save(Employe e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    public void update(Employe e) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }

    public void delete(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Employe e = session.get(Employe.class, id);
            if (e != null) session.remove(e);
            tx.commit();
        } catch (Exception ex) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", ex); }
    }
}