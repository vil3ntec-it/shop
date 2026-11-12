'use strict';
/**
 * IMG-11 (نسخهٔ بزرگ) — `lib/image-zoom.js`: zoom ِ ۲۴۰۰px ِ عکسِ بانک روی خودِ سرور، در همان `image_derivatives`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `image_bank.zoom` پیش‌فرض خاموش ⇒ ۴۰۴ (no-store) و هیچ ردیف/بلابی؛ display و thumb همان امروز.
 *   ۲) روشن ⇒ یک بار ساخته و نگه داشته می‌شود: JPEG، بزرگ‌ترین ضلع ۲۴۰۰، جهتِ EXIF روی پیکسل (orientation 1)، نسبتِ ابعادِ اصل،
 *      بی EXIF/GPS، رنگِ هر نیمه همان؛ درخواستِ دوم همان بایت‌ها و همان یک ردیف؛ `?v=` ِ نسخهٔ عکس ⇒ کشِ یک‌روزه.
 *   ۳) اصلِ کوچک (≤ display)، سرِ پرِ ساخت، کالای منتشرنشده ⇒ بی zoom و بی ردیف؛ zoom ای که برنامه فرستاده دست نمی‌خورد.
 *   ۴) همان نسخه برای برنامه از `/api/shop/images/file/:id/:v/zoom` (دکانِ دیگر ⇒ ۴۰۴) و برای گالریِ ویترین.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const zoom = require('../src/lib/image-zoom');

let sharp = null;
try { sharp = require('sharp'); } catch { /* بی sharp ⇒ این پرونده رد می‌شود */ }
const skip = !sharp && 'sharp روی این ماشین نیست';

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-zoom' },
  body: { device_id: 'dev-zoom', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** عکسِ واقعی (قابلِ رمزگشایی): نیمهٔ بالا قرمز، پایین آبی، با EXIF ِ چرخش و GPS ِ ساختگی */
async function photo(w, hgt, { orientation = 1 } = {}) {
  const top = { r: 220, g: 30, b: 40 };
  const bottom = { r: 30, g: 60, b: 210 };
  const half = Math.floor(hgt / 2);
  const base = await sharp({ create: { width: w, height: hgt, channels: 3, background: bottom } })
    .composite([{ input: { create: { width: w, height: half, channels: 3, background: top } }, top: 0, left: 0 }])
    .jpeg({ quality: 80 }).toBuffer();
  return sharp(base).withExif({ IFD0: { Make: 'TestCam' }, IFD3: { GPSLatitudeRef: 'N' } }).withMetadata({ orientation }).jpeg({ quality: 80 }).toBuffer();
}

/** نسخهٔ کوچکِ ساختهٔ برنامه (چرخانده، بی EXIF) */
const derive = (buf, side) => sharp(buf).rotate().resize({ width: side, height: side, fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 80 }).toBuffer();

async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.image_bank']) await setFlag(o.shopId, k, true);
  const slug = `z-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

/** کالای منتشرشده با عکسِ خودِ دکان در بانک (اصل + display + thumb ِ برنامه) و انتخاب‌شده برای ویترین */
async function productWithPhoto(s, id, src, { publish = true, role = 'main', select = true } = {}) {
  if (!(await one('SELECT 1 FROM sync_rows WHERE account_id = $1 AND row_id = $2', [s.shopId, id]).catch(() => null))) {
    assert.equal((await push(s.token, [op(id, { name: `کالا ${id}`, unit: 'دانه', salePrice: 50 })])).status, 200);
  }
  if (publish) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  const up = await h.raw('POST', `/api/shop/images?product_id=${id}&role=${role}`, src, { token: s.token });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  for (const [size, side] of [['display', 1200], ['thumb', 400]]) {
    const d = await h.raw('PUT', `/api/shop/images/${up.body.id}/v/1/${size}`, await derive(src, side), { token: s.token });
    assert.equal(d.status, 200, JSON.stringify(d.body));
  }
  if (select) {
    const sel = await h.post('/api/shop/images/select', { product_id: id, source: 'shop', image_id: up.body.id }, { token: s.token });
    assert.equal(sel.status, 200, JSON.stringify(sel.body));
  }
  return up.body.id;
}

const zooms = async (imageId) => Number((await one(`SELECT COUNT(*)::int AS n FROM image_derivatives WHERE image_id = $1 AND size = 'zoom'`, [imageId])).n);
const getZoom = (s, id, q = '') => h.download(`/api/store/${s.slug}/products/${id}/zoom${q}`);

test('IMG-11: پرچمِ خاموش ⇒ بی zoom و بی ردیف؛ روشن ⇒ یک بار ساخته، ۲۴۰۰px، چرخش و رنگ و نسبت درست، بی EXIF، همان بایت‌ها', { skip }, async () => {
  const s = await liveShop('بزرگ');
  const src = await photo(3000, 2000, { orientation: 6 });   //  ایستاده: ۲۰۰۰ × ۳۰۰۰
  const imageId = await productWithPhoto(s, 'tall', src);

  //  ۱) پیش‌فرض خاموش
  const off = await getZoom(s, 'tall');
  assert.equal(off.status, 404);
  assert.equal(off.headers.get('cache-control'), 'no-store');
  assert.equal(await zooms(imageId), 0, 'خاموش ⇒ هیچ نوشتنی');
  assert.equal((await h.download(`/api/store/${s.slug}/products/tall/photo`)).status, 200, 'display همان امروز');

  //  ۲) روشن
  await setFlag(s.shopId, zoom.FLAG, true);
  const z = await getZoom(s, 'tall');
  assert.equal(z.status, 200);
  assert.equal(z.headers.get('content-type'), 'image/jpeg');
  const m = await sharp(z.buffer).metadata();
  assert.deepEqual([m.width, m.height], [1600, 2400], 'بزرگ‌ترین ضلع ۲۴۰۰ و نسبتِ اصلِ ایستاده (۲:۳)');
  assert.ok(!m.orientation || m.orientation === 1, 'جهتِ EXIF روی پیکسل');
  assert.equal(m.exif, undefined, 'بی EXIF/GPS');
  assert.ok(z.buffer.indexOf(Buffer.from('TestCam')) < 0);
  //  رنگ: اصلِ ۳۰۰۰×۲۰۰۰ با چرخشِ ۶ (۹۰° ساعت‌گرد) ⇒ نیمهٔ بالای اصل (قرمز) سمتِ راستِ zoom
  const { data, info } = await sharp(z.buffer).raw().toBuffer({ resolveWithObject: true });
  const px = (x, y) => Array.from(data.subarray((y * info.width + x) * info.channels, (y * info.width + x) * info.channels + 3));
  const near = (a, b) => a.every((v, i) => Math.abs(v - b[i]) <= 12);
  assert.ok(near(px(1500, 1200), [220, 30, 40]), `راست قرمز: ${px(1500, 1200)}`);
  assert.ok(near(px(100, 1200), [30, 60, 210]), `چپ آبی: ${px(100, 1200)}`);
  assert.equal(await zooms(imageId), 1);

  //  همان بایت‌ها، همان یک ردیف؛ `?v=` ِ نسخهٔ عکس ⇒ کشِ یک‌روزه
  const tag = (await h.get(`/api/store/${s.slug}/products/tall`)).body.product.photoVersion;
  assert.match(String(tag), /^[0-9a-f]{12}$/);
  const again = await getZoom(s, 'tall', `?v=${tag}`);
  assert.equal(again.status, 200);
  assert.ok(again.buffer.equals(z.buffer));
  assert.equal(again.headers.get('cache-control'), 'public, max-age=86400');
  assert.equal((await getZoom(s, 'tall')).headers.get('cache-control'), 'public, max-age=300');
  assert.equal(await zooms(imageId), 1, 'یک‌بارنویس: هر (عکس، نسخه) یک بار');
});

test('IMG-11: اصلِ کوچک، سرِ پرِ ساخت و کالای منتشرنشده ⇒ بی zoom؛ zoom ِ فرستادهٔ برنامه دست نمی‌خورد', { skip }, async () => {
  const s = await liveShop('مرز');
  await setFlag(s.shopId, zoom.FLAG, true);

  //  اصل از display بزرگ‌تر نیست ⇒ zoom چیزی ندارد
  const small = await productWithPhoto(s, 'small', await photo(1000, 800));
  assert.equal((await getZoom(s, 'small')).status, 404);
  assert.equal(await zooms(small), 0);
  assert.equal((await h.get('/api/shop/images/products/small', { token: s.token })).body.urls.zoom, null, 'اصلِ کوچک ⇒ بی نشانیِ zoom');

  //  سرِ پر ⇒ همان display (۴۰۴)، نه صف؛ پس از آزاد شدن ساخته می‌شود
  const busy = await productWithPhoto(s, 'busy', await photo(2600, 2600));
  zoom._jobs(zoom.MAX_JOBS);
  try {
    assert.equal((await getZoom(s, 'busy')).status, 404);
    assert.equal(await zooms(busy), 0);
  } finally { zoom._jobs(0); }
  const built = await getZoom(s, 'busy');
  assert.equal(built.status, 200);
  assert.deepEqual([(await sharp(built.buffer).metadata()).width, (await sharp(built.buffer).metadata()).height], [2400, 2400]);

  //  منتشرنشده ⇒ ۴۰۴ با پرچمِ روشن
  const hidden = await productWithPhoto(s, 'hidden', await photo(2600, 2000), { publish: false });
  assert.equal((await getZoom(s, 'hidden')).status, 404);
  assert.equal(await zooms(hidden), 0);

  //  zoom ای که برنامه خودش فرستاده همان می‌ماند
  const src = await photo(3000, 2000);
  const own = await productWithPhoto(s, 'own', src);
  const appZoom = await derive(src, 2000);
  const put = await h.raw('PUT', `/api/shop/images/${own}/v/1/zoom`, appZoom, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const got = await getZoom(s, 'own');
  assert.equal(got.status, 200);
  assert.equal((await sharp(got.buffer).metadata()).width, 2000, 'همان zoom ِ برنامه، نه ساختهٔ سرور');
  assert.equal(await zooms(own), 1);
});

test('IMG-11: همان zoom برای برنامه (فقط همان دکان) و برای گالریِ ویترین', { skip }, async () => {
  const a = await liveShop('برنامه');
  const b = await liveShop('دیگری');
  await setFlag(a.shopId, zoom.FLAG, true);
  const imageId = await productWithPhoto(a, 'app', await photo(3000, 2000));
  //  برنامه نشانیِ نسخهٔ بزرگ را از «کدام عکس» می‌گیرد — پیش از نخستین ساخت هم (سرور می‌سازد)
  const shown = (await h.get('/api/shop/images/products/app', { token: a.token })).body;
  assert.equal(shown.urls.zoom, `/api/shop/images/file/${imageId}/1/zoom`, JSON.stringify(shown.urls));
  assert.equal(await zooms(imageId), 0, '«کدام عکس» خودش چیزی نمی‌سازد');
  const file = await h.download(`/api/shop/images/file/${imageId}/1/zoom`, { token: a.token });
  assert.equal(file.status, 200);
  assert.match(file.headers.get('cache-control'), /immutable/);
  assert.equal((await sharp(file.buffer).metadata()).width, 2400);
  const store = await getZoom(a, 'app');
  assert.ok(store.buffer.equals(file.buffer), 'ویترین و برنامه همان یک نسخه');
  assert.equal((await h.download(`/api/shop/images/file/${imageId}/1/zoom`, { token: b.token })).status, 404, 'دکانِ دیگر نه');
  //  پرچمِ خاموشِ دکانِ دیگر: درخواستِ خودش هم نمی‌سازد
  const bImage = await productWithPhoto(b, 'other', await photo(3000, 2000));
  assert.equal((await h.get('/api/shop/images/products/other', { token: b.token })).body.urls.zoom, null, 'خاموش ⇒ همان پاسخِ پیشین');
  assert.equal((await h.download(`/api/shop/images/file/${bImage}/1/zoom`, { token: b.token })).status, 404);
  assert.equal(await zooms(bImage), 0);

  //  گالری (عکسِ تکمیلی با نقشِ «جزئیات»)
  await setFlag(a.shopId, 'shop.storefront.gallery', true);
  const extra = await productWithPhoto(a, 'app', await photo(2800, 2100), { role: 'detail', select: false });
  const list = (await h.get(`/api/store/${a.slug}/products/app/gallery`)).body;
  assert.equal(list.items.length, 1, JSON.stringify(list));
  const g = await h.download(`/api/store/${a.slug}/products/app/gallery/${list.items[0].tag}/zoom`);
  assert.equal(g.status, 200);
  assert.deepEqual([(await sharp(g.buffer).metadata()).width, (await sharp(g.buffer).metadata()).height], [2400, 1800]);
  assert.equal(await zooms(extra), 1);
  assert.equal((await h.download(`/api/store/${a.slug}/products/app/gallery/${list.items[0].tag}/original`)).status, 404, 'اصل هرگز');
});
