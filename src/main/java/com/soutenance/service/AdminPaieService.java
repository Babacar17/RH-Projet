package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de paie — périmètre administrateur.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. Taux IPRES (7%) et charges patronales (20%) lus depuis ParametreSysteme.
 * 2. lancerCalcul() filtrait tous les employés sans tenir compte de l'institution
 *    → utilise maintenant employeDao.findActifsByInstitution(institutionId).
 * 3. CyclePaie.setDateCloture() inexistant → setDatePaiementEffective() (conservé).
 */
@Service
@Transactional
public class AdminPaieService {

    @Autowired private GrilleSalarialeDao  grilleDao;
    @Autowired private CyclePaieDao        cycleDao;
    @Autowired private BulletinPaieDao     bulletinDao;
    @Autowired private ElementSalaireDao   elementDao;
    @Autowired private EmployeDao          employeDao;
    @Autowired private InstitutionDao      institutionDao;
    @Autowired private LogActionDao        logActionDao;
    @Autowired private PresenceDao         presenceDao;
    @Autowired private EvenementDao        evenementDao;

    // CORRECTION 1 : injection du DAO paramètres
    @Autowired private ParametreDao        parametreDao;

    private static final String CLE_IPRES           = "TAUX_IPRES_SALARIE";
    private static final String CLE_CHARGES_PAT     = "TAUX_CHARGES_PATRONALES";
    private static final String CLE_TAUX_HORAIRE_SUP = "TAUX_HORAIRE_SUP";
    private static final String CLE_TAUX_IR           = "TAUX_IMPOT_REVENU";

    private BigDecimal getTauxIpres(Long institutionId) {
        try {
            ParametreSysteme p = parametreDao.findByCleAndInstitution(CLE_IPRES, institutionId);
            if (p != null && p.getValeur() != null)
                return new BigDecimal(p.getValeur())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        } catch (Exception ignored) {}
        return new BigDecimal("0.07");
    }

    private BigDecimal getTauxChargesPatronales(Long institutionId) {
        try {
            ParametreSysteme p = parametreDao.findByCleAndInstitution(CLE_CHARGES_PAT, institutionId);
            if (p != null && p.getValeur() != null)
                return new BigDecimal(p.getValeur())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        } catch (Exception ignored) {}
        return new BigDecimal("0.20");
    }

    /** Taux horaire des heures supplémentaires (FCFA/heure), appliqué aux heures pointées et validées. */
    private BigDecimal getTauxHoraireSup(Long institutionId) {
        try {
            ParametreSysteme p = parametreDao.findByCleAndInstitution(CLE_TAUX_HORAIRE_SUP, institutionId);
            if (p != null && p.getValeur() != null)
                return new BigDecimal(p.getValeur());
        } catch (Exception ignored) {}
        return new BigDecimal("2000");
    }

    /**
     * Taux d'impôt sur le revenu — APPROXIMATION SIMPLIFIÉE (taux fixe sur le revenu
     * imposable = brut - cotisations salariales), pas un vrai barème IRPP progressif
     * par tranches. Paramétrable par institution via la clé TAUX_IMPOT_REVENU.
     */
    private BigDecimal getTauxImpotRevenu(Long institutionId) {
        try {
            ParametreSysteme p = parametreDao.findByCleAndInstitution(CLE_TAUX_IR, institutionId);
            if (p != null && p.getValeur() != null)
                return new BigDecimal(p.getValeur())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        } catch (Exception ignored) {}
        return new BigDecimal("0.05");
    }

    // ══════════════════════════════════════════════════
    // GRILLES SALARIALES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<GrilleSalariale> getGrillesByInstitution(Long institutionId) {
        return grilleDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public GrilleSalariale getGrilleById(Long id) {
        return grilleDao.findById(id);
    }

    public void creerGrille(GrilleSalariale grille, Long institutionId, String operateur) {
        Institution institution = institutionDao.findById(institutionId);
        grille.setInstitution(institution);
        grille.setCreePar(operateur);
        grille.setStatut("ACTIVE");
        grilleDao.save(grille);
        logActionDao.save(LogAction.of(operateur, "CREATION_GRILLE",
            "Grille créée : " + grille.getLibelle()));
    }

    public void modifierGrille(GrilleSalariale grille, String operateur) {
        grilleDao.update(grille);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_GRILLE",
            "Grille modifiée : " + grille.getLibelle()));
    }

    public void archiverGrille(Long id, String operateur) {
        GrilleSalariale g = grilleDao.findById(id);
        if (g != null) {
            g.setStatut("ARCHIVEE");
            grilleDao.update(g);
            logActionDao.save(LogAction.of(operateur, "ARCHIVAGE_GRILLE",
                "Grille archivée : " + g.getLibelle()));
        }
    }

    public void supprimerGrille(Long id, String operateur) {
        long nbBulletins = bulletinDao.countByGrille(id);
        if (nbBulletins > 0) {
            throw new IllegalStateException(
                "Cette grille est référencée par " + nbBulletins
                + " bulletin(s). Archivez-la plutôt.");
        }
        grilleDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_GRILLE",
            "Grille supprimée : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // CYCLES DE PAIE
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<CyclePaie> getCyclesByInstitution(Long institutionId) {
        return cycleDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public List<CyclePaie> getCyclesFiltre(Long institutionId, String statut, Integer annee) {
        return cycleDao.findByFiltre(institutionId, statut, annee);
    }

    public void ouvrirCycle(Long institutionId, int annee, int mois, String operateur) {
        ouvrirCycle(institutionId, annee, mois, null, null, operateur);
    }

    /**
     * Calcule la prochaine période logique à proposer pour l'ouverture d'un cycle :
     * le mois suivant le dernier cycle de l'institution, ou le mois courant si aucun
     * cycle n'existe encore ou si l'institution a pris du retard (on rattrape le mois réel).
     * Résultat : {mois, année}.
     */
    @Transactional(readOnly = true)
    public int[] getProchainePeriodeSuggeree(Long institutionId) {
        LocalDate aujourdHui = LocalDate.now().withDayOfMonth(1);
        LocalDate suggestion = aujourdHui;

        CyclePaie dernier = (institutionId != null) ? cycleDao.findDernierCycle(institutionId) : null;
        if (dernier != null && dernier.getAnnee() != null && dernier.getMois() != null) {
            LocalDate apresDernier = LocalDate.of(dernier.getAnnee(), dernier.getMois(), 1).plusMonths(1);
            if (apresDernier.isAfter(suggestion)) suggestion = apresDernier;
        }

        while (institutionId != null
                && cycleDao.existsByInstitutionAndPeriode(institutionId, suggestion.getYear(), suggestion.getMonthValue())) {
            suggestion = suggestion.plusMonths(1);
        }
        return new int[] { suggestion.getMonthValue(), suggestion.getYear() };
    }

    public void ouvrirCycle(Long institutionId, int annee, int mois, LocalDate datePaiementPrevue, String operateur) {
        ouvrirCycle(institutionId, annee, mois, datePaiementPrevue, null, operateur);
    }

    public void ouvrirCycle(Long institutionId, int annee, int mois, LocalDate datePaiementPrevue,
                             BigDecimal budgetDisponible, String operateur) {
        if (cycleDao.existsByInstitutionAndPeriode(institutionId, annee, mois)) {
            throw new IllegalStateException(
                "Un cycle existe déjà pour " + mois + "/" + annee + ".");
        }
        Institution institution = institutionDao.findById(institutionId);
        CyclePaie cycle = new CyclePaie();
        cycle.setInstitution(institution);
        cycle.setAnnee(annee);
        cycle.setMois(mois);
        cycle.setStatut("OUVERT");
        cycle.setDateDebut(LocalDate.of(annee, mois, 1));
        cycle.setDatePaiementPrevue(datePaiementPrevue);
        cycle.setBudgetDisponible(budgetDisponible);
        cycle.setCreePar(operateur);
        cycleDao.save(cycle);
        logActionDao.save(LogAction.of(operateur, "OUVERTURE_CYCLE",
            "Cycle ouvert : " + mois + "/" + annee
            + (datePaiementPrevue != null ? " | versement prévu le " + datePaiementPrevue : "")
            + (budgetDisponible != null ? " | budget disponible : " + budgetDisponible.toPlainString() + " FCFA" : "")));
    }

    /** Masse salariale nette totale d'un cycle (tous bulletins non annulés), pour la comparer au budget. */
    @Transactional(readOnly = true)
    public BigDecimal getMasseSalarialeCycle(Long cycleId) {
        return bulletinDao.findByCycle(cycleId).stream()
            .filter(b -> !"ANNULE".equals(b.getStatut()) && b.getSalaireNet() != null)
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void lancerCalcul(Long cycleId, String operateur) {
        CyclePaie cycle = cycleDao.findById(cycleId);
        if (cycle == null) throw new IllegalArgumentException("Cycle introuvable.");
        if (!"OUVERT".equals(cycle.getStatut()))
            throw new IllegalStateException("Le cycle doit être OUVERT pour lancer le calcul.");

        cycle.setStatut("EN_CALCUL");
        cycleDao.update(cycle);

        Long institutionId = cycle.getInstitution() != null
            ? cycle.getInstitution().getId() : null;

        // CORRECTION 2 : filtrer par institution — avant on prenait tous les employés actifs
        List<Employe> employes = (institutionId != null)
            ? employeDao.findActifsByInstitution(institutionId)
            : employeDao.findActifs();

        // CORRECTION 1 : taux depuis ParametreSysteme
        BigDecimal tauxIpres      = getTauxIpres(institutionId);
        BigDecimal tauxPatronal   = getTauxChargesPatronales(institutionId);
        BigDecimal tauxHoraireSup = getTauxHoraireSup(institutionId);
        BigDecimal tauxIR         = getTauxImpotRevenu(institutionId);

        // Bornes de la période couverte par ce cycle, pour agréger présences et événements du mois.
        LocalDate periodeDebut = LocalDate.of(cycle.getAnnee(), cycle.getMois(), 1);
        LocalDate periodeFin   = periodeDebut.withDayOfMonth(periodeDebut.lengthOfMonth());

        for (Employe e : employes) {
            BulletinPaie b = new BulletinPaie();
            b.setEmploye(e);
            b.setCyclePaie(cycle);

            // CORRECTION 4 : salaire de base tiré de la grille salariale rattachée
            // à l'employé si elle existe, sinon repli sur le salaireBase manuel.
            GrilleSalariale grille = e.getGrilleSalariale();
            BigDecimal base = (grille != null && grille.getSalaireBase() != null)
                ? grille.getSalaireBase()
                : (e.getSalaireBase() != null ? e.getSalaireBase() : BigDecimal.ZERO);
            b.setSalaireBase(base);

            BigDecimal totalPrimes      = BigDecimal.ZERO;
            BigDecimal totalHeuresSupp  = BigDecimal.ZERO;
            BigDecimal totalAvantages   = BigDecimal.ZERO;
            BigDecimal autresRetenues   = BigDecimal.ZERO;
            BigDecimal cotSalarialesEl  = BigDecimal.ZERO;
            BigDecimal cotPatronalesEl  = BigDecimal.ZERO;

            // CORRECTION 4 : application des éléments de salaire (primes, indemnités,
            // retenues, cotisations) rattachés à la grille, clonés sur le bulletin.
            if (grille != null) {
                List<ElementSalaire> modeles = elementDao.findByGrille(grille.getId()).stream()
                    .filter(el -> "ACTIF".equals(el.getStatut()))
                    .toList();
                List<ElementSalaire> clones = new ArrayList<>();

                // 1ère passe : éléments fixes ou en % du salaire de base
                // (nécessaires pour obtenir un brut provisoire avant la 2e passe).
                for (ElementSalaire modele : modeles) {
                    if ("POURCENTAGE_SALAIRE_BRUT".equals(modele.getModeCalcul())) continue;
                    ElementSalaire clone = clonerElementSurBulletin(modele, b);
                    BigDecimal montant = clone.calculerMontant(base, null);
                    clones.add(clone);
                    switch (clone.getType()) {
                        case "PRIME", "INDEMNITE"   -> totalPrimes = totalPrimes.add(montant);
                        case "HEURES_SUP"           -> totalHeuresSupp = totalHeuresSupp.add(montant);
                        case "AVANTAGE_EN_NATURE"   -> totalAvantages = totalAvantages.add(montant);
                        case "COTISATION_SALARIALE" -> cotSalarialesEl = cotSalarialesEl.add(montant);
                        case "COTISATION_PATRONALE" -> cotPatronalesEl = cotPatronalesEl.add(montant);
                        case "RETENUE"              -> autresRetenues = autresRetenues.add(montant);
                        default -> { /* type inconnu, ignoré */ }
                    }
                }

                // 2e passe : éléments en % du salaire brut (brut provisoire = base + ce qui précède).
                BigDecimal brutProvisoire = base.add(totalPrimes).add(totalHeuresSupp).add(totalAvantages);
                for (ElementSalaire modele : modeles) {
                    if (!"POURCENTAGE_SALAIRE_BRUT".equals(modele.getModeCalcul())) continue;
                    ElementSalaire clone = clonerElementSurBulletin(modele, b);
                    BigDecimal montant = clone.calculerMontant(base, brutProvisoire);
                    clones.add(clone);
                    switch (clone.getType()) {
                        case "PRIME", "INDEMNITE"   -> totalPrimes = totalPrimes.add(montant);
                        case "HEURES_SUP"           -> totalHeuresSupp = totalHeuresSupp.add(montant);
                        case "AVANTAGE_EN_NATURE"   -> totalAvantages = totalAvantages.add(montant);
                        case "COTISATION_SALARIALE" -> cotSalarialesEl = cotSalarialesEl.add(montant);
                        case "COTISATION_PATRONALE" -> cotPatronalesEl = cotPatronalesEl.add(montant);
                        case "RETENUE"              -> autresRetenues = autresRetenues.add(montant);
                        default -> { /* type inconnu, ignoré */ }
                    }
                }

                b.getElements().addAll(clones);
            }

            // CORRECTION 5 : heures supplémentaires réellement pointées (module Présence),
            // uniquement celles validées par le RH/manager, valorisées au taux horaire paramétré.
            double heuresReelles = presenceDao.sumHeuresSupplementairesValidees(e.getId(), periodeDebut, periodeFin);
            if (heuresReelles > 0) {
                BigDecimal montantHeuresReelles = BigDecimal.valueOf(heuresReelles)
                    .multiply(tauxHoraireSup)
                    .setScale(2, RoundingMode.HALF_UP);
                totalHeuresSupp = totalHeuresSupp.add(montantHeuresReelles);
            }

            // CORRECTION 5 : indemnités des événements rémunérés (ex. séminaires) du mois,
            // pour tout événement où l'employé a le statut de participation PRESENT.
            BigDecimal indemnitesEvenements = evenementDao.sumIndemnitesParticipations(
                e.getId(), periodeDebut.atStartOfDay(), periodeFin.atTime(23, 59, 59));
            if (indemnitesEvenements.compareTo(BigDecimal.ZERO) > 0) {
                totalPrimes = totalPrimes.add(indemnitesEvenements);
            }

            b.setTotalPrimes(totalPrimes.setScale(2, RoundingMode.HALF_UP));
            b.setTotalHeuresSupp(totalHeuresSupp.setScale(2, RoundingMode.HALF_UP));
            b.setTotalAvantages(totalAvantages.setScale(2, RoundingMode.HALF_UP));
            b.setAutresRetenues(autresRetenues.setScale(2, RoundingMode.HALF_UP));

            BigDecimal cot = base.multiply(tauxIpres)
                .setScale(2, RoundingMode.HALF_UP)
                .add(cotSalarialesEl);
            b.setTotalCotisationsSalariales(cot);

            BigDecimal chargesPatronales = base.multiply(tauxPatronal)
                .setScale(2, RoundingMode.HALF_UP)
                .add(cotPatronalesEl);
            b.setTotalCotisationsPatronales(chargesPatronales);

            // CORRECTION 6 : impôt sur le revenu — approximation à taux fixe sur
            // (brut - cotisations salariales), voir getTauxImpotRevenu().
            BigDecimal brutApprox = base.add(totalPrimes).add(totalHeuresSupp).add(totalAvantages);
            BigDecimal revenuImposable = brutApprox.subtract(cot);
            if (revenuImposable.compareTo(BigDecimal.ZERO) < 0) revenuImposable = BigDecimal.ZERO;
            BigDecimal impot = revenuImposable.multiply(tauxIR).setScale(2, RoundingMode.HALF_UP);
            b.setImpotRevenu(impot);

            // @PrePersist appelle calculerTotaux() — salaireBrut et salaireNet calculés auto
            b.setStatut("CALCULE");
            b.setCreePar(operateur);
            bulletinDao.save(b);
        }
        cycle.setStatut("CALCULE");
        cycleDao.update(cycle);
        logActionDao.save(LogAction.of(operateur, "CALCUL_CYCLE",
            employes.size() + " bulletins calculés pour le cycle "
            + cycle.getMois() + "/" + cycle.getAnnee()
            + " | Taux IPRES=" + tauxIpres.multiply(BigDecimal.valueOf(100)).toPlainString() + "%"
            + " | Charges patronales=" + tauxPatronal.multiply(BigDecimal.valueOf(100)).toPlainString() + "%"));
    }

    /** Clone un élément de salaire « modèle » (rattaché à une grille) en ligne propre au bulletin. */
    private ElementSalaire clonerElementSurBulletin(ElementSalaire modele, BulletinPaie bulletin) {
        ElementSalaire clone = new ElementSalaire();
        clone.setLibelle(modele.getLibelle());
        clone.setType(modele.getType());
        clone.setModeCalcul(modele.getModeCalcul());
        clone.setValeurFixe(modele.getValeurFixe());
        clone.setTauxPourcentage(modele.getTauxPourcentage());
        clone.setFormule(modele.getFormule());
        clone.setEstImposable(modele.isEstImposable());
        clone.setEstCotisable(modele.isEstCotisable());
        clone.setStatut("ACTIF");
        clone.setBulletinPaie(bulletin);
        return clone;
    }

    /**
     * Ajoute un élément de salaire ponctuel (prime exceptionnelle, retenue isolée…)
     * directement sur UN bulletin, sans passer par une grille salariale.
     * Recalcule aussitôt les totaux du bulletin concerné.
     */
    public void ajouterElementPonctuel(Long bulletinId, ElementSalaire saisie, String operateur) {
        BulletinPaie b = bulletinDao.findById(bulletinId);
        if (b == null) throw new IllegalArgumentException("Bulletin introuvable.");
        if ("ANNULE".equals(b.getStatut()))
            throw new IllegalStateException("Ce bulletin est annulé.");
        if (b.getCyclePaie() != null && "CLOTURE".equals(b.getCyclePaie().getStatut()))
            throw new IllegalStateException("Le cycle de ce bulletin est clôturé.");

        ElementSalaire el = new ElementSalaire();
        el.setLibelle(saisie.getLibelle());
        el.setType(saisie.getType());
        el.setModeCalcul(saisie.getModeCalcul());
        el.setValeurFixe(saisie.getValeurFixe());
        el.setTauxPourcentage(saisie.getTauxPourcentage());
        el.setFormule(saisie.getFormule());
        el.setEstImposable(saisie.isEstImposable());
        el.setEstCotisable(saisie.isEstCotisable());
        el.setStatut("ACTIF");
        el.setBulletinPaie(b);

        BigDecimal montant = el.calculerMontant(b.getSalaireBase(), b.getSalaireBrut());
        switch (el.getType()) {
            case "PRIME", "INDEMNITE"   -> b.setTotalPrimes(nz(b.getTotalPrimes()).add(montant));
            case "HEURES_SUP"           -> b.setTotalHeuresSupp(nz(b.getTotalHeuresSupp()).add(montant));
            case "AVANTAGE_EN_NATURE"   -> b.setTotalAvantages(nz(b.getTotalAvantages()).add(montant));
            case "COTISATION_SALARIALE" -> b.setTotalCotisationsSalariales(nz(b.getTotalCotisationsSalariales()).add(montant));
            case "COTISATION_PATRONALE" -> b.setTotalCotisationsPatronales(nz(b.getTotalCotisationsPatronales()).add(montant));
            case "RETENUE"              -> b.setAutresRetenues(nz(b.getAutresRetenues()).add(montant));
            default -> { /* type inconnu, ignoré */ }
        }

        elementDao.save(el);
        bulletinDao.update(b); // @PreUpdate recalcule salaireBrut / salaireNet
        logActionDao.save(LogAction.of(operateur, "AJOUT_ELEMENT_PONCTUEL",
            "Élément « " + el.getLibelle() + " » (" + montant.toPlainString()
            + " FCFA) ajouté au bulletin #" + bulletinId));
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    public void validerCycle(Long cycleId, String operateur) {
        CyclePaie cycle = cycleDao.findById(cycleId);
        if (cycle == null) throw new IllegalArgumentException("Cycle introuvable.");
        if (!"CALCULE".equals(cycle.getStatut()))
            throw new IllegalStateException("Le cycle doit être CALCULÉ pour être validé.");

        List<BulletinPaie> bulletins = bulletinDao.findByCycle(cycleId);
        for (BulletinPaie b : bulletins) {
            if (!"ANNULE".equals(b.getStatut())) {
                b.setStatut("VALIDE");
                b.setValidePar(operateur);
                bulletinDao.update(b);
            }
        }
        cycle.setStatut("VALIDE");
        cycle.setValidePar(operateur);
        cycleDao.update(cycle);
        logActionDao.save(LogAction.of(operateur, "VALIDATION_CYCLE",
            "Cycle validé : " + cycle.getMois() + "/" + cycle.getAnnee()));
    }

    /**
     * Marque un cycle validé comme effectivement payé : bascule tous ses bulletins
     * VALIDE vers PAYE et enregistre la date de versement effective.
     */
    public void marquerPaye(Long cycleId, String operateur) {
        CyclePaie cycle = cycleDao.findById(cycleId);
        if (cycle == null) throw new IllegalArgumentException("Cycle introuvable.");
        if (!"VALIDE".equals(cycle.getStatut()))
            throw new IllegalStateException("Le cycle doit être VALIDÉ avant d'être marqué comme payé.");

        List<BulletinPaie> bulletins = bulletinDao.findByCycle(cycleId);
        for (BulletinPaie b : bulletins) {
            if ("VALIDE".equals(b.getStatut())) {
                b.setStatut("PAYE");
                b.setDatePaiement(LocalDate.now());
                bulletinDao.update(b);
            }
        }
        cycle.setStatut("PAYE");
        cycle.setDatePaiementEffective(LocalDate.now());
        cycleDao.update(cycle);
        logActionDao.save(LogAction.of(operateur, "PAIEMENT_CYCLE",
            "Cycle marqué payé : " + cycle.getMois() + "/" + cycle.getAnnee()
            + " (" + bulletins.size() + " bulletins)"));
    }

    /**
     * Paiement par lot : marque une sélection précise de bulletins comme payés,
     * sans exiger que tous les bulletins du cycle le soient en même temps —
     * utile en cas de trésorerie insuffisante (paiement échelonné/par priorité,
     * choisi par le RH). Si plus aucun bulletin VALIDE ne reste dans le cycle
     * après ce lot, le cycle bascule automatiquement à PAYE.
     */
    public void marquerBulletinsPayes(List<Long> bulletinIds, String operateur) {
        if (bulletinIds == null || bulletinIds.isEmpty())
            throw new IllegalArgumentException("Aucun bulletin sélectionné.");

        CyclePaie cycleTouche = null;
        int nbPayes = 0;
        for (Long id : bulletinIds) {
            BulletinPaie b = bulletinDao.findById(id);
            if (b == null || !"VALIDE".equals(b.getStatut())) continue;
            if (b.getCyclePaie() == null || !"VALIDE".equals(b.getCyclePaie().getStatut()))
                throw new IllegalStateException("Le cycle du bulletin de "
                    + (b.getEmploye() != null ? b.getEmploye().getNomComplet() : id)
                    + " doit être validé avant paiement.");
            b.setStatut("PAYE");
            b.setDatePaiement(LocalDate.now());
            bulletinDao.update(b);
            cycleTouche = b.getCyclePaie();
            nbPayes++;
        }
        if (nbPayes == 0) return;

        if (cycleTouche != null) {
            boolean resteDesValides = bulletinDao.findByCycle(cycleTouche.getId()).stream()
                .anyMatch(b -> "VALIDE".equals(b.getStatut()));
            if (!resteDesValides) {
                cycleTouche.setStatut("PAYE");
                cycleTouche.setDatePaiementEffective(LocalDate.now());
                cycleDao.update(cycleTouche);
            }
        }

        logActionDao.save(LogAction.of(operateur, "PAIEMENT_LOT",
            nbPayes + " bulletin(s) marqué(s) payé(s) (paiement par lot)."));
    }

    public void cloturerCycle(Long cycleId, String operateur) {
        CyclePaie cycle = cycleDao.findById(cycleId);
        if (cycle == null) throw new IllegalArgumentException("Cycle introuvable.");
        if (!"PAYE".equals(cycle.getStatut()))
            throw new IllegalStateException("Le cycle doit être marqué PAYÉ avant d'être clôturé.");
        cycle.setStatut("CLOTURE");
        cycle.setCloturePar(operateur);
        cycleDao.update(cycle);
        logActionDao.save(LogAction.of(operateur, "CLOTURE_CYCLE",
            "Cycle clôturé : " + cycle.getMois() + "/" + cycle.getAnnee()));
    }

    // ══════════════════════════════════════════════════
    // BULLETINS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<BulletinPaie> getBulletinsByCycle(Long cycleId) {
        return bulletinDao.findByCycle(cycleId);
    }

    @Transactional(readOnly = true)
    public BulletinPaie getBulletinById(Long id) {
        return bulletinDao.findById(id);
    }

    /** Bulletins validés mais toujours pas payés alors que la date de versement prévue est dépassée. */
    @Transactional(readOnly = true)
    public List<BulletinPaie> getArrieresByInstitution(Long institutionId) {
        return bulletinDao.findArrieresByInstitution(institutionId);
    }

    public void validerBulletin(Long bulletinId, String operateur) {
        BulletinPaie b = bulletinDao.findById(bulletinId);
        if (b != null) {
            b.setStatut("VALIDE");
            b.setValidePar(operateur);
            bulletinDao.update(b);
            logActionDao.save(LogAction.of(operateur, "VALIDATION_BULLETIN",
                "Bulletin validé : " + (b.getEmploye() != null
                    ? b.getEmploye().getNomComplet() : bulletinId)));
        }
    }

    public void annulerBulletin(Long bulletinId, String operateur) {
        BulletinPaie b = bulletinDao.findById(bulletinId);
        if (b != null) {
            if (b.getCyclePaie() != null && "CLOTURE".equals(b.getCyclePaie().getStatut())) {
                throw new IllegalStateException(
                    "Impossible d'annuler ce bulletin : le cycle de paie est déjà clôturé.");
            }
            b.setStatut("ANNULE");
            bulletinDao.update(b);
            logActionDao.save(LogAction.of(operateur, "ANNULATION_BULLETIN",
                "Bulletin annulé : " + (b.getEmploye() != null
                    ? b.getEmploye().getNomComplet() : bulletinId)));
        }
    }

    // ══════════════════════════════════════════════════
    // ÉLÉMENTS DE SALAIRE
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ElementSalaire> getElementsByInstitution(Long institutionId) {
        return elementDao.findByInstitution(institutionId);
    }

    public void creerElement(ElementSalaire element, String operateur) {
        elementDao.save(element);
        logActionDao.save(LogAction.of(operateur, "CREATION_ELEMENT_SALAIRE",
            "Élément créé : " + element.getLibelle()));
    }

    public void modifierElement(ElementSalaire element, String operateur) {
        elementDao.update(element);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_ELEMENT_SALAIRE",
            "Élément modifié : " + element.getLibelle()));
    }

    public void supprimerElement(Long id, String operateur) {
        elementDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_ELEMENT_SALAIRE",
            "Élément supprimé : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // STATISTIQUES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public BigDecimal getMasseSalarialeTotale(Long institutionId) {
        return bulletinDao.findByInstitution(institutionId).stream()
            .filter(b -> "VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut()))
            .filter(b -> b.getSalaireNet() != null)
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMasseSalarialeMoyenne(Long institutionId) {
        List<BulletinPaie> valides = bulletinDao.findByInstitution(institutionId).stream()
            .filter(b -> ("VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut()))
                && b.getSalaireNet() != null)
            .toList();
        if (valides.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = valides.stream().map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(valides.size()), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public long countBulletinsValides(Long institutionId) {
        return bulletinDao.findByInstitution(institutionId).stream()
            .filter(b -> "VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut()))
            .count();
    }
}