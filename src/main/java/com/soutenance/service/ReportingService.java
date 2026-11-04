package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de reporting — périmètre RH / institution.
 *
 * Couvre :
 *  - Compteurs analytiques (effectifs, congés, candidatures, formations…)
 *  - Indicateurs clés (masse salariale, taux d'absentéisme, turnover)
 *  - Répartition par niveau hiérarchique et statut
 *  - Évolution temporelle des effectifs et de l'absentéisme
 *  - Exports CSV, Excel et PDF
 *  - Comparaison masse salariale inter-institutions
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    @Autowired private EmployeDao           employeDao;
    @Autowired private DemandeCongeDao      demandeCongeDao;
    @Autowired private CandidatureDao       candidatureDao;
    @Autowired private OffreEmploiDao       offreEmploiDao;
    @Autowired private FormationDao         formationDao;
    @Autowired private BulletinPaieDao      bulletinDao;
    @Autowired private MouvementCarriereDao mouvementDao;
    @Autowired private InstitutionDao       institutionDao;

    // ══════════════════════════════════════════════════
    // COMPTEURS EFFECTIFS
    // ══════════════════════════════════════════════════

    public long countTotalEmployes(Long institutionId) {
        return employeDao.findByInstitution(institutionId).size();
    }

    public long countEmployesActifs(Long institutionId) {
        return employeDao.findByInstitutionAndStatut(institutionId, "ACTIF").size();
    }

    // ══════════════════════════════════════════════════
    // COMPTEURS CONGÉS
    // ══════════════════════════════════════════════════

    public long countDemandesConge(Long institutionId, LocalDate debut, LocalDate fin) {
        return filtrerParPeriode(demandeCongeDao.findByInstitution(institutionId), debut, fin).size();
    }

    public long countDemandesCongeApprouvees(Long institutionId, LocalDate debut, LocalDate fin) {
        return filtrerParPeriode(demandeCongeDao.findByStatutAndInstitution("APPROUVE", institutionId),
            debut, fin).size();
    }

    public long countDemandesCongeRefusees(Long institutionId, LocalDate debut, LocalDate fin) {
        return filtrerParPeriode(demandeCongeDao.findByStatutAndInstitution("REFUSE", institutionId),
            debut, fin).size();
    }

    public double getMoyenneJoursConge(Long institutionId, LocalDate debut, LocalDate fin) {
        List<DemandeConge> approuvees = filtrerParPeriode(
            demandeCongeDao.findByStatutAndInstitution("APPROUVE", institutionId), debut, fin);
        if (approuvees.isEmpty()) return 0.0;
        return approuvees.stream()
            .mapToInt(d -> d.getNombreJours() != null ? d.getNombreJours() : 0)
            .average().orElse(0.0);
    }

    // ══════════════════════════════════════════════════
    // COMPTEURS RECRUTEMENT
    // ══════════════════════════════════════════════════

    public long countCandidatures(Long institutionId, LocalDate debut, LocalDate fin) {
        return candidatureDao.findByInstitution(institutionId).stream()
            .filter(c -> c.getDateCandidature() != null
                && !c.getDateCandidature().toLocalDate().isBefore(debut)
                && !c.getDateCandidature().toLocalDate().isAfter(fin))
            .count();
    }

    public long countCandidaturesRetenues(Long institutionId, LocalDate debut, LocalDate fin) {
        return candidatureDao.findByInstitution(institutionId).stream()
            .filter(c -> "RETENUE".equals(c.getStatut())
                && c.getDateCandidature() != null
                && !c.getDateCandidature().toLocalDate().isBefore(debut)
                && !c.getDateCandidature().toLocalDate().isAfter(fin))
            .count();
    }

    public long countRecrutements(Long institutionId, LocalDate debut, LocalDate fin) {
        return mouvementDao.findByInstitution(institutionId).stream()
            .filter(m -> "RECRUTEMENT".equals(m.getTypeMouvement())
                && m.getDateMouvement() != null
                && !m.getDateMouvement().isBefore(debut)
                && !m.getDateMouvement().isAfter(fin))
            .count();
    }

    public long countDeparts(Long institutionId, LocalDate debut, LocalDate fin) {
        return mouvementDao.findByInstitution(institutionId).stream()
            .filter(m -> ("DEPART".equals(m.getTypeMouvement()) || "RETRAITE".equals(m.getTypeMouvement()))
                && m.getDateMouvement() != null
                && !m.getDateMouvement().isBefore(debut)
                && !m.getDateMouvement().isAfter(fin))
            .count();
    }

    public long countOffresPubliees(Long institutionId, LocalDate debut, LocalDate fin) {
        return offreEmploiDao.findByInstitution(institutionId).stream()
            .filter(o -> "PUBLIEE".equals(o.getStatut())
                && o.getDatePublication() != null
                && !o.getDatePublication().isBefore(debut)
                && !o.getDatePublication().isAfter(fin))
            .count();
    }

    // ══════════════════════════════════════════════════
    // FORMATIONS
    // ══════════════════════════════════════════════════

    public long countFormationsRealisees(Long institutionId, LocalDate debut, LocalDate fin) {
        return formationDao.findByInstitution(institutionId).stream()
            .filter(f -> "REALISEE".equals(f.getStatut())
                && f.getDateFin() != null
                && !f.getDateFin().isBefore(debut)
                && !f.getDateFin().isAfter(fin))
            .count();
    }

    public long countSalariesFormes(Long institutionId, LocalDate debut, LocalDate fin) {
        return formationDao.findByInstitution(institutionId).stream()
            .filter(f -> f.getDateFin() != null
                && !f.getDateFin().isBefore(debut)
                && !f.getDateFin().isAfter(fin))
            .flatMap(f -> f.getParticipants().stream())
            .map(Employe::getId)
            .distinct()
            .count();
    }

    // ══════════════════════════════════════════════════
    // INDICATEURS SALARIAUX
    // ══════════════════════════════════════════════════

    public BigDecimal getMasseSalariale(Long institutionId) {
        return bulletinDao.findByInstitution(institutionId).stream()
            .filter(b -> b.getSalaireNet() != null
                && ("VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut())))
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getSalaireMoyen(Long institutionId) {
        List<Employe> employes = employeDao.findByInstitutionAndStatut(institutionId, "ACTIF");
        if (employes.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = employes.stream()
            .filter(e -> e.getSalaireBase() != null)
            .map(Employe::getSalaireBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(employes.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    // ══════════════════════════════════════════════════
    // TAUX
    // ══════════════════════════════════════════════════

    public double calculerTauxAbsenteisme(Long institutionId, LocalDate debut, LocalDate fin) {
        long total = countTotalEmployes(institutionId);
        if (total == 0) return 0.0;
        long absents = countDemandesCongeApprouvees(institutionId, debut, fin);
        long jours = debut.until(fin).getDays() + 1;
        return jours > 0 ? (double) absents / (total * jours) * 100 : 0.0;
    }

    public double calculerTauxTurnover(Long institutionId, LocalDate debut, LocalDate fin) {
        long total = countTotalEmployes(institutionId);
        if (total == 0) return 0.0;
        long departs = countDeparts(institutionId, debut, fin);
        long entrees = countRecrutements(institutionId, debut, fin);
        return (double)(departs + entrees) / 2 / total * 100;
    }

    // ══════════════════════════════════════════════════
    // RÉPARTITIONS
    // ══════════════════════════════════════════════════

    public Map<String, Long> getRepartitionStatuts(Long institutionId) {
        return employeDao.findByInstitution(institutionId).stream()
            .collect(Collectors.groupingBy(
                e -> e.getStatut() != null ? e.getStatut() : "INCONNU",
                Collectors.counting()));
    }

    public Map<String, Long> getRepartitionNiveaux(Long institutionId) {
        return employeDao.findByInstitution(institutionId).stream()
            .collect(Collectors.groupingBy(
                e -> e.getNiveauHierarchique() != null ? e.getNiveauHierarchique() : "EMPLOYE",
                Collectors.counting()));
    }

    // ══════════════════════════════════════════════════
    // ÉVOLUTIONS TEMPORELLES
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION : retourne Map<String, Long> (nom du mois → effectif)
     * au lieu de Map<Integer, Long>, pour correspondre au type attendu
     * par ReportingBean (Map<String, Long> evolutionEffectifs).
     */
    public Map<String, Long> getEvolutionEffectifs(Long institutionId, int annee) {
        Map<String, Long> evolution = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            String label = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            evolution.put(label, countTotalEmployes(institutionId));
        }
        return evolution;
    }

    /**
     * CORRECTION : retourne Map<String, Long> (nom du mois → taux arrondi)
     * au lieu de Map<Integer, Double>, pour correspondre au type attendu
     * par ReportingBean (Map<String, Long> evolutionAbsenteisme).
     */
    public Map<String, Long> getEvolutionAbsenteisme(Long institutionId, int annee) {
        Map<String, Long> evolution = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            String label    = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            LocalDate debut = LocalDate.of(annee, m, 1);
            LocalDate fin   = debut.withDayOfMonth(debut.lengthOfMonth());
            double taux     = calculerTauxAbsenteisme(institutionId, debut, fin);
            evolution.put(label, Math.round(taux));
        }
        return evolution;
    }

    // ══════════════════════════════════════════════════
    // COMPARAISON INTER-INSTITUTIONS
    // ══════════════════════════════════════════════════

    public Map<String, BigDecimal> getComparaisonMasseSalariale() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Institution inst : institutionDao.findAll()) {
            result.put(inst.getNom(), getMasseSalariale(inst.getId()));
        }
        return result;
    }

    // ══════════════════════════════════════════════════
    // EXPORTS — surcharges avec (typeRapport, institutionId, debut, fin)
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION : ReportingBean appelle genererExcel(String, Long, LocalDate, LocalDate).
     * La signature originale n'acceptait que List<Employe>.
     * Cette surcharge résout la liste selon le typeRapport et délègue.
     */
    public byte[] genererExcel(String typeRapport, Long institutionId,
                               LocalDate debut, LocalDate fin) {
        List<Employe> employes = getEmployesPourRapport(typeRapport, institutionId, debut, fin);
        return genererExcel(employes);
    }

    /**
     * CORRECTION : ReportingBean appelle genererPdf(String, Long, LocalDate, LocalDate).
     * La signature originale n'acceptait que List<Employe>.
     * Cette surcharge résout la liste selon le typeRapport et délègue.
     */
    public byte[] genererPdf(String typeRapport, Long institutionId,
                             LocalDate debut, LocalDate fin) {
        List<Employe> employes = getEmployesPourRapport(typeRapport, institutionId, debut, fin);
        return genererPdf(employes);
    }

    /**
     * CORRECTION : cohérence avec genererExcel et genererPdf —
     * surcharge ajoutée pour genererCsv également.
     */
    public byte[] genererCsv(String typeRapport, Long institutionId,
                             LocalDate debut, LocalDate fin) {
        List<Employe> employes = getEmployesPourRapport(typeRapport, institutionId, debut, fin);
        return genererCsv(employes);
    }

    /**
     * Résout la liste d'employés à inclure dans un rapport selon son type.
     */
    private List<Employe> getEmployesPourRapport(String typeRapport, Long institutionId,
                                                  LocalDate debut, LocalDate fin) {
        if ("EFFECTIFS".equals(typeRapport) || typeRapport == null) {
            return employeDao.findByInstitution(institutionId);
        }
        // Pour les autres types de rapport, on exporte les employés actifs sur la période
        return employeDao.findByInstitutionAndStatut(institutionId, "ACTIF");
    }

    // ══════════════════════════════════════════════════
    // EXPORTS — signatures originales (List<Employe>)
    // ══════════════════════════════════════════════════

    public byte[] genererCsv(List<Employe> employes) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Matricule;Nom;Prénom;Statut;Poste;Service;Salaire base\n");
        for (Employe e : employes) {
            sb.append(safe(e.getMatricule())).append(";")
              .append(safe(e.getNom())).append(";")
              .append(safe(e.getPrenom())).append(";")
              .append(safe(e.getStatut())).append(";")
              .append(e.getPoste() != null ? safe(e.getPoste().getIntitule()) : "").append(";")
              .append(e.getServiceOrg() != null ? safe(e.getServiceOrg().getNom()) : "").append(";")
              .append(e.getSalaireBase() != null ? e.getSalaireBase() : "").append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] genererExcel(List<Employe> employes) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Employés");
            String[] cols = {"Matricule","Nom","Prénom","Statut","Poste","Salaire base"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            int r = 1;
            for (Employe e : employes) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(safe(e.getMatricule()));
                row.createCell(1).setCellValue(safe(e.getNom()));
                row.createCell(2).setCellValue(safe(e.getPrenom()));
                row.createCell(3).setCellValue(safe(e.getStatut()));
                row.createCell(4).setCellValue(e.getPoste() != null ? safe(e.getPoste().getIntitule()) : "");
                row.createCell(5).setCellValue(
                    e.getSalaireBase() != null ? e.getSalaireBase().doubleValue() : 0);
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération Excel : " + ex.getMessage(), ex);
        }
    }

    /** Génération PDF simplifiée (texte brut). À remplacer par iText/JasperReports. */
    public byte[] genererPdf(List<Employe> employes) {
        StringBuilder sb = new StringBuilder("RAPPORT EFFECTIFS\n\n");
        sb.append("Total : ").append(employes.size()).append(" employés\n\n");
        for (Employe e : employes) {
            sb.append(e.getMatricule()).append(" | ")
              .append(e.getNomComplet()).append(" | ")
              .append(safe(e.getStatut())).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════

    private <T extends DemandeConge> List<T> filtrerParPeriode(List<T> liste,
            LocalDate debut, LocalDate fin) {
        if (debut == null && fin == null) return liste;
        return liste.stream()
            .filter(d -> {
                LocalDate date = d.getDateDebut();
                if (date == null) return false;
                if (debut != null && date.isBefore(debut)) return false;
                if (fin   != null && date.isAfter(fin))   return false;
                return true;
            })
            .collect(Collectors.toList());
    }

    private String safe(String s) { return s != null ? s : ""; }
}