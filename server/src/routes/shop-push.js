'use strict';
/**
 * پوشِ گوشیِ دکان (Firebase/FCM؛ آیفون بعداً APNs) — `/api/shop/push` (تصمیمِ O-01، `docs/FIREBASE-SETUP-fa.md`).
 *
 *   GET    /config    `{enabled, server: {ready, fcm, apns}, device: {registered}, orderAlerts}` — برنامه پیش از هر
 *                     کاری همین را می‌پرسد: خاموش ⇒ Firebase روی گوشی اصلاً راه نمی‌افتد و توکنی ساخته نمی‌شود
 *   POST   /devices   `{token, provider?: fcm|apns, platform?: android|ios}` ⇒ ثبتِ توکنِ **همین** عضو و **همین** دستگاه
 *   DELETE /devices   `{token?}` ⇒ توکن(های) همین عضو روی همین دستگاه کهنه می‌شوند (خروج، خاموش کردنِ پرچم)
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.device_push` (live_config، دکان یا `all`) ثبت ۴۰۳ `device_push_disabled` است و هیچ
 *    توکنی جمع نمی‌شود؛ پاک کردن همیشه باز است.
 * ⛔ کاربر، دکان و دستگاه فقط از توکنِ ورود (`requireUser`، `requireShop`، `req.device`)؛ `userId`/`shopId` ِ بدنه
 *    نادیده (SEC-AUTH-11). عضوِ برداشته‌شده یا دستگاهِ خارج‌شده ⇒ توکن کهنه (`lib/push.js`).
 * ⛔ خبر و اعلان پشتِ اشتراک نیست (SUB-10: FREE)؛ هر عضوِ فعال (پیک نه — درِ `/api/shop` به او بسته است).
 * سقف: سطلِ IP پیش از احراز؛ و در `lib/push.js` حداکثر ۱۰ توکنِ زنده برای هر کس.
 */
const express = require('express');
const config = require('../config');
const push = require('../lib/push');
const apns = require('../lib/apns');
const notify = require('../lib/order-notify');
const { query } = require('../db');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden, badRequest } = require('../middleware/errors');

const FLAG_KEY = 'shop.device_push';
/** FCM: نویسه‌های امنِ base64url و «:» (حدودِ ۱۵۰ تا ۲۰۰)؛ APNs: هگزِ ۶۴ نویسه‌ای (یا بلندتر در آینده) */
const TOKEN_RE = { fcm: /^[A-Za-z0-9_:-]{32,500}$/, apns: /^[0-9a-fA-F]{64,200}$/ };

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-push-ip' }));
router.use(requireUser, requireShop);

function decode(text) { try { return JSON.parse(text); } catch { return null; } }

/** همان قاعدهٔ پرچم‌های دکان: مقدارِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است */
async function flagOf(shopId) {
  const rows = (await query(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)', [FLAG_KEY, 'all', String(shopId)]
  )).rows;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

const deviceOf = (req) => (req.device ? String(req.device.device_uid || req.device.id || '') : '');

router.get('/config', async (req, res) => {
  const enabled = await flagOf(req.shopId);
  const cfg = await push.current();
  const apnsOn = cfg.enabled && Boolean(apns.status().configured);
  const fcm = cfg.enabled && Boolean(cfg.serviceAccount);
  res.json({
    enabled,
    //  «سرور می‌تواند بفرستد؟» — فقط بله/نه، هرگز نامِ پروژه یا حساب
    server: { ready: fcm || apnsOn, fcm, apns: apnsOn },
    device: {
      registered: enabled && await push.deviceRegistered({
        app: 'shop', userId: req.user.id, shopId: req.shopId, deviceUid: deviceOf(req),
      }),
    },
    orderAlerts: await notify.flagOf(req.shopId),
  });
});

router.post('/devices', async (req, res, next) => {
  if (!(await flagOf(req.shopId))) {
    return next(forbidden('پوشِ گوشی برای این دکان روشن نیست', 'device_push_disabled'));
  }
  const provider = req.body?.provider === undefined ? 'fcm' : String(req.body.provider);
  if (!TOKEN_RE[provider]) return next(badRequest('سرویسِ پوش ناشناخته است', 'bad_provider'));
  const token = typeof req.body?.token === 'string' ? req.body.token.trim() : '';
  if (!TOKEN_RE[provider].test(token)) return next(badRequest('توکنِ پوش معتبر نیست', 'bad_push_token'));
  const platform = ['android', 'ios'].includes(req.body?.platform) ? req.body.platform : (provider === 'apns' ? 'ios' : 'android');
  await push.register({
    app: 'shop', token, provider, platform,
    userId: req.user.id, shopId: req.shopId, deviceUid: deviceOf(req), exclusive: true,
  });
  res.status(201).json({ ok: true, registered: true });
});

router.delete('/devices', async (req, res) => {
  const raw = req.body?.token ?? req.query?.token;
  const token = typeof raw === 'string' ? raw.trim().slice(0, 500) : '';
  const revoked = await push.unregisterOwn({ app: 'shop', userId: req.user.id, deviceUid: deviceOf(req), token });
  res.json({ ok: true, revoked });
});

module.exports = router;
module.exports.FLAG_KEY = FLAG_KEY;
module.exports.flagOf = flagOf;
