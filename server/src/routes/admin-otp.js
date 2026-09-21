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
const { one } = require('../db');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { badRequest, notFound } = require('../middleware/errors');
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

/**
 * فرستادنِ دوبارهٔ همان کد — «ارسالِ خودکار نشد، خودم می‌فرستم».
 *
 * خواستهٔ صاحب سامانه: «اگر در مرورِ زمان مشکل در ارسالِ خودکار پیش آمد،
 * خودم درجا و سریع بفرستم به طرف.»
 *
 * ⛔ **همان کد، نه کدِ تازه** (`otp.resend`) — وگرنه کدی که همین حالا
 * دستِ مشتری است باطل می‌شد و مدیر برای کمک کردن کارش را خراب می‌کرد.
 *
 * ⚠️ این‌جا `requireSuperAdmin` **نیست** و عمداً: فرستادنِ دوبارهٔ کد به
 * **همان** نشانیِ همیشگی هیچ رازی را جابه‌جا نمی‌کند، برخلافِ «نمایشِ
 * کد» که کد را به چشمِ مدیر می‌آورد. همان مرزی که `admin-logins` دارد.
 */
router.post('/:id/resend', async (req, res, next) => {
  try {
    const out = await otp.resend(req.params.id);
    if (!out) return next(notFound('این درخواست پیدا نشد', 'request_not_found'));

    await audit.log({
      actorType: 'admin', action: 'otp.resend', ip: clientIp(req),
      targetType: 'otp_code', targetId: String(req.params.id),
      detail: { ok: out.ok, reason: out.ok ? out.via : out.error },
    });

    //  ⛔ «نرفت» با ۲۰۰ برنگردد: صفحه باید سرخ نشان بدهد، نه سبزِ دروغ
    if (!out.ok) return res.status(409).json({ error: { code: out.error, message: out.message } });
    res.json({ ok: true, via: out.via, request: await otp.requestById(req.params.id) });
  } catch (err) { next(err); }
});

/**
 * فرستادنِ کدِ **بازیابیِ رمز** به یک حساب — بندِ ۶.۱ سندِ ریمیک.
 *
 * خواستهٔ صاحب سامانه: «تغییرِ رمز برای کاربرهای برنامه راحت و آسان شود:
 * کدِ شش‌رقمی به طرف داده شود، بعد از زدنش در برنامه بتواند رمزِ تازه
 * بگذارد و همان بشود رمزِ جدید و روی سرور ذخیره شود.»
 *
 * ⛔ **هیچ منطقِ تازه‌ای ساخته نشد.** همان `otp.request` با
 * `purpose: 'reset'` است — دقیقاً همان کدی که `POST /api/auth/password/forgot`
 * می‌سازد. پس کد در همان `otp_codes` می‌نشیند، در میزِ مدیر دیده می‌شود،
 * و با همان `/reveal` و `/resend` اداره می‌شود. یک راه، نه دو.
 *
 * ⛔ **و رمزِ تازه از این‌جا گذاشته نمی‌شود.** کاربر خودش در برنامه
 * `password/reset` را می‌زند. مدیری که بتواند رمزِ کسی را عوض کند،
 * می‌تواند جای او وارد شود — و آن یک درِ پشتی است، نه یک قابلیت.
 *
 * ⚠️ **و این‌جا برخلافِ مسیرِ عمومی، «حساب نیست» گفته می‌شود.** آن‌جا
 * ۲۰۰ِ همیشگی برای این است که کسی نتواند با امتحانِ نشانی‌ها بفهمد چه
 * کسانی حساب دارند؛ مدیر از قبل فهرستِ حساب‌ها را دارد، پس پنهان کردنش
 * از او فقط او را گیج می‌کند.
 */
router.post('/password-reset', async (req, res, next) => {
  try {
    const email = otp.isEmail(req.body?.email) ? String(req.body.email).trim().toLowerCase() : '';
    if (!email) return next(badRequest('ایمیل لازم است', 'email_required'));

    const user = await one('SELECT id, email FROM users WHERE lower(email)=$1', [email]);
    if (!user) return next(notFound('حسابی با این ایمیل نیست', 'user_not_found'));

    let out;
    try {
      out = await otp.request(user.email, {
        purpose: 'reset',
        ip: clientIp(req),
        app: String(req.body?.app || ''),
      });
    } catch (err) {
      /*
       *  ⚠️ سقفِ نرخ و «ارسال نشد» هر دو خطاهای **بادار** خودِ `otp` هستند
       *  (۴۲۹ و ۵۰۲) و همان‌طور که هستند بالا می‌روند — پیامشان برای مدیر
       *  همان‌قدر لازم است که برای کاربر.
       */
      return next(err);
    }

    await audit.log({
      actorType: 'admin', action: 'otp.password_reset_sent', ip: clientIp(req),
      targetType: 'user', targetId: user.id,
      detail: { destination: otp.mask(user.email) },
    });

    res.status(201).json({
      ok: true,
      email: user.email,
      expiresAt: out.expiresAt,
      resendSeconds: out.resendSeconds,
      //  ⛔ خودِ کد این‌جا برنمی‌گردد — میزِ کدها و `/reveal` کارِ خودشان را دارند
    });
  } catch (err) { next(err); }
});

module.exports = router;
