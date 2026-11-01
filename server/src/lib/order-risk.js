'use strict';
/**
 * ریسکِ سفارشِ ویترین — RSK-02 (سقفِ مبلغ و سفارشِ بازِ مشتریِ تازه)، RSK-05 (سفارشِ تکراری) و
 * RSK-03 (سابقهٔ رد و برگشتِ مشتری پیش از ارسال) — §۶.۲۱.
 *
 *   ثبت از ویترین ─▶ orders.create ─▶ order-rate.guard: قفلِ شماره ⇒ سقفِ سرعت (RSK-07) ⇒ قیمت ⇒ checkIn ِ همین‌جا
 *   برنامهٔ دکان ──▶ GET /api/shop/orders/:id/risk ⇒ riskOf: سابقه و نشانه‌های توضیح‌دار
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پیش‌فرض خاموش: بی کلیدِ `shop.order_risk` (دکان یا `all`) هیچ قاعده‌ای اجرا نمی‌شود و
 *     نمای سابقه فقط `{enabled:false}` می‌گوید.
 *   • ⛔ فقط سفارشِ ویترین (مشتری با شمارهٔ تأییدشده). فروشِ حضوری، صندوق و سفارشِ دستیِ کارمند هرگز
 *     از این‌جا رد نمی‌شوند — قاعده فقط در قیمت‌گذارِ ویترین صدا زده می‌شود.
 *   • قاعده سمتِ سرور و درونِ همان تراکنشِ ثبت، پس از قفلِ `order-phone:<شماره>` (`order-rate.check`) ⇒
 *     دو ثبتِ هم‌زمانِ یک شماره پشتِ هم می‌نشینند و تکرار از لای آن‌ها رد نمی‌شود.
 *   • هر رد توضیح دارد: کدِ پایدار (`duplicate_order`، `new_customer_limit`)، `data.reason`، عددِ سقف و
 *     راهِ بعدی (منتظر ماندن یا تماس با دکان)؛ تکرارِ همان کلیدِ یکتایی پیش از این‌جا پاسخ می‌گیرد.
 *   • «مشتریِ تازه» = این شماره در همین دکان هنوز هیچ سفارشِ تحویل‌شده‌ای ندارد (از هر منبع).
 *   • «تکراری» = همان شماره، همان روشِ تحویل و نشانی (پینِ گرد به ۴ رقم، ناحیه، متن) و همان اقلام و
 *     مقدارها در `duplicateWindowMin` دقیقهٔ اخیر، از سفارشی که رد یا لغو نشده است.
 *   • سیاست (`POLICY_KEY`، شیءِ JSON در «تنظیماتِ زنده»، دکان بر `all` جلو می‌افتد): هر عددِ نامعتبر
 *     همان پیش‌فرض است؛ ۰ = آن قاعده خاموش.
 *   • نمای سابقه فقط همین دکان را می‌شمارد (نه دکان‌های دیگر — حریمِ مشتری) و شماره را برنمی‌گرداند.
 *   • RSK-04 (`lib/risk-lists`): شماره یا پینِ فهرستِ بسته ⇒ ۴۰۳ `order_blocked` با پیامِ خنثی، پیش از هر
 *     قاعدهٔ دیگر؛ شمارهٔ فهرستِ مطمئن از سقفِ مشتریِ تازه رد نمی‌شود (قاعدهٔ تکرار همچنان هست).
 */
const { query, one } = require('../db');
const { ApiError } = require('../middleware/errors');

const FLAG_KEY = 'shop.order_risk';
const POLICY_KEY = 'shop.order_risk.policy';

const DEFAULTS = Object.freeze({
  newCustomerMaxTotalMinor: 1_000_000,   // ۱۰٬۰۰۰ افغانی
  newCustomerMaxOpen: 2,
  duplicateWindowMin: 10,
});
const BOUNDS = Object.freeze({
  newCustomerMaxTotalMinor: 100_000_000_000,
  newCustomerMaxOpen: 100,
  duplicateWindowMin: 24 * 60,
});

/** سفارش رد یا لغو شده ⇒ نه «باز» است، نه «تکراری» حساب می‌شود. */
const DEAD = Object.freeze(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled']);
/** مشتری کالا را گرفته است (برگشت و بازپرداخت هم یعنی پیش‌تر تحویل شده). */
const COMPLETED = Object.freeze(['delivered', 'picked_up', 'return_requested', 'refunded_partial', 'refunded_full']);
const OPEN = Object.freeze(['new', 'accepted', 'picking', 'packed', 'ready_for_pickup', 'out_for_delivery',
  'delivery_failed', 'retry_scheduled']);

// ════════════════════════════════════════════════════════════════════
//  پرچم و سیاست
// ════════════════════════════════════════════════════════════════════

function decode(text) { try { return JSON.parse(text); } catch { return null; } }

/** مقدارِ کلید: `all` و سپس خودِ دکان (دکان جلو می‌افتد). */
async function configIn(run, shopId, key) {
  const rows = (await run(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)', [key, 'all', String(shopId)]
  )).rows;
  const out = [];
  for (const r of rows) if (r.scope === 'all') out.push(decode(r.value));
  for (const r of rows) if (r.scope !== 'all') out.push(decode(r.value));
  return out;
}

async function flagIn(run, shopId) {
  const vals = await configIn(run, shopId, FLAG_KEY);
  return vals.length > 0 && vals[vals.length - 1] === true;
}

/** سیاستِ مؤثر: پیش‌فرض ⇐ `all` ⇐ دکان؛ فقط عددِ صحیحِ درونِ بازه پذیرفته است. */
async function policyIn(run, shopId) {
  const out = { ...DEFAULTS };
  for (const v of await configIn(run, shopId, POLICY_KEY)) {
    if (!v || typeof v !== 'object' || Array.isArray(v)) continue;
    for (const k of Object.keys(DEFAULTS)) {
      const x = v[k];
      if (Number.isSafeInteger(x) && x >= 0 && x <= BOUNDS[k]) out[k] = x;
    }
  }
  return out;
}

const viaDb = (t, p) => query(t, p);

// ════════════════════════════════════════════════════════════════════
//  اثرِ انگشتِ سفارش (RSK-05)
// ════════════════════════════════════════════════════════════════════

function norm(s) {
  return String(s || '').toLowerCase().replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/[\s‌‏‎.,،:;!?؟\-_/\\()]+/g, '');
}

/** همان جا؟ پین (گرد به ۴ رقم ≈ ۱۱ متر)، ناحیه و متن/نشانه — هر کدام که هست. */
function placeKey(fulfillment, address) {
  if (fulfillment !== 'delivery') return 'pickup';
  const a = address && typeof address === 'object' ? address : {};
  const pin = a.lat !== undefined && a.lat !== null && a.lng !== undefined && a.lng !== null
    ? `${Number(a.lat).toFixed(4)},${Number(a.lng).toFixed(4)}` : '';
  const zone = a.zone && typeof a.zone === 'object' ? String(a.zone.id || '') : '';
  return `delivery|${pin}|${zone}|${norm(a.text)}|${norm(a.landmark)}`;
}

/** همان اقلام و همان مقدارها، بی ترتیب. */
function linesKey(lines) {
  return (lines || []).map((l) => `${l.productId || l.product_id}:${Number(l.qtyMilli ?? l.qty_milli)}`).sort().join('|');
}

// ════════════════════════════════════════════════════════════════════
//  قاعده‌ها — درونِ تراکنشِ ثبت
// ════════════════════════════════════════════════════════════════════

const asJson = (x) => (x && typeof x === 'object' ? x : decode(x) || {});

/** سفارشِ زنده‌ای از همین شماره با همان جا و همان اقلام، ساخته‌شده در بازهٔ (from, to) — یا null. */
async function duplicateOf(run, shopId, forms, input, { from, to = Number.MAX_SAFE_INTEGER, excludeId = '' }) {
  const recent = (await run(
    `SELECT id, order_no, fulfillment, address, created_at FROM online_orders
      WHERE customer_phone = ANY($1::text[]) AND source = 'storefront' AND shop_id = $2
        AND created_at > $3 AND created_at < $4 AND id <> $5
        AND NOT is_test AND status <> ALL($6::text[])
      ORDER BY created_at DESC LIMIT 10`,
    [forms, shopId, from, to, excludeId, DEAD]
  )).rows;
  if (!recent.length) return null;
  const place = placeKey(input.fulfillment, input.address);
  const wanted = linesKey(input.lines);
  const same = recent.filter((r) => r.fulfillment === input.fulfillment && placeKey(r.fulfillment, asJson(r.address)) === place);
  if (!same.length) return null;
  const lines = (await run(
    'SELECT order_id, product_id, qty_milli FROM online_order_lines WHERE order_id = ANY($1::text[])', [same.map((r) => r.id)]
  )).rows;
  for (const r of same) {
    if (linesKey(lines.filter((l) => l.order_id === r.id)) === wanted) return r;
  }
  return null;
}

async function countOf(run, shopId, forms, statuses) {
  const r = (await run(
    `SELECT COUNT(*)::int AS n FROM online_orders
      WHERE shop_id = $1 AND customer_phone = ANY($2::text[]) AND NOT is_test AND status = ANY($3::text[])`,
    [shopId, forms, statuses]
  )).rows[0];
  return r.n;
}

function refuse(status, code, message, data) {
  const err = new ApiError(status, code, message);
  err.data = data;
  return err;
}

/**
 * RSK-02 و RSK-05 برای سفارشِ ویترین — `input` همان خروجیِ قیمت‌گذار (`fulfillment`، `address`، `lines`،
 * `totalMinor`). پرچم خاموش ⇒ هیچ. ردِ توضیح‌دار ⇒ ApiError ِ ۴۰۹ با `data`.
 */
async function checkIn(run, shopId, phone, input, { at = Date.now() } = {}) {
  if (!phone || !input || !(await flagIn(run, shopId))) return;
  //  RSK-04: فهرستِ بسته ⇒ ردِ خنثی (نه دلیل، نه این‌که شماره بود یا جا)؛ فهرستِ مطمئن ⇒ بی سقفِ مشتریِ تازه
  const listed = await require('./risk-lists').matchIn(run, shopId, {
    phone, address: input.fulfillment === 'delivery' ? input.address : null, at,
  });
  if (listed.block) {
    throw refuse(403, 'order_blocked',
      'این دکان فعلاً سفارشِ اینترنتی از این شماره یا این نشانی نمی‌پذیرد؛ برای سفارش با خودِ دکان تماس بگیرید', {});
  }
  const policy = await policyIn(run, shopId);
  const forms = require('./order-rate').formsOf(require('./order-rate').canon(phone));

  if (policy.duplicateWindowMin > 0) {
    const windowMs = policy.duplicateWindowMin * 60_000;
    const dup = await duplicateOf(run, shopId, forms, input, { from: at - windowMs });
    if (dup) {
      const retryAfter = Math.max(1, Math.ceil((Number(dup.created_at) + windowMs - at) / 1000));
      const err = refuse(409, 'duplicate_order',
        `همین سفارش (همان اقلام و همان نشانی) چند دقیقه پیش با شمارهٔ ${dup.order_no} ثبت شده است؛ آن را پیگیری کنید، یا اگر دوباره همین را می‌خواهید کمی بعد امتحان کنید یا با دکان تماس بگیرید`,
        { reason: 'same_items_same_place', orderNo: Number(dup.order_no), windowMin: policy.duplicateWindowMin,
          retryAfter, token: require('./storefront').tokenOf(shopId, dup.id) });
      err.retryAfter = retryAfter;
      throw err;
    }
  }

  const capOpen = policy.newCustomerMaxOpen;
  const capTotal = policy.newCustomerMaxTotalMinor;
  if (!capOpen && !capTotal) return;
  if (listed.allow) return;   // مشتریِ مطمئنِ همین دکان (RSK-04) — تکرار همچنان سنجیده شد
  if (await countOf(run, shopId, forms, COMPLETED) > 0) return;   // مشتریِ آشنای همین دکان
  if (capTotal && Number(input.totalMinor) > capTotal) {
    throw refuse(409, 'new_customer_limit',
      'برای نخستین سفارش از این دکان، مبلغِ سفارش از سقفِ مشتریِ تازه بیشتر است؛ سبد را کوچک‌تر کنید یا با دکان تماس بگیرید',
      { reason: 'amount', maxTotalMinor: capTotal });
  }
  if (capOpen) {
    const open = await countOf(run, shopId, forms, OPEN);
    if (open >= capOpen) {
      throw refuse(409, 'new_customer_limit',
        'تا نخستین سفارشِ شما از این دکان تحویل نشده، سفارشِ بازِ بیشتری پذیرفته نمی‌شود؛ منتظر بمانید یا با دکان تماس بگیرید',
        { reason: 'open_orders', maxOpen: capOpen, open });
    }
  }
}

// ════════════════════════════════════════════════════════════════════
//  RSK-03: سابقه و نشانه‌ها برای برنامهٔ دکان
// ════════════════════════════════════════════════════════════════════

/**
 * سابقهٔ همین شماره در همین دکان (بی خودِ این سفارش) و نشانه‌های توضیح‌دار — هر نشانه کد، شدت و عدد
 * دارد، نه حکم: تصمیم با دکان است. سفارشِ دکانِ دیگر ⇒ null.
 */
async function riskOf(shopId, orderId, { at = Date.now() } = {}) {
  const id = String(orderId || '').slice(0, 64);
  const order = await one(
    `SELECT id, source, customer_phone, fulfillment, address, created_at FROM online_orders WHERE shop_id=$1 AND id=$2`,
    [shopId, id]
  );
  if (!order) return null;
  if (!(await flagIn(viaDb, shopId))) return { enabled: false };
  const policy = await policyIn(viaDb, shopId);
  if (!order.customer_phone) return { enabled: true, policy, known: false, history: null, flags: [] };

  const forms = require('./order-rate').formsOf(require('./order-rate').canon(order.customer_phone));
  const rows = (await query(
    `SELECT status, status_reason, COUNT(*)::int AS n, MIN(created_at) AS first_at, MAX(created_at) AS last_at
       FROM online_orders
      WHERE shop_id = $1 AND customer_phone = ANY($2::text[]) AND id <> $3 AND NOT is_test
      GROUP BY status, status_reason`,
    [shopId, forms, order.id]
  )).rows;
  const sum = (pred) => rows.filter(pred).reduce((s, r) => s + r.n, 0);
  const failedEver = (await one(
    `SELECT COUNT(DISTINCT e.order_id)::int AS n FROM online_order_events e
       JOIN online_orders o ON o.id = e.order_id
      WHERE o.shop_id = $1 AND o.customer_phone = ANY($2::text[]) AND o.id <> $3 AND NOT o.is_test
        AND e.kind = 'status' AND e.to_status = 'delivery_failed'`,
    [shopId, forms, order.id]
  )).n;
  const history = {
    orders: sum(() => true),
    completed: sum((r) => COMPLETED.includes(r.status)),
    open: sum((r) => OPEN.includes(r.status)),
    rejectedByShop: sum((r) => r.status === 'rejected' || r.status === 'auto_rejected'),
    cancelledByCustomer: sum((r) => r.status === 'cancelled_by_customer'),
    cancelledByShop: sum((r) => r.status === 'cancelled'),
    noShow: sum((r) => r.status === 'cancelled' && r.status_reason === 'no_show'),
    deliveryFailed: failedEver,
    returnedToShop: sum((r) => r.status === 'returned_to_store' || r.status === 'restocked'),
    returnsOrRefunds: sum((r) => ['return_requested', 'refunded_partial', 'refunded_full'].includes(r.status)),
    firstOrderAt: rows.length ? Math.min(...rows.map((r) => Number(r.first_at))) : null,
    lastOrderAt: rows.length ? Math.max(...rows.map((r) => Number(r.last_at))) : null,
  };

  const flags = [];
  //  RSK-04: در فهرستِ بسته/مطمئن؟ (بی دلیل — دلیل در خودِ فهرست برای صاحب و مدیر است)
  const listed = await require('./risk-lists').matchIn(viaDb, shopId, {
    phone: order.customer_phone, address: order.fulfillment === 'delivery' ? asJson(order.address) : null, at,
  });
  if (listed.block) flags.push({ code: 'blocklisted', severity: 'warn', count: 1, until: Number(listed.block.expires_at) });
  if (listed.allow) flags.push({ code: 'allowlisted', severity: 'info', count: 1, until: Number(listed.allow.expires_at) });
  if (history.completed === 0) flags.push({ code: 'new_customer', severity: 'info', count: 0 });
  if (history.deliveryFailed > 0) flags.push({ code: 'delivery_failed_before', severity: 'warn', count: history.deliveryFailed });
  if (history.noShow > 0) flags.push({ code: 'no_show_before', severity: 'warn', count: history.noShow });
  if (history.returnedToShop > 0) flags.push({ code: 'returned_to_shop_before', severity: 'warn', count: history.returnedToShop });
  if (history.returnsOrRefunds > 0) flags.push({ code: 'returns_before', severity: 'info', count: history.returnsOrRefunds });
  if (history.cancelledByCustomer >= 2) flags.push({ code: 'cancels_often', severity: 'warn', count: history.cancelledByCustomer });
  if (history.open >= 2) flags.push({ code: 'many_open', severity: 'warn', count: history.open });
  if (order.source === 'storefront' && policy.duplicateWindowMin > 0) {
    const windowMs = policy.duplicateWindowMin * 60_000;
    const lines = (await query('SELECT product_id, qty_milli FROM online_order_lines WHERE order_id=$1', [order.id])).rows;
    const t = Number(order.created_at);
    //  جفتِ دیگر (نه خودش) در پنجرهٔ پیش یا پس از این سفارش — مثلاً پیش از روشن شدنِ پرچم، یا ثبتِ دستی
    const twin = await duplicateOf(viaDb, shopId, forms, {
      fulfillment: order.fulfillment, address: asJson(order.address), lines,
    }, { from: t - windowMs, to: t + windowMs, excludeId: order.id });
    if (twin) flags.push({ code: 'possible_duplicate', severity: 'warn', count: 1, orderNo: Number(twin.order_no) });
  }
  return { enabled: true, policy, known: history.orders > 0, history, flags };
}

module.exports = {
  FLAG_KEY, POLICY_KEY, DEFAULTS, DEAD, COMPLETED, OPEN,
  flagIn, policyIn, placeKey, linesKey, checkIn, riskOf,
};
