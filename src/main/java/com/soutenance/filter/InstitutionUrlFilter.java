package com.soutenance.filter;

import com.soutenance.dao.InstitutionDao;
import com.soutenance.entity.Institution;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;
import java.util.Set;

/**
 * Filtre d'URL multi-tenant : capte les chemins
 * /Soutenance/{CODE}/login           → /Authentification/login.xhtml
 * /Soutenance/{CODE}/inscription     → /Authentification/inscription.xhtml
 * /Soutenance/{CODE}                 → /Authentification/login.xhtml
 *
 * Le code d'institution est résolu via {@link InstitutionDao} puis stocké
 * dans la HttpSession sous la clé « institutionPortail ». Cela permet au
 * LoginBean et au login.xhtml d'afficher le branding (logo, nom) et de
 * vérifier que l'utilisateur authentifié appartient bien à l'institution.
 *
 * URLs Jakarta EE/JSF habituelles (Administrateur, Employes, etc.) ne sont
 * PAS captées par ce filtre : il vérifie d'abord que le segment n'est pas
 * un dossier existant dans webapp/.
 */
@WebFilter(urlPatterns = "/*")
public class InstitutionUrlFilter implements Filter {

    /** Premiers segments qu'on ne doit JAMAIS interpréter comme code institution. */
    private static final Set<String> SEGMENTS_RESERVES = Set.of(
        "Authentification", "Administrateur", "ResponsableRH",
        "ResponsableHierarchique", "Employes", "Candidat", "Auditeur",
        "Principal", "WEB-INF", "META-INF", "resources",
        "jakarta.faces.resource", "javax.faces.resource", "org.primefaces",
        "faces"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri  = request.getRequestURI();
        String ctx  = request.getContextPath();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        if (path.startsWith("/")) path = path.substring(1);

        // Pas d'URL → on continue (servlet par défaut affichera l'index)
        if (path.isEmpty()) { chain.doFilter(req, res); return; }

        // Extraire le premier segment
        int slash = path.indexOf('/');
        String premier = (slash < 0) ? path : path.substring(0, slash);
        String reste   = (slash < 0) ? ""   : path.substring(slash + 1);

        // Ressources statiques ou segments réservés : on laisse passer
        if (SEGMENTS_RESERVES.contains(premier) || premier.contains(".")
            || premier.startsWith("jakarta") || premier.startsWith("javax")) {
            chain.doFilter(req, res);
            return;
        }

        // Tenter de résoudre comme code d'institution
        Institution inst = chercherInstitution(request, premier);
        if (inst == null) {
            // Code inconnu : on continue (autre filtre / 404 standard)
            chain.doFilter(req, res);
            return;
        }

        // ── Stocker le contexte institution dans la session ──────────────
        HttpSession session = request.getSession(true);
        session.setAttribute("institutionPortail",     inst);
        session.setAttribute("institutionPortailCode", inst.getCode());
        session.setAttribute("institutionPortailNom",  inst.getNom());

        // ── Mapper la sous-route vers la page JSF effective ──────────────
        String cible;
        if (reste.isEmpty() || "login".equalsIgnoreCase(reste) || "login.xhtml".equalsIgnoreCase(reste)) {
            cible = "/Authentification/login.xhtml";
        } else if ("inscription".equalsIgnoreCase(reste) || "inscription.xhtml".equalsIgnoreCase(reste)) {
            cible = "/Authentification/inscription.xhtml";
        } else if ("reset".equalsIgnoreCase(reste) || reste.startsWith("reset")) {
            cible = "/Authentification/reset.xhtml" + (request.getQueryString() != null
                ? "?" + request.getQueryString() : "");
        } else {
            // Pas une sous-route gérée : on laisse continuer (peut-être ressource)
            chain.doFilter(req, res);
            return;
        }

        request.getRequestDispatcher(cible).forward(req, res);
    }

    /** Résout un code d'institution via Spring (DAO). Retourne null si introuvable. */
    private Institution chercherInstitution(HttpServletRequest req, String code) {
        try {
            WebApplicationContext ctx = WebApplicationContextUtils
                .getWebApplicationContext(req.getServletContext());
            if (ctx == null) return null;
            InstitutionDao dao = ctx.getBean(InstitutionDao.class);
            return dao.findByCode(code);
        } catch (Exception e) {
            return null;
        }
    }
}
