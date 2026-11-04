package com.soutenance.service;

import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.LogAction;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Service d'envoi d'emails (Jakarta Mail).
 *
 * Configuration via variables d'environnement :
 *   SOUTENANCE_SMTP_HOST     (défaut : smtp.gmail.com)
 *   SOUTENANCE_SMTP_PORT     (défaut : 587)
 *   SOUTENANCE_SMTP_USER     (obligatoire pour envoi réel)
 *   SOUTENANCE_SMTP_PASSWORD (obligatoire pour envoi réel)
 *   SOUTENANCE_SMTP_FROM     (défaut : SMTP_USER)
 *   SOUTENANCE_BASE_URL      (défaut : http://localhost:8080/Soutenance)
 *
 * Si SMTP_USER ou SMTP_PASSWORD est absent, l'envoi est désactivé et
 * le contenu de l'email est journalisé pour développement local
 * (mode « console »). Cela permet à l'application de fonctionner sans
 * crasher en l'absence de configuration SMTP.
 */
@Service
public class EmailService {

    @Autowired(required = false) private LogActionDao logActionDao;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    // ── Envois métier ────────────────────────────────────────────────────────

    /**
     * Envoi d'une invitation à un nouvel utilisateur (lien d'activation et
     * mot de passe temporaire). En production, remplacer le mot de passe
     * temporaire par un token à usage unique permettant de saisir son mdp.
     */
    public boolean envoyerInvitation(String destinataire, String prenom,
                                     String username, String mdpTemporaire,
                                     String operateur) {
        String url = baseUrl() + "/Authentification/login.xhtml";
        String corps =
            "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">" +
            "<h2 style=\"color:#1F4E79;\">Bienvenue " + safe(prenom) + " !</h2>" +
            "<p>Un compte vient d'être créé pour vous sur la plateforme SIRH.</p>" +
            "<table style=\"border-collapse:collapse;margin:14px 0;\">" +
            "<tr><td style=\"padding:6px 14px;background:#f8fafc;font-weight:600;\">Identifiant</td>" +
            "<td style=\"padding:6px 14px;background:#f8fafc;font-family:monospace;\">" + safe(username) + "</td></tr>" +
            "<tr><td style=\"padding:6px 14px;font-weight:600;\">Mot de passe temporaire</td>" +
            "<td style=\"padding:6px 14px;font-family:monospace;\">" + safe(mdpTemporaire) + "</td></tr>" +
            "</table>" +
            "<p><a href=\"" + url + "\" style=\"display:inline-block;background:#1F4E79;color:#fff;" +
            "padding:10px 18px;border-radius:6px;text-decoration:none;\">Se connecter</a></p>" +
            "<p style=\"color:#888;font-size:12px;margin-top:18px;\">" +
            "Vous devrez changer votre mot de passe lors de votre première connexion.</p>" +
            "</div>";
        return envoyer(destinataire,
            "Bienvenue sur la plateforme SIRH", corps,
            operateur, "INVITATION_USER");
    }

    /**
     * Envoi d'un lien de réinitialisation de mot de passe.
     * Le token est à usage unique et expire après 1 heure.
     */
    public boolean envoyerResetMdp(String destinataire, String prenom,
                                   String token, String operateur) {
        String url = baseUrl() + "/Authentification/reset.xhtml?token=" + token;
        String corps =
            "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">" +
            "<h2 style=\"color:#1F4E79;\">Réinitialisation de votre mot de passe</h2>" +
            "<p>Bonjour " + safe(prenom) + ",</p>" +
            "<p>Une demande de réinitialisation de mot de passe a été faite pour votre compte. " +
            "Cliquez sur le bouton ci-dessous pour choisir un nouveau mot de passe :</p>" +
            "<p style=\"margin:18px 0;\"><a href=\"" + url + "\" style=\"display:inline-block;" +
            "background:#dc2626;color:#fff;padding:10px 18px;border-radius:6px;text-decoration:none;\">" +
            "Réinitialiser mon mot de passe</a></p>" +
            "<p style=\"color:#888;font-size:12px;\">Ce lien est valable une heure. " +
            "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.</p>" +
            "<p style=\"color:#888;font-size:11px;margin-top:18px;\">Envoyé le " +
            LocalDateTime.now().format(FMT) + "</p>" +
            "</div>";
        return envoyer(destinataire,
            "Réinitialisation de votre mot de passe", corps,
            operateur, "RESET_MDP_EMAIL");
    }

    /**
     * Notification générique (validation congé, nouvelle candidature, etc.).
     */
    public boolean envoyerNotification(String destinataire, String sujet,
                                       String corpsHtml, String operateur) {
        return envoyer(destinataire, sujet, corpsHtml, operateur, "EMAIL_NOTIFICATION");
    }

    // ── Cœur de l'envoi ──────────────────────────────────────────────────────

    private boolean envoyer(String destinataire, String sujet, String corpsHtml,
                            String operateur, String codeAction) {
        if (destinataire == null || destinataire.isBlank()) {
            traceLog(operateur, codeAction + "_ECHEC", "Destinataire vide");
            return false;
        }

        String host = envOr("SOUTENANCE_SMTP_HOST", "smtp.gmail.com");
        String port = envOr("SOUTENANCE_SMTP_PORT", "587");
        String user = envOr("SOUTENANCE_SMTP_USER", null);
        String pass = envOr("SOUTENANCE_SMTP_PASSWORD", null);
        String from = envOr("SOUTENANCE_SMTP_FROM", user);

        // Mode console : SMTP non configuré → on logue le contenu pour dev local
        if (user == null || pass == null) {
            System.out.println("\n────────────── EMAIL (mode console) ──────────────");
            System.out.println("À      : " + destinataire);
            System.out.println("Sujet  : " + sujet);
            System.out.println("Corps  :\n" + corpsHtml.replaceAll("<[^>]+>", ""));
            System.out.println("──────────────────────────────────────────────────\n");
            traceLog(operateur, codeAction + "_CONSOLE",
                "Email simulé (SMTP non configuré) vers " + destinataire + " — sujet : " + sujet);
            return true;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout",           "10000");

            final String u = user, p = pass;
            Session session = Session.getInstance(props, new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(u, p);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire));
            msg.setSubject(sujet, StandardCharsets.UTF_8.name());
            msg.setContent(corpsHtml, "text/html; charset=UTF-8");
            msg.setSentDate(new java.util.Date());

            Transport.send(msg);
            traceLog(operateur, codeAction,
                "Email envoyé à " + destinataire + " — sujet : " + sujet);
            return true;

        } catch (MessagingException ex) {
            traceLog(operateur, codeAction + "_ECHEC",
                "Erreur SMTP vers " + destinataire + " — " + ex.getMessage());
            return false;
        }
    }

    private void traceLog(String op, String action, String detail) {
        if (logActionDao != null) {
            try { logActionDao.save(LogAction.of(op, action, detail)); }
            catch (Exception ignored) { /* log best-effort */ }
        }
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /** URL publique de l'application pour construire les liens dans les emails. */
    private static String baseUrl() {
        return envOr("SOUTENANCE_BASE_URL", "http://localhost:8080/Soutenance");
    }

    private static String safe(String s) { return s != null ? s : ""; }
}
