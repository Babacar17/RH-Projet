package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité GED — fichier archivé dans le système documentaire.
 *
 * CORRECTION : DocumentArchive étend désormais Document au lieu de mapper
 * indépendamment la même table "documents".
 *
 * Stratégie : SINGLE_TABLE avec discriminateur "type_doc = 'DOCUMENT_ARCHIVE'".
 * Les champs communs (nom, categorie, statut, employe, institution, entite…)
 * sont hérités de Document — ils ne sont PAS redéfinis ici.
 *
 * Champs propres à DocumentArchive (GED spécifique) :
 *   - titre         : alias lisible du nom (interface GED)
 *   - contenu       : contenu binaire (BYTEA, PostgreSQL) pour stockage en base
 *   - typeDocument  : type GED (CONTRAT_CDI, ATTESTATION_TRAVAIL…)
 *   - motifArchivage, dateArchivage : cycle de vie GED
 *   - emailSignataire : workflow signature électronique
 *   - deposePar      : traçabilité de l'upload
 *   - institutionId  : clé Long directe conservée pour compatibilité avec les
 *                      DAO existants (DocumentArchiveDao utilise institutionId).
 *                      La relation institution héritée de Document est préférée
 *                      pour les nouvelles requêtes JPQL.
 *
 * MIGRATION SQL requise (une seule fois) :
 *   ALTER TABLE documents ADD COLUMN type_doc VARCHAR(30) NOT NULL DEFAULT 'DOCUMENT_ARCHIVE';
 *   -- Si des enregistrements Document pur existent :
 *   UPDATE documents SET type_doc = 'DOCUMENT' WHERE <condition>;
 */
@Entity
@DiscriminatorValue("DOCUMENT_ARCHIVE")
public class DocumentArchive extends Document implements Serializable {

    private static final long serialVersionUID = 2L;

    // ── Titre (alias GED de Document.nom) ─────────────────────────────────

    /**
     * Titre lisible affiché dans l'interface GED.
     * Alias de Document.nom — les deux pointent vers des colonnes distinctes
     * car Document.nom est déjà mappé. On utilise une colonne dédiée "titre".
     *
     * NOTE : si vous souhaitez unifier, supprimez ce champ et utilisez
     * getNom() / setNom() hérités directement.
     */
    @Column(name = "titre", length = 300)
    private String titre;

    // ── Contenu binaire ────────────────────────────────────────────────────

    @Lob
    @Column(columnDefinition = "BYTEA")
    private byte[] contenu;

    // ── Type GED ──────────────────────────────────────────────────────────

    /** Type de document GED : CONTRAT_CDI, ATTESTATION_TRAVAIL, BULLETIN_PAIE… */
    @Column(name = "type_document", length = 100)
    private String typeDocument;

    // ── Cycle de vie GED ─────────────────────────────────────────────────

    @Column(name = "date_depot")
    private LocalDate dateDepot;

    @Column(name = "date_archivage")
    private LocalDate dateArchivage;

    @Column(columnDefinition = "TEXT")
    private String motifArchivage;

    // ── Signature électronique ─────────────────────────────────────────────

    @Column(name = "email_signataire", length = 150)
    private String emailSignataire;

    // ── Traçabilité upload ────────────────────────────────────────────────

    @Column(name = "depose_par", length = 100)
    private String deposePar;

    /**
     * Clé Long directe conservée pour compatibilité avec DocumentArchiveDao
     * (qui filtre via cb.equal(root.get("institutionId"), institutionId)).
     *
     * Préférez institution (hérité de Document) pour les nouvelles requêtes.
     */
    @Column(name = "institution_id_legacy", insertable = false, updatable = false)
    private Long institutionId;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (dateDepot == null) dateDepot = LocalDate.now();
    }

    public boolean isExpire() {
        return false; // À surcharger si dateExpiration est utilisée dans GED
    }

    // ── Getters / Setters spécifiques à DocumentArchive ───────────────────

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }

    public String getTypeDocument() { return typeDocument; }
    public void setTypeDocument(String typeDocument) { this.typeDocument = typeDocument; }

    public LocalDate getDateDepot() { return dateDepot; }
    public void setDateDepot(LocalDate dateDepot) { this.dateDepot = dateDepot; }

    public LocalDate getDateArchivage() { return dateArchivage; }
    public void setDateArchivage(LocalDate dateArchivage) { this.dateArchivage = dateArchivage; }

    public String getMotifArchivage() { return motifArchivage; }
    public void setMotifArchivage(String motifArchivage) { this.motifArchivage = motifArchivage; }

    public String getEmailSignataire() { return emailSignataire; }
    public void setEmailSignataire(String emailSignataire) { this.emailSignataire = emailSignataire; }

    public String getDeposePar() { return deposePar; }
    public void setDeposePar(String deposePar) { this.deposePar = deposePar; }

    /**
     * Compatibilité DAO : retourne l'ID de l'institution.
     * Délègue vers la relation héritée si disponible, sinon retourne la colonne legacy.
     */
    public Long getInstitutionId() {
        if (getInstitution() != null) return getInstitution().getId();
        return institutionId;
    }

    /**
     * Compatibilité DAO : positionne l'institution via son ID.
     * À terme, remplacer par setInstitution(institutionDao.findById(id)).
     */
    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }
}