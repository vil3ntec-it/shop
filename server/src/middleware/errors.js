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

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, _next) {
  const status = err.status || 500;
  const exposed = err.expose === true || status < 500;
  const ref = status >= 500 ? errorRef() : '';
  if (status >= 500) console.error(`[error] ${ref}`, req.method, req.path, err);
  res.status(status).json({
    error: {
      code: err.code || (status >= 500 ? 'internal' : 'error'),
      message: exposed
        ? err.message
        //  همان جمله، به‌علاوهٔ تنها چیزی که پیدا کردنِ ریشه را ممکن می‌کند
        : `خطای داخلی سرور (کدِ پیگیری: ${ref})`,
      ...(ref ? { ref } : {}),
    },
  });
}

module.exports = { ApiError, errorRef, badRequest, unauthorized, forbidden, notFound, conflict, tooMany, upstream, notFoundHandler, errorHandler };
