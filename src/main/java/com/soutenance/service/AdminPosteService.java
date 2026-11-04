package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de gestion des postes — périmètre administrateur.
 *
 * Couvre :
 *  - Référentiel des postes par institution avec contrôle unicité code
 *  - Fiches de poste (CRUD, validation, archivage)
 *  - Activation / désactivation des postes
 *  - Supervision des vacances de postes
 */
@Service
@Transactional
public class AdminPosteService {

    @Autowired private PosteDao      posteDao;
    @Autowired private FichePosteDao fichePosteDao;
    @Autowired private LogActionDao  logActionDao;
    @Autowired private InstitutionDao institutionDao;

    // ══════════════════════════════════════════════════
    // POSTES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Poste> getPostesByInstitution(Long institutionId) {
        return posteDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public Poste getPosteById(Long id) {
        return posteDao.findById(id);
    }

    /**
     * Crée un poste en vérifiant l'unicité du code dans l'institution.
     *
     * @return null si succès, message d'erreur sinon
     */
    public String creerPoste(Poste poste, Long institutionId, String operateur) {
        if (posteDao.existsByCodeAndInstitution(poste.getCode(), institutionId)) {
            return "Un poste avec le code « " + poste.getCode() + " » existe déjà dans cette institution.";
        }
        Institution institution = institutionDao.findById(institutionId);
        poste.setInstitution(institution);
        poste.setCreePar(operateur);
        if (poste.getStatut() == null) poste.setStatut("ACTIF");
        posteDao.save(poste);
        logActionDao.save(LogAction.of(operateur, "CREATION_POSTE",
            "Poste créé : " + poste.getIntitule() + " (" + poste.getCode() + ")"));
        return null;
    }

    public void modifierPoste(Poste poste, String operateur) {
        posteDao.update(poste);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_POSTE",
            "Poste modifié : " + poste.getIntitule()));
    }

    public void changerStatutPoste(Long id, String statut, String operateur) {
        Poste p = posteDao.findById(id);
        if (p != null) {
            p.setStatut(statut);
            posteDao.update(p);
            logActionDao.save(LogAction.of(operateur,
                "ACTIF".equals(statut) ? "ACTIVATION_POSTE" : "DESACTIVATION_POSTE",
                "Statut poste → " + statut + " : " + p.getIntitule()));
        }
    }

    /**
     * Supprime un poste uniquement s'il n'est pas occupé.
     *
     * @return null si succès, message d'erreur sinon
     */
    public String supprimerPoste(Long id, String operateur) {
        Poste p = posteDao.findById(id);
        if (p == null) return "Poste introuvable.";
        if ("OCCUPE".equals(p.getStatut())) {
            return "Ce poste est occupé. Effectuez un mouvement de carrière avant de le supprimer.";
        }
        logActionDao.save(LogAction.of(operateur, "SUPPRESSION_POSTE",
            "Poste supprimé : " + p.getIntitule()));
        posteDao.delete(id);
        return null;
    }

    // ══════════════════════════════════════════════════
    // FICHES DE POSTE
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public FichePoste getFicheById(Long ficheId) {
        return fichePosteDao.findById(ficheId);
    }

    public void creerFichePoste(FichePoste fiche, String operateur) {
        fiche.setCreePar(operateur);
        fiche.setStatut("BROUILLON");
        fichePosteDao.save(fiche);
        logActionDao.save(LogAction.of(operateur, "CREATION_FICHE_POSTE",
            "Fiche créée pour poste : "
            + (fiche.getPoste() != null ? fiche.getPoste().getIntitule() : "")));
    }

    public void modifierFichePoste(FichePoste fiche, String operateur) {
        fichePosteDao.update(fiche);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_FICHE_POSTE",
            "Fiche modifiée : id=" + fiche.getId()));
    }

    public void validerFichePoste(Long ficheId, String operateur) {
        FichePoste fiche = fichePosteDao.findById(ficheId);
        if (fiche != null) {
            fiche.setStatut("VALIDEE");
            fiche.setDateValidation(java.time.LocalDate.now());
            fiche.setValidePar(operateur);
            fichePosteDao.update(fiche);
            logActionDao.save(LogAction.of(operateur, "VALIDATION_FICHE_POSTE",
                "Fiche validée : id=" + ficheId));
        }
    }
}