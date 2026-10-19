'use strict';
/**
 *   GET /api/shop/sync-epoch   «دورانِ» همگام‌سازیِ v1 برای حسابِ همین توکن (§۹.۵)
 *
 *   ⇐ { ok, epoch: { id, at, base, head } }
 *        id    با هر بالا آمدنِ سرور عوض می‌شود (و پس از هر بازیابی، حتماً)
 *        base  سرِ دفترِ همین حساب در لحظهٔ آن بالا آمدن
 *        head  سرِ دفترِ همین حساب همین حالا
 *
 * برنامه (`SyncV1Engine.checkEpoch`) دوران را با آخرین دورانی که دیده مقایسه می‌کند؛ عوض
 * شده ⇒ opهای پذیرفته‌شدهٔ خودش با server_seq > base را با همان op_id دوباره می‌فرستد و
 * cursor را تا base پایین می‌آورد. شرح در `lib/sync-epoch.js`.
 *
 * ⛔ جدا از `routes/sync-v1.js` (مشترک با پمپ) و فقط دکان: توکنِ پمپ ۴۰۴ می‌گیرد.
 * ⛔ همان احرازِ همگام‌سازی (`requireSyncAccount`): حساب از توکن، نه از درخواست.
 */
const express = require('express');
const config = require('../config');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');
const { requireSyncAccount } = require('../lib/sync-v1-auth');
const epoch = require('../lib/sync-epoch');

const router = express.Router();

router.use(rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'syncepochip' }));
router.use(requireSyncAccount);
router.use(rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'syncepoch',
  key: (req) => (req.sync ? req.sync.app + ':' + req.sync.accountId : ''),
}));

router.get('/', async (req, res, next) => {
  try {
    if (!config.syncEpoch.enabled || req.sync.app !== 'shop') return next(notFound());
    res.set('Cache-Control', 'no-store');
    res.json({ ok: true, epoch: await epoch.forAccount(req.sync, 'v1') });
  } catch (err) { next(err); }
});

module.exports = router;
