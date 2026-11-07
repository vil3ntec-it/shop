'use strict';
/**
 * حافظهٔ کوتاهِ چند خواندنِ داغ — `HOT_CACHE_MS` (پیش‌فرض ۰ = خاموش، همان پرسش در هر درخواست؛ docs/SCALING-fa.md).
 *
 * چرا: آزمونِ بار (docs/PERF-LOAD-fa.md، ۱۴۰۵/۰۸/۱۷) نشان داد هر درخواستِ ویترین ~۲۲ پرسش به پایگاه می‌زند و
 * نیمی از آن‌ها همان «پرچمِ دکان» ِ `orders.flagOf` است (~۱۰ بار برای یک درخواست). هزینه زمانِ پایگاه نیست
 * (هر کدام ~۰٫۰۱ms) بلکه رفت‌وبرگشت و نوبتِ استخر؛ زیرِ بار همین صفِ استخر است که p95 را بالا می‌برد.
 *
 *   • `get(ns, key, loader)`: تا `ttl` میلی‌ثانیه همان مقدار؛ خواندن‌های هم‌زمانِ یک کلید یک پرسش‌اند.
 *   • `invalidate(ns, prefix)`: پس از نوشتن (پس از commit) — همان دم در همین نمونه و با گذرگاهِ
 *     `lib/cluster.js` در نمونه‌های دیگر. نوشتنی که وسطِ خواندن رسید، نتیجهٔ آن خواندن را در حافظه نمی‌گذارد.
 *   • نوشتن‌های پرچم همه از یک در می‌روند (`live-config.set/setOnce/remove`، `shop-holds.place/lift`) و همان‌جا
 *     باطل می‌شوند؛ نوشتنِ مستقیم با SQL (فقط آزمون‌ها) تا `ttl` دیده نمی‌شود — برای همین پیش‌فرض خاموش است.
 * ⛔ هیچ دادهٔ مشتری یا پولی این‌جا نیست: فقط پرچم‌های روشن/خاموش و تقویمِ تعطیلی.
 */
const config = require('../config');
const cluster = require('./cluster');

const MAX_KEYS = 20_000;
const spaces = new Map();   //  ns ⇒ Map(key ⇒ { at, v, p })

const ttl = () => Number(config.hotCache && config.hotCache.ttlMs) || 0;

async function get(ns, key, loader) {
  const ms = ttl();
  if (!(ms > 0)) return loader();
  let m = spaces.get(ns);
  if (!m) { m = new Map(); spaces.set(ns, m); }
  const e = m.get(key);
  if (e && (e.p || Date.now() - e.at < ms)) return e.p || e.v;
  const entry = { at: 0, v: undefined, p: null };
  entry.p = Promise.resolve().then(loader).then((v) => {
    if (m.get(key) === entry) { entry.v = v; entry.at = Date.now(); entry.p = null; }
    return v;
  }, (err) => {
    if (m.get(key) === entry) m.delete(key);
    throw err;
  });
  if (m.size >= MAX_KEYS) m.clear();
  m.set(key, entry);
  return entry.p;
}

function invalidateHere(ns, prefix = '') {
  const m = spaces.get(ns);
  if (!m) return;
  if (!prefix) { m.clear(); return; }
  for (const k of [...m.keys()]) if (k.startsWith(prefix)) m.delete(k);
}
/** باطل کردن (همه یا کلیدهای با این پیشوند) — همین نمونه و نمونه‌های دیگر */
function invalidate(ns, prefix = '') {
  invalidateHere(ns, prefix);
  cluster.publish('hot-cache', { ns, p: prefix });
}
cluster.subscribe('hot-cache', (d) => invalidateHere(String(d.ns || ''), String(d.p || '')));
cluster.onResync(() => spaces.clear());

function clear() { spaces.clear(); }

module.exports = { get, invalidate, clear, ttl };
