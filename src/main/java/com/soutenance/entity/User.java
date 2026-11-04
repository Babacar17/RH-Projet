package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "nom")
    private String nom;

    @Column(name = "prenom")
    private String prenom;

    @Column(name = "telephone")
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_candidat")
    private TypeCandidat typeCandidat;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /**
     * Institution d'appartenance.
     * NULL  → super-admin global (accès à toutes les institutions)
     * non NULL → utilisateur cloisonné à cette institution
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "institution_id", nullable = true)
    private Institution institution;

    @Column(name = "actif", columnDefinition = "boolean default true")
    private boolean actif = true;

    // ── Champs de sécurité ─────────────────────────────────────────────────

    /** Nombre de tentatives de connexion échouées consécutives. */
    @Column(name = "tentatives_connexion", columnDefinition = "integer default 0")
    private int tentativesConnexion = 0;

    /** Compte temporairement verrouillé après trop d'échecs. */
    @Column(name = "compte_bloque", columnDefinition = "boolean default false")
    private boolean compteBloque = false;

    /** Date/heure du verrouillage automatique. */
    @Column(name = "date_verrouillage")
    private LocalDateTime dateVerrouillage;

    /** Token de réinitialisation de mot de passe (hashé). */
    @Column(name = "token_reset", length = 255)
    private String tokenReset;

    /** Date d'expiration du token de réinitialisation. */
    @Column(name = "date_expiration_token")
    private LocalDateTime dateExpirationToken;

    /** Authentification à deux facteurs activée. */
    @Column(name = "two_factor_enabled", columnDefinition = "boolean default false")
    private boolean twoFactorEnabled = false;

    /** Secret TOTP (Base32, stocké chiffré en prod). */
    @Column(name = "two_factor_secret", length = 100)
    private String twoFactorSecret;

    /** Dernier mot de passe utilisé (pour empêcher la réutilisation). */
    @Column(name = "hash_ancien_mdp", length = 255)
    private String hashAncienMdp;

    /** Date du dernier changement de mot de passe. */
    @Column(name = "date_changement_mdp")
    private LocalDateTime dateChangementMdp;

    // ── Dates système ──────────────────────────────────────────────────────

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;

    @PrePersist
    public void prePersist() {
        this.dateCreation = LocalDateTime.now();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public boolean isSuperAdmin() {
        return institution == null && getRoles().stream()
            .anyMatch(r -> "SUPER_ADMIN".equals(r.getNom()) || "ADMIN".equals(r.getNom()));
    }

    public boolean isAdminInstitution() {
        return institution != null && getRoles().stream()
            .anyMatch(r -> "ADMIN".equals(r.getNom()));
    }

    public Long getInstitutionId() {
        return institution != null ? institution.getId() : null;
    }

    public String getRolePrincipal() {
        if (roles == null || roles.isEmpty()) return "AUCUN";
        return roles.iterator().next().getNom();
    }

    /** Vérifie si le compte est accessible (actif, non bloqué). */
    public boolean isAccessible() {
        return actif && !compteBloque;
    }

    /** Incrémente le compteur d'échecs et bloque si ≥ 5. */
    public void enregistrerEchecConnexion() {
        this.tentativesConnexion++;
        if (this.tentativesConnexion >= 5) {
            this.compteBloque = true;
            this.dateVerrouillage = LocalDateTime.now();
        }
    }

    /** Réinitialise le compteur après une connexion réussie. */
    public void reinitialiserEchecs() {
        this.tentativesConnexion = 0;
        this.compteBloque = false;
        this.dateVerrouillage = null;
        this.derniereConnexion = LocalDateTime.now();
    }

    /** Vérifie si le token de reset est encore valide. */
    public boolean isTokenResetValide() {
        return tokenReset != null
            && dateExpirationToken != null
            && LocalDateTime.now().isBefore(dateExpirationToken);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public TypeCandidat getTypeCandidat() { return typeCandidat; }
    public void setTypeCandidat(TypeCandidat typeCandidat) { this.typeCandidat = typeCandidat; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public int getTentativesConnexion() { return tentativesConnexion; }
    public void setTentativesConnexion(int tentativesConnexion) { this.tentativesConnexion = tentativesConnexion; }

    public boolean isCompteBloque() { return compteBloque; }
    public void setCompteBloque(boolean compteBloque) { this.compteBloque = compteBloque; }

    public LocalDateTime getDateVerrouillage() { return dateVerrouillage; }
    public void setDateVerrouillage(LocalDateTime dateVerrouillage) { this.dateVerrouillage = dateVerrouillage; }

    public String getTokenReset() { return tokenReset; }
    public void setTokenReset(String tokenReset) { this.tokenReset = tokenReset; }

    public LocalDateTime getDateExpirationToken() { return dateExpirationToken; }
    public void setDateExpirationToken(LocalDateTime dateExpirationToken) { this.dateExpirationToken = dateExpirationToken; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public String getTwoFactorSecret() { return twoFactorSecret; }
    public void setTwoFactorSecret(String twoFactorSecret) { this.twoFactorSecret = twoFactorSecret; }

    public String getHashAncienMdp() { return hashAncienMdp; }
    public void setHashAncienMdp(String hashAncienMdp) { this.hashAncienMdp = hashAncienMdp; }

    public LocalDateTime getDateChangementMdp() { return dateChangementMdp; }
    public void setDateChangementMdp(LocalDateTime dateChangementMdp) { this.dateChangementMdp = dateChangementMdp; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime derniereConnexion) { this.derniereConnexion = derniereConnexion; }
}