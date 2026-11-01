/**
 * بخشِ «سفارش‌ها»ی نسخهٔ وب — فاز ۴ (ORD-01، ۰۶، ۰۷، ۱۱، ۱۲، ۱۶، ۱۷، ۲۱).
 *
 * ⛔ پیش‌فرض خاموش: بخش فقط وقتی ساخته می‌شود که `GET /api/v1/shop/orders/config`
 * بگوید `enabled` (پرچمِ `shop.orders` + اشتراک). خاموش ⇒ نه صفحه‌ای، نه
 * دکمه‌ای در منو، نه درخواستِ `summary` — برای هیچ دکانی چیزی عوض نمی‌شود.
 * ⛔ وضعیت را سرور عوض می‌کند (`order-states.js`)؛ دکمه‌ها همان `next`ِ
 * کارت‌اند و این‌جا فقط دلیل و زمانِ آماده را می‌پرسد.
 * ⛔ شمارهٔ کامل فقط وقتی سرور بدهد (`orders.contact`)؛ وگرنه نقاب‌دار.
 * ⛔ صفِ زنده با پرسیدنِ `summary` است، فقط وقتی برگه جلوی چشم است؛ سوکتِ
 * تازه‌ای باز نمی‌شود.
 *
 * بخشِ بالایی («هسته») خالص است و در Node هم بار می‌شود
 * (`server/test/order-vectors.test.js` آن را با بردارِ مشترکِ
 * `core/test-vectors/order-states.json` می‌سنجد).
 */
(function (root) {
  'use strict';

  // ══════════════════════════════ هسته ══════════════════════════════

  const TABS = ['new', 'preparing', 'ready', 'on_the_way', 'scheduled', 'done'];
  const TAB_LABELS = {
    new: 'جدید', preparing: 'در آماده‌سازی', ready: 'آماده',
    on_the_way: 'در مسیر', scheduled: 'زمان‌بندی‌شده', done: 'تمام‌شده',
  };
  const STATUS_LABELS = {
    new: 'جدید', accepted: 'قبول‌شده', rejected: 'ردشده', auto_rejected: 'ردِ خودکار',
    cancelled_by_customer: 'لغو از سوی مشتری', picking: 'در حالِ جمع‌آوری', packed: 'بسته‌بندی‌شده',
    ready_for_pickup: 'آمادهٔ تحویل در دکان', out_for_delivery: 'در مسیر', delivered: 'تحویل‌شده',
    delivery_failed: 'تحویل نشد', retry_scheduled: 'تلاشِ دوباره', returned_to_store: 'برگشت به دکان',
    restocked: 'برگشت به انبار', cancelled: 'لغوشده', picked_up: 'مشتری تحویل گرفت',
    return_requested: 'درخواستِ برگشت', refunded_partial: 'بازپرداختِ بخشی', refunded_full: 'بازپرداختِ کامل',
  };
  /** نامِ دکمهٔ هر گذار (به مقصد) */
  const ACTION_LABELS = {
    accepted: 'قبول', rejected: 'رد', auto_rejected: 'ردِ خودکار', cancelled_by_customer: 'مشتری لغو کرد',
    picking: 'شروعِ جمع‌آوری', packed: 'بسته شد', ready_for_pickup: 'آماده برای تحویل',
    out_for_delivery: 'ارسال شد', delivered: 'تحویل شد', delivery_failed: 'تحویل نشد',
    retry_scheduled: 'تلاشِ دوباره', returned_to_store: 'برگشت به دکان', restocked: 'برگشت به انبار',
    cancelled: 'لغو', picked_up: 'مشتری گرفت', return_requested: 'درخواستِ برگشت',
    refunded_partial: 'بازپرداختِ بخشی', refunded_full: 'بازپرداختِ کامل',
  };
  const REASON_LABELS = {
    out_of_stock: 'ناموجود', closed: 'دکان بسته است', busy: 'شلوغ', out_of_area: 'خارج از محدوده',
    other: 'دلیلِ دیگر', timeout: 'پایانِ مهلت', customer_request: 'درخواستِ مشتری', no_show: 'مشتری نیامد',
    no_answer: 'جواب نداد', wrong_address: 'نشانیِ غلط', refused: 'مشتری نپذیرفت',
    recalled: 'دکان پس از حرکتِ پیک برگرداند',
  };
  /** ماشینِ جدای «نقد هنگامِ تحویل» (رویدادِ `cod` در خطِ زمانی) */
  const COD_LABELS = {
    to_collect: 'در انتظارِ دریافت', collected_by_courier: 'پیک گرفت',
    collected_in_store: 'در دکان گرفته شد', settled_to_shop: 'به دکان تسویه شد',
  };
  const SOURCE_LABELS = { phone: 'تلفنی', telegram: 'تلگرام', whatsapp: 'واتس‌اپ', manual: 'دستی', web: 'ویترین', storefront: 'ویترین' };
  /** گذارهایی که دکمه‌شان «خطر» است */
  const DANGER = ['rejected', 'cancelled', 'cancelled_by_customer', 'delivery_failed'];
  /** پول برمی‌گردد ⇒ یک بار دیگر می‌پرسیم */
  const MONEY = ['refunded_partial', 'refunded_full'];
  const READY_MINUTES = [10, 15, 20, 30, 45, 60, 90];

  /** دلیل‌های پنجرهٔ «دلیل» — «آماده ⇐ لغو» فقط «نیامد» (نمودارِ ۳.۹) */
  function reasonChoices(from, to, reasons) {
    const list = reasons && Array.isArray(reasons[to]) ? reasons[to].slice() : [];
    if (to === 'cancelled' && from === 'ready_for_pickup') return list.filter((r) => r === 'no_show');
    return list;
  }

  function needsReason(to, reasons) {
    return !!(reasons && Array.isArray(reasons[to]) && reasons[to].length);
  }

  /** دکمه‌های کارت برای این کاربر: `next`ِ سرور، فقط با `orders.manage` */
  function actionsFor(order, perms) {
    if (!order || !perms || !perms.manage) return [];
    return Array.isArray(order.next) ? order.next.slice() : [];
  }

  /** رقمِ فارسی/عربی ⇐ لاتین، «٫» ⇐ نقطه، جداکنندهٔ هزارگان پاک */
  function latin(s) {
    return String(s == null ? '' : s)
      .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
      .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
      .replace(/[٫]/g, '.')
      .replace(/[,٬\s]/g, '');
  }

  /** «1.5» با سه رقمِ اعشار ⇐ 1500 — بی ممیزِ شناور؛ نامعتبر ⇐ null */
  function scaled(text, places) {
    const s = latin(text);
    const m = /^(\d{1,12})(?:\.(\d*))?$/.exec(s);
    if (!m) return null;
    const frac = m[2] || '';
    if (frac.length > places) return null;
    return Number(m[1]) * (10 ** places) + Number((frac + '0'.repeat(places)).slice(0, places) || '0');
  }

  /** مبلغِ واحدِ کوچک ⇐ «1,250» یا «33.33» */
  function money(minor) {
    const n = Math.round(Number(minor) || 0);
    const neg = n < 0;
    const a = Math.abs(n);
    const whole = String(Math.floor(a / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    const cents = a % 100;
    return (neg ? '-' : '') + whole + (cents ? '.' + String(cents).padStart(2, '0') : '');
  }

  function ago(ms, now) {
    const d = Math.max(0, Math.floor(((now || Date.now()) - Number(ms || 0)) / 60000));
    if (d < 1) return 'همین حالا';
    if (d < 60) return `${d} دقیقه پیش`;
    if (d < 24 * 60) return `${Math.floor(d / 60)} ساعت پیش`;
    return `${Math.floor(d / (24 * 60))} روز پیش`;
  }

  /**
   * فرمِ «سفارشِ دستی» ⇐ بدنهٔ `POST /orders` (ORD-21). مبلغ را سرور می‌سازد؛
   * این‌جا فقط مقدار به هزارم و قیمت به یک‌صدم می‌رود.
   */
  function buildCreateBody(f) {
    const x = f || {};
    const err = (error) => ({ ok: false, error });
    const fulfillment = x.fulfillment === 'delivery' ? 'delivery' : 'pickup';
    const name = String(x.name || '').trim();
    if (!name) return err('نامِ مشتری لازم است');
    const phone = String(x.phone || '').trim();
    if (fulfillment === 'delivery' && !phone) return err('برای ارسال، شمارهٔ مشتری لازم است');
    const addressText = String(x.addressText || '').trim();
    const landmark = String(x.landmark || '').trim();
    if (fulfillment === 'delivery' && !addressText && !landmark) return err('نشانی یا نشانهٔ تحویل لازم است');

    const lines = [];
    const rows = Array.isArray(x.lines) ? x.lines : [];
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i] || {};
      const ln = String(r.name || '').trim();
      const q = String(r.qty || '').trim();
      const p = String(r.price || '').trim();
      if (!ln && !q && !p) continue;
      if (!ln) return err(`نامِ قلمِ ${i + 1} لازم است`);
      const qtyMilli = scaled(q || '1', 3);
      if (!qtyMilli) return err(`مقدارِ «${ln}» درست نیست`);
      const unitPriceMinor = scaled(p, 2);
      if (unitPriceMinor === null) return err(`قیمتِ «${ln}» درست نیست`);
      lines.push({ name: ln, qtyMilli, unitPriceMinor });
    }
    if (!lines.length) return err('دست‌کم یک قلم بنویسید');

    let deliveryFeeMinor = 0;
    if (fulfillment === 'delivery' && String(x.deliveryFee || '').trim()) {
      deliveryFeeMinor = scaled(x.deliveryFee, 2);
      if (deliveryFeeMinor === null) return err('هزینهٔ ارسال درست نیست');
    }

    const body = {
      source: String(x.source || 'manual'),
      fulfillment,
      paymentMethod: 'cod',
      customer: { name, ...(phone ? { phone } : {}) },
      lines,
    };
    if (fulfillment === 'delivery') {
      body.address = { ...(addressText ? { text: addressText } : {}), ...(landmark ? { landmark } : {}) };
      if (deliveryFeeMinor) body.deliveryFeeMinor = deliveryFeeMinor;
    }
    const note = String(x.note || '').trim();
    if (note) body.note = note;
    if (x.test === true) body.test = true;
    return { ok: true, body };
  }

  /** کلیدِ یکتایی — یک بار برای هر فرم (CHK-19) */
  function newKey() {
    const c = root.crypto;
    if (c && typeof c.randomUUID === 'function') return 'web-' + c.randomUUID();
    let s = 'web-';
    for (let i = 0; i < 32; i++) s += Math.floor(Math.random() * 16).toString(16);
    return s;
  }

  const core = {
    TABS, TAB_LABELS, STATUS_LABELS, ACTION_LABELS, REASON_LABELS, SOURCE_LABELS, COD_LABELS, READY_MINUTES,
    reasonChoices, needsReason, actionsFor, latin, scaled, money, ago, buildCreateBody, newKey,
  };
  if (typeof module !== 'undefined' && module.exports) { module.exports = core; return; }
  if (typeof document === 'undefined') return;

  // ══════════════════════════════ مرورگر ══════════════════════════════

  const API = '/api/v1/shop/orders';
  /** فاصلهٔ پرسیدنِ شمارش‌ها؛ آزمون می‌تواند کوتاهش کند */
  const POLL_MS = Math.max(500, Number(root.TOHID_ORDERS_POLL_MS) || 20000);
  /** هر چند وقت یک بار «روشن است؟» دوباره پرسیده شود */
  const CONFIG_MS = 10 * 60 * 1000;
  const PAGE_SIZE = 30;

  const st = {
    known: false, enabled: false, cfg: null, cfgAt: 0, cfgBusy: null,
    counts: {}, rev: null, tab: 'new', items: [], cursor: null, loading: false, err: '', listSeq: 0,
    open: null, act: null, formKey: '', timer: null, busy: false, detailSeq: 0, extLoading: {},
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const shop = () => root.TohidShop;
  const call = (path, opts) => shop().apiAuth(API + path, opts);
  const toast = (m) => { try { shop()._toast(m); } catch { /* بی پیام */ } };
  const visible = () => document.visibilityState !== 'hidden';
  const isActive = () => !!($('page-orders') && $('page-orders').classList.contains('active'));
  const af = (minor) => `${money(minor)} افغانی`;
  const DATE_FMT = (() => {
    try { return new Intl.DateTimeFormat('fa-AF-u-nu-latn', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }); }
    catch { return null; }
  })();
  const when = (ms) => (DATE_FMT ? DATE_FMT.format(new Date(Number(ms))) : new Date(Number(ms)).toLocaleString());

  /** افزونه‌های پشتِ پرچم: `orders-desk.js` (ORD-03 «باز / بسته»، ORD-07 «عقب انداختن») با `presence`/`delay` و `area-requests.js` (CHK-10 «خبرم کن») با `notifyArea` ِ `/config` */
  const extApi = { call, toast, esc, when, $, closeModal, get cfg() { return st.cfg; }, get open() { return st.open; },
    showOrder(o) { st.open = o; st.detailSeq++; paintDetail(); }, reopen(id) { openDetail(id, true); },
    refresh() { pollSummary(); if (isActive()) loadList(true); } };
  const ext = (name, ...a) => [root.TohidOrdersDesk, root.TohidAreaRequests].reduce((r, x) => (x && typeof x[name] === 'function' ? x[name](extApi, ...a) ?? r : r), undefined);
  function loadExt() {
    [['TohidOrdersDesk', 'orders-desk', st.cfg && (st.cfg.delay || st.cfg.presence)], ['TohidAreaRequests', 'area-requests', st.cfg && st.cfg.notifyArea]].forEach(([g, f, on]) => {
      if (!on || root[g] || st.extLoading[f]) return;
      const el = Object.assign(document.createElement('script'), { src: `license/${f}.js?v=1` }); st.extLoading[f] = true;
      el.onload = () => { st.extLoading[f] = false; ext('mount'); if (st.open) paintDetail(); };
      el.onerror = () => { st.extLoading[f] = false; }; document.head.appendChild(el);
    });
  }

  const ICON = '<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>';

  // ── پرچم ─────────────────────────────────────────────────────────

  function refreshConfig() {
    if (st.cfgBusy) return st.cfgBusy;
    st.cfgBusy = (async () => {
      let cfg = null;
      let transient = false;
      const sh = shop();
      if (sh && sh.account && sh.account.loggedIn) {
        try { cfg = await call('/config'); }
        catch (e) { transient = !!e && (e.code === 'network' || (e.status >= 500)); }
      }
      st.cfgAt = Date.now();
      //  قطعِ گذرا بخشِ روشن را خاموش نمی‌کند
      if (transient && st.known) return;
      st.known = true;
      st.cfg = cfg && cfg.enabled ? cfg : null;
      apply(!!st.cfg);
    })().finally(() => { st.cfgBusy = null; });
    return st.cfgBusy;
  }

  function apply(on) {
    const was = st.enabled;
    st.enabled = on;
    if (on) {
      mount();
      loadExt(); ext('mount');
      paintTabs();
      if (!was) { st.rev = null; pollSummary(); }
      //  نشانیِ #orders پیش از روشن شدن باز شده بود ⇒ حالا خودِ صفحه
      if (location.hash === '#orders' && !isActive() && typeof root.goTo === 'function') root.goTo('orders');
      else if (isActive()) show();
    } else {
      ext('unmount');
      unmount();
      st.items = []; st.counts = {}; st.rev = null; st.open = null;
      if (location.hash === '#orders') leave();
    }
  }

  /** بخش خاموش است ولی کسی #orders را باز کرده ⇒ داشبورد */
  function leave() {
    if (location.hash === '#dashboard' && typeof root.goTo === 'function') root.goTo('dashboard');
    else location.hash = '#dashboard';
  }

  // ── زمان‌سنج: فقط وقتی برگه پیداست ─────────────────────────────────

  function tick() {
    if (!visible()) return;
    if (Date.now() - st.cfgAt > CONFIG_MS) { refreshConfig(); return; }
    if (st.enabled) pollSummary();
  }
  function startTimer() { if (!st.timer) st.timer = setInterval(tick, POLL_MS); }
  function stopTimer() { if (st.timer) { clearInterval(st.timer); st.timer = null; } }

  async function pollSummary() {
    if (!st.enabled || !visible()) return;
    try {
      const s = await call('/summary');
      const changed = st.rev !== null && s.rev !== st.rev;
      st.counts = s.counts || {};
      st.rev = s.rev;
      paintTabs();
      paintBadges();
      ext('summary', s);
      if (changed && isActive()) {
        loadList(true);
        if (st.open) openDetail(st.open.id, true);
      }
    } catch (e) {
      if (e && e.code === 'orders_disabled') { st.cfgAt = 0; refreshConfig(); return; }
      ext('summaryFailed', e);
    }
  }

  // ── ساختن و برداشتنِ بخش ──────────────────────────────────────────

  function mount() {
    if ($('page-orders')) return;
    const anchor = $('page-more');
    if (!anchor) return;
    const canCreate = !!(st.cfg && st.cfg.permissions && st.cfg.permissions.create);
    const sec = document.createElement('section');
    sec.className = 'page';
    sec.id = 'page-orders';
    sec.innerHTML = `
      <div class="page-toolbar">
        <div class="page-title-block">
          <h2>سفارش‌ها</h2>
          <p>ویترین، تلفن، تلگرام و واتس‌اپ — در یک صف</p>
        </div>
        <div class="toolbar-actions">
          <div class="ord-view" role="group" aria-label="نمای فهرست" id="ord-view">
            <button type="button" class="ord-view-btn" data-view="card" aria-label="نمای کارتی" title="نمای کارتی"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg></button>
            <button type="button" class="ord-view-btn" data-view="table" aria-label="نمای جدولی" title="نمای جدولی"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 5h18M3 12h18M3 19h18"/></svg></button>
          </div>
          ${canCreate ? '<button type="button" class="btn btn-primary btn-sm" id="ord-new-btn">+ سفارشِ دستی</button>' : ''}
        </div>
      </div>
      <p class="ord-hint ord-live-note">اعلانِ فوری (وقتی برنامه بسته است) هنوز وصل نیست؛ تا این برنامه باز است، صف هر چند ثانیه خودش تازه می‌شود.</p>
      <div class="ord-tabs" role="tablist" aria-label="برگه‌های سفارش" id="ord-tabs"></div>
      <div class="ord-list" id="ord-list" aria-live="polite"></div>
      <button type="button" class="btn btn-secondary btn-block ord-more" id="ord-more" hidden>سفارش‌های بیشتر</button>`;
    anchor.parentElement.insertBefore(sec, anchor);
    sec.addEventListener('click', onPageClick);

    //  منوی کناری: پس از «فروش»
    const nav = $('sidebar-nav');
    if (nav) {
      const li = document.createElement('li');
      li.id = 'ord-nav';
      li.innerHTML = `<a class="nav-item" data-page="orders" href="#orders"><span class="ic">${ICON}</span>سفارش‌ها<span class="ord-badge" data-ord-badge hidden></span></a>`;
      const sale = nav.querySelector('[data-page="sale"]');
      const after = sale && sale.closest('li');
      if (after && after.nextSibling) nav.insertBefore(li, after.nextSibling); else nav.appendChild(li);
    }
    //  «بیشتر» (گوشی): اولین کارت
    const more = $('more-nav-list');
    if (more) {
      const a = document.createElement('a');
      a.className = 'more-card';
      a.id = 'ord-more-card';
      a.dataset.page = 'orders';
      a.href = '#orders';
      a.innerHTML = `<span class="more-card-ic">${ICON}</span>
        <span class="more-card-txt"><b>سفارش‌ها <span class="ord-badge" data-ord-badge hidden></span></b><i>صفِ سفارش‌های آنلاین و تلفنی</i></span>
        <span class="more-card-go"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg></span>`;
      more.insertBefore(a, more.firstChild);
    }

    document.body.insertAdjacentHTML('beforeend', `
      <div class="modal-scrim ord-modal" id="ord-detail-modal">
        <div class="modal-box ord-detail-box" role="dialog" aria-modal="true" aria-labelledby="ord-detail-title">
          <div class="modal-head"><h3 id="ord-detail-title">سفارش</h3><button type="button" class="modal-close" data-ord-close aria-label="بستن">✕</button></div>
          <div id="ord-detail-body"></div>
        </div>
      </div>
      <div class="modal-scrim ord-modal" id="ord-act-modal">
        <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="ord-act-title">
          <div class="modal-head"><h3 id="ord-act-title">تغییرِ وضعیت</h3><button type="button" class="modal-close" data-ord-close aria-label="بستن">✕</button></div>
          <div id="ord-act-body"></div>
          <p class="ord-err" id="ord-act-err" role="alert"></p>
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" data-ord-close>انصراف</button>
            <button type="button" class="btn btn-primary" id="ord-act-ok">تأیید</button>
          </div>
        </div>
      </div>
      <div class="modal-scrim ord-modal" id="ord-new-modal">
        <div class="modal-box ord-new-box" role="dialog" aria-modal="true" aria-labelledby="ord-new-title">
          <div class="modal-head"><h3 id="ord-new-title">سفارشِ دستی</h3><button type="button" class="modal-close" data-ord-close aria-label="بستن">✕</button></div>
          <form id="ord-new-form" novalidate></form>
        </div>
      </div>`);
    document.querySelectorAll('.ord-modal').forEach((m) => m.addEventListener('click', onModalClick));
    $('ord-act-modal').addEventListener('input', paintActOk);
    $('ord-act-modal').addEventListener('change', paintActOk);
    $('ord-act-ok').addEventListener('click', confirmAct);
    $('ord-new-form').addEventListener('submit', submitNew);
    $('ord-new-form').addEventListener('click', onFormClick);
    $('ord-new-form').addEventListener('change', onFormChange);
  }

  function unmount() {
    ['page-orders', 'ord-nav', 'ord-more-card', 'ord-detail-modal', 'ord-act-modal', 'ord-new-modal']
      .forEach((id) => { const el = $(id); if (el) el.remove(); });
  }

  function closeModal(m) { if (m) m.classList.remove('open'); }
  function onModalClick(e) {
    const m = e.currentTarget;
    if (e.target === m || e.target.closest('[data-ord-close]')) {
      if (m.id === 'ord-detail-modal') closeDetail(m); else closeModal(m);
    }
  }

  // ── برگه‌ها و فهرست ────────────────────────────────────────────────

  function show() {
    const t = $('page-title');
    if (t) t.textContent = 'سفارش‌ها';
    paintTabs();
    loadList(true);
  }

  /** برگه‌ها یک بار ساخته می‌شوند و بعد فقط شمارش و انتخاب عوض می‌شود — دکمه زیرِ انگشت عوض نمی‌شود */
  function paintTabs() {
    const box = $('ord-tabs');
    if (!box) return;
    if (!box.children.length) {
      box.innerHTML = TABS.map((t) => `<button type="button" role="tab" class="ord-tab" data-tab="${t}" aria-selected="false">
        ${esc(TAB_LABELS[t])}<span class="ord-count" hidden></span></button>`).join('');
    }
    box.querySelectorAll('[data-tab]').forEach((b) => {
      const t = b.dataset.tab;
      const sel = t === st.tab;
      b.classList.toggle('on', sel);
      b.setAttribute('aria-selected', String(sel));
      const n = Number(st.counts[t] || 0);
      const c = b.querySelector('.ord-count');
      c.hidden = !n;
      c.textContent = n ? String(n) : '';
    });
  }

  function paintBadges() {
    const n = Number(st.counts.new || 0);
    document.querySelectorAll('[data-ord-badge]').forEach((b) => {
      b.hidden = !n;
      b.textContent = n ? String(n) : '';
    });
  }

  async function loadList(reset) {
    if (!st.enabled) return;
    const seq = ++st.listSeq;
    const tab = st.tab;
    st.loading = true;
    if (reset) { st.items = []; st.cursor = null; }
    paintList();
    try {
      let q = `?tab=${encodeURIComponent(tab)}&limit=${PAGE_SIZE}`;
      if (!reset && st.cursor) q += `&cursor=${encodeURIComponent(st.cursor)}`;
      const r = await call(q);
      if (seq !== st.listSeq) return;
      st.items = reset ? (r.items || []) : st.items.concat(r.items || []);
      st.cursor = r.nextCursor || null;
      st.err = '';
    } catch (e) {
      if (seq === st.listSeq) st.err = (e && e.message) || 'فهرست نیامد — اتصال را بررسی کنید.';
    } finally {
      if (seq === st.listSeq) { st.loading = false; paintList(); }
    }
  }

  function payLabel(o) {
    if (o.paymentStatus === 'paid') return 'پرداخت‌شده';
    if (o.paymentMethod === 'cod') return 'نقد هنگامِ تحویل';
    return 'پرداخت‌نشده';
  }

  function tags(o) {
    const out = [
      `<span class="ord-tag">${o.fulfillment === 'delivery' ? 'ارسال' : 'حضوری'}</span>`,
      `<span class="ord-tag">${Number(o.itemsCount || 0)} قلم</span>`,
      `<span class="ord-tag">${esc(payLabel(o))}</span>`,
      `<span class="ord-tag">${esc(SOURCE_LABELS[o.source] || o.source || '')}</span>`,
    ];
    if (o.isTest) out.push('<span class="ord-tag warn">آزمایشی</span>');
    return out.join('');
  }

  function timeOf(o) {
    if (o.scheduledFor) return `برای ${esc(when(o.scheduledFor))}`;
    return esc(ago(o.createdAt));
  }

  /** نشانِ وضعیت: متن + رنگ + نقطه، هرگز رنگِ تنها */
  const pill = (status) => `<span class="ord-status s-${esc(status)}"><i class="ord-dot" aria-hidden="true"></i>${esc(STATUS_LABELS[status] || status)}</span>`;

  // ── نمای کارتی / جدولی (VISUAL-DIRECTION §۱٫۵) ─────────────────────
  //  ترجیحِ نمایشی، روی همین دستگاه، جدا برای هر کاربر و هر صفحه؛ همگام نمی‌شود.
  //  گوشی همیشه کارتی (بی کلید)؛ کامپیوتر/تبلت پیش‌فرضِ صفحهٔ مدیریت: جدولی.
  const NARROW = root.matchMedia ? root.matchMedia('(max-width: 639px)') : null;
  const narrow = () => !!(NARROW && NARROW.matches);
  const viewKey = () => {
    const sh = shop();
    const u = String((sh && sh.account && sh.account.userId) || 'local').replace(/[^\w.@-]/g, '_');
    return `view_mode:${u}:orders`;
  };
  function viewMode() {
    if (narrow()) return 'card';
    let v = null;
    try { v = localStorage.getItem(viewKey()); } catch { /* حالتِ خصوصی */ }
    return v === 'card' || v === 'table' ? v : 'table';
  }
  function setView(v) {
    if (narrow()) return;
    try { localStorage.setItem(viewKey(), v); } catch { /* حالتِ خصوصی */ }
    paintList();
  }
  function paintView(mode) {
    const box = $('ord-view');
    if (!box) return;
    box.hidden = narrow();
    box.querySelectorAll('[data-view]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.view === mode)));
  }
  if (NARROW && NARROW.addEventListener) NARROW.addEventListener('change', () => paintList());

  function cardHtml(o) {
    return `
        <button type="button" class="ord-card" data-id="${esc(o.id)}">
          <span class="ord-card-top">
            <b>#${Number(o.orderNo)}</b>
            <span class="ord-name">${esc(o.customer && o.customer.name)}</span>
            ${pill(o.status)}
          </span>
          <span class="ord-tags">${tags(o)}</span>
          <span class="ord-card-foot"><span class="ord-time">${timeOf(o)}</span><b>${af(o.totalMinor)}</b></span>
        </button>`;
  }

  function tableHtml(items) {
    const rows = items.map((o) => `<tr data-id="${esc(o.id)}">
        <td><button type="button" class="ord-row-open" data-id="${esc(o.id)}">#${Number(o.orderNo)}</button>${o.isTest ? ' <span class="ord-tag warn">آزمایشی</span>' : ''}</td>
        <td>${esc(o.customer && o.customer.name)}<small dir="ltr">${esc((o.customer && o.customer.phoneMasked) || '')}</small></td>
        <td>${pill(o.status)}</td>
        <td>${o.fulfillment === 'delivery' ? 'ارسال' : 'حضوری'}</td>
        <td>${Number(o.itemsCount || 0)}</td>
        <td>${esc(payLabel(o))}</td>
        <td>${esc(SOURCE_LABELS[o.source] || o.source || '')}</td>
        <td>${timeOf(o)}</td>
        <td dir="ltr" class="ord-num">${money(o.totalMinor)}</td>
      </tr>`).join('');
    return `<div class="ord-table-wrap"><table class="ord-table">
      <thead><tr><th scope="col">سفارش</th><th scope="col">مشتری</th><th scope="col">وضعیت</th><th scope="col">تحویل</th>
        <th scope="col">اقلام</th><th scope="col">پرداخت</th><th scope="col">منبع</th><th scope="col">زمان</th><th scope="col">مبلغ (افغانی)</th></tr></thead>
      <tbody>${rows}</tbody></table></div>`;
  }

  function paintList() {
    const box = $('ord-list');
    if (!box) return;
    const more = $('ord-more');
    const mode = viewMode();
    paintView(mode);
    box.dataset.view = mode;
    if (st.err && !st.items.length) {
      box.innerHTML = `<div class="ord-empty" role="alert"><p>${esc(st.err)}</p><button type="button" class="btn btn-secondary btn-sm" id="ord-retry">تلاشِ دوباره</button></div>`;
    } else if (!st.items.length) {
      box.innerHTML = st.loading
        ? '<div class="ord-skeleton" aria-label="در حالِ آوردن"><i></i><i></i><i></i></div>'
        : `<p class="ord-empty">در برگهٔ «${esc(TAB_LABELS[st.tab])}» سفارشی نیست.${st.tab === 'new' && st.cfg && st.cfg.permissions && st.cfg.permissions.create ? ' سفارشِ تلفنی یا پیامی را با «+ سفارشِ دستی» ثبت کنید.' : ''}</p>`;
    } else {
      box.innerHTML = mode === 'table' ? tableHtml(st.items) : st.items.map(cardHtml).join('');
    }
    if (more) { more.hidden = !st.cursor; more.disabled = st.loading; }
  }

  function onPageClick(e) {
    const tab = e.target.closest('[data-tab]');
    if (tab) {
      st.tab = tab.dataset.tab;
      paintTabs();
      loadList(true);
      return;
    }
    const view = e.target.closest('.ord-view-btn');
    if (view) { setView(view.dataset.view); return; }
    const card = e.target.closest('.ord-card, .ord-table tbody tr');
    if (card) { openDetail(card.dataset.id); return; }
    if (e.target.closest('#ord-retry')) { loadList(true); return; }
    if (e.target.closest('#ord-more')) { loadList(false); return; }
    if (e.target.closest('#ord-new-btn')) openNew();
  }

  // ── جزئیات و خطِ زمانی ───────────────────────────────────────────

  /**
   *  فقط تازه‌ترین پرسش جای پنجره می‌نشیند: پاسخِ دیرِ سفارشی که بسته شد یا جایش
   *  سفارشِ دیگری باز شد، نه آن را دوباره «باز» می‌کند و نه دکمه‌هایش را روی
   *  سفارشِ تازه می‌گذارد — وگرنه گذار روی سفارشِ دیگری می‌نشست.
   */
  async function openDetail(id, quiet) {
    const m = $('ord-detail-modal');
    if (!m) return;
    const seq = ++st.detailSeq;
    if (!quiet) {
      st.open = null;
      $('ord-detail-body').innerHTML = '<p class="ord-empty">در حالِ آوردن…</p>';
      m.classList.add('open');
    }
    const current = () => seq === st.detailSeq && m.classList.contains('open');
    try {
      const r = await call('/' + encodeURIComponent(id));
      if (!current()) return;
      st.open = r.order;
      paintDetail();
    } catch (e) {
      if (!quiet && current()) $('ord-detail-body').innerHTML = `<p class="ord-empty">${esc((e && e.message) || 'سفارش نیامد.')}</p>`;
    }
  }

  function closeDetail(m) {
    closeModal(m);
    st.open = null;
    st.detailSeq++;
  }

  function actorName(e) {
    if (e.actorType === 'system') return 'سیستم';
    if (e.actorType === 'customer') return 'مشتری';
    const sh = shop();
    const me = sh && sh.account && sh.account.userId;
    if (me && e.actorId === me) return 'شما';
    const members = (sh && sh.state && sh.state.members) || [];
    const m = members.find((x) => x && x.userId === e.actorId);
    return (m && (m.name || m.label)) || 'کارمند';
  }

  function paintDetail() {
    const o = st.open;
    const body = $('ord-detail-body');
    if (!o || !body) return;
    $('ord-detail-title').textContent = `سفارش #${Number(o.orderNo)}`;
    const c = o.customer || {};
    const phone = o.canContact && c.phone
      ? `<a class="ord-phone" dir="ltr" href="tel:${esc(c.phone)}">${esc(c.phone)}</a> <a class="btn btn-secondary btn-sm" href="tel:${esc(c.phone)}">تماس</a>`
      : (c.phoneMasked ? `<span class="ord-phone" dir="ltr">${esc(c.phoneMasked)}</span> <span class="ord-hint">شمارهٔ کامل فقط برای صاحب و مدیرِ دکان</span>` : '<span class="ord-hint">شماره ندارد</span>');
    const addr = o.address || {};
    const addrBits = [addr.text, addr.landmark && `نشانه: ${addr.landmark}`].filter(Boolean).map(esc);
    if (addr.lat != null && addr.lng != null) {
      addrBits.push(`<a href="https://www.openstreetmap.org/?mlat=${Number(addr.lat)}&amp;mlon=${Number(addr.lng)}#map=18/${Number(addr.lat)}/${Number(addr.lng)}" target="_blank" rel="noopener">پین روی نقشه</a>`);
    }
    const lines = (o.lines || []).map((l) => `<tr>
        <td>${esc(l.name)}${l.note ? `<small>${esc(l.note)}</small>` : ''}</td>
        <td dir="ltr">${esc(String(Number(l.qtyMilli) / 1000))}${l.unit ? ` ${esc(l.unit)}` : ''}</td>
        <td dir="ltr">${money(l.unitPriceMinor)}</td>
        <td dir="ltr">${money(l.lineTotalMinor)}</td></tr>`).join('');
    const events = (o.events || []).map((e) => {
      const own = ext('eventLine', e); if (own) return own;
      const what = e.kind === 'created' ? 'ثبت شد'
        : e.kind === 'cod' ? `نقد: ${esc(COD_LABELS[e.toStatus] || e.toStatus)}`
          : `${esc(STATUS_LABELS[e.fromStatus] || e.fromStatus)} ← ${esc(STATUS_LABELS[e.toStatus] || e.toStatus)}`;
      const ready = e.detail && e.detail.readyAt ? ` · آماده تا ${esc(when(e.detail.readyAt))}` : '';
      return `<li><time>${esc(when(e.at))}</time><b>${what}</b>
        <span>${esc(actorName(e))}${e.reason ? ` · دلیل: ${esc(REASON_LABELS[e.reason] || e.reason)}` : ''}${ready}</span>
        ${e.note ? `<span class="ord-note">${esc(e.note)}</span>` : ''}</li>`;
    }).join('');
    const perms = (st.cfg && st.cfg.permissions) || {};
    const acts = actionsFor(o, perms).map((to) => `<button type="button" class="btn ${DANGER.includes(to) ? 'btn-danger' : 'btn-primary'} btn-sm" data-act="${esc(to)}">${esc(ACTION_LABELS[to] || to)}</button>`).join('')
      + (ext('detailButtons', o) || '');
    body.innerHTML = `
      <p class="ord-status-line">${pill(o.status)}
        ${o.statusReason ? `<span class="ord-hint">دلیل: ${esc(REASON_LABELS[o.statusReason] || o.statusReason)}</span>` : ''}
        ${o.readyAt ? `<span class="ord-hint">آماده تا ${esc(when(o.readyAt))}</span>` : ''}</p>
      <p class="ord-tags">${tags(o)}<span class="ord-tag">${timeOf(o)}</span></p>
      ${acts ? `<div class="ord-actions" id="ord-actions">${acts}</div>` : ''}
      <h4 class="ord-h">مشتری</h4>
      <p class="ord-cust"><b>${esc(c.name)}</b> ${phone}</p>
      ${o.fulfillment === 'delivery' ? `<h4 class="ord-h">نشانی</h4><p>${addrBits.join(' · ') || '—'}</p>` : ''}
      ${o.note ? `<h4 class="ord-h">یادداشت</h4><p class="ord-note">${esc(o.note)}</p>` : ''}
      <h4 class="ord-h">اقلام</h4>
      <div class="ord-table-wrap"><table class="ord-lines"><thead><tr><th>قلم</th><th>مقدار</th><th>فی</th><th>جمع</th></tr></thead><tbody>${lines}</tbody></table></div>
      <dl class="ord-sum">
        <dt>جمعِ اقلام</dt><dd dir="ltr">${money(o.subtotalMinor)}</dd>
        ${o.discountMinor ? `<dt>تخفیف</dt><dd dir="ltr">-${money(o.discountMinor)}</dd>` : ''}
        ${o.deliveryFeeMinor ? `<dt>هزینهٔ ارسال</dt><dd dir="ltr">${money(o.deliveryFeeMinor)}</dd>` : ''}
        <dt><b>جمعِ کل</b></dt><dd dir="ltr"><b>${af(o.totalMinor)}</b></dd>
      </dl>
      <h4 class="ord-h">خطِ زمانی</h4>
      <ol class="ord-timeline">${events}</ol>`;
    const box = $('ord-actions');
    if (box) box.addEventListener('click', (e) => { if (ext('detailClick', e)) return; const b = e.target.closest('[data-act]'); if (b) startAct(b.dataset.act); });
  }

  // ── گذار: دلیل، زمانِ آماده، تأیید ─────────────────────────────────

  function startAct(to) {
    const o = st.open;
    if (!o) return;
    const reasons = st.cfg && st.cfg.reasons;
    const needR = needsReason(to, reasons);
    if (!needR && to !== 'accepted' && !MONEY.includes(to)) { send({ to }); return; }
    st.act = { to };
    $('ord-act-title').textContent = `${ACTION_LABELS[to] || to} — سفارش #${Number(o.orderNo)}`;
    let html = '';
    if (needR) {
      html += `<fieldset class="ord-reasons"><legend>دلیل</legend>${reasonChoices(o.status, to, reasons).map((r, i) => `
        <label class="ord-radio"><input type="radio" name="ord-reason" value="${esc(r)}"${i === 0 && reasonChoices(o.status, to, reasons).length === 1 ? ' checked' : ''}> ${esc(REASON_LABELS[r] || r)}</label>`).join('')}</fieldset>`;
    }
    if (to === 'accepted') {
      html += `<div class="form-group"><label for="ord-ready">زمانِ آماده شدن</label>
        <select class="form-input" id="ord-ready"><option value="">بی زمان</option>${READY_MINUTES.map((m) => `<option value="${m}"${m === 15 ? ' selected' : ''}>${m} دقیقه</option>`).join('')}</select></div>`;
    }
    if (needR) {
      html += `<div class="form-group"><label for="ord-act-note">توضیح <small>(برای «دلیلِ دیگر» لازم است)</small></label>
        <textarea class="form-textarea" id="ord-act-note" maxlength="300"></textarea></div>`;
    }
    if (MONEY.includes(to)) html += `<p>پول به مشتری برمی‌گردد (${esc(af(o.totalMinor))}). مطمئنید؟</p>`;
    $('ord-act-body').innerHTML = html;
    $('ord-act-err').textContent = '';
    $('ord-act-modal').classList.add('open');
    paintActOk();
  }

  function actInput() {
    const r = document.querySelector('#ord-act-body input[name="ord-reason"]:checked');
    const n = $('ord-act-note');
    const ready = $('ord-ready');
    return { reason: r ? r.value : '', note: n ? n.value.trim() : '', ready: ready ? ready.value : '' };
  }

  function paintActOk() {
    const ok = $('ord-act-ok');
    if (!ok || !st.act) return;
    const v = actInput();
    const needR = needsReason(st.act.to, st.cfg && st.cfg.reasons);
    ok.disabled = st.busy || (needR && (!v.reason || (v.reason === 'other' && !v.note)));
  }

  function confirmAct() {
    if (!st.act) return;
    const v = actInput();
    const body = { to: st.act.to };
    if (v.reason) body.reason = v.reason;
    if (v.note) body.note = v.note;
    if (st.act.to === 'accepted' && v.ready) body.readyInMinutes = Number(v.ready);
    send(body);
  }

  async function send(body) {
    const o = st.open;
    if (!o || st.busy) return;
    st.busy = true;
    paintActOk();
    try {
      const r = await call('/' + encodeURIComponent(o.id) + '/transition', { method: 'POST', body: { ...body, from: o.status } });
      st.open = r.order;
      st.detailSeq++;   // تازه‌سازیِ در راه از پیش از این گذار است
      closeModal($('ord-act-modal'));
      st.act = null;
      paintDetail();
      toast(`سفارش #${Number(r.order.orderNo)}: ${STATUS_LABELS[r.order.status] || r.order.status}`);
    } catch (e) {
      if (e && e.code === 'status_changed') {
        //  کسِ دیگری زودتر زد: پنجره بسته، وضعیتِ تازه روی صفحه
        closeModal($('ord-act-modal'));
        st.act = null;
        toast('وضعیتِ این سفارش همین حالا جای دیگری عوض شد؛ وضعیتِ تازه را ببینید.');
        openDetail(o.id, true);
      } else {
        const msg = (e && e.message) || 'نشد — دوباره بزنید.';
        if ($('ord-act-modal').classList.contains('open')) $('ord-act-err').textContent = msg; else toast(msg);
      }
    } finally {
      st.busy = false;
      paintActOk();
      pollSummary();
      if (isActive()) loadList(true);
    }
  }

  // ── سفارشِ دستی (ORD-21) ───────────────────────────────────────────

  function lineRow(i) {
    return `<div class="ord-line" data-line>
      <input class="form-input" name="ln-name" aria-label="نامِ قلمِ ${i + 1}" placeholder="نامِ قلم" maxlength="200">
      <input class="form-input" name="ln-qty" aria-label="مقدارِ قلمِ ${i + 1}" placeholder="مقدار" inputmode="decimal" dir="ltr" value="1">
      <input class="form-input" name="ln-price" aria-label="فیِ قلمِ ${i + 1} (افغانی)" placeholder="فی" inputmode="decimal" dir="ltr">
      <button type="button" class="btn btn-secondary btn-sm" data-del-line aria-label="حذفِ قلمِ ${i + 1}">✕</button>
    </div>`;
  }

  function openNew() {
    const form = $('ord-new-form');
    if (!form) return;
    //  کلید یک بار برای همین فرم؛ تکرارِ «ثبت» (قطعِ نت، دو بار زدن) همان سفارش است
    st.formKey = newKey();
    const sources = (st.cfg && st.cfg.sources) || ['phone', 'telegram', 'whatsapp', 'manual'];
    form.innerHTML = `
      <div class="form-row-2">
        <div class="form-group"><label for="ord-f-source">از کجا</label>
          <select class="form-input" id="ord-f-source" name="source">${sources.map((s) => `<option value="${esc(s)}">${esc(SOURCE_LABELS[s] || s)}</option>`).join('')}</select></div>
        <div class="form-group"><label for="ord-f-ful">تحویل</label>
          <select class="form-input" id="ord-f-ful" name="fulfillment"><option value="pickup">حضوری (از دکان)</option><option value="delivery">ارسال</option></select></div>
      </div>
      <div class="form-row-2">
        <div class="form-group"><label for="ord-f-name">نامِ مشتری</label><input class="form-input" id="ord-f-name" name="name" maxlength="80" autocomplete="off"></div>
        <div class="form-group"><label for="ord-f-phone">شماره</label><input class="form-input" id="ord-f-phone" name="phone" inputmode="tel" dir="ltr" maxlength="20" autocomplete="off"></div>
      </div>
      <div id="ord-f-delivery" hidden>
        <div class="form-group"><label for="ord-f-addr">نشانی</label><input class="form-input" id="ord-f-addr" name="addressText" maxlength="300"></div>
        <div class="form-row-2">
          <div class="form-group"><label for="ord-f-landmark">نشانه</label><input class="form-input" id="ord-f-landmark" name="landmark" maxlength="200"></div>
          <div class="form-group"><label for="ord-f-fee">هزینهٔ ارسال (افغانی)</label><input class="form-input" id="ord-f-fee" name="deliveryFee" inputmode="decimal" dir="ltr"></div>
        </div>
      </div>
      <fieldset class="ord-lines-box"><legend>اقلام <small>(مقدار و فی به افغانی)</small></legend>
        <div id="ord-f-lines">${lineRow(0)}</div>
        <button type="button" class="btn btn-secondary btn-sm" id="ord-f-addline">+ قلمِ دیگر</button>
      </fieldset>
      <div class="form-group"><label for="ord-f-note">یادداشت</label><textarea class="form-textarea" id="ord-f-note" name="note" maxlength="500"></textarea></div>
      <label class="ord-radio"><input type="checkbox" name="test" id="ord-f-test"> سفارشِ آزمایشی (برای آزمودنِ صف و چاپ)</label>
      <p class="ord-err" id="ord-new-err" role="alert"></p>
      <div class="modal-actions">
        <button type="button" class="btn btn-secondary" data-ord-close>انصراف</button>
        <button type="submit" class="btn btn-primary" id="ord-new-ok">ثبتِ سفارش</button>
      </div>`;
    $('ord-new-modal').classList.add('open');
    const first = $('ord-f-name');
    if (first) first.focus();
  }

  function onFormChange(e) {
    if (e.target && e.target.id === 'ord-f-ful') $('ord-f-delivery').hidden = e.target.value !== 'delivery';
  }

  function onFormClick(e) {
    if (e.target.closest('#ord-f-addline')) {
      const box = $('ord-f-lines');
      box.insertAdjacentHTML('beforeend', lineRow(box.querySelectorAll('[data-line]').length));
      return;
    }
    const del = e.target.closest('[data-del-line]');
    if (del) {
      const box = $('ord-f-lines');
      if (box.querySelectorAll('[data-line]').length > 1) del.closest('[data-line]').remove();
    }
  }

  function readForm() {
    const f = $('ord-new-form');
    const v = (n) => { const el = f.elements[n]; return el ? el.value : ''; };
    return {
      source: v('source'), fulfillment: v('fulfillment'), name: v('name'), phone: v('phone'),
      addressText: v('addressText'), landmark: v('landmark'), deliveryFee: v('deliveryFee'), note: v('note'),
      test: !!(f.elements.test && f.elements.test.checked),
      lines: [...f.querySelectorAll('[data-line]')].map((row) => ({
        name: row.querySelector('[name="ln-name"]').value,
        qty: row.querySelector('[name="ln-qty"]').value,
        price: row.querySelector('[name="ln-price"]').value,
      })),
    };
  }

  async function submitNew(e) {
    e.preventDefault();
    if (st.busy) return;
    const out = buildCreateBody(readForm());
    const err = $('ord-new-err');
    if (!out.ok) { err.textContent = out.error; return; }
    err.textContent = '';
    st.busy = true;
    const ok = $('ord-new-ok');
    ok.disabled = true;
    try {
      const r = await call('', { method: 'POST', body: { ...out.body, clientKey: st.formKey } });
      st.formKey = '';
      closeModal($('ord-new-modal'));
      toast(`سفارش #${Number(r.order.orderNo)} ثبت شد`);
      st.tab = r.order.tab || 'new';
      paintTabs();
      loadList(true);
      pollSummary();
    } catch (x) {
      err.textContent = x && x.code === 'network'
        ? 'اتصال قطع شد. دوباره «ثبتِ سفارش» را بزنید — سفارش دو بار ثبت نمی‌شود.'
        : ((x && x.message) || 'ثبت نشد — دوباره بزنید.');
    } finally {
      st.busy = false;
      ok.disabled = false;
    }
  }

  // ── راه‌اندازی ─────────────────────────────────────────────────────

  document.addEventListener('tohid:page-change', (e) => {
    const id = e.detail && e.detail.pageId;
    if (id === 'orders') {
      if (st.enabled) show();
      else if (st.known) leave();
      else refreshConfig();
    } else if (id === 'more' && Date.now() - st.cfgAt > 60 * 1000) {
      refreshConfig();
    }
  });

  document.addEventListener('visibilitychange', () => {
    if (!visible()) { stopTimer(); return; }
    startTimer();
    if (Date.now() - st.cfgAt > 60 * 1000) refreshConfig(); else if (st.enabled) pollSummary();
  });

  //  §۳۰.۲ بندِ ۴: برگشتِ اتصال ⇐ صف از سرور دوباره، تا هیچ سفارشی جا نماند
  window.addEventListener('online', () => {
    if (!visible()) return;
    if (!st.known || Date.now() - st.cfgAt > 60 * 1000) refreshConfig(); else if (st.enabled) pollSummary();
  });

  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    const open = [...document.querySelectorAll('.ord-modal.open')].pop();
    if (open) { if (open.id === 'ord-detail-modal') closeDetail(open); else closeModal(open); }
  });

  /**
   * باز کردنِ یک سفارش با شناسه — راهِ ورودِ اعلان (§۳۰.۱ بندِ ۵): جزئیات
   * همیشه تازه از سرور و با دسترسیِ همین کاربر خوانده می‌شود، نه از متنِ اعلان.
   */
  async function openOrder(id) {
    if (!st.known) await refreshConfig();
    if (!st.enabled || !id) return false;
    if (!isActive() && typeof root.goTo === 'function') root.goTo('orders');
    openDetail(String(id));
    return true;
  }

  root.TohidOrders = { refreshConfig, pollSummary, openOrder, get enabled() { return st.enabled; }, core };

  refreshConfig();
  if (visible()) startTimer();
})(typeof window !== 'undefined' ? window : globalThis);
