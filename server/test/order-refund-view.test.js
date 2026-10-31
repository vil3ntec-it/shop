'use strict';
/**
 * NTF-08 — پیگیریِ بازپرداخت برای مشتریِ ویترین (`lib/order-refund-view.js`).
 *
 *   ۱) ⛔ پرچمِ خاموش ⇒ پاسخِ پیگیری بی کلیدِ `refund` (همان شکلِ پیشین)، حتی با دفترِ پُر.
 *   ۲) روشن: بی درخواستِ برگشت `null`؛ «درخواستِ برگشت» ⇒ `requested`؛ بخشی ⇒ `in_progress`؛
 *      بستنِ بخشی (`final`) ⇒ `partial`؛ کامل ⇒ `full` — مبلغ، مانده و ردیف‌ها همان دفتر.
 *   ۳) ⛔ مشتری دلیلِ نوشتهٔ کارمند و کننده را نمی‌بیند؛ توکنِ دکانِ دیگر ۴۰۴.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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

async function shop(label, { view = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `r-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.refunds']) await setFlag(s.shopId, k, true);
  if (view) await setFlag(s.shopId, 'shop.orders.refund_tracking', true);
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
const refund = async (s, id, body) => {
  const r = await h.post(`/api/shop/orders/${id}/refunds`, body, { token: s.token, headers: { 'Idempotency-Key': `rf-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
};
const track = async (s, token) => {
  const r = await h.get(`/api/store/${s.slug}/orders/${token}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order;
};
const DELIVERED = ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'];

test('⛔ پرچمِ خاموش: پیگیری بی کلیدِ refund، حتی با دفترِ پُر', async () => {
  const s = await shop('خاموش', { view: false });
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  await refund(s, o.id, { amountMinor: 1000, reason: 'خراب' });
  assert.equal('refund' in (await track(s, o.token)), false);
});

test('روشن: requested ⇒ in_progress ⇒ partial، با مبلغ و مانده؛ بی دلیل و کننده', async () => {
  const s = await shop('بخشی');
  const o = await storeOrder(s);
  assert.equal(o.total, 60000);
  assert.equal((await track(s, o.token)).refund, null, 'بی درخواستِ برگشت');
  await move(s, o.id, DELIVERED);
  assert.equal((await track(s, o.token)).refund, null);
  await move(s, o.id, ['return_requested']);
  let r = (await track(s, o.token)).refund;
  assert.equal(r.status, 'requested');
  assert.equal(typeof r.requestedAt, 'number');
  assert.deepEqual([r.refundedMinor, r.remainingMinor, r.entries], [0, 60000, []]);
  assert.equal(r.updatedAt, r.requestedAt);

  await refund(s, o.id, { amountMinor: 20000, reason: 'یادداشتِ داخلی: یک کیسه پاره بود' });
  r = (await track(s, o.token)).refund;
  assert.equal(r.status, 'in_progress');
  assert.deepEqual([r.refundedMinor, r.remainingMinor], [20000, 40000]);
  assert.deepEqual(r.entries.map((e) => [e.amountMinor, e.method, e.final]), [[20000, 'cash', false]]);
  assert.deepEqual(Object.keys(r.entries[0]).sort(), ['amountMinor', 'at', 'final', 'method']);
  assert.equal(r.updatedAt, r.entries[0].at);

  await refund(s, o.id, { amountMinor: 5000, reason: 'بستن', final: true });
  const body = await track(s, o.token);
  r = body.refund;
  assert.equal(r.status, 'partial');
  assert.deepEqual([r.refundedMinor, r.remainingMinor, r.currency], [25000, 35000, 'AFN']);
  assert.doesNotMatch(JSON.stringify(body), /یادداشتِ داخلی|actorId|"reason":"بستن"/, 'نه دلیلِ کارمند، نه کننده');
});

test('روشن: کامل ⇒ full؛ توکنِ دکانِ دیگر ۴۰۴', async () => {
  const s = await shop('کامل');
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  await refund(s, o.id, { amountMinor: 60000, reason: 'همه برگشت' });
  const r = (await track(s, o.token)).refund;
  assert.equal(r.status, 'full');
  assert.deepEqual([r.refundedMinor, r.remainingMinor], [60000, 0]);
  const b = await shop('دیگر');
  assert.equal((await h.get(`/api/store/${b.slug}/orders/${o.token}`)).status, 404);
});
