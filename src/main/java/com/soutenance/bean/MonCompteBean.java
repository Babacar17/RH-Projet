package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.dao.UserDao;
import com.soutenance.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Bean JSF — Gestion du compte personnel de l'utilisateur connecté.
 *
 * CORRECTION : BCryptPasswordEncoder était instancié directement avec
 *   private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
 *
 * Problèmes de cette approche :
 *   - Un nouveau BCryptPasswordEncoder est créé à chaque instanciation du bean,
 *     ce qui gaspille des ressources inutilement.
 *   - Si la configuration de Spring Security change le coût BCrypt (strength),
 *     le bean utiliserait un encodeur avec la valeur par défaut (10) et non
 *     celle configurée dans le contexte Spring.
 *   - Cela rend le test unitaire plus difficile (impossible de mocker l'encodeur).
 *
 * SOLUTION : injecter le bean BCryptPasswordEncoder déclaré dans la configuration
 * Spring Security (SecurityConfig ou équivalent). Si aucun bean n'existe encore,
 * il suffit de l'ajouter une seule fois dans la configuration :
 *
 *   @Bean
 *   public BCryptPasswordEncoder passwordEncoder() {
 *       return new BCryptPasswordEncoder();
 *   }
 */
@Component("monCompteBean")
@Scope("view")
public class MonCompteBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(MonCompteBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private UserDao              userDao;
    @Autowired private SessionBean          sessionBean;

    // CORRECTION : injecté par Spring au lieu d'être instancié manuellement
    @Autowired private BCryptPasswordEncoder encoder;

    // ── Formulaire infos personnelles ─────────────────────────────────────────
    private String nom;
    private String prenom;
    private String email;
    private String telephone;

    // ── Changement username ───────────────────────────────────────────────────
    private String nouveauUsername;

    // ── Changement mot de passe ───────────────────────────────────────────────
    private String ancienMotDePasse;
    private String nouveauMotDePasse;
    private String confirmMotDePasse;

    // ── Utilisateur courant ───────────────────────────────────────────────────
    private User userCourant;

    @PostConstruct
    public void init() {
        userCourant = sessionBean.getUtilisateurConnecte();
        if (userCourant != null) {
            // Recharger depuis la DB pour avoir les données fraîches
            User frais = userDao.findById(userCourant.getId());
            if (frais != null) userCourant = frais;
            // Pré-remplir les champs
            nom             = userCourant.getNom();
            prenom          = userCourant.getPrenom();
            email           = userCourant.getEmail();
            telephone       = userCourant.getTelephone();
            nouveauUsername = userCourant.getUsername();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAUVEGARDER INFORMATIONS PERSONNELLES
    // ─────────────────────────────────────────────────────────────────────────

    public void sauvegarderInfos() {
        if (userCourant == null) { addError("Session expirée."); return; }
        try {
            if (email == null || email.isBlank()) {
                addError("L'email est obligatoire.");
                return;
            }
            User u = userDao.findById(userCourant.getId());
            if (u == null) { addError("Utilisateur introuvable."); return; }
            u.setNom(nom);
            u.setPrenom(prenom);
            u.setEmail(email);
            u.setTelephone(telephone);
            userDao.update(u);
            // Synchroniser la session
            userCourant.setNom(nom);
            userCourant.setPrenom(prenom);
            userCourant.setEmail(email);
            userCourant.setTelephone(telephone);
            sessionBean.getUtilisateurConnecte().setNom(nom);
            sessionBean.getUtilisateurConnecte().setPrenom(prenom);
            addInfo("Informations mises à jour avec succès.");
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            addError("Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHANGER LE NOM D'UTILISATEUR
    // ─────────────────────────────────────────────────────────────────────────

    public void changerUsername() {
        if (userCourant == null) { addError("Session expirée."); return; }
        try {
            if (nouveauUsername == null || nouveauUsername.isBlank()) {
                addError("Le nom d'utilisateur ne peut pas être vide.");
                return;
            }
            nouveauUsername = nouveauUsername.trim().toLowerCase();
            if (!nouveauUsername.equals(userCourant.getUsername())) {
                User existant = userDao.findByUsername(nouveauUsername);
                if (existant != null) {
                    addError("Ce nom d'utilisateur est déjà pris.");
                    return;
                }
            }
            User u = userDao.findById(userCourant.getId());
            if (u == null) { addError("Utilisateur introuvable."); return; }
            u.setUsername(nouveauUsername);
            userDao.update(u);
            userCourant.setUsername(nouveauUsername);
            sessionBean.getUtilisateurConnecte().setUsername(nouveauUsername);
            addInfo("Nom d'utilisateur modifié : " + nouveauUsername);
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            addError("Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHANGER LE MOT DE PASSE
    // ─────────────────────────────────────────────────────────────────────────

    public void changerMotDePasse() {
        if (userCourant == null) { addError("Session expirée."); return; }
        try {
            if (ancienMotDePasse == null || ancienMotDePasse.isBlank()) {
                addError("Veuillez saisir votre mot de passe actuel.");
                return;
            }
            if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 6) {
                addError("Le nouveau mot de passe doit contenir au moins 6 caractères.");
                return;
            }
            if (!nouveauMotDePasse.equals(confirmMotDePasse)) {
                addError("Les mots de passe ne correspondent pas.");
                return;
            }
            User u = userDao.findById(userCourant.getId());
            if (u == null) { addError("Utilisateur introuvable."); return; }

            // CORRECTION : encoder est injecté par Spring — même instance que celle
            // utilisée pour l'authentification dans UserService
            if (!encoder.matches(ancienMotDePasse, u.getPassword())) {
                addError("Le mot de passe actuel est incorrect.");
                return;
            }
            u.setPassword(encoder.encode(nouveauMotDePasse));
            userDao.update(u);
            ancienMotDePasse = null;
            nouveauMotDePasse = null;
            confirmMotDePasse = null;
            addInfo("Mot de passe modifié avec succès.");
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            addError("Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GETTERS / SETTERS
    // ─────────────────────────────────────────────────────────────────────────

    public User   getUserCourant()                  { return userCourant; }
    public String getNom()                          { return nom; }
    public void   setNom(String v)                  { this.nom = v; }
    public String getPrenom()                       { return prenom; }
    public void   setPrenom(String v)               { this.prenom = v; }
    public String getEmail()                        { return email; }
    public void   setEmail(String v)                { this.email = v; }
    public String getTelephone()                    { return telephone; }
    public void   setTelephone(String v)            { this.telephone = v; }
    public String getNouveauUsername()              { return nouveauUsername; }
    public void   setNouveauUsername(String v)      { this.nouveauUsername = v; }
    public String getAncienMotDePasse()             { return ancienMotDePasse; }
    public void   setAncienMotDePasse(String v)     { this.ancienMotDePasse = v; }
    public String getNouveauMotDePasse()            { return nouveauMotDePasse; }
    public void   setNouveauMotDePasse(String v)    { this.nouveauMotDePasse = v; }
    public String getConfirmMotDePasse()            { return confirmMotDePasse; }
    public void   setConfirmMotDePasse(String v)    { this.confirmMotDePasse = v; }
}