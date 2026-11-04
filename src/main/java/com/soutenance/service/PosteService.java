package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service de gestion des postes — périmètre RH / employé.
 *
 * Couvre :
 *  - Référentiel des postes (CRUD simple)
 *  - Fiches de poste (création, modification, validation)
 *  - Organigramme des postes (liste, familles)
 *  - Consultation de l'employé affecté à un poste
 */
@Service
@Transactional
public class PosteService {

    @Autowired private PosteDao        posteDao;
    @Autowired private FichePosteDao   fichePosteDao;
    @Autowired private EmployeDao      employeDao;
    @Autowired private LogActionDao    logActionDao;

    // AJOUT : DAO pour les familles de postes
    @Autowired(required = false)
    private FamillePosteDao famillePosteDao;

    // ══════════════════════════════════════════════════
    // POSTES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Poste> getTousLesPostes() {
        return posteDao.findAll();
    }

    public List<Poste> getPostesByInstitution(Long institutionId) {
        if (institutionId == null) return posteDao.findAll();
        return posteDao.findByInstitution(institutionId);
    }

    @Transactional(readOnly = true)
    public Poste getPosteById(Long id) {
        return posteDao.findById(id);
    }

    public void creerPoste(Poste poste, String operateur) {
        poste.setCreePar(operateur);
        posteDao.save(poste);
        logActionDao.save(LogAction.of(operateur, "CREATION_POSTE",
            "Poste créé : " + poste.getIntitule() + " (" + poste.getCode() + ")"));
    }

    public void modifierPoste(Poste poste, String operateur) {
        posteDao.update(poste);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_POSTE",
            "Poste modifié : " + poste.getIntitule()));
    }

    public void supprimerPoste(Long id, String operateur) {
        Poste p = posteDao.findById(id);
        if (p != null) {
            logActionDao.save(LogAction.of(operateur, "SUPPRESSION_POSTE",
                "Poste supprimé : " + p.getIntitule()));
            posteDao.delete(id);
        }
    }

    // ══════════════════════════════════════════════════
    // FICHES DE POSTE
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<FichePoste> getToutesFichesPoste() {
        return fichePosteDao.findAll();
    }

    @Transactional(readOnly = true)
    public FichePoste getFicheByPoste(Long posteId) {
        return fichePosteDao.findByPoste(posteId);
    }

    public void creerFichePoste(FichePoste fiche, String operateur) {
        fiche.setCreePar(operateur);
        fichePosteDao.save(fiche);
        logActionDao.save(LogAction.of(operateur, "CREATION_FICHE_POSTE",
            "Fiche créée pour : " + (fiche.getPoste() != null ? fiche.getPoste().getIntitule() : "")));
    }

    public void modifierFichePoste(FichePoste fiche, String operateur) {
        fichePosteDao.update(fiche);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_FICHE_POSTE",
            "Fiche modifiée : id=" + fiche.getId()));
    }

    /**
     * CORRECTION :
     *  - fiche.setValide(true)    → champ booléen désormais présent dans FichePoste
     *  - fiche.setDateValidation  → renseigné avec la date du jour
     *  - fiche.setValidePar       → renseigné avec l'opérateur
     */
    public void validerFichePoste(Long ficheId, String operateur) {
        FichePoste fiche = fichePosteDao.findById(ficheId);
        if (fiche != null) {
            fiche.setValide(true);
            fiche.setStatut("VALIDEE");
            fiche.setDateValidation(LocalDate.now());
            fiche.setValidePar(operateur);
            fichePosteDao.update(fiche);
            logActionDao.save(LogAction.of(operateur, "VALIDATION_FICHE_POSTE",
                "Fiche validée : id=" + ficheId));
        }
    }

    // ══════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════

    /**
     * CORRECTION : employeDao.findByPoste(Long) est désormais implémenté dans EmployeDao.
     */
    @Transactional(readOnly = true)
    public Employe getEmployeSurPoste(Long posteId) {
        return employeDao.findByPoste(posteId);
    }

    @Transactional(readOnly = true)
    public List<String> getToutesFamillesPostes() {
        return posteDao.findAll().stream()
            .map(Poste::getFamille)
            .filter(f -> f != null && !f.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════
    // FAMILLES DE POSTES — AJOUT
    // Nouvelles méthodes pour PosteBean.familleForm
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<FamillePoste> getToutesFamilles() {
        if (famillePosteDao == null) return List.of();
        return famillePosteDao.findAll();
    }

    @Transactional
    public void creerFamille(FamillePoste famille, String operateur) {
        if (famillePosteDao == null) return;
        famille.setCreePar(operateur);
        famillePosteDao.save(famille);
        logActionDao.save(LogAction.of(operateur, "CREATION_FAMILLE_POSTE",
            "Famille créée : " + famille.getNom()));
    }

    @Transactional
    public void modifierFamille(FamillePoste famille, String operateur) {
        if (famillePosteDao == null) return;
        famillePosteDao.update(famille);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_FAMILLE_POSTE",
            "Famille modifiée : " + famille.getNom()));
    }
}