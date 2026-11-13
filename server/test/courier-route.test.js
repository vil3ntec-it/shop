'use strict';
/**
 * چند سفارش در یک مسیر با ترتیبِ پیشنهادی — DLV-04 (پرچمِ `shop.delivery_route`).
 *
 * ترتیب را برنامهٔ پیک روی دستگاه می‌سازد (`core/courier/RoutePlan.forCards`؛ بردارِ `core/test-vectors/courier-route-v1.json`
 * در `RoutePlanVectorsTest`). آن‌چه این پرونده قفل می‌کند:
 *   ۱) پرچم فقط دکمه را در `GET /api/courier/me` (`routeEnabled`) نشان یا پنهان می‌کند؛ فهرستِ مأموریت‌ها همان.
 *   ۲) ⛔ درِ دوقلوی سرور `GET /api/courier/route` (بی صدازننده) با توکنِ پیک و پرچمِ روشن هم ۴۰۴ ِ «درِ ناشناختهٔ پیک»
 *      است و موقعیتِ پیک هیچ‌جا نمی‌نشیند (B15 ِ `docs/SECURITY-AUDIT-fa.md`، `dead-doors.test.js`).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
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
  //  ترتیب پیش‌فرض‌روشن است (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) ⇒ «خاموش» ِ این پرونده `false` ِ صریح است
  await setFlag(s.shopId, R.FLAG, false);
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

test('DLV-04: پرچم فقط `routeEnabled` ِ `/courier/me`؛ درِ مسیرِ سرور نیست (۴۰۴ ِ درِ پیک، با پرچمِ خاموش و روشن)', async () => {
  const s = await liveShop('مسیر');
  await mission(s, s.courier, { pin: [34.51, 69.17] });
  const gone = async () => {
    const r = await route(s.courier, '?lat=34.5&lng=69.17');
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'not_found', 'همان پاسخِ «این نشانی در درِ پیک نیست»');
  };
  await gone();
  assert.equal((await h.get('/api/courier/missions', { token: s.courier.token })).body.items.length, 1);
  assert.equal((await h.get('/api/courier/me', { token: s.courier.token })).body.routeEnabled, false, 'دکمهٔ مسیر پنهان');
  await setFlag(s.shopId, R.FLAG, true);
  assert.equal((await h.get('/api/courier/me', { token: s.courier.token })).body.routeEnabled, true, 'دکمهٔ مسیر دیده می‌شود');
  await gone();
  assert.equal((await many('SELECT * FROM delivery_locations')).length, 0, 'موقعیت هیچ‌جا ننشست');
});
