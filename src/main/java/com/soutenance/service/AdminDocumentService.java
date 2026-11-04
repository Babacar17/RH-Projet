package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service de gestion documentaire — périmètre administrateur.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. genererDocument() — moteur de template branché.
 *    AVANT : contenu du modèle copié tel quel → les variables {{NOM}},
 *    {{DATE_JOUR}}, etc. restaient dans le document généré.
 *
 *    APRÈS : injection de DocumentService (qui contient le moteur
 *    de substitution {{VARIABLE}} → valeur réelle).
 *    La substitution est effectuée AVANT la sauvegarde du document.
 *
 * 2. getEmployesActifs() — filtre par institution.
 *    AVANT : employeDao.findActifs() retournait tous les employés.
 *    APRÈS : employeDao.findActifsByInstitution(institutionId) filtre
 *    correctement par institution.
 */
@Service
@Transactional
public class AdminDocumentService {

    @Autowired private DocumentArchiveDao documentDao;
    @Autowired private ModeleDocumentDao  modeleDao;
    @Autowired private EmployeDao         employeDao;
    @Autowired private LogActionDao       logActionDao;

    // CORRECTION 1 : injection du service qui contient le moteur de template
    @Autowired private DocumentService    documentService;

    // ══════════════════════════════════════════════════
    // DOCUMENTS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<DocumentArchive> getDocumentsByInstitution(Long institutionId) {
        return documentDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public DocumentArchive getDocumentById(Long id) {
        return documentDao.findById(id);
    }

    public String uploadDocument(DocumentArchive document, byte[] contenu,
                                  Long institutionId, String operateur) {
        if (document.getTitre() == null || document.getTitre().isBlank())
            return "Le titre du document est obligatoire.";
        document.setContenu(contenu);
        document.setInstitutionId(institutionId);
        document.setDateDepot(LocalDate.now());
        document.setStatut("ACTIF");
        document.setDeposePar(operateur);
        documentDao.save(document);
        logActionDao.save(LogAction.of(operateur, "UPLOAD_DOCUMENT",
            "Document déposé : " + document.getTitre()));
        return null;
    }

    public void modifierDocument(DocumentArchive document, String operateur) {
        documentDao.update(document);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_DOCUMENT",
            "Document modifié : " + document.getTitre()));
    }

    public void changerStatutDocument(Long id, String statut, String operateur) {
        DocumentArchive doc = documentDao.findById(id);
        if (doc != null) {
            doc.setStatut(statut);
            if ("ARCHIVE".equals(statut)) doc.setDateArchivage(LocalDate.now());
            documentDao.update(doc);
            logActionDao.save(LogAction.of(operateur, "STATUT_DOCUMENT",
                "Statut → " + statut + " : " + doc.getTitre()));
        }
    }

    public void supprimerDocument(Long id, String operateur) {
        DocumentArchive doc = documentDao.findById(id);
        if (doc != null) {
            logActionDao.save(LogAction.of(operateur, "SUPPRESSION_DOCUMENT",
                "Document supprimé : " + doc.getTitre()));
            documentDao.delete(id);
        }
    }

    public void marquerSigne(Long id, LocalDate dateSig, String operateur) {
        DocumentArchive doc = documentDao.findById(id);
        if (doc != null) {
            doc.setStatut("SIGNE");
            doc.setDateSignature(dateSig);
            documentDao.update(doc);
            logActionDao.save(LogAction.of(operateur, "SIGNATURE_DOCUMENT",
                "Document signé : " + doc.getTitre()));
        }
    }

    // ══════════════════════════════════════════════════
    // MODÈLES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ModeleDocument> getModelesByInstitution(Long institutionId) {
        return modeleDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public ModeleDocument getModeleById(Long id) {
        return modeleDao.findById(id);
    }

    public void creerModele(ModeleDocument modele, Long institutionId, String operateur) {
        modele.setInstitutionId(institutionId);
        modele.setCreePar(operateur);
        modele.setDateCreation(LocalDate.now());
        modele.setStatut("ACTIF");
        modele.setActif(true);
        modeleDao.save(modele);
        logActionDao.save(LogAction.of(operateur, "CREATION_MODELE",
            "Modèle créé : " + modele.getTitre()));
    }

    public void modifierModele(ModeleDocument modele, String operateur) {
        modeleDao.update(modele);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_MODELE",
            "Modèle modifié : " + modele.getTitre()));
    }

    public void changerStatutModele(Long id, String statut, String operateur) {
        ModeleDocument m = modeleDao.findById(id);
        if (m != null) {
            m.setStatut(statut);
            m.setActif("ACTIF".equals(statut));
            modeleDao.update(m);
            logActionDao.save(LogAction.of(operateur, "STATUT_MODELE",
                "Statut modèle → " + statut + " : " + m.getTitre()));
        }
    }

    public void supprimerModele(Long id, String operateur) {
        modeleDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_MODELE",
            "Modèle supprimé : id=" + id));
    }

    // ══════════════════════════════════════════════════
    // GÉNÉRATION AVEC MOTEUR DE TEMPLATE — CORRECTION 1
    // ══════════════════════════════════════════════════

    /**
     * Génère un document à partir d'un modèle avec substitution des variables.
     *
     * CORRECTION : avant, le contenu du modèle était copié brut sans aucune
     * substitution (TODO dans le code original). Maintenant :
     *
     *   1. Chargement du modèle et de l'employé
     *   2. Substitution des variables {{NOM}}, {{DATE_JOUR}}, {{POSTE}},
     *      {{INSTITUTION_NOM}}... via DocumentService.substituer()
     *   3. Sauvegarde du document généré avec le contenu substitué
     *
     * Variables disponibles dans les modèles :
     *   Employé  : {{NOM}}, {{PRENOM}}, {{NOM_COMPLET}}, {{MATRICULE}},
     *              {{POSTE}}, {{SERVICE}}, {{DIRECTION}}, {{GRADE}},
     *              {{SALAIRE_BASE}}, {{TYPE_CONTRAT}}, {{DATE_EMBAUCHE}}
     *   Institution: {{INSTITUTION_NOM}}, {{INSTITUTION_CODE}},
     *              {{INSTITUTION_VILLE}}, {{INSTITUTION_EMAIL}}
     *   Dates    : {{DATE_JOUR}}, {{DATE_JOUR_LONG}}, {{ANNEE}}, {{MOIS}}
     *
     * @return null si succès, message d'erreur sinon
     */
    public String genererDocument(Long modeleId, Long employeId,
                                   Long institutionId, String operateur) {
        // 1. Charger le modèle
        ModeleDocument modele = modeleDao.findById(modeleId);
        if (modele == null) return "Modèle introuvable.";

        // 2. Charger l'employé (optionnel — certains documents sont généraux)
        Employe employe = (employeId != null)
            ? employeDao.findById(employeId) : null;

        // 3. CORRECTION : substitution des variables via DocumentService
        String contenuBrut  = modele.getContenuTemplate();
        String contenuFinal = documentService.substituer(contenuBrut, employe);

        // 4. Créer le DocumentArchive avec le contenu substitué
        DocumentArchive doc = new DocumentArchive();
        doc.setTitre(modele.getTitre()
            + (employe != null ? " — " + employe.getNomComplet() : ""));
        doc.setTypeDocument(modele.getTypeDocument());
        doc.setInstitutionId(institutionId);
        doc.setDateDepot(LocalDate.now());
        doc.setStatut("ACTIF");
        doc.setDeposePar(operateur);
        doc.setEmploye(employe);

        // Contenu avec variables substituées, encodé UTF-8
        doc.setContenu(contenuFinal.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doc.setNomFichier(modele.getTitre().replaceAll("[^a-zA-Z0-9_-]", "_") + ".txt");

        documentDao.save(doc);
        logActionDao.save(LogAction.of(operateur, "GENERATION_DOCUMENT",
            "Document généré depuis modèle \"" + modele.getTitre() + "\""
            + (employe != null ? " pour " + employe.getNomComplet() : "")));
        return null;
    }

    // ══════════════════════════════════════════════════
    // EMPLOYÉS ACTIFS
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION 2 : utilisait findActifs() global.
     * Utilise maintenant findActifsByInstitution() pour respecter le multi-tenant.
     */
    @Transactional(readOnly = true)
    public List<Employe> getEmployesActifs(Long institutionId) {
        if (institutionId == null) return employeDao.findActifs();
        return employeDao.findActifsByInstitution(institutionId);
    }
}