package com.soutenance.service;

import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.JourFerieDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.PlanningEquipeDao;
import com.soutenance.entity.EquipeService;
import com.soutenance.entity.Employe;
import com.soutenance.entity.ExceptionPlanning;
import com.soutenance.entity.JourFerie;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.MembreEquipe;
import com.soutenance.entity.PlageHoraireService;
import com.soutenance.entity.PlanningPresence;
import com.soutenance.entity.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Régime de planning "par équipes" : plusieurs plages horaires (Matin/Soir/Nuit...)
 * tournent chaque semaine entre les équipes d'un service, selon une formule
 * déterministe (pas de table de rotation stockée) — voir plageRotation().
 * Les écarts ponctuels (remplacement, échange, absence, congé) priment sur la
 * rotation calculée — voir getCelluleEffective().
 * Ne touche jamais à PlanningPresence : les services à régime "FIXE" continuent
 * de fonctionner exactement comme avant (PresenceService).
 */
@org.springframework.stereotype.Service
public class PlanningEquipeService {

    @Autowired private PlanningEquipeDao dao;
    @Autowired private EmployeDao employeDao;
    @Autowired private LogActionDao logActionDao;
    @Autowired private JourFerieDao jourFerieDao;

    /** Dates des jours fériés d'une institution sur une période — à précharger une fois par semaine affichée. */
    public Set<LocalDate> getJoursFeries(Long institutionId, LocalDate debut, LocalDate fin) {
        Set<LocalDate> dates = new HashSet<>();
        for (JourFerie j : jourFerieDao.findByPeriodeAndInstitution(debut, fin, institutionId)) dates.add(j.getDate());
        return dates;
    }

    // ── Services ──────────────────────────────────────────────────────

    public Service getService(Long serviceId) {
        return dao.findServiceById(serviceId);
    }

    /** Active le régime "par équipes" pour ce service (idempotent) et fixe la date de référence si absente. */
    public void activerRegimeEquipes(Long serviceId, LocalDate dateReference, String operateur) {
        Service s = dao.findServiceById(serviceId);
        if (s == null) return;
        s.setRegimeHoraire("EQUIPES");
        if (s.getDateReferenceRotation() == null) s.setDateReferenceRotation(dateReference);
        dao.updateService(s);
        logActionDao.save(new LogAction(operateur, "ACTIVATION_REGIME_EQUIPES",
            "Régime par équipes activé pour le service \"" + s.getNom() + "\""));
    }

    /**
     * Revient au régime fixe. Les plages/équipes/affectations déjà configurées ne sont pas
     * supprimées (elles réapparaissent si le régime équipes est réactivé plus tard) — seuls
     * les employés redeviennent "Non planifié" tant qu'aucun planning individuel n'existe
     * pour eux (à créer par le chef de service).
     */
    public void desactiverRegimeEquipes(Long serviceId, String operateur) {
        Service s = dao.findServiceById(serviceId);
        if (s == null) return;
        s.setRegimeHoraire("FIXE");
        dao.updateService(s);
        logActionDao.save(new LogAction(operateur, "DESACTIVATION_REGIME_EQUIPES",
            "Régime par équipes désactivé pour le service \"" + s.getNom() + "\" (retour au régime fixe)"));
    }

    public void setDateReferenceRotation(Long serviceId, LocalDate date, String operateur) {
        Service s = dao.findServiceById(serviceId);
        if (s == null) return;
        s.setDateReferenceRotation(date);
        dao.updateService(s);
        logActionDao.save(new LogAction(operateur, "CONFIG_ROTATION",
            "Date de référence de rotation modifiée pour \"" + s.getNom() + "\" : " + date));
    }

    // ── Plages ────────────────────────────────────────────────────────

    public List<PlageHoraireService> getPlages(Long serviceId) {
        return dao.findPlagesByService(serviceId);
    }

    public void sauvegarderPlage(PlageHoraireService p, String operateur) {
        dao.savePlage(p);
        logActionDao.save(new LogAction(operateur, "CONFIG_PLAGE",
            "Plage \"" + p.getNom() + "\" enregistrée pour le service \"" + p.getService().getNom() + "\""));
    }

    public void supprimerPlage(Long id, String operateur) {
        dao.deletePlage(id);
        logActionDao.save(new LogAction(operateur, "CONFIG_PLAGE", "Plage supprimée (id=" + id + ")"));
    }

    // ── Équipes ───────────────────────────────────────────────────────

    public List<EquipeService> getEquipes(Long serviceId) {
        return dao.findEquipesByService(serviceId);
    }

    public void sauvegarderEquipe(EquipeService e, String operateur) {
        dao.saveEquipe(e);
        logActionDao.save(new LogAction(operateur, "CONFIG_EQUIPE",
            "Équipe \"" + e.getNom() + "\" enregistrée pour le service \"" + e.getService().getNom() + "\""));
    }

    public void supprimerEquipe(Long id, String operateur) {
        dao.deleteEquipe(id);
        logActionDao.save(new LogAction(operateur, "CONFIG_EQUIPE", "Équipe supprimée (id=" + id + ")"));
    }

    // ── Membres ───────────────────────────────────────────────────────

    /** Membres de toutes les équipes d'un service, indexés par id d'équipe. */
    public Map<Long, List<Employe>> getMembresParEquipe(Long serviceId) {
        List<MembreEquipe> membres = dao.findMembresByService(serviceId);
        return membres.stream().collect(Collectors.groupingBy(
            m -> m.getEquipe().getId(),
            Collectors.mapping(MembreEquipe::getEmploye, Collectors.toList())));
    }

    /** Équipe d'appartenance de chaque employé du service, indexée par id d'employé. */
    public Map<Long, Long> getEquipeParEmploye(Long serviceId) {
        List<MembreEquipe> membres = dao.findMembresByService(serviceId);
        return membres.stream().collect(Collectors.toMap(
            m -> m.getEmploye().getId(), m -> m.getEquipe().getId(), (a, b) -> a));
    }

    /** Réaffecte un employé à une équipe (le retire d'abord des autres équipes du même service). */
    public void affecterMembre(Long employeId, Long equipeId, Long serviceId, String operateur) {
        dao.retirerEmployeDesEquipesDuService(employeId, serviceId);
        if (equipeId == null) return;
        MembreEquipe m = new MembreEquipe();
        Employe emp = employeDao.findById(employeId);
        EquipeService eq = dao.findEquipesByService(serviceId).stream()
            .filter(x -> x.getId().equals(equipeId)).findFirst().orElse(null);
        if (emp == null || eq == null) return;
        m.setEmploye(emp);
        m.setEquipe(eq);
        dao.saveMembre(m);
        logActionDao.save(new LogAction(operateur, "AFFECTATION_EQUIPE",
            emp.getNomComplet() + " affecté(e) à \"" + eq.getNom() + "\""));
    }

    // ── Rotation ──────────────────────────────────────────────────────

    /** Plage occupée par une équipe (selon son ordre de rotation) pour la semaine du lundi donné. */
    public PlageHoraireService plageRotation(EquipeService equipe, List<PlageHoraireService> plages, LocalDate lundiSemaine) {
        if (plages.isEmpty() || equipe.getService().getDateReferenceRotation() == null) return null;
        long semaines = ChronoUnit.WEEKS.between(equipe.getService().getDateReferenceRotation(), lundiSemaine);
        int n = plages.size();
        int idx = (int) (((equipe.getOrdreRotation() + semaines) % n + n) % n);
        return plages.get(idx);
    }

    public void creerOuModifierException(ExceptionPlanning x, String operateur) {
        x.setCreePar(operateur);
        dao.saveException(x);
        logActionDao.save(new LogAction(operateur, "EXCEPTION_PLANNING",
            x.getEmploye().getNomComplet() + " — " + x.getType() + " le " + x.getDate()));
    }

    public void supprimerException(Long employeId, LocalDate date, String operateur) {
        dao.deleteException(employeId, date);
        logActionDao.save(new LogAction(operateur, "EXCEPTION_PLANNING",
            "Exception retirée pour l'employé #" + employeId + " le " + date));
    }

    public List<ExceptionPlanning> getExceptionsSemainePourEmployes(List<Long> employeIds, LocalDate lundi) {
        return dao.findExceptionsByEmployesAndPeriode(employeIds, lundi, lundi.plusDays(6));
    }

    // ── Cellule effective régime-agnostique (FIXE ou EQUIPES, congés + exceptions inclus) ──

    private static final List<String> JOURS_FR = List.of(
        "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE");

    private static final Map<String, String> COULEUR_TYPE = Map.of(
        "NORMAL", "#2563eb", "DECALE", "#c27a14", "NUIT", "#5b4bb7",
        "ROTATION", "#0d9488", "ASTREINTE", "#b3261e");

    /**
     * Horaire effectif d'un employé un jour donné, régime-agnostique : jour férié
     * en premier, puis congé approuvé, puis exception ponctuelle, puis rotation
     * d'équipe (régime EQUIPES) ou planning individuel actif (régime FIXE).
     * Partagé entre l'espace RH (vue de supervision, lecture seule) et l'espace
     * chef de service (édition des exceptions de sa propre équipe).
     * L'exception du jour et le caractère férié sont passés en paramètre (déjà
     * résolus par l'appelant à partir d'une seule requête groupée pour toute la
     * semaine) plutôt que d'être requêtés ici, pour éviter une requête SQL par
     * cellule du calendrier.
     */
    public CelluleHoraire getCelluleEffective(Employe employe, LocalDate date, boolean estFerie, boolean enConge,
            ExceptionPlanning exceptionDuJour, Long equipeId, List<EquipeService> equipes,
            List<PlageHoraireService> plages, List<PlanningPresence> planningsFixe) {
        if (estFerie) {
            return new CelluleHoraire(true, false, "Férié", null, null, "#8a93a6", null);
        }
        if (enConge) {
            return new CelluleHoraire(true, false, "Congé", null, null, "#8a93a6", null);
        }
        if (exceptionDuJour != null) {
            if (exceptionDuJour.isOff()) {
                return new CelluleHoraire(true, true, libelleException(exceptionDuJour.getType()), null, null, "#8a93a6", null);
            }
            PlageHoraireService p = exceptionDuJour.getPlage();
            return new CelluleHoraire(false, true, p != null ? p.getNom() : libelleException(exceptionDuJour.getType()),
                p != null ? p.getHeureDebut() : null, p != null ? p.getHeureFin() : null,
                p != null ? p.getCouleur() : "#2563eb", p != null ? p.getId() : null);
        }
        Service s = employe.getServiceOrg();
        if (s != null && s.isRegimeEquipes()) {
            EquipeService eq = equipeId == null ? null : equipes.stream()
                .filter(x -> x.getId().equals(equipeId)).findFirst().orElse(null);
            if (eq == null) return new CelluleHoraire(true, false, "Sans équipe", null, null, "#8a93a6", null);
            PlageHoraireService p = plageRotation(eq, plages, date.minusDays(date.getDayOfWeek().getValue() - 1L));
            if (p == null) return new CelluleHoraire(true, false, "Non configuré", null, null, "#8a93a6", null);
            return new CelluleHoraire(false, false, p.getNom(), p.getHeureDebut(), p.getHeureFin(), p.getCouleur(), p.getId());
        }
        PlanningPresence pp = planningActifParmi(planningsFixe, employe.getId(), date);
        if (pp == null) return new CelluleHoraire(true, false, "Non planifié", null, null, "#8a93a6", null);
        return new CelluleHoraire(false, false, libelleType(pp.getTypePlanning()), pp.getHeureDebut(), pp.getHeureFin(),
            COULEUR_TYPE.getOrDefault(pp.getTypePlanning(), "#2563eb"), null);
    }

    private PlanningPresence planningActifParmi(List<PlanningPresence> plannings, Long employeId, LocalDate date) {
        String jourFr = JOURS_FR.get(date.getDayOfWeek().getValue() - 1);
        for (PlanningPresence p : plannings) {
            if (p.getEmploye() == null || !p.getEmploye().getId().equals(employeId)) continue;
            if (p.getDateDebut() != null && date.isBefore(p.getDateDebut())) continue;
            if (p.getDateFin() != null && date.isAfter(p.getDateFin())) continue;
            if (p.getJoursTravailles() != null && !p.getJoursTravailles().contains(jourFr)) continue;
            return p;
        }
        return null;
    }

    private String libelleType(String t) {
        if (t == null) return "Normal";
        return switch (t) {
            case "NORMAL" -> "Normal"; case "DECALE" -> "Décalé"; case "NUIT" -> "Nuit";
            case "ROTATION" -> "Rotation"; case "ASTREINTE" -> "Astreinte"; default -> t;
        };
    }

    private String libelleException(String t) {
        if (t == null) return "Exception";
        return switch (t) {
            case "ABSENCE" -> "Absent"; case "CONGE" -> "Congé";
            case "REMPLACEMENT" -> "Remplacement"; case "ECHANGE" -> "Échange"; default -> t;
        };
    }

    /** Résultat régime-agnostique du calcul d'horaire effectif d'un employé un jour donné. */
    public static class CelluleHoraire implements Serializable {
        private static final long serialVersionUID = 1L;
        private final boolean off;
        private final boolean exception;
        private final String libelle;
        private final LocalTime heureDebut;
        private final LocalTime heureFin;
        private final String couleur;
        private final Long plageId;

        public CelluleHoraire(boolean off, boolean exception, String libelle, LocalTime heureDebut, LocalTime heureFin, String couleur, Long plageId) {
            this.off = off; this.exception = exception; this.libelle = libelle;
            this.heureDebut = heureDebut; this.heureFin = heureFin; this.couleur = couleur; this.plageId = plageId;
        }
        public boolean isOff() { return off; }
        public boolean isException() { return exception; }
        public String getLibelle() { return libelle; }
        public LocalTime getHeureDebut() { return heureDebut; }
        public LocalTime getHeureFin() { return heureFin; }
        public String getCouleur() { return couleur; }
        public Long getPlageId() { return plageId; }
    }
}
