package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comité / jury d'évaluation constitué pour un appel d'offre (concours).
 * Correspond à Comite du modèle gestion_rh ; ses membres sont des employés
 * de l'institution (voir ComiteEvaluateur).
 */
@Entity
@Table(name = "comites")
public class Comite implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id")
    private OffreEmploi offre;

    @Column(length = 20)
    private String statut;

    @OneToMany(mappedBy = "comite", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ComiteEvaluateur> membres = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public OffreEmploi getOffre() { return offre; }
    public void setOffre(OffreEmploi offre) { this.offre = offre; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public List<ComiteEvaluateur> getMembres() { return membres; }
    public void setMembres(List<ComiteEvaluateur> membres) { this.membres = membres; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}
