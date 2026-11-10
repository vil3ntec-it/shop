/**
 * OPS-05 — سنجهٔ «تازه‌ها» ِ PR (`check.mjs`). اجرا: node --test tools/whatsnew/check.test.mjs
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { addedEntries, check, isAppCode, scan } from './check.mjs';

const BEFORE = `# تازه‌ها
- تازه: این سطرِ شرح است و شمرده نمی‌شود

## 3.2 · ۱۴۰۵/۰۸/۱۱ — پیشین
<!-- shipped: preview=61 beta=1896 -->
- تازه: کارِ پیشین
`;
const AFTER = `# تازه‌ها
- تازه: این سطرِ شرح است و شمرده نمی‌شود

## 3.2 · ۱۴۰۵/۰۸/۱۲ — تازه
- رفع: کارِ  تازه
- برداشته: شکلِ قدیم ⇐ به‌جایش: شکلِ تازه

## 3.2 · ۱۴۰۵/۰۸/۱۱ — پیشین
<!-- shipped: preview=61 beta=1896 -->
- تازه: کارِ پیشین
`;

test('only user-facing app code needs a line', () => {
  assert.ok(isAppCode('android-native/app/src/main/java/ir/vil3ntec/tohid/ui/AppRoot.kt'));
  assert.ok(isAppCode('core/src/commonMain/kotlin/x.kt'));
  assert.ok(isAppCode('desktop/src/main/kotlin/x.kt'));
  assert.ok(isAppCode('ios/iosApp/App.swift'));
  assert.ok(!isAppCode('android-native/app/src/test/java/x.kt'));
  assert.ok(!isAppCode('core/src/commonTest/kotlin/x.kt'));
  assert.ok(!isAppCode('server/src/routes/x.js'));
  assert.ok(!isAppCode('docs/WHATS-NEW-fa.md'));
});

test('app change with a new line passes; without one it is flagged', () => {
  const changed = ['android-native/app/src/main/java/x.kt', 'docs/WHATS-NEW-fa.md'];
  const ok = check({ changed, before: BEFORE, after: AFTER });
  assert.equal(ok.code, 0);
  assert.deepEqual(ok.added, ['- رفع: کارِ تازه', '- برداشته: شکلِ قدیم ⇐ به‌جایش: شکلِ تازه']);
  assert.equal(check({ changed: ['android-native/app/src/main/java/x.kt'], before: BEFORE, after: BEFORE }).code, 1);
  //  سطرِ شرح (پیش از نخستین بخش) سطرِ تازه نیست
  const preambleOnly = BEFORE.replace('# تازه‌ها\n', '# تازه‌ها\n- تازه: شرحِ دیگر\n');
  assert.equal(check({ changed: ['core/src/commonMain/x.kt'], before: BEFORE, after: preambleOnly }).code, 1);
  //  سرور و آزمون سطر نمی‌خواهند
  assert.equal(check({ changed: ['server/src/a.js', 'core/src/commonTest/a.kt'], before: BEFORE, after: BEFORE }).code, 0);
});

test('moved or duplicated lines count only once', () => {
  assert.deepEqual(addedEntries(AFTER, AFTER), []);
  assert.deepEqual(addedEntries(BEFORE, BEFORE + '- تازه: کارِ پیشین\n'), ['- تازه: کارِ پیشین']);
});

test('bad format is reported (same rules as WhatsNew.parse)', () => {
  const bad = scan(`## 3.2 · خوب
- برداشته: بی جای تازه
- تازه: با ⇐ به‌جایش: نه
- شاید: نوعِ ناشناخته
متنِ آزاد
- تازه:
<!-- shipped: preview=x -->
## بی نسخه
- تازه: زیرِ سرخطِ بد
## 3.2 · خالی
`);
  assert.equal(bad.problems.length, 8, bad.problems.join('\n'));
  assert.equal(check({ changed: [], before: '', after: '## 3.2 · x\nمتن\n' }).code, 2);
  //  توضیحِ چندسطری کنار می‌رود
  assert.deepEqual(scan('## 3.2 · x\n<!--\n- نه: این توضیح است\n-->\n- تازه: بله\n').problems, []);
  //  دو مشکلِ دیگرِ WhatsNew.parse: سطرِ هم‌کلید (فاصله‌ها یکدست، حتی در بخشِ دیگر) و shipped ِ صفر
  assert.match(scan('## 3.2 · x\n- تازه: یک\n## 3.2 · y\n- تازه:  یک \n').problems.join(), /پیش‌تر آمده/);
  assert.match(scan('## 3.2 · x\n<!-- shipped: preview=0 -->\n- تازه: یک\n').problems.join(), /preview=0/);
  //  بخش‌ها (همان log.sections): تازه‌ترین اول، shipped ِ همان بخش
  assert.deepEqual(scan(AFTER).sections.map((s) => [s.line, s.shipped]), [['3.2', {}], ['3.2', { preview: 61, beta: 1896 }]]);
});

test('the real changelog has the right shape', () => {
  const real = scan(readFileSync(new URL('../../docs/WHATS-NEW-fa.md', import.meta.url), 'utf8'));
  assert.deepEqual(real.problems, []);
  assert.ok(real.entries.length > 10);
});
