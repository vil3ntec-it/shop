'use strict';
/**
 * انتشارِ گروهیِ کالا — `PUT /api/shop/storefront/products` (M2 ِ قراردادِ بازار).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۰) ⛔ پیش‌فرض خاموش: بی `shop.storefront.bulk` مسیرِ گروهی ۴۰۳ و هیچ ردیفی؛
 *      `/config` می‌گوید `bulk: false` و فهرستِ کالا همان شکلِ قبلی (بی `notLive`).
 *   ۱) ۵۰۰ کالا در یک درخواست، همه در ویترین؛ ۵۰۱ ⇒ ۴۰۰ و هیچ چیز.
 *   ۲) کلیدِ یکتایی اجباری؛ تکرارِ همان کلید و بدنه ⇒ همان پاسخ، بی نوشتنِ دوباره؛
 *      همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ و بی تغییر.
 *   ۳) فقط فیلدهای آمده عوض می‌شوند (قیمتِ آنلاینِ پیشین پاک نمی‌شود).
 *   ۴) دلیلِ نیامدن برای هر کالا: بی‌قیمت، غیرفعال، پنهان‌شده با جریمه، نبوده در دفتر.
 *   ۵) بدنهٔ نادرست (شناسهٔ تکراری، عددِ بد) ⇒ ۴۰۰ و هیچ نیمه‌نوشتنی.
 *   ۶) نقش: کارمندِ بی `settings.write` نمی‌نویسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
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
test.after(() => h.stop());
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-bulk') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function store(label, { bulk = true, extra = [] } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (bulk) await setFlag(o.shopId, 'shop.storefront.bulk', true);
  const put = await h.put('/api/shop/storefront', { slug: `b-${uniq()}`, enabled: true, title: `ویترینِ ${label}` }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const ops = [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('free', { name: 'بی‌قیمت', unit: 'دانه', category: 'خوراکی' }),
    op('old', { name: 'کهنه', unit: 'دانه', salePrice: 10, active: false }),
    op('bad', { name: 'جریمه‌دار', unit: 'دانه', salePrice: 50 }),
    ...extra,
  ];
  for (let i = 0; i < ops.length; i += 200) {
    const p = await push(o.token, ops.slice(i, i + 200));
    assert.equal(p.status, 200, JSON.stringify(p.body));
  }
  return o;
}

const bulk = (o, body, key = `bulk-${uniq()}-key`) => h.api('PUT', '/api/shop/storefront/products', {
  token: o.token, body, headers: key ? { 'Idempotency-Key': key } : {},
});
const count = async (shopId) => Number((await one(
  'SELECT COUNT(*)::int AS n FROM storefront_products WHERE shop_id = $1', [shopId]
)).n);

test('⛔ پیش‌فرض خاموش: بی پرچمِ گروهی ۴۰۳، هیچ ردیف، پاسخِ فهرست همان قبلی', async () => {
  const o = await store('خاموش', { bulk: false });
  const cfg = await h.get('/api/shop/storefront/config', { token: o.token });
  assert.equal(cfg.body.bulk, false);
  const r = await bulk(o, { items: [{ productId: 'rice', published: true }] });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'bulk_disabled');
  assert.equal(await count(o.shopId), 0);
  const list = await h.get('/api/shop/storefront/products', { token: o.token });
  assert.equal(list.status, 200);
  assert.ok(list.body.items.every((p) => !('notLive' in p)), 'بی پرچم فیلدِ تازه‌ای نیست');
  //  مسیرِ تکیِ قبلی دست نخورده
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
});

test('۵۰۰ کالا در یک درخواست؛ تکرار با همان کلید اثرِ دوباره ندارد', async () => {
  const ids = Array.from({ length: 500 }, (_, i) => `p${String(i).padStart(3, '0')}`);
  const o = await store('پانصد', { extra: ids.map((id, i) => op(id, { name: `کالای ${i}`, unit: 'دانه', salePrice: 10 + i })) });
  assert.equal((await h.get('/api/shop/storefront/config', { token: o.token })).body.bulk, true);
  const body = { items: ids.map((productId) => ({ productId, published: true, maxQtyMilli: 5000 })) };
  const key = `bulk-${uniq()}-500`;
  const first = await bulk(o, body, key);
  assert.equal(first.status, 200, JSON.stringify(first.body).slice(0, 400));
  assert.equal(first.body.applied, 500);
  assert.equal(first.body.published, 500);
  assert.equal(first.body.replayed, false);
  assert.ok(first.body.results.every((r) => r.ok && r.published && r.notLive.length === 0));
  const live = await one(
    'SELECT COUNT(*)::int AS n, MAX(updated_at) AS at FROM storefront_products WHERE shop_id = $1 AND published AND max_qty_milli = 5000',
    [o.shopId]
  );
  assert.equal(live.n, 500);
  const audits = async () => Number((await one(
    "SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id = $1 AND action = 'storefront.products_bulk'", [o.shopId]
  )).n);
  assert.equal(await audits(), 1);

  //  کالا را دستی برمی‌داریم؛ تکرارِ همان درخواست نباید دوباره منتشرش کند
  assert.equal((await h.put('/api/shop/storefront/products/p000', { published: false }, { token: o.token })).status, 200);
  const again = await bulk(o, body, key);
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.replayed, true);
  assert.deepEqual(again.body.results, first.body.results, 'همان پاسخِ اول');
  assert.equal((await one('SELECT published FROM storefront_products WHERE shop_id = $1 AND product_id = $2', [o.shopId, 'p000'])).published, false);
  assert.equal(await audits(), 1, 'تکرار سابقهٔ دوم نمی‌سازد');

  //  همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ و بی تغییر
  const other = await bulk(o, { items: [{ productId: 'p000', published: true }] }, key);
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  assert.equal((await one('SELECT published FROM storefront_products WHERE shop_id = $1 AND product_id = $2', [o.shopId, 'p000'])).published, false);

  //  دو درخواستِ هم‌زمان با یک کلید ⇒ یک بار نوشتن
  const key2 = `bulk-${uniq()}-race`;
  const race = await Promise.all([1, 2, 3].map(() => bulk(o, { items: [{ productId: 'p000', published: true }] }, key2)));
  assert.ok(race.every((r) => r.status === 200));
  assert.equal(race.filter((r) => r.body.replayed === false).length, 1);
  assert.equal(await audits(), 2);

  //  ۵۰۱ ⇒ ۴۰۰ و هیچ چیز
  const tooMany = await bulk(o, { items: [...ids, 'rice'].map((productId) => ({ productId, published: false })) });
  assert.equal(tooMany.status, 400);
  assert.equal(tooMany.body.error.code, 'too_many_items');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM storefront_products WHERE shop_id = $1 AND published', [o.shopId])).n), 500);
});

test('کلیدِ یکتایی اجباری؛ بدنهٔ نادرست هیچ چیزی نمی‌نویسد', async () => {
  const o = await store('کلید');
  const noKey = await bulk(o, { items: [{ productId: 'rice', published: true }] }, null);
  assert.equal(noKey.status, 400);
  assert.equal(noKey.body.error.code, 'idempotency_key_required');
  for (const [items, code] of [
    [[], 'bad_items'],
    [[{ productId: 'rice', published: true }, { productId: 'rice', published: false }], 'duplicate_product'],
    [[{ productId: 'rice', published: true }, { productId: 'free', onlinePriceMinor: -1 }], 'bad_number'],
    [[{ productId: 'rice', published: 'yes' }], 'bad_boolean'],
    [[{ productId: '../x' }], 'bad_product'],
  ]) {
    const r = await bulk(o, { items });
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, code);
  }
  assert.equal(await count(o.shopId), 0, 'هیچ نیمه‌نوشتنی');
});

test('فقط فیلدهای آمده؛ دلیلِ نیامدن برای هر کالا', async () => {
  const o = await store('دلیل');
  //  قیمت و توضیحِ پیشین با مسیرِ تکی
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: false, onlinePriceMinor: 15000, description: 'برنجِ خوب' }, { token: o.token })).status, 200);
  const at = now();
  const caseId = `tc-${uniq()}`;
  await query(
    `INSERT INTO trust_cases (id, shop_id, kind, subject, product_id, category, severity, reporter_key, created_at, updated_at)
     VALUES ($1,$2,'report','product','bad','other','low','k',$3,$3)`, [caseId, o.shopId, at]
  );
  await query(
    `INSERT INTO trust_sanctions (id, shop_id, case_id, kind, product_id, reason, created_by, created_at)
     VALUES ($1,$2,$3,'hide_listing','bad','آزمون','adm',$4)`, [`ts-${uniq()}`, o.shopId, caseId, at]
  );
  const r = await bulk(o, { items: ['rice', 'free', 'old', 'bad', 'ghost'].map((productId) => ({ productId, published: true })) });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const by = Object.fromEntries(r.body.results.map((x) => [x.productId, x]));
  assert.deepEqual(by.rice.notLive, []);
  assert.equal(by.rice.onlinePriceMinor, 15000, 'قیمتِ آنلاینِ پیشین ماند');
  assert.equal((await one('SELECT description FROM storefront_products WHERE shop_id = $1 AND product_id = $2', [o.shopId, 'rice'])).description, 'برنجِ خوب');
  assert.deepEqual(by.free.notLive, ['no_price']);
  assert.deepEqual(by.old.notLive, ['inactive']);
  assert.deepEqual(by.bad.notLive, ['hidden_listing']);
  assert.deepEqual(by.ghost, { productId: 'ghost', ok: false, error: 'product_not_found' });
  assert.equal(r.body.applied, 4);
  assert.equal(r.body.notFound, 1);

  //  قیمتِ آنلاین برای بی‌قیمت ⇒ دلیل می‌رود؛ null قیمتِ آنلاین را برمی‌دارد
  const r2 = await bulk(o, { items: [{ productId: 'free', onlinePriceMinor: 900, description: 'تازه' }, { productId: 'rice', onlinePriceMinor: null }] });
  const by2 = Object.fromEntries(r2.body.results.map((x) => [x.productId, x]));
  assert.deepEqual(by2.free.notLive, []);
  assert.equal(by2.free.published, true, 'انتشار دست نخورد');
  assert.equal(by2.rice.onlinePriceMinor, null);

  //  فهرستِ کارمند هم همان دلیل‌ها را دارد
  const list = await h.get('/api/shop/storefront/products', { token: o.token });
  const li = Object.fromEntries(list.body.items.map((x) => [x.id, x]));
  assert.deepEqual(li.bad.notLive, ['hidden_listing']);
  assert.deepEqual(li.old.notLive, ['inactive']);
  assert.deepEqual(li.free.notLive, []);

  //  ویترینِ عمومی همان قاعده را می‌بیند: جریمه‌دار و غیرفعال نیستند
  const slug = (await h.get('/api/shop/storefront/config', { token: o.token })).body.settings.slug;
  const pub = await h.get(`/api/store/${slug}/products`);
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  const shown = new Set(pub.body.items.map((x) => x.id));
  assert.ok(shown.has('rice') && shown.has('free'));
  assert.ok(!shown.has('bad') && !shown.has('old'));
});

test('نقش: کارمندِ بی settings.write نمی‌نویسد', async () => {
  const o = await store('نقش');
  const u = await h.newUser('فروشنده', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), o.shopId, u.user.id, now()]
  );
  const r = await h.api('PUT', '/api/shop/storefront/products', {
    token: u.accessToken, body: { items: [{ productId: 'rice', published: true }] }, headers: { 'Idempotency-Key': `bulk-${uniq()}-role` },
  });
  assert.equal(r.status, 403);
  assert.equal(await count(o.shopId), 0);
});
