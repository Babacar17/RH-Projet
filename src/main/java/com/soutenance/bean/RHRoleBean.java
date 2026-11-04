package com.soutenance.bean;

import com.soutenance.entity.Permission;
import com.soutenance.entity.Role;
import com.soutenance.service.AdminService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean JSF — Consultation (lecture seule) des rôles et de leurs permissions,
 * périmètre Responsable RH.
 *
 * Role/Permission sont des entités globales au système, non rattachées à une
 * institution : les éditer depuis l'espace RH affecterait toutes les
 * institutions à la fois. Ce bean ne permet donc que la consultation de la
 * matrice de permissions des rôles que le RH peut lui-même assigner
 * (RH/MANAGER/EMPLOYE/CANDIDAT — voir RHAccesBean). La création de rôles et
 * la modification des permissions restent réservées à l'Admin
 * (Administrateur/roles.xhtml).
 */
@Component("rhRoleBean")
@Scope("view")
public class RHRoleBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> ROLES_CONSULTABLES =
        List.of("RH", "MANAGER", "EMPLOYE", "CANDIDAT");

    @Autowired private AdminService adminService;

    private List<Role> roles = new ArrayList<>();
    private Role roleSelectionne;
    private Map<String, List<Permission>> permissionsParModule = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        roles = adminService.getTousLesRoles().stream()
            .filter(r -> ROLES_CONSULTABLES.contains(r.getNom()))
            .sorted(Comparator.comparing(Role::getNom))
            .toList();
        if (!roles.isEmpty()) {
            selectionnerRole(roles.get(0));
        }
    }

    public void selectionnerRole(Role r) {
        if (r == null || !ROLES_CONSULTABLES.contains(r.getNom())) {
            return;
        }
        this.roleSelectionne = r;
        permissionsParModule = new LinkedHashMap<>();
        r.getPermissions().stream()
            .sorted(Comparator.comparing(Permission::getLibelle))
            .forEach(p -> permissionsParModule
                .computeIfAbsent(p.getModule() != null ? p.getModule() : "AUTRE", k -> new ArrayList<>())
                .add(p));
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public List<Role> getRoles()                    { return roles; }
    public Role getRoleSelectionne()                 { return roleSelectionne; }
    public Map<String, List<Permission>> getPermissionsParModule() { return permissionsParModule; }
    public int getTotalPermissionsRole() {
        return roleSelectionne != null ? roleSelectionne.getPermissions().size() : 0;
    }
}
