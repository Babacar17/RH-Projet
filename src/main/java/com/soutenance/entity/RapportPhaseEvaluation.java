package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rapport transmis par le jury à l'autorité de décision à l'issue d'une
 * phase (synthèse des résultats + fichier joint). Correspond à
 * RapportPhaseEvaluation du modèle gestion_rh.
 */
@Entity
@Table(name = "rapports_phase")
public class RapportPhaseEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_offre_id", nullable = false)
    private PhaseOffre phaseOffre;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(length = 255)
    private String fichier;

    @Column(columnDefinition = "TEXT")
    private String notesAutorite;

    @Column(name = "candidats_retenus")
    private int candidatsRetenus;

    /** Contexte et besoin : création de poste, remplacement, renfort... */
    @Column(name = "motif_recrutement", columnDefinition = "TEXT")
    private String motifRecrutement;

    @Column(name = "candidatures_recevables")
    private Integer candidaturesRecevables;

    @Column(name = "candidatures_irrecevables")
    private Integer candidaturesIrrecevables;

    @Column(name = "motifs_exclusion", columnDefinition = "TEXT")
    private String motifsExclusion;

    @Column(name = "composition_comite", columnDefinition = "TEXT")
    private String compositionComite;

    @Column(name = "difficultes_ameliorations", columnDefinition = "TEXT")
    private String difficultesAmeliorations;

    /** Décision proposée à la direction et justification du choix. */
    @Column(name = "recommandation_decision", columnDefinition = "TEXT")
    private String recommandationDecision;

    private boolean transmis;
    private LocalDateTime dateTransmission;

    private boolean lu;
    private LocalDateTime dateLecture;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PhaseOffre getPhaseOffre() { return phaseOffre; }
    public void setPhaseOffre(PhaseOffre phaseOffre) { this.phaseOffre = phaseOffre; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getFichier() { return fichier; }
    public void setFichier(String fichier) { this.fichier = fichier; }

    public String getNotesAutorite() { return notesAutorite; }
    public void setNotesAutorite(String notesAutorite) { this.notesAutorite = notesAutorite; }

    public int getCandidatsRetenus() { return candidatsRetenus; }
    public void setCandidatsRetenus(int candidatsRetenus) { this.candidatsRetenus = candidatsRetenus; }

    public String getMotifRecrutement() { return motifRecrutement; }
    public void setMotifRecrutement(String v) { this.motifRecrutement = v; }

    public Integer getCandidaturesRecevables() { return candidaturesRecevables; }
    public void setCandidaturesRecevables(Integer v) { this.candidaturesRecevables = v; }

    public Integer getCandidaturesIrrecevables() { return candidaturesIrrecevables; }
    public void setCandidaturesIrrecevables(Integer v) { this.candidaturesIrrecevables = v; }

    public String getMotifsExclusion() { return motifsExclusion; }
    public void setMotifsExclusion(String v) { this.motifsExclusion = v; }

    public String getCompositionComite() { return compositionComite; }
    public void setCompositionComite(String v) { this.compositionComite = v; }

    public String getDifficultesAmeliorations() { return difficultesAmeliorations; }
    public void setDifficultesAmeliorations(String v) { this.difficultesAmeliorations = v; }

    public String getRecommandationDecision() { return recommandationDecision; }
    public void setRecommandationDecision(String v) { this.recommandationDecision = v; }

    public boolean isTransmis() { return transmis; }
    public void setTransmis(boolean transmis) { this.transmis = transmis; }

    public LocalDateTime getDateTransmission() { return dateTransmission; }
    public void setDateTransmission(LocalDateTime dateTransmission) { this.dateTransmission = dateTransmission; }

    public boolean isLu() { return lu; }
    public void setLu(boolean lu) { this.lu = lu; }

    public LocalDateTime getDateLecture() { return dateLecture; }
    public void setDateLecture(LocalDateTime dateLecture) { this.dateLecture = dateLecture; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}
