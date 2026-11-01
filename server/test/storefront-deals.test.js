'use strict';
/**
 * WEB-02 — «پیشنهادهای واقعی» ِ ویترینِ یک دکان (`GET /api/store/:slug/highlights` ⇒ `deals`).
 *
 * همان قاعدهٔ «ارزان‌تر از قیمتِ دکان» ِ بازارچه (`lib/market.js`، `loadDeals`):
 *   ۱) ⛔ پیش‌فرض خاموش: تا صاحبِ دکان `showDeals` را روشن نکرده، فهرست خالی است و قیمتِ
 *      فروشِ دفتر (`regularPriceMinor`) هیچ‌جای پاسخ نیست.
 *   ۲) «تخفیف» فقط وقتی قیمتِ آنلاین واقعاً کمتر از قیمتِ دفتر است؛ قیمتِ آنلاینِ گران‌تر یا
 *      برابر، و کالای بی قیمتِ آنلاین، تخفیف نیست.
 *   ۳) کالای منتشرنشده/پاک‌شده و کالای دکانِ دیگر هرگز در فهرستِ این دکان نیست.
 */
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

async function liveStore(label, products) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `d-${uniq()}` };
  for (const key of ['shop.orders', 'shop.storefront']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}` }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = `dev-${uniq()}`;
  const ops = products.map(([id, name, price]) => ({
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
    fields: { name, unit: 'دانه', salePrice: price, category: 'خوراکی' },
  }));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  return s;
}
const publish = async (s, id, extra = {}) => {
  const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true, ...extra }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const highlights = async (s) => {
  const r = await h.get(`/api/store/${s.slug}/highlights`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
};
const dealsOf = async (s) => (await highlights(s)).deals.map((p) => [p.id, p.priceMinor, p.regularPriceMinor]);

test('WEB-02: «ارزان‌تر از قیمتِ دکان» فقط با خواستِ صاحبِ دکان و فقط کالای واقعاً ارزان‌تر', async () => {
  const s = await liveStore('پیشنهاد', [['oil', 'روغن', 300], ['tea', 'چای', 100], ['rice', 'برنج', 120.5], ['salt', 'نمک', 20]]);
  await publish(s, 'oil', { onlinePriceMinor: 25000 });   // ۳۰۰ ⇒ ۲۵۰: ارزان‌تر
  await publish(s, 'tea', { onlinePriceMinor: 12000 });   // ۱۰۰ ⇒ ۱۲۰: گران‌تر، تخفیف نیست
  await publish(s, 'salt', { onlinePriceMinor: 2000 });   // برابر: تخفیف نیست
  await publish(s, 'rice');                                // بی قیمتِ آنلاین

  //  ⛔ پیش‌فرض خاموش ⇒ هیچ قیمتِ دفتری بیرون نمی‌رود
  const off = await highlights(s);
  assert.deepEqual(off.deals, [], 'بی خواستِ صاحبِ دکان «پیشنهاد» خالی است');
  assert.ok(!JSON.stringify(off).includes('regularPriceMinor'), 'قیمتِ فروشِ دفتر هیچ‌جای پاسخ نیست');
  assert.ok(off.newArrivals.some((p) => p.id === 'oil' && p.priceMinor === 25000), 'خودِ کالا با قیمتِ آنلاین در «تازه‌ها» هست');

  const on = await h.put('/api/shop/market', { showDeals: true }, { token: s.token });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.deepEqual(await dealsOf(s), [['oil', 25000, 30000]], 'فقط کالای ارزان‌تر، با قیمتِ دفتر');
  const hl = await highlights(s);
  assert.ok(!hl.newArrivals.some((p) => p.regularPriceMinor !== undefined), '«تازه‌ها» قیمتِ دفتر ندارد');

  //  ترتیب: بیشترین تخفیفِ نسبی اول
  await publish(s, 'rice', { onlinePriceMinor: 6000 });   // ۱۲۰٫۵ ⇒ ۶۰: نصف
  assert.deepEqual(await dealsOf(s), [['rice', 6000, 12050], ['oil', 25000, 30000]]);

  //  برداشتن از ویترین ⇒ همان دم بیرون؛ قیمتِ آنلاینِ تازهٔ گران‌تر ⇒ دیگر تخفیف نیست
  await h.put('/api/shop/storefront/products/rice', { published: false }, { token: s.token });
  await publish(s, 'oil', { onlinePriceMinor: 31000 });
  assert.deepEqual(await dealsOf(s), [], 'منتشرنشده و گران‌ترشده بیرون');

  //  خاموش کردن ⇒ دوباره هیچ
  await publish(s, 'oil', { onlinePriceMinor: 25000 });
  assert.equal((await dealsOf(s)).length, 1);
  assert.equal((await h.put('/api/shop/market', { showDeals: false }, { token: s.token })).status, 200);
  assert.deepEqual(await dealsOf(s), []);
});

test('WEB-02: «پیشنهاد» ِ دکانِ دیگر در ویترینِ این دکان نیست؛ مدیرِ دکان آن را روشن نمی‌کند', async () => {
  const a = await liveStore('الف', [['oil', 'روغنِ الف', 300]]);
  const b = await liveStore('ب', [['oil', 'روغنِ ب', 300], ['ghee', 'روغنِ زرد', 500]]);
  await publish(a, 'oil');
  await publish(b, 'oil', { onlinePriceMinor: 20000 });
  await publish(b, 'ghee', { onlinePriceMinor: 40000 });
  assert.equal((await h.put('/api/shop/market', { showDeals: true }, { token: b.token })).status, 200);

  assert.deepEqual(await dealsOf(a), [], 'دکانِ الف خودش چیزی ارزان‌تر ندارد و از ب هم نمی‌گیرد');
  assert.deepEqual((await dealsOf(b)).map((x) => x[0]), ['oil', 'ghee']);

  //  ⛔ همان سدِّ بازارچه: فقط صاحبِ دکان قیمتِ دفتر را منتشر می‌کند
  const mgr = await h.newUser('مدیرِ الف');
  const code = (await h.post('/api/shop/staff-code', { role: 'manager' }, { token: a.token })).body.code;
  assert.equal((await h.post('/api/shop/staff/join', { code }, { token: mgr.accessToken })).status, 201);
  await publish(a, 'oil', { onlinePriceMinor: 10000 });
  const deny = await h.put('/api/shop/market', { showDeals: true }, { token: mgr.accessToken });
  assert.equal(deny.status, 403, JSON.stringify(deny.body));
  assert.deepEqual(await dealsOf(a), [], 'بی صاحبِ دکان هیچ');
});
