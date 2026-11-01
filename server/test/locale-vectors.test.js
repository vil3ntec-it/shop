'use strict';
/**
 * بخش‌های ۳.۱۳ تا ۳.۱۵ — کلیدِ جست‌وجو، تقویم، ارقام، افغانی و تلفن.
 *
 * همان فایل‌های `core/test-vectors/*.json` که آزمونِ کاتلینِ نیتیو
 * (`CoreVectorsTest.kt`) می‌خواند، این‌جا روی پیاده‌سازیِ وب
 * (`license/text-core.js` و `license/calendar-core.js`) دویده می‌شوند.
 * سرور نسخهٔ جدایی از این منطق ندارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const T = require(path.join(ROOT, 'license', 'text-core.js'));
const C = require(path.join(ROOT, 'license', 'calendar-core.js'));
const vec = (name) => JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', name), 'utf8'));
const TEXT = vec('text-normalize.json');
const LOC = vec('locale-format.json');
const CAL = vec('calendar.json');
const ymd = (o) => (o ? [o.y, o.m, o.d] : null);

test('کلیدِ جست‌وجو و کلیدِ مقایسه', () => {
  for (const v of TEXT.searchKey) assert.equal(T.searchKey(v.in), v.out, JSON.stringify(v));
  for (const v of TEXT.compareKey) assert.equal(T.compareKey(v.in), v.out, JSON.stringify(v));
});

test('جورشدن، مترادف و غلطِ تایپی', () => {
  for (const v of TEXT.matches) assert.equal(T.matches(v.text, v.query, v.synonyms || null), v.out, JSON.stringify(v));
  for (const v of TEXT.editDistance) assert.equal(T.editDistance(v.a, v.b, v.max), v.out, JSON.stringify(v));
  for (const v of TEXT.typoBudget) assert.equal(T.typoBudget(v.len), v.out, JSON.stringify(v));
  for (const v of TEXT.fuzzyMatches) assert.equal(T.fuzzyMatches(v.text, v.query), v.out, JSON.stringify(v));
});

test('ارقام، افغانی و تلفن', () => {
  for (const v of LOC.asciiDigits) assert.equal(T.asciiDigits(v.in), v.out, JSON.stringify(v));
  for (const v of LOC.displayDigits) assert.equal(T.displayDigits(v.in, v.style), v.out, JSON.stringify(v));
  for (const v of LOC.formatAfghani) assert.equal(T.formatAfghani(v.amount, v.symbol, v.digits), v.out, JSON.stringify(v));
  for (const v of LOC.normalizePhone) assert.equal(T.normalizePhone(v.in), v.out, JSON.stringify(v));
  for (const v of LOC.formatPhone) assert.equal(T.formatPhone(v.in), v.out, JSON.stringify(v));
});

test('خورشیدی: تاریخ‌های شناخته‌شده، کبیسه، نام ماه‌ها و قالب', () => {
  for (const v of CAL.solar) {
    assert.deepEqual(ymd(C.toSolar(v.iso)), v.solar, v.iso);
    assert.equal(C.fromSolar(...v.solar), v.iso, v.iso);
  }
  for (const iso of CAL.invalidIso) assert.equal(C.toSolar(iso), null, iso);
  for (const s of CAL.fromSolarInvalid) assert.equal(C.fromSolar(...s), null, String(s));
  for (const v of CAL.fromSolarExtra) assert.equal(C.fromSolar(...v.solar), v.iso);
  const leaps = [];
  for (let y = 1395; y <= 1415; y++) if (C.isSolarLeap(y)) leaps.push(y);
  assert.deepEqual(leaps, CAL.leapYears1395to1415);
  for (const v of CAL.monthLength) assert.equal(C.solarMonthLength(v.y, v.m), v.out, JSON.stringify(v));
  for (let m = 1; m <= 12; m++) {
    assert.equal(C.solarMonthName(m, 'fa'), CAL.monthNames.fa[m - 1]);
    assert.equal(C.solarMonthName(m, 'ps'), CAL.monthNames.ps[m - 1]);
  }
  for (const v of CAL.formatSolarNumeric) assert.equal(C.formatSolarNumeric(v.iso, v.digits), v.out, JSON.stringify(v));
  for (const v of CAL.formatSolarLong) assert.equal(C.formatSolarLong(v.iso, v.lang, v.digits), v.out, JSON.stringify(v));
  for (const v of CAL.fiscalYear) assert.deepEqual(C.fiscalYearRange(v.y), { from: v.from, to: v.to });
});

test('LOC-02: سه‌ماههٔ مالی (حمل–جوزا … جدی–حوت) و سه‌ماههٔ یک روز', () => {
  for (const v of CAL.fiscalQuarter) {
    assert.deepEqual(C.fiscalQuarterRange(v.y, v.q), v.from === null ? null : { from: v.from, to: v.to }, JSON.stringify(v));
  }
  for (const v of CAL.fiscalQuarterOf) {
    assert.deepEqual(C.fiscalQuarterOf(v.iso), v.y === null ? null : { y: v.y, q: v.q }, v.iso);
  }
  //  چهار سه‌ماهه بی شکاف و بی هم‌پوشانی همان سالِ مالی را می‌پوشانند (۱۳۰۰ تا ۱۵۰۰)
  for (let y = 1300; y <= 1500; y++) {
    const year = C.fiscalYearRange(y);
    const qs = [1, 2, 3, 4].map((q) => C.fiscalQuarterRange(y, q));
    assert.equal(qs[0].from, year.from, String(y));
    assert.equal(qs[3].to, year.to, String(y));
    for (let q = 1; q < 4; q++) assert.equal(C.addDays(qs[q - 1].to, 1), qs[q].from, `${y}/${q}`);
    for (let q = 1; q <= 4; q++) {
      assert.deepEqual(C.fiscalQuarterOf(qs[q - 1].from), { y, q });
      assert.deepEqual(C.fiscalQuarterOf(qs[q - 1].to), { y, q });
    }
  }
  assert.equal(C.fiscalQuarterRange(1405, 2.5), null, 'سه‌ماههٔ غیرِ صحیح');
});

test('قمری، روزِ هفته و روزِ کاری', () => {
  for (const v of CAL.lunar) assert.deepEqual(ymd(C.toLunar(v.iso, v.adjust)), v.lunar, JSON.stringify(v));
  for (const v of CAL.formatLunarLong) assert.equal(C.formatLunarLong(v.iso, v.digits, v.adjust), v.out, JSON.stringify(v));
  for (const v of CAL.weekday) assert.equal(C.isoWeekday(v.iso), v.out, v.iso);
  for (const v of CAL.addDays) assert.equal(C.addDays(v.iso, v.n), v.out, JSON.stringify(v));
  for (const v of CAL.weekStart) assert.equal(C.weekStartOf(v.iso, v.rules), v.out, JSON.stringify(v));
  for (const v of CAL.dayKind) assert.equal(C.dayKind(v.iso, v.rules), v.out, JSON.stringify(v));
  for (const v of CAL.businessDate) assert.equal(C.businessDate(v.local, v.dayStart), v.out, JSON.stringify(v));
  for (const v of CAL.businessDateAt) assert.equal(C.businessDateAt(v.epochMs, v.offset, v.dayStart), v.out, JSON.stringify(v));
});

test('خورشیدی ۱۳۰۰ تا ۱۵۰۰: هر روز رفت‌وبرگشت، پیوسته، و برابر با Intl ِ مرورگر', () => {
  const persian = new Intl.DateTimeFormat('en-US-u-ca-persian-nu-latn', { timeZone: 'UTC', year: 'numeric', month: 'numeric', day: 'numeric' });
  const civil = new Intl.DateTimeFormat('en-US-u-ca-islamic-civil-nu-latn', { timeZone: 'UTC', year: 'numeric', month: 'numeric', day: 'numeric' });
  const hasIntl = persian.resolvedOptions().calendar === 'persian' && civil.resolvedOptions().calendar === 'islamic-civil';
  const parts = (f, iso) => {
    const p = f.formatToParts(new Date(iso + 'T00:00:00Z'));
    const g = (t) => parseInt(p.find((x) => x.type === t).value, 10);
    return [g('year'), g('month'), g('day')];
  };
  const first = C.jdnOfIso(C.fromSolar(1300, 1, 1));
  const last = C.jdnOfIso(C.fromSolar(1500, 12, C.solarMonthLength(1500, 12)));
  let prev = null;
  let days = 0;
  for (let j = first; j <= last; j++, days++) {
    const iso = C.isoOfJdn(j);
    const s = C.toSolar(iso);
    assert.equal(C.fromSolar(s.y, s.m, s.d), iso, iso);
    if (prev) {
      const next = prev.d < C.solarMonthLength(prev.y, prev.m) ? [prev.y, prev.m, prev.d + 1]
        : prev.m < 12 ? [prev.y, prev.m + 1, 1] : [prev.y + 1, 1, 1];
      assert.deepEqual(ymd(s), next, iso);
    }
    prev = s;
    if (hasIntl) {
      assert.deepEqual(ymd(s), parts(persian, iso), `persian ${iso}`);
      assert.deepEqual(ymd(C.toLunar(iso, 0)), parts(civil, iso), `islamic-civil ${iso}`);
    }
  }
  assert.equal(days, 73414);
});
