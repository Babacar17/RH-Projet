package com.soutenance.service;

import com.soutenance.dao.DemandeCongeDao;
import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.EvenementDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.entity.DemandeConge;
import com.soutenance.entity.Employe;
import com.soutenance.entity.Evenement;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.ParticipationEvenement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service métier pour les événements (séminaires, réunions, voyages…).
 * Gère le CRUD, les participations et l'envoi des invitations email.
 */
@Service
@Transactional
public class EvenementService {

    @Autowired private EvenementDao   evenementDao;
    @Autowired private EmployeDao     employeDao;
    @Autowired private LogActionDao   logActionDao;
    @Autowired(required = false) private DemandeCongeDao demandeCongeDao;
    @Autowired(required = false) private EmailService emailService;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", new Locale("fr", "FR"));

    // ── CRUD ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Evenement> getAll() { return evenementDao.findAll(); }

    @Transactional(readOnly = true)
    public List<Evenement> getByInstitution(Long institutionId) {
        return institutionId != null
            ? evenementDao.findByInstitution(institutionId)
            : evenementDao.findAll();
    }

    @Transactional(readOnly = true)
    public List<Evenement> getAVenirByInstitution(Long institutionId, int limit) {
        return evenementDao.findAVenirByInstitution(institutionId, limit);
    }

    @Transactional(readOnly = true)
    public Evenement getById(Long id) { return evenementDao.findById(id); }

    @Transactional(readOnly = true)
    public List<Evenement> getByParticipant(Long employeId) {
        return evenementDao.findByParticipant(employeId);
    }

    @Transactional(readOnly = true)
    public long countByInstitution(Long institutionId) {
        return evenementDao.countByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public long countAVenirByInstitution(Long institutionId) {
        return evenementDao.countAVenirByInstitution(institutionId);
    }

    public void creer(Evenement e, String operateur) {
        if (e.getStatut() == null) e.setStatut("PROGRAMME");
        e.setCreePar(operateur);
        evenementDao.save(e);
        logActionDao.save(LogAction.of(operateur, "EVENEMENT_CREATION",
            "Création de l'événement « " + e.getTitre() + " » ("
            + e.getType() + ") le " + e.getDateDebut()));
    }

    public void modifier(Evenement e, String operateur) {
        evenementDao.update(e);
        logActionDao.save(LogAction.of(operateur, "EVENEMENT_MODIFICATION",
            "Événement modifié : " + e.getTitre()));
    }

    public void supprimer(Long id, String operateur) {
        Evenement e = evenementDao.findById(id);
        if (e == null) return;
        String titre = e.getTitre();
        evenementDao.delete(id);
        logActionDao.save(LogAction.of(operateur, "EVENEMENT_SUPPRESSION",
            "Événement supprimé : " + titre));
    }

    /** Bascule le statut PROGRAMME → CONFIRME ou inversement. */
    public void changerStatut(Long id, String nouveauStatut, String operateur) {
        Evenement e = evenementDao.findById(id);
        if (e == null) return;
        String ancien = e.getStatut();
        e.setStatut(nouveauStatut);
        evenementDao.update(e);
        logActionDao.save(LogAction.of(operateur, "EVENEMENT_STATUT",
            e.getTitre() + " : " + ancien + " → " + nouveauStatut));
    }

    // ── Participations ──────────────────────────────────────────────────────

    /**
     * Convoque un employé à un événement et envoie une notification (pas une
     * demande de confirmation : l'employé est informé qu'il doit participer,
     * il n'a pas à valider — c'est au RH de vérifier au préalable les conflits
     * de congé via {@link #aConflitConge}). RG-06 : si la capacité maximale est
     * déjà atteinte, l'employé est placé en liste d'attente et sera promu
     * automatiquement si un confirmé se désiste ({@link #seDesister}).
     * Idempotent : si l'employé est déjà convoqué, ne fait rien.
     */
    public ParticipationEvenement inviter(Long evenementId, Long employeId, String operateur) {
        Evenement e = evenementDao.findById(evenementId);
        Employe   emp = employeDao.findById(employeId);
        if (e == null || emp == null) return null;

        ParticipationEvenement existante =
            evenementDao.findParticipation(evenementId, employeId);
        if (existante != null) return existante;

        String statutInitial = "CONFIRME";
        Integer cap = e.getCapaciteMax();
        if (cap != null && cap > 0) {
            long confirmesActuels = e.getParticipations() != null
                ? e.getParticipations().stream().filter(x -> "CONFIRME".equals(x.getStatut())).count() : 0;
            if (confirmesActuels >= cap) statutInitial = "LISTE_ATTENTE";
        }

        ParticipationEvenement p = new ParticipationEvenement();
        p.setEvenement(e);
        p.setEmploye(emp);
        p.setStatut(statutInitial);
        p.setDateReponse(LocalDateTime.now());
        evenementDao.saveParticipation(p);

        // Envoi email de convocation (informatif)
        if (emailService != null && emp.getEmail() != null && !emp.getEmail().isBlank()) {
            String corps = construireEmailInvitation(e, emp);
            boolean ok = emailService.envoyerNotification(emp.getEmail(),
                ("LISTE_ATTENTE".equals(statutInitial) ? "Liste d'attente : " : "Convocation : ") + e.getTitre(),
                corps, operateur);
            if (ok) {
                p.setEmailEnvoye(true);
                evenementDao.updateParticipation(p);
            }
        }

        logActionDao.save(LogAction.of(operateur, "EVENEMENT_CONVOCATION",
            emp.getNomComplet() + " convoqué(e) à « " + e.getTitre() + " »"
            + ("LISTE_ATTENTE".equals(statutInitial) ? " (liste d'attente, capacité atteinte)" : "")));
        return p;
    }

    /**
     * Désistement volontaire d'un participant avant le début de l'événement,
     * avec motif facultatif. Sans action de l'employé, sa convocation reste
     * valable (CONFIRME) — ce n'est que s'il clique explicitement qu'il se
     * désiste. Si le désisté était CONFIRME, le premier de la liste d'attente
     * (par ordre de convocation) est automatiquement promu et notifié (RG-06).
     *
     * @throws IllegalStateException si l'événement a déjà commencé ou si le
     *         statut actuel ne permet pas de se désister
     */
    public void seDesister(Long participationId, String motif, String operateur) {
        ParticipationEvenement p = evenementDao.findParticipationById(participationId);
        if (p == null) throw new IllegalArgumentException("Participation introuvable.");
        Evenement e = p.getEvenement();

        if (e != null && e.getDateDebut() != null && e.getDateDebut().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Impossible de se désister : l'événement a déjà commencé.");
        }
        if (!"CONFIRME".equals(p.getStatut()) && !"LISTE_ATTENTE".equals(p.getStatut())) {
            throw new IllegalStateException("Ce statut ne permet pas de se désister.");
        }

        String ancienStatut = p.getStatut();
        p.setStatut("DESISTE");
        p.setCommentaire(motif);
        p.setDateReponse(LocalDateTime.now());
        evenementDao.updateParticipation(p);

        logActionDao.save(LogAction.of(operateur, "EVENEMENT_DESISTEMENT",
            (p.getEmploye() != null ? p.getEmploye().getNomComplet() : "?")
            + " s'est désisté(e) de « " + (e != null ? e.getTitre() : "?") + " »"
            + (motif != null && !motif.isBlank() ? " — motif : " + motif : "")));

        if ("CONFIRME".equals(ancienStatut) && e != null) {
            promouvoirProchainEnAttente(e.getId(), operateur);
        }
    }

    /** Promeut le participant le plus ancien en liste d'attente vers CONFIRME, et le notifie. */
    private void promouvoirProchainEnAttente(Long evenementId, String operateur) {
        Evenement e = evenementDao.findById(evenementId);
        if (e == null || e.getParticipations() == null) return;

        e.getParticipations().stream()
            .filter(x -> "LISTE_ATTENTE".equals(x.getStatut()))
            .sorted(java.util.Comparator.comparing(ParticipationEvenement::getDateInvitation,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .findFirst()
            .ifPresent(suivant -> {
                suivant.setStatut("CONFIRME");
                suivant.setDateReponse(LocalDateTime.now());
                evenementDao.updateParticipation(suivant);

                Employe emp = suivant.getEmploye();
                if (emailService != null && emp != null && emp.getEmail() != null && !emp.getEmail().isBlank()) {
                    String corps = construireEmailInvitation(e, emp);
                    boolean ok = emailService.envoyerNotification(emp.getEmail(),
                        "Convocation confirmée : " + e.getTitre(), corps, operateur);
                    if (ok) {
                        suivant.setEmailEnvoye(true);
                        evenementDao.updateParticipation(suivant);
                    }
                }

                logActionDao.save(LogAction.of(operateur, "EVENEMENT_PROMOTION_LISTE_ATTENTE",
                    (emp != null ? emp.getNomComplet() : "?") + " promu(e) de la liste d'attente pour « "
                    + e.getTitre() + " » suite à un désistement."));
            });
    }

    /**
     * Change le statut d'une participation (utilisé en interne pour le
     * marquage présent/absent — les employés n'ont plus d'étape de
     * confirmation/déclinaison, ils sont directement convoqués par
     * {@link #inviter}).
     */
    public void repondreInvitation(Long participationId, String reponse,
                                   String commentaire, String operateur) {
        ParticipationEvenement p = evenementDao.findParticipationById(participationId);
        if (p == null) return;

        p.setStatut(reponse);
        p.setCommentaire(commentaire);
        p.setDateReponse(LocalDateTime.now());
        evenementDao.updateParticipation(p);

        logActionDao.save(LogAction.of(operateur, "EVENEMENT_REPONSE",
            (p.getEmploye() != null ? p.getEmploye().getNomComplet() : "?")
            + " → " + reponse + " pour « "
            + (p.getEvenement() != null ? p.getEvenement().getTitre() : "?") + " »"));
    }

    /**
     * Marque un participant présent/absent — RG-08 : réservé aux participants
     * CONFIRME (ou déjà marqués, pour permettre une correction avant validation).
     * Refusé si les présences de l'événement sont déjà validées (RG-09).
     */
    public void marquerPresence(Long participationId, boolean present, String operateur) {
        ParticipationEvenement p = evenementDao.findParticipationById(participationId);
        if (p == null) return;
        if (p.getEvenement() != null && p.getEvenement().isPresenceValidee()) {
            throw new IllegalStateException(
                "Les présences de cet événement sont déjà validées et verrouillées.");
        }
        if (!"CONFIRME".equals(p.getStatut()) && !"PRESENT".equals(p.getStatut()) && !"ABSENT".equals(p.getStatut())) {
            throw new IllegalStateException(
                "Seuls les participants confirmés peuvent être marqués présents ou absents.");
        }
        repondreInvitation(participationId, present ? "PRESENT" : "ABSENT", null, operateur);
    }

    /**
     * Verrouille les présences de l'événement (RG-09) : à partir de cet instant,
     * plus aucun marquage n'est accepté, et seule cette validation autorise
     * EvenementDao.sumIndemnitesParticipations à compter l'événement côté paie.
     */
    public void validerPresences(Long evenementId, String operateur) {
        Evenement e = evenementDao.findById(evenementId);
        if (e == null) throw new IllegalArgumentException("Événement introuvable.");
        if (e.isPresenceValidee())
            throw new IllegalStateException("Les présences sont déjà validées.");
        if (e.getDateFin() != null && e.getDateFin().isAfter(LocalDateTime.now())
                && (e.getDateDebut() == null || e.getDateDebut().isAfter(LocalDateTime.now())))
            throw new IllegalStateException("L'événement n'a pas encore commencé.");

        e.setPresenceValidee(true);
        e.setPresenceValideePar(operateur);
        e.setDatePresenceValidee(LocalDateTime.now());
        evenementDao.update(e);

        long nbPresents = e.getParticipations() != null
            ? e.getParticipations().stream().filter(p -> "PRESENT".equals(p.getStatut())).count() : 0;
        logActionDao.save(LogAction.of(operateur, "EVENEMENT_PRESENCES_VALIDEES",
            "Présences validées pour « " + e.getTitre() + "» : " + nbPresents + " présent(s)."
            + (e.getIndemniteParticipant() != null && e.getIndemniteParticipant().signum() > 0
                ? " Indemnités débloquées pour la paie." : "")));
    }

    /**
     * RG-07 : signale si l'employé a un congé approuvé chevauchant la période de
     * l'événement. Renvoie false si le module Congés n'est pas disponible.
     */
    @Transactional(readOnly = true)
    public boolean aConflitConge(Long employeId, LocalDateTime debutEvt, LocalDateTime finEvt) {
        if (demandeCongeDao == null || employeId == null || debutEvt == null || finEvt == null) return false;
        LocalDate debut = debutEvt.toLocalDate();
        LocalDate fin = finEvt.toLocalDate();
        try {
            return demandeCongeDao.findByEmployeId(employeId).stream()
                .filter(d -> "APPROUVE".equalsIgnoreCase(d.getStatut()))
                .anyMatch(d -> d.getDateDebut() != null && d.getDateFin() != null
                    && !d.getDateFin().isBefore(debut) && !d.getDateDebut().isAfter(fin));
        } catch (Exception ex) { return false; }
    }

    public void retirerParticipant(Long participationId, String operateur) {
        ParticipationEvenement p = evenementDao.findParticipationById(participationId);
        boolean etaitConfirme = p != null && "CONFIRME".equals(p.getStatut());
        Long evenementId = p != null && p.getEvenement() != null ? p.getEvenement().getId() : null;

        evenementDao.deleteParticipation(participationId);
        logActionDao.save(LogAction.of(operateur, "EVENEMENT_DESINVIT",
            "Participation supprimée id=" + participationId));

        // Même règle que seDesister (RG-06) : libérer une place CONFIRMÉE promeut le premier en liste d'attente.
        if (etaitConfirme && evenementId != null) {
            promouvoirProchainEnAttente(evenementId, operateur);
        }
    }

    // ── Email helpers ───────────────────────────────────────────────────────

    private String construireEmailInvitation(Evenement e, Employe emp) {
        String dateDebut = e.getDateDebut() != null ? e.getDateDebut().format(FMT) : "?";
        String dateFin   = e.getDateFin()   != null ? e.getDateFin().format(FMT)   : "?";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222;\">")
          .append("<p>Bonjour ").append(safe(emp.getPrenom())).append(",</p>")
          .append("<p>Votre présence est requise à l'événement suivant :</p>")
          .append("<h2 style=\"color:#1F4E79;margin:18px 0 8px;\">").append(safe(e.getTitre())).append("</h2>")
          .append("<p><em>").append(safe(e.getType())).append("</em></p>");

        if (e.getDescription() != null && !e.getDescription().isBlank()) {
            sb.append("<p style=\"background:#f8fafc;padding:12px 14px;border-left:3px solid #1F4E79;\">")
              .append(safe(e.getDescription())).append("</p>");
        }

        sb.append("<table style=\"border-collapse:collapse;margin:14px 0;\">")
          .append("<tr><td style=\"padding:6px 14px;font-weight:600;background:#f8fafc;\">Date de début</td>")
          .append("<td style=\"padding:6px 14px;background:#f8fafc;\">").append(dateDebut).append("</td></tr>")
          .append("<tr><td style=\"padding:6px 14px;font-weight:600;\">Date de fin</td>")
          .append("<td style=\"padding:6px 14px;\">").append(dateFin).append("</td></tr>")
          .append("<tr><td style=\"padding:6px 14px;font-weight:600;background:#f8fafc;\">Modalité</td>")
          .append("<td style=\"padding:6px 14px;background:#f8fafc;\">").append(safe(e.getModalite())).append("</td></tr>");
        if (e.getLieu() != null && !e.getLieu().isBlank()) {
            sb.append("<tr><td style=\"padding:6px 14px;font-weight:600;\">Lieu</td>")
              .append("<td style=\"padding:6px 14px;\">").append(safe(e.getLieu())).append("</td></tr>");
        }
        if (e.getUrlVisio() != null && !e.getUrlVisio().isBlank()) {
            sb.append("<tr><td style=\"padding:6px 14px;font-weight:600;background:#f8fafc;\">Lien visio</td>")
              .append("<td style=\"padding:6px 14px;background:#f8fafc;\">")
              .append("<a href=\"").append(safe(e.getUrlVisio())).append("\">")
              .append(safe(e.getUrlVisio())).append("</a></td></tr>");
        }
        sb.append("</table>")
          .append("<p>Merci de noter cette date dans votre agenda. Retrouvez le détail depuis votre espace personnel.</p>")
          .append("<p style=\"color:#888;font-size:12px;\">Le service des Ressources Humaines</p>")
          .append("</div>");
        return sb.toString();
    }

    private String safe(String s) { return s != null ? s : ""; }
}
