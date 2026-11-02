'use strict';
/**
 * «حساب‌پیِ دکان» — `/api/v1/shop/hesabpay` (همان `/api/shop/hesabpay`)؛ فقط صاحبِ دکان. شرح: `lib/hesabpay-account.js`.
 *
 *   GET  /                 حال بی رمز: {pinSet, locked, lockedUntil, mode, connected, holdUntil, autoAvailable, autoReason}
 *   POST /open             {pin} ⇒ نمای کامل (شماره/QR ِ ساده؛ از کلیدها فقط چهار نویسهٔ آخر و نشانیِ وب‌هوکِ دکان)
 *   PUT  /pin              {newPin} بارِ اول؛ {pin, newPin} عوض کردن
 *   POST /pin/reset        رمز فراموش شد ⇒ رمز و حساب پاک، ۲۴ ساعت بی حسابِ تازه
 *   PUT  /account          {pin, mode:'simple', account?, qr?} | {pin, mode:'auto', apiKey, webhookSecret, merchantId?}
 *   POST /account/remove   {pin}
 *
 * ⛔ shop_id و نقش فقط از نشست (`requireShop`)؛ مدیر و کارمند ⇒ ۴۰۳ `owner_only`.
 * ⛔ رمز و قفلش در سرور سنجیده می‌شوند، نه فقط در برنامه؛ کلید و راز هرگز در پاسخ نیستند.
 * ⛔ SUB-10: دستهٔ FREE — صاحب پس از پایانِ اشتراک هم باید بتواند حسابِ پولش را ببیند و ببندد.
 */
const express = require('express');
const acc = require('../lib/hesabpay-account');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
//  سقفِ نرخ پیش از احراز (هر IP) و پس از آن برای هر صاحب؛ قفلِ رمز جدا در خودِ سرور است
router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'shop-hesabpay-ip' }));
//  همان الگوی `order-evidence.js`: احراز و سقفِ هر صاحب در یک `use` (CodeQL ِ js/missing-rate-limiting)
const limit = rateLimit({
  max: Number(process.env.RATE_HESABPAY_ACCOUNT_MAX) || 30, windowMs: 60 * 1000, keyPrefix: 'shop-hesabpay',
  key: (req) => `${req.shopId}:${req.user.id}`,
});
router.use(requireUser, requireShop, limit);
router.use((req, res, next) => (req.role === 'owner' ? next()
  : next(forbidden('حساب‌پیِ دکان را فقط صاحبِ دکان می‌بیند و تنظیم می‌کند', 'owner_only'))));

const actor = (req) => ({ id: req.user.id, ip: clientIp(req) });

router.get('/', async (req, res) => {
  res.json(await acc.status(req.shopId));
});

router.post('/open', async (req, res) => {
  res.json(await acc.open(req.shopId, req.body, actor(req)));
});

router.put('/pin', async (req, res) => {
  res.json(await acc.setPin(req.shopId, req.body, actor(req)));
});

router.post('/pin/reset', async (req, res) => {
  res.json(await acc.resetPin(req.shopId, actor(req)));
});

router.put('/account', async (req, res) => {
  res.json(await acc.saveAccount(req.shopId, req.body, actor(req)));
});

router.post('/account/remove', async (req, res) => {
  res.json(await acc.removeAccount(req.shopId, req.body, actor(req)));
});

module.exports = router;
