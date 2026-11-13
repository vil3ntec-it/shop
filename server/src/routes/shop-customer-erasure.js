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

//  CRM-15 (د۱۸): فرمانِ ناشناس کردنِ پرونده در دفترِ دستگاه (`lib/customer-erasure-ledger.js`) — پرچمِ خاموشِ
//  `shop.customer_erasure_ledger` پیش از هر بدنه (خاموش ⇒ ۴۰۴)؛ صاحب و مدیر (`data.delete.any`، همان «بستنِ تعارض»)
const ledger = require('../lib/customer-erasure-ledger');
const { requirePermission } = require('../middleware/auth');
const { notFound, badRequest } = require('../middleware/errors');
const ledgerOn = async (req, res, next) => {
  if (!(await require('../lib/orders').flagOf(req.shopId, ledger.FLAG))) return next(notFound('فرمانِ دفترِ دستگاه روشن نیست', 'erasure_ledger_off'));
  next();
};
router.get('/ledger', requireShop, shopLimit, ledgerOn, requirePermission('data.delete.any'), async (req, res) => {
  res.set('Cache-Control', 'no-store').json({ debtors: await ledger.pending(req.shopId) });
});
router.post('/ledger/applied', requireShop, shopLimit, ledgerOn, requirePermission('data.delete.any'), async (req, res) => {
  const out = await ledger.applied(req.shopId, req.body);
  if (!out) throw badRequest('فهرستِ پرونده‌ها درست نیست', 'bad_debtors');
  res.set('Cache-Control', 'no-store').json(out);
});

module.exports = router;
