'use strict';
/**
 * S124 (§۲۱.۱۹) — گزارشِ پولِ مدیر: GMV، درآمدِ پلتفرم، فروشِ صندوق، فروشِ آنلاین، وصول‌شده، تسویه‌نشده و
 * بازپرداخت جدا، و هر کدام برابرِ جمعِ دستیِ سندهای نمونهٔ همین آزمون (`?money=1` روی درِ تطبیق).
 *
 *   ۱) بی `money` پاسخ همان پیشین است (بی کلیدِ `money`)؛ `money=abc` ⇒ ۴۰۰ ِ `bad_money`.
 *   ۲) صندوق: فاکتورِ زنده در بازه شمرده؛ لغوشده، حذف‌شده، بیرونِ بازه و فاکتورِ تحویلِ سفارشِ آنلاین (`o-…`، `w-…`،
 *      `channel = online`) نه؛ برگشتیِ در بازهٔ فاکتورِ کهنه شمرده، برگشتیِ فاکتورِ آنلاین نه ⇒ هیچ فروشی دو بار.
 *   ۳) آنلاین، بازپرداخت، نقدِ پیک (وصول/تحویل/تسویه‌نشده) همان سندهای کاشته‌شده؛ GMV = صندوق + آنلاین.
 *   ۴) درآمدِ پلتفرم = پرداخت‌های زندهٔ بخشِ دکان در بازه (حذف‌شده و پمپ بیرون)، همان `central-finance.summary`.
 *   ۵) دکانِ نشان‌خوردهٔ «آزمایشی» در هیچ عددِ دکانی نیست.
 *   ۶) `posTotals` روی بازهٔ روزِ کامل همان `report-core.sales` است (یک قاعده، دو جا نه).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const Ref = require('../src/lib/order-refunds');
const RC = require('../src/lib/report-core');
const { posTotals } = require('../src/lib/admin-money');

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
let root = '';
let A = null;
let B = null;
let T = null;
let keySeq = 0;
const get = (q = '') => h.get(`/api/admin/system/reconciliation${q}`, { token: root });
const sample = () => ({
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
  lines: [{ productId: 'p1', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 3333 }, { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 }],
});

async function readyShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const key of ['shop.orders', Ref.FLAG_KEY]) {
    const r = await h.put('/api/admin/pump/live-config', { scope: made.body.shop.id, key, value: true }, { token: root });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { token: u.accessToken, shopId: made.body.shop.id };
}
async function order(shop) {
  const r = await h.post('/api/shop/orders', sample(), { token: shop.token, headers: { 'Idempotency-Key': `money-${++keySeq}-${Date.now()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}
async function walk(shop, id, steps) {
  for (const to of steps) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: shop.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}
async function row(shopId, table, id, data, deletedAt = null) {
  await query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, deleted_at, created_at, updated_at)
     VALUES ('shop','shop',$1,$2,$3,$4::jsonb,$5,1,1)`,
    [shopId, table, id, JSON.stringify(data), deletedAt]);
}
const sale = (shopId, id, finalTotal, createdAt, over = {}, deletedAt = null) => row(shopId, 'sales', id,
  { total: finalTotal + 20, discount: 20, finalTotal, paymentMethod: 'cash', status: 'completed', createdAt, date: '', ...over }, deletedAt);
const ret = (shopId, id, saleId, amount, createdAt) => row(shopId, 'returns', id, { saleId, amount, quantity: 1, createdAt, date: '' });
async function payment(app, amount, paidAt, deleted = false) {
  const id = newId('pay');
  await query(
    `INSERT INTO sub_payments (id, app, tenant_kind, tenant_id, amount, currency, method, receipt_no, paid_at, created_at, deleted)
     VALUES ($1,$2,$3,$4,$5,'AFN','cash',$6,$7,$7,$8)`,
    [id, app, app === 'shop' ? 'shop' : 'station', app === 'shop' ? A.shopId : 'st_money', amount, `R-${id}`, paidAt, deleted]);
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  const t = now();

  //  دکانِ الف: یک سفارشِ برده‌شده (۷۵۰۰) با بازپرداختِ ۲۰۰۰ + دفترِ صندوق
  A = await readyShop('پول‌الف');
  const sold = await order(A);
  await walk(A, sold.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']);
  const refund = await h.post(`/api/shop/orders/${sold.id}/refunds`, { amountMinor: 2000, reason: 'یک قلم خراب بود' },
    { token: A.token, headers: { 'Idempotency-Key': `money-ref-${Date.now()}` } });
  assert.equal(refund.status, 201, JSON.stringify(refund.body));
  await sale(A.shopId, 'a-live', 100, t - HOUR);                                   // ۱۰۰۰۰ ✔
  await sale(A.shopId, 'a-void', 50, t - HOUR, { status: 'cancelled' });          // لغو
  await sale(A.shopId, 'a-gone', 40, t - HOUR, {}, t);                             // حذف‌شده ⇒ لغو
  await sale(A.shopId, `o-${sold.id}`, 75, t - HOUR, { channel: 'online' });       // همان سفارشِ آنلاین
  await sale(A.shopId, 'w-legacy', 60, t - HOUR);                                  // W ِ قدیمی
  await sale(A.shopId, 'a-old', 30, t - 40 * DAY);                                 // بیرونِ بازه
  await ret(A.shopId, 'r-old', 'a-old', 10, t - 2 * HOUR);                         // ۱۰۰۰ ✔ (برگشتِ در بازه)
  await ret(A.shopId, 'r-online', `o-${sold.id}`, 20, t - 2 * HOUR);               // بازپرداختِ آنلاین است، نه صندوق
  await ret(A.shopId, 'r-void', 'a-void', 5, t - 2 * HOUR);                        // فاکتورِ لغوشده

  //  دکانِ ب: نقدِ پیک — ۵۰۰۰ تسویه‌نشده، ۳۰۰۰ تسویه‌شده که ۲۵۰۰ ِ آن تحویلِ دکان شد
  B = await readyShop('پول‌ب');
  const [b1, b2] = [await order(B), await order(B)];
  for (const [o, cash, settlement] of [[b1, 5000, ''], [b2, 3000, 'cst_money_1']]) {
    await query(
      `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, status, expected_collect_minor,
          cash_collected_minor, delivered_at, settlement_id, created_at, updated_at)
       VALUES ($1,$2,$3,1,'shop_courier','delivered',$4,$4,$5,$6,$5,$5)`,
      [newId('dla'), B.shopId, o.id, cash, t - HOUR, settlement]);
  }
  await query(
    `INSERT INTO courier_settlements (id, shop_id, courier_id, settle_no, client_key, request_hash, assignments_count,
        expected_cash_minor, collected_cash_minor, collection_diff_minor, fare_total_minor, handover_due_minor,
        handed_over_minor, received_minor, shortage_minor, excess_minor, confirmed_by, created_at)
     VALUES ('cst_money_1',$1,'cour_1',1,'k1','h1',1,3000,3000,0,0,3000,2500,2500,500,0,'owner',$2)`,
    [B.shopId, t - HOUR]);

  //  دکانِ آزمایشی (نشانِ مدیر): فروشِ صندوق و سفارشِ برده‌شده — هیچ‌کدام در عدد نیست
  T = await readyShop('پول‌آزمایشی');
  const fake = await order(T);
  await walk(T, fake.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']);
  await sale(T.shopId, 't-live', 999, t - HOUR);
  await query(`INSERT INTO shop_census_marks (shop_id, kind, reason, set_by, set_at) VALUES ($1,'test','دکانِ نمایشی','root',$2)`,
    [T.shopId, t]);

  //  درآمدِ پلتفرم: ۵۰۰ ِ زندهٔ دکان؛ حذف‌شده، پمپ و بیرونِ بازه نه
  await payment('shop', 500, t - HOUR);
  await payment('shop', 700, t - HOUR, true);
  await payment('pump', 900, t - HOUR);
  await payment('shop', 300, t - 40 * DAY);
});
test.after(async () => { await h.stop(); });

test('S124: بی money پاسخ همان پیشین؛ money ِ نادرست ۴۰۰', async () => {
  const plain = await get();
  assert.equal(plain.status, 200, JSON.stringify(plain.body));
  assert.equal('money' in plain.body, false);
  const bad = await get('?money=abc');
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_money');
});

test('S124: هفت عدد جدا و برابرِ سندهای نمونه؛ هیچ فروشی دو بار و هیچ دکانِ آزمایشی', async () => {
  const r = await get('?money=1');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const m = r.body.money;
  assert.equal(m.currency, 'AFN');
  assert.deepEqual(m.pos, { shops: 1, invoices: 1, salesMinor: 10000, returnCount: 1, returnsMinor: 1000, netMinor: 9000,
    partial: false, truncated: false });
  assert.deepEqual(m.online, { orders: 1, salesMinor: 7500, refundCount: 1, refundsMinor: 2000, netMinor: 5500 });
  assert.equal(m.posSalesMinor, 10000);
  assert.equal(m.onlineSalesMinor, 7500);
  assert.equal(m.gmvMinor, 17500);
  assert.equal(m.refundsMinor, 3000);
  assert.equal(m.netMinor, 14500);
  assert.equal(m.collectedMinor, 8000);
  assert.equal(m.handedOverMinor, 2500);
  assert.equal(m.unsettledMinor, 5000);
  assert.equal(m.testShopsExcluded, 1);
  assert.deepEqual(m.platformRevenue.currencies, ['AFN']);
  assert.equal(m.platformRevenue.totals.AFN.payments.count, 1);
  assert.equal(m.platformRevenue.totals.AFN.net, 500);
  //  ردیف‌های دکانِ خودِ تطبیق دست نخورده‌اند (دکانِ آزمایشی همان‌جا هست؛ فقط بلوکِ پول بیرونش می‌گذارد)
  assert.ok(r.body.shops.some((s) => s.shopId === T.shopId));
  //  هیچ دادهٔ مشتری
  assert.equal(JSON.stringify(m).includes('0791234567'), false);
});

test('S124: posTotals روی روزِ کامل همان report-core.sales است', () => {
  const day = '2026-03-10';
  const at = Date.parse(`${day}T08:00:00Z`);
  const raw = {
    sales: [
      { id: 's1', total: 120, discount: 20, finalTotal: 100, status: 'completed', createdAt: at, date: '' },
      { id: 's2', total: 50, discount: 0, finalTotal: 50, status: 'cancelled', createdAt: at, date: '' },
      { id: 's3', total: 33.5, discount: 0, finalTotal: 33.5, status: 'completed', createdAt: 0, date: day },
      { id: 's4', total: 70, discount: 0, finalTotal: 70, status: 'completed', createdAt: at - 5 * DAY, date: '' },
    ],
    returns: [
      { id: 'r1', saleId: 's4', amount: 12.25, quantity: 1, createdAt: at + HOUR, date: '' },
      { id: 'r2', saleId: 's2', amount: 9, quantity: 1, createdAt: at, date: '' },
    ],
  };
  const clock = RC.fixedClock(0, 0);
  const core = RC.sales(raw, { from: day, to: day, clock }).totals;
  const mine = posTotals(raw, { fromAt: Date.parse(`${day}T00:00:00Z`), until: Date.parse(`${day}T00:00:00Z`) + DAY, from: day, to: day });
  assert.deepEqual(mine, { invoices: core.invoices, salesMinor: core.sales, returnCount: core.returnCount, returnsMinor: core.returns });
  assert.deepEqual(mine, { invoices: 2, salesMinor: 13350, returnCount: 1, returnsMinor: 1225 });
});
