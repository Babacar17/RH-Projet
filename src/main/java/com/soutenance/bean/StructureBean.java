package com.soutenance.bean;

import com.soutenance.entity.*;
import com.soutenance.service.StructureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Component("structureBean")
@Scope("view")
public class StructureBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private StructureService structureService;
    @Autowired private SessionBean sessionBean;
    @Autowired(required = false) private com.soutenance.dao.InstitutionDao institutionDao;

    // ── Niveaux hiérarchiques de l'institution courante ───────────────────
    private com.soutenance.entity.Institution institutionCourante;
    private List<String> niveauxHierarchie = new ArrayList<>();

    // ── Listes ────────────────────────────────────────────────
    private List<Direction>  directions  = new ArrayList<>();
    private List<Division>   divisions   = new ArrayList<>();
    private List<com.soutenance.entity.Service> services = new ArrayList<>();
    private List<Bureau>     bureaux     = new ArrayList<>();
    private List<Employe>    employes    = new ArrayList<>();
    private List<Departement> departements = new ArrayList<>();

    // Listes filtrées (pour les selects cascadés)
    private List<Division>   divisionsParDirection = new ArrayList<>();
    private List<com.soutenance.entity.Service> servicesParDivision = new ArrayList<>();

    // ── Objets en cours d'édition ──────────────────────────────
    private Direction  direction  = new Direction();
    private Division   division   = new Division();
    private com.soutenance.entity.Service service = new com.soutenance.entity.Service();
    private Bureau     bureau     = new Bureau();
    private Departement departement = new Departement();

    // ── Mode édition ──────────────────────────────────────────
    private boolean modeEditionDirection  = false;
    private boolean modeEditionDivision   = false;
    private boolean modeEditionService    = false;
    private boolean modeEditionBureau     = false;
    private boolean modeEditionDepartement = false;

    // Structure libre : parent d'un service (une seule des 3 valeurs est utilisée)
    private Long serviceDirectionId;
    private Long serviceDepartementId;
    // Direction parente d'un département + chef du département
    private Long departementDirectionId;
    private Long chefDepartementId;

    // IDs pour les sélects cascadés
    private Long directionSelectId;
    private Long divisionSelectId;
    private Long serviceSelectId;

    // IDs des chefs sélectionnés
    private Long chefDirectionId;
    private Long chefDivisionId;
    private Long chefServiceId;
    private Long chefBureauId;

    @PostConstruct
    public void init() {
        try {
            chargerTout();
        } catch (Exception e) {
            System.err.println("StructureBean init error: " + e.getMessage());
        }
    }

    private Long getInstitutionId() {
        try {
            org.springframework.web.context.WebApplicationContext ctx =
                org.springframework.web.context.support.WebApplicationContextUtils
                    .getWebApplicationContext(
                        (jakarta.servlet.ServletContext)
                        jakarta.faces.context.FacesContext.getCurrentInstance()
                            .getExternalContext().getContext());
            if (ctx != null) {
                com.soutenance.bean.AdminBean ab = ctx.getBean(com.soutenance.bean.AdminBean.class);
                if (ab != null && ab.getInstitutionFiltreId() != null)
                    return ab.getInstitutionFiltreId();
            }
        } catch (Exception ignored) {}
        return sessionBean.getInstitutionId();
    }

    private void chargerTout() {
        Long instId = getInstitutionId();

        // Charger l'institution et ses niveaux hiérarchiques
        if (instId != null && institutionDao != null) {
            institutionCourante = institutionDao.findById(instId);
        } else if (sessionBean.getInstitutionCourante() != null) {
            institutionCourante = sessionBean.getInstitutionCourante();
        }
        niveauxHierarchie = (institutionCourante != null)
            ? institutionCourante.getNiveauxHierarchie()
            : java.util.Arrays.asList("Direction", "Division", "Service", "Bureau");

        directions = structureService.getDirectionsByInstitution(instId);
        divisions  = structureService.getDivisionsByInstitution(instId);
        services   = structureService.getServicesByInstitution(instId);
        bureaux    = structureService.getBureauxByInstitution(instId);
        employes   = structureService.getAllEmployes();
        departements = structureService.getDepartementsByInstitution(instId);
    }

    private String getUsername() {
        try { return sessionBean.getUtilisateurConnecte().getUsername(); }
        catch (Exception e) { return "admin"; }
    }

    // ══════════════════════════════════════════════════════════
    //  DIRECTIONS
    // ══════════════════════════════════════════════════════════

    public void preparerNouvelleDirection() {
        direction = new Direction();
        chefDirectionId = null;
        modeEditionDirection = false;
    }

    public void preparerEditionDirection(Direction d) {
        this.direction = d;
        chefDirectionId = (d.getChef() != null) ? d.getChef().getId() : null;
        modeEditionDirection = true;
    }

    public void sauvegarderDirection() {
        try {
            appliquerChefDirection();
            if (direction.getInstitution() == null) direction.setInstitution(institutionCourante);
            if (modeEditionDirection) {
                structureService.updateDirection(direction);
                addMessage("Direction modifiée avec succès.");
            } else {
                structureService.saveDirection(direction, getUsername());
                addMessage("Direction créée avec succès.");
            }
            chargerTout();
            direction = new Direction();
        } catch (Exception e) {
            addErrorMessage("Erreur : " + e.getMessage());
        }
    }

    public void supprimerDirection(Long id) {
        try {
            structureService.deleteDirection(id);
            chargerTout();
            addMessage("Direction supprimée.");
        } catch (Exception e) {
            addErrorMessage("Impossible de supprimer (des divisions existent peut-être).");
        }
    }

    private void appliquerChefDirection() {
        if (chefDirectionId != null) {
            direction.setChef(trouverEmploye(chefDirectionId));
        } else {
            direction.setChef(null);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  DIVISIONS
    // ══════════════════════════════════════════════════════════

    public void preparerNouvelleDivision() {
        division = new Division();
        chefDivisionId = null;
        directionSelectId = null;
        modeEditionDivision = false;
    }

    public void preparerEditionDivision(Division d) {
        this.division = d;
        chefDivisionId = (d.getChef() != null) ? d.getChef().getId() : null;
        directionSelectId = (d.getDirection() != null) ? d.getDirection().getId() : null;
        modeEditionDivision = true;
    }

    public void sauvegarderDivision() {
        try {
            appliquerChefDivision();
            appliquerDirectionDivision();
            if (division.getInstitution() == null) division.setInstitution(institutionCourante);
            if (modeEditionDivision) {
                structureService.updateDivision(division);
                addMessage("Division modifiée avec succès.");
            } else {
                structureService.saveDivision(division, getUsername());
                addMessage("Division créée avec succès.");
            }
            chargerTout();
            division = new Division();
        } catch (Exception e) {
            addErrorMessage("Erreur : " + e.getMessage());
        }
    }

    public void supprimerDivision(Long id) {
        try {
            structureService.deleteDivision(id);
            chargerTout();
            addMessage("Division supprimée.");
        } catch (Exception e) {
            addErrorMessage("Impossible de supprimer (des services existent peut-être).");
        }
    }

    private void appliquerChefDivision() {
        if (chefDivisionId != null) division.setChef(trouverEmploye(chefDivisionId));
        else division.setChef(null);
    }

    private void appliquerDirectionDivision() {
        if (directionSelectId != null) {
            division.setDirection(structureService.getDirectionById(directionSelectId));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SERVICES
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauService() {
        service = new com.soutenance.entity.Service();
        chefServiceId = null;
        divisionSelectId = null;
        serviceDirectionId = null;
        serviceDepartementId = null;
        modeEditionService = false;
    }

    public void preparerEditionService(com.soutenance.entity.Service s) {
        this.service = s;
        chefServiceId = (s.getChef() != null) ? s.getChef().getId() : null;
        divisionSelectId = (s.getDivision() != null) ? s.getDivision().getId() : null;
        serviceDepartementId = (s.getDepartement() != null) ? s.getDepartement().getId() : null;
        serviceDirectionId = (s.getDirection() != null) ? s.getDirection().getId() : null;
        modeEditionService = true;
    }

    public void sauvegarderService() {
        try {
            appliquerChefService();
            appliquerParentService();
            if (service.getInstitution() == null) service.setInstitution(institutionCourante);
            if (modeEditionService) {
                structureService.updateService(service);
                addMessage("Service modifié avec succès.");
            } else {
                structureService.saveService(service, getUsername());
                addMessage("Service créé avec succès.");
            }
            chargerTout();
            service = new com.soutenance.entity.Service();
        } catch (Exception e) {
            addErrorMessage("Erreur : " + e.getMessage());
        }
    }

    public void supprimerService(Long id) {
        try {
            structureService.deleteService(id);
            chargerTout();
            addMessage("Service supprimé.");
        } catch (Exception e) {
            addErrorMessage("Impossible de supprimer (des bureaux existent peut-être).");
        }
    }

    private void appliquerChefService() {
        if (chefServiceId != null) service.setChef(trouverEmploye(chefServiceId));
        else service.setChef(null);
    }

    /**
     * Parent LIBRE d'un service : une Division, OU un Département, OU directement
     * une Direction, OU aucun. Un seul parent est conservé (priorité : division,
     * département, direction) ; les autres liens sont effacés.
     */
    private void appliquerParentService() {
        service.setDivision(null);
        service.setDepartement(null);
        service.setDirection(null);
        if (divisionSelectId != null) {
            service.setDivision(structureService.getDivisionById(divisionSelectId));
        } else if (serviceDepartementId != null) {
            service.setDepartement(structureService.getDepartementById(serviceDepartementId));
        } else if (serviceDirectionId != null) {
            service.setDirection(structureService.getDirectionById(serviceDirectionId));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  DÉPARTEMENTS (rattachés directement à une Direction)
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauDepartement() {
        departement = new Departement();
        chefDepartementId = null;
        departementDirectionId = null;
        modeEditionDepartement = false;
    }

    public void preparerEditionDepartement(Departement d) {
        this.departement = d;
        chefDepartementId = (d.getChef() != null) ? d.getChef().getId() : null;
        departementDirectionId = (d.getDirection() != null) ? d.getDirection().getId() : null;
        modeEditionDepartement = true;
    }

    public void sauvegarderDepartement() {
        try {
            departement.setChef(chefDepartementId != null ? trouverEmploye(chefDepartementId) : null);
            departement.setDirection(departementDirectionId != null
                ? structureService.getDirectionById(departementDirectionId) : null);
            if (departement.getInstitution() == null) departement.setInstitution(institutionCourante);
            if (modeEditionDepartement) {
                structureService.updateDepartement(departement);
                addMessage("Département modifié avec succès.");
            } else {
                structureService.saveDepartement(departement, getUsername());
                addMessage("Département créé avec succès.");
            }
            chargerTout();
            departement = new Departement();
            modeEditionDepartement = false;
        } catch (Exception e) {
            addErrorMessage("Erreur : " + e.getMessage());
        }
    }

    public void supprimerDepartement(Long id) {
        try {
            structureService.deleteDepartement(id);
            chargerTout();
            addMessage("Département supprimé.");
        } catch (Exception e) {
            addErrorMessage("Impossible de supprimer (des services y sont peut-être rattachés).");
        }
    }

    public long countServicesByDepartement(Long id) {
        try { return structureService.countServicesByDepartement(id); } catch (Exception e) { return 0; }
    }

    public List<Departement> getDepartements() { return departements; }
    public Departement getDepartement() { return departement; }
    public void setDepartement(Departement departement) { this.departement = departement; }
    public boolean isModeEditionDepartement() { return modeEditionDepartement; }
    public Long getServiceDirectionId() { return serviceDirectionId; }
    public void setServiceDirectionId(Long id) { this.serviceDirectionId = id; }
    public Long getServiceDepartementId() { return serviceDepartementId; }
    public void setServiceDepartementId(Long id) { this.serviceDepartementId = id; }
    public Long getDepartementDirectionId() { return departementDirectionId; }
    public void setDepartementDirectionId(Long id) { this.departementDirectionId = id; }
    public Long getChefDepartementId() { return chefDepartementId; }
    public void setChefDepartementId(Long id) { this.chefDepartementId = id; }

    // ══════════════════════════════════════════════════════════
    //  BUREAUX
    // ══════════════════════════════════════════════════════════

    public void preparerNouveauBureau() {
        bureau = new Bureau();
        chefBureauId = null;
        serviceSelectId = null;
        modeEditionBureau = false;
    }

    public void preparerEditionBureau(Bureau b) {
        this.bureau = b;
        chefBureauId = (b.getChef() != null) ? b.getChef().getId() : null;
        serviceSelectId = (b.getService() != null) ? b.getService().getId() : null;
        modeEditionBureau = true;
    }

    public void sauvegarderBureau() {
        try {
            appliquerChefBureau();
            appliquerServiceBureau();
            if (bureau.getInstitution() == null) bureau.setInstitution(institutionCourante);
            if (modeEditionBureau) {
                structureService.updateBureau(bureau);
                addMessage("Bureau modifié avec succès.");
            } else {
                structureService.saveBureau(bureau, getUsername());
                addMessage("Bureau créé avec succès.");
            }
            chargerTout();
            bureau = new Bureau();
        } catch (Exception e) {
            addErrorMessage("Erreur : " + e.getMessage());
        }
    }

    public void supprimerBureau(Long id) {
        try {
            structureService.deleteBureau(id);
            chargerTout();
            addMessage("Bureau supprimé.");
        } catch (Exception e) {
            addErrorMessage("Erreur suppression bureau.");
        }
    }

    private void appliquerChefBureau() {
        if (chefBureauId != null) bureau.setChef(trouverEmploye(chefBureauId));
        else bureau.setChef(null);
    }

    private void appliquerServiceBureau() {
        if (serviceSelectId != null) {
            bureau.setService(structureService.getServiceById(serviceSelectId));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTHODES UTILITAIRES
    // ══════════════════════════════════════════════════════════

    private Employe trouverEmploye(Long id) {
        if (id == null || employes == null) return null;
        return employes.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
    }

    public long countDivisionsByDirection(Long id) {
        try { return structureService.countDivisionsByDirection(id); } catch(Exception e) { return 0; }
    }

    public long countServicesByDivision(Long id) {
        try { return structureService.countServicesByDivision(id); } catch(Exception e) { return 0; }
    }

    public long countBureauxByService(Long id) {
        try { return structureService.countBureauxByService(id); } catch(Exception e) { return 0; }
    }

    private void addMessage(String msg) {
        jakarta.faces.application.FacesMessage fm = new jakarta.faces.application.FacesMessage(
            jakarta.faces.application.FacesMessage.SEVERITY_INFO, msg, null);
        jakarta.faces.context.FacesContext.getCurrentInstance().addMessage("growl", fm);
    }

    private void addErrorMessage(String msg) {
        jakarta.faces.application.FacesMessage fm = new jakarta.faces.application.FacesMessage(
            jakarta.faces.application.FacesMessage.SEVERITY_ERROR, msg, null);
        jakarta.faces.context.FacesContext.getCurrentInstance().addMessage("growl", fm);
    }

    // ── Getters / Setters ──────────────────────────────────────
    public List<Direction> getDirections() { return directions; }
    public List<Division> getDivisions() { return divisions; }
    public List<com.soutenance.entity.Service> getServices() { return services; }
    public List<Bureau> getBureaux() { return bureaux; }
    public List<Employe> getEmployes() { return employes; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }
    public com.soutenance.entity.Service getService() { return service; }
    public void setService(com.soutenance.entity.Service service) { this.service = service; }
    public Bureau getBureau() { return bureau; }
    public void setBureau(Bureau bureau) { this.bureau = bureau; }

    public boolean isModeEditionDirection() { return modeEditionDirection; }
    public boolean isModeEditionDivision() { return modeEditionDivision; }
    public boolean isModeEditionService() { return modeEditionService; }
    public boolean isModeEditionBureau() { return modeEditionBureau; }

    public Long getDirectionSelectId() { return directionSelectId; }
    public void setDirectionSelectId(Long directionSelectId) { this.directionSelectId = directionSelectId; }
    public Long getDivisionSelectId() { return divisionSelectId; }
    public void setDivisionSelectId(Long divisionSelectId) { this.divisionSelectId = divisionSelectId; }
    public Long getServiceSelectId() { return serviceSelectId; }
    public void setServiceSelectId(Long serviceSelectId) { this.serviceSelectId = serviceSelectId; }

    public Long getChefDirectionId() { return chefDirectionId; }
    public void setChefDirectionId(Long chefDirectionId) { this.chefDirectionId = chefDirectionId; }
    public Long getChefDivisionId() { return chefDivisionId; }
    public void setChefDivisionId(Long chefDivisionId) { this.chefDivisionId = chefDivisionId; }
    public Long getChefServiceId() { return chefServiceId; }
    public void setChefServiceId(Long chefServiceId) { this.chefServiceId = chefServiceId; }
    public Long getChefBureauId() { return chefBureauId; }
    public void setChefBureauId(Long chefBureauId) { this.chefBureauId = chefBureauId; }

    // ── Niveaux hiérarchiques ─────────────────────────────────────────────

    public com.soutenance.entity.Institution getInstitutionCourante() { return institutionCourante; }
    public List<String> getNiveauxHierarchie() { return niveauxHierarchie; }

    /** Libellé du niveau 1 (ex: "Direction", "Département") */
    public String getNiveau1() { return getNiveau(0, "Direction"); }
    /** Libellé du niveau 2 */
    public String getNiveau2() { return getNiveau(1, "Division"); }
    /** Libellé du niveau 3 */
    public String getNiveau3() { return getNiveau(2, "Service"); }
    /** Libellé du niveau 4 */
    public String getNiveau4() { return getNiveau(3, "Bureau"); }

    // Alias getLabelNiveauX() — requis par structures.xhtml (#{structureBean.labelNiveau1})
    public String getLabelNiveau1()   { return getNiveau1(); }
    public String getLabelNiveau2()   { return getNiveau2(); }
    public String getLabelNiveau3()   { return getNiveau3(); }
    public String getLabelNiveau4()   { return getNiveau4(); }
    public String getLabelNiveau1Pl() { return getNiveau1() + "s"; }
    public String getLabelNiveau2Pl() { return getNiveau2() + "s"; }
    public String getLabelNiveau3Pl() { return getNiveau3() + "s"; }
    public String getLabelNiveau4Pl() { return getNiveau4() + "x"; }
    public int    getNombreNiveaux()  { return niveauxHierarchie != null ? niveauxHierarchie.size() : 4; }

    /**
     * Retourne les types d'unité disponibles selon le modèle hiérarchique
     * de l'institution courante.
     * Utilisé par #{structureBean.typesUnite} dans les formulaires directions.xhtml.
     * Format : [SelectItem(value=NOM_NIVEAU, label=Nom du niveau)]
     */
    public java.util.List<jakarta.faces.model.SelectItem> getTypesUnite() {
        java.util.List<jakarta.faces.model.SelectItem> items = new java.util.ArrayList<>();
        for (String niveau : getNiveaux()) {
            String val = niveau.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_");
            items.add(new jakarta.faces.model.SelectItem(val, niveau.trim()));
        }
        return items;
    }

    /** Retourne les niveaux comme simple liste de String. */
    public java.util.List<String> getNiveaux() {
        if (niveauxHierarchie != null && !niveauxHierarchie.isEmpty())
            return niveauxHierarchie;
        return java.util.Arrays.asList("Direction", "Division", "Service", "Bureau");
    }

    /** True si l'institution utilise ce niveau */
    public boolean isNiveau2Actif() { return niveauxHierarchie.size() >= 2; }
    public boolean isNiveau3Actif() { return niveauxHierarchie.size() >= 3; }
    public boolean isNiveau4Actif() { return niveauxHierarchie.size() >= 4; }

    private String getNiveau(int index, String defaut) {
        if (niveauxHierarchie != null && niveauxHierarchie.size() > index)
            return niveauxHierarchie.get(index).trim();
        return defaut;
    }

    // ── Alias creer/modifier/prepareEdit ─────────────────────────────────
    public void creer()  { sauvegarderDirection(); }
    public void modifier() { sauvegarderDirection(); }
    public void prepareEdit(com.soutenance.entity.Direction d) { preparerEditionDirection(d); }
}