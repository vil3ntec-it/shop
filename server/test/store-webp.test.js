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
