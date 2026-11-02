'use strict';
/**
 * PAY-20 / RPT-12 — گزارشِ خودکار برای صاحبِ دکان: `/api/shop/owner-report` (`lib/owner-report.js`).
 *
 *   GET  /   پرچمِ `shop.owner_report` خاموش ⇒ `{enabled:false}` و بس. روشن و نقشِ دیگر ⇒ `{enabled:true, owner:false}`
 *            (نه تنظیم، نه عدد). صاحب ⇒ تنظیم، منطقهٔ زمانی، کانال‌های ممکن (واتس‌اپ: false) و ۱۴ گزارشِ آخر.
 *   PUT  /   فقط صاحبِ دکان (مدیر و کارمند ⇒ ۴۰۳ `owner_only`)؛ پرچم خاموش ⇒ ۴۰۳ `owner_report_disabled`.
 *            {active, frequency: daily|weekly, weekday: 0–6, hour: 0–23, channels: [inapp|push|email], expectedVersion?}
 *
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن (`requireShop`)؛ نقش از همان عضویت، نه از بدنه.
 */
const express = require('express');
const rep = require('../lib/owner-report');
const audit = require('../lib/audit');
const { requireUser, requireShop } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
//  سقفِ نرخ برای هر کاربر — تنظیم کم عوض می‌شود؛ خواندن هم پایگاه را می‌زند
const limit = rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'owner-report', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit);

const isOwner = (req) => req.role === 'owner';

const needFlag = async (req, res, next) => {
  if (!(await rep.flagOf(req.shopId))) return next(forbidden('گزارشِ خودکار برای این دکان روشن نیست', 'owner_report_disabled'));
  next();
};
const needOwner = (req, res, next) => (isOwner(req) ? next()
  : next(forbidden('گزارشِ خودکار را فقط صاحبِ دکان تنظیم می‌کند', 'owner_only')));

router.get('/', async (req, res) => {
  if (!(await rep.flagOf(req.shopId))) return res.json({ enabled: false });
  if (!isOwner(req)) return res.json({ enabled: true, owner: false });
  res.json({ enabled: true, owner: true, ...(await rep.viewOf(req.shopId)) });
});

router.put('/', needFlag, needOwner, async (req, res) => {
  const settings = await rep.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'owner_report.settings', targetType: 'shop', targetId: req.shopId,
    detail: { active: settings.active, frequency: settings.frequency, weekday: settings.weekday, hour: settings.hour, channels: settings.channels },
    ip: clientIp(req),
  });
  res.json({ settings });
});

module.exports = router;
