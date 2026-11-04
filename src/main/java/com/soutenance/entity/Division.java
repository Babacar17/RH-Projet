package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "divisions")
public class Division implements Serializable {

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

    // Direction parente — OPTIONNELLE : certaines institutions n'ont pas
    // le niveau Direction dans leur hiérarchie (ex. MAE : Service,Bureau)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "direction_id")
    private Direction direction;

    // Institution de rattachement direct — renseignée systématiquement,
    // permet de scoper sans passer par la chaîne des parents
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    // Chef de division
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chef_id")
    private Employe chef;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    // Relation vers services
    @OneToMany(mappedBy = "division", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Service> services = new ArrayList<>();

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

    public int getNombreServices() {
        return services != null ? services.size() : 0;
    }

    // Nom complet : Direction > Division
    public String getNomComplet() {
        if (direction != null) return direction.getNom() + " › " + nom;
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
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    /** Institution effective : lien direct, sinon via la direction parente. */
    public Institution getInstitutionEffective() {
        if (institution != null) return institution;
        return direction != null ? direction.getInstitution() : null;
    }
    public Employe getChef() { return chef; }
    public void setChef(Employe chef) { this.chef = chef; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public List<Service> getServices() { return services; }
    public void setServices(List<Service> services) { this.services = services; }
}