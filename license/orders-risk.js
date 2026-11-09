/**
 * افزونهٔ بخشِ «سفارش‌ها»ی وب — کارتِ «سابقهٔ این مشتری در دکانِ شما» در جزئیاتِ سفارش (RSK-03).
 *
 * ⛔ پیش‌فرض خاموش: `license/orders-ui.js` این فایل را فقط وقتی بار می‌کند که `GET /orders/config`
 * بگوید `risk` (پرچمِ `shop.order_risk`)؛ پرچمِ خاموش ⇒ نه فایل، نه کارت، نه درخواستِ `/risk`.
 * ⛔ داوری با سرور (`GET /orders/:id/risk`، `server/src/lib/order-risk.js`): فقط سفارش‌های همین دکان و
 * **بی شماره**. خطا ⇒ «سابقهٔ مشتری خوانده نشد» با «دوباره»؛ هرگز کارتِ خالیِ «بی‌خطر» به‌جای خطا.
 * همان کارت و همان جمله‌های برنامهٔ نسخهٔ ۳ (`OrderRisk.visible/shown`، `StrRisk`): هشدارها اول.
 *
 * بخشِ بالایی («هسته») خالص است و در Node هم بار می‌شود (`server/test/orders-risk-core.test.js`).
 */
(function (root) {
  'use strict';

  // ══════════════════════════════ هسته ══════════════════════════════

  const TITLE = 'سابقهٔ این مشتری در دکانِ شما';
  const FOOTER = 'فقط سفارش‌های همین دکان؛ شماره دیده نمی‌شود.';
  const FAILED = 'سابقهٔ مشتری خوانده نشد';
  const n = (v) => (Number.isFinite(Number(v)) ? Math.trunc(Number(v)) : 0);
  const FLAGS = {
    blocklisted: () => 'این شماره یا جا در فهرستِ بستهٔ دکان است',
    allowlisted: () => 'این شماره در فهرستِ مطمئنِ دکان است',
    new_customer: () => 'مشتریِ تازه — هنوز هیچ سفارشی از شما نگرفته',
    delivery_failed_before: (f) => `${n(f.count)} بار تحویل ناموفق بوده`,
    no_show_before: (f) => `${n(f.count)} بار سرِ تحویل نبوده`,
    returned_to_shop_before: (f) => `${n(f.count)} سفارش به دکان برگشته`,
    returns_before: (f) => `${n(f.count)} برگشت یا بازپرداخت داشته`,
    cancels_often: (f) => `${n(f.count)} بار خودش لغو کرده`,
    many_open: (f) => `${n(f.count)} سفارشِ بازِ دیگر دارد`,
    possible_duplicate: (f) => (f.orderNo ? `شاید تکراریِ سفارشِ ${n(f.orderNo)}` : 'شاید سفارشِ تکراری'),
  };
  const flagText = (f) => (FLAGS[f.code] ? FLAGS[f.code](f) : `نشانهٔ دیگر: ${String(f.code)}`);

  /**
   * پاسخِ سرور ⇒ آن‌چه کارت می‌گوید، یا null (پرچمِ خاموش، یا چیزی برای گفتن نیست — همان `OrderRisk.visible`).
   * `{ title, summary|null, items:[{warn, text}], footer }`؛ هشدارها اول، به ترتیبِ سرور در هر گروه.
   */
  function view(r) {
    if (!r || r.enabled !== true) return null;
    const flags = (Array.isArray(r.flags) ? r.flags : []).filter((f) => f && typeof f.code === 'string' && f.code);
    if (!r.known && !flags.length) return null;
    const h = r.history;
    const summary = h && n(h.orders) > 0
      ? `${n(h.orders)} سفارشِ پیشین · ${n(h.completed)} تحویل · ${n(h.cancelledByCustomer) + n(h.cancelledByShop) + n(h.rejectedByShop)} لغو یا رد · ${n(h.open)} باز`
      : null;
    const warn = (f) => f.severity === 'warn';
    const items = [...flags.filter(warn), ...flags.filter((f) => !warn(f))].map((f) => ({ warn: warn(f), text: flagText(f) }));
    return { title: TITLE, summary, items, footer: FOOTER };
  }

  const core = { view, flagText, TITLE, FOOTER, FAILED };
  if (typeof module !== 'undefined' && module.exports) { module.exports = core; return; }
  if (typeof document === 'undefined') return;

  // ══════════════════════════════ مرورگر ═════════════════════════════

  /** پاسخِ هر سفارش تا دوباره باز شدنِ همان: { id, state:'loading'|'ok'|'failed', data } */
  const st = { cur: null, mounted: false };

  function html(api) {
    const c = st.cur;
    if (!c || c.state === 'loading') return '';
    if (c.state === 'failed') {
      return `<p class="ord-hint ord-risk-err" role="status">${api.esc(FAILED)}</p>
        <button type="button" class="btn btn-secondary btn-sm" data-risk-retry="${api.esc(c.id)}">دوباره</button>`;
    }
    const v = view(c.data);
    if (!v) return '';
    const li = v.items.map((x) => `<li class="${x.warn ? 'ord-risk-warn' : 'ord-risk-info'}">${api.esc(x.text)}</li>`).join('');
    return `<h4 class="ord-h">${api.esc(v.title)}</h4>
      ${v.summary ? `<p class="ord-risk-sum">${api.esc(v.summary)}</p>` : ''}
      ${li ? `<ul class="ord-risk-flags">${li}</ul>` : ''}
      <p class="ord-hint">${api.esc(v.footer)}</p>`;
  }

  function fill(api) {
    const box = api.$('ord-risk');
    if (box && st.cur && box.dataset.id === st.cur.id) box.innerHTML = html(api);
  }

  async function load(api, id) {
    st.cur = { id, state: 'loading', data: null };
    try {
      const data = await api.call('/' + encodeURIComponent(id) + '/risk');
      if (st.cur && st.cur.id === id) st.cur = { id, state: 'ok', data };
    } catch {
      if (st.cur && st.cur.id === id) st.cur = { id, state: 'failed', data: null };
    }
    fill(api);
  }

  function mount(api) {
    if (st.mounted) return;
    st.mounted = true;
    document.addEventListener('click', (e) => {
      const b = e.target && e.target.closest && e.target.closest('[data-risk-retry]');
      if (b && api.open && api.open.id === b.dataset.riskRetry) { load(api, b.dataset.riskRetry); fill(api); }
    });
  }

  /** جای کارت در جزئیات؛ سفارشِ تازه ⇒ یک بار پرسیدن (بازنقاشیِ همان سفارش دوباره نمی‌پرسد) */
  function detailCard(api, o) {
    if (!o || !o.id || !(api.cfg && api.cfg.risk)) return '';
    if (!st.cur || st.cur.id !== o.id) load(api, o.id);
    return `<div class="ord-risk" id="ord-risk" data-id="${api.esc(o.id)}" aria-live="polite">${html(api)}</div>`;
  }

  root.TohidOrdersRisk = { mount, detailCard, core };
})(typeof window !== 'undefined' ? window : globalThis);
