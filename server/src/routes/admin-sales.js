'use strict';
/**
 * داشبوردِ فروش، پرداخت‌ها، تخفیف‌ها، افزونه‌ها و کمپین — سمتِ مدیر.
 *
 *   /api/admin/sales/summary          درآمدِ امروز/ماه/سال به تفکیکِ بخش و ارز + نمودارِ ۱۲ ماه
 *   /api/admin/sales/subscriptions    همهٔ اشتراک‌ها با فیلتر
 *   /api/admin/sales/expiring         رو به پایان + یادآوریِ ایمیلی از مرکزِ اعلان
 *   /api/admin/sales/debts            اشتراکِ داده‌شده، پرداخت‌نشده
 *   /api/admin/payments               ثبت و ویرایشِ پرداخت؛ /:id/receipt رسیدِ چاپی
 *   /api/admin/discount-codes         کدهای تخفیف
 *   /api/admin/campaigns              فیلتر ⇒ کد ⇒ اعلان، در یک تماس
 *   /api/admin/subscriptions/:id/…    تخفیفِ مستقیم · افزونه · دائمی (دکان) — و همان‌ها زیرِ /pump
 *
 * ⛔ قیمت‌ها فقط از سرور: هیچ عددی این‌جا نیست، همه از `plans` و از خودِ
 *    اشتراک/پرداخت خوانده می‌شود.
 * ⛔ شناسهٔ حساب از مسیر/بدنه می‌آید ولی **فقط مدیر** این‌جاست؛ برای
 *    خودِ مشتری همه‌چیز از توکن است (`routes/portal.js`).
 * ⚠️ رسید HTMLِ چاپی است، نه PDF: مرورگر خودش «چاپ ⇒ ذخیره به PDF» دارد
 *    و این سرور عمداً هیچ کتابخانهٔ PDFی ندارد.
 */
const express = require('express');
const { query, one, many, newId, now } = require('../db');
const v = require('../lib/validate');
const plans = require('../lib/plans');
const subs = require('../lib/subscriptions');
const tenancy = require('../lib/tenancy');
const discounts = require('../lib/discounts');
const notices = require('../lib/notices');
const audit = require('../lib/audit');
const { requireAdmin } = require('../middleware/auth');
const { badRequest, notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireAdmin);

const DAY = 24 * 60 * 60 * 1000;
const PERMANENT_YEARS = 50;
const CURRENCY_FA = { AFN: 'افغانی', USD: 'دالر' };
const METHOD_FA = { cash: 'نقد', hawala: 'حواله', exchange: 'صرافی' };

function appOf(raw, def = 'shop') { return tenancy.sectionOf(raw) || def; }

/** شروعِ روز/ماه/سال به وقتِ سرور (UTC) — همان ساعتی که همهٔ اشتراک‌ها با آن حساب می‌شوند. */
function startOf(kind, at = now()) {
  const d = new Date(at);
  if (kind === 'day') d.setUTCHours(0, 0, 0, 0);
  if (kind === 'month') { d.setUTCDate(1); d.setUTCHours(0, 0, 0, 0); }
  if (kind === 'year') { d.setUTCMonth(0, 1); d.setUTCHours(0, 0, 0, 0); }
  return d.getTime();
}

/* ==========================================================
   پرداخت‌ها
   ========================================================== */

function shapePayment(r) {
  return {
    id: r.id, app: r.app, tenantKind: r.tenant_kind, tenantId: r.tenant_id, subscriptionId: r.subscription_id || '',
    amount: Number(r.amount), currency: r.currency, method: r.method, receiptNo: r.receipt_no || '',
    note: r.note || '', paidAt: Number(r.paid_at), createdBy: r.created_by || '', createdAt: Number(r.created_at),
    ...(r.tenant_name !== undefined ? { tenantName: r.tenant_name || '' } : {}),
    ...(r.owner_name !== undefined ? { ownerName: r.owner_name || '', ownerEmail: r.owner_email || '', ownerPhone: r.owner_phone || '' } : {}),
  };
}

const PAYMENT_JOIN = `
  LEFT JOIN shops s   ON p.app='shop' AND s.id = p.tenant_id
  LEFT JOIN stations st ON p.app='pump' AND st.id = p.tenant_id
  LEFT JOIN users u   ON u.id = COALESCE(s.owner_user_id, st.owner_user_id)`;
const PAYMENT_COLS = `p.*, COALESCE(s.name, st.name) AS tenant_name,
  u.name AS owner_name, u.email AS owner_email, u.phone AS owner_phone`;

router.get('/payments', async (req, res, next) => {
  try {
    const app = req.query?.app ? appOf(req.query.app) : '';
    const rows = await many(
      `SELECT ${PAYMENT_COLS} FROM sub_payments p ${PAYMENT_JOIN}
        WHERE p.deleted = false AND ($1 = '' OR p.app = $1) AND ($2 = '' OR p.tenant_id = $2)
        ORDER BY p.paid_at DESC LIMIT $3`,
      [app, v.text(req.query?.tenantId, { max: 80 }), v.integer(req.query?.limit, { min: 1, max: 500, def: 100 })]
    );
    res.json({ payments: rows.map(shapePayment) });
  } catch (err) { next(err); }
});

async function paymentInput(body, cur = null) {
  const app = cur ? cur.app : appOf(body?.app);
  const T = tenancy.byApp(app);
  const tenantId = cur ? cur.tenant_id : v.id(body?.tenantId, { field: 'شناسهٔ حساب' });
  if (!cur) {
    const t = await one(`SELECT id FROM ${T.tenantTable} WHERE id=$1`, [tenantId]);
    if (!t) throw notFound(T.notFoundMessage, T.notFoundCode);
  }
  let subscriptionId = body?.subscriptionId === undefined ? (cur ? cur.subscription_id : '') : String(body.subscriptionId || '');
  if (!cur && !subscriptionId) {
    const live = await subs.forApp(app).latestOf(tenantId);
    subscriptionId = live ? live.id : '';
  }
  return {
    app, tenantKind: app === 'pump' ? 'station' : 'shop', tenantId, subscriptionId,
    amount: body?.amount === undefined && cur ? Number(cur.amount) : v.integer(body?.amount, { field: 'مبلغ', min: 1, max: 1e9 }),
    currency: body?.currency === undefined && cur ? cur.currency
      : v.oneOf(body?.currency, ['AFN', 'USD'], { field: 'ارز', def: app === 'pump' ? 'USD' : 'AFN' }),
    method: body?.method === undefined && cur ? cur.method
      : v.oneOf(body?.method, ['cash', 'hawala', 'exchange'], { field: 'روش', def: 'cash' }),
    receiptNo: body?.receiptNo === undefined && cur ? cur.receipt_no : v.text(body?.receiptNo, { max: 60 }),
    note: body?.note === undefined && cur ? cur.note : v.text(body?.note, { max: 300 }),
    paidAt: body?.paidAt === undefined && cur ? Number(cur.paid_at) : v.timestamp(body?.paidAt, { def: now() }),
  };
}

router.post('/payments', async (req, res, next) => {
  try {
    const p = await paymentInput(req.body);
    const t = now();
    const id = newId('pay');
    //  شمارهٔ رسید اگر نیامد، خودمان می‌سازیم: سال-شماره
    const receiptNo = p.receiptNo || `${new Date(t).getUTCFullYear()}-${String(
      (await one(`SELECT COUNT(*)::int n FROM sub_payments`)).n + 1
    ).padStart(5, '0')}`;
    await query(
      `INSERT INTO sub_payments (id, app, tenant_kind, tenant_id, subscription_id, amount, currency, method,
                             receipt_no, note, paid_at, created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
      [id, p.app, p.tenantKind, p.tenantId, p.subscriptionId, p.amount, p.currency, p.method,
        receiptNo, p.note, p.paidAt, req.admin.id, t]
    );
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.payment_created', targetType: 'payment', targetId: id,
      detail: { app: p.app, amount: p.amount, currency: p.currency } });
    const row = await one(`SELECT ${PAYMENT_COLS} FROM sub_payments p ${PAYMENT_JOIN} WHERE p.id=$1`, [id]);
    res.status(201).json({ payment: shapePayment(row) });
  } catch (err) { next(err); }
});

router.put('/payments/:id', async (req, res, next) => {
  try {
    const cur = await one('SELECT * FROM sub_payments WHERE id=$1 AND deleted=false', [v.id(req.params.id)]);
    if (!cur) return next(notFound('پرداخت پیدا نشد', 'payment_not_found'));
    const p = await paymentInput(req.body, cur);
    await query(
      `UPDATE sub_payments SET subscription_id=$2, amount=$3, currency=$4, method=$5, receipt_no=$6, note=$7, paid_at=$8 WHERE id=$1`,
      [cur.id, p.subscriptionId, p.amount, p.currency, p.method, p.receiptNo, p.note, p.paidAt]
    );
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.payment_updated', targetType: 'payment', targetId: cur.id });
    const row = await one(`SELECT ${PAYMENT_COLS} FROM sub_payments p ${PAYMENT_JOIN} WHERE p.id=$1`, [cur.id]);
    res.json({ payment: shapePayment(row) });
  } catch (err) { next(err); }
});

router.delete('/payments/:id', async (req, res, next) => {
  try {
    const row = await one(`UPDATE sub_payments SET deleted=true WHERE id=$1 AND deleted=false RETURNING id`, [v.id(req.params.id)]);
    if (!row) return next(notFound('پرداخت پیدا نشد', 'payment_not_found'));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.payment_deleted', targetType: 'payment', targetId: row.id });
    res.json({ ok: true });
  } catch (err) { next(err); }
});

function escapeHtml(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/**
 * رسید/فاکتورِ چاپی — HTML راست‌به‌چپ.
 *
 * مرورگر «چاپ» را می‌زند و «ذخیره به PDF» را انتخاب می‌کند؛ هیچ کتابخانهٔ
 * PDFی روی سرور نیست و نمی‌آید (سرور عمداً چهار وابستگی بیشتر ندارد).
 */
async function receiptHtml(p) {
  const T = tenancy.byApp(p.app);
  const sub = p.subscription_id ? await one(`SELECT * FROM ${T.subsTable} WHERE id=$1`, [p.subscription_id]) : null;
  const planRow = sub ? await plans.getPlan(sub.plan, p.app) : null;
  const cfg = await plans.allConfig();
  const brand = cfg.brand_name || 'VILL3N';
  const fa = (n) => Number(n || 0).toLocaleString('fa-AF');
  const date = (ms) => ms ? new Date(Number(ms)).toLocaleDateString('fa-AF', { year: 'numeric', month: '2-digit', day: '2-digit' }) : '—';
  const rows = [
    ['شمارهٔ رسید', p.receipt_no || '—'],
    ['تاریخ پرداخت', date(p.paid_at)],
    ['مشتری', `${p.owner_name || '—'}${p.owner_phone ? ` · ${p.owner_phone}` : ''}${p.owner_email ? ` · ${p.owner_email}` : ''}`],
    [p.app === 'pump' ? 'پمپ‌بنزین' : 'دکان', p.tenant_name || '—'],
    ['برنامه', notices.APP_LABEL[p.app]],
    ['پلن', planRow ? planRow.title : (sub ? sub.plan : '—')],
    ['دورهٔ اشتراک', sub ? `${date(sub.starts_at)} تا ${date(sub.ends_at)}` : '—'],
    ['روش پرداخت', METHOD_FA[p.method] || p.method],
    ['یادداشت', p.note || '—'],
  ];
  return `<!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>رسید ${escapeHtml(p.receipt_no)} — ${escapeHtml(brand)}</title>
<style>
  body{font-family:Vazirmatn,Tahoma,Arial,sans-serif;background:#f4f6fb;color:#1a2233;margin:0;padding:24px}
  .sheet{max-width:720px;margin:0 auto;background:#fff;border:1px solid #e5eaf3;border-radius:16px;padding:32px}
  .head{display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #2c5ce6;padding-bottom:14px;margin-bottom:18px}
  .brand{font:900 26px/1 Arial,Tahoma;letter-spacing:3px;color:#2c5ce6}
  h1{font-size:20px;margin:0}
  table{width:100%;border-collapse:collapse;margin-top:8px}
  td{padding:10px 8px;border-bottom:1px solid #eef2fa;font-size:14px;vertical-align:top}
  td:first-child{color:#7c8698;width:36%}
  .total{margin-top:22px;padding:16px;background:#eaf0ff;border-radius:12px;display:flex;justify-content:space-between;font-size:20px;font-weight:900}
  .foot{margin-top:24px;font-size:12px;color:#7c8698;text-align:center}
  .btn{display:inline-block;margin:0 auto 18px;padding:10px 18px;border-radius:10px;background:#2c5ce6;color:#fff;border:0;font:700 14px Vazirmatn,Tahoma;cursor:pointer}
  @media print{body{background:#fff;padding:0}.sheet{border:0;border-radius:0}.btn{display:none}}
</style></head><body>
<div style="text-align:center"><button class="btn" onclick="window.print()">چاپ / ذخیره به PDF</button></div>
<div class="sheet">
  <div class="head"><div class="brand">${escapeHtml(brand)}</div><h1>رسیدِ پرداخت</h1></div>
  <table>${rows.map(([k, val]) => `<tr><td>${escapeHtml(k)}</td><td>${escapeHtml(val)}</td></tr>`).join('')}</table>
  <div class="total"><span>مبلغ</span><span>${fa(p.amount)} ${CURRENCY_FA[p.currency] || escapeHtml(p.currency)}</span></div>
  <div class="foot">این رسید از سامانهٔ ${escapeHtml(brand)} صادر شده است · ${date(p.created_at)}</div>
</div></body></html>`;
}

router.get('/payments/:id/receipt', async (req, res, next) => {
  try {
    const row = await one(`SELECT ${PAYMENT_COLS} FROM sub_payments p ${PAYMENT_JOIN} WHERE p.id=$1 AND p.deleted=false`, [v.id(req.params.id)]);
    if (!row) return next(notFound('پرداخت پیدا نشد', 'payment_not_found'));
    res.set('Content-Type', 'text/html; charset=utf-8');
    res.send(await receiptHtml(row));
  } catch (err) { next(err); }
});

/* ==========================================================
   خلاصهٔ فروش
   ========================================================== */

router.get('/sales/summary', async (req, res, next) => {
  try {
    const at = now();
    const marks = { today: startOf('day', at), month: startOf('month', at), year: startOf('year', at) };
    const rows = await many(`SELECT app, currency, amount, paid_at FROM sub_payments WHERE deleted=false AND paid_at >= $1`,
      [Math.min(marks.year, at - 366 * DAY)]);
    const revenue = {};
    const bump = (period, app, cur, amount) => {
      revenue[period] = revenue[period] || {};
      revenue[period][app] = revenue[period][app] || {};
      revenue[period][app][cur] = (revenue[period][app][cur] || 0) + amount;
    };
    for (const period of ['today', 'month', 'year']) revenue[period] = { shop: {}, pump: {} };
    for (const r of rows) {
      const t = Number(r.paid_at); const a = Number(r.amount);
      if (t >= marks.today) bump('today', r.app, r.currency, a);
      if (t >= marks.month) bump('month', r.app, r.currency, a);
      if (t >= marks.year) bump('year', r.app, r.currency, a);
    }
    //  نمودارِ رشد: ۱۲ ماهِ اخیر، هر ماه به تفکیکِ بخش و ارز
    const series = [];
    const d = new Date(at); d.setUTCDate(1); d.setUTCHours(0, 0, 0, 0);
    for (let i = 11; i >= 0; i--) {
      const m = new Date(d); m.setUTCMonth(m.getUTCMonth() - i);
      const from = m.getTime();
      const to = plans.endOfPeriod(from, 1, 'month');
      const bucket = { month: `${m.getUTCFullYear()}-${String(m.getUTCMonth() + 1).padStart(2, '0')}`, from, to, shop: {}, pump: {}, payments: 0 };
      for (const r of rows) {
        const t = Number(r.paid_at);
        if (t >= from && t < to) { bucket[r.app][r.currency] = (bucket[r.app][r.currency] || 0) + Number(r.amount); bucket.payments++; }
      }
      series.push(bucket);
    }
    const counts = {};
    for (const app of ['shop', 'pump']) {
      const T = tenancy.byApp(app);
      await subs.forApp(app).expireDue(at);
      counts[app] = {
        active: (await one(`SELECT COUNT(*)::int n FROM ${T.subsTable} WHERE status='active'`)).n,
        expired: (await one(`SELECT COUNT(*)::int n FROM ${T.subsTable} WHERE status='expired'`)).n,
        suspended: (await one(`SELECT COUNT(*)::int n FROM ${T.subsTable} WHERE status='suspended'`)).n,
        tenants: (await one(`SELECT COUNT(*)::int n FROM ${T.tenantTable} WHERE status='active'`)).n,
      };
    }
    res.json({ revenue, series, counts, serverTime: at });
  } catch (err) { next(err); }
});

/* ==========================================================
   فهرستِ اشتراک‌ها با فیلتر
   ========================================================== */

async function subscriptionsOf(app, { status = '', city = '', kind = '', limit = 300 } = {}) {
  const T = tenancy.byApp(app);
  const at = now();
  const rows = await many(
    `SELECT sub.*, s.name AS tenant_name, s.owner_user_id, u.name AS owner_name, u.email AS owner_email,
            u.phone AS owner_phone, u.city AS owner_city,
            (SELECT dl.label FROM device_locations dl WHERE dl.user_id = u.id ORDER BY dl.created_at DESC LIMIT 1) AS loc_label,
            (SELECT COALESCE(SUM(p.amount),0)::bigint FROM sub_payments p
              WHERE p.app=$1 AND p.subscription_id = sub.id AND p.deleted=false) AS paid
       FROM ${T.subsTable} sub
       JOIN ${T.tenantTable} s ON s.id = sub.${T.tenantKey}
       LEFT JOIN users u ON u.id = s.owner_user_id
      ORDER BY sub.updated_at DESC LIMIT $2`,
    [app, Math.min(Number(limit) || 300, 2000)]
  );
  const planList = await plans.listPlans({ activeOnly: false, app });
  const titles = Object.fromEntries(planList.map(p => [p.code, p.title]));
  const cityQ = String(city || '').trim().toLowerCase();
  return rows.map(r => {
    const state = subs.stateOf(r, at);
    const permanent = r.plan === 'perm' || Number(r.ends_at) - at > 10 * 365 * DAY;
    return {
      id: r.id, app, tenantId: r[T.tenantKey], tenantName: r.tenant_name, ownerUserId: r.owner_user_id || '',
      ownerName: r.owner_name || '', ownerEmail: r.owner_email || '', ownerPhone: r.owner_phone || '',
      city: r.owner_city || r.loc_label || '',
      plan: r.plan, planTitle: titles[r.plan] || r.plan, status: state.status, active: state.active,
      startsAt: state.startsAt, endsAt: state.endsAt, daysLeft: Math.ceil((state.graceEndsAt - at) / DAY),
      permanent, price: r.price === null || r.price === undefined ? null : Number(r.price), currency: r.currency,
      paid: Number(r.paid), features: r.features, note: r.note || '',
    };
  }).filter(r => {
    if (status && r.status !== status) return false;
    if (cityQ && !String(r.city).toLowerCase().includes(cityQ)) return false;
    if (kind === 'permanent' && !r.permanent) return false;
    if (kind && kind !== 'permanent' && r.plan !== kind) return false;
    return true;
  });
}

router.get('/sales/subscriptions', async (req, res, next) => {
  try {
    const app = req.query?.app ? appOf(req.query.app) : '';
    const opts = {
      status: v.text(req.query?.status, { max: 20 }), city: v.text(req.query?.city, { max: 60 }),
      kind: v.text(req.query?.kind, { max: 20 }), limit: v.integer(req.query?.limit, { min: 1, max: 2000, def: 300 }),
    };
    const list = [];
    for (const a of app ? [app] : ['shop', 'pump']) list.push(...await subscriptionsOf(a, opts));
    res.json({ subscriptions: list, serverTime: now() });
  } catch (err) { next(err); }
});

/* ==========================================================
   رو به پایان — و یادآوریِ ایمیلی از مرکزِ اعلان
   ========================================================== */

async function expiringList(days, app = '') {
  const out = [];
  for (const a of app ? [app] : ['shop', 'pump']) {
    const rows = await subs.forApp(a).expiringSoon({ withinDays: days, includeExpired: 0, limit: 500 });
    out.push(...rows.map(r => ({ ...r, app: a })));
  }
  return out.sort((x, y) => x.daysLeft - y.daysLeft);
}

router.get('/sales/expiring', async (req, res, next) => {
  try {
    const days = v.integer(req.query?.days, { field: 'روزها', min: 1, max: 90, def: 7 });
    res.json({ expiring: await expiringList(days, req.query?.app ? appOf(req.query.app) : ''), days, serverTime: now() });
  } catch (err) { next(err); }
});

/**
 * یادآوری به همهٔ رو به پایان‌ها با قالبِ `expiring`، از همان مرکزِ اعلان —
 * پس گزارشش هم همان‌جاست (هر گیرنده، هر کانال).
 */
router.post('/sales/expiring/remind', async (req, res, next) => {
  try {
    const days = v.integer(req.body?.days, { field: 'روزها', min: 1, max: 90, def: 7 });
    const app = v.oneOf(req.body?.app, ['shop', 'pump', 'both'], { field: 'بخش', def: 'both' });
    const tpl = await notices.templateFor('expiring', app === 'pump' ? 'pump' : 'shop');
    const n = await notices.create({
      app, audience: { kind: 'filter', expiring_days: days },
      channels: req.body?.channels || (tpl ? tpl.channels : ['inapp', 'email']),
      title: tpl ? tpl.title : 'اشتراک رو به پایان است', body: tpl ? tpl.body : '', templateKey: 'expiring',
    }, { createdBy: req.admin.id });
    const sent = await notices.send(n.id, { by: req.admin.id });
    res.json({ noticeId: n.id, ...sent });
  } catch (err) { next(err); }
});

/* ==========================================================
   بدهی‌ها: اشتراکِ فعال که پرداختش کم‌تر از قیمتش است
   ========================================================== */

router.get('/sales/debts', async (req, res, next) => {
  try {
    const app = req.query?.app ? appOf(req.query.app) : '';
    const list = [];
    for (const a of app ? [app] : ['shop', 'pump']) {
      const rows = await subscriptionsOf(a, { limit: 2000 });
      for (const r of rows) {
        if (!['active', 'suspended'].includes(r.status)) continue;
        let price = r.price;
        if (price === null) {
          const pl = await plans.getPlan(r.plan, a);
          price = pl ? plans.discountOf(pl, Number(r.startsAt)).finalPrice : 0;
        }
        if (price > r.paid) list.push({ ...r, price, debt: price - r.paid });
      }
    }
    res.json({ debts: list.sort((x, y) => y.debt - x.debt), serverTime: now() });
  } catch (err) { next(err); }
});

/* ==========================================================
   کارهای یک‌کلیکی روی اشتراک: دائمی · تخفیفِ مستقیم · افزونه
   ----------------------------------------------------------
   دکان: /admin/subscriptions/:id/…   پمپ: /admin/pump/subscriptions/:id/…
   (صدور/تمدید/تعلیق/لغو از قبل در admin.js و admin-pump.js هست.)
   ========================================================== */

async function makePermanent(app, id, by) {
  const T = tenancy.byApp(app);
  const cur = await one(`SELECT * FROM ${T.subsTable} WHERE id=$1`, [id]);
  if (!cur) throw notFound('اشتراک پیدا نشد', 'subscription_not_found');
  const t = now();
  const end = plans.endOfPeriod(t, PERMANENT_YEARS, 'year');
  const row = await one(
    `UPDATE ${T.subsTable} SET status='active', ends_at=$2, plan=CASE WHEN plan='' OR plan='custom' THEN 'perm' ELSE plan END,
            updated_at=$3, created_by=COALESCE(NULLIF($4,''), created_by) WHERE id=$1 RETURNING *`,
    [cur.id, end, t, by]
  );
  await query(
    `INSERT INTO ${T.historyTable}
       (id, subscription_id, ${T.tenantKey}, action, plan, prev_status, new_status, prev_ends_at, new_ends_at, actor, note, created_at)
     VALUES ($1,$2,$3,'permanent',$4,$5,$6,$7,$8,$9,$10,$11)`,
    [newId('sbh'), row.id, row[T.tenantKey], row.plan, cur.status, row.status, Number(cur.ends_at), Number(row.ends_at), by, 'تبدیل به دائمی', t]
  );
  return row;
}

for (const [prefix, app] of [['/subscriptions', 'shop'], ['/pump/subscriptions', 'pump']]) {
  router.post(`${prefix}/:id/permanent`, async (req, res, next) => {
    try {
      const row = await makePermanent(app, v.id(req.params.id), req.admin.id);
      await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.subscription_permanent', targetType: 'subscription', targetId: row.id, detail: { app } });
      res.json({ subscription: row, state: subs.stateOf(row), permanent: true });
    } catch (err) { next(err); }
  });

  router.post(`${prefix}/:id/discount`, async (req, res, next) => {
    try {
      const out = await discounts.directDiscount({
        app, subscriptionId: v.id(req.params.id),
        percent: v.integer(req.body?.percent, { min: 0, max: 100, def: 0 }),
        amount: v.integer(req.body?.amount, { min: 0, max: 1e9, def: 0 }),
        reason: v.text(req.body?.reason, { max: 200 }), by: req.admin.id,
      });
      await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.subscription_discount', targetType: 'subscription', targetId: req.params.id, detail: { app, finalPrice: out.finalPrice } });
      res.json({ ...out, state: subs.stateOf(out.subscription) });
    } catch (err) { next(err); }
  });

  router.get(`${prefix}/:id/addons`, async (req, res, next) => {
    try { res.json({ addons: await discounts.listAddons({ app, subscriptionId: v.id(req.params.id) }) }); }
    catch (err) { next(err); }
  });

  router.post(`${prefix}/:id/addons`, async (req, res, next) => {
    try {
      const addon = await discounts.addAddon({
        app, subscriptionId: v.id(req.params.id), feature: v.text(req.body?.feature, { max: 40, required: true, field: 'قابلیت' }),
        price: v.integer(req.body?.price, { min: 0, max: 1e9, def: 0 }), note: v.text(req.body?.note, { max: 200 }), by: req.admin.id,
      });
      await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.addon_added', targetType: 'subscription', targetId: req.params.id, detail: { app, feature: addon.feature } });
      res.status(201).json({ addon });
    } catch (err) { next(err); }
  });

  router.delete(`${prefix}/:id/addons/:addonId`, async (req, res, next) => {
    try {
      const addon = await discounts.removeAddon(v.id(req.params.addonId), { app });
      await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.addon_removed', targetType: 'subscription', targetId: req.params.id, detail: { app, feature: addon.feature } });
      res.json({ addon });
    } catch (err) { next(err); }
  });
}

/* ==========================================================
   کدهای تخفیف و تاریخچهٔ قیمت
   ========================================================== */

router.get('/discount-codes', async (req, res, next) => {
  try {
    res.json({ codes: await discounts.listCodes({
      app: req.query?.app ? appOf(req.query.app) : '', status: v.text(req.query?.status, { max: 20 }),
      limit: v.integer(req.query?.limit, { min: 1, max: 1000, def: 200 }),
    }) });
  } catch (err) { next(err); }
});

router.post('/discount-codes', async (req, res, next) => {
  try {
    const code = await discounts.createCode(req.body || {}, { createdBy: req.admin.id });
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.discount_code_created', targetType: 'discount_code', targetId: code.id, detail: { app: code.app, kind: code.kind, value: code.value } });
    res.status(201).json({ code });
  } catch (err) { next(err); }
});

router.post('/discount-codes/:id/revoke', async (req, res, next) => {
  try {
    const code = await discounts.revokeCode(v.id(req.params.id));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.discount_code_revoked', targetId: code.id });
    res.json({ code });
  } catch (err) { next(err); }
});

/** سنجیدنِ یک کد از پنل — همان `quote`ی که برنامه می‌زند. */
router.post('/discount-codes/quote', async (req, res, next) => {
  try {
    res.json(await discounts.quote(req.body?.code, {
      app: appOf(req.body?.app), plan: v.text(req.body?.plan, { max: 20 }), userId: v.text(req.body?.userId, { max: 80 }),
    }));
  } catch (err) { next(err); }
});

router.get('/plans/:code/price-history', async (req, res, next) => {
  try {
    res.json({ history: await discounts.priceHistory({ app: appOf(req.query?.app), plan: req.params.code }) });
  } catch (err) { next(err); }
});

router.get('/price-history', async (req, res, next) => {
  try {
    res.json({ history: await discounts.priceHistory({ app: appOf(req.query?.app), plan: v.text(req.query?.plan, { max: 20 }) }) });
  } catch (err) { next(err); }
});

/* ==========================================================
   کمپین
   ========================================================== */

router.get('/campaigns', async (req, res, next) => {
  try { res.json({ campaigns: await discounts.listCampaigns() }); } catch (err) { next(err); }
});

router.post('/campaigns', async (req, res, next) => {
  try {
    const out = await discounts.createCampaign(req.body || {}, { createdBy: req.admin.id });
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.campaign_created', targetType: 'campaign', targetId: out.campaign.id, detail: { app: out.campaign.app } });
    res.status(201).json(out);
  } catch (err) { next(err); }
});

router.get('/campaigns/:id/stats', async (req, res, next) => {
  try { res.json(await discounts.campaignStats(v.id(req.params.id))); } catch (err) { next(err); }
});

/**
 * نشانیِ دانلود و آخرین نسخهٔ هر برنامه — همان چیزی که پورتال و SDK
 * (`GET /api/downloads`) می‌خوانند. در `app_config` می‌نشیند.
 */
router.put('/downloads', async (req, res, next) => {
  try {
    const app = appOf(req.body?.app);
    for (const [key, max] of [['url', 300], ['version', 32], ['notes', 1000]]) {
      if (req.body?.[key] !== undefined) {
        const cfgKey = key === 'url' ? `download_url_${app}` : key === 'version' ? `latest_version_${app}` : `release_notes_${app}`;
        await plans.setConfig(cfgKey, v.text(req.body[key], { max }));
      }
    }
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.downloads_updated', detail: { app } });
    res.json(await require('./portal').downloadsOf(app));
  } catch (err) { next(err); }
});

/** شهرِ مشتری — برای فیلترِ «شهر» در اعلان و فروش. */
router.put('/users/:id/city', async (req, res, next) => {
  try {
    const row = await one('UPDATE users SET city=$2, updated_at=$3 WHERE id=$1 RETURNING id, city',
      [v.id(req.params.id), v.text(req.body?.city, { max: 60 }), now()]);
    if (!row) return next(notFound('کاربر پیدا نشد'));
    res.json({ user: row });
  } catch (err) { next(err); }
});

module.exports = router;
module.exports.receiptHtml = receiptHtml;
module.exports.shapePayment = shapePayment;
module.exports.PAYMENT_JOIN = PAYMENT_JOIN;
module.exports.PAYMENT_COLS = PAYMENT_COLS;
