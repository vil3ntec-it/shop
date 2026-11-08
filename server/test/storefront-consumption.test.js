'use strict';
/**
 * MKT-06 — «چرخهٔ مصرف» ِ کالا (روز) از فرمِ کالای برنامه تا کارخواهِ برنامهٔ خرید.
 *
 * برنامه (گوشیِ نسخهٔ ۳ و کامپیوتر) فیلدِ اختیاریِ `consumptionDays` را روی ردیفِ کالا می‌نویسد؛ همان صفِ همگام‌سازی
 * (v1 یا v3) آن را می‌برد و ویترین در نمای عمومیِ کالا (`product` ِ `public-views.js`) نشانش می‌دهد. آن‌چه قفل می‌شود:
 *   ⛔ فقط عددِ صحیحِ ۱ تا ۳۶۵؛ نبود، صفر، منفی، اعشار، متن یا بیش از سقف ⇒ **بی کلید** (پاسخِ پیشین بی‌کم‌وکاست).
 *   ⛔ همان کلید در فهرست، `?ids=` (کارخواهِ برنامه برای یادآوری) و برگهٔ کالا؛ قیمت همان قیمتِ سرور است، نه چیزی از دستگاه.
 *   ⛔ ویرایشِ کالا (برداشتنِ چرخه) از درخواستِ بعد دیده می‌شود.
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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-cycle' },
  //  `caps: a00` = ساختِ تازهٔ برنامه: کلیدی که کاربر پاک کرد `null` ِ صریح می‌آید و می‌نشیند (`newer-fields.js`)
  body: { device_id: 'dev-cycle', schema_version: 2, caps: ['a00'], ops },
});
const row = (table, rowId, fields, type = 'insert') => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type, fields });

const CASES = {
  tea: 30, rice: 365, soap: 1,
  //  نادرست ⇒ بی کلید
  oil: 0, salt: -5, sugar: 7.5, flour: '14', milk: 366,
};

async function liveStore() {
  const u = await h.newUser('چرخه', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ چرخه' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id, slug: `c-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: 'ویترینِ چرخه', minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const ops = Object.entries(CASES).map(([id, days]) => row('products', id, { name: `کالای ${id}`, unit: 'دانه', salePrice: 50, consumptionDays: days }));
  ops.push(row('products', 'plain', { name: 'کالای بی‌چرخه', unit: 'دانه', salePrice: 50 }));
  const p = await push(o.token, ops);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of [...Object.keys(CASES), 'plain']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return o;
}

const cycles = (items) => Object.fromEntries(items.map((p) => [p.id, Object.prototype.hasOwnProperty.call(p, 'consumptionDays') ? p.consumptionDays : 'none']));
const WANT = { tea: 30, rice: 365, soap: 1, oil: 'none', salt: 'none', sugar: 'none', flour: 'none', milk: 'none', plain: 'none' };

test('MKT-06: چرخهٔ مصرف فقط عددِ صحیحِ ۱ تا ۳۶۵؛ همان در فهرست، ?ids= و برگهٔ کالا؛ قیمت از سرور', async () => {
  const s = await liveStore();
  const list = await h.get(`/api/store/${s.slug}/products?limit=50`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual(cycles(list.body.items), WANT);
  for (const p of list.body.items) assert.equal(p.priceMinor, 5000);

  const byIds = await h.get(`/api/store/${s.slug}/products?ids=tea,plain,milk`);
  assert.equal(byIds.status, 200, JSON.stringify(byIds.body));
  assert.deepEqual(cycles(byIds.body.items), { tea: 30, plain: 'none', milk: 'none' });

  const page = await h.get(`/api/store/${s.slug}/products/tea`);
  assert.equal(page.status, 200, JSON.stringify(page.body));
  assert.equal(page.body.product.consumptionDays, 30);
  //  کالای بی‌چرخه همان کلیدهای پیشین را دارد
  const plain = await h.get(`/api/store/${s.slug}/products/plain`);
  assert.equal(Object.prototype.hasOwnProperty.call(plain.body.product, 'consumptionDays'), false);
});

test('MKT-06: برداشتن یا عوض کردنِ چرخه در برنامه از درخواستِ بعد دیده می‌شود', async () => {
  const s = await liveStore();
  const p = await push(s.token, [
    row('products', 'tea', { name: 'کالای tea', unit: 'دانه', salePrice: 50, consumptionDays: null }, 'update'),
    row('products', 'soap', { name: 'کالای soap', unit: 'دانه', salePrice: 50, consumptionDays: 14 }, 'update'),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const byIds = await h.get(`/api/store/${s.slug}/products?ids=tea,soap`);
  assert.deepEqual(cycles(byIds.body.items), { tea: 'none', soap: 14 });
});
