package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.CandidatService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component("candidatBean")
@Scope("view")
public class CandidatBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(CandidatBean.class);

    private static final long serialVersionUID = 1L;

    // ── Dossier de stockage des fichiers ──────────────────────────────────────
    // Chemin absolu sur le serveur — adapter si nécessaire
    private static final String UPLOAD_DIR = System.getProperty("catalina.home")
            + File.separator + "webapps" + File.separator + "uploads" + File.separator + "candidatures";

    @Autowired private CandidatService candidatService;
    @Autowired private SessionBean     sessionBean;
    @Autowired private com.soutenance.dao.OffreEmploiDao offreDao; // pour recharger par id
    @Autowired(required = false) private com.soutenance.service.OffreEmploiPdfService offreEmploiPdfService;

    private List<OffreEmploi>  offresDisponibles;
    private List<Candidature>  mesCandidatures;
    /** Statut choisi pour filtrer "Mes candidatures" ; null = rien affiché tant qu'aucun choix n'est fait. */
    private String             filtreStatutMesCandidatures;
    private List<OffreEmploi>  offresFiltrees;
    private OffreEmploi        offreSelectionnee;
    private Candidature        nouvelleCandidature = new Candidature();
    private String             filtrePoste;
    private User               candidatConnecte;
    private String             ancienMotDePasse;
    private String             nouveauMotDePasse;
    private String             confirmMotDePasse;

    // Paramètre URL pour postuler.xhtml
    private Long offreId;

    // ── Fichiers uploadés (PrimeFaces UploadedFile) ───────────────────────────
    private org.primefaces.model.file.UploadedFile cvFichier;
    /** Pièces supplémentaires saisies par le candidat (libellé + fichier). */
    private java.util.List<LigneDocSupp> lignesDocsSupp = new java.util.ArrayList<>();
    /** Un fichier par DocumentRequisOffre.id — les champs sont générés dynamiquement selon l'offre. */
    private java.util.Map<Long, org.primefaces.model.file.UploadedFile> fichiersDynamiques = new java.util.HashMap<>();

    @PostConstruct
    public void init() {
        candidatConnecte = sessionBean.getUtilisateurConnecte();
        chargerOffres();
        chargerCandidatures();
        // Lire offreId depuis le paramètre de requête (GET ?offreId=X ou POST champ hidden)
        if (offreId == null) {
            try {
                jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
                if (fc != null) {
                    String param = fc.getExternalContext().getRequestParameterMap().get("offreId");
                    if (param != null && !param.isEmpty()) {
                        offreId = Long.parseLong(param);
                    }
                }
            } catch (Exception ignored) {}
        }
        // Charger l'offre sélectionnée depuis la BD si on a un offreId
        if (offreId != null && offreSelectionnee == null) {
            offreSelectionnee = offreDao.findById(offreId);
            // Un candidat ne doit jamais pouvoir postuler à une offre d'une autre institution,
            // même en devinant l'id dans l'URL.
            if (offreSelectionnee != null && candidatConnecte != null
                    && !java.util.Objects.equals(offreSelectionnee.getInstitutionId(), candidatConnecte.getInstitutionId())) {
                offreSelectionnee = null;
            }
        }
        // Pré-remplir nouvelleCandidature seulement si elle est encore vide
        // (ne pas écraser les données saisies sur un POST de soumission)
        if (offreSelectionnee != null && candidatConnecte != null
                && nouvelleCandidature.getNomCandidat() == null) {
            nouvelleCandidature.setNomCandidat(candidatConnecte.getNom());
            nouvelleCandidature.setPrenomCandidat(candidatConnecte.getPrenom());
            nouvelleCandidature.setEmailCandidat(candidatConnecte.getEmail());
            nouvelleCandidature.setTelephoneCandidat(candidatConnecte.getTelephone());
        }
    }

    public void chargerOffres() {
        offresDisponibles = candidatService.getOffresDisponibles();
        offresFiltrees    = offresDisponibles;
    }

    public void chargerCandidatures() {
        if (candidatConnecte != null) {
            mesCandidatures = candidatService.getCandidaturesParUser(candidatConnecte.getId());
        }
    }

    public void preparerPostuler(OffreEmploi offre) {
        this.offreSelectionnee   = offre;
        this.nouvelleCandidature = new Candidature();
        // Réinitialiser les fichiers
        cvFichier = null;
        lignesDocsSupp = new java.util.ArrayList<>();
        lignesDocsSupp.add(new LigneDocSupp());
        fichiersDynamiques.clear();
        // Pré-remplir depuis le profil
        if (candidatConnecte != null) {
            nouvelleCandidature.setNomCandidat(candidatConnecte.getNom());
            nouvelleCandidature.setPrenomCandidat(candidatConnecte.getPrenom());
            nouvelleCandidature.setEmailCandidat(candidatConnecte.getEmail());
            nouvelleCandidature.setTelephoneCandidat(candidatConnecte.getTelephone());
        }
    }

    /**
     * Navigue vers postuler.xhtml en passant l'id de l'offre en paramètre URL.
     */
    public String allerPostuler(OffreEmploi offre) {
        return "/Candidat/postuler.xhtml?faces-redirect=true&offreId=" + offre.getId();
    }

    /**
     * Soumet la candidature depuis postuler.xhtml et redirige vers mes-candidatures.
     */
    public String postulerEtRediriger() {
        if (candidatConnecte == null) { addError("Vous devez être connecté."); return null; }
        if (offreSelectionnee == null) { addError("Aucune offre sélectionnée."); return null; }
        if (candidatService.aDejaPostule(candidatConnecte.getId(), offreSelectionnee.getId())) {
            addError("Vous avez déjà postulé à cette offre."); return null;
        }
        if (cvFichier == null || cvFichier.getSize() == 0) {
            addError("Le CV est obligatoire."); return null;
        }
        for (com.soutenance.entity.DocumentRequisOffre doc : offreSelectionnee.getDocumentsRequis()) {
            if (!doc.isObligatoire()) continue;
            org.primefaces.model.file.UploadedFile f = fichiersDynamiques.get(doc.getId());
            if (f == null || f.getSize() == 0) {
                addError("Le document « " + doc.getLibelle() + " » est obligatoire pour cette offre."); return null;
            }
        }
        try {
            String userFolder = candidatConnecte.getUsername();
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(UPLOAD_DIR, userFolder);
            java.nio.file.Files.createDirectories(uploadPath);

            nouvelleCandidature.setCvUrl(              sauvegarderFichier(cvFichier,               uploadPath, "cv"));
            int rang = 0;
            for (LigneDocSupp ligne : lignesDocsSupp) {
                if (ligne.getFichier() == null || ligne.getFichier().getSize() == 0) continue;
                rang++;
                String url = sauvegarderFichier(ligne.getFichier(), uploadPath, "supp" + rang);
                String libelle = ligne.getLibelle() != null && !ligne.getLibelle().isBlank()
                    ? ligne.getLibelle().trim() : ligne.getFichier().getFileName();
                nouvelleCandidature.getDocumentsSupplementaires().add(
                    new com.soutenance.entity.DocumentSupplementaire(nouvelleCandidature, libelle, url));
            }

            for (com.soutenance.entity.DocumentRequisOffre doc : offreSelectionnee.getDocumentsRequis()) {
                org.primefaces.model.file.UploadedFile f = fichiersDynamiques.get(doc.getId());
                String url = sauvegarderFichier(f, uploadPath, "doc" + doc.getId());
                if (url != null) {
                    nouvelleCandidature.getDocumentsSoumis().add(
                        new com.soutenance.entity.DocumentSoumisCandidature(nouvelleCandidature, doc.getLibelle(), url));
                }
            }

            candidatService.postuler(candidatConnecte, offreSelectionnee, nouvelleCandidature);
            chargerCandidatures();
            // Redirection vers mes-candidatures avec message succès via flash scope
            jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
            fc.getExternalContext().getFlash().put("successMsg", "Candidature envoyée avec succès ! Votre dossier a bien été reçu.");
            return "/Candidat/mes-candidatures.xhtml?faces-redirect=true";
        } catch (Exception e) {
            addError("Erreur lors de l'envoi : " + e.getMessage());
            LOG.error("Erreur inattendue", e);
            return null;
        }
    }

    /**
     * Sauvegarde un UploadedFile sur le disque et retourne le chemin relatif,
     * ou null si le fichier est vide.
     */
    private String sauvegarderFichier(org.primefaces.model.file.UploadedFile fichier, Path dossier, String prefixe) throws IOException {
        if (fichier == null || fichier.getSize() == 0) return null;
        String nomOriginal = Paths.get(fichier.getFileName()).getFileName().toString();
        String extension   = nomOriginal.contains(".") ? nomOriginal.substring(nomOriginal.lastIndexOf(".")) : "";
        String nomFichier  = prefixe + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
        Path   dest        = dossier.resolve(nomFichier);
        Files.write(dest, fichier.getContent());
        return dossier.getFileName().toString() + "/" + nomFichier;
    }

    public void annulerCandidature(Candidature c) {
        if (!"RECUE".equals(c.getStatut())) {
            addError("Impossible d'annuler une candidature déjà traitée."); return;
        }
        try {
            candidatService.annulerCandidature(c.getId());
            chargerCandidatures();
            addInfo("Candidature annulée.");
        } catch (Exception e) {
            addError("Erreur lors de l'annulation.");
        }
    }

    public void filtrerOffres() {
        if (filtrePoste == null || filtrePoste.isEmpty()) {
            offresFiltrees = offresDisponibles; return;
        }
        String f = filtrePoste.toLowerCase();
        offresFiltrees = offresDisponibles.stream()
            .filter(o -> o.getTitre().toLowerCase().contains(f)
                || (o.getDescription() != null && o.getDescription().toLowerCase().contains(f)))
            .collect(Collectors.toList());
    }

    public void reinitialiserFiltres() {
        filtrePoste    = null;
        offresFiltrees = offresDisponibles;
    }

    public void changerMotDePasse() {
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 6) {
            addError("Minimum 6 caractères."); return;
        }
        if (!nouveauMotDePasse.equals(confirmMotDePasse)) {
            addError("Les mots de passe ne correspondent pas."); return;
        }
        boolean ok = candidatService.changerMotDePasse(
            candidatConnecte.getUsername(), ancienMotDePasse, nouveauMotDePasse);
        if (ok) {
            addInfo("Mot de passe modifié !");
            ancienMotDePasse = nouveauMotDePasse = confirmMotDePasse = null;
        } else {
            addError("Ancien mot de passe incorrect.");
        }
    }

    public boolean peutPostuler(OffreEmploi offre) {
        if (mesCandidatures == null) return true;
        return mesCandidatures.stream()
            .noneMatch(c -> c.getOffre() != null && c.getOffre().getId().equals(offre.getId()));
    }

    /** Ouvre le PDF de la fiche d'offre dans un nouvel onglet, pour consultation/téléchargement. */
    public void voirOffrePdf(Long offreId) {
        try {
            if (offreEmploiPdfService == null || offreId == null) {
                addError("Impossible de générer le PDF de l'offre.");
                return;
            }
            OffreEmploi offre = offreDao.findById(offreId);
            if (offre == null) {
                addError("Offre introuvable.");
                return;
            }
            byte[] pdf = offreEmploiPdfService.genererOffre(offre);
            if (pdf == null || pdf.length == 0) {
                addError("Impossible de générer le PDF de l'offre.");
                return;
            }
            FacesContext fc = FacesContext.getCurrentInstance();
            jakarta.faces.context.ExternalContext ec = fc.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/pdf");
            ec.setResponseHeader("Content-Disposition",
                "inline; filename=\"offre-" + offreId + ".pdf\"");
            ec.setResponseContentLength(pdf.length);
            ec.getResponseOutputStream().write(pdf);
            fc.responseComplete();
        } catch (Exception ex) {
            addError("Erreur lors de la génération du PDF : " + ex.getMessage());
        }
    }

    // ── Listes limitées pour le dashboard (évite begin/end sur ui:repeat) ────

    public List<OffreEmploi> getOffresRecentes() {
        if (offresDisponibles == null || offresDisponibles.isEmpty()) return new java.util.ArrayList<>();
        int limit = Math.min(4, offresDisponibles.size());
        return offresDisponibles.subList(0, limit);
    }

    public List<Candidature> getDernieresCandidatures() {
        if (mesCandidatures == null || mesCandidatures.isEmpty()) return new java.util.ArrayList<>();
        int limit = Math.min(5, mesCandidatures.size());
        return mesCandidatures.subList(0, limit);
    }

    /** Entretiens à venir (candidatures au statut ENTRETIEN avec une date future), triés par date croissante. */
    public List<Candidature> getEntretiensAVenir() {
        if (mesCandidatures == null) return new java.util.ArrayList<>();
        java.time.LocalDate aujourdhui = java.time.LocalDate.now();
        return mesCandidatures.stream()
            .filter(c -> c.getDateEntretien() != null && !c.getDateEntretien().isBefore(aujourdhui))
            .sorted(java.util.Comparator.comparing(Candidature::getDateEntretien))
            .collect(Collectors.toList());
    }

    public long getNbEnAttente() {
        if (mesCandidatures == null) return 0;
        return mesCandidatures.stream().filter(c -> "RECUE".equals(c.getStatut())).count();
    }
    public long getNbRetenues() {
        if (mesCandidatures == null) return 0;
        return mesCandidatures.stream().filter(c -> "RETENUE".equals(c.getStatut()) || "RECRUTEE".equals(c.getStatut())).count();
    }
    public long getNbRefusees() {
        if (mesCandidatures == null) return 0;
        return mesCandidatures.stream().filter(c -> "REFUSEE".equals(c.getStatut())).count();
    }
    public long getNbEntretiens() {
        if (mesCandidatures == null) return 0;
        return mesCandidatures.stream().filter(c -> "ENTRETIEN".equals(c.getStatut())).count();
    }

    public String getFiltreStatutMesCandidatures() { return filtreStatutMesCandidatures; }
    public void   setFiltreStatutMesCandidatures(String v) { this.filtreStatutMesCandidatures = v; }

    /** Table masquée tant qu'aucun statut n'est choisi ; "TOUTES" affiche tout. */
    public List<Candidature> getMesCandidaturesFiltrees() {
        if (filtreStatutMesCandidatures == null || mesCandidatures == null) return new java.util.ArrayList<>();
        if ("TOUTES".equals(filtreStatutMesCandidatures)) return mesCandidatures;
        if ("RETENUE".equals(filtreStatutMesCandidatures)) {
            return mesCandidatures.stream()
                .filter(c -> "RETENUE".equals(c.getStatut()) || "RECRUTEE".equals(c.getStatut()))
                .collect(Collectors.toList());
        }
        return mesCandidatures.stream()
            .filter(c -> filtreStatutMesCandidatures.equals(c.getStatut()))
            .collect(Collectors.toList());
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }
    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────
    public Long   getOffreId()                        { return offreId; }
    public void   setOffreId(Long id)                 { this.offreId = id; }
    public List<OffreEmploi> getOffresDisponibles()   { return offresDisponibles; }
    public List<OffreEmploi> getOffresFiltrees()      { return offresFiltrees; }
    public List<Candidature> getMesCandidatures()     { return mesCandidatures; }
    public OffreEmploi       getOffreSelectionnee()   { return offreSelectionnee; }
    public void setOffreSelectionnee(OffreEmploi o)   { this.offreSelectionnee = o; }
    public Candidature       getNouvelleCandidature() { return nouvelleCandidature; }
    public void setNouvelleCandidature(Candidature c) { this.nouvelleCandidature = c; }
    public String            getFiltrePoste()         { return filtrePoste; }
    public void setFiltrePoste(String f)              { this.filtrePoste = f; }
    public User              getCandidatConnecte()    { return candidatConnecte; }
    public String            getAncienMotDePasse()    { return ancienMotDePasse; }
    public void setAncienMotDePasse(String s)         { this.ancienMotDePasse = s; }
    public String            getNouveauMotDePasse()   { return nouveauMotDePasse; }
    public void setNouveauMotDePasse(String s)        { this.nouveauMotDePasse = s; }
    public String            getConfirmMotDePasse()   { return confirmMotDePasse; }
    public void setConfirmMotDePasse(String s)        { this.confirmMotDePasse = s; }
    public org.primefaces.model.file.UploadedFile getCvFichier()                        { return cvFichier; }
    public void setCvFichier(org.primefaces.model.file.UploadedFile f)                  { this.cvFichier = f; }
    public java.util.List<LigneDocSupp> getLignesDocsSupp() { return lignesDocsSupp; }

    public void ajouterLigneDocSupp() { lignesDocsSupp.add(new LigneDocSupp()); }

    public void retirerLigneDocSupp(LigneDocSupp ligne) { lignesDocsSupp.remove(ligne); }

    public static class LigneDocSupp {
        private String libelle;
        private org.primefaces.model.file.UploadedFile fichier;

        public String getLibelle() { return libelle; }
        public void setLibelle(String libelle) { this.libelle = libelle; }

        public org.primefaces.model.file.UploadedFile getFichier() { return fichier; }
        public void setFichier(org.primefaces.model.file.UploadedFile fichier) { this.fichier = fichier; }
    }
    public java.util.Map<Long, org.primefaces.model.file.UploadedFile> getFichiersDynamiques() { return fichiersDynamiques; }
}