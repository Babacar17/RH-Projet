package com.soutenance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.soutenance.entity.Institution;
import com.soutenance.entity.OffreEmploi;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service de génération de la fiche d'offre d'emploi au format PDF.
 *
 * Utilise iText 5 (déjà au classpath du projet, voir ContratService).
 * Aucun template externe : tout est codé dans la méthode.
 */
@Service
public class OffreEmploiPdfService {

    private static final Logger LOG = LoggerFactory.getLogger(OffreEmploiPdfService.class);

    private static final DateTimeFormatter FMT_DATE =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter FMT_DATETIME =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH);

    private static final Font FONT_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 18, new BaseColor(30, 58, 95));
    private static final Font FONT_SOUS_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 12, new BaseColor(31, 78, 121));
    private static final Font FONT_NORMAL = FontFactory.getFont(
        FontFactory.HELVETICA, 10.5f, BaseColor.BLACK);
    private static final Font FONT_BOLD = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 10.5f, BaseColor.BLACK);
    private static final Font FONT_PETIT_GRIS = FontFactory.getFont(
        FontFactory.HELVETICA, 9, BaseColor.GRAY);

    /** Génère le PDF de la fiche d'offre. Retourne le contenu binaire. */
    public byte[] genererOffre(OffreEmploi offre) {
        if (offre == null) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 55, 55, 50, 50);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Institution inst = offre.getInstitution();

            // En-tête institution
            if (inst != null) {
                Paragraph entete = new Paragraph();
                entete.add(new Chunk("RÉPUBLIQUE DU SÉNÉGAL\n", FONT_BOLD));
                entete.add(new Chunk("Un Peuple — Un But — Une Foi\n\n", FONT_PETIT_GRIS));
                entete.add(new Chunk(safe(inst.getNom()).toUpperCase() + "\n", FONT_BOLD));
                if (inst.getAdresse() != null) {
                    entete.add(new Chunk(inst.getAdresse(), FONT_PETIT_GRIS));
                }
                entete.setAlignment(Element.ALIGN_CENTER);
                doc.add(entete);
                doc.add(new Paragraph(" "));
            }

            // Titre + statut
            Paragraph titre = new Paragraph(safe(offre.getTitre()).toUpperCase(), FONT_TITRE);
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setSpacingAfter(4);
            doc.add(titre);

            Paragraph sousTitre = new Paragraph();
            sousTitre.setAlignment(Element.ALIGN_CENTER);
            sousTitre.setSpacingAfter(16);
            String contrat = offre.getTypeContrat() != null ? offre.getTypeContrat() : "—";
            String lieu = offre.getLocalisation() != null ? offre.getLocalisation() : "—";
            sousTitre.add(new Chunk(contrat + "  •  " + lieu, FONT_PETIT_GRIS));
            doc.add(sousTitre);

            // Présentation de l'institution
            if (inst != null && inst.getDescription() != null && !inst.getDescription().isBlank()) {
                doc.add(new Paragraph("Présentation de l'institution", FONT_SOUS_TITRE));
                doc.add(paragrapheJustifie(inst.getDescription()));
                doc.add(new Paragraph(" "));
            }

            // Bloc infos clés (tableau)
            PdfPTable infos = new PdfPTable(2);
            infos.setWidthPercentage(100);
            infos.setSpacingAfter(16);
            infos.setWidths(new float[]{1f, 1f});
            ajouterInfo(infos, "Département", offre.getDepartement());
            ajouterInfo(infos, "Nombre de postes",
                offre.getNombrePostes() != null ? offre.getNombrePostes().toString() : null);
            ajouterInfo(infos, "Niveau d'études", offre.getNiveauEtudes());
            ajouterInfo(infos, "Expérience requise", offre.getNiveauExperience());
            ajouterInfo(infos, "Rémunération", formatSalaire(offre.getSalaireMin(), offre.getSalaireMax()));
            ajouterInfo(infos, "Date limite de candidature",
                offre.getDateLimiteFermeture() != null
                    ? offre.getDateLimiteFermeture().format(FMT_DATETIME) : null);
            if (infos.getRows().size() > 0) doc.add(infos);

            ajouterSection(doc, "Description du poste", offre.getDescription());
            ajouterSection(doc, "Profil recherché", offre.getProfil());
            ajouterSection(doc, "Rattachement hiérarchique", offre.getRattachementHierarchique());
            ajouterSection(doc, "Avantages", offre.getAvantages());
            ajouterSection(doc, "Documents à fournir", offre.getDocumentsRequisTexte());
            ajouterSection(doc, "Modalités de candidature", offre.getModalitesCandidature());

            // Pied
            doc.add(new Paragraph(" "));
            Paragraph pied = new Paragraph();
            pied.add(new Chunk("Offre publiée le "
                + (offre.getDatePublication() != null
                    ? offre.getDatePublication().format(FMT_DATE)
                    : LocalDate.now().format(FMT_DATE))
                + " — Document généré automatiquement par le SIRH.", FONT_PETIT_GRIS));
            pied.setAlignment(Element.ALIGN_CENTER);
            pied.setSpacingBefore(20);
            doc.add(pied);

            doc.close();
        } catch (DocumentException e) {
            LOG.error("Erreur inattendue", e);
            return new byte[0];
        }
        return baos.toByteArray();
    }

    private void ajouterSection(Document doc, String titre, String contenu) throws DocumentException {
        if (contenu == null || contenu.isBlank()) return;
        doc.add(new Paragraph(titre, FONT_SOUS_TITRE));
        doc.add(paragrapheJustifie(contenu));
        doc.add(new Paragraph(" "));
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

    private String formatSalaire(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return null;
        if (min != null && max != null)
            return formatMontant(min) + " — " + formatMontant(max) + " FCFA";
        BigDecimal v = min != null ? min : max;
        return formatMontant(v) + " FCFA";
    }

    private String formatMontant(BigDecimal v) {
        return String.format("%,.0f", v.doubleValue()).replace(',', ' ');
    }

    private Paragraph paragrapheJustifie(String texte) {
        Paragraph p = new Paragraph(texte, FONT_NORMAL);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(6);
        return p;
    }

    private String safe(String s) { return s != null ? s : ""; }
}
