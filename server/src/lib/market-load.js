'use strict';
/**
 * جداسازیِ بارِ بازار از دکان (S7/S8 ِ docs/MARKETPLACE-CONTRACT-fa.md §۳؛ §۴۰.۴).
 *
 * ⛔ پیش‌فرض خاموش: همه پشتِ پرچمِ `market.load` (فقط scope ِ `all`)؛ خاموش ⇒ رفتارِ پیشین
 * بی هیچ تفاوتی. روشن ⇒
 *   ۱) حضورِ ویترین و برشِ بازار «آخرین تماس» را از `storefronts.last_seen_at` می‌خوانند (S7)،
 *      نه پرسشِ `MAX(sync_devices.last_seen_at)` برای هر دکان؛
 *   ۲) `/api/store/*` و `/api/market/*` (بی‌توکن) سطلِ نرخِ خودشان (`rateLimit.publicMax`) را
 *      دارند و سطلِ `general` ِ همان IP را — که `/api/shop/*` می‌خورد — مصرف نمی‌کنند؛
 *   ۳) همان مسیرها سقفِ هم‌زمانیِ خودشان را دارند (`market.publicConcurrency`، صفِ کوتاه، بعد ۵۰۳)،
 *      تا شلوغیِ بازار همهٔ اتصال‌های پایگاه را نگیرد؛ همگام‌سازی و فروش در این سقف نیستند؛
 *   ۴) نمای کالا و پروفایلِ ویترین `Cache-Control: no-cache` می‌گیرند (به‌جای `no-store`) تا
 *      مرورگر با `ETag` ِ پاسخ بازپرسی کند و ۳۰۴ بگیرد.
 *
 * ⛔ فشار نیست: مقدارِ پرچم با نسخهٔ حافظه‌ایِ `live-config` نگه داشته می‌شود؛ تا نسخهٔ `all`
 * عوض نشده، هیچ پرسشی به پایگاه نمی‌رود.
 */
const config = require('../config');
const { one } = require('../db');
const { ApiError } = require('../middleware/errors');

const FLAG_KEY = 'market.load';
const PUBLIC_RE = /^\/api(?:\/v1)?\/(?:store|market)(?:\/|$)/;

let cached = { version: null, on: false };

async function enabled() {
  const version = await require('./live-config').versionOf();
  if (cached.version === version) return cached.on;
  const r = await one("SELECT value FROM live_config WHERE key = $1 AND scope = 'all'", [FLAG_KEY]);
  let on = false;
  try { on = r ? JSON.parse(r.value) === true : false; } catch { on = false; }
  cached = { version, on };
  return on;
}

function _forget() { cached = { version: null, on: false }; }

const isPublic = (path) => PUBLIC_RE.test(path);

/**
 * سقفِ هم‌زمانی با صفِ کوتاه: `max` درخواست هم‌زمان، تا `queue` منتظر (هر کدام حداکثر `waitMs`)،
 * بیشتر ⇒ `false` (رد). `release` دقیقاً یک بار برای هر `acquire` ِ موفق.
 */
function createGate({ max, queue, waitMs }) {
  let active = 0;
  const waiting = [];
  return {
    get active() { return active; },
    get waiting() { return waiting.length; },
    acquire() {
      if (active < max) { active++; return Promise.resolve(true); }
      if (waiting.length >= queue) return Promise.resolve(false);
      return new Promise((resolve) => {
        const w = { resolve, timer: null };
        w.timer = setTimeout(() => {
          const i = waiting.indexOf(w);
          if (i >= 0) waiting.splice(i, 1);
          resolve(false);
        }, waitMs);
        if (w.timer.unref) w.timer.unref();
        waiting.push(w);
      });
    },
    release() {
      const w = waiting.shift();
      if (w) { clearTimeout(w.timer); w.resolve(true); return; }   //  جا به منتظرِ بعدی می‌رسد
      if (active > 0) active--;
    },
  };
}

const gate = createGate({
  max: Math.max(1, Number(config.market.publicConcurrency) || 1),
  queue: Math.max(0, Number(config.market.publicQueue) || 0),
  waitMs: Math.max(0, Number(config.market.publicWaitMs) || 0),
});

/** میان‌افزارِ سقفِ هم‌زمانیِ مسیرهای عمومی (فقط با پرچمِ روشن صدا زده می‌شود). */
function concurrency(req, res, next) {
  gate.acquire().then((ok) => {
    if (!ok) {
      res.set('Retry-After', '1');
      return next(new ApiError(503, 'market_busy', 'بازار الان شلوغ است؛ چند لحظهٔ دیگر دوباره امتحان کنید'));
    }
    let done = false;
    const free = () => { if (!done) { done = true; gate.release(); } };
    res.once('finish', free);
    res.once('close', free);
    next();
  }, next);
}

module.exports = { FLAG_KEY, enabled, isPublic, createGate, gate, concurrency, _forget };
