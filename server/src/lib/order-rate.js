'use strict';
/**
 * RSK-07: سقفِ ثبتِ سفارشِ ویترین به‌ازای شمارهٔ تأییدشده — روی سقفِ IP
 * (`storeCheckoutMax` در مسیر) سوار است، نه به‌جایش.
 *
 * دو قاعده (ثابت‌های همین پرونده، بی متغیرِ محیطی):
 *   - همان دکان: ۱۰ سفارش در یک ساعت؛ یازدهمی ⇒ ۴۲۹ (صفِ دکان پرِ سفارشِ
 *     ساختگیِ یک شماره نشود).
 *   - همهٔ ویترین‌ها: ۴۰ سفارش در ۲۴ ساعت؛ چهل‌ویکمی ⇒ ۴۲۹ (یک شماره
 *     ده‌ها دکان را با هم پر نکند). مشتریِ واقعیِ بازارچه به هیچ‌کدام نمی‌رسد.
 * شمارش از خودِ `online_orders` (source = storefront) با نمایهٔ موجودِ
 * `ix_online_orders_storefront_phone` (۱۱۵) — بی جدول و بی مهاجرتِ تازه.
 * سفارشِ لغوشده هم شمرده می‌شود (ثبت‌ولغوِ پیاپی همان سوءاستفاده است).
 *
 * ⛔ سنجش **درونِ تراکنشِ `orders.create`** است (`guard` دورِ `price`، که
 * پس از قفلِ `orders:${shopId}` و پس از بررسیِ تکرارِ کلید اجرا می‌شود):
 *   - همان دکان: قفلِ دکان همهٔ ثبت‌های آن دکان را پشتِ هم می‌نشاند ⇒ سقف سخت است.
 *   - همهٔ ویترین‌ها: قفلِ `order-phone:<شماره>` (پس از قفلِ دکان؛ هر تراکنش
 *     فقط یک قفلِ شماره می‌گیرد، پس بن‌بست نمی‌سازد) ⇒ این هم سخت است.
 *     در PGlite همه‌چیز یک اتصال است و خودبه‌خود پشتِ هم.
 * ⛔ تکرارِ همان کلیدِ یکتایی (CHK-19) پیش از `price` پاسخ داده می‌شود و هرگز ۴۲۹
 * نمی‌گیرد.
 * کلیدِ سقف صفرِ پس از ‎+93‎ را برمی‌دارد (‎+930700…‎ همان ‎+93700…‎ است) —
 * فقط برای شمردن؛ دادهٔ ذخیره‌شده دست نمی‌خورد.
 */
const { now } = require('../db');
const { ApiError } = require('../middleware/errors');

const HOUR_MS = 60 * 60 * 1000;
const PER_SHOP = Object.freeze({ scope: 'shop', windowMs: HOUR_MS, max: 10 });
const ALL_SHOPS = Object.freeze({ scope: 'all', windowMs: 24 * HOUR_MS, max: 40 });
const RULES = Object.freeze([PER_SHOP, ALL_SHOPS]);

/** کلیدِ سقف و هر دو شکلِ ذخیره‌شده‌اش. */
const canon = (phone) => String(phone || '').replace(/^\+930/, '+93');
const formsOf = (key) => (key.startsWith('+93') ? [key, `+930${key.slice(3)}`] : [key]);

/**
 * زمانِ ساختِ max-امین سفارشِ تازه در پنجره — یا null اگر هنوز زیرِ سقف است.
 * همین زمان می‌گوید کِی یک جا باز می‌شود (Retry-After).
 */
async function nthNewest(run, rule, shopId, forms, at) {
  const params = [forms, at - rule.windowMs, rule.max - 1];
  let byShop = '';
  if (rule.scope === 'shop') { params.push(shopId); byShop = 'AND shop_id = $4'; }
  const r = (await run(
    `SELECT created_at FROM online_orders
      WHERE customer_phone = ANY($1::text[]) AND source = 'storefront' AND created_at > $2 ${byShop}
      ORDER BY created_at DESC OFFSET $3 LIMIT 1`,
    params
  )).rows[0];
  return r ? Number(r.created_at) : null;
}

/**
 * با اتصالِ تراکنش (`run`): شماره زیرِ هر دو سقف است؟ وگرنه ApiError ِ ۴۲۹ با
 * `retryAfter` (ثانیه، بیشترینِ قاعده‌های پرشده). شمارهٔ خالی کارِ این‌جا نیست.
 */
async function check(run, shopId, phone, { at = now(), rules = RULES } = {}) {
  if (!phone) return;
  const key = canon(phone);
  await run('SELECT pg_advisory_xact_lock(hashtext($1))', [`order-phone:${key}`]);
  let retryAfter = 0;
  let scope = '';
  for (const rule of rules) {
    const nth = await nthNewest(run, rule, shopId, formsOf(key), at);
    if (nth === null) continue;
    const ra = Math.max(1, Math.ceil((nth + rule.windowMs - at) / 1000));
    if (ra > retryAfter) { retryAfter = ra; scope = rule.scope; }
  }
  if (!retryAfter) return;
  const err = new ApiError(429, 'phone_order_limit',
    'از این شماره در این مدت سفارشِ زیادی ثبت شده است؛ کمی بعد دوباره امتحان کنید یا با دکان تماس بگیرید');
  err.retryAfter = retryAfter;
  err.data = { scope, retryAfter };
  throw err;
}

/**
 * `price` ِ `orders.create` با سقف پیش از قیمت‌گذاری — همان تراکنش، همان قفل. پس از قیمت‌گذاری،
 * قاعده‌های ریسکِ ویترین (RSK-02 مشتریِ تازه، RSK-05 تکراری؛ پشتِ پرچمِ خاموشِ `shop.order_risk`،
 * `lib/order-risk`) با همان قفلِ شماره، تا دو ثبتِ هم‌زمان از لای قاعده رد نشوند.
 */
function guard(price, { shopId, phone }) {
  return async (run, at) => {
    await check(run, shopId, phone, { at });
    const input = await price(run, at);
    await require('./order-risk').checkIn(run, shopId, phone, input, { at });
    return input;
  };
}

module.exports = { check, guard, canon, formsOf, RULES, PER_SHOP, ALL_SHOPS };
