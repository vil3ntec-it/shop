'use strict';
/**
 * ۲-۹ — دسترسیِ پشتیبانی، سمتِ صاحبِ دکان (برنامه): «چه کسی، کی، چرا» و پذیرفتن/رد/لغو. منطق: `lib/support-access.js`.
 *
 *   GET  /api/shop/support-access                    ⇒ { grants, policy }  (پشتیبانی که الان می‌بیند: status=active)
 *   POST /api/shop/support-access/:gid/accept   ✱    درخواستِ بی‌پاسخ ⇒ active از همین دم تا `minutes`
 *   POST /api/shop/support-access/:gid/deny          درخواستِ بی‌پاسخ ⇒ denied
 *   POST /api/shop/support-access/:gid/revoke        اجازه‌ای که دکان داده (pending/active) ⇒ revoked؛ «شکستنِ شیشه» ⇒ ‎409 break_glass_owner‎
 *
 * ✱ تأییدِ دوبارهٔ ورود (`lib/reauth.js`، همان `ReauthGate` ِ برنامه): دادنِ دسترسی به دادهٔ خصوصی کارِ حساس است؛ رد و لغو
 *   (کم کردنِ دسترسی) نمی‌خواهند.
 * ⛔ زیرِ `routes/shop.js` (همان `requireUser`)؛ shop_id و نقش فقط از نشست (`requireShop`)؛ مدیر و کارمند ⇒ ‎403 owner_only‎.
 *    اجازهٔ دکانِ دیگر ⇒ ‎404 grant_not_found‎ (بی نشانی از بودنش).
 * ⛔ SUB-10: دستهٔ FREE — پس از پایانِ اشتراک هم صاحب باید ببیند و ببندد.
 */
const express = require('express');
const access = require('../lib/support-access');
const v = require('../lib/validate');
const { requireShop } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { requireRecentAuth } = require('../lib/reauth');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
const limit = rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'shop-support-access' });

function ownerOnly(req, res, next) {
  if (req.member?.role === 'owner') return next();
  return next(forbidden('دسترسیِ پشتیبانی را فقط صاحبِ دکان می‌بیند و می‌دهد', 'owner_only'));
}

router.get('/support-access', limit, requireShop, ownerOnly, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json({ grants: await access.list(req.shopId), policy: access.POLICY });
});

router.post('/support-access/:gid/accept', limit, requireShop, ownerOnly, requireRecentAuth, async (req, res) => {
  res.json(await access.ownerDecide(req, v.id(req.params.gid, { field: 'شناسهٔ اجازه' }), 'accept'));
});

router.post('/support-access/:gid/deny', limit, requireShop, ownerOnly, async (req, res) => {
  res.json(await access.ownerDecide(req, v.id(req.params.gid, { field: 'شناسهٔ اجازه' }), 'deny'));
});

router.post('/support-access/:gid/revoke', limit, requireShop, ownerOnly, async (req, res) => {
  res.json(await access.ownerDecide(req, v.id(req.params.gid, { field: 'شناسهٔ اجازه' }), 'revoke'));
});

module.exports = router;
