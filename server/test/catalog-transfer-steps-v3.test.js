'use strict';
/**
 * INV-11 (ادامه): گامِ بی‌اینترنتِ انتقال از راهِ **Sync v3**، و STF-06 برای همین گام‌ها (مهاجرتِ ۲۸۰).
 *
 * `catalog-transfer-steps.test.js` همان گام‌ها را از `POST /api/sync/v1/push` می‌سنجد؛ این‌جا دکانِ با منبعِ v3
 * (`syncSource: 'v3'`) همان ردیف‌های `auditLog` ِ `stock_transfer` را با `POST /api/sync/v3/push` (HLC، فیلدهای v3)
 * می‌فرستد و سرور هنگامِ خواندنِ `sync_v3_rows` داوری می‌کند. آن‌چه قفل می‌شود:
 *   ⛔ v3: ارسال و دو دریافتِ دستگاه در یک push؛ فرستادنِ دوباره (op ِ تازه، همان ردیف) و خواندنِ کاملِ دوباره بی اثرِ دوم؛
 *      گامِ نپذیرفته (کمبود) با کد و وظیفه ثبت می‌شود و صف را نمی‌بندد. دفتر = مانده‌ها.
 *   ⛔ STF-06: عضوِ محدود (پرچمِ `shop.staff_scope`) — هویت فقط از توکنِ push، نه `by` ِ ردیف — ارسال از بیرونِ محدوده و
 *      دریافت/رد در مقصدِ بیرونِ محدوده ⇒ «نپذیرفته» با `location_out_of_scope`، بی هیچ حرکت؛ درونِ محدوده می‌نشیند.
 *      همان در v1. مالک هرگز محدود نیست؛ پرچم خاموش ⇒ هیچ ردیفِ ۲۸۰ و همان رفتارِ پیشین. `by` ِ دروغ چیزی را باز نمی‌کند.
 *   ⛔ rollback ِ ۲۸۰ جدول و ردیفِ خودش را برمی‌دارد و دوباره نشستن همان.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const fs = require('fs');
const path = require('path');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
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
test.after(async () => { await h.stop(); });

// ── کمکی‌ها ────────────────────────────────────────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-v3-${made.body.shop.id}`.slice(0, 60) };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, device: `dev-m-${u.user.id}`.slice(0, 60) };
}
async function flag(s, key, value = true) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
let tick = 0;
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++tick) * 10, counter: 0, node: 'v3xfer' });
const oid = () => `01JV3XF${String(++opN).padStart(19, '0')}`;
/** push ِ v3؛ هیچ op ِ ردشده‌ای نباید باشد (`ok` = applied/duplicate/…) */
async function pushV3(who, ops) {
  const r = await h.api('POST', '/api/sync/v3/push', {
    token: who.token, body: { device_id: who.device, protocol: 3, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results || []) assert.notEqual(x.status, 'rejected', JSON.stringify(x));
  return r.body.results;
}
const v3op = (table, rowId, fields) => ({ op_id: oid(), table, row_id: rowId, kind: 'insert', hlc: H(), fields });
const product3 = (id) => v3op('products', id, { name: `کالای ${id}`, unit: 'دانه', salePriceMinor: 5000, purchasePriceMinor: 4000, active: true });
const entry3 = (id, pid, units) => v3op('warehouseEntries', id, { productId: pid, unitsMilli: units * 1000 });
/** گامِ انتقالِ دفترِ دستگاه — همان شکلِ `StockTransferBook.row` (در v3 هم `xfer` شیء است) */
const step3 = (rowId, xfer, by = 'دستگاه') => v3op('auditLog', rowId, {
  type: 'stock_transfer', date: '2026-11-08', createdAt: Date.now(), notes: 'انتقال', by, xfer: { v: 1, ...xfer },
});

let v1N = 0;
const v1id = () => `01JXV${String(Date.now()).slice(-8)}${String(++v1N).padStart(8, '0')}`;
async function pushV1(who, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: who.token, body: { device_id: who.device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': who.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}
const v1op = (table, rowId, fields) => ({ op_id: v1id(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
const product1 = (id) => v1op('products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
const entry1 = (id, pid, units) => v1op('warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});
const step1 = (rowId, xfer, by = 'دستگاه') => v1op('auditLog', rowId, {
  type: 'stock_transfer', date: '2026-11-08', createdAt: Date.now(), notes: 'انتقال', by, xfer: { v: 1, ...xfer },
});

let keyN = 0;
const fresh = () => `xv3-test-${++keyN}-${Date.now()}`;
const cat = (s, p, token = s.token) => h.get(`/api/shop/catalog${p}`, { token });
const send = (s, body) => h.post('/api/shop/catalog/transfers', body, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
const L = (productId, qtyMilli) => ({ productId, qtyMilli });
const count = async (sql, params) => (await one(sql, params)).n;

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
const outcomes = (body) => Object.fromEntries(body.steps.map((x) => [x.key, [x.kind, x.outcome, x.code]]));

/** دکانِ پرچم‌روشن با منبعِ v3؛ دو کالا، هر کدام `units` روی قفسه */
async function seededV3(name, units = 6) {
  const s = await shop(name);
  await flag(s, 'shop.catalog');
  const st = await h.put('/api/shop/catalog/settings', { syncSource: 'v3' }, { token: s.token });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  await pushV3(s, [product3('x1'), entry3('x1-e', 'x1', units), product3('x2'), entry3('x2-e', 'x2', units)]);
  assert.deepEqual(await levels(s, 'x1'), { floor: units * 1000, wh: 0, transit: 0 });
  return s;
}

// ── Sync v3 ────────────────────────────────────────────────────────────

test('INV-11 v3: ارسال و دو دریافتِ دستگاه در یک push ِ v3؛ فرستادنِ دوباره و خواندنِ کامل بی اثرِ دوم', async () => {
  const s = await seededV3('سه‌گام');
  const ops = [
    step3('x3-send-1', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 4000), L('x2', 3000)], note: 'به انبار' }),
    step3('x3-recv-1', { step: 'receive', sendKey: 'x3-send-1', final: false, lines: [L('x1', 3000)] }),
    step3('x3-recv-2', { step: 'receive', sendKey: 'x3-send-1', final: true, lines: [L('x1', 0)], reason: 'یکی گم شد' }),
  ];
  await pushV3(s, ops);
  const body = await transfersOf(s, ['x3-send-1', 'x3-recv-1', 'x3-recv-2']);
  assert.equal(body.transfers.length, 1);
  const t = body.transfers[0];
  assert.equal(t.sendKey, 'x3-send-1');
  assert.equal(t.status, 'received');
  assert.deepEqual(t.lines.map((l) => [l.productId, l.receivedMilli, l.shortMilli]), [['x1', 3000, 1000], ['x2', 3000, 0]]);
  assert.deepEqual(body.steps.map((x) => [x.key, x.kind, x.outcome, x.transferId === t.id, x.origin]), [
    ['x3-send-1', 'send', 'applied', true, 'device'],
    ['x3-recv-1', 'receive', 'applied', true, 'device'],
    ['x3-recv-2', 'receive', 'applied', true, 'device'],
  ]);
  const want = { x1: { floor: 2000, wh: 3000, transit: 0 }, x2: { floor: 3000, wh: 3000, transit: 0 } };
  assert.deepEqual(await levels(s, 'x1'), want.x1);
  assert.deepEqual(await levels(s, 'x2'), want.x2);
  //  کسری هرگز بی‌صدا: وظیفهٔ مدیر
  assert.ok((await cat(s, '/tasks?status=open')).body.tasks.some((x) => x.kind === 'transfer_short'));

  //  پاسخِ push گم شد و برنامه همان ردیف‌ها را با op ِ تازه (و HLC ِ تازه) دوباره فرستاد ⇒ هیچ اثرِ دومی
  await pushV3(s, ops.map((o) => ({ ...o, op_id: oid(), hlc: H() })));
  const full = await h.post('/api/shop/catalog/refresh', { full: true }, { token: s.token });
  assert.equal(full.status, 200, JSON.stringify(full.body));
  assert.equal((await transfersOf(s)).transfers.length, 1);
  assert.deepEqual(await levels(s, 'x1'), want.x1);
  assert.deepEqual(await levels(s, 'x2'), want.x2);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfer_steps WHERE shop_id=$1', [s.shopId]), 3);
  //  گام‌ها از sync_v3_rows آمدند، نه v1
  assert.equal(await count(
    "SELECT COUNT(*)::int AS n FROM sync_rows WHERE account_id=$1 AND table_name='auditLog'", [s.shopId]), 0);
  await reconciled(s);
});

test('INV-11 v3: گامِ نپذیرفته (کمبود، انتقالِ ناشناس) ثبت می‌شود، هیچ حرکتی نمی‌گذارد و صف را نمی‌بندد', async () => {
  const s = await seededV3('کمبودِ سه', 3);
  await pushV3(s, [
    step3('x3-big-0001', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000), L('x2', 9000)] }),
    step3('x3-ok-00001', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x2', 1000)] }),
    step3('x3-bad-0001', { step: 'reject', transferId: 'xfr_not_here', reason: 'نیامد' }),
  ]);
  const body = await transfersOf(s, ['x3-big-0001', 'x3-ok-00001', 'x3-bad-0001']);
  assert.deepEqual(outcomes(body), {
    'x3-big-0001': ['send', 'refused', 'insufficient_stock'],
    'x3-ok-00001': ['send', 'applied', ''],
    'x3-bad-0001': ['reject', 'refused', 'transfer_not_found'],
  });
  assert.deepEqual(await levels(s, 'x1'), { floor: 3000, wh: 0, transit: 0 });
  assert.deepEqual(await levels(s, 'x2'), { floor: 2000, wh: 0, transit: 1000 });
  const refused = (await cat(s, '/tasks?status=open')).body.tasks.filter((x) => x.kind === 'transfer_refused');
  assert.deepEqual(refused.map((x) => x.detail.stepKey).sort(), ['x3-bad-0001', 'x3-big-0001']);
  await reconciled(s);
});

// ── STF-06 برای گامِ دستگاه ────────────────────────────────────────────

test('STF-06 v3: عضوِ محدود به انبار — گامِ بیرونِ محدوده «نپذیرفته»، درونِ محدوده می‌نشیند؛ مالک آزاد؛ `by` ِ دروغ بی‌اثر', async () => {
  const s = await seededV3('محدودهٔ سه');
  await flag(s, 'shop.staff_scope');
  const m = await member(s, 'manager', 'انباردارِ سه');
  const set = await h.put(`/api/shop/staff-scope/members/${m.user.id}`, { places: ['warehouse'] }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));

  //  مالک دو انتقال می‌فرستد: یکی به انبار (مقصد در محدودهٔ عضو) و یکی به قفسه
  const toWh = (await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000)] })).body.transfer;
  const back = await send(s, { from: 'shop_floor', to: 'warehouse', lines: [L('x2', 3000)] });
  //  ارسال از انبار به قفسه برای انتقالِ دوم: اول بگذار برسد
  await h.post(`/api/shop/catalog/transfers/${back.body.transfer.id}/receive`, { lines: [] }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  const toFloor = (await send(s, { from: 'warehouse', to: 'shop_floor', lines: [L('x2', 1000)] })).body.transfer;

  //  عضو (توکنِ خودش) بی اینترنت: ارسال از قفسه (بیرون)، ارسال از انبار (درون)، دریافت در انبار (درون)، رد در قفسه (بیرون)
  //  — و ادعای «مالک» در `by` هیچ چیز را باز نمی‌کند
  await pushV3(m, [
    step3('sc3-send-out', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }, 'مالک'),
    step3('sc3-send-in1', { step: 'send', from: 'warehouse', to: 'shop_floor', lines: [L('x2', 1000)] }),
    step3('sc3-recv-in1', { step: 'receive', transferId: toWh.id, final: true, lines: [] }),
    step3('sc3-rjct-out', { step: 'reject', transferId: toFloor.id, reason: 'نمی‌خواهم' }, 'مالک'),
  ]);
  const body = await transfersOf(s, ['sc3-send-out', 'sc3-send-in1', 'sc3-recv-in1', 'sc3-rjct-out']);
  assert.deepEqual(outcomes(body), {
    'sc3-send-out': ['send', 'refused', 'location_out_of_scope'],
    'sc3-send-in1': ['send', 'applied', ''],
    'sc3-recv-in1': ['receive', 'applied', ''],
    'sc3-rjct-out': ['reject', 'refused', 'location_out_of_scope'],
  });
  const byId = Object.fromEntries(body.transfers.map((t) => [t.id, t.status]));
  assert.equal(byId[toWh.id], 'received');
  assert.equal(byId[toFloor.id], 'in_transit', 'ردِ بیرونِ محدوده هیچ اثری ندارد');
  //  هرگز بی‌صدا: وظیفهٔ مدیر با همان کد
  const refused = (await cat(s, '/tasks?status=open')).body.tasks.filter((x) => x.kind === 'transfer_refused');
  assert.deepEqual(refused.map((x) => [x.detail.stepKey, x.detail.code]).sort(),
    [['sc3-rjct-out', 'location_out_of_scope'], ['sc3-send-out', 'location_out_of_scope']]);
  //  ردیفِ ۲۸۰ فقط برای گام‌های همین عضو، با راهِ v3
  const marks = (await query('SELECT step_key, user_id, places, via FROM stock_transfer_step_scopes WHERE shop_id=$1 ORDER BY step_key', [s.shopId])).rows;
  assert.deepEqual(marks.map((r) => [r.step_key, r.user_id === m.user.id, r.via]),
    [['sc3-recv-in1', true, 'v3'], ['sc3-rjct-out', true, 'v3'], ['sc3-send-in1', true, 'v3'], ['sc3-send-out', true, 'v3']]);
  assert.deepEqual(typeof marks[0].places === 'string' ? JSON.parse(marks[0].places) : marks[0].places, ['warehouse']);

  //  همان ردیفِ ردشده که بعداً با توکنِ مالک دوباره رسید ⇒ ردیف پیش‌تر نشسته، داوری عوض نمی‌شود
  await pushV3(s, [{ ...step3('sc3-send-out', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }) }]);
  await h.post('/api/shop/catalog/refresh', { full: true }, { token: s.token });
  assert.deepEqual(outcomes(await transfersOf(s, ['sc3-send-out']))['sc3-send-out'], ['send', 'refused', 'location_out_of_scope']);
  //  مالک خودش همان کار را می‌کند ⇒ می‌نشیند (مالک هرگز محدود نیست)
  await pushV3(s, [step3('sc3-own-rjct', { step: 'reject', transferId: toFloor.id, reason: 'برگشت' })]);
  assert.deepEqual(outcomes(await transfersOf(s, ['sc3-own-rjct']))['sc3-own-rjct'], ['reject', 'applied', '']);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfer_step_scopes WHERE shop_id=$1', [s.shopId]), 4);
  await reconciled(s);
});

test('STF-06 v1: همان داوری از راهِ v1؛ پرچمِ محدوده خاموش ⇒ هیچ ردیفِ ۲۸۰ و همان رفتارِ پیشین', async () => {
  const s = await shop('محدودهٔ یک');
  await flag(s, 'shop.catalog');
  await pushV1(s, [product1('x1'), entry1('x1-e', 'x1', 6)]);
  await levels(s, 'x1');
  await flag(s, 'shop.staff_scope');
  const m = await member(s, 'manager', 'قفسه‌دارِ یک');
  const set = await h.put(`/api/shop/staff-scope/members/${m.user.id}`, { places: ['warehouse'] }, { token: s.token });
  assert.equal(set.status, 200, JSON.stringify(set.body));

  await pushV1(m, [step1('sc1-send-out', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }, 'مالک')]);
  assert.deepEqual(outcomes(await transfersOf(s, ['sc1-send-out'])), { 'sc1-send-out': ['send', 'refused', 'location_out_of_scope'] });
  assert.deepEqual(await levels(s, 'x1'), { floor: 6000, wh: 0, transit: 0 });
  assert.equal(await count("SELECT COUNT(*)::int AS n FROM stock_transfer_step_scopes WHERE shop_id=$1 AND via='v1'", [s.shopId]), 1);

  //  پرچم خاموش ⇒ محدوده‌ای نیست: همان گام از همان عضو می‌نشیند و هیچ ردیفِ ۲۸۰ ِ تازه‌ای نوشته نمی‌شود
  await flag(s, 'shop.staff_scope', false);
  await pushV1(m, [step1('sc1-send-off', { step: 'send', from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] })]);
  assert.deepEqual(outcomes(await transfersOf(s, ['sc1-send-off'])), { 'sc1-send-off': ['send', 'applied', ''] });
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM stock_transfer_step_scopes WHERE shop_id=$1', [s.shopId]), 1);
  assert.deepEqual(await levels(s, 'x1'), { floor: 5000, wh: 0, transit: 1000 });
  await reconciled(s);
});

test('مهاجرتِ ۲۸۰: rollback جدول و ردیفِ خودش را برمی‌دارد؛ دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '280_transfer_step_scopes.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async () => (await query("SELECT 1 FROM information_schema.tables WHERE table_name='stock_transfer_step_scopes'")).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has(), 1);
});
