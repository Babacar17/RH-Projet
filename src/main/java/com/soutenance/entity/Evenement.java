package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Événement officiel d'une institution.
 *
 * Types : REUNION, FORMATION, SEMINAIRE, ATELIER, CONFERENCE, AUTRE —
 *         chaque type pilote l'affichage d'un bloc de champs spécifiques
 *         et les règles de budget/capacité/présence/indemnité (cahier des
 *         charges « Gestion des événements », section 4).
 *
 * Workflow : PROGRAMME → CONFIRME → EN_COURS → TERMINE (ou ANNULE)
 *
 * Le cloisonnement par institution est assuré via institution_id NOT NULL.
 * Les participants sont gérés via {@link ParticipationEvenement} pour permettre
 * un statut de participation par employé (invité, confirmé, présent, absent).
 */
@Entity
@Table(name = "evenements",
       indexes = {
           @Index(name = "idx_evt_institution", columnList = "institution_id"),
           @Index(name = "idx_evt_dates",       columnList = "date_debut, date_fin"),
           @Index(name = "idx_evt_statut",      columnList = "statut")
       })
public class Evenement implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** SEMINAIRE, REUNION, VOYAGE, FORMATION_EXTERNE, WORKSHOP, CONFERENCE,
     *  JOURNEE_INTEGRATION, AUTRE */
    @Column(nullable = false, length = 30)
    private String type;

    /** PROGRAMME, CONFIRME, EN_COURS, TERMINE, ANNULE */
    @Column(nullable = false, length = 20)
    private String statut = "PROGRAMME";

    /** PRESENTIEL, DISTANCIEL, HYBRIDE */
    @Column(length = 20)
    private String modalite = "PRESENTIEL";

    @Column(length = 300)
    private String lieu;

    /** URL de la salle virtuelle (Zoom, Teams, Google Meet) si modalité DISTANCIEL/HYBRIDE. */
    @Column(length = 500)
    private String urlVisio;

    @Column(name = "date_debut", nullable = false)
    private LocalDateTime dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDateTime dateFin;

    /** Estimation du budget alloué à l'événement. */
    @Column(precision = 14, scale = 2)
    private BigDecimal budget;

    /**
     * Indemnité versée à chaque participant présent (séminaire rémunéré).
     * Null ou zéro = événement non rémunéré. Appliquée automatiquement sur le
     * bulletin de paie du mois de l'événement pour tout participant au statut PRESENT.
     */
    @Column(name = "indemnite_participant", precision = 12, scale = 2)
    private BigDecimal indemniteParticipant;

    @Column(length = 10)
    private String devise = "FCFA";

    /** Nombre maximum de participants (0 = illimité). */
    private Integer capaciteMax;

    /** URL du programme PDF, photo de couverture, etc. */
    @Column(length = 500)
    private String programmeUrl;

    @Column(length = 500)
    private String photoUrl;

    // ── Blocs spécifiques par type (cahier des charges §6) ──────────────────

    /** Réunion : ordre du jour. */
    @Column(name = "ordre_du_jour", columnDefinition = "TEXT")
    private String ordreDuJour;

    /** Réunion : AUCUNE, HEBDOMADAIRE, MENSUELLE. */
    @Column(length = 20)
    private String recurrence;

    /** Formation : formateur ou organisme (obligatoire pour ce type). */
    @Column(length = 200)
    private String formateur;

    @Column(name = "attestation_delivree")
    private boolean attestationDelivree = false;

    /** Séminaire : programme détaillé par session. */
    @Column(name = "programme_sessions", columnDefinition = "TEXT")
    private String programmeSessions;

    @Column(name = "restauration_prevue")
    private boolean restaurationPrevue = false;

    @Column(name = "hebergement_prevu")
    private boolean hebergementPrevu = false;

    private Integer nombreNuits;

    /** Atelier : prérequis et matériel nécessaire. */
    @Column(name = "prerequis_materiel", columnDefinition = "TEXT")
    private String prerequisMateriel;

    /** Conférence : intervenant (obligatoire pour ce type). */
    @Column(length = 200)
    private String intervenant;

    @Column(name = "ouvert_externe")
    private boolean ouvertExterne = false;

    // ── Verrou de validation des présences (RG-08 à RG-10) ──────────────────
    // Tant que presenceValidee=false, le marquage présent/absent reste modifiable
    // et AUCUNE indemnité n'est comptée côté paie. Une fois validée, l'opération
    // est figée : plus aucun marquage n'est accepté, et c'est ce verrou (et lui
    // seul) qui autorise EvenementDao.sumIndemnitesParticipations à compter cet
    // événement — garantissant qu'une indemnité n'est jamais ajoutée avant
    // validation, ni modifiée silencieusement après.

    @Column(name = "presence_validee")
    private boolean presenceValidee = false;

    @Column(name = "presence_validee_par", length = 100)
    private String presenceValideePar;

    @Column(name = "date_presence_validee")
    private LocalDateTime datePresenceValidee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisateur_id")
    private Employe organisateur;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @OneToMany(mappedBy = "evenement", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ParticipationEvenement> participations = new ArrayList<>();

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(name = "cree_par", length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public int getNombreParticipants() {
        return participations != null ? participations.size() : 0;
    }

    public long getNombreConfirmes() {
        if (participations == null) return 0;
        return participations.stream()
            .filter(p -> "CONFIRME".equalsIgnoreCase(p.getStatut())
                      || "PRESENT".equalsIgnoreCase(p.getStatut())).count();
    }

    public boolean isFutur() {
        return dateDebut != null && dateDebut.isAfter(LocalDateTime.now());
    }

    public boolean isEnCours() {
        if (dateDebut == null || dateFin == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(dateDebut) && !now.isAfter(dateFin);
    }

    public Long getInstitutionId() {
        return institution != null ? institution.getId() : null;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getModalite() { return modalite; }
    public void setModalite(String modalite) { this.modalite = modalite; }

    public String getLieu() { return lieu; }
    public void setLieu(String lieu) { this.lieu = lieu; }

    public String getUrlVisio() { return urlVisio; }
    public void setUrlVisio(String urlVisio) { this.urlVisio = urlVisio; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }

    public BigDecimal getIndemniteParticipant() { return indemniteParticipant; }
    public void setIndemniteParticipant(BigDecimal indemniteParticipant) { this.indemniteParticipant = indemniteParticipant; }

    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }

    public Integer getCapaciteMax() { return capaciteMax; }
    public void setCapaciteMax(Integer capaciteMax) { this.capaciteMax = capaciteMax; }

    public String getProgrammeUrl() { return programmeUrl; }
    public void setProgrammeUrl(String programmeUrl) { this.programmeUrl = programmeUrl; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getOrdreDuJour() { return ordreDuJour; }
    public void setOrdreDuJour(String ordreDuJour) { this.ordreDuJour = ordreDuJour; }

    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }

    public String getFormateur() { return formateur; }
    public void setFormateur(String formateur) { this.formateur = formateur; }

    public boolean isAttestationDelivree() { return attestationDelivree; }
    public void setAttestationDelivree(boolean attestationDelivree) { this.attestationDelivree = attestationDelivree; }

    public String getProgrammeSessions() { return programmeSessions; }
    public void setProgrammeSessions(String programmeSessions) { this.programmeSessions = programmeSessions; }

    public boolean isRestaurationPrevue() { return restaurationPrevue; }
    public void setRestaurationPrevue(boolean restaurationPrevue) { this.restaurationPrevue = restaurationPrevue; }

    public boolean isHebergementPrevu() { return hebergementPrevu; }
    public void setHebergementPrevu(boolean hebergementPrevu) { this.hebergementPrevu = hebergementPrevu; }

    public Integer getNombreNuits() { return nombreNuits; }
    public void setNombreNuits(Integer nombreNuits) { this.nombreNuits = nombreNuits; }

    public String getPrerequisMateriel() { return prerequisMateriel; }
    public void setPrerequisMateriel(String prerequisMateriel) { this.prerequisMateriel = prerequisMateriel; }

    public String getIntervenant() { return intervenant; }
    public void setIntervenant(String intervenant) { this.intervenant = intervenant; }

    public boolean isOuvertExterne() { return ouvertExterne; }
    public void setOuvertExterne(boolean ouvertExterne) { this.ouvertExterne = ouvertExterne; }

    public boolean isPresenceValidee() { return presenceValidee; }
    public void setPresenceValidee(boolean presenceValidee) { this.presenceValidee = presenceValidee; }

    public String getPresenceValideePar() { return presenceValideePar; }
    public void setPresenceValideePar(String presenceValideePar) { this.presenceValideePar = presenceValideePar; }

    public LocalDateTime getDatePresenceValidee() { return datePresenceValidee; }
    public void setDatePresenceValidee(LocalDateTime datePresenceValidee) { this.datePresenceValidee = datePresenceValidee; }

    public Employe getOrganisateur() { return organisateur; }
    public void setOrganisateur(Employe organisateur) { this.organisateur = organisateur; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public List<ParticipationEvenement> getParticipations() { return participations; }
    public void setParticipations(List<ParticipationEvenement> p) { this.participations = p; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}
