package com.soutenance.service;

import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.RoleDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.Role;
import com.soutenance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service d'authentification et de gestion des utilisateurs.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. BCryptPasswordEncoder instancié manuellement → injecté via Spring.
 *    Raison : AdminService, CandidatService et UserService créaient chacun
 *    leur propre instance. Si SpringJsfConfig déclare un @Bean avec un
 *    strength différent de 10 (la valeur par défaut), les vérifications
 *    échoueraient silencieusement. Une seule instance partagée garantit
 *    la cohérence.
 *    Prérequis : ajouter dans SpringJsfConfig.java :
 *      @Bean
 *      public BCryptPasswordEncoder passwordEncoder() {
 *          return new BCryptPasswordEncoder();
 *      }
 *
 * 2. authenticate() n'appliquait pas le verrouillage de compte.
 *    L'entité User possède enregistrerEchecConnexion() (auto-verrouillage
 *    après 5 échecs) et reinitialiserEchecs() mais ces méthodes n'étaient
 *    jamais appelées. Un attaquant pouvait tenter un nombre illimité de
 *    mots de passe.
 *    Ajout :
 *      - Vérification compteBloque avant l'authentification
 *      - enregistrerEchecConnexion() + userDao.update() en cas d'échec
 *      - reinitialiserEchecs() + userDao.update() en cas de succès
 *      - Log d'audit sur chaque tentative échouée
 *
 * 3. getRolePrincipal() était dupliquée avec SessionBean.getRolePrincipal().
 *    Conservée pour compatibilité mais marquée @Deprecated.
 */
@Service
@Transactional
public class UserService {

    @Autowired private UserDao              userDao;
    @Autowired private RoleDao              roleDao;
    @Autowired private LogActionDao         logActionDao;

    // CORRECTION 1 : injecté par Spring, plus de new BCryptPasswordEncoder()
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    // ── Inscription ────────────────────────────────────────────────────────

    /**
     * Inscrit un nouvel utilisateur avec le rôle CANDIDAT.
     * Le mot de passe fourni dans user.getPassword() doit être en clair —
     * il est hashé ici avant persistence.
     */
    public void inscrireUtilisateur(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        Role roleCandidat = roleDao.findByNom("CANDIDAT");
        if (roleCandidat != null) {
            user.getRoles().add(roleCandidat);
        } else {
            System.err.println("AVERTISSEMENT : Rôle CANDIDAT introuvable — vérifiez la table roles");
        }

        userDao.save(user);
    }

    // ── Authentification ───────────────────────────────────────────────────

    /**
     * Authentifie un utilisateur par username + mot de passe.
     *
     * CORRECTION 2 : applique désormais le verrouillage de compte.
     *
     * Séquence :
     *  1. Compte introuvable           → false (log)
     *  2. Compte désactivé (actif=false) → false (log)
     *  3. Compte bloqué (≥5 échecs)    → false (log)
     *  4. Mot de passe incorrect       → incrément échecs, update, log, false
     *     → si 5e échec : compte bloqué automatiquement
     *  5. Mot de passe correct         → remise à zéro des échecs, update, log, true
     *
     * @return true si authentification réussie
     */
    public boolean authenticate(String username, String password) {
        User user = userDao.findByUsername(username);

        // 1. Utilisateur introuvable
        if (user == null) {
            System.out.println("Auth échouée — utilisateur introuvable : " + username);
            logActionDao.save(LogAction.of(username, "CONNEXION_ECHEC",
                "Utilisateur introuvable"));
            return false;
        }

        // 2. Compte désactivé
        if (!user.isActif()) {
            System.out.println("Auth échouée — compte désactivé : " + username);
            logActionDao.save(LogAction.of(username, "CONNEXION_ECHEC",
                "Compte désactivé"));
            return false;
        }

        // 3. Compte bloqué (trop de tentatives)
        if (user.isCompteBloque()) {
            System.out.println("Auth échouée — compte bloqué : " + username);
            logActionDao.save(LogAction.of(username, "CONNEXION_BLOQUEE",
                "Compte verrouillé après trop de tentatives"));
            return false;
        }

        // 4 & 5. Vérification du mot de passe
        boolean match = passwordEncoder.matches(password, user.getPassword());

        if (match) {
            // Succès : remise à zéro des compteurs et mise à jour derniereConnexion
            user.reinitialiserEchecs();
            userDao.update(user);
            System.out.println("Auth réussie : " + username);
        } else {
            // Échec : incrément du compteur (bloque automatiquement si >= 5)
            user.enregistrerEchecConnexion();
            userDao.update(user);

            if (user.isCompteBloque()) {
                System.out.println("Auth échouée — compte verrouillé après trop d'échecs : " + username);
                logActionDao.save(LogAction.of(username, "COMPTE_VERROUILLE",
                    "Compte verrouillé après " + user.getTentativesConnexion() + " tentatives"));
            } else {
                System.out.println("Auth échouée — mot de passe incorrect : " + username
                    + " (tentative " + user.getTentativesConnexion() + "/5)");
                logActionDao.save(LogAction.of(username, "CONNEXION_ECHEC",
                    "Mot de passe incorrect (tentative " + user.getTentativesConnexion() + "/5)"));
            }
        }

        return match;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean usernameExiste(String username) {
        return userDao.findByUsername(username) != null;
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userDao.findByUsername(username);
    }

    /**
     * @deprecated Utiliser SessionBean.getRolePrincipal() en contexte JSF.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public String getRolePrincipal(String username) {
        User user = userDao.findByUsername(username);
        if (user == null || user.getRoles().isEmpty()) return "AUCUN";
        return user.getRoles().iterator().next().getNom();
    }
}