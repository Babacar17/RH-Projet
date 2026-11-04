package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Procès-verbal de délibération du jury pour un appel d'offre — clôture
 * officiellement le concours. Correspond à PVDeliberation du modèle
 * gestion_rh.
 */
@Entity
@Table(name = "pv_deliberation")
public class PVDeliberation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreEmploi offre;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(length = 255)
    private String fichier;

    @Column(name = "nombre_membres")
    private int nombreMembres;

    @Column(length = 150)
    private String lieu;

    @Column(name = "heure_debut", length = 20)
    private String heureDebut;

    @Column(name = "heure_cloture", length = 20)
    private String heureCloture;

    /** Un membre par ligne : "Nom - Fonction - Présent/Absent/Excusé". */
    @Column(name = "membres_jury", columnDefinition = "TEXT")
    private String membresJury;

    @Column(name = "declaration_conflit_interet", columnDefinition = "TEXT")
    private String declarationConflitInteret;

    /** CONSENSUS ou VOTE. */
    @Column(name = "mode_decision", length = 20)
    private String modeDecision;

    @Column(name = "resultat_vote", columnDefinition = "TEXT")
    private String resultatVote;

    @Column(columnDefinition = "TEXT")
    private String reserves;

    /** Notification des candidats, délais de réponse, étapes suivantes. */
    @Column(name = "suites_a_donner", columnDefinition = "TEXT")
    private String suitesADonner;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OffreEmploi getOffre() { return offre; }
    public void setOffre(OffreEmploi offre) { this.offre = offre; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getFichier() { return fichier; }
    public void setFichier(String fichier) { this.fichier = fichier; }

    public int getNombreMembres() { return nombreMembres; }
    public void setNombreMembres(int nombreMembres) { this.nombreMembres = nombreMembres; }

    public String getLieu() { return lieu; }
    public void setLieu(String v) { this.lieu = v; }

    public String getHeureDebut() { return heureDebut; }
    public void setHeureDebut(String v) { this.heureDebut = v; }

    public String getHeureCloture() { return heureCloture; }
    public void setHeureCloture(String v) { this.heureCloture = v; }

    public String getMembresJury() { return membresJury; }
    public void setMembresJury(String v) { this.membresJury = v; }

    public String getDeclarationConflitInteret() { return declarationConflitInteret; }
    public void setDeclarationConflitInteret(String v) { this.declarationConflitInteret = v; }

    public String getModeDecision() { return modeDecision; }
    public void setModeDecision(String v) { this.modeDecision = v; }

    public String getResultatVote() { return resultatVote; }
    public void setResultatVote(String v) { this.resultatVote = v; }

    public String getReserves() { return reserves; }
    public void setReserves(String v) { this.reserves = v; }

    public String getSuitesADonner() { return suitesADonner; }
    public void setSuitesADonner(String v) { this.suitesADonner = v; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}
