package com.soutenance.service;

import com.soutenance.entity.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit l'organigramme de la direction d'un employé.
 *
 * La structure est LIBRE et propre à chaque direction :
 *   Direction → Départements → Services → Bureaux
 *   Direction → Services → Bureaux
 *   Direction → Divisions → Services → Bureaux
 *   (ou n'importe quel mélange de ces branches)
 *
 * Toute la construction se fait dans UNE session Hibernate ouverte, ce qui
 * évite les LazyInitializationException.
 */
@org.springframework.stereotype.Service
public class OrganigrammeService {

    private static final Logger LOG = LoggerFactory.getLogger(OrganigrammeService.class);

    @Autowired private SessionFactory sessionFactory;

    // ══════════════════════════════════════════════════════════
    //  Modèle d'affichage
    // ══════════════════════════════════════════════════════════

    public static class Membre implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String nom;
        private String poste;
        private boolean chef;
        private boolean moi;

        public Long getId()      { return id; }
        public String getNom()   { return nom; }
        public String getPoste() { return poste; }
        public boolean isChef()  { return chef; }
        public boolean isMoi()   { return moi; }
    }

    public static class Noeud implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type;          // DIRECTION, DIVISION, DEPARTEMENT, SERVICE, BUREAU
        private String libelleType;
        private Long id;
        private String nom;
        private String code;
        private String chefNom;
        private List<Membre> membres = new ArrayList<>();
        private List<Noeud>  enfants = new ArrayList<>();
        private int totalEmployes;
        private boolean moi;          // l'utilisateur est rattaché ici
        private boolean contientMoi;  // l'utilisateur est dans cette branche

        public String getType()        { return type; }
        public String getLibelleType() { return libelleType; }
        public Long getId()            { return id; }
        public String getNom()         { return nom; }
        public String getCode()        { return code; }
        public String getChefNom()     { return chefNom; }
        public List<Membre> getMembres() { return membres; }
        public List<Noeud>  getEnfants() { return enfants; }
        public int getTotalEmployes()  { return totalEmployes; }
        public boolean isMoi()         { return moi; }
        public boolean isContientMoi() { return contientMoi; }
    }

    // ══════════════════════════════════════════════════════════
    //  API
    // ══════════════════════════════════════════════════════════

    /** Organigramme de la direction de l'employé (null si l'employé n'a aucun rattachement). */
    public Noeud construirePourEmploye(Long employeId) {
        if (employeId == null) return null;
        try (Session s = sessionFactory.openSession()) {
            Employe emp = s.get(Employe.class, employeId);
            if (emp == null) return null;

            Noeud racine;
            Direction dir = resoudreDirection(emp);
            if (dir != null) {
                racine = noeudDirection(s, employeId, dir);
            } else {
                racine = racineSansDirection(s, employeId, emp);
            }
            if (racine != null) calculer(racine);
            return racine;
        } catch (Exception e) {
            LOG.error("Erreur construction organigramme (employé {})", employeId, e);
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Résolution de la racine
    // ══════════════════════════════════════════════════════════

    private Service serviceDe(Employe e) {
        if (e.getServiceOrg() != null) return e.getServiceOrg();
        return e.getBureauOrg() != null ? e.getBureauOrg().getService() : null;
    }

    private Direction resoudreDirection(Employe e) {
        if (e.getDirectionOrg() != null) return e.getDirectionOrg();
        if (e.getDivisionOrg() != null && e.getDivisionOrg().getDirection() != null)
            return e.getDivisionOrg().getDirection();
        if (e.getDepartementOrg() != null && e.getDepartementOrg().getDirection() != null)
            return e.getDepartementOrg().getDirection();
        Service srv = serviceDe(e);
        return srv != null ? srv.getDirectionEffective() : null;
    }

    /** Institution sans niveau Direction : on part de la plus haute unité connue. */
    private Noeud racineSansDirection(Session s, Long empId, Employe e) {
        Service srv = serviceDe(e);
        Division dv = e.getDivisionOrg() != null ? e.getDivisionOrg()
                    : (srv != null ? srv.getDivision() : null);
        if (dv != null) return noeudDivision(s, empId, dv);
        Departement dp = e.getDepartementOrg() != null ? e.getDepartementOrg()
                       : (srv != null ? srv.getDepartement() : null);
        if (dp != null) return noeudDepartement(s, empId, dp);
        if (srv != null) return noeudService(s, empId, srv);
        return null;
    }

    // ══════════════════════════════════════════════════════════
    //  Construction des nœuds
    // ══════════════════════════════════════════════════════════

    private Noeud noeudDirection(Session s, Long empId, Direction d) {
        Noeud n = base("DIRECTION", "Direction", d.getId(), d.getNom(), d.getCode());
        n.membres = membres(s, empId, "e.directionOrg.id", d.getId(), d.getChef());
        n.chefNom = nomChef(d.getChef());

        for (Division dv : s.createQuery(
                "from Division dv where dv.direction.id = :id order by dv.nom", Division.class)
                .setParameter("id", d.getId()).list())
            n.enfants.add(noeudDivision(s, empId, dv));

        for (Departement dp : s.createQuery(
                "from Departement dp where dp.direction.id = :id order by dp.nom", Departement.class)
                .setParameter("id", d.getId()).list())
            n.enfants.add(noeudDepartement(s, empId, dp));

        for (Service sv : s.createQuery(
                "from Service sv where sv.direction.id = :id "
              + "and sv.division is null and sv.departement is null order by sv.nom", Service.class)
                .setParameter("id", d.getId()).list())
            n.enfants.add(noeudService(s, empId, sv));
        return n;
    }

    private Noeud noeudDivision(Session s, Long empId, Division dv) {
        Noeud n = base("DIVISION", "Division", dv.getId(), dv.getNom(), dv.getCode());
        n.membres = membres(s, empId, "e.divisionOrg.id", dv.getId(), dv.getChef());
        n.chefNom = nomChef(dv.getChef());
        for (Service sv : s.createQuery(
                "from Service sv where sv.division.id = :id order by sv.nom", Service.class)
                .setParameter("id", dv.getId()).list())
            n.enfants.add(noeudService(s, empId, sv));
        return n;
    }

    private Noeud noeudDepartement(Session s, Long empId, Departement dp) {
        Noeud n = base("DEPARTEMENT", "Département", dp.getId(), dp.getNom(), dp.getCode());
        n.membres = membres(s, empId, "e.departementOrg.id", dp.getId(), dp.getChef());
        n.chefNom = nomChef(dp.getChef());
        for (Service sv : s.createQuery(
                "from Service sv where sv.departement.id = :id and sv.division is null order by sv.nom",
                Service.class).setParameter("id", dp.getId()).list())
            n.enfants.add(noeudService(s, empId, sv));
        return n;
    }

    private Noeud noeudService(Session s, Long empId, Service sv) {
        Noeud n = base("SERVICE", "Service", sv.getId(), sv.getNom(), sv.getCode());
        n.membres = membres(s, empId, "e.serviceOrg.id", sv.getId(), sv.getChef());
        n.chefNom = nomChef(sv.getChef());
        for (Bureau b : s.createQuery(
                "from Bureau b where b.service.id = :id order by b.nom", Bureau.class)
                .setParameter("id", sv.getId()).list()) {
            Noeud nb = base("BUREAU", "Bureau", b.getId(), b.getNom(), b.getCode());
            nb.membres = membres(s, empId, "e.bureauOrg.id", b.getId(), b.getChef());
            nb.chefNom = nomChef(b.getChef());
            n.enfants.add(nb);
        }
        return n;
    }

    // ══════════════════════════════════════════════════════════
    //  Utilitaires
    // ══════════════════════════════════════════════════════════

    private Noeud base(String type, String libelle, Long id, String nom, String code) {
        Noeud n = new Noeud();
        n.type = type; n.libelleType = libelle; n.id = id; n.nom = nom; n.code = code;
        return n;
    }

    private String nomChef(Employe chef) {
        return chef != null ? chef.getNomComplet().trim() : null;
    }

    /** Agents rattachés DIRECTEMENT à cette unité (le chef est toujours listé en premier). */
    private List<Membre> membres(Session s, Long empId, String attr, Long unitId, Employe chef) {
        List<Employe> agents = s.createQuery(
                "select e from Employe e left join fetch e.poste "
              + "where " + attr + " = :id order by e.nom, e.prenom", Employe.class)
                .setParameter("id", unitId).list();

        List<Membre> res = new ArrayList<>();
        boolean chefPresent = false;
        for (Employe e : agents) {
            boolean estChef = chef != null && chef.getId().equals(e.getId());
            chefPresent |= estChef;
            Membre m = versMembre(e, empId, estChef);
            if (estChef) res.add(0, m); else res.add(m);
        }
        if (chef != null && !chefPresent) res.add(0, versMembre(chef, empId, true));
        return res;
    }

    private Membre versMembre(Employe e, Long empId, boolean chef) {
        Membre m = new Membre();
        m.id = e.getId();
        m.nom = e.getNomComplet().trim();
        String poste;
        try { poste = e.getIntiutePoste(); } catch (Exception ex) { poste = null; }
        m.poste = poste;
        m.chef = chef;
        m.moi = empId.equals(e.getId());
        return m;
    }

    /** Calcule les effectifs cumulés et marque la branche de l'utilisateur. */
    private void calculer(Noeud n) {
        int total = n.membres.size();
        boolean moi = false;
        for (Membre m : n.membres) moi |= m.moi;
        n.moi = moi;
        boolean contient = moi;
        for (Noeud c : n.enfants) {
            calculer(c);
            total += c.totalEmployes;
            contient |= c.contientMoi;
        }
        n.totalEmployes = total;
        n.contientMoi = contient;
    }
}
