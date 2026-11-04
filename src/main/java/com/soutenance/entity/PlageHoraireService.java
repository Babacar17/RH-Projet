package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalTime;

/**
 * Une plage horaire (ex: Matin, Soir, Nuit) d'un service à régime "par équipes".
 * L'ordre de rotation détermine l'enchaînement hebdomadaire des équipes sur
 * les plages — voir PlanningEquipeService.plageRotation().
 */
@Entity
@Table(name = "plages_horaire_service")
public class PlageHoraireService implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    /** Peut être antérieure à heureDebut (ex: Nuit 22h-06h) — traversée de minuit gérée par PlanningEquipeService. */
    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    @Column(name = "effectif_minimum")
    private int effectifMinimum;

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

    public LocalTime getHeureDebut() { return heureDebut; }
    public void setHeureDebut(LocalTime v) { this.heureDebut = v; }

    public LocalTime getHeureFin() { return heureFin; }
    public void setHeureFin(LocalTime v) { this.heureFin = v; }

    public int getEffectifMinimum() { return effectifMinimum; }
    public void setEffectifMinimum(int v) { this.effectifMinimum = v; }

    public int getOrdreRotation() { return ordreRotation; }
    public void setOrdreRotation(int v) { this.ordreRotation = v; }

    public String getCouleur() { return couleur; }
    public void setCouleur(String v) { this.couleur = v; }

    /** Traverse minuit si l'heure de fin est avant l'heure de début (ex: Nuit 22h-06h). */
    public boolean isTraverseMinuit() {
        return heureFin != null && heureDebut != null && heureFin.isBefore(heureDebut);
    }
}
