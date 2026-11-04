package com.soutenance.bean;

import com.soutenance.dao.CandidatureDao;
import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.OffreEmploiDao;
import com.soutenance.entity.*;
import com.soutenance.service.RHService;
import com.soutenance.service.RapportPvPdfService;
import com.soutenance.service.RecrutementService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean JSF pilotant le concours de recrutement (référentiel d'évaluation,
 * jury, notation, délibération et décision finale) — périmètre Responsable
 * RH, scopé à l'institution du RH connecté.
 */
@Component("recrutementBean")
@Scope("view")
public class RecrutementBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private RecrutementService recrutementService;
    @Autowired private RHService          rhService;
    @Autowired private OffreEmploiDao     offreEmploiDao;
    @Autowired private CandidatureDao     candidatureDao;
    @Autowired private EmployeDao         employeDao;
    @Autowired private SessionBean        sessionBean;
    @Autowired(required = false) private RapportPvPdfService rapportPvPdfService;

    private List<OffreEmploi> offres = new ArrayList<>();
    private List<Employe>     employes = new ArrayList<>();

    /** Onglet actif du concours — pilotable depuis un lien (ex: sidebar "Notes" -> ?tab=2). */
    private int activeTabIndex = 0;
    public int getActiveTabIndex() { return activeTabIndex; }
    public void setActiveTabIndex(int v) { this.activeTabIndex = v; }

    // ── Référentiels ────────────────────────────────────────────────────
    private List<ReferentielEvaluation> referentiels = new ArrayList<>();
    private ReferentielEvaluation referentielSelectionne;
    private List<PhaseEvaluation> phases = new ArrayList<>();
    private PhaseEvaluation phaseSelectionnee;
    private List<Rubrique> rubriques = new ArrayList<>();
    private Rubrique rubriqueSelectionnee;
    private List<ElementRubrique> elements = new ArrayList<>();

    private String nouveauReferentielLibelle;
    private String nouvellePhaseLibelle;
    private Integer nouvellePhaseNoteEliminatoire;
    private String nouvelleRubriqueLibelle;
    private int nouvelleRubriqueCoefficient = 1;
    private String nouvelElementLibelle;
    private int nouvelElementPointsMax = 20;

    // ── Offre sélectionnée (jury / phases / notation / décision) ─────────
    private OffreEmploi offreSelectionnee;
    private Long referentielAAssocierId;

    // ── Jury ──────────────────────────────────────────────────────────
    private List<Comite> comites = new ArrayList<>();
    private Comite comiteSelectionne;
    private List<ComiteEvaluateur> membres = new ArrayList<>();
    private String nouveauComiteLibelle;
    private Long employeAAjouterId;
    private String roleMembre = "MEMBRE";

    // ── Phases lancées & notation ─────────────────────────────────────
    private List<PhaseOffre> phasesOffre = new ArrayList<>();
    private PhaseOffre phaseOffreSelectionnee;
    private List<Candidature> candidatures = new ArrayList<>();
    /** Éléments notés de la phase sélectionnée — alimente l'avancement par membre du jury (getSuiviJury). */
    private List<ElementRubrique> elementsNotation = new ArrayList<>();

    // ── Rapport & délibération ──────────────────────────────────────
    private List<RapportPhaseEvaluation> rapports = new ArrayList<>();
    private String nouveauRapportLibelle;
    private String nouveauRapportNotes;
    private String nouveauRapportMotif;
    private Integer nouveauRapportCandidaturesRecevables;
    private Integer nouveauRapportCandidaturesIrrecevables;
    private String nouveauRapportMotifsExclusion;
    private String nouveauRapportCompositionComite;
    private String nouveauRapportDifficultes;
    private String nouveauRapportRecommandation;
    private boolean dlgRapportOuvert = false;

    private List<PVDeliberation> pvs = new ArrayList<>();
    private String nouveauPvLibelle;
    private int nouveauPvNombreMembres;
    private String nouveauPvLieu;
    private String nouveauPvHeureDebut;
    private String nouveauPvHeureCloture;
    private String nouveauPvMembresJury;
    private String nouveauPvDeclarationConflitInteret;
    private String nouveauPvModeDecision;
    private String nouveauPvResultatVote;
    private String nouveauPvReserves;
    private String nouveauPvSuitesADonner;
    private boolean dlgPvOuvert = false;

    // ── Classement final & décision ──────────────────────────────────
    private List<CandidatFinal> candidatsFinaux = new ArrayList<>();
    private CandidatFinal candidatFinalSelectionne;
    private String notesDecision;

    @PostConstruct
    public void init() {
        Long instId = sessionBean.getInstitutionId();
        offres = offreEmploiDao.findByInstitution(instId);
        employes = rhService.getEmployesByInstitution(instId);
        referentiels = recrutementService.getReferentiels(instId);
    }

    private String getOperateur() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    /** Fiche employé du RH connecté — attribuée automatiquement comme autorité des décisions finales qu'il prend. */
    private Employe getEmployeConnecte() {
        return sessionBean.getUtilisateurConnecte() != null
            ? employeDao.findByUserId(sessionBean.getUtilisateurConnecte().getId()) : null;
    }

    private void addMsg(FacesMessage.Severity sev, String s, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, s, d));
    }

    // ════════════════════════════════════════════════════════════════════
    // RÉFÉRENTIELS
    // ════════════════════════════════════════════════════════════════════

    public void creerReferentiel() {
        if (nouveauReferentielLibelle == null || nouveauReferentielLibelle.isBlank()) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le libellé est obligatoire.");
            return;
        }
        if (offreSelectionnee == null || offreSelectionnee.getReferentiel() != null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Offre requise",
                "Choisissez une offre sans référentiel pour lui en créer un.");
            return;
        }
        String libelle = nouveauReferentielLibelle;
        recrutementService.creerReferentiel(libelle, sessionBean.getInstitutionCourante(), getOperateur());
        referentiels = recrutementService.getReferentiels(sessionBean.getInstitutionId());
        nouveauReferentielLibelle = null;
        // 1 offre = 1 référentiel : le référentiel qu'on vient de créer est rattaché à l'offre.
        referentiels.stream().filter(r -> libelle.equals(r.getLibelle()))
            .max(java.util.Comparator.comparing(ReferentielEvaluation::getId))
            .ifPresent(r -> { referentielAAssocierId = r.getId(); associerReferentiel(); });
    }

    public void selectionnerReferentiel(ReferentielEvaluation r) {
        this.referentielSelectionne = r;
        this.phases = recrutementService.getPhases(r.getId());
        this.phaseSelectionnee = null;
        this.rubriques = new ArrayList<>();
        this.elements = new ArrayList<>();
    }

    public void ajouterPhase() {
        if (referentielSelectionne == null || nouvellePhaseLibelle == null || nouvellePhaseLibelle.isBlank()) return;
        recrutementService.ajouterPhase(referentielSelectionne, nouvellePhaseLibelle, nouvellePhaseNoteEliminatoire);
        phases = recrutementService.getPhases(referentielSelectionne.getId());
        nouvellePhaseLibelle = null;
        nouvellePhaseNoteEliminatoire = null;
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Phase ajoutée.");
    }

    public void selectionnerPhase(PhaseEvaluation p) {
        this.phaseSelectionnee = p;
        this.rubriques = recrutementService.getRubriques(p.getId());
        this.elements = new ArrayList<>();
    }

    public void ajouterRubrique() {
        if (phaseSelectionnee == null || nouvelleRubriqueLibelle == null || nouvelleRubriqueLibelle.isBlank()) return;
        recrutementService.ajouterRubrique(phaseSelectionnee, nouvelleRubriqueLibelle, nouvelleRubriqueCoefficient);
        rubriques = recrutementService.getRubriques(phaseSelectionnee.getId());
        nouvelleRubriqueLibelle = null;
        nouvelleRubriqueCoefficient = 1;
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Rubrique ajoutée.");
    }

    public void selectionnerRubrique(Rubrique r) {
        this.rubriqueSelectionnee = r;
        this.elements = recrutementService.getElements(r.getId());
    }

    public void ajouterElement() {
        if (rubriqueSelectionnee == null || nouvelElementLibelle == null || nouvelElementLibelle.isBlank()) return;
        recrutementService.ajouterElement(rubriqueSelectionnee, nouvelElementLibelle, nouvelElementPointsMax);
        elements = recrutementService.getElements(rubriqueSelectionnee.getId());
        nouvelElementLibelle = null;
        nouvelElementPointsMax = 20;
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Élément ajouté.");
    }

    // ════════════════════════════════════════════════════════════════════
    // SÉLECTION D'UNE OFFRE (contexte commun jury / phases / décision)
    // ════════════════════════════════════════════════════════════════════

    public Long getOffreSelectionneeId() {
        return offreSelectionnee != null ? offreSelectionnee.getId() : null;
    }

    public void setOffreSelectionneeId(Long id) {
        // f:viewParam est retraité à CHAQUE requête ajax tant que l'URL porte ?offreId=...
        // (pas seulement au chargement initial) — sans ce garde-fou, selectionnerOffre()
        // réinitialisait phaseOffreSelectionnee (et tout l'état dépendant) à chaque clic,
        // y compris quand l'offre sélectionnée ne changeait pas réellement.
        if (id == null) return;
        if (offreSelectionnee != null && offreSelectionnee.getId().equals(id)) return;
        selectionnerOffre(offres.stream().filter(o -> o.getId().equals(id)).findFirst().orElse(null));
    }

    public void selectionnerOffre(OffreEmploi o) {
        this.offreSelectionnee = o;
        this.elementsPourPhaseCache.clear();
        this.releveNotesCache.clear();
        this.avisCandidatureCache.clear();
        this.notesParCandidatureCache.clear();
        this.pointsCumulesCache.clear();
        this.pointsMaxCumulesCache.clear();
        this.releveCumuleCache = null;
        // Une offre a au plus un référentiel : on ouvre directement le sien (ou rien).
        this.referentielSelectionne = null;
        this.phases = new ArrayList<>();
        this.phaseSelectionnee = null;
        this.rubriques = new ArrayList<>();
        this.rubriqueSelectionnee = null;
        this.elements = new ArrayList<>();
        if (o == null) return;
        if (o.getReferentiel() != null) selectionnerReferentiel(o.getReferentiel());
        this.comites = recrutementService.getComitesByOffre(o.getId());
        this.comiteSelectionne = null;
        this.membres = new ArrayList<>();
        this.phasesOffre = recrutementService.getPhasesOffre(o.getId());
        this.phaseOffreSelectionnee = null;
        this.vueCumuleeSelectionnee = false;
        this.candidatures = candidatureDao.findByOffre(o.getId());
        this.rapports = recrutementService.getRapports(o.getId());
        this.pvs = recrutementService.getPvs(o.getId());
        this.candidatsFinaux = recrutementService.getCandidatsFinaux(o.getId());
    }

    public void associerReferentiel() {
        if (offreSelectionnee == null || referentielAAssocierId == null) return;
        ReferentielEvaluation ref = referentiels.stream()
            .filter(r -> r.getId().equals(referentielAAssocierId)).findFirst().orElse(null);
        if (ref == null) return;
        offreSelectionnee.setReferentiel(ref);
        offreEmploiDao.update(offreSelectionnee);
        selectionnerReferentiel(ref);
        addMsg(FacesMessage.SEVERITY_INFO, "Succès",
            "Référentiel « " + ref.getLibelle() + " » associé à l'offre.");
    }

    // ════════════════════════════════════════════════════════════════════
    // JURY
    // ════════════════════════════════════════════════════════════════════

    public void creerComite() {
        if (offreSelectionnee == null || nouveauComiteLibelle == null || nouveauComiteLibelle.isBlank()) return;
        recrutementService.creerComite(offreSelectionnee, nouveauComiteLibelle);
        comites = recrutementService.getComitesByOffre(offreSelectionnee.getId());
        nouveauComiteLibelle = null;
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Comité créé.");
    }

    public void selectionnerComite(Comite c) {
        this.comiteSelectionne = c;
        this.membres = recrutementService.getMembres(c.getId());
    }

    public void ajouterMembre() {
        if (comiteSelectionne == null || employeAAjouterId == null) return;
        Employe employe = employes.stream().filter(e -> e.getId().equals(employeAAjouterId)).findFirst().orElse(null);
        if (employe == null) return;
        String erreur = recrutementService.ajouterMembre(comiteSelectionne, employe, roleMembre);
        if (erreur != null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Attention", erreur);
        } else {
            membres = recrutementService.getMembres(comiteSelectionne.getId());
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Membre ajouté au jury.");
        }
        employeAAjouterId = null;
    }

    public void retirerMembre(ComiteEvaluateur m) {
        recrutementService.retirerMembre(m);
        membres = recrutementService.getMembres(comiteSelectionne.getId());
        addMsg(FacesMessage.SEVERITY_WARN, "Retiré", "Membre retiré du jury.");
    }

    // ════════════════════════════════════════════════════════════════════
    // PHASES & NOTATION
    // ════════════════════════════════════════════════════════════════════

    /** Phase la plus avancée de cette offre — utilisé pour l'aperçu "Notes" quand aucune offre n'est encore sélectionnée. */
    public PhaseOffre getPhaseActuelleOffre(Long offreId) {
        if (offreId == null) return null;
        List<PhaseOffre> phases = recrutementService.getPhasesOffre(offreId);
        return (phases == null || phases.isEmpty()) ? null : phases.get(phases.size() - 1);
    }

    private PhaseOffre trouverPhaseOffre(PhaseEvaluation phase) {
        if (phase == null || phasesOffre == null) return null;
        return phasesOffre.stream().filter(po -> po.getPhase().getId().equals(phase.getId())).findFirst().orElse(null);
    }

    /** Nombre de candidats ayant déjà reçu au moins une note sur cette phase (toutes évaluateurs confondus). */
    public int getNbCandidatsNotes(PhaseOffre po) {
        if (po == null) return 0;
        return (int) recrutementService.getNotesPhase(po.getId()).stream()
            .map(n -> n.getCandidature().getId()).distinct().count();
    }

    /** Une étape du parcours de l'offre : la phase du référentiel, sa phase_offre si déjà lancée/planifiée, et sa progression. */
    public static class EtapePhase implements Serializable {
        private static final long serialVersionUID = 1L;
        private final PhaseEvaluation phase;
        private final PhaseOffre phaseOffre;
        private final boolean disponible;
        private final int candidatsNotes;
        private final int candidatsTotal;

        EtapePhase(PhaseEvaluation phase, PhaseOffre phaseOffre, boolean disponible, int candidatsNotes, int candidatsTotal) {
            this.phase = phase; this.phaseOffre = phaseOffre; this.disponible = disponible;
            this.candidatsNotes = candidatsNotes; this.candidatsTotal = candidatsTotal;
        }
        public PhaseEvaluation getPhase() { return phase; }
        public PhaseOffre getPhaseOffre() { return phaseOffre; }
        public boolean isDisponible() { return disponible; }
        public int getCandidatsNotes() { return candidatsNotes; }
        public int getCandidatsTotal() { return candidatsTotal; }
        /** PLANIFIEE / EN_COURS / CLOTUREE si lancée, sinon NON_PLANIFIEE (dispo) ou INDISPONIBLE (phase précédente pas close). */
        public String getStatutAffiche() {
            if (phaseOffre != null) return phaseOffre.getStatut();
            return disponible ? "NON_PLANIFIEE" : "INDISPONIBLE";
        }
    }

    /** Le parcours complet des phases du référentiel de l'offre, pour le stepper et les cartes de l'onglet Phases & Notation. */
    public List<EtapePhase> getEtapesPhases() {
        List<EtapePhase> result = new ArrayList<>();
        if (phases == null || phases.isEmpty()) return result;
        List<PhaseEvaluation> triees = phases.stream()
            .sorted(java.util.Comparator.comparing(PhaseEvaluation::getNumeroOrdre))
            .collect(java.util.stream.Collectors.toList());
        int total = candidatures == null ? 0 : candidatures.size();
        boolean precedenteCloturee = true;
        for (PhaseEvaluation p : triees) {
            PhaseOffre po = trouverPhaseOffre(p);
            boolean disponible = precedenteCloturee;
            int notes = getNbCandidatsNotes(po);
            result.add(new EtapePhase(p, po, disponible, notes, total));
            precedenteCloturee = po != null && "CLOTUREE".equals(po.getStatut());
        }
        return result;
    }

    // ── Panneau latéral "Planifier «X»" — cible une phase du référentiel, qu'elle soit déjà lancée ou non ──

    private PhaseEvaluation phaseActionEnCours;
    public PhaseEvaluation getPhaseActionEnCours() { return phaseActionEnCours; }

    /** Phase_offre déjà existante pour la phase actuellement ciblée par le panneau (mode modification), ou null (mode planification). */
    public PhaseOffre getPhaseOffreActionEnCours() { return trouverPhaseOffre(phaseActionEnCours); }

    /** Ouvre le panneau pour une phase donnée, préremplissant ses dates si elle est déjà planifiée/en cours. */
    public void ouvrirPlanification(PhaseEvaluation phase) {
        this.phaseActionEnCours = phase;
        PhaseOffre po = trouverPhaseOffre(phase);
        this.dateDebutPhase = po != null ? po.getDateDebutPrevue() : null;
        this.dateLimitePhase = po != null ? po.getDateLimite() : null;
    }

    public void fermerPlanification() {
        this.phaseActionEnCours = null;
        this.dateDebutPhase = null;
        this.dateLimitePhase = null;
    }

    /** Lance/planifie la phase ciblée si elle n'existe pas encore, sinon met à jour ses dates. */
    public void confirmerPlanification() {
        if (phaseActionEnCours == null) return;
        PhaseOffre po = trouverPhaseOffre(phaseActionEnCours);
        if (po == null) {
            lancerPhase(phaseActionEnCours);
        } else {
            recrutementService.modifierDatesPhase(po, dateDebutPhase, dateLimitePhase);
            phasesOffre = recrutementService.getPhasesOffre(offreSelectionnee.getId());
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Dates mises à jour pour « " + po.getPhase().getLibelle() + " ».");
            dateDebutPhase = null;
            dateLimitePhase = null;
        }
        phaseActionEnCours = null;
    }

    private java.time.LocalDateTime dateDebutPhase;
    private java.time.LocalDateTime dateLimitePhase;
    public java.time.LocalDateTime getDateDebutPhase() { return dateDebutPhase; }
    public void setDateDebutPhase(java.time.LocalDateTime v) { this.dateDebutPhase = v; }
    public java.time.LocalDateTime getDateLimitePhase() { return dateLimitePhase; }
    public void setDateLimitePhase(java.time.LocalDateTime v) { this.dateLimitePhase = v; }
    public java.time.LocalDateTime getMaintenant() { return java.time.LocalDateTime.now(); }

    /**
     * Si une date de début future est fournie, planifie la phase (lancement
     * automatique par le scheduler) ; sinon la lance immédiatement comme
     * avant. La date de fin (si fournie) déclenche la clôture automatique
     * dans les deux cas.
     */
    public void lancerPhase(PhaseEvaluation phase) {
        if (offreSelectionnee == null) return;
        if (!"FERMEE".equals(offreSelectionnee.getStatut())) {
            addMsg(FacesMessage.SEVERITY_WARN, "Attention",
                "L'offre doit d'abord être fermée aux candidatures avant de lancer une phase d'évaluation.");
            return;
        }
        if (dateDebutPhase != null && dateDebutPhase.isAfter(java.time.LocalDateTime.now())) {
            recrutementService.planifierPhase(offreSelectionnee, phase, dateDebutPhase, dateLimitePhase);
            addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                "Phase « " + phase.getLibelle() + " » planifiée — elle se lancera automatiquement le "
                    + dateDebutPhase.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".");
        } else {
            recrutementService.lancerPhase(offreSelectionnee, phase, dateLimitePhase, getOperateur());
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Phase « " + phase.getLibelle() + " » lancée.");
        }
        phasesOffre = recrutementService.getPhasesOffre(offreSelectionnee.getId());
        dateDebutPhase = null;
        dateLimitePhase = null;
    }

    /** Force le lancement immédiat d'une phase planifiée, avant sa date de début prévue. */
    public void activerPhaseMaintenant(PhaseOffre po) {
        if (po == null || !"PLANIFIEE".equals(po.getStatut())) return;
        recrutementService.activerPhasePlanifiee(po, getOperateur());
        phasesOffre = recrutementService.getPhasesOffre(offreSelectionnee.getId());
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Phase « " + po.getPhase().getLibelle() + " » lancée.");
    }

    /** Enregistre les dates (modifiées directement sur l'objet via les champs liés) d'une phase planifiée ou en cours. */
    public void enregistrerDatesPhase(PhaseOffre po) {
        if (po == null) return;
        recrutementService.modifierDatesPhase(po, po.getDateDebutPrevue(), po.getDateLimite());
        phasesOffre = recrutementService.getPhasesOffre(offreSelectionnee.getId());
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Dates mises à jour pour « " + po.getPhase().getLibelle() + " ».");
    }

    private PhaseOffre phaseAReouvrir;
    private java.time.LocalDateTime nouvelleDateFinReouverture;
    public PhaseOffre getPhaseAReouvrir() { return phaseAReouvrir; }
    public java.time.LocalDateTime getNouvelleDateFinReouverture() { return nouvelleDateFinReouverture; }
    public void setNouvelleDateFinReouverture(java.time.LocalDateTime v) { this.nouvelleDateFinReouverture = v; }

    /** Ouvre la petite boîte de dialogue demandant la nouvelle date/heure de fin, pour une phase clôturée donnée. */
    public void preparerReouverture(PhaseOffre po) {
        this.phaseAReouvrir = po;
        this.nouvelleDateFinReouverture = null;
    }

    public void fermerReouverture() {
        this.phaseAReouvrir = null;
    }

    /** Repousse la date de fin d'une phase déjà clôturée — la phase repasse EN_COURS jusqu'à cette nouvelle échéance. */
    public void confirmerReouverture() {
        if (phaseAReouvrir == null || nouvelleDateFinReouverture == null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Attention", "Choisissez une nouvelle date et heure de fin.");
            return;
        }
        recrutementService.reouvrirPhase(phaseAReouvrir, nouvelleDateFinReouverture);
        phasesOffre = recrutementService.getPhasesOffre(offreSelectionnee.getId());
        addMsg(FacesMessage.SEVERITY_INFO, "Phase rouverte",
            "« " + phaseAReouvrir.getPhase().getLibelle() + " » repasse en cours jusqu'au "
                + nouvelleDateFinReouverture.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".");
        phaseAReouvrir = null;
        nouvelleDateFinReouverture = null;
    }

    public void selectionnerPhaseOffre(PhaseOffre po) {
        this.phaseOffreSelectionnee = po;
        this.elementsNotation = chargerElementsPourNotation(po.getPhase());
        this.vueCumuleeSelectionnee = false;
    }

    /** Pour la page Notes : reclique sur la phase déjà affichée = la masquer, sinon l'affiche (et masque la vue cumulée). */
    public void togglePhaseNotes(PhaseOffre po) {
        if (phaseOffreSelectionnee != null && po != null && phaseOffreSelectionnee.getId().equals(po.getId())) {
            phaseOffreSelectionnee = null;
        } else {
            selectionnerPhaseOffre(po);
        }
    }

    /** Pour la page Notes : reclique sur « Total cumulé » déjà affiché = le masquer. */
    public void toggleVueCumulee() {
        if (vueCumuleeSelectionnee) {
            vueCumuleeSelectionnee = false;
        } else {
            selectionnerVueCumulee();
        }
    }

    /** Vrai si l'offre a au moins une phase et qu'elles sont toutes clôturées — condition d'affichage du relevé cumulé. */
    public boolean isToutesPhasesCloturees() {
        return phasesOffre != null && !phasesOffre.isEmpty()
            && phasesOffre.stream().allMatch(po -> "CLOTUREE".equals(po.getStatut()));
    }

    private boolean vueCumuleeSelectionnee;
    public boolean isVueCumuleeSelectionnee() { return vueCumuleeSelectionnee; }

    public void selectionnerVueCumulee() {
        this.vueCumuleeSelectionnee = true;
        this.phaseOffreSelectionnee = null;
    }

    /** Une ligne du relevé cumulé : candidat, total et max additionnés sur toutes les phases clôturées, rang, statut global. */
    public static class LigneCumulee implements Serializable {
        private static final long serialVersionUID = 1L;
        private Candidature candidature;
        private int total;
        private int max;
        private Integer rang;
        private String statut;
        public Candidature getCandidature() { return candidature; }
        public int getTotal() { return total; }
        public int getMax() { return max; }
        public Integer getRang() { return rang; }
        public String getStatut() { return statut; }
    }

    /** Mémoïsation — le relevé cumulé est référencé plusieurs fois par rendu (empty, rendered, value). */
    private List<LigneCumulee> releveCumuleCache;

    /** Relevé cumulé de l'offre : points obtenus / points maximum additionnés sur toutes les phases clôturées, par candidat. */
    public List<LigneCumulee> getReleveCumule() {
        if (releveCumuleCache != null) return releveCumuleCache;
        List<LigneCumulee> lignes = new ArrayList<>();
        if (candidatures != null && !candidatures.isEmpty()) {
            List<Long> ids = candidatures.stream().map(Candidature::getId).toList();
            Map<Long, com.soutenance.service.RecrutementService.ResumeCumule> resumes =
                recrutementService.getResumesCumulesParCandidature(ids);
            for (Candidature c : candidatures) {
                com.soutenance.service.RecrutementService.ResumeCumule r = resumes.get(c.getId());
                if (r == null || r.points() == null || r.pointsMax() == null) continue;
                LigneCumulee l = new LigneCumulee();
                l.candidature = c;
                l.total = r.points();
                l.max = r.pointsMax();
                l.statut = r.statut();
                lignes.add(l);
            }
        }
        lignes.sort((a, b) -> Integer.compare(b.total, a.total));
        int rang = 1;
        for (LigneCumulee l : lignes) l.rang = rang++;
        releveCumuleCache = lignes;
        return releveCumuleCache;
    }

    private List<ElementRubrique> chargerElementsPourNotation(PhaseEvaluation phase) {
        List<ElementRubrique> tous = new ArrayList<>();
        for (Rubrique r : recrutementService.getRubriques(phase.getId())) {
            tous.addAll(recrutementService.getElements(r.getId()));
        }
        return tous;
    }

    /** Caches par phaseOffre.id — évite de recalculer à chaque référence EL (p:columns, rendered, value...). */
    private final Map<Long, List<ElementRubrique>> elementsPourPhaseCache = new HashMap<>();
    private final Map<Long, List<LigneReleve>> releveNotesCache = new HashMap<>();
    /** Mémoïsation par candidatureId — ces getters sont appelés plusieurs fois par rendu (rendered + value des dataTable imbriquées). */
    private final Map<Long, List<AvisSelectionCandidat>> avisCandidatureCache = new HashMap<>();
    private final Map<Long, List<NoteCandidatEvalElement>> notesParCandidatureCache = new HashMap<>();
    private final Map<Long, Integer> pointsCumulesCache = new HashMap<>();
    private final Map<Long, Integer> pointsMaxCumulesCache = new HashMap<>();

    /** Colonnes (éléments notés) d'une phase — pour le relevé de notes détaillé. */
    public List<ElementRubrique> getElementsPourPhase(PhaseOffre po) {
        if (po == null) return new ArrayList<>();
        return elementsPourPhaseCache.computeIfAbsent(po.getId(), id -> chargerElementsPourNotation(po.getPhase()));
    }

    /** Une ligne du relevé de notes détaillé : candidat, rang, moyenne par élément noté, total. */
    public static class LigneReleve implements Serializable {
        private static final long serialVersionUID = 1L;
        private Candidature candidature;
        private Integer rang;
        private Integer totalPoints;
        private String statut;
        private Map<Long, Double> notesParElement = new HashMap<>();

        public Candidature getCandidature() { return candidature; }
        public Integer getRang() { return rang; }
        public Integer getTotalPoints() { return totalPoints; }
        public String getStatut() { return statut; }
        public Map<Long, Double> getNotesParElement() { return notesParElement; }
    }

    /**
     * Relevé de notes détaillé d'une phase : pour chaque candidat, son rang,
     * sa note moyenne (tous évaluateurs confondus) sur chaque élément noté,
     * et son total — contrairement au classement qui ne montre que le total.
     */
    public List<LigneReleve> getReleveNotes(PhaseOffre po) {
        if (po == null) return new ArrayList<>();
        List<LigneReleve> cached = releveNotesCache.get(po.getId());
        if (cached != null) return cached;
        List<NoteCandidatEvalElement> notes = recrutementService.getNotesPhase(po.getId());
        List<ResultatPhaseCandidature> resultats = recrutementService.getResultats(po.getId());

        Map<Long, ResultatPhaseCandidature> resultatParCandidature = new HashMap<>();
        for (ResultatPhaseCandidature r : resultats) resultatParCandidature.put(r.getCandidature().getId(), r);

        Map<Long, Candidature> candidatureParId = new java.util.LinkedHashMap<>();
        Map<Long, Map<Long, List<Integer>>> pointsParCandidatureEtElement = new HashMap<>();
        for (NoteCandidatEvalElement n : notes) {
            Long cid = n.getCandidature().getId();
            candidatureParId.putIfAbsent(cid, n.getCandidature());
            pointsParCandidatureEtElement
                .computeIfAbsent(cid, k -> new HashMap<>())
                .computeIfAbsent(n.getElement().getId(), k -> new ArrayList<>())
                .add(n.getPoints());
        }

        List<LigneReleve> lignes = new ArrayList<>();
        for (Candidature c : candidatureParId.values()) {
            LigneReleve l = new LigneReleve();
            l.candidature = c;
            ResultatPhaseCandidature r = resultatParCandidature.get(c.getId());
            l.rang = r != null ? r.getRang() : null;
            l.totalPoints = r != null ? r.getNombrePoints() : null;
            l.statut = r != null ? r.getStatut() : null;
            for (Map.Entry<Long, List<Integer>> e : pointsParCandidatureEtElement.getOrDefault(c.getId(), new HashMap<>()).entrySet()) {
                l.notesParElement.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0));
            }
            lignes.add(l);
        }
        lignes.sort(java.util.Comparator.comparing(l -> l.rang == null ? Integer.MAX_VALUE : l.rang));
        releveNotesCache.put(po.getId(), lignes);
        return lignes;
    }

    /** Clôture une phase donnée (depuis sa carte dans le stepper). */
    public void cloturerPhase(PhaseOffre po) {
        if (po == null) return;
        recrutementService.cloturerPhase(po);
        phasesOffre = recrutementService.getPhasesOffre(offreSelectionnee.getId());
        addMsg(FacesMessage.SEVERITY_INFO, "Phase clôturée", "Classement calculé pour « " + po.getPhase().getLibelle() + " ».");
    }

    // ════════════════════════════════════════════════════════════════════
    // RAPPORT & DÉLIBÉRATION
    // ════════════════════════════════════════════════════════════════════

    public void ouvrirDlgRapport() {
        if (phaseOffreSelectionnee == null) return;

        // Composition du comité — pré-remplie depuis le jury réel de l'offre, comme pour le PV.
        if (nouveauRapportCompositionComite == null || nouveauRapportCompositionComite.isBlank()) {
            List<ComiteEvaluateur> tousMembres = new ArrayList<>();
            for (Comite c : comites) {
                tousMembres.addAll(recrutementService.getMembres(c.getId()));
            }
            if (!tousMembres.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ComiteEvaluateur m : tousMembres) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(m.getEvaluateur().getNomComplet()).append(" (").append(libelleRole(m.getRole())).append(")");
                }
                nouveauRapportCompositionComite = sb.toString();
            }
        }

        // Motif du recrutement — repris de l'offre si elle le précise (saisi une seule fois à la création de l'offre).
        if ((nouveauRapportMotif == null || nouveauRapportMotif.isBlank())
                && offreSelectionnee != null && offreSelectionnee.getMotifRecrutement() != null
                && !offreSelectionnee.getMotifRecrutement().isBlank()) {
            nouveauRapportMotif = offreSelectionnee.getMotifRecrutement();
        }

        // Candidatures recevables/irrecevables + motifs — approximation automatique à partir des
        // candidatures déjà refusées (avec motif) sur cette offre. Le système ne distingue pas un
        // rejet administratif initial (hors délai, dossier incomplet) d'un refus après entretien,
        // donc ces valeurs restent modifiables par le RH si elles ne correspondent pas exactement.
        if (nouveauRapportCandidaturesRecevables == null) {
            long irrecevables = candidatures.stream().filter(c -> "REFUSEE".equals(c.getStatut())).count();
            nouveauRapportCandidaturesIrrecevables = (int) irrecevables;
            nouveauRapportCandidaturesRecevables = candidatures.size() - (int) irrecevables;
            String motifs = candidatures.stream()
                .filter(c -> "REFUSEE".equals(c.getStatut()) && c.getCommentaireRH() != null && !c.getCommentaireRH().isBlank())
                .map(c -> c.getNomPrenomCandidat() + " : " + c.getCommentaireRH())
                .distinct()
                .reduce((a, b) -> a + "\n" + b).orElse(null);
            if (motifs != null) nouveauRapportMotifsExclusion = motifs;
        }
        // p:inputNumber avec une valeur Integer null au premier affichage d'un dialogue dynamique
        // peut échouer silencieusement côté client (contrairement à un int primitif, jamais null) —
        // on force donc 0 par défaut si rien n'a pu être calculé.
        if (nouveauRapportCandidaturesRecevables == null) nouveauRapportCandidaturesRecevables = 0;
        if (nouveauRapportCandidaturesIrrecevables == null) nouveauRapportCandidaturesIrrecevables = 0;

        dlgRapportOuvert = true;
    }

    public void fermerDlgRapport() {
        dlgRapportOuvert = false;
    }

    public void genererRapport() {
        if (phaseOffreSelectionnee == null || nouveauRapportLibelle == null || nouveauRapportLibelle.isBlank()) return;
        RapportPhaseEvaluation r = new RapportPhaseEvaluation();
        r.setPhaseOffre(phaseOffreSelectionnee);
        r.setLibelle(nouveauRapportLibelle);
        r.setNotesAutorite(nouveauRapportNotes);
        r.setMotifRecrutement(nouveauRapportMotif);
        r.setCandidaturesRecevables(nouveauRapportCandidaturesRecevables);
        r.setCandidaturesIrrecevables(nouveauRapportCandidaturesIrrecevables);
        r.setMotifsExclusion(nouveauRapportMotifsExclusion);
        r.setCompositionComite(nouveauRapportCompositionComite);
        r.setDifficultesAmeliorations(nouveauRapportDifficultes);
        r.setRecommandationDecision(nouveauRapportRecommandation);
        recrutementService.genererRapport(r);
        rapports = recrutementService.getRapports(offreSelectionnee.getId());
        nouveauRapportLibelle = null;
        nouveauRapportNotes = null;
        nouveauRapportMotif = null;
        nouveauRapportCandidaturesRecevables = null;
        nouveauRapportCandidaturesIrrecevables = null;
        nouveauRapportMotifsExclusion = null;
        nouveauRapportCompositionComite = null;
        nouveauRapportDifficultes = null;
        nouveauRapportRecommandation = null;
        dlgRapportOuvert = false;
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Rapport généré.");
    }

    public void ouvrirDlgPv() {
        if (offreSelectionnee == null) return;
        if (nouveauPvMembresJury == null || nouveauPvMembresJury.isBlank()) {
            List<ComiteEvaluateur> tousMembres = new ArrayList<>();
            for (Comite c : comites) {
                tousMembres.addAll(recrutementService.getMembres(c.getId()));
            }
            if (!tousMembres.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ComiteEvaluateur m : tousMembres) {
                    sb.append(m.getEvaluateur().getNomComplet())
                      .append(" - ").append(libelleRole(m.getRole()))
                      .append(" - Présent(e)\n");
                }
                nouveauPvMembresJury = sb.toString().stripTrailing();
                nouveauPvNombreMembres = tousMembres.size();
            }
        }
        dlgPvOuvert = true;
    }

    private String libelleRole(String role) {
        if (role == null) return "Membre";
        return switch (role) {
            case "PRESIDENT" -> "Président(e)";
            case "SECRETAIRE" -> "Secrétaire de séance";
            case "RAPPORTEUR" -> "Rapporteur";
            default -> "Membre";
        };
    }

    public void fermerDlgPv() {
        dlgPvOuvert = false;
    }

    public void creerPv() {
        if (offreSelectionnee == null || nouveauPvLibelle == null || nouveauPvLibelle.isBlank()) return;
        PVDeliberation pv = new PVDeliberation();
        pv.setOffre(offreSelectionnee);
        pv.setLibelle(nouveauPvLibelle);
        pv.setNombreMembres(nouveauPvNombreMembres);
        pv.setLieu(nouveauPvLieu);
        pv.setHeureDebut(nouveauPvHeureDebut);
        pv.setHeureCloture(nouveauPvHeureCloture);
        pv.setMembresJury(nouveauPvMembresJury);
        pv.setDeclarationConflitInteret(nouveauPvDeclarationConflitInteret);
        pv.setModeDecision(nouveauPvModeDecision);
        pv.setResultatVote(nouveauPvResultatVote);
        pv.setReserves(nouveauPvReserves);
        pv.setSuitesADonner(nouveauPvSuitesADonner);
        recrutementService.creerPv(pv);
        pvs = recrutementService.getPvs(offreSelectionnee.getId());
        nouveauPvLibelle = null;
        nouveauPvNombreMembres = 0;
        nouveauPvLieu = null;
        nouveauPvHeureDebut = null;
        nouveauPvHeureCloture = null;
        nouveauPvMembresJury = null;
        nouveauPvDeclarationConflitInteret = null;
        nouveauPvModeDecision = null;
        nouveauPvResultatVote = null;
        nouveauPvReserves = null;
        nouveauPvSuitesADonner = null;
        dlgPvOuvert = false;
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "PV de délibération enregistré.");
    }

    public void notifierJuryRapport(Long rapportId) {
        RapportPhaseEvaluation r = rapports.stream()
            .filter(x -> x.getId().equals(rapportId)).findFirst().orElse(null);
        if (r == null) return;
        recrutementService.notifierJuryRapport(r);
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Jury notifié.");
    }

    public void notifierJuryPv(Long pvId) {
        PVDeliberation pv = pvs.stream()
            .filter(x -> x.getId().equals(pvId)).findFirst().orElse(null);
        if (pv == null) return;
        recrutementService.notifierJuryPv(pv);
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Jury notifié.");
    }

    public void telechargerRapport(Long rapportId) {
        RapportPhaseEvaluation r = rapports.stream()
            .filter(x -> x.getId().equals(rapportId)).findFirst().orElse(null);
        if (r == null || rapportPvPdfService == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Impossible de générer le PDF de ce rapport.");
            return;
        }
        ecrirePdf(rapportPvPdfService.genererRapport(r), "rapport-" + rapportId + ".pdf");
    }

    public void telechargerPv(Long pvId) {
        PVDeliberation pv = pvs.stream()
            .filter(x -> x.getId().equals(pvId)).findFirst().orElse(null);
        if (pv == null || rapportPvPdfService == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Impossible de générer le PDF de ce PV.");
            return;
        }
        ecrirePdf(rapportPvPdfService.genererPv(pv, candidatsFinaux), "pv-deliberation-" + pvId + ".pdf");
    }

    private void ecrirePdf(byte[] pdf, String nomFichier) {
        if (pdf == null || pdf.length == 0) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Erreur lors de la génération du PDF.");
            return;
        }
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + nomFichier + "\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Erreur lors du téléchargement : " + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CLASSEMENT FINAL & DÉCISION
    // ════════════════════════════════════════════════════════════════════

    public void construireClassementFinal() {
        if (offreSelectionnee == null) return;
        try {
            recrutementService.construireClassementFinal(offreSelectionnee);
            candidatsFinaux = recrutementService.getCandidatsFinaux(offreSelectionnee.getId());
            pointsCumulesCache.clear();
            pointsMaxCumulesCache.clear();
            releveCumuleCache = null;
            if (candidatsFinaux.isEmpty()) {
                addMsg(FacesMessage.SEVERITY_WARN, "Aucun candidat",
                    "Aucune candidature n'est ADMIS a la derniere phase cloturee de cette offre.");
            } else {
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Classement final construit.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void selectionnerCandidatFinal(CandidatFinal cf) {
        this.candidatFinalSelectionne = cf;
    }

    public void fermerDecision() {
        this.candidatFinalSelectionne = null;
    }

    /** Score maximum cumulé sur toutes les phases clôturées — même échelle que le total affiché dans le classement. */
    public Integer getPointsMaxTotalConcours() {
        return offreSelectionnee == null ? null
            : recrutementService.getPointsMaxToutesPhases(offreSelectionnee.getId());
    }

    public Integer getSeuilPointsTotalConcours() {
        if (offreSelectionnee == null || offreSelectionnee.getSeuilRecrutementAutoPct() == null) return null;
        Integer max = getPointsMaxTotalConcours();
        if (max == null) return null;
        return (int) Math.ceil(max * offreSelectionnee.getSeuilRecrutementAutoPct() / 100.0);
    }

    /** Seuil comparé au total cumulé du candidat (toutes phases), cohérent avec le classement affiché. */
    public boolean isCandidatEligibleAuto(CandidatFinal cf) {
        if (cf == null || offreSelectionnee == null) return false;
        Integer seuilPct = offreSelectionnee.getSeuilRecrutementAutoPct();
        if (seuilPct == null) return false;
        Integer max = cf.getCandidature() != null
            ? recrutementService.getPointsMaxCandidature(cf.getCandidature().getId())
            : null;
        if (max == null || max == 0) return false;
        double pourcentageObtenu = cf.getNombrePoints() * 100.0 / max;
        return pourcentageObtenu >= seuilPct;
    }

    /** Vrai si ce candidat fait partie des meilleurs selon le nombre de postes disponibles sur l'offre. */
    public boolean isCandidatDansQuotaPostes(CandidatFinal cf) {
        if (cf == null || cf.getRang() == null || offreSelectionnee == null) return false;
        Integer postes = offreSelectionnee.getNombrePostes();
        return postes != null && cf.getRang() <= postes;
    }

    private long getNbRetenusOffre() {
        if (candidatsFinaux == null) return 0;
        return candidatsFinaux.stream().filter(cf -> "RETENU".equals(cf.getStatut())).count();
    }

    public void decider(String statut) {
        if (candidatFinalSelectionne == null) return;
        Employe autorite = getEmployeConnecte();

        if ("RETENU".equals(statut) && offreSelectionnee != null && offreSelectionnee.getNombrePostes() != null
                && !"RETENU".equals(candidatFinalSelectionne.getStatut())
                && getNbRetenusOffre() >= offreSelectionnee.getNombrePostes()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Quota de postes atteint",
                "Les " + offreSelectionnee.getNombrePostes() + " poste(s) disponible(s) pour cette offre sont déjà pourvus — vous pouvez tout de même continuer.");
        }

        String erreur = recrutementService.decider(candidatFinalSelectionne, statut, autorite, notesDecision, getOperateur());
        if (erreur != null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
        } else {
            candidatsFinaux = recrutementService.getCandidatsFinaux(offreSelectionnee.getId());
            avisCandidatureCache.clear();
            addMsg(FacesMessage.SEVERITY_INFO, "Décision enregistrée",
                "RETENU".equals(statut) ? "Le candidat a été recruté." : "Décision enregistrée.");
        }
    }

    /**
     * Décision automatique en un clic : reconstruit le classement (par total cumulé), retient les
     * meilleurs candidats à hauteur du nombre de postes disponibles, refuse les autres — ne touche
     * pas aux candidats déjà décidés (RETENU/NON_RETENU), pour ne jamais revenir sur une décision.
     */
    public void deciderAutomatiquement() {
        if (offreSelectionnee == null || offreSelectionnee.getNombrePostes() == null) return;
        construireClassementFinal();
        if (candidatsFinaux == null || candidatsFinaux.isEmpty()) return;

        Employe autorite = getEmployeConnecte();
        int postes = offreSelectionnee.getNombrePostes();
        int retenus = 0;
        int refuses = 0;
        for (CandidatFinal cf : candidatsFinaux) {
            if ("RETENU".equals(cf.getStatut()) || "NON_RETENU".equals(cf.getStatut())) continue;
            String statut = (cf.getRang() != null && cf.getRang() <= postes) ? "RETENU" : "NON_RETENU";
            String erreur = recrutementService.decider(cf, statut, autorite,
                "Décision automatique — classement selon le total cumulé (" + postes + " poste(s) disponible(s)).",
                getOperateur());
            if (erreur != null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
            } else if ("RETENU".equals(statut)) {
                retenus++;
            } else {
                refuses++;
            }
        }
        candidatsFinaux = recrutementService.getCandidatsFinaux(offreSelectionnee.getId());
        addMsg(FacesMessage.SEVERITY_INFO, "Décision automatique appliquée",
            retenus + " candidat(s) recruté(s), " + refuses + " non retenu(s).");
    }

    /** Vrai si tous les candidats finalistes ont déjà une décision (RETENU/NON_RETENU) — plus rien à décider. */
    public boolean isDecisionFinaleTerminee() {
        return candidatsFinaux != null && !candidatsFinaux.isEmpty()
            && candidatsFinaux.stream().allMatch(cf -> "RETENU".equals(cf.getStatut()) || "NON_RETENU".equals(cf.getStatut()));
    }

    public long getNbCandidatsRetenus() {
        return candidatsFinaux == null ? 0 : candidatsFinaux.stream().filter(cf -> "RETENU".equals(cf.getStatut())).count();
    }

    /** Avis déjà donné par le président du jury sur le candidat sélectionné — lecture seule, le RH ne le saisit pas. */
    public List<AvisSelectionCandidat> getAvisPourSelection() {
        if (candidatFinalSelectionne == null) return new ArrayList<>();
        return recrutementService.getAvis(candidatFinalSelectionne.getCandidature().getId());
    }

    /** Avis du jury sur un candidat donné — pour le détail du PV de délibération (un candidat quelconque, pas forcément sélectionné). */
    public List<AvisSelectionCandidat> getAvisCandidature(Candidature c) {
        if (c == null) return new ArrayList<>();
        return avisCandidatureCache.computeIfAbsent(c.getId(), id -> recrutementService.getAvis(id));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public List<OffreEmploi> getOffres() { return offres; }
    public List<Employe> getEmployes() { return employes; }

    /** Membres des comités de jury de l'offre sélectionnée, tous rôles confondus — seuls eux peuvent noter. */
    public List<Employe> getEvaluateursDisponibles() {
        if (comites == null || comites.isEmpty()) return new ArrayList<>();
        java.util.LinkedHashMap<Long, Employe> uniques = new java.util.LinkedHashMap<>();
        for (Comite c : comites) {
            for (ComiteEvaluateur m : recrutementService.getMembres(c.getId())) {
                uniques.put(m.getEvaluateur().getId(), m.getEvaluateur());
            }
        }
        return new ArrayList<>(uniques.values());
    }

    /** Nombre total de membres de jury (tous comités confondus) pour l'offre sélectionnée — affiché en badge sur l'onglet Jury. */
    public int getNbMembresJury() { return getEvaluateursDisponibles().size(); }

    /** Unité (direction/division/service/bureau) du poste visé par l'offre sélectionnée, si renseignée. */
    private String uniteOffreSelectionnee() {
        if (offreSelectionnee == null || offreSelectionnee.getPoste() == null) return null;
        String u = offreSelectionnee.getPoste().getUnite();
        return (u != null && !u.isBlank()) ? u : null;
    }

    /** Employés rattachés à l'unité du poste offert — proposés en premier lors du choix d'un membre de jury. */
    public List<Employe> getEmployesUniteConcernee() {
        String unite = uniteOffreSelectionnee();
        if (unite == null) return new ArrayList<>();
        return employes.stream().filter(e ->
            unite.equalsIgnoreCase(e.getNomDirection())
            || unite.equalsIgnoreCase(e.getNomDivision())
            || unite.equalsIgnoreCase(e.getNomService())
            || unite.equalsIgnoreCase(e.getNomBureau())
        ).collect(java.util.stream.Collectors.toList());
    }

    /** Tous les autres employés de l'institution (ou tous, si le poste n'a pas d'unité renseignée). */
    public List<Employe> getEmployesAutres() {
        String unite = uniteOffreSelectionnee();
        if (unite == null) return employes;
        List<Employe> concernes = getEmployesUniteConcernee();
        return employes.stream().filter(e -> !concernes.contains(e)).collect(java.util.stream.Collectors.toList());
    }

    public List<ReferentielEvaluation> getReferentiels() { return referentiels; }
    public ReferentielEvaluation getReferentielSelectionne() { return referentielSelectionne; }
    public List<PhaseEvaluation> getPhases() { return phases; }
    public PhaseEvaluation getPhaseSelectionnee() { return phaseSelectionnee; }
    public List<Rubrique> getRubriques() { return rubriques; }
    public Rubrique getRubriqueSelectionnee() { return rubriqueSelectionnee; }
    public List<ElementRubrique> getElements() { return elements; }

    public String getNouveauReferentielLibelle() { return nouveauReferentielLibelle; }
    public void setNouveauReferentielLibelle(String v) { this.nouveauReferentielLibelle = v; }
    public String getNouvellePhaseLibelle() { return nouvellePhaseLibelle; }
    public void setNouvellePhaseLibelle(String v) { this.nouvellePhaseLibelle = v; }
    public Integer getNouvellePhaseNoteEliminatoire() { return nouvellePhaseNoteEliminatoire; }
    public void setNouvellePhaseNoteEliminatoire(Integer v) { this.nouvellePhaseNoteEliminatoire = v; }
    public String getNouvelleRubriqueLibelle() { return nouvelleRubriqueLibelle; }
    public void setNouvelleRubriqueLibelle(String v) { this.nouvelleRubriqueLibelle = v; }
    public int getNouvelleRubriqueCoefficient() { return nouvelleRubriqueCoefficient; }
    public void setNouvelleRubriqueCoefficient(int v) { this.nouvelleRubriqueCoefficient = v; }
    public String getNouvelElementLibelle() { return nouvelElementLibelle; }
    public void setNouvelElementLibelle(String v) { this.nouvelElementLibelle = v; }
    public int getNouvelElementPointsMax() { return nouvelElementPointsMax; }
    public void setNouvelElementPointsMax(int v) { this.nouvelElementPointsMax = v; }

    public OffreEmploi getOffreSelectionnee() { return offreSelectionnee; }
    public Long getReferentielAAssocierId() { return referentielAAssocierId; }
    public void setReferentielAAssocierId(Long v) { this.referentielAAssocierId = v; }

    public List<Comite> getComites() { return comites; }
    public Comite getComiteSelectionne() { return comiteSelectionne; }
    public List<ComiteEvaluateur> getMembres() { return membres; }
    public String getNouveauComiteLibelle() { return nouveauComiteLibelle; }
    public void setNouveauComiteLibelle(String v) { this.nouveauComiteLibelle = v; }
    public Long getEmployeAAjouterId() { return employeAAjouterId; }
    public void setEmployeAAjouterId(Long v) { this.employeAAjouterId = v; }
    public String getRoleMembre() { return roleMembre; }
    public void setRoleMembre(String v) { this.roleMembre = v; }

    public List<PhaseOffre> getPhasesOffre() { return phasesOffre; }
    public PhaseOffre getPhaseOffreSelectionnee() { return phaseOffreSelectionnee; }

    /** Avancement de la notation d'un membre du jury sur la phase sélectionnée. */
    public static class SuiviJure implements Serializable {
        private static final long serialVersionUID = 1L;
        private final ComiteEvaluateur membre;
        private final int saisies;
        private final int attendues;
        SuiviJure(ComiteEvaluateur membre, int saisies, int attendues) {
            this.membre = membre; this.saisies = saisies; this.attendues = attendues;
        }
        public ComiteEvaluateur getMembre() { return membre; }
        public int getSaisies() { return saisies; }
        public int getAttendues() { return attendues; }
        public String getStatut() {
            if (attendues > 0 && saisies >= attendues) return "TERMINE";
            return saisies == 0 ? "PAS_COMMENCE" : "EN_COURS";
        }
    }

    /**
     * Pour chaque membre du jury de l'offre : notes saisies / notes attendues
     * (candidats × éléments de la phase) — le RH voit ainsi qui n'a pas encore noté.
     */
    public List<SuiviJure> getSuiviJury() {
        List<SuiviJure> suivi = new ArrayList<>();
        if (phaseOffreSelectionnee == null || comites == null) return suivi;
        int attendues = (candidatures == null ? 0 : candidatures.size())
                      * (elementsNotation == null ? 0 : elementsNotation.size());
        java.util.Map<Long, Integer> parEvaluateur = new java.util.HashMap<>();
        for (NoteCandidatEvalElement n : recrutementService.getNotesPhase(phaseOffreSelectionnee.getId())) {
            parEvaluateur.merge(n.getEvaluateur().getId(), 1, Integer::sum);
        }
        for (Comite c : comites) {
            for (ComiteEvaluateur m : recrutementService.getMembres(c.getId())) {
                suivi.add(new SuiviJure(m, parEvaluateur.getOrDefault(m.getEvaluateur().getId(), 0), attendues));
            }
        }
        return suivi;
    }
    public List<Candidature> getCandidatures() { return candidatures; }

    public List<RapportPhaseEvaluation> getRapports() { return rapports; }
    public String getNouveauRapportLibelle() { return nouveauRapportLibelle; }
    public void setNouveauRapportLibelle(String v) { this.nouveauRapportLibelle = v; }
    public String getNouveauRapportNotes() { return nouveauRapportNotes; }
    public void setNouveauRapportNotes(String v) { this.nouveauRapportNotes = v; }
    public String getNouveauRapportMotif() { return nouveauRapportMotif; }
    public void setNouveauRapportMotif(String v) { this.nouveauRapportMotif = v; }
    public Integer getNouveauRapportCandidaturesRecevables() { return nouveauRapportCandidaturesRecevables; }
    public void setNouveauRapportCandidaturesRecevables(Integer v) { this.nouveauRapportCandidaturesRecevables = v; }
    public Integer getNouveauRapportCandidaturesIrrecevables() { return nouveauRapportCandidaturesIrrecevables; }
    public void setNouveauRapportCandidaturesIrrecevables(Integer v) { this.nouveauRapportCandidaturesIrrecevables = v; }
    public String getNouveauRapportMotifsExclusion() { return nouveauRapportMotifsExclusion; }
    public void setNouveauRapportMotifsExclusion(String v) { this.nouveauRapportMotifsExclusion = v; }
    public String getNouveauRapportCompositionComite() { return nouveauRapportCompositionComite; }
    public void setNouveauRapportCompositionComite(String v) { this.nouveauRapportCompositionComite = v; }
    public String getNouveauRapportDifficultes() { return nouveauRapportDifficultes; }
    public void setNouveauRapportDifficultes(String v) { this.nouveauRapportDifficultes = v; }
    public String getNouveauRapportRecommandation() { return nouveauRapportRecommandation; }
    public void setNouveauRapportRecommandation(String v) { this.nouveauRapportRecommandation = v; }
    public boolean isDlgRapportOuvert() { return dlgRapportOuvert; }

    public List<PVDeliberation> getPvs() { return pvs; }
    public String getNouveauPvLibelle() { return nouveauPvLibelle; }
    public void setNouveauPvLibelle(String v) { this.nouveauPvLibelle = v; }
    public int getNouveauPvNombreMembres() { return nouveauPvNombreMembres; }
    public void setNouveauPvNombreMembres(int v) { this.nouveauPvNombreMembres = v; }
    public String getNouveauPvLieu() { return nouveauPvLieu; }
    public void setNouveauPvLieu(String v) { this.nouveauPvLieu = v; }
    public String getNouveauPvHeureDebut() { return nouveauPvHeureDebut; }
    public void setNouveauPvHeureDebut(String v) { this.nouveauPvHeureDebut = v; }
    public String getNouveauPvHeureCloture() { return nouveauPvHeureCloture; }
    public void setNouveauPvHeureCloture(String v) { this.nouveauPvHeureCloture = v; }
    public String getNouveauPvMembresJury() { return nouveauPvMembresJury; }
    public void setNouveauPvMembresJury(String v) { this.nouveauPvMembresJury = v; }
    public String getNouveauPvDeclarationConflitInteret() { return nouveauPvDeclarationConflitInteret; }
    public void setNouveauPvDeclarationConflitInteret(String v) { this.nouveauPvDeclarationConflitInteret = v; }
    public String getNouveauPvModeDecision() { return nouveauPvModeDecision; }
    public void setNouveauPvModeDecision(String v) { this.nouveauPvModeDecision = v; }
    public String getNouveauPvResultatVote() { return nouveauPvResultatVote; }
    public void setNouveauPvResultatVote(String v) { this.nouveauPvResultatVote = v; }
    public String getNouveauPvReserves() { return nouveauPvReserves; }
    public void setNouveauPvReserves(String v) { this.nouveauPvReserves = v; }
    public String getNouveauPvSuitesADonner() { return nouveauPvSuitesADonner; }
    public void setNouveauPvSuitesADonner(String v) { this.nouveauPvSuitesADonner = v; }
    public boolean isDlgPvOuvert() { return dlgPvOuvert; }

    public List<CandidatFinal> getCandidatsFinaux() { return candidatsFinaux; }

    /** Notes détaillées par membre du jury pour un candidat — sous-expansion d'une ligne du classement. */
    public List<NoteCandidatEvalElement> getNotesParCandidature(Long candidatureId) {
        if (candidatureId == null) return new ArrayList<>();
        return notesParCandidatureCache.computeIfAbsent(candidatureId, recrutementService::getNotesParCandidature);
    }
    public CandidatFinal getCandidatFinalSelectionne() { return candidatFinalSelectionne; }
    public String getNotesDecision() { return notesDecision; }

    /** Total des points cumulés sur toutes les phases clôturées — affiché partout à la place d'un score à une seule phase. */
    public Integer getPointsCumules(Candidature c) {
        if (c == null) return null;
        return pointsCumulesCache.computeIfAbsent(c.getId(), id -> recrutementService.getPointsCandidature(id));
    }

    public Integer getPointsMaxCumules(Candidature c) {
        if (c == null) return null;
        return pointsMaxCumulesCache.computeIfAbsent(c.getId(), id -> recrutementService.getPointsMaxCandidature(id));
    }
    public void setNotesDecision(String v) { this.notesDecision = v; }
}
