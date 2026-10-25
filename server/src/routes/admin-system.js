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
 *
 * زیرِ `requireAdmin`ِ `routes/admin.js` سوار است. فقط عدد و وضعیت؛ هیچ
 * راز، نشانی یا دادهٔ مشتری (شرح در `lib/system-health.js`).
 */
const express = require('express');
const health = require('../lib/system-health');

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

module.exports = router;
