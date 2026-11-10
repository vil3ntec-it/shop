'use strict';
/**
 * RPT-13 — «دورهٔ قبل / بعد» و «تغییر نسبت به دورهٔ قبل» در نسخهٔ وب (`license/report-period-core.js`).
 *
 *  ۱) بردارهای مرجعِ مشترک (`core/test-vectors/report-period-v1.json`): همان پرونده را `ReportPeriodVectorsTest.kt` روی
 *     `core/report/ReportPeriod` ِ Kotlin می‌دواند؛ موردهای `ReportPeriodTest.kt` (که در CI سبز است) عیناً در آن آمده‌اند.
 *  ۲) هیچ دوره‌ای سراسر در آینده نیست؛ پس و پیشِ پی‌درپی به همان بازه برمی‌گردد؛ درصد بی «صفرِ منفی» و همیشه عددِ صحیح.
 *  ۳) ES5 (همان قیدِ report-core).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FILE = path.join(ROOT, 'license', 'report-period-core.js');
const P = require(FILE);
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'report-period-v1.json'), 'utf8'));

test('بردارهای مرجع: هر مورد عیناً همان expect (همان ReportPeriod ِ Kotlin)', () => {
  assert.equal(V.format, P.VECTORS_FORMAT);
  assert.equal(V.max_diff, P.MAX_DIFF);
  assert.equal(P.MAX_DIFF, Math.floor(Number.MAX_SAFE_INTEGER / 10000));
  assert.ok(V.shift.length >= 25 && V.change.length >= 15, `${V.shift.length} / ${V.change.length}`);
  for (const c of V.shift) {
    assert.ok(P.UNITS.includes(c.unit), c.name);
    assert.deepEqual(P.shift(c.unit, c.from, c.to, c.steps, c.today), c.expect, c.name);
  }
  for (const c of V.change) assert.deepEqual(P.change(c.current, c.previous), c.expect, c.name);
});

test('هیچ دورهٔ سراسر آینده؛ پس و پیش به همان بازه برمی‌گردد', () => {
  const today = '2026-10-31';
  for (const [unit, from, to] of [['days', '2026-10-25', today], ['days', today, today], ['gmonth', '2026-10-01', today], ['jmonth', '2026-10-23', '2026-11-21'], ['jyear', '2026-03-21', '2027-03-20']]) {
    for (let k = 1; k <= 26; k++) {
      const back = P.shift(unit, from, to, -k, today);
      assert.ok(back && back.from <= today && back.from <= back.to, `${unit} −${k}`);
      const again = P.shift(unit, back.from, back.to, k, today);
      if (unit === 'gmonth') assert.equal(again.from, from, `${unit} −${k}+${k}`);
      else assert.deepEqual(again, P.shift(unit, from, to, 0, today), `${unit} −${k}+${k}`);
    }
    assert.equal(P.shift(unit, from, to, 1, today), null, `${unit}: دورهٔ بعد ِ جاری`);
  }
  assert.equal(P.shift('days', today, today, 1.5, today), null, 'گامِ ناصحیح');
  assert.equal(P.shift('weeks', today, today, -1, today), null, 'واحدِ ناشناخته');
});

test('درصد همیشه عددِ صحیح و بی «صفرِ منفی»', () => {
  for (let cur = -40; cur <= 40; cur += 3) {
    for (const prev of [-7, -1, 1, 3, 7, 9]) {
      const c = P.change(cur, prev);
      assert.ok(Number.isSafeInteger(c.bp), `${cur}/${prev}`);
      assert.ok(!Object.is(c.bp, -0), `${cur}/${prev}`);
      //  همان گردِ «نیمه دور از صفر» با حسابِ کسرِ دقیق
      const exact = ((cur - prev) * 10000) / Math.abs(prev);
      assert.equal(c.bp, Math.sign(exact) * Math.round(Math.abs(exact)) || 0, `${cur}/${prev}`);
    }
  }
});

test('ES5: بی let/const/arrow/backtick — همان قیدِ report-core', () => {
  const src = fs.readFileSync(FILE, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  assert.doesNotMatch(src, /=>|`|\blet\s|\bconst\s|\bclass\s/);
});
