package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Regroupement de critères notés au sein d'une PhaseEvaluation
 * (ex : "Compétences techniques", "Aptitudes managériales").
 */
@Entity
@Table(name = "rubriques")
public class Rubrique implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private PhaseEvaluation phase;

    @Column(nullable = false, length = 150)
    private String libelle;

    /** Coefficient de pondération de la rubrique dans la phase. */
    @Column(nullable = false)
    private int coefficient = 1;

    @OneToMany(mappedBy = "rubrique", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ElementRubrique> elements = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PhaseEvaluation getPhase() { return phase; }
    public void setPhase(PhaseEvaluation phase) { this.phase = phase; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public int getCoefficient() { return coefficient; }
    public void setCoefficient(int coefficient) { this.coefficient = coefficient; }

    public List<ElementRubrique> getElements() { return elements; }
    public void setElements(List<ElementRubrique> elements) { this.elements = elements; }
}
