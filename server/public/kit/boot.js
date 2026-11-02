/* پیش از نخستین نقاشی (همزمان در <head>؛ بی اسکریپتِ درون‌خطی، CSP): زبان (fa پیش‌فرض | prs | en | ps؛
   ‎?lang=‎ جلوتر و ماندگار) با lang/dir ِ <html>، تم و واژه‌نامهٔ همان زبان. دری (prs) = واژه‌نامهٔ فارسی
   (src) + لایهٔ kit/dari.js که kit.js فقط برای دری می‌خواهد. */
(function () {
  var d = document.documentElement;
  var DIR = { fa: 'rtl', prs: 'rtl', en: 'ltr', ps: 'rtl' };
  function ok(l) { return Object.prototype.hasOwnProperty.call(DIR, l); }
  function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function put(k, v) { try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* بی حافظه */ } }
  var q = (/[?&]lang=(fa|prs|en|ps)(&|$)/.exec(location.search) || [])[1];
  if (q) put('vill3n-lang', q);
  var lang = q || get('vill3n-lang') || 'fa';
  if (!ok(lang)) lang = 'fa';
  var src = lang == 'prs' ? 'fa' : lang;
  d.lang = lang; d.dir = DIR[lang];
  //  تا جایگزینیِ متنِ فارسیِ HTML صفحه پنهان (kit.js برمی‌دارد؛ نهایتاً ۳ ثانیه)
  if (lang !== 'fa') { d.setAttribute('data-i18n', 'wait'); setTimeout(function () { d.removeAttribute('data-i18n'); }, 3000); }
  //  یک بار دوباره؛ باز نیامد ⇒ {} (kit.js متنِ فارسیِ HTML را نگه می‌دارد، کلیدِ خام هرگز)
  function load() {
    return fetch('/kit/i18n/' + src + '.json', { credentials: 'omit' })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); });
  }
  var dict = load().catch(function () { return new Promise(function (r) { setTimeout(r, 500); }).then(load); })
    .catch(function () { return {}; });
  var m = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  function pref() { return get('vill3n-theme') || 'system'; }
  function apply() {
    var p = pref();
    d.setAttribute('data-theme', p === 'light' || p === 'dark' ? p : (m && m.matches ? 'dark' : 'light'));
  }
  apply();
  if (m && m.addEventListener) m.addEventListener('change', apply);
  window.VILL3N = {
    lang: lang, src: src, dict: dict, pref: pref, apply: apply,
    setTheme: function (p) { put('vill3n-theme', p === 'system' ? null : p); apply(); },
    setLang: function (l) { put('vill3n-lang', ok(l) ? l : 'fa'); },
  };
})();
