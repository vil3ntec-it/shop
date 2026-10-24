'use strict';
/**
 * سلامتِ سرور در پنلِ مدیر — v3 §۳۸.۸، OPS-01@38.
 *
 *   GET /api/admin/system/health    ⇐ API، دیتابیس، صفِ ایمیل و تلگرام،
 *                                      خطا و تعارضِ ۲۴ ساعت، دیسک، پشتیبان
 *   GET /api/admin/system/providers ⇐ پیامک، ایمیل، پوش، پرداخت و عکس: زنده
 *                                      یا «آزمایشی» (OPS-04@29)
 *   GET /api/admin/system/versions  ⇐ نسخهٔ دستگاه‌های فعال و هشدارِ کهنه یا
 *                                      زیرِ حداقل (OPS-04)
 *   POST /api/admin/system/chain-verify ⇐ بررسیِ زنجیرهٔ هشِ سابقه (SEC-02)؛
 *                                      فقط مدیرِ ارشد، فقط‌خواندنی، با یک ردیفِ سابقه
 *   GET /api/admin/system/reconciliation ⇐ تطبیقِ فروشِ آنلاین، لغو، بازپرداخت، نقدِ
 *                                      پیک و تسویه به‌ازای هر دکان (E2E-ADM-15)؛ فقط ارشد
 *   GET /api/admin/system/sms-usage  ⇐ مصرف و هزینهٔ پیامکِ هر دکان (OPS-09)؛ فقط ارشد
 *   PUT /api/admin/system/sms-cost   ⇐ بهای هر تکهٔ پیامک (واحدِ خُرد)؛ فقط ارشد، با سابقه
 *
 * زیرِ `requireAdmin`ِ `routes/admin.js` سوار است. فقط عدد و وضعیت؛ هیچ
 * راز، نشانی یا دادهٔ مشتری (شرح در `lib/system-health.js`).
 */
const express = require('express');
const health = require('../lib/system-health');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

router.get('/health', async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json(await health.snapshot());
  } catch (err) { next(err); }
});

router.get('/providers', async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json(await health.providers());
  } catch (err) { next(err); }
});

router.get('/versions', async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json(await health.versions());
  } catch (err) { next(err); }
});

/*
 *  ⛔ فقط مدیرِ ارشد: نامِ زنجیره‌ها شناسهٔ دکان و کاربر را دارد و سنجش هزینه
 *  دارد. سابقه پس از سنجش نوشته می‌شود تا ردیفِ خودش وسطِ سنجش نیاید.
 */
router.post('/chain-verify', requireSuperAdmin, async (req, res, next) => {
  try {
    const b = req.body || {};
    const out = await health.chainReport({ rows: b.rows !== false, chains: b.chain ? [String(b.chain).slice(0, 200)] : [] });
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip: clientIp(req), action: 'admin.chain_verified',
      detail: { ok: out.ok, total: out.total, broken: out.brokenCount, rows: out.rows },
    });
    res.set('Cache-Control', 'no-store');
    res.json(out);
  } catch (err) { next(err); }
});

/** ⛔ پولِ همهٔ دکان‌ها ⇒ فقط مدیرِ ارشد؛ فقط‌خواندنی (همان `order-refunds.report`). */
router.get('/reconciliation', requireSuperAdmin, async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json(await health.reconciliation({ from: req.query?.from, to: req.query?.to }));
  } catch (err) { next(err); }
});

/** ⛔ هزینهٔ پلتفرم ⇒ فقط مدیرِ ارشد (`lib/sms-usage.js`، دفترِ ۱۲۰). */
router.get('/sms-usage', requireSuperAdmin, async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json(await require('../lib/sms-usage').report({ days: req.query?.days }));
  } catch (err) { next(err); }
});

/** نوشتن و سابقه (پیش و پس) در خودِ `setUnitCost`؛ نادرست ⇒ ۴۰۰ bad_sms_cost و بی‌تغییر. */
router.put('/sms-cost', requireSuperAdmin, async (req, res, next) => {
  try {
    res.json(await require('../lib/sms-usage').setUnitCost(req.body?.unitCostMinor, { adminId: req.admin.id, ip: clientIp(req) }));
  } catch (err) { next(err); }
});

module.exports = router;
