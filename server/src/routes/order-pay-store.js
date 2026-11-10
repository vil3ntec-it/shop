'use strict';
/**
 * «پرداخت پس از پذیرش» ِ مشتری: `POST /api/store/:slug/orders/:token/pay-choice` (`lib/order-flow.choose`).
 * پس از «قبول» ِ فروشنده، مشتری «نقد هنگامِ تحویل/برداشت» را برمی‌گزیند؛ حساب‌پی همان `POST …/pay` است.
 *
 * ⛔ فقط با تنظیمِ روشنِ «پرداخت پس از پذیرش» ِ همین دکان (`storefronts.pay_after_accept`)؛ خاموش ⇒ ۴۰۴ (همان «نیست»).
 * ⛔ ویترینِ روشن با اشتراکِ زنده (`sf.open` ⇒ وگرنه ۴۰۴ `store_not_found`، SUB-10 دستهٔ STORE_PAID) و توکنِ
 *    پیگیریِ همین ویترین (نادرست ۴۰۴، منقضی/باطل ۴۱۰). مشتری فقط «نقد» می‌گذارد؛ نه مبلغ، نه قبول.
 * ⛔ سقف: سطلِ جدای IP (`store-pay-choice-ip`، همان اندازهٔ «ثبت») فقط روی همین نشانی — پیش از هر کار.
 * پیشوندِ تازه نیست (`/store`)؛ ACCOUNT_PREFIXES دست نمی‌خورد.
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const flow = require('../lib/order-flow');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

router.use('/:slug/orders/:token/pay-choice', rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-pay-choice-ip' }));

router.post('/:slug/orders/:token/pay-choice', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  const store = await sf.open(req.params.slug);
  const orderId = await sf.orderIdOfLink(store, req.params.token);
  const out = await flow.choose(store.shop_id, orderId, req.body);
  //  همان نمای پیگیری (`GET …/orders/:token`، `lib/store-order-view.js`): صفحه با همین پاسخ دوباره می‌کشد
  const order = await require('../lib/store-order-view').customerOrder(store, req.params.token);
  res.status(200).json({ changed: out.changed, order });
});

module.exports = router;
