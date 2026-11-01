'use strict';
/**
 * پنلِ مرکزی ۴.۷ — نظارت بر پیک و تحویل (`/api/admin/delivery/*`). جای میزِ تحویلِ دکان نیست.
 *
 *   GET /overview      ?days=   صف‌ها (آماده‌سازی، منتظرِ ارسال، سپرده، در راه، ناموفق)، گیرکرده/ناهم‌خوان/اختلاف، ۲۴ ساعت
 *   GET /queue         ?view=all|preparing|dispatch|assigned|in_delivery|failed|stuck|mismatch|discrepancy&shopId=&days=&limit=&offset=
 *   GET /events        ?shopId=&kind=&before=&limit=   رویدادهای کلیدی، بی یادداشتِ آزاد و بی کد
 *   GET /connectivity  آخرین رویدادِ پیکِ هر دکان، دستگاه‌ها و اختلافِ ساعت
 *   GET /report        ?days=|from=&to=   کارکرد و نقدِ هر دکان و هر پیک (یک ردیفِ سابقه)
 *   GET /thresholds    آستانه‌های «گیر کرده» و منبعِ هر کدام
 *   PUT /thresholds    {thresholds:{…}, reason}  فقط ارشد، دلیل اجباری؛ تکرارِ همان مقدار ⇒ unchanged (بی ردیفِ دوم)
 */
const express = require('express');
const d = require('../lib/admin-delivery');
const { thresholds } = require('../lib/admin-market');
const watch = require('../lib/order-watch');
const liveConfig = require('../lib/live-config');
const audit = require('../lib/audit');
const svc = require('../lib/admin-service');
const { requireSuperAdmin } = require('../middleware/auth');
const { requireReason } = require('../lib/admin-access');
const { clientIp } = require('../middleware/ratelimit');
const { badRequest } = require('../middleware/errors');

const router = express.Router();

router.use((req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });

router.get('/overview', async (req, res) => res.json(await d.overview(req.query || {})));
router.get('/queue', async (req, res) => res.json(await d.queue(req.query || {})));
router.get('/events', async (req, res) => res.json(await d.events(req.query || {})));
router.get('/connectivity', async (req, res) => res.json(await d.connectivity()));

/** ⛔ پولِ همهٔ دکان‌ها ⇒ دیدنش یک ردیفِ سابقه با بازه (همان الگوی `system/reconciliation` ِ #411). */
router.get('/report', async (req, res) => {
  const out = await d.report(req.query || {});
  await audit.log({
    actorType: 'admin', userId: req.admin.id, ip: clientIp(req), action: 'admin.delivery_report_viewed',
    detail: { from: out.window.from, to: out.window.to, rows: out.rows.length },
  });
  res.json(out);
});

router.get('/thresholds', async (req, res) => {
  res.json({ ...(await thresholds()), defaults: watch.DEFAULT_THRESHOLDS, min: watch.MIN_MINUTES, max: watch.MAX_MINUTES });
});

router.put('/thresholds', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const ok = watch.validateThresholds(req.body?.thresholds);
  if (!ok.ok) throw badRequest(`آستانه درست نیست (${ok.error}${ok.key ? `: ${ok.key}` : ''})`, 'bad_thresholds');
  const cur = await thresholds();
  //  فقط کلیدهایی که از live_config آمده‌اند + کلیدهای تازه ⇒ پیش‌فرض‌ها به داده نمی‌چسبند
  const stored = Object.fromEntries(Object.entries(cur.values).filter(([k]) => cur.source[k] === 'live_config'));
  const next = { ...stored, ...ok.value };
  const same = Object.keys(next).length === Object.keys(stored).length
    && Object.entries(next).every(([k, v]) => stored[k] === v);
  if (same) return res.json({ ok: true, unchanged: true, ...(await thresholds()) });
  const out = await liveConfig.set('all', watch.KEY, next, svc.label(req), { reason, actor: svc.actorRecord(req) });
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.flag_set', ip: clientIp(req),
    targetType: 'live_config', targetId: `${out.scope}:${out.key}`, detail: { scope: out.scope, key: out.key, reason, value: next },
  });
  res.json({ ok: true, unchanged: false, version: out.version, ...(await thresholds()) });
});

module.exports = router;
