'use strict';
/**
 * وضعیتِ همگام‌سازی در پنلِ مدیریت — بندِ ۱۳.۲ پرامپت.
 *
 *   GET  /api/admin/sync/status?app=      هر حساب و دستگاه: آخرین op، صف، تعارض
 *   GET  /api/admin/sync/conflicts        تعارض‌ها با امکانِ بازگرداندن
 *   POST /api/admin/sync/conflicts/:id/restore
 *   GET  /api/admin/sync/errors           خطاهای گزارش‌شدهٔ برنامه‌ها
 *
 * ⚠️ نسخهٔ بازنده هیچ‌وقت پاک نشده — این‌جا برمی‌گردد و به شکلِ یک opِ
 * تازه به دستگاه‌ها می‌رسد. «هیچ داده‌ای بی‌صدا حذف نمی‌شود.»
 */
const express = require('express');
const sync = require('../lib/sync-v1');
const { many } = require('../db');
const audit = require('../lib/audit');
const { sectionOf } = require('../lib/tenancy');
const { badRequest } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

router.get('/status', async (req, res, next) => {
  try {
    const app = sectionOf(req.query?.app) || 'shop';
    res.json({
      app,
      devices: await sync.adminStatus(app, {
        accountId: String(req.query?.account || '').slice(0, 80),
        limit: Math.min(500, Number(req.query?.limit || 100) || 100),
      }),
    });
  } catch (err) { next(err); }
});

router.get('/conflicts', async (req, res, next) => {
  try {
    const app = sectionOf(req.query?.app) || 'shop';
    const accountId = String(req.query?.account || '').slice(0, 80);
    if (!accountId) return next(badRequest('شناسهٔ حساب لازم است', 'account_required'));
    const ctx = { app, accountKind: app === 'pump' ? 'station' : 'shop', accountId };
    res.json({
      conflicts: await sync.listConflicts(ctx, {
        limit: Math.min(500, Number(req.query?.limit || 100) || 100),
        includeRestored: req.query?.all === '1',
      }),
    });
  } catch (err) { next(err); }
});

router.post('/conflicts/:id/restore', async (req, res, next) => {
  try {
    const out = await sync.restoreConflict(req.params.id);
    await audit.log({
      actorType: 'admin', action: 'sync.conflict_restored', ip: clientIp(req),
      targetType: 'sync_conflict', targetId: String(req.params.id), detail: out || {},
    });
    res.json({ ok: true, ...(out || {}) });
  } catch (err) { next(err); }
});

router.get('/errors', async (req, res, next) => {
  try {
    const app = sectionOf(req.query?.app);
    const limit = Math.min(500, Number(req.query?.limit || 100) || 100);
    const rows = app
      ? await many('SELECT * FROM client_errors WHERE app=$1 ORDER BY at DESC LIMIT $2', [app, limit])
      : await many('SELECT * FROM client_errors ORDER BY at DESC LIMIT $1', [limit]);
    res.json({ errors: rows });
  } catch (err) { next(err); }
});

module.exports = router;
