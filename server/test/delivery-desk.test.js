'use strict';
/**
 * میزِ تحویلِ برنامهٔ دکان (C01) — آن‌چه سرور برای رابطِ بومی اضافه کرد:
 *
 *   ۱) تابلوی مأموریت‌ها نامِ مشتری و وضعیتِ سفارش را هم می‌دهد (S43:
 *      «تطبیقِ نام» در دریافتِ حضوری؛ «سفارش هنوز آماده نیست»).
 *   ۲) `POST /couriers/:id` همان `PATCH` است (JVMِ برنامهٔ کامپیوتر PATCH
 *      نمی‌فرستد) — با همان دسترسی و همان ممیزی.
 *   ۳) شرکتِ پیکِ بیرونی (DLV-19): فهرست از خودِ مأموریت‌ها، کرایهٔ آخرین
 *      سپردن؛ صورت‌حسابِ بازه برای تطبیق — فقط صاحب/مدیر، جدا برای هر دکان،
 *      «پس‌گرفته» هرگز در صورت‌حساب نیست.
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

async function enable(shopId) {
  for (const key of ['shop.orders', 'shop.delivery']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
}

let seq = 0;
const freshKey = (p = 'k') => `${p}-${++seq}-${Date.now()}`;
const TOTAL = 1_240_000;

function orderBody(over = {}) {
  return {
    source: 'phone',
    fulfillment: 'delivery',
    paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'نزدیکِ مسجدِ جامع', text: 'کارتهٔ چهار' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
    deliveryFeeMinor: 40_000,
    ...over,
  };
}

async function packedOrder(owner, over) {
  const r = await h.post('/api/shop/orders', orderBody(over), { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = r.body.order.id;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: owner.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  return r.body.order;
}

const assign = (token, orderId, body) => h.post(`/api/shop/delivery/orders/${orderId}/assign`, body, { token });
const staffStatus = (token, id, body) => h.post(`/api/shop/delivery/assignments/${id}/status`, body, { token });

test('تابلو: نامِ مشتری و وضعیتِ سفارش کنارِ هر مأموریت', async () => {
  const a = await shopOwner('تابلو');
  await enable(a.shopId);
  const o = await packedOrder(a, { customer: { name: 'زهرا', phone: '0791234500' } });
  const r = await assign(a.token, o.id, { mode: 'external', externalName: 'پیک‌رسان', fareMinor: 20_000 });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const live = await h.get('/api/shop/delivery/assignments?scope=live', { token: a.token });
  assert.equal(live.status, 200);
  const it = live.body.items.find((x) => x.orderId === o.id);
  assert.ok(it, 'مأموریت در تابلو');
  assert.equal(it.customerName, 'زهرا');
  assert.equal(it.orderStatus, 'packed');
  assert.equal(it.fareMinor, 20_000);
  assert.equal(it.externalName, 'پیک‌رسان');
  assert.equal(it.customerPhone, undefined, 'شمارهٔ مشتری در تابلو نمی‌آید');
});

test('POST /couriers/:id همان PATCH است — سقفِ نقد و فعال/غیرفعال، فقط با دسترسیِ پیک‌ها', async () => {
  const a = await shopOwner('سقف');
  await enable(a.shopId);
  const m = await member(a.shopId, 'staff', 'پیکِ یک');
  const c = await h.post('/api/shop/delivery/couriers', { userId: m.user.id }, { token: a.token });
  assert.equal(c.status, 201, JSON.stringify(c.body));
  const id = c.body.courier.id;

  const up = await h.post(`/api/shop/delivery/couriers/${id}`, { cashCapMinor: 2_500_000 }, { token: a.token });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  assert.equal(up.body.courier.cashCapMinor, 2_500_000);
  assert.equal(up.body.courier.active, true);
  const off = await h.post(`/api/shop/delivery/couriers/${id}`, { active: false }, { token: a.token });
  assert.equal(off.body.courier.active, false);
  assert.equal(off.body.courier.cashCapMinor, 2_500_000, 'میدانِ نفرستاده دست نمی‌خورد');
  const audit = await one(
    `SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='delivery.courier.update'`, [a.shopId]
  );
  assert.equal(audit.n, 2, 'هر تغییر در سابقه');

  const staff = await member(a.shopId, 'staff', 'شاگرد');
  const denied = await h.post(`/api/shop/delivery/couriers/${id}`, { cashCapMinor: 1 }, { token: staff.token });
  assert.equal(denied.status, 403);
  const bad = await h.post(`/api/shop/delivery/couriers/${id}`, { cashCapMinor: -5 }, { token: a.token });
  assert.equal(bad.status, 400);

  const other = await shopOwner('دکانِ دیگر');
  await enable(other.shopId);
  const foreign = await h.post(`/api/shop/delivery/couriers/${id}`, { cashCapMinor: 1 }, { token: other.token });
  assert.equal(foreign.status, 404, 'پیکِ دکانِ دیگر پیدا نمی‌شود');
});

test('شرکتِ بیرونی (DLV-19): فهرست با کرایهٔ آخرین سپردن و صورت‌حسابِ بازه برای تطبیق', async () => {
  const a = await shopOwner('بیرونی');
  await enable(a.shopId);
  const before = Date.now() - 1000;

  const o1 = await packedOrder(a);
  const r1 = await assign(a.token, o1.id, { mode: 'external', externalName: 'پیک‌رسانِ شهر', externalRef: 'TRK-1', fareMinor: 15_000 });
  assert.equal(r1.status, 201, JSON.stringify(r1.body));
  const o2 = await packedOrder(a);
  const r2 = await assign(a.token, o2.id, { mode: 'external', externalName: 'پیک‌رسانِ شهر', externalRef: 'TRK-2', fareMinor: 18_000 });
  assert.equal(r2.status, 201);
  const o3 = await packedOrder(a);
  const r3 = await assign(a.token, o3.id, { mode: 'external', externalName: 'باربریِ غرب', fareMinor: 30_000 });
  assert.equal(r3.status, 201);
  const o4 = await packedOrder(a);
  const r4 = await assign(a.token, o4.id, { mode: 'external', externalName: 'پیک‌رسانِ شهر', externalRef: 'TRK-9', fareMinor: 18_000 });
  //  پیش از دادن به شرکت پس گرفته شد ⇒ هرگز در صورت‌حسابِ شرکت نیست
  const w = await staffStatus(a.token, r4.body.assignment.id, { to: 'withdrawn', reason: 'reassign' });
  assert.equal(w.status, 200, JSON.stringify(w.body));

  //  اولی تحویل شد (نقد گرفته شد)، دومی ناموفق
  assert.equal((await staffStatus(a.token, r1.body.assignment.id, { to: 'picked_up' })).status, 200);
  const d1 = await staffStatus(a.token, r1.body.assignment.id, { to: 'delivered', cashCollectedMinor: TOTAL });
  assert.equal(d1.status, 200, JSON.stringify(d1.body));
  assert.equal((await staffStatus(a.token, r2.body.assignment.id, { to: 'picked_up' })).status, 200);
  const f2 = await staffStatus(a.token, r2.body.assignment.id, { to: 'failed', reason: 'no_answer' });
  assert.equal(f2.status, 200, JSON.stringify(f2.body));

  const list = await h.get('/api/shop/delivery/external', { token: a.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  const city = list.body.items.find((x) => x.name === 'پیک‌رسانِ شهر');
  const west = list.body.items.find((x) => x.name === 'باربریِ غرب');
  assert.ok(city && west);
  assert.equal(city.assignments, 3);
  assert.equal(city.delivered, 1);
  assert.equal(city.live, 1, 'ناموفق هنوز زنده است');
  assert.equal(city.lastFareMinor, 18_000, 'کرایهٔ آخرین سپردن');
  assert.equal(west.lastFareMinor, 30_000);

  const q = (name, extra = '') => `/api/shop/delivery/external/statement?name=${encodeURIComponent(name)}${extra}`;
  const st = await h.get(q('پیک‌رسانِ شهر', `&from=${before}`), { token: a.token });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  assert.deepEqual(st.body.rows.map((r) => r.externalRef).sort(), ['TRK-1', 'TRK-2'], 'پس‌گرفته نمی‌آید');
  const row1 = st.body.rows.find((r) => r.externalRef === 'TRK-1');
  assert.equal(row1.status, 'delivered');
  assert.equal(row1.fareMinor, 15_000);
  assert.equal(row1.cashCollectedMinor, TOTAL);
  assert.equal(row1.orderNo > 0, true);
  assert.equal(st.body.rows.find((r) => r.externalRef === 'TRK-2').status, 'failed');
  assert.deepEqual(st.body.totals, { count: 2, delivered: 1, deliveredFareMinor: 15_000, deliveredCashMinor: TOTAL });
  assert.equal(st.body.truncated, false);

  const future = await h.get(q('پیک‌رسانِ شهر', `&from=${Date.now() + 60_000}`), { token: a.token });
  assert.equal(future.body.rows.length, 0, 'بازه رعایت می‌شود');
  const upto = await h.get(q('پیک‌رسانِ شهر', `&to=${before}`), { token: a.token });
  assert.equal(upto.body.rows.length, 0);

  assert.equal((await h.get('/api/shop/delivery/external/statement', { token: a.token })).status, 400);
  assert.equal((await h.get(q('پیک‌رسانِ شهر', '&from=abc'), { token: a.token })).status, 400);

  //  شاگرد فهرست را می‌بیند (برای فرمِ سپردن) ولی صورت‌حسابِ پولی را نه
  const staff = await member(a.shopId, 'staff', 'شاگردِ بیرونی');
  assert.equal((await h.get('/api/shop/delivery/external', { token: staff.token })).status, 200);
  assert.equal((await h.get(q('پیک‌رسانِ شهر'), { token: staff.token })).status, 403);

  //  دکانِ دیگر هیچ‌چیزِ این دکان را نمی‌بیند
  const b = await shopOwner('بیرونیِ دیگر');
  await enable(b.shopId);
  assert.deepEqual((await h.get('/api/shop/delivery/external', { token: b.token })).body.items, []);
  assert.deepEqual((await h.get(q('پیک‌رسانِ شهر'), { token: b.token })).body.rows, []);
});

test('بی پرچم: فهرست و صورت‌حسابِ شرکت هم ۴۰۴ است', async () => {
  const a = await shopOwner('خاموش');
  for (const path of ['/api/shop/delivery/external', '/api/shop/delivery/external/statement?name=x']) {
    const r = await h.get(path, { token: a.token });
    assert.equal(r.status, 404, path);
    assert.equal(r.body.error.code, 'delivery_disabled');
  }
});
