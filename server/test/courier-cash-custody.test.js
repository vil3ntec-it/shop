'use strict';
/**
 * امانتِ نقدِ پیک (DLV-11، DLV-12؛ §۲۰.۶، §۲۸.۵) — «پیک فقط بدهیِ امروزِ سفارش را می‌گیرد، یک بار».
 *
 * آن‌چه این پرونده قفل می‌کند (روی جریانِ سرتاسریِ `order-flow-e2e` ِ #928 که «نقد ⇐ گرفته ⇐ تسویه» را دارد، تکرار نمی‌کند):
 *   ۱) سفارشِ نقدی پس از سپردن به پیک با حساب‌پی پرداخت شد ⇒ «نقدِ موردِ انتظار» ِ مأموریتِ زنده صفر؛ پیک بی نقد
 *      «تحویل شد» می‌زند و نقدی در دستش نمی‌ماند (پیش از این پیک همان جمع را دوباره از مشتری می‌خواست).
 *   ۲) ویرایشِ سفارش پس از سپردن (ORD-14) ⇒ «نقدِ موردِ انتظار» = جمعِ تازه، نه جمعِ لحظهٔ سپردن.
 *   ۳) پیک قیمت و جمع را عوض نمی‌کند: هر کلیدِ مبلغ جز «گرفتم» در رویداد نادیده است و کمتر از بدهی بی دلیل رد.
 *   ۴) دو بار زدن: همان «تحویل شد» دو بار ⇒ یک اثر؛ تسویهٔ هم‌زمان با همان کلید ⇒ یک سند، با دو کلید ⇒ یکی ۴۰۹؛
 *      نقدِ سفارش فقط یک بار «به دکان رسید».
 */
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-courier-cash';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const P = require('../src/lib/payment-providers');

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'cash-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'cash-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

let seq = 0;
const key = (p = 'k') => `${p}-${++seq}-${Date.now()}`;
const flag = (scope, k) => h.put('/api/admin/pump/live-config', { scope, key: k, value: true }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

async function shop(name, flags = []) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.delivery', ...flags]) await flag(s.shopId, k);
  return s;
}

/** پیک با «ورود با کدِ شاگرد» (توکنِ دامنه‌دارِ پیک) — همان راهِ برنامهٔ گوشی */
async function courier(s, name) {
  const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: s.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const r = await h.post('/api/auth/staff', { code: inv.body.code, name, device: { uid: key('phone'), name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const me = await h.get('/api/courier/me', { token: r.body.accessToken });
  assert.equal(me.status, 200, JSON.stringify(me.body));
  return { token: r.body.accessToken, id: me.body.courier.id };
}

/** سفارشِ تلفنیِ نقدی (۲ کیلو برنج × ۶۰۰ + کرایهٔ ۴۰ = ۱٬۲۴۰ افغانی) تا وضعِ `upTo`، سپرده به پیک */
async function assigned(s, c, upTo = ['accepted', 'picking', 'packed']) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ name: 'برنج', qtyMilli: 2000, unitPriceMinor: 60_000 }], deliveryFeeMinor: 4_000,
  }, { token: s.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const order = r.body.order;
  for (const to of upTo) {
    const t = await h.post(`/api/shop/orders/${order.id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'shop_courier', courierId: c.id }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.assignment.expectedCollectMinor, 124_000);
  return { order, id: a.body.assignment.id };
}

let ev = 0;
const evt = (id, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++ev}`, assignmentId: id, type, deviceTime: Date.now(), ...extra });
async function says(c, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-cash-1', events }, { token: c.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results;
}
const card = async (c, id) => (await h.get('/api/courier/missions', { token: c.token })).body.items.find((m) => m.id === id);
const staffRow = async (s, orderId) => (await h.get(`/api/shop/delivery/orders/${orderId}`, { token: s.token })).body.assignments.at(-1);
const inHand = async (c) => (await h.get('/api/courier/summary', { token: c.token })).body.cash;

test('DLV-11: سفارشِ نقدی پس از سپردن با حساب‌پی پرداخت شد ⇒ پیک دیگر نقدی نمی‌خواهد و نمی‌گیرد', async () => {
  const s = await shop('حساب‌پی', ['pay.hesabpay']);
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  const c = await courier(s, 'کریم');
  const { order, id } = await assigned(s, c);
  for (const x of await says(c, [evt(id, 'assigned'), evt(id, 'picked_up')])) assert.equal(x.status, 'applied', JSON.stringify(x));
  assert.equal((await card(c, id)).expectedCollectMinor, 124_000);
  assert.equal((await inHand(c)).liveExpectedMinor, 124_000);

  //  مشتری در راه با پیوندِ حساب‌پی ِ دکان پرداخت کرد (مبلغ از سرور)
  const it = await h.post('/api/shop/payments/intents', { kind: 'online_order', refId: order.id, amount: 124_000, currency: 'AFN' },
    { token: s.token, headers: { 'Idempotency-Key': key('intent') } });
  assert.equal(it.status, 201, JSON.stringify(it.body));
  const buf = Buffer.from(JSON.stringify({ eventId: key('wh'), sessionId: P.fake.sessionOf(it.body.id), status: 'paid', amountMinor: 124_000, currency: 'AFN', providerRef: 'HP-C-1', settle: true }));
  const wh = await h.raw('POST', '/api/payments/webhook/fake', buf, { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(buf) } });
  assert.equal(wh.status, 200, JSON.stringify(wh.body));
  assert.equal((await one('SELECT payment_status FROM online_orders WHERE id=$1', [order.id])).payment_status, 'paid');

  //  کارتِ پیک، میزِ دکان و «نقدِ من»: دیگر چیزی برای گرفتن نیست
  assert.equal((await card(c, id)).expectedCollectMinor, 0);
  assert.equal((await staffRow(s, order.id)).expectedCollectMinor, 0);
  assert.equal((await inHand(c)).liveExpectedMinor, 0);

  //  «تحویل شد» بی نقد پذیرفته؛ هیچ نقدی در دستِ پیک و هیچ «نقد گرفته شد» روی سفارشِ پرداخت‌شده
  const [done] = await says(c, [evt(id, 'delivered')]);
  assert.equal(done.status, 'applied', JSON.stringify(done));
  assert.equal((await inHand(c)).inHandMinor, 0);
  const o = await one('SELECT payment_status, cod_status FROM online_orders WHERE id=$1', [order.id]);
  assert.deepEqual(o, { payment_status: 'paid', cod_status: '' });
  const pv = await h.get(`/api/shop/delivery/couriers/${c.id}/settlement-preview`, { token: s.token });
  assert.deepEqual([pv.body.totals.expectedCashMinor, pv.body.totals.collectedCashMinor], [0, 0]);
});

test('ORD-14 پس از سپردن: «نقدِ موردِ انتظار» ِ پیک همان جمعِ تازهٔ سفارش است، نه جمعِ لحظهٔ سپردن', async () => {
  const s = await shop('ویرایش', ['shop.orders.amend']);
  const c = await courier(s, 'رحیم');
  const { order, id } = await assigned(s, c, ['accepted']);   //  ویرایش فقط «تازه/پذیرفته»؛ سپردن از «پذیرفته»
  const am = await h.post(`/api/shop/orders/${order.id}/amendments`, {
    changes: [{ lineNo: 1, qtyMilli: 1000 }], reason: 'out_of_stock', consent: { via: 'phone', note: 'مشتری پذیرفت' },
  }, { token: s.token, headers: { 'Idempotency-Key': key('amend') } });
  assert.equal(am.status, 201, JSON.stringify(am.body));
  const total = Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [order.id])).total_minor);
  assert.equal(total, 64_000);
  assert.equal((await staffRow(s, order.id)).expectedCollectMinor, total);
  assert.equal((await card(c, id)).expectedCollectMinor, total);
  assert.equal((await inHand(c)).liveExpectedMinor, total);
});

test('پیک قیمت و جمع را عوض نمی‌کند: کلیدِ مبلغِ دیگر در رویداد بی‌اثر؛ کمتر از بدهی بی دلیل رد', async () => {
  const s = await shop('قیمت');
  const c = await courier(s, 'نبی');
  const { order, id } = await assigned(s, c);
  await says(c, [evt(id, 'assigned'), evt(id, 'picked_up')]);
  const forged = { expectedCollectMinor: 1_000, totalMinor: 1_000, fareMinor: 99_999, cashCollectedMinor: 1_000 };
  const [r1] = await says(c, [evt(id, 'delivered', forged)]);
  assert.equal(r1.status, 'rejected');
  assert.equal(r1.code, 'cash_reason_required');
  const row = await staffRow(s, order.id);
  assert.deepEqual([row.status, row.expectedCollectMinor, row.fareMinor], ['picked_up', 124_000, 0]);
  assert.equal(Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [order.id])).total_minor), 124_000);
  //  درِ ویرایشِ قلم و جمعِ سفارش با توکنِ پیک بسته است (فهرستِ کاملِ درها: `courier-scope`)
  const edit = await h.post(`/api/shop/orders/${order.id}/amendments`, { changes: [{ lineNo: 1, qtyMilli: 1 }] }, { token: c.token, headers: { 'Idempotency-Key': key('am') } });
  assert.equal(edit.status, 403, JSON.stringify(edit.body));
  assert.equal(edit.body.error.code, 'courier_scope');
});

test('دو بار زدن: «تحویل شد» ِ تکراری یک اثر؛ تسویهٔ هم‌زمان یک سند و نقد یک بار «به دکان رسید»', async () => {
  const s = await shop('تکرار');
  const c = await courier(s, 'جواد');
  const { order, id } = await assigned(s, c);
  await says(c, [evt(id, 'assigned'), evt(id, 'picked_up')]);
  const done = evt(id, 'delivered', { cashCollectedMinor: 124_000 });
  const [a, b] = await Promise.all([says(c, [done]), says(c, [done])]);
  assert.deepEqual([a[0].status, b[0].status].sort(), ['applied', 'duplicate']);
  assert.equal((await inHand(c)).inHandMinor, 124_000);

  const body = { assignmentIds: [id], handedOverMinor: 124_000, receivedMinor: 124_000 };
  const url = `/api/shop/delivery/couriers/${c.id}/settlements`;
  const same = key('settle');
  const [x, y] = await Promise.all([h.post(url, body, { token: s.token, headers: { 'Idempotency-Key': same } }),
    h.post(url, body, { token: s.token, headers: { 'Idempotency-Key': same } })]);
  assert.deepEqual([x.status, y.status].sort(), [200, 201]);
  assert.equal(x.body.settlement.id, y.body.settlement.id);
  const other = await h.post(url, body, { token: s.token, headers: { 'Idempotency-Key': key('settle') } });
  assert.equal(other.status, 409, JSON.stringify(other.body));
  assert.equal(other.body.error.code, 'settlement_changed');

  assert.equal((await many('SELECT id FROM courier_settlements WHERE shop_id=$1', [s.shopId])).length, 1);
  const cod = await many(`SELECT to_status FROM online_order_events WHERE order_id=$1 AND kind='cod' ORDER BY seq`, [order.id]);
  assert.deepEqual(cod.map((e) => e.to_status), ['collected_by_courier', 'settled_to_shop']);
  assert.equal((await inHand(c)).inHandMinor, 0);
});

test('CHK-21 پس از سپردنِ «دریافت از دکان»: قلمی که مشتری افزود در بدهیِ نقدِ تحویل هم هست', async () => {
  const s = await shop('افزودن', ['shop.storefront', 'shop.orders.add_items']);
  const slug = `cash-${Date.now().toString(36)}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', contactPhone: '0700000001' }, { token: s.token })).status, 200);
  const ops = ['rice', 'salt'].map((id, i) => ({ op_id: key('op'), ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { name: id, unit: 'کیلو', salePrice: 100 * (i + 1) } }));
  assert.equal((await h.api('POST', '/api/sync/v1/push', { token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-c' }, body: { device_id: 'dev-c', schema_version: 2, ops } })).status, 200);
  for (const p of ['rice', 'salt']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
  const phone = '0791112299';
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const ticket = (await h.post(`/api/store/${slug}/verify/check`, { phone, code: v.body.devCode })).body.ticket;
  const ck = await h.post(`/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }] },
    { headers: { 'Idempotency-Key': key('ck'), 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, ck.body.order.orderNo])).id;
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: s.token })).status, 200);
  const a = await h.post(`/api/shop/delivery/orders/${id}/assign`, { mode: 'pickup' }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.assignment.expectedCollectMinor, 10_000);
  const add = await h.post(`/api/store/${slug}/orders/${ck.body.order.token}/items`, { lines: [{ productId: 'salt', qtyMilli: 1000 }] },
    { headers: { 'Idempotency-Key': key('add'), 'X-Store-Ticket': ticket } });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  const total = Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [id])).total_minor);
  assert.equal(total, 30_000);
  assert.equal((await staffRow(s, id)).expectedCollectMinor, total);
});
