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
 *      زیاد) یا سفارشِ لغوشده چیزی نمی‌سازد؛ سه شمارهٔ جدا ⇒ نمایش. بی رتبه‌بندیِ
 *      فروش میانِ دکان‌ها: هر دکان تا ۲ کالا، به نوبت به ترتیبِ رتبهٔ دکان؛ بازهٔ ثابتِ
 *      روزانه (سفارشِ امروز تا فردا شمرده نمی‌شود و در طولِ روز چیزی تکان نمی‌خورد).
 *   ۴) ⛔ «ارزان‌تر از قیمتِ دکان» فقط با `showDeals` ِ صاحبِ دکان (پیش‌فرض خاموش ⇒
 *      هیچ قیمتِ دفتری در پاسخ نیست؛ مدیر نمی‌تواند روشنش کند)، تا ۲ کالا از هر دکان.
 *   ۵) دسته، «نزدیکِ من» و دکان‌های پیشنهادی با عکسِ یکی از کالاهای خودشان.
 *   ۶) ⛔ حافظه: هر پاسخ کالاها را زنده می‌سنجد (منتشرنشده/جریمه همان دم بیرون)،
 *      یک‌پرواز، `only=lists`، و سقفِ ۳۰ در دقیقه.
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
const DAY = 24 * 60 * 60 * 1000;

async function ticketOf(phone) {
  const st = await h.post('/api/market/verify/start', { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post('/api/market/verify/check', { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}
/** سفارشِ واقعیِ ویترین با شمارهٔ تأییدشده؛ `ids` = کالاهای یک سفارش. */
async function orderOf(store, phone, ids) {
  const r = await h.post(`/api/store/${store.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [].concat(ids).map((productId) => ({ productId, qtyMilli: 1000 })),
  }, { headers: { 'Idempotency-Key': `fd-${uniq()}-key`, 'X-Store-Ticket': await ticketOf(phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}
/** «پرفروش» فقط از بازهٔ ثابتِ روزانه (تا آغازِ امروز) ⇒ سفارش‌های آزمون یک روز عقب. */
const backdate = (store) => query('UPDATE online_orders SET created_at = created_at - $1 WHERE shop_id = $2', [DAY, store.shopId]);

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

test('⛔ «پرفروش» فقط با k-گمنامی (۳ شمارهٔ جدا)؛ لغوشده نمی‌شمارد؛ «ارزان‌تر» فقط با خواستِ صاحبِ دکان و قیمتِ آنلاینِ کمتر', async () => {
  await setFlag('all', 'market.directory', true);
  const s = await liveStore('پرفروش', {
    products: [['rice', 'برنج', 120], ['oil', 'روغن', 300], ['tea', 'چای', 90], ['d1', 'لوبیا', 100], ['d2', 'نخود', 100], ['d3', 'عدس', 100]],
  });
  const order = (phone, productId) => orderOf(s, phone, productId);
  const popular = async () => { await backdate(s); return (await feed()).body.popular.map((p) => p.id); };

  for (let i = 0; i < 4; i++) await order('0791110001', 'rice');
  assert.deepEqual(await popular(), [], 'چهار سفارشِ یک نفر = یک نفر');
  await order('0791110002', 'rice');
  const third = await order('0791110003', 'rice');
  const cancel = await h.post(`/api/store/${s.slug}/orders/${third.token}/cancel`, {});
  assert.equal(cancel.status, 200, JSON.stringify(cancel.body));
  assert.deepEqual(await popular(), [], 'لغوشده نمی‌شمارد ⇒ هنوز دو شماره');
  await order('0791110004', 'rice');
  assert.deepEqual((await feed()).body.popular, [], 'سفارشِ امروز تا آغازِ فردا شمرده نمی‌شود (بازهٔ ثابتِ روزانه)');
  assert.deepEqual(await popular(), ['rice'], 'سه شمارهٔ جدا ⇒ «پرفروش»');
  assert.ok(!JSON.stringify((await feed()).body).includes('079111'), 'هیچ شماره‌ای در پاسخ نیست');

  //  ارزان‌تر از قیمتِ دکان: ⛔ پیش‌فرض خاموش ⇒ هیچ قیمتِ دفتری منتشر نمی‌شود
  await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 25000 }, { token: s.token });
  await h.put('/api/shop/storefront/products/tea', { published: true, onlinePriceMinor: 9500 }, { token: s.token });
  const off = (await feed()).body;
  assert.deepEqual(off.deals, [], 'بی خواستِ صاحبِ دکان «ارزان‌تر» خالی است');
  assert.ok(!JSON.stringify(off).includes('regularPriceMinor'), 'قیمتِ فروشِ دفتر هیچ‌جای پاسخ نیست');
  assert.ok(off.items.some((p) => p.id === 'oil' && p.priceMinor === 25000), 'خودِ کالا با قیمتِ آنلاین هست');
  assert.equal((await h.get('/api/shop/market', { token: s.token })).body.listing.showDeals, false);

  //  ⛔ فقط صاحبِ دکان: مدیر (settings.write) نمی‌تواند روشنش کند
  const mgr = await h.newUser('مدیرِ پرفروش');
  const code = (await h.post('/api/shop/staff-code', { role: 'manager' }, { token: s.token })).body.code;
  assert.equal((await h.post('/api/shop/staff/join', { code }, { token: mgr.accessToken })).status, 201);
  const deny = await h.put('/api/shop/market', { showDeals: true }, { token: mgr.accessToken });
  assert.equal(deny.status, 403, JSON.stringify(deny.body));
  assert.equal(deny.body.error.code, 'deals_owner_only');
  assert.equal((await h.put('/api/shop/market', { area: 'کارته پروان' }, { token: mgr.accessToken })).status, 200, 'بقیهٔ تنظیم با settings.write');
  assert.equal((await h.put('/api/shop/market', { showDeals: 'yes' }, { token: s.token })).status, 400);

  const on = await h.put('/api/shop/market', { showDeals: true }, { token: s.token });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.equal(on.body.listing.showDeals, true);
  const d = (await feed()).body.deals;
  assert.deepEqual(d.map((p) => [p.id, p.priceMinor, p.regularPriceMinor]), [['oil', 25000, 30000]],
    'فقط کالای ارزان‌تر، با قیمتِ دفتر؛ قیمتِ آنلاینِ گران‌تر تخفیف نیست');
  assert.deepEqual((await feed()).body.items.filter((p) => p.regularPriceMinor !== undefined).map((p) => [p.id, p.regularPriceMinor]),
    [['oil', 30000]], 'در شبکهٔ کالا هم همان قیمتِ دفتر فقط برای کالای ارزان‌تر');

  //  ⛔ هر دکان حداکثر ۲ کالا (درصدِ تخفیف فقط درونِ همان دکان)
  for (const id of ['d1', 'd2', 'd3']) {
    await h.put(`/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: 5000 }, { token: s.token });
  }
  assert.deepEqual((await feed()).body.deals.map((p) => p.id), ['d1', 'd2'], 'سه کالای ارزان‌تر ⇒ فقط دو');

  //  خاموش کردن همان دم
  assert.equal((await h.put('/api/shop/market', { showDeals: false }, { token: s.token })).status, 200);
  const off2 = (await feed()).body;
  assert.deepEqual(off2.deals, []);
  assert.ok(!JSON.stringify(off2).includes('regularPriceMinor'));
  assert.equal((await h.put('/api/shop/market', { showDeals: true }, { token: s.token })).status, 200);

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

test('کارتِ دکان: جلد از عکسِ کالای خودش، «تازه» فقط از تاریخِ واقعیِ ساخت، دلخواه‌ها (first) اول', async () => {
  await setFlag('all', 'market.directory', true);
  const a = await liveStore('جلد', { products: [['c1', 'قند', 50]] });
  const b = await liveStore('کهنه', { products: [['k1', 'نمک', 20]] });
  await query(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
     VALUES ('shop', $1, 'c1', $2, 'h2', 4, 1, $3)`,
    [a.shopId, Buffer.from([0xff, 0xd8, 0xff, 0xe0]), now()]
  );
  await query('UPDATE storefronts SET created_at = $1 WHERE shop_id = $2', [now() - 90 * 24 * 60 * 60 * 1000, b.shopId]);
  const r = await h.get('/api/market/shops');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const by = Object.fromEntries(r.body.shops.map((s) => [s.slug, s]));
  assert.equal(by[a.slug].cover, 'c1', 'جلد = عکسِ کالای خودِ دکان');
  assert.equal(by[b.slug].cover, null, 'بی عکس ⇒ بی جلد');
  assert.equal(by[a.slug].isNew, true);
  assert.equal(by[b.slug].isNew, false, 'ویترینِ ۹۰ روزه «تازه» نیست');
  assert.ok(r.body.shops.every((s) => s.rating === undefined), 'بی امتیازِ ساختگی');
  //  دلخواه‌های همین مرورگر اول، بی دخل در فهرست و دسته
  const f = await h.get(`/api/market/shops?first=${b.slug},bad%20slug,not-listed-x`);
  assert.equal(f.body.shops[0].slug, b.slug);
  assert.equal(f.body.total, r.body.total, 'first فقط ترتیب را عوض می‌کند');
  for (const s of [a, b]) await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('⛔ «پرفروش» بی رتبه‌بندیِ فروش میانِ دکان‌ها، روزانه؛ هر پاسخ زنده سنجیده می‌شود؛ یک‌پرواز؛ only=lists؛ سقفِ ۳۰ در دقیقه', async () => {
  await setFlag('all', 'market.directory', true);
  const config = require('../src/config');
  const market = require('../src/lib/market');
  const A = await liveStore('آ-پرفروش', { products: [['p1', 'برنج', 100], ['p2', 'روغن', 100], ['p3', 'چای', 100]] });
  const B = await liveStore('ب-کم‌فروش', { products: [['q1', 'نان', 10], ['q2', 'کلچه', 20]] });
  //  دکانِ «آ» سه کالای پرفروش (هر کدام ۴ شماره)، «ب» یک کالا با همان کمینهٔ ۳ شماره
  for (const ph of ['0792220001', '0792220002', '0792220003', '0792220004']) await orderOf(A, ph, ['p1', 'p2', 'p3']);
  for (const ph of ['0792220001', '0792220002', '0792220003']) await orderOf(B, ph, ['q1']);
  await backdate(A);
  await backdate(B);

  const pop = (await feed()).body.popular;
  assert.equal(pop.filter((p) => p.shop.slug === A.slug).length, 2, 'هر دکان حداکثر ۲ کالا، هرچه پرفروش‌تر');
  assert.deepEqual(pop.map((p) => p.shop.slug), [A.slug, B.slug, A.slug],
    'به نوبت و به ترتیبِ رتبهٔ دکان (نه حجمِ فروش): اولیِ هر دکان، بعد دومی');
  assert.ok(pop.some((p) => p.shop.slug === B.slug && p.id === 'q1'));

  config.market.feedCacheMs = 60 * 1000;
  try {
    market.clearCache();
    const ids = async () => (await feed()).body.popular.map(keyOf).sort();
    const day0 = await ids();
    //  ⛔ روزانه: سفارشِ تازهٔ «ب» (حتی پیش از امروز) تا روزِ بعد چیزی را تکان نمی‌دهد
    for (const ph of ['0792220005', '0792220006', '0792220007']) await orderOf(B, ph, ['q2']);
    await backdate(B);
    assert.deepEqual(await ids(), day0, 'در طولِ روز همان فهرست');
    market.clearCache();
    assert.ok((await ids()).includes(`${B.slug}/q2`), 'روزِ بعد (حافظهٔ تازه) دیده می‌شود');

    //  ⛔ سنجشِ زنده با حافظهٔ روشن: منتشرنشده و جریمهٔ «پنهان کردن» همان دم بیرون
    const first = (await feed()).body;
    const aIds = first.popular.filter((p) => p.shop.slug === A.slug).map((p) => p.id);
    assert.equal((await h.put(`/api/shop/storefront/products/${aIds[0]}`, { published: false }, { token: A.token })).status, 200);
    const after = (await feed()).body;
    assert.ok(!after.popular.some((p) => p.shop.slug === A.slug && p.id === aIds[0]), 'منتشرنشده از «پرفروش» بیرون');
    assert.ok(!after.items.some((p) => p.shop.slug === A.slug && p.id === aIds[0]), 'و از قفسهٔ حافظه‌دار هم');
    const at = now();
    const caseId = `tc-${uniq()}`;
    await query(
      `INSERT INTO trust_cases (id, shop_id, kind, subject, product_id, category, severity, reporter_key, created_at, updated_at)
       VALUES ($1,$2,'report','product',$4,'other','low','k',$3,$3)`, [caseId, A.shopId, at, aIds[1]]
    );
    await query(
      `INSERT INTO trust_sanctions (id, shop_id, case_id, kind, product_id, reason, created_by, created_at)
       VALUES ($1,$2,$3,'hide_listing',$4,'آزمون','adm',$5)`, [`ts-${uniq()}`, A.shopId, caseId, aIds[1], at]
    );
    const hidden = (await feed()).body;
    assert.ok(![...hidden.popular, ...hidden.items].some((p) => p.shop.slug === A.slug && p.id === aIds[1]), 'جریمهٔ «پنهان کردن» همان دم');

    //  ⛔ یک‌پرواز: پنج درخواستِ هم‌زمان با حافظهٔ خالی ⇒ هر قفسه یک بار، «پرفروش» یک بار
    market.clearCache();
    const s0 = market.stats();
    const all = await Promise.all([1, 2, 3, 4, 5].map(() => feed()));
    assert.ok(all.every((r) => r.status === 200));
    const s1 = market.stats();
    assert.equal(s1.shelves - s0.shelves, 2, 'دو دکان ⇒ دو قفسه، نه ده');
    assert.equal(s1.popular - s0.popular, 1);
    assert.equal(s1.deals - s0.deals, 1);

    //  only=lists: فقط دو فهرست، بی ساختنِ قفسه
    market.clearCache();
    const s2 = market.stats();
    const lists = (await feed('?only=lists')).body;
    assert.deepEqual([lists.items, lists.shops], [[], []]);
    assert.ok(lists.popular.length > 0);
    assert.equal(market.stats().shelves - s2.shelves, 0, 'بی قفسه');
    //  limit کوچک ⇒ قفسهٔ کم (نه ۲۴)
    market.clearCache();
    const s3 = market.stats();
    assert.equal((await feed('?limit=1')).body.items.length, 1);
    assert.ok(market.stats().shelves - s3.shelves <= 6);
  } finally {
    config.market.feedCacheMs = 0;
  }

  //  ⛔ سقفِ ۳۰ در دقیقه برای هر IP (سطلِ جدا از جست‌وجو)
  resetAll();
  for (let i = 0; i < 30; i++) assert.equal((await feed('?only=lists')).status, 200);
  assert.equal((await feed('?only=lists')).status, 429);
  assert.equal((await h.get(`/api/market/search?q=${encodeURIComponent('برنج')}`)).status, 200, 'جست‌وجو سطلِ خودش را دارد');
  resetAll();
  for (const s of [A, B]) await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});
