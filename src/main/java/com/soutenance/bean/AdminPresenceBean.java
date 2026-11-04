package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.AdminPresenceService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean de gestion des présences et absences — périmètre administrateur.
 *
 * Couvre :
 *  - Pointage manuel (création/modification de présences)
 *  - Supervision des absences et retards
 *  - Validation des feuilles de présence
 *  - Statistiques de présence (taux, retards, heures supplémentaires)
 *  - Gestion des jours fériés par institution / région
 *  - Export CSV des données de présence
 */
@Component("adminPresenceBean")
@Scope("view")
public class AdminPresenceBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPresenceBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminPresenceService    presenceService;
    @Autowired private com.soutenance.dao.DemandeCongeDao demandeCongeDao;
    @Autowired private com.soutenance.dao.TypeCongeDao    typeCongeDao;
    @Autowired private SessionBean          sessionBean;
    @Autowired(required = false)
    private com.soutenance.bean.AdminBean adminBean;

    private Long getInstId() {
        if (adminBean != null && adminBean.getInstitutionFiltreId() != null)
            return adminBean.getInstitutionFiltreId();
        return sessionBean.getInstitutionId();
    }

    // ── Présences ─────────────────────────────────────────────────────────
    private List<Presence>   presences         = new ArrayList<>();
    private List<Presence>   presencesFiltrees = new ArrayList<>();
    private Presence         presence          = new Presence();
    private Presence         presenceSelectionnee;
    private boolean          modeEditionPresence = false;
    private Long             employeSelectionneId;
    private Long             typeCongeSelectionneId;
    private java.util.List<com.soutenance.entity.TypeConge> typesConge = new java.util.ArrayList<>();

    // ── Jours fériés ──────────────────────────────────────────────────────
    private List<JourFerie>  joursFeries        = new ArrayList<>();
    private JourFerie        jourFerie          = new JourFerie();
    private boolean          modeEditionFerie   = false;

    // ── Filtres présences ─────────────────────────────────────────────────
    private LocalDate        filtreDateDebut;
    private LocalDate        filtreDateFin;
    private String           filtreTypePresence;
    private Long             filtreEmployeId;
    private boolean          filtreNonValides   = false;

    // ── Stats du jour ─────────────────────────────────────────────────────
    private long presents;
    private long absents;
    private long retards;
    private long enConge;
    private long heuresSupTotales;

    // ── Sélection employé pour pointage ──────────────────────────────────
    private List<Employe>    employes           = new ArrayList<>();
    private Long             employePointageId;

    @PostConstruct
    public void init() {
        filtreDateDebut = LocalDate.now().withDayOfMonth(1);
        filtreDateFin   = LocalDate.now();
        chargerTout();
    }

    private void chargerTout() {
        try {
            Long instId = getInstId();
            presences    = presenceService.getPresencesParPeriode(instId, filtreDateDebut, filtreDateFin);
            presencesFiltrees = new ArrayList<>(presences);
            joursFeries  = presenceService.getJoursFeriesByInstitution(instId);
            employes     = presenceService.getEmployesActifs(instId);
            typesConge   = (typeCongeDao != null)
                    ? typeCongeDao.findActifsByInstitution(instId)
                    : new java.util.ArrayList<>();
            calculerStats();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            presences         = new ArrayList<>();
            presencesFiltrees = new ArrayList<>();
            joursFeries       = new ArrayList<>();
        }
    }

    private void calculerStats() {
        LocalDate aujourd = LocalDate.now();
        List<Presence> presencesAujourd = presences.stream()
            .filter(p -> aujourd.equals(p.getDatePresence()))
            .collect(Collectors.toList());

        presents = presencesAujourd.stream()
            .filter(p -> "PRESENT".equals(p.getType()) || "TELETRAVAIL".equals(p.getType())).count();
        absents  = presencesAujourd.stream()
            .filter(p -> "ABSENT".equals(p.getType())).count();
        enConge  = presencesAujourd.stream()
            .filter(p -> "CONGE".equals(p.getType())).count();
        retards  = presencesAujourd.stream()
            .filter(p -> p.getRetardMinutes() != null && p.getRetardMinutes() > 0).count();
        heuresSupTotales = (long) presences.stream()
            .filter(p -> p.getHeuresSupplementaires() != null)
            .mapToDouble(Presence::getHeuresSupplementaires)
            .sum();
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    // ══════════════════════════════════════════════════════════
    // POINTAGE MANUEL
    // ══════════════════════════════════════════════════════════

    public void preparerNouvellePresence() {
        presence = new Presence();
        presence.setDatePresence(LocalDate.now());
        employePointageId  = null;
        modeEditionPresence = false;
    }

    public void preparerEditionPresence(Presence p) {
        this.presence = presenceService.getPresenceById(p.getId());
        if (this.presence == null) this.presence = p;
        employePointageId   = (p.getEmploye() != null) ? p.getEmploye().getId() : null;
        modeEditionPresence = true;
    }

    public String sauvegarderPresence() {
        try {
            if (employePointageId == null) {
                addError("Sélectionnez un employé.");
                return null;
            }
            if (presence.getDatePresence() == null) {
                addError("La date est obligatoire.");
                return null;
            }

            Employe emp = trouverEmploye(employePointageId);
            if (emp == null) {
                addError("Employé introuvable.");
                return null;
            }
            presence.setEmploye(emp);
            presence.setSourceSaisie("MANUEL");

            if (modeEditionPresence) {
                presenceService.modifierPresence(presence, getOp());
                addInfo("Présence modifiée.");
            } else {
                String erreur = presenceService.enregistrerPresence(presence, getOp());
                if (erreur != null) {
                    addError(erreur);
                    return null;
                }
                addInfo("Présence enregistrée.");
            }
            chargerTout();
            presence = new Presence();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void validerPresence(Long id) {
        try {
            presenceService.validerPresence(id, getOp());
            chargerTout();
            addInfo("Présence validée.");
        } catch (Exception ex) {
            addError("Erreur validation : " + ex.getMessage());
        }
    }

    public void validerToutesPresencesJour(LocalDate date) {
        try {
            Long instId = getInstId();
            int nb = presenceService.validerPresencesJour(instId, date, getOp());
            chargerTout();
            addInfo(nb + " présence(s) validée(s) pour le " + date);
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
    }

    public void supprimerPresence(Long id) {
        try {
            presenceService.supprimerPresence(id, getOp());
            chargerTout();
            addInfo("Présence supprimée.");
        } catch (Exception ex) {
            addError("Erreur suppression : " + ex.getMessage());
        }
    }

    public void selectionnerPresence(Presence p) {
        this.presenceSelectionnee = p;
    }

    // ══════════════════════════════════════════════════════════
    // FILTRES PRÉSENCES
    // ══════════════════════════════════════════════════════════

    public void filtrerPresences() {
        try {
            Long instId = getInstId();
            presences = presenceService.getPresencesParPeriode(instId, filtreDateDebut, filtreDateFin);

            presencesFiltrees = presences.stream()
                .filter(p -> {
                    if (filtreTypePresence != null && !filtreTypePresence.isBlank()
                            && !filtreTypePresence.equals(p.getType())) return false;
                    if (filtreEmployeId != null
                            && (p.getEmploye() == null || !filtreEmployeId.equals(p.getEmploye().getId())))
                        return false;
                    if (filtreNonValides && p.isEstValide()) return false;
                    return true;
                })
                .collect(Collectors.toList());

            calculerStats();
        } catch (Exception ex) {
            addError("Erreur filtre : " + ex.getMessage());
        }
    }

    public void reinitialiserFiltres() {
        filtreDateDebut    = LocalDate.now().withDayOfMonth(1);
        filtreDateFin      = LocalDate.now();
        filtreTypePresence = null;
        filtreEmployeId    = null;
        filtreNonValides   = false;
        chargerTout();
    }

    public void filtrerMoisCourant() {
        filtreDateDebut = LocalDate.now().withDayOfMonth(1);
        filtreDateFin   = LocalDate.now();
        filtrerPresences();
    }

    public void filtrerSemaineCourante() {
        LocalDate today = LocalDate.now();
        filtreDateDebut = today.with(java.time.DayOfWeek.MONDAY);
        filtreDateFin   = today.with(java.time.DayOfWeek.FRIDAY);
        filtrerPresences();
    }

    // ══════════════════════════════════════════════════════════
    // JOURS FÉRIÉS
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauJourFerie() {
        jourFerie = new JourFerie();
        modeEditionFerie = false;
    }

    public void preparerEditionJourFerie(JourFerie jf) {
        this.jourFerie = jf;
        modeEditionFerie = true;
    }

    public String sauvegarderJourFerie() {
        try {
            if (jourFerie.getLibelle() == null || jourFerie.getLibelle().isBlank()) {
                addError("Le libellé est obligatoire.");
                return null;
            }
            if (jourFerie.getDate() == null) {
                addError("La date est obligatoire.");
                return null;
            }

            Long instId = getInstId();

            if (modeEditionFerie) {
                presenceService.modifierJourFerie(jourFerie, getOp());
                addInfo("Jour férié modifié : " + jourFerie.getLibelle());
            } else {
                presenceService.creerJourFerie(jourFerie, instId, getOp());
                addInfo("Jour férié créé : " + jourFerie.getLibelle());
            }
            joursFeries = presenceService.getJoursFeriesByInstitution(instId);
            jourFerie = new JourFerie();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void supprimerJourFerie(Long id) {
        try {
            presenceService.supprimerJourFerie(id, getOp());
            joursFeries = presenceService.getJoursFeriesByInstitution(getInstId());
            addInfo("Jour férié supprimé.");
        } catch (Exception ex) {
            addError("Erreur suppression : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT CSV
    // ══════════════════════════════════════════════════════════

    public void exporterPresencesCSV() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) fc.getExternalContext().getResponse();

            String filename = "presences_"
                + filtreDateDebut + "_" + filtreDateFin + "_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".csv";

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('\uFEFF');
            writer.println("Matricule;Nom;Prénom;Date;Type;Heure arrivée;Heure départ;Heures travaillées;Heures sup;Retard (min);Validé");

            for (Presence p : presencesFiltrees) {
                Employe emp = p.getEmploye();
                writer.println(
                    safe(emp != null ? emp.getMatricule() : "") + ";" +
                    safe(emp != null ? emp.getNom()       : "") + ";" +
                    safe(emp != null ? emp.getPrenom()    : "") + ";" +
                    (p.getDatePresence()        != null ? p.getDatePresence().toString() : "") + ";" +
                    safe(p.getType()) + ";" +
                    (p.getHeureArrivee()        != null ? p.getHeureArrivee().toString()  : "") + ";" +
                    (p.getHeureDepart()         != null ? p.getHeureDepart().toString()   : "") + ";" +
                    (p.getHeuresTravaillees()   != null ? p.getHeuresTravaillees()        : "") + ";" +
                    (p.getHeuresSupplementaires()!= null? p.getHeuresSupplementaires()   : "") + ";" +
                    (p.getRetardMinutes()       != null ? p.getRetardMinutes()            : 0) + ";" +
                    (p.isEstValide() ? "Oui" : "Non")
                );
            }
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS UI
    // ══════════════════════════════════════════════════════════

    public String getBadgeTypePresence(String type) {
        if (type == null) return "badge-secondary";
        return switch (type) {
            case "PRESENT"    -> "badge-success";
            case "ABSENT"     -> "badge-danger";
            case "CONGE"      -> "badge-info";
            case "MISSION"    -> "badge-primary";
            case "TELETRAVAIL"-> "badge-warning";
            case "FORMATION"  -> "badge-primary";
            case "JOUR_FERIE" -> "badge-secondary";
            default           -> "badge-secondary";
        };
    }

    public String getLabelTypePresence(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "PRESENT"    -> "Présent";
            case "ABSENT"     -> "Absent";
            case "CONGE"      -> "Congé";
            case "MISSION"    -> "Mission";
            case "TELETRAVAIL"-> "Télétravail";
            case "FORMATION"  -> "Formation";
            case "JOUR_FERIE" -> "Jour férié";
            case "WEEKEND"    -> "Week-end";
            default           -> type;
        };
    }

    /** Taux de présence du jour en % (évite division par zéro). */
    public int getTauxPresenceJour() {
        long total = presents + absents + enConge;
        if (total == 0) return 0;
        return (int)(presents * 100 / total);
    }

    /** Nombre de présences non encore validées. */
    public long getNbPresencesNonValidees() {
        if (presencesFiltrees == null) return 0;
        return presencesFiltrees.stream().filter(p -> !p.isEstValide()).count();
    }

    private Employe trouverEmploye(Long id) {
        if (id == null || employes == null) return null;
        return employes.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
    }

    private String safe(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ══════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════════════

    public List<Presence>  getPresences()               { return presencesFiltrees; }

    /** Absences (filtrées par institution via employes déjà chargés) */
    public List<Presence> getAbsences() {
        if (presencesFiltrees == null) return new java.util.ArrayList<>();
        return presencesFiltrees.stream()
            .filter(p -> p != null && (
                "ABSENCE".equals(p.getType()) ||
                "RETARD".equals(p.getType())  ||
                "MALADIE".equals(p.getType())))
            .collect(java.util.stream.Collectors.toList());
    }

    // ── Méthodes de calcul soldes ─────────────────────────────────────────────

    /** Formate la durée d'une présence pour l'affichage : "45 min" ou "1 jour" */
    public String getDureePresence(com.soutenance.entity.Presence p) {
        if (p == null) return "-";
        if (p.getRetardMinutes() != null && p.getRetardMinutes() > 0)
            return p.getRetardMinutes() + " min";
        if (p.getHeuresTravaillees() != null && p.getHeuresTravaillees() > 0)
            return String.format("%.1fh", p.getHeuresTravaillees());
        return "1 jour";
    }

    /** Cache soldes par employé pour éviter N+1 */
    private java.util.Map<Long, Integer> cacheSoldes = null;

    private java.util.Map<Long, Integer> getSoldesMap() {
        if (cacheSoldes == null) {
            cacheSoldes = new java.util.HashMap<>();
            if (employes != null && demandeCongeDao != null) {
                for (com.soutenance.entity.Employe e : employes) {
                    try {
                        int pris = (int) demandeCongeDao.countJoursApprouvesCetteAnnee(e.getId());
                        cacheSoldes.put(e.getId(), pris);
                    } catch (Exception ex) {
                        cacheSoldes.put(e.getId(), 0);
                    }
                }
            }
        }
        return cacheSoldes;
    }

    public int getSoldeInfo(Long employeId, String type) {
        if (employeId == null) return 0;
        int pris = getSoldesMap().getOrDefault(employeId, 0);
        if ("PRIS".equals(type)) return pris;
        return Math.max(0, 30 - pris); // RESTANT
    }

    /** #{adminPresenceBean.getQuotaAnnuel(id)} */
    public int getQuotaAnnuel(Long employeId) { return 30; }

    /** Jours de congé pris (approuvés) pour l'employé */
    public int getJoursPris(Long employeId) {
        if (employeId == null || demandeCongeDao == null) return 0;
        try { return (int) demandeCongeDao.countJoursApprouvesCetteAnnee(employeId); }
        catch (Exception e) { return 0; }
    }

    /** Jours restants = quota - pris */
    public int getJoursRestants(Long employeId) {
        return Math.max(0, getQuotaAnnuel(employeId) - getJoursPris(employeId));
    }

    /** Report de l'année précédente (0 par défaut) */
    public int getReportAnnee(Long employeId) { return 0; }

    /** Total disponible = restants + report */
    public int getTotalDisponible(Long employeId) {
        return getJoursRestants(employeId) + getReportAnnee(employeId);
    }
    public List<JourFerie> getJoursFeries()             { return joursFeries; }
    public List<Employe>   getEmployes()                { return employes; }
    public java.util.List<com.soutenance.entity.TypeConge> getTypesConge() { return typesConge; }
    public Long    getEmployeSelectionneId()             { return employeSelectionneId; }
    public void    setEmployeSelectionneId(Long id)      { this.employeSelectionneId = id; }
    public Long    getTypeCongeSelectionneId()            { return typeCongeSelectionneId; }
    public void    setTypeCongeSelectionneId(Long id)     { this.typeCongeSelectionneId = id; }

    public Presence        getPresence()                { return presence; }
    public void            setPresence(Presence p)      { this.presence = p; }
    public Presence        getPresenceSelectionnee()    { return presenceSelectionnee; }
    public JourFerie       getJourFerie()               { return jourFerie; }
    public void            setJourFerie(JourFerie jf)   { this.jourFerie = jf; }

    public boolean         isModeEditionPresence()      { return modeEditionPresence; }
    public boolean         isModeEditionFerie()         { return modeEditionFerie; }

    public LocalDate       getFiltreDateDebut()         { return filtreDateDebut; }
    public void            setFiltreDateDebut(LocalDate d){ this.filtreDateDebut = d; }
    public LocalDate       getFiltreDateFin()           { return filtreDateFin; }
    public void            setFiltreDateFin(LocalDate d){ this.filtreDateFin = d; }
    public String          getFiltreTypePresence()      { return filtreTypePresence; }
    public void            setFiltreTypePresence(String v){ this.filtreTypePresence = v; }
    public Long            getFiltreEmployeId()         { return filtreEmployeId; }
    public void            setFiltreEmployeId(Long v)   { this.filtreEmployeId = v; }
    public boolean         isFiltreNonValides()         { return filtreNonValides; }
    public void            setFiltreNonValides(boolean v){ this.filtreNonValides = v; }
    public Long            getEmployePointageId()       { return employePointageId; }
    public void            setEmployePointageId(Long v) { this.employePointageId = v; }

    public long            getPresents()                { return presents; }
    public long            getAbsents()                 { return absents; }
    public long            getRetards()                 { return retards; }
    public long            getEnConge()                 { return enConge; }
    public long            getHeuresSupTotales()        { return heuresSupTotales; }

    // ════════════════════════════════════════════════════════════
    // CORRECTIONS : getters manquants appelés par conges.xhtml
    // ════════════════════════════════════════════════════════════

    /** #{adminPresenceBean.totalEmployes} */
    public long getTotalEmployes() {
        return employes != null ? employes.size() : 0;
    }

    /** #{adminPresenceBean.totalAbsents} — alias de absents */
    public long getTotalAbsents()    { return absents; }

    /**
     * #{adminPresenceBean.totalApprouves} — présences validées (estValide=true).
     * CORRECTION : Presence n'a pas getStatut() — utilise isEstValide().
     */
    public long getTotalApprouves() {
        if (presencesFiltrees == null) return 0;
        return presencesFiltrees.stream()
            .filter(p -> p.isEstValide()).count();
    }

    /**
     * #{adminPresenceBean.totalEnAttente} — présences non encore validées.
     * CORRECTION : utilise !isEstValide() au lieu de getStatut().
     */
    public long getTotalEnAttente() {
        if (presencesFiltrees == null) return 0;
        return presencesFiltrees.stream()
            .filter(p -> !p.isEstValide()).count();
    }

    /** #{adminPresenceBean.totalHeuresSupp} — heures supplémentaires totales. */
    public long getTotalHeuresSupp() { return heuresSupTotales; }

    /**
     * #{adminPresenceBean.tauxAbsenteisme} — % d'absents sur les présents+absents.
     */
    public double getTauxAbsenteisme() {
        long total = presents + absents;
        if (total == 0) return 0.0;
        return (double) absents / total * 100;
    }

    /**
     * #{adminPresenceBean.institutions} — liste des institutions pour le filtre.
     * Retourne une liste vide si InstitutionService non injecté.
     * Le XHTML accède à institutions[0].nom → liste avec au moins 1 élément
     * pour éviter IndexOutOfBoundsException.
     */
    public List<com.soutenance.entity.Institution> getInstitutions() {
        try {
            if (sessionBean != null && sessionBean.getUtilisateurConnecte() != null) {
                com.soutenance.entity.User u = sessionBean.getUtilisateurConnecte();
                if (u.getInstitution() != null) {
                    return java.util.Collections.singletonList(u.getInstitution());
                }
            }
        } catch (Exception ignored) {}
        return java.util.Collections.emptyList();
    }
}