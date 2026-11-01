'use strict';
/**
 * اعلانِ سفارش برای برنامه‌های دکان — `/api/shop/order-notify` (v3 §۳۰).
 *
 *   GET  /config     همیشه باز: پرچم، پوش تنظیم شده؟ این دستگاه ثبت شده؟
 *                    کانالِ پشتیبان (امروز «تنظیم نشده») — §۳۰.۲ بندِ ۳
 *   GET  /pending    اعلان‌های بازنشده‌ای که سفارششان هنوز «تازه» است
 *                    (برنامهٔ باز یا کامپیوترِ روشن همین را می‌خواند)
 *   POST /:id/ack    رسیدِ دستگاه: {kind: 'received' | 'opened'}
 *
 * ⛔ پیش‌فرض خاموش: بی `shop.order_notify` فقط `/config` جواب می‌دهد.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن؛ اعلانِ دکانِ دیگر ⇒ ۴۰۴.
 * ⛔ رسید اشتراک نمی‌خواهد: دادهٔ کسب‌وکار نیست و بستنش فقط زنگ را بی‌پایان‌تر می‌کرد.
 */
const express = require('express');
const notify = require('../lib/order-notify');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { forbidden } = require('../middleware/errors');
const { can } = require('../lib/permissions');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));

const deviceOf = (req) => (req.device ? String(req.device.device_uid || req.device.id || '') : '');

const needFlag = async (req, res, next) => {
  if (!(await notify.flagOf(req.shopId))) {
    return next(forbidden('اعلانِ سفارش برای این دکان روشن نیست', 'order_notify_disabled'));
  }
  next();
};

router.get('/config', async (req, res) => {
  res.json(await notify.config(req.shopId, { userId: req.user.id, deviceUid: deviceOf(req) }));
});

router.get('/pending', needFlag, async (req, res) => {
  res.json({ notifications: await notify.pending(req.shopId, { limit: req.query.limit }) });
});

//  A16: «باز شد» اعلان را برای همهٔ دکان می‌بندد (یادآوری دیگر نمی‌رود) ⇐ فقط رسیدگی‌کنندهٔ
//  سفارش (`orders.manage`). «رسید» فقط ثبت است و برای هر بینندهٔ سفارش باز می‌ماند.
function needOpenRight(req, res, next) {
  const kind = req.body?.kind;
  if ((kind === undefined || kind === 'opened') && !can(req.role, 'orders.manage')) {
    return next(forbidden('بستنِ اعلانِ سفارش کارِ کسی است که سفارش را رسیدگی می‌کند', 'permission_denied'));
  }
  next();
}

router.post('/:id/ack', needOpenRight, needFlag, async (req, res) => {
  const out = await notify.ack(req.shopId, req.params.id, {
    kind: req.body?.kind, userId: req.user.id, deviceUid: deviceOf(req),
  });
  res.json(out);
});

module.exports = router;
