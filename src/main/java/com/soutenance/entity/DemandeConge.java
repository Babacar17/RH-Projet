package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Demande de congé soumise par un employé.
 * Corrigé : liaison vers TypeConge (FK), support workflow multi-niveaux,
 * champs de validation par étape.
 */
@Entity
@Table(name = "demandes_conge")
public class DemandeConge implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    /**
     * Lien vers le référentiel TypeConge configurable.
     * Remplace l'ancien champ String typeConge.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_conge_id", nullable = false)
    private TypeConge typeConge;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    private Integer nombreJours;

    @Column(columnDefinition = "TEXT")
    private String motif;

    /** URL du justificatif uploadé (médical ou administratif). */
    @Column(length = 300)
    private String justificatifUrl;

    /**
     * Statut global :
     * EN_ATTENTE → EN_VALIDATION_MANAGER → EN_VALIDATION_RH
     * → APPROUVE → REFUSE → ANNULE
     */
    @Column(length = 30)
    private String statut;

    // ── Validation niveau 1 : Manager direct ──────────────────────────────

    @Column(length = 100)
    private String validationManager;   // APPROUVE, REFUSE, EN_ATTENTE

    @Column(length = 100)
    private String traitePar1;          // username manager

    private LocalDateTime dateTraitement1;

    @Column(columnDefinition = "TEXT")
    private String commentaireManager;

    // ── Validation niveau 2 : RH ───────────────────────────────────────────

    @Column(length = 100)
    private String validationRH;        // APPROUVE, REFUSE, EN_ATTENTE

    @Column(length = 100)
    private String traitePar2;          // username RH

    private LocalDateTime dateTraitement2;

    @Column(columnDefinition = "TEXT")
    private String commentaireRH;

    // ── Étape workflow courante ────────────────────────────────────────────

    /** Numéro de l'étape workflow en cours (1, 2…). */
    private Integer etapeActuelle;

    @Column(updatable = false)
    private LocalDateTime dateDemande;

    /** Évite de notifier le RH plusieurs fois pour la même demande restée en attente après la fin de sa période. */
    @Column(name = "relance_expiration_envoyee")
    private boolean relanceExpirationEnvoyee;

    @PrePersist
    protected void onCreate() {
        dateDemande = LocalDateTime.now();
        if (statut == null) statut = "EN_ATTENTE";
        if (etapeActuelle == null) etapeActuelle = 1;
        calculerNombreJours();
    }

    @PreUpdate
    protected void onUpdate() {
        calculerNombreJours();
    }

    private void calculerNombreJours() {
        if (dateDebut != null && dateFin != null) {
            nombreJours = (int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        }
    }

    /** True si la demande est dans un état terminal. */
    public boolean isTerminee() {
        return "APPROUVE".equals(statut)
            || "REFUSE".equals(statut)
            || "ANNULE".equals(statut);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public TypeConge getTypeConge() { return typeConge; }
    public void setTypeConge(TypeConge typeConge) { this.typeConge = typeConge; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public Integer getNombreJours() { return nombreJours; }
    public void setNombreJours(Integer nombreJours) { this.nombreJours = nombreJours; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public String getJustificatifUrl() { return justificatifUrl; }
    public void setJustificatifUrl(String justificatifUrl) { this.justificatifUrl = justificatifUrl; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getValidationManager() { return validationManager; }
    public void setValidationManager(String validationManager) { this.validationManager = validationManager; }

    public String getTraitePar1() { return traitePar1; }
    public void setTraitePar1(String traitePar1) { this.traitePar1 = traitePar1; }

    public LocalDateTime getDateTraitement1() { return dateTraitement1; }
    public void setDateTraitement1(LocalDateTime dateTraitement1) { this.dateTraitement1 = dateTraitement1; }

    public String getCommentaireManager() { return commentaireManager; }
    public void setCommentaireManager(String commentaireManager) { this.commentaireManager = commentaireManager; }

    public String getValidationRH() { return validationRH; }
    public void setValidationRH(String validationRH) { this.validationRH = validationRH; }

    public String getTraitePar2() { return traitePar2; }
    public void setTraitePar2(String traitePar2) { this.traitePar2 = traitePar2; }

    public LocalDateTime getDateTraitement2() { return dateTraitement2; }
    public void setDateTraitement2(LocalDateTime dateTraitement2) { this.dateTraitement2 = dateTraitement2; }

    public String getCommentaireRH() { return commentaireRH; }
    public void setCommentaireRH(String commentaireRH) { this.commentaireRH = commentaireRH; }

    public Integer getEtapeActuelle() { return etapeActuelle; }
    public void setEtapeActuelle(Integer etapeActuelle) { this.etapeActuelle = etapeActuelle; }

    public LocalDateTime getDateDemande() { return dateDemande; }

    public boolean isRelanceExpirationEnvoyee() { return relanceExpirationEnvoyee; }
    public void setRelanceExpirationEnvoyee(boolean v) { this.relanceExpirationEnvoyee = v; }

    // ── Aliases pour compatibilité avec ManagerService ─────────────────────

    /**
     * Alias de setTraitePar1 — le manager est le traitant de niveau 1.
     * Utilisé par ManagerService.approuverConge / refuserConge.
     */
    public void setTraitePar(String traitePar) { this.traitePar1 = traitePar; }
    public String getTraitePar()               { return traitePar1; }

    /**
     * Alias de setDateTraitement1.
     * Utilisé par ManagerService.approuverConge / refuserConge.
     */
    public void setDateTraitement(LocalDateTime dt) { this.dateTraitement1 = dt; }
    public LocalDateTime getDateTraitement()        { return dateTraitement1; }
}
