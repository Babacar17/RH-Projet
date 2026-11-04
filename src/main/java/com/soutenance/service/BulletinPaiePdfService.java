package com.soutenance.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.soutenance.entity.BulletinPaie;
import com.soutenance.entity.Employe;
import com.soutenance.entity.Institution;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Génération du bulletin de paie au format PDF (iText 5, déjà au classpath
 * du projet — voir ContratService / OffreEmploiPdfService pour le même
 * principe : pas de template externe, tout est codé dans la méthode).
 */
@Service
public class BulletinPaiePdfService {

    private static final Font FONT_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 16, BaseColor.DARK_GRAY);
    private static final Font FONT_SOUS_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 11, new BaseColor(31, 78, 121));
    private static final Font FONT_NORMAL = FontFactory.getFont(
        FontFactory.HELVETICA, 10, BaseColor.BLACK);
    private static final Font FONT_BOLD = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 10, BaseColor.BLACK);
    private static final Font FONT_PETIT_GRIS = FontFactory.getFont(
        FontFactory.HELVETICA, 8, BaseColor.GRAY);
    private static final Font FONT_NET = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 13, new BaseColor(6, 95, 70));

    private static final BaseColor BORDURE = new BaseColor(226, 232, 240);
    private static final BaseColor ENTETE_COL = new BaseColor(241, 245, 249);

    private final NumberFormat fmtMontant;

    public BulletinPaiePdfService() {
        fmtMontant = NumberFormat.getNumberInstance(Locale.FRANCE);
        fmtMontant.setMaximumFractionDigits(0);
    }

    public byte[] genererBulletinPdf(BulletinPaie b) {
        if (b == null) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Employe employe = b.getEmploye();
            Institution inst = employe != null ? employe.getInstitution() : null;

            // En-tête institution
            if (inst != null) {
                Paragraph entete = new Paragraph();
                entete.add(new Chunk("RÉPUBLIQUE DU SÉNÉGAL\n", FONT_BOLD));
                entete.add(new Chunk("Un Peuple — Un But — Une Foi\n\n", FONT_PETIT_GRIS));
                entete.add(new Chunk(inst.getNom() != null ? inst.getNom().toUpperCase() : "", FONT_BOLD));
                entete.setAlignment(Element.ALIGN_CENTER);
                doc.add(entete);
            }

            doc.add(new Paragraph(" "));
            Paragraph titre = new Paragraph("BULLETIN DE PAIE", FONT_TITRE);
            titre.setAlignment(Element.ALIGN_CENTER);
            doc.add(titre);

            String periode = b.getCyclePaie() != null ? b.getCyclePaie().getPeriodeLibelle() : "—";
            Paragraph sousPeriode = new Paragraph(periode, FONT_SOUS_TITRE);
            sousPeriode.setAlignment(Element.ALIGN_CENTER);
            sousPeriode.setSpacingAfter(16);
            doc.add(sousPeriode);

            // Infos employé
            PdfPTable infos = new PdfPTable(2);
            infos.setWidthPercentage(100);
            infos.setSpacingAfter(16);
            infos.setWidths(new float[]{1f, 1f});
            ajouterInfo(infos, "Employé", employe != null ? employe.getNomComplet() : "—");
            ajouterInfo(infos, "Matricule", employe != null ? employe.getMatricule() : "—");
            ajouterInfo(infos, "Poste", employe != null ? employe.getIntiutePoste() : "—");
            ajouterInfo(infos, "Statut du bulletin",
                "PAYE".equals(b.getStatut()) ? "Payé" : "Validé — en attente de versement");
            doc.add(infos);

            // Gains
            doc.add(new Paragraph("GAINS", FONT_SOUS_TITRE));
            PdfPTable gains = nouvelleTableMontants();
            ligneMontant(gains, "Salaire de base", b.getSalaireBase());
            ligneMontant(gains, "Primes et indemnités", b.getTotalPrimes());
            ligneMontant(gains, "Heures supplémentaires", b.getTotalHeuresSupp());
            ligneMontant(gains, "Avantages en nature", b.getTotalAvantages());
            ligneMontantGras(gains, "TOTAL BRUT", b.getSalaireBrut());
            doc.add(gains);

            doc.add(new Paragraph(" "));

            // Retenues
            doc.add(new Paragraph("RETENUES", FONT_SOUS_TITRE));
            PdfPTable retenues = nouvelleTableMontants();
            ligneMontant(retenues, "Cotisations salariales (IPRES, CSS…)", b.getTotalCotisationsSalariales());
            ligneMontant(retenues, "Impôt sur le revenu", b.getImpotRevenu());
            ligneMontant(retenues, "Autres retenues", b.getAutresRetenues());
            ligneMontantGras(retenues, "TOTAL RETENUES", b.getTotalRetenues());
            doc.add(retenues);

            doc.add(new Paragraph(" "));

            // Net à payer
            PdfPTable net = new PdfPTable(2);
            net.setWidthPercentage(100);
            net.setWidths(new float[]{1f, 1f});
            PdfPCell celLabel = new PdfPCell(new Phrase("NET À PAYER", FONT_NET));
            celLabel.setBorder(Rectangle.TOP);
            celLabel.setBorderWidth(1.5f);
            celLabel.setPadding(10);
            net.addCell(celLabel);
            PdfPCell celVal = new PdfPCell(new Phrase(formater(b.getSalaireNet()) + " FCFA", FONT_NET));
            celVal.setBorder(Rectangle.TOP);
            celVal.setBorderWidth(1.5f);
            celVal.setPadding(10);
            celVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            net.addCell(celVal);
            doc.add(net);

            doc.add(new Paragraph(" "));
            Paragraph pied = new Paragraph();
            pied.add(new Chunk("Cotisations patronales (information, non déduites) : "
                + formater(b.getTotalCotisationsPatronales()) + " FCFA\n", FONT_PETIT_GRIS));
            pied.add(new Chunk("Document généré automatiquement par le SIRH.", FONT_PETIT_GRIS));
            doc.add(pied);

        } catch (DocumentException e) {
            return new byte[0];
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    private PdfPTable nouvelleTableMontants() throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.2f, 1f});
        table.setSpacingAfter(4);
        return table;
    }

    private void ligneMontant(PdfPTable table, String label, BigDecimal montant) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, FONT_NORMAL));
        c1.setBorder(Rectangle.BOTTOM);
        c1.setBorderColor(BORDURE);
        c1.setPadding(6);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(formater(montant) + " FCFA", FONT_NORMAL));
        c2.setBorder(Rectangle.BOTTOM);
        c2.setBorderColor(BORDURE);
        c2.setPadding(6);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c2);
    }

    private void ligneMontantGras(PdfPTable table, String label, BigDecimal montant) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, FONT_BOLD));
        c1.setBackgroundColor(ENTETE_COL);
        c1.setPadding(6);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(formater(montant) + " FCFA", FONT_BOLD));
        c2.setBackgroundColor(ENTETE_COL);
        c2.setPadding(6);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c2);
    }

    private void ajouterInfo(PdfPTable table, String label, String valeur) {
        PdfPCell celLabel = new PdfPCell(new Phrase(label, FONT_PETIT_GRIS));
        celLabel.setBorder(Rectangle.BOTTOM);
        celLabel.setBorderColor(BORDURE);
        celLabel.setPadding(6);
        table.addCell(celLabel);

        PdfPCell celValeur = new PdfPCell(new Phrase(valeur != null ? valeur : "—", FONT_BOLD));
        celValeur.setBorder(Rectangle.BOTTOM);
        celValeur.setBorderColor(BORDURE);
        celValeur.setPadding(6);
        table.addCell(celValeur);
    }

    private String formater(BigDecimal montant) {
        return montant != null ? fmtMontant.format(montant) : "0";
    }
}
