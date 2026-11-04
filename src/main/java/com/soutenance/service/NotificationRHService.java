package com.soutenance.service;

import com.soutenance.dao.NotificationDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.Notification;
import com.soutenance.entity.User;
import com.soutenance.notification.ModeleNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationRHService {

    @Autowired private UserDao userDao;
    @Autowired private NotificationDao notificationDao;

    public void notifierRH(Long institutionId, ModeleNotification modele, String entiteLiee, Long entiteLieeId,
                           Object... valeurs) {
        if (institutionId == null) return;
        for (User rh : userDao.findActifsByRoleAndInstitution("RH", institutionId)) {
            Notification n = new Notification();
            n.setTitre(modele.getTitre());
            n.setMessage(modele.rendreMessage(valeurs));
            n.setType(modele.getType());
            n.setPriorite(modele.getPriorite());
            n.setDestinataire(rh);
            n.setEntiteLiee(entiteLiee);
            n.setEntiteLieeId(entiteLieeId);
            n.setLienAction(modele.getLienAction());
            notificationDao.save(n);
        }
    }

    /** Notifie un utilisateur précis (ex : un membre de jury), quel que soit son rôle. */
    public void notifierUtilisateur(User destinataire, ModeleNotification modele, String entiteLiee, Long entiteLieeId,
                                     Object... valeurs) {
        if (destinataire == null) return;
        Notification n = new Notification();
        n.setTitre(modele.getTitre());
        n.setMessage(modele.rendreMessage(valeurs));
        n.setType(modele.getType());
        n.setPriorite(modele.getPriorite());
        n.setDestinataire(destinataire);
        n.setEntiteLiee(entiteLiee);
        n.setEntiteLieeId(entiteLieeId);
        n.setLienAction(modele.getLienAction());
        notificationDao.save(n);
    }
}
