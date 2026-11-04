document.addEventListener('DOMContentLoaded', function () {
  /* Menu mobile */
  var burger = document.getElementById('hamburger');
  var nav = document.getElementById('mobileNav');
  var closeBtn = document.getElementById('mobileClose');
  function toggleMenu() {
    nav.classList.toggle('open');
    burger.classList.toggle('open');
    document.body.style.overflow = nav.classList.contains('open') ? 'hidden' : '';
  }
  if (burger && nav) { burger.addEventListener('click', toggleMenu); }
  if (closeBtn) { closeBtn.addEventListener('click', toggleMenu); }

  /* Filtres + recherche : <div data-filterable> avec [data-filter], [data-cat], [data-search] */
  document.querySelectorAll('[data-filterable]').forEach(function (zone) {
    var chips = zone.querySelectorAll('[data-filter]');
    var items = zone.querySelectorAll('[data-cat]');
    var search = zone.querySelector('[data-search]');
    var empty = zone.querySelector('.empty-state');
    var current = 'all';

    function apply() {
      var q = search ? search.value.trim().toLowerCase() : '';
      var shown = 0;
      items.forEach(function (it) {
        var cats = it.getAttribute('data-cat').split(' ');
        var okCat = current === 'all' || cats.indexOf(current) > -1;
        var okQ = !q || it.textContent.toLowerCase().indexOf(q) > -1;
        it.hidden = !(okCat && okQ);
        if (okCat && okQ) { shown++; }
      });
      if (empty) { empty.hidden = shown > 0; }
    }

    chips.forEach(function (chip) {
      chip.addEventListener('click', function () {
        current = chip.getAttribute('data-filter');
        chips.forEach(function (c) {
          c.classList.toggle('active', c === chip);
          c.setAttribute('aria-pressed', c === chip ? 'true' : 'false');
        });
        apply();
      });
    });
    if (search) { search.addEventListener('input', apply); }

    var h = window.location.hash.replace('#', '');
    chips.forEach(function (c) { if (c.getAttribute('data-filter') === h) { c.click(); } });
  });
});
