'use strict';
/**
 * ویترین — قفسه: عکسِ کوچک از بانکِ تصاویر و وضعیتِ موجودی از کاتالوگ
 * (فاز ۴؛ WEB-08/09/16، v3 ۳۲.۳ و ۳۷).
 *
 *   عکس ──── پرچمِ `shop.image_bank` ِ دکان روشن و کالا عکسِ انتخاب‌شده دارد
 *            ⇒ نسخهٔ کوچکِ `thumb` (کارت) یا `display` (صفحهٔ کالا) از همان بانک
 *            (#262)، با همان قاعدهٔ «این دکان این نسخه را می‌خواند؟». وگرنه
 *            عکسِ همگام‌شدهٔ امروز (۰۴۵). سرور عکس را کوچک نمی‌کند.
 *   موجودی ─ `catalog.publicStock()` (PR #263، پرچمِ `shop.catalog`) اگر روی
 *            همین سرور باشد؛ نبودِ ماژول یا پرچمِ خاموش ⇒ همان «در حال بررسی»
 *            ِ امروز، هرگز «موجود» ِ ساختگی.
 */
const db = require('../db');
const { many } = db;

let catalog;
function catalogLib() {
  if (catalog === undefined) {
    try {
      catalog = require('./catalog');
    } catch (err) {
      if (err && err.code === 'MODULE_NOT_FOUND' && String(err.message).includes('./catalog')) catalog = null;
      else throw err;
    }
  }
  return catalog && typeof catalog.publicStock === 'function' ? catalog : null;
}

/** وضعیتِ موجودیِ کالاها را روی همان نماها می‌نشاند (درجا). */
async function applyStock(shopId, items) {
  const lib = catalogLib();
  if (!lib || !items.length) return items;
  const badges = await lib.publicStock(shopId, items.map((p) => p.id));
  if (!badges) return items;
  for (const p of items) {
    const b = badges.get(p.id);
    if (b) p.stock = b;
  }
  return items;
}

/** ناموجود هرگز پیشنهاد نمی‌شود (WEB-09). */
const suggestable = (p) => !(p.stock && (p.stock.status === 'out_of_stock' || p.stock.status === 'unavailable'));

/** کالاهایی که عکسِ بانک دارند (پرچمِ دکان روشن) — مجموعهٔ شناسه‌ها. */
async function bankIds(shopId, ids) {
  if (!ids.length) return new Set();
  const bank = require('./image-bank');
  if (!(await bank.shopEnabled(shopId))) return new Set();
  const rows = await many(
    `SELECT product_id FROM shop_product_images
      WHERE shop_id = $1 AND product_id = ANY($2::text[]) AND source <> 'none' AND image_id <> ''`,
    [shopId, ids]
  );
  return new Set(rows.map((r) => r.product_id));
}

/** نسخهٔ کوچکِ بانک برای این کالا — یا null (پرچم خاموش، بی عکس، بی اجازه، بی آن اندازه). */
async function bankImage(shopId, productId, size) {
  const bank = require('./image-bank');
  if (!(await bank.shopEnabled(shopId))) return null;
  const row = await db.one(
    `SELECT image_id, version FROM shop_product_images
      WHERE shop_id = $1 AND product_id = $2 AND source <> 'none' AND image_id <> ''`,
    [shopId, productId]
  );
  if (!row) return null;
  const img = await bank.imageRow(row.image_id);
  const version = Number(row.version);
  if (!img || !(await bank.shopCanRead(shopId, img, version, size))) return null;
  const f = await bank.readFile(img.id, version, size);
  return f ? { data: f.data, hash: f.hash, mime: f.mime } : null;
}

module.exports = { applyStock, suggestable, bankIds, bankImage, catalogLib };
