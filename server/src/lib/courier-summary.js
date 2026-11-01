'use strict';
/**
 * پیک (DLV-11/12): خطِ زمانیِ مأموریت‌ها و «نقدِ من» — فقط خواندن. از `delivery.js` جدا شد تا آن فایل
 * از سقفِ اندازه نگذرد؛ همان پرسش‌ها و همان شکلِ پاسخ. کمکی‌های تسویه و نقد از خودِ `delivery.js` می‌آیند
 * (`_forSummary`)، پس یک قاعده دو جا نوشته نمی‌شود.
 */
const { one, many, now } = require('../db');
const calendar = require('./calendar-core');

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

/**
 * آغازِ «امروز» و «این هفته» به وقتِ دکان — منطقهٔ زمانیِ ویترین (§۳۱، پیش‌فرض
 * Asia/Kabul؛ `storefront.localParts`) و آغازِ هفتهٔ دکان (`calendar-core.weekStartOf`،
 * همتای `BusinessDay`؛ دکان هنوز قاعدهٔ هفتهٔ جدا ندارد ⇒ قاعدهٔ پیش‌فرضِ همان‌جا).
 * ⛔ ساعتِ گوشیِ پیک مرجع نیست: گوشیِ جلوافتاده «امروزِ آینده» نمی‌فرستد.
 * منطقه‌های زمانیِ بی ساعتِ تابستانی (کابل) دقیق‌اند؛ در منطقه‌های دیگر مرزِ
 * هفته در روزِ تغییرِ ساعت تا یک ساعت جابه‌جا می‌شود.
 */
function periodStarts(at, timezone) {
  const { localParts } = require('./storefront');   //  هنگامِ صدا، نه بارگذاری (حلقهٔ require نمی‌سازد)
  let tz = timezone || 'Asia/Kabul';
  let local;
  try { local = localParts(at, tz); } catch { tz = 'Asia/Kabul'; local = localParts(at, tz); }
  const today = at - local.minutes * 60_000 - (at % 60_000);
  const weekDate = calendar.weekStartOf(local.date);
  const back = calendar.jdnOfIso(local.date) - calendar.jdnOfIso(weekDate);
  return { timezone: tz, date: local.date, weekDate, today, week: today - back * 86_400_000 };
}

/**
 * «نقدِ من» و آمارِ پیک (DLV-11/12؛ فقط خواندن). ⛔ فقط دادهٔ همین پیک در همین
 * دکان. «جای خالی» با همان سنجهٔ سپردن حساب می‌شود (در دست + موردِ انتظارِ
 * مأموریت‌های زنده در برابرِ سقف؛ سقفِ صفر = «نقد نگیرد»). مرزِ روز و هفته را
 * سرور به وقتِ دکان می‌سازد (`periodStarts`).
 */
async function courierSummary(shopId, courier) {
  const at = now();
  const shop = await one(
    `SELECT s.name, COALESCE(sf.title, '') AS title, COALESCE(sf.contact_phone, '') AS phone,
            COALESCE(sf.timezone, '') AS timezone
       FROM shops s LEFT JOIN storefronts sf ON sf.shop_id = s.id WHERE s.id=$1`,
    [shopId]
  );
  const from = periodStarts(at, shop?.timezone);
  const { cashStats, viaDb, SETTLE_ROWS, settleRowView } = D();
  const st = await cashStats(viaDb, shopId, courier.id);
  const cap = Number(courier.cash_cap_minor);
  const unsettled = await many(
    `${SETTLE_ROWS} WHERE a.shop_id=$1 AND a.courier_id=$2 AND a.status='delivered' AND a.settlement_id=''
      ORDER BY a.delivered_at DESC, a.id LIMIT 50`,
    [shopId, courier.id]
  );
  //  شمارِ کل، تا فهرستِ ۵۰تایی بگوید «+N مورد دیگر» (جمعِ «در دست» همه را دارد)
  const unsettledCount = Number((await one(
    `SELECT COUNT(*)::int AS n FROM delivery_assignments
      WHERE shop_id=$1 AND courier_id=$2 AND status='delivered' AND settlement_id=''`,
    [shopId, courier.id]
  )).n);
  const settlements = await many(
    'SELECT * FROM courier_settlements WHERE shop_id=$1 AND courier_id=$2 ORDER BY created_at DESC, id LIMIT 20',
    [shopId, courier.id]
  );
  const period = async (since) => {
    const r = await one(
      `SELECT COUNT(*) FILTER (WHERE status='delivered' AND delivered_at >= $3)::int AS delivered,
              COALESCE(SUM(cash_collected_minor) FILTER (WHERE status='delivered' AND delivered_at >= $3), 0) AS collected,
              COALESCE(SUM(fare_minor) FILTER (WHERE status='delivered' AND delivered_at >= $3), 0) AS fares
         FROM delivery_assignments WHERE shop_id=$1 AND courier_id=$2`,
      [shopId, courier.id, since]
    );
    //  «ناموفق» = مأموریت‌هایی که خودِ پیک در همین بازه «تحویل نشد» زد — به زمانِ
    //  همان رویداد، نه updated_at (پس‌خواستن آن را جابه‌جا می‌کرد) و نه «برگشت»ِ دکان
    const f = await one(
      `SELECT COUNT(DISTINCT e.assignment_id)::int AS n
         FROM delivery_events e JOIN delivery_assignments a ON a.id = e.assignment_id
        WHERE a.shop_id=$1 AND a.courier_id=$2 AND e.shop_id=$1 AND e.kind='status'
          AND e.to_status='failed' AND e.actor_type='courier' AND e.server_time >= $3`,
      [shopId, courier.id, since]
    );
    return {
      from: since, delivered: Number(r.delivered), failed: Number(f.n),
      collectedCashMinor: Number(r.collected), fareMinor: Number(r.fares),
    };
  };
  return {
    courier: { name: courier.name, active: courier.active === true },
    shop: { name: shop?.name || shop?.title || '', phone: shop?.phone || '', timezone: from.timezone },
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
    unsettledCount,
    settlements: settlements.map(courierSettlementView),
    stats: { today: await period(from.today), week: await period(from.week) },
    serverTime: at,
  };
}

module.exports = { withTimeline, summary: courierSummary, periodStarts, TIMELINE_KINDS };
