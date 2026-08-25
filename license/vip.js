/* ==========================================================
   توحید | اشتراک VIP، دوره آزمایشی و قفل قابلیت‌ها
   ----------------------------------------------------------
   ورود اجباری نیست. کاربر بدون حساب هم وارد برنامه می‌شود و
   بخش‌های رایگان را کامل دارد. فقط وقتی سراغ قابلیت اشتراکی برود،
   پیشنهاد ساخت حساب / خرید اشتراک می‌آید.

   تصمیم نهایی درباره‌ی دسترسی با سرور است. این فایل فقط نمایش می‌دهد.
   ========================================================== */
(function () {
  'use strict';

  const ACCT_KEY = 'tohid-license-v1';
  const ENT_KEY  = 'tohid-entitlement-v1';
  const SERVER_KEY = 'tohid-license-server-url';

  // قابلیت‌های رایگان — همان فهرستی که سرور دارد
  const FREE = ['warehouse', 'expenses', 'purchasing', 'reports', 'audit_log', 'backup', 'csv_export'];
  const CORE = ['dashboard', 'products', 'settings'];
  const PAID = ['sales', 'debtors', 'barcode', 'multi_device'];

  const LABELS = {
    sales: 'فروش (صندوق)', debtors: 'قرض‌داران', barcode: 'اسکنر بارکد',
    multi_device: 'چند کاربر روی یک دکان',
  };

  // نگاشت قابلیت اشتراکی → صفحه‌های برنامه
  const PAGES = {
    sales: ['sale', 'quick-sale', 'sales'],
    debtors: ['debtors', 'debtor-account'],
  };
  const ELEMENTS = {
    barcode: ['#btn-test-camera', '#btn-manual-lookup'],
  };

  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fa = (n) => Number(n).toLocaleString('fa-IR');
  const read = (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } };
  const write = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };

  function serverUrl() {
    const v = (localStorage.getItem(SERVER_KEY) || '').trim();
    return v ? v.replace(/\/+$/, '') : '';
  }
  const account = () => read(ACCT_KEY, {});
  const loggedIn = () => !!account().accessToken;

  // ---------- وضعیت دسترسی ----------
  // آخرین پاسخ سرور کش می‌شود تا برنامه آفلاین هم بداند چه چیزی باز است.
  let ENT = read(ENT_KEY, null) || {
    source: 'guest', features: FREE.slice(), free: FREE, core: CORE,
    trial: { used: false, active: false, daysLeft: 0 }, isPaid: false, message: '',
  };

  /*
   * تصمیم نهایی با سرور است. تا وقتی آدرس سروری تنظیم نشده، هیچ چیزی
   * قفل نمی‌شود: قفلِ سمت مرورگر نه قابل اتکاست و نه راهی برای خرید
   * باقی می‌گذارد. در این حالت فقط نشان و صفحه‌ی قیمت‌ها دیده می‌شود.
   */
  function enforcing() { return !!serverUrl(); }

  function hasFeature(key) {
    if (CORE.includes(key)) return true;
    if (!enforcing()) return true;
    return (ENT.features || []).includes(key);
  }

  async function refresh() {
    const base = serverUrl();
    if (!base) return ENT;
    const headers = { 'Content-Type': 'application/json' };
    const a = account();
    if (a.accessToken) headers.Authorization = 'Bearer ' + a.accessToken;
    try {
      const res = await fetch(base + '/api/v1/billing/status', { headers });
      if (!res.ok) return ENT;
      const data = await res.json();
      if (data && data.entitlement) {
        ENT = data.entitlement;
        write(ENT_KEY, ENT);
        apply();
      }
    } catch { /* آفلاین: با وضعیت کش‌شده ادامه می‌دهیم */ }
    return ENT;
  }

  // ---------- پلن‌ها ----------
  /*
   * قیمت‌های پیش‌فرض — همان چیزی که سرور موقع نصب می‌نویسد.
   * وقتی سرور وصل است، همیشه پاسخ سرور جای این می‌نشیند؛ پس تغییر قیمت
   * از پنل مدیریت انجام می‌شود و این فهرست فقط برای وقتی است که سرور
   * در دسترس نیست (مثلاً نسخه‌ی نمایشی روی گیت‌هاب) تا کاربر
   * دست‌کم بداند اشتراک چند است.
   */
  const FALLBACK_CURRENCY = 'افغانی';
  const FALLBACK_WHATSAPP = '0792236008';
  const FALLBACK_MESSAGE = 'سلام، می‌خواهم اشتراک برنامه توحید را بخرم.';
  const FALLBACK_PLANS = [
    { code: 'w1', title: '۱ هفته', amount: 1, unit: 'week', price: 100 },
    { code: 'm1', title: '۱ ماه', amount: 1, unit: 'month', price: 300 },
    { code: 'm3', title: '۳ ماه', amount: 3, unit: 'month', price: 800 },
    { code: 'm6', title: '۶ ماه', amount: 6, unit: 'month', price: 1500 },
    { code: 'y1', title: '۱ سال', amount: 1, unit: 'year', price: 2800, badge: 'پیشنهاد ما' },
    { code: 'y2', title: '۲ سال', amount: 2, unit: 'year', price: 5000 },
    { code: 'y3', title: '۳ سال', amount: 3, unit: 'year', price: 6800, badge: 'بیشترین صرفه' },
    { code: 'custom', title: 'دلخواه', amount: null, unit: null, price: 0, negotiable: true },
  ];

  function approxDays(amount, unit) {
    if (!amount || !unit) return 0;
    if (unit === 'day') return amount;
    if (unit === 'week') return amount * 7;
    if (unit === 'month') return amount * 30;
    if (unit === 'year') return amount * 365;
    return 0;
  }

  function waUrl(number, text) {
    const digits = String(number).replace(/[^0-9]/g, '').replace(/^0/, '93');
    return 'https://wa.me/' + digits + '?text=' + encodeURIComponent(text);
  }

  function fallbackPlans() {
    return {
      currency: FALLBACK_CURRENCY,
      plans: FALLBACK_PLANS.map(p => {
        const days = approxDays(p.amount, p.unit);
        const perDay = (!p.negotiable && days > 0 && p.price > 0)
          ? Math.round((p.price / days) * 10) / 10 : null;
        return {
          code: p.code, title: p.title, amount: p.amount, unit: p.unit,
          price: p.price, negotiable: !!p.negotiable, badge: p.badge || '',
          features: PAID.slice(), approxDays: days, pricePerDay: perDay,
          whatsappUrl: waUrl(
            FALLBACK_WHATSAPP,
            FALLBACK_MESSAGE + ' (' + p.title + ')',
          ),
        };
      }),
      whatsapp: { number: FALLBACK_WHATSAPP, url: waUrl(FALLBACK_WHATSAPP, FALLBACK_MESSAGE) },
    };
  }

  let PLANS = null;
  async function loadPlans() {
    if (PLANS) return PLANS;
    const base = serverUrl();
    if (base) {
      try {
        const res = await fetch(base + '/api/v1/billing/plans');
        if (res.ok) {
          PLANS = await res.json();
          return PLANS;
        }
      } catch { /* آفلاین یا سرور خاموش — با قیمت‌های پیش‌فرض ادامه می‌دهیم */ }
    }
    PLANS = fallbackPlans();
    return PLANS;
  }

  // ---------- قفل رابط کاربری ----------
  function lockedList() { return PAID.filter(k => !hasFeature(k)); }

  function apply() {
    const root = document.documentElement;
    const locked = lockedList();
    PAID.forEach(k => root.classList.toggle('vip-lock-' + k, locked.includes(k)));
    paintPages(locked);
    paintElements(locked);
    paintBadge();
    paintTrialBar();
  }

  function paintPages(locked) {
    Object.keys(PAGES).forEach(feature => {
      PAGES[feature].forEach(pageId => {
        const page = document.getElementById('page-' + pageId);
        if (!page) return;
        let ov = page.querySelector(':scope > .vip-overlay');
        if (!locked.includes(feature)) { if (ov) ov.remove(); return; }
        if (ov) return;
        ov = document.createElement('div');
        ov.className = 'vip-overlay';
        ov.innerHTML = `
          <div class="vip-card">
            <div class="vip-crown">👑</div>
            <h3>${esc(LABELS[feature] || feature)}</h3>
            <p>${esc(reasonText())}</p>
            <button type="button" class="vip-btn" data-vip-open>مشاهده اشتراک‌ها</button>
          </div>`;
        ov.querySelector('[data-vip-open]').addEventListener('click', () => open(feature));
        page.appendChild(ov);
      });
    });
  }

  function paintElements(locked) {
    Object.keys(ELEMENTS).forEach(feature => {
      const isLocked = locked.includes(feature);
      ELEMENTS[feature].forEach(sel => $$(sel).forEach(el => {
        el.classList.toggle('vip-locked-el', isLocked);
        if (isLocked) el.setAttribute('data-vip-feature', feature);
        else el.removeAttribute('data-vip-feature');
      }));
    });
  }

  function reasonText() {
    const t = ENT.trial || {};
    if (!loggedIn()) return 'برای استفاده از این بخش، حساب بسازید و ۷ روز رایگان امتحان کنید.';
    if (t.used && !t.active) return 'دوره آزمایشی شما به پایان رسیده است. برای ادامه استفاده، یک اشتراک انتخاب کنید.';
    return 'این بخش نیازمند اشتراک است.';
  }

  /** نشان کوچک بالای صفحه: تبلیغ حساب رایگان یا روزهای باقی‌مانده. */
  function paintBadge() {
    const header = $('.header .header-right');
    if (!header) return;
    let el = $('#vip-badge');
    const t = ENT.trial || {};

    let text, tone;
    if (!enforcing()) { text = 'اشتراک و قیمت‌ها'; tone = 'promo'; }
    else if (!loggedIn()) { text = 'حساب رایگان بسازید'; tone = 'promo'; }
    else if (ENT.source === 'subscription') { text = 'اشتراک فعال'; tone = 'ok'; }
    else if (t.active) {
      text = t.daysLeft <= 1 ? 'کمتر از یک روز' : `${fa(t.daysLeft)} روز آزمایشی`;
      tone = t.daysLeft <= 2 ? 'warn' : 'trial';
    } else { text = 'ارتقا به VIP'; tone = 'warn'; }

    if (!el) {
      el = document.createElement('button');
      el.id = 'vip-badge';
      el.type = 'button';
      el.addEventListener('click', () => open());
      header.insertBefore(el, header.firstChild);
    }
    el.className = 'vip-badge vip-badge-' + tone;
    el.innerHTML = `<span class="vip-badge-icon">👑</span><span>${esc(text)}</span>`;
  }

  /** نوار هشدار در روزهای آخر دوره آزمایشی و پس از پایان آن. */
  function paintTrialBar() {
    const t = ENT.trial || {};
    let show = false, text = '', tone = 'warn';

    if (loggedIn()) {
      if (t.active && t.daysLeft <= 2) {
        text = t.daysLeft <= 1
          ? 'کمتر از یک روز از دوره آزمایشی شما باقی مانده است'
          : `${fa(t.daysLeft)} روز از دوره آزمایشی شما باقی مانده است`;
        show = true;
      } else if (t.used && !t.active && ENT.source !== 'subscription') {
        text = 'دوره آزمایشی شما به پایان رسیده است. برای ادامه استفاده، یک اشتراک انتخاب کنید.';
        tone = 'danger'; show = true;
      }
    }

    let bar = $('#vip-trial-bar');
    if (!show) { if (bar) bar.remove(); return; }
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'vip-trial-bar';
      bar.innerHTML = '<span></span><button type="button" class="vip-btn vip-btn-sm">مشاهده اشتراک‌ها</button>';
      bar.querySelector('button').addEventListener('click', () => open());
      document.body.appendChild(bar);
    }
    bar.className = 'vip-bar-' + tone;
    bar.querySelector('span').textContent = text;
  }

  // کلیک روی دکمه‌های قفل: به جای اجرا، پیشنهاد اشتراک
  document.addEventListener('click', function (e) {
    const el = e.target.closest && e.target.closest('[data-vip-feature]');
    if (!el) return;
    const feature = el.getAttribute('data-vip-feature');
    if (hasFeature(feature)) return;
    e.preventDefault(); e.stopPropagation();
    if (e.stopImmediatePropagation) e.stopImmediatePropagation();
    open(feature);
  }, true);

  // ---------- پنجره اشتراک ----------
  let modal = null;

  function ensure() {
    if (modal) return modal;
    modal = document.createElement('div');
    modal.id = 'vip-modal';
    modal.className = 'vip-scrim';
    modal.innerHTML = `
      <div class="vip-box">
        <div class="vip-head">
          <h3><span>👑</span> اشتراک VIP</h3>
          <button type="button" class="vip-close" aria-label="بستن">&times;</button>
        </div>
        <div class="vip-body">
          <div id="vip-status"></div>
          <div id="vip-note"></div>
          <div id="vip-plans" class="vip-plans"></div>
          <div class="vip-contact">
            <p class="vip-hint">برای خرید اشتراک، پلن مورد نظر را انتخاب کنید تا در واتساپ پیام بدهید.</p>
            <a class="vip-wa" id="vip-wa" target="_blank" rel="noopener">
              <span>واتساپ</span>
              <b dir="ltr" id="vip-wa-num">—</b>
            </a>
          </div>
        </div>
      </div>`;
    document.body.appendChild(modal);
    modal.querySelector('.vip-close').addEventListener('click', close);
    modal.addEventListener('mousedown', e => { if (e.target === modal) close(); });
    return modal;
  }

  function statusHtml() {
    const t = ENT.trial || {};
    if (!loggedIn()) {
      return `<div class="vip-status vip-status-promo">
        <b>۷ روز رایگان</b>
        <span>حساب بسازید و همه‌ی قابلیت‌ها را ۷ روز رایگان امتحان کنید. اطلاعاتی که ثبت می‌کنید در حساب خودتان می‌ماند.</span>
      </div>`;
    }
    if (ENT.source === 'subscription') {
      const s = ENT.subscription || {};
      const end = s.endsAt ? new Date(s.endsAt).toLocaleDateString('fa-IR') : '—';
      return `<div class="vip-status vip-status-ok">
        <b>اشتراک فعال</b>
        <span>تا تاریخ ${esc(end)}</span>
      </div>`;
    }
    if (t.active) {
      const msg = t.daysLeft <= 1
        ? 'کمتر از یک روز از دوره آزمایشی شما باقی مانده است'
        : `${fa(t.daysLeft)} روز از دوره آزمایشی شما باقی مانده است`;
      return `<div class="vip-status vip-status-trial"><b>دوره آزمایشی رایگان</b><span>${esc(msg)}</span></div>`;
    }
    return `<div class="vip-status vip-status-end">
      <b>دوره آزمایشی به پایان رسیده</b>
      <span>اطلاعات شما محفوظ است. برای ادامه استفاده، یک اشتراک انتخاب کنید.</span>
    </div>`;
  }

  function planCard(p, currency) {
    const per = p.negotiable ? '' :
      (p.pricePerDay ? `<span class="vip-per">روزی حدود ${fa(p.pricePerDay)} ${esc(currency)}</span>` : '');
    const price = p.negotiable ? 'توافقی' : `${fa(p.price)} <small>${esc(currency)}</small>`;
    return `<button type="button" class="vip-plan${p.badge ? ' vip-plan-badge' : ''}" data-plan="${esc(p.code)}">
      ${p.badge ? `<span class="vip-tag">${esc(p.badge)}</span>` : ''}
      <span class="vip-plan-title">${esc(p.title)}</span>
      <span class="vip-plan-price">${price}</span>
      ${per}
    </button>`;
  }

  async function render(highlight) {
    const box = ensure();
    $('#vip-status', box).innerHTML = statusHtml();
    $('#vip-note', box).innerHTML = highlight
      ? `<div class="vip-note">قابلیت «${esc(LABELS[highlight] || highlight)}» با اشتراک باز می‌شود.</div>`
      : '';

    const data = await loadPlans();
    if (!data) {
      $('#vip-plans', box).innerHTML =
        '<p class="vip-hint">برای دیدن پلن‌ها، آدرس سرور را در تنظیمات وارد کنید.</p>';
      return;
    }
    $('#vip-plans', box).innerHTML = data.plans.map(p => planCard(p, data.currency)).join('');
    $('#vip-wa', box).href = data.whatsapp.url;
    $('#vip-wa-num', box).textContent = data.whatsapp.number;

    $$('#vip-plans [data-plan]', box).forEach(btn => {
      btn.addEventListener('click', () => {
        const plan = data.plans.find(p => p.code === btn.dataset.plan);
        if (!plan) return;
        $$('#vip-plans [data-plan]', box).forEach(b => b.classList.toggle('vip-plan-on', b === btn));
        $('#vip-wa', box).href = plan.whatsappUrl || data.whatsapp.url;
        if (loggedIn()) requestPlan(plan.code);
      });
    });
  }

  /** ثبت درخواست خرید روی سرور تا مدیر آن را در پنل ببیند. */
  async function requestPlan(code) {
    const base = serverUrl();
    const a = account();
    if (!base || !a.accessToken) return;
    try {
      await fetch(base + '/api/v1/billing/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + a.accessToken },
        body: JSON.stringify({ planCode: code }),
      });
    } catch { /* بی‌صدا: خرید از راه واتساپ انجام می‌شود */ }
  }

  function open(highlight) { ensure(); render(highlight); modal.classList.add('vip-open'); }
  function close() { if (modal) modal.classList.remove('vip-open'); }

  // ---------- راه‌اندازی ----------
  function init() {
    apply();
    refresh();
    setInterval(refresh, 10 * 60 * 1000);
    window.addEventListener('online', refresh);
    document.addEventListener('tohid:license-change', refresh);
  }

  window.TohidVip = {
    get entitlement() { return ENT; },
    hasFeature, refresh, open, close, loadPlans,
    FREE, PAID, CORE,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
