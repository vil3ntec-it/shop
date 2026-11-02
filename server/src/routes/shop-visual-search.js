'use strict';
/**
 * AI-11 — «جست‌وجو با عکس» برای کارمندِ دکان (`lib/visual-search.js`).
 *
 *   POST /api/shop/visual-search?limit=   بدنه: بایت‌های عکس (JPEG/PNG، همان سقفِ عکسِ کالا)
 *     ⇒ `{ label: 'مشابه', notice, threshold, indexed, items: [{ product_id, name, distance, similarity, label }] }`
 *
 * ⛔ پرچمِ `shop.visual_search` (`live_config`، پیش‌فرض خاموش): خاموش ⇒ `next('router')` — درخواست همان راهِ
 *    امروز را می‌رود (روترِ `/shop` و همان ۴۰۴)، بایت‌به‌بایت؛ بدنه پیش از پرچم خوانده نمی‌شود.
 * ⛔ فقط کالاهای همان دکانِ توکن (`requireShop`)؛ عکسِ پرسش ذخیره نمی‌شود و در سابقه نمی‌آید.
 * ⛔ فقط فهرستِ «مشابه»: هیچ کالایی خودکار انتخاب یا به فاکتور افزوده نمی‌شود — انتخاب با کاربر.
 * ⛔ کلیدِ کاتالوگ ندارد (دستهٔ FREE ِ SUB-10): فقط‌خواندنی، مثلِ جست‌وجوی متنیِ کالا.
 */
const express = require('express');
const config = require('../config');
const vs = require('../lib/visual-search');
const photos = require('../lib/product-photos');
const { requireUser, requireShop, optionalShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'visual-search-ip' }));
router.use(requireUser);
//  دکانِ نشست فقط برای خواندنِ پرچم؛ هر ردی این‌جا ⇒ همان راهِ امروز
router.use((req, res, next) => optionalShop(req, res, (err) => (err ? next('router') : next())));
router.use(async (req, res, next) => {
  try {
    if (req.shopId && (await vs.flagOn(req.shopId))) return next();
    delete req.member; delete req.shopId; delete req.role;
    next('router');
  } catch (err) { next(err); }
});
//  هر جست‌وجو یک رمزگشاییِ کامل است ⇒ سطلِ جدای دکان، کم‌تر از سقفِ عمومی
const shopLimit = rateLimit({ max: 60, keyPrefix: 'visual-search', key: (req) => (req.shopId ? `shop:${req.shopId}` : '') });
const body = express.raw({ type: () => true, limit: photos.MAX_BYTES + 1024 });

router.post('/', requireShop, requirePermission('data.read'), shopLimit, body, async (req, res) => {
  const buf = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
  res.set('Cache-Control', 'no-store').json(await vs.search(req.shopId, buf, { limit: req.query.limit }));
});

module.exports = router;
