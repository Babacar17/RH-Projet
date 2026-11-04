package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.DocumentArchive;
import com.soutenance.entity.Employe;
import com.soutenance.entity.ModeleDocument;
import com.soutenance.service.DocumentService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletResponse;
import org.primefaces.model.file.UploadedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean JSF — Gestion documentaire.
 *
 * Fonctionnalités couvertes (point 10 du cahier des charges) :
 *  - Bibliothèque de modèles de documents (contrats, attestations, lettres…)
 *  - Génération automatique de documents officiels pré-remplis
 *  - Archivage numérique des dossiers employés (upload + métadonnées)
 *  - Téléchargement sécurisé des documents archivés
 *  - Signature électronique (déclenchement du processus)
 *  - Filtrage et recherche dans la GED
 *  - Suppression avec traçabilité (soft-delete)
 *
 * NOTE : Entités attendues dans com.soutenance.entity :
 *   ModeleDocument : id, titre, typeDocument, contenuHtml (template Thymeleaf/FreeMarker),
 *                    actif (bool), creePar, dateCreation, variables (List<String>)
 *   DocumentArchive: id, employe, titre, typeDocument, cheminFichier, taille,
 *                    dateDepot, deposepar, signe (bool), dateSignature, actif (bool)
 */
@Component("documentBean")
@Scope("view")
public class DocumentBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private DocumentService documentService;
    @Autowired private SessionBean     sessionBean;

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<ModeleDocument>  modeles;
    private List<DocumentArchive> archives;
    private List<DocumentArchive> archivesFiltrees;
    private List<Employe>         employes;

    // ── Entités en cours d'édition ───────────────────────────────────────────
    private ModeleDocument  modele           = new ModeleDocument();
    private DocumentArchive documentArchive  = new DocumentArchive();

    // ── Génération de document ───────────────────────────────────────────────
    private Long    employeIdGeneration;
    private Long    modeleIdGeneration;
    private String  typeDocumentGeneration;
    private Map<String, String> variablesSupplementaires;

    // ── Upload fichier ────────────────────────────────────────────────────────
    private UploadedFile fichierUpload;
    private Long         employeIdUpload;
    private String       titreUpload;
    private String       typeDocumentUpload;

    // ── Filtres ──────────────────────────────────────────────────────────────
    private String  filtreTypeDocument;
    private String  filtreEmploye;
    private Long    filtreEmployeId;
    private boolean filtreSigneUniquement = false;

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean modeEditionModele  = false;
    private DocumentArchive documentSelectionne;
    private ModeleDocument  modeleSelectionne;

    // ── Statistiques ─────────────────────────────────────────────────────────
    private long totalDocuments;
    private long documentsSignes;
    private long totalModeles;

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("DocumentBean init error: " + e.getMessage());
            modeles          = new ArrayList<>();
            archives         = new ArrayList<>();
            archivesFiltrees = new ArrayList<>();
            employes         = new ArrayList<>();
        }
    }

    private void chargerTout() {
        Long institutionId = sessionBean.getInstitutionId();
        modeles          = documentService.getTousModeles(institutionId);
        archives         = documentService.getTousDocuments(institutionId);
        archivesFiltrees = archives;
        employes         = documentService.getEmployesPourGed(institutionId);
        totalDocuments   = archives.size();
        // CORRECTION 1 : DocumentArchive n'a pas de champ boolean signe.
        // Le statut "SIGNE" est tracké via le champ String statut.
        documentsSignes  = archives.stream()
            .filter(d -> "SIGNE".equals(d.getStatut())).count();
        totalModeles     = modeles.size();
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "rh";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // MODÈLES DE DOCUMENTS
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauModele() {
        modele            = new ModeleDocument();
        modele.setActif(true);
        modeEditionModele = false;
    }

    public void preparerEditionModele(ModeleDocument m) {
        this.modele        = documentService.getModeleById(m.getId());
        if (this.modele == null) this.modele = m;
        modeEditionModele  = true;
    }

    public String sauvegarderModele() {
        try {
            if (modele.getTitre() == null || modele.getTitre().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le titre du modèle est obligatoire.");
                return null;
            }
            if (modele.getTypeDocument() == null || modele.getTypeDocument().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le type de document est obligatoire.");
                return null;
            }
            // CORRECTION 2 : ModeleDocument n'a pas de champ contenuHtml.
            // Le champ équivalent est contenuTemplate.
            if (modele.getContenuTemplate() == null || modele.getContenuTemplate().isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Le contenu du modèle est obligatoire.");
                return null;
            }
            if (modeEditionModele) {
                documentService.modifierModele(modele, getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Modèle modifié",
                    modele.getTitre() + " mis à jour.");
            } else {
                documentService.creerModele(modele, sessionBean.getInstitutionId(), getOp());
                addMsg(FacesMessage.SEVERITY_INFO, "Modèle créé",
                    modele.getTitre() + " ajouté à la bibliothèque.");
            }
            chargerTout();
            modele = new ModeleDocument();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void toggleActifModele(Long id) {
        try {
            documentService.toggleActifModele(id, getOp());
            modeles = documentService.getTousModeles(sessionBean.getInstitutionId());
            addMsg(FacesMessage.SEVERITY_INFO, "Statut modifié",
                "Le modèle a été activé/désactivé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void supprimerModele(Long id) {
        try {
            documentService.supprimerModele(id, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Modèle supprimé",
                "Le modèle a été retiré de la bibliothèque.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void selectionnerModele(ModeleDocument m) {
        this.modeleSelectionne = m;
    }

    // ════════════════════════════════════════════════════════════
    // GÉNÉRATION AUTOMATIQUE DE DOCUMENTS
    // ════════════════════════════════════════════════════════════

    /**
     * Prépare le formulaire de génération pour un employé et un modèle donnés.
     */
    public void preparerGeneration(Long employeId, Long modeleId) {
        this.employeIdGeneration = employeId;
        this.modeleIdGeneration  = modeleId;
    }

    /**
     * Génère un document officiel en PDF à partir d'un modèle et des données
     * de l'employé sélectionné, puis le pousse dans la réponse HTTP.
     */
    public void genererDocument() {
        if (employeIdGeneration == null || modeleIdGeneration == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Veuillez sélectionner un employé et un modèle.");
            return;
        }
        try {
            // CORRECTION 1 : genererPdf(ModeleDocument, Employe, String) attend des entités,
            // pas des Long. On résout les IDs avant l'appel.
            // Le paramètre variablesSupplementaires n'est pas accepté par cette signature.
            ModeleDocument m = documentService.getModeleById(modeleIdGeneration);
            if (m == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Modèle introuvable.");
                return;
            }
            Employe emp = documentService.getEmployeById(employeIdGeneration);
            if (emp == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Employé introuvable.");
                return;
            }
            byte[] pdf = documentService.genererPdf(m, emp, getOp());

            String nomFichier = m.getTitre().replaceAll("\\s+", "_")
                + "_" + LocalDate.now() + ".pdf";

            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nomFichier + "\"");
            response.setContentLength(pdf.length);

            try (OutputStream os = response.getOutputStream()) {
                os.write(pdf);
                os.flush();
            }
            fc.responseComplete();

            // NOTE : archiverDocumentGenere(Long documentId, String operateur) attend un ID
            // de document déjà persisté. Le PDF ici est streamé directement au navigateur
            // sans être sauvegardé en base — il n'y a donc pas d'ID disponible.
            // Pour archiver automatiquement, il faudrait appeler sauvegarderDocument()
            // avant le stream et récupérer l'ID généré. Omis ici faute d'ID valide.

        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur de génération",
                "Impossible de générer le document : " + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // ARCHIVAGE NUMÉRIQUE (UPLOAD)
    // ════════════════════════════════════════════════════════════

    /**
     * Prépare le formulaire d'upload pour un employé donné.
     */
    public void preparerUpload(Long employeId) {
        this.employeIdUpload    = employeId;
        this.fichierUpload      = null;
        this.titreUpload        = null;
        this.typeDocumentUpload = null;
    }

    /**
     * Sauvegarde un fichier uploadé dans l'archive numérique de l'employé.
     * Contrôles : fichier non vide, titre obligatoire, taille max 10 Mo.
     */
    public String archiverDocument() {
        try {
            if (fichierUpload == null || fichierUpload.getSize() == 0) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Aucun fichier sélectionné.");
                return null;
            }
            if (fichierUpload.getSize() > 10 * 1024 * 1024) { // 10 Mo
                addMsg(FacesMessage.SEVERITY_ERROR, "Fichier trop lourd",
                    "La taille maximale autorisée est de 10 Mo.");
                return null;
            }
            if (titreUpload == null || titreUpload.isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Le titre du document est obligatoire.");
                return null;
            }
            if (employeIdUpload == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Aucun employé sélectionné.");
                return null;
            }

            // CORRECTION 3 : archiverDocument(DocumentArchive, String, String) attend
            // une entité pré-construite, pas des champs éclatés.
            // On résout l'Employe et on assemble le DocumentArchive avant l'appel.
            Employe empUpload = documentService.getEmployeById(employeIdUpload);
            if (empUpload == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Employé introuvable.");
                return null;
            }
            DocumentArchive archive = new DocumentArchive();
            archive.setTitre(titreUpload);
            archive.setTypeDocument(typeDocumentUpload);
            archive.setNomFichier(fichierUpload.getFileName());
            archive.setContenu(fichierUpload.getContent());
            archive.setTailleFichier(fichierUpload.getSize());
            archive.setTypeMime(fichierUpload.getContentType());
            archive.setEmploye(empUpload);
            archive.setDeposePar(getOp());

            documentService.archiverDocument(archive, fichierUpload.getContentType(), getOp());

            chargerTout();
            fichierUpload      = null;
            titreUpload        = null;
            typeDocumentUpload = null;
            addMsg(FacesMessage.SEVERITY_INFO, "Document archivé",
                "Le fichier a été ajouté au dossier numérique.");
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur d'archivage", ex.getMessage());
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════
    // TÉLÉCHARGEMENT D'UN DOCUMENT ARCHIVÉ
    // ════════════════════════════════════════════════════════════

    /**
     * Télécharge un document archivé depuis le stockage serveur.
     * Journalise l'accès dans les logs d'audit.
     */
    public void telechargerDocument(Long documentId) {
        try {
            DocumentArchive doc = documentService.getDocumentById(documentId);
            if (doc == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Document introuvable.");
                return;
            }
            byte[] contenu = documentService.lireContenu(doc, getOp());

            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response =
                (HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + doc.getTitre() + "\"");
            response.setContentLength(contenu.length);

            try (OutputStream os = response.getOutputStream()) {
                os.write(contenu);
                os.flush();
            }
            fc.responseComplete();
        } catch (IOException ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur de téléchargement",
                ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // SIGNATURE ÉLECTRONIQUE
    // ════════════════════════════════════════════════════════════

    /**
     * Déclenche le processus de signature électronique pour un document.
     * Envoie une demande de signature au signataire désigné.
     */
    public void demanderSignature(Long documentId, String emailSignataire) {
        try {
            if (emailSignataire == null || emailSignataire.isBlank()) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "L'adresse email du signataire est obligatoire.");
                return;
            }
            documentService.demanderSignature(documentId, emailSignataire, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Demande envoyée",
                "Une demande de signature a été transmise à " + emailSignataire + ".");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /**
     * Marque un document comme signé manuellement (visa physique scanné).
     */
    public void marquerCommeSigne(Long documentId) {
        try {
            documentService.marquerCommeSigne(documentId, getOp());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Document signé",
                "Le document a été marqué comme signé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // SUPPRESSION (SOFT DELETE)
    // ════════════════════════════════════════════════════════════

    public void supprimerDocument(Long id) {
        try {
            documentService.archiverSuppression(id, getOp()); // soft-delete traçable
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Document supprimé",
                "Le document a été retiré de l'archive active.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // FILTRAGE & RECHERCHE
    // ════════════════════════════════════════════════════════════

    public void filtrer() {
        archivesFiltrees = archives.stream()
            .filter(d -> {
                if (filtreTypeDocument != null && !filtreTypeDocument.isBlank()
                    && !filtreTypeDocument.equals(d.getTypeDocument())) return false;
                if (filtreEmployeId != null
                    && (d.getEmploye() == null || !filtreEmployeId.equals(d.getEmploye().getId())))
                    return false;
                // CORRECTION 1 : pas de isSigne() — on compare statut
                if (filtreSigneUniquement && !"SIGNE".equals(d.getStatut())) return false;
                if (filtreEmploye != null && !filtreEmploye.isBlank()) {
                    String q = filtreEmploye.toLowerCase();
                    String nom = d.getEmploye() != null
                        ? d.getEmploye().getNomComplet().toLowerCase() : "";
                    if (!nom.contains(q)) return false;
                }
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    public void reinitialiserFiltres() {
        filtreTypeDocument    = null;
        filtreEmployeId       = null;
        filtreEmploye         = null;
        filtreSigneUniquement = false;
        archivesFiltrees      = archives;
    }

    public void selectionnerDocument(DocumentArchive d) {
        this.documentSelectionne = documentService.getDocumentById(d.getId());
        if (this.documentSelectionne == null) this.documentSelectionne = d;
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    public String getIconeTypeDocument(String type) {
        if (type == null) return "fa-file";
        return switch (type) {
            case "CONTRAT"      -> "fa-file-contract";
            case "ATTESTATION"  -> "fa-certificate";
            case "BULLETIN"     -> "fa-file-invoice-dollar";
            case "LETTRE"       -> "fa-envelope-open-text";
            case "DIPLOME"      -> "fa-graduation-cap";
            case "FICHE_POSTE"  -> "fa-clipboard-list";
            case "IDENTITE"     -> "fa-id-card";
            default             -> "fa-file-alt";
        };
    }

    public String getLabelTypeDocument(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "CONTRAT"     -> "Contrat de travail";
            case "ATTESTATION" -> "Attestation";
            case "BULLETIN"    -> "Bulletin de paie";
            case "LETTRE"      -> "Lettre";
            case "DIPLOME"     -> "Diplôme";
            case "FICHE_POSTE" -> "Fiche de poste";
            case "IDENTITE"    -> "Pièce d'identité";
            default            -> type;
        };
    }

    public String formatTaille(long octets) {
        if (octets < 1024)       return octets + " o";
        if (octets < 1024 * 1024) return (octets / 1024) + " Ko";
        return String.format("%.1f Mo", octets / (1024.0 * 1024.0));
    }

    /** Taux de documents signés (pour barre de progression). */
    public long getTauxSignature() {
        if (totalDocuments == 0) return 0;
        return documentsSignes * 100 / totalDocuments;
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<ModeleDocument>  getModeles()              { return modeles; }
    public List<DocumentArchive> getArchives()             { return archives; }
    public List<DocumentArchive> getArchivesFiltrees()     { return archivesFiltrees; }
    public List<Employe>         getEmployes()             { return employes; }

    public ModeleDocument  getModele()                     { return modele; }
    public void setModele(ModeleDocument m)                { this.modele = m; }
    public DocumentArchive getDocumentArchive()            { return documentArchive; }
    public void setDocumentArchive(DocumentArchive d)      { this.documentArchive = d; }

    public DocumentArchive getDocumentSelectionne()        { return documentSelectionne; }
    public void setDocumentSelectionne(DocumentArchive d)  { this.documentSelectionne = d; }
    public ModeleDocument  getModeleSelectionne()          { return modeleSelectionne; }
    public void setModeleSelectionne(ModeleDocument m)     { this.modeleSelectionne = m; }

    public Long    getEmployeIdGeneration()                { return employeIdGeneration; }
    public void    setEmployeIdGeneration(Long id)         { this.employeIdGeneration = id; }
    public Long    getModeleIdGeneration()                 { return modeleIdGeneration; }
    public void    setModeleIdGeneration(Long id)          { this.modeleIdGeneration = id; }
    public String  getTypeDocumentGeneration()             { return typeDocumentGeneration; }
    public void    setTypeDocumentGeneration(String t)     { this.typeDocumentGeneration = t; }
    public Map<String, String> getVariablesSupplementaires() { return variablesSupplementaires; }
    public void setVariablesSupplementaires(Map<String, String> m) { this.variablesSupplementaires = m; }

    public UploadedFile getFichierUpload()                 { return fichierUpload; }
    public void setFichierUpload(UploadedFile f)           { this.fichierUpload = f; }
    public Long    getEmployeIdUpload()                    { return employeIdUpload; }
    public void    setEmployeIdUpload(Long id)             { this.employeIdUpload = id; }
    public String  getTitreUpload()                        { return titreUpload; }
    public void    setTitreUpload(String t)                { this.titreUpload = t; }
    public String  getTypeDocumentUpload()                 { return typeDocumentUpload; }
    public void    setTypeDocumentUpload(String t)         { this.typeDocumentUpload = t; }

    public String  getFiltreTypeDocument()                 { return filtreTypeDocument; }
    public void    setFiltreTypeDocument(String f)         { this.filtreTypeDocument = f; }
    public String  getFiltreEmploye()                      { return filtreEmploye; }
    public void    setFiltreEmploye(String f)              { this.filtreEmploye = f; }
    public Long    getFiltreEmployeId()                    { return filtreEmployeId; }
    public void    setFiltreEmployeId(Long id)             { this.filtreEmployeId = id; }
    public boolean isFiltreSigneUniquement()               { return filtreSigneUniquement; }
    public void    setFiltreSigneUniquement(boolean b)     { this.filtreSigneUniquement = b; }

    public boolean isModeEditionModele()                   { return modeEditionModele; }

    public long getTotalDocuments()                        { return totalDocuments; }
    public long getDocumentsSignes()                       { return documentsSignes; }
    public long getTotalModeles()                          { return totalModeles; }
}