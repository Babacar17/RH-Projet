package com.soutenance.bean;

import com.soutenance.entity.Institution;
import com.soutenance.entity.User;
import com.soutenance.service.UserService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class InscriptionBean {

    @Autowired
    private UserService userService;

    private User user = new User();
    private String password;
    private String confirmPassword;

    /**
     * Lit l'institution du portail courant (déposée en session par
     * {@link com.soutenance.filter.InstitutionUrlFilter}).
     * Null si on est sur la page d'inscription globale (non rattachée).
     */
    public Institution getInstitutionPortail() {
        try {
            HttpSession s = (HttpSession) FacesContext.getCurrentInstance()
                .getExternalContext().getSession(false);
            return s != null ? (Institution) s.getAttribute("institutionPortail") : null;
        } catch (Exception e) { return null; }
    }

    public String  getNomInstitutionPortail() {
        Institution i = getInstitutionPortail();
        return i != null ? i.getNom() : null;
    }
    public String  getCodeInstitutionPortail() {
        Institution i = getInstitutionPortail();
        return i != null ? i.getCode() : null;
    }
    public String  getLogoInstitutionPortail() {
        Institution i = getInstitutionPortail();
        return i != null ? i.getLogoUrl() : null;
    }

    public String inscrire() {

        // Vérification mot de passe minimum 8 caractères
        if (password == null || password.length() < 8) {
            addError("Le mot de passe doit contenir au moins 8 caractères.");
            return null;
        }

        // Vérification que les mots de passe correspondent
        if (!password.equals(confirmPassword)) {
            addError("Les mots de passe ne correspondent pas.");
            return null;
        }

        // Vérification que le nom d'utilisateur n'existe pas déjà
        if (userService.usernameExiste(user.getUsername())) {
            addError("Ce nom d'utilisateur est déjà pris.");
            return null;
        }

        // Vérification email non vide
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            addError("L'adresse email est obligatoire.");
            return null;
        }

        // Si l'inscription est faite depuis un portail institution, on
        // rattache automatiquement le nouvel utilisateur à cette institution
        Institution portail = getInstitutionPortail();
        if (portail != null) {
            user.setInstitution(portail);
        }

        // Sauvegarde — le rôle CANDIDAT est assigné automatiquement dans le service
        user.setPassword(password);
        userService.inscrireUtilisateur(user);

        String msg = portail != null
            ? "Compte créé pour le portail " + portail.getNom() + " — vous pouvez vous connecter."
            : "Compte créé avec succès ! Vous pouvez vous connecter.";

        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));

        // Redirection vers le login du portail (s'il y en a un) ou le login global
        String target = portail != null
            ? "/" + portail.getCode() + "/login?faces-redirect=true"
            : "/Authentification/login?faces-redirect=true";
        return target;
    }

    private void addError(String message) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", message));
    }

    // Getters et Setters
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}