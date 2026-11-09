'use strict';
/**
 * OPS-09 (§۶.۲۴ «مصرف پیامک و هزینه»؛ §۱۱.۳ «مصرف پیامک و واتس‌اپ برای هر فروشگاه و سقف روزانه») — مصرف و هزینهٔ
 * پیام‌های مشتری برای هر دکان و هر کانال، با نشانه‌های سوءاستفاده، برای زبانهٔ «عملیات» ِ پنلِ مدیر. فقط خواندن.
 *
 *   منبع ── همان دو صندوقی که سقفِ روزانهٔ NTF-02 (`customer-notify.spentToday`) می‌شمارد: `customer_notify_outbox`
 *           (اعلانِ سفارش و پوزش) و `storefront_alerts` (خبرِ «موجود/ارزان شد»). هزینه فقط از `tried` ِ ردیفِ «فرستاده»
 *           با `notify-cost.spentOf` (ردیفِ بی `costMicros` ⇒ قیمتِ امروزِ یک پیام، نه صفر) — همان عددی که سقف با آن
 *           سنجیده می‌شود. پیامی که نرفت هزینه ندارد.
 *   نشانه ─ «سقفِ هزینه»: ردیفی که کانالِ پولی‌اش را سقفِ روزانهٔ دکان راه نداد (`cost_cap` در `tried`، یا `notice` ِ
 *           خبر)؛ «سقفِ مشتری»: ردیفی که سقفِ ساعتی/روزانهٔ یک شماره نگهش داشت (`rate_limited`)؛ «نزدیکِ سقف»: هزینهٔ
 *           ۲۴ ساعتِ گذشتهٔ یک کانالِ پولی دست‌کم ۸۰٪ ِ سقفِ همان کانال.
 *   کدها ── `codes`: پیامکِ کدِ ورود/ثبت‌نام/ویترین از دفترِ `sms_usage` (`lib/sms-usage.js`) در همان بازه، جمع و به تفکیکِ
 *           `purpose` (با دکان/بی دکان)؛ هزینه به واحدِ خودِ آن دفتر (`currency`، `costMinor`)، نه میکروی کانال‌ها.
 *   حریم ── فقط شناسه و نامِ دکان، کانال و عدد؛ هیچ شماره، `phone_key`، متن، شناسهٔ سفارش یا کالا.
 *   حد ──── بازه ۱ تا ۳۱ روز (پیش‌فرض ۷)؛ حداکثر [MAX_SHOPS] دکان، پرهزینه‌ترین و سپس پرپیام‌ترین اول؛ هزینه از حداکثر
 *           [MAX_ROWS] ردیفِ «فرستاده» ِ هر صندوق (بیشتر ⇒ `truncated`، تازه‌ترین‌ها شمرده می‌شوند).
 */
const { many, now } = require('../db');
const cost = require('./notify-cost');

const CHANNELS = Object.freeze(Object.keys(cost.UNIT_MICROS));
const DEFAULT_DAYS = 7;
const MAX_DAYS = 31;
const MAX_SHOPS = 50;
const MAX_ROWS = 20_000;
const NEAR_CAP = 0.8;

/** روزهای بازه: عددِ صحیحِ ۱ تا ۳۱؛ هر چیزِ دیگر ⇒ پیش‌فرض */
function daysOf(raw) {
  const n = Number(raw);
  return Number.isInteger(n) && n >= 1 && n <= MAX_DAYS ? n : DEFAULT_DAYS;
}

const asList = (x) => {
  if (Array.isArray(x)) return x;
  try { const v = JSON.parse(x || '[]'); return Array.isArray(v) ? v : []; } catch { return []; }
};

function blankShop(shopId) {
  return {
    shopId, name: '', sent: 0, costMicros: 0,
    byChannel: Object.fromEntries(CHANNELS.map((c) => [c, { sent: 0, costMicros: 0, last24hMicros: 0 }])),
    costCap: 0, rateLimited: 0, nearCap: [],
  };
}

/**
 * @param {{days?: number, at?: number, env?: object}} [o]
 * @returns {Promise<object>} شرح در سرِ پرونده
 */
async function report({ days, at = now(), env = process.env } = {}) {
  const d = daysOf(days);
  const since = at - d * cost.DAY_MS;
  const dayAgo = at - cost.DAY_MS;
  const shops = new Map();
  const shopOf = (id) => {
    if (!shops.has(id)) shops.set(id, blankShop(id));
    return shops.get(id);
  };

  let truncated = false;
  for (const table of ['customer_notify_outbox', 'storefront_alerts']) {
    const sentCol = table === 'customer_notify_outbox' ? 'status' : 'notice';
    const rows = await many(
      `SELECT shop_id, channel, tried, sent_at FROM ${table}
        WHERE ${sentCol} = 'sent' AND sent_at > $1 AND sent_at <= $2
        ORDER BY sent_at DESC LIMIT ${MAX_ROWS + 1}`,
      [since, at]
    );
    if (rows.length > MAX_ROWS) { truncated = true; rows.length = MAX_ROWS; }
    for (const r of rows) {
      const ch = CHANNELS.includes(r.channel) ? r.channel : null;
      if (!ch) continue;
      const s = shopOf(r.shop_id);
      const micros = cost.spentOf([asList(r.tried)], ch, env);
      s.sent += 1;
      s.costMicros += micros;
      s.byChannel[ch].sent += 1;
      s.byChannel[ch].costMicros += micros;
      if (Number(r.sent_at) > dayAgo) s.byChannel[ch].last24hMicros += micros;
    }
  }

  const blocked = await many(
    `SELECT shop_id, SUM(cost_cap)::int AS cost_cap, SUM(rate_limited)::int AS rate_limited FROM (
       SELECT shop_id,
              COUNT(*) FILTER (WHERE tried @> '[{"result":"cost_cap"}]'::jsonb) AS cost_cap,
              COUNT(*) FILTER (WHERE status = 'rate_limited') AS rate_limited
         FROM customer_notify_outbox WHERE updated_at > $1 AND updated_at <= $2 GROUP BY shop_id
       UNION ALL
       SELECT shop_id,
              COUNT(*) FILTER (WHERE notice = 'cost_cap' OR tried @> '[{"result":"cost_cap"}]'::jsonb) AS cost_cap,
              COUNT(*) FILTER (WHERE notice = 'rate_limited') AS rate_limited
         FROM storefront_alerts WHERE updated_at > $1 AND updated_at <= $2 GROUP BY shop_id
     ) b GROUP BY shop_id`,
    [since, at]
  );
  for (const b of blocked) {
    if (!(b.cost_cap > 0 || b.rate_limited > 0)) continue;
    const s = shopOf(b.shop_id);
    s.costCap += Number(b.cost_cap) || 0;
    s.rateLimited += Number(b.rate_limited) || 0;
  }

  const channels = CHANNELS.map((c) => ({ channel: c, unitMicros: cost.unitMicros(c, env), capMicros: cost.capMicros(c, env) }));
  for (const s of shops.values()) {
    for (const c of channels) {
      if (c.unitMicros > 0 && s.byChannel[c.channel].last24hMicros >= Math.ceil(c.capMicros * NEAR_CAP)) s.nearCap.push(c.channel);
    }
  }

  const all = [...shops.values()].sort((a, b) => b.costMicros - a.costMicros || b.sent - a.sent
    || (b.costCap + b.rateLimited) - (a.costCap + a.rateLimited) || (a.shopId < b.shopId ? -1 : 1));
  const shown = all.slice(0, MAX_SHOPS);
  if (shown.length) {
    const names = await many('SELECT id, name FROM shops WHERE id = ANY($1::text[])', [shown.map((s) => s.shopId)]);
    const byId = new Map(names.map((n) => [n.id, n.name]));
    for (const s of shown) s.name = byId.get(s.shopId) || '';
  }

  const totals = { sent: 0, costMicros: 0, costCap: 0, rateLimited: 0, shops: all.length };
  for (const s of all) {
    totals.sent += s.sent;
    totals.costMicros += s.costMicros;
    totals.costCap += s.costCap;
    totals.rateLimited += s.rateLimited;
  }
  //  OPS-09: پیامکِ کدهای ورود و ویترین در همین گزارش (دفترِ `sms_usage`؛ واحدِ هزینه‌اش جداست: `currency`/`costMinor`)
  const sms = require('./sms-usage');
  const [codeTotal, codeKinds, unitCostMinor] = await Promise.all([sms.totals({ since, until: at + 1 }), sms.byPurpose({ since, until: at + 1 }), sms.unitCost()]);
  return {
    days: d, since, checkedAt: at, channels, totals, shops: shown,
    shopsTruncated: all.length > shown.length, truncated,
    codes: { currency: sms.CURRENCY, unitCostMinor, total: codeTotal, byPurpose: codeKinds },
  };
}

module.exports = { CHANNELS, DEFAULT_DAYS, MAX_DAYS, MAX_SHOPS, MAX_ROWS, NEAR_CAP, daysOf, report };
