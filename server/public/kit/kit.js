/*
 * کیتِ مشترکِ وبِ خریدار (بازارچه + ویترین). بی وابستگی و بی innerHTML برای داده:
 * هر متنی که از سرور می‌آید با textContent می‌نشیند (XSS). CSP: فقط همین مبدأ.
 * حافظهٔ مرورگر ممکن است بسته باشد؛ صفحه بی آن هم کار می‌کند.
 */
(function () {
  'use strict';
  var K = window.K = {};
  var SVGNS = 'http://www.w3.org/2000/svg';

  K.$ = function (id) { return document.getElementById(id); };

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

  // ── نشانه‌ها (خطی، ۲۴×۲۴) ──
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
    refresh: 'M4 12a8 8 0 0 1 14-5.3L20 9 M20 4v5h-5 M20 12a8 8 0 0 1-14 5.3L4 15 M4 20v-5h5',
    user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 21a7.5 7.5 0 0 1 15 0',
    cash: 'M3 7h18v10H3z M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M6 10v4 M18 10v4',
    box: 'M3.5 8L12 3.5 20.5 8v8L12 20.5 3.5 16V8z M3.5 8L12 12.5 20.5 8 M12 12.5v8',
    x: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M9 9l6 6 M15 9l-6 6',
  };
  K.icon = function (name, cls) {
    var s = document.createElementNS(SVGNS, 'svg');
    s.setAttribute('viewBox', '0 0 24 24'); s.setAttribute('fill', 'none'); s.setAttribute('stroke', 'currentColor');
    s.setAttribute('stroke-width', '2'); s.setAttribute('stroke-linecap', 'round'); s.setAttribute('stroke-linejoin', 'round');
    s.setAttribute('aria-hidden', 'true'); s.setAttribute('focusable', 'false');
    if (cls) s.setAttribute('class', cls);
    var p = document.createElementNS(SVGNS, 'path');
    p.setAttribute('d', ICONS[name] || ICONS.info);
    s.appendChild(p);
    return s;
  };

  // ── عدد، پول، زمان ──
  //  جداکنندهٔ هزارگانِ فارسی «٬» و اعشارِ «٫» (نه ویرگول و نقطهٔ لاتین)
  K.fa = function (n, d) {
    return Number(n).toLocaleString('fa-AF', { maximumFractionDigits: d === undefined ? 2 : d }).replace(/,/g, '٬').replace(/\./g, '٫');
  };
  K.money = function (minor) { return K.fa(Math.round(Number(minor)) / 100) + ' افغانی'; };
  K.qty = function (milli) { return K.fa(Number(milli) / 1000, 3); };
  /** همان گردکردنِ سرور: هر قلم به افغانیِ کامل (order-states.lineTotalMinor). */
  K.lineTotal = function (qtyMilli, priceMinor) { return Math.floor((qtyMilli * priceMinor + 50000) / 100000) * 100; };
  K.hm = function (min) { return K.fa(Math.floor(min / 60), 0) + ':' + K.fa(min % 60, 0).padStart(2, '۰'); };
  K.clock = function (at, tz) {
    try { return new Date(at).toLocaleTimeString('fa-AF', { timeZone: tz || undefined, hour: '2-digit', minute: '2-digit' }); } catch (e) { return ''; }
  };
  K.when = function (at) {
    try { return new Date(at).toLocaleString('fa-AF', { dateStyle: 'medium', timeStyle: 'short' }); } catch (e) { return ''; }
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

  // ── API ──
  K.api = function (url, opts) {
    opts = opts || {};
    var headers = { Accept: 'application/json' };
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
        var err = new Error(e.message || 'خطا؛ دوباره بزنید');
        err.status = res.status; err.code = e.code || ''; err.data = e;
        if (res.status === 429) err.message = 'درخواست‌ها زیاد شد؛ کمی بعد دوباره بزنید.';
        throw err;
      });
    }, function () {
      var err = new Error('اتصال برقرار نشد؛ اینترنت را ببینید و دوباره بزنید.');
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
      case 'open_online': return { text: 'باز است', kind: 'ok' };
      case 'open_unconfirmed': return { text: 'باز، اتصال تأیید نشده', kind: 'warn' };
      case 'declared_until': return { text: 'آنلاین تا ' + K.clock(s.declaredUntil, s.timezone), kind: 'ok' };
      case 'closed': return { text: 'بسته است', kind: 'off' };
      case 'offline': return { text: 'موقتاً آفلاین', kind: 'off' };
      default: return { text: '', kind: 'off' };
    }
  };
  K.pill = function (s) { var st = K.status(s); return K.el('span', { class: 'pill ' + st.kind, text: st.text }); };
  K.ACCEPT = {
    accepting: 'سفارش همین حالا به دکان می‌رسد.',
    delayed: 'سفارش ثبت می‌شود، ولی ممکن است دیرتر بررسی شود.',
    later: 'سفارش ثبت می‌شود و پس از باز شدنِ دکان بررسی می‌شود.',
  };
  K.hours = function (s) {
    if (!s) return '';
    if (s.holiday) return 'امروز تعطیل';
    if (s.today && s.today.length) return 'امروز ' + s.today.map(function (r) { return K.hm(r[0]) + '–' + K.hm(r[1]); }).join('، ');
    return s.hoursSet ? 'امروز تعطیل' : '';
  };

  // ── آواتارِ دکان: رنگِ خودِ دکان، وگرنه یکی از شش رنگِ آرام ──
  function hash(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return Math.abs(h); }
  K.avatar = function (title, accent, seed, size) {
    var a = K.el('span', { class: 'avatar' + (size ? ' ' + size : ''), text: String(title || '؟').trim().slice(0, 1), attrs: { 'aria-hidden': 'true' } });
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
      var no = K.el('button', { class: 'btn btn-ghost btn-sm', text: 'نه', attrs: { type: 'button' } });
      var yes = K.el('button', { class: 'btn btn-danger btn-sm', text: okText || 'بله', attrs: { type: 'button' } });
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
    var b = K.el('div', { class: 'offline', text: 'اینترنت وصل نیست؛ سبدِ شما روی همین دستگاه می‌ماند.', attrs: { role: 'status' }, hidden: navigator.onLine !== false });
    document.body.insertBefore(b, document.body.firstChild);
    window.addEventListener('online', function () { b.hidden = true; });
    window.addEventListener('offline', function () { b.hidden = false; });
  };

  // ── سبدهای مهمان (هر دکان جدا؛ همان کلیدِ ویترینِ پیشین) ──
  K.cartKey = function (slug) { return 'store-cart:' + slug; };
  K.cartMetaKey = function (slug) { return 'store-cart-meta:' + slug; };
  K.openCarts = function () {
    var out = [];
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (!k || k.indexOf('store-cart:') !== 0) continue;
        var slug = k.slice(11);
        var cart = K.load(k, {});
        var ids = Object.keys(cart || {});
        if (!ids.length) continue;
        var meta = K.load(K.cartMetaKey(slug), {}) || {};
        var total = ids.reduce(function (s, id) { return s + K.lineTotal(cart[id].qtyMilli, cart[id].priceMinor); }, 0);
        out.push({ slug: slug, title: meta.title || slug, accent: meta.accent || '', count: ids.length, totalMinor: total, at: meta.at || 0 });
      }
    } catch (e) { /* بی حافظه */ }
    return out.sort(function (a, b) { return b.at - a.at; });
  };

  // ── وضعیتِ سفارش برای خریدار ──
  K.ORDER = {
    new: 'ثبت شد — منتظرِ تأییدِ دکان', accepted: 'دکان قبول کرد', rejected: 'دکان نپذیرفت',
    auto_rejected: 'بی پاسخ ماند', cancelled_by_customer: 'شما لغو کردید', picking: 'در حالِ جمع‌آوری',
    packed: 'بسته‌بندی شد', ready_for_pickup: 'آمادهٔ دریافت از دکان', out_for_delivery: 'در راه',
    picked_up: 'تحویل گرفتید', delivered: 'تحویل شد', cancelled: 'لغو شد', delivery_failed: 'تحویل نشد',
    retry_scheduled: 'تحویلِ دوباره', returned_to_store: 'به دکان برگشت', restocked: 'به دکان برگشت',
    return_requested: 'درخواستِ مرجوعی', refunded_partial: 'بخشی بازپرداخت شد', refunded_full: 'بازپرداخت شد',
  };
  K.REASON = { out_of_stock: 'ناموجود', closed: 'دکان بسته بود', busy: 'دکان شلوغ بود', out_of_area: 'بیرون از محدوده', customer_request: 'به درخواستِ مشتری', no_show: 'نیامدید', timeout: 'زمانِ پاسخ گذشت' };
  var STOP = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'returned_to_store', 'restocked'];
  K.orderKind = function (status) {
    if (STOP.indexOf(status) >= 0) return 'err';
    if (['delivered', 'picked_up', 'refunded_partial', 'refunded_full'].indexOf(status) >= 0) return 'ok';
    if (status === 'new') return 'warn';
    return 'info';
  };
  /** پله‌های پیشرفتِ سفارش، هر کدام done | now | todo | stop. */
  K.orderSteps = function (o) {
    var pickup = o.fulfillment === 'pickup';
    var steps = [
      { label: 'ثبت شد', keys: ['new'] },
      { label: 'دکان قبول کرد', keys: ['accepted'] },
      { label: 'آماده‌سازی', keys: ['picking', 'packed'] },
      pickup ? { label: 'آمادهٔ دریافت', keys: ['ready_for_pickup'] } : { label: 'در راه', keys: ['out_for_delivery', 'delivery_failed', 'retry_scheduled'] },
      pickup ? { label: 'تحویل گرفتید', keys: ['picked_up', 'return_requested', 'refunded_partial', 'refunded_full'] }
        : { label: 'تحویل شد', keys: ['delivered', 'return_requested', 'refunded_partial', 'refunded_full'] },
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
      return { label: s.label, at: t, state: state };
    });
    //  سفارشِ ردشده/لغوشده: پله‌های بعدی نیست، فقط همان پایان
    return stopped ? out.slice(0, idx + 1).concat([{ label: K.ORDER[o.status] || o.status, at: at[o.status] || o.updatedAt, state: 'stop' }]) : out;
  };
})();
