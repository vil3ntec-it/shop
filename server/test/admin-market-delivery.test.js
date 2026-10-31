'use strict';
/**
 * پنلِ مرکزی ۴.۶ (مدیریتِ بازار) و ۴.۷ (نظارت بر پیک) — `/api/admin/market/*` و `/api/admin/delivery/*`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) کدهای «گیر کرده / ناهم‌خوان / اختلاف» از دادهٔ واقعیِ دو ماشینِ وضعیت (و خودِ تابعِ خالص).
 *   ۲) آستانه‌ها از live_config: فقط ارشد، دلیل اجباری، مقدارِ بد ۴۰۰، تکرارِ همان مقدار بی ردیفِ دوم (idempotent).
 *   ۳) جداییِ دکان‌ها در پالایش (`shopId`) و هیچ دادهٔ شخصیِ مشتری در هیچ پاسخی.
 *   ۴) دروازه‌های واقعیِ فروشنده (پرچم، حالت، جریمه، فهرست) — تعلیقِ «بازار» با همان `shop_holds`.
 *   ۵) کمیسیون و سبدِ چنددکانی «در مدل نیست» ⇒ available:false، نه صفرِ ساختگی.
 *   ۶) گزارشِ کارکرد: جمعِ ردیف‌ها = جمعِ کل، «در دست» = تسویه‌نشده، و یک ردیفِ سابقه.
 *   ۷) مرزِ نقش: viewer فقط خواندن، finance فقط درهای پولی، کاربرِ دکان ۴۰۱؛ و برگشتِ مهاجرتِ ۱۷۲.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const watch = require('../src/lib/order-watch');

const MIN = 60_000;
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword('Admin!12345');
  const t = now();
  for (const [u, role, at] of [['arshad', 'superadmin', t - 9000], ['modir', 'admin', t - 8000], ['mali', 'finance', t - 7000], ['binande', 'viewer', t - 6000]]) {
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [newId('adm'), u, hash, role, at]
    );
  }
});
test.after(async () => { await h.stop(); });

const tokens = {};
async function tok(u) {
  if (!tokens[u]) tokens[u] = (await h.post('/api/admin/login', { username: u, password: 'Admin!12345' })).body.token;
  return tokens[u];
}
const sup = () => tok('arshad');

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: await sup() });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shopOwner(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}

const TOTAL = 1_240_000;
const PHONE = '0791234567';
const NAME = 'احمدِ آزمون';
const order = () => ({
  source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
  customer: { name: NAME, phone: PHONE },
  address: { landmark: 'نزدیکِ مسجد', text: 'کارتهٔ چهار', lat: 34.5, lng: 69.1 },
  delivery: { photoConsent: false },
  lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
  deliveryFeeMinor: 40_000,
});

async function newOrder(o) {
  const r = await h.post('/api/shop/orders', order(), { token: o.token, headers: { 'Idempotency-Key': `ord-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order.id;
}
async function move(o, id, steps) {
  for (const to of steps) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: o.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
}
async function addCourier(o, name) {
  const m = await member(o.shopId, 'staff', name);
  const r = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, cashCapMinor: 50_000_000 }, { token: o.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...m, courierId: r.body.courier.id };
}
async function assign(o, id, c) {
  const a = await h.post(`/api/shop/delivery/orders/${id}/assign`, { mode: 'shop_courier', courierId: c.courierId, overrideCashCap: true },
    { token: o.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return a.body.assignment.id;
}
let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now() - 5000, ...extra });
async function send(c, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone-1', events }, { token: c.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

/** صحنه: یک دکانِ پیک‌دار با هر نوع گرفتاری، و دکانِ دوم برای جداییِ پالایش. */
let A, B, c1, ids;
async function scene() {
  if (A) return;
  A = await shopOwner('نظارتِ پیک الف');
  B = await shopOwner('نظارتِ پیک ب');
  for (const s of [A, B]) for (const k of ['shop.orders', 'shop.delivery']) await setFlag(s.shopId, k, true);
  c1 = await addCourier(A, 'پیکِ یکم');
  ids = {};
  //  ۱) تحویل‌شدهٔ درست
  ids.ok = await newOrder(A);
  await move(A, ids.ok, ['accepted', 'picking', 'packed']);
  const a1 = await assign(A, ids.ok, c1);
  await send(c1, [ev(a1, 'assigned'), ev(a1, 'picked_up'), ev(a1, 'delivered', { cashCollectedMinor: TOTAL })]);
  //  ۲) منتظرِ ارسال، کهنه
  ids.dispatch = await newOrder(A);
  await move(A, ids.dispatch, ['accepted', 'picking', 'packed']);
  await query('UPDATE online_orders SET updated_at = $2 WHERE id = $1', [ids.dispatch, now() - 120 * MIN]);
  //  ۳) سپرده و برداشته نشده، کهنه
  ids.pickup = await newOrder(A);
  await move(A, ids.pickup, ['accepted', 'picking', 'packed']);
  const a3 = await assign(A, ids.pickup, c1);
  await send(c1, [ev(a3, 'assigned')]);
  await query('UPDATE delivery_assignments SET updated_at = $2 WHERE id = $1', [a3, now() - 60 * MIN]);
  //  ۴) ناهم‌خوان: مأموریت «تحویل شد»، سفارش (به‌زور) «در راه»
  ids.mismatch = await newOrder(A);
  await move(A, ids.mismatch, ['accepted', 'picking', 'packed']);
  const a4 = await assign(A, ids.mismatch, c1);
  await send(c1, [ev(a4, 'assigned'), ev(a4, 'picked_up'), ev(a4, 'delivered', { cashCollectedMinor: TOTAL })]);
  await query(`UPDATE online_orders SET status = 'out_for_delivery' WHERE id = $1`, [ids.mismatch]);
  //  ۵) اختلافِ نقد: کمتر گرفت، با دلیل
  ids.cash = await newOrder(A);
  await move(A, ids.cash, ['accepted', 'picking', 'packed']);
  const a5 = await assign(A, ids.cash, c1);
  await send(c1, [ev(a5, 'assigned'), ev(a5, 'picked_up'),
    ev(a5, 'delivered', { cashCollectedMinor: TOTAL - 40_000, cashReason: 'partial_payment' })]);
  //  ۶) «تازه» و پذیرفته‌نشده، کهنه
  ids.fresh = await newOrder(A);
  await query('UPDATE online_orders SET created_at = $2 WHERE id = $1', [ids.fresh, now() - 90 * MIN]);
  //  دکانِ ب: یک منتظرِ ارسال
  ids.other = await newOrder(B);
  await move(B, ids.other, ['accepted', 'picking', 'packed']);
}

function noPersonal(body, where) {
  const s = JSON.stringify(body);
  assert.ok(!s.includes(PHONE), `${where}: شمارهٔ مشتری نباید بیرون برود`);
  assert.ok(!s.includes(NAME), `${where}: نامِ مشتری نباید بیرون برود`);
  assert.ok(!s.includes('کارتهٔ چهار'), `${where}: نشانی نباید بیرون برود`);
}

/* ───────────────────────── تابعِ خالص ───────────────────────── */

test('order-watch: آستانه‌ها، اعتبارسنجی و کدهای هر حالت (خالص)', () => {
  const t0 = 10_000_000_000;
  const n = watch.normalizeThresholds({ dispatchMin: 45, acceptMin: 0, pickupMin: '30', zzz: 1, failedMin: 99999 });
  assert.equal(n.values.dispatchMin, 45);
  assert.equal(n.source.dispatchMin, 'live_config');
  for (const k of ['acceptMin', 'pickupMin', 'failedMin']) assert.equal(n.source[k], 'default', k);
  assert.deepEqual(watch.normalizeThresholds(null).values, watch.DEFAULT_THRESHOLDS);
  assert.equal(watch.validateThresholds({ dispatchMin: 0 }).error, 'bad_value');
  assert.equal(watch.validateThresholds({ nope: 5 }).error, 'unknown_key');
  assert.equal(watch.validateThresholds({}).error, 'empty');
  assert.equal(watch.validateThresholds([1]).error, 'not_object');
  assert.deepEqual(watch.validateThresholds({ inDeliveryMin: 200 }), { ok: true, value: { inDeliveryMin: 200 } });

  const th = watch.DEFAULT_THRESHOLDS;
  const base = { status: 'packed', fulfillment: 'delivery', payment_method: 'cod', payment_status: 'unpaid', cod_status: 'to_collect',
    created_at: t0, updated_at: t0 };
  const at = t0 + 31 * MIN;
  assert.deepEqual(watch.classify(base, th, at).stuck, ['dispatch_overdue']);
  assert.deepEqual(watch.classify(base, th, t0 + 29 * MIN).stuck, [], 'زیرِ آستانه گیر نیست');
  assert.deepEqual(watch.classify({ ...base, a_status: 'requested', a_updated_at: t0 }, th, at).stuck, ['courier_unaccepted']);
  assert.deepEqual(watch.classify({ ...base, status: 'out_for_delivery', a_status: 'moving', a_updated_at: t0 }, th, t0 + 121 * MIN).stuck, ['delivery_overdue']);
  assert.deepEqual(watch.classify({ ...base, status: 'new' }, th, at).stuck, ['accept_overdue']);
  //  ناهم‌خوان‌ها
  const m = (o) => watch.classify({ ...base, ...o }, th, t0).mismatch;
  assert.deepEqual(m({ status: 'out_for_delivery', a_status: 'delivered', a_mode: 'shop_courier' }), ['delivered_not_reflected']);
  assert.deepEqual(m({ status: 'delivered', a_status: 'delivered', a_mode: 'shop_courier' }), []);
  assert.deepEqual(m({ status: 'picked_up', a_status: 'delivered', a_mode: 'pickup' }), []);
  assert.deepEqual(m({ status: 'delivered', a_status: 'delivered', a_mode: 'pickup' }), ['delivered_not_reflected']);
  assert.deepEqual(m({ status: 'delivered', a_status: 'delivered', a_mode: 'shop_courier', a_cash_collected_minor: '5' }), ['cod_not_recorded']);
  assert.deepEqual(m({ status: 'packed', a_status: 'moving' }), ['departed_not_reflected']);
  assert.deepEqual(m({ status: 'out_for_delivery', a_status: 'failed' }), ['failed_not_reflected']);
  assert.deepEqual(m({ status: 'out_for_delivery', a_status: 'returned' }), ['returned_not_reflected']);
  assert.deepEqual(m({ status: 'cancelled', a_status: 'assigned' }), ['closed_order_live_mission']);
  assert.deepEqual(m({ status: 'packed', a_status: 'withdrawn' }), []);
  //  اختلاف
  assert.deepEqual(watch.classify({ ...base, status: 'delivered', a_status: 'delivered', cod_status: 'collected_by_courier', a_cash_review: true }, th, t0).discrepancy, ['cash_review']);
  assert.deepEqual(watch.classify({ ...base, status: 'delivered', payment_method: 'online', payment_status: 'pending' }, th, t0).discrepancy, ['payment_unconfirmed']);
  //  جای در صف
  assert.equal(watch.stageOf(base), 'dispatch');
  assert.equal(watch.stageOf({ ...base, a_status: 'withdrawn' }), 'dispatch');
  assert.equal(watch.stageOf({ ...base, a_status: 'assigned' }), 'assigned');
  assert.equal(watch.stageOf({ ...base, status: 'out_for_delivery', a_status: 'picked_up' }), 'in_delivery');
  assert.equal(watch.stageOf({ ...base, fulfillment: 'pickup' }), null);
});

/* ───────────────────────── ۴.۷ نظارت بر پیک ───────────────────────── */

test('۴.۷ overview و queue: گیرکرده، ناهم‌خوان و اختلاف از دادهٔ واقعی؛ بی دادهٔ شخصی', async () => {
  await scene();
  const ov = await h.get('/api/admin/delivery/overview', { token: await sup() });
  assert.equal(ov.status, 200, JSON.stringify(ov.body));
  noPersonal(ov.body, 'overview');
  assert.equal(ov.body.stuck.dispatch_overdue >= 1, true, JSON.stringify(ov.body.stuck));
  assert.equal(ov.body.stuck.pickup_overdue >= 1, true);
  assert.equal(ov.body.stuck.accept_overdue >= 1, true);
  assert.equal(ov.body.mismatch.delivered_not_reflected, 1);
  assert.equal(ov.body.discrepancy.cash_review, 1);
  assert.equal(ov.body.stages.dispatch, 2, 'الف + ب');
  assert.equal(ov.body.couriers.active >= 1, true);
  assert.equal(ov.body.thresholds.source.dispatchMin, 'default');
  assert.equal(typeof ov.body.serverTime, 'number');
  assert.equal(ov.body.truncated, false);

  const q = async (view, extra = '') => {
    const r = await h.get(`/api/admin/delivery/queue?view=${view}${extra}`, { token: await sup() });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    noPersonal(r.body, `queue ${view}`);
    return r.body;
  };
  const stuck = await q('stuck');
  const stuckIds = stuck.rows.map((x) => x.orderId);
  for (const k of ['dispatch', 'pickup', 'fresh']) assert.ok(stuckIds.includes(ids[k]), k);
  assert.ok(!stuckIds.includes(ids.ok));
  const mm = await q('mismatch');
  assert.deepEqual(mm.rows.map((x) => x.orderId), [ids.mismatch]);
  assert.deepEqual(mm.rows[0].issues.mismatch, ['delivered_not_reflected']);
  assert.equal(mm.rows[0].mission.courierName, 'پیکِ یکم');
  assert.deepEqual((await q('discrepancy')).rows.map((x) => x.orderId), [ids.cash]);
  //  جداییِ دکان در پالایش
  const onlyB = await q('all', `&shopId=${B.shopId}`);
  assert.deepEqual(onlyB.rows.map((x) => x.orderId), [ids.other]);
  assert.ok(onlyB.rows.every((x) => x.shopId === B.shopId));
  //  صفحه‌بندی سمتِ سرور
  const p1 = await q('all', '&limit=2&offset=0');
  const p2 = await q('all', '&limit=2&offset=2');
  assert.equal(p1.rows.length, 2);
  assert.equal(p1.total, p2.total);
  assert.ok(!p2.rows.some((x) => p1.rows.some((y) => y.orderId === x.orderId)));
  //  ورودیِ بد ۴۰۰
  assert.equal((await h.get('/api/admin/delivery/queue?view=x', { token: await sup() })).status, 400);
  assert.equal((await h.get('/api/admin/delivery/queue?limit=999', { token: await sup() })).status, 400);
});

test('۴.۷ آستانه‌ها: فقط ارشد، دلیل اجباری، مقدارِ بد ۴۰۰، تکرارِ همان مقدار بی ردیفِ دوم، و اثرش بر «گیر کرده»', async () => {
  await scene();
  const put = async (u, body) => h.put('/api/admin/delivery/thresholds', body, { token: await tok(u) });
  for (const u of ['modir', 'mali', 'binande']) {
    assert.equal((await put(u, { thresholds: { dispatchMin: 600 }, reason: 'آزمون' })).status, 403, u);
  }
  assert.equal((await put('arshad', { thresholds: { dispatchMin: 600 } })).body.error.code, 'reason_required');
  for (const bad of [{ dispatchMin: 0 }, { dispatchMin: 1.5 }, { dispatchMin: '600' }, { zzz: 5 }, {}, null]) {
    const r = await put('arshad', { thresholds: bad, reason: 'آزمون' });
    assert.equal(r.status, 400, JSON.stringify(bad));
    assert.equal(r.body.error.code, 'bad_thresholds');
  }
  const ok = await put('arshad', { thresholds: { dispatchMin: 600 }, reason: 'شبِ شلوغ' });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.unchanged, false);
  assert.equal(ok.body.values.dispatchMin, 600);
  assert.equal(ok.body.source.dispatchMin, 'live_config');
  assert.equal(ok.body.source.acceptMin, 'default');
  const hist = () => many(`SELECT * FROM live_config_history WHERE key = $1`, [watch.KEY]);
  const n1 = (await hist()).length;
  const again = await put('arshad', { thresholds: { dispatchMin: 600 }, reason: 'شبِ شلوغ' });
  assert.equal(again.body.unchanged, true);
  assert.equal((await hist()).length, n1, 'تکرار ⇒ بی ردیفِ دوم');
  assert.ok((await hist()).some((r) => r.reason === 'شبِ شلوغ'), 'دلیل در تاریخچه');
  //  منتظرِ ارسالِ دوساعته دیگر «گیر کرده» نیست
  const stuck = (await h.get('/api/admin/delivery/queue?view=stuck', { token: await sup() })).body.rows.map((x) => x.orderId);
  assert.ok(!stuck.includes(ids.dispatch));
  assert.ok(stuck.includes(ids.pickup));
  //  خواندن برای همه
  for (const u of ['modir', 'binande']) {
    const g = await h.get('/api/admin/delivery/thresholds', { token: await tok(u) });
    assert.equal(g.status, 200);
    assert.equal(g.body.values.dispatchMin, 600);
  }
  //  پاک کردن (همان درِ پرچم‌ها، با دلیل) ⇒ پیش‌فرض
  const del = await h.del('/api/admin/flags', { token: await sup(), body: { scope: 'all', key: watch.KEY, reason: 'برگشت' } });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.equal((await h.get('/api/admin/delivery/thresholds', { token: await sup() })).body.source.dispatchMin, 'default');
});

test('۴.۷ رویدادها، اتصالِ پیک و گزارشِ کارکرد (جمعِ ردیف‌ها = جمعِ کل، یک ردیفِ سابقه)', async () => {
  await scene();
  const evs = await h.get(`/api/admin/delivery/events?shopId=${A.shopId}&limit=100`, { token: await sup() });
  assert.equal(evs.status, 200, JSON.stringify(evs.body));
  noPersonal(evs.body, 'events');
  assert.ok(evs.body.events.length >= 10);
  assert.ok(evs.body.events.every((e) => e.shopId === A.shopId && !('note' in e)));
  const delivered = evs.body.events.find((e) => e.to === 'delivered' && e.actorType === 'courier');
  assert.ok(delivered && delivered.skewMs >= 4000, JSON.stringify(delivered));
  const onlyB = await h.get(`/api/admin/delivery/events?shopId=${B.shopId}`, { token: await sup() });
  assert.deepEqual(onlyB.body.events, []);

  const con = await h.get('/api/admin/delivery/connectivity', { token: await sup() });
  assert.equal(con.status, 200);
  assert.equal(con.body.heartbeat.inModel, false);
  const row = con.body.shops.find((s) => s.shopId === A.shopId);
  assert.ok(row.lastCourierEventAt > 0 && row.events24h >= 9 && row.devices24h === 1, JSON.stringify(row));

  const before = (await many(`SELECT id FROM audit_logs WHERE action = 'admin.delivery_report_viewed'`)).length;
  const rep = await h.get('/api/admin/delivery/report?days=1', { token: await sup() });
  assert.equal(rep.status, 200, JSON.stringify(rep.body));
  noPersonal(rep.body, 'report');
  const mine = rep.body.rows.find((r) => r.shopId === A.shopId && r.courierId === c1.courierId);
  assert.equal(mine.courierName, 'پیکِ یکم');
  assert.equal(mine.delivered, 3);
  assert.equal(mine.cashDeliveries, 3);
  assert.equal(mine.collectedCashMinor, 3 * TOTAL - 40_000);
  assert.equal(mine.unsettledMinor, mine.collectedCashMinor, 'هیچ تسویه‌ای نشده');
  assert.equal(mine.inHandMinor, mine.unsettledMinor);
  assert.ok(mine.avgMinutes !== null && mine.medianMinutes !== null);
  for (const k of Object.keys(rep.body.totals)) {
    assert.equal(rep.body.totals[k], rep.body.rows.reduce((s, x) => s + x[k], 0), k);
  }
  assert.equal((await many(`SELECT id FROM audit_logs WHERE action = 'admin.delivery_report_viewed'`)).length, before + 1);
  assert.equal((await h.get('/api/admin/delivery/report?from=10&to=5', { token: await sup() })).body.error.code, 'bad_range');
});

/* ───────────────────────── ۴.۶ مدیریتِ بازار ───────────────────────── */

let S;
async function liveStore() {
  if (S) return S;
  const o = await shopOwner('فروشندهٔ بازار');
  for (const k of ['shop.orders', 'shop.storefront', 'market.directory']) await setFlag(o.shopId, k, true);
  const slug = `m-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ آزمون', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-m' },
    body: { device_id: 'dev-m', schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  const l = await h.put('/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, { token: o.token });
  assert.equal(l.status, 200, JSON.stringify(l.body));
  const phone = '0791110077';
  const s1 = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const s2 = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s1.body.devCode });
  assert.equal(s2.status, 200, JSON.stringify(s2.body));
  const co = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'خریدارِ بازار', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `mk-${uniq()}`, 'X-Store-Ticket': s2.body.ticket } });
  assert.equal(co.status, 201, JSON.stringify(co.body));
  S = { ...o, slug, phone };
  return S;
}

test('۴.۶ overview: فروشنده‌ها، دروازه‌ها، و «در مدل نیست» برای فروشندهٔ مستقل، سبدِ چنددکانی و کمیسیون', async () => {
  const s = await liveStore();
  const r = await h.get('/api/admin/market/overview?days=1', { token: await sup() });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.sellers.total >= 1 && r.body.sellers.listed >= 1, JSON.stringify(r.body.sellers));
  assert.equal(r.body.sellerKinds.independent, null);
  assert.equal(r.body.sellerKinds.note, 'not_in_model');
  assert.equal(r.body.directory.on, false, 'پرچمِ همگانیِ بازار خاموش است');
  assert.equal(r.body.orders.total, 1);
  assert.equal(r.body.orders.groups.awaiting, 1);
  //  روی shop ِ این پایه ستون‌های #432 نیست ⇒ «در مدل نیست»، نه صفر
  const cols = (await many(`SELECT column_name FROM information_schema.columns WHERE table_name='online_orders' AND column_name='market_order_group_id'`)).length;
  assert.equal(r.body.multiSeller.inModel, cols === 1);
  if (!cols) assert.equal(r.body.multiSeller.groups, null);
  noPersonal(r.body, 'market overview');
  assert.ok(!JSON.stringify(r.body).includes(s.phone));
});

test('۴.۶ sellers: دروازه‌های واقعی، تعلیقِ «بازار» با همان shop_holds، و پالایش', async () => {
  const s = await liveStore();
  const find = async (extra = '') => {
    const r = await h.get(`/api/admin/market/sellers?q=${encodeURIComponent('فروشندهٔ بازار')}${extra}`, { token: await sup() });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body;
  };
  let b = await find();
  assert.equal(b.total, 1);
  let row = b.sellers[0];
  assert.equal(row.shopId, s.shopId);
  assert.equal(row.kind, 'vill3n_shop');
  assert.equal(row.gates.listed, true);
  assert.equal(row.gates.marketFlag, true);
  assert.equal(row.gates.storefrontEnabled, true);
  assert.equal(row.publishedProducts, 1);
  assert.deepEqual(row.blockedBy, ['directory'], 'فقط پرچمِ همگانی بسته است');
  await setFlag('all', 'market.directory', true);
  b = await find();
  assert.deepEqual(b.sellers[0].blockedBy, []);

  //  تعلیقِ «بازار» برای همین دکان — همان درِ حالت‌ها، با دلیل و کلیدِ یکتایی
  const hold = await h.post(`/api/admin/shops/${s.shopId}/holds`, { kind: 'service_disabled', service: 'market.directory', reason: 'گزارشِ تقلب' },
    { token: await tok('modir'), headers: { 'Idempotency-Key': `hold-${uniq()}` } });
  assert.equal(hold.status, 201, JSON.stringify(hold.body));
  b = await find();
  row = b.sellers[0];
  assert.equal(row.gates.marketFlag, false);
  assert.deepEqual(row.blockedBy, ['marketFlag']);
  assert.equal(row.holds[0].service, 'market.directory');
  assert.equal((await find('&state=held')).total, 1);
  assert.equal((await find('&state=sanctioned')).total, 0);
  //  ویترینِ عمومی هنوز باز است (فقط بازار بسته شد) و بازار این دکان را ندارد
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 200);
  const mk = await h.get('/api/market/shops');
  assert.equal(mk.status, 200);
  assert.ok(!JSON.stringify(mk.body).includes(s.slug));
  //  سابقهٔ کارِ حساس، و نگهداشتِ حالتی که ربطی به بازار ندارد بیرون
  await h.post(`/api/admin/shops/${s.shopId}/holds`, { kind: 'service_disabled', service: 'shop.chat', reason: 'بی‌ربط به بازار' },
    { token: await tok('modir'), headers: { 'Idempotency-Key': `hold-${uniq()}` } });
  const hist = await h.get(`/api/admin/market/history?shopId=${s.shopId}`, { token: await sup() });
  assert.equal(hist.status, 200, JSON.stringify(hist.body));
  const acts = hist.body.entries.map((e) => `${e.action}:${e.detail?.service || ''}`);
  assert.ok(acts.includes('admin.shop_hold_placed:market.directory'), acts.join(' '));
  assert.ok(!acts.includes('admin.shop_hold_placed:shop.chat'), acts.join(' '));
  assert.ok(acts.some((a) => a.startsWith('market.listing')), acts.join(' '));
  //  برداشتن ⇒ دوباره باز
  const lift = await h.post(`/api/admin/shops/${s.shopId}/holds/${hold.body.hold.id}/lift`, { reason: 'بررسی شد' }, { token: await tok('modir') });
  assert.equal(lift.status, 200, JSON.stringify(lift.body));
  assert.deepEqual((await find()).sellers[0].blockedBy, []);
  await setFlag('all', 'market.directory', false);
  assert.equal((await h.get('/api/admin/market/sellers?state=bogus', { token: await sup() })).status, 400);
});

test('۴.۶ orders: سفارش‌های ویترین بی دادهٔ شخصی، پالایشِ دکان و «مشکل‌دار»؛ کمیسیون صادقانه', async () => {
  const s = await liveStore();
  await scene();
  const r = await h.get(`/api/admin/market/orders?days=1&shopId=${s.shopId}`, { token: await sup() });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.total, 1);
  const o = r.body.orders[0];
  assert.equal(o.source, 'storefront');
  assert.equal(o.shopId, s.shopId);
  assert.ok(!JSON.stringify(r.body).includes(s.phone) && !JSON.stringify(r.body).includes('خریدارِ بازار'));
  //  سفارش‌های تلفنیِ دکانِ پیک «بازار» نیستند
  const a = await h.get(`/api/admin/market/orders?days=1&shopId=${A.shopId}`, { token: await sup() });
  assert.equal(a.body.total, 0);
  //  «مشکل‌دار»: سفارشِ ویترینِ تازه را کهنه کن ⇒ accept_overdue
  await query('UPDATE online_orders SET created_at = $2 WHERE id = $1', [o.id, now() - 90 * MIN]);
  const pr = await h.get(`/api/admin/market/orders?days=1&problem=1`, { token: await sup() });
  assert.equal(pr.status, 200);
  assert.deepEqual(pr.body.orders.map((x) => x.id), [o.id]);
  assert.deepEqual(pr.body.orders[0].issues.stuck, ['accept_overdue']);
  assert.equal(pr.body.truncated, false);

  const c = await h.get('/api/admin/market/commission?days=30', { token: await sup() });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const has = (await many(`SELECT 1 FROM information_schema.columns WHERE table_name='online_orders' AND column_name='platform_fee_minor'`)).length;
  if (!has) {
    assert.equal(c.body.inModel, false);
    assert.equal(c.body.reason, 'not_in_schema');
    assert.ok(!('totals' in c.body), 'بی ستون هیچ جمعی ساخته نمی‌شود');
  } else {
    assert.equal(c.body.inModel, true);
  }
  assert.equal(c.body.decision.date, '1405/08/08');
});

test('مرزِ نقش: viewer فقط خواندن، finance فقط درهای پولی، کاربرِ دکان و بی‌توکن بیرون', async () => {
  await scene();
  const get = async (u, p) => (await h.get(p, { token: u ? await tok(u) : undefined })).status;
  for (const p of ['/api/admin/market/overview', '/api/admin/market/sellers', '/api/admin/market/orders', '/api/admin/market/commission',
    '/api/admin/market/history', '/api/admin/delivery/overview', '/api/admin/delivery/queue', '/api/admin/delivery/events',
    '/api/admin/delivery/connectivity', '/api/admin/delivery/report', '/api/admin/delivery/thresholds']) {
    assert.equal(await get('binande', p), 200, `viewer ${p}`);
    assert.equal(await get('modir', p), 200, `admin ${p}`);
    assert.equal(await get(null, p), 401, `بی‌توکن ${p}`);
    assert.equal((await h.get(p, { token: A.token })).status, 401, `کاربرِ دکان ${p}`);
  }
  for (const p of ['/api/admin/market/overview', '/api/admin/market/orders', '/api/admin/market/commission',
    '/api/admin/delivery/overview', '/api/admin/delivery/report']) {
    assert.equal(await get('mali', p), 200, `finance ${p}`);
  }
  for (const p of ['/api/admin/market/sellers', '/api/admin/market/history', '/api/admin/delivery/queue',
    '/api/admin/delivery/events', '/api/admin/delivery/connectivity', '/api/admin/delivery/thresholds']) {
    assert.equal(await get('mali', p), 403, `finance ${p}`);
  }
  const me = await h.get('/api/admin/me', { token: await tok('binande') });
  assert.ok(me.body.capabilities.includes('market-admin.v1') && me.body.capabilities.includes('delivery-watch.v1'));
});

//  ⛔ آخرین آزمونِ این پرونده: نمایه‌ها را برمی‌دارد و دوباره می‌سازد.
test('مهاجرتِ ۱۷۲: فقط نمایه؛ خط‌های rollback اجرا می‌شوند و اجرای دوباره همان را برمی‌گرداند', async () => {
  const migrate = require('../src/migrate');
  const FILE = '172_console_market_delivery.sql';
  const NAMES = ['ix_online_orders_updated_all', 'ix_online_orders_open_status', 'ix_online_orders_storefront_created',
    'ix_delivery_assignments_live_status', 'ix_delivery_events_time'];
  const present = async () => (await many(`SELECT indexname FROM pg_indexes WHERE indexname = ANY($1::text[])`, [NAMES])).length;
  assert.equal(await present(), NAMES.length);
  const sql = fs.readFileSync(path.join(__dirname, '..', 'migrations', FILE), 'utf8');
  assert.ok(!/\b(ALTER|DROP|DELETE|UPDATE|INSERT)\b/i.test(sql.split('\n').filter((l) => !l.startsWith('--')).join('\n')), 'فقط CREATE INDEX');
  const lines = sql.split('\n').map((l) => l.match(/^--\s+rollback:\s+(.+)$/)).filter(Boolean).map((m) => m[1]);
  assert.equal(lines.length, NAMES.length + 1);
  const count = (await one('SELECT COUNT(*)::int AS n FROM online_orders')).n;
  for (const l of lines) await query(l);
  assert.equal(await present(), 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM online_orders')).n, count);
  assert.deepEqual(await migrate.run(), [FILE]);
  assert.equal(await present(), NAMES.length);
  assert.equal((await h.get('/api/admin/delivery/overview', { token: await sup() })).status, 200);
});
