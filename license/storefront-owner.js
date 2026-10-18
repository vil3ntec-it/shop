/*
 * ویترینِ اینترنتی — صفحهٔ صاحب دکان در نسخهٔ وب (فاز ۴؛ WEB-01/03، PRD-27، §۳۱ PRES).
 *
 * همان کارِ صفحهٔ «ویترین» ِ برنامهٔ نصبی (`ui/screens/Storefront.kt`) روی همان
 * API (`/api/v1/shop/storefront`) و با همان قاعده‌ها (`core/storefront/StorefrontOwner.kt`):
 *   • وضعیت ── همان جمله‌ای که مشتری می‌بیند (§۳۱.۱) + اعلامِ «آنلاین تا…»/آفلاین.
 *              «ثبت شد» فقط پس از پاسخِ سرور؛ بی اینترنت اعلام در صفِ محلی
 *              می‌ماند و هشدار می‌دهد (PRES-02/09)؛ همان eventId و زمانِ دستگاه
 *              دوباره می‌رود تا تکرار بی‌اثر و اعلامِ کهنه بی‌زور بماند (PRES-08).
 *   • تنظیمات ── نشانی، نام، تماس، رنگ، ساعت‌ها، تعطیلی، هزینه و حداقل.
 *   • کالاها ── انتشار، قیمتِ آنلاین، سقفِ هر سفارش، انتخاب و انتشارِ گروهی؛
 *               نمای کارتی/جدولی با ترجیحِ محلی (VISUAL-DIRECTION §۱.۵).
 *
 * ⛔ بخش فقط وقتی دیده می‌شود که سرور بگوید روشن است و کاربر صاحب/مدیر است.
 * ⛔ همهٔ متن‌ها با escape؛ هیچ دادهٔ سرور خام در innerHTML نمی‌نشیند.
 */
(function () {
  'use strict';

  const API = '/api/v1/shop/storefront';
  const PENDING_KEY = 'tohid-storefront-pending';
  const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/;
  const RESERVED = new Set([
    'api', 'www', 'admin', 'panel', 'portal', 'sdk', 'shop', 'store', 'stores', 'beta', 'app', 'apps',
    'pump', 'mail', 'smtp', 'imap', 'pop', 'ftp', 'ns1', 'ns2', 'cdn', 'static', 'assets', 'help',
    'support', 'status', 'docs', 'blog', 'dev', 'test', 'staging', 'login', 'auth', 'account',
    'billing', 'pay', 'payment', 'download', 'downloads', 'home', 'vill3n', 'tohid', 'official',
  ]);
  const DAY_NAMES = ['یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'];
  const DISPLAY_DAYS = [6, 0, 1, 2, 3, 4, 5];
  const NOT_CONFIRMED = 'این تغییر هنوز به سرور نرسیده؛ وضعیتِ آنلاینِ شما برای مشتری تأیید نشده است.';
  const MAX_PRICE_MINOR = 100000000000;
  const MAX_FEE_MINOR = 100000000;

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const toFa = (s) => String(s).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[+d]);
  const toast = (m, kind) => { if (typeof window.showToast === 'function') window.showToast(m, kind); };
  const $ = (id) => document.getElementById(id);

  /* ------------------------------ قاعده‌ها (همان StorefrontOwner.kt) ------------------------------ */

  function ascii(raw) {
    return String(raw || '').trim()
      .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
      .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
      .replace(/٫/g, '.').replace(/[٬,،\s‌]/g, '');
  }
  function slugError(raw) {
    const s = String(raw || '').trim().toLowerCase();
    if (!s) return 'نشانی لازم است';
    if (!SLUG_RE.test(s) || s.includes('--')) return '۳ تا ۳۲ حرفِ کوچکِ انگلیسی، رقم یا خطِ تیره';
    if (RESERVED.has(s)) return 'این نشانی رزرو است';
    return null;
  }
  /** «۱۲۰٫۵» ⇒ ۱۲۰۵۰؛ خالی ⇒ null؛ نادرست ⇒ Error */
  function parseMinor(raw, max = MAX_PRICE_MINOR) {
    const s = ascii(raw);
    if (!s) return null;
    if (!/^\d{1,12}(\.\d{1,2})?$/.test(s)) throw new Error('مبلغ درست نیست');
    const [w, f = ''] = s.split('.');
    const minor = Number(w) * 100 + Number((f + '00').slice(0, 2));
    if (minor > max) throw new Error('مبلغ بیش از حد بزرگ است');
    return minor;
  }
  const formatMinor = (m) => (m % 100 === 0 ? String(m / 100) : (m / 100).toFixed(2).replace(/0$/, ''));
  function parseQtyMilli(raw) {
    const s = ascii(raw);
    if (!s) return null;
    if (!/^\d{1,6}(\.\d{1,3})?$/.test(s)) throw new Error('مقدار درست نیست');
    const [w, f = ''] = s.split('.');
    const milli = Number(w) * 1000 + Number((f + '000').slice(0, 3));
    if (milli < 1 || milli > 10000000) throw new Error('مقدار باید بیش از صفر باشد');
    return milli;
  }
  const formatQty = (m) => (m % 1000 === 0 ? String(m / 1000) : String(m / 1000));
  function parseHm(raw) {
    const m = /^(\d{1,2}):(\d{2})$/.exec(ascii(raw).replace('.', ':'));
    if (!m) return null;
    const h = +m[1], mi = +m[2];
    if (mi > 59 || h > 24 || (h === 24 && mi !== 0)) return null;
    return h * 60 + mi;
  }
  const hm = (min) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;
  function parseDay(raw) {
    const s = String(raw || '').trim();
    if (!s) return { ranges: [] };
    const parts = s.split(/[,،;؛]/).map((x) => x.trim()).filter(Boolean);
    if (parts.length > 4) return { error: 'حداکثر ۴ بازه در روز' };
    const ranges = [];
    for (const p of parts) {
      const ends = p.split(/[-–—]/).map((x) => x.trim());
      if (ends.length !== 2) return { error: 'هر بازه مثلِ 08:00-12:00 نوشته شود' };
      const a = parseHm(ends[0]); const b = parseHm(ends[1]);
      if (a === null) return { error: `ساعتِ «${ends[0]}» درست نیست` };
      if (b === null) return { error: `ساعتِ «${ends[1]}» درست نیست` };
      if (a >= 1440 || b <= a) return { error: 'پایانِ بازه باید پس از آغاز باشد (شبِ پس از ۲۴ را در روزِ بعد بنویسید)' };
      ranges.push([a, b]);
    }
    ranges.sort((x, y) => x[0] - y[0]);
    for (let i = 1; i < ranges.length; i++) if (ranges[i][0] < ranges[i - 1][1]) return { error: 'بازه‌ها روی هم افتاده‌اند' };
    return { ranges };
  }
  const formatDay = (ranges) => (ranges || []).map((r) => `${hm(r[0])}-${hm(r[1])}`).join('، ');
  function validDate(s) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
    if (!m) return false;
    const d = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]));
    return d.getUTCFullYear() === +m[1] && d.getUTCMonth() === +m[2] - 1 && d.getUTCDate() === +m[3];
  }
  function parseClosed(raw) {
    const items = String(raw || '').split(/[,،\s]+/).map(ascii).filter(Boolean);
    if (items.length > 60) return { error: 'حداکثر ۶۰ روزِ تعطیل' };
    for (const d of items) if (!validDate(d)) return { error: `تاریخِ «${d}» باید مثلِ 2026-10-20 باشد` };
    return { dates: [...new Set(items)].sort() };
  }

  /** «HH:MM» به ساعتِ دکان */
  function clock(at, tz) {
    try {
      const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
        .formatToParts(new Date(at)).map((x) => [x.type, x.value]));
      return `${String(+p.hour % 24).padStart(2, '0')}:${p.minute}`;
    } catch { return ''; }
  }
  /** نخستین «HH:MM» ِ دکان که هنوز نگذشته (امروز یا فردا) */
  function declaredUntilFor(minutes, now, tz) {
    const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: tz, hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })
      .formatToParts(new Date(now)).map((x) => [x.type, x.value]));
    const since = ((+p.hour % 24) * 3600 + (+p.minute) * 60 + (+p.second)) * 1000 + (now % 1000);
    let t = now - since + minutes * 60000;
    if (t <= now + 60000) t += 24 * 3600000;
    return t;
  }
  const BADGE = {
    open_online: ['باز است · آنلاین', 'ok'],
    open_unconfirmed: ['باز است · اتصال تأیید نشده', 'warn'],
    declared_until: ['', 'ok'],
    closed: ['بسته است', 'off'],
    offline: ['موقتاً آفلاین', 'off'],
  };
  function badgeLabel(st) {
    if (st.badge === 'declared_until') return st.declaredUntil ? `تا ساعتِ ${toFa(clock(st.declaredUntil, st.timezone))} آنلاین اعلام شده` : 'آنلاین اعلام شده';
    return (BADGE[st.badge] || ['وضعیت نامعلوم'])[0];
  }
  const ACCEPT = {
    accepting: 'سفارش همین حالا به دکان می‌رسد.',
    delayed: 'سفارش ثبت می‌شود، ولی مشتری می‌بیند که دیرتر بررسی می‌شود.',
    later: 'سفارش ثبت می‌شود و مشتری می‌بیند که پس از باز شدنِ دکان بررسی می‌شود.',
  };
  const MODE = { auto: 'طبقِ ساعتِ کاری', online: 'آنلاین، بی زمانِ پایان', online_until: 'آنلاین تا ساعتِ مشخص', offline: 'آفلاین', closed: 'تعطیل/بسته' };

  /* ------------------------------ صفِ اعلام ------------------------------ */

  function loadPending() {
    try {
      const p = JSON.parse(localStorage.getItem(PENDING_KEY) || 'null');
      if (!p || !/^[A-Za-z0-9_.:-]{8,80}$/.test(p.eventId) || !MODE[p.mode] || (p.mode === 'online_until' && !p.declaredUntil)) return null;
      return p;
    } catch { return null; }
  }
  function savePending(p) { try { if (p) localStorage.setItem(PENDING_KEY, JSON.stringify(p)); else localStorage.removeItem(PENDING_KEY); } catch { /* بی حافظه */ } }

  /* ------------------------------ وضعیتِ صفحه ------------------------------ */

  const state = { config: null, tab: 'status', products: null, productsError: '', selected: new Set(), query: '', filter: 'all', notice: '', sending: false, bulk: null, errors: {} };

  async function call(path, opts) {
    if (!window.TohidShop || !window.TohidShop.apiAuth) throw Object.assign(new Error('اتصال به سرور آماده نیست'), { code: 'network' });
    return window.TohidShop.apiAuth(API + path, opts);
  }
  const signedIn = () => !!(window.TohidShop && window.TohidShop.account && window.TohidShop.account.loggedIn);
  const visible = () => !!(state.config && state.config.flag && state.config.permissions && state.config.permissions.manage);

  /** دیده شدنِ کارتِ «ویترین» در «بیشتر» — فقط با گفتهٔ سرور */
  async function refreshGate() {
    if (!signedIn()) { state.config = null; applyGate(); return null; }
    try { state.config = await call('/config'); } catch { /* بی اینترنت: همان قبلی؛ اگر نبود پنهان */ }
    applyGate();
    return state.config;
  }
  function applyGate() {
    const card = $('more-storefront');
    if (card) card.hidden = !visible();
  }

  /* ------------------------------ نما ------------------------------ */

  const root = () => $('sf-root');

  async function open() {
    const r = root();
    if (!r) return;
    r.innerHTML = '<div class="panel"><p class="settings-hint">در حالِ باز کردن…</p></div>';
    if (!signedIn()) { r.innerHTML = '<div class="panel"><p class="settings-hint">برای تنظیمِ ویترین وارد حساب شوید.</p></div>'; return; }
    try {
      state.config = await call('/config');
    } catch (e) {
      if (!state.config) {
        r.innerHTML = `<div class="panel"><h2 class="sf-h">ویترین باز نشد</h2><p class="settings-hint">${esc(e.message)}</p>
          <button type="button" class="btn btn-secondary" data-sf="retry">تلاشِ دوباره</button></div>`;
        return;
      }
    }
    applyGate();
    render();
    if (state.tab === 'status' && loadPending()) flush();
  }

  function render() {
    const r = root();
    if (!r) return;
    const c = state.config;
    if (!c || !c.flag) { r.innerHTML = '<div class="panel"><p class="settings-hint">ویترین برای این دکان روشن نیست.</p></div>'; return; }
    if (!c.permissions || !c.permissions.manage) { r.innerHTML = '<div class="panel"><p class="settings-hint">تنظیمِ ویترین کارِ صاحب دکان یا مدیر است.</p></div>'; return; }
    const tabs = [['status', 'وضعیت'], ['settings', 'تنظیمات'], ['products', 'کالاها']]
      .map(([id, label]) => `<button type="button" class="sf-tab" role="tab" aria-selected="${state.tab === id}" data-sf-tab="${id}">${label}</button>`).join('');
    const warn = c.entitled ? '' : '<p class="sf-note warn">اشتراکِ این دکان ویترین را در بر ندارد؛ تغییرها ذخیره نمی‌شوند.</p>';
    let body = '';
    if (state.tab === 'status') body = statusHtml();
    else if (state.tab === 'settings') body = settingsHtml();
    else body = c.settings ? productsHtml() : emptyHtml('اول ویترین را بسازید', 'در «تنظیمات» نشانی و نام را بنویسید و ذخیره کنید.');
    r.innerHTML = `${warn}<div class="sf-tabs" role="tablist" aria-label="بخش‌های ویترین">${tabs}</div><div id="sf-body">${body}</div>`;
    if (state.tab === 'products' && c.settings && state.products === null && !state.productsError) loadProducts();
  }

  const emptyHtml = (title, text) =>
    `<div class="panel sf-empty"><h2 class="sf-h">${esc(title)}</h2><p class="settings-hint">${esc(text)}</p>
      <button type="button" class="btn btn-primary" data-sf-tab="settings">رفتن به تنظیمات</button></div>`;

  function statusHtml() {
    const s = state.config.settings;
    if (!s) return emptyHtml('هنوز ویترینی ساخته نشده', 'نشانی و نامِ ویترین را در «تنظیمات» بنویسید.');
    const st = s.status;
    const pending = loadPending();
    let card = '<p class="settings-hint">وضعیت هنوز از سرور نیامده.</p>';
    if (st) {
      const tone = (BADGE[st.badge] || ['', 'off'])[1];
      const today = st.holiday ? 'امروز تعطیلِ استثنایی'
        : (st.today && st.today.length ? 'امروز ' + toFa(formatDay(st.today)) : (st.hoursSet ? 'امروز تعطیل' : 'ساعتِ کاری تنظیم نشده'));
      card = `<span class="sf-badge ${tone}" id="sf-badge">${esc(badgeLabel(st))}</span>
        <p class="sf-line">${esc(st.lastConfirmedAt ? 'آخرین تأییدِ اتصال: ' + toFa(clock(st.lastConfirmedAt, st.timezone)) : 'اتصالِ دستگاه‌های دکان هنوز تأیید نشده')}</p>
        <p class="sf-line">ساعتِ دکان: ${esc(toFa(st.localTime || ''))} · ${esc(today)}</p>
        <p class="sf-line strong">${esc(ACCEPT[st.orderAcceptance] || '')}</p>
        <p class="sf-line">اعلامِ فعلیِ شما: ${esc(MODE[st.mode] || st.mode)}</p>`;
    }
    return `<div class="panel"><h2 class="sf-h">مشتری الان این را می‌بیند</h2>${card}
        <p class="sf-line ${s.enabled ? '' : 'warn'}">نشانی: <span dir="ltr">${esc(s.slug)}.vill3n.top</span>${s.enabled ? '' : ' — ویترین خاموش است'}</p></div>
      ${pending ? `<div class="panel"><p class="sf-note warn" id="sf-pending">${NOT_CONFIRMED}</p>
        <button type="button" class="btn btn-secondary" data-sf="flush" ${state.sending ? 'disabled' : ''}>${state.sending ? 'در حالِ فرستادن…' : 'دوباره بفرست'}</button></div>` : ''}
      ${state.notice ? `<p class="sf-note" id="sf-notice">${esc(state.notice)}</p>` : ''}
      <div class="panel"><h2 class="sf-h">اعلامِ شما به مشتری</h2>
        <div class="sf-row">
          <div class="form-group sf-grow"><label for="sf-until">آنلاین تا ساعتِ (مثلاً 20:30)</label>
            <input class="form-input" id="sf-until" dir="ltr" inputmode="numeric" placeholder="20:30"></div>
          <button type="button" class="btn btn-primary" data-sf-mode="online_until" ${pending || state.sending ? 'disabled' : ''}>ثبت</button>
        </div>
        <p class="sf-err" id="sf-until-err" hidden></p>
        <div class="sf-actions">
          ${['online', 'offline', 'closed', 'auto'].map((m) => `<button type="button" class="btn btn-secondary btn-sm" data-sf-mode="${m}" ${pending || state.sending ? 'disabled' : ''}>${esc({ online: 'آنلاین بی زمانِ پایان', offline: 'آفلاین', closed: 'تعطیل/بسته', auto: 'طبقِ ساعتِ کاری' }[m])}</button>`).join('')}
        </div>
        <p class="settings-hint">«ثبت شد» فقط وقتی دیده می‌شود که سرور گرفته باشد. بی اینترنت، تغییر این‌جا می‌ماند و برای مشتری عوض نمی‌شود.</p>
      </div>`;
  }

  function field(id, label, value, extra = '') {
    const err = state.errors[id];
    return `<div class="form-group"><label for="sf-${id}">${esc(label)}</label>
      <input class="form-input" id="sf-${id}" value="${esc(value)}" ${extra} ${err ? 'aria-invalid="true"' : ''}>
      ${err ? `<p class="sf-err">${esc(err)}</p>` : ''}</div>`;
  }
  const check = (id, label, on) => `<label class="sf-check"><input type="checkbox" id="sf-${id}" ${on ? 'checked' : ''}> ${esc(label)}</label>`;

  function settingsHtml() {
    const s = state.config.settings || {};
    const has = !!state.config.settings;
    const f = s.fulfillment || { delivery: true, pickup: true };
    const wh = s.weeklyHours || {};
    const days = DISPLAY_DAYS.map((d) => field(`day${d}`, DAY_NAMES[d], formatDay(wh[String(d)] || []), 'dir="ltr" placeholder="08:00-20:00"')).join('');
    return `<form id="sf-settings" novalidate>
      <div class="panel"><h2 class="sf-h">نشانی و نام</h2>
        ${field('slug', 'نشانی (زیرِ vill3n.top)', s.slug || '', 'dir="ltr" placeholder="my-shop" autocomplete="off"')}
        ${field('title', 'نامِ ویترین (نامِ کسب‌وکار، نه نامِ شخص)', s.title || '')}
        ${field('tagline', 'یک جملهٔ کوتاه (اختیاری)', s.tagline || '')}
        ${field('phone', 'شمارهٔ تماسِ دکان', String(s.contactPhone || '').replace(/^\+93/, '0'), 'dir="ltr" inputmode="tel"')}
        ${field('accent', 'رنگِ ویترین (اختیاری)', s.accent || '', 'dir="ltr" placeholder="#2c5ce6"')}
        ${field('notice', 'پیامِ کوتاه کنارِ وضعیت (اختیاری)', s.notice || '')}
        ${check('enabled', 'ویترین روشن باشد', !!s.enabled)}
      </div>
      <div class="panel"><h2 class="sf-h">تحویل و هزینه</h2>
        ${check('delivery', 'ارسال به نشانیِ مشتری', f.delivery !== false)}
        ${check('pickup', 'دریافت از دکان', f.pickup !== false)}
        ${state.errors.fulfillment ? `<p class="sf-err">${esc(state.errors.fulfillment)}</p>` : ''}
        ${field('fee', 'هزینهٔ ارسال (افغانی)', has ? formatMinor(s.deliveryFeeMinor || 0) : '', 'dir="ltr" inputmode="decimal"')}
        ${field('min', 'حداقلِ سفارش (افغانی)', has ? formatMinor(s.minOrderMinor || 0) : '', 'dir="ltr" inputmode="decimal"')}
      </div>
      <div class="panel"><h2 class="sf-h">ساعتِ کاری</h2>
        <p class="settings-hint">هر روز مثلِ 08:00-12:00، 14:00-20:00؛ خالی = تعطیل. شبِ پس از ۲۴ را در روزِ بعد بنویسید. همه خالی = ساعت تنظیم نشده.</p>
        ${field('tz', 'منطقهٔ زمانیِ دکان', s.timezone || 'Asia/Kabul', 'dir="ltr"')}
        ${days}
        ${field('closed', 'تعطیلیِ استثنایی (تاریخ‌ها مثلِ 2026-10-20)', (s.closedDates || []).join('، '), 'dir="ltr"')}
      </div>
      <button type="submit" class="btn btn-primary btn-block" id="sf-save">ذخیرهٔ ویترین</button>
    </form>`;
  }

  /* ------------------------------ کالاها ------------------------------ */

  function viewMode() {
    if (window.innerWidth < 600) return 'card';   // گوشی همیشه کارتی
    const user = (window.TohidShop && window.TohidShop.account && window.TohidShop.account.userId) || 'local';
    try { return localStorage.getItem(`view_mode:${user}:storefront-products`) === 'card' ? 'card' : 'table'; } catch { return 'table'; }
  }
  function setViewMode(m) {
    const user = (window.TohidShop && window.TohidShop.account && window.TohidShop.account.userId) || 'local';
    try { localStorage.setItem(`view_mode:${user}:storefront-products`, m); } catch { /* بی حافظه */ }
  }

  async function loadProducts() {
    const all = [];
    let cursor = '';
    try {
      for (let i = 0; i < 100; i++) {
        const r = await call('/products?limit=100' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''));
        all.push(...(r.items || []));
        if (!r.nextCursor) break;
        cursor = r.nextCursor;
      }
      state.products = all; state.productsError = '';
    } catch (e) {
      state.productsError = e.message || 'فهرست نیامد';
    }
    if (state.tab === 'products') render();
  }

  function priceText(p) {
    if (p.onlinePriceMinor != null) return `${toFa(formatMinor(p.onlinePriceMinor))} ؋ (آنلاین)`;
    if (p.priceMinor != null) return `${toFa(formatMinor(p.priceMinor))} ؋`;
    return 'بی‌قیمت — در ویترین دیده نمی‌شود';
  }
  function pill(p) {
    const [t, cls] = !p.active ? ['غیرفعال در دفتر', 'muted'] : (p.published ? ['در ویترین', 'ok'] : ['بیرون از ویترین', 'muted']);
    return `<span class="sf-pill ${cls}">${t}</span>`;
  }

  function productsHtml() {
    if (state.productsError && !state.products) {
      return `<div class="panel"><p class="settings-hint">${esc(state.productsError)}</p><button type="button" class="btn btn-secondary" data-sf="reload-products">تلاشِ دوباره</button></div>`;
    }
    if (!state.products) return '<div class="panel"><p class="settings-hint">در حالِ آوردنِ کالاها…</p></div>';
    if (!state.products.length) return '<div class="panel"><p class="settings-hint">هنوز کالایی همگام نشده؛ کالاها پس از همگام‌سازی با سرور این‌جا دیده می‌شوند.</p></div>';
    const q = state.query.trim().toLowerCase();
    const shown = state.products.filter((p) => (!q || String(p.name).toLowerCase().includes(q))
      && (state.filter === 'on' ? p.published : state.filter === 'off' ? !p.published : true));
    const mode = viewMode();
    const sel = state.selected;
    const toggle = window.innerWidth < 600 ? '' : `<div class="sf-view" role="group" aria-label="نما">
        <button type="button" class="btn btn-secondary btn-sm" data-sf-view="card" aria-pressed="${mode === 'card'}">نمای کارتی</button>
        <button type="button" class="btn btn-secondary btn-sm" data-sf-view="table" aria-pressed="${mode === 'table'}">نمای جدولی</button></div>`;
    const filters = [['all', 'همه'], ['on', 'در ویترین'], ['off', 'بیرون از ویترین']]
      .map(([id, l]) => `<button type="button" class="sf-chip" data-sf-filter="${id}" aria-pressed="${state.filter === id}">${l}</button>`).join('');
    const bulk = sel.size ? `<div class="sf-bulk" id="sf-bulk"><b>${toFa(sel.size)} انتخاب‌شده</b>
        <button type="button" class="btn btn-primary btn-sm" data-sf="bulk-on">انتشار</button>
        <button type="button" class="btn btn-secondary btn-sm" data-sf="bulk-off">برداشتن</button>
        <button type="button" class="btn btn-secondary btn-sm" data-sf="bulk-clear" aria-label="لغوِ انتخاب">×</button></div>` : '';
    const report = state.bulk ? `<p class="sf-note ${state.bulk.failed.length ? 'warn' : 'ok'}" id="sf-bulk-report">${esc(state.bulk.summary)}${state.bulk.failed.map(([id, e]) => `<br>• ${esc((state.products.find((p) => p.id === id) || {}).name || id)}: ${esc(e)}`).join('')}</p>` : '';
    let list;
    if (!shown.length) list = '<p class="settings-hint">کالایی با این جست‌وجو نیست.</p>';
    else if (mode === 'table') {
      const all = shown.every((p) => sel.has(p.id));
      list = `<div class="sf-table-wrap"><table class="sf-table"><thead><tr>
          <th><input type="checkbox" data-sf="select-all" aria-label="انتخابِ همه" ${all ? 'checked' : ''}></th>
          <th>کالا</th><th>قیمت</th><th>سقفِ هر سفارش</th><th>وضعیت</th><th>در ویترین</th><th></th></tr></thead><tbody>
          ${shown.map((p) => `<tr data-id="${esc(p.id)}">
            <td><input type="checkbox" data-sf="select" aria-label="انتخابِ ${esc(p.name)}" ${sel.has(p.id) ? 'checked' : ''}></td>
            <td><b>${esc(p.name)}</b><br><small>${esc(p.category || '—')}</small></td>
            <td>${esc(priceText(p))}</td>
            <td>${p.maxQtyMilli ? toFa(formatQty(p.maxQtyMilli)) : '—'}</td>
            <td>${pill(p)}</td>
            <td><input type="checkbox" class="sf-switch" data-sf="publish" aria-label="در ویترین: ${esc(p.name)}" ${p.published ? 'checked' : ''}></td>
            <td><button type="button" class="btn btn-secondary btn-sm" data-sf="edit">قیمت و سقف</button></td></tr>`).join('')}
          </tbody></table></div>`;
    } else {
      list = `<div class="sf-cards">${shown.map((p) => `<div class="sf-card" data-id="${esc(p.id)}">
          <div class="sf-card-top"><input type="checkbox" data-sf="select" aria-label="انتخابِ ${esc(p.name)}" ${sel.has(p.id) ? 'checked' : ''}>
            <div class="sf-grow"><b>${esc(p.name)}</b><br><small>${esc([p.category, p.unit].filter(Boolean).join(' · '))}</small></div>
            <label class="sf-check"><input type="checkbox" class="sf-switch" data-sf="publish" aria-label="در ویترین: ${esc(p.name)}" ${p.published ? 'checked' : ''}></label></div>
          <div class="sf-card-bot">${pill(p)} <span class="sf-grow">${esc(priceText(p))}</span>
            <button type="button" class="btn btn-secondary btn-sm" data-sf="edit">قیمت و سقف</button></div></div>`).join('')}</div>`;
    }
    return `<div class="panel"><div class="sf-row"><div class="form-group sf-grow"><label for="sf-q">جست‌وجوی نامِ کالا</label>
        <input class="form-input" id="sf-q" type="search" value="${esc(state.query)}"></div>${toggle}</div>
      <div class="sf-chips" role="group" aria-label="پالایه">${filters}</div>${bulk}${report}${list}</div>`;
  }

  async function saveProduct(p, published, online, max) {
    try {
      await call('/products/' + encodeURIComponent(p.id), { method: 'PUT', body: { published, onlinePriceMinor: online, maxQtyMilli: max } });
      Object.assign(p, { published, onlinePriceMinor: online, maxQtyMilli: max });
      return null;
    } catch (e) { return e.message || 'ذخیره نشد'; }
  }

  async function bulk(publish) {
    const list = state.products.filter((p) => state.selected.has(p.id));
    const results = [];
    for (const p of list) results.push([p.id, await saveProduct(p, publish, p.onlinePriceMinor ?? null, p.maxQtyMilli ?? null)]);
    const failed = results.filter((r) => r[1]);
    const ok = results.length - failed.length;
    state.bulk = { failed, summary: failed.length ? `${toFa(ok)} انجام شد، ${toFa(failed.length)} نشد` : `${toFa(ok)} کالا انجام شد` };
    state.selected.clear();
    toast(state.bulk.summary, failed.length ? 'error' : undefined);
    render();
  }

  function editDialog(p) {
    const online = window.prompt(`قیمتِ آنلاینِ «${p.name}» به افغانی (خالی = قیمتِ دفتر)`, p.onlinePriceMinor != null ? formatMinor(p.onlinePriceMinor) : '');
    if (online === null) return;
    const max = window.prompt(`سقفِ هر سفارشِ «${p.name}» (${p.unit || 'واحد'}، خالی = بی سقف)`, p.maxQtyMilli ? formatQty(p.maxQtyMilli) : '');
    if (max === null) return;
    let o, m;
    try { o = parseMinor(online); m = parseQtyMilli(max); } catch (e) { toast(e.message, 'error'); return; }
    saveProduct(p, p.published, o, m).then((err) => { if (err) toast(err, 'error'); else toast('ذخیره شد'); render(); });
  }

  /* ------------------------------ اعلام ------------------------------ */

  function declare(mode, until) {
    const now = Date.now();
    const p = { eventId: `pres-${now.toString(36)}-${Math.random().toString(16).slice(2, 12)}`, mode, declaredUntil: mode === 'online_until' ? until : null, clientEventAt: now };
    savePending(p);
    state.notice = '';
    render();
    flush();
  }

  async function flush() {
    const p = loadPending();
    if (!p || state.sending) return;
    state.sending = true;
    render();
    try {
      const body = { eventId: p.eventId, mode: p.mode, clientEventAt: p.clientEventAt };
      if (p.declaredUntil) body.declaredUntil = p.declaredUntil;
      const r = await call('/presence', { method: 'POST', body });
      savePending(null);
      if (state.config && state.config.settings) Object.assign(state.config.settings, { status: r.status, presenceMode: r.status.mode, declaredUntil: r.status.declaredUntil });
      state.notice = r.applied === false
        ? 'ثبت شد، ولی وضعیتِ تازه‌تری از دستگاهِ دیگر جلوتر است؛ همان می‌ماند.'
        : `ثبت شد — زمانِ سرور: ${toFa(clock(r.serverReceivedAt, (r.status && r.status.timezone) || 'Asia/Kabul'))}`;
      toast('وضعیتِ ویترین روی سرور ثبت شد');
    } catch (e) {
      //  سرور رد کرد (۴xx) ⇒ از صف بیرون، بی «ثبت شد»؛ شبکه ⇒ در صف می‌ماند
      if (e.status && e.status >= 400 && e.status < 500 && e.status !== 408 && e.status !== 429) { savePending(null); state.notice = e.message; }
      else state.notice = NOT_CONFIRMED;
    } finally {
      state.sending = false;
      if (state.tab === 'status') render();
    }
  }

  /* ------------------------------ ذخیرهٔ تنظیمات ------------------------------ */

  async function saveSettings(form) {
    const v = (id) => (form.querySelector('#sf-' + id) || {}).value || '';
    const on = (id) => !!(form.querySelector('#sf-' + id) || {}).checked;
    const errors = {};
    const slug = v('slug').trim().toLowerCase();
    const se = slugError(slug); if (se) errors.slug = se;
    if (on('enabled') && !v('title').trim()) errors.title = 'برای روشن کردن، نامِ ویترین لازم است';
    if (v('accent').trim() && !/^#[0-9a-fA-F]{6}$/.test(v('accent').trim())) errors.accent = 'مثلِ #2c5ce6';
    if (!on('delivery') && !on('pickup')) errors.fulfillment = 'دست‌کم یکی از ارسال یا دریافت از دکان';
    let fee = 0, min = 0;
    try { fee = parseMinor(v('fee'), MAX_FEE_MINOR) || 0; } catch (e) { errors.fee = e.message; }
    try { min = parseMinor(v('min')) || 0; } catch (e) { errors.min = e.message; }
    const weeklyHours = {};
    for (let d = 0; d < 7; d++) {
      const r = parseDay(v('day' + d));
      if (r.error) errors['day' + d] = r.error; else if (r.ranges.length) weeklyHours[String(d)] = r.ranges;
    }
    const closed = parseClosed(v('closed'));
    if (closed.error) errors.closed = closed.error;
    //  خطا کنارِ همان کادر؛ چیزی که نوشته شده دست نمی‌خورد (بی رندرِ دوباره)
    form.querySelectorAll('.sf-err').forEach((x) => x.remove());
    form.querySelectorAll('[aria-invalid]').forEach((x) => x.removeAttribute('aria-invalid'));
    if (Object.keys(errors).length) {
      for (const [id, msg] of Object.entries(errors)) {
        const input = form.querySelector('#sf-' + id) || form.querySelector('#sf-pickup');
        if (!input) continue;
        if (id !== 'fulfillment') input.setAttribute('aria-invalid', 'true');
        const p = document.createElement('p');
        p.className = 'sf-err'; p.textContent = msg;
        (input.closest('.form-group') || input.closest('label') || input).insertAdjacentElement('afterend', p);
      }
      const first = form.querySelector('[aria-invalid="true"]');
      if (first) first.focus();
      return;
    }
    const btn = $('sf-save');
    if (btn) { btn.disabled = true; btn.textContent = 'در حالِ ذخیره…'; }
    try {
      const r = await call('', {
        method: 'PUT',
        body: {
          slug, enabled: on('enabled'), title: v('title').trim(), tagline: v('tagline').trim(), contactPhone: v('phone').trim(),
          accent: v('accent').trim().toLowerCase(), notice: v('notice').trim(), timezone: v('tz').trim() || 'Asia/Kabul',
          weeklyHours, closedDates: closed.dates, acceptDelivery: on('delivery'), acceptPickup: on('pickup'),
          deliveryFeeMinor: fee, minOrderMinor: min,
        },
      });
      state.config.settings = r.settings;
      toast('ویترین ذخیره شد');
      render();
    } catch (e) {
      toast(e.message || 'ذخیره نشد', 'error');
      if (btn) { btn.disabled = false; btn.textContent = 'ذخیرهٔ ویترین'; }
      const box = $('sf-body');
      if (box) box.insertAdjacentHTML('afterbegin', `<p class="sf-note warn" role="alert">${esc(e.message || 'ذخیره نشد')}</p>`);
    }
  }

  /* ------------------------------ رویدادها ------------------------------ */

  function wire() {
    const r = root();
    if (!r || r.dataset.wired) return;
    r.dataset.wired = '1';
    r.addEventListener('click', (e) => {
      const t = e.target.closest('[data-sf-tab],[data-sf],[data-sf-mode],[data-sf-view],[data-sf-filter]');
      if (!t || !r.contains(t)) return;
      if (t.dataset.sfTab) { state.tab = t.dataset.sfTab; state.notice = ''; render(); if (state.tab === 'status' && loadPending()) flush(); return; }
      if (t.dataset.sfView) { setViewMode(t.dataset.sfView); render(); return; }
      if (t.dataset.sfFilter) { state.filter = t.dataset.sfFilter; render(); return; }
      if (t.dataset.sfMode) {
        const tz = (state.config.settings && state.config.settings.timezone) || 'Asia/Kabul';
        if (t.dataset.sfMode === 'online_until') {
          const m = parseHm(($('sf-until') || {}).value || '');
          const err = $('sf-until-err');
          if (m === null || m >= 1440) { if (err) { err.hidden = false; err.textContent = 'ساعت را مثلِ 20:30 بنویسید'; } return; }
          declare('online_until', declaredUntilFor(m, Date.now(), tz));
        } else declare(t.dataset.sfMode);
        return;
      }
      const act = t.dataset.sf;
      const row = t.closest('[data-id]');
      const p = row && state.products ? state.products.find((x) => x.id === row.dataset.id) : null;
      if (act === 'retry') open();
      else if (act === 'flush') flush();
      else if (act === 'reload-products') { state.products = null; state.productsError = ''; render(); }
      else if (act === 'bulk-on') bulk(true);
      else if (act === 'bulk-off') bulk(false);
      else if (act === 'bulk-clear') { state.selected.clear(); render(); }
      else if (act === 'edit' && p) editDialog(p);
    });
    r.addEventListener('change', (e) => {
      const t = e.target;
      const act = t.dataset && t.dataset.sf;
      if (!act) return;
      const row = t.closest('[data-id]');
      const p = row && state.products ? state.products.find((x) => x.id === row.dataset.id) : null;
      if (act === 'select' && p) { if (t.checked) state.selected.add(p.id); else state.selected.delete(p.id); render(); }
      else if (act === 'select-all') {
        const q = state.query.trim().toLowerCase();
        state.products.filter((x) => (!q || String(x.name).toLowerCase().includes(q))
          && (state.filter === 'on' ? x.published : state.filter === 'off' ? !x.published : true))
          .forEach((x) => (t.checked ? state.selected.add(x.id) : state.selected.delete(x.id)));
        render();
      } else if (act === 'publish' && p) {
        const want = t.checked;
        t.disabled = true;
        saveProduct(p, want, p.onlinePriceMinor ?? null, p.maxQtyMilli ?? null).then((err) => {
          if (err) toast(err, 'error');
          render();
        });
      }
    });
    r.addEventListener('input', (e) => {
      if (e.target.id === 'sf-q') {
        state.query = e.target.value;
        clearTimeout(wire.t);
        wire.t = setTimeout(() => { const pos = e.target.selectionStart; render(); const q = $('sf-q'); if (q) { q.focus(); try { q.setSelectionRange(pos, pos); } catch { /* */ } } }, 250);
      }
    });
    r.addEventListener('submit', (e) => {
      if (e.target.id === 'sf-settings') { e.preventDefault(); saveSettings(e.target); }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    wire();
    //  دکمه‌ای که کار نمی‌کند دیده نمی‌شود: تا سرور «روشن» نگفته، کارت پنهان است
    setTimeout(refreshGate, 600);
    document.addEventListener('tohid:page-change', (e) => {
      const id = e.detail && e.detail.pageId;
      if (id === 'more') refreshGate();
    });
    window.addEventListener('online', () => { if (loadPending()) flush(); });
  });

  window.TohidStorefrontOwner = {
    open, refreshGate,
    //  برای آزمون: همان قاعده‌های StorefrontOwner.kt
    _rules: { slugError, parseMinor, formatMinor, parseQtyMilli, parseHm, parseDay, formatDay, parseClosed, declaredUntilFor, clock, badgeLabel },
  };
})();
