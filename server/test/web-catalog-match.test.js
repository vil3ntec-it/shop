'use strict';
/**
 * POS-04 — جست‌وجوی نامِ کالا در کادرِ صندوقِ نسخهٔ وبِ دکان (`license/catalog-match.js`)، بی مرورگر و بی پایگاه.
 * (رفتارِ صفحه در کرومیومِ واقعی: `test/web-pos-name.mjs`.)
 *
 *   ⛔ همان `core/text/CatalogMatch.kt` ِ صندوقِ کامپیوتر: بردارِ مشترکِ `core/test-vectors/catalog-match-v1.json`
 *      (`CatalogMatchVectorsTest` ِ Kotlin همان را می‌خواند)؛ یک‌شکل کردن فقط از `license/text-core.js` (= `TextNormalizer.kt`).
 *   ⛔ تصمیمِ کادر: خالی یا بارکدِ کامل ⇒ راهِ صفحه؛ پیشوند فقط با تنظیمِ خودش (و «۳*کد» با بارکد همان cart-query.js).
 *   ⛔ متن: کلیدهای واژه‌نامهٔ صفحه با انگلیسی و پشتو؛ بارکننده `shop-sync.js`، سرویس‌ورکر v72، index.html ِ یخ‌زده دست‌نخورده.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const read = (f) => fs.readFileSync(path.join(ROOT, f), 'utf8');
const C = require('../../license/catalog-match.js');
const T = require('../../license/text-core.js');
const V = JSON.parse(read('core/test-vectors/catalog-match-v1.json'));
const src = read('license/catalog-match.js');

test('جست‌وجو: همان بردارِ مشترکِ برنامه برای هر پرس‌وجو (حرفِ عربی، نیم‌فاصله، اعراب، رقمِ فارسی، غلطِ تایپی، مترادف)', () => {
  assert.ok(V.search.length >= 50, `موردها: ${V.search.length}`);
  const ix = Object.fromEntries(Object.entries(V.catalogs).map(([k, items]) => [k, C.index(items)]));
  for (const r of V.search) {
    assert.deepEqual(C.search(ix[r.catalog], r.query, r.synonyms, r.fuzzyBelow), r.out, JSON.stringify(r));
  }
});

test('فاصلهٔ ویرایشی: همان بردار، و همان `editDistance` ِ هستهٔ متن (همان `localDistanceIsTheSameAsTheNormalizerOne`)', () => {
  assert.ok(V.distance.length >= 100);
  for (const d of V.distance) assert.equal(C.distance(d.a, d.b, d.max), d.out, JSON.stringify(d));
  const words = ['', 'روغن', 'روغنن', 'رغن', 'کچالو', 'کچالوو', 'برنج', 'بنج', 'abc', 'abd', 'kitten', 'sitting'];
  for (const a of words) for (const b of words) for (let max = 0; max <= 2; max++) {
    assert.equal(C.distance(a, b, max), T.editDistance(a, b, max), `${a}/${b}/${max}`);
  }
});

test('بردار همان انتظارهای `CatalogMatchTest` ِ برنامه را دارد (کاتالوگِ test)', () => {
  const kt = read('core/src/commonTest/kotlin/ir/vil3ntec/tohid/core/text/CatalogMatchTest.kt');
  const rows = [...kt.matchAll(/assertEquals\((listOf\(([^)]*)\)|emptyList\(\)), CatalogMatch\.search\(index, "([^"]*)"(?:, fuzzyBelow = (\d+))?\)\)/g)];
  assert.ok(rows.length >= 12, `موردهای CatalogMatchTest: ${rows.length}`);
  for (const [, , ids, query, below] of rows) {
    const want = ids ? [...ids.matchAll(/"([^"]*)"/g)].map((m) => m[1]) : [];
    const fb = below === undefined ? undefined : Number(below);
    const c = V.search.find((r) => r.catalog === 'test' && r.query === query && r.fuzzyBelow === fb && !r.synonyms);
    assert.ok(c, `در بردار نیست: ${query} ${below || ''}`);
    assert.deepEqual(c.out, want, query);
  }
});

test('۲۰ هزار کالا: نمایه یک بار، هر جست‌وجو در بودجه (POS-42)', () => {
  const items = Array.from({ length: 20000 }, (_, i) => ({ id: `p${i}`, name: `کالای شماره ${i} بسته ${i % 97}`, barcodes: [String(6260000000000 + i)] }));
  const ix = C.index(items);
  C.search(ix, 'کالاي 1234');
  const t0 = process.hrtime.bigint();
  const hits = C.search(ix, 'کالاي شماره 1234');
  const typo = C.search(ix, 'کالایی شمارة 19999');
  const ms = Number(process.hrtime.bigint() - t0) / 1e6;
  assert.ok(hits.includes('p1234'));
  assert.ok(typo.includes('p19999'));
  assert.ok(ms < 3000, `دو جست‌وجو ${ms}ms`);
});

test('تصمیمِ کادر: خالی و بارکدِ کامل ⇒ صفحه؛ نام ⇒ «هر اسکن»؛ پیشوند فقط با تنظیمِ خودش', () => {
  const codes = new Set(['62611', '12x34']);
  const isCode = (t) => codes.has(String(t).trim());
  assert.equal(C.query('', isCode), null);
  assert.equal(C.query('   ', isCode), null);
  assert.equal(C.query(null, isCode), null);
  assert.equal(C.query('62611', isCode), null, 'بارکدِ کامل همیشه راهِ صفحه');
  assert.equal(C.query(' 12x34 ', isCode), null);
  assert.deepEqual(C.query('  برنج ', isCode), { count: 1, term: 'برنج' }, 'بی «هر اسکن» ِ صفحه ⇒ ۱');
  assert.deepEqual(C.query('3*برنج', isCode), { count: 1, term: '3*برنج' }, 'پیشوندِ خاموش ⇒ همان متن');
  const prev = { ls: globalThis.localStorage, cq: globalThis.TohidCartQuery };
  try {
    globalThis.localStorage = { getItem: (k) => (k === 'tohid-pos-qty-prefix-v1' ? '1' : null) };
    globalThis.TohidCartQuery = require('../../license/cart-query.js');
    assert.deepEqual(C.query('۳*برنج', isCode), { count: 3, term: 'برنج' });
    assert.deepEqual(C.query('0.5×شکر', isCode), { count: 0.5, term: 'شکر' });
    assert.equal(C.query('3*62611', isCode), null, '«۳*کد» با بارکد ⇒ cart-query.js');
    assert.equal(C.query('3*', isCode), null, '«۳*» ِ تنها ⇒ cart-query.js');
    assert.equal(C.query('0*برنج', isCode), null, 'شمارِ صفر ⇒ پیامِ cart-query.js');
    assert.equal(C.query('12x34', isCode), null, 'متنی که خودش بارکد است بر پیشوند پیش است');
    assert.deepEqual(C.query('برنج', isCode), { count: 1, term: 'برنج' });
  } finally {
    globalThis.localStorage = prev.ls;
    globalThis.TohidCartQuery = prev.cq;
  }
});

test('کلیدِ تنها در جست‌وجو همان `String.trim` ِ JVM است (U+001C تا U+001F هم، نیم‌فاصله و BOM نه)', () => {
  assert.equal(C.ktTrim('\u001C a \u001F'), 'a');
  assert.equal(C.ktTrim('  a　'), 'a');
  assert.equal(C.ktTrim('‌a﻿'), '‌a﻿');
  assert.deepEqual(C.search(C.index([{ id: 'x', name: 'a' }]), '\u001Ca\u001D'), ['x']);
});

test('متن: کلیدهای واژه‌نامهٔ صفحه، با انگلیسی و پشتو، و جای‌خالی همان', () => {
  const fa = JSON.parse(read('license/i18n/web.fa.json'));
  const en = JSON.parse(read('license/i18n/web.en.json'));
  const ps = JSON.parse(read('license/i18n/web.ps.json'));
  const keys = Object.values(C.TEXT);
  assert.equal(keys.length, 7);
  for (const k of keys) {
    assert.ok(fa.includes(k), `web.fa.json: ${k}`);
    assert.ok(en[k], `en: ${k}`);
    assert.ok(ps[k], `ps: ${k}`);
  }
  assert.equal(C.TEXT.placeholderOff, 'یا بارکد را دستی وارد کنید', 'خاموش ⇒ همان placeholder ِ index.html');
  assert.ok(read('index.html').includes(`placeholder="${C.TEXT.placeholderOff}"`));
});

test('پیش‌فرض خاموش: کلیدِ همین مرورگر فقط با «۱» روشن است و پرونده بی مرورگر هیچ نصب نمی‌کند', () => {
  assert.equal(C.PREF, 'tohid-pos-name-search-v1');
  assert.match(src, /getItem\(k\) === '1'/);
  assert.doesNotThrow(() => C.install(), 'در Node (بی document) کاری نمی‌کند');
});

test('بارکننده و سرویس‌ورکر v72 یا تازه‌تر؛ index.html ِ یخ‌زده دست‌نخورده؛ بی innerHTML/صفتِ style/eval', () => {
  assert.match(read('license/shop-sync.js'), /src: 'license\/catalog-match\.js\?v=1'/);
  const sw = read('sw.js');
  assert.ok(sw.includes("'./license/catalog-match.js'"), 'نصبِ آفلاین هم پرونده را دارد');
  //  همان نسخه‌ای که این پرونده را آورد یا تازه‌تر (دسته‌های بعدی نسخه را بالا می‌برند؛ پایین آمدن یعنی کشِ کهنه)
  const ver = Number((sw.match(/'tohid-shop-v(\d+)'/) || [])[1]);
  assert.ok(ver >= 72, `نسخهٔ سرویس‌ورکر ${ver}`);
  assert.ok(!read('index.html').includes('catalog-match'), 'index.html یخ‌زده است: بارکننده shop-sync.js');
  assert.ok(!/\bfunction (searchKey|compareKey|typoBudget)\b/.test(src), 'یک‌شکل کردن فقط از text-core.js، نه رونوشت');
  for (const [bad, re] of [
    ['eval', /\beval\s*\(/], ['new Function', /new\s+Function\s*\(/], ['رویدادِ درون‌خطی', /\son[a-z]+\s*=\s*["']/i],
    ['setAttribute(on…)', /setAttribute\(\s*['"]on/i], ['javascript:', /javascript:/i],
    ['صفتِ style', /setAttribute\(\s*['"]style|\sstyle\s*=\s*["']/], ['innerHTML', /\.(?:inner|outer)HTML\s*=|insertAdjacentHTML/],
  ]) assert.ok(!re.test(src), bad);
});
