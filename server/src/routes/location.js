'use strict';
/**
 * لوکیشن دستگاه.
 *
 *   POST /api/v1/location        ثبت لوکیشن (با حساب یا بدون حساب)
 *   GET  /api/v1/location/mine   آخرین لوکیشن‌های همین حساب
 *
 * ── چرا بدون حساب هم قبول می‌شود ───────────────────────────────────
 * قرار صاحب مخزن: «بدون اینکه برنامه برود ثبت‌نام کند هم لوکیشن باید
 * روشن باشد و لوکیشن طرف ثبت بشود و بیاید به سرور.» پس این مسیر پشت
 * توکن نیست؛ شناسه‌ی دستگاه کافی است. اگر توکن هم آمده باشد، ردیف به
 * همان حساب بسته می‌شود.
 *
 * ── چه چیزی جلوی سوءاستفاده را می‌گیرد ─────────────────────────────
 * مسیرِ باز یعنی هر کسی می‌تواند صدا بزند، پس محدودیت نرخ روی همان
 * شناسه‌ی دستگاه است نه فقط IP: یک دستگاه در هر پنجره چند بار بیشتر
 * نمی‌فرستد. داده‌ی ذخیره‌شده هم فقط دو عدد است و هیچ چیزی از دفتر
 * دکان در آن نیست.
 */
const express = require('express');
const geo = require('../lib/geo');
const tokens = require('../lib/tokens');
const { one, many } = require('../db');
const config = require('../config');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireUser } = require('../middleware/auth');
const { badRequest } = require('../middleware/errors');

const router = express.Router();

//  یک دستگاه در هر پنجره‌ی نرخ، این‌قدر ردیف می‌سازد و بس. جابه‌جا شدنِ
//  یک دکان در روز چند بار است، نه چند صد بار.
const locationLimit = rateLimit({
  max: Math.max(20, config.rateLimit.otpMax * 4),
  keyPrefix: 'location',
  key: (req) => String(req.body?.device?.uid || req.body?.device?.deviceId || req.body?.deviceUid || '').slice(0, 64) || null,
});

/** اگر توکن آمده و درست بود، کاربرش را برمی‌گرداند؛ وگرنه هیچ. */
async function userIfAny(req) {
  const m = /^Bearer\s+(.+)$/i.exec((req.headers.authorization || '').trim());
  if (!m) return null;
  const row = await tokens.verify(m[1].trim(), 'access');
  if (!row) return null;
  const user = await one('SELECT id, status FROM users WHERE id=$1', [row.subject_id]);
  return user && user.status === 'active' ? user : null;
}

router.post('/', locationLimit, async (req, res, next) => {
  const deviceUid = String(req.body?.device?.uid || req.body?.device?.deviceId || req.body?.deviceUid || '');
  const user = await userIfAny(req);
  const raw = req.body?.location || req.body;

  const saved = await geo.record(
    { deviceUid, userId: user ? user.id : '', ip: clientIp(req) },
    raw
  );
  if (!saved) return next(badRequest('لوکیشن فرستاده نشده است', 'location_required'));

  //  همان دستگاه حالا حساب دارد؟ ردیف‌های بی‌نامش هم مال همان حساب است
  if (user) await geo.claimDevice(deviceUid, user.id);

  res.status(201).json({ ok: true, ...saved, linked: !!user });
});

router.get('/mine', requireUser, async (req, res) => {
  const rows = await many(
    'SELECT lat, lng, accuracy, source, label, created_at FROM device_locations WHERE user_id=$1 ORDER BY created_at DESC LIMIT 20',
    [req.user.id]
  );
  res.json({
    locations: rows.map(r => ({
      lat: Number(r.lat), lng: Number(r.lng), accuracy: Number(r.accuracy),
      source: r.source, label: r.label, at: Number(r.created_at),
    })),
  });
});

module.exports = router;
