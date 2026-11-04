package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de gestion administrative des présences.
 *
 * Couvre :
 *  - Pointage manuel (enregistrement / modification / suppression)
 *  - Validation unitaire et en masse des feuilles de présence
 *  - Gestion des jours fériés par institution
 *  - Récupération des présences par période et par institution
 *
 * Corrections appliquées :
 *  1. p.setValideePar()   → p.setValidePar()       (typo sur le nom du setter)
 *  2. p.setDateValidation() ajouté dans Presence.java (champ manquant)
 *  3. jourFerie.setInstitutionId() → setInstitution() via InstitutionDao
 *  4. employeDao.findActifsByInstitution() ajouté dans EmployeDao
 */
@Service
@Transactional
public class AdminPresenceService {

    @Autowired private PresenceDao      presenceDao;
    @Autowired private JourFerieDao     jourFerieDao;
    @Autowired private EmployeDao       employeDao;
    @Autowired private LogActionDao     logActionDao;
    // CORRECTION 3 : injection de InstitutionDao pour charger l'objet Institution
    @Autowired private InstitutionDao   institutionDao;

    // ══════════════════════════════════════════════════
    // PRÉSENCES
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Presence> getPresencesParPeriode(Long institutionId, LocalDate debut, LocalDate fin) {
        return presenceDao.findByPeriodeAndInstitution(institutionId, debut, fin);
    }

    @Transactional(readOnly = true)
    public Presence getPresenceById(Long id) {
        return presenceDao.findById(id);
    }

    /**
     * Enregistre une nouvelle présence.
     *
     * @return null si succès, message d'erreur sinon
     */
    public String enregistrerPresence(Presence presence, String operateur) {
        if (presence.getEmploye() == null) return "Employé obligatoire.";
        if (presence.getDatePresence() == null) return "Date obligatoire.";
        presence.setSourceSaisie("MANUEL");
        presenceDao.save(presence);
        logActionDao.save(LogAction.of(operateur, "POINTAGE_MANUEL",
            "Présence enregistrée : " + presence.getEmploye().getNomComplet()
            + " le " + presence.getDatePresence()));
        return null;
    }

    public void modifierPresence(Presence presence, String operateur) {
        presenceDao.update(presence);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_PRESENCE",
            "Présence modifiée : id=" + presence.getId()));
    }

    public void supprimerPresence(Long id, String operateur) {
        Presence p = presenceDao.findById(id);
        if (p != null) {
            logActionDao.save(LogAction.of(operateur, "SUPPRESSION_PRESENCE",
                "Présence supprimée : " + (p.getEmploye() != null ? p.getEmploye().getNomComplet() : id)));
            presenceDao.delete(id);
        }
    }

    public void validerPresence(Long id, String operateur) {
        Presence p = presenceDao.findById(id);
        if (p != null) {
            p.setEstValide(true);
            // CORRECTION 1 : setValideePar → setValidePar (nom exact du setter dans Presence)
            p.setValidePar(operateur);
            // CORRECTION 2 : setDateValidation désormais disponible (champ ajouté dans Presence)
            p.setDateValidation(LocalDate.now());
            presenceDao.update(p);
            logActionDao.save(LogAction.of(operateur, "VALIDATION_PRESENCE",
                "Présence validée : id=" + id));
        }
    }

    /**
     * Valide toutes les présences non encore validées pour une journée donnée.
     *
     * @return nombre de présences validées
     */
    public int validerPresencesJour(Long institutionId, LocalDate date, String operateur) {
        List<Presence> presences = presenceDao.findByDateAndInstitution(institutionId, date);
        int count = 0;
        for (Presence p : presences) {
            if (!p.isEstValide()) {
                p.setEstValide(true);
                // CORRECTION 1 (idem) : setValideePar → setValidePar
                p.setValidePar(operateur);
                // CORRECTION 2 (idem) : setDateValidation disponible
                p.setDateValidation(LocalDate.now());
                presenceDao.update(p);
                count++;
            }
        }
        if (count > 0) {
            logActionDao.save(LogAction.of(operateur, "VALIDATION_PRESENCE_MASSE",
                count + " présence(s) validée(s) pour le " + date));
        }
        return count;
    }

    // ══════════════════════════════════════════════════
    // JOURS FÉRIÉS
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<JourFerie> getJoursFeriesByInstitution(Long institutionId) {
        return jourFerieDao.findByInstitution(institutionId);
    }

    public void creerJourFerie(JourFerie jourFerie, Long institutionId, String operateur) {
        // CORRECTION 3 : JourFerie.institution est un objet @ManyToOne, pas un Long.
        // On charge l'Institution via son DAO avant de l'affecter.
        Institution institution = institutionDao.findById(institutionId);
        jourFerie.setInstitution(institution);
        jourFerieDao.save(jourFerie);
        logActionDao.save(LogAction.of(operateur, "CREATION_JOUR_FERIE",
            "Jour férié créé : " + jourFerie.getLibelle() + " (" + jourFerie.getDate() + ")"));
    }

    public void modifierJourFerie(JourFerie jourFerie, String operateur) {
        jourFerieDao.update(jourFerie);
        logActionDao.save(LogAction.of(operateur, "MODIFICATION_JOUR_FERIE",
            "Jour férié modifié : " + jourFerie.getLibelle()));
    }

    public void supprimerJourFerie(Long id, String operateur) {
        JourFerie jf = jourFerieDao.findById(id);
        if (jf != null) {
            logActionDao.save(LogAction.of(operateur, "SUPPRESSION_JOUR_FERIE",
                "Jour férié supprimé : " + jf.getLibelle()));
            jourFerieDao.delete(id);
        }
    }

    // ══════════════════════════════════════════════════
    // EMPLOYÉS ACTIFS (pour sélection pointage)
    // ══════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Employe> getEmployesActifs(Long institutionId) {
        // CORRECTION 4 : méthode findActifsByInstitution ajoutée dans EmployeDao
        return employeDao.findActifsByInstitution(institutionId);
    }
}