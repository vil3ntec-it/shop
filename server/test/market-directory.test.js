'use strict';
/**
 * بازارچهٔ عمومی — فهرستِ دکان‌ها، جست‌وجوی کالا در همهٔ ویترین‌ها و
 * «سفارش‌های من» (WEB-01/02/05، §۲۳، §۳۱.۱، §۳۲.۳؛ `lib/market.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `market.directory` (all) همهٔ `/api/market/*` ۴۰۴.
 *   ۲) ⛔ جداییِ دکان‌ها و حریم: ویترینِ خاموش/بی پرچم/بی اشتراک، دکانِ معلق،
 *      «نمی‌خواهم در فهرست باشم»، پرچمِ بازارچهٔ خاموشِ همان دکان و دکانِ بی
 *      کالا هرگز دیده نمی‌شوند؛ کالای منتشرنشده، غیرفعال یا حذف‌شده هرگز در
 *      جست‌وجو نیست؛ پاسخ هیچ شناسهٔ داخلی، شماره، ایمیل یا مختصاتی ندارد.
 *   ۳) «نزدیکِ من» فقط فاصلهٔ گردشده؛ دسته و جست‌وجو.
 *   ۴) «سفارش‌های من» فقط با شمارهٔ تأییدشده و فقط همان شماره.
 *   ۵) سقفِ درخواستِ جست‌وجو (۴۲۹).
 *   ۶) موجودیِ ویترین با `shop.catalog` از دفترِ سرور؛ خاموش ⇒ «در حال بررسی».
 */
process.env.RATE_MARKET_SEARCH_MAX = '40';
process.env.MARKET_GATE_CACHE_MS = '0';
//  آزمون‌های «همان دم» بی برش؛ آزمونِ برشِ یک‌پرواز خودش زمانش را می‌گذارد
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
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

const push = (token, ops, device) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields, type = 'insert') => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
});

/**
 * دکانِ روشن با ویترین؛ `products` = [شناسه, نام, قیمت, منتشر؟]. بازارچه فقط با
 * خواستِ صاحب دکان است ⇒ پیش‌فرضِ این‌جا `listed: true`؛ `listing: null` ⇒ بی ردیف.
 */
async function liveStore(label, { products, settings = {}, listing = {} } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `m-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, tagline: 'کالای روزانه', contactPhone: '0700000001',
    deliveryFeeMinor: 5000, ...settings,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const list = products || [['rice', 'برنج', 120, true], ['oil', 'روغن', 300, true], ['soap', 'صابون', 40, false]];
  const device = `dev-${uniq()}`;
  const p = await push(o.token, list.map(([id, name, price]) => op(id, { name, unit: 'دانه', salePrice: price, category: 'خوراکی' })), device);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id, , , pub] of list) {
    if (!pub) continue;
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  if (listing) {
    const r = await h.put('/api/shop/market', { listed: true, ...listing }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug, device };
}

const slugsOf = (r) => r.body.shops.map((s) => s.slug).sort();

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچمِ market.directory: همهٔ درهای بازارچه ۴۰۴ و ویترینِ دکان همان قبلی', async () => {
  const s = await liveStore('خاموش', { listing: { category: 'grocery', area: 'کارتهٔ سه' } });
  for (const r of [
    await h.get('/api/market'),
    await h.get('/api/market/shops'),
    await h.get('/api/market/search?q=برنج'),
    await h.get('/api/market/orders'),
    await h.post('/api/market/verify/start', { phone: '0791112233' }),
  ]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'market_not_found');
  }
  //  پرچمِ یک دکان بازارچه را باز نمی‌کند؛ فقط scope ِ all
  await setFlag(s.shopId, 'market.directory', true);
  assert.equal((await h.get('/api/market')).status, 404);
  const page = await h.get(`/api/store/${s.slug}`);
  assert.equal(page.status, 200, 'ویترینِ خودِ دکان دست نخورده');
  assert.equal(page.body.market.available, false, 'پیوندِ بازارچه فقط با بازارچهٔ باز');
  await setFlag(s.shopId, 'market.directory', null);
  await setFlag('all', 'market.directory', true);
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.market.available, true);
  await setFlag('all', 'market.directory', false);

  //  سبدِ مهمان با شناسه تازه می‌شود: فقط منتشرشده، بی تکرار، بی شناسهٔ بدشکل
  const ids = await h.get(`/api/store/${s.slug}/products?ids=rice,soap,rice,${encodeURIComponent('bad id')},nope`);
  assert.equal(ids.status, 200, JSON.stringify(ids.body));
  assert.deepEqual(ids.body.items.map((p) => p.id), ['rice']);
  assert.equal(ids.body.items[0].priceMinor, 12000);
  assert.equal((await h.get(`/api/store/${s.slug}/products?ids=`)).body.items.length, 0);
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

/* ══════════════════════════════════════════════════════════════════
   ۲) جداییِ دکان‌ها و حریم
   ══════════════════════════════════════════════════════════════════ */

test('فهرست فقط دکان‌های باز، در فهرست و کالادار؛ هیچ دادهٔ خصوصی', async () => {
  await setFlag('all', 'market.directory', true);
  const a = await liveStore('الف', { listing: { category: 'grocery', area: 'کارتهٔ سه، کابل' } });
  const b = await liveStore('ب', { listing: { category: 'bakery', area: 'شهرِ نو' } });
  const unlisted = await liveStore('نخواسته', { listing: { listed: false } });
  const off = await liveStore('خاموش‌شده');
  await h.put('/api/shop/storefront', { enabled: false }, { token: off.token });
  const noFlag = await liveStore('بی‌پرچم');
  await setFlag(noFlag.shopId, 'shop.storefront', false);
  const optOut = await liveStore('بیرون‌مانده');
  await setFlag(optOut.shopId, 'market.directory', false);
  const empty = await liveStore('بی‌کالا', { products: [['x1', 'چیزی', 10, false]] });
  const suspended = await liveStore('معلق');
  await query("UPDATE shops SET status = 'disabled' WHERE id = $1", [suspended.shopId]);

  const r = await h.get('/api/market/shops');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(slugsOf(r), [a.slug, b.slug].sort());
  assert.equal(r.body.total, 2);

  const card = r.body.shops.find((s) => s.slug === a.slug);
  assert.equal(card.title, 'ویترینِ الف');
  assert.equal(card.category, 'grocery');
  assert.equal(card.categoryLabel, 'سوپرمارکت و خواربار');
  assert.equal(card.area, 'کارتهٔ سه، کابل');
  assert.equal(card.deliveryFeeMinor, 5000);
  assert.equal(card.productCount, 2, 'فقط کالای منتشرشده شمرده می‌شود');
  assert.ok(['open_online', 'open_unconfirmed', 'declared_until', 'closed', 'offline'].includes(card.status.badge));
  assert.ok(['accepting', 'delayed', 'later'].includes(card.status.orderAcceptance));
  assert.equal(card.distanceKm, null);

  //  حریم: هیچ شناسهٔ داخلی، شماره، ایمیل، نامِ صاحب یا مختصات
  const all = JSON.stringify(r.body);
  for (const s of [a, b, unlisted, off, noFlag, optOut, empty, suspended]) {
    assert.ok(!all.includes(s.shopId), 'شناسهٔ داخلیِ دکان بیرون نمی‌رود');
    assert.ok(!all.includes(s.email), 'ایمیلِ صاحب بیرون نمی‌رود');
  }
  for (const bad of ['0700000001', '+93700000001', 'contactPhone', 'shop_id', 'shopId', 'lat', 'lng', 'geo', 'owner']) {
    assert.ok(!all.includes(bad), `«${bad}» در پاسخ نیست`);
  }

  //  نمای اول: دسته‌ها فقط با دکانِ واقعی
  const ov = await h.get('/api/market');
  assert.equal(ov.status, 200);
  assert.equal(ov.body.shopCount, 2);
  assert.deepEqual(ov.body.categories.map((c) => c.key).sort(), ['bakery', 'grocery']);
  assert.equal(typeof ov.body.verification.available, 'boolean');

  //  جست‌وجوی کالا: فقط منتشرشده و فقط دکان‌های باز
  const s1 = await h.get(`/api/market/search?q=${encodeURIComponent('برن')}`);
  assert.equal(s1.status, 200, JSON.stringify(s1.body));
  assert.deepEqual(s1.body.products.map((p) => p.shop.slug).sort(), [a.slug, b.slug].sort());
  assert.ok(s1.body.products.every((p) => p.id === 'rice' && p.priceMinor === 12000));
  const s2 = await h.get(`/api/market/search?q=${encodeURIComponent('صابون')}`);
  assert.equal(s2.body.products.length, 0, 'کالای منتشرنشده هرگز');
  const s3 = await h.get(`/api/market/search?q=${encodeURIComponent('چیزی')}`);
  assert.equal(s3.body.products.length, 0, 'کالای دکانِ بیرون از فهرست هرگز');
  const sAll = JSON.stringify(s1.body);
  for (const s of [a, b]) assert.ok(!sAll.includes(s.shopId));
  assert.ok(!sAll.includes('0700000001'));

  //  کالای غیرفعال/حذف‌شده همان لحظه بیرون می‌رود
  await push(a.token, [op('rice', { active: false }, 'update')], a.device);
  await push(b.token, [op('rice', {}, 'delete')], b.device);
  const s4 = await h.get(`/api/market/search?q=${encodeURIComponent('برنج')}`);
  assert.equal(s4.body.products.length, 0, JSON.stringify(s4.body.products));
  await push(a.token, [op('rice', { active: true }, 'update')], a.device);

  //  برداشتنِ انتشار ⇒ همان لحظه
  await h.put('/api/shop/storefront/products/oil', { published: false }, { token: a.token });
  await h.put('/api/shop/storefront/products/oil', { published: false }, { token: b.token });
  const s5 = await h.get(`/api/market/search?q=${encodeURIComponent('روغن')}`);
  assert.equal(s5.body.products.length, 0);

  //  خاموش کردنِ ویترینِ یک دکان ⇒ همان لحظه از فهرست و جست‌وجو بیرون
  await h.put('/api/shop/storefront', { enabled: false }, { token: b.token });
  assert.deepEqual(slugsOf(await h.get('/api/market/shops')), [a.slug]);
  //  دکانِ «ب» دیگر کالایی هم ندارد؛ «الف» برنجِ فعال دارد
  const s6 = await h.get(`/api/market/search?q=${encodeURIComponent('برنج')}`);
  assert.deepEqual(s6.body.products.map((p) => p.shop.slug), [a.slug]);

  //  پاک‌سازی برای آزمون‌های بعد: همه بیرون
  for (const s of [a, unlisted, optOut, empty]) await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('دسته، جست‌وجو در نام و ناحیه، عبارتِ هم‌نامِ دسته و «نزدیکِ من» با فاصلهٔ گردشده', async () => {
  await setFlag('all', 'market.directory', true);
  const near = await liveStore('نزدیک', { listing: { category: 'bakery', area: 'شهرِ نو', location: { lat: 34.5312, lng: 69.1661 } } });
  const far = await liveStore('دور', { listing: { category: 'grocery', area: 'خیرخانه', location: { lat: 34.5853, lng: 69.1201 } } });
  const nowhere = await liveStore('بی‌مکان', { listing: { category: 'grocery' } });

  const byCat = await h.get('/api/market/shops?category=grocery');
  assert.deepEqual(slugsOf(byCat), [far.slug, nowhere.slug].sort());
  assert.equal((await h.get('/api/market/shops?category=nope')).status, 400);

  const byArea = await h.get(`/api/market/shops?q=${encodeURIComponent('خیر خانه'.replace(' ', ''))}`);
  assert.deepEqual(slugsOf(byArea), [far.slug]);

  const geo = await h.get('/api/market/shops?lat=34.53&lng=69.17');
  assert.equal(geo.status, 200);
  const d = Object.fromEntries(geo.body.shops.map((s) => [s.slug, s.distanceKm]));
  assert.equal(d[nowhere.slug], null, 'دکانِ بی مکان فاصله ندارد');
  assert.ok(d[near.slug] >= 0.5 && d[near.slug] <= 1, `نزدیک: ${d[near.slug]}`);
  assert.ok(d[far.slug] > 5 && d[far.slug] < 9, `دور: ${d[far.slug]}`);
  assert.ok(Number.isInteger(d[far.slug] * 2), 'گردشده به نیم کیلومتر');
  //  وضعیتِ هر سه یکی است ⇒ ترتیب با فاصله؛ بی مکان آخر
  const order = geo.body.shops.map((s) => s.slug);
  assert.ok(order.indexOf(near.slug) < order.indexOf(far.slug) && order.indexOf(far.slug) < order.indexOf(nowhere.slug), order.join());
  const txt = h.withoutEpochs(JSON.stringify(geo.body), order);
  for (const secret of ['34.5', '69.1', '3453', '6916', '6912']) {
    assert.ok(!txt.includes(secret), `مختصاتِ دکان بیرون نمی‌رود («${secret}»)`);
  }
  assert.equal((await h.get('/api/market/shops?lat=95&lng=10')).status, 400);
  assert.equal((await h.get('/api/market/shops?lat=34')).status, 400);

  const cat = await h.get(`/api/market/search?q=${encodeURIComponent('نانوایی')}`);
  assert.equal(cat.body.category, 'bakery', 'عبارتِ هم‌نامِ دسته ⇒ همان دسته (WEB-05)');
  assert.equal((await h.get('/api/market/search?q=a')).status, 400);

  const page = await h.get('/api/market/shops?limit=1');
  assert.equal(page.body.shops.length, 1);
  assert.equal(page.body.total, 3);
  assert.equal(page.body.nextOffset, 1);
  const page2 = await h.get('/api/market/shops?limit=1&offset=2');
  assert.equal(page2.body.nextOffset, null);

  for (const s of [near, far, nowhere]) await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('تنظیمِ دکان: اعتبارسنجی، موقعیتِ درشت، قفلِ پرچمِ ویترین و نقش', async () => {
  const s = await liveStore('تنظیم', { listing: null });
  const get = await h.get('/api/shop/market', { token: s.token });
  assert.equal(get.status, 200, JSON.stringify(get.body));
  assert.equal(get.body.listing.listed, false, 'نبودِ ردیف = بیرون از فهرست (فقط با خواستِ صاحب دکان)');
  assert.ok(get.body.listing.categories.length >= 10);

  assert.equal((await h.put('/api/shop/market', { category: 'casino' }, { token: s.token })).body.error.code, 'bad_category');
  assert.equal((await h.put('/api/shop/market', { listed: 'yes' }, { token: s.token })).body.error.code, 'bad_listed');
  assert.equal((await h.put('/api/shop/market', { location: { lat: 200, lng: 1 } }, { token: s.token })).body.error.code, 'bad_location');
  const ok = await h.put('/api/shop/market', {
    category: 'dairy', area: `  ${'الف'.repeat(40)}  `, location: { lat: 34.55531, lng: 69.20749 },
  }, { token: s.token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.listing.area.length, 60);
  assert.deepEqual(ok.body.listing.location, { lat: 34.56, lng: 69.21 }, 'درشت: یک‌صدمِ درجه');
  const row = await one('SELECT geo_lat, geo_lng FROM market_listings WHERE shop_id=$1', [s.shopId]);
  assert.deepEqual([Number(row.geo_lat), Number(row.geo_lng)], [3456, 6921]);
  //  فقط فیلدِ آمده عوض می‌شود
  const part = await h.put('/api/shop/market', { listed: false }, { token: s.token });
  assert.equal(part.body.listing.category, 'dairy');
  assert.equal(part.body.listing.listed, false);
  const cleared = await h.put('/api/shop/market', { location: null }, { token: s.token });
  assert.equal(cleared.body.listing.location, null);
  const audit = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='market.listing' ORDER BY created_at DESC LIMIT 1", [s.shopId]);
  assert.ok(audit && !JSON.stringify(audit.detail).includes('34.5'), 'موقعیت در سابقه نمی‌نشیند');

  //  شاگرد (بی settings.write) نمی‌نویسد
  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), s.shopId, staff.user.id, now()]
  );
  assert.equal((await h.put('/api/shop/market', { listed: true }, { token: staff.accessToken })).status, 403);
  assert.equal((await one('SELECT listed FROM market_listings WHERE shop_id=$1', [s.shopId])).listed, false);

  //  بی توکن ⇒ ۴۰۱؛ بی پرچمِ ویترین ⇒ ۴۰۳
  assert.equal((await h.get('/api/shop/market')).status, 401);
  await setFlag(s.shopId, 'shop.storefront', false);
  const no = await h.put('/api/shop/market', { listed: true }, { token: s.token });
  assert.equal(no.status, 403);
  assert.equal(no.body.error.code, 'storefront_disabled');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token }).catch(() => {});
});

/* ══════════════════════════════════════════════════════════════════
   ۳) «سفارش‌های من» از همهٔ ویترین‌ها
   ══════════════════════════════════════════════════════════════════ */

test('سفارش‌های من: فقط با بلیت، فقط همان شماره، از هر ویترینِ باز', async () => {
  await setFlag('all', 'market.directory', true);
  const a = await liveStore('سفارشِ الف');
  const b = await liveStore('سفارشِ ب', { listing: { listed: false } });
  const ticketOf = async (phone) => {
    const s = await h.post('/api/market/verify/start', { phone });
    assert.equal(s.status, 200, JSON.stringify(s.body));
    const c = await h.post('/api/market/verify/check', { phone, code: s.body.devCode });
    assert.equal(c.status, 200, JSON.stringify(c.body));
    return c.body.ticket;
  };
  const mine = await ticketOf('0791112233');
  const other = await ticketOf('0792223344');
  const order = (slug, ticket, phone) => h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `mk-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal((await order(a.slug, mine, '0791112233')).status, 201);
  assert.equal((await order(b.slug, mine, '0791112233')).status, 201, 'بلیتِ بازارچه در ویترینِ هر دکان هم معتبر است');
  assert.equal((await order(a.slug, other, '0792223344')).status, 201);

  const none = await h.get('/api/market/orders');
  assert.equal(none.status, 401);
  assert.equal(none.body.error.code, 'verification_required');
  assert.equal((await h.get('/api/market/orders', { headers: { 'X-Store-Ticket': `${mine}x` } })).status, 401);

  const r = await h.get('/api/market/orders', { headers: { 'X-Store-Ticket': mine } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.orders.map((o) => o.store.slug).sort(), [a.slug, b.slug].sort(),
    'دکانِ بیرون از فهرست هم سفارشِ خودِ مشتری را نشان می‌دهد');
  assert.ok(r.body.orders.every((o) => o.totalMinor === 12000 && o.itemsCount === 1));
  const txt = JSON.stringify(r.body);
  assert.ok(!txt.includes('0792223344') && !txt.includes('+93792223344') && !txt.includes(a.shopId));
  //  توکنِ پیگیری همان توکنِ ویترین است
  const o = r.body.orders.find((x) => x.store.slug === a.slug);
  const tr = await h.get(`/api/store/${a.slug}/orders/${o.token}`);
  assert.equal(tr.status, 200, JSON.stringify(tr.body));
  //  ویترینِ خاموش ⇒ سفارشش هم از فهرست بیرون (همان ۴۰۴ ِ پیگیری)
  await h.put('/api/shop/storefront', { enabled: false }, { token: b.token });
  const r2 = await h.get('/api/market/orders', { headers: { 'X-Store-Ticket': mine } });
  assert.deepEqual(r2.body.orders.map((x) => x.store.slug), [a.slug]);
  await h.put('/api/shop/storefront', { enabled: false }, { token: a.token });
});

/* ══════════════════════════════════════════════════════════════════
   ۴) سقفِ درخواست و موجودی
   ══════════════════════════════════════════════════════════════════ */

test('سقفِ جست‌وجو به‌ازای IP ⇒ ۴۲۹ (ضربه‌ای: ۳۰ در دقیقه — امن برای CGNAT ِ اپراتور — کنارِ سقفِ ربع‌ساعته)', async () => {
  await setFlag('all', 'market.directory', true);
  const config = require('../src/config');
  assert.equal(config.rateLimit.marketSearchBurstMax, 30);
  const statuses = [];
  for (let i = 0; i < 31; i++) statuses.push((await h.get(`/api/market/search?q=${encodeURIComponent('برنج')}`)).status);
  assert.deepEqual(statuses, [...Array(30).fill(200), 429]);
  //  فهرست سطلِ خودش را دارد
  assert.equal((await h.get('/api/market/shops')).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) بازبینیِ امنیتی: فهرست فقط با خواستِ صاحب دکان؛ برشِ یک‌پرواز؛ نمایهٔ جست‌وجو
   ══════════════════════════════════════════════════════════════════ */

test('⛔ فهرست فقط با خواستِ صاحب دکان: ویترینِ روشنِ بی ردیف در بازارچه نیست تا خودش بخواهد', async () => {
  await setFlag('all', 'market.directory', true);
  const silent = await liveStore('بی‌خواست', { listing: null, products: [['lentil', 'عدس‌ویژه', 90, true]] });
  const shops = await h.get('/api/market/shops');
  assert.ok(!slugsOf(shops).includes(silent.slug), 'بی خواست در فهرست نیست');
  assert.equal((await h.get(`/api/market/search?q=${encodeURIComponent('عدس‌ویژه')}`)).body.products.length, 0);
  assert.equal((await h.get(`/api/store/${silent.slug}`)).status, 200, 'ویترینِ خودش با نشانیِ خودش باز است');
  const row = await one('SELECT listed FROM market_listings WHERE shop_id=$1', [silent.shopId]);
  assert.equal(row, null);
  //  صاحب دکان می‌خواهد ⇒ همان دم
  assert.equal((await h.put('/api/shop/market', { listed: true }, { token: silent.token })).status, 200);
  assert.ok(slugsOf(await h.get('/api/market/shops')).includes(silent.slug));
  assert.equal((await h.get(`/api/market/search?q=${encodeURIComponent('عدس‌ویژه')}`)).body.products.length, 1);
  await h.put('/api/shop/storefront', { enabled: false }, { token: silent.token });
});

test('⛔ DoS: برشِ دکان‌ها یک‌پرواز و ماندگار؛ جست‌وجو از نمایهٔ باریک با سنجشِ زنده', async () => {
  await setFlag('all', 'market.directory', true);
  const market = require('../src/lib/market');
  const config = require('../src/config');
  const a = await liveStore('برش', { products: [['dal', 'ماشِ‌سبز', 70, true], ['tea', 'چای‌سبز', 150, true]] });
  const saved = config.market.eligibleCacheMs;
  config.market.eligibleCacheMs = 60_000;
  try {
    market.clearCache();
    const before = market.stats().builds;
    //  ۲۰ درخواستِ هم‌زمان و ۱۰ پیاپی ⇒ یک بار ساختن
    const burst = await Promise.all(Array.from({ length: 20 }, () => h.get('/api/market/shops')));
    assert.ok(burst.every((r) => r.status === 200 && slugsOf(r).includes(a.slug)));
    for (let i = 0; i < 5; i++) assert.equal((await h.get('/api/market')).status, 200);
    for (let i = 0; i < 5; i++) assert.equal((await h.get(`/api/market/search?q=${encodeURIComponent('سبز')}`)).status, 200);
    assert.equal(market.stats().builds - before, 1, 'یک ساختن برای ۳۰ درخواست');

    //  نمایه: هر دو کالا در `market_search`
    const idx = await query('SELECT product_id FROM market_search WHERE shop_id=$1 ORDER BY product_id', [a.shopId]);
    assert.deepEqual(idx.rows.map((r) => r.product_id), ['dal', 'tea']);
    //  نام عوض شد: نامِ کهنه همان دم بیرون (سنجشِ زنده)، نامِ تازه پس از برشِ بعد (نمایه)
    await push(a.token, [op('tea', { name: 'قهوه‌تلخ' }, 'update')], a.device);
    const old = await h.get(`/api/market/search?q=${encodeURIComponent('چای')}`);
    assert.equal(old.body.products.length, 0, 'نتیجهٔ کهنهٔ نمایه با دادهٔ زنده رد می‌شود');
    assert.equal((await h.get(`/api/market/search?q=${encodeURIComponent('قهوه')}`)).body.products.length, 0,
      'جست‌وجو فقط از نمایه است (نه پیمایشِ همهٔ کالاها)');
    market.clearCache();
    const fresh = await h.get(`/api/market/search?q=${encodeURIComponent('قهوه')}`);
    assert.deepEqual(fresh.body.products.map((p) => p.id), ['tea']);
    //  خاموش کردنِ ویترین با برشِ ماندگار هم همان دم بیرون می‌برد (سنجشِ زنده)
    await h.put('/api/shop/storefront', { enabled: false }, { token: a.token });
    assert.ok(!slugsOf(await h.get('/api/market/shops')).includes(a.slug));
    assert.equal((await h.get(`/api/market/search?q=${encodeURIComponent('ماش')}`)).body.products.length, 0);
  } finally {
    config.market.eligibleCacheMs = saved;
    market.clearCache();
  }
});

test('موجودیِ ویترین: بی shop.catalog «در حال بررسی»؛ با آن از دفترِ سرور و هرگز «موجود» ِ بی‌شاهد', async () => {
  const s = await liveStore('موجودی');
  const off = await h.get(`/api/store/${s.slug}/products`);
  assert.equal(off.status, 200);
  assert.ok(off.body.items.every((p) => p.stock.status === 'unverified'));
  await setFlag(s.shopId, 'shop.catalog', true);
  const on = await h.get(`/api/store/${s.slug}/products`);
  assert.equal(on.status, 200, JSON.stringify(on.body));
  for (const p of on.body.items) {
    assert.ok(['in_stock', 'in_warehouse', 'out_of_stock', 'unverified', 'unavailable'].includes(p.stock.status), JSON.stringify(p.stock));
    //  هیچ ورودِ انباری نیامده ⇒ «موجود» نیست
    assert.notEqual(p.stock.status, 'in_stock');
    assert.equal(p.stock.sellableMilli, undefined, 'عددِ دقیق فقط با showQty = exact');
  }
  await setFlag(s.shopId, 'shop.catalog', false);
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});
