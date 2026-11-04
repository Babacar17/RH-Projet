package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service RH.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. BUG LogAction — ordre des paramètres inversé (BUG #1)
 *    Avant : new LogAction("CODE_ACTION", "detail", operateur)
 *    Après : LogAction.of(operateur, "CODE_ACTION", "detail")
 *
 * 2. BUG Transactions (BUG #2)
 *    Les DAOs doivent utiliser getCurrentSession() et non openSession().
 *    Correction dans la couche DAO (voir OffreEmploiDao.java).
 *
 * 3. FIX TYPE — approuverMouvement()
 *    Poste est une @Entity, pas un enum → Poste.valueOf() n'existe pas.
 *    MouvementCarriere.getNouveauPoste() retourne un String (le code du poste).
 *    → Lookup via PosteDao.findByCode(). Si aucun Poste trouvé, le champ
 *      n'est pas mis à jour (comportement défensif).
 */
@Service
@Transactional
public class RHService {

    @Autowired private EmployeDao           employeDao;
    @Autowired private OffreEmploiDao       offreEmploiDao;
    @Autowired private CandidatureDao       candidatureDao;
    @Autowired private DemandeCongeDao      demandeCongeDao;
    @Autowired private FormationDao         formationDao;
    @Autowired private EvaluationDao        evaluationDao;
    @Autowired private MouvementCarriereDao mouvementCarriereDao;
    @Autowired private LogActionDao         logActionDao;
    @Autowired private PosteDao             posteDao;   // FIX #3 : ajout
    @Autowired(required = false) private EmailService emailService;
    // Injections pour la création automatique d'un compte User à l'embauche
    @Autowired(required = false) private com.soutenance.dao.UserDao userDao;
    @Autowired(required = false) private com.soutenance.dao.RoleDao roleDao;
    @Autowired(required = false) private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;
    @Autowired(required = false) private ContratService contratService;
    @Autowired(required = false) private OnboardingService onboardingService;
    @Autowired(required = false) private OffreEmploiPdfService offreEmploiPdfService;
    @Autowired(required = false) private NotificationDao notificationDao;

    /** Crée une notification in-app pour le candidat lié à cette candidature (si un compte existe). */
    private void notifierCandidat(Candidature c, String titre, String message, String priorite) {
        if (notificationDao == null || c.getCandidatUser() == null) return;
        try {
            Notification n = new Notification();
            n.setTitre(titre);
            n.setMessage(message);
            n.setType("RECRUTEMENT_CANDIDATURE");
            n.setPriorite(priorite);
            n.setDestinataire(c.getCandidatUser());
            n.setEntiteLiee("CANDIDATURE");
            n.setEntiteLieeId(c.getId());
            n.setLienAction("/Candidat/mes-candidatures.xhtml");
            notificationDao.save(n);
        } catch (Exception ignored) { /* best effort, ne doit pas bloquer le changement de statut */ }
    }

    // ══════════════════════════════════════════════════
    // EMPLOYÉS
    // ══════════════════════════════════════════════════

    public List<Employe> getTousEmployes() {
        return employeDao.findAll();
    }

    public List<Employe> getEmployesByInstitution(Long institutionId) {
        if (institutionId == null) return employeDao.findAll();
        return employeDao.findByInstitution(institutionId);
    }

    public List<Employe> rechercherEmployes(String terme) {
        return employeDao.search(terme);
    }

    public Employe getEmployeById(Long id) {
        return employeDao.findById(id);
    }

    public void creerEmploye(Employe employe, String operateur) {
        if (employe.getMatricule() == null || employe.getMatricule().isBlank()) {
            employe.setMatricule(genererMatricule());
        }
        employeDao.save(employe);
        logActionDao.save(LogAction.of(operateur,
            "CREATION_EMPLOYE",
            "Fiche créée : " + employe.getNomComplet()));
    }

    public void modifierEmploye(Employe employe, String operateur) {
        employeDao.update(employe);
        logActionDao.save(LogAction.of(operateur,
            "MODIFICATION_EMPLOYE",
            "Fiche modifiée : " + employe.getNomComplet()));
    }

    public void supprimerEmploye(Long id, String operateur) {
        Employe e = employeDao.findById(id);
        if (e != null) {
            logActionDao.save(LogAction.of(operateur,
                "SUPPRESSION_EMPLOYE",
                "Fiche supprimée : " + e.getNomComplet()));
            employeDao.delete(id);
        }
    }

    public long countEmployes()       { return employeDao.countTotal(); }
    public long countEmployesActifs() { return employeDao.countByStatut("ACTIF"); }

    private String genererMatricule() {
        long count = employeDao.countTotal() + 1;
        return "EMP" + String.format("%04d", count);
    }

    // ══════════════════════════════════════════════════
    // OFFRES D'EMPLOI
    // ══════════════════════════════════════════════════

    public List<OffreEmploi> getToutesOffres() {
        return offreEmploiDao.findAll();
    }

    public OffreEmploi getOffreById(Long id) {
        return offreEmploiDao.findById(id);
    }

    /** Génère le PDF de la fiche d'offre. Retourne le contenu binaire. */
    public byte[] genererOffrePdf(Long offreId) {
        if (offreEmploiPdfService == null || offreId == null) return new byte[0];
        OffreEmploi offre = offreEmploiDao.findById(offreId);
        if (offre == null) return new byte[0];
        return offreEmploiPdfService.genererOffre(offre);
    }

    public void publierOffre(OffreEmploi offre, String operateur) {
        offre.setStatut("PUBLIEE");
        if (offre.getId() == null) {
            offre.setCreePar(operateur);
            offreEmploiDao.save(offre);
        } else {
            offreEmploiDao.update(offre);
        }
        logActionDao.save(LogAction.of(operateur,
            "PUBLICATION_OFFRE",
            "Offre publiée : " + offre.getTitre()));
    }

    public void sauvegarderOffre(OffreEmploi offre, String operateur) {
        if (offre.getId() == null) {
            offre.setCreePar(operateur);
            offreEmploiDao.save(offre);
        } else {
            offreEmploiDao.update(offre);
        }
        logActionDao.save(LogAction.of(operateur,
            "SAUVEGARDE_OFFRE",
            "Offre sauvegardée : " + offre.getTitre()));
    }

    public void fermerOffre(Long id, String operateur) {
        OffreEmploi offre = offreEmploiDao.findById(id);
        if (offre != null) {
            offre.setStatut("FERMEE");
            offreEmploiDao.update(offre);
            logActionDao.save(LogAction.of(operateur,
                "FERMETURE_OFFRE",
                "Offre fermée : " + offre.getTitre()));
        }
    }

    public long countOffresActives()                    { return offreEmploiDao.countByStatut("PUBLIEE"); }
    public long countCandidaturesParOffre(Long offreId) { return offreEmploiDao.countCandidaturesParOffre(offreId); }

    // ══════════════════════════════════════════════════
    // CANDIDATURES
    // ══════════════════════════════════════════════════

    public List<Candidature> getToutesCandidatures() {
        return candidatureDao.findAll();
    }

    public List<Candidature> getCandidaturesByOffre(Long offreId) {
        return candidatureDao.findByOffre(offreId);
    }

    public void changerStatutCandidature(Long candidatureId, String nouveauStatut, String operateur) {
        Candidature c = candidatureDao.findById(candidatureId);
        if (c == null) return;
        String ancien = c.getStatut();
        c.setStatut(nouveauStatut);
        c.setDateModification(LocalDateTime.now());
        candidatureDao.update(c);
        logActionDao.save(LogAction.of(operateur,
            "STATUT_CANDIDATURE",
            c.getNomPrenomCandidat() + " : " + ancien + " -> " + nouveauStatut));

        // Notification email — sauf si on passe à RECRUTEE (géré séparément)
        if (emailService != null && c.getEmailCandidat() != null
                && !"RECRUTEE".equalsIgnoreCase(nouveauStatut)) {
            String sujet = "Votre candidature — " + (c.getOffre() != null ? c.getOffre().getTitre() : "");
            String corps = construireEmailStatut(c, nouveauStatut);
            emailService.envoyerNotification(c.getEmailCandidat(), sujet, corps, operateur);
        }

        if (!"RECRUTEE".equalsIgnoreCase(nouveauStatut)) {
            String titreOffre = c.getOffre() != null ? c.getOffre().getTitre() : "votre candidature";
            notifierCandidat(c, "Mise à jour de votre candidature",
                "Votre candidature pour « " + titreOffre + " » est passée au statut : "
                    + labelStatutCandidature(nouveauStatut) + ".",
                "HAUTE");
        }
    }

    /** Crée une notification in-app pour le compte User lié à cet employé (si un compte existe). */
    private void notifierEmploye(Employe e, String titre, String message, String type, String priorite) {
        if (notificationDao == null || e == null || e.getUser() == null) return;
        try {
            Notification n = new Notification();
            n.setTitre(titre);
            n.setMessage(message);
            n.setType(type);
            n.setPriorite(priorite);
            n.setDestinataire(e.getUser());
            notificationDao.save(n);
        } catch (Exception ignored) { /* best effort, ne doit pas bloquer le traitement */ }
    }

    private String labelStatutCandidature(String statut) {
        if (statut == null) return "";
        return switch (statut) {
            case "RECUE"     -> "Reçue";
            case "ENTRETIEN" -> "Entretien";
            case "RETENUE"   -> "Retenue";
            case "REFUSEE"   -> "Refusée";
            case "RECRUTEE"  -> "Recrutée";
            default          -> statut;
        };
    }

    public void refuserCandidature(Long candidatureId, String motifRefus, String operateur) {
        Candidature c = candidatureDao.findById(candidatureId);
        if (c == null) return;
        c.setStatut("REFUSEE");
        c.setCommentaireRH(motifRefus);
        c.setDateModification(LocalDateTime.now());
        candidatureDao.update(c);
        logActionDao.save(LogAction.of(operateur,
            "REFUS_CANDIDATURE",
            "Refus de " + c.getNomPrenomCandidat() + " : " + motifRefus));

        if (emailService != null && c.getEmailCandidat() != null) {
            String sujet = "Suite donnée à votre candidature";
            String corps = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">"
                + "<p>Bonjour " + safe(c.getPrenomCandidat()) + " " + safe(c.getNomCandidat()) + ",</p>"
                + "<p>Nous vous remercions de l'intérêt que vous avez porté au poste de "
                + "<strong>" + (c.getOffre() != null ? safe(c.getOffre().getTitre()) : "") + "</strong>.</p>"
                + "<p>Après étude attentive de votre dossier, nous sommes au regret de "
                + "ne pas pouvoir donner une suite favorable à votre candidature.</p>"
                + "<p style=\"background:#fef2f2;border-left:3px solid #dc2626;padding:10px 14px;"
                + "color:#7f1d1d;font-style:italic;\">" + safe(motifRefus) + "</p>"
                + "<p>Nous vous souhaitons pleine réussite dans vos démarches futures.</p>"
                + "<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p>"
                + "</div>";
            emailService.envoyerNotification(c.getEmailCandidat(), sujet, corps, operateur);
        }

        notifierCandidat(c, "Réponse à votre candidature",
            "Votre candidature pour « " + (c.getOffre() != null ? c.getOffre().getTitre() : "l'offre")
                + " » n'a pas été retenue. Motif : " + safe(motifRefus),
            "NORMALE");
    }

    public void planifierEntretien(Candidature candidature, String operateur) {
        candidature.setStatut("ENTRETIEN");
        candidatureDao.update(candidature);
        logActionDao.save(LogAction.of(operateur,
            "PLANIFICATION_ENTRETIEN",
            "Entretien planifié pour " + candidature.getNomPrenomCandidat()));

        if (emailService != null && candidature.getEmailCandidat() != null) {
            java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",
                    new java.util.Locale("fr", "FR"));
            String dateFmt = candidature.getDateEntretien() != null
                ? candidature.getDateEntretien().format(fmt) : "à confirmer";
            String sujet = "Convocation à un entretien — " + safe(candidature.getOffre() != null
                ? candidature.getOffre().getTitre() : "");
            String corps = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">"
                + "<p>Bonjour " + safe(candidature.getPrenomCandidat()) + ",</p>"
                + "<p>Nous avons le plaisir de vous convier à un entretien dans le cadre de votre "
                + "candidature pour le poste de <strong>"
                + safe(candidature.getOffre() != null ? candidature.getOffre().getTitre() : "")
                + "</strong>.</p>"
                + "<table style=\"border-collapse:collapse;margin:14px 0;\">"
                + "<tr><td style=\"padding:6px 14px;background:#f8fafc;font-weight:600;\">Date</td>"
                + "<td style=\"padding:6px 14px;background:#f8fafc;\">" + dateFmt + "</td></tr>"
                + "<tr><td style=\"padding:6px 14px;font-weight:600;\">Modalité</td>"
                + "<td style=\"padding:6px 14px;\">"
                + safe(candidature.getTypeEntretien() != null ? candidature.getTypeEntretien() : "—")
                + "</td></tr></table>"
                + "<p>Merci de bien vouloir nous confirmer votre présence par retour de mail.</p>"
                + "<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p>"
                + "</div>";
            emailService.envoyerNotification(candidature.getEmailCandidat(), sujet, corps, operateur);
        }

        String dateEntretienTxt = candidature.getDateEntretien() != null
            ? candidature.getDateEntretien().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            : "à confirmer";
        notifierCandidat(candidature, "Entretien programmé",
            "Un entretien a été programmé pour votre candidature à « "
                + (candidature.getOffre() != null ? candidature.getOffre().getTitre() : "l'offre")
                + " » le " + dateEntretienTxt + ".",
            "HAUTE");
    }

    /**
     * Convertit une candidature retenue en employé.
     * À appeler quand l'admin RH passe le statut à RECRUTEE.
     * Crée l'entité Employe avec les infos du candidat et l'institution
     * de l'offre. Retourne l'Employe créé, ou null si échec.
     */
    @Transactional
    public Employe recruterCandidat(Long candidatureId, String operateur) {
        Candidature c = candidatureDao.findById(candidatureId);
        if (c == null) return null;
        if ("RECRUTEE".equalsIgnoreCase(c.getStatut())) {
            // Déjà recruté, on évite le double
            return null;
        }

        // Recrutement spontané : pas d'offre liée → on retombe sur l'institution
        // de l'utilisateur connecté (recruteur RH) via SessionBean, sinon null
        com.soutenance.entity.Institution institutionRecrut = null;
        String typeContratRecrut = "CDI";
        if (c.getOffre() != null) {
            institutionRecrut = c.getOffre().getInstitution();
            if (c.getOffre().getTypeContrat() != null)
                typeContratRecrut = c.getOffre().getTypeContrat();
        }
        // Fallback : si le candidat User est rattaché à une institution
        if (institutionRecrut == null && c.getCandidatUser() != null) {
            institutionRecrut = c.getCandidatUser().getInstitution();
        }

        Employe emp = new Employe();
        emp.setNom(c.getNomCandidat());
        emp.setPrenom(c.getPrenomCandidat());
        emp.setEmail(c.getEmailCandidat());
        emp.setTelephone(c.getTelephoneCandidat());
        emp.setInstitution(institutionRecrut);
        emp.setDateEmbauche(java.time.LocalDate.now());
        emp.setTypeContrat(typeContratRecrut);
        emp.setStatut("ACTIF");
        emp.setMatricule(genererMatricule(
            institutionRecrut != null ? institutionRecrut.getId() : null));
        if (c.getOffre() != null) {
            OffreEmploi offreRecrut = c.getOffre();
            if (offreRecrut.getPoste() != null) emp.setPoste(offreRecrut.getPoste());
            // Le bureau est plus precis que le service : quand il est renseigne, il
            // porte lui-meme la chaine bureau -> service -> division -> direction.
            if (offreRecrut.getBureau() != null) {
                emp.setBureauOrg(offreRecrut.getBureau());
                if (offreRecrut.getBureau().getService() != null) {
                    emp.setServiceOrg(offreRecrut.getBureau().getService());
                } else if (offreRecrut.getService() != null) {
                    emp.setServiceOrg(offreRecrut.getService());
                }
            } else if (offreRecrut.getService() != null) {
                emp.setServiceOrg(offreRecrut.getService());
            }
        }
        employeDao.save(emp);

        c.setStatut("RECRUTEE");
        c.setDateModification(LocalDateTime.now());
        candidatureDao.update(c);

        logActionDao.save(LogAction.of(operateur, "RECRUTEMENT",
            c.getNomPrenomCandidat() + " recruté — matricule " + emp.getMatricule()));

        notifierCandidat(c, "Félicitations, vous êtes recruté(e) !",
            "Votre candidature pour « " + (c.getOffre() != null ? c.getOffre().getTitre() : "l'offre")
                + " » a été retenue. Bienvenue ! Votre matricule employé : " + emp.getMatricule() + ".",
            "URGENTE");

        // ── Création automatique de la checklist d'onboarding ──
        if (onboardingService != null) {
            try {
                onboardingService.creerChecklistStandard(emp.getId(), operateur);
            } catch (Exception ignored) { /* recrutement ne doit pas crasher */ }
        }

        // ── Création automatique d'un compte User rôle EMPLOYE ──
        String usernameCree   = null;
        String mdpTemporaire  = genererMdpTemporaire();
        if (userDao != null && roleDao != null && passwordEncoder != null
                && c.getEmailCandidat() != null && !c.getEmailCandidat().isBlank()) {
            try {
                String username = genererUsername(c.getPrenomCandidat(), c.getNomCandidat());
                User u = new User();
                u.setUsername(username);
                u.setEmail(c.getEmailCandidat());
                u.setNom(c.getNomCandidat());
                u.setPrenom(c.getPrenomCandidat());
                u.setTelephone(c.getTelephoneCandidat());
                u.setInstitution(institutionRecrut);
                u.setActif(true);
                u.setPassword(passwordEncoder.encode(mdpTemporaire));
                java.util.Set<Role> roles = new java.util.HashSet<>();
                Role roleEmploye = roleDao.findByNom("EMPLOYE");
                if (roleEmploye != null) roles.add(roleEmploye);
                u.setRoles(roles);
                userDao.save(u);
                emp.setUser(u);
                employeDao.update(emp);
                usernameCree = username;
                logActionDao.save(LogAction.of(operateur, "COMPTE_AUTO_EMBAUCHE",
                    "Compte User créé pour " + emp.getNomComplet() + " (" + username + ")"));

                if (notificationDao != null) {
                    try {
                        Notification n = new Notification();
                        n.setTitre("Votre compte employé est prêt");
                        n.setMessage("Identifiant : " + username + " — Mot de passe temporaire : " + mdpTemporaire
                            + ". Changez-le dès votre première connexion.");
                        n.setType("COMPTE_EMPLOYE_CREE");
                        n.setPriorite("HAUTE");
                        n.setDestinataire(u);
                        n.setEntiteLiee("EMPLOYE");
                        n.setEntiteLieeId(emp.getId());
                        n.setLienAction("/Authentification/login.xhtml");
                        notificationDao.save(n);
                    } catch (Exception ignored) { /* best effort */ }
                }
            } catch (Exception ex) {
                logActionDao.save(LogAction.of(operateur, "COMPTE_AUTO_ECHEC",
                    "Échec création compte pour " + emp.getNomComplet() + " : " + ex.getMessage()));
            }
        }

        // Email de bienvenue avec accès au portail
        if (emailService != null && c.getEmailCandidat() != null) {
            String codeInst = institutionRecrut != null && institutionRecrut.getCode() != null
                ? institutionRecrut.getCode() : "";
            String portailUrl = "/" + codeInst;
            String posteRecrut = c.getOffre() != null
                ? safe(c.getOffre().getTitre()) : "candidature spontanée";
            String sujet = "Félicitations — Vous êtes recruté !";
            StringBuilder corps = new StringBuilder();
            corps.append("<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">")
                 .append("<h2 style=\"color:#15803d;\">Bienvenue ").append(safe(c.getPrenomCandidat()))
                 .append(" !</h2>")
                 .append("<p>Nous avons le plaisir de vous informer que votre candidature pour le poste de ")
                 .append("<strong>").append(posteRecrut).append("</strong> a été retenue.</p>")
                 .append("<p>Votre matricule employé : ")
                 .append("<strong style=\"font-family:monospace;background:#f0fdf4;padding:4px 10px;")
                 .append("border-radius:6px;\">").append(safe(emp.getMatricule())).append("</strong></p>");

            if (usernameCree != null) {
                corps.append("<h3 style=\"color:#1F4E79;margin-top:18px;\">Votre accès à la plateforme</h3>")
                     .append("<table style=\"border-collapse:collapse;margin:14px 0;\">")
                     .append("<tr><td style=\"padding:6px 14px;background:#f8fafc;font-weight:600;\">Identifiant</td>")
                     .append("<td style=\"padding:6px 14px;background:#f8fafc;font-family:monospace;\">")
                     .append(safe(usernameCree)).append("</td></tr>")
                     .append("<tr><td style=\"padding:6px 14px;font-weight:600;\">Mot de passe temporaire</td>")
                     .append("<td style=\"padding:6px 14px;font-family:monospace;\">")
                     .append(safe(mdpTemporaire)).append("</td></tr>")
                     .append("<tr><td style=\"padding:6px 14px;background:#f8fafc;font-weight:600;\">Portail</td>")
                     .append("<td style=\"padding:6px 14px;background:#f8fafc;font-family:monospace;\">")
                     .append(safe(portailUrl)).append("</td></tr>")
                     .append("</table>")
                     .append("<p style=\"color:#888;font-size:12px;\">Vous serez invité à changer ce mot de passe lors de votre première connexion.</p>");
            } else {
                corps.append("<p>Un compte utilisateur vous sera transmis prochainement par l'administration.</p>");
            }

            corps.append("<p>Date d'embauche : <strong>").append(emp.getDateEmbauche()).append("</strong></p>")
                 .append("<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p>")
                 .append("</div>");
            emailService.envoyerNotification(c.getEmailCandidat(), sujet, corps.toString(), operateur);
        }
        return emp;
    }

    /** Construit le corps HTML pour les emails de changement de statut. */
    private String construireEmailStatut(Candidature c, String statut) {
        String entete = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">"
            + "<p>Bonjour " + safe(c.getPrenomCandidat()) + ",</p>";
        String pied = "<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p></div>";
        String corps;
        switch (statut.toUpperCase()) {
            case "EN_COURS":
                corps = "<p>Votre candidature pour le poste de <strong>"
                    + safe(c.getOffre() != null ? c.getOffre().getTitre() : "")
                    + "</strong> est en cours d'étude par notre équipe.</p>"
                    + "<p>Nous reviendrons vers vous dans les meilleurs délais.</p>";
                break;
            case "RETENUE":
                corps = "<p>Bonne nouvelle ! Votre candidature pour le poste de <strong>"
                    + safe(c.getOffre() != null ? c.getOffre().getTitre() : "")
                    + "</strong> est <strong>retenue</strong> pour la suite du processus.</p>";
                break;
            case "ENTRETIEN":
                corps = "<p>Votre candidature est sélectionnée pour un entretien.</p>"
                    + "<p>Vous recevrez prochainement les détails de la convocation.</p>";
                break;
            default:
                corps = "<p>Votre candidature a évolué — nouveau statut : <strong>"
                    + safe(statut) + "</strong>.</p>";
        }
        return entete + corps + pied;
    }

    /** Génère un matricule unique format MAT-YYYY-NNNNN pour l'institution. */
    private String genererMatricule(Long institutionId) {
        long n = employeDao.countTotal() + 1;
        return String.format("MAT-%d-%05d", java.time.Year.now().getValue(), n);
    }

    /**
     * Génère le PDF du contrat de travail pour un employé.
     * Retourne le contenu binaire prêt à être téléchargé ou stocké en GED.
     */
    public byte[] genererContratPdf(Long employeId, String operateur) {
        if (contratService == null || employeId == null) return new byte[0];
        com.soutenance.entity.Employe emp = employeDao.findById(employeId);
        if (emp == null) return new byte[0];
        byte[] pdf = contratService.genererContrat(emp);
        if (pdf != null && pdf.length > 0) {
            logActionDao.save(LogAction.of(operateur, "CONTRAT_PDF_GENERE",
                "PDF contrat généré pour " + emp.getNomComplet()
                + " (" + (pdf.length / 1024) + " Ko)"));
        }
        return pdf;
    }

    /**
     * Génère un nom d'utilisateur unique au format pnom (initiale prénom + nom).
     * Si le username est déjà pris, on ajoute un suffixe numérique.
     */
    private String genererUsername(String prenom, String nom) {
        String base = ((safe(prenom).isEmpty() ? "" : safe(prenom).substring(0, 1))
                    + safe(nom))
            .toLowerCase()
            .replaceAll("[^a-z0-9]", "")
            .trim();
        if (base.isEmpty()) base = "user";
        String candidat = base;
        int i = 1;
        while (userDao != null && userDao.findByUsername(candidat) != null) {
            candidat = base + i++;
            if (i > 1000) { candidat = base + System.currentTimeMillis() % 100000; break; }
        }
        return candidat;
    }

    /** Mot de passe temporaire à 10 caractères, à changer à la 1re connexion. */
    private String genererMdpTemporaire() {
        String alpha = "abcdefghijkmnpqrstuvwxyz";
        String num   = "23456789";
        String maj   = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        java.security.SecureRandom rng = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(maj.charAt(rng.nextInt(maj.length())));
        for (int i = 0; i < 6; i++) sb.append(alpha.charAt(rng.nextInt(alpha.length())));
        for (int i = 0; i < 3; i++) sb.append(num.charAt(rng.nextInt(num.length())));
        return sb.toString();
    }

    private String safe(String s) { return s != null ? s : ""; }

    public long countCandidaturesEnAttente() { return candidatureDao.countByStatut("RECUE"); }

    // ══════════════════════════════════════════════════
    // CONGÉS / ABSENCES
    // ══════════════════════════════════════════════════

    public List<DemandeConge> getToutesDemandesConge() {
        return demandeCongeDao.findAll();
    }

    public List<DemandeConge> getDemandesEnAttente() {
        return demandeCongeDao.findByStatut("EN_ATTENTE");
    }

    @Autowired(required = false) private com.soutenance.dao.SoldeCongeDao soldeCongeDao;

    /**
     * Validation Niveau 1 par le Manager (responsable hiérarchique).
     * Le congé passe de l'étape 1 (en attente manager) à l'étape 2 (en attente RH).
     * Si refusé par le manager, le workflow s'arrête.
     */
    public void validerCongeManager(Long id, boolean approuve, String commentaire, String managerUsername) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d == null) return;

        d.setValidationManager(approuve ? "APPROUVE" : "REFUSE");
        d.setTraitePar1(managerUsername);
        d.setDateTraitement1(LocalDateTime.now());
        d.setCommentaireManager(commentaire);

        if (approuve) {
            d.setEtapeActuelle(2); // En attente du RH
            d.setStatut("EN_ATTENTE_RH");
        } else {
            d.setEtapeActuelle(0); // Workflow stoppé
            d.setStatut("REFUSE");
            libererReservation(d);
        }
        demandeCongeDao.update(d);

        logActionDao.save(LogAction.of(managerUsername,
            approuve ? "VALIDATION_MANAGER_CONGE" : "REFUS_MANAGER_CONGE",
            d.getEmploye().getNomComplet() + " — " + (approuve ? "validé" : "refusé")
            + " par manager (" + d.getNombreJours() + " j)"));

        // Email à l'employé en cas de refus, sinon il attend la validation RH
        if (!approuve && emailService != null && d.getEmploye() != null
                && d.getEmploye().getEmail() != null) {
            String corps = "<div style=\"font-family:Arial,sans-serif;font-size:14px;\">"
                + "<p>Bonjour " + safe(d.getEmploye().getPrenom()) + ",</p>"
                + "<p>Votre demande de congé a été <strong style=\"color:#dc2626;\">refusée</strong> "
                + "par votre responsable hiérarchique.</p>"
                + "<p style=\"background:#fef2f2;border-left:3px solid #dc2626;padding:10px;\">"
                + safe(commentaire) + "</p>"
                + "<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p></div>";
            emailService.envoyerNotification(d.getEmploye().getEmail(),
                "Votre demande de congé (avis manager)", corps, managerUsername);
        }
    }

    public void approuverConge(Long id, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d == null) return;

        // Vérification workflow 2 niveaux : le manager doit avoir validé d'abord
        // (sauf si la demande est ancienne, sans etapeActuelle initialisé)
        if (d.getEtapeActuelle() != null && d.getEtapeActuelle() == 1
                && !"APPROUVE".equalsIgnoreCase(d.getValidationManager())) {
            logActionDao.save(LogAction.of(operateur, "RH_BLOQUE",
                "Tentative approbation par RH sans validation manager : "
                + d.getEmploye().getNomComplet()));
            throw new IllegalStateException("Le manager doit d'abord valider cette demande de congé.");
        }

        d.setStatut("APPROUVE");
        d.setValidationRH("APPROUVE");
        d.setTraitePar2(operateur);
        d.setDateTraitement2(LocalDateTime.now());
        d.setTraitePar(operateur);
        d.setDateTraitement(LocalDateTime.now());
        d.setEtapeActuelle(3); // Cycle terminé
        demandeCongeDao.update(d);

        // ── Décompte automatique du solde de congés ──
        String detailSolde = decompterSolde(d);

        logActionDao.save(LogAction.of(operateur,
            "APPROBATION_CONGE",
            "Congé approuvé : " + d.getEmploye().getNomComplet()
            + " (" + d.getNombreJours() + "j)" + detailSolde));

        // Email à l'employé concerné
        if (emailService != null && d.getEmploye() != null && d.getEmploye().getEmail() != null) {
            String corps = "<div style=\"font-family:Arial,sans-serif;font-size:14px;\">"
                + "<p>Bonjour " + safe(d.getEmploye().getPrenom()) + ",</p>"
                + "<p>Votre demande de congé du <strong>" + d.getDateDebut()
                + "</strong> au <strong>" + d.getDateFin() + "</strong> ("
                + d.getNombreJours() + " jours) a été <strong style=\"color:#15803d;\">"
                + "approuvée</strong>.</p>"
                + "<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p></div>";
            emailService.envoyerNotification(d.getEmploye().getEmail(),
                "Votre congé est approuvé", corps, operateur);
        }

        notifierEmploye(d.getEmploye(), "Congé approuvé",
            "Votre demande de congé du " + d.getDateDebut() + " au " + d.getDateFin()
                + " (" + d.getNombreJours() + " jours) a été approuvée.",
            "CONGE_APPROUVE", "NORMALE");
    }

    public void refuserConge(Long id, String motif, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d == null) return;
        d.setStatut("REFUSE");
        d.setValidationRH("REFUSE");
        d.setTraitePar2(operateur);
        d.setDateTraitement2(LocalDateTime.now());
        d.setCommentaireRH(motif);
        d.setTraitePar(operateur);
        d.setDateTraitement(LocalDateTime.now());
        d.setEtapeActuelle(0); // Stoppé
        demandeCongeDao.update(d);

        // Libérer les jours réservés (joursEnAttente)
        libererReservation(d);

        logActionDao.save(LogAction.of(operateur,
            "REFUS_CONGE",
            "Congé refusé : " + d.getEmploye().getNomComplet()));

        if (emailService != null && d.getEmploye() != null && d.getEmploye().getEmail() != null) {
            String corps = "<div style=\"font-family:Arial,sans-serif;font-size:14px;\">"
                + "<p>Bonjour " + safe(d.getEmploye().getPrenom()) + ",</p>"
                + "<p>Votre demande de congé du <strong>" + d.getDateDebut()
                + "</strong> au <strong>" + d.getDateFin() + "</strong> a été "
                + "<strong style=\"color:#dc2626;\">refusée</strong>.</p>"
                + "<p style=\"background:#fef2f2;border-left:3px solid #dc2626;padding:10px 14px;"
                + "color:#7f1d1d;font-style:italic;\">" + safe(motif) + "</p>"
                + "<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p></div>";
            emailService.envoyerNotification(d.getEmploye().getEmail(),
                "Votre demande de congé", corps, operateur);
        }

        notifierEmploye(d.getEmploye(), "Congé refusé",
            "Votre demande de congé du " + d.getDateDebut() + " au " + d.getDateFin()
                + " a été refusée. Motif : " + safe(motif),
            "CONGE_REFUSE", "NORMALE");
    }

    /**
     * Décompte les jours pris du SoldeConge de l'employé sur l'exercice
     * de la date de début. Retourne un détail formaté pour le log.
     * Si pas de solde existant, retourne une chaîne vide (pas de décompte).
     */
    private String decompterSolde(DemandeConge d) {
        if (soldeCongeDao == null || d.getEmploye() == null
            || d.getTypeConge() == null || d.getNombreJours() == null) return "";
        try {
            String exercice = String.valueOf(d.getDateDebut().getYear());
            com.soutenance.entity.SoldeConge solde = soldeCongeDao.findByEmployeTypeExercice(
                d.getEmploye().getId(), d.getTypeConge().getId(), exercice);
            if (solde == null) return " (pas de solde — décompte ignoré)";
            double avant = solde.getSoldeReel();
            solde.consommer(d.getNombreJours());
            soldeCongeDao.update(solde);
            return String.format(" — solde %s : %.1f → %.1f j",
                d.getTypeConge().getLibelle(), avant, solde.getSoldeReel());
        } catch (Exception ex) {
            return " (erreur décompte : " + ex.getMessage() + ")";
        }
    }

    /** Libère les jours réservés (en attente) lors d'un refus ou annulation. */
    private void libererReservation(DemandeConge d) {
        if (soldeCongeDao == null || d.getEmploye() == null
            || d.getTypeConge() == null || d.getNombreJours() == null) return;
        try {
            String exercice = String.valueOf(d.getDateDebut().getYear());
            com.soutenance.entity.SoldeConge solde = soldeCongeDao.findByEmployeTypeExercice(
                d.getEmploye().getId(), d.getTypeConge().getId(), exercice);
            if (solde != null) {
                solde.setJoursEnAttente(Math.max(0,
                    solde.getJoursEnAttente() - d.getNombreJours()));
                soldeCongeDao.update(solde);
            }
        } catch (Exception ignored) { /* best effort */ }
    }

    public long countCongesEnAttente() { return demandeCongeDao.countByStatut("EN_ATTENTE"); }

    // ══════════════════════════════════════════════════
    // FORMATIONS
    // ══════════════════════════════════════════════════

    public List<Formation> getToutesFormations() {
        return formationDao.findAll();
    }

    public Formation getFormationById(Long id) {
        return formationDao.findById(id);
    }

    public void creerFormation(Formation formation, String operateur) {
        formation.setCreePar(operateur);
        formationDao.save(formation);
        logActionDao.save(LogAction.of(operateur,
            "CREATION_FORMATION",
            "Formation créée : " + formation.getTitre()));
    }

    public void modifierFormation(Formation formation, String operateur) {
        formationDao.update(formation);
        logActionDao.save(LogAction.of(operateur,
            "MODIFICATION_FORMATION",
            "Formation modifiée : " + formation.getTitre()));
    }

    public void inscrireEmploye(Long formationId, Long employeId, String operateur) {
        Formation f = formationDao.findById(formationId);
        Employe   e = employeDao.findById(employeId);
        if (f != null && e != null) {
            f.getParticipants().add(e);
            formationDao.update(f);
            logActionDao.save(LogAction.of(operateur,
                "INSCRIPTION_FORMATION",
                e.getNomComplet() + " inscrit à " + f.getTitre()));
        }
    }

    public long countFormationsActives() { return formationDao.countByStatut("PLANIFIEE"); }

    // ══════════════════════════════════════════════════
    // ÉVALUATIONS
    // ══════════════════════════════════════════════════

    public List<Evaluation> getToutesEvaluations() {
        return evaluationDao.findAll();
    }

    public void lancerEvaluation(Evaluation evaluation, String operateur) {
        evaluation.setCreePar(operateur);
        evaluationDao.save(evaluation);
        logActionDao.save(LogAction.of(operateur,
            "LANCEMENT_EVALUATION",
            "Évaluation lancée : " + evaluation.getEmploye().getNomComplet()));
    }

    public void validerEvaluation(Long id, String operateur) {
        Evaluation e = evaluationDao.findById(id);
        if (e != null) {
            e.setStatut("VALIDEE");
            evaluationDao.update(e);
            logActionDao.save(LogAction.of(operateur,
                "VALIDATION_EVALUATION",
                "Évaluation validée : " + e.getEmploye().getNomComplet()));
        }
    }

    public void sauvegarderEvaluation(Evaluation evaluation, String operateur) {
        if (evaluation.getId() == null) {
            lancerEvaluation(evaluation, operateur);
        } else {
            evaluationDao.update(evaluation);
        }
    }

    // ══════════════════════════════════════════════════
    // CARRIÈRES
    // ══════════════════════════════════════════════════

    public List<MouvementCarriere> getTousMouvements() {
        return mouvementCarriereDao.findAll();
    }

    public List<MouvementCarriere> getMouvementsByEmploye(Long employeId) {
        return mouvementCarriereDao.findByEmploye(employeId);
    }

    public void enregistrerMouvement(MouvementCarriere mouvement, String operateur) {
        mouvement.setCreePar(operateur);
        mouvementCarriereDao.save(mouvement);
        logActionDao.save(LogAction.of(operateur,
            "MOUVEMENT_CARRIERE",
            mouvement.getTypeMouvement() + " : " + mouvement.getEmploye().getNomComplet()));
    }

    public void approuverMouvement(Long id, String operateur) {
        MouvementCarriere m = mouvementCarriereDao.findById(id);
        if (m != null) {
            m.setStatut("APPROUVE");
            m.setApprouvePar(operateur);
            m.setDateApprobation(LocalDateTime.now());
            Employe e = m.getEmploye();

            // ── Résolution du nouveau poste ────────────────────────────────
            if (m.getNouveauPoste() != null) {
                Poste nouveauPoste = posteDao.findByIntitule(m.getNouveauPoste());
                if (nouveauPoste != null) e.setPoste(nouveauPoste);
            }

            // ── Mise à jour salaire et grade ───────────────────────────────
            if (m.getNouveauSalaire() != null) e.setSalaireBase(m.getNouveauSalaire());
            if (m.getNouveauGrade()   != null) e.setGradeEchelon(m.getNouveauGrade());

            // ── CORRECTION : mutation inter-institutions ───────────────────
            // Si le mouvement a une institution de destination, réaffecter
            // l'employé à cette institution ET changer son statut en DETACHE
            // ou le laisser ACTIF selon le type de mouvement.
            if (m.isMutationInterInstitution()) {
                Institution ancienneInstitution = e.getInstitution();
                e.setInstitution(m.getNouvelleInstitution());

                // Traçabilité : enregistrer l'ancienne institution dans les observations
                String obs = (m.getObservations() != null ? m.getObservations() + " | " : "")
                    + "Mutation depuis : "
                    + (ancienneInstitution != null ? ancienneInstitution.getNom() : "?");
                m.setObservations(obs);

                // Statut détaché si détachement, sinon ACTIF
                if ("DETACHEMENT".equals(m.getTypeMouvement())) {
                    e.setStatut("DETACHE");
                }

                logActionDao.save(LogAction.of(operateur,
                    "MUTATION_INTER_INSTITUTION",
                    e.getNomComplet() + " muté vers " + m.getNouvelleInstitution().getNom()));
            }

            employeDao.update(e);
            mouvementCarriereDao.update(m);
            logActionDao.save(LogAction.of(operateur,
                "APPROBATION_MOUVEMENT",
                "Mouvement approuvé : " + e.getNomComplet()));
        }
    }
}