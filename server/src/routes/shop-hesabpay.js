'use strict';
/**
 * «حساب‌پیِ دکان» — `/api/v1/shop/hesabpay` (همان `/api/shop/hesabpay`)؛ فقط صاحبِ دکان. شرح: `lib/hesabpay-account.js`.
 *
 *   GET  /                 حال بی رمز: {pinSet, locked, mode, connected, holdUntil, autoAvailable, payee:{name,last4},
 *                          pending:{name,last4,until}|null, sectionLockedUntil, device:{trusted,trustedAfter}, …}
 *   GET  /payees?days=30   «پول به کدام حساب رفت»: حساب‌های فعال/کهنه با جمعِ پرداخت‌ها و هشدارها (فقط نام و ۴ رقم)
 *   POST /open             {pin} ⇒ نمای کامل (شماره/QR ِ ساده؛ از کلیدها فقط چهار نویسهٔ آخر و نشانیِ وب‌هوکِ دکان)
 *   PUT  /pin         ✱    {newPin} بارِ اول؛ {pin, newPin} عوض کردن
 *   POST /pin/reset   ✱    رمز فراموش شد ⇒ رمز و حساب پاک، ۲۴ ساعت بی حسابِ تازه
 *   PUT  /account     ✱    {pin, name, mode:'simple', account?, qr?} | {pin, name, mode:'auto', apiKey, webhookSecret, merchantId}
 *                          نخستین حساب فوری؛ بعدی ۲۴ ساعت «در انتظار» (پوش + «این من نبودم»)
 *   POST /account/remove ✱ {pin}
 *   POST /pending/cancel ✱ «این من نبودم — لغو کن»: حسابِ در انتظار پاک و بخش ۲۴ ساعت بسته (بی رمزِ حساب‌پی)
 *
 * ✱ تأییدِ دوبارهٔ ورود (`lib/reauth.js`، همان `ReauthGate` ِ برنامه) **و** — جز لغو — دستگاهِ مطمئن و رمزِ حساب‌پی
 *   (`lib/hesabpay-guard.js`)؛ دستگاهِ تازه حتی یک تلاشِ رمز ندارد.
 *
 * ⛔ shop_id و نقش فقط از نشست (`requireShop`)؛ مدیر و کارمند ⇒ ۴۰۳ `owner_only`.
 * ⛔ رمز و قفلش در سرور سنجیده می‌شوند، نه فقط در برنامه؛ کلید و راز هرگز در پاسخ نیستند.
 * ⛔ SUB-10: دستهٔ FREE — صاحب پس از پایانِ اشتراک هم باید بتواند حسابِ پولش را ببیند و ببندد.
 */
const express = require('express');
const acc = require('../lib/hesabpay-account');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireRecentAuth } = require('../lib/reauth');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
//  سقفِ نرخ پیش از احراز (هر IP) و پس از آن برای هر صاحب؛ قفلِ رمز جدا در خودِ سرور است
router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'shop-hesabpay-ip' }));
//  همان الگوی `order-evidence.js`: احراز و سقفِ هر صاحب در یک `use` (CodeQL ِ js/missing-rate-limiting)
const limit = rateLimit({
  max: Number(process.env.RATE_HESABPAY_ACCOUNT_MAX) || 30, windowMs: 60 * 1000, keyPrefix: 'shop-hesabpay',
  key: (req) => `${req.shopId}:${req.user.id}`,
});
const ownerOnly = (req, res, next) => (req.role === 'owner' ? next()
  : next(forbidden('حساب‌پیِ دکان را فقط صاحبِ دکان می‌بیند و تنظیم می‌کند', 'owner_only')));
router.use(requireUser, requireShop, limit, ownerOnly);

//  «دستگاهِ مطمئن» ِ نگهبان: دستگاهِ همین توکن (`middleware/auth`) و زنجیرهٔ نشستش (`tokens.session_at` = ورودی که آن را
//  ساخت؛ پیش از ۲۰۵ ⇒ خودِ صدورِ توکن) — هر دو از سرور، هیچ‌کدام از چیزی که برنامه می‌فرستد
const sessionOf = (row) => (row ? { at: Number(row.session_at) || Number(row.issued_at), deviceId: row.device_id || '' } : null);
const actor = (req) => ({ id: req.user.id, ip: clientIp(req), device: req.device || null, session: sessionOf(req.tokenRow) });

router.get('/', async (req, res) => {
  res.json(await acc.status(req.shopId, actor(req)));
});

router.get('/payees', async (req, res) => {
  res.json(await acc.payees(req.shopId, { days: req.query.days }));
});

router.post('/open', async (req, res) => {
  res.json(await acc.open(req.shopId, req.body, actor(req)));
});

router.put('/pin', requireRecentAuth, async (req, res) => {
  res.json(await acc.setPin(req.shopId, req.body, actor(req)));
});

router.post('/pin/reset', requireRecentAuth, async (req, res) => {
  res.json(await acc.resetPin(req.shopId, actor(req)));
});

router.put('/account', requireRecentAuth, async (req, res) => {
  res.json(await acc.saveAccount(req.shopId, req.body, actor(req)));
});

router.post('/account/remove', requireRecentAuth, async (req, res) => {
  res.json(await acc.removeAccount(req.shopId, req.body, actor(req)));
});

router.post('/pending/cancel', requireRecentAuth, async (req, res) => {
  res.json(await acc.cancelPending(req.shopId, actor(req)));
});

module.exports = router;
