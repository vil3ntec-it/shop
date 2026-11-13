'use strict';
/**
 * IMG-12 (§۳۴.۴) در ویترینِ وب: عوض شدنِ عکس با نشانیِ نسخه‌دار به خریدار می‌رسد، و هیچ نشانیِ پیشینی نمی‌شکند.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) هر کالای عکس‌دار در کاتالوگ، صفحهٔ کالا، تازه‌ها و `?ids=` (سبد و خریدِ دوباره) یک `photoVersion` ِ یکسان دارد
 *      (۱۲ رقمِ hex)؛ کالای بی عکس این کلید را ندارد.
 *   ۲) `/thumb?v=<همان>` و `/photo?v=<همان>` یک روز در کش می‌مانند؛ بی `v` یا با `v` ِ کهنه/نادرست/تکراری همان
 *      عکسِ امروز با ۵ دقیقه (نشانیِ پیشین، SEO و سبدِ ذخیره‌شده نمی‌شکنند).
 *   ۳) عکسِ تازه ⇒ `photoVersion` ِ تازه؛ نشانیِ کهنه هرگز با عکسِ تازه به‌درازا کش نمی‌شود.
 *   ۴) بانکِ تصاویر (پرچمِ دکان): نسخه از (عکس، نسخه)ِ بانک؛ اندازه‌ای که از بانک نیست و به عکسِ همگام‌شده برمی‌گردد
 *      هرگز زیرِ نسخهٔ بانک به‌درازا کش نمی‌شود؛ نسخهٔ تازهٔ بانک ⇒ نشانیِ تازه؛ `?ids=` همان عکسِ بانک را می‌بیند.
 *   ۵) عکسِ برداشته یا کالای منتشرنشده با هر `v` ⇒ ۴۰۴.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const LONG = 'public, max-age=86400';
const SHORT = 'public, max-age=300';
const TAG = /^[0-9a-f]{12}$/;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });
const SYNC = { 'X-App': 'shop', 'X-Device': 'dev-photo' };

/** دکانِ روشن با ویترین: برنج و روغن منتشرشده، صابون نه. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id, slug: `ph-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 0, minOrderMinor: 0 },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', { token: o.token, headers: SYNC, body: { device_id: 'dev-photo', schema_version: 2, ops: [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return o;
}

const syncPhoto = async (s, id, buf) => {
  const r = await h.api('PUT', `/api/sync/v1/photos/${id}`, { token: s.token, headers: SYNC, body: { data: buf.toString('base64') } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

async function pic(s, p, headers = {}) {
  const r = await fetch(`${h.base()}/api/store/${s.slug}${p}`, { headers });
  return { status: r.status, cache: r.headers.get('cache-control'), etag: r.headers.get('etag'), buf: Buffer.from(await r.arrayBuffer()) };
}

/** `photoVersion` ِ یک کالا در هر چهار راهی که ویترین کالا را می‌گیرد — باید یکی باشند. */
/** بایت‌های نسخهٔ کوچکِ بانک، همان‌طور که بانک نگه داشته (بی EXIF) */
const bankThumb = async (s, imageId, version) => (await h.download(`/api/shop/images/file/${imageId}/${version}/thumb`, { token: s.token })).buffer;

async function versionsOf(s, id) {
  const cat = (await h.get(`/api/store/${s.slug}/products`)).body.items.find((p) => p.id === id);
  const byIds = (await h.get(`/api/store/${s.slug}/products?ids=${id}`)).body.items.find((p) => p.id === id);
  const detail = (await h.get(`/api/store/${s.slug}/products/${id}`)).body.product;
  const fresh = (await h.get(`/api/store/${s.slug}/highlights`)).body.newArrivals.find((p) => p.id === id);
  return { cat, byIds, detail, fresh, all: [cat, byIds, detail, fresh].map((p) => p && p.photoVersion) };
}

test('IMG-12: عکسِ همگام‌شده — نشانیِ نسخه‌دار یک روز، بی نسخه یا کهنه ۵ دقیقه؛ عکسِ تازه نشانیِ تازه', async () => {
  const s = await liveStore('نسخهٔ عکس');
  const first = F.jpeg(320, 320, { seed: 501 });
  await syncPhoto(s, 'rice', first);

  const v1 = await versionsOf(s, 'rice');
  assert.match(String(v1.cat.photoVersion), TAG, JSON.stringify(v1.cat));
  assert.equal(v1.cat.photo, true);
  assert.deepEqual(v1.all, Array(4).fill(v1.cat.photoVersion), 'کاتالوگ، ?ids=، صفحهٔ کالا و تازه‌ها یک نسخه');
  const oil = (await h.get(`/api/store/${s.slug}/products`)).body.items.find((p) => p.id === 'oil');
  assert.equal(oil.photo, false);
  assert.equal('photoVersion' in oil, false, 'بی عکس ⇒ بی نسخه');
  const tag1 = v1.cat.photoVersion;

  for (const size of ['thumb', 'photo']) {
    const hit = await pic(s, `/products/rice/${size}?v=${tag1}`);
    assert.equal(hit.status, 200, size);
    assert.equal(hit.cache, LONG, `${size}: نشانیِ نسخه‌دار یک روز`);
    assert.ok(hit.buf.equals(first), size);
    const again = await pic(s, `/products/rice/${size}?v=${tag1}`, { 'If-None-Match': hit.etag });
    assert.equal(again.status, 304);
    const plain = await pic(s, `/products/rice/${size}`);
    assert.equal(plain.status, 200, `${size}: نشانیِ بی نسخه (پیوند و سبدِ پیشین) هنوز کار می‌کند`);
    assert.equal(plain.cache, SHORT);
    assert.ok(plain.buf.equals(first));
  }
  for (const odd of ['?v=000000000000', `?v=${tag1}&v=${tag1}`, '?v=', `?v=${'f'.repeat(500)}`, `?V=${tag1}`]) {
    const r = await pic(s, `/products/rice/thumb${odd}`);
    assert.deepEqual([r.status, r.cache], [200, SHORT], `نسخهٔ نادرست ${odd.slice(0, 30)} ⇒ همان عکس با ۵ دقیقه، نه خطا`);
  }

  //  عکسِ تازه ⇒ نسخهٔ تازه در هر چهار راه؛ نشانیِ کهنه عکسِ تازه را با ۵ دقیقه می‌دهد، هرگز یک روز
  const second = F.jpeg(320, 320, { seed: 502 });
  await syncPhoto(s, 'rice', second);
  const v2 = await versionsOf(s, 'rice');
  const tag2 = v2.cat.photoVersion;
  assert.match(String(tag2), TAG);
  assert.notEqual(tag2, tag1, 'عکسِ تازه ⇒ نشانیِ تازه');
  assert.deepEqual(v2.all, Array(4).fill(tag2));
  const old = await pic(s, `/products/rice/thumb?v=${tag1}`);
  assert.deepEqual([old.status, old.cache], [200, SHORT], 'پیوندِ کهنه نمی‌شکند و به‌درازا کش نمی‌شود');
  assert.ok(old.buf.equals(second), 'پیوندِ کهنه همان عکسِ امروز');
  const now2 = await pic(s, `/products/rice/thumb?v=${tag2}`);
  assert.deepEqual([now2.status, now2.cache], [200, LONG]);
  assert.ok(now2.buf.equals(second));

  //  همان بایت‌ها دوباره ⇒ همان نسخه (از محتوا، نه از زمان)
  await syncPhoto(s, 'rice', second);
  assert.equal((await versionsOf(s, 'rice')).cat.photoVersion, tag2);

  //  عکس برداشته ⇒ کاتالوگ بی عکس و بی نسخه؛ هر نشانی ۴۰۴
  const del = await h.api('DELETE', '/api/sync/v1/photos/rice', { token: s.token, headers: SYNC });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  const gone = (await h.get(`/api/store/${s.slug}/products`)).body.items.find((p) => p.id === 'rice');
  assert.equal(gone.photo, false);
  assert.equal('photoVersion' in gone, false);
  assert.equal((await pic(s, `/products/rice/thumb?v=${tag2}`)).status, 404);
  assert.equal((await pic(s, '/products/rice/thumb')).status, 404);
  //  منتشرنشده با عکس ⇒ ۴۰۴ با هر نسخه
  await syncPhoto(s, 'soap', first);
  assert.equal((await pic(s, `/products/soap/thumb?v=${tag1}`)).status, 404);
});

test('IMG-12: بانکِ تصاویر — نسخه از (عکس، نسخه)ِ بانک؛ اندازهٔ برگشته به عکسِ همگام‌شده کوتاه؛ نسخهٔ تازهٔ بانک نشانیِ تازه', async () => {
  const s = await liveStore('نسخهٔ بانک');
  const legacy = F.jpeg(320, 320, { seed: 601 });
  await syncPhoto(s, 'rice', legacy);
  const legacyTag = (await versionsOf(s, 'rice')).cat.photoVersion;
  assert.match(String(legacyTag), TAG);

  await setFlag(s.shopId, 'shop.image_bank', true);
  const own = await h.raw('POST', '/api/shop/images/?product_id=rice', F.jpeg(800, 600, { seed: 602 }), { token: s.token });
  assert.equal(own.status, 201, JSON.stringify(own.body));
  const thumb1 = F.jpeg(400, 300, { seed: 603 });
  assert.equal((await h.raw('PUT', `/api/shop/images/${own.body.id}/v/1/thumb`, thumb1, { token: s.token })).status, 200);
  const sel = await h.post('/api/shop/images/select', { product_id: 'rice', source: 'shop', image_id: own.body.id }, { token: s.token });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  //  روغن فقط عکسِ بانک دارد
  const own2 = await h.raw('POST', '/api/shop/images/?product_id=oil', F.jpeg(800, 600, { seed: 604 }), { token: s.token });
  await h.raw('PUT', `/api/shop/images/${own2.body.id}/v/1/thumb`, F.jpeg(400, 300, { seed: 605 }), { token: s.token });
  await h.post('/api/shop/images/select', { product_id: 'oil', source: 'shop', image_id: own2.body.id }, { token: s.token });

  const b1 = await versionsOf(s, 'rice');
  const bankTag = b1.cat.photoVersion;
  assert.match(String(bankTag), TAG);
  assert.notEqual(bankTag, legacyTag, 'عکسِ بانک ⇒ نسخهٔ بانک، نه عکسِ همگام‌شده');
  assert.deepEqual(b1.all, Array(4).fill(bankTag), 'سبد (?ids=) همان عکسِ بانک را می‌بیند');
  const oilIds = (await h.get(`/api/store/${s.slug}/products?ids=oil`)).body.items[0];
  assert.equal(oilIds.photo, true, '?ids=: کالای فقط-بانک هم «عکس دارد» (عکسِ سبد پس از تازه شدن نمی‌پرد)');
  assert.match(String(oilIds.photoVersion), TAG);

  const t = await pic(s, `/products/rice/thumb?v=${bankTag}`);
  assert.deepEqual([t.status, t.cache], [200, LONG]);
  assert.ok(t.buf.length > 0 && t.buf.equals(await bankThumb(s, own.body.id, 1)) && !t.buf.equals(legacy), 'نسخهٔ کوچکِ بانک');
  //  اندازهٔ «display» در بانک نیست ⇒ عکسِ همگام‌شده — زیرِ نسخهٔ بانک هرگز یک روز نمی‌ماند
  const big = await pic(s, `/products/rice/photo?v=${bankTag}`);
  assert.deepEqual([big.status, big.cache], [200, SHORT]);
  assert.ok(big.buf.equals(legacy));
  //  اگر نشانی با نسخهٔ عکسِ همگام‌شده خواسته شود ولی بانک سرو کند ⇒ باز هم کوتاه
  assert.equal((await pic(s, `/products/rice/thumb?v=${legacyTag}`)).cache, SHORT);

  //  عکسِ تازهٔ بانک (همان راهِ برنامه: بارگذاریِ تازه + گزینش) ⇒ نشانیِ تازه؛ نشانیِ پیشین همان عکسِ امروز با ۵ دقیقه
  const v2 = await h.raw('POST', '/api/shop/images/?product_id=rice', F.jpeg(800, 600, { seed: 606 }), { token: s.token });
  assert.equal(v2.status, 201, JSON.stringify(v2.body));
  const thumb2 = F.jpeg(400, 300, { seed: 607 });
  assert.equal((await h.raw('PUT', `/api/shop/images/${v2.body.id}/v/1/thumb`, thumb2, { token: s.token })).status, 200);
  const sel2 = await h.post('/api/shop/images/select', { product_id: 'rice', source: 'shop', image_id: v2.body.id }, { token: s.token });
  assert.equal(sel2.status, 200, JSON.stringify(sel2.body));
  const b2 = await versionsOf(s, 'rice');
  const bankTag2 = b2.cat.photoVersion;
  assert.notEqual(bankTag2, bankTag, 'نسخهٔ تازهٔ بانک ⇒ نشانیِ تازه');
  assert.deepEqual(b2.all, Array(4).fill(bankTag2));
  const fresh = await pic(s, `/products/rice/thumb?v=${bankTag2}`);
  assert.deepEqual([fresh.status, fresh.cache], [200, LONG]);
  const thumb2Bytes = await bankThumb(s, v2.body.id, 1);
  assert.ok(fresh.buf.length > 0 && fresh.buf.equals(thumb2Bytes) && !fresh.buf.equals(t.buf));
  const stale = await pic(s, `/products/rice/thumb?v=${bankTag}`);
  assert.deepEqual([stale.status, stale.cache], [200, SHORT]);
  assert.ok(stale.buf.equals(thumb2Bytes), 'نشانیِ نسخهٔ ۱ عکسِ امروز را می‌دهد');

  //  پرچمِ بانک خاموش ⇒ دوباره نسخهٔ عکسِ همگام‌شده
  await setFlag(s.shopId, 'shop.image_bank', false);
  assert.deepEqual((await versionsOf(s, 'rice')).all, Array(4).fill(legacyTag));
  const back = await pic(s, `/products/rice/thumb?v=${legacyTag}`);
  assert.deepEqual([back.status, back.cache], [200, LONG]);
  assert.ok(back.buf.equals(legacy));
});
