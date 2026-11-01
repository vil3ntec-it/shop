'use strict';
/**
 * پیوندِ کمپین و QR ِ دکان از برنامهٔ مدیریت — `/api/shop/storefront/campaigns`
 * (MKT-10، MKT-11؛ `lib/campaigns.js`، مهاجرتِ ۱۵۳).
 *
 * ⛔ پیش‌فرض خاموش: پرچم‌های ویترین (`shop.orders` + `shop.storefront`) و پرچمِ
 * جدای `shop.storefront.campaigns`. بی آن‌ها فقط `GET /config` جواب می‌دهد (تا
 * برنامه بداند بخش را نشان ندهد) و هیچ ردیفی ساخته نمی‌شود.
 * ⛔ نوشتن کلیدهای اشتراکِ ویترین را می‌خواهد (SUB-10: PAID، `needWrite`).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. ساختن و ویرایش کارِ صاحب و مدیر است
 * (`settings.write`)؛ دیدنِ شمارها برای هر عضو (`data.read`).
 *
 * ⚠️ در app.js پیش از `/shop/storefront` سوار می‌شود.
 */
const express = require('express');
const sf = require('../lib/storefront');
const campaigns = require('../lib/campaigns');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop);

async function stateOf(shopId) {
  const [st, flag] = await Promise.all([sf.stateOf(shopId), campaigns.flagOn(shopId)]);
  return { st, flag: st.flag && flag };
}

const needFlag = async (req, res, next) => {
  const { st, flag } = await stateOf(req.shopId);
  if (!flag) return next(forbidden('پیوندِ کمپین برای این دکان روشن نیست', 'campaigns_disabled'));
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

router.get('/config', async (req, res) => {
  const { st, flag } = await stateOf(req.shopId);
  res.json({
    flag,
    enabled: flag && st.entitled,
    sources: campaigns.SOURCES,
    permissions: { manage: can(req.role, 'settings.write') },
  });
});

router.get('/', requirePermission('data.read'), needFlag, async (req, res) => {
  res.json(await campaigns.list(req.shopId, { days: req.query.days }));
});

router.post('/', requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const out = await campaigns.create(req.shopId, req.body, req.user.id);
  if (!out.replayed) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'storefront.campaign.create',
      targetType: 'store_campaign', targetId: out.campaign.id,
      detail: { name: out.campaign.name, source: out.campaign.source }, ip: clientIp(req),
    });
  }
  res.status(out.replayed ? 200 : 201).json(out);
});

router.put('/:id', requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const out = await campaigns.update(req.shopId, req.params.id, req.body);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.campaign.update',
    targetType: 'store_campaign', targetId: out.campaign.id,
    detail: { name: out.campaign.name, archived: out.campaign.archived }, ip: clientIp(req),
  });
  res.json(out);
});

module.exports = router;
