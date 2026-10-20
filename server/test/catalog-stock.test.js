'use strict';
/**
 * کاتالوگِ واحد و موجودیِ هوشمند — v3 §۳۲.۴ (INV-01..16@32)، §۳۸.۳، INV-19، CHK-18.
 *
 * هر آزمون از راهِ واقعی می‌رود: کالا و فروش را «دستگاه» با همان
 * `POST /api/sync/v1/push` (یا v3) می‌فرستد، سفارش از `POST /api/shop/orders`
 * ثبت می‌شود و موجودی از `/api/shop/catalog` خوانده می‌شود. هیچ مسیرِ واقعی‌ای
 * ساختگی نیست؛ فقط «آفلاین ماندنِ دستگاه» (INV-14) با کهنه کردنِ زمانِ دیده‌شدن
 * شبیه‌سازی می‌شود.
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ پرچمِ `shop.catalog` خاموش ⇒ هیچ ردیفی و همان رفتارِ پیشین.
 *   ⛔ رزرو و ساختِ سفارش در یک تراکنش؛ دو خریدِ هم‌زمانِ آخرین عدد ⇒ فقط یکی.
 *   ⛔ فروشِ ثبت‌شدهٔ دستگاه هرگز رد نمی‌شود و دو بار کم نمی‌شود؛ تعارض ⇒ وظیفه.
 *   ⛔ دفتر = مانده‌ها = بازپخشِ `stock-core` (INV-16).
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const stock = require('../src/lib/stock');
const catalog = require('../src/lib/catalog');
const hlc = require('../src/lib/hlc');

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
test.after(async () => { stock.stopSweeper(); await h.stop(); });

// ── کمکی‌ها ──────────────────────────────────────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-a-${made.body.shop.id}`.slice(0, 60) };
}

async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}

async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function enable(s) {
  await live(s.shopId, 'shop.orders', true);
  await live(s.shopId, 'shop.catalog', true);
}

let opN = 0;
const opId = () => `01JCS${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops, { device = s.device, ...extra } = {}) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: device, schema_version: 2, ops, ...extra },
    headers: { 'X-App': 'shop', 'X-Device': device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  return r.body;
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id, f = {}) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(), ...f,
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

let keyN = 0;
const fresh = () => `cat-stock-${++keyN}-${Date.now()}`;
const line = (pid, qtyMilli, extra = {}) => ({ productId: pid, name: `کالای ${pid}`, unit: 'دانه', qtyMilli, unitPriceMinor: 5000, ...extra });
const order = (s, lines, key = fresh()) => h.post('/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری' }, lines,
}, { token: s.token, headers: { 'Idempotency-Key': key } });
const go = (s, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
const cat = (s, p, token = s.token) => h.get(`/api/shop/catalog${p}`, { token });
const mv = (s, body, { key = fresh(), token = s.token } = {}) =>
  h.post('/api/shop/catalog/moves', body, { token, headers: { 'Idempotency-Key': key } });
const settings = (s, body) => h.put('/api/shop/catalog/settings', body, { token: s.token });

async function stockOf(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock;
}
async function heldOf(s, orderId) {
  return (await cat(s, `/reservations?orderId=${orderId}`)).body.reservations;
}
async function movesOf(s, pid) {
  return (await cat(s, `/moves?productId=${pid}&limit=100`)).body.items;
}
async function openTasks(s) {
  return (await cat(s, '/tasks?status=open')).body.tasks;
}
/** INV-16: مانده = جمعِ دفتر = بازپخشِ هسته. */
async function reconciled(s, pid) {
  const all = await cat(s, '/report');
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.equal(all.body.ok, true, JSON.stringify(all.body.mismatches));
  if (pid) {
    const one1 = await cat(s, `/report?productId=${pid}`);
    assert.equal(one1.body.ok, true, JSON.stringify(one1.body.mismatches));
    return one1.body.replay;
  }
  return null;
}
async function count(table, shopId) {
  return Number((await one(`SELECT COUNT(*)::int AS n FROM ${table} WHERE shop_id=$1`, [shopId])).n);
}

// ════════════════════════════════════════════════════════════════════

test('هستهٔ موجودیِ سرور همان license/stock-core.js است (بایت‌به‌بایت، بی چندشاخه شدن)', () => {
  const a = fs.readFileSync(path.join(__dirname, '..', '..', 'license', 'stock-core.js'));
  const b = fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'stock-core.js'));
  assert.ok(a.equals(b), 'server/src/lib/stock-core.js باید کپیِ دقیقِ license/stock-core.js باشد');
});

test('⛔ پرچمِ shop.catalog خاموش: هیچ ردیفی، سفارش مثلِ پیش', async () => {
  const s = await shop('خاموش');
  await live(s.shopId, 'shop.orders', true);
  await push(s, [product('off1'), entry('off-e1', 'off1', 1)]);
  //  بیش از موجودیِ دستگاه: بی کاتالوگ همان رفتارِ امروز (سفارشِ دستی پذیرفته می‌شود)
  const o = await order(s, [line('off1', 5000)]);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  assert.equal((await go(s, o.body.order.id, { to: 'rejected', reason: 'busy' })).status, 200);

  const cfg = await cat(s, '/config');
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  const blocked = await cat(s, '/products');
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'catalog_disabled');
  assert.equal(await catalog.publicStock(s.shopId, ['off1']), null);
  for (const t of ['catalog_settings', 'catalog_products', 'catalog_sync_state', 'stock_moves', 'stock_levels',
    'stock_reservations', 'stock_sources', 'stock_tasks']) {
    assert.equal(await count(t, s.shopId), 0, `${t} باید خالی بماند`);
  }
});

test('INV-01: کالای تعریف‌شده در دستگاه با همان شناسه در کاتالوگ، موجودی و سفارش دیده می‌شود', async () => {
  const s = await shop('یک‌تعریف');
  await enable(s);
  await push(s, [product('p1', { name: 'برنج', salePrice: 120.5, barcodes: ['6261234'], category: 'غله' }), entry('p1-e1', 'p1', 10)]);
  const r = await cat(s, '/products');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const p = r.body.items.find((i) => i.id === 'p1');
  assert.ok(p, 'کالا در کاتالوگ نیست');
  assert.equal(p.name, 'برنج');
  assert.equal(p.priceMinor, 12050);
  assert.equal(p.category, 'غله');
  assert.deepEqual(p.barcodes, ['6261234']);
  assert.equal(p.stock.levels.shop_floor, 10000);
  assert.equal(p.stock.badge.status, 'in_stock');
  assert.ok((await cat(s, `/products?q=${encodeURIComponent('برن')}`)).body.items.some((i) => i.id === 'p1'));
  assert.ok((await cat(s, '/products?q=6261234')).body.items.some((i) => i.id === 'p1'));

  const o = await order(s, [line('p1', 2000)]);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  const held = await heldOf(s, o.body.order.id);
  assert.deepEqual(held.map((x) => [x.productId, x.qtyMilli, x.status]), [['p1', 2000, 'held']]);
  await reconciled(s, 'p1');
});

test('INV-02: قفسه صفر و انبار ۵ ⇒ «در انبار، آماده‌سازی لازم»، نه «تمام شده»؛ رزرو وظیفهٔ انتقال می‌سازد', async () => {
  const s = await shop('انبار');
  await enable(s);
  await push(s, [product('w1'), entry('w1-e1', 'w1', 5)]);
  const t = await mv(s, { kind: 'transfer', productId: 'w1', from: 'shop_floor', to: 'warehouse', qtyMilli: 5000 });
  assert.equal(t.status, 201, JSON.stringify(t.body));

  let st = await stockOf(s, 'w1');
  assert.equal(st.levels.shop_floor, 0);
  assert.equal(st.levels.warehouse, 5000);
  assert.equal(st.badge.status, 'in_warehouse');
  assert.equal(st.badge.prepMinutes, null, 'بی تنظیمِ واقعی زمانی ساخته نمی‌شود');

  assert.equal((await settings(s, { prepMinutes: 45, showQty: 'exact' })).status, 200);
  let pub = (await catalog.publicStock(s.shopId, ['w1'])).get('w1');
  assert.equal(pub.status, 'in_warehouse');
  assert.equal(pub.prepMinutes, 45);
  assert.equal(pub.readyMilli, 0);
  assert.equal(pub.laterMilli, 5000);

  assert.equal((await settings(s, { showQty: 'hidden' })).status, 200);
  pub = (await catalog.publicStock(s.shopId, ['w1'])).get('w1');
  assert.equal(pub.status, 'in_warehouse');
  assert.equal(pub.sellableMilli, undefined, 'عددِ انبار پنهان است ولی وضعیت دروغ نیست');

  const o = await order(s, [line('w1', 2000)]);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  const task = (await openTasks(s)).find((x) => x.kind === 'transfer_needed' && x.productId === 'w1');
  assert.ok(task, 'وظیفهٔ انتقالِ انبار به قفسه ساخته نشد');
  assert.equal(task.detail.needMilli, 2000);
  st = await stockOf(s, 'w1');
  assert.equal(st.sellableMilli, 3000);
  await reconciled(s, 'w1');
});

test('INV-03: قفسه و انبار صفر ⇒ «تمام شده» و سفارش رد می‌شود (بی هیچ ردیفِ سفارش)', async () => {
  const s = await shop('صفر');
  await enable(s);
  await push(s, [product('z1', { name: 'روغن' })]);
  assert.equal((await stockOf(s, 'z1')).badge.status, 'out_of_stock');
  const before = await count('online_orders', s.shopId);
  const o = await order(s, [line('z1', 1000)]);
  assert.equal(o.status, 409, JSON.stringify(o.body));
  assert.equal(o.body.error.code, 'out_of_stock');
  assert.match(o.body.error.message, /روغن/);
  assert.equal(await count('online_orders', s.shopId), before, 'سفارشِ ردشده نباید ردیف بسازد');
  assert.equal(await count('stock_reservations', s.shopId), 0);
});

test('INV-04: ۵ موجود و ۴ رزرو ⇒ فقط ۱ آزاد؛ نمایش و ثبت با همان سازگار', async () => {
  const s = await shop('آزاد');
  await enable(s);
  await push(s, [product('f1'), entry('f1-e1', 'f1', 5)]);
  assert.equal((await order(s, [line('f1', 4000)])).status, 201);
  let st = await stockOf(s, 'f1');
  assert.equal(st.reservedMilli, 4000);
  assert.equal(st.sellableMilli, 1000);
  assert.equal(st.badge.status, 'in_stock');

  const two = await order(s, [line('f1', 2000)]);
  assert.equal(two.status, 409);
  assert.equal(two.body.error.code, 'out_of_stock');
  assert.equal(two.body.error.lines[0].availableMilli, 1000, 'مشتری مقدارِ قابلِ سفارش را می‌بیند');
  assert.equal(two.body.error.lines[0].requestedMilli, 2000);

  assert.equal((await order(s, [line('f1', 1000)])).status, 201);
  st = await stockOf(s, 'f1');
  assert.equal(st.sellableMilli, 0);
  assert.equal(st.badge.status, 'out_of_stock');
  await reconciled(s, 'f1');
});

test('INV-05: دو خریدِ هم‌زمانِ آخرین عدد ⇒ فقط یکی؛ شش خریدِ هم‌زمان با سه عدد ⇒ فقط سه', async () => {
  const s = await shop('هم‌زمان');
  await enable(s);
  await push(s, [product('last1', { name: 'شیر' }), entry('last1-e', 'last1', 1), product('m3'), entry('m3-e', 'm3', 3)]);
  //  کاتالوگ یک بار خوانده شود تا هم‌زمانی روی خودِ رزرو باشد
  await stockOf(s, 'last1');

  const two = await Promise.all([order(s, [line('last1', 1000)]), order(s, [line('last1', 1000)])]);
  assert.deepEqual(two.map((r) => r.status).sort(), [201, 409], JSON.stringify(two.map((r) => r.body)));
  const lost = two.find((r) => r.status === 409);
  assert.equal(lost.body.error.code, 'out_of_stock');
  assert.match(lost.body.error.message, /شیر.*موجود نیست/);

  const six = await Promise.all(Array.from({ length: 6 }, () => order(s, [line('m3', 1000)])));
  assert.equal(six.filter((r) => r.status === 201).length, 3, JSON.stringify(six.map((r) => r.status)));
  assert.equal(six.filter((r) => r.status === 409).length, 3);

  for (const pid of ['last1', 'm3']) {
    const st = await stockOf(s, pid);
    assert.equal(st.sellableMilli, 0);
    assert.ok(st.levels.shop_floor >= 0, 'موجودی منفی نشد');
    assert.equal(st.reservedMilli, st.levels.shop_floor);
    await reconciled(s, pid);
  }
});

test('INV-06 + INV-19: فروشِ حضوری و سفارشِ آنلاینِ هم‌زمان ⇒ فروشِ ثبت‌شده رد نمی‌شود، رزروِ بی‌پشتوانه وظیفه می‌سازد', async () => {
  const s = await shop('حضوری');
  await enable(s);
  await push(s, [product('mix'), entry('mix-e', 'mix', 2)]);
  const o = await order(s, [line('mix', 2000)]);
  assert.equal(o.status, 201);

  //  هم‌زمان: دستگاه یک فروشِ حضوری می‌فرستد و مشتریِ دیگری سفارش می‌دهد
  const [pushed, other] = await Promise.all([push(s, sale('mix-s1', [['mix-i1', 'mix', 1]])), order(s, [line('mix', 1000)])]);
  assert.ok(pushed.results.every((x) => x.status === 'applied'), 'فروشِ ثبت‌شدهٔ دستگاه پذیرفته شد');
  assert.equal(other.status, 409, 'همهٔ موجودی رزرو بود');

  const st = await stockOf(s, 'mix');
  assert.equal(st.levels.shop_floor, 1000, 'فروشِ حضوری کم شد');
  assert.equal(st.reservedMilli, 2000);
  assert.equal(st.sellableMilli, 0);
  const deviceSale = (await movesOf(s, 'mix')).find((m) => m.source === 'device' && m.type === 'sale');
  assert.ok(deviceSale, 'حرکتِ فروشِ دستگاه در دفتر نیست');
  assert.equal(deviceSale.mode, 'recorded');
  const short = (await openTasks(s)).find((x) => x.kind === 'reservation_short' && x.productId === 'mix');
  assert.ok(short, 'رزروِ بی‌پشتوانه بی‌صدا ماند');
  assert.deepEqual(short.detail.orders, [o.body.order.id]);
  await reconciled(s, 'mix');
});

test('INV-07 + §۳۸.۳: فروشِ آفلاینِ دو دستگاه ⇒ هر دو ثبت، منفی و وظیفه؛ همگام‌سازیِ دوباره دو بار کم نمی‌کند', async () => {
  const s = await shop('دو دستگاه');
  await enable(s);
  await push(s, [product('d1', { minStock: 0 }), entry('d1-e', 'd1', 1)]);
  await stockOf(s, 'd1');

  //  هر دو دستگاه آفلاین آخرین عدد را فروخته‌اند و حالا می‌فرستند
  const saleA = sale('d1-sa', [['d1-ia', 'd1', 1]]);
  await push(s, saleA, { device: `${s.device}-A` });
  await push(s, sale('d1-sb', [['d1-ib', 'd1', 1]]), { device: `${s.device}-B` });
  let st = await stockOf(s, 'd1');
  assert.equal(st.levels.shop_floor, -1000, 'فروشِ ثبت‌شده رد نشد؛ موجودی منفی شد');
  const neg = (await openTasks(s)).find((x) => x.kind === 'negative_stock' && x.productId === 'd1');
  assert.ok(neg, 'وظیفهٔ بررسیِ موجودی ساخته نشد');

  const n = (await movesOf(s, 'd1')).length;
  //  همان opها دوباره (قطعِ شبکه) و خواندنِ کاملِ دوباره ⇒ هیچ حرکتِ تازه‌ای نه
  await push(s, saleA, { device: `${s.device}-A` });
  assert.equal((await h.post('/api/shop/catalog/refresh', { full: true }, { token: s.token })).status, 200);
  assert.equal((await movesOf(s, 'd1')).length, n, 'فروشِ تکراری دوباره کم شد');

  //  برگشتِ یک عدد روی فاکتورِ A و لغوِ فاکتورِ B ⇒ هر کدام یک حرکتِ جدا
  await push(s, [op('update', 'saleItems', 'd1-ia', { returnedQty: 1 })]);
  await push(s, [op('update', 'sales', 'd1-sb', { status: 'cancelled' })]);
  st = await stockOf(s, 'd1');
  assert.equal(st.levels.shop_floor, 1000);
  const types = (await movesOf(s, 'd1')).filter((m) => m.source === 'device').map((m) => `${m.type}:${m.reason}`).sort();
  assert.deepEqual(types, ['customer_return:device_cancel', 'customer_return:device_return',
    'purchase_receipt:device_receipt', 'sale:device_sale', 'sale:device_sale']);
  assert.equal((await h.post('/api/shop/catalog/refresh', { full: true }, { token: s.token })).status, 200);
  assert.equal((await movesOf(s, 'd1')).length, n + 2);
  await reconciled(s, 'd1');
});

test('INV-08: انتقال، خراب، مرجوعی، لغوِ سفارش و دورریز ⇒ رویدادهای جدا با اجازهٔ درست', async () => {
  const s = await shop('رویدادها');
  await enable(s);
  const staff = await member(s, 'staff', 'شاگرد');
  const manager = await member(s, 'manager', 'مدیر');
  await push(s, [product('t1'), entry('t1-e', 't1', 6)]);
  await stockOf(s, 't1');

  assert.equal((await mv(s, { kind: 'transfer', productId: 't1', from: 'shop_floor', to: 'warehouse', qtyMilli: 2000 }, { token: staff.token })).status, 201);
  const denied = await mv(s, { kind: 'damage', productId: 't1', qtyMilli: 1000, reason: 'شکسته' }, { token: staff.token });
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'permission_denied');
  assert.equal((await mv(s, { kind: 'damage', productId: 't1', qtyMilli: 1000, reason: 'شکسته' }, { token: manager.token })).status, 201);
  assert.equal((await mv(s, { kind: 'return', productId: 't1', qtyMilli: 1000, reason: 'مرجوعیِ مشتری' }, { token: manager.token })).status, 201);
  assert.equal((await mv(s, { kind: 'writeoff', productId: 't1', qtyMilli: 1000, reason: 'غیرقابلِ تعمیر' }, { token: manager.token })).status, 201);

  const o = await order(s, [line('t1', 1000)]);
  assert.equal(o.status, 201);
  assert.equal((await go(s, o.body.order.id, { to: 'rejected', reason: 'busy' })).status, 200);
  assert.deepEqual((await heldOf(s, o.body.order.id)).map((x) => [x.status, x.closeReason]), [['released', 'rejected']]);

  const st = await stockOf(s, 't1');
  assert.deepEqual([st.levels.shop_floor, st.levels.warehouse, st.levels.damaged], [4000, 2000, 0]);
  assert.equal(st.sellableMilli, 6000, 'خراب هرگز قابل‌فروش نیست');
  const kinds = (await movesOf(s, 't1')).filter((m) => m.source !== 'device').map((m) => `${m.type}@${m.location}`).sort();
  assert.deepEqual(kinds, ['customer_return@shop_floor', 'damage@damaged', 'release@', 'reserve@',
    'transfer_in@damaged', 'transfer_in@warehouse', 'transfer_out@shop_floor', 'transfer_out@shop_floor']);
  await reconciled(s, 't1');
});

test('INV-10: واحدِ دوم (کارتن = ۲۴ دانه) با ضریب رزرو می‌شود؛ واحدِ ناشناس رد می‌شود', async () => {
  const s = await shop('واحد');
  await enable(s);
  await push(s, [product('u1', { unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24 }), entry('u1-e', 'u1', 48)]);
  const o = await order(s, [line('u1', 1000, { unit: 'کارتن' })]);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  assert.equal((await heldOf(s, o.body.order.id))[0].qtyMilli, 24000);

  const bad = await order(s, [line('u1', 1000, { unit: 'کیلو' })]);
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_unit');
  assert.deepEqual(bad.body.error.units, ['دانه', 'کارتن']);

  const over = await order(s, [line('u1', 2000, { unit: 'کارتن' })]);
  assert.equal(over.status, 409);
  assert.equal(over.body.error.lines[0].availableMilli, 24000);
  assert.equal((await stockOf(s, 'u1')).sellableMilli, 24000);
});

test('INV-11: کاربرِ دکانِ B موجودی، حرکت و رزروِ دکانِ A را نه می‌بیند نه عوض می‌کند', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  await enable(a);
  await enable(b);
  await push(a, [product('iso1', { salePrice: 999 }), entry('iso1-e', 'iso1', 3)]);
  const o = await order(a, [line('iso1', 1000)]);
  assert.equal(o.status, 201);

  assert.equal((await cat(b, '/products/iso1')).status, 404);
  assert.ok(!(await cat(b, '/products')).body.items.some((i) => i.id === 'iso1'));
  assert.deepEqual((await cat(b, '/moves?productId=iso1')).body.items, []);
  assert.deepEqual((await heldOf(b, o.body.order.id)), []);
  const steal = await mv(b, { kind: 'adjust', productId: 'iso1', location: 'shop_floor', qtyMilli: -1000, reason: 'دزدی' });
  assert.equal(steal.status, 404);
  const buy = await order(b, [line('iso1', 1000)]);
  assert.equal(buy.status, 409);
  assert.equal(buy.body.error.code, 'product_unavailable');
  assert.equal((await stockOf(a, 'iso1')).levels.shop_floor, 3000, 'موجودیِ A دست نخورد');
});

test('INV-12: اصلاحِ دستی بی دلیل یا بی اجازه رد می‌شود؛ اصلاحِ مجاز کاربر، دلیل و زمان دارد', async () => {
  const s = await shop('اصلاح');
  await enable(s);
  const staff = await member(s, 'staff', 'شاگردِ اصلاح');
  const manager = await member(s, 'manager', 'مدیرِ اصلاح');
  await push(s, [product('a1'), entry('a1-e', 'a1', 5)]);
  await stockOf(s, 'a1');

  const noReason = await mv(s, { kind: 'adjust', productId: 'a1', location: 'shop_floor', qtyMilli: -2000 }, { token: manager.token });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  assert.equal((await mv(s, { kind: 'adjust', productId: 'a1', location: 'shop_floor', qtyMilli: -2000, reason: 'گم‌شده' }, { token: staff.token })).status, 403);
  const tooMuch = await mv(s, { kind: 'adjust', productId: 'a1', location: 'shop_floor', qtyMilli: -9000, reason: 'گم‌شده' }, { token: manager.token });
  assert.equal(tooMuch.status, 409);
  assert.equal(tooMuch.body.error.code, 'insufficient_stock');
  assert.equal(tooMuch.body.error.availableMilli, 5000);

  const t0 = Date.now();
  const ok = await mv(s, { kind: 'adjust', productId: 'a1', location: 'shop_floor', qtyMilli: -2000, reason: 'گم‌شده' }, { token: manager.token });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  const m = ok.body.moves[0];
  assert.equal(m.type, 'adjustment');
  assert.equal(m.actorId, manager.user.id);
  assert.equal(m.reason, 'گم‌شده');
  assert.ok(m.at >= t0 && m.createdAt >= t0);
  assert.equal((await stockOf(s, 'a1')).levels.shop_floor, 3000);

  //  شمارشِ اصلاحی (§۳۸.۳): شمرده‌شده − موجودی ⇒ یک حرکت؛ همان عدد دوباره ⇒ بی‌حرکت
  const counted = await mv(s, { kind: 'count', productId: 'a1', location: 'shop_floor', countedMilli: 7000, reason: 'شمارشِ ماهانه' }, { token: manager.token });
  assert.equal(counted.status, 201);
  assert.deepEqual(counted.body.moves.map((x) => [x.type, x.qtyMilli]), [['count_adjust', 4000]]);
  const same = await mv(s, { kind: 'count', productId: 'a1', location: 'shop_floor', countedMilli: 7000, reason: 'شمارشِ دوباره' }, { token: manager.token });
  assert.equal(same.status, 200);
  assert.equal(same.body.unchanged, true);
  await reconciled(s, 'a1');
});

test('INV-13: کالای غیرفعال، حذف‌شده یا ناشناس در ثبتِ سفارش رد می‌شود', async () => {
  const s = await shop('غیرفعال');
  await enable(s);
  await push(s, [product('in1'), entry('in1-e', 'in1', 5), product('dl1'), entry('dl1-e', 'dl1', 5)]);
  await push(s, [op('update', 'products', 'in1', { active: false }), op('delete', 'products', 'dl1')]);
  for (const pid of ['in1', 'dl1', 'ghost']) {
    const r = await order(s, [line(pid, 1000)]);
    assert.equal(r.status, 409, pid);
    assert.equal(r.body.error.code, 'product_unavailable');
    assert.deepEqual(r.body.error.productIds, [pid]);
  }
  const pub = await catalog.publicStock(s.shopId, ['in1', 'dl1', 'ghost']);
  assert.deepEqual([...pub.values()].map((x) => x.status), ['unavailable', 'unavailable', 'unavailable']);
});

test('INV-14: دستگاهِ کهنه یا صفِ نفرستاده ⇒ «در حال بررسی»، هرگز «موجود»', async () => {
  const s = await shop('کهنه');
  await enable(s);
  await push(s, [product('st1'), entry('st1-e', 'st1', 5)]);
  assert.equal((await stockOf(s, 'st1')).badge.status, 'in_stock');
  assert.equal((await settings(s, { staleAfterSec: 60 })).status, 200);

  //  دستگاه ده دقیقه است دیده نشده (برق رفته)
  await query(`UPDATE sync_devices SET last_seen_at = $2 WHERE app='shop' AND account_id=$1`, [s.shopId, Date.now() - 600_000]);
  let b = (await stockOf(s, 'st1')).badge;
  assert.equal(b.status, 'unverified');
  assert.equal(b.reason, 'stale');
  assert.equal((await catalog.publicStock(s.shopId, ['st1'])).get('st1').status, 'unverified');
  //  ۳۲.۲ بندِ ۴: سرور آنلاین است ⇒ سفارش طبقِ موجودیِ سرور پذیرفته می‌شود (هنوز «جدید»، نه قطعی)
  const o = await order(s, [line('st1', 1000)]);
  assert.equal(o.status, 201);
  assert.equal(o.body.order.status, 'new');

  //  دستگاه برگشت ولی هنوز ۳ عملیاتِ نفرستاده دارد
  await push(s, [], { queued: 3 });
  b = (await stockOf(s, 'st1')).badge;
  assert.equal(b.status, 'unverified');
  assert.equal(b.reason, 'pending_sync');
  await push(s, [], { queued: 0 });
  assert.equal((await stockOf(s, 'st1')).badge.status, 'in_stock');
});

test('INV-15: خرید، لغو و حرکتِ دستیِ تکراری با همان کلید اثرِ دوم ندارد', async () => {
  const s = await shop('تکرار');
  await enable(s);
  await push(s, [product('k1'), entry('k1-e', 'k1', 5)]);
  const key = fresh();
  const first = await order(s, [line('k1', 2000)], key);
  const again = await order(s, [line('k1', 2000)], key);
  assert.equal(first.status, 201);
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(await count('stock_reservations', s.shopId), 1);

  const id = first.body.order.id;
  assert.equal((await go(s, id, { to: 'cancelled_by_customer' })).status, 200);
  const twice = await go(s, id, { to: 'cancelled_by_customer' });
  assert.equal(twice.status, 200);
  assert.equal(twice.body.unchanged, true);
  assert.equal((await movesOf(s, 'k1')).filter((m) => m.type === 'release').length, 1);

  const mkey = fresh();
  const body = { kind: 'transfer', productId: 'k1', from: 'shop_floor', to: 'warehouse', qtyMilli: 1000 };
  assert.equal((await mv(s, body, { key: mkey })).status, 201);
  const replay = await mv(s, body, { key: mkey });
  assert.equal(replay.status, 200);
  assert.equal(replay.body.replayed, true);
  const reused = await mv(s, { ...body, qtyMilli: 2000 }, { key: mkey });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  assert.equal((await stockOf(s, 'k1')).levels.warehouse, 1000);
  await reconciled(s, 'k1');
});

test('CHK-18: رزرو مهلت دارد؛ سررسید ⇒ آزاد؛ قبول ⇒ بی‌مهلت یا ۴۰۹ اگر موجودی رفته', async () => {
  const s = await shop('مهلت');
  await enable(s);
  assert.equal((await settings(s, { reserveTtlSec: 60 })).status, 200);
  await push(s, [product('ex1'), entry('ex1-e', 'ex1', 1)]);
  const t0 = Date.now();
  const a = await order(s, [line('ex1', 1000)]);
  assert.equal(a.status, 201);
  const [ha] = await heldOf(s, a.body.order.id);
  assert.ok(ha.expiresAt >= t0 + 60_000 && ha.expiresAt <= Date.now() + 60_000);
  assert.equal((await order(s, [line('ex1', 1000)])).status, 409);

  assert.equal(await stock.sweepExpired(Date.now() + 61_000, { shopId: s.shopId }), 1);
  assert.deepEqual((await heldOf(s, a.body.order.id)).map((x) => [x.status, x.closeReason]), [['expired', 'expired']]);
  const b = await order(s, [line('ex1', 1000)]);
  assert.equal(b.status, 201, 'پس از آزادسازی دوباره قابلِ سفارش است');

  const acceptA = await go(s, a.body.order.id, { to: 'accepted' });
  assert.equal(acceptA.status, 409);
  assert.equal(acceptA.body.error.code, 'out_of_stock');
  const stillNew = await h.get(`/api/shop/orders/${a.body.order.id}`, { token: s.token });
  assert.equal(stillNew.body.order.status, 'new', 'قبولِ بی‌موجودی برگشت');
  assert.equal((await go(s, a.body.order.id, { to: 'rejected', reason: 'out_of_stock' })).status, 200);

  assert.equal((await go(s, b.body.order.id, { to: 'accepted' })).status, 200);
  assert.equal((await heldOf(s, b.body.order.id))[0].expiresAt, null);
  assert.equal(await stock.sweepExpired(Date.now() + 36_000_000, { shopId: s.shopId }), 0, 'سفارشِ قبول‌شده منقضی نمی‌شود');
  await reconciled(s, 'ex1');
});

test('تحویل ⇒ کسرِ قطعی از قفسه و آزادیِ رزرو در یک تراکنش؛ تکرارش بی‌اثر', async () => {
  const s = await shop('تحویل');
  await enable(s);
  await push(s, [product('c1'), entry('c1-e', 'c1', 3)]);
  const o = await order(s, [line('c1', 2000)]);
  assert.equal(o.status, 201);
  const id = o.body.order.id;
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const r = await go(s, id, { to });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
  assert.deepEqual((await heldOf(s, id)).map((x) => x.status), ['committed']);
  let st = await stockOf(s, 'c1');
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli, st.sellableMilli], [1000, 0, 1000]);
  const sold = (await movesOf(s, 'c1')).filter((m) => m.source === 'order' && m.type === 'sale');
  assert.deepEqual(sold.map((m) => [m.location, m.qtyMilli, m.refId]), [['shop_floor', 2000, id]]);
  assert.equal((await go(s, id, { to: 'picked_up' })).body.unchanged, true);
  st = await stockOf(s, 'c1');
  assert.equal(st.levels.shop_floor, 1000);
  await reconciled(s, 'c1');
});

test('موجودیِ کم (§۳۲.۲ بندِ ۷): آستانهٔ کالا از دستگاه و آستانهٔ هر مکان ⇒ وظیفه برای مدیر', async () => {
  const s = await shop('کم');
  await enable(s);
  await push(s, [product('lo1', { minStock: 3 }), entry('lo1-e', 'lo1', 4)]);
  await stockOf(s, 'lo1');
  assert.ok(!(await openTasks(s)).some((x) => x.kind === 'low_stock'));
  await push(s, sale('lo1-s', [['lo1-i', 'lo1', 1]]));
  await stockOf(s, 'lo1');
  const t = (await openTasks(s)).find((x) => x.kind === 'low_stock' && x.productId === 'lo1' && !x.location);
  assert.ok(t, 'وظیفهٔ موجودیِ کم ساخته نشد');
  assert.deepEqual([t.detail.freeMilli, t.detail.thresholdMilli], [3000, 3000]);

  const setLow = await h.put('/api/shop/catalog/products/lo1/low', { location: 'shop_floor', lowMilli: 5000 }, { token: s.token });
  assert.equal(setLow.status, 200);
  await mv(s, { kind: 'transfer', productId: 'lo1', from: 'shop_floor', to: 'warehouse', qtyMilli: 1000 });
  assert.ok((await openTasks(s)).some((x) => x.kind === 'low_stock' && x.location === 'shop_floor'));

  const resolve = await h.post(`/api/shop/catalog/tasks/${t.id}/resolve`, { action: 'resolved', note: 'سفارشِ خرید داده شد' }, { token: s.token });
  assert.equal(resolve.status, 200);
  assert.ok(!(await openTasks(s)).some((x) => x.id === t.id));
});

test('Sync v3: با منبعِ v3 کالا و موجودی از sync_v3_rows (مقدار و پولِ صحیح) خوانده می‌شود', async () => {
  const s = await shop('نسخهٔ سه');
  await enable(s);
  assert.equal((await settings(s, { syncSource: 'v3' })).status, 200);
  let tick = 0;
  const H = () => hlc.format({ wall: Date.now() - 60_000 + (++tick) * 10, counter: 0, node: 'v3dev' });
  const oid = () => `01JV3CAT${String(++opN).padStart(18, '0')}`;
  const r = await h.api('POST', '/api/sync/v3/push', {
    token: s.token,
    body: {
      device_id: 'v3-cat-dev', protocol: 3, ops: [
        { op_id: oid(), table: 'products', row_id: 'v3p', kind: 'insert', hlc: H(), fields: { name: 'چای', unit: 'بسته', salePriceMinor: 9900 } },
        { op_id: oid(), table: 'warehouseEntries', row_id: 'v3e', kind: 'insert', hlc: H(), fields: { productId: 'v3p', unitsMilli: 4000 } },
      ],
    },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const p = (await cat(s, '/products/v3p')).body.product;
  assert.equal(p.name, 'چای');
  assert.equal(p.priceMinor, 9900);
  assert.equal(p.stock.levels.shop_floor, 4000);
  await reconciled(s, 'v3p');
});

test('ارسال (همان گذارهایی که پیکِ #249 از transitionIn می‌زند): تحویل ⇒ کسر؛ برگشت به دکان و بازگشت به قفسه ⇒ آزادی', async () => {
  const s = await shop('ارسال');
  await enable(s);
  await push(s, [product('dv1'), entry('dv1-e', 'dv1', 3)]);
  const send = (qty) => h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'مشتری', phone: '0791234567' }, address: { landmark: 'کنارِ مسجد' }, lines: [line('dv1', qty)],
  }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  const a = await send(1000);
  const b = await send(1000);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(b.status, 201);
  for (const to of ['accepted', 'picking', 'packed', 'out_for_delivery']) {
    assert.equal((await go(s, a.body.order.id, { to })).status, 200, to);
    assert.equal((await go(s, b.body.order.id, { to })).status, 200, to);
  }
  assert.equal((await go(s, a.body.order.id, { to: 'delivered' })).status, 200);
  for (const [to, extra] of [['delivery_failed', { reason: 'no_answer' }], ['retry_scheduled', {}],
    ['returned_to_store', {}], ['restocked', {}]]) {
    const r = await go(s, b.body.order.id, { to, ...extra });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
  assert.deepEqual((await heldOf(s, a.body.order.id)).map((x) => x.status), ['committed']);
  assert.deepEqual((await heldOf(s, b.body.order.id)).map((x) => [x.status, x.closeReason]), [['released', 'restocked']]);
  const st = await stockOf(s, 'dv1');
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli, st.sellableMilli], [2000, 0, 2000]);
  await reconciled(s, 'dv1');
});

// ════════════════════════════════════════════════════════════════════
//  QA #263 — سفارشِ آزمایشی، نخستین خواندنِ دفتر، خواندنِ کامل
// ════════════════════════════════════════════════════════════════════

test('QA #263: سفارشِ آزمایشیِ کارمند نه رزرو می‌کند نه فروشِ قطعی؛ رزروِ ماندهٔ قدیمی آزاد می‌شود، کسر نه', async () => {
  const s = await shop('آزمایشیِ موجودی');
  await enable(s);
  await push(s, [product('t1'), entry('t1-e', 't1', 3)]);
  await stockOf(s, 't1');
  const made = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'آزمونِ کارمند' }, test: true,
    lines: [line('t1', 2000)],
  }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.order.isTest, true);
  const id = made.body.order.id;
  assert.deepEqual(await heldOf(s, id), [], 'سفارشِ آزمایشی رزرو نمی‌کند');
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const r = await go(s, id, { to });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
  assert.deepEqual(await heldOf(s, id), []);
  const st = await stockOf(s, 't1');
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli], [3000, 0], 'موجودیِ سرور = موجودیِ دستگاه');
  assert.equal((await movesOf(s, 't1')).filter((m) => m.refId === id).length, 0, 'هیچ حرکتِ فروشی برای سفارشِ آزمایشی');

  //  رزروی که پیش از این اصلاح برای سفارشِ آزمایشی گرفته شده بود ⇒ در پایان آزاد، نه کسرِ قطعی
  const old = await order(s, [line('t1', 1000)]);
  assert.equal(old.status, 201, JSON.stringify(old.body));
  assert.deepEqual((await heldOf(s, old.body.order.id)).map((x) => x.status), ['held']);
  await query('UPDATE online_orders SET is_test=true WHERE id=$1', [old.body.order.id]);
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    assert.equal((await go(s, old.body.order.id, { to })).status, 200, to);
  }
  assert.deepEqual((await heldOf(s, old.body.order.id)).map((x) => x.status), ['released']);
  const after = await stockOf(s, 't1');
  assert.deepEqual([after.levels.shop_floor, after.reservedMilli], [3000, 0]);
  assert.equal((await movesOf(s, 't1')).filter((m) => m.type === 'sale' && m.source === 'order').length, 0);
  await reconciled(s, 't1');

  //  سفارشِ واقعی همان رفتارِ پیشین را دارد
  const real = await order(s, [line('t1', 1000)]);
  assert.equal(real.status, 201);
  assert.deepEqual((await heldOf(s, real.body.order.id)).map((x) => [x.qtyMilli, x.status]), [[1000, 'held']]);
});

test('QA #263: نخستین خواندنِ دفتر بیرون از تراکنشِ سفارش است — ردِ سفارشِ نخست آن را برنمی‌گرداند', async () => {
  const s = await shop('نخستین خواندن');
  await enable(s);
  await push(s, [product('f0', { name: 'نمک' }), product('f1'), entry('f1-e', 'f1', 2)]);
  assert.equal(await count('catalog_sync_state', s.shopId), 0, 'هنوز هیچ خواندنی');
  //  نخستین سفارشِ مشتری کمبود دارد ⇒ ۴۰۹ و تراکنشِ سفارش برمی‌گردد …
  const o = await order(s, [line('f0', 1000)]);
  assert.equal(o.status, 409, JSON.stringify(o.body));
  assert.equal(o.body.error.code, 'out_of_stock');
  //  … ولی خواندنِ کامل در تراکنشِ خودش ثبت شده بود (پیش از این اصلاح با سفارش برمی‌گشت)
  assert.equal(await count('catalog_sync_state', s.shopId), 1);
  assert.equal(await count('catalog_products', s.shopId), 2);
  assert.equal(await count('online_orders', s.shopId), 0);
  //  سفارشِ بعدی فقط تفاوت را می‌خواند و درست رزرو می‌کند
  const ok = await order(s, [line('f1', 2000)]);
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.deepEqual((await heldOf(s, ok.body.order.id)).map((x) => [x.productId, x.qtyMilli]), [['f1', 2000]]);
  //  پرچمِ خاموش ⇒ prepare هیچ نمی‌کند
  const off = await shop('نخستین خاموش');
  assert.equal(await catalog.prepare(off.shopId), null);
  assert.equal(await count('catalog_sync_state', off.shopId), 0);
  await reconciled(s, 'f1');
});

test('QA #263: خواندنِ کاملِ دفتر (full) فقط مالک و مدیر؛ خواندنِ تفاوت برای هر دارندهٔ stock.move', async () => {
  const s = await shop('خواندنِ کامل');
  await enable(s);
  const staff = await member(s, 'staff', 'شاگردِ خواندن');
  const manager = await member(s, 'manager', 'مدیرِ خواندن');
  await push(s, [product('r1'), entry('r1-e', 'r1', 4)]);
  const refresh = (token, body) => h.post('/api/shop/catalog/refresh', body, { token });

  const denied = await refresh(staff.token, { full: true });
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  assert.equal(denied.body.error.code, 'full_refresh_forbidden');
  assert.equal(await count('catalog_sync_state', s.shopId), 0, 'ردشده چیزی نخواند');
  assert.equal((await refresh(staff.token, {})).status, 200, 'خواندنِ تفاوت برای شاگرد باز است');
  assert.equal((await refresh(manager.token, { full: true })).status, 200);
  const own = await refresh(s.token, { full: true });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.equal((await stockOf(s, 'r1')).levels.shop_floor, 4000, 'خواندنِ دوباره دو بار نمی‌شمارد');
  await reconciled(s, 'r1');
});
