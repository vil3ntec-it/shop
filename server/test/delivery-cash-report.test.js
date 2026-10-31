'use strict';
/**
 * RPT-15 — گزارشِ نقدِ پیک‌ها و تسویه (`GET /api/shop/delivery/cash-report`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) هر پیک: نقدِ گرفته‌شدهٔ بازه، تسویه‌نشدهٔ همان، سندهای تسویهٔ بازه (کسری/اضافه/کرایه)
 *      و «در دست» ِ همین لحظه؛ جمعِ ردیف‌ها = جمعِ کل.
 *   ۲) تطبیق با دفترِ تسویه: ستون‌های تسویهٔ جمعِ کل = جمعِ `GET /settlements` با همان بازه؛
 *      «در دست» = `cashHeldMinor` ِ فهرستِ پیک‌ها.
 *   ۳) پرس‌وجوی محدود: بازهٔ اجباری، to > from، حداکثر ۴۰۰ روز (۴۰۰ `bad_range`).
 *   ۴) اجازهٔ `delivery.settle`، جداییِ دکان‌ها، و بی پرچم ۴۰۴.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

let admin = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}

async function enable(shopId) {
  for (const key of ['shop.orders', 'shop.delivery']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
}

let seq = 0;
const freshKey = (p = 'k') => `${p}-${++seq}-${Date.now()}`;
const TOTAL = 1_240_000;
const order = () => ({
  source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
  customer: { name: 'احمد', phone: '0791234567' },
  address: { landmark: 'نزدیکِ مسجد', text: 'کارتهٔ چهار', lat: 34.5, lng: 69.1 },
  delivery: { photoConsent: false },
  lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
  deliveryFeeMinor: 40_000,
});

async function addCourier(owner, name) {
  const m = await member(owner.shopId, 'staff', name);
  const r = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, cashCapMinor: 50_000_000 }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...m, courierId: r.body.courier.id };
}

let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({
  eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra,
});

async function sendOk(token, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone-1', events }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

/** سفارشِ نقدِ هنگامِ تحویل ⇐ سپردن به پیک ⇐ تحویل با همهٔ نقد */
async function deliver(owner, courier) {
  const o = await h.post('/api/shop/orders', order(), { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(o.status, 201, JSON.stringify(o.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${o.body.order.id}/transition`, { to }, { token: owner.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  const a = await h.post(`/api/shop/delivery/orders/${o.body.order.id}/assign`,
    { mode: 'shop_courier', courierId: courier.courierId, overrideCashCap: true }, { token: owner.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  await sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up'), ev(id, 'delivered', { cashCollectedMinor: TOTAL })]);
  return id;
}

async function settle(owner, courier, ids, received, reason) {
  const r = await h.post(`/api/shop/delivery/couriers/${courier.courierId}/settlements`,
    { assignmentIds: ids, handedOverMinor: received, receivedMinor: received, ...(reason ? { reason } : {}) },
    { token: owner.token, headers: { 'Idempotency-Key': freshKey('s') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.settlement;
}

const report = (owner, from, to) => h.get(`/api/shop/delivery/cash-report?from=${from}&to=${to}`, { token: owner.token });

test('هر پیک: گرفته، تسویه‌نشده، تسویه با کسری و «در دست»؛ جمع‌ها با دفترِ تسویه و فهرستِ پیک‌ها می‌خوانند', async () => {
  const a = await shopOwner('گزارشِ نقدِ پیک');
  await enable(a.shopId);
  const c1 = await addCourier(a, 'پیکِ یک');
  const c2 = await addCourier(a, 'پیکِ دو');
  await addCourier(a, 'پیکِ بی‌کار');
  const from = Date.now() - 1000;

  const x1 = await deliver(a, c1);
  await deliver(a, c1);
  const y1 = await deliver(a, c2);
  await settle(a, c1, [x1], TOTAL);
  const s2 = await settle(a, c2, [y1], TOTAL - 10_000, 'شمارش در حضورِ پیک');
  assert.equal(s2.shortageMinor, 10_000);
  const to = Date.now() + 1000;

  const r = await report(a, from, to);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.from, from);
  assert.equal(r.body.to, to);
  //  پیکِ بی گردش و بی نقد ردیف نمی‌گیرد؛ ترتیب همان فهرستِ پیک‌ها
  assert.deepEqual(r.body.couriers.map((x) => x.name), ['پیکِ یک', 'پیکِ دو']);
  const [one, two] = r.body.couriers;
  assert.equal(one.courierId, c1.courierId);
  assert.deepEqual(
    [one.deliveries, one.expectedCashMinor, one.collectedCashMinor, one.unsettledMinor, one.settlements, one.receivedMinor, one.shortageMinor, one.inHandMinor],
    [2, 2 * TOTAL, 2 * TOTAL, TOTAL, 1, TOTAL, 0, TOTAL],
  );
  assert.deepEqual(
    [two.deliveries, two.collectedCashMinor, two.unsettledMinor, two.settlements, two.handoverDueMinor, two.receivedMinor, two.shortageMinor, two.excessMinor, two.inHandMinor],
    [1, TOTAL, 0, 1, TOTAL, TOTAL - 10_000, 10_000, 0, 0],
  );

  //  جمعِ ردیف‌ها = جمعِ کل (هر ستون)
  for (const k of Object.keys(r.body.totals)) {
    assert.equal(r.body.totals[k], r.body.couriers.reduce((s, x) => s + x[k], 0), k);
  }
  //  تطبیق با دفترِ تسویه در همان بازه
  const led = await h.get(`/api/shop/delivery/settlements?from=${from}&to=${to}`, { token: a.token });
  assert.equal(led.status, 200);
  assert.equal(r.body.totals.settlements, led.body.totals.settlements);
  assert.equal(r.body.totals.handoverDueMinor, led.body.totals.handoverDueMinor);
  assert.equal(r.body.totals.receivedMinor, led.body.totals.receivedMinor);
  assert.equal(r.body.totals.shortageMinor, led.body.totals.shortageMinor);
  assert.equal(r.body.totals.excessMinor, led.body.totals.excessMinor);
  assert.equal(r.body.totals.fareTotalMinor, led.body.totals.fareTotalMinor);
  //  «در دست» همان نقدِ فهرستِ پیک‌ها (DLV-11)
  const list = await h.get('/api/shop/delivery/couriers', { token: a.token });
  const held = Object.fromEntries((list.body.couriers || list.body.items || list.body).map((c) => [c.id, c.cashHeldMinor]));
  assert.equal(one.inHandMinor, held[c1.courierId]);
  assert.equal(two.inHandMinor, held[c2.courierId]);

  //  بازهٔ بعد: گردشی نیست، ولی نقدِ در دستِ پیکِ یک هنوز دیده می‌شود
  const later = await report(a, to, to + 60_000);
  assert.equal(later.status, 200);
  assert.deepEqual(later.body.couriers.map((x) => [x.name, x.deliveries, x.settlements, x.inHandMinor]), [['پیکِ یک', 0, 0, TOTAL]]);
  assert.equal(later.body.totals.collectedCashMinor, 0);
});

test('بازهٔ نادرست ۴۰۰، اجازهٔ تسویه لازم، دکانِ دیگر چیزی نمی‌بیند، بی پرچم ۴۰۴', async () => {
  const a = await shopOwner('گزارشِ نقد، مرزها');
  await enable(a.shopId);
  const c = await addCourier(a, 'پیکِ مرز');
  const from = Date.now() - 1000;
  await deliver(a, c);
  const to = Date.now() + 1000;

  const DAY = 86_400_000;
  for (const q of ['', `?from=${from}`, `?to=${to}`, `?from=${to}&to=${from}`, `?from=${from}&to=${from}`,
    `?from=x&to=${to}`, `?from=${from}&to=${from + 401 * DAY}`, `?from=1.5&to=${to}`]) {
    const r = await h.get(`/api/shop/delivery/cash-report${q}`, { token: a.token });
    assert.equal(r.status, 400, q);
    assert.equal(r.body.error.code, 'bad_range', q);
  }
  assert.equal((await h.get(`/api/shop/delivery/cash-report?from=${from}&to=${from + 400 * DAY}`, { token: a.token })).status, 200);

  const staff = await member(a.shopId, 'staff', 'شاگردِ گزارش');
  assert.equal((await report(staff, from, to)).status, 403);

  const b = await shopOwner('دکانِ دیگرِ گزارش');
  await enable(b.shopId);
  const rb = await report(b, from, to);
  assert.equal(rb.status, 200);
  assert.deepEqual(rb.body.couriers, []);
  assert.equal(rb.body.totals.collectedCashMinor, 0);

  const off = await shopOwner('بی پرچمِ گزارش');
  assert.equal((await report(off, from, to)).status, 404);
});
