'use strict';
/**
 * AI-11 — «جست‌وجو با عکس» در ویترین: `POST /api/store/:slug/visual-search?limit=` (`lib/visual-search.js`).
 *   بدنه: بایت‌های عکس ⇒ `{ label: 'مشابه', notice, threshold, indexed, items: [{ product, product_id, distance, similarity, label }] }`
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.storefront.visual_search` **و** پرچمِ نمایهٔ `shop.visual_search` ِ همان دکان؛ یکی
 *    خاموش ⇒ `next()` پیش از خواندنِ بدنه، همان پاسخِ مسیرِ ناشناختهٔ امروز. ویترینِ نبوده یا بسته (اشتراکِ
 *    منقضی/معلق/لغو) ⇒ همان ۴۰۴ `store_not_found` ِ `sf.open` (SUB-10: دستهٔ STORE_PAID).
 * ⛔ فقط کالاهای منتشرشده و پنهان‌نشدهٔ همان دکان (`sf.catalogByIds`)؛ shop_id فقط از `slug`.
 * ⛔ عکسِ مشتری ذخیره نمی‌شود؛ هیچ کالایی خودکار انتخاب یا به سبد افزوده نمی‌شود.
 * ⛔ سقف: سطلِ IP پیش از هر کار، و سطلِ جدای هر ویترین.
 */
const express = require('express');
const sf = require('../lib/storefront');
const vs = require('../lib/visual-search');
const photos = require('../lib/product-photos');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

const ipLimit = rateLimit({ max: 20, keyPrefix: 'store-visual-ip' });
const storeLimit = rateLimit({ max: 300, keyPrefix: 'store-visual', key: (req) => `store:${req.params.slug}` });
const body = express.raw({ type: () => true, limit: photos.MAX_BYTES + 1024 });

router.post('/:slug/visual-search', ipLimit, async (req, res, next) => {
  const store = await sf.open(req.params.slug);
  if (!(await vs.storeFlagOn(store.shop_id))) return next('route');
  req.visualShop = store.shop_id;
  next();
}, storeLimit, body, async (req, res) => {
  const buf = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
  const shopId = req.visualShop;
  const out = await vs.search(shopId, buf, {
    limit: req.query.limit,
    store: async (ids) => (await sf.catalogByIds(shopId, ids.join(','))).items,
  });
  res.set('Cache-Control', 'no-store').json(out);
});

module.exports = router;
