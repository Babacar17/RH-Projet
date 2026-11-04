package com.soutenance.bean;

import com.soutenance.dao.CompetenceDao;
import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.Competence;
import com.soutenance.entity.CompetenceEmploye;
import com.soutenance.entity.Employe;
import com.soutenance.entity.LogAction;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean Skills Matrix RH.
 *
 * Gère le référentiel de compétences de l'institution + l'évaluation
 * du niveau de chaque employé sur chaque compétence (matrice).
 */
@Component("competenceBean")
@Scope("view")
public class CompetenceBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private CompetenceDao  competenceDao;
    @Autowired private EmployeDao     employeDao;
    @Autowired private LogActionDao   logActionDao;
    @Autowired private SessionBean    sessionBean;

    private List<Competence>          competences = new ArrayList<>();
    private List<Employe>             employes    = new ArrayList<>();

    // Édition compétence
    private Competence competence = new Competence();
    private boolean    modeEdition = false;

    // Édition niveau d'un employé sur une compétence
    private CompetenceEmploye lien = new CompetenceEmploye();

    @PostConstruct
    public void init() { chargerTout(); }

    public void chargerTout() {
        try {
            Long instId = sessionBean.getInstitutionId();
            if (instId == null) {
                competences = competenceDao.findAll();
                employes = employeDao.findAll();
            } else {
                competences = competenceDao.findByInstitution(instId);
                employes = employeDao.findByInstitution(instId);
            }
        } catch (Exception ignored) {
            competences = new ArrayList<>();
            employes    = new ArrayList<>();
        }
    }

    // ── Référentiel ────────────────────────────────────────────────────────

    public void preparerNouvelle() {
        competence = new Competence();
        competence.setCategorie("TECHNIQUE");
        modeEdition = false;
    }

    public void preparerEdition(Competence c) {
        competence = c;
        modeEdition = true;
    }

    public void sauvegarder() {
        try {
            if (competence.getInstitution() == null) {
                competence.setInstitution(sessionBean.getInstitutionCourante());
            }
            if (modeEdition) {
                competenceDao.update(competence);
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Compétence modifiée : " + competence.getLibelle());
            } else {
                competenceDao.save(competence);
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Compétence ajoutée : " + competence.getLibelle());
            }
            logActionDao.save(LogAction.of(getOp(),
                modeEdition ? "COMPETENCE_MODIF" : "COMPETENCE_AJOUT",
                competence.getLibelle()));
            chargerTout();
            competence = new Competence();
            modeEdition = false;
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void supprimer(Long id) {
        try {
            competenceDao.delete(id);
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimée", "Compétence supprimée.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Niveaux par employé ────────────────────────────────────────────────

    /**
     * Retourne la matrice : pour chaque employé, sa map compétence → niveau.
     * Utilisée pour rendre la grille croisée.
     */
    public Map<Long, Map<Long, Integer>> getMatrice() {
        Map<Long, Map<Long, Integer>> matrice = new LinkedHashMap<>();
        for (Employe e : employes) matrice.put(e.getId(), new LinkedHashMap<>());

        Long instId = sessionBean.getInstitutionId();
        List<CompetenceEmploye> liens = instId != null
            ? competenceDao.findLiensByInstitution(instId)
            : new ArrayList<>();
        for (CompetenceEmploye ce : liens) {
            if (ce.getEmploye() != null && ce.getCompetence() != null) {
                Map<Long, Integer> ligne = matrice.get(ce.getEmploye().getId());
                if (ligne != null) ligne.put(ce.getCompetence().getId(), ce.getNiveau());
            }
        }
        return matrice;
    }

    /** Initialise ou récupère le lien et l'ouvre dans la dialog. */
    public void ouvrirEdition(Long employeId, Long competenceId) {
        CompetenceEmploye existant = competenceDao.findCompetenceEmploye(employeId, competenceId);
        if (existant != null) { lien = existant; return; }
        lien = new CompetenceEmploye();
        lien.setEmploye(employeDao.findById(employeId));
        lien.setCompetence(competenceDao.findById(competenceId));
        lien.setNiveau(0);
        lien.setMode("MANAGER");
        lien.setEvaluateur(getOp());
        lien.setDateEvaluation(LocalDate.now());
    }

    public void sauvegarderLien() {
        try {
            lien.setEvaluateur(getOp());
            lien.setDateEvaluation(LocalDate.now());
            if (lien.getId() == null) competenceDao.saveCompetenceEmploye(lien);
            else                       competenceDao.updateCompetenceEmploye(lien);
            logActionDao.save(LogAction.of(getOp(), "COMPETENCE_EVAL",
                (lien.getEmploye() != null ? lien.getEmploye().getNomComplet() : "?")
                + " → "
                + (lien.getCompetence() != null ? lien.getCompetence().getLibelle() : "?")
                + " = " + lien.getNiveau() + "/4"));
            addMsg(FacesMessage.SEVERITY_INFO, "Niveau enregistré",
                "Évaluation mise à jour.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── Helpers UI ──────────────────────────────────────────────────────────

    public String getCouleurNiveau(Integer niveau) {
        if (niveau == null) return "#f1f5f9";
        return switch (niveau) {
            case 1  -> "#fef3c7";
            case 2  -> "#dbeafe";
            case 3  -> "#dcfce7";
            case 4  -> "#7c3aed";
            default -> "#f1f5f9";
        };
    }

    public String getLibelleNiveau(Integer niveau) {
        if (niveau == null || niveau == 0) return "—";
        return switch (niveau) {
            case 1  -> "Débutant";
            case 2  -> "Intermédiaire";
            case 3  -> "Confirmé";
            case 4  -> "Expert";
            default -> "—";
        };
    }

    public String getBadgeCategorie(String cat) {
        if (cat == null) return "#f1f5f9";
        return switch (cat.toUpperCase()) {
            case "TECHNIQUE"        -> "#dbeafe";
            case "METIER"           -> "#dcfce7";
            case "MANAGERIALE"      -> "#fef3c7";
            case "LINGUISTIQUE"     -> "#ede9fe";
            case "COMPORTEMENTALE"  -> "#fee2e2";
            default                  -> "#f1f5f9";
        };
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String t, String d) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, t, d));
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public List<Competence>      getCompetences()    { return competences; }
    public List<Employe>         getEmployes()       { return employes; }
    public Competence            getCompetence()     { return competence; }
    public void                  setCompetence(Competence c) { this.competence = c; }
    public boolean               isModeEdition()     { return modeEdition; }
    public CompetenceEmploye     getLien()           { return lien; }
    public void                  setLien(CompetenceEmploye l) { this.lien = l; }
}
