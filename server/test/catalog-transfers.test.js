'use strict';
/**
 * انتقالِ دومرحله‌ایِ موجودی با «در مسیر» و قبول یا رد در مقصد — INV-11، سناریوی S21 (v3 §۶.۵).
 *
 * از راهِ واقعی: کالا و ورودِ انبار را «دستگاه» با `POST /api/sync/v1/push` می‌فرستد و انتقال از
 * `/api/shop/catalog/transfers` می‌گذرد. آن‌چه قفل می‌شود:
 *   ⛔ پرچمِ `shop.catalog` خاموش ⇒ ۴۰۳ و هیچ ردیفی.
 *   ⛔ ارسال: مبدأ همان لحظه کم، «در مسیر» زیاد؛ «در مسیر» قابل‌فروش نیست؛ کمبود ⇒ ۴۰۹ بی هیچ ردیف.
 *   ⛔ دریافت: رسیده به مقصد؛ کسری فقط با دلیل، با حرکتِ جدا و وظیفهٔ بررسی — هرگز بی‌صدا گم نمی‌شود.
 *   ⛔ رد: همه به مبدأ برمی‌گردد. انتقالِ بسته‌شده دوباره بسته نمی‌شود (حتی هم‌زمان).
 *   ⛔ کلیدِ یکتایی: تکرار ⇒ همان نتیجه بی اثرِ دوم؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹.
 *   ⛔ دفتر = مانده‌ها = بازپخشِ هسته (INV-16) پس از هر آزمون؛ دکانِ دیگر به انتقال نمی‌رسد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

// ── کمکی‌ها (همان راهِ catalog-stock.test.js) ──────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-x-${made.body.shop.id}`.slice(0, 60) };
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
async function enable(s) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key: 'shop.catalog', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
const opId = () => `01JXF${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
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

let keyN = 0;
const fresh = () => `xfer-test-${++keyN}-${Date.now()}`;
const cat = (s, p, token = s.token) => h.get(`/api/shop/catalog${p}`, { token });
const send = (s, body, { key = fresh(), token = s.token } = {}) =>
  h.post('/api/shop/catalog/transfers', body, { token, headers: { 'Idempotency-Key': key } });
const receive = (s, id, body, { key = fresh(), token = s.token } = {}) =>
  h.post(`/api/shop/catalog/transfers/${id}/receive`, body, { token, headers: { 'Idempotency-Key': key } });
const reject = (s, id, body, { key = fresh(), token = s.token } = {}) =>
  h.post(`/api/shop/catalog/transfers/${id}/reject`, body, { token, headers: { 'Idempotency-Key': key } });

async function levels(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const st = r.body.product.stock;
  return { floor: st.levels.shop_floor, wh: st.levels.warehouse, transit: st.levels.in_transit, sellable: st.sellableMilli };
}
async function staffMoves(s, pid) {
  return (await cat(s, `/moves?productId=${pid}&limit=100`)).body.items.filter((m) => m.source === 'staff');
}
async function reconciled(s) {
  const r = await cat(s, '/report');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
async function seeded(name, units = 6) {
  const s = await shop(name);
  await enable(s);
  await push(s, [product('x1'), entry('x1-e', 'x1', units), product('x2'), entry('x2-e', 'x2', units)]);
  await levels(s, 'x1');   //  خواندنِ نخستِ دفترِ دستگاه
  return s;
}
const L = (productId, qtyMilli) => ({ productId, qtyMilli });

// ── آزمون‌ها ─────────────────────────────────────────────────────────

test('INV-11: پرچمِ shop.catalog خاموش ⇒ ۴۰۳ و هیچ ردیفی', async () => {
  const s = await shop('بی‌پرچم');
  const r = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'catalog_disabled');
  assert.equal((await cat(s, '/transfers')).status, 403);
  const n = await one('SELECT COUNT(*)::int AS n FROM stock_transfers WHERE shop_id=$1', [s.shopId]);
  assert.equal(n.n, 0);
});

test('INV-11: ارسال مبدأ را کم و «در مسیر» را زیاد می‌کند؛ در مسیر قابل‌فروش نیست؛ تکرار بی اثرِ دوم', async () => {
  const s = await seeded('ارسال');
  const key = fresh();
  const r = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 4000), L('x2', 1000)], note: 'به انبارِ پشتی' }, { key });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const t = r.body.transfer;
  assert.equal(t.status, 'in_transit');
  assert.deepEqual(t.lines.map((l) => [l.productId, l.sentMilli, l.receivedMilli]), [['x1', 4000, null], ['x2', 1000, null]]);
  assert.deepEqual(await levels(s, 'x1'), { floor: 2000, wh: 0, transit: 4000, sellable: 2000 });
  assert.deepEqual(await levels(s, 'x2'), { floor: 5000, wh: 0, transit: 1000, sellable: 5000 });
  assert.deepEqual((await staffMoves(s, 'x1')).map((m) => `${m.type}@${m.location}:${m.refId === t.id}`).sort(),
    ['transfer_in@in_transit:true', 'transfer_out@shop_floor:true']);

  //  همان کلید و همان بدنه ⇒ همان انتقال، بی حرکتِ تازه
  const again = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 4000), L('x2', 1000)], note: 'به انبارِ پشتی' }, { key });
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.transfer.id, t.id);
  assert.equal((await staffMoves(s, 'x1')).length, 2);
  //  همان کلید با بدنهٔ دیگر ⇒ ۴۰۹
  const reused = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }, { key });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');

  const list = await cat(s, '/transfers?status=in_transit');
  assert.equal(list.status, 200);
  assert.deepEqual(list.body.transfers.map((x) => x.id), [t.id]);
  await reconciled(s);
});

test('INV-11: کمبود، کالای ناشناس و درخواستِ نادرست رد می‌شوند و هیچ ردیفی نمی‌ماند', async () => {
  const s = await seeded('رد', 3);
  const short = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000), L('x2', 5000)] });
  assert.equal(short.status, 409, JSON.stringify(short.body));
  //  ردیفِ نخست هم برگشت (یک تراکنش)
  assert.deepEqual(await levels(s, 'x1'), { floor: 3000, wh: 0, transit: 0, sellable: 3000 });
  assert.equal((await staffMoves(s, 'x1')).length, 0);

  const unknown = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('nope', 1000)] });
  assert.equal(unknown.status, 404);
  assert.equal(unknown.body.error.code, 'product_not_found');
  for (const [body, code] of [
    [{ from: 'shop_floor', to: 'shop_floor', lines: [L('x1', 1000)] }, 'bad_location'],
    [{ from: 'in_transit', to: 'warehouse', lines: [L('x1', 1000)] }, 'bad_location'],
    [{ from: 'shop_floor', to: 'warehouse', lines: [] }, 'lines_required'],
    [{ from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000), L('x1', 1000)] }, 'duplicate_product'],
    [{ from: 'shop_floor', to: 'warehouse', lines: [L('x1', 0)] }, 'bad_qty'],
    [{ from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1.5)] }, 'bad_qty'],
  ]) {
    const r = await send(s, body);
    assert.equal(r.status, 400, JSON.stringify(body));
    assert.equal(r.body.error.code, code);
  }
  const noKey = await h.post('/api/shop/catalog/transfers', { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }, { token: s.token });
  assert.equal(noKey.status, 400);
  assert.equal(noKey.body.error.code, 'idempotency_key_required');
  const n = await one('SELECT COUNT(*)::int AS n FROM stock_transfers WHERE shop_id=$1', [s.shopId]);
  assert.equal(n.n, 0);
  await reconciled(s);
});

test('INV-11: دریافتِ کامل در مقصد؛ تکرار بی اثر؛ انتقالِ بسته دوباره بسته نمی‌شود', async () => {
  const s = await seeded('دریافت');
  const t = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 4000)] })).body.transfer;
  const key = fresh();
  const r = await receive(s, t.id, {}, { key });   //  بی فهرست ⇒ همه رسید
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.transfer.status, 'received');
  assert.equal(r.body.transfer.shortMilli, 0);
  assert.deepEqual(await levels(s, 'x1'), { floor: 2000, wh: 4000, transit: 0, sellable: 6000 });

  const again = await receive(s, t.id, {}, { key });
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  const twice = await receive(s, t.id, {});
  assert.equal(twice.status, 409);
  assert.equal(twice.body.error.code, 'transfer_closed');
  const late = await reject(s, t.id, { reason: 'دیر' });
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'transfer_closed');
  assert.deepEqual(await levels(s, 'x1'), { floor: 2000, wh: 4000, transit: 0, sellable: 6000 });
  assert.equal((await staffMoves(s, 'x1')).length, 4);
  await reconciled(s);
});

test('INV-11 / S21: کسری فقط با دلیل، با حرکتِ جدا و وظیفهٔ بررسی؛ بیش از ارسال پذیرفته نیست', async () => {
  const s = await seeded('کسری');
  const t = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 4000), L('x2', 2000)] })).body.transfer;

  const noReason = await receive(s, t.id, { lines: [{ productId: 'x1', receivedMilli: 3000 }] });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  const over = await receive(s, t.id, { lines: [{ productId: 'x1', receivedMilli: 5000 }], reason: 'شمارش' });
  assert.equal(over.status, 400);
  assert.equal(over.body.error.code, 'over_received');
  const stranger = await receive(s, t.id, { lines: [{ productId: 'zz', receivedMilli: 1 }], reason: 'شمارش' });
  assert.equal(stranger.status, 400);
  assert.equal(stranger.body.error.code, 'product_not_in_transfer');
  //  هیچ‌کدام چیزی ننوشت
  assert.deepEqual(await levels(s, 'x1'), { floor: 2000, wh: 0, transit: 4000, sellable: 2000 });

  const r = await receive(s, t.id, { lines: [{ productId: 'x1', receivedMilli: 3000 }], reason: 'یک کارتن در راه شکست' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const done = r.body.transfer;
  assert.equal(done.status, 'received');
  assert.deepEqual(done.lines.map((l) => [l.productId, l.receivedMilli, l.shortMilli]), [['x1', 3000, 1000], ['x2', 2000, 0]]);
  assert.equal(done.shortMilli, 1000);
  assert.equal(done.reason, 'یک کارتن در راه شکست');
  assert.deepEqual(await levels(s, 'x1'), { floor: 2000, wh: 3000, transit: 0, sellable: 5000 });
  assert.deepEqual(await levels(s, 'x2'), { floor: 4000, wh: 2000, transit: 0, sellable: 6000 });

  const shortMove = (await staffMoves(s, 'x1')).find((m) => m.type === 'adjustment');
  assert.ok(shortMove, 'کسری حرکتِ جدا دارد');
  assert.equal(shortMove.location, 'in_transit');
  assert.equal(shortMove.deltaOnHandMilli, -1000);
  assert.match(shortMove.reason, /کسریِ انتقال: یک کارتن در راه شکست/);
  const tasks = (await cat(s, '/tasks?status=open')).body.tasks.filter((x) => x.kind === 'transfer_short');
  assert.equal(tasks.length, 1);
  assert.equal(tasks[0].productId, 'x1');
  assert.equal(tasks[0].detail.shortMilli, 1000);
  assert.equal(tasks[0].detail.transferId, t.id);
  await reconciled(s);
});

test('INV-11: ردِ انتقال در مقصد همه را به مبدأ برمی‌گرداند؛ دلیل اجباری است', async () => {
  const s = await seeded('برگشت');
  const t = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 3000)] })).body.transfer;
  const noReason = await reject(s, t.id, {});
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  const r = await reject(s, t.id, { reason: 'انبار جا ندارد' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.transfer.status, 'rejected');
  assert.equal(r.body.transfer.reason, 'انبار جا ندارد');
  assert.deepEqual(await levels(s, 'x1'), { floor: 6000, wh: 0, transit: 0, sellable: 6000 });
  const after = await receive(s, t.id, {});
  assert.equal(after.status, 409);
  assert.equal(after.body.error.code, 'transfer_closed');
  assert.deepEqual((await cat(s, '/transfers?status=rejected')).body.transfers.map((x) => x.id), [t.id]);
  await reconciled(s);
});

test('INV-11: دو دریافتِ هم‌زمان ⇒ فقط یکی؛ موجودی یک بار جابه‌جا می‌شود', async () => {
  const s = await seeded('هم‌زمان');
  //  انبار خالی است ⇒ کمبود، بی ردیف
  assert.equal((await send(s, { from: 'warehouse', to: 'shop_floor', lines: [L('x1', 1000)] })).status, 409);
  const ok = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000)] })).body.transfer;
  const [a, b] = await Promise.all([receive(s, ok.id, {}), receive(s, ok.id, {})]);
  assert.deepEqual([a.status, b.status].sort(), [200, 409]);
  assert.deepEqual(await levels(s, 'x1'), { floor: 4000, wh: 2000, transit: 0, sellable: 6000 });
  assert.equal((await staffMoves(s, 'x1')).length, 4);
  await reconciled(s);
});

test('INV-11: اجازه و جداییِ دکان — شاگرد (stock.move) می‌فرستد؛ دکانِ دیگر به انتقال نمی‌رسد', async () => {
  const s = await seeded('جدا');
  const staff = await member(s, 'staff', 'شاگردِ انتقال');
  const r = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }, { token: staff.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const other = await seeded('دیگر');
  const cross = await receive(other, r.body.transfer.id, {});
  assert.equal(cross.status, 404);
  assert.equal(cross.body.error.code, 'transfer_not_found');
  assert.deepEqual((await cat(other, '/transfers')).body.transfers, []);
  assert.deepEqual(await levels(s, 'x1'), { floor: 5000, wh: 0, transit: 1000, sellable: 5000 });
  //  ممیزی: کننده و انتقال
  const a = await one(`SELECT user_id, target_id FROM audit_logs WHERE shop_id=$1 AND action='stock.transfer_send'`, [s.shopId]);
  assert.equal(a.target_id, r.body.transfer.id);
  assert.equal(a.user_id, staff.user.id);
});
