'use strict';
/**
 * INV-11 (ادامه، مهاجرتِ ۲۶۵): دریافتِ ناقص در چند گام، و ثبتِ گام‌ها از دستگاهِ بی‌اینترنت با همان صفِ همگام‌سازی.
 *
 * از راهِ واقعی: کالا، ورودِ انبار و **گامِ انتقال** (ردیفِ `auditLog` با `type = stock_transfer`) را «دستگاه» با
 * `POST /api/sync/v1/push` می‌فرستد؛ سرور هنگامِ خواندنِ دفترِ دستگاه داوری می‌کند. آن‌چه قفل می‌شود:
 *   ⛔ ناقص: رسیده به مقصد، بقیه «در مسیر» و وضعیت `partial`؛ هر گام کلیدِ خودش، تکرار بی اثرِ دوم؛
 *      گامِ پایانی کسری را با دلیل می‌بندد؛ رد فقط ماندهٔ در مسیر را برمی‌گرداند. هیچ واحدی دو بار شمرده نمی‌شود.
 *   ⛔ بی‌اینترنت: ارسال و دریافت از دفترِ دستگاه، فرستادنِ دوباره (قطعِ شبکه) و خواندنِ کاملِ دوباره بی اثرِ دوم؛
 *      گامِ نپذیرفته (کمبود) ثبت می‌شود — نتیجه با کد و وظیفهٔ `transfer_refused` — و صف را نمی‌بندد.
 *   ⛔ دکانِ دیگر با شناسهٔ انتقالِ این دکان به آن نمی‌رسد. پرچم خاموش ⇒ ردیفِ دستگاه بی‌اثر.
 *   ⛔ دفتر = مانده‌ها (`/report`) پس از هر آزمون. rollback ِ ۲۶۵ با انتقالِ نیمه‌رسیده می‌ایستد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const fs = require('fs');
const path = require('path');
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

// ── کمکی‌ها (همان راهِ catalog-transfers.test.js) ─────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-s-${made.body.shop.id}`.slice(0, 60) };
}
async function enable(s) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key: 'shop.catalog', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
const opId = () => `01JXS${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  return r.body.results;
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
const entry = (id, pid, units) => op('insert', 'warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});
/** گامِ انتقالِ دفترِ دستگاه — همان شکلی که `StockTransferBook` می‌نویسد */
const step = (rowId, xfer) => op('insert', 'auditLog', rowId, {
  type: 'stock_transfer', date: '2026-11-07', createdAt: Date.now(), notes: 'انتقال', by: 'u-1', xfer: { v: 1, ...xfer },
});

let keyN = 0;
const fresh = () => `xstep-test-${++keyN}-${Date.now()}`;
const cat = (s, p, token = s.token) => h.get(`/api/shop/catalog${p}`, { token });
const send = (s, body, { key = fresh() } = {}) =>
  h.post('/api/shop/catalog/transfers', body, { token: s.token, headers: { 'Idempotency-Key': key } });
const receive = (s, id, body, { key = fresh() } = {}) =>
  h.post(`/api/shop/catalog/transfers/${id}/receive`, body, { token: s.token, headers: { 'Idempotency-Key': key } });
const reject = (s, id, body, { key = fresh() } = {}) =>
  h.post(`/api/shop/catalog/transfers/${id}/reject`, body, { token: s.token, headers: { 'Idempotency-Key': key } });

async function levels(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const st = r.body.product.stock;
  return { floor: st.levels.shop_floor, wh: st.levels.warehouse, transit: st.levels.in_transit };
}
async function reconciled(s) {
  const r = await cat(s, '/report');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
async function transfersOf(s, keys = []) {
  const r = await cat(s, `/transfers${keys.length ? `?keys=${keys.join(',')}` : ''}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}
async function seeded(name, units = 6) {
  const s = await shop(name);
  await enable(s);
  await push(s, [product('x1'), entry('x1-e', 'x1', units), product('x2'), entry('x2-e', 'x2', units)]);
  await levels(s, 'x1');
  return s;
}
const L = (productId, qtyMilli) => ({ productId, qtyMilli });
const R = (productId, receivedMilli) => ({ productId, receivedMilli });
const count = async (sql, params) => (await one(sql, params)).n;

// ── دریافتِ ناقص (REST) ─────────────────────────────────────────────────

test('INV-11 ناقص: دو گام، هر گام یک بار؛ بقیه در مسیر، بستن با کسریِ دلیل‌دار — هر واحد یک بار', async () => {
  const s = await seeded('ناقص');
  const t = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 6000), L('x2', 2000)] })).body.transfer;

  const k1 = fresh();
  const p1 = await receive(s, t.id, { final: false, lines: [R('x1', 4000)] }, { key: k1 });
  assert.equal(p1.status, 200, JSON.stringify(p1.body));
  assert.equal(p1.body.transfer.status, 'partial');
  assert.deepEqual(p1.body.transfer.lines.map((l) => [l.productId, l.receivedMilli, l.pendingMilli, l.shortMilli]),
    [['x1', 4000, 2000, 0], ['x2', 0, 2000, 0]]);
  assert.deepEqual(await levels(s, 'x1'), { floor: 0, wh: 4000, transit: 2000 });

  //  پاسخ گم شد و برنامه همان گام را دوباره فرستاد ⇒ همان، بی اثرِ دوم
  const again = await receive(s, t.id, { final: false, lines: [R('x1', 4000)] }, { key: k1 });
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(await levels(s, 'x1'), { floor: 0, wh: 4000, transit: 2000 });
  //  همان کلید، بدنهٔ دیگر ⇒ ۴۰۹
  assert.equal((await receive(s, t.id, { final: false, lines: [R('x1', 1000)] }, { key: k1 })).body.error.code, 'idempotency_key_reused');

  //  بیش از ماندهٔ در مسیر، یا گامِ ناقصِ خالی ⇒ ۴۰۰ بی هیچ اثر
  assert.equal((await receive(s, t.id, { final: false, lines: [R('x1', 2001)] })).body.error.code, 'over_received');
  assert.equal((await receive(s, t.id, { final: false, lines: [R('x2', 0)] })).body.error.code, 'nothing_received');
  assert.equal((await receive(s, t.id, { final: 'yes' })).body.error.code, 'bad_final');

  //  گامِ دوم: یک x2؛ هنوز باز
  const p2 = await receive(s, t.id, { final: false, lines: [R('x2', 1000)] });
  assert.equal(p2.body.transfer.status, 'partial');
  assert.equal(p2.body.transfer.pendingMilli, 3000);
  assert.deepEqual((await cat(s, '/transfers?status=open')).body.transfers.map((x) => x.id), [t.id]);

  //  بستن: x1 فقط ۱ از ۲ ِ مانده رسید (کسری = ۱، با دلیل)؛ x2 نیامده ⇒ بقیه‌اش رسید
  assert.equal((await receive(s, t.id, { lines: [R('x1', 1000)] })).body.error.code, 'reason_required');
  const done = await receive(s, t.id, { lines: [R('x1', 1000)], reason: 'یک کارتن شکسته رسید' });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.transfer.status, 'received');
  assert.deepEqual(done.body.transfer.lines.map((l) => [l.productId, l.sentMilli, l.receivedMilli, l.shortMilli, l.pendingMilli]),
    [['x1', 6000, 5000, 1000, 0], ['x2', 2000, 2000, 0, 0]]);
  assert.deepEqual(await levels(s, 'x1'), { floor: 0, wh: 5000, transit: 0 });
  assert.deepEqual(await levels(s, 'x2'), { floor: 4000, wh: 2000, transit: 0 });
  const tasks = (await cat(s, '/tasks?status=open')).body.tasks.filter((x) => x.kind === 'transfer_short');
  assert.deepEqual(tasks.map((x) => [x.productId, x.detail.shortMilli, x.detail.receivedMilli]), [['x1', 1000, 5000]]);
  //  بسته دوباره بسته نمی‌شود
  assert.equal((await receive(s, t.id, { final: false, lines: [R('x2', 1)] })).body.error.code, 'transfer_closed');
  //  هر گام یک ردیف: ارسال + سه دریافت
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfer_steps WHERE shop_id=$1 AND transfer_id=$2', [s.shopId, t.id]), 4);
  await reconciled(s);
});

test('INV-11 ناقص: رد پس از دریافتِ ناقص فقط ماندهٔ در مسیر را به مبدأ برمی‌گرداند', async () => {
  const s = await seeded('ردِ ناقص');
  const t = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 5000)] })).body.transfer;
  await receive(s, t.id, { final: false, lines: [R('x1', 2000)] });
  const r = await reject(s, t.id, { reason: 'بقیه اشتباه بار شد' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.transfer.status, 'rejected');
  assert.deepEqual(r.body.transfer.lines.map((l) => [l.receivedMilli, l.pendingMilli]), [[2000, 0]]);
  assert.deepEqual(await levels(s, 'x1'), { floor: 4000, wh: 2000, transit: 0 });
  await reconciled(s);
});

// ── بی‌اینترنت: گام‌ها از دفترِ دستگاه ────────────────────────────────

test('INV-11 بی‌اینترنت: ارسال و دو دریافتِ دستگاه در یک push؛ فرستادنِ دوباره و خواندنِ کامل بی اثرِ دوم', async () => {
  const s = await seeded('دستگاه');
  //  دستگاه بی اینترنت فرستاد، نیمی را رساند و بعد بقیه را با کسری بست — هر سه پس از وصل شدن یک‌جا می‌رسند
  const ops = [
    step('xs-send-1', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 4000), L('x2', 3000)], note: 'به انبار' }),
    step('xs-recv-1', { step: 'receive', sendKey: 'xs-send-1', final: false, lines: [L('x1', 3000)] }),
    step('xs-recv-2', { step: 'receive', sendKey: 'xs-send-1', final: true, lines: [L('x1', 0)], reason: 'یکی گم شد' }),
  ];
  await push(s, ops);
  const body = await transfersOf(s, ['xs-send-1', 'xs-recv-1', 'xs-recv-2', 'xs-unknown']);
  assert.equal(body.transfers.length, 1);
  const t = body.transfers[0];
  assert.equal(t.sendKey, 'xs-send-1');
  assert.equal(t.status, 'received');
  assert.deepEqual(t.lines.map((l) => [l.productId, l.receivedMilli, l.shortMilli]), [['x1', 3000, 1000], ['x2', 3000, 0]]);
  assert.deepEqual(body.steps.map((x) => [x.key, x.kind, x.outcome, x.transferId === t.id, x.origin]), [
    ['xs-send-1', 'send', 'applied', true, 'device'],
    ['xs-recv-1', 'receive', 'applied', true, 'device'],
    ['xs-recv-2', 'receive', 'applied', true, 'device'],
  ]);
  const want = { x1: { floor: 2000, wh: 3000, transit: 0 }, x2: { floor: 3000, wh: 3000, transit: 0 } };
  assert.deepEqual(await levels(s, 'x1'), want.x1);
  assert.deepEqual(await levels(s, 'x2'), want.x2);

  //  پاسخِ push گم شد و برنامه همان ردیف‌ها را با op ِ تازه دوباره فرستاد ⇒ هیچ اثرِ دومی
  await push(s, ops.map((o) => ({ ...o, op_id: opId() })));
  //  و مدیر دفتر را از اول خواند
  const full = await h.post('/api/shop/catalog/refresh', { full: true }, { token: s.token });
  assert.equal(full.status, 200, JSON.stringify(full.body));
  assert.equal((await transfersOf(s)).transfers.length, 1);
  assert.deepEqual(await levels(s, 'x1'), want.x1);
  assert.deepEqual(await levels(s, 'x2'), want.x2);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfer_steps WHERE shop_id=$1', [s.shopId]), 3);
  await reconciled(s);
});

test('INV-11 بی‌اینترنت: گامِ نپذیرفته (کمبود) ثبت می‌شود، هیچ حرکتی نمی‌گذارد و صف را نمی‌بندد', async () => {
  const s = await seeded('کمبودِ دستگاه', 3);
  await push(s, [
    step('xs-big-0001', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000), L('x2', 9000)] }),
    step('xs-ok-00001', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x2', 1000)] }),
    step('xs-bad-0001', { step: 'receive', transferId: 'xfr_not_here', final: true, lines: [] }),
    step('xs-odd-0001', { step: 'teleport', lines: [L('x1', 1)] }),
  ]);
  const body = await transfersOf(s, ['xs-big-0001', 'xs-ok-00001', 'xs-bad-0001', 'xs-odd-0001']);
  const by = Object.fromEntries(body.steps.map((x) => [x.key, [x.kind, x.outcome, x.code]]));
  assert.deepEqual(by, {
    'xs-big-0001': ['send', 'refused', 'insufficient_stock'],
    'xs-ok-00001': ['send', 'applied', ''],
    'xs-bad-0001': ['receive', 'refused', 'transfer_not_found'],
    'xs-odd-0001': ['other', 'refused', 'bad_step'],
  });
  //  از ارسالِ ردشده (حتی ردیفِ نخستش، x1) هیچ نماند؛ ارسالِ بعدی نشست
  assert.deepEqual(await levels(s, 'x1'), { floor: 3000, wh: 0, transit: 0 });
  assert.deepEqual(await levels(s, 'x2'), { floor: 2000, wh: 0, transit: 1000 });
  assert.deepEqual(body.transfers.map((x) => x.sendKey), ['xs-ok-00001']);
  //  هرگز بی‌صدا: وظیفهٔ مدیر برای هر گامِ رد
  const refused = (await cat(s, '/tasks?status=open')).body.tasks.filter((x) => x.kind === 'transfer_refused');
  assert.deepEqual(refused.map((x) => x.detail.stepKey).sort(), ['xs-bad-0001', 'xs-big-0001', 'xs-odd-0001']);
  //  دوباره خواندن همان؛ رد شده دوباره داوری نمی‌شود
  await h.post('/api/shop/catalog/refresh', { full: true }, { token: s.token });
  assert.equal(await count("SELECT COUNT(*)::int AS n FROM stock_transfer_steps WHERE shop_id=$1 AND outcome='refused'", [s.shopId]), 3);
  //  کلیدِ گامِ ردشده با REST همان ۴۰۹ ِ رد است، نه اجرای دوباره
  const viaApi = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }, { key: 'xs-big-0001' });
  assert.equal(viaApi.status, 409);
  await reconciled(s);
});

test('INV-11 بی‌اینترنت: دکانِ دیگر با شناسهٔ انتقالِ این دکان به آن نمی‌رسد', async () => {
  const a = await seeded('دکانِ الف');
  const b = await seeded('دکانِ ب');
  const t = (await send(a, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000)] })).body.transfer;
  await push(b, [step('xs-steal', { step: 'reject', transferId: t.id, reason: 'دزدی' }), step('xs-steal2', { step: 'receive', sendKey: t.sendKey, lines: [] })]);
  const bb = await transfersOf(b, ['xs-steal', 'xs-steal2']);
  assert.deepEqual(bb.steps.map((x) => x.code), ['transfer_not_found', 'transfer_not_found']);
  assert.deepEqual(bb.transfers, []);
  //  الف دست‌نخورده؛ و گام‌های ب برای الف دیده نمی‌شوند
  const aa = await transfersOf(a, ['xs-steal', 'xs-steal2']);
  assert.deepEqual(aa.steps, []);
  assert.equal(aa.transfers[0].status, 'in_transit');
  assert.deepEqual(await levels(a, 'x1'), { floor: 4000, wh: 0, transit: 2000 });
});

test('INV-11 بی‌اینترنت: پرچمِ shop.catalog خاموش ⇒ ردیفِ دستگاه بی‌اثر و پاسخِ فهرست همان شکلِ پیشین', async () => {
  const s = await shop('بی‌پرچمِ دستگاه');
  await push(s, [product('x1'), entry('x1-e', 'x1', 5), step('xs-off', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] })]);
  assert.equal((await cat(s, '/transfers?keys=xs-off')).status, 403);
  assert.equal((await cat(s, '/config')).body.enabled, false);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfer_steps WHERE shop_id=$1', [s.shopId]), 0);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfers WHERE shop_id=$1', [s.shopId]), 0);
  //  بی `keys` پاسخ فقط `transfers` دارد (همان شکلِ پیشین)
  await enable(s);
  assert.deepEqual(Object.keys(await transfersOf(s)), ['transfers']);
});

test('مهاجرتِ ۲۶۵: rollback با انتقالِ نیمه‌رسیده می‌ایستد؛ پس از بستن جدول و ردیفش را برمی‌دارد و دوباره نشستن همان', async () => {
  const s = await seeded('برگشتِ ۲۶۵');
  const t = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000)] })).body.transfer;
  await receive(s, t.id, { final: false, lines: [R('x1', 1000)] });
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '265_stock_transfer_steps.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async () => (await query("SELECT 1 FROM information_schema.tables WHERE table_name='stock_transfer_steps'")).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await assert.rejects(() => query(down), /partial/);
  assert.equal(await has(), 1);
  await receive(s, t.id, { lines: [] });
  await query(down);
  assert.equal(await has(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has(), 1);
  await reconciled(s);
});
