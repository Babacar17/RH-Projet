package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Valeurs possibles : SUPER_ADMIN, ADMIN, RH, MANAGER, EMPLOYE, AUDITEUR, CANDIDAT */
    @Column(name = "nom", unique = true, nullable = false)
    private String nom;

    @Column(name = "description")
    private String description;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    /**
     * Permissions associées à ce rôle.
     * Un rôle peut avoir plusieurs permissions, une permission peut être partagée par plusieurs rôles.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public Role() {}

    public Role(String nom, String description) {
        this.nom = nom;
        this.description = description;
    }

    /** Vérifie si ce rôle possède une permission donnée par son code. */
    public boolean hasPermission(String code) {
        return permissions.stream().anyMatch(p -> code.equals(p.getCode()));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Set<User> getUsers() { return users; }
    public void setUsers(Set<User> users) { this.users = users; }

    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
}