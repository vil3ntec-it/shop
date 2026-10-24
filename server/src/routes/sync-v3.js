'use strict';
/**
 * VILL3N Sync v3 — بخشِ ۳.۸. فقط `app=shop`؛ v1 و پمپ دست نمی‌خورند.
 *
 *   GET  /api/sync/v3/hello                 نسخه، سقف‌ها، جدول‌ها و سیاست‌ها
 *   POST /api/sync/v3/push                  opهای v3 (یا صفِ v2 با format=v2)
 *   GET  /api/sync/v3/pull                  اثرِ opهای دستگاه‌های دیگر (gzip)
 *   GET  /api/sync/v3/snapshot              ردیف‌های زنده + cursor (gzip)
 *   GET  /api/sync/v3/status                نقطهٔ وضعیت (SYN-08) و دستگاه‌ها (SYN-10)
 *   POST /api/sync/v3/report                تعدادِ منتظر، خطای آخر، اختلافِ ساعت
 *   GET  /api/sync/v3/conflicts             صندوقِ تعارض و وظیفه‌ها (SYN-07)
 *   POST /api/sync/v3/conflicts/:id/resolve keep | revert
 *
 * ⛔ تا `SYNC_V3_ENABLED` روشن نشود، **همه** همان ۴۰۴ِ سرورِ قدیمی‌اند —
 *    برنامه‌ای که امتحان می‌کند فرقی میانِ «سرورِ قدیمی» و «خاموش» نمی‌بیند.
 * ⛔ حساب از توکن (`requireSyncAccount`ِ v1، فقط خوانده می‌شود).
 * ⚠️ هر پاسخ `server_time` دارد (و سرآیندِ `X-Server-Time`) تا دستگاه
 *    اختلافِ ساعتش را نگه دارد (بخشِ ۳.۸.۱ بندِ ۸).
 */
const express = require('express');
const zlib = require('zlib');
const { promisify } = require('util');
const config = require('../config');
const sync = require('../lib/sync-v3');
const newerFields = require('../lib/newer-fields');
const safeLedger = require('../lib/safe-ledger');
const { rateLimit } = require('../middleware/ratelimit');
const { requireSyncAccount, requireSyncWrite } = require('../lib/sync-v1-auth');
const { forbidden, notFoundHandler } = require('../middleware/errors');
const v = require('../lib/validate');

const gzip = promisify(zlib.gzip);
const router = express.Router();

router.use((req, res, next) => {
  if (!config.syncV3.enabled) return notFoundHandler(req, res);
  res.set('X-Server-Time', String(Date.now()));
  res.set('Cache-Control', 'no-store');
  next();
});
router.use(rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'sync3ip' }));
router.use(requireSyncAccount);
router.use((req, res, next) => (req.sync.app === 'shop' && req.sync.accountKind === 'shop'
  ? next() : next(forbidden('همگام‌سازیِ v3 فقط برای دکان است', 'v3_shop_only'))));
router.use(rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'sync3',
  key: (req) => (req.sync ? 'shop:' + req.sync.accountId : ''),
}));

const deviceOf = (src) => v.id(src?.device_id || src?.deviceId, { field: 'شناسه دستگاه', required: true, max: 64 });

/** پاسخِ JSON، فشرده اگر برنامه بخواهد — روی 2G هر بایت مهم است. */
async function send(req, res, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  res.set('Content-Type', 'application/json; charset=utf-8');
  if (body.length > 1024 && String(req.headers['accept-encoding'] || '').includes('gzip')) {
    res.set('Content-Encoding', 'gzip');
    res.set('Vary', 'Accept-Encoding');
    return res.end(await gzip(body));
  }
  res.end(body);
}

router.get('/hello', (req, res) => {
  res.json({ ok: true, enabled: true, ...sync.describe() });
});

router.post('/push', requireSyncWrite, async (req, res, next) => {
  try {
    const b = req.body || {};
    //  گاوصندوق (PAY-18): همان قفلِ نگهبانِ v1 تا پایانِ پاسخ — پلِ v3⇒v1 همان `sync_rows` را
    //  می‌نویسد که آن نگهبان «ردیف هست؟» را از آن می‌خواند (‎lib/safe-ledger.js‎)
    if (safeLedger.touches(b.ops) && !(await safeLedger.hold(res, req.sync))) return;
    const appVersion = String(req.headers['x-app-version'] || '').slice(0, 32);
    //  A00: `null` ِ ساختِ پیش از A00 برای فیلدی که نمی‌شناسد کنار می‌رود (‎lib/newer-fields.js‎)
    const fl = newerFields.filterOps(b.ops, {
      version: appVersion, shape: b.format === 'v2' ? 'v1' : 'v3', caps: b.caps, client: b.client,
    });
    const out = await sync.push(req.sync, {
      deviceId: deviceOf(b),
      protocol: b.protocol,
      format: b.format === 'v2' ? 'v2' : 'v3',
      schemaVersion: Number(b.schema_version || b.schemaVersion || 0),
      ops: fl.ops,
      appVersion,
      queued: b.queued,
      lastError: b.last_error,
      clockOffsetMs: b.clock_offset_ms,
    });
    out.results = newerFields.mergeResults(fl, out.results);
    await send(req, res, { ok: true, ...out, cursor: out.head });
  } catch (err) {
    //  برنامه جلوتر از سرور است: هیچ چیزی اعمال نشد و opها روی برنامه می‌مانند
    if (err && err.status === 426) {
      return res.status(426).json({
        ok: false, error: 'UPGRADE_REQUIRED', message: err.message, protocol: sync.PROTOCOL, server_time: Date.now(),
      });
    }
    next(err);
  }
});

router.get('/pull', async (req, res, next) => {
  try {
    const out = await sync.pull(req.sync, {
      deviceId: deviceOf(req.query),
      since: Number(req.query?.since || 0),
      maxBytes: Number(req.query?.max_bytes || 0) || undefined,
      limit: Number(req.query?.limit || 0) || undefined,
    });
    await send(req, res, { ok: true, ...out });
  } catch (err) { next(err); }
});

router.get('/snapshot', async (req, res, next) => {
  try { await send(req, res, { ok: true, ...(await sync.snapshot(req.sync)) }); } catch (err) { next(err); }
});

router.get('/status', async (req, res, next) => {
  try {
    const deviceId = String(req.query?.device_id || req.query?.deviceId || '').slice(0, 64);
    res.json({ ok: true, ...(await sync.status(req.sync, deviceId)) });
  } catch (err) { next(err); }
});

router.post('/report', async (req, res, next) => {
  try {
    const b = req.body || {};
    res.json({ ok: true, ...(await sync.report(req.sync, deviceOf(b), {
      queued: b.queued, lastError: b.last_error, clockOffsetMs: b.clock_offset_ms,
      appVersion: String(req.headers['x-app-version'] || '').slice(0, 32),
    })) });
  } catch (err) { next(err); }
});

router.get('/conflicts', async (req, res, next) => {
  try {
    res.json({
      ok: true, server_time: Date.now(),
      conflicts: await sync.listConflicts(req.sync, {
        status: req.query?.status === 'all' ? 'all' : 'open', limit: Number(req.query?.limit || 100),
      }),
    });
  } catch (err) { next(err); }
});

router.post('/conflicts/:id/resolve', requireSyncWrite, async (req, res, next) => {
  try {
    const out = await sync.resolveConflict(req.sync, req.params.id, String(req.body?.action || ''), {
      userId: req.sync.userId,
    });
    res.json({ ok: true, ...out, server_time: Date.now() });
  } catch (err) { next(err); }
});

module.exports = router;
