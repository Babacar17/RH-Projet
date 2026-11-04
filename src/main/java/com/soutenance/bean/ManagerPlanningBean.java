package com.soutenance.bean;

import com.soutenance.dao.EmployeDao;
import com.soutenance.entity.DemandeConge;
import com.soutenance.entity.Employe;
import com.soutenance.entity.EquipeService;
import com.soutenance.entity.ExceptionPlanning;
import com.soutenance.entity.PlageHoraireService;
import com.soutenance.entity.PlanningPresence;
import com.soutenance.entity.Service;
import com.soutenance.service.ManagerService;
import com.soutenance.service.PlanningEquipeService;
import com.soutenance.service.PresenceService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bean JSF derrière planning.xhtml (espace Responsable Hiérarchique) :
 * calendrier hebdomadaire éditable du SEUL service du chef de service
 * connecté (CHEF_SERVICE), tous régimes confondus — FIXE ou EQUIPES. Le
 * chef gère à la fois le planning de base de son équipe (plannings
 * individuels en régime FIXE, plages/équipes/affectations en régime
 * EQUIPES) et les exceptions ponctuelles au jour le jour (remplacement,
 * échange, absence, congé). RH ne garde que le choix du régime du service
 * et une vue de supervision multi-services (RHHorairesBean, espace RH).
 */
@Component("managerPlanningBean")
@Scope("view")
public class ManagerPlanningBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private PlanningEquipeService planningEquipeService;
    @Autowired private PresenceService presenceService;
    @Autowired private ManagerService managerService;
    @Autowired private EmployeDao employeDao;
    @Autowired private SessionBean sessionBean;

    private Service monService;
    private boolean regimeEquipes;
    private LocalDate semaineDebut = lundiDe(LocalDate.now());
    private List<LocalDate> joursSemaine = new ArrayList<>();

    private List<Employe> employesDuService = new ArrayList<>();
    private List<EquipeService> equipes = new ArrayList<>();
    private List<PlageHoraireService> plages = new ArrayList<>();
    private Map<Long, List<Employe>> membresParEquipe = new HashMap<>();
    private Map<Long, Long> equipeParEmploye = new HashMap<>();
    private List<PlanningPresence> planningsFixe = new ArrayList<>();
    private Set<String> congeKeys = new HashSet<>();
    private Map<String, ExceptionPlanning> exceptionsSemaine = new HashMap<>();
    private Set<LocalDate> joursFeriesSemaine = new HashSet<>();

    private final Map<String, PlanningEquipeService.CelluleHoraire> celluleCache = new HashMap<>();

    // ── Dialogue exception ponctuelle ───────────────────────────────────
    private Long excEmployeId;
    private LocalDate excDate;
    private String excType = "ABSENCE";
    private Long excPlageId;
    private String excCommentaire;
    private boolean dlgExcOuvert = false;

    // ── Configuration du régime équipes (plages, équipes, date de référence) ──
    private boolean dlgConfigOuvert = false;
    private LocalDate configDateReference;
    private PlageHoraireService plageEnEdition;
    private boolean dlgPlageOuvert = false;
    private EquipeService equipeEnEdition;
    private boolean dlgEquipeOuvert = false;

    // ── Plannings individuels (régime FIXE) ─────────────────────────────
    private static final List<String> JOURS_FR =
        List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE");
    private PlanningPresence planningEnEdition = new PlanningPresence();
    private Long employePlanningId;
    private List<String> joursSelectionnes = new ArrayList<>();
    private boolean modeEditionPlanning = false;
    private boolean dlgPlanningOuvert = false;
    /** EMPLOYE : un seul employé choisi. SERVICE : applique le même planning à tout le service (création uniquement). */
    private String ciblePlanning = "EMPLOYE";

    @PostConstruct
    public void init() {
        calculerJoursSemaine();
        recharger();
    }

    private void recharger() {
        Employe moi = managerService.getManagerEmploye(getOp());
        monService = moi != null ? moi.getServiceOrg() : null;
        if (monService == null) {
            employesDuService = new ArrayList<>();
            equipes = new ArrayList<>();
            plages = new ArrayList<>();
            membresParEquipe = new HashMap<>();
            equipeParEmploye = new HashMap<>();
            celluleCache.clear();
            return;
        }
        regimeEquipes = monService.isRegimeEquipes();
        employesDuService = employeDao.findByService(monService.getId());
        if (regimeEquipes) {
            equipes = planningEquipeService.getEquipes(monService.getId());
            plages = planningEquipeService.getPlages(monService.getId());
            membresParEquipe = planningEquipeService.getMembresParEquipe(monService.getId());
            equipeParEmploye = planningEquipeService.getEquipeParEmploye(monService.getId());
        } else {
            equipes = new ArrayList<>();
            plages = new ArrayList<>();
            membresParEquipe = new HashMap<>();
            equipeParEmploye = new HashMap<>();
        }
        planningsFixe = presenceService.getPlannings(sessionBean.getInstitutionId());
        chargerConges();
        chargerExceptions();
        chargerJoursFeries();
        celluleCache.clear();
    }

    private void chargerConges() {
        congeKeys = new HashSet<>();
        if (monService == null) return;
        Map<String, List<DemandeConge>> cal = presenceService.getCalendrierConges(
            semaineDebut, semaineDebut.plusDays(6), sessionBean.getInstitutionId());
        for (Map.Entry<String, List<DemandeConge>> e : cal.entrySet()) {
            for (DemandeConge d : e.getValue()) {
                if (d.getEmploye() != null) congeKeys.add(d.getEmploye().getId() + "_" + e.getKey());
            }
        }
    }

    /** Précharge en UNE requête les exceptions de la semaine affichée, pour éviter une requête par cellule. */
    private void chargerExceptions() {
        exceptionsSemaine = new HashMap<>();
        if (monService == null) return;
        List<Long> ids = employesDuService.stream().map(Employe::getId).toList();
        for (ExceptionPlanning x : planningEquipeService.getExceptionsSemainePourEmployes(ids, semaineDebut)) {
            if (x.getEmploye() != null) exceptionsSemaine.put(x.getEmploye().getId() + "_" + x.getDate(), x);
        }
    }

    private void chargerJoursFeries() {
        joursFeriesSemaine = monService == null ? new HashSet<>()
            : planningEquipeService.getJoursFeries(sessionBean.getInstitutionId(), semaineDebut, semaineDebut.plusDays(6));
    }

    private static LocalDate lundiDe(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1L);
    }

    private void calculerJoursSemaine() {
        joursSemaine = new ArrayList<>();
        for (int i = 0; i < 7; i++) joursSemaine.add(semaineDebut.plusDays(i));
    }

    public void semainePrecedente() { semaineDebut = semaineDebut.minusWeeks(1); calculerJoursSemaine(); chargerConges(); chargerExceptions(); chargerJoursFeries(); celluleCache.clear(); }
    public void semaineSuivante()   { semaineDebut = semaineDebut.plusWeeks(1);  calculerJoursSemaine(); chargerConges(); chargerExceptions(); chargerJoursFeries(); celluleCache.clear(); }
    public void aujourdhui()        { semaineDebut = lundiDe(LocalDate.now());  calculerJoursSemaine(); chargerConges(); chargerExceptions(); chargerJoursFeries(); celluleCache.clear(); }

    public String getLibelleSemaine() {
        LocalDate fin = semaineDebut.plusDays(6);
        return "Semaine du " + semaineDebut.getDayOfMonth() + " " + moisFr(semaineDebut)
            + " au " + fin.getDayOfMonth() + " " + moisFr(fin) + " " + fin.getYear();
    }
    private String moisFr(LocalDate d) { return d.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH); }
    public String getNomJour(LocalDate d) { return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH); }

    public List<Employe> getMembres(Long equipeId) {
        return membresParEquipe.getOrDefault(equipeId, new ArrayList<>());
    }

    public PlageHoraireService getPlageEquipeSemaine(EquipeService equipe) {
        return planningEquipeService.plageRotation(equipe, plages, semaineDebut);
    }

    /** Nombre d'employés effectivement présents sur une plage donnée, un jour donné — réutilise la cellule mémoïsée. */
    public int getCouverture(Long plageId, LocalDate date) {
        int n = 0;
        for (Employe e : employesDuService) {
            PlanningEquipeService.CelluleHoraire c = getCellule(e.getId(), date);
            if (!c.isOff() && plageId.equals(c.getPlageId())) n++;
        }
        return n;
    }

    // ── Cellule effective (régime-agnostique) ───────────────────────────

    public PlanningEquipeService.CelluleHoraire getCellule(Long employeId, LocalDate date) {
        String cle = employeId + "_" + date;
        if (celluleCache.containsKey(cle)) return celluleCache.get(cle);
        Employe e = trouverEmploye(employeId);
        boolean estFerie = joursFeriesSemaine.contains(date);
        boolean enConge = congeKeys.contains(employeId + "_" + date);
        ExceptionPlanning exc = exceptionsSemaine.get(cle);
        PlanningEquipeService.CelluleHoraire c = e == null
            ? new PlanningEquipeService.CelluleHoraire(true, false, "—", null, null, "#8a93a6", null)
            : planningEquipeService.getCelluleEffective(e, date, estFerie, enConge, exc, equipeParEmploye.get(employeId),
                equipes, plages, planningsFixe);
        celluleCache.put(cle, c);
        return c;
    }

    private Employe trouverEmploye(Long id) {
        for (Employe e : employesDuService) if (e.getId().equals(id)) return e;
        return null;
    }

    // ── KPIs ─────────────────────────────────────────────────────────────

    public int getNbEmployesPlanifies() {
        Set<Long> ids = new HashSet<>();
        for (Employe e : employesDuService)
            for (LocalDate d : joursSemaine)
                if (!getCellule(e.getId(), d).isOff()) { ids.add(e.getId()); break; }
        return ids.size();
    }

    public int getNbAlertesCouverture() {
        if (!regimeEquipes) return 0;
        int n = 0;
        for (PlageHoraireService p : plages)
            for (LocalDate d : joursSemaine)
                if (getCouverture(p.getId(), d) < p.getEffectifMinimum()) n++;
        return n;
    }

    public int getNbExceptionsSemaine() {
        return exceptionsSemaine.size();
    }

    // ── Dialogue exception ponctuelle ───────────────────────────────────

    public void ouvrirDlgException(Long employeId, LocalDate date) {
        excEmployeId = employeId;
        excDate = date;
        ExceptionPlanning existante = exceptionsSemaine.get(employeId + "_" + date);
        if (existante != null) {
            excType = existante.getType();
            excPlageId = existante.getPlage() != null ? existante.getPlage().getId() : null;
            excCommentaire = existante.getCommentaire();
        } else {
            excType = regimeEquipes ? "REMPLACEMENT" : "ABSENCE";
            excPlageId = null;
            excCommentaire = null;
        }
        dlgExcOuvert = true;
    }

    public List<SelectItem> getExcTypeOptions() {
        List<SelectItem> opts = new ArrayList<>();
        if (regimeEquipes) {
            opts.add(new SelectItem("REMPLACEMENT", "Remplacement (autre plage)"));
            opts.add(new SelectItem("ECHANGE", "Échange avec un collègue"));
        }
        opts.add(new SelectItem("ABSENCE", "Absence"));
        opts.add(new SelectItem("CONGE", "Congé"));
        return opts;
    }

    public void fermerDlgException() { dlgExcOuvert = false; }

    public void enregistrerException() {
        if (excEmployeId == null || excDate == null) return;
        Employe e = employeDao.findById(excEmployeId);
        if (e == null) return;
        ExceptionPlanning x = new ExceptionPlanning();
        x.setEmploye(e);
        x.setDate(excDate);
        x.setType(excType);
        if ("REMPLACEMENT".equals(excType) || "ECHANGE".equals(excType)) {
            x.setPlage(plages.stream().filter(p -> p.getId().equals(excPlageId)).findFirst().orElse(null));
        }
        x.setCommentaire(excCommentaire);
        planningEquipeService.creerOuModifierException(x, getOp());
        dlgExcOuvert = false;
        chargerExceptions();
        celluleCache.clear();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Exception enregistrée.");
    }

    public void retirerException() {
        if (excEmployeId == null || excDate == null) return;
        planningEquipeService.supprimerException(excEmployeId, excDate, getOp());
        dlgExcOuvert = false;
        chargerExceptions();
        celluleCache.clear();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Exception retirée.");
    }

    public Employe getExcEmploye() { return excEmployeId != null ? trouverEmploye(excEmployeId) : null; }
    public boolean isExcExisteDeja() {
        return excEmployeId != null && excDate != null && exceptionsSemaine.containsKey(excEmployeId + "_" + excDate);
    }

    // ── Configuration du régime équipes (plages, équipes, affectations) ─────

    public void ouvrirDlgConfig() {
        if (monService == null) return;
        configDateReference = monService.getDateReferenceRotation() != null
            ? monService.getDateReferenceRotation() : lundiDe(LocalDate.now());
        dlgConfigOuvert = true;
    }

    public void fermerDlgConfig() { dlgConfigOuvert = false; rafraichirConfigEquipes(); }

    public void enregistrerDateReference() {
        if (monService == null || configDateReference == null) return;
        planningEquipeService.setDateReferenceRotation(monService.getId(), configDateReference, getOp());
        rafraichirConfigEquipes();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Date de référence enregistrée.");
    }

    public void ouvrirDlgPlage(PlageHoraireService p) {
        plageEnEdition = p != null ? p : new PlageHoraireService();
        if (p == null) {
            plageEnEdition.setService(monService);
            plageEnEdition.setOrdreRotation(plages.size());
            plageEnEdition.setHeureDebut(LocalTime.of(8, 0));
            plageEnEdition.setHeureFin(LocalTime.of(16, 0));
            plageEnEdition.setEffectifMinimum(1);
            plageEnEdition.setCouleur("#2563eb");
        }
        dlgPlageOuvert = true;
    }

    public void fermerDlgPlage() { dlgPlageOuvert = false; }

    public void enregistrerPlage() {
        if (plageEnEdition.getNom() == null || plageEnEdition.getNom().isBlank()) return;
        planningEquipeService.sauvegarderPlage(plageEnEdition, getOp());
        dlgPlageOuvert = false;
        rafraichirConfigEquipes();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Plage enregistrée.");
    }

    public void supprimerPlage(Long id) {
        planningEquipeService.supprimerPlage(id, getOp());
        rafraichirConfigEquipes();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Plage supprimée.");
    }

    public void ouvrirDlgEquipe(EquipeService e) {
        equipeEnEdition = e != null ? e : new EquipeService();
        if (e == null) {
            equipeEnEdition.setService(monService);
            equipeEnEdition.setOrdreRotation(equipes.size());
            equipeEnEdition.setCouleur("#2563eb");
        }
        dlgEquipeOuvert = true;
    }

    public void fermerDlgEquipe() { dlgEquipeOuvert = false; }

    public void enregistrerEquipe() {
        if (equipeEnEdition.getNom() == null || equipeEnEdition.getNom().isBlank()) return;
        planningEquipeService.sauvegarderEquipe(equipeEnEdition, getOp());
        dlgEquipeOuvert = false;
        rafraichirConfigEquipes();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Équipe enregistrée.");
    }

    public void supprimerEquipe(Long id) {
        planningEquipeService.supprimerEquipe(id, getOp());
        rafraichirConfigEquipes();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Équipe supprimée.");
    }

    public void affecterMembre(Long employeId, Long equipeId) {
        if (monService == null) return;
        planningEquipeService.affecterMembre(employeId, equipeId, monService.getId(), getOp());
        rafraichirConfigEquipes();
    }

    private void rafraichirConfigEquipes() {
        if (monService == null) return;
        equipes = planningEquipeService.getEquipes(monService.getId());
        plages = planningEquipeService.getPlages(monService.getId());
        membresParEquipe = planningEquipeService.getMembresParEquipe(monService.getId());
        equipeParEmploye = planningEquipeService.getEquipeParEmploye(monService.getId());
        celluleCache.clear();
    }

    public LocalDate getConfigDateReference() { return configDateReference; }
    public void setConfigDateReference(LocalDate v) { this.configDateReference = v; }
    public PlageHoraireService getPlageEnEdition() { return plageEnEdition; }
    public boolean isDlgPlageOuvert() { return dlgPlageOuvert; }
    public EquipeService getEquipeEnEdition() { return equipeEnEdition; }
    public boolean isDlgEquipeOuvert() { return dlgEquipeOuvert; }
    public boolean isDlgConfigOuvert() { return dlgConfigOuvert; }
    public Map<Long, Long> getEquipeParEmploye() { return equipeParEmploye; }

    // ── Plannings individuels (régime FIXE) ─────────────────────────────

    public List<PlanningPresence> getPlanningsDuService() {
        Set<Long> ids = employesDuService.stream().map(Employe::getId).collect(Collectors.toSet());
        return planningsFixe.stream().filter(p -> p.getEmploye() != null && ids.contains(p.getEmploye().getId())).toList();
    }

    public List<String> getTousLesJours() { return JOURS_FR; }

    /** Horizon "indéfiniment" : le planning suit le calendrier de façon permanente, pas de date de fin à saisir. */
    private static final LocalDate HORIZON_PERPETUEL = LocalDate.of(2099, 12, 31);

    public void preparerNouveauPlanning() {
        planningEnEdition = new PlanningPresence();
        planningEnEdition.setDateDebut(LocalDate.now());
        planningEnEdition.setDateFin(HORIZON_PERPETUEL);
        employePlanningId = null;
        joursSelectionnes = new ArrayList<>(List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"));
        modeEditionPlanning = false;
        ciblePlanning = "EMPLOYE";
        dlgPlanningOuvert = true;
    }

    public void preparerEditionPlanning(PlanningPresence p) {
        planningEnEdition = p;
        employePlanningId = p.getEmploye() != null ? p.getEmploye().getId() : null;
        joursSelectionnes = p.getJoursTravailles() != null
            ? new ArrayList<>(Arrays.asList(p.getJoursTravailles().split(",")))
            : new ArrayList<>();
        modeEditionPlanning = true;
        dlgPlanningOuvert = true;
    }

    public void fermerDlgPlanning() { dlgPlanningOuvert = false; }

    public void sauvegarderPlanning() {
        if (planningEnEdition.getDateDebut() == null) planningEnEdition.setDateDebut(LocalDate.now());
        if (planningEnEdition.getDateFin() == null) planningEnEdition.setDateFin(HORIZON_PERPETUEL);
        planningEnEdition.setJoursTravailles(String.join(",", joursSelectionnes));
        String operateur = getOp();

        if (!modeEditionPlanning && "SERVICE".equals(ciblePlanning)) {
            int n = 0;
            for (Employe emp : employesDuService) {
                PlanningPresence existant = planningsFixe.stream()
                    .filter(p -> p.getEmploye() != null && p.getEmploye().getId().equals(emp.getId()))
                    .findFirst().orElse(null);
                PlanningPresence p = existant != null ? existant : new PlanningPresence();
                p.setEmploye(emp);
                p.setDateDebut(planningEnEdition.getDateDebut());
                p.setDateFin(planningEnEdition.getDateFin());
                p.setHeureDebut(planningEnEdition.getHeureDebut());
                p.setHeureFin(planningEnEdition.getHeureFin());
                p.setDureePauseMinutes(planningEnEdition.getDureePauseMinutes());
                p.setTypePlanning(planningEnEdition.getTypePlanning());
                p.setJoursTravailles(planningEnEdition.getJoursTravailles());
                p.setCommentaire(planningEnEdition.getCommentaire());
                if (existant != null) presenceService.modifierPlanning(p, operateur);
                else presenceService.creerPlanning(p, operateur);
                n++;
            }
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Planning appliqué à " + n + " employé(s) du service.");
        } else {
            if (employePlanningId == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Choisissez un employé.");
                return;
            }
            Employe employe = trouverEmploye(employePlanningId);
            if (employe == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Employé introuvable dans votre service.");
                return;
            }
            planningEnEdition.setEmploye(employe);
            if (modeEditionPlanning) {
                presenceService.modifierPlanning(planningEnEdition, operateur);
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Planning modifié.");
            } else {
                presenceService.creerPlanning(planningEnEdition, operateur);
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Planning créé pour " + employe.getNomComplet() + ".");
            }
        }
        planningsFixe = presenceService.getPlannings(sessionBean.getInstitutionId());
        celluleCache.clear();
        dlgPlanningOuvert = false;
    }

    public void supprimerPlanning(Long id) {
        presenceService.supprimerPlanning(id, getOp());
        planningsFixe = presenceService.getPlannings(sessionBean.getInstitutionId());
        celluleCache.clear();
        addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Planning supprimé.");
    }

    public PlanningPresence getPlanningEnEdition() { return planningEnEdition; }
    public Long getEmployePlanningId() { return employePlanningId; }
    public void setEmployePlanningId(Long v) { this.employePlanningId = v; }
    public List<String> getJoursSelectionnes() { return joursSelectionnes; }
    public void setJoursSelectionnes(List<String> v) { this.joursSelectionnes = v; }
    public boolean isModeEditionPlanning() { return modeEditionPlanning; }
    public boolean isDlgPlanningOuvert() { return dlgPlanningOuvert; }
    public String getCiblePlanning() { return ciblePlanning; }
    public void setCiblePlanning(String v) { this.ciblePlanning = v; }

    // ── Divers ────────────────────────────────────────────────────────

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "manager";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public Service getMonService() { return monService; }
    public boolean isRegimeEquipes() { return regimeEquipes; }
    public LocalDate getSemaineDebut() { return semaineDebut; }
    public List<LocalDate> getJoursSemaine() { return joursSemaine; }
    public List<Employe> getEmployesDuService() { return employesDuService; }
    public List<EquipeService> getEquipes() { return equipes; }
    public List<PlageHoraireService> getPlages() { return plages; }

    public Long getExcEmployeId() { return excEmployeId; }
    public LocalDate getExcDate() { return excDate; }
    public String getExcType() { return excType; }
    public void setExcType(String v) { this.excType = v; }
    public Long getExcPlageId() { return excPlageId; }
    public void setExcPlageId(Long v) { this.excPlageId = v; }
    public String getExcCommentaire() { return excCommentaire; }
    public void setExcCommentaire(String v) { this.excCommentaire = v; }
    public boolean isDlgExcOuvert() { return dlgExcOuvert; }
}
