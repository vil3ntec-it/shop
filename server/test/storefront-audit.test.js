'use strict';
/**
 * ممیزیِ ویترین با چشمِ مشتری (۱۴۰۵/۰۸/۱۷) — قفلِ سمتِ سرور (رابطِ مرورگر: `web-store-audit.mjs`):
 *   ۵) WEB-01: لوگوی خودِ دکان روی کارتِ بازارچه (`logo`، پرچمِ `shop.storefront.media`)، نه فقط عکسِ یکی از کالاها؛
 *   ۶) WEB-02/CHK-11: نشانی و پینِ دکان (مهاجرتِ ۲۱۵) در تنظیمات و پاسخِ عمومی؛ rollback و دوباره نشستن؛
 *   ۹) WEB-02/07: «مرتب‌سازی» (ارزان/گران/تازه) با صفحه‌بندیِ بی‌تکرار، شمارِ کالا و «قیمتِ پیشینِ خط‌خورده» ِ کارت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const F = require('./image-fixtures');
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
     VALUES ($1,'audit-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'audit-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const flag = (scope, key, value = true) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

/** دکانِ روشن با ویترین و کالاهای منتشرشده: [id, نام, قیمتِ دفتر] */
async function liveShop(label, products) {
  const u = await h.newUser(`صاحب ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `au-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront']) await flag(s.shopId, k);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}` }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const dev = `dev-${uniq()}`;
  const ops = products.map(([id, name, price]) => ({
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
    fields: { name, unit: 'دانه', salePrice: price, category: 'خوراکی', active: true },
  }));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: dev, schema_version: 3, ops }, headers: { 'X-App': 'shop', 'X-Device': dev },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id] of products) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

test('مشکلِ ۶: نشانی و پینِ دکان — تنظیم، نمای عمومیِ `place` (بی کلیدِ شخصی‌نما)، برداشتن و سنجش', async () => {
  const s = await liveShop('نشانی', [['rice', 'برنج', 100]]);
  let pub = (await h.get(`/api/store/${s.slug}`)).body.store;
  assert.equal('place' in pub, false, 'بی نشانی ⇒ کلید نیست (همان پاسخِ پیشین)');
  const r = await h.put('/api/shop/storefront', { pickupAddress: '  کابل، کارتهٔ سه، سرکِ اول  ', pickupPin: { lat: 34.5281, lng: 69.1723 } }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.settings.pickupAddress, 'کابل، کارتهٔ سه، سرکِ اول', 'تنظیماتِ صاحب همان را برمی‌گرداند (برنامه)');
  assert.deepEqual(r.body.settings.pickupPin, { lat: 34.5281, lng: 69.1723 });
  pub = (await h.get(`/api/store/${s.slug}`)).body.store;
  assert.deepEqual(pub.place, { text: 'کابل، کارتهٔ سه، سرکِ اول', map: 'https://www.google.com/maps/search/?api=1&query=34.5281%2C69.1723' });
  //  WEB-23: نمای عمومی هیچ کلیدِ address/lat/lng/pickup ِ خام ندارد
  assert.deepEqual(JSON.stringify(pub).match(/"(address|lat|lng|pin|pickup(Address|Pin|_\w+))"/g), null);
  //  ذخیرهٔ بعدیِ بی کلید ⇒ همان نشانی
  assert.equal((await h.put('/api/shop/storefront', { title: 'نامِ تازه' }, { token: s.token })).body.settings.pickupAddress, 'کابل، کارتهٔ سه، سرکِ اول');
  for (const bad of [{ lat: 91, lng: 0 }, { lat: '34', lng: 69 }, { lat: 34 }, 'x']) {
    const b = await h.put('/api/shop/storefront', { pickupPin: bad }, { token: s.token });
    assert.equal(b.status, 400, JSON.stringify(bad));
    assert.equal(b.body.error.code, 'bad_pin');
  }
  //  بی پین ⇒ پیوندِ جست‌وجوی نام و نشانی
  assert.equal((await h.put('/api/shop/storefront', { pickupPin: null }, { token: s.token })).status, 200);
  pub = (await h.get(`/api/store/${s.slug}`)).body.store;
  assert.equal(pub.place.map, `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent('نامِ تازه کابل، کارتهٔ سه، سرکِ اول')}`);
  assert.equal((await h.put('/api/shop/storefront', { pickupAddress: '' }, { token: s.token })).status, 200);
  pub = (await h.get(`/api/store/${s.slug}`)).body.store;
  assert.equal('place' in pub, false, 'برداشته شد');
  const long = await h.put('/api/shop/storefront', { pickupAddress: 'ا'.repeat(400) }, { token: s.token });
  assert.equal(long.body.settings.pickupAddress.length, 200, 'تا ۲۰۰ نویسه');
});

test('مشکلِ ۹: مرتب‌سازی، صفحه‌بندیِ بی‌تکرار، شمارِ کالا و قیمتِ پیشینِ خط‌خورده', async () => {
  const s = await liveShop('ترتیب', [['a', 'الف', 50], ['b', 'ب', 10], ['c', 'ت', 30], ['d', 'ث', 20], ['e', 'ج', 40]]);
  //  قیمتِ آنلاینِ «ت» پایین‌تر از قیمتِ دکان (۳۰ ⇒ ۲۵)؛ «الف» بالاتر (۵۰ ⇒ ۶۰، خط‌خورده نه)
  assert.equal((await h.put('/api/shop/storefront/products/c', { published: true, onlinePriceMinor: 2500 }, { token: s.token })).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/a', { published: true, onlinePriceMinor: 6000 }, { token: s.token })).status, 200);
  const list = async (qs) => (await h.get(`/api/store/${s.slug}/products${qs}`)).body;
  const ids = (b) => b.items.map((p) => p.id);
  const all = await list('');
  assert.deepEqual(ids(all), ['a', 'b', 'c', 'd', 'e'], 'پیش‌فرض همان الفبا');
  assert.equal(all.total, 5, '«۵ کالا»');
  assert.deepEqual(ids(await list('?sort=price')), ['b', 'd', 'c', 'e', 'a'], 'ارزان‌ترین (قیمتِ آنلاین، وگرنه دفتر)');
  assert.deepEqual(ids(await list('?sort=-price')), ['a', 'e', 'c', 'd', 'b'], 'گران‌ترین');
  assert.deepEqual(ids(await list('?sort=nonsense')), ['a', 'b', 'c', 'd', 'e'], 'مقدارِ ناشناس ⇒ پیش‌فرض');
  //  صفحه‌بندی با ترتیب: بی تکرار و بی جاافتاده؛ شمار فقط در صفحهٔ نخست
  const p1 = await list('?sort=price&limit=2');
  assert.equal(p1.total, 5, 'شمارِ کلّ در صفحهٔ نخست');
  const p2 = await list(`?sort=price&limit=2&cursor=${encodeURIComponent(p1.nextCursor)}`);
  const p3 = await list(`?sort=price&limit=2&cursor=${encodeURIComponent(p2.nextCursor)}`);
  assert.deepEqual([...ids(p1), ...ids(p2), ...ids(p3)], ['b', 'd', 'c', 'e', 'a']);
  assert.equal(p3.nextCursor, null);
  assert.equal('total' in p2, false);
  const n1 = await list('?limit=2');
  const n2 = await list(`?limit=2&cursor=${encodeURIComponent(n1.nextCursor)}`);
  assert.deepEqual([...ids(n1), ...ids(n2)], ['a', 'b', 'c', 'd'], 'نشانگرِ نامِ پیشین همان کار می‌کند');
  assert.equal((await h.get(`/api/store/${s.slug}/products?sort=price&cursor=bad`)).status, 400);
  //  خط‌خورده فقط وقتی آنلاین ارزان‌تر از قیمتِ دکان است
  const c = all.items.find((p) => p.id === 'c');
  assert.equal(c.priceMinor, 2500);
  assert.equal(c.regularPriceMinor, 3000, 'قیمتِ پیشینِ خط‌خورده');
  assert.ok(all.items.filter((p) => p.id !== 'c').every((p) => p.regularPriceMinor === undefined), 'جز «ت» هیچ کارتی');
  //  ردیفِ «ارزان‌تر از قیمتِ دکان» (WEB-02) همان رضایتِ بازارچه را می‌خواهد
  const hl = (await h.get(`/api/store/${s.slug}/highlights`)).body;
  assert.ok(!JSON.stringify(hl).includes('regularPriceMinor'));
});

test('مشکلِ ۵: کارتِ بازارچه لوگوی خودِ دکان را دارد (پرچمِ media)، نه فقط عکسِ کالا', async () => {
  await flag('all', 'market.directory');
  const s = await liveShop('لوگو', [['rice', 'برنج', 100]]);
  assert.equal((await h.put('/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, { token: s.token })).status, 200);
  const card = async () => (await h.get('/api/market/shops')).body.shops.find((x) => x.slug === s.slug);
  assert.ok(await card(), 'دکان در بازارچه');
  assert.equal((await card()).logo, undefined, 'بی لوگو ⇒ کلید نیست');
  await flag(s.shopId, 'shop.storefront.media');
  const up = await h.raw('PUT', '/api/shop/storefront/media/logo', F.png(256, 256), { token: s.token, headers: { 'Content-Type': 'image/png' } });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  assert.equal((await card()).logo, `/api/store/${s.slug}/media/logo/1`);
  assert.equal((await h.download(`/api/store/${s.slug}/media/logo/1`)).status, 200);
  await flag(s.shopId, 'shop.storefront.media', false);
  assert.equal((await card()).logo, undefined, 'پرچمِ خاموش ⇒ همان پیشین');
});

test('مهاجرتِ ۲۱۵: rollback ستون‌ها را برمی‌دارد و دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const down = fs.readFileSync(path.join(dir, 'rollback', '215_storefront_place.down.sql'), 'utf8');
  const up = fs.readFileSync(path.join(dir, '215_storefront_place.sql'), 'utf8');
  const cols = async () => (await query(
    "SELECT column_name FROM information_schema.columns WHERE table_name='storefronts' AND column_name IN ('pickup_address','pickup_lat','pickup_lng')"
  )).rows.length;
  assert.equal(await cols(), 3);
  const mark = await one("SELECT * FROM schema_migrations WHERE version='215_storefront_place.sql'");
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await cols(), 0);
  assert.equal(await one("SELECT 1 AS x FROM schema_migrations WHERE version='215_storefront_place.sql'"), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await cols(), 3);
});
