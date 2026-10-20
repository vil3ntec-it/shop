'use strict';
/**
 * خانهٔ بازارچه: «اول کالا» (`GET /api/market/products`، `lib/market.js` ← feed).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `market.directory` ۴۰۴.
 *   ۲) ⛔ جداییِ دکان‌ها و حریم: کالای دکانِ بیرون از فهرست، منتشرنشده یا حذف‌شده
 *      هرگز در خانه نیست؛ پاسخ شناسهٔ داخلیِ دکان، شماره یا مختصات ندارد؛ کالاها
 *      به نوبت از دکان‌ها می‌آیند (یک دکان خانه را پر نمی‌کند).
 *   ۳) ⛔ «پرفروش» بی عددِ ساختگی و با k-گمنامی: سفارش‌های یک یا دو شماره (حتی
 *      زیاد) یا سفارشِ لغوشده چیزی نمی‌سازد؛ سه شمارهٔ جدا ⇒ نمایش.
 *   ۴) «ارزان‌تر از قیمتِ دکان» فقط وقتی قیمتِ آنلاین کمتر از قیمتِ فروشِ دفتر است.
 *   ۵) دسته، «نزدیکِ من» و دکان‌های پیشنهادی با عکسِ یکی از کالاهای خودشان.
 */
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
process.env.MARKET_FEED_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترین؛ `products` = [شناسه, نام, قیمت, منتشر؟]. */
async function liveStore(label, { products, listing = {} } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `f-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 5000,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = `dev-${uniq()}`;
  const ops = products.map(([id, name, price]) => ({
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
    fields: { name, unit: 'دانه', salePrice: price, category: 'خوراکی' },
  }));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id, , , pub] of products) {
    if (pub === false) continue;
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  if (listing) {
    const r = await h.put('/api/shop/market', { listed: true, ...listing }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug, device };
}

const feed = (qs = '') => h.get(`/api/market/products${qs}`);
const keyOf = (p) => `${p.shop.slug}/${p.id}`;

test('بی پرچمِ market.directory خانهٔ کالا ۴۰۴ است', async () => {
  const s = await liveStore('خاموش', { products: [['rice', 'برنج', 120]] });
  const r = await feed();
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'market_not_found');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('⛔ جداییِ دکان‌ها: فقط کالای منتشرشدهٔ دکان‌های در فهرست، به نوبت از هر دکان، بی دادهٔ خصوصی', async () => {
  await setFlag('all', 'market.directory', true);
  const a = await liveStore('الف', { products: [['a1', 'آرد', 100], ['a2', 'برنج', 120], ['a3', 'چای', 90], ['a4', 'صابون', 40, false]], listing: { category: 'grocery' } });
  const b = await liveStore('ب', { products: [['b1', 'نان', 15], ['b2', 'کلچه', 30]], listing: { category: 'bakery', location: { lat: 34.531, lng: 69.172 } } });
  const c = await liveStore('بیرون', { products: [['c1', 'شیر', 60]], listing: { listed: false } });

  const r = await feed();
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const keys = r.body.items.map(keyOf);
  assert.deepEqual(keys.slice().sort(), [`${a.slug}/a1`, `${a.slug}/a2`, `${a.slug}/a3`, `${b.slug}/b1`, `${b.slug}/b2`].sort(),
    'نه منتشرنشده، نه دکانِ بیرون از فهرست');
  assert.notEqual(r.body.items[0].shop.slug, r.body.items[1].shop.slug, 'به نوبت از هر دکان');
  assert.ok(r.body.items.every((p) => p.priceMinor > 0 && p.stock && p.stock.status === 'unverified'), 'موجودی بی پرچم «در حال بررسی»');
  assert.deepEqual(r.body.popular, [], 'بی سفارش، «پرفروش» خالی است — نه ساختگی');
  assert.deepEqual(r.body.deals, []);
  assert.deepEqual(r.body.shops.map((s) => s.slug).sort(), [a.slug, b.slug].sort());
  assert.ok(r.body.shops.every((s) => s.cover === null), 'بی عکس ⇒ بی جلد');
  const txt = JSON.stringify(r.body);
  for (const secret of [a.shopId, b.shopId, c.shopId, c.slug, '0700000001', '34.531', '3453']) {
    assert.ok(!txt.includes(secret), `پاسخ «${secret}» را ندارد`);
  }

  //  حذف ⇒ همان دم بیرون (قفسه با feedCacheMs=0)
  await h.api('POST', '/api/sync/v1/push', {
    token: a.token, headers: { 'X-App': 'shop', 'X-Device': a.device },
    body: { device_id: a.device, schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'a3', type: 'delete', fields: {} }] },
  });
  assert.ok(!(await feed()).body.items.some((p) => p.id === 'a3'), 'کالای حذف‌شده در خانه نیست');

  //  دسته و «نزدیکِ من»
  const bak = await feed('?category=bakery');
  assert.deepEqual([...new Set(bak.body.items.map((p) => p.shop.slug))], [b.slug]);
  assert.equal((await feed('?category=nope')).status, 400);
  const nr = await feed('?lat=34.53&lng=69.17');
  assert.equal(nr.body.items[0].shop.slug, b.slug, 'نزدیک‌ترها اول');
  assert.equal(nr.body.items[0].shop.distanceKm, 0.5, 'فقط فاصلهٔ گردشده');
  assert.equal((await feed('?lat=999&lng=1')).status, 400);

  //  عکسِ یکی از کالاهای خودِ دکان ⇒ جلدِ کارتِ دکان، و عکس‌دارها اولِ قفسه
  await query(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
     VALUES ('shop', $1, 'a2', $2, 'h1', 4, 1, $3)`,
    [a.shopId, Buffer.from([0xff, 0xd8, 0xff, 0xe0]), now()]
  );
  const ph = await feed('?category=grocery');
  assert.equal(ph.body.items[0].id, 'a2', 'کالای عکس‌دار اول');
  assert.equal(ph.body.items[0].photo, true);
  assert.equal(ph.body.shops.find((s) => s.slug === a.slug).cover, 'a2');

  for (const s of [a, b, c]) await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('⛔ «پرفروش» فقط با k-گمنامی (۳ شمارهٔ جدا)؛ لغوشده نمی‌شمارد؛ «ارزان‌تر» فقط با قیمتِ آنلاینِ کمتر', async () => {
  await setFlag('all', 'market.directory', true);
  const s = await liveStore('پرفروش', { products: [['rice', 'برنج', 120], ['oil', 'روغن', 300], ['tea', 'چای', 90]] });
  const ticketOf = async (phone) => {
    const st = await h.post('/api/market/verify/start', { phone });
    assert.equal(st.status, 200, JSON.stringify(st.body));
    const c = await h.post('/api/market/verify/check', { phone, code: st.body.devCode });
    assert.equal(c.status, 200, JSON.stringify(c.body));
    return c.body.ticket;
  };
  const order = async (phone, productId) => {
    const r = await h.post(`/api/store/${s.slug}/checkout`, {
      fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId, qtyMilli: 1000 }],
    }, { headers: { 'Idempotency-Key': `fd-${uniq()}-key`, 'X-Store-Ticket': await ticketOf(phone) } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body.order;
  };
  const popular = async () => (await feed()).body.popular.map((p) => p.id);

  for (let i = 0; i < 4; i++) await order('0791110001', 'rice');
  assert.deepEqual(await popular(), [], 'چهار سفارشِ یک نفر = یک نفر');
  await order('0791110002', 'rice');
  const third = await order('0791110003', 'rice');
  const cancel = await h.post(`/api/store/${s.slug}/orders/${third.token}/cancel`, {});
  assert.equal(cancel.status, 200, JSON.stringify(cancel.body));
  assert.deepEqual(await popular(), [], 'لغوشده نمی‌شمارد ⇒ هنوز دو شماره');
  await order('0791110004', 'rice');
  assert.deepEqual(await popular(), ['rice'], 'سه شمارهٔ جدا ⇒ «پرفروش»');
  assert.ok(!JSON.stringify((await feed()).body).includes('079111'), 'هیچ شماره‌ای در پاسخ نیست');

  //  ارزان‌تر از قیمتِ دکان: قیمتِ آنلاین کمتر از قیمتِ فروشِ دفتر
  await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 25000 }, { token: s.token });
  await h.put('/api/shop/storefront/products/tea', { published: true, onlinePriceMinor: 9500 }, { token: s.token });
  const d = (await feed()).body.deals;
  assert.deepEqual(d.map((p) => [p.id, p.priceMinor, p.regularPriceMinor]), [['oil', 25000, 30000]],
    'فقط کالای ارزان‌تر، با قیمتِ دفتر؛ قیمتِ آنلاینِ گران‌تر تخفیف نیست');
  assert.deepEqual((await feed()).body.items.filter((p) => p.regularPriceMinor !== undefined).map((p) => [p.id, p.regularPriceMinor]),
    [['oil', 30000]], 'در شبکهٔ کالا هم همان قیمتِ دفتر فقط برای کالای ارزان‌تر');

  //  دکانِ بیرون‌رفته ⇒ «پرفروش» و «ارزان‌تر» ش هم بیرون
  await h.put('/api/shop/market', { listed: false }, { token: s.token });
  const gone = (await feed()).body;
  assert.deepEqual([gone.popular.length, gone.deals.length, gone.items.length], [0, 0, 0]);
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('⛔ DoS: قفسه و فهرست‌های سراسری `feedCacheMs` می‌مانند؛ بیرون رفتن از فهرست همان دم است', async () => {
  await setFlag('all', 'market.directory', true);
  const config = require('../src/config');
  const market = require('../src/lib/market');
  const s = await liveStore('حافظه', { products: [['m1', 'ماش', 70]] });
  config.market.feedCacheMs = 60 * 1000;
  try {
    market.clearCache();
    assert.deepEqual((await feed()).body.items.map((p) => p.id), ['m1']);
    //  کالای تازه تا پایانِ همان مدت در خانه نیست (همان کهنگیِ «برشِ» دکان‌ها)
    await h.api('POST', '/api/sync/v1/push', {
      token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
      body: { device_id: s.device, schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'm2', type: 'insert', fields: { name: 'نخود', unit: 'کیلو', salePrice: 80 } }] },
    });
    await h.put('/api/shop/storefront/products/m2', { published: true }, { token: s.token });
    assert.deepEqual((await feed()).body.items.map((p) => p.id), ['m1'], 'از حافظه، بی پرسشِ تازه');
    market.clearCache();
    assert.deepEqual((await feed()).body.items.map((p) => p.id).sort(), ['m1', 'm2']);
    await h.put('/api/shop/market', { listed: false }, { token: s.token });
    assert.deepEqual((await feed()).body.items, [], 'بیرون رفتن از فهرست همان دم، حتی با حافظه');
  } finally {
    config.market.feedCacheMs = 0;
    await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
  }
});
