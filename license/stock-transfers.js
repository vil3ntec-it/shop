/**
 * «انتقالِ موجودی» ِ نسخهٔ وب (INV-11، S21) — صفحهٔ «انبار» ⇐ دکمهٔ «انتقالِ موجودی».
 *
 * همان درهای سرور که برنامهٔ گوشی و کامپیوتر می‌زنند (`/api/v1/shop/catalog/transfers`): فهرست، پیش‌نویس (روی همین
 * مرورگر)، فرستادن، دریافتِ ناقص در چند گام، بستن با کسریِ دلیل‌دار و رد. هیچ عددی این‌جا «حساب» نمی‌شود: موجودی،
 * «در مسیر»، کسری و گذارِ وضعیت را سرور می‌گوید؛ این‌جا فقط ورودی سنجیده می‌شود تا درخواستِ بیهوده نرود.
 *
 * ⛔ پیش‌فرض خاموش: دکمه فقط وقتی ساخته می‌شود که `GET …/catalog/config` بگوید `enabled` (پرچمِ `shop.catalog`)؛
 *    بی اجازهٔ `stock.move` فقط دیدن. خاموش ⇒ نه دکمه، نه پنجره، نه درخواستِ دیگر.
 * ⛔ هیچ واحدی دو بار نه: کلیدِ یکتاییِ فرستادن (`clientKey`) **درونِ خودِ پیش‌نویس** است (ماندگار روی مرورگر)، پس ویرایش،
 *    بستن یا بازخوانیِ صفحه پس از قطعِ شبکه کلیدِ تازه نمی‌سازد: یا همان ارسال یک بار می‌نشیند، یا سرور با ۴۰۹ می‌گوید
 *    ارسالِ پیشین ثبت شده بود. فرمِ دریافت/رد همیشه روی حالِ تازهٔ سرور باز می‌شود و کلیدش تا پاسخِ قطعی می‌ماند.
 * ⛔ هیچ متنِ فارسی در این پرونده نیست: همه از واژه‌نامهٔ `license/i18n/transfers.<زبان>.json` (fa، en، ps)؛
 *    هیچ اسکریپت یا رویدادِ درون‌خطی ساخته نمی‌شود (CSP). متنِ کاربر و سرور همیشه با `esc`. زبانِ صفحه که عوض شود
 *    (`tohid:lang` ِ `web-i18n.js`) واژه‌نامهٔ همان زبان بار و پنجره دوباره نقاشی می‌شود، بی بارگذاریِ دوبارهٔ صفحه و بی گم
 *    شدنِ آنچه در فرم نوشته شده؛ نامِ کالا `data-raw` دارد (دادهٔ دفتر، هرگز ترجمه نمی‌شود).
 * ⛔ `index.html` یخ‌زده است ⇒ این پرونده را `orders-ui.js` بار می‌کند (هیچ خطی به index.html اضافه نمی‌شود).
 * INV-01: زبانهٔ سومِ همین پنجره «موجودیِ هر مکان» — موجودیِ سرور برای هر کالا در قفسه، انبار، خراب و در مسیر (همان
 *    `GET …/catalog/products`؛ عضوِ محدود فقط مکان‌های محدوده‌اش را می‌گیرد، STF-06) و جمعِ همان مکان‌ها؛ فقط دیدن، هیچ حسابی جز جمع.
 *
 * بخشِ بالایی («هسته») خالص است و در Node هم بار می‌شود (`server/test/web-transfers-core.test.js`).
 */
(function (root) {
  'use strict';

  // ══════════════════════════════ هسته ══════════════════════════════

  const LOCS = ['shop_floor', 'warehouse'];
  //  همهٔ مکان‌های موجودیِ سرور (`stock.LOCATIONS`)؛ خراب و در مسیر فقط دیدنی‌اند، نه مبدأ/مقصدِ انتقال
  const PLACES = ['shop_floor', 'warehouse', 'damaged', 'in_transit'];
  const TABS = ['open', 'all', 'stock'];
  const OPEN = ['in_transit', 'partial'];
  const STATUSES = ['in_transit', 'partial', 'received', 'rejected'];
  const MAX_LINES = 100;
  const MAX_QTY_MILLI = 1000000000;
  const LANGS = ['fa', 'en', 'ps'];

  /** رقمِ فارسی/عربی ⇒ لاتین؛ ممیزِ «٫» و «،» ⇒ نقطه */
  function latin(s) {
    return String(s == null ? '' : s)
      .replace(/[\u06F0-\u06F9]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
      .replace(/[\u0660-\u0669]/g, (d) => String(d.charCodeAt(0) - 0x0660))
      .replace(/[\u066B\u060C]/g, '.')
      .trim();
  }

  /** متنِ مقدار ⇒ هزارم؛ نادرست ⇒ null. `zero` یعنی صفر هم درست است (دریافت) */
  function toMilli(text, zero) {
    const t = latin(text);
    if (!/^\d{1,7}(\.\d{1,3})?$/.test(t)) return null;
    const m = Math.round(Number(t) * 1000);
    if (!Number.isSafeInteger(m) || m > MAX_QTY_MILLI || m < (zero ? 0 : 1)) return null;
    return m;
  }

  /** هزارم ⇒ متن (بی اعشارِ بی‌مورد، رقمِ لاتین) */
  function qtyText(milli) {
    const n = Number(milli) || 0;
    const whole = Math.trunc(n / 1000);
    const frac = Math.abs(n % 1000);
    return frac ? `${n < 0 && whole === 0 ? '-' : ''}${whole}.${String(frac).padStart(3, '0').replace(/0+$/, '')}` : String(whole);
  }

  const isOpen = (t) => !!t && OPEN.includes(t.status);

  /** INV-01 — یک کالای کاتالوگِ سرور ⇒ موجودیِ هر مکانِ `places` (عددِ ناجور یا نبود ⇒ ۰) و جمعِ همان مکان‌ها */
  function placesRow(p, places) {
    const lv = (p && p.stock && p.stock.levels) || {};
    const cells = places.map((l) => (Number.isSafeInteger(lv[l]) ? lv[l] : 0));
    return { id: String(p && p.id), name: String((p && (p.name || p.id)) || ''), cells, totalMilli: cells.reduce((a, b) => a + b, 0) };
  }

  /** پیش‌نویس ⇒ بدنهٔ `POST /transfers` یا { error, n } (کلیدِ واژه‌نامه و شمارهٔ ردیف از یک) */
  function draftBody(d) {
    const from = d && d.from;
    const to = d && d.to;
    if (!LOCS.includes(from) || !LOCS.includes(to) || from === to) return { error: 'draft.same' };
    const lines = Array.isArray(d.lines) ? d.lines : [];
    if (!lines.length) return { error: 'draft.empty' };
    if (lines.length > MAX_LINES) return { error: 'draft.tooMany' };
    const out = [];
    for (let i = 0; i < lines.length; i++) {
      const q = toMilli(lines[i].qty, false);
      if (q === null || !lines[i].productId) return { error: 'draft.badQty', n: i + 1 };
      out.push({ productId: String(lines[i].productId), qtyMilli: q });
    }
    return { body: { from, to, lines: out, note: String(d.note || '').trim().slice(0, 300) } };
  }

  /**
   * دریافت ⇒ بدنهٔ `POST /transfers/:id/receive` یا { error, n }. `got` = { productId: متن }؛ نبود ⇒ صفر.
   * همهٔ ردیف‌های هنوز در مسیر فرستاده می‌شوند (حتی صفر)، تا «بستن» هرگز «بقیه رسید» ِ ناخواسته نشود.
   */
  function receiveBody(t, got, final, reason) {
    const lines = (t && Array.isArray(t.lines) ? t.lines : []).filter((l) => Number(l.pendingMilli) > 0);
    const out = [];
    let short = 0;
    let any = 0;
    for (let i = 0; i < lines.length; i++) {
      const raw = got && got[lines[i].productId];
      const q = raw === undefined || raw === null || latin(raw) === '' ? 0 : toMilli(raw, true);
      if (q === null) return { error: 'recv.badQty', n: i + 1 };
      if (q > Number(lines[i].pendingMilli)) return { error: 'recv.over', n: i + 1 };
      short += Number(lines[i].pendingMilli) - q;
      any += q;
      out.push({ productId: lines[i].productId, receivedMilli: q });
    }
    const why = String(reason || '').trim().slice(0, 120);
    if (!final && any === 0) return { error: 'recv.nothing' };
    if (final && short > 0 && !why) return { error: 'recv.needReason' };
    return { body: { final: !!final, lines: out, ...(why ? { reason: why } : {}) } };
  }

  function rejectBody(reason) {
    const why = String(reason || '').trim().slice(0, 120);
    return why ? { body: { reason: why } } : { error: 'rej.needReason' };
  }

  /** کلیدِ یکتایی — همان الگوی `KEY_RE` ِ سرور */
  function newKey(rand) {
    const r = typeof rand === 'function' ? rand : () => Math.random();
    let s = 'webxfr-' + Date.now().toString(36) + '-';
    for (let i = 0; i < 16; i++) s += Math.floor(r() * 36).toString(36);
    return s;
  }

  /** «{name}» ⇒ مقدار */
  function fill(text, vars) {
    return String(text).replace(/\{(\w+)\}/g, (m, k) => (vars && vars[k] !== undefined && vars[k] !== null ? String(vars[k]) : m));
  }

  const langOf = (raw) => {
    const l = String(raw || '').toLowerCase().slice(0, 2);
    return LANGS.includes(l) ? l : 'fa';
  };

  const core = { LOCS, PLACES, TABS, OPEN, STATUSES, MAX_LINES, LANGS, latin, toMilli, qtyText, isOpen, placesRow, draftBody, receiveBody, rejectBody, newKey, fill, langOf };
  if (typeof module !== 'undefined' && module.exports) { module.exports = core; return; }
  if (typeof document === 'undefined') return;

  // ══════════════════════════════ مرورگر ══════════════════════════════

  const API = '/api/v1/shop/catalog';
  const DRAFT_KEY = 'tohid-stock-transfer-draft-v1';
  const $ = (id) => document.getElementById(id);
  //  همان escapeHtml ِ خودِ برنامهٔ وب (SEC-API-01، index.html) — نسخهٔ دوم ساخته نمی‌شود؛ نبودش ⇒ صفحه ساخته نمی‌شود
  const escOk = () => typeof root.escapeHtml === 'function';
  const esc = (s) => root.escapeHtml(String(s == null ? '' : s));
  const shop = () => root.TohidShop;
  const call = (path, opts) => shop().apiAuth(API + path, opts);
  const toast = (m) => { try { shop()._toast(m); } catch { /* no toast */ } };

  const st = {
    dict: null, cfg: null, busy: false, tab: 'open', list: [], loading: false, err: '',
    view: 'list', open: null, draft: null, found: [], names: {}, act: null, key: '', listSeq: 0, findSeq: 0, dictLang: '', langSeq: 0,
    sq: '', sItems: [], sNext: null, sFresh: null,
  };
  const t = (k, vars) => fill(st.dict && Object.prototype.hasOwnProperty.call(st.dict, k) ? st.dict[k] : k, vars);
  const loc = (l) => t(`loc.${l}`);
  const canMove = () => !!(st.cfg && st.cfg.permissions && st.cfg.permissions.move);
  const places = () => (st.cfg && Array.isArray(st.cfg.locations) ? LOCS.filter((l) => st.cfg.locations.includes(l)) : LOCS);
  //  ستون‌های «موجودیِ هر مکان»: همان مکان‌هایی که سرور برای این عضو می‌گوید (محدوده ⇒ فقط همان‌ها)
  const stockPlaces = () => (st.cfg && Array.isArray(st.cfg.locations) ? PLACES.filter((l) => st.cfg.locations.includes(l)) : PLACES);
  //  هر بار از نو، نه یک قالبِ ثابت از بارِ نخست: تقویم و زبانِ صفحه بی بارگذاریِ دوباره عوض می‌شوند (`web-i18n.js`)
  const WHEN = { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' };
  const when = (ms) => {
    if (!ms) return '';
    const d = new Date(Number(ms));
    try { return d.toLocaleString('fa-AF-u-nu-latn', WHEN); } catch { return d.toLocaleString(); }
  };
  function errText(e) {
    if (e && e.code === 'network') return t('err.network');
    const k = e && e.code ? `err.${e.code}` : '';
    if (k && st.dict && st.dict[k]) return t(k);
    return (e && e.message) || t('err.generic');
  }

  // ── واژه‌نامه و پرچم ─────────────────────────────────────────────────

  async function loadDict() {
    if (st.dict) return true;
    const lang = langOf(document.documentElement.lang);
    for (const l of lang === 'fa' ? ['fa'] : [lang, 'fa']) {
      try {
        const r = await fetch(`license/i18n/transfers.${l}.json?v=1`, { cache: 'no-cache' });
        if (r.ok) { st.dict = await r.json(); st.dictLang = lang; return true; }
      } catch { /* next */ }
    }
    return false;
  }

  /** زبان یا تقویمِ صفحه عوض شد ⇒ واژه‌نامهٔ همان زبان (دری = فارسی + لایهٔ واژهٔ web-i18n) و نقاشیِ دوباره */
  async function relang() {
    const seq = ++st.langSeq;
    if (st.dict && st.dictLang !== langOf(document.documentElement.lang)) {
      const prev = st.dict;
      st.dict = null;
      const ok = await loadDict();
      if (seq !== st.langSeq) return;
      if (!ok) { st.dict = prev; return; }
    }
    if (!st.dict) return;
    const b = $('xfr-btn');
    const label = b && b.querySelector('span');
    if (label) label.textContent = t('btn');
    const x = document.querySelector('#xfr-modal .modal-close');
    if (x) x.setAttribute('aria-label', t('close'));
    const m = $('xfr-modal');
    if (m && m.classList.contains('open')) repaintKeep();
  }

  /** نقاشیِ دوباره بی گم شدنِ آنچه کاربر در فرمِ باز نوشته (مقدارِ دریافت، دلیل، تیکِ «بستن») */
  function repaintKeep() {
    const id = (i) => `${i.name}|${i.dataset.id || ''}|${i.dataset.i || ''}`;
    const kept = new Map();
    const body = $('xfr-body');
    if (body) body.querySelectorAll('input').forEach((i) => kept.set(id(i), i.type === 'checkbox' ? i.checked : i.value));
    paint();
    const fresh = $('xfr-body');
    if (!fresh) return;
    fresh.querySelectorAll('input').forEach((i) => {
      if (!kept.has(id(i))) return;
      if (i.type === 'checkbox') i.checked = kept.get(id(i)); else i.value = kept.get(id(i));
    });
  }

  async function refresh() {
    const sh = shop();
    if (!sh || !sh.account || !sh.account.loggedIn) { apply(null); return; }
    let cfg = null;
    try { cfg = await call('/config'); } catch { return; }   //  قطعِ گذرا چیزی را خاموش نمی‌کند
    if (!cfg || !cfg.enabled) { apply(null); return; }
    if (!(await loadDict())) return;
    apply(cfg);
  }

  function apply(cfg) {
    st.cfg = cfg;
    if (!cfg) { unmount(); return; }
    mount();
  }

  // ── ساختن ───────────────────────────────────────────────────────────

  const ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h13l-3-3M20 17H7l3 3"/></svg>';

  function mount() {
    if ($('xfr-btn') || !escOk()) return;
    const bar = document.querySelector('#page-warehouse .toolbar-actions');
    if (!bar) return;
    if (!document.querySelector('link[data-xfr-css]')) {
      const css = Object.assign(document.createElement('link'), { rel: 'stylesheet', href: 'license/stock-transfers.css?v=1' });
      css.dataset.xfrCss = '1';
      document.head.appendChild(css);
    }
    const b = document.createElement('button');
    b.type = 'button';
    b.id = 'xfr-btn';
    b.className = 'btn btn-secondary btn-sm';
    b.innerHTML = `${ICON}<span>${esc(t('btn'))}</span>`;
    b.addEventListener('click', openPanel);
    bar.insertBefore(b, bar.firstChild && bar.firstChild.nextSibling);
    document.body.insertAdjacentHTML('beforeend', `
      <div class="modal-scrim xfr-modal" id="xfr-modal">
        <div class="modal-box xfr-box" role="dialog" aria-modal="true" aria-labelledby="xfr-title">
          <div class="modal-head"><h3 id="xfr-title"></h3><button type="button" class="modal-close" data-xfr="close" aria-label="${esc(t('close'))}">✕</button></div>
          <div id="xfr-body" class="xfr-body"></div>
          <p class="xfr-err" id="xfr-err" role="alert"></p>
        </div>
      </div>`);
    const m = $('xfr-modal');
    m.addEventListener('click', onClick);
    m.addEventListener('submit', onSubmit);
    m.addEventListener('input', onInput);
    m.addEventListener('change', onInput);
  }

  function unmount() {
    ['xfr-btn', 'xfr-modal'].forEach((id) => { const el = $(id); if (el) el.remove(); });
  }

  // ── پنجره ───────────────────────────────────────────────────────────

  function openPanel() {
    const m = $('xfr-modal');
    if (!m) return;
    m.classList.add('open');
    st.view = 'list';
    paint();
    reloadTab();
  }
  const reloadTab = () => (st.tab === 'stock' ? loadStock(false) : loadList());
  function closePanel() { const m = $('xfr-modal'); if (m) m.classList.remove('open'); st.act = null; st.key = ''; }
  function setErr(msg) { const el = $('xfr-err'); if (el) el.textContent = msg || ''; }

  async function loadList() {
    const seq = ++st.listSeq;
    st.loading = true; st.err = ''; paint();
    try {
      const r = await call(`/transfers?limit=100${st.tab === 'open' ? '&status=open' : ''}`);
      if (seq !== st.listSeq) return;
      st.list = Array.isArray(r.transfers) ? r.transfers : [];
      await names(st.list.flatMap((x) => x.lines.map((l) => l.productId)));
    } catch (e) {
      if (seq !== st.listSeq) return;
      st.err = errText(e);
    } finally {
      if (seq === st.listSeq) { st.loading = false; paint(); }
    }
  }

  /** نامِ کالاها از کاتالوگِ سرور (یک بار برای هر شناسه، حداکثر ۳۰ در هر دور) */
  async function names(ids) {
    const want = [...new Set(ids)].filter((id) => !(id in st.names)).slice(0, 30);
    await Promise.all(want.map(async (id) => {
      try { st.names[id] = ((await call(`/products/${encodeURIComponent(id)}`)).product || {}).name || id; }
      catch { st.names[id] = id; }
    }));
  }
  const nameOf = (id) => st.names[id] || id;

  function paint() {
    const body = $('xfr-body');
    if (!body) return;
    const title = $('xfr-title');
    setErr('');
    if (st.view === 'draft') { title.textContent = t('draft.title'); body.innerHTML = draftHtml(); return; }
    if (st.view === 'detail' && st.open) { title.textContent = t('detail.title'); body.innerHTML = detailHtml(st.open); return; }
    title.textContent = t('title');
    body.innerHTML = listHtml();
  }

  function listHtml() {
    const tabs = TABS.map((k) => `<button type="button" role="tab" class="xfr-tab${st.tab === k ? ' on' : ''}" aria-selected="${st.tab === k}" data-xfr="tab" data-tab="${k}">${esc(t(`tab.${k}`))}</button>`).join('');
    const head = `<p class="xfr-sub">${esc(t('sub'))}</p>
      <div class="xfr-bar"><div class="xfr-tabs" role="tablist">${tabs}</div>
        ${canMove() ? `<button type="button" class="btn btn-primary btn-sm" data-xfr="new">${esc(t('new'))}</button>` : ''}</div>
      ${canMove() ? '' : `<p class="xfr-note">${esc(t('readonly'))}</p>`}`;
    if (st.tab === 'stock') return head + stockHtml();
    if (st.loading && !st.list.length) return `${head}<p class="xfr-empty">${esc(t('loading'))}</p>`;
    if (st.err) return `${head}<p class="xfr-empty">${esc(st.err)} <button type="button" class="btn btn-secondary btn-sm" data-xfr="reload">${esc(t('retry'))}</button></p>`;
    if (!st.list.length) return `${head}<p class="xfr-empty">${esc(t(st.tab === 'open' ? 'empty.open' : 'empty.all'))}</p>`;
    const rows = st.list.map((x) => `
      <li><button type="button" class="xfr-row" data-xfr="open" data-id="${esc(x.id)}">
        <span class="xfr-route">${esc(t('row.route', { from: loc(x.from), to: loc(x.to) }))}</span>
        <span class="xfr-chip xfr-${esc(x.status)}">${esc(t(`st.${x.status}`))}</span>
        <span class="xfr-meta">${esc(t('row.items', { n: x.lines.length }))}${x.pendingMilli > 0 ? ` · ${esc(t('row.pending', { q: qtyText(x.pendingMilli) }))}` : ''}${x.shortMilli > 0 ? ` · ${esc(t('row.short', { q: qtyText(x.shortMilli) }))}` : ''}</span>
        <span class="xfr-when">${esc(when(x.createdAt))}</span>
      </button></li>`).join('');
    return `${head}<ul class="xfr-list" id="xfr-list">${rows}</ul>`;
  }

  // ── موجودیِ هر مکان (INV-01) ───────────────────────────────────────

  function stockHtml() {
    const ps = stockPlaces();
    const top = `<form id="xfr-stock" class="xfr-form" role="search" novalidate><div class="xfr-search"><label class="xfr-grow"><span class="sr">${esc(t('draft.search'))}</span>
        <input type="search" name="sq" value="${esc(st.sq)}" placeholder="${esc(t('draft.search'))}" aria-label="${esc(t('draft.search'))}"></label>
        <button type="submit" class="btn btn-secondary btn-sm">${esc(t('draft.searchBtn'))}</button></div></form>
      <p class="xfr-note">${esc(t('stock.sub'))}</p>${st.sFresh && st.sFresh.fresh === false ? `<p class="xfr-note" role="status">${esc(t('stock.stale'))}</p>` : ''}`;
    if (st.loading && !st.sItems.length) return `${top}<p class="xfr-empty">${esc(t('loading'))}</p>`;
    if (st.err) return `${top}<p class="xfr-empty">${esc(st.err)} <button type="button" class="btn btn-secondary btn-sm" data-xfr="reload">${esc(t('retry'))}</button></p>`;
    if (!st.sItems.length) return `${top}<p class="xfr-empty">${esc(t(st.sq.trim() ? 'draft.noResult' : 'stock.empty'))}</p>`;
    const rows = st.sItems.map((p) => {
      const r = placesRow(p, ps);
      const cell = (q) => `<td${q < 0 ? ' class="xfr-neg"' : ''}>${esc(qtyText(q))}</td>`;
      return `<tr data-id="${esc(r.id)}"><th scope="row" data-raw>${esc(r.name)}</th>${r.cells.map(cell).join('')}<td><b>${esc(qtyText(r.totalMilli))}</b></td></tr>`;
    }).join('');
    return `${top}<div class="xfr-scroll" tabindex="0" role="region" aria-label="${esc(t('tab.stock'))}"><table class="xfr-table" id="xfr-stock-table">
      <thead><tr><th scope="col">${esc(t('detail.product'))}</th>${ps.map((l) => `<th scope="col" data-loc="${l}">${esc(loc(l))}</th>`).join('')}<th scope="col">${esc(t('stock.total'))}</th></tr></thead>
      <tbody>${rows}</tbody></table></div>${st.sNext ? `<div class="modal-actions"><button type="button" class="btn btn-secondary btn-sm" data-xfr="more">${esc(t('stock.more'))}</button></div>` : ''}`;
  }

  /** صفحهٔ کالاهای کاتالوگ با موجودیِ هر مکان؛ `more` ⇒ صفحهٔ بعد. همان نسلِ فهرست (`listSeq`): پاسخِ زبانهٔ پیشین نمی‌نشیند */
  async function loadStock(more) {
    if (more && (st.loading || !st.sNext)) return;
    const seq = ++st.listSeq;
    const q = st.sq.trim();
    const cursor = more ? st.sNext : null;
    st.loading = true; st.err = '';
    if (!more) { st.sItems = []; st.sNext = null; }
    paint();
    try {
      const r = await call(`/products?limit=50${q ? `&q=${encodeURIComponent(q)}` : ''}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`);
      if (seq !== st.listSeq) return;
      const items = (Array.isArray(r.items) ? r.items : []).filter((p) => p && !p.deleted);
      items.forEach((p) => { st.names[p.id] = p.name; });
      st.sItems = more ? st.sItems.concat(items) : items;
      st.sNext = r.nextCursor || null;
      st.sFresh = r.freshness || null;
    } catch (e) {
      if (seq !== st.listSeq) return;
      st.err = errText(e);
    } finally {
      if (seq === st.listSeq) { st.loading = false; paint(); }
    }
  }

  // ── پیش‌نویس ────────────────────────────────────────────────────────

  function loadDraft() {
    const ps = places();
    let d = null;
    try { d = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null'); } catch { d = null; }
    const ok = d && LOCS.includes(d.from) && LOCS.includes(d.to) && Array.isArray(d.lines);
    const out = ok ? d : { from: ps[0] || LOCS[0], to: LOCS.find((l) => l !== (ps[0] || LOCS[0])), lines: [], note: '', q: '' };
    if (!/^[A-Za-z0-9_.:-]{8,128}$/.test(String(out.key || ''))) out.key = newKey();
    return out;
  }
  function saveDraft() {
    try {
      if (st.draft) localStorage.setItem(DRAFT_KEY, JSON.stringify({ ...st.draft, lines: st.draft.lines.slice(0, MAX_LINES) }));
      else localStorage.removeItem(DRAFT_KEY);
    } catch { /* convenience only */ }
  }

  function draftHtml() {
    const d = st.draft;
    const opt = (sel, list) => list.map((l) => `<option value="${l}"${sel === l ? ' selected' : ''}>${esc(loc(l))}</option>`).join('');
    const found = st.found.map((p) => `<li><span data-raw>${esc(p.name)}</span><span class="xfr-meta">${esc(t('draft.have', { q: qtyText(((p.stock || {}).levels || {})[d.from] || 0) }))}</span>
        <button type="button" class="btn btn-secondary btn-sm" data-xfr="add" data-id="${esc(p.id)}">${esc(t('draft.add'))}</button></li>`).join('');
    const lines = d.lines.map((l, i) => `<li><span data-raw>${esc(l.name || l.productId)}</span>
        <label class="xfr-qty"><span class="sr">${esc(t('draft.qty'))}</span><input inputmode="decimal" name="xfr-qty" data-i="${i}" value="${esc(l.qty)}" aria-label="${esc(t('draft.qty'))}"></label>
        <button type="button" class="btn btn-secondary btn-sm" data-xfr="drop" data-i="${i}" aria-label="${esc(t('draft.remove'))}">✕</button></li>`).join('');
    return `<form id="xfr-draft" class="xfr-form" novalidate>
      <div class="xfr-two">
        <label>${esc(t('draft.from'))}<select name="from">${opt(d.from, places())}</select></label>
        <label>${esc(t('draft.to'))}<select name="to">${opt(d.to, LOCS)}</select></label>
      </div>
      <div class="xfr-search"><label class="xfr-grow"><span class="sr">${esc(t('draft.search'))}</span>
        <input type="search" name="q" value="${esc(d.q || '')}" placeholder="${esc(t('draft.search'))}" aria-label="${esc(t('draft.search'))}"></label>
        <button type="button" class="btn btn-secondary btn-sm" data-xfr="find">${esc(t('draft.searchBtn'))}</button></div>
      ${st.found.length ? `<ul class="xfr-found">${found}</ul>` : (st.searched ? `<p class="xfr-note">${esc(t('draft.noResult'))}</p>` : '')}
      <ul class="xfr-lines">${lines}</ul>
      <label>${esc(t('draft.note'))}<input name="note" maxlength="300" value="${esc(d.note || '')}"></label>
      <p class="xfr-note">${esc(t('draft.saved'))}</p>
      <div class="modal-actions">
        <button type="button" class="btn btn-secondary" data-xfr="back">${esc(t('back'))}</button>
        <button type="submit" class="btn btn-primary" id="xfr-send">${esc(t('draft.send'))}</button>
      </div></form>`;
  }

  /**
   * جست‌وجوی کالا. ⛔ نتیجهٔ کهنه هرگز به‌جای نتیجهٔ جست‌وجوی تازه نمی‌نشیند: فهرستِ پیشین همان لحظه برداشته می‌شود
   * (تا «افزودن» روی کالای جست‌وجوی قبلی زده نشود) و پاسخی که پس از جست‌وجوی تازه‌تر برسد کنار می‌رود.
   */
  async function find(keepFocus) {
    const q = (st.draft.q || '').trim();
    if (!q) return;
    const seq = ++st.findSeq;
    const refocus = () => { const el = keepFocus && document.querySelector('#xfr-draft input[name="q"]'); if (el) el.focus(); };
    st.found = []; st.searched = false;
    paint(); refocus();
    try {
      const r = await call(`/products?limit=20&q=${encodeURIComponent(q)}`);
      if (seq !== st.findSeq || !st.draft) return;
      st.found = (r.items || []).filter((p) => p.active !== false && !p.deleted);
      st.found.forEach((p) => { st.names[p.id] = p.name; });
      st.searched = true;
      paint(); refocus();
    } catch (e) { if (seq === st.findSeq) setErr(errText(e)); }
  }

  function addLine(id) {
    const p = st.found.find((x) => x.id === id);
    if (!p || st.draft.lines.some((l) => l.productId === id)) return;
    st.draft.lines.push({ productId: p.id, name: p.name, qty: '1' });
    saveDraft();
    paint();
  }

  async function sendDraft() {
    const out = draftBody(st.draft);
    if (out.error) { setErr(t(out.error, { n: out.n })); return; }
    if (st.busy) return;
    st.busy = true;
    const btn = $('xfr-send');
    if (btn) btn.disabled = true;
    try {
      await call('/transfers', { method: 'POST', body: { ...out.body, clientKey: st.draft.key } });
      st.draft = null; st.found = []; st.searched = false; saveDraft();
      toast(t('draft.sent'));
      st.view = 'list'; st.tab = 'open';
      paint(); loadList();
    } catch (e) {
      setErr(errText(e));
      //  ۴۰۹: ارسالِ پیشینِ همین پیش‌نویس (پاسخش نرسیده بود) پیش‌تر ثبت شده و پیش‌نویس از آن پس ویرایش شده است ⇒
      //  کلیدِ تازه فقط برای ارسالِ **عمداً** دوم؛ فهرست تازه می‌شود تا انتقالِ ثبت‌شده پیدا باشد
      if (e && e.code === 'idempotency_key_reused') { st.draft.key = newKey(); saveDraft(); refreshList(); }
    } finally {
      st.busy = false;
      if (btn) btn.disabled = false;
    }
  }

  /** فهرست بی نقاشیِ دوبارهٔ پیش‌نویس (پس از ۴۰۹ ِ ارسال) */
  async function refreshList() {
    try {
      const r = await call(`/transfers?limit=100${st.tab === 'open' ? '&status=open' : ''}`);
      st.list = Array.isArray(r.transfers) ? r.transfers : st.list;
    } catch { /* stays */ }
  }

  // ── جزئیات، دریافت و رد ─────────────────────────────────────────────

  function detailHtml(x) {
    const rows = x.lines.map((l) => `<tr><td data-raw>${esc(nameOf(l.productId))}</td><td>${esc(qtyText(l.sentMilli))}</td>
      <td>${l.receivedMilli === null ? '—' : esc(qtyText(l.receivedMilli))}</td><td>${l.pendingMilli ? esc(qtyText(l.pendingMilli)) : '—'}</td>
      <td>${l.shortMilli ? esc(qtyText(l.shortMilli)) : '—'}</td></tr>`).join('');
    const info = [
      x.note ? t('detail.note', { t: x.note }) : '', x.reason ? t('detail.reason', { t: x.reason }) : '',
      t('detail.when', { t: when(x.createdAt) }), x.closedAt ? t('detail.closedAt', { t: when(x.closedAt) }) : '',
    ].filter(Boolean).map((s) => `<span>${esc(s)}</span>`).join('');
    const can = canMove() && isOpen(x);
    return `<div class="xfr-head"><b>${esc(t('row.route', { from: loc(x.from), to: loc(x.to) }))}</b>
        <span class="xfr-chip xfr-${esc(x.status)}">${esc(t(`st.${x.status}`))}</span></div>
      <p class="xfr-info">${info}</p>
      <div class="xfr-scroll"><table class="xfr-table"><thead><tr><th>${esc(t('detail.product'))}</th><th>${esc(t('detail.sent'))}</th>
        <th>${esc(t('detail.got'))}</th><th>${esc(t('detail.pending'))}</th><th>${esc(t('detail.short'))}</th></tr></thead><tbody>${rows}</tbody></table></div>
      ${st.act === 'receive' && can ? receiveHtml(x) : ''}${st.act === 'reject' && can ? rejectHtml() : ''}
      <div class="modal-actions">
        <button type="button" class="btn btn-secondary" data-xfr="back">${esc(t('back'))}</button>
        ${can && !st.act ? `<button type="button" class="btn btn-secondary" data-xfr="act" data-act="reject">${esc(t('act.reject'))}</button>
          <button type="button" class="btn btn-primary" data-xfr="act" data-act="receive">${esc(t('act.receive'))}</button>` : ''}
      </div>`;
  }

  function receiveHtml(x) {
    const rows = x.lines.filter((l) => l.pendingMilli > 0).map((l) => `<li><span data-raw>${esc(nameOf(l.productId))}</span>
      <span class="xfr-meta">${esc(t('row.pending', { q: qtyText(l.pendingMilli) }))}</span>
      <label class="xfr-qty"><span class="sr">${esc(t('recv.now'))}</span><input inputmode="decimal" name="xfr-got" data-id="${esc(l.productId)}" value="${esc(qtyText(l.pendingMilli))}" aria-label="${esc(t('recv.now'))}"></label></li>`).join('');
    return `<form id="xfr-recv" class="xfr-form xfr-act" novalidate><h4>${esc(t('recv.title', { to: loc(x.to) }))}</h4>
      <ul class="xfr-lines">${rows}</ul>
      <label class="xfr-check"><input type="checkbox" name="final" checked> ${esc(t('recv.final'))}</label>
      <label>${esc(t('recv.reason'))}<input name="reason" maxlength="120"></label>
      <div class="modal-actions"><button type="button" class="btn btn-secondary" data-xfr="act-cancel">${esc(t('cancel'))}</button>
        <button type="submit" class="btn btn-primary" id="xfr-act-ok">${esc(t('recv.ok'))}</button></div></form>`;
  }

  function rejectHtml() {
    return `<form id="xfr-rej" class="xfr-form xfr-act" novalidate><h4>${esc(t('rej.title'))}</h4><p class="xfr-note">${esc(t('rej.hint'))}</p>
      <label>${esc(t('rej.reason'))}<input name="reason" maxlength="120" required></label>
      <div class="modal-actions"><button type="button" class="btn btn-secondary" data-xfr="act-cancel">${esc(t('cancel'))}</button>
        <button type="submit" class="btn btn-danger" id="xfr-act-ok">${esc(t('rej.ok'))}</button></div></form>`;
  }

  async function openDetail(id) {
    const x = st.list.find((r) => r.id === id);
    if (!x) return;
    st.open = x; st.act = null; st.key = ''; st.view = 'detail';
    await names(x.lines.map((l) => l.productId));
    paint();
  }

  async function confirmAct(form) {
    const x = st.open;
    if (!x || st.busy) return;
    let out;
    if (form.id === 'xfr-recv') {
      const got = {};
      form.querySelectorAll('input[name="xfr-got"]').forEach((i) => { got[i.dataset.id] = i.value; });
      out = receiveBody(x, got, form.elements.final.checked, form.elements.reason.value);
    } else {
      out = rejectBody(form.elements.reason.value);
    }
    if (out.error) { setErr(t(out.error, { n: out.n })); return; }
    st.busy = true;
    if (!st.key) st.key = newKey();   //  همان کلیدِ فرم تا پاسخِ قطعی (قطعِ شبکه ⇒ «دوباره» همان گام)
    const btn = $('xfr-act-ok');
    if (btn) btn.disabled = true;
    const what = form.id === 'xfr-recv' ? 'receive' : 'reject';
    try {
      const r = await call(`/transfers/${encodeURIComponent(x.id)}/${what}`, { method: 'POST', body: { ...out.body, clientKey: st.key } });
      st.key = ''; st.act = null;
      st.open = r.transfer || x;
      st.list = st.list.map((y) => (y.id === st.open.id ? st.open : y));
      toast(t(what === 'receive' ? 'recv.done' : 'rej.done'));
      paint();
    } catch (e) {
      setErr(errText(e));
      //  گامِ پیشینِ همین فرم ثبت شده بود ⇒ فرم بسته و حالِ تازهٔ سرور، نه کلیدِ تازه روی عددِ کهنه
      if (e && e.code === 'idempotency_key_reused') { st.key = ''; st.act = null; await reopen(x.id); }
    } finally {
      st.busy = false;
      if (btn) btn.disabled = false;
    }
  }

  /** همین انتقال از نو از سرور (پیش از هر فرمِ دریافت/رد، و پس از گامِ نامعلوم) */
  async function reopen(id) {
    try {
      const r = await call('/transfers?limit=100');
      const fresh = (Array.isArray(r.transfers) ? r.transfers : []).find((y) => y.id === id);
      if (fresh) {
        st.open = fresh;
        st.list = st.list.some((y) => y.id === id) ? st.list.map((y) => (y.id === id ? fresh : y)) : st.list;
      }
    } catch (e) { setErr(errText(e)); return false; }
    paint();
    return true;
  }

  // ── رویدادها (همه از یک شنونده؛ هیچ رویدادِ درون‌خطی) ────────────────

  function onClick(e) {
    const m = e.currentTarget;
    if (e.target === m) { closePanel(); return; }
    const b = e.target.closest('[data-xfr]');
    if (!b) return;
    const a = b.dataset.xfr;
    if (a === 'close') closePanel();
    else if (a === 'tab') { st.tab = TABS.includes(b.dataset.tab) ? b.dataset.tab : 'open'; reloadTab(); }
    else if (a === 'reload') reloadTab();
    else if (a === 'more') loadStock(true);
    else if (a === 'new') { st.draft = loadDraft(); saveDraft(); st.found = []; st.searched = false; st.view = 'draft'; paint(); }
    else if (a === 'back') { st.view = 'list'; st.act = null; st.key = ''; paint(); if (st.open) loadList(); }
    else if (a === 'find') find();
    else if (a === 'add') addLine(b.dataset.id);
    else if (a === 'drop') { st.draft.lines.splice(Number(b.dataset.i), 1); saveDraft(); paint(); }
    else if (a === 'open') openDetail(b.dataset.id);
    else if (a === 'act') openAct(b.dataset.act);
    else if (a === 'act-cancel') { st.act = null; st.key = ''; reopen(st.open && st.open.id); }
  }

  /** فرمِ دریافت/رد فقط روی حالِ تازهٔ سرور (ماندهٔ «در مسیر» ِ امروز) و با کلیدِ تازه */
  async function openAct(what) {
    if (!st.open || st.busy) return;
    st.act = null; st.key = '';
    if (!(await reopen(st.open.id))) return;
    if (!isOpen(st.open)) return;
    st.act = what; st.key = newKey();
    paint();
  }

  function onSubmit(e) {
    e.preventDefault();
    if (e.target.id === 'xfr-stock') { loadStock(false); return; }
    //  Enter در کادرِ جست‌وجو یعنی «جست‌وجو»، هرگز «فرستادن» ِ انتقال
    const active = document.activeElement;
    if (e.target.id === 'xfr-draft' && active && active.name === 'q' && e.target.contains(active)) { find(true); return; }
    if (e.target.id === 'xfr-draft') sendDraft();
    else confirmAct(e.target);
  }

  function onInput(e) {
    const el = e.target;
    if (el.name === 'sq' && el.closest('#xfr-stock')) { st.sq = el.value; return; }
    if (!st.draft || !el.closest('#xfr-draft')) return;
    //  ⛔ کلید عوض نمی‌شود (درونِ پیش‌نویس است): ارسالِ نامعلومِ پیشین با بدنهٔ دیگر ⇒ ۴۰۹، هرگز انتقالِ دوم
    if (el.name === 'xfr-qty') st.draft.lines[Number(el.dataset.i)].qty = el.value;
    else if (el.name === 'from' || el.name === 'to' || el.name === 'note' || el.name === 'q') st.draft[el.name] = el.value;
    if (el.name === 'from' && e.type === 'change') { st.found = []; st.searched = false; paint(); }
    saveDraft();
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && $('xfr-modal') && $('xfr-modal').classList.contains('open')) closePanel();
  });
  document.addEventListener('tohid:page-change', (e) => {
    if (e.detail && e.detail.pageId === 'warehouse') refresh();
  });
  window.addEventListener('online', () => { if (!st.cfg) refresh(); });
  root.addEventListener('tohid:lang', relang);

  root.TohidStockTransfers = { refresh, open: openPanel, get enabled() { return !!st.cfg; }, core };
  refresh();
})(typeof window !== 'undefined' ? window : globalThis);
