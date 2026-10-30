'use strict';
/**
 * M5 — رزروِ نرمِ موجودی برای سفارشِ آنلاین (قراردادِ بازار §۱.۴، §۱.۷، ردیفِ M5 ِ §۳؛ v3 §۳۲.۲ بندِ ۲؛ INV-19).
 *
 * از راهِ واقعی: کالا و فروش با `POST /api/sync/v1/push`، سفارش با `POST /api/shop/orders`، گذار با
 * `…/transition`. هم‌زمانی با درخواست‌های واقعاً هم‌زمان روی همان سرور (استخرِ PostgreSQL ⇒ تراکنش‌های
 * جدا؛ PGlite ⇒ همان صف). فقط «گذشتِ زمان» با جابه‌جا کردنِ `expires_at` شبیه‌سازی می‌شود.
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ `shop.stock_reserve` خاموش ⇒ همان رفتارِ امروز (بی رزرو، دو سفارشِ آخرین عدد هر دو ۲۰۱).
 *   ⛔ روشن و تازه: دو مشتریِ هم‌زمانِ آخرین عدد ⇒ دقیقاً یکی ۲۰۱، دیگری ۴۰۹ `out_of_stock` با پیامِ روشن.
 *   ⛔ تکرارِ همان کلید دو بار رزرو نمی‌کند؛ لغو/رد آزاد می‌کند؛ انقضا آزاد می‌کند (تنبل + جارو).
 *   ⛔ فروشِ حضوری هرگز رد نمی‌شود؛ اگر رزرو را برد، سفارش به بررسی می‌رود (INV-19).
 *   ⛔ موجودیِ کهنه ⇒ بی رزرو. دفتر = رزروها (INV-16).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now, tx } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const catalog = require('../src/lib/catalog');
const reserve = require('../src/lib/stock-reserve');

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

// ── کمکی‌ها (همان شکلِ catalog-stock.test.js) ─────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-r-${made.body.shop.id}`.slice(0, 60) };
}
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function enable(s, { reserve: on = true } = {}) {
  await live(s.shopId, 'shop.orders', true);
  await live(s.shopId, 'shop.catalog', true);
  if (on) await live(s.shopId, 'shop.stock_reserve', true);
}

let opN = 0;
const opId = () => `01JRS${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  return r.body;
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

let keyN = 0;
const fresh = () => `stock-rsv-${++keyN}-${Date.now()}`;
const line = (pid, qtyMilli) => ({ productId: pid, name: `کالای ${pid}`, unit: 'دانه', qtyMilli, unitPriceMinor: 5000 });
const order = (s, lines, key = fresh()) => h.post('/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری' }, lines,
}, { token: s.token, headers: { 'Idempotency-Key': key } });
const go = (s, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
const getOrder = (s, id) => h.get(`/api/shop/orders/${id}`, { token: s.token });
const cat = (s, p) => h.get(`/api/shop/catalog${p}`, { token: s.token });

async function holds(s, status = 'held') {
  return (await query(
    `SELECT * FROM stock_reservations WHERE shop_id=$1 AND status=$2 ORDER BY created_at, id`, [s.shopId, status]
  )).rows;
}
async function reconciled(s, pid) {
  const all = await cat(s, '/report');
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.equal(all.body.ok, true, JSON.stringify(all.body.mismatches));
  const r = await cat(s, `/report?productId=${pid}`);
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
async function sellable(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock;
}

// ════════════════════════════════════════════════════════════════════

test('⛔ shop.stock_reserve خاموش: همان رفتارِ امروز — بی رزرو، دو سفارشِ آخرین عدد هر دو ۲۰۱، /config بی reserve', async () => {
  const s = await shop('رزرو-خاموش');
  await enable(s, { reserve: false });
  await push(s, [product('f1'), entry('f1-e', 'f1', 1)]);
  const [a, b] = await Promise.all([order(s, [line('f1', 1000)]), order(s, [line('f1', 1000)])]);
  assert.deepEqual([a.status, b.status], [201, 201]);
  assert.equal(a.body.order.stockHold, undefined);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [s.shopId])).n), 0);
  assert.equal((await cat(s, '/config')).body.reserve, undefined);
  //  مهلت بی پرچم مثلِ پیش نادیده
  const set = await h.put('/api/shop/catalog/settings', { reserveTtlSec: 60 }, { token: s.token });
  assert.equal(set.status, 200);
  assert.equal(set.body.settings.reserveTtlSec, undefined);
});

test('M5: دو مشتریِ هم‌زمان روی آخرین عدد ⇒ دقیقاً یکی موفق، دیگری ۴۰۹ با پیامِ روشن؛ دفتر = رزرو', async () => {
  const s = await shop('رزرو-هم‌زمان');
  await enable(s);
  await push(s, [product('r1'), entry('r1-e', 'r1', 1)]);
  const rs = await Promise.all([order(s, [line('r1', 1000)]), order(s, [line('r1', 1000)])]);
  const codes = rs.map((r) => r.status).sort();
  assert.deepEqual(codes, [201, 409], JSON.stringify(rs.map((r) => r.body)));
  const lost = rs.find((r) => r.status === 409);
  assert.equal(lost.body.error.code, 'out_of_stock');
  assert.match(lost.body.error.message, /برای سفارشِ دیگری نگه داشته شده/);
  const won = rs.find((r) => r.status === 201).body.order;
  assert.equal(won.stockHold.status, 'held');
  assert.equal(won.stockHold.review, false);
  assert.ok(won.stockHold.expiresAt > Date.now());
  assert.equal((await holds(s)).length, 1);
  const st = await sellable(s, 'r1');
  assert.equal(st.reservedMilli, 1000);
  assert.equal(st.sellableMilli, 0);
  assert.equal((await catalog.publicStock(s.shopId, ['r1'])).get('r1').status, 'out_of_stock');
  const cfg = (await cat(s, '/config')).body;
  assert.deepEqual(cfg.reserve, { enabled: true, ttlSec: 1800 });
  await reconciled(s, 'r1');
});

test('M5: پنج درخواستِ هم‌زمان روی دو عدد ⇒ دقیقاً دو موفق (بی فروشِ بیش از موجودی)', async () => {
  const s = await shop('رزرو-پنج');
  await enable(s);
  await push(s, [product('r5'), entry('r5-e', 'r5', 2)]);
  const rs = await Promise.all([1, 2, 3, 4, 5].map(() => order(s, [line('r5', 1000)])));
  assert.equal(rs.filter((r) => r.status === 201).length, 2, JSON.stringify(rs.map((r) => r.status)));
  assert.equal(rs.filter((r) => r.status === 409).length, 3);
  assert.equal((await holds(s)).reduce((a, r) => a + Number(r.qty_milli), 0), 2000);
  await reconciled(s, 'r5');
});

test('M5: تکرارِ همان کلید دو بار رزرو نمی‌کند؛ رد/لغو آزاد می‌کند و مشتریِ بعدی می‌گذرد', async () => {
  const s = await shop('رزرو-تکرار');
  await enable(s);
  await push(s, [product('i1'), entry('i1-e', 'i1', 1)]);
  const key = fresh();
  const [a, b] = await Promise.all([order(s, [line('i1', 1000)], key), order(s, [line('i1', 1000)], key)]);
  assert.deepEqual([a.status, b.status].sort(), [200, 201].sort().map((x) => x), JSON.stringify([a.body, b.body]));
  assert.equal(a.body.order.id, b.body.order.id);
  assert.equal((await holds(s)).length, 1, 'تکرار ⇒ یک رزرو');
  assert.equal((await order(s, [line('i1', 1000)])).status, 409);

  assert.equal((await go(s, a.body.order.id, { to: 'rejected', reason: 'busy' })).status, 200);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await holds(s, 'released')).length, 1);
  const c = await order(s, [line('i1', 1000)]);
  assert.equal(c.status, 201, JSON.stringify(c.body));
  //  قبول ⇒ بی انقضا؛ لغوِ پس از قبول ⇒ آزاد
  assert.equal((await go(s, c.body.order.id, { to: 'accepted' })).status, 200);
  assert.equal((await holds(s))[0].expires_at, null);
  assert.equal((await go(s, c.body.order.id, { to: 'cancelled', reason: 'customer_request' })).status, 200);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await getOrder(s, c.body.order.id)).body.order.stockHold.status, 'released');
  await reconciled(s, 'i1');
});

test('M5: انقضا موجودی را آزاد می‌کند — تنبل هنگامِ ساخت و جاروی دوره‌ای؛ مهلت از تنظیم', async () => {
  const s = await shop('رزرو-انقضا');
  await enable(s);
  const set = await h.put('/api/shop/catalog/settings', { reserveTtlSec: 120 }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.equal(set.body.settings.reserveTtlSec, 120);
  assert.equal((await h.put('/api/shop/catalog/settings', { reserveTtlSec: 5 }, { token: s.token })).status, 400);
  await push(s, [product('e1'), entry('e1-e', 'e1', 1)]);
  const t0 = Date.now();
  const a = await order(s, [line('e1', 1000)]);
  assert.equal(a.status, 201);
  const exp = Number((await holds(s))[0].expires_at);
  assert.ok(exp >= t0 + 120_000 && exp <= Date.now() + 120_000, 'expires_at = اکنون + مهلت');
  assert.equal((await order(s, [line('e1', 1000)])).status, 409);

  //  زمان گذشت ⇒ آزادِ تنبل: مشتریِ بعدی می‌گذرد، رزروِ پیشین «منقضی» در ردیف و دفتر
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE shop_id=$1 AND status='held'`, [s.shopId, Date.now() - 1]);
  assert.equal((await getOrder(s, a.body.order.id)).body.order.stockHold.status, 'expired');
  //  خواندن (کارمند و ویترین) پیش از هر جارو، رزروِ گذشته را نمی‌شمارد
  assert.equal((await sellable(s, 'e1')).sellableMilli, 1000);
  assert.equal((await catalog.publicStock(s.shopId, ['e1'])).get('e1').status, 'in_stock');
  const b = await order(s, [line('e1', 1000)]);
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.equal((await holds(s, 'expired')).length, 1);
  await reconciled(s, 'e1');

  //  جارو: رزروِ گذشته بی هیچ درخواستی بسته می‌شود
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE shop_id=$1 AND status='held'`, [s.shopId, Date.now() - 1]);
  assert.ok((await reserve.sweep()) >= 1);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await holds(s, 'expired')).length, 2);
  assert.equal(await reserve.sweep(), 0, 'دورِ دوم هیچ');
  await reconciled(s, 'e1');
});

test('⛔ INV-19: فروشِ حضوری روی موجودیِ رزروشده هرگز رد نمی‌شود؛ سفارش به بررسی می‌رود، نه فروش', async () => {
  const s = await shop('رزرو-حضوری');
  await enable(s);
  await push(s, [product('p1'), entry('p1-e', 'p1', 2)]);
  const a = await order(s, [line('p1', 1000)]);
  const b = await order(s, [line('p1', 1000)]);
  assert.deepEqual([a.status, b.status], [201, 201]);
  //  صندوق هر دو عدد را فروخت (آفلاین/حضوری) ⇒ push پذیرفته
  await push(s, sale('p1-s', [['p1-i', 'p1', 2]]));
  assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
  const st = await sellable(s, 'p1');
  assert.equal(st.levels.shop_floor, 0, 'فروش نشست');
  const da = (await getOrder(s, a.body.order.id)).body.order.stockHold;
  const db = (await getOrder(s, b.body.order.id)).body.order.stockHold;
  assert.deepEqual([da.review, db.review], [true, true]);
  const tasks = (await cat(s, '/tasks?status=open')).body.tasks.filter((t) => t.kind === 'reservation_short');
  assert.equal(tasks.length, 1);
  assert.deepEqual([...tasks[0].detail.orderIds].sort(), [a.body.order.id, b.body.order.id].sort());
  //  ورودِ یک عدد ⇒ قدیمی‌ترین سفارش دوباره پوشش دارد، تازه‌ترین هنوز در بررسی
  await push(s, [entry('p1-e2', 'p1', 1)]);
  assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
  assert.equal((await getOrder(s, a.body.order.id)).body.order.stockHold.review, false);
  assert.equal((await getOrder(s, b.body.order.id)).body.order.stockHold.review, true);
  //  دکان سفارشِ بی‌پوشش را رد می‌کند ⇒ آزاد و بررسی تمام
  assert.equal((await go(s, b.body.order.id, { to: 'rejected', reason: 'out_of_stock' })).status, 200);
  assert.equal((await holds(s)).length, 1);
  await reconciled(s, 'p1');
});

test('M5: فروشِ تحویلِ همان سفارش (o-<سفارش>-<قلم>) از دستگاه ⇒ رزرو «انجام‌شده»، بی دو بار شمردن', async () => {
  const s = await shop('رزرو-تحویل');
  await enable(s);
  await push(s, [product('d1'), entry('d1-e', 'd1', 3)]);
  const a = await order(s, [line('d1', 2000)]);
  assert.equal(a.status, 201);
  assert.equal((await sellable(s, 'd1')).sellableMilli, 1000);
  assert.equal((await go(s, a.body.order.id, { to: 'accepted' })).status, 200);
  const oid = a.body.order.id;
  await push(s, sale(`o-${oid}`, [[`o-${oid}-1`, 'd1', 2]]));
  assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
  const st = await sellable(s, 'd1');
  assert.equal(st.levels.shop_floor, 1000);
  assert.equal(st.reservedMilli, 0);
  assert.equal(st.sellableMilli, 1000, 'یک عدد باقی، نه منفی و نه دو بار کم');
  assert.equal((await holds(s, 'committed')).length, 1);
  assert.equal((await getOrder(s, oid)).body.order.stockHold.status, 'committed');
  await reconciled(s, 'd1');
});

test('M5: موجودیِ کهنه ⇒ بی رزرو (رفتارِ امروز)؛ سفارشِ آزمایشی هرگز', async () => {
  const s = await shop('رزرو-کهنه');
  await enable(s);
  await push(s, [product('k1'), entry('k1-e', 'k1', 1)]);
  await query(`UPDATE sync_devices SET last_seen_at = $2 WHERE app='shop' AND account_id=$1`, [s.shopId, Date.now() - 3_600_000]);
  const [a, b] = await Promise.all([order(s, [line('k1', 1000)]), order(s, [line('k1', 1000)])]);
  assert.deepEqual([a.status, b.status], [201, 201]);
  assert.equal(a.body.order.stockHold, undefined);
  assert.equal((await holds(s)).length, 0);
});

test('⛔ کنار گذاشتنِ موجودیِ سفارش‌محورِ پیشین (retire) به رزروِ نرم دست نمی‌زند', async () => {
  const s = await shop('رزرو-retire');
  await enable(s);
  await push(s, [product('l1'), entry('l1-e', 'l1', 2)]);
  assert.equal((await order(s, [line('l1', 1000)])).status, 201);
  const out = await tx((c) => catalog.retireOrderStock(c, s.shopId, Date.now()));
  assert.equal(out.changed, false);
  assert.equal((await holds(s)).length, 1);
  await reconciled(s, 'l1');
});
