'use strict';
/**
 * SEC-10 — درِ صاحبِ دکان برای حذفِ دادهٔ شخصیِ یک مشتری (`lib/customer-erasure.js`).
 *
 *   POST /api/shop/customer-erasure   بدنه: `{ phone }` یا `{ orderId }`، و `dryRun: true` برای پیش‌نمایش
 *     ⇒ `{ dryRun, mode, openOrders, counts, total, kept, erasureId? }`
 *
 * ⛔ پرچمِ `shop.customer_erasure` (`live_config`، پیش‌فرض خاموش): خاموش ⇒ `next('router')` — درخواست
 *    همان راهِ امروز را می‌رود (روترِ `/shop` و همان ۴۰۴)، بایت‌به‌بایت.
 * ⛔ فقط صاحبِ دکان (`owner_only`)؛ اجرای واقعی تأییدِ دوبارهٔ هویت می‌خواهد (`reauth_required`).
 * ⛔ کلیدِ کاتالوگ ندارد (دستهٔ FREE ِ SUB-10): حقِ حذفِ دادهٔ شخصی به اشتراک بسته نیست.
 * ⛔ دکان فقط از توکن (`requireShop`)؛ `shopId` ِ بدنه نادیده است.
 */
const express = require('express');
const config = require('../config');
const erasure = require('../lib/customer-erasure');
const reauth = require('../lib/reauth');
const { requireUser, requireShop, optionalShop } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();

router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'customer-erasure-ip' }));
router.use(requireUser);
//  دکانِ نشست فقط برای خواندنِ پرچم؛ هر ردی این‌جا (مثلاً پیک) ⇒ همان راهِ امروز
router.use((req, res, next) => optionalShop(req, res, (err) => (err ? next('router') : next())));
router.use(async (req, res, next) => {
  try {
    if (req.shopId && (await erasure.flagOn(req.shopId))) return next();
    delete req.member; delete req.shopId; delete req.role;
    next('router');
  } catch (err) { next(err); }
});
const shopLimit = rateLimit({ max: 30, keyPrefix: 'customer-erasure', key: (req) => (req.shopId ? `shop:${req.shopId}` : '') });

router.post('/', requireShop, shopLimit, async (req, res) => {
  if (req.role !== 'owner') throw forbidden('حذفِ دادهٔ مشتری فقط کارِ صاحبِ دکان است', 'owner_only');
  const b = req.body && typeof req.body === 'object' ? req.body : {};
  const dryRun = b.dryRun === true;
  if (!dryRun) await reauth.assertFresh(req);
  const out = await erasure.erase(req.shopId, { phone: b.phone, orderId: b.orderId }, {
    dryRun, by: 'owner', userId: req.user.id, ip: clientIp(req),
  });
  res.set('Cache-Control', 'no-store').json(out);
});

module.exports = router;
