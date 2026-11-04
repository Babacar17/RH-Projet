package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "candidatures")
public class Candidature implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreEmploi offre;

    // Candidat lié au compte si existant
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User candidatUser;

    // Données du candidat (saisie directe si pas de compte)
    @Column(nullable = false, length = 100)
    private String nomCandidat;

    @Column(nullable = false, length = 100)
    private String prenomCandidat;

    @Column(nullable = false, length = 150)
    private String emailCandidat;

    @Column(length = 20)
    private String telephoneCandidat;

    @Column(columnDefinition = "TEXT")
    private String lettreMotivation;

    @Column(length = 255)
    private String cvUrl;

    @Column(length = 255)
    private String autresDocumentsUrl;

    /** Pièces supplémentaires ajoutées librement par le candidat. */
    @OneToMany(mappedBy = "candidature", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<DocumentSupplementaire> documentsSupplementaires = new ArrayList<>();

    /** Un fichier par document que l'offre exigeait (liste librement énumérée par le RH). */
    @OneToMany(mappedBy = "candidature", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentSoumisCandidature> documentsSoumis = new ArrayList<>();

    // Statut du dossier
    @Column(length = 30)
    private String statut; // RECUE, EN_COURS, ENTRETIEN, RETENUE, REFUSEE, RECRUTEE

    // Entretien
    private LocalDate dateEntretien;

    @Column(length = 50)
    private String typeEntretien; // PRESENTIEL, VISIO, TELEPHONIQUE

    @Column(columnDefinition = "TEXT")
    private String notesEntretien;

    private Integer noteEntretien; // /20

    @Column(columnDefinition = "TEXT")
    private String commentaireRH;

    @Column(updatable = false)
    private LocalDateTime dateCandidature;

    private LocalDateTime dateModification;

    /** CV du candidat — diplômes et expériences déclarés pour ce dossier. */
    @OneToMany(mappedBy = "candidature", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Diplome> diplomes = new ArrayList<>();

    @OneToMany(mappedBy = "candidature", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ExperienceProfessionnelle> experiences = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        dateCandidature = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "RECUE";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    public String getNomPrenomCandidat() {
        return prenomCandidat + " " + nomCandidat;
    }

    // ── Getters / Setters ──────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OffreEmploi getOffre() { return offre; }
    public void setOffre(OffreEmploi offre) { this.offre = offre; }
    public User getCandidatUser() { return candidatUser; }
    public void setCandidatUser(User candidatUser) { this.candidatUser = candidatUser; }
    public String getNomCandidat() { return nomCandidat; }
    public void setNomCandidat(String nomCandidat) { this.nomCandidat = nomCandidat; }
    public String getPrenomCandidat() { return prenomCandidat; }
    public void setPrenomCandidat(String prenomCandidat) { this.prenomCandidat = prenomCandidat; }
    public String getEmailCandidat() { return emailCandidat; }
    public void setEmailCandidat(String emailCandidat) { this.emailCandidat = emailCandidat; }
    public String getTelephoneCandidat() { return telephoneCandidat; }
    public void setTelephoneCandidat(String telephoneCandidat) { this.telephoneCandidat = telephoneCandidat; }
    public String getLettreMotivation() { return lettreMotivation; }
    public void setLettreMotivation(String lettreMotivation) { this.lettreMotivation = lettreMotivation; }
    public String getCvUrl() { return cvUrl; }
    public void setCvUrl(String cvUrl) { this.cvUrl = cvUrl; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDate getDateEntretien() { return dateEntretien; }
    public void setDateEntretien(LocalDate dateEntretien) { this.dateEntretien = dateEntretien; }
    public String getTypeEntretien() { return typeEntretien; }
    public void setTypeEntretien(String typeEntretien) { this.typeEntretien = typeEntretien; }
    public String getNotesEntretien() { return notesEntretien; }
    public void setNotesEntretien(String notesEntretien) { this.notesEntretien = notesEntretien; }
    public Integer getNoteEntretien() { return noteEntretien; }
    public void setNoteEntretien(Integer noteEntretien) { this.noteEntretien = noteEntretien; }
    public String getCommentaireRH() { return commentaireRH; }
    public void setCommentaireRH(String commentaireRH) { this.commentaireRH = commentaireRH; }
    public LocalDateTime getDateCandidature() { return dateCandidature; }
    public LocalDateTime getDateModification() { return dateModification; }
    public void setDateModification(java.time.LocalDateTime dateModification) { this.dateModification = dateModification; }
    public List<DocumentSupplementaire> getDocumentsSupplementaires() { return documentsSupplementaires; }
    public void setDocumentsSupplementaires(List<DocumentSupplementaire> d) { this.documentsSupplementaires = d; }

    public String getAutresDocumentsUrl() { return autresDocumentsUrl; }
    public void setAutresDocumentsUrl(String url) { this.autresDocumentsUrl = url; }

    public List<DocumentSoumisCandidature> getDocumentsSoumis() { return documentsSoumis; }
    public void setDocumentsSoumis(List<DocumentSoumisCandidature> v) { this.documentsSoumis = v; }

    public List<Diplome> getDiplomes() { return diplomes; }
    public void setDiplomes(List<Diplome> diplomes) { this.diplomes = diplomes; }

    public List<ExperienceProfessionnelle> getExperiences() { return experiences; }
    public void setExperiences(List<ExperienceProfessionnelle> experiences) { this.experiences = experiences; }
}