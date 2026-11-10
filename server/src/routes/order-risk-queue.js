'use strict';
/**
 * RSK-03 «کارت در صف» — `GET /api/shop/orders/risk/queue` (`lib/order-risk.queueOf`): سفارش‌های بازِ پیش از ارسالِ
 * همین دکان که دستِ کم یک هشدارِ سابقه دارند، برای کارتِ «نگاهِ دوباره» ِ صفِ سفارش‌های برنامه.
 *
 * ⛔ فقط‌خواندنی؛ پشتِ پرچمِ `shop.orders` (خاموش ⇒ ۴۰۳ `orders_disabled`) و `shop.order_risk` (خاموش ⇒ `{enabled:false}`،
 *    همان `GET /:id/risk`). همان کسانی که `/:id/risk` را می‌بینند (`orders.view`)؛ پاسخ بی شماره و بی دلیلِ فهرست.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. هر بار تا ۲۰ سفارش × `riskOf` ⇒ سطلِ IP پیش از احراز و سقفِ هر کاربر.
 * ⚠️ در app.js پیش از `/shop/orders` سوار می‌شود.
 */
const express = require('express');
const config = require('../config');
const orders = require('../lib/orders');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'order-risk-queue-ip' }));
const perUser = rateLimit({ max: 30, windowMs: 60 * 1000, keyPrefix: 'order-risk-queue', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, perUser, requirePermission('orders.view'));

const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  next();
};

router.get('/', needFlag, async (req, res) => {
  res.json(await require('../lib/order-risk').queueOf(req.shopId));
});

module.exports = router;
