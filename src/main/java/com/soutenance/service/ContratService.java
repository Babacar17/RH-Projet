package com.soutenance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.soutenance.entity.Employe;
import com.soutenance.entity.Institution;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service de génération de contrats de travail au format PDF.
 *
 * Utilise iText 5 (déjà au classpath du projet). Le contrat est généré
 * dynamiquement à partir des informations de l'employé et de son institution.
 * Aucun template externe : tout est codé dans la méthode pour éviter les
 * dépendances de fichiers.
 *
 * Trois types de contrats supportés : CDI, CDD, STAGE. Les clauses
 * sont adaptées selon le type. Pour des clauses spécifiques (mobilité,
 * non-concurrence, etc.) on peut surcharger via les paramètres.
 */
@Service
public class ContratService {

    private static final Logger LOG = LoggerFactory.getLogger(ContratService.class);

    private static final DateTimeFormatter FMT_DATE =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private static final Font FONT_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 18, BaseColor.DARK_GRAY);
    private static final Font FONT_SOUS_TITRE = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 13, new BaseColor(31, 78, 121));
    private static final Font FONT_NORMAL = FontFactory.getFont(
        FontFactory.HELVETICA, 11, BaseColor.BLACK);
    private static final Font FONT_BOLD = FontFactory.getFont(
        FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK);
    private static final Font FONT_PETIT_GRIS = FontFactory.getFont(
        FontFactory.HELVETICA, 9, BaseColor.GRAY);

    /**
     * Génère le PDF du contrat pour un employé donné.
     * Retourne le contenu binaire à stocker ou envoyer par email.
     */
    public byte[] genererContrat(Employe employe) {
        if (employe == null) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 60, 60, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            // En-tête institution
            Institution inst = employe.getInstitution();
            if (inst != null) {
                Paragraph entete = new Paragraph();
                entete.add(new Chunk("RÉPUBLIQUE DU SÉNÉGAL\n", FONT_BOLD));
                entete.add(new Chunk("Un Peuple — Un But — Une Foi\n\n", FONT_PETIT_GRIS));
                entete.add(new Chunk(inst.getNom().toUpperCase() + "\n", FONT_BOLD));
                if (inst.getAdresse() != null) {
                    entete.add(new Chunk(inst.getAdresse(), FONT_PETIT_GRIS));
                }
                entete.setAlignment(Element.ALIGN_CENTER);
                doc.add(entete);
            }

            doc.add(new Paragraph(" "));

            // Titre du contrat
            String typeContrat = employe.getTypeContrat() != null
                ? employe.getTypeContrat() : "CDI";
            Paragraph titre = new Paragraph("CONTRAT DE TRAVAIL — " + typeContrat, FONT_TITRE);
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setSpacingAfter(20);
            doc.add(titre);

            // Préambule
            doc.add(paragrapheJustifie(
                "Entre les soussignés :"));
            doc.add(new Paragraph(" "));

            // Employeur
            doc.add(new Paragraph("L'EMPLOYEUR", FONT_SOUS_TITRE));
            String nomEmployeur = inst != null ? inst.getNom() : "l'Institution";
            doc.add(paragrapheJustifie(nomEmployeur
                + (inst != null && inst.getAdresse() != null
                    ? ", dont le siège est situé à " + inst.getAdresse() : "")
                + (inst != null && inst.getTelephone() != null
                    ? ", téléphone " + inst.getTelephone() : "")
                + ", représenté par sa direction des Ressources Humaines,"));
            doc.add(paragrapheJustifie("Ci-après dénommé « l'Employeur »,"));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("D'UNE PART,", FONT_BOLD));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("ET", FONT_BOLD));
            doc.add(new Paragraph(" "));

            // Salarié
            doc.add(new Paragraph("LE SALARIÉ", FONT_SOUS_TITRE));
            String identitePart = "Monsieur / Madame " + safe(employe.getPrenom()).toUpperCase()
                + " " + safe(employe.getNom()).toUpperCase();
            if (employe.getDateNaissance() != null) {
                identitePart += ", né(e) le " + employe.getDateNaissance().format(FMT_DATE);
                if (employe.getLieuNaissance() != null)
                    identitePart += " à " + employe.getLieuNaissance();
            }
            if (employe.getCin() != null)
                identitePart += ", titulaire de la CIN n° " + employe.getCin();
            if (employe.getAdresse() != null)
                identitePart += ", domicilié à " + employe.getAdresse();
            doc.add(paragrapheJustifie(identitePart + ","));
            doc.add(paragrapheJustifie("Ci-après dénommé(e) « le Salarié »,"));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("D'AUTRE PART,", FONT_BOLD));

            doc.add(new Paragraph(" "));
            doc.add(paragrapheJustifie("IL A ÉTÉ CONVENU CE QUI SUIT :"));
            doc.add(new Paragraph(" "));

            // ── Articles ─────────────────────────────────────────────────────
            ajouterArticle(doc, "Article 1 — Engagement",
                "Le Salarié est engagé par l'Employeur en qualité de "
                + (employe.getPoste() != null ? safe(employe.getPoste().toString()) : "salarié")
                + ", à compter du "
                + (employe.getDateEmbauche() != null
                    ? employe.getDateEmbauche().format(FMT_DATE)
                    : LocalDate.now().format(FMT_DATE))
                + ", sous réserve des résultats favorables de la visite médicale "
                + "d'embauche et de la vérification des références.");

            String dureeClause;
            if ("CDD".equalsIgnoreCase(typeContrat)) {
                String fin = employe.getDateFinContrat() != null
                    ? employe.getDateFinContrat().format(FMT_DATE) : "[à préciser]";
                dureeClause = "Le présent contrat est conclu pour une durée déterminée "
                    + "et prendra fin le " + fin
                    + ", sauf renouvellement express convenu entre les parties.";
            } else if ("STAGE".equalsIgnoreCase(typeContrat)) {
                String fin = employe.getDateFinContrat() != null
                    ? employe.getDateFinContrat().format(FMT_DATE) : "[à préciser]";
                dureeClause = "Le présent stage est conclu pour la période débutant à la "
                    + "date d'embauche et se terminant le " + fin + ".";
            } else {
                dureeClause = "Le présent contrat est conclu pour une durée indéterminée. "
                    + "Une période d'essai de trois (3) mois, renouvelable une fois, "
                    + "est convenue entre les parties.";
            }
            ajouterArticle(doc, "Article 2 — Durée", dureeClause);

            ajouterArticle(doc, "Article 3 — Fonctions",
                "Le Salarié exercera les fonctions de "
                + (employe.getPoste() != null ? safe(employe.getPoste().toString()) : "[poste]")
                + " au sein de "
                + (employe.getNomDirection() != null
                    ? "la direction « " + employe.getNomDirection() + " »"
                    : "l'institution")
                + ". Il accomplira les tâches relevant de cette fonction ainsi que toute "
                + "mission compatible avec sa qualification professionnelle.");

            String salaire = employe.getSalaireBase() != null
                ? String.format("%,.0f", employe.getSalaireBase().doubleValue())
                    .replace(',', ' ')
                : "[à préciser]";
            ajouterArticle(doc, "Article 4 — Rémunération",
                "En contrepartie de ses fonctions, le Salarié percevra une rémunération "
                + "mensuelle brute de " + salaire + " FCFA, versée par virement bancaire "
                + "à la fin de chaque mois. À cette rémunération s'ajouteront, le cas "
                + "échéant, les primes et indemnités prévues par la grille salariale en vigueur.");

            ajouterArticle(doc, "Article 5 — Horaires et lieu de travail",
                "Le Salarié exercera ses fonctions à temps plein, selon les horaires en "
                + "vigueur au sein de l'institution. Le lieu de travail principal est fixé "
                + "au siège de "
                + (inst != null ? inst.getNom() : "l'Employeur")
                + ". Tout déplacement ou changement de lieu de travail fera l'objet d'un "
                + "avenant au présent contrat.");

            ajouterArticle(doc, "Article 6 — Congés",
                "Le Salarié bénéficiera d'un congé payé annuel conformément à la "
                + "législation en vigueur et à la convention collective applicable. "
                + "Les modalités de prise des congés seront fixées en accord avec son "
                + "responsable hiérarchique.");

            ajouterArticle(doc, "Article 7 — Confidentialité",
                "Le Salarié s'engage à observer la plus stricte discrétion sur l'ensemble "
                + "des informations dont il aura connaissance dans l'exercice de ses fonctions, "
                + "tant pendant la durée du contrat qu'après sa cessation.");

            ajouterArticle(doc, "Article 8 — Dispositions diverses",
                "Pour tout ce qui n'est pas prévu au présent contrat, les parties se "
                + "soumettent aux dispositions du Code du travail sénégalais et de la "
                + "convention collective applicable. Tout différend qui pourrait naître "
                + "à l'occasion de l'interprétation ou de l'exécution du présent contrat "
                + "sera porté devant les tribunaux compétents de Dakar.");

            doc.add(new Paragraph(" "));
            doc.add(paragrapheJustifie("Fait à "
                + (inst != null && inst.getVille() != null ? inst.getVille() : "Dakar")
                + ", le " + LocalDate.now().format(FMT_DATE)
                + ", en deux exemplaires originaux."));

            // ── Signatures ──────────────────────────────────────────────────
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            PdfPTable signatures = new PdfPTable(2);
            signatures.setWidthPercentage(100);
            signatures.setSpacingBefore(20);
            signatures.addCell(celluleSignature("Pour l'Employeur",
                "Direction des Ressources Humaines"));
            signatures.addCell(celluleSignature("Le Salarié",
                safe(employe.getPrenom()) + " " + safe(employe.getNom())));
            doc.add(signatures);

            // Pied
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            Paragraph pied = new Paragraph();
            pied.add(new Chunk("Matricule employé : " + safe(employe.getMatricule()) + " — ",
                FONT_PETIT_GRIS));
            pied.add(new Chunk("Document généré automatiquement par le SIRH le "
                + LocalDate.now().format(FMT_DATE), FONT_PETIT_GRIS));
            pied.setAlignment(Element.ALIGN_CENTER);
            doc.add(pied);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            LOG.error("Erreur inattendue", e);
            return new byte[0];
        }
        return baos.toByteArray();
    }

    private Paragraph paragrapheJustifie(String texte) {
        Paragraph p = new Paragraph(texte, FONT_NORMAL);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(6);
        return p;
    }

    private void ajouterArticle(Document doc, String titre, String contenu)
            throws DocumentException {
        doc.add(new Paragraph(titre, FONT_SOUS_TITRE));
        Paragraph p = new Paragraph(contenu, FONT_NORMAL);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(12);
        p.setIndentationLeft(10);
        doc.add(p);
    }

    private PdfPCell celluleSignature(String role, String identite) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        Paragraph p = new Paragraph();
        p.add(new Chunk(role + "\n", FONT_BOLD));
        p.add(new Chunk(identite + "\n\n\n", FONT_PETIT_GRIS));
        p.add(new Chunk("___________________________\n", FONT_NORMAL));
        p.add(new Chunk("Signature et date", FONT_PETIT_GRIS));
        cell.addElement(p);
        return cell;
    }

    private String safe(String s) { return s != null ? s : ""; }
}
