'use strict';
/**
 * ORD-20 — سفارشِ آنلاینِ تحویل‌شده ⇒ فاکتورِ سریِ W در دفترِ خودِ دکان
 * (v3 §۳۸.۲، §۲۲.۲ مرحلهٔ A بندِ ۶). روی PGlite، از راهِ HTTP ِ واقعی.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش (`shop.orders.ledger`) ⇒ هیچ سند، هیچ op، هیچ ردیف.
 *   ۲) سفارش ← تحویل ⇒ دستگاه با همان pull/snapshot ِ Sync v1 یک فاکتورِ W
 *      می‌گیرد، به همان شکلی که خودش فروش می‌نویسد؛ موجودیِ دستگاه یک بار کم
 *      می‌شود و فروشنده دوباره فاکتور نمی‌زند.
 *   ۳) تکرار (گذارِ دوباره، قلّابِ دوباره) هیچ چیزِ تازه‌ای نمی‌نویسد؛ سفارشِ
 *      بعدی W-2 می‌گیرد.
 *   ۴) بازپرداختِ کامل ⇒ سندِ برگشت، نه ویرایش؛ مرجوعیِ پیشینِ دستگاه دو بار
 *      برنمی‌گردد.
 *   ۵) زنجیرهٔ هشِ سری سالم است و ردیفِ سند ویرایش‌پذیر نیست.
 *   ۶) لایهٔ موجودیِ سرور (#263): رزروِ کسرِ قطعی‌شده ⇒ سهمِ قلمِ W از پیش ثبت
 *      می‌شود تا دفترِ دستگاه آن را دوباره کم نکند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now, tx } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const ledger = require('../src/lib/order-ledger');
const chain = require('../src/lib/hash-chain-store');

const DEV_A = 'dev-ledger-a';   // دستگاهِ صندوق که کالا و ورودِ انبار را نوشته
const DEV_B = 'dev-ledger-b';   // دستگاهِ دوم که فقط می‌گیرد
let admin = '';
let opSeq = 0;
const opId = () => `01L${String(Date.now()).slice(-9)}${String(++opSeq).padStart(5, '0')}`;

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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکان + صاحب با توکنِ دستگاه، پرچمِ سفارش روشن، یک کالا و ۱۰ کیلو ورودِ انبار از دستگاهِ A */
async function shopWithStock(name, { ledgerOn = true } = {}) {
  let u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  u = { ...u, ...(await h.api('POST', '/api/auth/login', {
    body: { identifier: u.email, password: u.password, app: 'shop', device: { deviceId: DEV_A, name: 'A', platform: 'test' } },
  })).body };
  await setFlag(shopId, 'shop.orders', true);
  if (ledgerOn) await setFlag(shopId, ledger.FLAG_KEY, true);
  const ops = [
    { op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', saleUnit: '', unitFactor: 1, salePrice: 120, purchasePrice: 90, active: true } },
    { op_id: opId(), ts: Date.now(), table: 'warehouseEntries', row_id: 'we-1', type: 'insert',
      fields: { productId: 'p-rice', units: 10, cartons: 0, price: 90, date: '2026-10-18', createdAt: Date.now() } },
  ];
  const r = await push(u.accessToken, DEV_A, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));
  return { ...u, token: u.accessToken, shopId };
}

const push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: device, schema_version: 3, ops }, headers: { 'X-App': 'shop', 'X-Device': device },
});

/** دستگاه همه چیز را از ابتدا pull می‌کند و دفترش را مثلِ `LedgerDiff.applyRemote` می‌سازد */
async function devicePull(token, device) {
  const tables = {};
  const ops = [];
  let since = 0;
  for (;;) {
    const r = await h.api('GET', `/api/sync/v1/pull?device_id=${device}&since=${since}`, { token, headers: { 'X-App': 'shop' } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    for (const op of r.body.ops) {
      ops.push(op);
      const list = (tables[op.table] ||= new Map());
      if (op.type === 'delete') { list.delete(op.row_id); continue; }
      const cur = { ...(list.get(op.row_id) || {}) };
      for (const [f, v] of Object.entries(op.fields || {})) {
        cur[f] = v && typeof v === 'object' && '$inc' in v ? (Number(cur[f]) || 0) + v.$inc : v;
      }
      list.set(op.row_id, cur);
    }
    since = r.body.cursor;
    if (!r.body.has_more) break;
  }
  const rows = (t) => [...(tables[t] || new Map()).entries()].map(([id, d]) => ({ id, ...d }));
  return { ops, rows };
}

/** موجودیِ دستگاه — همان `ShopStore.StockIndex`: ورودِ انبار − Σ(فروش − برگشت) ِ فاکتورهای لغونشده */
function deviceStock(view, productId) {
  const entered = view.rows('warehouseEntries').filter((e) => e.productId === productId)
    .reduce((s, e) => s + Number(e.units || 0), 0);
  const cancelled = new Set(view.rows('sales').filter((s) => s.status === 'cancelled').map((s) => s.id));
  const sold = view.rows('saleItems').filter((i) => i.productId === productId && !cancelled.has(i.saleId))
    .reduce((s, i) => s + Number(i.quantity || 0) - Number(i.returnedQty || 0), 0);
  return Math.round((entered - sold) * 1000) / 1000;
}

let keySeq = 0;
async function newOrder(owner, over = {}) {
  const body = {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    lines: [
      { productId: 'p-rice', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 12000 },
      { name: 'کیسه', qtyMilli: 1000, unitPriceMinor: 500 },
    ],
    discountMinor: 1000,
    ...over,
  };
  const r = await h.post('/api/shop/orders', body, {
    token: owner.token, headers: { 'Idempotency-Key': `ledger-order-${++keySeq}-${Date.now()}` },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

async function walk(owner, id, steps) {
  for (const to of steps) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: owner.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}
const TO_PICKED_UP = ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'];

const serverOps = (shopId) => one(
  `SELECT COUNT(*)::int AS n FROM oplog WHERE app='shop' AND account_id=$1 AND device_id='server'`, [shopId]
);

/* ══════════════════════════════════════════════════════════════════ */

test('پرچمِ خاموش: سفارشِ تحویل‌شده هیچ سند و هیچ opی در دفترِ دستگاه نمی‌سازد', async () => {
  const a = await shopWithStock('خاموش', { ledgerOn: false });
  const order = await newOrder(a);
  await walk(a, order.id, TO_PICKED_UP);

  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_ledger_docs WHERE shop_id=$1', [a.shopId])).n, 0);
  assert.equal((await serverOps(a.shopId)).n, 0);
  const view = await devicePull(a.token, DEV_B);
  assert.equal(view.rows('sales').length, 0);
  assert.equal(deviceStock(view, 'p-rice'), 10, 'موجودیِ دستگاه دست نخورده');
  assert.equal((await one(
    "SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain=$1", [ledger.chainOf(a.shopId)]
  )).n, 0);
});

test('سفارش ← تحویل ⇒ دستگاه فاکتورِ W را با pull می‌گیرد؛ موجودی یک بار کم می‌شود', async () => {
  const a = await shopWithStock('تحویل');
  const order = await newOrder(a);
  //  پیش از تحویل هیچ
  await walk(a, order.id, ['accepted', 'picking', 'packed', 'ready_for_pickup']);
  assert.equal((await serverOps(a.shopId)).n, 0);
  await walk(a, order.id, ['picked_up']);

  const docs = await ledger.docsOf({ query }, order.id);
  assert.equal(docs.length, 1);
  const doc = docs[0];
  assert.equal(doc.kind, 'sale');
  assert.equal(doc.label, 'W-1');
  assert.equal(doc.onStatus, 'picked_up');
  assert.equal(doc.totalMinor, order.totalMinor);

  const view = await devicePull(a.token, DEV_B);
  const sales = view.rows('sales');
  assert.equal(sales.length, 1);
  const sale = sales[0];
  assert.equal(sale.id, `w-${order.id}`);
  //  همان شکلِ `Sale` ِ دستگاه (SalesEngine.record) + سری و کانال
  for (const f of ['invoiceNumber', 'total', 'discountType', 'discountValue', 'discount', 'finalTotal',
    'paymentMethod', 'debtorId', 'paidAmount', 'remaining', 'status', 'debtGiven', 'debtSettled',
    'createdAt', 'date', 'cancelReason', 'seller', 'series']) {
    assert.ok(Object.prototype.hasOwnProperty.call(sale, f), `فیلدِ ${f}`);
  }
  assert.equal(sale.series, 'W');
  assert.equal(sale.invoiceNumber, 1);
  assert.equal(sale.channel, 'online');
  assert.equal(sale.status, 'completed');
  assert.equal(sale.paymentMethod, 'cash');
  assert.equal(sale.finalTotal, order.totalMinor / 100);
  assert.equal(sale.total - sale.discount, sale.finalTotal);
  assert.match(sale.date, /^\d{4}-\d{2}-\d{2}$/);

  const items = view.rows('saleItems').filter((i) => i.saleId === sale.id);
  assert.equal(items.length, 2);
  const rice = items.find((i) => i.productId === 'p-rice');
  assert.equal(rice.quantity, 1.5);
  assert.equal(rice.totalPrice, 180);
  assert.equal(rice.purchasePrice, 90, 'بهای تمام‌شده از کالای دفترِ دستگاه');
  assert.equal(rice.returnedQty, 0);
  const bag = items.find((i) => i.productId === '');
  assert.equal(bag.name, 'کیسه');
  assert.equal(items.reduce((s, i) => s + i.totalPrice, 0), sale.total);

  const moves = view.rows('stockMovements').filter((m) => m.refId === sale.id);
  assert.deepEqual(moves.map((m) => [m.productId, m.type, m.qty]), [['p-rice', 'sale', -1.5]]);
  assert.equal(view.rows('auditLog').filter((x) => x.refId === sale.id && x.type === 'sale').length, 1);

  assert.equal(deviceStock(view, 'p-rice'), 8.5, 'موجودیِ دستگاه فروشِ اینترنتی را می‌شمارد');
  //  op از «سرور» است، نه از دستگاهی که فاکتور می‌زند — فروشنده چیزی ثبت نکرده
  assert.ok(view.ops.filter((o) => o.table === 'sales').every((o) => o.device_id === 'server'));
  assert.equal((await one(
    "SELECT COUNT(*)::int AS n FROM sync_devices WHERE app='shop' AND account_id=$1 AND device_id='server'", [a.shopId]
  )).n, 0, 'سرور دستگاهِ همگام‌سازی نیست');

  //  دستگاهِ نو با snapshot همان را می‌گیرد
  const snap = await h.api('GET', '/api/sync/v1/snapshot', { token: a.token, headers: { 'X-App': 'shop' } });
  assert.equal(snap.status, 200);
  assert.equal(snap.body.tables.sales.length, 1);
  assert.equal(snap.body.tables.sales[0].data.series, 'W');

  //  زنجیرهٔ هشِ سری
  const v = await chain.verifyChain(ledger.chainOf(a.shopId));
  assert.equal(v.ok, true, JSON.stringify(v));
  assert.equal(v.length, 1);
});

test('تکرار: گذار و قلّابِ دوباره چیزی نمی‌نویسند؛ سفارشِ بعدی W-2 می‌گیرد', async () => {
  const a = await shopWithStock('تکرار');
  const order = await newOrder(a);
  await walk(a, order.id, TO_PICKED_UP);
  const before = (await serverOps(a.shopId)).n;
  assert.ok(before > 0);

  //  دو بار کلیک
  const again = await h.post(`/api/shop/orders/${order.id}/transition`, { to: 'picked_up' }, { token: a.token });
  assert.equal(again.status, 200);
  assert.equal(again.body.unchanged, true);
  //  قلّاب مستقیم، دو بار (مثلاً مسیرِ دیگری که همان گذار را دوباره گزارش کند)
  for (let i = 0; i < 2; i++) {
    const out = await tx((c) => ledger.onOrderStatus(c, a.shopId, { id: order.id, to: 'picked_up' }, { type: 'system' }, now()));
    assert.equal(out.replayed, true);
    assert.equal(out.doc.label, 'W-1');
  }
  assert.equal((await serverOps(a.shopId)).n, before);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_ledger_docs WHERE order_id=$1', [order.id])).n, 1);

  const second = await newOrder(a, { lines: [{ productId: 'p-rice', name: 'برنج', unit: 'کیلو', qtyMilli: 2000, unitPriceMinor: 12000 }], discountMinor: 0 });
  await walk(a, second.id, TO_PICKED_UP);
  const docs = await ledger.docsOf({ query }, second.id);
  assert.equal(docs[0].label, 'W-2');

  const view = await devicePull(a.token, DEV_B);
  assert.equal(view.rows('sales').length, 2);
  assert.deepEqual(view.rows('sales').map((s) => `${s.series}-${s.invoiceNumber}`).sort(), ['W-1', 'W-2']);
  assert.equal(deviceStock(view, 'p-rice'), 6.5);

  //  ردیفِ سند ویرایش‌پذیر نیست
  await assert.rejects(query('UPDATE order_ledger_docs SET total_minor=0 WHERE order_id=$1', [order.id]));
  assert.equal((await chain.verifyChain(ledger.chainOf(a.shopId))).length, 2);
});

test('بازپرداختِ کامل ⇒ سندِ برگشت؛ فاکتور ویرایش نمی‌شود و موجودی برمی‌گردد', async () => {
  const a = await shopWithStock('برگشت');
  const order = await newOrder(a);
  await walk(a, order.id, TO_PICKED_UP);
  const saleRow = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='sales' AND row_id=$2`,
    [a.shopId, `w-${order.id}`]
  );
  await walk(a, order.id, ['return_requested']);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM order_ledger_docs WHERE order_id=$1 AND kind='return'", [order.id])).n, 0,
    'درخواستِ برگشت هنوز برگشت نیست');
  await walk(a, order.id, ['refunded_full']);

  const docs = await ledger.docsOf({ query }, order.id);
  assert.deepEqual(docs.map((d) => d.kind).sort(), ['return', 'sale']);
  const ret = docs.find((d) => d.kind === 'return');
  assert.equal(ret.totalMinor, order.totalMinor, 'جمعِ برگشت = مبلغِ پرداخت‌شده');

  const view = await devicePull(a.token, DEV_B);
  const sale = view.rows('sales')[0];
  assert.equal(sale.status, 'completed', 'فاکتور لغو نمی‌شود');
  const after = await one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='sales' AND row_id=$2`,
    [a.shopId, `w-${order.id}`]
  );
  assert.deepEqual(after.data, saleRow.data, 'ردیفِ فاکتور دست نخورده');
  const returns = view.rows('returns').filter((r) => r.saleId === sale.id);
  assert.equal(returns.length, 2);
  assert.equal(Math.round(returns.reduce((s, r) => s + r.amount, 0) * 100), order.totalMinor);
  assert.ok(view.rows('saleItems').filter((i) => i.saleId === sale.id).every((i) => i.returnedQty === i.quantity));
  assert.deepEqual(view.rows('stockMovements').filter((m) => m.type === 'customer_return').map((m) => [m.productId, m.qty]),
    [['p-rice', 1.5]]);
  assert.equal(deviceStock(view, 'p-rice'), 10);
  assert.equal(view.rows('auditLog').filter((x) => x.refId === sale.id && x.type === 'return').length, 1);

  //  تکرارِ قلّابِ برگشت
  const n = (await serverOps(a.shopId)).n;
  const out = await tx((c) => ledger.onOrderStatus(c, a.shopId, { id: order.id, to: 'refunded_full' }, { type: 'system' }, now()));
  assert.equal(out.replayed, true);
  assert.equal((await serverOps(a.shopId)).n, n);
  assert.equal((await chain.verifyChain(ledger.chainOf(a.shopId))).length, 2);
});

test('مرجوعیِ پیشینِ دستگاه دو بار برنمی‌گردد؛ پرچمِ خاموش‌شده برگشت را نمی‌بندد', async () => {
  const a = await shopWithStock('مرجوعیِ دستگاه');
  const order = await newOrder(a, { discountMinor: 0 });
  await walk(a, order.id, TO_PICKED_UP);
  const itemId = `w-${order.id}-1`;
  //  دستگاه نیم کیلو را خودش برگرداند (همان شکلِ `SalesEngine.recordReturn`)
  const r = await push(a.token, DEV_A, [
    { op_id: opId(), ts: Date.now(), table: 'returns', row_id: 'dev-ret-1', type: 'insert',
      fields: { saleId: `w-${order.id}`, saleItemId: itemId, productId: 'p-rice', quantity: 0.5, amount: 60,
        reason: '', date: '2026-10-18', createdAt: Date.now() } },
    { op_id: opId(), ts: Date.now(), table: 'saleItems', row_id: itemId, type: 'update', fields: { returnedQty: { $inc: 0.5 } } },
  ]);
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body));

  await setFlag(a.shopId, ledger.FLAG_KEY, false);
  await walk(a, order.id, ['return_requested', 'refunded_full']);
  const ret = (await ledger.docsOf({ query }, order.id)).find((d) => d.kind === 'return');
  assert.ok(ret, 'سندِ برگشت حتی با پرچمِ خاموش‌شده');
  const riceLine = ret.lines.find((l) => l.productId === 'p-rice');
  assert.equal(riceLine.qtyMilli, 1000, 'فقط ماندهٔ برنگشته');
  assert.equal(ret.totalMinor + 6000, order.totalMinor, 'جمعِ دو برگشت = مبلغِ پرداخت‌شده');

  const view = await devicePull(a.token, DEV_B);
  const rice = view.rows('saleItems').find((i) => i.id === itemId);
  assert.equal(rice.returnedQty, 1.5);
  assert.equal(deviceStock(view, 'p-rice'), 10);
});

test('دفترِ بازپرداخت (#255، shop.orders.refunds): بازپرداختِ کامل با مبلغ ⇒ همان سندِ برگشت؛ تکرارِ درخواست ⇒ هیچ', async () => {
  const a = await shopWithStock('دفترِ بازپرداخت');
  await setFlag(a.shopId, 'shop.orders.refunds', true);
  const order = await newOrder(a);
  await walk(a, order.id, [...TO_PICKED_UP, 'return_requested']);
  const key = `ledger-refund-${Date.now()}`;
  const refund = () => h.post(`/api/shop/orders/${order.id}/refunds`,
    { amountMinor: order.totalMinor, reason: 'کالا خراب بود' },
    { token: a.token, headers: { 'Idempotency-Key': key } });
  const r = await refund();
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [order.id])).status, 'refunded_full');

  const ret = (await ledger.docsOf({ query }, order.id)).find((d) => d.kind === 'return');
  assert.ok(ret, 'سندِ برگشت از مسیرِ دفترِ بازپرداخت هم ساخته می‌شود');
  assert.equal(ret.onStatus, 'refunded_full');
  assert.equal(ret.totalMinor, order.totalMinor);
  const n = (await serverOps(a.shopId)).n;
  const again = await refund();
  assert.ok([200, 201].includes(again.status), JSON.stringify(again.body));
  assert.equal((await serverOps(a.shopId)).n, n);
  assert.equal(deviceStock(await devicePull(a.token, DEV_B), 'p-rice'), 10);
});

test('سفارشِ آزمایشی سند نمی‌سازد', async () => {
  const a = await shopWithStock('آزمایشی');
  const order = await newOrder(a);
  await query('UPDATE online_orders SET is_test=true WHERE id=$1', [order.id]);
  await walk(a, order.id, TO_PICKED_UP);
  assert.equal((await ledger.docsOf({ query }, order.id)).length, 0);
  assert.equal((await serverOps(a.shopId)).n, 0);
});

test('لایهٔ موجودیِ سرور (#263، shop.catalog): کسرِ قطعیِ رزرو + فاکتورِ W ⇒ موجودیِ سرور یک بار کم می‌شود؛ برگشت یک بار برمی‌گردد', async () => {
  const a = await shopWithStock('کاتالوگ');
  await setFlag(a.shopId, 'shop.catalog', true);
  const catalog = require('../src/lib/catalog');
  const onHand = async () => Number((await one(
    `SELECT COALESCE(SUM(on_hand_milli), 0) AS n FROM stock_levels WHERE shop_id=$1 AND product_id='p-rice'`, [a.shopId]
  )).n);
  const moves = (type) => many(
    `SELECT source, qty_milli FROM stock_moves WHERE shop_id=$1 AND product_id='p-rice' AND type=$2 ORDER BY created_at, id`,
    [a.shopId, type]
  );

  //  ساختِ سفارش ⇒ #263 دفترِ دستگاه را می‌خواند (۱۰ کیلو) و ۱٫۵ را رزرو می‌کند
  const order = await newOrder(a);
  assert.equal(await onHand(), 10000);
  await walk(a, order.id, TO_PICKED_UP);
  const committed = await many(
    `SELECT line_no, qty_milli FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='committed'`,
    [a.shopId, order.id]
  );
  assert.deepEqual(committed.map((r) => [Number(r.line_no), Number(r.qty_milli)]), [[1, 1500]], 'کسرِ قطعیِ #263');

  //  پیش‌ثبت = همان کلید و همان معنای `ingestTx`: si:<قلم> و posted_milli = want = −مقدار
  const pre = await many('SELECT source_key, product_id, posted_milli, rev FROM stock_sources WHERE shop_id=$1 AND source_key LIKE $2',
    [a.shopId, 'si:w-%']);
  assert.deepEqual(pre.map((r) => [r.source_key, r.product_id, Number(r.posted_milli), Number(r.rev)]),
    [[`si:w-${order.id}-1`, 'p-rice', -1500, 0]], 'قلمِ آزاد سهمی ندارد');

  //  خواندنِ دوباره و کاملِ دفترِ دستگاه (همان که دکمهٔ «تازه کردن» و هر سفارشِ بعدی می‌زند)
  for (let i = 0; i < 2; i++) await catalog.refresh(a.shopId, { full: true });
  assert.equal(await onHand(), 8500, 'یک بار کم شد، نه دو بار');
  assert.deepEqual((await moves('sale')).map((m) => [m.source, Number(m.qty_milli)]), [['order', 1500]],
    'فروشِ W از دفترِ دستگاه حرکتِ دومی نساخت');

  //  بازپرداختِ کامل ⇒ سندِ برگشتِ W ⇒ ingest یک بار برمی‌گرداند
  await walk(a, order.id, ['return_requested', 'refunded_full']);
  for (let i = 0; i < 2; i++) await catalog.refresh(a.shopId, { full: true });
  assert.equal(await onHand(), 10000);
  assert.deepEqual((await moves('customer_return')).map((m) => [m.source, Number(m.qty_milli)]), [['device', 1500]]);
  //  موجودیِ دستگاه هم همان
  assert.equal(deviceStock(await devicePull(a.token, DEV_B), 'p-rice'), 10);
});
