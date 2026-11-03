'use strict';
/**
 * عکسِ تحویل (۲۲.۲ C بندِ ۴، DLV-08) — از `lib/delivery.js` جدا شد تا زیرِ سقفِ اندازه بماند؛
 * رفتار همان. وابستگی‌های همان پرونده (نوشتنِ رویداد، سنجشِ عکس، سقفِ
 * شمارِ عکس) تزریق می‌شوند.
 */
const crypto = require('crypto');
const { one, many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict, ApiError } = require('../middleware/errors');
const { eventKey, optInt, asJson, n } = require('./delivery-input');

module.exports = ({ appendEvent, sniff, PHOTO_MAX_BYTES, MAX_PHOTOS }) => {
/**
 * ⛔ قاعدهٔ رضایت — عکس فقط وقتی ذخیره می‌شود که همه باشد:
 *   ۱) مشتری هنگامِ سفارش رضایت داده (`delivery.photoConsent === true`)؛
 *   ۲) پیک تأیید کرده عکس فقط از بسته است، نه شخص (`parcelOnly: true`)؛
 *   ۳) خودِ پیکِ همین مأموریت، در «رسیدم» یا «تحویل شد».
 * در غیرِ این، هیچ بایتی نوشته نمی‌شود.
 */
async function putPhoto(shopId, assignmentId, body, actor) {
  const b = body && typeof body === 'object' ? body : {};
  const key = eventKey(b.eventId);
  if (b.parcelOnly !== true) throw badRequest('عکس فقط از بسته — تأییدش لازم است', 'parcel_only_required');
  const b64 = typeof b.imageBase64 === 'string' ? b.imageBase64 : '';
  if (!b64 || b64.length > Math.ceil(PHOTO_MAX_BYTES * 4 / 3) + 8) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const buf = Buffer.from(b64, 'base64');
  if (buf.length < 100) throw badRequest('عکس خالی است', 'bad_photo');
  if (buf.length > PHOTO_MAX_BYTES) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const mime = sniff(buf);
  if (!mime) throw badRequest('فقط JPEG، PNG یا WebP', 'bad_photo');
  const hash = crypto.createHash('sha256').update(buf).digest('hex');
  const deviceTime = optInt(b.deviceTime, 'زمانِ دستگاه', { min: 0, max: 4102444800000 });

  return tx(async (c) => {
    const a = (await c.query(
      'SELECT * FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(assignmentId || '')]
    )).rows[0];
    if (!a || a.mode !== 'shop_courier' || a.courier_id !== actor.courierId) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
    const prev = (await c.query(
      'SELECT id, hash, assignment_id FROM delivery_photos WHERE shop_id=$1 AND event_key=$2', [shopId, key]
    )).rows[0];
    if (prev) {
      if (prev.hash !== hash || prev.assignment_id !== a.id) throw conflict('این شناسه پیش‌تر برای عکسِ دیگری به کار رفته است', 'event_key_reused');
      return { photoId: prev.id, replayed: true };
    }
    if (!['arrived', 'delivered'].includes(a.status)) throw conflict('عکسِ تحویل فقط هنگامِ رسیدن یا تحویل', 'photo_not_allowed_now');
    const order = (await c.query('SELECT delivery FROM online_orders WHERE id=$1', [a.order_id])).rows[0];
    if (asJson(order.delivery).photoConsent !== true) {
      throw new ApiError(403, 'photo_consent_missing', 'مشتری به عکسِ تحویل رضایت نداده است');
    }
    const count = Number((await c.query('SELECT COUNT(*)::int AS n FROM delivery_photos WHERE assignment_id=$1', [a.id])).rows[0].n);
    if (count >= MAX_PHOTOS) throw conflict(`حداکثر ${MAX_PHOTOS} عکس برای هر مأموریت`, 'too_many_photos');
    const at = now();
    const id = ulid(at);
    const consent = { customer: true, parcelOnly: true };
    await c.query(
      `INSERT INTO delivery_photos (id, shop_id, assignment_id, order_id, courier_id, event_key, mime, size, hash,
         data, consent, device_time, created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12,$13,$14)`,
      [id, shopId, a.id, a.order_id, a.courier_id, key, mime, buf.length, hash, buf, JSON.stringify(consent),
        deviceTime, actor.userId || '', at]
    );
    await appendEvent(c, a, {
      key: `photo:${key}`, hash, kind: 'photo', from: a.status, to: a.status,
      actorType: 'courier', actorId: actor.userId, deviceId: actor.deviceId, deviceTime,
      detail: { photoId: id, size: buf.length, mime },
    }, at);
    return { photoId: id, replayed: false };
  });
}

async function listPhotos(shopId, assignmentId) {
  //  مأموریتِ دکانِ دیگر (یا ناموجود) همان ۴۰۴ِ بقیهٔ درهای شناسه‌دار است، نه «۲۰۰ و فهرستِ خالی»
  const own = await one('SELECT 1 AS ok FROM delivery_assignments WHERE shop_id=$1 AND id=$2', [shopId, String(assignmentId || '')]);
  if (!own) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
  const rows = await many(
    `SELECT id, assignment_id, order_id, courier_id, mime, size, hash, consent, device_time, created_at
       FROM delivery_photos WHERE shop_id=$1 AND assignment_id=$2 ORDER BY created_at, id`,
    [shopId, String(assignmentId || '')]
  );
  return rows.map((r) => ({
    id: r.id, assignmentId: r.assignment_id, orderId: r.order_id, courierId: r.courier_id, mime: r.mime,
    size: Number(r.size), hash: r.hash, consent: asJson(r.consent), deviceTime: n(r.device_time),
    createdAt: Number(r.created_at),
  }));
}

async function getPhoto(shopId, photoId) {
  const r = await one('SELECT mime, data, order_id FROM delivery_photos WHERE shop_id=$1 AND id=$2', [shopId, String(photoId || '')]);
  if (!r) throw notFound('عکس پیدا نشد', 'photo_not_found');
  return { mime: r.mime, data: Buffer.from(r.data), orderId: r.order_id };
}

  return { putPhoto, listPhotos, getPhoto };
};
