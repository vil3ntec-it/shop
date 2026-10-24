'use strict';
/**
 * سفارشِ آنلاین و دفترِ دکان — تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): «خریدِ اینترنتی موجودیِ
 * سرور را نه کم می‌کند نه رزرو؛ دکان کالا را از برنامهٔ خودش تحویل می‌دهد و همان‌جا فروشِ
 * عادی ثبت می‌شود». روی PGlite، از راهِ HTTP ِ واقعی.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ با همهٔ پرچم‌ها (`shop.orders`، `shop.orders.ledger` ِ بازنشسته، `shop.catalog`)
 *      سفارش تا «تحویل» و «بازپرداختِ کامل» هیچ op، هیچ فاکتورِ W، هیچ رزرو و هیچ
 *      حرکتِ موجودی‌ای نمی‌سازد؛ موجودیِ سرور، ویترین و دستگاه همان است.
 *   ۲) `GET /shop/orders/handover`: سفارش‌های تحویل‌شدهٔ بی‌فروش — فقط پس از تحویل، نه
 *      آزمایشی، نه پیش از `since`، و نه وقتی فروشِ `o-…` یا فاکتورِ W ِ قدیمی (`w-…`) هست.
 *   ۳) فروشِ برنامه با شناسهٔ ثابتِ سفارش از دو دستگاه (یا تلاشِ دوباره) ⇒ یک فروش و یک
 *      بار کسر، روی سرور، دستگاهِ سوم و کاتالوگ؛ ویترین همان خواندنِ بعدی عددِ تازه را دارد.
 *   ۴) سنجشِ فقط‌خواندنیِ ساختِ سفارش: بیش از موجودی ⇒ ۴۰۹ بی هیچ ردیف.
 */
//  ویترین در آزمون بی مهلتِ پرسش (پیش‌فرضِ تولید ۵۰۰ms؛ آزمونِ سرتاسری با پیش‌فرض)
process.env.CATALOG_REFRESH_THROTTLE_MS = '0';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now, tx } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const ledger = require('../src/lib/order-ledger');

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

/** دکان + صاحب با توکنِ دستگاه، پرچم‌های سفارش و کاتالوگ روشن، یک کالا و ۱۰ کیلو ورودِ انبار از دستگاهِ A */
async function shopWithStock(name, { catalogOn = true, claim = true, ledgerOn = true } = {}) {
  let u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  u = { ...u, ...(await h.api('POST', '/api/auth/login', {
    body: { identifier: u.email, password: u.password, app: 'shop', device: { deviceId: DEV_A, name: 'A', platform: 'test' } },
  })).body };
  await setFlag(shopId, 'shop.orders', true);
  //  پرچمِ بازنشستهٔ فاکتورِ W هم روشن: دیگر هیچ اثری ندارد
  if (ledgerOn) await setFlag(shopId, 'shop.orders.ledger', true);
  if (catalogOn) await setFlag(shopId, 'shop.catalog', true);
  const ops = [
    { op_id: opId(), ts: Date.now(), table: 'products', row_id: 'p-rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', saleUnit: '', unitFactor: 1, salePrice: 120, purchasePrice: 90, active: true } },
    { op_id: opId(), ts: Date.now(), table: 'warehouseEntries', row_id: 'we-1', type: 'insert',
      fields: { productId: 'p-rice', units: 10, cartons: 0, price: 90, date: '2026-10-18', createdAt: Date.now() } },
  ];
  const r = await push(u.accessToken, DEV_A, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));
  //  برنامهٔ «فروشِ تحویل» نخستین بار config را می‌پرسد ⇒ کفِ تحویلِ همین دکان (پیش از هر سفارش)
  if (claim) assert.ok((await appConfig(u.accessToken)).body.handoverSince > 0);
  return { ...u, token: u.accessToken, shopId };
}

/** همان `OrdersRepository.config` ِ برنامه: توانِ «فروشِ تحویل» را می‌گوید */
const appConfig = (token, caps = 'saleOnHandover') => h.get(`/api/shop/orders/config?caps=${caps}`, { token });

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

/**
 * همان فروشی که برنامه هنگامِ تحویل می‌سازد (`OrderFulfillment.kt`)، به شکلِ opهای Sync v1 —
 * شناسه‌ها از سفارش می‌آیند؛ پس هر دستگاه **همان** ردیف‌ها را می‌نویسد.
 */
function appSaleOps(order, { invoiceNumber = 1, series = 'A1' } = {}) {
  const saleId = ledger.saleIdOf(order.id);
  const at = Date.now();
  const ops = [{ op_id: opId(), ts: at, table: 'sales', row_id: saleId, type: 'insert', fields: {
    invoiceNumber, series, total: (order.subtotalMinor + order.deliveryFeeMinor) / 100, discountType: 'amount',
    discountValue: order.discountMinor / 100, discount: order.discountMinor / 100, finalTotal: order.totalMinor / 100,
    paymentMethod: 'cash', paidAmount: order.totalMinor / 100, remaining: 0, status: 'completed', debtGiven: 0,
    debtSettled: 0, createdAt: at, date: '2026-10-19', syncStatus: 'pending', cancelReason: '', seller: '',
    channel: 'online', orderId: order.id, orderNo: order.orderNo,
  } }];
  for (const l of order.lines) {
    const item = `${saleId}-${l.lineNo}`;
    ops.push({ op_id: opId(), ts: at, table: 'saleItems', row_id: item, type: 'insert', fields: {
      saleId, productId: l.productId, name: l.productId ? '' : l.name, quantity: l.qtyMilli / 1000,
      unitPrice: l.unitPriceMinor / 100, purchasePrice: 0, totalPrice: l.lineTotalMinor / 100, returnedQty: 0,
    } });
    if (l.productId) {
      ops.push({ op_id: opId(), ts: at, table: 'stockMovements', row_id: `${item}-m`, type: 'insert', fields: {
        productId: l.productId, type: 'sale', qty: -l.qtyMilli / 1000, date: '2026-10-19', notes: '', refId: saleId, createdAt: at,
      } });
    }
  }
  return ops;
}

const handover = (owner, since = 0) => h.get(`/api/shop/orders/handover?since=${since}`, { token: owner.token });
const levels = async (owner, pid = 'p-rice') => {
  const r = await h.get(`/api/shop/catalog/products/${pid}`, { token: owner.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock;
};
const count = async (sql, p) => Number((await one(sql, p)).n);
const order1 = (lines) => ({ source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد' }, lines });
const riceLine = (qtyMilli) => [{ productId: 'p-rice', name: 'برنج', unit: 'کیلو', qtyMilli, unitPriceMinor: 12000 }];

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ سفارش تا «تحویل» و «بازپرداختِ کامل» هیچ چیز در دفترِ دکان و موجودیِ سرور نمی‌نویسد (همهٔ پرچم‌ها روشن)', async () => {
  const a = await shopWithStock('بی‌اثر');
  assert.equal((await levels(a)).levels.shop_floor, 10000);
  const order = await newOrder(a);
  //  ساختِ سفارش: نه رزرو، نه «رزروشده»
  const placed = await levels(a);
  assert.equal(placed.reservedMilli, 0);
  assert.equal(placed.sellableMilli, 10000);
  await walk(a, order.id, TO_PICKED_UP);
  await walk(a, order.id, ['return_requested', 'refunded_full']);

  assert.equal((await serverOps(a.shopId)).n, 0, 'سرور op ِ دفترِ دکان نساخت');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM order_ledger_docs WHERE shop_id=$1', [a.shopId]), 0);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [a.shopId]), 0);
  assert.equal(await count("SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1 AND source <> 'device'", [a.shopId]), 0);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain=$1', [ledger.chainOf(a.shopId)]), 0);
  assert.equal((await levels(a)).levels.shop_floor, 10000, 'موجودیِ سرور دست نخورد');
  const pub = await require('../src/lib/catalog').publicStock(a.shopId, ['p-rice']);
  assert.equal(pub.get('p-rice').status, 'in_stock');
  const view = await devicePull(a.token, DEV_B);
  assert.equal(view.rows('sales').length, 0);
  assert.equal(deviceStock(view, 'p-rice'), 10, 'موجودیِ دستگاه دست نخورد');
});

test('handover: فقط سفارشِ تحویل‌شدهٔ بی‌فروش — نه پیش از تحویل، نه آزمایشی، نه پیش از since', async () => {
  const a = await shopWithStock('تحویل');
  const order = await newOrder(a);
  const trial = await newOrder(a);
  await query('UPDATE online_orders SET is_test=true WHERE id=$1', [trial.id]);
  await walk(a, order.id, ['accepted', 'picking', 'packed', 'ready_for_pickup']);
  assert.deepEqual((await handover(a)).body.items, [], 'پیش از تحویل هیچ');

  const t0 = Date.now();
  await walk(a, order.id, ['picked_up']);
  await walk(a, trial.id, TO_PICKED_UP);
  const r = await handover(a);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.items.map((x) => x.id), [order.id], 'سفارشِ آزمایشی هرگز');
  const it = r.body.items[0];
  assert.equal(it.saleId, `o-${order.id}`);
  assert.equal(it.status, 'picked_up');
  assert.ok(it.handedOverAt >= t0 - 5);
  assert.deepEqual(it.lines.map((l) => [l.lineNo, l.productId, l.qtyMilli, l.lineTotalMinor]),
    [[1, 'p-rice', 1500, 18000], [2, '', 1000, 500]]);
  assert.equal(it.totalMinor, order.totalMinor);
  //  since: تحویلِ پیش از به‌کارگیریِ قابلیت در برنامه دوباره فروخته نمی‌شود
  assert.deepEqual((await handover(a, Date.now() + 60_000)).body.items, []);
  //  بی ورود ⇒ ۴۰۱
  assert.equal((await h.get('/api/shop/orders/handover', {})).status, 401);
});

test('فروشِ برنامه با شناسهٔ ثابت از دو دستگاه و تلاشِ دوباره ⇒ یک فروش، یک بار کسر؛ ویترین همان خواندنِ بعدی', async () => {
  const a = await shopWithStock('دودستگاه');
  const catalog = require('../src/lib/catalog');
  const sett = await h.put('/api/shop/catalog/settings', { showQty: 'exact' }, { token: a.token });
  assert.equal(sett.status, 200, JSON.stringify(sett.body));
  const order = (await h.get(`/api/shop/orders/${(await newOrder(a)).id}`, { token: a.token })).body.order;
  await walk(a, order.id, TO_PICKED_UP);
  assert.equal((await catalog.publicStock(a.shopId, ['p-rice'])).get('p-rice').sellableMilli, 10000);

  //  دستگاهِ A و دستگاهِ B هر دو تحویل را ثبت می‌کنند (هم‌زمان)، A یک بار هم دوباره می‌فرستد
  const [ra, rb] = await Promise.all([
    push(a.token, DEV_A, appSaleOps(order, { invoiceNumber: 7, series: 'A1' })),
    push(a.token, DEV_B, appSaleOps(order, { invoiceNumber: 3, series: 'B1' })),
  ]);
  for (const r of [ra, rb]) {
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.ok(r.body.results.every((x) => x.status === 'applied' || x.status === 'duplicate'), JSON.stringify(r.body.results));
  }
  assert.equal((await push(a.token, DEV_A, appSaleOps(order, { invoiceNumber: 7, series: 'A1' }))).status, 200);

  const sales = await many(
    `SELECT row_id FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='sales' AND data->>'orderId'=$2`,
    [a.shopId, order.id]
  );
  assert.deepEqual(sales.map((r) => r.row_id), [`o-${order.id}`], 'یک فروش برای یک سفارش');
  assert.equal(await count(
    `SELECT COUNT(*)::int AS n FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='saleItems' AND data->>'saleId'=$2`,
    [a.shopId, `o-${order.id}`]
  ), 2);

  //  دستگاهِ سوم (تازه) و کاتالوگ: یک بار کم
  const view = await devicePull(a.token, 'dev-ledger-c');
  assert.equal(deviceStock(view, 'p-rice'), 8.5);
  const t0 = Date.now();
  const pub = (await catalog.publicStock(a.shopId, ['p-rice'])).get('p-rice');
  assert.equal(pub.sellableMilli, 8500, 'ویترین همان خواندنِ بعدی (بی مهلتِ ۵ ثانیه)');
  assert.ok(Date.now() - t0 < 5000);
  assert.equal((await levels(a)).levels.shop_floor, 8500);
  for (let i = 0; i < 2; i++) await catalog.refresh(a.shopId, { full: true });
  assert.equal((await levels(a)).levels.shop_floor, 8500, 'خواندنِ دوباره و کامل دو بار کم نمی‌کند');
  const rep = await h.get('/api/shop/catalog/report?productId=p-rice', { token: a.token });
  assert.equal(rep.body.ok, true, JSON.stringify(rep.body.mismatches));
  //  دیگر در فهرستِ تحویلِ بی‌فروش نیست
  assert.deepEqual((await handover(a)).body.items, []);
});

test('فاکتورِ W ِ قدیمی (`w-…`) همان سفارش را «فروخته» می‌شمارد ⇒ handover دوباره نمی‌خواهد', async () => {
  const a = await shopWithStock('قدیمی');
  const order = await newOrder(a);
  await walk(a, order.id, TO_PICKED_UP);
  assert.equal((await handover(a)).body.items.length, 1);
  const r = await push(a.token, DEV_A, [{ op_id: opId(), ts: Date.now(), table: 'sales', row_id: ledger.legacySaleIdOf(order.id),
    type: 'insert', fields: { series: 'W', channel: 'online', orderId: order.id, status: 'completed' } }]);
  assert.equal(r.status, 200);
  assert.deepEqual((await handover(a)).body.items, []);
});

test('سنجشِ فقط‌خواندنیِ ساختِ سفارش: بیش از موجودی ⇒ ۴۰۹ بی هیچ ردیف؛ دو سفارشِ «همهٔ موجودی» هر دو ثبت؛ کاتالوگِ خاموش ⇒ بی سنجش', async () => {
  const a = await shopWithStock('سنجش');
  let k = 0;
  const place = (owner, lines) => h.post('/api/shop/orders', order1(lines), {
    token: owner.token, headers: { 'Idempotency-Key': `ledger-chk-${++k}-${Date.now()}` },
  });
  const r = await place(a, riceLine(10500));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'out_of_stock');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [a.shopId]), 0);
  //  همهٔ ۱۰ کیلو، دو بار: هر دو پذیرفته (رزروی نیست؛ دکان یکی را با «ناموجود» رد می‌کند)
  for (let i = 0; i < 2; i++) assert.equal((await place(a, riceLine(10000))).status, 201);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [a.shopId]), 0);
  assert.equal((await levels(a)).sellableMilli, 10000);

  const off = await shopWithStock('بی‌کاتالوگ', { catalogOn: false });
  assert.equal((await place(off, riceLine(10500))).status, 201, 'بی پرچمِ کاتالوگ همان رفتارِ پیشین');
});

/* ══════════════════════ بازبینیِ #340: اجازه، کف، صفحه، برگشت، کسرِ قدیمی ══════════════════════ */

async function staffOf(a, name, role = 'staff') {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`, [newId('mem'), a.shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}
const tick = () => new Promise((r) => setTimeout(r, 5));

test('config: سرور «فروشِ تحویل» و کفش را می‌گوید؛ اجازهٔ ثبت همان سنجشِ Sync v1 است (شاگرد: بی تخفیف و بی لغو)', async () => {
  const a = await shopWithStock('اجازه');
  const own = (await h.get('/api/shop/orders/config', { token: a.token })).body;
  assert.equal(own.saleOnHandover, true);
  assert.equal(own.handoverSince, await ledger.floorOf(a.shopId), 'کفِ ذخیره‌شدهٔ همین دکان');
  assert.ok(own.handoverSince > 0 && own.handoverSince <= Date.now());
  assert.deepEqual([own.permissions.recordSale, own.permissions.saleDiscount, own.permissions.saleCancel], [true, true, true]);
  const s = await staffOf(a, 'شاگردِ اجازه');
  const st = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.deepEqual([st.permissions.recordSale, st.permissions.saleDiscount, st.permissions.saleCancel], [true, false, false]);
  assert.deepEqual(ledger.capsOf('nobody'), { recordSale: false, saleDiscount: false, saleCancel: false });

  //  چرا: Sync v1 فروشِ تخفیف‌دار و لغو را از شاگرد رد می‌کند ⇒ فروش فقط روی دستگاهِ او می‌ماند
  const order = await newOrder(a);
  const ops = appSaleOps(order).map((o) => (o.table === 'sales' ? { ...o, fields: { ...o.fields, discount: 10 } } : o));
  const r = await push(s.token, 'dev-ledger-staff', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const sale = r.body.results.find((x) => x.op_id === ops[0].op_id);
  assert.deepEqual([sale.status, sale.reason], ['rejected', 'discount_not_allowed']);
  const cancel = await push(s.token, 'dev-ledger-staff', [{ op_id: opId(), ts: Date.now(), table: 'sales',
    row_id: `o-${order.id}`, type: 'update', fields: { status: 'cancelled' } }]);
  assert.deepEqual([cancel.body.results[0].status, cancel.body.results[0].reason], ['rejected', 'cancel_not_allowed']);
});

test('handover: نخستین گذارِ تحویل باید پس از کف باشد — «ارسال» پیش از کف و «رسید» پس از آن ⇒ نه فروشِ خودکار، در review با دلیل', async () => {
  const a = await shopWithStock('در راه', { claim: false });
  const send = async () => {
    const r = await h.post('/api/shop/orders', { source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
      customer: { name: 'احمد', phone: '0791234567' }, address: { landmark: 'کنارِ مسجد' }, lines: riceLine(1000) },
    { token: a.token, headers: { 'Idempotency-Key': `ledger-fly-${++keySeq}-${Date.now()}` } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body.order;
  };
  const flying = await send();
  const old = await newOrder(a);
  await walk(a, flying.id, ['accepted', 'picking', 'packed', 'out_for_delivery']);
  await walk(a, old.id, TO_PICKED_UP);
  await tick();
  const floor = (await appConfig(a.token)).body.handoverSince;
  await tick();
  await walk(a, flying.id, ['delivered']);
  //  در راه هنگامِ کف: دستگاهِ کهنه شاید پیش‌تر دستی فروخته باشد ⇒ هرگز خودکار نه
  let r = (await handover(a)).body;
  assert.deepEqual(r.items, [], 'نخستین گذار پیش از کف ⇒ فروشِ خودکار نه');
  assert.deepEqual(r.review.map((x) => [x.id, x.action, x.reviewCode]), [[flying.id, 'review', 'before_floor']]);
  assert.ok(r.review[0].handedOverAt < floor);
  //  سفارشی که همه‌اش پیش از کف بود (بی گذارِ تحویلِ پس از کف) ⇒ نه فهرست، نه review
  assert.ok(!r.review.some((x) => x.id === old.id));
  const one1 = (await h.get(`/api/shop/orders/handover?since=0&orderId=${flying.id}`, { token: a.token })).body;
  assert.deepEqual([one1.items, one1.review.map((x) => x.id)], [[], [flying.id]]);
  const one2 = (await h.get(`/api/shop/orders/handover?since=0&orderId=${old.id}`, { token: a.token })).body;
  assert.deepEqual([one2.items, one2.review], [[], []]);

  //  since ِ برنامه پس از کف: نخستین گذارِ پیش از since ⇒ نه؛ پس از آن ⇒ بله
  const mid = await send();
  await walk(a, mid.id, ['accepted', 'picking', 'packed', 'out_for_delivery']);
  await tick();
  const since = Date.now();
  await tick();
  await walk(a, mid.id, ['delivered']);
  const late = await newOrder(a);
  await walk(a, late.id, TO_PICKED_UP);
  r = (await handover(a, since)).body;
  assert.deepEqual(r.items.map((x) => x.id), [late.id], 'نخستین گذارِ پس از since');
  assert.ok(r.items[0].handedOverAt >= since);
  assert.equal(r.since, since);
  assert.deepEqual((await handover(a)).body.items.map((x) => x.id).sort(), [mid.id, late.id].sort());
  assert.equal((await h.get('/api/shop/orders/handover?after=bad', { token: a.token })).status, 400);
});

test('handover: ۲۳ سفارشِ گیرکرده ⇒ صفحهٔ ۲۰تایی با after، بی تکرار و بی جاافتادن', async () => {
  const a = await shopWithStock('گیرکرده');
  const ids = [];
  for (let i = 0; i < 23; i++) {
    const o = await newOrder(a, { lines: [{ name: 'کیسه', qtyMilli: 1000, unitPriceMinor: 500 }], discountMinor: 0 });
    await walk(a, o.id, TO_PICKED_UP);
    ids.push(o.id);
  }
  const p1 = (await handover(a)).body;
  assert.equal(p1.items.length, 20);
  assert.ok(p1.nextAfter);
  const p2 = (await h.get(`/api/shop/orders/handover?since=0&after=${encodeURIComponent(p1.nextAfter)}`, { token: a.token })).body;
  assert.equal(p2.items.length, 3);
  assert.equal(p2.nextAfter, null);
  assert.deepEqual([...p1.items, ...p2.items].map((x) => x.id).sort(), [...ids].sort());
  const at = [...p1.items, ...p2.items].map((x) => x.handedOverAt);
  assert.deepEqual(at, [...at].sort((x, y) => x - y), 'به ترتیبِ زمانِ تحویل');
});

test('برگشت به دکان پس از فروشِ تحویل ⇒ در reversals؛ لغوِ فروش ⇒ دیگر نه؛ موجودیِ سرور یک بار برمی‌گردد', async () => {
  const a = await shopWithStock('برگشت');
  const r0 = await h.post('/api/shop/orders', { source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, address: { landmark: 'کنارِ مسجد' }, lines: riceLine(1500) },
  { token: a.token, headers: { 'Idempotency-Key': `ledger-back-${Date.now()}` } });
  assert.equal(r0.status, 201, JSON.stringify(r0.body));
  const id = r0.body.order.id;
  await walk(a, id, ['accepted', 'picking', 'packed', 'out_for_delivery']);
  const order = (await h.get(`/api/shop/orders/${id}`, { token: a.token })).body.order;
  assert.equal((await push(a.token, DEV_A, appSaleOps(order))).status, 200);
  assert.equal((await levels(a)).levels.shop_floor, 8500);
  await h.post(`/api/shop/orders/${id}/transition`, { to: 'delivery_failed', reason: 'no_answer' }, { token: a.token });
  await walk(a, id, ['retry_scheduled', 'returned_to_store']);
  let r = (await handover(a)).body;
  assert.deepEqual(r.items, [], 'فروش هست');
  assert.deepEqual(r.reversals.map((x) => [x.id, x.action]), [[id, 'reverse']]);
  assert.ok(r.reversals[0].returnedAt > 0);
  //  برنامه فروش را لغو می‌کند (همان opِ OrderFulfillment.reverse)
  const cancel = [{ op_id: opId(), ts: Date.now(), table: 'sales', row_id: `o-${id}`, type: 'update',
    fields: { status: 'cancelled', cancelReason: 'سفارشِ اینترنتی به دکان برگشت' } }];
  assert.equal((await push(a.token, DEV_A, cancel)).status, 200);
  assert.equal((await push(a.token, DEV_B, cancel.map((o) => ({ ...o, op_id: opId() })))).status, 200);
  r = (await handover(a)).body;
  assert.deepEqual(r.reversals, []);
  assert.equal((await levels(a)).levels.shop_floor, 10000, 'یک بار برگشت');
});

test('کسرِ سفارش‌محورِ پیشین (res:…:sale) بی فاکتورِ W ⇒ حرکتِ جبرانی؛ با W ⇒ می‌ماند (هر قلم جدا)؛ رزروِ نگه‌داشته آزاد؛ وظیفه‌های رزرو بسته', async () => {
  const a = await shopWithStock('قدیمی‌کسر');
  const stock = require('../src/lib/stock');
  const t = now();
  const move = (c, key, ref, q) => stock.postMove(c, a.shopId, {
    productId: 'p-rice', location: 'shop_floor', type: 'sale', qtyMilli: q, mode: 'recorded', source: 'order',
    sourceKey: key, refType: 'order', refId: ref, actorType: 'user', actorId: '', reason: 'order_fulfilled', at: t,
  }, t);
  await tx(async (c) => {
    await move(c, 'res:legacy1:sale:shop_floor', 'ord-legacy-1', 1500);
    await move(c, 'res:legacy2:sale:shop_floor', 'ord-legacy-2', 500);
    await c.query(`INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at)
                   VALUES ($1,'si:w-ord-legacy-2-1','p-rice',-500,0,$2)`, [a.shopId, t]);
    await c.query(`INSERT INTO stock_reservations (id, shop_id, order_id, line_no, product_id, branch_id, qty_milli, status, expires_at, created_at)
                   VALUES ('rsv-legacy',$1,'ord-legacy-3',1,'p-rice','',700,'held',NULL,$2)`, [a.shopId, t]);
    await stock.insertMove(c, a.shopId, { productId: 'p-rice', location: '', type: 'reserve', qtyMilli: 700, deltaOnHand: 0,
      deltaReserved: 700, mode: 'local', source: 'order', sourceKey: 'res:rsv-legacy:reserve', refType: 'order',
      refId: 'ord-legacy-3', actorType: 'user', actorId: '', reason: 'order', at: t }, t);
    await stock.openTask(c, a.shopId, { kind: 'reservation_short', productId: 'p-rice', dedupe: 'short:p-rice', detail: {} }, t);
    //  سفارشِ دوقلمی: هر دو قلم کسرِ سرور داشتند، فاکتورِ W فقط قلمِ ۱ را دارد ⇒ قلمِ ۲ جبران می‌شود
    for (const [line, q] of [[1, 200], [2, 300]]) {
      await c.query(`INSERT INTO stock_reservations (id, shop_id, order_id, line_no, product_id, branch_id, qty_milli, status, expires_at, created_at, closed_at)
                     VALUES ($1,$2,'ord-legacy-4',$3,'p-rice','',$4,'committed',NULL,$5,$5)`, [`rsv-l4-${line}`, a.shopId, line, q, t]);
      await move(c, `res:rsv-l4-${line}:sale:shop_floor`, 'ord-legacy-4', q);
    }
    await c.query(`INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at)
                   VALUES ($1,'si:w-ord-legacy-4-1','p-rice',-200,0,$2)`, [a.shopId, t]);
  });
  //  نخستین خواندنِ کاتالوگ ⇒ کنار گذاشتن (یک بار)
  const st = await levels(a);
  assert.equal(st.levels.shop_floor, 9300, '۱۰ − ۱٫۵ + ۱٫۵ (جبران) − ۰٫۵ (پشتِ W) − ۰٫۲ (قلمِ ۱ پشتِ W) − ۰٫۳ + ۰٫۳ (قلمِ ۲ بی W)');
  assert.equal(st.reservedMilli, 0);
  const undo = await many(`SELECT source, type, qty_milli, ref_id, source_key FROM stock_moves WHERE shop_id=$1 AND source_key LIKE 'undo:%' ORDER BY source_key`, [a.shopId]);
  assert.deepEqual(undo.map((m) => [m.source, m.type, Number(m.qty_milli), m.ref_id, m.source_key]), [
    ['system', 'customer_return', 1500, 'ord-legacy-1', 'undo:res:legacy1:sale:shop_floor'],
    ['system', 'customer_return', 300, 'ord-legacy-4', 'undo:res:rsv-l4-2:sale:shop_floor'],
  ]);
  assert.equal((await one('SELECT status FROM stock_reservations WHERE id=$1', ['rsv-legacy'])).status, 'released');
  assert.equal(await count("SELECT COUNT(*)::int AS n FROM stock_tasks WHERE shop_id=$1 AND status='open' AND kind='reservation_short'", [a.shopId]), 0);
  const rep = await h.get('/api/shop/catalog/report?productId=p-rice', { token: a.token });
  assert.equal(rep.body.ok, true, JSON.stringify(rep.body.mismatches));
  //  یک سطر در دفترِ ممیزیِ دکان با شمارش‌ها
  const audits = await many(`SELECT actor_type, detail FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired'`, [a.shopId]);
  assert.equal(audits.length, 1);
  assert.equal(audits[0].actor_type, 'system');
  const det = typeof audits[0].detail === 'string' ? JSON.parse(audits[0].detail) : audits[0].detail;
  assert.deepEqual([det.undoneMoves, det.undoneMilli, det.keptWithW, det.releasedHolds, det.releasedMilli, det.dismissedTasks],
    [2, 1800, 2, 1, 700, 1]);
  //  دوباره خواندن (و حتی دوباره دویدن پس از راه‌اندازیِ دوباره) چیزی را دو بار جبران نمی‌کند
  const catalog = require('../src/lib/catalog');
  await catalog.refresh(a.shopId, { full: true });
  assert.equal((await levels(a)).levels.shop_floor, 9300);
  const again = await tx((c) => catalog.retireOrderStock(c, a.shopId, now()));
  assert.equal(again.changed, false, JSON.stringify(again.audit));
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired'`, [a.shopId])).length, 1);
  //  دکانی بی ردیفِ سفارش‌محورِ پیشین (یا پرچمِ هرگز روشن‌نشده) ⇒ هیچ سطر و هیچ حرکت
  const clean = await shopWithStock('بی‌کسرِ قدیمی');
  await levels(clean);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired'`, [clean.shopId])).length, 0);
  assert.equal(await count(`SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1 AND source='system'`, [clean.shopId]), 0);
  assert.equal((await tx((c) => catalog.retireOrderStock(c, clean.shopId, now()))).changed, false);
});

test('کنار گذاشتنِ کسرِ قدیمی از راهِ «حرکتِ دستی» (نخستین کارِ کاتالوگ) هم یک سطرِ ممیزی دارد', async () => {
  const a = await shopWithStock('قدیمی‌دستی');
  const stock = require('../src/lib/stock');
  const t = now();
  await tx((c) => stock.postMove(c, a.shopId, {
    productId: 'p-rice', location: 'shop_floor', type: 'sale', qtyMilli: 400, mode: 'recorded', source: 'order',
    sourceKey: 'res:legacy-mm:sale:shop_floor', refType: 'order', refId: 'ord-legacy-mm', actorType: 'user', actorId: '',
    reason: 'order_fulfilled', at: t,
  }, t));
  //  نخستین کارِ کاتالوگِ این دکان پس از به‌کارگیری: حرکتِ دستی (نه خواندن)
  const r = await h.post('/api/shop/catalog/moves', { kind: 'adjust', productId: 'p-rice', location: 'shop_floor', qtyMilli: 100, reason: 'شمارش' },
    { token: a.token, headers: { 'Idempotency-Key': `ledger-mm-${Date.now()}` } });
  assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
  const audits = await many(`SELECT actor_type, detail FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired'`, [a.shopId]);
  assert.equal(audits.length, 1, 'سطرِ ممیزی از راهِ حرکتِ دستی');
  const det = typeof audits[0].detail === 'string' ? JSON.parse(audits[0].detail) : audits[0].detail;
  assert.deepEqual([det.undoneMoves, det.undoneMilli], [1, 400]);
  assert.equal((await levels(a)).levels.shop_floor, 10100, '۱۰ − ۰٫۴ + ۰٫۴ (جبران) + ۰٫۱ (اصلاح)');
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired'`, [a.shopId])).length, 1, 'یک بار');
});

/* ══════════════════════ بازبینیِ دومِ #340: کفِ هر دکان، اجازهٔ هر سفارش، بازپرداختِ جزئی ══════════════════════ */

test('کفِ هر دکان: نخستین config ِ برنامهٔ «فروشِ تحویل» (caps) — نه زودتر از زمانِ به‌کارگیری؛ تحویلِ پیش از آن هرگز', async () => {
  const a = await shopWithStock('کفِ دکان', { claim: false });
  const early = await newOrder(a);
  await walk(a, early.id, TO_PICKED_UP);
  //  هنوز هیچ برنامهٔ «فروشِ تحویل» این دکان را ندیده ⇒ هیچ فهرستی، حتی با since=0
  let r = (await handover(a)).body;
  assert.deepEqual([r.items, r.reversals, r.since], [[], [], null]);
  //  برنامهٔ کهنه (بی caps) کف نمی‌گذارد
  assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.handoverSince, null);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM live_config WHERE scope=$1 AND key=$2', [a.shopId, ledger.FLOOR_KEY]), 0);
  assert.equal(await ledger.floorOf(a.shopId), null);

  await tick();
  const t0 = Date.now();
  const cfg = (await appConfig(a.token)).body;
  assert.ok(cfg.handoverSince >= t0 && cfg.handoverSince <= Date.now(), `${cfg.handoverSince} ≥ ${t0}`);
  //  دستگاهِ دوم، نقشِ دیگر یا دوباره ⇒ همان کف (نخستین بار می‌ماند)
  await tick();
  const s = await staffOf(a, 'شاگردِ کف');
  assert.equal((await appConfig(s.token, 'other,saleOnHandover')).body.handoverSince, cfg.handoverSince);
  assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.handoverSince, cfg.handoverSince);
  //  تحویلِ پیش از کف هرگز — نه در فهرست، نه با orderId
  assert.deepEqual((await handover(a)).body.items, []);
  assert.deepEqual((await h.get(`/api/shop/orders/handover?since=0&orderId=${early.id}`, { token: a.token })).body.items, []);
  await tick();
  const late = await newOrder(a);
  await walk(a, late.id, TO_PICKED_UP);
  r = (await handover(a)).body;
  assert.deepEqual(r.items.map((x) => x.id), [late.id]);
  assert.equal(r.since, cfg.handoverSince);

  //  زمانِ به‌کارگیری (env) دیرتر از کفِ ذخیره‌شده ⇒ همان جلو می‌افتد
  const deploy = Date.now() + 60_000;
  process.env.ORDERS_HANDOVER_FLOOR_MS = String(deploy);
  try {
    assert.equal((await appConfig(a.token)).body.handoverSince, deploy);
    assert.deepEqual((await handover(a)).body.items, []);
    const fresh = await shopWithStock('کفِ پس از به‌کارگیری', { claim: false });
    assert.equal((await appConfig(fresh.token)).body.handoverSince, deploy, 'نه زودتر از زمانِ به‌کارگیری');
  } finally {
    delete process.env.ORDERS_HANDOVER_FLOOR_MS;
  }
});

test('handover: اجازهٔ هر سفارش از اجازهٔ مؤثرِ همین کاربر (لایه‌های نقش و عضو) و درصدِ تخفیفِ همان سفارش', async () => {
  const a = await shopWithStock('اجازهٔ سفارش');
  const m = await staffOf(a, 'مدیرِ سقف', 'manager');
  const s = await staffOf(a, 'شاگردِ سفارش');
  //  جمعِ اقلام ۱۸۵ افغانی: بی تخفیف، ۵ افغانی (۲٫۷٪)، ۱۵ افغانی (۸٫۱٪)
  const plain = await newOrder(a, { discountMinor: 0 });
  const small = await newOrder(a, { discountMinor: 500 });
  const big = await newOrder(a, { discountMinor: 1500 });
  for (const o of [plain, small, big]) await walk(a, o.id, TO_PICKED_UP);
  const flags = async (u) => {
    const r = await handover(u);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return [plain, small, big].map((o) => r.body.items.find((x) => x.id === o.id).recordable);
  };
  const setRole = (role, perms) => h.put(`/api/shop/permissions/roles/${role}`, { perms }, { token: a.token });
  const setMember = (u, perms) => h.put(`/api/shop/permissions/members/${u.user.id}`, { perms }, { token: a.token });

  assert.deepEqual(await flags(a), [true, true, true], 'صاحب');
  assert.deepEqual(await flags(m), [true, true, true], 'مدیر بی لایه');
  assert.deepEqual(await flags(s), [true, false, false], 'شاگرد: تخفیف ندارد');
  //  لایهٔ نقش: سقفِ ۵٪ ⇒ تخفیفِ ۸٫۱٪ نیازمندِ تأیید است و Sync v1 ردش می‌کند
  const role = await setRole('manager', { 'sales.discount': { caps: { maxPct: 5 } } });
  assert.equal(role.status, 200, JSON.stringify(role.body));
  assert.deepEqual(await flags(m), [true, true, false], 'مدیر با سقفِ ۵٪');
  //  همان داوریِ Sync v1: فروشِ ۸٫۱٪ از دستگاهِ همین مدیر بی برگه رد می‌شود
  const ops = appSaleOps((await h.get(`/api/shop/orders/${big.id}`, { token: a.token })).body.order);
  const pushed = await push(m.token, 'dev-ledger-mgr', ops);
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  const head = pushed.body.results.find((x) => x.op_id === ops[0].op_id);
  assert.deepEqual([head.status, head.reason], ['rejected', 'approval_required']);
  //  لایهٔ همین کاربر: سقفِ مبلغِ ۴ افغانی ⇒ تخفیفِ ۵ افغانی هم نه
  const mem = await setMember(m, { 'sales.discount': { caps: { maxAmount: 400 } } });
  assert.equal(mem.status, 200, JSON.stringify(mem.body));
  assert.deepEqual(await flags(m), [true, false, false], 'مدیر با سقفِ مبلغِ خودش');
  assert.deepEqual(await flags(a), [true, true, true], 'صاحب را لایه‌ای قفل نمی‌کند');

  //  برگشته به دکان با فروشِ زنده ⇒ reversible همان سنجشِ لغو
  const r0 = await h.post('/api/shop/orders', { source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, address: { landmark: 'کنارِ مسجد' }, lines: riceLine(1000) },
  { token: a.token, headers: { 'Idempotency-Key': `ledger-rev-${Date.now()}` } });
  assert.equal(r0.status, 201, JSON.stringify(r0.body));
  const id = r0.body.order.id;
  await walk(a, id, ['accepted', 'picking', 'packed', 'out_for_delivery']);
  const order = (await h.get(`/api/shop/orders/${id}`, { token: a.token })).body.order;
  assert.equal((await push(a.token, DEV_A, appSaleOps(order))).status, 200);
  await h.post(`/api/shop/orders/${id}/transition`, { to: 'delivery_failed', reason: 'no_answer' }, { token: a.token });
  await walk(a, id, ['retry_scheduled', 'returned_to_store']);
  const rev = async (u) => (await handover(u)).body.reversals.find((x) => x.id === id).reversible;
  assert.equal(await rev(a), true);
  assert.equal(await rev(m), true);
  assert.equal(await rev(s), false, 'شاگرد لغو ندارد');
  assert.equal((await setRole('manager', { 'sales.cancel': { mode: 'approve' } })).status, 200);
  assert.equal(await rev(m), false, 'مدیرِ «نیازمندِ تأیید» برای لغو');
  assert.equal(await rev(a), true);
});

test('بازپرداختِ جزئی (کالا پیشِ مشتری ماند) بی فروش ⇒ در فهرستِ تحویل؛ پس از فروش نه', async () => {
  const a = await shopWithStock('بازپرداختِ جزئی');
  const order = await newOrder(a);
  await walk(a, order.id, [...TO_PICKED_UP, 'return_requested', 'refunded_partial']);
  const r = (await handover(a)).body;
  assert.deepEqual(r.items.map((x) => [x.id, x.status, x.recordable]), [[order.id, 'refunded_partial', true]]);
  assert.ok(ledger.OUT_OF_SHOP.includes('refunded_partial'));
  const full = (await h.get(`/api/shop/orders/${order.id}`, { token: a.token })).body.order;
  assert.equal((await push(a.token, DEV_A, appSaleOps(full))).status, 200);
  assert.deepEqual((await handover(a)).body.items, []);
});

/* ══════════════════════ بازبینیِ سومِ #340: کفِ راه‌اندازی، اشتراکِ شاگرد، کلیدِ رزرو، ممیزیِ گم‌شده ══════════════════════ */

test('راه‌اندازیِ سرور: دکانِ دارای shop.orders.ledger یا shop.catalog و بی کف ⇒ کف = لحظهٔ راه‌اندازی؛ بقیه و کفِ موجود دست نمی‌خورند', async () => {
  const both = await shopWithStock('آغاز-دوپرچم', { claim: false });
  const cat = await shopWithStock('آغاز-کاتالوگ', { claim: false, ledgerOn: false });
  const led = await shopWithStock('آغاز-دفتر', { claim: false, catalogOn: false });
  const plain = await shopWithStock('آغاز-ساده', { claim: false, catalogOn: false, ledgerOn: false });
  const had = await shopWithStock('آغاز-کف‌دار');
  const kept = await ledger.floorOf(had.shopId);
  //  پرچمِ خاموشِ صریح هم «روشن» نیست
  const off = await shopWithStock('آغاز-خاموش', { claim: false, catalogOn: false, ledgerOn: false });
  await setFlag(off.shopId, 'shop.catalog', false);
  //  سفارشی که پیش از راه‌اندازیِ این نسخه (پیش از کف) تحویل شده بود
  const early = await newOrder(both);
  await walk(both, early.id, TO_PICKED_UP);
  await tick();
  const t0 = Date.now();
  const n = await ledger.claimFloorsAtStart();
  assert.ok(n >= 3, `${n}`);
  for (const s of [both, cat, led]) {
    const f = await ledger.floorOf(s.shopId);
    assert.ok(f >= t0 && f <= Date.now(), `${s.shopId}: ${f}`);
  }
  assert.equal(await ledger.floorOf(plain.shopId), null, 'بی کسرِ سفارشیِ پیشین ⇒ کف با نخستین config');
  assert.equal(await ledger.floorOf(off.shopId), null);
  assert.equal(await ledger.floorOf(had.shopId), kept, 'کفِ موجود جابه‌جا نمی‌شود');
  //  config ِ برنامه همان کفِ راه‌اندازی را می‌گوید؛ تحویلِ پیش از آن هرگز خودکار نه
  assert.equal((await appConfig(both.token)).body.handoverSince, await ledger.floorOf(both.shopId));
  assert.deepEqual((await handover(both)).body.items, []);
  const late = await newOrder(both);
  await walk(both, late.id, TO_PICKED_UP);
  assert.deepEqual((await handover(both)).body.items.map((x) => x.id), [late.id], 'پنجرهٔ «فروشِ گم‌شده» بسته است');
  //  دورِ دوم: هیچ
  const f1 = await ledger.floorOf(both.shopId);
  assert.equal(await ledger.claimFloorsAtStart(), 0);
  assert.equal(await ledger.floorOf(both.shopId), f1);
});

test('شاگردِ بی اشتراکِ چنددستگاهی (multi_device) ⇒ recordSale و recordable نه — همان requireSyncWrite ِ push', async () => {
  const a = await shopWithStock('بی‌اشتراک');
  const s = await staffOf(a, 'شاگردِ بی‌اشتراک');
  const order = await newOrder(a, { discountMinor: 0 });
  await walk(a, order.id, TO_PICKED_UP);
  const flag = async (u) => (await handover(u)).body.items.find((x) => x.id === order.id).recordable;
  assert.equal(await flag(s), true, 'با دورهٔ آزمایشی');
  assert.equal((await appConfig(s.token)).body.permissions.recordSale, true);
  //  اشتراک و دورهٔ آزمایشی تمام ⇒ push ِ شاگرد ۴۰۳ ⇒ برنامه نباید بنویسد
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [a.shopId, Date.now() - 400 * 24 * 3600 * 1000]);
  const full = (await h.get(`/api/shop/orders/${order.id}`, { token: a.token })).body.order;
  const pushed = await push(s.token, 'dev-ledger-noent', appSaleOps(full));
  assert.deepEqual([pushed.status, pushed.body.error && pushed.body.error.code], [403, 'subscription_required']);
  assert.equal(await flag(s), false);
  assert.equal((await appConfig(s.token)).body.permissions.recordSale, false);
  assert.equal(await flag(a), true, 'صاحب بی اشتراک هم می‌نویسد');
});

test('کلیدِ کف (shop.orders.handover_floor) از پنلِ «تنظیماتِ زنده» نوشته یا پاک نمی‌شود', async () => {
  const a = await shopWithStock('کلیدِ رزرو');
  const kept = await ledger.floorOf(a.shopId);
  const put = await h.put('/api/admin/pump/live-config', { scope: a.shopId, key: ledger.FLOOR_KEY, value: 1 }, { token: admin });
  assert.deepEqual([put.status, put.body.error.code], [409, 'reserved_key']);
  const del = await h.api('DELETE', '/api/admin/pump/live-config', { token: admin, body: { scope: a.shopId, key: ledger.FLOOR_KEY } });
  assert.deepEqual([del.status, del.body.error.code], [409, 'reserved_key']);
  assert.equal(await ledger.floorOf(a.shopId), kept);
});

test('ممیزیِ کنار گذاشتن که نوشته نشد (خطا پس از کامیت) ⇒ دورِ بعد همان سطر را با شمارش‌های دفتر می‌نویسد، یک بار', async () => {
  const a = await shopWithStock('ممیزیِ گم‌شده');
  const stock = require('../src/lib/stock');
  const catalog = require('../src/lib/catalog');
  const t = now();
  await tx((c) => stock.postMove(c, a.shopId, {
    productId: 'p-rice', location: 'shop_floor', type: 'sale', qtyMilli: 600, mode: 'recorded', source: 'order',
    sourceKey: 'res:legacy-lost:sale:shop_floor', refType: 'order', refId: 'ord-legacy-lost', actorType: 'user', actorId: '',
    reason: 'order_fulfilled', at: t,
  }, t));
  //  دورِ نخست در تراکنش، بی سطرِ ممیزی (همان «سرور پس از کامیت افتاد»)
  const first = await tx((c) => catalog.retireOrderStock(c, a.shopId, now()));
  assert.equal(first.changed, true);
  const audits = () => many(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired'`, [a.shopId]);
  assert.equal((await audits()).length, 0);
  //  راه‌اندازیِ دوباره: دورِ دوم چیزی جبران نمی‌کند ولی سطرِ گم‌شده را می‌خواهد
  const again = await tx((c) => catalog.retireOrderStock(c, a.shopId, now()));
  assert.equal(again.changed, false);
  assert.equal(again.needsAudit, true);
  await catalog.afterIngest(a.shopId, { retired: again });
  const rows = await audits();
  assert.equal(rows.length, 1);
  const det = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.deepEqual([det.undoneMoves, det.undoneMilli, det.recovered], [1, 600, true]);
  //  سوم: سطر هست ⇒ هیچ
  const third = await tx((c) => catalog.retireOrderStock(c, a.shopId, now()));
  assert.deepEqual([third.changed, Boolean(third.needsAudit)], [false, false]);
  assert.equal((await audits()).length, 1);
});
