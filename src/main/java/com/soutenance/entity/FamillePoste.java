package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Famille / catégorie métier regroupant des postes similaires.
 *
 * Exemples : "Direction", "Ressources Humaines", "Informatique", "Finance"...
 *
 * Utilisée dans :
 *   - PosteBean.familleForm  → formulaire de création/édition
 *   - PosteBean.familles     → liste pour le filtre et le select
 *   - Poste.familleMetier    → référence texte (non FK pour l'instant)
 *
 * Migration SQL :
 *   CREATE TABLE familles_postes (
 *     id          BIGSERIAL PRIMARY KEY,
 *     nom         VARCHAR(100) NOT NULL UNIQUE,
 *     description TEXT,
 *     code        VARCHAR(20),
 *     actif       BOOLEAN NOT NULL DEFAULT true,
 *     cree_par    VARCHAR(100),
 *     date_creation DATE
 *   );
 */
@Entity
@Table(name = "familles_postes")
public class FamillePoste implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String code;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(length = 100)
    private String creePar;

    private LocalDate dateCreation;

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) dateCreation = LocalDate.now();
        if (code == null && nom != null)
            code = nom.toUpperCase().replaceAll("[^A-Z0-9]", "_").substring(
                0, Math.min(nom.length(), 20));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long    getId()                      { return id; }
    public void    setId(Long id)               { this.id = id; }

    public String  getNom()                     { return nom; }
    public void    setNom(String nom)           { this.nom = nom; }

    public String  getDescription()             { return description; }
    public void    setDescription(String d)     { this.description = d; }

    public String  getCode()                    { return code; }
    public void    setCode(String code)         { this.code = code; }

    public boolean isActif()                    { return actif; }
    public void    setActif(boolean actif)      { this.actif = actif; }

    public String  getCreePar()                 { return creePar; }
    public void    setCreePar(String s)         { this.creePar = s; }

    public LocalDate getDateCreation()          { return dateCreation; }
    public void      setDateCreation(LocalDate d) { this.dateCreation = d; }

    @Override
    public String toString() { return nom != null ? nom : ""; }
}