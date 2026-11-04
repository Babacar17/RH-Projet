package com.soutenance.bean;


import java.io.Serializable;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component("accueilBean")
@Scope("request")
public class AccueilBean implements Serializable {

    private String titre = "Système de Gestion";
    private String description = "Application basée sur JSF, PrimeFaces, Spring et Hibernate";

    // Getters / Setters
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}