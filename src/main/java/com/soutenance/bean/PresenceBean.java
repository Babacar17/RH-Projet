package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.DemandeConge;
import com.soutenance.entity.Employe;
import com.soutenance.entity.PlanningPresence;
import com.soutenance.service.PresenceService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean JSF — Gestion des présences, absences et plannings.
 *
 * Fonctionnalités couvertes (point 5 du cahier des charges) :
 *  - Superviser et valider les demandes de congés (toutes institutions)
 *  - Suivi des absences, retards et heures supplémentaires
 *  - Gestion des plannings et rotations par unité organisationnelle
 *  - Vue calendrier des congés approuvés
 *  - Indicateurs : taux de présence, absentéisme, soldes congés
 *  - Configuration des quotas de congés par type et par institution
 *
 * Ce bean complète RHBean (qui gère déjà la validation individuelle) en
 * offrant une vue consolidée et un planning visuel multi-employés.
 * Il est accessible aux rôles RH, MANAGER et ADMIN.
 */
@Component("presenceBean")
@Scope("view")
public class PresenceBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(PresenceBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private PresenceService presenceService;
    @Autowired private SessionBean     sessionBean;

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<DemandeConge>    toutesDemandesConge;
    private List<DemandeConge>    demandesFiltrees;
    private List<DemandeConge>    demandesEnAttente;
    private List<Employe>         employes;
    private List<PlanningPresence> plannings;

    /** Vue calendrier : clé = date ISO, valeur = liste de congés ce jour */
    private Map<String, List<DemandeConge>> calendrierConges;

    // ── Entité en cours d'édition ────────────────────────────────────────────
    private DemandeConge    demandeConge  = new DemandeConge();
    private PlanningPresence planning     = new PlanningPresence();
    private String          commentaireRefus;

    // ── Période calendrier ───────────────────────────────────────────────────
    private LocalDate debutSemaine  = LocalDate.now().with(
        java.time.DayOfWeek.MONDAY);
    private LocalDate debutMois     = LocalDate.now().withDayOfMonth(1);
    private String    vuCalendrier  = "MOIS"; // SEMAINE | MOIS

    // ── Filtres ──────────────────────────────────────────────────────────────
    private String    filtreStatut;
    private String    filtreTypeConge;
    private Long      filtreEmployeId;
    private LocalDate filtreDateDebut;
    private LocalDate filtreDateFin;

    // ── Statistiques ─────────────────────────────────────────────────────────
    private long   totalDemandes;
    private long   demandesApprouvees;
    private long   demandesRefusees;
    private long   employesEnCongeAujourdhui;
    private long   totalEmployes;
    private double tauxPresenceAujourdhui;
    private double moyenneJoursConge;

    @PostConstruct
    public void init() {
        try {
            filtreDateDebut = debutMois;
            filtreDateFin   = debutMois.plusMonths(1).minusDays(1);
            chargerTout();
        } catch (Exception e) {
            System.err.println("PresenceBean init error: " + e.getMessage());
            toutesDemandesConge = new ArrayList<>();
            demandesFiltrees    = new ArrayList<>();
            demandesEnAttente   = new ArrayList<>();
            employes            = new ArrayList<>();
            plannings           = new ArrayList<>();
        }
    }

    private void chargerTout() {
        Long institutionId = sessionBean.getInstitutionId();

        toutesDemandesConge     = presenceService.getToutesDemandesConge(institutionId);
        demandesEnAttente       = presenceService.getDemandesEnAttente(institutionId);
        employes                = presenceService.getTousEmployes(institutionId);
        plannings               = presenceService.getPlannings(institutionId);
        calendrierConges        = presenceService.getCalendrierConges(
            debutMois, debutMois.plusMonths(1).minusDays(1), institutionId);

        appliquerFiltres();
        calculerStatistiques(institutionId);
    }

    private void calculerStatistiques(Long institutionId) {
        totalDemandes        = toutesDemandesConge.size();
        demandesApprouvees   = toutesDemandesConge.stream()
            .filter(d -> "APPROUVE".equals(d.getStatut())).count();
        demandesRefusees     = toutesDemandesConge.stream()
            .filter(d -> "REFUSE".equals(d.getStatut())).count();
        totalEmployes        = employes.size();
        employesEnCongeAujourdhui = presenceService
            .countEmployesEnCongeAujourdhui(institutionId);
        tauxPresenceAujourdhui = totalEmployes > 0
            ? (double)(totalEmployes - employesEnCongeAujourdhui) / totalEmployes * 100 : 100;
        moyenneJoursConge    = presenceService.getMoyenneJoursConge(institutionId);
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // VALIDATION DES CONGÉS
    // ════════════════════════════════════════════════════════════

    public void preparerValidation(DemandeConge d) {
        this.demandeConge   = d;
        this.commentaireRefus = "";
    }

    /**
     * Approuve une demande de congé avec vérification du solde restant.
     */
    public void approuverConge(Long id) {
        try {
            // Vérification du solde avant approbation
            DemandeConge d = presenceService.getDemandeById(id);
            if (d != null && d.getEmploye() != null) {
                int solde = presenceService.getSoldeConges(d.getEmploye().getId());
                int jours = d.getNombreJours() != null ? d.getNombreJours() : 0;
                if (jours > solde) {
                    addMsg(FacesMessage.SEVERITY_WARN, "Solde insuffisant",
                        d.getEmploye().getNomComplet() + " n'a que " + solde
                        + " jours disponibles (demandé : " + jours + " jours).");
                    // On laisse la décision à l'opérateur : on affiche juste le warning
                }
            }
            presenceService.approuverConge(id, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Congé approuvé",
                "La demande a été approuvée avec succès.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /**
     * Refuse une demande de congé avec motif obligatoire.
     */
    public void refuserConge(Long id) {
        if (commentaireRefus == null || commentaireRefus.isBlank()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Motif requis",
                "Veuillez saisir un motif de refus.");
            return;
        }
        try {
            presenceService.refuserConge(id, commentaireRefus, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Congé refusé",
                "La demande a été refusée.");
            commentaireRefus = "";
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /**
     * Annule un congé déjà approuvé (retour au solde de l'employé).
     */
    public void annulerCongeApprouve(Long id) {
        try {
            presenceService.annulerCongeApprouve(id, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Congé annulé",
                "Le congé approuvé a été annulé et le solde restitué.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // PLANNING & ROTATIONS
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauPlanning() {
        planning = new PlanningPresence();
        planning.setDateDebut(LocalDate.now());
        planning.setDateFin(LocalDate.now().plusDays(6));
    }

    public void preparerEditionPlanning(PlanningPresence p) {
        this.planning = p;
    }

    public String sauvegarderPlanning() {
        try {
            if (planning.getDateDebut() == null || planning.getDateFin() == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Les dates de début et de fin sont obligatoires.");
                return null;
            }
            if (planning.getDateFin().isBefore(planning.getDateDebut())) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "La date de fin doit être après la date de début.");
                return null;
            }
            if (planning.getId() == null) {
                presenceService.creerPlanning(planning, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Planning créé",
                    "Le planning a été enregistré.");
            } else {
                presenceService.modifierPlanning(planning, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Planning modifié",
                    "Le planning a été mis à jour.");
            }
            plannings = presenceService.getPlannings(sessionBean.getInstitutionId());
            planning  = new PlanningPresence();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimerPlanning(Long id) {
        try {
            presenceService.supprimerPlanning(id, getOp());
            plannings = presenceService.getPlannings(sessionBean.getInstitutionId());
            addMsg(FacesMessage.SEVERITY_WARN, "Planning supprimé",
                "Le planning a été supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // CALENDRIER & NAVIGATION
    // ════════════════════════════════════════════════════════════

    /** Passe au mois précédent dans le calendrier. */
    public void moisPrecedent() {
        debutMois = debutMois.minusMonths(1);
        actualiserCalendrier();
    }

    /** Passe au mois suivant dans le calendrier. */
    public void moisSuivant() {
        debutMois = debutMois.plusMonths(1);
        actualiserCalendrier();
    }

    /** Revient au mois en cours. */
    public void moisActuel() {
        debutMois = LocalDate.now().withDayOfMonth(1);
        actualiserCalendrier();
    }

    private void actualiserCalendrier() {
        LocalDate fin = debutMois.plusMonths(1).minusDays(1);
        calendrierConges = presenceService.getCalendrierConges(
            debutMois, fin, sessionBean.getInstitutionId());
    }

    /** Retourne le libellé du mois/année affiché dans le calendrier. */
    public String getLabelMoisCalendrier() {
        return debutMois.format(DateTimeFormatter.ofPattern("MMMM yyyy",
            java.util.Locale.FRENCH));
    }

    /** Retourne les congés pour un jour donné (utilisé dans le calendrier XHTML). */
    public List<DemandeConge> getCongesDuJour(LocalDate date) {
        if (calendrierConges == null || date == null) return new ArrayList<>();
        String cle = date.toString();
        return calendrierConges.getOrDefault(cle, new ArrayList<>());
    }

    /** Retourne la liste des jours du mois courant pour itération dans le template. */
    public List<LocalDate> getJoursDuMois() {
        List<LocalDate> jours = new ArrayList<>();
        LocalDate fin = debutMois.plusMonths(1).minusDays(1);
        LocalDate jour = debutMois;
        while (!jour.isAfter(fin)) {
            jours.add(jour);
            jour = jour.plusDays(1);
        }
        return jours;
    }

    // ════════════════════════════════════════════════════════════
    // FILTRAGE
    // ════════════════════════════════════════════════════════════

    public void appliquerFiltres() {
        if (toutesDemandesConge == null) { demandesFiltrees = new ArrayList<>(); return; }
        demandesFiltrees = toutesDemandesConge.stream()
            .filter(d -> {
                if (filtreStatut != null && !filtreStatut.isBlank()
                    && !filtreStatut.equals(d.getStatut())) return false;
                if (filtreTypeConge != null && !filtreTypeConge.isBlank()
                    && !filtreTypeConge.equals(d.getTypeConge())) return false;
                if (filtreEmployeId != null
                    && (d.getEmploye() == null || !filtreEmployeId.equals(d.getEmploye().getId())))
                    return false;
                if (filtreDateDebut != null && d.getDateDebut() != null
                    && d.getDateDebut().isBefore(filtreDateDebut)) return false;
                if (filtreDateFin != null && d.getDateFin() != null
                    && d.getDateFin().isAfter(filtreDateFin)) return false;
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    public void reinitialiserFiltres() {
        filtreStatut    = null;
        filtreTypeConge = null;
        filtreEmployeId = null;
        filtreDateDebut = debutMois;
        filtreDateFin   = debutMois.plusMonths(1).minusDays(1);
        demandesFiltrees = toutesDemandesConge;
    }

    // ════════════════════════════════════════════════════════════
    // SOLDES & HELPERS
    // ════════════════════════════════════════════════════════════

    /** Retourne le solde de congés restant d'un employé. */
    public int getSoldeConges(Long employeId) {
        try { return presenceService.getSoldeConges(employeId); }
        catch (Exception e) { return 0; }
    }

    /** Vérifie si un employé est en congé un jour donné. */
    public boolean isEnConge(Long employeId, LocalDate date) {
        List<DemandeConge> congesDuJour = getCongesDuJour(date);
        return congesDuJour.stream()
            .anyMatch(d -> d.getEmploye() != null
                && employeId.equals(d.getEmploye().getId())
                && "APPROUVE".equals(d.getStatut()));
    }

    /** Classe CSS du badge statut de demande de congé. */
    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "EN_ATTENTE" -> "badge-warning";
            case "APPROUVE"   -> "badge-success";
            case "REFUSE"     -> "badge-danger";
            case "ANNULE"     -> "badge-secondary";
            default           -> "badge-secondary";
        };
    }

    /** Libellé du statut de demande de congé. */
    public String getLabelStatut(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "EN_ATTENTE" -> "En attente";
            case "APPROUVE"   -> "Approuvé";
            case "REFUSE"     -> "Refusé";
            case "ANNULE"     -> "Annulé";
            default           -> statut;
        };
    }

    /** Couleur CSS du taux de présence (jauge). */
    public String getCouleurPresence() {
        if (tauxPresenceAujourdhui >= 90) return "text-success";
        if (tauxPresenceAujourdhui >= 70) return "text-warning";
        return "text-danger";
    }

    public String formatPourcentage(double valeur) {
        return String.format("%.1f%%", valeur);
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<DemandeConge>    getToutesDemandesConge()   { return toutesDemandesConge; }
    public List<DemandeConge>    getDemandesFiltrees()      { return demandesFiltrees; }
    public List<DemandeConge>    getDemandesEnAttente()     { return demandesEnAttente; }
    public List<Employe>         getEmployes()              { return employes; }
    public List<PlanningPresence> getPlannings()            { return plannings; }
    public Map<String, List<DemandeConge>> getCalendrierConges() { return calendrierConges; }

    public DemandeConge     getDemandeConge()               { return demandeConge; }
    public void setDemandeConge(DemandeConge d)             { this.demandeConge = d; }
    public PlanningPresence getPlanning()                   { return planning; }
    public void setPlanning(PlanningPresence p)             { this.planning = p; }

    public String  getCommentaireRefus()                    { return commentaireRefus; }
    public void    setCommentaireRefus(String s)            { this.commentaireRefus = s; }

    public LocalDate getDebutMois()                         { return debutMois; }
    public void      setDebutMois(LocalDate d)              { this.debutMois = d; }
    public LocalDate getDebutSemaine()                      { return debutSemaine; }
    public String    getVuCalendrier()                      { return vuCalendrier; }
    public void      setVuCalendrier(String v)              { this.vuCalendrier = v; }

    public String    getFiltreStatut()                      { return filtreStatut; }
    public void      setFiltreStatut(String s)              { this.filtreStatut = s; }
    public String    getFiltreTypeConge()                   { return filtreTypeConge; }
    public void      setFiltreTypeConge(String s)           { this.filtreTypeConge = s; }
    public Long      getFiltreEmployeId()                   { return filtreEmployeId; }
    public void      setFiltreEmployeId(Long id)            { this.filtreEmployeId = id; }
    public LocalDate getFiltreDateDebut()                   { return filtreDateDebut; }
    public void      setFiltreDateDebut(LocalDate d)        { this.filtreDateDebut = d; }
    public LocalDate getFiltreDateFin()                     { return filtreDateFin; }
    public void      setFiltreDateFin(LocalDate d)          { this.filtreDateFin = d; }

    public long   getTotalDemandes()                        { return totalDemandes; }
    public long   getDemandesApprouvees()                   { return demandesApprouvees; }
    public long   getDemandesRefusees()                     { return demandesRefusees; }
    public long   getDemandesEnAttenteCount()               { return demandesEnAttente != null ? demandesEnAttente.size() : 0; }
    public long   getEmployesEnCongeAujourdhui()            { return employesEnCongeAujourdhui; }
    public long   getTotalEmployes()                        { return totalEmployes; }
    public double getTauxPresenceAujourdhui()               { return tauxPresenceAujourdhui; }
    public double getMoyenneJoursConge()                    { return moyenneJoursConge; }
}