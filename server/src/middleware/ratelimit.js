'use strict';
/**
 * محدودیت نرخ درخواست — پنجره‌ی لغزان در حافظه.
 * برای یک سرور خانگی تک‌نمونه‌ای کافی است و وابستگی بیرونی نمی‌خواهد.
 */
const config = require('../config');
const { tooMany } = require('./errors');

const buckets = new Map();

function clientIp(req) {
  if (config.trustProxy) {
    const fwd = req.headers['x-forwarded-for'];
    if (typeof fwd === 'string' && fwd.length) return fwd.split(',')[0].trim();
  }
  return req.socket?.remoteAddress || 'unknown';
}

function rateLimit({ max, windowMs = config.rateLimit.windowMs, keyPrefix = '' }) {
  return function (req, res, next) {
    const key = `${keyPrefix}:${clientIp(req)}`;
    const t = Date.now();
    let hits = buckets.get(key);
    if (!hits) { hits = []; buckets.set(key, hits); }
    // حذف درخواست‌های خارج از پنجره
    while (hits.length && hits[0] <= t - windowMs) hits.shift();
    if (hits.length >= max) {
      res.set('Retry-After', String(Math.ceil((hits[0] + windowMs - t) / 1000)));
      return next(tooMany());
    }
    hits.push(t);
    next();
  };
}

// پاک‌سازی دوره‌ای تا حافظه رشد نکند
const sweeper = setInterval(() => {
  const cutoff = Date.now() - config.rateLimit.windowMs;
  for (const [k, hits] of buckets) {
    while (hits.length && hits[0] <= cutoff) hits.shift();
    if (!hits.length) buckets.delete(k);
  }
}, 60_000);
if (sweeper.unref) sweeper.unref();

function resetAll() { buckets.clear(); }

module.exports = { rateLimit, clientIp, resetAll };
