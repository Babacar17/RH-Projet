package com.soutenance.bean;

import com.soutenance.dao.NotificationDao;
import com.soutenance.entity.Notification;
import com.soutenance.entity.User;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean cloche de notifications, accessible dans n'importe quelle sidebar
 * via {@code #{notificationBean.xxx}}.
 *
 * Fonctionnalités :
 *  - Compteur de notifications non lues
 *  - Liste des notifications récentes (10 dernières) pour le menu dropdown
 *  - Action « marquer comme lue » sur une notification cliquée
 *  - Action « tout marquer comme lu »
 */
@Component("notificationBean")
@Scope("view")
public class NotificationBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired(required = false) private NotificationDao notificationDao;
    @Autowired private SessionBean sessionBean;

    private List<Notification> recentes = new ArrayList<>();
    private long               compteurNonLues;

    @PostConstruct
    public void init() { recharger(); }

    public void recharger() {
        User u = sessionBean.getUtilisateurConnecte();
        if (u == null || notificationDao == null) {
            recentes        = new ArrayList<>();
            compteurNonLues = 0;
            return;
        }
        try {
            recentes        = notificationDao.findRecentesByDestinataire(u.getId(), 10);
            compteurNonLues = notificationDao.countNonLues(u.getId());
        } catch (Exception e) {
            recentes        = new ArrayList<>();
            compteurNonLues = 0;
        }
    }

    /** Marque une notification comme lue et recharge la liste. */
    public void marquerLue(Long notifId) {
        if (notificationDao == null || notifId == null) return;
        try {
            Notification n = notificationDao.findById(notifId);
            if (n != null) {
                n.marquerLue();
                notificationDao.update(n);
            }
            recharger();
        } catch (Exception ignored) { /* best effort */ }
    }

    /** Marque toutes les notifications de l'utilisateur comme lues. */
    public void marquerToutesLues() {
        User u = sessionBean.getUtilisateurConnecte();
        if (u == null || notificationDao == null) return;
        try {
            for (Notification n : notificationDao.findNonLuesByDestinataire(u.getId())) {
                n.marquerLue();
                notificationDao.update(n);
            }
            recharger();
        } catch (Exception ignored) { /* best effort */ }
    }

    /** Icône PrimeIcons correspondant au type de notification. */
    public String getIcone(String type) {
        if (type == null) return "pi-bell";
        return switch (type) {
            case "CONGE_SOUMIS"               -> "pi-calendar-plus";
            case "CONGE_APPROUVE"             -> "pi-check-circle";
            case "CONGE_REFUSE"               -> "pi-times-circle";
            case "EVALUATION_PLANIFIEE"       -> "pi-star";
            case "CONTRAT_EXPIRATION"         -> "pi-file-edit";
            case "PAIE_DISPONIBLE"            -> "pi-wallet";
            case "RECRUTEMENT_CANDIDATURE"    -> "pi-user-plus";
            case "FORMATION_INSCRIPTION"      -> "pi-book";
            case "ALERTE_SYSTEME"             -> "pi-exclamation-triangle";
            default                            -> "pi-bell";
        };
    }

    /** Couleur d'accent CSS selon la priorité. */
    public String getCouleur(String priorite) {
        if (priorite == null) return "#64748b";
        return switch (priorite) {
            case "URGENTE" -> "#dc2626";
            case "HAUTE"   -> "#f59e0b";
            case "BASSE"   -> "#94a3b8";
            default        -> "#2563eb";
        };
    }

    /** Format relatif lisible : « il y a 2h », « hier », etc. */
    public String getDateRelative(java.time.LocalDateTime d) {
        if (d == null) return "";
        java.time.Duration delta = java.time.Duration.between(d, java.time.LocalDateTime.now());
        long minutes = delta.toMinutes();
        if (minutes < 1)     return "à l'instant";
        if (minutes < 60)    return "il y a " + minutes + " min";
        long heures = delta.toHours();
        if (heures < 24)     return "il y a " + heures + " h";
        long jours = delta.toDays();
        if (jours < 7)       return "il y a " + jours + " j";
        if (jours < 30)      return "il y a " + (jours / 7) + " sem";
        return d.toLocalDate().toString();
    }

    public List<Notification> getRecentes()        { return recentes; }
    public long               getCompteurNonLues() { return compteurNonLues; }
    public boolean            isAUneNouvelle()     { return compteurNonLues > 0; }

    /** Historique complet (pas seulement les 10 dernières) — pour une page dédiée "Toutes mes notifications". */
    public List<Notification> getToutes() {
        User u = sessionBean.getUtilisateurConnecte();
        if (u == null || notificationDao == null) return new ArrayList<>();
        try {
            return notificationDao.findByDestinataire(u.getId());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
