'use strict';
/**
 * مالیِ مرکزی (ماژولِ ۴.۵) — گزارش‌های کنارِ دفتر: درگاهِ حساب‌پی (یا BLOCKED-EXTERNAL)، در انتظار/ناموفق،
 * مغایرت، تاریخچهٔ کارهای مالی و خروجیِ CSV. دفتر، سند و بدهی در `lib/central-finance.js`.
 * ⛔ همان قاعده‌ها: هیچ عدد بی ردیفِ منبع؛ پولِ درگاه (یکصدم) هرگز با درآمدِ اشتراک جمع نمی‌شود.
 */
const { one, many, now } = require('../db');
const audit = require('./audit');
const plans = require('./plans');
const v = require('./validate');
const {
  LIMITS, APPS, METHODS, FIN_ACTIONS, N, cursorOf, cursorFor, shapeAudit, requireWriter, balances, payments, adjustments, localYmd,
} = require('./central-finance');

const DAY = 24 * 60 * 60 * 1000;

/* ═══════════════════════════════ درگاه (حساب‌پی) ═══════════════════════════════ */

const BLOCKED = 'BLOCKED-EXTERNAL: کلیدِ حساب‌پی نیامده';

async function hasTable(name) {
  const r = await one(
    `SELECT 1 AS ok FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = $1`, [name]
  );
  return Boolean(r);
}

function hesabpayConfigured() {
  try {
    const m = require('./payment-hesabpay');   // #518 — روی این شاخه شاید نباشد
    if (typeof m.configured === 'function') return Boolean(m.configured());
  } catch { /* پایین */ }
  return Boolean(String(process.env.HESABPAY_API_KEY || '').trim() && String(process.env.HESABPAY_WEBHOOK_SECRET || '').trim());
}

/**
 * حالِ درگاه برای تطبیقِ «پرداخت ⇄ شمارهٔ تراکنش». ⛔ هیچ کلید یا نشانیِ پرداختی برنمی‌گردد.
 *   blocked_external  جدولِ `payment_intents` (#518) نیست، یا کلیدهای HESABPAY_* داده نشده
 *   live              شمارِ هر وضعیت، «نیازمندِ بررسی» (تطبیقِ شبانهٔ #529) و فهرستِ صفحه‌بندی‌شده
 * ⚠️ پرداختِ درگاه پولِ مشتریِ دکان است (فروشِ صندوق/سفارشِ آنلاین) به واحدِ یکصدم (`amountMinor`) — هرگز با
 *    درآمدِ اشتراک جمع نمی‌شود.
 */
async function gateway(q = {}) {
  const base = { provider: 'hesabpay', checkedAt: now(), unit: 'minor' };
  if (!(await hasTable('payment_intents'))) {
    return { ...base, state: 'blocked_external', code: 'payment_intents_missing', message: BLOCKED, needs: 'shop #518 + #529 + HESABPAY_*' };
  }
  const rows = N((await one('SELECT COUNT(*)::int AS n FROM payment_intents'))?.n);
  if (!hesabpayConfigured()) {
    return { ...base, state: 'blocked_external', code: 'hesabpay_key_missing', message: BLOCKED, needs: 'HESABPAY_API_KEY + HESABPAY_WEBHOOK_SECRET', intents: rows };
  }
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max: LIMITS.pageMax, def: 50 });
  const where = [];
  const params = [];
  if (q.status) { params.push(v.oneOf(q.status, ['created', 'pending', 'paid', 'failed', 'expired', 'cancelled', 'refunded'], { field: 'وضعیت' })); where.push(`i.status = $${params.length}`); }
  if (v.bool(q.review)) where.push('i.needs_review = true');
  if (q.match === 'unmatched') where.push(`i.status IN ('paid','refunded') AND i.provider_ref = ''`);
  const c = cursorOf(q.cursor);
  if (c) { params.push(c.at, c.id); where.push(`(i.updated_at, i.id) < ($${params.length - 1}::bigint, $${params.length}::text)`); }
  params.push(limit + 1);
  const [counts, list] = await Promise.all([
    many(`SELECT status, COUNT(*)::int AS n, COUNT(*) FILTER (WHERE needs_review)::int AS review,
                 COUNT(*) FILTER (WHERE status IN ('paid','refunded') AND provider_ref = '')::int AS unmatched,
                 COALESCE(SUM(amount_minor), 0)::bigint AS minor
            FROM payment_intents GROUP BY status ORDER BY status`),
    many(`SELECT i.id, i.shop_id, s.name AS shop_name, i.kind, i.ref_id, i.amount_minor, i.currency, i.status, i.provider_ref,
                 i.paid_at, i.needs_review, i.status_reason, i.created_at, i.updated_at
            FROM payment_intents i LEFT JOIN shops s ON s.id = i.shop_id
           ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
           ORDER BY i.updated_at DESC, i.id DESC LIMIT $${params.length}`, params),
  ]);
  const page = list.slice(0, limit);
  const last = page[page.length - 1];
  return {
    ...base, state: 'live', intents: rows,
    byStatus: counts.map((r) => ({ status: r.status, count: N(r.n), review: N(r.review), unmatched: N(r.unmatched), amountMinor: N(r.minor) })),
    items: page.map((r) => ({
      id: r.id, shopId: r.shop_id, shopName: r.shop_name ?? null, kind: r.kind, refId: r.ref_id, amountMinor: N(r.amount_minor),
      currency: r.currency, status: r.status, providerRef: r.provider_ref || '', matched: Boolean(r.provider_ref),
      paidAt: r.paid_at === null ? null : N(r.paid_at), needsReview: r.needs_review === true, statusReason: r.status_reason || '',
      createdAt: N(r.created_at), updatedAt: N(r.updated_at),
    })),
    nextCursor: list.length > limit && last ? cursorFor(last.updated_at, last.id) : null,
  };
}

/* ═══════════════════════════════ در انتظار / ناموفق ═══════════════════════════════ */

async function pending(q = {}) {
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max: LIMITS.pageMax, def: 50 });
  const offset = v.integer(q.offset, { field: 'آغاز', min: 0, max: 1e6, def: 0 });
  const t = now();
  const [counts, rows] = await Promise.all([
    many(`SELECT status, COUNT(*)::int AS n FROM purchase_requests GROUP BY status`),
    many(`SELECT p.id, p.shop_id, s.name AS shop_name, p.plan_code, p.note, p.created_at
            FROM purchase_requests p LEFT JOIN shops s ON s.id = p.shop_id
           WHERE p.status = 'pending' ORDER BY p.created_at ASC, p.id LIMIT $1 OFFSET $2`, [limit, offset]),
  ]);
  const planCache = new Map();
  const items = [];
  for (const r of rows) {
    if (!planCache.has(r.plan_code)) planCache.set(r.plan_code, await plans.getPlan(r.plan_code, 'shop'));
    const pl = planCache.get(r.plan_code);
    items.push({
      id: r.id, shopId: r.shop_id, shopName: r.shop_name ?? null, planCode: r.plan_code, planTitle: pl ? pl.title : null,
      //  قیمتِ امروزِ پلن (برآورد)، نه پول: درخواستِ خرید هنوز پرداختی ندارد
      quotedPrice: pl ? plans.discountOf(pl, t).finalPrice : null, currency: 'AFN', note: r.note || '',
      createdAt: N(r.created_at), ageDays: Math.floor((t - N(r.created_at)) / DAY),
    });
  }
  const by = Object.fromEntries(counts.map((r) => [r.status, N(r.n)]));
  return {
    purchaseRequests: { counts: { pending: by.pending || 0, approved: by.approved || 0, rejected: by.rejected || 0 }, items, limit, offset, total: by.pending || 0 },
    //  پرداختِ اشتراک امروز فقط دستی ثبت می‌شود ⇒ «ناموفق» برایش وجود ندارد؛ گفته می‌شود، نه صفرِ ساختگی
    subscriptionPayments: { failed: null, reason: 'manual_only', methods: METHODS },
    gateway: await gateway({ limit: 20, status: q.gatewayStatus }),
    checkedAt: t,
  };
}

/* ═══════════════════════════════ مغایرت ═══════════════════════════════ */

const ITEM_COLS = `p.id AS payment_id, p.app, p.tenant_id, COALESCE(s.name, st.name) AS tenant_name, p.amount, p.currency,
  p.receipt_no, p.paid_at, p.subscription_id`;
const ITEM_JOIN = `LEFT JOIN shops s ON p.app = 'shop' AND s.id = p.tenant_id
  LEFT JOIN stations st ON p.app = 'pump' AND st.id = p.tenant_id`;
const shapeItem = (r, extra = {}) => ({
  paymentId: r.payment_id, app: r.app, tenantId: r.tenant_id, tenantName: r.tenant_name ?? null, amount: N(r.amount),
  currency: r.currency, receiptNo: r.receipt_no || '', paidAt: N(r.paid_at), subscriptionId: r.subscription_id || '', ...extra,
});

async function finding(code, severity, source, countSql, itemsSql, params = [], extra = (r) => ({})) {
  const [c, rows] = await Promise.all([one(countSql, params), many(`${itemsSql} LIMIT ${LIMITS.findings}`, params)]);
  return { code, severity, source, count: N(c?.n), items: rows.map((r) => shapeItem(r, extra(r))) };
}

/**
 * گزارشِ مغایرتِ مالیِ مرکزی — هر یافته از ردیف‌های واقعی با شمارِ دقیق و تا ۵۰ نمونه.
 * تطبیقِ فروشِ آنلاینِ دکان‌ها (#411) مالِ `GET /api/admin/system/reconciliation` است (پل جدا می‌خواندش)؛
 * تطبیقِ درگاه (#529) ⇐ `gateway` (این‌جا فقط «نیازمندِ بررسی» و «بی شمارهٔ تراکنش»).
 */
async function discrepancies() {
  const live = `FROM sub_payments p ${ITEM_JOIN} WHERE p.deleted = false`;
  const subMissing = `((p.app = 'shop' AND NOT EXISTS (SELECT 1 FROM subscriptions x WHERE x.id = p.subscription_id))
     OR (p.app = 'pump' AND NOT EXISTS (SELECT 1 FROM station_subscriptions x WHERE x.id = p.subscription_id)))`;
  const tenantMissing = `((p.app = 'shop' AND s.id IS NULL) OR (p.app = 'pump' AND st.id IS NULL))`;
  const curJoin = `LEFT JOIN subscriptions sub ON p.app = 'shop' AND sub.id = p.subscription_id
     LEFT JOIN station_subscriptions ssub ON p.app = 'pump' AND ssub.id = p.subscription_id`;
  const curMismatch = `COALESCE(sub.currency, ssub.currency) IS NOT NULL AND COALESCE(sub.currency, ssub.currency) <> p.currency`;
  const edited = `(EXISTS (SELECT 1 FROM sub_payment_revisions r WHERE r.payment_id = p.id AND r.op = 'UPDATE'
                           AND (r.before->'deleted') IS NOT DISTINCT FROM (r.after->'deleted'))
                OR EXISTS (SELECT 1 FROM audit_logs al WHERE al.action = 'admin.payment_updated' AND al.target_id = p.id))`;
  const findings = [];
  findings.push(await finding('payment_without_subscription', 'warn', 'sub_payments',
    `SELECT COUNT(*)::int AS n ${live} AND p.subscription_id = ''`,
    `SELECT ${ITEM_COLS} ${live} AND p.subscription_id = '' ORDER BY p.paid_at DESC`));
  findings.push(await finding('subscription_missing', 'bad', 'sub_payments ⇄ subscriptions',
    `SELECT COUNT(*)::int AS n ${live} AND p.subscription_id <> '' AND ${subMissing}`,
    `SELECT ${ITEM_COLS} ${live} AND p.subscription_id <> '' AND ${subMissing} ORDER BY p.paid_at DESC`));
  findings.push(await finding('tenant_missing', 'bad', 'sub_payments ⇄ shops/stations',
    `SELECT COUNT(*)::int AS n ${live} AND ${tenantMissing}`,
    `SELECT ${ITEM_COLS} ${live} AND ${tenantMissing} ORDER BY p.paid_at DESC`));
  findings.push(await finding('currency_mismatch', 'bad', 'sub_payments ⇄ subscriptions.currency',
    `SELECT COUNT(*)::int AS n FROM sub_payments p ${ITEM_JOIN} ${curJoin} WHERE p.deleted = false AND ${curMismatch}`,
    `SELECT ${ITEM_COLS}, COALESCE(sub.currency, ssub.currency) AS sub_currency FROM sub_payments p ${ITEM_JOIN} ${curJoin}
      WHERE p.deleted = false AND ${curMismatch} ORDER BY p.paid_at DESC`, [], (r) => ({ subscriptionCurrency: r.sub_currency })));
  //  رسیدِ تکراری (پیش از قیدِ یکتای #592 ممکن بود)
  {
    const dupe = `FROM sub_payments p WHERE p.deleted = false AND p.receipt_no <> ''
      AND EXISTS (SELECT 1 FROM sub_payments q WHERE q.deleted = false AND q.receipt_no = p.receipt_no AND q.id <> p.id)`;
    findings.push(await finding('duplicate_receipt', 'bad', 'sub_payments.receipt_no',
      `SELECT COUNT(*)::int AS n ${dupe}`,
      `SELECT ${ITEM_COLS} FROM sub_payments p ${ITEM_JOIN} WHERE p.id IN (SELECT p.id ${dupe}) ORDER BY p.receipt_no, p.paid_at`));
  }
  findings.push(await finding('legacy_edited', 'warn', 'sub_payment_revisions + audit_logs(admin.payment_updated)',
    `SELECT COUNT(*)::int AS n FROM sub_payments p WHERE ${edited}`,
    `SELECT ${ITEM_COLS} FROM sub_payments p ${ITEM_JOIN} WHERE ${edited} ORDER BY p.paid_at DESC`));
  findings.push(await finding('legacy_deleted', 'warn', 'sub_payments.deleted',
    `SELECT COUNT(*)::int AS n FROM sub_payments p WHERE p.deleted = true`,
    `SELECT ${ITEM_COLS} FROM sub_payments p ${ITEM_JOIN} WHERE p.deleted = true ORDER BY p.paid_at DESC`));
  findings.push(await finding('adjusted_payment_deleted', 'bad', 'finance_adjustments ⇄ sub_payments.deleted',
    `SELECT COUNT(DISTINCT p.id)::int AS n FROM sub_payments p JOIN finance_adjustments a ON a.payment_id = p.id WHERE p.deleted = true`,
    `SELECT ${ITEM_COLS} FROM sub_payments p ${ITEM_JOIN}
      WHERE p.deleted = true AND EXISTS (SELECT 1 FROM finance_adjustments a WHERE a.payment_id = p.id) ORDER BY p.paid_at DESC`));
  //  یک شمارهٔ تراکنش روی چند پرداخت
  {
    const dupRef = `SELECT a.reference FROM finance_adjustments a WHERE a.reference <> '' GROUP BY a.reference HAVING COUNT(DISTINCT a.payment_id) > 1`;
    findings.push(await finding('duplicate_reference', 'warn', 'finance_adjustments.reference',
      `SELECT COUNT(DISTINCT a.payment_id)::int AS n FROM finance_adjustments a WHERE a.reference IN (${dupRef})`,
      `SELECT DISTINCT ON (p.id) ${ITEM_COLS}, a.reference FROM finance_adjustments a JOIN sub_payments p ON p.id = a.payment_id ${ITEM_JOIN}
        WHERE a.reference IN (${dupRef}) ORDER BY p.id`, [], (r) => ({ reference: r.reference })));
  }
  //  بیش‌پرداخت: ماندهٔ منفیِ اشتراک (همان قاعدهٔ بدهی)
  {
    const over = [];
    for (const a of APPS) {
      const b = await balances(a, { onlyPaid: true });
      over.push(...b.rows.filter((r) => r.balance !== null && r.balance < 0));
    }
    over.sort((x, y) => x.balance - y.balance);
    findings.push({
      code: 'overpaid', severity: 'warn', source: 'subscriptions.price ⇄ sub_payments + finance_adjustments', count: over.length,
      items: over.slice(0, LIMITS.findings).map((r) => ({
        paymentId: '', app: r.app, tenantId: r.tenantId, tenantName: r.tenantName, amount: r.paid, currency: r.currency,
        receiptNo: '', paidAt: 0, subscriptionId: r.subscriptionId, price: r.price, excess: -r.balance,
      })),
    });
  }
  const gw = await gateway({ limit: LIMITS.findings, review: '1' });
  if (gw.state === 'live') {
    const review = gw.byStatus.reduce((s, r) => s + r.review, 0);
    const unmatched = gw.byStatus.reduce((s, r) => s + r.unmatched, 0);
    findings.push({ code: 'gateway_review', severity: 'bad', source: 'payment_intents.needs_review (#529)', count: review, items: gw.items });
    findings.push({ code: 'gateway_unmatched', severity: 'warn', source: "payment_intents (paid، provider_ref = '')", count: unmatched, items: [] });
  }
  return {
    findings, gateway: { state: gw.state, code: gw.code || null, message: gw.message || null, needs: gw.needs || null },
    totals: { findings: findings.filter((f) => f.count > 0).length, items: findings.reduce((s, f) => s + f.count, 0) },
    checkedAt: now(),
  };
}

/* ═══════════════════════════════ تاریخچهٔ مالی ═══════════════════════════════ */

async function history(q = {}) {
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max: LIMITS.pageMax, def: 50 });
  const params = [[...FIN_ACTIONS]];
  const where = ['action = ANY($1::text[])'];
  if (q.action) {
    params.push(v.oneOf(q.action, FIN_ACTIONS, { field: 'کار' }));
    where.push(`action = $${params.length}`);
  }
  const target = v.id(q.targetId, { field: 'شناسه', required: false });
  if (target) { params.push(target); where.push(`target_id = $${params.length}`); }
  const c = cursorOf(q.cursor);
  if (c) { params.push(c.at, c.id); where.push(`(created_at, id) < ($${params.length - 1}::bigint, $${params.length}::text)`); }
  params.push(limit + 1);
  const rows = await many(
    `SELECT * FROM audit_logs WHERE ${where.join(' AND ')} ORDER BY created_at DESC, id DESC LIMIT $${params.length}`, params
  );
  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  return {
    entries: page.map(shapeAudit), actions: FIN_ACTIONS,
    nextCursor: rows.length > limit && last ? cursorFor(last.created_at, last.id) : null, limit, checkedAt: now(),
  };
}

/* ═══════════════════════════════ خروجیِ CSV ═══════════════════════════════ */

/** خانهٔ CSV — نقل‌قول، و جلوی «فرمول» در اکسل (= + - @ در آغازِ متن). */
function cell(x) {
  if (x === null || x === undefined) return '';
  if (typeof x === 'number') return String(x);
  let s = String(x);
  if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
const iso = (ms) => (ms ? new Date(Number(ms)).toISOString() : '');

/**
 * خروجیِ CSV، صفحه‌به‌صفحه (هر صفحه حداکثر ۵۰۰۰ ردیف، نشانگرِ بعدی در پاسخ). ⇒ { csv, rows, nextCursor, filename }
 * ⛔ فقط مالی/ارشد (همان `requireWriter`) و هر صفحه یک ردیفِ سابقه با پالایش و شمار.
 */
async function exportCsv(req, q = {}) {
  requireWriter(req, 'خروجیِ مالی');
  const dataset = v.oneOf(q.dataset, ['payments', 'adjustments'], { field: 'داده', def: 'payments' });
  const opts = { max: LIMITS.exportMax };
  const qq = { ...q, limit: q.limit || LIMITS.exportMax };
  let header;
  let lines;
  let nextCursor;
  if (dataset === 'payments') {
    const page = await payments(qq, opts);
    header = ['payment_id', 'receipt_no', 'paid_at', 'app', 'tenant_id', 'tenant_name', 'subscription_id', 'plan', 'amount',
      'adjustments', 'net', 'currency', 'method', 'deleted', 'references', 'created_by', 'created_at'];
    lines = page.payments.map((p) => [p.id, p.receiptNo, iso(p.paidAt), p.app, p.tenantId, p.tenantName, p.subscriptionId, p.plan,
      p.amount, p.adjustments.total, p.deleted ? 0 : p.net, p.currency, p.method, p.deleted ? 'yes' : 'no', p.references.join(' | '),
      p.createdBy, iso(p.createdAt)]);
    nextCursor = page.nextCursor;
  } else {
    const page = await adjustments(qq, opts);
    header = ['doc_no', 'adjustment_id', 'kind', 'payment_id', 'receipt_no', 'app', 'tenant_id', 'tenant_name', 'amount', 'currency',
      'net_before', 'net_after', 'reference', 'reason', 'actor', 'created_at'];
    lines = page.adjustments.map((a) => [a.docNo, a.id, a.kind, a.paymentId, a.receiptNo ?? '', a.app, a.tenantId, a.tenantName,
      a.amount, a.currency, a.netBefore, a.netAfter, a.reference, a.reason, a.actor?.name || a.actor?.id || a.createdBy, iso(a.createdAt)]);
    nextCursor = page.nextCursor;
  }
  const csv = `﻿${[header, ...lines].map((l) => l.map(cell).join(',')).join('\r\n')}\r\n`;
  const filters = Object.fromEntries(Object.entries(q).filter(([k, x]) => x !== undefined && x !== '' && k !== 'password').map(([k, x]) => [k, String(x).slice(0, 80)]));
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.finance_exported', targetType: 'finance', targetId: dataset,
    detail: { dataset, rows: lines.length, filters, more: Boolean(nextCursor) },
  });
  const stamp = localYmd(now());
  return { dataset, filename: `vill3n-finance-${dataset}-${stamp}.csv`, rows: lines.length, nextCursor, csv, checkedAt: now() };
}

module.exports = { BLOCKED, gateway, pending, discrepancies, history, exportCsv, cell };
