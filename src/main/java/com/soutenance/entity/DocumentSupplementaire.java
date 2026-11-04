package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/** Pièce ajoutée librement par le candidat, en plus des documents requis par l'offre. */
@Entity
@Table(name = "documents_supplementaires")
public class DocumentSupplementaire implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(nullable = false, length = 255)
    private String url;

    public DocumentSupplementaire() {}

    public DocumentSupplementaire(Candidature candidature, String libelle, String url) {
        this.candidature = candidature;
        this.libelle = libelle;
        this.url = url;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidature getCandidature() { return candidature; }
    public void setCandidature(Candidature candidature) { this.candidature = candidature; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
