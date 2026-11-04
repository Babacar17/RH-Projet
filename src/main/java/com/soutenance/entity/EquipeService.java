package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Une équipe (ex: Équipe A, B, C) d'un service à régime "par équipes".
 * L'ordre de rotation détermine sa position dans le cycle — voir
 * PlanningEquipeService.plageRotation().
 */
@Entity
@Table(name = "equipes_service")
public class EquipeService implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(name = "ordre_rotation")
    private int ordreRotation;

    @Column(length = 20)
    private String couleur;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public int getOrdreRotation() { return ordreRotation; }
    public void setOrdreRotation(int v) { this.ordreRotation = v; }

    public String getCouleur() { return couleur; }
    public void setCouleur(String v) { this.couleur = v; }
}
