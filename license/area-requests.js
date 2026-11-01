/**
 * افزونهٔ بخشِ «سفارش‌ها»ی وب — فهرستِ «وقتی ارسال به این‌جا رسید خبرم کن» ِ ویترین برای دکان (CHK-10، S34).
 *
 * ⛔ پیش‌فرض خاموش: `license/orders-ui.js` (`loadExt`) این فایل را فقط وقتی بار می‌کند که `GET /orders/config` بگوید
 * `notifyArea` (پرچمِ `shop.storefront.notify_area` روی ناحیه‌ها، و فقط صاحب و مدیر — `settings.write`).
 * خاموش ⇒ نه دکمه، نه درخواست.
 * ⛔ داده فقط از `GET /api/v1/shop/storefront/area-requests`: تقاضا به ازای خانهٔ ۳۰۰ متریِ شبکه، **بی شماره**؛
 * شماره فقط برای درخواستی که ناحیهٔ فعالِ امروز آن را پوشانده (همان کسی که باید خبرش کرد). «خبر دادم» ⇒
 * `POST …/:id/notified` و سرور ردیف را پاک می‌کند؛ پاسخِ ۴۰۴/۴۰۹ (دیگری زودتر خبر داد / ناحیه برداشته شد) ⇒ فهرستِ تازه.
 * ⛔ سرور پیامی نمی‌فرستد (D-09): دکان خودش زنگ می‌زند؛ این‌جا فقط پیوندِ `tel:`.
 *
 * بخشِ بالایی («هسته») خالص است و در Node هم بار می‌شود (`server/test/area-requests-web.test.js`).
 */
(function (root) {
  'use strict';

  // ══════════════════════════════ هسته ══════════════════════════════

  const FMT = (() => {
    try { return new Intl.NumberFormat('fa-AF', { maximumFractionDigits: 3 }); } catch { return null; }
  })();
  const num = (n) => (FMT ? FMT.format(Number(n)) : String(n));

  /** پاسخِ سرور ⇒ آن‌چه صفحه نشان می‌دهد: شمار، خانه‌های پرتقاضا (بیرون از ناحیه اول) و فهرستِ خبر دادن */
  function view(r) {
    const x = r && typeof r === 'object' ? r : {};
    const cells = Array.isArray(x.cells) ? x.cells : [];
    const contacts = Array.isArray(x.contacts) ? x.contacts : [];
    return {
      total: Number(x.total) || 0,
      covered: Number(x.covered) || 0,
      //  جایی که هنوز ارسال نمی‌رسد اول — تصمیمِ «ناحیه را بزرگ‌تر کنم؟»
      cells: cells.slice().sort((a, b) => Number(Boolean(a.coveredBy)) - Number(Boolean(b.coveredBy)) || b.count - a.count).slice(0, 20),
      contacts,
    };
  }
  function cellLabel(c) {
    return `${num(c.count)} درخواست نزدیکِ ${num(c.lat)}، ${num(c.lng)} — ${c.coveredBy ? `اکنون در ناحیهٔ «${c.coveredBy}»` : 'هنوز بیرون از ناحیه‌ها'}`;
  }

  const core = { view, cellLabel };
  if (typeof module !== 'undefined' && module.exports) { module.exports = core; return; }

  // ══════════════════════════════ مرورگر ══════════════════════════════

  const BASE = '/api/v1/shop/storefront/area-requests';
  //  `api.call` ِ orders-ui زیرِ `/orders` است؛ این‌جا همان `apiAuth` ِ حساب با مسیرِ ویترین
  const raw = (path, opts) => root.TohidShop.apiAuth(path, opts);
  const st = { busy: false, data: null };

  /** orders-ui هر بار که `/config` تازه شد `mount` را می‌خواند؛ `notifyArea` رفت ⇒ برداشته می‌شود */
  function mount(api) {
    if (!api.cfg || !api.cfg.notifyArea) { unmount(api); return; }
    if (api.$('ord-area-btn')) return;
    const bar = document.querySelector('#page-orders .toolbar-actions');
    if (!bar) return;
    bar.insertAdjacentHTML('afterbegin', '<button type="button" class="btn btn-secondary btn-sm" id="ord-area-btn">خبرم کن <span class="ord-badge" id="ord-area-n" hidden></span></button>');
    api.$('ord-area-btn').addEventListener('click', () => open(api));
    load(api, false);
  }
  function unmount(api) {
    ['ord-area-btn', 'ord-area-modal'].forEach((id) => { const el = api.$(id); if (el) el.remove(); });
    st.data = null;
  }

  function modal(api) {
    let m = api.$('ord-area-modal');
    if (m) return m;
    document.body.insertAdjacentHTML('beforeend', `
      <div class="modal-scrim ord-modal" id="ord-area-modal">
        <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="ord-area-title">
          <div class="modal-head"><h3 id="ord-area-title">«وقتی ارسال به این‌جا رسید خبرم کن»</h3><button type="button" class="modal-close" data-area-close aria-label="بستن">✕</button></div>
          <div id="ord-area-body" aria-live="polite"></div>
          <p class="ord-err" id="ord-area-err" role="alert"></p>
        </div>
      </div>`);
    m = api.$('ord-area-modal');
    m.addEventListener('click', (e) => {
      if (e.target === m || e.target.closest('[data-area-close]')) { api.closeModal(m); return; }
      const b = e.target.closest('[data-area-done]');
      if (b) done(api, b.getAttribute('data-area-done'), b);
    });
    return m;
  }

  function open(api) {
    modal(api).classList.add('open');
    paint(api);
    load(api, true);
  }

  async function load(api, show) {
    if (st.busy) return;
    st.busy = true;
    try {
      st.data = view(await raw(BASE));
      if (show || (api.$('ord-area-modal') && api.$('ord-area-modal').classList.contains('open'))) paint(api);
      if (api.$('ord-area-err')) api.$('ord-area-err').textContent = '';
    } catch (e) {
      if (api.$('ord-area-err')) api.$('ord-area-err').textContent = (e && e.message) || 'فهرست نیامد؛ دوباره بکوشید.';
    } finally {
      st.busy = false;
      badge(api);
    }
  }

  function badge(api) {
    const n = api.$('ord-area-n');
    if (!n) return;
    const c = st.data ? st.data.contacts.length : 0;
    n.textContent = c ? num(c) : '';
    n.hidden = !c;
  }

  function paint(api) {
    const body = api.$('ord-area-body');
    if (!body) return;
    const d = st.data;
    if (!d) { body.innerHTML = '<p class="ord-hint">در حالِ خواندن…</p>'; return; }
    const esc = api.esc;
    const rows = d.contacts.map((c) => `
      <li class="ord-area-row">
        <span><a class="ltr" href="tel:${esc(c.phone)}">${esc(c.phone)}</a><small class="ord-hint"> — ناحیهٔ «${esc(c.zone)}»، ${esc(api.when(c.requestedAt))}</small></span>
        <button type="button" class="btn btn-primary btn-sm" data-area-done="${esc(c.id)}">خبر دادم</button>
      </li>`).join('');
    body.innerHTML = `
      <p class="ord-hint">${num(d.total)} درخواست؛ ${num(d.covered)} تا اکنون در ناحیهٔ ارسال است. شماره فقط برای همین‌ها دیده می‌شود؛ پیام را خودتان بفرستید یا زنگ بزنید — سرور چیزی نمی‌فرستد.</p>
      ${d.contacts.length ? `<ul class="ord-area-list">${rows}</ul>` : '<p class="ord-hint">کسی برای خبر دادن نیست؛ وقتی ناحیه‌ای محلِ درخواستی را بپوشاند، شماره‌اش این‌جا می‌آید.</p>'}
      ${d.cells.length ? `<h4>تقاضا به ازای محل (خانهٔ ۳۰۰ متری، بی شماره)</h4><ul class="ord-area-cells">${d.cells.map((c) => `<li>${esc(cellLabel(c))}</li>`).join('')}</ul>` : ''}`;
  }

  async function done(api, id, btn) {
    btn.disabled = true;
    try {
      await raw(`${BASE}/${encodeURIComponent(id)}/notified`, { method: 'POST', body: {} });
      api.toast('ثبت شد؛ این درخواست از فهرست برداشته شد.');
    } catch (e) {
      api.toast((e && e.message) || 'ثبت نشد؛ دوباره بکوشید.');
    }
    await load(api, true);
  }

  root.TohidAreaRequests = { mount, unmount, core };
})(typeof window !== 'undefined' ? window : globalThis);
