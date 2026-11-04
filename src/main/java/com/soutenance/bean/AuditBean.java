package com.soutenance.bean;

import com.soutenance.entity.LogAction;
import com.soutenance.service.AdminService;
import com.soutenance.service.AuditService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean JSF — Audit, sécurité et conformité RGPD.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. Propriété manquante : #{auditBean.dateDerniereSauvegarde}
 *    → champ dateDerniereSauvegarde ajouté, alimenté par declencherSauvegarde().
 *
 * 2. Incohérence codes d'action :
 *    AuditBean filtrait "ECHEC_CONNEXION" et "CONNEXION"
 *    mais UserService (corrigé) log "CONNEXION_ECHEC", "CONNEXION_BLOQUEE"
 *    et LoginBean log "CONNEXION".
 *    → calculerStatistiques() couvre les deux formes pour rétrocompatibilité.
 *
 * 3. Accès données sensibles : filtre étendu aux actions PAIE, DOCUMENT,
 *    BULLETIN pour une meilleure couverture RGPD.
 */
@Component("auditBean")
@Scope("view")
public class AuditBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private AuditService  auditService;
    @Autowired private AdminService  adminService;
    @Autowired private SessionBean   sessionBean;

    // ── Journal d'audit ──────────────────────────────────────────────────────
    private List<LogAction> logsComplets;
    private List<LogAction> logsFiltres;
    private List<LogAction> logsRecents;

    // ── Filtres de recherche ─────────────────────────────────────────────────
    private String    filtreUsername;
    private String    filtreAction;
    private String    filtreIp;
    private LocalDate filtreDateDebut;
    private LocalDate filtreDateFin;
    private String    filtrePeriode = "7J";

    // ── Statistiques sécurité ────────────────────────────────────────────────
    private long totalActions;
    private long connexionsReussies;
    private long connexionsEchouees;
    private long actionsAdmin;
    private long actionsRH;
    private long actionsAujourdHui;
    private long utilisateursDistincts;

    private List<Map.Entry<String, Long>> topUtilisateurs;
    private Map<String, Long>             repartitionActions;
    private Map<String, Long>             evolutionConnexions;

    // ── Log sélectionné ──────────────────────────────────────────────────────
    private LogAction logSelectionne;
    private boolean   afficherDetailsLog = false;

    // ── CORRECTION : date de dernière sauvegarde ─────────────────────────────
    /**
     * Exposé via #{auditBean.dateDerniereSauvegarde}.
     * Initialisé à null (aucune sauvegarde encore déclenchée dans cette session).
     * Mis à jour à chaque appel de declencherSauvegarde().
     */
    private String dateDerniereSauvegarde;

    private static final DateTimeFormatter FMT_SAUVEGARDE =
        DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    @PostConstruct
    public void init() {
        try {
            filtreDateFin   = LocalDate.now();
            filtreDateDebut = filtreDateFin.minusDays(6);
            chargerTout();
        } catch (Exception e) {
            System.err.println("AuditBean init error: " + e.getMessage());
            logsComplets = new ArrayList<>();
            logsFiltres  = new ArrayList<>();
            logsRecents  = new ArrayList<>();
        }
    }

    private void chargerTout() {
        logsComplets = adminService.getTousLesLogs();
        logsRecents  = adminService.getLogsRecents(50);
        appliquerFiltres();
        calculerStatistiques();
    }

    private void calculerStatistiques() {
        if (logsComplets == null) return;

        totalActions = logsComplets.size();

        // CORRECTION : couvre "CONNEXION" (LoginBean) ET les nouvelles formes
        connexionsReussies = logsComplets.stream()
            .filter(l -> l.getAction() != null && (
                "CONNEXION".equals(l.getAction())
            )).count();

        // CORRECTION : couvre "ECHEC_CONNEXION" (ancienne forme) ET
        // "CONNEXION_ECHEC", "CONNEXION_BLOQUEE" (nouvelles formes UserService)
        connexionsEchouees = logsComplets.stream()
            .filter(l -> l.getAction() != null && (
                "ECHEC_CONNEXION".equals(l.getAction())
                || "CONNEXION_ECHEC".equals(l.getAction())
                || "CONNEXION_BLOQUEE".equals(l.getAction())
                || "COMPTE_VERROUILLE".equals(l.getAction())
            )).count();

        actionsAdmin = logsComplets.stream()
            .filter(l -> l.getAction() != null && (
                l.getAction().startsWith("CREATION_")
                || l.getAction().startsWith("SUPPRESSION_")
                || l.getAction().startsWith("MODIFICATION_")
                || l.getAction().startsWith("ACTIVATION_")
                || l.getAction().startsWith("RESET_")
                || l.getAction().startsWith("DEVERROUILLAGE_")
            )).count();

        actionsRH = logsComplets.stream()
            .filter(l -> l.getAction() != null && (
                l.getAction().contains("CONGE")
                || l.getAction().contains("EMPLOYE")
                || l.getAction().contains("CANDIDATURE")
                || l.getAction().contains("EVALUATION")
                || l.getAction().contains("MOUVEMENT")
                || l.getAction().contains("FORMATION")
            )).count();

        LocalDate today = LocalDate.now();
        actionsAujourdHui = logsComplets.stream()
            .filter(l -> l.getDateAction() != null
                && l.getDateAction().toLocalDate().equals(today))
            .count();

        utilisateursDistincts = logsComplets.stream()
            .map(LogAction::getUsername)
            .filter(u -> u != null)
            .distinct().count();

        try {
            topUtilisateurs     = auditService.getTopUtilisateurs(5);
            repartitionActions  = auditService.getRepartitionActions();
            evolutionConnexions = auditService.getEvolutionConnexions(30);
        } catch (Exception ignored) {}
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "audit";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // FILTRAGE
    // ════════════════════════════════════════════════════════════

    public void appliquerFiltres() {
        if (logsComplets == null) { logsFiltres = new ArrayList<>(); return; }
        logsFiltres = logsComplets.stream()
            .filter(l -> {
                if (filtreUsername != null && !filtreUsername.isBlank()) {
                    String u = l.getUsername() != null ? l.getUsername().toLowerCase() : "";
                    if (!u.contains(filtreUsername.toLowerCase())) return false;
                }
                if (filtreAction != null && !filtreAction.isBlank()) {
                    String a = l.getAction() != null ? l.getAction().toLowerCase() : "";
                    if (!a.contains(filtreAction.toLowerCase())) return false;
                }
                if (filtreIp != null && !filtreIp.isBlank()) {
                    String ip = l.getAdresseIp() != null ? l.getAdresseIp() : "";
                    if (!ip.contains(filtreIp)) return false;
                }
                if (filtreDateDebut != null && l.getDateAction() != null
                    && l.getDateAction().toLocalDate().isBefore(filtreDateDebut)) return false;
                if (filtreDateFin != null && l.getDateAction() != null
                    && l.getDateAction().toLocalDate().isAfter(filtreDateFin)) return false;
                return true;
            })
            .sorted((a, b) -> {
                if (a.getDateAction() == null && b.getDateAction() == null) return 0;
                if (a.getDateAction() == null) return 1;
                if (b.getDateAction() == null) return -1;
                return b.getDateAction().compareTo(a.getDateAction());
            })
            .collect(java.util.stream.Collectors.toList());
    }

    public void appliquerPeriodeRapide() {
        filtreDateFin = LocalDate.now();
        switch (filtrePeriode) {
            case "7J"  -> filtreDateDebut = filtreDateFin.minusDays(6);
            case "30J" -> filtreDateDebut = filtreDateFin.minusDays(29);
            case "90J" -> filtreDateDebut = filtreDateFin.minusDays(89);
            case "TOUT" -> { filtreDateDebut = null; filtreDateFin = null; }
            default    -> filtreDateDebut = filtreDateFin.minusDays(6);
        }
        appliquerFiltres();
    }

    public void reinitialiserFiltres() {
        filtreUsername  = null;
        filtreAction    = null;
        filtreIp        = null;
        filtreDateDebut = LocalDate.now().minusDays(6);
        filtreDateFin   = LocalDate.now();
        filtrePeriode   = "7J";
        appliquerFiltres();
    }

    public void selectionnerLog(LogAction log) {
        this.logSelectionne    = log;
        this.afficherDetailsLog = true;
    }

    // ════════════════════════════════════════════════════════════
    // CONFORMITÉ RGPD
    // ════════════════════════════════════════════════════════════

    /**
     * CORRECTION : filtre étendu aux données paie et documentaires.
     */
    public List<LogAction> getAccesDonneesSensibles() {
        if (logsComplets == null) return new ArrayList<>();
        return logsComplets.stream()
            .filter(l -> {
                String action = l.getAction() != null ? l.getAction() : "";
                return action.contains("EMPLOYE")
                    || action.contains("BULLETIN")
                    || action.contains("SALAIRE")
                    || action.contains("CONTRAT")
                    || action.contains("PAIE")
                    || action.contains("DOCUMENT")
                    || action.contains("CONGE");
            })
            .limit(100)
            .collect(java.util.stream.Collectors.toList());
    }

    public List<LogAction> getConnexionsSuspectes() {
        if (logsComplets == null) return new ArrayList<>();
        return logsComplets.stream()
            .filter(l -> l.getAction() != null && (
                "ECHEC_CONNEXION".equals(l.getAction())
                || "CONNEXION_ECHEC".equals(l.getAction())
                || "CONNEXION_BLOQUEE".equals(l.getAction())
                || "COMPTE_VERROUILLE".equals(l.getAction())
            ))
            .limit(50)
            .collect(java.util.stream.Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // EXPORTS
    // ════════════════════════════════════════════════════════════

    public void exporterCsv() {
        try {
            StringBuilder csv = new StringBuilder(
                "\uFEFF\"Date\",\"Utilisateur\",\"Action\",\"Détail\",\"IP\"\n");
            for (LogAction log : logsFiltres) {
                csv.append("\"").append(log.getDateAction() != null ? log.getDateAction() : "").append("\",");
                csv.append("\"").append(safe(log.getUsername())).append("\",");
                csv.append("\"").append(safe(log.getAction())).append("\",");
                csv.append("\"").append(safe(log.getDetail()).replace("\"", "'")).append("\",");
                csv.append("\"").append(safe(log.getAdresseIp())).append("\"\n");
            }
            byte[] contenu = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"audit_" + LocalDate.now() + ".csv\"");
            response.setContentLength(contenu.length);
            try (OutputStream os = response.getOutputStream()) {
                os.write(contenu); os.flush();
            }
            fc.responseComplete();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur export", ex.getMessage());
        }
    }

    public void exporterExcel() {
        try {
            byte[] contenu = auditService.exporterLogsExcel(logsFiltres, getOp());
            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"audit_" + LocalDate.now() + ".xlsx\"");
            response.setContentLength(contenu.length);
            try (OutputStream os = response.getOutputStream()) {
                os.write(contenu); os.flush();
            }
            fc.responseComplete();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur export Excel", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // SAUVEGARDES
    // ════════════════════════════════════════════════════════════

    /**
     * CORRECTION : mise à jour de dateDerniereSauvegarde après déclenchement.
     * #{auditBean.dateDerniereSauvegarde} est maintenant alimenté.
     */
    public void declencherSauvegarde() {
        try {
            String resultat = auditService.declencherSauvegarde(getOp());
            dateDerniereSauvegarde = LocalDateTime.now().format(FMT_SAUVEGARDE);
            chargerTout();
            sauvegardesDisponibles = auditService.listerSauvegardes();
            addMsg(FacesMessage.SEVERITY_INFO, "Sauvegarde déclenchée", resultat);
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur sauvegarde", ex.getMessage());
        }
    }

    // ── Rétention / Purge des logs ─────────────────────────────────────────
    /** Nombre de jours conservés (paramétrable depuis l'UI). */
    private int retentionJours = 180;
    /** Sauvegardes physiques détectées dans le dossier BACKUP_DIR. */
    private List<Map<String, Object>> sauvegardesDisponibles = new ArrayList<>();

    /** Purge les logs antérieurs à {@link #retentionJours} jours. */
    public void purgerLogsAnciens() {
        try {
            String res = auditService.purgerLogsAnciens(retentionJours, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Purge terminée", res);
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur purge", ex.getMessage());
        }
    }

    /** Rafraîchit la liste des fichiers de sauvegarde présents sur le serveur. */
    public void rafraichirSauvegardes() {
        try {
            sauvegardesDisponibles = auditService.listerSauvegardes();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public int  getRetentionJours()         { return retentionJours; }
    public void setRetentionJours(int j)    { this.retentionJours = j; }
    public List<Map<String, Object>> getSauvegardesDisponibles() {
        if (sauvegardesDisponibles == null || sauvegardesDisponibles.isEmpty()) {
            try { sauvegardesDisponibles = auditService.listerSauvegardes(); }
            catch (Exception ignored) { sauvegardesDisponibles = new ArrayList<>(); }
        }
        return sauvegardesDisponibles;
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    private String safe(String s) { return s != null ? s : ""; }

    public String getCssAction(String action) {
        if (action == null) return "";
        if (action.startsWith("CREATION_"))      return "text-success fw-semibold";
        if (action.startsWith("SUPPRESSION_"))   return "text-danger fw-semibold";
        if (action.startsWith("MODIFICATION_"))  return "text-warning";
        if ("CONNEXION".equals(action))          return "text-info";
        if (action.contains("ECHEC") || action.contains("BLOQUEE")) return "text-danger fw-bold";
        if ("DECONNEXION".equals(action))        return "text-secondary";
        if (action.startsWith("ACTIVATION_"))    return "text-success";
        if (action.startsWith("DESACTIVATION_")) return "text-warning";
        if (action.startsWith("DEVERROUILLAGE_"))return "text-info";
        return "";
    }

    public String getIconeAction(String action) {
        if (action == null) return "fa-circle";
        if (action.startsWith("CREATION_"))                          return "fa-plus-circle";
        if (action.startsWith("SUPPRESSION_"))                       return "fa-trash";
        if (action.startsWith("MODIFICATION_"))                      return "fa-edit";
        if ("CONNEXION".equals(action))                              return "fa-sign-in-alt";
        if (action.contains("ECHEC") || action.contains("BLOQUEE")) return "fa-exclamation-triangle";
        if ("DECONNEXION".equals(action))                            return "fa-sign-out-alt";
        if (action.startsWith("RESET_") || action.startsWith("DEVERROUILLAGE_")) return "fa-key";
        if (action.contains("CONGE"))       return "fa-calendar-times";
        if (action.contains("EVALUATION"))  return "fa-star";
        if (action.contains("CANDIDATURE")) return "fa-user-plus";
        if (action.contains("PAIE") || action.contains("BULLETIN")) return "fa-money-bill";
        if (action.contains("MUTATION"))    return "fa-exchange-alt";
        return "fa-circle";
    }

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

    public double getTauxEchecConnexion() {
        long total = connexionsReussies + connexionsEchouees;
        if (total == 0) return 0;
        return (double) connexionsEchouees / total * 100;
    }

    public String getCouleurSecurite() {
        double taux = getTauxEchecConnexion();
        if (taux < 5)  return "text-success";
        if (taux < 20) return "text-warning";
        return "text-danger";
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<LogAction> getLogsComplets()              { return logsComplets; }
    public List<LogAction> getLogsFiltres()               { return logsFiltres; }
    public List<LogAction> getLogsRecents()               { return logsRecents; }
    public LogAction       getLogSelectionne()            { return logSelectionne; }
    public void            setLogSelectionne(LogAction l) { this.logSelectionne = l; }

    public String    getFiltreUsername()                  { return filtreUsername; }
    public void      setFiltreUsername(String s)          { this.filtreUsername = s; }
    public String    getFiltreAction()                    { return filtreAction; }
    public void      setFiltreAction(String s)            { this.filtreAction = s; }
    public String    getFiltreIp()                        { return filtreIp; }
    public void      setFiltreIp(String s)                { this.filtreIp = s; }
    public LocalDate getFiltreDateDebut()                 { return filtreDateDebut; }
    public void      setFiltreDateDebut(LocalDate d)      { this.filtreDateDebut = d; }
    public LocalDate getFiltreDateFin()                   { return filtreDateFin; }
    public void      setFiltreDateFin(LocalDate d)        { this.filtreDateFin = d; }
    public String    getFiltrePeriode()                   { return filtrePeriode; }
    public void      setFiltrePeriode(String p)           { this.filtrePeriode = p; }

    public long getTotalActions()                         { return totalActions; }
    public long getConnexionsReussies()                   { return connexionsReussies; }
    public long getConnexionsEchouees()                   { return connexionsEchouees; }
    public long getActionsAdmin()                         { return actionsAdmin; }
    public long getActionsRH()                            { return actionsRH; }
    public long getActionsAujourdHui()                    { return actionsAujourdHui; }
    public long getUtilisateursDistincts()                { return utilisateursDistincts; }
    public long getNbLogsFiltres()                        { return logsFiltres != null ? logsFiltres.size() : 0; }

    /** CORRECTION : propriété manquante appelée par #{auditBean.dateDerniereSauvegarde} */
    public String getDateDerniereSauvegarde()             { return dateDerniereSauvegarde; }

    public List<Map.Entry<String, Long>> getTopUtilisateurs() { return topUtilisateurs; }
    public Map<String, Long> getRepartitionActions()       { return repartitionActions; }
    public Map<String, Long> getEvolutionConnexions()      { return evolutionConnexions; }

    public boolean isAfficherDetailsLog()                  { return afficherDetailsLog; }
    public void    setAfficherDetailsLog(boolean b)        { this.afficherDetailsLog = b; }
}