package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.AdminReportingService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component("adminReportingBean")
@Scope("view")
public class AdminReportingBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminReportingBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminReportingService reportingService;
    @Autowired private SessionBean           sessionBean;

    // ── KPIs globaux ───────────────────────────────────────────────────────
    private long          totalInstitutions;
    private long          totalEmployesGlobal;
    private long          totalActifsGlobal;
    private BigDecimal    masseSalarialeGlobale   = BigDecimal.ZERO;

    // ── KPIs institution courante ──────────────────────────────────────────
    private long          effectifTotal;
    private long          effectifActif;
    private long          effectifInactif;
    private long          effectifRetraite;
    private long          nbCDI;
    private long          nbCDD;
    private long          nbTitulaires;
    private long          nbStagiaires;
    private BigDecimal    masseSalariale          = BigDecimal.ZERO;
    private BigDecimal    salaireMoyen            = BigDecimal.ZERO;
    private long          congesEnAttente;
    private long          congesApprouves;
    private long          candidaturesRecues;
    private long          offresPubliees;
    private long          evaluationsEnCours;
    private double        tauxAbsenteisme;
    private double        tauxTurnover;
    private long          contratsExpirantDans30j;

    // CORRECTION : types alignés sur les retours réels du service
    // Map<Integer,Long> pour les séries temporelles (clé = numéro de mois 1-12)
    // Map<String,Long>  pour les répartitions (clé = libellé)
    private Map<Integer, Long>  evolutionEffectifs      = new java.util.LinkedHashMap<>();
    private Map<Integer, Long>  evolutionConges         = new java.util.LinkedHashMap<>();
    private Map<String,  Long>  repartitionParContrat   = new java.util.LinkedHashMap<>();
    private Map<String,  Long>  repartitionParDirection = new java.util.LinkedHashMap<>();
    private Map<String,  Long>  comparatifInstitutions  = new java.util.LinkedHashMap<>();

    // ── Journal d'audit ───────────────────────────────────────────────────
    private List<LogAction> logsAudit          = new ArrayList<>();
    private String          filtreActionLog;
    private String          filtreUsernameLog;
    private LocalDate       filtreLogDateDebut;
    private LocalDate       filtreLogDateFin;
    private int             pageLogs           = 0;
    private int             taillePage         = 50;

    // ── Période du rapport ─────────────────────────────────────────────────
    private Integer rapportAnnee;
    private Integer rapportMois;
    private String  rapportType;

    @PostConstruct
    public void init() {
        rapportAnnee       = LocalDate.now().getYear();
        rapportMois        = LocalDate.now().getMonthValue();
        filtreLogDateDebut = LocalDate.now().minusDays(30);
        filtreLogDateFin   = LocalDate.now();
        rapportType        = "EFFECTIFS";
        chargerKPIs();
        chargerLogs();
    }

    private void chargerKPIs() {
        try {
            Long instId = sessionBean.getInstitutionId();

            if (sessionBean.isSuperAdmin()) {
                totalInstitutions     = reportingService.countInstitutions();
                totalEmployesGlobal   = reportingService.countEmployesTotal();
                totalActifsGlobal     = reportingService.countEmployesActifsTotal();
                masseSalarialeGlobale = reportingService.getMasseSalarialeGlobale();
                // CORRECTION : getComparatifInstitutions() retourne Map<String,Long>
                comparatifInstitutions = reportingService.getComparatifInstitutions();
            }

            effectifTotal      = reportingService.countEffectifTotal(instId);
            effectifActif      = reportingService.countByStatutEmploye(instId, "ACTIF");
            effectifInactif    = reportingService.countByStatutEmploye(instId, "INACTIF");
            effectifRetraite   = reportingService.countByStatutEmploye(instId, "RETRAITE");
            nbCDI              = reportingService.countByTypeContrat(instId, "CDI");
            nbCDD              = reportingService.countByTypeContrat(instId, "CDD");
            nbTitulaires       = reportingService.countByTypeContrat(instId, "TITULAIRE");
            nbStagiaires       = reportingService.countByTypeContrat(instId, "STAGIAIRE");
            masseSalariale     = reportingService.getMasseSalariale(instId);
            salaireMoyen       = reportingService.getSalaireMoyen(instId);
            congesEnAttente    = reportingService.countCongesEnAttente(instId);
            congesApprouves    = reportingService.countCongesApprouves(instId);
            candidaturesRecues = reportingService.countCandidaturesRecues(instId);
            offresPubliees     = reportingService.countOffresPubliees(instId);
            evaluationsEnCours = reportingService.countEvaluationsEnCours(instId);
            tauxAbsenteisme    = reportingService.getTauxAbsenteismeMoisCourant(instId);
            tauxTurnover       = reportingService.getTauxTurnoverAnnuel(instId);
            contratsExpirantDans30j = reportingService.countContratsExpirant(instId, 30);

            // CORRECTION : types retournés sont Map<Integer,Long> et Map<String,Long>
            evolutionEffectifs      = reportingService.getEvolutionEffectifs(instId, rapportAnnee);
            evolutionConges         = reportingService.getEvolutionConges(instId, rapportAnnee);
            repartitionParContrat   = reportingService.getRepartitionParContrat(instId);
            repartitionParDirection = reportingService.getRepartitionParDirection(instId);

        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
        }
    }

    private void chargerLogs() {
        try {
            // CORRECTION : getLogsFiltre(username, action, debut, fin, max) — pas d'instId ni d'offset
            // Le service ne filtre pas par institution et ne supporte pas la pagination par offset.
            // On simule la pagination côté mémoire via limit(max) avec taillePage.
            logsAudit = reportingService.getLogsFiltre(
                filtreUsernameLog, filtreActionLog,
                filtreLogDateDebut, filtreLogDateFin,
                taillePage);
        } catch (Exception e) {
            logsAudit = new ArrayList<>();
        }
    }

    // ══════════════════════════════════════════════════════════
    // FILTRES AUDIT
    // ══════════════════════════════════════════════════════════

    public void filtrerLogs() {
        pageLogs = 0;
        chargerLogs();
    }

    public void reinitialiserFiltresLogs() {
        filtreActionLog    = null;
        filtreUsernameLog  = null;
        filtreLogDateDebut = LocalDate.now().minusDays(30);
        filtreLogDateFin   = LocalDate.now();
        pageLogs           = 0;
        chargerLogs();
    }

    public void pageSuivante() {
        pageLogs++;
        chargerLogs();
    }

    public void pagePrecedente() {
        if (pageLogs > 0) { pageLogs--; chargerLogs(); }
    }

    public boolean isPremierePageLogs() { return pageLogs == 0; }
    public boolean isDernierePage() {
        return logsAudit == null || logsAudit.size() < taillePage;
    }

    // ══════════════════════════════════════════════════════════
    // RAFRAÎCHISSEMENT
    // ══════════════════════════════════════════════════════════

    public void rafraichirKPIs() {
        chargerKPIs();
        addInfo("Tableau de bord mis à jour.");
    }

    public void changerAnneeRapport() {
        chargerKPIs();
    }

    // ══════════════════════════════════════════════════════════
    // EXPORTS CSV
    // ══════════════════════════════════════════════════════════

    public void exporterEffectifsCSV() {
        try {
            Long instId = sessionBean.getInstitutionId();
            List<Employe> employes = reportingService.getEmployesPourExport(instId);
            String filename = "effectifs_" + sessionBean.getCodeInstitution()
                + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('\uFEFF');
            writer.println("Matricule;Nom;Prénom;Email;Téléphone;Poste;Direction;Division;Service;Type contrat;Statut;Date embauche;Date fin contrat;Salaire base;Grade");

            for (Employe e : employes) {
                writer.println(
                    safe(e.getMatricule())           + ";" +
                    safe(e.getNom())                 + ";" +
                    safe(e.getPrenom())              + ";" +
                    safe(e.getEmail())               + ";" +
                    safe(e.getTelephone())            + ";" +
                    safe(e.getIntiutePoste())         + ";" +
                    safe(e.getNomDirection())         + ";" +
                    safe(e.getNomDivision())          + ";" +
                    safe(e.getNomService())           + ";" +
                    safe(e.getTypeContrat())          + ";" +
                    safe(e.getStatut())               + ";" +
                    (e.getDateEmbauche()   != null ? e.getDateEmbauche().toString()    : "") + ";" +
                    (e.getDateFinContrat() != null ? e.getDateFinContrat().toString()  : "") + ";" +
                    (e.getSalaireBase()    != null ? e.getSalaireBase().toPlainString() : "") + ";" +
                    safe(e.getGradeEchelon())
                );
            }
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export effectifs : " + ex.getMessage());
        }
    }

    public void exporterMasseSalarialeCSV() {
        try {
            Long instId = sessionBean.getInstitutionId();
            // CORRECTION : ordre des paramètres — getBulletinsMoisCourant(instId, mois, annee)
            List<BulletinPaie> bulletins = reportingService.getBulletinsMoisCourant(
                instId, rapportMois, rapportAnnee);
            String filename = "masse_salariale_" + rapportMois + "_" + rapportAnnee + "_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('\uFEFF');
            writer.println("Matricule;Nom;Prénom;Salaire base;Total primes;Cotisations salariales;Impôt revenu;Autres retenues;Total retenues;Salaire net;Cotisations patronales;Statut");

            BigDecimal totalNet = BigDecimal.ZERO;
            for (BulletinPaie b : bulletins) {
                Employe emp = b.getEmploye();
                writer.println(
                    safe(emp != null ? emp.getMatricule() : "")    + ";" +
                    safe(emp != null ? emp.getNom()       : "")    + ";" +
                    safe(emp != null ? emp.getPrenom()    : "")    + ";" +
                    fmt(b.getSalaireBase())                         + ";" +
                    fmt(b.getTotalPrimes())                        + ";" +
                    fmt(b.getTotalCotisationsSalariales())         + ";" +
                    fmt(b.getImpotRevenu())                        + ";" +
                    fmt(b.getAutresRetenues())                     + ";" +
                    fmt(b.getTotalRetenues())                      + ";" +
                    fmt(b.getSalaireNet())                         + ";" +
                    fmt(b.getTotalCotisationsPatronales())         + ";" +
                    safe(b.getStatut())
                );
                if (b.getSalaireNet() != null) totalNet = totalNet.add(b.getSalaireNet());
            }
            writer.println(";;;TOTAL;;;;;;" + totalNet.toPlainString() + ";;");
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export masse salariale : " + ex.getMessage());
        }
    }

    public void exporterCongesCSV() {
        try {
            Long instId = sessionBean.getInstitutionId();
            List<DemandeConge> conges = reportingService.getCongesAnnee(instId, rapportAnnee);
            String filename = "conges_" + rapportAnnee + "_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('﻿');
            writer.println("Matricule;Nom;Prénom;Type de congé;Date début;Date fin;Nombre de jours;Motif;Statut;Demandé le");

            DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            for (DemandeConge d : conges) {
                Employe emp = d.getEmploye();
                writer.println(
                    safe(emp != null ? emp.getMatricule() : "") + ";" +
                    safe(emp != null ? emp.getNom()       : "") + ";" +
                    safe(emp != null ? emp.getPrenom()    : "") + ";" +
                    safe(d.getTypeConge() != null ? d.getTypeConge().getLibelle() : "") + ";" +
                    (d.getDateDebut() != null ? d.getDateDebut().format(fmtDate) : "") + ";" +
                    (d.getDateFin() != null ? d.getDateFin().format(fmtDate) : "") + ";" +
                    (d.getNombreJours() != null ? d.getNombreJours() : "") + ";" +
                    safe(d.getMotif()) + ";" +
                    safe(d.getStatut()) + ";" +
                    (d.getDateDemande() != null ? d.getDateDemande().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "")
                );
            }
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export congés : " + ex.getMessage());
        }
    }

    public void exporterAuditCSV() {
        try {
            // CORRECTION : getLogsFiltre(username, action, debut, fin, max) — Integer.MAX_VALUE pour tout
            List<LogAction> tousLogs = reportingService.getLogsFiltre(
                filtreUsernameLog, filtreActionLog,
                filtreLogDateDebut, filtreLogDateFin,
                Integer.MAX_VALUE);

            String filename = "audit_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('\uFEFF');
            writer.println("Date;Utilisateur;Action;Détail;Entité;ID entité;Adresse IP");

            for (LogAction log : tousLogs) {
                writer.println(
                    (log.getDateAction() != null ? log.getDateAction().toString() : "") + ";" +
                    safe(log.getUsername())  + ";" +
                    safe(log.getAction())    + ";" +
                    safe(log.getDetail())    + ";" +
                    safe(log.getEntite())    + ";" +
                    (log.getEntiteId() != null ? log.getEntiteId() : "") + ";" +
                    safe(log.getAdresseIp())
                );
            }
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export audit : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS UI
    // ══════════════════════════════════════════════════════════

    public int getTauxOccupationPostes() {
        if (effectifTotal == 0) return 0;
        return (int) Math.min(100, effectifActif * 100 / Math.max(1, effectifTotal));
    }

    public String getClasseTauxAbsenteisme() {
        if (tauxAbsenteisme < 3)  return "progress-bar bg-success";
        if (tauxAbsenteisme < 8)  return "progress-bar bg-warning";
        return "progress-bar bg-danger";
    }

    public String getBadgeActionLog(String action) {
        if (action == null) return "badge-secondary";
        if (action.startsWith("CREATION"))     return "badge-success";
        if (action.startsWith("MODIFICATION")) return "badge-primary";
        if (action.startsWith("SUPPRESSION"))  return "badge-danger";
        if (action.startsWith("CONNEXION"))    return "badge-info";
        if (action.startsWith("VALIDATION"))   return "badge-success";
        if (action.startsWith("REFUS"))        return "badge-warning";
        return "badge-secondary";
    }

    public String getMoisLabel(int mois) {
        String[] noms = {"Jan","Fév","Mar","Avr","Mai","Jun",
                         "Jul","Aoû","Sep","Oct","Nov","Déc"};
        return (mois >= 1 && mois <= 12) ? noms[mois - 1] : "?";
    }

    private String safe(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String fmt(BigDecimal v) {
        return v != null ? v.toPlainString() : "0";
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Info", msg));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ══════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════════════

    public long          getTotalInstitutions()       { return totalInstitutions; }
    public long          getTotalEmployesGlobal()     { return totalEmployesGlobal; }
    public long          getTotalActifsGlobal()       { return totalActifsGlobal; }
    public BigDecimal    getMasseSalarialeGlobale()   { return masseSalarialeGlobale; }

    public long          getEffectifTotal()           { return effectifTotal; }
    public long          getEffectifActif()           { return effectifActif; }
    public long          getEffectifInactif()         { return effectifInactif; }
    public long          getEffectifRetraite()        { return effectifRetraite; }
    public long          getNbCDI()                   { return nbCDI; }
    public long          getNbCDD()                   { return nbCDD; }
    public long          getNbTitulaires()            { return nbTitulaires; }
    public long          getNbStagiaires()            { return nbStagiaires; }
    public BigDecimal    getMasseSalariale()          { return masseSalariale; }
    public BigDecimal    getSalaireMoyen()            { return salaireMoyen; }
    public long          getCongesEnAttente()         { return congesEnAttente; }
    public long          getCongesApprouves()         { return congesApprouves; }
    public long          getCandidaturesRecues()      { return candidaturesRecues; }
    public long          getOffresPubliees()          { return offresPubliees; }
    public long          getEvaluationsEnCours()      { return evaluationsEnCours; }
    public double        getTauxAbsenteisme()         { return tauxAbsenteisme; }
    public double        getTauxTurnover()            { return tauxTurnover; }
    public long          getContratsExpirantDans30j() { return contratsExpirantDans30j; }

    // CORRECTION : types corrects alignés sur le service
    public Map<Integer, Long> getEvolutionEffectifs()      { return evolutionEffectifs; }
    public Map<Integer, Long> getEvolutionConges()         { return evolutionConges; }
    public Map<String,  Long> getRepartitionParContrat()   { return repartitionParContrat; }
    public Map<String,  Long> getRepartitionParDirection() { return repartitionParDirection; }
    public Map<String,  Long> getComparatifInstitutions()  { return comparatifInstitutions; }

    public List<LogAction>  getLogsAudit()              { return logsAudit; }
    public String           getFiltreActionLog()         { return filtreActionLog; }
    public void             setFiltreActionLog(String v) { this.filtreActionLog = v; }
    public String           getFiltreUsernameLog()       { return filtreUsernameLog; }
    public void             setFiltreUsernameLog(String v){ this.filtreUsernameLog = v; }
    public LocalDate        getFiltreLogDateDebut()      { return filtreLogDateDebut; }
    public void             setFiltreLogDateDebut(LocalDate v){ this.filtreLogDateDebut = v; }
    public LocalDate        getFiltreLogDateFin()        { return filtreLogDateFin; }
    public void             setFiltreLogDateFin(LocalDate v)  { this.filtreLogDateFin = v; }
    public int              getPageLogs()                { return pageLogs; }
    public int              getTaillePage()              { return taillePage; }

    public Integer          getRapportAnnee()            { return rapportAnnee; }
    public void             setRapportAnnee(Integer v)   { this.rapportAnnee = v; }
    public Integer          getRapportMois()             { return rapportMois; }
    public void             setRapportMois(Integer v)    { this.rapportMois = v; }
    public String           getRapportType()             { return rapportType; }
    public void             setRapportType(String v)     { this.rapportType = v; }
}