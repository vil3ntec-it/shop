'use strict';
/**
 * S33 (§۷.۴) — Plus Code ِ ساخته از پین در سفارش و نشانیِ ذخیره‌شده (`lib/plus-code.js`، `public/store/plus-code.js`؛
 * پرچمِ خاموشِ `shop.orders.plus_code`). پیش‌تر کد فقط هنگامِ نمایش در جزئیاتِ مأموریتِ برنامهٔ پیک ساخته می‌شد.
 *
 *   ۱) بردارِ مشترکِ `core/test-vectors/plus-code-v1.json` (همان که `PlusCodeVectorsTest.kt` می‌خواند): سرور و همان
 *      پروندهٔ ویترین در مرورگر (window) هر دو همان کد را می‌دهند.
 *   ۲) پرچمِ خاموش ⇒ همان امروز: نشانیِ سفارش و نشانیِ ذخیره‌شده بی کلیدِ تازه؛ ویترین `features.plusCode` ندارد.
 *   ۳) پرچمِ روشن ⇒ سفارشِ ویترین و کارمند با پین `pinCode` ِ همان پین را می‌گیرد؛ `plusCode` ِ تایپ‌شده دست نمی‌خورد؛
 *      `pinCode` ِ بدنه هرگز پذیرفته نیست؛ بی پین ⇒ بی کد. نشانیِ ذخیره‌شدهٔ حساب کد را از پینِ خودش دارد.
 *   ۴) «اصلاحِ نشانی» ِ دکان: پینِ تازه ⇒ کدِ تازه؛ `keepPin` ⇒ همان کد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const plus = require('../src/lib/plus-code');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const VEC = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'plus-code-v1.json'), 'utf8'));
const num = (v) => (typeof v === 'string' ? Number(v) : v);

test('S33 ۱) بردارِ مشترک: سرور و پروندهٔ ویترین در مرورگر همان کد را می‌دهند', () => {
  assert.equal(VEC.version, 1);
  assert.ok(VEC.cases.length >= 15);
  const win = {};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', 'public', 'store', 'plus-code.js'), 'utf8'), { window: win });
  assert.equal(typeof win.StorePlusCode.encode, 'function', 'در مرورگر window.StorePlusCode');
  for (const c of VEC.cases) {
    assert.equal(plus.encode(num(c.lat), num(c.lng)), c.code, JSON.stringify(c));
    assert.equal(win.StorePlusCode.encode(num(c.lat), num(c.lng)), c.code, `مرورگر: ${JSON.stringify(c)}`);
  }
  //  نشانیِ بی پین یا با پینِ نیمه ⇒ هیچ
  assert.equal(plus.codeOf({ landmark: 'مسجد' }), null);
  assert.equal(plus.codeOf({ lat: 34.5 }), null);
  assert.deepEqual(plus.stamp({ landmark: 'مسجد', pinCode: 'XXXX' }, true), { landmark: 'مسجد' });
  assert.deepEqual(plus.stamp({ lat: 34.5553, lng: 69.2075, pinCode: 'XXXX' }, false), { lat: 34.5553, lng: 69.2075, pinCode: 'XXXX' });
});

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
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
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-plus' }, body: { device_id: 'dev-plus', schema_version: 2, ops },
});

async function liveShop(label, on) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `pc-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.storefront.web_account', 'shop.storefront.address_extra']) {
    await setFlag(s.shopId, k, true);
  }
  if (on) await setFlag(s.shopId, plus.FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(s.token, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' } }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

const PHONE = '0791112233';
async function ticketOf(s) {
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(PHONE) });
  const ck = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(PHONE), code: st.body.devCode });
  assert.equal(ck.status, 200, JSON.stringify(ck.body));
  return ck.body.ticket;
}
async function webOrder(s, address) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone: PHONE }, address, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketOf(s) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return one('SELECT id, address FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo]);
}
async function staffOrder(s, address) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    address, lines: [{ name: 'نان', qtyMilli: 1000, unitPriceMinor: 1_000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `so-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}
async function sessionOf(s) {
  const a = await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': await ticketOf(s) } });
  assert.ok(a.body.session, JSON.stringify(a.body));
  return { headers: { 'X-Store-Session': a.body.session } };
}
const addr = (x) => (typeof x === 'string' ? JSON.parse(x) : x);
const KABUL = { lat: 34.5553, lng: 69.2075 };

test('S33 ۲) پرچمِ خاموش ⇒ همان امروز: نشانیِ سفارش، نشانیِ ذخیره‌شده و ویترین بی کلیدِ تازه', async () => {
  const s = await liveShop('کدخاموش', false);
  const o = await webOrder(s, { ...KABUL, landmark: 'نزدیکِ مسجد', pinCode: 'XXXX' });
  assert.deepEqual(addr(o.address), { ...KABUL, landmark: 'نزدیکِ مسجد' });
  assert.deepEqual((await staffOrder(s, { ...KABUL, landmark: 'نانوایی' })).address, { ...KABUL, landmark: 'نانوایی' });
  const info = await h.get(`/api/store/${s.slug}`);
  assert.equal(info.status, 200);
  assert.equal(info.body.features?.plusCode, undefined);
  const ses = await sessionOf(s);
  const saved = await h.post(`/api/store/${s.slug}/account/addresses`, { label: 'خانه', ...KABUL }, ses);
  assert.equal(saved.status, 201, JSON.stringify(saved.body));
  assert.equal('pinCode' in saved.body.address, false);
  assert.equal('pinCode' in (await h.get(`/api/store/${s.slug}/account/addresses`, ses)).body.addresses[0], false);
});

test('S33 ۳) پرچمِ روشن ⇒ کدِ همان پین در سفارش و نشانیِ ذخیره‌شده؛ تایپ‌شده دست‌نخورده؛ کدِ بدنه پذیرفته نیست', async () => {
  const s = await liveShop('کدروشن', true);
  const want = plus.encode(KABUL.lat, KABUL.lng);
  assert.equal(want, '8J6FH645+42');
  const o = await webOrder(s, { ...KABUL, landmark: 'نزدیکِ مسجد', plusCode: '9g8f+6w کابل', pinCode: 'XXXX' });
  assert.deepEqual(addr(o.address), { ...KABUL, landmark: 'نزدیکِ مسجد', plusCode: '9G8F+6W کابل', pinCode: want });
  //  بی پین ⇒ بی کد، حتی اگر بدنه کدی بفرستد
  assert.deepEqual(addr((await webOrder(s, { landmark: 'بازار', pinCode: '8J6FH645+42' })).address), { landmark: 'بازار' });
  //  سفارشِ تلفنیِ کارمند با پین هم
  const herat = { lat: 34.3482, lng: 62.1997 };
  assert.deepEqual((await staffOrder(s, { ...herat, landmark: 'نانوایی' })).address, { ...herat, landmark: 'نانوایی', pinCode: '8J6485XX+7V' });
  //  ویترین زیرِ پین نشان می‌دهد
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.features.plusCode, true);
  //  نشانیِ ذخیره‌شده: کد از پینِ خودش؛ بی پین هیچ؛ پینِ تازه ⇒ کدِ تازه
  const ses = await sessionOf(s);
  const saved = await h.post(`/api/store/${s.slug}/account/addresses`, { label: 'خانه', ...KABUL, pinCode: 'XXXX' }, ses);
  assert.equal(saved.status, 201, JSON.stringify(saved.body));
  assert.equal(saved.body.address.pinCode, want);
  assert.equal(saved.body.address.plusCode, '', 'ستونِ تایپ‌شده خالی می‌ماند');
  const bare = await h.post(`/api/store/${s.slug}/account/addresses`, { label: 'دکان', landmark: 'سرِ کوچه' }, ses);
  assert.equal('pinCode' in bare.body.address, false);
  const moved = await h.put(`/api/store/${s.slug}/account/addresses/${saved.body.address.id}`, { label: 'خانه', ...herat }, ses);
  assert.equal(moved.body.address.pinCode, '8J6485XX+7V');
  const ids = [saved.body.address.id, bare.body.address.id];
  const codes = (list) => ids.map((id) => list.find((a) => a.id === id)?.pinCode ?? null);
  assert.deepEqual(codes((await h.get(`/api/store/${s.slug}/account/addresses`, ses)).body.addresses), ['8J6485XX+7V', null]);
  assert.deepEqual(codes((await h.get(`/api/store/${s.slug}/account`, ses)).body.addresses), ['8J6485XX+7V', null]);
  //  ستونِ تازه‌ای در پایگاه نیست: کد همیشه از پینِ ذخیره‌شده است، پس کهنه نمی‌شود
  const row = await one('SELECT * FROM store_customer_addresses WHERE id=$1', [saved.body.address.id]);
  assert.equal('pin_code' in row, false);
});

test('S33 ۴) «اصلاحِ نشانی» ِ دکان: پینِ تازه ⇒ کدِ تازه؛ keepPin ⇒ همان کد', async () => {
  const s = await liveShop('کداصلاح', true);
  const o = await webOrder(s, { ...KABUL, landmark: 'نزدیکِ مسجد' });
  for (const to of ['accepted', 'picking', 'packed']) {
    assert.equal((await h.post(`/api/shop/orders/${o.id}/transition`, { to }, { token: s.token })).status, 200, to);
  }
  const a = await h.post(`/api/shop/delivery/orders/${o.id}/assign`, { mode: 'external', externalName: 'پیکِ بیرونی' }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const fix = (body) => h.post(`/api/shop/delivery/assignments/${a.body.assignment.id}/address`, body,
    { token: s.token, headers: { 'Idempotency-Key': `fx-${uniq()}-key` } });
  const kept = await fix({ address: { landmark: 'روبه‌روی نانوایی' }, keepPin: true });
  assert.equal(kept.status, 200, JSON.stringify(kept.body));
  let now1 = addr((await one('SELECT address FROM online_orders WHERE id=$1', [o.id])).address);
  assert.equal(now1.pinCode, '8J6FH645+42');
  assert.equal(now1.landmark, 'روبه‌روی نانوایی');
  const moved = await fix({ address: { lat: 31.6133, lng: 65.7101, landmark: 'کندهار' } });
  assert.equal(moved.status, 200, JSON.stringify(moved.body));
  now1 = addr((await one('SELECT address FROM online_orders WHERE id=$1', [o.id])).address);
  assert.equal(now1.pinCode, '8J37JP76+82');
  const bare = await fix({ address: { landmark: 'بی پین' } });
  assert.equal(bare.status, 200, JSON.stringify(bare.body));
  assert.equal('pinCode' in addr((await one('SELECT address FROM online_orders WHERE id=$1', [o.id])).address), false);
});
