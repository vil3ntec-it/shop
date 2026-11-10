'use strict';
/**
 * خرج کردن کد وی‌آی‌پی — سمت کاربر.
 *
 * کاربر شش رقمی را که به ایمیلش رسیده اینجا می‌زند و اشتراکِ دکانش
 * همان لحظه فعال می‌شود. نه واسطه‌ای لازم است، نه صاحب سامانه باید
 * پشت پنل باشد.
 *
 * محدودیت نرخ تنگ است: کد شش رقم دارد و بدون این، کسی می‌توانست با
 * آزمودن پشت‌سرهم به کدِ زنده‌ی دیگری برسد.
 */
const express = require('express');
const { now } = require('../db');
const v = require('../lib/validate');
const vip = require('../lib/vip-codes');
const offline = require('../lib/shop-offline-codes');
const subs = require('../lib/subscriptions');
const audit = require('../lib/audit');
const { entitlementOf } = require('../lib/entitlement');
const config = require('../config');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit, refund, clientIp } = require('../middleware/ratelimit');

const router = express.Router();

/*
 *  ⛔ ممیزیِ ۳۵ (X4): سطلِ IP به‌تنهایی حدس از چند IP را نمی‌بندد ⇒ دو سطلِ «تلاشِ ناموفق» پس از احراز:
 *    • هر حساب (`u:`) — صاحب و کارمند؛
 *    • هر دکان، فقط کارمندها با هم (`s:`) — صد حسابِ کارمندِ یک دکان هم با هم همان ده تا. صاحب در این سطل نیست:
 *      کارمند (یا حسابِ دزدیدهٔ او) هرگز درِ کدِ صاحب را نمی‌بندد.
 *  موفق پس داده می‌شود (`refundable`)؛ کدِ خرج‌شده/باطل/دکانِ دیگر ناموفق است (خودش نشانِ «این کد هست» است).
 *  قفل موقت است (`vipFailWindowMs`) و فقط همین در: ورود، نشست و بقیهٔ دکان دست نمی‌خورند.
 */
const VIP_FAIL_USER = 'vip-redeem-fail-user';
const VIP_FAIL_SHOP = 'vip-redeem-fail-shop';
const failBucket = (keyPrefix, key) => rateLimit({
  max: config.rateLimit.vipFailMax, windowMs: config.rateLimit.vipFailWindowMs, keyPrefix, key, refundable: true,
});
const failPerUser = failBucket(VIP_FAIL_USER, (req) => `u:${req.user.id}`);
const failPerShop = failBucket(VIP_FAIL_SHOP, (req) => `s:${req.shopId}`);
const failPerStaff = (req, res, next) => (req.member?.role === 'owner' ? next() : failPerShop(req, res, next));

router.post(
  '/redeem',
  //  همان سقفی که ورودِ شاگرد با کد دارد: هر دو «کدی که حدس زدنش
  //  نباید ممکن باشد» هستند، پس یک عدد برای هر دو
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'vip-redeem' }),
  requireUser,
  requireShop,
  failPerUser,
  failPerStaff,
  async (req, res, next) => {
    try {
      const code = v.text(req.body?.code, { max: 20, required: true, field: 'کد' });
      const out = await vip.redeem(code, { userId: req.user.id, shopId: req.shopId });
      refund(req, VIP_FAIL_USER);
      refund(req, VIP_FAIL_SHOP);
      await audit.log({
        shopId: req.shopId, userId: req.user.id, action: 'vip.redeemed',
        detail: { plan: out.plan, days: out.days }, ip: clientIp(req),
      });
      const ent = await entitlementOf(req.shopId);
      res.status(201).json({
        ok: true,
        message: `اشتراک شما فعال شد — ${out.days} روز.`,
        subscription: subs.stateOf(out.subscription),
        entitlement: ent,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

/**
 * کدِ اشتراکِ آفلاین ⇒ اشتراکِ دکانِ همین حساب (lib/shop-offline-codes.js).
 *
 * برنامه کد را بی اینترنت سنجیده و باز کرده؛ این‌جا وقتی آنلاین شد می‌آورد
 * تا سرور هم بداند. ⛔ یک کد ⇒ یک دکان؛ باطل ⇒ ۴۱۰.
 */
router.post(
  '/offline-code',
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'shop-offline-code' }),
  requireUser,
  requireShop,
  async (req, res, next) => {
    try {
      const code = v.text(req.body?.code, { max: 300, required: true, field: 'کد' });
      const computer = v.text(req.body?.computer, { max: 40, required: true, field: 'شناسهٔ کامپیوتر' });
      const out = await offline.redeem({ shopId: req.shopId, userId: req.user.id, code, computer });
      await audit.log({
        shopId: req.shopId, userId: req.user.id, action: 'shop.offline_code_redeemed',
        detail: { serial: out.offline.serial, status: out.status }, ip: clientIp(req),
      });
      res.json({
        ok: true,
        status: out.status,
        offline: out.offline,
        entitlement: await entitlementOf(req.shopId),
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

module.exports = router;
