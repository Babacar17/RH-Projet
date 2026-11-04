package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Un document que le RH exige des candidats pour une offre donnée — énuméré
 * librement à la création de l'offre (ex. "Lettre de motivation", "Casier
 * judiciaire", "Photo d'identité"...). Le système génère automatiquement un
 * champ d'upload correspondant sur le formulaire de candidature.
 */
@Entity
@Table(name = "documents_requis_offre")
public class DocumentRequisOffre implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private OffreEmploi offre;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(nullable = false)
    private boolean obligatoire = true;

    public DocumentRequisOffre() {}

    public DocumentRequisOffre(OffreEmploi offre, String libelle) {
        this(offre, libelle, true);
    }

    public DocumentRequisOffre(OffreEmploi offre, String libelle, boolean obligatoire) {
        this.offre = offre;
        this.libelle = libelle;
        this.obligatoire = obligatoire;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OffreEmploi getOffre() { return offre; }
    public void setOffre(OffreEmploi offre) { this.offre = offre; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }
}
