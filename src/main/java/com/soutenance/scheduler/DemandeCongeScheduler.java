package com.soutenance.scheduler;

import com.soutenance.dao.DemandeCongeDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.DemandeConge;
import com.soutenance.entity.LogAction;
import com.soutenance.notification.ModeleNotification;
import com.soutenance.service.NotificationRHService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Signale au RH les demandes de congé dont la période est déjà terminée sans
 * qu'une décision finale (manager ou RH) ait été prise — ne refuse jamais
 * automatiquement, c'est un simple rappel (une seule fois par demande).
 */
@Component
public class DemandeCongeScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private DemandeCongeDao demandeCongeDao;
    @Autowired private LogActionDao logActionDao;
    @Autowired private NotificationRHService notificationRHService;

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void relancerDemandesEnRetard() {
        LocalDate aujourdhui = LocalDate.now();
        for (DemandeConge d : demandeCongeDao.findAll()) {
            if (d.isRelanceExpirationEnvoyee()) continue;
            if (!enAttente(d.getStatut())) continue;
            if (d.getDateFin() == null || !d.getDateFin().isBefore(aujourdhui)) continue;
            if (d.getEmploye() == null || d.getEmploye().getInstitution() == null) continue;

            notificationRHService.notifierRH(d.getEmploye().getInstitution().getId(),
                ModeleNotification.CONGE_EN_RETARD, "DEMANDE_CONGE", d.getId(),
                d.getEmploye().getNomComplet(),
                d.getTypeConge() != null ? d.getTypeConge().getLibelle() : "congé",
                d.getDateFin().format(FMT));

            d.setRelanceExpirationEnvoyee(true);
            demandeCongeDao.update(d);

            String detail = "Relance : demande de congé de " + d.getEmploye().getNomComplet()
                + " toujours en attente alors que la période (fin le " + d.getDateFin().format(FMT) + ") est passée.";
            logActionDao.save(new LogAction("SCHEDULER", "CONGE_RELANCE_RETARD", detail));
            System.out.println("[Scheduler] " + detail);
        }
    }

    private boolean enAttente(String statut) {
        return "EN_ATTENTE".equalsIgnoreCase(statut)
            || "EN_VALIDATION_MANAGER".equalsIgnoreCase(statut)
            || "EN_VALIDATION_RH".equalsIgnoreCase(statut);
    }
}
