'use strict';
/**
 * پنلِ مرکزی ۴.۷ — نظارت بر پیک و تحویل در **همهٔ دکان‌ها**؛ فقط خواندن. جای «میزِ تحویلِ» دکان را نمی‌گیرد:
 * سپردن، پس گرفتن، فراخوانی و تسویه همان‌جا (`/api/shop/delivery`) و با کارمندِ همان دکان می‌ماند. تنها
 * کنترل‌های مرکزی: آستانه‌های «گیر کرده» (live_config، از درِ `PUT /api/admin/flags` با دلیل و تاریخچه) و
 * خاموش کردنِ سرویسِ تحویلِ یک دکان (حالتِ `service_disabled` / `shop.delivery` ِ `shop_holds`).
 *
 * ⛔ هیچ دادهٔ شخصیِ مشتری (نام، شماره، نشانی، یادداشت) و هیچ کد/هشِ تحویل در پاسخ نیست؛ از پیک فقط نام.
 * ⛔ تعریف‌ها همان تعریف‌های گزارشِ نقدِ پیکِ دکان (RPT-15، PRِ #554) و تطبیقِ مدیر (#411) است:
 *    «گرفته» از `delivered_at` (فقط `shop_courier`)، «تسویه» از `created_at` ِ سند، «در دست» = تحویل‌شدهٔ
 *    تسویه‌نشده، مستقل از بازه. همه زمانِ سرور.
 */
const { one, many, now } = require('../db');
const watch = require('./order-watch');
const { thresholds, windowOf } = require('./admin-market');
const { badRequest } = require('../middleware/errors');

const DAY = 24 * 60 * 60 * 1000;
const N = (x) => (x === null || x === undefined ? null : Number(x));
const CAP = 5000;
const VIEWS = Object.freeze(['all', 'preparing', 'dispatch', 'assigned', 'in_delivery', 'failed', 'stuck', 'mismatch', 'discrepancy']);

function intIn(v, { min, max, def, field }) {
  if (v === undefined || v === null || v === '') return def;
  const n = Number(v);
  if (!Number.isSafeInteger(n) || n < min || n > max) throw badRequest(`${field} در بازهٔ مجاز نیست`, 'bad_query');
  return n;
}
function idOrEmpty(v) {
  const s = typeof v === 'string' ? v.trim() : '';
  if (!s) return '';
  if (s.length > 80 || !/^[A-Za-z0-9_.:-]+$/.test(s)) throw badRequest('شناسهٔ دکان معتبر نیست', 'bad_query');
  return s;
}

/**
 * ردیف‌های زیرِ نظر: سفارشِ ارسالی (یا هر سفارشی که مأموریت دارد) که هنوز باز است، یا در `days` روزِ گذشته
 * عوض شده — با آخرین مأموریتش. سقفِ ۵۰۰۰ و `truncated` ِ راست.
 */
async function watched({ days = 7, shopId = '' } = {}) {
  const at = now();
  const args = [at - days * DAY, watch.ORDER_OPEN, watch.LIVE];
  let shopWhere = '';
  if (shopId) { args.push(shopId); shopWhere = `AND o.shop_id = $${args.length}`; }
  //  نامزدها از سه نمایهٔ مهاجرتِ ۱۷۲ (تازه‌ها، وضعیتِ باز، مأموریتِ زنده) — نه پیمایشِ کلِ سفارش‌ها
  const rows = await many(
    `WITH cand AS (
       SELECT id FROM online_orders WHERE updated_at >= $1
       UNION SELECT id FROM online_orders WHERE status = ANY($2::text[])
       UNION SELECT order_id FROM delivery_assignments WHERE status = ANY($3::text[])
     )
     SELECT o.id, o.shop_id, s.name AS shop_name, o.order_no, o.source, o.status, o.fulfillment, o.payment_method,
            o.payment_status, o.cod_status, o.currency, o.total_minor, o.created_at, o.updated_at,
            la.*, c.name AS courier_name
       FROM cand
       JOIN online_orders o ON o.id = cand.id
       JOIN shops s ON s.id = o.shop_id
       LEFT JOIN LATERAL (
         SELECT a.id AS a_id, a.status AS a_status, a.mode AS a_mode, a.courier_id AS a_courier_id,
                a.updated_at AS a_updated_at, a.created_at AS a_created_at, a.cash_review AS a_cash_review,
                a.cash_collected_minor AS a_cash_collected_minor, a.expected_collect_minor AS a_expected_collect_minor,
                a.recall_outcome AS a_recall_outcome, a.recall_requested_at AS a_recall_requested_at,
                a.attempt_no AS a_attempt_no, a.external_name AS a_external_name
           FROM delivery_assignments a WHERE a.order_id = o.id ORDER BY a.attempt_no DESC LIMIT 1) la ON true
       LEFT JOIN shop_couriers c ON c.id = la.a_courier_id AND c.shop_id = o.shop_id
      WHERE o.is_test = false
        AND (o.fulfillment = 'delivery' OR la.a_id IS NOT NULL)
        ${shopWhere}
      ORDER BY o.updated_at DESC, o.id DESC
      LIMIT ${CAP + 1}`,
    args
  );
  return { at, rows: rows.slice(0, CAP), truncated: rows.length > CAP };
}

function rowView(r, cls, at) {
  const ageFrom = r.a_updated_at || r.updated_at;
  return {
    orderId: r.id, shopId: r.shop_id, shopName: r.shop_name || '', orderNo: N(r.order_no), source: r.source,
    status: r.status, fulfillment: r.fulfillment, stage: watch.stageOf(r),
    payment: { method: r.payment_method, status: r.payment_status, cod: r.cod_status || null },
    currency: r.currency, totalMinor: N(r.total_minor), createdAt: N(r.created_at), updatedAt: N(r.updated_at),
    mission: r.a_id ? {
      id: r.a_id, status: r.a_status, mode: r.a_mode, attemptNo: N(r.a_attempt_no),
      courierId: r.a_courier_id || null, courierName: r.courier_name || (r.a_mode === 'external' ? r.a_external_name || null : null),
      createdAt: N(r.a_created_at), updatedAt: N(r.a_updated_at), cashReview: r.a_cash_review === true,
      expectedCollectMinor: N(r.a_expected_collect_minor), cashCollectedMinor: N(r.a_cash_collected_minor),
      recall: r.a_recall_outcome || null,
    } : null,
    ageMs: ageFrom ? Math.max(0, at - Number(ageFrom)) : null,
    issues: cls,
  };
}

const zeroOf = (list) => Object.fromEntries(list.map((k) => [k, 0]));

async function overview(q = {}) {
  const days = intIn(q.days, { min: 1, max: 30, def: 7, field: 'روز' });
  const th = await thresholds();
  const { at, rows, truncated } = await watched({ days });
  const stages = zeroOf(['preparing', 'dispatch', 'assigned', 'in_delivery', 'failed']);
  const stuck = zeroOf(watch.STUCK);
  const mismatch = zeroOf(watch.MISMATCH);
  const discrepancy = zeroOf(watch.DISCREPANCY);
  const ordersWith = { stuck: 0, mismatch: 0, discrepancy: 0 };
  for (const r of rows) {
    const st = watch.stageOf(r);
    if (st) stages[st]++;
    const c = watch.classify(r, th.values, at);
    for (const k of c.stuck) stuck[k]++;
    for (const k of c.mismatch) mismatch[k]++;
    for (const k of c.discrepancy) discrepancy[k]++;
    if (c.stuck.length) ordersWith.stuck++;
    if (c.mismatch.length) ordersWith.mismatch++;
    if (c.discrepancy.length) ordersWith.discrepancy++;
  }
  const since = at - DAY;
  const [couriers, last24, flags, holds] = await Promise.all([
    one(`SELECT COUNT(*)::int AS total, COUNT(*) FILTER (WHERE active)::int AS active, COUNT(DISTINCT shop_id)::int AS shops
           FROM shop_couriers`),
    one(
      `SELECT COUNT(*) FILTER (WHERE status = 'delivered' AND delivered_at >= $1)::int AS delivered,
              COUNT(*) FILTER (WHERE status = 'failed' AND updated_at >= $1)::int AS failed,
              COUNT(*) FILTER (WHERE status = 'returned' AND updated_at >= $1)::int AS returned,
              COUNT(*) FILTER (WHERE created_at >= $1)::int AS created
         FROM delivery_assignments WHERE updated_at >= $1`,
      [since]
    ),
    many(`SELECT scope, value FROM live_config WHERE key = 'shop.delivery'`),
    one(`SELECT COUNT(*)::int AS n FROM shop_holds WHERE kind = 'service_disabled' AND service = 'shop.delivery' AND lifted_at IS NULL`),
  ]);
  const dec = (v) => { try { return JSON.parse(v); } catch { return null; } };
  const allRow = flags.find((f) => f.scope === 'all');
  return {
    serverTime: at, windowDays: days, scanned: rows.length, truncated,
    thresholds: th,
    stages, stuck, mismatch, discrepancy, ordersWith,
    last24h: last24,
    couriers,
    service: {
      flagAll: allRow ? dec(allRow.value) === true : false,
      flagAllSet: Boolean(allRow),
      shopsOn: flags.filter((f) => f.scope !== 'all' && dec(f.value) === true).length,
      shopsOff: flags.filter((f) => f.scope !== 'all' && dec(f.value) !== true).length,
      heldByAdmin: holds.n,
    },
  };
}

async function queue(q = {}) {
  const view = typeof q.view === 'string' && q.view ? q.view : 'all';
  if (!VIEWS.includes(view)) throw badRequest('نما درست نیست', 'bad_query');
  const days = intIn(q.days, { min: 1, max: 30, def: 7, field: 'روز' });
  const limit = intIn(q.limit, { min: 1, max: 200, def: 50, field: 'تعداد' });
  const offset = intIn(q.offset, { min: 0, max: 100_000, def: 0, field: 'از' });
  const shopId = idOrEmpty(q.shopId);
  const th = await thresholds();
  const { at, rows, truncated } = await watched({ days, shopId });
  const hits = [];
  for (const r of rows) {
    const c = watch.classify(r, th.values, at);
    const st = watch.stageOf(r);
    const keep = view === 'all' ? Boolean(st || c.stuck.length || c.mismatch.length || c.discrepancy.length)
      : view === 'stuck' ? c.stuck.length > 0
      : view === 'mismatch' ? c.mismatch.length > 0
      : view === 'discrepancy' ? c.discrepancy.length > 0
      : st === view;
    if (keep) hits.push(rowView(r, c, at));
  }
  //  کهنه‌ترین اول (گیرکرده‌ترین) — همان ترتیبی که صفِ کار می‌خواهد
  hits.sort((a, b) => (b.ageMs ?? 0) - (a.ageMs ?? 0) || String(a.orderId).localeCompare(String(b.orderId)));
  return {
    serverTime: at, view, windowDays: days, thresholds: th.values, scanned: rows.length, truncated,
    total: hits.length, limit, offset, rows: hits.slice(offset, offset + limit),
  };
}

/** رویدادهای کلیدی — `delivery_events` ِ همهٔ دکان‌ها، تازه‌ترین اول؛ بی یادداشتِ آزاد و بی کد. */
async function events(q = {}) {
  const limit = intIn(q.limit, { min: 1, max: 200, def: 50, field: 'تعداد' });
  const before = intIn(q.before, { min: 0, max: Number.MAX_SAFE_INTEGER, def: 0, field: 'پیش از' });
  const shopId = idOrEmpty(q.shopId);
  const kind = typeof q.kind === 'string' ? q.kind : '';
  if (kind && !['created', 'status', 'recall', 'code', 'photo'].includes(kind)) throw badRequest('نوعِ رویداد درست نیست', 'bad_query');
  const args = [];
  const where = [];
  if (shopId) { args.push(shopId); where.push(`e.shop_id = $${args.length}`); }
  if (before) { args.push(before); where.push(`e.server_time < $${args.length}`); }
  if (kind) { args.push(kind); where.push(`e.kind = $${args.length}`); }
  args.push(limit);
  const rows = await many(
    `SELECT e.id, e.shop_id, s.name AS shop_name, e.assignment_id, e.order_id, o.order_no, e.kind, e.from_status,
            e.to_status, e.actor_type, e.device_time, e.server_time, e.reason, e.detail
       FROM delivery_events e
       JOIN shops s ON s.id = e.shop_id
       LEFT JOIN online_orders o ON o.id = e.order_id
      ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
      ORDER BY e.server_time DESC, e.id DESC LIMIT $${args.length}`,
    args
  );
  const KEEP = ['codeVerified', 'cashReason', 'recallOutcome', 'expectedCollectMinor', 'cashCollectedMinor', 'order', 'cod', 'locked'];
  return {
    serverTime: now(),
    events: rows.map((r) => {
      const d = typeof r.detail === 'string' ? JSON.parse(r.detail || '{}') : (r.detail || {});
      const detail = Object.fromEntries(Object.entries(d).filter(([k]) => KEEP.includes(k)));
      const dt = N(r.device_time);
      return {
        id: r.id, shopId: r.shop_id, shopName: r.shop_name || '', assignmentId: r.assignment_id, orderId: r.order_id,
        orderNo: N(r.order_no), kind: r.kind, from: r.from_status || null, to: r.to_status || null, actorType: r.actor_type,
        reason: r.reason || null, serverTime: N(r.server_time), deviceTime: dt,
        skewMs: dt === null ? null : N(r.server_time) - dt, detail,
      };
    }),
  };
}

/**
 * «اتصالِ سرویسِ پیک» از دادهٔ واقعی: درِ `/api/courier` همین سرور است، پس تنها نشانهٔ زنده بودنش رسیدنِ
 * رویدادِ پیک است. ⛔ تپشِ قلب (heartbeat) در مدل نیست؛ رویدادِ ردشده هم ذخیره نمی‌شود — این دو «نیست» گفته
 * می‌شوند، نه حدس. برای هر دکانِ پیک‌دار: آخرین رویدادِ پیک، شمار و دستگاه‌های ۲۴ ساعت، و بیشترین اختلافِ ساعتِ دستگاه.
 */
async function connectivity() {
  const at = now();
  const since = at - DAY;
  const rows = await many(
    `SELECT s.id AS shop_id, s.name AS shop_name,
            COUNT(c.id)::int AS couriers, COUNT(c.id) FILTER (WHERE c.active)::int AS active,
            (SELECT MAX(e.server_time) FROM delivery_events e WHERE e.shop_id = s.id AND e.actor_type = 'courier') AS last_event_at,
            (SELECT COUNT(*)::int FROM delivery_events e WHERE e.shop_id = s.id AND e.actor_type = 'courier' AND e.server_time >= $1) AS events_24h,
            (SELECT COUNT(DISTINCT e.device_id)::int FROM delivery_events e
              WHERE e.shop_id = s.id AND e.actor_type = 'courier' AND e.server_time >= $1 AND e.device_id <> '') AS devices_24h,
            (SELECT MAX(ABS(e.server_time - e.device_time)) FROM delivery_events e
              WHERE e.shop_id = s.id AND e.actor_type = 'courier' AND e.server_time >= $1 AND e.device_time IS NOT NULL) AS max_skew_ms,
            (SELECT COUNT(*)::int FROM delivery_assignments a
              WHERE a.shop_id = s.id AND a.status IN ('requested','assigned','picked_up','moving','arrived','failed')) AS live_missions
       FROM shops s JOIN shop_couriers c ON c.shop_id = s.id
      GROUP BY s.id, s.name
      ORDER BY last_event_at DESC NULLS LAST, s.id
      LIMIT 500`,
    [since]
  );
  const shops = rows.map((r) => ({
    shopId: r.shop_id, shopName: r.shop_name || '', couriers: r.couriers, active: r.active,
    lastCourierEventAt: N(r.last_event_at), events24h: r.events_24h, devices24h: r.devices_24h,
    maxSkewMs: N(r.max_skew_ms), liveMissions: r.live_missions,
  }));
  const lastAt = shops.reduce((m, x) => Math.max(m, x.lastCourierEventAt || 0), 0);
  return {
    serverTime: at,
    endpoint: { path: '/api/courier', sameServer: true },
    lastCourierEventAt: lastAt || null,
    heartbeat: { inModel: false },
    rejectedEvents: { stored: false },
    shops,
  };
}

/**
 * گزارشِ کارکرد از دادهٔ واقعی در [from, to): برای هر دکان و هر پیک — مأموریت‌های تمام‌شده، زمانِ سپردن تا
 * تحویل (میانگین و میانه، دقیقه)، نقدِ گرفته و تسویه‌نشده، سندهای تسویه (کسری/اضافه/کرایه) و «در دست» ِ همین
 * لحظه. جمعِ ردیف‌ها = جمعِ کل (هر ستون). دیدنش یک ردیفِ سابقه دارد (پولِ همهٔ دکان‌ها؛ همان الگوی #411).
 */
async function report(q = {}) {
  const at = now();
  const w = windowOf(q, at, 30);
  const [done, cash, settled, held, names] = await Promise.all([
    many(
      `SELECT shop_id, courier_id, mode,
              COUNT(*) FILTER (WHERE status = 'delivered')::int AS delivered,
              COUNT(*) FILTER (WHERE status = 'returned')::int AS returned,
              COUNT(*) FILTER (WHERE status = 'rejected')::int AS rejected,
              COUNT(*) FILTER (WHERE status = 'withdrawn')::int AS withdrawn,
              AVG((delivered_at - created_at) / 60000.0) FILTER (WHERE status = 'delivered' AND delivered_at IS NOT NULL) AS avg_min,
              percentile_cont(0.5) WITHIN GROUP (ORDER BY (delivered_at - created_at) / 60000.0)
                FILTER (WHERE status = 'delivered' AND delivered_at IS NOT NULL) AS median_min
         FROM delivery_assignments
        WHERE COALESCE(delivered_at, updated_at) >= $1 AND COALESCE(delivered_at, updated_at) < $2
          AND status IN ('delivered','returned','rejected','withdrawn')
        GROUP BY shop_id, courier_id, mode`,
      [w.from, w.to]
    ),
    many(
      `SELECT shop_id, courier_id, COUNT(*)::int AS n,
              COALESCE(SUM(expected_collect_minor),0) AS expected, COALESCE(SUM(cash_collected_minor),0) AS collected,
              COALESCE(SUM(cash_collected_minor) FILTER (WHERE settlement_id = ''),0) AS unsettled
         FROM delivery_assignments
        WHERE mode = 'shop_courier' AND status = 'delivered' AND delivered_at >= $1 AND delivered_at < $2
        GROUP BY shop_id, courier_id`,
      [w.from, w.to]
    ),
    many(
      `SELECT shop_id, courier_id, COUNT(*)::int AS n, COALESCE(SUM(handover_due_minor),0) AS due,
              COALESCE(SUM(received_minor),0) AS received, COALESCE(SUM(shortage_minor),0) AS shortage,
              COALESCE(SUM(excess_minor),0) AS excess, COALESCE(SUM(fare_total_minor),0) AS fares
         FROM courier_settlements WHERE created_at >= $1 AND created_at < $2
        GROUP BY shop_id, courier_id`,
      [w.from, w.to]
    ),
    many(
      `SELECT shop_id, courier_id, COALESCE(SUM(cash_collected_minor),0) AS held
         FROM delivery_assignments WHERE mode = 'shop_courier' AND status = 'delivered' AND settlement_id = ''
        GROUP BY shop_id, courier_id`
    ),
    many(`SELECT c.id, c.shop_id, c.name, s.name AS shop_name FROM shop_couriers c JOIN shops s ON s.id = c.shop_id`),
  ]);
  const key = (r) => `${r.shop_id}\u0000${r.courier_id || ''}`;
  const rows = new Map();
  const zero = () => ({
    delivered: 0, returned: 0, rejected: 0, withdrawn: 0, avgMinutes: null, medianMinutes: null,
    cashDeliveries: 0, expectedCashMinor: 0, collectedCashMinor: 0, unsettledMinor: 0,
    settlements: 0, handoverDueMinor: 0, receivedMinor: 0, shortageMinor: 0, excessMinor: 0, fareTotalMinor: 0, inHandMinor: 0,
  });
  const courierName = new Map(names.map((n) => [`${n.shop_id}\u0000${n.id}`, n.name]));
  const shopName = new Map(names.map((n) => [n.shop_id, n.shop_name]));
  const rowOf = (r) => {
    const k = key(r);
    if (!rows.has(k)) rows.set(k, { shopId: r.shop_id, courierId: r.courier_id || null, ...zero() });
    return rows.get(k);
  };
  const timing = new Map();
  for (const r of done) {
    const x = rowOf(r);
    x.delivered += r.delivered; x.returned += r.returned; x.rejected += r.rejected; x.withdrawn += r.withdrawn;
    //  یک پیک = یک حالت در عمل؛ اگر دو حالت بود، زمان‌ها از پرکارترین (بی میانگینِ میانگین‌ها)
    const prev = timing.get(key(r));
    if (r.avg_min !== null && (!prev || prev.n < r.delivered)) timing.set(key(r), { n: r.delivered, avg: Number(r.avg_min), med: Number(r.median_min) });
  }
  for (const [k, t] of timing) { const x = rows.get(k); x.avgMinutes = Math.round(t.avg * 10) / 10; x.medianMinutes = Math.round(t.med * 10) / 10; }
  for (const r of cash) {
    const x = rowOf(r);
    x.cashDeliveries += r.n; x.expectedCashMinor += Number(r.expected); x.collectedCashMinor += Number(r.collected);
    x.unsettledMinor += Number(r.unsettled);
  }
  for (const r of settled) {
    const x = rowOf(r);
    x.settlements += r.n; x.handoverDueMinor += Number(r.due); x.receivedMinor += Number(r.received);
    x.shortageMinor += Number(r.shortage); x.excessMinor += Number(r.excess); x.fareTotalMinor += Number(r.fares);
  }
  for (const r of held) rowOf(r).inHandMinor += Number(r.held);
  const SUM = ['delivered', 'returned', 'rejected', 'withdrawn', 'cashDeliveries', 'expectedCashMinor', 'collectedCashMinor',
    'unsettledMinor', 'settlements', 'handoverDueMinor', 'receivedMinor', 'shortageMinor', 'excessMinor', 'fareTotalMinor', 'inHandMinor'];
  const totals = Object.fromEntries(SUM.map((k) => [k, 0]));
  const list = [...rows.values()].map((x) => ({
    ...x, shopName: shopName.get(x.shopId) || '',
    courierName: x.courierId ? courierName.get(`${x.shopId}\u0000${x.courierId}`) || '' : '',
  }));
  for (const x of list) for (const k of SUM) totals[k] += x[k];
  for (const [k, v] of Object.entries(totals)) {
    if (!Number.isSafeInteger(v)) throw badRequest(`جمعِ گزارش (${k}) بیرون از بازهٔ دقیق است`, 'report_overflow');
  }
  list.sort((a, b) => a.shopName.localeCompare(b.shopName) || String(a.courierName).localeCompare(String(b.courierName)));
  return { serverTime: at, window: w, rows: list, totals };
}

module.exports = { VIEWS, CAP, watched, overview, queue, events, connectivity, report };
