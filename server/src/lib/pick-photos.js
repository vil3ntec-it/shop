'use strict';
/**
 * عکسِ سفارشِ بسته‌بندی‌شده به‌عنوانِ مدرک (PCK-08، v3 §۶.۱۷) — پشتِ همان پرچمِ خاموشِ
 * `shop.orders.picking` (درها در `routes/order-picking.js` می‌سنجند).
 *
 *   ⛔ فقط از بسته، نه شخص: `parcelOnly: true` شرط است (همان قاعدهٔ عکسِ تحویل، DLV-08)؛ وگرنه هیچ بایتی.
 *   ⛔ فقط وقتی برگه «بسته‌بندی‌شده» است و سفارش هنوز از دکان نرفته (`packed` / `ready_for_pickup`).
 *   ⛔ همان سقفِ اندازه و سنجشِ نوعِ واقعیِ عکس (`product-photos.sniff`، نه پسوند)؛ حداکثر ۳ برای هر سفارش.
 *   ⛔ کلیدِ یکتای دستگاه: ارسالِ دوباره ⇒ همان عکس؛ همان کلید با عکسِ دیگر ⇒ `event_key_reused`.
 *   مدرک فقط افزوده می‌شود (نه ویرایش، نه پاک) و هر دیدنِ بایت‌ها در سابقه ثبت می‌شود (درِ آن).
 */
const crypto = require('crypto');
const db = require('../db');
const { now, tx } = db;
const { ulid } = require('./ulid');
const { sniff, MAX_BYTES } = require('./product-photos');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const ID_RE = /^[0-9A-Z]{26}$/;
const MAX_PHOTOS = 3;
const ON_SHELF = ['packed', 'ready_for_pickup'];

const str = (v) => (typeof v === 'string' ? v.trim() : '');

function orderIdOf(raw) {
  const id = str(raw);
  if (!ID_RE.test(id)) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return id;
}

/** بدنه ⇒ `{key, buf, mime, hash, deviceTime}` — همهٔ سنجش‌ها پیش از هر خواندن از پایگاه */
function parse(body) {
  const b = body && typeof body === 'object' ? body : {};
  const key = str(b.eventId);
  if (!key) throw badRequest('شناسهٔ یکتای رویداد لازم است', 'event_id_required');
  if (!KEY_RE.test(key)) throw badRequest('شناسهٔ یکتای رویداد نامعتبر است', 'bad_event_id');
  if (b.parcelOnly !== true) throw badRequest('عکس فقط از بسته — تأییدش لازم است', 'parcel_only_required');
  const b64 = typeof b.imageBase64 === 'string' ? b.imageBase64 : '';
  if (!b64 || b64.length > Math.ceil(MAX_BYTES * 4 / 3) + 8) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const buf = Buffer.from(b64, 'base64');
  if (buf.length < 100) throw badRequest('عکس خالی است', 'bad_photo');
  if (buf.length > MAX_BYTES) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const mime = sniff(buf);
  if (!mime) throw badRequest('فقط JPEG، PNG یا WebP', 'bad_photo');
  let deviceTime = null;
  if (b.deviceTime !== undefined && b.deviceTime !== null) {
    if (typeof b.deviceTime !== 'number' || !Number.isSafeInteger(b.deviceTime) || b.deviceTime < 0 || b.deviceTime > 4102444800000) {
      throw badRequest('زمانِ دستگاه معتبر نیست', 'bad_device_time');
    }
    deviceTime = b.deviceTime;
  }
  return { key, buf, mime, hash: crypto.createHash('sha256').update(buf).digest('hex'), deviceTime };
}

function view(r) {
  return {
    id: r.id, orderId: r.order_id, mime: r.mime, size: Number(r.size), hash: r.hash, bags: Number(r.bags),
    deviceTime: r.device_time === null || r.device_time === undefined ? null : Number(r.device_time),
    createdBy: r.created_by, createdAt: Number(r.created_at),
  };
}

/** ⇒ `{photo, replayed}` */
async function add(shopId, orderId, body, actor) {
  const id = orderIdOf(orderId);
  const p = parse(body);
  return tx(async (c) => {
    const order = (await c.query('SELECT id, status FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, id])).rows[0];
    if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
    const pick = (await c.query('SELECT status, bags FROM order_picks WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
    if (!pick) throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');
    const prev = (await c.query('SELECT * FROM order_pick_photos WHERE shop_id=$1 AND event_key=$2', [shopId, p.key])).rows[0];
    if (prev) {
      if (prev.hash !== p.hash || prev.order_id !== id) throw conflict('این شناسه پیش‌تر برای عکسِ دیگری به کار رفته است', 'event_key_reused');
      return { photo: view(prev), replayed: true };
    }
    if (pick.status !== 'packed' || !ON_SHELF.includes(order.status)) {
      const err = conflict('عکسِ مدرک فقط پس از بسته‌بندی و پیش از تحویل', 'photo_not_allowed_now');
      err.data = { pickStatus: pick.status, orderStatus: order.status };
      throw err;
    }
    const n = Number((await c.query('SELECT COUNT(*)::int AS n FROM order_pick_photos WHERE order_id=$1', [id])).rows[0].n);
    if (n >= MAX_PHOTOS) throw conflict(`حداکثر ${MAX_PHOTOS} عکس برای هر سفارش`, 'too_many_photos');
    const at = now();
    const row = (await c.query(
      `INSERT INTO order_pick_photos (id, shop_id, order_id, event_key, mime, size, hash, data, bags, device_time, created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING id, order_id, mime, size, hash, bags, device_time, created_by, created_at`,
      [ulid(at), shopId, id, p.key, p.mime, p.buf.length, p.hash, p.buf, Number(pick.bags), p.deviceTime, actor.id || '', at]
    )).rows[0];
    return { photo: view(row), replayed: false };
  });
}

/** فهرستِ عکس‌های یک سفارش (بی بایت). سفارشِ دکانِ دیگر ⇒ ۴۰۴. */
async function list(shopId, orderId) {
  const id = orderIdOf(orderId);
  const own = (await db.query('SELECT 1 FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
  if (!own) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const rows = (await db.query(
    `SELECT id, order_id, mime, size, hash, bags, device_time, created_by, created_at FROM order_pick_photos
      WHERE shop_id=$1 AND order_id=$2 ORDER BY created_at, id`, [shopId, id]
  )).rows;
  return { photos: rows.map(view) };
}

/** بایت‌های یک عکس — فقط همان سفارش و همان دکان */
async function get(shopId, orderId, photoId) {
  const id = orderIdOf(orderId);
  const pid = str(photoId);
  if (!ID_RE.test(pid)) throw notFound('عکس پیدا نشد', 'photo_not_found');
  const r = (await db.query(
    'SELECT mime, data FROM order_pick_photos WHERE shop_id=$1 AND order_id=$2 AND id=$3', [shopId, id, pid]
  )).rows[0];
  if (!r) throw notFound('عکس پیدا نشد', 'photo_not_found');
  return { mime: r.mime, data: Buffer.from(r.data) };
}

module.exports = { MAX_PHOTOS, add, list, get };
