/* Sidebar Espace Candidat : menu mobile + panneau de notifications.
   À placer dans webapp/resources/js/ */
(function () {
    'use strict';

    function byId(id) { return document.getElementById(id); }

    var sidebar, overlay, toggle, bell, panel;

    function setSidebar(open) {
        if (!sidebar) { return; }
        sidebar.classList.toggle('open', open);
        if (overlay) { overlay.classList.toggle('active', open); }
        if (toggle) {
            toggle.classList.toggle('open', open);
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            toggle.setAttribute('aria-label', open ? 'Fermer le menu' : 'Ouvrir le menu');
        }
        document.body.style.overflow = open ? 'hidden' : '';
    }

    function setPanel(open) {
        if (!panel) { return; }
        panel.classList.toggle('open', open);
        if (bell) { bell.setAttribute('aria-expanded', open ? 'true' : 'false'); }
    }

    function init() {
        sidebar = byId('mainSidebar');
        overlay = byId('sidebarOverlay');
        toggle  = byId('sidebarToggle');
        bell    = byId('notifBell');
        panel   = byId('notifPanelCandidat');
        if (!sidebar) { return; }

        if (toggle)  { toggle.addEventListener('click', function () { setSidebar(!sidebar.classList.contains('open')); }); }
        if (overlay) { overlay.addEventListener('click', function () { setSidebar(false); }); }

        /* Un clic sur un lien du menu referme la sidebar (mobile) */
        sidebar.querySelectorAll('.nav-item').forEach(function (a) {
            a.addEventListener('click', function () { setSidebar(false); });
        });

        /* Cloche : ouvre/ferme le panneau */
        if (bell && panel) {
            bell.addEventListener('click', function (e) {
                e.stopPropagation();
                setPanel(!panel.classList.contains('open'));
            });
            /* Clic en dehors du panneau = fermeture */
            document.addEventListener('click', function (e) {
                if (panel.classList.contains('open') && !panel.contains(e.target)) { setPanel(false); }
            });
        }

        /* Échap : ferme panneau et menu */
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') { return; }
            if (panel && panel.classList.contains('open')) { setPanel(false); if (bell) { bell.focus(); } }
            else if (sidebar.classList.contains('open')) { setSidebar(false); if (toggle) { toggle.focus(); } }
        });

        /* Retour en mode bureau : on referme le menu mobile */
        window.addEventListener('resize', function () {
            if (window.innerWidth > 768) { setSidebar(false); }
        });
    }

    /* Appelée par oncomplete d'une notification : redirige vers le lien associé */
    window.allerVersNotif = function (url, hasLink) {
        if (hasLink) { window.location.href = url; }
    };

    /* Compatibilité avec l'ancien code */
    window.toggleSidebar = function () { setSidebar(!(sidebar && sidebar.classList.contains('open'))); };
    window.closeSidebar  = function () { setSidebar(false); };

    if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); }
    else { init(); }
})();