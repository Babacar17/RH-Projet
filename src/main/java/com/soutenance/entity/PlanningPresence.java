package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Planning de présence d'un employé sur une période.
 * Définit les horaires théoriques attendus pour le calcul des retards
 * et des heures supplémentaires.
 *
 * Utilisé par PresenceService (RH) et AdminPresenceService (Admin).
 */
@Entity
@Table(name = "plannings_presence",
       indexes = {
           @Index(name = "idx_planning_employe", columnList = "employe_id"),
           @Index(name = "idx_planning_periode", columnList = "date_debut, date_fin")
       })
public class PlanningPresence implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /** Heure d'arrivée théorique (ex : 08:00). */
    @Column(name = "heure_debut")
    private LocalTime heureDebut;

    /** Heure de départ théorique (ex : 17:00). */
    @Column(name = "heure_fin")
    private LocalTime heureFin;

    /** Durée de pause déjeuner en minutes (ex : 60). */
    @Column(name = "duree_pause_minutes")
    private Integer dureePauseMinutes = 60;

    /**
     * Type de planning : NORMAL, DECALE, NUIT, ROTATION, ASTREINTE
     */
    @Column(length = 30)
    private String typePlanning;

    /**
     * Jours travaillés : JSON ou chaîne séparée par virgules.
     * Ex : "LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI"
     */
    @Column(name = "jours_travailles", length = 100)
    private String joursTravailles;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    /** ACTIF, ARCHIVE */
    @Column(length = 20)
    private String statut;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null)        statut        = "ACTIF";
        if (typePlanning == null)  typePlanning  = "NORMAL";
        if (joursTravailles == null)
            joursTravailles = "LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    /** Durée théorique journalière en heures (hors pause). */
    public double getDureeJournaliereHeures() {
        if (heureDebut == null || heureFin == null) return 8.0;
        double totalMinutes = java.time.Duration.between(heureDebut, heureFin).toMinutes();
        totalMinutes -= (dureePauseMinutes != null ? dureePauseMinutes : 60);
        return Math.max(0, totalMinutes / 60.0);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public LocalTime getHeureDebut() { return heureDebut; }
    public void setHeureDebut(LocalTime heureDebut) { this.heureDebut = heureDebut; }

    public LocalTime getHeureFin() { return heureFin; }
    public void setHeureFin(LocalTime heureFin) { this.heureFin = heureFin; }

    public Integer getDureePauseMinutes() { return dureePauseMinutes; }
    public void setDureePauseMinutes(Integer dureePauseMinutes) { this.dureePauseMinutes = dureePauseMinutes; }

    public String getTypePlanning() { return typePlanning; }
    public void setTypePlanning(String typePlanning) { this.typePlanning = typePlanning; }

    public String getJoursTravailles() { return joursTravailles; }
    public void setJoursTravailles(String joursTravailles) { this.joursTravailles = joursTravailles; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
}