package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employes")
public class Employe implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Identité ───────────────────────────────────────────────────────────

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String telephone;

    @Column(length = 20)
    private String cin;

    private LocalDate dateNaissance;

    @Column(length = 100)
    private String lieuNaissance;

    @Column(length = 10)
    private String sexe; // M, F

    @Column(length = 100)
    private String nationalite;

    @Column(length = 50)
    private String situationFamiliale; // CELIBATAIRE, MARIE, DIVORCE, VEUF

    private Integer nombreEnfants;

    @Column(length = 200)
    private String adresse;

    // ── Contact d'urgence ──────────────────────────────────────────────────

    @Column(name = "urgence_nom", length = 150)
    private String urgenceNom;

    @Column(name = "urgence_telephone", length = 20)
    private String urgenceTelephone;

    @Column(name = "urgence_lien", length = 50)
    private String urgenceLien; // CONJOINT, PARENT, AMI…

    // ── Informations professionnelles ──────────────────────────────────────

    @Column(nullable = false, length = 100, unique = true)
    private String matricule;

    /**
     * Lien vers le référentiel Poste.
     * Le champ texte librePoste est conservé pour rétrocompatibilité,
     * mais poste (FK) est la référence officielle.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poste_id")
    private Poste poste;

    /** Intitulé libre (utilisé si poste FK est null, ou pour les cas atypiques). */
    @Column(name = "libre_poste", length = 150)
    private String librePoste;

    @Column(length = 50)
    private String niveauHierarchique; // DIRECTEUR, CHEF_DIV, CHEF_SERVICE, CHEF_BUREAU, EMPLOYE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service serviceOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bureau_id")
    private Bureau bureauOrg;

    // Rattachements directs (structure libre) — tous OPTIONNELS
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direction_id")
    private Direction directionOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id")
    private Division divisionOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_id")
    private Departement departementOrg;

    @Column(nullable = false)
    private LocalDate dateEmbauche;

    /** Date de fin de contrat (obligatoire pour CDD, null pour CDI/titulaire). */
    @Column(name = "date_fin_contrat")
    private LocalDate dateFinContrat;

    /** CDD, CDI, TITULAIRE, STAGIAIRE, CONTRACTUEL, DETACHE */
    @Column(length = 50)
    private String typeContrat;

    /** ACTIF, INACTIF, SUSPENDU, RETRAITE, DEMISSIONNAIRE, LICENCIE */
    @Column(length = 50)
    private String statut;

    /** Salaire de base manuel — utilisé seulement si aucune grille salariale n'est rattachée. */
    @Column(columnDefinition = "numeric(10,2)")
    private BigDecimal salaireBase;

    @Column(length = 100)
    private String gradeEchelon;

    /** Grille salariale de référence pour le calcul de paie — prioritaire sur salaireBase si renseignée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grille_salariale_id")
    private GrilleSalariale grilleSalariale;

    /** Niveau d'études : BAC, LICENCE, MASTER, DOCTORAT, BTS, etc. */
    @Column(length = 100)
    private String niveauEtudes;

    // ── Coordonnées bancaires (pour un futur export de fichier de virement) ─
    @Column(length = 100)
    private String banque;

    @Column(name = "numero_compte", length = 50)
    private String numeroCompte;

    @Column(length = 200)
    private String photoUrl;

    // ── Compte utilisateur lié ─────────────────────────────────────────────

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // ── Responsable hiérarchique ───────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employe manager;

    /**
     * Institution de rattachement.
     * Clé de filtrage principale pour le multi-tenancy.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateModification;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        dateCreation    = LocalDateTime.now();
        dateModification = LocalDateTime.now();
        if (statut == null) statut = "ACTIF";
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public String getNomComplet() {
        return (prenom != null ? prenom : "") + " " + (nom != null ? nom : "");
    }

    public String getServiceComplet() {
        try {
            if (serviceOrg != null) return serviceOrg.getNomComplet();
        } catch (Exception e) { /* proxy non initialisé */ }
        return "—";
    }

    public String getNomDirection() {
        try {
            if (directionOrg != null) return directionOrg.getNom();
            if (divisionOrg != null && divisionOrg.getDirection() != null)
                return divisionOrg.getDirection().getNom();
            if (departementOrg != null && departementOrg.getDirection() != null)
                return departementOrg.getDirection().getNom();
            Service srv = serviceOrg;
            if (srv == null && bureauOrg != null) srv = bureauOrg.getService();
            if (srv != null && srv.getDirectionEffective() != null)
                return srv.getDirectionEffective().getNom();
        } catch (Exception e) { /* proxy non initialisé */ }
        return "—";
    }

    public String getNomDivision() {
        try {
            if (divisionOrg != null) return divisionOrg.getNom();
            if (serviceOrg != null && serviceOrg.getDivision() != null)
                return serviceOrg.getDivision().getNom();
        } catch (Exception e) { /* proxy non initialisé */ }
        return "—";
    }

    public String getNomDepartement() {
        try {
            if (departementOrg != null) return departementOrg.getNom();
            if (serviceOrg != null && serviceOrg.getDepartement() != null)
                return serviceOrg.getDepartement().getNom();
        } catch (Exception e) { /* proxy non initialisé */ }
        return "—";
    }

    public String getNomService() {
        try {
            if (serviceOrg != null) return serviceOrg.getNom();
        } catch (Exception e) { /* proxy non initialisé */ }
        return "—";
    }

    public String getNomBureau() {
        try {
            if (bureauOrg != null) return bureauOrg.getNom();
        } catch (Exception e) { /* proxy non initialisé */ }
        return "—";
    }

    public String getIntiutePoste() {
        if (poste != null) return poste.getIntitule();
        return librePoste != null ? librePoste : "—";
    }

    public boolean isChef() {
        return niveauHierarchique != null && !niveauHierarchique.equals("EMPLOYE");
    }

    public String getLabelNiveau() {
        if (niveauHierarchique == null) return "Employé";
        return switch (niveauHierarchique) {
            case "DIRECTEUR"    -> "Chef de Direction";
            case "CHEF_DIV"     -> "Chef de Division";
            case "CHEF_SERVICE" -> "Chef de Service";
            case "CHEF_BUREAU"  -> "Chef de Bureau";
            default             -> "Employé";
        };
    }

    /** True si le contrat CDD est arrivé à terme. */
    public boolean isContratExpire() {
        return dateFinContrat != null && LocalDate.now().isAfter(dateFinContrat);
    }

    public Long getInstitutionId() {
        return institution != null ? institution.getId() : null;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }

    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getLieuNaissance() { return lieuNaissance; }
    public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public String getNationalite() { return nationalite; }
    public void setNationalite(String nationalite) { this.nationalite = nationalite; }

    public String getSituationFamiliale() { return situationFamiliale; }
    public void setSituationFamiliale(String situationFamiliale) { this.situationFamiliale = situationFamiliale; }

    public Integer getNombreEnfants() { return nombreEnfants; }
    public void setNombreEnfants(Integer nombreEnfants) { this.nombreEnfants = nombreEnfants; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getUrgenceNom() { return urgenceNom; }
    public void setUrgenceNom(String urgenceNom) { this.urgenceNom = urgenceNom; }

    public String getUrgenceTelephone() { return urgenceTelephone; }
    public void setUrgenceTelephone(String urgenceTelephone) { this.urgenceTelephone = urgenceTelephone; }

    public String getUrgenceLien() { return urgenceLien; }
    public void setUrgenceLien(String urgenceLien) { this.urgenceLien = urgenceLien; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }

    public String getLibrePoste() { return librePoste; }
    public void setLibrePoste(String librePoste) { this.librePoste = librePoste; }

    public String getNiveauHierarchique() { return niveauHierarchique; }
    public void setNiveauHierarchique(String n) { this.niveauHierarchique = n; }

    public Service getServiceOrg() { return serviceOrg; }
    public void setServiceOrg(Service serviceOrg) { this.serviceOrg = serviceOrg; }

    public Bureau getBureauOrg() { return bureauOrg; }
    public void setBureauOrg(Bureau bureauOrg) { this.bureauOrg = bureauOrg; }

    public Direction getDirectionOrg() { return directionOrg; }
    public void setDirectionOrg(Direction directionOrg) { this.directionOrg = directionOrg; }

    public Division getDivisionOrg() { return divisionOrg; }
    public void setDivisionOrg(Division divisionOrg) { this.divisionOrg = divisionOrg; }

    public Departement getDepartementOrg() { return departementOrg; }
    public void setDepartementOrg(Departement departementOrg) { this.departementOrg = departementOrg; }

    public LocalDate getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(LocalDate dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public LocalDate getDateFinContrat() { return dateFinContrat; }
    public void setDateFinContrat(LocalDate dateFinContrat) { this.dateFinContrat = dateFinContrat; }

    public String getTypeContrat() { return typeContrat; }
    public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }

    public String getGradeEchelon() { return gradeEchelon; }
    public void setGradeEchelon(String gradeEchelon) { this.gradeEchelon = gradeEchelon; }

    public GrilleSalariale getGrilleSalariale() { return grilleSalariale; }
    public void setGrilleSalariale(GrilleSalariale grilleSalariale) { this.grilleSalariale = grilleSalariale; }

    public String getNiveauEtudes() { return niveauEtudes; }
    public void setNiveauEtudes(String niveauEtudes) { this.niveauEtudes = niveauEtudes; }

    public String getBanque() { return banque; }
    public void setBanque(String banque) { this.banque = banque; }

    public String getNumeroCompte() { return numeroCompte; }
    public void setNumeroCompte(String numeroCompte) { this.numeroCompte = numeroCompte; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Employe getManager() { return manager; }
    public void setManager(Employe manager) { this.manager = manager; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateModification() { return dateModification; }

    // ── Vues façade pour les pages JSF ─────────────────────────────────────
    // ma-carriere.xhtml et consorts accèdent à #{employe.departement},
    // #{employe.service}, #{employe.structure}, #{...responsable.dateAffectation}.
    // Aucune n'est en base ; on les dérive du contexte organisationnel.

    /**
     * Département lisible — première brique remontante (Direction → Division).
     * Protégé par try/catch pour éviter LazyInitializationException
     * quand les proxies Hibernate ne sont plus dans une session ouverte.
     */
    @Transient
    public String getDepartement() {
        try {
            if (serviceOrg != null && serviceOrg.getDivision() != null) {
                var div = serviceOrg.getDivision();
                if (div.getDirection() != null && div.getDirection().getNom() != null)
                    return div.getDirection().getNom();
                if (div.getNom() != null) return div.getNom();
            }
            if (bureauOrg != null && bureauOrg.getService() != null
                && bureauOrg.getService().getNom() != null) {
                return bureauOrg.getService().getNom();
            }
        } catch (Exception e) { /* proxy non initialisé — session Hibernate fermée */ }
        return null;
    }

    /**
     * Service lisible — nom du service organisationnel.
     * Protégé par try/catch pour éviter LazyInitializationException.
     */
    @Transient
    public String getService() {
        try {
            if (serviceOrg != null && serviceOrg.getNom() != null) return serviceOrg.getNom();
            if (bureauOrg != null && bureauOrg.getService() != null)
                return bureauOrg.getService().getNom();
        } catch (Exception e) { /* proxy non initialisé */ }
        return null;
    }

    /** Représentation complète Direction › Division › Service. */
    @Transient
    public String getStructure() {
        String s = getServiceComplet();
        return (s != null && !s.isBlank()) ? s : getDepartement();
    }

    /**
     * Date d'affectation au poste actuel — alias de dateEmbauche faute
     * d'historique de mouvements disponibles via cette entité.
     */
    @Transient
    public LocalDate getDateAffectation() { return dateEmbauche; }
}