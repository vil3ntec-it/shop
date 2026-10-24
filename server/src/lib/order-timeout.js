'use strict';
/**
 * ORD-05 — زمان‌سنجِ قبول: سفارشِ تازهٔ ویترین که دکان در ۱۵ دقیقه به آن
 * پاسخ ندهد، خودکار «ردشده» (`auto_rejected`، دلیلِ `timeout`) می‌شود و مشتری
 * همان را در پیگیریِ سفارش می‌بیند (رویدادِ وضعیت با بازیگرِ «سیستم»).
 *
 * ── قاعده (تصمیمِ هماهنگ‌کننده؛ صاحب‌کار می‌تواند عوضش کند) ─────────────
 *   • فقط سفارشِ **ویترین**، و فقط اگر دکان **همان لحظهٔ ثبت** سفارش می‌پذیرفت
 *     (`orderAcceptance = accepting` ِ lib/storefront). سفارشی که با «بعداً»
 *     (`later`) یا «با تأخیر» (`delayed`) گرفته شد هرگز خودکار رد نمی‌شود.
 *   • مهلت ثابت است: ۱۵ دقیقه از ثبت (`ACCEPT_TIMEOUT_MS`).
 *   • مهلت روی خودِ سفارش مُهر می‌خورد (`accept_deadline_at`، مهاجرتِ ۱۲۱)،
 *     چون «می‌پذیرفت یا نه» را بعداً نمی‌شود بازسازی کرد.
 *
 * ── ایمنی ───────────────────────────────────────────────────────────────
 *   • گذار فقط از راهِ `orders.transition` با `from: 'new'` و بازیگرِ سیستم
 *     (`SYSTEM_ONLY` ِ order-states). ردیف `FOR UPDATE` قفل می‌شود؛ تکرارِ همان
 *     گذار «unchanged» است و رویدادِ دوم نمی‌سازد ⇒ دو نمونهٔ سرور که هم‌زمان
 *     جاروب کنند، هر سفارش را فقط یک بار رد می‌کنند.
 *   • سفارشی که پیش از رسیدنِ جاروب پذیرفته/لغو شد ⇒ `status_changed` و رد نمی‌شود.
 *   • مُهر فقط یک بار (`accept_deadline_at IS NULL`): تکرارِ کلیدِ یکتایی چیزی را
 *     جابه‌جا نمی‌کند.
 */
const { one, many, now } = require('../db');

const ACCEPT_TIMEOUT_MS = 15 * 60 * 1000;
const TICK_MS = 60 * 1000;
const BATCH = 100;
const SYSTEM = Object.freeze({ type: 'system', id: '' });

/**
 * پس از ثبتِ سفارشِ تازهٔ ویترین: اگر دکان همان لحظه می‌پذیرد، مهلت را مُهر می‌زند.
 * @returns {Promise<number|null>} مهلت یا null (بی زمان‌سنج)
 */
async function arm(sf, orderId, { at = now() } = {}) {
  const st = await require('./storefront').statusOf(sf, at);
  if (st.orderAcceptance !== 'accepting') return null;
  const r = await one(
    `UPDATE online_orders SET accept_deadline_at = created_at + $3
      WHERE id = $1 AND shop_id = $2 AND source = 'storefront' AND status = 'new' AND accept_deadline_at IS NULL
      RETURNING accept_deadline_at`,
    [String(orderId || ''), sf.shop_id, ACCEPT_TIMEOUT_MS]
  );
  return r ? Number(r.accept_deadline_at) : null;
}

/** سفارش‌های تازه‌ای که مهلتشان گذشته ⇒ auto_rejected. خروجی: شمارِ ردشده‌ها. */
async function sweep(at = now()) {
  const orders = require('./orders');
  const due = await many(
    `SELECT id, shop_id FROM online_orders
      WHERE status = 'new' AND accept_deadline_at IS NOT NULL AND accept_deadline_at <= $1
      ORDER BY accept_deadline_at LIMIT $2`,
    [at, BATCH]
  );
  let rejected = 0;
  for (const o of due) {
    try {
      const out = await orders.transition(o.shop_id, o.id, { to: 'auto_rejected', from: 'new', reason: 'timeout' }, SYSTEM);
      if (!out.unchanged) rejected++;
    } catch (err) {
      //  هم‌زمان پذیرفته/لغو شد — درست همان چیزی که باید بماند
      if (err && (err.code === 'status_changed' || err.code === 'invalid_transition')) continue;
      console.error('[order-timeout]', err && err.message);
    }
  }
  return rejected;
}

let timer = null;
let sweeping = false;
async function tick() {
  if (sweeping) return 0;
  sweeping = true;
  try { return await sweep(); } finally { sweeping = false; }
}
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[order-timeout]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = { ACCEPT_TIMEOUT_MS, arm, sweep, start, stop };
