'use strict';
/**
 * چند سفارش در یک مسیر با ترتیبِ پیشنهادی — DLV-04 (`GET /api/courier/route`، پشتِ پرچمِ خاموشِ `shop.delivery_route`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ترتیب همان بردارِ `core/test-vectors/courier-route-v1.json` است که `RoutePlan.kt` می‌خواند.
 *   ۲) پرچمِ خاموش ⇒ ۴۰۴ `delivery_route_disabled`؛ فهرستِ مأموریت‌ها همان پیشین.
 *   ۳) فقط مأموریتِ پذیرفته و زندهٔ **همین** پیک ایستگاه است (نه «درخواست‌شده»، نه پایان‌یافته، نه پیکِ دیگر)؛
 *      بی پین ⇒ ته؛ موقعیتِ پیک فقط برای همین پاسخ (نیمه یا نامعتبر ⇒ ۴۰۰) و `no-store`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');
const R = require('../src/lib/courier-route');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const key = (p = 'k') => `${p}-${Date.now().toString(36)}${++seq}-key`;

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
test.beforeEach(() => resetAll());

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function courierOf(shop, name) {
  const c = await h.newUser(`پیک ${name} ${++seq}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), shop.shopId, c.user.id, now()]
  );
  const r = await h.post('/api/shop/delivery/couriers', { userId: c.user.id, name, phone: '0799999999' }, { token: shop.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: c.accessToken, id: r.body.courier.id };
}

async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.delivery']) await setFlag(s.shopId, k, true);
  return { ...s, courier: await courierOf(s, 'اول') };
}

let evSeq = 0;
const ev = (assignmentId, type) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now() });

/** سفارشِ بسته‌شده، سپرده به `courier`؛ `accept` ⇒ پیک قبول کرده. ⇒ شناسهٔ مأموریت */
async function mission(s, courier, { pin = null, landmark = 'نشانه', accept = true } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark, ...(pin ? { lat: pin[0], lng: pin[1] } : {}) },
    lines: [{ name: 'نان', qtyMilli: 1000, unitPriceMinor: 1000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    assert.equal((await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: s.token })).status, 200);
  }
  const a = await h.post(`/api/shop/delivery/orders/${r.body.order.id}/assign`,
    { mode: 'shop_courier', courierId: courier.id, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  if (accept) await events(courier, [ev(a.body.assignment.id, 'assigned')]);
  return a.body.assignment.id;
}
async function events(courier, list) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone', events: list }, { token: courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}
const route = (courier, q = '') => h.get(`/api/courier/route${q}`, { token: courier.token });

/* ══════════════════════════════════════════════════════════════════ */

test('DLV-04 courier-route-v1: نزدیک‌ترین بعدی + 2-opt — همان بردارِ RoutePlan.kt', () => {
  const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'courier-route-v1.json'), 'utf8'));
  assert.equal(V.version, 1);
  assert.ok(V.plan.length >= 8);
  const pt = (x) => (x ? { lat: x[0], lng: x[1] } : null);
  for (const c of V.plan) assert.deepEqual(R.plan(pt(c.start), c.pins.map(pt)), c.want, c.name);
  //  2-opt واقعاً کار می‌کند: در دو بردار از «نزدیک‌ترین بعدیِ» تنها کوتاه‌تر است
  const { distanceM } = require('../src/lib/courier-checks');
  const nnTotal = (start, pins) => {
    const left = pins.map((_, i) => i);
    let cur = start;
    let total = 0;
    while (left.length) {
      left.sort((a, b) => distanceM(cur, pins[a]) - distanceM(cur, pins[b]) || a - b);
      const i = left.shift();
      total += distanceM(cur, pins[i]);
      cur = pins[i];
    }
    return total;
  };
  const twoOpt = V.plan.filter((c) => c.name.startsWith('2-opt'));
  assert.equal(twoOpt.length, 2);
  for (const c of twoOpt) assert.ok(c.want.totalM < nnTotal(pt(c.start), c.pins.map(pt)), c.name);
});

test('DLV-04 خاموش: ۴۰۴ delivery_route_disabled؛ فهرستِ مأموریت‌ها همان', async () => {
  const s = await liveShop('مسیر-خاموش');
  await mission(s, s.courier, { pin: [34.51, 69.17] });
  const r = await route(s.courier, '?lat=34.5&lng=69.17');
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'delivery_route_disabled');
  assert.equal((await h.get('/api/courier/missions', { token: s.courier.token })).body.items.length, 1);
  assert.equal((await h.get('/api/courier/me', { token: s.courier.token })).body.routeEnabled, false, 'دکمهٔ مسیر پنهان');
  await setFlag(s.shopId, R.FLAG, true);
  assert.equal((await h.get('/api/courier/me', { token: s.courier.token })).body.routeEnabled, true, 'دکمهٔ مسیر دیده می‌شود');
});

test('DLV-04: ترتیبِ پیشنهادی فقط برای مأموریتِ پذیرفتهٔ همین پیک؛ بی پین ته؛ موقعیت نیمه یا نامعتبر ۴۰۰؛ no-store', async () => {
  const s = await liveShop('مسیر');
  await setFlag(s.shopId, R.FLAG, true);
  const far = await mission(s, s.courier, { pin: [34.527, 69.17], landmark: 'سه کیلومتر' });
  const near = await mission(s, s.courier, { pin: [34.509, 69.17], landmark: 'یک کیلومتر' });
  const bare = await mission(s, s.courier, { landmark: 'بی پین' });
  const mid = await mission(s, s.courier, { pin: [34.518, 69.17], landmark: 'دو کیلومتر' });
  const waiting = await mission(s, s.courier, { pin: [34.501, 69.17], accept: false });
  const done = await mission(s, s.courier, { pin: [34.502, 69.17] });
  await events(s.courier, [ev(done, 'picked_up'), { ...ev(done, 'delivered'), cashCollectedMinor: 1000 }]);
  const other = await courierOf(s, 'دوم');
  const notMine = await mission(s, other, { pin: [34.5005, 69.17] });
  await events(s.courier, [ev(mid, 'picked_up')]);

  const r = await route(s.courier, '?lat=34.5&lng=69.17');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.from, 'position');
  assert.deepEqual(r.body.stops.map((x) => [x.assignmentId, x.seq, x.legM]), [[near, 1, 1001], [mid, 2, 1001], [far, 3, 1001]]);
  assert.equal(r.body.stops[1].status, 'picked_up');
  assert.equal(r.body.stops[0].landmark, 'یک کیلومتر');
  assert.deepEqual(r.body.unpinned.map((x) => x.assignmentId), [bare]);
  assert.equal(r.body.totalM, 3003);
  const all = JSON.stringify(r.body);
  for (const id of [waiting, done, notMine]) assert.ok(!all.includes(id), `ایستگاهِ نابه‌جا: ${id}`);

  const noPos = await route(s.courier);
  assert.equal(noPos.status, 200);
  assert.equal(noPos.body.from, 'first_stop');
  assert.equal(noPos.body.stops[0].legM, 0);
  assert.deepEqual(noPos.body.stops.map((x) => x.assignmentId).sort(), [far, near, mid].sort());

  for (const q of ['?lat=34.5', '?lng=69', '?lat=91&lng=69', '?lat=x&lng=69', '?lat=34.5&lng=181']) {
    const b = await route(s.courier, q);
    assert.equal(b.status, 400, `${q}: ${JSON.stringify(b.body)}`);
    assert.equal(b.body.error.code, 'bad_position');
  }
  //  فقط پیشنهاد: ترتیبِ فهرستِ مأموریت‌ها و وضعیت‌ها دست نخورد، و موقعیت هیچ‌جا ننشست
  const list = (await h.get('/api/courier/missions', { token: s.courier.token })).body.items.map((x) => x.id);
  assert.deepEqual(list, [far, near, bare, mid, waiting]);
  assert.equal((await many('SELECT * FROM delivery_locations')).length, 0);
});
