package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/** Appartenance d'un employé à une équipe de rotation (voir EquipeService). */
@Entity
@Table(name = "membres_equipe", uniqueConstraints = @UniqueConstraint(columnNames = {"equipe_id", "employe_id"}))
public class MembreEquipe implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_id", nullable = false)
    private EquipeService equipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EquipeService getEquipe() { return equipe; }
    public void setEquipe(EquipeService equipe) { this.equipe = equipe; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }
}
