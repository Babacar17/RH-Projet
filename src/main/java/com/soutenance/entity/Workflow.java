package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Ancienne entité workflow (table "workflows").
 *
 * CORRECTION : suppression de la relation @OneToMany vers EtapeValidation.
 *
 * CAUSE DE L'ERREUR AU DÉMARRAGE :
 *   Workflow.etapes était mappedBy="workflow", mais EtapeValidation.workflow
 *   a été migré vers WorkflowValidation. Hibernate détectait une incohérence
 *   de type et refusait de construire la SessionFactory.
 *
 * SOLUTION : cette ancienne entité ne gère plus les étapes directement.
 * Les étapes sont désormais portées par WorkflowValidation via @ElementCollection.
 *
 * NOTE : si vous n'utilisez plus du tout cette entité (Workflow),
 * vous pouvez la retirer de spring-config.xml (liste des entités annotatedClasses)
 * pour alléger le modèle. Elle est conservée ici pour compatibilité
 * avec les données existantes en table "workflows".
 */
@Entity
@Table(name = "workflows",
       uniqueConstraints = @UniqueConstraint(columnNames = {"module", "institution_id"}))
public class Workflow implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Module : CONGE, RECRUTEMENT, EVALUATION, MOUVEMENT_CARRIERE, FORMATION, PAIE
     */
    @Column(nullable = false, length = 100)
    private String module;

    /** ACTIF, INACTIF */
    @Column(length = 20)
    private String statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /**
     * CORRECTION : @OneToMany(mappedBy = "workflow") SUPPRIMÉ.
     *
     * Cette collection causait l'AnnotationException au démarrage car
     * EtapeValidation.workflow référence maintenant WorkflowValidation,
     * pas Workflow. Les deux types sont incompatibles pour Hibernate.
     *
     * Si vous avez besoin de lister les étapes d'un ancien workflow,
     * utilisez EtapeWorkflowDao.findByWorkflow(workflowId) directement.
     */

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}