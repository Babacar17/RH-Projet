package com.soutenance.service;

import com.soutenance.bean.DemandeSpontaneeBean;
import com.soutenance.dao.CandidatDao;
import com.soutenance.dao.OffreEmploiDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.Candidature;
import com.soutenance.entity.OffreEmploi;
import com.soutenance.entity.User;
import com.soutenance.notification.ModeleNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service candidat — offres, candidatures, profil.
 *
 * CORRECTION : BCryptPasswordEncoder instancié manuellement → injecté via Spring.
 *
 * AVANT :
 *   private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
 *
 * PROBLÈME : UserService, AdminService et CandidatService créaient chacun leur
 * propre instance BCryptPasswordEncoder. Si l'un changeait le strength par
 * rapport aux autres, changerMotDePasse() échouerait silencieusement car
 * le hash stocké (créé par UserService) ne correspond plus à l'encodeur
 * local de CandidatService.
 *
 * APRÈS : @Autowired BCryptPasswordEncoder encoder — un seul bean partagé
 * déclaré dans SpringJsfConfig.
 *
 * Prérequis : SpringJsfConfig doit déclarer :
 *   @Bean
 *   public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 */
@Service
public class CandidatService {

    @Autowired private OffreEmploiDao          offreDao;
    @Autowired private CandidatDao             candidatDao;
    @Autowired private UserDao                 userDao;
    @Autowired private NotificationRHService   notificationRHService;

    // CORRECTION : injecté par Spring — plus de new BCryptPasswordEncoder()
    @Autowired private BCryptPasswordEncoder   encoder;

    private static final String UPLOAD_DIR = "/uploads/demandes/";

    // ── Offres ────────────────────────────────────────────────────────────────

    /**
     * Offres ouvertes aux candidatures, toutes institutions confondues : un candidat
     * n'appartient à aucune institution avant de postuler, et celle-ci est ensuite
     * celle de l'offre retenue.
     */
    public List<OffreEmploi> getOffresDisponibles() {
        return offreDao.findByStatut("PUBLIEE")
               .stream()
               .filter(OffreEmploi::isOuverte)
               .collect(Collectors.toList());
    }

    // ── Candidatures ──────────────────────────────────────────────────────────

    public List<Candidature> getCandidaturesParUser(Long userId) {
        return candidatDao.findByUserId(userId);
    }

    public boolean aDejaPostule(Long userId, Long offreId) {
        return candidatDao.existsByUserAndOffre(userId, offreId);
    }

    /**
     * Soumet une candidature.
     * Lève IllegalStateException si l'offre est fermée — protection serveur
     * indépendante de l'interface.
     */
    public void postuler(User candidat, OffreEmploi offre, Candidature candidature) {
        if (!offre.isOuverte()) {
            throw new IllegalStateException(
                "Cette offre est clôturée et n'accepte plus de candidatures.");
        }
        candidature.setOffre(offre);
        candidature.setCandidatUser(candidat);
        if (candidature.getNomCandidat() == null || candidature.getNomCandidat().isEmpty())
            candidature.setNomCandidat(candidat.getNom() != null ? candidat.getNom() : "");
        if (candidature.getPrenomCandidat() == null || candidature.getPrenomCandidat().isEmpty())
            candidature.setPrenomCandidat(candidat.getPrenom() != null ? candidat.getPrenom() : "");
        if (candidature.getEmailCandidat() == null || candidature.getEmailCandidat().isEmpty())
            candidature.setEmailCandidat(candidat.getEmail() != null ? candidat.getEmail() : "");
        if (candidature.getTelephoneCandidat() == null || candidature.getTelephoneCandidat().isEmpty())
            candidature.setTelephoneCandidat(candidat.getTelephone() != null ? candidat.getTelephone() : "");
        candidature.setStatut("RECUE");
        candidatDao.save(candidature);
        notificationRHService.notifierRH(offre.getInstitution() != null ? offre.getInstitution().getId() : null,
            ModeleNotification.CANDIDATURE_RECUE, "CANDIDATURE", candidature.getId(),
            candidature.getPrenomCandidat() + " " + candidature.getNomCandidat(), offre.getTitre());
    }

    public void annulerCandidature(Long id) {
        Candidature c = candidatDao.findById(id);
        if (c != null && "RECUE".equals(c.getStatut())) {
            candidatDao.delete(id);
        }
    }

    // ── Demande Spontanée ─────────────────────────────────────────────────────

    public void enregistrerDemande(DemandeSpontaneeBean bean, User user) throws Exception {
        saveFile(bean.getCvFichier().getContent(),            "cv_"       + bean.getEmail() + ".pdf");
        saveFile(bean.getLettreFichier().getContent(),        "lettre_"   + bean.getEmail() + ".pdf");
        saveFile(bean.getPieceIdentiteFichier().getContent(), "piece_id_" + bean.getEmail() + ".pdf");
        saveFile(bean.getDiplomesFichier().getContent(),      "diplomes_" + bean.getEmail() + ".pdf");

        if ("STAGE".equals(bean.getTypeDemande())) {
            saveFile(bean.getLettreEtablissementFichier().getContent(),
                     "lettre_etab_" + bean.getEmail() + ".pdf");
            saveFile(bean.getReleveNotesFichier().getContent(),
                     "releve_"      + bean.getEmail() + ".pdf");
        } else {
            if (bean.getCertificatsTravailFichier() != null
                    && bean.getCertificatsTravailFichier().getSize() > 0)
                saveFile(bean.getCertificatsTravailFichier().getContent(),
                         "certifs_" + bean.getEmail() + ".pdf");
            if (bean.getCasierJudiciaireFichier() != null
                    && bean.getCasierJudiciaireFichier().getSize() > 0)
                saveFile(bean.getCasierJudiciaireFichier().getContent(),
                         "casier_"  + bean.getEmail() + ".pdf");
        }
        // TODO: persister une entité DemandeSpontanee en base si nécessaire
    }

    private void saveFile(byte[] data, String filename) throws IOException {
        Path dir = Paths.get(UPLOAD_DIR);
        Files.createDirectories(dir);
        Files.write(dir.resolve(filename), data);
    }

    // ── Profil ────────────────────────────────────────────────────────────────

    /**
     * Change le mot de passe d'un candidat.
     * CORRECTION : encoder injecté — cohérence garantie avec UserService.
     */
    public boolean changerMotDePasse(String username, String ancienMdp, String nouveauMdp) {
        User user = userDao.findByUsername(username);
        if (user == null) return false;
        if (!encoder.matches(ancienMdp, user.getPassword())) return false;
        user.setPassword(encoder.encode(nouveauMdp));
        userDao.update(user);
        return true;
    }

    public void mettreAJourProfil(User user) {
        userDao.update(user);
    }
}