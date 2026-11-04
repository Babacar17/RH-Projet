package com.soutenance.service;

import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.LogAction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'audit et de supervision des logs.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. declencherSauvegarde() → retourne maintenant la date/heure formatée
 *    pour que AuditBean puisse alimenter #{auditBean.dateDerniereSauvegarde}.
 *
 * 2. getRepartitionActions() → normalise les codes d'action pour regrouper
 *    les variantes ("ECHEC_CONNEXION" + "CONNEXION_ECHEC" → même compteur).
 *
 * 3. getTopUtilisateurs() → exclut "SCHEDULER" des utilisateurs listés
 *    (actions techniques automatiques ne doivent pas polluer le top).
 *
 * 4. getEvolutionConnexions() → couvre les deux formes de code connexion
 *    réussie ("CONNEXION", "LOGIN") pour rétrocompatibilité.
 *
 * 5. Nouvelle méthode getStatsSécurité() → agrège les indicateurs de
 *    sécurité en une seule structure pour le dashboard.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    @Autowired private LogActionDao logActionDao;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FMT_SAUVEGARDE =
        DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    // ══════════════════════════════════════════════════
    // STATISTIQUES
    // ══════════════════════════════════════════════════

    /**
     * Répartition des actions par catégorie.
     *
     * CORRECTION : normalise les variantes de codes pour éviter les doublons
     * dans l'interface (ex : CONNEXION_ECHEC et ECHEC_CONNEXION → ECHEC_CONNEXION).
     */
    public Map<String, Long> getRepartitionActions() {
        return logActionDao.findAll().stream()
            .filter(l -> l.getAction() != null)
            .collect(Collectors.groupingBy(
                l -> normaliserAction(l.getAction()),
                Collectors.counting()
            ));
    }

    /**
     * Normalise les codes d'action pour regrouper les variantes historiques.
     */
    private String normaliserAction(String action) {
        if (action == null) return "AUTRE";
        // Unifier les codes connexion échouée (ancienne et nouvelle forme)
        if ("CONNEXION_ECHEC".equals(action)
            || "ECHEC_CONNEXION".equals(action)
            || "CONNEXION_BLOQUEE".equals(action)
            || "COMPTE_VERROUILLE".equals(action))
            return "ECHEC_CONNEXION";
        // Unifier les codes connexion réussie
        if ("CONNEXION".equals(action) || "LOGIN".equals(action))
            return "CONNEXION";
        return action;
    }

    /**
     * Top N utilisateurs par nombre d'actions.
     *
     * CORRECTION : exclut "SCHEDULER" (actions techniques automatiques).
     */
    public List<Map.Entry<String, Long>> getTopUtilisateurs(int n) {
        Map<String, Long> countByUser = logActionDao.findAll().stream()
            .filter(l -> l.getUsername() != null
                && !"SCHEDULER".equals(l.getUsername())
                && !"SYSTEM".equals(l.getUsername()))
            .collect(Collectors.groupingBy(LogAction::getUsername, Collectors.counting()));
        return countByUser.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    /**
     * Évolution des connexions réussies par jour sur N jours.
     *
     * CORRECTION : couvre "CONNEXION" et "LOGIN" pour rétrocompatibilité.
     */
    public Map<String, Long> getEvolutionConnexions(int dernierJours) {
        LocalDateTime limite = LocalDateTime.now().minusDays(dernierJours);
        return logActionDao.findAll().stream()
            .filter(l -> (
                    "CONNEXION".equals(l.getAction())
                    || "LOGIN".equals(l.getAction())
                )
                && l.getDateAction() != null
                && l.getDateAction().isAfter(limite))
            .collect(Collectors.groupingBy(
                l -> l.getDateAction().toLocalDate().toString(),
                Collectors.counting()
            ));
    }

    /**
     * Indicateurs de sécurité agrégés pour le dashboard.
     * Retourne une Map avec les clés :
     *   connexionsReussies, connexionsEchouees, comptesBloques,
     *   actionsAujourdHui, tauxEchec
     */
    public Map<String, Object> getStatsSécurité() {
        List<LogAction> tous = logActionDao.findAll();
        long reussies = tous.stream()
            .filter(l -> "CONNEXION".equals(normaliserAction(l.getAction())))
            .count();
        long echouees = tous.stream()
            .filter(l -> "ECHEC_CONNEXION".equals(normaliserAction(l.getAction())))
            .count();
        long bloques = tous.stream()
            .filter(l -> "COMPTE_VERROUILLE".equals(l.getAction()))
            .count();
        long aujourdhui = tous.stream()
            .filter(l -> l.getDateAction() != null
                && l.getDateAction().toLocalDate().equals(java.time.LocalDate.now()))
            .count();
        double tauxEchec = (reussies + echouees) == 0 ? 0
            : (double) echouees / (reussies + echouees) * 100;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("connexionsReussies", reussies);
        stats.put("connexionsEchouees", echouees);
        stats.put("comptesBloques",     bloques);
        stats.put("actionsAujourdHui",  aujourdhui);
        stats.put("tauxEchec",          Math.round(tauxEchec * 10.0) / 10.0);
        return stats;
    }

    // ══════════════════════════════════════════════════
    // EXPORT EXCEL
    // ══════════════════════════════════════════════════

    @Transactional
    public byte[] exporterLogsExcel(List<LogAction> logs, String operateur) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Journal d'audit");

            // Style en-têtes
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] cols = { "ID", "Date / Heure", "Utilisateur", "Action",
                              "Détail", "Entité", "ID entité", "Adresse IP" };
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données
            int rowNum = 1;
            for (LogAction l : logs) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(l.getId() != null ? l.getId() : 0);
                row.createCell(1).setCellValue(
                    l.getDateAction() != null ? l.getDateAction().format(FMT) : "");
                row.createCell(2).setCellValue(safe(l.getUsername()));
                row.createCell(3).setCellValue(safe(normaliserAction(l.getAction())));
                row.createCell(4).setCellValue(safe(l.getDetail()));
                row.createCell(5).setCellValue(safe(l.getEntite()));
                row.createCell(6).setCellValue(
                    l.getEntiteId() != null ? l.getEntiteId() : 0);
                row.createCell(7).setCellValue(safe(l.getAdresseIp()));
            }

            // Ligne totaux
            Row totaux = sheet.createRow(rowNum + 1);
            totaux.createCell(0).setCellValue("Total lignes exportées : " + logs.size());

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            logActionDao.save(LogAction.of(operateur, "EXPORT_LOGS_EXCEL",
                logs.size() + " ligne(s) exportée(s)"));
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération Excel : " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════
    // SAUVEGARDE
    // ══════════════════════════════════════════════════

    /**
     * Lance une sauvegarde réelle de la base PostgreSQL via pg_dump.
     *
     * Le fichier est écrit dans BACKUP_DIR (configurable via la variable
     * d'environnement SOUTENANCE_BACKUP_DIR, valeur par défaut :
     * java.io.tmpdir/soutenance-backups). Le nom du fichier inclut la date
     * et l'heure pour éviter les écrasements.
     *
     * pg_dump doit être présent dans le PATH du serveur. Les identifiants
     * de connexion sont lus dans les variables d'environnement
     * SOUTENANCE_DB_*, sinon les valeurs par défaut localhost/soutenance_db
     * sont utilisées.
     *
     * En cas d'échec, la méthode logue l'erreur et retourne un message
     * d'erreur lisible — elle ne lance pas d'exception.
     */
    @Transactional
    public String declencherSauvegarde(String operateur) {
        String dateHeure = LocalDateTime.now().format(FMT_SAUVEGARDE);
        String horodate  = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        Path backupDir = backupDirectory();
        Path target    = backupDir.resolve("soutenance_db_" + horodate + ".sql");

        try {
            Files.createDirectories(backupDir);

            String host = envOrDefault("SOUTENANCE_DB_HOST", "localhost");
            String port = envOrDefault("SOUTENANCE_DB_PORT", "5432");
            String db   = envOrDefault("SOUTENANCE_DB_NAME", "soutenance_db");
            String user = envOrDefault("SOUTENANCE_DB_USER", "postgres");
            String pass = envOrDefault("SOUTENANCE_DB_PASSWORD", "Basse");

            ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "-h", host,
                "-p", port,
                "-U", user,
                "-F", "p",           // format SQL plain
                "-f", target.toString(),
                db
            );
            pb.environment().put("PGPASSWORD", pass);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (stderr.length() < 2000) stderr.append(line).append('\n');
                }
            }
            int code = p.waitFor();

            if (code == 0 && Files.exists(target) && Files.size(target) > 0) {
                long tailleKo = Files.size(target) / 1024L;
                logActionDao.save(LogAction.of(operateur, "SAUVEGARDE_MANUELLE",
                    "Sauvegarde OK le " + dateHeure
                    + " — fichier " + target.getFileName() + " (" + tailleKo + " Ko)"));
                return "Sauvegarde réussie le " + dateHeure
                       + " — fichier : " + target.getFileName();
            }

            // Échec : on conserve la stderr en log d'audit pour diagnostic
            String erreur = stderr.toString().trim();
            if (erreur.length() > 500) erreur = erreur.substring(0, 500) + "…";
            logActionDao.save(LogAction.of(operateur, "SAUVEGARDE_ECHEC",
                "Échec pg_dump (code " + code + ") le " + dateHeure
                + " — " + (erreur.isEmpty() ? "raison inconnue" : erreur)));
            return "Échec de la sauvegarde (pg_dump code " + code + "). "
                 + "Vérifiez que pg_dump est installé et accessible sur le serveur.";

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            logActionDao.save(LogAction.of(operateur, "SAUVEGARDE_ECHEC",
                "Exception : " + ex.getClass().getSimpleName() + " — " + ex.getMessage()));
            return "Erreur technique : " + ex.getMessage();
        }
    }

    /**
     * Purge les logs antérieurs à N jours.
     * Garde une trace d'audit de la purge effectuée.
     */
    @Transactional
    public String purgerLogsAnciens(int nbJours, String operateur) {
        if (nbJours < 1) return "Le seuil doit être au minimum 1 jour.";
        LocalDateTime seuil = LocalDateTime.now().minusDays(nbJours);
        long nbAvant = logActionDao.countAvant(seuil);
        if (nbAvant <= 0) {
            return "Aucun log à purger (rétention " + nbJours + " jours).";
        }
        int supprimes = logActionDao.purgerAvant(seuil);
        if (supprimes < 0) return "Erreur technique lors de la purge.";
        // On loggue après la purge pour garder une trace
        logActionDao.save(LogAction.of(operateur, "PURGE_LOGS",
            "Purge des logs antérieurs au " + seuil.format(FMT_SAUVEGARDE)
            + " — " + supprimes + " entrée(s) supprimée(s)"));
        return supprimes + " log(s) supprimé(s) (antérieurs à " + nbJours + " jours).";
    }

    /** Liste les sauvegardes présentes dans BACKUP_DIR (du plus récent au plus ancien). */
    public List<Map<String, Object>> listerSauvegardes() {
        List<Map<String, Object>> out = new ArrayList<>();
        Path dir = backupDirectory();
        if (!Files.isDirectory(dir)) return out;
        try {
            Files.list(dir)
                .filter(f -> f.getFileName().toString().endsWith(".sql"))
                .sorted(Comparator.comparingLong(
                    (Path f) -> {
                        try { return Files.getLastModifiedTime(f).toMillis(); }
                        catch (Exception e) { return 0L; }
                    }).reversed())
                .forEach(f -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nom",   f.getFileName().toString());
                    row.put("chemin", f.toString());
                    try {
                        row.put("taille",
                            (Files.size(f) / 1024L) + " Ko");
                        row.put("date",
                            LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(f).toInstant(),
                                java.time.ZoneId.systemDefault())
                            .format(FMT));
                    } catch (Exception ignored) { /* skip */ }
                    out.add(row);
                });
        } catch (IOException ignored) { /* dir inaccessible */ }
        return out;
    }

    private static Path backupDirectory() {
        String dir = envOrDefault("SOUTENANCE_BACKUP_DIR",
            Paths.get(System.getProperty("java.io.tmpdir"),
                      "soutenance-backups").toString());
        return Paths.get(dir);
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    // ══════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════

    private String safe(String s) { return s != null ? s : ""; }
}