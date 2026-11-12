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

test('خودِ نگهبان: نامِ «3*» را می‌گیرد', () => {
  assert.ok(BAD.test('S01 — اسکن، 3*، نوشا'));
  assert.ok(!BAD.test('S01 — اسکن، ۳ ضرب، نوشا'));
});
