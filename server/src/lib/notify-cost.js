'use strict';
/**
 * NTF-02 — هزینهٔ هر پیامِ مشتری و سقفِ روزانهٔ هزینهٔ هر دکان و هر کانال (§۳.۱۲: «سقف روزانه هزینه برای هر فروشگاه
 * و هر کانال لازم است»). خالص: نه دیتابیس، نه ساعت؛ کارگرِ `customer-notify` پیش از صدا زدنِ هر کانالِ پولی می‌پرسد.
 *
 *   • واحد میکرودلار (یک‌میلیونیمِ دلار، عددِ صحیح) — بی ممیزِ شناور.
 *   • ⛔ «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳): پیامک از زنجیرهٔ اعلانِ مشتری برداشته شد ⇐ به‌جایش ایمیل (رایگان)؛
 *     قیمت و بخش‌بندیِ پیامک (§۴.۳) و سقفِ ۲٫۲۳ دلاریِ آن با آن رفتند.
 *   • قیمتِ پیش‌فرض: پوش، ایمیل، واتس‌اپ و تلگرام صفر — همان «FREE» ِ زنجیره. هر کدام با
 *     `CUSTOMER_NOTIFY_COST_MICROS_<CHANNEL>` (قیمتِ قالبِ واتس‌اپ با حسابِ واقعی، D-09).
 *   • سقفِ پیش‌فرضِ هر دکان در ۲۴ ساعتِ گذشته برای کانالی که قیمت بگیرد: ۱ دلار.
 *     `CUSTOMER_NOTIFY_DAILY_CAP_MICROS_<CHANNEL>`؛ صفر ⇒ هیچ پیامِ پولی از آن کانال.
 *   • پیامِ بی هزینه هرگز به این سقف نمی‌خورد (سقفِ هر مشتری جدا و همیشگی است: `PER_HOUR`/`PER_DAY`).
 *   • مقدارِ نادرستِ env (منفی، اعشار، متن) ⇒ همان پیش‌فرض.
 */

const CHANNEL_KEYS = Object.freeze({ push: 'PUSH', email: 'EMAIL', whatsapp: 'WHATSAPP', telegram: 'TELEGRAM' });

/** قیمتِ پیش‌فرضِ هر پیام، میکرودلار */
const UNIT_MICROS = Object.freeze({ push: 0, email: 0, whatsapp: 0, telegram: 0 });
/** سقفِ پیش‌فرضِ هزینهٔ هر دکان در ۲۴ ساعت، میکرودلار */
const DAILY_CAP_MICROS = Object.freeze({ push: 1_000_000, email: 1_000_000, whatsapp: 1_000_000, telegram: 1_000_000 });
const DAY_MS = 24 * 60 * 60_000;

function micros(env, name, fallback) {
  const raw = env && env[name];
  if (raw == null || String(raw).trim() === '') return fallback;
  const v = Number(raw);
  return Number.isSafeInteger(v) && v >= 0 ? v : fallback;
}

function unitMicros(channel, env = process.env) {
  if (!Object.prototype.hasOwnProperty.call(UNIT_MICROS, channel)) return 0;
  return micros(env, `CUSTOMER_NOTIFY_COST_MICROS_${CHANNEL_KEYS[channel]}`, UNIT_MICROS[channel]);
}

function capMicros(channel, env = process.env) {
  if (!Object.prototype.hasOwnProperty.call(DAILY_CAP_MICROS, channel)) return 0;
  return micros(env, `CUSTOMER_NOTIFY_DAILY_CAP_MICROS_${CHANNEL_KEYS[channel]}`, DAILY_CAP_MICROS[channel]);
}

/** هزینهٔ یک پیام از این کانال با همین متن (همهٔ کانال‌های امروز هزینهٔ ثابتِ هر پیام دارند) */
function costMicros(channel, text, env = process.env) {   // eslint-disable-line no-unused-vars
  return unitMicros(channel, env);
}

/**
 * این پیام فرستاده شود؟ [spentMicros] هزینهٔ پیام‌های پذیرفتهٔ همین دکان و همین کانال در ۲۴ ساعتِ گذشته.
 * @returns {{ ok: boolean, costMicros: number, capMicros: number }}
 */
function allows(channel, text, spentMicros, env = process.env) {
  const cost = costMicros(channel, text, env);
  const cap = capMicros(channel, env);
  const spent = Math.max(0, Number(spentMicros) || 0);
  return { ok: cost === 0 || spent + cost <= cap, costMicros: cost, capMicros: cap };
}

/**
 * هزینهٔ ثبت‌شدهٔ پیام‌های پذیرفته از ردیف‌های `tried` ِ صندوقِ پیام. ردیفِ پیش از NTF-02 (بی `costMicros`)
 * با قیمتِ امروزِ یک پیام شمرده می‌شود — نه صفر. ردیفِ کهنهٔ کانالِ برداشته‌شدهٔ پیامک دیگر شمرده نمی‌شود.
 */
function spentOf(triedLists, channel, env = process.env) {
  let sum = 0;
  for (const list of triedLists) {
    for (const t of Array.isArray(list) ? list : []) {
      if (!t || t.channel !== channel || t.result !== 'accepted') continue;
      const c = Number(t.costMicros);
      sum += Number.isSafeInteger(c) && c >= 0 ? c : unitMicros(channel, env);
    }
  }
  return sum;
}

module.exports = { UNIT_MICROS, DAILY_CAP_MICROS, DAY_MS, unitMicros, capMicros, costMicros, allows, spentOf };
