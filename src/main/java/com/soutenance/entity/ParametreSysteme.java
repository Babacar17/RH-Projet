package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "parametres_systeme",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cle", "institution_id"}))
public class ParametreSysteme implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String cle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valeur;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String categorie;

    @Column(name = "modifiable_par", length = 100)
    private String modifiablePar;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(name = "modifie_par", length = 100)
    private String modifiePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    // ── CORRECTION : relation Institution ajoutée ────────────────────────────
    // nullable = true car les paramètres globaux ont institution = NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = true)
    private Institution institution;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Constructeurs ────────────────────────────────────────────────────────

    public ParametreSysteme() {}

    public ParametreSysteme(String cle, String valeur, String description,
                             String categorie, String modifiablePar) {
        this.cle           = cle;
        this.valeur        = valeur;
        this.description   = description;
        this.categorie     = categorie;
        this.modifiablePar = modifiablePar;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCle() { return cle; }
    public void setCle(String cle) { this.cle = cle; }

    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getModifiablePar() { return modifiablePar; }
    public void setModifiablePar(String modifiablePar) { this.modifiablePar = modifiablePar; }

    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(LocalDateTime dateModification) {
        this.dateModification = dateModification;
    }

    public String getModifiePar() { return modifiePar; }
    public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    // ── CORRECTION : getter/setter institution ───────────────────────────────
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    @Override
    public String toString() {
        return "ParametreSysteme{cle='" + cle + "', valeur='" + valeur + "'}";
    }
}