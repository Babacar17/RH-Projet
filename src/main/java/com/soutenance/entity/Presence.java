package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Enregistrement de présence journalier d'un employé.
 * Supporte le pointage manuel et l'import depuis un système de contrôle d'accès.
 */
@Entity
@Table(name = "presences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "date_presence"}))
public class Presence implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(name = "date_presence", nullable = false)
    private LocalDate datePresence;

    /** Heure d'arrivée. */
    @Column(name = "heure_arrivee")
    private LocalTime heureArrivee;

    /** Heure de départ. */
    @Column(name = "heure_depart")
    private LocalTime heureDepart;

    /** Heure de départ en pause déjeuner. */
    @Column(name = "heure_debut_pause")
    private LocalTime heureDebutPause;

    /** Heure de retour de pause déjeuner. */
    @Column(name = "heure_fin_pause")
    private LocalTime heureFinPause;

    /** Durée totale en heures (calculée). */
    @Column(name = "heures_travaillees")
    private Double heuresTravaillees;

    /** Heures supplémentaires (au-delà de l'horaire théorique). */
    @Column(name = "heures_supplementaires")
    private Double heuresSupplementaires;

    /**
     * Type de présence : PRESENT, ABSENT, CONGE, MISSION, FORMATION,
     * TELETRAVAIL, JOUR_FERIE, WEEKEND, SUSPENSION
     */
    @Column(length = 30)
    private String type;

    /** Raison de l'absence si type = ABSENT. */
    @Column(length = 200)
    private String motifAbsence;

    /** Nombre de minutes de retard par rapport à l'heure théorique d'arrivée. */
    private Integer retardMinutes;

    /** Source de la saisie : MANUEL, BADGE, IMPORT */
    @Column(length = 20)
    private String sourceSaisie;

    /** Commentaire du RH ou du manager. */
    @Column(columnDefinition = "TEXT")
    private String commentaire;

    /** Présence validée par le RH ou le manager. */
    @Column(name = "est_valide")
    private boolean estValide = false;

    // ── CORRECTION : champ renommé validePar (cohérent avec le getter/setter existant)
    @Column(name = "valide_par", length = 100)
    private String validePar;

    // ── CORRECTION : champ ajouté (manquait dans la version originale)
    @Column(name = "date_validation")
    private LocalDate dateValidation;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (type == null) type = "PRESENT";
        calculerDurees();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
        calculerDurees();
    }

    /** Calcule automatiquement les heures travaillées et les retards. */
    private void calculerDurees() {
        if (heureArrivee != null && heureDepart != null) {
            double totalMinutes = java.time.Duration.between(heureArrivee, heureDepart).toMinutes();
            if (heureDebutPause != null && heureFinPause != null) {
                totalMinutes -= java.time.Duration.between(heureDebutPause, heureFinPause).toMinutes();
            }
            this.heuresTravaillees = Math.max(0, totalMinutes / 60.0);
        }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public LocalDate getDatePresence() { return datePresence; }
    public void setDatePresence(LocalDate datePresence) { this.datePresence = datePresence; }

    public LocalTime getHeureArrivee() { return heureArrivee; }
    public void setHeureArrivee(LocalTime heureArrivee) { this.heureArrivee = heureArrivee; }

    public LocalTime getHeureDepart() { return heureDepart; }
    public void setHeureDepart(LocalTime heureDepart) { this.heureDepart = heureDepart; }

    public LocalTime getHeureDebutPause() { return heureDebutPause; }
    public void setHeureDebutPause(LocalTime heureDebutPause) { this.heureDebutPause = heureDebutPause; }

    public LocalTime getHeureFinPause() { return heureFinPause; }
    public void setHeureFinPause(LocalTime heureFinPause) { this.heureFinPause = heureFinPause; }

    public Double getHeuresTravaillees() { return heuresTravaillees; }
    public void setHeuresTravaillees(Double heuresTravaillees) { this.heuresTravaillees = heuresTravaillees; }

    public Double getHeuresSupplementaires() { return heuresSupplementaires; }
    public void setHeuresSupplementaires(Double heuresSupplementaires) { this.heuresSupplementaires = heuresSupplementaires; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMotifAbsence() { return motifAbsence; }
    public void setMotifAbsence(String motifAbsence) { this.motifAbsence = motifAbsence; }

    public Integer getRetardMinutes() { return retardMinutes; }
    public void setRetardMinutes(Integer retardMinutes) { this.retardMinutes = retardMinutes; }

    public String getSourceSaisie() { return sourceSaisie; }
    public void setSourceSaisie(String sourceSaisie) { this.sourceSaisie = sourceSaisie; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public boolean isEstValide() { return estValide; }
    public void setEstValide(boolean estValide) { this.estValide = estValide; }

    public String getValidePar() { return validePar; }
    public void setValidePar(String validePar) { this.validePar = validePar; }

    // ── CORRECTION : getter/setter pour dateValidation ──
    public LocalDate getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDate dateValidation) { this.dateValidation = dateValidation; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    // ── Vue façade « demande de congé » pour conges.xhtml ──────────────────
    // Le XHTML de gestion des congés (Administrateur/conges.xhtml) itère
    // sur adminPresenceBean.presences (List<Presence>) mais accède aux
    // propriétés d'une demande de congé : typeCongé, dateDebut, dateFin,
    // dateDepot, nbreJours, statut, dateAbsence. Ces accesseurs renvoient
    // des valeurs dérivées des champs réels de Presence pour rendre le
    // rendu compatible sans casser la sémantique de pointage.

    /** Du XHTML : {@code #{p.dateDebut}} — alias de la date de présence. */
    @Transient
    public LocalDate getDateDebut() { return datePresence; }

    /** Du XHTML : {@code #{p.dateFin}} — alias de la date de présence. */
    @Transient
    public LocalDate getDateFin() { return datePresence; }

    /** Du XHTML : {@code #{p.dateAbsence}} — date de l'absence ou null. */
    @Transient
    public LocalDate getDateAbsence() {
        return "ABSENCE".equalsIgnoreCase(type) ? datePresence : null;
    }

    /** Du XHTML : {@code #{p.dateDepot}} — date de saisie de l'entrée. */
    @Transient
    public LocalDate getDateDepot() {
        return dateCreation != null ? dateCreation.toLocalDate() : datePresence;
    }

    /** Du XHTML : {@code #{p.nbreJours}} — toujours 1 (pointage journalier). */
    @Transient
    public Integer getNbreJours() { return 1; }

    /** Du XHTML : {@code #{p.statut}} — déduit du flag de validation. */
    @Transient
    public String getStatut() {
        if (estValide) return "APPROUVE";
        if ("ABSENCE".equalsIgnoreCase(type) || "CONGE".equalsIgnoreCase(type)) return "EN_ATTENTE";
        return "APPROUVE";
    }

    /**
     * Du XHTML : {@code #{p.typeCongé.libelle}}.
     * Le nom de propriété contient un accent ; on expose à la fois la
     * version accentuée (utilisée dans le XHTML) et l'équivalent ASCII
     * {@link #getTypeConge()} pour les contextes EL plus stricts.
     */
    @Transient
    public TypeConge getTypeCongé() {
        if (motifAbsence == null) return null;
        TypeConge t = new TypeConge();
        t.setLibelle(motifAbsence);
        return t;
    }

    /** Équivalent ASCII de {@link #getTypeCongé()}. */
    @Transient
    public TypeConge getTypeConge() { return getTypeCongé(); }
}