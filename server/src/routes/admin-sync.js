'use strict';
/**
 * وضعیتِ همگام‌سازی در پنلِ مدیریت — بندِ ۱۳.۲ پرامپت.
 *
 *   GET  /api/admin/sync/status?app=      هر حساب و دستگاه: آخرین op، صف، تعارض
 *   GET  /api/admin/sync/conflicts        تعارض‌ها با امکانِ بازگرداندن
 *   POST /api/admin/sync/conflicts/:id/restore
 *   GET  /api/admin/sync/errors           خطاهای گزارش‌شدهٔ برنامه‌ها
 *   GET  /api/admin/sync/version-health   یک نسخه: چند دستگاه، چند خطا (شمرده، نه فهرست)
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
const { requireSuperAdmin } = require('../middleware/auth');
const { requireReason } = require('../lib/admin-access');
const finalized = require('../lib/finalized-sales');
const once = require('../lib/sync-restore-once');

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
    const conflicts = await sync.listConflicts(ctx, {
      limit: Math.min(500, Number(req.query?.limit || 100) || 100),
      includeRestored: req.query?.all === '1',
    });
    //  تعارض‌ها مقدارِ خودِ ردیف‌های حساب را دارند (نام، قیمت، مانده) —
    //  دیدنشان دیدنِ دادهٔ خصوصی است و سابقه دارد (ADMIN-CONTROL-PLANE.md)
    await audit.log({
      shopId: app === 'shop' ? accountId : '', actorType: 'admin', userId: req.admin.id,
      action: 'admin.sync_conflicts_viewed', targetType: 'account', targetId: accountId,
      ip: clientIp(req), detail: { app, count: conflicts.length },
    });
    res.json({ conflicts });
  } catch (err) { next(err); }
});

/*
 *  ⛔ بازگرداندن دادهٔ یک حساب را عوض می‌کند (opِ تازه به همهٔ دستگاه‌هایش)
 *  ⇒ فقط مدیرِ ارشد، فقط با دلیلِ نوشته (ADMIN-CONTROL-PLANE.md، v3 ۳۹.۸).
 *  ⛔ و فقط یک بار (`lib/sync-restore-once.js`، مهاجرتِ ۴۹۵): شناسه فقط رقم و یک شکل (۴۰۰ `bad_id`)؛ برگردانده‌شده ⇒ ۴۰۹
 *  `already_restored`؛ در حالِ رفتن ⇒ ۴۰۹ `restore_in_progress`؛ `Idempotency-Key` ⇒ همان پاسخ (`Idempotent-Replayed`).
 */
router.post('/conflicts/:id/restore', requireSuperAdmin, async (req, res, next) => {
  try {
    const id = once.conflictIdOf(req.params.id);
    const reason = requireReason(req);
    const done = await once.restoreOnce(req, id, async () => {
      //  SEC-03 (پرچمِ `shop.doc_chain`): مقدارِ بازنده فاکتورِ ثبت‌شده را سرِ جا عوض نمی‌کند ⇒ ۴۰۹ (‎lib/finalized-sales.js‎)
      await finalized.adminRestoreCheck(id, { adminId: req.admin.id, ip: clientIp(req) });
      return sync.restoreConflict(id);
    });
    if (done.replayed) {
      res.set('Idempotent-Replayed', 'true');
      return res.json(done.response);
    }
    const out = done.out;
    await audit.log({
      shopId: out?.ctx?.accountKind === 'shop' ? String(out.ctx.accountId || '') : '',
      actorType: 'admin', userId: req.admin.id, action: 'sync.conflict_restored', ip: clientIp(req),
      targetType: 'sync_conflict', targetId: id, detail: { ...(out || {}), reason },
    });
    res.json(done.response);
  } catch (err) { next(err); }
});

router.get('/errors', async (req, res, next) => {
  try {
    const app = sectionOf(req.query?.app);
    const limit = Math.min(500, Math.max(1, Number(req.query?.limit || 100) || 100));   //  ممیزیِ ۳۵ (X1): منفی ⇒ ۵۰۰ ِ LIMIT
    const rows = app
      ? await many('SELECT * FROM client_errors WHERE app=$1 ORDER BY at DESC LIMIT $2', [app, limit])
      : await many('SELECT * FROM client_errors ORDER BY at DESC LIMIT $1', [limit]);
    res.json({ errors: rows });
  } catch (err) { next(err); }
});

/**
 * ⛔ شورا، د۶ — «کانالِ پایدار»ِ آپدیتِ پمپ (مرکز فرمان) از همین می‌پرسد.
 * هر دو عدد با ‎COUNT‎ شمرده می‌شوند، نه از فهرستِ آخرین خطاها (که سقفِ ۵۰۰
 * داشت و نسخهٔ کهنه را از قلم می‌انداخت). ‎installs‎ = دستگاه‌هایی که با همین
 * نسخه همگام شده‌اند — «صفر خطا» بی دستگاه یعنی «کسی نصبش نکرده»، نه «سالم».
 */
router.get('/version-health', async (req, res, next) => {
  try {
    const app = sectionOf(req.query?.app);
    const version = String(req.query?.version || '').trim().replace(/^v/i, '').slice(0, 32);
    if (!app) return next(badRequest('برنامه لازم است', 'app_required'));
    if (!/^\d+(\.\d+){1,3}$/.test(version)) return next(badRequest('نسخه درست نیست', 'bad_version'));
    const forms = [version, `v${version}`];
    const [inst, errs] = await Promise.all([
      many(`SELECT COUNT(*)::int AS n FROM (SELECT DISTINCT account_id, device_id FROM sync_devices
              WHERE app=$1 AND app_version = ANY($2::text[])) d`, [app, forms]),
      many('SELECT COUNT(*)::int AS n FROM client_errors WHERE app=$1 AND app_version = ANY($2::text[])', [app, forms]),
    ]);
    res.json({ app, version, installs: Number(inst[0]?.n) || 0, crashes: Number(errs[0]?.n) || 0 });
  } catch (err) { next(err); }
});

module.exports = router;
