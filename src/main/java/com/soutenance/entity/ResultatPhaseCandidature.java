package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Résultat consolidé (points cumulés, rang) d'une candidature à l'issue
 * d'une phase lancée — calculé à partir des NoteCandidatEvalElement.
 * Correspond à CandidatReussiPhasePoste du modèle gestion_rh.
 */
@Entity
@Table(name = "resultats_phase_candidature",
       uniqueConstraints = @UniqueConstraint(columnNames = {"phase_offre_id", "candidature_id"}))
public class ResultatPhaseCandidature implements Serializable {

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

    @Column(name = "nombre_points", nullable = false)
    private int nombrePoints;

    private Integer rang;

    /** ADMIS, ELIMINE, EN_ATTENTE */
    @Column(length = 20)
    private String statut;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PhaseOffre getPhaseOffre() { return phaseOffre; }
    public void setPhaseOffre(PhaseOffre phaseOffre) { this.phaseOffre = phaseOffre; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public int getNombrePoints() { return nombrePoints; }
    public void setNombrePoints(int nombrePoints) { this.nombrePoints = nombrePoints; }

    public Integer getRang() { return rang; }
    public void setRang(Integer rang) { this.rang = rang; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
}
