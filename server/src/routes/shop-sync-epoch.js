'use strict';
/**
 *   GET  /api/shop/sync-epoch           «دورانِ بازیابیِ» همگام‌سازیِ v1 برای دکانِ همین توکن (§۹.۵)
 *   POST /api/shop/sync-epoch/deleted   از ردیف‌های تنهای دستگاه کدام روی سرور حذف‌شده است
 *
 *   GET ⇐ { ok, epoch: { id, at, base, head } }
 *        id    فقط با بازیابیِ سرور از پشتیبان عوض می‌شود (نه با بالا آمدنِ عادی)
 *        base  سرِ دفترِ همین دکان در لحظهٔ آن بازیابی
 *        head  سرِ دفترِ همین دکان همین حالا
 *   POST { rows: [{table, row_id}] } (حداکثر ۵۰۰) ⇐ { ok, deleted: [{table, row_id}] }
 *
 * برنامه (`V1Restore`) دوران را با آخرین دورانی که دیده مقایسه می‌کند؛ عوض شده ⇒ opهای
 * پذیرفته‌شدهٔ خودش و opهای دیگرانی که گرفته بود (server_seq > base) را به ترتیبِ سرور با همان
 * op_id دوباره می‌فرستد و دفترش را از Snapshot از نو می‌سازد — ردیف‌های تنهای خودش می‌مانند و
 * «درج» می‌روند، مگر همین‌جا حذف‌شده باشند. شرح در `lib/sync-epoch.js`.
 *
 * ⛔ جدا از `routes/sync-v1.js` (مشترک با پمپ) و فقط دکان: همان احرازِ مسیرهای `/api/shop`
 *    (`requireUser` + `requireShop`) — توکنِ پمپ ۴۰۱ — و حساب از **همان** راهِ خودِ همگام‌سازی
 *    (`resolveToken`ِ `sync-v1-auth`)، نه از `req.shopId`: دوران و پایه باید مالِ همان دفتری
 *    باشند که push/pull/snapshot ِ همین توکن می‌خوانند.
 */
const express = require('express');
const config = require('../config');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound, forbidden, badRequest } = require('../middleware/errors');
const { requireUser, requireShop } = require('../middleware/auth');
const { resolveToken, tokenOf } = require('../lib/sync-v1-auth');
const { SHOP } = require('../lib/tenancy');
const epoch = require('../lib/sync-epoch');

const router = express.Router();

router.use(rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'syncepochip' }));
router.use(requireUser, requireShop);
router.use(async (req, res, next) => {
  try {
    if (!config.syncEpoch.enabled) return next(notFound());
    const ctx = await resolveToken(tokenOf(req));
    //  حسابِ دکان: `app` و `accountKind` هر دو همان نامِ بخشِ دکان (`tenancy.SHOP.app`)
    if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId) {
      return next(forbidden('برای این حساب دکانی ثبت نشده است', (ctx && ctx.missing) || 'no_account'));
    }
    req.syncAccount = { app: ctx.app, accountKind: ctx.accountKind, accountId: ctx.accountId };
    res.set('Cache-Control', 'no-store');
    next();
  } catch (err) { next(err); }
});
router.use(rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'syncepoch',
  key: (req) => (req.syncAccount ? 'shop:' + req.syncAccount.accountId : ''),
}));

router.get('/', async (req, res, next) => {
  try {
    res.json({ ok: true, epoch: await epoch.forAccount(req.syncAccount, 'v1') });
  } catch (err) { next(err); }
});

router.post('/deleted', async (req, res, next) => {
  try {
    const rows = req.body && req.body.rows;
    if (!Array.isArray(rows) || rows.length > epoch.MAX_ASK) {
      return next(badRequest(`فهرستِ ردیف‌ها لازم است (حداکثر ${epoch.MAX_ASK})`, 'bad_rows'));
    }
    res.json({ ok: true, deleted: await epoch.deletedRows(req.syncAccount, 'v1', rows) });
  } catch (err) { next(err); }
});

module.exports = router;
