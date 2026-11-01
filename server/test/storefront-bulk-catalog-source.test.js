'use strict';
/**
 * S1 × M2 — انتشارِ گروهی (`shop.storefront.bulk`) وقتی ویترین از کاتالوگِ واحد
 * (`storefront.catalog_source`) می‌خواند.
 *
 * ریشهٔ خطا: `setProducts` (M2) کالا را هنوز در `sync_rows` (دفترِ v1) می‌جست، در حالی که
 * با پرچمِ S1 ویترین از `catalog_products` می‌خواند ⇒ در دکانِ v3 همهٔ کالاها
 * `product_not_found` («هنوز به سرور همگام نشده») می‌گرفتند و هیچ چیز منتشر نمی‌شد.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) هر دو پرچم روشن: دکانِ v1 و دکانِ v3 همان پاسخِ گروهی (پیدا، دلیلِ نیامدن، قیمت)
 *      و همان ویترین را می‌دهند.
 *   ۲) انتشارِ گروهی کاتالوگ را بی مهلت تازه می‌کند (همان `strict` ِ انتشارِ تکی): کالای
 *      تازهٔ دفتر بی‌درنگ پیدا می‌شود و کالای حذف‌شده دیگر نه.
 *   ۳) پرچمِ کاتالوگ خاموش ⇒ همان `sync_rows` ِ پیشین (دکانِ v1 بی تغییر).
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
//  مهلتِ بلند: خواندنِ پرترافیک (ویترین، فهرستِ کارمند) کاتالوگ را فقط یک بار پر می‌کند؛
//  پس هر «تازه»‌ای پس از آن فقط از راهِ تازه‌سازیِ بی‌مهلتِ نوشتن می‌آید (آزمونِ ۲)
process.env.CATALOG_REFRESH_THROTTLE_MS = '600000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const hlc = require('../src/lib/hlc');

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

// ── دو دفترِ همگام‌سازی (همان شکلِ storefront-catalog-source.test.js) ──

const pushV1 = (o, products) => h.api('POST', '/api/sync/v1/push', {
  token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-v1' },
  body: {
    device_id: 'dev-v1', schema_version: 2,
    ops: products.map(([rowId, fields, type = 'insert']) => ({
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
    })),
  },
});

let tick = 0;
let opN = 0;
const H = () => hlc.format({ wall: Date.now() - 60_000 + (++tick) * 10, counter: 0, node: 'v3bk' });
const oid = () => `01JV3BKC${String(++opN).padStart(18, '0')}`;
const pushV3 = (o, products) => h.api('POST', '/api/sync/v3/push', {
  token: o.token,
  body: {
    device_id: 'dev-v3', protocol: 3,
    ops: products.map(([rowId, fields, kind = 'insert']) => ({ op_id: oid(), table: 'products', row_id: rowId, kind, hlc: H(), fields })),
  },
});
const push = (o, products) => (o.fmt === 'v3' ? pushV3(o, products) : pushV1(o, products));

//  یک فهرست در دو قالب: v1 اعشاری (`salePrice`)، v3 صحیح (`salePriceMinor`)؛ null ⇒ بی‌قیمت
const LIST = [
  ['rice', 'برنج', 'کیلو', 12050, true],
  ['oil', 'روغن', 'بوتل', 30000, true],
  ['free', 'بی‌قیمت', 'دانه', null, true],
  ['old', 'کهنه', 'دانه', 1000, false],
  ['bad', 'جریمه‌دار', 'دانه', 5000, true],
];
const fieldsOf = (fmt, [, name, unit, minor, active]) => ({
  name, unit, category: 'خوراکی', active,
  ...(minor === null ? {} : fmt === 'v3' ? { salePriceMinor: minor } : { salePrice: minor / 100 }),
});

async function liveStore(label, fmt) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, fmt };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  await setFlag(o.shopId, 'shop.storefront.bulk', true);
  o.slug = `k-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o, LIST.map((x) => [x[0], fieldsOf(fmt, x)]));
  assert.equal(p.status, 200, JSON.stringify(p.body));
  //  جریمهٔ «پنهان کردنِ کالا» برای `bad` (همان دلیلِ `hidden_listing` ِ M2)
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
  return o;
}

const bulk = (o, body, key = `bulk-${uniq()}-key`) => h.api('PUT', '/api/shop/storefront/products', {
  token: o.token, body, headers: { 'Idempotency-Key': key },
});
const storeList = async (o) => {
  const r = await h.get(`/api/store/${o.slug}/products`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.map((p) => [p.id, p.priceMinor]).sort();
};
const byId = (results) => Object.fromEntries(results.map((x) => [x.productId, x]));

const ALL = ['rice', 'oil', 'free', 'old', 'bad', 'ghost'];
const publishAll = (o) => bulk(o, {
  items: ALL.map((productId) => (productId === 'oil'
    ? { productId, published: true, onlinePriceMinor: 29000 }
    : { productId, published: true })),
});

/* ══════════════════════════════════════════════════════════════════ */

test('هر دو پرچم روشن: انتشارِ گروهی در دکانِ v1 و v3 یکی — همه پیدا، همان دلیل‌ها، همان ویترین', async () => {
  await setFlag('all', 'storefront.catalog_source', true);
  const a = await liveStore('گروهی-یک', 'v1');
  const b = await liveStore('گروهی-سه', 'v3');
  const out = {};
  for (const o of [a, b]) {
    const r = await publishAll(o);
    assert.equal(r.status, 200, `${o.fmt} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.applied, 5, `${o.fmt}: همهٔ کالاهای دفتر پیدا شدند ${JSON.stringify(r.body.results)}`);
    assert.equal(r.body.notFound, 1, o.fmt);
    assert.equal(r.body.published, 5, o.fmt);
    const by = byId(r.body.results);
    assert.deepEqual(by.rice.notLive, [], o.fmt);
    assert.deepEqual(by.oil.notLive, [], o.fmt);
    assert.equal(by.oil.onlinePriceMinor, 29000, o.fmt);
    assert.deepEqual(by.free.notLive, ['no_price'], o.fmt);
    assert.deepEqual(by.old.notLive, ['inactive'], o.fmt);
    assert.deepEqual(by.bad.notLive, ['hidden_listing'], o.fmt);
    assert.deepEqual(by.ghost, { productId: 'ghost', ok: false, error: 'product_not_found' }, o.fmt);
    out[o.fmt] = r.body.results;
  }
  assert.deepEqual(out.v3, out.v1, 'دکانِ v3 همان پاسخِ دکانِ v1');

  //  ویترین: فقط زنده‌ها، با همان قیمت (روغن با قیمتِ آنلاین)
  for (const o of [a, b]) assert.deepEqual(await storeList(o), [['oil', 29000], ['rice', 12050]], o.fmt);

  //  فهرستِ کارمند با پرچمِ گروهی: همان دلیل‌ها از همان کاتالوگ
  for (const o of [a, b]) {
    const staff = await h.get('/api/shop/storefront/products', { token: o.token });
    assert.equal(staff.status, 200, JSON.stringify(staff.body));
    assert.deepEqual(staff.body.items.map((p) => [p.id, p.notLive]).sort(), [
      ['bad', ['hidden_listing']], ['free', ['no_price']], ['oil', []], ['old', ['inactive']], ['rice', []],
    ], o.fmt);
  }

  //  برداشتنِ گروهی هم در دکانِ v3 می‌نشیند
  const off = await bulk(b, { items: [{ productId: 'rice', published: false }] });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal(off.body.applied, 1);
  assert.deepEqual(byId(off.body.results).rice.notLive, ['not_published']);
  assert.deepEqual(await storeList(b), [['oil', 29000]]);
  await setFlag('all', 'storefront.catalog_source', false);
});

test('هر دو پرچم روشن: انتشارِ گروهی کاتالوگ را بی مهلت تازه می‌کند — کالای تازه پیدا، حذف‌شده نه', async () => {
  await setFlag('all', 'storefront.catalog_source', true);
  for (const fmt of ['v1', 'v3']) {
    const o = await liveStore(`تازه-${fmt}`, fmt);
    //  خواندنِ پرترافیک کاتالوگ را یک بار پر می‌کند و مهلتِ «تازه؟» را برای این دکان می‌گیرد
    const staff = await h.get('/api/shop/storefront/products', { token: o.token });
    assert.equal(staff.status, 200, JSON.stringify(staff.body));
    assert.equal(staff.body.items.length, LIST.length, fmt);

    //  پس از آن: چای در دفتر می‌آید و برنج حذف می‌شود
    assert.equal((await push(o, [['tea', fieldsOf(fmt, ['tea', 'چای', 'بسته', 8000, true])]])).status, 200);
    assert.equal((await push(o, [['rice', {}, 'delete']])).status, 200);

    const r = await bulk(o, { items: [{ productId: 'tea', published: true }, { productId: 'rice', published: true }] });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    const by = byId(r.body.results);
    assert.equal(by.tea.ok, true, `${fmt}: کالای تازهٔ دفتر بی‌درنگ پیدا شد ${JSON.stringify(by.tea)}`);
    assert.deepEqual(by.tea.notLive, [], fmt);
    assert.deepEqual(by.rice, { productId: 'rice', ok: false, error: 'product_not_found' }, `${fmt}: حذف‌شده`);
    assert.equal((await one(
      'SELECT published FROM storefront_products WHERE shop_id = $1 AND product_id = $2', [o.shopId, 'tea']
    )).published, true, fmt);
    assert.equal((await one(
      'SELECT COUNT(*)::int AS n FROM storefront_products WHERE shop_id = $1 AND product_id = $2', [o.shopId, 'rice']
    )).n, 0, `${fmt}: برای کالای حذف‌شده ردیفی ساخته نشد`);
  }
  await setFlag('all', 'storefront.catalog_source', false);
});

test('پرچمِ کاتالوگ خاموش: انتشارِ گروهی همان sync_rows ِ پیشین — دکانِ v1 بی تغییر، کاتالوگ پر نمی‌شود', async () => {
  await setFlag('all', 'storefront.catalog_source', false);
  const a = await liveStore('خاموش-یک', 'v1');
  const r = await publishAll(a);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.applied, 5);
  assert.equal(r.body.notFound, 1);
  assert.deepEqual(await storeList(a), [['oil', 29000], ['rice', 12050]]);
  //  شکافِ پیشین (همان که S1 پشتِ پرچمش می‌بندد): کالای v3 در `sync_rows` نیست
  const b = await liveStore('خاموش-سه', 'v3');
  const r3 = await publishAll(b);
  assert.equal(r3.status, 200, JSON.stringify(r3.body));
  assert.equal(r3.body.applied, 0);
  assert.equal((await one(
    'SELECT COUNT(*)::int AS n FROM catalog_products WHERE shop_id = ANY($1::text[])', [[a.shopId, b.shopId]]
  )).n, 0, 'خاموش ⇒ کاتالوگ برای ویترین پر نمی‌شود');
});
