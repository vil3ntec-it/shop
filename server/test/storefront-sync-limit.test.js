'use strict';
/**
 * سطلِ جدای هم‌گامیِ حساب (CHK-03، WEB-11، CHK-19): ویرایشِ سبدِ سرور و ♥ هرگز سهمِ ثبتِ سفارش را نمی‌خورد.
 *
 *   پیش از این، `PUT /account/cart`، `POST /account/cart/merge` و `PUT/DELETE /account/favorites/:id` همان
 *   سطلِ `store-checkout` ِ ثبتِ سفارش را (۲۰ در ربع ساعت به‌ازای IP) می‌خوردند: مشتریِ واردشده که ۲۰ بار سبد را
 *   عوض می‌کرد (هر تغییر یک PUT پس از ۴۰۰ms) دیگر نمی‌توانست سفارش ثبت کند (۴۲۹). حالا آن چهار در سطلِ
 *   `store-acct-sync` (`storeSyncMax`، به‌ازای IP، پیش از نشست) است و ثبت، لغو، پرداخت و نشانی همان سطلِ پیشین.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '3';
process.env.RATE_STORE_SYNC_MAX = '8';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.cart', 'shop.storefront.favorites']) await setFlag(o.shopId, k, true);
  const slug = `l-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-lim' },
    body: { device_id: 'dev-lim', schema_version: 2, ops: [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 100 }), op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300 })] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

/** نشستِ حساب: کد (سطلِ `store-verify`) + ساختن (یک سهمِ `store-checkout`) */
async function session(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return { headers: { 'X-Store-Session': a.body.session } };
}

const checkout = (slug, A, phone, lines, total) => h.post(`/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتریِ سطل', phone }, lines, expectedTotalMinor: total,
}, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, ...A.headers } });

test('هم‌گامیِ سبد و ♥ سطلِ خودش را دارد: ۸ نوشتن ⇒ ثبتِ سفارش هنوز آزاد؛ نهمی ۴۲۹ و ثبت همچنان آزاد', async () => {
  const s = await liveStore('سطلِ هم‌گامی');
  const phone = '0791300001';
  const A = await session(s.slug, phone); //  سهمِ ۱ از ۳ ِ store-checkout
  const cart = `/api/store/${s.slug}/account/cart`;
  const fav = `/api/store/${s.slug}/account/favorites`;
  let v = 0;
  //  مشتریِ عادی: هر «+» یک PUT ِ سبد، و ♥ روی دو کالا (۶ + ۲ = ۸ = سقفِ سطلِ هم‌گامی)
  const merged = await h.post(`${cart}/merge`, { items: [{ productId: 'rice', qtyMilli: 1000 }] }, A);
  assert.equal(merged.status, 200, JSON.stringify(merged.body));
  v = merged.body.cart.version;
  for (let q = 2; q <= 5; q++) {
    const r = await h.put(cart, { version: v, items: [{ productId: 'rice', qtyMilli: q * 1000 }] }, A);
    assert.equal(r.status, 200, `PUT ِ سبد ${q}: ${JSON.stringify(r.body)}`);
    v = r.body.cart.version;
  }
  const last = await h.put(cart, { version: v, items: [{ productId: 'rice', qtyMilli: 5000 }, { productId: 'oil', qtyMilli: 1000 }] }, A);
  assert.equal(last.status, 200, JSON.stringify(last.body));
  assert.equal((await h.put(`${fav}/rice`, {}, A)).status, 200);
  assert.equal((await h.del(`${fav}/rice`, A)).status, 200);

  //  ⛔ پیش از جدا شدنِ سطل‌ها این‌جا ۴۲۹ بود: ثبت پشتِ ویرایش‌های سبد می‌ماند
  const c = last.body.cart;
  const ck = await checkout(s.slug, A, phone, c.lines.map((l) => ({ productId: l.productId, qtyMilli: l.qtyMilli })), c.subtotalMinor);
  assert.equal(ck.status, 201, JSON.stringify(ck.body));

  //  سطلِ هم‌گامی خودش سقف دارد (IP، پیش از نشست) ⇒ نهمی ۴۲۹ با Retry-After، بی نشست هم
  const over = await h.put(`${fav}/oil`, {}, A);
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.ok(Number(over.headers.get('retry-after')) > 0);
  assert.equal((await h.put(cart, { version: c.version, items: [] })).status, 429, 'بی نشست هم همان سطل');
  //  …و ثبت همچنان سهمِ خودش را دارد (۳ از ۳)
  const again = await checkout(s.slug, A, phone, [{ productId: 'oil', qtyMilli: 1000 }], 30000);
  assert.equal(again.status, 201, JSON.stringify(again.body));
  //  ثبتِ چهارم ⇒ ۴۲۹ ِ سطلِ ثبت (سقفِ ثبت هنوز سر جایش)
  assert.equal((await checkout(s.slug, A, phone, [{ productId: 'oil', qtyMilli: 1000 }], 30000)).status, 429);
});
