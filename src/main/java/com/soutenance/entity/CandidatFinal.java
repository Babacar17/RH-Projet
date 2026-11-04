package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Résultat final du concours pour une candidature (classement final,
 * décision de l'autorité). Correspond à CandidatFinal du modèle
 * gestion_rh — le passage à "RETENU" déclenche le recrutement effectif
 * (RHService.recruterCandidat).
 */
@Entity
@Table(name = "candidats_finaux", uniqueConstraints = @UniqueConstraint(columnNames = {"candidature_id"}))
public class CandidatFinal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreEmploi offre;

    /**
     * Phase dont les résultats ont servi à calculer ce rang/score — fige la
     * référence au moment de construireClassementFinal, pour que le score
     * et son maximum restent cohérents même si une phase supplémentaire est
     * ajoutée et clôturée plus tard sur le référentiel.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_offre_id")
    private PhaseOffre phaseOffre;

    private Integer rang;

    @Column(name = "nombre_points")
    private int nombrePoints;

    /** EN_ATTENTE, RETENU, NON_RETENU */
    @Column(length = 20)
    private String statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_id")
    private Employe autorite;

    private LocalDateTime dateDecision;

    @Column(columnDefinition = "TEXT")
    private String notesAutorite;

    @PrePersist
    protected void onCreate() {
        if (statut == null) statut = "EN_ATTENTE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public OffreEmploi getOffre() { return offre; }
    public void setOffre(OffreEmploi offre) { this.offre = offre; }

    public PhaseOffre getPhaseOffre() { return phaseOffre; }
    public void setPhaseOffre(PhaseOffre phaseOffre) { this.phaseOffre = phaseOffre; }

    public Integer getRang() { return rang; }
    public void setRang(Integer rang) { this.rang = rang; }

    public int getNombrePoints() { return nombrePoints; }
    public void setNombrePoints(int nombrePoints) { this.nombrePoints = nombrePoints; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Employe getAutorite() { return autorite; }
    public void setAutorite(Employe autorite) { this.autorite = autorite; }

    public LocalDateTime getDateDecision() { return dateDecision; }
    public void setDateDecision(LocalDateTime dateDecision) { this.dateDecision = dateDecision; }

    public String getNotesAutorite() { return notesAutorite; }
    public void setNotesAutorite(String notesAutorite) { this.notesAutorite = notesAutorite; }
}
