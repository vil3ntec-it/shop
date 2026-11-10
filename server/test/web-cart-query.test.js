'use strict';
/**
 * POS-03 — پیشوندِ شمار «۳*» در کادرِ بارکدِ صندوقِ نسخهٔ وبِ دکان (`license/cart-query.js`)، بی مرورگر و بی پایگاه.
 * (رفتارِ صفحه در کرومیومِ واقعی: `test/web-pos-qty.mjs`.)
 *
 *   ⛔ همان `data/CartQuery.kt` ِ صندوقِ کامپیوتر و گوشی: بردارِ مشترکِ `license/test-vectors/cart-query-v1.json`
 *      (`CartQueryVectorsTest` ِ Kotlin همان را می‌خواند)، با `\s`، `.` و `$` ِ java.util.regex (نه JS) — همهٔ BMP.
 *   ⛔ تصمیمِ کادر: بی پیشوند یا متنی که خودش بارکدِ کالاست ⇒ همان راهِ صفحه؛ شمارِ صفر/نادرست ⇒ هیچ افزودن.
 *   ⛔ متن: چهار کلیدِ واژه‌نامهٔ صفحه با انگلیسی و پشتو؛ بارکننده `shop-sync.js`، سرویس‌ورکر، index.html ِ یخ‌زده دست‌نخورده.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const read = (f) => fs.readFileSync(path.join(ROOT, f), 'utf8');
const X = require('../../license/cart-query.js');
const V = JSON.parse(read('license/test-vectors/cart-query-v1.json'));
const src = read('license/cart-query.js');

test('پیشوند: همان بردارِ مشترکِ برنامه (رقمِ فارسی/عربی، «٫ ٬»، فاصله و پایانِ خطِ JVM)', () => {
  assert.ok(V.parse.length >= 40, `موردها: ${V.parse.length}`);
  for (const c of V.parse) assert.deepEqual(X.parse(c.in), { count: c.count, term: c.term }, JSON.stringify(c.in));
});

test('بردار همان موردهای `CartQueryTest` ِ برنامه را دارد', () => {
  const kt = read('android-native/app/src/test/java/ir/vil3ntec/tohid/data/CartQueryTest.kt');
  const pairs = [...kt.matchAll(/CartQuery\.Parsed\(([0-9.]+), "([^"]*)"\), CartQuery\.parse\("([^"]*)"\)/g)];
  assert.ok(pairs.length >= 8, `موردهای CartQueryTest: ${pairs.length}`);
  for (const [, count, term, input] of pairs) {
    const c = V.parse.find((x) => x.in === input);
    assert.ok(c, `در بردار نیست: ${input}`);
    assert.deepEqual({ count: c.count, term: c.term }, { count: Number(count), term }, input);
  }
});

test('یک‌شکل کردنِ رقم همان `latinDigits` ِ برنامه است — همهٔ BMP، نویسه به نویسه', () => {
  const kt = (c) => {
    const n = c.charCodeAt(0);
    if (n >= 0x06F0 && n <= 0x06F9) return String.fromCharCode(48 + n - 0x06F0);
    if (n >= 0x0660 && n <= 0x0669) return String.fromCharCode(48 + n - 0x0660);
    return n === 0x066B || n === 0x066C ? '.' : c;
  };
  const miss = [];
  for (let n = 0; n < 0x10000; n++) {
    if (n >= 0xD800 && n <= 0xDFFF) continue;
    const c = String.fromCharCode(n);
    if (X.latinDigits(c) !== kt(c)) miss.push(n.toString(16));
  }
  assert.deepEqual(miss, []);
});

test('فاصله و پایانِ خط همان java.util.regex است، نه JS — همهٔ BMP', () => {
  const JAVA_WS = new Set([0x20, 0x09, 0x0A, 0x0B, 0x0C, 0x0D]);
  const JAVA_EOL = new Set([0x0A, 0x0D, 0x85, 0x2028, 0x2029]);
  const miss = [];
  for (let n = 0; n < 0x10000; n++) {
    if (n >= 0xD800 && n <= 0xDFFF) continue;
    const c = String.fromCharCode(n);
    if (/[0-9*×xX.۰-۹٠-٩٫٬]/.test(c)) continue;
    //  سرِ متن: فقط `\s` ِ جاوا پیش از شمار پذیرفته است
    const lead = X.parse(`${c}3*a`);
    if ((lead.count === 3) !== JAVA_WS.has(n)) miss.push(`lead ${n.toString(16)}`);
    //  میانه: «.» هر نویسه جز پایانِ خط؛ پایانِ خطِ میانه ⇒ بی جور
    const mid = X.parse(`3*a${c}b`);
    if ((mid.count === 3) !== !JAVA_EOL.has(n)) miss.push(`mid ${n.toString(16)}`);
    //  ته: پایانِ خطِ پایانی پذیرفته و در بخشِ پس از ستاره می‌ماند
    const tail = X.parse(`3*a${c}`);
    if (tail.count !== 3 || tail.term !== `a${c}`) miss.push(`tail ${n.toString(16)}`);
  }
  assert.deepEqual(miss, []);
});

test('تصمیمِ کادر: بی پیشوند و بارکدِ کامل ⇒ صفحه؛ «۳*کد» ⇒ افزودن؛ «۳*» ⇒ اسکنِ بعدی؛ صفر ⇒ هیچ', () => {
  const codes = new Set(['111', '12x34']);
  const isBarcode = (t) => codes.has(t.trim());
  const page = { kind: 'page' };
  assert.deepEqual(X.decide('', isBarcode), page);
  assert.deepEqual(X.decide('   ', isBarcode), page);
  assert.deepEqual(X.decide('111', isBarcode), page, 'بی پیشوند همان راهِ امروزِ صفحه');
  assert.deepEqual(X.decide('برنج', isBarcode), page);
  assert.deepEqual(X.decide('12x34', isBarcode), page, 'متنی که خودش بارکدِ کالاست بی پیشوند می‌ماند');
  assert.deepEqual(X.decide('12x35', isBarcode), { kind: 'add', count: 12, code: '35' }, 'بارکدِ ناشناس ⇒ همان پیشوند');
  assert.deepEqual(X.decide('۳*111', isBarcode), { kind: 'add', count: 3, code: '111' });
  assert.deepEqual(X.decide(' 2 × 111 ', isBarcode), { kind: 'add', count: 2, code: '111' }, 'کد همان trim ِ صفحه');
  assert.deepEqual(X.decide('۰٫۲۵*222', isBarcode), { kind: 'add', count: 0.25, code: '222' });
  assert.deepEqual(X.decide('3*', isBarcode), { kind: 'wait', count: 3 });
  assert.deepEqual(X.decide('۵× ', isBarcode), { kind: 'wait', count: 5 });
  assert.deepEqual(X.decide('0*111', isBarcode), { kind: 'bad', wait: false });
  assert.deepEqual(X.decide('0*', isBarcode), { kind: 'bad', wait: true });
  assert.deepEqual(X.decide(`${'9'.repeat(400)}*111`, isBarcode), { kind: 'bad', wait: false }, 'بی‌نهایت شمار نیست');
  assert.deepEqual(X.decide(null, isBarcode), page);
  assert.deepEqual(X.decide('3*111', null), { kind: 'add', count: 3, code: '111' });
});

test('متن: چهار کلیدِ واژه‌نامهٔ صفحه، با انگلیسی و پشتو، و جای‌خالی همان', () => {
  const fa = JSON.parse(read('license/i18n/web.fa.json'));
  const en = JSON.parse(read('license/i18n/web.en.json'));
  const ps = JSON.parse(read('license/i18n/web.ps.json'));
  const keys = Object.values(X.TEXT);
  assert.equal(keys.length, 4);
  for (const k of keys) {
    assert.ok(fa.includes(k), `web.fa.json: ${k}`);
    assert.ok(en[k] && !/[؀-ۿ]/.test(en[k]), `en: ${k}`);
    assert.ok(ps[k], `ps: ${k}`);
  }
  assert.match(X.TEXT.next, /\{0\}/);
  assert.match(en[X.TEXT.next], /\{0\}/);
  assert.match(ps[X.TEXT.next], /\{0\}/);
});

test('پیش‌فرض خاموش: کلیدِ همین مرورگر فقط با «۱» روشن است و پرونده بی مرورگر هیچ نصب نمی‌کند', () => {
  assert.equal(X.PREF, 'tohid-pos-qty-prefix-v1');
  assert.match(src, /getItem\(PREF\) === '1'/);
  assert.doesNotThrow(() => X.install(), 'در Node (بی document) کاری نمی‌کند');
});

test('بارکننده و سرویس‌ورکر؛ index.html ِ یخ‌زده دست‌نخورده؛ اسکن فقط از دستگیرهٔ صفحه؛ بی innerHTML/صفتِ style/eval', () => {
  assert.match(read('license/shop-sync.js'), /src: 'license\/cart-query\.js\?v=1'/);
  assert.ok(read('sw.js').includes("'./license/cart-query.js'"), 'نصبِ آفلاین هم پرونده را دارد');
  assert.ok(!read('index.html').includes('cart-query'), 'index.html یخ‌زده است: بارکننده shop-sync.js');
  assert.ok(!/addToCart|cart\.push|setCartQty/.test(src), 'راهِ دومِ افزودن نه: فقط `handleBarcodeScanned` ِ صفحه');
  for (const [bad, re] of [
    ['eval', /\beval\s*\(/], ['new Function', /new\s+Function\s*\(/], ['رویدادِ درون‌خطی', /\son[a-z]+\s*=\s*["']/i],
    ['setAttribute(on…)', /setAttribute\(\s*['"]on/i], ['javascript:', /javascript:/i],
    ['صفتِ style', /setAttribute\(\s*['"]style|\sstyle\s*=\s*["']/], ['innerHTML', /\.(?:inner|outer)HTML\s*=|insertAdjacentHTML/],
    ['پرچمِ d ِ regex (مرورگرِ کهنه)', /hasIndices|\/[a-z]*d[a-z]*\)?\.exec/],
  ]) assert.ok(!re.test(src), bad);
});
