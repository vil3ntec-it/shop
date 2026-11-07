'use strict';
/**
 * محدودیت نرخ درخواست — پنجره‌ی لغزان.
 *
 * دو انبار، یک قرارداد (`RATE_LIMIT_STORE`؛ docs/SCALING-fa.md):
 *   • `memory` (پیش‌فرضِ تک‌نمونه) — همان فهرستِ زمان‌ها در حافظهٔ همین فرآیند، دقیق و بی هیچ پرسش.
 *   • `pg` (پیش‌فرضِ `CLUSTER_ENABLED=1`) — شمارنده‌های مشترکِ همهٔ نمونه‌ها در جدولِ UNLOGGED ِ
 *     `rate_limit_hits` (مهاجرتِ ۲۱۵): هر پنجره ۶۰ برش (برشِ ربع‌ساعت = ۱۵ ثانیه)، یک upsert ِ شرطی برای هر
 *     درخواست. پذیرفتن یا رد در **همان یک دستور** و زیرِ قفلِ ردیفِ برشِ جاری ⇒ درخواست‌های هم‌زمان از چند
 *     نمونه از سقف نمی‌گذرند. ردشده شمرده نمی‌شود (همان رفتارِ حافظه)؛ `refund` همان برش را یکی کم می‌کند.
 *     فرقِ تنها: هر درخواست تا پایانِ برشِ خودش شمرده می‌ماند (حداکثر یک برش دیرتر، سخت‌گیرتر نه
 *     آسان‌تر). کلید با sha256 درهم می‌شود (ایمیل/شماره در پایگاه نمی‌نشیند). خطای پایگاه ⇒ همان
 *     درخواست با انبارِ حافظه سنجیده می‌شود (باز نه، بسته نه: سقفِ همین نمونه) و هر دقیقه یک خط لاگ.
 *
 * ⛔ ترتیبِ میان‌افزارها همان است: سقفِ IP پیش از احراز (`router.use(rateLimit(…))`)، سقفِ حساب پس از
 *    آن — CodeQL ِ `js/missing-rate-limiting` همین `rateLimit` را می‌شناسد (`.github/codeql/extensions`).
 */
const crypto = require('node:crypto');
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
  const peer = req.socket?.remoteAddress || 'unknown';
  if (config.trustProxy && trustedPeer(peer)) {
    const fwd = req.headers['x-forwarded-for'];
    if (typeof fwd === 'string' && fwd.length) {
      const parts = fwd.split(',').map((x) => x.trim()).filter(Boolean);
      if (parts.length) return parts[parts.length - 1];
    }
  }
  return peer;
}

/*
 *  ⛔ SEC-AUTH-05 — `X-Forwarded-For` فقط از **پروکسیِ مطمئن**.
 *
 *  تا ۲.۱۴.۴ با `TRUST_PROXY=true` سرآیند از **هر** همسایه‌ای خوانده می‌شد. سرور
 *  پیش‌فرض روی `0.0.0.0` گوش می‌دهد؛ هر کس که مستقیم (شبکهٔ محلی، پورتِ بازِ
 *  اشتباهی) به آن برسد با یک `X-Forwarded-For`ِ تازه در هر درخواست IPِ تازه‌ای
 *  می‌شد و سقفِ ورود، کد و قفلِ «شناسه + IP» را دور می‌زد. حالا سرآیند فقط وقتی
 *  خوانده می‌شود که خودِ سوکت از loopback یا شبکهٔ خصوصی (یا `TRUST_PROXY_FROM`)
 *  آمده باشد؛ وگرنه IPِ خودِ سوکت.
 */
function v4(addr) {
  const s = String(addr || '').replace(/^::ffff:/i, '');
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(s);
  if (!m) return null;
  const o = m.slice(1).map(Number);
  if (o.some((x) => x > 255)) return null;
  return ((o[0] << 24) >>> 0) + (o[1] << 16) + (o[2] << 8) + o[3];
}
function inCidr(n, cidr) {
  const [base, bitsRaw] = String(cidr).split('/');
  const b = v4(base);
  const bits = bitsRaw === undefined ? 32 : Number(bitsRaw);
  if (b === null || !Number.isInteger(bits) || bits < 0 || bits > 32) return false;
  const mask = bits === 0 ? 0 : (~0 << (32 - bits)) >>> 0;
  return ((n & mask) >>> 0) === ((b & mask) >>> 0);
}
const PRIVATE_V4 = ['127.0.0.0/8', '10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16', '169.254.0.0/16'];
function trustedPeer(addr) {
  const a = String(addr || '').trim().toLowerCase();
  if (!a || a === 'unknown') return false;
  const n = v4(a);
  const own = config.trustProxyFrom || [];
  if (own.length) {
    return own.some((x) => (n !== null ? inCidr(n, x) : String(x).trim().toLowerCase() === a));
  }
  if (n !== null) return PRIVATE_V4.some((c) => inCidr(n, c));
  //  IPv6: loopback، یکتای محلی (fc00::/7) و پیوندِ محلی (fe80::/10)
  return a === '::1' || /^f[cd][0-9a-f]{0,2}:/.test(a) || /^fe[89ab][0-9a-f]?:/.test(a);
}

/**
 * @param {object} opts
 * @param {function} [opts.key] کلید دلخواه (مثلاً شماره‌ی موبایل) به‌جای IP
 * @param {boolean} [opts.refundable] سهمِ این درخواست با `refund(req, keyPrefix)` پس‌دادنی است
 */
function rateLimit({ max, windowMs = config.rateLimit.windowMs, keyPrefix = '', key: keyFn = null, refundable = false }) {
  return function (req, res, next) {
    const key = `${keyPrefix}:${keyFn ? (keyFn(req) || clientIp(req)) : clientIp(req)}`;
    if (config.rateLimit.store === 'pg' && !pgDown()) return pgHit(req, res, next, { key, max, windowMs, keyPrefix, refundable });
    return memoryHit(req, res, next, { key, max, windowMs, keyPrefix, refundable });
  };
}

function memoryHit(req, res, next, { key, max, windowMs, keyPrefix, refundable }) {
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
}

// ── انبارِ مشترک (PostgreSQL) ───────────────────────────────────────────────
/** برش‌های هر پنجره؛ برشِ کوچک‌تر از یک ثانیه نه */
const SLOTS = 60;
const slotMsOf = (windowMs) => Math.max(1000, Math.ceil(windowMs / SLOTS));
/** پیشوند خوانا می‌ماند (کدام سطل)، بقیه درهم — ایمیل، شماره و IP در پایگاه نمی‌نشیند */
function storeKey(key) {
  const i = key.indexOf(':');
  const prefix = i >= 0 ? key.slice(0, i) : '';
  return `${prefix.slice(0, 48)}:${crypto.createHash('sha256').update(key).digest('base64url')}`;
}
/*
 *  یک دستور: جمعِ برش‌های پیشینِ پنجره (پایدار، پیش از این نوشته شده‌اند) + برشِ جاری زیرِ قفلِ ردیف
 *  (`ON CONFLICT DO UPDATE` آخرین نسخهٔ ردیف را قفل و همان را می‌سنجد). ردیف برگشت ⇒ پذیرفته؛ نه ⇒ رد.
 */
const HIT_SQL = `
  WITH older AS (
    SELECT COALESCE(sum(n), 0)::int AS s FROM rate_limit_hits WHERE key = $1 AND slot >= $3 AND slot < $2
  )
  INSERT INTO rate_limit_hits AS r (key, slot, n, expires_at)
  SELECT $1, $2, 1, $5 FROM older WHERE older.s < $4
  ON CONFLICT (key, slot) DO UPDATE SET n = r.n + 1
    WHERE r.n + (SELECT s FROM older) < $4
  RETURNING r.n`;
let downUntil = 0;
let lastWarn = 0;
const pgDown = () => downUntil > Date.now();
function pgFailed(err) {
  //  پایگاه در دسترس نیست ⇒ ۵ ثانیه همه با حافظه، تا هر درخواست پشتِ اتصالِ مرده نماند
  downUntil = Date.now() + 5000;
  if (Date.now() - lastWarn > 60_000) {
    lastWarn = Date.now();
    console.error('[ratelimit] انبارِ مشترک در دسترس نیست؛ موقتاً سقفِ همین نمونه (حافظه):', err && err.message);
  }
}
function pgHit(req, res, next, o) {
  const db = require('../db');
  const t = Date.now();
  const slotMs = slotMsOf(o.windowMs);
  const cur = Math.floor(t / slotMs);
  const lo = Math.floor((t - o.windowMs) / slotMs);
  const k = storeKey(o.key);
  const exp = (cur + 1) * slotMs + o.windowMs;
  db.query(HIT_SQL, [k, cur, lo, o.max, exp]).then(async (r) => {
    if (r.rows.length) {
      if (o.refundable) {
        if (!req.rateLimitTaken) req.rateLimitTaken = new Map();
        req.rateLimitTaken.set(o.keyPrefix, () => {
          db.query('UPDATE rate_limit_hits SET n = n - 1 WHERE key = $1 AND slot = $2 AND n > 0', [k, cur])
            .catch((err) => pgFailed(err));
        });
      }
      return next();
    }
    const row = await db.one('SELECT min(slot) AS s FROM rate_limit_hits WHERE key = $1 AND slot >= $2 AND n > 0', [k, lo]);
    const first = row && row.s != null ? Number(row.s) : cur;
    res.set('Retry-After', String(Math.max(1, Math.ceil(((first + 1) * slotMs + o.windowMs - t) / 1000))));
    return next(tooMany());
  }, (err) => {
    pgFailed(err);
    memoryHit(req, res, next, o);
  }).catch((err) => next(err));
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
/** برش‌های گذشته از پنجره (انبارِ مشترک) — فقط یک نمونه (رهبر)؛ نمایهٔ `expires_at` */
async function sweepStore(t = Date.now()) {
  if (config.rateLimit.store !== 'pg') return 0;
  const r = await require('../db').query('DELETE FROM rate_limit_hits WHERE expires_at < $1', [t]);
  return r.rowCount;
}
const sweeper = setInterval(() => {
  sweep();
  if (config.rateLimit.store === 'pg' && require('../lib/cluster').runsJobs()) sweepStore().catch((err) => pgFailed(err));
}, 60_000);
if (sweeper.unref) sweeper.unref();

function resetAll() { buckets.clear(); windows.clear(); downUntil = 0; }

module.exports = { rateLimit, refund, clientIp, trustedPeer, resetAll, sweep, sweepStore, storeKey, SLOTS };
