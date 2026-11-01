'use strict';
/**
 * NTF-02 — هزینهٔ هر پیامِ مشتری و سقفِ روزانهٔ هزینهٔ هر دکان و هر کانال (§۳.۱۲: «سقف روزانه هزینه برای هر فروشگاه
 * و هر کانال لازم است، همراه با محافظت در برابر سوءاستفاده (SMS pumping)»). خالص: نه دیتابیس، نه ساعت؛ کارگرِ
 * `customer-notify` پیش از صدا زدنِ هر کانالِ پولی می‌پرسد.
 *
 *   • واحد میکرودلار (یک‌میلیونیمِ دلار، عددِ صحیح) — بی ممیزِ شناور.
 *   • پیامک بخش‌به‌بخش (§۴.۳): متنِ ASCII ِ چاپی تا ۱۶۰ نویسه یک بخش و بیشتر ۱۵۳ در هر بخش (نویسه‌های دوتاییِ GSM
 *     — `^{}\[~]|` — دو شمرده می‌شوند)؛ هر متنِ دیگر (دری، پشتو) یونیکد است: تا ۷۰ واحدِ UTF-16 یک بخش، بیشتر ۶۷.
 *   • قیمتِ پیش‌فرض: پیامک ۰٫۴۴۶ دلار هر بخش (Twilio به ‎+93، §۳.۱۲ و §۴.۳)؛ پوش، واتس‌اپ و تلگرام صفر — همان
 *     «FREE» ِ زنجیره. هر کدام با `CUSTOMER_NOTIFY_COST_MICROS_<CHANNEL>` (قیمتِ قالبِ واتس‌اپ با حسابِ واقعی، D-09).
 *   • سقفِ پیش‌فرضِ هر دکان در ۲۴ ساعتِ گذشته (D-18، پیشنهاد با دلیل): پیامک ۲٫۲۳ دلار = ۵ بخش — پیامک فقط رویدادِ
 *     فوری است و دکانِ کوچک در روز چند سفارشِ آنلاین دارد؛ ماهانه حداکثر حدودِ ۶۷ دلار برای هر دکان. کانالِ دیگری که
 *     قیمت بگیرد: ۱ دلار. `CUSTOMER_NOTIFY_DAILY_CAP_MICROS_<CHANNEL>`؛ صفر ⇒ هیچ پیامِ پولی از آن کانال.
 *   • پیامِ بی هزینه هرگز به این سقف نمی‌خورد (سقفِ هر مشتری جدا و همیشگی است: `PER_HOUR`/`PER_DAY`).
 *   • مقدارِ نادرستِ env (منفی، اعشار، متن) ⇒ همان پیش‌فرض.
 */

const CHANNEL_KEYS = Object.freeze({ push: 'PUSH', whatsapp: 'WHATSAPP', telegram: 'TELEGRAM', sms: 'SMS' });

/** قیمتِ پیش‌فرضِ هر پیام (پیامک: هر بخش)، میکرودلار */
const UNIT_MICROS = Object.freeze({ push: 0, whatsapp: 0, telegram: 0, sms: 446_000 });
/** سقفِ پیش‌فرضِ هزینهٔ هر دکان در ۲۴ ساعت، میکرودلار */
const DAILY_CAP_MICROS = Object.freeze({ push: 1_000_000, whatsapp: 1_000_000, telegram: 1_000_000, sms: 2_230_000 });
const DAY_MS = 24 * 60 * 60_000;

/** نویسه‌های GSM که در پیامکِ لاتین دو جا می‌گیرند */
const GSM_DOUBLE = /[\^{}\\[~\]|]/g;

function micros(env, name, fallback) {
  const raw = env && env[name];
  if (raw == null || String(raw).trim() === '') return fallback;
  const v = Number(raw);
  return Number.isSafeInteger(v) && v >= 0 ? v : fallback;
}

/** شمارِ بخشِ پیامک برای همین متن (متنِ خالی ⇒ یک بخش) */
function segments(text) {
  const s = String(text == null ? '' : text);
  if (/^[\x20-\x7e\r\n]*$/.test(s)) {
    const len = s.length + (s.match(GSM_DOUBLE) || []).length;
    return len <= 160 ? 1 : Math.ceil(len / 153);
  }
  return s.length <= 70 ? 1 : Math.ceil(s.length / 67);
}

function unitMicros(channel, env = process.env) {
  if (!Object.prototype.hasOwnProperty.call(UNIT_MICROS, channel)) return 0;
  return micros(env, `CUSTOMER_NOTIFY_COST_MICROS_${CHANNEL_KEYS[channel]}`, UNIT_MICROS[channel]);
}

function capMicros(channel, env = process.env) {
  if (!Object.prototype.hasOwnProperty.call(DAILY_CAP_MICROS, channel)) return 0;
  return micros(env, `CUSTOMER_NOTIFY_DAILY_CAP_MICROS_${CHANNEL_KEYS[channel]}`, DAILY_CAP_MICROS[channel]);
}

/** هزینهٔ یک پیام از این کانال با همین متن */
function costMicros(channel, text, env = process.env) {
  const unit = unitMicros(channel, env);
  return channel === 'sms' ? unit * segments(text) : unit;
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
 * با قیمتِ امروزِ یک پیام/یک بخش شمرده می‌شود — نه صفر.
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

module.exports = { UNIT_MICROS, DAILY_CAP_MICROS, DAY_MS, segments, unitMicros, capMicros, costMicros, allows, spentOf };
