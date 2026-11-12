'use strict';
/**
 * عکسِ دروازه و یادداشتِ صوتیِ نشانیِ سفارش برای برنامهٔ دکان — **یک در** برای هر دو کاربرد (CRM-09 نشانیِ ذخیره‌شدهٔ حساب و
 * CHK-08 نشانیِ همین سفارش؛ `lib/address-media.js`)، زیرِ `/api/shop/orders` (`router.use` در `routes/orders.js`، پس از
 * `requireUser`، `requireShop` و `orders.view`):
 *
 *   GET /:id/address-media          `{enabled, gatePhoto, voiceNote}` (هر کدام `{kind, mime, size, createdAt, source}` یا null؛
 *                                   `source`: order | address)؛ پرچمِ خاموش ⇒ `{enabled:false}`
 *   GET /:id/address-media/:kind    بایت (gate-photo | voice-note) — رسانهٔ سفارش اگر هست، وگرنه نشانیِ ذخیره‌شده
 *
 * ⛔ فقط کسی که نشانی و شمارهٔ کاملِ مشتری را می‌بیند: `orders.contact` + «پنهان کردنِ شمارهٔ مشتری» ِ صاحب
 *    (`requireOrdersContact`)؛ چرا نه `orders.view`: سرِ `lib/address-media.js`. سفارشِ دکانِ دیگر ⇒ ۴۰۴. سقف: سطلِ هر کاربر.
 *    هر دیدنِ بایت در سابقه (`order.address_media.view`). پیک (توکنِ دامنهٔ courier) به این‌جا نمی‌رسد؛ درِ خودش در `routes/courier.js`.
 */
const express = require('express');
const core = require('../lib/address-media');
const audit = require('../lib/audit');
const { requirePermission } = require('../middleware/auth');
const { requireOrdersContact } = require('../lib/customer-contact');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

router.get('/:id/address-media', core.viewLimit, requirePermission('orders.contact'), requireOrdersContact, async (req, res) => {
  res.set('Cache-Control', 'private, no-store').json(await core.forOrder(req.shopId, req.params.id));
});

router.get('/:id/address-media/:kind', core.viewLimit, requirePermission('orders.contact'), requireOrdersContact, async (req, res) => {
  const m = await core.orderMedia(req.shopId, req.params.id, req.params.kind);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.address_media.view', targetType: 'online_order',
    targetId: req.params.id, detail: { kind: req.params.kind, source: m.source }, ip: clientIp(req),
  });
  core.send(res, m);
});

module.exports = router;
