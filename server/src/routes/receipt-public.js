'use strict';
/**
 * درِ عمومیِ فاکتور — `GET /api/receipt/:tag/:token` (QRِ هر فاکتور، فاکتورِ نسخهٔ ۲).
 * بی ورود؛ منطق در `lib/receipt-public.js`. صفحه‌اش `/r/:tag/:token` (`app.js`).
 *
 * ⛔ هر پاسخ (حتی ۴۰۴ و ۴۲۹): `noindex`، بی کش، بی ارجاع‌دهنده.
 * ⛔ دو سطل به‌ازای IP: ضربه‌ای در دقیقه و سقفِ ربع‌ساعته (`config.rateLimit.receiptRead*`).
 */
const express = require('express');
const config = require('../config');
const pub = require('../lib/receipt-public');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();

const burst = rateLimit({ max: config.rateLimit.receiptReadBurstMax, windowMs: 60 * 1000, keyPrefix: 'receipt-burst' });
const read = rateLimit({ max: config.rateLimit.receiptReadMax, keyPrefix: 'receipt-read' });

function privateHeaders(req, res, next) {
  res.set('X-Robots-Tag', 'noindex, nofollow, noarchive');
  res.set('Cache-Control', 'no-store');
  res.set('Referrer-Policy', 'no-referrer');
  next();
}

router.use(privateHeaders);

router.get('/:tag/:token', burst, read, async (req, res) => {
  res.json({ receipt: await pub.receiptOf(req.params.tag, req.params.token) });
});

module.exports = router;
module.exports.privateHeaders = privateHeaders;
