'use strict';
/**
 * S86 (§۱۹.۱۲) — بازسازیِ projection ِ **کاتالوگ** (`catalog_products`) از دفترِ دستگاه‌ها.
 *
 * `stock.rebuild` فقط مانده‌ها را می‌ساخت؛ کالای کاتالوگ (نام، قیمتِ نمایش، فعال/حذف) که از دفترِ همگام‌سازی پر
 * می‌شود راهی برای بازسازی نداشت — ردیفِ خراب تا تغییرِ بعدیِ همان کالا در دستگاه همان می‌ماند (آبراهه جلو رفته است).
 * `catalog-products.rebuildProducts` (و `npm run stock-rebuild -- --catalog`):
 *   • پیش‌فرض فقط نشان می‌دهد؛ با `apply` تعداد، قیمتِ نمایش و وضعیت = دفترِ دستگاه‌ها (گم ⇒ ساخته، بی منبع ⇒ پاک)؛
 *   • دفترِ همگام‌سازی و دکانِ دیگر دست نمی‌خورند؛ زیرِ قفلِ موجودیِ دکان با سابقهٔ `catalog.rebuild`؛ بارِ دوم بی‌اثر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const cp = require('../src/lib/catalog-products');

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
  const out = { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-c-${made.body.shop.id}`.slice(0, 60) };
  for (const key of ['shop.orders', 'shop.catalog']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: out.shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return out;
}

let opN = 0;
const opId = () => `01JSC${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (rowId, fields) => ({ op_id: opId(), ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });
/** سه کالا در دستگاه (قیمتِ ۵۰، ۷۰ و ۹۰ افغانی) ⇒ کاتالوگ از دفتر */
async function seed(s) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: [
      op('pa', { name: 'کالای الف', unit: 'دانه', salePrice: 50, active: true, barcodes: [] }),
      op('pb', { name: 'کالای ب', unit: 'دانه', salePrice: 70, active: true, barcodes: [] }),
      op('pc', { name: 'کالای ج', unit: 'دانه', salePrice: 90, active: true, barcodes: [] }),
    ] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  const f = await h.post('/api/shop/catalog/refresh', {}, { token: s.token });
  assert.equal(f.status, 200, JSON.stringify(f.body));
}
const products = async (s) => (await many(
  'SELECT product_id, name, price_minor, active, deleted FROM catalog_products WHERE shop_id=$1 ORDER BY product_id', [s.shopId]
)).map((r) => `${r.product_id}:${r.price_minor === null ? '-' : Number(r.price_minor)}:${r.active ? 'on' : 'off'}${r.deleted ? ':del' : ''}`);
const rebuilds = (s) => many(`SELECT * FROM audit_logs WHERE shop_id=$1 AND action='catalog.rebuild'`, [s.shopId]);
const ledger = async (s) => Number((await one(
  `SELECT COUNT(*)::int AS n FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='products'`, [s.shopId]
)).n);

/** projection ِ خراب: قیمتِ نمایشِ دیگر، کالای گم، وضعیتِ وارونه و کالای بی منبع */
async function corrupt(s) {
  await query(`UPDATE catalog_products SET price_minor = 1 WHERE shop_id=$1 AND product_id='pa'`, [s.shopId]);
  await query(`DELETE FROM catalog_products WHERE shop_id=$1 AND product_id='pb'`, [s.shopId]);
  await query(`UPDATE catalog_products SET active = false WHERE shop_id=$1 AND product_id='pc'`, [s.shopId]);
  await query(
    `INSERT INTO catalog_products (shop_id, product_id, name, price_minor, source, updated_at) VALUES ($1,'ghost','شبح',100,'v1',$2)`,
    [s.shopId, now()]
  );
}

const HEALTHY = ['pa:5000:on', 'pb:7000:on', 'pc:9000:on'];

test('S86 کاتالوگ: پیش‌فرض فقط نشان می‌دهد؛ با apply تعداد، قیمتِ نمایش و وضعیت = دفترِ دستگاه‌ها؛ دکانِ دیگر دست نمی‌خورد؛ بارِ دوم بی‌اثر', async () => {
  const a = await shop('کاتالوگِ بازسازی');
  const b = await shop('همسایهٔ کاتالوگ');
  await seed(a);
  await seed(b);
  assert.deepEqual(await products(a), HEALTHY, 'پیش از خرابی همان دفتر');
  const fresh = await cp.rebuildProducts(a.shopId);
  assert.deepEqual([fresh.ok, fresh.changes.length, fresh.source], [true, 0, 'v1'], JSON.stringify(fresh));
  await corrupt(a);
  await query(`UPDATE catalog_products SET price_minor = 2 WHERE shop_id=$1`, [b.shopId]);
  const ledgerA = await ledger(a);

  const dry = await cp.rebuildProducts(a.shopId);
  assert.equal(dry.ok, false);
  assert.equal(dry.applied, false);
  assert.deepEqual(dry.count, { projection: 3, source: 3 });
  assert.deepEqual(dry.changes, [
    { productId: 'ghost', kind: 'dropped' },
    { productId: 'pa', kind: 'fixed', fields: ['price_minor'] },
    { productId: 'pb', kind: 'built' },
    { productId: 'pc', kind: 'fixed', fields: ['active'] },
  ]);
  assert.deepEqual(await products(a), ['ghost:100:on', 'pa:1:on', 'pc:9000:off'], 'نمایش هیچ چیز نمی‌نویسد');
  assert.equal((await rebuilds(a)).length, 0);

  const done = await cp.rebuildProducts(a.shopId, { apply: true, by: 'آزمون' });
  assert.equal(done.applied, true);
  assert.equal(done.ok, true);
  assert.equal(done.changes.length, 4);
  assert.deepEqual(await products(a), HEALTHY);
  assert.equal(await ledger(a), ledgerA, 'دفترِ همگام‌سازی دست نمی‌خورد');
  const log = await rebuilds(a);
  assert.equal(log.length, 1);
  assert.equal(log[0].user_id, 'آزمون');
  assert.equal(log[0].detail.changes, 4);
  //  کارمند همان قیمت و وضعیت را می‌بیند
  const p = await h.get('/api/shop/catalog/products/pa', { token: a.token });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.product.priceMinor, 5000);
  assert.equal(p.body.product.active, true);
  //  دکانِ دیگر همان خرابیِ خودش را دارد
  assert.deepEqual(await products(b), ['pa:2:on', 'pb:2:on', 'pc:2:on']);

  const again = await cp.rebuildProducts(a.shopId, { apply: true });
  assert.deepEqual([again.ok, again.applied, again.changes.length], [true, false, 0]);
  assert.equal((await rebuilds(a)).length, 1, 'بی تغییر ⇒ بی سابقهٔ تازه');
  //  ingest ِ بعدی همان را نگه می‌دارد (آبراهه جلو نرفته و پس نرفته)
  assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: a.token })).status, 200);
  assert.deepEqual(await products(a), HEALTHY);
});

test('S86 کاتالوگ: کالای حذف‌شده در دستگاه «حذف‌شده» ساخته می‌شود؛ دستورِ stock-rebuild --catalog کدِ ۲ بی نوشتن و با --apply کدِ ۰', async () => {
  const cli = require('../scripts/stock-rebuild');
  const s = await shop('دستورِ کاتالوگ');
  await seed(s);
  //  دستگاه کالای «ج» را پاک کرد؛ projection هنوز زنده نشانش می‌دهد (مثلاً بازگردانیِ پشتیبانِ کهنه)
  const del = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: [{ op_id: opId(), ts: Date.now(), table: 'products', row_id: 'pc', type: 'delete', fields: {} }] },
  });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
  assert.deepEqual(await products(s), ['pa:5000:on', 'pb:7000:on', 'pc:9000:on:del']);
  await query(`UPDATE catalog_products SET deleted = false WHERE shop_id=$1 AND product_id='pc'`, [s.shopId]);
  await query(`UPDATE catalog_products SET price_minor = NULL WHERE shop_id=$1 AND product_id='pb'`, [s.shopId]);

  const out = [];
  assert.equal(await cli.main(['--shop', s.shopId, '--catalog'], (x) => out.push(x)), 2);
  assert.match(out.join('\n'), /کاتالوگ ناهمخوان: 2/);
  assert.match(out.join('\n'), /pc: fixed \(deleted\)/);
  assert.deepEqual(await products(s), ['pa:5000:on', 'pb:-:on', 'pc:9000:on']);
  //  بی --catalog فقط موجودی (همان رفتارِ پیشین)
  assert.equal(await cli.main(['--shop', s.shopId], () => {}), 0);
  out.length = 0;
  assert.equal(await cli.main(['--shop', s.shopId, '--catalog', '--apply', '--by', 'اپراتور'], (x) => out.push(x)), 0);
  assert.deepEqual(await products(s), ['pa:5000:on', 'pb:7000:on', 'pc:9000:on:del']);
  assert.equal(await cli.main(['--shop', s.shopId, '--catalog'], () => {}), 0);
  const json = [];
  assert.equal(await cli.main(['--shop', s.shopId, '--catalog', '--json'], (x) => json.push(x)), 0);
  assert.deepEqual(JSON.parse(json[0]).catalog.count, { projection: 3, source: 3 });
});
