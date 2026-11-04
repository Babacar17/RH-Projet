package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Critère élémentaire noté par le jury (ex : "Maîtrise Java", "Expression orale").
 * Chaque élément a un nombre de points maximum ; la note du candidat est
 * enregistrée dans NoteCandidatEvalElement.
 */
@Entity
@Table(name = "elements_rubrique")
public class ElementRubrique implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubrique_id", nullable = false)
    private Rubrique rubrique;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "points_max", nullable = false)
    private int pointsMax = 20;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Rubrique getRubrique() { return rubrique; }
    public void setRubrique(Rubrique rubrique) { this.rubrique = rubrique; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public int getPointsMax() { return pointsMax; }
    public void setPointsMax(int pointsMax) { this.pointsMax = pointsMax; }
}
