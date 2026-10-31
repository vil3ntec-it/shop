'use strict';
/**
 * S5 (قراردادِ بازار §۳، §۴۰.۱) — نمای پیک برای مشتری در پیگیریِ سفارشِ ویترین.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.delivery_customer_view` پاسخِ مشتری همان
 *      شکلِ پیشین است (بی کلیدِ `delivery`)، درهای تازهٔ پیک ۴۰۴ و کد بازسازی‌پذیر نیست.
 *   ۲) وضعیتِ عمومی، نامِ کوچکِ پیک (بی شماره و شناسه)، «تماس» از راهِ گفت‌وگوی
 *      سفارش، کدِ تحویل (همان کدِ کارمند) و زمانِ تقریبی؛ پس از تحویل کد نیست.
 *   ۳) موقعیت پشتِ پرچمِ جدای `shop.delivery_live_location`، فقط «در راه»، گرد
 *      شده؛ با «رسیدم» و «تحویل» همان لحظه پاک؛ نقطهٔ کهنه دیده نمی‌شود و پاک می‌شود.
 *   ۴) مشتریِ سفارشِ دیگر (همین دکان یا دکانِ دیگر) هیچ چیزی نمی‌بیند؛ پیکِ دیگر
 *      برای مأموریتِ دیگری موقعیت نمی‌فرستد.
 *   ۵) مهاجرتِ ۱۳۲: برگشت (ROLLBACK) و اجرای دوباره.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const delivery = require('../src/lib/delivery');

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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین، تحویل و یک پیک. */
async function liveShop(label) {
  const o = await shopOwner(label);
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await setFlag(o.shopId, k, true);
  const slug = `s-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(o.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

async function addCourier(owner, name) {
  const u = await h.newUser(`پیک ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), owner.shopId, u.user.id, now()]
  );
  const r = await h.post('/api/shop/delivery/couriers', { userId: u.user.id, name, phone: '0799999999' }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: u.accessToken, userId: u.user.id, courierId: r.body.courier.id };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

/** سفارشِ ارسالیِ ویترین، بسته‌بندی‌شده (آمادهٔ سپردن به پیک). */
async function packedOrder(s, phone = '0791112233') {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery',
    customer: { name: 'مشتری', phone },
    address: { lat: 34.523456, lng: 69.181234, landmark: 'نزدیکِ مسجد' },
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  return { id, token: r.body.order.token };
}

const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra });
async function sendOk(token, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone', events }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}
const assignTo = (s, orderId, courierId) => h.post(`/api/shop/delivery/orders/${orderId}/assign`,
  { mode: 'shop_courier', courierId, requireCode: true, overrideCashCap: true }, { token: s.token });
const location = (token, id, body) => h.post(`/api/courier/missions/${id}/location`, body, { token });
const eta = (token, id, minutes) => h.post(`/api/courier/missions/${id}/eta`, { minutes }, { token });

/* ══════════════════════════════════════════════════════════════════ */

test('پیش‌فرض خاموش: پاسخِ مشتری بی `delivery`، درهای تازه ۴۰۴، کد بازسازی‌پذیر نیست', async () => {
  const s = await liveShop('خاموش');
  const courier = await addCourier(s, 'کریم احمدی');
  const o = await packedOrder(s);
  const a = await assignTo(s, o.id, courier.courierId);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  await sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up')]);

  const t = await track(s, o.token);
  assert.equal(t.status, 200);
  assert.equal('delivery' in t.body.order, false, 'شکلِ پاسخ همان پیشین');
  assert.equal((await one('SELECT code_nonce FROM delivery_assignments WHERE id=$1', [id])).code_nonce, '');
  const e = await eta(courier.token, id, 15);
  assert.equal(e.status, 404);
  assert.equal(e.body.error.code, 'customer_view_disabled');
  //  پرچمِ موقعیت به‌تنهایی هیچ دری باز نمی‌کند
  await setFlag(s.shopId, delivery.LOCATION_FLAG, true);
  const l = await location(courier.token, id, { lat: 34.5, lng: 69.1 });
  assert.equal(l.status, 404);
  assert.equal(l.body.error.code, 'live_location_disabled');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM delivery_locations WHERE assignment_id=$1', [id])).n, 0);
});

test('مسیرِ کامل: وضعیت، نامِ کوچک، کد، زمانِ تقریبی و موقعیت فقط «در راه»؛ پس از تحویل هیچ', async () => {
  const s = await liveShop('روشن');
  await setFlag(s.shopId, delivery.VIEW_FLAG, true);
  const courier = await addCourier(s, 'محمود رحیمی');
  const o = await packedOrder(s);
  const a = await assignTo(s, o.id, courier.courierId);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  const staffCode = a.body.code;
  assert.match(staffCode, /^\d{6}$/);

  //  هنوز قبول نشده: «منتظرِ پیک»، بی نام؛ کد از همین حالا برای مشتری
  let d = (await track(s, o.token)).body.order.delivery;
  assert.equal(d.status, 'waiting');
  assert.equal(d.courierName, null);
  assert.equal(d.code, staffCode, 'مشتری همان کدی را می‌بیند که پیک می‌پرسد');
  assert.deepEqual(d.contact, { via: 'order_chat', available: false }, 'بی پرچمِ گفت‌وگو تماس در دسترس نیست');

  await sendOk(courier.token, [ev(id, 'assigned')]);
  d = (await track(s, o.token)).body.order.delivery;
  assert.equal(d.status, 'assigned');
  assert.equal(d.courierName, 'محمود', 'فقط نامِ کوچک');
  //  جلوتر از «گرفتنِ بسته» نه زمان، نه موقعیت
  assert.equal((await eta(courier.token, id, 20)).body.error.code, 'not_on_the_way');

  await sendOk(courier.token, [ev(id, 'picked_up')]);
  const e = await eta(courier.token, id, 20);
  assert.equal(e.status, 200, JSON.stringify(e.body));
  assert.equal((await eta(courier.token, id, 0)).status, 400);
  assert.equal((await eta(courier.token, id, 241)).status, 400);
  //  موقعیت هنوز خاموش است (پرچمِ جدا)
  assert.equal((await location(courier.token, id, { lat: 34.5, lng: 69.1 })).body.error.code, 'live_location_disabled');
  d = (await track(s, o.token)).body.order.delivery;
  assert.equal(d.status, 'on_the_way');
  assert.equal(d.etaAt, e.body.etaAt);
  assert.equal(d.location, null);

  await setFlag(s.shopId, 'shop.chat', true);
  await setFlag(s.shopId, delivery.LOCATION_FLAG, true);
  assert.equal((await location(courier.token, id, { lat: 91, lng: 69.1 })).status, 400);
  assert.equal((await location(courier.token, id, { lat: '34.5', lng: 69.1 })).status, 400);
  const l1 = await location(courier.token, id, { lat: 34.531234, lng: 69.172345, accuracyM: 12, deviceTime: Date.now() });
  assert.equal(l1.status, 200, JSON.stringify(l1.body));
  assert.equal(l1.body.accepted, true);
  const l2 = await location(courier.token, id, { lat: 34.6, lng: 69.2 });
  assert.equal(l2.body.accepted, false, 'نقطه‌های تندتر از ۵ ثانیه نادیده');
  d = (await track(s, o.token)).body.order.delivery;
  assert.deepEqual({ lat: d.location.lat, lng: d.location.lng, approximate: d.location.approximate },
    { lat: 34.531, lng: 69.172, approximate: true }, 'گرد شده، نه نقطهٔ دقیق');
  assert.deepEqual(d.contact, { via: 'order_chat', available: true });

  //  ⛔ هیچ شماره، شناسه یا نقطهٔ دقیقی در پاسخ نیست
  const raw = JSON.stringify((await track(s, o.token)).body);
  for (const secret of ['0799999999', courier.courierId, courier.userId, id, '34.531234', 'رحیمی']) {
    assert.equal(raw.includes(secret), false, `نباید باشد: ${secret}`);
  }

  //  «رسیدم» ⇒ موقعیت همان لحظه پاک، زمانِ تقریبی هم
  await sendOk(courier.token, [ev(id, 'arrived')]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM delivery_locations WHERE assignment_id=$1', [id])).n, 0);
  assert.equal((await location(courier.token, id, { lat: 34.5, lng: 69.1 })).body.error.code, 'not_on_the_way');
  d = (await track(s, o.token)).body.order.delivery;
  assert.equal(d.status, 'arrived');
  assert.equal(d.location, null);
  assert.equal(d.etaAt, null);
  assert.equal(d.code, staffCode);

  //  تحویل با کدی که مشتری دید ⇒ کد دیگر نه دیده می‌شود نه بازسازی
  await sendOk(courier.token, [ev(id, 'delivered', { code: d.code, cashCollectedMinor: 12000 })]);
  d = (await track(s, o.token)).body.order.delivery;
  assert.equal(d.status, 'delivered');
  assert.equal(d.code, null);
  assert.equal(d.location, null);
  assert.equal(d.courierName, null);
  assert.equal(d.contact.available, false);
  const row = await one('SELECT code_nonce, eta_at FROM delivery_assignments WHERE id=$1', [id]);
  assert.deepEqual({ ...row }, { code_nonce: '', eta_at: null });
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM delivery_locations WHERE assignment_id=$1', [id])).n, 0);
});

test('مشتریِ سفارشِ دیگر و پیکِ دیگر هیچ چیزی نمی‌بینند و نمی‌فرستند', async () => {
  const s = await liveShop('جدایی');
  const other = await liveShop('دکانِ دیگر');
  for (const shop of [s, other]) {
    await setFlag(shop.shopId, delivery.VIEW_FLAG, true);
    await setFlag(shop.shopId, delivery.LOCATION_FLAG, true);
  }
  const courier = await addCourier(s, 'نادر');
  const stranger = await addCourier(s, 'ناصر');
  const mine = await packedOrder(s, '0791110001');
  const theirs = await packedOrder(s, '0791110002');
  const a = await assignTo(s, mine.id, courier.courierId);
  const id = a.body.assignment.id;
  await sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up')]);
  assert.equal((await location(courier.token, id, { lat: 34.5, lng: 69.1 })).body.accepted, true);

  //  سفارشِ دیگرِ همین دکان: مأموریتی ندارد ⇒ هیچ
  const t2 = await track(s, theirs.token);
  assert.equal(t2.status, 200);
  assert.equal(t2.body.order.delivery, null);
  //  توکنِ دستکاری‌شده و توکنِ این دکان روی دکانِ دیگر ⇒ ۴۰۴
  const forged = `${id}.${mine.token.split('.')[1]}`;
  assert.equal((await track(s, forged)).status, 404);
  assert.equal((await track(other, mine.token)).status, 404);
  //  پیکِ دیگرِ همین دکان: «پیدا نشد» و هیچ ردیفی عوض نمی‌شود
  const before = await one('SELECT lat, server_time FROM delivery_locations WHERE assignment_id=$1', [id]);
  const steal = await location(stranger.token, id, { lat: 10, lng: 10 });
  assert.equal(steal.status, 404);
  assert.equal(steal.body.error.code, 'assignment_not_found');
  assert.equal((await eta(stranger.token, id, 5)).status, 404);
  assert.deepEqual(await one('SELECT lat, server_time FROM delivery_locations WHERE assignment_id=$1', [id]), before);
});

test('نگهداریِ کوتاه: نقطهٔ کهنه نشان داده نمی‌شود و پاک می‌شود', async () => {
  const s = await liveShop('نگهداری');
  await setFlag(s.shopId, delivery.VIEW_FLAG, true);
  await setFlag(s.shopId, delivery.LOCATION_FLAG, true);
  const courier = await addCourier(s, 'جاوید');
  const o = await packedOrder(s);
  const id = (await assignTo(s, o.id, courier.courierId)).body.assignment.id;
  await sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up')]);
  const old = now() - delivery.LOCATION_TTL_MS - 1000;
  await query(
    `INSERT INTO delivery_locations (assignment_id, shop_id, lat, lng, server_time) VALUES ($1,$2,34.5,69.1,$3)`,
    [id, s.shopId, old]
  );
  assert.equal((await track(s, o.token)).body.order.delivery.location, null);
  await delivery.purgeLocations();
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM delivery_locations WHERE assignment_id=$1', [id])).n, 0);
});

test('مهاجرتِ ۱۳۲: برگشت و اجرای دوباره', async () => {
  const sql = fs.readFileSync(path.join(__dirname, '..', 'migrations', '132_delivery_customer_view.sql'), 'utf8');
  const lines = sql.split('\n');
  const start = lines.indexOf('-- ROLLBACK:');
  assert.ok(start >= 0);
  const rollback = [];
  for (const l of lines.slice(start + 1)) {
    if (!l.startsWith('-- ')) break;
    rollback.push(l.slice(3));
  }
  assert.equal(rollback.length, 3);
  const cols = async () => (await query(
    `SELECT column_name FROM information_schema.columns
      WHERE table_name='delivery_assignments' AND column_name IN ('code_nonce','eta_at') ORDER BY 1`
  )).rows.map((r) => r.column_name);
  const table = async () => Boolean((await one("SELECT to_regclass('delivery_locations') AS t")).t);

  for (const stmt of rollback) await query(stmt);
  assert.deepEqual(await cols(), []);
  assert.equal(await table(), false);
  await query(sql);
  assert.deepEqual(await cols(), ['code_nonce', 'eta_at']);
  assert.equal(await table(), true);
  await query(sql);   // دوباره بی‌خطر (IF NOT EXISTS)
});
