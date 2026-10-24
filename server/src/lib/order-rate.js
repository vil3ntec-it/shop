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
 * ⛔ تکرارِ همان کلیدِ یکتایی (CHK-19) هرگز ۴۲۹ نمی‌گیرد: سفارش پیش‌تر ساخته
 * شده و پاسخِ «replayed» باید به مشتری برسد.
 * ⚠️ سقفِ نرم: دو ثبتِ واقعاً هم‌زمان ممکن است یکی بیشتر بگذرند؛ سقفِ IP و
 * زمانِ تأییدِ شماره همین را کوچک نگه می‌دارند. این‌جا قفلی روی سفارش نیست.
 */
const { one, now } = require('../db');
const { ApiError } = require('../middleware/errors');

const HOUR_MS = 60 * 60 * 1000;
const PER_SHOP = Object.freeze({ scope: 'shop', windowMs: HOUR_MS, max: 10 });
const ALL_SHOPS = Object.freeze({ scope: 'all', windowMs: 24 * HOUR_MS, max: 40 });
const RULES = Object.freeze([PER_SHOP, ALL_SHOPS]);

/**
 * زمانِ ساختِ max-امین سفارشِ تازه در پنجره — یا null اگر هنوز زیرِ سقف است.
 * همین زمان می‌گوید کِی یک جا باز می‌شود (Retry-After).
 */
async function nthNewest(rule, shopId, phone, at) {
  const params = [phone, at - rule.windowMs, rule.max - 1];
  let byShop = '';
  if (rule.scope === 'shop') { params.push(shopId); byShop = 'AND shop_id = $4'; }
  const r = await one(
    `SELECT created_at FROM online_orders
      WHERE customer_phone = $1 AND source = 'storefront' AND created_at > $2 ${byShop}
      ORDER BY created_at DESC OFFSET $3 LIMIT 1`,
    params
  );
  return r ? Number(r.created_at) : null;
}

async function isReplay(shopId, clientKey) {
  if (!clientKey) return false;
  return Boolean(await one('SELECT 1 AS x FROM online_orders WHERE shop_id = $1 AND client_key = $2', [shopId, String(clientKey)]));
}

/**
 * پیش از `orders.create`: شماره زیرِ هر دو سقف است؟ وگرنه ApiError ِ ۴۲۹
 * با `retryAfter` (ثانیه). شمارهٔ خالی (هنوز تأییدنشده) کارِ این‌جا نیست —
 * خودِ ثبت ۴۰۱ می‌دهد.
 */
async function check(shopId, phone, { clientKey = '', at = now(), rules = RULES } = {}) {
  if (!phone) return { ok: true };
  for (const rule of rules) {
    const nth = await nthNewest(rule, shopId, phone, at);
    if (nth === null) continue;
    if (await isReplay(shopId, clientKey)) return { ok: true, replay: true };
    const err = new ApiError(429, 'phone_order_limit',
      'از این شماره در این مدت سفارشِ زیادی ثبت شده است؛ کمی بعد دوباره امتحان کنید یا با دکان تماس بگیرید');
    err.retryAfter = Math.max(1, Math.ceil((nth + rule.windowMs - at) / 1000));
    err.data = { scope: rule.scope, retryAfter: err.retryAfter };
    throw err;
  }
  return { ok: true };
}

module.exports = { check, RULES, PER_SHOP, ALL_SHOPS };
