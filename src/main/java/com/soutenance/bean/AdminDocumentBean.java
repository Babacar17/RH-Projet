package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.*;
import com.soutenance.service.AdminDocumentService;
import com.soutenance.service.DocumentService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean JSF — Gestion documentaire (périmètre administrateur).
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. Propriétés XHTML manquantes ajoutées :
 *    - #{adminDocumentBean.totalArchives}     → documents statut ARCHIVE
 *    - #{adminDocumentBean.totalGeneres}      → documents générés depuis modèle
 *    - #{adminDocumentBean.totalEnSignature}  → documents EN_SIGNATURE
 *    - #{adminDocumentBean.totalExpires}      → documents expirés
 *    - #{adminDocumentBean.newModele.*}       → alias de modele (XHTML utilise newModele)
 *    - #{adminDocumentBean.handleModeleUpload}→ listener PrimeFaces FileUpload pour modèles
 *
 * 2. genererDocument() branchée sur DocumentService.substituer() :
 *    - Avant : transmettait le contenu brut du template sans substitution
 *    - Après : appelle documentService.substituer(template, employe) pour
 *      remplacer {{NOM}}, {{DATE_JOUR}}, {{POSTE}}... avant sauvegarde
 *
 * 3. handleModeleUpload() : lecture du fichier uploadé pour initialiser
 *    le contenu du modèle via PrimeFaces FileUploadEvent.
 */
@Component("adminDocumentBean")
@Scope("view")
public class AdminDocumentBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminDocumentBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminDocumentService documentService;
    @Autowired private DocumentService      templateService; // pour substitution
    @Autowired private SessionBean          sessionBean;

    // ── Modèles de documents ──────────────────────────────────────────────
    private List<ModeleDocument>  modeles           = new ArrayList<>();
    private ModeleDocument        modele            = new ModeleDocument();
    private ModeleDocument        modeleSelectionne;
    private boolean               modeEditionModele = false;

    // ── Documents GED ─────────────────────────────────────────────────────
    private List<DocumentArchive> documents         = new ArrayList<>();
    private DocumentArchive       document          = new DocumentArchive();
    private DocumentArchive       documentSelectionne;
    private boolean               modeEditionDoc    = false;

    // ── Upload fichier ────────────────────────────────────────────────────
    private UploadedFile          fichierUploade;
    private Long                  employeDocId;

    // ── Génération depuis modèle ──────────────────────────────────────────
    private Long                  modeleGenerId;
    private Long                  employeGenerId;

    // ── Filtres GED ───────────────────────────────────────────────────────
    private String                filtreCategorie;
    private String                filtreStatutDoc;
    private String                filtreEntite;
    private Long                  filtreEmployeId;
    private boolean               filtreExpires     = false;
    private boolean               filtreNonSignes   = false;
    private String                rechercheDoc;

    // ── Stats KPI dashboard ───────────────────────────────────────────────
    private long totalDocuments;
    private long totalModeles;

    // CORRECTION 1 : stats manquantes pour le dashboard documents.xhtml
    private long totalArchives;       // #{adminDocumentBean.totalArchives}
    private long totalGeneres;        // #{adminDocumentBean.totalGeneres}
    private long totalEnSignature;    // #{adminDocumentBean.totalEnSignature}
    private long totalExpires;        // #{adminDocumentBean.totalExpires}

    // ── Listes de support ─────────────────────────────────────────────────
    private List<Employe>         employes          = new ArrayList<>();

    @PostConstruct
    public void init() {
        chargerTout();
    }

    private void chargerTout() {
        try {
            Long instId = sessionBean.getInstitutionId();
            modeles   = documentService.getModelesByInstitution(instId);
            documents = documentService.getDocumentsByInstitution(instId);
            employes  = documentService.getEmployesActifs(instId);
            calculerStats();
        } catch (Exception e) {
            LOG.error("Erreur inattendue", e);
            modeles = new ArrayList<>(); documents = new ArrayList<>(); employes = new ArrayList<>();
        }
    }

    private void calculerStats() {
        totalDocuments   = documents.size();
        totalModeles     = modeles.size();

        // CORRECTION 1 : calcul des stats manquantes
        totalArchives     = documents.stream()
            .filter(d -> "ARCHIVE".equals(d.getStatut())).count();
        totalEnSignature  = documents.stream()
            .filter(d -> "EN_SIGNATURE".equals(d.getStatut())).count();
        totalExpires      = documents.stream()
            .filter(DocumentArchive::isExpire).count();
        // totalGeneres : documents qui ont été créés depuis un modèle
        // (heuristique : typeDocument non null = généré depuis modèle)
        totalGeneres      = documents.stream()
            .filter(d -> d.getTypeDocument() != null && !d.getTypeDocument().isBlank()).count();
    }

    private String getOp() {
        return sessionBean.getUtilisateurConnecte() != null
            ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    // ════════════════════════════════════════════════════════════
    // MODÈLES DE DOCUMENTS
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauModele() {
        modele = new ModeleDocument(); modeEditionModele = false;
    }

    public void preparerEditionModele(ModeleDocument md) {
        this.modele = documentService.getModeleById(md.getId());
        if (this.modele == null) this.modele = md;
        modeEditionModele = true;
    }

    public String sauvegarderModele() {
        try {
            if (modele.getTitre() == null || modele.getTitre().isBlank()) {
                addError("Le titre du modèle est obligatoire."); return null;
            }
            Long instId = sessionBean.getInstitutionId();
            if (modeEditionModele) {
                documentService.modifierModele(modele, getOp());
                addInfo("Modèle mis à jour.");
            } else {
                documentService.creerModele(modele, instId, getOp());
                addInfo("Modèle créé.");
            }
            chargerTout();
            modele = new ModeleDocument();
        } catch (Exception ex) { addError("Erreur : " + ex.getMessage()); }
        return null;
    }

    /**
     * CORRECTION 3 : Listener PrimeFaces pour l'upload du contenu d'un modèle.
     * Appelé via #{adminDocumentBean.handleModeleUpload} dans un p:fileUpload.
     *
     * PrimeFaces appelle cette méthode avec un FileUploadEvent contenant le fichier.
     * Le contenu est stocké dans modele.contenuTemplate pour être sauvegardé ensuite.
     */
    public void handleModeleUpload(FileUploadEvent event) {
        try {
            UploadedFile f = event.getFile();
            if (f != null && f.getSize() > 0) {
                // Lire le contenu comme texte UTF-8 (les modèles sont du texte/HTML)
                String contenu = new String(f.getContent(), java.nio.charset.StandardCharsets.UTF_8);
                modele.setContenuTemplate(contenu);
                addInfo("Fichier modèle chargé : " + f.getFileName()
                    + " (" + f.getSize() + " octets)");
            }
        } catch (Exception ex) {
            addError("Erreur chargement fichier modèle : " + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // GÉNÉRATION AVEC MOTEUR DE TEMPLATE
    // ════════════════════════════════════════════════════════════

    /**
     * CORRECTION 2 : génération avec substitution des variables {{NOM}}.
     *
     * AVANT : transmettait le contenu brut du template au service,
     *   le document généré contenait les placeholders {{NOM}} non remplacés.
     *
     * APRÈS :
     *   1. Charge le modèle et l'employé
     *   2. Appelle templateService.substituer() pour remplacer {{NOM}},
     *      {{DATE_JOUR}}, {{POSTE}}, {{INSTITUTION_NOM}}, etc.
     *   3. Sauvegarde le document avec le contenu substitué
     */
    public String genererDocument() {
        try {
            if (modeleGenerId == null) {
                addError("Sélectionnez un modèle."); return null;
            }
            Long instId = sessionBean.getInstitutionId();

            // Charger le modèle
            ModeleDocument modeleChoisi = documentService.getModeleById(modeleGenerId);
            if (modeleChoisi == null) {
                addError("Modèle introuvable."); return null;
            }

            // Charger l'employé si sélectionné
            Employe employe = null;
            if (employeGenerId != null) {
                employe = trouverEmploye(employeGenerId);
            }

            // CORRECTION 2 : substitution des variables avant sauvegarde
            String contenuBrut  = modeleChoisi.getContenuTemplate();
            String contenuFinal = templateService.substituer(contenuBrut, employe);

            // Créer le DocumentArchive avec le contenu substitué
            DocumentArchive docGenere = new DocumentArchive();
            docGenere.setTitre(modeleChoisi.getTitre()
                + (employe != null ? " — " + employe.getNomComplet() : ""));
            docGenere.setTypeDocument(modeleChoisi.getTypeDocument());
            docGenere.setInstitutionId(instId);
            docGenere.setDateDepot(LocalDate.now());
            docGenere.setStatut("ACTIF");
            docGenere.setDeposePar(getOp());
            docGenere.setEmploye(employe);
            docGenere.setContenu(
                contenuFinal.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Sauvegarder via le service (upload sans fichier physique)
            String erreur = documentService.uploadDocument(
                docGenere,
                docGenere.getContenu(),
                instId,
                getOp());

            if (erreur != null) { addError(erreur); return null; }

            addInfo("Document généré avec substitution des variables : " + docGenere.getTitre());
            chargerTout();
            modeleGenerId  = null;
            employeGenerId = null;
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addError("Erreur génération : " + ex.getMessage());
        }
        return null;
    }

    /**
     * Prévisualise le résultat de la substitution avant génération.
     * Retourne le contenu avec les variables remplacées pour un employe donné.
     */
    public String previsualiserTemplate() {
        if (modeleGenerId == null) return "Sélectionnez un modèle.";
        ModeleDocument m = documentService.getModeleById(modeleGenerId);
        if (m == null || m.getContenuTemplate() == null) return "Modèle vide.";
        Employe emp = (employeGenerId != null) ? trouverEmploye(employeGenerId) : null;
        String apercu = templateService.substituer(m.getContenuTemplate(), emp);
        // Retourne les 500 premiers caractères pour l'aperçu
        return apercu.length() > 500 ? apercu.substring(0, 500) + "..." : apercu;
    }

    // ════════════════════════════════════════════════════════════
    // DOCUMENTS GED
    // ════════════════════════════════════════════════════════════

    public void preparerUploadDocument() {
        document = new DocumentArchive(); fichierUploade = null;
        employeDocId = null; modeEditionDoc = false;
    }

    public void preparerEditionDocument(DocumentArchive d) {
        this.document      = documentService.getDocumentById(d.getId());
        if (this.document == null) this.document = d;
        this.employeDocId  = (d.getEmploye() != null) ? d.getEmploye().getId() : null;
        this.modeEditionDoc = true;
    }

    public String sauvegarderDocument() {
        try {
            if (document.getTitre() == null || document.getTitre().isBlank()) {
                addError("Le titre est obligatoire."); return null;
            }
            Long instId = sessionBean.getInstitutionId();
            if (employeDocId != null) document.setEmploye(trouverEmploye(employeDocId));

            if (modeEditionDoc) {
                documentService.modifierDocument(document, getOp());
                addInfo("Document mis à jour.");
            } else {
                if (fichierUploade == null || fichierUploade.getSize() == 0) {
                    addError("Sélectionnez un fichier."); return null;
                }
                document.setNomFichier(fichierUploade.getFileName());
                document.setTypeMime(fichierUploade.getContentType());
                String erreur = documentService.uploadDocument(
                    document, fichierUploade.getContent(), instId, getOp());
                if (erreur != null) { addError(erreur); return null; }
                addInfo("Document téléversé.");
            }
            chargerTout();
            document = new DocumentArchive();
        } catch (Exception ex) { addError("Erreur : " + ex.getMessage()); }
        return null;
    }

    public void rechercherDocuments() {
        try {
            Long instId = sessionBean.getInstitutionId();
            List<DocumentArchive> source = documentService.getDocumentsByInstitution(instId);
            documents = source.stream()
                .filter(d -> {
                    if (rechercheDoc != null && !rechercheDoc.isBlank()) {
                        String q = rechercheDoc.toLowerCase();
                        boolean matchNom  = d.getTitre()     != null && d.getTitre().toLowerCase().contains(q);
                        boolean matchFich = d.getNomFichier() != null && d.getNomFichier().toLowerCase().contains(q);
                        if (!matchNom && !matchFich) return false;
                    }
                    if (filtreCategorie != null && !filtreCategorie.isBlank()
                        && !filtreCategorie.equals(d.getTypeDocument())) return false;
                    if (filtreStatutDoc != null && !filtreStatutDoc.isBlank()
                        && !filtreStatutDoc.equals(d.getStatut())) return false;
                    if (filtreEmployeId != null
                        && (d.getEmploye() == null || !filtreEmployeId.equals(d.getEmploye().getId())))
                        return false;
                    if (filtreExpires   && !d.isExpire()) return false;
                    if (filtreNonSignes && !"EN_SIGNATURE".equals(d.getStatut())) return false;
                    return true;
                })
                .collect(Collectors.toList());
            calculerStats();
        } catch (Exception ex) { addError("Erreur recherche : " + ex.getMessage()); }
    }

    public void marquerSigne(Long id) {
        try {
            documentService.marquerSigne(id, LocalDate.now(), getOp());
            chargerTout();
            addInfo("Document marqué comme signé.");
        } catch (Exception ex) { addError("Erreur : " + ex.getMessage()); }
    }

    public void changerStatut(Long id, String statut) {
        try {
            documentService.changerStatutDocument(id, statut, getOp());
            chargerTout();
            addInfo("Statut mis à jour : " + statut);
        } catch (Exception ex) { addError("Erreur : " + ex.getMessage()); }
    }

    public void supprimerDocument(Long id) {
        try {
            documentService.supprimerDocument(id, getOp());
            chargerTout();
            addInfo("Document supprimé.");
        } catch (Exception ex) { addError("Erreur : " + ex.getMessage()); }
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private Employe trouverEmploye(Long id) {
        if (id == null || employes == null) return null;
        return employes.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Succès", msg));
    }
    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", msg));
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<ModeleDocument>  getModeles()               { return modeles; }
    public ModeleDocument        getModele()                { return modele; }
    public void                  setModele(ModeleDocument m){ this.modele = m; }

    // CORRECTION 1 : alias newModele → modele (XHTML utilise #{adminDocumentBean.newModele.*})
    public ModeleDocument        getNewModele()             { return modele; }
    public void                  setNewModele(ModeleDocument m) { this.modele = m; }

    public ModeleDocument        getModeleSelectionne()     { return modeleSelectionne; }
    public void                  setModeleSelectionne(ModeleDocument m) { this.modeleSelectionne = m; }
    public boolean               isModeEditionModele()      { return modeEditionModele; }

    public List<DocumentArchive> getDocuments()             { return documents; }
    public DocumentArchive       getDocument()              { return document; }
    public void                  setDocument(DocumentArchive d) { this.document = d; }
    public DocumentArchive       getDocumentSelectionne()   { return documentSelectionne; }
    public void                  setDocumentSelectionne(DocumentArchive d) { this.documentSelectionne = d; }
    public boolean               isModeEditionDoc()         { return modeEditionDoc; }

    public UploadedFile          getFichierUploade()        { return fichierUploade; }
    public void                  setFichierUploade(UploadedFile f) { this.fichierUploade = f; }
    public Long                  getEmployeDocId()          { return employeDocId; }
    public void                  setEmployeDocId(Long id)   { this.employeDocId = id; }

    public Long                  getModeleGenerId()         { return modeleGenerId; }
    public void                  setModeleGenerId(Long id)  { this.modeleGenerId = id; }
    public Long                  getEmployeGenerId()        { return employeGenerId; }
    public void                  setEmployeGenerId(Long id) { this.employeGenerId = id; }

    public String                getFiltreCategorie()       { return filtreCategorie; }
    public void                  setFiltreCategorie(String s){ this.filtreCategorie = s; }
    public String                getFiltreStatutDoc()       { return filtreStatutDoc; }
    public void                  setFiltreStatutDoc(String s){ this.filtreStatutDoc = s; }
    public String                getFiltreEntite()          { return filtreEntite; }
    public void                  setFiltreEntite(String s)  { this.filtreEntite = s; }
    public Long                  getFiltreEmployeId()       { return filtreEmployeId; }
    public void                  setFiltreEmployeId(Long id){ this.filtreEmployeId = id; }
    public boolean               isFiltreExpires()          { return filtreExpires; }
    public void                  setFiltreExpires(boolean b){ this.filtreExpires = b; }
    public boolean               isFiltreNonSignes()        { return filtreNonSignes; }
    public void                  setFiltreNonSignes(boolean b) { this.filtreNonSignes = b; }
    public String                getRechercheDoc()          { return rechercheDoc; }
    public void                  setRechercheDoc(String s)  { this.rechercheDoc = s; }

    // ── Stats KPI (CORRECTION 1 : tous les getters manquants) ──────────────
    public long getTotalDocuments()       { return totalDocuments; }
    public long getTotalModeles()         { return totalModeles; }
    public long getTotalArchives()        { return totalArchives; }       // ← manquait
    public long getTotalGeneres()         { return totalGeneres; }        // ← manquait
    public long getTotalEnSignature()     { return totalEnSignature; }    // ← manquait
    public long getTotalExpires()         { return totalExpires; }        // ← manquait

    public List<Employe>         getEmployes()              { return employes; }
}