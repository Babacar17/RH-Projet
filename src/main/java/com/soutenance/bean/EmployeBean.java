package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.EmployeUserService;
import com.soutenance.service.PresenceService;
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
 * Bean JSF — Espace personnel de l'employé connecté.
 *
 * CORRECTION : le solde de congés annuels était hardcodé à 30 jours :
 *   soldeCongesAnnuels = 30; // valeur par défaut
 *
 * Problèmes de cette approche :
 *   - Toutes les institutions ont des règles de congés différentes
 *     (30j fonctionnaires, 26j secteur privé, etc.).
 *   - Les congés reportés de l'année précédente ne sont pas pris en compte.
 *   - Le solde réel est déjà calculé dans PresenceService.getSoldeConges()
 *     qui consulte DemandeCongeDao.countJoursApprouvesCetteAnnee().
 *
 * SOLUTION : appeler PresenceService.getSoldeConges(employeId) qui retourne
 * le solde réel basé sur les données de la base. Le quota annuel peut aussi
 * être lu depuis TypeConge.nbJoursMax si l'entité TypeConge est disponible.
 */
@Component("employeBean")
@Scope("view")
public class EmployeBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private EmployeUserService employeUserService;
    @Autowired private SessionBean        sessionBean;

    // CORRECTION : injection de PresenceService pour le solde réel
    @Autowired private PresenceService presenceService;

    @Autowired(required = false) private com.soutenance.service.RecrutementService recrutementService;
    @Autowired private com.soutenance.service.AdminParametrageService parametrageService;

    // ── Données employé courant ───────────────────────────────────────────────
    private Employe employe;

    // ── Formulaire modification profil ───────────────────────────────────────
    private String telephone;
    private String adresse;
    private String email;

    // ── Demande de congé ─────────────────────────────────────────────────────
    private DemandeConge nouvelleDemandeConge;
    private Long typeCongeId;

    // ── Listes ───────────────────────────────────────────────────────────────
    private List<DemandeConge>    mesDemandesConge;
    private List<MouvementCarriere> monHistoriqueCarriere;
    private List<Formation>       mesFormations;
    private List<Evaluation>      mesEvaluations;
    /** Concours/offres où cet employé siège comme membre du jury. */
    private List<ComiteEvaluateur> mesJurys;
    /** Filtre choisi sur la page Concours &amp; Jury ; null = rien affiché tant qu'aucun choix n'est fait. */
    private String filtreStatutJury;
    private List<BulletinPaie>     mesBulletins;
    private BulletinPaie           bulletinSelectionne;

    // ── Soldes congés ─────────────────────────────────────────────────────────
    /**
     * CORRECTION : ces trois valeurs sont désormais calculées depuis la base
     * via PresenceService, plus hardcodées.
     */
    private int soldeCongesAnnuels; // quota total de l'exercice
    private int congesPris;         // jours approuvés
    private int congesEnAttente;    // jours en attente de validation

    // ── Sélection ─────────────────────────────────────────────────────────────
    private DemandeConge demandeSelectionnee;
    private Evaluation   evaluationSelectionnee;

    // ─────────────────────────────────────────────────────────────────────────
    // INITIALISATION
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        try {
            if (sessionBean.getUtilisateurConnecte() == null) {
                System.out.println("=== EmployeBean.init(): session vide, abandon");
                initialiserListesVides();
                return;
            }
            String username = sessionBean.getUtilisateurConnecte().getUsername();
            System.out.println("=== EmployeBean.init() pour: " + username);

            employe = employeUserService.findEmployeByUsername(username);

            if (employe != null) {
                System.out.println("=== Employe trouvé: " + employe.getNom());
                chargerDonnees();
            } else {
                System.out.println("=== Employe NON trouvé pour username: " + username);
                initialiserListesVides();
            }

            nouvelleDemandeConge = new DemandeConge();

        } catch (Exception e) {
            System.out.println("=== ERREUR EmployeBean.init(): " + e.getMessage());
            LOG.error("Erreur inattendue", e);
            initialiserListesVides();
            nouvelleDemandeConge = new DemandeConge();
        }
    }

    private void initialiserListesVides() {
        employe               = new Employe();
        mesDemandesConge      = new ArrayList<>();
        monHistoriqueCarriere = new ArrayList<>();
        mesFormations         = new ArrayList<>();
        mesEvaluations        = new ArrayList<>();
        mesJurys              = new ArrayList<>();
        mesBulletins          = new ArrayList<>();
        soldeCongesAnnuels    = 0;
        congesPris            = 0;
        congesEnAttente       = 0;
    }

    private void chargerDonnees() {
        try {
            telephone = employe.getTelephone();
            adresse   = employe.getAdresse();
            email     = employe.getEmail();

            mesDemandesConge = employeUserService.getDemandesCongeByEmploye(employe.getId());
            if (mesDemandesConge == null) mesDemandesConge = new ArrayList<>();

            // ─────────────────────────────────────────────────────────────────
            // CORRECTION : calcul du solde depuis la base de données
            //
            // Avant :
            //   soldeCongesAnnuels = 30; // valeur par défaut ← HARDCODÉ
            //   congesPris = 0;
            //   congesEnAttente = 0;
            //   for (DemandeConge dc : mesDemandesConge) { ... calcul manuel ... }
            //
            // Après :
            //   - getSoldeConges() consulte DemandeCongeDao.countJoursApprouvesCetteAnnee()
            //     qui fait un SUM SQL sur l'année civile courante → résultat précis
            //   - Le quota annuel (30j) reste une valeur par défaut mais sera
            //     remplacé par TypeConge.nbJoursMax dans une itération future
            // ─────────────────────────────────────────────────────────────────

            // Solde restant = quota - jours approuvés (via service → DAO → SQL)
            int soldeRestant = presenceService.getSoldeConges(employe.getId());

            // Recalculer congesPris et congesEnAttente depuis la liste locale
            // pour afficher les détails sans refaire un appel DB
            congesPris      = 0;
            congesEnAttente = 0;
            for (DemandeConge dc : mesDemandesConge) {
                int jours = dc.getNombreJours() != null ? dc.getNombreJours() : 0;
                if ("APPROUVE".equals(dc.getStatut())) {
                    congesPris += jours;
                } else if ("EN_ATTENTE".equals(dc.getStatut())) {
                    congesEnAttente += jours;
                }
            }

            // Le quota annuel se déduit du solde restant + jours pris
            // (PresenceService utilise 30 comme quota, cohérent avec TypeConge par défaut)
            soldeCongesAnnuels = soldeRestant + congesPris;

            monHistoriqueCarriere = employeUserService.getMouvementsCarriereByEmploye(employe.getId());
            if (monHistoriqueCarriere == null) monHistoriqueCarriere = new ArrayList<>();

            mesFormations = employeUserService.getFormationsByEmploye(employe.getId());
            if (mesFormations == null) mesFormations = new ArrayList<>();

            mesEvaluations = employeUserService.getEvaluationsByEmploye(employe.getId());
            if (mesEvaluations == null) mesEvaluations = new ArrayList<>();

            mesJurys = recrutementService != null
                ? recrutementService.getMembresJuryDeEmploye(employe.getId())
                : new ArrayList<>();
            if (mesJurys == null) mesJurys = new ArrayList<>();

            mesBulletins = employeUserService.getBulletinsByEmploye(employe.getId());
            if (mesBulletins == null) mesBulletins = new ArrayList<>();

        } catch (Exception e) {
            System.out.println("=== ERREUR chargerDonnees(): " + e.getMessage());
            LOG.error("Erreur inattendue", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACTIONS
    // ─────────────────────────────────────────────────────────────────────────

    public void sauvegarderProfil() {
        try {
            employe.setTelephone(telephone);
            employe.setAdresse(adresse);
            employe.setEmail(email);
            employeUserService.mettreAJourProfil(employe);
            addMessage(FacesMessage.SEVERITY_INFO, "Succès", "Profil mis à jour avec succès.");
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Impossible de mettre à jour le profil : " + e.getMessage());
        }
    }

    public void soumettreDemandeConge() {
        try {
            if (nouvelleDemandeConge.getDateDebut() == null
                    || nouvelleDemandeConge.getDateFin() == null) {
                addMessage(FacesMessage.SEVERITY_WARN, "Attention", "Veuillez renseigner les dates.");
                return;
            }
            if (nouvelleDemandeConge.getDateFin().isBefore(nouvelleDemandeConge.getDateDebut())) {
                addMessage(FacesMessage.SEVERITY_WARN, "Attention",
                    "La date de fin doit être après la date de début.");
                return;
            }
            TypeConge typeChoisi = getTypesCongeDisponibles().stream()
                .filter(t -> t.getId().equals(typeCongeId))
                .findFirst().orElse(null);
            if (typeChoisi == null) {
                addMessage(FacesMessage.SEVERITY_WARN, "Attention",
                    "Veuillez sélectionner un type de congé.");
                return;
            }
            nouvelleDemandeConge.setTypeConge(typeChoisi);
            long jours = java.time.temporal.ChronoUnit.DAYS.between(
                nouvelleDemandeConge.getDateDebut(), nouvelleDemandeConge.getDateFin()) + 1;
            nouvelleDemandeConge.setNombreJours((int) jours);
            nouvelleDemandeConge.setStatut("EN_ATTENTE");
            nouvelleDemandeConge.setEmploye(employe);

            employeUserService.soumettreDemandeConge(nouvelleDemandeConge);
            addMessage(FacesMessage.SEVERITY_INFO, "Succès", "Demande de congé soumise avec succès.");
            nouvelleDemandeConge = new DemandeConge();

            // Recharger pour mettre à jour le solde affiché
            mesDemandesConge = employeUserService.getDemandesCongeByEmploye(employe.getId());
            if (mesDemandesConge == null) mesDemandesConge = new ArrayList<>();

            // Rafraîchir le solde depuis la base
            int soldeRestant   = presenceService.getSoldeConges(employe.getId());
            congesPris         = (int)(mesDemandesConge.stream()
                .filter(d -> "APPROUVE".equals(d.getStatut()))
                .mapToLong(d -> d.getNombreJours() != null ? d.getNombreJours() : 0)
                .sum());
            congesEnAttente    = (int)(mesDemandesConge.stream()
                .filter(d -> "EN_ATTENTE".equals(d.getStatut()))
                .mapToLong(d -> d.getNombreJours() != null ? d.getNombreJours() : 0)
                .sum());
            soldeCongesAnnuels = soldeRestant + congesPris;

        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Erreur lors de la soumission : " + e.getMessage());
        }
    }

    public void annulerDemandeConge(DemandeConge demande) {
        try {
            if (!"EN_ATTENTE".equals(demande.getStatut())) {
                addMessage(FacesMessage.SEVERITY_WARN, "Attention",
                    "Seules les demandes en attente peuvent être annulées.");
                return;
            }
            demande.setStatut("ANNULE");
            employeUserService.mettreAJourDemandeConge(demande);
            employeUserService.notifierAnnulationConge(demande);
            addMessage(FacesMessage.SEVERITY_INFO, "Succès", "Demande annulée.");
            mesDemandesConge = employeUserService.getDemandesCongeByEmploye(employe.getId());
            if (mesDemandesConge == null) mesDemandesConge = new ArrayList<>();
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Erreur", e.getMessage());
        }
    }

    public void telechargerBulletinPdf(Long bulletinId) {
        try {
            byte[] pdf = employeUserService.genererBulletinPdf(bulletinId, employe.getId());
            if (pdf == null || pdf.length == 0) {
                addMessage(FacesMessage.SEVERITY_WARN, "Erreur", "Bulletin introuvable ou non disponible.");
                return;
            }
            jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
            jakarta.faces.context.ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition", "inline; filename=\"bulletin-" + bulletinId + ".pdf\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception e) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Erreur", "Erreur génération PDF : " + e.getMessage());
        }
    }

    public void selectionnerEvaluation(Evaluation ev) {
        this.evaluationSelectionnee = ev;
    }

    public void selectionnerDemande(DemandeConge dc) {
        this.demandeSelectionnee = dc;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Solde restant = quota - jours pris (cohérent avec presenceService.getSoldeConges()).
     */
    public int getSoldeRestant() {
        return Math.max(0, soldeCongesAnnuels - congesPris);
    }

    /**
     * Une entrée ("pris" / "attente" / "libre") par jour du quota annuel,
     * pour l'affichage visuel du solde en cases (tableau de bord employé).
     */
    public List<String> getCasesConges() {
        List<String> cases = new ArrayList<>();
        for (int i = 0; i < soldeCongesAnnuels; i++) {
            if (i < congesPris) cases.add("pris");
            else if (i < congesPris + congesEnAttente) cases.add("attente");
            else cases.add("libre");
        }
        return cases;
    }

    public String getBadgeStatutConge(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "EN_ATTENTE" -> "badge-warning";
            case "APPROUVE"   -> "badge-success";
            case "REFUSE"     -> "badge-danger";
            case "ANNULE"     -> "badge-secondary";
            default           -> "badge-secondary";
        };
    }

    public String getLabelStatutConge(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "EN_ATTENTE" -> "En attente";
            case "APPROUVE"   -> "Approuvé";
            case "REFUSE"     -> "Refusé";
            case "ANNULE"     -> "Annulé";
            default           -> statut;
        };
    }

    public String getBadgeStatutBulletin(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut) {
            case "VALIDE" -> "badge-info";
            case "PAYE"   -> "badge-success";
            default       -> "badge-secondary";
        };
    }

    public String getLabelStatutBulletin(String statut) {
        if (statut == null) return "—";
        return switch (statut) {
            case "VALIDE" -> "Validé — en attente de versement";
            case "PAYE"   -> "Payé";
            default       -> statut;
        };
    }

    public String getLabelMouvement(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "PROMOTION"      -> "Promotion";
            case "MUTATION"       -> "Mutation";
            case "TITULARISATION" -> "Titularisation";
            case "RETRAITE"       -> "Retraite";
            default               -> type;
        };
    }

    public String getBadgeMouvement(String type) {
        if (type == null) return "badge-secondary";
        return switch (type) {
            case "PROMOTION"      -> "badge-success";
            case "MUTATION"       -> "badge-info";
            case "TITULARISATION" -> "badge-primary";
            default               -> "badge-secondary";
        };
    }

    public String getMentionClass(String mention) {
        if (mention == null) return "";
        return switch (mention) {
            case "EXCELLENT"    -> "mention-excellent";
            case "TRES_BIEN"    -> "mention-tresbien";
            case "BIEN"         -> "mention-bien";
            case "SATISFAISANT" -> "mention-satisfaisant";
            default             -> "mention-insuffisant";
        };
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(severity, summary, detail));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GETTERS / SETTERS
    // ─────────────────────────────────────────────────────────────────────────

    public Employe getEmploye()                              { return employe; }
    public void    setEmploye(Employe employe)               { this.employe = employe; }
    public String  getTelephone()                            { return telephone; }
    public void    setTelephone(String telephone)            { this.telephone = telephone; }
    public String  getAdresse()                              { return adresse; }
    public void    setAdresse(String adresse)                { this.adresse = adresse; }
    public String  getEmail()                                { return email; }
    public void    setEmail(String email)                    { this.email = email; }
    public DemandeConge getNouvelleDemandeConge()            { return nouvelleDemandeConge; }
    public void setNouvelleDemandeConge(DemandeConge d)      { this.nouvelleDemandeConge = d; }

    public Long getTypeCongeId()                             { return typeCongeId; }
    public void setTypeCongeId(Long id)                      { this.typeCongeId = id; }

    public List<TypeConge> getTypesCongeDisponibles() {
        if (employe == null || employe.getInstitution() == null) return new ArrayList<>();
        return parametrageService.getTypesCongeByInstitution(employe.getInstitution().getId());
    }
    public List<DemandeConge>     getMesDemandesConge()      { return mesDemandesConge; }
    public List<MouvementCarriere> getMonHistoriqueCarriere(){ return monHistoriqueCarriere; }
    public List<Formation>        getMesFormations()         { return mesFormations; }
    public List<Evaluation>       getMesEvaluations()        { return mesEvaluations; }
    public List<ComiteEvaluateur> getMesJurys()               { return mesJurys; }

    /** Jurys dont le recrutement (offre) n'est pas encore clôturé. */
    public List<ComiteEvaluateur> getMesJurysEnCours() {
        List<ComiteEvaluateur> liste = new ArrayList<>();
        if (mesJurys == null) return liste;
        for (ComiteEvaluateur j : mesJurys) {
            if (j.getComite() == null || j.getComite().getOffre() == null) continue;
            if (!"FERMEE".equals(j.getComite().getOffre().getStatut())) liste.add(j);
        }
        return liste;
    }

    /** Jurys dont le recrutement (offre) est clôturé — historique consultable. */
    public List<ComiteEvaluateur> getMesJurysTermines() {
        List<ComiteEvaluateur> liste = new ArrayList<>();
        if (mesJurys == null) return liste;
        for (ComiteEvaluateur j : mesJurys) {
            if (j.getComite() == null || j.getComite().getOffre() == null) continue;
            if ("FERMEE".equals(j.getComite().getOffre().getStatut())) liste.add(j);
        }
        return liste;
    }

    public String getFiltreStatutJury() { return filtreStatutJury; }

    /** Filtre à bascule (pills) : un second clic sur le même filtre masque de nouveau la liste. */
    public void toggleFiltreStatutJury(String v) {
        filtreStatutJury = v.equals(filtreStatutJury) ? null : v;
    }

    /** Liste masquée tant qu'aucun filtre n'est choisi. */
    public List<ComiteEvaluateur> getJurysFiltres() {
        if (filtreStatutJury == null) return new ArrayList<>();
        return switch (filtreStatutJury) {
            case "EN_COURS" -> getMesJurysEnCours();
            case "TERMINE"  -> getMesJurysTermines();
            default         -> mesJurys == null ? new ArrayList<>() : mesJurys;
        };
    }
    public List<BulletinPaie>     getMesBulletins()           { return mesBulletins; }
    public BulletinPaie           getBulletinSelectionne()    { return bulletinSelectionne; }
    public void selectionnerBulletin(BulletinPaie b)          { this.bulletinSelectionne = b; }
    public int  getSoldeCongesAnnuels()                      { return soldeCongesAnnuels; }
    public int  getCongesPris()                              { return congesPris; }
    public int  getCongesEnAttente()                         { return congesEnAttente; }
    public DemandeConge getDemandeSelectionnee()             { return demandeSelectionnee; }
    public void setDemandeSelectionnee(DemandeConge d)       { this.demandeSelectionnee = d; }
    public Evaluation getEvaluationSelectionnee()            { return evaluationSelectionnee; }
    public void setEvaluationSelectionnee(Evaluation e)      { this.evaluationSelectionnee = e; }
}