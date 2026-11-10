'use strict';
/**
 * MKT-06 — «چرخهٔ مصرف (روز)» در ویرایشِ کالای نسخهٔ وبِ دکان (`license/product-cycle.js`)، بی مرورگر و بی پایگاه.
 * (وبِ دکان به تصمیمِ صاحب برداشته شد، ۱۴۰۵/۰۸/۲۰: واژه‌نامهٔ صفحه، بارکننده، سرویس‌ورکر و index.html رفتند؛ هسته و بردارش همین‌جاست.)
 *
 *   ⛔ کادر همان `data/ProductCycle.kt` ِ برنامه است: بردارِ مشترکِ `license/test-vectors/product-cycle-v1.json`
 *      (`ProductCycleVectorsTest` ِ Kotlin همان را می‌خواند) و همان مجموعهٔ فاصلهٔ `trim` ِ Kotlin روی JVM.
 *   ⛔ دفتر: کالای بی چرخه بایت‌به‌بایت همان (هیچ کلیدِ تازه، حتی null)؛ پاک کردن = برداشتنِ کلید (سایهٔ همگام‌سازی
 *      آن را null ِ صریح می‌فرستد)؛ کادرِ خراب هیچ چیزی را عوض نمی‌کند.
 *   ⛔ متن: سه نوشتهٔ کادر همان فارسیِ `StrProducts` ِ برنامه؛ بی innerHTML/style/eval.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const read = (f) => fs.readFileSync(path.join(ROOT, f), 'utf8');
const X = require('../../license/product-cycle.js');
const V = JSON.parse(read('license/test-vectors/product-cycle-v1.json'));
const src = read('license/product-cycle.js');

/** `Character.isWhitespace(c) || Character.isSpaceChar(c)` روی JDK 21 (همان `Char.isWhitespace` ِ Kotlin روی JVM) */
const JVM_WS = [[0x09, 0x0D], [0x1C, 0x20], [0xA0, 0xA0], [0x1680, 0x1680], [0x2000, 0x200A], [0x2028, 0x2029], [0x202F, 0x202F], [0x205F, 0x205F], [0x3000, 0x3000]];
const jvmWs = (c) => JVM_WS.some(([a, b]) => c >= a && c <= b);
const want = (out) => (out === 'clear' ? { kind: 'clear' } : out === 'bad' ? { kind: 'bad' } : { kind: 'days', days: out });

test('کادر: همان بردارِ مشترکِ برنامه (رقمِ فارسی/عربی، ۱ تا ۳۶۵، بقیه خراب)', () => {
  assert.ok(V.parse.length >= 30, `موردها: ${V.parse.length}`);
  for (const c of V.parse) assert.deepEqual(X.parse(c.in), want(c.out), JSON.stringify(c.in));
  for (const c of V.text) assert.equal(X.text(c.in), c.out, JSON.stringify(c.in));
  assert.equal(X.MAX, V.max);
});

test('سقف همان برنامه و ویترین است (`ProductCycle.MAX`، `CYCLE_MAX` ِ storefront.js)', () => {
  const kt = read('android-native/app/src/main/java/ir/vil3ntec/tohid/data/ProductCycle.kt');
  assert.equal(Number((/const val MAX = (\d+)/.exec(kt) || [])[1]), X.MAX);
  const sf = read('server/src/lib/storefront.js');
  assert.equal(Number((/const CYCLE_MAX = (\d+);/.exec(sf) || [])[1]), X.MAX);
});

test('فاصلهٔ دو سر همان trim ِ Kotlin است، نه trim ِ JS (U+001F بله، U+FEFF نه) — همهٔ BMP', () => {
  const miss = [];
  for (let c = 0; c < 0x10000; c++) {
    if (c >= 0xD800 && c <= 0xDFFF) continue;
    const ch = String.fromCharCode(c);
    if (/[0-9۰-۹٠-٩]/.test(ch)) continue;
    const got = X.parse(`${ch}30${ch}`);
    const exp = jvmWs(c) ? { kind: 'days', days: 30 } : { kind: 'bad' };
    if (got.kind !== exp.kind) miss.push(c.toString(16));
  }
  assert.deepEqual(miss, []);
});

test('متنِ کادر از دفتر: فقط عددِ صحیحِ ۱ تا ۳۶۵؛ مقدارِ نادرستِ دفتر خالی', () => {
  for (const bad of [undefined, null, 0, 366, 7.5, '14', -1, NaN, Infinity, {}, [30], true]) assert.equal(X.text(bad), '', String(bad));
  assert.equal(X.text(14), '14');
});

test('دفتر: بی‌تغییر یا خراب ⇒ همان شیء؛ تازه، عوض‌شده و پاک‌شده با «از ⇐ به»', () => {
  const plain = { id: 'p1', name: 'برنج', salePrice: 50 };
  const before = JSON.stringify(plain);
  assert.equal(X.applyTo(plain, ''), null);
  assert.equal(X.applyTo(plain, '   '), null);
  assert.equal(JSON.stringify(plain), before, 'کالای بی چرخه بایت‌به‌بایت همان — هیچ کلیدِ تازه، حتی null');
  for (const bad of ['0', '366', '۷٫۵', 'abc']) {
    assert.equal(X.applyTo(plain, bad), null, bad);
    assert.equal(JSON.stringify(plain), before, `کادرِ خراب (${bad}) چیزی را عوض نمی‌کند`);
  }

  assert.deepEqual(X.applyTo(plain, '۳۰'), { from: null, to: 30 });
  assert.equal(plain.consumptionDays, 30);
  assert.equal(X.applyTo(plain, '30'), null, 'همان مقدار ⇒ بی تغییر');
  assert.deepEqual(X.applyTo(plain, '45'), { from: 30, to: 45 });
  assert.equal(X.applyTo(plain, '400'), null);
  assert.equal(plain.consumptionDays, 45, 'کادرِ خراب مقدارِ پیشین را نگه می‌دارد');
  assert.deepEqual(X.applyTo(plain, ''), { from: 45, to: null });
  assert.equal(Object.hasOwn(plain, 'consumptionDays'), false, 'پاک کردن = برداشتنِ کلید (سایه null ِ صریح می‌فرستد)');
  assert.equal(JSON.stringify(plain), before);

  //  مقدارِ نادرستی که از جای دیگر رسیده (کادرش خالی نشان داده شد) با ذخیرهٔ کادرِ خالی برداشته می‌شود — همان `apply` ِ Kotlin
  for (const odd of ['14', 7.5, 400, null]) {
    const p = { id: 'p2', consumptionDays: odd };
    assert.equal(X.text(p.consumptionDays), '');
    assert.deepEqual(X.applyTo(p, ''), { from: null, to: null }, String(odd));
    assert.equal(Object.hasOwn(p, 'consumptionDays'), false);
  }
  assert.equal(X.applyTo(null, '30'), null);
  assert.equal(X.applyTo({ id: 'p3' }, 30), null, 'فقط متنِ کادر');
});

test('سابقهٔ عملیات: یک تکهٔ خوانا برای ویرایشِ کالا', () => {
  assert.equal(X.changeText({ from: null, to: 30 }), 'چرخهٔ مصرف — ⇐ 30 روز');
  assert.equal(X.changeText({ from: 30, to: 45 }), 'چرخهٔ مصرف 30 ⇐ 45 روز');
  assert.equal(X.changeText({ from: 45, to: null }), 'چرخهٔ مصرف 45 ⇐ — روز');
  assert.equal(X.changeText({ from: null, to: null }), '', 'برداشتنِ مقدارِ نادرستِ دیده‌نشده تکهٔ سابقه نمی‌سازد');
  assert.equal(X.changeText(null), '');
});

test('متن: سه برچسبِ هسته همان فارسیِ `StrProducts` ِ برنامه', () => {
  const kt = read('android-native/app/src/main/java/ir/vil3ntec/tohid/core/i18n/StrProducts.kt');
  const tri = (name) => {
    const m = new RegExp(`val ${name}: String get\\(\\) = t\\(\\s*"([^"]+)",\\s*"([^"]+)",\\s*"([^"]+)",?\\s*\\)`).exec(kt);
    assert.ok(m, `StrProducts.${name}`);
    return m.slice(1);
  };
  const pairs = [[X.TEXT.label, tri('cycle')], [X.TEXT.hint, tri('cycleHelper')], [X.TEXT.bad, tri('cycleBad')]];
  for (const [key, [kfa]] of pairs) assert.equal(key, kfa, 'فارسی همان برنامه');
});

test('بی innerHTML/style/eval/رویدادِ درون‌خطی', () => {
  for (const [bad, re] of [
    ['eval', /\beval\s*\(/], ['new Function', /new\s+Function\s*\(/], ['رویدادِ درون‌خطی', /\son[a-z]+\s*=\s*["']/i],
    ['setAttribute(on…)', /setAttribute\(\s*['"]on/i], ['javascript:', /javascript:/i],
    ['صفتِ style', /setAttribute\(\s*['"]style|\sstyle\s*=\s*["']|\.style\./], ['innerHTML', /\.(?:inner|outer)HTML\s*=|insertAdjacentHTML/],
  ]) assert.ok(!re.test(src), bad);
});
