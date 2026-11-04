package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Workflow de validation configurable par type d'objet métier et par institution.
 *
 * Un workflow définit la séquence d'étapes de validation à franchir pour
 * qu'une demande (congé, recrutement, évaluation…) soit approuvée.
 *
 * Types d'objet supportés : CONGE | RECRUTEMENT | EVALUATION | MOUVEMENT | PAIE
 *
 * Règle : si {@code institution} est null, le workflow est global et s'applique
 * à toutes les institutions qui n'ont pas de workflow spécifique.
 */
@Entity
@Table(name = "workflow_validations")
public class WorkflowValidation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type d'objet concerné : CONGE, RECRUTEMENT, EVALUATION, MOUVEMENT, PAIE.
     * Non null — un workflow est toujours rattaché à un type précis.
     */
    @Column(name = "type_objet", nullable = false, length = 30)
    private String typeObjet;

    /**
     * Séquence ordonnée des étapes de validation.
     * Chaque étape est le libellé ou le rôle valideur.
     * Ex : ["Manager N+1", "DRH", "Direction générale"]
     *
     * Stocké en table secondaire pour éviter les colonnes CSV fragiles.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "workflow_etapes",
        joinColumns = @JoinColumn(name = "workflow_id")
    )
    @Column(name = "etape", length = 100)
    @OrderColumn(name = "ordre")
    private List<String> etapes = new ArrayList<>();

    /** Workflow actif et applicable. Si false, il est ignoré par le moteur. */
    @Column(name = "actif")
    private boolean actif = true;

    /**
     * Institution concernée.
     * Null → workflow global applicable à toutes les institutions.
     * Non null → workflow spécifique à cette institution.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    /** Description libre du workflow, affichée dans l'interface. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Délai maximum (en jours) avant relance automatique à chaque étape.
     * Null = pas de relance automatique.
     */
    @Column(name = "delai_relance_jours")
    private Integer delaiRelanceJours;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @Column(name = "modifie_par", length = 100)
    private String modifiePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Constructeurs ────────────────────────────────────────────────────────

    public WorkflowValidation() {}

    public WorkflowValidation(String typeObjet, List<String> etapes,
                               boolean actif, Institution institution) {
        this.typeObjet    = typeObjet;
        this.etapes       = etapes != null ? etapes : new ArrayList<>();
        this.actif        = actif;
        this.institution  = institution;
    }

    // ── Méthodes métier ──────────────────────────────────────────────────────

    /** Nombre d'étapes dans ce workflow. */
    public int getNbEtapes() {
        return etapes == null ? 0 : etapes.size();
    }

    /** Ajoute une étape en fin de workflow. */
    public void ajouterEtape(String etape) {
        if (etapes == null) etapes = new ArrayList<>();
        etapes.add(etape);
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTypeObjet() { return typeObjet; }
    public void setTypeObjet(String typeObjet) { this.typeObjet = typeObjet; }

    public List<String> getEtapes() { return etapes; }
    public void setEtapes(List<String> etapes) { this.etapes = etapes; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getDelaiRelanceJours() { return delaiRelanceJours; }
    public void setDelaiRelanceJours(Integer delaiRelanceJours) {
        this.delaiRelanceJours = delaiRelanceJours;
    }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public String getModifiePar() { return modifiePar; }
    public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    @Override
    public String toString() {
        return "WorkflowValidation{typeObjet='" + typeObjet + "', etapes=" + etapes + "}";
    }
}