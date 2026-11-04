package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Référentiel des postes / métiers.
 *
 * Un Poste est la définition abstraite d'une fonction (ex : "Responsable paie").
 * Un Employe occupe un Poste à un instant donné.
 * Une FichePoste décrit en détail les missions et compétences requises.
 */
@Entity
@Table(name = "postes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"code", "institution_id"}))
public class Poste implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique par institution. Ex : RH-001, FIN-002 */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String intitule;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Famille métier : FINANCE, RH, INFORMATIQUE, JURIDIQUE, TECHNIQUE… */
    @Column(length = 100)
    private String famille;

    /** Corps ou catégorie : A, B, C (fonction publique) ou CADRE, TECHNICIEN… */
    @Column(length = 100)
    private String corps;

    /** Catégorie hiérarchique : A1, A2, B1… */
    @Column(length = 50)
    private String categorie;

    /** Effectif théorique prévu pour ce poste dans l'institution. */
    @Column(name = "effectif_cible")
    private Integer effectifCible;

    /** ACTIF, INACTIF, SUPPRIME */
    @Column(length = 20)
    private String statut;

    /**
     * Unité organisationnelle de rattachement (libellé libre).
     * Ex : "Direction des Ressources Humaines", "Service Informatique"…
     * CORRECTION : champ ajouté car utilisé dans PosteBean (filtrage par unite).
     */
    @Column(length = 200)
    private String unite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @OneToOne(mappedBy = "poste", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private FichePoste fichePoste;

    /** Employés actuellement affectés à ce poste. */
    @OneToMany(mappedBy = "poste", fetch = FetchType.LAZY)
    private List<Employe> employes = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    /** Nombre d'employés actuellement sur ce poste. */
    public int getEffectifActuel() {
        return employes != null
            ? (int) employes.stream().filter(e -> "ACTIF".equals(e.getStatut())).count()
            : 0;
    }

    /** Nombre de postes vacants (cible - actuel). */
    public int getNombreVacances() {
        if (effectifCible == null) return 0;
        return Math.max(0, effectifCible - getEffectifActuel());
    }

    public boolean isVacant() {
        return getNombreVacances() > 0;
    }

    public Long getInstitutionId() {
        return institution != null ? institution.getId() : null;
    }

    /**
     * CORRECTION : PosteBean utilise getStatutPoste() / setStatutPoste().
     * Le champ sous-jacent est "statut" — ces méthodes sont des alias.
     */
    public String getStatutPoste() { return statut; }
    public void   setStatutPoste(String statutPoste) { this.statut = statutPoste; }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getIntitule() { return intitule; }
    public void setIntitule(String intitule) { this.intitule = intitule; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFamille() { return famille; }
    public void setFamille(String famille) { this.famille = famille; }

    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public Integer getEffectifCible() { return effectifCible; }
    public void setEffectifCible(Integer effectifCible) { this.effectifCible = effectifCible; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getUnite() { return unite; }
    public void setUnite(String unite) { this.unite = unite; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public FichePoste getFichePoste() { return fichePoste; }
    public void setFichePoste(FichePoste fichePoste) { this.fichePoste = fichePoste; }

    public List<Employe> getEmployes() { return employes; }
    public void setEmployes(List<Employe> employes) { this.employes = employes; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    // ── Champs UI non persistés pour postes.xhtml ──────────────────────────
    // Le formulaire de saisie de poste expose : familleMetier, direction,
    // grade, salaireMin, missions, competences, niveauEtudes,
    // niveauHierarchique, anneesExperience. Ces données vivent normalement
    // dans FichePoste ; on les expose ici en transient pour le binding EL.

    @Transient private String     familleMetier;
    @Transient private String     direction;
    @Transient private String     grade;
    @Transient private BigDecimal salaireMin;
    @Transient private String     missions;
    @Transient private String     competences;
    @Transient private String     niveauEtudes;
    @Transient private String     niveauHierarchique;
    @Transient private Integer    anneesExperience;

    public String getFamilleMetier() {
        return familleMetier != null ? familleMetier : famille;
    }
    public void setFamilleMetier(String v) { this.familleMetier = v; }

    public String getDirection() { return direction; }
    public void setDirection(String v) { this.direction = v; }

    public String getGrade() { return grade; }
    public void setGrade(String v) { this.grade = v; }

    public BigDecimal getSalaireMin() { return salaireMin; }
    public void setSalaireMin(BigDecimal v) { this.salaireMin = v; }

    public String getMissions() {
        if (missions != null) return missions;
        return fichePoste != null ? fichePoste.getMissions() : null;
    }
    public void setMissions(String v) { this.missions = v; }

    public String getCompetences() {
        if (competences != null) return competences;
        return fichePoste != null ? fichePoste.getCompetencesTechniques() : null;
    }
    public void setCompetences(String v) { this.competences = v; }

    public String getNiveauEtudes() {
        if (niveauEtudes != null) return niveauEtudes;
        return fichePoste != null ? fichePoste.getNiveauEtudesRequis() : null;
    }
    public void setNiveauEtudes(String v) { this.niveauEtudes = v; }

    public String getNiveauHierarchique() { return niveauHierarchique; }
    public void setNiveauHierarchique(String v) { this.niveauHierarchique = v; }

    public Integer getAnneesExperience() {
        if (anneesExperience != null) return anneesExperience;
        return fichePoste != null ? fichePoste.getExperienceMinAnnees() : null;
    }
    public void setAnneesExperience(Integer v) { this.anneesExperience = v; }
}