'use strict';
/**
 * CHK-18 + INV-06: «قبول» ِ سفارشی که رزروش دیگر پشتوانه ندارد.
 *
 * سناریوی سرتاسری (test/e2e/stock.e2e.mjs) روی main نشان داد: مشتری آخرین واحد را از ویترین
 * رزرو می‌کند، فروشنده همان واحد را در دکان می‌فروشد (فروشِ ثبت‌شده رد نمی‌شود و وظیفهٔ
 * «رزروِ بی‌پشتوانه» ساخته می‌شود — درست)، ولی بعد «قبول» ِ همان سفارش ۲۰۰ می‌گرفت و رزرو
 * بی‌مهلت می‌شد: به مشتری قولِ کالایی داده می‌شد که در دکان نیست. سند (CHK-18): «قبول ⇒
 * بی‌مهلت یا ۴۰۹ اگر موجودی رفته».
 *
 * قاعده: رزروها به ترتیبِ ساخت از موجودیِ قابلِ فروش برداشته می‌شوند؛ اگر سهمِ این سفارش
 * بیرون بیفتد ⇒ ۴۰۹ `out_of_stock` و سفارش «جدید» می‌ماند (رد با دلیل آزادش می‌کند).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const stock = require('../src/lib/stock');

let admin = '';

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { stock.stopSweeper(); await h.stop(); });

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  const s = await h.signIn(u, 'shop');
  const shopId = made.body.shop.id;
  for (const key of ['shop.orders', 'shop.catalog']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { token: s.accessToken, shopId, device: `dev-${shopId}`.slice(0, 60) };
}

let n = 0;
const opId = () => `01JCA${String(Date.now()).slice(-8)}${String(++n).padStart(8, '0')}`;
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops }, headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}
const product = (id) => op('insert', 'products', id, { name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now() });
const entry = (id, pid, units) => op('insert', 'warehouseEntries', id, { productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() });
const sale = (id, pid, q) => [
  op('insert', 'sales', id, { status: 'completed', total: 0, createdAt: Date.now() }),
  op('insert', 'saleItems', `${id}-i`, { saleId: id, productId: pid, quantity: q, returnedQty: 0, unitPrice: 50, createdAt: Date.now() }),
];
let k = 0;
const order = (s, pid, q) => h.post('/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری' },
  lines: [{ productId: pid, name: 'کالا', unit: 'دانه', qtyMilli: q * 1000, unitPriceMinor: 5000 }],
}, { token: s.token, headers: { 'Idempotency-Key': `acc-${++k}-${Date.now()}` } });
const go = (s, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
const statusOf = async (s, id) => (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order.status;
const held = async (s, pid) => (await h.get(`/api/shop/catalog/products/${pid}`, { token: s.token })).body.product.stock.reservedMilli;

test('آخرین واحد رزرو شد و در دکان فروخته شد ⇒ «قبول» ۴۰۹ و سفارش «جدید» می‌ماند؛ رد ⇒ رزرو آزاد', async () => {
  const s = await shop('بی‌پشتوانه');
  await push(s, [product('p1'), entry('p1-e', 'p1', 1)]);
  const o = await order(s, 'p1', 1);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  await push(s, sale('p1-s', 'p1', 1));   //  فروشِ حضوری — رد نمی‌شود (INV-06)
  const acc = await go(s, o.body.order.id, { to: 'accepted', from: 'new' });
  assert.equal(acc.status, 409, JSON.stringify(acc.body));
  assert.equal(acc.body.error.code, 'out_of_stock');
  assert.equal(await statusOf(s, o.body.order.id), 'new');
  assert.equal(await held(s, 'p1'), 1000, 'رزرو تا تصمیمِ دکان می‌ماند');
  assert.equal((await go(s, o.body.order.id, { to: 'rejected', from: 'new', reason: 'out_of_stock' })).status, 200);
  assert.equal(await held(s, 'p1'), 0);
});

test('رزروِ با پشتوانه مثلِ پیش قبول و بی‌مهلت می‌شود', async () => {
  const s = await shop('با پشتوانه');
  await push(s, [product('p2'), entry('p2-e', 'p2', 3)]);
  const o = await order(s, 'p2', 2);
  assert.equal(o.status, 201);
  await push(s, sale('p2-s', 'p2', 1));   //  ۳ − ۱ = ۲ ⇒ هنوز کافی
  const acc = await go(s, o.body.order.id, { to: 'accepted', from: 'new' });
  assert.equal(acc.status, 200, JSON.stringify(acc.body));
  const rows = (await query(`SELECT expires_at FROM stock_reservations WHERE order_id=$1 AND status='held'`, [o.body.order.id])).rows;
  assert.ok(rows.length && rows.every((r) => r.expires_at === null), 'پس از قبول بی‌مهلت');
});

test('دو رزرو و یک واحد کم شد ⇒ سفارشِ زودتر قبول می‌شود، دیرتر ۴۰۹ (ترتیبِ رسیدن)', async () => {
  const s = await shop('ترتیب');
  await push(s, [product('p3'), entry('p3-e', 'p3', 2)]);
  const a = await order(s, 'p3', 1);
  const b = await order(s, 'p3', 1);
  assert.deepEqual([a.status, b.status], [201, 201]);
  await push(s, sale('p3-s', 'p3', 1));   //  حالا فقط یک واحد پشتِ دو رزرو
  assert.equal((await go(s, b.body.order.id, { to: 'accepted', from: 'new' })).status, 409, 'دیرتر');
  assert.equal((await go(s, a.body.order.id, { to: 'accepted', from: 'new' })).status, 200, 'زودتر');
});
