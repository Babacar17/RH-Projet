package com.soutenance.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.soutenance.dao.CandidatureDao;
import com.soutenance.entity.CandidatFinal;
import com.soutenance.entity.Institution;
import com.soutenance.entity.NoteCandidatEvalElement;
import com.soutenance.entity.OffreEmploi;
import com.soutenance.entity.PVDeliberation;
import com.soutenance.entity.PhaseEvaluation;
import com.soutenance.entity.RapportPhaseEvaluation;
import com.soutenance.entity.Rubrique;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Génération PDF des rapports de phase et PV de délibération du module
 * recrutement — même approche qu'{@link OffreEmploiPdfService} (iText 5,
 * tout codé en dur, pas de template externe).
 */
@Service
public class RapportPvPdfService {

    private static final Logger LOG = LoggerFactory.getLogger(RapportPvPdfService.class);

    @Autowired private RecrutementService recrutementService;
    @Autowired private CandidatureDao candidatureDao;

    private static final DateTimeFormatter FMT_DATETIME =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter FMT_DATE =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private static final Font FONT_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 16, new BaseColor(30, 58, 95));
    private static final Font FONT_SOUS_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 11, new BaseColor(31, 78, 121));
    private static final Font FONT_NORMAL = FontFactory.getFont(
        FontFactory.HELVETICA, 10.5f, BaseColor.BLACK);
    private static final Font FONT_BOLD = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 10.5f, BaseColor.BLACK);
    private static final Font FONT_PETIT_GRIS = FontFactory.getFont(
        FontFactory.HELVETICA, 9, BaseColor.GRAY);

    public byte[] genererRapport(RapportPhaseEvaluation r) {
        if (r == null || r.getPhaseOffre() == null) return new byte[0];
        OffreEmploi offre = r.getPhaseOffre().getOffre();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 55, 55, 50, 50);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            enteteInstitution(doc, offre != null ? offre.getInstitution() : null);

            Paragraph titre = new Paragraph("RAPPORT DE RECRUTEMENT", FONT_TITRE);
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setSpacingAfter(4);
            doc.add(titre);

            Paragraph sousTitre = new Paragraph(safe(r.getLibelle()), FONT_SOUS_TITRE);
            sousTitre.setAlignment(Element.ALIGN_CENTER);
            sousTitre.setSpacingAfter(18);
            doc.add(sousTitre);

            PdfPTable infos = new PdfPTable(2);
            infos.setWidthPercentage(100);
            infos.setSpacingAfter(16);
            infos.setWidths(new float[]{1f, 1f});
            ajouterInfo(infos, "Offre concernée", offre != null ? offre.getTitre() : null);
            ajouterInfo(infos, "Type de contrat", offre != null ? offre.getTypeContrat() : null);
            ajouterInfo(infos, "Nombre de postes", offre != null && offre.getNombrePostes() != null ? String.valueOf(offre.getNombrePostes()) : null);
            ajouterInfo(infos, "Phase évaluée",
                r.getPhaseOffre().getPhase() != null ? r.getPhaseOffre().getPhase().getLibelle() : null);
            ajouterInfo(infos, "Candidats retenus", String.valueOf(r.getCandidatsRetenus()));
            ajouterInfo(infos, "Créé le",
                r.getDateCreation() != null ? r.getDateCreation().format(FMT_DATETIME) : null);
            doc.add(infos);

            sectionTitre(doc, "Contexte et besoin");
            if (r.getMotifRecrutement() != null && !r.getMotifRecrutement().isBlank()) {
                doc.add(paragrapheJustifie(r.getMotifRecrutement()));
            }
            if (offre != null) {
                if (offre.getDescription() != null && !offre.getDescription().isBlank()) {
                    doc.add(new Paragraph("Missions principales :", FONT_BOLD));
                    doc.add(paragrapheJustifie(offre.getDescription()));
                }
                if (offre.getProfil() != null && !offre.getProfil().isBlank()) {
                    doc.add(new Paragraph("Profil recherché :", FONT_BOLD));
                    doc.add(paragrapheJustifie(offre.getProfil()));
                }
            }

            if (offre != null) {
                sectionTitre(doc, "Publication de l'offre");
                PdfPTable pub = new PdfPTable(2);
                pub.setWidthPercentage(100);
                pub.setSpacingAfter(12);
                pub.setWidths(new float[]{1f, 1f});
                ajouterInfo(pub, "Plateforme utilisée", "Plateforme SIRH (en ligne)");
                ajouterInfo(pub, "Date d'ouverture",
                    offre.getDatePublication() != null ? offre.getDatePublication().format(FMT_DATE) : null);
                ajouterInfo(pub, "Date de clôture",
                    offre.getDateLimiteFermeture() != null ? offre.getDateLimiteFermeture().format(FMT_DATETIME) : null);
                ajouterInfo(pub, "Pièces exigées", offre.getDocumentsRequisTexte());
                if (pub.getRows().size() > 0) doc.add(pub);
                if (offre.getModalitesCandidature() != null && !offre.getModalitesCandidature().isBlank()) {
                    doc.add(new Paragraph("Critères d'éligibilité :", FONT_BOLD));
                    doc.add(paragrapheJustifie(offre.getModalitesCandidature()));
                }
            }

            sectionTitre(doc, "Réception des candidatures");
            PdfPTable recep = new PdfPTable(2);
            recep.setWidthPercentage(100);
            recep.setSpacingAfter(10);
            recep.setWidths(new float[]{1f, 1f});
            ajouterInfo(recep, "Candidatures reçues", String.valueOf(candidaturesTotal(offre)));
            ajouterInfo(recep, "Candidatures recevables",
                r.getCandidaturesRecevables() != null ? String.valueOf(r.getCandidaturesRecevables()) : null);
            ajouterInfo(recep, "Candidatures irrecevables",
                r.getCandidaturesIrrecevables() != null ? String.valueOf(r.getCandidaturesIrrecevables()) : null);
            if (recep.getRows().size() > 0) doc.add(recep);
            if (r.getMotifsExclusion() != null && !r.getMotifsExclusion().isBlank()) {
                doc.add(new Paragraph("Motifs d'exclusion :", FONT_BOLD));
                doc.add(paragrapheJustifie(r.getMotifsExclusion()));
            }

            PhaseEvaluation phase = r.getPhaseOffre().getPhase();
            if (phase != null && phase.getRubriques() != null && !phase.getRubriques().isEmpty()) {
                sectionTitre(doc, "Présélection — grille d'évaluation");
                PdfPTable grille = new PdfPTable(2);
                grille.setWidthPercentage(100);
                grille.setSpacingAfter(10);
                grille.setWidths(new float[]{3f, 1f});
                enteteCellule(grille, "Rubrique");
                enteteCellule(grille, "Coefficient");
                for (Rubrique rub : phase.getRubriques()) {
                    donneeCellule(grille, safe(rub.getLibelle()));
                    donneeCellule(grille, String.valueOf(rub.getCoefficient()));
                }
                doc.add(grille);
            }
            if (r.getCompositionComite() != null && !r.getCompositionComite().isBlank()) {
                doc.add(new Paragraph("Composition du comité de sélection :", FONT_BOLD));
                doc.add(paragrapheJustifie(r.getCompositionComite()));
            }

            sectionTitre(doc, "Épreuves et entretiens");
            PdfPTable epreuve = new PdfPTable(2);
            epreuve.setWidthPercentage(100);
            epreuve.setSpacingAfter(10);
            epreuve.setWidths(new float[]{1f, 1f});
            ajouterInfo(epreuve, "Phase", phase != null ? phase.getLibelle() : null);
            ajouterInfo(epreuve, "Lancée le",
                r.getPhaseOffre().getDateLancement() != null ? r.getPhaseOffre().getDateLancement().format(FMT_DATETIME) : null);
            ajouterInfo(epreuve, "Clôturée le",
                r.getPhaseOffre().getDateCloture() != null ? r.getPhaseOffre().getDateCloture().format(FMT_DATETIME) : null);
            if (epreuve.getRows().size() > 0) doc.add(epreuve);

            List<CandidatFinal> classement = offre != null
                ? recrutementService.getCandidatsFinaux(offre.getId()) : List.of();
            if (!classement.isEmpty()) {
                sectionTitre(doc, "Classement et proposition");
                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setSpacingAfter(10);
                table.setWidths(new float[]{0.6f, 2.5f, 1f, 1.2f});
                enteteCellule(table, "Rang");
                enteteCellule(table, "Candidat");
                enteteCellule(table, "Points");
                enteteCellule(table, "Décision");
                for (CandidatFinal cf : classement) {
                    donneeCellule(table, cf.getRang() != null ? String.valueOf(cf.getRang()) : "—");
                    String nomCandidat = cf.getCandidature() != null
                        ? safe(cf.getCandidature().getPrenomCandidat()) + " " + safe(cf.getCandidature().getNomCandidat())
                        : "—";
                    donneeCellule(table, nomCandidat);
                    donneeCellule(table, pointsTexte(cf));
                    donneeCellule(table, safe(cf.getStatut()));
                }
                doc.add(table);
            }

            boolean aConclusion = (r.getRecommandationDecision() != null && !r.getRecommandationDecision().isBlank())
                || (r.getDifficultesAmeliorations() != null && !r.getDifficultesAmeliorations().isBlank());
            if (aConclusion) {
                sectionTitre(doc, "Conclusion et recommandations");
                if (r.getRecommandationDecision() != null && !r.getRecommandationDecision().isBlank()) {
                    doc.add(new Paragraph("Décision proposée à la direction :", FONT_BOLD));
                    doc.add(paragrapheJustifie(r.getRecommandationDecision()));
                }
                if (r.getDifficultesAmeliorations() != null && !r.getDifficultesAmeliorations().isBlank()) {
                    doc.add(new Paragraph("Difficultés rencontrées / améliorations possibles :", FONT_BOLD));
                    doc.add(paragrapheJustifie(r.getDifficultesAmeliorations()));
                }
            }

            if (r.getNotesAutorite() != null && !r.getNotesAutorite().isBlank()) {
                sectionTitre(doc, "Notes");
                doc.add(paragrapheJustifie(r.getNotesAutorite()));
            }

            doc.add(new Paragraph("Annexes : offre publiée, grilles d'évaluation et export des candidatures disponibles sur la plateforme SIRH.", FONT_PETIT_GRIS));

            pied(doc);
            doc.close();
        } catch (DocumentException e) {
            LOG.error("Erreur inattendue", e);
            return new byte[0];
        }
        return baos.toByteArray();
    }

    private int candidaturesTotal(OffreEmploi offre) {
        if (offre == null) return 0;
        return candidatureDao.findByOffre(offre.getId()).size();
    }

    private void sectionTitre(Document doc, String texte) throws DocumentException {
        Paragraph p = new Paragraph(texte, FONT_SOUS_TITRE);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    /** Chaque ligne attendue sous la forme "Nom - Fonction - Présence" ; champs manquants laissés vides. */
    private List<String[]> parserMembres(String texte) {
        List<String[]> resultat = new ArrayList<>();
        if (texte == null || texte.isBlank()) return resultat;
        for (String ligne : texte.split("\\r?\\n")) {
            if (ligne.isBlank()) continue;
            String[] parts = ligne.split("-", 3);
            String nom = parts.length > 0 ? parts[0].trim() : ligne.trim();
            String fonction = parts.length > 1 ? parts[1].trim() : "";
            String presence = parts.length > 2 ? parts[2].trim() : "";
            resultat.add(new String[]{nom, fonction, presence});
        }
        return resultat;
    }

    public byte[] genererPv(PVDeliberation pv, List<CandidatFinal> classement) {
        if (pv == null) return new byte[0];
        OffreEmploi offre = pv.getOffre();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 55, 55, 50, 50);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            enteteInstitution(doc, offre != null ? offre.getInstitution() : null);

            Paragraph titre = new Paragraph("PROCÈS-VERBAL DE DÉLIBÉRATION", FONT_TITRE);
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setSpacingAfter(4);
            doc.add(titre);

            Paragraph sousTitre = new Paragraph(safe(pv.getLibelle()), FONT_SOUS_TITRE);
            sousTitre.setAlignment(Element.ALIGN_CENTER);
            sousTitre.setSpacingAfter(18);
            doc.add(sousTitre);

            PdfPTable infos = new PdfPTable(2);
            infos.setWidthPercentage(100);
            infos.setSpacingAfter(16);
            infos.setWidths(new float[]{1f, 1f});
            ajouterInfo(infos, "Offre concernée", offre != null ? offre.getTitre() : null);
            ajouterInfo(infos, "Lieu de la réunion", pv.getLieu());
            ajouterInfo(infos, "Date", pv.getDateCreation() != null ? pv.getDateCreation().format(FMT_DATETIME) : null);
            ajouterInfo(infos, "Heure de début", pv.getHeureDebut());
            ajouterInfo(infos, "Membres présents", String.valueOf(pv.getNombreMembres()));
            doc.add(infos);

            List<String[]> membres = parserMembres(pv.getMembresJury());
            sectionTitre(doc, "Participants");
            if (!membres.isEmpty()) {
                PdfPTable tParticipants = new PdfPTable(3);
                tParticipants.setWidthPercentage(100);
                tParticipants.setSpacingAfter(8);
                tParticipants.setWidths(new float[]{2f, 2f, 1.3f});
                enteteCellule(tParticipants, "Nom");
                enteteCellule(tParticipants, "Fonction");
                enteteCellule(tParticipants, "Présence");
                for (String[] m : membres) {
                    donneeCellule(tParticipants, m[0]);
                    donneeCellule(tParticipants, m[1]);
                    donneeCellule(tParticipants, m[2]);
                }
                doc.add(tParticipants);
            } else {
                doc.add(new Paragraph("Composition du jury non détaillée — voir « Membres présents » ci-dessus.", FONT_PETIT_GRIS));
            }
            doc.add(paragrapheJustifie(pv.getDeclarationConflitInteret() != null && !pv.getDeclarationConflitInteret().isBlank()
                ? pv.getDeclarationConflitInteret() : "Aucun membre n'a déclaré de conflit d'intérêts."));

            sectionTitre(doc, "Rappel de l'objet");
            doc.add(paragrapheJustifie(
                "Poste à pourvoir : " + (offre != null ? safe(offre.getTitre()) : "—")
                + ". Procédure suivie : recrutement via la plateforme SIRH selon le référentiel d'évaluation défini pour cette offre."));

            if (classement != null && !classement.isEmpty()) {
                sectionTitre(doc, "Déroulement de la séance et délibération");
                doc.add(new Paragraph(classement.size() + " candidat(s) examiné(s) en phase finale. Classement final :", FONT_NORMAL));
                doc.add(new Paragraph(" "));

                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setSpacingAfter(12);
                table.setWidths(new float[]{0.6f, 2.5f, 1f, 1.2f});
                enteteCellule(table, "Rang");
                enteteCellule(table, "Candidat");
                enteteCellule(table, "Points");
                enteteCellule(table, "Décision");
                for (CandidatFinal cf : classement) {
                    donneeCellule(table, cf.getRang() != null ? String.valueOf(cf.getRang()) : "—");
                    String nomCandidat = cf.getCandidature() != null
                        ? safe(cf.getCandidature().getPrenomCandidat()) + " " + safe(cf.getCandidature().getNomCandidat())
                        : "—";
                    donneeCellule(table, nomCandidat);
                    donneeCellule(table, pointsTexte(cf));
                    donneeCellule(table, safe(cf.getStatut()));
                }
                doc.add(table);

                doc.add(new Paragraph("Détail des notes par membre du jury", FONT_SOUS_TITRE));
                doc.add(new Paragraph(" "));

                PdfPTable notesTable = new PdfPTable(5);
                notesTable.setWidthPercentage(100);
                notesTable.setSpacingAfter(12);
                notesTable.setWidths(new float[]{2f, 1.3f, 1.6f, 1.6f, 0.8f});
                enteteCellule(notesTable, "Candidat");
                enteteCellule(notesTable, "Phase");
                enteteCellule(notesTable, "Membre du jury");
                enteteCellule(notesTable, "Élément noté");
                enteteCellule(notesTable, "Points");
                for (CandidatFinal cf : classement) {
                    if (cf.getCandidature() == null) continue;
                    String nomCandidat = safe(cf.getCandidature().getPrenomCandidat()) + " " + safe(cf.getCandidature().getNomCandidat());
                    List<NoteCandidatEvalElement> notes = recrutementService.getNotesParCandidature(cf.getCandidature().getId());
                    if (notes.isEmpty()) continue;
                    for (NoteCandidatEvalElement n : notes) {
                        donneeCellule(notesTable, nomCandidat);
                        donneeCellule(notesTable, n.getPhaseOffre() != null && n.getPhaseOffre().getPhase() != null
                            ? n.getPhaseOffre().getPhase().getLibelle() : "—");
                        donneeCellule(notesTable, n.getEvaluateur() != null ? n.getEvaluateur().getNomComplet() : "—");
                        donneeCellule(notesTable, n.getElement() != null ? n.getElement().getLibelle() : "—");
                        donneeCellule(notesTable, String.valueOf(n.getPoints()));
                    }
                }
                doc.add(notesTable);
            } else {
                doc.add(new Paragraph("Aucun candidat finaliste pour cette offre.", FONT_PETIT_GRIS));
            }

            boolean aDecision = (pv.getModeDecision() != null && !pv.getModeDecision().isBlank())
                || (pv.getResultatVote() != null && !pv.getResultatVote().isBlank())
                || (pv.getReserves() != null && !pv.getReserves().isBlank());
            if (aDecision) {
                sectionTitre(doc, "Mode de décision");
                PdfPTable dec = new PdfPTable(2);
                dec.setWidthPercentage(100);
                dec.setSpacingAfter(8);
                dec.setWidths(new float[]{1f, 1f});
                ajouterInfo(dec, "Mode de décision", "VOTE".equals(pv.getModeDecision()) ? "Vote"
                    : "CONSENSUS".equals(pv.getModeDecision()) ? "Consensus" : pv.getModeDecision());
                ajouterInfo(dec, "Résultat du vote", pv.getResultatVote());
                if (dec.getRows().size() > 0) doc.add(dec);
                if (pv.getReserves() != null && !pv.getReserves().isBlank()) {
                    doc.add(new Paragraph("Réserves ou observations :", FONT_BOLD));
                    doc.add(paragrapheJustifie(pv.getReserves()));
                }
            }

            if (pv.getSuitesADonner() != null && !pv.getSuitesADonner().isBlank()) {
                sectionTitre(doc, "Suites à donner");
                doc.add(paragrapheJustifie(pv.getSuitesADonner()));
            }

            sectionTitre(doc, "Clôture et signatures");
            if (pv.getHeureCloture() != null && !pv.getHeureCloture().isBlank()) {
                doc.add(new Paragraph("Heure de clôture : " + pv.getHeureCloture(), FONT_NORMAL));
                doc.add(new Paragraph(" "));
            }
            if (!membres.isEmpty()) {
                PdfPTable tSignatures = new PdfPTable(3);
                tSignatures.setWidthPercentage(100);
                tSignatures.setSpacingBefore(6);
                tSignatures.setWidths(new float[]{2f, 2f, 2f});
                enteteCellule(tSignatures, "Nom");
                enteteCellule(tSignatures, "Fonction");
                enteteCellule(tSignatures, "Signature");
                for (String[] m : membres) {
                    donneeCellule(tSignatures, m[0]);
                    donneeCellule(tSignatures, m[1]);
                    PdfPCell vide = new PdfPCell(new Phrase(" "));
                    vide.setMinimumHeight(28);
                    vide.setBorderColor(new BaseColor(226, 232, 240));
                    tSignatures.addCell(vide);
                }
                doc.add(tSignatures);
            }

            pied(doc);
            doc.close();
        } catch (DocumentException e) {
            LOG.error("Erreur inattendue", e);
            return new byte[0];
        }
        return baos.toByteArray();
    }

    private void enteteInstitution(Document doc, Institution inst) throws DocumentException {
        if (inst == null) return;
        Paragraph entete = new Paragraph();
        entete.add(new Chunk("RÉPUBLIQUE DU SÉNÉGAL\n", FONT_BOLD));
        entete.add(new Chunk("Un Peuple — Un But — Une Foi\n\n", FONT_PETIT_GRIS));
        entete.add(new Chunk(safe(inst.getNom()).toUpperCase() + "\n", FONT_BOLD));
        entete.setAlignment(Element.ALIGN_CENTER);
        doc.add(entete);
        doc.add(new Paragraph(" "));
    }

    private void pied(Document doc) throws DocumentException {
        doc.add(new Paragraph(" "));
        Paragraph pied = new Paragraph();
        pied.add(new Chunk("Document généré automatiquement par le SIRH.", FONT_PETIT_GRIS));
        pied.setAlignment(Element.ALIGN_CENTER);
        pied.setSpacingBefore(20);
        doc.add(pied);
    }

    private void enteteCellule(PdfPTable table, String texte) {
        PdfPCell cell = new PdfPCell(new Phrase(texte, FONT_BOLD));
        cell.setBackgroundColor(new BaseColor(241, 245, 249));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void donneeCellule(PdfPTable table, String texte) {
        PdfPCell cell = new PdfPCell(new Phrase(texte, FONT_NORMAL));
        cell.setBorderColor(new BaseColor(226, 232, 240));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void ajouterInfo(PdfPTable table, String label, String valeur) {
        if (valeur == null || valeur.isBlank()) return;
        PdfPCell celLabel = new PdfPCell(new Phrase(label, FONT_PETIT_GRIS));
        celLabel.setBorder(Rectangle.BOTTOM);
        celLabel.setBorderColor(new BaseColor(226, 232, 240));
        celLabel.setPadding(6);
        table.addCell(celLabel);

        PdfPCell celValeur = new PdfPCell(new Phrase(valeur, FONT_BOLD));
        celValeur.setBorder(Rectangle.BOTTOM);
        celValeur.setBorderColor(new BaseColor(226, 232, 240));
        celValeur.setPadding(6);
        table.addCell(celValeur);
    }

    private Paragraph paragrapheJustifie(String texte) {
        Paragraph p = new Paragraph(texte, FONT_NORMAL);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(6);
        return p;
    }

    private String pointsTexte(CandidatFinal cf) {
        if (cf.getCandidature() == null) return "—";
        Integer total = recrutementService.getPointsCandidature(cf.getCandidature().getId());
        Integer max = recrutementService.getPointsMaxCandidature(cf.getCandidature().getId());
        if (total == null || max == null) return "—";
        return total + "/" + max;
    }

    private String safe(String s) { return s != null ? s : ""; }
}
