package com.soutenance.service;

import com.soutenance.dao.*;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service Manager.
 *
 * CORRECTION : BUG #1 — ordre des paramètres LogAction inversé.
 * Tous les appels new LogAction("CODE", "detail", operateur)
 * remplacés par LogAction.of(operateur, "CODE", "detail").
 */
@Service
@Transactional
public class ManagerService {

    @Autowired private EmployeDao           employeDao;
    @Autowired private DemandeCongeDao      demandeCongeDao;
    @Autowired private EvaluationDao        evaluationDao;
    @Autowired private MouvementCarriereDao mouvementCarriereDao;
    @Autowired private LogActionDao         logActionDao;

    // ══════════════════════════════════════════════════
    // IDENTIFICATION DU MANAGER CONNECTÉ
    // ══════════════════════════════════════════════════

    public Employe getManagerEmploye(String username) {
        Employe e = employeDao.findByUsername(username);
        if (e == null) e = employeDao.findByEmail(username);
        return e;
    }

    // ══════════════════════════════════════════════════
    // ÉQUIPE HIÉRARCHIQUE COMPLÈTE
    // ══════════════════════════════════════════════════

    public List<Employe> getMonEquipe(String managerUsername) {
        Employe manager = getManagerEmploye(managerUsername);
        if (manager == null) return employeDao.findActifs();

        String niveau = manager.getNiveauHierarchique();
        if (niveau == null) return employeDao.findByManager(manager.getId());

        return switch (niveau) {
            case "DIRECTEUR" -> {
                if (manager.getServiceOrg() != null
                        && manager.getServiceOrg().getDivision() != null
                        && manager.getServiceOrg().getDivision().getDirection() != null) {
                    Long dirId = manager.getServiceOrg().getDivision().getDirection().getId();
                    yield employeDao.findByDirection(dirId)
                          .stream()
                          .filter(e -> !e.getId().equals(manager.getId()))
                          .collect(Collectors.toList());
                }
                yield employeDao.findByManager(manager.getId());
            }
            case "CHEF_DIV" -> {
                if (manager.getServiceOrg() != null
                        && manager.getServiceOrg().getDivision() != null) {
                    Long divId = manager.getServiceOrg().getDivision().getId();
                    yield employeDao.findByDivision(divId)
                          .stream()
                          .filter(e -> !e.getId().equals(manager.getId()))
                          .collect(Collectors.toList());
                }
                yield employeDao.findByManager(manager.getId());
            }
            case "CHEF_SERVICE" -> {
                if (manager.getServiceOrg() != null) {
                    Long srvId = manager.getServiceOrg().getId();
                    yield employeDao.findByService(srvId)
                          .stream()
                          .filter(e -> !e.getId().equals(manager.getId()))
                          .collect(Collectors.toList());
                }
                yield employeDao.findByManager(manager.getId());
            }
            case "CHEF_BUREAU" -> {
                if (manager.getBureauOrg() != null) {
                    Long burId = manager.getBureauOrg().getId();
                    yield employeDao.findByBureau(burId)
                          .stream()
                          .filter(e -> !e.getId().equals(manager.getId()))
                          .collect(Collectors.toList());
                }
                yield employeDao.findByManager(manager.getId());
            }
            default -> employeDao.findByManager(manager.getId());
        };
    }

    public List<Employe> getChefsDivision(String managerUsername) {
        return getMonEquipe(managerUsername).stream()
            .filter(e -> "CHEF_DIV".equals(e.getNiveauHierarchique()))
            .collect(Collectors.toList());
    }

    public List<Employe> getChefsService(String managerUsername) {
        return getMonEquipe(managerUsername).stream()
            .filter(e -> "CHEF_SERVICE".equals(e.getNiveauHierarchique()))
            .collect(Collectors.toList());
    }

    public List<Employe> getChefsBureau(String managerUsername) {
        return getMonEquipe(managerUsername).stream()
            .filter(e -> "CHEF_BUREAU".equals(e.getNiveauHierarchique()))
            .collect(Collectors.toList());
    }

    public List<Employe> getEmployesStandards(String managerUsername) {
        return getMonEquipe(managerUsername).stream()
            .filter(e -> "EMPLOYE".equals(e.getNiveauHierarchique()))
            .collect(Collectors.toList());
    }

    public long countEquipe(String managerUsername) {
        return getMonEquipe(managerUsername).size();
    }

    // ══════════════════════════════════════════════════
    // CONGÉS DE L'ÉQUIPE
    // ══════════════════════════════════════════════════

    public List<DemandeConge> getCongesEquipe(String managerUsername) {
        List<Employe> equipe = getMonEquipe(managerUsername);
        if (equipe.isEmpty()) return new ArrayList<>();
        List<Long> ids = equipe.stream().map(Employe::getId).collect(Collectors.toList());
        return demandeCongeDao.findByEmployeIds(ids);
    }

    public List<DemandeConge> getCongesEnAttenteEquipe(String managerUsername) {
        return getCongesEquipe(managerUsername).stream()
            .filter(d -> "EN_ATTENTE".equals(d.getStatut()))
            .collect(Collectors.toList());
    }

    /**
     * Validation NIVEAU 1 par le manager. Si OK, le congé passe en attente RH
     * (et c'est le RH qui finalise via RHService.approuverConge).
     * Si refusé, le workflow s'arrête.
     */
    public void approuverConge(Long id, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d == null) return;
        d.setValidationManager("APPROUVE");
        d.setTraitePar1(operateur);
        d.setDateTraitement1(LocalDateTime.now());
        d.setCommentaireManager("Approuvé par le manager");
        d.setEtapeActuelle(2); // Niveau 2 : en attente du RH
        d.setStatut("EN_ATTENTE_RH");
        demandeCongeDao.update(d);
        logActionDao.save(LogAction.of(operateur,
            "VALIDATION_MANAGER_CONGE",
            "Congé validé niveau 1 : " + d.getEmploye().getNomComplet()
            + " (" + d.getNombreJours() + "j) — en attente RH"));
    }

    public void refuserConge(Long id, String motif, String operateur) {
        DemandeConge d = demandeCongeDao.findById(id);
        if (d == null) return;
        d.setStatut("REFUSE");
        d.setValidationManager("REFUSE");
        d.setCommentaireManager(motif);
        d.setTraitePar1(operateur);
        d.setDateTraitement1(LocalDateTime.now());
        d.setEtapeActuelle(0);
        demandeCongeDao.update(d);
        logActionDao.save(LogAction.of(operateur,
            "REFUS_MANAGER_CONGE",
            "Congé refusé par manager : " + d.getEmploye().getNomComplet()));
    }

    public long countCongesEnAttente(String managerUsername) {
        return getCongesEnAttenteEquipe(managerUsername).size();
    }

    // ══════════════════════════════════════════════════
    // ÉVALUATIONS
    // ══════════════════════════════════════════════════

    public List<Evaluation> getEvaluationsEquipe(String managerUsername) {
        List<Employe> equipe = getMonEquipe(managerUsername);
        if (equipe.isEmpty()) return new ArrayList<>();
        List<Long> ids = equipe.stream().map(Employe::getId).collect(Collectors.toList());
        return evaluationDao.findByEmployeIds(ids);
    }

    public void soumettreEvaluation(Evaluation evaluation, String operateur) {
        evaluation.setStatut("SOUMISE");
        if (evaluation.getId() == null) {
            evaluation.setCreePar(operateur);
            evaluationDao.save(evaluation);
        } else {
            evaluationDao.update(evaluation);
        }
        // FIX BUG #1
        logActionDao.save(LogAction.of(operateur,
            "EVALUATION_MANAGER",
            "Evaluation soumise : " + evaluation.getEmploye().getNomComplet()));
    }

    public void sauvegarderEvaluation(Evaluation evaluation, String operateur) {
        if (evaluation.getId() == null) {
            evaluation.setCreePar(operateur);
            evaluationDao.save(evaluation);
        } else {
            evaluationDao.update(evaluation);
        }
    }

    public long countEvaluationsEnCours(String managerUsername) {
        return getEvaluationsEquipe(managerUsername).stream()
            .filter(e -> "EN_COURS".equals(e.getStatut()))
            .count();
    }

    // ══════════════════════════════════════════════════
    // MUTATIONS / PROMOTIONS
    // ══════════════════════════════════════════════════

    public List<MouvementCarriere> getMouvementsEquipe(String managerUsername) {
        List<Employe> equipe = getMonEquipe(managerUsername);
        if (equipe.isEmpty()) return new ArrayList<>();
        List<Long> ids = equipe.stream().map(Employe::getId).collect(Collectors.toList());
        return mouvementCarriereDao.findByEmployeIds(ids);
    }

    public void soumettreMouvement(MouvementCarriere mouvement, String operateur) {
        mouvement.setCreePar(operateur);
        mouvement.setStatut("EN_ATTENTE");
        mouvementCarriereDao.save(mouvement);
        // FIX BUG #1
        logActionDao.save(LogAction.of(operateur,
            "DEMANDE_MOUVEMENT_MANAGER",
            mouvement.getTypeMouvement() + " demandé : "
            + mouvement.getEmploye().getNomComplet()));
    }

    // ══════════════════════════════════════════════════
    // STATISTIQUES PRÉSENCE/ABSENCE
    // ══════════════════════════════════════════════════

    public long countPresentsAujourdhui(String managerUsername) {
        List<Employe> equipe = getMonEquipe(managerUsername);
        if (equipe.isEmpty()) return 0;
        long absents = countAbsentsAujourdhui(managerUsername);
        return equipe.size() - absents;
    }

    public long countAbsentsAujourdhui(String managerUsername) {
        return getCongesEquipe(managerUsername).stream()
            .filter(d -> "APPROUVE".equals(d.getStatut()))
            .filter(d -> {
                java.time.LocalDate today = java.time.LocalDate.now();
                return d.getDateDebut() != null && d.getDateFin() != null
                    && !today.isBefore(d.getDateDebut())
                    && !today.isAfter(d.getDateFin());
            })
            .count();
    }
}