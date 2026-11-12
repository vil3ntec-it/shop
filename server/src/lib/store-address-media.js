'use strict';
/**
 * CRM-09 / CHK-08 (§۴.۴ «یادداشتِ صوتی و عکسِ دروازه، اختیاری») — رسانهٔ نشانیِ نام‌دارِ حسابِ مشتریِ ویترین
 * (مهاجرتِ ۸۳۶؛ بایت از راهِ انبارِ عکس، `lib/media-store.js` ‹`address`›).
 *
 *   مشتری (نشستِ حساب) ─▶ PUT/GET/DELETE /api/store/:slug/account/addresses/:id/media/:kind   (kind: gate-photo | voice-note)
 *   ثبتِ ویترین ────────▶ `address.savedId` ِ بدنه ⇒ linkOrder: سفارش ⇐ همان نشانی (فقط نشانیِ خودِ همان حساب)
 *   برنامهٔ دکان ───────▶ GET /api/shop/orders/:id/address-media (کدام هست) و …/:kind (بایت) — `orders.view` + `orders.contact`
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پرچمِ پیش‌فرض‌خاموشِ `shop.storefront.address_media` ِ همان دکان: خاموش ⇒ درهای مشتری ۴۰۴، نمای دکان
 *     `{enabled:false}` و بایتش ۴۰۴، و هیچ پیوندِ سفارشی نوشته نمی‌شود.
 *   • نوع از بایت، نه از سرآیند (`chat-media.sniff`): عکس فقط JPEG/PNG/WebP تا [PHOTO_MAX]، صدا فقط Ogg/WebM/MP4/3GP/AMR تا
 *     [VOICE_MAX]؛ نوعِ دیگر ⇒ ۴۱۵ `bad_media_type`، بزرگ‌تر ⇒ ۴۱۳ `too_large`.
 *   • هر نشانی حداکثر یکی از هر نوع (تازه جای پیشین را می‌گیرد)؛ فقط مالِ همان حساب — نشانیِ حسابِ دیگر ⇒ ۴۰۴ (نه ۴۰۳).
 *   • دکان فقط رسانهٔ نشانیِ **سفارشِ خودش** را می‌بیند و فقط تا وقتی مشتری نگه داشته؛ پاک کردنِ مشتری (یا نشانی/حساب)
 *     از سفارش هم پاک می‌کند؛ «حذفِ داده‌های من در این دکان» (SEC-10) پیوندِ سفارش‌های همان دکان را برمی‌دارد.
 */
const { one, many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const { badRequest, notFound, ApiError } = require('../middleware/errors');
const media = require('./media-store').address;
const { sniff } = require('./chat-media');

const FLAG = 'shop.storefront.address_media';
const PHOTO_MAX = 1536 * 1024;
const VOICE_MAX = 1024 * 1024;
/** نامِ نشانی ⇐ نامِ ستون؛ پاسخ‌ها با نامِ نشانی */
const KINDS = Object.freeze({ 'gate-photo': 'gate_photo', 'voice-note': 'voice_note' });
const WIRE = Object.freeze({ gate_photo: 'gatePhoto', voice_note: 'voiceNote' });

const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG);

function kindOf(raw) {
  const k = Object.prototype.hasOwnProperty.call(KINDS, raw) ? KINDS[raw] : '';
  if (!k) throw notFound('این بخش پیدا نشد', 'media_not_found');
  return k;
}

/** بایتِ رسیده ⇐ mime، یا خطای روشن (نوع از خودِ بایت) */
function mimeOf(kind, buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 16) throw badRequest('فایلی نرسید', 'bad_media');
  const max = kind === 'gate_photo' ? PHOTO_MAX : VOICE_MAX;
  if (buf.length > max) throw new ApiError(413, 'too_large', `فایل بزرگ‌تر از ${Math.round(max / 1024)} کیلوبایت است`);
  const s = sniff(buf);
  const want = kind === 'gate_photo' ? 'image' : 'audio';
  if (!s || s.kind !== want) {
    throw new ApiError(415, 'bad_media_type', kind === 'gate_photo' ? 'فقط عکسِ JPEG، PNG یا WebP' : 'فقط صدای ضبط‌شده (Ogg، WebM، MP4 یا AMR)');
  }
  return s.mime;
}

async function ownAddress(run, customer, addressId) {
  const r = (await run('SELECT id FROM store_customer_addresses WHERE id = $1 AND customer_id = $2',
    [String(addressId || '').slice(0, 40), customer.id])).rows[0];
  if (!r) throw notFound('این نشانی پیدا نشد', 'address_not_found');
  return r.id;
}

const metaOf = (r) => ({ kind: WIRE[r.kind], mime: r.mime, size: Number(r.size_bytes), createdAt: Number(r.created_at) });

/** نوشتن/جایگزینی؛ ⇒ `{media}` */
async function put(customer, addressId, rawKind, buf) {
  const kind = kindOf(rawKind);
  const mime = mimeOf(kind, buf);
  const old = [];
  const row = await media.put(customer.id, mime, buf, (s) => tx(async (c) => {
    const id = await ownAddress((q, a) => c.query(q, a), customer, addressId);
    old.push(...(await c.query(
      'DELETE FROM store_address_media WHERE address_id = $1 AND kind = $2 RETURNING customer_id, store_key', [id, kind])).rows);
    const at = now();
    const r = (await c.query(
      `INSERT INTO store_address_media (id, customer_id, address_id, kind, mime, size_bytes, data, store_key, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id, kind, mime, size_bytes, created_at`,
      [ulid(at), customer.id, id, kind, mime, buf.length, s.data, s.key, at]
    )).rows[0];
    s.used = true;
    return r;
  }));
  await media.drop(old);
  return { media: metaOf(row) };
}

async function rowOf(customer, addressId, rawKind) {
  const kind = kindOf(rawKind);
  const r = await one(
    `SELECT * FROM store_address_media WHERE customer_id = $1 AND address_id = $2 AND kind = $3`,
    [customer.id, String(addressId || '').slice(0, 40), kind]);
  if (!r) throw notFound('برای این نشانی چیزی ذخیره نشده است', 'media_not_found');
  return r;
}

/** بایت برای خودِ مشتری ⇒ `{mime, data}` */
async function get(customer, addressId, rawKind) {
  const r = await rowOf(customer, addressId, rawKind);
  const data = await media.bytes(r);
  if (!data) throw notFound('برای این نشانی چیزی ذخیره نشده است', 'media_not_found');
  return { mime: r.mime, data };
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

async function orderRow(shopId, orderId) {
  const o = await one('SELECT id FROM online_orders WHERE id = $1 AND shop_id = $2', [String(orderId || '').slice(0, 40), shopId]);
  if (!o) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return o;
}

/** نمای دکان: کدام رسانه برای نشانیِ این سفارش هست (پرچمِ خاموش ⇒ `{enabled:false}`) */
async function forOrder(shopId, orderId) {
  if (!(await flagOn(shopId))) return { enabled: false };
  const o = await orderRow(shopId, orderId);
  const rows = await many(
    `SELECT m.kind, m.mime, m.size_bytes, m.created_at FROM online_order_address_refs r
       JOIN store_address_media m ON m.address_id = r.address_id
      WHERE r.order_id = $1 AND r.shop_id = $2`, [o.id, shopId]);
  const out = { enabled: true, gatePhoto: null, voiceNote: null };
  for (const r of rows) out[WIRE[r.kind]] = metaOf(r);
  return out;
}

/** بایت برای دکان ⇒ `{mime, data}`؛ پرچمِ خاموش، سفارشِ دکانِ دیگر یا نبودن ⇒ ۴۰۴ */
async function orderMedia(shopId, orderId, rawKind) {
  const kind = kindOf(rawKind);
  if (!(await flagOn(shopId))) throw notFound('این بخش پیدا نشد', 'media_not_found');
  const o = await orderRow(shopId, orderId);
  const r = await one(
    `SELECT m.* FROM online_order_address_refs r JOIN store_address_media m ON m.address_id = r.address_id
      WHERE r.order_id = $1 AND r.shop_id = $2 AND m.kind = $3`, [o.id, shopId, kind]);
  const data = r ? await media.bytes(r) : null;
  if (!data) throw notFound('برای نشانیِ این سفارش چیزی نیست', 'media_not_found');
  return { mime: r.mime, data };
}

module.exports = {
  FLAG, PHOTO_MAX, VOICE_MAX, KINDS, flagOn, kindOf, mimeOf, put, get, remove, withMedia, linkOrder, forOrder, orderMedia,
};
