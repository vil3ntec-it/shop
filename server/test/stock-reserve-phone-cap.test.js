'use strict';
/**
 * ۲-۲۱ ِ صاحب‌کار (R5 الف؛ `lib/stock-reserve.js` ‹`phoneCapOk`›، همان پرچمِ `shop.stock_reserve`): سقفِ نگه‌داشتنِ هر شماره.
 *
 *   یک شمارهٔ مشتری (ویترین/بازار) روی هم فقط نیمِ موجودیِ آزادِ هر کالا را نگه می‌دارد (دست‌کم یک دانه). «آزاد» از دیدِ همان
 *   شماره = قابل‌فروش − نگه‌داشتهٔ شماره‌های دیگر. سفارشِ تازه‌ای که شماره را از سقف بگذراند **مثلِ امروز ثبت می‌شود، فقط بی
 *   نگه‌داشتن** (کلِ سفارش؛ بعداً هم قبول، ویرایش یا جمع‌آوری برایش نگه‌داشته نمی‌سازد) — هیچ سفارشی رد نمی‌شود.
 *   نگه‌داشتهٔ گذشته یا آزادشده نمی‌شمارد؛ سفارشِ کارمند (تلفن، دستی…) سقف ندارد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');

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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { token: s.accessToken, shopId: made.body.shop.id, device: `dev-pc-${made.body.shop.id}`.slice(0, 60) };
}
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
let opN = 0;
const opId = () => `01JPC${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops }, headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
const entry = (id, pid, units) => op('insert', 'warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});
const refresh = async (s) => assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
let keyN = 0;
const fresh = () => `pcap-${++keyN}-${Date.now()}`;

/** دکانِ ویترین‌دار با نگه‌داشتنِ روشن و کالاهای [stock] (شناسه ⇐ عدد) */
async function storeShop(name, stock) {
  const s = await shop(name);
  for (const k of ['shop.orders', 'shop.catalog', 'shop.stock_reserve', 'shop.storefront']) await live(s.shopId, k, true);
  s.slug = `pcap-${Date.now().toString(36)}-${++keyN}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: 'ویترینِ سقف', contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  for (const [pid, units] of Object.entries(stock)) await push(s, [product(pid), entry(`${pid}-e`, pid, units)]);
  await refresh(s);
  for (const pid of Object.keys(stock)) {
    assert.equal((await h.put(`/api/shop/storefront/products/${pid}`, { published: true }, { token: s.token })).status, 200);
  }
  return s;
}
/** سفارشِ ویترین با شمارهٔ تأییدشده ⇒ شناسهٔ سفارش ([lines]: [کالا، عدد]) */
async function customerOrder(s, phone, lines) {
  resetAll();
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const ticket = (await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode })).body.ticket;
  assert.ok(ticket, 'تیکتِ شمارهٔ تأییدشده');
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: lines.map(([productId, n]) => ({ productId, qtyMilli: n * 1000 })),
  }, { headers: { 'Idempotency-Key': fresh(), 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, ck.body.order.orderNo])).id;
  links.set(id, ck.body.order.token);
  return id;
}
const links = new Map();   //  شناسهٔ سفارش ⇐ توکنِ پیگیری (درِ افزودنِ مشتری)
const heldOf = async (s, orderId) => (await query(
  `SELECT product_id, qty_milli FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held' ORDER BY line_no`, [s.shopId, orderId]
)).rows.map((r) => [r.product_id, Number(r.qty_milli)]);

test('یک شماره روی هم فقط نیمِ آزاد را نگه می‌دارد؛ سفارشِ بیشتر ۲۰۱ ولی بی نگه‌داشتن؛ شمارهٔ دیگر جدا', async () => {
  const s = await storeShop('سقف-شماره', { c1: 4 });
  const a = await customerOrder(s, '0791220001', [['c1', 2]]);
  assert.deepEqual(await heldOf(s, a), [['c1', 2000]], 'نیمِ ۴ = ۲ ⇒ نگه داشته');
  const b = await customerOrder(s, '0791220001', [['c1', 1]]);
  assert.deepEqual(await heldOf(s, b), [], 'همان شماره: ۲ + ۱ از نیمِ ۴ بیشتر ⇒ ثبت، بی نگه‌داشتن');
  const order = (await h.get(`/api/shop/orders/${b}`, { token: s.token })).body.order;
  assert.equal(order.status, 'new');
  assert.equal(order.stockHold, undefined, 'همان پاسخِ سفارشِ بی نگه‌داشتن');
  //  شمارهٔ دیگر: آزاد از دیدِ او ۴ − ۲ = ۲ ⇒ نیم = ۱
  const c = await customerOrder(s, '0791220002', [['c1', 1]]);
  assert.deepEqual(await heldOf(s, c), [['c1', 1000]]);
  //  قبولِ سفارشِ بی نگه‌داشته هم نگه‌داشته نمی‌سازد
  assert.equal((await h.post(`/api/shop/orders/${b}/transition`, { to: 'accepted' }, { token: s.token })).status, 200);
  assert.deepEqual(await heldOf(s, b), []);
});

test('دست‌کم یک دانه؛ یک سفارشِ بیش از نیم (هر کالایش) کلاً بی نگه‌داشتن', async () => {
  const s = await storeShop('سقف-یک', { d1: 1, d2: 3, d3: 10 });
  const a = await customerOrder(s, '0791220003', [['d1', 1]]);
  assert.deepEqual(await heldOf(s, a), [['d1', 1000]], 'تنها عدد: نیم کمتر از یک ⇒ یک دانه');
  const b = await customerOrder(s, '0791220004', [['d3', 2], ['d2', 2]]);
  assert.deepEqual(await heldOf(s, b), [], 'd2: ۲ از نیمِ ۳ بیشتر ⇒ کلِ سفارش مثلِ امروز');
  const c = await customerOrder(s, '0791220004', [['d3', 5]]);
  assert.deepEqual(await heldOf(s, c), [['d3', 5000]], 'سفارشِ بی نگه‌داشته از سهمِ همان شماره نمی‌شمارد');
});

test('نگه‌داشتهٔ گذشته یا آزادشده نمی‌شمارد؛ سفارشِ کارمند سقف ندارد', async () => {
  const s = await storeShop('سقف-گذشته', { e1: 4 });
  const a = await customerOrder(s, '0791220005', [['e1', 2]]);
  assert.deepEqual(await heldOf(s, a), [['e1', 2000]]);
  await query(`UPDATE stock_reservations SET expires_at = $2 WHERE shop_id=$1 AND order_id=$3`, [s.shopId, Date.now() - 1000, a]);
  const b = await customerOrder(s, '0791220005', [['e1', 2]]);
  assert.deepEqual(await heldOf(s, b), [['e1', 2000]], 'نگه‌داشتهٔ گذشته از سهم بیرون');
  assert.equal((await h.post(`/api/shop/orders/${b}/transition`, { to: 'rejected', reason: 'out_of_stock' }, { token: s.token })).status, 200);
  const c = await customerOrder(s, '0791220005', [['e1', 2]]);
  assert.deepEqual(await heldOf(s, c), [['e1', 2000]], 'آزادشده هم');
  //  کارمند: همان شماره، باقیِ آزاد (۲ عدد) ⇒ کامل نگه داشته
  const st = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری', phone: '0791220005' },
    lines: [{ productId: 'e1', name: 'کالای e1', unit: 'دانه', qtyMilli: 2000, unitPriceMinor: 5000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  assert.deepEqual(await heldOf(s, st.body.order.id), [['e1', 2000]]);
});

/** CHK-21: افزودنِ قلم از ویترین به همان سفارش ⇒ همان شماره، همان سقف (پیش‌تر فقط سقفِ هر سفارش) */
async function addItems(s, phone, orderId, lines) {
  resetAll();
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const ticket = (await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode })).body.ticket;
  return h.post(`/api/store/${s.slug}/orders/${links.get(orderId)}/items`, { lines: lines.map(([productId, n]) => ({ productId, qtyMilli: n * 1000 })) },
    { headers: { 'Idempotency-Key': fresh(), 'X-Store-Ticket': ticket } });
}

test('CHK-21: افزودن به همان سفارش هم از سقفِ شماره نمی‌گذرد — نگه‌داشتهٔ پیشین می‌ماند، افزودهٔ بیش از سقف بی نگه‌داشتن', async () => {
  const s = await storeShop('سقف-افزودن', { f1: 4, f2: 6 });
  await live(s.shopId, 'shop.orders.add_items', true);
  const a = await customerOrder(s, '0791220006', [['f1', 2]]);
  assert.deepEqual(await heldOf(s, a), [['f1', 2000]], 'نیمِ ۴ = ۲ ⇒ نگه داشته');
  //  همان کالا، همان قیمت ⇒ همان قلم بزرگ می‌شود (۲ ⇒ ۳): ۳ از نیمِ ۴ بیشتر ⇒ ثبت، ولی نگه‌داشته همان ۲ ِ پیشین
  const r1 = await addItems(s, '0791220006', a, [['f1', 1]]);
  assert.equal(r1.status, 201, JSON.stringify(r1.body));
  assert.deepEqual(await heldOf(s, a), [['f1', 2000]], 'افزودن از سقف گذشت ⇒ بی نگه‌داشتنِ افزوده و بی گم شدنِ پیشین');
  //  کالای دیگر در سقفِ خودش (۳ از نیمِ ۶) ⇒ نگه داشته؛ کالای پیشین دست‌نخورده
  const r2 = await addItems(s, '0791220006', a, [['f2', 3]]);
  assert.equal(r2.status, 201, JSON.stringify(r2.body));
  assert.deepEqual(await heldOf(s, a), [['f1', 2000], ['f2', 3000]], 'کالای تازه در سقف ⇒ نگه داشته');
  //  همان کالای دوم بیش از سقف (۳ + ۱ از نیمِ ۶) ⇒ همان ۳
  const r3 = await addItems(s, '0791220006', a, [['f2', 1]]);
  assert.equal(r3.status, 201, JSON.stringify(r3.body));
  assert.deepEqual(await heldOf(s, a), [['f1', 2000], ['f2', 3000]]);
  const lines = (await query('SELECT product_id, qty_milli FROM online_order_lines WHERE shop_id=$1 AND order_id=$2 ORDER BY line_no', [s.shopId, a])).rows
    .map((l) => [l.product_id, Number(l.qty_milli)]);
  assert.deepEqual(lines, [['f1', 3000], ['f2', 4000]], 'قلم‌ها همه ثبت شدند (هیچ افزودنی رد نشد)');
  //  بازسازی (S86) همان قاعده را دارد: قلمِ بزرگ‌شدهٔ بیش از سقف «ناهم‌اندازه» نیست و دوباره نگه داشته نمی‌شود
  const reserve = require('../src/lib/stock-reserve');
  const rb = await reserve.rebuild(s.shopId);
  assert.equal(rb.ok, true, JSON.stringify(rb.changes));
  const vd = await require('../src/lib/verify-data').verify({ shopId: s.shopId, only: ['sum'] });
  assert.deepEqual(vd.checks.filter((x) => x.count > 0).map((x) => x.id), [], 'جمعِ نگه‌داشته با دفتر می‌خواند');
  assert.deepEqual(await heldOf(s, a), [['f1', 2000], ['f2', 3000]], 'بازسازی چیزی عوض نکرد');
});
