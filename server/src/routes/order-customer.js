'use strict';
/**
 * CRM-07 — سفارش‌های هنوز تحویل‌نشده یا لغوشدهٔ یک مشتری برای پروندهٔ او: `GET /api/shop/orders/customer?phone=…`
 * (`lib/orders.forCustomer`؛ تا دو `phone`، شمارهٔ اول و دومِ مشتری).
 *
 * ⛔ فقط‌خواندنی؛ پشتِ همان پرچمِ `shop.orders` (خاموش ⇒ ۴۰۳ `orders_disabled`، همان `/shop/orders`). خواندن با
 *    اشتراکِ تمام‌شده هم باز است (داده گروگان نیست).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. جست‌وجو با شماره فقط برای کسی که شمارهٔ کامل را می‌بیند
 *    (`orders.view` + `orders.contact`)؛ پاسخ همان کارتِ فهرست است (شمارهٔ پوشیده).
 * ⛔ سقف: سطلِ IP (`order-customer-ip`) پیش از احراز.
 * CRM-15: هر پرسش «سابقهٔ دسترسی» می‌نویسد (`customerId` ِ اختیاری = شناسهٔ پروندهٔ برنامه، و هر شمارهٔ خوانده‌شده‌ای که در
 *   پروندهٔ همان شناسه نیست با کلیدِ یک‌طرفهٔ شماره — `customer-access.noteLookup`؛ شمارهٔ دوم یا شناسهٔ ساختگی پنهانش نمی‌کند).
 * ⚠️ در app.js پیش از `/shop/orders` سوار می‌شود.
 */
const express = require('express');
const config = require('../config');
const orders = require('../lib/orders');
const access = require('../lib/customer-access');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'order-customer-ip' }));
//  CRM-15: جست‌وجو با شمارهٔ مشتری ⇒ `customers.contact` هم (‎lib/customer-contact.js‎)
router.use(requireUser, requireShop, requirePermission('orders.view'), requirePermission('orders.contact'),
  require('../lib/customer-contact').requireOrdersContact);

const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  next();
};

router.get('/', needFlag, async (req, res) => {
  const q = req.query?.phone;
  const phones = (Array.isArray(q) ? q : [q]).filter((x) => typeof x === 'string');
  const out = await orders.forCustomer(req.shopId, phones, { type: 'user', id: req.user.id, role: req.role });
  //  CRM-15: «چه کسی پروندهٔ مشتری را دید» — شناسهٔ پروندهٔ برنامه (`customerId`) و هر شمارهٔ خوانده‌شده‌ای که در دفترِ
  //  سرورِ همان پرونده نیست (کلیدِ یک‌طرفه)؛ بی انتظار و بی خطا (‎lib/customer-access.noteLookup‎)
  access.noteLookup(req, { customerId: req.query?.customerId, phones, kind: 'detail', door: 'orders' });
  res.json(out);
});

module.exports = router;
