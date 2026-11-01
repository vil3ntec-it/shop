'use strict';
/**
 * ORD-14 — ویرایشِ سفارشِ **پرداخت‌شده** با ثبتِ تسویهٔ تفاوت (`lib/order-amend.js`، پرچمِ جدای
 * `shop.orders.amend_paid`).
 *
 *   ۱) ⛔ پرچمِ خاموش: همان ۴۰۹ `amend_paid_order`، درِ تسویه ۴۰۳، `config.amend` بی `paid`.
 *   ۲) روشن، سفارشِ کارمند: ویرایش همان دم و رویداد `settlement` (کم ⇒ `refund`، زیاد ⇒ `charge`) با مبلغ؛
 *      وضعیتِ پرداخت و نقدِ پیک دست نمی‌خورد. تسویه فقط نقد، یک بار، با کلیدِ یکتایی و سابقه.
 *   ۳) سفارشِ ویترین: پذیرشِ مشتری همان ثبت را دارد؛ مشتری «تسویه شد؟» را می‌بیند، نه کارمند و روش.
 *   ۴) پرداختِ در جریان یا بخشی‌بازپرداخت‌شده هنوز نه؛ سفارشِ پرداخت‌نشده بی کلیدِ تازه؛ دکانِ دیگر ۴۰۴.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
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

/** دکانِ روشن با ویترین و دو کالای منتشرشده؛ `amend` = پرچمِ ویرایش. */
async function shop(label, { amend = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `a-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(s.shopId, k, true);
  if (amend) await setFlag(s.shopId, 'shop.orders.amend', true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 333.33, category: 'خوراکی' }),
  ])).status, 200);
  for (const p of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

const key = () => `amend-${uniq()}-key`;
async function staffOrder(s, over = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'احمد', phone: '0791234567' },
    lines: [
      { name: 'برنج', qtyMilli: 2000, unitPriceMinor: 12000 },
      { name: 'روغن', qtyMilli: 1000, unitPriceMinor: 33333 },
      { name: 'نمک', qtyMilli: 1000, unitPriceMinor: 2000 },
    ], ...over,
  }, { token: s.token, headers: { 'Idempotency-Key': key() } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
async function storeOrder(s, phone = '0791112233') {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token };
}

const propose = (s, id, body, k = key()) => h.post(`/api/shop/orders/${id}/amendments`, body, { token: s.token, headers: k ? { 'Idempotency-Key': k } : {} });
const decide = (s, token, amendId, what) => h.post(`/api/store/${s.slug}/orders/${token}/amendments/${amendId}/${what}`, {});
const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const linesOf = (id) => many('SELECT line_no, qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [id]);
const orderRow = (id) => one('SELECT status, subtotal_minor, total_minor, items_count, version FROM online_orders WHERE id=$1', [id]);
const amendEvents = (id) => many(
  `SELECT detail, actor_type, note FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type' LIKE 'amend_%' ORDER BY seq`, [id]);
const move = async (s, id, to) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};


const paid = (id, status = 'paid') =>
  query(`UPDATE online_orders SET payment_status=$2, payment_method='online', cod_status='' WHERE id=$1`, [id, status]);
const settle = (s, id, amendId, body, k = key()) =>
  h.post(`/api/shop/orders/${id}/amendments/${amendId}/settlement`, body, { token: s.token, headers: { 'Idempotency-Key': k } });
const payRow = (id) => one('SELECT payment_status, payment_method, cod_status, total_minor FROM online_orders WHERE id=$1', [id]);

test('⛔ پرچمِ خاموش: پرداخت‌شده همان ۴۰۹، درِ تسویه ۴۰۳، config بی `paid`', async () => {
  const s = await shop('خاموش');
  const o = await staffOrder(s);
  await paid(o.id);
  const r = await propose(s, o.id, { changes: [{ lineNo: 3, qtyMilli: 0 }], consent: { via: 'phone' } });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'amend_paid_order');
  const st = await settle(s, o.id, `am_${'0'.repeat(26)}`, { method: 'cash' });
  assert.equal(st.status, 403);
  assert.equal(st.body.error.code, 'amend_paid_disabled');
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body.amend;
  assert.ok(cfg && !('paid' in cfg) && !('settleMethods' in cfg), JSON.stringify(cfg));
  const rec = await h.get('/api/shop/orders/reconciliation', { token: s.token });
  assert.equal(rec.status, 200);
  assert.ok(!('amendSettlements' in rec.body), 'گزارشِ تطبیق همان پاسخِ پیشین');
  assert.equal((await amendEvents(o.id)).length, 0);
});

test('روشن، سفارشِ کارمند: کم ⇒ «بازپرداخت» و زیاد ⇒ «پرداختِ تکمیلی» ثبت؛ تسویه فقط نقد، یک بار، با کلیدِ یکتایی', async () => {
  const s = await shop('کارمند');
  await setFlag(s.shopId, 'shop.orders.amend_paid', true);
  assert.deepEqual((await h.get('/api/shop/orders/config', { token: s.token })).body.amend.settleMethods, ['cash']);
  const o = await staffOrder(s);
  await paid(o.id);
  const before = await payRow(o.id);
  const r = await propose(s, o.id, { changes: [{ lineNo: 3, qtyMilli: 0 }], consent: { via: 'phone', note: 'تلفنی' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const a = r.body.amendment;
  assert.equal(a.status, 'applied');
  assert.deepEqual(a.settlement, { kind: 'refund', amountMinor: 2000, paidTotalMinor: Number(before.total_minor), settledAt: null, method: null });
  const after = await payRow(o.id);
  assert.equal(Number(after.total_minor), Number(before.total_minor) - 2000);
  assert.equal(after.payment_status, 'paid', 'وضعیتِ پرداخت دست نمی‌خورد (بی درگاه)');
  assert.equal(after.cod_status, before.cod_status);
  assert.equal((await amendEvents(o.id))[0].detail.settlement.kind, 'refund');

  //  حساب: بدهیِ باز در گزارشِ تطبیق
  const open = (await h.get('/api/shop/orders/reconciliation', { token: s.token })).body.amendSettlements;
  assert.deepEqual(open.open.map((x) => [x.orderId, x.amendId, x.kind, x.amountMinor]), [[o.id, a.id, 'refund', 2000]]);
  assert.equal(open.openRefundMinor, 2000);
  assert.equal(open.openChargeMinor, 0);
  assert.deepEqual(open.settled, { refundMinor: 0, chargeMinor: 0, count: 0 });
  //  فقط نقد
  const card = await settle(s, o.id, a.id, { method: 'card' });
  assert.equal(card.status, 400);
  assert.equal(card.body.error.code, 'bad_method');
  const k = key();
  const ok = await settle(s, o.id, a.id, { method: 'cash', note: 'پول به دستش داده شد' }, k);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.replayed, false);
  assert.equal(ok.body.amendment.settlement.method, 'cash');
  assert.ok(ok.body.amendment.settlement.settledAt > 0);
  assert.equal((await h.get(`/api/shop/orders/${o.id}`, { token: s.token })).body.order.amendment.settlement.method, 'cash');
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.amend_settle'`, [s.shopId])).n, 1);
  //  تکرار ⇒ همان؛ کلیدِ دیگر ⇒ پیش‌تر تسویه شده؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹
  const again = await settle(s, o.id, a.id, { method: 'cash', note: 'پول به دستش داده شد' }, k);
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  const second = await settle(s, o.id, a.id, { method: 'cash' });
  assert.equal(second.status, 409);
  assert.equal(second.body.error.code, 'already_settled');
  const reused = await settle(s, o.id, a.id, { method: 'cash', note: 'دیگر' }, k);
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  const evs = await amendEvents(o.id);
  assert.deepEqual(evs.map((e) => e.detail.type), ['amend_applied', 'amend_settled']);
  assert.equal(evs[1].detail.amountMinor, 2000);
  assert.equal(evs[1].note, 'پول به دستش داده شد');
  assert.equal((await payRow(o.id)).payment_status, 'paid');

  //  زیاد ⇒ پرداختِ تکمیلی؛ هم‌زمان دو تسویه با دو کلید ⇒ یکی
  const o2 = await staffOrder(s);
  await paid(o2.id);
  const up = await propose(s, o2.id, { changes: [{ lineNo: 1, qtyMilli: 3000 }], consent: { via: 'in_person' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.amendment.settlement.kind, 'charge');
  assert.equal(up.body.amendment.settlement.amountMinor, 12000);
  const both = await Promise.all([settle(s, o2.id, up.body.amendment.id, { method: 'cash' }), settle(s, o2.id, up.body.amendment.id, { method: 'cash' })]);
  assert.deepEqual(both.map((x) => x.status).sort(), [200, 409]);
  assert.equal((await amendEvents(o2.id)).filter((e) => e.detail.type === 'amend_settled').length, 1);
  //  حساب پس از تسویه: هیچ بدهیِ باز؛ جمعِ بازه به تفکیکِ نوع
  const done = (await h.get('/api/shop/orders/reconciliation', { token: s.token })).body.amendSettlements;
  assert.deepEqual(done.open, []);
  assert.deepEqual(done.settled, { refundMinor: 2000, chargeMinor: 12000, count: 2 });
});

test('سفارشِ ویترین: پذیرشِ مشتری همان ثبت را دارد؛ مشتری «تسویه شد؟» را می‌بیند', async () => {
  const s = await shop('ویترینِ پرداخت‌شده');
  await setFlag(s.shopId, 'shop.orders.amend_paid', true);
  const so = await storeOrder(s);
  await paid(so.id);
  const r = await propose(s, so.id, { changes: [{ lineNo: 1, qtyMilli: 2000 }], reason: 'out_of_stock' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.ok(!('settlement' in r.body.amendment), 'تا پذیرش هیچ تسویه‌ای نیست');
  const ok = await decide(s, so.token, r.body.amendment.id, 'accept');
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.status, 'applied');
  const view = (await track(s, so.token)).body.order.amendment;
  assert.deepEqual(view.settlement, { kind: 'refund', amountMinor: 12000, settled: false });
  assert.doesNotMatch(JSON.stringify(view), /method|paidTotalMinor/, 'روش و جزئیاتِ داخلی برای مشتری نیست');
  const st = await settle(s, so.id, r.body.amendment.id, { method: 'cash' });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  assert.equal((await track(s, so.token)).body.order.amendment.settlement.settled, true);
});

test('پرداختِ در جریان یا بخشی‌بازپرداخت‌شده نه؛ پرداخت‌نشده بی کلیدِ تازه؛ دکانِ دیگر ۴۰۴', async () => {
  const s = await shop('مرز');
  await setFlag(s.shopId, 'shop.orders.amend_paid', true);
  for (const st of ['pending', 'partially_refunded']) {
    const o = await staffOrder(s);
    await paid(o.id, st);
    const r = await propose(s, o.id, { changes: [{ lineNo: 3, qtyMilli: 0 }], consent: { via: 'phone' } });
    assert.equal(r.status, 409, st);
    assert.equal(r.body.error.code, 'amend_paid_order', st);
  }
  const u = await staffOrder(s);
  const r = await propose(s, u.id, { changes: [{ lineNo: 3, qtyMilli: 0 }], consent: { via: 'phone' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.ok(!('settlement' in r.body.amendment), 'پرداخت‌نشده همان پاسخِ پیشین');
  assert.ok(!('settlement' in (await amendEvents(u.id))[0].detail));
  const none = await settle(s, u.id, r.body.amendment.id, { method: 'cash' });
  assert.equal(none.status, 404);
  assert.equal(none.body.error.code, 'amend_not_found');

  const o = await staffOrder(s);
  await paid(o.id);
  const a = (await propose(s, o.id, { changes: [{ lineNo: 3, qtyMilli: 0 }], consent: { via: 'phone' } })).body.amendment;
  const b = await shop('همسایه');
  await setFlag(b.shopId, 'shop.orders.amend_paid', true);
  const cross = await settle(b, o.id, a.id, { method: 'cash' });
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
  assert.equal((await amendEvents(o.id)).length, 1);
});
