'use strict';
/**
 * مالیِ مرکزی — ماژولِ ۴.۵ ِ پنلِ مرکزیِ مالک (شاخهٔ `claude/console-finance-api`، مهاجرتِ ۱۷۱).
 *
 * «مالیِ مرکزی» پولِ **اشتراکِ خودِ پلتفرم** است، جدا از حساب‌داریِ داخلیِ هر دکان:
 *   • درآمد و پرداخت‌ها      ⇐ `sub_payments` (ثبتِ دستیِ مدیر: نقد، حواله، صرافی) — حذف‌شده‌ها بیرون
 *   • اصلاح، بازپرداخت، ابطال ⇐ `finance_adjustments` (سندِ تازه؛ هیچ ردیفی ویرایش/پاک نمی‌شود)
 *   • بدهی                   ⇐ اشتراکِ فعال/معلق که پرداختِ خالصِ هم‌ارزش کم‌تر از قیمتش است
 *   • در انتظار               ⇐ `purchase_requests` (درخواستِ خرید؛ هنوز پولی نیامده)
 *   • درگاه (حساب‌پی)        ⇐ `payment_intents` ِ #518/#529 **اگر** روی این سرور هست و کلید داده شده؛
 *                              وگرنه «BLOCKED-EXTERNAL: کلیدِ حساب‌پی نیامده» — هرگز عددِ ساختگی
 *   • تاریخچه                 ⇐ `audit_logs` (کارهای مالی) + `sub_payment_revisions` (تریگرِ ۱۷۱)
 *
 * ⛔ هیچ عددی بی ردیفِ منبع: هر جمع از همین جدول‌ها و با `checkedAt` ِ سرور. نبود ⇒ خالی، نه صفرِ ساختگی.
 * ⛔ ارزها هرگز با هم جمع نمی‌شوند (AFN و USD جدا).
 * ⛔ سندِ اصلاحی در **روزِ خودش** حساب می‌شود، نه روزِ پرداختِ اصلی ⇒ گزارشِ دورهٔ بسته با اصلاحِ امروز
 *    عوض نمی‌شود (قاعدهٔ «سندِ تازه، نه ویرایش»).
 * ⛔ بازه‌ها به وقتِ `config.defaults.timezone` (کابل)؛ ماهِ پیش‌فرض خورشیدی (`calendar=persian`)، هفته از شنبه.
 */
const config = require('../config');
const { tx, one, many, newId, now } = require('../db');
const { badRequest, notFound, conflict, forbidden, tooMany } = require('../middleware/errors');
const idempotency = require('./idempotency');
const audit = require('./audit');
const plans = require('./plans');
const subs = require('./subscriptions');
const tenancy = require('./tenancy');
const v = require('./validate');

const { windowOf, bucketsOf, bucketYmd, localYmd, badWindow } = require('./finance-window');

const APPS = Object.freeze(['shop', 'pump']);
/** `breakdown?by=` — به‌ازای دکان/پمپ (نامِ پارامتر همان «shop» است، برای هر دو بخش). */
const BY_TENANT = 'shop';
const CURRENCIES = Object.freeze(['AFN', 'USD']);
const METHODS = Object.freeze(['cash', 'hawala', 'exchange']);
const KINDS = Object.freeze(['correction', 'refund', 'reversal', 'reference']);
const LIMITS = Object.freeze({ maxRows: 200000, pageMax: 200, exportMax: 5000, receivablesScan: 20000, findings: 50 });
/** نقش‌هایی که سندِ اصلاحی می‌زنند و خروجیِ CSV می‌گیرند (مدیرِ عادی و viewer نه). */
const WRITE_ROLES = Object.freeze(['superadmin', 'finance']);
/** کارهایی که «تاریخچهٔ مالی» نشان می‌دهد. */
const FIN_ACTIONS = Object.freeze([
  'admin.payment_created', 'admin.payment_updated', 'admin.payment_deleted',
  'admin.finance_adjustment_created', 'admin.finance_exported', 'admin.finance_reauth_failed',
  'admin.request_approved', 'admin.request_rejected',
]);
const REAUTH_FAIL_MAX = 5;
const REAUTH_FAIL_WINDOW = 15 * 60 * 1000;

const N = (x) => (x === null || x === undefined ? 0 : Number(x));
const json = (x) => (x && typeof x === 'string' ? JSON.parse(x) : x || null);

/* ═══════════════════════════════ دفترِ یکپارچه ═══════════════════════════════ */

/**
 * پرداخت‌های زنده + سندهای اصلاحیِ پولی در بازه — یک دفتر. سندِ پرداختِ حذف‌شده هم بیرون (همان پرداخت
 * بیرون است). `$1`=از، `$2`=تا، `$3`=بخش ('' = هر دو).
 */
const LEDGER_SQL = `
  SELECT 'payment'::text AS src, p.id, p.app, p.tenant_id, p.subscription_id, p.currency, p.method,
         p.amount::bigint AS amount, p.paid_at AS at
    FROM sub_payments p
   WHERE p.deleted = false AND p.paid_at >= $1 AND p.paid_at < $2 AND ($3::text = '' OR p.app = $3::text)
  UNION ALL
  SELECT a.kind, a.id, a.app, a.tenant_id, a.subscription_id, a.currency, '' AS method, a.amount, a.created_at
    FROM finance_adjustments a
   WHERE a.amount <> 0 AND a.created_at >= $1 AND a.created_at < $2 AND ($3::text = '' OR a.app = $3::text)
     AND NOT EXISTS (SELECT 1 FROM sub_payments d WHERE d.id = a.payment_id AND d.deleted)`;

function appFilter(raw) {
  if (raw === undefined || raw === null || raw === '' || raw === 'all') return '';
  return v.oneOf(raw, APPS, { field: 'بخش' });
}

const empty = () => ({
  payments: { count: 0, amount: 0 }, correction: { count: 0, amount: 0 }, refund: { count: 0, amount: 0 },
  reversal: { count: 0, amount: 0 }, net: 0,
});

/** درآمدِ بازه: جمعِ هر ارز، سطل‌های زمانی، به تفکیکِ بخش و روش. */
async function summary(q = {}) {
  const w = windowOf(q);
  const app = appFilter(q.app);
  const buckets = bucketsOf(w);
  const rows = await many(`SELECT src, app, currency, method, amount, at FROM (${LEDGER_SQL}) l LIMIT ${LIMITS.maxRows + 1}`,
    [w.from, w.to, app]);
  if (rows.length > LIMITS.maxRows) throw badWindow('ردیف‌های این بازه بیش از سقفِ گزارش است — بازه را کوتاه کنید');
  const index = new Map(buckets.map((b, i) => [b.key, i]));
  const series = buckets.map((b) => ({ ...b, currencies: {} }));
  const totals = {};
  const byService = new Map();
  const byMethod = new Map();
  for (const r of rows) {
    const amount = N(r.amount);
    const cur = r.currency;
    const t = (totals[cur] ||= empty());
    const isPay = r.src === 'payment';
    const box = isPay ? t.payments : t[r.src];
    box.count += 1;
    box.amount += amount;
    t.net += amount;
    const key = bucketYmd(localYmd(Number(r.at), w.timezone), w.group, w.calendar);
    const s = series[index.get(key)];
    if (s) {
      const c = (s.currencies[cur] ||= { payments: 0, adjustments: 0, net: 0, count: 0 });
      if (isPay) { c.payments += amount; c.count += 1; } else c.adjustments += amount;
      c.net += amount;
    }
    const sk = `${r.app}|${cur}`;
    const sv = byService.get(sk) || { app: r.app, currency: cur, payments: 0, adjustments: 0, net: 0, count: 0 };
    if (isPay) { sv.payments += amount; sv.count += 1; } else sv.adjustments += amount;
    sv.net += amount;
    byService.set(sk, sv);
    if (isPay) {
      const mk = `${r.method}|${cur}`;
      const mv = byMethod.get(mk) || { method: r.method, currency: cur, count: 0, amount: 0 };
      mv.count += 1;
      mv.amount += amount;
      byMethod.set(mk, mv);
    }
  }
  return {
    window: w, app: app || 'all', currencies: Object.keys(totals).sort(), totals, series,
    byService: [...byService.values()].sort((a, b) => a.app.localeCompare(b.app) || a.currency.localeCompare(b.currency)),
    byMethod: [...byMethod.values()].sort((a, b) => b.amount - a.amount),
    sources: { payments: 'sub_payments (deleted=false)', adjustments: 'finance_adjustments' },
    rows: rows.length, checkedAt: now(),
  };
}

/** نامِ دکان/پمپ برای فهرستی از (بخش، شناسه). */
async function tenantNames(pairs) {
  const out = new Map();
  for (const app of APPS) {
    const ids = [...new Set(pairs.filter((p) => p.app === app).map((p) => p.tenantId))];
    if (!ids.length) continue;
    const T = tenancy.byApp(app);
    for (const r of await many(`SELECT id, name FROM ${T.tenantTable} WHERE id = ANY($1::text[])`, [ids])) {
      out.set(`${app}|${r.id}`, r.name || '');
    }
  }
  return out;
}

async function planTitles() {
  const out = new Map();
  for (const r of await many('SELECT app, code, title FROM plans')) out.set(`${r.app}|${r.code}`, r.title || r.code);
  return out;
}

/** تفکیکِ بازه به دکان (`by=shop`) یا پلن (`by=plan`) — صفحه‌بندیِ سمتِ سرور. */
async function breakdown(q = {}) {
  const w = windowOf(q);
  const app = appFilter(q.app);
  const by = v.oneOf(q.by, ['shop', 'plan'], { field: 'تفکیک', def: 'shop' });
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max: LIMITS.pageMax, def: 50 });
  const offset = v.integer(q.offset, { field: 'آغاز', min: 0, max: 1e6, def: 0 });
  const keyCol = by === BY_TENANT ? 'l.tenant_id' : 'l.plan';
  const base = `
    WITH l0 AS (${LEDGER_SQL}),
    l AS (
      SELECT l0.*, COALESCE(CASE WHEN l0.app = 'shop' THEN s.plan ELSE st.plan END, '') AS plan
        FROM l0
        LEFT JOIN subscriptions s ON l0.app = 'shop' AND s.id = l0.subscription_id
        LEFT JOIN station_subscriptions st ON l0.app = 'pump' AND st.id = l0.subscription_id)`;
  const [rows, count] = await Promise.all([
    many(`${base}
      SELECT l.app, ${keyCol} AS key, l.currency,
             COALESCE(SUM(l.amount) FILTER (WHERE l.src = 'payment'), 0)::bigint AS payments,
             COALESCE(SUM(l.amount) FILTER (WHERE l.src <> 'payment'), 0)::bigint AS adjustments,
             SUM(l.amount)::bigint AS net, (COUNT(*) FILTER (WHERE l.src = 'payment'))::int AS count
        FROM l GROUP BY l.app, ${keyCol}, l.currency
       ORDER BY net DESC, l.app, ${keyCol}, l.currency
       LIMIT $4 OFFSET $5`, [w.from, w.to, app, limit, offset]),
    one(`${base} SELECT COUNT(*)::int AS n FROM (SELECT 1 FROM l GROUP BY l.app, ${keyCol}, l.currency) g`, [w.from, w.to, app]),
  ]);
  const names = by === BY_TENANT ? await tenantNames(rows.map((r) => ({ app: r.app, tenantId: r.key }))) : await planTitles();
  return {
    window: w, app: app || 'all', by, limit, offset, total: N(count?.n),
    rows: rows.map((r) => ({
      app: r.app, key: r.key, name: names.get(`${r.app}|${r.key}`) ?? '', currency: r.currency,
      payments: N(r.payments), adjustments: N(r.adjustments), net: N(r.net), count: N(r.count),
    })),
    checkedAt: now(),
  };
}

/* ═══════════════════════════════ پرداخت‌ها (دفتر) ═══════════════════════════════ */

/** نشانگرِ صفحه: [زمان، شناسه] به base64url. */
function cursorOf(raw) {
  if (raw === undefined || raw === null || raw === '') return null;
  try {
    const arr = JSON.parse(Buffer.from(String(raw), 'base64url').toString('utf8'));
    if (Array.isArray(arr) && arr.length === 2 && Number.isSafeInteger(arr[0]) && /^[A-Za-z0-9_.:-]{1,80}$/.test(String(arr[1]))) {
      return { at: arr[0], id: String(arr[1]) };
    }
  } catch { /* پایین */ }
  throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
}
const cursorFor = (at, id) => Buffer.from(JSON.stringify([Number(at), String(id)])).toString('base64url');
const likeOf = (s) => `%${String(s).replace(/[\\%_]/g, (c) => `\\${c}`)}%`;

const PAYMENT_SELECT = `
  SELECT p.*, COALESCE(s.name, st.name) AS tenant_name,
         COALESCE(sub.plan, ssub.plan, '') AS plan, COALESCE(sub.currency, ssub.currency) AS sub_currency,
         COALESCE(adj.n, 0)::int AS adj_count, COALESCE(adj.total, 0)::bigint AS adj_total,
         COALESCE(adj.refunded, 0)::bigint AS refunded, COALESCE(adj.reversed, false) AS reversed,
         COALESCE(adj.refs, ARRAY[]::text[]) AS refs, COALESCE(rv.n, 0)::int AS revisions
    FROM sub_payments p
    LEFT JOIN shops s ON p.app = 'shop' AND s.id = p.tenant_id
    LEFT JOIN stations st ON p.app = 'pump' AND st.id = p.tenant_id
    LEFT JOIN subscriptions sub ON p.app = 'shop' AND sub.id = p.subscription_id
    LEFT JOIN station_subscriptions ssub ON p.app = 'pump' AND ssub.id = p.subscription_id
    LEFT JOIN LATERAL (
      SELECT COUNT(*) AS n, SUM(a.amount) AS total, -SUM(a.amount) FILTER (WHERE a.kind = 'refund') AS refunded,
             bool_or(a.kind = 'reversal') AS reversed, array_agg(a.reference ORDER BY a.created_at) FILTER (WHERE a.reference <> '') AS refs
        FROM finance_adjustments a WHERE a.payment_id = p.id) adj ON true
    LEFT JOIN LATERAL (SELECT COUNT(*) AS n FROM sub_payment_revisions r WHERE r.payment_id = p.id) rv ON true`;

function shapePayment(r) {
  const amount = N(r.amount);
  const adj = N(r.adj_total);
  return {
    id: r.id, app: r.app, tenantId: r.tenant_id, tenantName: r.tenant_name ?? null,
    subscriptionId: r.subscription_id || '', plan: r.plan || '', subscriptionCurrency: r.sub_currency || null,
    amount, currency: r.currency, method: r.method, receiptNo: r.receipt_no || '', note: r.note || '',
    paidAt: N(r.paid_at), createdBy: r.created_by || '', createdAt: N(r.created_at), deleted: r.deleted === true,
    adjustments: { count: N(r.adj_count), total: adj, refunded: N(r.refunded), reversed: r.reversed === true },
    net: amount + adj, references: Array.isArray(r.refs) ? r.refs : [], revisions: N(r.revisions),
  };
}

/** شرط‌های دفترِ پرداخت از پرس‌وجو ⇒ { where, params } (پارامترها از ‎$1‎). */
function paymentFilters(q = {}) {
  const where = [];
  const params = [];
  const add = (sql, val) => { params.push(val); where.push(sql.replaceAll('$?', `$${params.length}`)); };
  const app = appFilter(q.app);
  if (app) add('p.app = $?', app);
  const shopId = v.id(q.shopId ?? q.tenantId, { field: 'شناسهٔ دکان', required: false });
  if (shopId) add('p.tenant_id = $?', shopId);
  const from = v.timestamp(q.from);
  const to = v.timestamp(q.to);
  if (q.fromDate || q.toDate) {
    const w = windowOf({ fromDate: q.fromDate, toDate: q.toDate || localYmd(now()), group: 'day' });
    add('p.paid_at >= $?', w.from);
    add('p.paid_at < $?', w.to);
  } else {
    if (from !== null) add('p.paid_at >= $?', from);
    if (to !== null) add('p.paid_at < $?', to);
  }
  if (q.method) add('p.method = $?', v.oneOf(q.method, METHODS, { field: 'روش' }));
  if (q.currency) add('p.currency = $?', v.oneOf(q.currency, CURRENCIES, { field: 'ارز' }));
  const status = v.oneOf(q.status, ['all', 'active', 'deleted'], { field: 'وضعیت', def: 'all' });
  if (status === 'active') where.push('p.deleted = false');
  if (status === 'deleted') where.push('p.deleted = true');
  const text = v.text(q.q, { max: 80 }).trim();
  if (text) {
    params.push(likeOf(text));
    const like = `$${params.length}`;
    params.push(text);
    const raw = `$${params.length}`;
    where.push(`(p.receipt_no ILIKE ${like} OR COALESCE(s.name, st.name, '') ILIKE ${like} OR p.note ILIKE ${like}
      OR p.id = ${raw} OR p.tenant_id = ${raw}
      OR EXISTS (SELECT 1 FROM finance_adjustments a2 WHERE a2.payment_id = p.id AND (a2.reference ILIKE ${like} OR a2.doc_no = ${raw})))`);
  }
  return { where, params };
}

/** دفترِ پرداخت‌ها با نشانگر (paid_at، id) نزولی. */
async function payments(q = {}, { max = LIMITS.pageMax } = {}) {
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max, def: Math.min(50, max) });
  const { where, params } = paymentFilters(q);
  const c = cursorOf(q.cursor);
  if (c) {
    params.push(c.at, c.id);
    where.push(`(p.paid_at, p.id) < ($${params.length - 1}::bigint, $${params.length}::text)`);
  }
  params.push(limit + 1);
  const rows = await many(
    `${PAYMENT_SELECT} ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
     ORDER BY p.paid_at DESC, p.id DESC LIMIT $${params.length}`, params
  );
  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  return {
    payments: page.map(shapePayment),
    nextCursor: rows.length > limit && last ? cursorFor(last.paid_at, last.id) : null,
    limit, checkedAt: now(),
  };
}

function shapeAdjustment(r) {
  return {
    id: r.id, docNo: r.doc_no, kind: r.kind, paymentId: r.payment_id, app: r.app, tenantId: r.tenant_id,
    tenantName: r.tenant_name ?? null, subscriptionId: r.subscription_id || '', amount: N(r.amount), currency: r.currency,
    netBefore: N(r.net_before), netAfter: N(r.net_after), reference: r.reference || '', reason: r.reason,
    createdBy: r.created_by, actor: json(r.created_actor), reauth: r.reauth || '', createdAt: N(r.created_at),
    ...(r.receipt_no !== undefined ? { receiptNo: r.receipt_no || '' } : {}),
  };
}

const REVISION_FIELDS = ['amount', 'currency', 'method', 'receipt_no', 'note', 'paid_at', 'subscription_id', 'tenant_id', 'app', 'deleted'];
function shapeRevision(r) {
  const before = json(r.before) || {};
  const after = json(r.after);
  const changed = after ? REVISION_FIELDS.filter((k) => JSON.stringify(before[k]) !== JSON.stringify(after[k])) : REVISION_FIELDS;
  const pick = (o) => (o ? Object.fromEntries(REVISION_FIELDS.map((k) => [k, o[k] ?? null])) : null);
  return { id: N(r.id), op: r.op, changedAt: N(r.changed_at), changed, before: pick(before), after: pick(after) };
}

function shapeAudit(r) {
  const d = json(r.detail) || {};
  return {
    id: r.id, at: N(r.created_at), action: r.action, actorType: r.actor_type, adminId: r.user_id || '',
    actor: d.actor || null, targetType: r.target_type || '', targetId: r.target_id || '', shopId: r.shop_id || '', detail: d,
  };
}

/** یک پرداخت با همهٔ سندها، بازنگری‌های راهِ پیشین و ردیف‌های سابقه — «تاریخچهٔ یک سند». */
async function paymentDetail(id) {
  const row = await one(`${PAYMENT_SELECT} WHERE p.id = $1`, [id]);
  if (!row) throw notFound('پرداخت پیدا نشد', 'payment_not_found');
  const [adjs, revs, logs] = await Promise.all([
    many('SELECT * FROM finance_adjustments WHERE payment_id = $1 ORDER BY created_at, id', [id]),
    many('SELECT * FROM sub_payment_revisions WHERE payment_id = $1 ORDER BY id', [id]),
    many(`SELECT * FROM audit_logs WHERE target_id = $1 AND (target_type = 'payment' OR action LIKE 'admin.payment_%')
           ORDER BY created_at, id LIMIT 200`, [id]),
  ]);
  return {
    payment: shapePayment(row), adjustments: adjs.map(shapeAdjustment), revisions: revs.map(shapeRevision),
    audit: logs.map(shapeAudit), checkedAt: now(),
  };
}

/** سندهای اصلاحی با نشانگر (created_at، id) نزولی. */
async function adjustments(q = {}, { max = LIMITS.pageMax } = {}) {
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max, def: Math.min(50, max) });
  const where = [];
  const params = [];
  const add = (sql, val) => { params.push(val); where.push(sql.replaceAll('$?', `$${params.length}`)); };
  if (q.kind) add('a.kind = $?', v.oneOf(q.kind, KINDS, { field: 'نوعِ سند' }));
  const app = appFilter(q.app);
  if (app) add('a.app = $?', app);
  const shopId = v.id(q.shopId ?? q.tenantId, { field: 'شناسهٔ دکان', required: false });
  if (shopId) add('a.tenant_id = $?', shopId);
  const paymentId = v.id(q.paymentId, { field: 'شناسهٔ پرداخت', required: false });
  if (paymentId) add('a.payment_id = $?', paymentId);
  const from = v.timestamp(q.from);
  const to = v.timestamp(q.to);
  if (from !== null) add('a.created_at >= $?', from);
  if (to !== null) add('a.created_at < $?', to);
  const text = v.text(q.q, { max: 80 }).trim();
  if (text) {
    params.push(likeOf(text));
    const like = `$${params.length}`;
    params.push(text);
    where.push(`(a.reference ILIKE ${like} OR a.reason ILIKE ${like} OR a.doc_no = $${params.length} OR a.payment_id = $${params.length})`);
  }
  const c = cursorOf(q.cursor);
  if (c) {
    params.push(c.at, c.id);
    where.push(`(a.created_at, a.id) < ($${params.length - 1}::bigint, $${params.length}::text)`);
  }
  params.push(limit + 1);
  const rows = await many(
    `SELECT a.*, COALESCE(s.name, st.name) AS tenant_name, p.receipt_no
       FROM finance_adjustments a
       LEFT JOIN sub_payments p ON p.id = a.payment_id
       LEFT JOIN shops s ON a.app = 'shop' AND s.id = a.tenant_id
       LEFT JOIN stations st ON a.app = 'pump' AND st.id = a.tenant_id
      ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
      ORDER BY a.created_at DESC, a.id DESC LIMIT $${params.length}`, params
  );
  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  //  جمعِ همین پالایش (نه فقط این صفحه) به تفکیکِ ارز — از همان ردیف‌ها
  const sumParams = params.slice(0, params.length - 1 - (c ? 2 : 0));
  const sumWhere = c ? where.slice(0, -1) : where;
  const sums = await many(
    `SELECT a.currency, COUNT(*)::int AS n, COALESCE(SUM(a.amount), 0)::bigint AS total
       FROM finance_adjustments a
       LEFT JOIN shops s ON a.app = 'shop' AND s.id = a.tenant_id
       LEFT JOIN stations st ON a.app = 'pump' AND st.id = a.tenant_id
      ${sumWhere.length ? `WHERE ${sumWhere.join(' AND ')}` : ''} GROUP BY a.currency ORDER BY a.currency`, sumParams
  );
  return {
    adjustments: page.map(shapeAdjustment),
    totals: Object.fromEntries(sums.map((r) => [r.currency, { count: N(r.n), amount: N(r.total) }])),
    nextCursor: rows.length > limit && last ? cursorFor(last.created_at, last.id) : null,
    limit, checkedAt: now(),
  };
}

/* ═══════════════════════════════ سندِ اصلاحی ═══════════════════════════════ */

function requireWriter(req, what = 'این کار') {
  if (!WRITE_ROLES.includes(req.admin?.role)) {
    throw forbidden(`${what} فقط از نقشِ مالی یا مدیرِ ارشد است`, 'role_forbidden');
  }
}

/**
 * احرازِ دوباره برای سندِ اصلاحی (و فقط همین). ⇒ 'login' | 'password' | 'panel'
 *   • آدم با توکنِ خودش: نشستی که در `REAUTH_WINDOW_MIN` (پیش‌فرض ده دقیقه) گذشته ساخته شده، یا `password`
 *     ِ خودش همراهِ همین درخواست (پنج اشتباه در ربع ساعت ⇒ ۴۲۹).
 *   • پلِ پنل با کلیدِ سرویس: پنل خودش رمزِ کاربرش را دوباره سنجیده و زمانش را در `X-Actor-Reauth-At`
 *     می‌گوید — همان اندازه معتبر که کلیدِ سرویس؛ کهنه‌تر از همان پنجره یا در آینده ⇒ رد.
 */
async function assertReauth(req) {
  const win = Math.max(60_000, Number(config.tokens.reauthWindowMs) || 600_000);
  const t = now();
  if (req.service) {
    const at = Number(String(req.headers['x-actor-reauth-at'] || '').trim());
    if (!Number.isSafeInteger(at) || at > t + 60_000 || t - at > win) {
      throw forbidden('سندِ اصلاحی احرازِ دوبارهٔ همین چند دقیقه را می‌خواهد (رمزِ پنل)', 'reauth_required');
    }
    return 'panel';
  }
  const issued = N(req.tokenRow?.issued_at);
  if (issued && t - issued <= win) return 'login';
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  if (!password) throw forbidden('برای سندِ اصلاحی رمزِ خودتان را دوباره بزنید (یا دوباره وارد شوید)', 'reauth_required');
  const fails = await one(
    `SELECT COUNT(*)::int AS n FROM audit_logs WHERE action = 'admin.finance_reauth_failed' AND user_id = $1 AND created_at > $2`,
    [req.admin.id, t - REAUTH_FAIL_WINDOW]
  );
  if (N(fails?.n) >= REAUTH_FAIL_MAX) throw tooMany('رمز چند بار اشتباه بود؛ ربعِ ساعتِ دیگر دوباره امتحان کنید', 'reauth_locked');
  const row = await one('SELECT password_hash FROM admins WHERE id = $1', [req.admin.id]);
  const ok = row && await require('./password').verifyPassword(password, row.password_hash);
  if (!ok) {
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.finance_reauth_failed', targetType: 'admin', targetId: req.admin.id });
    throw forbidden('رمز درست نیست', 'reauth_failed');
  }
  return 'password';
}

/** کلیدِ یکتایی — بدنه بی `password` هش می‌شود (رمز هرگز در هیچ هش/ردیفی نمی‌نشیند). */
function idemOf(req) {
  const { password, ...body } = req.body || {};
  void password;
  const actorId = req.actor?.id || req.admin.id;
  return idempotency.fromRequest({
    get: (h) => req.get(h), headers: req.headers, method: req.method, baseUrl: req.baseUrl, path: req.path, body,
  }, `finance-adjustment:${actorId}`);
}

function parseAdjustment(body = {}) {
  const kind = v.oneOf(body.kind, KINDS, { field: 'نوعِ سند' });
  const paymentId = v.id(body.paymentId, { field: 'شناسهٔ پرداخت' });
  const reason = v.text(body.reason, { max: 300 }).trim();
  if (reason.length < 3) throw badRequest('دلیلِ سندِ اصلاحی را بنویسید (۳ تا ۳۰۰ نویسه)', 'reason_required');
  const reference = v.text(body.reference, { max: 120 }).trim();
  if (reference && !/^[\p{L}\p{N} _./#:-]{1,120}$/u.test(reference)) throw badRequest('شمارهٔ تراکنش فقط حرف، رقم و - _ . / # :', 'bad_reference');
  const raw = body.amount;
  const amount = raw === undefined || raw === null || raw === '' ? null : Number(raw);
  if (amount !== null && (!Number.isSafeInteger(amount) || Math.abs(amount) > 1e9)) throw badRequest('مبلغ عددِ صحیح نیست', 'bad_amount');
  const expected = body.expectedNet === undefined || body.expectedNet === null || body.expectedNet === '' ? null : Number(body.expectedNet);
  if (expected !== null && !Number.isSafeInteger(expected)) throw badRequest('خالصِ مورد انتظار عدد نیست', 'bad_expected_net');
  if (kind === 'correction' && (!amount || amount === 0)) throw badRequest('اصلاحِ مبلغ عددِ غیرِ صفر می‌خواهد (+ یا −)', 'bad_amount');
  if (kind === 'refund' && !(amount > 0)) throw badRequest('مبلغِ بازپرداخت عددِ مثبت است', 'bad_amount');
  if (kind === 'reference' && !reference) throw badRequest('شمارهٔ تراکنشِ بیرونی را بنویسید', 'reference_required');
  if (kind === 'reference' && amount) throw badRequest('بستنِ شمارهٔ تراکنش مبلغ ندارد', 'bad_amount');
  return { kind, paymentId, reason, reference, amount, expected };
}

/**
 * ثبتِ سندِ اصلاحی. ⇒ { adjustment, payment, replayed }
 * ⛔ نقش (مالی/ارشد) ⇒ کلیدِ یکتایی ⇒ احرازِ دوباره ⇒ زیرِ قفلِ ردیفِ پرداخت، خالص هرگز منفی ⇒ سند و ردیفِ سابقه
 *    در **یک** تراکنش. پرداخت خودش دست نمی‌خورد.
 */
async function createAdjustment(req, { ip = '' } = {}) {
  requireWriter(req, 'سندِ اصلاحی');
  const input = parseAdjustment(req.body);
  const idem = idemOf(req);
  if (!idem) throw badRequest('سندِ اصلاحی سرآیندِ Idempotency-Key می‌خواهد', 'idempotency_key_required');
  //  تکرارِ همان کلید پیش از احرازِ دوباره پاسخ می‌گیرد: کارِ تازه‌ای نمی‌کند و رمزِ دوباره نمی‌خواهد
  const prior = await idempotency.find(idem);
  if (prior) return { ...prior.response, replayed: true };
  const reauth = await assertReauth(req);
  const actor = require('./admin-service').actorRecord(req);
  return tx(async (c) => {
    await idempotency.lock(c, idem);
    const again = await idempotency.find(idem, idempotency.viaClient(c));
    if (again) return { ...again.response, replayed: true };
    const pr = (await c.query('SELECT * FROM sub_payments WHERE id = $1 FOR UPDATE', [input.paymentId])).rows[0];
    if (!pr) throw notFound('پرداخت پیدا نشد', 'payment_not_found');
    if (pr.deleted) throw conflict('این پرداخت (از راهِ پیشین) حذف شده است؛ سندِ اصلاحی نمی‌گیرد', 'payment_deleted');
    const sum = (await c.query('SELECT COALESCE(SUM(amount), 0)::bigint AS s FROM finance_adjustments WHERE payment_id = $1', [pr.id])).rows[0];
    const netBefore = N(pr.amount) + N(sum.s);
    if (input.expected !== null && input.expected !== netBefore) {
      const err = conflict('این پرداخت پس از باز شدنِ صفحه عوض شده است؛ دوباره ببینید', 'stale_net');
      err.netNow = netBefore;
      throw err;
    }
    let amount = 0;
    if (input.kind === 'correction') amount = input.amount;
    if (input.kind === 'refund') {
      if (input.amount > netBefore) throw conflict('بازپرداخت از خالصِ این پرداخت بیشتر است', 'refund_exceeds_net');
      amount = -input.amount;
    }
    if (input.kind === 'reversal') {
      if (netBefore <= 0) throw conflict('این پرداخت چیزی برای ابطال ندارد', 'nothing_to_reverse');
      if (input.amount !== null && Math.abs(input.amount) !== netBefore) {
        throw conflict('مبلغِ ابطال باید همهٔ خالصِ همین لحظه باشد', 'stale_net');
      }
      amount = -netBefore;
    }
    if (input.kind === 'reference') {
      const dup = (await c.query('SELECT 1 FROM finance_adjustments WHERE payment_id = $1 AND reference = $2', [pr.id, input.reference])).rows[0];
      if (dup) throw conflict('این شمارهٔ تراکنش پیش‌تر به همین پرداخت بسته شده است', 'reference_exists');
    }
    const netAfter = netBefore + amount;
    if (netAfter < 0) throw conflict('خالصِ پرداخت منفی می‌شد — اصلاح پذیرفته نشد', 'net_negative');
    const t = now();
    const seq = N((await c.query(`SELECT nextval('finance_adjustment_seq') AS n`)).rows[0].n);
    const docNo = `ADJ-${localYmd(t).slice(0, 4)}-${String(seq).padStart(5, '0')}`;
    const ins = await c.query(
      `INSERT INTO finance_adjustments (id, doc_no, kind, payment_id, app, tenant_id, subscription_id, amount, currency,
                                        net_before, net_after, reference, reason, created_by, created_actor, reauth, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15::jsonb,$16,$17) RETURNING *`,
      [newId('fad'), docNo, input.kind, pr.id, pr.app, pr.tenant_id, pr.subscription_id || '', amount, pr.currency,
        netBefore, netAfter, input.reference, input.reason, req.admin.id, JSON.stringify(actor), reauth, t]
    );
    const adj = shapeAdjustment(ins.rows[0]);
    await audit.log({
      shopId: pr.app === tenancy.SHOP.app ? pr.tenant_id : '', actorType: 'admin', userId: req.admin.id,
      action: 'admin.finance_adjustment_created', targetType: 'payment', targetId: pr.id, ip,
      detail: {
        adjustmentId: adj.id, docNo, kind: adj.kind, amount, currency: pr.currency, netBefore, netAfter,
        ...(adj.reference ? { reference: adj.reference } : {}), reason: adj.reason, reauth, app: pr.app, tenantId: pr.tenant_id,
      },
    }, { client: c });
    const out = { adjustment: adj, payment: { id: pr.id, amount: N(pr.amount), currency: pr.currency, netBefore, net: netAfter } };
    await idempotency.save(c, idem, { tenantId: pr.tenant_id, resultId: adj.id, response: out });
    return { ...out, replayed: false };
  });
}

/* ═══════════════════════════════ بدهی‌ها ═══════════════════════════════ */

/**
 * ماندهٔ هر اشتراک: قیمت (خودِ اشتراک، وگرنه قیمتِ پلن در روزِ آغاز — همان قاعدهٔ `/sales/debts`) منهای
 * پرداختِ خالصِ **هم‌ارز** (پرداخت + سندهایش). پرداختِ ارزِ دیگر این‌جا شمرده نمی‌شود و در «مغایرت» می‌آید.
 */
async function balances(app, { onlyPaid = false, shopId = '' } = {}) {
  const T = tenancy.byApp(app);
  const rows = await many(
    `SELECT sub.*, t.name AS tenant_name,
            COALESCE((SELECT SUM(p.amount) FROM sub_payments p
                       WHERE p.app = $1 AND p.subscription_id = sub.id AND p.deleted = false AND p.currency = sub.currency), 0)::bigint AS paid,
            COALESCE((SELECT SUM(a.amount) FROM finance_adjustments a JOIN sub_payments p ON p.id = a.payment_id
                       WHERE a.app = $1 AND a.subscription_id = sub.id AND p.deleted = false AND a.currency = sub.currency), 0)::bigint AS adjusted
       FROM ${T.subsTable} sub JOIN ${T.tenantTable} t ON t.id = sub.${T.tenantKey}
      WHERE ($2::text = '' OR sub.${T.tenantKey} = $2::text)
        ${onlyPaid ? `AND EXISTS (SELECT 1 FROM sub_payments p WHERE p.app = $1 AND p.subscription_id = sub.id AND p.deleted = false)` : ''}
      ORDER BY sub.updated_at DESC LIMIT ${LIMITS.receivablesScan + 1}`,
    [app, shopId]
  );
  const truncated = rows.length > LIMITS.receivablesScan;
  const planCache = new Map();
  const at = now();
  const out = [];
  for (const r of rows.slice(0, LIMITS.receivablesScan)) {
    const state = subs.stateOf(r, at);
    let price = r.price === null || r.price === undefined ? null : N(r.price);
    let priceSource = 'subscription';
    if (price === null) {
      if (!planCache.has(r.plan)) planCache.set(r.plan, await plans.getPlan(r.plan, app));
      const pl = planCache.get(r.plan);
      price = pl ? plans.discountOf(pl, N(r.starts_at)).finalPrice : null;
      priceSource = pl ? 'plan' : 'unknown';
    }
    const paid = N(r.paid) + N(r.adjusted);
    out.push({
      subscriptionId: r.id, app, tenantId: r[T.tenantKey], tenantName: r.tenant_name || '', plan: r.plan, status: state.status,
      startsAt: state.startsAt, endsAt: state.endsAt, price, priceSource, currency: r.currency,
      payments: N(r.paid), adjustments: N(r.adjusted), paid, balance: price === null ? null : price - paid,
    });
  }
  return { rows: out, truncated };
}

async function receivables(q = {}) {
  const app = appFilter(q.app);
  const shopId = v.id(q.shopId ?? q.tenantId, { field: 'شناسهٔ دکان', required: false });
  const limit = v.integer(q.limit, { field: 'اندازهٔ صفحه', min: 1, max: LIMITS.pageMax, def: 50 });
  const offset = v.integer(q.offset, { field: 'آغاز', min: 0, max: 1e6, def: 0 });
  const all = [];
  let truncated = false;
  for (const a of app ? [app] : APPS) {
    const b = await balances(a, { shopId });
    truncated ||= b.truncated;
    all.push(...b.rows.filter((r) => ['active', 'suspended'].includes(r.status) && r.balance !== null && r.balance > 0)
      .map(({ balance, ...r }) => ({ ...r, debt: balance })));
  }
  all.sort((x, y) => y.debt - x.debt || String(x.subscriptionId).localeCompare(String(y.subscriptionId)));
  const totals = {};
  for (const r of all) {
    const t = (totals[r.currency] ||= { count: 0, debt: 0 });
    t.count += 1;
    t.debt += r.debt;
  }
  return {
    app: app || 'all', total: all.length, limit, offset, totals, truncated,
    rows: all.slice(offset, offset + limit),
    rule: 'price − (payments + adjustments) in the subscription currency; active or suspended only',
    checkedAt: now(),
  };
}

module.exports = {
  LIMITS, APPS, METHODS, KINDS, WRITE_ROLES, FIN_ACTIONS, N, cursorOf, cursorFor, shapeAudit, requireWriter, balances,
  summary, breakdown, payments, paymentDetail, adjustments, createAdjustment, receivables,
  //  بازه‌ها (`lib/finance-window.js`) — برای آزمون از همین در هم (⚠️ نه `...spread`: `LIMITS` ِ آن‌جا جای این را می‌گرفت)
  windowOf, bucketsOf, bucketYmd, localYmd,
  dayStart: require('./finance-window').dayStart, persianOf: require('./finance-window').persianOf,
};
