package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fiche de poste détaillée associée à un Poste.
 * Décrit les missions, compétences requises, conditions d'exercice.
 */
@Entity
@Table(name = "fiches_poste")
public class FichePoste implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Relation 1-1 avec le Poste. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poste_id", nullable = false, unique = true)
    private Poste poste;

    @Column(columnDefinition = "TEXT")
    private String missionPrincipale;

    @Column(columnDefinition = "TEXT")
    private String activitesPrincipales;

    @Column(columnDefinition = "TEXT")
    private String competencesTechniques;

    @Column(columnDefinition = "TEXT")
    private String competencesTransversales;

    /** Niveau d'études minimum requis. */
    @Column(length = 100)
    private String niveauEtudesRequis;

    /** Années d'expérience minimales requises. */
    private Integer experienceMinAnnees;

    @Column(columnDefinition = "TEXT")
    private String conditionsExercice;

    @Column(columnDefinition = "TEXT")
    private String relationsFonctionnelles;

    /** Supérieur hiérarchique direct (intitulé du poste). */
    @Column(length = 200)
    private String responsableHierarchique;

    /** Postes sous autorité directe (intitulés). */
    @Column(columnDefinition = "TEXT")
    private String postesSupervises;

    /** BROUILLON, VALIDEE, ARCHIVEE */
    @Column(length = 20)
    private String statut;

    // ══════════════════════════════════════════════════
    // CORRECTION : champ manquant appelé par PosteService (setValide / isValide)
    // ══════════════════════════════════════════════════

    /**
     * Indicateur booléen de validation.
     * Redondant avec statut = "VALIDEE" mais exposé pour compatibilité
     * avec PosteService.validerFichePoste() et tout bean JSF/REST existant.
     */
    private boolean valide;

    private LocalDate dateValidation;

    @Column(length = 100)
    private String validePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "BROUILLON";
        valide = false;
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    /**
     * Alias de missionPrincipale — utilisé par PosteBean.
     */
    public String getMissions() { return missionPrincipale; }
    public void   setMissions(String missions) { this.missionPrincipale = missions; }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }

    public String getMissionPrincipale() { return missionPrincipale; }
    public void setMissionPrincipale(String missionPrincipale) { this.missionPrincipale = missionPrincipale; }

    public String getActivitesPrincipales() { return activitesPrincipales; }
    public void setActivitesPrincipales(String activitesPrincipales) { this.activitesPrincipales = activitesPrincipales; }

    public String getCompetencesTechniques() { return competencesTechniques; }
    public void setCompetencesTechniques(String competencesTechniques) { this.competencesTechniques = competencesTechniques; }

    public String getCompetencesTransversales() { return competencesTransversales; }
    public void setCompetencesTransversales(String competencesTransversales) { this.competencesTransversales = competencesTransversales; }

    public String getNiveauEtudesRequis() { return niveauEtudesRequis; }
    public void setNiveauEtudesRequis(String niveauEtudesRequis) { this.niveauEtudesRequis = niveauEtudesRequis; }

    public Integer getExperienceMinAnnees() { return experienceMinAnnees; }
    public void setExperienceMinAnnees(Integer experienceMinAnnees) { this.experienceMinAnnees = experienceMinAnnees; }

    public String getConditionsExercice() { return conditionsExercice; }
    public void setConditionsExercice(String conditionsExercice) { this.conditionsExercice = conditionsExercice; }

    public String getRelationsFonctionnelles() { return relationsFonctionnelles; }
    public void setRelationsFonctionnelles(String relationsFonctionnelles) { this.relationsFonctionnelles = relationsFonctionnelles; }

    public String getResponsableHierarchique() { return responsableHierarchique; }
    public void setResponsableHierarchique(String responsableHierarchique) { this.responsableHierarchique = responsableHierarchique; }

    public String getPostesSupervises() { return postesSupervises; }
    public void setPostesSupervises(String postesSupervises) { this.postesSupervises = postesSupervises; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    // CORRECTION : getter/setter du champ valide
    public boolean isValide() { return valide; }
    public void setValide(boolean valide) { this.valide = valide; }

    public LocalDate getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDate dateValidation) { this.dateValidation = dateValidation; }

    public String getValidePar() { return validePar; }
    public void setValidePar(String validePar) { this.validePar = validePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}