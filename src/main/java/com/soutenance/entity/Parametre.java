package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Paramètres de configuration clé/valeur par institution.
 * Permet de personnaliser le comportement de l'application
 * sans modifier le code (délais, seuils, libellés, règles métier…).
 *
 * Ex :
 *   cle = "CONGE_ANNUEL_JOURS_MAX"         valeur = "30"
 *   cle = "PAIE_JOUR_VIREMENT"             valeur = "25"
 *   cle = "NOTIF_EMAIL_ENABLED"            valeur = "true"
 *   cle = "LOGO_URL"                       valeur = "https://..."
 */
@Entity
@Table(name = "parametres",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cle", "institution_id"}))
public class Parametre implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clé technique normalisée. Ex : CONGE_ANNUEL_JOURS_MAX */
    @Column(nullable = false, length = 150)
    private String cle;

    /** Valeur stockée toujours en String, à caster côté service. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String valeur;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Groupe fonctionnel pour l'affichage dans l'interface :
     * CONGE, PAIE, SECURITE, NOTIFICATION, GENERAL, RECRUTEMENT, FORMATION
     */
    @Column(length = 100)
    private String groupe;

    /**
     * Type de la valeur pour la validation côté UI :
     * STRING, INTEGER, DECIMAL, BOOLEAN, DATE, URL, EMAIL
     */
    @Column(length = 20)
    private String typeValeur;

    /** La valeur peut-elle être modifiée par l'admin de l'institution ? */
    @Column(name = "editable_par_admin")
    private boolean editableParAdmin = true;

    /** ACTIF, INACTIF */
    @Column(length = 20)
    private String statut;

    /**
     * NULL → paramètre global (valeur par défaut pour toutes les institutions).
     * non NULL → paramètre spécifique à cette institution (surcharge le global).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String modifiePar;

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null)     statut    = "ACTIF";
        if (typeValeur == null) typeValeur = "STRING";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Helpers de cast ────────────────────────────────────────────────────

    public Integer getValeurInt() {
        try { return Integer.parseInt(valeur); } catch (Exception e) { return null; }
    }

    public Boolean getValeurBoolean() {
        return "true".equalsIgnoreCase(valeur) || "1".equals(valeur);
    }

    public Double getValeurDouble() {
        try { return Double.parseDouble(valeur); } catch (Exception e) { return null; }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCle() { return cle; }
    public void setCle(String cle) { this.cle = cle; }

    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroupe() { return groupe; }
    public void setGroupe(String groupe) { this.groupe = groupe; }

    public String getTypeValeur() { return typeValeur; }
    public void setTypeValeur(String typeValeur) { this.typeValeur = typeValeur; }

    public boolean isEditableParAdmin() { return editableParAdmin; }
    public void setEditableParAdmin(boolean editableParAdmin) { this.editableParAdmin = editableParAdmin; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getModifiePar() { return modifiePar; }
    public void setModifiePar(String modifiePar) { this.modifiePar = modifiePar; }
}