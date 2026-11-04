package com.soutenance.filter;

import com.soutenance.bean.SessionBean;
import com.soutenance.entity.User;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;

@WebFilter(urlPatterns = {
    "/Administrateur/*",
    "/ResponsableRH/*",
    "/ResponsableHierarchique/*",
    "/Employes/*",
    "/Candidat/*",
    "/Auditeur/*",
    "/Principal/mon-compte.xhtml"
})
public class SecuriteFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String uri = request.getRequestURI();

        // Ressources statiques : toujours laisser passer
        if (isStaticResource(uri)) {
            chain.doFilter(req, res);
            return;
        }

        // Lire l'utilisateur depuis HttpSession
        User user = null;
        String role = null;

        HttpSession httpSession = request.getSession(false);
        if (httpSession != null) {
            user = (User) httpSession.getAttribute("utilisateurConnecte");
            role = (String) httpSession.getAttribute("roleConnecte");
        }

        // Fallback : Spring SessionBean
        if (user == null) {
            try {
                WebApplicationContext ctx = WebApplicationContextUtils
                    .getWebApplicationContext(request.getServletContext());
                if (ctx != null) {
                    SessionBean sessionBean = ctx.getBean(SessionBean.class);
                    if (sessionBean != null && sessionBean.getUtilisateurConnecte() != null) {
                        user = sessionBean.getUtilisateurConnecte();
                        role = sessionBean.getRolePrincipal();
                        HttpSession newSession = request.getSession(true);
                        newSession.setAttribute("utilisateurConnecte", user);
                        newSession.setAttribute("roleConnecte", role);
                    }
                }
            } catch (Exception e) { /* Spring context pas encore prêt */ }
        }

        System.out.println("[FILTRE] URI=" + uri
            + " | session=" + (httpSession != null ? httpSession.getId() : "NULL")
            + " | user=" + (user != null ? user : "NULL")
            + " | role=" + role);

        // Non connecté → login
        if (user == null) {
            response.sendRedirect(request.getContextPath()
                + "/Authentification/login.xhtml");
            return;
        }

        if (role == null) role = "";

        // ── Page mon-compte : accessible à TOUS les rôles connectés ──
        if (uri.contains("/Principal/mon-compte.xhtml")) {
            chain.doFilter(req, res);
            return;
        }

        if (isAutorise(uri, role)) {
            // ── CORRECTION : super-admin sans institution sélectionnée ────────
            // Si l'admin est super-admin ET qu'il n'a pas encore choisi une
            // institution ET qu'il tente d'accéder à une page qui requiert
            // une institution (tout sauf dashboard, institutions, parametres),
            // on le redirige vers le dashboard pour qu'il fasse son choix.
            if ("ADMIN".equals(role) && requiertInstitution(uri)) {
                try {
                    org.springframework.web.context.WebApplicationContext ctx =
                        org.springframework.web.context.support.WebApplicationContextUtils
                            .getWebApplicationContext(request.getServletContext());
                    if (ctx != null) {
                        SessionBean sb = ctx.getBean(SessionBean.class);
                        if (sb != null && sb.isSuperAdmin()) {
                            // Récupérer AdminBean pour vérifier institutionFiltreId
                            com.soutenance.bean.AdminBean adminBean =
                                ctx.getBean(com.soutenance.bean.AdminBean.class);
                            boolean sansFiltreInst = (adminBean == null
                                || adminBean.getInstitutionFiltreId() == null);
                            if (sansFiltreInst) {
                                response.sendRedirect(request.getContextPath()
                                    + "/Administrateur/dashboard.xhtml"
                                    + "?msg=selectInstitution");
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            chain.doFilter(req, res);
        } else {
            response.sendRedirect(getDashboardForRole(role, request.getContextPath()));
        }
    }

    private boolean isStaticResource(String uri) {
        return uri.contains("/javax.faces.resource/")
            || uri.contains("/jakarta.faces.resource/")
            || uri.contains("/org.primefaces/")
            || uri.contains("/faces/")
            || uri.endsWith(".css") || uri.endsWith(".js")
            || uri.endsWith(".png") || uri.endsWith(".jpg")
            || uri.endsWith(".gif") || uri.endsWith(".ico")
            || uri.endsWith(".woff") || uri.endsWith(".woff2")
            || uri.endsWith(".ttf") || uri.endsWith(".map");
    }

    /**
     * Pages admin qui nécessitent une institution sélectionnée.
     * Le dashboard et la page institutions sont accessibles sans filtre
     * (ce sont les pages où le super-admin fait son choix).
     */
    private boolean requiertInstitution(String uri) {
        if (uri == null) return false;
        // Pages accessibles sans sélection d'institution
        if (uri.contains("/dashboard.xhtml"))   return false;
        if (uri.contains("/institutions.xhtml")) return false;
        if (uri.contains("/parametres.xhtml"))  return false;
        if (uri.contains("/logs.xhtml"))         return false;
        if (uri.contains("/audit.xhtml"))        return false;
        if (uri.contains("/roles.xhtml"))        return false;
        // Toutes les autres pages admin requièrent une institution
        return uri.contains("/Administrateur/");
    }

    private boolean isAutorise(String uri, String role) {
        if (uri.contains("/Administrateur/"))          return "ADMIN".equals(role);
        if (uri.contains("/ResponsableRH/"))           return "RH".equals(role) || "ADMIN".equals(role);
        if (uri.contains("/ResponsableHierarchique/")) return "MANAGER".equals(role) || "ADMIN".equals(role) || "RH".equals(role);
        if (uri.contains("/Employes/"))                return "EMPLOYE".equals(role) || "MANAGER".equals(role) || "ADMIN".equals(role);
        if (uri.contains("/Candidat/"))                return "CANDIDAT".equals(role) || "ADMIN".equals(role);
        if (uri.contains("/Auditeur/"))                return "AUDITEUR".equals(role) || "ADMIN".equals(role);
        return false;
    }

    private String getDashboardForRole(String role, String ctx) {
        return switch (role) {
            case "ADMIN"    -> ctx + "/Administrateur/dashboard.xhtml";
            case "RH"       -> ctx + "/ResponsableRH/dashboard.xhtml";
            case "MANAGER"  -> ctx + "/ResponsableHierarchique/dashboard.xhtml";
            case "EMPLOYE"  -> ctx + "/Employes/dashboard.xhtml";
            case "CANDIDAT" -> ctx + "/Candidat/dashboard.xhtml";
            default         -> ctx + "/Authentification/login.xhtml";
        };
    }
}