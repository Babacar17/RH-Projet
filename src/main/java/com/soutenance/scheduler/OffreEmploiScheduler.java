package com.soutenance.scheduler;

import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.OffreEmploiDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.OffreEmploi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class OffreEmploiScheduler {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired private OffreEmploiDao offreEmploiDao;
    @Autowired private LogActionDao   logActionDao;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void fermerOffresExpirees() {
        LocalDateTime maintenant = LocalDateTime.now();
        List<OffreEmploi> expirees = offreEmploiDao.findExpirees(maintenant);

        if (expirees.isEmpty()) return;

        for (OffreEmploi offre : expirees) {
            offre.setStatut("FERMEE");
            offreEmploiDao.update(offre);

            // LogAction(username, action, detail)
            String detail = String.format(
                "Fermeture automatique : \"%s\" (limite : %s)",
                offre.getTitre(),
                offre.getDateLimiteFermeture().format(FMT)
            );
            logActionDao.save(new LogAction("SCHEDULER", "FERMETURE_AUTO_OFFRE", detail));

            System.out.println("[Scheduler] " + detail);
        }

        System.out.printf("[Scheduler] %d offre(s) fermée(s) a %s%n",
            expirees.size(), maintenant.format(FMT));
    }
}