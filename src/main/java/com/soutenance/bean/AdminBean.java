package com.soutenance.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soutenance.entity.LogAction;
import com.soutenance.entity.Permission;
import com.soutenance.entity.Role;
import com.soutenance.entity.User;
import com.soutenance.entity.Institution;
import com.soutenance.service.AdminService;
import com.soutenance.service.RHService;
import com.soutenance.service.InstitutionService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean JSF — Administration des utilisateurs, rôles et journal d'audit.
 *
 * CORRECTION : ajout des getters manquants appelés par le dashboard XHTML :
 *   - getCongesEnAttente()       → #{adminBean.congesEnAttente}
 *   - getCandidaturesEnAttente() → #{adminBean.candidaturesEnAttente}
 *   - getTotalEmployes()         → #{adminBean.totalEmployes}
 *   - getOffresActives()         → #{adminBean.offresActives}
 *
 * Ces propriétés sont calculées via RHService (déjà disponible dans le contexte).
 * InstitutionService est injecté pour remplir totalInstitutions correctement.
 *
 * Toutes les valeurs sont protégées par try/catch pour éviter tout crash
 * si un service retourne une exception au chargement du dashboard.
 */
@Component("adminBean")
@Scope("session")
public class AdminBean implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBean.class);

    private static final long serialVersionUID = 1L;

    @Autowired private AdminService      adminService;
    @Autowired private SessionBean       sessionBean;

    // CORRECTION : injection des services pour les stats du dashboard
    @Autowired(required = false) private RHService          rhService;
    @Autowired(required = false) private InstitutionService institutionService;
    @Autowired(required = false) private com.soutenance.dao.DemandeCongeDao  demandeCongeDao;
    @Autowired(required = false) private com.soutenance.dao.CandidatureDao   candidatureDao;
    @Autowired(required = false) private com.soutenance.dao.OffreEmploiDao   offreEmploiDao;
    @Autowired(required = false) private com.soutenance.dao.PosteDao         posteDao;

    // ── Listes ──────────────────────────────────────────────────────────────
    private List<User>      utilisateurs;
    private List<Role>      roles;
    private List<LogAction> logs;
    private List<LogAction> logsRecents;
    private List<Institution> toutesInstitutions;

    // ── Entité en cours d'édition ────────────────────────────────────────────
    private User   utilisateur      = new User();
    private Role   role             = new Role();
    private Long   roleSelectionne;
    private String nouveauMotDePasse;
    private String motDePasseConfirm;

    // ── UI State ─────────────────────────────────────────────────────────────
    private boolean modeEdition    = false;
    private Long    institutionFiltreId;
    private String  institutionFiltreNom; // nom affiché dans la topbar

    // ── Institution management ─────────────────────────────────────────────────
    private Institution nouvelleInstitution  = new Institution();
    private Institution institutionEnEdition = new Institution();

    private String  motCleRecherche;

    // ── Statistiques dashboard — utilisateurs ────────────────────────────────
    private long totalUtilisateurs;
    private long utilisateursActifs;
    private long utilisateursInactifs;
    private long totalRoles;
    private long totalInstitutions;

    // ── Statistiques dashboard — RH (CORRECTION : champs manquants) ──────────
    private long totalEmployes;
    private long employesActifs;
    private long congesEnAttente;
    private long candidaturesEnAttente;
    private long offresActives;
    // Compteurs par rôle — pour le dashboard
    private long countAdmin;
    private long countRH;
    private long countManager;
    private long countEmploye;
    private long countCandidat;

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("AdminBean init error: " + e.getMessage());
            LOG.error("Erreur inattendue", e);
        }
    }

    private void chargerTout() {
        // ── Données utilisateurs ─────────────────────────────────────────────
        utilisateurs         = adminService.getTousLesUtilisateurs();
        roles                = adminService.getTousLesRoles();
        logs                 = adminService.getTousLesLogs();
        logsRecents          = adminService.getLogsRecents(20);
        totalUtilisateurs    = adminService.getNombreTotal();
        utilisateursActifs   = adminService.getNombreActifs();
        utilisateursInactifs = adminService.getNombreInactifs();
        totalRoles           = adminService.getNombreRoles();

        // ── Institutions ─────────────────────────────────────────────────────
        // CORRECTION : utilise InstitutionService si disponible, sinon 0
        try {
            totalInstitutions = (institutionService != null)
                ? institutionService.countTotal() : 0;
        } catch (Exception e) {
            totalInstitutions = 0;
        }

        // ── Liste complète des institutions pour le filtre ────────────────────
        try {
            toutesInstitutions = (institutionService != null)
                ? institutionService.getToutesLesInstitutions()
                : java.util.Collections.emptyList();
        } catch (Exception e) {
            toutesInstitutions = java.util.Collections.emptyList();
        }

        // ── Stats RH pour le dashboard ───────────────────────────────────────
        // CORRECTION : ces valeurs étaient absentes, causant PropertyNotFoundException
        try {
            totalEmployes  = (rhService != null) ? rhService.countEmployes()      : 0;
            employesActifs = (rhService != null) ? rhService.countEmployesActifs() : 0;
        } catch (Exception e) { totalEmployes = 0; employesActifs = 0; }

        try {
            congesEnAttente = (rhService != null) ? rhService.countCongesEnAttente() : 0;
        } catch (Exception e) { congesEnAttente = 0; }

        try {
            candidaturesEnAttente = (rhService != null) ? rhService.countCandidaturesEnAttente() : 0;
        } catch (Exception e) { candidaturesEnAttente = 0; }

        try {
            offresActives = (rhService != null) ? rhService.countOffresActives() : 0;
        } catch (Exception e) { offresActives = 0; }
    }

    private String getOperateur() {
        return sessionBean.getUtilisateurConnecte() != null
               ? sessionBean.getUtilisateurConnecte().getUsername() : "admin";
    }

    private void addMsg(FacesMessage.Severity sev, String titre, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(sev, titre, detail));
    }

    // ════════════════════════════════════════════════════════════
    // GESTION DES UTILISATEURS
    // ════════════════════════════════════════════════════════════

    public void preparerNouvelUtilisateur() {
        utilisateur       = new User();
        roleSelectionne   = null;
        nouveauMotDePasse = null;
        motDePasseConfirm = null;
        modeEdition       = false;
    }

    public void preparerEditionUtilisateur(User u) {
        this.utilisateur     = adminService.getUserById(u.getId());
        if (this.utilisateur == null) this.utilisateur = u;
        this.roleSelectionne = !utilisateur.getRoles().isEmpty()
            ? utilisateur.getRoles().iterator().next().getId() : null;
        this.nouveauMotDePasse = null;
        this.motDePasseConfirm = null;
        this.modeEdition = true;
    }

    public String sauvegarderUtilisateur() {
        try {
            if (modeEdition) {
                String erreur = adminService.modifierUtilisateur(
                    utilisateur, roleSelectionne, getOperateur());
                if (erreur != null) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
                    return null;
                }
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Utilisateur modifié : " + utilisateur.getUsername());
            } else {
                if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                        "Le mot de passe doit contenir au moins 8 caractères.");
                    return null;
                }
                if (!nouveauMotDePasse.equals(motDePasseConfirm)) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                        "Les mots de passe ne correspondent pas.");
                    return null;
                }
                String erreur = adminService.creerUtilisateur(
                    utilisateur, nouveauMotDePasse, roleSelectionne, getOperateur());
                if (erreur != null) {
                    addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", erreur);
                    return null;
                }
                addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                    "Compte créé : " + utilisateur.getUsername());
            }
            chargerTout();
            utilisateur = new User();
        } catch (Exception ex) {
            LOG.error("Erreur inattendue", ex);
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        return null;
    }

    public void supprimerUtilisateur(Long id) {
        try {
            User cible = adminService.getUserById(id);
            if (cible != null && cible.getUsername().equals(getOperateur())) {
                addMsg(FacesMessage.SEVERITY_WARN, "Interdit",
                    "Vous ne pouvez pas supprimer votre propre compte.");
                return;
            }
            adminService.supprimerUtilisateur(id, getOperateur());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_WARN, "Supprimé", "Compte supprimé.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ── 2FA ──────────────────────────────────────────────────────────────────
    @Autowired(required = false) private com.soutenance.service.TotpService totpService;

    /** État courant de la dialog 2FA (secret + URL otpauth). */
    private String secret2fa;
    private String otpauthUrl;
    private String qrCodeUrl;
    private User   userCible2fa;

    /** Génère un nouveau secret et prépare l'affichage du QR. */
    public void preparer2fa(User u) {
        this.userCible2fa = u;
        if (totpService == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Service TOTP indisponible.");
            return;
        }
        this.secret2fa  = totpService.genererSecret();
        String compte   = u.getEmail() != null ? u.getEmail() : u.getUsername();
        this.otpauthUrl = totpService.construireOtpauthUrl("Soutenance HR", compte, secret2fa);
        this.qrCodeUrl  = totpService.construireQrCodeUrl(otpauthUrl);
    }

    /** Confirme l'activation : enregistre le secret en base. */
    public void confirmer2fa() {
        if (userCible2fa == null || secret2fa == null) return;
        try {
            adminService.activer2fa(userCible2fa.getId(), secret2fa, getOperateur());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "2FA activée",
                "L'authentification à deux facteurs est active pour " + userCible2fa.getUsername());
            secret2fa = null;
            otpauthUrl = null;
            qrCodeUrl = null;
            userCible2fa = null;
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Désactive la 2FA d'un utilisateur. */
    public void desactiver2fa(Long userId) {
        try {
            adminService.desactiver2fa(userId, getOperateur());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "2FA désactivée", "Compte retombé en simple facteur.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public String getSecret2fa()    { return secret2fa; }
    public String getOtpauthUrl()   { return otpauthUrl; }
    public String getQrCodeUrl()    { return qrCodeUrl; }
    public User   getUserCible2fa() { return userCible2fa; }

    /** Débloque manuellement un compte verrouillé (5 tentatives). */
    public void debloquerCompte(Long id) {
        try {
            String err = adminService.debloquerCompte(id, getOperateur());
            if (err != null) {
                addMsg(FacesMessage.SEVERITY_WARN, "Action ignorée", err);
            } else {
                chargerTout();
                addMsg(FacesMessage.SEVERITY_INFO, "Compte débloqué",
                    "Les compteurs d'échecs ont été remis à zéro.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void basculerStatutUtilisateur(Long id) {
        try {
            User cible = adminService.getUserById(id);
            if (cible != null && cible.getUsername().equals(getOperateur())) {
                addMsg(FacesMessage.SEVERITY_WARN, "Interdit",
                    "Vous ne pouvez pas désactiver votre propre compte.");
                return;
            }
            adminService.toggleActifUtilisateur(id, getOperateur());
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Statut modifié",
                "Le statut du compte a été mis à jour.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void reinitialiserMotDePasse(Long userId) {
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Le nouveau mot de passe doit contenir au moins 8 caractères.");
            return;
        }
        if (!nouveauMotDePasse.equals(motDePasseConfirm)) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Les mots de passe ne correspondent pas.");
            return;
        }
        try {
            adminService.reinitialiserMotDePasse(userId, nouveauMotDePasse, getOperateur());
            nouveauMotDePasse = null;
            motDePasseConfirm = null;
            chargerTout();
            addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                "Mot de passe réinitialisé avec succès.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public void preparerResetMotDePasse(User u) {
        this.utilisateur       = u;
        this.nouveauMotDePasse = null;
        this.motDePasseConfirm = null;
    }

    // ════════════════════════════════════════════════════════════
    // RECHERCHE
    // ════════════════════════════════════════════════════════════

    public void rechercherUtilisateurs() {
        utilisateurs = adminService.rechercherUtilisateurs(motCleRecherche);
    }

    public void reinitialiserRecherche() {
        motCleRecherche = null;
        utilisateurs = adminService.getTousLesUtilisateurs();
    }

    // ════════════════════════════════════════════════════════════
    // GESTION DES RÔLES
    // ════════════════════════════════════════════════════════════

    public void preparerNouveauRole() {
        role = new Role();
    }

    public void creerRole() {
        if (role.getNom() == null || role.getNom().isBlank()) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Le nom du rôle est obligatoire.");
            return;
        }
        try {
            role.setNom(role.getNom().toUpperCase().trim());
            adminService.creerRole(role, getOperateur());
            chargerTout();
            role = new Role();
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Rôle créé avec succès.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Charge un rôle existant dans le formulaire d'édition. */
    public void preparerEditionRole(Role r) {
        this.role        = r;
        this.modeEdition = true;
    }

    /** Met à jour un rôle (description et, hors rôles système, le nom). */
    public void modifierRole() {
        if (role == null || role.getId() == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                "Aucun rôle sélectionné.");
            return;
        }
        try {
            adminService.modifierRole(role.getId(),
                role.getNom(), role.getDescription(), getOperateur());
            chargerTout();
            role = new Role();
            modeEdition = false;
            addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Rôle modifié.");
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Supprime un rôle non système et non utilisé. */
    public void supprimerRole(Long roleId) {
        try {
            String err = adminService.supprimerRole(roleId, getOperateur());
            if (err != null) {
                addMsg(FacesMessage.SEVERITY_WARN, "Suppression impossible", err);
            } else {
                chargerTout();
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Rôle supprimé.");
            }
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // GESTION DES PERMISSIONS PAR RÔLE
    // ════════════════════════════════════════════════════════════

    /** Référentiel complet des permissions disponibles. */
    private List<Permission> toutesPermissions = new ArrayList<>();
    /** IDs des permissions cochées pour le rôle en cours d'édition. */
    private List<Long>       permissionsSelectionnees = new ArrayList<>();

    /**
     * Charge le rôle ciblé et précoche ses permissions actuelles
     * pour l'écran de gestion des permissions.
     */
    public void preparerPermissionsRole(Role r) {
        this.role = r;
        try {
            this.toutesPermissions = adminService.getToutesLesPermissions();
            this.permissionsSelectionnees = adminService.getPermissionsDuRole(r.getId())
                .stream().map(Permission::getId).collect(Collectors.toList());
        } catch (Exception ex) {
            this.toutesPermissions = new ArrayList<>();
            this.permissionsSelectionnees = new ArrayList<>();
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    /** Enregistre les permissions cochées pour le rôle courant. */
    public void enregistrerPermissions() {
        if (role == null || role.getId() == null) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", "Aucun rôle sélectionné.");
            return;
        }
        try {
            adminService.mettreAJourPermissions(role.getId(),
                new java.util.HashSet<>(permissionsSelectionnees), getOperateur());
            addMsg(FacesMessage.SEVERITY_INFO, "Succès",
                permissionsSelectionnees.size() + " permission(s) attribuée(s) au rôle " + role.getNom());
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    public List<Permission> getToutesPermissions()       { return toutesPermissions; }
    public List<Long>       getPermissionsSelectionnees(){ return permissionsSelectionnees; }
    public void setPermissionsSelectionnees(List<Long> ids) { this.permissionsSelectionnees = ids; }

    // ════════════════════════════════════════════════════════════
    // AUDIT / LOGS
    // ════════════════════════════════════════════════════════════

    public void actualiserLogs() {
        logs        = adminService.getTousLesLogs();
        logsRecents = adminService.getLogsRecents(20);
        addMsg(FacesMessage.SEVERITY_INFO, "Logs actualisés",
            logs.size() + " entrées trouvées.");
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS UI
    // ════════════════════════════════════════════════════════════

    public String getBadgeStatut(boolean actif) {
        return actif ? "badge-success" : "badge-danger";
    }

    public String getRolePrincipal(User u) {
        if (u == null || u.getRoles().isEmpty()) return "Aucun";
        return u.getRoles().iterator().next().getNom();
    }

    public long getTauxActivite() {
        if (totalUtilisateurs == 0) return 0;
        return utilisateursActifs * 100 / totalUtilisateurs;
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ════════════════════════════════════════════════════════════

    public List<User>      getUtilisateurs()          { return utilisateurs; }
    public List<Role>      getRoles()                 { return roles; }
    public List<LogAction> getLogs()                  { return logs; }
    public List<LogAction> getLogsRecents()           { return logsRecents; }

    public User   getUtilisateur()                    { return utilisateur; }
    public void   setUtilisateur(User u)              { this.utilisateur = u; }
    public Role   getRole()                           { return role; }
    public void   setRole(Role r)                     { this.role = r; }

    public Long   getRoleSelectionne()                { return roleSelectionne; }
    public void   setRoleSelectionne(Long id)         { this.roleSelectionne = id; }

    public String getNouveauMotDePasse()              { return nouveauMotDePasse; }
    public void   setNouveauMotDePasse(String s)      { this.nouveauMotDePasse = s; }
    public String getMotDePasseConfirm()              { return motDePasseConfirm; }
    public void   setMotDePasseConfirm(String s)      { this.motDePasseConfirm = s; }

    public boolean isModeEdition()                    { return modeEdition; }
    public void    setModeEdition(boolean b)          { this.modeEdition = b; }

    public String  getMotCleRecherche()               { return motCleRecherche; }
    public void    setMotCleRecherche(String s)       { this.motCleRecherche = s; }

    public Long    getInstitutionFiltreId()            { return institutionFiltreId; }
    public void    setInstitutionFiltreId(Long id)     { this.institutionFiltreId = id; }

    /**
     * Sélectionne une institution depuis le guard ou le bouton rapide.
     * Appelé via #{adminBean.selectionnerInstitution(inst.id)}.
     * Redirige vers la page courante après sélection.
     */
    public String selectionnerInstitution(Long id) {
        this.institutionFiltreId = id;
        filtrerParInstitution();
        return null; // rester sur la page courante, le guard disparaît
    }

    /**
     * Capte le paramètre ?instId=... sur dashboard.xhtml (lien simple depuis
     * la grille de cartes de l'écran de choix) et sélectionne l'institution.
     * Appelé via <f:event type="preRenderView"> — évite les soucis de postback
     * des commandLink à l'intérieur d'une itération.
     */
    public void onDashboardPreRender() {
        String instIdParam = FacesContext.getCurrentInstance()
            .getExternalContext().getRequestParameterMap().get("instId");
        if (instIdParam != null && !instIdParam.isBlank()) {
            try {
                selectionnerInstitution(Long.parseLong(instIdParam.trim()));
            } catch (NumberFormatException ignored) { /* paramètre invalide, on ignore */ }
        }
    }

    /**
     * Bascule la vue du super-admin sur une institution précise puis
     * recharge le dashboard pour afficher les KPI filtrés.
     * Appelé par le bouton « œil » de la liste des institutions.
     */
    // ════════════════════════════════════════════════════════════
    // DASHBOARD ENRICHI PAR INSTITUTION (KPI + graphiques)
    // ════════════════════════════════════════════════════════════

    /** Total employés actifs de l'institution sélectionnée. */
    public long getEffectifInstitution() {
        if (institutionFiltreId == null || rhService == null) return totalEmployes;
        try {
            return rhService.getEmployesByInstitution(institutionFiltreId).stream()
                .filter(e -> "ACTIF".equalsIgnoreCase(e.getStatut())).count();
        } catch (Exception e) { return 0; }
    }

    /** Nombre de postes vacants de l'institution. */
    public long getPostesVacantsInstitution() {
        if (institutionFiltreId == null || posteDao == null) return 0;
        try { return posteDao.countVacantsByInstitution(institutionFiltreId); }
        catch (Exception e) { return 0; }
    }

    /** Nombre total de postes de l'institution. */
    public long getTotalPostesInstitution() {
        if (institutionFiltreId == null || posteDao == null) return 0;
        try { return posteDao.countByInstitution(institutionFiltreId); }
        catch (Exception e) { return 0; }
    }

    /** Pourcentage de couverture des postes (occupés / total). */
    public int getTauxOccupationPostes() {
        long total = getTotalPostesInstitution();
        if (total == 0) return 0;
        long vacants = getPostesVacantsInstitution();
        return (int) Math.round(100.0 * (total - vacants) / total);
    }

    /** Répartition des employés par direction (top 6) pour bar chart. */
    public java.util.Map<String, Long> getEffectifParDirection() {
        java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>();
        if (institutionFiltreId == null || rhService == null) return result;
        try {
            java.util.Map<String, Long> raw = rhService.getEmployesByInstitution(institutionFiltreId).stream()
                .filter(e -> "ACTIF".equalsIgnoreCase(e.getStatut()))
                .collect(java.util.stream.Collectors.groupingBy(
                    e -> {
                        String dep = e.getDepartement();
                        return (dep != null && !dep.isBlank()) ? dep : "Non rattaché";
                    },
                    java.util.stream.Collectors.counting()));
            raw.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        } catch (Exception ignored) {}
        return result;
    }

    /** Répartition par type de contrat (CDI, CDD, STAGE, etc.) pour pie chart. */
    public java.util.Map<String, Long> getEffectifParTypeContrat() {
        java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>();
        if (institutionFiltreId == null || rhService == null) return result;
        try {
            rhService.getEmployesByInstitution(institutionFiltreId).stream()
                .filter(e -> "ACTIF".equalsIgnoreCase(e.getStatut()))
                .collect(java.util.stream.Collectors.groupingBy(
                    e -> e.getTypeContrat() != null ? e.getTypeContrat() : "Non précisé",
                    java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        } catch (Exception ignored) {}
        return result;
    }

    /** Évolution des embauches sur les 12 derniers mois pour line chart. */
    public java.util.Map<String, Long> getEvolutionEmbauches12Mois() {
        java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter fmt =
            java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");

        // Initialiser les 12 derniers mois à 0
        for (int i = 11; i >= 0; i--) {
            result.put(now.minusMonths(i).format(fmt), 0L);
        }
        if (institutionFiltreId == null || rhService == null) return result;

        try {
            for (com.soutenance.entity.Employe e :
                    rhService.getEmployesByInstitution(institutionFiltreId)) {
                if (e.getDateEmbauche() == null) continue;
                java.time.LocalDate emb = e.getDateEmbauche();
                if (emb.isBefore(now.minusMonths(12))) continue;
                String key = emb.format(fmt);
                result.computeIfPresent(key, (k, v) -> v + 1);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** 5 derniers employés arrivés dans l'institution. */
    public java.util.List<com.soutenance.entity.Employe> getDerniersEmployesInstitution() {
        if (institutionFiltreId == null || rhService == null) return java.util.List.of();
        try {
            return rhService.getEmployesByInstitution(institutionFiltreId).stream()
                .filter(e -> e.getDateEmbauche() != null)
                .sorted(java.util.Comparator.comparing(
                    com.soutenance.entity.Employe::getDateEmbauche).reversed())
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) { return java.util.List.of(); }
    }

    /** Pourcentage de comptes activés / total pour la jauge. */
    public int getTauxActivationComptes() {
        if (utilisateurs == null || utilisateurs.isEmpty()) return 0;
        long actifs = utilisateurs.stream().filter(com.soutenance.entity.User::isActif).count();
        return (int) Math.round(100.0 * actifs / utilisateurs.size());
    }

    // ════════════════════════════════════════════════════════════
    // ÉDITION D'UNE INSTITUTION (depuis le tableau de bord)
    // ════════════════════════════════════════════════════════════

    /**
     * Charge l'institution sélectionnée dans le formulaire d'édition.
     * Appelée par les boutons « Voir » et « Modifier » de la table.
     *
     * Sans cette méthode, le bouton crayon ouvrait la dialog avec un
     * objet Institution vide et les champs apparaissaient non pré-remplis.
     */
    public void preparerEditionInstitution(Institution inst) {
        if (inst == null) {
            this.institutionEnEdition = new Institution();
            return;
        }
        // On charge l'objet « frais » depuis le service pour éviter les
        // problèmes de session Hibernate détachée (LazyInitException)
        try {
            if (institutionService != null) {
                Institution rafraichie = institutionService.getById(inst.getId());
                this.institutionEnEdition = rafraichie != null ? rafraichie : inst;
            } else {
                this.institutionEnEdition = inst;
            }
        } catch (Exception e) {
            this.institutionEnEdition = inst;
        }
    }

    /**
     * Bascule le statut ACTIVE ↔ INACTIVE d'une institution.
     * Appelé par le bouton pause/play de la table des institutions.
     */
    public void basculerStatutInstitution(Institution inst) {
        if (inst == null || inst.getId() == null) return;
        try {
            if (institutionService == null) {
                addMsg(FacesMessage.SEVERITY_ERROR, "Erreur",
                    "Service institutions indisponible.");
                return;
            }
            boolean estActive = "ACTIVE".equalsIgnoreCase(inst.getStatut());
            if (estActive) {
                institutionService.desactiverInstitution(inst.getId(), getOperateur());
                addMsg(FacesMessage.SEVERITY_WARN, "Désactivée",
                    inst.getNom() + " a été désactivée.");
            } else {
                institutionService.activerInstitution(inst.getId(), getOperateur());
                addMsg(FacesMessage.SEVERITY_INFO, "Activée",
                    inst.getNom() + " est désormais active.");
            }
            chargerTout(); // rafraîchir la liste des institutions
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // QR CODE PAR INSTITUTION (URL portail dédiée)
    // ════════════════════════════════════════════════════════════

    /** Institution dont on affiche le QR code dans la dialog. */
    private Institution institutionQr;

    public Institution getInstitutionQr()            { return institutionQr; }
    public void setInstitutionQr(Institution i)      { this.institutionQr = i; }

    /** Prépare l'affichage du QR code pour l'institution donnée. */
    public void preparerQrCode(Institution inst) {
        this.institutionQr = inst;
    }

    /**
     * URL publique du portail pour l'institution sélectionnée.
     * Construit à partir de la requête HTTP en cours : scheme + host + port
     * + context path + code institution. Exemple :
     *   http://srv.local:8080/Soutenance/MFB
     */
    public String getUrlPortailQr() {
        if (institutionQr == null || institutionQr.getCode() == null) return "";
        try {
            jakarta.faces.context.ExternalContext ec =
                jakarta.faces.context.FacesContext.getCurrentInstance().getExternalContext();
            jakarta.servlet.http.HttpServletRequest req =
                (jakarta.servlet.http.HttpServletRequest) ec.getRequest();
            String scheme = req.getScheme();
            String host   = req.getServerName();
            int    port   = req.getServerPort();
            String ctx    = ec.getRequestContextPath();
            boolean defaultPort = ("http".equals(scheme) && port == 80)
                               || ("https".equals(scheme) && port == 443);
            String base = scheme + "://" + host
                        + (defaultPort ? "" : ":" + port)
                        + ctx + "/" + institutionQr.getCode();
            return base;
        } catch (Exception e) {
            return "/" + institutionQr.getCode();
        }
    }

    /** URL de l'image QR code (service quickchart.io, déjà utilisé pour 2FA). */
    public String getQrImageUrl() {
        String url = getUrlPortailQr();
        if (url == null || url.isEmpty()) return "";
        String encoded = java.net.URLEncoder.encode(url,
            java.nio.charset.StandardCharsets.UTF_8);
        return "https://quickchart.io/qr?text=" + encoded + "&size=300&margin=2";
    }

    public String voirDashboardInstitution(Long id) {
        try {
            this.institutionFiltreId = id;
            filtrerParInstitution();
            // Trace l'action dans l'audit (utile en multi-institutions)
            try { adminService.log(getOperateur(), "VUE_INSTITUTION",
                    "Bascule sur l'institution id=" + id); }
            catch (Exception ignored) { /* best effort */ }

            // Récupérer le nom pour le growl
            String nomInst = "—";
            if (toutesInstitutions != null) {
                nomInst = toutesInstitutions.stream()
                    .filter(i -> id.equals(i.getId()))
                    .findFirst()
                    .map(Institution::getNom)
                    .orElse("—");
            }
            addMsg(FacesMessage.SEVERITY_INFO, "Institution sélectionnée",
                "Vue filtrée sur : " + nomInst);
        } catch (Exception ex) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", ex.getMessage());
        }
        // Rechargement complet du dashboard pour que tous les panels se rafraîchissent
        return "/Administrateur/dashboard.xhtml?faces-redirect=true";
    }

    /**
     * Réinitialise le filtre institution (retour à la vue globale).
     * Appelé via #{adminBean.reinitialiserInstitution()}.
     */
    public String reinitialiserInstitution() {
        this.institutionFiltreId = null;
        this.institutionFiltreNom = null;
        utilisateurs = adminService.getTousLesUtilisateurs();
        return null;
    }

    public void filtrerParInstitution() {
        if (institutionFiltreId == null) {
            // Vue globale — tout recharger sans filtre
            chargerTout();
            return;
        }

        // ── Utilisateurs filtrés ──────────────────────────────────────────
        List<User> tousUsers = adminService.getTousLesUtilisateurs();
        utilisateurs = tousUsers.stream()
            .filter(u -> u.getInstitution() != null
                && institutionFiltreId.equals(u.getInstitution().getId()))
            .collect(java.util.stream.Collectors.toList());

        totalUtilisateurs    = utilisateurs.size();
        utilisateursActifs   = utilisateurs.stream().filter(User::isActif).count();
        utilisateursInactifs = utilisateurs.stream().filter(u -> !u.isActif()).count();

        // ── Nom institution sélectionnée ──────────────────────────────────
        if (toutesInstitutions != null) {
            institutionFiltreNom = toutesInstitutions.stream()
                .filter(i -> institutionFiltreId.equals(i.getId()))
                .map(Institution::getNom)
                .findFirst().orElse("");
        }

        // ── KPI RH filtrés par institution ────────────────────────────────
        try {
            if (rhService != null) {
                // Employés
                List<?> emps = rhService.getEmployesByInstitution(institutionFiltreId);
                totalEmployes  = emps.size();
                employesActifs = emps.stream()
                    .filter(e -> "ACTIF".equals(((com.soutenance.entity.Employe) e).getStatut()))
                    .count();

                // Congés en attente pour cette institution
                congesEnAttente = demandeCongeDao != null
                    ? demandeCongeDao.findByStatutAndInstitution("EN_ATTENTE", institutionFiltreId).size()
                    : 0;

                // Candidatures reçues pour les offres de cette institution
                candidaturesEnAttente = candidatureDao != null
                    ? candidatureDao.findByInstitution(institutionFiltreId).stream()
                        .filter(c -> "RECUE".equals(c.getStatut())).count()
                    : 0;

                // Offres actives
                offresActives = offreEmploiDao != null
                    ? offreEmploiDao.findByInstitution(institutionFiltreId).stream()
                        .filter(o -> "PUBLIEE".equals(o.getStatut())).count()
                    : 0;
            }
        } catch (Exception e) {
            System.err.println("filtrerParInstitution KPI error: " + e.getMessage());
        }

        // ── Compteurs par rôle recalculés ─────────────────────────────────
        calculerStatistiquesRoles();
    }

    public void reinitialiserFiltreInstitution() {
        institutionFiltreId = null;
        utilisateurs = adminService.getTousLesUtilisateurs();
    }

    /**
     * Institution réellement active pour l'affichage (sidebar, hiérarchie) :
     * l'institution filtrée par le super-admin si elle existe, sinon
     * celle de l'utilisateur connecté. sessionBean.institutionCourante n'est
     * jamais mis à jour lors d'un changement de filtre, donc c'est ici la
     * seule source fiable une fois qu'un super-admin a basculé de vue.
     */
    public Institution getInstitutionActive() {
        if (institutionFiltreId != null && toutesInstitutions != null) {
            return toutesInstitutions.stream()
                .filter(i -> institutionFiltreId.equals(i.getId()))
                .findFirst()
                .orElse(null);
        }
        return sessionBean.getInstitutionCourante();
    }

    // ── Stats utilisateurs (noms français) ───────────────────────────────────
    public long getTotalUtilisateurs()                { return totalUtilisateurs; }
    public long getUtilisateursActifs()               { return utilisateursActifs; }
    public long getUtilisateursInactifs()             { return utilisateursInactifs; }
    public long getTotalRoles()                       { return totalRoles; }
    public List<Institution> getToutesInstitutions()  { return toutesInstitutions; }
    public long getTotalInstitutions()                { return totalInstitutions; }

    // ── Alias anglais — compatibilité dashboard.xhtml ────────────────────────
    // Le XHTML utilise #{adminBean.totalUsers} au lieu de #{adminBean.totalUtilisateurs}
    // Ces getters délèguent simplement vers les champs français existants.

    /** Alias de getTotalUtilisateurs() → #{adminBean.totalUsers} */
    public long getTotalUsers()          { return totalUtilisateurs; }

    /** Alias de getUtilisateursActifs() → #{adminBean.activeUsers} */
    public long getActiveUsers()         { return utilisateursActifs; }

    /** Alias de getUtilisateursInactifs() → #{adminBean.inactiveUsers} */
    public long getInactiveUsers()       { return utilisateursInactifs; }

    /** Alias de getTotalRoles() → #{adminBean.totalRoles} (même nom, ok) */
    // getTotalRoles() existe déjà

    /** Alias → #{adminBean.totalInstitutions} (même nom, ok) */
    // getTotalInstitutions() existe déjà

    /** Alias de getTotalEmployes() → #{adminBean.totalEmployees} */
    public long getTotalEmployees()      { return totalEmployes; }

    /** Alias de getCongesEnAttente() → #{adminBean.pendingLeaves} */
    public long getPendingLeaves()       { return congesEnAttente; }

    /** Alias de getCandidaturesEnAttente() → #{adminBean.pendingApplications} */
    public long getPendingApplications() { return candidaturesEnAttente; }

    /** Alias de getOffresActives() → #{adminBean.activeJobs} */
    public long getActiveJobs()          { return offresActives; }

    /** Alias → #{adminBean.tauxActivite} */
    // getTauxActivite() existe déjà

    // ── Stats RH (noms français) ──────────────────────────────────────────────
    public long getTotalEmployes()                    { return totalEmployes; }
    public long getCongesEnAttente()                  { return congesEnAttente; }
    public long getCandidaturesEnAttente()            { return candidaturesEnAttente; }
    public long getOffresActives()                    { return offresActives; }

    // ════════════════════════════════════════════════════════════
    // GESTION INSTITUTIONS
    // ════════════════════════════════════════════════════════════

    public void preparerNouvelleInstitution() {
        nouvelleInstitution = new Institution();
    }

    // NOTE : preparerEditionInstitution(Institution) est définie plus haut
    // (version robuste qui recharge l'objet depuis le service).

    public void creerInstitution() {
        try {
            if (institutionService != null) {
                institutionService.creer(nouvelleInstitution, getOperateur());
                nouvelleInstitution = new Institution();
                chargerTout();
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Institution créée.");
            }
        } catch (Exception e) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", e.getMessage());
        }
    }

    public void sauvegarderInstitution() {
        try {
            if (institutionService != null) {
                institutionService.modifier(institutionEnEdition, getOperateur());
                chargerTout();
                addMsg(FacesMessage.SEVERITY_INFO, "Succès", "Institution mise à jour.");
            }
        } catch (Exception e) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", e.getMessage());
        }
    }

    public void toggleActif(com.soutenance.entity.User u) {
        try {
            adminService.toggleActifUtilisateur(u.getId(), getOperateur());
            chargerTout();
        } catch (Exception e) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Erreur", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    // STATS RÔLES — répartition (comptage + pourcentage)
    // ════════════════════════════════════════════════════════════

    private void calculerStatistiquesRoles() {
        countAdmin    = countParRole("ADMIN");
        countRH       = countParRole("RH");
        countManager  = countParRole("MANAGER");
        countEmploye  = countParRole("EMPLOYE");
        countCandidat = countParRole("CANDIDAT");
    }

    private long countParRole(String roleNom) {
        if (utilisateurs == null) return 0;
        return utilisateurs.stream()
            .filter(u -> u.getRoles() != null &&
                u.getRoles().stream().anyMatch(r -> roleNom.equalsIgnoreCase(r.getNom())))
            .count();
    }

    private long pct(long val) {
        if (totalUtilisateurs == 0) return 0;
        return val * 100 / totalUtilisateurs;
    }

    public long getCountAdmin()    { return countParRole("ADMIN"); }
    public long getCountRH()       { return countParRole("RH"); }
    public long getCountManager()  { return countParRole("MANAGER"); }
    public long getCountEmploye()  { return countParRole("EMPLOYE"); }
    public long getCountCandidat() { return countParRole("CANDIDAT"); }

    public long getPctAdmin()    { return pct(getCountAdmin()); }
    public long getPctRH()       { return pct(getCountRH()); }
    public long getPctManager()  { return pct(getCountManager()); }
    public long getPctEmploye()  { return pct(getCountEmploye()); }
    public long getPctCandidat() { return pct(getCountCandidat()); }

    // ════════════════════════════════════════════════════════════
    // STATS INSTITUTIONS
    // ════════════════════════════════════════════════════════════

    public long getInstitutionsActives() {
        if (toutesInstitutions == null) return 0;
        return toutesInstitutions.stream()
            .filter(i -> "ACTIVE".equals(i.getStatut())).count();
    }

    public long getInstitutionsEnConfig() {
        if (toutesInstitutions == null) return 0;
        return toutesInstitutions.stream()
            .filter(i -> "CONFIG".equals(i.getStatut())).count();
    }

    public long countEmployesByInstitution(Long instId) {
        // RHService n'expose pas countEmployesByInstitution —
        // on délègue à InstitutionService si disponible, sinon 0
        try {
            return (institutionService != null && instId != null)
                ? institutionService.countEmployesByInstitution(instId) : 0;
        } catch (Exception e) { return 0; }
    }

    public long countUsersByInstitution(Long instId) {
        if (utilisateurs == null || instId == null) return 0;
        return utilisateurs.stream()
            .filter(u -> u.getInstitution() != null
                && instId.equals(u.getInstitution().getId()))
            .count();
    }

    public long getPctEmployesByInstitution(Long instId) {
        long total = totalEmployes;
        if (total == 0) return 0;
        return countEmployesByInstitution(instId) * 100 / total;
    }

    /** Alias sans préfixe get — requis pour #{adminBean.pctEmployesByInstitution(inst.id)} dans ui:repeat */
    public long pctEmployesByInstitution(Long instId) {
        return getPctEmployesByInstitution(instId);
    }

    /** Alias sans préfixe get — requis pour #{adminBean.countEmployesByInstitution(inst.id)} dans ui:repeat */
    public long countEmployesByInstitution_alias(Long instId) {
        return countEmployesByInstitution(instId);
    }

    public String getInstitutionFiltreNom() {
        // Calculé dynamiquement depuis la liste si le champ cache est vide
        if (institutionFiltreNom != null) return institutionFiltreNom;
        if (institutionFiltreId == null || toutesInstitutions == null) return "";
        return toutesInstitutions.stream()
            .filter(i -> institutionFiltreId.equals(i.getId()))
            .map(Institution::getNom)
            .findFirst().orElse("");
    }
    public void setInstitutionFiltreNom(String s) { this.institutionFiltreNom = s; }

    // ════════════════════════════════════════════════════════════
    // ALIAS DASHBOARD (noms utilisés dans le XHTML)
    // ════════════════════════════════════════════════════════════

    /** #{adminBean.usersActifs} */
    public long getUsersActifs()    { return utilisateursActifs; }

    /** #{adminBean.usersInactifs} */
    public long getUsersInactifs()  { return utilisateursInactifs; }

    /**
     * #{adminBean.employesActifs}
     * Retourne le champ mis à jour par chargerTout() ou filtrerParInstitution()
     * selon le contexte (global ou filtré par institution).
     * NE PAS appeler rhService.countEmployesActifs() ici : ce serait un comptage
     * global ignorant le filtre institution, causant l'affichage de 110 actifs
     * même quand une institution filtrée a 0 employé.
     */
    public long getEmployesActifs() { return employesActifs; }

    /** #{adminBean.totalActionsEnAttente} */
    public long getTotalActionsEnAttente() {
        return congesEnAttente + candidaturesEnAttente;
    }

    // ════════════════════════════════════════════════════════════
    // GETTERS INSTITUTION MANAGEMENT
    // ════════════════════════════════════════════════════════════

    public Institution getNouvelleInstitution()   { return nouvelleInstitution; }
    public void setNouvelleInstitution(Institution i) { this.nouvelleInstitution = i; }

    public Institution getInstitutionEnEdition()  { return institutionEnEdition; }
    public void setInstitutionEnEdition(Institution i) { this.institutionEnEdition = i; }

    // ════════════════════════════════════════════════════════════
    // DONNÉES GRAPHIQUES — JSON SÉCURISÉ (évite l'injection dans ui:repeat)
    // ════════════════════════════════════════════════════════════

    /**
     * Retourne les données des trois graphiques du dashboard sous forme de JSON
     * proprement échappé pour éviter toute injection si un nom de direction
     * contient des guillemets ou des backslashes.
     *
     * Utilisation dans dashboard.xhtml :
     *   <h:outputText value="#{adminBean.chartDataJson}" escape="false"/>
     */
    public String getChartDataJson() {
        java.util.Map<String, Long> effectifDir = getEffectifParDirection();
        java.util.Map<String, Long> typeContrat = getEffectifParTypeContrat();
        java.util.Map<String, Long> embauches   = getEvolutionEmbauches12Mois();

        return "{"
            + "\"effectifDir\":" + mapToJson(effectifDir) + ","
            + "\"typeContrat\":" + mapToJson(typeContrat) + ","
            + "\"embauches\":"   + mapToJson(embauches)
            + "}";
    }

    /** Sérialise une Map&lt;String, Long&gt; en objet JSON {labels:[...], values:[...]}. */
    private String mapToJson(java.util.Map<String, Long> map) {
        if (map == null || map.isEmpty()) {
            return "{\"labels\":[],\"values\":[]}";
        }
        StringBuilder labels = new StringBuilder("[");
        StringBuilder values = new StringBuilder("[");
        boolean first = true;
        for (java.util.Map.Entry<String, Long> entry : map.entrySet()) {
            if (!first) { labels.append(","); values.append(","); }
            labels.append("\"").append(escapeJson(entry.getKey())).append("\"");
            values.append(entry.getValue() != null ? entry.getValue() : 0);
            first = false;
        }
        return "{\"labels\":" + labels + "],\"values\":" + values + "]}";
    }

    /** Échappe les caractères spéciaux JSON dans une chaîne. */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}