package com.soutenance.bean;

import com.soutenance.entity.Employe;
import com.soutenance.entity.Evenement;
import com.soutenance.entity.ParticipationEvenement;
import com.soutenance.service.EvenementService;
import com.soutenance.service.RHService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean JSF — gestion des événements (séminaires, réunions, voyages…).
 *
 * Scope view : un bean par onglet. Cloisonné par institution via SessionBean.
 */
@Component("evenementBean")
@Scope("view")
public class EvenementBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private EvenementService evenementService;
    @Autowired private RHService        rhService;
    @Autowired private SessionBean      sessionBean;

    private List<Evenement> evenements = new ArrayList<>();
    /** Chargée indépendamment pour que les compteurs KPI restent exacts même liste masquée. */
    private List<Evenement> tousEvenementsPourKpi = new ArrayList<>();
    private List<Employe>   employesInstitution = new ArrayList<>();
    /** La liste reste cachée tant que le RH n'a pas cliqué sur « Filtrer ». */
    private boolean filtresAppliques = false;

    // Entité en cours d'édition
    private Evenement evenement = new Evenement();
    private boolean   modeEdition = false;

    // Sélection en cours
    private Evenement evenementSelectionne;
    private List<Long> employesAInviter = new ArrayList<>();

    // Filtres
    private String filtreType;
    private String filtreStatut;

    @PostConstruct
    public void init() { chargerTout(); }

    public void chargerTout() {
        try {
            Long instId = sessionBean.getInstitutionId();
            tousEvenementsPourKpi = evenementService.getByInstitution(instId);
            evenements = filtresAppliques ? tousEvenementsPourKpi : new ArrayList<>();
            employesInstitution = (instId != null && rhService != null)
                ? rhService.getEmployesByInstitution(instId)
                : (rhService != null ? rhService.getTousEmployes() : new ArrayList<>());
        } catch (Exception e) {
            evenements = new ArrayList<>();
            tousEvenementsPourKpi = new ArrayList<>();
            employesInstitution = new ArrayList<>();
        }
    }

    /** Empêche de choisir une date de début/fin d'événement déjà passée. */
    public java.util.Date getDateAujourdHui() {
        return new java.util.Date();
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    public void preparerNouvel() {
        evenement = new Evenement();
        evenement.setType("REUNION");
        evenement.setStatut("PROGRAMME");
        evenement.setModalite("PRESENTIEL");
        evenement.setDateDebut(LocalDateTime.now().plusDays(7).withHour(9).withMinute(0));
        evenement.setDateFin(LocalDateTime.now().plusDays(7).withHour(12).withMinute(0));
        modeEdition = false;
    }

    public void preparerEdition(Evenement e) {
        evenement = evenementService.getById(e.getId());
        if (evenement == null) evenement = e;
        modeEdition = true;
    }

    /** RG-02 à RG-04, RG-18 : les règles masquées côté client sont revérifiées ici. */
    private String validerEvenement() {
        if (evenement.getTitre() == null || evenement.getTitre().isBlank())
            return "Le titre est obligatoire.";
        if (evenement.getDateDebut() == null || evenement.getDateFin() == null)
            return "Les deux dates sont obligatoires.";
        if (!evenement.getDateFin().isAfter(evenement.getDateDebut()))
            return "La date de fin doit être postérieure à la date de début.";
        if (!modeEdition && evenement.getDateDebut().isBefore(LocalDateTime.now()))
            return "La date de début ne peut pas être antérieure à aujourd'hui.";
        String mod = evenement.getModalite();
        if (!"DISTANCIEL".equals(mod) && (evenement.getLieu() == null || evenement.getLieu().isBlank()))
            return "Le lieu est obligatoire en présentiel ou hybride.";
        if (!"PRESENTIEL".equals(mod) && (evenement.getUrlVisio() == null || !evenement.getUrlVisio().matches("^https?://.+")))
            return "Une URL visio valide (https://…) est obligatoire en distanciel ou hybride.";
        String type = evenement.getType();
        if (isBudgetRequis(type) && (evenement.getBudget() == null || evenement.getBudget().signum() <= 0))
            return "Le budget est requis pour ce type d'événement.";
        if (isCapaciteRequise(type) && (evenement.getCapaciteMax() == null || evenement.getCapaciteMax() <= 0))
            return "La capacité maximale est requise pour ce type d'événement.";
        if ("FORMATION".equals(type) && (evenement.getFormateur() == null || evenement.getFormateur().isBlank()))
            return "Le formateur ou l'organisme est obligatoire pour une formation.";
        if ("CONFERENCE".equals(type) && (evenement.getIntervenant() == null || evenement.getIntervenant().isBlank()))
            return "L'intervenant est obligatoire pour une conférence.";
        return null;
    }

    public String sauvegarder() {
        try {
            String erreur = validerEvenement();
            if (erreur != null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
                return null;
            }
            if (evenement.getInstitution() == null) {
                evenement.setInstitution(sessionBean.getInstitutionCourante());
            }
            if (modeEdition) {
                evenementService.modifier(evenement, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Événement modifié : " + evenement.getTitre());
            } else {
                evenementService.creer(evenement, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Événement créé : " + evenement.getTitre());
            }
            chargerTout();
            evenement = new Evenement();
            modeEdition = false;
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimer(Long id) {
        try {
            evenementService.supprimer(id, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Événement supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void confirmer(Long id)  { changerStatut(id, "CONFIRME",  "Événement confirmé."); }
    public void demarrer(Long id)   { changerStatut(id, "EN_COURS",  "Événement démarré."); }
    public void terminer(Long id)   { changerStatut(id, "TERMINE",   "Événement terminé."); }
    public void annuler(Long id)    { changerStatut(id, "ANNULE",    "Événement annulé."); }

    private void changerStatut(Long id, String statut, String msg) {
        try {
            evenementService.changerStatut(id, statut, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, statut, msg);
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Participants ────────────────────────────────────────────────────────

    public void preparerParticipants(Evenement e) {
        this.evenementSelectionne = evenementService.getById(e.getId());
        if (this.evenementSelectionne == null) this.evenementSelectionne = e;
        this.employesAInviter = new ArrayList<>();
    }

    public void inviterEmploye() {
        if (evenementSelectionne == null || employesAInviter == null || employesAInviter.isEmpty()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Sélection requise",
                "Choisissez au moins un employé à inviter.");
            return;
        }
        int nb = 0;
        int attente = 0;
        for (Long id : employesAInviter) {
            ParticipationEvenement p = evenementService.inviter(evenementSelectionne.getId(), id, getOp());
            if (p != null && "LISTE_ATTENTE".equals(p.getStatut())) attente++;
            else if (p != null) nb++;
        }
        // Recharger les participations
        this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
        chargerTout();
        this.employesAInviter = new ArrayList<>();
        String resume = nb + " employé(s) convoqué(s).";
        if (attente > 0) resume += " " + attente + " en liste d'attente (capacité atteinte).";
        addMsg(attente > 0 ? FacesMessage.SEVERITY_WARN : FacesMessage.SEVERITY_INFO,
            "Convocation envoyée", resume);
    }

    private String serviceAInviter;
    public String getServiceAInviter() { return serviceAInviter; }
    public void setServiceAInviter(String s) { this.serviceAInviter = s; }

    /** Liste des services distincts de l'institution, pour le sélecteur "inviter un service". */
    public List<String> getServicesInstitution() {
        if (employesInstitution == null) return new ArrayList<>();
        return employesInstitution.stream()
            .map(Employe::getNomService)
            .filter(s -> s != null && !"—".equals(s))
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
    }

    /** Invite tous les employés actifs d'un service donné (spec §8 : sélection par service). */
    public void inviterService() {
        if (evenementSelectionne == null || serviceAInviter == null || serviceAInviter.isBlank()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Sélection requise", "Choisissez un service.");
            return;
        }
        int ok = 0;
        int attente = 0;
        for (Employe e : employesInstitution) {
            if (!"ACTIF".equalsIgnoreCase(e.getStatut())) continue;
            if (!serviceAInviter.equals(e.getNomService())) continue;
            try {
                ParticipationEvenement p = evenementService.inviter(evenementSelectionne.getId(), e.getId(), getOp());
                if (p != null && "LISTE_ATTENTE".equals(p.getStatut())) attente++;
                else if (p != null) ok++;
            } catch (Exception ignored) {}
        }
        this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
        chargerTout();
        serviceAInviter = null;
        String resume = ok + " employé(s) du service invité(s).";
        if (attente > 0) resume += " " + attente + " en liste d'attente (capacité atteinte).";
        addMsg(attente > 0 ? FacesMessage.SEVERITY_WARN : FacesMessage.SEVERITY_INFO,
            "Invitations envoyées", resume);
    }

    /**
     * Import d'une liste de participants depuis un fichier texte/CSV (un
     * matricule ou un email par ligne — en-tête éventuel et lignes vides
     * ignorés). Pour les gros événements (spec §8 : "Import d'une liste").
     */
    public void handleImportParticipants(FileUploadEvent event) {
        if (evenementSelectionne == null) return;
        try {
            UploadedFile f = event.getFile();
            String contenu = new String(f.getContent(), StandardCharsets.UTF_8);
            String[] lignes = contenu.split("\\r?\\n");

            int invites = 0;
            int attente = 0;
            List<String> introuvables = new ArrayList<>();

            for (String ligneBrute : lignes) {
                // Une ligne peut contenir plusieurs colonnes (CSV) ; on ne retient que
                // la première valeur non vide comme identifiant (matricule ou email).
                String ligne = ligneBrute.trim();
                if (ligne.isEmpty()) continue;
                String identifiant = ligne.split("[;,]")[0].trim();
                if (identifiant.isEmpty()) continue;
                if (identifiant.equalsIgnoreCase("matricule") || identifiant.equalsIgnoreCase("email")) continue; // en-tête

                Employe trouve = employesInstitution.stream()
                    .filter(e -> identifiant.equalsIgnoreCase(e.getMatricule())
                              || identifiant.equalsIgnoreCase(e.getEmail()))
                    .findFirst().orElse(null);

                if (trouve == null) {
                    introuvables.add(identifiant);
                    continue;
                }
                try {
                    ParticipationEvenement p = evenementService.inviter(evenementSelectionne.getId(), trouve.getId(), getOp());
                    if (p != null && "LISTE_ATTENTE".equals(p.getStatut())) attente++;
                    else if (p != null) invites++;
                } catch (Exception ignored) {}
            }

            this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
            chargerTout();

            String resume = invites + " participant(s) importé(s) et convoqué(s).";
            if (attente > 0) resume += " " + attente + " en liste d'attente (capacité atteinte).";
            if (!introuvables.isEmpty()) {
                resume += " " + introuvables.size() + " identifiant(s) non reconnu(s) : "
                    + String.join(", ", introuvables.subList(0, Math.min(5, introuvables.size())))
                    + (introuvables.size() > 5 ? "…" : "");
            }
            addMsg((introuvables.isEmpty() && attente == 0) ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_WARN,
                "Import terminé", resume);
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur d'import", ex.getMessage());
        }
    }

    public void inviterTousEmployes() {
        if (evenementSelectionne == null) return;
        int ok = 0;
        int attente = 0;
        for (Employe e : employesInstitution) {
            if (!"ACTIF".equalsIgnoreCase(e.getStatut())) continue;
            try {
                ParticipationEvenement p = evenementService.inviter(evenementSelectionne.getId(), e.getId(), getOp());
                if (p != null && "LISTE_ATTENTE".equals(p.getStatut())) attente++;
                else if (p != null) ok++;
            } catch (Exception ignored) {}
        }
        this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
        chargerTout();
        String resume = ok + " employé(s) invité(s).";
        if (attente > 0) resume += " " + attente + " en liste d'attente (capacité atteinte).";
        addMsg(attente > 0 ? FacesMessage.SEVERITY_WARN : FacesMessage.SEVERITY_INFO,
            "Invitations envoyées", resume);
    }

    public void retirerParticipant(Long participationId) {
        try {
            evenementService.retirerParticipant(participationId, getOp());
            this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Retiré", "Participant retiré.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void marquerPresent(Long participationId)  { marquer(participationId, true); }
    public void marquerAbsent(Long participationId)   { marquer(participationId, false); }

    private void marquer(Long participationId, boolean present) {
        try {
            evenementService.marquerPresence(participationId, present, getOp());
            if (evenementSelectionne != null)
                this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
            chargerTout();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** RG-09 : verrouille les présences — après ça, l'indemnité est débloquée côté paie. */
    public void validerPresences() {
        if (evenementSelectionne == null) return;
        try {
            evenementService.validerPresences(evenementSelectionne.getId(), getOp());
            this.evenementSelectionne = evenementService.getById(evenementSelectionne.getId());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Présences validées",
                "Les présences sont verrouillées ; les indemnités seront incluses au prochain calcul de paie.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** RG-07 : alerte de conflit congé pour l'affichage dans le dialog participants. */
    public boolean aConflitConge(Long employeId) {
        if (evenementSelectionne == null) return false;
        return evenementService.aConflitConge(employeId,
            evenementSelectionne.getDateDebut(), evenementSelectionne.getDateFin());
    }

    // ── Filtres ─────────────────────────────────────────────────────────────

    public void appliquerFiltres() {
        filtresAppliques = true;
        chargerTout();
        if (filtreType != null && !filtreType.isBlank()) {
            evenements = evenements.stream()
                .filter(e -> filtreType.equalsIgnoreCase(e.getType()))
                .collect(java.util.stream.Collectors.toList());
        }
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            evenements = evenements.stream()
                .filter(e -> filtreStatut.equalsIgnoreCase(e.getStatut()))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    public void reinitialiserFiltres() {
        filtreType   = null;
        filtreStatut = null;
        filtresAppliques = false;
        chargerTout();
    }

    public boolean isFiltresAppliques() { return filtresAppliques; }

    /** Raccourci cliquable depuis les cartes KPI : affiche la liste déjà filtrée sur ce sous-ensemble. */
    public void filtrerParKpi(String kpi) {
        filtreType = null;
        filtreStatut = null;
        filtresAppliques = true;
        chargerTout();
        LocalDateTime now = LocalDateTime.now();
        switch (kpi) {
            case "A_VENIR" -> evenements = tousEvenementsPourKpi.stream()
                .filter(e -> e.getDateDebut() != null && e.getDateDebut().isAfter(now)
                          && !"ANNULE".equalsIgnoreCase(e.getStatut()))
                .collect(java.util.stream.Collectors.toList());
            case "EN_COURS" -> evenements = tousEvenementsPourKpi.stream()
                .filter(Evenement::isEnCours)
                .collect(java.util.stream.Collectors.toList());
            case "TERMINES" -> evenements = tousEvenementsPourKpi.stream()
                .filter(e -> "TERMINE".equalsIgnoreCase(e.getStatut()))
                .collect(java.util.stream.Collectors.toList());
            default -> evenements = tousEvenementsPourKpi; // TOTAL : pas de filtre
        }
    }

    // ── KPI (toujours sur l'ensemble, indépendamment de la liste masquée) ──

    public long getNombreEvenements()    { return tousEvenementsPourKpi != null ? tousEvenementsPourKpi.size() : 0; }
    public long getNombreAVenir() {
        if (tousEvenementsPourKpi == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        return tousEvenementsPourKpi.stream()
            .filter(e -> e.getDateDebut() != null && e.getDateDebut().isAfter(now)
                      && !"ANNULE".equalsIgnoreCase(e.getStatut())).count();
    }
    public long getNombreEnCours() {
        if (tousEvenementsPourKpi == null) return 0;
        return tousEvenementsPourKpi.stream().filter(Evenement::isEnCours).count();
    }
    public long getNombreTermines() {
        if (tousEvenementsPourKpi == null) return 0;
        return tousEvenementsPourKpi.stream()
            .filter(e -> "TERMINE".equalsIgnoreCase(e.getStatut())).count();
    }

    // ── Helpers UI ──────────────────────────────────────────────────────────

    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut.toUpperCase()) {
            case "PROGRAMME" -> "badge-info";
            case "CONFIRME"  -> "badge-primary";
            case "EN_COURS"  -> "badge-warning";
            case "TERMINE"   -> "badge-success";
            case "ANNULE"    -> "badge-danger";
            default          -> "badge-secondary";
        };
    }

    public String getIconeType(String type) {
        if (type == null) return "pi-calendar";
        return switch (type.toUpperCase()) {
            case "SEMINAIRE"  -> "pi-bookmark";
            case "REUNION"    -> "pi-users";
            case "FORMATION"  -> "pi-book";
            case "ATELIER"    -> "pi-cog";
            case "CONFERENCE" -> "pi-megaphone";
            default           -> "pi-calendar";
        };
    }

    public String getLabelType(String type) {
        if (type == null) return "—";
        return switch (type.toUpperCase()) {
            case "REUNION"    -> "Réunion";
            case "FORMATION"  -> "Formation";
            case "SEMINAIRE"  -> "Séminaire";
            case "ATELIER"    -> "Atelier";
            case "CONFERENCE" -> "Conférence";
            default           -> "Autre";
        };
    }

    // ── Configuration des types (cahier des charges §4) ─────────────────────
    // Réglages par défaut en dur pour l'instant ; une vraie table de
    // configuration admin-modifiable (type_evenement) est prévue en phase
    // ultérieure, conformément à la feuille de route du rapport.

    public boolean isBudgetRequis(String type)    { return "FORMATION".equals(type) || "SEMINAIRE".equals(type) || "CONFERENCE".equals(type); }
    public boolean isCapaciteRequise(String type) { return "FORMATION".equals(type) || "SEMINAIRE".equals(type) || "ATELIER".equals(type); }
    public boolean isIndemniteAutorisee(String type) { return !"CONFERENCE".equals(type) && !"REUNION".equals(type); }

    public String getSuiviPresenceLabel(String type) {
        if ("FORMATION".equals(type) || "ATELIER".equals(type)) return "Obligatoire";
        if ("SEMINAIRE".equals(type)) return "Recommandé";
        return "Facultatif";
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public List<Evenement> getEvenements()              { return evenements; }
    public List<Employe>   getEmployesInstitution()     { return employesInstitution; }

    /** Employés pas encore participants de l'événement sélectionné — pour ne pas pouvoir les choisir deux fois. */
    public List<Employe> getEmployesDisponiblesPourInvitation() {
        if (employesInstitution == null) return new ArrayList<>();
        if (evenementSelectionne == null || evenementSelectionne.getParticipations() == null)
            return employesInstitution;
        java.util.Set<Long> dejaParticipants = evenementSelectionne.getParticipations().stream()
            .filter(p -> p.getEmploye() != null)
            .map(p -> p.getEmploye().getId())
            .collect(java.util.stream.Collectors.toSet());
        return employesInstitution.stream()
            .filter(e -> !dejaParticipants.contains(e.getId()))
            .collect(java.util.stream.Collectors.toList());
    }
    public Evenement       getEvenement()               { return evenement; }
    public void            setEvenement(Evenement e)    { this.evenement = e; }
    public boolean         isModeEdition()              { return modeEdition; }
    public Evenement       getEvenementSelectionne()    { return evenementSelectionne; }
    public List<Long>      getEmployesAInviter()            { return employesAInviter; }
    public void            setEmployesAInviter(List<Long> ids) { this.employesAInviter = ids; }
    public String          getFiltreType()              { return filtreType; }
    public void            setFiltreType(String t)      { this.filtreType = t; }
    public String          getFiltreStatut()            { return filtreStatut; }
    public void            setFiltreStatut(String s)    { this.filtreStatut = s; }

    public List<ParticipationEvenement> getParticipationsSelection() {
        return evenementSelectionne != null && evenementSelectionne.getParticipations() != null
            ? evenementSelectionne.getParticipations() : new ArrayList<>();
    }
}
