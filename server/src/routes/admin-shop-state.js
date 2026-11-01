'use strict';
/**
 * پنلِ مرکزی، فاز ۳ — سلامت و «حالت‌های مدیریتیِ» یک دکان.
 *
 *   GET  /api/admin/shops/:id/health                  فراداده: همگام‌سازی، نسخهٔ هر دستگاه، خطا، اشتراک، حالت‌ها
 *   GET  /api/admin/shops/:id/holds                   حالت‌های باز و تاریخچه
 *   POST /api/admin/shops/:id/holds                   {kind, service?, reason} — گذاشتن
 *   POST /api/admin/shops/:id/holds/:holdId/lift      {reason} — برداشتن
 *
 * زیرِ `requireAdmin` ِ `routes/admin.js` (و مرزِ نقشِ `lib/admin-roles.js`). نقشِ هر حالت و قاعده‌ها:
 * `lib/shop-holds.js`. شناسهٔ دکان فقط از نشانی، و هر حالت فقط از **همان** دکان برداشته می‌شود.
 */
const express = require('express');
const v = require('../lib/validate');
const holds = require('../lib/shop-holds');
const { one } = require('../db');
const { notFound } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');
const { requireReason } = require('../lib/admin-access');

const router = express.Router({ mergeParams: true });

const shopIdOf = (req) => v.id(req.params.id);

router.get('/health', async (req, res, next) => {
  const out = await require('../lib/shop-health').healthOf(shopIdOf(req));
  if (!out) return next(notFound('دکان پیدا نشد'));
  res.json(out);
});

router.get('/holds', async (req, res, next) => {
  const id = shopIdOf(req);
  if (!(await one('SELECT 1 FROM shops WHERE id=$1', [id]))) return next(notFound('دکان پیدا نشد'));
  const limit = v.integer(req.query?.limit, { min: 1, max: 500, def: 100 });
  res.json({
    shopId: id, ...(await holds.listOf(id, { limit })),
    kinds: holds.KINDS, services: holds.SERVICES, roles: holds.ROLES_OF,
  });
});

/** پس از بستنِ دسترسی، سوکت‌های زندهٔ همان دکان هم بسته شوند. */
function closeLive(shopId) {
  try {
    require('../lib/sync-v1-live').closeRoom({ app: 'shop', accountKind: 'shop', accountId: shopId });
  } catch { /* سوکتی نبود */ }
}

router.post('/holds', async (req, res) => {
  const id = shopIdOf(req);
  const reason = requireReason(req);
  const out = await holds.place(id, req, { reason, ip: clientIp(req) });
  if (out.accessChanged) closeLive(id);
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  const { accessChanged, ...body } = out;
  res.status(out.replayed ? 200 : 201).json(body);
});

router.post('/holds/:holdId/lift', async (req, res) => {
  const id = shopIdOf(req);
  const holdId = v.id(req.params.holdId, { field: 'شناسهٔ حالت' });
  const reason = requireReason(req);
  const out = await holds.lift(id, holdId, req, { reason, ip: clientIp(req) });
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  res.json(out);
});

module.exports = router;
