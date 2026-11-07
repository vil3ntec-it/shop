'use strict';
/**
 * S83 (§۱۹.۱۲): محدودهٔ خدمتِ هر پیک — مهاجرتِ ۲۵۰ (`shop_couriers.areas`).
 *
 *   • فهرستِ خالی (همهٔ ردیف‌های پیشین) = همه‌جا ⇒ رفتارِ امروز.
 *   • ناحیهٔ سفارش = نامِ ناحیهٔ ارسالِ ویترین (`address.zone.name`، CHK-07)، وگرنه «ناحیه» ِ نشانی
 *     (`address.area`، DLV-02). شناسهٔ ناحیه پایدار نیست (`storefront-delivery.saveSettings` همه را از نو
 *     می‌سازد)، پس مقایسه با نام و با کلیدِ مقایسهٔ `text-core` است (همان `TextNormalizer.compareKey`):
 *     «کارتهٔ ۴» = «کارته 4»، «خیر‌خانه» = «خیرخانه».
 *   • سفارشِ بی ناحیه را هیچ پیکی «بیرون از محدوده» نمی‌بیند (چیزی برای سنجیدن نیست).
 *   • `parse` — مقدارِ ثبت/ویرایشِ پیک: آرایهٔ نام (`null` = خالی)، هر کدام ۱ تا ۸۰ نویسه، حداکثر ۳۰ (همان
 *     CHECK ِ مهاجرت)؛ تکراری با همان کلید یکی می‌شود؛ نامعتبر ⇒ ۴۰۰ `bad_areas`.
 *   • `check` — هنگامِ سپردن، زیرِ قفلِ ردیفِ سفارش و پیک (`FOR UPDATE` در `delivery.assign`): بیرون از
 *     محدوده ⇒ ۴۰۹ `courier_out_of_area`، مگر مدیر آگاهانه `overrideArea: true` بفرستد؛ آن‌گاه `true`
 *     برمی‌گردد و در رویدادِ «created» ثبت می‌شود. سپردنِ خودکارِ برنامه (`core/delivery/AutoAssign`) پیکِ
 *     بیرون از محدوده را هرگز نمی‌گیرد و سفارش «جا نشد» در صفِ سپردنِ دستی می‌ماند.
 *
 * دوقلو: `core/delivery/CourierArea.kt`؛ بردارِ مشترک `core/test-vectors/courier-area-v1.json`.
 */
const db = require('../db');
const T = require('./text-core');
const { stripControl } = require('./validate');
const { badRequest, conflict } = require('../middleware/errors');

const MAX_AREAS = 30;
const MAX_LEN = 80;

const keyOf = (s) => T.compareKey(typeof s === 'string' ? s : '');
const tidy = (s) => stripControl(s).replace(/\s+/g, ' ').trim();

/** ورودیِ ثبت/ویرایش ⇒ فهرستِ پاک (ترتیبِ ورودی، بی تکرار). */
function parse(v) {
  if (v === null) return [];
  if (!Array.isArray(v) || v.length > MAX_AREAS) throw badRequest(`محدودهٔ خدمت حداکثر ${MAX_AREAS} ناحیه است`, 'bad_areas');
  const out = [];
  const seen = new Set();
  for (const x of v) {
    if (typeof x !== 'string') throw badRequest('نامِ ناحیه باید متن باشد', 'bad_areas');
    const name = tidy(x);
    const k = keyOf(name);
    if (!k || name.length > MAX_LEN) throw badRequest(`نامِ هر ناحیه ۱ تا ${MAX_LEN} نویسه است`, 'bad_areas');
    if (seen.has(k)) continue;
    seen.add(k);
    out.push(name);
  }
  return out;
}

/** مقدارِ ردیفِ `shop_couriers` برای پاسخ. */
function view(row) {
  let a = row ? row.areas : null;
  if (typeof a === 'string') { try { a = JSON.parse(a); } catch { a = []; } }
  return Array.isArray(a) ? a.filter((x) => typeof x === 'string') : [];
}

/** ناحیهٔ سفارش از `online_orders.address`. */
function orderArea(address) {
  let a = address;
  if (typeof a === 'string') { try { a = JSON.parse(a); } catch { a = null; } }
  if (!a || typeof a !== 'object') return '';
  const zone = a.zone && typeof a.zone === 'object' && typeof a.zone.name === 'string' ? tidy(a.zone.name) : '';
  return zone || (typeof a.area === 'string' ? tidy(a.area) : '');
}

/** این محدوده ناحیهٔ سفارش را می‌گیرد؟ خالی ⇒ همه‌جا؛ سفارشِ بی ناحیه ⇒ بله. */
function covers(areas, area) {
  if (!Array.isArray(areas) || areas.length === 0) return true;
  const k = keyOf(area);
  if (!k) return true;
  return areas.some((x) => keyOf(x) === k);
}

/** بیرون از محدوده؟ بی `override` ⇒ ۴۰۹؛ با آن ⇒ `true` (برای ثبت در رویداد). */
function check(courierRow, order, override) {
  const area = orderArea(order.address);
  if (covers(view(courierRow), area)) return false;
  if (override !== true) {
    throw conflict(`ناحیهٔ این سفارش («${area}») در محدودهٔ خدمتِ این پیک نیست؛ پیکِ دیگری بسپارید`, 'courier_out_of_area');
  }
  return true;
}

/** نامِ ناحیه‌های ارسالِ ویترینِ همین دکان — گزینه‌های فرمِ پیک (`GET /shop/delivery/config`). */
async function choicesOf(shopId) {
  const rows = await db.many('SELECT name FROM storefront_zones WHERE shop_id=$1 ORDER BY sort, id', [shopId]);
  const seen = new Set();
  const out = [];
  for (const r of rows) {
    const name = tidy(String(r.name || ''));
    const k = keyOf(name);
    if (!k || seen.has(k)) continue;
    seen.add(k);
    out.push(name);
  }
  return out;
}

module.exports = { MAX_AREAS, MAX_LEN, keyOf, parse, view, orderArea, covers, check, choicesOf };
