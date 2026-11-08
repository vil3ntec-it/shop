'use strict';
/**
 * WEB-09 (§۶.۱۴): سیاستِ کالای ناموجود در ویترین — «نشان بده با «ناموجود» و «خبرم کن»» (پیش‌فرض) یا «پنهان کن»
 * (`hideOutOfStock` ِ `PUT /api/shop/storefront`، مهاجرتِ ۲۴۰، `storefront-filters.hiding`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پیش‌فرض خاموش: ناموجود با نشانِ `out_of_stock` در فهرست می‌ماند؛ تنظیم فقط بله/نه و ذخیرهٔ کلیدِ دیگر آن را برنمی‌گرداند.
 *   ۲) ⛔ سرور تصمیم می‌گیرد: روشن ⇒ فهرست، جست‌وجو، صفحه‌بندی، فیلترِ WEB-07 و دسته‌ها بی ناموجودِ دانسته — بی صفحهٔ خالی؛
 *      سبد (`?ids=`) و صفحهٔ کالا همان «ناموجود» ِ راست را می‌گیرند (پیوند و سبد نمی‌شکنند).
 *   ۳) ⛔ موجودیِ نامعلوم (پرچمِ `shop.catalog` خاموش) هرگز پنهان نمی‌شود — دکانِ بی دفتر هیچ کالایی گم نمی‌کند.
 *   ۴) مهاجرتِ ۲۴۰: rollback ستون و ردیفِ `schema_migrations` را برمی‌دارد و دوباره نشستن همان.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
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
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-hide' },
  body: { device_id: 'dev-hide', schema_version: 2, ops },
});
const row = (table, rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type: 'insert', fields });

/** ویترینِ روشن با چهار کالای منتشرشده (به ترتیبِ نام: برنج، روغن، صابون، نمک) و دفترِ روشن: فقط برنج در انبار. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id, slug: `h-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    row('products', 'rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    row('products', 'oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    row('products', 'soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
    row('products', 'salt', { name: 'نمک', unit: 'بسته', salePrice: 20, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil', 'soap', 'salt']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  await setFlag(o.shopId, 'shop.catalog', true);
  const w = await push(o.token, [row('warehouseEntries', `we-${uniq()}`, {
    productId: 'rice', units: 10, cartons: 0, perCarton: 0, unit: 'کیلو', price: 100, createdAt: Date.now(),
  })]);
  assert.equal(w.status, 200, JSON.stringify(w.body));
  return o;
}

const ids = (r) => r.body.items.map((p) => p.id);
const badges = (r) => Object.fromEntries(r.body.items.map((p) => [p.id, p.stock.status]));
const save = (o, body) => h.put('/api/shop/storefront', body, { token: o.token });

test('WEB-09: پیش‌فرض خاموش — ناموجود با نشانِ «ناموجود» دیده می‌شود؛ تنظیم فقط بله/نه و ذخیرهٔ دیگر برش نمی‌گرداند', async () => {
  const s = await liveStore('پیش‌فرض');
  const cfg = await h.get('/api/shop/storefront/config', { token: s.token });
  assert.equal(cfg.status, 200, JSON.stringify(cfg.body));
  assert.equal(cfg.body.settings.hideOutOfStock, false, 'پیش‌فرض: نشان بده');
  const list = await h.get(`/api/store/${s.slug}/products`);
  assert.deepEqual(ids(list), ['rice', 'oil', 'soap', 'salt']);
  const st = badges(list);
  assert.ok(['in_stock', 'in_warehouse'].includes(st.rice), JSON.stringify(st));
  assert.deepEqual([st.oil, st.soap, st.salt], ['out_of_stock', 'out_of_stock', 'out_of_stock']);

  const bad = await save(s, { hideOutOfStock: 'yes' });
  assert.equal(bad.status, 400, JSON.stringify(bad.body));
  assert.equal(bad.body.error.code, 'bad_boolean');
  const on = await save(s, { hideOutOfStock: true });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.equal(on.body.settings.hideOutOfStock, true);
  //  برنامهٔ پیشین کلید را نمی‌فرستد ⇒ همان مقدار می‌ماند
  const other = await save(s, { tagline: 'تازه' });
  assert.equal(other.status, 200, JSON.stringify(other.body));
  assert.equal(other.body.settings.hideOutOfStock, true, 'ذخیرهٔ کلیدِ دیگر سیاست را برنمی‌گرداند');
  const row240 = await one('SELECT hide_out_of_stock FROM storefronts WHERE shop_id = $1', [s.shopId]);
  assert.equal(row240.hide_out_of_stock, true);
  //  نمای عمومی سیاست را لو نمی‌دهد (فقط سرور پالایش می‌کند)
  const pub = await h.get(`/api/store/${s.slug}`);
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  assert.ok(!JSON.stringify(pub.body).includes('hideOutOfStock') && !JSON.stringify(pub.body).includes('hide_out_of_stock'));
});

test('⛔ WEB-09 روشن: فهرست، جست‌وجو، صفحه‌بندی، فیلتر و دسته‌ها بی ناموجودِ دانسته؛ سبد و صفحهٔ کالا راستش را می‌گیرند', async () => {
  const s = await liveStore('پنهان');
  const before = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(before.body.store.categories.slice().sort(), ['بهداشتی', 'خوراکی']);
  assert.equal((await save(s, { hideOutOfStock: true })).status, 200);

  const list = await h.get(`/api/store/${s.slug}/products`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual(ids(list), ['rice'], 'فقط کالای موجود');
  assert.equal(list.body.total, 1, 'شمارِ صفحهٔ نخست همان کالاهای دیدنی');
  //  صفحه‌بندی: کالای پنهان صفحهٔ خالی نمی‌سازد و نشانگرِ بی‌جا نمی‌دهد
  const p1 = await h.get(`/api/store/${s.slug}/products?limit=1`);
  assert.deepEqual([ids(p1), p1.body.nextCursor], [['rice'], null]);
  //  جست‌وجو و دسته
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent('روغن')}`)), []);
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products?category=${encodeURIComponent('بهداشتی')}`)), []);
  const prof = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(prof.body.store.categories, ['خوراکی'], 'دستهٔ همه‌ناموجود («بهداشتی») در ویترین نیست');
  //  با فیلترِ WEB-07 هم (پرچمِ جدا) — دو پالایه با هم
  await setFlag(s.shopId, 'shop.storefront.filters', true);
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products?minPriceMinor=1`)), ['rice']);
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products?inStock=1`)), ['rice']);

  //  سبدِ مهمان و پیوندِ مستقیم: راستیِ امروز، نه «دیگر در ویترین نیست»
  const cart = await h.get(`/api/store/${s.slug}/products?ids=oil,rice`);
  assert.deepEqual(ids(cart).sort(), ['oil', 'rice']);
  const detail = await h.get(`/api/store/${s.slug}/products/oil`);
  assert.equal(detail.status, 200, JSON.stringify(detail.body));
  assert.equal(detail.body.product.stock.status, 'out_of_stock', 'صفحهٔ کالا «ناموجود» (جای «خبرم کن» ِ WEB-10)');

  //  موجود شد ⇒ بی هیچ کاری دوباره در فهرست؛ خاموش ⇒ همان رفتارِ پیشین
  const w = await push(s.token, [row('warehouseEntries', `we-${uniq()}`, {
    productId: 'oil', units: 2, cartons: 0, perCarton: 0, unit: 'بوتل', price: 250, createdAt: Date.now(),
  })]);
  assert.equal(w.status, 200, JSON.stringify(w.body));
  //  خواندنِ ویترین دفتر را حداکثر هر چند ثانیه یک بار تازه می‌کند (`REFRESH_THROTTLE_MS`)؛ این‌جا همان تازه‌کردن بی صبر
  await require('../src/lib/catalog').refresh(s.shopId);
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products`)), ['rice', 'oil']);
  assert.equal((await save(s, { hideOutOfStock: false })).status, 200);
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products`)), ['rice', 'oil', 'soap', 'salt']);
});

test('⛔ WEB-09: موجودیِ نامعلوم هرگز پنهان نمی‌شود (پرچمِ `shop.catalog` خاموش ⇒ همهٔ کالاها «در حال بررسی» و دیدنی)', async () => {
  const s = await liveStore('نامعلوم');
  assert.equal((await save(s, { hideOutOfStock: true })).status, 200);
  assert.deepEqual(ids(await h.get(`/api/store/${s.slug}/products`)), ['rice']);
  await setFlag(s.shopId, 'shop.catalog', false);
  const list = await h.get(`/api/store/${s.slug}/products`);
  assert.deepEqual(ids(list), ['rice', 'oil', 'soap', 'salt']);
  assert.ok(list.body.items.every((p) => p.stock.status === 'unverified'), JSON.stringify(badges(list)));
  assert.deepEqual((await h.get(`/api/store/${s.slug}`)).body.store.categories.slice().sort(), ['بهداشتی', 'خوراکی']);
});

test('مهاجرتِ ۲۴۰: rollback ستون و ردیفش را برمی‌دارد و دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '240_storefront_hide_out_of_stock.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const cols = async () => (await query(
    "SELECT column_name FROM information_schema.columns WHERE table_name='storefronts' AND column_name = 'hide_out_of_stock'"
  )).rows.length;
  assert.equal(await cols(), 1);
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await cols(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await cols(), 1);
});
