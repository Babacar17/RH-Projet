package com.soutenance.bean;

import com.soutenance.service.HealthCheckService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean dashboard health check système.
 */
@Component("healthBean")
@Scope("view")
public class HealthBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private HealthCheckService healthService;

    private Map<String, Object>            statutSysteme;
    private List<Map<String, Object>>      statsBdd       = new ArrayList<>();
    private List<Map<String, Object>>      activites      = new ArrayList<>();

    @PostConstruct
    public void init() { rafraichir(); }

    public void rafraichir() {
        try {
            statutSysteme = healthService.getStatutSysteme();
            statsBdd      = healthService.getStatistiquesBdd();
            activites     = healthService.getActivitesTechniques();
        } catch (Exception ignored) { /* dashboard ne doit jamais crasher */ }
    }

    /** Couleur d'alerte selon le ratio (mémoire, disque). */
    public String getCouleurRatio(Object ratio) {
        if (ratio == null) return "#16a34a";
        try {
            long r = Long.parseLong(String.valueOf(ratio));
            if (r >= 90) return "#dc2626";
            if (r >= 75) return "#f59e0b";
            return "#16a34a";
        } catch (Exception ex) { return "#64748b"; }
    }

    public Map<String, Object>      getStatutSysteme() { return statutSysteme; }
    public List<Map<String, Object>> getStatsBdd()     { return statsBdd; }
    public List<Map<String, Object>> getActivites()    { return activites; }
}
