'use strict';
/**
 * CHK-08 (§۴.۴) — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ **یک سفارش** (مهمان یا نشانیِ یک‌باره؛ مهاجرتِ ۸۴۷). طراحیِ مشترک با
 * رسانهٔ نشانیِ ذخیره‌شدهٔ حساب (CRM-09) در `lib/address-media.js`: همان پرچم، نوع‌ها، سقف‌ها، سنجشِ بایت (EXIF پاک) و انبار؛
 * دکان و پیک هر دو را از همان یک در می‌بینند (رسانهٔ همین سفارش اگر هست، وگرنه نشانیِ ذخیره‌شده).
 *
 *   put(sf, token, kind, buf)    مشتری؛ اجازه فقط توکنِ امضاشدهٔ پیگیریِ همان سفارشِ همان ویترین (`orderIdOfLink`:
 *                                نادرست ⇒ ۴۰۴، باطل/گذشته ⇒ ۴۱۰). فقط تا پیش از سپردن به پیک یا «مشتری گرفت» (۴۰۹).
 *                                هر سفارش از هر نوع یکی؛ گذاشتنِ دوباره جایگزین می‌کند (همان بایت ⇒ همان ردیف).
 *   remove(sf, token, kind)      مشتری؛ همان اجازه و همان مهلت.
 *   view(shopId, orderId)        `{gatePhoto, voiceNote, saved}` (بی بایت): رسانهٔ همین سفارش و این‌که نشانیِ ذخیره‌شدهٔ
 *                                همان سفارش چه دارد (`saved` فقط بولی).
 *   customerView(shopId, id, o)  کلیدِ `addressMedia` ِ پیگیریِ مشتری (`store/order-addr-media.js`): همان نما + سقف‌ها.
 *
 * ⛔ حذفِ دادهٔ مشتری (`customer-erasure`) همه را پاک می‌کند. پرچمِ خاموش ⇒ هیچ نوشتن و هیچ خواندن (۴۰۴).
 */
const { many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const media = require('./media-store').address;
const core = require('./address-media');
const { notFound, conflict } = require('../middleware/errors');

const { FLAG, flagOn, kindOf, WIRE } = core;
/** سپرده به پیک یا پس از آن نشانی دیگر عوض نمی‌شود ⇒ گذاشتن/برداشتن بسته (`core.FINISHED` = هر وضعیتی که دیگر به ارسال برنمی‌گردد) */
const CLOSED = Object.freeze(['out_for_delivery', ...core.FINISHED]);

/** سفارشِ ویترینِ همین توکن، با قفل؛ بسته ⇒ ۴۰۹ */
async function openOrder(c, sf, orderId) {
  const row = (await c.query(
    `SELECT id, status, fulfillment, source FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE`,
    [sf.shop_id, orderId]
  )).rows[0];
  if (!row || row.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  if (row.fulfillment !== 'delivery') throw conflict('این سفارش ارسال ندارد', 'not_delivery');
  if (CLOSED.includes(row.status)) throw conflict('سفارش دیگر به نشانی نیاز ندارد', 'order_closed');
  return row;
}

/** ⇒ `{media, replaced, unchanged}` */
async function put(sf, token, rawKind, buf) {
  const kind = kindOf(rawKind);
  const ok = core.accept(kind, buf);
  const orderId = await require('./storefront').orderIdOfLink(sf, token);
  let gone = [];
  const out = await media.put({ shop_id: sf.shop_id }, ok.mime, ok.data, (st) => tx(async (c) => {
    await openOrder(c, sf, orderId);
    const prev = (await c.query(
      `SELECT id, kind, mime, size, sha256, created_at, store_key, shop_id FROM order_address_media
        WHERE shop_id=$1 AND order_id=$2 AND kind=$3 FOR UPDATE`,
      [sf.shop_id, orderId, kind]
    )).rows[0];
    if (prev && prev.sha256 === ok.sha256) return { row: prev, replaced: false, unchanged: true };
    if (prev) {
      await c.query('DELETE FROM order_address_media WHERE id=$1', [prev.id]);
      gone = [prev];
    }
    const at = now();
    st.used = true;
    const row = (await c.query(
      `INSERT INTO order_address_media (id, shop_id, order_id, kind, mime, size, sha256, data, store_key, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id, kind, mime, size, created_at`,
      [ulid(at), sf.shop_id, orderId, kind, ok.mime, ok.data.length, ok.sha256, st.data, st.key, at]
    )).rows[0];
    return { row, replaced: !!prev, unchanged: false };
  }));
  if (gone.length) await media.drop(gone);
  return { media: core.metaOf(out.row), replaced: out.replaced, unchanged: out.unchanged };
}

/** ⇒ `{deleted}` */
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
  await media.drop(gone);
  return { deleted: gone.length > 0 };
}

/** نمای مشتری (بی بایت): رسانهٔ همین سفارش + `saved` (نشانیِ ذخیره‌شدهٔ همان سفارش چه دارد) */
async function view(shopId, orderId) {
  const id = String(orderId || '');
  const [own, saved] = await Promise.all([
    many('SELECT kind, mime, size, created_at FROM order_address_media WHERE shop_id=$1 AND order_id=$2', [shopId, id]),
    many(`SELECT m.kind FROM online_order_address_refs r JOIN store_address_media m ON m.address_id = r.address_id
           WHERE r.shop_id=$1 AND r.order_id=$2`, [shopId, id]),
  ]);
  const out = { gatePhoto: null, voiceNote: null, saved: { gatePhoto: false, voiceNote: false } };
  for (const r of own) out[WIRE[r.kind]] = core.metaOf(r);
  for (const r of saved) out.saved[WIRE[r.kind]] = true;
  return out;
}

/**
 * کلیدِ `addressMedia` ِ پیگیریِ مشتری (`store-order-view`، `store/order-addr-media.js` ِ دیربار): فقط با پرچم و سفارشِ ارسالیِ
 * هنوز باز ⇒ `{photoMax, voiceMax, gatePhoto, voiceNote, saved}`؛ وگرنه undefined (کلید نیست ⇒ نه اسکریپت، نه واژه‌نامه، نه گره).
 */
async function customerView(shopId, orderId, order) {
  if (!order || order.fulfillment !== 'delivery' || CLOSED.includes(order.status) || !(await flagOn(shopId))) return undefined;
  return { ...core.caps(), ...(await view(shopId, orderId)) };
}

module.exports = { FLAG, CLOSED, flagOn, put, remove, view, customerView };
