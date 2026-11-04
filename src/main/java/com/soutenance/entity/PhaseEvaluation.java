package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Étape d'un référentiel d'évaluation (ex : "Présélection dossier",
 * "Épreuve écrite", "Entretien oral"). Ordonnée par numeroOrdre au sein
 * de son ReferentielEvaluation.
 */
@Entity
@Table(name = "phases_evaluation")
public class PhaseEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referentiel_id", nullable = false)
    private ReferentielEvaluation referentiel;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "numero_ordre", nullable = false)
    private int numeroOrdre;

    /** Note minimale (sur 20) pour être admis à la phase suivante. */
    @Column(name = "note_eliminatoire")
    private Integer noteEliminatoire;

    @Column(length = 20)
    private String statut;

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Rubrique> rubriques = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReferentielEvaluation getReferentiel() { return referentiel; }
    public void setReferentiel(ReferentielEvaluation referentiel) { this.referentiel = referentiel; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public int getNumeroOrdre() { return numeroOrdre; }
    public void setNumeroOrdre(int numeroOrdre) { this.numeroOrdre = numeroOrdre; }

    public Integer getNoteEliminatoire() { return noteEliminatoire; }
    public void setNoteEliminatoire(Integer noteEliminatoire) { this.noteEliminatoire = noteEliminatoire; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public List<Rubrique> getRubriques() { return rubriques; }
    public void setRubriques(List<Rubrique> rubriques) { this.rubriques = rubriques; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}
