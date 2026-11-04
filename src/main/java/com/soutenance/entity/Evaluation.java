package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluations")
public class Evaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "evaluateur_id")
    private Employe evaluateur;

    @Column(length = 50)
    private String typeEvaluation; // ANNUELLE, SEMESTRIELLE, PÉRIODE_ESSAI, PROMOTION

    @Column(length = 10)
    private String periode; // 2024, 2024-S1

    // Notes (sur 5)
    private BigDecimal noteObjectifs;
    private BigDecimal noteCompetences;
    private BigDecimal noteComportement;
    private BigDecimal notePonctualite;
    private BigDecimal noteGlobale; // Calculée automatiquement

    @Column(length = 50)
    private String mentionGlobale; // INSUFFISANT, PASSABLE, BIEN, TRES_BIEN, EXCELLENT

    @Column(columnDefinition = "TEXT")
    private String pointsForts;

    @Column(columnDefinition = "TEXT")
    private String pointsAmeliorer;

    @Column(columnDefinition = "TEXT")
    private String objectifsPeriodeSuivante;

    @Column(columnDefinition = "TEXT")
    private String commentaireRH;

    @Column(length = 20)
    private String statut; // EN_COURS, SOUMISE, VALIDEE, ARCHIVEE

    private LocalDate datePrevue;
    private LocalDate dateRealisation;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "EN_COURS";
    }

    @PreUpdate
    protected void onUpdate() {
        // Recalcul de la note globale
        if (noteObjectifs != null && noteCompetences != null
                && noteComportement != null && notePonctualite != null) {
            noteGlobale = noteObjectifs
                .add(noteCompetences)
                .add(noteComportement)
                .add(notePonctualite)
                .divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP);
            // Attribution de la mention
            int cmp2  = noteGlobale.compareTo(new BigDecimal("2.0"));
            int cmp3  = noteGlobale.compareTo(new BigDecimal("3.0"));
            int cmp4  = noteGlobale.compareTo(new BigDecimal("4.0"));
            int cmp45 = noteGlobale.compareTo(new BigDecimal("4.5"));
            if      (cmp2  < 0) mentionGlobale = "INSUFFISANT";
            else if (cmp3  < 0) mentionGlobale = "PASSABLE";
            else if (cmp4  < 0) mentionGlobale = "BIEN";
            else if (cmp45 < 0) mentionGlobale = "TRES_BIEN";
            else                 mentionGlobale = "EXCELLENT";
        }
    }

    // ── Getters / Setters ──────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }
    public Employe getEvaluateur() { return evaluateur; }
    public void setEvaluateur(Employe evaluateur) { this.evaluateur = evaluateur; }
    public String getTypeEvaluation() { return typeEvaluation; }
    public void setTypeEvaluation(String typeEvaluation) { this.typeEvaluation = typeEvaluation; }
    public String getPeriode() { return periode; }
    public void setPeriode(String periode) { this.periode = periode; }
    public BigDecimal getNoteObjectifs() { return noteObjectifs; }
    public void setNoteObjectifs(BigDecimal noteObjectifs) { this.noteObjectifs = noteObjectifs; }
    public BigDecimal getNoteCompetences() { return noteCompetences; }
    public void setNoteCompetences(BigDecimal noteCompetences) { this.noteCompetences = noteCompetences; }
    public BigDecimal getNoteComportement() { return noteComportement; }
    public void setNoteComportement(BigDecimal noteComportement) { this.noteComportement = noteComportement; }
    public BigDecimal getNotePonctualite() { return notePonctualite; }
    public void setNotePonctualite(BigDecimal notePonctualite) { this.notePonctualite = notePonctualite; }
    public BigDecimal getNoteGlobale() { return noteGlobale; }
    public void setNoteGlobale(BigDecimal noteGlobale) { this.noteGlobale = noteGlobale; }
    public String getMentionGlobale() { return mentionGlobale; }
    public void setMentionGlobale(String mentionGlobale) { this.mentionGlobale = mentionGlobale; }
    public String getPointsForts() { return pointsForts; }
    public void setPointsForts(String pointsForts) { this.pointsForts = pointsForts; }
    public String getPointsAmeliorer() { return pointsAmeliorer; }
    public void setPointsAmeliorer(String pointsAmeliorer) { this.pointsAmeliorer = pointsAmeliorer; }
    public String getObjectifsPeriodeSuivante() { return objectifsPeriodeSuivante; }
    public void setObjectifsPeriodeSuivante(String objectifsPeriodeSuivante) { this.objectifsPeriodeSuivante = objectifsPeriodeSuivante; }
    public String getCommentaireRH() { return commentaireRH; }
    public void setCommentaireRH(String commentaireRH) { this.commentaireRH = commentaireRH; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDate getDatePrevue() { return datePrevue; }
    public void setDatePrevue(LocalDate datePrevue) { this.datePrevue = datePrevue; }
    public LocalDate getDateRealisation() { return dateRealisation; }
    public void setDateRealisation(LocalDate dateRealisation) { this.dateRealisation = dateRealisation; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    /** Alias EL #{evaluation.mention} → mentionGlobale (evaluations.xhtml). */
    @Transient
    public String getMention() { return mentionGlobale; }
    public void setMention(String mention) { this.mentionGlobale = mention; }
}