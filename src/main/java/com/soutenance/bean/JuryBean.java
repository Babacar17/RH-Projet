package com.soutenance.bean;

import com.soutenance.entity.*;
import com.soutenance.service.RHService;
import com.soutenance.service.RapportPvPdfService;
import com.soutenance.service.RecrutementService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Espace libre-service d'un membre de jury connecté (Employé ou Manager) :
 * consulte une offre où il siège et note lui-même les candidats sur la
 * phase actuellement lancée — jusqu'ici seul le RH pouvait saisir des notes,
 * au nom de n'importe quel évaluateur, depuis ResponsableRH/recrutement.xhtml.
 * Réutilisé tel quel par les deux portails (Employes/ et
 * ResponsableHierarchique/) via un fragment de vue partagé.
 */
@Component("juryBean")
@Scope("view")
public class JuryBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private RecrutementService recrutementService;
    @Autowired private RHService rhService;
    @Autowired private SessionBean sessionBean;
    @Autowired private com.soutenance.dao.EmployeDao employeDao;
    @Autowired(required = false) private RapportPvPdfService rapportPvPdfService;

    private Long offreId;

    private Employe moi;
    private OffreEmploi offre;
    private boolean autorise = false;
    private String monRole;

    private PhaseOffre phaseActuelle;
    private List<PhaseOffre> phasesOffre = new ArrayList<>();
    private List<Rubrique> rubriques = new ArrayList<>();
    private Map<Long, List<ElementRubrique>> elementsParRubrique = new LinkedHashMap<>();
    private List<Candidature> candidatures = new ArrayList<>();

    /** Mes notes deja saisies, cle "candidatureId_elementId" -> points (0..pointsMax). */
    private Map<String, Object> mesNotes = new HashMap<>();
    /** Commentaires associes, meme cle. */
    private Map<String, String> mesCommentaires = new HashMap<>();

    /** Classement final (candidats finalistes) — uniquement rempli pour le président du jury. */
    private List<CandidatFinal> candidatsFinaux = new ArrayList<>();
    /** Mon avis en cours de saisie, cle candidatureId -> FAVORABLE/DEFAVORABLE/RESERVE. */
    private Map<Long, String> monAvisSaisi = new HashMap<>();
    /** Commentaire associe, meme cle. */
    private Map<Long, String> monAvisCommentaireSaisi = new HashMap<>();

    /** Rapports et PV deja generes pour cette offre, consultables en lecture seule par le jury. */
    private List<RapportPhaseEvaluation> rapports = new ArrayList<>();
    private List<PVDeliberation> pvs = new ArrayList<>();

    public Long getOffreId() { return offreId; }
    public void setOffreId(Long offreId) { this.offreId = offreId; }

    /** Point d'entree de la page (f:viewAction) — ne recharge pas lors des postbacks ajax de notation. */
    public void charger() {
        if (FacesContext.getCurrentInstance().isPostback()) return;
        autorise = false;
        if (offreId == null) return;

        String username = sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : null;
        moi = username != null ? employeDao.findByUsername(username) : null;
        if (moi == null) return;

        offre = rhService.getOffreById(offreId);
        if (offre == null) return;

        ComiteEvaluateur monMembre = recrutementService.getMembresJuryDeEmploye(moi.getId()).stream()
            .filter(m -> m.getComite() != null && m.getComite().getOffre() != null
                      && offreId.equals(m.getComite().getOffre().getId()))
            .findFirst().orElse(null);
        if (monMembre == null) return; // pas jure de cette offre : acces refuse

        autorise = true;
        monRole = monMembre.getRole();

        phasesOffre = recrutementService.getPhasesOffre(offreId);
        phaseActuelle = phasesOffre.stream()
            .filter(p -> "EN_COURS".equals(p.getStatut()))
            .findFirst()
            // Sinon la dernière déjà clôturée pour consultation — jamais une phase encore
            // PLANIFIEE, qui n'a rien à noter tant que le scheduler ne l'a pas activée.
            .orElseGet(() -> phasesOffre.stream()
                .filter(p -> "CLOTUREE".equals(p.getStatut()))
                .reduce((first, second) -> second)
                .orElse(null));

        rubriques = new ArrayList<>();
        elementsParRubrique = new LinkedHashMap<>();
        if (phaseActuelle != null) {
            rubriques = recrutementService.getRubriques(phaseActuelle.getPhase().getId());
            for (Rubrique r : rubriques) {
                elementsParRubrique.put(r.getId(), recrutementService.getElements(r.getId()));
            }
        }

        candidatures = rhService.getCandidaturesByOffre(offreId);

        mesNotes = new HashMap<>();
        mesCommentaires = new HashMap<>();
        if (phaseActuelle != null) {
            for (Candidature c : candidatures) {
                for (NoteCandidatEvalElement n : recrutementService.getNotes(phaseActuelle.getId(), c.getId())) {
                    if (n.getEvaluateur() != null && n.getEvaluateur().getId().equals(moi.getId())) {
                        String cle = c.getId() + "_" + n.getElement().getId();
                        mesNotes.put(cle, n.getPoints());
                        mesCommentaires.put(cle, n.getCommentaire());
                    }
                }
            }
        }

        rapports = recrutementService.getRapports(offreId);
        pvs = recrutementService.getPvs(offreId);

        // Avis sur les finalistes : réservé au président du jury, l'autorité de décision de l'offre.
        candidatsFinaux = new ArrayList<>();
        monAvisSaisi = new HashMap<>();
        monAvisCommentaireSaisi = new HashMap<>();
        if ("PRESIDENT".equals(monRole)) {
            candidatsFinaux = recrutementService.getCandidatsFinaux(offreId);
            for (CandidatFinal cf : candidatsFinaux) {
                AvisSelectionCandidat mien = recrutementService.getAvisDe(cf.getCandidature().getId(), moi.getId());
                if (mien != null) {
                    monAvisSaisi.put(cf.getCandidature().getId(), mien.getAvis());
                    monAvisCommentaireSaisi.put(cf.getCandidature().getId(), mien.getCommentaire());
                }
            }
        }
    }

    /** Enregistre (ou met à jour) mon avis de président sur ce candidat finaliste. */
    public void donnerMonAvis(CandidatFinal cf) {
        if (moi == null || cf == null || !"PRESIDENT".equals(monRole)) return;
        String avis = monAvisSaisi.get(cf.getCandidature().getId());
        if (avis == null || avis.isBlank()) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, "Attention", "Choisissez un avis avant d'enregistrer."));
            return;
        }
        recrutementService.donnerAvis(cf.getCandidature(), moi, avis, monAvisCommentaireSaisi.get(cf.getCandidature().getId()));
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès",
                "Avis enregistré pour " + cf.getCandidature().getNomPrenomCandidat() + "."));
    }

    /** Enregistre en base toutes les notes actuellement saisies (dans mesNotes) pour ce candidat, sur la phase en cours. */
    private int enregistrerNotesSaisies(Candidature candidature) {
        int nb = 0;
        for (Rubrique r : rubriques) {
            for (ElementRubrique el : elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>())) {
                String cle = candidature.getId() + "_" + el.getId();
                Object saisie = mesNotes.get(cle);
                if (saisie == null || String.valueOf(saisie).isBlank()) continue;
                int points = new java.math.BigDecimal(String.valueOf(saisie).trim()).intValue();
                String erreur = recrutementService.noterCandidat(
                    phaseActuelle, candidature, el, moi, points, mesCommentaires.get(cle));
                if (erreur != null) {
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", erreur));
                } else {
                    nb++;
                }
            }
        }
        return nb;
    }

    /**
     * Enregistre les notes saisies à l'instant du clic (comme noterCandidat), puis confirme si la
     * phase est bien complète pour ce candidat ou prévient de ce qu'il reste à noter — le bouton
     * "Valider la phase" reste toujours cliquable, sa valeur n'étant connue qu'au moment du clic.
     */
    public void validerPhase(Candidature candidature) {
        if (phaseActuelle == null || candidature == null) return;
        enregistrerNotesSaisies(candidature);
        pointsProvisoiresCache.remove(candidature.getId());
        if (isCandidatComplet(candidature)) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Phase validée",
                    "Toutes les notes sont enregistrées pour " + candidature.getNomPrenomCandidat() + "."));
        } else {
            int restant = getNbCriteresTotalPhase() - getNbNotesSaisiesCandidat(candidature);
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, "Notation incomplète",
                    "Il reste " + restant + " critère(s) à noter pour " + candidature.getNomPrenomCandidat() + "."));
        }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Employe getMoi() { return moi; }
    public OffreEmploi getOffre() { return offre; }
    public boolean isAutorise() { return autorise; }
    public String getMonRole() { return monRole; }
    public PhaseOffre getPhaseActuelle() { return phaseActuelle; }
    public List<Rubrique> getRubriques() { return rubriques; }
    public Map<Long, List<ElementRubrique>> getElementsParRubrique() { return elementsParRubrique; }
    public List<Candidature> getCandidatures() { return candidatures; }
    public Map<String, Object> getMesNotes() { return mesNotes; }
    public Map<String, String> getMesCommentaires() { return mesCommentaires; }
    public List<CandidatFinal> getCandidatsFinaux() { return candidatsFinaux; }
    public Map<Long, String> getMonAvisSaisi() { return monAvisSaisi; }
    public Map<Long, String> getMonAvisCommentaireSaisi() { return monAvisCommentaireSaisi; }
    public List<RapportPhaseEvaluation> getRapports() { return rapports; }
    public List<PVDeliberation> getPvs() { return pvs; }

    /** Ouvre le rapport dans un nouvel onglet (lecture seule, le jury ne peut ni modifier ni générer). */
    public void ouvrirRapport(Long rapportId) {
        RapportPhaseEvaluation r = rapports.stream().filter(x -> x.getId().equals(rapportId)).findFirst().orElse(null);
        if (r == null || rapportPvPdfService == null) return;
        ecrirePdf(rapportPvPdfService.genererRapport(r), "rapport-" + rapportId + ".pdf");
    }

    public void ouvrirPv(Long pvId) {
        PVDeliberation pv = pvs.stream().filter(x -> x.getId().equals(pvId)).findFirst().orElse(null);
        if (pv == null || rapportPvPdfService == null) return;
        List<CandidatFinal> classement = recrutementService.getCandidatsFinaux(offreId);
        ecrirePdf(rapportPvPdfService.genererPv(pv, classement), "pv-deliberation-" + pvId + ".pdf");
    }

    private void ecrirePdf(byte[] pdf, String nomFichier) {
        if (pdf == null || pdf.length == 0) return;
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition", "inline; filename=\"" + nomFichier + "\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception ignored) { }
    }

    /**
     * Pieces obligatoires de l'offre que ce candidat n'a pas fournies (CV inclus, toujours obligatoire).
     * Une piece "Lettre de motivation" est aussi satisfaite par le texte saisi dans le dossier ;
     * les autres sont rapprochees par libelle des documents soumis.
     */
    public List<String> getManquants(Candidature c) {
        List<String> manquants = new ArrayList<>();
        if (c == null) return manquants;
        if (c.getCvUrl() == null || c.getCvUrl().isBlank()) manquants.add("CV");
        if (offre == null || offre.getDocumentsRequis() == null) return manquants;
        for (DocumentRequisOffre req : offre.getDocumentsRequis()) {
            if (!req.isObligatoire()) continue;
            String libelle = req.getLibelle() == null ? "" : req.getLibelle().trim();
            boolean fourni = c.getDocumentsSoumis() != null && c.getDocumentsSoumis().stream()
                .anyMatch(d -> d.getLibelle() != null && d.getLibelle().trim().equalsIgnoreCase(libelle));
            boolean lettreTexte = libelle.toLowerCase().contains("lettre")
                && c.getLettreMotivation() != null && !c.getLettreMotivation().isBlank();
            if (!fourni && !lettreTexte) manquants.add(libelle);
        }
        return manquants;
    }

    /** Cle composite utilisee dans mesNotes / mesCommentaires — expose pour le binding EL de la vue. */
    public String cle(Long candidatureId, Long elementId) {
        return candidatureId + "_" + elementId;
    }

    /** Seuil eliminatoire de la phase en cours, le cas echeant. */
    public Integer getSeuilEliminatoire() {
        return phaseActuelle == null ? null : phaseActuelle.getPhase().getNoteEliminatoire();
    }

    /** Mémoïsation — appelé plusieurs fois par rendu pour un même candidat (badge, en-tête, seuil éliminatoire). */
    private final Map<Long, Double> pointsProvisoiresCache = new HashMap<>();

    /** Total pondere provisoire d'un candidat sur la phase en cours, a partir des notes deja saisies par tout le jury. */
    public double getPointsProvisoires(Candidature candidature) {
        if (phaseActuelle == null || candidature == null) return 0;
        return pointsProvisoiresCache.computeIfAbsent(candidature.getId(), id ->
            recrutementService.getPointsProvisoires(recrutementService.getNotes(phaseActuelle.getId(), id)));
    }

    /** Score maximum atteignable sur la phase en cours (ponderation des coefficients incluse). */
    public Integer getPointsMaxPhaseActuelle() {
        return phaseActuelle == null ? null : recrutementService.getPointsMaxPhase(phaseActuelle);
    }

    // ── Parcours de l'offre (bandeau + etapes) ───────────────────────────────

    /** Une etape du parcours affichee dans le bandeau d'en-tete : une phase du referentiel et son statut de lancement. */
    public static class EtapeJury implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String libelle;
        private final String statut;
        EtapeJury(String libelle, String statut) { this.libelle = libelle; this.statut = statut; }
        public String getLibelle() { return libelle; }
        public String getStatut() { return statut; }
    }

    /** Toutes les phases du referentiel de l'offre, dans l'ordre, avec leur statut de lancement (ou NON_LANCEE). */
    public List<EtapeJury> getEtapes() {
        List<EtapeJury> result = new ArrayList<>();
        if (offre == null || offre.getReferentiel() == null) return result;
        List<PhaseEvaluation> toutes = recrutementService.getPhases(offre.getReferentiel().getId()).stream()
            .sorted(Comparator.comparing(PhaseEvaluation::getNumeroOrdre))
            .collect(Collectors.toList());
        for (PhaseEvaluation p : toutes) {
            PhaseOffre po = phasesOffre.stream().filter(x -> x.getPhase().getId().equals(p.getId())).findFirst().orElse(null);
            result.add(new EtapeJury(p.getLibelle(), po != null ? po.getStatut() : "NON_LANCEE"));
        }
        return result;
    }

    // ── Avancement de ma notation (phase en cours) ───────────────────────────

    /** Nombre total de criteres (elements notes) a saisir sur la phase en cours, pour un candidat. */
    public int getNbCriteresTotalPhase() {
        int total = 0;
        for (Rubrique r : rubriques) total += elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>()).size();
        return total;
    }

    /** Nombre de criteres deja notes par moi pour ce candidat, sur la phase en cours. */
    public int getNbNotesSaisiesCandidat(Candidature c) {
        if (c == null) return 0;
        int nb = 0;
        for (Rubrique r : rubriques) {
            for (ElementRubrique el : elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>())) {
                Object v = mesNotes.get(cle(c.getId(), el.getId()));
                if (v != null && !String.valueOf(v).isBlank()) nb++;
            }
        }
        return nb;
    }

    public boolean isCandidatComplet(Candidature c) {
        int total = getNbCriteresTotalPhase();
        return total > 0 && getNbNotesSaisiesCandidat(c) == total;
    }

    public int getNbCandidaturesCompletes() {
        int nb = 0;
        for (Candidature c : candidatures) if (isCandidatComplet(c)) nb++;
        return nb;
    }

    public int getNbCandidaturesACompleter() {
        return candidatures.size() - getNbCandidaturesCompletes();
    }

    public int getNbCriteresNotesTotal() {
        int nb = 0;
        for (Candidature c : candidatures) nb += getNbNotesSaisiesCandidat(c);
        return nb;
    }

    public int getNbCriteresATotal() {
        return getNbCriteresTotalPhase() * candidatures.size();
    }

    // ── Avancement par rubrique (pour un candidat) ───────────────────────────

    public int getNbNotesSaisiesRubrique(Candidature c, Rubrique r) {
        if (c == null || r == null) return 0;
        int nb = 0;
        for (ElementRubrique el : elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>())) {
            Object v = mesNotes.get(cle(c.getId(), el.getId()));
            if (v != null && !String.valueOf(v).isBlank()) nb++;
        }
        return nb;
    }

    public int getNbElementsRubrique(Rubrique r) {
        return r == null ? 0 : elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>()).size();
    }

    /** Somme brute (non ponderee par le coefficient) des points deja saisis par moi pour ce candidat sur cette rubrique. */
    /** Pondérée par le coefficient de la rubrique, pour rester sur la même échelle que la note provisoire globale. */
    public int getSommePointsRubrique(Candidature c, Rubrique r) {
        if (c == null || r == null) return 0;
        int total = 0;
        for (ElementRubrique el : elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>())) {
            Object v = mesNotes.get(cle(c.getId(), el.getId()));
            if (v != null && !String.valueOf(v).isBlank()) {
                total += new java.math.BigDecimal(String.valueOf(v).trim()).intValue();
            }
        }
        return total * r.getCoefficient();
    }

    public int getSommeMaxRubrique(Rubrique r) {
        if (r == null) return 0;
        int total = 0;
        for (ElementRubrique el : elementsParRubrique.getOrDefault(r.getId(), new ArrayList<>())) total += el.getPointsMax();
        return total * r.getCoefficient();
    }

    // ── Dossier candidat & presentation ──────────────────────────────────────

    public int getNbPiecesFournies(Candidature c) {
        if (c == null) return 0;
        int nb = 0;
        if (c.getCvUrl() != null && !c.getCvUrl().isBlank()) nb++;
        if (c.getDocumentsSoumis() != null) nb += c.getDocumentsSoumis().size();
        return nb;
    }

    private static final String[] AVATAR_COLORS = {"#6366f1", "#ec4899", "#10b981", "#f59e0b", "#06b6d4", "#8b5cf6", "#ef4444", "#14b8a6"};

    public String getInitiales(Candidature c) {
        if (c == null) return "";
        String p = c.getPrenomCandidat() != null && !c.getPrenomCandidat().isBlank() ? c.getPrenomCandidat().substring(0, 1) : "";
        String n = c.getNomCandidat() != null && !c.getNomCandidat().isBlank() ? c.getNomCandidat().substring(0, 1) : "";
        return (p + n).toUpperCase();
    }

    public String getAvatarColor(Candidature c) {
        if (c == null || c.getId() == null) return AVATAR_COLORS[0];
        int idx = (int) (Math.abs(c.getId()) % AVATAR_COLORS.length);
        return AVATAR_COLORS[idx];
    }
}
