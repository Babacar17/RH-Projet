package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulletin de paie mensuel d'un employé.
 * Rattaché à un CyclePaie et contient la liste détaillée des éléments de rémunération.
 */
@Entity
@Table(name = "bulletins_paie",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "cycle_paie_id"}))
public class BulletinPaie implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_paie_id", nullable = false)
    private CyclePaie cyclePaie;

    // ── Données de rémunération brute ──────────────────────────────────────

    @Column(precision = 12, scale = 2)
    private BigDecimal salaireBase;

    /** Total des primes et indemnités. */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalPrimes = BigDecimal.ZERO;

    /** Total des heures supplémentaires. */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalHeuresSupp = BigDecimal.ZERO;

    /** Avantages en nature valorisés. */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAvantages = BigDecimal.ZERO;

    /** Salaire brut = base + primes + heures sup + avantages. */
    @Column(precision = 12, scale = 2)
    private BigDecimal salaireBrut;

    // ── Retenues ───────────────────────────────────────────────────────────

    /** Total cotisations salariales (IPRES, CSS…). */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalCotisationsSalariales = BigDecimal.ZERO;

    /** Impôt sur le revenu (IR / IRPP). */
    @Column(precision = 12, scale = 2)
    private BigDecimal impotRevenu = BigDecimal.ZERO;

    /** Autres retenues diverses. */
    @Column(precision = 12, scale = 2)
    private BigDecimal autresRetenues = BigDecimal.ZERO;

    /** Total des retenues. */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalRetenues;

    // ── Net ────────────────────────────────────────────────────────────────

    /** Salaire net à payer = brut - retenues. */
    @Column(precision = 12, scale = 2)
    private BigDecimal salaireNet;

    /** Cotisations patronales (pour information). */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalCotisationsPatronales = BigDecimal.ZERO;

    // ── Présence ───────────────────────────────────────────────────────────

    private Integer joursOuvres;
    private Integer joursTravailles;
    private Integer joursConge;
    private Integer joursAbsence;
    private Double heuresTravaillees;
    private Double heuresSupplementairesTotales;

    // ── Statut & traçabilité ───────────────────────────────────────────────

    /** BROUILLON → CALCULE → VALIDE → PAYE → ANNULE */
    @Column(length = 20)
    private String statut;

    private LocalDate datePaiement;

    @Column(length = 100)
    private String modePaiement; // VIREMENT, CHEQUE, ESPECES

    /** URL du PDF généré pour ce bulletin. */
    @Column(length = 300)
    private String pdfUrl;

    @Column(length = 100)
    private String validePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    /** Éléments détaillés de ce bulletin (primes, retenues ligne par ligne). */
    @OneToMany(mappedBy = "bulletinPaie", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ElementSalaire> elements = new ArrayList<>();

    // ── Champs UI non persistés (utilisés par les pages JSF) ───────────────
    // Le XHTML (paie.xhtml) accède directement à #{paieBean.bulletin.institutionId},
    // #{paieBean.bulletin.observations}, #{paieBean.bulletin.cycle} et
    // #{paieBean.bulletin.lignes}. Ces propriétés n'existent pas en base ;
    // elles sont exposées ici en @Transient pour que l'EL JSF les résolve.

    /** Institution rattachée (déduite de l'employé, surchargée par l'UI si besoin). */
    @Transient
    private Long institutionId;

    /** Observation libre saisie dans le formulaire de cycle de paie. */
    @Transient
    private String observations;

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "BROUILLON";
        calculerTotaux();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
        calculerTotaux();
    }

    /** Recalcule brut, retenues totales et net. */
    public void calculerTotaux() {
        BigDecimal base   = salaireBase          != null ? salaireBase          : BigDecimal.ZERO;
        BigDecimal primes = totalPrimes          != null ? totalPrimes          : BigDecimal.ZERO;
        BigDecimal heures = totalHeuresSupp      != null ? totalHeuresSupp      : BigDecimal.ZERO;
        BigDecimal avt    = totalAvantages       != null ? totalAvantages       : BigDecimal.ZERO;

        salaireBrut = base.add(primes).add(heures).add(avt);

        BigDecimal cot  = totalCotisationsSalariales != null ? totalCotisationsSalariales : BigDecimal.ZERO;
        BigDecimal imp  = impotRevenu                != null ? impotRevenu                : BigDecimal.ZERO;
        BigDecimal aut  = autresRetenues             != null ? autresRetenues             : BigDecimal.ZERO;

        totalRetenues = cot.add(imp).add(aut);
        salaireNet    = salaireBrut.subtract(totalRetenues);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public CyclePaie getCyclePaie() { return cyclePaie; }
    public void setCyclePaie(CyclePaie cyclePaie) { this.cyclePaie = cyclePaie; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public BigDecimal getTotalPrimes() { return totalPrimes; }
    public void setTotalPrimes(BigDecimal totalPrimes) { this.totalPrimes = totalPrimes; }

    public BigDecimal getTotalHeuresSupp() { return totalHeuresSupp; }
    public void setTotalHeuresSupp(BigDecimal totalHeuresSupp) { this.totalHeuresSupp = totalHeuresSupp; }

    public BigDecimal getTotalAvantages() { return totalAvantages; }
    public void setTotalAvantages(BigDecimal totalAvantages) { this.totalAvantages = totalAvantages; }

    public BigDecimal getSalaireBrut() { return salaireBrut; }
    public void setSalaireBrut(BigDecimal salaireBrut) { this.salaireBrut = salaireBrut; }

    public BigDecimal getTotalCotisationsSalariales() { return totalCotisationsSalariales; }
    public void setTotalCotisationsSalariales(BigDecimal totalCotisationsSalariales) { this.totalCotisationsSalariales = totalCotisationsSalariales; }

    public BigDecimal getImpotRevenu() { return impotRevenu; }
    public void setImpotRevenu(BigDecimal impotRevenu) { this.impotRevenu = impotRevenu; }

    public BigDecimal getAutresRetenues() { return autresRetenues; }
    public void setAutresRetenues(BigDecimal autresRetenues) { this.autresRetenues = autresRetenues; }

    public BigDecimal getTotalRetenues() { return totalRetenues; }
    public void setTotalRetenues(BigDecimal totalRetenues) { this.totalRetenues = totalRetenues; }

    public BigDecimal getSalaireNet() { return salaireNet; }
    public void setSalaireNet(BigDecimal salaireNet) { this.salaireNet = salaireNet; }

    public BigDecimal getTotalCotisationsPatronales() { return totalCotisationsPatronales; }
    public void setTotalCotisationsPatronales(BigDecimal totalCotisationsPatronales) { this.totalCotisationsPatronales = totalCotisationsPatronales; }

    public Integer getJoursOuvres() { return joursOuvres; }
    public void setJoursOuvres(Integer joursOuvres) { this.joursOuvres = joursOuvres; }

    public Integer getJoursTravailles() { return joursTravailles; }
    public void setJoursTravailles(Integer joursTravailles) { this.joursTravailles = joursTravailles; }

    public Integer getJoursConge() { return joursConge; }
    public void setJoursConge(Integer joursConge) { this.joursConge = joursConge; }

    public Integer getJoursAbsence() { return joursAbsence; }
    public void setJoursAbsence(Integer joursAbsence) { this.joursAbsence = joursAbsence; }

    public Double getHeuresTravaillees() { return heuresTravaillees; }
    public void setHeuresTravaillees(Double heuresTravaillees) { this.heuresTravaillees = heuresTravaillees; }

    public Double getHeuresSupplementairesTotales() { return heuresSupplementairesTotales; }
    public void setHeuresSupplementairesTotales(Double heuresSupplementairesTotales) { this.heuresSupplementairesTotales = heuresSupplementairesTotales; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDate getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDate datePaiement) { this.datePaiement = datePaiement; }

    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }

    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }

    public String getValidePar() { return validePar; }
    public void setValidePar(String validePar) { this.validePar = validePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public List<ElementSalaire> getElements() { return elements; }
    public void setElements(List<ElementSalaire> elements) { this.elements = elements; }

    // ── Accesseurs transients pour l'EL JSF ────────────────────────────────

    /**
     * #{...bulletin.institutionId} — utilisé par le selectOneMenu du dialogue
     * « Cycle de paie ». Retourne la valeur saisie dans l'UI si présente,
     * sinon dérive l'institution de l'employé.
     */
    public Long getInstitutionId() {
        if (institutionId != null) return institutionId;
        if (employe != null) {
            try { return employe.getInstitutionId(); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }
    public void setInstitutionId(Long institutionId) { this.institutionId = institutionId; }

    /** #{...bulletin.observations} — commentaire libre du formulaire. */
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }

    /** #{...bulletin.cycle} — alias EL vers cyclePaie pour le XHTML existant. */
    @Transient
    public CyclePaie getCycle() { return cyclePaie; }
    public void setCycle(CyclePaie cycle) { this.cyclePaie = cycle; }

    /** #{...bulletin.lignes} — alias EL vers elements pour le XHTML existant. */
    @Transient
    public List<ElementSalaire> getLignes() { return elements; }
    public void setLignes(List<ElementSalaire> lignes) { this.elements = lignes; }

    // ── Vue agrégée façade « cycle » exposée par paie.xhtml ───────────────
    // Le dataTable des cycles itère sur paieBean.bulletins (List<BulletinPaie>)
    // et accède à : mois, libelle, totalBrut, avancement, nbEmployes,
    // datePaiementEffective, institution. Aucune de ces propriétés n'est en
    // base ; on les dérive du cycle de paie et de l'employé rattachés.

    /** Mois (1-12) du cycle de paie rattaché. */
    @Transient
    public Integer getMois() {
        return cyclePaie != null ? cyclePaie.getMois() : null;
    }

    /** Libellé lisible du bulletin : « Mois Année — Employé ». */
    @Transient
    public String getLibelle() {
        String per = cyclePaie != null ? cyclePaie.getPeriodeLibelle() : "—";
        String emp = (employe != null) ? employe.getNomComplet() : "?";
        return per + " — " + emp;
    }

    /** Total brut (alias du salaire brut individuel). */
    @Transient
    public BigDecimal getTotalBrut() {
        return salaireBrut != null ? salaireBrut : BigDecimal.ZERO;
    }

    /** Date de paiement effective déléguée au cycle. */
    @Transient
    public LocalDate getDatePaiementEffective() {
        return cyclePaie != null ? cyclePaie.getDatePaiementEffective() : null;
    }

    /** Nombre d'employés couverts par ce bulletin — toujours 1. */
    @Transient
    public Integer getNbEmployes() { return 1; }

    /** Avancement du traitement (0-100) déduit du statut. */
    @Transient
    public Integer getAvancement() {
        if (statut == null) return 0;
        return switch (statut) {
            case "BROUILLON"           -> 10;
            case "CALCULE", "EN_COURS" -> 60;
            case "VALIDE"              -> 90;
            case "PAYE", "CLOTURE"     -> 100;
            default                    -> 0;
        };
    }

    /** Institution rattachée — via l'employé. */
    @Transient
    public Institution getInstitution() {
        return employe != null ? employe.getInstitution() : null;
    }
}