'use strict';
/**
 * سقفِ کارِ هم‌زمانِ پیک در سرور — DLV-03 (P1: «خودکار … با سقفِ کارِ هم‌زمان»).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) بی سقف (همهٔ پیک‌های امروز) رفتار همان پیشین است.
 *   ۲) سقف ۱ تا ۵۰؛ `null` سقف را برمی‌دارد؛ نیامدن همان قبلی؛ مقدارِ نامعتبر ۴۰۰.
 *   ۳) پیکِ به‌سقف‌رسیده کارِ تازه نمی‌گیرد (۴۰۹ `courier_busy`) مگر مدیر آگاهانه
 *      `overrideLiveCap` بزند — که در رویدادِ ساخت ثبت می‌شود. «رد»، «پس‌گرفتن» و پایانِ
 *      مأموریت جا را باز می‌کنند (همان شمارشِ مأموریتِ زنده).
 *   ۴) دو سپردنِ هم‌زمان به یک پیک با یک جای خالی: فقط یکی می‌نشیند.
 *   ۵) شاگرد سقف را عوض نمی‌کند؛ دکانِ دیگر پیکِ این دکان را نمی‌بیند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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

async function enable(shopId) {
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.delivery', true);
}

let seq = 0;
const freshKey = (p = 'k') => `${p}-${++seq}-${Date.now()}`;

/** سفارشِ پیش‌پرداخت‌نشدهٔ «نقدِ هنگامِ تحویل» با مبلغِ کم — سقفِ نقد مزاحمِ این آزمون نیست */
async function readyOrder(owner) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'پشتِ مسجد' },
    lines: [{ name: 'نان', qtyMilli: 1000, unitPriceMinor: 1_000 }],
  }, { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: owner.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  return r.body.order;
}

async function addCourier(owner, name, over = {}) {
  const m = await member(owner.shopId, 'staff', name);
  const r = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, ...over }, { token: owner.token });
  return { ...m, res: r, courierId: r.body.courier?.id };
}

const assign = (owner, orderId, courierId, extra = {}) =>
  h.post(`/api/shop/delivery/orders/${orderId}/assign`, { mode: 'shop_courier', courierId, ...extra }, { token: owner.token });
const update = (token, courierId, body) => h.post(`/api/shop/delivery/couriers/${courierId}`, body, { token });

test('DLV-03: سقفِ کارِ هم‌زمان — ۱ تا ۵۰، null برمی‌دارد، نیامدن همان قبلی، بی سقف همان رفتارِ پیشین', async () => {
  const a = await shopOwner('سقف');
  await enable(a.shopId);
  const free = await addCourier(a, 'پیکِ بی‌سقف');
  assert.equal(free.res.status, 201, JSON.stringify(free.res.body));
  assert.equal(free.res.body.courier.maxLive, null, 'پیش‌فرض بی‌سقف');
  for (let i = 0; i < 4; i++) {
    const o = await readyOrder(a);
    const r = await assign(a, o.id, free.courierId);
    assert.equal(r.status, 201, `بی‌سقف: ${JSON.stringify(r.body)}`);
  }

  for (const bad of [0, 51, -1, 1.5, '2', true]) {
    const r = await update(a.token, free.courierId, { maxLive: bad });
    assert.equal(r.status, 400, `${JSON.stringify(bad)}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_number');
  }
  const capped = await addCourier(a, 'پیکِ دوتایی', { maxLive: 2 });
  assert.equal(capped.res.status, 201, JSON.stringify(capped.res.body));
  assert.equal(capped.res.body.courier.maxLive, 2);
  const rename = await update(a.token, capped.courierId, { name: 'پیکِ دوتاییِ تازه' });
  assert.equal(rename.body.courier.maxLive, 2, 'نیامدنِ maxLive سقف را دست نمی‌زند');
  const cleared = await update(a.token, capped.courierId, { maxLive: null });
  assert.equal(cleared.body.courier.maxLive, null);
  const back = await update(a.token, capped.courierId, { maxLive: 50 });
  assert.equal(back.body.courier.maxLive, 50);
  const list = await h.get('/api/shop/delivery/couriers', { token: a.token });
  assert.equal(list.body.items.find((c) => c.id === free.courierId).liveCount, 4);
  assert.equal(list.body.items.find((c) => c.id === capped.courierId).maxLive, 50);
});

test('DLV-03: پیکِ به‌سقف‌رسیده کارِ تازه نمی‌گیرد مگر آگاهانه؛ رد، پس‌گرفتن و پایان جا باز می‌کنند', async () => {
  const a = await shopOwner('پر');
  await enable(a.shopId);
  const c = await addCourier(a, 'پیکِ پر', { maxLive: 2 });
  const o1 = await readyOrder(a);
  const o2 = await readyOrder(a);
  const o3 = await readyOrder(a);
  const a1 = await assign(a, o1.id, c.courierId);
  const a2 = await assign(a, o2.id, c.courierId);
  assert.equal(a1.status, 201, JSON.stringify(a1.body));
  assert.equal(a2.status, 201, JSON.stringify(a2.body));

  const busy = await assign(a, o3.id, c.courierId);
  assert.equal(busy.status, 409, JSON.stringify(busy.body));
  assert.equal(busy.body.error.code, 'courier_busy');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM delivery_assignments WHERE order_id=$1', [o3.id])).n), 0);

  const bad = await assign(a, o3.id, c.courierId, { overrideLiveCap: 'yes' });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_override');

  //  مدیر آگاهانه می‌سپارد ⇒ در رویدادِ ساخت ثبت می‌شود
  const forced = await assign(a, o3.id, c.courierId, { overrideLiveCap: true });
  assert.equal(forced.status, 201, JSON.stringify(forced.body));
  const ev = await one(`SELECT detail FROM delivery_events WHERE assignment_id=$1 AND kind='created'`, [forced.body.assignment.id]);
  const detail = typeof ev.detail === 'string' ? JSON.parse(ev.detail) : ev.detail;
  assert.equal(detail.overLiveCap, true);
  const plain = await one(`SELECT detail FROM delivery_events WHERE assignment_id=$1 AND kind='created'`, [a1.body.assignment.id]);
  assert.equal((typeof plain.detail === 'string' ? JSON.parse(plain.detail) : plain.detail).overLiveCap, undefined);

  //  «رد» ِ پیک و «پس‌گرفتن» ِ دکان جا باز می‌کنند (۳ ⇒ ۱ زنده)
  const ev1 = await h.post('/api/courier/events', { deviceId: 'p1', events: [{
    eventId: `ev-${Date.now()}-1`, assignmentId: a1.body.assignment.id, type: 'rejected', reason: 'busy', deviceTime: Date.now(),
  }] }, { token: c.token });
  assert.equal(ev1.body.results[0].status, 'applied', JSON.stringify(ev1.body));
  const wd = await h.post(`/api/shop/delivery/assignments/${a2.body.assignment.id}/status`,
    { to: 'withdrawn', reason: 'reassign' }, { token: a.token });
  assert.equal(wd.status, 200, JSON.stringify(wd.body));
  const o4 = await readyOrder(a);
  const again = await assign(a, o4.id, c.courierId);
  assert.equal(again.status, 201, `یک زنده از سقفِ ۲: ${JSON.stringify(again.body)}`);
  const o5 = await readyOrder(a);
  const full = await assign(a, o5.id, c.courierId);
  assert.equal(full.body.error?.code, 'courier_busy', 'دوباره پر');
});

test('DLV-03: دو سپردنِ هم‌زمان با یک جای خالی فقط یکی می‌نشیند؛ شاگرد و دکانِ دیگر سقف را عوض نمی‌کنند', async () => {
  const a = await shopOwner('هم‌زمان');
  await enable(a.shopId);
  const c = await addCourier(a, 'پیکِ تک', { maxLive: 1 });
  const [x, y] = [await readyOrder(a), await readyOrder(a)];
  const both = await Promise.all([assign(a, x.id, c.courierId), assign(a, y.id, c.courierId)]);
  assert.deepEqual(both.map((r) => r.status).sort(), [201, 409], JSON.stringify(both.map((r) => r.body)));
  assert.equal(both.find((r) => r.status === 409).body.error.code, 'courier_busy');
  const live = await one(
    `SELECT COUNT(*)::int AS n FROM delivery_assignments WHERE courier_id=$1
        AND status IN ('requested','assigned','picked_up','moving','arrived','failed')`, [c.courierId]);
  assert.equal(Number(live.n), 1);

  const apprentice = await member(a.shopId, 'staff', 'شاگردِ سقف');
  const denied = await update(apprentice.token, c.courierId, { maxLive: 10 });
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  const b = await shopOwner('دکانِ دیگرِ سقف');
  await enable(b.shopId);
  const other = await update(b.token, c.courierId, { maxLive: 10 });
  assert.equal(other.status, 404, JSON.stringify(other.body));
  assert.equal(Number((await one('SELECT max_live FROM shop_couriers WHERE id=$1', [c.courierId])).max_live), 1);
});
