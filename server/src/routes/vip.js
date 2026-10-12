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
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();

router.post(
  '/redeem',
  //  همان سقفی که ورودِ شاگرد با کد دارد: هر دو «کدی که حدس زدنش
  //  نباید ممکن باشد» هستند، پس یک عدد برای هر دو
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'vip-redeem' }),
  requireUser,
  requireShop,
  async (req, res, next) => {
    try {
      const code = v.text(req.body?.code, { max: 20, required: true, field: 'کد' });
      const out = await vip.redeem(code, { userId: req.user.id, shopId: req.shopId });
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
