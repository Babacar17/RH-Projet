package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Référentiel de compétences (skills) — partagé par toute l'institution.
 *
 * Catégories : TECHNIQUE, METIER, MANAGERIALE, LINGUISTIQUE, COMPORTEMENTALE.
 * Niveau attendu : 1 = Débutant, 2 = Intermédiaire, 3 = Confirmé, 4 = Expert.
 */
@Entity
@Table(name = "competences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"institution_id", "code"}),
       indexes = {
           @Index(name = "idx_comp_inst", columnList = "institution_id"),
           @Index(name = "idx_comp_cat",  columnList = "categorie")
       })
public class Competence implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** TECHNIQUE, METIER, MANAGERIALE, LINGUISTIQUE, COMPORTEMENTALE */
    @Column(nullable = false, length = 30)
    private String categorie;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** Pour le tri / ordre dans la matrice. */
    @Column(name = "ordre_affichage")
    private Integer ordreAffichage;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { dateCreation = LocalDateTime.now(); }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public Integer getOrdreAffichage() { return ordreAffichage; }
    public void setOrdreAffichage(Integer o) { this.ordreAffichage = o; }
    public LocalDateTime getDateCreation() { return dateCreation; }
}
