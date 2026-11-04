package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Jours fériés configurables par institution et/ou par région.
 * Utilisé pour le calcul des congés, de la paie et du planning.
 */
@Entity
@Table(name = "jours_feries")
public class JourFerie implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(nullable = false)
    private LocalDate date;

    /**
     * Type : NATIONAL (commun à tous), RELIGIEUX, REGIONAL, INSTITUTIONNEL
     * Les jours NATIONAL sont appliqués automatiquement à toutes les institutions.
     */
    @Column(length = 30)
    private String type;

    /**
     * Ce jour est-il récurrent chaque année à la même date ?
     * Faux pour les fêtes à date variable (Tabaski, Korité…).
     */
    @Column(name = "recurrent")
    private boolean recurrent = true;

    /** Région concernée (null = national). */
    @Column(length = 100)
    private String region;

    /**
     * NULL → s'applique à toutes les institutions (jour férié national).
     * non NULL → jour férié spécifique à cette institution.
     *
     * NOTE : l'entity utilise un objet Institution (relation @ManyToOne),
     * pas un simple Long. Dans AdminPresenceService, utiliser setInstitution()
     * après avoir chargé l'objet via InstitutionDao.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (type == null) type = "NATIONAL";
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRecurrent() { return recurrent; }
    public void setRecurrent(boolean recurrent) { this.recurrent = recurrent; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}