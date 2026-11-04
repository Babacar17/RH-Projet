package com.soutenance.bean;

import com.soutenance.dao.EmployeDao;
import com.soutenance.entity.Employe;
import com.soutenance.service.OrganigrammeService;
import com.soutenance.service.OrganigrammeService.Noeud;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Organigramme visible par tout employé / chef : celui de SA direction.
 * Aucun paramètre d'URL → un utilisateur ne peut pas consulter une autre direction.
 */
@Component("organigrammeBean")
@Scope("view")
public class OrganigrammeBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired private OrganigrammeService organigrammeService;
    @Autowired private EmployeDao          employeDao;
    @Autowired private SessionBean         sessionBean;

    private Noeud racine;

    @PostConstruct
    public void init() {
        try {
            if (sessionBean.getUtilisateurConnecte() == null) return;
            Employe moi = employeDao.findByUserId(sessionBean.getUtilisateurConnecte().getId());
            if (moi != null) racine = organigrammeService.construirePourEmploye(moi.getId());
        } catch (Exception e) {
            racine = null;
        }
    }

    public Noeud getRacine()  { return racine; }
    public boolean isVide()   { return racine == null; }
}
