'use strict';
/**
 * WEB-16، تصمیمِ ۴-۲ «الف» — WebP ِ عکس‌های ویترین با `sharp` ِ اختیاری (`lib/store-webp.js`).
 *
 *   ۱) خاموش (`STORE_WEBP` نه) ⇒ بایت‌به‌بایت همان عکس و بی `Vary`، حتی با `Accept: image/webp`.
 *   ۲) روشن + `Accept: image/webp` ⇒ `image/webp`، کوچک‌تر، بی EXIF، ETag ِ جدا و ۳۰۴؛ `Vary: Accept`.
 *   ۳) روشن بی `Accept: image/webp` ⇒ همان JPEG؛ داده‌ای که libvips نمی‌خواند ⇒ همان اصل، بی ۵۰۰.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const config = require('../src/config');
const W = require('../src/lib/store-webp');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
//  زیرِ Electron sharp بار نمی‌شود (`store-webp.lib`) — همان‌جا «روشن» ⇒ همان JPEG (آزمونِ پایین)
const hasSharp = !process.versions.electron && (() => { try { require('sharp'); return true; } catch { return false; } })();

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { config.storefront.webp = false; await h.stop(); });
test.beforeEach(() => { resetAll(); W._reset(); });

const setFlag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
async function storeWithPhoto(photo) {
  const u = await h.newUser('عکس', 'shop');
  const shopId = (await h.post('/api/shop', { name: 'دکانِ عکس' }, { token: u.accessToken })).body.shop.id;
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(shopId, k, true);
  const slug = `webp-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', deliveryFeeMinor: 0, minOrderMinor: 0 }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const SYNC = { 'X-App': 'shop', 'X-Device': 'dev-webp' };
  const p = await h.api('POST', '/api/sync/v1/push', { token: u.accessToken, headers: SYNC, body: { device_id: 'dev-webp', schema_version: 2, ops: [
    { op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' } },
  ] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const pub = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: u.accessToken });
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  const up = await h.api('PUT', '/api/sync/v1/photos/rice', { token: u.accessToken, headers: SYNC, body: { data: photo.toString('base64') } });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  return slug;
}
const get = async (slug, path, headers = {}) => {
  const r = await fetch(`${h.base()}/api/store/${slug}${path}`, { headers });
  return { status: r.status, buf: Buffer.from(await r.arrayBuffer()), type: r.headers.get('content-type') || '',
    vary: r.headers.get('vary') || '', etag: r.headers.get('etag') || '' };
};
const ACCEPT = { Accept: 'image/avif,image/webp,image/apng,image/*,*/*;q=0.8' };

test('خاموش: همان عکس بایت‌به‌بایت، بی Vary، حتی با Accept: image/webp', async () => {
  config.storefront.webp = false;
  const jpg = F.jpeg(640, 480, { seed: 91 });
  const slug = await storeWithPhoto(jpg);
  const r = await get(slug, '/products/rice/photo', ACCEPT);
  assert.equal(r.status, 200);
  assert.ok(r.buf.equals(jpg));
  assert.match(r.type, /image\/jpeg/);
  assert.doesNotMatch(r.vary, /accept/i);
});

test('روشن: WebP ِ سبک‌تر فقط برای مرورگرِ پذیرنده؛ ETag ِ جدا و ۳۰۴؛ Vary: Accept', { skip: !hasSharp && 'sharp نصب نیست' }, async () => {
  config.storefront.webp = true;
  try {
    //  عکسِ واقعی (گرادیان + نقش) — `image-fixtures` ِ کوچک برای libvips JPEG ِ کامل نیست
    const W0 = 640; const H0 = 480; const raw = Buffer.alloc(W0 * H0 * 3);
    for (let y = 0; y < H0; y++) for (let x = 0; x < W0; x++) {
      const i = (y * W0 + x) * 3; raw[i] = x % 256; raw[i + 1] = y % 256; raw[i + 2] = ((x ^ y) * 3) % 256;
    }
    const jpg = await require('sharp')(raw, { raw: { width: W0, height: H0, channels: 3 } }).jpeg({ quality: 90 }).toBuffer();
    const slug = await storeWithPhoto(jpg);
    const w = await get(slug, '/products/rice/photo', ACCEPT);
    assert.equal(w.status, 200);
    assert.match(w.type, /^image\/webp/);
    assert.equal(w.buf.subarray(0, 4).toString('latin1'), 'RIFF');
    assert.equal(w.buf.subarray(8, 12).toString('latin1'), 'WEBP');
    assert.ok(w.buf.length < jpg.length, `${w.buf.length} < ${jpg.length}`);
    assert.doesNotMatch(w.buf.toString('latin1'), /EXIF|Exif/, 'بی فراداده');
    assert.match(w.vary, /accept/i);
    assert.match(w.etag, /-webp"$/);
    const again = await fetch(`${h.base()}/api/store/${slug}/products/rice/photo`, { headers: { ...ACCEPT, 'If-None-Match': w.etag } });
    assert.equal(again.status, 304);
    const plain = await get(slug, '/products/rice/photo', { Accept: 'image/jpeg,*/*' });
    assert.ok(plain.buf.equals(jpg), 'بی image/webp ⇒ همان JPEG');
    assert.match(plain.vary, /accept/i);
    assert.doesNotMatch(plain.etag, /-webp/);
  } finally { config.storefront.webp = false; }
});

test('زیرِ Electron: روشن هم بی sharp ⇒ همان JPEG (available = false)', { skip: !process.versions.electron && 'فقط زیرِ Electron' }, async () => {
  config.storefront.webp = true;
  try { assert.equal(W.available(), false); } finally { config.storefront.webp = false; }
});

test('روشن: دادهٔ ناخوانا یا بی sharp ⇒ null (همان اصل)، بی پرتاب', async () => {
  config.storefront.webp = true;
  try {
    assert.equal(await W.webpOf(Buffer.from('not an image at all'), 'h1', 'display'), null);
    assert.equal(await W.webpOf(Buffer.alloc(0), 'h2', 'display'), null);
    assert.equal(await W.webpOf(F.jpeg(100, 100, { seed: 1 }), 'h3', 'original'), null, 'فقط thumb/display');
    assert.equal(W.wants('image/webp'), true);
    assert.equal(W.wants('text/html,image/webpx'), false);
    assert.equal(W.wants(''), false);
  } finally { config.storefront.webp = false; }
});

test('AVIF (`STORE_AVIF`): مرورگرِ پذیرندهٔ AVIF ⇒ image/avif ِ سبک‌تر با ETag ِ خودش؛ فقط WebP ⇒ WebP؛ خاموش ⇒ همان WebP', { skip: !hasSharp && 'sharp نصب نیست' }, async () => {
  config.storefront.webp = true;
  config.storefront.avif = true;
  try {
    const W0 = 640; const H0 = 480; const raw = Buffer.alloc(W0 * H0 * 3);
    for (let y = 0; y < H0; y++) for (let x = 0; x < W0; x++) {
      const i = (y * W0 + x) * 3; raw[i] = x % 256; raw[i + 1] = y % 256; raw[i + 2] = ((x ^ y) * 3) % 256;
    }
    const jpg = await require('sharp')(raw, { raw: { width: W0, height: H0, channels: 3 } }).jpeg({ quality: 90 }).toBuffer();
    const slug = await storeWithPhoto(jpg);
    const a = await get(slug, '/products/rice/photo', ACCEPT);
    assert.equal(a.status, 200);
    assert.match(a.type, /^image\/avif/);
    assert.equal(a.buf.subarray(4, 8).toString('latin1'), 'ftyp');
    assert.match(a.buf.subarray(8, 12).toString('latin1'), /^avi[fs]$/);
    assert.ok(a.buf.length < jpg.length, `${a.buf.length} < ${jpg.length}`);
    assert.match(a.vary, /accept/i);
    assert.match(a.etag, /-avif"$/);
    const again = await fetch(`${h.base()}/api/store/${slug}/products/rice/photo`, { headers: { ...ACCEPT, 'If-None-Match': a.etag } });
    assert.equal(again.status, 304);
    //  فقط WebP ⇒ WebP (نه AVIF)؛ کارتِ کوچک هم AVIF
    const w = await get(slug, '/products/rice/photo', { Accept: 'image/webp,*/*' });
    assert.match(w.type, /^image\/webp/);
    const t = await get(slug, '/products/rice/thumb', ACCEPT);
    assert.match(t.type, /^image\/avif/);
    //  AVIF خاموش ⇒ همان WebP ِ امروز برای همان مرورگر
    config.storefront.avif = false;
    W._reset();
    const off = await get(slug, '/products/rice/photo', ACCEPT);
    assert.match(off.type, /^image\/webp/);
    assert.equal(W.pick('image/avif,image/webp'), 'webp');
    config.storefront.avif = true;
    assert.equal(W.pick('image/avif,image/webp'), 'avif');
    assert.equal(W.pick('image/webp'), 'webp');
    assert.equal(W.pick('image/avifx,text/html'), null);
  } finally { config.storefront.webp = false; config.storefront.avif = false; }
});

/**
 * بازبینیِ ۵۴ب: (۱) عکسی که WebP/AVIF اش سبک‌تر نمی‌شود — درخواستِ دوم هم همان JPEG ِ کامل است، هرگز ۲۰۰ ِ بی بدنه با
 * image/avif|webp (کشِ «سبک‌تر نشد» بافرِ خالی را پس می‌داد و همان در کشِ عمومی می‌ماند)؛ (۲) AVIF بالای سقفِ پیکسل ⇒ WebP.
 */
test('درخواستِ دوم ِ عکسی که سبک‌تر نمی‌شود: همان JPEG ِ کامل برای AVIF و WebP، هرگز بدنهٔ خالی', { skip: !hasSharp && 'sharp نصب نیست' }, async () => {
  config.storefront.webp = true;
  config.storefront.avif = true;
  try {
    const crypto = require('node:crypto');
    //  نویزِ ۱۲۰×۱۲۰ با JPEG ِ کیفیتِ ۱۰: WebP و AVIF ِ آن از خودش سنگین‌ترند
    const jpg = await require('sharp')(crypto.randomBytes(120 * 120 * 3), { raw: { width: 120, height: 120, channels: 3 } }).jpeg({ quality: 10 }).toBuffer();
    const slug = await storeWithPhoto(jpg);
    for (const accept of ['image/avif,image/webp,*/*', 'image/webp,*/*', 'image/avif,*/*']) {
      W._reset();
      for (const round of [1, 2, 3]) {
        const r = await get(slug, '/products/rice/photo', { Accept: accept });
        assert.equal(r.status, 200);
        assert.match(r.type, /^image\/jpeg/, `${accept} بارِ ${round}: ${r.type}`);
        assert.ok(r.buf.equals(jpg), `${accept} بارِ ${round}: ${r.buf.length} بایت به جای ${jpg.length}`);
      }
    }
  } finally { config.storefront.webp = false; config.storefront.avif = false; }
});

test('AVIF بالای سقفِ پیکسل ⇒ WebP ِ همان عکس (کوچک‌شده تا ضلعِ display)؛ AVIF خاموش ⇒ همان', { skip: !hasSharp && 'sharp نصب نیست' }, async () => {
  config.storefront.webp = true;
  config.storefront.avif = true;
  try {
    const sharp = require('sharp');
    //  ۲۵۰۰×۲۰۰۰ (۵ مگاپیکسل) ِ یک‌رنگ: کم‌حجم، ولی AVIF اش سنگین
    const png = await sharp({ create: { width: 2500, height: 2000, channels: 3, background: { r: 30, g: 90, b: 160 } } }).png().toBuffer();
    const slug = await storeWithPhoto(png);
    const r = await get(slug, '/products/rice/photo', { Accept: 'image/avif,image/webp,*/*' });
    assert.equal(r.status, 200);
    assert.match(r.type, /^image\/webp/, r.type);
    assert.match(r.etag, /-webp"$/);
    const m = await sharp(r.buf).metadata();
    assert.deepEqual([m.width, m.height], [1200, 960], 'کوچک‌شده تا ضلعِ display');
    //  فقط AVIF ⇒ همان اصل
    const only = await get(slug, '/products/rice/photo', { Accept: 'image/avif,*/*' });
    assert.match(only.type, /^image\/png/);
    assert.ok(only.buf.equals(png));
  } finally { config.storefront.webp = false; config.storefront.avif = false; }
});

test('srcset (`STORE_SIZES`): `?w=` از فهرستِ پهناها ⇒ همان عکس با همان پهنا؛ پهنای دیگر یا خاموش ⇒ همان اصل؛ سرِ ویترین پهناها را فقط روشن می‌گوید', { skip: !hasSharp && 'sharp نصب نیست' }, async () => {
  const sharp = require('sharp');
  const W0 = 640; const H0 = 480; const raw = Buffer.alloc(W0 * H0 * 3);
  for (let y = 0; y < H0; y++) for (let x = 0; x < W0; x++) {
    const i = (y * W0 + x) * 3; raw[i] = x % 256; raw[i + 1] = y % 256; raw[i + 2] = ((x ^ y) * 3) % 256;
  }
  const jpg = await sharp(raw, { raw: { width: W0, height: H0, channels: 3 } }).jpeg({ quality: 90 }).toBuffer();
  config.storefront.sizes = false;
  const slug = await storeWithPhoto(jpg);
  try {
    //  خاموش: `?w=` نادیده، سر بی `imageWidths`
    let off = await get(slug, '/products/rice/photo?w=320', { Accept: 'image/jpeg' });
    assert.ok(off.buf.equals(jpg));
    let head = await (await fetch(`${h.base()}/api/store/${slug}`)).json();
    assert.equal(head.imageWidths, undefined);

    config.storefront.sizes = true;
    W._reset();
    head = await (await fetch(`${h.base()}/api/store/${slug}`)).json();
    assert.deepEqual(head.imageWidths, W.WIDTHS);
    const r = await get(slug, '/products/rice/photo?w=320', { Accept: 'image/jpeg,*/*' });
    assert.equal(r.status, 200);
    assert.match(r.type, /^image\/jpeg/);
    assert.equal((await sharp(r.buf).metadata()).width, 320);
    assert.match(r.vary, /accept/i);
    assert.match(r.etag, /-w320-jpeg"$/);
    const again = await fetch(`${h.base()}/api/store/${slug}/products/rice/photo?w=320`, { headers: { Accept: 'image/jpeg', 'If-None-Match': r.etag } });
    assert.equal(again.status, 304);
    //  با WebP ِ روشن همان پهنا به WebP
    config.storefront.webp = true;
    const w = await get(slug, '/products/rice/photo?w=160', ACCEPT);
    assert.match(w.type, /^image\/webp/);
    assert.equal((await sharp(w.buf).metadata()).width, 160);
    config.storefront.webp = false;
    //  بزرگ‌تر از اصل ⇒ بزرگ نمی‌شود؛ پهنای بیرون از فهرست ⇒ همان اصل
    const big = await get(slug, '/products/rice/photo?w=960', { Accept: 'image/jpeg' });
    assert.ok((await sharp(big.buf).metadata()).width <= W0);
    off = await get(slug, '/products/rice/photo?w=333', { Accept: 'image/jpeg' });
    assert.ok(off.buf.equals(jpg));
    off = await get(slug, '/products/rice/photo?w=abc', { Accept: 'image/jpeg' });
    assert.ok(off.buf.equals(jpg));
  } finally { config.storefront.sizes = false; config.storefront.webp = false; }
});
