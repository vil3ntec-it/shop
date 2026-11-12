'use strict';
/**
 * CRM-09 (§۴.۴ «یادداشتِ صوتی و عکسِ دروازه، اختیاری») — رسانهٔ نشانیِ نام‌دارِ حسابِ مشتریِ ویترین (مهاجرتِ ۸۳۶). طراحیِ
 * مشترک با رسانهٔ نشانیِ یک سفارش (CHK-08) در `lib/address-media.js`: همان پرچم، سقف‌ها، سنجشِ بایت (EXIF پاک) و انبار.
 *
 *   مشتری (نشستِ حساب) ─▶ PUT/GET/DELETE /api/store/:slug/account/addresses/:id/media/:kind   (kind: gate-photo | voice-note)
 *   ثبتِ ویترین ────────▶ `address.savedId` ِ بدنه ⇒ linkOrder: سفارش ⇐ همان نشانی (فقط نشانیِ خودِ همان حساب)
 *   دکان و پیک ─────────▶ همان نمای یکتای `lib/address-media.js` (رسانهٔ سفارش اگر هست، وگرنه همین نشانی)
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پرچمِ پیش‌فرض‌خاموشِ `shop.storefront.address_media` ِ همان دکان: خاموش ⇒ درهای مشتری ۴۰۴ و هیچ پیوندِ سفارشی.
 *   • هر نشانی حداکثر یکی از هر نوع (تازه جای پیشین را می‌گیرد)؛ فقط مالِ همان حساب — نشانیِ حسابِ دیگر ⇒ ۴۰۴ (نه ۴۰۳).
 *   • دکان فقط رسانهٔ نشانیِ **سفارشِ خودش** را می‌بیند و فقط تا وقتی مشتری نگه داشته؛ پاک کردنِ مشتری (یا نشانی/حساب)
 *     از سفارش هم پاک می‌کند؛ «حذفِ داده‌های من در این دکان» (SEC-10) پیوندِ سفارش‌های همان دکان را برمی‌دارد.
 */
const { one, many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const { notFound } = require('../middleware/errors');
const media = require('./media-store').address;
const core = require('./address-media');

const { FLAG, flagOn, kindOf } = core;

/** نشانیِ همین حساب، **با قفل**: دو PUT ِ هم‌زمانِ یک نشانی (و PUT کنارِ حذفِ نشانی) پشتِ هم ⇒ نه ۲۳۵۰۵ ِ یکتایی، نه شیءِ یتیم */
async function ownAddress(run, customer, addressId) {
  const r = (await run('SELECT id FROM store_customer_addresses WHERE id = $1 AND customer_id = $2 FOR UPDATE',
    [String(addressId || '').slice(0, 40), customer.id])).rows[0];
  if (!r) throw notFound('این نشانی پیدا نشد', 'address_not_found');
  return r.id;
}

/** نوشتن/جایگزینی (بایت از `core.accept`: نوع، سقف، EXIF پاک)؛ ⇒ `{media, replaced}` */
async function put(customer, addressId, rawKind, buf) {
  const kind = kindOf(rawKind);
  const ok = core.accept(kind, buf);
  const old = [];
  const row = await media.put({ customer_id: customer.id }, ok.mime, ok.data, (s) => tx(async (c) => {
    const id = await ownAddress((q, a) => c.query(q, a), customer, addressId);
    old.push(...(await c.query(
      'DELETE FROM store_address_media WHERE address_id = $1 AND kind = $2 RETURNING customer_id, store_key', [id, kind])).rows);
    const at = now();
    const r = (await c.query(
      `INSERT INTO store_address_media (id, customer_id, address_id, kind, mime, size_bytes, data, store_key, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id, kind, mime, size_bytes, created_at`,
      [ulid(at), customer.id, id, kind, ok.mime, ok.data.length, s.data, s.key, at]
    )).rows[0];
    s.used = true;
    return r;
  }));
  await media.drop(old);
  return { media: core.metaOf(row), replaced: old.length > 0 };
}

async function rowOf(customer, addressId, rawKind) {
  const kind = kindOf(rawKind);
  const r = await one(
    `SELECT * FROM store_address_media WHERE customer_id = $1 AND address_id = $2 AND kind = $3`,
    [customer.id, String(addressId || '').slice(0, 40), kind]);
  if (!r) throw notFound('برای این نشانی چیزی ذخیره نشده است', 'media_not_found');
  return r;
}

/** بایت برای خودِ مشتری ⇒ `{kind, mime, data}` (`core.send`) */
async function get(customer, addressId, rawKind) {
  const r = await rowOf(customer, addressId, rawKind);
  const data = await media.bytes(r);
  if (!data) throw notFound('برای این نشانی چیزی ذخیره نشده است', 'media_not_found');
  return { kind: r.kind, mime: r.mime, data };
}

async function remove(customer, addressId, rawKind) {
  const kind = kindOf(rawKind);
  const rows = await many(
    `DELETE FROM store_address_media WHERE customer_id = $1 AND address_id = $2 AND kind = $3 RETURNING customer_id, store_key`,
    [customer.id, String(addressId || '').slice(0, 40), kind]);
  await media.drop(rows);
  return { deleted: rows.length > 0 };
}

/** نمای نشانی‌ها + `media: {gatePhoto, voiceNote}` (فقط بولی؛ بایت از درِ خودش) */
async function withMedia(customer, addresses) {
  const rows = await many('SELECT address_id, kind FROM store_address_media WHERE customer_id = $1', [customer.id]);
  const by = new Map();
  for (const r of rows) by.set(`${r.address_id}/${r.kind}`, true);
  return addresses.map((a) => ({
    ...a, media: { gatePhoto: by.has(`${a.id}/gate_photo`), voiceNote: by.has(`${a.id}/voice_note`) },
  }));
}

/**
 * حذفِ نشانی یا حساب (`store-customers.removeAddress` / `remove`)، درونِ همان تراکنش و پیش از حذفِ آبشاری: نشانی‌ها قفل (همان قفلِ
 * [put])، ردیف‌های رسانه پاک و `{customer_id, store_key}` شان برمی‌گردد تا [drop] پس از commit شیءِ انبار (disk/s3) را هم بردارد —
 * وگرنه عکسِ درِ خانه و صدای مشتریِ حساب‌پاک‌کرده تا `gc` ِ دستی می‌ماند. بی جدولِ ۸۳۶ (برگشته) ⇒ هیچ.
 */
async function detach(c, customerId, addressId = null) {
  if (!(await c.query("SELECT to_regclass('store_address_media') AS r")).rows[0].r) return [];
  await c.query('SELECT id FROM store_customer_addresses WHERE customer_id = $1 AND ($2::text IS NULL OR id = $2) FOR UPDATE',
    [customerId, addressId]);
  return (await c.query(
    'DELETE FROM store_address_media WHERE customer_id = $1 AND ($2::text IS NULL OR address_id = $2) RETURNING customer_id, store_key',
    [customerId, addressId])).rows;
}

/** شیءهای انبارِ ردیف‌های [detach] — فقط پس از commit (بی خطا؛ یتیمِ احتمالی را `gc` برمی‌دارد) */
const drop = (rows) => media.drop(rows);

/**
 * پس از ثبتِ ویترین (بیرون از تراکنشِ ثبت، مثلِ نسبتِ کمپین): `address.savedId` ِ بدنه و نشستِ حساب ⇒ پیوندِ سفارش به همان
 * نشانی، فقط اگر نشانی مالِ همان حساب است و پرچمِ دکان روشن. هر چیزِ دیگر ⇒ هیچ (بی خطا).
 */
async function linkOrder(shopId, orderId, session, body) {
  const addr = body && typeof body === 'object' && body.address && typeof body.address === 'object' ? body.address : null;
  const savedId = addr && typeof addr.savedId === 'string' ? addr.savedId.slice(0, 40) : '';
  if (!savedId || !session || !orderId || !(await flagOn(shopId))) return false;
  const a = await require('./store-customers').auth(session);
  if (!a) return false;
  const customer = a.customer;
  const r = await one(
    `INSERT INTO online_order_address_refs (order_id, shop_id, address_id, created_at)
     SELECT o.id, o.shop_id, a.id, $4 FROM online_orders o, store_customer_addresses a
      WHERE o.id = $1 AND o.shop_id = $2 AND a.id = $3 AND a.customer_id = $5
     ON CONFLICT (order_id) DO NOTHING RETURNING order_id`,
    [orderId, shopId, savedId, now(), customer.id]);
  return !!r;
}

module.exports = { FLAG, flagOn, put, get, remove, withMedia, detach, drop, linkOrder };
