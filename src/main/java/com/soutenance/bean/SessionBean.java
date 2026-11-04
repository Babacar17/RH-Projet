package com.soutenance.bean;

import com.soutenance.entity.Institution;
import com.soutenance.entity.User;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Bean de session : stocke l'utilisateur connecté et son institution.
 *
 * Deux cas :
 *  - Super-admin   : institution == null, isSuperAdmin() == true
 *  - Autres rôles  : institution != null, données filtrées par institutionId
 */
@Component("sessionBean")
@Scope("session")
public class SessionBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private User        utilisateurConnecte;
    private Institution institutionCourante;
    private boolean     connecte = false;

    // ── Connexion / Déconnexion ────────────────────────────────────────────

    public void connecter(User user) {
        this.utilisateurConnecte = user;
        this.institutionCourante = user.getInstitution();
        this.connecte = true;
    }

    public void deconnecter() {
        this.utilisateurConnecte = null;
        this.institutionCourante = null;
        this.connecte = false;
    }

    // ── Helpers rôle ──────────────────────────────────────────────────────

    public boolean isAdmin() {
        return connecte && utilisateurConnecte != null
            && utilisateurConnecte.getRoles().stream()
                .anyMatch(r -> "ADMIN".equals(r.getNom()));
    }

    /**
     * True si l'utilisateur connecté est super-admin global
     * (admin sans institution assignée).
     */
    public boolean isSuperAdmin() {
        return isAdmin() && institutionCourante == null;
    }

    /**
     * True si l'utilisateur est admin d'une institution spécifique
     * (pas super-admin global).
     */
    public boolean isAdminInstitution() {
        return isAdmin() && institutionCourante != null;
    }

    // ── Helpers institution ───────────────────────────────────────────────

    /**
     * Retourne l'ID de l'institution courante, ou null pour le super-admin.
     * À utiliser dans tous les appels de service pour le filtrage.
     *
     * Exemple d'utilisation dans un bean :
     *   rhService.getTousEmployes(sessionBean.getInstitutionId())
     */
    public Long getInstitutionId() {
        return institutionCourante != null ? institutionCourante.getId() : null;
    }

    /**
     * Retourne le nom de l'institution courante, ou "Global" pour le super-admin.
     */
    public String getNomInstitution() {
        if (institutionCourante == null) return "Global";
        return institutionCourante.getNom();
    }

    /**
     * Retourne le code de l'institution courante (ex : "MFB"), ou "SA" pour le super-admin.
     */
    public String getCodeInstitution() {
        if (institutionCourante == null) return "SA";
        return institutionCourante.getCode();
    }

    // ── Helpers utilisateur ───────────────────────────────────────────────

    public String getNomComplet() {
        if (utilisateurConnecte == null) return "";
        String n = utilisateurConnecte.getNom()    != null ? utilisateurConnecte.getNom()    : "";
        String p = utilisateurConnecte.getPrenom() != null ? utilisateurConnecte.getPrenom() : "";
        return (p + " " + n).trim();
    }

    public String getRolePrincipal() {
        if (utilisateurConnecte == null || utilisateurConnecte.getRoles().isEmpty()) return "—";
        return utilisateurConnecte.getRoles().iterator().next().getNom();
    }

    /** URL du dashboard selon le rôle — utilisé par mon-compte.xhtml */
    public String getDashboardUrl() {
        String role = getRolePrincipal();
        return switch (role) {
            case "ADMIN"    -> "Administrateur/dashboard.xhtml";
            case "RH"       -> "ResponsableRH/dashboard.xhtml";
            case "MANAGER"  -> "ResponsableHierarchique/dashboard.xhtml";
            case "EMPLOYE"  -> "Employes/dashboard.xhtml";
            case "AUDITEUR" -> "Auditeur/dashboard.xhtml";
            case "CANDIDAT" -> "Candidat/dashboard.xhtml";
            default         -> "Principal/index.xhtml";
        };
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public User        getUtilisateurConnecte()             { return utilisateurConnecte; }
    public void        setUtilisateurConnecte(User u)       { this.utilisateurConnecte = u; }

    public Institution getInstitutionCourante()                    { return institutionCourante; }
    public void        setInstitutionCourante(Institution inst)    { this.institutionCourante = inst; }

    public boolean     isConnecte()                         { return connecte; }
    public void        setConnecte(boolean connecte)        { this.connecte = connecte; }
}