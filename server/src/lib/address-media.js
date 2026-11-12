'use strict';
/**
 * رسانهٔ نشانی (§۴.۴ «یادداشتِ صوتی و عکسِ دروازه، اختیاری») — یک طراحی، دو کاربرد (CRM-09 + CHK-08، یکی‌شده):
 *
 *   نشانیِ ذخیره‌شدهٔ حساب   `lib/store-address-media.js`، جدولِ `store_address_media` (۸۳۶) — مالِ حسابِ مشتری، برای هر
 *                            سفارشی که با همان نشانی ثبت شود (`online_order_address_refs`)؛ درهای `…/account/addresses/:id/media/:kind`.
 *   نشانیِ همین سفارش         `lib/order-address-media.js`، جدولِ `order_address_media` (۸۴۷) — مهمان یا نشانیِ یک‌باره، با
 *                            توکنِ پیگیری؛ درهای `…/orders/:token/address-media[/:kind]`.
 *
 * آن‌چه این‌جا یکی است:
 *   • پرچمِ پیش‌فرض‌خاموشِ `shop.storefront.address_media`، نوع‌ها (`gate-photo` | `voice-note` در نشانی؛ `gatePhoto` |
 *     `voiceNote` در پاسخ)، یک مجموعه سقف (`config.addressMedia`) و یک سنجشِ بایت ([accept]): نوع فقط از خودِ بایت
 *     (`chat-media.sniff`؛ ۴۱۵ `unsupported_media`)، بزرگ‌تر از سقف ۴۱۳ `too_large`، و عکس پیش از ذخیره بی EXIF/XMP ِ
 *     مکان و ابعادِ سرآیند زیرِ سقف (`chat-media.cleanImage`). یک انبار: `media-store.address`.
 *   • **یک در و یک نما برای دکان و پیک** ([forOrder] / [orderMedia] / [forMission] / [missionMedia]): برای هر نوع، رسانهٔ
 *     خودِ سفارش اگر هست، وگرنه رسانهٔ نشانیِ ذخیره‌شدهٔ همان سفارش (`source`: `order` | `address`). پرچمِ خاموش ⇒ نما
 *     `{enabled:false}` و بایت ۴۰۴؛ سفارشِ دکانِ دیگر ⇒ ۴۰۴.
 *   • نقشِ دکان: `orders.contact` (+ «پنهان کردنِ شمارهٔ مشتری» ِ صاحب، `requireOrdersContact`) — نه `orders.view`: صدای
 *     خودِ مشتری و عکسِ درِ خانه‌اش از شمارهٔ او کم‌حساس‌تر نیست و به مشتری گفته شده «فقط دکانِ همین سفارش و پیکش»؛ کسی که
 *     بسته را می‌برد پیک است و درِ خودش را دارد. پیک: فقط مأموریتِ خودش، پیکِ فعال، پس از «قبول» و پیش از «تحویل شد».
 */
const crypto = require('crypto');
const express = require('express');
const config = require('../config');
const { one, many } = require('../db');
const chatMedia = require('./chat-media');
const media = require('./media-store').address;
const { rateLimit } = require('../middleware/ratelimit');
const { ApiError, badRequest, notFound } = require('../middleware/errors');

const FLAG = 'shop.storefront.address_media';
/** نامِ نشانی ⇐ نامِ ستون */
const KINDS = Object.freeze({ 'gate-photo': 'gate_photo', 'voice-note': 'voice_note' });
/** نامِ ستون ⇐ کلیدِ پاسخ */
const WIRE = Object.freeze({ gate_photo: 'gatePhoto', voice_note: 'voiceNote' });
const TYPE = Object.freeze({ gate_photo: 'image', voice_note: 'audio' });

const flagOn = (shopId, run = null) => require('./orders').flagOf(shopId, FLAG, run);
const caps = () => ({ photoMax: config.addressMedia.photoMax, voiceMax: config.addressMedia.voiceMax });
const capOf = (kind) => (kind === 'gate_photo' ? config.addressMedia.photoMax : config.addressMedia.voiceMax);
const kb = (bytes) => `${Math.round(bytes / 1024)} کیلوبایت`;

/** `gate-photo` | `voice-note` ⇒ نامِ ستون؛ هر چیزِ دیگر (و هر نارشته) ⇒ ۴۰۴ */
function kindOf(raw) {
  const k = typeof raw === 'string' && Object.prototype.hasOwnProperty.call(KINDS, raw) ? KINDS[raw] : '';
  if (!k) throw notFound('این بخش پیدا نشد', 'media_not_found');
  return k;
}

/**
 * بایتِ رسیده ⇒ `{mime, data, sha256}` (عکس بی metadata)، یا خطای روشن. ترتیب: نابایت/خالی ۴۰۰ `bad_media`، سقفِ همان نوع ۴۱۳،
 * نوع از بایت ۴۱۵، ابعادِ عکس ۴۱۳ `image_too_large_dimensions`.
 * ⛔ مرزِ در: `value` همان `req.body` است و ممکن است رشته، آرایه یا شیءِ JSON باشد (parser ِ دیگری پیش از `express.raw`)؛ پیش
 *    از هر `.length` صریحاً سنجیده می‌شود (CodeQL ِ «type confusion through parameter tampering»: `Buffer.isBuffer` تنها بس نیست).
 */
function accept(kind, value) {
  if (typeof value === 'string' || typeof value !== 'object' || value === null || Array.isArray(value) || !Buffer.isBuffer(value) || !value.length) {
    throw badRequest('فایلی نرسید', 'bad_media');
  }
  const buf = value;
  if (buf.length > capOf(kind)) throw new ApiError(413, 'too_large', `فایل بزرگ‌تر از ${kb(capOf(kind))} است`);
  const s = chatMedia.sniff(buf);
  if (!s || s.kind !== TYPE[kind]) {
    throw new ApiError(415, 'unsupported_media', kind === 'gate_photo' ? 'فقط عکس (JPG، PNG، WebP)' : 'فقط پیامِ صوتی (Opus، AAC، MP3، AMR)');
  }
  const data = s.kind === 'image' ? chatMedia.cleanImage(buf, s.mime, 'main') : buf;
  return { mime: s.mime, data, sha256: crypto.createHash('sha256').update(data).digest('hex') };
}

/** بدنهٔ خام تا سقفِ بزرگ‌ترین نوع؛ بزرگ‌تر پیش از خواندن ۴۱۳ (هر دو درِ مشتری) */
const BODY_MAX = Math.max(config.addressMedia.photoMax, config.addressMedia.voiceMax);
const parseRaw = express.raw({ type: () => true, limit: BODY_MAX });
function rawBody(req, res, next) {
  const tooBig = () => new ApiError(413, 'too_large', `فایل بزرگ‌تر از ${kb(BODY_MAX)} است`);
  const len = Number(req.headers['content-length']);
  if (Number.isFinite(len) && len > BODY_MAX) return next(tooBig());
  parseRaw(req, res, (err) => {
    if (err && err.type === 'entity.too.large') return next(tooBig());
    if (err) return next(badRequest('فایل درست نرسید', 'bad_media'));
    next();
  });
}

/** سقفِ نرخِ خواندنِ دکان و پیک (سطلِ هر کاربر) */
const viewLimit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'addr-media-view', key: (req) => req.user && req.user.id });

/** ردیف ⇒ نمای بی بایت (`size` یا `size_bytes`) */
const metaOf = (r) => ({ kind: WIRE[r.kind], mime: r.mime, size: Number(r.size ?? r.size_bytes), createdAt: Number(r.created_at) });

// ── نمای یکتای دکان و پیک ──────────────────────────────────────────────

async function orderRow(shopId, orderId) {
  const o = await one('SELECT id FROM online_orders WHERE id = $1 AND shop_id = $2', [String(orderId || '').slice(0, 40), shopId]);
  if (!o) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return o.id;
}

const OWN = 'FROM order_address_media WHERE shop_id = $1 AND order_id = $2';
const SAVED = `FROM online_order_address_refs r JOIN store_address_media m ON m.address_id = r.address_id
                WHERE r.shop_id = $1 AND r.order_id = $2`;

/** نما: `{enabled, gatePhoto, voiceNote}` با `source` ِ هر کدام (پرچمِ خاموش ⇒ `{enabled:false}`) */
async function forOrder(shopId, orderId) {
  if (!(await flagOn(shopId))) return { enabled: false };
  const id = await orderRow(shopId, orderId);
  const [own, saved] = await Promise.all([
    many(`SELECT kind, mime, size, created_at ${OWN}`, [shopId, id]),
    many(`SELECT m.kind, m.mime, m.size_bytes, m.created_at ${SAVED}`, [shopId, id]),
  ]);
  const out = { enabled: true, gatePhoto: null, voiceNote: null };
  for (const r of saved) out[WIRE[r.kind]] = { ...metaOf(r), source: 'address' };
  for (const r of own) out[WIRE[r.kind]] = { ...metaOf(r), source: 'order' };
  return out;
}

/** بایت ⇒ `{kind, mime, data, source}`؛ همان انتخابِ نما (ردیفِ سفارش اگر هست، وگرنه نشانیِ ذخیره‌شده) */
async function orderMedia(shopId, orderId, rawKind) {
  const kind = kindOf(rawKind);
  if (!(await flagOn(shopId))) throw notFound('این بخش پیدا نشد', 'media_not_found');
  const id = await orderRow(shopId, orderId);
  let r = await one(`SELECT id, shop_id, kind, mime, data, store_key ${OWN} AND kind = $3`, [shopId, id, kind]);
  let source = 'order';
  if (!r) {
    r = await one(`SELECT m.id, m.customer_id, m.kind, m.mime, m.data, m.store_key ${SAVED} AND m.kind = $3`, [shopId, id, kind]);
    source = 'address';
  }
  const data = r ? await media.bytes(r) : null;
  if (!data) throw notFound('برای نشانیِ این سفارش چیزی نیست', 'media_not_found');
  return { kind, mime: r.mime, data, source };
}

/**
 * سفارشِ مأموریتِ پیک: فقط `shop_courier` ِ همین پیک، پیکِ فعال، و فقط تا مأموریت زنده است (همان `CALLABLE` ِ «تماس با
 * مشتری»: پس از «قبول» تا پیش از «تحویل شد»/«لغو») — مأموریتِ دیگری یا تمام‌شده ⇒ ۴۰۴.
 */
async function missionOrder(shopId, assignmentId, actor) {
  const a = await one('SELECT order_id, status, mode, courier_id FROM delivery_assignments WHERE shop_id = $1 AND id = $2',
    [shopId, String(assignmentId || '').slice(0, 60)]);
  if (!a || a.mode !== 'shop_courier' || !actor || a.courier_id !== actor.courierId) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
  if (actor.active !== true) throw new ApiError(403, 'courier_inactive', 'این پیک غیرفعال است');
  if (!require('./delivery-call').CALLABLE.includes(a.status)) throw notFound('برای این مأموریت چیزی نیست', 'media_not_found');
  return a.order_id;
}

async function forMission(shopId, assignmentId, actor) {
  if (!(await flagOn(shopId))) return { enabled: false };
  return forOrder(shopId, await missionOrder(shopId, assignmentId, actor));
}

async function missionMedia(shopId, assignmentId, rawKind, actor) {
  kindOf(rawKind);
  if (!(await flagOn(shopId))) throw notFound('این بخش پیدا نشد', 'media_not_found');
  return orderMedia(shopId, await missionOrder(shopId, assignmentId, actor), rawKind);
}

/** بایت به بیننده با سرآیندهای امن (`chat-media.send`: no-store، nosniff، sandbox، inline) */
function send(res, m) {
  chatMedia.send(res, { mime: m.mime, data: m.data, size: m.data.length, kind: TYPE[m.kind] });
}

module.exports = {
  FLAG, KINDS, WIRE, flagOn, caps, capOf, kindOf, accept, rawBody, viewLimit, metaOf,
  forOrder, orderMedia, forMission, missionMedia, send,
};
