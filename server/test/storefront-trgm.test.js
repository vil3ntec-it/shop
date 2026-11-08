'use strict';
/**
 * جست‌وجوی ویترین با نمایهٔ سه‌حرفی (`pg_trgm`، مهاجرتِ ۲۲۶؛ docs/SCALING-fa.md).
 *
 *   ۱) نتیجه‌ها همان قاعدهٔ امروز است روی هر دو راه‌انداز — PostgreSQL با نمایه و PGlite بی آن (همان LIKE):
 *      ي/ك ِ عربی، نیم‌فاصله، حرفِ بزرگِ لاتین، و ⛔ گریزِ `%` و `_` و `\` (واژهٔ «50%» هرگز «500 گرم» را نمی‌آورد).
 *   ۲) ⛔ کالای دکانِ دیگر هرگز (نمایه همهٔ دکان‌ها را دارد؛ شرطِ دکان همان می‌ماند).
 *   ۳) پرچمِ WEB-05 (`shop.storefront.search`): واژهٔ بی رقم با همان `SQL_NAME` ِ نمایه‌دار، واژهٔ رقم‌دار با
 *      `sqlName` — نتیجه در هر دو همان پیشین.
 *   ۴) فقط PostgreSQL با pg_trgm + btree_gin: نمایه (account_id، نام) هست و همان عبارتِ پرسش از آن پاسخ می‌گیرد
 *      (EXPLAIN)؛ و بی نمایه همان ردیف‌ها.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const sf = require('../src/lib/storefront');
const search = require('../src/lib/storefront-search');
const { resetAll } = require('../src/middleware/ratelimit');

const PGLITE = /^pglite:/i.test(process.env.TEST_DATABASE_URL || '');
const INDEX = 'ix_sync_rows_product_name_trgm';
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await db.query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [db.newId('adm'), await pw.hashPassword('Admin!12345'), db.now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

const PRODUCTS = {
  milkAr: 'شير پاستوریزه',          //  ي ِ عربی
  cake: 'كيك شکلاتی',                //  ك و ي ِ عربی
  tissue: 'دستمال‌کاغذی نرم',         //  نیم‌فاصله
  pct: 'تخفیف 50% صابون',
  grams: 'برنج 500 گرم',
  under: 'رنگ a_b',
  axb: 'رنگ axb',
  slash: 'Back\\Slash',
  apple: 'Apple Juice',
  oil5: 'روغن ۵ لیتری',
};

async function liveStore(label, products) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `tg-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000004' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const ops = Object.entries(products).map(([id, name]) => op(id, { name, unit: 'دانه', salePrice: 10, category: 'همه' }));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-tg' }, body: { device_id: 'dev-tg', schema_version: 2, ops },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of Object.keys(products)) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug };
}

const ids = async (s, q) => {
  const r = await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent(q)}&limit=50`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.map((p) => p.id).sort();
};

const CASES = [
  ['شیر', ['milkAr']],
  ['کیک', ['cake']],
  ['دستمال کاغذی', ['tissue']],
  ['دستمال‌کاغذی', ['tissue']],
  ['50%', ['pct']],
  ['500', ['grams']],
  ['a_b', ['under']],
  ['k\\s', ['slash']],
  ['apple', ['apple']],
  ['JUICE', ['apple']],
  ['رنگ', ['axb', 'under']],
  ['چیزی‌که‌نیست', []],
];

let one = null;
test('همان نتیجه‌ها: ي/ك، نیم‌فاصله، حرفِ بزرگ و ⛔ گریزِ % _ \\؛ ⛔ دکانِ دیگر هرگز', async () => {
  one = await liveStore('trgm-1', PRODUCTS);
  const other = await liveStore('trgm-2', { theirs: 'شیر دکانِ دیگر', theirs2: 'رنگ a_b دیگر' });
  for (const [q, want] of CASES) assert.deepEqual(await ids(one, q), want, q);
  assert.deepEqual(await ids(other, 'شیر'), ['theirs']);
  assert.deepEqual(await ids(other, 'a_b'), ['theirs2']);
});

test('WEB-05 روشن: واژهٔ بی رقم و رقم‌دار همان نتیجهٔ پیشین (نمایه فقط برای بی‌رقم)', async () => {
  assert.ok(one);
  await setFlag(one.shopId, search.SMART_FLAG, true);
  const smart = { categories: async () => [], normalize: sf.normalize, SQL_NAME: sf.SQL_NAME };
  assert.equal((await search.plan(one.shopId, 'شیر', '', smart)).name, sf.SQL_NAME, 'بی رقم ⇒ همان عبارتِ نمایه');
  assert.equal((await search.plan(one.shopId, '۵ لیتری', '', smart)).name, search.sqlName(sf.SQL_NAME), 'رقم‌دار ⇒ با رقمِ لاتین');
  assert.deepEqual(await ids(one, 'شیر'), ['milkAr']);
  assert.deepEqual(await ids(one, '۵ لیتری'), ['oil5']);
  assert.deepEqual(await ids(one, '5 لیتری'), ['oil5']);
  assert.deepEqual(await ids(one, '50%'), ['pct']);
  await setFlag(one.shopId, search.SMART_FLAG, false);
});

test('PostgreSQL: نمایه با همان عبارت، برنامه‌ریز آن را برمی‌دارد و بی آن همان ردیف‌ها', { skip: PGLITE && 'PGlite pg_trgm ندارد (همان LIKE، آزمونِ بالا)' }, async () => {
  const ext = await db.one("SELECT COUNT(*)::int AS n FROM pg_extension WHERE extname IN ('pg_trgm', 'btree_gin')");
  if (ext.n < 2) {
    //  PostgreSQL ِ بی contrib: مهاجرت نمایه نساخت و نشکست (نمایهٔ تک‌ستونی عمداً نه — کندتر بود)
    assert.equal(await db.one('SELECT 1 FROM pg_indexes WHERE indexname = $1', [INDEX]), null);
    return;
  }
  const ix = await db.one('SELECT indexdef FROM pg_indexes WHERE indexname = $1', [INDEX]);
  assert.ok(ix, 'نمایه ساخته شد');
  assert.match(ix.indexdef, /USING gin/);
  assert.match(ix.indexdef, /gin_trgm_ops/);
  //  دکانِ ۳٬۰۰۰ کالایی + یک کالای کم‌یاب
  const big = `shp_trgm_big_${uniq()}`;
  await db.query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
     SELECT 'shop', 'shop', $1, 'products', 'p' || g, jsonb_build_object('name', 'کالای شماره ' || g), 0, 0
       FROM generate_series(1, 3000) g`,
    [big]
  );
  await db.query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
     VALUES ('shop', 'shop', $1, 'products', 'rare', '{"name":"زعفران قائنات"}', 0, 0)`,
    [big]
  );
  await db.query('ANALYZE sync_rows');
  //  همان شکلِ پرسشِ `catalog` (SHOP_ROWS + حذف‌نشده + دکان + همان `SQL_NAME`)
  const where = `r.app = 'shop' AND r.account_kind = 'shop' AND r.table_name = 'products' AND r.deleted_at IS NULL AND r.account_id = $1 AND ${sf.SQL_NAME} LIKE $2`;
  const sql = `SELECT r.row_id FROM sync_rows r WHERE ${where} ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT 25`;
  //  نمایه به کار می‌آید؟ (عبارت و شرطِ جزئی جورند) — جدولِ آزمون کوچک است و برنامه‌ریز به‌حق پیمایش را ارزان‌تر می‌بیند؛
  //  پس فقط در همین تراکنش راه‌های دیگر بسته می‌شوند و برمی‌گردند. هر تغییرِ `SQL_NAME` بی نمایهٔ تازه این‌جا سرخ است.
  const UNDO = new Error('undo');
  let plan = '';
  await db.tx(async (c) => {
    await c.query('DROP INDEX ix_sync_rows_shop_product_name');
    await c.query('DROP INDEX ix_sync_rows_shop_product_category');
    await c.query('ALTER TABLE sync_rows DROP CONSTRAINT sync_rows_pkey');
    await c.query('SET LOCAL enable_seqscan = off');
    plan = (await c.query(`EXPLAIN ${sql}`, [big, '%زعفران%'])).rows.map((r) => r['QUERY PLAN']).join('\n');
    throw UNDO;
  }).catch((e) => { if (e !== UNDO) throw e; });
  assert.match(plan, new RegExp(`Bitmap Index Scan on ${INDEX}`), `عبارتِ پرسش با نمایه جور نیست:\n${plan}`);
  assert.match(plan, /Index Cond: \(\(account_id = |Index Cond: \(translate/, plan);
  assert.ok(await db.one("SELECT 1 FROM pg_indexes WHERE indexname = 'sync_rows_pkey'"), 'کلیدِ اصلی برگشت');
  const withIx = (await db.query(sql, [big, '%زعفران%'])).rows.map((r) => r.row_id);
  assert.deepEqual(withIx, ['rare']);
  //  بی نمایه (همان پیمایشِ امروز) ⇒ همان ردیف‌ها، برای چند واژه
  for (const needle of ['%زعفران%', '%شماره 29%', '%کالای%']) {
    const a = (await db.query(sql, [big, needle])).rows.map((r) => r.row_id);
    const b = await db.tx(async (c) => {
      await c.query('SET LOCAL enable_bitmapscan = off');
      await c.query(`SET LOCAL enable_indexscan = off`);
      return (await c.query(sql, [big, needle])).rows.map((r) => r.row_id);
    });
    assert.deepEqual(a, b, needle);
  }
  await db.query("DELETE FROM sync_rows WHERE account_id = $1", [big]);
});
