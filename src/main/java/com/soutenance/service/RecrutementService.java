package com.soutenance.service;

import com.soutenance.dao.CandidatureDao;
import com.soutenance.dao.RecrutementDao;
import com.soutenance.entity.*;
import com.soutenance.notification.ModeleNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestre le concours de recrutement décrit dans le modèle gestion_rh :
 * référentiel d'évaluation (phases/rubriques/éléments) → comité d'évaluateurs
 * → lancement de phase → notation → classement → rapport → délibération →
 * décision finale de l'autorité (qui déclenche le recrutement effectif via
 * RHService.recruterCandidat).
 */
@Service
public class RecrutementService {

    @Autowired private RecrutementDao recrutementDao;
    @Autowired private CandidatureDao candidatureDao;
    @Autowired private RHService rhService;
    @Autowired private NotificationRHService notificationRHService;

    // ════════════════════════════════════════════════════════════════════
    // RÉFÉRENTIEL D'ÉVALUATION
    // ════════════════════════════════════════════════════════════════════

    public List<ReferentielEvaluation> getReferentiels(Long institutionId) {
        return recrutementDao.findReferentielsByInstitution(institutionId);
    }

    @Transactional
    public void creerReferentiel(String libelle, Institution institution, String operateur) {
        ReferentielEvaluation r = new ReferentielEvaluation();
        r.setLibelle(libelle);
        r.setInstitution(institution);
        r.setCreePar(operateur);
        recrutementDao.save(r);
    }

    public List<PhaseEvaluation> getPhases(Long referentielId) {
        return recrutementDao.findPhasesByReferentiel(referentielId);
    }

    @Transactional
    public void ajouterPhase(ReferentielEvaluation referentiel, String libelle, Integer noteEliminatoire) {
        List<PhaseEvaluation> existantes = recrutementDao.findPhasesByReferentiel(referentiel.getId());
        PhaseEvaluation p = new PhaseEvaluation();
        p.setReferentiel(referentiel);
        p.setLibelle(libelle);
        p.setNumeroOrdre(existantes.size() + 1);
        p.setNoteEliminatoire(noteEliminatoire);
        recrutementDao.save(p);
    }

    public List<Rubrique> getRubriques(Long phaseId) {
        return recrutementDao.findRubriquesByPhase(phaseId);
    }

    @Transactional
    public void ajouterRubrique(PhaseEvaluation phase, String libelle, int coefficient) {
        Rubrique r = new Rubrique();
        r.setPhase(phase);
        r.setLibelle(libelle);
        r.setCoefficient(coefficient);
        recrutementDao.save(r);
    }

    public List<ElementRubrique> getElements(Long rubriqueId) {
        return recrutementDao.findElementsByRubrique(rubriqueId);
    }

    @Transactional
    public void ajouterElement(Rubrique rubrique, String libelle, int pointsMax) {
        ElementRubrique e = new ElementRubrique();
        e.setRubrique(rubrique);
        e.setLibelle(libelle);
        e.setPointsMax(pointsMax);
        recrutementDao.save(e);
    }

    // ════════════════════════════════════════════════════════════════════
    // COMITÉ & ÉVALUATEURS
    // ════════════════════════════════════════════════════════════════════

    public List<Comite> getComitesByOffre(Long offreId) {
        return recrutementDao.findComitesByOffre(offreId);
    }

    @Transactional
    public void creerComite(OffreEmploi offre, String libelle) {
        Comite c = new Comite();
        c.setOffre(offre);
        c.setLibelle(libelle);
        recrutementDao.save(c);
    }

    public List<ComiteEvaluateur> getMembres(Long comiteId) {
        return recrutementDao.findMembresByComite(comiteId);
    }

    /** Offres/concours où cet employé siège comme membre du jury. */
    public List<ComiteEvaluateur> getMembresJuryDeEmploye(Long employeId) {
        return recrutementDao.findMembresByEvaluateur(employeId);
    }

    /** Ajoute un employé comme membre du jury ; refuse les doublons. */
    @Transactional
    public String ajouterMembre(Comite comite, Employe employe, String role) {
        if (recrutementDao.isDejaMembre(comite.getId(), employe.getId())) {
            return "Cet employé est déjà membre de ce comité.";
        }
        ComiteEvaluateur m = new ComiteEvaluateur();
        m.setComite(comite);
        m.setEvaluateur(employe);
        m.setRole(role);
        recrutementDao.save(m);
        return null;
    }

    @Transactional
    public void retirerMembre(ComiteEvaluateur membre) {
        recrutementDao.delete(membre);
    }

    // ════════════════════════════════════════════════════════════════════
    // LANCEMENT DE PHASE & NOTATION
    // ════════════════════════════════════════════════════════════════════

    public List<PhaseOffre> getPhasesOffre(Long offreId) {
        return recrutementDao.findPhasesOffreByOffre(offreId);
    }

    /** Lancement immédiat, sans planification — le statut passe directement à EN_COURS. */
    @Transactional
    public void lancerPhase(OffreEmploi offre, PhaseEvaluation phase, LocalDateTime dateLimite, String operateur) {
        PhaseOffre po = new PhaseOffre();
        po.setOffre(offre);
        po.setPhase(phase);
        po.setStatut("EN_COURS");
        po.setDateLancement(LocalDateTime.now());
        po.setDateLimite(dateLimite);
        recrutementDao.save(po);
        syncCandidaturesEntretien(offre, phase, operateur);
    }

    /**
     * Planifie une phase avec une fenêtre de dates : elle reste PLANIFIEE et
     * n'est pas encore notable — PhaseOffreScheduler la fera passer EN_COURS
     * automatiquement dès que dateDebut est atteinte (et la phase précédente
     * clôturée).
     */
    @Transactional
    public void planifierPhase(OffreEmploi offre, PhaseEvaluation phase, LocalDateTime dateDebut, LocalDateTime dateFin) {
        PhaseOffre po = new PhaseOffre();
        po.setOffre(offre);
        po.setPhase(phase);
        po.setStatut("PLANIFIEE");
        po.setDateDebutPrevue(dateDebut);
        po.setDateLimite(dateFin);
        recrutementDao.save(po);
    }

    /** Active maintenant une phase PLANIFIEE — utilisé par le scheduler et par le forçage manuel du RH. */
    @Transactional
    public void activerPhasePlanifiee(PhaseOffre po, String operateur) {
        po.setStatut("EN_COURS");
        po.setDateLancement(LocalDateTime.now());
        recrutementDao.update(po);
        syncCandidaturesEntretien(po.getOffre(), po.getPhase(), operateur);
    }

    /**
     * Une phase d'entretien lancée côté jury fait automatiquement avancer le
     * statut des candidatures encore en lice, pour que les deux pipelines
     * (suivi RH simple et évaluation par jury) restent cohérents.
     */
    private void syncCandidaturesEntretien(OffreEmploi offre, PhaseEvaluation phase, String operateur) {
        if (phase.getLibelle() == null || !phase.getLibelle().toLowerCase().contains("entretien")) return;
        for (Candidature c : candidatureDao.findByOffre(offre.getId())) {
            if ("RECUE".equals(c.getStatut()) || "EN_COURS".equals(c.getStatut())) {
                rhService.changerStatutCandidature(c.getId(), "ENTRETIEN", operateur);
            }
        }
    }

    /** Met à jour la fenêtre de planification d'une phase PLANIFIEE ou EN_COURS — le RH garde la main sur les dates. */
    @Transactional
    public void modifierDatesPhase(PhaseOffre po, LocalDateTime dateDebut, LocalDateTime dateFin) {
        po.setDateDebutPrevue(dateDebut);
        po.setDateLimite(dateFin);
        recrutementDao.update(po);
    }

    @Transactional
    public void cloturerPhase(PhaseOffre phaseOffre) {
        phaseOffre.setStatut("CLOTUREE");
        phaseOffre.setDateCloture(LocalDateTime.now());
        recrutementDao.update(phaseOffre);
        calculerResultatsPhase(phaseOffre);
    }

    /**
     * Repousse la date de fin d'une phase déjà clôturée et la rouvre
     * (EN_COURS) — permet au RH de revenir sur une clôture (manuelle ou
     * automatique) trop précoce. Le scheduler la reclôturera normalement à
     * la nouvelle échéance, en recalculant le classement avec les notes
     * ajoutées entre-temps.
     */
    @Transactional
    public void reouvrirPhase(PhaseOffre phaseOffre, LocalDateTime nouvelleDateFin) {
        phaseOffre.setStatut("EN_COURS");
        phaseOffre.setDateCloture(null);
        phaseOffre.setDateLimite(nouvelleDateFin);
        recrutementDao.update(phaseOffre);
    }

    /**
     * Enregistre (ou met à jour) la note d'un évaluateur sur un élément de
     * rubrique pour une candidature, dans le cadre d'une phase lancée.
     */
    @Transactional
    public String noterCandidat(PhaseOffre phaseOffre, Candidature candidature, ElementRubrique element,
                                 Employe evaluateur, int points, String commentaire) {
        if (!"EN_COURS".equals(phaseOffre.getStatut())) {
            return "Cette phase n'est plus en cours — elle doit être réouverte pour pouvoir noter.";
        }
        if (points < 0 || points > element.getPointsMax()) {
            return "La note doit être comprise entre 0 et " + element.getPointsMax() + ".";
        }
        NoteCandidatEvalElement note = recrutementDao.findNote(
            phaseOffre.getId(), candidature.getId(), element.getId(), evaluateur.getId());
        if (note == null) {
            note = new NoteCandidatEvalElement();
            note.setPhaseOffre(phaseOffre);
            note.setCandidature(candidature);
            note.setElement(element);
            note.setEvaluateur(evaluateur);
            note.setPoints(points);
            note.setCommentaire(commentaire);
            recrutementDao.save(note);
        } else {
            note.setPoints(points);
            note.setCommentaire(commentaire);
            recrutementDao.update(note);
        }
        return null;
    }

    /** Toutes les notes d'une phase lancée (tous évaluateurs, tous candidats). */
    public List<NoteCandidatEvalElement> getNotesPhase(Long phaseOffreId) {
        return recrutementDao.findNotesByPhaseOffre(phaseOffreId);
    }

    public List<NoteCandidatEvalElement> getNotes(Long phaseOffreId, Long candidatureId) {
        return recrutementDao.findNotesByPhaseOffreEtCandidature(phaseOffreId, candidatureId);
    }

    /** Détail des notes d'un candidat par membre du jury, toutes phases confondues — pour le PV. */
    @Transactional(readOnly = true)
    public List<NoteCandidatEvalElement> getNotesParCandidature(Long candidatureId) {
        return recrutementDao.findNotesByCandidature(candidatureId);
    }

    // ════════════════════════════════════════════════════════════════════
    // RÉSULTATS & CLASSEMENT
    // ════════════════════════════════════════════════════════════════════

    public List<ResultatPhaseCandidature> getResultats(Long phaseOffreId) {
        return recrutementDao.findResultatsByPhaseOffre(phaseOffreId);
    }

    /**
     * Calcule le score pondéré de chaque candidature ayant été notée dans
     * cette phase (moyenne des notes par élément × coefficient de sa
     * rubrique), classe les candidatures par score décroissant, et marque
     * ADMIS/ELIMINE selon la note éliminatoire de la phase le cas échéant.
     */
    @Transactional
    public void calculerResultatsPhase(PhaseOffre phaseOffre) {
        List<NoteCandidatEvalElement> notes = recrutementDao.findNotesByPhaseOffre(phaseOffre.getId());
        if (notes.isEmpty()) return;

        Map<Long, List<NoteCandidatEvalElement>> parCandidature = notes.stream()
            .collect(Collectors.groupingBy(n -> n.getCandidature().getId()));

        record Score(Candidature candidature, double points) {}
        List<Score> scores = parCandidature.values().stream().map(notesCandidat -> {
            Candidature cand = notesCandidat.get(0).getCandidature();
            Map<Long, List<NoteCandidatEvalElement>> parElement = notesCandidat.stream()
                .collect(Collectors.groupingBy(n -> n.getElement().getId()));
            double total = parElement.values().stream()
                .mapToDouble(notesElement -> {
                    ElementRubrique el = notesElement.get(0).getElement();
                    double moyenne = notesElement.stream().mapToInt(NoteCandidatEvalElement::getPoints).average().orElse(0);
                    return moyenne * el.getRubrique().getCoefficient();
                })
                .sum();
            return new Score(cand, total);
        }).sorted(Comparator.comparingDouble(Score::points).reversed()).toList();

        int rang = 1;
        for (Score s : scores) {
            ResultatPhaseCandidature r = recrutementDao.findResultat(phaseOffre.getId(), s.candidature().getId());
            if (r == null) {
                r = new ResultatPhaseCandidature();
                r.setPhaseOffre(phaseOffre);
                r.setCandidature(s.candidature());
            }
            r.setNombrePoints((int) Math.round(s.points()));
            r.setRang(rang++);
            Integer seuil = phaseOffre.getPhase().getNoteEliminatoire();
            r.setStatut(seuil == null || s.points() >= seuil ? "ADMIS" : "ELIMINE");
            if (r.getId() == null) recrutementDao.save(r); else recrutementDao.update(r);
        }
    }

    /**
     * Total pondéré provisoire d'un candidat sur une phase, calculé avec la même
     * formule que calculerResultatsPhase (moyenne par élément × coefficient de sa
     * rubrique) mais à partir des notes déjà saisies, avant même la clôture —
     * permet d'avertir en temps réel pendant la notation si le candidat est
     * sous le seuil éliminatoire de la phase.
     */
    public double getPointsProvisoires(List<NoteCandidatEvalElement> notes) {
        if (notes.isEmpty()) return 0;
        Map<Long, List<NoteCandidatEvalElement>> parElement = notes.stream()
            .collect(Collectors.groupingBy(n -> n.getElement().getId()));
        return parElement.values().stream()
            .mapToDouble(notesElement -> {
                ElementRubrique el = notesElement.get(0).getElement();
                double moyenne = notesElement.stream().mapToInt(NoteCandidatEvalElement::getPoints).average().orElse(0);
                return moyenne * el.getRubrique().getCoefficient();
            })
            .sum();
    }

    // ════════════════════════════════════════════════════════════════════
    // RAPPORT & DÉLIBÉRATION
    // ════════════════════════════════════════════════════════════════════

    public List<RapportPhaseEvaluation> getRapports(Long offreId) {
        return recrutementDao.findRapportsByOffre(offreId);
    }

    @Transactional
    /** Le rapport doit déjà porter sa phaseOffre et ses champs saisis par le RH ; seul le nombre de candidats retenus est calculé ici. */
    public void genererRapport(RapportPhaseEvaluation rapport) {
        List<ResultatPhaseCandidature> resultats = recrutementDao.findResultatsByPhaseOffre(rapport.getPhaseOffre().getId());
        rapport.setCandidatsRetenus((int) resultats.stream().filter(x -> "ADMIS".equals(x.getStatut())).count());
        recrutementDao.save(rapport);
    }

    public List<PVDeliberation> getPvs(Long offreId) {
        return recrutementDao.findPvByOffre(offreId);
    }

    /** Archive consolidée (page dédiée) : tous les rapports de phase de l'institution. */
    @Transactional(readOnly = true)
    public List<RapportPhaseEvaluation> getRapportsInstitution(Long institutionId) {
        return recrutementDao.findRapportsByInstitution(institutionId);
    }

    /** Archive consolidée (page dédiée) : tous les PV de délibération de l'institution. */
    @Transactional(readOnly = true)
    public List<PVDeliberation> getPvsInstitution(Long institutionId) {
        return recrutementDao.findPvByInstitution(institutionId);
    }

    @Transactional
    public void creerPv(PVDeliberation pv) {
        recrutementDao.save(pv);
    }

    /** Notifie chaque membre du jury (ayant un compte utilisateur) qu'un rapport est disponible. */
    public void notifierJuryRapport(RapportPhaseEvaluation rapport) {
        if (rapport == null || rapport.getPhaseOffre() == null || rapport.getPhaseOffre().getOffre() == null) return;
        OffreEmploi offre = rapport.getPhaseOffre().getOffre();
        for (ComiteEvaluateur m : recrutementDao.findMembresJuryAvecUserByOffre(offre.getId())) {
            if (m.getEvaluateur() == null || m.getEvaluateur().getUser() == null) continue;
            notificationRHService.notifierUtilisateur(m.getEvaluateur().getUser(),
                ModeleNotification.RAPPORT_JURY_DISPONIBLE, "RAPPORT_PHASE", rapport.getId(),
                rapport.getLibelle(), offre.getTitre());
        }
    }

    /** Notifie chaque membre du jury (ayant un compte utilisateur) qu'un PV est disponible. */
    public void notifierJuryPv(PVDeliberation pv) {
        if (pv == null || pv.getOffre() == null) return;
        for (ComiteEvaluateur m : recrutementDao.findMembresJuryAvecUserByOffre(pv.getOffre().getId())) {
            if (m.getEvaluateur() == null || m.getEvaluateur().getUser() == null) continue;
            notificationRHService.notifierUtilisateur(m.getEvaluateur().getUser(),
                ModeleNotification.PV_JURY_DISPONIBLE, "PV_DELIBERATION", pv.getId(),
                pv.getLibelle(), pv.getOffre().getTitre());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CLASSEMENT FINAL & DÉCISION DE L'AUTORITÉ
    // ════════════════════════════════════════════════════════════════════

    public List<CandidatFinal> getCandidatsFinaux(Long offreId) {
        return recrutementDao.findCandidatsFinauxByOffre(offreId);
    }

    /**
     * Construit (ou reconstruit) le classement final d'un appel d'offre :
     * chaque candidature ADMISE à la dernière phase clôturée devient un
     * CandidatFinal en attente de décision, classé par son total CUMULÉ
     * (toutes phases confondues) — pas seulement le score de la dernière
     * phase — pour rester cohérent avec les points cumulés affichés partout
     * ailleurs (relevé cumulé, décision finale) et avec la logique de
     * sélection automatique des meilleurs selon le quota de postes.
     */
    @Transactional
    public void construireClassementFinal(OffreEmploi offre) {
        List<PhaseOffre> phases = recrutementDao.findPhasesOffreByOffre(offre.getId());
        PhaseOffre derniere = phases.stream()
            .filter(p -> "CLOTUREE".equals(p.getStatut()))
            .max(Comparator.comparing(p -> p.getPhase().getNumeroOrdre()))
            .orElse(null);
        if (derniere == null) return;

        record Admis(Candidature candidature, int cumule) {}
        List<Admis> admis = recrutementDao.findResultatsByPhaseOffre(derniere.getId()).stream()
            .filter(res -> "ADMIS".equals(res.getStatut()))
            .map(res -> new Admis(res.getCandidature(), getPointsCandidature(res.getCandidature().getId())))
            .sorted(Comparator.comparingInt(Admis::cumule).reversed())
            .toList();

        int rang = 1;
        for (Admis a : admis) {
            CandidatFinal cf = recrutementDao.findCandidatFinalByCandidature(a.candidature().getId());
            if (cf == null) {
                cf = new CandidatFinal();
                cf.setCandidature(a.candidature());
                cf.setOffre(offre);
            }
            cf.setRang(rang++);
            cf.setNombrePoints(a.cumule());
            cf.setPhaseOffre(derniere);
            if (cf.getId() == null) recrutementDao.save(cf); else recrutementDao.update(cf);
        }
    }

    /**
     * Avis du président du jury sur un candidat finaliste — saisi par lui-même
     * depuis son espace libre-service (JuryBean), pas par le RH en son nom.
     * Un second avis de la même autorité met à jour le précédent plutôt que
     * d'en créer un doublon.
     */
    @Transactional
    public void donnerAvis(Candidature candidature, Employe autorite, String avis, String commentaire) {
        AvisSelectionCandidat a = recrutementDao.findAvis(candidature.getId(), autorite.getId());
        if (a == null) {
            a = new AvisSelectionCandidat();
            a.setCandidature(candidature);
            a.setAutorite(autorite);
        }
        a.setAvis(avis);
        a.setCommentaire(commentaire);
        if (a.getId() == null) recrutementDao.save(a); else recrutementDao.update(a);
    }

    public List<AvisSelectionCandidat> getAvis(Long candidatureId) {
        return recrutementDao.findAvisByCandidature(candidatureId);
    }

    public AvisSelectionCandidat getAvisDe(Long candidatureId, Long autoriteId) {
        return recrutementDao.findAvis(candidatureId, autoriteId);
    }

    /**
     * Score maximum atteignable sur la dernière phase clôturée d'une offre —
     * utilisé pour convertir le seuil de recrutement automatique (exprimé en %)
     * en points comparables au score réel des candidats.
     */
    /**
     * Score maximum cumulé sur TOUTES les phases clôturées de l'offre — utilisé pour le seuil de
     * recrutement automatique, afin qu'il porte sur le même total que le classement final
     * (cf. getPointsCandidature/getPointsMaxCandidature), et non sur la seule dernière phase.
     */
    public Integer getPointsMaxToutesPhases(Long offreId) {
        List<PhaseOffre> phases = recrutementDao.findPhasesOffreByOffre(offreId);
        List<PhaseOffre> cloturees = phases.stream().filter(p -> "CLOTUREE".equals(p.getStatut())).toList();
        if (cloturees.isEmpty()) return null;
        return cloturees.stream().mapToInt(this::calculerPointsMax).sum();
    }

    /** Score maximum atteignable sur une phase précise — à utiliser dès qu'on connaît la phase exacte du candidat. */
    public int getPointsMaxPhase(PhaseOffre phaseOffre) {
        return calculerPointsMax(phaseOffre);
    }

    private int calculerPointsMax(PhaseOffre phaseOffre) {
        int max = 0;
        for (Rubrique r : recrutementDao.findRubriquesByPhase(phaseOffre.getPhase().getId())) {
            int sommeElements = recrutementDao.findElementsByRubrique(r.getId()).stream()
                .mapToInt(ElementRubrique::getPointsMax).sum();
            max += sommeElements * r.getCoefficient();
        }
        return max;
    }

    /** Total des points obtenus par ce candidat, toutes phases clôturées confondues (pas une seule phase). */
    public Integer getPointsCandidature(Long candidatureId) {
        List<ResultatPhaseCandidature> resultats = recrutementDao.findResultatsByCandidature(candidatureId);
        if (resultats.isEmpty()) return null;
        return resultats.stream().mapToInt(ResultatPhaseCandidature::getNombrePoints).sum();
    }

    /** Somme des scores maximums de chaque phase où ce candidat a été noté — dénominateur du total cumulé. */
    public Integer getPointsMaxCandidature(Long candidatureId) {
        List<ResultatPhaseCandidature> resultats = recrutementDao.findResultatsByCandidature(candidatureId);
        if (resultats.isEmpty()) return null;
        return resultats.stream().mapToInt(r -> calculerPointsMax(r.getPhaseOffre())).sum();
    }

    /** ELIMINE si le candidat a été éliminé sur au moins une phase clôturée, sinon ADMIS (null si aucun résultat). */
    public String getStatutCumule(Long candidatureId) {
        List<ResultatPhaseCandidature> resultats = recrutementDao.findResultatsByCandidature(candidatureId);
        if (resultats.isEmpty()) return null;
        return resultats.stream().anyMatch(r -> "ELIMINE".equals(r.getStatut())) ? "ELIMINE" : "ADMIS";
    }

    /** Points, points max et statut cumulés d'un candidat — équivalent à getPointsCandidature +
     * getPointsMaxCandidature + getStatutCumule réunis, en une seule requête des résultats au lieu
     * de trois. À utiliser dès qu'on a besoin de plus d'une des trois valeurs pour un même candidat. */
    public record ResumeCumule(Integer points, Integer pointsMax, String statut) {}

    public ResumeCumule getResumeCumule(Long candidatureId) {
        List<ResultatPhaseCandidature> resultats = recrutementDao.findResultatsByCandidature(candidatureId);
        if (resultats.isEmpty()) return new ResumeCumule(null, null, null);
        int points = resultats.stream().mapToInt(ResultatPhaseCandidature::getNombrePoints).sum();
        int max = resultats.stream().mapToInt(r -> calculerPointsMax(r.getPhaseOffre())).sum();
        String statut = resultats.stream().anyMatch(r -> "ELIMINE".equals(r.getStatut())) ? "ELIMINE" : "ADMIS";
        return new ResumeCumule(points, max, statut);
    }

    /**
     * Même chose que getResumeCumule() mais pour plusieurs candidats d'un coup — le score
     * maximum de chaque phase clôturée (rubriques/éléments) n'est calculé qu'une seule fois
     * et réutilisé pour tous les candidats qui partagent cette phase, au lieu d'être
     * recalculé à chaque candidat.
     */
    public Map<Long, ResumeCumule> getResumesCumulesParCandidature(List<Long> candidatureIds) {
        Map<Long, Integer> maxParPhase = new HashMap<>();
        Map<Long, ResumeCumule> out = new HashMap<>();
        for (Long cid : candidatureIds) {
            List<ResultatPhaseCandidature> resultats = recrutementDao.findResultatsByCandidature(cid);
            if (resultats.isEmpty()) { out.put(cid, new ResumeCumule(null, null, null)); continue; }
            int points = resultats.stream().mapToInt(ResultatPhaseCandidature::getNombrePoints).sum();
            int max = 0;
            for (ResultatPhaseCandidature r : resultats) {
                max += maxParPhase.computeIfAbsent(r.getPhaseOffre().getId(), k -> calculerPointsMax(r.getPhaseOffre()));
            }
            String statut = resultats.stream().anyMatch(r -> "ELIMINE".equals(r.getStatut())) ? "ELIMINE" : "ADMIS";
            out.put(cid, new ResumeCumule(points, max, statut));
        }
        return out;
    }

    /**
     * Décision finale de l'autorité sur un candidat. Le passage à "RETENU"
     * déclenche le recrutement effectif (création de l'employé) via
     * RHService — les autres décisions (NON_RETENU) se contentent de
     * clôturer le dossier.
     */
    @Transactional
    public String decider(CandidatFinal candidatFinal, String statut, Employe autorite,
                           String notesAutorite, String operateur) {
        candidatFinal.setStatut(statut);
        candidatFinal.setAutorite(autorite);
        candidatFinal.setNotesAutorite(notesAutorite);
        candidatFinal.setDateDecision(LocalDateTime.now());
        recrutementDao.update(candidatFinal);

        if ("RETENU".equals(statut)) {
            Employe employe = rhService.recruterCandidat(candidatFinal.getCandidature().getId(), operateur);
            if (employe == null) {
                return "La candidature n'a pas pu être convertie en employé (déjà recrutée ?).";
            }
        } else if ("NON_RETENU".equals(statut)) {
            Candidature c = candidatureDao.findById(candidatFinal.getCandidature().getId());
            if (c != null) {
                c.setStatut("REFUSEE");
                candidatureDao.update(c);
            }
        }
        return null;
    }
}
