package com.soutenance.bean;

import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.StructureDao;
import com.soutenance.entity.DemandeConge;
import com.soutenance.entity.Employe;
import com.soutenance.entity.EquipeService;
import com.soutenance.entity.ExceptionPlanning;
import com.soutenance.entity.PlageHoraireService;
import com.soutenance.entity.PlanningPresence;
import com.soutenance.entity.Service;
import com.soutenance.service.PlanningEquipeService;
import com.soutenance.service.PresenceService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bean JSF derrière horaires.xhtml : vue de supervision RH sur le calendrier
 * hebdomadaire de tous les services de l'institution, tous régimes confondus
 * (FIXE via PlanningPresence, EQUIPES via PlanningEquipeService) — en LECTURE
 * SEULE. La gestion du planning de base (plannings individuels en régime
 * FIXE, plages/équipes/affectations en régime EQUIPES) et des exceptions
 * ponctuelles au jour le jour est du ressort du chef de service (espace
 * Responsable Hiérarchique, ManagerPlanningBean). RH ne garde que le choix
 * du régime d'un service (activerRegimeEquipes) et la supervision
 * multi-services.
 */
@Component("rhHorairesBean")
@Scope("view")
public class RHHorairesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private PlanningEquipeService planningEquipeService;
    @Autowired private PresenceService presenceService;
    @Autowired private EmployeDao employeDao;
    @Autowired private StructureDao structureDao;
    @Autowired private SessionBean sessionBean;

    // ── Vue ──────────────────────────────────────────────────────────────
    private String vue = "cal"; // cal | cfg
    private Long serviceFiltreId; // null = tous les services
    private LocalDate semaineDebut = lundiDe(LocalDate.now());
    private List<LocalDate> joursSemaine = new ArrayList<>();
    /** Services dont le détail (agents, cellules) est déplié dans le calendrier — repliés par défaut. */
    private Set<Long> servicesDeplies = new HashSet<>();

    // ── Données chargées ─────────────────────────────────────────────────
    private List<Service> tousLesServices = new ArrayList<>();
    private Map<Long, List<Employe>> employesParService = new HashMap<>();
    private Map<Long, List<EquipeService>> equipesParService = new HashMap<>();
    private Map<Long, List<PlageHoraireService>> plagesParService = new HashMap<>();
    private Map<Long, List<Employe>> membresParEquipe = new HashMap<>();
    private Map<Long, Long> equipeParEmploye = new HashMap<>();
    private List<PlanningPresence> planningsFixe = new ArrayList<>();
    private Set<String> congeKeys = new HashSet<>(); // "employeId_dateIso"
    private Map<String, ExceptionPlanning> exceptionsSemaine = new HashMap<>(); // "employeId_dateIso"
    private Set<LocalDate> joursFeriesSemaine = new HashSet<>();

    private final Map<String, PlanningEquipeService.CelluleHoraire> celluleCache = new HashMap<>();

    @PostConstruct
    public void init() {
        calculerJoursSemaine();
        recharger();
    }

    private void recharger() {
        Long instId = sessionBean.getInstitutionId();
        tousLesServices = structureDao.findAllServicesByInstitution(instId);
        employesParService = new HashMap<>();
        equipesParService = new HashMap<>();
        plagesParService = new HashMap<>();
        membresParEquipe = new HashMap<>();
        equipeParEmploye = new HashMap<>();
        for (Service s : tousLesServices) {
            employesParService.put(s.getId(), employeDao.findByService(s.getId()));
            if (s.isRegimeEquipes()) {
                equipesParService.put(s.getId(), planningEquipeService.getEquipes(s.getId()));
                plagesParService.put(s.getId(), planningEquipeService.getPlages(s.getId()));
                membresParEquipe.putAll(planningEquipeService.getMembresParEquipe(s.getId()));
                equipeParEmploye.putAll(planningEquipeService.getEquipeParEmploye(s.getId()));
            }
        }
        planningsFixe = presenceService.getPlannings(instId);
        chargerConges();
        chargerExceptions();
        chargerJoursFeries();
        celluleCache.clear();
    }

    private void chargerConges() {
        congeKeys = new HashSet<>();
        Long instId = sessionBean.getInstitutionId();
        Map<String, List<DemandeConge>> cal =
            presenceService.getCalendrierConges(semaineDebut, semaineDebut.plusDays(6), instId);
        for (Map.Entry<String, List<DemandeConge>> e : cal.entrySet()) {
            for (DemandeConge d : e.getValue()) {
                if (d.getEmploye() != null) congeKeys.add(d.getEmploye().getId() + "_" + e.getKey());
            }
        }
    }

    private void chargerJoursFeries() {
        joursFeriesSemaine = planningEquipeService.getJoursFeries(
            sessionBean.getInstitutionId(), semaineDebut, semaineDebut.plusDays(6));
    }

    /** Précharge en UNE requête les exceptions de la semaine affichée, pour éviter une requête par cellule. */
    private void chargerExceptions() {
        List<Long> ids = new ArrayList<>();
        for (List<Employe> l : employesParService.values()) for (Employe e : l) ids.add(e.getId());
        exceptionsSemaine = new HashMap<>();
        for (ExceptionPlanning x : planningEquipeService.getExceptionsSemainePourEmployes(ids, semaineDebut)) {
            if (x.getEmploye() != null) exceptionsSemaine.put(x.getEmploye().getId() + "_" + x.getDate(), x);
        }
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

    public void onFiltreChange() { celluleCache.clear(); }

    /** Valeur sentinelle du filtre représentant le choix explicite "Tous les services". */
    public static final Long TOUS_SERVICES = -1L;
    public Long getTousServicesSentinel() { return TOUS_SERVICES; }

    /** Tant qu'aucun service n'a été choisi (filtre vide), on n'affiche rien — pas de chargement par défaut. */
    public List<Service> getServicesAffiches() {
        if (serviceFiltreId == null) return List.of();
        if (TOUS_SERVICES.equals(serviceFiltreId)) return tousLesServices;
        return tousLesServices.stream().filter(s -> s.getId().equals(serviceFiltreId)).toList();
    }

    /** Déplié si le RH l'a explicitement ouvert, ou si c'est le seul service affiché (filtre actif). */
    public boolean isDeplie(Long serviceId) {
        return servicesDeplies.contains(serviceId) || serviceId.equals(serviceFiltreId);
    }

    public void toggleService(Long serviceId) {
        if (!servicesDeplies.remove(serviceId)) servicesDeplies.add(serviceId);
    }

    public List<Employe> getMembres(Long equipeId) {
        return membresParEquipe.getOrDefault(equipeId, new ArrayList<>());
    }

    public PlageHoraireService getPlageEquipeSemaine(EquipeService equipe) {
        return planningEquipeService.plageRotation(equipe,
            plagesParService.getOrDefault(equipe.getService().getId(), List.of()), semaineDebut);
    }

    /** Nombre d'employés effectivement présents sur une plage donnée, un jour donné — réutilise la cellule mémoïsée. */
    public int getCouverture(Service service, Long plageId, LocalDate date) {
        int n = 0;
        for (Employe e : employesParService.getOrDefault(service.getId(), List.of())) {
            PlanningEquipeService.CelluleHoraire c = getCellule(e.getId(), date);
            if (!c.isOff() && plageId.equals(c.getPlageId())) n++;
        }
        return n;
    }

    // ── Cellule effective (régime-agnostique, lecture seule) ────────────

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
                equipesParService.getOrDefault(e.getServiceOrg() != null ? e.getServiceOrg().getId() : null, List.of()),
                plagesParService.getOrDefault(e.getServiceOrg() != null ? e.getServiceOrg().getId() : null, List.of()),
                planningsFixe);
        celluleCache.put(cle, c);
        return c;
    }

    private Employe trouverEmploye(Long id) {
        for (List<Employe> l : employesParService.values())
            for (Employe e : l) if (e.getId().equals(id)) return e;
        return null;
    }

    // ── KPIs ─────────────────────────────────────────────────────────────

    public int getNbEmployesPlanifies() {
        Set<Long> ids = new HashSet<>();
        for (Service s : getServicesAffiches())
            for (Employe e : employesParService.getOrDefault(s.getId(), List.of())) {
                for (LocalDate d : joursSemaine) {
                    if (!getCellule(e.getId(), d).isOff()) { ids.add(e.getId()); break; }
                }
            }
        return ids.size();
    }

    public int getNbEmployesTotal() {
        int n = 0;
        for (Service s : getServicesAffiches()) n += employesParService.getOrDefault(s.getId(), List.of()).size();
        return n;
    }

    public int getNbAlertesCouverture() {
        int n = 0;
        for (Service s : getServicesAffiches()) {
            if (!s.isRegimeEquipes()) continue;
            for (PlageHoraireService p : plagesParService.getOrDefault(s.getId(), List.of()))
                for (LocalDate d : joursSemaine)
                    if (getCouverture(s, p.getId(), d) < p.getEffectifMinimum()) n++;
        }
        return n;
    }

    public int getNbExceptionsSemaine() {
        return exceptionsSemaine.size();
    }

    public int getNbCongesSemaine() {
        Set<Long> ids = new HashSet<>();
        for (String k : congeKeys) ids.add(Long.valueOf(k.substring(0, k.indexOf('_'))));
        return ids.size();
    }

    // ── Activation du régime (seule action structurelle laissée à RH) ────

    public void activerRegimeEquipes(Long serviceId) {
        if (serviceId == null) return;
        planningEquipeService.activerRegimeEquipes(serviceId, lundiDe(LocalDate.now()), getOperateur());
        recharger();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Régime par équipes activé pour ce service. Le chef de service peut maintenant configurer ses plages/équipes et planifier son équipe.");
    }

    public void desactiverRegimeEquipes(Long serviceId) {
        if (serviceId == null) return;
        planningEquipeService.desactiverRegimeEquipes(serviceId, getOperateur());
        recharger();
        addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Régime par équipes désactivé — ce service repasse en horaires fixes. Le chef de service devra planifier chaque employé individuellement.");
    }

    // ── Divers ────────────────────────────────────────────────────────

    private String getOperateur() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public String getVue() { return vue; }
    public void setVue(String v) { this.vue = v; }
    public Long getServiceFiltreId() { return serviceFiltreId; }
    public void setServiceFiltreId(Long v) { this.serviceFiltreId = v; }
    public LocalDate getSemaineDebut() { return semaineDebut; }
    public List<LocalDate> getJoursSemaine() { return joursSemaine; }
    public List<Service> getTousLesServices() { return tousLesServices; }
    public Map<Long, List<Employe>> getEmployesParService() { return employesParService; }
    public Map<Long, List<EquipeService>> getEquipesParService() { return equipesParService; }
    public Map<Long, List<PlageHoraireService>> getPlagesParService() { return plagesParService; }
}
