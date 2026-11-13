/*
 * کیتِ وبِ خریدار — پنجرهٔ تنظیمات: زبان، تم و شمارهٔ تأییدشدهٔ همین دستگاه. دیربار: فقط با زدنِ دکمهٔ تنظیمات/زبان
 * (`K.settings` در kit.js) بار می‌شود، نه در بارِ نخستِ ویترین و بازارچه (بودجهٔ WEB-15، §۳.۱۰).
 * مانندِ kit.js: ES5، بی innerHTML و هر متن با textContent.
 */
(function () {
  'use strict';
  var K = window.K;
  var V = window.VILL3N || {};

  /** زبانِ تازه ⇒ بارگذاریِ دوباره بی ‎?lang=‎ (همان نشانی و همان #مسیر). */
  function setLang(l) {
    if (V.setLang) V.setLang(l);
    var q = location.search.replace(/([?&])lang=[^&]*(&|$)/, '$1').replace(/[?&]$/, '');
    if (q == location.search) location.reload(); else location.replace(location.pathname + q + location.hash);
  }
  var LANGS = [['fa', 'فارسی'], ['prs', 'دری'], ['en', 'English'], ['ps', 'پښتو']];

  function radios(name, list, cur, on, label) {
    var seg = K.el('div', { class: 'seg', attrs: { role: 'radiogroup', 'aria-labelledby': label } });
    list.forEach(function (x) {
      var r = K.el('input', { attrs: { type: 'radio', name: name, value: x[0] } });
      r.checked = cur === x[0];
      r.addEventListener('change', function () { on(x[0]); });
      seg.appendChild(K.el('label', { attrs: x[2] ? { lang: x[0], dir: x[0] === 'en' ? 'ltr' : 'rtl' } : {} }, r, x[1]));
    });
    return seg;
  }
  window.KitSettings = function (focusLang) {
    var d = K.el('dialog', { class: 'confirm', attrs: { 'aria-labelledby': 'set-title' } });
    var close = K.el('button', { class: 'btn btn-sm', text: K.t('close'), attrs: { type: 'button' } });
    var lang = radios('lang', LANGS.map(function (x) { return [x[0], x[1], true]; }), K.lang, setLang, 'set-lang');
    var theme = radios('theme', [['light', K.t('theme.light')], ['dark', K.t('theme.dark')], ['system', K.t('theme.system')]], K.theme.pref(), K.theme.set, 'set-theme');
    d.append(K.el('h2', { text: K.t('set.title'), attrs: { id: 'set-title' } }),
      K.el('p', { class: 'label', text: K.t('set.lang'), attrs: { id: 'set-lang' } }), lang,
      K.el('p', { class: 'label section', text: K.t('set.theme'), attrs: { id: 'set-theme' } }), theme);
    var t = K.ticket();
    if (t) {
      var out = K.el('button', { class: 'btn btn-ghost btn-sm', text: K.t('set.out'), attrs: { type: 'button' } });
      out.addEventListener('click', function () { K.setTicket(null); d.close(); location.reload(); });
      d.append(K.el('div', { class: 'inner section' }, K.el('p', { class: 'small muted', text: K.t('set.phone') }),
        K.el('div', { class: 'spread' }, K.el('b', { class: 'ltr', text: t.email }), out)));
    }
    d.append(K.el('div', { class: 'row' }, close));
    document.body.appendChild(d);
    var done = function () { d.close(); d.remove(); };
    close.addEventListener('click', done);
    d.addEventListener('cancel', function (e) { e.preventDefault(); done(); });
    d.showModal();
    ((focusLang === true ? lang : theme).querySelector('input:checked') || close).focus();
  };
})();
