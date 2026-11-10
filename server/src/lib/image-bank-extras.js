'use strict';
/**
 * PRD-17 — برداشتنِ عکسِ تکمیلی از کالای دکان (`POST /api/shop/images/:id/detach`، پرچمِ `shop.image_bank`).
 *
 * فقط پیوندِ کالا (`images.shop_product_id`) پاک می‌شود؛ اصل، نسخه‌ها و فایل‌ها می‌مانند (۳۷: اصل هرگز پاک نمی‌شود) و
 * عکس در «عکس‌های من» ِ دکان بی کالا می‌ماند. فقط عکسِ فضای `shop` ِ همین دکان (همان قاعدهٔ `ownImage` ِ
 * `lib/image-bank.js`)؛ وگرنه همان ۴۰۴ ِ «نیست». عکسی که همین حالا عکسِ نمایشِ همان کالاست (`shop_product_images`،
 * source=shop) ⇒ ۴۰۹ `image_selected`. دوباره ⇒ `detached: false`، بی نوشتن.
 */
const { tx, now } = require('../db');
const { notFound, conflict } = require('../middleware/errors');

async function detach(shopId, id) {
  return tx(async (c) => {
    const img = (await c.query('SELECT id, space, owner_shop_id, shop_product_id FROM images WHERE id=$1 FOR UPDATE', [String(id)])).rows[0];
    if (!img || img.space !== 'shop' || img.owner_shop_id !== shopId) throw notFound('عکس پیدا نشد');
    const pid = img.shop_product_id;
    if (!pid) return { image_id: img.id, product_id: '', detached: false };
    const shown = (await c.query(
      `SELECT 1 FROM shop_product_images WHERE shop_id=$1 AND product_id=$2 AND source='shop' AND image_id=$3 FOR UPDATE`,
      [shopId, pid, img.id])).rows[0];
    if (shown) throw conflict('این عکس همین حالا عکسِ کالا در ویترین است؛ اول عکسِ دیگری بگذارید', 'image_selected');
    await c.query(`UPDATE images SET shop_product_id='', rev=nextval('image_bank_rev'), updated_at=$2 WHERE id=$1`, [img.id, now()]);
    return { image_id: img.id, product_id: pid, detached: true };
  });
}

module.exports = { detach };
