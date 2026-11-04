package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.AdminPosteService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean de gestion des postes et fiches de poste — périmètre administrateur.
 *
 * Couvre :
 *  - Référentiel des postes (CRUD, activation/désactivation)
 *  - Fiches de poste détaillées (missions, compétences, conditions)
 *  - Suivi des vacances de postes (effectif cible vs actuel)
 *  - Organigramme des postes par direction/division/service
 *  - Export CSV des postes vacants
 */
@Component("adminPosteBean")
@Scope("view")
public class AdminPosteBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPosteBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminPosteService posteService;
    @Autowired private SessionBean       sessionBean;

    // ── Listes ────────────────────────────────────────────────────────────
    private List<Poste>       postes         = new ArrayList<>();
    private List<Poste>       postesVacants  = new ArrayList<>();

    // ── Entités en édition ────────────────────────────────────────────────
    private Poste             poste          = new Poste();
    private FichePoste        fichePoste     = new FichePoste();
    private Poste             posteSelectionne;

    // ── Flags UI ──────────────────────────────────────────────────────────
    private boolean modeEditionPoste      = false;
    private boolean modeEditionFiche      = false;
    private boolean afficherPostesVacants = false;

    // ── Filtres ───────────────────────────────────────────────────────────
    private String recherche;
    private String filtreFamille;
    private String filtreStatut;
    private String filtreCorps;

    // ── Stats ──────────────────────────────────────────────────────────────
    private long totalPostes;
    private long postesActifs;
    private long totalVacances;
    private long postesAvecFiche;

    @PostConstruct
    public void init() {
        chargerTout();
    }

    private void chargerTout() {
        try {
            Long instId = sessionBean.getInstitutionId();
            postes        = posteService.getPostesByInstitution(instId);
            postesVacants = postes.stream().filter(Poste::isVacant).collect(Collectors.toList());
            calculerStats();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            postes        = new ArrayList<>();
            postesVacants = new ArrayList<>();
        }
    }

    private void calculerStats() {
        totalPostes    = postes.size();
        postesActifs   = postes.stream().filter(p -> "ACTIF".equals(p.getStatut())).count();
        totalVacances  = postesVacants.stream()
            .mapToLong(Poste::getNombreVacances).sum();
        postesAvecFiche = postes.stream()
            .filter(p -> p.getFichePoste() != null).count();
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    // ══════════════════════════════════════════════════════════
    // POSTES — CRUD
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauPoste() {
        poste          = new Poste();
        modeEditionPoste = false;
    }

    public void preparerEditionPoste(Poste p) {
        this.poste = posteService.getPosteById(p.getId());
        if (this.poste == null) this.poste = p;
        modeEditionPoste = true;
    }

    public String sauvegarderPoste() {
        try {
            // Validations
            if (poste.getCode() == null || poste.getCode().isBlank()) {
                addError("Le code du poste est obligatoire.");
                return null;
            }
            if (poste.getIntitule() == null || poste.getIntitule().isBlank()) {
                addError("L'intitulé du poste est obligatoire.");
                return null;
            }

            Long instId = sessionBean.getInstitutionId();
            if (instId == null) {
                addError("Aucune institution sélectionnée.");
                return null;
            }

            if (modeEditionPoste) {
                posteService.modifierPoste(poste, getOp());
                addInfo("Poste modifié : " + poste.getIntitule());
            } else {
                // Vérifier unicité du code dans l'institution
                String erreur = posteService.creerPoste(poste, instId, getOp());
                if (erreur != null) {
                    addError(erreur);
                    return null;
                }
                addInfo("Poste créé : " + poste.getIntitule());
            }
            chargerTout();
            poste = new Poste();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void activerPoste(Long id) {
        try {
            posteService.changerStatutPoste(id, "ACTIF", getOp());
            chargerTout();
            addInfo("Poste activé.");
        } catch (Exception ex) {
            addError("Erreur activation : " + ex.getMessage());
        }
    }

    public void desactiverPoste(Long id) {
        try {
            posteService.changerStatutPoste(id, "INACTIF", getOp());
            chargerTout();
            addInfo("Poste désactivé.");
        } catch (Exception ex) {
            addError("Erreur désactivation : " + ex.getMessage());
        }
    }

    public void supprimerPoste(Long id) {
        try {
            String erreur = posteService.supprimerPoste(id, getOp());
            if (erreur != null) {
                addError(erreur);
            } else {
                chargerTout();
                addInfo("Poste supprimé.");
            }
        } catch (Exception ex) {
            addError("Impossible de supprimer ce poste.");
        }
    }

    public void selectionnerPoste(Poste p) {
        this.posteSelectionne = posteService.getPosteById(p.getId());
        if (this.posteSelectionne == null) this.posteSelectionne = p;
    }

    // ══════════════════════════════════════════════════════════
    // FICHES DE POSTE
    // ══════════════════════════════════════════════════════════

    public void preparerFichePoste(Poste p) {
        this.posteSelectionne = p;
        FichePoste fp = p.getFichePoste();
        if (fp != null) {
            this.fichePoste    = posteService.getFicheById(fp.getId());
            if (this.fichePoste == null) this.fichePoste = fp;
            this.modeEditionFiche = true;
        } else {
            this.fichePoste    = new FichePoste();
            this.fichePoste.setPoste(p);
            this.modeEditionFiche = false;
        }
    }

    public String sauvegarderFichePoste() {
        try {
            if (posteSelectionne == null) {
                addError("Aucun poste sélectionné.");
                return null;
            }
            fichePoste.setPoste(posteSelectionne);

            if (modeEditionFiche) {
                posteService.modifierFichePoste(fichePoste, getOp());
                addInfo("Fiche de poste mise à jour.");
            } else {
                posteService.creerFichePoste(fichePoste, getOp());
                addInfo("Fiche de poste créée.");
            }
            chargerTout();
            fichePoste = new FichePoste();
        } catch (Exception ex) {
            addError("Erreur : " + ex.getMessage());
        }
        return null;
    }

    public void validerFichePoste(Long ficheId) {
        try {
            posteService.validerFichePoste(ficheId, getOp());
            chargerTout();
            addInfo("Fiche de poste validée.");
        } catch (Exception ex) {
            addError("Erreur validation fiche : " + ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // RECHERCHE & FILTRES
    // ══════════════════════════════════════════════════════════

    public void rechercherPostes() {
        try {
            Long instId = sessionBean.getInstitutionId();
            List<Poste> source = posteService.getPostesByInstitution(instId);

            postes = source.stream()
                .filter(p -> {
                    // Filtre texte
                    if (recherche != null && !recherche.isBlank()) {
                        String q = recherche.toLowerCase();
                        boolean matchCode     = p.getCode()     != null && p.getCode().toLowerCase().contains(q);
                        boolean matchIntitule = p.getIntitule() != null && p.getIntitule().toLowerCase().contains(q);
                        boolean matchFamille  = p.getFamille()  != null && p.getFamille().toLowerCase().contains(q);
                        if (!matchCode && !matchIntitule && !matchFamille) return false;
                    }
                    // Filtre famille
                    if (filtreFamille != null && !filtreFamille.isBlank()) {
                        if (!filtreFamille.equals(p.getFamille())) return false;
                    }
                    // Filtre statut
                    if (filtreStatut != null && !filtreStatut.isBlank()) {
                        if (!filtreStatut.equals(p.getStatut())) return false;
                    }
                    // Filtre corps
                    if (filtreCorps != null && !filtreCorps.isBlank()) {
                        if (!filtreCorps.equals(p.getCorps())) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

            postesVacants = postes.stream().filter(Poste::isVacant).collect(Collectors.toList());
            calculerStats();

        } catch (Exception ex) {
            addError("Erreur recherche : " + ex.getMessage());
        }
    }

    public void reinitialiserFiltres() {
        recherche    = null;
        filtreFamille = null;
        filtreStatut  = null;
        filtreCorps   = null;
        chargerTout();
    }

    public void toggleAfficherVacants() {
        afficherPostesVacants = !afficherPostesVacants;
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT CSV — POSTES VACANTS
    // ══════════════════════════════════════════════════════════

    public void exporterPostesVacantsCSV() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) fc.getExternalContext().getResponse();

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"postes_vacants_" + LocalDate.now() + ".csv\"");

            java.io.PrintWriter writer = response.getWriter();
            writer.write('\uFEFF');
            writer.println("Code;Intitulé;Famille;Corps;Catégorie;Effectif cible;Effectif actuel;Vacances;Statut");

            for (Poste p : postesVacants) {
                writer.println(
                    safe(p.getCode())       + ";" +
                    safe(p.getIntitule())   + ";" +
                    safe(p.getFamille())    + ";" +
                    safe(p.getCorps())      + ";" +
                    safe(p.getCategorie())  + ";" +
                    (p.getEffectifCible() != null ? p.getEffectifCible() : 0) + ";" +
                    p.getEffectifActuel()   + ";" +
                    p.getNombreVacances()   + ";" +
                    safe(p.getStatut())
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

    public String getBadgeStatutPoste(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "ACTIF"    -> "badge-success";
            case "INACTIF"  -> "badge-warning";
            case "SUPPRIME" -> "badge-danger";
            default         -> "badge-secondary";
        };
    }

    public String getBadgeStatutFiche(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "BROUILLON" -> "badge-warning";
            case "VALIDEE"   -> "badge-success";
            case "ARCHIVEE"  -> "badge-secondary";
            default          -> "badge-secondary";
        };
    }

    /** Taux d'occupation d'un poste en % (pour barre de progression). */
    public int getTauxOccupation(Poste p) {
        if (p.getEffectifCible() == null || p.getEffectifCible() == 0) return 0;
        return Math.min(100, p.getEffectifActuel() * 100 / p.getEffectifCible());
    }

    /** Renvoie les familles distinctes présentes dans la liste courante. */
    public List<String> getFamillesDistinctes() {
        if (postes == null) return new ArrayList<>();
        return postes.stream()
            .map(Poste::getFamille)
            .filter(f -> f != null && !f.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /** Renvoie les corps distincts. */
    public List<String> getCorpsDistincts() {
        if (postes == null) return new ArrayList<>();
        return postes.stream()
            .map(Poste::getCorps)
            .filter(c -> c != null && !c.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
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

    public List<Poste> getPostes()                    { return postes; }
    public List<Poste> getPostesVacants()             { return postesVacants; }

    public Poste       getPoste()                     { return poste; }
    public void        setPoste(Poste p)              { this.poste = p; }
    public FichePoste  getFichePoste()                { return fichePoste; }
    public void        setFichePoste(FichePoste fp)   { this.fichePoste = fp; }
    public Poste       getPosteSelectionne()          { return posteSelectionne; }
    public void        setPosteSelectionne(Poste p)   { this.posteSelectionne = p; }

    public boolean     isModeEditionPoste()           { return modeEditionPoste; }
    public boolean     isModeEditionFiche()           { return modeEditionFiche; }
    public boolean     isAfficherPostesVacants()      { return afficherPostesVacants; }

    public String      getRecherche()                 { return recherche; }
    public void        setRecherche(String v)         { this.recherche = v; }
    public String      getFiltreFamille()             { return filtreFamille; }
    public void        setFiltreFamille(String v)     { this.filtreFamille = v; }
    public String      getFiltreStatut()              { return filtreStatut; }
    public void        setFiltreStatut(String v)      { this.filtreStatut = v; }
    public String      getFiltreCorps()               { return filtreCorps; }
    public void        setFiltreCorps(String v)       { this.filtreCorps = v; }

    public long        getTotalPostes()               { return totalPostes; }
    public long        getPostesActifs()              { return postesActifs; }
    public long        getTotalVacances()             { return totalVacances; }
    public long        getPostesAvecFiche()           { return postesAvecFiche; }
}