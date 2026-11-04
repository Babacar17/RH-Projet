package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cycle de paie mensuel d'une institution.
 * Regroupe tous les bulletins du mois concerné.
 */
@Entity
@Table(name = "cycles_paie",
       uniqueConstraints = @UniqueConstraint(columnNames = {"institution_id", "annee", "mois"}))
public class CyclePaie implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false)
    private Integer annee;

    /** Mois de 1 à 12. */
    @Column(nullable = false)
    private Integer mois;

    /** Date de début de la période de paie. */
    private LocalDate dateDebut;

    /** Date de fin de la période de paie. */
    private LocalDate dateFin;

    /**
     * Statut : OUVERT → EN_CALCUL → CALCULE → VALIDE → PAYE → CLOTURE
     */
    @Column(length = 20)
    private String statut;

    private LocalDate datePaiementPrevue;
    private LocalDate datePaiementEffective;

    /** Budget que le RH sait pouvoir mobiliser pour ce cycle (optionnel). */
    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal budgetDisponible;

    /** Empêche de renvoyer plusieurs fois le rappel « versement proche » (scheduler). */
    @Column(name = "alerte_versement_envoyee")
    private boolean alerteVersementEnvoyee = false;

    @Column(length = 100)
    private String validePar;

    @Column(length = 100)
    private String cloturePar;

    @OneToMany(mappedBy = "cyclePaie", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BulletinPaie> bulletins = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "OUVERT";
    }

    public String getPeriodeLibelle() {
        String[] moisNoms = {"Janvier","Février","Mars","Avril","Mai","Juin",
                             "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};
        return (mois != null && mois >= 1 && mois <= 12 ? moisNoms[mois - 1] : "?") + " " + annee;
    }

    public int getNombreBulletins() {
        return bulletins != null ? bulletins.size() : 0;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public Integer getAnnee() { return annee; }
    public void setAnnee(Integer annee) { this.annee = annee; }

    public Integer getMois() { return mois; }
    public void setMois(Integer mois) { this.mois = mois; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDate getDatePaiementPrevue() { return datePaiementPrevue; }
    public void setDatePaiementPrevue(LocalDate datePaiementPrevue) { this.datePaiementPrevue = datePaiementPrevue; }

    public LocalDate getDatePaiementEffective() { return datePaiementEffective; }
    public void setDatePaiementEffective(LocalDate datePaiementEffective) { this.datePaiementEffective = datePaiementEffective; }

    public java.math.BigDecimal getBudgetDisponible() { return budgetDisponible; }
    public void setBudgetDisponible(java.math.BigDecimal budgetDisponible) { this.budgetDisponible = budgetDisponible; }

    public boolean isAlerteVersementEnvoyee() { return alerteVersementEnvoyee; }
    public void setAlerteVersementEnvoyee(boolean alerteVersementEnvoyee) { this.alerteVersementEnvoyee = alerteVersementEnvoyee; }

    public String getValidePar() { return validePar; }
    public void setValidePar(String validePar) { this.validePar = validePar; }

    public String getCloturePar() { return cloturePar; }
    public void setCloturePar(String cloturePar) { this.cloturePar = cloturePar; }

    public List<BulletinPaie> getBulletins() { return bulletins; }
    public void setBulletins(List<BulletinPaie> bulletins) { this.bulletins = bulletins; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}