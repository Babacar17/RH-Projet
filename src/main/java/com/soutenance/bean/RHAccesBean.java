package com.soutenance.bean;

import com.soutenance.entity.Role;
import com.soutenance.entity.User;
import com.soutenance.service.AdminService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean JSF — Gestion des comptes utilisateurs, périmètre Responsable RH.
 *
 * Contrairement à AdminBean (super-admin, toutes institutions), ce bean :
 *  - ne liste et ne modifie que les comptes de l'institution du RH connecté
 *    (sessionBean.getInstitutionId()) ;
 *  - force cette institution sur tout compte créé, sans possibilité de la
 *    changer depuis le formulaire ;
 *  - restreint les rôles assignables à RH/MANAGER/EMPLOYE/CANDIDAT — un RH
 *    ne peut pas créer de compte ADMIN ni AUDITEUR.
 * Toute action sur un compte est re-vérifiée côté serveur pour confirmer
 * qu'il appartient bien à l'institution du RH, même si l'id est manipulé
 * côté client.
 */
@Component("rhAccesBean")
@Scope("view")
public class RHAccesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> ROLES_ASSIGNABLES =
        List.of("RH", "MANAGER", "EMPLOYE", "CANDIDAT");

    @Autowired private AdminService adminService;
    @Autowired private SessionBean  sessionBean;

    private List<User> utilisateurs   = new ArrayList<>();
    private List<Role> rolesDisponibles = new ArrayList<>();

    private User    utilisateur       = new User();
    private Long    roleSelectionne;
    private String  nouveauMotDePasse;
    private String  motDePasseConfirm;
    private boolean modeEdition       = false;
    private String  motCleRecherche;

    @PostConstruct
    public void init() {
        rolesDisponibles = adminService.getTousLesRoles().stream()
            .filter(r -> ROLES_ASSIGNABLES.contains(r.getNom()))
            .toList();
        chargerUtilisateurs();
    }

    private void chargerUtilisateurs() {
        Long instId = sessionBean.getInstitutionId();
        utilisateurs = adminService.getTousLesUtilisateurs().stream()
            .filter(u -> appartientAInstitution(u, instId))
            .toList();
    }

    private boolean appartientAInstitution(User u, Long instId) {
        return u != null && u.getInstitution() != null && instId != null
            && instId.equals(u.getInstitution().getId());
    }

    private boolean appartientAMonInstitution(User u) {
        return appartientAInstitution(u, sessionBean.getInstitutionId());
    }

    // ════════════════════════════════════════════════════════════
    // CRUD UTILISATEURS
    // ════════════════════════════════════════════════════════════

    public void preparerNouvelUtilisateur() {
        utilisateur       = new User();
        roleSelectionne   = null;
        nouveauMotDePasse = null;
        motDePasseConfirm = null;
        modeEdition       = false;
    }

    public void preparerEditionUtilisateur(User u) {
        if (!appartientAMonInstitution(u)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                "Ce compte n'appartient pas à votre institution.");
            return;
        }
        User frais = adminService.getUserById(u.getId());
        this.utilisateur     = frais != null ? frais : u;
        this.roleSelectionne = !utilisateur.getRoles().isEmpty()
            ? utilisateur.getRoles().iterator().next().getId() : null;
        this.nouveauMotDePasse = null;
        this.motDePasseConfirm = null;
        this.modeEdition = true;
    }

    public String sauvegarderUtilisateur() {
        if (roleSelectionne != null
                && rolesDisponibles.stream().noneMatch(r -> r.getId().equals(roleSelectionne))) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Rôle non autorisé.");
            return null;
        }
        try {
            if (modeEdition) {
                if (!appartientAMonInstitution(utilisateur)) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                        "Ce compte n'appartient pas à votre institution.");
                    return null;
                }
                String erreur = adminService.modifierUtilisateur(
                    utilisateur, roleSelectionne, getOperateur());
                if (erreur != null) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
                    return null;
                }
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Utilisateur modifié : " + utilisateur.getUsername());
            } else {
                if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                        "Le mot de passe doit contenir au moins 8 caractères.");
                    return null;
                }
                if (!nouveauMotDePasse.equals(motDePasseConfirm)) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                        "Les mots de passe ne correspondent pas.");
                    return null;
                }
                // Verrouillé sur l'institution du RH — jamais modifiable depuis le formulaire.
                utilisateur.setInstitution(sessionBean.getInstitutionCourante());
                String erreur = adminService.creerUtilisateur(
                    utilisateur, nouveauMotDePasse, roleSelectionne, getOperateur());
                if (erreur != null) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
                    return null;
                }
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Compte créé : " + utilisateur.getUsername());
            }
            chargerUtilisateurs();
            utilisateur = new User();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimerUtilisateur(Long id) {
        User cible = adminService.getUserById(id);
        if (!appartientAMonInstitution(cible)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                "Ce compte n'appartient pas à votre institution.");
            return;
        }
        if (cible.getUsername().equals(getOperateur())) {
            addMsg(FacesMessage.SEVERITY_WARN, "Interdit",
                "Vous ne pouvez pas supprimer votre propre compte.");
            return;
        }
        try {
            adminService.supprimerUtilisateur(id, getOperateur());
            chargerUtilisateurs();
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Compte supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void basculerStatutUtilisateur(Long id) {
        User cible = adminService.getUserById(id);
        if (!appartientAMonInstitution(cible)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                "Ce compte n'appartient pas à votre institution.");
            return;
        }
        if (cible.getUsername().equals(getOperateur())) {
            addMsg(FacesMessage.SEVERITY_WARN, "Interdit",
                "Vous ne pouvez pas désactiver votre propre compte.");
            return;
        }
        try {
            adminService.toggleActifUtilisateur(id, getOperateur());
            chargerUtilisateurs();
            addMsg(FacesMessage.SEVERITY_INFO, "Statut modifié",
                "Le statut du compte a été mis à jour.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void debloquerCompte(Long id) {
        User cible = adminService.getUserById(id);
        if (!appartientAMonInstitution(cible)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                "Ce compte n'appartient pas à votre institution.");
            return;
        }
        try {
            String err = adminService.debloquerCompte(id, getOperateur());
            if (err != null) {
                addMsg(FacesMessage.SEVERITY_WARN, "Action ignorée", err);
            } else {
                chargerUtilisateurs();
                addMsg(FacesMessage.SEVERITY_INFO, "Compte débloqué",
                    "Les compteurs d'échecs ont été remis à zéro.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void preparerResetMotDePasse(User u) {
        if (!appartientAMonInstitution(u)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                "Ce compte n'appartient pas à votre institution.");
            return;
        }
        this.utilisateur       = u;
        this.nouveauMotDePasse = null;
        this.motDePasseConfirm = null;
    }

    public void reinitialiserMotDePasse(Long userId) {
        User cible = adminService.getUserById(userId);
        if (!appartientAMonInstitution(cible)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Non autorisé",
                "Ce compte n'appartient pas à votre institution.");
            return;
        }
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Le nouveau mot de passe doit contenir au moins 8 caractères.");
            return;
        }
        if (!nouveauMotDePasse.equals(motDePasseConfirm)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Les mots de passe ne correspondent pas.");
            return;
        }
        try {
            adminService.reinitialiserMotDePasse(userId, nouveauMotDePasse, getOperateur());
            nouveauMotDePasse = null;
            motDePasseConfirm = null;
            chargerUtilisateurs();
            addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                "Mot de passe réinitialisé avec succès.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // RECHERCHE
    // ════════════════════════════════════════════════════════════

    public void rechercherUtilisateurs() {
        Long instId = sessionBean.getInstitutionId();
        if (motCleRecherche == null || motCleRecherche.isBlank()) {
            chargerUtilisateurs();
            return;
        }
        utilisateurs = adminService.rechercherUtilisateurs(motCleRecherche).stream()
            .filter(u -> appartientAInstitution(u, instId))
            .toList();
    }

    public void reinitialiserRecherche() {
        motCleRecherche = null;
        chargerUtilisateurs();
    }

    private String getOperateur() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String s, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, s, d));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public List<User> getUtilisateurs()             { return utilisateurs; }
    public List<Role> getRolesDisponibles()          { return rolesDisponibles; }

    public User    getUtilisateur()                  { return utilisateur; }
    public void    setUtilisateur(User u)             { this.utilisateur = u; }
    public Long    getRoleSelectionne()               { return roleSelectionne; }
    public void    setRoleSelectionne(Long id)        { this.roleSelectionne = id; }
    public String  getNouveauMotDePasse()             { return nouveauMotDePasse; }
    public void    setNouveauMotDePasse(String m)     { this.nouveauMotDePasse = m; }
    public String  getMotDePasseConfirm()             { return motDePasseConfirm; }
    public void    setMotDePasseConfirm(String m)     { this.motDePasseConfirm = m; }
    public boolean isModeEdition()                    { return modeEdition; }
    public String  getMotCleRecherche()                { return motCleRecherche; }
    public void    setMotCleRecherche(String m)        { this.motCleRecherche = m; }

    public long getTotalUtilisateurs()  { return utilisateurs.size(); }
    public long getUtilisateursActifs() {
        return utilisateurs.stream().filter(User::isActif).count();
    }
    public long getUtilisateursBloques() {
        return utilisateurs.stream().filter(User::isCompteBloque).count();
    }
}
