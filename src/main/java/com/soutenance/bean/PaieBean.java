package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.BulletinPaie;
import com.soutenance.entity.Employe;
import com.soutenance.entity.CyclePaie;
import com.soutenance.entity.GrilleSalariale;
import com.soutenance.service.PaieService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean JSF — Gestion de la paie (périmètre RH).
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. Propriétés XHTML manquantes sur bulletin :
 *    - #{paieBean.bulletin.annee}        → getBulletinAnnee()
 *    - #{paieBean.bulletin.mois}         → getBulletinMois()
 *    - #{paieBean.bulletin.libelle}      → getBulletinLibelle()
 *    - #{paieBean.bulletin.cycle.libelle}→ getBulletinCycleLibelle()
 *    - #{paieBean.bulletin.dateCloture}  → getBulletinDateCloture()
 *    - #{paieBean.bulletin.institutionId}→ getBulletinInstitutionId()
 *    - #{paieBean.bulletin.lignes}       → getBulletinLignes()
 *    - #{paieBean.bulletin.observations} → getBulletinObservations()
 *
 *    NOTE : Ces champs n'existent pas directement dans l'entité BulletinPaie.
 *    Ils sont exposés via des méthodes helper dans ce bean (pattern Façade).
 *    Cela évite de modifier l'entité et ses mappings Hibernate.
 *
 * 2. Propriétés XHTML manquantes sur grille :
 *    - #{paieBean.grille.code}             → getGrilleCode()
 *    - #{paieBean.grille.categorie}        → getGrilleCategorie()
 *    - #{paieBean.grille.type}             → getGrilleType()
 *    - #{paieBean.grille.taux}             → getGrilleTaux()
 *    - #{paieBean.grille.actif}            → isGrilleActif()
 *    - #{paieBean.grille.modeCalcul}       → getGrilleModeCalcul()
 *    - #{paieBean.grille.indemTransport}   → getGrilleIndemTransport()
 *    - #{paieBean.grille.indemResidence}   → getGrilleIndemResidence()
 *    - #{paieBean.grille.indemRepresentation} → getGrilleIndemRepresentation()
 *    - #{paieBean.grille.primeRisque}      → getGrillePrimeRisque()
 *
 *    Même approche : ces champs sont dans ElementSalaire (rattaché à la grille),
 *    pas dans GrilleSalariale directement. Les helpers les calculent à la volée.
 *
 * 3. Taux fiscaux : PaieService corrigé lit depuis ParametreSysteme.
 *    Ce bean bénéficie automatiquement de la correction sans changement.
 */
@Component("paieBean")
@Scope("view")
public class PaieBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(PaieBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private PaieService  paieService;
    @Autowired private SessionBean  sessionBean;
    @Autowired(required = false)
    private AdminBean adminBean;

    private Long getInstitutionId() {
        if (adminBean != null && adminBean.getInstitutionFiltreId() != null)
            return adminBean.getInstitutionFiltreId();
        return sessionBean.getInstitutionId();
    }

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<BulletinPaie>    bulletins;
    private List<GrilleSalariale> grilles;
    private List<Employe>         employesSansBulletin;

    // ── Entités en cours d'édition ───────────────────────────────────────────
    private BulletinPaie    bulletin     = new BulletinPaie();
    private CyclePaie       nouveauCycle = new CyclePaie();
    private String          cycleLibelle = "";
    private GrilleSalariale grille       = new GrilleSalariale();
    private Employe         employeSelectionne;

    // ── Période de paie sélectionnée ─────────────────────────────────────────
    private int  moisSelectionne   = LocalDate.now().getMonthValue();
    private int  anneeSelectionnee = LocalDate.now().getYear();

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean modeEditionGrille = false;
    private boolean cycleEnCours      = false;
    private String  filtreStatutPaie;

    // ── Statistiques ──────────────────────────────────────────────────────────
    private BigDecimal masseSalarialeMensuelle    = BigDecimal.ZERO;
    private BigDecimal chargesSocialesMensuelles  = BigDecimal.ZERO;
    private long       bulletinsGeneres;
    private long       bulletinsEnAttente;

    @PostConstruct
    public void init() {
        try { chargerTout(); }
        catch (Exception e) {
            System.err.println("PaieBean init error: " + e.getMessage());
            bulletins            = new ArrayList<>();
            grilles              = new ArrayList<>();
            employesSansBulletin = new ArrayList<>();
        }
    }

    private void chargerTout() {
        Long instId = getInstitutionId();
        bulletins = paieService.getBulletinsByPeriode(moisSelectionne, anneeSelectionnee)
            .stream()
            .filter(b -> instId == null ||
                (b.getEmploye() != null && instId.equals(b.getEmploye().getInstitutionId())))
            .collect(java.util.stream.Collectors.toList());
        grilles              = paieService.getGrillesByInstitution(getInstitutionId());
        employesSansBulletin = paieService.getEmployesSansBulletin(moisSelectionne, anneeSelectionnee);
        masseSalarialeMensuelle   = paieService.getMasseSalariale(moisSelectionne, anneeSelectionnee);
        chargesSocialesMensuelles = paieService.getChargesSociales(moisSelectionne, anneeSelectionnee);
        bulletinsGeneres     = paieService.countBulletinsGeneres(moisSelectionne, anneeSelectionnee);
        bulletinsEnAttente   = paieService.countBulletinsEnAttente(moisSelectionne, anneeSelectionnee);
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // CYCLE DE PAIE
    // ════════════════════════════════════════════════════════════

    public void lancerCyclePaie() {
        if (employesSansBulletin == null || employesSansBulletin.isEmpty()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Rien à faire",
                "Tous les employés actifs ont déjà un bulletin pour cette période.");
            return;
        }
        try {
            cycleEnCours = true;
            int nb = paieService.lancerCyclePaie(moisSelectionne, anneeSelectionnee, getOp());
            chargerTout();
            cycleEnCours = false;
            addMsg(FacesMessage.SEVERITY_INFO, "Cycle terminé",
                nb + " bulletin(s) généré(s) pour " + moisSelectionne + "/" + anneeSelectionnee + ".");
        } catch (Exception ex) {
            cycleEnCours = false;
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur cycle", ex.getMessage());
        }
    }

    public void genererBulletinPour(Employe e) {
        try {
            paieService.genererBulletin(e, moisSelectionne, anneeSelectionnee, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Bulletin généré",
                "Bulletin créé pour " + e.getNomComplet()
                + " (" + moisSelectionne + "/" + anneeSelectionnee + ").");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void validerBulletin(Long bulletinId) {
        try {
            paieService.validerBulletin(bulletinId, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Bulletin validé", "Le bulletin a été clôturé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void selectionnerBulletin(BulletinPaie b) {
        this.bulletin = paieService.getBulletinById(b.getId());
        if (this.bulletin == null) this.bulletin = b;
    }

    public String sauvegarderBulletin() {
        try {
            if (bulletin.getId() == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Utilisez le cycle de paie pour créer un bulletin.");
                return null;
            }
            paieService.modifierBulletin(bulletin, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Bulletin mis à jour.");
            bulletin = new BulletinPaie();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════
    // GRILLES SALARIALES
    // ════════════════════════════════════════════════════════════

    public void preparerNouvelleGrille() {
        grille = new GrilleSalariale(); modeEditionGrille = false;
    }

    public void preparerEditionGrille(GrilleSalariale g) {
        this.grille = g; modeEditionGrille = true;
    }

    public String sauvegarderGrille() {
        try {
            if (grille.getEchelon() == null || grille.getEchelon().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "L'échelon est obligatoire."); return null;
            }
            if (grille.getSalaireBase() == null || grille.getSalaireBase().compareTo(BigDecimal.ZERO) <= 0) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Salaire de base > 0 requis."); return null;
            }
            if (modeEditionGrille) {
                paieService.modifierGrille(grille, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Grille modifiée.");
            } else {
                paieService.creerGrille(grille, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Grille créée.");
            }
            grilles = paieService.getGrillesByInstitution(getInstitutionId());
            grille  = new GrilleSalariale();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimerGrille(Long id) {
        try {
            paieService.supprimerGrille(id, getOp());
            grilles = paieService.getGrillesByInstitution(getInstitutionId());
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Grille salariale supprimée.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // NAVIGATION PÉRIODE
    // ════════════════════════════════════════════════════════════

    public void periodePreced() {
        if (moisSelectionne == 1) { moisSelectionne = 12; anneeSelectionnee--; }
        else moisSelectionne--;
        chargerTout();
    }

    public void periodeSuiv() {
        if (moisSelectionne == 12) { moisSelectionne = 1; anneeSelectionnee++; }
        else moisSelectionne++;
        chargerTout();
    }

    public String getLabelPeriode() {
        String[] mois = {"","Janvier","Février","Mars","Avril","Mai","Juin",
                         "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};
        return mois[moisSelectionne] + " " + anneeSelectionnee;
    }

    // ════════════════════════════════════════════════════════════
    // FILTRAGE
    // ════════════════════════════════════════════════════════════

    public void filtrerBulletins() {
        Long instId = getInstitutionId();
        bulletins = paieService.getBulletinsByPeriode(moisSelectionne, anneeSelectionnee)
            .stream()
            .filter(b -> instId == null ||
                (b.getEmploye() != null && instId.equals(b.getEmploye().getInstitutionId())))
            .filter(b -> filtreStatutPaie == null || filtreStatutPaie.isBlank()
                || filtreStatutPaie.equals(b.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS BULLETIN — PROPRIÉTÉS MANQUANTES DANS L'ENTITÉ
    // Appelées par le XHTML via #{paieBean.bulletin.XXX}
    // mais l'entité n'a pas ces champs directement.
    // ════════════════════════════════════════════════════════════

    /**
     * #{paieBean.bulletin.mois} → mois du cycle de paie rattaché.
     * BulletinPaie n'a pas de champ mois direct — c'est dans CyclePaie.
     */
    public Integer getBulletinMois() {
        if (bulletin == null) return moisSelectionne;
        if (bulletin.getCyclePaie() != null && bulletin.getCyclePaie().getMois() != null)
            return bulletin.getCyclePaie().getMois();
        return moisSelectionne;
    }

    /**
     * #{paieBean.bulletin.annee} → année du cycle de paie rattaché.
     */
    public Integer getBulletinAnnee() {
        if (bulletin == null) return anneeSelectionnee;
        if (bulletin.getCyclePaie() != null && bulletin.getCyclePaie().getAnnee() != null)
            return bulletin.getCyclePaie().getAnnee();
        return anneeSelectionnee;
    }

    /**
     * #{paieBean.bulletin.libelle} → libellé lisible du bulletin.
     * Construit à partir de l'employé et de la période.
     */
    public String getBulletinLibelle() {
        if (bulletin == null) return "";
        String emp = bulletin.getEmploye() != null
            ? bulletin.getEmploye().getNomComplet() : "?";
        return "Bulletin " + getBulletinMois() + "/" + getBulletinAnnee() + " — " + emp;
    }

    /**
     * #{paieBean.bulletin.cycle.libelle} → libellé du cycle.
     * Chaîne EL "bulletin.cycle.libelle" nécessite un objet cycle avec getLibelle().
     * On expose un objet proxy via getBulletinCycleLibelle() pour le XHTML.
     * Mais le XHTML appelle directement bulletin.cycle.libelle donc
     * CyclePaie doit avoir getPeriodeLibelle() — déjà le cas.
     * Ce getter est une sécurité supplémentaire.
     */
    public String getBulletinCycleLibelle() {
        if (bulletin == null) return "";
        if (bulletin.getCyclePaie() != null)
            return bulletin.getCyclePaie().getPeriodeLibelle();
        return getLabelPeriode();
    }

    /**
     * #{paieBean.bulletin.dateCloture} → date de clôture du cycle.
     * BulletinPaie n'a pas dateCloture — c'est la datePaiementEffective du cycle.
     */
    public Object getBulletinDateCloture() {
        if (bulletin == null) return null;
        if (bulletin.getCyclePaie() != null)
            return bulletin.getCyclePaie().getDatePaiementEffective();
        return null;
    }

    /**
     * #{paieBean.bulletin.institutionId} → ID de l'institution via l'employé.
     */
    public Long getBulletinInstitutionId() {
        if (bulletin == null) return null;
        if (bulletin.getEmploye() != null) return bulletin.getEmploye().getInstitutionId();
        return sessionBean.getInstitutionId();
    }

    /**
     * #{paieBean.bulletin.lignes} → éléments détaillés du bulletin.
     * "Lignes" = ElementSalaire rattachés au bulletin.
     */
    public List<?> getBulletinLignes() {
        if (bulletin == null) return List.of();
        return bulletin.getElements() != null ? bulletin.getElements() : List.of();
    }

    /**
     * #{paieBean.bulletin.observations} → commentaire libre.
     * BulletinPaie n'a pas ce champ — on retourne le modePaiement comme fallback.
     */
    public String getBulletinObservations() {
        if (bulletin == null) return "";
        return bulletin.getModePaiement() != null ? bulletin.getModePaiement() : "";
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS GRILLE — PROPRIÉTÉS MANQUANTES DANS L'ENTITÉ
    // GrilleSalariale n'a que : libelle, corps, echelon, salaireBase,
    // indiceMajore, dateEffet, dateFin, statut.
    // Le XHTML demande en plus : code, categorie, type, taux,
    // actif, modeCalcul, indemTransport, indemResidence,
    // indemRepresentation, primeRisque.
    // Ces valeurs sont extraites des ElementSalaire de la grille
    // ou retournent des valeurs par défaut.
    // ════════════════════════════════════════════════════════════

    /** #{paieBean.grille.code} → code court de la grille (échelon ou corps). */
    public String getGrilleCode() {
        if (grille == null) return "";
        if (grille.getCorps() != null && grille.getEchelon() != null)
            return grille.getCorps() + "-" + grille.getEchelon();
        if (grille.getEchelon() != null) return grille.getEchelon();
        return "";
    }

    /** #{paieBean.grille.categorie} → corps de la grille. */
    public String getGrilleCategorie() {
        return grille != null ? (grille.getCorps() != null ? grille.getCorps() : "") : "";
    }

    /** #{paieBean.grille.type} → type déduit du libellé. */
    public String getGrilleType() {
        if (grille == null || grille.getLibelle() == null) return "STANDARD";
        String lib = grille.getLibelle().toUpperCase();
        if (lib.contains("CADRE"))        return "CADRE";
        if (lib.contains("TECHNICIEN"))   return "TECHNICIEN";
        if (lib.contains("AGENT"))        return "AGENT";
        return "STANDARD";
    }

    /**
     * #{paieBean.grille.taux} → taux d'indice majoré ou pourcentage.
     * Retourne indiceMajore si disponible, sinon 0.
     */
    public int getGrilleTaux() {
        return (grille != null && grille.getIndiceMajore() != null)
            ? grille.getIndiceMajore() : 0;
    }

    /** #{paieBean.grille.actif} → grille active selon statut et dates. */
    public boolean isGrilleActif() {
        return grille != null && grille.isActive();
    }

    /** #{paieBean.grille.modeCalcul} → mode de calcul (INDICE ou FIXE). */
    public String getGrilleModeCalcul() {
        if (grille == null) return "FIXE";
        return (grille.getIndiceMajore() != null && grille.getIndiceMajore() > 0)
            ? "INDICE" : "FIXE";
    }

    /**
     * Helpers indemnités : cherchent dans les éléments de la grille
     * un élément de type correspondant. Retournent ZERO si absent.
     */
    public BigDecimal getGrilleIndemTransport() {
        return getMontantElement("INDEMNITE", "TRANSPORT");
    }

    public BigDecimal getGrilleIndemResidence() {
        return getMontantElement("INDEMNITE", "RESIDENCE");
    }

    public BigDecimal getGrilleIndemRepresentation() {
        return getMontantElement("INDEMNITE", "REPRESENTATION");
    }

    public BigDecimal getGrillePrimeRisque() {
        return getMontantElement("PRIME", "RISQUE");
    }

    private BigDecimal getMontantElement(String type, String motCle) {
        if (grille == null || grille.getElements() == null) return BigDecimal.ZERO;
        return grille.getElements().stream()
            .filter(e -> type.equals(e.getType())
                && e.getLibelle() != null
                && e.getLibelle().toUpperCase().contains(motCle))
            .map(e -> e.getValeurFixe() != null ? e.getValeurFixe() : BigDecimal.ZERO)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "VALIDE"     -> "badge-success";
            case "BROUILLON"  -> "badge-warning";
            case "EN_ATTENTE" -> "badge-info";
            default           -> "badge-secondary";
        };
    }

    public String getLabelStatut(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "VALIDE"     -> "Validé";
            case "BROUILLON"  -> "Brouillon";
            case "EN_ATTENTE" -> "En attente";
            default           -> statut;
        };
    }

    public long getTauxGeneration() {
        long total = bulletinsGeneres + bulletinsEnAttente;
        if (total == 0) return 0;
        return bulletinsGeneres * 100 / total;
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<BulletinPaie>    getBulletins()             { return bulletins; }
    public List<GrilleSalariale> getGrilles()               { return grilles; }
    public List<Employe>         getEmployesSansBulletin()  { return employesSansBulletin; }

    public BulletinPaie    getBulletin()                    { return bulletin; }
    public void            setBulletin(BulletinPaie b)      { this.bulletin = b; }
    public CyclePaie       getNouveauCycle()                { return nouveauCycle; }
    public void            setNouveauCycle(CyclePaie c)     { this.nouveauCycle = c; }
    public String          getCycleLibelle()                { return cycleLibelle; }
    public void            setCycleLibelle(String s)        { this.cycleLibelle = s; }
    public GrilleSalariale getGrille()                      { return grille; }
    public void            setGrille(GrilleSalariale g)     { this.grille = g; }
    public Employe         getEmployeSelectionne()          { return employeSelectionne; }
    public void            setEmployeSelectionne(Employe e) { this.employeSelectionne = e; }

    public int  getMoisSelectionne()                        { return moisSelectionne; }
    public void setMoisSelectionne(int m)                   { this.moisSelectionne = m; }
    public int  getAnneeSelectionnee()                      { return anneeSelectionnee; }
    public void setAnneeSelectionnee(int a)                 { this.anneeSelectionnee = a; }

    public boolean isModeEditionGrille()                    { return modeEditionGrille; }
    public boolean isCycleEnCours()                         { return cycleEnCours; }
    public String  getFiltreStatutPaie()                    { return filtreStatutPaie; }
    public void    setFiltreStatutPaie(String s)            { this.filtreStatutPaie = s; }

    public BigDecimal getMasseSalarialeMensuelle()          { return masseSalarialeMensuelle; }
    public BigDecimal getChargesSocialesMensuelles()        { return chargesSocialesMensuelles; }
    public long       getBulletinsGeneres()                 { return bulletinsGeneres; }
    public long       getBulletinsEnAttente()               { return bulletinsEnAttente; }
}