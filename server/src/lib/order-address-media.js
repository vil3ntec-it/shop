'use strict';
/**
 * CHK-08 (§۴.۴) — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ سفارش، پشتِ پرچمِ پیش‌فرض خاموشِ `shop.storefront.address_media`
 * (مهاجرتِ ۸۴۷). درها: `routes/order-address-media.js` (مشتری با توکنِ پیگیری، کارمند، پیک).
 *
 *   put(sf, token, kind, buf)      مشتری؛ اجازه فقط توکنِ امضاشدهٔ پیگیریِ همان سفارشِ همان ویترین (`orderIdOfLink`:
 *                                  نادرست ⇒ ۴۰۴، باطل/گذشته ⇒ ۴۱۰). فقط تا پیش از سپردن به پیک یا «مشتری گرفت».
 *                                  هر سفارش از هر نوع یکی؛ گذاشتنِ دوباره جایگزین می‌کند (همان بایت ⇒ همان ردیف).
 *   remove(sf, token, kind)        مشتری؛ همان اجازه و همان مهلت.
 *   listFor(shopId, orderId)       فهرست (بی بایت) برای کارمند و پیک.
 *   bytesOf(shopId, orderId, id)   بایت‌ها برای کارمند و پیک.
 *
 * ⛔ نوع فقط از روی بایت‌ها (`chat-media.sniff`)؛ سرآیند و پسوند ملاک نیست: `gate_photo` فقط JPEG/PNG/WebP،
 *    `voice_note` فقط صدای فشرده (Opus/WebM، AAC، MP3، AMR، 3GP/M4A) ⇒ وگرنه ۴۱۵. سقفِ اندازه پیش از هر کار (۴۱۳).
 * ⛔ عکس پیش از ذخیره بی EXIF/XMP ِ مکان (`cleanImage`، سقفِ ابعاد از سرآیند). بایت در پایگاه یا انبارِ شیء
 *    (`media-store.chat`، tenant ِ `shop:<دکان>`؛ `image-store-move.TABLES`).
 * ⛔ حذفِ دادهٔ مشتری (`customer-erasure`) همه را پاک می‌کند. پرچمِ خاموش ⇒ هیچ نوشتن و هیچ خواندن (۴۰۴).
 */
const crypto = require('crypto');
const config = require('../config');
const { one, many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const chatMedia = require('./chat-media');
const mediaStore = require('./media-store');
const { ApiError, notFound, conflict } = require('../middleware/errors');

const FLAG = 'shop.storefront.address_media';
const KINDS = Object.freeze({ gate_photo: 'image', voice_note: 'audio' });
/** پس از این‌ها نشانی دیگر به کارِ تحویل نمی‌آید ⇒ گذاشتن/برداشتن بسته */
const CLOSED = Object.freeze(['out_for_delivery', 'picked_up', 'delivered', 'rejected', 'auto_rejected', 'cancelled',
  'cancelled_by_customer', 'returned_to_store', 'restocked', 'refunded_full']);
const ID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;

const flagOn = (shopId, run = null) => require('./orders').flagOf(shopId, FLAG, run);
const capOf = (kind) => (kind === 'gate_photo' ? config.addressMedia.photoMax : config.addressMedia.voiceMax);
const kb = (bytes) => `${Math.round(bytes / 1024)} کیلوبایت`;

function kindOf(raw) {
  const k = String(raw || '');
  if (!Object.prototype.hasOwnProperty.call(KINDS, k)) throw notFound('پیدا نشد', 'not_found');
  return k;
}

/** سفارشِ ویترینِ همین توکن، با قفل؛ بسته ⇒ ۴۰۹ */
async function openOrder(c, sf, orderId) {
  const row = (await c.query(
    `SELECT id, status, fulfillment, source, is_test FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE`,
    [sf.shop_id, orderId]
  )).rows[0];
  if (!row || row.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  if (row.fulfillment !== 'delivery') throw conflict('این سفارش ارسال ندارد', 'not_delivery');
  if (CLOSED.includes(row.status)) throw conflict('سفارش دیگر به نشانی نیاز ندارد', 'order_closed');
  return row;
}

async function put(sf, token, rawKind, buf) {
  const kind = kindOf(rawKind);
  const bytes = require('./raw-body').mediaBytes(buf);
  if (bytes.length > capOf(kind)) throw new ApiError(413, 'too_large', `فایل بزرگ‌تر از ${kb(capOf(kind))} است`);
  const s = chatMedia.sniff(bytes);
  if (!s || s.kind !== KINDS[kind]) {
    throw new ApiError(415, 'unsupported_media', kind === 'gate_photo' ? 'فقط عکس (JPG، PNG، WebP)' : 'فقط پیامِ صوتی (Opus، AAC، MP3، AMR)');
  }
  const orderId = await require('./storefront').orderIdOfLink(sf, token);
  const data = s.kind === 'image' ? chatMedia.cleanImage(bytes, s.mime, 'main') : bytes;
  const hash = crypto.createHash('sha256').update(data).digest('hex');
  let gone = [];
  const out = await mediaStore.chat.put(sf.shop_id, s.mime, data, (st) => tx(async (c) => {
    await openOrder(c, sf, orderId);
    const prev = (await c.query(
      'SELECT id, sha256, store_key, shop_id FROM order_address_media WHERE shop_id=$1 AND order_id=$2 AND kind=$3 FOR UPDATE',
      [sf.shop_id, orderId, kind]
    )).rows[0];
    if (prev && prev.sha256 === hash) return { id: prev.id, replaced: false, same: true };
    if (prev) {
      await c.query('DELETE FROM order_address_media WHERE id=$1', [prev.id]);
      gone = [prev];
    }
    const at = now();
    const id = ulid(at);
    st.used = true;
    await c.query(
      `INSERT INTO order_address_media (id, shop_id, order_id, kind, mime, size, sha256, data, store_key, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
      [id, sf.shop_id, orderId, kind, s.mime, data.length, hash, st.data, st.key, at]
    );
    return { id, replaced: !!prev, same: false };
  }));
  if (gone.length) await mediaStore.chat.drop(gone);
  return { id: out.id, kind, mime: s.mime, size: data.length, replaced: out.replaced, unchanged: out.same };
}

async function remove(sf, token, rawKind) {
  const kind = kindOf(rawKind);
  const orderId = await require('./storefront').orderIdOfLink(sf, token);
  const gone = await tx(async (c) => {
    await openOrder(c, sf, orderId);
    return (await c.query(
      'DELETE FROM order_address_media WHERE shop_id=$1 AND order_id=$2 AND kind=$3 RETURNING shop_id, store_key',
      [sf.shop_id, orderId, kind]
    )).rows;
  });
  await mediaStore.chat.drop(gone);
  return { removed: gone.length > 0 };
}

/** فهرستِ بی بایت (کارمند/پیک/مشتری) */
async function listFor(shopId, orderId) {
  const rows = await many(
    `SELECT id, kind, mime, size, created_at FROM order_address_media WHERE shop_id=$1 AND order_id=$2 ORDER BY kind`,
    [shopId, String(orderId || '')]
  );
  return rows.map((r) => ({ id: r.id, kind: r.kind, mime: r.mime, size: Number(r.size), createdAt: Number(r.created_at) }));
}

/** بایت‌های یک رسانهٔ همان سفارشِ همان دکان — نبود ⇒ ۴۰۴ */
async function bytesOf(shopId, orderId, mediaId) {
  const lost = () => notFound('این فایل روی سرور نیست', 'media_gone');
  const mid = String(mediaId || '');
  if (!ID_RE.test(mid)) throw lost();
  const r = await one(
    'SELECT id, shop_id, kind, mime, data, store_key FROM order_address_media WHERE shop_id=$1 AND order_id=$2 AND id=$3',
    [shopId, String(orderId || ''), mid]
  );
  if (!r) throw lost();
  const data = await mediaStore.chat.bytes(r);
  if (!data) throw lost();
  return { id: r.id, kind: r.kind, mime: r.mime, data };
}

/** فرستادنِ بایت با سرآیندهای امن (همان `chat-media.send`) */
function send(res, m) {
  chatMedia.send(res, { mime: m.mime, data: m.data, size: m.data.length, kind: KINDS[m.kind] });
}

module.exports = { FLAG, KINDS, CLOSED, flagOn, put, remove, listFor, bytesOf, send };
