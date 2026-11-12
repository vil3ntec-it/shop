'use strict';
/**
 * S125 — «کاتالوگِ بزرگ یک‌جا بار نمی‌شود»: هر فهرستِ کالا سقفِ صفحهٔ ۱۰۰ دارد، هر `limit` ِ بزرگ‌تری بخواهند.
 *   ۱) ویترینِ همگانی (`/api/store/:slug/products`): ۱۳۰ کالای منتشرشده با `limit=500` ⇒ ۱۰۰ و نشانگرِ صفحهٔ بعد؛ صفحهٔ دوم ۳۰
 *      و هیچ کالایی دو بار یا جا افتاده نیست.
 *   ۲) فهرستِ «در ویترین» ِ برنامه (`/api/shop/storefront/products`) و کاتالوگِ کارمند (`/api/shop/catalog/products`) هم ۱۰۰.
 *   ۳) `limit` ِ نادرست (صفر، منفی، متن) ⇒ صفحهٔ پیش‌فرض، نه خطا و نه بی‌سقف.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const N = 130;

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

const setFlag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

test('S125: ۱۳۰ کالا ⇒ هر فهرست حداکثر ۱۰۰ در یک صفحه، صفحهٔ دوم بقیه، بی تکرار و جاافتاده', async () => {
  const u = await h.newUser('دکانِ بزرگ', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ بزرگ' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const slug = `cap-${uniq()}`;
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(shopId, k, true);
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ بزرگ', minOrderMinor: 0 }, { token: u.accessToken })).status, 200);
  const ids = Array.from({ length: N }, (_, i) => `p${String(i).padStart(3, '0')}`);
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-cap' },
    body: { device_id: 'dev-cap', schema_version: 2, ops: ids.map((id, i) => ({ op_id: `op-${id}-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { name: `کالای ${i}`, unit: 'دانه', salePrice: 10 + i, category: 'خوراکی' } })) },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body).slice(0, 200));
  for (const id of ids) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: u.accessToken })).status, 200);

  //  ۱) ویترینِ همگانی
  const p1 = (await h.get(`/api/store/${slug}/products?limit=500`)).body;
  assert.equal(p1.items.length, 100, 'صفحهٔ نخست با limit=500 فقط ۱۰۰');
  assert.ok(p1.nextCursor, 'نشانگرِ صفحهٔ بعد');
  const p2 = (await h.get(`/api/store/${slug}/products?limit=500&cursor=${encodeURIComponent(p1.nextCursor)}`)).body;
  assert.equal(p2.items.length, N - 100);
  assert.equal(p2.nextCursor, null);
  const seen = [...p1.items, ...p2.items].map((p) => p.id);
  assert.equal(new Set(seen).size, N, 'بی تکرار');
  assert.deepEqual([...seen].sort(), ids, 'بی جاافتاده');
  for (const bad of ['0', '-5', 'abc']) {
    const r = await h.get(`/api/store/${slug}/products?limit=${bad}`);
    assert.equal(r.status, 200, `${bad} ⇒ ${r.status}`);
    assert.ok(r.body.items.length > 0 && r.body.items.length <= 100, `${bad} ⇒ ${r.body.items.length}`);
  }

  //  ۲) «در ویترین» ِ برنامه و کاتالوگِ کارمند
  const staff = (await h.get('/api/shop/storefront/products?limit=500', { token: u.accessToken })).body;
  assert.equal(staff.items.length, 100, 'فهرستِ «در ویترین» ِ برنامه ۱۰۰');
  await setFlag(shopId, 'shop.catalog', true);
  const cat = await h.get('/api/shop/catalog/products?limit=500', { token: u.accessToken });
  assert.equal(cat.status, 200, JSON.stringify(cat.body).slice(0, 200));
  assert.ok(cat.body.items.length <= 100 && cat.body.items.length > 0, `کاتالوگِ کارمند ${cat.body.items.length}`);
});
