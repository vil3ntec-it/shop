'use strict';
/**
 * گزارشِ نقدِ پیک‌ها و تسویه (RPT-15) — جدا از `delivery.js` تا آن پرونده بزرگ‌تر نشود؛
 * فقط خواندن از همان جدول‌های `delivery_assignments`، `courier_settlements` و `shop_couriers`.
 */
const { many } = require('../db');
const { badRequest, ApiError } = require('../middleware/errors');

/** بیشترین پهنای بازهٔ گزارشِ نقدِ پیک‌ها (پرس‌وجوی محدود) */
const CASH_REPORT_MAX_SPAN_MS = 400 * 86_400_000;

/**
 * گزارشِ نقدِ پیک‌ها و تسویه (RPT-15) در بازهٔ [from, to) ِ زمانِ **سرور** — فقط خواندن.
 * برای هر پیک: نقدی که در بازه هنگامِ تحویل گرفت (مأموریتِ تحویل‌شده با `delivered_at` در بازه)،
 * بخشی از همان که هنوز تسویه نشده، و سندهای تسویهٔ همان بازه (`created_at`) با جمعِ هر ستون؛ و
 * «در دست» ِ همین لحظه (همان `cashHeldMinor` ِ فهرستِ پیک‌ها، مستقل از بازه).
 * تطبیق: جمعِ ردیف‌ها = جمعِ کل؛ ستون‌های تسویه = جمعِ `listSettlements` با همان بازه.
 * بازه اجباری، to > from و پهنا حداکثر ۴۰۰ روز (۴۰۰ `bad_range`)؛ هر دو پرس‌وجو GROUP BY روی همان دکان.
 */
async function cashReport(shopId, { from, to } = {}) {
  const f = from === undefined || from === '' ? NaN : Number(from);
  const t = to === undefined || to === '' ? NaN : Number(to);
  if (!Number.isSafeInteger(f) || !Number.isSafeInteger(t) || t <= f || t - f > CASH_REPORT_MAX_SPAN_MS) {
    throw badRequest('بازهٔ زمانی معتبر نیست', 'bad_range');
  }
  const couriers = await many('SELECT id, name FROM shop_couriers WHERE shop_id=$1 ORDER BY created_at, id', [shopId]);
  const got = await many(
    `SELECT courier_id, COUNT(*)::int AS n,
            COALESCE(SUM(expected_collect_minor),0) AS expected, COALESCE(SUM(cash_collected_minor),0) AS collected,
            COALESCE(SUM(cash_collected_minor) FILTER (WHERE settlement_id=''),0) AS unsettled
       FROM delivery_assignments
      WHERE shop_id=$1 AND mode='shop_courier' AND status='delivered' AND delivered_at >= $2 AND delivered_at < $3
      GROUP BY courier_id`,
    [shopId, f, t]
  );
  const settled = await many(
    `SELECT courier_id, COUNT(*)::int AS n, COALESCE(SUM(handover_due_minor),0) AS due,
            COALESCE(SUM(received_minor),0) AS received, COALESCE(SUM(shortage_minor),0) AS shortage,
            COALESCE(SUM(excess_minor),0) AS excess, COALESCE(SUM(fare_total_minor),0) AS fares
       FROM courier_settlements WHERE shop_id=$1 AND created_at >= $2 AND created_at < $3
      GROUP BY courier_id`,
    [shopId, f, t]
  );
  const held = await many(
    `SELECT courier_id, COALESCE(SUM(cash_collected_minor),0) AS held
       FROM delivery_assignments WHERE shop_id=$1 AND mode='shop_courier' AND status='delivered' AND settlement_id=''
      GROUP BY courier_id`,
    [shopId]
  );
  const by = (list) => new Map(list.map((r) => [r.courier_id, r]));
  const G = by(got), S = by(settled), H = by(held);
  const ids = [...couriers.map((c) => c.id)];
  for (const m of [G, S, H]) for (const id of m.keys()) if (!ids.includes(id)) ids.push(id);
  const names = new Map(couriers.map((c) => [c.id, c.name]));
  const zero = () => ({
    deliveries: 0, expectedCashMinor: 0, collectedCashMinor: 0, unsettledMinor: 0, settlements: 0,
    handoverDueMinor: 0, receivedMinor: 0, shortageMinor: 0, excessMinor: 0, fareTotalMinor: 0, inHandMinor: 0,
  });
  const totals = zero();
  const rows = [];
  for (const id of ids) {
    const g = G.get(id), s = S.get(id), h = H.get(id);
    const row = {
      ...zero(), courierId: id, name: names.get(id) || '',
      deliveries: g ? Number(g.n) : 0, expectedCashMinor: g ? Number(g.expected) : 0,
      collectedCashMinor: g ? Number(g.collected) : 0, unsettledMinor: g ? Number(g.unsettled) : 0,
      settlements: s ? Number(s.n) : 0, handoverDueMinor: s ? Number(s.due) : 0, receivedMinor: s ? Number(s.received) : 0,
      shortageMinor: s ? Number(s.shortage) : 0, excessMinor: s ? Number(s.excess) : 0, fareTotalMinor: s ? Number(s.fares) : 0,
      inHandMinor: h ? Number(h.held) : 0,
    };
    //  پیکی که در بازه هیچ گردشی ندارد و چیزی در دست ندارد ردیف نمی‌گیرد
    if (!g && !s && !row.inHandMinor) continue;
    for (const k of Object.keys(totals)) totals[k] += row[k];
    rows.push(row);
  }
  for (const [k, v] of Object.entries(totals)) {
    if (!Number.isSafeInteger(v)) throw new ApiError(500, 'report_overflow', `جمعِ گزارش (${k}) بیرون از بازهٔ دقیق است`);
  }
  return { from: f, to: t, couriers: rows, totals };
}

module.exports = { cashReport, CASH_REPORT_MAX_SPAN_MS };
