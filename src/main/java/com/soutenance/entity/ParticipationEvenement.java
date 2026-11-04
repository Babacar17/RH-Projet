package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lien participant ↔ événement avec statut de participation.
 *
 * Statuts d'invitation : CONFIRME, LISTE_ATTENTE, DESISTE, PRESENT, ABSENT.
 * Workflow : convocation directe à CONFIRME (ou LISTE_ATTENTE si la capacité
 * maximale de l'événement est déjà atteinte, RG-06) → PRESENT (ou ABSENT) après
 * réalisation — uniquement depuis CONFIRME (RG-08). Un participant CONFIRME ou
 * LISTE_ATTENTE peut se désister volontairement avant le début de l'événement
 * (statut DESISTE, motif facultatif dans {@code commentaire}) ; un désistement
 * depuis CONFIRME promeut automatiquement le premier de la liste d'attente.
 */
@Entity
@Table(name = "participations_evenement",
       uniqueConstraints = @UniqueConstraint(columnNames = {"evenement_id", "employe_id"}),
       indexes = {
           @Index(name = "idx_part_evt",  columnList = "evenement_id"),
           @Index(name = "idx_part_emp",  columnList = "employe_id"),
           @Index(name = "idx_part_stat", columnList = "statut")
       })
public class ParticipationEvenement implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evenement_id", nullable = false)
    private Evenement evenement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    /** INVITE, CONFIRME, DECLINE, PRESENT, ABSENT */
    @Column(nullable = false, length = 20)
    private String statut = "INVITE";

    @Column(length = 500)
    private String commentaire;

    @Column(name = "date_invitation", updatable = false)
    private LocalDateTime dateInvitation;

    @Column(name = "date_reponse")
    private LocalDateTime dateReponse;

    /** True si un email d'invitation a été envoyé. */
    @Column(name = "email_envoye")
    private boolean emailEnvoye = false;

    @PrePersist
    protected void onCreate() {
        dateInvitation = LocalDateTime.now();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Evenement getEvenement() { return evenement; }
    public void setEvenement(Evenement evenement) { this.evenement = evenement; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateInvitation() { return dateInvitation; }
    public LocalDateTime getDateReponse() { return dateReponse; }
    public void setDateReponse(LocalDateTime dateReponse) { this.dateReponse = dateReponse; }

    public boolean isEmailEnvoye() { return emailEnvoye; }
    public void setEmailEnvoye(boolean emailEnvoye) { this.emailEnvoye = emailEnvoye; }
}
