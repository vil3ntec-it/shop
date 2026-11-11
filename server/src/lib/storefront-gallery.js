'use strict';
/**
 * PRD-17 و PRD-27 (تصمیمِ ۴-۲۲ ِ صاحب‌کار، ۱۴۰۵/۰۸/۲۰) — گالریِ عکس‌های بیشترِ کالا در برگهٔ کالای ویترین.
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.storefront.gallery` **و** `shop.image_bank` ِ همان دکان؛ یکی خاموش ⇒ `null` (درِ فهرست و
 *    فایل همان «نیست» ِ امروز، و `features.gallery` نیست).
 * ⛔ فقط عکس‌های فضای `shop` ِ **همین دکان** که به **همین کالا** وصل‌اند (`images.shop_product_id`)، تأییدشده، با نقشِ
 *    تکمیلی (`EXTRA_ROLES` = همان `ProductGallery.EXTRA_ROLES` ِ برنامه) و نسخهٔ `display` ِ نسخهٔ جاری؛ عکسی که همین حالا
 *    عکسِ ویترینِ کالاست، نقشِ `main`/`promo` و عکسِ برداشته‌شده (`detach`) نه. فقط کالای منتشرشده و زنده (همان `photo()`).
 * ⛔ شناسهٔ درونیِ عکس بیرون نمی‌رود: هر عکس با `tag` = ۱۲ hex از (عکس، نسخه) — همان `photoTag` ِ IMG-12؛ نسخهٔ تازه ⇒
 *    نشانیِ تازه و نشانیِ کهنه ۴۰۴. فایل فقط از راهِ همین فهرست: (دکان، کالا، tag) ⇒ همان ردیف، `shopCanRead` ِ بانک و
 *    اندازهٔ `thumb`/`display` (اصل هرگز).
 * سقف: ۸ عکس (همان `MAX_EXTRAS` ِ برنامه)، به ترتیبِ بارگذاری.
 */
const db = require('../db');
const orders = require('./orders');
const shelf = require('./storefront-shelf');

const FLAG = 'shop.storefront.gallery';
const EXTRA_ROLES = Object.freeze(['detail', 'back', 'label', 'variant']);
const SIZES = Object.freeze(['thumb', 'display']);
const MAX = 8;
const TAG_RE = /^[0-9a-f]{12}$/;

const tagOf = (r) => shelf.photoTag(`g:${r.id}:${Number(r.current_version)}`);

/** هر دو پرچمِ همان دکان روشن؟ */
async function flagOn(shopId) {
  if (!(await orders.flagOf(shopId, FLAG))) return false;
  return require('./image-bank').shopEnabled(shopId);
}

/** کالای منتشرشده و زندهٔ همین دکان؟ (همان شرطِ `photo()` ِ ویترین) */
async function isLive(shopId, productId) {
  const sf = require('./storefront');
  if (!sf.PRODUCT_ID_RE.test(String(productId || ''))) return false;
  return Boolean(await db.one(
    `SELECT 1 FROM ${await sf.productFrom(shopId)} WHERE sp.shop_id = $1 AND sp.product_id = $2 AND sp.published AND ${sf.LIVE_PRODUCT}`,
    [shopId, String(productId)]));
}

/** ردیف‌های گالریِ کالای منتشرشده — یا null (پرچم خاموش، کالای نبوده/منتشرنشده). */
async function rows(shopId, productId) {
  if (!(await flagOn(shopId)) || !(await isLive(shopId, productId))) return null;
  return db.many(
    `SELECT i.id, i.role, i.current_version FROM images i
      WHERE i.space = 'shop' AND i.owner_shop_id = $1 AND i.shop_product_id = $2 AND i.status = 'APPROVED'
        AND i.role = ANY($3::text[])
        AND EXISTS (SELECT 1 FROM image_derivatives d WHERE d.image_id = i.id AND d.version = i.current_version AND d.size = 'display')
        AND NOT EXISTS (SELECT 1 FROM shop_product_images s WHERE s.shop_id = $1 AND s.product_id = $2 AND s.source = 'shop' AND s.image_id = i.id)
      ORDER BY i.id LIMIT ${MAX}`,
    [shopId, String(productId), EXTRA_ROLES]
  );
}

/** `{ items: [{ tag, role }] }` — یا null. */
async function list(shopId, productId) {
  const rs = await rows(shopId, productId);
  return rs && { items: rs.map((r) => ({ tag: tagOf(r), role: r.role })) };
}

/** خودِ فایل `{ data, hash, mime }` — یا null (پرچم، کالا، tag یا اندازه نادرست؛ نسخه هنوز ساخته نشده). */
async function file(shopId, productId, tag, size) {
  if (!SIZES.includes(String(size)) || !TAG_RE.test(String(tag || ''))) return null;
  const rs = await rows(shopId, productId);
  const r = rs && rs.find((x) => tagOf(x) === tag);
  if (!r) return null;
  const bank = require('./image-bank');
  const img = await bank.imageRow(r.id);
  const version = Number(r.current_version);
  if (!img || !(await bank.shopCanRead(shopId, img, version, size))) return null;
  return bank.readFile(img.id, version, size);
}

module.exports = { FLAG, EXTRA_ROLES, MAX, flagOn, list, file };
