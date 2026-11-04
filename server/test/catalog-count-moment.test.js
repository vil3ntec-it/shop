'use strict';
/**
 * شمارشِ مکانِ سرور با لحظهٔ شمردن — INV-10 (فروش‌های حینِ شمارش خودکار لحاظ می‌شوند)، سناریوی S20 (v3 §۶.۵).
 *
 * `POST /api/shop/catalog/moves` با `kind: 'count'` اختلاف را با موجودیِ **همین لحظهٔ سرور** می‌سنجید. شمارشی که
 * دیرتر از شمردن می‌رسد (شمارندهٔ آفلاین، صفِ دوباره‌فرستادن، یا فروشِ صندوق در همان فاصله) فروش‌های پس از شمردن را
 * دوباره «اضافه» می‌کرد: ۲۰ شمرده، سپس ۳ فروش ⇒ ۲۰ (۳ کالای ساختگی). حالا `countedAt` (اختیاری، میلی‌ثانیه):
 * مورد انتظار = موجودیِ امروزِ همان مکان − جمعِ نشانه‌دارِ حرکت‌های همان کالا و همان مکان پس از آن لحظه.
 *   ⛔ بی `countedAt` ⇒ بیت‌به‌بیت همان رفتارِ پیشین (catalog-stock.test.js).
 *   ⛔ لحظهٔ آینده (بیش از ۵ دقیقه) یا کهنه‌تر از ۳۰ روز یا نادرست ⇒ ۴۰۰ و هیچ ردیف.
 *   ⛔ کلیدِ یکتایی همان: تکرار ⇒ همان نتیجه؛ دفتر = مانده‌ها = بازپخشِ هسته.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
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
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-c-${made.body.shop.id}`.slice(0, 60) };
}
async function enable(s) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key: 'shop.catalog', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
const opId = () => `01JCM${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
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
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() - 3_600_000,
});
/** فروشِ دستگاه در لحظهٔ [at] (همان `createdAt` ِ قلم که سرور زمانِ حرکت می‌گیرد) */
const sale = (id, pid, q, at) => [
  op('insert', 'sales', id, { status: 'completed', total: 0, createdAt: at }),
  op('insert', 'saleItems', `${id}-i`, { saleId: id, productId: pid, quantity: q, returnedQty: 0, unitPrice: 50, createdAt: at }),
];

let keyN = 0;
const fresh = () => `count-at-${++keyN}-${Date.now()}`;
const cat = (s, p) => h.get(`/api/shop/catalog${p}`, { token: s.token });
const mv = (s, body, key = fresh()) => h.post('/api/shop/catalog/moves', body, { token: s.token, headers: { 'Idempotency-Key': key } });
const count = (pid, countedMilli, extra = {}) => ({ kind: 'count', productId: pid, location: 'shop_floor', countedMilli, reason: 'انبارگردانی', ...extra });

async function floor(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock.levels.shop_floor;
}
async function reconciled(s) {
  const r = await cat(s, '/report');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
async function seeded(name, units = 20) {
  const s = await shop(name);
  await enable(s);
  await push(s, [product('c1'), entry('c1-e', 'c1', units)]);
  assert.equal(await floor(s, 'c1'), units * 1000);
  return s;
}

// ── آزمون‌ها ──────────────────────────────────────────────────────────

test('S20: ۲۰ شمرده و ۳ فروش پس از شمردن ⇒ بی اصلاح و موجودیِ ۱۷، نه ۲۰ ِ ساختگی', async () => {
  const s = await seeded('پس‌از‌شمارش');
  const countedAt = Date.now() - 120_000;
  await push(s, sale('sa', 'c1', 3, countedAt + 30_000));
  assert.equal(await floor(s, 'c1'), 17_000);
  const r = await mv(s, count('c1', 20_000, { countedAt }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.unchanged, true);
  assert.equal(await floor(s, 'c1'), 17_000);
  await reconciled(s);
});

test('S20: ۱۷ شمرده (۱۸ مورد انتظار) ⇒ فقط −۱ و ۱۴؛ فروشِ پیش از شمردن در عدد است و دوباره کم نمی‌شود', async () => {
  const s = await seeded('یک‌کسری');
  const countedAt = Date.now() - 120_000;
  await push(s, [...sale('sb', 'c1', 2, countedAt - 30_000), ...sale('sc', 'c1', 3, countedAt + 30_000)]);
  assert.equal(await floor(s, 'c1'), 15_000);
  //  ۲۰ − ۲ (پیش از شمردن) = ۱۸ مورد انتظار؛ ۱۷ شمرده ⇒ −۱
  const r = await mv(s, count('c1', 17_000, { countedAt }));
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.deepEqual(r.body.moves.map((x) => [x.type, x.qtyMilli]), [['count_adjust', -1000]]);
  assert.equal(await floor(s, 'c1'), 14_000);
  await reconciled(s);
});

test('حرکتِ همان مکان پس از شمردن (انتقال به انبار) هم کنار می‌رود؛ مکانِ دیگر نه', async () => {
  const s = await seeded('انتقال');
  const countedAt = Date.now() - 60_000;
  const t = await mv(s, { kind: 'transfer', productId: 'c1', from: 'shop_floor', to: 'warehouse', qtyMilli: 2000 });
  assert.equal(t.status, 201, JSON.stringify(t.body));
  assert.equal(await floor(s, 'c1'), 18_000);
  const r = await mv(s, count('c1', 20_000, { countedAt }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.unchanged, true);
  await reconciled(s);
});

test('بی countedAt همان رفتارِ پیشین: اختلاف با موجودیِ همین لحظه', async () => {
  const s = await seeded('بی‌لحظه');
  await push(s, sale('sd', 'c1', 3, Date.now() - 30_000));
  const r = await mv(s, count('c1', 20_000));
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.deepEqual(r.body.moves.map((x) => [x.type, x.qtyMilli]), [['count_adjust', 3000]]);
  await reconciled(s);
});

test('countedAt ِ آینده، کهنه یا نادرست ⇒ ۴۰۰ و هیچ ردیف؛ تکرارِ همان کلید ⇒ همان نتیجه', async () => {
  const s = await seeded('لحظهٔ بد');
  const before = (await cat(s, '/moves?productId=c1&limit=100')).body.items.length;
  for (const bad of [Date.now() + 3_600_000, Date.now() - 31 * 86_400_000, 1.5, '123', -1, 0]) {
    const r = await mv(s, count('c1', 19_000, { countedAt: bad }));
    assert.equal(r.status, 400, `${bad}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_counted_at', String(bad));
  }
  assert.equal((await cat(s, '/moves?productId=c1&limit=100')).body.items.length, before);
  const key = fresh();
  const body = count('c1', 19_000, { countedAt: Date.now() - 10_000 });
  const a = await mv(s, body, key);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await mv(s, body, key);
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal(b.body.replayed, true);
  assert.equal(await floor(s, 'c1'), 19_000);
  //  همان کلید با لحظهٔ دیگر ⇒ ۴۰۹
  const c = await mv(s, { ...body, countedAt: body.countedAt - 1 }, key);
  assert.equal(c.status, 409);
  await reconciled(s);
});
