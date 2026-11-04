package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Écart ponctuel à la rotation d'équipe normale d'un employé, pour une date
 * précise : remplacement sur une autre plage, échange, absence ou congé.
 * Prime toujours sur la plage calculée par rotation — voir
 * PlanningEquipeService.horaireEffectif().
 */
@Entity
@Table(name = "exceptions_planning", uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "date"}))
public class ExceptionPlanning implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(nullable = false)
    private LocalDate date;

    /** REMPLACEMENT, ECHANGE, ABSENCE, CONGE */
    @Column(length = 20, nullable = false)
    private String type;

    /** Nouvelle plage pour REMPLACEMENT/ECHANGE — null pour ABSENCE/CONGE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plage_id")
    private PlageHoraireService plage;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(length = 100)
    private String creePar;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public PlageHoraireService getPlage() { return plage; }
    public void setPlage(PlageHoraireService plage) { this.plage = plage; }

    public boolean isOff() { return "ABSENCE".equals(type) || "CONGE".equals(type); }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String v) { this.commentaire = v; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String v) { this.creePar = v; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}
