package com.soutenance.bean;

import com.soutenance.dao.EmployeDao;
import com.soutenance.entity.Employe;
import com.soutenance.entity.Evenement;
import com.soutenance.entity.ParticipationEvenement;
import com.soutenance.service.EvenementService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean côté Employé : ses événements (passés, en cours, à venir) et
 * réponse aux invitations.
 */
@Component("mesEvenementsBean")
@Scope("view")
public class MesEvenementsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private EvenementService evenementService;
    @Autowired private EmployeDao       employeDao;
    @Autowired private SessionBean      sessionBean;

    private Employe          monEmploye;
    private List<Evenement>  mesEvenements = new ArrayList<>();

    private Long   participationASeDesister;
    private String motifDesistement;

    @PostConstruct
    public void init() { recharger(); }

    public void recharger() {
        if (sessionBean.getUtilisateurConnecte() != null) {
            try {
                monEmploye = employeDao.findByUserId(
                    sessionBean.getUtilisateurConnecte().getId());
                if (monEmploye != null) {
                    mesEvenements = evenementService.getByParticipant(monEmploye.getId());
                }
            } catch (Exception e) {
                mesEvenements = new ArrayList<>();
            }
        }
    }

    /** Retourne ma participation pour un événement donné. */
    public ParticipationEvenement maParticipation(Evenement e) {
        if (e == null || monEmploye == null || e.getParticipations() == null) return null;
        return e.getParticipations().stream()
            .filter(p -> p.getEmploye() != null
                      && monEmploye.getId().equals(p.getEmploye().getId()))
            .findFirst().orElse(null);
    }

    public List<Evenement> getMesEvenementsAVenir() {
        if (mesEvenements == null) return new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        return mesEvenements.stream()
            .filter(e -> e.getDateDebut() != null
                      && (e.getDateDebut().isAfter(now) || e.isEnCours())
                      && !"ANNULE".equalsIgnoreCase(e.getStatut()))
            .sorted(java.util.Comparator.comparing(Evenement::getDateDebut))
            .collect(java.util.stream.Collectors.toList());
    }

    public List<Evenement> getMesEvenementsPasses() {
        if (mesEvenements == null) return new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        return mesEvenements.stream()
            .filter(e -> e.getDateFin() != null && e.getDateFin().isBefore(now))
            .sorted(java.util.Comparator.comparing(Evenement::getDateDebut).reversed())
            .collect(java.util.stream.Collectors.toList());
    }

    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut.toUpperCase()) {
            case "CONFIRME"      -> "badge-success";
            case "LISTE_ATTENTE" -> "badge-warning";
            case "DESISTE"       -> "badge-danger";
            case "PRESENT"       -> "badge-primary";
            case "ABSENT"        -> "badge-warning";
            default              -> "badge-secondary";
        };
    }

    /** Libellé lisible : les employés sont convoqués directement, sans étape de confirmation. */
    public String getLabelStatut(String statut) {
        if (statut == null) return "—";
        return switch (statut.toUpperCase()) {
            case "CONFIRME"      -> "Convoqué(e)";
            case "LISTE_ATTENTE" -> "Liste d'attente";
            case "DESISTE"       -> "Désisté(e)";
            case "PRESENT"       -> "Présent(e)";
            case "ABSENT"        -> "Absent(e)";
            default              -> statut;
        };
    }

    /** Un participant CONFIRME ou en liste d'attente peut se désister avant le début de l'événement. */
    public boolean peutSeDesister(Evenement e) {
        if (e == null || e.getDateDebut() == null || e.getDateDebut().isBefore(LocalDateTime.now())) return false;
        ParticipationEvenement p = maParticipation(e);
        return p != null && ("CONFIRME".equals(p.getStatut()) || "LISTE_ATTENTE".equals(p.getStatut()));
    }

    /** Ouvre le dialogue de désistement pour la participation donnée. */
    public void preparerDesistement(Long participationId) {
        this.participationASeDesister = participationId;
        this.motifDesistement = null;
    }

    /** Confirme le désistement (motif facultatif) saisi dans le dialogue. */
    public void confirmerDesistement() {
        if (participationASeDesister == null) return;
        try {
            evenementService.seDesister(participationASeDesister, motifDesistement, getOp());
            recharger();
            addMsg(FacesMessage.SEVERITY_INFO, "Désistement enregistré",
                "Votre désistement a bien été pris en compte.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        participationASeDesister = null;
        motifDesistement = null;
    }

    public String getIconeType(String type) {
        if (type == null) return "pi-calendar";
        return switch (type.toUpperCase()) {
            case "SEMINAIRE"           -> "pi-bookmark";
            case "REUNION"             -> "pi-users";
            case "VOYAGE"              -> "pi-send";
            case "FORMATION_EXTERNE"   -> "pi-book";
            case "WORKSHOP"            -> "pi-cog";
            case "CONFERENCE"          -> "pi-megaphone";
            case "JOURNEE_INTEGRATION" -> "pi-star";
            default                    -> "pi-calendar";
        };
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "employe";
    }

    private void addMsg(FacesMessage.Severity sev, String t, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, t, d));
    }

    // Getters / Setters
    public Employe getMonEmploye()                          { return monEmploye; }
    public List<Evenement> getMesEvenements()               { return mesEvenements; }
    public String getMotifDesistement()                     { return motifDesistement; }
    public void setMotifDesistement(String motifDesistement) { this.motifDesistement = motifDesistement; }
}
