package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de gestion documentaire — périmètre employé / RH.
 *
 * CORRECTION : implémentation du moteur de template par substitution de variables.
 *
 * AVANT : genererPdf() et genererDocument() retournaient le template brut
 *   avec un commentaire "TODO: moteur de template".
 *
 * APRÈS : SubstitutionTemplate remplace les variables {{VARIABLE}} dans le
 *   contenu du modèle par les données réelles de l'employé et de l'institution.
 *
 * VARIABLES SUPPORTÉES :
 *
 *   Employé       : {{NOM}}, {{PRENOM}}, {{NOM_COMPLET}}, {{MATRICULE}},
 *                   {{EMAIL}}, {{TELEPHONE}}, {{DATE_EMBAUCHE}}, {{POSTE}},
 *                   {{SERVICE}}, {{DIRECTION}}, {{GRADE}}, {{SALAIRE_BASE}},
 *                   {{TYPE_CONTRAT}}, {{STATUT}}
 *
 *   Institution   : {{INSTITUTION_NOM}}, {{INSTITUTION_CODE}},
 *                   {{INSTITUTION_ADRESSE}}, {{INSTITUTION_VILLE}},
 *                   {{INSTITUTION_TELEPHONE}}, {{INSTITUTION_EMAIL}}
 *
 *   Dates système : {{DATE_JOUR}}, {{DATE_JOUR_LONG}}, {{ANNEE}}, {{MOIS}}
 *
 * SYNTAXE dans le modèle (contenuTemplate) :
 *   Bonjour {{NOM_COMPLET}}, votre contrat {{TYPE_CONTRAT}} est confirmé.
 *   Date : {{DATE_JOUR}}
 *
 * Si une variable est inconnue ou que la donnée est null, elle est remplacée
 * par une chaîne vide (comportement défensif — le document reste lisible).
 */
@Service
@Transactional
public class DocumentService {

    @Autowired private DocumentArchiveDao   documentDao;
    @Autowired private ModeleDocumentDao    modeleDao;
    @Autowired private EmployeDao           employeDao;
    @Autowired private LogActionDao         logActionDao;

    // Pattern de détection des variables {{NOM_VARIABLE}}
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([A-Z_0-9]+)\\}\\}");

    private static final DateTimeFormatter FMT_COURT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_LONG  =
        DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRENCH);

    // ══════════════════════════════════════════════════
    // MOTEUR DE TEMPLATE
    // ══════════════════════════════════════════════════

    /**
     * Substitue les variables {{NOM}} dans le template avec les données de l'employé.
     *
     * @param template   contenu brut du modèle
     * @param employe    employé concerné (peut être null)
     * @return           contenu avec les variables remplacées
     */
    public String substituer(String template, Employe employe) {
        if (template == null) return "";
        Map<String, String> vars = construireVariables(employe);
        return remplacer(template, vars);
    }

    /**
     * Construit la map de toutes les variables disponibles pour un employé.
     */
    private Map<String, String> construireVariables(Employe employe) {
        Map<String, String> vars = new HashMap<>();

        // ── Dates système ──────────────────────────────────────────────────
        LocalDate aujourd = LocalDate.now();
        vars.put("DATE_JOUR",      aujourd.format(FMT_COURT));
        vars.put("DATE_JOUR_LONG", aujourd.format(FMT_LONG));
        vars.put("ANNEE",          String.valueOf(aujourd.getYear()));
        vars.put("MOIS",           String.valueOf(aujourd.getMonthValue()));

        if (employe == null) return vars;

        // ── Données employé ────────────────────────────────────────────────
        vars.put("NOM",          safe(employe.getNom()));
        vars.put("PRENOM",       safe(employe.getPrenom()));
        vars.put("NOM_COMPLET",  employe.getNomComplet());
        vars.put("MATRICULE",    safe(employe.getMatricule()));
        vars.put("EMAIL",        safe(employe.getEmail()));
        vars.put("TELEPHONE",    safe(employe.getTelephone()));
        vars.put("GRADE",        safe(employe.getGradeEchelon()));
        vars.put("TYPE_CONTRAT", safe(employe.getTypeContrat()));
        vars.put("STATUT",       safe(employe.getStatut()));
        vars.put("SALAIRE_BASE", employe.getSalaireBase() != null
            ? employe.getSalaireBase().toPlainString() + " FCFA" : "");
        vars.put("DATE_EMBAUCHE", employe.getDateEmbauche() != null
            ? employe.getDateEmbauche().format(FMT_COURT) : "");

        // Poste via FK ou librePoste
        vars.put("POSTE", employe.getIntiutePoste());

        // Structure organisationnelle (accès protégé contre LazyInit)
        vars.put("SERVICE",    employe.getNomService());
        vars.put("DIRECTION",  employe.getNomDirection());
        vars.put("DIVISION",   employe.getNomDivision());
        vars.put("BUREAU",     employe.getNomBureau());

        // ── Données institution ────────────────────────────────────────────
        Institution inst = employe.getInstitution();
        if (inst != null) {
            vars.put("INSTITUTION_NOM",       safe(inst.getNom()));
            vars.put("INSTITUTION_CODE",      safe(inst.getCode()));
            vars.put("INSTITUTION_ADRESSE",   safe(inst.getAdresse()));
            vars.put("INSTITUTION_VILLE",     safe(inst.getVille()));
            vars.put("INSTITUTION_TELEPHONE", safe(inst.getTelephone()));
            vars.put("INSTITUTION_EMAIL",     safe(inst.getEmail()));
        }

        return vars;
    }

    /**
     * Remplace toutes les occurrences de {{CLE}} par la valeur correspondante.
     * Variable inconnue → chaîne vide (pas de {{CLE}} résiduel dans le document).
     */
    private String remplacer(String template, Map<String, String> vars) {
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String cle = m.group(1);
            String val = vars.getOrDefault(cle, ""); // vide si inconnue
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String safe(String s) { return s != null ? s : ""; }

    // ══════════════════════════════════════════════════
    // DOCUMENTS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<DocumentArchive> getTousDocuments(Long institutionId) {
        return documentDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public DocumentArchive getDocumentById(Long id) {
        return documentDao.findById(id);
    }

    public byte[] lireContenu(DocumentArchive doc, String operateur) {
        logActionDao.save(LogAction.of(operateur, "LECTURE_DOCUMENT",
            "Document consulté : " + doc.getTitre()));
        return doc.getContenu();
    }

    public void archiverDocument(DocumentArchive doc, String motif, String operateur) {
        doc.setStatut("ARCHIVE");
        doc.setMotifArchivage(motif);
        doc.setDateArchivage(LocalDate.now());
        documentDao.update(doc);
        logActionDao.save(LogAction.of(operateur, "ARCHIVAGE_DOCUMENT",
            "Document archivé : " + doc.getTitre()));
    }

    public void archiverDocumentGenere(Long documentId, String operateur) {
        DocumentArchive doc = documentDao.findById(documentId);
        if (doc != null) archiverDocument(doc, "Archivage automatique", operateur);
    }

    public void archiverSuppression(Long id, String operateur) {
        DocumentArchive doc = documentDao.findById(id);
        if (doc != null) {
            doc.setStatut("SUPPRIME");
            doc.setDateArchivage(LocalDate.now());
            documentDao.update(doc);
            logActionDao.save(LogAction.of(operateur, "SUPPRESSION_DOCUMENT",
                "Document supprimé : " + doc.getTitre()));
        }
    }

    public void demanderSignature(Long documentId, String emailSignataire, String operateur) {
        DocumentArchive doc = documentDao.findById(documentId);
        if (doc != null) {
            doc.setStatut("EN_SIGNATURE");
            doc.setEmailSignataire(emailSignataire);
            documentDao.update(doc);
            logActionDao.save(LogAction.of(operateur, "DEMANDE_SIGNATURE",
                "Signature demandée à " + emailSignataire + " pour : " + doc.getTitre()));
        }
    }

    public void marquerCommeSigne(Long documentId, String operateur) {
        DocumentArchive doc = documentDao.findById(documentId);
        if (doc != null) {
            doc.setStatut("SIGNE");
            doc.setDateSignature(LocalDate.now());
            documentDao.update(doc);
            logActionDao.save(LogAction.of(operateur, "SIGNATURE_DOCUMENT",
                "Document signé : " + doc.getTitre()));
        }
    }

    // ══════════════════════════════════════════════════
    // MODÈLES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ModeleDocument> getTousModeles(Long institutionId) {
        return modeleDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public ModeleDocument getModeleById(Long id) {
        return modeleDao.findById(id);
    }

    public void creerModele(ModeleDocument modele, Long institutionId, String operateur) {
        modele.setInstitutionId(institutionId);
        modele.setCreePar(operateur);
        modeleDao.save(modele);
        logActionDao.save(LogAction.of(operateur, "CREATION_MODELE",
            "Modèle créé : " + modele.getTitre()));
    }

    public void modifierModele(ModeleDocument modele, String operateur) {
        modeleDao.update(modele);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_MODELE",
            "Modèle modifié : " + modele.getTitre()));
    }

    public void supprimerModele(Long id, String operateur) {
        modeleDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_MODELE",
            "Modèle supprimé : id=" + id));
    }

    public void toggleActifModele(Long id, String operateur) {
        ModeleDocument m = modeleDao.findById(id);
        if (m != null) {
            m.setActif(!m.isActif());
            modeleDao.update(m);
            logActionDao.save(LogAction.of(operateur, "TOGGLE_MODELE",
                "Modèle " + (m.isActif() ? "activé" : "désactivé") + " : " + m.getTitre()));
        }
    }

    // ══════════════════════════════════════════════════
    // GÉNÉRATION PDF AVEC SUBSTITUTION (CORRECTION)
    // ══════════════════════════════════════════════════

    /**
     * Génère un document en appliquant le moteur de substitution.
     *
     * CORRECTION : avant, retournait le template brut.
     * Maintenant, substitue les variables {{NOM}}, {{DATE_JOUR}}, etc.
     * avant de convertir en bytes UTF-8.
     *
     * @param modele   modèle de document
     * @param employe  employé concerné
     * @param operateur username de l'opérateur
     * @return         contenu avec variables substituées, encodé UTF-8
     */
    public byte[] genererPdf(ModeleDocument modele, Employe employe, String operateur) {
        String contenuBrut = modele.getContenuTemplate();
        String contenuFinal = substituer(contenuBrut, employe);

        logActionDao.save(LogAction.of(operateur, "GENERATION_PDF",
            "PDF généré : " + modele.getTitre()
            + (employe != null ? " pour " + employe.getNomComplet() : "")));

        return contenuFinal.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Génère et archive un document depuis un modèle pour un employé.
     *
     * @return null si succès, message d'erreur sinon
     */
    public String genererEtArchiver(Long modeleId, Long employeId,
                                     Long institutionId, String operateur) {
        ModeleDocument modele = modeleDao.findById(modeleId);
        if (modele == null) return "Modèle introuvable.";

        Employe employe = (employeId != null) ? employeDao.findById(employeId) : null;

        // Substitution des variables
        String contenuFinal = substituer(modele.getContenuTemplate(), employe);

        DocumentArchive doc = new DocumentArchive();
        doc.setTitre(modele.getTitre()
            + (employe != null ? " — " + employe.getNomComplet() : ""));
        doc.setTypeDocument(modele.getTypeDocument());
        doc.setInstitutionId(institutionId);
        doc.setDateDepot(LocalDate.now());
        doc.setStatut("ACTIF");
        doc.setDeposePar(operateur);
        doc.setEmploye(employe);
        doc.setContenu(contenuFinal.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        documentDao.save(doc);

        logActionDao.save(LogAction.of(operateur, "GENERATION_DOCUMENT",
            "Document généré : " + doc.getTitre()));
        return null;
    }

    // ══════════════════════════════════════════════════
    // EMPLOYÉS POUR GED
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Employe> getEmployesPourGed(Long institutionId) {
        return employeDao.findActifsByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public Employe getEmployeById(Long id) {
        return employeDao.findById(id);
    }
}