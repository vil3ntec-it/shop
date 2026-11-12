'use strict';
/**
 * WEB-16 — سدهای `lib/store-webp.js` (بازبینیِ ۵۴ب)، بی پایگاه و بی libvips: `sharp-lib` جانشین دارد تا هر سد جدا و تند
 * سنجیده شود. راهِ واقعی (HTTP و `sharp` ِ واقعی) در `store-webp.test.js`.
 *
 *   ۱) ⛔ «سبک‌تر نشد» ِ یادشده در کش هرگز بافرِ خالی برنمی‌گرداند (پیش‌تر درخواستِ دوم ۲۰۰ ِ بی بدنه با image/avif|webp بود).
 *   ۲) ⛔ پیش از رمزگذاری `resize` تا ضلعِ همان اندازه (thumb ۴۰۰، display ۱۲۰۰) با `withoutEnlargement`.
 *   ۳) ⛔ AVIF فقط تا `AVIF_MAX_PIXELS` (از `metadata()` ِ ارزان)؛ بیشتر ⇒ null (مسیر WebP/JPEG می‌دهد) و یاد می‌ماند.
 *   ۴) ⛔ سقفِ کار: AVIF یکی، کل `MAX_JOBS`؛ سنجش و افزودن بی هیچ await در میان.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

//  ── جانشینِ `sharp` پیش از بار شدنِ store-webp ──────────────────────────────
const calls = { encode: 0, meta: 0, active: { webp: 0, avif: 0 }, peak: { webp: 0, avif: 0, all: 0 }, resize: [] };
const knob = { dims: { width: 800, height: 600 }, out: 10, delay: 5 };
const wait = () => new Promise((r) => setTimeout(r, knob.delay));
function fakeSharp() {
  let fmt = null;
  const chain = {
    rotate: () => chain,
    resize: (o) => { calls.resize.push(o); return chain; },
    webp: () => { fmt = 'webp'; return chain; },
    avif: () => { fmt = 'avif'; return chain; },
    async metadata() { calls.meta++; await wait(); return { ...knob.dims }; },
    async toBuffer() {
      calls.encode++; calls.active[fmt]++;
      calls.peak[fmt] = Math.max(calls.peak[fmt], calls.active[fmt]);
      calls.peak.all = Math.max(calls.peak.all, calls.active.webp + calls.active.avif);
      try { await wait(); return Buffer.alloc(knob.out, 1); } finally { calls.active[fmt]--; }
    },
  };
  return chain;
}
const sharpLib = path.resolve(__dirname, '../src/lib/sharp-lib.js');
require.cache[sharpLib] = { id: sharpLib, filename: sharpLib, loaded: true, exports: { lib: () => fakeSharp } };

const config = require('../src/config');
const W = require('../src/lib/store-webp');

const SRC = Buffer.alloc(100, 7);   //  «اصل» ِ ۱۰۰ بایتی
test.beforeEach(() => {
  W._reset();
  Object.assign(calls, { encode: 0, meta: 0, active: { webp: 0, avif: 0 }, peak: { webp: 0, avif: 0, all: 0 }, resize: [] });
  Object.assign(knob, { dims: { width: 800, height: 600 }, out: 10, delay: 5 });
  config.storefront.webp = true; config.storefront.avif = true;
});
test.after(() => { config.storefront.webp = false; config.storefront.avif = false; });

test('۱) «سبک‌تر نشد» یاد می‌ماند و درخواستِ دوم هم null است، هرگز بافرِ خالی (WebP و AVIF)', async () => {
  knob.out = 150;   //  از اصل سنگین‌تر
  for (const fmt of ['webp', 'avif']) {
    assert.equal(await W.encodedOf(SRC, `h-${fmt}`, 'display', fmt), null, `${fmt}: بارِ نخست`);
    const before = calls.encode;
    const again = await W.encodedOf(SRC, `h-${fmt}`, 'display', fmt);
    assert.equal(again, null, `${fmt}: بارِ دوم بافرِ ${again && again.length} بایتی داد`);
    assert.equal(calls.encode, before, `${fmt}: بارِ دوم از یاد، بی رمزگذاری`);
  }
});

test('۲) پیش از رمزگذاری resize تا ضلعِ همان اندازه، بی بزرگ کردن', async () => {
  assert.ok(await W.encodedOf(SRC, 'h1', 'display', 'webp'));
  assert.ok(await W.encodedOf(SRC, 'h2', 'thumb', 'avif'));
  assert.deepEqual(calls.resize, [
    { width: 1200, height: 1200, fit: 'inside', withoutEnlargement: true },
    { width: 400, height: 400, fit: 'inside', withoutEnlargement: true },
  ]);
});

test('۳) AVIF بالای سقفِ پیکسل ⇒ null بی رمزگذاری و یاد می‌ماند؛ WebP ِ همان عکس ساخته می‌شود', async () => {
  knob.dims = { width: 2500, height: 2000 };   //  ۵ مگاپیکسل
  assert.ok(knob.dims.width * knob.dims.height > W.AVIF_MAX_PIXELS);
  assert.equal(await W.encodedOf(SRC, 'big', 'display', 'avif'), null);
  assert.equal(calls.encode, 0, 'AVIF رمزگذاری نشد');
  assert.equal(await W.encodedOf(SRC, 'big', 'display', 'avif'), null);
  assert.equal(calls.meta, 1, 'بارِ دوم حتی metadata هم نه');
  assert.ok(await W.encodedOf(SRC, 'big', 'display', 'webp'), 'WebP همان عکس');
  //  درست در سقف ⇒ AVIF
  knob.dims = { width: 2000, height: 2000 };
  assert.ok(await W.encodedOf(SRC, 'edge', 'display', 'avif'));
});

test('۴) سقفِ کار: AVIF یکی و کل دو — شش درخواستِ هم‌زمان', async () => {
  const run = (fmt, n) => Array.from({ length: n }, (_, i) => W.encodedOf(SRC, `${fmt}-${i}`, 'display', fmt));
  const out = await Promise.all([...run('avif', 3), ...run('webp', 3)]);
  assert.equal(calls.peak.avif, W.AVIF_JOBS, `بیشینهٔ AVIF ِ هم‌زمان ${calls.peak.avif}`);
  assert.ok(calls.peak.all <= W.MAX_JOBS, `بیشینهٔ کل ${calls.peak.all}`);
  assert.equal(out.filter(Boolean).length, W.MAX_JOBS, 'بقیه null (همان JPEG، نه صف)');
  //  پس از کار شمارنده‌ها صفرند: درخواستِ تازه می‌سازد
  assert.ok(await W.encodedOf(SRC, 'later', 'display', 'avif'));
});

test('۴) سدِ هم‌زمانیِ WebP: شش درخواستِ هم‌زمان ⇒ حداکثر MAX_JOBS رمزگذاری', async () => {
  config.storefront.avif = false;
  await Promise.all(Array.from({ length: 6 }, (_, i) => W.encodedOf(SRC, `w-${i}`, 'thumb', 'webp')));
  assert.equal(calls.peak.webp, W.MAX_JOBS);
});

test('قالب‌های پاسخ به ترتیب: AVIF ⇐ WebP، فقط روشن‌ها و پذیرفته‌ها', () => {
  assert.deepEqual(W.formats('image/avif,image/webp,*/*'), ['avif', 'webp']);
  assert.deepEqual(W.formats('image/webp,*/*'), ['webp']);
  assert.deepEqual(W.formats('image/avif'), ['avif']);
  assert.deepEqual(W.formats('text/html'), []);
  config.storefront.avif = false;
  assert.deepEqual(W.formats('image/avif,image/webp'), ['webp']);
  config.storefront.webp = false; config.storefront.avif = true;
  assert.deepEqual(W.formats('image/avif,image/webp'), ['avif']);
});
