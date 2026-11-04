package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.ManagerService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component("managerBean")
@Scope("view")
public class ManagerBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private ManagerService managerService;
    @Autowired private SessionBean    sessionBean;
    @Autowired(required = false) private com.soutenance.service.RecrutementService recrutementService;

    // ── Listes ─────────────────────────────────────────────────
    private List<Employe>          monEquipe;
    private List<Employe>          chefsDivision;
    private List<Employe>          chefsService;
    private List<Employe>          chefsBureau;
    private List<Employe>          employesStandards;
    private List<DemandeConge>     congesEquipe;
    private List<Evaluation>       evaluationsEquipe;
    private List<MouvementCarriere> mouvementsEquipe;
    private List<ComiteEvaluateur> mesJurys;

    // ── Entités en cours d'édition ─────────────────────────────
    private DemandeConge     demandeConge  = new DemandeConge();
    private Evaluation       evaluation    = new Evaluation();
    private MouvementCarriere mouvement    = new MouvementCarriere();
    private Employe          employeSelectionne;

    // ── UI State ───────────────────────────────────────────────
    private boolean modeEdition     = false;
    private String  commentaireRefus = "";

    // ── Stats ──────────────────────────────────────────────────
    private long totalEquipe;
    private long presentsAujourdhui;
    private long absentsAujourdhui;
    private long congesEnAttente;
    private long evaluationsEnCours;

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("=== MANAGER BEAN INIT ERROR : " + e.getMessage());
            LOG.error("Erreur inattendue", e);
            // Initialiser des listes vides pour éviter la page blanche
            monEquipe         = new java.util.ArrayList<>();
            chefsDivision     = new java.util.ArrayList<>();
            chefsService      = new java.util.ArrayList<>();
            chefsBureau       = new java.util.ArrayList<>();
            employesStandards = new java.util.ArrayList<>();
            congesEquipe      = new java.util.ArrayList<>();
            evaluationsEquipe = new java.util.ArrayList<>();
            mouvementsEquipe  = new java.util.ArrayList<>();
            mesJurys          = new java.util.ArrayList<>();
        }
    }

    private void chargerTout() {
        String op = getOp();
        monEquipe          = managerService.getMonEquipe(op);
        chefsDivision      = managerService.getChefsDivision(op);
        chefsService       = managerService.getChefsService(op);
        chefsBureau        = managerService.getChefsBureau(op);
        employesStandards  = managerService.getEmployesStandards(op);
        congesEquipe       = managerService.getCongesEquipe(op);
        evaluationsEquipe  = managerService.getEvaluationsEquipe(op);
        mouvementsEquipe   = managerService.getMouvementsEquipe(op);

        Employe moi = managerService.getManagerEmploye(op);
        mesJurys = (moi != null && recrutementService != null)
            ? recrutementService.getMembresJuryDeEmploye(moi.getId())
            : new java.util.ArrayList<>();
        if (mesJurys == null) mesJurys = new java.util.ArrayList<>();
        // Stats
        totalEquipe        = managerService.countEquipe(op);
        presentsAujourdhui = managerService.countPresentsAujourdhui(op);
        absentsAujourdhui  = managerService.countAbsentsAujourdhui(op);
        congesEnAttente    = managerService.countCongesEnAttente(op);
        evaluationsEnCours = managerService.countEvaluationsEnCours(op);
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "manager";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    // ══════════════════════════════════════════════════
    // CONGÉS
    // ══════════════════════════════════════════════════

    public void preparerEditionConge(DemandeConge d) {
        this.demandeConge     = d;
        this.commentaireRefus = "";
    }

    public void approuverConge(Long id) {
        try {
            managerService.approuverConge(id, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Conge approuve", "Demande approuvee avec succes.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void refuserConge(Long id) {
        try {
            managerService.refuserConge(id, commentaireRefus, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Conge refuse", "Demande refusee.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════
    // ÉVALUATIONS
    // ══════════════════════════════════════════════════

    public void preparerNouvelleEvaluation(Employe e) {
        evaluation = new Evaluation();
        evaluation.setEmploye(e);
        modeEdition = false;
    }

    public void preparerEditionEvaluation(Evaluation ev) {
        this.evaluation = ev;
        modeEdition = true;
    }

    public String sauvegarderEvaluation() {
        try {
            managerService.sauvegarderEvaluation(evaluation, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Succes", "Evaluation sauvegardee.");
            evaluation = new Evaluation();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void soumettreEvaluation(Long id) {
        try {
            // Récupérer l'évaluation et la soumettre
            Evaluation ev = evaluationsEquipe.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst().orElse(null);
            if (ev != null) {
                managerService.soumettreEvaluation(ev, getOp());
                chargerTout();
                addMsg(FacesMessage.SEVERITY_INFO, "Evaluation soumise", "Transmise au RH.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // RACCOURCIS MANAGER : réunion d'équipe, demande formation, recrutement
    // ════════════════════════════════════════════════════════════

    @Autowired(required = false) private com.soutenance.service.EvenementService evenementService;
    @Autowired(required = false) private com.soutenance.service.RHService        rhService;
    @Autowired(required = false) private com.soutenance.dao.NotificationDao      notificationDao;
    @Autowired(required = false) private com.soutenance.dao.UserDao              userDao;

    // ── Réunion d'équipe rapide ─────────────────────────────────────────────
    private com.soutenance.entity.Evenement reunion = new com.soutenance.entity.Evenement();

    public com.soutenance.entity.Evenement getReunion()        { return reunion; }
    public void setReunion(com.soutenance.entity.Evenement r)  { this.reunion = r; }

    public void preparerReunionEquipe() {
        reunion = new com.soutenance.entity.Evenement();
        reunion.setType("REUNION");
        reunion.setStatut("PROGRAMME");
        reunion.setModalite("PRESENTIEL");
        reunion.setTitre("Réunion d'équipe");
        java.time.LocalDateTime demain = java.time.LocalDateTime.now().plusDays(1)
            .withHour(10).withMinute(0).withSecond(0).withNano(0);
        reunion.setDateDebut(demain);
        reunion.setDateFin(demain.plusHours(1));
    }

    public void creerReunionEquipe() {
        if (evenementService == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Service événements indisponible.");
            return;
        }
        try {
            if (reunion.getInstitution() == null) {
                reunion.setInstitution(sessionBean.getInstitutionCourante());
            }
            evenementService.creer(reunion, getOp());
            int invites = 0;
            for (Employe e : monEquipe) {
                if ("ACTIF".equalsIgnoreCase(e.getStatut())) {
                    try {
                        evenementService.inviter(reunion.getId(), e.getId(), getOp());
                        invites++;
                    } catch (Exception ignored) {}
                }
            }
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Réunion créée",
                "Événement créé et " + invites + " membre(s) invité(s).");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Demande de formation pour un membre ──────────────────────────────────
    private Long       formationDemandeeId;
    private Employe    membreCiblePourFormation;
    private String     justificationFormation;

    public Long    getFormationDemandeeId()              { return formationDemandeeId; }
    public void    setFormationDemandeeId(Long id)       { this.formationDemandeeId = id; }
    public Employe getMembreCiblePourFormation()         { return membreCiblePourFormation; }
    public String  getJustificationFormation()           { return justificationFormation; }
    public void    setJustificationFormation(String j)   { this.justificationFormation = j; }

    /** Binding par id depuis la dialog (résout vers l'Employe complet). */
    public Long getMembreCibleFormationId() {
        return membreCiblePourFormation != null ? membreCiblePourFormation.getId() : null;
    }
    public void setMembreCibleFormationId(Long id) {
        if (id == null) { this.membreCiblePourFormation = null; return; }
        this.membreCiblePourFormation = monEquipe.stream()
            .filter(e -> id.equals(e.getId()))
            .findFirst().orElse(null);
    }

    public java.util.List<com.soutenance.entity.Formation> getFormationsDisponibles() {
        if (rhService == null) return java.util.Collections.emptyList();
        try {
            return rhService.getToutesFormations().stream()
                .filter(f -> "PLANIFIEE".equalsIgnoreCase(f.getStatut())
                          || "EN_COURS".equalsIgnoreCase(f.getStatut()))
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public void preparerDemandeFormation(Employe e) {
        this.membreCiblePourFormation = e;
        this.formationDemandeeId = null;
        this.justificationFormation = null;
    }

    public void envoyerDemandeFormation() {
        if (formationDemandeeId == null || membreCiblePourFormation == null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Sélection requise",
                "Choisissez une formation et un employé.");
            return;
        }
        try {
            if (rhService != null) {
                rhService.inscrireEmploye(formationDemandeeId,
                    membreCiblePourFormation.getId(), getOp());
            }
            notifierRolesRH("Demande de formation",
                "Le manager " + getOp() + " propose d'inscrire "
                + membreCiblePourFormation.getNomComplet()
                + " à une formation. Justification : "
                + (justificationFormation != null ? justificationFormation : "non précisée"));
            addMsg(FacesMessage.SEVERITY_INFO, "Demande envoyée",
                membreCiblePourFormation.getNomComplet() + " a été inscrit(e) à la formation. "
                + "Le RH a été notifié.");
            this.formationDemandeeId = null;
            this.justificationFormation = null;
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Demande de recrutement ──────────────────────────────────────────────
    private String posteRecherche;
    private String profilRecherche;
    private String motifRecrutement;

    public String getPosteRecherche()                { return posteRecherche; }
    public void   setPosteRecherche(String p)        { this.posteRecherche = p; }
    public String getProfilRecherche()               { return profilRecherche; }
    public void   setProfilRecherche(String p)       { this.profilRecherche = p; }
    public String getMotifRecrutement()              { return motifRecrutement; }
    public void   setMotifRecrutement(String m)      { this.motifRecrutement = m; }

    public void envoyerDemandeRecrutement() {
        if (posteRecherche == null || posteRecherche.isBlank()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Champ requis",
                "Précisez le poste recherché.");
            return;
        }
        try {
            String message = "Demande de recrutement émise par " + getOp()
                + ".\nPoste : " + posteRecherche
                + ".\nProfil recherché : "
                + (profilRecherche != null ? profilRecherche : "non précisé")
                + ".\nMotif : "
                + (motifRecrutement != null ? motifRecrutement : "non précisé");
            notifierRolesRH("Nouvelle demande de recrutement", message);
            addMsg(FacesMessage.SEVERITY_INFO, "Demande transmise au RH",
                "Le service RH va étudier votre demande et publier l'offre si elle est validée.");
            posteRecherche = null;
            profilRecherche = null;
            motifRecrutement = null;
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Calendrier équipe (congés + événements) ─────────────────────────────

    /** Tous les congés de tous les membres de l'équipe. */
    public java.util.List<com.soutenance.entity.DemandeConge> getCongesEquipeComplets() {
        return congesEquipe != null ? congesEquipe : java.util.Collections.emptyList();
    }

    /** Tous les événements auxquels au moins un membre de l'équipe est invité. */
    public java.util.List<com.soutenance.entity.Evenement> getEvenementsEquipe() {
        if (evenementService == null || monEquipe == null) return java.util.Collections.emptyList();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        java.util.List<com.soutenance.entity.Evenement> tous = new java.util.ArrayList<>();
        for (Employe m : monEquipe) {
            try {
                java.util.List<com.soutenance.entity.Evenement> evs =
                    evenementService.getByParticipant(m.getId());
                for (com.soutenance.entity.Evenement ev : evs) {
                    if (ev.getId() != null && ids.add(ev.getId())) {
                        tous.add(ev);
                    }
                }
            } catch (Exception ignored) {}
        }
        return tous;
    }

    /** Envoie une notification à tous les RH de l'institution du manager. */
    private void notifierRolesRH(String titre, String message) {
        if (notificationDao == null || userDao == null) return;
        try {
            Long instId = sessionBean.getInstitutionId();
            java.util.List<com.soutenance.entity.User> tousUsers = userDao.findAll();
            for (com.soutenance.entity.User u : tousUsers) {
                if (!u.isActif()) continue;
                boolean estRH = u.getRoles() != null
                    && u.getRoles().stream().anyMatch(r -> "RH".equals(r.getNom()));
                if (!estRH) continue;
                if (instId != null && u.getInstitution() != null
                        && !instId.equals(u.getInstitution().getId())) continue;
                com.soutenance.entity.Notification n = new com.soutenance.entity.Notification();
                n.setTitre(titre);
                n.setMessage(message);
                n.setType("RECRUTEMENT_CANDIDATURE");
                n.setPriorite("HAUTE");
                n.setDestinataire(u);
                notificationDao.save(n);
            }
        } catch (Exception ignored) { /* best effort */ }
    }

    // ══════════════════════════════════════════════════
    // MUTATIONS / PROMOTIONS
    // ══════════════════════════════════════════════════

    public void preparerNouveauMouvement(Employe e) {
        mouvement = new MouvementCarriere();
        mouvement.setEmploye(e);
        mouvement.setAncienPoste(e.getIntiutePoste());
        mouvement.setAncienDepartement(e.getNomDirection());
        mouvement.setAncienSalaire(e.getSalaireBase());
        mouvement.setAncienGrade(e.getGradeEchelon());
        modeEdition = false;
    }

    public String soumettreMouvement() {
        try {
            managerService.soumettreMouvement(mouvement, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Demande soumise",
                "La demande a ete transmise au service RH.");
            mouvement = new MouvementCarriere();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void selectionnerEmploye(Employe e) {
        this.employeSelectionne = e;
    }

    // ══════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════

    public List<Employe>           getMonEquipe()         { return monEquipe; }
    public List<Employe>           getChefsDivision()     { return chefsDivision; }
    public List<Employe>           getChefsService()      { return chefsService; }
    public List<Employe>           getChefsBureau()       { return chefsBureau; }
    public List<Employe>           getEmployesStandards() { return employesStandards; }

    /** Niveau hiérarchique du manager connecté */
    public String getNiveauManager() {
        Employe mgr = managerService.getManagerEmploye(getOp());
        return mgr != null ? mgr.getLabelNiveau() : "Manager";
    }
    public List<DemandeConge>      getCongesEquipe()      { return congesEquipe; }
    public List<Evaluation>        getEvaluationsEquipe() { return evaluationsEquipe; }
    public List<MouvementCarriere> getMouvementsEquipe()  { return mouvementsEquipe; }
    public List<ComiteEvaluateur>  getMesJurys()          { return mesJurys; }

    public List<DemandeConge> getCongesEnAttenteEquipe() {
        if (congesEquipe == null) return java.util.Collections.emptyList();
        return congesEquipe.stream()
            .filter(d -> "EN_ATTENTE".equals(d.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    public DemandeConge      getDemandeConge()          { return demandeConge; }
    public void              setDemandeConge(DemandeConge d) { this.demandeConge = d; }
    public Evaluation        getEvaluation()            { return evaluation; }
    public void              setEvaluation(Evaluation e) { this.evaluation = e; }
    public MouvementCarriere getMouvement()             { return mouvement; }
    public void              setMouvement(MouvementCarriere m) { this.mouvement = m; }
    public Employe           getEmployeSelectionne()   { return employeSelectionne; }
    public void              setEmployeSelectionne(Employe e) { this.employeSelectionne = e; }

    public boolean isModeEdition()                { return modeEdition; }
    public void    setModeEdition(boolean b)      { this.modeEdition = b; }
    public String  getCommentaireRefus()          { return commentaireRefus; }
    public void    setCommentaireRefus(String s)  { this.commentaireRefus = s; }

    public long getTotalEquipe()        { return totalEquipe; }
    public long getPresentsAujourdhui() { return presentsAujourdhui; }
    public long getAbsentsAujourdhui()  { return absentsAujourdhui; }
    public long getCongesEnAttente()    { return congesEnAttente; }
    public long getEvaluationsEnCours() { return evaluationsEnCours; }

    // ── Helpers planning.xhtml ─────────────────────────────────

    /** Taux de présence en pourcentage (évite division par zéro) */
    public long getTauxPresence() {
        if (totalEquipe == 0) return 0;
        return presentsAujourdhui * 100 / totalEquipe;
    }

    /** Nombre de congés approuvés */
    public long getNbCongesApprouves() {
        if (congesEquipe == null) return 0;
        return congesEquipe.stream()
            .filter(d -> "APPROUVE".equals(d.getStatut()))
            .count();
    }

    /** Liste des congés approuvés uniquement */
    public List<DemandeConge> getCongesApprouves() {
        if (congesEquipe == null) return new java.util.ArrayList<>();
        return congesEquipe.stream()
            .filter(d -> "APPROUVE".equals(d.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    /** Vérifie si un employé est en congé approuvé */
    public boolean isEnConge(Long employeId) {
        if (congesEquipe == null || employeId == null) return false;
        return congesEquipe.stream()
            .anyMatch(d -> "APPROUVE".equals(d.getStatut())
                && d.getEmploye() != null
                && employeId.equals(d.getEmploye().getId()));
    }

    /** Nombre de jours pris (congés approuvés) pour un employé */
    public long getJoursPrisParEmploye(Long employeId) {
        if (congesEquipe == null || employeId == null) return 0;
        return congesEquipe.stream()
            .filter(d -> "APPROUVE".equals(d.getStatut())
                && d.getEmploye() != null
                && employeId.equals(d.getEmploye().getId())
                && d.getNombreJours() != null)
            .mapToLong(d -> d.getNombreJours())
            .sum();
    }

    /** Taux d'absence pour la barre (sur 30 jours max) */
    public long getTauxAbsenceEmploye(Long employeId) {
        long jours = getJoursPrisParEmploye(employeId);
        return Math.min(jours * 100 / 30, 100);
    }
}