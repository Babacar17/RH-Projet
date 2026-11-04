package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Compteur de solde de congés par employé et par type de congé, pour un exercice donné.
 *
 * Ex : Diallo Moussa — Congé annuel — 2025 — Acquis: 30j — Pris: 10j — Reste: 20j
 */
@Entity
@Table(name = "soldes_conge",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "type_conge_id", "exercice"}))
public class SoldeConge implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_conge_id", nullable = false)
    private TypeConge typeConge;

    /** Année de l'exercice. Ex : 2025 */
    @Column(nullable = false, length = 10)
    private String exercice;

    /** Nombre de jours acquis pour cet exercice. */
    private double joursAcquis = 0.0;

    /** Nombre de jours pris (approuvés) sur cet exercice. */
    private double joursPris = 0.0;

    /** Nombre de jours en attente (demandes non encore approuvées). */
    private double joursEnAttente = 0.0;

    /** Nombre de jours reportés de l'exercice précédent. */
    private double joursReportes = 0.0;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Solde disponible = acquis + reportés - pris - en attente */
    public double getSoldeDisponible() {
        return joursAcquis + joursReportes - joursPris - joursEnAttente;
    }

    /** Solde restant réel (sans tenir compte des demandes en attente). */
    public double getSoldeReel() {
        return joursAcquis + joursReportes - joursPris;
    }

    /** Ajoute des jours pris lors d'une approbation. */
    public void consommer(double jours) {
        this.joursEnAttente = Math.max(0, this.joursEnAttente - jours);
        this.joursPris += jours;
    }

    /** Réserve des jours lors du dépôt d'une demande. */
    public void reserver(double jours) {
        this.joursEnAttente += jours;
    }

    /** Libère la réservation en cas de refus ou annulation. */
    public void liberer(double jours) {
        this.joursEnAttente = Math.max(0, this.joursEnAttente - jours);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public TypeConge getTypeConge() { return typeConge; }
    public void setTypeConge(TypeConge typeConge) { this.typeConge = typeConge; }

    public String getExercice() { return exercice; }
    public void setExercice(String exercice) { this.exercice = exercice; }

    public double getJoursAcquis() { return joursAcquis; }
    public void setJoursAcquis(double joursAcquis) { this.joursAcquis = joursAcquis; }

    public double getJoursPris() { return joursPris; }
    public void setJoursPris(double joursPris) { this.joursPris = joursPris; }

    public double getJoursEnAttente() { return joursEnAttente; }
    public void setJoursEnAttente(double joursEnAttente) { this.joursEnAttente = joursEnAttente; }

    public double getJoursReportes() { return joursReportes; }
    public void setJoursReportes(double joursReportes) { this.joursReportes = joursReportes; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
}