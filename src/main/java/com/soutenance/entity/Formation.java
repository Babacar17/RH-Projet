package com.soutenance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "formations")
public class Formation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String formateur;

    @Column(length = 100)
    private String organisme;

    @Column(length = 100)
    private String categorie;

    @Column(length = 50)
    private String modalite;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Column(length = 100)
    private String lieu;

    private Integer dureeHeures;
    private Integer capaciteMax;
    private BigDecimal cout;

    @Column(length = 20)
    private String statut;

    /**
     * Institution organisatrice de la formation.
     * Les RH ne voient que les formations de leur institution.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "formation_employes",
        joinColumns = @JoinColumn(name = "formation_id"),
        inverseJoinColumns = @JoinColumn(name = "employe_id")
    )
    private List<Employe> participants = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @Column(length = 100)
    private String creePar;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (statut == null) statut = "PLANIFIEE";
    }

    public int getNombreParticipants() {
        return participants != null ? participants.size() : 0;
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

    public String getFormateur() { return formateur; }
    public void setFormateur(String formateur) { this.formateur = formateur; }

    public String getOrganisme() { return organisme; }
    public void setOrganisme(String organisme) { this.organisme = organisme; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public String getModalite() { return modalite; }
    public void setModalite(String modalite) { this.modalite = modalite; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public String getLieu() { return lieu; }
    public void setLieu(String lieu) { this.lieu = lieu; }

    public Integer getDureeHeures() { return dureeHeures; }
    public void setDureeHeures(Integer dureeHeures) { this.dureeHeures = dureeHeures; }

    public Integer getCapaciteMax() { return capaciteMax; }
    public void setCapaciteMax(Integer capaciteMax) { this.capaciteMax = capaciteMax; }

    public BigDecimal getCout() { return cout; }
    public void setCout(BigDecimal cout) { this.cout = cout; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public List<Employe> getParticipants() { return participants; }
    public void setParticipants(List<Employe> participants) { this.participants = participants; }

    public LocalDateTime getDateCreation() { return dateCreation; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }

    // ── Aliases EL pour mes-formations.xhtml ───────────────────────────────
    // Le XHTML accède à #{f.intitule}, #{f.dureeJours} et #{f.objectifs}.
    // L'entité ne possède que titre, dureeHeures et description.

    @Transient
    private String objectifs;

    /** Alias EL #{formation.intitule} → titre. */
    @Transient
    public String getIntitule() { return titre; }
    public void setIntitule(String intitule) { this.titre = intitule; }

    /** Durée en jours, dérivée de dureeHeures (8 h = 1 jour). */
    @Transient
    public Integer getDureeJours() {
        return dureeHeures != null ? (int) Math.ceil(dureeHeures / 8.0) : null;
    }
    public void setDureeJours(Integer jours) {
        this.dureeHeures = jours != null ? jours * 8 : null;
    }

    /** Objectifs pédagogiques — non persistés, retombent sur la description. */
    @Transient
    public String getObjectifs() {
        return objectifs != null ? objectifs : description;
    }
    public void setObjectifs(String objectifs) { this.objectifs = objectifs; }
}