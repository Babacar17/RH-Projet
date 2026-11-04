package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "bureaux")
public class Bureau implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(length = 20)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String statut; // ACTIVE, INACTIVE

    // Service parent — OPTIONNEL : hiérarchie flexible par institution
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    private Service service;

    // Institution de rattachement direct
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    // Chef de bureau
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chef_id")
    private Employe chef;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // Nom complet : Direction > Division > Service > Bureau
    public String getNomComplet() {
        if (service != null) return service.getNomComplet() + " › " + nom;
        return nom;
    }

    // ── Getters / Setters ──────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    /** Institution effective : lien direct, sinon via la chaîne des parents. */
    public Institution getInstitutionEffective() {
        if (institution != null) return institution;
        return service != null ? service.getInstitutionEffective() : null;
    }
    public Employe getChef() { return chef; }
    public void setChef(Employe chef) { this.chef = chef; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}