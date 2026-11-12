'use strict';
/**
 * بازبینیِ امنیتیِ دسته‌های ۴۱ و ۴۲ (docs/SECURITY-AUDIT-fa.md، «بازبینیِ ۴۲»). هر آزمون پیش از رفع سرخ بود؛ روی PGlite و PostgreSQL
 * (SR42-2 فقط PostgreSQL: PGlite یک اتصال است و دو تراکنش را درهم نمی‌بافد).
 *
 *   SR42-1  O-06 + CHK-21 (`stock-reserve.syncOrderIn`): قلمِ بزرگ‌شده جای صفِ («بررسی») پیشینِ خودش را نگه می‌داشت ⇒ مشتری با
 *           سفارشِ کوچکِ زود و «افزودن»ِ دیر از سفارش‌های پیش‌ترِ دیگران جلو می‌زد: فروشِ حضوریِ بعدی سفارشِ **دیگری** را به بررسی
 *           می‌برد؛ و با موجودیِ کهنه (سنجشِ افزودن بی نگه‌داشتهٔ دیگران) همان دم.
 *   SR42-2  O-06 (`stock-reserve.notifyReviewIn`): نگه‌دارندهٔ قفلِ موجودی (فروشِ حضوری/دستی) برای خبرِ `order_review` قفلِ کلیدِ
 *           ردیفِ سفارش (FK ِ `order_notify_outbox`) را می‌خواست، و گذار/ویرایش/بسته‌بندیِ همان سفارش ردیف را `FOR UPDATE` گرفته و
 *           منتظرِ قفلِ موجودی بود ⇒ بن‌بست؛ PostgreSQL گذارِ کارمند را با ۴۰P01 (۵۰۰) می‌انداخت.
 *   R6      O-06 (`stock-reserve`): خاموش کردنِ `shop.stock_reserve` نگه‌داشته‌های باز را آزاد نمی‌کرد و نشانِ ویترین/کاتالوگ آن‌ها را
 *           «گرفته» می‌شمرد در حالی که ثبت دیگر نمی‌شمرد؛ «افزودن» نگه‌داشتهٔ تازه می‌ساخت، «قبول» بی‌مهلتش و «تحویل» دراز.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const { query, one, newId, now, tx } = db;
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
const lib = () => require('../src/lib/stock-reserve');

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

// ── کمکی‌ها (همان شکلِ stock-reserve.test.js) ─────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { token: s.accessToken, shopId: made.body.shop.id, device: `dev-sr42-${made.body.shop.id}`.slice(0, 60) };
}
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
let opN = 0;
const opId = () => `01JSQ${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
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
const sale = (id, items) => [
  op('insert', 'sales', id, { status: 'completed', total: 0, createdAt: Date.now() }),
  ...items.map(([iid, pid, q]) => op('insert', 'saleItems', iid, {
    saleId: id, productId: pid, quantity: q, returnedQty: 0, unitPrice: 50, createdAt: Date.now(),
  })),
];
const refresh = async (s) => assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
const review = async (s, id) => (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order.stockHold;
let keyN = 0;
const fresh = () => `sr42-${++keyN}-${Date.now()}`;

/** دکانِ ویترین‌دار با نگه‌داشتن و «افزودن به سفارش» روشن و کالای [pid] با [units] عدد */
async function storeShop(name, pid, units) {
  const s = await shop(name);
  for (const k of ['shop.orders', 'shop.catalog', 'shop.stock_reserve', 'shop.storefront', 'shop.orders.add_items']) await live(s.shopId, k, true);
  s.slug = `sr42-${Date.now().toString(36)}-${++keyN}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: 'ویترینِ بازبینی', contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  await push(s, [product(pid), entry(`${pid}-e`, pid, units)]);
  await refresh(s);
  assert.equal((await h.put(`/api/shop/storefront/products/${pid}`, { published: true }, { token: s.token })).status, 200);
  return s;
}
/** مشتریِ ویترین با شمارهٔ تأییدشده: سفارشِ [qty] عدد ⇒ { id, token, ticket } */
async function customerOrder(s, phone, pid, qty) {
  resetAll();
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const ticket = (await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode })).body.ticket;
  assert.ok(ticket, 'تیکتِ شمارهٔ تأییدشده');
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: pid, qtyMilli: qty * 1000 }],
  }, { headers: { 'Idempotency-Key': fresh(), 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, ck.body.order.orderNo])).id;
  return { id, token: ck.body.order.token, ticket };
}
const addItems = (s, o, pid, qty) => h.post(`/api/store/${s.slug}/orders/${o.token}/items`, { lines: [{ productId: pid, qtyMilli: qty * 1000 }] },
  { headers: { 'Idempotency-Key': fresh(), 'X-Store-Ticket': o.ticket } });
const heldRows = async (s, orderId) => (await query(
  `SELECT line_no, qty_milli FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held' ORDER BY line_no`, [s.shopId, orderId]
)).rows.map((r) => [Number(r.line_no), Number(r.qty_milli)]);

// ---------------------------------------------------------------- SR42-1

test('SR42-1: «افزودن» ِ دیر به سفارشِ زود جلوی سفارشِ پیش‌ترِ دیگری نمی‌زند — فروشِ حضوریِ بعدی همان افزاینده را به بررسی می‌برد', async () => {
  //  ۵ عدد: سقفِ شمارهٔ x (۲-۲۱، CHK-18) نیمِ آزادِ او = (۵ − ۱ ِ y) / ۲ = ۲ ⇒ افزودن در سقف
  const s = await storeShop('بازبینی۴۲-صف', 'q1', 5);
  const x = await customerOrder(s, '0791110001', 'q1', 1);   //  زود: یک عدد
  const y = await customerOrder(s, '0791110002', 'q1', 1);   //  سپس مشتریِ دیگر: یک عدد
  //  افزودن روی همان قلم (هم‌قیمت)
  const a = await addItems(s, x, 'q1', 1);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.deepEqual(await heldRows(s, x.id), [[1, 2000]], 'همان قلم بزرگ شد');
  //  صندوق سه عدد فروخت (INV-19، هرگز رد نمی‌شود) ⇒ ۲ قابل‌فروش، ۳ نگه‌داشته: ادعای تازه (افزودهٔ x) کم می‌آورد، نه y
  await push(s, sale('q1-s', [['q1-i', 'q1', 3]]));
  await refresh(s);
  assert.equal((await review(s, y.id)).review, false, 'سفارشِ پیش‌ترِ y پوشش دارد');
  assert.equal((await review(s, x.id)).review, true, 'افزایندهٔ دیر در بررسی');
});

test('SR42-1: موجودیِ کهنه — افزودنِ بی سنجشِ نگه‌داشتهٔ دیگران همان دم سفارشِ دیگری را به بررسی نمی‌برد', async () => {
  const s = await storeShop('بازبینی۴۲-کهنه', 'k1', 2);
  const x = await customerOrder(s, '0791110003', 'k1', 1);
  const y = await customerOrder(s, '0791110004', 'k1', 1);
  assert.equal((await review(s, y.id)).status, 'held');
  //  دستگاه‌های دکان دیر دیده شده‌اند ⇒ «کهنه»: سنجشِ افزودن فقط دفترِ ۲ عدد را می‌بیند (نه نگه‌داشتهٔ y)
  await query(`UPDATE sync_devices SET last_seen_at = $2 WHERE app='shop' AND account_id=$1`, [s.shopId, Date.now() - 3_600_000]);
  const a = await addItems(s, x, 'k1', 1);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  //  CHK-18 (۲-۲۱): همان افزودن از سقفِ شمارهٔ x (نیمِ آزادِ او: (۲ − ۱) / ۲ ⇒ یک دانه) می‌گذرد ⇒ ثبت، ولی ادعای تازه بی
  //  نگه‌داشتن و نگه‌داشتهٔ پیشینِ x همان یک دانه — پس کمبودی هم ساخته نمی‌شود (پیش‌تر x به «بررسی» می‌رفت)
  assert.deepEqual(await heldRows(s, x.id), [[1, 1000]], 'افزودهٔ بیش از سقف بی نگه‌داشتن');
  const line = await one('SELECT qty_milli FROM online_order_lines WHERE shop_id=$1 AND order_id=$2 AND line_no=1', [s.shopId, x.id]);
  assert.equal(Number(line.qty_milli), 2000, 'قلم بزرگ شد');
  assert.equal((await review(s, y.id)).review, false, 'نگه‌داشتهٔ پیش‌ترِ y دست‌نخورده');
  assert.equal((await review(s, x.id)).review, false, 'نگه‌داشته‌ها از موجودی بیشتر نشدند');
});

test('SR42-1: کوچک شدن و همان مقدار جای صف را نگه می‌دارند (فقط بزرگ شدن یا کالای دیگر ادعای تازه است)', async () => {
  const s = await shop('بازبینی۴۲-کوچک');
  for (const k of ['shop.orders', 'shop.catalog', 'shop.stock_reserve', 'shop.orders.amend']) await live(s.shopId, k, true);
  await push(s, [product('c1'), entry('c1-e', 'c1', 3)]);
  await refresh(s);
  const ln = (q) => ({ productId: 'c1', name: 'کالای c1', unit: 'دانه', qtyMilli: q, unitPriceMinor: 5000 });
  const mk = async (q) => {
    const r = await h.post('/api/shop/orders', { source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'م' }, lines: [ln(q)] },
      { token: s.token, headers: { 'Idempotency-Key': fresh() } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body.order.id;
  };
  const a = await mk(2000);
  const b = await mk(1000);
  const placeA = Number((await one(`SELECT created_at FROM stock_reservations WHERE order_id=$1 AND status='held'`, [a])).created_at);
  const r = await h.post(`/api/shop/orders/${a}/amendments`, {
    changes: [{ lineNo: 1, qtyMilli: 1000 }], reason: 'customer_request', consent: { via: 'phone' },
  }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const after = await one(`SELECT created_at, qty_milli FROM stock_reservations WHERE order_id=$1 AND status='held'`, [a]);
  assert.deepEqual([Number(after.qty_milli), Number(after.created_at)], [1000, placeA], 'کوچک‌شده همان جا');
  //  دو عدد فروخته شد ⇒ یک قابل‌فروش: a (پیش‌تر، همان جا) پوشش دارد، b در بررسی
  await push(s, sale('c1-s', [['c1-i', 'c1', 2]]));
  await refresh(s);
  assert.deepEqual([(await review(s, a)).review, (await review(s, b)).review], [false, true]);
});

// ---------------------------------------------------------------- R6

const pubList = async (s, pid) => (await h.get(`/api/store/${s.slug}/products`)).body.items.find((p) => p.id === pid).stock.status;
const pubDetail = async (s, pid) => (await h.get(`/api/store/${s.slug}/products/${pid}`)).body.product.stock.status;
const staffStock = async (s, pid) => {
  const one1 = (await h.get(`/api/shop/catalog/products/${pid}`, { token: s.token })).body.product.stock;
  const listed = (await h.get('/api/shop/catalog/products', { token: s.token })).body.items.find((p) => p.id === pid).stock;
  assert.deepEqual(listed, one1, 'فهرست و جزئیاتِ کاتالوگ یکی');
  return [one1.reservedMilli, one1.sellableMilli, one1.badge.status];
};
const holdOf = async (orderId) => (await query(
  `SELECT status, expires_at, short_at FROM stock_reservations WHERE order_id=$1 ORDER BY created_at, id`, [orderId]
)).rows.map((r) => ({ status: r.status, expiresAt: r.expires_at === null ? null : Number(r.expires_at), short: r.short_at !== null }));
const cards = async (s) => (await h.get('/api/shop/orders?tab=new', { token: s.token })).body.items;
const shortTasks = async (s) => (await h.get('/api/shop/catalog/tasks?status=open', { token: s.token })).body.tasks
  .filter((t) => t.kind === 'reservation_short');

test('R6: `shop.stock_reserve` خاموش ⇒ نگه‌داشتهٔ باز هیچ‌جا اثر ندارد (ویترین، کاتالوگ، ثبت، افزودن، قبول)؛ روشنِ دوباره ⇒ نگذشته می‌شمارد، گذشته نه', async () => {
  const s = await storeShop('بازبینی۴۲-خاموش', 'o1', 1);
  await push(s, [product('o2'), entry('o2-e', 'o2', 1)]);
  await refresh(s);
  assert.equal((await h.put('/api/shop/storefront/products/o2', { published: true }, { token: s.token })).status, 200);
  const x = await customerOrder(s, '0791110011', 'o1', 1);   //  تنها o1 را نگه داشت (با مهلت)
  const [h0] = await holdOf(x.id);
  assert.equal(h0.status, 'held');
  assert.ok(h0.expiresAt > Date.now());
  assert.equal(await pubList(s, 'o1'), 'out_of_stock', 'روشن: نگه‌داشته گرفته است');

  await live(s.shopId, 'shop.stock_reserve', false);
  //  ویترین (فهرست و برگهٔ کالا) و کاتالوگِ کارمند: همان «امروزِ main» — نگه‌داشته شمرده نمی‌شود
  assert.equal(await pubList(s, 'o1'), 'in_stock', 'خاموش: فهرستِ ویترین موجود');
  assert.equal(await pubDetail(s, 'o1'), 'in_stock', 'خاموش: برگهٔ کالا موجود');
  assert.deepEqual(await staffStock(s, 'o1'), [0, 1000, 'in_stock'], 'خاموش: کاتالوگِ کارمند بی نگه‌داشته');
  assert.equal(await review(s, x.id), undefined, 'خاموش: جزئیاتِ سفارش بی stockHold');
  //  ثبتِ مشتریِ دیگر همان آخرین عدد ⇒ ۲۰۱ (بی نگه‌داشته)
  const y = await customerOrder(s, '0791110012', 'o1', 1);
  assert.deepEqual(await holdOf(y.id), []);
  //  افزودن ⇒ نگه‌داشتهٔ تازه نه (قلمِ ۲ بی ردیف)
  const a = await addItems(s, x, 'o2', 1);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.deepEqual(await heldRows(s, x.id), [[1, 1000]], 'خاموش: افزودن نگه‌داشته نمی‌سازد');
  //  قبول ⇒ مهلت همان (جارو با زمانِ خودش می‌بندد)، نه بی‌مهلت
  for (const id of [x.id, y.id]) assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: s.token })).status, 200);
  assert.deepEqual(await holdOf(x.id), [h0], 'خاموش: قبول نگه‌داشته را بی‌مهلت نمی‌کند');
  assert.ok((await cards(s)).every((c) => c.stockReview === undefined));

  //  روشنِ دوباره: نگه‌داشتهٔ نگذشته دوباره می‌شمارد (ردیف در جدول ماند)
  await live(s.shopId, 'shop.stock_reserve', true);
  assert.equal(await pubList(s, 'o1'), 'out_of_stock');
  assert.deepEqual(await staffStock(s, 'o1'), [1000, 0, 'out_of_stock']);
  assert.equal((await review(s, x.id)).status, 'held');
  //  گذشت ⇒ دیگر نمی‌شمارد و جارو می‌بندد؛ دفتر = نگه‌داشته‌ها
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE order_id=$1 AND status='held'`, [x.id, Date.now() - 1]);
  assert.equal(await pubList(s, 'o1'), 'in_stock');
  assert.deepEqual(await staffStock(s, 'o1'), [0, 1000, 'in_stock']);
  assert.equal((await review(s, x.id)).status, 'expired');
  assert.ok((await lib().sweep()) >= 1);
  assert.deepEqual((await holdOf(x.id)).map((r) => r.status), ['expired']);
  const rep = await h.get('/api/shop/catalog/report', { token: s.token });
  assert.equal(rep.body.ok, true, JSON.stringify(rep.body.mismatches));
});

test('R6: خاموش ⇒ فروشِ حضوری وظیفه/نشانِ بررسی نمی‌سازد و جارو با مهلتِ خودش می‌بندد؛ روشنِ دوباره ⇒ کمبودِ ثبت‌شده درست', async () => {
  const s = await storeShop('بازبینی۴۲-خاموش-صندوق', 'p1', 1);
  const x = await customerOrder(s, '0791110013', 'p1', 1);
  await live(s.shopId, 'shop.stock_reserve', false);
  await push(s, sale('p1-s', [['p1-i', 'p1', 1]]));   //  INV-19: هرگز رد نمی‌شود
  await refresh(s);
  assert.deepEqual(await shortTasks(s), [], 'خاموش: وظیفهٔ «کمبودِ نگه‌داشته» نه');
  assert.ok((await cards(s)).every((c) => c.stockReview === undefined), 'خاموش: کارتِ سفارش بی «در بررسی»');
  assert.equal(await review(s, x.id), undefined);
  //  روشنِ دوباره پیش از گذشت ⇒ همان کمبودِ واقعی (فروشِ حضوری عدد را برد)
  await live(s.shopId, 'shop.stock_reserve', true);
  assert.equal((await review(s, x.id)).review, true, 'روشن: سفارش در بررسی');
  //  دوباره خاموش و گذشت ⇒ جارو مثلِ همیشه می‌بندد
  await live(s.shopId, 'shop.stock_reserve', false);
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE order_id=$1 AND status='held'`, [x.id, Date.now() - 1]);
  assert.ok((await lib().sweep()) >= 1);
  assert.deepEqual((await holdOf(x.id)).map((r) => r.status), ['expired']);
  await live(s.shopId, 'shop.stock_reserve', true);
  assert.equal((await review(s, x.id)).status, 'expired');
  const rep = await h.get('/api/shop/catalog/report', { token: s.token });
  assert.equal(rep.body.ok, true, JSON.stringify(rep.body.mismatches));
});

test('R6: خاموش ⇒ تحویل مهلت را فقط کوتاه می‌کند — بی‌مهلتِ قبول‌شده سرانجام می‌بندد، مهلت‌دار دراز نمی‌شود', async () => {
  const s = await shop('بازبینی۴۲-خاموش-تحویل');
  for (const k of ['shop.orders', 'shop.catalog', 'shop.stock_reserve']) await live(s.shopId, k, true);
  await push(s, [product('t1'), entry('t1-e', 't1', 2)]);
  await refresh(s);
  const mk = async () => {
    const r = await h.post('/api/shop/orders', {
      source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'م' },
      lines: [{ productId: 't1', name: 'کالای t1', unit: 'دانه', qtyMilli: 1000, unitPriceMinor: 5000 }],
    }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body.order.id;
  };
  const go = async (id, to) => assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token })).status, 200, to);
  const a = await mk();
  const b = await mk();
  await go(a, 'accepted');   //  روشن ⇒ بی‌مهلت
  assert.deepEqual((await holdOf(a)).map((r) => r.expiresAt), [null]);
  const [hb] = await holdOf(b);
  await live(s.shopId, 'shop.stock_reserve', false);
  const t0 = Date.now();
  for (const id of [a, b]) {
    for (const to of [...(id === b ? ['accepted'] : []), 'picking', 'packed', 'ready_for_pickup', 'picked_up']) await go(id, to);
  }
  const [ha] = await holdOf(a);
  assert.equal(ha.status, 'held');
  assert.ok(ha.expiresAt >= t0 + lib().HANDOVER_GRACE_MS && ha.expiresAt <= Date.now() + lib().HANDOVER_GRACE_MS, 'بی‌مهلت ⇒ مهلتِ پشتیبان');
  assert.deepEqual(await holdOf(b), [hb], 'مهلت‌دار دراز نشد');
});

// ---------------------------------------------------------------- SR42-2

test('SR42-2 (PostgreSQL): گذارِ کارمند و فروشِ حضوریِ هم‌زمانِ همان سفارش بن‌بست نمی‌سازند — هر دو انجام، کمبود ثبت', { skip: db.isPglite() }, async () => {
  const s = await shop('بازبینی۴۲-بن‌بست');
  for (const k of ['shop.orders', 'shop.catalog', 'shop.stock_reserve', 'shop.order_notify']) await live(s.shopId, k, true);
  await push(s, [product('d1'), entry('d1-e', 'd1', 2)]);
  await refresh(s);
  const mk = async () => {
    const r = await h.post('/api/shop/orders', {
      source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'م' },
      lines: [{ productId: 'd1', name: 'کالای d1', unit: 'دانه', qtyMilli: 1000, unitPriceMinor: 5000 }],
    }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body.order.id;
  };
  await mk();
  const b = await mk();   //  تازه‌ترین ⇒ همان که با فروشِ یک عدد کم می‌آورد

  const stock = require('../src/lib/stock');
  const orders = require('../src/lib/orders');
  let letGo;
  const gate = new Promise((r) => { letGo = r; });
  let locked;
  const holding = new Promise((r) => { locked = r; });
  //  ب) نگه‌دارندهٔ قفلِ موجودی — همان کارِ `checkProducts` پس از فروشِ حضوری (یک عدد کم ⇒ b تازه کم می‌آورد ⇒ خبرِ بررسی)
  const sold = tx(async (c) => {
    await stock.lock(c, s.shopId);
    locked();
    await gate;
    const r = await c.query(
      `UPDATE stock_levels SET on_hand_milli = on_hand_milli - 1000 WHERE shop_id=$1 AND product_id='d1' AND on_hand_milli >= 1000 RETURNING 1`, [s.shopId]);
    assert.equal(r.rowCount, 1);
    await lib().afterMovesIn(c, s.shopId, ['d1'], await stock.settingsOf(s.shopId, c), now());
  });
  await holding;
  //  الف) کارمند همان سفارش را قبول می‌کند: ردیف `FOR UPDATE`، سپس قفلِ موجودی (منتظرِ ب)
  const accepted = orders.transition(s.shopId, b, { to: 'accepted' }, { type: 'user', id: 'sr42' });
  const t0 = Date.now();
  for (;;) {
    const w = await one(`SELECT COUNT(*)::int AS n FROM pg_locks WHERE locktype='advisory' AND NOT granted`);
    if (w.n > 0) break;
    assert.ok(Date.now() - t0 < 5000, 'گذار پشتِ قفلِ موجودی نرسید');
    await new Promise((r) => setTimeout(r, 20));
  }
  letGo();
  const [acc, sale1] = await Promise.allSettled([accepted, sold]);
  assert.equal(sale1.status, 'fulfilled', String(sale1.reason && sale1.reason.message));
  assert.equal(acc.status, 'fulfilled', `گذار: ${acc.reason && `${acc.reason.code} ${acc.reason.message}`}`);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [b])).status, 'accepted');
  //  کمبود هم ثبت شد (نشانِ «در بررسی» و وظیفه)؛ فقط پوشِ همان سفارشی که همین لحظه در دستِ کارمند بود کنار ماند
  assert.equal((await review(s, b)).review, true);
});
