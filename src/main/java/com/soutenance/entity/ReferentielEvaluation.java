package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Grille d'évaluation réutilisable pour un concours de recrutement
 * (correspond à ReferentielEvaluation du modèle gestion_rh).
 * Regroupe plusieurs phases (PhaseEvaluation), chacune composée de
 * rubriques et d'éléments de rubrique notés.
 */
@Entity
@Table(name = "referentiels_evaluation")
public class ReferentielEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(length = 20)
    private String statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @OneToMany(mappedBy = "referentiel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PhaseEvaluation> phases = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public List<PhaseEvaluation> getPhases() { return phases; }
    public void setPhases(List<PhaseEvaluation> phases) { this.phases = phases; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}
