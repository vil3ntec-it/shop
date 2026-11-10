'use strict';
/**
 * RPT-14 — بالای قیفِ ویترینِ وب: بازدید ⇐ دیدنِ کالا ⇐ سبد، برای «گزارشِ فروشِ اینترنتی» (`order-online-report.js`؛
 * مهاجرتِ ۴۹۱؛ تپش: `POST /api/store/:slug/funnel/:step`، `routes/storefront-funnel-store.js`).
 *
 *   تپش ──── ویترینِ وب (`store.js`) برای هر گام در هر بارِ صفحه یک POST ِ بی بدنه می‌فرستد، فقط با `features.funnel`
 *            (همان پرچمِ پیش‌فرض خاموشِ `shop.orders.online_report` ِ گزارش). برنامهٔ خریدِ بومی هنوز تپش ندارد ⇒
 *            گزارش `web: true` می‌گوید و نسبتی از «ثبت» (که سفارشِ برنامه را هم دارد) ساخته نمی‌شود.
 *   یکتا ─── بازدیدکننده = HMAC(رازِ سرور، دکان + روزِ UTC + IP + مرورگر)، بریده به ۲۲ نویسه — بازگشت‌ناپذیر و هر روز
 *            تازه؛ IP و مرورگر جایی نوشته نمی‌شوند. هر (بازدیدکننده، گام) در روز یک بار: درجِ `ON CONFLICT DO NOTHING`
 *            و افزودن به شمارِ همان روز در **یک** دستور ⇒ تپشِ تکراری یا هم‌زمان هیچ‌گاه دو بار نمی‌شمارد.
 *   پیوسته ── گامِ n گام‌های ۱..n را هم می‌نشاند (سبد بی دیدنِ ثبت‌شده، مثلاً از پیوندِ کالا) ⇒ بازدید ≥ دیدن ≥ سبد.
 *   سقف ──── بازدیدکنندهٔ تازه فقط تا `STORE_FUNNEL_DAILY_CAP` (پیش‌فرض ۵۰۰۰) در روزِ هر دکان (سیلِ ساختگی جدول را پر
 *            نمی‌کند)؛ بازدیدکنندهٔ شمرده‌شده هنوز گام می‌گیرد؛ روزِ سقف‌خورده در گزارش `capped` است.
 *   نگهداری ─ ردِ بازدیدکننده ۲ روز (فقط برای حذفِ تکرارِ همان روز)، شمارِ روز ۹۲ روز (همان پنجرهٔ گزارش).
 *   ⛔ هرگز پرتاب نمی‌کند: خطای پایگاه (و نبودِ جدول پس از rollback) نه تپش را می‌خواباند نه گزارش را (`null` = «عدد نیست»).
 */
const crypto = require('node:crypto');
const { query, one, now } = require('../db');
const { badRequest } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.online_report';
const DAY_MS = 24 * 60 * 60 * 1000;
const STEPS = Object.freeze({ visit: 1, view: 2, cart: 3 });
const SEEN_KEEP_DAYS = 2;
const KEEP_DAYS = 92;
const DEFAULT_CAP = 5000;

const dayOf = (ms) => Math.floor(ms / DAY_MS);
function dailyCap() {
  const n = Number(process.env.STORE_FUNNEL_DAILY_CAP);
  return Number.isSafeInteger(n) && n > 0 ? n : DEFAULT_CAP;
}
const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

/** گامِ نشانی ⇐ شماره؛ ناشناخته ⇒ ۴۰۰ `bad_step` */
function stepOf(raw) {
  const n = Object.prototype.hasOwnProperty.call(STEPS, raw) ? STEPS[raw] : 0;
  if (!n) throw badRequest('گامِ ناشناخته', 'bad_step');
  return n;
}

function visitorOf(shopId, day, ip, ua) {
  const secret = require('./server-secret').serverSecret(['api', 'jwt'], '');   //  همان زنجیرهٔ یگانهٔ راز
  const key = crypto.createHash('sha256').update(`storefront-funnel-visitor\n${secret}`).digest();
  return crypto.createHmac('sha256', key).update(`${shopId}\n${day}\n${ip}\n${String(ua || '').slice(0, 300)}`)
    .digest('base64url').slice(0, 22);
}

const RECORD_SQL = `
  WITH ins AS (
    INSERT INTO storefront_funnel_seen (shop_id, day, visitor, step)
    SELECT $1, $2, $3, s FROM generate_series(1, $4::int) AS s
     WHERE EXISTS (SELECT 1 FROM storefront_funnel_seen WHERE shop_id = $1 AND day = $2 AND visitor = $3 AND step = 1)
        OR COALESCE((SELECT visits FROM storefront_funnel_days WHERE shop_id = $1 AND day = $2), 0) < $5
    ON CONFLICT DO NOTHING
    RETURNING step
  )
  INSERT INTO storefront_funnel_days AS d (shop_id, day, visits, views, carts)
  SELECT $1, $2, COUNT(*) FILTER (WHERE step = 1), COUNT(*) FILTER (WHERE step = 2), COUNT(*) FILTER (WHERE step = 3)
    FROM ins HAVING COUNT(*) > 0
  ON CONFLICT (shop_id, day) DO UPDATE
    SET visits = d.visits + EXCLUDED.visits, views = d.views + EXCLUDED.views, carts = d.carts + EXCLUDED.carts`;

/** یک تپش (پرچم پیش‌تر سنجیده شده) — true اگر چیزی تازه شمرده شد؛ هرگز پرتاب نمی‌کند. */
async function record(shopId, step, { ip = '', ua = '', at = now() } = {}) {
  try {
    const day = dayOf(at);
    const r = await query(RECORD_SQL, [String(shopId), day, visitorOf(String(shopId), day, ip, ua), step, dailyCap()]);
    return r.rowCount > 0;
  } catch (err) {
    console.error('[storefront-funnel] تپش ثبت نشد', err.message);
    return false;
  }
}

/**
 * جمعِ بازهٔ `w` ({from, to} به ms؛ روزهای UTC ِ `from` تا `to − 1`) برای یک دکان، یا null وقتی هنوز هیچ روزی شمرده
 * نشده (یا جدول نیست) — «عدد نیست»، نه صفر. `since` آغازِ نخستین روزِ شمرده‌شده (در ۹۲ روزِ نگهداری) به ms.
 */
async function totals(shopId, w) {
  try {
    const r = await one(
      `SELECT COALESCE(SUM(visits) FILTER (WHERE day BETWEEN $2 AND $3), 0)::int AS visits,
              COALESCE(SUM(views)  FILTER (WHERE day BETWEEN $2 AND $3), 0)::int AS views,
              COALESCE(SUM(carts)  FILTER (WHERE day BETWEEN $2 AND $3), 0)::int AS carts,
              COALESCE(BOOL_OR(visits >= $4) FILTER (WHERE day BETWEEN $2 AND $3), false) AS capped,
              MIN(day) AS since
         FROM storefront_funnel_days WHERE shop_id = $1`,
      [String(shopId), dayOf(w.from), dayOf(w.to - 1), dailyCap()]
    );
    if (!r || r.since === null || r.since === undefined) return null;
    return {
      visits: Number(r.visits), views: Number(r.views), carts: Number(r.carts),
      capped: r.capped === true, since: Number(r.since) * DAY_MS,
    };
  } catch (err) {
    console.error('[storefront-funnel] جمع خوانده نشد', err.message);
    return null;
  }
}

/** نگهداری: ردِ بازدیدکننده پس از ۲ روز، شمارِ روز پس از ۹۲ روز. */
async function sweep(ms = now()) {
  const today = dayOf(ms);
  try {
    const a = await query('DELETE FROM storefront_funnel_seen WHERE day < $1', [today - SEEN_KEEP_DAYS + 1]);
    const b = await query('DELETE FROM storefront_funnel_days WHERE day < $1', [today - KEEP_DAYS]);   //  بازهٔ ۹۲ روزه تا ۹۳ روزِ UTC را می‌پوشاند
    return a.rowCount + b.rowCount;
  } catch (err) {
    console.error('[storefront-funnel] نگهداری', err.message);
    return 0;
  }
}

module.exports = { FLAG_KEY, STEPS, DEFAULT_CAP, flagOn, stepOf, record, totals, sweep, dayOf };
