package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "experiences_professionnelles")
public class ExperienceProfessionnelle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(length = 200)
    private String employeur;

    @Column(length = 10)
    private String annee;

    @Column(length = 50)
    private String duree;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getEmployeur() { return employeur; }
    public void setEmployeur(String employeur) { this.employeur = employeur; }

    public String getAnnee() { return annee; }
    public void setAnnee(String annee) { this.annee = annee; }

    public String getDuree() { return duree; }
    public void setDuree(String duree) { this.duree = duree; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
