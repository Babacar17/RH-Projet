package com.soutenance.bean;

import com.soutenance.entity.User;
import com.soutenance.service.PasswordResetService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Bean gestionnaire du flow « mot de passe oublié ».
 *
 *  - demanderReset()    : envoie un lien de reset à l'email saisi
 *  - chargerToken()     : appelée par <f:viewParam> de la page reset.xhtml
 *  - definirNouveauMdp(): consomme le token et change le mot de passe
 */
@Component("resetPasswordBean")
@Scope("request")
public class ResetPasswordBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private PasswordResetService resetService;

    private String  email;
    private String  token;
    private String  nouveauMdp;
    private String  confirmationMdp;
    private boolean tokenValide = false;
    private String  utilisateurAffiche;

    // ── Étape 1 : demande ────────────────────────────────────────────────────

    public String demanderReset() {
        if (email == null || email.isBlank() || !email.contains("@")) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Email invalide", "Saisissez une adresse email valide."));
            return null;
        }
        try {
            resetService.demanderReset(email);
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Email envoyé",
                    "Si l'adresse existe dans nos systèmes, un lien de réinitialisation y a été envoyé."));
        } catch (Exception ex) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur", ex.getMessage()));
        }
        email = null;
        return null;
    }

    // ── Étape 2 : validation du token (page reset.xhtml) ────────────────────

    /** Appelée par <f:viewAction> pour valider le token reçu dans l'URL. */
    public void chargerToken() {
        if (token == null || token.isBlank()) {
            this.tokenValide = false;
            return;
        }
        User u = resetService.validerToken(token);
        if (u != null) {
            this.tokenValide        = true;
            this.utilisateurAffiche = u.getPrenom() + " " + u.getNom();
        } else {
            this.tokenValide = false;
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Lien invalide", "Ce lien de réinitialisation est invalide ou expiré."));
        }
    }

    // ── Étape 3 : changement effectif du mot de passe ───────────────────────

    public String definirNouveauMdp() {
        if (nouveauMdp == null || !nouveauMdp.equals(confirmationMdp)) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur", "Les deux mots de passe doivent être identiques."));
            return null;
        }
        String err = resetService.reinitialiser(token, nouveauMdp);
        if (err != null) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", err));
            return null;
        }
        // Reset effectué : redirection vers le login avec un message
        try {
            FacesContext.getCurrentInstance().getExternalContext().redirect(
                FacesContext.getCurrentInstance().getExternalContext()
                    .getRequestContextPath() + "/Authentification/login.xhtml?reset=ok");
        } catch (Exception ignored) { /* ignore */ }
        return null;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String  getEmail()                       { return email; }
    public void    setEmail(String email)           { this.email = email; }

    public String  getToken()                       { return token; }
    public void    setToken(String token)           { this.token = token; }

    public String  getNouveauMdp()                  { return nouveauMdp; }
    public void    setNouveauMdp(String n)          { this.nouveauMdp = n; }

    public String  getConfirmationMdp()             { return confirmationMdp; }
    public void    setConfirmationMdp(String c)     { this.confirmationMdp = c; }

    public boolean isTokenValide()                  { return tokenValide; }
    public String  getUtilisateurAffiche()          { return utilisateurAffiche; }
}
