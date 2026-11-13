'use strict';
/**
 * حسابِ مشتری در وبِ ویترین (CHK-01/03/09، WEB-14؛ پرچمِ `shop.storefront.web_account`، صفحه: `store/acct.js`).
 * درها همان `/api/store/:slug/account…` ِ `lib/store-customers.js` و `lib/store-cart.js`اند؛ این پرونده قراردادی را
 * قفل می‌کند که صفحهٔ وب به آن تکیه دارد (مرورگر: `web-store-acct.mjs`):
 *   ۱) ⛔ پرچمِ خاموش ⇒ `GET /:slug` بی `features.webAccount` (صفحه acct.js را بار نمی‌کند)؛ روشن ⇒ `webAccount`،
 *      و `accountCart` فقط وقتی `shop.storefront.cart` هم روشن است.
 *   ۲) CHK-01: پس از ثبتِ مهمان با بلیت، همان بلیتِ تازه حساب می‌سازد؛ بلیتِ کهنه ⇒ ۴۰۱ `verification_required`
 *      (صفحه همان‌جا کد می‌خواهد)؛ سپس نشستِ تنها (بی بلیت) ثبت و «سفارش‌های من» را می‌پذیرد — فقط برای شمارهٔ خودِ حساب.
 *   ۳) CHK-09: نشانی‌های نام‌دار با پین فقط برای همان نشست؛ نشستِ دیگر نمی‌بیند و حذفش ۴۰۴ است.
 *   ۴) CHK-03/WEB-14: ادغام ⇒ نسخه؛ نوشتنِ کهنه ⇒ ۴۰۹ `cart_changed` با `error.cart` (نسخه و قلم‌ها) که صفحه رویش
 *      دوباره می‌نویسد؛ ادغامِ دوباره بی اثر؛ خروج ⇒ نشست دیگر کار نمی‌کند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
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
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `wa-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0, acceptPickup: true }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-wa' },
    body: { device_id: 'dev-wa', schema_version: 2, ops: [
      op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120 }),
      op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300 }),
    ] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  return { ...o, slug };
}

async function ticketOf(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

const checkout = (slug, phone, headers) => h.post(`/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
}, { headers: { 'Idempotency-Key': `wa-${uniq()}`, ...headers } });

test('⛔ پرچمِ خاموش ⇒ بی `features.webAccount`؛ روشن ⇒ `webAccount`، و `accountCart` فقط با پرچمِ سبد', async () => {
  const s = await liveStore('پرچم');
  const feat = async () => ((await h.get(`/api/store/${s.slug}`)).body.features || {});
  let f = await feat();
  assert.equal(f.webAccount, undefined);
  assert.equal(f.accountCart, undefined);
  await setFlag(s.shopId, 'shop.storefront.cart', true);
  f = await feat();
  assert.equal(f.webAccount, undefined, 'سبدِ سرور بی پرچمِ حسابِ وب ⇒ صفحه هنوز کاری نمی‌کند');
  await setFlag(s.shopId, 'shop.storefront.web_account', true);
  f = await feat();
  assert.equal(f.webAccount, true);
  assert.equal(f.accountCart, true);
  await setFlag(s.shopId, 'shop.storefront.cart', false);
  f = await feat();
  assert.equal(f.webAccount, true);
  assert.equal(f.accountCart, undefined);
});

test('CHK-01: ثبتِ مهمان ⇒ همان بلیتِ تازه حساب می‌سازد؛ بلیتِ کهنه ۴۰۱؛ نشستِ تنها ثبت و «سفارش‌های من» را می‌پذیرد', async () => {
  const s = await liveStore('پیشنهاد');
  await setFlag(s.shopId, 'shop.storefront.web_account', true);
  const phone = '0791230001';
  const ticket = await ticketOf(s.slug, phone);
  const first = await checkout(s.slug, phone, { 'X-Store-Ticket': ticket });
  assert.equal(first.status, 201, JSON.stringify(first.body));

  //  بلیتِ کهنه‌تر از مهلتِ ساختنِ حساب ⇒ صفحه کدِ تازه می‌خواهد
  const keep = config.storefront.accountTicketMaxMs;
  config.storefront.accountTicketMaxMs = 1;
  try {
    await new Promise((r) => setTimeout(r, 15));
    const stale = await h.post(`/api/store/${s.slug}/account`, { name: 'مشتری' }, { headers: { 'X-Store-Ticket': ticket } });
    assert.equal(stale.status, 401, JSON.stringify(stale.body));
    assert.equal(stale.body.error.code, 'verification_required');
  } finally { config.storefront.accountTicketMaxMs = keep; }

  const made = await h.post(`/api/store/${s.slug}/account`, { name: 'مشتری' }, { headers: { 'X-Store-Ticket': ticket } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.match(made.body.session, /^sc1\./);
  assert.equal(made.body.customer.phone, '+93791230001');
  assert.ok(made.body.expiresAt > Date.now() + 100 * 24 * 60 * 60 * 1000, 'نشستِ بلند (۱۸۰ روز)');
  const sess = { 'X-Store-Session': made.body.session };

  //  بی بلیت، فقط با نشست
  const second = await checkout(s.slug, phone, sess);
  assert.equal(second.status, 201, JSON.stringify(second.body));
  const other = await checkout(s.slug, '0791230002', sess);
  assert.equal(other.status, 403, JSON.stringify(other.body));
  assert.equal(other.body.error.code, 'phone_not_verified');
  const mine = await h.get(`/api/store/${s.slug}/orders`, { headers: sess });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  assert.deepEqual(mine.body.orders.map((o) => o.orderNo).sort(), [first.body.order.orderNo, second.body.order.orderNo].sort());

  //  خروج ⇒ نشست دیگر چیزی را باز نمی‌کند
  assert.equal((await h.post(`/api/store/${s.slug}/account/logout`, {}, { headers: sess })).status, 200);
  const after = await checkout(s.slug, phone, sess);
  assert.equal(after.status, 401, JSON.stringify(after.body));
  assert.equal(after.body.error.code, 'verification_required');
});

test('CHK-09: نشانیِ نام‌دار با پین فقط مالِ همان نشست؛ دیگری نمی‌بیند و نمی‌تواند پاکش کند', async () => {
  const s = await liveStore('نشانی');
  await setFlag(s.shopId, 'shop.storefront.web_account', true);
  const signIn = async (phone) => {
    const r = await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': await ticketOf(s.slug, phone) } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return { 'X-Store-Session': r.body.session };
  };
  const a = await signIn('0791230011');
  const b = await signIn('0791230012');
  const add = await h.post(`/api/store/${s.slug}/account/addresses`,
    { label: 'خانه', text: 'کارتهٔ ۳، کوچهٔ ۲', landmark: 'پشتِ مسجد', lat: 34.5553, lng: 69.2075 }, { headers: a });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  const noLabel = await h.post(`/api/store/${s.slug}/account/addresses`, { label: '', text: 'x' }, { headers: a });
  assert.equal(noLabel.status, 400);
  const list = (await h.get(`/api/store/${s.slug}/account/addresses`, { headers: a })).body.addresses;
  assert.deepEqual(list.map((x) => [x.label, x.text, x.landmark, x.lat, x.lng]), [['خانه', 'کارتهٔ ۳، کوچهٔ ۲', 'پشتِ مسجد', 34.5553, 69.2075]]);
  assert.deepEqual((await h.get(`/api/store/${s.slug}/account/addresses`, { headers: b })).body.addresses, []);
  assert.equal((await h.del(`/api/store/${s.slug}/account/addresses/${add.body.address.id}`, { headers: b })).status, 404);
  assert.equal((await h.del(`/api/store/${s.slug}/account/addresses/${add.body.address.id}`, { headers: a })).status, 200);
  assert.deepEqual((await h.get(`/api/store/${s.slug}/account/addresses`, { headers: a })).body.addresses, []);
});

test('CHK-03/WEB-14: ادغام ⇒ نسخه؛ نوشتنِ کهنه ⇒ ۴۰۹ با `error.cart` که صفحه رویش دوباره می‌نویسد', async () => {
  const s = await liveStore('سبد');
  await setFlag(s.shopId, 'shop.storefront.web_account', true);
  await setFlag(s.shopId, 'shop.storefront.cart', true);
  const r = await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': await ticketOf(s.slug, '0791230021') } });
  const sess = { 'X-Store-Session': r.body.session };
  const url = `/api/store/${s.slug}/account/cart`;
  const merged = await h.post(`${url}/merge`, { items: [{ productId: 'rice', qtyMilli: 2000, note: 'سفید' }] }, { headers: sess });
  assert.equal(merged.status, 200, JSON.stringify(merged.body));
  assert.equal(merged.body.cart.version, 1);
  const again = await h.post(`${url}/merge`, { items: [{ productId: 'rice', qtyMilli: 2000, note: 'سفید' }] }, { headers: sess });
  assert.equal(again.body.cart.version, 1, 'ادغامِ دوباره (تلاشِ دوبارهٔ صفحه) اثرِ دوم ندارد');
  //  «دستگاهِ دیگر» نوشت ⇒ نسخهٔ ۲
  assert.equal((await h.put(url, { version: 1, items: [{ productId: 'oil', qtyMilli: 1000 }] }, { headers: sess })).status, 200);
  //  تغییرِ در صفِ این دستگاه با نسخهٔ کهنه ⇒ ۴۰۹ و سبدِ تازه در خودِ خطا
  const stale = await h.put(url, { version: 1, items: [{ productId: 'rice', qtyMilli: 3000 }] }, { headers: sess });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'cart_changed');
  assert.equal(stale.body.error.cart.version, 2);
  assert.deepEqual(stale.body.error.cart.lines.map((l) => [l.productId, l.qtyMilli]), [['oil', 1000]]);
  const won = await h.put(url, { version: stale.body.error.cart.version, items: [{ productId: 'rice', qtyMilli: 3000 }] }, { headers: sess });
  assert.equal(won.status, 200, JSON.stringify(won.body));
  assert.deepEqual(won.body.cart.lines.map((l) => [l.productId, l.qtyMilli, l.priceMinor]), [['rice', 3000, 12000]]);
});
