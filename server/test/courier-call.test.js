'use strict';
/**
 * «دکمهٔ تماس» روی کارتِ پیک — DLV-02 (§۶.۱۸)، با قاعدهٔ §۳.۱۱ و §۳۸: شمارهٔ مشتری در
 * فهرست و کارتِ پیک پوشیده است؛ شمارهٔ کامل فقط برای همان یک تماس بیرون می‌رود.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `shop.delivery_courier_call` ۴۰۴ و `/me` می‌گوید «خاموش».
 *   ۲) فقط مأموریتِ پذیرفته و زندهٔ همین پیک؛ پیش از قبول و پس از پایان ۴۰۹.
 *   ۳) پیکِ دیگرِ همین دکان، پیکِ دکانِ دیگر، شاگرد و صاحب ⇒ هیچ شماره‌ای؛ پیکِ غیرفعال ۴۰۳.
 *   ۴) هر تماس در سابقهٔ دکان می‌نشیند، بی خودِ شماره؛ پاسخ `no-store`؛ فهرست همچنان پوشیده.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');

const PHONE = '0791234567';
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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function enable(shopId, call = true) {
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.delivery', true);
  if (call) await setFlag(shopId, 'shop.delivery_courier_call', true);
}

let seq = 0;
const freshKey = (p = 'k') => `${p}-${++seq}-${Date.now()}`;

async function readyOrder(owner) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: PHONE },
    address: { landmark: 'پشتِ مسجد', text: 'کوچهٔ سوم' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
    deliveryFeeMinor: 40_000,
  }, { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: owner.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  return r.body.order;
}

async function addCourier(owner, name) {
  const m = await member(owner.shopId, 'staff', name);
  const r = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, cashCapMinor: 50_000_000 }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...m, courierId: r.body.courier.id };
}

async function assigned(owner, courier) {
  const order = await readyOrder(owner);
  const r = await h.post(`/api/shop/delivery/orders/${order.id}/assign`,
    { mode: 'shop_courier', courierId: courier.courierId }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { order, id: r.body.assignment.id };
}

let evSeq = 0;
async function events(token, id, ...types) {
  const r = await h.post('/api/courier/events', {
    deviceId: 'courier-phone-1',
    events: types.map((type) => ({
      eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId: id, type, deviceTime: Date.now(),
      ...(type === 'delivered' ? { cashCollectedMinor: 1_240_000 } : {}),
    })),
  }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

const call = (token, id) => h.post(`/api/courier/missions/${id}/call`, {}, { token });
const callLogs = (shopId) => many(`SELECT user_id, target_id, detail FROM audit_logs WHERE shop_id=$1 AND action='delivery.courier.call'`, [shopId]);

test('DLV-02: پیش‌فرض خاموش — بی پرچمِ تماس ۴۰۴، `/me` خاموش و هیچ سابقه‌ای', async () => {
  const a = await shopOwner('خاموش');
  await enable(a.shopId, false);
  const courier = await addCourier(a, 'پیکِ خاموش');
  const { id } = await assigned(a, courier);
  await events(courier.token, id, 'assigned');

  const me = await h.get('/api/courier/me', { token: courier.token });
  assert.equal(me.status, 200);
  assert.equal(me.body.callEnabled, false);
  const r = await call(courier.token, id);
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'courier_call_disabled');
  assert.equal(JSON.stringify(r.body).includes(PHONE.slice(1)), false);
  assert.equal((await callLogs(a.shopId)).length, 0);
});

test('DLV-02: پس از قبول تا پایان، شمارهٔ کامل فقط برای همین تماس؛ پیش از قبول و پس از پایان نه', async () => {
  const a = await shopOwner('تماس');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ تماس');
  const { order, id } = await assigned(a, courier);

  assert.equal((await h.get('/api/courier/me', { token: courier.token })).body.callEnabled, true);
  const early = await call(courier.token, id);
  assert.equal(early.status, 409, 'پیش از قبول، پیک فقط آن‌چه برای تصمیم لازم است را می‌بیند');
  assert.equal(early.body.error.code, 'not_accepted');

  for (const step of ['assigned', 'picked_up', 'arrived']) {
    await events(courier.token, id, step);
    const r = await call(courier.token, id);
    assert.equal(r.status, 200, `${step}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.phone.replace(/\D/g, '').endsWith(PHONE.slice(1)), true, r.body.phone);
    assert.equal(r.body.status, step);
    assert.equal(r.headers.get('cache-control'), 'no-store');
  }

  //  فهرست و کارت همچنان پوشیده
  const list = await h.get('/api/courier/missions', { token: courier.token });
  const raw = JSON.stringify(list.body);
  assert.equal(raw.includes(PHONE.slice(1)), false, raw);
  assert.ok(list.body.items.find((x) => x.id === id).customer.phoneMasked.includes('•'));

  //  سابقه: سه تماس، با پیک و مأموریت، بی خودِ شماره
  const logs = await callLogs(a.shopId);
  assert.equal(logs.length, 3);
  for (const l of logs) {
    assert.equal(l.user_id, courier.user.id);
    assert.equal(l.target_id, id);
    const d = typeof l.detail === 'string' ? l.detail : JSON.stringify(l.detail);
    assert.ok(d.includes(order.id), d);
    assert.equal(d.includes(PHONE.slice(1)), false, d);
  }

  await events(courier.token, id, 'delivered');
  const after = await call(courier.token, id);
  assert.equal(after.status, 409);
  assert.equal(after.body.error.code, 'not_live');
  assert.equal(JSON.stringify(after.body).includes(PHONE.slice(1)), false);
});

test('DLV-02: پیکِ دیگر، دکانِ دیگر، شاگرد، صاحب، پیکِ غیرفعال و پرچمِ تحویلِ خاموش هیچ شماره‌ای نمی‌گیرند', async () => {
  const a = await shopOwner('مرزِ تماس');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ اول');
  const other = await addCourier(a, 'پیکِ دوم');
  const { id } = await assigned(a, courier);
  await events(courier.token, id, 'assigned', 'picked_up');

  const b = await shopOwner('دکانِ دیگرِ تماس');
  await enable(b.shopId);
  const foreign = await addCourier(b, 'پیکِ دکانِ دیگر');
  const apprentice = await member(a.shopId, 'staff', 'شاگردِ تماس');

  const checks = [
    ['پیکِ دیگرِ همین دکان', other.token, 404, 'assignment_not_found'],
    ['پیکِ دکانِ دیگر', foreign.token, 404, 'assignment_not_found'],
    ['شاگردِ بی ردیفِ پیک', apprentice.token, 403, 'not_a_courier'],
    ['صاحبِ دکان (از درِ پیک نه)', a.token, 403, 'not_a_courier'],
  ];
  for (const [who, token, status, code] of checks) {
    const r = await call(token, id);
    assert.equal(r.status, status, `${who}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code, who);
    assert.equal(JSON.stringify(r.body).includes(PHONE.slice(1)), false, who);
  }

  const off = await h.post(`/api/shop/delivery/couriers/${courier.courierId}`, { active: false }, { token: a.token });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  const inactive = await call(courier.token, id);
  assert.equal(inactive.status, 403);
  assert.equal(inactive.body.error.code, 'courier_inactive');
  //  پیکِ غیرفعال رویدادِ مأموریتِ گرفته‌شده را هنوز می‌فرستد (§۲۸.۶) — تماس جدا بسته است
  await events(courier.token, id, 'arrived');

  await h.post(`/api/shop/delivery/couriers/${courier.courierId}`, { active: true }, { token: a.token });
  await setFlag(a.shopId, 'shop.delivery', false);
  const disabled = await call(courier.token, id);
  assert.equal(disabled.status, 404);
  assert.equal(disabled.body.error.code, 'delivery_disabled');

  assert.equal((await callLogs(a.shopId)).length, 0, 'هیچ تماسی ننشست');
  assert.equal((await callLogs(b.shopId)).length, 0);
});
