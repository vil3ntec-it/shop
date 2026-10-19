'use strict';
/**
 * بانکِ تصاویر — بخش‌های خالص: بایت‌های عکس، هویت و تطبیق، زنجیرهٔ پردازش،
 * و برابریِ نسخهٔ سرورِ هستهٔ مشترکِ متن با `license/text-core.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const bytes = require('../src/lib/image-bytes');
const ID = require('../src/lib/image-identity');
const pipeline = require('../src/lib/image-pipeline');
const F = require('./image-fixtures');

const code = (fn) => { try { fn(); } catch (e) { return e.code; } return 'no-error'; };

test('هستهٔ متنِ سرور بایت‌به‌بایت همان license/text-core.js است (یک منطق، نه دو)', () => {
  const root = path.join(__dirname, '..', '..');
  const shared = fs.readFileSync(path.join(root, 'license', 'text-core.js'));
  const copy = fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'text-core.js'));
  assert.ok(shared.equals(copy), 'server/src/lib/text-core.js را با cp از license/text-core.js تازه کنید');
  //  و همان بردارهای مرجعِ مشترک روی نسخهٔ سرور
  const T = require('../src/lib/text-core');
  const vec = JSON.parse(fs.readFileSync(path.join(root, 'core', 'test-vectors', 'text-normalize.json'), 'utf8'));
  const cases = vec.searchKey || vec.search_key || [];
  for (const c of cases) assert.equal(T.searchKey(c.in ?? c.input), c.out ?? c.expected);
});

test('IMG-03: نوع از محتوا؛ پسوندِ جعلی، فایلِ خراب و ناقص رد می‌شوند', () => {
  assert.deepEqual(
    (({ mime, width, height }) => ({ mime, width, height }))(bytes.inspect(F.jpeg(800, 600))),
    { mime: 'image/jpeg', width: 800, height: 600 });
  assert.equal(bytes.inspect(F.png(320, 200)).width, 320);
  assert.deepEqual([bytes.inspect(F.webp(640, 480)).width, bytes.inspect(F.webp(640, 480)).height], [640, 480]);

  assert.equal(code(() => bytes.inspect(Buffer.from('MZ' + 'x'.repeat(500)))), 'image_type');     // exe به نامِ jpg
  assert.equal(code(() => bytes.inspect(F.jpeg(800, 600).subarray(0, 60))), 'image_corrupt');    // نیمه‌کاره
  const bad = Buffer.from(F.png(100, 100)); bad[bad.length - 20] ^= 0xff;                       // CRCِ خراب
  assert.equal(code(() => bytes.inspect(bad)), 'image_corrupt');
  assert.ok(bytes.inspect(F.jpeg(800, 600, { eoi: false })).warnings.includes('truncated_suspect'));
  assert.equal(code(() => bytes.inspect(F.png(20000, 20000))), 'image_too_large_dimensions');   // بمبِ فشرده
  assert.equal(bytes.extensionMatches('a.jpg', 'image/png'), false);
  assert.equal(bytes.extensionMatches('a.PNG', 'image/png'), true);
});

test('فرادادهٔ حساس (EXIF/GPS، متن، XMP) برداشته می‌شود؛ ICC و ابعاد می‌مانند', () => {
  const j = F.jpeg(800, 600, { gps: true });
  const js = bytes.stripMetadata(j);
  assert.ok(js.indexOf(Buffer.from('Exif')) < 0 && js.indexOf(Buffer.from('34.5,69.1')) < 0);
  assert.ok(js.indexOf(Buffer.from('ICC_PROFILE')) > 0);
  assert.equal(bytes.inspect(js).width, 800);

  const ps = bytes.stripMetadata(F.png(300, 200));
  assert.ok(ps.indexOf(Buffer.from('tEXt')) < 0);
  assert.equal(bytes.inspect(ps).height, 200);               // CRCها هنوز درست

  const ws = bytes.stripMetadata(F.webp(400, 300));
  assert.ok(ws.indexOf(Buffer.from('EXIF')) < 0);
  assert.equal(ws[20] & 0x08, 0);                             // پرچمِ EXIF در VP8X
  assert.equal(ws.readUInt32LE(4) + 8, ws.length);            // اندازهٔ RIFF
  assert.equal(bytes.inspect(ws).width, 400);

  assert.equal(bytes.inspect(F.jpeg(800, 600, { orientation: 6 })).orientation, 6);
});

test('هویت: GTIN با رقمِ کنترل، رقمِ فارسی و UPC = EAN با صفر', () => {
  const e = F.ean13('590123412345');
  assert.equal(ID.gtin(e).valid, true);
  assert.equal(ID.gtin(e.slice(0, -1) + ((Number(e.at(-1)) + 1) % 10)).valid, false);
  const fa = e.replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
  assert.equal(ID.gtin(fa).gtin14, ID.gtin(e).gtin14);
  assert.equal(ID.gtin('036000291452').gtin14, ID.gtin('0036000291452').gtin14);
  assert.deepEqual(ID.size('1', 'kg'), ID.size('1000', 'g'));
  assert.deepEqual(ID.size('۱٫۵', 'لیتر'), { base: 1500, dim: 'volume' });
});

test('قاعدهٔ تطبیقِ ۳۷.۴: نامِ مشابه هرگز به‌تنهایی قطعی نیست', () => {
  const e = F.ean13('590123412345');
  const bank = { gtin: e, name: 'چای سبز', brand: 'الکوزی', size_value: '500', size_unit: 'g' };
  assert.equal(ID.classify(bank, { barcode: e }).status, 'exact');
  assert.equal(ID.classify(bank, { barcode: e, size_value: '250', size_unit: 'g' }).status, 'ambiguous');
  assert.deepEqual(ID.classify(bank, { barcode: F.ean13('590123412346') }).reasons, ['barcode_different']);
  //  بی بارکد: نام و برند و اندازهٔ یکسان هنوز فقط «محتمل» است
  assert.equal(ID.classify(bank, { name: 'چاي سبز', brand: 'الکوزی', size_value: '0.5', size_unit: 'kg' }).status, 'probable');
  assert.deepEqual(ID.classify(bank, { name: 'چای سبز', size_value: '1', size_unit: 'kg' }).diffs, ['size']);
  assert.equal(ID.classify(bank, { name: 'چای سیاه' }).status, 'unconfirmed');
});

test('زنجیرهٔ پردازش: مرحلهٔ بیرونی BLOCKED-EXTERNAL است و هیچ کاری نمی‌کند', () => {
  const st = Object.fromEntries(pipeline.describe().map((s) => [s.id, s.status]));
  assert.equal(st.validate, 'available');
  assert.equal(st.strip_metadata, 'available');
  assert.equal(st.encode_webp, 'missing');
  assert.equal(st.encode_avif, 'missing');
  for (const s of ['enhance', 'background_removal', 'upscale', 'inpainting']) assert.equal(st[s], 'blocked_external');
  let err;
  try { pipeline.run('background_removal', F.jpeg(800, 600)); } catch (e) { err = e; }
  assert.equal(err.status, 503);
  assert.equal(err.code, 'step_unavailable');
  assert.equal(err.state, 'blocked_external');
});
