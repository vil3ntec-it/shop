'use strict';
/**
 * تسویهٔ پیک (DLV-11، DLV-12، S40، S57) — از `lib/delivery.js` جدا شد تا زیرِ سقفِ اندازه بماند؛
 * رفتار همان. وابستگی‌های همان پرونده (نقدِ در دستِ پیک، اجرای پرسش، سقفِ
 * هر تسویه) تزریق می‌شوند.
 */
const { one, many, now, tx } = require('../db');
const D = require('./delivery-states');
const orders = require('./orders');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');
const { ID_RE, str, noteText, int, n, hashOf } = require('./delivery-input');
const { courierRowView } = require('./delivery-views');

module.exports = ({ cashStats, viaDb, MAX_SETTLE }) => {
const SETTLE_ROWS = `
  SELECT a.id, a.order_id, a.expected_collect_minor, a.cash_collected_minor, a.fare_minor, a.cash_review,
         a.delivered_at, a.status, a.settlement_id, o.order_no
    FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id`;

function settleRowView(r) {
  const expected = Number(r.expected_collect_minor);
  const collected = Number(r.cash_collected_minor || 0);
  return {
    assignmentId: r.id, orderId: r.order_id, orderNo: Number(r.order_no), deliveredAt: n(r.delivered_at),
    expectedCollectMinor: expected, cashCollectedMinor: collected, collectionDiffMinor: collected - expected,
    fareMinor: Number(r.fare_minor), cashReview: r.cash_review === true,
  };
}

async function courierInShop(run, shopId, courierId) {
  const cr = (await run('SELECT * FROM shop_couriers WHERE shop_id=$1 AND id=$2', [shopId, String(courierId || '')])).rows[0];
  if (!cr) throw notFound('پیک پیدا نشد', 'courier_not_found');
  return cr;
}

/** پیش‌نمایشِ تسویه: مأموریت‌های تحویل‌شده و تسویه‌نشده، به زمانِ سرور. */
async function settlementPreview(shopId, courierId) {
  const cr = await courierInShop(viaDb, shopId, courierId);
  const rows = await many(
    `${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.status='delivered' AND a.settlement_id=''
      ORDER BY a.delivered_at, a.id LIMIT ${MAX_SETTLE}`,
    [shopId, cr.id]
  );
  const t = D.settlementTotals(rows);
  return {
    courier: courierRowView(cr, await cashStats(viaDb, shopId, cr.id)),
    rows: rows.map(settleRowView),
    totals: {
      count: t.count, expectedCashMinor: t.expectedCashMinor, collectedCashMinor: t.collectedCashMinor,
      collectionDiffMinor: t.collectionDiffMinor, fareTotalMinor: t.fareTotalMinor,
    },
    serverTime: now(),
  };
}

function settlementView(s) {
  return {
    id: s.id, settleNo: Number(s.settle_no), courierId: s.courier_id, status: s.status, currency: s.currency,
    assignmentsCount: Number(s.assignments_count), periodFrom: n(s.period_from), periodTo: n(s.period_to),
    expectedCashMinor: Number(s.expected_cash_minor), collectedCashMinor: Number(s.collected_cash_minor),
    collectionDiffMinor: Number(s.collection_diff_minor), fareTotalMinor: Number(s.fare_total_minor),
    farePaidFromCash: s.fare_paid_from_cash === true, handoverDueMinor: Number(s.handover_due_minor),
    handedOverMinor: Number(s.handed_over_minor), receivedMinor: Number(s.received_minor),
    shortageMinor: Number(s.shortage_minor), excessMinor: Number(s.excess_minor),
    reason: s.reason, note: s.note, confirmedBy: s.confirmed_by, createdAt: Number(s.created_at),
  };
}

/**
 * تسویهٔ پایانِ روز (DLV-12): شمارش (received)، مقایسه با بدهیِ پیک،
 * اختلاف با دلیل، تأییدِ مدیر (همین کننده با `delivery.settle`). سندِ بسته.
 * ⛔ هر مأموریت فقط یک‌بار؛ تکرارِ همان درخواست ⇒ همان سند (کلیدِ یکتایی).
 */
async function settle(shopId, courierId, body, actor, key) {
  const b = body && typeof body === 'object' ? body : {};
  if (!key) throw badRequest('کلیدِ یکتاییِ تسویه لازم است', 'idempotency_key_required');
  if (!Array.isArray(b.assignmentIds) || b.assignmentIds.length === 0) throw badRequest('مأموریتی برای تسویه نیامد', 'bad_assignments');
  if (b.assignmentIds.length > MAX_SETTLE) throw badRequest(`حداکثر ${MAX_SETTLE} مأموریت`, 'bad_assignments');
  const ids = [...new Set(b.assignmentIds.map((x) => str(x)))];
  if (ids.length !== b.assignmentIds.length || ids.some((x) => !ID_RE.test(x))) throw badRequest('فهرستِ مأموریت‌ها نامعتبر است', 'bad_assignments');
  const handedOverMinor = int(b.handedOverMinor, 'مبلغِ تحویل‌داده');
  const receivedMinor = int(b.receivedMinor, 'مبلغِ دریافت‌شده');
  if (b.farePaidFromCash !== undefined && typeof b.farePaidFromCash !== 'boolean') throw badRequest('نامعتبر', 'bad_fare_policy');
  const farePaidFromCash = b.farePaidFromCash === true;
  const reason = noteText(b.reason, 200);   //  S117
  const note = noteText(b.note, 300);
  const reqHash = hashOf({ courierId: String(courierId || ''), ids: [...ids].sort(), handedOverMinor, receivedMinor, farePaidFromCash, reason, note });

  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`delivery-settle:${shopId}`]);
    const prev = (await c.query(
      'SELECT * FROM courier_settlements WHERE shop_id=$1 AND client_key=$2', [shopId, key]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== reqHash) throw conflict('این کلیدِ یکتایی پیش‌تر برای تسویهٔ دیگری به کار رفته است', 'idempotency_key_reused');
      const rows = (await c.query(`${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.settlement_id=$2 ORDER BY a.delivered_at, a.id`, [shopId, prev.id])).rows;
      return { settlement: settlementView(prev), rows: rows.map(settleRowView), replayed: true };
    }
    const cr = await courierInShop(run, shopId, courierId);
    const ph = ids.map((_, i) => `$${i + 3}`).join(',');
    //  ترتیبِ قفل «سفارش ⇐ مأموریت» (`delivery.lockOrderOf`): پیش‌تر مأموریت‌ها پیش از سفارش‌ها (در `codIn`) قفل
    //  می‌شدند و رویدادِ دوبارهٔ پیک (صفِ بی‌اینترنتِ گوشی «تحویل شد» را دوباره می‌فرستد) سفارش ⇐ مأموریت ⇒ بن‌بستِ
    //  PostgreSQL (`40P01`، تسویه یا رویداد ۵۰۰). سفارش‌ها به ترتیبِ شناسه، پیش از مأموریت‌ها.
    await c.query(
      `SELECT o.id FROM delivery_assignments a JOIN online_orders o ON o.id = a.order_id AND o.shop_id = a.shop_id
        WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.id IN (${ph}) ORDER BY o.id FOR UPDATE OF o`,
      [shopId, cr.id, ...ids]
    );
    const rows = (await c.query(
      `${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.id IN (${ph}) ORDER BY a.delivered_at, a.id FOR UPDATE OF a`,
      [shopId, cr.id, ...ids]
    )).rows;
    if (rows.length !== ids.length || rows.some((r) => r.status !== 'delivered' || r.settlement_id !== '')) {
      throw conflict('فهرستِ تسویه عوض شده است (مأموریتِ تسویه‌شده یا نامربوط)؛ دوباره پیش‌نمایش بگیرید', 'settlement_changed');
    }
    const t = D.settlementTotals(rows, { handedOverMinor, receivedMinor, farePaidFromCash });
    if ((t.shortageMinor || t.excessMinor || t.collectionDiffMinor) && !reason) {
      throw badRequest('اختلاف هست؛ دلیلش لازم است', 'reason_required');
    }
    const at = now();
    const id = ulid(at);
    const no = Number((await c.query(
      'SELECT COALESCE(MAX(settle_no), 0) + 1 AS n FROM courier_settlements WHERE shop_id=$1', [shopId]
    )).rows[0].n);
    const times = rows.map((r) => Number(r.delivered_at));
    await c.query(
      `INSERT INTO courier_settlements (id, shop_id, courier_id, settle_no, client_key, request_hash, status,
         assignments_count, period_from, period_to, expected_cash_minor, collected_cash_minor, collection_diff_minor,
         fare_total_minor, fare_paid_from_cash, handover_due_minor, handed_over_minor, received_minor,
         shortage_minor, excess_minor, reason, note, confirmed_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,'confirmed',$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21,$22,$23)`,
      [id, shopId, cr.id, no, key, reqHash, t.count, Math.min(...times), Math.max(...times),
        t.expectedCashMinor, t.collectedCashMinor, t.collectionDiffMinor, t.fareTotalMinor, farePaidFromCash,
        t.handoverDueMinor, t.handedOverMinor, t.receivedMinor, t.shortageMinor, t.excessMinor,
        reason, note, actor.userId || '', at]
    );
    await c.query(
      `UPDATE delivery_assignments SET settlement_id=$2, version=version+1, updated_at=$3
        WHERE shop_id=$1 AND id IN (${ids.map((_, i) => `$${i + 4}`).join(',')})`,
      [shopId, id, at, ...ids]
    );
    //  نقدِ «گرفته‌شده توسطِ پیک» اکنون به دکان رسید — ماشینِ جدای COD
    const orderActor = { type: 'user', id: actor.userId || '', role: actor.role || '' };
    for (const r of rows) {
      const o = (await c.query('SELECT cod_status FROM online_orders WHERE id=$1', [r.order_id])).rows[0];
      if (o && o.cod_status === 'collected_by_courier') {
        await orders.codIn(c, shopId, r.order_id, 'settled_to_shop', orderActor, { settlementId: id, settleNo: no });
      }
    }
    const s = (await c.query('SELECT * FROM courier_settlements WHERE id=$1', [id])).rows[0];
    return { settlement: settlementView(s), rows: rows.map(settleRowView), replayed: false };
  });
}

/** گزارشِ تسویه‌ها با جمعِ کل (به زمانِ سرور). */
async function listSettlements(shopId, { courierId, from, to, limit } = {}) {
  const params = [shopId];
  let where = 'shop_id=$1';
  if (courierId) { params.push(String(courierId)); where += ` AND courier_id=$${params.length}`; }
  for (const [v, op] of [[from, '>='], [to, '<']]) {
    if (v === undefined || v === '') continue;
    const x = Number(v);
    if (!Number.isSafeInteger(x)) throw badRequest('بازهٔ زمانی معتبر نیست', 'bad_range');
    params.push(x);
    where += ` AND created_at ${op} $${params.length}`;
  }
  const lim = Math.min(200, Math.max(1, Number.parseInt(limit, 10) || 50));
  const items = await many(`SELECT * FROM courier_settlements WHERE ${where} ORDER BY created_at DESC, id LIMIT ${lim}`, params);
  const sums = await one(
    `SELECT COUNT(*)::int AS n, COALESCE(SUM(assignments_count),0) AS assignments,
            COALESCE(SUM(expected_cash_minor),0) AS expected, COALESCE(SUM(collected_cash_minor),0) AS collected,
            COALESCE(SUM(fare_total_minor),0) AS fares, COALESCE(SUM(handover_due_minor),0) AS due,
            COALESCE(SUM(received_minor),0) AS received, COALESCE(SUM(shortage_minor),0) AS shortage,
            COALESCE(SUM(excess_minor),0) AS excess
       FROM courier_settlements WHERE ${where}`,
    params
  );
  return {
    items: items.map(settlementView),
    totals: {
      settlements: Number(sums.n), assignments: Number(sums.assignments), expectedCashMinor: Number(sums.expected),
      collectedCashMinor: Number(sums.collected), fareTotalMinor: Number(sums.fares),
      handoverDueMinor: Number(sums.due), receivedMinor: Number(sums.received),
      shortageMinor: Number(sums.shortage), excessMinor: Number(sums.excess),
    },
  };
}

  return { SETTLE_ROWS, settleRowView, settlementPreview, settle, listSettlements };
};
