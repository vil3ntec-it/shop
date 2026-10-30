'use strict';
/**
 * محدودیت نرخ درخواست — پنجره‌ی لغزان در حافظه.
 * برای یک سرور خانگی تک‌نمونه‌ای کافی است و وابستگی بیرونی نمی‌خواهد.
 */
const config = require('../config');
const { tooMany } = require('./errors');

const buckets = new Map();
//  پنجرهٔ هر سطل (کلید ⇒ میلی‌ثانیه) تا پاک‌سازی سقفِ روزانه را با ربع ساعت نبُرد
const windows = new Map();

/**
 * IPِ کاربر برای سقفِ نرخ.
 *
 * ⛔ **راست‌ترین** بخشِ `X-Forwarded-For`، نه چپ‌ترین. چپ‌ترین همان
 * چیزی است که خودِ کاربر می‌فرستد؛ هر پروکسیِ مطمئن IPِ **دیده‌شده** را
 * ته فهرست اضافه می‌کند. پس با چپ‌ترین، هر کسی با یک سرآیندِ ساختگی هر
 * بار IPِ تازه‌ای می‌شد و سقفِ ورود و کد را دور می‌زد. درگاهِ پنلِ خانگی
 * کلِ سرآیند را با IPِ واقعی **بازنویسی** می‌کند، پس آن‌جا هر دو یکی‌اند.
 */
function clientIp(req) {
  if (config.trustProxy) {
    const fwd = req.headers['x-forwarded-for'];
    if (typeof fwd === 'string' && fwd.length) {
      const parts = fwd.split(',').map((x) => x.trim()).filter(Boolean);
      if (parts.length) return parts[parts.length - 1];
    }
  }
  return req.socket?.remoteAddress || 'unknown';
}

/**
 * @param {object} opts
 * @param {function} [opts.key] کلید دلخواه (مثلاً شماره‌ی موبایل) به‌جای IP
 * @param {boolean} [opts.refundable] سهمِ این درخواست با `refund(req, keyPrefix)` پس‌دادنی است
 */
function rateLimit({ max, windowMs = config.rateLimit.windowMs, keyPrefix = '', key: keyFn = null, refundable = false }) {
  return function (req, res, next) {
    const key = `${keyPrefix}:${keyFn ? (keyFn(req) || clientIp(req)) : clientIp(req)}`;
    const t = Date.now();
    let hits = buckets.get(key);
    if (!hits) { hits = []; buckets.set(key, hits); }
    windows.set(key, windowMs);
    // حذف درخواست‌های خارج از پنجره
    while (hits.length && hits[0] <= t - windowMs) hits.shift();
    if (hits.length >= max) {
      res.set('Retry-After', String(Math.ceil((hits[0] + windowMs - t) / 1000)));
      return next(tooMany());
    }
    hits.push(t);
    if (refundable) {
      if (!req.rateLimitTaken) req.rateLimitTaken = new Map();
      req.rateLimitTaken.set(keyPrefix, () => {
        const i = hits.lastIndexOf(t);
        if (i >= 0) hits.splice(i, 1);
      });
    }
    next();
  };
}

/**
 * پس دادنِ سهمی که همین درخواست از سطلِ `keyPrefix` برداشت (فقط سطلِ
 * `refundable`، فقط یک بار). برای تکرارِ بی‌اثر: همان پیام با همان شناسه یا
 * همان بایتِ بارگذاری‌شده — تلاشِ دوبارهٔ دستگاه نباید سهمیه بخورد (CHAT-11).
 * درخواستِ ردشده (۴۰۰، ۴۰۹…) پس داده نمی‌شود؛ سقفِ کلیِ IP هم دست نمی‌خورد.
 */
function refund(req, keyPrefix) {
  const undo = req && req.rateLimitTaken && req.rateLimitTaken.get(keyPrefix);
  if (!undo) return false;
  req.rateLimitTaken.delete(keyPrefix);
  undo();
  return true;
}

/**
 * پاک‌سازی دوره‌ای تا حافظه رشد نکند — هر سطل با پنجرهٔ **خودش**.
 * ⛔ تا پیش از این همه با `windowMs` ِ پیش‌فرض (ربع ساعت) پاک می‌شدند، پس
 * سقفِ روزانه پس از ربع ساعت عملاً صفر می‌شد.
 */
function sweep(t = Date.now()) {
  for (const [k, hits] of buckets) {
    const cutoff = t - (windows.get(k) || config.rateLimit.windowMs);
    while (hits.length && hits[0] <= cutoff) hits.shift();
    if (!hits.length) { buckets.delete(k); windows.delete(k); }
  }
}
const sweeper = setInterval(() => sweep(), 60_000);
if (sweeper.unref) sweeper.unref();

function resetAll() { buckets.clear(); windows.clear(); }

module.exports = { rateLimit, refund, clientIp, resetAll, sweep };
