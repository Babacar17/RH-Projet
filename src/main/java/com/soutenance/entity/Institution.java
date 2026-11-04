package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité Institution — niveau racine du multi-tenancy.
 * Corrigée : champs typeInstitution, effectifCible, dateArchivage, raisonArchivage.
 */
@Entity
@Table(name = "institutions")
public class Institution implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String nom;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Type d'institution : MINISTERE, AGENCE, COLLECTIVITE,
     * ETABLISSEMENT_PUBLIC, DIRECTION_NATIONALE, AUTRE
     */
    @Column(name = "type_institution", length = 100)
    private String typeInstitution;

    @Column(length = 200)
    private String adresse;

    @Column(length = 100)
    private String ville;

    @Column(length = 100)
    private String pays;

    @Column(length = 20)
    private String telephone;

    @Column(length = 150)
    private String email;

    @Column(name = "site_web", length = 300)
    private String siteWeb;

    @Column(name = "logo_url", length = 300)
    private String logoUrl;

    /**
     * Modèle hiérarchique, stocké en chaîne ordonnée.
     * Ex : "Direction,Division,Service,Bureau"
     */
    @Column(name = "type_hierarchie", length = 500)
    private String typeHierarchie;

    /** Effectif théorique cible de l'institution. */
    @Column(name = "effectif_cible")
    private Integer effectifCible;

    /**
     * Statut : ACTIVE | CONFIG | INACTIVE | ARCHIVEE
     */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    /**
     * Indique si l'institution est active (non archivée/supprimée).
     * Colonne NOT NULL en base — initialisée à true par défaut.
     */
    @Column(nullable = false)
    private boolean actif = true;

    // ── Archivage ──────────────────────────────────────────────────────────

    @Column(name = "date_archivage")
    private LocalDate dateArchivage;

    @Column(name = "raison_archivage", columnDefinition = "TEXT")
    private String raisonArchivage;

    @Column(name = "archive_par", length = 100)
    private String archivePar;

    // ── Dates système ──────────────────────────────────────────────────────

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    // ── Relations inverses ─────────────────────────────────────────────────

    @OneToMany(mappedBy = "institution", fetch = FetchType.LAZY)
    private List<User> utilisateurs = new ArrayList<>();

    @OneToMany(mappedBy = "institution", fetch = FetchType.LAZY)
    private List<Direction> directions = new ArrayList<>();

    @OneToMany(mappedBy = "institution", fetch = FetchType.LAZY)
    private List<Employe> employes = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "CONFIG";
        if (pays == null)   pays   = "Sénégal";
        // actif est déjà initialisé à true au niveau du champ
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public List<String> getNiveauxHierarchie() {
        if (typeHierarchie == null || typeHierarchie.isBlank())
            return List.of("Direction", "Division", "Service", "Bureau");
        return List.of(typeHierarchie.split(","));
    }

    public int getNombreNiveaux() { return getNiveauxHierarchie().size(); }

    public String getNiveauRacine() {
        List<String> niveaux = getNiveauxHierarchie();
        return niveaux.isEmpty() ? "Direction" : niveaux.get(0);
    }

    public boolean isActif()  { return actif; }
    public void    setActif(boolean actif) { this.actif = actif; }

    public boolean isActive() { return "ACTIVE".equals(statut); }

    public boolean isArchivee() { return "ARCHIVEE".equals(statut); }

    /** Archive l'institution avec motif. */
    public void archiver(String operateur, String raison) {
        this.statut         = "ARCHIVEE";
        this.dateArchivage  = LocalDate.now();
        this.raisonArchivage = raison;
        this.archivePar     = operateur;
    }

    /** Taux d'occupation : effectif actuel / effectif cible. */
    public double getTauxOccupation() {
        if (effectifCible == null || effectifCible == 0) return 0.0;
        return (double) getNombreEmployes() / effectifCible * 100.0;
    }

    public int getNombreUtilisateurs() {
        return utilisateurs != null ? utilisateurs.size() : 0;
    }

    public int getNombreEmployes() {
        return employes != null ? employes.size() : 0;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code != null ? code.toUpperCase() : null; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTypeInstitution() { return typeInstitution; }
    public void setTypeInstitution(String typeInstitution) { this.typeInstitution = typeInstitution; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSiteWeb() { return siteWeb; }
    public void setSiteWeb(String siteWeb) { this.siteWeb = siteWeb; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getTypeHierarchie() { return typeHierarchie; }
    public void setTypeHierarchie(String typeHierarchie) { this.typeHierarchie = typeHierarchie; }

    public Integer getEffectifCible() { return effectifCible; }
    public void setEffectifCible(Integer effectifCible) { this.effectifCible = effectifCible; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDate getDateArchivage() { return dateArchivage; }
    public void setDateArchivage(LocalDate dateArchivage) { this.dateArchivage = dateArchivage; }

    public String getRaisonArchivage() { return raisonArchivage; }
    public void setRaisonArchivage(String raisonArchivage) { this.raisonArchivage = raisonArchivage; }

    public String getArchivePar() { return archivePar; }
    public void setArchivePar(String archivePar) { this.archivePar = archivePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public List<User> getUtilisateurs() { return utilisateurs; }
    public List<Direction> getDirections() { return directions; }
    public List<Employe> getEmployes() { return employes; }
}