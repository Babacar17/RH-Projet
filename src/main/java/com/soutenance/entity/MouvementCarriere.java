package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mouvement de carrière d'un employé.
 *
 * CORRECTION : ajout du champ nouvelleInstitution pour les mutations
 * inter-institutions (MUTATION, DETACHEMENT, MOBILITE).
 *
 * Avant : seul ancienDepartement / nouveauDepartement (String) existaient,
 * ce qui ne permettait pas de tracer vers quelle institution l'employé partait.
 *
 * Après : nouvelleInstitution (FK @ManyToOne) permet de :
 *   - Identifier l'institution de destination
 *   - Réaffecter l'employé lors de l'approbation (employe.setInstitution)
 *   - Filtrer les mutations par institution dans les rapports
 *
 * Migration SQL requise :
 *   ALTER TABLE mouvements_carriere
 *     ADD COLUMN nouvelle_institution_id BIGINT,
 *     ADD CONSTRAINT fk_mouvement_nouvelle_institution
 *       FOREIGN KEY (nouvelle_institution_id) REFERENCES institutions(id);
 */
@Entity
@Table(name = "mouvements_carriere")
public class MouvementCarriere implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    /**
     * Type de mouvement :
     * PROMOTION, MUTATION, MOBILITE, RETRAITE, DEMISSION, LICENCIEMENT,
     * DETACHEMENT, FIN_DETACHEMENT, SUSPENSION, REHABILITATION
     */
    @Column(length = 50)
    private String typeMouvement;

    // ── Situation AVANT ────────────────────────────────────────────────────

    @Column(length = 150)
    private String ancienPoste;

    @Column(length = 100)
    private String ancienDepartement;

    @Column(precision = 12, scale = 2)
    private BigDecimal ancienSalaire;

    @Column(length = 100)
    private String ancienGrade;

    // ── Situation APRÈS ────────────────────────────────────────────────────

    @Column(length = 150)
    private String nouveauPoste;

    @Column(length = 100)
    private String nouveauDepartement;

    @Column(precision = 12, scale = 2)
    private BigDecimal nouveauSalaire;

    @Column(length = 100)
    private String nouveauGrade;

    /**
     * CORRECTION : institution de destination pour les mutations inter-institutions.
     *
     * Null → mouvement interne (promotion, changement de service au sein de
     *        la même institution).
     * Non null → mutation vers une autre institution. Lors de l'approbation,
     *            RHService.approuverMouvement() réaffecte l'employé à cette
     *            institution.
     *
     * Types concernés : MUTATION, MOBILITE, DETACHEMENT
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nouvelle_institution_id")
    private Institution nouvelleInstitution;

    // ── Dates et traçabilité ───────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDate dateEffet;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @Column(columnDefinition = "TEXT")
    private String observations;

    /** EN_ATTENTE, APPROUVE, REJETE, APPLIQUE */
    @Column(length = 20)
    private String statut;

    @Column(length = 100)
    private String creePar;

    @Column(length = 100)
    private String approuvePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateApprobation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "EN_ATTENTE";
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * True si ce mouvement implique un changement d'institution.
     * Utilisé par RHService.approuverMouvement() pour décider
     * s'il faut réaffecter employe.institution.
     */
    public boolean isMutationInterInstitution() {
        return nouvelleInstitution != null
            && ("MUTATION".equals(typeMouvement)
                || "MOBILITE".equals(typeMouvement)
                || "DETACHEMENT".equals(typeMouvement));
    }

    /** Alias de getDateEffet() — utilisé par ReportingService. */
    public LocalDate getDateMouvement() { return dateEffet; }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public Employe getEmploye()                  { return employe; }
    public void setEmploye(Employe employe)      { this.employe = employe; }

    public String getTypeMouvement()             { return typeMouvement; }
    public void setTypeMouvement(String t)       { this.typeMouvement = t; }

    public String getAncienPoste()               { return ancienPoste; }
    public void setAncienPoste(String s)         { this.ancienPoste = s; }

    public String getAncienDepartement()         { return ancienDepartement; }
    public void setAncienDepartement(String s)   { this.ancienDepartement = s; }

    public BigDecimal getAncienSalaire()         { return ancienSalaire; }
    public void setAncienSalaire(BigDecimal v)   { this.ancienSalaire = v; }

    public String getAncienGrade()               { return ancienGrade; }
    public void setAncienGrade(String s)         { this.ancienGrade = s; }

    public String getNouveauPoste()              { return nouveauPoste; }
    public void setNouveauPoste(String s)        { this.nouveauPoste = s; }

    public String getNouveauDepartement()        { return nouveauDepartement; }
    public void setNouveauDepartement(String s)  { this.nouveauDepartement = s; }

    public BigDecimal getNouveauSalaire()        { return nouveauSalaire; }
    public void setNouveauSalaire(BigDecimal v)  { this.nouveauSalaire = v; }

    public String getNouveauGrade()              { return nouveauGrade; }
    public void setNouveauGrade(String s)        { this.nouveauGrade = s; }

    /** CORRECTION : getter/setter pour l'institution de destination. */
    public Institution getNouvelleInstitution()          { return nouvelleInstitution; }
    public void setNouvelleInstitution(Institution inst) { this.nouvelleInstitution = inst; }

    public LocalDate getDateEffet()              { return dateEffet; }
    public void setDateEffet(LocalDate d)        { this.dateEffet = d; }

    public String getMotif()                     { return motif; }
    public void setMotif(String s)               { this.motif = s; }

    public String getObservations()              { return observations; }
    public void setObservations(String s)        { this.observations = s; }

    public String getStatut()                    { return statut; }
    public void setStatut(String s)              { this.statut = s; }

    public String getCreePar()                   { return creePar; }
    public void setCreePar(String s)             { this.creePar = s; }

    public String getApprouvePar()               { return approuvePar; }
    public void setApprouvePar(String s)         { this.approuvePar = s; }

    public LocalDateTime getDateCreation()       { return dateCreation; }
    public LocalDateTime getDateApprobation()    { return dateApprobation; }
    public void setDateApprobation(LocalDateTime d) { this.dateApprobation = d; }
}