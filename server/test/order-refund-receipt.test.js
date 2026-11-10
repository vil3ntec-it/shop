'use strict';
/**
 * E2E-RET-11 (§۲۴.۶، §۶.۸) — رسیدِ اصلاحیِ بازپرداختِ سفارشِ آنلاین (`lib/order-refund-receipt.js`، پرچمِ خاموشِ
 * `shop.orders.refund_receipt`)، ساخته از همان دفترِ فقط‌افزودنیِ `online_order_refunds`.
 *
 *   ۱) خالص: هر ردیف یک رسید با شمارهٔ `<سفارش>-R<seq>`، جمعِ اصلی، برگشته پیش و پس از آن و مبلغِ درستِ سفارش؛
 *      ترتیب با seq، نه ترتیبِ ورودی؛ دلیل و کننده فقط برای کارمند.
 *   ۲) ⛔ پرچمِ خاموش ⇒ همان پاسخِ امروز: نه `order.refund.receipts` ِ کارمند، نه `refund.receipts` ِ مشتری.
 *   ۳) روشن ⇒ کارمند (جزئیات و پاسخِ ثبتِ بازپرداخت) و مشتری (پیگیری) همان رسیدها؛ مشتری بی دلیل و کننده؛
 *      تکرارِ همان کلیدِ یکتایی رسیدِ دوم نمی‌سازد و رسیدِ پیشین هرگز عوض نمی‌شود.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const rr = require('../src/lib/order-refund-receipt');

test('E2E-RET-11 ۱) رسیدِ اصلاحی: شماره، جمعِ پیش و پس و مبلغِ درست؛ ترتیب با seq', () => {
  const order = { orderNo: 12, totalMinor: 60000, currency: 'AFN' };
  const entries = [
    { seq: 2, amountMinor: 5000, method: 'cash', final: true, at: 20, reason: 'بستن', actorId: 'u1' },
    { seq: 1, amountMinor: 20000, method: 'cash', final: false, at: 10, reason: 'پاره', actorId: 'u1' },
  ];
  const cust = rr.receiptsOf(order, entries);
  assert.deepEqual(cust, [
    { no: '12-R1', seq: 1, at: 10, orderNo: 12, currency: 'AFN', method: 'cash', final: false, totalMinor: 60000,
      refundedBeforeMinor: 0, amountMinor: 20000, refundedAfterMinor: 20000, netMinor: 40000 },
    { no: '12-R2', seq: 2, at: 20, orderNo: 12, currency: 'AFN', method: 'cash', final: true, totalMinor: 60000,
      refundedBeforeMinor: 20000, amountMinor: 5000, refundedAfterMinor: 25000, netMinor: 35000 },
  ]);
  assert.deepEqual(rr.receiptsOf(order, entries, { staff: true }).map((r) => [r.no, r.reason, r.actorId]), [['12-R1', 'پاره', 'u1'], ['12-R2', 'بستن', 'u1']]);
  assert.deepEqual(rr.receiptsOf(order, []), []);
  assert.equal(entries[0].seq, 2, 'ورودی دست نمی‌خورد');
});

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
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function shop(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `r-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.refunds']) await setFlag(s.shopId, k, true);
  await setFlag(s.shopId, 'shop.orders.refund_tracking', true);
  if (on) await setFlag(s.shopId, rr.FLAG, true);
  assert.equal((await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token })).status, 200);
  assert.equal((await push(s.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

async function storeOrder(s, phone = '0791112233') {
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode });
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 5000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token, total: r.body.order.totalMinor };
}
const move = async (s, id, list) => {
  for (const to of list) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
};
const refund = async (s, id, body, key = `rf-${uniq()}-key`) => {
  const r = await h.post(`/api/shop/orders/${id}/refunds`, body, { token: s.token, headers: { 'Idempotency-Key': key } });
  assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
  return r.body;
};
const track = async (s, token) => {
  const r = await h.get(`/api/store/${s.slug}/orders/${token}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order;
};
const DELIVERED = ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'];
const staffRefund = async (s, id) => {
  const r = await h.get(`/api/shop/orders/${id}`, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order.refund;
};

test('E2E-RET-11 ۲) ⛔ پرچمِ خاموش: کارمند و مشتری بی رسیدِ اصلاحی (همان پاسخِ امروز)', async () => {
  const s = await shop('رسیدخاموش', { on: false });
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  const out = await refund(s, o.id, { amountMinor: 1000, reason: 'خراب' });
  assert.equal('receipts' in out.order.refund, false);
  assert.equal('receipts' in (await staffRefund(s, o.id)), false);
  assert.equal('receipts' in (await track(s, o.token)).refund, false);
});

test('E2E-RET-11 ۳) روشن: رسیدِ هر ردیف برای کارمند و مشتری؛ تکرارِ کلید رسیدِ دوم نمی‌سازد', async () => {
  const s = await shop('رسیدروشن');
  const o = await storeOrder(s);
  const no = (await one('SELECT order_no FROM online_orders WHERE id=$1', [o.id])).order_no;
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  assert.deepEqual((await staffRefund(s, o.id)).receipts, [], 'بی ردیف ⇒ بی رسید');
  const key = `rf-${uniq()}-once`;
  const first = await refund(s, o.id, { amountMinor: 20000, reason: 'یادداشتِ داخلی: یک کیسه پاره بود' }, key);
  assert.deepEqual(first.order.refund.receipts.map((r) => [r.no, r.amountMinor, r.netMinor]), [[`${no}-R1`, 20000, 40000]]);
  const again = await refund(s, o.id, { amountMinor: 20000, reason: 'یادداشتِ داخلی: یک کیسه پاره بود' }, key);
  assert.equal(again.replayed, true);
  assert.equal(again.order.refund.receipts.length, 1, 'تکرارِ همان کلید رسیدِ دوم نمی‌سازد');
  await refund(s, o.id, { amountMinor: 5000, reason: 'بستن', final: true });
  const staff = (await staffRefund(s, o.id)).receipts;
  assert.deepEqual(staff.map((r) => [r.no, r.totalMinor, r.refundedBeforeMinor, r.amountMinor, r.refundedAfterMinor, r.netMinor, r.final]),
    [[`${no}-R1`, 60000, 0, 20000, 20000, 40000, false], [`${no}-R2`, 60000, 20000, 5000, 25000, 35000, true]]);
  assert.equal(staff[0].reason, 'یادداشتِ داخلی: یک کیسه پاره بود');
  assert.equal(staff[0].at, first.order.refund.receipts[0].at, 'رسیدِ پیشین عوض نمی‌شود');
  //  مشتری همان عددها، بی دلیل و کننده
  const body = await track(s, o.token);
  assert.deepEqual(body.refund.receipts.map((r) => [r.no, r.netMinor]), [[`${no}-R1`, 40000], [`${no}-R2`, 35000]]);
  assert.equal(body.refund.receipts.at(-1).netMinor, 60000 - body.refund.refundedMinor, 'مبلغِ درست = جمع − برگشتهٔ دفتر');
  assert.doesNotMatch(JSON.stringify(body.refund.receipts), /یادداشتِ داخلی|actorId|reason/);
});
