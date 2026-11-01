'use strict';
/**
 * ORD-05 — زمان‌سنجِ قبول: سفارشِ تازهٔ ویترین که دکان در ۱۵ دقیقه به آن
 * پاسخ ندهد، خودکار «ردشده» (`auto_rejected`، دلیلِ `timeout`) می‌شود و مشتری
 * همان را در پیگیریِ سفارش می‌بیند (رویدادِ وضعیت با بازیگرِ «سیستم»).
 *
 * ── قاعده (تصمیمِ هماهنگ‌کننده؛ صاحب‌کار می‌تواند عوضش کند) ─────────────
 *   • فقط سفارشِ **ویترین**، و فقط اگر دکان **همان لحظهٔ ثبت** سفارش می‌پذیرفت
 *     (`orderAcceptance = accepting` ِ lib/storefront، با ساعتِ `created_at`).
 *     سفارشی که با «بعداً» (`later`) یا «با تأخیر» (`delayed`) گرفته شد هرگز
 *     خودکار رد نمی‌شود.
 *   • سفارشِ **زمان‌بندی‌شده** (`scheduled_for` دارد) هم «بعداً» است و رد نمی‌شود
 *     (تصمیمِ هماهنگ‌کننده).
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
 *   • سفارشی که در جاروب خطای پیش‌بینی‌نشده می‌دهد صف را نمی‌گیرد: مهلتش
 *     RETRY_MS جلو می‌رود و پس از MAX_FAILURES بار، زمان‌سنجش برداشته می‌شود
 *     (سفارش «تازه» می‌ماند، با یک خطِ لاگ و یک ردیفِ سابقه).
 *   • هر ردِ خودکار یک ردیفِ سابقه (`order.auto_rejected`) دارد و به دکان از همان
 *     راهِ اعلانِ سفارش (پرچمِ `shop.order_notify`، lib/order-notify + push) خبر
 *     می‌رود؛ پرچم خاموش ⇒ فقط سابقه. خطای اعلان هرگز رد را پس نمی‌گیرد.
 */
const { query, one, many, now } = require('../db');

const ACCEPT_TIMEOUT_MS = 15 * 60 * 1000;
const TICK_MS = 60 * 1000;
const BATCH = 100;
const RETRY_MS = 5 * 60 * 1000;
const MAX_FAILURES = 5;
const failures = new Map();   // orderId ⇒ شمارِ خطاهای پیاپی (فقط در همین نمونه)
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);
const SYSTEM = Object.freeze({ type: 'system', id: '' });

/**
 * پس از ثبتِ سفارشِ تازهٔ ویترین: اگر دکان در لحظهٔ ساختِ سفارش (`at`) می‌پذیرفت،
 * مهلت را مُهر می‌زند. سفارشِ زمان‌بندی‌شده نه.
 * @returns {Promise<number|null>} مهلت یا null (بی زمان‌سنج)
 */
async function arm(sf, orderId, { at = now() } = {}) {
  const st = await require('./storefront').statusOf(sf, Number(at) || now());
  if (st.orderAcceptance !== 'accepting') return null;
  const r = await one(
    `UPDATE online_orders SET accept_deadline_at = created_at + $3
      WHERE id = $1 AND shop_id = $2 AND source = 'storefront' AND status = 'new'
        AND scheduled_for IS NULL AND accept_deadline_at IS NULL
      RETURNING accept_deadline_at`,
    [String(orderId || ''), sf.shop_id, ACCEPT_TIMEOUT_MS]
  );
  return r ? Number(r.accept_deadline_at) : null;
}

/** اعلانِ «سفارشی بی‌پاسخ ماند و رد شد» به دکان — همان پرچم، گیرنده‌ها و پوشِ اعلانِ سفارش. */
async function notifyShop(o) {
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(o.shop_id))) return { sent: 0, skipped: 'flag_off' };
    const onlyUserIds = await on.recipientIds(o.shop_id);
    const no = Number(o.order_no);
    return (await require('./push').sendTo({ shopId: o.shop_id, app: 'shop', onlyUserIds }, {
      title: `سفارشِ بی‌پاسخ رد شد — شمارهٔ ${faNum(no)}`,
      body: 'تا ۱۵ دقیقه کسی پاسخ نداد؛ سفارش خودکار رد شد و مشتری خبردار است.',
      channel: on.CHANNEL,
      data: { type: 'order_auto_rejected', orderId: o.id, orderNo: String(no), shopId: o.shop_id },
    })) || {};
  } catch (err) {
    console.error('[order-timeout] اعلان', err && err.message);
    return { sent: 0, error: String((err && err.message) || err) };
  }
}

/** سفارشِ خطادار: مهلت جلو می‌رود؛ پس از MAX_FAILURES زمان‌سنجش برداشته می‌شود. */
async function deferFailed(o, at, err) {
  const n = (failures.get(o.id) || 0) + 1;
  console.error('[order-timeout]', o.id, err && err.message);
  if (n >= MAX_FAILURES) {
    failures.delete(o.id);
    await query(`UPDATE online_orders SET accept_deadline_at = NULL WHERE id = $1 AND status = 'new'`, [o.id]);
    await require('./audit').log({
      shopId: o.shop_id, actorType: 'system', action: 'order.auto_reject_failed',
      targetType: 'online_order', targetId: o.id, detail: { attempts: n },
    }).catch(() => {});
    return;
  }
  failures.set(o.id, n);
  await query(`UPDATE online_orders SET accept_deadline_at = $2 WHERE id = $1 AND status = 'new'`, [o.id, at + RETRY_MS]);
}

/** سفارش‌های تازه‌ای که مهلتشان گذشته ⇒ auto_rejected. خروجی: شمارِ ردشده‌ها. */
async function sweep(at = now()) {
  const orders = require('./orders');
  const due = await many(
    `SELECT id, shop_id, order_no FROM online_orders
      WHERE status = 'new' AND accept_deadline_at IS NOT NULL AND accept_deadline_at <= $1
      ORDER BY accept_deadline_at LIMIT $2`,
    [at, BATCH]
  );
  let rejected = 0;
  for (const o of due) {
    let out;
    try {
      out = await orders.transition(o.shop_id, o.id, { to: 'auto_rejected', from: 'new', reason: 'timeout' }, SYSTEM);
    } catch (err) {
      //  هم‌زمان پذیرفته/لغو شد — درست همان چیزی که باید بماند
      if (err && (err.code === 'status_changed' || err.code === 'invalid_transition')) continue;
      await deferFailed(o, at, err).catch((e) => console.error('[order-timeout]', e.message));
      continue;
    }
    failures.delete(o.id);
    if (out.unchanged) continue;   // نمونهٔ دیگری همین حالا رد کرد
    rejected++;
    await require('./audit').log({
      shopId: o.shop_id, actorType: 'system', action: 'order.auto_rejected',
      targetType: 'online_order', targetId: o.id,
      detail: { orderNo: Number(o.order_no), reason: 'timeout', timeoutMinutes: ACCEPT_TIMEOUT_MS / 60000 },
    }).catch(() => {});
    await notifyShop(o);
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
  const run = require('./job-beat').wrap('order-timeout', TICK_MS, tick);   //  پنلِ مرکزی ۴.۹
  timer = setInterval(() => run().catch((err) => console.error('[order-timeout]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = { ACCEPT_TIMEOUT_MS, RETRY_MS, MAX_FAILURES, arm, sweep, start, stop };
