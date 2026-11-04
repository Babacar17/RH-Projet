package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Type de congé configurable par institution.
 *
 * Exemples : Congé annuel, Congé maladie, Congé maternité, RTT,
 *            Congé sans solde, Congé exceptionnel…
 *
 * Si {@code institution} est null, le type est global (commun à toutes
 * les institutions). Sinon il est propre à l'institution concernée.
 */
@Entity
@Table(name = "types_conge")
public class TypeConge implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Code technique unique du type (ex : "ANNUEL", "MALADIE", "MATERNITE").
     * Toujours stocké en majuscules.
     */
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    /** Libellé affiché dans les interfaces. */
    @Column(nullable = false, length = 200)
    private String libelle;

    /** Description fonctionnelle affichée dans le formulaire de demande. */
    @Column(columnDefinition = "TEXT")
    private String description;

    // ── CORRECTION : champs attendus par ConfigurationBean ───────────────────

    /** Nombre de jours maximum autorisés par an pour ce type de congé. */
    @Column(name = "nb_jours_max", nullable = false)
    private int nbJoursMax = 30;

    /** Ce type de congé est-il rémunéré (payé) ? */
    @Column(name = "payable", nullable = false)
    private boolean payable = true;

    /** Ce type de congé est-il actif et sélectionnable dans les demandes ? */
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    // ── Champs complémentaires ───────────────────────────────────────────────

    /**
     * Délai de prévenance minimum en jours avant la date de départ.
     * 0 = aucun délai requis.
     */
    @Column(name = "delai_prevenance_jours")
    private int delaiPrevenanceJours = 0;

    /**
     * Validation hiérarchique requise avant accord ?
     * Si false, la demande est automatiquement acceptée.
     */
    @Column(name = "validation_requise")
    private boolean validationRequise = true;

    /**
     * Institution propriétaire.
     * Null = type global, non null = type spécifique à cette institution.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @Column(name = "modifie_par", length = 100)
    private String modifiePar;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (code != null) code = code.toUpperCase().trim();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
        if (code != null) code = code.toUpperCase().trim();
    }

    // ── Constructeurs ────────────────────────────────────────────────────────

    public TypeConge() {}

    public TypeConge(String code, String libelle, int nbJoursMax,
                     boolean payable, boolean actif) {
        this.code       = code;
        this.libelle    = libelle;
        this.nbJoursMax = nbJoursMax;
        this.payable    = payable;
        this.actif      = actif;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ── CORRECTION : getters/setters des champs manquants ───────────────────

    public int getNbJoursMax() { return nbJoursMax; }
    public void setNbJoursMax(int nbJoursMax) { this.nbJoursMax = nbJoursMax; }

    public boolean isPayable() { return payable; }
    public void setPayable(boolean payable) { this.payable = payable; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    // ── Autres getters/setters ───────────────────────────────────────────────

    public int getDelaiPrevenanceJours() { return delaiPrevenanceJours; }
    public void setDelaiPrevenanceJours(int delaiPrevenanceJours) {
        this.delaiPrevenanceJours = delaiPrevenanceJours;
    }

    public boolean isValidationRequise() { return validationRequise; }
    public void setValidationRequise(boolean validationRequise) {
        this.validationRequise = validationRequise;
    }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public String getModifiePar() { return modifiePar; }
    public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    @Override
    public String toString() {
        return "TypeConge{code='" + code + "', libelle='" + libelle + "'}";
    }
}