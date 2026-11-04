package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.Bureau;
import com.soutenance.entity.Direction;
import com.soutenance.entity.Division;
import com.soutenance.entity.Employe;
import com.soutenance.entity.FamillePoste;
import com.soutenance.entity.FichePoste;
import com.soutenance.entity.Poste;
import com.soutenance.entity.Service;
import com.soutenance.service.PosteService;
import com.soutenance.service.StructureService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean JSF — Gestion des postes, fiches de poste et organigramme.
 *
 * Fonctionnalités couvertes (point 4 du cahier des charges) :
 *  - Créer et gérer le référentiel des postes et métiers
 *  - Définir les fiches de poste (missions, compétences, diplômes requis)
 *  - Visualiser et modifier l'organigramme de chaque institution
 *  - Gérer les effectifs cibles et les vacances de postes
 *  - Lier un poste à une unité organisationnelle (direction/division/service/bureau)
 *
 * NOTE : Les entités Poste et FichePoste sont attendues dans le package
 * com.soutenance.entity. Si elles n'existent pas encore, créer :
 *   - Poste      : id, intitule, code, famille, niveauRequis, statutPoste,
 *                  effectifCible, effectifActuel, unite (String), creePar
 *   - FichePoste : id, poste (ManyToOne), missions, competences, diplomesRequis,
 *                  experienceMinimale, dateCreation, valide (boolean)
 */
@Component("posteBean")
@Scope("view")
public class PosteBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(PosteBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private PosteService    posteService;
    @Autowired private StructureService structureService;
    @Autowired private SessionBean     sessionBean;
    @Autowired(required = false)
    private com.soutenance.bean.AdminBean adminBean;
    @Autowired(required = false)
    private com.soutenance.dao.InstitutionDao institutionDao;

    private Long getInstitutionId() {
        if (adminBean != null && adminBean.getInstitutionFiltreId() != null)
            return adminBean.getInstitutionFiltreId();
        return sessionBean.getInstitutionId();
    }

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<Poste>        postes;
    private List<Poste>        postesFiltres;
    private List<FichePoste>   fichesPoste;
    private List<FamillePoste> familles = new ArrayList<>(); // CORRECTION

    /** Unités organisationnelles de l'institution, pour le champ "Unité de rattachement" du poste. */
    private List<Direction> directionsStructure = new ArrayList<>();
    private List<Division>  divisionsStructure  = new ArrayList<>();
    private List<Service>   servicesStructure   = new ArrayList<>();
    private List<Bureau>    bureauxStructure    = new ArrayList<>();

    // ── Entités en cours d'édition ───────────────────────────────────────────
    private Poste        poste       = new Poste();
    private FichePoste   fichePoste  = new FichePoste();
    private FamillePoste familleForm = new FamillePoste(); // CORRECTION : #{posteBean.familleForm.*}

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean modeEditionPoste  = false;
    private boolean modeEditionFiche  = false;
    private boolean modeFamille       = false;       // CORRECTION : #{posteBean.modeFamille}
    private String  filtreStatutPoste;
    private String  filtreFamille;
    private String  termeRecherche;
    private String  recherche;                       // CORRECTION : #{posteBean.recherche}
    private Poste   posteSelectionne;

    // ── Statistiques ─────────────────────────────────────────────────────────
    private long totalPostes;
    private long postesVacants;
    private long postesOccupes;
    private long fichesSansPoste;
    private long totalFamilles;                      // CORRECTION : #{posteBean.totalFamilles}

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("PosteBean init error: " + e.getMessage());
            postes        = new ArrayList<>();
            postesFiltres = new ArrayList<>();
            fichesPoste   = new ArrayList<>();
        }
    }

    private void chargerTout() {
        Long instId = getInstitutionId();
        postes = (instId != null) ? posteService.getPostesByInstitution(instId) : posteService.getTousLesPostes();
        postesFiltres = postes;
        directionsStructure = instId != null ? structureService.getDirectionsByInstitution(instId) : new ArrayList<>();
        divisionsStructure  = instId != null ? structureService.getDivisionsByInstitution(instId)  : new ArrayList<>();
        servicesStructure   = instId != null ? structureService.getServicesByInstitution(instId)   : new ArrayList<>();
        bureauxStructure    = instId != null ? structureService.getBureauxByInstitution(instId)     : new ArrayList<>();
        // Dérivées des postes déjà scopés à l'institution — getToutesFichesPoste()
        // renvoie les fiches de TOUTES les institutions, ce qui fuiterait des
        // données entre institutions pour un Admin non super-admin ou un RH.
        fichesPoste = postes.stream()
            .map(p -> posteService.getFicheByPoste(p.getId()))
            .filter(java.util.Objects::nonNull)
            .toList();
        totalPostes   = postes.size();
        postesVacants = postes.stream()
            .filter(p -> "VACANT".equals(p.getStatutPoste())).count();
        postesOccupes = postes.stream()
            .filter(p -> "OCCUPE".equals(p.getStatutPoste())).count();
        fichesSansPoste = postes.stream()
            .filter(p -> posteService.getFicheByPoste(p.getId()) == null).count();
        // CORRECTION : charger les familles de postes
        try {
            familles     = posteService.getToutesFamilles();
            totalFamilles = familles.size();
        } catch (Exception e) {
            familles      = new ArrayList<>();
            totalFamilles = 0;
        }
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // POSTES
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauPoste() {
        poste            = new Poste();
        poste.setStatutPoste("VACANT");
        modeEditionPoste = false;
    }

    public void preparerEditionPoste(Poste p) {
        this.poste        = posteService.getPosteById(p.getId());
        if (this.poste == null) this.poste = p;
        modeEditionPoste = true;
    }

    public String sauvegarderPoste() {
        try {
            // Validation minimale
            if (poste.getIntitule() == null || poste.getIntitule().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "L'intitulé du poste est obligatoire.");
                return null;
            }
            if (poste.getCode() == null || poste.getCode().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Le code du poste est obligatoire.");
                return null;
            }
            if (modeEditionPoste) {
                posteService.modifierPoste(poste, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Poste modifié : " + poste.getIntitule());
            } else {
                poste.setCode(poste.getCode().toUpperCase().trim());
                if (poste.getInstitution() == null && institutionDao != null) {
                    poste.setInstitution(institutionDao.findById(getInstitutionId()));
                }
                posteService.creerPoste(poste, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Poste créé : " + poste.getIntitule());
            }
            chargerTout();
            poste = new Poste();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimerPoste(Long id) {
        try {
            // Vérifier si le poste est occupé
            Poste p = posteService.getPosteById(id);
            if (p != null && "OCCUPE".equals(p.getStatutPoste())) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Suppression impossible",
                    "Ce poste est actuellement occupé. Effectuez d'abord un mouvement de carrière.");
                return;
            }
            posteService.supprimerPoste(id, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Poste supprimé",
                "Le poste a été supprimé du référentiel.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void selectionnerPoste(Poste p) {
        this.posteSelectionne = p;
        // Charger la fiche associée si elle existe
        FichePoste fiche = posteService.getFicheByPoste(p.getId());
        if (fiche != null) {
            this.fichePoste = fiche;
            this.modeEditionFiche = true;
        } else {
            this.fichePoste = new FichePoste();
            this.fichePoste.setPoste(p);
            this.modeEditionFiche = false;
        }
    }

    // ════════════════════════════════════════════════════════════
    // FICHES DE POSTE
    // ════════════════════════════════════════════════════════════

    public void preparerNouvelleFiche(Poste p) {
        fichePoste           = new FichePoste();
        fichePoste.setPoste(p);
        modeEditionFiche     = false;
    }

    public void preparerEditionFiche(FichePoste f) {
        this.fichePoste   = f;
        modeEditionFiche  = true;
    }

    public String sauvegarderFichePoste() {
        try {
            if (fichePoste.getPoste() == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Veuillez associer un poste à cette fiche.");
                return null;
            }
            if (fichePoste.getMissions() == null || fichePoste.getMissions().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "La description des missions est obligatoire.");
                return null;
            }
            if (modeEditionFiche) {
                posteService.modifierFichePoste(fichePoste, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Fiche de poste mise à jour.");
            } else {
                posteService.creerFichePoste(fichePoste, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Fiche de poste créée.");
            }
            chargerTout();
            fichePoste = new FichePoste();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void validerFichePoste(Long ficheId) {
        try {
            posteService.validerFichePoste(ficheId, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Fiche validée",
                "La fiche de poste a été validée et publiée.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // FILTRAGE
    // ════════════════════════════════════════════════════════════

    public void filtrer() {
        postesFiltres = postes.stream()
            .filter(p -> {
                // Filtre statut
                if (filtreStatutPoste != null && !filtreStatutPoste.isBlank()
                        && !filtreStatutPoste.equals(p.getStatutPoste())) return false;
                // Filtre famille
                if (filtreFamille != null && !filtreFamille.isBlank()
                        && !filtreFamille.equals(p.getFamille())) return false;
                // Filtre texte
                if (termeRecherche != null && !termeRecherche.isBlank()) {
                    String q = termeRecherche.toLowerCase();
                    String intitule = (p.getIntitule() != null ? p.getIntitule() : "").toLowerCase();
                    String code     = (p.getCode() != null ? p.getCode() : "").toLowerCase();
                    String unite    = (p.getUnite() != null ? p.getUnite() : "").toLowerCase();
                    if (!intitule.contains(q) && !code.contains(q) && !unite.contains(q))
                        return false;
                }
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    public void reinitialiserFiltres() {
        filtreStatutPoste = null;
        filtreFamille     = null;
        termeRecherche    = null;
        postesFiltres     = postes;
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    /** Taux d'occupation des postes (en %). */
    public long getTauxOccupation() {
        if (totalPostes == 0) return 0;
        return postesOccupes * 100 / totalPostes;
    }

    /** Classe CSS badge selon le statut du poste. */
    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "OCCUPE"    -> "badge-success";
            case "VACANT"    -> "badge-warning";
            case "SUPPRIME"  -> "badge-danger";
            default          -> "badge-secondary";
        };
    }

    /** Libellé du statut du poste. */
    public String getLabelStatut(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "OCCUPE"   -> "Occupé";
            case "VACANT"   -> "Vacant";
            case "SUPPRIME" -> "Supprimé";
            default         -> statut;
        };
    }

    /** Retourne l'employé actuellement affecté à un poste, ou null. */
    public Employe getEmployeSurPoste(Long posteId) {
        try { return posteService.getEmployeSurPoste(posteId); }
        catch (Exception e) { return null; }
    }

    /** Retourne la liste de toutes les familles de postes (pour le filtre). */
    public List<String> getFamillesPostes() {
        try { return posteService.getToutesFamillesPostes(); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    // ════════════════════════════════════════════════════════════
    // FAMILLES DE POSTES — CORRECTION
    // #{posteBean.familleForm.*}, #{posteBean.modeFamille},
    // #{posteBean.sauvegarderFamille()}, #{posteBean.totalFamilles}
    // ════════════════════════════════════════════════════════════

    public void preparerNouvelleFamillePoste() {
        familleForm  = new FamillePoste();
        modeFamille  = false;
    }

    public void preparerEditionFamillePoste(FamillePoste f) {
        this.familleForm = f;
        modeFamille      = true;
    }

    public String sauvegarderFamille() {
        try {
            if (familleForm.getNom() == null || familleForm.getNom().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le nom de la famille est obligatoire.");
                return null;
            }
            if (modeFamille) {
                posteService.modifierFamille(familleForm, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Famille modifiée.");
            } else {
                posteService.creerFamille(familleForm, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Famille créée : " + familleForm.getNom());
            }
            chargerTout();
            familleForm = new FamillePoste();
            modeFamille = false;
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    /**
     * CORRECTION : #{posteBean.preparerEdition(posteBean.posteSelectionne)}
     * Version sans paramètre — prépare l'édition du poste actuellement sélectionné.
     */
    public void preparerEdition(Poste p) {
        preparerEditionPoste(p != null ? p : posteSelectionne);
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<Poste>      getPostes()              { return postes; }
    public List<Poste>      getPostesFiltres()       { return postesFiltres; }
    public List<FichePoste> getFichesPoste()         { return fichesPoste; }

    public Poste      getPoste()                     { return poste; }
    public void       setPoste(Poste p)              { this.poste = p; }
    public FichePoste getFichePoste()                { return fichePoste; }
    public void       setFichePoste(FichePoste f)    { this.fichePoste = f; }
    public Poste      getPosteSelectionne()          { return posteSelectionne; }
    public void       setPosteSelectionne(Poste p)   { this.posteSelectionne = p; }

    public boolean isModeEditionPoste()              { return modeEditionPoste; }
    public boolean isModeEditionFiche()              { return modeEditionFiche; }

    public String  getFiltreStatutPoste()            { return filtreStatutPoste; }
    public void    setFiltreStatutPoste(String s)    { this.filtreStatutPoste = s; }
    public String  getFiltreFamille()                { return filtreFamille; }
    public void    setFiltreFamille(String s)        { this.filtreFamille = s; }
    public String  getTermeRecherche()               { return termeRecherche; }
    public void    setTermeRecherche(String s)       { this.termeRecherche = s; }

    /** #{posteBean.postesVacantsList} — pour le tab "Postes vacants" */
    public List<Poste> getPostesVacantsList() {
        if (postes == null) return new ArrayList<>();
        return postes.stream()
            .filter(p -> "VACANT".equals(p.getStatutPoste()))
            .collect(java.util.stream.Collectors.toList());
    }

    /** #{posteBean.countPostesByFamille(fm.nom)} — nombre de postes par famille */
    public long countPostesByFamille(String nomFamille) {
        if (postes == null || nomFamille == null) return 0;
        return postes.stream()
            .filter(p -> nomFamille.equals(p.getFamille()))
            .count();
    }

    public long getTotalPostes()                     { return totalPostes; }
    public long getPostesVacants()                   { return postesVacants; }
    public long getPostesOccupes()                   { return postesOccupes; }
    public long getFichesSansPoste()                 { return fichesSansPoste; }

    // CORRECTION : alias pour le XHTML
    /** #{posteBean.posteForm.*} — alias de poste */
    public Poste getPosteForm()                      { return poste; }
    public void  setPosteForm(Poste p)               { this.poste = p; }

    /** #{posteBean.sauvegarder()} — alias de sauvegarderPoste() */
    public String sauvegarder()                      { return sauvegarderPoste(); }

    // CORRECTION : getters manquants pour le XHTML
    public long              getTotalFamilles()      { return totalFamilles; }
    public List<FamillePoste> getFamilles()          { return familles; }
    public FamillePoste      getFamilleForm()        { return familleForm; }
    public void              setFamilleForm(FamillePoste f) { this.familleForm = f; }
    public boolean           isModeFamille()         { return modeFamille; }
    public void              setModeFamille(boolean b) { this.modeFamille = b; }

    public List<Direction> getDirectionsStructure() { return directionsStructure; }
    public List<Division>  getDivisionsStructure()  { return divisionsStructure; }
    public List<Service>   getServicesStructure()   { return servicesStructure; }
    public List<Bureau>    getBureauxStructure()    { return bureauxStructure; }

    /** #{posteBean.recherche} — alias de termeRecherche */
    public String getRecherche()                     { return termeRecherche; }
    public void   setRecherche(String s)             { this.termeRecherche = s; }
}