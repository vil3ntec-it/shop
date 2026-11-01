'use strict';
/**
 * پیک (DLV-11/12): خطِ زمانیِ مأموریت‌ها و «نقدِ من» — فقط خواندن. از `delivery.js` جدا شد تا آن فایل
 * از سقفِ اندازه نگذرد؛ همان پرسش‌ها و همان شکلِ پاسخ. کمکی‌های تسویه و نقد از خودِ `delivery.js` می‌آیند
 * (`_forSummary`)، پس یک قاعده دو جا نوشته نمی‌شود.
 */
const { one, many, now } = require('../db');
const { badRequest } = require('../middleware/errors');

const D = () => require('./delivery')._forSummary;

/** رویدادهایی که پیک در خطِ زمانیِ مأموریتِ خودش می‌بیند */
const TIMELINE_KINDS = ['created', 'status', 'recall', 'photo'];

/**
 * خطِ زمانیِ مأموریت برای پیک: نوع، وضعیتِ تازه، زمانِ سرور، کدِ دلیل و این‌که
 * کارِ خودش بوده یا دکان. ⛔ بی شناسهٔ کاربر/دستگاه و بی یادداشتِ آزاد.
 */
function timelineView(e) {
  return {
    kind: e.kind, to: e.to_status, at: Number(e.server_time), reason: e.reason || '',
    by: e.actor_type === 'courier' ? 'courier' : 'shop',
  };
}

/** کارت‌های پیک + خطِ زمانیِ هر کدام، با یک پرسش برای همه (نه یکی‌یکی) */
async function withTimeline(shopId, rows, courierView) {
  const byA = new Map();
  if (rows.length) {
    const evs = await many(
      `SELECT assignment_id, kind, to_status, server_time, reason, actor_type, seq FROM delivery_events
        WHERE shop_id=$1 AND assignment_id = ANY($2::text[]) AND kind = ANY($3::text[])
        ORDER BY server_time, seq`,
      [shopId, rows.map((r) => r.id), TIMELINE_KINDS]
    );
    for (const e of evs) {
      if (!byA.has(e.assignment_id)) byA.set(e.assignment_id, []);
      byA.get(e.assignment_id).push(timelineView(e));
    }
  }
  return rows.map((r) => ({ ...courierView(r), timeline: byA.get(r.id) || [] }));
}

/** تسویهٔ خودِ پیک — همان سند، بی شناسهٔ تأییدکننده و بی یادداشتِ مدیر */
function courierSettlementView(s) {
  return {
    settleNo: Number(s.settle_no), at: Number(s.created_at), status: s.status,
    assignmentsCount: Number(s.assignments_count),
    collectedCashMinor: Number(s.collected_cash_minor), fareTotalMinor: Number(s.fare_total_minor),
    handoverDueMinor: Number(s.handover_due_minor), receivedMinor: Number(s.received_minor),
    shortageMinor: Number(s.shortage_minor), excessMinor: Number(s.excess_minor), reason: s.reason || '',
  };
}

function sinceOf(v, fallback, at) {
  if (v === undefined || v === '') return fallback;
  const x = Number(v);
  if (!Number.isSafeInteger(x) || x < 0 || x > at) throw badRequest('آغازِ بازه معتبر نیست', 'bad_range');
  return x;
}

/**
 * «نقدِ من» و آمارِ پیک (DLV-11/12؛ فقط خواندن). ⛔ فقط دادهٔ همین پیک در همین
 * دکان. «جای خالی» با همان سنجهٔ سپردن حساب می‌شود (در دست + موردِ انتظارِ
 * مأموریت‌های زنده در برابرِ سقف). آغازِ «امروز» و «هفته» را برنامه به وقتِ
 * خودِ گوشی می‌فرستد (روزِ دکان)؛ بی آن ۲۴ ساعت و ۷ روزِ گذشته.
 */
async function courierSummary(shopId, courier, { today, week } = {}) {
  const at = now();
  const from = { today: sinceOf(today, at - 86_400_000, at), week: sinceOf(week, at - 7 * 86_400_000, at) };
  const { cashStats, viaDb, SETTLE_ROWS, settleRowView } = D();
  const st = await cashStats(viaDb, shopId, courier.id);
  const cap = Number(courier.cash_cap_minor);
  const unsettled = await many(
    `${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.status='delivered' AND a.settlement_id=''
      ORDER BY a.delivered_at DESC, a.id LIMIT 50`,
    [shopId, courier.id]
  );
  const settlements = await many(
    'SELECT * FROM courier_settlements WHERE shop_id=$1 AND courier_id=$2 ORDER BY created_at DESC, id LIMIT 20',
    [shopId, courier.id]
  );
  const period = async (since) => {
    const r = await one(
      `SELECT COUNT(*) FILTER (WHERE status='delivered' AND delivered_at >= $3)::int AS delivered,
              COUNT(*) FILTER (WHERE status IN ('failed','returned') AND updated_at >= $3)::int AS failed,
              COALESCE(SUM(cash_collected_minor) FILTER (WHERE status='delivered' AND delivered_at >= $3), 0) AS collected,
              COALESCE(SUM(fare_minor) FILTER (WHERE status='delivered' AND delivered_at >= $3), 0) AS fares
         FROM delivery_assignments WHERE shop_id=$1 AND courier_id=$2`,
      [shopId, courier.id, since]
    );
    return {
      from: since, delivered: Number(r.delivered), failed: Number(r.failed),
      collectedCashMinor: Number(r.collected), fareMinor: Number(r.fares),
    };
  };
  const shop = await one(
    `SELECT s.name, COALESCE(sf.title, '') AS title, COALESCE(sf.contact_phone, '') AS phone
       FROM shops s LEFT JOIN storefronts sf ON sf.shop_id = s.id WHERE s.id=$1`,
    [shopId]
  );
  return {
    courier: { name: courier.name, active: courier.active === true },
    shop: { name: shop?.name || shop?.title || '', phone: shop?.phone || '' },
    cash: {
      inHandMinor: st.held, liveExpectedMinor: st.live_expected, capMinor: cap,
      headroomMinor: Math.max(0, cap - st.held - st.live_expected), liveCount: st.live,
    },
    unsettled: unsettled.map((r) => {
      const v = settleRowView(r);
      return {
        orderNo: v.orderNo, deliveredAt: v.deliveredAt, expectedCollectMinor: v.expectedCollectMinor,
        cashCollectedMinor: v.cashCollectedMinor, fareMinor: v.fareMinor,
      };
    }),
    settlements: settlements.map(courierSettlementView),
    stats: { today: await period(from.today), week: await period(from.week) },
    serverTime: at,
  };
}

module.exports = { withTimeline, summary: courierSummary, TIMELINE_KINDS };
