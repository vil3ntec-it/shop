'use strict';
/**
 * RSK-07: سقفِ ثبتِ سفارشِ ویترین به‌ازای مشتریِ تأییدشده — **ایمیلِ تأییدشده** («فقط ایمیل»، ۱۴۰۵/۰۸/۲۳؛ پیش‌تر
 * شمارهٔ تأییدشده) — روی سقفِ IP (`storeCheckoutMax` در مسیر) سوار است، نه به‌جایش.
 *
 * دو قاعده (ثابت‌های همین پرونده، بی متغیرِ محیطی):
 *   - همان دکان: ۱۰ سفارش در یک ساعت؛ یازدهمی ⇒ ۴۲۹ (صفِ دکان پرِ سفارشِ
 *     ساختگیِ یک مشتری نشود).
 *   - همهٔ ویترین‌ها: ۴۰ سفارش در ۲۴ ساعت؛ چهل‌ویکمی ⇒ ۴۲۹ (یک مشتری
 *     ده‌ها دکان را با هم پر نکند). مشتریِ واقعیِ بازارچه به هیچ‌کدام نمی‌رسد.
 * شمارش از خودِ `online_orders.customer_email` (source = storefront) با نمایهٔ
 * `ix_online_orders_storefront_email` (۸۸۱).
 * سفارشِ لغوشده هم شمرده می‌شود (ثبت‌ولغوِ پیاپی همان سوءاستفاده است).
 *
 * ⛔ سنجش **درونِ تراکنشِ `orders.create`** است (`guard` دورِ `price`، که
 * پس از قفلِ `orders:${shopId}` و پس از بررسیِ تکرارِ کلید اجرا می‌شود):
 *   - همان دکان: قفلِ دکان همهٔ ثبت‌های آن دکان را پشتِ هم می‌نشاند ⇒ سقف سخت است.
 *   - همهٔ ویترین‌ها: قفلِ `order-customer:<ایمیل>` (پس از قفلِ دکان؛ هر تراکنش
 *     فقط یک قفلِ مشتری می‌گیرد، پس بن‌بست نمی‌سازد) ⇒ این هم سخت است.
 *     در PGlite همه‌چیز یک اتصال است و خودبه‌خود پشتِ هم.
 * ⛔ تکرارِ همان کلیدِ یکتایی (CHK-19) پیش از `price` پاسخ داده می‌شود و هرگز ۴۲۹
 * نمی‌گیرد.
 * قاعده‌های ریسکِ دکان (`order-risk`، فهرستِ سیاه، «مشتریِ تازه»، تکراری) روی **شمارهٔ تماسِ** فرم می‌مانند —
 * دادهٔ دکان، نه هویت — پشتِ قفلِ `order-phone:<کلیدِ شماره>` (یک قفلِ شماره در هر تراکنش، پس از قفلِ مشتری).
 * کلیدِ شماره همهٔ صفرهای پس از ‎+93‎ را برمی‌دارد (`phoneKey`/`phoneForms` ِ validate) — فقط برای شمردن.
 */
const { now } = require('../db');
const { ApiError } = require('../middleware/errors');

const HOUR_MS = 60 * 60 * 1000;
const PER_SHOP = Object.freeze({ scope: 'shop', windowMs: HOUR_MS, max: 10 });
const ALL_SHOPS = Object.freeze({ scope: 'all', windowMs: 24 * HOUR_MS, max: 40 });
const RULES = Object.freeze([PER_SHOP, ALL_SHOPS]);

/** کلیدِ سقف و همهٔ شکل‌های ذخیره‌شدنی‌اش (RSK-07). */
const { phoneKey: canon, phoneForms: formsOf } = require('./validate');

/**
 * زمانِ ساختِ max-امین سفارشِ تازه در پنجره — یا null اگر هنوز زیرِ سقف است.
 * همین زمان می‌گوید کِی یک جا باز می‌شود (Retry-After).
 */
async function nthNewest(run, rule, shopId, email, at) {
  const params = [email, at - rule.windowMs, rule.max - 1];
  let byShop = '';
  if (rule.scope === 'shop') { params.push(shopId); byShop = 'AND shop_id = $4'; }
  const r = (await run(
    `SELECT created_at FROM online_orders
      WHERE customer_email = $1 AND source = 'storefront' AND created_at > $2 ${byShop}
      ORDER BY created_at DESC OFFSET $3 LIMIT 1`,
    params
  )).rows[0];
  return r ? Number(r.created_at) : null;
}

/**
 * با اتصالِ تراکنش (`run`): مشتری (ایمیلِ تأییدشده) زیرِ هر دو سقف است؟ وگرنه ApiError ِ ۴۲۹ با
 * `retryAfter` (ثانیه، بیشترینِ قاعده‌های پرشده). ایمیلِ خالی کارِ این‌جا نیست.
 */
async function check(run, shopId, email, { at = now(), rules = RULES } = {}) {
  if (!email) return;
  const key = String(email).toLowerCase();
  await run('SELECT pg_advisory_xact_lock(hashtext($1))', [`order-customer:${key}`]);
  let retryAfter = 0;
  let scope = '';
  for (const rule of rules) {
    const nth = await nthNewest(run, rule, shopId, key, at);
    if (nth === null) continue;
    const ra = Math.max(1, Math.ceil((nth + rule.windowMs - at) / 1000));
    if (ra > retryAfter) { retryAfter = ra; scope = rule.scope; }
  }
  if (!retryAfter) return;
  //  کدِ پاسخ همان `phone_order_limit` ِ قرارداد می‌ماند (کارخواه‌ها همین را می‌شناسند)
  const err = new ApiError(429, 'phone_order_limit',
    'از این حساب در این مدت سفارشِ زیادی ثبت شده است؛ کمی بعد دوباره امتحان کنید یا با دکان تماس بگیرید');
  err.retryAfter = retryAfter;
  err.data = { scope, retryAfter };
  throw err;
}

/**
 * `price` ِ `orders.create` با سقف پیش از قیمت‌گذاری — همان تراکنش، همان قفل. پس از قیمت‌گذاری،
 * قاعده‌های ریسکِ ویترین (RSK-02 مشتریِ تازه، RSK-05 تکراری؛ پشتِ پرچمِ خاموشِ `shop.order_risk`،
 * `lib/order-risk`) روی شمارهٔ تماسِ فرم، با قفلِ همان شماره، تا دو ثبتِ هم‌زمان از لای قاعده رد نشوند.
 */
function guard(price, { shopId, email, secondOrder = false, deviceId = '', key = '' }) {
  //  RSK-04 «دستگاه»: شناسهٔ تصادفیِ مرورگر/برنامهٔ خرید ⇒ چکیدهٔ همین دکان (`lib/risk-device`)؛ نادرست ⇒ «نامعلوم»
  const deviceKey = require('./risk-device').keyOf(shopId, deviceId);
  return async (run, at) => {
    await check(run, shopId, email, { at });
    const input = await price(run, at);
    const phone = input && input.customerPhone;
    if (phone) await run('SELECT pg_advisory_xact_lock(hashtext($1))', [`order-phone:${canon(phone)}`]);
    await require('./order-risk').checkIn(run, shopId, phone, input, { at, secondOrder: secondOrder === true, deviceKey });
    //  پذیرفته شد ⇒ دستگاهِ همین سفارش (فقط با پرچمِ روشن، همان تراکنش) تا دکان بتواند «این دستگاه» را ببندد
    if (deviceKey) await require('./risk-device').rememberIn(run, shopId, key, deviceKey, at);
    return input;
  };
}

module.exports = { check, guard, canon, formsOf, RULES, PER_SHOP, ALL_SHOPS };
