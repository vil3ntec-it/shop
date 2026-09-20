'use strict';
/**
 * VILL3N Sync v1 — بخشِ ۲۰ پرامپت.
 *
 *   POST /api/sync/v1/push       دسته‌ای از تغییرها (فقط تغییر، نه کلِ داده)
 *   GET  /api/sync/v1/pull       تغییرهای دستگاه‌های دیگر، بعد از cursor
 *   GET  /api/sync/v1/snapshot   یک‌بار برای دستگاهِ تازه (gzip)
 *   GET  /api/sync/v1/status     حالِ همین دستگاه
 *   POST /api/sync/v1/errors     گزارشِ خطای برنامه
 *
 *   wss  /api/sync/v1/live       «چیزی عوض شد» ⇒ برنامه Pull می‌کند
 *
 * ── قاعده‌هایی که نباید بشکنند ─────────────────────────────────────────
 *  ⛔ **شناسهٔ حساب از توکن می‌آید، نه از درخواست** — `requireSyncAccount`
 *     آن را می‌نشاند. بی این، یک دکان می‌توانست در دفترِ دکانِ دیگری
 *     بنویسد.
 *  ⛔ **هر پرس‌وجو `app` را هم شرط می‌کند** — یک جدول برای دو بخش است.
 *  ⛔ **حذف نرم است.** هیچ ردیفی واقعاً نابود نمی‌شود و از پنل برمی‌گردد.
 *  ⚠️ نسخهٔ اسکیمای جلوتر از سرور ⇒ ۴۲۶ و **هیچ چیزی اعمال نمی‌شود**؛
 *     برنامه opها را نگه می‌دارد تا سرور به‌روز شود.
 */
const express = require('express');
const zlib = require('zlib');
const { promisify } = require('util');
const sync = require('../lib/sync-v1');
const { requireSyncAccount, requireSyncWrite } = require('../lib/sync-v1-auth');
const live = require('../lib/sync-v1-live');
const v = require('../lib/validate');

const gzip = promisify(zlib.gzip);
const router = express.Router();

router.use(requireSyncAccount);

/* ------------------------------------------------------------------ Push */

router.post('/push', requireSyncWrite, async (req, res, next) => {
  try {
    const deviceId = v.id(req.body?.device_id || req.body?.deviceId, {
      field: 'شناسه دستگاه', required: true, max: 64,
    });
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : [];
    const schemaVersion = Number(req.body?.schema_version || req.body?.schemaVersion || 0);

    let out;
    try {
      out = await sync.push(req.sync, {
        deviceId,
        schemaVersion,
        ops,
        appVersion: String(req.headers['x-app-version'] || '').slice(0, 32),
        queued: Number.isFinite(Number(req.body?.queued)) ? Number(req.body.queued) : null,
      });
    } catch (err) {
      //  نسخهٔ برنامه از سرور جلوتر است: هیچ چیزی اعمال نشد و برنامه باید
      //  opها را نگه دارد. شکلِ پاسخ همان بندِ ۲ پرامپت است، نه شکلِ عمومیِ خطا.
      if (err && err.status === 426) {
        return res.status(426).json({
          ok: false, error: 'UPGRADE_REQUIRED', message: err.message,
          schema_version: schemaVersion,
        });
      }
      throw err;
    }

    //  فقط وقتی واقعاً چیزی نشست، بقیهٔ دستگاه‌ها را بیدار کن
    if (out.applied > 0) live.broadcast(req.sync, out.head, deviceId);
    res.json({ ok: true, ...out, cursor: out.head });
  } catch (err) { next(err); }
});

/* ------------------------------------------------------------------ Pull */

router.get('/pull', async (req, res, next) => {
  try {
    const deviceId = v.id(req.query?.device_id || req.query?.deviceId, {
      field: 'شناسه دستگاه', required: true, max: 64,
    });
    const since = Number(req.query?.since || 0);
    const limit = Number(req.query?.limit || 0) || undefined;
    res.json({ ok: true, ...(await sync.pull(req.sync, { since, deviceId, limit })) });
  } catch (err) { next(err); }
});

/* -------------------------------------------------------------- Snapshot */

router.get('/snapshot', async (req, res, next) => {
  try {
    const snap = await sync.snapshot(req.sync);
    const body = JSON.stringify({ ok: true, ...snap });
    //  عکسِ کاملِ یک دفتر چند مگابایت می‌شود؛ روی اینترنتِ موبایل فشرده می‌رود
    if (String(req.headers['accept-encoding'] || '').includes('gzip')) {
      const packed = await gzip(Buffer.from(body, 'utf8'));
      res.set('Content-Encoding', 'gzip');
      res.set('Content-Type', 'application/json; charset=utf-8');
      return res.end(packed);
    }
    res.set('Content-Type', 'application/json; charset=utf-8');
    res.end(body);
  } catch (err) { next(err); }
});

/* ---------------------------------------------------------------- Status */

router.get('/status', async (req, res, next) => {
  try {
    const deviceId = String(req.query?.device_id || req.query?.deviceId || '').slice(0, 64);
    res.json({ ok: true, ...(await sync.status(req.sync, deviceId)) });
  } catch (err) { next(err); }
});

/* ------------------------------------------------- تعارض‌ها و حذف‌شده‌ها */

router.get('/conflicts', async (req, res, next) => {
  try {
    res.json({
      ok: true,
      conflicts: await sync.listConflicts(req.sync, {
        limit: Number(req.query?.limit || 100),
        includeRestored: req.query?.all === '1',
      }),
    });
  } catch (err) { next(err); }
});

router.get('/deleted', async (req, res, next) => {
  try {
    res.json({ ok: true, rows: await sync.listDeleted(req.sync, { limit: Number(req.query?.limit || 100) }) });
  } catch (err) { next(err); }
});

module.exports = router;
