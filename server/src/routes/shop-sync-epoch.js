'use strict';
/**
 *   GET /api/shop/sync-epoch   «دورانِ بازیابیِ» همگام‌سازیِ v1 برای دکانِ همین توکن (§۹.۵)
 *
 *   ⇐ { ok, epoch: { id, at, base, head } }
 *        id    فقط با بازیابیِ سرور از پشتیبان عوض می‌شود (نه با بالا آمدنِ عادی)
 *        base  سرِ دفترِ همین دکان در لحظهٔ آن بازیابی
 *        head  سرِ دفترِ همین دکان همین حالا
 *
 * برنامه (`V1Restore.check`) دوران را با آخرین دورانی که دیده مقایسه می‌کند؛ عوض شده ⇒
 * opهای پذیرفته‌شدهٔ خودش با server_seq > base را با همان op_id دوباره می‌فرستد و دفترش را
 * از Snapshot از نو می‌سازد. شرح در `lib/sync-epoch.js`.
 *
 * ⛔ جدا از `routes/sync-v1.js` (مشترک با پمپ) و فقط دکان: همان احرازِ مسیرهای `/api/shop`
 *    (`requireUser` + `requireShop`) — توکنِ پمپ ۴۰۱، دکان از عضویت، نه از درخواست.
 */
const express = require('express');
const config = require('../config');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');
const { requireUser, requireShop } = require('../middleware/auth');
const epoch = require('../lib/sync-epoch');

const router = express.Router();

router.use(rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'syncepochip' }));
router.use(requireUser, requireShop);
router.use(rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'syncepoch',
  key: (req) => (req.shopId ? 'shop:' + req.shopId : ''),
}));

router.get('/', async (req, res, next) => {
  try {
    if (!config.syncEpoch.enabled) return next(notFound());
    res.set('Cache-Control', 'no-store');
    res.json({ ok: true, epoch: await epoch.forAccount({ app: 'shop', accountKind: 'shop', accountId: req.shopId }, 'v1') });
  } catch (err) { next(err); }
});

module.exports = router;
