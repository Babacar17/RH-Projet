package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Élément de salaire : prime, indemnité, retenue, cotisation.
 * Peut être rattaché à une grille salariale (élément fixe)
 * ou à un bulletin de paie spécifique (élément variable ou exceptionnel).
 */
@Entity
@Table(name = "elements_salaire")
public class ElementSalaire implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String libelle;

    /**
     * Type : PRIME, INDEMNITE, RETENUE, COTISATION_SALARIALE,
     *        COTISATION_PATRONALE, AVANTAGE_EN_NATURE, HEURES_SUP
     */
    @Column(nullable = false, length = 50)
    private String type;

    /**
     * Mode de calcul : FIXE, POURCENTAGE_SALAIRE_BASE,
     *                  POURCENTAGE_SALAIRE_BRUT, FORMULE
     */
    @Column(length = 50)
    private String modeCalcul;

    /** Valeur fixe (si modeCalcul = FIXE). */
    @Column(precision = 12, scale = 2)
    private BigDecimal valeurFixe;

    /** Taux en % (si modeCalcul = POURCENTAGE_*). */
    @Column(precision = 6, scale = 4)
    private BigDecimal tauxPourcentage;

    /** Formule de calcul libre (ex : "SALAIRE_BASE * 0.15 * NB_ENFANTS"). */
    @Column(columnDefinition = "TEXT")
    private String formule;

    /** Cet élément est-il imposable ? */
    @Column(name = "est_imposable")
    private boolean estImposable = true;

    /** Cet élément est-il soumis aux cotisations sociales ? */
    @Column(name = "est_cotisable")
    private boolean estCotisable = true;

    /** Grille salariale de référence (null si élément ponctuel sur bulletin). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grille_salariale_id")
    private GrilleSalariale grilleSalariale;

    /** Bulletin de paie auquel cet élément est rattaché (null si sur grille). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulletin_paie_id")
    private BulletinPaie bulletinPaie;

    /** ACTIF, INACTIF */
    @Column(length = 20)
    private String statut;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
        if (modeCalcul == null) modeCalcul = "FIXE";
    }

    /** Calcule le montant de cet élément en fonction du salaire de base. */
    public BigDecimal calculerMontant(BigDecimal salaireBase, BigDecimal salaireBrut) {
        return switch (modeCalcul != null ? modeCalcul : "FIXE") {
            case "FIXE"                    -> valeurFixe != null ? valeurFixe : BigDecimal.ZERO;
            case "POURCENTAGE_SALAIRE_BASE" -> salaireBase != null && tauxPourcentage != null
                                               ? salaireBase.multiply(tauxPourcentage).divide(BigDecimal.valueOf(100))
                                               : BigDecimal.ZERO;
            case "POURCENTAGE_SALAIRE_BRUT" -> salaireBrut != null && tauxPourcentage != null
                                               ? salaireBrut.multiply(tauxPourcentage).divide(BigDecimal.valueOf(100))
                                               : BigDecimal.ZERO;
            default                        -> valeurFixe != null ? valeurFixe : BigDecimal.ZERO;
        };
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getModeCalcul() { return modeCalcul; }
    public void setModeCalcul(String modeCalcul) { this.modeCalcul = modeCalcul; }

    public BigDecimal getValeurFixe() { return valeurFixe; }
    public void setValeurFixe(BigDecimal valeurFixe) { this.valeurFixe = valeurFixe; }

    public BigDecimal getTauxPourcentage() { return tauxPourcentage; }
    public void setTauxPourcentage(BigDecimal tauxPourcentage) { this.tauxPourcentage = tauxPourcentage; }

    public String getFormule() { return formule; }
    public void setFormule(String formule) { this.formule = formule; }

    public boolean isEstImposable() { return estImposable; }
    public void setEstImposable(boolean estImposable) { this.estImposable = estImposable; }

    public boolean isEstCotisable() { return estCotisable; }
    public void setEstCotisable(boolean estCotisable) { this.estCotisable = estCotisable; }

    public GrilleSalariale getGrilleSalariale() { return grilleSalariale; }
    public void setGrilleSalariale(GrilleSalariale grilleSalariale) { this.grilleSalariale = grilleSalariale; }

    public BulletinPaie getBulletinPaie() { return bulletinPaie; }
    public void setBulletinPaie(BulletinPaie bulletinPaie) { this.bulletinPaie = bulletinPaie; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}