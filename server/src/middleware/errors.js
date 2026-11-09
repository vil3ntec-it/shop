'use strict';
/** خطاهای قابل نمایش به کاربر — پیام‌های داخلی هرگز به بیرون درز نمی‌کنند. */
class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status; this.code = code; this.expose = true;
  }
}
const badRequest   = (m, c = 'bad_request')  => new ApiError(400, c, m);
const unauthorized = (m = 'احراز هویت لازم است', c = 'unauthorized') => new ApiError(401, c, m);
const forbidden    = (m = 'دسترسی مجاز نیست', c = 'forbidden') => new ApiError(403, c, m);
const notFound     = (m = 'پیدا نشد', c = 'not_found') => new ApiError(404, c, m);
const conflict     = (m, c = 'conflict') => new ApiError(409, c, m);
const tooMany      = (m = 'تعداد درخواست بیش از حد مجاز است', c = 'rate_limited') => new ApiError(429, c, m);
//  سرویسِ بیرونی (پیامک، ایمیل) نشد — نه اشکالِ کاربر است و نه خرابیِ
//  ما. پیامش نمایش داده می‌شود تا کاربر بداند دوباره تلاش کند و مدیر
//  بداند کجا را درست کند.
const upstream     = (m, c = 'delivery_failed') => new ApiError(502, c, m);

function notFoundHandler(req, res) {
  res.status(404).json({ error: { code: 'not_found', message: 'این مسیر وجود ندارد' } });
}

/**
 * شناسهٔ کوتاهِ همین یک خرابی.
 *
 * ⛔ **«خطای داخلی سرور» بی نشانی، هیچ کاری دستِ کسی نمی‌داد.** پیامِ
 * خامِ استثنا عمداً به بیرون نمی‌رود (ممکن است نشانی، نامِ میزبان یا
 * تکه‌ای از پرس‌وجو داشته باشد) — ولی وقتی صاحبِ سامانه عکسِ «خطای
 * داخلی سرور» را می‌فرستد، هیچ راهی نبود که آن را به یک سطرِ لاگ وصل
 * کنیم. این شناسه همان پل است: در پاسخ می‌آید و کنارِ همان استثنا هم
 * در لاگ چاپ می‌شود.
 *
 * ⚠️ و هیچ چیزی از **درونِ** خطا در خودش نیست — تصادفی است. پس نه
 * نشانی درز می‌کند و نه چیزی دربارهٔ کاربر.
 */
function errorRef() {
  return `e${Math.random().toString(36).slice(2, 8)}${Date.now().toString(36).slice(-4)}`;
}

/*
 *  PERF-04 (§۱۵.۹ بندِ ۴): استخرِ PostgreSQL تا `PG_CONNECT_TIMEOUT_MS` اتصالی نداد (pg-pool همین
 *  پیامِ خام را می‌دهد) ⇒ «شلوغ است، کمی بعد» (۵۰۳ + Retry-After)، نه ۵۰۰ ِ «خطای داخلی». برنامه
 *  ۵۰۳ را با تأخیر دوباره می‌فرستد و کاربر می‌داند خرابی نیست. ثبتِ ۵xx (`recordServer`) سقفِ
 *  دقیقه‌ای دارد، پس طوفانِ همین خطا استخرِ پر را پرتر نمی‌کند. سنجه: `test/db-pool-busy.test.js`.
 *  `busy()` همان خطا برای پس‌زدنِ زود، پیش از هر انتظار (`db.js`، `lib/sync-v3-gate.js`).
 */
const POOL_BUSY = 'timeout exceeded when trying to connect';

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, _next) {
  if (err && !err.status && err.message === POOL_BUSY) {
    err = Object.assign(new ApiError(503, 'db_busy', 'سرور الان شلوغ است؛ چند ثانیهٔ دیگر دوباره بکوشید'), { cause: err });
    res.set('Retry-After', '2');
  } else if (err && !err.status && (err.code === '57014' || err.code === '25P03')) {
    //  PERF-04: سقفِ پرس‌وجو/تراکنشِ بیکار (`PG_STATEMENT_TIMEOUT_MS`، `PG_IDLE_TX_TIMEOUT_MS`) بُرید ⇒ «دیر شد، دوباره»
    err = Object.assign(new ApiError(503, 'db_timeout', 'پاسخ از پایگاه دیر شد؛ چند ثانیهٔ دیگر دوباره بکوشید'), { cause: err });
    res.set('Retry-After', '2');
  } else if (err && err.status === 503 && err.code === 'db_busy') {
    //  پس‌زدنِ زودِ خودِ سرور (صفِ استخر `PG_POOL_MAX_WAITING`، دروازهٔ push ِ v3) — همان پاسخ
    res.set('Retry-After', String(err.retryAfter || 2));
  }
  const status = err.status || 500;
  const exposed = err.expose === true || status < 500;
  const ref = status >= 500 ? errorRef() : '';
  if (status >= 500) {
    const where = require('../lib/log-redact').redactPath(req.originalUrl || req.path);
    //  استخرِ پر زیرِ بار صدها بار در ثانیه می‌رسد: یک خط، بی stack (لاگ خودش بارِ تازه نشود)
    if (err.code === 'db_busy' || err.code === 'db_timeout') console.error(`[error] ${ref} ${err.code}`, req.method, where);
    else console.error(`[error] ${ref}${err.step ? ` @${err.step}` : ''}`, req.method, where, err);
    //  پنلِ مرکزی ۴.۱۰: همان خطا با همان «کدِ پیگیری» در `server_errors` (پوشانده از راز، بی await؛
    //  شکستِ ثبت هرگز به پاسخ نمی‌رسد) ⇒ مرکزِ رخدادِ پنل آن را می‌بیند، نه فقط لاگِ کنسول
    require('../lib/ops-errors').recordServer({ ref, err, req, status });
  }
  res.status(status).json({
    error: {
      //  جزئیاتِ قابلِ نمایش (مثلاً «فقط ۱ عدد قابلِ سفارش است»: `lines`) — فقط برای
      //  خطای نمایش‌دادنی؛ کد و پیام همیشه بر آن جلو می‌افتند
      ...(exposed && status < 500 && err.data && typeof err.data === 'object' && !Array.isArray(err.data) ? err.data : {}),
      code: err.code || (status >= 500 ? 'internal' : 'error'),
      message: exposed
        ? err.message
        //  همان جمله، به‌علاوهٔ تنها چیزی که پیدا کردنِ ریشه را ممکن می‌کند
        : `خطای داخلی سرور (کدِ پیگیری: ${ref})`,
      ...(ref ? { ref } : {}),
    },
  });
}

/** ۵۰۳ ِ `db_busy` ِ پس‌زدنِ زود: «شلوغ است، [retryAfter] ثانیهٔ دیگر» */
function busy(retryAfter = 2) {
  return Object.assign(new ApiError(503, 'db_busy', 'سرور الان شلوغ است؛ چند ثانیهٔ دیگر دوباره بکوشید'), { retryAfter });
}

module.exports = { ApiError, busy, errorRef, badRequest, unauthorized, forbidden, notFound, conflict, tooMany, upstream, notFoundHandler, errorHandler };
