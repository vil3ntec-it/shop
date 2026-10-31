'use strict';
/**
 * DLV-08 — مدرکِ تحویل: «کدِ تحویلِ مشتری برای سفارشِ نقد هنگامِ تحویل یا گران‌قیمت» (§۶.۱۸).
 *
 * پشتِ پرچمِ خاموشِ `shop.delivery_code_rule` (دکان / `all`، قاعدهٔ `orders.flagOf`):
 *   روشن ⇒ سپردن به پیکِ دکان (`shop_courier`) کدِ تحویل می‌سازد، حتی اگر دکان نخواسته باشد، وقتی
 *     ۱) نقدِ سفارش هنگامِ تحویل گرفته می‌شود (`expectedCollectMinor > 0`)، یا
 *     ۲) جمعِ سفارش از آستانهٔ `shop.delivery_code_rule.min_total_minor` کم‌تر نیست.
 *   خاموش ⇒ همان رفتارِ پیشین: کد فقط با `requireCode` (و همیشه در دریافتِ حضوری).
 * آستانه عددِ صحیحِ ≥ ۱ به واحدِ خرد است؛ نبود، ۰ یا نامعتبر ⇒ قاعدهٔ «گران‌قیمت» خاموش
 * (آستانهٔ پیش‌فرض تصمیمِ صاحب‌کار است). شرکتِ بیرونی عوض نمی‌شود: کد را کارمند ثبت می‌کند، نه پیکِ ما.
 * تصمیم فقط در سرور است و زیرِ قفلِ ردیفِ سفارش گرفته می‌شود؛ گوشی و میز چیزی را خاموش نمی‌کنند.
 */
const { many } = require('../db');

const FLAG = 'shop.delivery_code_rule';
const MIN_TOTAL_KEY = 'shop.delivery_code_rule.min_total_minor';
const MAX_MINOR = 100_000_000_000;   //  همان سقفِ پولِ سفارش
const OFF = Object.freeze({ on: false, minTotalMinor: 0 });

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** پرچم و آستانهٔ دکان؛ مقدارِ خودِ دکان بر `all` جلو می‌افتد. */
async function policyOf(shopId) {
  const rows = await many(
    'SELECT scope, key, value FROM live_config WHERE key IN ($1,$2) AND scope IN ($3,$4)',
    [FLAG, MIN_TOTAL_KEY, 'all', String(shopId)]
  );
  const pick = (key) => {
    let v;
    for (const r of rows) if (r.key === key && r.scope === 'all') v = decode(r.value);
    for (const r of rows) if (r.key === key && r.scope !== 'all') v = decode(r.value);
    return v;
  };
  if (pick(FLAG) !== true) return OFF;
  const m = pick(MIN_TOTAL_KEY);
  return { on: true, minTotalMinor: Number.isSafeInteger(m) && m >= 1 && m <= MAX_MINOR ? m : 0 };
}

/**
 * چرا این سپردن کد می‌خواهد؟ `''` یعنی قاعده کدی نمی‌خواهد.
 * @returns {''|'cod'|'high_value'}
 */
function requires(policy, { mode, totalMinor, expectedCollectMinor }) {
  if (!policy || policy.on !== true || mode !== 'shop_courier') return '';
  if (Number(expectedCollectMinor) > 0) return 'cod';
  if (policy.minTotalMinor > 0 && Number(totalMinor) >= policy.minTotalMinor) return 'high_value';
  return '';
}

module.exports = { FLAG, MIN_TOTAL_KEY, OFF, policyOf, requires };
