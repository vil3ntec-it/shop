'use strict';
/**
 * M4 (قراردادِ دکان ⇐ بازار، بخشِ ۳) — برند، بارکد، واحدِ فروش، variant،
 * `bankProductId` و دستهٔ سراسری در نمای عمومیِ کالا (`lib/product-details.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `market.product_details` (all) نمای کالا، جست‌وجو و
 *      تنظیمِ بازارِ دکان همان شکلِ امروز؛ `/api/market/same/*` ۴۰۴.
 *   ۲) پذیرش: جست‌وجوی بازار با بارکد (رقمِ فارسی هم) و با برند.
 *   ۳) پذیرش: «همین کالا در دکان‌های دیگر» با `bankProductId` — فقط کالای زنده،
 *      منتشرشده و با پیوندِ خودِ دکان.
 *   ۴) دستهٔ سراسری = نگاشتِ صاحب دکان، وگرنه دستهٔ خودِ دکان؛ کلیدِ ناشناخته رد.
 *   ۵) کاتالوگِ واحد اگر ردیف دارد مقدم است.
 *   ۶) مهاجرتِ ۱۳۰: برگشت (rollback) و اجرای دوباره.
 */
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  await setFlag('all', 'market.directory', true);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const detailsOn = (v) => setFlag('all', 'market.product_details', v);

const push = (token, ops, device) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن، در فهرستِ بازار؛ `products` = [شناسه, فیلدها, منتشر؟]. */
async function liveStore(label, products, listing = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `pd-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 5000,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, products.map(([id, f]) => op(id, { unit: 'دانه', salePrice: 100, category: 'خوراکی', ...f })), `dev-${uniq()}`);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id, , pub] of products) {
    if (!pub) continue;
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const r = await h.put('/api/shop/market', { listed: true, category: 'grocery', ...listing }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug };
}

/** پیوندِ کالای دکان به کالای بانک (همان ردیفی که انتخابِ عکسِ مرکزی می‌سازد). */
async function linkBank(shopId, productId, bankId) {
  await query(
    `INSERT INTO shop_product_images (shop_id, product_id, source, bank_product_id, updated_at)
     VALUES ($1,$2,'none',$3,$4)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET bank_product_id = EXCLUDED.bank_product_id`,
    [shopId, productId, bankId, now()]
  );
}

const q = (s) => encodeURIComponent(s);
const NEW_KEYS = ['brand', 'barcodes', 'subcategory', 'saleUnit', 'variant', 'bankProductId', 'globalCategory'];

test('پرچمِ خاموش: نما، جست‌وجو و تنظیمِ بازار همان قبلی؛ «همین کالا» ۴۰۴', async () => {
  const bank = `bp-off-${uniq()}`;
  const s = await liveStore('خاموش', [['tea', { name: 'چای سبز', brand: 'الکوزی', barcode: '6260000000017' }, true]]);
  await linkBank(s.shopId, 'tea', bank);

  const list = await h.get(`/api/store/${s.slug}/products`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  const item = list.body.items.find((p) => p.id === 'tea');
  for (const k of NEW_KEYS) assert.equal(k in item, false, `${k} بی پرچم نیست`);
  const one1 = await h.get(`/api/store/${s.slug}/products/tea`);
  for (const k of NEW_KEYS) assert.equal(k in one1.body.product, false);

  assert.equal((await h.get(`/api/market/search?q=${q('الکوزی')}`)).body.products.length, 0, 'برند بی پرچم جست‌وجو نمی‌شود');
  assert.equal((await h.get('/api/market/search?q=6260000000017')).body.products.length, 0);
  assert.equal((await h.get(`/api/market/search?q=${q('چای')}`)).body.products.length >= 1, true, 'نام مثلِ قبل');
  const same = await h.get(`/api/market/same/${bank}`);
  assert.equal(same.status, 404);

  const m = await h.put('/api/shop/market', { categoryMap: { 'خوراکی': 'grocery' } }, { token: s.token });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  assert.equal('categoryMap' in m.body.listing, false, 'فیلدِ تازه بی پرچم در پاسخ نیست');
  assert.equal('categoryMap' in (await h.get('/api/shop/market', { token: s.token })).body.listing, false);
  const row = await one('SELECT category_map FROM market_listings WHERE shop_id = $1', [s.shopId]);
  assert.deepEqual(typeof row.category_map === 'string' ? JSON.parse(row.category_map) : row.category_map, {}, 'بی پرچم ذخیره نمی‌شود');
});

test('پرچمِ روشن: جزئیات در نما و جست‌وجو با برند و بارکد (رقمِ فارسی هم)', async () => {
  const s = await liveStore('روشن', [
    ['milk', { name: 'شیر پاکتی', brand: 'Kabul Dairy', barcodes: ['4820000000011', ' '], saleUnit: 'کارتن', unitFactor: 12,
      subcategory: 'شیر', parentId: 'milk-all', variant: { size: '1L', 'رنگ': 'سفید', bad: { x: 1 } } }, true],
    ['hidden', { name: 'شیر خشک', brand: 'Kabul Dairy', barcode: '4820000000028' }, false],
  ]);
  await detailsOn(true);
  try {
    const list = await h.get(`/api/store/${s.slug}/products`);
    const p = list.body.items.find((x) => x.id === 'milk');
    assert.equal(p.brand, 'Kabul Dairy');
    assert.deepEqual(p.barcodes, ['4820000000011']);
    assert.equal(p.subcategory, 'شیر');
    assert.deepEqual(p.saleUnit, { unit: 'کارتن', factorMilli: 12000 });
    assert.deepEqual(p.variant, { parentId: 'milk-all', attrs: { size: '1L', 'رنگ': 'سفید' } });
    assert.equal(p.bankProductId, '');
    assert.equal(p.globalCategory, 'grocery', 'بی نگاشت ⇒ دستهٔ خودِ دکان');

    const byBrand = await h.get(`/api/market/search?q=${q('kabul')}`);
    assert.equal(byBrand.status, 200, JSON.stringify(byBrand.body));
    const ids = byBrand.body.products.filter((x) => x.shop.slug === s.slug).map((x) => x.id);
    assert.deepEqual(ids, ['milk'], 'برند پیدا می‌شود؛ منتشرنشده هرگز');
    assert.equal(byBrand.body.products[0].brand, 'Kabul Dairy');

    const byCode = await h.get(`/api/market/search?q=${q('۴۸۲۰۰۰۰۰۰۰۰۱۱')}`);
    assert.deepEqual(byCode.body.products.map((x) => x.id), ['milk']);
    assert.equal((await h.get('/api/market/search?q=4820000000028')).body.products.length, 0, 'بارکدِ کالای منتشرنشده نه');
    assert.equal((await h.get('/api/market/search?q=482000000001')).body.products.length, 0, 'بارکد فقط برابرِ کامل');

    //  ⛔ نمایه فقط نامزد است: کالای غیرفعال‌شده دیگر با برند پیدا نمی‌شود
    await query("UPDATE sync_rows SET data = jsonb_set(data, '{active}', 'false') WHERE account_id = $1 AND row_id = 'milk'", [s.shopId]);
    assert.equal((await h.get(`/api/market/search?q=${q('kabul')}`)).body.products.filter((x) => x.shop.slug === s.slug).length, 0);
  } finally {
    await detailsOn(false);
  }
});

test('«همین کالا در دکان‌های دیگر» با bankProductId: فقط پیوندِ زندهٔ خودِ دکان‌ها', async () => {
  const bank = `bp-${uniq()}`;
  const a = await liveStore('الف', [['sugar', { name: 'شکر یک کیلویی', salePrice: 90 }, true]]);
  const b = await liveStore('ب', [['s2', { name: 'شکرِ سفید', salePrice: 80 }, true], ['s3', { name: 'شکر', salePrice: 70 }, false]]);
  const c = await liveStore('ج', [['x', { name: 'شکر قهوه‌ای', salePrice: 60 }, true]]);
  await linkBank(a.shopId, 'sugar', bank);
  await linkBank(b.shopId, 's2', bank);
  await linkBank(b.shopId, 's3', bank);   // منتشرنشده
  await detailsOn(true);
  try {
    const one1 = await h.get(`/api/store/${a.slug}/products/sugar`);
    assert.equal(one1.body.product.bankProductId, bank, 'کلیدِ «همین کالا» روی صفحهٔ کالا');

    const r = await h.get(`/api/market/same/${bank}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(r.body.offers.map((o) => `${o.shop.slug}/${o.id}`).sort(), [`${a.slug}/sugar`, `${b.slug}/s2`].sort());
    assert.ok(r.body.offers.every((o) => o.bankProductId === bank && o.priceMinor > 0));
    assert.equal(r.body.offers.some((o) => o.shop.slug === c.slug), false, 'بی پیوند ⇒ نه');

    const ex = await h.get(`/api/market/same/${bank}?exclude=${a.slug}`);
    assert.deepEqual(ex.body.offers.map((o) => o.shop.slug), [b.slug], 'دکانِ همین صفحه بیرون');

    //  پیوند برداشته شد ⇒ همان دم بیرون (سنجشِ زنده)
    await query("UPDATE shop_product_images SET bank_product_id = '' WHERE shop_id = $1", [b.shopId]);
    assert.deepEqual((await h.get(`/api/market/same/${bank}`)).body.offers.map((o) => o.shop.slug), [a.slug]);

    assert.equal((await h.get(`/api/market/same/${q('bad id!')}`)).status, 400);
    assert.deepEqual((await h.get('/api/market/same/nothing-here')).body.offers, []);
  } finally {
    await detailsOn(false);
  }
});

test('دستهٔ سراسری با نگاشتِ صاحب دکان؛ کلیدِ ناشناخته و نگاشتِ بزرگ رد', async () => {
  const s = await liveStore('دسته', [['soap', { name: 'صابون', category: 'بهداشتي' }, true]], { category: 'grocery' });
  await detailsOn(true);
  try {
    const bad = await h.put('/api/shop/market', { categoryMap: { 'بهداشتی': 'tree/beauty' } }, { token: s.token });
    assert.equal(bad.status, 400);
    assert.equal(bad.body.error.code, 'bad_category');
    assert.equal((await h.put('/api/shop/market', { categoryMap: ['x'] }, { token: s.token })).status, 400);
    const big = Object.fromEntries(Array.from({ length: 61 }, (_, i) => [`دسته ${i}`, 'other']));
    assert.equal((await h.put('/api/shop/market', { categoryMap: big }, { token: s.token })).body.error.code, 'category_map_too_large');

    //  «ي» ِ عربی و «ی» ِ فارسی یک دسته‌اند
    const ok = await h.put('/api/shop/market', { categoryMap: { 'بهداشتی': 'beauty' } }, { token: s.token });
    assert.equal(ok.status, 200, JSON.stringify(ok.body));
    assert.deepEqual(ok.body.listing.categoryMap, { 'بهداشتی': 'beauty' });
    assert.equal(ok.body.listing.category, 'grocery', 'فیلدهای دیگر دست نخورد');
    const p = (await h.get(`/api/store/${s.slug}/products`)).body.items.find((x) => x.id === 'soap');
    assert.equal(p.globalCategory, 'beauty');

    const off = await h.put('/api/shop/market', { categoryMap: { 'بهداشتی': '' } }, { token: s.token });
    assert.deepEqual(off.body.listing.categoryMap, {});
    assert.equal((await h.get(`/api/store/${s.slug}/products`)).body.items.find((x) => x.id === 'soap').globalCategory, 'grocery');
  } finally {
    await detailsOn(false);
  }
});

test('کاتالوگِ واحد اگر ردیف دارد مقدم است (همان قالبِ کاتالوگ)', async () => {
  const s = await liveStore('کاتالوگ', [['pen', { name: 'خودکار', brand: 'قدیمی' }, true]]);
  await query(
    `INSERT INTO catalog_products (shop_id, product_id, name, brand, barcodes, sale_unit, unit_factor_milli, variant, source, updated_at)
     VALUES ($1,'pen','خودکار','Bic','["3086123456789"]'::jsonb,'بسته',10000,'{"color":"آبی"}'::jsonb,'v1',$2)`,
    [s.shopId, now()]
  );
  await detailsOn(true);
  try {
    const p = (await h.get(`/api/store/${s.slug}/products/pen`)).body.product;
    assert.equal(p.brand, 'Bic');
    assert.deepEqual(p.barcodes, ['3086123456789']);
    assert.deepEqual(p.saleUnit, { unit: 'بسته', factorMilli: 10000 });
    assert.deepEqual(p.variant, { parentId: '', attrs: { color: 'آبی' } });
  } finally {
    await detailsOn(false);
  }
});

test('مهاجرتِ ۱۳۰: برگشت ستون‌ها را برمی‌دارد و اجرای دوباره برمی‌گرداند', async () => {
  const cols = async () => (await many(
    `SELECT table_name || '.' || column_name AS c FROM information_schema.columns
      WHERE (table_name = 'market_search' AND column_name IN ('brand_norm','barcodes','bank_product_id'))
         OR (table_name = 'market_listings' AND column_name = 'category_map') ORDER BY 1`
  )).map((r) => r.c);
  const all = ['market_listings.category_map', 'market_search.bank_product_id', 'market_search.barcodes', 'market_search.brand_norm'];
  assert.deepEqual(await cols(), all);
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '130_market_product_details.down.sql'), 'utf8');
  await query(down);
  assert.deepEqual(await cols(), []);
  assert.equal(await one("SELECT 1 FROM schema_migrations WHERE version = '130_market_product_details.sql'"), null);
  await require('../src/migrate').run();
  assert.deepEqual(await cols(), all);
  const search = await h.get(`/api/market/search?q=${q('شکر')}`);
  assert.equal(search.status, 200, JSON.stringify(search.body));
});
