package com.soutenance.scheduler;

import com.soutenance.dao.CyclePaieDao;
import com.soutenance.entity.CyclePaie;
import com.soutenance.notification.ModeleNotification;
import com.soutenance.service.NotificationRHService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Prévient le RH quand la date de versement prévue d'un cycle de paie approche
 * (5 jours par défaut) alors que le calcul n'a pas encore été lancé — pour
 * éviter de découvrir au dernier moment que la masse salariale dépasse le budget.
 * N'agit jamais à la place du RH : envoie uniquement une notification.
 */
@Component
public class CyclePaieScheduler {

    private static final int JOURS_SEUIL_ALERTE = 5;

    @Autowired private CyclePaieDao cycleDao;
    @Autowired private NotificationRHService notificationRHService;

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000) // toutes les 6 heures
    @Transactional
    public void alerterVersementsProches() {
        List<CyclePaie> cycles = cycleDao.findCyclesVersementProche(JOURS_SEUIL_ALERTE);
        for (CyclePaie cycle : cycles) {
            if (cycle.getInstitution() == null || cycle.getDatePaiementPrevue() == null) continue;

            long joursRestants = ChronoUnit.DAYS.between(LocalDate.now(), cycle.getDatePaiementPrevue());
            notificationRHService.notifierRH(
                cycle.getInstitution().getId(),
                ModeleNotification.CYCLE_PAIE_VERSEMENT_PROCHE,
                "CYCLE_PAIE", cycle.getId(),
                cycle.getPeriodeLibelle(), Math.max(0, joursRestants));

            cycle.setAlerteVersementEnvoyee(true);
            cycleDao.update(cycle);
        }
    }
}
