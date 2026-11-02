'use strict';
/**
 * پیش‌شرط‌های رویدادِ پیک — DLV-07 (اسکنِ کیسه‌ها هنگامِ گرفتن از دکان) و DLV-17 («تحویل شد» فقط نزدیکِ پین).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) منطقِ خالص همان بردارِ `core/test-vectors/courier-checks-v1.json` است که `CourierChecks.kt` می‌خواند.
 *   ۲) هر دو پرچم خاموش ⇒ رفتار همان پیشین: «گرفتم» بی برچسب و «تحویل شد» بی موقعیت پذیرفته؛ کارت بی کلیدِ تازه.
 *   ۳) `shop.delivery_bag_scan`: «گرفتم» فقط با برچسبِ همهٔ کیسه‌های بسته؛ کیسهٔ سفارشِ دیگر، برچسبِ کهنه و
 *      کیسهٔ جامانده رد (با `missing`)؛ سفارشِ بی جمع‌آوری چیزی برای اسکن ندارد؛ رویداد `bagsScanned` دارد و
 *      تکرارِ همان رویداد «duplicate» است.
 *   ۴) `shop.delivery_near_pin`: «تحویل شد» ِ پیک فقط با موقعیت و در فاصلهٔ `max_m` از پین؛ دور ⇒ رد با فاصله و
 *      **بی سوختنِ تلاشِ کد**؛ سفارشِ بی پین سنجیده نمی‌شود؛ تأییدِ دستیِ کارمند (با توضیح) از آن نمی‌گذرد؛
 *      موقعیت هیچ‌جا در دیتابیس نمی‌ماند، فقط فاصلهٔ گرد.
 *   ۵) هم‌زمانی: ۱۵ «گرفتم» ِ هم‌زمانِ یک مأموریت (کیسهٔ جامانده) پشتِ `FOR UPDATE` ِ مأموریت ⇒ همه رد،
 *      بی ۵۰۰. خواندنِ پرچم‌ها درونِ همان تراکنش است؛ پیش‌تر از استخر بود و روی PostgreSQL (استخرِ ۱۰)
 *      دارندهٔ قفل اتصالِ دوم نمی‌گرفت ⇒ `timeout exceeded when trying to connect`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const K = require('../src/lib/courier-checks');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const key = (p = 'k') => `${p}-${uniq()}-key`;

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

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-checks' },
  body: { device_id: 'dev-checks', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن (سفارش، تحویل، جمع‌آوری) با یک کالای بی‌بارکد و یک پیک */
async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.delivery', 'shop.orders.picking']) await setFlag(o.shopId, k, true);
  assert.equal((await push(o.token, [op('salt', { name: 'نمک', unit: 'بسته', salePrice: 50, category: 'خوراکی' })])).status, 200);
  const c = await h.newUser(`پیک ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), o.shopId, c.user.id, now()]
  );
  const r = await h.post('/api/shop/delivery/couriers', { userId: c.user.id, name: 'پیک', phone: '0799999999' }, { token: o.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...o, courier: { token: c.accessToken, id: r.body.courier.id } };
}

const PIN = { lat: 34.523456, lng: 69.181234 };
const NEAR = { lat: 34.524356, lng: 69.181234 };   //  ۱۰۰ متر
const FAR = { lat: 34.532456, lng: 69.181234 };    //  ۱۰۰۱ متر

/** سفارشِ ارسالی؛ `bags` ⇒ با جمع‌آوری بسته، وگرنه با گذارِ دستی. ⇒ `{id, orderNo}` */
async function packedOrder(s, { bags = 0, pin = false } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'پشتِ مسجد', ...(pin ? PIN : {}) },
    lines: [{ productId: 'salt', name: 'نمک', unit: 'بسته', qtyMilli: 1000, unitPriceMinor: 5000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = r.body.order.id;
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: s.token })).status, 200);
  if (bags) {
    assert.equal((await h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: s.token })).status, 201);
    const e = await h.post(`/api/shop/orders/${id}/pick/events`, { events: [{ eventId: key('pe'), lineNo: 1, type: 'pick', qtyMilli: 1000 }] }, { token: s.token });
    assert.equal(e.body.results[0].status, 'accepted', JSON.stringify(e.body));
    const p = await h.post(`/api/shop/orders/${id}/pick/pack`, { bags }, { token: s.token, headers: { 'Idempotency-Key': key('pack') } });
    assert.equal(p.status, 200, JSON.stringify(p.body));
  } else {
    for (const to of ['picking', 'packed']) {
      assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token })).status, 200);
    }
  }
  return { id, orderNo: r.body.order.orderNo };
}

async function assigned(s, order, extra = {}) {
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`,
    { mode: 'shop_courier', courierId: s.courier.id, overrideCashCap: true, ...extra }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  assert.equal((await send(s, [ev(id, 'assigned')]))[0].status, 'applied');
  return { id, code: a.body.code };
}

let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra });
async function send(s, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone', events }, { token: s.courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results;
}
async function card(s, id) {
  const r = await h.get('/api/courier/missions', { token: s.courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.find((x) => x.id === id);
}
const deliver = (id, extra = {}) => ev(id, 'delivered', { cashCollectedMinor: 5000, ...extra });
const label = (o, bag, of) => `VLNB-${o.orderNo}-${bag}-${of}`;

/* ══════════════════════════════════════════════════════════════════
   ۱) منطقِ خالص — همان بردارِ Kotlin
   ══════════════════════════════════════════════════════════════════ */

test('DLV-07/17 courier-checks-v1: کیسه‌ها، فاصله، نزدیکِ پین و max_m — همان بردارِ CourierChecks.kt', () => {
  const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'courier-checks-v1.json'), 'utf8'));
  assert.equal(V.version, 1);
  for (const c of V.bags) assert.deepEqual(K.bagsCheck(c.labels, { orderNo: c.orderNo, bags: c.bags }), c.want, c.name);
  const pt = (x) => (x ? { lat: x[0], lng: x[1] } : null);
  for (const c of V.distance) assert.equal(K.distanceM(pt(c.a), pt(c.b)), c.m, JSON.stringify(c));
  for (const c of V.nearPin) assert.deepEqual(K.nearPin(pt(c.position), pt(c.pin), c.maxM), c.want, c.name);
  for (const c of V.maxM) assert.equal(K.maxMOf(c.raw), c.want, JSON.stringify(c.raw));
  assert.ok(V.bags.length >= 8 && V.distance.length >= 6 && V.nearPin.length >= 6);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) پرچم‌های خاموش
   ══════════════════════════════════════════════════════════════════ */

test('DLV-07/17 خاموش: «گرفتم» بی برچسب و «تحویل شد» بی موقعیت همان پیشین؛ کارت بی bagScan و nearPin', async () => {
  const s = await liveShop('خاموش');
  const o = await packedOrder(s, { bags: 2, pin: true });
  const a = await assigned(s, o);
  const c = await card(s, a.id);
  assert.equal(c.bagScan, undefined);
  assert.equal(c.nearPin, undefined);
  assert.equal((await send(s, [ev(a.id, 'picked_up')]))[0].status, 'applied');
  assert.equal((await send(s, [deliver(a.id)]))[0].status, 'applied');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) DLV-07 — اسکنِ کیسه‌ها هنگامِ گرفتن
   ══════════════════════════════════════════════════════════════════ */

test('DLV-07: «گرفتم» فقط با برچسبِ همهٔ کیسه‌ها؛ سفارشِ دیگر، کهنه و جامانده رد؛ تکرار بی‌اثر', async () => {
  const s = await liveShop('کیسه');
  await setFlag(s.shopId, K.BAG_FLAG, true);
  const o = await packedOrder(s, { bags: 2 });
  const other = await packedOrder(s, { bags: 2 });
  const a = await assigned(s, o);
  assert.deepEqual((await card(s, a.id)).bagScan, { bags: 2 }, 'کارت شمارِ کیسه را پیش از گرفتن می‌گوید');

  const cases = [
    [undefined, 'bags_required', [1, 2]],
    [[label(o, 1, 2)], 'bags_incomplete', [2]],
    [[label(o, 1, 2), label(other, 2, 2)], 'bag_wrong_order'],
    [[label(o, 1, 3), label(o, 2, 3)], 'label_mismatch'],
    [['VLNH-1-123456'], 'bad_label'],
  ];
  for (const [bags, code, missing] of cases) {
    const [r] = await send(s, [ev(a.id, 'picked_up', bags ? { bags } : {})]);
    assert.equal(r.status, 'rejected', JSON.stringify(r));
    assert.equal(r.code, code, JSON.stringify(r));
    assert.equal(r.current, 'assigned');
    assert.equal(r.data.bags, 2);
    if (missing) assert.deepEqual(r.data.missing, missing);
  }
  const [bad] = await send(s, [ev(a.id, 'picked_up', { bags: 'VLNB-1-1-1' })]);
  assert.equal(bad.code, 'bad_bags', 'شکلِ نادرست');
  assert.equal((await one('SELECT status FROM delivery_assignments WHERE id=$1', [a.id])).status, 'assigned', 'هیچ ردی گذار نزد');

  const ok = ev(a.id, 'picked_up', { bags: [` VLNB-${String(o.orderNo).replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d])}-۲-۲ `, label(o, 1, 2), label(o, 1, 2)] });
  assert.equal((await send(s, [ok]))[0].status, 'applied');
  assert.equal((await send(s, [ok]))[0].status, 'duplicate', 'تکرارِ همان رویداد از صفِ آفلاین');
  const row = await one(`SELECT detail FROM delivery_events WHERE assignment_id=$1 AND to_status='picked_up'`, [a.id]);
  const detail = typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail;
  assert.equal(detail.bagsScanned, 2);
  assert.equal((await card(s, a.id)).bagScan, undefined, 'پس از گرفتن دیگر اسکن نمی‌خواهد');
});

test('DLV-07: سفارشِ بسته‌شده بی جمع‌آوری کیسهٔ شمرده ندارد ⇒ «گرفتم» بی اسکن؛ کارمند از اسکن نمی‌گذرد', async () => {
  const s = await liveShop('بی‌کیسه');
  await setFlag(s.shopId, K.BAG_FLAG, true);
  const o = await packedOrder(s, { bags: 0 });
  const a = await assigned(s, o);
  assert.equal((await card(s, a.id)).bagScan, undefined);
  assert.equal((await send(s, [ev(a.id, 'picked_up')]))[0].status, 'applied');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) DLV-17 — «تحویل شد» فقط نزدیکِ پین
   ══════════════════════════════════════════════════════════════════ */

test('DLV-17: «تحویل شد» فقط با موقعیت و در max_m از پین؛ دور ⇒ رد با فاصله، بی سوختنِ کد؛ موقعیت نمی‌ماند', async () => {
  const s = await liveShop('پین');
  await setFlag(s.shopId, K.PIN_FLAG, true);
  await setFlag(s.shopId, K.MAX_M_KEY, 150);
  const o = await packedOrder(s, { pin: true });
  const a = await assigned(s, o, { requireCode: true });
  assert.ok(a.code, 'کدِ تحویل');
  assert.deepEqual((await card(s, a.id)).nearPin, { maxM: 150 });
  assert.equal((await send(s, [ev(a.id, 'picked_up')]))[0].status, 'applied');

  let [r] = await send(s, [deliver(a.id, { code: a.code })]);
  assert.equal(r.code, 'position_required', JSON.stringify(r));
  [r] = await send(s, [deliver(a.id, { code: '000000', position: FAR })]);
  assert.equal(r.code, 'too_far_from_pin', JSON.stringify(r));
  assert.deepEqual(r.data, { distanceM: 1001, maxM: 150 });
  assert.equal(Number((await one('SELECT code_attempts FROM delivery_assignments WHERE id=$1', [a.id])).code_attempts), 0,
    'پیکِ دور از پین تلاشِ کد را نمی‌سوزاند');
  [r] = await send(s, [deliver(a.id, { code: a.code, position: { lat: 'x', lng: 69 } })]);
  assert.equal(r.code, 'bad_position');

  [r] = await send(s, [deliver(a.id, { code: a.code, position: NEAR })]);
  assert.equal(r.status, 'applied', JSON.stringify(r));
  const rows = await many('SELECT detail, note FROM delivery_events WHERE assignment_id=$1', [a.id]);
  const done = rows.map((x) => (typeof x.detail === 'string' ? JSON.parse(x.detail) : x.detail)).find((d) => d && d.distanceM !== undefined);
  assert.equal(done.distanceM, 100);
  const dump = JSON.stringify(rows) + JSON.stringify(await many('SELECT * FROM delivery_locations WHERE assignment_id=$1', [a.id]));
  assert.ok(!dump.includes('34.524356') && !dump.includes('34.532456'), 'موقعیتِ پیک در دیتابیس نماند');
});

test('DLV-17: سفارشِ بی پین سنجیده نمی‌شود؛ max_m ِ نامعتبر ⇒ ۳۰۰؛ تأییدِ دستیِ کارمند با توضیح از آن نمی‌گذرد', async () => {
  const s = await liveShop('بی‌پین');
  await setFlag(s.shopId, K.PIN_FLAG, true);
  await setFlag(s.shopId, K.MAX_M_KEY, 'زیاد');
  const plain = await packedOrder(s, { pin: false });
  const a = await assigned(s, plain);
  assert.equal((await card(s, a.id)).nearPin, undefined);
  assert.equal((await send(s, [ev(a.id, 'picked_up')]))[0].status, 'applied');
  assert.equal((await send(s, [deliver(a.id)]))[0].status, 'applied', 'بی پین ⇒ بی موقعیت');

  const pinned = await packedOrder(s, { pin: true });
  const b = await assigned(s, pinned);
  assert.deepEqual((await card(s, b.id)).nearPin, { maxM: K.DEFAULT_MAX_M });
  assert.equal((await send(s, [ev(b.id, 'picked_up')]))[0].status, 'applied');
  const staff = await h.post(`/api/shop/delivery/assignments/${b.id}/status`,
    { type: 'delivered', note: 'گوشیِ پیک خاموش بود؛ مشتری زنگ زد', cashCollectedMinor: 5000 },
    { token: s.token, headers: { 'Idempotency-Key': key('staff') } });
  assert.equal(staff.status, 200, JSON.stringify(staff.body));
  assert.equal((await one('SELECT status FROM delivery_assignments WHERE id=$1', [b.id])).status, 'delivered');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) هم‌زمانی — خواندنِ پرچم درونِ تراکنشِ رویداد
   ══════════════════════════════════════════════════════════════════ */

test('DLV-07 هم‌زمان: ۱۵ «گرفتم» ِ هم‌زمانِ یک مأموریت ⇒ همه سنجیده و رد، بی ۵۰۰؛ «گرفتم» ِ درست سپس applied', async () => {
  const s = await liveShop('هم‌زمان');
  await setFlag(s.shopId, K.BAG_FLAG, true);
  const o = await packedOrder(s, { bags: 2 });
  const a = await assigned(s, o);
  //  هر ۱۵ تا پشتِ `FOR UPDATE` ِ مأموریت می‌مانند و هر کدام پرچم را درونِ تراکنشِ خودش می‌خواند
  const res = await Promise.all(Array.from({ length: 15 }, () => h.post('/api/courier/events',
    { deviceId: 'courier-phone', events: [ev(a.id, 'picked_up', { bags: [label(o, 1, 2)] })] }, { token: s.courier.token })));
  assert.deepEqual(res.map((r) => r.status), Array(15).fill(200), JSON.stringify(res.filter((r) => r.status !== 200).map((r) => r.body)));
  for (const r of res) assert.equal(r.body.results[0].code, 'bags_incomplete', JSON.stringify(r.body));
  assert.equal((await one('SELECT status FROM delivery_assignments WHERE id=$1', [a.id])).status, 'assigned', 'هیچ ردی گذار نزد');
  assert.equal((await send(s, [ev(a.id, 'picked_up', { bags: [label(o, 1, 2), label(o, 2, 2)] })]))[0].status, 'applied');
});
