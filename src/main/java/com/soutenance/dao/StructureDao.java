package com.soutenance.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class StructureDao {

    private static final Logger LOG = LoggerFactory.getLogger(StructureDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    // ══════════════════════════════════════════════════
    // DIRECTIONS
    // ══════════════════════════════════════════════════

    public Direction findDirectionById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Direction.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Direction> findAllDirections() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Direction> cq = cb.createQuery(Direction.class);
            Root<Direction> root = cq.from(Direction.class);
            root.fetch("chef", jakarta.persistence.criteria.JoinType.LEFT);
            cq.select(root).distinct(true).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** CORRECTION : filtre par institution + JOIN FETCH chef pour éviter N+1 */
    public List<Direction> findDirectionsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT DISTINCT d FROM Direction d " +
                "LEFT JOIN FETCH d.chef " +
                "WHERE d.institution.id = :iid ORDER BY d.nom",
                Direction.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** CORRECTION : divisions des directions d'une institution */
    public List<Division> findDivisionsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT dv FROM Division dv LEFT JOIN dv.direction dir " +
                "WHERE (dv.institution.id = :iid OR dir.institution.id = :iid) ORDER BY dv.nom",
                Division.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** CORRECTION : services des divisions d'une institution */
    public List<com.soutenance.entity.Service> findServicesByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT s FROM Service s LEFT JOIN s.division dv LEFT JOIN dv.direction dir " +
                "LEFT JOIN s.direction sd LEFT JOIN s.departement dp LEFT JOIN dp.direction dpd " +
                "WHERE (s.institution.id = :iid OR dv.institution.id = :iid " +
                "OR dir.institution.id = :iid OR sd.institution.id = :iid " +
                "OR dp.institution.id = :iid OR dpd.institution.id = :iid) ORDER BY s.nom",
                com.soutenance.entity.Service.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** CORRECTION : bureaux des services d'une institution */
    public List<Bureau> findBureauxByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT b FROM Bureau b LEFT JOIN b.service s " +
                "LEFT JOIN s.division dv LEFT JOIN dv.direction dir " +
                "WHERE (b.institution.id = :iid OR s.institution.id = :iid " +
                "OR dv.institution.id = :iid OR dir.institution.id = :iid) ORDER BY b.nom",
                Bureau.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Direction> findDirectionsActives() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Direction> cq = cb.createQuery(Direction.class);
            Root<Direction> root = cq.from(Direction.class);
            cq.select(root)
              .where(cb.equal(root.get("statut"), "ACTIVE"))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countDirections() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            cq.select(cb.count(cq.from(Direction.class)));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countDivisionsByDirection(Long directionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Division> root = cq.from(Division.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("direction").get("id"), directionId));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void saveDirection(Direction d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void updateDirection(Direction d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void deleteDirection(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Direction d = session.get(Direction.class, id);
            if (d != null) session.remove(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ══════════════════════════════════════════════════
    // DIVISIONS
    // ══════════════════════════════════════════════════

    public Division findDivisionById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Division.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Division> findAllDivisions() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Division> cq = cb.createQuery(Division.class);
            Root<Division> root = cq.from(Division.class);
            cq.select(root).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Division> findDivisionsByDirection(Long directionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Division> cq = cb.createQuery(Division.class);
            Root<Division> root = cq.from(Division.class);
            cq.select(root)
              .where(cb.equal(root.get("direction").get("id"), directionId))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countServicesByDivision(Long divisionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Service> root = cq.from(Service.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("division").get("id"), divisionId));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void saveDivision(Division d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void updateDivision(Division d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void deleteDivision(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Division d = session.get(Division.class, id);
            if (d != null) session.remove(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ══════════════════════════════════════════════════
    // SERVICES
    // ══════════════════════════════════════════════════

    public Service findServiceById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Service.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /**
     * Tous les services d'une institution, tous régimes confondus — rattachement direct,
     * via une division, ou via la direction d'une division (chaîne complète, cf.
     * Service.getInstitutionEffective()). Filtré côté Java car l'institution effective
     * dépend de la chaîne de parents, pas d'une seule colonne.
     */
    public List<Service> findAllServicesByInstitution(Long institutionId) {
        if (institutionId == null) return new ArrayList<>();
        try (Session session = sessionFactory.openSession()) {
            List<Service> tous = session.createQuery("FROM Service s ORDER BY s.nom", Service.class).list();
            List<Service> resultat = new ArrayList<>();
            for (Service s : tous) {
                Institution inst = s.getInstitutionEffective();
                if (inst != null && institutionId.equals(inst.getId())) resultat.add(s);
            }
            return resultat;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Service> findAllServices() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Service> cq = cb.createQuery(Service.class);
            Root<Service> root = cq.from(Service.class);
            cq.select(root).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Service> findServicesByDivision(Long divisionId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Service> cq = cb.createQuery(Service.class);
            Root<Service> root = cq.from(Service.class);
            cq.select(root)
              .where(cb.equal(root.get("division").get("id"), divisionId))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countBureauxByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<Bureau> root = cq.from(Bureau.class);
            cq.select(cb.count(root)).where(cb.equal(root.get("service").get("id"), serviceId));
            return session.createQuery(cq).uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public void saveService(Service s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void updateService(Service s) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void deleteService(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Service s = session.get(Service.class, id);
            if (s != null) session.remove(s);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ══════════════════════════════════════════════════
    // BUREAUX
    // ══════════════════════════════════════════════════

    public Bureau findBureauById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Bureau.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Bureau> findAllBureaux() {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Bureau> cq = cb.createQuery(Bureau.class);
            Root<Bureau> root = cq.from(Bureau.class);
            cq.select(root).orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Bureau> findBureauxByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Bureau> cq = cb.createQuery(Bureau.class);
            Root<Bureau> root = cq.from(Bureau.class);
            cq.select(root)
              .where(cb.equal(root.get("service").get("id"), serviceId))
              .orderBy(cb.asc(root.get("nom")));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void saveBureau(Bureau b) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(b);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void updateBureau(Bureau b) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(b);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    public void deleteBureau(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Bureau b = session.get(Bureau.class, id);
            if (b != null) session.remove(b);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); }
    }

    // ── Compteurs directs par Direction (évite les sous-boucles N+1) ──

    public long countServicesByDirection(Long directionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(s) FROM Service s " +
                "JOIN s.division d JOIN d.direction dir " +
                "WHERE dir.id = :dirId", Long.class)
                .setParameter("dirId", directionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countBureauxByDirection(Long directionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(b) FROM Bureau b " +
                "JOIN b.service s JOIN s.division d JOIN d.direction dir " +
                "WHERE dir.id = :dirId", Long.class)
                .setParameter("dirId", directionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countEmployesByDirection(Long directionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(e) FROM Employe e " +
                "JOIN e.serviceOrg s JOIN s.division d JOIN d.direction dir " +
                "WHERE dir.id = :dirId AND e.statut = 'ACTIF'", Long.class)
                .setParameter("dirId", directionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countEmployesByDivision(Long divisionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(e) FROM Employe e " +
                "JOIN e.serviceOrg s JOIN s.division d " +
                "WHERE d.id = :divId AND e.statut = 'ACTIF'", Long.class)
                .setParameter("divId", divisionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countEmployesByService(Long serviceId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(e) FROM Employe e " +
                "WHERE e.serviceOrg.id = :srvId AND e.statut = 'ACTIF'", Long.class)
                .setParameter("srvId", serviceId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countEmployesByBureau(Long bureauId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(e) FROM Employe e " +
                "WHERE e.bureauOrg.id = :burId AND e.statut = 'ACTIF'", Long.class)
                .setParameter("burId", bureauId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }

    public long countBureauxByDivision(Long divisionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT COUNT(b) FROM Bureau b JOIN b.service s WHERE s.division.id = :divId",
                Long.class)
                .setParameter("divId", divisionId)
                .uniqueResult();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0; }
    }


    // ══════════════════════════════════════════════════
    // DÉPARTEMENTS (niveau optionnel, rattaché à une Direction)
    // ══════════════════════════════════════════════════

    public Departement findDepartementById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(Departement.class, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<Departement> findDepartementsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            if (institutionId == null) {
                return session.createQuery("FROM Departement dp ORDER BY dp.nom", Departement.class).list();
            }
            return session.createQuery(
                "SELECT dp FROM Departement dp LEFT JOIN dp.direction dir " +
                "WHERE (dp.institution.id = :iid OR dir.institution.id = :iid) ORDER BY dp.nom",
                Departement.class)
                .setParameter("iid", institutionId)
                .list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public long countServicesByDepartement(Long departementId) {
        try (Session session = sessionFactory.openSession()) {
            Long n = session.createQuery(
                "SELECT COUNT(s) FROM Service s WHERE s.departement.id = :id", Long.class)
                .setParameter("id", departementId).uniqueResult();
            return n != null ? n : 0L;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return 0L; }
    }

    public void saveDepartement(Departement d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); throw new RuntimeException(e); }
    }

    public void updateDepartement(Departement d) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); throw new RuntimeException(e); }
    }

    public void deleteDepartement(Long id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            Departement d = session.get(Departement.class, id);
            if (d != null) session.remove(d);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("Erreur inattendue", e); throw new RuntimeException(e); }
    }
}