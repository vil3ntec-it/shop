'use strict';
/**
 * PERF-04 — مهلتِ سراسریِ درخواست (`REQUEST_TIMEOUT_MS`؛ پیش‌فرض ۰ = خاموش، بیت‌به‌بیت همان پیشین).
 *
 * روشن ⇒ درخواستِ `/api` که در این مدت پاسخی نگرفته ۵۰۳ ِ `request_timeout` با `Retry-After` می‌گیرد، نه انتظارِ بی‌پایان
 * پشتِ پایگاهِ گیرکرده. کارِ پایگاه را `PG_STATEMENT_TIMEOUT_MS` می‌بُرد (`db.js`)؛ این فقط کلاینت را آزاد می‌کند.
 *
 *   کف ─────── [FLOOR_MS] (۵ ثانیه): عددِ کمتر بی‌صدا همین کف می‌شود.
 *   long-poll ─ هر long-poll ِ امروز `?wait=` دارد (چتِ دکان و ویترین تا ۲۵ ثانیه، وضعیتِ پرداخت تا ۲۵ ثانیه) ⇒ همان درخواست
 *              [LONG_POLL_MS] (۳۰ ثانیه) بیشتر می‌گیرد. long-poll ِ تازه بی `wait` یا بالای ۲۵ ثانیه ⇒ اول همین را عوض کن.
 *   زمان ───── از پس از خواندنِ بدنهٔ JSON (این‌جا پس از `express.json` نشسته است)؛ درخواستی که بدنه‌اش هنوز کامل نرسیده
 *              (`!req.complete`: بارگذاریِ خام/چندبخشیِ کند که خودِ مسیر می‌خواند) ⇒ بیرون: شبکهٔ کندِ مشتری کارِ سرور نیست.
 *   بیرون ──── پمپ و Sync v1 (`/api/pump/*`، `/api/sync/v1/*`: کدِ مشترکِ پمپ بی اجازهٔ صاحب‌کار دست نمی‌خورد) و پاسخی که
 *              سرآیندش رفته (جریان، دانلود).
 *   دیرکرد ─── نوشتنِ دیرِ همان مسیر پس از ۵۰۳ بی‌صدا دور ریخته می‌شود (هرگز `ERR_HTTP_HEADERS_SENT` یا افتادنِ فرایند).
 *              ⚠️ کارِ مسیر ممکن است پس از ۵۰۳ تمام شود ⇒ برنامه فقط مسیرهای تکرارپذیر (کلیدِ یکتایی، opId) را دوباره بفرستد.
 */
const FLOOR_MS = 5_000;
const LONG_POLL_MS = 30_000;
const SKIP = /^\/(?:v1\/)?(?:pump\/|sync\/v1\/)/;

function requestDeadline({ ms = require('../config').http.requestTimeoutMs, floorMs = FLOOR_MS } = {}) {
  if (!(Number(ms) > 0)) return function requestDeadlineOff(req, res, next) { next(); };
  const limit = Math.max(Number(ms), floorMs);
  return function requestDeadline(req, res, next) {
    if (SKIP.test(req.path)) return next();
    //  کلاینت هنوز بدنه را می‌فرستد (express.json بدنهٔ JSON را پیش از این تا ته خوانده است ⇒ `readableEnded`)
    const hasBody = Number(req.headers['content-length']) > 0 || req.headers['transfer-encoding'] !== undefined;
    if (hasBody && !req.readableEnded && !req.complete) return next();
    const hold = req.query && req.query.wait !== undefined && req.query.wait !== '' && req.query.wait !== '0' ? LONG_POLL_MS : 0;
    const timer = setTimeout(() => {
      if (res.headersSent || res.writableEnded) return;
      res.set('Retry-After', '5');
      res.status(503).json({ error: { code: 'request_timeout', message: 'پاسخ دیر شد؛ چند ثانیهٔ دیگر دوباره بکوشید' } });
      res.locals.timedOut = true;
      const drop = function drop() { return this; };
      res.setHeader = drop;
      res.writeHead = drop;
      //  یک خط، بی stack (همان `db_busy`)؛ و در `server_errors` با سقفِ دقیقه‌ای ⇒ پنل و قاعدهٔ ۵xx ِ `critical_health` می‌بینندش
      const ref = require('./errors').errorRef();
      console.error(`[error] ${ref} request_timeout`, req.method, require('../lib/log-redact').redactPath(req.originalUrl || req.path));
      require('../lib/ops-errors').recordServer({ ref, req, status: 503, err: { name: 'Deadline', code: 'request_timeout', message: 'request_timeout' } });
    }, limit + hold);
    if (timer.unref) timer.unref();
    const clear = () => clearTimeout(timer);
    res.once('finish', clear);
    res.once('close', clear);
    next();
  };
}

module.exports = { requestDeadline, FLOOR_MS, LONG_POLL_MS };
