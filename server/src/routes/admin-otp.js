'use strict';
/**
 * میزِ «کدهای شش‌رقمی» برای دفترِ دومِ کدها — `otp_codes`.
 *
 *   GET  /api/admin/otp?destination=&app=&purpose=&limit=
 *   POST /api/admin/otp/:id/reveal      کد را به مدیر نشان بده
 *
 * ── چرا لازم شد ────────────────────────────────────────────────────────
 * گزارشِ صاحب سامانه با عکس: کد به ایمیلش رسید، ولی در «کدهای شش‌رقمی»ِ
 * پنل «هنوز کسی کد نخواسته» بود و هیچ‌جا نمی‌گفت آن کد **برای کدام حساب،
 * کدام برنامه و کدام ایمیل** ساخته شده.
 *
 * ⛔ **ریشه: دو دفترِ کد بود و پنل یکی‌شان را می‌دید.** `login_requests`
 * مالِ «ورود با کدِ ایمیلی» است و از ۱.۴۵.۳ در پنل دیده می‌شود
 * (`admin-logins.js`). ولی کدِ **ثبت‌نام** و **رمزِ فراموش‌شده** — یعنی
 * همان کدی که کاربرِ تازه می‌گیرد — در `otp_codes` می‌نشیند و آن جدول
 * **هیچ مسیرِ مدیریتی نداشت**. همان درسِ همیشگی: دو دفتر یعنی دو حقیقت.
 *
 * ── دو قاعده، مو‌به‌مو مثلِ `admin-logins.js` ─────────────────────────
 *  ⛔ **کد هیچ‌وقت در فهرست نمی‌آید** — فقط در پاسخِ همان مسیرِ جدا.
 *  ⛔ **نمایشِ کد فقط مدیرِ کل و همیشه با ثبت در دفترِ رخدادها**، و فقط
 *     برای کدی که هنوز زنده است.
 */
const express = require('express');
const otp = require('../lib/otp');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { notFound } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

router.get('/', async (req, res, next) => {
  try {
    res.json({
      requests: await otp.listRequests({
        destination: String(req.query?.destination || req.query?.email || ''),
        app: String(req.query?.app || ''),
        purpose: String(req.query?.purpose || ''),
        limit: Math.min(200, Number(req.query?.limit || 50) || 50),
      }),
    });
  } catch (err) { next(err); }
});

router.post('/:id/reveal', requireSuperAdmin, async (req, res, next) => {
  try {
    const out = await otp.reveal(req.params.id);
    if (!out) return next(notFound('این درخواست پیدا نشد', 'request_not_found'));
    if (out.expired || !out.code) {
      return next(notFound('کدِ زنده‌ای برای این درخواست نیست — کدِ تازه بفرستید', 'code_unavailable'));
    }
    await audit.log({
      actorType: 'admin', action: 'otp.code_revealed', ip: clientIp(req),
      targetType: 'otp_code', targetId: String(req.params.id),
      //  ⚠️ نشانیِ کامل در دفترِ رخدادها نمی‌نشیند — همان قاعدهٔ `logins`
      detail: { app: out.request?.app || '', destination: out.request?.masked_destination || '', purpose: out.request?.purpose || '' },
    });
    res.json({ ok: true, code: out.code, expires_in: out.expires_in, request: out.request });
  } catch (err) { next(err); }
});

module.exports = router;
