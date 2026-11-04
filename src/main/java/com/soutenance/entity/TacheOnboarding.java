package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tâche d'intégration d'un nouvel employé.
 *
 * Chaque nouvelle embauche déclenche la création d'une checklist standard
 * d'environ 10 tâches (badge, ordinateur, accès SIRH, briefing sécurité,
 * documents administratifs, formation initiale, etc.).
 *
 * Catégories : LOGISTIQUE, ACCES, ADMINISTRATIF, FORMATION, INTEGRATION.
 * Responsables : RH, IT, MANAGER, SUPERVISEUR.
 * Statuts : A_FAIRE, EN_COURS, FAIT, BLOQUE.
 */
@Entity
@Table(name = "onboarding_taches",
       indexes = {
           @Index(name = "idx_onb_employe", columnList = "employe_id"),
           @Index(name = "idx_onb_statut",  columnList = "statut")
       })
public class TacheOnboarding implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** LOGISTIQUE, ACCES, ADMINISTRATIF, FORMATION, INTEGRATION */
    @Column(nullable = false, length = 30)
    private String categorie;

    /** RH, IT, MANAGER, SUPERVISEUR — qui doit faire la tâche */
    @Column(nullable = false, length = 30)
    private String responsable = "RH";

    /** A_FAIRE, EN_COURS, FAIT, BLOQUE */
    @Column(nullable = false, length = 20)
    private String statut = "A_FAIRE";

    /** Ordre d'affichage dans la checklist (1, 2, 3…). */
    private Integer ordre;

    /** Date limite pour réaliser la tâche (J+N après embauche). */
    @Column(name = "date_limite")
    private LocalDate dateLimite;

    @Column(name = "date_realisation")
    private LocalDate dateRealisation;

    @Column(length = 100)
    private String realisePar;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "A_FAIRE";
    }

    public boolean isFait() { return "FAIT".equalsIgnoreCase(statut); }

    public boolean isEnRetard() {
        return !isFait() && dateLimite != null
            && LocalDate.now().isAfter(dateLimite);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public LocalDate getDateLimite() { return dateLimite; }
    public void setDateLimite(LocalDate dateLimite) { this.dateLimite = dateLimite; }

    public LocalDate getDateRealisation() { return dateRealisation; }
    public void setDateRealisation(LocalDate dateRealisation) { this.dateRealisation = dateRealisation; }

    public String getRealisePar() { return realisePar; }
    public void setRealisePar(String realisePar) { this.realisePar = realisePar; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}
