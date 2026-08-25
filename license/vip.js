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


  /* ---------- آیکون‌ها ----------
     همه دستی کشیده شده‌اند (SVG). هیچ ایموجی در برنامه استفاده نمی‌شود،
     چون ایموجی روی هر گوشی شکل و رنگ متفاوتی دارد و در ویندوز قدیمی
     اصلاً نمایش داده نمی‌شود. */
  const SVG = (d, extra) =>
    `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" ` +
    `stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"${extra || ''}>${d}</svg>`;

  const ICON = {
    crown: SVG('<path d="M3 8.5l3.6 2.6L12 4l5.4 7.1L21 8.5 19.2 18H4.8L3 8.5z"/>' +
               '<path d="M4.8 20.5h14.4"/>'),
    check: SVG('<path d="M4.5 12.6l4.6 4.6L19.5 6.8"/>'),
    lock: SVG('<rect x="4.5" y="10.5" width="15" height="9.5" rx="2.2"/>' +
              '<path d="M8 10.5V7.8a4 4 0 1 1 8 0v2.7"/>'),
    gift: SVG('<path d="M3.5 11.5h17V20a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 20v-8.5z"/>' +
              '<rect x="2.5" y="7.5" width="19" height="4" rx="1.3"/>' +
              '<path d="M12 7.5v14"/>' +
              '<path d="M12 7.5S10.6 2.5 8 2.5a2.5 2.5 0 0 0 0 5"/>' +
              '<path d="M12 7.5s1.4-5 4-5a2.5 2.5 0 0 1 0 5"/>'),
    chat: SVG('<path d="M20.5 12.2c0 4-3.8 7.2-8.5 7.2-1 0-2-.15-2.9-.42L4 20.5l1.6-3.9' +
              'C4.25 15.35 3.5 13.85 3.5 12.2c0-4 3.8-7.2 8.5-7.2s8.5 3.2 8.5 7.2z"/>'),
    globe: SVG('<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17"/>' +
               '<path d="M12 3.5c2.2 2.4 3.3 5.4 3.3 8.5S14.2 18.1 12 20.5"/>' +
               '<path d="M12 3.5C9.8 5.9 8.7 8.9 8.7 12s1.1 6.1 3.3 8.5"/>'),
    close: SVG('<path d="M6.2 6.2l11.6 11.6"/><path d="M17.8 6.2L6.2 17.8"/>'),
    users: SVG('<circle cx="9" cy="8" r="3.4"/>' +
               '<path d="M3 20v-1.2A4.8 4.8 0 0 1 7.8 14h2.4a4.8 4.8 0 0 1 4.8 4.8V20"/>' +
               '<path d="M16.5 4.9a3.4 3.4 0 0 1 0 6.2"/><path d="M17.6 14h.6A4.8 4.8 0 0 1 23 18.8V20"/>'),
    cart: SVG('<circle cx="9.5" cy="19.5" r="1.5"/><circle cx="17.5" cy="19.5" r="1.5"/>' +
              '<path d="M2.5 3.5h2.6l2.4 11.3h11l2-7.8H6.4"/>'),
    scan: SVG('<path d="M3.5 8V5.5A2 2 0 0 1 5.5 3.5H8"/><path d="M16 3.5h2.5a2 2 0 0 1 2 2V8"/>' +
              '<path d="M20.5 16v2.5a2 2 0 0 1-2 2H16"/><path d="M8 20.5H5.5a2 2 0 0 1-2-2V16"/>' +
              '<path d="M3.5 12h17"/>'),
    box: SVG('<path d="M3.5 7.8L12 3.5l8.5 4.3v8.4L12 20.5 3.5 16.2V7.8z"/>' +
             '<path d="M3.5 7.8L12 12l8.5-4.2"/><path d="M12 12v8.5"/>'),
  };

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
    { code: 'm1', title: 'ماهانه', amount: 1, unit: 'month', price: 500 },
    { code: 'm6', title: '۶ ماهه', amount: 6, unit: 'month', price: 2000, badge: 'پیشنهاد ما' },
    { code: 'y1', title: '۱ ساله', amount: 1, unit: 'year', price: 3000, badge: 'بیشترین صرفه' },
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
            <div class="vip-crown">${ICON.lock}</div>
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
    el.innerHTML = `<span class="vip-badge-icon">${ICON.crown}</span><span>${esc(text)}</span>`;
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
        <button type="button" class="vip-close" aria-label="بستن">${ICON.close}</button>

        <div class="vip-hero">
          <div class="vip-logo">${ICON.crown}</div>
          <h2><b>قیمت ساده</b> برای<br>مدیریت دکان</h2>
          <p>رایگان شروع کنید. بدون هزینه‌ی پنهان.</p>
        </div>

        <div class="vip-body">
          <div id="vip-status"></div>
          <div id="vip-note"></div>

          <div class="vip-tiers" id="vip-tiers"></div>

          <div class="vip-section-title">مدت اشتراک را انتخاب کنید</div>
          <div id="vip-plans" class="vip-plans"></div>

          <a class="vip-cta" id="vip-cta" target="_blank" rel="noopener">
            <span class="vip-cta-ic">${ICON.gift}</span>
            <b>گرفتن اشتراک</b>
            <i>بدون قرارداد. بدون ریسک.</i>
          </a>

          <div class="vip-section-title">راه‌های تماس</div>
          <div class="vip-contact">
            <a class="vip-ccard" id="vip-wa" target="_blank" rel="noopener">
              <span class="vip-ccard-ic vip-ic-wa">${ICON.chat}</span>
              <span class="vip-ccard-txt">
                <b>واتساپ</b>
                <i dir="ltr" id="vip-wa-num">—</i>
              </span>
            </a>
            <div class="vip-ccard">
              <span class="vip-ccard-ic vip-ic-web">${ICON.globe}</span>
              <span class="vip-ccard-txt">
                <b>پرداخت بیرون از برنامه</b>
                <i>هماهنگی و پرداخت از راه واتساپ انجام می‌شود.</i>
              </span>
            </div>
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

  /* برچسب فارسی هر قابلیت رایگان، برای فهرست تیک‌دار کارت رایگان */
  const FREE_LABELS = {
    warehouse: 'انبار و موجودی', expenses: 'مصارف دکان',
    purchasing: 'خریداری و تأمین‌کننده', reports: 'گزارش‌ها و سود',
    audit_log: 'دفتر رویدادها', backup: 'پشتیبان‌گیری', csv_export: 'خروجی اکسل',
  };

  function tick(text, on) {
    return `<li class="${on ? 'on' : 'off'}">
      <span class="vip-tick">${on ? ICON.check : ICON.lock}</span>${esc(text)}</li>`;
  }

  /** دو کارت مقایسه‌ای: رایگان در برابر اشتراک. */
  function tiersHtml(currency) {
    const paidNow = ENT.source === 'subscription';
    const trialNow = !!(ENT.trial && ENT.trial.active);

    const freeList = Object.keys(FREE_LABELS).map(k => tick(FREE_LABELS[k], true)).join('');
    const paidExtra = PAID.map(k => tick(LABELS[k] || k, true)).join('');
    const freeLocked = PAID.map(k => tick(LABELS[k] || k, false)).join('');

    return `
      <div class="vip-tier">
        <div class="vip-tier-name">رایگان</div>
        <div class="vip-tier-price">۰ <small>${esc(currency)}</small></div>
        <div class="vip-tier-sub">همیشه رایگان</div>
        <ul class="vip-ticks">${freeList}${freeLocked}</ul>
        <div class="vip-tier-foot${paidNow || trialNow ? '' : ' is-now'}">
          ${paidNow || trialNow ? 'شامل حال شما نیست' : 'همین حالا فعال است'}
        </div>
      </div>
      <div class="vip-tier vip-tier-hot">
        <div class="vip-ribbon">پیشنهاد ما</div>
        <div class="vip-tier-name">اشتراک VIP</div>
        <div class="vip-tier-price">همه‌چیز</div>
        <div class="vip-tier-sub">هر مدتی که بخواهید</div>
        <ul class="vip-ticks">${freeList}${paidExtra}</ul>
        <div class="vip-tier-foot${paidNow || trialNow ? ' is-now' : ''}">
          ${paidNow ? 'اشتراک شما فعال است'
            : trialNow ? 'در دوره‌ی آزمایشی باز است'
            : 'مدت را از پایین انتخاب کنید'}
        </div>
      </div>`;
  }

  function planCard(p, currency) {
    const per = p.negotiable ? '<span class="vip-per">با ما هماهنگ کنید</span>' :
      (p.pricePerDay ? `<span class="vip-per">روزی حدود ${fa(p.pricePerDay)} ${esc(currency)}</span>` : '');
    const price = p.negotiable ? 'توافقی' : `${fa(p.price)} <small>${esc(currency)}</small>`;
    return `<button type="button" class="vip-plan${p.badge ? ' vip-plan-badge' : ''}" data-plan="${esc(p.code)}">
      ${p.badge ? `<span class="vip-tag">${esc(p.badge)}</span>` : ''}
      <span class="vip-plan-title">${esc(p.title)}</span>
      <span class="vip-plan-price">${price}</span>
      ${per}
      <span class="vip-plan-pick">${ICON.check}<i>انتخاب</i></span>
    </button>`;
  }

  async function render(highlight) {
    const box = ensure();
    $('#vip-status', box).innerHTML = statusHtml();
    $('#vip-note', box).innerHTML = highlight
      ? `<div class="vip-note">قابلیت «${esc(LABELS[highlight] || highlight)}» با اشتراک باز می‌شود.</div>`
      : '';

    const data = await loadPlans();
    $('#vip-tiers', box).innerHTML = tiersHtml(data.currency);
    $('#vip-plans', box).innerHTML = data.plans.map(p => planCard(p, data.currency)).join('');
    $('#vip-wa', box).href = data.whatsapp.url;
    $('#vip-wa-num', box).textContent = data.whatsapp.number;
    $('#vip-cta', box).href = data.whatsapp.url;

    // ورود پلکانی کارت‌ها — هر کارت کمی بعد از قبلی بالا می‌آید
    $$('#vip-plans .vip-plan', box).forEach((el, i) => {
      el.style.setProperty('--vip-delay', (i * 45) + 'ms');
    });
    $$('#vip-tiers .vip-tier', box).forEach((el, i) => {
      el.style.setProperty('--vip-delay', (i * 90) + 'ms');
    });

    $$('#vip-plans [data-plan]', box).forEach(btn => {
      btn.addEventListener('click', () => {
        const plan = data.plans.find(p => p.code === btn.dataset.plan);
        if (!plan) return;
        $$('#vip-plans [data-plan]', box).forEach(b => b.classList.toggle('vip-plan-on', b === btn));
        const url = plan.whatsappUrl || data.whatsapp.url;
        $('#vip-wa', box).href = url;
        $('#vip-cta', box).href = url;
        $('#vip-cta b', box).textContent = 'گرفتن اشتراک ' + plan.title;
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
