package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Grille salariale de référence par institution.
 * Définit les niveaux de rémunération en fonction du corps/catégorie/échelon.
 */
@Entity
@Table(name = "grilles_salariales")
public class GrilleSalariale implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String libelle;

    /** Corps ou catégorie concerné(e). Ex : A1, Cadre, Technicien… */
    @Column(length = 100)
    private String corps;

    /** Échelon ou indice. Ex : 1, 2, 3 ou DEBUTANT, CONFIRME… */
    @Column(length = 50)
    private String echelon;

    /** Salaire de base correspondant. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal salaireBase;

    /** Indice majoré (fonction publique). */
    private Integer indiceMajore;

    /** Date d'entrée en vigueur de cette grille. */
    @Column(nullable = false)
    private LocalDate dateEffet;

    /** Date de fin de validité (null = en cours). */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    /** ACTIVE, ARCHIVEE */
    @Column(length = 20)
    private String statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** Éléments salariaux fixes rattachés à cette grille (primes, indemnités forfaitaires…). */
    @OneToMany(mappedBy = "grilleSalariale", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ElementSalaire> elements = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    // ── Champs UI non persistés (utilisés par paie.xhtml) ──────────────────
    // Le XHTML accède à #{paieBean.grille.categorie}, .code, .type, .taux,
    // .actif, .modeCalcul, .indemTransport, .indemResidence,
    // .indemRepresentation, .primeRisque. Aucune de ces propriétés n'existe
    // en colonne sur la table grilles_salariales ; on les expose ici en
    // @Transient pour que l'EL JSF les résolve sans toucher au schéma.

    @Transient private String     categorie;
    @Transient private String     code;
    @Transient private String     type;
    @Transient private Integer    taux;
    @Transient private Boolean    actifUi;
    @Transient private String     modeCalcul;
    @Transient private BigDecimal indemTransport;
    @Transient private BigDecimal indemResidence;
    @Transient private BigDecimal indemRepresentation;
    @Transient private BigDecimal primeRisque;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "ACTIVE";
    }

    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return "ACTIVE".equals(statut)
            && !today.isBefore(dateEffet)
            && (dateFin == null || !today.isAfter(dateFin));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }

    public String getEchelon() { return echelon; }
    public void setEchelon(String echelon) { this.echelon = echelon; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public Integer getIndiceMajore() { return indiceMajore; }
    public void setIndiceMajore(Integer indiceMajore) { this.indiceMajore = indiceMajore; }

    public LocalDate getDateEffet() { return dateEffet; }
    public void setDateEffet(LocalDate dateEffet) { this.dateEffet = dateEffet; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public List<ElementSalaire> getElements() { return elements; }
    public void setElements(List<ElementSalaire> elements) { this.elements = elements; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    // ── Accesseurs UI (transient) ──────────────────────────────────────────
    // Les getters tombent sur la valeur saisie côté UI si présente, sinon
    // déduisent une valeur par défaut à partir des champs persistés.

    public String getCategorie() {
        return categorie != null ? categorie : (corps != null ? corps : "");
    }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getCode() {
        if (code != null) return code;
        if (corps != null && echelon != null) return corps + "-" + echelon;
        return echelon != null ? echelon : "";
    }
    public void setCode(String code) { this.code = code; }

    public String getType() {
        if (type != null) return type;
        if (libelle == null) return "STANDARD";
        String lib = libelle.toUpperCase();
        if (lib.contains("CADRE"))      return "CADRE";
        if (lib.contains("TECHNICIEN")) return "TECHNICIEN";
        if (lib.contains("AGENT"))      return "AGENT";
        return "STANDARD";
    }
    public void setType(String type) { this.type = type; }

    public Integer getTaux() {
        if (taux != null) return taux;
        return indiceMajore != null ? indiceMajore : 0;
    }
    public void setTaux(Integer taux) { this.taux = taux; }

    public boolean isActif() {
        if (actifUi != null) return actifUi;
        return isActive();
    }
    public void setActif(boolean actif) { this.actifUi = actif; }

    public String getModeCalcul() {
        if (modeCalcul != null) return modeCalcul;
        return (indiceMajore != null && indiceMajore > 0) ? "INDICE" : "FIXE";
    }
    public void setModeCalcul(String modeCalcul) { this.modeCalcul = modeCalcul; }

    public BigDecimal getIndemTransport() {
        return indemTransport != null ? indemTransport : BigDecimal.ZERO;
    }
    public void setIndemTransport(BigDecimal v) { this.indemTransport = v; }

    public BigDecimal getIndemResidence() {
        return indemResidence != null ? indemResidence : BigDecimal.ZERO;
    }
    public void setIndemResidence(BigDecimal v) { this.indemResidence = v; }

    public BigDecimal getIndemRepresentation() {
        return indemRepresentation != null ? indemRepresentation : BigDecimal.ZERO;
    }
    public void setIndemRepresentation(BigDecimal v) { this.indemRepresentation = v; }

    public BigDecimal getPrimeRisque() {
        return primeRisque != null ? primeRisque : BigDecimal.ZERO;
    }
    public void setPrimeRisque(BigDecimal v) { this.primeRisque = v; }
}