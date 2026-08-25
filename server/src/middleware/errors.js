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

function notFoundHandler(req, res) {
  res.status(404).json({ error: { code: 'not_found', message: 'این مسیر وجود ندارد' } });
}

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, _next) {
  const status = err.status || 500;
  const exposed = err.expose === true || status < 500;
  if (status >= 500) console.error('[error]', req.method, req.path, err);
  res.status(status).json({
    error: {
      code: err.code || (status >= 500 ? 'internal' : 'error'),
      message: exposed ? err.message : 'خطای داخلی سرور',
    },
  });
}

module.exports = { ApiError, badRequest, unauthorized, forbidden, notFound, conflict, tooMany, notFoundHandler, errorHandler };
