'use strict';
/**
 * پنلِ مرکزی ۴.۶ و ۴.۷ — «کدام سفارش گیر کرده یا با مأموریتِ تحویلش نمی‌خواند؟» (خالص؛ نه دیتابیس، نه ساعت).
 *
 * ورودی یک ردیف = سفارشِ آنلاین + **آخرین** مأموریتِ تحویلش (بیشترین `attempt_no`)؛ خروجی فهرستِ کدها.
 * هیچ‌کدام حکم نیست و چیزی را عوض نمی‌کند — فقط برای دیدنِ مدیر. قاعده‌ها از خودِ دو ماشینِ وضعیت می‌آیند:
 * `lib/order-states.js` (سفارش) و `lib/delivery-states.js` (مأموریت؛ `orderSteps` اثرِ هر گذار بر سفارش را
 * می‌گوید) — پس «ناهم‌خوان» یعنی حالتی که این دو ماشین با هم هرگز به آن نمی‌رسند.
 *
 *   گیر کرده (stuck) — آستانه‌ها دقیقه‌اند و تنظیم‌پذیر (`console.delivery_thresholds` در live_config):
 *     accept_overdue      سفارشِ «تازه» بیش از acceptMin پذیرفته نشده
 *     dispatch_overdue    سفارشِ ارسالیِ «بسته‌بندی‌شده» بی مأموریتِ زنده، بیش از dispatchMin
 *     courier_unaccepted  مأموریتِ «درخواست‌شده» که پیک بیش از courierAcceptMin قبول نکرده
 *     pickup_overdue      مأموریتِ «سپرده» که بسته بیش از pickupMin از دکان برداشته نشده
 *     delivery_overdue    بسته در راه و بیش از inDeliveryMin هیچ وضعیتِ تازه‌ای نیامده
 *     failed_unresolved   ناموفق/زمان‌بندیِ دوباره بیش از failedMin بی تصمیم
 *     recall_pending      فراخوانیِ پس از حرکت بیش از recallMin بی نتیجه
 *   ناهم‌خوان (mismatch):
 *     delivered_not_reflected  مأموریت «تحویل شد»، سفارش نه
 *     departed_not_reflected   بسته در راه، سفارش «در راه» نیست
 *     failed_not_reflected     مأموریت ناموفق، سفارش نه
 *     returned_not_reflected   بسته برگشت، سفارش «برگشته» نیست
 *     closed_order_live_mission سفارش بسته/لغو، مأموریت هنوز زنده
 *     cod_not_recorded         پیک نقد گرفت، نقدِ سفارش هنوز «گرفتنی»
 *   اختلافِ پول (discrepancy):
 *     cash_review              گرفته ≠ موردِ انتظار (پیک دلیل داده؛ §۲۰.۶)
 *     payment_unconfirmed      پرداختِ آنلاین و سفارشِ تمام‌شده بی «پرداخت‌شده» (امروز پرداختِ آنلاین نیست — D-08)
 */

const KEY = 'console.delivery_thresholds';

/** پیش‌فرض‌ها (دقیقه). ⚠️ پیشنهادِ آغازین‌اند، نه قاعدهٔ کسب‌وکار؛ صاحبِ سامانه از پنل عوضشان می‌کند. */
const DEFAULT_THRESHOLDS = Object.freeze({
  acceptMin: 30,
  dispatchMin: 30,
  courierAcceptMin: 15,
  pickupMin: 30,
  inDeliveryMin: 120,
  failedMin: 240,
  recallMin: 60,
});
const MIN_MINUTES = 1;
const MAX_MINUTES = 7 * 24 * 60;

const LIVE = Object.freeze(['requested', 'assigned', 'picked_up', 'moving', 'arrived', 'failed']);
const DEPARTED = Object.freeze(['picked_up', 'moving', 'arrived']);
const AFTER_SALE = Object.freeze(['return_requested', 'refunded_partial', 'refunded_full']);
/** سفارشی که دیگر مأموریتِ زنده نباید داشته باشد. */
const ORDER_CLOSED = Object.freeze([
  'rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'delivered', 'picked_up', 'restocked',
  'refunded_partial', 'refunded_full',
]);
/** وضعیت‌های «هنوز کار دارد» ِ سفارش — بیرون از پنجرهٔ زمانی هم دیده می‌شوند. */
const ORDER_OPEN = Object.freeze([
  'new', 'accepted', 'picking', 'packed', 'ready_for_pickup', 'out_for_delivery', 'delivery_failed',
  'retry_scheduled', 'returned_to_store', 'return_requested',
]);

const STUCK = Object.freeze(['accept_overdue', 'dispatch_overdue', 'courier_unaccepted', 'pickup_overdue',
  'delivery_overdue', 'failed_unresolved', 'recall_pending']);
const MISMATCH = Object.freeze(['delivered_not_reflected', 'departed_not_reflected', 'failed_not_reflected',
  'returned_not_reflected', 'closed_order_live_mission', 'cod_not_recorded']);
const DISCREPANCY = Object.freeze(['cash_review', 'payment_unconfirmed']);

/**
 * آستانه‌ها از مقدارِ live_config (هر چیزی) — فقط کلیدهای شناخته، فقط عددِ صحیحِ ۱ تا ۱۰٬۰۸۰؛ بقیه
 * پیش‌فرض. `source` می‌گوید هر کدام از کجا آمد (نامعتبر ⇒ «default»، نه حدس).
 */
function normalizeThresholds(raw) {
  const v = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : {};
  const out = {};
  const source = {};
  for (const [k, def] of Object.entries(DEFAULT_THRESHOLDS)) {
    const x = v[k];
    if (typeof x === 'number' && Number.isInteger(x) && x >= MIN_MINUTES && x <= MAX_MINUTES) {
      out[k] = x;
      source[k] = 'live_config';
    } else {
      out[k] = def;
      source[k] = 'default';
    }
  }
  return { values: out, source };
}

/** بدنهٔ نوشتن از پنل: همهٔ کلیدها لازم نیست، ولی هر کلیدِ آمده باید معتبر باشد (نه بی‌صدا دور ریخته). */
function validateThresholds(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { ok: false, error: 'not_object' };
  const out = {};
  for (const [k, x] of Object.entries(raw)) {
    if (!(k in DEFAULT_THRESHOLDS)) return { ok: false, error: 'unknown_key', key: k };
    if (typeof x !== 'number' || !Number.isInteger(x) || x < MIN_MINUTES || x > MAX_MINUTES) {
      return { ok: false, error: 'bad_value', key: k };
    }
    out[k] = x;
  }
  if (!Object.keys(out).length) return { ok: false, error: 'empty' };
  return { ok: true, value: out };
}

const N = (x) => (x === null || x === undefined || x === '' ? null : Number(x));

/**
 * کدهای یک ردیف. `r`: ستون‌های سفارش (`status`، `fulfillment`، `payment_method`، `payment_status`، `cod_status`،
 * `created_at`، `updated_at`) و آخرین مأموریت با پیشوندِ `a_` (`a_status`، `a_mode`، `a_updated_at`،
 * `a_cash_review`، `a_cash_collected_minor`، `a_recall_outcome`، `a_recall_requested_at`) — یا بی آن.
 */
function classify(r, thresholds, at) {
  const th = thresholds || DEFAULT_THRESHOLDS;
  const over = (t, min) => {
    const n = N(t);
    return n !== null && n > 0 && at - n > min * 60_000;
  };
  const s = r.status;
  const a = r.a_status || null;
  const live = a !== null && LIVE.includes(a);
  const stuck = [];
  const mismatch = [];
  const discrepancy = [];

  //  ── گیر کرده ──
  if (s === 'new' && over(r.created_at, th.acceptMin)) stuck.push('accept_overdue');
  if (r.fulfillment === 'delivery' && s === 'packed' && !live && over(r.updated_at, th.dispatchMin)) stuck.push('dispatch_overdue');
  if (a === 'requested' && over(r.a_updated_at, th.courierAcceptMin)) stuck.push('courier_unaccepted');
  if (a === 'assigned' && over(r.a_updated_at, th.pickupMin)) stuck.push('pickup_overdue');
  if (DEPARTED.includes(a) && over(r.a_updated_at, th.inDeliveryMin)) stuck.push('delivery_overdue');
  if ((a === 'failed' && over(r.a_updated_at, th.failedMin))
      || ((s === 'delivery_failed' || s === 'retry_scheduled') && !(live && a !== 'failed') && over(r.updated_at, th.failedMin))) {
    stuck.push('failed_unresolved');
  }
  if (r.a_recall_outcome === 'pending' && over(r.a_recall_requested_at, th.recallMin)) stuck.push('recall_pending');

  //  ── ناهم‌خوانیِ سفارش و مأموریت (فقط وقتی مأموریتی هست) ──
  if (a === 'delivered') {
    const done = r.a_mode === 'pickup' ? ['picked_up', ...AFTER_SALE] : ['delivered', ...AFTER_SALE];
    if (!done.includes(s)) mismatch.push('delivered_not_reflected');
    if (N(r.a_cash_collected_minor) > 0 && r.payment_method === 'cod' && r.cod_status === 'to_collect') {
      mismatch.push('cod_not_recorded');
    }
  }
  if (DEPARTED.includes(a) && s !== 'out_for_delivery') mismatch.push('departed_not_reflected');
  if (a === 'failed' && s !== 'delivery_failed' && s !== 'retry_scheduled') mismatch.push('failed_not_reflected');
  if (a === 'returned' && s !== 'returned_to_store' && s !== 'restocked') mismatch.push('returned_not_reflected');
  if (live && ORDER_CLOSED.includes(s)) mismatch.push('closed_order_live_mission');

  //  ── پول ──
  if (r.a_cash_review === true || r.a_cash_review === 't') discrepancy.push('cash_review');
  if (r.payment_method === 'online' && (s === 'delivered' || s === 'picked_up') && r.payment_status !== 'paid') {
    discrepancy.push('payment_unconfirmed');
  }
  return { stuck, mismatch, discrepancy };
}

/** «جای این سفارش در صفِ تحویل» — یکی، برای شمردن و پالایش. */
function stageOf(r) {
  const a = r.a_status || null;
  if (r.fulfillment !== 'delivery') return null;
  if (a === 'requested' || a === 'assigned') return 'assigned';
  if (DEPARTED.includes(a)) return 'in_delivery';
  if (a === 'failed') return 'failed';
  if (r.status === 'packed' && !(a && LIVE.includes(a))) return 'dispatch';
  if (r.status === 'accepted' || r.status === 'picking') return 'preparing';
  return null;
}

module.exports = {
  KEY, DEFAULT_THRESHOLDS, MIN_MINUTES, MAX_MINUTES, LIVE, DEPARTED, ORDER_CLOSED, ORDER_OPEN,
  STUCK, MISMATCH, DISCREPANCY, normalizeThresholds, validateThresholds, classify, stageOf,
};
