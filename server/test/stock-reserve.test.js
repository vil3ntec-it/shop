'use strict';
/**
 * O-06 = ب (ORD-20، CHK-18): نگه‌داشتنِ کوتاهِ موجودی برای سفارشِ آنلاین تا دکان قبول کند، پشتِ پرچمِ
 * خاموشِ `shop.stock_reserve` (با `shop.catalog`) — `lib/stock-reserve.js`، مهاجرتِ ۴۳۵.
 *
 * از راهِ واقعی: کالا و فروش با `POST /api/sync/v1/push`، سفارش با `POST /api/shop/orders`، گذار با
 * `…/transition`. هم‌زمانی با درخواست‌های واقعاً هم‌زمان روی همان سرور (PostgreSQL ⇒ تراکنش‌های جدا؛
 * PGlite ⇒ همان صف). فقط «گذشتِ زمان» با جابه‌جا کردنِ `expires_at` شبیه‌سازی می‌شود.
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ پرچم خاموش ⇒ همان رفتارِ امروز (بی رزرو؛ دو سفارشِ آخرین عدد هر دو ۲۰۱).
 *   ⛔ روشن و تازه: دو سفارشِ هم‌زمانِ آخرین عدد ⇒ دقیقاً یکی ۲۰۱، دیگری ۴۰۹ `out_of_stock`.
 *   ⛔ «کسرِ قطعی» (ORD-20) فقط فروشِ تحویلِ برنامه (`o-<سفارش>-<قلم>`) است و یک بار: رزرو تا همان لحظه
 *      نگه داشته می‌شود و در **همان تراکنش** «انجام‌شده» می‌شود؛ تکرارِ همگام‌سازی، دو بار کلیک و تکرارِ
 *      کلیدِ یکتایی اثرِ دوم ندارند.
 *   ⛔ انقضا آزاد می‌کند (تنبل + جارو)؛ رد/لغو/برگشت آزاد می‌کند؛ قبولِ پس از انقضا یا دوباره نگه
 *      می‌دارد یا ۴۰۹.
 *   ⛔ فروشِ حضوری هرگز رد نمی‌شود؛ اگر رزرو را برد، سفارش به بررسی می‌رود (INV-19).
 *   ⛔ دکان‌ها جدا؛ موجودیِ کهنه یا سفارشِ آزمایشی ⇒ بی رزرو. دفتر = رزروها (INV-16).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now, tx } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

let admin = '';
const lib = () => require('../src/lib/stock-reserve');
const catalog = () => require('../src/lib/catalog');

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

// ── کمکی‌ها (همان شکلِ catalog-accept.test.js) ─────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { token: s.accessToken, shopId: made.body.shop.id, device: `dev-r-${made.body.shop.id}`.slice(0, 60) };
}
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function enable(s, { reserve = true } = {}) {
  await live(s.shopId, 'shop.orders', true);
  await live(s.shopId, 'shop.catalog', true);
  if (reserve) await live(s.shopId, 'shop.stock_reserve', true);
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
const order = (s, lines, key = fresh(), extra = {}) => h.post('/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری' }, lines, ...extra,
}, { token: s.token, headers: { 'Idempotency-Key': key } });
const go = (s, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
const getOrder = (s, id) => h.get(`/api/shop/orders/${id}`, { token: s.token });
const cat = (s, p) => h.get(`/api/shop/catalog${p}`, { token: s.token });
const refresh = async (s) => assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);

async function holds(s, status = 'held') {
  return (await query(
    'SELECT * FROM stock_reservations WHERE shop_id=$1 AND status=$2 ORDER BY created_at, id', [s.shopId, status]
  )).rows;
}
async function reconciled(s, pid) {
  const all = await cat(s, '/report');
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.equal(all.body.ok, true, JSON.stringify(all.body.mismatches));
  const r = await cat(s, `/report?productId=${pid}`);
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
async function stockOf(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock;
}
/** کالا با موجودی در دکانی که همین حالا پرچم‌هایش روشن شد */
async function stocked(s, pid, units) {
  await push(s, [product(pid), entry(`${pid}-e`, pid, units)]);
  await refresh(s);
}
/** پیش بردنِ سفارشِ «تحویل در دکان» تا «مشتری گرفت» */
async function handOver(s, id) {
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const r = await go(s, id, { to });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}

// ════════════════════════════════════════════════════════════════════

test('⛔ پرچمِ `shop.stock_reserve` خاموش: همان رفتارِ امروز — بی رزرو، دو سفارشِ آخرین عدد هر دو ۲۰۱، /config بی reserve', async () => {
  const { KNOWN } = require('../src/routes/admin-flags');
  const k = KNOWN.find((x) => x.key === 'shop.stock_reserve');
  assert.ok(k, 'پرچم در کاتالوگِ پرچم‌ها');
  assert.deepEqual([k.scope, k.default], ['shop', false]);

  const s = await shop('رزرو-خاموش');
  await enable(s, { reserve: false });
  await stocked(s, 'f1', 1);
  const [a, b] = await Promise.all([order(s, [line('f1', 1000)]), order(s, [line('f1', 1000)])]);
  assert.deepEqual([a.status, b.status], [201, 201]);
  assert.equal(a.body.order.stockHold, undefined);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [s.shopId])).n), 0);
  assert.equal((await cat(s, '/config')).body.reserve, undefined);
  //  مهلت بی پرچم مثلِ پیش نادیده
  const set = await h.put('/api/shop/catalog/settings', { reserveTtlSec: 60 }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.equal(set.body.settings.reserveTtlSec, undefined);
  //  گذارها هم هیچ نمی‌نویسند
  assert.equal((await go(s, a.body.order.id, { to: 'accepted' })).status, 200);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [s.shopId])).n), 0);
});

test('ORD-20: دو سفارشِ هم‌زمان روی آخرین عدد ⇒ دقیقاً یکی ۲۰۱، دیگری ۴۰۹ با پیامِ روشن؛ دفتر = رزرو', async () => {
  const s = await shop('رزرو-هم‌زمان');
  await enable(s);
  await stocked(s, 'r1', 1);
  const rs = await Promise.all([order(s, [line('r1', 1000)]), order(s, [line('r1', 1000)])]);
  assert.deepEqual(rs.map((r) => r.status).sort(), [201, 409], JSON.stringify(rs.map((r) => r.body)));
  const lost = rs.find((r) => r.status === 409);
  assert.equal(lost.body.error.code, 'out_of_stock');
  assert.match(lost.body.error.message, /برای سفارشِ دیگری نگه داشته شده/);
  const won = rs.find((r) => r.status === 201).body.order;
  assert.equal(won.stockHold.status, 'held');
  assert.equal(won.stockHold.review, false);
  assert.ok(won.stockHold.expiresAt > Date.now());
  assert.equal((await holds(s)).length, 1);
  const st = await stockOf(s, 'r1');
  assert.equal(st.reservedMilli, 1000);
  assert.equal(st.sellableMilli, 0);
  assert.equal((await catalog().publicStock(s.shopId, ['r1'])).get('r1').status, 'out_of_stock');
  assert.deepEqual((await cat(s, '/config')).body.reserve, { enabled: true, ttlSec: 1800 });
  //  فقط یک سفارش ساخته شد (بازنده هیچ ردیفی ننوشت)
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [s.shopId])).n), 1);
  await reconciled(s, 'r1');
});

test('ORD-20: هشت درخواستِ هم‌زمان روی دو عدد ⇒ دقیقاً دو موفق (بی فروشِ بیش از موجودی)', async () => {
  const s = await shop('رزرو-هشت');
  await enable(s);
  await stocked(s, 'r5', 2);
  const rs = await Promise.all([1, 2, 3, 4, 5, 6, 7, 8].map(() => order(s, [line('r5', 1000)])));
  assert.equal(rs.filter((r) => r.status === 201).length, 2, JSON.stringify(rs.map((r) => r.status)));
  assert.equal(rs.filter((r) => r.status === 409).length, 6);
  assert.equal((await holds(s)).reduce((a, r) => a + Number(r.qty_milli), 0), 2000);
  await reconciled(s, 'r5');
});

test('CHK-18: تکرارِ همان کلید (هم‌زمان) یک رزرو؛ رد آزاد می‌کند؛ قبول ⇒ بی انقضا؛ لغوِ پس از قبول آزاد می‌کند', async () => {
  const s = await shop('رزرو-تکرار');
  await enable(s);
  await stocked(s, 'i1', 1);
  const key = fresh();
  const [a, b] = await Promise.all([order(s, [line('i1', 1000)], key), order(s, [line('i1', 1000)], key)]);
  assert.deepEqual([a.status, b.status].sort(), [200, 201], JSON.stringify([a.body, b.body]));
  assert.equal(a.body.order.id, b.body.order.id);
  assert.equal((await holds(s)).length, 1, 'تکرار ⇒ یک رزرو');
  assert.equal((await order(s, [line('i1', 1000)])).status, 409);

  assert.equal((await go(s, a.body.order.id, { to: 'rejected', reason: 'busy' })).status, 200);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await holds(s, 'released')).length, 1);
  const c = await order(s, [line('i1', 1000)]);
  assert.equal(c.status, 201, JSON.stringify(c.body));
  //  دو بار کلیکِ «قبول» ⇒ یکی عوض می‌کند، دومی بی‌اثر
  const [x, y] = await Promise.all([go(s, c.body.order.id, { to: 'accepted' }), go(s, c.body.order.id, { to: 'accepted' })]);
  assert.deepEqual([x.status, y.status], [200, 200]);
  assert.deepEqual([x.body.unchanged, y.body.unchanged].sort(), [false, true]);
  const kept = await holds(s);
  assert.equal(kept.length, 1);
  assert.equal(kept[0].expires_at, null, 'قبول ⇒ بی انقضا');
  assert.equal((await go(s, c.body.order.id, { to: 'cancelled', reason: 'customer_request' })).status, 200);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await getOrder(s, c.body.order.id)).body.order.stockHold.status, 'released');
  assert.equal((await stockOf(s, 'i1')).sellableMilli, 1000);
  await reconciled(s, 'i1');
});

test('CHK-18: انقضا موجودی را آزاد می‌کند — تنبل هنگامِ خواندن/ساخت و جاروی دوره‌ای؛ مهلت از تنظیم (۶۰ تا ۸۶۴۰۰)', async () => {
  const s = await shop('رزرو-انقضا');
  await enable(s);
  const set = await h.put('/api/shop/catalog/settings', { reserveTtlSec: 120 }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.equal(set.body.settings.reserveTtlSec, 120);
  for (const bad of [5, 86401, '120', 1.5]) {
    assert.equal((await h.put('/api/shop/catalog/settings', { reserveTtlSec: bad }, { token: s.token })).status, 400, String(bad));
  }
  await stocked(s, 'e1', 1);
  const t0 = Date.now();
  const a = await order(s, [line('e1', 1000)]);
  assert.equal(a.status, 201);
  const exp = Number((await holds(s))[0].expires_at);
  assert.ok(exp >= t0 + 120_000 && exp <= Date.now() + 120_000, 'expires_at = اکنون + مهلت');
  assert.equal((await order(s, [line('e1', 1000)])).status, 409);

  //  زمان گذشت ⇒ آزادِ تنبل: خواندن (کارمند و ویترین) پیش از هر جارو آن را نمی‌شمارد
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE shop_id=$1 AND status='held'`, [s.shopId, Date.now() - 1]);
  assert.equal((await getOrder(s, a.body.order.id)).body.order.stockHold.status, 'expired');
  assert.equal((await stockOf(s, 'e1')).sellableMilli, 1000);
  assert.equal((await catalog().publicStock(s.shopId, ['e1'])).get('e1').status, 'in_stock');
  const b = await order(s, [line('e1', 1000)]);
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.equal((await holds(s, 'expired')).length, 1);
  await reconciled(s, 'e1');

  //  جارو: رزروِ گذشته بی هیچ درخواستی بسته می‌شود؛ دورِ دوم هیچ
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE shop_id=$1 AND status='held'`, [s.shopId, Date.now() - 1]);
  assert.ok((await lib().sweep()) >= 1);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await holds(s, 'expired')).length, 2);
  assert.equal(await lib().sweep(), 0, 'دورِ دوم هیچ');
  await reconciled(s, 'e1');
});

test('CHK-18: قبولِ پس از انقضا ⇒ اگر آزاد است دوباره بی‌مهلت نگه داشته می‌شود، وگرنه ۴۰۹ و سفارش «تازه» می‌ماند', async () => {
  const s = await shop('رزرو-قبول-دیر');
  await enable(s);
  await stocked(s, 'q1', 1);
  const a = await order(s, [line('q1', 1000)]);
  assert.equal(a.status, 201);
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE shop_id=$1 AND status='held'`, [s.shopId, Date.now() - 1]);
  //  هنوز کسی نگرفته ⇒ قبول دوباره نگه می‌دارد
  const ok = await go(s, a.body.order.id, { to: 'accepted' });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.order.stockHold.status, 'held');
  const held = await holds(s);
  assert.equal(held.length, 1);
  assert.equal(held[0].expires_at, null);
  assert.equal((await stockOf(s, 'q1')).sellableMilli, 0);

  //  سفارشِ دوم دیر شد و عدد را سفارشِ سوم گرفت ⇒ قبولِ دوم ۴۰۹، بی گذار و بی رزرو
  await push(s, [entry('q1-e2', 'q1', 1)]);
  await refresh(s);
  const b = await order(s, [line('q1', 1000)]);
  assert.equal(b.status, 201);
  await query(`UPDATE stock_reservations SET expires_at=$3 WHERE shop_id=$1 AND order_id=$2 AND status='held'`,
    [s.shopId, b.body.order.id, Date.now() - 1]);
  const c = await order(s, [line('q1', 1000)]);
  assert.equal(c.status, 201, JSON.stringify(c.body));
  const late = await go(s, b.body.order.id, { to: 'accepted' });
  assert.equal(late.status, 409, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'out_of_stock');
  assert.equal((await getOrder(s, b.body.order.id)).body.order.status, 'new');
  assert.equal((await holds(s)).length, 2, 'فقط رزروِ سفارش‌های اول و سوم');
  await reconciled(s, 'q1');
});

test('ORD-20 (روی PostgreSQL تراکنش‌های واقعاً جدا): قبولِ سفارشِ دیرشده و سفارشِ تازه هم‌زمان روی آخرین عدد ⇒ دقیقاً یکی', async () => {
  const s = await shop('رزرو-مسابقه');
  await enable(s);
  for (let round = 1; round <= 6; round++) {
    const pid = `m${round}`;
    await stocked(s, pid, 1);
    const a = await order(s, [line(pid, 1000)]);
    assert.equal(a.status, 201, JSON.stringify(a.body));
    await query(`UPDATE stock_reservations SET expires_at=$3 WHERE shop_id=$1 AND order_id=$2 AND status='held'`,
      [s.shopId, a.body.order.id, Date.now() - 1]);
    //  جارو پیش‌تر بست ⇒ هیچ ردیفِ مشترکی میانِ دو تراکنش قفل نمی‌شود؛ فقط قفلِ موجودیِ دکان جدایشان می‌کند
    assert.equal(await lib().sweep(), 1);
    const [acc, b] = await Promise.all([go(s, a.body.order.id, { to: 'accepted' }), order(s, [line(pid, 1000)])]);
    const won = [acc.status === 200, b.status === 201].filter(Boolean).length;
    assert.equal(won, 1, `دورِ ${round}: ${acc.status}/${b.status} ${JSON.stringify([acc.body, b.body])}`);
    assert.ok([409].includes(acc.status === 200 ? b.status : acc.status));
    const held = await one(
      `SELECT COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations WHERE shop_id=$1 AND product_id=$2 AND status='held'`,
      [s.shopId, pid]
    );
    assert.equal(Number(held.q), 1000, `دورِ ${round}: یک عدد، یک نگه‌داشته`);
    await reconciled(s, pid);
  }
});

test('⛔ ORD-20: کسرِ قطعی فقط فروشِ تحویلِ برنامه و یک بار — رزرو تا رسیدنِ همان فروش می‌ماند و در همان تراکنش «انجام‌شده» می‌شود', async () => {
  const s = await shop('رزرو-تحویل');
  await enable(s);
  await stocked(s, 'd1', 3);
  const a = await order(s, [line('d1', 2000)]);
  assert.equal(a.status, 201);
  const oid = a.body.order.id;
  assert.equal((await stockOf(s, 'd1')).sellableMilli, 1000);
  await handOver(s, oid);
  //  «مشتری گرفت» هنوز فروشی در دفتر نیست ⇒ رزرو نمی‌رود (وگرنه همین فاصله آخرین عدد را دوباره می‌فروخت)
  const mid = await holds(s);
  assert.equal(mid.length, 1);
  assert.ok(Number(mid[0].expires_at) > Date.now() + 23 * 3_600_000, 'مهلتِ پشتیبانِ پس از تحویل');
  assert.equal((await stockOf(s, 'd1')).sellableMilli, 1000);
  assert.equal((await order(s, [line('d1', 2000)])).status, 409, 'همان فاصله: دو عددِ تحویل‌شده دوباره فروخته نمی‌شود');
  //  دو بار کلیکِ «مشتری گرفت» ⇒ بی‌اثر
  const again = await go(s, oid, { to: 'picked_up' });
  assert.equal(again.body.unchanged, true);

  //  برنامه فروشِ تحویل را ثبت می‌کند (شناسه‌های ثابت) ⇒ موجودی یک بار کم، رزرو «انجام‌شده»
  await push(s, sale(`o-${oid}`, [[`o-${oid}-1`, 'd1', 2]]));
  await refresh(s);
  let st = await stockOf(s, 'd1');
  assert.equal(st.levels.shop_floor, 1000);
  assert.equal(st.reservedMilli, 0);
  assert.equal(st.sellableMilli, 1000, 'یک عدد باقی، نه منفی و نه دو بار کم');
  assert.equal((await holds(s, 'committed')).length, 1);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await getOrder(s, oid)).body.order.stockHold.status, 'committed');
  const commit = await one(
    `SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1 AND type='release' AND reason='order_fulfilled'`, [s.shopId]
  );
  assert.equal(Number(commit.n), 1);

  //  دستگاهِ دوم همان فروش را دوباره می‌فرستد + خواندنِ کامل از نو ⇒ هیچ کسرِ دوم و هیچ آزادسازیِ دوم
  await push(s, sale(`o-${oid}`, [[`o-${oid}-1`, 'd1', 2]]));
  await refresh(s);
  await tx(async (c) => catalog().ingestTx(c, s.shopId, Date.now(), await require('../src/lib/stock').settingsOf(s.shopId, c), { full: true }));
  st = await stockOf(s, 'd1');
  assert.equal(st.levels.shop_floor, 1000);
  assert.equal(st.sellableMilli, 1000);
  const sales = await one(
    `SELECT COUNT(*)::int AS n, COALESCE(SUM(delta_on_hand_milli),0)::bigint AS d FROM stock_moves
      WHERE shop_id=$1 AND product_id='d1' AND type='sale'`, [s.shopId]
  );
  assert.deepEqual([Number(sales.n), Number(sales.d)], [1, -2000], 'یک حرکتِ فروش برای کلِ سفارش');
  assert.equal(Number((await one(
    `SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1 AND type='release' AND reason='order_fulfilled'`, [s.shopId]
  )).n), 1);
  //  سرور خودش هرگز کم نمی‌کند: هیچ حرکتِ مکان‌دارِ سفارشی
  assert.equal(Number((await one(
    `SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1 AND source='order' AND delta_on_hand_milli <> 0`, [s.shopId]
  )).n), 0);
  await reconciled(s, 'd1');
});

test('ORD-20: قلمِ جایگزین‌شده (فروشِ همان قلم با کالای دیگر) هم رزروِ کالای اصلی را می‌بندد', async () => {
  const s = await shop('رزرو-جایگزین');
  await enable(s);
  await push(s, [product('g1'), entry('g1-e', 'g1', 1), product('g2'), entry('g2-e', 'g2', 1)]);
  await refresh(s);
  const a = await order(s, [line('g1', 1000)]);
  assert.equal(a.status, 201);
  const oid = a.body.order.id;
  await handOver(s, oid);
  assert.equal((await holds(s)).length, 1, 'پس از تحویل و پیش از فروش هنوز نگه داشته');
  assert.equal((await stockOf(s, 'g1')).sellableMilli, 0);
  await push(s, sale(`o-${oid}`, [[`o-${oid}-1`, 'g2', 1]]));
  await refresh(s);
  assert.equal((await holds(s)).length, 0);
  assert.equal((await stockOf(s, 'g1')).sellableMilli, 1000, 'کالای اصلی آزاد شد');
  assert.equal((await stockOf(s, 'g2')).sellableMilli, 0);
  await reconciled(s, 'g1');
});

test('ORD-20: برگشت به دکان پیش از ثبتِ فروش ⇒ آزاد؛ مهلتِ پشتیبانِ پس از تحویل هم سرانجام آزاد می‌کند', async () => {
  const s = await shop('رزرو-برگشت');
  await enable(s);
  await stocked(s, 'b1', 1);
  const a = await order(s, [line('b1', 1000)], fresh(), { fulfillment: 'delivery', customer: { name: 'مشتری', phone: '0700123456' }, address: { text: 'کابل، کارتهٔ سه' } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const oid = a.body.order.id;
  for (const to of ['accepted', 'picking', 'packed', 'out_for_delivery']) assert.equal((await go(s, oid, { to })).status, 200, to);
  for (const [to, reason] of [['delivery_failed', 'no_answer'], ['retry_scheduled', ''], ['returned_to_store', '']]) {
    const r = await go(s, oid, { to, reason });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
    if (to !== 'returned_to_store') assert.equal((await holds(s)).length, 1, `${to}: کالا هنوز بیرون است`);
  }
  assert.equal((await holds(s)).length, 0);
  assert.equal((await stockOf(s, 'b1')).sellableMilli, 1000);

  //  تحویل شد ولی فروش هرگز نرسید (برنامهٔ کهنه) ⇒ مهلتِ پشتیبان سر رسید ⇒ جارو آزاد می‌کند
  const c = await order(s, [line('b1', 1000)]);
  assert.equal(c.status, 201);
  await handOver(s, c.body.order.id);
  await query(`UPDATE stock_reservations SET expires_at=$2 WHERE shop_id=$1 AND status='held'`, [s.shopId, Date.now() - 1]);
  assert.ok((await lib().sweep()) >= 1);
  assert.equal((await holds(s)).length, 0);
  await reconciled(s, 'b1');
});

test('⛔ INV-19: فروشِ حضوری روی موجودیِ رزروشده هرگز رد نمی‌شود؛ سفارش به بررسی می‌رود، نه فروش', async () => {
  const s = await shop('رزرو-حضوری');
  await enable(s);
  await stocked(s, 'p1', 2);
  const a = await order(s, [line('p1', 1000)]);
  const b = await order(s, [line('p1', 1000)]);
  assert.deepEqual([a.status, b.status], [201, 201]);
  //  صندوق هر دو عدد را فروخت ⇒ push پذیرفته
  await push(s, sale('p1-s', [['p1-i', 'p1', 2]]));
  await refresh(s);
  assert.equal((await stockOf(s, 'p1')).levels.shop_floor, 0, 'فروش نشست');
  const da = (await getOrder(s, a.body.order.id)).body.order.stockHold;
  const db = (await getOrder(s, b.body.order.id)).body.order.stockHold;
  assert.deepEqual([da.review, db.review], [true, true]);
  const list = (await h.get('/api/shop/orders?tab=new', { token: s.token })).body.items;
  assert.equal(list.find((o) => o.id === a.body.order.id).stockReview, true);
  const tasks = (await cat(s, '/tasks?status=open')).body.tasks.filter((t) => t.kind === 'reservation_short');
  assert.equal(tasks.length, 1);
  assert.deepEqual([...tasks[0].detail.orderIds].sort(), [a.body.order.id, b.body.order.id].sort());
  //  ورودِ یک عدد ⇒ قدیمی‌ترین سفارش دوباره پوشش دارد، تازه‌ترین هنوز در بررسی
  await push(s, [entry('p1-e2', 'p1', 1)]);
  await refresh(s);
  assert.equal((await getOrder(s, a.body.order.id)).body.order.stockHold.review, false);
  assert.equal((await getOrder(s, b.body.order.id)).body.order.stockHold.review, true);
  //  دکان سفارشِ بی‌پوشش را رد می‌کند ⇒ آزاد و بررسی تمام
  assert.equal((await go(s, b.body.order.id, { to: 'rejected', reason: 'out_of_stock' })).status, 200);
  assert.equal((await holds(s)).length, 1);
  await reconciled(s, 'p1');
});

test('⛔ دکان‌ها جدا: رزروِ یک دکان موجودیِ همان شناسهٔ کالا در دکانِ دیگر را کم نمی‌کند؛ کارمندِ دکانِ دیگر به سفارش راه ندارد', async () => {
  const A = await shop('رزرو-الف');
  const B = await shop('رزرو-ب');
  await enable(A);
  await enable(B);
  await stocked(A, 't1', 1);
  await stocked(B, 't1', 1);
  const a = await order(A, [line('t1', 1000)]);
  assert.equal(a.status, 201);
  const b = await order(B, [line('t1', 1000)]);
  assert.equal(b.status, 201, 'رزروِ دکانِ الف در دکانِ ب شمرده نمی‌شود');
  assert.equal((await stockOf(B, 't1')).reservedMilli, 1000);
  //  گذار با توکنِ دکانِ دیگر ⇒ پیدا نمی‌شود، رزرو دست نمی‌خورد
  const cross = await go(B, a.body.order.id, { to: 'rejected', reason: 'busy' });
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
  assert.equal((await holds(A)).length, 1);
  //  فروشِ تحویل با شناسهٔ سفارشِ الف در دفترِ ب ⇒ رزروِ الف «انجام‌شده» نمی‌شود
  await push(B, sale(`o-${a.body.order.id}`, [[`o-${a.body.order.id}-1`, 't1', 1]]));
  await refresh(B);
  assert.equal((await holds(A)).length, 1);
  assert.equal((await holds(A, 'committed')).length, 0);
  await reconciled(A, 't1');
  await reconciled(B, 't1');
});

test('CHK-18: لغوِ مشتری (ویترین) آزاد می‌کند؛ موجودیِ کهنه یا سفارشِ آزمایشی ⇒ بی رزرو', async () => {
  const s = await shop('رزرو-کهنه');
  await enable(s);
  await stocked(s, 'k1', 1);
  const a = await order(s, [line('k1', 1000)]);
  assert.equal(a.status, 201);
  await require('../src/lib/orders').transition(s.shopId, a.body.order.id, { to: 'cancelled_by_customer' }, { type: 'customer', id: '' });
  assert.equal((await holds(s)).length, 0);
  assert.equal((await holds(s, 'released')).length, 1);

  const t = await order(s, [line('k1', 1000)], fresh(), { test: true });
  assert.equal(t.status, 201, JSON.stringify(t.body));
  assert.equal((await holds(s)).length, 0, 'سفارشِ آزمایشی هرگز');

  await query(`UPDATE sync_devices SET last_seen_at = $2 WHERE app='shop' AND account_id=$1`, [s.shopId, Date.now() - 3_600_000]);
  const [x, y] = await Promise.all([order(s, [line('k1', 1000)]), order(s, [line('k1', 1000)])]);
  assert.deepEqual([x.status, y.status], [201, 201]);
  assert.equal(x.body.order.stockHold, undefined);
  assert.equal((await holds(s)).length, 0);
});

test('CHK-21 + O-06: افزودنِ قلم به سفارشِ ویترین نگه‌داشتهٔ خودِ همان سفارش را «دیگران» نمی‌شمارد و افزوده را هم نگه می‌دارد', async () => {
  const s = await shop('رزرو-افزودن');
  await enable(s);
  for (const k of ['shop.storefront', 'shop.orders.add_items']) await live(s.shopId, k, true);
  const slug = `rsv-add-${Date.now().toString(36)}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ نگه‌داشتن', contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  await stocked(s, 'ad1', 2);
  assert.equal((await h.put('/api/shop/storefront/products/ad1', { published: true }, { token: s.token })).status, 200);
  const phone = '0791112299';
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const ticket = (await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode })).body.ticket;
  assert.ok(ticket, 'تیکتِ شمارهٔ تأییدشده');
  const ck = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'ad1', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `rsv-ck-${Date.now()}`, 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, ck.body.order.orderNo])).id;
  const add = (k) => h.post(`/api/store/${slug}/orders/${ck.body.order.token}/items`, { lines: [{ productId: 'ad1', qtyMilli: 1000 }] },
    { headers: { 'Idempotency-Key': k, 'X-Store-Ticket': ticket } });
  //  دو عدد در قفسه، یکی نگه‌داشتهٔ همین سفارش ⇒ افزودنِ یکی دیگر می‌گذرد (نگه‌داشتهٔ خودی «دیگران» نیست)
  const k1 = `rsv-add-${Date.now()}-1`;
  const a1 = await add(k1);
  assert.equal(a1.status, 201, JSON.stringify(a1.body));
  const mine = await query(`SELECT COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held'`, [s.shopId, id]);
  assert.equal(Number(mine.rows[0].q), 2000, 'افزوده هم نگه داشته شد');
  assert.equal((await stockOf(s, 'ad1')).sellableMilli, 0);
  //  تکرارِ همان کلید ⇒ همان نتیجه، بی نگه‌داشتنِ دوم
  assert.equal((await add(k1)).status, 200);
  assert.equal(Number((await query(`SELECT COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations WHERE shop_id=$1 AND status='held'`, [s.shopId])).rows[0].q), 2000);
  //  سومی دیگر نیست
  const a3 = await add(`rsv-add-${Date.now()}-3`);
  assert.equal(a3.status, 409, JSON.stringify(a3.body));
  assert.equal(a3.body.error.code, 'out_of_stock');
  await reconciled(s, 'ad1');
});

test('ORD-14 + O-06: ویرایشِ سفارشِ کارمند نگه‌داشته را با قلم‌های تازه هم‌اندازه می‌کند (کم ⇒ آزاد، برداشته ⇒ آزاد)', async () => {
  const s = await shop('رزرو-ویرایش');
  await enable(s);
  await live(s.shopId, 'shop.orders.amend', true);
  await push(s, [product('am1'), entry('am1-e', 'am1', 3), product('am2'), entry('am2-e', 'am2', 1)]);
  await refresh(s);
  const a = await order(s, [line('am1', 3000), line('am2', 1000)]);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.order.id;
  const r = await h.post(`/api/shop/orders/${id}/amendments`, {
    changes: [{ lineNo: 1, qtyMilli: 1000 }, { lineNo: 2, qtyMilli: 0 }], reason: 'customer_request', consent: { via: 'phone' },
  }, { token: s.token, headers: { 'Idempotency-Key': `rsv-am-${Date.now()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const held = await query(
    `SELECT line_no, product_id, qty_milli FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held' ORDER BY line_no`,
    [s.shopId, id]
  );
  assert.deepEqual(held.rows.map((x) => [Number(x.line_no), x.product_id, Number(x.qty_milli)]), [[1, 'am1', 1000]]);
  assert.equal((await stockOf(s, 'am1')).sellableMilli, 2000);
  assert.equal((await stockOf(s, 'am2')).sellableMilli, 1000);
  assert.equal((await getOrder(s, id)).body.order.stockHold.status, 'held');
  await reconciled(s, 'am1');
  await reconciled(s, 'am2');
});

test('PCK-07 + O-06: بسته‌بندی پس از جمع‌آوری نگه‌داشته را همان «واقعاً برداشته» می‌کند (پیدا نشد ⇒ آزاد)', async () => {
  const s = await shop('رزرو-جمع‌آوری');
  await enable(s);
  await live(s.shopId, 'shop.orders.picking', true);
  await push(s, [product('pk1'), entry('pk1-e', 'pk1', 3), product('pk2'), entry('pk2-e', 'pk2', 1)]);
  await refresh(s);
  const a = await order(s, [line('pk1', 2000), line('pk2', 1000)]);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.order.id;
  assert.equal((await go(s, id, { to: 'accepted' })).status, 200);
  assert.ok([200, 201].includes((await h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: s.token })).status));
  let evN = 0;
  const ev = (lineNo, type, extra = {}) => ({ eventId: `ev-${++evN}-${Date.now()}`, lineNo, type, ...extra });
  const sent = await h.post(`/api/shop/orders/${id}/pick/events`, {
    events: [ev(1, 'pick', { qtyMilli: 1000 }), ev(1, 'not_found'), ev(2, 'not_found')],
  }, { token: s.token });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  const p = await h.post(`/api/shop/orders/${id}/pick/pack`, { bags: 1 }, { token: s.token, headers: { 'Idempotency-Key': `rsv-pack-${Date.now()}` } });
  assert.ok([200, 201].includes(p.status), JSON.stringify(p.body));
  const held = await query(
    `SELECT line_no, product_id, qty_milli, expires_at FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held' ORDER BY line_no`,
    [s.shopId, id]
  );
  assert.deepEqual(held.rows.map((x) => [Number(x.line_no), x.product_id, Number(x.qty_milli), x.expires_at]), [[1, 'pk1', 1000, null]]);
  assert.equal((await stockOf(s, 'pk1')).sellableMilli, 2000);
  assert.equal((await stockOf(s, 'pk2')).sellableMilli, 1000);
  await reconciled(s, 'pk1');
  await reconciled(s, 'pk2');
});

test('⛔ کنار گذاشتنِ موجودیِ سفارش‌محورِ پیشین (retire) به رزروِ نرم دست نمی‌زند', async () => {
  const s = await shop('رزرو-retire');
  await enable(s);
  await stocked(s, 'l1', 2);
  assert.equal((await order(s, [line('l1', 1000)])).status, 201);
  const out = await tx((c) => catalog().retireOrderStock(c, s.shopId, Date.now()));
  assert.equal(out.changed, false);
  assert.equal((await holds(s)).length, 1);
  await reconciled(s, 'l1');
});

// ── S82 (§۱۹.۱۲، «رزرو منقضی»؛ با O-06 = ب: نگه‌داشتن تا قبولِ دکان، نه تا پرداخت) ─────────────────
//  پرداخت/قبول نیامد ⇒ نگه‌داشته با همان مهلت آزاد می‌شود (امروز سفارش فقط «نقد هنگامِ تحویل» است، `bad_payment_method`)؛ «worker» ِ تکراری و پیامِ دیررسیده نگه‌داشتهٔ سفارشِ معتبر
//  (قبول‌شده) را آزاد نمی‌کنند. «معتبر» = سفارشی که دکان قبول کرده (بی‌مهلت تا پایانش).

const releasesOf = async (s, ids) => (await query(
  `SELECT source_key FROM stock_moves WHERE shop_id=$1 AND type='release' AND source_key = ANY($2::text[])`,
  [s.shopId, ids.map((id) => `res:${id}:release`)]
)).rows.map((r) => r.source_key).sort();
const SYSTEM = { type: 'system', id: '' };

test('S82: «worker» ِ تکراری — چند جارو و چند انقضای هم‌زمان هر نگه‌داشتهٔ گذشته را دقیقاً یک بار می‌بندند؛ سفارشِ قبول‌شده دست نمی‌خورد', async () => {
  const s = await shop('رزرو-جاروی-دوتایی');
  await enable(s);
  for (const pid of ['w1', 'w2', 'w3', 'w4']) await stocked(s, pid, 1);
  const due = [];
  for (const pid of ['w1', 'w2', 'w3']) {
    const r = await order(s, [line(pid, 1000)]);
    assert.equal(r.status, 201, JSON.stringify(r.body));
    due.push(r.body.order.id);
  }
  const valid = await order(s, [line('w4', 1000)]);
  assert.equal(valid.status, 201, JSON.stringify(valid.body));
  assert.equal((await go(s, valid.body.order.id, { to: 'accepted' })).status, 200);
  //  نه پرداخت آمد نه قبول، و مهلت گذشت
  await query(`UPDATE stock_reservations SET expires_at=$3 WHERE shop_id=$1 AND order_id = ANY($2::text[]) AND status='held'`,
    [s.shopId, due, Date.now() - 1]);
  const ids = (await holds(s)).filter((r) => due.includes(r.order_id)).map((r) => r.id);
  assert.equal(ids.length, 3);

  //  چهار جاروی سراسری (دو نمونهٔ سرور، دو تیکِ هم‌پوشان) و دو انقضای مستقیمِ همین دکان، همه هم‌زمان
  const at = Date.now();
  const direct = () => tx(async (c) => { await require('../src/lib/stock').lock(c, s.shopId); return lib().expireDueIn(c, s.shopId, at); });
  const [w1, w2, w3, w4, d1, d2] = await Promise.all([lib().sweep(at), lib().sweep(at), lib().sweep(at), lib().sweep(at), direct(), direct()]);
  assert.equal(d1 + d2 <= 3, true, `انقضای مستقیم ${d1}+${d2}`);
  assert.ok(w1 + w2 + w3 + w4 + d1 + d2 >= 3, 'همهٔ گذشته‌ها بسته شدند');
  const closed = (await query(
    'SELECT id, status, close_reason FROM stock_reservations WHERE id = ANY($1::text[]) ORDER BY id', [ids]
  )).rows;
  assert.deepEqual(closed.map((r) => [r.status, r.close_reason]), ids.map(() => ['expired', 'ttl']));
  assert.deepEqual(await releasesOf(s, ids), ids.map((id) => `res:${id}:release`).sort(), 'هر نگه‌داشته یک آزادسازی، نه بیشتر');
  //  دورِ دوم هیچ — نه ردیف، نه حرکتِ تازه
  assert.equal(await direct(), 0);
  assert.equal((await releasesOf(s, ids)).length, 3);

  const kept = (await holds(s)).filter((r) => r.order_id === valid.body.order.id);
  assert.equal(kept.length, 1, 'نگه‌داشتهٔ سفارشِ قبول‌شده ماند');
  assert.equal(kept[0].expires_at, null);
  for (const pid of ['w1', 'w2', 'w3']) assert.equal((await stockOf(s, pid)).sellableMilli, 1000, pid);
  assert.equal((await stockOf(s, 'w4')).sellableMilli, 0);
  for (const pid of ['w1', 'w2', 'w3', 'w4']) await reconciled(s, pid);
});

test('S82: پیامِ دیررسیده — جارو با زمانِ پیشین، ردِ خودکار و لغوِ مشتریِ پس از قبول نگه‌داشتهٔ سفارشِ معتبر را آزاد نمی‌کنند', async () => {
  const s = await shop('رزرو-پیام-دیر');
  await enable(s);
  const set = await h.put('/api/shop/catalog/settings', { reserveTtlSec: 60 }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  await stocked(s, 'd1', 1);
  const a = await order(s, [line('d1', 1000)]);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const dueAt = Number((await holds(s))[0].expires_at);
  //  جارویی که سرِ مهلت تصمیم گرفت ولی دیر رسید: قبول پیش از نوشتنش آمد
  assert.equal((await go(s, a.body.order.id, { to: 'accepted' })).status, 200);
  assert.equal(await lib().sweep(dueAt + 1), 0);
  assert.equal(await tx(async (c) => { await require('../src/lib/stock').lock(c, s.shopId); return lib().expireDueIn(c, s.shopId, dueAt + 2 * 86_400_000); }), 0);
  //  ردِ خودکارِ «زمان گذشت» (`order-timeout`، از «تازه») و لغوِ مشتری پس از قبول ⇒ رد، بی گذار
  await assert.rejects(
    require('../src/lib/orders').transition(s.shopId, a.body.order.id, { to: 'auto_rejected', from: 'new', reason: 'timeout' }, SYSTEM),
    (err) => err.status === 409, 'ردِ خودکارِ دیررسیده'
  );
  await assert.rejects(
    require('../src/lib/orders').transition(s.shopId, a.body.order.id, { to: 'cancelled_by_customer' }, { type: 'customer', id: '' }),
    (err) => err.status >= 400 && err.status < 500, 'لغوِ دیررسیدهٔ مشتری'
  );
  const view = (await getOrder(s, a.body.order.id)).body.order;
  assert.equal(view.status, 'accepted');
  assert.equal(view.stockHold.status, 'held');
  const kept = await holds(s);
  assert.equal(kept.length, 1);
  assert.equal(kept[0].expires_at, null);
  assert.equal((await releasesOf(s, [kept[0].id])).length, 0);
  assert.equal((await stockOf(s, 'd1')).sellableMilli, 0);
  await reconciled(s, 'd1');
});

test('S82 (روی PostgreSQL تراکنش‌های واقعاً جدا): قبول، جارو و ردِ خودکارِ هم‌زمان روی نگه‌داشتهٔ گذشته ⇒ یا قبول با یک نگه‌داشته یا رد با آزاد — هرگز قبولِ بی نگه‌داشته', async () => {
  const s = await shop('رزرو-مسابقهٔ-جارو');
  await enable(s);
  for (let round = 1; round <= 6; round++) {
    const pid = `x${round}`;
    await stocked(s, pid, 1);
    const a = await order(s, [line(pid, 1000)]);
    assert.equal(a.status, 201, JSON.stringify(a.body));
    const id = a.body.order.id;
    const [row] = (await holds(s)).filter((r) => r.order_id === id);
    await query('UPDATE stock_reservations SET expires_at=$2 WHERE id=$1', [row.id, Date.now() - 1]);
    const late = round % 2 === 0
      ? require('../src/lib/orders').transition(s.shopId, id, { to: 'auto_rejected', from: 'new', reason: 'timeout' }, SYSTEM).then(() => 'rejected', () => 'lost')
      : Promise.resolve('none');
    const [acc] = await Promise.all([go(s, id, { to: 'accepted' }), lib().sweep(), lib().sweep(), late]);
    const st = (await getOrder(s, id)).body.order;
    const held = await one(
      `SELECT COALESCE(SUM(qty_milli),0)::bigint AS q FROM stock_reservations WHERE shop_id=$1 AND product_id=$2 AND status='held'`,
      [s.shopId, pid]
    );
    if (st.status === 'accepted') {
      assert.equal(acc.status, 200, `دورِ ${round}: ${JSON.stringify(acc.body)}`);
      assert.equal(Number(held.q), 1000, `دورِ ${round}: قبول ⇒ یک نگه‌داشته`);
      assert.equal(st.stockHold.status, 'held');
    } else {
      assert.equal(st.status, 'auto_rejected', `دورِ ${round}: ${st.status}`);
      assert.equal(Number(held.q), 0, `دورِ ${round}: رد ⇒ آزاد`);
    }
    assert.ok((await releasesOf(s, [row.id])).length <= 1, `دورِ ${round}: آزادسازیِ دوتایی`);
    await reconciled(s, pid);
  }
});
