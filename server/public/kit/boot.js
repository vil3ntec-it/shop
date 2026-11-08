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
  //  WEB-15: ویترین (data-pre) داده و catalog.js را هم‌زمان با JS می‌خواهد؛ K.api هر کدام را یک بار با همین سرآیندها برمی‌دارد
  var pre = {}, me = document.currentScript, hs = location.hash;
  if (me && me.hasAttribute('data-pre')) try {
    var p = location.pathname.split('/').filter(Boolean), now = Date.now();
    var h = /^#\/p\/([^/]+)$/.exec(hs) || (!hs || hs == '#/') && /\/p\/([^/]+)\/?$/.exec(location.pathname);
    var b = '/api/store/' + encodeURIComponent((p[0] == 'store' && p[1] || location.hostname.split('.')[0]).toLowerCase());
    var H = { Accept: 'application/json', 'Accept-Language': lang };
    var js = function (k) { try { return JSON.parse(get(k)) || {}; } catch (e) { return {}; } };
    var t = js('store-ticket'), ss = js('store-session');
    if (t.ticket && t.expiresAt > now) H['X-Store-Ticket'] = t.ticket;
    if (ss.token && ss.expiresAt > now) H['X-Store-Session'] = ss.token;
    [b, b + '/products?limit=40&sort=', b + '/highlights', h && b + '/products/' + h[1]].forEach(function (u) {
      if (u) (pre[u] = { h: JSON.stringify(H), r: fetch(u, { headers: H, credentials: 'omit', cache: 'no-store' }) }).r['catch'](function () {});
    });
    ['catalog', h && 'product'].forEach(function (n) {
      var l = n && document.createElement('link');
      if (l) { l.rel = 'preload'; l.as = 'script'; l.href = '/store/' + n + '.js'; document.head.appendChild(l); }
    });
  } catch (e) { pre = {}; } // بی پیش‌خوان همان راهِ پیشین
  window.VILL3N = {
    lang: lang, src: src, dict: dict, pref: pref, apply: apply, pre: pre,
    setTheme: function (p) { put('vill3n-theme', p === 'system' ? null : p); apply(); },
    setLang: function (l) { put('vill3n-lang', ok(l) ? l : 'fa'); },
  };
})();
