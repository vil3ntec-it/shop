/*
 * ویترین — صفِ پیام‌های متنیِ نرفته (CHAT-02 در وب). منطقِ خالص، بی DOM و بی شبکه؛ `store/chat.js` آن را دیربار
 * (فقط با گشودنِ گفت‌وگو) بار می‌کند و `test/web-chat-out.test.js` همین پرونده را در Node می‌آزماید.
 * همزادِ `ChatOutbox` ِ برنامهٔ خرید (`core/shopper/ShopperChat.kt`):
 *   • هر پیام هنگامِ نوشتن یک `clientId` می‌گیرد و تا رسیدن همان را نگه می‌دارد ⇒ سرور تکراری نمی‌سازد (CHAT-11)؛
 *   • در هر گفت‌وگو فقط سرِ صف می‌رود (ترتیبِ نوشتن)؛ «نرفته» پشتِ سری‌ها را گیر نمی‌اندازد؛
 *   • خطای گذرا (بی‌اینترنت، ۴۰۸، ۴۲۹، ۵xx) ⇒ backoff ِ نمایی با لرزش، از ۲ تا ۶۰ ثانیه؛ `online` ⇒ همین حالا؛
 *   • ۴۰۱ / `verification_required` ⇒ همهٔ صف تا تأییدِ دوبارهٔ شماره می‌ماند (بلیت برای همهٔ رشته‌ها یکی است)؛
 *   • «دوباره» همان شناسه از سرِ نو؛ «لغو» فقط پیامی که در راه نیست (پیامِ در راه شاید همین حالا به سرور رسیده باشد)؛
 *   • پاسخ (CHAT-14) فقط شناسهٔ پیامِ اصل را با خود دارد (`replyTo`)؛ سرور آن را در همان گفت‌وگو می‌سنجد (`bad_reply`).
 * ⛔ روی localStorage ِ `store-chat-out` فقط متن (نه عکس و نه بایت)، با برچسبِ همان شماره؛ تأییدِ شمارهٔ دیگر روی همین
 *   دستگاه ⇒ صف پاک (پیامِ یک نفر به نامِ دیگری نمی‌رود). حداکثر ۵۰ پیام؛ پیامِ تهی یا بلندتر از ۴۰۰۰ نویسه رد می‌شود،
 *   هرگز بریده نمی‌شود (همان `cleanText` ِ سرور). «در راه» ِ برگهٔ بسته‌شده ⇒ دوباره در صف با همان شناسه.
 */
(function () {
  'use strict';
  var KEY = 'store-chat-out';
  var MAX = 50;
  var MAX_TEXT = 4000;
  var BASE_MS = 2000;
  var MAX_MS = 60000;
  var QUEUED = 'queued'; var SENDING = 'sending'; var WAITING = 'waiting'; var VERIFY = 'verify'; var FAILED = 'failed';
  var STATES = [QUEUED, SENDING, WAITING, VERIFY, FAILED];
  var ID_RE = /^[A-Za-z0-9_.:-]{8,128}$/;           //  همان `CLIENT_ID_RE` ِ سرور
  var CONV_RE = /^[A-Za-z0-9_]{1,64}$/;             //  همان `ID_RE` ِ سرور
  var SLUG_RE = /^[a-z0-9-]{1,64}$/;
  // eslint-disable-next-line no-control-regex
  var CTRL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g;
  var ABC = 'abcdefghijklmnopqrstuvwxyz0123456789';

  function empty(phone) { return { phone: phone || '', items: [] }; }
  function copy(s, items) { return { phone: s.phone, items: items }; }
  function num(x) { var v = Number(x); return isFinite(v) && v >= 0 ? Math.floor(v) : 0; }

  /** متنِ پیام همان‌گونه که سرور می‌پذیرد، یا کدِ رد: `empty` / `too_long` (هرگز بریدن) */
  function clean(raw) {
    var t = String(raw == null ? '' : raw).replace(CTRL, '').trim();
    if (!t) return { error: 'empty' };
    if (t.length > MAX_TEXT) return { error: 'too_long' };
    return { text: t };
  }

  function newId(at, rand) {
    var r = typeof rand === 'function' ? rand : Math.random;
    var s = 'c' + Math.max(0, Math.floor(at)).toString(36) + '-';
    for (var i = 0; i < 20; i++) s += ABC[Math.floor(r() * ABC.length) % ABC.length];
    return s;
  }

  /** مهلتِ تلاشِ nام (از ۱): ۲s·۲ⁿ⁻¹ تا سقفِ ۶۰s، با لرزشِ نیمه — همیشه در [۲s، ۶۰s] */
  function delayMs(attempt, rand) {
    var n = Math.max(1, num(attempt));
    var d = Math.min(MAX_MS, BASE_MS * Math.pow(2, Math.min(n - 1, 20)));
    var j = Math.min(1, Math.max(0, Number(rand) || 0));
    return Math.min(MAX_MS, Math.max(BASE_MS, Math.round(d / 2 + j * (d / 2))));
  }

  /** خطای `K.api` (status، code) ⇐ `retry` (گذرا) | `verify` (شماره) | `fail` (قطعی) */
  function failureOf(e) {
    var s = Number(e && e.status) || 0; var c = (e && e.code) || '';
    if (s === 401 || c === 'verification_required' || c === 'session_required') return 'verify';
    if (s === 0 || s === 408 || s === 429 || s >= 500) return 'retry';
    return 'fail';
  }

  function valid(x) {
    return Boolean(x) && typeof x.clientId === 'string' && ID_RE.test(x.clientId) && typeof x.convId === 'string' && CONV_RE.test(x.convId) &&
      typeof x.slug === 'string' && SLUG_RE.test(x.slug) && !clean(x.body).error && clean(x.body).text === x.body &&
      (x.replyTo == null || (typeof x.replyTo === 'string' && CONV_RE.test(x.replyTo)));
  }
  function pure(x) {
    return {
      clientId: x.clientId, slug: x.slug, convId: x.convId, body: x.body, replyTo: x.replyTo || null,
      state: STATES.indexOf(x.state) >= 0 ? x.state : QUEUED, attempts: num(x.attempts), nextAt: num(x.nextAt),
      error: typeof x.error === 'string' ? x.error.slice(0, 64) : '', createdAt: num(x.createdAt),
    };
  }

  /** خواندن از دیسک: خراب ⇒ تهی؛ «در راه» (برگه وسطِ ارسال بسته شد) ⇒ دوباره در صف با همان شناسه */
  function load(raw) {
    var v = null;
    try { v = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch (e) { v = null; }
    if (!v || typeof v !== 'object' || !Array.isArray(v.items)) return empty('');
    var seen = {};
    var items = v.items.filter(function (x) { if (!valid(x) || seen[x.clientId]) return false; seen[x.clientId] = true; return true; })
      .map(pure).map(function (x) { if (x.state === SENDING) { x.state = QUEUED; x.nextAt = 0; } return x; })
      .sort(function (a, b) { return a.createdAt - b.createdAt; }).slice(-MAX);
    return { phone: typeof v.phone === 'string' ? v.phone : '', items: items };
  }
  function dump(s) { return JSON.stringify({ v: 1, phone: s.phone, items: s.items.map(pure) }); }

  /** صفِ این شماره: شمارهٔ دیگر ⇒ صفِ تهی (پیامِ نفرِ پیشین هرگز به نامِ این یکی نمی‌رود) */
  function forPhone(s, phone) {
    if (!phone) return s;
    if (s.phone && s.phone !== phone) return empty(phone);
    return s.phone === phone ? s : copy({ phone: phone }, s.items);
  }

  /**
   * پیامِ تازه، پشتِ صفِ همان گفت‌وگو ⇒ { state, item } یا { error: 'empty' | 'too_long' | 'full' }.
   * صفِ پُر: کهنه‌ترین «نرفته» جا باز می‌کند؛ پیامِ هنوز در صف هرگز بی‌صدا نمی‌رود (⇒ `full` و متن در کادر می‌ماند).
   */
  function add(s, o, now, rand) {
    var c = clean(o && o.body);
    if (c.error) return { error: c.error };
    if (!o || !SLUG_RE.test(String(o.slug || '')) || !CONV_RE.test(String(o.convId || ''))) return { error: 'bad' };
    if (o.replyTo != null && (typeof o.replyTo !== 'string' || !CONV_RE.test(o.replyTo))) return { error: 'bad' };
    var items = s.items.slice();
    if (items.length >= MAX) {
      var f = -1;
      for (var i = 0; i < items.length; i++) if (items[i].state === FAILED) { f = i; break; }
      if (f < 0) return { error: 'full' };
      items.splice(f, 1);
    }
    var at = Math.max(num(now), items.reduce(function (m, x) { return Math.max(m, x.createdAt); }, 0) + 1);
    var item = { clientId: newId(at, rand), slug: o.slug, convId: o.convId, body: c.text, replyTo: o.replyTo || null,
      state: QUEUED, attempts: 0, nextAt: 0, error: '', createdAt: at };
    items.push(item);
    return { state: copy(s, items), item: item };
  }

  function forConv(s, convId) {
    return s.items.filter(function (x) { return x.convId === convId; }).sort(function (a, b) { return a.createdAt - b.createdAt; });
  }
  function find(s, clientId) { for (var i = 0; i < s.items.length; i++) if (s.items[i].clientId === clientId) return s.items[i]; return null; }
  function held(s) { return s.items.some(function (x) { return x.state === VERIFY; }); }

  /** سرِ صفِ یک گفت‌وگو اگر الان وقتش است؛ «نرفته» رد می‌شود؛ «در راه» و «منتظرِ اینترنت» ِ زود صف را نگه می‌دارند */
  function next(s, convId, now) {
    if (held(s)) return null;
    var list = forConv(s, convId);
    for (var i = 0; i < list.length; i++) {
      var x = list[i];
      if (x.state === FAILED) continue;
      if (x.state === SENDING || (x.state === WAITING && x.nextAt > now)) return null;
      return x;
    }
    return null;
  }
  /** نخستین پیامِ آمادهٔ این ویترین (سرِ صفِ هر گفت‌وگو، به ترتیبِ نوشتن) */
  function due(s, slug, now) {
    var convs = [];
    s.items.slice().sort(function (a, b) { return a.createdAt - b.createdAt; })
      .forEach(function (x) { if (x.slug === slug && convs.indexOf(x.convId) < 0) convs.push(x.convId); });
    for (var i = 0; i < convs.length; i++) { var x = next(s, convs[i], now); if (x) return x; }
    return null;
  }

  function patch(s, clientId, fn) {
    return copy(s, s.items.map(function (x) { if (x.clientId !== clientId) return x; var y = pure(x); fn(y); return y; }));
  }
  function sending(s, clientId) { return patch(s, clientId, function (x) { x.state = SENDING; }); }
  function done(s, clientId) { return copy(s, s.items.filter(function (x) { return x.clientId !== clientId; })); }
  function failed(s, clientId, e, now, rand) {
    var kind = failureOf(e); var code = String((e && e.code) || '').slice(0, 64);
    return patch(s, clientId, function (x) {
      x.error = code;
      if (kind === 'retry') { x.attempts += 1; x.state = WAITING; x.nextAt = num(now) + delayMs(x.attempts, rand); }
      else x.state = kind === 'verify' ? VERIFY : FAILED;
    });
  }
  /** «دوباره» ِ کاربر: همان شناسه، از سرِ نو (پیامِ در راه دست نمی‌خورد) */
  function retry(s, clientId) {
    var x = find(s, clientId);
    if (!x || x.state === SENDING) return s;
    return patch(s, clientId, function (y) { y.state = QUEUED; y.attempts = 0; y.nextAt = 0; y.error = ''; });
  }
  /** «لغو»: فقط پیامی که در راه نیست ⇒ { state, ok } */
  function cancel(s, clientId) {
    var x = find(s, clientId);
    if (!x || x.state === SENDING) return { state: s, ok: false };
    return { state: done(s, clientId), ok: true };
  }
  /** شماره تأیید شد: شمارهٔ دیگر ⇒ پاک؛ همان ⇒ «منتظرِ تأیید»ها دوباره در صف */
  function reverified(s, phone) {
    var t = forPhone(s, phone);
    return copy(t, t.items.map(function (x) { if (x.state !== VERIFY) return x; var y = pure(x); y.state = QUEUED; y.nextAt = 0; y.error = ''; return y; }));
  }
  /** اینترنت برگشت: «منتظرِ اینترنت»ها همین حالا */
  function online(s) {
    return copy(s, s.items.map(function (x) { if (x.state !== WAITING) return x; var y = pure(x); y.nextAt = 0; return y; }));
  }
  /** زودترین بیدار شدنِ لازم (فقط «منتظرِ اینترنت»ها ِ همین ویترین)؛ null ⇒ چیزی منتظرِ زمان نیست */
  function wakeAt(s, slug) {
    var w = s.items.filter(function (x) { return x.state === WAITING && x.slug === slug; }).map(function (x) { return x.nextAt; });
    return w.length ? Math.min.apply(null, w) : null;
  }

  var lib = {
    KEY: KEY, MAX: MAX, MAX_TEXT: MAX_TEXT, BASE_MS: BASE_MS, MAX_MS: MAX_MS,
    QUEUED: QUEUED, SENDING: SENDING, WAITING: WAITING, VERIFY: VERIFY, FAILED: FAILED,
    empty: empty, clean: clean, newId: newId, delayMs: delayMs, failureOf: failureOf, load: load, dump: dump, forPhone: forPhone,
    add: add, forConv: forConv, find: find, held: held, next: next, due: due, sending: sending, done: done, failed: failed,
    retry: retry, cancel: cancel, reverified: reverified, online: online, wakeAt: wakeAt,
  };
  if (typeof module === 'object' && module.exports) { module.exports = lib; return; }

  //  ── صفحه (فقط مرورگر): حبابِ پیام‌های نرفته با «دوباره» و «لغو»؛ واژه‌ها در `kit/i18n/chatq.<زبان>.json` ──
  var $ = K.$; var t = K.t; var el = K.el;
  lib.ready = K.part('chatq', $('chat-sheet'));
  function word(x) {
    if (x.state === SENDING) return t('cq.sending');
    if (x.state === WAITING) return t('cq.wait');
    if (x.state === VERIFY) return t('cq.verify');
    if (x.state === FAILED) return t('cq.failed') + (x.error && K.has('err.' + x.error) ? t('sep') + t('err.' + x.error) : '');
    return t('cq.queued');
  }
  /** پیامِ ردشده پیش از صف (`add`): متنِ روشن؛ متن در کادر می‌ماند */
  lib.why = function (code) { return t(code === 'full' ? 'cq.full' : 'cq.long'); };
  /** حباب‌های صفِ همین گفت‌وگو؛ تمرکزِ درونِ حباب (دکمهٔ «دوباره»/«لغو») پس از ساختنِ دوباره سرِ جایش می‌ماند */
  lib.render = function (ol, items, on) {
    var a = document.activeElement;
    var keep = a && ol.contains(a) && a.getAttribute('data-q') ? [a.closest('[data-out]').getAttribute('data-out'), a.getAttribute('data-q')] : null;
    K.clear(ol);
    ol.setAttribute('aria-label', t('cq.list'));
    items.forEach(function (x) {
      var li = el('li', { class: 'ct-msg mine ct-q', attrs: { 'data-out': x.clientId, 'data-st': x.state } },
        el('p', { text: x.body }), el('div', { class: 'ct-meta' }, el('small', { class: 'muted', text: word(x) })));
      if (x.state !== SENDING) {
        var row = el('div', { class: 'ct-acts' });
        if (x.state !== QUEUED) {
          row.appendChild(el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'data-q': 'retry' }, text: t('cq.retry'),
            on: { click: function () { on.retry(x.clientId); } } }));
        }
        row.appendChild(el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'data-q': 'cancel' }, text: t('cq.cancel'),
          on: { click: function () { on.cancel(x.clientId); } } }));
        li.appendChild(row);
      }
      ol.appendChild(li);
    });
    if (!keep) return;
    var li = ol.querySelector('[data-out="' + keep[0] + '"]');
    var b = li && (li.querySelector('[data-q="' + keep[1] + '"]') || li.querySelector('button'));
    if (b) b.focus(); else on.home();
  };
  window.StoreChatOut = lib;
})();
