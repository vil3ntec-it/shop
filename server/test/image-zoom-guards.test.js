'use strict';
/**
 * IMG-11 — سدهای `lib/image-zoom.js` (بازبینیِ ۵۴)، بی پایگاه و بی libvips: `sharp-lib` و درهای بانک جانشین دارند تا هر
 * سد جدا و تند سنجیده شود. ساختِ واقعی، مالکیت و سرآیندها در `image-zoom.test.js` (از راهِ HTTP و `sharp` ِ واقعی).
 *
 *   ۱) ⛔ سدِ هم‌زمانی: N درخواستِ هم‌زمان ⇒ حداکثر `MAX_JOBS` ساخت (پیش‌تر سنجش پیش از await و افزودن پس از آن ⇒ همه).
 *   ۲) ⛔ یادِ شکست: zoom ای که در هیچ کیفیتی زیرِ سقفِ بایت نمی‌شود (یا رمزگشایی نمی‌شود) یک بار ساخته می‌شود، نه با هر
 *      درخواستِ عمومی؛ یاد کران‌دار است و مهلت دارد. ابعاد از فرادادهٔ پایگاه پیش از خواندنِ بایت‌های اصل.
 *   ۳) ⛔ پرچمِ خاموش ⇒ `sharp` حتی بار نمی‌شود (اول پرچم، بعد `lib()`).
 *   ۴) هرگز پرتاب (⇒ ۵۰۰ نه): خطای خواندن ⇒ null؛ نوشتنِ هم‌زمان (یکتاییِ `23505`) مثلِ `derivative_exists` ⇒ همان که نشست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const F = require('./image-fixtures');

//  ── جانشینِ `sharp` پیش از بار شدنِ image-zoom ──────────────────────────────
const calls = { lib: 0, encode: 0, active: 0, peak: 0 };
const knob = { mode: 'ok', delay: 5 };
const wait = () => (knob.delay ? new Promise((r) => setTimeout(r, knob.delay)) : new Promise((r) => setImmediate(r)));
function fakeSharp() {
  const chain = {
    rotate: () => chain, resize: () => chain, toColourspace: () => chain, jpeg: () => chain,
    async toBuffer() {
      calls.encode++; calls.active++; calls.peak = Math.max(calls.peak, calls.active);
      try {
        await wait();
        if (knob.mode === 'throw') throw new Error('VipsJpeg: premature end of input file');
        return knob.mode === 'big' ? Buffer.alloc(2 * 1024 * 1024 + 1) : Buffer.from('zoom-ok');
      } finally { calls.active--; }
    },
  };
  return chain;
}
const sharpLib = path.resolve(__dirname, '../src/lib/sharp-lib.js');
require.cache[sharpLib] = { id: sharpLib, filename: sharpLib, loaded: true, exports: { lib: () => { calls.lib++; return fakeSharp; } } };

const db = require('../src/db');
const bank = require('../src/lib/image-bank');
const zoom = require('../src/lib/image-zoom');

//  ── درهای بانک و پایگاه ─────────────────────────────────────────────────────
const ORIGINAL = F.jpeg(3000, 2000);
const state = { flag: true, dims: { width: 3000, height: 2000 }, readFails: false, stored: new Map(), reads: 0, tx: null };
bank.flagOf = async () => { await new Promise((r) => setImmediate(r)); return state.flag; };
bank.readFile = async (id, version, size) => {
  await new Promise((r) => setImmediate(r));
  if (state.readFails) throw new Error('connection terminated');
  if (size === 'zoom') return state.stored.get(`${id}@${version}`) || null;
  state.reads++;
  return { data: ORIGINAL, hash: 'orig', mime: 'image/jpeg' };
};
bank.tx = async (fn) => (state.tx ? state.tx(fn) : undefined);
db.one = async () => state.dims;

const img = (n) => ({ id: `img_${String(n).padStart(26, '0')}`, space: 'shop', owner_shop_id: 'shp_a' });
function reset() {
  Object.assign(calls, { lib: 0, encode: 0, active: 0, peak: 0 });
  Object.assign(knob, { mode: 'ok', delay: 5 });
  Object.assign(state, { flag: true, dims: { width: 3000, height: 2000 }, readFails: false, reads: 0, tx: null });
  state.stored.clear();
  if (zoom._reset) zoom._reset(); else zoom._jobs(0);
}
test.beforeEach(reset);

test('۱) سدِ هم‌زمانی: شش درخواستِ هم‌زمان برای شش عکس ⇒ فقط یک ساخت؛ بقیه همان display (null)، بی صف', async () => {
  const out = await Promise.all(Array.from({ length: 6 }, (_, i) => zoom.ensure('shp_a', img(i + 1), 1)));
  assert.equal(calls.peak, zoom.MAX_JOBS, `بیشینهٔ ساختِ هم‌زمان ${calls.peak}`);
  assert.equal(state.reads, 1, 'فقط یک اصل خوانده شد');
  assert.deepEqual(out, [null, null, null, null, null, null]);
  assert.equal(zoom._jobs(), 0, 'شمارنده پس از کار صفر');
});

test('۲) یادِ شکست: zoom ِ بیش از سقفِ بایت یک بار ساخته می‌شود، نه با هر درخواست؛ نسخهٔ دیگر جداست', async () => {
  knob.mode = 'big';
  assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  assert.equal(calls.encode, 3, 'سه کیفیت آزموده شد');
  assert.equal(state.reads, 1);
  for (let i = 0; i < 3; i++) assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  assert.equal(calls.encode, 3, 'درخواست‌های بعدی هیچ رمزگذاری‌ای نمی‌کنند');
  assert.equal(state.reads, 1, 'و اصل را دوباره نمی‌خوانند');
  //  نسخهٔ تازهٔ همان عکس یادِ جدا دارد
  assert.equal(await zoom.ensure('shp_a', img(1), 2), null);
  assert.equal(calls.encode, 6);
  //  «کدام عکس» نشانیِ zoom ِ شکست‌خورده را وعده نمی‌دهد
  const shown = await zoom.withUrl('shp_a', { image_id: img(1).id, version: 1, urls: { zoom: null } }, '/b');
  assert.equal(shown.urls.zoom, null);
});

test('۲) یادِ شکست: رمزگشاییِ ناموفق هم یاد می‌ماند؛ پس از مهلت دوباره آزموده می‌شود', async () => {
  knob.mode = 'throw';
  assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  assert.equal(calls.encode, 1, 'دومی نساخت');
  const real = Date.now;
  try {
    Date.now = () => real() + zoom.FAIL_TTL_MS + 1000;
    knob.mode = 'ok';
    assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
    assert.equal(calls.encode, 2, 'پس از مهلت دوباره');
  } finally { Date.now = real; }
});

test('۲) یادِ شکست کران‌دار است: بیش از سقف ⇒ کهنه‌ترین کنار می‌رود، تازه‌ترین می‌ماند', async () => {
  knob.mode = 'big'; knob.delay = 0;
  const n = zoom.FAIL_MAX + 5;
  for (let i = 1; i <= n; i++) await zoom.ensure('shp_a', img(i), 1);
  assert.ok(zoom._failed() <= zoom.FAIL_MAX, `اندازهٔ یاد ${zoom._failed()}`);
  const before = calls.encode;
  await zoom.ensure('shp_a', img(n), 1);
  assert.equal(calls.encode, before, 'تازه‌ترین هنوز یاد است');
  await zoom.ensure('shp_a', img(1), 1);
  assert.equal(calls.encode, before + 3, 'کهنه‌ترین کنار رفته بود');
});

test('۲) ابعاد پیش از بایت‌ها: اصلِ کوچک‌تر از display یا ردیفِ بی دسترسی ⇒ null بی خواندنِ اصل و بی ساخت', async () => {
  state.dims = { width: 1000, height: 800 };
  assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  state.dims = null;   //  ردیفی برای این دکان نیست (عکسِ دکانِ دیگر یا مرکزی)
  assert.equal(await zoom.ensure('shp_a', img(2), 1), null);
  assert.equal(state.reads, 0, 'اصل خوانده نشد');
  assert.equal(calls.encode, 0);
});

test('۳) پرچمِ خاموش ⇒ `sharp` بار نمی‌شود (نه در ساخت، نه در «کدام عکس»)', async () => {
  state.flag = false;
  assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  const shown = await zoom.withUrl('shp_a', { image_id: img(1).id, version: 1, urls: { zoom: null } }, '/b');
  assert.equal(shown.urls.zoom, null);
  assert.equal(calls.lib, 0, 'lib() صدا زده نشد');
  assert.equal(state.reads, 0);
});

test('۴) هرگز پرتاب: خطای خواندن ⇒ null؛ یکتاییِ هم‌زمان (23505) ⇒ همان zoom ای که دیگری نشاند', async () => {
  state.readFails = true;
  assert.equal(await zoom.ensure('shp_a', img(1), 1), null);
  assert.equal(zoom._jobs(), 0);
  state.readFails = false;
  const other = { data: Buffer.from('other'), hash: 'h2', mime: 'image/jpeg' };
  state.tx = async () => {
    state.stored.set(`${img(2).id}@1`, other);   //  درخواستِ دیگر همان دم نشاند
    const e = new Error('duplicate key value violates unique constraint "image_derivatives_pkey"');
    e.code = '23505';
    throw e;
  };
  assert.deepEqual(await zoom.ensure('shp_a', img(2), 1), other);
  //  یکتاییِ هم‌زمان شکست نیست: یاد نمی‌شود
  state.stored.clear(); state.tx = null;
  await zoom.ensure('shp_a', img(2), 1);
  assert.equal(calls.encode, 2, 'دوباره ساخته شد');
});
