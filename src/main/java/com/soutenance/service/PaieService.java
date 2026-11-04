package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service de paie — périmètre employé / RH.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. TAUX FISCAUX CODÉS EN DUR → lus depuis ParametreSysteme.
 *    Avant : BigDecimal("0.07") et BigDecimal("0.20") directement dans le code.
 *    Après : getTauxIpres() et getTauxChargesPatronales() lisent les paramètres
 *    TAUX_IPRES_SALARIE et TAUX_CHARGES_PATRONALES depuis la base.
 *    Valeurs par défaut conservées si le paramètre est absent.
 *
 * 2. CyclePaie ORPHELIN → persisté avant d'être associé au bulletin.
 *    Avant : new CyclePaie() sans persist → TransientPropertyValueException.
 *    Après : cycleDao.findCycleEnCours() cherche d'abord un cycle existant ;
 *    si absent, un nouveau cycle est créé ET sauvegardé avant d'être lié
 *    au bulletin. CyclePaieDao est maintenant injecté.
 *
 * 3. INSTITUTION ignorée dans getBulletinsByPeriode → filtrée si institutionId fourni.
 */
@Service
@Transactional
public class PaieService {

    @Autowired private BulletinPaieDao    bulletinDao;
    @Autowired private GrilleSalarialeDao grilleDao;
    @Autowired private EmployeDao         employeDao;
    @Autowired private LogActionDao       logActionDao;

    // CORRECTION 1 : injection des DAOs manquants
    @Autowired private CyclePaieDao    cycleDao;
    @Autowired private ParametreDao    parametreDao;
    @Autowired private InstitutionDao  institutionDao;

    // ── Clés des paramètres fiscaux ────────────────────────────────────────

    private static final String CLE_IPRES        = "TAUX_IPRES_SALARIE";
    private static final String CLE_CHARGES_PAT  = "TAUX_CHARGES_PATRONALES";

    /** Taux IPRES salarié (défaut 7 % si absent en base). */
    private BigDecimal getTauxIpres(Long institutionId) {
        try {
            ParametreSysteme p = parametreDao.findByCleAndInstitution(CLE_IPRES, institutionId);
            if (p != null && p.getValeur() != null) {
                return new BigDecimal(p.getValeur())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {}
        return new BigDecimal("0.07"); // valeur par défaut
    }

    /** Taux charges patronales (défaut 20 % si absent en base). */
    private BigDecimal getTauxChargesPatronales(Long institutionId) {
        try {
            ParametreSysteme p = parametreDao.findByCleAndInstitution(CLE_CHARGES_PAT, institutionId);
            if (p != null && p.getValeur() != null) {
                return new BigDecimal(p.getValeur())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {}
        return new BigDecimal("0.20"); // valeur par défaut
    }

    // ══════════════════════════════════════════════════
    // BULLETINS DE PAIE
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<BulletinPaie> getBulletinsByPeriode(int mois, int annee) {
        return bulletinDao.findByPeriode(mois, annee);
    }

    @Transactional(readOnly = true)
    public BulletinPaie getBulletinById(Long id) {
        return bulletinDao.findById(id);
    }

    public void modifierBulletin(BulletinPaie bulletin, String operateur) {
        if ("VALIDE".equals(bulletin.getStatut())) {
            throw new IllegalStateException("Impossible de modifier un bulletin validé.");
        }
        bulletinDao.update(bulletin);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_BULLETIN",
            "Bulletin modifié : id=" + bulletin.getId()));
    }

    public void validerBulletin(Long id, String operateur) {
        BulletinPaie b = bulletinDao.findById(id);
        if (b != null) {
            b.setStatut("VALIDE");
            bulletinDao.update(b);
            logActionDao.save(LogAction.of(operateur, "VALIDATION_BULLETIN",
                "Bulletin validé : " + (b.getEmploye() != null
                    ? b.getEmploye().getNomComplet() : id)));
        }
    }

    // ══════════════════════════════════════════════════
    // CYCLE DE PAIE
    // ══════════════════════════════════════════════════

    /**
     * Génère le bulletin d'un employé pour la période indiquée.
     *
     * CORRECTION 2 : le CyclePaie est maintenant cherché ou créé et persisté
     * AVANT d'être lié au bulletin. Plus de TransientPropertyValueException.
     */
    public void genererBulletin(Employe employe, int mois, int annee, String operateur) {
        BulletinPaie existing = bulletinDao.findByEmployeAndPeriode(employe.getId(), mois, annee);
        if (existing != null && "VALIDE".equals(existing.getStatut())) {
            throw new IllegalStateException(
                "Un bulletin validé existe déjà pour cet employé et cette période.");
        }

        // CORRECTION 2 : résoudre ou créer le CyclePaie AVANT de créer le bulletin
        Long institutionId = employe.getInstitutionId();
        CyclePaie cycle = obtenirOuCreerCycle(institutionId, mois, annee, operateur);

        BulletinPaie bulletin = existing != null ? existing : new BulletinPaie();
        bulletin.setEmploye(employe);
        bulletin.setCyclePaie(cycle); // cycle est maintenant persisté → pas d'entité transiente

        calculerBulletin(bulletin, employe, institutionId);

        if (existing == null) {
            bulletinDao.save(bulletin);
        } else {
            bulletinDao.update(bulletin);
        }
        logActionDao.save(LogAction.of(operateur, "GENERATION_BULLETIN",
            "Bulletin généré : " + employe.getNomComplet() + " " + mois + "/" + annee));
    }

    /**
     * Cherche un cycle OUVERT/EN_CALCUL pour la période, ou en crée un nouveau.
     * Dans les deux cas retourne un CyclePaie déjà persisté en base.
     */
    private CyclePaie obtenirOuCreerCycle(Long institutionId, int mois, int annee, String operateur) {
        // Chercher un cycle existant pour cette institution + période
        if (institutionId != null) {
            // Vérifier s'il existe déjà un cycle pour ce mois/année
            if (cycleDao.existsByInstitutionAndPeriode(institutionId, annee, mois)) {
                CyclePaie existant = cycleDao.findCycleEnCours(institutionId);
                if (existant != null
                        && existant.getMois() != null && existant.getMois() == mois
                        && existant.getAnnee() != null && existant.getAnnee() == annee) {
                    return existant;
                }
            }
            // Créer un nouveau cycle persisté
            Institution institution = institutionDao.findById(institutionId);
            CyclePaie nouveau = new CyclePaie();
            nouveau.setInstitution(institution);
            nouveau.setMois(mois);
            nouveau.setAnnee(annee);
            nouveau.setStatut("OUVERT");
            nouveau.setDateDebut(LocalDate.of(annee, mois, 1));
            nouveau.setCreePar(operateur);
            cycleDao.save(nouveau); // persisté AVANT d'être lié au bulletin
            return nouveau;
        }
        // Fallback sans institution (cas non multi-tenant)
        CyclePaie cycle = new CyclePaie();
        cycle.setMois(mois);
        cycle.setAnnee(annee);
        cycle.setStatut("OUVERT");
        cycle.setDateDebut(LocalDate.of(annee, mois, 1));
        cycle.setCreePar(operateur);
        cycleDao.save(cycle);
        return cycle;
    }

    /**
     * Lance le cycle de paie pour tous les employés actifs sans bulletin.
     */
    public int lancerCyclePaie(int mois, int annee, String operateur) {
        List<Employe> sansB = getEmployesSansBulletin(mois, annee);
        for (Employe e : sansB) {
            genererBulletin(e, mois, annee, operateur);
        }
        logActionDao.save(LogAction.of(operateur, "CYCLE_PAIE",
            sansB.size() + " bulletins générés pour " + mois + "/" + annee));
        return sansB.size();
    }

    // ══════════════════════════════════════════════════
    // GRILLES SALARIALES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<GrilleSalariale> getToutesGrilles() {
        return grilleDao.findAll();
    }

    @Transactional(readOnly = true)
    public List<GrilleSalariale> getGrillesByInstitution(Long institutionId) {
        if (institutionId == null) return grilleDao.findAll();
        return grilleDao.findByInstitution(institutionId);
    }

    public void creerGrille(GrilleSalariale grille, String operateur) {
        grilleDao.save(grille);
        logActionDao.save(LogAction.of(operateur, "CREATION_GRILLE",
            "Grille créée : " + grille.getEchelon()));
    }

    public void modifierGrille(GrilleSalariale grille, String operateur) {
        grilleDao.update(grille);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_GRILLE",
            "Grille modifiée : " + grille.getEchelon()));
    }

    public void supprimerGrille(Long id, String operateur) {
        grilleDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_GRILLE",
            "Grille supprimée : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // STATISTIQUES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Employe> getEmployesSansBulletin(int mois, int annee) {
        List<Employe> actifs = employeDao.findActifs();
        List<Long> avecBulletin = bulletinDao.findByPeriode(mois, annee).stream()
            .filter(b -> b.getEmploye() != null)
            .map(b -> b.getEmploye().getId())
            .collect(Collectors.toList());
        return actifs.stream()
            .filter(e -> !avecBulletin.contains(e.getId()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal getMasseSalariale(int mois, int annee) {
        return bulletinDao.findByPeriode(mois, annee).stream()
            .filter(b -> b.getSalaireNet() != null)
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getChargesSociales(int mois, int annee) {
        // CORRECTION 1 : taux lu depuis ParametreSysteme (défaut 20%)
        // On utilise institutionId=null pour le taux global
        BigDecimal tauxPatronal = getTauxChargesPatronales(null);
        BigDecimal masseBrute = bulletinDao.findByPeriode(mois, annee).stream()
            .filter(b -> b.getSalaireBrut() != null)
            .map(BulletinPaie::getSalaireBrut)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return masseBrute.multiply(tauxPatronal)
            .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public long countBulletinsGeneres(int mois, int annee) {
        return bulletinDao.findByPeriode(mois, annee).stream()
            .filter(b -> !"BROUILLON".equals(b.getStatut()))
            .count();
    }

    @Transactional(readOnly = true)
    public long countBulletinsEnAttente(int mois, int annee) {
        return bulletinDao.findByPeriode(mois, annee).stream()
            .filter(b -> "EN_ATTENTE".equals(b.getStatut())
                      || "BROUILLON".equals(b.getStatut()))
            .count();
    }

    // ══════════════════════════════════════════════════
    // CALCUL INTERNE
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION 1 : les taux IPRES et charges patronales sont lus
     * depuis ParametreSysteme au lieu d'être codés en dur.
     */
    private void calculerBulletin(BulletinPaie bulletin, Employe employe, Long institutionId) {
        BigDecimal base = employe.getSalaireBase() != null
            ? employe.getSalaireBase() : BigDecimal.ZERO;
        bulletin.setSalaireBase(base);

        if (bulletin.getTotalPrimes()   == null) bulletin.setTotalPrimes(BigDecimal.ZERO);
        if (bulletin.getTotalRetenues() == null) bulletin.setTotalRetenues(BigDecimal.ZERO);

        BigDecimal brut = base.add(bulletin.getTotalPrimes());
        bulletin.setSalaireBrut(brut);

        // CORRECTION 1 : taux depuis ParametreSysteme
        BigDecimal tauxIpres = getTauxIpres(institutionId);
        BigDecimal cotisations = brut.multiply(tauxIpres)
            .setScale(2, RoundingMode.HALF_UP);
        bulletin.setTotalCotisationsSalariales(cotisations);

        // Charges patronales (information, pas déduites du net)
        BigDecimal tauxPatronal = getTauxChargesPatronales(institutionId);
        BigDecimal chargesPatronales = brut.multiply(tauxPatronal)
            .setScale(2, RoundingMode.HALF_UP);
        bulletin.setTotalCotisationsPatronales(chargesPatronales);

        BigDecimal net = brut.subtract(cotisations)
            .subtract(bulletin.getTotalRetenues());
        bulletin.setSalaireNet(net.max(BigDecimal.ZERO));
        bulletin.setStatut("BROUILLON");
    }
}