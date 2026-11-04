package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Notification destinée à un utilisateur ou un groupe.
 * Générée automatiquement par les événements métier du système.
 */
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_notif_destinataire", columnList = "destinataire_id, est_lue"),
           @Index(name = "idx_notif_date",         columnList = "date_creation")
       })
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * Type : CONGE_SOUMIS, CONGE_APPROUVE, CONGE_REFUSE,
     *        EVALUATION_PLANIFIEE, CONTRAT_EXPIRATION,
     *        PAIE_DISPONIBLE, RECRUTEMENT_CANDIDATURE,
     *        FORMATION_INSCRIPTION, ALERTE_SYSTEME, AUTRE
     */
    @Column(nullable = false, length = 100)
    private String type;

    /**
     * Priorité : BASSE, NORMALE, HAUTE, URGENTE
     */
    @Column(length = 20)
    private String priorite;

    /** Utilisateur destinataire. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private User destinataire;

    /** La notification a-t-elle été lue ? */
    @Column(name = "est_lue")
    private boolean estLue = false;

    private LocalDateTime dateLecture;

    /** Lien vers l'entité concernée (pour navigation directe). */
    @Column(length = 100)
    private String entiteLiee;

    @Column(name = "entite_liee_id")
    private Long entiteLieeId;

    /** URL de redirection au clic sur la notification. */
    @Column(length = 300)
    private String lienAction;

    /**
     * La notification a-t-elle été envoyée par email ?
     * Utilise Boolean (objet) et non boolean (primitif) pour tolérer
     * les valeurs NULL déjà présentes en base (évite PropertyAccessException).
     */
    @Column(name = "email_envoye", columnDefinition = "boolean default false")
    private Boolean emailEnvoye = false;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateExpiration;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (priorite == null) priorite = "NORMALE";
    }

    public void marquerLue() {
        this.estLue      = true;
        this.dateLecture = LocalDateTime.now();
    }

    public boolean isExpiree() {
        return dateExpiration != null && LocalDateTime.now().isAfter(dateExpiration);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPriorite() { return priorite; }
    public void setPriorite(String priorite) { this.priorite = priorite; }

    public User getDestinataire() { return destinataire; }
    public void setDestinataire(User destinataire) { this.destinataire = destinataire; }

    public boolean isEstLue() { return estLue; }
    public void setEstLue(boolean estLue) { this.estLue = estLue; }

    public LocalDateTime getDateLecture() { return dateLecture; }
    public void setDateLecture(LocalDateTime dateLecture) { this.dateLecture = dateLecture; }

    public String getEntiteLiee() { return entiteLiee; }
    public void setEntiteLiee(String entiteLiee) { this.entiteLiee = entiteLiee; }

    public Long getEntiteLieeId() { return entiteLieeId; }
    public void setEntiteLieeId(Long entiteLieeId) { this.entiteLieeId = entiteLieeId; }

    public String getLienAction() { return lienAction; }
    public void setLienAction(String lienAction) { this.lienAction = lienAction; }

    public boolean isEmailEnvoye() { return emailEnvoye != null && emailEnvoye; }
    public void setEmailEnvoye(Boolean emailEnvoye) { this.emailEnvoye = emailEnvoye != null ? emailEnvoye : false; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public LocalDateTime getDateExpiration() { return dateExpiration; }
    public void setDateExpiration(LocalDateTime dateExpiration) { this.dateExpiration = dateExpiration; }
}