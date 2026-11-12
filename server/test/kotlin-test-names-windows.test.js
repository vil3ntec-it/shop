'use strict';
/**
 *  نامِ آزمونِ Kotlin (تابعِ میانِ `…`) روی ویندوز نامِ پرونده هم می‌شود: کامپایلر برای لامبدای درونِ آزمون کلاسی به همان نام
 *  می‌سازد (`Test$<نام>$1.class`). ویندوز `* ? < > | : " \ /` را در نامِ پرونده نمی‌پذیرد ⇒ «Internal compiler error» در
 *  `:compileTestKotlin` ِ کارِ «ذخیره روی دیسکِ ویندوز» (`ScenarioS01DesktopTest` با «3*»، CI ِ #1041). روی لینوکس سبز می‌ماند،
 *  پس همین‌جا می‌سنجیم: هر پوشهٔ آزمونی که در ساختِ desktop یا core کامپایل می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const DIRS = ['desktop/src/test', 'android-native/app/src/test', 'core/src/commonTest', 'core/src/jvmTest', 'customer-android/app/src/test'];
const BAD = /[*?<>|:"\\/]/;

function walk(dir, out) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}

test('نامِ آزمون‌های Kotlin نویسهٔ ممنوعِ نامِ پروندهٔ ویندوز ندارد (* ? < > | : " \\ /)', () => {
  const bad = [];
  for (const d of DIRS) {
    for (const f of walk(path.join(ROOT, d), [])) {
      const src = fs.readFileSync(f, 'utf8');
      for (const m of src.matchAll(/fun\s+`([^`]+)`/g)) {
        if (BAD.test(m[1])) bad.push(`${path.relative(ROOT, f)}: ${m[1].slice(0, 80)}`);
      }
    }
  }
  assert.deepEqual(bad, [], `نامِ آزمون با نویسهٔ ممنوعِ ویندوز:\n${bad.join('\n')}`);
});

//  آزمون‌هایی که برای Kotlin/Native (آیفون) هم کامپایل می‌شوند سخت‌گیرترند: «Name contains illegal characters» برای
//  ( ) [ ] < > : ; / . \ — کارِ «کامپایلِ core برای iOS» در CI ِ #1041 با پرانتز در نامِ `OnlineHoldsTest` افتاد
const NATIVE_DIRS = ['core/src/commonTest', 'core/src/iosTest', 'ios/shared/src/commonTest', 'ios/shared/src/iosTest'];
const NATIVE_BAD = /[()\[\]<>:;/.\\*?|"]/;

test('نامِ آزمون‌های Kotlin ِ مشترک با آیفون نویسهٔ ممنوعِ Kotlin/Native ندارد (( ) [ ] < > : ; / . \\)', () => {
  const bad = [];
  for (const d of NATIVE_DIRS) {
    for (const f of walk(path.join(ROOT, d), [])) {
      const src = fs.readFileSync(f, 'utf8');
      for (const m of src.matchAll(/fun\s+`([^`]+)`/g)) {
        if (NATIVE_BAD.test(m[1])) bad.push(`${path.relative(ROOT, f)}: ${m[1].slice(0, 80)}`);
      }
    }
  }
  assert.deepEqual(bad, [], `نامِ آزمون با نویسهٔ ممنوعِ Kotlin/Native:\n${bad.join('\n')}`);
});

test('خودِ نگهبان: نامِ «3*» را می‌گیرد', () => {
  assert.ok(BAD.test('S01 — اسکن، 3*، نوشا'));
  assert.ok(!BAD.test('S01 — اسکن، ۳ ضرب، نوشا'));
  assert.ok(NATIVE_BAD.test('نمای کهنه (بی اینترنت)') && !NATIVE_BAD.test('نمای کهنه، بی اینترنت ⇒ هیچ؛ همان'));
});
