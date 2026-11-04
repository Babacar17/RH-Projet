package com.soutenance.bean;

import com.soutenance.entity.Evenement;
import com.soutenance.entity.Institution;
import com.soutenance.service.EvenementService;
import com.soutenance.service.InstitutionService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue super-admin consolidée des événements toutes institutions.
 * Filtrable par institution, type, statut. Lecture seule pour l'instant —
 * la création/modification se fait depuis l'espace RH de l'institution.
 */
@Component("adminEvenementBean")
@Scope("view")
public class AdminEvenementBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private EvenementService    evenementService;
    @Autowired private InstitutionService  institutionService;
    @Autowired private SessionBean         sessionBean;

    private List<Evenement>   evenements         = new ArrayList<>();
    private List<Institution> toutesInstitutions = new ArrayList<>();

    private Long   filtreInstitutionId;
    private String filtreType;
    private String filtreStatut;

    @PostConstruct
    public void init() {
        toutesInstitutions = institutionService.getToutesLesInstitutions();
        chargerTout();
    }

    public void chargerTout() {
        if (sessionBean.isSuperAdmin()) {
            evenements = evenementService.getAll();
        } else {
            // Si pas super-admin, on cloisonne sur l'institution de l'utilisateur
            evenements = evenementService.getByInstitution(sessionBean.getInstitutionId());
        }
    }

    public void appliquerFiltres() {
        chargerTout();
        if (filtreInstitutionId != null) {
            evenements = evenements.stream()
                .filter(e -> e.getInstitution() != null
                          && filtreInstitutionId.equals(e.getInstitution().getId()))
                .collect(java.util.stream.Collectors.toList());
        }
        if (filtreType != null && !filtreType.isBlank()) {
            evenements = evenements.stream()
                .filter(e -> filtreType.equalsIgnoreCase(e.getType()))
                .collect(java.util.stream.Collectors.toList());
        }
        if (filtreStatut != null && !filtreStatut.isBlank()) {
            evenements = evenements.stream()
                .filter(e -> filtreStatut.equalsIgnoreCase(e.getStatut()))
                .collect(java.util.stream.Collectors.toList());
        }
    }

    public void reinitialiserFiltres() {
        filtreInstitutionId = null;
        filtreType         = null;
        filtreStatut       = null;
        chargerTout();
    }

    // ── KPI consolidés ──────────────────────────────────────────────────────

    public long getNombreEvenements()   { return evenements != null ? evenements.size() : 0; }

    public long getNombreAVenir() {
        if (evenements == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        return evenements.stream()
            .filter(e -> e.getDateDebut() != null && e.getDateDebut().isAfter(now)
                      && !"ANNULE".equalsIgnoreCase(e.getStatut())).count();
    }

    public long getNombreEnCours() {
        if (evenements == null) return 0;
        return evenements.stream().filter(Evenement::isEnCours).count();
    }

    public long getBudgetTotal() {
        if (evenements == null) return 0;
        return evenements.stream()
            .filter(e -> e.getBudget() != null)
            .mapToLong(e -> e.getBudget().longValue())
            .sum();
    }

    /** Répartition par institution pour graphique. */
    public java.util.Map<String, Long> getRepartitionParInstitution() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        if (evenements == null) return out;
        evenements.stream()
            .filter(e -> e.getInstitution() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getInstitution().getCode() != null
                    ? e.getInstitution().getCode() : "?",
                java.util.stream.Collectors.counting()))
            .forEach(out::put);
        return out;
    }

    /** Répartition par type pour graphique. */
    public java.util.Map<String, Long> getRepartitionParType() {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        if (evenements == null) return out;
        evenements.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getType() != null ? e.getType() : "?",
                java.util.stream.Collectors.counting()))
            .forEach(out::put);
        return out;
    }

    public String getBadgeStatut(String statut) {
        if (statut == null) return "badge-secondary";
        return switch (statut.toUpperCase()) {
            case "PROGRAMME" -> "badge-info";
            case "CONFIRME"  -> "badge-primary";
            case "EN_COURS"  -> "badge-warning";
            case "TERMINE"   -> "badge-success";
            case "ANNULE"    -> "badge-danger";
            default          -> "badge-secondary";
        };
    }

    public String getIconeType(String type) {
        if (type == null) return "pi-calendar";
        return switch (type.toUpperCase()) {
            case "SEMINAIRE"           -> "pi-bookmark";
            case "REUNION"             -> "pi-users";
            case "VOYAGE"              -> "pi-send";
            case "FORMATION_EXTERNE"   -> "pi-book";
            case "WORKSHOP"            -> "pi-cog";
            case "CONFERENCE"          -> "pi-megaphone";
            case "JOURNEE_INTEGRATION" -> "pi-star";
            default                    -> "pi-calendar";
        };
    }

    public List<Evenement>   getEvenements()              { return evenements; }
    public List<Institution> getToutesInstitutions()      { return toutesInstitutions; }
    public Long              getFiltreInstitutionId()     { return filtreInstitutionId; }
    public void              setFiltreInstitutionId(Long id) { this.filtreInstitutionId = id; }
    public String            getFiltreType()              { return filtreType; }
    public void              setFiltreType(String t)      { this.filtreType = t; }
    public String            getFiltreStatut()            { return filtreStatut; }
    public void              setFiltreStatut(String s)    { this.filtreStatut = s; }
}
