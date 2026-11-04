package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lancement concret d'une PhaseEvaluation pour un appel d'offre donné
 * (correspond à LancementPhasePoste du modèle gestion_rh). Une fois
 * lancée, les évaluateurs du Comite peuvent noter les candidatures
 * inscrites à cette phase via NoteCandidatEvalElement.
 */
@Entity
@Table(name = "phases_offre")
public class PhaseOffre implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreEmploi offre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private PhaseEvaluation phase;

    /** PLANIFIEE, EN_COURS, CLOTUREE */
    @Column(length = 20)
    private String statut;

    private LocalDateTime dateLancement;
    private LocalDateTime dateCloture;
    /**
     * Date de fin prévue par le RH — déclenche la clôture automatique de la
     * phase (PhaseOffreScheduler), en plus de servir de repère affiché au jury.
     */
    private LocalDateTime dateLimite;
    /**
     * Date de début prévue, saisie à la planification (statut PLANIFIEE) —
     * déclenche le lancement automatique dès qu'elle est atteinte et que la
     * phase précédente est clôturée (PhaseOffreScheduler).
     */
    private LocalDateTime dateDebutPrevue;

    @PrePersist
    protected void onCreate() {
        if (statut == null) statut = "PLANIFIEE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OffreEmploi getOffre() { return offre; }
    public void setOffre(OffreEmploi offre) { this.offre = offre; }

    public PhaseEvaluation getPhase() { return phase; }
    public void setPhase(PhaseEvaluation phase) { this.phase = phase; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDateTime getDateLancement() { return dateLancement; }
    public void setDateLancement(LocalDateTime dateLancement) { this.dateLancement = dateLancement; }

    public LocalDateTime getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDateTime dateCloture) { this.dateCloture = dateCloture; }

    public LocalDateTime getDateLimite() { return dateLimite; }
    public void setDateLimite(LocalDateTime dateLimite) { this.dateLimite = dateLimite; }

    public LocalDateTime getDateDebutPrevue() { return dateDebutPrevue; }
    public void setDateDebutPrevue(LocalDateTime dateDebutPrevue) { this.dateDebutPrevue = dateDebutPrevue; }

    public boolean isDelaiDepasse() {
        return dateLimite != null && "EN_COURS".equals(statut) && LocalDateTime.now().isAfter(dateLimite);
    }
}
