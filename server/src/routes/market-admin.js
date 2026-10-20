'use strict';
/**
 * سمتِ دکان برای بازارچه — `/api/shop/market/…`: «در فهرست باشم؟»، دسته،
 * ناحیه و موقعیتِ درشت برای «نزدیکِ من» (`lib/market.js`).
 *
 * همان قفل‌های تنظیمِ ویترین (`routes/storefront-admin.js`): کاربرِ دکان،
 * `settings.write` برای نوشتن، پرچمِ ویترین (۴۰۳ `storefront_disabled`) و
 * اشتراکِ نوشتن. خواندن با `data.read`.
 */
const express = require('express');
const market = require('../lib/market');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const needFlag = wrap(async (req, res, next) => {
  req.storeState = await market.requireStorefrontFlag(req.shopId);
  next();
});

const needWrite = (req, res, next) => {
  const { entitled, ent } = req.storeState;
  if (entitled) return next();
  return next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

router.get('/', requirePermission('data.read'), needFlag, wrap(async (req, res) => {
  res.json({ directory: await market.directoryOn(), listing: await market.listingOf(req.shopId) });
}));

router.put('/', requirePermission('settings.write'), needFlag, needWrite, wrap(async (req, res) => {
  const listing = await market.saveListing(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'market.listing',
    targetType: 'storefront', targetId: req.shopId,
    //  موقعیت در سابقه نمی‌نشیند؛ فقط «دارد/ندارد»
    detail: { listed: listing.listed, category: listing.category, hasLocation: listing.location !== null },
    ip: clientIp(req),
  });
  res.json({ listing });
}));

module.exports = router;
