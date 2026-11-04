package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.User;
import com.soutenance.service.AdminService;
import com.soutenance.service.TotpService;
import com.soutenance.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

@Component("loginBean")
@Scope("session")
public class LoginBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(LoginBean.class);


    private String username;
    private String password;
    /** Code TOTP saisi à l'étape 2 (vide tant qu'on est à l'étape 1). */
    private String code2fa;
    /** True après validation du mot de passe, en attente du code 2FA. */
    private boolean attendCode2fa = false;
    /** Utilisateur authentifié partiellement (mdp OK, 2FA en attente). */
    private User    userEnAttente2fa;

    /**
     * Institution sur laquelle l'utilisateur est arrivé via une URL
     * portail dédiée (/CODE/login). Null si accès direct au login global.
     * Alimentée par {@link com.soutenance.filter.InstitutionUrlFilter}.
     */
    public com.soutenance.entity.Institution getInstitutionPortail() {
        try {
            jakarta.servlet.http.HttpSession s = (jakarta.servlet.http.HttpSession)
                jakarta.faces.context.FacesContext.getCurrentInstance()
                    .getExternalContext().getSession(false);
            return s != null
                ? (com.soutenance.entity.Institution) s.getAttribute("institutionPortail")
                : null;
        } catch (Exception e) { return null; }
    }

    /** Nom de l'institution affiché dans le header du login. */
    public String getNomInstitutionPortail() {
        com.soutenance.entity.Institution i = getInstitutionPortail();
        return i != null ? i.getNom() : null;
    }

    /** URL du logo institution (ou null si pas de logo). */
    public String getLogoInstitutionPortail() {
        com.soutenance.entity.Institution i = getInstitutionPortail();
        return i != null ? i.getLogoUrl() : null;
    }

    /** Code court (ex : MFB) affiché dans le badge. */
    public String getCodeInstitutionPortail() {
        com.soutenance.entity.Institution i = getInstitutionPortail();
        return i != null ? i.getCode() : null;
    }

    @Autowired private UserService  userService;
    @Autowired private AdminService adminService;
    @Autowired private SessionBean  sessionBean;
    @Autowired private TotpService  totpService;

    @PostConstruct
    public void init() {
        System.out.println("LoginBean init OK");
    }

    public String login() {
        boolean valid = userService.authenticate(username, password);

        if (!valid) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur", "Identifiants incorrects ou compte desactive."));
            return null;
        }

        User user = userService.getByUsername(username);

        // ── Vérification d'appartenance institution (URL portail dédiée) ──
        com.soutenance.entity.Institution portail = getInstitutionPortail();
        if (portail != null && user != null) {
            boolean superAdmin = user.isSuperAdmin();
            boolean candidat = user.getRoles().stream().anyMatch(r -> "CANDIDAT".equals(r.getNom()));
            Long userInstId = user.getInstitutionId();
            if (!superAdmin && !candidat && (userInstId == null || !portail.getId().equals(userInstId))) {
                adminService.log(username, "CONNEXION_REFUSEE_INSTITUTION",
                    "Tentative login " + username + " sur portail " + portail.getCode()
                    + " (utilisateur rattaché à institution id=" + userInstId + ")");
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Accès refusé",
                        "Votre compte n'appartient pas à l'institution « "
                        + portail.getNom() + " »."));
                return null;
            }
        }

        // ── Étape intermédiaire : 2FA activée → on ne se connecte pas tout de suite
        if (user.isTwoFactorEnabled() && user.getTwoFactorSecret() != null
            && !user.getTwoFactorSecret().isBlank()) {
            this.userEnAttente2fa = user;
            this.attendCode2fa    = true;
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Code requis",
                    "Saisissez le code à 6 chiffres affiché par votre application d'authentification."));
            return null;
        }

        return finaliserConnexion(user);
    }

    /**
     * Étape 2 de la connexion lorsque 2FA est activée.
     * Vérifie le code TOTP saisi puis enchaîne sur {@link #finaliserConnexion(User)}.
     */
    public String validerCode2fa() {
        if (userEnAttente2fa == null) {
            // Sécurité : si l'utilisateur tente l'étape 2 sans avoir passé l'étape 1
            attendCode2fa = false;
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur", "Veuillez recommencer la connexion."));
            return null;
        }
        boolean ok = totpService.verifierCode(userEnAttente2fa.getTwoFactorSecret(), code2fa);
        if (!ok) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Code invalide", "Le code à 6 chiffres ne correspond pas."));
            adminService.log(userEnAttente2fa.getUsername(), "2FA_ECHEC",
                "Code TOTP refusé");
            return null;
        }
        User user = userEnAttente2fa;
        userEnAttente2fa = null;
        attendCode2fa    = false;
        code2fa          = null;
        adminService.log(user.getUsername(), "2FA_OK", "Code TOTP validé");
        return finaliserConnexion(user);
    }

    /**
     * Annule l'étape 2FA en cours et ramène à l'écran de saisie identifiants.
     */
    public String annulerCode2fa() {
        userEnAttente2fa = null;
        attendCode2fa    = false;
        code2fa          = null;
        password         = null;
        return null;
    }

    /** Branche commune à la connexion sans 2FA et après validation du code. */
    private String finaliserConnexion(User user) {
        sessionBean.connecter(user);

        // Stocker dans la HttpSession AVANT la redirection
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        HttpSession httpSession = (HttpSession) ec.getSession(true);
        httpSession.setAttribute("utilisateurConnecte", user);

        String role = sessionBean.getRolePrincipal();
        httpSession.setAttribute("roleConnecte", role);

        adminService.log(username, "CONNEXION", "Connexion reussie");

        // Redirection via ExternalContext (préserve la session)
        String ctx = ec.getRequestContextPath();
        String target;
        switch (role) {
            case "ADMIN":    target = ctx + "/Administrateur/dashboard.xhtml"; break;
            case "RH":       target = ctx + "/ResponsableRH/dashboard.xhtml"; break;
            case "MANAGER":  target = ctx + "/ResponsableHierarchique/dashboard.xhtml"; break;
            case "EMPLOYE":  target = ctx + "/Employes/dashboard.xhtml"; break;
            case "CANDIDAT": target = ctx + "/Candidat/dashboard.xhtml"; break;
            default:         target = ctx + "/Authentification/login.xhtml"; break;
        }

        try {
            ec.redirect(target);
        } catch (IOException e) {
            LOG.error("Erreur inattendue", e);
        }
        fc.responseComplete();
        return null;
    }

    public String deconnecter() {
        try {
            if (sessionBean.getUtilisateurConnecte() != null) {
                adminService.log(username, "DECONNEXION", "Deconnexion");
            }
        } catch (Exception ignored) {}

        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        sessionBean.deconnecter();
        ec.invalidateSession();

        try {
            ec.redirect(ec.getRequestContextPath() + "/Authentification/login.xhtml");
        } catch (IOException e) {
            LOG.error("Erreur inattendue", e);
        }
        fc.responseComplete();
        return null;
    }

    public String logout() { return deconnecter(); }

    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getPassword() { return password; }
    public void setPassword(String p) { this.password = p; }

    public String  getCode2fa()                { return code2fa; }
    public void    setCode2fa(String c)        { this.code2fa = c; }
    public boolean isAttendCode2fa()           { return attendCode2fa; }
}