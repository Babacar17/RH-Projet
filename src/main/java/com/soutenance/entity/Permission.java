package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Granularité des accès par module et action.
 * Ex : code = "EMPLOYE_CREER", module = "EMPLOYE", action = "CREER"
 */
@Entity
@Table(name = "permissions")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code unique machine. Ex : EMPLOYE_CREER, CONGE_APPROUVER, PAIE_VOIR */
    @Column(unique = true, nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String libelle;

    /** Module fonctionnel : EMPLOYE, CONGE, PAIE, RECRUTEMENT, FORMATION, EVALUATION, ADMIN, AUDIT */
    @Column(length = 100)
    private String module;

    /** Type d'action : CREER, LIRE, MODIFIER, SUPPRIMER, APPROUVER, EXPORTER, VALIDER */
    @Column(length = 50)
    private String action;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles = new HashSet<>();

    public Permission() {}

    public Permission(String code, String libelle, String module, String action) {
        this.code    = code;
        this.libelle = libelle;
        this.module  = module;
        this.action  = action;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}