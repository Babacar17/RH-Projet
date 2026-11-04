package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Modèle de document (template) utilisé pour la génération automatique.
 * Tous les champs correspondent aux appels effectués dans AdminDocumentService
 * et AdminDocumentBean.
 */
@Entity
@Table(name = "modele_document")
public class ModeleDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Utilisé dans : sauvegarderModele(), creerModele(), modifierModele(),
    //               changerStatutModele(), genererDocument()
    @Column(nullable = false)
    private String titre;

    /** #{adminDocumentBean.newModele.categorie} — catégorie du modèle */
    @Column(length = 100)
    private String categorie;

    /** #{adminDocumentBean.newModele.description} */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Alias de titre pour compatibilité avec le DAO qui trie par m.nom.
     * Retourne titre — les deux pointent vers le même champ.
     */
    public String getNom() { return titre; }
    public void   setNom(String n) { this.titre = n; }

    // Utilisé dans : genererDocument() → doc.setTypeDocument(modele.getTypeDocument())
    @Column(name = "type_document")
    private String typeDocument;

    // Utilisé dans : genererDocument() → modele.getContenuTemplate().getBytes(...)
    @Lob
    @Column(name = "contenu_template", columnDefinition = "TEXT")
    private String contenuTemplate;

    // Utilisé dans : creerModele() → modele.setInstitutionId(institutionId)
    @Column(name = "institution_id")
    private Long institutionId;

    // Utilisé dans : creerModele() → modele.setCreePar(operateur)
    @Column(name = "cree_par")
    private String creePar;

    // Utilisé dans : changerStatutModele() → m.setStatut(statut)
    // Valeurs possibles : ACTIF, INACTIF
    private String statut;

    // Utilisé dans : changerStatutModele() → m.setActif("ACTIF".equals(statut))
    private boolean actif = true;

    @Column(name = "date_creation")
    private LocalDate dateCreation;

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getTypeDocument() { return typeDocument; }
    public void setTypeDocument(String typeDocument) { this.typeDocument = typeDocument; }

    public String getContenuTemplate() { return contenuTemplate; }
    public void setContenuTemplate(String contenuTemplate) { this.contenuTemplate = contenuTemplate; }

    public Long getInstitutionId() { return institutionId; }
    public void setInstitutionId(Long institutionId) { this.institutionId = institutionId; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

    public String getCategorie() { return categorie; }
    public void   setCategorie(String c) { this.categorie = c; }

    public String getDescription() { return description; }
    public void   setDescription(String d) { this.description = d; }
}