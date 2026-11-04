package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.RHService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Component("rhBean")
@Scope("view")
public class RHBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(RHBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private RHService    rhService;
    @Autowired private com.soutenance.service.RecrutementService recrutementService;
    @Autowired private SessionBean  sessionBean;
    @Autowired private com.soutenance.dao.StructureDao structureDao;
    @Autowired private com.soutenance.dao.EmployeDao   employeDao;
    @Autowired private com.soutenance.dao.PosteDao      posteDao;
    @Autowired private com.soutenance.dao.GrilleSalarialeDao grilleSalarialeDao;

    // ── Listes ─────────────────────────────────────────────────
    private List<Employe>           employes;
    private List<OffreEmploi>       offres;
    /** Statut choisi pour filtrer la table des offres ; null = rien affiché tant qu'aucun choix n'est fait. */
    private String                  filtreStatutOffre;
    private List<Candidature>       candidatures;
    /** Statut choisi pour filtrer la table des candidatures ; null = rien affiché tant qu'aucun choix n'est fait. */
    private String                  filtreStatutCandidature;
    /** Offre choisie sur la page Candidatures ; null = on affiche la liste des offres, pas encore les candidatures. */
    private OffreEmploi             offreSelectionneeCandidature;
    private List<DemandeConge>      demandesConge;
    private List<Formation>         formations;
    private List<Evaluation>        evaluations;
    private List<MouvementCarriere> mouvements;
    /** Référentiels pour le formulaire d'offre (page dédiée offre-form.xhtml). */
    private List<Poste>     postesInstitution;
    private List<com.soutenance.entity.Service> servicesInstitution;
    private List<com.soutenance.entity.Bureau>  bureauxInstitution;
    /** Id de l'offre à charger dans le formulaire (paramètre de vue ; null = nouvelle offre). */
    private Long offreFormId;
    /** Id de la demande de congé ciblée depuis une notification (paramètre de vue ; met en évidence la ligne). */
    private Long demandeCongeCibleId;

    // ── Entités en cours d'édition ─────────────────────────────
    private Employe           employe      = new Employe();
    private OffreEmploi       offre        = new OffreEmploi();
    private String            nouveauDocumentRequisLibelle;
    private boolean           nouveauDocumentRequisObligatoire = true;
    private Candidature       candidature  = new Candidature();
    private DemandeConge      demandeConge = new DemandeConge();
    private Formation         formation    = new Formation();
    private Evaluation        evaluation   = new Evaluation();
    private MouvementCarriere mouvement    = new MouvementCarriere();

    // ── UI State ───────────────────────────────────────────────
    private boolean modeEdition = false;
    private String  filtreStatut;
    private String  termeRecherche;
    private String  commentaireRefus;

    // ── Stats (dashboard) ──────────────────────────────────────
    private long totalEmployes;
    private long employesActifs;
    private long offresActives;
    private long candidaturesEnAttente;
    private long congesEnAttente;
    private long formationsActives;

    @PostConstruct
    public void init() {
        chargerTout();
    }

    /**
     * Charge toutes les listes en filtrant par institution de l'utilisateur RH.
     *
     * CORRECTION CRITIQUE (cloisonnement multi-tenant) :
     * Avant, les listes contenaient les données de toutes les institutions.
     * Un RH d'une institution pouvait voir les employés, congés, candidatures
     * et évaluations des autres institutions. Maintenant chaque liste est
     * filtrée par sessionBean.getInstitutionId() avant d'être stockée.
     *
     * Un super-admin (institutionId == null) voit toujours toutes les données.
     */
    private void chargerTout() {
        Long instId = sessionBean.getInstitutionId();

        // ── Listes filtrées ─────────────────────────────────────────────
        employes      = filtrerParInstitution(rhService.getTousEmployes(),
                            e -> e.getInstitution() != null ? e.getInstitution().getId() : null,
                            instId);
        offres        = filtrerParInstitution(rhService.getToutesOffres(),
                            o -> o.getInstitutionId(),
                            instId);
        candidatures  = filtrerParInstitution(rhService.getToutesCandidatures(),
                            c -> c.getOffre() != null ? c.getOffre().getInstitutionId() : null,
                            instId);
        demandesConge = filtrerParInstitution(rhService.getToutesDemandesConge(),
                            d -> d.getEmploye() != null
                              && d.getEmploye().getInstitution() != null
                                ? d.getEmploye().getInstitution().getId() : null,
                            instId);
        formations    = filtrerParInstitution(rhService.getToutesFormations(),
                            f -> f.getInstitutionId(),
                            instId);
        evaluations   = filtrerParInstitution(rhService.getToutesEvaluations(),
                            ev -> ev.getEmploye() != null
                               && ev.getEmploye().getInstitution() != null
                                ? ev.getEmploye().getInstitution().getId() : null,
                            instId);
        mouvements    = filtrerParInstitution(rhService.getTousMouvements(),
                            m -> m.getEmploye() != null
                              && m.getEmploye().getInstitution() != null
                                ? m.getEmploye().getInstitution().getId() : null,
                            instId);
        // ── Compteurs : dérivés des listes filtrées ─────────────────────
        totalEmployes         = employes.size();
        employesActifs        = employes.stream()
                                    .filter(e -> "ACTIF".equalsIgnoreCase(e.getStatut())).count();
        offresActives         = offres.stream()
                                    .filter(o -> "PUBLIEE".equalsIgnoreCase(o.getStatut())).count();
        candidaturesEnAttente = candidatures.stream()
                                    .filter(c -> "RECUE".equalsIgnoreCase(c.getStatut())).count();
        congesEnAttente       = demandesConge.stream()
                                    .filter(d -> "EN_ATTENTE".equalsIgnoreCase(d.getStatut())).count();
        formationsActives     = formations.stream()
                                    .filter(f -> "PLANIFIEE".equalsIgnoreCase(f.getStatut())).count();
    }

    /**
     * Helper générique : filtre une liste par institution.
     * Si instId == null (super-admin), retourne la liste complète.
     */
    private <T> List<T> filtrerParInstitution(List<T> source,
                                              java.util.function.Function<T, Long> getter,
                                              Long instId) {
        if (source == null) return new java.util.ArrayList<>();
        if (instId == null) return source; // super-admin : pas de filtrage
        return source.stream()
            .filter(item -> {
                Long id = getter.apply(item);
                return id != null && id.equals(instId);
            })
            .collect(java.util.stream.Collectors.toList());
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(sev, titre, detail));
    }

    // ══════════════════════════════════════════════════
    // EMPLOYÉS
    // ══════════════════════════════════════════════════

    private Long managerIdSelectionne;
    private Long grilleIdSelectionne;

    public Long getManagerIdSelectionne()           { return managerIdSelectionne; }
    public void setManagerIdSelectionne(Long id)    { this.managerIdSelectionne = id; }

    public Long getGrilleIdSelectionne()            { return grilleIdSelectionne; }
    public void setGrilleIdSelectionne(Long id)     { this.grilleIdSelectionne = id; }

    /** Grilles salariales actives de l'institution courante — pour le selectOneMenu de la fiche employé. */
    public List<GrilleSalariale> getGrillesSalariales() {
        Long institutionId = sessionBean.getInstitutionCourante() != null
            ? sessionBean.getInstitutionCourante().getId() : null;
        return institutionId != null
            ? grilleSalarialeDao.findActivesParInstitution(institutionId)
            : java.util.Collections.emptyList();
    }

    public void preparerNouvelEmploye() { employe = new Employe(); modeEdition = false; managerIdSelectionne = null; grilleIdSelectionne = null; }

    public void preparerEditionEmploye(Employe e) {
        this.employe = e;
        modeEdition = true;
        managerIdSelectionne = e.getManager() != null ? e.getManager().getId() : null;
        grilleIdSelectionne = e.getGrilleSalariale() != null ? e.getGrilleSalariale().getId() : null;
    }

    public String sauvegarderEmploye() {
        try {
            if (!modeEdition && employe.getInstitution() == null) {
                // Affecter l'institution du RH connecté au nouvel employé
                employe.setInstitution(sessionBean.getInstitutionCourante());
            }
            if (managerIdSelectionne != null && managerIdSelectionne.equals(employe.getId())) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Un employé ne peut pas être son propre manager.");
                return null;
            }
            employe.setManager(managerIdSelectionne != null ? rhService.getEmployeById(managerIdSelectionne) : null);
            employe.setGrilleSalariale(grilleIdSelectionne != null ? grilleSalarialeDao.findById(grilleIdSelectionne) : null);
            if (modeEdition) {
                rhService.modifierEmploye(employe, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Fiche modifiée : " + employe.getNomComplet());
            } else {
                rhService.creerEmploye(employe, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Employé créé : " + employe.getNomComplet());
            }
            chargerTout();
            employe  = new Employe();
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
        return null;
    }

    public void supprimerEmploye(Long id) {
        try { rhService.supprimerEmploye(id, getOp()); chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Employé supprimé.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    // ── Filtres avancés employés ────────────────────────────────────────────
    private String filtreTypeContrat;
    private Long   filtreDirectionId;

    public String getFiltreTypeContrat()           { return filtreTypeContrat; }
    public void   setFiltreTypeContrat(String t)   { this.filtreTypeContrat = t; }
    public Long   getFiltreDirectionId()           { return filtreDirectionId; }
    public void   setFiltreDirectionId(Long id)    { this.filtreDirectionId = id; }

    /** Vrai dès qu'une recherche/filtre a été appliqué — la liste des employés reste cachée tant que c'est faux. */
    private boolean filtresEmployesAppliques = false;
    public boolean isFiltresEmployesAppliques() { return filtresEmployesAppliques; }

    /**
     * Applique les filtres combinés recherche + statut + contrat + direction sur la liste des
     * employés déjà cloisonnée par institution — appelé en direct à chaque frappe/changement
     * (recherche live), pas via un bouton.
     */
    public void appliquerFiltresEmployes() {
        chargerTout(); // repartir d'une liste fraîche, filtrée par institution
        java.util.stream.Stream<Employe> s = employes.stream();
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            s = s.filter(e -> filtreStatut.equalsIgnoreCase(e.getStatut()));
        }
        if (filtreTypeContrat != null && !filtreTypeContrat.isBlank()) {
            s = s.filter(e -> filtreTypeContrat.equalsIgnoreCase(e.getTypeContrat()));
        }
        if (filtreDirectionId != null) {
            s = s.filter(e -> {
                if (e.getServiceOrg() == null
                 || e.getServiceOrg().getDivision() == null
                 || e.getServiceOrg().getDivision().getDirection() == null) return false;
                return filtreDirectionId.equals(
                    e.getServiceOrg().getDivision().getDirection().getId());
            });
        }
        if (termeRecherche != null && !termeRecherche.isBlank()) {
            String terme = termeRecherche.toLowerCase().trim();
            s = s.filter(e -> {
                String nom    = e.getNom()    != null ? e.getNom().toLowerCase()    : "";
                String prenom = e.getPrenom() != null ? e.getPrenom().toLowerCase() : "";
                return nom.contains(terme)
                    || prenom.contains(terme)
                    || (prenom + " " + nom).contains(terme)
                    || (nom + " " + prenom).contains(terme)
                    || (e.getMatricule() != null && e.getMatricule().toLowerCase().contains(terme))
                    || (e.getEmail()     != null && e.getEmail().toLowerCase().contains(terme));
            });
        }
        employes = s.collect(java.util.stream.Collectors.toList());
        filtresEmployesAppliques = true;
    }

    // ── Actions rapides depuis une ligne employé ────────────────────────────

    /** Pré-remplit une évaluation pour cet employé et ouvre la dialog. */
    public void preparerEvaluationPour(Employe e) {
        this.evaluation = new Evaluation();
        this.evaluation.setEmploye(e);
        this.evaluation.setEvaluateur(e.getManager() != null ? e.getManager() : e);
        this.evaluation.setTypeEvaluation("ANNUELLE");
        this.evaluation.setPeriode("Annuelle " + java.time.Year.now().getValue());
        this.evaluation.setStatut("EN_COURS");
        this.evaluation.setDatePrevue(java.time.LocalDate.now().plusDays(15));
        this.modeEdition = false;
    }

    /** Pré-sélectionne l'employé pour l'inscription rapide à une formation. */
    public void preparerInscriptionPour(Employe e) {
        this.employeAInscrireId    = e.getId();
        this.formationSelectionnee = null; // l'utilisateur choisit la formation dans la dialog
    }

    public void rechercherEmployes() {
        Long instId = sessionBean.getInstitutionId();
        List<Employe> all = (termeRecherche != null && !termeRecherche.isBlank())
                          ? rhService.rechercherEmployes(termeRecherche)
                          : rhService.getTousEmployes();
        employes = filtrerParInstitution(all,
            e -> e.getInstitution() != null ? e.getInstitution().getId() : null,
            instId);
    }

    // ══════════════════════════════════════════════════
    // OFFRES D'EMPLOI
    // ══════════════════════════════════════════════════

    public Long getOffreFormId() { return offreFormId; }
    public void setOffreFormId(Long id) { this.offreFormId = id; }

    public Long getDemandeCongeCibleId() { return demandeCongeCibleId; }
    public void setDemandeCongeCibleId(Long id) { this.demandeCongeCibleId = id; }

    public List<Poste> getPostesInstitution() { return postesInstitution; }

    public List<com.soutenance.entity.Service> getServicesInstitution() { return servicesInstitution; }

    public List<com.soutenance.entity.Bureau> getBureauxInstitution() { return bureauxInstitution; }

    /**
     * Point d'entrée de la page offre-form.xhtml (f:event type="preRenderView").
     * Charge l'offre à éditer (ou en crée une nouvelle). Le positionnement se fait
     * directement par Service (liste complète de l'institution) puis Bureau (plus
     * précis, filtré par le service choisi) : Service et Bureau portent déjà toute
     * la chaîne jusqu'à la direction (Service.division.direction, Bureau.service),
     * inutile de faire resaisir Direction/Division au RH — elles restent visibles,
     * dérivées, dans le bandeau sous les champs.
     * Sans écraser l'édition en cours lors des postbacks ajax de la cascade elle-même.
     */
    public void chargerOffreForm() {
        if (FacesContext.getCurrentInstance().isPostback()) return;

        if (offreFormId != null) {
            OffreEmploi o = rhService.getOffreById(offreFormId);
            offre = o != null ? o : new OffreEmploi();
            modeEdition = o != null;
        } else {
            offre = new OffreEmploi();
            offre.setTypeOffre("EMPLOI");
            modeEdition = false;
        }

        Long instId = sessionBean.getInstitutionId();
        postesInstitution   = instId != null ? posteDao.findByInstitution(instId) : posteDao.findAll();
        servicesInstitution = instId != null ? structureDao.findServicesByInstitution(instId) : structureDao.findAllServices();

        com.soutenance.entity.Service serviceActuel = offre.getService() != null
            ? offre.getService() : (offre.getBureau() != null ? offre.getBureau().getService() : null);
        bureauxInstitution = serviceActuel != null
            ? structureDao.findBureauxByService(serviceActuel.getId()) : new java.util.ArrayList<>();
    }

    /** Ajax : le service choisi change → recharge les bureaux de ce service, vide le bureau choisi. */
    public void onServiceOffreChange() {
        offre.setBureau(null);
        Long sId = getServiceSelectionneId();
        bureauxInstitution = sId != null ? structureDao.findBureauxByService(sId) : new java.util.ArrayList<>();
    }

    /** Poste du référentiel sélectionné pour l'offre en cours d'édition (binding par id pour le selectOneMenu). */
    public Long getPosteSelectionneId() {
        return offre.getPoste() != null ? offre.getPoste().getId() : null;
    }

    public void setPosteSelectionneId(Long id) {
        if (id == null || postesInstitution == null) { offre.setPoste(null); return; }
        postesInstitution.stream().filter(p -> p.getId().equals(id)).findFirst()
            .ifPresentOrElse(offre::setPoste, () -> offre.setPoste(null));
    }

    /** Service (unité organisationnelle) sélectionné pour l'offre en cours d'édition. */
    public Long getServiceSelectionneId() {
        return offre.getService() != null ? offre.getService().getId() : null;
    }

    public void setServiceSelectionneId(Long id) {
        if (id == null || servicesInstitution == null) { offre.setService(null); return; }
        servicesInstitution.stream().filter(s -> s.getId().equals(id)).findFirst()
            .ifPresentOrElse(offre::setService, () -> offre.setService(null));
    }

    /** Bureau précis sélectionné pour l'offre en cours d'édition (plus fin que le service, optionnel). */
    public Long getBureauSelectionneId() {
        return offre.getBureau() != null ? offre.getBureau().getId() : null;
    }

    public void setBureauSelectionneId(Long id) {
        if (id == null || bureauxInstitution == null) { offre.setBureau(null); return; }
        bureauxInstitution.stream().filter(b -> b.getId().equals(id)).findFirst()
            .ifPresentOrElse(offre::setBureau, () -> offre.setBureau(null));
    }

    /** Ajoute un document librement énuméré à la liste des documents exigés de l'offre en cours d'édition. */
    public void ajouterDocumentRequis() {
        if (nouveauDocumentRequisLibelle == null || nouveauDocumentRequisLibelle.isBlank()) return;
        offre.getDocumentsRequis().add(new com.soutenance.entity.DocumentRequisOffre(
            offre, nouveauDocumentRequisLibelle.trim(), nouveauDocumentRequisObligatoire));
        nouveauDocumentRequisLibelle = null;
        nouveauDocumentRequisObligatoire = true;
    }

    public void retirerDocumentRequis(com.soutenance.entity.DocumentRequisOffre d) {
        offre.getDocumentsRequis().remove(d);
    }

    /** Une offre doit situer le poste dans l'organigramme : un service ou, plus precis, un bureau. */
    private boolean positionnementRenseigne(OffreEmploi o) {
        return o.getService() != null || o.getBureau() != null;
    }

    private static final List<String> CONTRATS_EMPLOI = List.of("CDI", "CDD", "Convention de prestation");
    private static final List<String> CONTRATS_STAGE  = List.of("Stage académique", "Stage professionnel", "Stage de fin d'études");

    /** Options de "type de contrat" proposées, selon que l'offre en cours d'édition est EMPLOI ou STAGE. */
    public List<String> getTypesContratDisponibles() {
        return "STAGE".equals(offre.getTypeOffre()) ? CONTRATS_STAGE : CONTRATS_EMPLOI;
    }

    /** Ajax : le type d'offre change (Emploi/Stage) → le type de contrat choisi ne correspond plus, on le réinitialise. */
    public void onTypeOffreChange() {
        offre.setTypeContrat(null);
    }

    /** Bouton "Annuler" de la page dédiée offre-form.xhtml. */
    public String annulerOffreForm() { return "offres?faces-redirect=true"; }

    public String sauvegarderOffre() {
        if (!positionnementRenseigne(offre)) {
            addMsg(FacesMessage.SEVERITY_WARN, "Positionnement requis",
                "Choisissez au moins un service (ou un bureau, plus precis) pour situer ce poste dans l'organigramme.");
            return null;
        }
        try {
            if (offre.getInstitution() == null) {
                offre.setInstitution(sessionBean.getInstitutionCourante());
            }
            rhService.sauvegarderOffre(offre, getOp());
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Offre sauvegardée.");
            return "offres?faces-redirect=true";
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); return null; }
    }

    /** Bouton "Publier l'offre" de la page dédiée offre-form.xhtml — publie puis retourne à la liste. */
    public String publierOffreEtRetour() {
        if (!positionnementRenseigne(offre)) {
            addMsg(FacesMessage.SEVERITY_WARN, "Positionnement requis",
                "Choisissez au moins un service (ou un bureau, plus precis) pour situer ce poste dans l'organigramme avant de publier.");
            return null;
        }
        try {
            if (offre.getInstitution() == null) {
                offre.setInstitution(sessionBean.getInstitutionCourante());
            }
            rhService.publierOffre(offre, getOp());
            addMsg(FacesMessage.SEVERITY_INFO, "Offre publiée", offre.getTitre());
            return "offres?faces-redirect=true";
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); return null; }
    }

    /** Publication directe depuis la liste (offre déjà en brouillon) — reste sur la page, pas de navigation. */
    public void publierOffre(OffreEmploi o) {
        if (!positionnementRenseigne(o)) {
            addMsg(FacesMessage.SEVERITY_WARN, "Positionnement requis",
                "Choisissez au moins un service (ou un bureau, plus precis) pour situer ce poste dans l'organigramme avant de publier.");
            return;
        }
        try {
            if (o.getInstitution() == null) {
                o.setInstitution(sessionBean.getInstitutionCourante());
            }
            rhService.publierOffre(o, getOp()); chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Offre publiée", o.getTitre());
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    public void fermerOffre(Long id) {
        try { rhService.fermerOffre(id, getOp()); chargerTout();
              addMsg(FacesMessage.SEVERITY_WARN, "Offre fermée", "L'offre a été fermée.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    // ══════════════════════════════════════════════════
    // CANDIDATURES
    // ══════════════════════════════════════════════════

    public void preparerEditionCandidature(Candidature c) { this.candidature = c; }

    public void voirCandidature(Candidature c) { this.candidature = c; this.commentaireRefus = ""; }

    public void preparerRefus(Candidature c)   { this.candidature = c; this.commentaireRefus = ""; }

    public void refuserAvecMotif() {
        if (commentaireRefus == null || commentaireRefus.trim().isEmpty()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Motif requis", "Veuillez saisir un motif de refus.");
            return;
        }
        try { candidature.setCommentaireRH(commentaireRefus);
              rhService.refuserCandidature(candidature.getId(), commentaireRefus, getOp());
              chargerTout();
              addMsg(FacesMessage.SEVERITY_WARN, "Candidature refusée", candidature.getNomPrenomCandidat());
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    public void accepterCandidature(Long id) {
        try { rhService.changerStatutCandidature(id, "RETENUE", getOp());
              chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Candidature retenue",
                "Le candidat a été retenu. Cliquez sur « Recruter » pour créer son dossier employé.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    /**
     * Conversion finale : crée un Employe à partir du candidat retenu.
     * Appelée par le bouton « Recruter » sur les candidatures statut RETENUE.
     */
    public void recruterCandidat(Long candidatureId) {
        try {
            Employe e = rhService.recruterCandidat(candidatureId, getOp());
            chargerTout();
            if (e != null) {
                addMsg(FacesMessage.SEVERITY_INFO, "Recrutement effectué",
                    e.getNomComplet() + " ajouté à l'effectif — matricule " + e.getMatricule());
            } else {
                addMsg(FacesMessage.SEVERITY_WARN, "Recrutement impossible",
                    "Ce candidat est déjà recruté ou la candidature est invalide.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void changerStatutCandidature(Long id, String statut) {
        try { rhService.changerStatutCandidature(id, statut, getOp());
              chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Statut mis à jour", statut);
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    public void planifierEntretien() {
        try { rhService.planifierEntretien(candidature, getOp());
              chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Entretien planifié", candidature.getNomPrenomCandidat());
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    // ══════════════════════════════════════════════════
    // CONGÉS
    // ══════════════════════════════════════════════════

    public void preparerEditionConge(DemandeConge d) { this.demandeConge = d; this.commentaireRefus = ""; }

    public void approuverConge(Long id) {
        try { rhService.approuverConge(id, getOp()); chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Congé approuvé", "Demande approuvée avec succès.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    public void refuserConge(Long id) {
        try { rhService.refuserConge(id, commentaireRefus, getOp()); chargerTout();
              addMsg(FacesMessage.SEVERITY_WARN, "Congé refusé", "Demande refusée.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    // ══════════════════════════════════════════════════
    // FORMATIONS
    // ══════════════════════════════════════════════════

    public void preparerNouvelleFormation() { formation = new Formation(); modeEdition = false; }

    public void preparerEditionFormation(Formation f) { this.formation = f; modeEdition = true; }

    // ── Inscription d'employés à une formation ──────────────────────────
    private Formation formationSelectionnee;
    private Long      employeAInscrireId;

    public Formation getFormationSelectionnee()                  { return formationSelectionnee; }
    public void setFormationSelectionnee(Formation f)            { this.formationSelectionnee = f; }
    public Long getEmployeAInscrireId()                          { return employeAInscrireId; }
    public void setEmployeAInscrireId(Long id)                   { this.employeAInscrireId = id; }

    /** Binding ID (utilisé par le selectOneMenu de la dialog rapide). */
    public Long getFormationSelectionneeId() {
        return formationSelectionnee != null ? formationSelectionnee.getId() : null;
    }
    public void setFormationSelectionneeId(Long id) {
        if (id == null) { this.formationSelectionnee = null; return; }
        this.formationSelectionnee = formations.stream()
            .filter(f -> id.equals(f.getId()))
            .findFirst().orElse(null);
    }

    /** Prépare la dialog d'inscription pour la formation sélectionnée. */
    public void preparerInscriptionFormation(Formation f) {
        this.formationSelectionnee = f;
        this.employeAInscrireId    = null;
    }

    /** Inscrit l'employé choisi à la formation sélectionnée. */
    public void inscrireEmployeFormation() {
        if (formationSelectionnee == null || employeAInscrireId == null) {
            addMsg(FacesMessage.SEVERITY_WARN, "Sélection requise",
                "Choisissez une formation et un employé.");
            return;
        }
        try {
            rhService.inscrireEmploye(formationSelectionnee.getId(),
                employeAInscrireId, getOp());
            chargerTout();
            // Recharger la formation pour rafraîchir la liste des participants
            this.formationSelectionnee = formations.stream()
                .filter(f -> f.getId().equals(formationSelectionnee.getId()))
                .findFirst()
                .orElse(formationSelectionnee);
            this.employeAInscrireId = null;
            addMsg(FacesMessage.SEVERITY_INFO, "Inscription enregistrée",
                "L'employé a été ajouté à la formation.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public String sauvegarderFormation() {
        try {
            if (!modeEdition && formation.getInstitution() == null) {
                formation.setInstitution(sessionBean.getInstitutionCourante());
            }
            if (modeEdition) rhService.modifierFormation(formation, getOp());
            else             rhService.creerFormation(formation, getOp());
            chargerTout(); formation = new Formation();
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Formation sauvegardée.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
        return null;
    }

    // ══════════════════════════════════════════════════
    // ÉVALUATIONS
    // ══════════════════════════════════════════════════

    public void preparerNouvelleEvaluation() { evaluation = new Evaluation(); modeEdition = false; }

    public void preparerEditionEvaluation(Evaluation e) { this.evaluation = e; modeEdition = true; }

    public String sauvegarderEvaluation() {
        try { rhService.sauvegarderEvaluation(evaluation, getOp());
              chargerTout(); evaluation = new Evaluation();
              addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Évaluation sauvegardée.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
        return null;
    }

    public void validerEvaluation(Long id) {
        try { rhService.validerEvaluation(id, getOp()); chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Évaluation validée", "Validée avec succès.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    // ══════════════════════════════════════════════════
    // CARRIÈRES
    // ══════════════════════════════════════════════════

    public void preparerNouveauMouvement() { mouvement = new MouvementCarriere(); modeEdition = false; }

    public void preparerNouveauMouvementPour(Employe e) {
        mouvement = new MouvementCarriere();
        mouvement.setEmploye(e);
        // FIX : e.getPoste() retourne Poste, setAncienPoste() attend String
        //       → conversion via toString()
        mouvement.setAncienPoste(e.getPoste() != null ? e.getPoste().toString() : null);
        mouvement.setAncienDepartement(e.getNomDirection());
        mouvement.setAncienSalaire(e.getSalaireBase());
        mouvement.setAncienGrade(e.getGradeEchelon());
        modeEdition = false;
    }

    public String sauvegarderMouvement() {
        try { rhService.enregistrerMouvement(mouvement, getOp());
              chargerTout(); mouvement = new MouvementCarriere();
              addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Mouvement enregistré.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
        return null;
    }

    public void approuverMouvement(Long id) {
        try { rhService.approuverMouvement(id, getOp()); chargerTout();
              addMsg(FacesMessage.SEVERITY_INFO, "Mouvement approuvé", "Le mouvement a été appliqué.");
        } catch (Exception ex) { addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage()); }
    }

    // ══════════════════════════════════════════════════
    // FILTRES
    // ══════════════════════════════════════════════════

    // Tous les filtres rechargent d'abord les listes filtrées par institution
    // (chargerTout), puis appliquent éventuellement le filtre statut local.

    public void filtrerConges() {
        chargerTout();
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            if ("EN_RETARD".equals(filtreStatut)) {
                demandesConge = demandesConge.stream()
                    .filter(this::isCongeEnRetard)
                    .collect(java.util.stream.Collectors.toList());
            } else {
                demandesConge = demandesConge.stream()
                    .filter(d -> filtreStatut.equals(d.getStatut() != null ? d.getStatut().toString() : ""))
                    .collect(java.util.stream.Collectors.toList());
            }
        }
    }

    /** Appelé depuis les cartes KPI de la page Congés : applique le filtre correspondant et recharge la liste. */
    public void filtrerParStatutDepuisKpi(String statut) {
        this.filtreStatut = statut;
        filtrerConges();
    }

    public void filtrerOffres() {
        chargerTout();
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            offres = offres.stream()
                .filter(o -> filtreStatut.equals(o.getStatut() != null ? o.getStatut().toString() : ""))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    public void filtrerEvaluations() {
        chargerTout();
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            evaluations = evaluations.stream()
                .filter(e -> filtreStatut.equals(e.getStatut() != null ? e.getStatut().toString() : ""))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    public void filtrerMouvements() {
        chargerTout();
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            mouvements = mouvements.stream()
                .filter(m -> filtreStatut.equals(m.getTypeMouvement() != null ? m.getTypeMouvement().toString() : ""))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    public void filtrerEmployesParStatut() {
        chargerTout();
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            employes = employes.stream()
                .filter(e -> filtreStatut.equals(e.getStatut() != null ? e.getStatut().toString() : ""))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    // ══════════════════════════════════════════════════
    // CONGÉS ENRICHIS
    // ══════════════════════════════════════════════════

    public long getCongesApprouves() {
        if (demandesConge == null) return 0;
        return demandesConge.stream()
               .filter(d -> "APPROUVE".equals(d.getStatut() != null ? d.getStatut().toString() : ""))
               .count();
    }

    public long getCongesRefuses() {
        if (demandesConge == null) return 0;
        return demandesConge.stream()
               .filter(d -> "REFUSE".equals(d.getStatut() != null ? d.getStatut().toString() : ""))
               .count();
    }

    /** Vrai si la demande n'a pas encore de décision finale (manager ou RH) alors que sa période de congé est déjà terminée. */
    public boolean isCongeEnRetard(DemandeConge d) {
        if (d == null || d.getStatut() == null || d.getDateFin() == null) return false;
        String s = d.getStatut();
        boolean enAttente = "EN_ATTENTE".equalsIgnoreCase(s) || "EN_VALIDATION_MANAGER".equalsIgnoreCase(s)
            || "EN_VALIDATION_RH".equalsIgnoreCase(s);
        return enAttente && d.getDateFin().isBefore(java.time.LocalDate.now());
    }

    public long getCongesEnRetard() {
        if (demandesConge == null) return 0;
        return demandesConge.stream().filter(this::isCongeEnRetard).count();
    }

    /**
     * Demandes à traiter par le RH = celles déjà validées par le manager
     * (statut EN_ATTENTE_RH) + les anciennes (statut EN_ATTENTE) qui n'ont
     * pas le workflow 2 niveaux initialisé.
     */
    public List<DemandeConge> getDemandesCongeEnAttente() {
        if (demandesConge == null) return java.util.Collections.emptyList();
        return demandesConge.stream()
               .filter(d -> {
                   String s = d.getStatut() != null ? d.getStatut() : "";
                   return "EN_ATTENTE_RH".equalsIgnoreCase(s)
                       || "EN_ATTENTE".equalsIgnoreCase(s);
               })
               .collect(java.util.stream.Collectors.toList());
    }

    // ══════════════════════════════════════════════════
    // SALAIRES
    // ══════════════════════════════════════════════════

    public java.math.BigDecimal getMasseSalariale() {
        if (employes == null) return java.math.BigDecimal.ZERO;
        return employes.stream()
               .filter(e -> e.getSalaireBase() != null
                   && "ACTIF".equals(e.getStatut() != null ? e.getStatut().toString() : ""))
               .map(Employe::getSalaireBase)
               .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    public java.math.BigDecimal getSalaireMoyen() {
        if (employes == null || employes.isEmpty()) return java.math.BigDecimal.ZERO;
        long actifs = employes.stream()
            .filter(e -> e.getSalaireBase() != null
                && "ACTIF".equals(e.getStatut() != null ? e.getStatut().toString() : ""))
            .count();
        if (actifs == 0) return java.math.BigDecimal.ZERO;
        return getMasseSalariale().divide(java.math.BigDecimal.valueOf(actifs),
               0, java.math.RoundingMode.HALF_UP);
    }

    public java.math.BigDecimal getSalaireMax() {
        if (employes == null) return java.math.BigDecimal.ZERO;
        return employes.stream()
               .filter(e -> e.getSalaireBase() != null)
               .map(Employe::getSalaireBase)
               .max(java.math.BigDecimal::compareTo)
               .orElse(java.math.BigDecimal.ZERO);
    }

    public void selectionnerEmployePourSalaire(Employe e) {
        this.employe = e; this.modeEdition = true;
    }

    public long countCandidaturesParOffre(Long offreId) {
        try { return rhService.countCandidaturesParOffre(offreId); }
        catch (Exception e) { return 0; }
    }

    // ══════════════════════════════════════════════════
    // DASHBOARD ENRICHI — Données pour les graphiques
    // ══════════════════════════════════════════════════

    /** Effectif actif réparti par direction (top 6) — bar chart. */
    public java.util.Map<String, Long> getEffectifParDirection() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        if (employes == null) return out;
        employes.stream()
            .filter(e -> "ACTIF".equalsIgnoreCase(e.getStatut()))
            .collect(java.util.stream.Collectors.groupingBy(
                e -> {
                    String d = e.getNomDirection();
                    return (d != null && !d.isBlank()) ? d : "Non rattaché";
                },
                java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(6)
            .forEachOrdered(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** Statuts des candidatures pour doughnut chart. */
    public java.util.Map<String, Long> getRepartitionCandidatures() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        if (candidatures == null) return out;
        candidatures.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                c -> c.getStatut() != null ? c.getStatut() : "—",
                java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
            .forEachOrdered(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** Évolution des embauches sur 12 mois — line chart. */
    public java.util.Map<String, Long> getEvolutionEmbauches12Mois() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter fmt =
            java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");
        for (int i = 11; i >= 0; i--) out.put(now.minusMonths(i).format(fmt), 0L);
        if (employes == null) return out;
        for (Employe e : employes) {
            if (e.getDateEmbauche() == null) continue;
            if (e.getDateEmbauche().isBefore(now.minusMonths(12))) continue;
            String key = e.getDateEmbauche().format(fmt);
            out.computeIfPresent(key, (k, v) -> v + 1);
        }
        return out;
    }

    /** Pyramide des âges par tranches (line/bar chart). */
    public java.util.Map<String, Long> getPyramideAges() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        out.put("< 25",   0L);
        out.put("25-34",  0L);
        out.put("35-44",  0L);
        out.put("45-54",  0L);
        out.put("55-64",  0L);
        out.put(">= 65",  0L);
        if (employes == null) return out;
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Employe e : employes) {
            if (e.getDateNaissance() == null) continue;
            int age = java.time.Period.between(e.getDateNaissance(), today).getYears();
            String key;
            if      (age < 25) key = "< 25";
            else if (age < 35) key = "25-34";
            else if (age < 45) key = "35-44";
            else if (age < 55) key = "45-54";
            else if (age < 65) key = "55-64";
            else               key = ">= 65";
            out.computeIfPresent(key, (k, v) -> v + 1);
        }
        return out;
    }

    /** Sexe (H/F) pour mini pie chart. */
    public java.util.Map<String, Long> getRepartitionSexe() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        if (employes == null) return out;
        employes.stream()
            .filter(e -> "ACTIF".equalsIgnoreCase(e.getStatut()))
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getSexe() != null ? e.getSexe() : "?",
                java.util.stream.Collectors.counting()))
            .forEach(out::put);
        return out;
    }

    /**
     * Candidatures regroupées par statut, pour le pipeline Kanban.
     * Retourne une Map ou la clé est le statut et la valeur la liste.
     * Toujours retourne les 5 colonnes même vides.
     */
    public java.util.Map<String, List<Candidature>> getCandidaturesParStatut() {
        java.util.LinkedHashMap<String, List<Candidature>> out =
            new java.util.LinkedHashMap<>();
        out.put("RECUE",     new java.util.ArrayList<>());
        out.put("EN_COURS",  new java.util.ArrayList<>());
        out.put("ENTRETIEN", new java.util.ArrayList<>());
        out.put("RETENUE",   new java.util.ArrayList<>());
        out.put("RECRUTEE",  new java.util.ArrayList<>());
        if (candidatures == null) return out;
        for (Candidature c : candidatures) {
            String s = c.getStatut() != null ? c.getStatut().toUpperCase() : "RECUE";
            if (out.containsKey(s)) out.get(s).add(c);
        }
        return out;
    }

    /** Taux de transformation candidatures → recrutés. */
    public int getTauxTransformation() {
        if (candidatures == null || candidatures.isEmpty()) return 0;
        long recrutes = candidatures.stream()
            .filter(c -> "RECRUTEE".equalsIgnoreCase(c.getStatut())).count();
        return (int) Math.round(100.0 * recrutes / candidatures.size());
    }

    // ══════════════════════════════════════════════════
    // EXPORTS CSV
    // ══════════════════════════════════════════════════

    private void telechargerCsv(String nom, String contenu) {
        try {
            jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
            jakarta.faces.context.ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("text/csv; charset=UTF-8");
            ec.setResponseHeader("Content-Disposition",
                "attachment; filename=\"" + nom + "\"");
            // BOM UTF-8 pour Excel
            byte[] bom = new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF};
            byte[] data = contenu.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ec.setResponseContentLength(bom.length + data.length);
            ec.getResponseOutputStream().write(bom);
            ec.getResponseOutputStream().write(data);
            fc.responseComplete();
        } catch (java.io.IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Export", ex.getMessage());
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n");
        String v = s.replace("\"", "\"\"");
        return needQuote ? "\"" + v + "\"" : v;
    }

    /** Ouvre la fiche PDF de l'offre dans un nouvel onglet (lecture + téléchargement natif du navigateur). */
    public void voirOffrePdf(Long offreId) {
        try {
            byte[] pdf = rhService.genererOffrePdf(offreId);
            if (pdf == null || pdf.length == 0) {
                addMsg(FacesMessage.SEVERITY_WARN, "Erreur",
                    "Impossible de générer le PDF de l'offre.");
                return;
            }
            jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
            jakarta.faces.context.ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition",
                "inline; filename=\"offre-" + offreId + ".pdf\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Télécharge le contrat PDF de l'employé sélectionné. */
    public void telechargerContratEmploye(Long employeId) {
        try {
            byte[] pdf = rhService.genererContratPdf(employeId, getOp());
            if (pdf == null || pdf.length == 0) {
                addMsg(FacesMessage.SEVERITY_WARN, "Erreur",
                    "Impossible de générer le contrat. Vérifiez le dossier employé.");
                return;
            }
            jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
            jakarta.faces.context.ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition",
                "attachment; filename=\"contrat-employe-" + employeId + ".pdf\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void exporterEmployesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Matricule,Nom,Prénom,Email,Téléphone,Poste,Direction,Date embauche,Statut,Type contrat,Salaire base\n");
        for (Employe e : (employes != null ? employes : java.util.List.<Employe>of())) {
            sb.append(csv(e.getMatricule())).append(',')
              .append(csv(e.getNom())).append(',')
              .append(csv(e.getPrenom())).append(',')
              .append(csv(e.getEmail())).append(',')
              .append(csv(e.getTelephone())).append(',')
              .append(csv(e.getPoste() != null ? e.getPoste().toString() : "")).append(',')
              .append(csv(e.getNomDirection())).append(',')
              .append(e.getDateEmbauche() != null ? e.getDateEmbauche().toString() : "").append(',')
              .append(csv(e.getStatut())).append(',')
              .append(csv(e.getTypeContrat())).append(',')
              .append(e.getSalaireBase() != null ? e.getSalaireBase().toPlainString() : "")
              .append('\n');
        }
        telechargerCsv("employes-" + java.time.LocalDate.now() + ".csv", sb.toString());
    }

    public void exporterCandidaturesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nom,Prénom,Email,Téléphone,Offre,Statut,Date candidature,Date entretien,Note,Commentaire RH\n");
        for (Candidature c : (candidatures != null ? candidatures : java.util.List.<Candidature>of())) {
            sb.append(csv(c.getNomCandidat())).append(',')
              .append(csv(c.getPrenomCandidat())).append(',')
              .append(csv(c.getEmailCandidat())).append(',')
              .append(csv(c.getTelephoneCandidat())).append(',')
              .append(csv(c.getOffre() != null ? c.getOffre().getTitre() : "")).append(',')
              .append(csv(c.getStatut())).append(',')
              .append(c.getDateCandidature() != null ? c.getDateCandidature().toString() : "").append(',')
              .append(c.getDateEntretien() != null ? c.getDateEntretien().toString() : "").append(',')
              .append(c.getNoteEntretien() != null ? c.getNoteEntretien().toString() : "").append(',')
              .append(csv(c.getCommentaireRH()))
              .append('\n');
        }
        telechargerCsv("candidatures-" + java.time.LocalDate.now() + ".csv", sb.toString());
    }

    public void exporterCongesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Employé,Type,Date début,Date fin,Nombre jours,Statut,Date demande,Motif\n");
        for (DemandeConge d : (demandesConge != null ? demandesConge : java.util.List.<DemandeConge>of())) {
            sb.append(csv(d.getEmploye() != null ? d.getEmploye().getNomComplet() : "")).append(',')
              .append(csv(d.getTypeConge() != null ? d.getTypeConge().getLibelle() : "")).append(',')
              .append(d.getDateDebut() != null ? d.getDateDebut().toString() : "").append(',')
              .append(d.getDateFin()   != null ? d.getDateFin().toString()   : "").append(',')
              .append(d.getNombreJours() != null ? d.getNombreJours().toString() : "").append(',')
              .append(csv(d.getStatut())).append(',')
              .append(d.getDateDemande() != null ? d.getDateDemande().toString() : "").append(',')
              .append(csv(d.getMotif()))
              .append('\n');
        }
        telechargerCsv("conges-" + java.time.LocalDate.now() + ".csv", sb.toString());
    }

    // ══════════════════════════════════════════════════
    // MASS ACTIONS
    // ══════════════════════════════════════════════════

    /**
     * Map id → booléen indiquant si la ligne est cochée.
     * Utilisée par le selectBooleanCheckbox de la colonne sélection.
     */
    private java.util.Map<Long, Boolean> congeCoche = new java.util.HashMap<>();

    public java.util.Map<Long, Boolean> getCongeCoche()       { return congeCoche; }
    public void setCongeCoche(java.util.Map<Long, Boolean> m) { this.congeCoche = m; }

    /** Retourne les ids actuellement cochés (Boolean.TRUE). */
    private java.util.List<Long> idsCoches() {
        java.util.List<Long> out = new java.util.ArrayList<>();
        if (congeCoche == null) return out;
        for (java.util.Map.Entry<Long, Boolean> e : congeCoche.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) out.add(e.getKey());
        }
        return out;
    }

    /** Approuve en lot les congés cochés. */
    public void approuverCongesEnMasse() {
        java.util.List<Long> ids = idsCoches();
        if (ids.isEmpty()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Aucune sélection",
                "Cochez au moins une demande à approuver.");
            return;
        }
        int ok = 0, ko = 0;
        for (Long id : ids) {
            try { rhService.approuverConge(id, getOp()); ok++; }
            catch (Exception ex) { ko++; }
        }
        congeCoche.clear();
        chargerTout();
        addMsg(FacesMessage.SEVERITY_INFO, "Validation en lot",
            ok + " demande(s) approuvée(s)" + (ko > 0 ? ", " + ko + " erreur(s)." : "."));
    }

    /** Refuse en lot les congés cochés (motif générique). */
    public void refuserCongesEnMasse() {
        java.util.List<Long> ids = idsCoches();
        if (ids.isEmpty()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Aucune sélection",
                "Cochez au moins une demande à refuser.");
            return;
        }
        String motif = (commentaireRefus != null && !commentaireRefus.isBlank())
                      ? commentaireRefus : "Refus en lot par le service RH";
        int ok = 0;
        for (Long id : ids) {
            try { rhService.refuserConge(id, motif, getOp()); ok++; }
            catch (Exception ignored) {}
        }
        congeCoche.clear();
        chargerTout();
        addMsg(FacesMessage.SEVERITY_WARN, "Refus en lot", ok + " demande(s) refusée(s).");
    }

    // ══════════════════════════════════════════════════
    // CAMPAGNE D'ÉVALUATION
    // ══════════════════════════════════════════════════

    private String campagnePeriode  = "Annuelle " + java.time.Year.now().getValue();
    private String campagneType     = "ANNUELLE";
    private Long   campagneDirectionId; // null = toute l'institution

    public String getCampagnePeriode()              { return campagnePeriode; }
    public void   setCampagnePeriode(String p)      { this.campagnePeriode = p; }
    public String getCampagneType()                 { return campagneType; }
    public void   setCampagneType(String t)         { this.campagneType = t; }
    public Long   getCampagneDirectionId()          { return campagneDirectionId; }
    public void   setCampagneDirectionId(Long id)   { this.campagneDirectionId = id; }

    /**
     * Lance une campagne d'évaluation : crée une évaluation EN_COURS
     * pour chaque employé actif de la direction choisie (ou tous si null).
     */
    public void lancerCampagneEvaluation() {
        if (employes == null || employes.isEmpty()) {
            addMsg(FacesMessage.SEVERITY_WARN, "Aucun employé",
                "Pas d'employé à évaluer dans le périmètre courant.");
            return;
        }
        int created = 0;
        for (Employe emp : employes) {
            if (!"ACTIF".equalsIgnoreCase(emp.getStatut())) continue;
            if (campagneDirectionId != null) {
                Long dirId = (emp.getServiceOrg() != null
                           && emp.getServiceOrg().getDivision() != null
                           && emp.getServiceOrg().getDivision().getDirection() != null)
                              ? emp.getServiceOrg().getDivision().getDirection().getId()
                              : null;
                if (!campagneDirectionId.equals(dirId)) continue;
            }
            try {
                Evaluation e = new Evaluation();
                e.setEmploye(emp);
                e.setEvaluateur(emp.getManager() != null ? emp.getManager() : emp);
                e.setTypeEvaluation(campagneType);
                e.setPeriode(campagnePeriode);
                e.setStatut("EN_COURS");
                e.setDatePrevue(java.time.LocalDate.now().plusDays(15));
                rhService.lancerEvaluation(e, getOp());
                created++;
            } catch (Exception ignored) {}
        }
        chargerTout();
        addMsg(FacesMessage.SEVERITY_INFO, "Campagne lancée",
            created + " évaluation(s) " + campagneType + " créée(s) pour la période « " + campagnePeriode + " ».");
    }

    // ══════════════════════════════════════════════════
    // ORGANIGRAMME — vue hiérarchique complète
    // ══════════════════════════════════════════════════

    public List<com.soutenance.entity.Direction> getDirections() {
        try { return structureDao.findAllDirections(); }
        catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public List<com.soutenance.entity.Division> getDivisionsByDirection(Long dirId) {
        try { return structureDao.findDivisionsByDirection(dirId); }
        catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public List<com.soutenance.entity.Service> getServicesByDivision(Long divId) {
        try { return structureDao.findServicesByDivision(divId); }
        catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public List<com.soutenance.entity.Bureau> getBureauxByService(Long srvId) {
        try { return structureDao.findBureauxByService(srvId); }
        catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    // ── Compteurs globaux ──────────────────────────────────────
    public long getTotalDirections() {
        try { return structureDao.countDirections(); } catch (Exception e) { return 0; }
    }
    public long getTotalDivisions() {
        try { return structureDao.findAllDivisions().size(); } catch (Exception e) { return 0; }
    }
    public long getTotalServices() {
        try { return structureDao.findAllServices().size(); } catch (Exception e) { return 0; }
    }
    public long getTotalBureaux() {
        try { return structureDao.findAllBureaux().size(); } catch (Exception e) { return 0; }
    }

    // ── Compteurs par Direction ────────────────────────────────
    public long countDivisionsByDirection(Long dirId) {
        try { return structureDao.countDivisionsByDirection(dirId); } catch (Exception e) { return 0; }
    }
    public long countServicesByDirection(Long dirId) {
        try { return structureDao.countServicesByDirection(dirId); } catch (Exception e) { return 0; }
    }
    public long countBureauxByDirection(Long dirId) {
        try { return structureDao.countBureauxByDirection(dirId); } catch (Exception e) { return 0; }
    }
    public long countEmployesByDirection(Long dirId) {
        try { return structureDao.countEmployesByDirection(dirId); } catch (Exception e) { return 0; }
    }

    // ── Compteurs par Division ─────────────────────────────────
    public long countServicesByDivision(Long divId) {
        try { return structureDao.countServicesByDivision(divId); } catch (Exception e) { return 0; }
    }
    public long countBureauxByDivision(Long divId) {
        try { return structureDao.countBureauxByDivision(divId); } catch (Exception e) { return 0; }
    }
    public long countEmployesByDivision(Long divId) {
        try { return structureDao.countEmployesByDivision(divId); } catch (Exception e) { return 0; }
    }

    // ── Compteurs par Service ──────────────────────────────────
    public long countBureauxByService(Long srvId) {
        try { return structureDao.countBureauxByService(srvId); } catch (Exception e) { return 0; }
    }
    public long countEmployesByService(Long srvId) {
        try { return structureDao.countEmployesByService(srvId); } catch (Exception e) { return 0; }
    }

    // ── Compteurs par Bureau ───────────────────────────────────
    public long countEmployesByBureau(Long burId) {
        try { return structureDao.countEmployesByBureau(burId); } catch (Exception e) { return 0; }
    }

    // ══════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ══════════════════════════════════════════════════

    // ── Dates minimales pour les datePickers ─────────────────────────────────

    /** Aujourd'hui — date minimum pour datePublication */
    public java.util.Date getAujourdhuiDate() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Demain — date minimum pour dateLimiteFermeture */
    public java.util.Date getDemainDate() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public List<Employe>           getEmployes()      { return employes; }
    public List<OffreEmploi>       getOffres()        { return offres; }

    public String getFiltreStatutOffre() { return filtreStatutOffre; }
    public void   setFiltreStatutOffre(String v) { this.filtreStatutOffre = v; }

    /** Reclique sur le statut déjà actif → revient à l'état initial (table masquée). */
    public void toggleFiltreStatutOffre(String v) {
        filtreStatutOffre = v.equals(filtreStatutOffre) ? null : v;
    }

    /** Table masquée tant qu'aucun statut n'est choisi ; "TOUTES" affiche tout. */
    public List<OffreEmploi> getOffresFiltrees() {
        if (filtreStatutOffre == null || offres == null) return new java.util.ArrayList<>();
        if ("TOUTES".equals(filtreStatutOffre)) return offres;
        if ("EN_ATTENTE".equals(filtreStatutOffre)) return offres.stream()
            .filter(OffreEmploi::isEnAttenteOuverture)
            .collect(java.util.stream.Collectors.toList());
        return offres.stream()
            .filter(o -> filtreStatutOffre.equalsIgnoreCase(o.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    public long getNbOffresEnAttenteOuverture() {
        if (offres == null) return 0;
        return offres.stream().filter(OffreEmploi::isEnAttenteOuverture).count();
    }

    public long getNbOffresParStatut(String statut) {
        if (offres == null) return 0;
        return offres.stream().filter(o -> statut.equalsIgnoreCase(o.getStatut())).count();
    }
    public Integer getPointsCandidature(Candidature c) {
        return c == null ? null : recrutementService.getPointsCandidature(c.getId());
    }

    public Integer getPointsMaxCandidature(Candidature c) {
        return c == null ? null : recrutementService.getPointsMaxCandidature(c.getId());
    }

    public PhaseOffre getPhaseActuelleOffre(Long offreId) {
        if (offreId == null) return null;
        List<PhaseOffre> phases = recrutementService.getPhasesOffre(offreId);
        return (phases == null || phases.isEmpty()) ? null : phases.get(phases.size() - 1);
    }

    public List<Candidature>       getCandidatures()  { return candidatures; }

    public String getFiltreStatutCandidature() { return filtreStatutCandidature; }
    public void   setFiltreStatutCandidature(String v) { this.filtreStatutCandidature = v; }

    public OffreEmploi getOffreSelectionneeCandidature() { return offreSelectionneeCandidature; }

    public Long getOffreSelectionneeCandidatureId() {
        return offreSelectionneeCandidature != null ? offreSelectionneeCandidature.getId() : null;
    }

    public void setOffreSelectionneeCandidatureId(Long id) {
        selectionnerOffreCandidature(id == null ? null :
            offres.stream().filter(o -> o.getId().equals(id)).findFirst().orElse(null));
    }

    /** Choix d'une offre sur la page Candidatures — bascule de la liste des offres vers ses candidatures. */
    public void selectionnerOffreCandidature(OffreEmploi o) {
        this.offreSelectionneeCandidature = o;
        this.filtreStatutCandidature = null;
    }

    public void retourListeOffresCandidature() {
        this.offreSelectionneeCandidature = null;
        this.filtreStatutCandidature = null;
    }

    /** Candidatures de l'offre actuellement choisie sur la page Candidatures (avant filtre de statut). */
    private List<Candidature> getCandidaturesDeOffreSelectionnee() {
        if (offreSelectionneeCandidature == null || candidatures == null) return new java.util.ArrayList<>();
        return candidatures.stream()
            .filter(c -> c.getOffre() != null && c.getOffre().getId().equals(offreSelectionneeCandidature.getId()))
            .collect(java.util.stream.Collectors.toList());
    }

    /** Reclique sur le statut déjà actif → revient à l'état initial (table masquée). */
    public void toggleFiltreStatutCandidature(String v) {
        filtreStatutCandidature = v.equals(filtreStatutCandidature) ? null : v;
    }

    /** Table masquée tant qu'aucun statut n'est choisi ; "TOUTES" affiche tout. */
    public List<Candidature> getCandidaturesFiltrees() {
        List<Candidature> base = getCandidaturesDeOffreSelectionnee();
        if (filtreStatutCandidature == null) return new java.util.ArrayList<>();
        if ("TOUTES".equals(filtreStatutCandidature)) return base;
        return base.stream()
            .filter(c -> filtreStatutCandidature.equalsIgnoreCase(c.getStatut()))
            .collect(java.util.stream.Collectors.toList());
    }

    public long getNbCandidaturesParStatut(String statut) {
        List<Candidature> base = getCandidaturesDeOffreSelectionnee();
        return base.stream().filter(c -> statut.equalsIgnoreCase(c.getStatut())).count();
    }

    public int getNbCandidaturesOffreSelectionnee() {
        return getCandidaturesDeOffreSelectionnee().size();
    }
    public List<DemandeConge>      getDemandesConge() { return demandesConge; }
    public List<Formation>         getFormations()    { return formations; }
    public List<Evaluation>        getEvaluations()   { return evaluations; }
    public List<MouvementCarriere> getMouvements()    { return mouvements; }

    public Employe           getEmploye()      { return employe; }
    public void              setEmploye(Employe e) { this.employe = e; }
    public OffreEmploi       getOffre()        { return offre; }
    public void              setOffre(OffreEmploi o) { this.offre = o; }
    public String            getNouveauDocumentRequisLibelle()      { return nouveauDocumentRequisLibelle; }
    public void              setNouveauDocumentRequisLibelle(String v) { this.nouveauDocumentRequisLibelle = v; }
    public boolean           isNouveauDocumentRequisObligatoire()   { return nouveauDocumentRequisObligatoire; }
    public void              setNouveauDocumentRequisObligatoire(boolean v) { this.nouveauDocumentRequisObligatoire = v; }
    public List<LigneAvancement> getAvancementCandidature() {
        List<LigneAvancement> lignes = new java.util.ArrayList<>();
        if (candidature == null || candidature.getOffre() == null) return lignes;
        Long candidatureId = candidature.getId();
        for (PhaseOffre po : recrutementService.getPhasesOffre(candidature.getOffre().getId())) {
            int nbNotes = recrutementService.getNotes(po.getId(), candidatureId).size();
            ResultatPhaseCandidature res = recrutementService.getResultats(po.getId()).stream()
                .filter(r -> r.getCandidature() != null && candidatureId.equals(r.getCandidature().getId()))
                .findFirst().orElse(null);
            lignes.add(new LigneAvancement(po.getPhase().getLibelle(), po.getStatut(), nbNotes,
                res == null ? null : res.getNombrePoints(),
                res == null ? null : res.getRang(),
                res == null ? null : res.getStatut()));
        }
        return lignes;
    }

    public static class LigneAvancement implements Serializable {
        private final String phase;
        private final String statutPhase;
        private final int nbNotes;
        private final Integer points;
        private final Integer rang;
        private final String statutResultat;

        public LigneAvancement(String phase, String statutPhase, int nbNotes,
                               Integer points, Integer rang, String statutResultat) {
            this.phase = phase;
            this.statutPhase = statutPhase;
            this.nbNotes = nbNotes;
            this.points = points;
            this.rang = rang;
            this.statutResultat = statutResultat;
        }

        public String  getPhase()          { return phase; }
        public String  getStatutPhase()    { return statutPhase; }
        public int     getNbNotes()        { return nbNotes; }
        public Integer getPoints()         { return points; }
        public Integer getRang()           { return rang; }
        public String  getStatutResultat() { return statutResultat; }
    }

    public Candidature       getCandidature()  { return candidature; }
    public void              setCandidature(Candidature c) { this.candidature = c; }
    public DemandeConge      getDemandeConge() { return demandeConge; }
    public void              setDemandeConge(DemandeConge d) { this.demandeConge = d; }
    public Formation         getFormation()    { return formation; }
    public void              setFormation(Formation f) { this.formation = f; }
    public Evaluation        getEvaluation()   { return evaluation; }
    public void              setEvaluation(Evaluation e) { this.evaluation = e; }
    public MouvementCarriere getMouvement()    { return mouvement; }
    public void              setMouvement(MouvementCarriere m) { this.mouvement = m; }

    public boolean isModeEdition()               { return modeEdition; }
    public void    setModeEdition(boolean b)     { this.modeEdition = b; }
    public String  getFiltreStatut()             { return filtreStatut; }
    public void    setFiltreStatut(String s)     { this.filtreStatut = s; }
    public String  getTermeRecherche()           { return termeRecherche; }
    public void    setTermeRecherche(String s)   { this.termeRecherche = s; }
    public String  getCommentaireRefus()         { return commentaireRefus; }
    public void    setCommentaireRefus(String s) { this.commentaireRefus = s; }

    public long getTotalEmployes()          { return totalEmployes; }
    public long getEmployesActifs()         { return employesActifs; }
    public long getOffresActives()          { return offresActives; }
    public long getCandidaturesEnAttente()  { return candidaturesEnAttente; }
    public long getCongesEnAttente()        { return congesEnAttente; }
    public long getFormationsActives()      { return formationsActives; }

    // ══════════════════════════════════════════════════
    // DIALOG : Employés d'une division
    // ══════════════════════════════════════════════════

    private List<Employe> employesDivisionSelectionnee = new java.util.ArrayList<>();
    private String titreDivisionSelectionnee = "Employés de la division";
    private long divisionSelectionneeId = 0;

    public void chargerEmployesDivision(Long divisionId) {
        try {
            this.divisionSelectionneeId = divisionId;
            this.employesDivisionSelectionnee = employeDao.findByDivision(divisionId);
            if (employesDivisionSelectionnee == null)
                employesDivisionSelectionnee = new java.util.ArrayList<>();
            com.soutenance.entity.Division div = structureDao.findDivisionById(divisionId);
            this.titreDivisionSelectionnee = (div != null)
                ? "Employés — " + div.getNom() + " (" + employesDivisionSelectionnee.size() + ")"
                : "Employés de la division";
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            employesDivisionSelectionnee = new java.util.ArrayList<>();
        }
    }

    public List<Employe> getEmployesDivisionSelectionnee() { return employesDivisionSelectionnee; }
    public String getTitreDivisionSelectionnee()           { return titreDivisionSelectionnee; }

    public long getNbEmployesDivision() {
        return employesDivisionSelectionnee != null ? employesDivisionSelectionnee.size() : 0;
    }
    public long getNbActifsDivision() {
        if (employesDivisionSelectionnee == null) return 0;
        return employesDivisionSelectionnee.stream()
            .filter(e -> "ACTIF".equals(e.getStatut() != null ? e.getStatut().toString() : ""))
            .count();
    }
    public long getNbCdiDivision() {
        if (employesDivisionSelectionnee == null) return 0;
        return employesDivisionSelectionnee.stream()
            .filter(e -> "CDI".equals(e.getTypeContrat() != null ? e.getTypeContrat().toString() : ""))
            .count();
    }
    public long getNbCadresDivision() {
        if (employesDivisionSelectionnee == null) return 0;
        return employesDivisionSelectionnee.stream()
            .filter(e -> {
                String n = e.getNiveauHierarchique() != null ? e.getNiveauHierarchique().toString() : "";
                return n.equals("DIRECTEUR") || n.equals("CHEF_DIV")
                    || n.equals("CHEF_SERVICE") || n.equals("CHEF_BUREAU");
            })
            .count();
    }

    // ── Recherche et filtre dans le dialog employés ────────────
    private String rechercheDialogDiv = "";
    private String filtreNiveauDiv    = "";
    private List<Employe> employesDivisionFiltres = new java.util.ArrayList<>();

    public void filtrerDialogDiv() {
        appliquerFiltreDiv();
    }

    public void reinitialiserFiltreDiv() {
        rechercheDialogDiv = "";
        filtreNiveauDiv    = "";
        if (!employesDivisionFiltres.isEmpty()) {
            employesDivisionSelectionnee = new java.util.ArrayList<>(employesDivisionFiltres);
        }
    }

    private void appliquerFiltreDiv() {
        List<Employe> source = employesDivisionFiltres.isEmpty()
            ? employesDivisionSelectionnee
            : employesDivisionFiltres;

        employesDivisionSelectionnee = source.stream()
            .filter(e -> {
                // Filtre niveau
                if (filtreNiveauDiv != null && !filtreNiveauDiv.isBlank()) {
                    String niv = e.getNiveauHierarchique() != null
                        ? e.getNiveauHierarchique().toString() : "";
                    if (!filtreNiveauDiv.equals(niv)) return false;
                }
                // Filtre texte
                if (rechercheDialogDiv != null && !rechercheDialogDiv.isBlank()) {
                    String q = rechercheDialogDiv.toLowerCase();
                    String nom = (e.getNom()       != null ? e.getNom()       : "").toLowerCase();
                    String pre = (e.getPrenom()    != null ? e.getPrenom()    : "").toLowerCase();
                    String mat = (e.getMatricule() != null ? e.getMatricule() : "").toLowerCase();
                    // FIX : e.getPoste() retourne Poste → appel .toString() avant toLowerCase()
                    String pos = (e.getPoste()     != null ? e.getPoste().toString() : "").toLowerCase();
                    String eml = (e.getEmail()     != null ? e.getEmail()     : "").toLowerCase();
                    if (!nom.contains(q) && !pre.contains(q)
                        && !mat.contains(q) && !pos.contains(q) && !eml.contains(q))
                        return false;
                }
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    public void chargerEmployesDivisionAvecFiltre(Long divisionId) {
        chargerEmployesDivision(divisionId);
        employesDivisionFiltres = new java.util.ArrayList<>(employesDivisionSelectionnee);
        rechercheDialogDiv = "";
        filtreNiveauDiv    = "";
    }

    public String  getRechercheDialogDiv()           { return rechercheDialogDiv; }
    public void    setRechercheDialogDiv(String v)   { this.rechercheDialogDiv = v; }
    public String  getFiltreNiveauDiv()              { return filtreNiveauDiv; }
    public void    setFiltreNiveauDiv(String v)      { this.filtreNiveauDiv = v; }
}