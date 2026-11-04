package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "directions")
public class Direction implements Serializable {

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
    private String statut;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chef_id")
    private Employe chef;

    // ── CORRECTIONS : champs manquants pour directions.xhtml ─────────────────

    /** #{structureBean.direction.actif} */
    @Column(nullable = false)
    private boolean actif = true;

    /** #{structureBean.direction.type} */
    @Column(length = 50)
    private String type;

    /** #{structureBean.direction.anneeFiscale} */
    private Integer anneeFiscale;

    /** #{structureBean.direction.budget} */
    @Column(name = "budget", precision = 15, scale = 2)
    private java.math.BigDecimal budget;

    /** #{structureBean.direction.nombreEmployes} — calculé */
    @Transient
    private long nombreEmployes;

    /** #{structureBean.direction.parent} / #{structureBean.direction.parentId} */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Direction parent;

    /**
     * Sous-directions (enfants dans la hiérarchie).
     * Appelé par directions.xhtml via #{d.enfants} et #{dir.enfants}.
     * CORRECTION : EAGER pour éviter LazyInitializationException lors du
     * rendu JSF (hors session Hibernate).
     */
    @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER)
    private List<Direction> enfants = new ArrayList<>();

    /** #{structureBean.direction.responsableId} (transient, pour le formulaire) */
    @Transient
    private Long responsableId;

    /** #{structureBean.direction.postes} */
    @Transient
    private List<Poste> postes = new ArrayList<>();

    /**
     * Institution propriétaire de cette direction.
     *
     * La contrainte unique (nom, institution_id) est appliquée en base
     * pour permettre le même nom de direction dans deux institutions
     * différentes (ex : "Direction des RH" peut exister dans plusieurs ministères).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    @OneToMany(mappedBy = "direction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Division> divisions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    public int getNombreDivisions() {
        return divisions != null ? divisions.size() : 0;
    }

    public Long getInstitutionId() {
        return institution != null ? institution.getId() : null;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

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

    public Employe getChef() { return chef; }
    public void setChef(Employe chef) { this.chef = chef; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public List<Division> getDivisions() { return divisions; }
    public void setDivisions(List<Division> divisions) { this.divisions = divisions; }

    // ── Getters/Setters des champs ajoutés ───────────────────────────────────
    public boolean    isActif()                          { return actif; }
    public void       setActif(boolean a)                { this.actif = a; }
    public String     getType()                          { return type; }
    public void       setType(String t)                  { this.type = t; }
    public Integer    getAnneeFiscale()                  { return anneeFiscale; }
    public void       setAnneeFiscale(Integer a)         { this.anneeFiscale = a; }
    public java.math.BigDecimal getBudget()              { return budget; }
    public void       setBudget(java.math.BigDecimal b)  { this.budget = b; }
    public long       getNombreEmployes()                { return nombreEmployes; }
    public void       setNombreEmployes(long n)          { this.nombreEmployes = n; }
    public Direction  getParent()                        { return parent; }
    public void       setParent(Direction p)             { this.parent = p; }
    public Long       getParentId()                      { return parent != null ? parent.getId() : null; }
    public void       setParentId(Long id)               { if (parent == null) parent = new Direction(); parent.setId(id); }
    public List<Direction> getEnfants() {
        try {
            return (enfants != null) ? enfants : new ArrayList<>();
        } catch (org.hibernate.LazyInitializationException e) {
            return new ArrayList<>();
        }
    }
    public void setEnfants(List<Direction> e) { this.enfants = e; }
    /** #{structureBean.direction.responsable} — alias du chef */
    public Employe    getResponsable()                   { return chef; }
    public void       setResponsable(Employe e)          { this.chef = e; }
    public Long       getResponsableId()                 { return responsableId != null ? responsableId : (chef != null ? chef.getId() : null); }
    public void       setResponsableId(Long id)          { this.responsableId = id; }
    public List<Poste> getPostes()                       { return postes; }
    public void        setPostes(List<Poste> p)          { this.postes = p; }
}