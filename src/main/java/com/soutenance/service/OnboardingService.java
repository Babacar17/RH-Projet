package com.soutenance.service;

import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.TacheOnboardingDao;
import com.soutenance.entity.Employe;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.TacheOnboarding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service Onboarding — gestion de l'intégration des nouveaux employés.
 *
 * À l'embauche, déclenche automatiquement la création d'une checklist
 * standard de 10 tâches couvrant les 5 domaines clés.
 */
@Service
@Transactional
public class OnboardingService {

    @Autowired private TacheOnboardingDao onboardingDao;
    @Autowired private EmployeDao         employeDao;
    @Autowired private LogActionDao       logActionDao;

    /**
     * Crée la checklist standard de 10 tâches pour un nouvel employé.
     * Idempotent : si l'employé a déjà des tâches, ne fait rien.
     */
    public int creerChecklistStandard(Long employeId, String operateur) {
        Employe emp = employeDao.findById(employeId);
        if (emp == null) return 0;

        List<TacheOnboarding> existantes = onboardingDao.findByEmployeId(employeId);
        if (!existantes.isEmpty()) return 0;

        LocalDate dateEmbauche = emp.getDateEmbauche() != null
            ? emp.getDateEmbauche() : LocalDate.now();

        // Définition standard : libellé / catégorie / responsable / jours après embauche
        String[][] taches = {
            {"Préparation du badge d'accès",            "LOGISTIQUE",    "RH",         "0",  "Badge à imprimer et activer pour l'accès aux locaux."},
            {"Attribution de l'ordinateur portable",     "LOGISTIQUE",    "IT",         "0",  "Préparer le poste de travail (PC, écran, clavier, souris)."},
            {"Création des comptes utilisateurs (SIRH, email, ERP)", "ACCES", "IT",  "1",  "Créer les accès aux applications métier nécessaires au poste."},
            {"Briefing sécurité et confidentialité",     "FORMATION",     "RH",         "1",  "Présentation des règles de sécurité, RGPD et confidentialité."},
            {"Signature du contrat de travail",          "ADMINISTRATIF", "RH",         "1",  "Faire signer le contrat en deux exemplaires originaux."},
            {"Remise du livret d'accueil",               "INTEGRATION",   "RH",         "1",  "Présentation de l'institution, organigramme, valeurs."},
            {"Visite des locaux",                         "INTEGRATION",   "MANAGER",    "2",  "Faire visiter les bureaux, présenter les collègues."},
            {"Point individuel avec le manager direct",  "INTEGRATION",   "MANAGER",    "3",  "Cadrage des missions, objectifs des 3 premiers mois."},
            {"Formation initiale au poste",              "FORMATION",     "SUPERVISEUR","7",  "Présentation des outils et procédures spécifiques au poste."},
            {"Bilan d'intégration à 30 jours",           "INTEGRATION",   "RH",         "30", "Entretien pour vérifier que l'intégration se passe bien."}
        };

        int created = 0;
        int ordre = 1;
        for (String[] t : taches) {
            try {
                TacheOnboarding tache = new TacheOnboarding();
                tache.setEmploye(emp);
                tache.setLibelle(t[0]);
                tache.setCategorie(t[1]);
                tache.setResponsable(t[2]);
                tache.setDateLimite(dateEmbauche.plusDays(Long.parseLong(t[3])));
                tache.setDescription(t[4]);
                tache.setOrdre(ordre++);
                tache.setStatut("A_FAIRE");
                onboardingDao.save(tache);
                created++;
            } catch (Exception ignored) { /* skip tâche en erreur */ }
        }

        logActionDao.save(LogAction.of(operateur, "ONBOARDING_CREATION",
            "Checklist d'intégration créée pour " + emp.getNomComplet()
            + " (" + created + " tâches)"));
        return created;
    }

    public void marquerFait(Long tacheId, String operateur) {
        TacheOnboarding t = onboardingDao.findById(tacheId);
        if (t == null) return;
        t.setStatut("FAIT");
        t.setRealisePar(operateur);
        t.setDateRealisation(LocalDate.now());
        onboardingDao.update(t);
        logActionDao.save(LogAction.of(operateur, "ONBOARDING_FAIT",
            "Tâche « " + t.getLibelle() + " » terminée pour "
            + (t.getEmploye() != null ? t.getEmploye().getNomComplet() : "?")));
    }

    public void marquerBloque(Long tacheId, String commentaire, String operateur) {
        TacheOnboarding t = onboardingDao.findById(tacheId);
        if (t == null) return;
        t.setStatut("BLOQUE");
        t.setCommentaire(commentaire);
        onboardingDao.update(t);
        logActionDao.save(LogAction.of(operateur, "ONBOARDING_BLOQUE",
            "Tâche « " + t.getLibelle() + " » bloquée : " + commentaire));
    }

    @Transactional(readOnly = true)
    public List<TacheOnboarding> getTachesByEmploye(Long employeId) {
        return onboardingDao.findByEmployeId(employeId);
    }

    /** Pourcentage d'avancement de l'onboarding d'un employé (0-100). */
    @Transactional(readOnly = true)
    public int getProgressionPourcent(Long employeId) {
        List<TacheOnboarding> taches = onboardingDao.findByEmployeId(employeId);
        if (taches.isEmpty()) return 0;
        long faites = taches.stream()
            .filter(t -> "FAIT".equalsIgnoreCase(t.getStatut())).count();
        return (int) (100L * faites / taches.size());
    }
}
