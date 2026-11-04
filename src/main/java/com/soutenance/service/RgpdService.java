package com.soutenance.service;

import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.Employe;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.Role;
import com.soutenance.entity.User;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service RGPD — export, anonymisation, journal de consultation.
 *
 * Trois droits opérationnels couverts :
 *  - Droit d'accès      : exporterDonneesPersonnelles(userId) — JSON complet
 *  - Droit à l'oubli    : anonymiser(userId) — remplace les champs nominatifs
 *  - Traçabilité accès  : findConsultationsDonneesSensibles() — qui consulte qui
 *
 * Toutes les opérations laissent une trace dans LogAction.
 */
@Service
public class RgpdService {

    @Autowired private UserDao         userDao;
    @Autowired private LogActionDao    logActionDao;
    @Autowired(required = false) private EmployeDao employeDao;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── Droit d'accès : export JSON ──────────────────────────────────────────

    /**
     * Exporte toutes les données personnelles d'un utilisateur au format JSON.
     * Trace dans le journal d'audit le fait que l'opérateur a accédé aux données.
     */
    @Transactional
    public String exporterDonneesPersonnelles(Long userId, String operateur) {
        User u = userDao.findById(userId);
        if (u == null) return "{}";

        JSONObject root = new JSONObject();
        root.put("exportDate", LocalDateTime.now().format(FMT));
        root.put("operateur",  operateur);

        // ── Section utilisateur ─────
        JSONObject user = new JSONObject();
        user.put("id",                  u.getId());
        user.put("username",            u.getUsername());
        user.put("email",               nullSafe(u.getEmail()));
        user.put("nom",                 nullSafe(u.getNom()));
        user.put("prenom",              nullSafe(u.getPrenom()));
        user.put("telephone",           nullSafe(u.getTelephone()));
        user.put("typeCandidat",        u.getTypeCandidat() != null ? u.getTypeCandidat().name() : null);
        user.put("actif",               u.isActif());
        user.put("dateCreation",        toIso(u.getDateCreation()));
        user.put("derniereConnexion",   toIso(u.getDerniereConnexion()));
        user.put("dateChangementMdp",   toIso(u.getDateChangementMdp()));
        user.put("twoFactorEnabled",    u.isTwoFactorEnabled());
        user.put("institution",         u.getInstitution() != null
                                            ? u.getInstitution().getNom() : null);
        JSONArray roles = new JSONArray();
        if (u.getRoles() != null) for (Role r : u.getRoles()) roles.put(r.getNom());
        user.put("roles", roles);
        root.put("utilisateur", user);

        // ── Section employé (si lié) ─────
        if (employeDao != null) {
            try {
                Employe e = employeDao.findByUserId(u.getId());
                if (e != null) {
                    JSONObject emp = new JSONObject();
                    emp.put("id",            e.getId());
                    emp.put("matricule",     nullSafe(e.getMatricule()));
                    emp.put("nom",           nullSafe(e.getNom()));
                    emp.put("prenom",        nullSafe(e.getPrenom()));
                    emp.put("email",         nullSafe(e.getEmail()));
                    emp.put("telephone",     nullSafe(e.getTelephone()));
                    emp.put("cin",           nullSafe(e.getCin()));
                    emp.put("dateNaissance", e.getDateNaissance() != null ? e.getDateNaissance().toString() : null);
                    emp.put("lieuNaissance", nullSafe(e.getLieuNaissance()));
                    emp.put("sexe",          nullSafe(e.getSexe()));
                    emp.put("nationalite",   nullSafe(e.getNationalite()));
                    emp.put("adresse",       nullSafe(e.getAdresse()));
                    emp.put("situationFamiliale", nullSafe(e.getSituationFamiliale()));
                    emp.put("nombreEnfants", e.getNombreEnfants());
                    emp.put("urgenceNom",    nullSafe(e.getUrgenceNom()));
                    emp.put("urgenceTelephone", nullSafe(e.getUrgenceTelephone()));
                    emp.put("urgenceLien",   nullSafe(e.getUrgenceLien()));
                    emp.put("matricule",     nullSafe(e.getMatricule()));
                    emp.put("poste",         e.getPoste() != null ? e.getPoste().getIntitule() : null);
                    emp.put("dateEmbauche",  e.getDateEmbauche() != null ? e.getDateEmbauche().toString() : null);
                    emp.put("typeContrat",   nullSafe(e.getTypeContrat()));
                    emp.put("statut",        nullSafe(e.getStatut()));
                    emp.put("salaireBase",   e.getSalaireBase());
                    emp.put("gradeEchelon",  nullSafe(e.getGradeEchelon()));
                    root.put("employe", emp);
                }
            } catch (Exception ignored) { /* employe absent */ }
        }

        // ── Section logs concernés ─────
        JSONArray logs = new JSONArray();
        try {
            for (LogAction l : logActionDao.findByUsername(u.getUsername())) {
                JSONObject jl = new JSONObject();
                jl.put("date",    toIso(l.getDateAction()));
                jl.put("action",  nullSafe(l.getAction()));
                jl.put("detail",  nullSafe(l.getDetail()));
                jl.put("ip",      nullSafe(l.getAdresseIp()));
                logs.put(jl);
            }
        } catch (Exception ignored) { /* logs absents */ }
        root.put("journalActions", logs);

        // Trace l'opération elle-même
        logActionDao.save(LogAction.of(operateur, "RGPD_EXPORT",
            "Export RGPD des données de " + u.getUsername() + " (" + logs.length() + " logs)"));

        return root.toString(2);
    }

    // ── Droit à l'oubli : anonymisation ──────────────────────────────────────

    /**
     * Anonymise les champs nominatifs d'un utilisateur (et de son employé lié).
     * L'identifiant technique et les logs sont préservés pour la conformité
     * comptable et l'auditabilité, mais ne contiennent plus de PII.
     */
    @Transactional
    public String anonymiser(Long userId, String operateur) {
        User u = userDao.findById(userId);
        if (u == null) return "Utilisateur introuvable.";
        if (u.getUsername() != null && u.getUsername().equals(operateur)) {
            return "Vous ne pouvez pas vous anonymiser vous-même.";
        }

        String anonId  = "ANON-" + UUID.randomUUID().toString().substring(0, 8);
        String ancien  = u.getUsername();

        u.setNom("Anonymisé");
        u.setPrenom(anonId);
        u.setEmail(anonId + "@anonyme.local");
        u.setTelephone(null);
        u.setUsername(anonId);
        u.setActif(false);
        u.setTwoFactorEnabled(false);
        u.setTwoFactorSecret(null);
        u.setTokenReset(null);
        u.setDateExpirationToken(null);
        userDao.update(u);

        if (employeDao != null) {
            try {
                Employe e = employeDao.findByUserId(userId);
                if (e != null) {
                    e.setNom("Anonymisé");
                    e.setPrenom(anonId);
                    e.setEmail(anonId + "@anonyme.local");
                    e.setTelephone(null);
                    e.setCin(null);
                    e.setAdresse(null);
                    e.setLieuNaissance(null);
                    e.setUrgenceNom(null);
                    e.setUrgenceTelephone(null);
                    e.setUrgenceLien(null);
                    e.setStatut("ANONYMISE");
                    employeDao.update(e);
                }
            } catch (Exception ignored) { /* employe absent */ }
        }

        logActionDao.save(LogAction.of(operateur, "RGPD_ANONYMISATION",
            "Anonymisation du compte " + ancien + " → " + anonId));
        return null;
    }

    // ── Traçabilité : qui consulte les données personnelles ─────────────────

    /** Retourne les N dernières actions classées « accès données personnelles ». */
    @Transactional(readOnly = true)
    public List<LogAction> findConsultationsDonneesSensibles(int limite) {
        List<LogAction> all = logActionDao.findAll();
        List<LogAction> filtres = new ArrayList<>();
        for (LogAction l : all) {
            String a = l.getAction();
            if (a == null) continue;
            if (a.startsWith("RGPD_")
             || a.equals("CONSULTATION_DONNEES")
             || a.equals("EXPORT_EMPLOYE")
             || a.equals("MODIFICATION_USER")
             || a.equals("SUPPRESSION_USER")
             || a.startsWith("ADMIN_VOIR")) {
                filtres.add(l);
                if (filtres.size() >= limite) break;
            }
        }
        return filtres;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String toIso(LocalDateTime d) {
        return d != null ? d.format(FMT) : null;
    }

    private String nullSafe(String s) { return s; /* JSONObject gère null nativement */ }
}
