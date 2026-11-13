'use strict';
/**
 * OPS-01 — انتشارِ مرحله‌ای (staged rollout) با **برچسبِ نسخه‌دار**، پشتِ پیکربندی.
 *
 * ── چرا ─────────────────────────────────────────────────────────────
 * برچسبِ پایدار (`tohid-native`، `tohid-desktop`) یک برچسبِ روان است: هر انتشارِ پایدار
 * همان را بازنویسی می‌کند و همهٔ دکان‌ها هم‌زمان نسخهٔ تازه را می‌گیرند. برای «اول ۱۰٪»
 * باید نسخهٔ تازه جایی بماند که برچسبِ روان را جابه‌جا نکند: برچسبِ نسخه‌دارِ ثابت
 * `<برچسبِ پایدار>-v<نسخه>` (مثلاً `tohid-native-v2.14.5`) که هرگز بازنویسی نمی‌شود.
 *
 * ── پیکربندی (پیش‌فرض: نیست ⇒ رفتارِ پیشین، بایت‌به‌بایت) ────────────────
 *   live_config، scope `release`، کلیدِ `stage.<برچسبِ پایدار>`:
 *     { "version": "2.14.5", "percent": 10 }
 *   • `percent` ۰ تا ۱۰۰ (صحیح). ۰ = ایست (کسی تازه نمی‌گیرد؛ آن‌که گرفته پایین نمی‌آید).
 *   • مقدارِ ناجور (نسخهٔ بد، درصدِ بیرون از بازه، شکلِ دیگر) ⇒ **مرحله‌ای نیست** (fail-closed):
 *     دستگاه همان برچسبِ روان را می‌گیرد، هرگز نسخه‌ای که مدیر نخواسته.
 *
 *   اختیاری (۱۴۰۵/۰۸/۲۳، OA §۹د):
 *   • `shops`: فهرستِ شناسهٔ دکان (حداکثر MAX_SHOPS) — «اول چند دکانِ مشخص»: هر دستگاهِ این دکان‌ها
 *     درونِ مرحله است، هر درصدی که باشد (درصدِ ۰ + فهرست = فقط همین دکان‌ها).
 *   • `since`: لحظهٔ شروعِ همین نسخه (پنل می‌گذارد؛ نگهبانِ ایست خطاها را از همین لحظه می‌شمارد).
 *   • `halted`: `{at, reason, by}` — ایست (دستی یا نگهبانِ خودکارِ `release-stage-guard.js`): **هیچ**
 *     دستگاهی تازه نمی‌گیرد، نه فهرست و نه درصد؛ همه برچسبِ روان (= نسخهٔ پایدارِ پیشین). دانلودِ
 *     نیمه‌کاره ادامه دارد. فقط تصمیمِ تازهٔ مدیر (`PUT` ِ پنل) برش می‌دارد.
 *   هر کدام ناجور ⇒ کلِ مرحله ناجور (fail-closed) ⇒ برچسبِ روان برای همه.
 *
 * ── کدام دستگاه ─────────────────────────────────────────────────────
 *   سطل = SHA-256(`<برچسب>|<نسخه>|<دستگاه>`)، ۴ بایتِ اول، پیمانهٔ ۱۰۰. پایدار برای یک دستگاه
 *   در یک نسخه (بالا بردنِ درصد کسی را بیرون نمی‌اندازد)، و با هر نسخه از نو (همیشه همان
 *   دستگاه‌ها پیش‌قراول نیستند). بی شناسهٔ دستگاه ⇒ بیرون (مگر دکانش در `shops` باشد).
 *   ⛔ شناسهٔ دستگاه و دکان راز نیست: بدترین جعل، گرفتنِ زودترِ همان نصبیِ امضاشده است.
 *
 * فقط منطق؛ خواندنِ پیکربندی و پاسخ در `routes/releases.js`.
 */
const crypto = require('crypto');

const KEY_PREFIX = 'stage.';
const VERSION = /^\d{1,6}(\.\d{1,6}){1,3}$/;
const ID = /^[A-Za-z0-9_-]{1,80}$/;
/** سقفِ فهرستِ دکان‌های یک مرحله — مقدارِ `live_config` حداکثر ۴۰۹۶ نویسه است */
const MAX_SHOPS = 50;

/** `tohid-native` + `2.14.5` ⇒ `tohid-native-v2.14.5` */
function versionedTag(base, version) {
  return `${base}-v${version}`;
}

/** برچسبِ نسخه‌دارِ یکی از پایه‌ها ⇒ { base, version }؛ وگرنه null */
function parseVersionedTag(tag, bases) {
  const s = String(tag || '');
  for (const base of bases) {
    if (!s.startsWith(`${base}-v`)) continue;
    const version = s.slice(base.length + 2);
    if (VERSION.test(version)) return { base, version };
  }
  return null;
}

/** مقدارِ ذخیره‌شده ⇒ { version, percent } یا null (هر چیزِ ناجور ⇒ null) */
function parseStage(value) {
  let v = value;
  if (typeof v === 'string') {
    try { v = JSON.parse(v); } catch { return null; }
  }
  if (!v || typeof v !== 'object' || Array.isArray(v)) return null;
  const version = typeof v.version === 'string' ? v.version.trim() : '';
  if (!VERSION.test(version)) return null;
  const percent = v.percent;
  if (!Number.isInteger(percent) || percent < 0 || percent > 100) return null;
  const out = { version, percent };
  if (v.shops !== undefined) {
    const shops = v.shops;
    if (!Array.isArray(shops) || shops.length > MAX_SHOPS || !shops.every((x) => typeof x === 'string' && ID.test(x))) return null;
    if (shops.length) out.shops = [...new Set(shops)];
  }
  if (v.since !== undefined) {
    if (!Number.isSafeInteger(v.since) || v.since <= 0) return null;
    out.since = v.since;
  }
  if (v.halted !== undefined && v.halted !== null) {
    const h = v.halted;
    if (typeof h !== 'object' || Array.isArray(h) || !Number.isSafeInteger(h.at) || h.at <= 0) return null;
    out.halted = { at: h.at, reason: String(h.reason ?? '').slice(0, 300), by: String(h.by ?? '').slice(0, 80) };
  }
  return out;
}

/** مرحله هنوز به کسی نسخهٔ تازه می‌دهد؟ (نایستاده و درصد یا فهرست دارد) */
function isActive(stage) {
  return !!stage && !stage.halted && (stage.percent > 0 || (stage.shops || []).length > 0);
}

/** سطلِ ۰ تا ۹۹ ِ یک دستگاه برای یک نسخه */
function bucketOf(base, version, device) {
  const h = crypto.createHash('sha256').update(`${base}|${version}|${device}`).digest();
  return h.readUInt32BE(0) % 100;
}

/** این دستگاه (از این دکان) در مرحلهٔ جاریِ این برچسب هست؟ ایستاده ⇒ هیچ‌کس. */
function inStage(stage, base, device, shop = '') {
  if (!stage || stage.halted) return false;
  const s = String(shop || '');
  if (stage.shops && ID.test(s) && stage.shops.includes(s)) return true;
  const d = String(device || '');
  if (stage.percent <= 0 || !ID.test(d)) return false;
  return bucketOf(base, stage.version, d) < stage.percent;
}

/** فهرستِ live_config ِ scope `release` ⇒ Map(برچسبِ پایه ⇒ مرحله)، فقط برای پایه‌های شناخته */
function stagesOf(items, bases) {
  const out = new Map();
  for (const it of items || []) {
    const key = String(it.key || '');
    if (!key.startsWith(KEY_PREFIX)) continue;
    const base = key.slice(KEY_PREFIX.length);
    if (!bases.includes(base)) continue;
    const stage = parseStage(it.value);
    if (stage) out.set(base, stage);
  }
  return out;
}

module.exports = { KEY_PREFIX, VERSION, ID, MAX_SHOPS, versionedTag, parseVersionedTag, parseStage, isActive, bucketOf, inStage, stagesOf };
