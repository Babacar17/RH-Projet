package com.soutenance.service;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service Health Check — agrège les métriques techniques pour le dashboard
 * système : état JVM, BDD, sauvegardes, scheduler, taille des tables principales.
 *
 * Aucune écriture, lecture seule.
 */
@Service
public class HealthCheckService {

    @Autowired private SessionFactory sessionFactory;
    @Autowired private AuditService   auditService;

    /** Retourne une map clé → valeur lisible pour l'affichage. */
    public Map<String, Object> getStatutSysteme() {
        Map<String, Object> out = new LinkedHashMap<>();

        // ── JVM ─────────────────────────────────────────────────────────────
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean  memory  = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        long uptimeMs = runtime.getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        out.put("jvmVersion",      System.getProperty("java.version"));
        out.put("jvmVendor",       System.getProperty("java.vendor"));
        out.put("uptime",          formatDuree(uptime));
        out.put("demarrage",       Instant.ofEpochMilli(runtime.getStartTime()).toString());
        out.put("processeurs",     os.getAvailableProcessors());
        out.put("osNom",           os.getName() + " " + os.getArch() + " " + os.getVersion());

        // ── Mémoire ─────────────────────────────────────────────────────────
        MemoryUsage heap = memory.getHeapMemoryUsage();
        long usedMb  = heap.getUsed() / (1024 * 1024);
        long maxMb   = heap.getMax()  / (1024 * 1024);
        long ratio   = maxMb > 0 ? (100L * usedMb / maxMb) : 0;
        out.put("memoireUtilisee", usedMb + " MB / " + maxMb + " MB (" + ratio + "%)");
        out.put("memoireRatio",    ratio);

        // ── Base de données ─────────────────────────────────────────────────
        out.put("bddStatut", testerBdd());
        out.put("dialecteHibernate", sessionFactory != null
            ? sessionFactory.getClass().getSimpleName() : "Indéterminé");

        // ── Sauvegardes ─────────────────────────────────────────────────────
        try {
            List<Map<String, Object>> sauv = auditService.listerSauvegardes();
            out.put("nbSauvegardes", sauv.size());
            if (!sauv.isEmpty()) {
                Map<String, Object> derniere = sauv.get(0);
                out.put("derniereSauvegarde", derniere.get("date"));
                out.put("derniereSauvegardeNom", derniere.get("nom"));
                out.put("derniereSauvegardeTaille", derniere.get("taille"));
            } else {
                out.put("derniereSauvegarde", "Aucune");
            }
        } catch (Exception ex) {
            out.put("nbSauvegardes", "?");
            out.put("derniereSauvegarde", "Erreur : " + ex.getMessage());
        }

        // ── Espace disque ───────────────────────────────────────────────────
        try {
            Path racine = Paths.get(".").toAbsolutePath();
            java.io.File f = racine.toFile();
            long total = f.getTotalSpace() / (1024L * 1024 * 1024);
            long libre = f.getUsableSpace() / (1024L * 1024 * 1024);
            long pct   = total > 0 ? (100L * (total - libre) / total) : 0;
            out.put("disqueTotal", total + " GB");
            out.put("disqueLibre", libre + " GB");
            out.put("disqueRatio", pct);
        } catch (Exception ex) {
            out.put("disqueTotal", "?");
        }

        return out;
    }

    /** Liste des tables principales avec leur nombre de lignes. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStatistiquesBdd() {
        List<Map<String, Object>> out = new ArrayList<>();
        String[][] tables = {
            {"users",                  "Utilisateurs"},
            {"employes",               "Employés"},
            {"institutions",           "Institutions"},
            {"roles",                  "Rôles"},
            {"permissions",            "Permissions"},
            {"candidatures",           "Candidatures"},
            {"offres_emploi",          "Offres d'emploi"},
            {"demandes_conge",         "Demandes congé"},
            {"formations",             "Formations"},
            {"evaluations",            "Évaluations"},
            {"mouvements_carriere",    "Mouvements carrière"},
            {"bulletins_paie",         "Bulletins paie"},
            {"cycles_paie",            "Cycles paie"},
            {"grilles_salariales",     "Grilles salariales"},
            {"presences",              "Présences"},
            {"documents",              "Documents GED"},
            {"evenements",             "Événements"},
            {"participations_evenement", "Participations événement"},
            {"notifications",          "Notifications"},
            {"log_actions",            "Logs audit"}
        };

        for (String[] t : tables) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("table",   t[0]);
            row.put("libelle", t[1]);
            row.put("count",   compterTable(t[0]));
            out.add(row);
        }
        return out;
    }

    /** Liste les dernières actions techniques (scheduler, sauvegardes, purges). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActivitesTechniques() {
        try (Session s = sessionFactory.openSession()) {
            List<Object[]> raws = s.createQuery(
                "select l.action, l.detail, l.dateAction from LogAction l " +
                "where l.username = 'SCHEDULER' or l.action like 'SAUVEGARDE%' " +
                "or l.action like 'PURGE%' or l.action like 'EVENEMENT_%' " +
                "order by l.dateAction desc",
                Object[].class).setMaxResults(20).list();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object[] r : raws) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("action", r[0]);
                row.put("detail", r[1]);
                row.put("date",   r[2]);
                out.add(row);
            }
            return out;
        } catch (Exception ex) { return new ArrayList<>(); }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String testerBdd() {
        try (Session s = sessionFactory.openSession()) {
            s.createNativeQuery("SELECT 1", Integer.class).getSingleResult();
            return "OK";
        } catch (Exception ex) {
            return "KO : " + ex.getMessage();
        }
    }

    private long compterTable(String table) {
        try (Session s = sessionFactory.openSession()) {
            Object res = s.createNativeQuery(
                "SELECT COUNT(*) FROM " + table, Number.class).getSingleResult();
            return res != null ? ((Number) res).longValue() : 0;
        } catch (Exception ex) { return -1; }
    }

    private String formatDuree(Duration d) {
        long days  = d.toDays();
        long hours = d.toHoursPart();
        long mins  = d.toMinutesPart();
        if (days > 0)  return days + "j " + hours + "h " + mins + "min";
        if (hours > 0) return hours + "h " + mins + "min";
        return mins + " minutes";
    }
}
