package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.User;
import com.soutenance.service.CandidatService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.model.file.UploadedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;

@Component("demandeSpontaneeBean")
@Scope("view")
public class DemandeSpontaneeBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(DemandeSpontaneeBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private CandidatService service;
    @Autowired private SessionBean sessionBean;

    // ── Type ──────────────────────────────────────────────────
    /** EMPLOI ou STAGE */
    private String typeDemande = "EMPLOI";

    // ── Informations personnelles ──────────────────────────────
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private LocalDate dateNaissance;
    private String nationalite;
    private String adresse;

    // ── Profil professionnel ───────────────────────────────────
    private String niveauEtudes;
    private String anneesExperience;
    private String domaine;
    private String posteVise;
    private String motivation;

    // ── Champs spécifiques STAGE ───────────────────────────────
    private String etablissement;
    private Integer dureeStageMois;
    private LocalDate dateDebutSouhaite;

    // ── Documents communs ──────────────────────────────────────
    private UploadedFile cvFichier;
    private UploadedFile lettreFichier;
    private UploadedFile pieceIdentiteFichier;
    private UploadedFile diplomesFichier;

    // ── Documents spécifiques EMPLOI ──────────────────────────
    private UploadedFile certificatsTravailFichier;
    private UploadedFile casierJudiciaireFichier;

    // ── Documents spécifiques STAGE ───────────────────────────
    private UploadedFile lettreEtablissementFichier;
    private UploadedFile releveNotesFichier;

    @PostConstruct
    public void init() {
        // Pré-remplir depuis le compte connecté
        User user = sessionBean.getUtilisateurConnecte();
        if (user != null) {
            nom      = user.getNom();
            prenom   = user.getPrenom();
            email    = user.getEmail();
            telephone = user.getTelephone();
        }
    }

    // ── Action principale ──────────────────────────────────────

    public String soumettre() {
        try {
            // Validation champs obligatoires communs
            if (nom == null || nom.isBlank() || prenom == null || prenom.isBlank()) {
                addError("Le nom et le prénom sont obligatoires.");
                return null;
            }
            if (email == null || email.isBlank()) {
                addError("L'adresse email est obligatoire.");
                return null;
            }
            if (cvFichier == null || cvFichier.getSize() == 0) {
                addError("Le CV est obligatoire.");
                return null;
            }
            if (lettreFichier == null || lettreFichier.getSize() == 0) {
                addError("La lettre de motivation est obligatoire.");
                return null;
            }
            if (pieceIdentiteFichier == null || pieceIdentiteFichier.getSize() == 0) {
                addError("La pièce d'identité est obligatoire.");
                return null;
            }
            if (diplomesFichier == null || diplomesFichier.getSize() == 0) {
                addError("Les diplômes sont obligatoires.");
                return null;
            }

            // Validation spécifique STAGE
            if ("STAGE".equals(typeDemande)) {
                if (etablissement == null || etablissement.isBlank()) {
                    addError("Le nom de l'établissement est obligatoire pour une demande de stage.");
                    return null;
                }
                if (lettreEtablissementFichier == null || lettreEtablissementFichier.getSize() == 0) {
                    addError("La lettre de recommandation de l'établissement est obligatoire.");
                    return null;
                }
                if (releveNotesFichier == null || releveNotesFichier.getSize() == 0) {
                    addError("Le relevé de notes est obligatoire.");
                    return null;
                }
            }

            // Appel service
            User user = sessionBean.getUtilisateurConnecte();
            service.enregistrerDemande(this, user);

            addInfo("Votre demande de " + ("EMPLOI".equals(typeDemande) ? "emploi" : "stage")
                    + " a été soumise avec succès ! Le service RH vous contactera sous 5 jours ouvrables.");

            // Réinitialiser le formulaire
            reinitialiser();

        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            addError("Une erreur est survenue lors de l'envoi : " + e.getMessage());
        }
        return null;
    }

    private void reinitialiser() {
        typeDemande  = "EMPLOI";
        motivation   = null;
        posteVise    = null;
        niveauEtudes = null;
        domaine      = null;
        anneesExperience = null;
        etablissement    = null;
        dureeStageMois   = null;
        dateDebutSouhaite = null;
        cvFichier = lettreFichier = pieceIdentiteFichier = diplomesFichier = null;
        lettreEtablissementFichier = releveNotesFichier = null;
        certificatsTravailFichier  = casierJudiciaireFichier = null;
    }

    // ── Helpers ────────────────────────────────────────────────

    public boolean isEmploi() { return "EMPLOI".equals(typeDemande); }
    public boolean isStage()  { return "STAGE".equals(typeDemande); }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }
    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ── Getters / Setters ──────────────────────────────────────

    public String getTypeDemande() { return typeDemande; }
    public void setTypeDemande(String t) { this.typeDemande = t; }

    public String getNom() { return nom; }
    public void setNom(String v) { this.nom = v; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String v) { this.prenom = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String v) { this.telephone = v; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate v) { this.dateNaissance = v; }
    public String getNationalite() { return nationalite; }
    public void setNationalite(String v) { this.nationalite = v; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String v) { this.adresse = v; }

    public String getNiveauEtudes() { return niveauEtudes; }
    public void setNiveauEtudes(String v) { this.niveauEtudes = v; }
    public String getAnneesExperience() { return anneesExperience; }
    public void setAnneesExperience(String v) { this.anneesExperience = v; }
    public String getDomaine() { return domaine; }
    public void setDomaine(String v) { this.domaine = v; }
    public String getPosteVise() { return posteVise; }
    public void setPosteVise(String v) { this.posteVise = v; }
    public String getMotivation() { return motivation; }
    public void setMotivation(String v) { this.motivation = v; }

    public String getEtablissement() { return etablissement; }
    public void setEtablissement(String v) { this.etablissement = v; }
    public Integer getDureeStageMois() { return dureeStageMois; }
    public void setDureeStageMois(Integer v) { this.dureeStageMois = v; }
    public LocalDate getDateDebutSouhaite() { return dateDebutSouhaite; }
    public void setDateDebutSouhaite(LocalDate v) { this.dateDebutSouhaite = v; }

    public UploadedFile getCvFichier() { return cvFichier; }
    public void setCvFichier(UploadedFile f) { this.cvFichier = f; }
    public UploadedFile getLettreFichier() { return lettreFichier; }
    public void setLettreFichier(UploadedFile f) { this.lettreFichier = f; }
    public UploadedFile getPieceIdentiteFichier() { return pieceIdentiteFichier; }
    public void setPieceIdentiteFichier(UploadedFile f) { this.pieceIdentiteFichier = f; }
    public UploadedFile getDiplomesFichier() { return diplomesFichier; }
    public void setDiplomesFichier(UploadedFile f) { this.diplomesFichier = f; }
    public UploadedFile getCertificatsTravailFichier() { return certificatsTravailFichier; }
    public void setCertificatsTravailFichier(UploadedFile f) { this.certificatsTravailFichier = f; }
    public UploadedFile getCasierJudiciaireFichier() { return casierJudiciaireFichier; }
    public void setCasierJudiciaireFichier(UploadedFile f) { this.casierJudiciaireFichier = f; }
    public UploadedFile getLettreEtablissementFichier() { return lettreEtablissementFichier; }
    public void setLettreEtablissementFichier(UploadedFile f) { this.lettreEtablissementFichier = f; }
    public UploadedFile getReleveNotesFichier() { return releveNotesFichier; }
    public void setReleveNotesFichier(UploadedFile f) { this.releveNotesFichier = f; }
}