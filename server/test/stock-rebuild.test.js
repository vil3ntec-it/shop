'use strict';
/**
 * S86 (§۱۹.۱۲) — بازسازیِ projection ِ موجودی از دفتر.
 *
 * تا امروز تطبیق (`stock.reconcile`، INV-16) فقط می‌گفت `stock_levels` با جمعِ `stock_moves` نمی‌خواند؛
 * راهی برای درست کردنش نبود. `stock.rebuild` (و دستورِ `npm run stock-rebuild`):
 *   • پیش‌فرض فقط نشان می‌دهد (هیچ نوشتنی)؛ با `apply` هر مانده = جمعِ دفترِ همان کالا و مکان؛
 *   • دفتر (`stock_moves`) هرگز دست نمی‌خورد و دکانِ دیگر هم نه؛
 *   • پشتِ همان قفلِ موجودیِ دکان، با یک ردیفِ سابقهٔ `stock.rebuild` در همان تراکنش؛ بارِ دوم بی‌اثر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const stock = require('../src/lib/stock');

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

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  const out = { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-r-${made.body.shop.id}`.slice(0, 60) };
  for (const key of ['shop.orders', 'shop.catalog']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: out.shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return out;
}

let opN = 0;
const opId = () => `01JSR${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
async function receive(s, pid, units) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: [
      op('products', pid, { name: `کالای ${pid}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now() }),
      op('warehouseEntries', `we-${pid}`, { productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() }),
    ] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  //  دفترِ موجودی از همگام‌سازی تنبل خوانده می‌شود (`catalog.refresh`)
  const f = await h.post('/api/shop/catalog/refresh', {}, { token: s.token });
  assert.equal(f.status, 200, JSON.stringify(f.body));
}
const level = async (s, pid, loc) => Number((await one(
  `SELECT on_hand_milli FROM stock_levels WHERE shop_id=$1 AND product_id=$2 AND location=$3 AND branch_id=''`, [s.shopId, pid, loc]
))?.on_hand_milli ?? NaN);
const moves = async (s) => Number((await one('SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1', [s.shopId])).n);
const rebuilds = (s) => many(`SELECT * FROM audit_logs WHERE shop_id=$1 AND action='stock.rebuild'`, [s.shopId]);

/** projection ِ خراب: یک مانده بیشتر، یک مانده گم، یک ماندهٔ بی دفتر */
async function corrupt(s) {
  const loc = (await one(`SELECT location FROM stock_moves WHERE shop_id=$1 AND product_id='pa' AND location<>'' LIMIT 1`, [s.shopId])).location;
  await query(`UPDATE stock_levels SET on_hand_milli = on_hand_milli + 7000 WHERE shop_id=$1 AND product_id='pa'`, [s.shopId]);
  await query(`DELETE FROM stock_levels WHERE shop_id=$1 AND product_id='pb'`, [s.shopId]);
  await query(
    `INSERT INTO stock_levels (shop_id, product_id, branch_id, location, on_hand_milli, updated_at) VALUES ($1,'ghost','',$2,3000,$3)`,
    [s.shopId, loc, now()]
  );
  return loc;
}

test('S86: پیش‌فرض فقط نشان می‌دهد؛ با apply مانده‌ها = جمعِ دفتر، دفتر و دکانِ دیگر دست نمی‌خورند؛ بارِ دوم بی‌اثر', async () => {
  const a = await shop('بازسازی');
  const b = await shop('همسایهٔ بازسازی');
  await receive(a, 'pa', 5);
  await receive(a, 'pb', 2);
  await receive(b, 'pa', 4);
  assert.equal((await stock.reconcile(a.shopId)).ok, true, 'پیش از خرابی سالم');
  const loc = await corrupt(a);
  await query(`UPDATE stock_levels SET on_hand_milli = 99000 WHERE shop_id=$1`, [b.shopId]);
  assert.equal((await stock.reconcile(a.shopId)).mismatches.length, 3);
  const movesA = await moves(a);

  const dry = await stock.rebuild(a.shopId);
  assert.equal(dry.applied, false);
  assert.equal(dry.ok, false);
  assert.deepEqual(dry.fixed.map((x) => `${x.productId}:${x.levelMilli}>${x.ledgerMilli}`).sort(),
    ['ghost:3000>0', 'pa:12000>5000', 'pb:0>2000']);
  assert.equal(await level(a, 'pa', loc), 12000, 'نمایش هیچ چیز نمی‌نویسد');
  assert.equal((await rebuilds(a)).length, 0);

  const done = await stock.rebuild(a.shopId, { apply: true, by: 'آزمون' });
  assert.equal(done.applied, true);
  assert.equal(done.fixed.length, 3);
  assert.equal((await stock.reconcile(a.shopId)).ok, true, JSON.stringify((await stock.reconcile(a.shopId)).mismatches));
  assert.equal(await level(a, 'pa', loc), 5000);
  assert.equal(await level(a, 'pb', loc), 2000);
  assert.equal(await level(a, 'ghost', loc), 0);
  assert.equal(await moves(a), movesA, 'دفتر فقط‌افزودنی است و دست نمی‌خورد');
  const log = await rebuilds(a);
  assert.equal(log.length, 1);
  assert.equal(log[0].user_id, 'آزمون');
  assert.equal(log[0].detail.fixed, 3);
  //  دکانِ دیگر همان خرابیِ خودش را دارد
  assert.equal(await level(b, 'pa', loc), 99000);
  assert.equal((await stock.reconcile(b.shopId)).ok, false);

  const again = await stock.rebuild(a.shopId, { apply: true });
  assert.equal(again.ok, true);
  assert.equal(again.applied, false);
  assert.equal(again.fixed.length, 0);
  assert.equal((await rebuilds(a)).length, 1, 'بی تغییر ⇒ بی سابقهٔ تازه');
  //  API ِ کاتالوگ همان عدد را می‌دهد
  const p = await h.get('/api/shop/catalog/products/pa', { token: a.token });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.product.stock.levels[loc], 5000);
  assert.equal(p.body.product.stock.sellableMilli, 5000);
});

test('S86: دستورِ stock-rebuild — بی --apply کدِ ۲ و بی نوشتن، با --apply کدِ ۰؛ دکانِ ناشناخته ⇒ خطا', async () => {
  const cli = require('../scripts/stock-rebuild');
  const s = await shop('دستورِ بازسازی');
  await receive(s, 'pa', 3);
  await receive(s, 'pb', 1);
  const loc = await corrupt(s);
  const out = [];
  assert.equal(await cli.main(['--shop', s.shopId], (x) => out.push(x)), 2);
  assert.match(out.join('\n'), /3/);
  assert.equal(await level(s, 'pa', loc), 10000);
  out.length = 0;
  assert.equal(await cli.main(['--shop', s.shopId, '--apply', '--by', 'اپراتور'], (x) => out.push(x)), 0);
  assert.equal(await level(s, 'pa', loc), 3000);
  assert.equal((await stock.reconcile(s.shopId)).ok, true);
  assert.equal(await cli.main(['--shop', s.shopId], () => {}), 0);
  await assert.rejects(() => cli.main([], () => {}), /--shop/);
  await assert.rejects(() => cli.main(['--shop', 'shop_nope'], () => {}), /دکان/);
});
