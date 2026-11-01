'use strict';
/**
 * S10 — سیاستِ کارمزدِ سامانه و کمیسیونِ بازار روی سفارشِ مشتری (مهاجرتِ ۱۳۰).
 *
 * تصمیمِ صاحب محصول (۱۴۰۵/۰۸/۰۸): «فعلاً سامانه از سفارشِ آنلاین هیچ نمی‌گیرد، ولی کدش باشد تا بعداً روشن شود.»
 * پس همهٔ نرخ‌ها پیش‌فرض ۰ دارند و تا کسی در «تنظیماتِ زنده» عددی نگذارد، هر دو ستون ۰ می‌مانند.
 *
 *   کلیدهای live_config (scope ِ `all`؛ scope ِ خودِ دکان بر `all` جلو می‌افتد، همان قاعدهٔ `orders.flagOf`):
 *     market.platform_fee_bps           کارمزدِ سامانه، صدمِ درصد (۱۰۰ = ۱٪). ۰ تا ۱۰٬۰۰۰.
 *     market.platform_fee_fixed_minor   مبلغِ ثابتِ کارمزد، یک‌صدم. ۰ تا MAX_FIXED_MINOR.
 *     market.commission_bps             کمیسیونِ بازار، صدمِ درصد. ۰ تا ۱۰٬۰۰۰.
 *     market.commission_fixed_minor     مبلغِ ثابتِ کمیسیون، یک‌صدم. ۰ تا MAX_FIXED_MINOR.
 *   مقدارِ نامعتبر (نه عددِ صحیحِ امن، منفی، یا بیرون از بازه) ⇒ ۰، یعنی نادیده و بی اثر؛ سفارش را نمی‌شکند.
 *
 *   قاعدهٔ حساب (همه با عددِ صحیح، BigInt، بی اعشار):
 *     پایه      = max(0, subtotalMinor − discountMinor)   ← ارزشِ کالا پس از تخفیف؛ هزینهٔ رساندن جزوِ پایه نیست.
 *     سهمِ نرخ  = floor((پایه × bps + 5000) / 10000)       ← گرد کردن «نیم به بالا» روی یک‌صدم.
 *     کارمزد    = min(سهمِ نرخ + ثابت، totalMinor)
 *     کمیسیون   = min(سهمِ نرخ + ثابت، totalMinor − کارمزد)
 *   پس هیچ‌کدام منفی نیست و جمعِ هر دو هرگز از مبلغِ کلِ سفارش بیشتر نمی‌شود.
 *
 *   کجا: فقط سفارشِ مشتری (`channel` ناتهی؛ امروز ویترین)، نه سفارشِ آزمایشی و نه سفارشِ دستیِ کارمند.
 *   کی: یک بار، در همان تراکنشِ `orders.create`. عوض شدنِ تنظیمات بعداً هیچ سفارشِ ثبت‌شده‌ای را بازنویسی
 *   نمی‌کند؛ تکرارِ همان کلیدِ یکتایی هم همان سفارشِ قبلی را برمی‌گرداند و دوباره حساب نمی‌کند.
 *   هیچ گزارش یا جمعی این دو ستون را نمی‌خواند و در پاسخ‌ها هم بیرون نمی‌روند.
 */

const KEYS = Object.freeze({
  platformFeeBps: 'market.platform_fee_bps',
  platformFeeFixedMinor: 'market.platform_fee_fixed_minor',
  commissionBps: 'market.commission_bps',
  commissionFixedMinor: 'market.commission_fixed_minor',
});
const MAX_BPS = 10_000;
const MAX_FIXED_MINOR = 100_000_000;   // یک میلیون افغانی — همان سقفِ `MAX_FEE_MINOR` ِ سفارش
const ZERO = Object.freeze({ platformFeeMinor: 0, commissionMinor: 0 });

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** عددِ صحیحِ امن در [0, max]؛ هر چیزِ دیگر ⇒ ۰. */
function clean(v, max) {
  return typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 && v <= max ? v : 0;
}

/** سیاستِ همین لحظهٔ یک دکان؛ `run(sql, params)` همان تراکنشِ ساختِ سفارش است. */
async function policyIn(run, shopId) {
  const keys = Object.values(KEYS);
  const r = await run(
    'SELECT scope, key, value FROM live_config WHERE key = ANY($1) AND scope IN ($2,$3)',
    [keys, 'all', String(shopId)]
  );
  const v = {};
  for (const row of r.rows) if (row.scope === 'all') v[row.key] = decode(row.value);
  for (const row of r.rows) if (row.scope !== 'all') v[row.key] = decode(row.value);
  return {
    platformFeeBps: clean(v[KEYS.platformFeeBps], MAX_BPS),
    platformFeeFixedMinor: clean(v[KEYS.platformFeeFixedMinor], MAX_FIXED_MINOR),
    commissionBps: clean(v[KEYS.commissionBps], MAX_BPS),
    commissionFixedMinor: clean(v[KEYS.commissionFixedMinor], MAX_FIXED_MINOR),
  };
}

/** سهمِ نرخ با گرد کردنِ «نیم به بالا»: floor((base × bps + 5000) / 10000). */
function share(base, bps) {
  return (base * BigInt(bps) + 5000n) / 10000n;
}

const min = (a, b) => (a < b ? a : b);

/** حسابِ خالص (بی دیتابیس) — `policy` از `policyIn`؛ مبلغ‌ها عددِ صحیحِ نامنفیِ سفارش. */
function compute(policy, { subtotalMinor, discountMinor, totalMinor }) {
  const p = {
    platformFeeBps: clean(policy && policy.platformFeeBps, MAX_BPS),
    platformFeeFixedMinor: clean(policy && policy.platformFeeFixedMinor, MAX_FIXED_MINOR),
    commissionBps: clean(policy && policy.commissionBps, MAX_BPS),
    commissionFixedMinor: clean(policy && policy.commissionFixedMinor, MAX_FIXED_MINOR),
  };
  const total = BigInt(Math.max(0, Number(totalMinor) || 0));
  const diff = BigInt(Number(subtotalMinor) || 0) - BigInt(Number(discountMinor) || 0);
  const base = diff > 0n ? diff : 0n;
  const platform = min(share(base, p.platformFeeBps) + BigInt(p.platformFeeFixedMinor), total);
  const commission = min(share(base, p.commissionBps) + BigInt(p.commissionFixedMinor), total - platform);
  return { platformFeeMinor: Number(platform), commissionMinor: Number(commission) };
}

/**
 * مبلغ‌های ستون‌های ۱۳۰ برای یک سفارشِ تازه. سفارشِ کارمند (`channel` تهی) یا آزمایشی ⇒ ۰ و بی پرسش.
 */
async function forNewOrder(run, shopId, { channel, isTest, subtotalMinor, discountMinor, totalMinor }) {
  if (!channel || isTest) return ZERO;
  return compute(await policyIn(run, shopId), { subtotalMinor, discountMinor, totalMinor });
}

module.exports = { KEYS, MAX_BPS, MAX_FIXED_MINOR, policyIn, compute, forNewOrder };
