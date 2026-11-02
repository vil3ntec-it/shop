'use strict';
/**
 * قاعدهٔ یکسانِ امتیازِ وفاداری برای همهٔ دستگاه‌های دکان — `/api/shop/loyalty` (PRM-13؛ `lib/loyalty-rule.js`).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.loyalty_rule` ⇒ `GET /` فقط `{enabled:false}` و `PUT /` ۴۰۳ `loyalty_rule_disabled`.
 * ⛔ shop_id فقط از عضویتِ توکن؛ دیدن برای هر عضو (`data.read` — صندوقِ همهٔ دستگاه‌ها همین را می‌خواهد تا بی‌اینترنت هم
 *    حساب کند)؛ نوشتن فقط صاحب و مدیر (`settings.write`). بخشی از صندوقِ پایه است: کلیدِ کاتالوگ ندارد (SUB-10: FREE).
 * سقف: سطلِ IP ِ همین در پیش از احراز (CodeQL).
 *
 *   GET /   `{enabled, on?, rule?, version?, updatedAt?, permissions}` — `rule: null` یعنی هنوز ذخیره نشده
 *   PUT /   `{on, rule: {everyAfn, pointAfn, maxSharePct, silverAfn, goldAfn, expiryMonths}, expectedVersion?}`
 */
const express = require('express');
const config = require('../config');
const loyalty = require('../lib/loyalty-rule');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-loyalty-ip' }));
router.use(requireUser, requireShop);

const needFlag = async (req, res, next) => {
  if (!(await loyalty.flagOn(req.shopId))) return next(forbidden('قاعدهٔ یکسانِ امتیاز برای این دکان روشن نیست', 'loyalty_rule_disabled'));
  next();
};

router.get('/', requirePermission('data.read'), async (req, res) => {
  if (!(await loyalty.flagOn(req.shopId))) return res.json({ enabled: false });
  const saved = await loyalty.read(req.shopId);
  res.json({
    enabled: true,
    ...(saved || { on: false, rule: null, version: 0, updatedAt: 0 }),
    permissions: { manage: can(req.role, 'settings.write') },
  });
});

router.put('/', requirePermission('settings.write'), needFlag, async (req, res) => {
  const saved = await loyalty.save(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'loyalty.rule',
    targetType: 'shop', targetId: req.shopId, detail: { on: saved.on, ...saved.rule, version: saved.version },
    ip: clientIp(req),
  });
  res.json({ enabled: true, ...saved, permissions: { manage: true } });
});

module.exports = router;
