package com.soutenance.dao;

import com.soutenance.entity.*;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO consolidé pour le workflow "concours de recrutement" (référentiel
 * d'évaluation, jury, notation, délibération). Les entités de ce module
 * sont nombreuses mais suivent toutes le même cycle CRUD simple — un DAO
 * générique par entité serait une pure répétition de boilerplate, d'où un
 * DAO unique regroupant persist/merge/remove et les requêtes spécifiques.
 */
@Repository
public class RecrutementDao {

    private static final Logger LOG = LoggerFactory.getLogger(RecrutementDao.class);

    @Autowired
    private SessionFactory sessionFactory;

    // ── Générique ──────────────────────────────────────────────────────────

    public <T> T find(Class<T> type, Long id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(type, id);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public <T> List<T> findAll(Class<T> type) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(type);
            cq.select(cq.from(type));
            return session.createQuery(cq).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public void save(Object entity) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.persist(entity);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
            throw new RuntimeException("Erreur lors de l'enregistrement : " + e.getMessage(), e);
        }
    }

    public void update(Object entity) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.merge(entity);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
            throw new RuntimeException("Erreur lors de la mise a jour : " + e.getMessage(), e);
        }
    }

    public void delete(Object entity) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.remove(session.merge(entity));
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("Erreur inattendue", e);
            throw new RuntimeException("Erreur lors de la suppression : " + e.getMessage(), e);
        }
    }

    // ── Référentiels ─────────────────────────────────────────────────────

    public List<ReferentielEvaluation> findReferentielsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ReferentielEvaluation r WHERE r.institution.id = :iid ORDER BY r.libelle",
                ReferentielEvaluation.class)
                .setParameter("iid", institutionId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<PhaseEvaluation> findPhasesByReferentiel(Long referentielId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM PhaseEvaluation p WHERE p.referentiel.id = :rid ORDER BY p.numeroOrdre",
                PhaseEvaluation.class)
                .setParameter("rid", referentielId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<Rubrique> findRubriquesByPhase(Long phaseId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM Rubrique r WHERE r.phase.id = :pid ORDER BY r.id",
                Rubrique.class)
                .setParameter("pid", phaseId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<ElementRubrique> findElementsByRubrique(Long rubriqueId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ElementRubrique e WHERE e.rubrique.id = :rid ORDER BY e.id",
                ElementRubrique.class)
                .setParameter("rid", rubriqueId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Tous les éléments notables d'un référentiel, dans l'ordre phase > rubrique > élément. */
    public List<ElementRubrique> findElementsByReferentiel(Long referentielId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ElementRubrique e " +
                "WHERE e.rubrique.phase.referentiel.id = :rid " +
                "ORDER BY e.rubrique.phase.numeroOrdre, e.rubrique.id, e.id",
                ElementRubrique.class)
                .setParameter("rid", referentielId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ── Comités & évaluateurs ────────────────────────────────────────────

    public List<Comite> findComitesByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM Comite c WHERE c.offre.id = :oid ORDER BY c.dateCreation DESC",
                Comite.class)
                .setParameter("oid", offreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<ComiteEvaluateur> findMembresByComite(Long comiteId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ComiteEvaluateur m JOIN FETCH m.evaluateur WHERE m.comite.id = :cid",
                ComiteEvaluateur.class)
                .setParameter("cid", comiteId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Comités où cet employé siège comme membre du jury, offre la plus récente en premier. */
    public List<ComiteEvaluateur> findMembresByEvaluateur(Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ComiteEvaluateur m " +
                "JOIN FETCH m.comite c " +
                "JOIN FETCH c.offre " +
                "WHERE m.evaluateur.id = :eid " +
                "ORDER BY c.dateCreation DESC",
                ComiteEvaluateur.class)
                .setParameter("eid", employeId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Tous les membres du jury de cette offre, avec leur compte utilisateur chargé (pour notification). */
    public List<ComiteEvaluateur> findMembresJuryAvecUserByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT m FROM ComiteEvaluateur m " +
                "JOIN FETCH m.evaluateur e LEFT JOIN FETCH e.user " +
                "WHERE m.comite.offre.id = :oid",
                ComiteEvaluateur.class)
                .setParameter("oid", offreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public boolean isDejaMembre(Long comiteId, Long employeId) {
        try (Session session = sessionFactory.openSession()) {
            Long n = session.createQuery(
                "SELECT COUNT(m) FROM ComiteEvaluateur m WHERE m.comite.id = :cid AND m.evaluateur.id = :eid",
                Long.class)
                .setParameter("cid", comiteId).setParameter("eid", employeId)
                .uniqueResult();
            return n != null && n > 0;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return false; }
    }

    // ── Phases lancées ───────────────────────────────────────────────────

    public List<PhaseOffre> findPhasesOffreByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM PhaseOffre p JOIN FETCH p.phase WHERE p.offre.id = :oid ORDER BY p.phase.numeroOrdre",
                PhaseOffre.class)
                .setParameter("oid", offreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Phases planifiées dont la date de début prévue est atteinte — pour le lancement automatique (scheduler). */
    public List<PhaseOffre> findPhasesPlanifieesALancer(java.time.LocalDateTime maintenant) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM PhaseOffre p JOIN FETCH p.phase JOIN FETCH p.offre " +
                "WHERE p.statut = 'PLANIFIEE' AND p.dateDebutPrevue IS NOT NULL AND p.dateDebutPrevue <= :maintenant",
                PhaseOffre.class)
                .setParameter("maintenant", maintenant).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Phases en cours dont la date de fin prévue est dépassée — pour la clôture automatique (scheduler). */
    public List<PhaseOffre> findPhasesEnCoursDelaiDepasse(java.time.LocalDateTime maintenant) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM PhaseOffre p JOIN FETCH p.phase JOIN FETCH p.offre " +
                "WHERE p.statut = 'EN_COURS' AND p.dateLimite IS NOT NULL AND p.dateLimite <= :maintenant",
                PhaseOffre.class)
                .setParameter("maintenant", maintenant).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ── Notation ─────────────────────────────────────────────────────────

    public NoteCandidatEvalElement findNote(Long phaseOffreId, Long candidatureId, Long elementId, Long evaluateurId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM NoteCandidatEvalElement n WHERE n.phaseOffre.id = :poid " +
                "AND n.candidature.id = :cid AND n.element.id = :elid AND n.evaluateur.id = :evid",
                NoteCandidatEvalElement.class)
                .setParameter("poid", phaseOffreId).setParameter("cid", candidatureId)
                .setParameter("elid", elementId).setParameter("evid", evaluateurId)
                .uniqueResultOptional().orElse(null);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<NoteCandidatEvalElement> findNotesByPhaseOffre(Long phaseOffreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT n FROM NoteCandidatEvalElement n " +
                "JOIN FETCH n.candidature " +
                "JOIN FETCH n.element el " +
                "JOIN FETCH el.rubrique " +
                "WHERE n.phaseOffre.id = :poid",
                NoteCandidatEvalElement.class)
                .setParameter("poid", phaseOffreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Toutes les notes d'un candidat, toutes phases de son offre confondues — détail par membre du jury. */
    public List<NoteCandidatEvalElement> findNotesByCandidature(Long candidatureId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT n FROM NoteCandidatEvalElement n " +
                "JOIN FETCH n.evaluateur " +
                "JOIN FETCH n.element el JOIN FETCH el.rubrique " +
                "JOIN FETCH n.phaseOffre po JOIN FETCH po.phase " +
                "WHERE n.candidature.id = :cid " +
                "ORDER BY po.phase.numeroOrdre, el.rubrique.libelle, n.evaluateur.nom",
                NoteCandidatEvalElement.class)
                .setParameter("cid", candidatureId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<NoteCandidatEvalElement> findNotesByPhaseOffreEtCandidature(Long phaseOffreId, Long candidatureId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT n FROM NoteCandidatEvalElement n " +
                "JOIN FETCH n.candidature " +
                "JOIN FETCH n.element el " +
                "JOIN FETCH el.rubrique " +
                "WHERE n.phaseOffre.id = :poid AND n.candidature.id = :cid",
                NoteCandidatEvalElement.class)
                .setParameter("poid", phaseOffreId).setParameter("cid", candidatureId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    // ── Résultats & décision ─────────────────────────────────────────────

    public List<ResultatPhaseCandidature> findResultatsByPhaseOffre(Long phaseOffreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT r FROM ResultatPhaseCandidature r JOIN FETCH r.candidature " +
                "WHERE r.phaseOffre.id = :poid ORDER BY r.rang",
                ResultatPhaseCandidature.class)
                .setParameter("poid", phaseOffreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public ResultatPhaseCandidature findResultat(Long phaseOffreId, Long candidatureId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM ResultatPhaseCandidature r WHERE r.phaseOffre.id = :poid AND r.candidature.id = :cid",
                ResultatPhaseCandidature.class)
                .setParameter("poid", phaseOffreId).setParameter("cid", candidatureId)
                .uniqueResultOptional().orElse(null);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    /** Tous les résultats d'un candidat, toutes phases confondues — pour le total cumulé affiché sur les candidatures. */
    public List<ResultatPhaseCandidature> findResultatsByCandidature(Long candidatureId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT r FROM ResultatPhaseCandidature r " +
                "JOIN FETCH r.phaseOffre po JOIN FETCH po.phase " +
                "WHERE r.candidature.id = :cid ORDER BY po.phase.numeroOrdre",
                ResultatPhaseCandidature.class)
                .setParameter("cid", candidatureId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<RapportPhaseEvaluation> findRapportsByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            List<RapportPhaseEvaluation> rapports = session.createQuery(
                "SELECT r FROM RapportPhaseEvaluation r " +
                "JOIN FETCH r.phaseOffre po JOIN FETCH po.offre o JOIN FETCH po.phase " +
                "LEFT JOIN FETCH o.institution " +
                "WHERE r.phaseOffre.offre.id = :oid ORDER BY r.dateCreation DESC",
                RapportPhaseEvaluation.class)
                .setParameter("oid", offreId).list();
            initialiserCollectionsRapport(rapports);
            return rapports;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<PVDeliberation> findPvByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PVDeliberation p JOIN FETCH p.offre o LEFT JOIN FETCH o.institution " +
                "WHERE p.offre.id = :oid ORDER BY p.dateCreation DESC",
                PVDeliberation.class)
                .setParameter("oid", offreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /** Archive consolidée : tous les rapports de phase de l'institution, toutes offres confondues. */
    public List<RapportPhaseEvaluation> findRapportsByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            List<RapportPhaseEvaluation> rapports = session.createQuery(
                "SELECT r FROM RapportPhaseEvaluation r " +
                "JOIN FETCH r.phaseOffre po JOIN FETCH po.offre o JOIN FETCH po.phase " +
                "JOIN FETCH o.institution " +
                "WHERE o.institution.id = :instId ORDER BY r.dateCreation DESC",
                RapportPhaseEvaluation.class)
                .setParameter("instId", institutionId).list();
            initialiserCollectionsRapport(rapports);
            return rapports;
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    /**
     * Initialise, tant que la session est encore ouverte, les collections lazy
     * (documentsRequis, rubriques) lues par RapportPvPdfService.genererRapport.
     * Deux JOIN FETCH sur des collections (bags) dans une même requête HQL
     * provoquant un MultipleBagFetchException, on les charge ici séparément.
     */
    private void initialiserCollectionsRapport(List<RapportPhaseEvaluation> rapports) {
        for (RapportPhaseEvaluation r : rapports) {
            Hibernate.initialize(r.getPhaseOffre().getOffre().getDocumentsRequis());
            if (r.getPhaseOffre().getPhase() != null) {
                Hibernate.initialize(r.getPhaseOffre().getPhase().getRubriques());
            }
        }
    }

    /** Archive consolidée : tous les PV de délibération de l'institution, toutes offres confondues. */
    public List<PVDeliberation> findPvByInstitution(Long institutionId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT p FROM PVDeliberation p JOIN FETCH p.offre o JOIN FETCH o.institution " +
                "WHERE o.institution.id = :instId ORDER BY p.dateCreation DESC",
                PVDeliberation.class)
                .setParameter("instId", institutionId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public List<CandidatFinal> findCandidatsFinauxByOffre(Long offreId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT c FROM CandidatFinal c JOIN FETCH c.candidature " +
                "LEFT JOIN FETCH c.phaseOffre po LEFT JOIN FETCH po.phase " +
                "WHERE c.offre.id = :oid ORDER BY c.rang",
                CandidatFinal.class)
                .setParameter("oid", offreId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }


    public CandidatFinal findCandidatFinalByCandidature(Long candidatureId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT c FROM CandidatFinal c " +
                "LEFT JOIN FETCH c.phaseOffre po LEFT JOIN FETCH po.phase " +
                "WHERE c.candidature.id = :cid",
                CandidatFinal.class)
                .setParameter("cid", candidatureId)
                .uniqueResultOptional().orElse(null);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

    public List<AvisSelectionCandidat> findAvisByCandidature(Long candidatureId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "SELECT a FROM AvisSelectionCandidat a JOIN FETCH a.autorite " +
                "WHERE a.candidature.id = :cid ORDER BY a.dateAvis DESC",
                AvisSelectionCandidat.class)
                .setParameter("cid", candidatureId).list();
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return new ArrayList<>(); }
    }

    public AvisSelectionCandidat findAvis(Long candidatureId, Long autoriteId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                "FROM AvisSelectionCandidat a WHERE a.candidature.id = :cid AND a.autorite.id = :aid",
                AvisSelectionCandidat.class)
                .setParameter("cid", candidatureId)
                .setParameter("aid", autoriteId)
                .uniqueResultOptional().orElse(null);
        } catch (Exception e) { LOG.error("Erreur inattendue", e); return null; }
    }

}
