'use strict';
/**
 * ساعتِ منطقیِ ترکیبی (HLC) — Sync v3، SYN-05 و بخشِ ۳.۸.۱ بندِ ۸.
 *
 * ── شکل ───────────────────────────────────────────────────────────
 *   "<wall:15 رقم>:<counter:6 رقم>:<node>"
 *   000001760000000000:000003:dev-a
 *
 *   wall    میلی‌ثانیهٔ UTC («زمانِ تصحیح‌شدهٔ» دستگاه)
 *   counter برای چند رویداد در یک میلی‌ثانیه
 *   node    شناسهٔ دستگاه (یا `server`) — فقط برای شکستنِ تساوی
 *
 * چون wall و counter پهنای ثابت دارند، **مقایسهٔ رشته همان مقایسهٔ زمان
 * است**؛ پس `ORDER BY hlc` و `hlc <= $T` در دیتابیس درست کار می‌کنند.
 * ورودیِ بی‌صفر (`1760000000000:3:dev-a`) هم پذیرفته و قانونی می‌شود.
 *
 * ── نگهبانِ ساعتِ غلط ─────────────────────────────────────────────
 * HLCی که بیش از `SKEW_MS` (۵ دقیقه) از ساعتِ سرور جلوتر است «کج»
 * است: سرور آن را ثبت می‌کند ولی (۱) ساعتِ خودش را با آن جلو نمی‌برد و
 * (۲) در «آخرین نوشتن» برنده‌اش نمی‌کند — آن تصمیم در `sync-v3.js` است.
 * ساعتِ **عقب‌مانده** خطا نیست: آفلاین مهلت ندارد (D-05) و سندِ قدیمی
 * فقط در «آخرین نوشتن» به نوشتهٔ تازه‌تر می‌بازد.
 */

const SKEW_MS = 5 * 60 * 1000;
const WALL_DIGITS = 15;
const COUNTER_DIGITS = 6;
const MAX_COUNTER = 999_999;
const RE = /^(\d{1,15}):(\d{1,6}):([A-Za-z0-9_.-]{1,64})$/;

/** @returns {{wall:number, counter:number, node:string} | null} */
function parse(s) {
  if (typeof s !== 'string') return null;
  const m = RE.exec(s);
  if (!m) return null;
  const wall = Number(m[1]);
  const counter = Number(m[2]);
  if (!Number.isSafeInteger(wall) || !Number.isSafeInteger(counter)) return null;
  return { wall, counter, node: m[3] };
}

function format({ wall, counter, node }) {
  return `${String(Math.max(0, Math.trunc(wall))).padStart(WALL_DIGITS, '0')}:`
    + `${String(Math.max(0, Math.trunc(counter))).padStart(COUNTER_DIGITS, '0')}:${node}`;
}

/** شکلِ قانونی (با صفرهای پیشرو)، یا `''` اگر نادرست باشد. */
function canonical(s) {
  const p = parse(s);
  return p ? format(p) : '';
}

function asTuple(x) { return typeof x === 'string' ? parse(x) : x; }

/** −۱ / ۰ / ۱ — رشته یا شیء. */
function compare(a, b) {
  const x = asTuple(a);
  const y = asTuple(b);
  if (x.wall !== y.wall) return x.wall < y.wall ? -1 : 1;
  if (x.counter !== y.counter) return x.counter < y.counter ? -1 : 1;
  if (x.node === y.node) return 0;
  return x.node < y.node ? -1 : 1;
}

/** زمانِ سادهٔ op v2 (`ts`) ⇒ HLC. عددِ نادرست یا منفی ⇒ صفر (همیشه بازنده). */
function fromTs(ts, counter, node) {
  const n = Number(ts);
  const wall = Number.isFinite(n) && n > 0 ? Math.min(Math.trunc(n), 10 ** WALL_DIGITS - 1) : 0;
  return format({ wall, counter: Math.min(Math.max(0, counter | 0), MAX_COUNTER), node });
}

/**
 * ساعتِ یک گره (سرور). حالتش در حافظه است؛ `observe` آن را پس از
 * راه‌اندازیِ دوباره از بزرگ‌ترین HLCِ ثبت‌شده جلو می‌برد.
 */
function createClock({ node = 'server', now = Date.now, skewMs = SKEW_MS } = {}) {
  let last = { wall: 0, counter: 0 };

  function bump(wall, counter) {
    if (counter > MAX_COUNTER) { wall += 1; counter = 0; }
    last = { wall, counter };
    return format({ wall, counter, node });
  }

  return {
    node,
    skewMs,
    /** رویدادِ تازهٔ خودِ این گره. */
    tick() {
      const pt = now();
      if (pt > last.wall) return bump(pt, 0);
      return bump(last.wall, last.counter + 1);
    },
    /**
     * HLCِ دستگاه رسید. کج ⇒ `{skewed:true}` و ساعت دست نمی‌خورد.
     * @returns {{skewed:boolean, hlc:string}}
     */
    recv(remote) {
      const r = asTuple(remote);
      const pt = now();
      if (!r) return { skewed: false, hlc: this.tick() };
      if (r.wall > pt + skewMs) return { skewed: true, hlc: format({ ...last, node }) };
      const wall = Math.max(last.wall, r.wall, pt);
      let counter;
      if (wall === last.wall && wall === r.wall) counter = Math.max(last.counter, r.counter) + 1;
      else if (wall === last.wall) counter = last.counter + 1;
      else if (wall === r.wall) counter = r.counter + 1;
      else counter = 0;
      return { skewed: false, hlc: bump(wall, counter) };
    },
    /** بی شرطِ کجی — فقط برای راه‌اندازی از روی دفترِ ثبت‌شده. */
    observe(seen) {
      const s = asTuple(seen);
      if (!s) return;
      if (s.wall > last.wall || (s.wall === last.wall && s.counter > last.counter)) {
        last = { wall: s.wall, counter: s.counter };
      }
    },
    isSkewed(remote) {
      const r = asTuple(remote);
      return !!r && r.wall > now() + skewMs;
    },
    peek() { return format({ ...last, node }); },
  };
}

module.exports = { SKEW_MS, parse, format, canonical, compare, fromTs, createClock };
