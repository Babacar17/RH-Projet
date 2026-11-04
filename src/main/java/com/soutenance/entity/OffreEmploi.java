package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "offres_emploi")
public class OffreEmploi implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String profil;

    /** Création de poste, remplacement, renfort... — repris automatiquement dans le rapport de recrutement. */
    @Column(name = "motif_recrutement", columnDefinition = "TEXT")
    private String motifRecrutement;

    @Column(name = "rattachement_hierarchique", length = 200)
    private String rattachementHierarchique;

    @Column(columnDefinition = "TEXT")
    private String avantages;

    @Column(name = "modalites_candidature", columnDefinition = "TEXT")
    private String modalitesCandidature;

    /**
     * Documents que le RH énumère librement comme exigés des candidats pour
     * cette offre (le CV, lui, reste toujours obligatoire indépendamment de
     * cette liste). Chaque entrée génère automatiquement un champ d'upload
     * correspondant sur le formulaire de candidature.
     */
    @OneToMany(mappedBy = "offre", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentRequisOffre> documentsRequis = new ArrayList<>();

    @Column(length = 100)
    private String departement;

    @Column(length = 100)
    private String localisation;

    @Column(length = 50)
    private String typeContrat;

    @Column(length = 50)
    private String niveauExperience;

    @Column(length = 100)
    private String niveauEtudes;

    /** EMPLOI ou STAGE — détermine la liste des types de contrat proposés côté formulaire. */
    @Column(name = "type_offre", length = 20)
    private String typeOffre;

    @Column(name = "niveau_hierarchique", length = 50)
    private String niveauHierarchique;

    @Column(name = "filiere_specialite", length = 150)
    private String filiereSpecialite;

    @Column(name = "date_prise_poste")
    private LocalDate datePriseDePoste;

    private BigDecimal salaireMin;
    private BigDecimal salaireMax;

    private Integer nombrePostes;

    @Column(nullable = false)
    private LocalDate datePublication;

    @Column(name = "date_limite_fermeture")
    private LocalDateTime dateLimiteFermeture;

    /**
     * Seuil, en pourcentage du score maximum atteignable sur la dernière
     * phase clôturée, à partir duquel un candidat est signalé comme
     * éligible au recrutement automatique. Exprimé en %, pas en points
     * bruts, car le score maximum dépend du référentiel d'évaluation
     * (nombre de critères, coefficients) et varie d'une offre à l'autre.
     * Le RH reste décisionnaire — cette valeur ne déclenche jamais de
     * recrutement sans son clic sur "Retenir".
     */
    @Column(name = "seuil_recrutement_auto_pct")
    private Integer seuilRecrutementAutoPct;

    @Column(length = 20)
    private String statut;

    /**
     * Institution qui publie cette offre.
     *
     * Les candidats ne voient que les offres PUBLIEES de leur institution
     * (ou de toutes les institutions si le recrutement est ouvert au public).
     * Les RH ne gèrent que les offres de leur institution.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** Poste du référentiel visé par ce concours (optionnel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poste_id")
    private Poste poste;

    /** Service (unité organisationnelle) auquel ce poste est rattaché — donne accès à la division et à la direction. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    /**
     * Bureau précis auquel ce poste est rattaché (optionnel, plus fin que service).
     * Quand renseigné, il est plus précis : sa chaîne bureau → service → division →
     * direction suffit à elle seule à situer le poste dans l'organigramme.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bureau_id")
    private Bureau bureau;

    /** Grille d'évaluation (phases/rubriques) appliquée à ce concours. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referentiel_id")
    private ReferentielEvaluation referentiel;

    @OneToMany(mappedBy = "offre", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Candidature> candidatures = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "BROUILLON";
        if (datePublication == null) datePublication = LocalDate.now();
        if (typeOffre == null) typeOffre = "EMPLOI";
    }

    public boolean isExpiree() {
        return dateLimiteFermeture != null
            && LocalDateTime.now().isAfter(dateLimiteFermeture);
    }

    public boolean isEnAttenteOuverture() {
        return "PUBLIEE".equals(statut)
            && datePublication != null
            && datePublication.isAfter(LocalDate.now());
    }

    public boolean isOuverte() {
        return "PUBLIEE".equals(statut) && !isExpiree() && !isEnAttenteOuverture();
    }

    public Long getInstitutionId() {
        return institution != null ? institution.getId() : null;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getProfil() { return profil; }
    public void setProfil(String profil) { this.profil = profil; }

    public String getMotifRecrutement() { return motifRecrutement; }
    public void setMotifRecrutement(String v) { this.motifRecrutement = v; }

    public String getRattachementHierarchique() { return rattachementHierarchique; }
    public void setRattachementHierarchique(String v) { this.rattachementHierarchique = v; }

    public String getAvantages() { return avantages; }
    public void setAvantages(String avantages) { this.avantages = avantages; }

    public String getModalitesCandidature() { return modalitesCandidature; }
    public void setModalitesCandidature(String v) { this.modalitesCandidature = v; }

    public List<DocumentRequisOffre> getDocumentsRequis() { return documentsRequis; }
    public void setDocumentsRequis(List<DocumentRequisOffre> v) { this.documentsRequis = v; }

    /** Liste des documents exigés des candidats pour cette offre (CV inclus), énumérés et séparés par une virgule. */
    public String getDocumentsRequisTexte() {
        List<String> docs = new ArrayList<>();
        docs.add("CV");
        for (DocumentRequisOffre d : documentsRequis) docs.add(d.getLibelle());
        return String.join(", ", docs);
    }

    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }

    public String getLocalisation() { return localisation; }
    public void setLocalisation(String localisation) { this.localisation = localisation; }

    public String getTypeContrat() { return typeContrat; }
    public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }

    public String getNiveauExperience() { return niveauExperience; }
    public void setNiveauExperience(String niveauExperience) { this.niveauExperience = niveauExperience; }

    public String getNiveauEtudes() { return niveauEtudes; }
    public void setNiveauEtudes(String niveauEtudes) { this.niveauEtudes = niveauEtudes; }

    public String getTypeOffre() { return typeOffre; }
    public void setTypeOffre(String typeOffre) { this.typeOffre = typeOffre; }

    public String getNiveauHierarchique() { return niveauHierarchique; }
    public void setNiveauHierarchique(String v) { this.niveauHierarchique = v; }

    public String getFiliereSpecialite() { return filiereSpecialite; }
    public void setFiliereSpecialite(String v) { this.filiereSpecialite = v; }

    public LocalDate getDatePriseDePoste() { return datePriseDePoste; }
    public void setDatePriseDePoste(LocalDate v) { this.datePriseDePoste = v; }

    public BigDecimal getSalaireMin() { return salaireMin; }
    public void setSalaireMin(BigDecimal salaireMin) { this.salaireMin = salaireMin; }

    public BigDecimal getSalaireMax() { return salaireMax; }
    public void setSalaireMax(BigDecimal salaireMax) { this.salaireMax = salaireMax; }

    public Integer getNombrePostes() { return nombrePostes; }
    public void setNombrePostes(Integer nombrePostes) { this.nombrePostes = nombrePostes; }

    public LocalDate getDatePublication() { return datePublication; }
    public void setDatePublication(LocalDate datePublication) { this.datePublication = datePublication; }

    public LocalDateTime getDateLimiteFermeture() { return dateLimiteFermeture; }
    public Integer getSeuilRecrutementAutoPct() { return seuilRecrutementAutoPct; }
    public void setSeuilRecrutementAutoPct(Integer v) { this.seuilRecrutementAutoPct = v; }
    public void setDateLimiteFermeture(LocalDateTime v) { this.dateLimiteFermeture = v; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public List<Candidature> getCandidatures() { return candidatures; }
    public void setCandidatures(List<Candidature> candidatures) { this.candidatures = candidatures; }

    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    public Bureau getBureau() { return bureau; }
    public void setBureau(Bureau bureau) { this.bureau = bureau; }

    /** Résumé "Poste — Direction › Division › Service [› Bureau]" pour affichage (null si rien de renseigné). */
    public String getPositionnementTexte() {
        StringBuilder sb = new StringBuilder();
        if (poste != null) sb.append(poste.getIntitule());
        String orga = bureau != null ? bureau.getNomComplet() : (service != null ? service.getNomComplet() : null);
        if (orga != null) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(orga);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** Fil d'ariane organisationnel du poste visé, sans l'intitulé du poste (ex: "Direction › Division › Service › Bureau"). */
    public String getOrganisationTexte() {
        if (bureau != null) return bureau.getNomComplet();
        if (service != null) return service.getNomComplet();
        return null;
    }

    public ReferentielEvaluation getReferentiel() { return referentiel; }
    public void setReferentiel(ReferentielEvaluation referentiel) { this.referentiel = referentiel; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}