'use strict';
/**
 * پنلِ مرکزیِ مالک — ماژول‌های عملیات (۴.۸ تا ۴.۱۱ و ۴.۱۳)، زیرِ `requireAdmin` ِ `routes/admin.js`.
 *
 *   GET  /api/admin/ops/releases                  نسخه‌ها و انتشار: پایدار/آزمایشیِ هر سکو، حداقل نسخه، نسخهٔ
 *                                                 API و طرحِ پایگاه، وضعِ به‌روزرسانیِ دستگاه‌ها، خطای پس از
 *                                                 انتشار، انتشارِ مرحله‌ای (OPS-01) اگر هست
 *   GET  /api/admin/ops/health                    سلامتِ سیستم: کارهای پس‌زمینه، همگام‌سازی، ۵xx، حلقهٔ رویداد
 *   GET  /api/admin/ops/integrations              سرویس‌های بیرونی، آخرین موفقیت/خطا، اعتبارنامه (فقط هست/نیست)،
 *                                                 کلیدِ سرویس، وب‌هوک، قرارداد، سقف و سیاستِ تلاشِ دوباره
 *   GET  /api/admin/ops/incidents                 مرکزِ رخداد (جست‌وجو، پالایش، مرتب‌سازی، صفحه‌بندی)
 *   GET  /api/admin/ops/incidents/:id             یک رخداد: وقوع‌ها، نسخه‌ها، پراکندگی، تاریخچه، پشتیبانی
 *   POST /api/admin/ops/incidents/:id/status      {status, note?, expectedVersion?}
 *   POST /api/admin/ops/incidents/:id/priority    {priority, reason?, expectedVersion?}
 *   POST /api/admin/ops/incidents/:id/notes       {note} (+ Idempotency-Key)
 *   POST /api/admin/ops/incidents/:id/link        {supportThreadId}
 *   GET  /api/admin/ops/alerts                    هشدارها + تعریف و حالِ هر قاعده
 *   POST /api/admin/ops/alerts/evaluate           «همین حالا بسنج» (حداکثر هر ۱۵ ثانیه)
 *   POST /api/admin/ops/alerts/:id/ack            {note?}
 *   POST /api/admin/ops/alerts/:id/resolve        {reason}
 *
 * ⛔ مرزِ نقش همان `lib/admin-roles.js`: خواندن برای هر مدیر (viewer هم)؛ نوشتن برای admin/superadmin؛
 *    finance به این درها نمی‌رسد (پیش‌فرض‌بسته). هر نوشتن یک ردیفِ سابقه در همان تراکنش دارد.
 * ⛔ هیچ راز در هیچ پاسخی (`lib/ops-overview.js`، `lib/ops-errors.js`).
 */
const express = require('express');
const v = require('../lib/validate');
const overview = require('../lib/ops-overview');
const incidents = require('../lib/ops-errors');
const alerts = require('../lib/ops-alerts');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

const noStore = (res) => res.set('Cache-Control', 'no-store');
const idOf = (raw) => v.id(raw, { field: 'شناسه' });

router.get('/releases', async (req, res) => {
  noStore(res);
  res.json(await overview.releases());
});

router.get('/health', async (req, res) => {
  noStore(res);
  res.json(await overview.health());
});

router.get('/integrations', async (req, res) => {
  noStore(res);
  res.json(await overview.integrations());
});

/* ───── رخدادها ───── */

router.get('/incidents', async (req, res) => {
  noStore(res);
  const q = req.query || {};
  //  ردیف‌های تازه پیش از فهرست (حداکثر هر ۱۵ ثانیه، تک‌پرواز)؛ شکستِ آن فهرست را نمی‌خواباند
  const ing = await incidents.ingest().catch((err) => ({ error: String(err.code || err.name || 'error') }));
  const out = await incidents.list({
    q: v.text(q.q, { max: 80 }).trim(), status: String(q.status || ''), source: String(q.source || ''),
    app: v.text(q.app, { max: 40 }).trim(), priority: String(q.priority || ''), shop: v.text(q.shop, { max: 80 }).trim(),
    version: v.text(q.version, { max: 32 }).trim(), sort: String(q.sort || ''), limit: q.limit, offset: q.offset,
  });
  res.json({ ...out, ingest: ing, checkedAt: Date.now() });
});

router.get('/incidents/:id', async (req, res) => {
  noStore(res);
  res.json(await incidents.get(idOf(req.params.id)));
});

router.post('/incidents/:id/status', async (req, res) => {
  const b = req.body || {};
  res.json(await incidents.setStatus(idOf(req.params.id), req, {
    status: String(b.status || ''), note: v.text(b.note, { max: 1000 }), expectedVersion: b.expectedVersion ?? null, ip: clientIp(req),
  }));
});

router.post('/incidents/:id/priority', async (req, res) => {
  const b = req.body || {};
  res.json(await incidents.setPriority(idOf(req.params.id), req, {
    priority: String(b.priority || ''), reason: v.text(b.reason, { max: 200 }), expectedVersion: b.expectedVersion ?? null, ip: clientIp(req),
  }));
});

router.post('/incidents/:id/notes', async (req, res) => {
  const out = await incidents.addNote(idOf(req.params.id), req, { note: v.text(req.body?.note, { max: 1000 }), ip: clientIp(req) });
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/incidents/:id/link', async (req, res) => {
  res.json(await incidents.linkSupport(idOf(req.params.id), req, {
    threadId: v.text(req.body?.supportThreadId, { max: 80 }), ip: clientIp(req),
  }));
});

/* ───── هشدارها ───── */

router.get('/alerts', async (req, res) => {
  noStore(res);
  const q = req.query || {};
  //  ارزیابیِ خودکار حداکثر هر دقیقه؛ شکستش فهرست را نمی‌خواباند و در `evaluation` گفته می‌شود
  const evaluation = await alerts.evaluate().catch((err) => ({ error: String(err.code || err.name || 'error') }));
  const out = await alerts.list({
    status: String(q.status || ''), severity: String(q.severity || ''), rule: String(q.rule || ''),
    since: q.since, limit: q.limit, offset: q.offset,
  });
  res.json({ ...out, evaluation, definitions: alerts.definitions(), checkedAt: Date.now() });
});

router.post('/alerts/evaluate', async (req, res) => {
  res.json(await alerts.evaluateNow());
});

router.post('/alerts/:id/ack', async (req, res) => {
  res.json(await alerts.acknowledge(idOf(req.params.id), req, { note: v.text(req.body?.note, { max: 300 }), ip: clientIp(req) }));
});

router.post('/alerts/:id/resolve', async (req, res) => {
  res.json(await alerts.resolve(idOf(req.params.id), req, { reason: v.text(req.body?.reason, { max: 200 }), ip: clientIp(req) }));
});

module.exports = router;
