'use strict';
/**
 * تخفیفِ اولین خرید و سدِ سوءاستفاده از تخفیفِ ویترین از برنامهٔ مدیریت — `/api/shop/storefront/promo-guard`
 * (MKT-02، RSK-09؛ `lib/storefront-promo-guard.js`، مهاجرتِ ۱۹۲).
 *
 * ⛔ پیش‌فرض خاموش: پرچم‌های ویترین (`shop.orders` + `shop.storefront`) و پرچمِ جدای
 * `shop.storefront.promo_guard`؛ بی آن‌ها ۴۰۳ `promo_guard_disabled` و هیچ ردیفی ساخته نمی‌شود.
 * ⛔ نوشتن کلیدهای اشتراکِ ویترین را می‌خواهد (SUB-10: PAID، `needWrite`).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. دیدن و نوشتن هر دو کارِ صاحب و مدیر است (`settings.write`):
 * دفترِ مصرف و فهرستِ «مشکوک» شمارهٔ مشتری دارد.
 *
 *   GET  /   تنظیم، دفترِ مصرفِ اخیر و فهرستِ «مشکوک» (چند شماره از یک دستگاه، چند دستگاه برای یک شماره)
 *   PUT  /   `{firstOrder:{enabled, kind:'percent'|'amount', bp|amountMinor, maxMinor, minSubtotalMinor, stack}, limits:{codePerPhone, codePerDevice, maxDiscountMinor}}`
 *
 * ⚠️ در app.js پیش از `/shop/storefront` سوار می‌شود.
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const guard = require('../lib/storefront-promo-guard');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop);

const limit = rateLimit({
  max: config.rateLimit.generalMax, keyPrefix: 'shop-promo-guard',
  key: (req) => (req.user ? `${req.shopId}:${req.user.id}` : ''),
});

const needFlag = async (req, res, next) => {
  const [st, on] = await Promise.all([sf.stateOf(req.shopId), guard.flagOn(req.shopId)]);
  if (!(st.flag && on)) return next(forbidden('سدِ تخفیفِ ویترین برای این دکان روشن نیست', 'promo_guard_disabled'));
  req.storeState = st;
  next();
};

const needWrite = (req, res, next) => {
  const { entitled, ent } = req.storeState;
  if (entitled) return next();
  return next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

router.get('/', limit, requirePermission('settings.write'), needFlag, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(await guard.ownerView(req.shopId));
});

router.put('/', limit, requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const out = await guard.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.promo_guard',
    targetType: 'shop', targetId: req.shopId, detail: out.settings, ip: clientIp(req),
  });
  res.json(out);
});

module.exports = router;
