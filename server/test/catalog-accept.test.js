'use strict';
/**
 * CHK-18 + INV-06 پس از تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): «خریدِ اینترنتی موجودیِ سرور را نه
 * رزرو می‌کند نه کم».
 *
 * پیش‌تر سفارش واحد را رزرو می‌کرد و «قبول» ِ سفارشی که رزروش را فروشِ حضوری برده بود ۴۰۹
 * می‌گرفت. حالا رزروی نیست: سرور دربارهٔ «قبول» داوریِ موجودی نمی‌کند (۲۰۰)، دکان خودش با
 * دلیلِ «ناموجود» رد می‌کند، و برنامه هنگامِ تحویل بیش از موجودی نمی‌فروشد (سیاستِ صندوق،
 * `OrderFulfillment.kt`). هیچ گذاری موجودیِ سرور را عوض نمی‌کند.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

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
test.after(async () => { await h.stop(); });

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

const shelf = async (s, pid) => (await h.get(`/api/shop/catalog/products/${pid}`, { token: s.token })).body.product.stock;

test('آخرین واحد سفارش داده شد و در دکان فروخته شد ⇒ «قبول» ۲۰۰ (سرور داوریِ موجودی نمی‌کند)؛ موجودی فقط با فروشِ دکان', async () => {
  const s = await shop('قبول');
  await push(s, [product('a1'), entry('a1-e', 'a1', 1)]);
  const o = await order(s, 'a1', 1);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  assert.equal(await held(s, 'a1'), 0, 'سفارش رزرو نمی‌کند');
  await push(s, sale('a1-s', 'a1', 1));
  const acc = await go(s, o.body.order.id, { to: 'accepted', from: 'new' });
  assert.equal(acc.status, 200, JSON.stringify(acc.body));
  assert.equal(await statusOf(s, o.body.order.id), 'accepted');
  const st = await shelf(s, 'a1');
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli, st.sellableMilli], [0, 0, 0]);
});

test('دو سفارش برای یک واحد ⇒ هر دو ثبت و قبول‌پذیر؛ رد با دلیلِ «ناموجود» هیچ اثری بر موجودی ندارد', async () => {
  const s = await shop('ترتیب');
  await push(s, [product('b1'), entry('b1-e', 'b1', 1)]);
  const first = await order(s, 'b1', 1);
  const second = await order(s, 'b1', 1);
  assert.deepEqual([first.status, second.status], [201, 201]);
  assert.equal((await go(s, first.body.order.id, { to: 'accepted', from: 'new' })).status, 200);
  const rej = await go(s, second.body.order.id, { to: 'rejected', from: 'new', reason: 'out_of_stock' });
  assert.equal(rej.status, 200, JSON.stringify(rej.body));
  assert.equal(await statusOf(s, second.body.order.id), 'rejected');
  const st = await shelf(s, 'b1');
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli, st.sellableMilli], [1000, 0, 1000]);
  assert.equal((await query('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [s.shopId])).rows[0].n, 0);
});
