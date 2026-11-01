'use strict';
/**
 * نشانیِ تحویل برای پیک — DLV-02 (کارتِ «اول نشانه»: نشانه، ناحیه، رنگِ دروازه)
 * و DLV-09 («اصلاحِ نشانی» به‌عنوانِ کارِ بعدی پس از ناموفق).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) نشانیِ سفارش ناحیه و رنگِ دروازه را نگه می‌دارد؛ این دو جای نشانی نیستند
 *      (بی پین/نشانه/متن ۴۰۰) و هر کلیدِ دیگرِ ورودی دور ریخته می‌شود.
 *   ۲) کارتِ پیک نشانه، ناحیه و رنگِ دروازه را فقط تا مأموریت زنده است می‌دهد.
 *   ۳) پیک «نشانی پیدا نشد» ⇐ دکان نشانی را اصلاح می‌کند ⇐ کارتِ پیک نشانیِ
 *      تازه و `correctedAt` را می‌بیند ⇐ تلاشِ دوباره ⇐ تحویل.
 *   ۴) کلیدِ یکتایی: تکرار همان پاسخ بی بالا رفتنِ دوبارهٔ نسخه؛ همان کلید با
 *      بدنهٔ دیگر ۴۰۹. نسخهٔ کهنه ۴۰۹ با وضعِ تازه. هم‌زمانی: یکی می‌نشیند.
 *   ۵) مأموریتِ پایان‌یافته و دریافتِ حضوری رد؛ شاگرد بی `delivery.assign`،
 *      دکانِ دیگر و پرچمِ خاموش هیچ‌چیز عوض نمی‌کنند؛ سابقه بی خودِ نشانی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const orders = require('../src/lib/orders');

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

const ADDRESS = {
  landmark: 'پشتِ مسجدِ جامع', area: 'کارتهٔ چهار', gateColor: 'سبز', text: 'کوچهٔ سوم', lat: 34.5, lng: 69.1,
};

function deliveryOrder(over = {}) {
  return {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: ADDRESS,
    delivery: { callBeforeArrival: true },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
    deliveryFeeMinor: 40_000,
    ...over,
  };
}

async function newOrder(owner, body = deliveryOrder()) {
  const r = await h.post('/api/shop/orders', body, { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

async function moveOrder(token, id, ...tos) {
  for (const to of tos) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}

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

/** سفارشِ بسته‌بندی‌شده، سپرده به پیک، قبول و گرفته‌شده. */
async function departed(owner, courier, orderBody) {
  const order = await newOrder(owner, orderBody);
  await moveOrder(owner.token, order.id, 'accepted', 'picking', 'packed');
  const r = await h.post(`/api/shop/delivery/orders/${order.id}/assign`,
    { mode: 'shop_courier', courierId: courier.courierId }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = r.body.assignment.id;
  await sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up')]);
  return { order, id };
}

const fix = (token, id, body, key) => h.post(`/api/shop/delivery/assignments/${id}/address`, body,
  { token, headers: key ? { 'Idempotency-Key': key } : {} });

async function card(courier, id, scope = 'active') {
  const r = await h.get(`/api/courier/missions?scope=${scope}`, { token: courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.find((x) => x.id === id);
}

const versionOf = async (id) => Number((await one('SELECT version FROM delivery_assignments WHERE id=$1', [id])).version);

/* ══════════════════════════════════════════════════════════════════
   ۱) DLV-02 — نشانی با ناحیه و رنگِ دروازه
   ══════════════════════════════════════════════════════════════════ */

test('DLV-02: ناحیه و رنگِ دروازه در نشانی می‌مانند، جای نشانی نیستند و کلیدِ ناشناخته دور ریخته می‌شود', () => {
  assert.deepEqual(orders.parseAddress({ ...ADDRESS, phone: '0791234567', correctedAt: 5 }), ADDRESS,
    'شماره و correctedAt ِ ورودی هرگز در نشانی نمی‌نشینند');
  assert.deepEqual(orders.parseAddress({ landmark: '  پلِ سرخ ', area: ' ', gateColor: '' }), { landmark: 'پلِ سرخ' },
    'خالی‌ها نوشته نمی‌شوند');
  assert.equal(orders.parseAddress({ landmark: 'x', area: 'ن'.repeat(200), gateColor: 'ر'.repeat(90) }).area.length, 80);
  assert.equal(orders.parseAddress({ landmark: 'x', gateColor: 'ر'.repeat(90) }).gateColor.length, 40);
  for (const bad of [{ area: 'کارتهٔ چهار', gateColor: 'سبز' }, {}, null, { lat: 34.5 }]) {
    assert.throws(() => orders.parseAddress(bad), (e) => e.code === 'bad_address', JSON.stringify(bad));
  }
});

test('DLV-02: کارتِ پیک «اول نشانه» — نشانه، ناحیه و رنگِ دروازه فقط تا مأموریت زنده است', async () => {
  const a = await shopOwner('اول-نشانه');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ نشانه');
  const { order, id } = await departed(a, courier);

  const stored = await one('SELECT address FROM online_orders WHERE id=$1', [order.id]);
  assert.deepEqual(typeof stored.address === 'string' ? JSON.parse(stored.address) : stored.address, ADDRESS);

  const live = await card(courier, id);
  assert.deepEqual(live.address, {
    landmark: ADDRESS.landmark, area: ADDRESS.area, gateColor: ADDRESS.gateColor, text: ADDRESS.text,
    lat: ADDRESS.lat, lng: ADDRESS.lng,
  });
  assert.equal('correctedAt' in live.address, false, 'اصلاح نشده ⇒ correctedAt نیست');
  assert.ok(live.customer.phoneMasked.includes('•'), 'شماره همچنان پوشیده');

  await sendOk(courier.token, [ev(id, 'arrived'), ev(id, 'delivered', { cashCollectedMinor: 1_240_000 })]);
  const done = await card(courier, id, 'done');
  assert.equal(done.address, null, 'پس از پایان نه نشانه، نه ناحیه، نه رنگِ دروازه');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) DLV-09 — اصلاحِ نشانی پس از ناموفق
   ══════════════════════════════════════════════════════════════════ */

test('DLV-09: «پیدا نشد» ⇐ اصلاحِ نشانی ⇐ کارتِ پیک نشانیِ تازه را می‌بیند ⇐ تلاشِ دوباره ⇐ تحویل', async () => {
  const a = await shopOwner('اصلاح');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ اصلاح');
  const { order, id } = await departed(a, courier);
  await sendOk(courier.token, [ev(id, 'failed', { reason: 'wrong_address' })]);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [order.id])).status, 'delivery_failed');

  const before = await versionOf(id);
  const orderVersion = Number((await one('SELECT version FROM online_orders WHERE id=$1', [order.id])).version);
  const next = { landmark: 'روبه‌روی نانواییِ حاجی', area: 'کارتهٔ سه', gateColor: 'آبی', text: 'کوچهٔ دوم', lat: 34.51, lng: 69.12 };
  const t0 = now();
  const r = await fix(a.token, id, { address: next, expectedVersion: before }, freshKey('fix'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.fields, ['landmark', 'area', 'gateColor', 'text', 'pin']);
  assert.equal(r.body.replayed, false);
  assert.equal(r.body.assignment.status, 'failed', 'اصلاح وضعیت را عوض نمی‌کند؛ کارِ بعدی تلاشِ دوباره است');
  assert.equal(r.body.assignment.version, before + 1);
  assert.ok(r.body.correctedAt >= t0, 'زمانِ سرور');
  assert.equal('address' in r.body, false, 'پاسخ رونوشتِ نشانی ندارد');
  assert.equal(Number((await one('SELECT version FROM online_orders WHERE id=$1', [order.id])).version), orderVersion + 1,
    'نسخهٔ سفارش هم بالا می‌رود تا دستگاهِ کهنه روی آن ننویسد');

  const c = await card(courier, id);
  assert.equal(c.status, 'failed');
  assert.equal(c.address.landmark, next.landmark);
  assert.equal(c.address.area, next.area);
  assert.equal(c.address.gateColor, next.gateColor);
  assert.equal(c.address.lat, next.lat);
  assert.equal(c.address.correctedAt, r.body.correctedAt);

  //  کارِ بعدی: پیک دوباره می‌رود و تحویل می‌دهد
  await sendOk(courier.token, [ev(id, 'moving'), ev(id, 'arrived'), ev(id, 'delivered', { cashCollectedMinor: 1_240_000 })]);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [order.id])).status, 'delivered');

  //  سابقه: نامِ کلیدهای عوض‌شده، هرگز خودِ نشانی
  const logs = await many(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='delivery.address.fix'`, [a.shopId]);
  assert.equal(logs.length, 1);
  const raw = typeof logs[0].detail === 'string' ? logs[0].detail : JSON.stringify(logs[0].detail);
  assert.ok(raw.includes('"fields"'), raw);
  for (const v of [next.landmark, next.area, next.gateColor, next.text, '34.51']) assert.equal(raw.includes(v), false, `${v} در سابقه`);
});

test('DLV-09: کلیدِ یکتایی، نسخهٔ کهنه و دو اصلاحِ هم‌زمان', async () => {
  const a = await shopOwner('یکتا');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ یکتا');
  const { id } = await departed(a, courier);
  await sendOk(courier.token, [ev(id, 'failed', { reason: 'no_answer' })]);

  const v0 = await versionOf(id);
  const key = freshKey('fix');
  const body = { address: { landmark: 'کنارِ مکتب' } };
  const first = await fix(a.token, id, body, key);
  assert.equal(first.status, 200, JSON.stringify(first.body));
  const again = await fix(a.token, id, body, key);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.correctedAt, first.body.correctedAt, 'همان پاسخ');
  assert.equal(await versionOf(id), v0 + 1, 'تکرار نسخه را دوباره بالا نمی‌برد');

  const reused = await fix(a.token, id, { address: { landmark: 'جای دیگر' } }, key);
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');

  //  کارمند نسخهٔ v0 را دیده بود؛ اکنون v0+1 است ⇒ ۴۰۹ با وضعِ تازه، بی نوشتن
  const stale = await fix(a.token, id, { address: { landmark: 'نسخهٔ کهنه' }, expectedVersion: v0 }, freshKey('fix'));
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'assignment_changed');
  assert.equal(stale.body.error.current.version, v0 + 1);
  assert.equal((await card(courier, id)).address.landmark, 'کنارِ مکتب');

  //  دو اصلاحِ هم‌زمان با همان نسخه: فقط یکی می‌نشیند
  const v1 = await versionOf(id);
  const names = ['هم‌زمان الف', 'هم‌زمان ب'];
  const both = await Promise.all(names.map((landmark) =>
    fix(a.token, id, { address: { landmark }, expectedVersion: v1 }, freshKey('fix'))));
  assert.deepEqual(both.map((x) => x.status).sort(), [200, 409], JSON.stringify(both.map((x) => x.body)));
  assert.equal(await versionOf(id), v1 + 1);
  const won = names[both.findIndex((x) => x.status === 200)];
  assert.equal((await card(courier, id)).address.landmark, won);
});

test('DLV-09: پایان‌یافته، دریافتِ حضوری، شاگرد، دکانِ دیگر و پرچمِ خاموش هیچ نشانی‌ای را عوض نمی‌کنند', async () => {
  const a = await shopOwner('مرز');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ مرز');
  const { order, id } = await departed(a, courier);
  const original = JSON.stringify((await one('SELECT address FROM online_orders WHERE id=$1', [order.id])).address);
  const body = { address: { landmark: 'نباید بنشیند' } };

  const bad = await fix(a.token, id, { address: { area: 'فقط ناحیه' } });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_address');

  const apprentice = await member(a.shopId, 'staff', 'شاگردِ مرز');
  const denied = await fix(apprentice.token, id, body);
  assert.equal(denied.status, 403, JSON.stringify(denied.body));

  const b = await shopOwner('دکانِ دیگر');
  await enable(b.shopId);
  const other = await fix(b.token, id, body);
  assert.equal(other.status, 404, JSON.stringify(other.body));

  //  پیک به درِ دکان راه ندارد
  const self = await fix(courier.token, id, body);
  assert.equal(self.status, 403, JSON.stringify(self.body));

  await setFlag(a.shopId, 'shop.delivery', false);
  const off = await fix(a.token, id, body);
  assert.equal(off.status, 404);
  assert.equal(off.body.error.code, 'delivery_disabled');
  await setFlag(a.shopId, 'shop.delivery', true);

  await sendOk(courier.token, [ev(id, 'arrived'), ev(id, 'delivered', { cashCollectedMinor: 1_240_000 })]);
  const ended = await fix(a.token, id, body);
  assert.equal(ended.status, 409);
  assert.equal(ended.body.error.code, 'not_live');
  assert.equal(JSON.stringify((await one('SELECT address FROM online_orders WHERE id=$1', [order.id])).address), original);

  //  دریافتِ حضوری نشانی ندارد
  const p = await newOrder(a, deliveryOrder({ fulfillment: 'pickup', address: undefined, deliveryFeeMinor: 0 }));
  await moveOrder(a.token, p.id, 'accepted', 'picking', 'packed');
  const pa = await h.post(`/api/shop/delivery/orders/${p.id}/assign`, { mode: 'pickup' }, { token: a.token });
  assert.equal(pa.status, 201, JSON.stringify(pa.body));
  const pickup = await fix(a.token, pa.body.assignment.id, body);
  assert.equal(pickup.status, 409);
  assert.equal(pickup.body.error.code, 'not_delivery');

  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='delivery.address.fix'`,
    [a.shopId])).n), 0, 'هیچ اصلاحی ننشست');
});

test('DLV-02/09: ناحیهٔ ارسالِ ویترین جای «ناحیه» را پر می‌کند و اصلاحِ نشانی سندِ ناحیه را نگه می‌دارد', async () => {
  const a = await shopOwner('ناحیهٔ-ویترین');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ ناحیه');
  const { order, id } = await departed(a, courier, deliveryOrder({ address: { landmark: 'کنارِ پارک', lat: 34.5, lng: 69.1 } }));
  //  همان شکلی که `storefront-delivery.forCheckout` روی سفارشِ ویترین می‌نشاند (CHK-07)
  const zone = { id: '01JZONE0000000000000000000', name: 'شهرنو', source: 'pin' };
  await query(`UPDATE online_orders SET address = address || $2::jsonb WHERE id=$1`, [order.id, JSON.stringify({ zone })]);

  const c = await card(courier, id);
  assert.equal(c.address.area, 'شهرنو', 'بی ناحیهٔ نوشته‌شده ⇒ نامِ ناحیهٔ ارسال');
  assert.equal('zone' in c.address, false, 'شناسه و منبعِ ناحیه به پیک نمی‌رسد');

  await sendOk(courier.token, [ev(id, 'failed', { reason: 'wrong_address' })]);
  const r = await fix(a.token, id, { address: { landmark: 'پشتِ پارک', area: 'شهرنوی بالا' } }, freshKey('fix'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.fields, ['landmark', 'area', 'pin'], 'ناحیهٔ ارسال در فهرستِ تغییر نیست؛ پینِ نیامده پاک شد');
  const stored = (await one('SELECT address FROM online_orders WHERE id=$1', [order.id])).address;
  const addr = typeof stored === 'string' ? JSON.parse(stored) : stored;
  assert.deepEqual(addr.zone, zone, 'سندِ قیمتِ سفارش دست نخورد');
  assert.equal(addr.lat, undefined);
  assert.equal((await card(courier, id)).address.area, 'شهرنوی بالا', 'نوشتهٔ دکان بر نامِ ناحیه جلو می‌افتد');
});

test('DLV-09: `keepPin` ِ میزِ بی نقشه پینِ مشتری را نگه می‌دارد؛ پینِ تازه بر آن جلو می‌افتد', async () => {
  const a = await shopOwner('پین');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ پین');
  const { id } = await departed(a, courier);
  await sendOk(courier.token, [ev(id, 'failed', { reason: 'no_answer' })]);

  const bad = await fix(a.token, id, { address: { landmark: 'x' }, keepPin: 'yes' });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_keep_pin');

  const kept = await fix(a.token, id, { address: { landmark: 'درِ دوم' }, keepPin: true }, freshKey('fix'));
  assert.equal(kept.status, 200, JSON.stringify(kept.body));
  assert.deepEqual(kept.body.fields, ['landmark', 'area', 'gateColor', 'text'], 'پین عوض نشد');
  const c = await card(courier, id);
  assert.equal(c.address.lat, ADDRESS.lat);
  assert.equal(c.address.lng, ADDRESS.lng);

  const moved = await fix(a.token, id, { address: { landmark: 'درِ دوم', lat: 34.6, lng: 69.2 }, keepPin: true }, freshKey('fix'));
  assert.equal(moved.status, 200, JSON.stringify(moved.body));
  assert.deepEqual(moved.body.fields, ['pin']);
  assert.equal((await card(courier, id)).address.lat, 34.6);
});
