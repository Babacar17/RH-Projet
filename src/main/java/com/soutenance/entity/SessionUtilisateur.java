package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Suivi des sessions actives des utilisateurs.
 * Permet la révocation de tokens, la détection de sessions multiples
 * et le tableau de bord des connexions actives.
 */
@Entity
@Table(name = "sessions_utilisateur",
       indexes = {
           @Index(name = "idx_session_token",     columnList = "token_jti", unique = true),
           @Index(name = "idx_session_user",       columnList = "user_id"),
           @Index(name = "idx_session_expiration", columnList = "date_expiration")
       })
public class SessionUtilisateur implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * JWT ID (jti claim) — identifiant unique du token JWT.
     * Permet de révoquer un token précis sans invalider tous les tokens.
     */
    @Column(name = "token_jti", nullable = false, unique = true, length = 255)
    private String tokenJti;

    /** Hash du refresh token associé. */
    @Column(name = "refresh_token_hash", length = 255)
    private String refreshTokenHash;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Navigateur / appareil extrait du User-Agent. */
    @Column(length = 100)
    private String dispositif;

    /** Localisation géographique approximative. */
    @Column(length = 100)
    private String localisation;

    /** Date de création de la session (première connexion). */
    @Column(name = "date_connexion", updatable = false)
    private LocalDateTime dateConnexion;

    /** Date d'expiration du token. */
    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    /** Dernière activité enregistrée sur cette session. */
    @Column(name = "derniere_activite")
    private LocalDateTime derniereActivite;

    /** La session a-t-elle été révoquée manuellement ? */
    @Column(name = "revoquee")
    private boolean revoquee = false;

    @Column(name = "date_revocation")
    private LocalDateTime dateRevocation;

    @Column(name = "raison_revocation", length = 200)
    private String raisonRevocation;

    @PrePersist
    protected void onCreate() {
        dateConnexion    = LocalDateTime.now();
        derniereActivite = LocalDateTime.now();
    }

    public boolean isActive() {
        return !revoquee
            && dateExpiration != null
            && LocalDateTime.now().isBefore(dateExpiration);
    }

    public void revoquer(String raison) {
        this.revoquee          = true;
        this.dateRevocation    = LocalDateTime.now();
        this.raisonRevocation  = raison;
    }

    public void rafraichirActivite() {
        this.derniereActivite = LocalDateTime.now();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getTokenJti() { return tokenJti; }
    public void setTokenJti(String tokenJti) { this.tokenJti = tokenJti; }

    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }

    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDispositif() { return dispositif; }
    public void setDispositif(String dispositif) { this.dispositif = dispositif; }

    public String getLocalisation() { return localisation; }
    public void setLocalisation(String localisation) { this.localisation = localisation; }

    public LocalDateTime getDateConnexion() { return dateConnexion; }

    public LocalDateTime getDateExpiration() { return dateExpiration; }
    public void setDateExpiration(LocalDateTime dateExpiration) { this.dateExpiration = dateExpiration; }

    public LocalDateTime getDerniereActivite() { return derniereActivite; }
    public void setDerniereActivite(LocalDateTime derniereActivite) { this.derniereActivite = derniereActivite; }

    public boolean isRevoquee() { return revoquee; }
    public void setRevoquee(boolean revoquee) { this.revoquee = revoquee; }

    public LocalDateTime getDateRevocation() { return dateRevocation; }
    public void setDateRevocation(LocalDateTime dateRevocation) { this.dateRevocation = dateRevocation; }

    public String getRaisonRevocation() { return raisonRevocation; }
    public void setRaisonRevocation(String raisonRevocation) { this.raisonRevocation = raisonRevocation; }
}