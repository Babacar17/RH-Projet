package com.soutenance.scheduler;

import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.RecrutementDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.PhaseOffre;
import com.soutenance.service.RecrutementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Fait vivre les phases planifiées par le RH (dateDebutPrevue / dateLimite)
 * sans action manuelle : lance une phase PLANIFIEE dès que sa date de début
 * est atteinte (et que la précédente est clôturée), et clôture une phase
 * EN_COURS dès que sa date de fin est dépassée.
 */
@Component
public class PhaseOffreScheduler {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired private RecrutementDao recrutementDao;
    @Autowired private RecrutementService recrutementService;
    @Autowired private LogActionDao logActionDao;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void gererPhasesPlanifiees() {
        LocalDateTime maintenant = LocalDateTime.now();
        cloturerPhasesEchues(maintenant);
        lancerPhasesPlanifiees(maintenant);
    }

    private void cloturerPhasesEchues(LocalDateTime maintenant) {
        List<PhaseOffre> aCloturer = recrutementDao.findPhasesEnCoursDelaiDepasse(maintenant);
        for (PhaseOffre po : aCloturer) {
            recrutementService.cloturerPhase(po);

            String detail = String.format(
                "Clôture automatique : phase \"%s\" de l'offre \"%s\" (fin prévue : %s)",
                po.getPhase().getLibelle(), po.getOffre().getTitre(), po.getDateLimite().format(FMT));
            logActionDao.save(new LogAction("SCHEDULER", "CLOTURE_AUTO_PHASE", detail));
            System.out.println("[Scheduler] " + detail);
        }
    }

    private void lancerPhasesPlanifiees(LocalDateTime maintenant) {
        List<PhaseOffre> candidates = recrutementDao.findPhasesPlanifieesALancer(maintenant);
        for (PhaseOffre po : candidates) {
            if (!precedenteCloturee(po)) continue;

            recrutementService.activerPhasePlanifiee(po, "SCHEDULER");

            String detail = String.format(
                "Lancement automatique : phase \"%s\" de l'offre \"%s\" (début prévu : %s)",
                po.getPhase().getLibelle(), po.getOffre().getTitre(), po.getDateDebutPrevue().format(FMT));
            logActionDao.save(new LogAction("SCHEDULER", "LANCEMENT_AUTO_PHASE", detail));
            System.out.println("[Scheduler] " + detail);
        }
    }

    /** Vraie si c'est la première phase du référentiel, ou si la phase de numeroOrdre précédent est déjà CLOTUREE. */
    private boolean precedenteCloturee(PhaseOffre po) {
        List<PhaseOffre> toutes = recrutementDao.findPhasesOffreByOffre(po.getOffre().getId());
        int ordre = po.getPhase().getNumeroOrdre();
        return toutes.stream()
            .filter(p -> p.getPhase().getNumeroOrdre() < ordre)
            .max(Comparator.comparing(p -> p.getPhase().getNumeroOrdre()))
            .map(p -> "CLOTUREE".equals(p.getStatut()))
            .orElse(true);
    }
}
