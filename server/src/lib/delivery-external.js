'use strict';
/**
 * شرکتِ پیکِ بیرونی (DLV-19) — فهرست و صورت‌حساب از خودِ مأموریت‌ها. جدا از `lib/delivery.js`، بی تغییرِ رفتار.
 */
const { many, now } = require('../db');
const { badRequest } = require('../middleware/errors');
const { text, n } = require('./delivery-input');

const MAX_STATEMENT = 1000;

/**
 * شرکت‌هایی که این دکان تا امروز به آن‌ها سفارش سپرده — از
 * `delivery_assignments` (روشِ external)، بی جدولِ تازه. کرایهٔ آخرین
 * سپردن «کرایهٔ هر سفارشِ» پیش‌فرضِ فرمِ سپردن است.
 */
async function externalCompanies(shopId) {
  const rows = await many(
    `SELECT external_name AS name,
            COUNT(*)::int AS assignments,
            COUNT(*) FILTER (WHERE status IN ('requested','assigned','picked_up','moving','arrived','failed'))::int AS live,
            COUNT(*) FILTER (WHERE status = 'delivered')::int AS delivered,
            (ARRAY_AGG(fare_minor ORDER BY created_at DESC, id DESC))[1] AS last_fare,
            MAX(created_at) AS last_used_at
       FROM delivery_assignments
      WHERE shop_id=$1 AND mode='external' AND external_name <> ''
      GROUP BY external_name
      ORDER BY MAX(created_at) DESC, external_name
      LIMIT 100`,
    [shopId]
  );
  return {
    items: rows.map((r) => ({
      name: r.name, assignments: Number(r.assignments), live: Number(r.live), delivered: Number(r.delivered),
      lastFareMinor: Number(r.last_fare || 0), lastUsedAt: Number(r.last_used_at),
    })),
  };
}

/**
 * صورت‌حسابِ یک شرکت در یک بازه (زمانِ سپردن، به زمانِ سرور) — برای
 * تطبیق با صورت‌حسابی که شرکت می‌فرستد. «پس‌گرفته» (withdrawn) هرگز به
 * شرکت نرسیده و نمی‌آید؛ ناموفق/برگشت می‌آید تا اگر شرکت آن را هم حساب
 * کرده بود، دیده شود.
 */
async function externalStatement(shopId, { name, from, to } = {}) {
  const company = text(name, 80);
  if (!company) throw badRequest('نامِ شرکتِ پیک لازم است', 'external_name_required');
  const params = [shopId, company];
  let where = `a.shop_id=$1 AND a.mode='external' AND a.external_name=$2 AND a.status <> 'withdrawn'`;
  for (const [v, op] of [[from, '>='], [to, '<']]) {
    if (v === undefined || v === '') continue;
    const x = Number(v);
    if (!Number.isSafeInteger(x) || x < 0) throw badRequest('بازهٔ زمانی معتبر نیست', 'bad_range');
    params.push(x);
    where += ` AND a.created_at ${op} $${params.length}`;
  }
  const rows = await many(
    `SELECT a.id, a.order_id, a.external_ref, a.status, a.fare_minor, a.expected_collect_minor,
            a.cash_collected_minor, a.delivered_at, a.created_at, o.order_no
       FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id
      WHERE ${where} ORDER BY a.created_at, a.id LIMIT ${MAX_STATEMENT + 1}`,
    params
  );
  const truncated = rows.length > MAX_STATEMENT;
  const list = rows.slice(0, MAX_STATEMENT).map((r) => ({
    assignmentId: r.id, orderId: r.order_id, orderNo: Number(r.order_no), externalRef: r.external_ref || '',
    status: r.status, fareMinor: Number(r.fare_minor), expectedCollectMinor: Number(r.expected_collect_minor),
    cashCollectedMinor: n(r.cash_collected_minor), deliveredAt: n(r.delivered_at), createdAt: Number(r.created_at),
  }));
  const delivered = list.filter((r) => r.status === 'delivered');
  return {
    name: company,
    rows: list,
    truncated,
    totals: {
      count: list.length,
      delivered: delivered.length,
      deliveredFareMinor: delivered.reduce((s, r) => s + r.fareMinor, 0),
      deliveredCashMinor: delivered.reduce((s, r) => s + (r.cashCollectedMinor || 0), 0),
    },
    serverTime: now(),
  };
}

module.exports = { externalCompanies, externalStatement };
