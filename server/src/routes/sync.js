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
      { shopId: req.shopId, userId: req.user.id, deviceId, role: req.role },
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
    role: req.role,
    subscription: ent.subscription,
    serverTime: now(),
  });
});

/*
 * خواندن باز است، نوشتن پشتِ اشتراک.
 *
 * دلیلش در `requireDataWrite` نوشته شده: داده‌ی فروشنده گروگان گرفته
 * نمی‌شود، ولی «چند کاربر روی یک دکان» — که قابلیت پولی است — دیگر
 * مجانی نیست.
 */
router.post('/', requireDataWrite, pushHandler);
router.get('/', pullHandler);
router.post('/push', requireDataWrite, pushHandler);
router.get('/pull', pullHandler);

module.exports = router;
module.exports.pushHandler = pushHandler;
module.exports.pullHandler = pullHandler;
