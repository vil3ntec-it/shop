'use strict';
/**
 * بازه‌ها و «سطل»های زمانیِ مالیِ مرکزی (ماژولِ ۴.۵) — به وقتِ `config.defaults.timezone` (کابل).
 * روز = روزِ تقویمیِ محلی؛ هفته از **شنبه**؛ ماه پیش‌فرض **خورشیدی** (`calendar=persian`) یا میلادی.
 * بی وابستگی به پایگاه — آزمون‌پذیر بی سرور (`central-finance.test.js`).
 */
const config = require('../config');
const { now } = require('../db');
const { badRequest } = require('../middleware/errors');
const v = require('./validate');

const DAY = 24 * 60 * 60 * 1000;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const GROUPS = Object.freeze(['day', 'week', 'month']);
const LIMITS = Object.freeze({ maxDays: 732, maxBuckets: 400 });
const tz = () => config.defaults.timezone || 'Asia/Kabul';

/** اختلافِ ساعتِ منطقه با UTC در لحظهٔ `at` (میلی‌ثانیه). */
function tzOffsetMs(at, zone) {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: zone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(new Date(at)).map((x) => [x.type, x.value]));
  return Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour % 24, +p.minute, +p.second) - Math.floor(at / 1000) * 1000;
}
/** آغازِ روزِ `YYYY-MM-DD` در منطقهٔ `zone`. */
function dayStart(ymd, zone = tz(), addDays = 0) {
  const [y, m, d] = ymd.split('-').map(Number);
  const utc = Date.UTC(y, m - 1, d + addDays);
  const guess = utc - tzOffsetMs(utc, zone);
  return utc - tzOffsetMs(guess, zone);
}
function localYmd(at, zone = tz()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(at));
}
function ymdAdd(ymd, n) {
  const [y, m, d] = ymd.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}
const dowOf = (ymd) => { const [y, m, d] = ymd.split('-').map(Number); return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); };
const PERSIAN = new Intl.DateTimeFormat('en-u-ca-persian-nu-latn', { timeZone: 'UTC', year: 'numeric', month: 'numeric', day: 'numeric' });
/** تاریخِ خورشیدیِ یک روزِ تقویمی. */
function persianOf(ymd) {
  const [y, m, d] = ymd.split('-').map(Number);
  const p = Object.fromEntries(PERSIAN.formatToParts(new Date(Date.UTC(y, m - 1, d, 12))).map((x) => [x.type, x.value]));
  return { y: Number(p.year), m: Number(p.month), d: Number(p.day) };
}
const pad = (n) => String(n).padStart(2, '0');

/** آغازِ «سطل»ی که روزِ `ymd` در آن است. هفته از شنبه. */
function bucketYmd(ymd, group, calendar) {
  if (group === 'day') return ymd;
  if (group === 'week') return ymdAdd(ymd, -((dowOf(ymd) + 1) % 7));
  if (calendar === 'gregorian') return `${ymd.slice(0, 8)}01`;
  return ymdAdd(ymd, -(persianOf(ymd).d - 1));
}
function nextBucketYmd(start, group, calendar) {
  if (group === 'day') return ymdAdd(start, 1);
  if (group === 'week') return ymdAdd(start, 7);
  return bucketYmd(ymdAdd(start, 32), 'month', calendar);
}
function bucketLabel(start, group, calendar) {
  if (calendar === 'gregorian') return group === 'month' ? start.slice(0, 7) : start;
  const p = persianOf(start);
  return group === 'month' ? `${p.y}/${pad(p.m)}` : `${p.y}/${pad(p.m)}/${pad(p.d)}`;
}

function badWindow(msg = 'بازه معتبر نیست') { return badRequest(msg, 'bad_window'); }

/**
 * بازهٔ گزارش. `fromDate`/`toDate` (روزِ تقویمیِ میلادی، «تا» شامل) جلوتر از `from`/`to` (میلی‌ثانیه)؛
 * بی هر دو ⇒ پیش‌فرضِ هر گروه (۳۰ روز · ۱۲ هفته · ۱۲ ماه، تا همین لحظه).
 */
function windowOf(q = {}, at = now()) {
  const zone = tz();
  const group = v.oneOf(q.group, GROUPS, { field: 'گروه‌بندی', def: 'day' });
  const calendar = v.oneOf(q.calendar, ['persian', 'gregorian'], { field: 'تقویم', def: 'persian' });
  const date = (x) => {
    if (x === undefined || x === null || x === '') return null;
    const s = String(x);
    const d = DATE_RE.test(s) ? new Date(`${s}T00:00:00Z`) : null;
    if (!d || Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== s) throw badWindow();
    return s;
  };
  const ms = (x) => {
    if (x === undefined || x === null || x === '') return null;
    const n = Number(x);
    if (!Number.isSafeInteger(n) || n < 0) throw badWindow();
    return n;
  };
  const fromDate = date(q.fromDate);
  const toDate = date(q.toDate);
  let to = toDate ? dayStart(toDate, zone, 1) : ms(q.to) ?? at;
  let from = fromDate ? dayStart(fromDate, zone) : ms(q.from);
  if (from === null) {
    const today = localYmd(Math.max(0, to - 1), zone);
    let start = bucketYmd(today, group, calendar);
    const back = group === 'day' ? 29 : 11;
    for (let i = 0; i < back; i++) start = bucketYmd(ymdAdd(start, -1), group, calendar);
    from = dayStart(start, zone);
  }
  if (!(from < to)) throw badWindow('آغازِ بازه باید پیش از پایانش باشد');
  if (to - from > LIMITS.maxDays * DAY) throw badWindow(`بازه حداکثر ${LIMITS.maxDays} روز است`);
  return { from, to, timezone: zone, group, calendar, fromDate: localYmd(from, zone), toDate: localYmd(to - 1, zone) };
}

/** سطل‌های بازه، به ترتیب. سطلِ اول/آخر به خودِ بازه بریده می‌شود. */
function bucketsOf(w) {
  const out = [];
  let ymd = bucketYmd(localYmd(w.from, w.timezone), w.group, w.calendar);
  while (dayStart(ymd, w.timezone) < w.to) {
    if (out.length >= LIMITS.maxBuckets) throw badWindow(`این بازه با این گروه‌بندی بیش از ${LIMITS.maxBuckets} ستون می‌شود`);
    const next = nextBucketYmd(ymd, w.group, w.calendar);
    out.push({
      key: ymd, label: bucketLabel(ymd, w.group, w.calendar),
      from: Math.max(dayStart(ymd, w.timezone), w.from), to: Math.min(dayStart(next, w.timezone), w.to),
    });
    ymd = next;
  }
  return out;
}

module.exports = { DAY, LIMITS, GROUPS, tz, badWindow, windowOf, bucketsOf, bucketYmd, nextBucketYmd, localYmd, dayStart, ymdAdd, persianOf };
