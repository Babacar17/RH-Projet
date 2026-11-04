package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Avis donné par l'autorité de décision sur une candidature retenue en
 * finale. Correspond à AvisSelectionCandidat du modèle gestion_rh —
 * l'autorité est un Employe (DG, DRH...) plutôt qu'une entité dédiée.
 */
@Entity
@Table(name = "avis_selection_candidat")
public class AvisSelectionCandidat implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_id", nullable = false)
    private Employe autorite;

    /** FAVORABLE, DEFAVORABLE, RESERVE */
    @Column(nullable = false, length = 30)
    private String avis;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(updatable = false)
    private LocalDateTime dateAvis;

    @PrePersist
    protected void onCreate() {
        dateAvis = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public Employe getAutorite() { return autorite; }
    public void setAutorite(Employe autorite) { this.autorite = autorite; }

    public String getAvis() { return avis; }
    public void setAvis(String avis) { this.avis = avis; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateAvis() { return dateAvis; }
}
