package com.soutenance.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Étape d'un workflow de validation.
 * Définit qui valide, dans quel ordre, avec quelle condition.
 *
 * CORRECTION : le champ "workflow" référençait l'ancienne entité Workflow
 * (table "workflows"). Il référence maintenant WorkflowValidation
 * (table "workflow_validations"), qui est le modèle actif du projet.
 *
 * Impact sur la base de données :
 *   - La colonne "workflow_id" dans "etapes_validation" doit pointer vers
 *     la table "workflow_validations" et non plus "workflows".
 *   - Si la table "workflows" est conservée, vérifier qu'aucune contrainte FK
 *     ne pointe encore vers elle depuis "etapes_validation".
 *   Migration :
 *     ALTER TABLE etapes_validation
 *       DROP FOREIGN KEY fk_etape_workflow,       -- nom exact à adapter
 *       ADD CONSTRAINT fk_etape_workflow_val
 *         FOREIGN KEY (workflow_id)
 *         REFERENCES workflow_validations(id);
 */
@Entity
@Table(name = "etapes_validation")
public class EtapeValidation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * CORRECTION : était "private Workflow workflow".
     * Remplacé par WorkflowValidation — entité active du projet.
     * La colonne FK "workflow_id" reste la même en base.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowValidation workflow;

    /** Numéro d'ordre de l'étape (1, 2, 3…). */
    @Column(nullable = false)
    private Integer ordre;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Rôle requis pour valider cette étape.
     * Ex : MANAGER, RH, ADMIN, DIRECTEUR
     */
    @Column(nullable = false, length = 100)
    private String roleValidateur;

    /**
     * Condition déclenchant cette étape (expression simple).
     * NULL = toujours déclenchée.
     * Ex : "NB_JOURS > 15", "TYPE_CONGE == SANS_SOLDE"
     */
    @Column(length = 300)
    private String conditionDeclenchement;

    /** Délai maximum en heures pour valider cette étape (0 = sans limite). */
    private Integer delaiHeures;

    /** Escalader vers l'étape suivante si le délai est dépassé ? */
    @Column(name = "escalade_auto")
    private boolean escaladeAuto = false;

    /** Cette étape est-elle obligatoire ou peut-elle être sautée ? */
    @Column(name = "obligatoire")
    private boolean obligatoire = true;

    /** Action en cas de refus : BLOQUER, RETOURNER_ETAPE_PRECEDENTE, REJETER_TOUT */
    @Column(length = 50)
    private String actionRefus;

    @Column(updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        if (actionRefus == null) actionRefus = "BLOQUER";
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** CORRECTION : type WorkflowValidation (anciennement Workflow). */
    public WorkflowValidation getWorkflow() { return workflow; }
    public void setWorkflow(WorkflowValidation workflow) { this.workflow = workflow; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRoleValidateur() { return roleValidateur; }
    public void setRoleValidateur(String roleValidateur) { this.roleValidateur = roleValidateur; }

    public String getConditionDeclenchement() { return conditionDeclenchement; }
    public void setConditionDeclenchement(String c) { this.conditionDeclenchement = c; }

    public Integer getDelaiHeures() { return delaiHeures; }
    public void setDelaiHeures(Integer delaiHeures) { this.delaiHeures = delaiHeures; }

    public boolean isEscaladeAuto() { return escaladeAuto; }
    public void setEscaladeAuto(boolean escaladeAuto) { this.escaladeAuto = escaladeAuto; }

    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }

    public String getActionRefus() { return actionRefus; }
    public void setActionRefus(String actionRefus) { this.actionRefus = actionRefus; }

    public LocalDateTime getDateCreation() { return dateCreation; }
}