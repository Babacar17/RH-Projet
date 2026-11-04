package com.soutenance.bean;

import com.soutenance.service.ReportingService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bean JSF — Reporting, tableaux de bord consolidés et exports.
 *
 * Fonctionnalités couvertes (point 7 du cahier des charges) :
 *  - Tableaux de bord consolidés multi-institutions
 *  - Rapports RH : effectifs, masse salariale, turnover, absentéisme
 *  - Indicateurs de performance RH (KPI)
 *  - Rapports comparatifs entre institutions
 *  - Export des données en Excel, PDF, CSV
 *
 * Ce bean est accessible aux rôles ADMIN, RH et AUDITEUR.
 * Le filtrage par institution est géré automatiquement via SessionBean :
 *   - super-admin → toutes institutions
 *   - admin institution → seulement son institution
 */
@Component("reportingBean")
@Scope("view")
public class ReportingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private ReportingService reportingService;
    @Autowired private SessionBean      sessionBean;

    // ── Période d'analyse ────────────────────────────────────────────────────
    private LocalDate dateDebut   = LocalDate.now().withDayOfMonth(1).minusMonths(11);
    private LocalDate dateFin     = LocalDate.now();
    private int       annee       = LocalDate.now().getYear();

    // ── KPI Effectifs ────────────────────────────────────────────────────────
    private long       totalEmployes;
    private long       employesActifs;
    private long       employesInactifs;
    private long       nouveauxRecrutements;       // sur la période
    private long       departsTotal;               // sur la période
    private double     tauxTurnover;               // en %
    private double     tauxAbsenteisme;            // en %
    private BigDecimal masseSalariale;
    private BigDecimal salaireMoyen;

    // ── KPI Recrutement ──────────────────────────────────────────────────────
    private long       totalCandidatures;
    private long       candidaturesRetenues;
    private long       offresPubliees;
    private double     tauxConversion;             // candidatures → recrutements

    // ── KPI Congés ───────────────────────────────────────────────────────────
    private long       demandesCongeTotal;
    private long       demandesCongeApprouvees;
    private long       demandesCongeRefusees;
    private double     moyenneJoursCongeParEmploye;

    // ── KPI Formations ───────────────────────────────────────────────────────
    private long       formationsRealisees;
    private long       salariesFormes;
    private double     tauxAccesFormation;         // % employés formés

    // ── Données pour graphiques ──────────────────────────────────────────────
    /** Évolution des effectifs mois par mois (label → nombre) */
    private Map<String, Long> evolutionEffectifs;
    /** Répartition par statut contractuel */
    private Map<String, Long> repartitionStatuts;
    /** Répartition par niveau hiérarchique */
    private Map<String, Long> repartitionNiveaux;
    /** Absentéisme mois par mois */
    private Map<String, Long> evolutionAbsenteisme;
    /** Comparaison masse salariale entre institutions (super-admin) */
    private Map<String, BigDecimal> comparaisonMasseSalariale;

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean chargementEnCours = false;
    private String  typeRapport       = "EFFECTIFS"; // EFFECTIFS, CONGES, RECRUTEMENT, PAIE, FORMATIONS

    @PostConstruct
    public void init() {
        try {
            chargerKpi();
        } catch (Exception e) {
            System.err.println("ReportingBean init error: " + e.getMessage());
        }
    }

    /**
     * Charge tous les KPI selon l'institution du connecté.
     * Le service filtre automatiquement si institutionId != null.
     */
    private void chargerKpi() {
        Long institutionId = sessionBean.getInstitutionId(); // null = super-admin (toutes institutions)

        // Effectifs
        totalEmployes         = reportingService.countTotalEmployes(institutionId);
        employesActifs        = reportingService.countEmployesActifs(institutionId);
        employesInactifs      = totalEmployes - employesActifs;
        nouveauxRecrutements  = reportingService.countRecrutements(institutionId, dateDebut, dateFin);
        departsTotal          = reportingService.countDeparts(institutionId, dateDebut, dateFin);
        tauxTurnover          = reportingService.calculerTauxTurnover(institutionId, dateDebut, dateFin);
        tauxAbsenteisme       = reportingService.calculerTauxAbsenteisme(institutionId, dateDebut, dateFin);
        masseSalariale        = reportingService.getMasseSalariale(institutionId);
        salaireMoyen          = reportingService.getSalaireMoyen(institutionId);

        // Recrutement
        totalCandidatures     = reportingService.countCandidatures(institutionId, dateDebut, dateFin);
        candidaturesRetenues  = reportingService.countCandidaturesRetenues(institutionId, dateDebut, dateFin);
        offresPubliees        = reportingService.countOffresPubliees(institutionId, dateDebut, dateFin);
        tauxConversion        = totalCandidatures > 0
            ? (double) candidaturesRetenues / totalCandidatures * 100 : 0;

        // Congés
        demandesCongeTotal     = reportingService.countDemandesConge(institutionId, dateDebut, dateFin);
        demandesCongeApprouvees = reportingService.countDemandesCongeApprouvees(institutionId, dateDebut, dateFin);
        demandesCongeRefusees  = reportingService.countDemandesCongeRefusees(institutionId, dateDebut, dateFin);
        moyenneJoursCongeParEmploye = reportingService.getMoyenneJoursConge(institutionId, dateDebut, dateFin);

        // Formations
        formationsRealisees   = reportingService.countFormationsRealisees(institutionId, dateDebut, dateFin);
        salariesFormes        = reportingService.countSalariesFormes(institutionId, dateDebut, dateFin);
        tauxAccesFormation    = employesActifs > 0
            ? (double) salariesFormes / employesActifs * 100 : 0;

        // Données graphiques
        evolutionEffectifs         = reportingService.getEvolutionEffectifs(institutionId, annee);
        repartitionStatuts         = reportingService.getRepartitionStatuts(institutionId);
        repartitionNiveaux         = reportingService.getRepartitionNiveaux(institutionId);
        evolutionAbsenteisme       = reportingService.getEvolutionAbsenteisme(institutionId, annee);

        // Comparaison inter-institutions uniquement pour super-admin
        if (sessionBean.isSuperAdmin()) {
            comparaisonMasseSalariale = reportingService.getComparaisonMasseSalariale();
        }
    }

    // ════════════════════════════════════════════════════════════
    // RECHARGEMENT
    // ════════════════════════════════════════════════════════════

    /** Recharge les KPI avec la période et l'institution courantes. */
    public void appliquerPeriode() {
        if (dateDebut != null && dateFin != null && dateDebut.isAfter(dateFin)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Période invalide",
                "La date de début doit être antérieure à la date de fin.");
            return;
        }
        chargerKpi();
        addMsg(FacesMessage.SEVERITY_INFO, "Données actualisées",
            "Rapport mis à jour pour la période sélectionnée.");
    }

    /** Recharge les KPI pour l'année sélectionnée. */
    public void changerAnnee() {
        dateDebut = LocalDate.of(annee, 1, 1);
        dateFin   = LocalDate.of(annee, 12, 31);
        chargerKpi();
    }

    // ════════════════════════════════════════════════════════════
    // EXPORTS
    // ════════════════════════════════════════════════════════════

    /**
     * Exporte le rapport en cours au format Excel (.xlsx).
     * Utilise le ReportingService pour générer les octets,
     * puis les pousse dans la réponse HTTP.
     */
    public void exporterExcel() {
        try {
            Long institutionId = sessionBean.getInstitutionId();
            byte[] contenu = reportingService.genererExcel(
                typeRapport, institutionId, dateDebut, dateFin);

            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();

            String nomFichier = "rapport_" + typeRapport.toLowerCase()
                + "_" + dateFin + ".xlsx";
            response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nomFichier + "\"");
            response.setContentLength(contenu.length);

            try (OutputStream os = response.getOutputStream()) {
                os.write(contenu);
                os.flush();
            }
            fc.responseComplete();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur export",
                "Impossible de générer le fichier Excel : " + ex.getMessage());
        }
    }

    /**
     * Exporte le rapport en cours au format PDF.
     */
    public void exporterPdf() {
        try {
            Long institutionId = sessionBean.getInstitutionId();
            byte[] contenu = reportingService.genererPdf(
                typeRapport, institutionId, dateDebut, dateFin);

            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();

            String nomFichier = "rapport_" + typeRapport.toLowerCase()
                + "_" + dateFin + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nomFichier + "\"");
            response.setContentLength(contenu.length);

            try (OutputStream os = response.getOutputStream()) {
                os.write(contenu);
                os.flush();
            }
            fc.responseComplete();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur export",
                "Impossible de générer le PDF : " + ex.getMessage());
        }
    }

    /**
     * Exporte au format CSV (léger, compatible tableur).
     */
    public void exporterCsv() {
        try {
            Long institutionId = sessionBean.getInstitutionId();
            byte[] contenu = reportingService.genererCsv(
                typeRapport, institutionId, dateDebut, dateFin);

            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();

            String nomFichier = "rapport_" + typeRapport.toLowerCase()
                + "_" + dateFin + ".csv";
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nomFichier + "\"");
            response.setContentLength(contenu.length);

            try (OutputStream os = response.getOutputStream()) {
                os.write(contenu);
                os.flush();
            }
            fc.responseComplete();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur export",
                "Impossible de générer le CSV : " + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    /** Formate un double en pourcentage avec 1 décimale. */
    public String formatPourcentage(double valeur) {
        return String.format("%.1f%%", valeur);
    }

    /** Couleur CSS selon le niveau de turnover. */
    public String getCouleurTurnover() {
        if (tauxTurnover < 5)  return "text-success";
        if (tauxTurnover < 15) return "text-warning";
        return "text-danger";
    }

    /** Couleur CSS selon le niveau d'absentéisme. */
    public String getCouleurAbsenteisme() {
        if (tauxAbsenteisme < 3)  return "text-success";
        if (tauxAbsenteisme < 8)  return "text-warning";
        return "text-danger";
    }

    /** Serialize une Map en JSON pour les graphiques (Chart.js/PrimeFaces). */
    public String toJsonLabels(Map<String, ?> data) {
        if (data == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        data.keySet().forEach(k -> sb.append("\"").append(k).append("\","));
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        return sb.append("]").toString();
    }

    public String toJsonValues(Map<String, ?> data) {
        if (data == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        data.values().forEach(v -> sb.append(v).append(","));
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        return sb.append("]").toString();
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public LocalDate getDateDebut()                          { return dateDebut; }
    public void      setDateDebut(LocalDate d)               { this.dateDebut = d; }
    public LocalDate getDateFin()                            { return dateFin; }
    public void      setDateFin(LocalDate d)                 { this.dateFin = d; }
    public int       getAnnee()                              { return annee; }
    public void      setAnnee(int a)                         { this.annee = a; }

    public String  getTypeRapport()                          { return typeRapport; }
    public void    setTypeRapport(String t)                  { this.typeRapport = t; }

    public long      getTotalEmployes()                      { return totalEmployes; }
    public long      getEmployesActifs()                     { return employesActifs; }
    public long      getEmployesInactifs()                   { return employesInactifs; }
    public long      getNouveauxRecrutements()               { return nouveauxRecrutements; }
    public long      getDepartsTotal()                       { return departsTotal; }
    public double    getTauxTurnover()                       { return tauxTurnover; }
    public double    getTauxAbsenteisme()                    { return tauxAbsenteisme; }
    public BigDecimal getMasseSalariale()                    { return masseSalariale; }
    public BigDecimal getSalaireMoyen()                      { return salaireMoyen; }

    public long    getTotalCandidatures()                    { return totalCandidatures; }
    public long    getCandidaturesRetenues()                 { return candidaturesRetenues; }
    public long    getOffresPubliees()                       { return offresPubliees; }
    public double  getTauxConversion()                       { return tauxConversion; }

    public long    getDemandesCongeTotal()                   { return demandesCongeTotal; }
    public long    getDemandesCongeApprouvees()              { return demandesCongeApprouvees; }
    public long    getDemandesCongeRefusees()                { return demandesCongeRefusees; }
    public double  getMoyenneJoursCongeParEmploye()          { return moyenneJoursCongeParEmploye; }

    public long    getFormationsRealisees()                  { return formationsRealisees; }
    public long    getSalariesFormes()                       { return salariesFormes; }
    public double  getTauxAccesFormation()                   { return tauxAccesFormation; }

    public Map<String, Long>       getEvolutionEffectifs()         { return evolutionEffectifs; }
    public Map<String, Long>       getRepartitionStatuts()         { return repartitionStatuts; }
    public Map<String, Long>       getRepartitionNiveaux()         { return repartitionNiveaux; }
    public Map<String, Long>       getEvolutionAbsenteisme()       { return evolutionAbsenteisme; }
    public Map<String, BigDecimal> getComparaisonMasseSalariale()  { return comparaisonMasseSalariale; }

    public boolean isChargementEnCours()                     { return chargementEnCours; }
}