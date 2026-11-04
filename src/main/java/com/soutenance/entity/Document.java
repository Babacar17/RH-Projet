package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité centrale de gestion documentaire — CLASSE PARENTE.
 *
 * CORRECTION : Document et DocumentArchive mappaient tous deux sur la table
 * "documents", ce qui provoque une erreur Hibernate au démarrage
 * ("Table 'documents' is mapped to multiple entities").
 *
 * SOLUTION : stratégie d'héritage SINGLE_TABLE avec colonne discriminatrice
 * "type_doc". Les deux entités partagent la même table mais Hibernate
 * les distingue grâce à cette colonne :
 *   - "DOCUMENT"         → instances de Document
 *   - "DOCUMENT_ARCHIVE" → instances de DocumentArchive
 *
 * DocumentArchive étend Document et ajoute ses champs spécifiques (GED).
 * Les champs communs (nom, categorie, statut, employe, institution…)
 * restent uniquement dans Document.
 *
 * Impact sur la base de données :
 *   - La table "documents" gagne une colonne "type_doc VARCHAR(30) NOT NULL".
 *   - Tous les enregistrements existants doivent être migrés :
 *       UPDATE documents SET type_doc = 'DOCUMENT_ARCHIVE';
 *     (ou 'DOCUMENT' selon le cas d'usage d'origine)
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type_doc", discriminatorType = DiscriminatorType.STRING, length = 30)
@DiscriminatorValue("DOCUMENT")
@Table(name = "documents",
       indexes = {
           @Index(name = "idx_doc_entite",      columnList = "entite, entite_id"),
           @Index(name = "idx_doc_institution", columnList = "institution_id"),
           @Index(name = "idx_doc_employe",     columnList = "employe_id")
       })
public class Document implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Catégorie : CV, LETTRE_MOTIVATION, DIPLOME, PIECE_IDENTITE,
     *             CONTRAT, AVENANT, BULLETIN_PAIE, ATTESTATION,
     *             RAPPORT_EVALUATION, FICHE_POSTE, AUTRE
     */
    @Column(nullable = false, length = 50)
    private String categorie;

    /** Nom original du fichier uploadé. */
    @Column(length = 300)
    private String nomFichier;

    /** Type MIME. Ex : application/pdf, image/jpeg */
    @Column(length = 100)
    private String typeMime;

    /** Taille en octets. */
    private Long tailleFichier;

    /** Chemin de stockage interne (relatif à la racine de stockage). */
    @Column(length = 500)
    private String cheminStockage;

    /** URL publique ou signée si stockage cloud. */
    @Column(length = 500)
    private String urlAcces;

    // ── Entité source (polymorphisme manuel) ───────────────────────────────

    /**
     * Nom de l'entité propriétaire du document.
     * Ex : "Employe", "Candidature", "BulletinPaie"
     */
    @Column(length = 100)
    private String entite;

    /** ID de l'enregistrement propriétaire. */
    @Column(name = "entite_id")
    private Long entiteId;

    // ── Relations ─────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id")
    private Employe employe;

    /**
     * NOTE : Document utilise une relation @ManyToOne vers Institution
     * (contrairement à DocumentArchive qui stockait institutionId en Long brut).
     * Cela permet les jointures JPQL standards : d.institution.id = :iid
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    // ── Cycle de vie ───────────────────────────────────────────────────────

    private LocalDate dateDocument;
    private LocalDate dateExpiration;

    /** Statut : ACTIF, ARCHIVE, SUPPRIME, EN_ATTENTE_SIGNATURE, SIGNE */
    @Column(length = 30)
    private String statut;

    @Column(name = "signature_requise")
    private boolean signatureRequise = false;

    private LocalDate dateSignature;

    @Column(length = 150)
    private String signePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation     = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    /** True si le document est expiré. */
    public boolean isExpire() {
        return dateExpiration != null && LocalDate.now().isAfter(dateExpiration);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }

    public String getTypeMime() { return typeMime; }
    public void setTypeMime(String typeMime) { this.typeMime = typeMime; }

    public Long getTailleFichier() { return tailleFichier; }
    public void setTailleFichier(Long tailleFichier) { this.tailleFichier = tailleFichier; }

    public String getCheminStockage() { return cheminStockage; }
    public void setCheminStockage(String cheminStockage) { this.cheminStockage = cheminStockage; }

    public String getUrlAcces() { return urlAcces; }
    public void setUrlAcces(String urlAcces) { this.urlAcces = urlAcces; }

    public String getEntite() { return entite; }
    public void setEntite(String entite) { this.entite = entite; }

    public Long getEntiteId() { return entiteId; }
    public void setEntiteId(Long entiteId) { this.entiteId = entiteId; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public LocalDate getDateDocument() { return dateDocument; }
    public void setDateDocument(LocalDate dateDocument) { this.dateDocument = dateDocument; }

    public LocalDate getDateExpiration() { return dateExpiration; }
    public void setDateExpiration(LocalDate dateExpiration) { this.dateExpiration = dateExpiration; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public boolean isSignatureRequise() { return signatureRequise; }
    public void setSignatureRequise(boolean s) { this.signatureRequise = s; }

    public LocalDate getDateSignature() { return dateSignature; }
    public void setDateSignature(LocalDate dateSignature) { this.dateSignature = dateSignature; }

    public String getSignePar() { return signePar; }
    public void setSignePar(String signePar) { this.signePar = signePar; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}