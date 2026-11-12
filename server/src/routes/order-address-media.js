'use strict';
/**
 * CRM-09 / CHK-08 — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ سفارشِ ویترین برای برنامهٔ دکان (`lib/store-address-media.js`)،
 * زیرِ `/api/shop/orders` (`router.use` در `routes/orders.js`، پس از `orders.view`):
 *
 *   GET /:id/address-media          کدام هست (`{enabled, gatePhoto, voiceNote}`؛ پرچمِ خاموش ⇒ `{enabled:false}`)
 *   GET /:id/address-media/:kind    بایت (gate-photo | voice-note)
 *
 * ⛔ فقط کسی که نشانی و شمارهٔ مشتری را می‌بیند (`orders.contact` + «پنهان کردنِ شمارهٔ مشتری» ِ صاحب، همان
 * `requireOrdersContact`)؛ سفارشِ دکانِ دیگر ⇒ ۴۰۴. هر دیدنِ بایت در سابقه (`order.address_media.view`).
 */
const express = require('express');
const addrMedia = require('../lib/store-address-media');
const audit = require('../lib/audit');
const { requirePermission } = require('../middleware/auth');
const { requireOrdersContact } = require('../lib/customer-contact');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();
const contact = [requirePermission('orders.contact'), requireOrdersContact];

router.get('/:id/address-media', ...contact, async (req, res) => {
  res.json(await addrMedia.forOrder(req.shopId, req.params.id));
});

router.get('/:id/address-media/:kind', ...contact, async (req, res) => {
  const m = await addrMedia.orderMedia(req.shopId, req.params.id, req.params.kind);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.address_media.view', targetType: 'online_order',
    targetId: req.params.id, detail: { kind: req.params.kind }, ip: clientIp(req),
  });
  res.set('Content-Type', m.mime);
  res.set('Cache-Control', 'private, no-store');
  res.set('X-Content-Type-Options', 'nosniff');
  res.send(m.data);
});

module.exports = router;
