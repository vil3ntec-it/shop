'use strict';
/**
 * تحویل و پیک روی سرور — v3 §۲۲.۲ مرحلهٔ C بندهای ۱ تا ۶ (DLV، PCK-11).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی پرچم‌ها همه ۴۰۴ و هیچ ردیفی ساخته نمی‌شود.
 *   ۲) مسیرِ کامل: سپردن ← قبول ← گرفتنِ بسته ← در راه ← رسیدم ← تحویل با کد
 *      و نقد ← عکسِ مجاز ← تسویه؛ سفارش فقط از راهِ ماشینِ وضعیتش جلو می‌رود.
 *   ۳) صفِ آفلاین: ترتیب با deviceSeq، تکرار بی‌اثر، کلیدِ تکراری با بدنهٔ
 *      دیگر رد، زمانِ دستگاه مرجعِ تسویه نیست.
 *   ۴) ناموفق ← تلاشِ دوباره ← ناموفق ← برگشت به دکان؛ نقدِ گرفته‌نشده در
 *      دفترِ پیک نمی‌نشیند (S39).
 *   ۵) لغو پس از حرکت: هرگز وانمود نمی‌کند بستهٔ تحویل‌شده قابلِ توقف است.
 *   ۶) حسابِ تسویه (S40: ۱۲٬۴۰۰ ⇐ ۱۲٬۰۰۰، کسریِ ۴۰۰ با دلیل)، ضدتکرار (S57).
 *   ۷) دسترسی و جداییِ دکان‌ها؛ عکس فقط با رضایت و فقط برای صاحب/مدیر.
 *   ۸) سقفِ نقدِ پیک، شبکهٔ پیکِ نافعال، دریافتِ حضوری با کد و قفلِ کد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const D = require('../src/lib/delivery-states');

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

/** ۱۲٬۰۰۰ + کرایهٔ ارسالِ ۴۰۰ = ۱۲٬۴۰۰ افغانی = ۱٬۲۴۰٬۰۰۰ واحدِ کوچک (S40) */
function deliveryOrder(over = {}) {
  return {
    source: 'phone',
    fulfillment: 'delivery',
    paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'نزدیکِ مسجدِ جامع', text: 'کارتهٔ چهار، کوچهٔ سوم', lat: 34.5, lng: 69.1 },
    delivery: { photoConsent: true, callBeforeArrival: true },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
    deliveryFeeMinor: 40_000,
    ...over,
  };
}
const TOTAL = 1_240_000;

async function newOrder(owner, body = deliveryOrder()) {
  const r = await h.post('/api/shop/orders', body, { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

async function moveOrder(token, id, ...tos) {
  let last;
  for (const to of tos) {
    last = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token });
    assert.equal(last.status, 200, `${to}: ${JSON.stringify(last.body)}`);
  }
  return last.body.order;
}

async function orderRow(id) {
  return one('SELECT status, cod_status FROM online_orders WHERE id=$1', [id]);
}

async function addCourier(owner, name, over = {}) {
  const m = await member(owner.shopId, 'staff', name);
  const r = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, ...over }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...m, courierId: r.body.courier.id };
}

const assign = (token, orderId, body, key) =>
  h.post(`/api/shop/delivery/orders/${orderId}/assign`, body, { token, headers: key ? { 'Idempotency-Key': key } : {} });
const staffStatus = (token, id, body, key) =>
  h.post(`/api/shop/delivery/assignments/${id}/status`, body, { token, headers: key ? { 'Idempotency-Key': key } : {} });

let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({
  eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra,
});
const send = (token, events, deviceId = 'courier-phone-1') => h.post('/api/courier/events', { deviceId, events }, { token });

async function sendOk(token, events) {
  const r = await send(token, events);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  return r.body;
}

const jpeg = () => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(300, 7)]).toString('base64');

async function count(table, shopId) {
  return Number((await one(`SELECT COUNT(*)::int AS n FROM ${table} WHERE shop_id=$1`, [shopId])).n);
}

/** سفارشِ آماده (بسته‌بندی‌شده) + مأموریتِ قبول‌شده و گرفته‌شده. */
async function departed(owner, courier, { assignBody = {}, orderBody } = {}) {
  const order = await newOrder(owner, orderBody);
  await moveOrder(owner.token, order.id, 'accepted', 'picking', 'packed');
  const r = await assign(owner.token, order.id, { mode: 'shop_courier', courierId: courier.courierId, overrideCashCap: true, ...assignBody });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = r.body.assignment.id;
  await sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up')]);
  return { order, id, code: r.body.code };
}

/* ══════════════════════════════════════════════════════════════════
   ۰) ماشینِ وضعیت و حساب — خالص
   ══════════════════════════════════════════════════════════════════ */

test('ماشینِ مأموریت: پایانی‌ها پایانی‌اند و پیک فقط کارِ خودش را می‌کند', () => {
  for (const s of ['delivered', 'returned', 'rejected', 'withdrawn']) assert.equal(D.isTerminal(s), true, s);
  assert.equal(D.canTransition('delivered', 'returned'), false, 'تحویل‌شده برنمی‌گردد');
  assert.equal(D.canTransition('assigned', 'returned'), false, 'پیش از حرکت «برگشت» نیست؛ پس‌گرفتن است');
  assert.equal(D.check({ status: 'assigned', mode: 'shop_courier', courier_id: 'C1' }, 'delivered',
    { actor: { kind: 'courier', courierId: 'C1' } }).code, 'invalid_transition', 'بی گرفتنِ بسته تحویل نیست');
  assert.equal(D.check({ status: 'assigned', mode: 'pickup', courier_id: '' }, 'picked_up',
    { actor: { kind: 'staff', canAssign: true, canHandover: true } }).code, 'invalid_transition');
  const a = { status: 'arrived', mode: 'shop_courier', courier_id: 'C1' };
  assert.equal(D.check(a, 'delivered', { actor: { kind: 'courier', courierId: 'C2' } }).code, 'not_your_mission');
  assert.equal(D.check(a, 'returned', { actor: { kind: 'courier', courierId: 'C1' } }).code, 'staff_only');
  assert.equal(D.check(a, 'failed', { actor: { kind: 'courier', courierId: 'C1' } }).code, 'reason_required');
  assert.equal(D.check(a, 'failed', { actor: { kind: 'courier', courierId: 'C1' }, reason: 'other' }).code, 'note_required');
  assert.equal(D.check(a, 'delivered', { actor: { kind: 'staff', canAssign: true } }).code, 'note_required', 'تأییدِ دستی توضیح می‌خواهد');
  assert.equal(D.check({ status: 'requested', mode: 'shop_courier', courier_id: 'C1' }, 'assigned',
    { actor: { kind: 'courier', courierId: 'C1', active: false } }).code, 'courier_inactive');
  //  اثر بر سفارش
  assert.deepEqual(D.orderSteps('assigned', 'picked_up', 'packed').steps, [{ to: 'out_for_delivery' }]);
  assert.equal(D.orderSteps('assigned', 'picked_up', 'picking').error, 'order_not_ready');
  assert.deepEqual(D.orderSteps('moving', 'returned', 'out_for_delivery').steps.map((s) => s.to),
    ['delivery_failed', 'retry_scheduled', 'returned_to_store']);
  assert.deepEqual(D.orderSteps('failed', 'moving', 'delivery_failed').steps.map((s) => s.to),
    ['retry_scheduled', 'out_for_delivery']);
});

test('حسابِ تسویه: S40 — موردِ انتظار ۱۲٬۴۰۰، شمرده ۱۲٬۰۰۰ ⇒ کسریِ ۴۰۰؛ کرایه جدا', () => {
  const rows = [{ expected_collect_minor: TOTAL, cash_collected_minor: TOTAL, fare_minor: 5_000 }];
  const t = D.settlementTotals(rows, { handedOverMinor: 1_200_000, receivedMinor: 1_200_000 });
  assert.equal(t.expectedCashMinor, 1_240_000);
  assert.equal(t.collectedCashMinor, 1_240_000);
  assert.equal(t.collectionDiffMinor, 0);
  assert.equal(t.handoverDueMinor, 1_240_000, 'کرایه بی‌اجازه از نقد کم نمی‌شود');
  assert.equal(t.shortageMinor, 40_000);
  assert.equal(t.excessMinor, 0);
  assert.equal(t.fareTotalMinor, 5_000);
  const f = D.settlementTotals(rows, { receivedMinor: 1_240_000, farePaidFromCash: true });
  assert.equal(f.handoverDueMinor, 1_235_000);
  assert.equal(f.excessMinor, 5_000);
  //  مشتری کمتر داد: اختلافِ وصول جدا گزارش می‌شود و بدهیِ پیک همان «گرفته‌شده» است
  const s = D.settlementTotals([{ expected_collect_minor: TOTAL, cash_collected_minor: 1_200_000, fare_minor: 0 }],
    { receivedMinor: 1_200_000 });
  assert.equal(s.collectionDiffMinor, -40_000);
  assert.equal(s.handoverDueMinor, 1_200_000);
  assert.equal(s.shortageMinor, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچم: همه ۴۰۴، config «خاموش» و هیچ ردیفی ساخته نمی‌شود', async () => {
  const a = await shopOwner('خاموشِ تحویل');
  const m = await member(a.shopId, 'staff', 'پیکِ خاموش');

  const cfg = await h.get('/api/shop/delivery/config', { token: a.token });
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  assert.equal(cfg.body.modes.platform, false);
  assert.equal('ent' in cfg.body, false);

  const probe = async (label) => {
    const calls = [
      h.get('/api/shop/delivery/couriers', { token: a.token }),
      h.post('/api/shop/delivery/couriers', { userId: m.user.id }, { token: a.token }),
      h.post('/api/shop/delivery/orders/01ARZ3NDEKTSV4RRFFQ69G5FAV/assign', { mode: 'pickup' }, { token: a.token }),
      h.get('/api/shop/delivery/settlements', { token: a.token }),
      h.get('/api/courier/missions', { token: m.token }),
      h.post('/api/courier/events', { events: [ev('01ARZ3NDEKTSV4RRFFQ69G5FAV', 'assigned')] }, { token: m.token }),
    ];
    for (const r of await Promise.all(calls)) {
      assert.equal(r.status, 404, `${label}: ${JSON.stringify(r.body)}`);
      assert.equal(r.body.error.code, 'delivery_disabled');
    }
  };
  await probe('هر دو خاموش');
  //  پرچمِ جدا: «سفارش‌ها» روشن، «تحویل» خاموش ⇒ هنوز ۴۰۴
  await setFlag(a.shopId, 'shop.orders', true);
  await probe('فقط سفارش روشن');
  //  و برعکس
  await setFlag(a.shopId, 'shop.orders', false);
  await setFlag(a.shopId, 'shop.delivery', true);
  await probe('فقط تحویل روشن');

  for (const t of ['shop_couriers', 'delivery_assignments', 'delivery_events', 'courier_settlements', 'delivery_photos']) {
    assert.equal(await count(t, a.shopId), 0, t);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۲) مسیرِ کامل
   ══════════════════════════════════════════════════════════════════ */

test('مسیرِ کامل: سپردن ← قبول ← گرفتن ← در راه ← رسیدم ← تحویل با کد و نقد ← عکس ← تسویه', async () => {
  const a = await shopOwner('مسیرِ کامل');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ یک', { cashCapMinor: 5_000_000 });
  const order = await newOrder(a);
  await moveOrder(a.token, order.id, 'accepted', 'picking');

  //  سپردن پیش از «بسته‌بندی» مجاز است؛ گرفتنِ بسته نه
  const asg = await assign(a.token, order.id, { mode: 'shop_courier', courierId: courier.courierId, fareMinor: 5_000, requireCode: true });
  assert.equal(asg.status, 201, JSON.stringify(asg.body));
  const id = asg.body.assignment.id;
  assert.equal(asg.body.assignment.status, 'requested');
  assert.equal(asg.body.assignment.expectedCollectMinor, TOTAL, 'مبلغِ موردِ انتظار را سرور از سفارش می‌سازد');
  assert.match(asg.body.code, /^\d{6}$/);
  const stored = await one('SELECT code_hash FROM delivery_assignments WHERE id=$1', [id]);
  assert.ok(stored.code_hash && !stored.code_hash.includes(asg.body.code), 'کد فقط HMAC');
  //  دو تخصیص برای یک سفارش نه (اتمیک)
  const dup = await assign(a.token, order.id, { mode: 'shop_courier', courierId: courier.courierId });
  assert.equal(dup.status, 409);
  assert.equal(dup.body.error.code, 'already_assigned');

  //  کارتِ پیک: «اول نشانه»، شمارهٔ پوشیده، بی زمان‌سنج
  const ms = await h.get('/api/courier/missions', { token: courier.token });
  assert.equal(ms.status, 200, JSON.stringify(ms.body));
  assert.equal(ms.body.items.length, 1);
  const card = ms.body.items[0];
  assert.equal(card.address.landmark, 'نزدیکِ مسجدِ جامع');
  assert.match(card.customer.phoneMasked, /•/);
  assert.equal(JSON.stringify(card).includes('791234567'), false, 'شمارهٔ کامل به پیک نمی‌رسد');
  assert.equal('readyAt' in card || 'scheduledFor' in card, false, 'DLV-20: پیک زمان‌سنج نمی‌بیند');
  assert.equal(card.requiresCode, true);
  assert.equal(card.expectedCollectMinor, TOTAL);
  assert.equal(card.fareMinor, 5_000);

  await sendOk(courier.token, [ev(id, 'assigned', { deviceSeq: 1 })]);
  const early = await send(courier.token, [ev(id, 'picked_up', { deviceSeq: 2 })]);
  assert.equal(early.body.results[0].status, 'rejected');
  assert.equal(early.body.results[0].code, 'order_not_ready');

  await moveOrder(a.token, order.id, 'packed');
  await sendOk(courier.token, [ev(id, 'picked_up', { deviceSeq: 3 })]);
  assert.equal((await orderRow(order.id)).status, 'out_for_delivery');
  await sendOk(courier.token, [ev(id, 'moving', { deviceSeq: 4 }), ev(id, 'arrived', { deviceSeq: 5 })]);

  //  بی کد، کدِ نادرست، بی مبلغ ⇒ رد
  const noCode = await send(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL })]);
  assert.equal(noCode.body.results[0].code, 'code_required');
  const wrong = await send(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL, code: asg.body.code === '000000' ? '111111' : '000000' })]);
  assert.equal(wrong.body.results[0].code, 'bad_code');
  const noCash = await send(courier.token, [ev(id, 'delivered', { code: asg.body.code })]);
  assert.equal(noCash.body.results[0].code, 'cash_required');

  //  عکس پیش از تحویل (در «رسیدم») با رضایتِ مشتری و «فقط بسته»
  const noParcel = await h.post(`/api/courier/missions/${id}/photos`, { eventId: freshKey('photo'), imageBase64: jpeg() }, { token: courier.token });
  assert.equal(noParcel.status, 400);
  assert.equal(noParcel.body.error.code, 'parcel_only_required');
  const photoKey = freshKey('photo');
  const ph = await h.post(`/api/courier/missions/${id}/photos`, { eventId: photoKey, imageBase64: jpeg(), parcelOnly: true }, { token: courier.token });
  assert.equal(ph.status, 201, JSON.stringify(ph.body));
  const again = await h.post(`/api/courier/missions/${id}/photos`, { eventId: photoKey, imageBase64: jpeg(), parcelOnly: true }, { token: courier.token });
  assert.equal(again.status, 200, 'تکرارِ همان عکس');
  assert.equal(again.body.photoId, ph.body.photoId);

  const devTime = Date.now() - 60_000;
  await sendOk(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL, code: asg.body.code, deviceTime: devTime, deviceSeq: 6 })]);
  const o = await orderRow(order.id);
  assert.equal(o.status, 'delivered');
  assert.equal(o.cod_status, 'collected_by_courier', 'تحویل ≠ تسویه');

  //  خطِ زمانی: زمانِ سرور، زمان و ترتیب و شناسهٔ دستگاه
  const det = await h.get(`/api/shop/delivery/orders/${order.id}`, { token: a.token });
  assert.equal(det.status, 200);
  const evs = det.body.assignments[0].events;
  const del = evs.find((e) => e.toStatus === 'delivered');
  assert.equal(del.deviceTime, devTime);
  assert.equal(del.deviceSeq, 6);
  assert.ok(del.deviceId, 'شناسهٔ دستگاه');
  assert.ok(del.serverTime >= devTime + 60_000 - 5_000, 'زمانِ سرور جدا از زمانِ دستگاه');
  assert.equal(del.detail.codeVerified, true);
  assert.deepEqual(evs.map((e) => e.toStatus).filter((s, i, arr) => arr.indexOf(s) === i),
    ['requested', 'assigned', 'picked_up', 'moving', 'arrived', 'delivered']);
  const orderEvents = (await h.get(`/api/shop/orders/${order.id}`, { token: a.token })).body.order.events;
  assert.deepEqual(orderEvents.map((e) => e.toStatus),
    ['new', 'accepted', 'picking', 'packed', 'out_for_delivery', 'delivered', 'collected_by_courier']);

  //  عکس: صاحب می‌بیند؛ شاگرد نه
  const list = await h.get(`/api/shop/delivery/assignments/${id}/photos`, { token: a.token });
  assert.equal(list.status, 200);
  assert.equal(list.body.items.length, 1);
  assert.deepEqual(list.body.items[0].consent, { customer: true, parcelOnly: true });
  const img = await h.download(`/api/shop/delivery/photos/${ph.body.photoId}`, { token: a.token });
  assert.equal(img.status, 200);
  assert.equal(img.headers.get('content-type'), 'image/jpeg');
  assert.equal(img.buffer.toString('base64'), jpeg());
  const staff = await member(a.shopId, 'staff', 'شاگردِ کنجکاو');
  const peek = await h.get(`/api/shop/delivery/photos/${ph.body.photoId}`, { token: staff.token });
  assert.equal(peek.status, 403);

  //  دفترِ نقدِ پیک (DLV-11)
  const cs = await h.get('/api/shop/delivery/couriers', { token: a.token });
  assert.equal(cs.body.items.find((c) => c.id === courier.courierId).cashHeldMinor, TOTAL);

  //  تسویه — بی اختلاف
  const pv = await h.get(`/api/shop/delivery/couriers/${courier.courierId}/settlement-preview`, { token: a.token });
  assert.equal(pv.status, 200);
  assert.equal(pv.body.rows.length, 1);
  assert.equal(pv.body.totals.expectedCashMinor, TOTAL);
  const key = freshKey('settle');
  const body = { assignmentIds: [id], handedOverMinor: TOTAL, receivedMinor: TOTAL };
  const st = await h.post(`/api/shop/delivery/couriers/${courier.courierId}/settlements`, body, { token: a.token, headers: { 'Idempotency-Key': key } });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  assert.equal(st.body.settlement.shortageMinor, 0);
  assert.equal(st.body.settlement.fareTotalMinor, 5_000);
  assert.equal((await orderRow(order.id)).cod_status, 'settled_to_shop');
  //  S57: تکرارِ همان ⇒ همان سند؛ کلیدِ تازه برای همان مأموریت ⇒ رد
  const rep = await h.post(`/api/shop/delivery/couriers/${courier.courierId}/settlements`, body, { token: a.token, headers: { 'Idempotency-Key': key } });
  assert.equal(rep.status, 200);
  assert.equal(rep.body.settlement.id, st.body.settlement.id);
  const twice = await h.post(`/api/shop/delivery/couriers/${courier.courierId}/settlements`, body, { token: a.token, headers: { 'Idempotency-Key': freshKey('settle') } });
  assert.equal(twice.status, 409);
  assert.equal(twice.body.error.code, 'settlement_changed');
  assert.equal(await count('courier_settlements', a.shopId), 1);
  const after = await h.get('/api/shop/delivery/couriers', { token: a.token });
  assert.equal(after.body.items.find((c) => c.id === courier.courierId).cashHeldMinor, 0);
  const rpt = await h.get('/api/shop/delivery/settlements', { token: a.token });
  assert.equal(rpt.body.totals.receivedMinor, TOTAL);
  assert.equal(rpt.body.totals.settlements, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) صفِ آفلاین
   ══════════════════════════════════════════════════════════════════ */

test('صفِ آفلاین: ترتیب با deviceSeq، تکرار بی‌اثر، کلیدِ تکراری با بدنهٔ دیگر رد، زمانِ دستگاه مرجع نیست', async () => {
  const a = await shopOwner('آفلاین');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ آفلاین', { cashCapMinor: 50_000_000 });
  const order = await newOrder(a);
  await moveOrder(a.token, order.id, 'accepted', 'picking', 'packed');
  const id = (await assign(a.token, order.id, { mode: 'shop_courier', courierId: courier.courierId })).body.assignment.id;

  const threeDaysAgo = Date.now() - 3 * 24 * 60 * 60 * 1000;
  const q = [
    ev(id, 'arrived', { deviceSeq: 3, deviceTime: threeDaysAgo + 3 }),
    ev(id, 'assigned', { deviceSeq: 1, deviceTime: threeDaysAgo + 1 }),
    ev(id, 'delivered', { deviceSeq: 4, deviceTime: threeDaysAgo + 4, cashCollectedMinor: TOTAL }),
    ev(id, 'picked_up', { deviceSeq: 2, deviceTime: threeDaysAgo + 2 }),
  ];
  const t0 = Date.now();
  const first = await send(courier.token, q, 'phone-offline');
  assert.equal(first.status, 200);
  assert.deepEqual(first.body.results.map((r) => r.status), ['applied', 'applied', 'applied', 'applied']);
  assert.equal((await orderRow(order.id)).status, 'delivered');
  const n1 = await count('delivery_events', a.shopId);

  //  همان صف دوباره (پاسخ گم شده بود) — هم‌زمان دو بار
  const [r1, r2] = await Promise.all([send(courier.token, q, 'phone-offline'), send(courier.token, q, 'phone-offline')]);
  for (const r of [r1, r2]) assert.deepEqual(r.body.results.map((x) => x.status), ['duplicate', 'duplicate', 'duplicate', 'duplicate']);
  assert.equal(await count('delivery_events', a.shopId), n1, 'هیچ رویدادِ دومی');
  const oev = (await h.get(`/api/shop/orders/${order.id}`, { token: a.token })).body.order.events;
  assert.equal(oev.filter((e) => e.toStatus === 'delivered').length, 1);

  //  همان شناسه با بدنهٔ دیگر
  const forged = await send(courier.token, [{ ...q[2], cashCollectedMinor: 1 }], 'phone-offline');
  assert.equal(forged.body.results[0].status, 'rejected');
  assert.equal(forged.body.results[0].code, 'event_key_reused');

  //  زمانِ سرور مرجع است، نه زمانِ دستگاهِ سه روز پیش
  const row = await one('SELECT delivered_at FROM delivery_assignments WHERE id=$1', [id]);
  assert.ok(Number(row.delivered_at) >= t0, 'delivered_at = زمانِ دریافتِ سرور');
  const e = await one(`SELECT device_time, device_id, server_time FROM delivery_events WHERE assignment_id=$1 AND to_status='delivered'`, [id]);
  assert.equal(Number(e.device_time), threeDaysAgo + 4);
  //  شناسهٔ دستگاهِ ثبت‌شدهٔ توکن بر برچسبِ بدنه جلو است (جعل‌شدنی نیست)
  const dev = await one('SELECT device_id FROM tokens WHERE device_id IS NOT NULL AND subject_id=$1 LIMIT 1', [courier.user.id]);
  assert.equal(e.device_id, dev.device_id);
  const pv = await h.get(`/api/shop/delivery/couriers/${courier.courierId}/settlement-preview`, { token: a.token });
  assert.ok(pv.body.rows[0].deliveredAt >= t0);

  //  شناسهٔ تکراری در همان دسته ⇒ دومی «duplicate»
  const order2 = await newOrder(a);
  await moveOrder(a.token, order2.id, 'accepted', 'picking', 'packed');
  const id2 = (await assign(a.token, order2.id, { mode: 'shop_courier', courierId: courier.courierId })).body.assignment.id;
  const one1 = ev(id2, 'assigned');
  const both = await send(courier.token, [one1, one1]);
  assert.deepEqual(both.body.results.map((x) => x.status), ['applied', 'duplicate']);
  //  ورودیِ بدشکل فقط همان رویداد را رد می‌کند
  const bad = await send(courier.token, [{ assignmentId: id2, type: 'picked_up' }, ev(id2, 'picked_up')]);
  assert.equal(bad.body.results[0].code, 'event_id_required');
  assert.equal(bad.body.results[1].status, 'applied');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) ناموفق و برگشت
   ══════════════════════════════════════════════════════════════════ */

test('ناموفق ← تلاشِ دوباره ← ناموفق ← برگشت به دکان (S39)؛ نقدِ گرفته‌نشده در دفترِ پیک نیست', async () => {
  const a = await shopOwner('ناموفق');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ ناموفق', { cashCapMinor: 50_000_000 });
  const { order, id } = await departed(a, courier);

  const noReason = await send(courier.token, [ev(id, 'failed')]);
  assert.equal(noReason.body.results[0].code, 'reason_required');
  await sendOk(courier.token, [ev(id, 'failed', { reason: 'no_answer' })]);
  assert.equal((await orderRow(order.id)).status, 'delivery_failed');

  //  تلاشِ دوباره (همان پیک، بسته هنوز با اوست)
  await sendOk(courier.token, [ev(id, 'moving')]);
  assert.equal((await orderRow(order.id)).status, 'out_for_delivery');
  await sendOk(courier.token, [ev(id, 'failed', { reason: 'refused' })]);

  //  «برگشت» را پیک نمی‌زند؛ دکان دریافتِ بسته را تأیید می‌کند (شاگرد هم می‌تواند)
  const self = await send(courier.token, [ev(id, 'returned')]);
  assert.equal(self.body.results[0].code, 'staff_only');
  const staff = await member(a.shopId, 'staff', 'شاگردِ انبار');
  //  CAS: «از وضعیتِ کهنه» ⇒ ۴۰۹ با وضعیتِ فعلی
  const stale = await staffStatus(staff.token, id, { to: 'returned', from: 'moving' });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'status_changed');
  assert.equal(stale.body.error.current, 'failed');
  const back = await staffStatus(staff.token, id, { to: 'returned', from: 'failed' });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  assert.equal(back.body.assignment.status, 'returned');
  assert.equal((await orderRow(order.id)).status, 'returned_to_store');
  assert.equal((await orderRow(order.id)).cod_status, 'to_collect', 'پولی گرفته نشد');

  const cs = await h.get('/api/shop/delivery/couriers', { token: a.token });
  const me = cs.body.items.find((c) => c.id === courier.courierId);
  assert.equal(me.cashHeldMinor, 0);
  assert.equal(me.liveCount, 0);
  //  پس از پایان، نشانی و مشتری از کارتِ پیک برداشته می‌شود (§۳۸.۴)
  const done = await h.get('/api/courier/missions?scope=done', { token: courier.token });
  assert.equal(done.body.items[0].address, null);
  assert.equal(done.body.items[0].customer, null);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) لغو پس از حرکت
   ══════════════════════════════════════════════════════════════════ */

test('لغو پس از حرکت: پیش از حرکت «پس‌گرفتن»؛ پس از حرکت فراخوانی؛ تحویل‌شده هرگز «متوقف» نمی‌شود', async () => {
  const a = await shopOwner('لغو پس از حرکت');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ لغو', { cashCapMinor: 50_000_000 });

  //  الف) پیش از حرکت
  const o1 = await newOrder(a);
  await moveOrder(a.token, o1.id, 'accepted', 'picking', 'packed');
  const id1 = (await assign(a.token, o1.id, { mode: 'shop_courier', courierId: courier.courierId })).body.assignment.id;
  await sendOk(courier.token, [ev(id1, 'assigned')]);
  const early = await h.post(`/api/shop/delivery/assignments/${id1}/recall`, { reason: 'customer_request' }, { token: a.token });
  assert.equal(early.status, 409);
  assert.equal(early.body.error.code, 'not_departed');
  const wd = await staffStatus(a.token, id1, { to: 'withdrawn', reason: 'order_cancelled' });
  assert.equal(wd.status, 200, JSON.stringify(wd.body));
  const cx = await h.post(`/api/shop/orders/${o1.id}/transition`, { to: 'cancelled', reason: 'customer_request' }, { token: a.token });
  assert.equal(cx.status, 200, JSON.stringify(cx.body));
  const late = await send(courier.token, [ev(id1, 'picked_up')]);
  assert.equal(late.body.results[0].code, 'invalid_transition', 'مأموریتِ پس‌گرفته‌شده حرکت نمی‌کند');

  //  ب) پس از حرکت ⇒ فراخوانی ⇒ برگشتِ واقعی
  const { order: o2, id: id2 } = await departed(a, courier);
  const staff = await member(a.shopId, 'staff', 'شاگردِ لغو');
  const denied = await h.post(`/api/shop/delivery/assignments/${id2}/recall`, { reason: 'customer_request' }, { token: staff.token });
  assert.equal(denied.status, 403);
  const rc = await h.post(`/api/shop/delivery/assignments/${id2}/recall`, { reason: 'customer_request' }, { token: a.token });
  assert.equal(rc.status, 200, JSON.stringify(rc.body));
  assert.equal(rc.body.assignment.recall.outcome, 'pending');
  assert.equal(rc.body.assignment.status, 'picked_up', 'فراخوانی وضعیت را جعل نمی‌کند');
  assert.equal((await orderRow(o2.id)).status, 'out_for_delivery');
  const card = (await h.get('/api/courier/missions', { token: courier.token })).body.items.find((x) => x.id === id2);
  assert.equal(card.recall.reason, 'customer_request');
  const back = await staffStatus(a.token, id2, { to: 'returned' });
  assert.equal(back.status, 200);
  assert.equal(back.body.assignment.recall.outcome, 'returned');
  assert.equal((await orderRow(o2.id)).status, 'returned_to_store');
  const failEv = (await h.get(`/api/shop/orders/${o2.id}`, { token: a.token })).body.order.events.find((e) => e.toStatus === 'delivery_failed');
  assert.equal(failEv.reason, 'recalled');
  const again = await h.post(`/api/shop/delivery/assignments/${id2}/recall`, { reason: 'customer_request' }, { token: a.token });
  assert.equal(again.body.error.code, 'already_returned');

  //  پ) دیر: پیک (آفلاین) پیش از رسیدنِ خبر تحویل داده بود
  const { order: o3, id: id3 } = await departed(a, courier);
  await h.post(`/api/shop/delivery/assignments/${id3}/recall`, { reason: 'wrong_order' }, { token: a.token });
  await sendOk(courier.token, [ev(id3, 'delivered', { cashCollectedMinor: TOTAL })]);
  assert.equal((await orderRow(o3.id)).status, 'delivered');
  const a3 = (await h.get(`/api/shop/delivery/orders/${o3.id}`, { token: a.token })).body.assignments[0];
  assert.equal(a3.recall.outcome, 'too_late');
  const after = await h.post(`/api/shop/delivery/assignments/${id3}/recall`, { reason: 'wrong_order' }, { token: a.token });
  assert.equal(after.status, 409);
  assert.equal(after.body.error.code, 'already_delivered');
});

/* ══════════════════════════════════════════════════════════════════
   ۶) تسویه با اختلاف
   ══════════════════════════════════════════════════════════════════ */

test('تسویه با کسری (S40) و وصولِ ناقص از مشتری: اختلاف‌ها جدا، دلیل لازم', async () => {
  const a = await shopOwner('تسویه با کسری');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ کسری', { cashCapMinor: 50_000_000 });

  const { order: o1, id: id1 } = await departed(a, courier, { assignBody: { fareMinor: 10_000 } });
  await sendOk(courier.token, [ev(id1, 'delivered', { cashCollectedMinor: TOTAL })]);

  //  مشتری ۴۰۰ کمتر داد
  const { id: id2 } = await departed(a, courier);
  const noWhy = await send(courier.token, [ev(id2, 'delivered', { cashCollectedMinor: 1_200_000 })]);
  assert.equal(noWhy.body.results[0].code, 'cash_reason_required');
  await sendOk(courier.token, [ev(id2, 'delivered', { cashCollectedMinor: 1_200_000, cashReason: 'partial_payment' })]);
  const a2 = await one('SELECT cash_review FROM delivery_assignments WHERE id=$1', [id2]);
  assert.equal(a2.cash_review, true, '«تحویل با اختلاف / در انتظارِ بررسی»');

  const pv = await h.get(`/api/shop/delivery/couriers/${courier.courierId}/settlement-preview`, { token: a.token });
  assert.equal(pv.body.totals.expectedCashMinor, 2 * TOTAL);
  assert.equal(pv.body.totals.collectedCashMinor, TOTAL + 1_200_000);
  assert.equal(pv.body.totals.collectionDiffMinor, -40_000);

  const url = `/api/shop/delivery/couriers/${courier.courierId}/settlements`;
  const base = { assignmentIds: [id1, id2], handedOverMinor: 2_400_000, receivedMinor: 2_400_000 };
  const why = await h.post(url, base, { token: a.token, headers: { 'Idempotency-Key': freshKey('s') } });
  assert.equal(why.status, 400);
  assert.equal(why.body.error.code, 'reason_required');
  const staff = await member(a.shopId, 'staff', 'شاگردِ تسویه');
  const deny = await h.post(url, { ...base, reason: 'x' }, { token: staff.token, headers: { 'Idempotency-Key': freshKey('s') } });
  assert.equal(deny.status, 403, 'تأییدِ مدیر (DLV-12)');
  const ok = await h.post(url, { ...base, reason: 'شمارش در حضورِ پیک' }, { token: a.token, headers: { 'Idempotency-Key': freshKey('s') } });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  const s = ok.body.settlement;
  assert.equal(s.expectedCashMinor, 2_480_000);
  assert.equal(s.collectedCashMinor, 2_440_000);
  assert.equal(s.collectionDiffMinor, -40_000);
  assert.equal(s.handoverDueMinor, 2_440_000);
  assert.equal(s.receivedMinor, 2_400_000);
  assert.equal(s.shortageMinor, 40_000);
  assert.equal(s.excessMinor, 0);
  assert.equal(s.fareTotalMinor, 10_000);
  assert.equal((await orderRow(o1.id)).cod_status, 'settled_to_shop');
  const g = await h.get(`/api/shop/delivery/settlements/${s.id}`, { token: a.token });
  assert.equal(g.body.rows.length, 2);
  assert.equal(g.body.rows.find((r) => r.assignmentId === id2).collectionDiffMinor, -40_000);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) دسترسی و جداییِ دکان‌ها
   ══════════════════════════════════════════════════════════════════ */

test('دسترسی و جدایی: دکانِ دیگر، پیکِ دیگر، شاگرد و بی‌رضایت هیچ‌کدام به چیزی نمی‌رسند', async () => {
  const a = await shopOwner('دکانِ الف');
  const b = await shopOwner('دکانِ ب');
  await enable(a.shopId);
  await enable(b.shopId);
  const c1 = await addCourier(a, 'پیکِ الف۱', { cashCapMinor: 50_000_000 });
  const c2 = await addCourier(a, 'پیکِ الف۲', { cashCapMinor: 50_000_000 });
  const cb = await addCourier(b, 'پیکِ ب', { cashCapMinor: 50_000_000 });
  const staff = await member(a.shopId, 'staff', 'شاگردِ الف');

  const order = await newOrder(a, deliveryOrder({ delivery: {} }));
  await moveOrder(a.token, order.id, 'accepted', 'picking', 'packed');

  //  شاگرد سفارش نمی‌سپارد، پیک ثبت نمی‌کند، تسویه نمی‌بیند
  assert.equal((await assign(staff.token, order.id, { mode: 'shop_courier', courierId: c1.courierId })).status, 403);
  assert.equal((await h.post('/api/shop/delivery/couriers', { userId: staff.user.id }, { token: staff.token })).status, 403);
  assert.equal((await h.get(`/api/shop/delivery/couriers/${c1.courierId}/settlement-preview`, { token: staff.token })).status, 403);
  //  شاگردی که پیک نیست درِ پیک را ندارد
  const nc = await h.get('/api/courier/missions', { token: staff.token });
  assert.equal(nc.status, 403);
  assert.equal(nc.body.error.code, 'not_a_courier');

  //  دکانِ دیگر
  assert.equal((await h.get(`/api/shop/delivery/orders/${order.id}`, { token: b.token })).status, 404);
  const bAssign = await assign(b.token, order.id, { mode: 'shop_courier', courierId: cb.courierId });
  assert.equal(bAssign.status, 404);
  assert.equal(bAssign.body.error.code, 'order_not_found');
  const cross = await assign(a.token, order.id, { mode: 'shop_courier', courierId: cb.courierId });
  assert.equal(cross.status, 404, 'پیکِ دکانِ دیگر');
  const foreignMember = await h.post('/api/shop/delivery/couriers', { userId: b.user.id }, { token: a.token });
  assert.equal(foreignMember.status, 404);
  assert.equal(foreignMember.body.error.code, 'member_not_found');
  assert.equal((await h.get(`/api/shop/delivery/couriers/${c1.courierId}/settlement-preview`, { token: b.token })).status, 404);

  const id = (await assign(a.token, order.id, { mode: 'shop_courier', courierId: c1.courierId })).body.assignment.id;
  //  پیکِ دیگرِ همان دکان و پیکِ دکانِ دیگر: «پیدا نشد»
  for (const who of [c2, cb]) {
    const r = await send(who.token, [ev(id, 'assigned')]);
    assert.equal(r.body.results[0].code, 'assignment_not_found');
  }
  assert.equal((await h.get('/api/courier/missions', { token: c2.token })).body.items.length, 0);
  assert.equal((await h.get(`/api/shop/delivery/assignments/${id}/photos`, { token: b.token })).body.items.length, 0);

  //  عکس بی رضایتِ مشتری ⇒ ۴۰۳ و هیچ بایتی
  await sendOk(c1.token, [ev(id, 'assigned'), ev(id, 'picked_up'), ev(id, 'arrived')]);
  const ph = await h.post(`/api/courier/missions/${id}/photos`, { eventId: freshKey('p'), imageBase64: jpeg(), parcelOnly: true }, { token: c1.token });
  assert.equal(ph.status, 403);
  assert.equal(ph.body.error.code, 'photo_consent_missing');
  assert.equal(await count('delivery_photos', a.shopId), 0);
  //  پیکِ دیگر هم عکس روی مأموریتِ دیگری نمی‌گذارد
  const ph2 = await h.post(`/api/courier/missions/${id}/photos`, { eventId: freshKey('p'), imageBase64: jpeg(), parcelOnly: true }, { token: c2.token });
  assert.equal(ph2.status, 404);
  //  و فایلِ نا-عکس پذیرفته نیست
  const txt = Buffer.alloc(300, 65).toString('base64');
  const ph3 = await h.post(`/api/courier/missions/${id}/photos`, { eventId: freshKey('p'), imageBase64: txt, parcelOnly: true }, { token: c1.token });
  assert.equal(ph3.status, 400);

  //  پیکِ غیرفعال: کارِ تازه نمی‌پذیرد، ولی رویدادِ مأموریتِ گرفته‌شده‌اش ثبت می‌شود (§۲۸.۶)
  const off = await h.patch(`/api/shop/delivery/couriers/${c1.courierId}`, { active: false }, { token: a.token });
  assert.equal(off.status, 200);
  await sendOk(c1.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL })]);
  const o2 = await newOrder(a);
  await moveOrder(a.token, o2.id, 'accepted');
  const r2 = await assign(a.token, o2.id, { mode: 'shop_courier', courierId: c1.courierId });
  assert.equal(r2.status, 409);
  assert.equal(r2.body.error.code, 'courier_inactive');
});

/* ══════════════════════════════════════════════════════════════════
   ۸) سقفِ نقد، شبکهٔ نافعال، شرکتِ بیرونی، دریافتِ حضوری با کد
   ══════════════════════════════════════════════════════════════════ */

test('سقفِ نقد (DLV-11)، شبکهٔ VILL3N نافعال، شرکتِ بیرونی دستی، دریافتِ حضوری با کد و قفلِ کد', async () => {
  const a = await shopOwner('روش‌ها');
  await enable(a.shopId);
  const courier = await addCourier(a, 'پیکِ سقف');   // سقفِ پیش‌فرض: ۱٬۰۰۰٬۰۰۰
  const order = await newOrder(a);
  await moveOrder(a.token, order.id, 'accepted', 'picking', 'packed');

  const over = await assign(a.token, order.id, { mode: 'shop_courier', courierId: courier.courierId });
  assert.equal(over.status, 409);
  assert.equal(over.body.error.code, 'cash_cap_exceeded');
  const plat = await assign(a.token, order.id, { mode: 'platform' });
  assert.equal(plat.status, 409);
  assert.equal(plat.body.error.code, 'platform_unavailable');
  const pickupOnDelivery = await assign(a.token, order.id, { mode: 'pickup' });
  assert.equal(pickupOnDelivery.body.error.code, 'fulfillment_mismatch');

  //  شرکتِ بیرونی: ثبت و تطبیقِ دستی؛ تکرارِ درخواست با همان کلید ⇒ همان مأموریت
  const k = freshKey('ext');
  const ext = await assign(a.token, order.id, { mode: 'external', externalName: 'پیک‌رسانِ شهر', externalRef: 'TRK-1', fareMinor: 15_000 }, k);
  assert.equal(ext.status, 201, JSON.stringify(ext.body));
  assert.equal(ext.body.assignment.status, 'assigned');
  const extRep = await assign(a.token, order.id, { mode: 'external', externalName: 'پیک‌رسانِ شهر', externalRef: 'TRK-1', fareMinor: 15_000 }, k);
  assert.equal(extRep.status, 200);
  assert.equal(extRep.body.assignment.id, ext.body.assignment.id);
  const xid = ext.body.assignment.id;
  assert.equal((await staffStatus(a.token, xid, { to: 'picked_up' })).status, 200);
  const d = await staffStatus(a.token, xid, { to: 'delivered', cashCollectedMinor: TOTAL });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal((await orderRow(order.id)).status, 'delivered');

  //  دریافتِ حضوری (DLV-15، PCK-11، S43)
  const p = await newOrder(a, deliveryOrder({ fulfillment: 'pickup', address: undefined, deliveryFeeMinor: 0 }));
  await moveOrder(a.token, p.id, 'accepted', 'picking', 'packed', 'ready_for_pickup');
  const pa = await assign(a.token, p.id, { mode: 'pickup' });
  assert.equal(pa.status, 201, JSON.stringify(pa.body));
  assert.match(pa.body.code, /^\d{6}$/);
  const pid = pa.body.assignment.id;
  const staff = await member(a.shopId, 'staff', 'شاگردِ پیشخوان');
  const wrongCode = pa.body.code === '123456' ? '654321' : '123456';
  const bad = await staffStatus(staff.token, pid, { to: 'delivered', code: wrongCode, cashCollectedMinor: 1_200_000 });
  assert.equal(bad.status, 422);
  assert.equal(bad.body.error.code, 'bad_code');
  const manual = await staffStatus(staff.token, pid, { to: 'delivered', note: 'کد را گم کرده', cashCollectedMinor: 1_200_000 });
  assert.equal(manual.status, 422, 'شاگرد تأییدِ دستی ندارد');
  assert.equal(manual.body.error.code, 'code_required');
  const good = await staffStatus(staff.token, pid, { to: 'delivered', code: pa.body.code, cashCollectedMinor: 1_200_000 });
  assert.equal(good.status, 200, JSON.stringify(good.body));
  const po = await orderRow(p.id);
  assert.equal(po.status, 'picked_up');
  assert.equal(po.cod_status, 'collected_in_store');

  //  قفلِ کد پس از ۵ تلاشِ نادرست؛ کدِ تازه قفل را باز می‌کند
  const q = await newOrder(a, deliveryOrder({ fulfillment: 'pickup', address: undefined, deliveryFeeMinor: 0 }));
  await moveOrder(a.token, q.id, 'accepted', 'picking', 'packed', 'ready_for_pickup');
  const qa = await assign(a.token, q.id, { mode: 'pickup' });
  const qid = qa.body.assignment.id;
  const wrongQ = qa.body.code === '000001' ? '000002' : '000001';
  for (let i = 0; i < 5; i += 1) {
    assert.equal((await staffStatus(staff.token, qid, { to: 'delivered', code: wrongQ, cashCollectedMinor: 1_200_000 })).status, 422);
  }
  const locked = await staffStatus(staff.token, qid, { to: 'delivered', code: qa.body.code, cashCollectedMinor: 1_200_000 });
  assert.equal(locked.status, 423);
  assert.equal(locked.body.error.code, 'code_locked');
  const regen = await h.post(`/api/shop/delivery/assignments/${qid}/code`, {}, { token: a.token });
  assert.equal(regen.status, 200);
  const fine = await staffStatus(staff.token, qid, { to: 'delivered', code: regen.body.code, cashCollectedMinor: 1_200_000 });
  assert.equal(fine.status, 200, JSON.stringify(fine.body));
});
