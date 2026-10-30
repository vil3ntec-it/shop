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
const { rateLimit } = require('../middleware/ratelimit');
const config = require('../config');
const { requireSyncAccount, requireSyncWrite, tokenOf } = require('../lib/sync-v1-auth');
const live = require('../lib/sync-v1-live');
const v = require('../lib/validate');
const syncEpoch = require('../lib/sync-epoch');
const { SHOP } = require('../lib/tenancy');

const gzip = promisify(zlib.gzip);
const router = express.Router();

//  ⛔ سطلِ خودِ همگام‌سازی — نه سطلِ عمومیِ آی‌پی (شرح: ‎config.rateLimit.syncMax‎).
//  اول به‌ازای آی‌پی (برای درخواستِ بی توکن)، بعد به‌ازای **حساب**.
router.use(rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'syncip' }));
router.use(requireSyncAccount);
router.use(rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'sync',
  key: (req) => (req.sync ? req.sync.app + ':' + req.sync.accountId : ''),
}));

/*
 *  ⛔ پمپ: همگام‌سازی فقط با خدماتِ سرور در پلن (۱۴۰۵/۰۷/۲۰) — استاندارد
 *  و دائمیِ بی‌تمدید به سرور وصل نمی‌شوند. ‎middleware/pump-services.js‎
 */
const pumpOnline = require('../middleware/pump-services').requirePumpServices();
function pumpServicesOnly(req, res, next) {
  if (req.sync && req.sync.accountKind === 'station') return pumpOnline(req, res, next);
  next();
}

/*
 *  §۹.۵: «دورانِ بازیابی» کنارِ pull و Snapshot ِ دکان — فقط با پرچمِ روشنِ `SYNC_EPOCH_ENABLED`.
 *  برنامه (`V1Restore.pulled`، وب `epochLive`) درِ جدای `GET /api/shop/sync-epoch` را **فقط** وقتی
 *  می‌زند که pull ِ پیشین دوران آورده باشد؛ خاموش ⇒ این کلید نیست ⇒ هیچ درخواستی که ۴۰۴ بگیرد.
 *  پمپ و پرچمِ خاموش ⇒ همان پاسخِ پیشین. شکستِ خواندنِ دوران pull را نمی‌خواباند (فقط کلید نیست).
 */
async function epochFor(ctx) {
  if (!config.syncEpoch.enabled || !ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app) return undefined;
  try {
    return await syncEpoch.forAccount(ctx, 'v1');
  } catch (err) {
    console.error('[sync-epoch] دوران کنارِ pull خوانده نشد:', err.message);
    return undefined;
  }
}

/* ------------------------------------------------------- بلیتِ سوکت (F0-11) */
/**
 * `POST /live/ticket` — بلیتِ یک‌بارمصرفِ ۶۰ ثانیه‌ای برای `wss …/live?ticket=`.
 * توکن در سرآیند می‌ماند و هرگز در آدرس (و لاگ) نمی‌نشیند.
 */
router.post('/live/ticket', (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(live.issueTicket(tokenOf(req)));
});

/* ------------------------------------------------------------------ Push */

/*
 *  دکان: سقفِ تخفیف/لغو/برگشت و «برگهٔ امضاشده» (STF-02/03، بخشِ ۱۰.۱) —
 *  ‎lib/staff-approval.js‎. حسابِ پمپ بی هیچ کاری از آن رد می‌شود.
 */
const { v1Guard: shopStaffGuard } = require('../lib/staff-approval');
/*
 *  دکان: `null` ِ صریحِ نسخهٔ قدیمی‌تر برای فیلدی که هنوز نمی‌شناسد کنار
 *  می‌رود (A00) — ‎lib/newer-fields.js‎. پمپ از آن هم بی هیچ کاری رد می‌شود.
 */
const { v1NewerFields: shopNewerFields } = require('../lib/newer-fields');

//  ترتیب مهم است: پالایشِ null opِ نادرست را درست می‌کند (کلیدِ null ِ ناشناخته بیرون، hash ِ تازه)،
//  پس نگهبان باید همان دسته‌ای را ببیند که به v1 می‌رسد — نه دستهٔ پیش از پالایش.
router.post('/push', requireSyncWrite, pumpServicesOnly, shopNewerFields, shopStaffGuard, async (req, res, next) => {
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

router.get('/pull', pumpServicesOnly, async (req, res, next) => {
  try {
    const deviceId = v.id(req.query?.device_id || req.query?.deviceId, {
      field: 'شناسه دستگاه', required: true, max: 64,
    });
    const since = Number(req.query?.since || 0);
    const limit = Number(req.query?.limit || 0) || undefined;
    const out = await sync.pull(req.sync, { since, deviceId, limit });
    const epoch = await epochFor(req.sync);
    res.json({ ok: true, ...out, ...(epoch ? { epoch } : {}) });
  } catch (err) { next(err); }
});

/* -------------------------------------------------------------- Snapshot */

router.get('/snapshot', pumpServicesOnly, async (req, res, next) => {
  try {
    //  دورانِ پیش از Snapshot: دفترِ ساخته‌شده از آن دست‌کم همین دوران را دارد
    const epoch = await epochFor(req.sync);
    const snap = await sync.snapshot(req.sync);
    const body = JSON.stringify({ ok: true, ...snap, ...(epoch ? { epoch } : {}) });
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

/* ------------------------------------------------------- عکسِ کالاها */
//  شرح: ‎lib/product-photos.js‎. عکس base64 در JSON می‌آید و می‌رود — وب،
//  اندروید و کامپیوتر همه همین را بی کتابخانهٔ اضافه می‌خوانند.
const photos = require('../lib/product-photos');

router.get('/photos', async (req, res, next) => {
  try {
    res.json({ ok: true, ...(await photos.list(req.sync, Number(req.query?.since || 0), Number(req.query?.limit || 500))) });
  } catch (err) { next(err); }
});

router.get('/photos/:productId', async (req, res, next) => {
  try {
    const p = await photos.get(req.sync, req.params.productId);
    if (!p) return res.status(404).json({ ok: false, error: 'NOT_FOUND', message: 'عکسی نیست' });
    res.set('Cache-Control', 'private, max-age=60');
    res.json({ ok: true, hash: p.hash, type: photos.sniff(p.data), data: p.data.toString('base64') });
  } catch (err) { next(err); }
});

router.put('/photos/:productId', requireSyncWrite, async (req, res, next) => {
  try {
    const b64 = String(req.body?.data || '');
    if (b64.length > Math.ceil(photos.MAX_BYTES * 4 / 3) + 8) {
      return res.status(413).json({ ok: false, error: 'TOO_LARGE', message: 'عکس بزرگ‌تر از ۶۰۰ کیلوبایت است' });
    }
    const deviceId = String(req.body?.device_id || req.headers['x-device'] || '').slice(0, 64);
    const out = await photos.put(req.sync, req.params.productId, Buffer.from(b64, 'base64'), deviceId);
    live.relay(req.sync, { event: 'photos', rev: out.rev }, deviceId);
    res.json({ ok: true, ...out });
  } catch (err) { next(err); }
});

router.delete('/photos/:productId', requireSyncWrite, async (req, res, next) => {
  try {
    const deviceId = String(req.query?.device_id || req.headers['x-device'] || '').slice(0, 64);
    const out = await photos.remove(req.sync, req.params.productId, deviceId);
    if (out.removed) live.relay(req.sync, { event: 'photos', rev: out.rev }, deviceId);
    res.json({ ok: true, ...out });
  } catch (err) { next(err); }
});

/* ---------------------------------------------- صندوقِ عکس (دوربینِ گوشی) */

router.post('/photo-inbox', requireSyncWrite, async (req, res, next) => {
  try {
    const b64 = String(req.body?.data || '');
    if (b64.length > Math.ceil(photos.INBOX_MAX_BYTES * 4 / 3) + 8) {
      return res.status(413).json({ ok: false, error: 'TOO_LARGE', message: 'عکس بزرگ‌تر از ۲ مگابایت است' });
    }
    const deviceId = String(req.body?.device_id || req.headers['x-device'] || '').slice(0, 64);
    const out = await photos.inboxPut(req.sync, Buffer.from(b64, 'base64'), {
      pair: req.body?.pair, productId: req.body?.product_id || '', deviceId,
    });
    live.relay(req.sync, { event: 'inbox', id: out.id, pair: out.pair, product_id: out.product_id }, deviceId);
    res.json({ ok: true, ...out });
  } catch (err) { next(err); }
});

router.get('/photo-inbox', async (req, res, next) => {
  try { res.json({ ok: true, items: await photos.inboxList(req.sync, req.query?.pair) }); } catch (err) { next(err); }
});

router.get('/photo-inbox/:id', async (req, res, next) => {
  try {
    const p = await photos.inboxGet(req.sync, req.params.id);
    if (!p) return res.status(404).json({ ok: false, error: 'NOT_FOUND', message: 'عکس دیگر در صندوق نیست' });
    res.json({ ok: true, product_id: p.product_id, type: photos.sniff(p.data), data: p.data.toString('base64') });
  } catch (err) { next(err); }
});

router.delete('/photo-inbox/:id', requireSyncWrite, async (req, res, next) => {
  try { res.json({ ok: true, removed: await photos.inboxDelete(req.sync, req.params.id) }); } catch (err) { next(err); }
});

module.exports = router;
