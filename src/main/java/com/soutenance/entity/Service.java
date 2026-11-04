package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "services")
public class Service implements Serializable {

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

    // Division parente — OPTIONNELLE : une institution dont la hiérarchie
    // commence au niveau Service (ex. MAE) crée ses services sans parent
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "division_id")
    private Division division;

    // Rattachement direct à une Direction (sans Division) — OPTIONNEL
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "direction_id")
    private Direction direction;

    // Rattachement à un Département — OPTIONNEL
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departement_id")
    private Departement departement;

    // Institution de rattachement direct
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    // Chef de service
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chef_id")
    private Employe chef;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    /**
     * FIXE (régime par défaut, géré via PlanningPresence) ou EQUIPES (postes
     * en rotation hebdomadaire entre plusieurs équipes — voir PlageHoraireService/
     * EquipeService/MembreEquipe gérés par PlanningEquipeService).
     */
    @Column(name = "regime_horaire", length = 20)
    private String regimeHoraire;

    /** Lundi de référence : l'équipe la plus ancienne (ordreRotation=0) occupe la plage ordreRotation=0 cette semaine-là. */
    @Column(name = "date_reference_rotation")
    private LocalDate dateReferenceRotation;

    // Relation vers bureaux
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Bureau> bureaux = new ArrayList<>();

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

    public int getNombreBureaux() {
        return bureaux != null ? bureaux.size() : 0;
    }

    // Nom complet : chemin réel (Direction › Division/Département › Service)
    public String getNomComplet() {
        if (division != null && division.getDirection() != null)
            return division.getDirection().getNom() + " › " + division.getNom() + " › " + nom;
        if (division != null)
            return division.getNom() + " › " + nom;
        if (departement != null)
            return departement.getNomComplet() + " › " + nom;
        if (direction != null)
            return direction.getNom() + " › " + nom;
        return nom;
    }

    /** Direction de rattachement, quel que soit le chemin (division, département ou direct). */
    public Direction getDirectionEffective() {
        if (division != null && division.getDirection() != null) return division.getDirection();
        if (departement != null && departement.getDirection() != null) return departement.getDirection();
        return direction;
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
    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public Departement getDepartement() { return departement; }
    public void setDepartement(Departement departement) { this.departement = departement; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    /** Institution effective : lien direct, sinon via la chaîne des parents. */
    public Institution getInstitutionEffective() {
        if (institution != null) return institution;
        if (division != null && division.getInstitutionEffective() != null)
            return division.getInstitutionEffective();
        if (departement != null && departement.getInstitutionEffective() != null)
            return departement.getInstitutionEffective();
        return direction != null ? direction.getInstitution() : null;
    }
    public Employe getChef() { return chef; }
    public void setChef(Employe chef) { this.chef = chef; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public List<Bureau> getBureaux() { return bureaux; }
    public void setBureaux(List<Bureau> bureaux) { this.bureaux = bureaux; }

    public String getRegimeHoraire() { return regimeHoraire; }
    public void setRegimeHoraire(String v) { this.regimeHoraire = v; }
    public boolean isRegimeEquipes() { return "EQUIPES".equals(regimeHoraire); }

    public LocalDate getDateReferenceRotation() { return dateReferenceRotation; }
    public void setDateReferenceRotation(LocalDate v) { this.dateReferenceRotation = v; }
}