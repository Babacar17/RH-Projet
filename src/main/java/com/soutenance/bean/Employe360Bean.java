package com.soutenance.bean;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import com.soutenance.entity.Service;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue 360° d'un employé : agrège en un seul écran l'identité, le contrat,
 * la carrière, les congés, les évaluations, les formations, les événements
 * et les documents.
 *
 * URL : /Soutenance/ResponsableRH/employe-360.xhtml?id=42
 */
@Component("employe360Bean")
@Scope("view")
public class Employe360Bean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private EmployeDao            employeDao;
    @Autowired(required = false) private DemandeCongeDao       demandeCongeDao;
    @Autowired(required = false) private EvaluationDao         evaluationDao;
    @Autowired(required = false) private FormationDao          formationDao;
    @Autowired(required = false) private MouvementCarriereDao  mouvementCarriereDao;
    @Autowired(required = false) private BulletinPaieDao       bulletinPaieDao;
    @Autowired(required = false) private SoldeCongeDao         soldeCongeDao;
    @Autowired(required = false) private com.soutenance.dao.EvenementDao evenementDao;

    private Long             employeId;
    private Employe          employe;

    // Agrégations
    private List<DemandeConge>       conges        = new ArrayList<>();
    private List<Evaluation>         evaluations   = new ArrayList<>();
    private List<Formation>          formations    = new ArrayList<>();
    private List<MouvementCarriere>  mouvements    = new ArrayList<>();
    private List<BulletinPaie>       bulletins     = new ArrayList<>();
    private List<SoldeConge>         soldes        = new ArrayList<>();
    private List<Evenement>          evenements    = new ArrayList<>();

    @PostConstruct
    public void init() { /* attendre setEmployeId via viewParam */ }

    public void chargerDonnees() {
        if (employeId == null) return;
        try {
            employe = employeDao.findById(employeId);
            if (employe == null) return;

            // Congés
            if (demandeCongeDao != null) {
                conges = demandeCongeDao.findByEmployeId(employeId);
            }
            // Évaluations
            if (evaluationDao != null) {
                evaluations = evaluationDao.findByEmployeId(employeId);
            }
            // Formations
            if (formationDao != null) {
                formations = formationDao.findByEmployeId(employeId);
            }
            // Mouvements de carrière
            if (mouvementCarriereDao != null) {
                mouvements = mouvementCarriereDao.findByEmploye(employeId);
            }
            // Bulletins de paie
            if (bulletinPaieDao != null) {
                bulletins = bulletinPaieDao.findByEmploye(employeId);
            }
            // Soldes de congés (année en cours)
            if (soldeCongeDao != null) {
                soldes = soldeCongeDao.findByEmploye(employeId);
            }
            // Événements
            if (evenementDao != null) {
                evenements = evenementDao.findByParticipant(employeId);
            }
        } catch (Exception ignored) { /* page robuste */ }
    }

    // ── KPI synthèse ────────────────────────────────────────────────────────

    public int getNombreCongesPris() {
        if (conges == null) return 0;
        return (int) conges.stream()
            .filter(c -> "APPROUVE".equalsIgnoreCase(c.getStatut())).count();
    }

    public int getNombreFormations() {
        return formations != null ? formations.size() : 0;
    }

    public int getNombreEvaluations() {
        return evaluations != null ? evaluations.size() : 0;
    }

    public int getNombreEvenements() {
        return evenements != null ? evenements.size() : 0;
    }

    public BigDecimal getMoyenneEvaluations() {
        if (evaluations == null || evaluations.isEmpty()) return BigDecimal.ZERO;
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        int n = 0;
        for (Evaluation e : evaluations) {
            if (e.getNoteGlobale() != null) {
                total = total.add(e.getNoteGlobale());
                n++;
            }
        }
        if (n == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(n), 1, java.math.RoundingMode.HALF_UP);
    }

    public int getAncienneteAnnees() {
        if (employe == null || employe.getDateEmbauche() == null) return 0;
        return java.time.Period.between(employe.getDateEmbauche(), LocalDate.now()).getYears();
    }

    // ── Helpers UI ──────────────────────────────────────────────────────────

    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut.toUpperCase()) {
            case "APPROUVE", "VALIDEE", "TERMINEE" -> "badge-success";
            case "REFUSE", "ANNULE", "ANNULEE"     -> "badge-danger";
            case "EN_ATTENTE", "EN_ATTENTE_RH",
                 "EN_COURS", "PROGRAMME"           -> "badge-info";
            case "CONFIRME", "RECRUTEE"            -> "badge-primary";
            default                                -> "badge-secondary";
        };
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long       getEmployeId()              { return employeId; }
    public void       setEmployeId(Long id)       { this.employeId = id; }
    public Employe    getEmploye()                { return employe; }

    public List<DemandeConge>       getConges()        { return conges; }
    public List<Evaluation>         getEvaluations()   { return evaluations; }
    public List<Formation>          getFormations()    { return formations; }
    public List<MouvementCarriere>  getMouvements()    { return mouvements; }
    public List<BulletinPaie>       getBulletins()     { return bulletins; }
    public List<SoldeConge>         getSoldes()        { return soldes; }
    public List<Evenement>          getEvenements()    { return evenements; }
}
