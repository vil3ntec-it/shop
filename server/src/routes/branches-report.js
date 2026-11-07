'use strict';
/**
 * RPT-18 — گزارشِ همهٔ شعبه‌ها در یک جا: `GET /api/shop/branches-report?from=YYYY-MM-DD&to=YYYY-MM-DD` (`lib/branches-report.js`).
 *
 *   پرچمِ `shop.branches_report` برای دکانِ همین نشست خاموش ⇒ `{enabled:false}` و بس. نقشِ دیگر ⇒ `{enabled:true, owner:false}`.
 *   صاحب ⇒ برای هر دکانی که صاحبِ فعالِ آن است و پرچمش روشن است، خلاصهٔ همان بازه (بی بازه ⇒ امروزِ هر دکان) و جمعِ همه.
 *
 * ⛔ فقط خواندنی؛ شعبه‌ها فقط از عضویت‌های خودِ کاربرِ توکن، هرگز از نشانی یا بدنه.
 * ⛔ سقفِ نرخ برای هر IP **پیش از** احراز و پس از آن برای هر کاربر (هر درخواست چند دکان را می‌خواند).
 */
const express = require('express');
const br = require('../lib/branches-report');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
router.use(rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'branches-report-ip' }));
router.use(requireUser, requireShop);
router.use(rateLimit({ max: 20, windowMs: 60 * 1000, keyPrefix: 'branches-report', key: (req) => req.user && req.user.id }));

//  نسخهٔ خواندنی (`DATABASE_REPLICA_URL`، پیش‌فرض خاموش): دفترِ بازه (`owner-report` ⇐ `lib/read-replica.js`)
router.get('/', require('../lib/read-replica').reads, async (req, res) => {
  res.set('Cache-Control', 'private, no-store');
  res.json(await br.reportFor(req.user.id, req.member, req.query || {}));
});

module.exports = router;
