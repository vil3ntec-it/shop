'use strict';
/**
 * دستورِ تحویلِ سفارش (فاز ۴؛ مهاجرتِ ۱۴۶):
 *
 *   CHK-14 ── «با اسکناسِ ۱۰۰۰ پرداخت می‌کنم» تا پیک پول خرد بیاورد: `delivery.cashTenderMinor`
 *             = مبلغی که مشتری هنگامِ تحویل می‌دهد (افغانیِ کامل، به یک‌صدم). فقط «ارسال» و
 *             «نقد هنگامِ تحویل»؛ کمتر از جمع نه؛ و پول خرد (= داده − جمع) کمتر از بزرگ‌ترین
 *             اسکناس (۱۰۰۰ افغانی، §۴.۱)، وگرنه یکی از اسکناس‌ها زیادی است و پیک بی‌جهت پول
 *             خردِ زیاد می‌برد. پیک «پول خرد» را از مبلغِ مورد انتظارِ همان مأموریت می‌بیند.
 *   CHK-24 ── ویرایشِ دستورِ تحویل تا پیش از ارسال: «پیش از رسیدن زنگ بزنید»، «تحویل به…»،
 *             نشانه و مبلغِ اسکناس. فقط سفارشِ «ارسال» و فقط در وضعیت‌های پیش از حرکت
 *             (`EDITABLE`)؛ پس از آن ۴۰۹ `delivery_locked`. نسخهٔ دیده‌شده (`version`) ناهمخوان ⇒
 *             ۴۰۹ `version_conflict` (ویرایشِ هم‌زمانِ دو دستگاه هرگز بی‌صدا رونویسی نمی‌شود).
 *             همان مقدار دوباره ⇒ ۲۰۰ `unchanged` بی رویداد. هر تغییر یک رویدادِ `delivery_edit`
 *             با «پیش» و «پس» در خطِ زمانی. کارتِ پیک همین JSON را زنده می‌خواند.
 *
 * ⛔ پرچمِ جدا و پیش‌فرض خاموشِ `shop.orders.delivery_prefs` (live_config، دکان یا all): خاموش ⇒
 *    `cashTenderMinor` خوانده نمی‌شود (همان سفارشِ امروز) و ویرایش ۴۰۳ `delivery_prefs_disabled`.
 * ⛔ پول فقط عددِ صحیحِ یک‌صدم؛ مبلغِ اسکناس هیچ اثری بر جمع، COD یا تسویه ندارد.
 */
const db = require('../db');
const { tx, now } = db;
const { ulid } = require('./ulid');
const { badRequest, conflict, notFound, forbidden } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.delivery_prefs';
/** بزرگ‌ترین اسکناسِ افغانی (۱۰۰۰) به یک‌صدم */
const LARGEST_NOTE_MINOR = 100_000;
const MAX_TENDER_MINOR = 100_000_000_000;
/** وضعیت‌های پیش از حرکتِ بسته */
const EDITABLE = Object.freeze(['new', 'accepted', 'picking', 'packed']);

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};

function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

/** پرچمِ همین دکان (پیش‌فرض خاموش). */
const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

/** مبلغِ اسکناس از بدنه: نیامده/خالی ⇒ null؛ نادرست ⇒ ۴۰۰. */
function parseTender(raw) {
  if (raw === undefined || raw === null || raw === '') return null;
  if (typeof raw !== 'number' || !Number.isSafeInteger(raw) || raw <= 0 || raw > MAX_TENDER_MINOR) {
    throw badRequest('مبلغِ اسکناسِ مشتری باید عددِ صحیحِ مثبت باشد', 'bad_cash_tender');
  }
  if (raw % 100 !== 0) throw badRequest('مبلغِ اسکناس افغانیِ کامل است', 'bad_cash_tender');
  return raw;
}

/** پول خردی که پیک می‌آورد — یا null اگر داده کمتر از بدهی است. */
const changeOf = (tenderMinor, dueMinor) => (tenderMinor >= dueMinor ? tenderMinor - dueMinor : null);

/** قاعدهٔ اسکناس در برابرِ جمعِ همین لحظه (ثبت و ویرایش هر دو). */
function checkTender(tenderMinor, { fulfillment, paymentMethod, totalMinor }) {
  if (tenderMinor === null) return;
  if (fulfillment !== 'delivery' || paymentMethod !== 'cod') {
    throw badRequest('مبلغِ اسکناس فقط برای ارسال با پرداختِ نقد هنگامِ تحویل', 'bad_cash_tender');
  }
  const change = changeOf(tenderMinor, totalMinor);
  if (change === null) throw badRequest('مبلغِ اسکناس از جمعِ سفارش کمتر است', 'cash_tender_short');
  if (change >= LARGEST_NOTE_MINOR) {
    throw badRequest('پول خرد از یک اسکناسِ ۱۰۰۰ بیشتر می‌شود؛ مبلغِ اسکناس را درست بنویسید', 'cash_tender_excess');
  }
}

/**
 * ثبتِ سفارش (کارمند و ویترین)، پس از ساختنِ جمع و پیش از نوشتن — `input` همان شکلِ
 * `parseCreate`. خاموش ⇒ کلید دور ریخته می‌شود (همان سفارشِ امروز).
 */
function applyOnCreate(input, body, on) {
  const { cashTenderMinor: _drop, ...delivery } = obj(input.delivery);
  if (!on) return { ...input, delivery };
  const tender = parseTender(obj(obj(body).delivery).cashTenderMinor);
  checkTender(tender, input);
  return { ...input, delivery: tender === null ? delivery : { ...delivery, cashTenderMinor: tender } };
}

/** ورودیِ ویرایش: فقط کلیدهای آمده؛ هیچ ⇒ ۴۰۰. */
function parseEdit(body) {
  const b = obj(body);
  const d = obj(b.delivery);
  const a = obj(b.address);
  const has = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
  const out = {};
  if (has(d, 'callBeforeArrival')) {
    if (typeof d.callBeforeArrival !== 'boolean') throw badRequest('«پیش از رسیدن زنگ بزنید» بله یا نه است', 'bad_delivery_edit');
    out.callBeforeArrival = d.callBeforeArrival;
  }
  if (has(d, 'leaveWith')) out.leaveWith = text(d.leaveWith, 80);
  if (has(d, 'cashTenderMinor')) out.cashTenderMinor = parseTender(d.cashTenderMinor);
  if (has(a, 'landmark')) out.landmark = text(a.landmark, 200);
  if (!Object.keys(out).length) throw badRequest('چیزی برای ویرایش نیامده است', 'bad_delivery_edit');
  let expectedVersion = null;
  if (b.expectedVersion !== undefined && b.expectedVersion !== null) {
    if (!Number.isSafeInteger(b.expectedVersion) || b.expectedVersion < 1) throw badRequest('نسخه معتبر نیست', 'bad_delivery_edit');
    expectedVersion = b.expectedVersion;
  }
  return { patch: out, expectedVersion };
}

/** همان چهار مقدار از ردیف — برای «پیش» و «پس» ِ رویداد. */
function prefsOf(delivery, address) {
  return {
    callBeforeArrival: delivery.callBeforeArrival === true,
    leaveWith: delivery.leaveWith || '',
    cashTenderMinor: Number.isSafeInteger(delivery.cashTenderMinor) ? delivery.cashTenderMinor : null,
    landmark: address.landmark || '',
  };
}

/**
 * ویرایشِ دستورِ تحویل (CHK-24) — یک تراکنش، زیرِ قفلِ ردیفِ سفارش.
 * @returns {{order:object, unchanged:boolean}}
 */
async function edit(shopId, id, body, actor, { source = null } = {}) {
  if (!(await flagOn(shopId))) throw forbidden('ویرایشِ دستورِ تحویل برای این دکان روشن نیست', 'delivery_prefs_disabled');
  const { patch, expectedVersion } = parseEdit(body);
  const orders = require('./orders');
  const out = await tx(async (c) => {
    const row = (await c.query(
      'SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(id || '')]
    )).rows[0];
    //  مشتری فقط سفارشِ ویترینِ خودش را (توکنِ پیگیری) — هر چیزِ دیگر «پیدا نشد»
    if (!row || (source && row.source !== source)) throw notFound('سفارش پیدا نشد', 'order_not_found');
    if (row.fulfillment !== 'delivery') throw conflict('دریافت از دکان دستورِ تحویل ندارد', 'not_delivery');
    if (!EDITABLE.includes(row.status)) {
      throw Object.assign(conflict('سفارش از دکان بیرون رفته است؛ با پیک تماس بگیرید', 'delivery_locked'),
        { data: { status: row.status } });
    }
    if (expectedVersion !== null && expectedVersion !== Number(row.version)) {
      throw Object.assign(conflict('سفارش همین حالا جای دیگری عوض شد؛ دوباره ببینید', 'version_conflict'),
        { data: { version: Number(row.version) } });
    }
    const delivery = asJson(row.delivery);
    const address = asJson(row.address);
    const before = prefsOf(delivery, address);
    const after = { ...before, ...patch };
    if (JSON.stringify(after) === JSON.stringify(before)) return { id: row.id, unchanged: true };
    checkTender(after.cashTenderMinor, {
      fulfillment: row.fulfillment, paymentMethod: row.payment_method, totalMinor: Number(row.total_minor),
    });
    const nextDelivery = { ...delivery, callBeforeArrival: after.callBeforeArrival, leaveWith: after.leaveWith };
    if (!nextDelivery.callBeforeArrival) delete nextDelivery.callBeforeArrival;
    if (!nextDelivery.leaveWith) delete nextDelivery.leaveWith;
    if (after.cashTenderMinor === null) delete nextDelivery.cashTenderMinor;
    else nextDelivery.cashTenderMinor = after.cashTenderMinor;
    const nextAddress = { ...address };
    if (after.landmark) nextAddress.landmark = after.landmark; else delete nextAddress.landmark;

    const at = now();
    const seq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [row.id]
    )).rows[0].n);
    await c.query(
      `UPDATE online_orders SET delivery=$3::jsonb, address=$4::jsonb, version=version+1, updated_at=$5
        WHERE shop_id=$1 AND id=$2`,
      [shopId, row.id, JSON.stringify(nextDelivery), JSON.stringify(nextAddress), at]
    );
    const changed = Object.keys(after).filter((k) => after[k] !== before[k]);
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'delivery_edit',$5,$5,$6,$7,'','',$8::jsonb,$9)`,
      [ulid(at), row.id, shopId, seq, row.status, actor.type, actor.id || '',
        JSON.stringify({ changed, before: pick(before, changed), after: pick(after, changed) }), at]
    );
    return { id: row.id, unchanged: false, changed };
  });
  //  نمای پس از کامیت (همان `GET /:id`)
  return { order: await orders.get(shopId, out.id, actor), unchanged: out.unchanged, changed: out.changed || [] };
}

const pick = (o, keys) => Object.fromEntries(keys.map((k) => [k, o[k]]));

/**
 * همان ویرایش از سوی خودِ مشتری با توکنِ پیگیری (`POST /api/store/:slug/orders/:token/delivery`) —
 * برای برنامهٔ خریدِ جدا (§۴۰.۵)؛ پاسخ همان نمای پیگیریِ مشتری است.
 */
async function editByToken(sf, token, body) {
  const store = require('./storefront');
  const id = store.orderIdOf(sf.shop_id, token);
  if (!id) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const out = await edit(sf.shop_id, id, body, { type: 'customer', id: '' }, { source: 'storefront' });
  return { order: await store.track(sf, token), unchanged: out.unchanged };
}

/** کارتِ پیک: مبلغِ اسکناس و پول خردِ همین مأموریت (از مبلغِ مورد انتظارِ خودش). */
function courierCash(delivery, expectedCollectMinor) {
  const tender = Number.isSafeInteger(delivery.cashTenderMinor) ? delivery.cashTenderMinor : null;
  if (tender === null) return { cashTenderMinor: null, changeMinor: null };
  return { cashTenderMinor: tender, changeMinor: changeOf(tender, Number(expectedCollectMinor)) };
}

module.exports = {
  FLAG_KEY, LARGEST_NOTE_MINOR, EDITABLE, flagOn, parseTender, changeOf, checkTender, applyOnCreate, parseEdit,
  edit, editByToken, courierCash,
};
