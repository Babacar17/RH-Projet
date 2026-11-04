package com.soutenance.service;

import com.soutenance.dao.NotificationDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.Notification;
import com.soutenance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Diffusion d'annonces globales par le super-admin.
 *
 * Trois ciblages possibles :
 *  - TOUS              : tous les utilisateurs actifs
 *  - ROLE              : tous les utilisateurs ayant un rôle donné
 *  - INSTITUTION       : tous les utilisateurs d'une institution donnée
 *
 * Chaque annonce génère une {@link Notification} par destinataire et
 * optionnellement un email. Toute l'opération est tracée dans le
 * journal d'audit.
 */
@Service
@Transactional
public class AnnonceService {

    @Autowired private UserDao         userDao;
    @Autowired private NotificationDao notificationDao;
    @Autowired private com.soutenance.dao.LogActionDao logActionDao;
    @Autowired(required = false) private EmailService emailService;

    /**
     * Envoie l'annonce à la cible choisie.
     *
     * @param cible           "TOUS" / "ROLE" / "INSTITUTION"
     * @param parametre       valeur dépendant de la cible :
     *                          ROLE        → nom du rôle (ADMIN, RH, EMPLOYE, etc.)
     *                          INSTITUTION → id institution (String)
     *                          TOUS        → ignoré
     * @param titre           sujet de l'annonce
     * @param message         corps texte (pas HTML)
     * @param priorite        BASSE / NORMALE / HAUTE / URGENTE
     * @param avecEmail       true pour envoyer aussi un email
     * @return nombre de destinataires touchés
     */
    public int diffuserAnnonce(String cible, String parametre,
                               String titre, String message,
                               String priorite, boolean avecEmail,
                               String operateur) {
        if (titre == null || titre.isBlank()) return 0;
        List<User> destinataires = resoudreCible(cible, parametre);
        String cibleDesc = cible
            + (parametre != null && !parametre.isBlank() ? " (" + parametre + ")" : "");
        Long institutionIdPourLog = null;
        if ("INSTITUTION".equalsIgnoreCase(cible) && parametre != null && !parametre.isBlank()) {
            try { institutionIdPourLog = Long.parseLong(parametre); } catch (NumberFormatException ignored) {}
        }
        return envoyerA(destinataires, titre, message, priorite, avecEmail, operateur, cibleDesc, institutionIdPourLog);
    }

    /**
     * Diffusion restreinte à une institution (utilisée par le RH — jamais de
     * portée "TOUS" ou inter-institutions, contrairement à diffuserAnnonce).
     *
     * @param institutionId   institution de l'opérateur, obligatoire
     * @param roleCible       nom de rôle pour filtrer, ou null/blank pour toute l'institution
     */
    public int diffuserAnnonceInstitution(Long institutionId, String roleCible,
                               String titre, String message,
                               String priorite, boolean avecEmail,
                               String operateur) {
        if (titre == null || titre.isBlank() || institutionId == null) return 0;

        List<User> destinataires = userDao.findAll().stream()
            .filter(User::isActif)
            .filter(u -> u.getInstitution() != null
                      && institutionId.equals(u.getInstitution().getId()))
            .filter(u -> roleCible == null || roleCible.isBlank()
                      || (u.getRoles() != null && u.getRoles().stream()
                              .anyMatch(r -> roleCible.equalsIgnoreCase(r.getNom()))))
            .collect(Collectors.toList());

        String cibleDesc = "INSTITUTION #" + institutionId
            + (roleCible != null && !roleCible.isBlank() ? " / ROLE=" + roleCible : "");
        return envoyerA(destinataires, titre, message, priorite, avecEmail, operateur, cibleDesc, institutionId);
    }

    private int envoyerA(List<User> destinataires, String titre, String message,
                          String priorite, boolean avecEmail, String operateur,
                          String cibleDesc, Long institutionIdPourLog) {
        if (priorite == null) priorite = "NORMALE";
        if (destinataires.isEmpty()) return 0;

        int ok = 0;
        for (User u : destinataires) {
            try {
                Notification n = new Notification();
                n.setTitre(titre);
                n.setMessage(message);
                n.setType("ALERTE_SYSTEME");
                n.setPriorite(priorite);
                n.setDestinataire(u);
                notificationDao.save(n);

                if (avecEmail && emailService != null && u.getEmail() != null
                        && !u.getEmail().isBlank()) {
                    String corps = construireCorpsEmail(titre, message, priorite);
                    boolean envoye = emailService.envoyerNotification(
                        u.getEmail(), "[" + priorite + "] " + titre,
                        corps, operateur);
                    if (envoye) {
                        n.setEmailEnvoye(true);
                        notificationDao.update(n);
                    }
                }
                ok++;
            } catch (Exception ignored) { /* skip user */ }
        }

        LogAction log = LogAction.of(operateur, "ANNONCE_DIFFUSION",
            "[" + priorite + "] « " + titre + " » → " + cibleDesc + " → " + ok + " destinataire(s)"
            + (avecEmail ? " (+ email)" : ""));
        log.setInstitutionId(institutionIdPourLog);
        logActionDao.save(log);
        return ok;
    }

    /** Résout la cible en liste d'utilisateurs. */
    private List<User> resoudreCible(String cible, String parametre) {
        List<User> tous = userDao.findAll();
        if (cible == null) cible = "TOUS";
        switch (cible.toUpperCase()) {
            case "ROLE":
                if (parametre == null || parametre.isBlank()) return List.of();
                String role = parametre.toUpperCase();
                return tous.stream()
                    .filter(User::isActif)
                    .filter(u -> u.getRoles() != null
                              && u.getRoles().stream()
                                  .anyMatch(r -> role.equals(r.getNom())))
                    .collect(Collectors.toList());
            case "INSTITUTION":
                if (parametre == null || parametre.isBlank()) return List.of();
                try {
                    Long instId = Long.parseLong(parametre);
                    return tous.stream()
                        .filter(User::isActif)
                        .filter(u -> u.getInstitution() != null
                                  && instId.equals(u.getInstitution().getId()))
                        .collect(Collectors.toList());
                } catch (NumberFormatException ex) { return List.of(); }
            case "TOUS":
            default:
                return tous.stream()
                    .filter(User::isActif)
                    .collect(Collectors.toList());
        }
    }

    private String construireCorpsEmail(String titre, String message, String priorite) {
        String accent;
        switch (priorite != null ? priorite.toUpperCase() : "NORMALE") {
            case "URGENTE": accent = "#dc2626"; break;
            case "HAUTE":   accent = "#f59e0b"; break;
            case "BASSE":   accent = "#94a3b8"; break;
            default:        accent = "#2563eb";
        }
        String texte = message != null
            ? message.replace("\n", "<br/>") : "";
        return "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">"
             + "<div style=\"background:" + accent + ";color:#fff;padding:14px 18px;"
             + "border-radius:8px 8px 0 0;font-weight:700;\">"
             + priorite + " — " + safe(titre) + "</div>"
             + "<div style=\"padding:18px;border:1px solid #e2e8f0;border-top:none;"
             + "border-radius:0 0 8px 8px;line-height:1.6;\">"
             + texte
             + "<p style=\"color:#888;font-size:12px;margin-top:18px;\">"
             + "Annonce émise par l'administration. Le " + LocalDateTime.now() + "</p>"
             + "</div></div>";
    }

    private String safe(String s) { return s != null ? s : ""; }
}
