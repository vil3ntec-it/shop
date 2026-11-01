'use strict';
/**
 * RPT-14 — گزارشِ فروشِ اینترنتیِ دکان (سفارش‌های ویترین)، پشتِ پرچمِ خاموشِ
 * `shop.orders.online_report` (دکان / `all`؛ فقط `true` روشن است).
 *
 *   قیف ────── از سفارش‌هایی که در بازه **ثبت** شدند: ثبت ⇐ قبول ⇐ بسته‌بندی ⇐
 *              سپردن (بیرون برای تحویل / آماده برای بردن) ⇐ انجام (تحویل / برده شد).
 *              هر گام «دست‌کم یک بار رسید» است، از خطِ زمانیِ `online_order_events`،
 *              نه از وضعیتِ امروز — سفارشِ برگشتی هم «انجام» را گذرانده است.
 *   از دست رفته ─ ردشده، ردِ خودکار (مهلتِ قبول)، لغوِ مشتری، لغوِ دکان، برگشت به دکان؛
 *              «ناموفقِ تحویل» جدا (دست‌کم یک تلاشِ ناموفق) با نرخش از سفارش‌های بیرون‌رفته.
 *   زمان‌ها ── قبول (ثبت ⇐ نخستین قبول)، آماده‌سازی (قبول ⇐ نخستین بسته‌بندی)، تحویل
 *              (نخستین «بیرون برای تحویل» ⇐ نخستین «تحویل شد»)، کل (ثبت ⇐ انجام):
 *              شمار، میانه و صدکِ ۹۰ به میلی‌ثانیه (رتبهٔ نزدیک؛ فقط عددِ صحیح).
 *   نرخ‌ها ─── به ده‌هزارم (basis point، عددِ صحیح، گرد کردنِ نیمه به بالا)؛ مخرجِ صفر ⇒ null.
 *   پول ───── جمعِ سفارش‌های «انجام‌شده» به یک‌صدمِ افغانی (bigint ⇒ Number ِ امن).
 *   بی‌نتیجه ── جست‌وجوهای ویترین که صفحهٔ نخستشان هیچ کالایی نداشت (`noteMiss`)، به‌ازای
 *              روزِ UTC؛ پرتکرارترین‌ها در بازه.
 *
 *   ⛔ فقط سفارشِ ویترین (`source = 'storefront'`) و بی آزمایشی؛ shop_id فقط از نشست.
 *   ⛔ بازه همان قاعدهٔ گزارشِ تطبیق: ms، حداکثر ۹۲ روز، پیش‌فرض ۲۴ ساعتِ گذشته.
 *   ⛔ دفترِ بی‌نتیجه فقط با پرچمِ روشن نوشته می‌شود؛ سقفِ `MISS_DAILY_CAP` عبارتِ تازه برای
 *     هر دکان در هر روز (سیلِ عبارتِ ساختگی جدول را پر نمی‌کند)، بی عبارتِ شبیهِ شماره تلفن
 *     (هفت رقم یا بیشتر) و حداکثر ۶۰ نویسه؛ ۹۲ روز می‌ماند (`sweep`). خطای نوشتن هرگز
 *     جوابِ جست‌وجوی مشتری را خراب نمی‌کند.
 *   ⛔ «قیفِ بازدید» (بازدید ⇐ دیدنِ کالا ⇐ سبد) این‌جا نیست: شمارِ بازدیدِ ویترین با
 *     MKT-10 (#572) می‌آید و تا آن روز هیچ عددِ ساختگی‌ای جایش نمی‌نشیند.
 */
const { many, query, now } = require('../db');
const orders = require('./orders');
const { normalize } = require('./storefront');
const { badRequest } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.online_report';
const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_WINDOW_MS = 92 * DAY_MS;
const KEEP_MS = 92 * DAY_MS;
const MISS_DAILY_CAP = 300;
const MISS_TOP = 50;
const MAX_Q = 60;

const LOST = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'returned_to_store'];

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

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

/** نسبت به ده‌هزارم، گردِ نیمه به بالا، فقط با عددِ صحیح؛ مخرجِ صفر ⇒ null. */
function bp(part, whole) {
  if (!whole) return null;
  return Math.floor((part * 10000 + Math.floor(whole / 2)) / whole);
}

/** شمار، میانه و صدکِ ۹۰ (رتبهٔ نزدیک: ⌈p·n⌉ام). فهرستِ خالی ⇒ null ها. */
function stats(values) {
  const v = values.filter((x) => Number.isSafeInteger(x) && x >= 0).sort((a, b) => a - b);
  const n = v.length;
  if (!n) return { n: 0, medianMs: null, p90Ms: null };
  const rank = (num, den) => v[Math.max(1, Math.ceil((num * n) / den)) - 1];
  return { n, medianMs: rank(1, 2), p90Ms: rank(9, 10) };
}

const at = (v) => (v === null || v === undefined ? null : Number(v));
const span = (a, b) => (a !== null && b !== null && b >= a ? b - a : null);

/** گزارشِ بازه برای یک دکان. */
async function report(shopId, q = {}) {
  const w = windowOf(q);
  const rows = await many(
    `SELECT o.id, o.created_at, o.total_minor, o.fulfillment,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'accepted') AS accepted_at,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'packed') AS packed_at,
            MIN(e.created_at) FILTER (WHERE e.to_status IN ('out_for_delivery','ready_for_pickup')) AS handed_at,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'out_for_delivery') AS out_at,
            MIN(e.created_at) FILTER (WHERE e.to_status = 'delivered') AS delivered_at,
            MIN(e.created_at) FILTER (WHERE e.to_status IN ('delivered','picked_up')) AS done_at,
            COALESCE(BOOL_OR(e.to_status = 'delivery_failed'), false) AS failed_delivery,
            ARRAY_REMOVE(ARRAY_AGG(DISTINCT e.to_status) FILTER (WHERE e.to_status = ANY($4::text[])), NULL) AS lost
       FROM online_orders o
       LEFT JOIN online_order_events e ON e.order_id = o.id AND e.kind = 'status'
      WHERE o.shop_id = $1 AND o.source = 'storefront' AND o.is_test = false
        AND o.created_at >= $2 AND o.created_at < $3
      GROUP BY o.id`,
    [String(shopId), w.from, w.to, LOST]
  );
  const funnel = { placed: 0, accepted: 0, packed: 0, handedOver: 0, completed: 0 };
  const lost = Object.fromEntries(LOST.map((s) => [s, 0]));
  const times = { accept: [], prep: [], delivery: [], total: [] };
  let wentOut = 0;
  let failedDelivery = 0;
  let completedTotalMinor = 0;
  for (const r of rows) {
    const created = Number(r.created_at);
    const [accepted, packed, handed, out, delivered, done] = [r.accepted_at, r.packed_at, r.handed_at, r.out_at, r.delivered_at, r.done_at].map(at);
    funnel.placed++;
    if (accepted !== null) funnel.accepted++;
    if (packed !== null) funnel.packed++;
    if (handed !== null) funnel.handedOver++;
    if (done !== null) {
      funnel.completed++;
      completedTotalMinor += Number(r.total_minor);
    }
    for (const s of r.lost || []) if (s in lost) lost[s]++;
    if (out !== null) wentOut++;
    if (r.failed_delivery === true) failedDelivery++;
    times.accept.push(span(created, accepted));
    times.prep.push(span(accepted, packed));
    times.delivery.push(span(out, delivered));
    times.total.push(span(created, done));
  }
  const lostTotal = LOST.reduce((s, k) => s + lost[k], 0);
  return {
    enabled: true,
    window: w,
    funnel,
    lost: { ...lost, deliveryFailed: failedDelivery },
    rates: {
      acceptBp: bp(funnel.accepted, funnel.placed),
      completeBp: bp(funnel.completed, funnel.placed),
      lostBp: bp(lostTotal, funnel.placed),
      deliveryFailBp: bp(failedDelivery, wentOut),
    },
    times: Object.fromEntries(Object.entries(times).map(([k, v]) => [k, stats(v)])),
    completedTotalMinor,
    currency: 'AFN',
    zeroResultSearches: await misses(shopId, w),
    //  شمارِ بازدیدِ ویترین (بالای قیف) با MKT-10 می‌آید (#572)؛ تا آن روز عددی نیست
    visits: null,
  };
}

// ── جست‌وجوهای بی‌نتیجه ───────────────────────────────────────────

const dayOf = (ms) => Math.floor(ms / DAY_MS);

/** عبارتِ قابلِ ثبت — یا '' (خالی، کوتاه‌تر از ۲، یا شبیهِ شماره تلفن). */
function missKey(q) {
  const s = normalize(String(q || '')).slice(0, MAX_Q).trim();
  if (s.length < 2) return '';
  const digits = s.replace(/[۰-۹]/g, (d) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d))).replace(/[٠-٩]/g, (d) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)));
  if (/\d{7,}/.test(digits.replace(/[\s-]/g, ''))) return '';
  return s;
}

/**
 * یک جست‌وجوی بی‌نتیجهٔ ویترین را بشمار — فقط با پرچمِ روشن؛ هرگز پرتاب نمی‌کند.
 * عبارتِ تازه فقط تا سقفِ روزانهٔ دکان؛ عبارتِ دیده‌شده همیشه یکی بالا می‌رود.
 */
async function noteMiss(shopId, q, ms = now()) {
  try {
    const key = missKey(q);
    if (!key || !(await flagOf(shopId))) return false;
    const day = dayOf(ms);
    const r = await query(
      `INSERT INTO storefront_search_misses (shop_id, day, q, n, last_at)
       SELECT $1, $2, $3, 1, $4
        WHERE EXISTS (SELECT 1 FROM storefront_search_misses WHERE shop_id = $1 AND day = $2 AND q = $3)
           OR (SELECT COUNT(*) FROM storefront_search_misses WHERE shop_id = $1 AND day = $2) < $5
       ON CONFLICT (shop_id, day, q) DO UPDATE SET n = storefront_search_misses.n + 1, last_at = EXCLUDED.last_at`,
      [String(shopId), day, key, ms, MISS_DAILY_CAP]
    );
    return r.rowCount > 0;
  } catch (err) {
    console.error('[online-report] جست‌وجوی بی‌نتیجه ثبت نشد', err.message);
    return false;
  }
}

async function misses(shopId, w) {
  const rows = await many(
    `SELECT q, SUM(n)::int AS n, MAX(last_at) AS last_at FROM storefront_search_misses
      WHERE shop_id = $1 AND day >= $2 AND day <= $3
      GROUP BY q ORDER BY 2 DESC, 1 LIMIT $4`,
    [String(shopId), dayOf(w.from), dayOf(w.to - 1), MISS_TOP]
  );
  return rows.map((r) => ({ q: r.q, n: Number(r.n), lastAt: Number(r.last_at) }));
}

/** نگهداری: روزهای کهنه‌تر از ۹۲ روز پاک می‌شوند (پرچم خاموش ⇒ جدول خالی و بی‌اثر). */
async function sweep(ms = now()) {
  const r = await query('DELETE FROM storefront_search_misses WHERE day < $1', [dayOf(ms - KEEP_MS)]);
  return r.rowCount;
}

module.exports = {
  FLAG_KEY, MISS_DAILY_CAP, MAX_WINDOW_MS,
  flagOf, windowOf, report, noteMiss, sweep,
  //  برای آزمون
  bp, stats, missKey,
};
