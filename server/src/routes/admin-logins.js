'use strict';
/**
 * میزِ «ورودها» در پنلِ مدیریت — بندِ ۳.۹ پرامپتِ ورود.
 *
 *   GET  /api/admin/logins?email=&app=       درخواست‌های اخیر با حالشان
 *   GET  /api/admin/logins/stats             p50/p95، درصدِ ناموفق، مدارشکن
 *   POST /api/admin/logins/:id/resend        دوباره بفرست
 *   POST /api/admin/logins/:id/reveal        کد را به مدیر نشان بده
 *   POST /api/admin/logins/unlock            قفلِ ۱۵ دقیقه‌ای را بردار
 *
 * ── چرا لازم است ───────────────────────────────────────────────────────
 * «وقتی مشتری زنگ می‌زند کد نیامد.» بی این میز، جوابِ صاحبِ سامانه فقط
 * حدس است. این‌جا معلوم می‌شود ایمیل **رفت یا نرفت و چرا**.
 *
 * ── دو قاعده ───────────────────────────────────────────────────────────
 *  ⛔ **نمایشِ کد فقط مدیرِ کل و همیشه با ثبت در دفترِ رخدادها.** این
 *     در پشتیِ ورود است و باید مثلِ در پشتی رفتار شود: کمیاب، ثبت‌شده،
 *     و فقط برای کدی که هنوز زنده است.
 *  ⛔ **کد هیچ‌وقت در فهرست نمی‌آید** — فقط در پاسخِ همان مسیرِ جدا.
 */
const express = require('express');
const codes = require('../lib/login-codes');
const outbox = require('../lib/login-outbox');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { badRequest, notFound } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

router.get('/', async (req, res, next) => {
  try {
    res.json({
      requests: await codes.listRequests({
        email: String(req.query?.email || ''),
        app: String(req.query?.app || ''),
        limit: Math.min(200, Number(req.query?.limit || 50) || 50),
      }),
      worker: outbox.workerStatus(),
    });
  } catch (err) { next(err); }
});

router.get('/stats', async (req, res, next) => {
  try {
    res.json({
      ...(await outbox.stats({ hours: Math.min(720, Number(req.query?.hours || 24) || 24) })),
      worker: outbox.workerStatus(),
      alerts: outbox.recentAlerts,
    });
  } catch (err) { next(err); }
});

/** دوباره فرستادن — همان کد، نه کدِ تازه (کاغذی که دستِ مشتری است عوض نمی‌شود). */
router.post('/:id/resend', async (req, res, next) => {
  try {
    const rec = await codes.requestById(req.params.id);
    if (!rec) return next(notFound('این درخواست پیدا نشد', 'request_not_found'));
    const result = await outbox.processById(rec.request_id);
    await audit.log({
      actorType: 'admin', action: 'login.resend', ip: clientIp(req),
      targetType: 'login_request', targetId: rec.request_id,
      detail: { app: rec.app, email: codes.mask(rec.email), result },
    });
    res.json({ ok: true, result, status: await outbox.statusOf(rec.request_id) });
  } catch (err) { next(err); }
});

/**
 * کد را به مدیر نشان بده — برای وقتی ایمیلِ مشتری واقعاً خراب است و باید
 * تلفنی گفته شود. فقط مدیرِ کل، فقط کدِ زنده، و همیشه در دفترِ رخدادها.
 */
router.post('/:id/reveal', requireSuperAdmin, async (req, res, next) => {
  try {
    const out = await codes.reveal(req.params.id, req.admin?.id || req.admin?.username || 'admin');
    if (!out) return next(notFound('این درخواست پیدا نشد', 'request_not_found'));
    if (out.expired || !out.code) {
      return next(notFound('کدِ زنده‌ای برای این درخواست نیست — کدِ تازه بفرستید', 'code_unavailable'));
    }
    await audit.log({
      actorType: 'admin', action: 'login.code_revealed', ip: clientIp(req),
      targetType: 'login_request', targetId: String(req.params.id),
      detail: { app: out.request.app, email: out.request.masked_email },
    });
    res.json({ ok: true, code: out.code, expires_in: out.expires_in, request: out.request });
  } catch (err) { next(err); }
});

router.post('/unlock', async (req, res, next) => {
  try {
    const app = codes.appOf(req.body?.app);
    const email = codes.normEmail(req.body?.email || '');
    if (!app || !email) return next(badRequest('برنامه و ایمیل لازم است', 'app_and_email_required'));
    await codes.unlock(app, email);
    await audit.log({
      actorType: 'admin', action: 'login.unlock', ip: clientIp(req),
      detail: { app, email: codes.mask(email) },
    });
    res.json({ ok: true });
  } catch (err) { next(err); }
});

module.exports = router;
