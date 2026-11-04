package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Membre d'un comité d'évaluation (association Comite ↔ Employe avec un rôle).
 * Correspond à ComiteEvaluateur du modèle gestion_rh — un évaluateur y est
 * simplement un Employe désigné comme membre du jury.
 */
@Entity
@Table(name = "comite_evaluateurs")
public class ComiteEvaluateur implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comite_id", nullable = false)
    private Comite comite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluateur_id", nullable = false)
    private Employe evaluateur;

    /** PRESIDENT, MEMBRE, SECRETAIRE, RAPPORTEUR */
    @Column(length = 30)
    private String role;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Comite getComite() { return comite; }
    public void setComite(Comite comite) { this.comite = comite; }

    public Employe getEvaluateur() { return evaluateur; }
    public void setEvaluateur(Employe evaluateur) { this.evaluateur = evaluateur; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
