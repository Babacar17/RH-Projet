package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "diplomes")
public class Diplome implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(length = 100)
    private String mention;

    @Column(length = 200)
    private String etablissement;

    @Column(length = 10)
    private String annee;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getMention() { return mention; }
    public void setMention(String mention) { this.mention = mention; }

    public String getEtablissement() { return etablissement; }
    public void setEtablissement(String etablissement) { this.etablissement = etablissement; }

    public String getAnnee() { return annee; }
    public void setAnnee(String annee) { this.annee = annee; }
}
