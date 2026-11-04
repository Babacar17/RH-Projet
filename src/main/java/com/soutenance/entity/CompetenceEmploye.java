package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Niveau d'un employé sur une compétence donnée.
 *
 * Échelle de niveaux :
 *   0 = Non évalué
 *   1 = Débutant         (notions, accompagné)
 *   2 = Intermédiaire    (autonome sur tâches simples)
 *   3 = Confirmé         (autonome sur l'ensemble du périmètre)
 *   4 = Expert           (référent, capable de former)
 *
 * Mode d'évaluation : AUTO (auto-évaluation employé) ou MANAGER (évaluation hiérarchique).
 */
@Entity
@Table(name = "competences_employe",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "competence_id"}),
       indexes = {
           @Index(name = "idx_compemp_emp",  columnList = "employe_id"),
           @Index(name = "idx_compemp_comp", columnList = "competence_id")
       })
public class CompetenceEmploye implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "competence_id", nullable = false)
    private Competence competence;

    /** Niveau réel constaté : 0 à 4. */
    @Column(nullable = false)
    private Integer niveau = 0;

    /** Niveau attendu pour le poste (calculé ou défini ailleurs) : 0 à 4. */
    @Column(name = "niveau_attendu")
    private Integer niveauAttendu;

    /** AUTO, MANAGER */
    @Column(length = 20)
    private String mode = "MANAGER";

    @Column(name = "date_evaluation")
    private LocalDate dateEvaluation;

    @Column(length = 100)
    private String evaluateur;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PreUpdate @PrePersist
    protected void onSave() { dateModification = LocalDateTime.now(); }

    /** Écart positif si surperformance, négatif si en dessous des attentes. */
    public int getEcart() {
        if (niveauAttendu == null) return 0;
        return (niveau != null ? niveau : 0) - niveauAttendu;
    }

    public boolean isEnDeficit() { return getEcart() < 0; }
    public boolean isAuNiveau()  { return getEcart() == 0 && niveauAttendu != null; }
    public boolean isExpertise() { return getEcart() > 0; }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }
    public Competence getCompetence() { return competence; }
    public void setCompetence(Competence competence) { this.competence = competence; }
    public Integer getNiveau() { return niveau; }
    public void setNiveau(Integer niveau) { this.niveau = niveau; }
    public Integer getNiveauAttendu() { return niveauAttendu; }
    public void setNiveauAttendu(Integer n) { this.niveauAttendu = n; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public LocalDate getDateEvaluation() { return dateEvaluation; }
    public void setDateEvaluation(LocalDate d) { this.dateEvaluation = d; }
    public String getEvaluateur() { return evaluateur; }
    public void setEvaluateur(String evaluateur) { this.evaluateur = evaluateur; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public LocalDateTime getDateModification() { return dateModification; }
}
