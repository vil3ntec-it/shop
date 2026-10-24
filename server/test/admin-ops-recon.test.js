'use strict';
/**
 * تطبیقِ فروشِ آنلاین در پنلِ VILL3N — E2E-ADM-15 (§۲۴.۶).
 *
 *   ۱) فقط مدیرِ ارشد؛ بازهٔ نادرست یا بیش از ۹۲ روز ۴۰۰.
 *   ۲) فروش، بازپرداخت، خالص و اختلاف همان `order-refunds.report` ِ خودِ دکان است
 *      (دادهٔ مرجع) — دقیقاً؛ لغوها شمرده می‌شوند؛ سفارشِ آزمایشی هیچ‌جا نیست.
 *   ۳) نقدِ پیک: گرفته‌شده در بازه، تسویه‌نشدهٔ همین لحظه (بی بازه)، تسویه‌ها با کسری.
 *   ۴) کارمزد تعریف نشده ⇒ null، نه صفر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const R = require('../src/lib/order-refunds');

const HOUR = 60 * 60 * 1000;
let root = '';
let clerk = '';
let A = null;
let B = null;
let keySeq = 0;
const get = (token, q = '') => h.get(`/api/admin/system/reconciliation${q}`, { token });
const create = (token, body) => h.post('/api/shop/orders', body, { token, headers: { 'Idempotency-Key': `recon-${++keySeq}-${Date.now()}` } });
const move = (token, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token });
const sample = (over = {}) => ({
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
  customer: { name: 'احمد', phone: '0791234567' },
  lines: [{ productId: 'p1', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 3333 }, { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 }],
  ...over,
});
const TOTAL = 7500;

async function readyShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const key of ['shop.orders', R.FLAG_KEY]) {
    const r = await h.put('/api/admin/pump/live-config', { scope: made.body.shop.id, key, value: true }, { token: root });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { token: u.accessToken, shopId: made.body.shop.id };
}

async function order(shop, over) {
  const r = await create(shop.token, sample(over));
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

async function walk(shop, id, steps) {
  for (const to of steps) {
    const r = await move(shop.token, id, typeof to === 'string' ? { to } : to);
    assert.equal(r.status, 200, `${JSON.stringify(to)}: ${JSON.stringify(r.body)}`);
  }
}

async function assignment(shopId, orderId, { cash, at, settlement = '' }) {
  await query(
    `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, status, expected_collect_minor,
        cash_collected_minor, delivered_at, settlement_id, created_at, updated_at)
     VALUES ($1,$2,$3,1,'shop_courier','delivered',$4,$4,$5,$6,$5,$5)`,
    [newId('dla'), shopId, orderId, cash, at, settlement]
  );
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;

  //  دکانِ الف: یک فروشِ برداشته‌شده با بازپرداختِ ۲۰۰۰، یک ردشده، یک آزمایشی (بیرون)
  A = await readyShop('تطبیق‌الف');
  const sold = await order(A);
  await walk(A, sold.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']);
  const ref = await h.post(`/api/shop/orders/${sold.id}/refunds`, { amountMinor: 2000, reason: 'یک قلم خراب بود' },
    { token: A.token, headers: { 'Idempotency-Key': `recon-ref-${Date.now()}` } });
  assert.equal(ref.status, 201, JSON.stringify(ref.body));
  const rejected = await order(A);
  await walk(A, rejected.id, [{ to: 'rejected', reason: 'out_of_stock' }]);
  const fake = await order(A, { test: true });
  await walk(A, fake.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']);

  //  دکانِ ب: نقدِ پیک (دادهٔ مرجعِ کاشته‌شده) — دو تحویل در بازه، یکی کهنهٔ تسویه‌نشده، یکی آزمایشی
  B = await readyShop('تطبیق‌ب');
  const [b1, b2, b3, b4] = [await order(B), await order(B), await order(B, { test: true }), await order(B)];
  const t = now();
  await assignment(B.shopId, b1.id, { cash: 5000, at: t - HOUR });
  await assignment(B.shopId, b2.id, { cash: 3000, at: t - 2 * HOUR, settlement: 'cst_recon_1' });
  await assignment(B.shopId, b3.id, { cash: 9999, at: t - HOUR });
  await assignment(B.shopId, b4.id, { cash: 1000, at: t - 40 * 24 * HOUR });
  await query(
    `INSERT INTO courier_settlements (id, shop_id, courier_id, settle_no, client_key, request_hash, assignments_count,
        expected_cash_minor, collected_cash_minor, collection_diff_minor, fare_total_minor, handover_due_minor,
        handed_over_minor, received_minor, shortage_minor, excess_minor, confirmed_by, created_at)
     VALUES ('cst_recon_1',$1,'cour_1',1,'k1','h1',1,3000,3000,0,0,3000,2500,2500,500,0,'owner',$2)`,
    [B.shopId, t - HOUR]
  );
});
test.after(async () => { await h.stop(); });

test('فقط مدیرِ ارشد؛ بازهٔ نادرست ۴۰۰', async () => {
  assert.equal((await get(null)).status, 401);
  assert.equal((await get(clerk)).status, 403);
  const t = now();
  for (const q of [`?from=${t}&to=${t - 1}`, `?from=${t - 93 * 24 * HOUR}&to=${t}`, '?from=abc']) {
    const r = await get(root, q);
    assert.equal(r.status, 400, q);
    assert.equal(r.body.error.code, 'bad_window');
  }
});

test('E2E-ADM-15: هر دکان دقیقاً برابرِ دادهٔ مرجع؛ آزمایشی بیرون؛ کارمزد null', async () => {
  const r = await get(root);
  assert.equal(r.status, 200, JSON.stringify(r.body).slice(0, 300));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const b = r.body;
  assert.equal(b.currency, 'AFN');
  assert.deepEqual(b.commission, { defined: false });
  const row = (id) => b.shops.find((s) => s.shopId === id);

  //  الف: همان عددی که خودِ دکان در /api/shop/orders/reconciliation می‌بیند
  const a = row(A.shopId);
  const own = (await h.get(`/api/shop/orders/reconciliation?from=${b.window.from}&to=${b.window.to}`, { token: A.token })).body;
  assert.deepEqual([a.sales, a.refunds, a.netMinor], [own.collected, own.refunds, own.netMinor]);
  assert.deepEqual(a.sales, { count: 1, totalMinor: TOTAL });
  assert.deepEqual(a.refunds, { count: 1, totalMinor: 2000 });
  assert.equal(a.netMinor, TOTAL - 2000);
  assert.equal(a.cancellations, 1);
  assert.equal(a.commission, null);
  assert.deepEqual(a.issues, []);
  assert.equal(a.shopName, 'دکانِ تطبیق‌الف');

  //  ب: نقدِ پیک
  assert.deepEqual(row(B.shopId).courier, {
    deliveries: 2, collectedMinor: 8000, heldMinor: 6000,
    settlements: 1, handoverDueMinor: 3000, receivedMinor: 2500, shortageMinor: 500, excessMinor: 0,
  });
  assert.deepEqual(row(B.shopId).sales, { count: 0, totalMinor: 0 });

  assert.equal(b.totals.salesMinor, TOTAL);
  assert.equal(b.totals.netMinor, TOTAL - 2000);
  assert.equal(b.totals.courierCollectedMinor, 8000);
  assert.equal(b.totals.shortageMinor, 500);
  assert.equal(b.truncated, false);
});

test('اختلافِ دفتر در صفِ هر دکان می‌آید', async () => {
  //  وضعیتِ «برگشتِ کامل» بی ردیفِ دفتر — همان کدِ issueOf
  const o = await order(A);
  await walk(A, o.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']);
  await query(`UPDATE online_orders SET status='refunded_full' WHERE id=$1`, [o.id]);
  const a = (await get(root)).body.shops.find((s) => s.shopId === A.shopId);
  assert.deepEqual(a.issues.map((i) => [i.orderNo, i.code]), [[Number(o.orderNo ?? o.order_no), 'refund_without_ledger']]);
});
