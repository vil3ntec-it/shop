'use strict';
/**
 * همگام‌سازی.
 *
 *   POST /api/sync   فرستادن تغییرات گوشی (با شناسه‌ی عملیات، ضد ثبت تکراری)
 *   GET  /api/sync   گرفتن تغییرات بعد از آخرین rev
 *
 * نام‌های قدیمی /shop/sync/push و /shop/sync/pull هم به همین‌ها می‌رسند.
 */
const express = require('express');
const { query, now } = require('../db');
const v = require('../lib/validate');
const sync = require('../lib/sync');
const audit = require('../lib/audit');
const { requireUser, requireShop, requireDataWrite } = require('../middleware/auth');
const { entitlementOf } = require('../lib/entitlement');

const router = express.Router();
router.use(requireUser, requireShop);

/** ثبت زمان آخرین همگام‌سازی دستگاه. */
async function touchDevice(req, deviceUid) {
  if (!deviceUid) return;
  await query(
    'UPDATE devices SET last_sync_at=$3, last_seen_at=$3 WHERE user_id=$1 AND device_uid=$2',
    [req.user.id, deviceUid, now()]
  ).catch(() => {});
}

async function pushHandler(req, res) {
  const deviceId = v.id(req.body?.deviceId, { field: 'شناسه دستگاه', required: false, max: 64 });
  const operationId = v.id(req.body?.operationId || req.body?.clientOperationId, {
    field: 'شناسه عملیات', required: false, max: 80,
  });
  const changes = Array.isArray(req.body?.changes) ? req.body.changes : [];

  const { result, replayed } = await sync.runIdempotent(
    { shopId: req.shopId, userId: req.user.id, deviceId, operationId },
    () => sync.pushChanges(
      //  S89: برنامهٔ کهنه (بی زنجیره) — با پرچمِ `shop.doc_chain` فقط رویداد، هرگز رد (F0-02)
      { shopId: req.shopId, userId: req.user.id, deviceId, role: req.role, finalized: 'log',
        ip: require('../middleware/ratelimit').clientIp(req) },
      changes
    )
  );

  await touchDevice(req, deviceId);
  res.json({ ...result, replayed, serverTime: now() });
}

async function pullHandler(req, res) {
  const since = v.integer(req.query?.since, { field: 'since', min: 0, max: Number.MAX_SAFE_INTEGER, def: 0 });
  const limit = v.integer(req.query?.limit, { field: 'limit', min: 1, max: sync.MAX_LIMIT, def: 1000 });
  const out = await sync.pullChanges(req.shopId, since, limit);
  await touchDevice(req, v.id(req.query?.deviceId, { required: false, max: 64 }));
  res.json(out);
}

/** وضعیت کوتاه همگام‌سازی — برای نوار «آخرین همگام‌سازی». */
router.get('/status', async (req, res) => {
  const ent = await entitlementOf(req.shopId);
  res.json({
    shopId: req.shopId,
    rev: await sync.currentRev(req.shopId),
    collections: sync.COLLECTIONS,
    role: require('../lib/permissions').legacyRole(req.role),
    actualRole: req.role,
    subscription: ent.subscription,
    serverTime: now(),
  });
});

/*
 * ⚠️ راهِ قدیم (F0-02) — هنوز کار می‌کند، فقط هشدار ثبت می‌شود.
 *
 * تنها موتورِ دفتر Sync v1 است (`/api/sync/v1/*`). push/pullِ این فایل
 * مستقیم در دفتر ادغام می‌کرد و سایهٔ v1 را جلو نمی‌برد؛ دستگاهی که هر دو
 * را می‌رفت، فیلدهای `$inc` (پرداخت‌ها، مانده‌ها) را دو بار جمع می‌کرد.
 * برنامه‌های تازه دیگر این‌جا نمی‌آیند، ولی طبقِ «قاعدهٔ سازگاری»
 * (docs/spec/02-current.md) تا وقتی کلاینتِ قدیمی هست، رد نمی‌شود — فقط
 * در لاگ می‌آید که چه کسی، با کدام نسخه، هنوز این راه را می‌رود.
 *
 * هر حساب و دستگاه هر ده دقیقه یک خط، نه هر درخواست یک خط.
 */
const LEGACY_WARN_MS = 10 * 60 * 1000;
const legacySeen = new Map();
function warnLegacy(req, _res, next) {
  try {
    const deviceId = String(req.body?.deviceId || req.query?.deviceId || '').slice(0, 64);
    const key = `${req.user?.id || ''}|${deviceId}`;
    const t = Date.now();
    if (!(t - (legacySeen.get(key) || 0) < LEGACY_WARN_MS)) {
      if (legacySeen.size > 5000) legacySeen.clear();
      legacySeen.set(key, t);
      console.warn(
        '[sync:legacy] %s %s — راهِ قدیمِ همگام‌سازی؛ Sync v1 جایش است (shop=%s user=%s device=%s app=%s v=%s)',
        req.method, req.originalUrl.split('?')[0], req.shopId || '', req.user?.id || '', deviceId || '-',
        String(req.get('X-App') || '-').slice(0, 32), String(req.get('X-App-Version') || '-').slice(0, 32),
      );
    }
  } catch { /* هشدار نباید خودِ درخواست را بشکند */ }
  next();
}

/*
 * خواندن باز است، نوشتن پشتِ اشتراک.
 *
 * دلیلش در `requireDataWrite` نوشته شده: داده‌ی فروشنده گروگان گرفته
 * نمی‌شود، ولی «چند کاربر روی یک دکان» — که قابلیت پولی است — دیگر
 * مجانی نیست.
 */
router.post('/', warnLegacy, requireDataWrite, pushHandler);
router.get('/', warnLegacy, pullHandler);
router.post('/push', warnLegacy, requireDataWrite, pushHandler);
router.get('/pull', warnLegacy, pullHandler);

module.exports = router;
module.exports.pushHandler = pushHandler;
module.exports.pullHandler = pullHandler;
