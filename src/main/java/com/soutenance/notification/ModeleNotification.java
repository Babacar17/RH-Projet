package com.soutenance.notification;

public enum ModeleNotification {

    CONGE_DEMANDE("CONGE_DEMANDE", "HAUTE", "/ResponsableRH/conges.xhtml",
        "Nouvelle demande de congé", "%s demande un congé de %d jour(s)."),

    CONGE_ANNULE("CONGE_ANNULE", "HAUTE", "/ResponsableRH/conges.xhtml",
        "Demande de congé annulée", "%s a annulé sa demande de %d jour(s)."),

    CANDIDATURE_RECUE("RECRUTEMENT_CANDIDATURE", "NORMALE", "/ResponsableRH/candidatures.xhtml",
        "Nouvelle candidature", "%s a postulé à « %s »."),

    CYCLE_PAIE_VERSEMENT_PROCHE("PAIE_RELANCE", "HAUTE", "/ResponsableRH/paie.xhtml",
        "Versement de paie à préparer",
        "Le versement de %s est prévu dans %d jour(s) et le calcul n'a pas encore été lancé."),

    RAPPORT_JURY_DISPONIBLE("RECRUTEMENT_RAPPORT", "NORMALE", "/Employes/concours-jury.xhtml",
        "Rapport de recrutement disponible",
        "Le rapport « %s » pour l'offre « %s » est maintenant disponible."),

    PV_JURY_DISPONIBLE("RECRUTEMENT_PV", "NORMALE", "/Employes/concours-jury.xhtml",
        "PV de délibération disponible",
        "Le PV « %s » pour l'offre « %s » est maintenant disponible."),

    CONGE_EN_RETARD("CONGE_RELANCE", "HAUTE", "/ResponsableRH/conges.xhtml",
        "Demande de congé en retard de traitement",
        "La demande de %s (%s) n'a toujours pas été traitée alors que la période demandée (jusqu'au %s) est déjà passée.");

    private final String type;
    private final String priorite;
    private final String lienAction;
    private final String titre;
    private final String messageFormat;

    ModeleNotification(String type, String priorite, String lienAction, String titre, String messageFormat) {
        this.type = type;
        this.priorite = priorite;
        this.lienAction = lienAction;
        this.titre = titre;
        this.messageFormat = messageFormat;
    }

    public String getType()       { return type; }
    public String getPriorite()   { return priorite; }
    public String getLienAction() { return lienAction; }
    public String getTitre()      { return titre; }

    public String rendreMessage(Object... valeurs) {
        return String.format(messageFormat, valeurs);
    }
}
