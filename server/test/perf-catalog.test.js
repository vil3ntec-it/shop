'use strict';
/**
 * PERF-03 — آزمونِ «رفتار عوض نشد» برای دو بهینه‌سازیِ کاتالوگ:
 *   • نمایهٔ ترتیبِ نام (migrations/113_perf_catalog_index.sql)
 *   • آمارِ planner برای PGlite (lib/pglite-stats.js)
 * کاتالوگِ عمومی، جست‌وجو، دسته، دسته‌ها و فهرستِ کالای صاحب دکان باید با و بی
 * نمایه و با و بی آمار **همان** ردیف‌ها را به **همان** ترتیب و همان صفحه‌بندی بدهند.
 * (اندازه و سرعت را `bench/server.mjs` می‌سنجد؛ این‌جا فقط درستی.)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now, isPglite } = require('../src/db');
const sf = require('../src/lib/storefront');
const stats = require('../src/lib/pglite-stats');

let shopId = '';
let seq = 0;

test.before(async () => {
  await h.start();
  const u = await h.newUser('کاتالوگ', 'shop');
  shopId = (await h.post('/api/shop', { name: 'دکانِ کاتالوگ' }, { token: u.accessToken })).body.shop.id;
  //  نام‌های تکراری، ی/ك عربی، بی‌نام، غیرفعال و حذف‌شده — همهٔ لبه‌های ترتیب و صافی
  const names = ['برنج', 'برنج', 'روغن', 'چاي', 'چای سبز', 'كیک', 'صابون', '', 'آب', 'ابریشم', 'zebra', 'Apple', '۱۲ عدد'];
  const ops = [];
  for (let i = 0; i < 140; i++) {
    const fields = { name: `${names[i % names.length]}${i % 3 ? ` ${i % 17}` : ''}`, salePrice: 10 + (i % 9), unit: 'عدد', category: ['خوراکی', 'بهداشتی', 'نوشیدنی'][i % 3] };
    if (i % 23 === 5) fields.active = false;
    ops.push({ op_id: `pc-${++seq}`, ts: Date.now(), table: 'products', row_id: `p${String(i).padStart(3, '0')}`, type: 'insert', fields });
  }
  ops.push({ op_id: `pc-${++seq}`, ts: Date.now(), table: 'products', row_id: 'p007', type: 'delete' });
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-pc' },
    body: { device_id: 'dev-pc', schema_version: 2, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  //  همه منتشر، جز چندتا؛ یکی با قیمتِ آنلاین
  await query(
    `INSERT INTO storefront_products (shop_id, product_id, published, online_price_minor, updated_at)
     SELECT account_id, row_id, (right(row_id, 1) <> '9'), CASE WHEN row_id = 'p010' THEN 1500 END, $2
       FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products'`,
    [shopId, now()]
  );
});
test.after(async () => { await h.stop(); });

async function snapshot() {
  const walk = async (opts) => {
    const pages = [];
    let cursor = null;
    do {
      const p = await sf.catalog(shopId, { ...opts, cursor, limit: 7 });
      pages.push(p.items.map((x) => `${x.id}:${x.name}:${x.priceMinor}`));
      cursor = p.nextCursor;
    } while (cursor && pages.length < 100);
    return pages;
  };
  const staff = [];
  let c = null;
  do {
    const p = await sf.staffProducts(shopId, { cursor: c, limit: 9 });
    staff.push(p.items.map((x) => `${x.id}:${x.published}:${x.active}`));
    c = p.nextCursor;
  } while (c && staff.length < 100);
  return {
    all: await walk({}),
    search: await walk({ q: 'برنج' }),
    searchArabicYeh: await walk({ q: 'چاي' }),
    category: await walk({ category: 'نوشیدنی' }),
    none: await walk({ q: 'هیچ‌چیز' }),
    categories: await sf.categories(shopId),
    staff,
  };
}

test('کاتالوگ با و بی نمایهٔ ۱۱۳ و با و بی آمارِ planner همان است', async () => {
  const withIndex = await snapshot();
  //  پیش‌شرط: آزمون واقعاً چیزی را می‌سنجد
  assert.ok(withIndex.all.length >= 15, 'چند صفحه لازم است');
  assert.ok(withIndex.search.flat().length >= 10);
  assert.ok(withIndex.searchArabicYeh.flat().length >= 1, 'ي عربی باید با ی یکی شود');
  assert.deepEqual(withIndex.none, [[]]);
  const ids = withIndex.all.flat().map((x) => x.split(':')[0]);
  assert.ok(!ids.includes('p007'), 'کالای حذف‌شده نباید بیاید');
  assert.ok(!ids.includes('p009'), 'کالای منتشرنشده نباید بیاید');
  assert.ok(!ids.includes('p005'), 'کالای غیرفعال نباید بیاید');
  assert.equal(new Set(ids).size, ids.length, 'هیچ کالایی دو بار نمی‌آید');

  await stats.analyzeNow();
  assert.deepEqual(await snapshot(), withIndex, 'پس از ANALYZE');

  await query('DROP INDEX ix_sync_rows_shop_product_name');
  try {
    assert.deepEqual(await snapshot(), withIndex, 'بی نمایه (رفتارِ پیش از ۱۱۳)');
  } finally {
    await query(`CREATE INDEX IF NOT EXISTS ix_sync_rows_shop_product_name
      ON sync_rows (account_id, (COALESCE(data->>'name', '')), row_id)
      WHERE app = 'shop' AND account_kind = 'shop' AND table_name = 'products' AND deleted_at IS NULL`);
  }
});

test('نمایهٔ ۱۱۳ فقط کالاهای زنده را دارد و روی نوشتنِ فاکتور اثری ندارد', async () => {
  const def = (await query(`SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_sync_rows_shop_product_name'`)).rows[0];
  assert.ok(def, 'نمایه ساخته نشده');
  assert.match(def.indexdef, /table_name = 'products'/);
  assert.match(def.indexdef, /deleted_at IS NULL/);
});

test('pglite-stats: فقط روی PGlite و قابلِ خاموش کردن', async () => {
  assert.equal(await stats.analyzeNow(), isPglite());
  const old = process.env.PGLITE_ANALYZE_HOURS;
  try {
    process.env.PGLITE_ANALYZE_HOURS = '0';
    assert.equal(stats.schedule(), null);
    process.env.PGLITE_ANALYZE_HOURS = 'x';
    assert.equal(stats.hours(), stats.DEFAULT_HOURS);
    process.env.PGLITE_ANALYZE_HOURS = '2';
    assert.equal(stats.hours(), 2);
    const lines = [];
    const handle = stats.schedule({ firstDelayMs: 5, log: { log: (s) => lines.push(s), error: (...a) => lines.push(a.join(' ')) } });
    if (isPglite()) {
      assert.ok(handle && typeof handle.stop === 'function');
      await new Promise((r) => setTimeout(r, 300));
      handle.stop();
      assert.ok(!lines.some((l) => l.includes('نشد')), lines.join('\n'));
    } else {
      assert.equal(handle, null, 'روی PostgreSQL کاری نمی‌کند (autovacuum هست)');
    }
  } finally {
    if (old === undefined) delete process.env.PGLITE_ANALYZE_HOURS; else process.env.PGLITE_ANALYZE_HOURS = old;
  }
  void newId;
});
