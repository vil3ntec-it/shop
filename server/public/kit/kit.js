/*
 * کیتِ مشترکِ وبِ خریدار (بازارچه + ویترین). بی وابستگی و بی innerHTML برای داده:
 * هر متنی که از سرور می‌آید با textContent می‌نشیند (XSS). CSP: فقط همین مبدأ.
 * سه زبان (fa پیش‌فرض، en، ps): هر متنِ صفحه از واژه‌نامهٔ /kit/i18n/<زبان>.json با
 * K.t(کلید)؛ HTML با data-t / data-ta (aria-label) / data-tp (placeholder).
 * حافظهٔ مرورگر ممکن است بسته باشد؛ صفحه بی آن هم کار می‌کند.
 */
(function () {
  'use strict';
  var K = window.K = {};
  var SVGNS = 'http://www.w3.org/2000/svg';
  var V = window.VILL3N || { lang: 'fa', dict: Promise.resolve({}) };
  var LANG = K.lang = V.lang;
  var DICT = {};
  var own = function (o, k) { return Object.prototype.hasOwnProperty.call(o, k); };
  //  بی واژه‌نامه: واژه‌های پایه (= fa.json، web-i18n)، بقیه خالی؛ کلیدِ خام هرگز
  K.FB = { 'money': '{n} افغانی', 'cur': 'افغانی', 'sep': '، ', 'err.generic': 'خطا؛ دوباره بزنید.', 'err.rate': 'درخواست‌ها زیاد شد؛ کمی بعد دوباره بزنید.', 'close': 'بستن', 'yes': 'بله', 'no': 'نه', 'add.one': 'افزودنِ {name} به سبد', 'stock.unk': 'موجودی در حال بررسی است', 'stock.out': 'ناموجود', 'st.open': 'باز است', 'st.closed': 'بسته است', 'unit': 'عدد', 'all': 'همه', 'ck.title': 'ثبتِ سفارش', 'ck.sending': 'در حالِ ثبت…', 'cod': 'نقد هنگامِ تحویل', 'free': 'رایگان', 'ship.fee': 'ارسال {fee}', 'ship.free': 'ارسالِ رایگان', 'pickup': 'دریافت از دکان', 'min.order': 'حداقلِ سفارش {m}', 'qty.less': 'یکی کمتر {name}', 'qty.more': 'یکی بیشتر {name}', 'ps.add': 'افزودن به سبد — {p}', 'bar.label': '، {n} قلم، جمع {sum} — باز کردنِ سبد', 'line.label': '{name}، {q}، {sum} — تغییرِ تعداد', 'od.title': 'سفارش', 'od.placed': 'سفارش ثبت شد. دکان آن را بررسی می‌کند؛ همین صفحه وضعیت را نشان می‌دهد.', 'od.no': 'سفارشِ شمارهٔ {n}', 'my.orders': 'سفارش‌های من', 'v.sent': 'کد فرستاده شد.', 'v.ok': 'شماره تأیید شده است.', 'err.bad_phone': 'شمارهٔ موبایل درست نیست.', 'cart.min': 'برای ادامه، سبد به حداقلِ سفارش برسد.', 'order.new': 'ثبت شد — منتظرِ تأییدِ دکان', 'order.accepted': 'دکان قبول کرد', 'order.rejected': 'دکان نپذیرفت', 'order.ready_for_pickup': 'آمادهٔ دریافت از دکان', 'order.out_for_delivery': 'در راه', 'order.delivered': 'تحویل شد', 'order.cancelled': 'لغو شد', 'order.cancelled_by_customer': 'شما لغو کردید', 'ck.submit': 'ثبتِ سفارش — {sum}', 'ck.fee': 'هزینهٔ ارسال', 'ship': 'ارسال', 'p.per': 'هر {unit}', 'acc.orders': 'سفارش‌های من', 'od.sub': 'جمعِ کالاها', 'od.total': 'مبلغِ نهایی', 'od.due': 'مانده (نقد هنگامِ تحویل)' };
  //  رقمِ فارسی برای fa و ps (پشتوی افغانستان همان ۰–۹ و ماهِ خورشیدی)، لاتین برای en
  var LOC = LANG === 'en' ? 'en-GB' : 'fa-AF';

  K.$ = function (id) { return document.getElementById(id); };

  /** متنِ همین زبان؛ {نام} با v.نام پر می‌شود. کلیدِ ناموجود ⇒ واژهٔ پایه یا ''. */
  K.t = function (key, v) {
    //  مفرد (en/ps): «‎.1‎» وقتی شمار یک است
    if (v && (v.n === '1' || v.n === '۱') && own(DICT, key + '.1')) key += '.1';
    var s = own(DICT, key) ? DICT[key] : own(K.FB, key) ? K.FB[key] : '';
    return v ? s.replace(/\{(\w+)\}/g, function (m, k) { return v[k] === undefined ? m : v[k]; }) : s;
  };
  K.has = function (key) { return own(DICT, key); };

  /** el('div', { class, text, attrs, on, hidden }, ...children) */
  K.el = function (tag, p) {
    var e = document.createElement(tag);
    p = p || {};
    if (p.class) e.className = p.class;
    if (p.text !== undefined && p.text !== null) e.textContent = p.text;
    if (p.attrs) Object.keys(p.attrs).forEach(function (k) { if (p.attrs[k] !== null && p.attrs[k] !== undefined) e.setAttribute(k, p.attrs[k]); });
    if (p.on) Object.keys(p.on).forEach(function (k) { e.addEventListener(k, p.on[k]); });
    if (p.hidden) e.hidden = true;
    for (var i = 2; i < arguments.length; i++) {
      var c = arguments[i];
      if (c === null || c === undefined || c === false) continue;
      e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    }
    return e;
  };
  K.clear = function (e) { while (e.firstChild) e.removeChild(e.firstChild); return e; };

  // ── نشانه‌ها (خطی، ۲۴×۲۴)؛ نشانه‌های جهت‌دار در en آینه می‌شوند ──
  var ICONS = {
    search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z M20 20l-3.5-3.5',
    bag: 'M5.5 8h13l-1 12.5h-11L5.5 8z M9 8V7a3 3 0 0 1 6 0v1',
    store: 'M4 10v10h16V10 M3.5 10L5 4h14l1.5 6 M3.5 10a2.8 2.8 0 0 0 5.6 0a2.8 2.8 0 0 0 5.6 0a2.8 2.8 0 0 0 5.6 0 M10 20v-5h4v5',
    back: 'M5 12h14 M13 6l6 6-6 6',
    close: 'M6 6l12 12 M18 6L6 18',
    plus: 'M12 5v14 M5 12h14',
    minus: 'M5 12h14',
    clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 7v5l3 2',
    truck: 'M2.5 6h11v10h-11z M13.5 9.5h4l3 3.5v3h-7 M6.5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M16.5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
    pin: 'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z M12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
    phone: 'M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z',
    check: 'M5 12.5l4.5 4.5L19 7.5',
    info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 11v5 M12 8h.01',
    alert: 'M12 3.5l9 16h-18l9-16z M12 10v4 M12 17h.01',
    receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3z M9 8h6 M9 12h6',
    locate: 'M12 2.5v3 M12 18.5v3 M2.5 12h3 M18.5 12h3 M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10z',
    chev: 'M15 6l-6 6 6 6',
    user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 21a7.5 7.5 0 0 1 15 0',
    cash: 'M3 7h18v10H3z M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M6 10v4 M18 10v4',
    box: 'M3.5 8L12 3.5 20.5 8v8L12 20.5 3.5 16V8z M3.5 8L12 12.5 20.5 8 M12 12.5v8',
    home: 'M3.5 10.5L12 4l8.5 6.5 M5.5 9v11h13V9 M10 20v-6h4v6',
    gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 13.5l1.6 1.2-2 3.4-1.9-.7a7 7 0 0 1-2.1 1.2L14.7 21h-5.4l-.3-2.4a7 7 0 0 1-2.1-1.2l-1.9.7-2-3.4 1.6-1.2a7 7 0 0 1 0-3l-1.6-1.2 2-3.4 1.9.7a7 7 0 0 1 2.1-1.2L9.3 3h5.4l.3 2.4a7 7 0 0 1 2.1 1.2l1.9-.7 2 3.4-1.6 1.2a7 7 0 0 1 0 3z',
    moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z',
    sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4',
    globe: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M3 12h18 M12 3c2.5 2.6 3.7 5.6 3.7 9s-1.2 6.4-3.7 9c-2.5-2.6-3.7-5.6-3.7-9S9.5 5.6 12 3z',
    grid: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
    tag: 'M3.5 12.5V4h8.5l8.5 8.5-8.5 8.5z M8 8.5h.01',
    flame: 'M12 21a6 6 0 0 0 6-6c0-4-3-6-4-9-1 2-2 3-3.5 3.5C9 8 8.5 7 8.5 5.5 6.5 7.5 6 10 6 15a6 6 0 0 0 6 6z',
    shield: 'M12 3l7.5 3v5.5c0 4.5-3.2 8-7.5 9.5-4.3-1.5-7.5-5-7.5-9.5V6z M9 12l2 2 4-4',
  };
  var DIRECTED = { back: 1, chev: 1 };
  K.ICONS = ICONS;
  K.icon = function (name, cls) {
    var s = document.createElementNS(SVGNS, 'svg');
    s.setAttribute('viewBox', '0 0 24 24'); s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor');
    s.setAttribute('stroke-width', '2'); s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.setAttribute('aria-hidden', 'true'); s.setAttribute('focusable', 'false');
    if (cls || DIRECTED[name]) s.setAttribute('class', (cls || '') + (DIRECTED[name] ? ' dir' : ''));
    var p = document.createElementNS(SVGNS, 'path');
    p.setAttribute('d', ICONS[name] || ICONS.info);
    s.appendChild(p);
    return s;
  };

  // ── عدد، پول، زمان (به زبانِ صفحه) ──
  //  fa/ps: رقمِ فارسی، هزارگانِ «٬» و اعشارِ «٫»؛ en: رقم و نشانه‌های لاتین
  K.fa = function (n, d) {
    var s = Number(n).toLocaleString(LOC, { maximumFractionDigits: d === undefined ? 2 : d });
    return LANG === 'en' ? s : s.replace(/,/g, '٬').replace(/\./g, '٫');
  };
  K.money = function (minor) { return K.t('money', { n: K.fa(Math.round(Number(minor)) / 100) }); };
  K.qty = function (milli) { return K.fa(Number(milli) / 1000, 3); };
  /** همان گردکردنِ سرور: هر قلم به افغانیِ کامل (order-states.lineTotalMinor). */
  K.lineTotal = function (qtyMilli, priceMinor) { return Math.floor((qtyMilli * priceMinor + 50000) / 100000) * 100; };
  K.hm = function (min) { return K.fa(Math.floor(min / 60), 0) + ':' + K.fa(min % 60, 0).padStart(2, K.fa(0, 0)); };
  K.clock = function (at, tz) {
    try { return new Date(at).toLocaleTimeString(LOC, { timeZone: tz || undefined, hour: '2-digit', minute: '2-digit', hour12: false }); } catch (e) { return ''; }
  };
  K.when = function (at) {
    try { return new Date(at).toLocaleString(LOC, { dateStyle: 'medium', timeStyle: 'short', hour12: false }); } catch (e) { return ''; }
  };

  // ── حافظهٔ مرورگر ──
  K.load = function (key, def) {
    try { var v = JSON.parse(localStorage.getItem(key)); return v === null || v === undefined ? def : v; } catch (e) { return def; }
  };
  K.save = function (key, v) {
    try { if (v === null) localStorage.removeItem(key); else localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* بی حافظه */ }
  };
  K.loadS = function (key) { try { return JSON.parse(sessionStorage.getItem(key)); } catch (e) { return null; } };
  K.saveS = function (key, v) { try { sessionStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* بی حافظه */ } };

  // ── بلیتِ شمارهٔ تأییدشده (۲۳.۲) — بینِ بازارچه و همهٔ ویترین‌ها مشترک ──
  var KEY_TICKET = 'store-ticket';
  K.ticket = function () {
    var t = K.load(KEY_TICKET, null);
    return t && t.ticket && t.expiresAt > Date.now() ? t : null;
  };
  K.setTicket = function (t) { K.save(KEY_TICKET, t ? { phone: t.phone, ticket: t.ticket, expiresAt: t.expiresAt } : null); };
  K.normPhone = function (v) {
    var s = String(v || '').replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[\s\-().]/g, '').replace(/^00/, '+');
    if (!s) return '';
    if (s.charAt(0) === '0') s = '+93' + s.slice(1);
    else if (s.charAt(0) !== '+') s = '+93' + s;
    return s;
  };
  K.localPhone = function (p) { return String(p || '').replace(/^\+93/, '0'); };

  // ── API: پیامِ خطای شناخته از واژه‌نامه (نه متنِ خامِ فارسیِ سرور در en/ps) ──
  K.api = function (url, opts) {
    opts = opts || {};
    var headers = { Accept: 'application/json', 'Accept-Language': LANG };
    if (opts.body) headers['Content-Type'] = 'application/json';
    var t = K.ticket();
    if (t) headers['X-Store-Ticket'] = t.ticket;
    if (opts.key) headers['Idempotency-Key'] = opts.key;
    return fetch(url, {
      method: opts.method || 'GET', headers: headers, body: opts.body ? JSON.stringify(opts.body) : undefined,
      credentials: 'omit', cache: 'no-store',
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (res.ok) return body;
        var e = (body && body.error) || {};
        var code = e.code || '';
        var msg = res.status === 429 && !K.has('err.' + code) ? K.t('err.rate')
          : K.has('err.' + code) ? K.t('err.' + code) : (LANG === 'fa' && e.message ? e.message : K.t('err.generic'));
        var err = new Error(msg);
        err.status = res.status; err.code = code; err.data = e;
        throw err;
      });
    }, function () {
      var err = new Error(K.t('err.network'));
      err.status = 0; err.code = 'network';
      throw err;
    });
  };
  /** کلیدِ یکتایی تا وقتی بدنه همان است می‌ماند (زدنِ دوباره پس از قطعِ شبکه). */
  K.keyFor = function (scope, body) {
    var json = JSON.stringify(body);
    var name = 'key:' + scope;
    var p = K.loadS(name);
    if (p && p.json === json) return p.key;
    var rnd = window.crypto && crypto.randomUUID ? crypto.randomUUID()
      : Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    var key = 'web-' + rnd;
    K.saveS(name, { json: json, key: key });
    return key;
  };
  K.dropKey = function (scope) { K.saveS('key:' + scope, null); };

  // ── وضعیتِ دکان (۳۱.۱): سه چیزِ جدا، بی زمانِ ساختگی ──
  K.status = function (s) {
    if (!s) return { text: '', kind: 'off' };
    switch (s.badge) {
      case 'open_online': return { text: K.t('st.open'), kind: 'ok' };
      case 'open_unconfirmed': return { text: K.t('st.unconfirmed'), kind: 'warn' };
      case 'declared_until': return { text: K.t('st.until', { t: K.clock(s.declaredUntil, s.timezone) }), kind: 'ok' };
      case 'closed': return { text: K.t('st.closed'), kind: 'off' };
      case 'offline': return { text: K.t('st.offline'), kind: 'off' };
      default: return { text: '', kind: 'off' };
    }
  };
  K.pill = function (s) { var st = K.status(s); return K.el('span', { class: 'pill ' + st.kind, text: st.text }); };
  K.accept = function (a) { return a ? K.t('accept.' + a) : ''; };
  K.hours = function (s) {
    if (!s) return '';
    if (s.holiday) return K.t('hours.off');
    if (s.today && s.today.length) return K.t('hours.today', { h: s.today.map(function (r) { return K.hm(r[0]) + '–' + K.hm(r[1]); }).join(K.t('sep')) });
    return s.hoursSet ? K.t('hours.off') : '';
  };

  // ── آواتارِ دکان: رنگِ خودِ دکان، وگرنه یکی از شش رنگِ آرام ──
  function hash(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return Math.abs(h); }
  K.avatar = function (title, accent, seed, size) {
    var a = K.el('span', { class: 'avatar' + (size ? ' ' + size : ''), text: String(title || '?').trim().slice(0, 1), attrs: { 'aria-hidden': 'true' } });
    if (/^#[0-9a-f]{6}$/i.test(accent || '')) {
      var n = parseInt(accent.slice(1), 16);
      var lum = (0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255;
      a.style.setProperty('background', accent);
      a.style.setProperty('color', lum > 0.6 ? '#171A26' : '#FFFFFF');
    } else {
      a.classList.add('c' + (1 + (hash(String(seed || title || '')) % 6)));
    }
    return a;
  };

  // ── برگه (sheet) با تمرکز، Esc و قفلِ پیمایش ──
  var openSheet = null;
  K.openSheet = function (sheet, onClose) {
    if (openSheet && openSheet.el !== sheet) K.closeSheet(true);
    var scrim = K.$('scrim');
    var last = document.activeElement;
    openSheet = { el: sheet, onClose: onClose, last: last };
    scrim.hidden = false; sheet.hidden = false;
    document.body.classList.add('lock');
    requestAnimationFrame(function () { scrim.classList.add('on'); sheet.classList.add('on'); });
    var f = sheet.querySelector('[data-autofocus]') || sheet.querySelector('button, a, input');
    setTimeout(function () { if (f) f.focus(); }, 60);
  };
  K.closeSheet = function (silent) {
    if (!openSheet) return;
    var s = openSheet; openSheet = null;
    var scrim = K.$('scrim');
    s.el.classList.remove('on'); scrim.classList.remove('on');
    document.body.classList.remove('lock');
    setTimeout(function () { if (!openSheet) { s.el.hidden = true; scrim.hidden = true; } else if (openSheet.el !== s.el) s.el.hidden = true; }, 220);
    if (s.last && s.last.focus && document.contains(s.last)) s.last.focus();
    if (!silent && s.onClose) s.onClose();
  };
  K.sheetOpen = function () { return openSheet ? openSheet.el : null; };
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && openSheet) { e.preventDefault(); K.closeSheet(); }
    if (e.key === 'Tab' && openSheet) {
      var f = openSheet.el.querySelectorAll('button:not([disabled]), a[href], input:not([disabled]), textarea');
      if (!f.length) return;
      var first = f[0]; var lastF = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); lastF.focus(); }
      else if (!e.shiftKey && document.activeElement === lastF) { e.preventDefault(); first.focus(); }
    }
  });
  document.addEventListener('click', function (e) { if (e.target && e.target.id === 'scrim') K.closeSheet(); });

  // ── پرسشِ تأیید (dialog، نه confirm ِ مرورگر) ──
  K.confirm = function (text, okText) {
    return new Promise(function (resolve) {
      var d = K.el('dialog', { class: 'confirm', attrs: { 'aria-label': text } });
      var no = K.el('button', { class: 'btn btn-ghost btn-sm', text: K.t('no'), attrs: { type: 'button' } });
      var yes = K.el('button', { class: 'btn btn-danger btn-sm', text: okText || K.t('yes'), attrs: { type: 'button' } });
      d.append(K.el('p', { text: text }), K.el('div', { class: 'row' }, no, yes));
      document.body.appendChild(d);
      var done = function (v) { d.close(); d.remove(); resolve(v); };
      no.addEventListener('click', function () { done(false); });
      yes.addEventListener('click', function () { done(true); });
      d.addEventListener('cancel', function (e) { e.preventDefault(); done(false); });
      d.showModal();
      no.focus();
    });
  };

  var toastTimer = 0;
  K.toast = function (text) {
    var t = K.$('toast');
    if (!t) { t = K.el('div', { class: 'toast', attrs: { id: 'toast', role: 'status', 'aria-live': 'polite' } }); document.body.appendChild(t); }
    t.textContent = text; t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 2600);
  };

  K.note = function (box, kind, text) {
    K.clear(box);
    if (!text) { box.hidden = true; return; }
    box.className = 'note ' + (kind || '');
    box.append(K.icon(kind === 'ok' ? 'check' : (kind === 'err' || kind === 'warn' ? 'alert' : 'info')), K.el('span', { text: text }));
    box.hidden = false;
  };

  // ── بی اینترنت (WEB-14): نوارِ کوچک؛ سبد روی همین دستگاه می‌ماند ──
  K.offline = function () {
    var b = K.el('div', { class: 'offline', text: K.t('offline'), attrs: { role: 'status' }, hidden: navigator.onLine !== false });
    document.body.insertBefore(b, document.body.firstChild);
    window.addEventListener('online', function () { b.hidden = true; });
    window.addEventListener('offline', function () { b.hidden = false; });
  };

  // ── سبدهای مهمان (هر دکان جدا؛ همان کلیدِ ویترینِ پیشین) ──
  K.cartKey = function (slug) { return 'store-cart:' + slug; };
  K.cartMetaKey = function (slug) { return 'store-cart-meta:' + slug; };

  // ── وضعیتِ سفارش برای خریدار (همان معنا در همه‌جا) ──
  K.order = function (st) { return K.t('order.' + st) || st; };
  K.reason = function (r) { return r && K.has('reason.' + r) ? K.t('reason.' + r) : ''; };
  var STOP = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'returned_to_store', 'restocked'];
  K.orderKind = function (status) {
    if (STOP.indexOf(status) >= 0) return 'err';
    if (['delivered', 'picked_up', 'refunded_partial', 'refunded_full'].indexOf(status) >= 0) return 'ok';
    if (status === 'new') return 'warn';
    if (['accepted', 'picking', 'packed', 'ready_for_pickup'].indexOf(status) >= 0) return 'prep';
    if (['return_requested', 'delivery_failed'].indexOf(status) >= 0) return 'review';
    return 'info';
  };
  /** پله‌های پیشرفتِ سفارش، هر کدام done | now | todo | stop. */
  K.orderSteps = function (o) {
    var pickup = o.fulfillment === 'pickup';
    var steps = [
      { label: 'step.placed', keys: ['new'] },
      { label: 'step.accepted', keys: ['accepted'] },
      { label: 'step.prep', keys: ['picking', 'packed'] },
      pickup ? { label: 'step.ready', keys: ['ready_for_pickup'] } : { label: 'step.way', keys: ['out_for_delivery', 'delivery_failed', 'retry_scheduled'] },
      pickup ? { label: 'step.picked', keys: ['picked_up', 'return_requested', 'refunded_partial', 'refunded_full'] }
        : { label: 'step.delivered', keys: ['delivered', 'return_requested', 'refunded_partial', 'refunded_full'] },
    ];
    var at = {};
    (o.timeline || []).forEach(function (e) { at[e.status] = e.at; });
    var stopped = STOP.indexOf(o.status) >= 0;
    var idx = 0;
    steps.forEach(function (s, i) { s.keys.forEach(function (k) { if (at[k] || o.status === k) idx = Math.max(idx, i); }); });
    var out = steps.map(function (s, i) {
      var t = null; s.keys.forEach(function (k) { if (at[k]) t = at[k]; });
      var state = i < idx ? 'done' : (i === idx ? (stopped ? 'done' : 'now') : 'todo');
      if (i === idx && ['delivered', 'picked_up'].indexOf(o.status) >= 0) state = 'done';
      return { label: K.t(s.label), at: t, state: state };
    });
    //  سفارشِ ردشده/لغوشده: پله‌های بعدی نیست، فقط همان پایان
    return stopped ? out.slice(0, idx + 1).concat([{ label: K.order(o.status), at: at[o.status] || o.updatedAt, state: 'stop' }]) : out;
  };

  // ── تم و زبان ──
  K.theme = {
    pref: function () { return V.pref ? V.pref() : 'system'; },
    dark: function () { return document.documentElement.getAttribute('data-theme') === 'dark'; },
    set: function (p) { if (V.setTheme) V.setTheme(p); themeButtons(); },
  };
  function themeButtons() {
    var dark = K.theme.dark();
    Array.prototype.forEach.call(document.querySelectorAll('[data-act="theme"]'), function (b) {
      K.clear(b).appendChild(K.icon(dark ? 'sun' : 'moon'));
      b.setAttribute('aria-pressed', String(dark));
    });
  }
  /** زبانِ تازه ⇒ بارگذاریِ دوباره بی ‎?lang=‎ (همان نشانی و همان #مسیر). */
  K.setLang = function (l) {
    if (V.setLang) V.setLang(l);
    var q = location.search.replace(/([?&])lang=[^&]*(&|$)/, '$1').replace(/[?&]$/, '');
    location.replace(location.pathname + q + location.hash);
  };
  var LANGS = [['fa', 'فارسی'], ['en', 'English'], ['ps', 'پښتو']];

  // ── تنظیمات (پنجره): زبان، تم و شمارهٔ تأییدشدهٔ همین دستگاه ──
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
  K.settings = function (focusLang) {
    var d = K.el('dialog', { class: 'confirm', attrs: { 'aria-labelledby': 'set-title' } });
    var close = K.el('button', { class: 'btn btn-sm', text: K.t('close'), attrs: { type: 'button' } });
    var lang = radios('lang', LANGS.map(function (x) { return [x[0], x[1], true]; }), LANG, K.setLang, 'set-lang');
    var theme = radios('theme', [['light', K.t('theme.light')], ['dark', K.t('theme.dark')], ['system', K.t('theme.system')]], K.theme.pref(), K.theme.set, 'set-theme');
    d.append(K.el('h2', { text: K.t('set.title'), attrs: { id: 'set-title' } }),
      K.el('p', { class: 'label', text: K.t('set.lang'), attrs: { id: 'set-lang' } }), lang,
      K.el('p', { class: 'label section', text: K.t('set.theme'), attrs: { id: 'set-theme' } }), theme);
    var t = K.ticket();
    if (t) {
      var out = K.el('button', { class: 'btn btn-ghost btn-sm', text: K.t('set.out'), attrs: { type: 'button' } });
      out.addEventListener('click', function () { K.setTicket(null); d.close(); location.reload(); });
      d.append(K.el('div', { class: 'inner section' }, K.el('p', { class: 'small muted', text: K.t('set.phone') }),
        K.el('div', { class: 'spread' }, K.el('b', { class: 'ltr', text: K.localPhone(t.phone) }), out)));
    }
    d.append(K.el('div', { class: 'row' }, close));
    document.body.appendChild(d);
    var done = function () { d.close(); d.remove(); };
    close.addEventListener('click', done);
    d.addEventListener('cancel', function (e) { e.preventDefault(); done(); });
    d.showModal();
    ((focusLang === true ? lang : theme).querySelector('input:checked') || close).focus();
  };

  // ── پوسته: نوارِ کناری/پایین، نشانه‌ها، تم، زبان، حساب، خطِ زیرِ نوارِ بالا ──
  K.nav = function (cur) {
    Array.prototype.forEach.call(document.querySelectorAll('[data-nav]'), function (a) {
      if (a.getAttribute('data-nav') === cur) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
    });
  };
  K.account = function () {
    var a = K.$('account');
    if (!a) return;
    var t = K.ticket();
    K.clear(a).append(K.el('span', { class: 'who' }, K.icon('user')),
      K.el('span', { class: 'lbl' }, t ? K.t('acc.mine') : K.t('acc.orders'), K.el('small', { class: t ? 'ltr' : '', text: t ? K.localPhone(t.phone) : K.t('acc.verified') })));
  };
  /** متنِ ثابتِ HTML به زبانِ صفحه: data-t (متن)، data-ta (aria-label)، data-tp (placeholder). */
  function translate(root) {
    var all = function (sel, fn) { Array.prototype.forEach.call(root.querySelectorAll(sel), fn); };
    //  کلیدِ نیامده ⇒ متنِ فارسیِ خودِ HTML می‌ماند
    var tr = function (a, set) { all('[' + a + ']', function (x) { var k = x.getAttribute(a); if (K.has(k)) set(x, K.t(k)); }); };
    tr('data-t', function (x, s) { x.textContent = s; });
    tr('data-ta', function (x, s) { x.setAttribute('aria-label', s); });
    tr('data-tp', function (x, s) { x.setAttribute('placeholder', s); });
  }
  var ready = Promise.all([
    V.dict.then(function (d) { DICT = d || {}; }),
    //  کیت «defer» است: تا DOMContentLoaded صبر می‌کند تا اسکریپتِ صفحه هم اجرا شده باشد
    new Promise(function (r) { if (document.readyState === 'complete') r(); else document.addEventListener('DOMContentLoaded', r); }),
  ]).then(function () {
    translate(document);
    document.documentElement.removeAttribute('data-i18n');
    Array.prototype.forEach.call(document.querySelectorAll('[data-i]'), function (x) { x.insertBefore(K.icon(x.getAttribute('data-i')), x.firstChild); });
    Array.prototype.forEach.call(document.querySelectorAll('[data-act="theme"]'), function (b) {
      b.addEventListener('click', function () { K.theme.set(K.theme.dark() ? 'light' : 'dark'); });
    });
    Array.prototype.forEach.call(document.querySelectorAll('[data-act="settings"]'), function (b) { b.addEventListener('click', K.settings); });
    Array.prototype.forEach.call(document.querySelectorAll('[data-act="lang"]'), function (b) {
      b.appendChild(K.icon('globe'));
      b.addEventListener('click', function () { K.settings(true); });
    });
    themeButtons();
    if (window.matchMedia) window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', themeButtons);
    K.account();
    var bar = document.querySelector('.topbar');
    var sur = K.$('surface');
    var onScroll = function () { if (bar) bar.classList.toggle('scrolled', (document.body.classList.contains('phone-cart') && sur ? sur.scrollTop : window.scrollY) > 4); };
    window.addEventListener('scroll', onScroll, { passive: true });
    if (sur) sur.addEventListener('scroll', onScroll, { passive: true });
    var cap = K.$('surface-cap');
    if (cap) cap.addEventListener('click', function () { if (cartCb) cartCb(); });
  });
  /** کارِ صفحه پس از DOM و واژه‌نامه (به جای DOMContentLoaded). */
  K.ready = function (fn) { ready.then(fn); };

  // ── کارتِ کالا (بازارچه و ویترین) ──
  /** ۳۲.۳: پیامِ موجودی ساده و دقیق؛ نامعلوم هرگز «موجود» نیست. */
  K.stockOf = function (p) {
    var s = (p && p.stock) || { status: 'unverified' };
    switch (s.status) {
      case 'in_stock': return { text: K.t('stock.in'), kind: 'ok', orderable: true, known: true };
      case 'in_warehouse': return { text: s.prepMinutes ? K.t('stock.wh_prep', { m: K.fa(s.prepMinutes, 0) }) : K.t('stock.wh'), kind: 'info', orderable: true, known: true };
      case 'out_of_stock': return { text: K.t('stock.out'), kind: 'err', orderable: false, known: true };
      case 'unavailable': return { text: K.t('stock.na'), kind: 'err', orderable: false, known: true };
      default: return { text: K.t('stock.unk'), kind: 'off', orderable: true };
    }
  };
  /** «+» = یک واحد؛ با کالای در سبد، تعداد روی دکمه. */
  K.addBtn = function (p, qtyMilli, onAdd) {
    var st = K.stockOf(p);
    var b = K.el('button', { class: 'p-add' + (qtyMilli ? ' in' : ''), attrs: { type: 'button' } });
    if (!st.orderable) {
      b.disabled = true;
      b.setAttribute('aria-label', p.name + ' — ' + st.text);
      b.appendChild(K.icon('plus'));
      return b;
    }
    if (qtyMilli) {
      b.textContent = K.qty(qtyMilli);
      b.setAttribute('aria-label', K.t('add.more', { name: p.name, q: K.qty(qtyMilli) }));
    } else {
      b.appendChild(K.icon('plus'));
      b.setAttribute('aria-label', K.t('add.one', { name: p.name }));
    }
    b.addEventListener('click', function (e) { e.stopPropagation(); onAdd(p); });
    return b;
  };
  K.photo = function (slug, id) { return '/api/store/' + encodeURIComponent(slug) + '/products/' + encodeURIComponent(id) + '/photo'; };
  /** o = { href | onOpen, photo (نشانی), qty, onAdd, shop: نامِ دکان روی کارت } */
  K.productCard = function (p, o) {
    var el = K.el;
    var st = K.stockOf(p);
    var li = el('li', { class: 'p-card' + (st.orderable ? '' : ' unavailable'), attrs: { 'data-id': p.id } });
    var img = el('span', { class: 'p-img' + (p.photo ? ' tile' : '') });
    if (p.photo) img.appendChild(el('img', { attrs: { src: o.photo, alt: '', loading: 'lazy', decoding: 'async', width: '300', height: '300' } }));
    else img.appendChild(el('span', { class: 'ph', text: String(p.name || '?').slice(0, 1), attrs: { 'aria-hidden': 'true' } }));
    var deal = p.regularPriceMinor > p.priceMinor;
    var label = [p.name, K.money(p.priceMinor) + (p.unit ? ' ' + K.t('p.per', { unit: p.unit }) : '')];
    if (deal) label.push(K.t('p.was') + ' ' + K.money(p.regularPriceMinor));
    if (o.shop) label.push(p.shop.title);
    var aria = label.join(K.t('sep')) + ' — ' + K.t('p.details');
    var open = o.href ? el('a', { class: 'p-open', attrs: { href: o.href, 'aria-label': aria } })
      : el('button', { class: 'p-open', attrs: { type: 'button', 'aria-label': aria }, on: { click: o.onOpen } });
    open.append(img, el('span', { class: 'p-price num' }, K.fa(p.priceMinor / 100), el('small', { text: ' ' + K.t('cur') })));
    if (deal) open.append(el('span', { class: 'p-was' }, K.t('p.was') + ' ', el('s', { text: K.money(p.regularPriceMinor) })));
    open.append(el('span', { class: 'p-name', text: p.name }));
    if (p.unit) open.append(el('span', { class: 'p-unit', text: K.t('p.per', { unit: p.unit }) }));
    //  ۳۲.۳: نشانِ موجودی فقط وقتی دانسته است
    if (st.known) open.append(el('span', { class: 'p-stock' }, el('span', { class: 'pill ' + st.kind, text: st.text })));
    if (o.shop) {
      open.append(el('span', { class: 'p-shop' }, K.avatar(p.shop.title, p.shop.accent, p.shop.slug, 'sm'), el('b', { text: p.shop.title }),
        el('span', { class: 'dot ' + K.status({ badge: p.shop.badge }).kind, attrs: { 'aria-hidden': 'true' } }),
        p.shop.distanceKm ? el('span', { class: 'num', text: K.t('dist', { n: K.fa(p.shop.distanceKm, 1) }) }) : null));
    }
    li.appendChild(open);
    if (deal) li.appendChild(el('span', { class: 'p-off', text: K.t('p.off', { n: K.fa(Math.round((1 - p.priceMinor / p.regularPriceMinor) * 100), 0) }), attrs: { 'aria-hidden': 'true' } }));
    li.appendChild(K.addBtn(p, o.qty || 0, o.onAdd));
    return li;
  };

  // ── سبد در گوشی (شکلِ r2/r3) و کشو/برگه در تبلت و کامپیوتر ──
  var PHONE = window.matchMedia('(max-width: 599px)');
  var phoneOpen = false; var cartCb = null; var strip = { q: 0, ids: {} };
  K.isPhone = function () { return PHONE.matches; };
  K.onPhone = function (fn) { PHONE.addEventListener('change', fn); };
  /** پایهٔ تیره فقط در گوشی و با سبدِ پُر؛ جای پیمایش حفظ می‌شود. */
  K.cartMode = function (want) {
    want = Boolean(want) && PHONE.matches;
    var on = document.body.classList.contains('phone-cart');
    if (want === on) return;
    var s = K.$('surface');
    var y = on ? s.scrollTop : window.scrollY;
    document.body.classList.toggle('phone-cart', want);
    if (want) s.scrollTop = y; else window.scrollTo(0, y);
  };
  K.cartIsOpen = function () { return phoneOpen || K.sheetOpen() === K.$('cart-sheet'); };
  K.cartOpen = function (onClose) {
    var sh = K.$('cart-sheet');
    cartCb = onClose;
    if (document.body.classList.contains('phone-cart')) {
      if (phoneOpen) return;
      phoneOpen = true;
      sh.hidden = false; K.$('surface-cap').hidden = false;
      K.$('surface').inert = true; K.$('cart-bar').inert = true;
      sh.classList.add('entering');
      setTimeout(function () { sh.classList.remove('entering'); }, 600);
      requestAnimationFrame(function () { document.body.classList.add('cart-open'); });
      setTimeout(function () { K.$('cart-title').focus(); }, 60);
    } else if (K.sheetOpen() !== sh) K.openSheet(sh, onClose);
  };
  K.cartClose = function () {
    if (phoneOpen) {
      phoneOpen = false;
      document.body.classList.remove('cart-open');
      K.$('surface').inert = false; K.$('cart-bar').inert = false;
      K.$('surface-cap').hidden = true;
      setTimeout(function () { if (!phoneOpen) K.$('cart-sheet').hidden = true; }, 360);
      if (!K.$('cart-bar').hidden) K.$('cart-bar').focus();
    } else if (K.sheetOpen() === K.$('cart-sheet')) K.closeSheet(true);
  };
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && phoneOpen && cartCb) { e.preventDefault(); cartCb(); } });
  /** نوار: تا سه عکس/آواتارِ گرد، «+N»، دایرهٔ شمار با پرشِ کوچک هنگامِ افزودن. */
  K.strip = function (o) {
    var th = K.clear(K.$('bar-thumbs')); var seen = {};
    o.list.slice(0, 3).forEach(function (x) {
      if (!strip.ids[x.id] && strip.q) x.node.classList.add('pop');
      seen[x.id] = true;
      th.appendChild(x.node);
    });
    strip.ids = seen;
    K.$('bar-more').hidden = o.n <= 3;
    K.$('bar-more').textContent = '+' + K.fa(o.n - 3, 0);
    K.$('bar-total').textContent = o.sum;
    var c = K.$('open-cart');
    c.textContent = o.count;
    if (strip.q && o.qty > strip.q) { c.classList.remove('bump'); void c.offsetWidth; c.classList.add('bump'); }
    strip.q = o.qty;
    K.$('bar-label').textContent = o.label;
  };
  K.stripReset = function () { strip.q = 0; strip.ids = {}; };
})();
