'use strict';
/**
 * INV-01@32 — «تعریف و انتشار از یک صفحه»: صفحهٔ هر کالا در برنامه وضعِ ویترینِ همان کالا را از
 * `GET /api/shop/storefront/products/:id` می‌خواند و از همان صفحه منتشرش می‌کند.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) کالایی که روی دستگاه تعریف شده ولی هنوز همگام نشده ⇒ خواندن و انتشار هر دو ۴۰۴ `product_not_found`
 *      (برنامه: «هنوز به سرور نرسیده»، دکمه خاموش) و هیچ ردیفی در `storefront_products` نمی‌نشیند.
 *   ۲) پس از همگام شدن: همان شناسه، همان شکلِ فهرستِ «در ویترین»، منتشرنشده؛ انتشار از همان راه ⇒ همان
 *      شناسه در کاتالوگِ عمومی، در فهرستِ کارمند و در خواندنِ تکی.
 *   ۳) مرزها: پرچمِ خاموش ⇒ ۴۰۳؛ شناسهٔ بد ⇒ ۴۰۰؛ کالای دکانِ دیگر ⇒ ۴۰۴ (نه نشتِ داده)؛ کارمندِ فقط‌خواندنی
 *      می‌خواند و نمی‌نویسد؛ با پرچمِ گروهی دلیلِ «در بازار نمی‌آید» همان فهرست.
 */
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

const push = (token, ops, device = 'dev-one') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields, type = 'insert') => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
});

async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `one-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

const read = (s, id, token = s.token) => h.get(`/api/shop/storefront/products/${encodeURIComponent(id)}`, { token });
const publish = (s, id, body = { published: true }, token = s.token) =>
  h.put(`/api/shop/storefront/products/${encodeURIComponent(id)}`, body, { token });

test('INV-01@32: هنوز همگام‌نشده ⇒ ۴۰۴ ِ خواندن و انتشار، بی ردیف؛ پس از همگام شدن همان شناسه از یک صفحه منتشر می‌شود', async () => {
  const s = await liveShop('یک‌صفحه');
  const id = `p-${uniq()}`;

  //  ۱) روی دستگاه تعریف شده، هنوز push نشده
  const before = await read(s, id);
  assert.equal(before.status, 404, JSON.stringify(before.body));
  assert.equal(before.body.error.code, 'product_not_found');
  const early = await publish(s, id);
  assert.equal(early.status, 404, 'انتشارِ کالای همگام‌نشده پذیرفته نمی‌شود');
  assert.equal(early.body.error.code, 'product_not_found');
  assert.equal(await one('SELECT 1 FROM storefront_products WHERE shop_id = $1 AND product_id = $2', [s.shopId, id]), null,
    'هیچ ردیفِ نیمه‌کاره‌ای نمی‌نشیند');

  //  ۲) همگام شد ⇒ همان شناسه، منتشرنشده، قیمتِ دفتر
  assert.equal((await push(s.token, [op(id, { name: 'زعفران', unit: 'گرم', salePrice: 95, category: 'ادویه' })])).status, 200);
  const got = await read(s, id);
  assert.equal(got.status, 200, JSON.stringify(got.body));
  assert.equal(got.body.id, id);
  assert.equal(got.body.name, 'زعفران');
  assert.equal(got.body.priceMinor, 9500);
  assert.equal(got.body.published, false);
  assert.equal(got.body.onlinePriceMinor, null);
  assert.equal(got.body.active, true);
  assert.equal(got.body.description, '');
  assert.deepEqual(got.body.specs, []);
  assert.ok(!('notLive' in got.body), 'بی پرچمِ گروهی همان شکلِ پیشینِ فهرست');
  const pubBefore = (await h.get(`/api/store/${s.slug}/products`)).body.items;
  assert.ok(!pubBefore.some((p) => p.id === id), 'منتشرنشده در ویترین نیست');

  //  همان شکلِ ردیفِ فهرستِ «در ویترین»
  const listed = (await h.get('/api/shop/storefront/products', { token: s.token })).body.items.find((p) => p.id === id);
  assert.deepEqual(got.body, listed);

  //  ۳) انتشار از همان صفحه ⇒ همان شناسه همه‌جا
  const put = await publish(s, id, { published: true, onlinePriceMinor: 9000, maxQtyMilli: null });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const after = await read(s, id);
  assert.equal(after.body.published, true);
  assert.equal(after.body.onlinePriceMinor, 9000);
  const pub = (await h.get(`/api/store/${s.slug}/products`)).body.items.find((p) => p.id === id);
  assert.ok(pub, 'منتشرشده با همان شناسه در ویترین');
  assert.equal(pub.priceMinor, 9000);
  assert.equal((await h.get(`/api/store/${s.slug}/products/${id}`)).body.product.id, id);

  //  برداشتن از همان صفحه
  assert.equal((await publish(s, id, { published: false, onlinePriceMinor: 9000, maxQtyMilli: null })).status, 200);
  assert.equal((await read(s, id)).body.published, false);
  assert.ok(!(await h.get(`/api/store/${s.slug}/products`)).body.items.some((p) => p.id === id));
});

test('INV-01@32: مرزها — پرچمِ خاموش، شناسهٔ بد، دکانِ دیگر، کارمندِ فقط‌خواندنی، کالای حذف‌شده و دلیلِ گروهی', async () => {
  const a = await liveShop('مرزِ الف');
  const b = await liveShop('مرزِ ب');
  assert.equal((await push(a.token, [op('saffron', { name: 'زعفران', unit: 'گرم', salePrice: 95 })])).status, 200);
  assert.equal((await push(b.token, [op('tea', { name: 'چای', unit: 'بسته', salePrice: 40 })], 'dev-b')).status, 200);

  //  شناسهٔ بد ⇒ ۴۰۰ (نه پرسشِ پایگاه)
  const bad = await read(a, 'x'.repeat(300));
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_product');

  //  کالای دکانِ دیگر ⇒ ۴۰۴، بی نامی از آن
  const other = await read(a, 'tea');
  assert.equal(other.status, 404);
  assert.ok(!JSON.stringify(other.body).includes('چای'));

  //  کارمندِ عادی: می‌خواند (`data.read`)، نمی‌نویسد (`settings.write`)
  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, staff.user.id, now()]
  );
  assert.equal((await read(a, 'saffron', staff.accessToken)).status, 200);
  assert.equal((await publish(a, 'saffron', { published: true }, staff.accessToken)).status, 403);

  //  با پرچمِ گروهی دلیلِ «نمی‌آید» همان فهرست
  await setFlag(a.shopId, 'shop.storefront.bulk', true);
  assert.deepEqual((await read(a, 'saffron')).body.notLive, ['not_published']);
  await publish(a, 'saffron', { published: true });
  assert.deepEqual((await read(a, 'saffron')).body.notLive, []);

  //  حذف در دفتر ⇒ دوباره ۴۰۴
  assert.equal((await push(a.token, [op('saffron', {}, 'delete')])).status, 200);
  assert.equal((await read(a, 'saffron')).status, 404);

  //  پرچمِ ویترینِ خاموش ⇒ ۴۰۳ `storefront_disabled`
  await setFlag(a.shopId, 'shop.storefront', false);
  const off = await read(a, 'saffron');
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'storefront_disabled');
});
