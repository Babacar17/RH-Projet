package com.soutenance.bean;

import com.soutenance.dao.OffreEmploiDao;
import com.soutenance.entity.CandidatFinal;
import com.soutenance.entity.OffreEmploi;
import com.soutenance.entity.PVDeliberation;
import com.soutenance.entity.RapportPhaseEvaluation;
import com.soutenance.service.RapportPvPdfService;
import com.soutenance.service.RecrutementService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Archive consolidée des rapports de phase et PV de délibération de toutes
 * les offres de l'institution — permet de retrouver un document sans avoir
 * à resélectionner l'offre d'origine sur la page Concours &amp; Jury.
 */
@Component("archiveRecrutementBean")
@Scope("view")
public class ArchiveRecrutementBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private RecrutementService recrutementService;
    @Autowired private OffreEmploiDao offreEmploiDao;
    @Autowired private SessionBean sessionBean;
    @Autowired(required = false) private RapportPvPdfService rapportPvPdfService;

    private List<RapportPhaseEvaluation> rapports = new ArrayList<>();
    private List<PVDeliberation> pvs = new ArrayList<>();

    /** Toutes les offres de l'institution, pour le filtre (pas seulement celles déjà archivées). */
    private List<OffreEmploi> offresDisponibles = new ArrayList<>();

    private Long filtreOffreId;
    /** Rien ne s'affiche tant que le RH n'a pas explicitement choisi une offre (même "Toutes les offres"). */
    private boolean filtresAppliques = false;

    /** Mémoïsation — ces getters sont appelés plusieurs fois par rendu (rendered + value des dataTable imbriquées). */
    private final Map<Long, List<CandidatFinal>> candidatsFinauxCache = new HashMap<>();
    private final Map<Long, List<com.soutenance.entity.NoteCandidatEvalElement>> notesParCandidatureCache = new HashMap<>();
    private final Map<Long, Integer> pointsCumulesCache = new HashMap<>();
    private final Map<Long, Integer> pointsMaxCumulesCache = new HashMap<>();

    @PostConstruct
    public void init() {
        Long instId = sessionBean.getInstitutionId();
        rapports = recrutementService.getRapportsInstitution(instId);
        pvs = recrutementService.getPvsInstitution(instId);

        offresDisponibles = new ArrayList<>(offreEmploiDao.findByInstitution(instId));
        offresDisponibles.sort((a, b) -> {
            String ta = a.getTitre() != null ? a.getTitre() : "";
            String tb = b.getTitre() != null ? b.getTitre() : "";
            return ta.compareToIgnoreCase(tb);
        });
    }

    public List<RapportPhaseEvaluation> getRapportsFiltres() {
        if (filtreOffreId == null) return rapports;
        return rapports.stream()
            .filter(r -> r.getPhaseOffre() != null && r.getPhaseOffre().getOffre() != null
                      && filtreOffreId.equals(r.getPhaseOffre().getOffre().getId()))
            .toList();
    }

    public List<PVDeliberation> getPvsFiltres() {
        if (filtreOffreId == null) return pvs;
        return pvs.stream()
            .filter(p -> p.getOffre() != null && filtreOffreId.equals(p.getOffre().getId()))
            .toList();
    }

    /** Appelé au changement du filtre : révèle les tableaux (même si "Toutes les offres" est choisi). */
    public void appliquerFiltre() {
        filtresAppliques = true;
    }

    /** Classement final de l'offre, pour l'expansion d'une ligne de PV. */
    public List<CandidatFinal> getCandidatsFinaux(Long offreId) {
        if (offreId == null) return new ArrayList<>();
        return candidatsFinauxCache.computeIfAbsent(offreId, recrutementService::getCandidatsFinaux);
    }

    /** Notes détaillées par membre du jury pour un candidat — sous-expansion d'une ligne du classement. */
    public List<com.soutenance.entity.NoteCandidatEvalElement> getNotesParCandidature(Long candidatureId) {
        if (candidatureId == null) return new ArrayList<>();
        return notesParCandidatureCache.computeIfAbsent(candidatureId, recrutementService::getNotesParCandidature);
    }

    public Integer getPointsCumules(com.soutenance.entity.Candidature c) {
        if (c == null) return null;
        return pointsCumulesCache.computeIfAbsent(c.getId(), id -> recrutementService.getPointsCandidature(id));
    }

    public Integer getPointsMaxCumules(com.soutenance.entity.Candidature c) {
        if (c == null) return null;
        return pointsMaxCumulesCache.computeIfAbsent(c.getId(), id -> recrutementService.getPointsMaxCandidature(id));
    }

    public void notifierJuryRapport(Long rapportId) {
        RapportPhaseEvaluation r = rapports.stream()
            .filter(x -> x.getId().equals(rapportId)).findFirst().orElse(null);
        if (r == null) return;
        recrutementService.notifierJuryRapport(r);
        addMsgSucces("Jury notifié.");
    }

    public void notifierJuryPv(Long pvId) {
        PVDeliberation pv = pvs.stream()
            .filter(x -> x.getId().equals(pvId)).findFirst().orElse(null);
        if (pv == null) return;
        recrutementService.notifierJuryPv(pv);
        addMsgSucces("Jury notifié.");
    }

    public void telechargerRapport(Long rapportId) {
        RapportPhaseEvaluation r = rapports.stream()
            .filter(x -> x.getId().equals(rapportId)).findFirst().orElse(null);
        if (r == null || rapportPvPdfService == null) {
            addMsg("Impossible de générer le PDF de ce rapport.");
            return;
        }
        byte[] pdf = rapportPvPdfService.genererRapport(r);
        ecrirePdf(pdf, "rapport-" + rapportId + ".pdf", false);
    }

    public void telechargerPv(Long pvId) {
        PVDeliberation pv = pvs.stream()
            .filter(x -> x.getId().equals(pvId)).findFirst().orElse(null);
        if (pv == null || rapportPvPdfService == null) {
            addMsg("Impossible de générer le PDF de ce PV.");
            return;
        }
        List<CandidatFinal> classement = pv.getOffre() != null
            ? recrutementService.getCandidatsFinaux(pv.getOffre().getId()) : new ArrayList<>();
        byte[] pdf = rapportPvPdfService.genererPv(pv, classement);
        ecrirePdf(pdf, "pv-deliberation-" + pvId + ".pdf", false);
    }

    /** Ouvre le rapport dans un nouvel onglet (affichage inline) plutôt que de forcer un téléchargement. */
    public void ouvrirRapport(Long rapportId) {
        RapportPhaseEvaluation r = rapports.stream()
            .filter(x -> x.getId().equals(rapportId)).findFirst().orElse(null);
        if (r == null || rapportPvPdfService == null) {
            addMsg("Impossible d'ouvrir le PDF de ce rapport.");
            return;
        }
        byte[] pdf = rapportPvPdfService.genererRapport(r);
        ecrirePdf(pdf, "rapport-" + rapportId + ".pdf", true);
    }

    /** Ouvre le PV dans un nouvel onglet (affichage inline) plutôt que de forcer un téléchargement. */
    public void ouvrirPv(Long pvId) {
        PVDeliberation pv = pvs.stream()
            .filter(x -> x.getId().equals(pvId)).findFirst().orElse(null);
        if (pv == null || rapportPvPdfService == null) {
            addMsg("Impossible d'ouvrir le PDF de ce PV.");
            return;
        }
        List<CandidatFinal> classement = pv.getOffre() != null
            ? recrutementService.getCandidatsFinaux(pv.getOffre().getId()) : new ArrayList<>();
        byte[] pdf = rapportPvPdfService.genererPv(pv, classement);
        ecrirePdf(pdf, "pv-deliberation-" + pvId + ".pdf", true);
    }

    private void ecrirePdf(byte[] pdf, String nomFichier, boolean inline) {
        if (pdf == null || pdf.length == 0) {
            addMsg("Erreur lors de la génération du PDF.");
            return;
        }
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition", (inline ? "inline" : "attachment") + "; filename=\"" + nomFichier + "\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception ex) {
            addMsg("Erreur lors du téléchargement : " + ex.getMessage());
        }
    }

    private void addMsgSucces(String texte) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", texte));
    }

    private void addMsg(String texte) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", texte));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public List<RapportPhaseEvaluation> getRapports() { return rapports; }
    public List<PVDeliberation> getPvs() { return pvs; }
    public List<OffreEmploi> getOffresDisponibles() { return offresDisponibles; }
    public Long getFiltreOffreId() { return filtreOffreId; }
    public void setFiltreOffreId(Long filtreOffreId) { this.filtreOffreId = filtreOffreId; }
    public boolean isFiltresAppliques() { return filtresAppliques; }
}
