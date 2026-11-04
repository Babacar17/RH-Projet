package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.AdminPaieService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bean de gestion de la paie — périmètre administrateur.
 *
 * Couvre :
 *  - Grilles salariales (CRUD, activation/archivage)
 *  - Cycles de paie (ouverture, calcul, validation, clôture)
 *  - Bulletins de paie (supervision, correction manuelle, export CSV)
 *  - Éléments de salaire (primes, retenues, cotisations)
 *  - Statistiques masse salariale
 *
 * Scope "view" : les listes sont rechargées à chaque navigation
 * vers la page, sans risque de données périmées entre deux onglets.
 */
@Component("adminPaieBean")
@Scope("view")
public class AdminPaieBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPaieBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminPaieService paieService;
    @Autowired private SessionBean      sessionBean;

    // ── Grilles salariales ─────────────────────────────────────────────────
    private List<GrilleSalariale> grilles       = new ArrayList<>();
    private GrilleSalariale       grille        = new GrilleSalariale();
    private boolean               modeEditionGrille = false;

    // ── Cycles de paie ─────────────────────────────────────────────────────
    private List<CyclePaie>  cycles           = new ArrayList<>();
    private CyclePaie        cycle            = new CyclePaie();
    private CyclePaie        cycleSelectionne;
    private boolean          modeEditionCycle = false;

    /** Mois et année pour la création d'un nouveau cycle */
    private Integer nouveauCycleMois;
    private Integer nouveauCycleAnnee;
    private java.time.LocalDate nouveauCycleDatePaiementPrevue;
    private BigDecimal nouveauCycleBudget;

    // ── Bulletins de paie ──────────────────────────────────────────────────
    private List<BulletinPaie> bulletins           = new ArrayList<>();
    private List<BulletinPaie> bulletinsSelectionnes = new ArrayList<>();
    private BulletinPaie       bulletinSelectionne;
    private String             filtreStatutBulletin;

    // ── Arriérés (bulletins validés, versement prévu dépassé) ──────────────
    private List<BulletinPaie> arrieres = new ArrayList<>();

    // ── Éléments de salaire ────────────────────────────────────────────────
    private List<ElementSalaire> elements        = new ArrayList<>();
    private ElementSalaire       element         = new ElementSalaire();
    private boolean              modeEditionElem = false;
    private Long                 elementGrilleIdSelectionne;

    // ── Élément ponctuel sur un bulletin précis ─────────────────────────────
    private ElementSalaire elementBulletin  = new ElementSalaire();
    private BulletinPaie   bulletinPourElement;

    // ── Stats masse salariale ──────────────────────────────────────────────
    private BigDecimal masseSalarialeTotal  = BigDecimal.ZERO;
    private BigDecimal masseSalarialeMoyenne = BigDecimal.ZERO;
    private long       nombreBulletinsValides;
    private long       nombreCyclesOuverts;

    // ── Filtres ────────────────────────────────────────────────────────────
    private String filtreStatutCycle;
    private Integer filtreAnnee;

    // ── Consultation directe des bulletins par période ──────────────────────
    private Integer anneeConsultation;
    private Integer moisConsultation;

    @PostConstruct
    public void init() {
        chargerTout();
    }

    private void chargerTout() {
        try {
            Long instId = sessionBean.getInstitutionId();
            grilles   = paieService.getGrillesByInstitution(instId);
            cycles    = paieService.getCyclesByInstitution(instId);
            elements  = paieService.getElementsByInstitution(instId);
            arrieres  = paieService.getArrieresByInstitution(instId);
            calculerStats();
            // Pré-remplit le formulaire d'ouverture avec la prochaine période logique,
            // sans écraser une saisie en cours de l'utilisateur.
            if (nouveauCycleMois == null && nouveauCycleAnnee == null) {
                int[] suggestion = paieService.getProchainePeriodeSuggeree(instId);
                nouveauCycleMois  = suggestion[0];
                nouveauCycleAnnee = suggestion[1];
            }
            // Pré-sélectionne la période du cycle le plus récent pour le raccourci "Consulter les bulletins".
            if (anneeConsultation == null && moisConsultation == null && !cycles.isEmpty()) {
                CyclePaie dernier = cycles.stream()
                    .max(Comparator.comparing(CyclePaie::getAnnee).thenComparing(CyclePaie::getMois))
                    .orElse(null);
                if (dernier != null) {
                    anneeConsultation = dernier.getAnnee();
                    moisConsultation  = dernier.getMois();
                }
            }
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            grilles  = new ArrayList<>();
            cycles   = new ArrayList<>();
            elements = new ArrayList<>();
            arrieres = new ArrayList<>();
        }
    }

    private void calculerStats() {
        try {
            Long instId = sessionBean.getInstitutionId();
            masseSalarialeTotal   = paieService.getMasseSalarialeTotale(instId);
            masseSalarialeMoyenne = paieService.getMasseSalarialeMoyenne(instId);
            nombreBulletinsValides = paieService.countBulletinsValides(instId);
            nombreCyclesOuverts   = cycles.stream()
                .filter(c -> "OUVERT".equals(c.getStatut()) || "EN_CALCUL".equals(c.getStatut()))
                .count();
        } catch (Exception ignored) {}
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    // ══════════════════════════════════════════════════════════
    // GRILLES SALARIALES
    // ══════════════════════════════════════════════════════════

    public void preparerNouvelleGrille() {
        grille = new GrilleSalariale();
        modeEditionGrille = false;
    }

    public void preparerEditionGrille(GrilleSalariale g) {
        this.grille = paieService.getGrilleById(g.getId());
        if (this.grille == null) this.grille = g;
        modeEditionGrille = true;
    }

    public String sauvegarderGrille() {
        try {
            Long instId = sessionBean.getInstitutionId();
            if (instId == null) {
                addError("Aucune institution sélectionnée.");
                return null;
            }
            if (grille.getSalaireBase() == null || grille.getSalaireBase().compareTo(BigDecimal.ZERO) <= 0) {
                addError("Le salaire de base doit être supérieur à zéro.");
                return null;
            }
            if (grille.getDateEffet() == null) {
                addError("La date d'effet est obligatoire.");
                return null;
            }
            if (modeEditionGrille) {
                paieService.modifierGrille(grille, getOp());
                addInfo("Grille salariale modifiée : " + grille.getLibelle());
            } else {
                grille.setCreePar(getOp());
                paieService.creerGrille(grille, instId, getOp());
                addInfo("Grille salariale créée : " + grille.getLibelle());
            }
            chargerTout();
            grille = new GrilleSalariale();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void archiverGrille(Long id) {
        try {
            paieService.archiverGrille(id, getOp());
            chargerTout();
            addInfo("Grille archivée.");
        } catch (Exception ex) {
            addError("Erreur archivage : " + ex.getMessage());
        }
    }

    public void supprimerGrille(Long id) {
        try {
            paieService.supprimerGrille(id, getOp());
            chargerTout();
            addInfo("Grille supprimée.");
        } catch (Exception ex) {
            addError("Impossible de supprimer cette grille (des bulletins y font peut-être référence).");
        }
    }

    // ══════════════════════════════════════════════════════════
    // CYCLES DE PAIE
    // ══════════════════════════════════════════════════════════

    public void ouvrirNouveauCycle() {
        try {
            Long instId = sessionBean.getInstitutionId();
            if (instId == null)            { addError("Aucune institution."); return; }
            if (nouveauCycleMois == null)  { addError("Le mois est obligatoire."); return; }
            if (nouveauCycleAnnee == null) { addError("L'année est obligatoire."); return; }
            if (nouveauCycleMois < 1 || nouveauCycleMois > 12) {
                addError("Mois invalide (1-12).");
                return;
            }
            if (nouveauCycleDatePaiementPrevue != null
                    && nouveauCycleDatePaiementPrevue.isBefore(java.time.LocalDate.now())) {
                addError("La date de versement ne peut pas être antérieure à aujourd'hui.");
                return;
            }
            paieService.ouvrirCycle(instId, nouveauCycleAnnee, nouveauCycleMois,
                nouveauCycleDatePaiementPrevue, nouveauCycleBudget, getOp());
            chargerTout();
            addInfo("Cycle " + nouveauCycleMois + "/" + nouveauCycleAnnee + " ouvert.");
            nouveauCycleMois  = null;
            nouveauCycleAnnee = null;
            nouveauCycleDatePaiementPrevue = null;
            nouveauCycleBudget = null;
        } catch (IllegalStateException ex) {
            addError(ex.getMessage()); // ex: "Cycle déjà existant"
        } catch (Exception ex) {
            addError("Erreur ouverture cycle : " + ex.getMessage());
        }
    }

    public void lancerCalculCycle(Long cycleId) {
        try {
            paieService.lancerCalcul(cycleId, getOp());
            chargerTout();
            addInfo("Calcul lancé pour le cycle.");

            CyclePaie cycle = cycles.stream().filter(c -> cycleId.equals(c.getId())).findFirst().orElse(null);
            if (cycle != null && cycle.getBudgetDisponible() != null) {
                BigDecimal masse  = paieService.getMasseSalarialeCycle(cycleId);
                BigDecimal budget = cycle.getBudgetDisponible();
                if (masse.compareTo(budget) > 0) {
                    BigDecimal deficit = masse.subtract(budget);
                    addError("Attention : la masse salariale (" + masse.toPlainString()
                        + " FCFA) dépasse le budget disponible (" + budget.toPlainString()
                        + " FCFA) de " + deficit.toPlainString()
                        + " FCFA. Un paiement partiel bulletin par bulletin sera nécessaire.");
                }
            }
        } catch (Exception ex) {
            addError("Erreur calcul : " + ex.getMessage());
        }
    }

    public void validerCycle(Long cycleId) {
        try {
            paieService.validerCycle(cycleId, getOp());
            chargerTout();
            addInfo("Cycle validé. Les bulletins sont prêts pour paiement.");
        } catch (Exception ex) {
            addError("Erreur validation : " + ex.getMessage());
        }
    }

    public void marquerPaye(Long cycleId) {
        try {
            paieService.marquerPaye(cycleId, getOp());
            chargerTout();
            addInfo("Cycle marqué comme payé.");
        } catch (Exception ex) {
            addError("Erreur paiement : " + ex.getMessage());
        }
    }

    /** Paie un seul bulletin (ex. depuis la liste des arriérés). */
    public void marquerPayeUnique(Long bulletinId) {
        try {
            paieService.marquerBulletinsPayes(List.of(bulletinId), getOp());
            addInfo("Bulletin marqué payé.");
            if (cycleSelectionne != null) {
                bulletins = paieService.getBulletinsByCycle(cycleSelectionne.getId());
            }
            chargerTout();
        } catch (Exception ex) {
            addError("Erreur paiement : " + ex.getMessage());
        }
    }

    /** Paiement par lot : paie uniquement les bulletins cochés par le RH (paiement échelonné/partiel). */
    public void marquerPayeSelection() {
        try {
            if (bulletinsSelectionnes == null || bulletinsSelectionnes.isEmpty()) {
                addError("Sélectionnez au moins un bulletin à payer.");
                return;
            }
            List<Long> ids = bulletinsSelectionnes.stream().map(BulletinPaie::getId).toList();
            paieService.marquerBulletinsPayes(ids, getOp());
            addInfo(ids.size() + " bulletin(s) marqué(s) payé(s).");
            if (cycleSelectionne != null) {
                bulletins = paieService.getBulletinsByCycle(cycleSelectionne.getId());
            }
            bulletinsSelectionnes = new ArrayList<>();
            chargerTout();
        } catch (Exception ex) {
            addError("Erreur paiement par lot : " + ex.getMessage());
        }
    }

    public void cloturerCycle(Long cycleId) {
        try {
            paieService.cloturerCycle(cycleId, getOp());
            chargerTout();
            addInfo("Cycle clôturé.");
        } catch (Exception ex) {
            addError("Erreur clôture : " + ex.getMessage());
        }
    }

    public void selectionnerCycle(CyclePaie c) {
        this.cycleSelectionne = c;
        try {
            this.bulletins = paieService.getBulletinsByCycle(c.getId());
        } catch (Exception ex) {
            this.bulletins = new ArrayList<>();
        }
    }

    /** Raccourci : va directement aux bulletins du cycle correspondant à l'année/mois choisis. */
    public void consulterBulletinsParPeriode() {
        if (anneeConsultation == null || moisConsultation == null) {
            addError("Choisissez une année et un mois.");
            return;
        }
        CyclePaie c = cycles.stream()
            .filter(cy -> anneeConsultation.equals(cy.getAnnee()) && moisConsultation.equals(cy.getMois()))
            .findFirst().orElse(null);
        if (c == null) {
            this.cycleSelectionne = null;
            this.bulletins = new ArrayList<>();
            addError("Aucun cycle de paie n'existe pour cette période.");
            return;
        }
        selectionnerCycle(c);
    }

    /** Années pour lesquelles au moins un cycle existe, les plus récentes d'abord. */
    public List<Integer> getAnneesDisponibles() {
        if (cycles == null) return new ArrayList<>();
        return cycles.stream().map(CyclePaie::getAnnee).distinct()
            .sorted(Comparator.reverseOrder()).toList();
    }

    public void filtrerCycles() {
        try {
            Long instId = sessionBean.getInstitutionId();
            cycles = paieService.getCyclesFiltre(instId, filtreStatutCycle, filtreAnnee);
        } catch (Exception ex) {
            addError("Erreur filtre : " + ex.getMessage());
        }
    }

    public void reinitialiserFiltreCycles() {
        filtreStatutCycle = null;
        filtreAnnee       = null;
        chargerTout();
    }

    // ══════════════════════════════════════════════════════════
    // BULLETINS DE PAIE
    // ══════════════════════════════════════════════════════════

    public void selectionnerBulletin(BulletinPaie b) {
        this.bulletinSelectionne = paieService.getBulletinById(b.getId());
        if (this.bulletinSelectionne == null) this.bulletinSelectionne = b;
    }

    public void validerBulletin(Long bulletinId) {
        try {
            paieService.validerBulletin(bulletinId, getOp());
            if (cycleSelectionne != null)
                bulletins = paieService.getBulletinsByCycle(cycleSelectionne.getId());
            addInfo("Bulletin validé.");
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
    }

    public void annulerBulletin(Long bulletinId) {
        try {
            paieService.annulerBulletin(bulletinId, getOp());
            if (cycleSelectionne != null)
                bulletins = paieService.getBulletinsByCycle(cycleSelectionne.getId());
            addInfo("Bulletin annulé.");
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
    }

    public void filtrerBulletins() {
        if (cycleSelectionne == null) return;
        try {
            List<BulletinPaie> tous = paieService.getBulletinsByCycle(cycleSelectionne.getId());
            if (filtreStatutBulletin == null || filtreStatutBulletin.isBlank()) {
                bulletins = tous;
            } else {
                bulletins = tous.stream()
                    .filter(b -> filtreStatutBulletin.equals(b.getStatut()))
                    .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception ex) {
            addError("Erreur filtre bulletins : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // ÉLÉMENTS DE SALAIRE
    // ══════════════════════════════════════════════════════════

    public void preparerNouvelElement() {
        element = new ElementSalaire();
        modeEditionElem = false;
        elementGrilleIdSelectionne = null;
    }

    public void preparerEditionElement(ElementSalaire es) {
        this.element = es;
        modeEditionElem = true;
        elementGrilleIdSelectionne = es.getGrilleSalariale() != null ? es.getGrilleSalariale().getId() : null;
    }

    public String sauvegarderElement() {
        try {
            if (element.getLibelle() == null || element.getLibelle().isBlank()) {
                addError("Le libellé est obligatoire.");
                return null;
            }
            if (element.getType() == null || element.getType().isBlank()) {
                addError("Le type est obligatoire.");
                return null;
            }
            element.setGrilleSalariale(elementGrilleIdSelectionne != null
                ? paieService.getGrilleById(elementGrilleIdSelectionne) : null);
            if (modeEditionElem) {
                paieService.modifierElement(element, getOp());
                addInfo("Élément modifié : " + element.getLibelle());
            } else {
                paieService.creerElement(element, getOp());
                addInfo("Élément créé : " + element.getLibelle());
            }
            chargerTout();
            element = new ElementSalaire();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void supprimerElement(Long id) {
        try {
            paieService.supprimerElement(id, getOp());
            chargerTout();
            addInfo("Élément supprimé.");
        } catch (Exception ex) {
            addError("Impossible de supprimer cet élément.");
        }
    }

    // ══════════════════════════════════════════════════════════
    // ÉLÉMENT PONCTUEL SUR UN BULLETIN (prime exceptionnelle, etc.)
    // ══════════════════════════════════════════════════════════

    public void preparerNouvelElementBulletin(BulletinPaie b) {
        this.bulletinPourElement = b;
        this.elementBulletin = new ElementSalaire();
    }

    public String sauvegarderElementBulletin() {
        try {
            if (bulletinPourElement == null) {
                addError("Aucun bulletin sélectionné.");
                return null;
            }
            if (elementBulletin.getLibelle() == null || elementBulletin.getLibelle().isBlank()) {
                addError("Le libellé est obligatoire.");
                return null;
            }
            if (elementBulletin.getType() == null || elementBulletin.getType().isBlank()) {
                addError("Le type est obligatoire.");
                return null;
            }
            paieService.ajouterElementPonctuel(bulletinPourElement.getId(), elementBulletin, getOp());
            addInfo("Élément ajouté au bulletin de " + bulletinPourElement.getEmploye().getNomComplet() + ".");
            if (cycleSelectionne != null) {
                bulletins = paieService.getBulletinsByCycle(cycleSelectionne.getId());
            }
            elementBulletin = new ElementSalaire();
            bulletinPourElement = null;
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT CSV BULLETINS
    // ══════════════════════════════════════════════════════════

    /**
     * Exporte les bulletins du cycle sélectionné en CSV.
     * Un cycle doit être sélectionné au préalable.
     */
    public void exporterBulletinsCSV() {
        if (cycleSelectionne == null) {
            addError("Sélectionnez un cycle avant d'exporter.");
            return;
        }
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();

            String filename = "bulletins_paie_"
                + cycleSelectionne.getMois() + "_"
                + cycleSelectionne.getAnnee() + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + ".csv";

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('\uFEFF'); // BOM UTF-8 pour Excel
            writer.println("Matricule;Nom;Prénom;Salaire base;Total primes;Total retenues;Salaire net;Statut;Date paiement");

            for (BulletinPaie b : paieService.getBulletinsByCycle(cycleSelectionne.getId())) {
                Employe emp = b.getEmploye();
                writer.println(
                    safe(emp != null ? emp.getMatricule() : "")     + ";" +
                    safe(emp != null ? emp.getNom()       : "")     + ";" +
                    safe(emp != null ? emp.getPrenom()    : "")     + ";" +
                    (b.getSalaireBase()   != null ? b.getSalaireBase()   : "") + ";" +
                    (b.getTotalPrimes()   != null ? b.getTotalPrimes()   : "") + ";" +
                    (b.getTotalRetenues() != null ? b.getTotalRetenues() : "") + ";" +
                    (b.getSalaireNet()    != null ? b.getSalaireNet()    : "") + ";" +
                    safe(b.getStatut()) + ";" +
                    (b.getDatePaiement() != null ? b.getDatePaiement().toString() : "")
                );
            }
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS UI
    // ══════════════════════════════════════════════════════════

    public String getLabelStatutCycle(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "OUVERT"    -> "Ouvert";
            case "EN_CALCUL" -> "En calcul";
            case "CALCULE"   -> "Calculé";
            case "VALIDE"    -> "Validé";
            case "PAYE"      -> "Payé";
            case "CLOTURE"   -> "Clôturé";
            default          -> statut;
        };
    }

    public String getBadgeStatutCycle(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "OUVERT"    -> "badge-info";
            case "EN_CALCUL" -> "badge-warning";
            case "CALCULE"   -> "badge-primary";
            case "VALIDE"    -> "badge-success";
            case "PAYE"      -> "badge-success";
            case "CLOTURE"   -> "badge-secondary";
            default          -> "badge-secondary";
        };
    }

    public String getBadgeStatutBulletin(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "BROUILLON" -> "badge-secondary";
            case "CALCULE"   -> "badge-primary";
            case "VALIDE"    -> "badge-success";
            case "PAYE"      -> "badge-success";
            case "ANNULE"    -> "badge-danger";
            default          -> "badge-secondary";
        };
    }

    /**
     * Déficit budgétaire du cycle (masse salariale - budget disponible), ou BigDecimal.ZERO
     * si aucun budget n'a été fixé ou si le calcul n'a pas encore produit de bulletins.
     */
    public BigDecimal getDeficitBudgetaire(CyclePaie c) {
        if (c == null || c.getBudgetDisponible() == null || c.getNombreBulletins() == 0) return BigDecimal.ZERO;
        try {
            BigDecimal masse = paieService.getMasseSalarialeCycle(c.getId());
            BigDecimal deficit = masse.subtract(c.getBudgetDisponible());
            return deficit.compareTo(BigDecimal.ZERO) > 0 ? deficit : BigDecimal.ZERO;
        } catch (Exception ex) { return BigDecimal.ZERO; }
    }

    public boolean isEnDeficit(CyclePaie c) {
        return getDeficitBudgetaire(c).compareTo(BigDecimal.ZERO) > 0;
    }

    /** True si le cycle peut passer à l'état suivant (bouton activé). */
    public boolean peutCalculer(CyclePaie c) {
        return c != null && "OUVERT".equals(c.getStatut());
    }

    public boolean peutValider(CyclePaie c) {
        return c != null && "CALCULE".equals(c.getStatut());
    }

    public boolean peutMarquerPaye(CyclePaie c) {
        return c != null && "VALIDE".equals(c.getStatut());
    }

    public boolean peutCloturer(CyclePaie c) {
        return c != null && "PAYE".equals(c.getStatut());
    }

    /** Total net du cycle sélectionné (somme des bulletins VALIDE/PAYE). */
    public BigDecimal getTotalNetCycle() {
        if (bulletins == null) return BigDecimal.ZERO;
        return bulletins.stream()
            .filter(b -> b.getSalaireNet() != null
                && ("VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut())))
            .map(BulletinPaie::getSalaireNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Nombre de bulletins en brouillon dans le cycle sélectionné. */
    public long getNbBrouillons() {
        if (bulletins == null) return 0;
        return bulletins.stream().filter(b -> "BROUILLON".equals(b.getStatut())).count();
    }

    /** Nombre de bulletins validés dans le cycle sélectionné. */
    public long getNbValides() {
        if (bulletins == null) return 0;
        return bulletins.stream()
            .filter(b -> "VALIDE".equals(b.getStatut()) || "PAYE".equals(b.getStatut()))
            .count();
    }

    // ══════════════════════════════════════════════════════════
    // RAPPORT DES PAIEMENTS EFFECTUÉS
    // ══════════════════════════════════════════════════════════

    /** Cycles déjà payés (ou payés puis clôturés) — alimente le rapport de paiements. */
    public List<CyclePaie> getCyclesPayes() {
        if (cycles == null) return new ArrayList<>();
        return cycles.stream()
            .filter(c -> "PAYE".equals(c.getStatut()) || "CLOTURE".equals(c.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    /** Montant net total versé pour un cycle (bulletins PAYE). */
    public BigDecimal getTotalNetByCycle(CyclePaie c) {
        if (c == null) return BigDecimal.ZERO;
        try {
            return paieService.getBulletinsByCycle(c.getId()).stream()
                .filter(b -> "PAYE".equals(b.getStatut()) && b.getSalaireNet() != null)
                .map(BulletinPaie::getSalaireNet)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception ex) { return BigDecimal.ZERO; }
    }

    /** Nombre de bulletins effectivement payés pour un cycle. */
    public long getNbBulletinsPayesByCycle(CyclePaie c) {
        if (c == null) return 0;
        try {
            return paieService.getBulletinsByCycle(c.getId()).stream()
                .filter(b -> "PAYE".equals(b.getStatut())).count();
        } catch (Exception ex) { return 0; }
    }

    /** Exporte le rapport des paiements effectués (tous cycles payés/clôturés) en CSV. */
    public void exporterRapportPaiementsCSV() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();

            String filename = "rapport_paiements_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + ".csv";

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            PrintWriter writer = response.getWriter();
            writer.write('﻿');
            writer.println("Periode;Date versement prevue;Date versement effective;Nb bulletins payes;Montant total net;Statut");

            for (CyclePaie c : getCyclesPayes()) {
                writer.println(
                    safe(c.getPeriodeLibelle()) + ";" +
                    (c.getDatePaiementPrevue()    != null ? c.getDatePaiementPrevue().toString()    : "") + ";" +
                    (c.getDatePaiementEffective()  != null ? c.getDatePaiementEffective().toString() : "") + ";" +
                    getNbBulletinsPayesByCycle(c) + ";" +
                    getTotalNetByCycle(c) + ";" +
                    safe(c.getStatut())
                );
            }
            writer.flush();
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur export : " + ex.getMessage());
        }
    }

    private String safe(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ══════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════════════

    public List<GrilleSalariale> getGrilles()             { return grilles; }
    public GrilleSalariale       getGrille()              { return grille; }
    public void                  setGrille(GrilleSalariale g){ this.grille = g; }
    public boolean               isModeEditionGrille()    { return modeEditionGrille; }

    public List<CyclePaie>  getCycles()                   { return cycles; }
    public CyclePaie        getCycle()                    { return cycle; }
    public void             setCycle(CyclePaie c)         { this.cycle = c; }
    public CyclePaie        getCycleSelectionne()         { return cycleSelectionne; }
    public void             setCycleSelectionne(CyclePaie c){ this.cycleSelectionne = c; }
    public boolean          isModeEditionCycle()          { return modeEditionCycle; }

    public Integer          getNouveauCycleMois()         { return nouveauCycleMois; }
    public void             setNouveauCycleMois(Integer m){ this.nouveauCycleMois = m; }
    public Integer          getNouveauCycleAnnee()        { return nouveauCycleAnnee; }
    public void             setNouveauCycleAnnee(Integer a){ this.nouveauCycleAnnee = a; }
    public java.time.LocalDate getNouveauCycleDatePaiementPrevue()           { return nouveauCycleDatePaiementPrevue; }
    public void                 setNouveauCycleDatePaiementPrevue(java.time.LocalDate d){ this.nouveauCycleDatePaiementPrevue = d; }
    public BigDecimal           getNouveauCycleBudget()                      { return nouveauCycleBudget; }
    public void                 setNouveauCycleBudget(BigDecimal b)         { this.nouveauCycleBudget = b; }

    /** Aujourd'hui, pour interdire au RH de choisir une date de versement déjà passée. */
    public java.util.Date getDateAujourdHui() {
        return new java.util.Date();
    }

    /** Libellé lisible de la prochaine période proposée, ex. "Octobre 2026". */
    public String getLibelleProchainCycle() {
        if (nouveauCycleMois == null || nouveauCycleAnnee == null) return "—";
        String[] moisNoms = {"Janvier","Février","Mars","Avril","Mai","Juin",
                             "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};
        return moisNoms[nouveauCycleMois - 1] + " " + nouveauCycleAnnee;
    }

    public List<BulletinPaie> getBulletins()              { return bulletins; }
    public BulletinPaie       getBulletinSelectionne()    { return bulletinSelectionne; }
    public String             getFiltreStatutBulletin()   { return filtreStatutBulletin; }
    public void               setFiltreStatutBulletin(String s){ this.filtreStatutBulletin = s; }
    public List<BulletinPaie> getBulletinsSelectionnes()       { return bulletinsSelectionnes; }
    public void                setBulletinsSelectionnes(List<BulletinPaie> l){ this.bulletinsSelectionnes = l; }
    public List<BulletinPaie> getArrieres()               { return arrieres; }

    public List<ElementSalaire> getElements()             { return elements; }
    public ElementSalaire       getElement()              { return element; }
    public void                 setElement(ElementSalaire es){ this.element = es; }
    public boolean              isModeEditionElem()       { return modeEditionElem; }
    public Long                 getElementGrilleIdSelectionne()          { return elementGrilleIdSelectionne; }
    public void                 setElementGrilleIdSelectionne(Long id)  { this.elementGrilleIdSelectionne = id; }

    public ElementSalaire       getElementBulletin()                    { return elementBulletin; }
    public void                 setElementBulletin(ElementSalaire es)  { this.elementBulletin = es; }
    public BulletinPaie         getBulletinPourElement()                { return bulletinPourElement; }

    public String getLabelTypeElement(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "PRIME"                 -> "Prime";
            case "INDEMNITE"              -> "Indemnité";
            case "RETENUE"                -> "Retenue";
            case "COTISATION_SALARIALE"   -> "Cotisation salariale";
            case "COTISATION_PATRONALE"   -> "Cotisation patronale";
            case "AVANTAGE_EN_NATURE"     -> "Avantage en nature";
            case "HEURES_SUP"             -> "Heures supplémentaires";
            default                       -> type;
        };
    }

    public String getLabelModeCalcul(String mode) {
        if (mode == null) return "—";
        return switch (mode) {
            case "FIXE"                      -> "Montant fixe";
            case "POURCENTAGE_SALAIRE_BASE"   -> "% du salaire de base";
            case "POURCENTAGE_SALAIRE_BRUT"   -> "% du salaire brut";
            case "FORMULE"                    -> "Formule";
            default                           -> mode;
        };
    }

    public BigDecimal getMasseSalarialeTotal()             { return masseSalarialeTotal; }
    public BigDecimal getMasseSalarialeMoyenne()          { return masseSalarialeMoyenne; }
    public long       getNombreBulletinsValides()         { return nombreBulletinsValides; }
    public long       getNombreCyclesOuverts()            { return nombreCyclesOuverts; }

    public String     getFiltreStatutCycle()              { return filtreStatutCycle; }
    public void       setFiltreStatutCycle(String s)      { this.filtreStatutCycle = s; }
    public Integer    getFiltreAnnee()                    { return filtreAnnee; }
    public void       setFiltreAnnee(Integer a)           { this.filtreAnnee = a; }

    public Integer    getAnneeConsultation()              { return anneeConsultation; }
    public void       setAnneeConsultation(Integer a)     { this.anneeConsultation = a; }
    public Integer    getMoisConsultation()               { return moisConsultation; }
    public void       setMoisConsultation(Integer m)      { this.moisConsultation = m; }
}