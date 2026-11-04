package com.soutenance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Journal d'audit complet de toutes les actions du système.
 *
 * Chaque entrée trace : QUI a fait QUOI sur QUELLE entité,
 * avec l'ancienne et la nouvelle valeur pour les modifications.
 */
@Entity
@Table(name = "log_actions",
       indexes = {
           @Index(name = "idx_log_username",    columnList = "username"),
           @Index(name = "idx_log_entite",      columnList = "entite, entite_id"),
           @Index(name = "idx_log_date_action", columnList = "date_action"),
           @Index(name = "idx_log_institution", columnList = "institution_id")
       })
public class LogAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom d'utilisateur de l'opérateur. */
    @Column(name = "username", length = 100)
    private String username;

    /**
     * Code action normalisé en MAJUSCULES_SNAKE_CASE.
     * Ex : EMPLOYE_CREER, CONGE_APPROUVER, USER_CONNEXION, PAIE_VALIDER
     */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    /** Description lisible de l'action. */
    @Column(name = "detail", length = 500)
    private String detail;

    // ── Traçabilité de l'entité modifiée ───────────────────────────────────

    /** Nom de l'entité concernée. Ex : Employe, DemandeConge, BulletinPaie */
    @Column(name = "entite", length = 100)
    private String entite;

    /** Identifiant de l'enregistrement modifié. */
    @Column(name = "entite_id")
    private Long entiteId;

    /**
     * Snapshot JSON de l'état avant modification.
     * Null pour les créations.
     */
    @Column(name = "ancienne_valeur", columnDefinition = "TEXT")
    private String ancienneValeur;

    /**
     * Snapshot JSON de l'état après modification.
     * Null pour les suppressions.
     */
    @Column(name = "nouvelle_valeur", columnDefinition = "TEXT")
    private String nouvelleValeur;

    // ── Contexte technique ─────────────────────────────────────────────────

    @Column(name = "adresse_ip", length = 45) // IPv6 max 45 chars
    private String adresseIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** Institution dans laquelle l'action a eu lieu. */
    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "date_action")
    private LocalDateTime dateAction;

    @PrePersist
    public void prePersist() {
        this.dateAction = LocalDateTime.now();
    }

    // ── Constructeurs ──────────────────────────────────────────────────────

    public LogAction() {}

    /**
     * Constructeur canonique : (username, action, detail).
     * Pour les actions sans entité précise (connexion, export global…).
     */
    public LogAction(String username, String action, String detail) {
        this.username = username;
        this.action   = action;
        this.detail   = detail;
    }

    /**
     * Constructeur complet pour les modifications CRUD avec traçabilité.
     */
    public LogAction(String username, String action, String detail,
                     String entite, Long entiteId,
                     String ancienneValeur, String nouvelleValeur) {
        this.username       = username;
        this.action         = action;
        this.detail         = detail;
        this.entite         = entite;
        this.entiteId       = entiteId;
        this.ancienneValeur = ancienneValeur;
        this.nouvelleValeur = nouvelleValeur;
    }

    /** Factory method — syntaxe fluide recommandée dans les services. */
    public static LogAction of(String operateur, String action, String detail) {
        return new LogAction(operateur, action, detail);
    }

    /** Factory method pour les modifications avec diff. */
    public static LogAction ofModification(String operateur, String action, String detail,
                                           String entite, Long entiteId,
                                           String ancienne, String nouvelle) {
        return new LogAction(operateur, action, detail, entite, entiteId, ancienne, nouvelle);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getEntite() { return entite; }
    public void setEntite(String entite) { this.entite = entite; }

    public Long getEntiteId() { return entiteId; }
    public void setEntiteId(Long entiteId) { this.entiteId = entiteId; }

    public String getAncienneValeur() { return ancienneValeur; }
    public void setAncienneValeur(String ancienneValeur) { this.ancienneValeur = ancienneValeur; }

    public String getNouvelleValeur() { return nouvelleValeur; }
    public void setNouvelleValeur(String nouvelleValeur) { this.nouvelleValeur = nouvelleValeur; }

    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Long getInstitutionId() { return institutionId; }
    public void setInstitutionId(Long institutionId) { this.institutionId = institutionId; }

    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }

    // ── Aliases EL pour audit.xhtml (var=sv) ──────────────────────────────
    // Le XHTML accède à #{sv.dateCreation}, #{sv.type}, #{sv.taille},
    // #{sv.succes}. Ces propriétés n'existent pas en base ; on les dérive.

    @Transient
    public LocalDateTime getDateCreation() { return dateAction; }

    @Transient
    public String getType() { return action; }

    /** Taille — non applicable aux logs, retourne « — ». */
    @Transient
    public String getTaille() { return "—"; }

    @Transient
    public boolean isSucces() {
        if (action == null) return true;
        String a = action.toUpperCase();
        return !(a.contains("ECHEC") || a.contains("BLOQUEE")
              || a.contains("VERROUILLE") || a.startsWith("ERREUR"));
    }
}