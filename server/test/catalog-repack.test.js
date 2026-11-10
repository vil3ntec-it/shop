'use strict';
/**
 * INV-21 — بسته‌بندیِ دوباره و ساختِ بسته در دفترِ موجودیِ سرور (`lib/catalog-repack.js`، پرچمِ خاموشِ `shop.catalog_repack`).
 *
 *   ⛔ خاموش ⇒ دو نوعِ تازه ناشناخته (۴۰۰ `bad_kind`)، `/config` همان، و ردیفِ تولیدِ برنامه همان «رسید + اصلاح» ِ امروز.
 *   ⛔ روشن ⇒ یک درخواست = یک سند: `production` ِ منفی از هر ورودی و `production` ِ مثبت به خروجی، یک تراکنش، همان
 *      `ref_type`/`ref_id`، دقیق به هزارم (نادرست ⇒ ۴۰۰ و هیچ ردیف)، کمبود ⇒ ۴۰۹ و هیچ ردیف، تکرار ⇒ همان حرکت‌ها،
 *      دکان از توکن (کالای دکانِ دیگر ⇒ ۴۰۴)، دفتر = مانده‌ها = بازپخشِ هسته (`/report`).
 *   ⛔ بها: دفترِ سرور مقدار است (D-23) ⇒ هیچ ستونِ پولی در حرکت نیست.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const repack = require('../src/lib/catalog-repack');

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

// ── کمکی‌ها (همان راهِ catalog-count-moment.test.js) ──────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-r-${made.body.shop.id}`.slice(0, 60) };
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

let opN = 0;
const opId = () => `01JRP${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id, unit = 'دانه') => op('insert', 'products', id, {
  name: `کالای ${id}`, unit, salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
const entry = (id, pid, units, extra = {}) => op('insert', 'warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() - 3_600_000, ...extra,
});

let keyN = 0;
const fresh = () => `repack-${++keyN}-${Date.now()}`;
const cat = (s, p) => h.get(`/api/shop/catalog${p}`, { token: s.token });
const mv = (s, body, key = fresh(), token = s.token) => h.post('/api/shop/catalog/moves', body, { token, headers: { 'Idempotency-Key': key } });
const brk = (extra = {}) => ({ kind: 'repack', productId: 'box', toProductId: 'pc', qtyMilli: 2000, perPackMilli: 24_000, ...extra });

async function levels(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock.levels;
}
async function reconciled(s, productId = '') {
  const r = await cat(s, `/report${productId ? `?productId=${productId}` : ''}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
const movesOf = async (s) => (await query('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY created_at, id', [s.shopId])).rows;
/** کارتن ۵ عدد، دانه ۳ عدد، دو جزء و یک بسته — همه در قفسه (`deviceLocation` ِ پیش‌فرض) */
async function seeded(name, { on = true } = {}) {
  const s = await shop(name);
  await live(s.shopId, 'shop.catalog', true);
  if (on) await live(s.shopId, repack.FLAG_KEY, true);
  await push(s, [
    product('box', 'کارتن'), product('pc'), product('pa'), product('pb'), product('kit', 'بسته'),
    entry('box-e', 'box', 5), entry('pc-e', 'pc', 3), entry('pa-e', 'pa', 10), entry('pb-e', 'pb', 4),
  ]);
  assert.equal((await levels(s, 'box')).shop_floor, 5000);
  return s;
}

// ── آزمون‌ها ──────────────────────────────────────────────────────────

test('خاموش: repack/assemble ناشناخته‌اند، /config همان فهرست و هیچ حرکتی', async () => {
  const s = await seeded('خاموش', { on: false });
  const cfg = await cat(s, '/config');
  assert.deepEqual(cfg.body.kinds, ['transfer', 'damage', 'writeoff', 'return', 'adjust', 'count']);
  const before = (await movesOf(s)).length;
  for (const body of [brk(), { kind: 'assemble', productId: 'kit', qtyMilli: 1000, parts: [{ productId: 'pa', qtyMilli: 1000 }] }]) {
    const r = await mv(s, body);
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'bad_kind');
  }
  assert.equal((await movesOf(s)).length, before);
  //  درِ مستقیمِ کتابخانه هم (نه فقط مسیر)
  const catalog = require('../src/lib/catalog');
  await assert.rejects(catalog.manualMove(s.shopId, { key: fresh(), body: brk(), actor: { type: 'user', id: 'u' } }), (e) => e.code === 'bad_kind');
  assert.equal((await movesOf(s)).length, before);
});

test('شکستنِ ۲ کارتن × ۲۴ ⇒ یک سند: production ِ −۲ کارتن و +۴۸ دانه با همان پیوند؛ دفتر = مانده = بازپخش', async () => {
  const s = await seeded('شکستن');
  const cfg = await cat(s, '/config');
  assert.deepEqual(cfg.body.kinds.slice(-2), ['repack', 'assemble']);
  const key = fresh();
  const r = await mv(s, brk(), key);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.deepEqual(r.body.moves.map((m) => [m.type, m.productId, m.location, m.qtyMilli, m.deltaOnHandMilli]), [
    ['production', 'box', 'shop_floor', -2000, -2000],
    ['production', 'pc', 'shop_floor', 48_000, 48_000],
  ]);
  const [a, b] = r.body.moves;
  assert.equal(a.refType, 'repack');
  assert.equal(b.refType, 'repack');
  assert.ok(a.refId && a.refId === b.refId, 'هر دو حرکت به یک سند');
  assert.equal(a.mode, 'local');
  const rows = (await query('SELECT source_key FROM stock_moves WHERE shop_id=$1 AND ref_type=$2 ORDER BY source_key', [s.shopId, 'repack'])).rows;
  assert.deepEqual(rows.map((x) => x.source_key), [`staff:${key}:1`, `staff:${key}:2`]);
  assert.equal((await levels(s, 'box')).shop_floor, 3000);
  assert.equal((await levels(s, 'pc')).shop_floor, 51_000);
  //  هیچ ستونِ پولی در دفترِ سرور نیست (D-23): سند پولی نمی‌سازد و گم نمی‌کند
  const cols = (await query(`SELECT column_name FROM information_schema.columns WHERE table_name='stock_moves'`)).rows.map((x) => x.column_name);
  assert.deepEqual(cols.filter((c) => /minor|cost|price|amount/i.test(c)), []);
  await reconciled(s);
  await reconciled(s, 'box');
  await reconciled(s, 'pc');
});

test('تکرارِ همان کلید ⇒ همان حرکت‌ها بی اثرِ دوم؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹', async () => {
  const s = await seeded('تکرار');
  const key = fresh();
  const first = await mv(s, brk({ qtyMilli: 1000 }), key);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const again = await mv(s, brk({ qtyMilli: 1000 }), key);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.deepEqual(again.body.moves.map((m) => m.id), first.body.moves.map((m) => m.id));
  assert.equal((await levels(s, 'box')).shop_floor, 4000);
  assert.equal((await levels(s, 'pc')).shop_floor, 27_000);
  const other = await mv(s, brk({ qtyMilli: 2000 }), key);
  assert.equal(other.status, 409, JSON.stringify(other.body));
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  assert.equal((await levels(s, 'box')).shop_floor, 4000);
  await reconciled(s);
});

test('کمبودِ کارتن یا جزء در همان مکان ⇒ ۴۰۹ و هیچ ردیف (نه نیمی از سند)', async () => {
  const s = await seeded('کمبود');
  const before = (await movesOf(s)).length;
  const r = await mv(s, brk({ qtyMilli: 6000 }));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'insufficient_stock');
  //  کارتن در قفسه است، نه انبار
  const there = await mv(s, brk({ qtyMilli: 1000, location: 'warehouse' }));
  assert.equal(there.status, 409, JSON.stringify(there.body));
  //  جزءِ دوم کم است ⇒ جزءِ نخست هم نوشته نمی‌شود
  const kit = await mv(s, { kind: 'assemble', productId: 'kit', qtyMilli: 3000, parts: [{ productId: 'pa', qtyMilli: 1000 }, { productId: 'pb', qtyMilli: 2000 }] });
  assert.equal(kit.status, 409, JSON.stringify(kit.body));
  assert.equal((await movesOf(s)).length, before);
  assert.equal((await levels(s, 'box')).shop_floor, 5000);
  assert.equal((await levels(s, 'pa')).shop_floor, 10_000);
  await reconciled(s);
});

test('مقدارِ نادرست و کالای نادرست ⇒ ۴۰۰/۴۰۴ و هیچ ردیف؛ خروجی هرگز گرد نمی‌شود', async () => {
  const s = await seeded('نادرست');
  const before = (await movesOf(s)).length;
  const bad = [
    [brk({ qtyMilli: 1500, perPackMilli: 333 }), 400, 'repack_inexact'],   //  ۱٫۵ × ۰٫۳۳۳ = ۰٫۴۹۹۵
    [brk({ qtyMilli: 1, perPackMilli: 1 }), 400, 'repack_inexact'],
    [brk({ qtyMilli: 1_000_000_000, perPackMilli: 24_000 }), 400, 'repack_inexact'],   //  بیش از سقف
    [brk({ toProductId: 'box' }), 400, 'same_product'],
    [brk({ toProductId: '' }), 400, 'product_required'],
    [brk({ perPackMilli: 0 }), 400, 'bad_qty'],
    [brk({ qtyMilli: 1.5 }), 400, 'bad_qty'],
    [brk({ location: 'damaged' }), 400, 'bad_location'],
    [brk({ toProductId: 'nope' }), 404, 'product_not_found'],
    [{ kind: 'assemble', productId: 'kit', qtyMilli: 1000, parts: [] }, 400, 'parts_required'],
    [{ kind: 'assemble', productId: 'kit', qtyMilli: 1000, parts: [{ productId: 'kit', qtyMilli: 1000 }] }, 400, 'same_product'],
    [{ kind: 'assemble', productId: 'kit', qtyMilli: 1000, parts: [{ productId: 'pa', qtyMilli: 1000 }, { productId: 'pa', qtyMilli: 1000 }] }, 400, 'duplicate_part'],
    [{ kind: 'assemble', productId: 'kit', qtyMilli: 1500, parts: [{ productId: 'pa', qtyMilli: 333 }] }, 400, 'repack_inexact'],
    [{ kind: 'assemble', productId: 'kit', qtyMilli: 1000, parts: Array.from({ length: 21 }, (_, i) => ({ productId: `x${i}`, qtyMilli: 1000 })) }, 400, 'too_many_parts'],
  ];
  for (const [body, status, code] of bad) {
    const r = await mv(s, body);
    assert.equal(r.status, status, `${JSON.stringify(body)} ⇒ ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code, JSON.stringify(body));
  }
  assert.equal((await movesOf(s)).length, before);
  //  ۱٫۵ کارتن × ۲۴ = ۳۶ دانهٔ دقیق
  const ok = await mv(s, brk({ qtyMilli: 1500 }));
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.deepEqual(ok.body.moves.map((m) => m.qtyMilli), [-1500, 36_000]);
  assert.equal(repack.exactTimes(9_007_199, 1_000_000_000, 1_000_000_000), null);
  assert.equal(repack.exactTimes(999, 1_000_000, 1_000_000_000), 999_000);
});

test('ساختِ ۳ بسته از دو جزء ⇒ مصرفِ هر جزء × ۳ و +۳ بسته، همه با پیوندِ assembly؛ ۱۱ جزء به ترتیبِ سند', async () => {
  const s = await seeded('بسته');
  const r = await mv(s, { kind: 'assemble', productId: 'kit', qtyMilli: 3000, parts: [{ productId: 'pa', qtyMilli: 2000 }, { productId: 'pb', qtyMilli: 500 }] });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.deepEqual(r.body.moves.map((m) => [m.type, m.productId, m.qtyMilli, m.refType]), [
    ['production', 'pa', -6000, 'assembly'], ['production', 'pb', -1500, 'assembly'], ['production', 'kit', 3000, 'assembly'],
  ]);
  assert.equal(new Set(r.body.moves.map((m) => m.refId)).size, 1);
  assert.equal((await levels(s, 'pa')).shop_floor, 4000);
  assert.equal((await levels(s, 'pb')).shop_floor, 2500);
  assert.equal((await levels(s, 'kit')).shop_floor, 3000);
  await reconciled(s);

  //  ۱۱ جزء ⇒ ۱۲ حرکت (`staff:k:10` پس از `:9`)، بسته آخر؛ تکرار همان ترتیب
  const ids = Array.from({ length: 11 }, (_, i) => `q${i}`);
  await push(s, [...ids.map((id) => product(id)), ...ids.map((id) => entry(`${id}-e`, id, 2))]);
  const key = fresh();
  const big = { kind: 'assemble', productId: 'kit', qtyMilli: 1000, parts: ids.map((id) => ({ productId: id, qtyMilli: 1000 })) };
  const made = await mv(s, big, key);
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.deepEqual(made.body.moves.map((m) => m.productId), [...ids, 'kit']);
  const again = await mv(s, big, key);
  assert.equal(again.status, 200);
  assert.deepEqual(again.body.moves.map((m) => m.productId), [...ids, 'kit']);
  assert.equal((await levels(s, 'kit')).shop_floor, 4000);
  await reconciled(s);
});

test('دکان از توکن: کالای دکانِ دیگر ⇒ ۴۰۴؛ پرچمِ یک دکان دکانِ دیگر را روشن نمی‌کند؛ شاگرد ⇒ ۴۰۳', async () => {
  const a = await seeded('دکانِ الف');
  const b = await seeded('دکانِ ب', { on: false });
  const stranger = await shop('بیگانه');
  await live(stranger.shopId, 'shop.catalog', true);
  await live(stranger.shopId, repack.FLAG_KEY, true);
  const r = await mv(stranger, brk({ qtyMilli: 1000 }));
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'product_not_found');
  const offB = await mv(b, brk({ qtyMilli: 1000 }));
  assert.equal(offB.status, 400, JSON.stringify(offB.body));
  assert.equal(offB.body.error.code, 'bad_kind');
  const staff = await member(a, 'staff', 'شاگردِ الف');
  const denied = await mv(a, brk({ qtyMilli: 1000 }), fresh(), staff.token);
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  assert.equal(denied.body.error.code, 'permission_denied');
  const manager = await member(a, 'manager', 'مدیرِ الف');
  assert.equal((await mv(a, brk({ qtyMilli: 1000 }), fresh(), manager.token)).status, 201);
  assert.equal((await levels(a, 'box')).shop_floor, 4000);
  assert.equal((await levels(b, 'box')).shop_floor, 5000);
});

test('ردیفِ تولیدِ برنامه (adjKind = repack): روشن ⇒ production ِ پیوسته با کلیدِ کار؛ خاموش ⇒ همان رسید + اصلاح؛ موجودی یکی', async () => {
  const pair = (k) => [
    entry(`${k}-in`, 'box', -1, { isAdjustment: true, adjKind: 'repack', refId: k }),
    entry(`${k}-out`, 'pc', 24, { isAdjustment: true, adjKind: 'repack', refId: k, price: 1.67 }),
  ];
  const view = async (s) => (await movesOf(s)).filter((m) => m.ref_id === 'rk-1' || m.ref_type === 'warehouseEntries' && /^rk-1-/.test(m.ref_id))
    .map((m) => [m.type, m.product_id, Number(m.qty_milli), Number(m.delta_on_hand_milli), m.reason, m.ref_type, m.ref_id])
    .sort((x, y) => x[1].localeCompare(y[1]));   //  هم‌لحظه ⇒ به کالا

  const off = await seeded('دستگاهِ خاموش', { on: false });
  await push(off, pair('rk-1'));
  assert.equal((await levels(off, 'pc')).shop_floor, 27_000);
  assert.deepEqual(await view(off), [
    ['adjustment', 'box', -1000, -1000, 'device_correction', 'warehouseEntries', 'rk-1-in'],
    ['purchase_receipt', 'pc', 24_000, 24_000, 'device_receipt', 'warehouseEntries', 'rk-1-out'],
  ]);

  const on = await seeded('دستگاهِ روشن');
  await push(on, pair('rk-1'));
  assert.equal((await levels(on, 'box')).shop_floor, 4000);
  assert.equal((await levels(on, 'pc')).shop_floor, 27_000);
  assert.deepEqual(await view(on), [
    ['production', 'box', -1000, -1000, 'device_repack', 'repack', 'rk-1'],
    ['production', 'pc', 24_000, 24_000, 'device_repack', 'repack', 'rk-1'],
  ]);
  //  ردیفِ اصلاحِ دیگر (نه تولید) همان امروز
  await push(on, [entry('adj-1', 'pa', -2, { isAdjustment: true, adjKind: 'damage' })]);
  assert.equal((await levels(on, 'pa')).shop_floor, 8000);
  assert.deepEqual((await movesOf(on)).filter((m) => m.ref_id === 'adj-1').map((m) => [m.type, m.reason]), [['adjustment', 'device_correction']]);
  //  خواندنِ دوبارهٔ کاملِ دفتر چیزی را دو بار نمی‌شمارد
  const catalog = require('../src/lib/catalog');
  await catalog.refresh(on.shopId, { full: true, throttle: false });
  assert.equal((await levels(on, 'pc')).shop_floor, 27_000);
  await reconciled(on);
});
