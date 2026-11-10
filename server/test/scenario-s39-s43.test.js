'use strict';
/**
 * S39 · تحویلِ ناموفق و S43 · دریافتِ حضوری (§۷.۴، خروجِ فاز ۴) — «مشتری پیام می‌گیرد» از راهِ واقعیِ هر کدام:
 *
 *   S39  پیک از درِ خودش (`POST /api/courier/events`) «تماس بی‌پاسخ» می‌زند ⇒ سفارش «تحویل ناموفق» و یک ردیفِ پیامِ
 *        مشتری (`order_delivery_failed`) در همان تراکنش؛ نقدِ گرفته‌نشده در مأموریت صفر است؛ پیامِ پیشینِ نرفته کنار.
 *   S43  دکان سفارشِ «دریافت از فروشگاه» را «آماده» می‌کند و برای دریافتِ حضوری می‌سپارد ⇒ پیامِ «آماده است»
 *        (`order_ready`) و صفحهٔ پیگیریِ مشتری همان کدی را نشان می‌دهد که میزِ تحویل یک بار به کارمند داد.
 *   هر دو پشتِ پرچم‌های خاموشِ `shop.customer_notify` و `shop.delivery_customer_view`؛ متنِ پیام بی کد و بی شماره.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const delivery = require('../src/lib/delivery');
const N = require('../src/lib/customer-notify');

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
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `n-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', delivery.VIEW_FLAG, N.FLAG_KEY]) await setFlag(s.shopId, k, true);
  assert.equal((await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: s.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', { token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-n' }, body: {
    device_id: 'dev-n', schema_version: 2,
    ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120 } }],
  } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

async function checkout(s, fulfillment) {
  const phone = '0791112244';
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const ck = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode });
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment, customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
    ...(fulfillment === 'delivery' ? { address: { lat: 34.523456, lng: 69.181234, landmark: 'نزدیکِ مسجد' } } : {}),
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ck.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token, phone };
}
const move = async (s, id, steps) => {
  for (const to of steps) assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token })).status, 200, to);
};
const events = (orderId) => many('SELECT event, status FROM customer_notify_outbox WHERE order_id=$1 ORDER BY created_at, id', [orderId]);

test('S39: «تماس بی‌پاسخ» ِ خودِ پیک ⇒ پیامِ «تحویل ناموفق» به مشتری و هیچ نقدِ گرفته‌نشده', async () => {
  const s = await liveShop('ناموفق');
  const u = await h.newUser(`پیک ${uniq()}`, 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), s.shopId, u.user.id, now()]);
  const cr = await h.post('/api/shop/delivery/couriers', { userId: u.user.id, name: 'کریم', phone: '0799999999' }, { token: s.token });
  assert.equal(cr.status, 201, JSON.stringify(cr.body));
  const o = await checkout(s, 'delivery');
  await move(s, o.id, ['accepted', 'picking', 'packed']);
  const a = await h.post(`/api/shop/delivery/orders/${o.id}/assign`,
    { mode: 'shop_courier', courierId: cr.body.courier.id, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const aid = a.body.assignment.id;
  const ev = (type, extra = {}) => ({ eventId: `ev-${uniq()}`, assignmentId: aid, type, deviceTime: Date.now(), ...extra });
  const sent = await h.post('/api/courier/events', { deviceId: 'courier-phone',
    events: [ev('assigned'), ev('picked_up'), ev('moving'), ev('failed', { reason: 'no_answer' })] }, { token: u.accessToken });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  for (const x of sent.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'delivery_failed');
  const rows = await events(o.id);
  assert.deepEqual(rows.slice(-1), [{ event: 'order_delivery_failed', status: 'pending' }], JSON.stringify(rows));
  assert.ok(rows.slice(0, -1).every((r) => r.status === 'superseded'), 'پیامِ پیشینِ نرفته کنار رفت');
  const cash = await one('SELECT status, cash_collected_minor FROM delivery_assignments WHERE id=$1', [aid]);
  assert.equal(cash.status, 'failed');
  assert.equal(Number(cash.cash_collected_minor || 0), 0, 'نقدِ گرفته‌نشده در دفترِ پیک نیست');
  const text = N.render('order_delivery_failed', 'fa', { no: 1, shop: 'ویترین' });
  assert.equal(/0799|0791|کریم/.test(JSON.stringify(text)), false);
});

test('S43: «آماده است» ⇒ پیامِ مشتری و همان کدِ دریافتِ حضوری در صفحهٔ پیگیری', async () => {
  const s = await liveShop('حضوری');
  const o = await checkout(s, 'pickup');
  await move(s, o.id, ['accepted', 'picking', 'packed', 'ready_for_pickup']);
  const a = await h.post(`/api/shop/delivery/orders/${o.id}/assign`, { mode: 'pickup' }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const code = a.body.code;
  assert.match(String(code), /^\d{4,8}$/, JSON.stringify(a.body));
  const rows = await events(o.id);
  assert.deepEqual(rows.slice(-1), [{ event: 'order_ready', status: 'pending' }], JSON.stringify(rows));
  const tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.status, 200, JSON.stringify(tr.body));
  assert.equal(tr.body.order.delivery.carrier, 'pickup');
  assert.equal(tr.body.order.delivery.code, code, 'همان کدِ میزِ تحویل');
  //  متنِ پیام کد و شماره ندارد (دادهٔ حساس فقط در صفحهٔ پیگیریِ توکن‌دار)
  const text = JSON.stringify(N.render('order_ready', 'fa', { no: 1, shop: 'ویترین' }));
  assert.equal(text.includes(code), false);
});
