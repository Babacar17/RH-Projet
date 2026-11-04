package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Note attribuée par un évaluateur à une candidature pour un élément de
 * rubrique donné, dans le cadre d'une phase lancée (PhaseOffre).
 * Correspond à NoteCandidatEvalElement du modèle gestion_rh.
 */
@Entity
@Table(name = "notes_candidat_element",
       uniqueConstraints = @UniqueConstraint(columnNames = {"candidature_id", "element_id", "evaluateur_id"}))
public class NoteCandidatEvalElement implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_offre_id", nullable = false)
    private PhaseOffre phaseOffre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "element_id", nullable = false)
    private ElementRubrique element;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluateur_id", nullable = false)
    private Employe evaluateur;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    private LocalDateTime dateNotation;

    @PrePersist
    protected void onCreate() {
        dateNotation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PhaseOffre getPhaseOffre() { return phaseOffre; }
    public void setPhaseOffre(PhaseOffre phaseOffre) { this.phaseOffre = phaseOffre; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public ElementRubrique getElement() { return element; }
    public void setElement(ElementRubrique element) { this.element = element; }

    public Employe getEvaluateur() { return evaluateur; }
    public void setEvaluateur(Employe evaluateur) { this.evaluateur = evaluateur; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateNotation() { return dateNotation; }
}
