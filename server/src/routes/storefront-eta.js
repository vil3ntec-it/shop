'use strict';
/**
 * تنظیمِ زمانِ تقریبیِ تحویل — `/api/shop/storefront/eta` (WEB-04؛ `lib/storefront-eta.js`).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچم‌های ویترین (`shop.orders` + `shop.storefront`) و `shop.storefront.eta` ⇒ `GET /`
 * فقط `{enabled:false}` و `PUT /` ۴۰۳ (`storefront_disabled` / `storefront_eta_disabled`). نوشتن کلیدهای اشتراکِ
 * ویترین را هم می‌خواهد (SUB-10: دستهٔ PAID ِ `paid-routes-entitlement.test.js`).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن؛ دیدن `data.read`، تنظیم کارِ صاحب و مدیر (`settings.write`).
 * سقف: سطلِ IP ِ همین در پیش از احراز (CodeQL).
 * ⚠️ در app.js پیش از `/shop/storefront` سوار می‌شود.
 *
 *   GET /   `{enabled, settings, zones, signals, preview, permissions}` — نشانه‌های همین لحظه فقط برای خودِ دکان
 *   PUT /   `{show?, prepMinutes?, travelMinutes?, parallel?, zoneTravel?: {شناسهٔ ناحیه: دقیقه}, expectedVersion?}`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const eta = require('../lib/storefront-eta');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-eta-admin-ip' }));
router.use(requireUser, requireShop);

async function stateOf(shopId) {
  const [st, flag] = await Promise.all([sf.stateOf(shopId), eta.flagOn(shopId)]);
  return { st, flag: st.flag && flag };
}

const needFlag = async (req, res, next) => {
  const { st, flag } = await stateOf(req.shopId);
  if (!st.flag) return next(forbidden('ویترین برای این دکان روشن نیست', 'storefront_disabled'));
  if (!flag) return next(forbidden('زمانِ تقریبیِ تحویل برای این دکان روشن نیست', 'storefront_eta_disabled'));
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

router.get('/', requirePermission('data.read'), async (req, res) => {
  const { flag } = await stateOf(req.shopId);
  if (!flag) return res.json({ enabled: false });
  res.json({
    enabled: true,
    ...(await eta.ownerView(req.shopId)),
    defaults: eta.DEFAULTS,
    permissions: { manage: can(req.role, 'settings.write') },
  });
});

router.put('/', requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const settings = await eta.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.eta.settings',
    targetType: 'storefront', targetId: req.shopId,
    detail: { show: settings.show, prepMinutes: settings.prepMinutes, travelMinutes: settings.travelMinutes, parallel: settings.parallel,
      zones: Object.keys(settings.zoneTravel).length },
    ip: clientIp(req),
  });
  res.json({ settings });
});

module.exports = router;
