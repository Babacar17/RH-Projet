package com.soutenance.service;

import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service de réinitialisation de mot de passe par lien email.
 *
 * Flow :
 *   1. L'utilisateur demande un reset → demanderReset(email)
 *      Un token aléatoire est stocké (hashé) avec une expiration de 1h.
 *      Le lien complet contenant le token brut est envoyé par email.
 *   2. L'utilisateur clique sur le lien → page login avec ?reset=TOKEN
 *      validerToken(token) → retourne le User si OK, null sinon.
 *   3. L'utilisateur saisit son nouveau mot de passe → reinitialiser(token, mdp)
 *      Le token est consommé (effacé) et le compte est débloqué.
 */
@Service
public class PasswordResetService {

    @Autowired private UserDao              userDao;
    @Autowired private LogActionDao         logDao;
    @Autowired private BCryptPasswordEncoder encoder;
    @Autowired(required = false) private EmailService emailService;

    private static final SecureRandom RNG = new SecureRandom();
    /** Durée de validité du lien de reset. */
    private static final long EXPIRATION_HEURES = 1;

    // ── Étape 1 — Demande de reset ───────────────────────────────────────────

    /**
     * Génère un token, le stocke (hashé) avec une expiration, envoie l'email.
     * Pour éviter l'énumération d'emails, on retourne toujours true même si
     * l'email est inconnu (silent failure).
     */
    @Transactional
    public boolean demanderReset(String email) {
        if (email == null || email.isBlank()) return false;
        User user = userDao.findByEmail(email.trim());
        if (user == null) {
            // On loggue pour audit mais on ne révèle rien au demandeur
            logDao.save(LogAction.of("PUBLIC", "RESET_DEMANDE_INCONNUE",
                "Tentative de reset pour email inconnu : " + email));
            return true;
        }

        String tokenBrut   = genererTokenAleatoire();
        String tokenHash   = encoder.encode(tokenBrut);
        user.setTokenReset(tokenHash);
        user.setDateExpirationToken(LocalDateTime.now().plusHours(EXPIRATION_HEURES));
        userDao.update(user);

        logDao.save(LogAction.of(user.getUsername(), "RESET_DEMANDE",
            "Lien de reset généré (expire dans " + EXPIRATION_HEURES + "h)"));

        if (emailService != null) {
            emailService.envoyerResetMdp(
                user.getEmail(),
                user.getPrenom() != null ? user.getPrenom() : user.getUsername(),
                tokenBrut,
                "SYSTEM");
        }
        return true;
    }

    // ── Étape 2 — Validation du token (sans réinitialiser) ───────────────────

    /**
     * Vérifie qu'un token est encore valide et retourne l'utilisateur associé.
     *
     * On ne peut pas indexer par token hashé (BCrypt change à chaque calcul).
     * On parcourt donc les utilisateurs ayant un token non expiré et on teste.
     */
    @Transactional(readOnly = true)
    public User validerToken(String tokenBrut) {
        if (tokenBrut == null || tokenBrut.isBlank()) return null;
        for (User u : userDao.findAll()) {
            if (u.getTokenReset() == null || u.getDateExpirationToken() == null) continue;
            if (LocalDateTime.now().isAfter(u.getDateExpirationToken()))         continue;
            if (encoder.matches(tokenBrut, u.getTokenReset())) {
                return u;
            }
        }
        return null;
    }

    // ── Étape 3 — Réinitialisation effective ─────────────────────────────────

    /**
     * Consomme le token, change le mot de passe, débloque le compte.
     * Retourne null si OK, sinon un message d'erreur.
     */
    @Transactional
    public String reinitialiser(String tokenBrut, String nouveauMdp) {
        if (nouveauMdp == null || nouveauMdp.length() < 8) {
            return "Le mot de passe doit contenir au moins 8 caractères.";
        }
        User user = validerToken(tokenBrut);
        if (user == null) {
            return "Lien invalide ou expiré.";
        }
        user.setPassword(encoder.encode(nouveauMdp));
        user.setTokenReset(null);
        user.setDateExpirationToken(null);
        user.reinitialiserEchecs();
        userDao.update(user);
        logDao.save(LogAction.of(user.getUsername(), "RESET_EFFECTUE",
            "Mot de passe réinitialisé via lien email"));
        return null;
    }

    private String genererTokenAleatoire() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
