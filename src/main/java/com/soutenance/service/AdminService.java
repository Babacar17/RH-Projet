package com.soutenance.service;

import com.soutenance.dao.EmployeDao;
import com.soutenance.dao.LogActionDao;
import com.soutenance.dao.PermissionDao;
import com.soutenance.dao.RoleDao;
import com.soutenance.dao.UserDao;
import com.soutenance.entity.LogAction;
import com.soutenance.entity.Permission;
import com.soutenance.entity.Role;
import com.soutenance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service d'administration — utilisateurs, rôles, logs.
 *
 * CORRECTIONS APPLIQUÉES :
 *
 * 1. BCryptPasswordEncoder instancié manuellement → injecté via Spring.
 *    Même encodeur partagé que UserService et CandidatService.
 *    Prérequis : @Bean BCryptPasswordEncoder déclaré dans SpringJsfConfig.
 *
 * 2. reinitialiserMotDePasse() déverrouille maintenant le compte.
 *    Quand l'admin réinitialise le mot de passe d'un utilisateur bloqué,
 *    les compteurs de tentatives sont remis à zéro automatiquement.
 *    Sans cette correction, un compte bloqué restait bloqué même après
 *    une réinitialisation du mot de passe.
 *
 * 3. toggleActifUtilisateur() déverrouille le compte lors de la réactivation.
 *    Si un admin réactive un compte bloqué, les compteurs sont remis à zéro.
 */
@Service
public class AdminService {

    @Autowired private UserDao       userDao;
    @Autowired private RoleDao       roleDao;
    @Autowired private LogActionDao  logDao;
    @Autowired(required = false) private PermissionDao permissionDao;
    @Autowired(required = false) private EmailService  emailService;
    @Autowired(required = false) private EmployeDao    employeDao;

    /** Rôles purement "système", sans fiche employé — tous les autres rôles représentent un membre du personnel. */
    private static final Set<String> ROLES_SANS_FICHE_EMPLOYE = Set.of("SUPER_ADMIN", "ADMIN", "CANDIDAT");

    // CORRECTION 1 : injecté par Spring — plus de new BCryptPasswordEncoder()
    @Autowired private BCryptPasswordEncoder encoder;

    // ── Initialisation ────────────────────────────────────────────────────────

    @PostConstruct
    @Transactional
    public void initialiser() {
        roleDao.initRolesParDefaut();
        if (permissionDao != null) permissionDao.initPermissionsParDefaut();
        creerAdminParDefautSiAbsent();
    }

    private void creerAdminParDefautSiAbsent() {
        if (userDao.findByUsername("admin") == null) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("Admin@123"));
            admin.setEmail("admin@institution.sn");
            admin.setNom("Administrateur");
            admin.setPrenom("Système");
            admin.setActif(true);

            Role roleAdmin = roleDao.findByNom("ADMIN");
            if (roleAdmin != null) {
                Set<Role> roles = new HashSet<>();
                roles.add(roleAdmin);
                admin.setRoles(roles);
            }
            userDao.save(admin);
            System.out.println("Compte admin créé : admin / Admin@123");
        }
    }

    // ── Gestion des utilisateurs ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<User> getTousLesUtilisateurs() {
        return userDao.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> rechercherUtilisateurs(String motCle) {
        if (motCle == null || motCle.trim().isEmpty()) return userDao.findAll();
        return userDao.rechercherParNom(motCle.trim());
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userDao.findById(id);
    }

    @Transactional
    public String creerUtilisateur(User user, String motDePasse, Long roleId, String adminUsername) {
        if (userDao.findByUsername(user.getUsername()) != null) {
            return "Ce nom d'utilisateur existe déjà.";
        }
        // CORRECTION 1 : encoder injecté
        user.setPassword(encoder.encode(motDePasse));
        user.setActif(true);

        Role role = null;
        if (roleId != null) {
            role = roleDao.findById(roleId);
            if (role != null) {
                Set<Role> roles = new HashSet<>();
                roles.add(role);
                user.setRoles(roles);
            }
        }
        userDao.save(user);
        log(adminUsername, "CREATION_USER", "Utilisateur créé : " + user.getUsername());

        // Tout rôle représentant un membre du personnel (RH, MANAGER, EMPLOYE, AUDITEUR...)
        // doit avoir une fiche employé — seuls SUPER_ADMIN/ADMIN/CANDIDAT en sont dispensés.
        if (employeDao != null && role != null && !ROLES_SANS_FICHE_EMPLOYE.contains(role.getNom())
                && user.getInstitution() != null) {
            try {
                creerFicheEmployeAuto(user, role);
            } catch (Exception ignored) { /* best effort — ne doit pas bloquer la création du compte */ }
        }

        // Envoi d'une invitation par email (mode console si SMTP non configuré)
        if (emailService != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                emailService.envoyerInvitation(
                    user.getEmail(),
                    user.getPrenom() != null ? user.getPrenom() : user.getUsername(),
                    user.getUsername(),
                    motDePasse,
                    adminUsername);
            } catch (Exception ignored) { /* l'utilisateur est créé même si l'email échoue */ }
        }
        return null;
    }

    /** Crée une fiche employé minimale pour un compte dont le rôle représente un membre du personnel. */
    private void creerFicheEmployeAuto(User user, Role role) {
        com.soutenance.entity.Employe emp = new com.soutenance.entity.Employe();
        emp.setNom(user.getNom());
        emp.setPrenom(user.getPrenom());
        emp.setEmail(user.getEmail());
        emp.setTelephone(user.getTelephone());
        emp.setInstitution(user.getInstitution());
        emp.setUser(user);
        emp.setStatut("ACTIF");
        emp.setTypeContrat("CDI");
        emp.setDateEmbauche(java.time.LocalDate.now());
        emp.setLibrePoste("RH".equals(role.getNom()) ? "Responsable Ressources Humaines" : "À définir");
        long n = employeDao.countTotal() + 1;
        emp.setMatricule("MAT-" + role.getNom() + "-" + String.format("%03d", n));
        employeDao.save(emp);
    }

    @Transactional
    public String modifierUtilisateur(User user, Long roleId, String adminUsername) {
        User existant = userDao.findById(user.getId());
        if (existant == null) return "Utilisateur introuvable.";

        existant.setNom(user.getNom());
        existant.setPrenom(user.getPrenom());
        existant.setEmail(user.getEmail());
        existant.setTelephone(user.getTelephone());

        if (roleId != null) {
            Role role = roleDao.findById(roleId);
            if (role != null) {
                Set<Role> roles = new HashSet<>();
                roles.add(role);
                existant.setRoles(roles);
            }
        }
        userDao.update(existant);
        log(adminUsername, "MODIFICATION_USER", "Utilisateur modifié : " + existant.getUsername());
        return null;
    }

    /**
     * Réinitialise le mot de passe ET déverrouille le compte si bloqué.
     *
     * CORRECTION 2 : reinitialiserEchecs() remet tentativesConnexion=0
     * et compteBloque=false. Sans cela, un compte bloqué après 5 tentatives
     * restait inaccessible même après un reset de mot de passe.
     */
    @Transactional
    public void reinitialiserMotDePasse(Long userId, String nouveauMdp, String adminUsername) {
        User user = userDao.findById(userId);
        if (user != null) {
            // CORRECTION 1 : encoder injecté
            user.setPassword(encoder.encode(nouveauMdp));
            // CORRECTION 2 : déverrouillage automatique
            if (user.isCompteBloque()) {
                user.reinitialiserEchecs();
                log(adminUsername, "DEVERROUILLAGE_USER",
                    "Compte déverrouillé lors du reset MDP : " + user.getUsername());
            }
            userDao.update(user);
            log(adminUsername, "RESET_MDP",
                "Mot de passe réinitialisé pour : " + user.getUsername());
        }
    }

    /**
     * Bascule actif/inactif.
     *
     * CORRECTION 3 : lors de la réactivation d'un compte bloqué,
     * les compteurs de tentatives sont remis à zéro.
     */
    @Transactional
    public void toggleActifUtilisateur(Long userId, String adminUsername) {
        User user = userDao.findById(userId);
        if (user != null) {
            boolean ancienStatut = user.isActif();
            userDao.toggleActif(userId);

            // CORRECTION 3 : réactivation → déverrouiller si bloqué
            if (!ancienStatut && user.isCompteBloque()) {
                User frais = userDao.findById(userId);
                if (frais != null) {
                    frais.reinitialiserEchecs();
                    userDao.update(frais);
                }
            }

            String action = ancienStatut ? "DESACTIVATION_USER" : "ACTIVATION_USER";
            log(adminUsername, action, "Statut modifié pour : " + user.getUsername());
        }
    }

    @Transactional
    public void supprimerUtilisateur(Long userId, String adminUsername) {
        User user = userDao.findById(userId);
        if (user != null) {
            String username = user.getUsername();
            userDao.delete(userId);
            log(adminUsername, "SUPPRESSION_USER", "Utilisateur supprimé : " + username);
        }
    }

    // ── Gestion des rôles ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Role> getTousLesRoles() {
        return roleDao.findAll();
    }

    @Transactional
    public void creerRole(Role role, String adminUsername) {
        roleDao.save(role);
        log(adminUsername, "CREATION_ROLE", "Rôle créé : " + role.getNom());
    }

    /**
     * Modifie la description (et optionnellement le nom) d'un rôle existant.
     * Les rôles système (ADMIN, RH, MANAGER, EMPLOYE, AUDITEUR, CANDIDAT)
     * voient leur nom protégé en écriture pour préserver les contrôles d'accès.
     */
    @Transactional
    public void modifierRole(Long roleId, String nouveauNom, String description,
                             String adminUsername) {
        Role role = roleDao.findById(roleId);
        if (role == null) return;
        String ancienNom = role.getNom();
        if (!estRoleSysteme(ancienNom) && nouveauNom != null && !nouveauNom.isBlank()) {
            role.setNom(nouveauNom.trim().toUpperCase());
        }
        role.setDescription(description);
        roleDao.update(role);
        log(adminUsername, "MODIFICATION_ROLE",
            "Rôle modifié : " + ancienNom + (role.getNom().equals(ancienNom)
                ? "" : " → " + role.getNom()));
    }

    /**
     * Supprime un rôle s'il n'est utilisé par aucun utilisateur.
     * Les rôles système ne peuvent pas être supprimés.
     *
     * @return message d'erreur, ou null si la suppression a réussi.
     */
    @Transactional
    public String supprimerRole(Long roleId, String adminUsername) {
        Role role = roleDao.findById(roleId);
        if (role == null) return "Rôle introuvable.";
        if (estRoleSysteme(role.getNom())) {
            return "Le rôle « " + role.getNom() + " » est un rôle système et ne peut pas être supprimé.";
        }
        int nbUsers = roleDao.delete(roleId);
        if (nbUsers > 0) {
            return nbUsers + " utilisateur(s) sont encore liés à ce rôle. Réaffectez-les avant la suppression.";
        }
        if (nbUsers < 0) {
            return "Erreur technique lors de la suppression.";
        }
        log(adminUsername, "SUPPRESSION_ROLE", "Rôle supprimé : " + role.getNom());
        return null;
    }

    private boolean estRoleSysteme(String nom) {
        if (nom == null) return false;
        return switch (nom.toUpperCase()) {
            case "ADMIN", "RH", "MANAGER", "EMPLOYE", "AUDITEUR", "CANDIDAT" -> true;
            default -> false;
        };
    }

    // ── Gestion de la 2FA ─────────────────────────────────────────────────────

    /**
     * Active la 2FA pour un utilisateur et retourne le secret généré
     * (à afficher une seule fois en QR code).
     */
    @Transactional
    public String activer2fa(Long userId, String secretBase32, String adminUsername) {
        User user = userDao.findById(userId);
        if (user == null) return null;
        user.setTwoFactorSecret(secretBase32);
        user.setTwoFactorEnabled(true);
        userDao.update(user);
        log(adminUsername, "2FA_ACTIVATION",
            "2FA activée pour " + user.getUsername());
        return secretBase32;
    }

    /** Désactive la 2FA et efface le secret. */
    @Transactional
    public void desactiver2fa(Long userId, String adminUsername) {
        User user = userDao.findById(userId);
        if (user == null) return;
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userDao.update(user);
        log(adminUsername, "2FA_DESACTIVATION",
            "2FA désactivée pour " + user.getUsername());
    }

    /**
     * Débloque manuellement un compte utilisateur sans changer son mot de passe.
     * Remet tentativesConnexion à 0, compteBloque à false et efface la date de
     * verrouillage. Trace l'action dans le journal d'audit.
     */
    @Transactional
    public String debloquerCompte(Long userId, String adminUsername) {
        User user = userDao.findById(userId);
        if (user == null) return "Utilisateur introuvable.";
        if (!user.isCompteBloque() && user.getTentativesConnexion() == 0) {
            return "Le compte n'est pas bloqué.";
        }
        user.reinitialiserEchecs();
        userDao.update(user);
        log(adminUsername, "DEBLOCAGE_COMPTE",
            "Compte " + user.getUsername() + " débloqué manuellement");
        return null;
    }

    // ── Gestion des permissions par rôle ──────────────────────────────────────

    /** Toutes les permissions disponibles, triées par module puis action. */
    @Transactional(readOnly = true)
    public List<Permission> getToutesLesPermissions() {
        return permissionDao != null ? permissionDao.findAll() : List.of();
    }

    /** Permissions actuellement attribuées à un rôle. */
    @Transactional(readOnly = true)
    public List<Permission> getPermissionsDuRole(Long roleId) {
        return permissionDao != null ? permissionDao.findByRole(roleId) : List.of();
    }

    /**
     * Remplace l'ensemble des permissions d'un rôle par la liste fournie.
     * Approche idempotente : la transaction reset le set complet puis le
     * recompose à partir des ids passés.
     */
    @Transactional
    public void mettreAJourPermissions(Long roleId, Set<Long> permissionIds,
                                       String adminUsername) {
        Role role = roleDao.findById(roleId);
        if (role == null) return;

        Set<Permission> nouvelles = new HashSet<>();
        if (permissionIds != null && !permissionIds.isEmpty() && permissionDao != null) {
            for (Long pid : permissionIds) {
                Permission p = permissionDao.findById(pid);
                if (p != null) nouvelles.add(p);
            }
        }
        // Comparaison avant/après pour la traçabilité
        Set<String> codesAvant = new HashSet<>();
        if (role.getPermissions() != null) {
            for (Permission p : role.getPermissions()) codesAvant.add(p.getCode());
        }
        Set<String> codesApres = new HashSet<>();
        for (Permission p : nouvelles) codesApres.add(p.getCode());

        role.setPermissions(nouvelles);
        roleDao.update(role);

        log(adminUsername, "PERMISSIONS_ROLE",
            "Permissions modifiées pour le rôle " + role.getNom()
                + " : " + codesAvant.size() + " → " + codesApres.size() + " permissions");
    }

    /** Initialise le référentiel de permissions par défaut. */
    @Transactional
    public void initialiserPermissions() {
        if (permissionDao != null) permissionDao.initPermissionsParDefaut();
    }

    // ── Statistiques dashboard ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long getNombreTotal()    { return userDao.countTotal(); }

    @Transactional(readOnly = true)
    public long getNombreActifs()   { return userDao.countActifs(); }

    @Transactional(readOnly = true)
    public long getNombreInactifs() { return userDao.countInactifs(); }

    @Transactional(readOnly = true)
    public long getNombreRoles()    { return roleDao.findAll().size(); }

    // ── Logs ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LogAction> getTousLesLogs()      { return logDao.findAll(); }

    @Transactional(readOnly = true)
    public List<LogAction> getLogsRecents(int n) { return logDao.findRecents(n); }

    @Transactional
    public void log(String username, String action, String detail) {
        String ip = null;
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                HttpServletRequest req =
                    (HttpServletRequest) fc.getExternalContext().getRequest();
                ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        LogAction log = new LogAction(username, action, detail);
        log.setAdresseIp(ip);
        logDao.save(log);
    }
}