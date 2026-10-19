'use strict';
/**
 * دفترِ بازپرداختِ سفارشِ آنلاین و تطبیقِ آن — v3 §۳۸.۲، §۳۸.۷ (FIN-01).
 *
 * شکافی که این پرونده می‌بندد: «بازپرداختِ جزئی/کامل» تا امروز فقط یک
 * وضعیت بود، بی مبلغ. پس نه دفترِ مالی می‌دانست چه‌قدر برگشته، نه گزارشِ
 * سفارش می‌توانست با آن تطبیق داده شود.
 *
 *   پرچم ── `shop.orders.refunds` در «تنظیماتِ زنده» (scope = دکان یا `all`).
 *           ⛔ پیش‌فرض خاموش: خاموش ⇒ همان رفتارِ پیشین (گذارِ سادهٔ وضعیت).
 *           روشن ⇒ وضعیتِ بازپرداخت فقط از راهِ همین دفتر عوض می‌شود.
 *   ثبت ─── `record`: با کلیدِ یکتایی، در یک تراکنش و زیرِ قفلِ ردیفِ سفارش:
 *           ردیفِ دفتر + رویدادِ `payment` + (اگر لازم بود) گذارِ وضعیت با
 *           همان `order-states.check` و رویدادِ `status`.
 *             return_requested = «بازپرداخت در انتظار»؛ مبلغ‌ها جمع می‌شوند.
 *             جمع = کلِ سفارش        ⇒ refunded_full (خودکار)
 *             `final` و جمع < کل      ⇒ refunded_partial
 *   تطبیق ── `issueOf` (خالص) و `report`: جمعِ دریافتی، بازپرداخت و خالص در
 *           بازه، و صفِ اختلاف‌ها (وضعیت و دفتر با هم نمی‌خوانند).
 *
 * ⛔ ردیفِ دفتر هرگز ویرایش یا پاک نمی‌شود؛ هیچ مسیری برای آن نیست.
 * ⛔ مبلغ عددِ صحیح به واحدِ کوچک؛ از کلِ سفارش بیشتر برنمی‌گردد.
 * ⛔ سفارشِ آزمایشی در جمع‌های گزارش شمرده نمی‌شود.
 */
const db = require('../db');
const { many, now, tx } = db;
const S = require('./order-states');
const { ulid } = require('./ulid');
const { hashOf } = require('./orders');
const { can } = require('./permissions');
const { badRequest, forbidden, notFound, conflict, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.refunds';
/** «بازپرداخت در انتظار» — تنها وضعیتی که ردیفِ تازه می‌پذیرد. */
const OPEN_STATUS = 'return_requested';
const CLOSED = Object.freeze(['refunded_partial', 'refunded_full']);
const METHODS = Object.freeze(['cash']);
const MAX_AMOUNT_MINOR = 100_000_000_000;
const MAX_WINDOW_MS = 92 * 24 * 60 * 60 * 1000;
const MAX_ISSUES = 100;
const DAY_MS = 24 * 60 * 60 * 1000;

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** همان قاعدهٔ `orders.flagOf`: مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است. */
async function flagOf(shopId) {
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG_KEY, 'all', String(shopId)]
  );
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

const str = (v) => (typeof v === 'string' ? v.trim() : '');

function parse(body) {
  const b = body && typeof body === 'object' ? body : {};
  const amountMinor = b.amountMinor;
  if (typeof amountMinor !== 'number' || !Number.isSafeInteger(amountMinor)) {
    throw badRequest('مبلغِ بازپرداخت باید عددِ صحیح (واحدِ کوچک) باشد', 'bad_amount');
  }
  if (amountMinor < 1 || amountMinor > MAX_AMOUNT_MINOR) throw badRequest('مبلغِ بازپرداخت در بازهٔ مجاز نیست', 'bad_amount');
  const { stripControl } = require('./validate');
  const reason = stripControl(str(b.reason)).slice(0, 300);
  if (!reason) throw badRequest('دلیلِ بازپرداخت لازم است', 'reason_required');
  const method = str(b.method) || 'cash';
  if (!METHODS.includes(method)) throw badRequest('فعلاً فقط بازپرداختِ نقدی', 'bad_method');
  if (b.final !== undefined && typeof b.final !== 'boolean') throw badRequest('final باید true یا false باشد', 'bad_final');
  return { amountMinor, reason, method, final: b.final === true };
}

/**
 * آیا وضعیتِ سفارش با جمعِ دفترش می‌خواند؟ خالص — برای گزارش و آزمون.
 * @returns {string|null} کدِ اختلاف یا null
 */
function issueOf(status, totalMinor, refundedMinor) {
  const total = Number(totalMinor);
  const refunded = Number(refundedMinor);
  if (refunded > total) return 'refund_exceeds_total';
  if (status === 'refunded_full') {
    if (refunded === 0) return 'refund_without_ledger';
    return refunded === total ? null : 'refund_full_mismatch';
  }
  if (status === 'refunded_partial') {
    if (refunded === 0) return 'refund_without_ledger';
    return refunded < total ? null : 'refund_partial_mismatch';
  }
  if (status === OPEN_STATUS) return refunded < total || total === 0 ? null : 'refund_not_closed';
  return refunded > 0 ? 'refund_on_wrong_status' : null;
}

function entryOut(r) {
  return {
    seq: Number(r.seq), amountMinor: Number(r.amount_minor), currency: r.currency, method: r.method,
    reason: r.reason, final: r.final === true, actorId: r.actor_id, at: Number(r.created_at),
  };
}

/** خلاصهٔ دفترِ یک سفارش — کنارِ جزئیاتِ سفارش. */
async function summaryOf(run, shopId, orderId) {
  const order = (await run('SELECT status, total_minor, currency FROM online_orders WHERE shop_id=$1 AND id=$2',
    [shopId, orderId])).rows[0];
  if (!order) return null;
  const rows = (await run('SELECT * FROM online_order_refunds WHERE shop_id=$1 AND order_id=$2 ORDER BY seq',
    [shopId, orderId])).rows;
  const totalMinor = Number(order.total_minor);
  const refundedMinor = rows.reduce((s, r) => s + Number(r.amount_minor), 0);
  return {
    currency: order.currency,
    totalMinor,
    refundedMinor,
    remainingMinor: Math.max(0, totalMinor - refundedMinor),
    netMinor: totalMinor - refundedMinor,
    issue: issueOf(order.status, totalMinor, refundedMinor),
    entries: rows.map(entryOut),
  };
}

const viaDb = (text, params) => db.query(text, params);

async function summary(shopId, orderId) {
  return summaryOf(viaDb, shopId, String(orderId || ''));
}

const CHECK_STATUS = { permission_denied: 403, system_only: 403 };

/**
 * ثبتِ یک بازپرداخت.
 * @param {{key:string, body:object, actor:{type:string,id?:string,role?:string}}} input
 * @returns {{replayed:boolean, from:string, to:string, entry:object}}
 */
async function record(shopId, orderId, { key, body, actor }) {
  const input = parse(body);
  const hash = hashOf(body);
  if (actor.type !== 'system' && !can(actor.role, 'orders.refund')) {
    throw forbidden('این کار در حدِ دسترسیِ شما نیست', 'permission_denied');
  }

  return tx(async (c) => {
    const row = (await c.query(
      'SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(orderId || '')]
    )).rows[0];
    if (!row) throw notFound('سفارش پیدا نشد', 'order_not_found');

    const prev = (await c.query(
      'SELECT * FROM online_order_refunds WHERE order_id=$1 AND client_key=$2', [row.id, key]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== hash) {
        throw conflict('این کلیدِ یکتایی پیش‌تر برای بازپرداختِ دیگری به کار رفته است', 'idempotency_key_reused');
      }
      return { replayed: true, from: row.status, to: row.status, entry: entryOut(prev) };
    }

    if (CLOSED.includes(row.status)) throw conflict('بازپرداختِ این سفارش بسته شده است', 'refund_closed');
    if (row.status !== OPEN_STATUS) {
      throw conflict('بازپرداخت فقط پس از «درخواستِ برگشت» ثبت می‌شود', 'not_refundable');
    }

    const totalMinor = Number(row.total_minor);
    const sums = (await c.query(
      'SELECT COALESCE(SUM(amount_minor), 0) AS s, COALESCE(MAX(seq), 0) AS n FROM online_order_refunds WHERE order_id=$1',
      [row.id]
    )).rows[0];
    const refundedBefore = Number(sums.s);
    const remaining = totalMinor - refundedBefore;
    if (input.amountMinor > remaining) {
      const err = new ApiError(409, 'refund_exceeds_remaining', 'مبلغِ بازپرداخت از ماندهٔ قابلِ برگشت بیشتر است');
      err.remainingMinor = remaining;
      throw err;
    }
    const refundedAfter = refundedBefore + input.amountMinor;
    let to = row.status;
    if (refundedAfter === totalMinor) to = 'refunded_full';
    else if (input.final) to = 'refunded_partial';
    if (to !== row.status) {
      const verdict = S.check(row, to, { actor });
      if (!verdict.ok) throw new ApiError(CHECK_STATUS[verdict.code] || 409, verdict.code, 'این گذار مجاز نیست');
    }

    const at = now();
    const seq = Number(sums.n) + 1;
    const entryId = ulid(at);
    await c.query(
      `INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor,
         currency, method, reason, final, actor_id, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
      [entryId, row.id, shopId, seq, key, hash, input.amountMinor, row.currency, input.method, input.reason,
        input.final, actor.id || '', at]
    );

    let evSeq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [row.id]
    )).rows[0].n);
    const event = (kind, toStatus, note, detail) => c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,'',$10,$11::jsonb,$12)`,
      [ulid(at), row.id, shopId, evSeq++, kind, row.status, toStatus, actor.type, actor.id || '',
        note, JSON.stringify(detail), at]
    );
    await event('payment', row.status, input.reason.slice(0, 300), {
      refund: { seq, amountMinor: input.amountMinor, method: input.method, final: input.final },
      refundedMinor: refundedAfter, remainingMinor: totalMinor - refundedAfter,
    });
    if (to !== row.status) await event('status', to, '', { refundedMinor: refundedAfter });

    //  نسخه و زمان همیشه جلو می‌روند تا شمارش‌ها و `rev`ِ خلاصه تغییر را ببینند
    await c.query(
      'UPDATE online_orders SET status=$3, status_reason=$4, version=version+1, updated_at=$5 WHERE shop_id=$1 AND id=$2',
      [shopId, row.id, to, to === row.status ? row.status_reason : '', at]
    );
    //  ORD-20: همان قلّابِ `orders.applyTransition` — بازپرداختِ کامل ⇒ سندِ برگشتِ فاکتورِ W
    //  در دفترِ دکان، در همین تراکنش (بی فاکتورِ W ⇒ هیچ)
    if (to !== row.status) {
      await require('./order-ledger').onOrderStatus(c, shopId, { id: row.id, from: row.status, to }, actor, at);
    }
    const entry = (await c.query('SELECT * FROM online_order_refunds WHERE id=$1', [entryId])).rows[0];
    return { replayed: false, from: row.status, to, entry: entryOut(entry) };
  });
}

// ════════════════════════════════════════════════════════════════════
//  تطبیق
// ════════════════════════════════════════════════════════════════════

function windowOf({ from, to } = {}, at = now()) {
  const num = (v, field) => {
    if (v === undefined || v === null || v === '') return null;
    const n = Number(v);
    if (!Number.isSafeInteger(n) || n < 0) throw badRequest(`${field} معتبر نیست`, 'bad_window');
    return n;
  };
  const end = num(to, 'to') ?? at;
  const start = num(from, 'from') ?? end - DAY_MS;
  if (start >= end) throw badRequest('آغازِ بازه باید پیش از پایانش باشد', 'bad_window');
  if (end - start > MAX_WINDOW_MS) throw badRequest('بازه حداکثر ۹۲ روز', 'bad_window');
  return { from: start, to: end };
}

/**
 * گزارشِ تطبیقِ دکان:
 *   collected ── سفارش‌هایی که در بازه «تحویل‌شده» یا «برده‌شده» شدند (پول گرفته شد)
 *   refunds ──── ردیف‌های دفتر در بازه
 *   net ──────── collected − refunds
 *   issues ───── صفِ اختلاف‌ها (همهٔ زمان‌ها، تازه‌ترین اول، حداکثر ۱۰۰)
 * سفارشِ آزمایشی در جمع‌ها نیست؛ در صفِ اختلاف با `isTest` هست.
 */
async function report(shopId, query = {}) {
  const w = windowOf(query);
  const collected = (await db.query(
    `SELECT COUNT(*)::int AS n, COALESCE(SUM(o.total_minor), 0) AS s
       FROM online_order_events e JOIN online_orders o ON o.id = e.order_id
      WHERE e.shop_id = $1 AND e.kind = 'status' AND e.to_status IN ('delivered','picked_up')
        AND e.created_at >= $2 AND e.created_at < $3 AND o.is_test = false`,
    [shopId, w.from, w.to]
  )).rows[0];
  const refunds = (await db.query(
    `SELECT COUNT(*)::int AS n, COALESCE(SUM(r.amount_minor), 0) AS s
       FROM online_order_refunds r JOIN online_orders o ON o.id = r.order_id
      WHERE r.shop_id = $1 AND r.created_at >= $2 AND r.created_at < $3 AND o.is_test = false`,
    [shopId, w.from, w.to]
  )).rows[0];
  const rows = (await db.query(
    `SELECT o.id, o.order_no, o.status, o.total_minor, o.is_test, o.updated_at, COALESCE(r.s, 0) AS refunded
       FROM online_orders o
       LEFT JOIN (SELECT order_id, SUM(amount_minor) AS s FROM online_order_refunds
                   WHERE shop_id = $1 GROUP BY order_id) r ON r.order_id = o.id
      WHERE o.shop_id = $1
        AND (o.status IN ('return_requested','refunded_partial','refunded_full') OR r.s IS NOT NULL)
      ORDER BY o.updated_at DESC, o.id DESC
      LIMIT 1000`,
    [shopId]
  )).rows;
  const issues = [];
  for (const r of rows) {
    const code = issueOf(r.status, r.total_minor, r.refunded);
    if (!code) continue;
    issues.push({
      orderId: r.id, orderNo: Number(r.order_no), status: r.status, code, isTest: r.is_test === true,
      totalMinor: Number(r.total_minor), refundedMinor: Number(r.refunded), updatedAt: Number(r.updated_at),
    });
    if (issues.length >= MAX_ISSUES) break;
  }
  const collectedMinor = Number(collected.s);
  const refundedMinor = Number(refunds.s);
  return {
    window: w,
    currency: 'AFN',
    collected: { count: Number(collected.n), totalMinor: collectedMinor },
    refunds: { count: Number(refunds.n), totalMinor: refundedMinor },
    netMinor: collectedMinor - refundedMinor,
    issues,
    serverTime: now(),
  };
}

module.exports = { FLAG_KEY, flagOf, parse, issueOf, summary, record, report };
