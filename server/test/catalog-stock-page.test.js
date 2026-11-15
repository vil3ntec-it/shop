'use strict';
/**
 * GATE-NEW-02 روی رابطِ هدف (INV-01@32 «تعریف و انتشار از یک صفحه»): همان سناریوهای INV ِ `catalog-stock.test.js`، این بار
 * از راهی که دکان‌دار واقعاً می‌رود — کالا روی دستگاه تعریف می‌شود (`POST /api/sync/v1/push`)، صفحهٔ همان کالا در برنامه وضعِ
 * ویترینش را از `GET /api/shop/storefront/products/:productId` می‌خواند و از همان‌جا منتشرش می‌کند (`PUT` ِ همان نشانی)، مشتری
 * کالا و نشانِ موجودی را در ویترینِ عمومی می‌بیند (`GET /api/store/:slug/products?ids=`) و با شمارهٔ تأییدشده سفارش می‌دهد
 * (`POST /api/store/:slug/checkout`). هر سناریو با تطبیقِ دفتر تمام می‌شود (`reconciled`: مانده = جمعِ دفتر = بازپخشِ stock-core).
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ انتشار و برداشتن از صفحهٔ کالا هیچ حرکتی در دفترِ موجودی نمی‌سازد (شمارِ `stock_moves` پیش و پس یکی).
 *   ⛔ همان شناسه در دستگاه، صفحهٔ کالا، کاتالوگ، ویترین و سفارش؛ نشانِ ویترین از همان دفتر (`catalog.publicStock`).
 *   ⛔ پاسخِ درِ صفحهٔ کالا در هر سناریو همان است که `core/test-vectors/product-page-inv-v1.json` نگه می‌دارد و مدلِ Kotlin ِ
 *      صفحه (`ProductStorefront`، آزمونِ `ProductStorefrontInvVectorsTest`) همان پاسخ‌ها را با کلاینتِ واقعی می‌خواند.
 *      نوشتنِ دوبارهٔ بردار: `UPDATE_VECTORS=1`.
 *
 * بیرون از این رابط (با دلیل): INV-08 و INV-12 (حرکت‌های دستیِ انبار، درِ `/api/shop/catalog/moves` ِ کارمند — نه صفحهٔ کالا،
 * نه ویترین؛ اثرِ جابه‌جایی بر نشانِ ویترین در INV-02 همین‌جا)، INV-09 (§۶.۵، سناریوی کاتالوگ نیست)، INV-16 (خودِ `reconciled`).
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const VECTORS = path.join(__dirname, '..', '..', 'core', 'test-vectors', 'product-page-inv-v1.json');
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
test.beforeEach(() => resetAll());

// ── کمکی‌ها ──────────────────────────────────────────────────────────

let n = 0;
const uniq = () => `${Date.now().toString(36)}${++n}`;
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
/** دکانِ روشن (سفارش، کاتالوگ، ویترین) با ویترینِ باز — همان چیزی که صفحهٔ کالا در برنامه لازم دارد */
async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...(await h.signIn(u, 'shop')), shopId: made.body.shop.id };
  s.token = s.accessToken;
  s.device = `dev-p-${s.shopId}`.slice(0, 60);
  for (const k of ['shop.orders', 'shop.catalog', 'shop.storefront']) await live(s.shopId, k, true);
  s.slug = `pg-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${name}`, contactPhone: '0700000001' },
    { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return s;
}

let opN = 0;
const opId = () => `01JPG${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops, { device = s.device, ...extra } = {}) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: device, schema_version: 2, ops, ...extra },
    headers: { 'X-App': 'shop', 'X-Device': device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  return r.body;
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id, f = {}) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(), ...f,
});
const entry = (id, pid, units) => op('insert', 'warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});
const sale = (id, items) => [
  op('insert', 'sales', id, { status: 'completed', total: 0, createdAt: Date.now() }),
  ...items.map(([iid, pid, q]) => op('insert', 'saleItems', iid, {
    saleId: id, productId: pid, quantity: q, returnedQty: 0, unitPrice: 50, createdAt: Date.now(),
  })),
];

//  صفحهٔ کالا در برنامه (INV-01@32)
const page = (s, pid, token = s.token) => h.get(`/api/shop/storefront/products/${encodeURIComponent(pid)}`, { token });
const publish = (s, pid, published = true, token = s.token) =>
  h.put(`/api/shop/storefront/products/${encodeURIComponent(pid)}`, { published }, { token });
/** انتشار از صفحه و پس از آن همان صفحه ⇒ منتشرشده؛ دفتر بی حرکتِ تازه */
async function publishFromPage(s, pid) {
  const before = await moves(s);
  const r = await publish(s, pid);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const p = await page(s, pid);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.published, true);
  assert.equal(await moves(s), before, 'انتشار از صفحهٔ کالا به دفترِ موجودی دست زد');
  return p;
}

//  ویترینِ عمومی و سفارشِ مشتری
async function shelf(s, pid) {
  const r = await h.get(`/api/store/${s.slug}/products?ids=${encodeURIComponent(pid)}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.find((i) => i.id === pid) || null;
}
const tickets = new Map();
async function ticket(s, phone) {
  const k = `${s.slug}:${phone}`;
  if (!tickets.has(k)) {
    const a = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(phone) });
    assert.equal(a.status, 200, JSON.stringify(a.body));
    const c = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(phone), code: a.body.devCode });
    assert.equal(c.status, 200, JSON.stringify(c.body));
    tickets.set(k, c.body.ticket);
  }
  return tickets.get(k);
}
async function buy(s, lines, { key = `pg-${uniq()}`, phone = '0791112233' } = {}) {
  resetAll();
  return h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ صفحه', phone }, lines,
  }, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': await ticket(s, phone) } });
}

const cat = (s, p) => h.get(`/api/shop/catalog${p}`, { token: s.token });
async function stockOf(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock;
}
const moves = async (s) => Number((await one('SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1', [s.shopId])).n);
const orders = async (s) => Number((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [s.shopId])).n);
/** INV-16: مانده = جمعِ دفتر = بازپخشِ هسته (همان سنجهٔ `catalog-stock.test.js`). */
async function reconciled(s, pid) {
  const all = await cat(s, '/report');
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.equal(all.body.ok, true, JSON.stringify(all.body.mismatches));
  if (pid) {
    const r = await cat(s, `/report?productId=${pid}`);
    assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
  }
}

/** پاسخِ درِ صفحهٔ کالا در هر سناریو ⇐ بردارِ Kotlin (`expect`: آن‌چه `ProductStorefront` باید از آن بسازد) */
const recorded = [];
function rec(name, inv, res, expect) {
  recorded.push({ name, inv, path: `/api/v1/shop/storefront/products/${res.pid}`, status: res.status, body: res.body, expect });
}
const seen = async (s, pid) => ({ ...(await page(s, pid)), pid });

// ════════════════════════════════════════════════════════════════════

test('INV-01: تعریف روی دستگاه، انتشار از صفحهٔ همان کالا ⇒ همان شناسه در صفحه، کاتالوگ، ویترین و سفارش؛ دفتر دست نمی‌خورد', async () => {
  const s = await shop('یک‌صفحه');
  const early = await seen(s, 'pg-rice');
  assert.equal(early.status, 404, 'هنوز همگام نشده');
  rec('not_synced', 'INV-01', early, { state: 'NotSynced' });

  await push(s, [product('pg-rice', { name: 'برنج', salePrice: 120.5, category: 'غله' }), entry('pg-rice-e', 'pg-rice', 10)]);
  const synced = await seen(s, 'pg-rice');
  assert.equal(synced.status, 200, JSON.stringify(synced.body));
  assert.deepEqual([synced.body.id, synced.body.published, synced.body.active, synced.body.priceMinor], ['pg-rice', false, true, 12050]);
  rec('synced_unpublished', 'INV-01', synced, { state: 'Ready', published: false, active: true, subtitle: 'ledgerPrice' });
  assert.equal(await shelf(s, 'pg-rice'), null, 'منتشرنشده در ویترین نیست');

  await publishFromPage(s, 'pg-rice');
  rec('published', 'INV-01', await seen(s, 'pg-rice'), { state: 'Ready', published: true, active: true, subtitle: 'ledgerPrice' });
  const item = await shelf(s, 'pg-rice');
  assert.equal(item?.id, 'pg-rice');
  assert.equal(item.stock.status, 'in_stock', 'نشانِ ویترین از همان دفتر');
  assert.equal((await stockOf(s, 'pg-rice')).sellableMilli, 10000);

  const o = await buy(s, [{ productId: 'pg-rice', qtyMilli: 2000 }]);
  assert.equal(o.status, 201, JSON.stringify(o.body));
  assert.equal((await stockOf(s, 'pg-rice')).sellableMilli, 10000, 'سفارشِ ویترین چیزی کم نمی‌کند');
  await reconciled(s, 'pg-rice');
});

test('INV-02: قفسه صفر و انبار ۵ ⇒ ویترینِ کالای منتشرشده «در انبار» می‌گوید، نه «تمام شده»؛ سفارش پذیرفته و بی‌رزرو', async () => {
  const s = await shop('انبارِ صفحه');
  await push(s, [product('pg-w'), entry('pg-w-e', 'pg-w', 5)]);
  const t = await h.post('/api/shop/catalog/moves', { kind: 'transfer', productId: 'pg-w', from: 'shop_floor', to: 'warehouse', qtyMilli: 5000 },
    { token: s.token, headers: { 'Idempotency-Key': `pg-mv-${uniq()}` } });
  assert.equal(t.status, 201, JSON.stringify(t.body));
  await publishFromPage(s, 'pg-w');
  assert.equal((await shelf(s, 'pg-w')).stock.status, 'in_warehouse');
  assert.equal((await buy(s, [{ productId: 'pg-w', qtyMilli: 2000 }])).status, 201);
  assert.equal((await stockOf(s, 'pg-w')).sellableMilli, 5000);
  await reconciled(s, 'pg-w');
});

test('INV-03: کالای منتشرشده با قفسه و انبارِ صفر ⇒ ویترین «تمام شده» و سفارش بی ردیف رد؛ صفحه همچنان منتشرشده', async () => {
  const s = await shop('صفرِ صفحه');
  await push(s, [product('pg-z', { name: 'روغن' })]);
  await publishFromPage(s, 'pg-z');
  assert.equal((await shelf(s, 'pg-z')).stock.status, 'out_of_stock');
  const o = await buy(s, [{ productId: 'pg-z', qtyMilli: 1000 }]);
  assert.equal(o.status, 409, JSON.stringify(o.body));
  assert.equal(o.body.error.code, 'out_of_stock');
  assert.equal(await orders(s), 0);
  assert.equal((await page(s, 'pg-z')).body.published, true, 'انتشار وضعِ موجودی نیست');
  await reconciled(s, 'pg-z');
});

test('INV-04: ۵ موجود ⇒ سفارشِ ۴ از ویترین چیزی کم نمی‌کند؛ ۶ رد با مقدارِ قابلِ سفارش؛ فروشِ دستگاه کم می‌کند', async () => {
  const s = await shop('آزادِ صفحه');
  await push(s, [product('pg-f'), entry('pg-f-e', 'pg-f', 5)]);
  await publishFromPage(s, 'pg-f');
  assert.equal((await buy(s, [{ productId: 'pg-f', qtyMilli: 4000 }])).status, 201);
  assert.equal((await stockOf(s, 'pg-f')).sellableMilli, 5000);
  const six = await buy(s, [{ productId: 'pg-f', qtyMilli: 6000 }]);
  assert.equal(six.status, 409);
  assert.equal(six.body.error.lines[0].availableMilli, 5000);
  //  دکان تحویل داد ⇒ فروشِ دستگاه همه را کم کرد. سفارشِ بعدی همان دم با دفترِ تازه سنجیده و رد می‌شود؛ نشانِ ویترین حداکثر
  //  یک پنجرهٔ `REFRESH_THROTTLE_MS` دیرتر (`lib/catalog.js` › `refresh`) — این‌جا پس از همان سفارش که دفتر را خواند
  await push(s, sale('pg-f-s', [['pg-f-i', 'pg-f', 5]]));
  const late = await buy(s, [{ productId: 'pg-f', qtyMilli: 1000 }]);
  assert.equal(late.status, 409, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'out_of_stock');
  assert.equal((await shelf(s, 'pg-f')).stock.status, 'out_of_stock');
  await reconciled(s, 'pg-f');
});

test('INV-05: خریدِ هم‌زمانِ آخرین عدد از ویترین ⇒ هر دو ثبت، موجودی دست نمی‌خورد و منفی نمی‌شود', async () => {
  const s = await shop('هم‌زمانِ صفحه');
  await push(s, [product('pg-l', { name: 'شیر' }), entry('pg-l-e', 'pg-l', 1)]);
  await publishFromPage(s, 'pg-l');
  await ticket(s, '0791112244');
  await ticket(s, '0791112255');
  const two = await Promise.all([
    buy(s, [{ productId: 'pg-l', qtyMilli: 1000 }], { phone: '0791112244' }),
    buy(s, [{ productId: 'pg-l', qtyMilli: 1000 }], { phone: '0791112255' }),
  ]);
  assert.deepEqual(two.map((r) => r.status), [201, 201], JSON.stringify(two.map((r) => r.body)));
  const st = await stockOf(s, 'pg-l');
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli, st.sellableMilli], [1000, 0, 1000]);
  await reconciled(s, 'pg-l');
});

test('INV-06: فروشِ حضوری و سفارشِ ویترینِ هم‌زمان ⇒ فروشِ ثبت‌شده رد نمی‌شود؛ فقط همان کم می‌شود', async () => {
  const s = await shop('حضوریِ صفحه');
  await push(s, [product('pg-m'), entry('pg-m-e', 'pg-m', 2)]);
  await publishFromPage(s, 'pg-m');
  await ticket(s, '0791112233');
  const [pushed, other] = await Promise.all([push(s, sale('pg-m-s', [['pg-m-i', 'pg-m', 1]])), buy(s, [{ productId: 'pg-m', qtyMilli: 1000 }])]);
  assert.ok(pushed.results.every((x) => x.status === 'applied'));
  assert.equal(other.status, 201, JSON.stringify(other.body));
  assert.equal((await stockOf(s, 'pg-m')).sellableMilli, 1000);
  await reconciled(s, 'pg-m');
});

test('INV-07: فروشِ آفلاینِ دو دستگاه آخرین عدد را منفی می‌کند ⇒ ویترین «تمام شده» و سفارشِ تازه رد؛ دفتر همان', async () => {
  const s = await shop('دو دستگاهِ صفحه');
  await push(s, [product('pg-d', { minStock: 0 }), entry('pg-d-e', 'pg-d', 1)]);
  await publishFromPage(s, 'pg-d');
  await push(s, sale('pg-d-sa', [['pg-d-ia', 'pg-d', 1]]), { device: `${s.device}-A` });
  await push(s, sale('pg-d-sb', [['pg-d-ib', 'pg-d', 1]]), { device: `${s.device}-B` });
  assert.equal((await stockOf(s, 'pg-d')).levels.shop_floor, -1000);
  assert.equal((await shelf(s, 'pg-d')).stock.status, 'out_of_stock');
  assert.equal((await buy(s, [{ productId: 'pg-d', qtyMilli: 1000 }])).status, 409);
  await reconciled(s, 'pg-d');
});

test('INV-10: کالای کارتنی (۲۴ دانه) در صفحه و ویترین با واحدِ پایه؛ سفارشِ ویترین با همان واحد سنجیده می‌شود', async () => {
  const s = await shop('واحدِ صفحه');
  await push(s, [product('pg-u', { unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24 }), entry('pg-u-e', 'pg-u', 30)]);
  const p = await publishFromPage(s, 'pg-u');
  assert.equal(p.body.unit, 'دانه');
  assert.equal((await buy(s, [{ productId: 'pg-u', qtyMilli: 24000 }])).status, 201);
  const over = await buy(s, [{ productId: 'pg-u', qtyMilli: 31000 }]);
  assert.equal(over.status, 409);
  assert.equal(over.body.error.lines[0].availableMilli, 30000);
  await reconciled(s, 'pg-u');
});

test('INV-11: دکانِ ب کالای الف را از صفحهٔ کالا نه می‌خواند نه منتشر می‌کند، در ویترینش نمی‌بیند و نمی‌فروشد', async () => {
  const a = await shop('الفِ صفحه');
  const b = await shop('بِ صفحه');
  await push(a, [product('pg-iso', { salePrice: 999 }), entry('pg-iso-e', 'pg-iso', 3)]);
  await publishFromPage(a, 'pg-iso');
  const r = await seen(b, 'pg-iso');
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'product_not_found');
  rec('other_shop', 'INV-11', r, { state: 'NotSynced' });
  assert.equal((await publish(b, 'pg-iso')).status, 404);
  assert.equal(await one('SELECT 1 FROM storefront_products WHERE shop_id=$1 AND product_id=$2', [b.shopId, 'pg-iso']), null);
  assert.equal(await shelf(b, 'pg-iso'), null);
  const steal = await buy(b, [{ productId: 'pg-iso', qtyMilli: 1000 }]);
  assert.equal(steal.status, 409, JSON.stringify(steal.body));
  assert.equal(steal.body.error.code, 'product_unavailable');
  assert.equal((await stockOf(a, 'pg-iso')).levels.shop_floor, 3000);
  await reconciled(a, 'pg-iso');
  await reconciled(b);
});

test('INV-13: غیرفعال ⇒ صفحه «غیرفعال» و ویترین بی آن، سفارش رد؛ حذف‌شده ⇒ صفحه «نرسیده» و انتشار ۴۰۴', async () => {
  const s = await shop('غیرفعالِ صفحه');
  await push(s, [product('pg-in'), entry('pg-in-e', 'pg-in', 5), product('pg-dl'), entry('pg-dl-e', 'pg-dl', 5)]);
  await publishFromPage(s, 'pg-in');
  await publishFromPage(s, 'pg-dl');
  await push(s, [op('update', 'products', 'pg-in', { active: false }), op('delete', 'products', 'pg-dl')]);
  const inactive = await seen(s, 'pg-in');
  assert.equal(inactive.status, 200);
  assert.equal(inactive.body.active, false);
  rec('inactive', 'INV-13', inactive, { state: 'Ready', published: true, active: false, subtitle: 'inactive' });
  const gone = await seen(s, 'pg-dl');
  assert.equal(gone.status, 404);
  rec('deleted', 'INV-13', gone, { state: 'NotSynced' });
  assert.equal((await publish(s, 'pg-dl', false)).status, 404);
  for (const pid of ['pg-in', 'pg-dl']) {
    assert.equal(await shelf(s, pid), null, pid);
    const o = await buy(s, [{ productId: pid, qtyMilli: 1000 }]);
    assert.equal(o.status, 409, pid);
    assert.equal(o.body.error.code, 'product_unavailable');
  }
  await reconciled(s);
});

test('INV-14: دستگاهِ کهنه یا صفِ نفرستاده ⇒ ویترینِ کالای منتشرشده «در حال بررسی»، هرگز «موجود»؛ صفحه همان', async () => {
  const s = await shop('کهنهٔ صفحه');
  await push(s, [product('pg-st'), entry('pg-st-e', 'pg-st', 5)]);
  await publishFromPage(s, 'pg-st');
  assert.equal((await h.put('/api/shop/catalog/settings', { staleAfterSec: 60 }, { token: s.token })).status, 200);
  await query(`UPDATE sync_devices SET last_seen_at = $2 WHERE app='shop' AND account_id=$1`, [s.shopId, Date.now() - 600_000]);
  assert.deepEqual([(await shelf(s, 'pg-st')).stock.status, (await shelf(s, 'pg-st')).stock.reason], ['unverified', 'stale']);
  rec('stale_device', 'INV-14', await seen(s, 'pg-st'), { state: 'Ready', published: true, active: true, subtitle: 'ledgerPrice' });
  assert.equal((await buy(s, [{ productId: 'pg-st', qtyMilli: 1000 }])).status, 201);
  await push(s, [], { queued: 3 });
  assert.equal((await shelf(s, 'pg-st')).stock.reason, 'pending_sync');
  await push(s, [], { queued: 0 });
  assert.equal((await shelf(s, 'pg-st')).stock.status, 'in_stock');
  await reconciled(s, 'pg-st');
});

test('INV-15: انتشارِ دوباره از صفحه و سفارشِ تکراریِ ویترین با همان کلید اثرِ دوم ندارد', async () => {
  const s = await shop('تکرارِ صفحه');
  await push(s, [product('pg-k'), entry('pg-k-e', 'pg-k', 5)]);
  await publishFromPage(s, 'pg-k');
  await publishFromPage(s, 'pg-k');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM storefront_products WHERE shop_id=$1', [s.shopId])).n), 1);
  const key = `pg-k-${uniq()}`;
  assert.equal((await buy(s, [{ productId: 'pg-k', qtyMilli: 2000 }], { key })).status, 201);
  const again = await buy(s, [{ productId: 'pg-k', qtyMilli: 2000 }], { key });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal(await orders(s), 1);
  //  برداشتن از همان صفحه ⇒ از ویترین بیرون، دفتر همان
  const before = await moves(s);
  assert.equal((await publish(s, 'pg-k', false)).status, 200);
  assert.equal(await shelf(s, 'pg-k'), null);
  assert.equal(await moves(s), before);
  await reconciled(s, 'pg-k');
});

test('GATE-NEW-02 (صفحهٔ کالا): هر سناریوی INV ِ همین پرونده از صفحهٔ کالا می‌گذرد و با تطبیقِ دفتر تمام می‌شود', () => {
  const src = fs.readFileSync(__filename, 'utf8');
  const blocks = src.split(/\ntest\(/).slice(1).filter((b) => /^'INV-\d\d/.test(b));
  const ids = new Set(blocks.map((b) => Number(b.slice(5, 7))));
  for (const i of [1, 2, 3, 4, 5, 6, 7, 10, 11, 13, 14, 15]) assert.ok(ids.has(i), `INV-${String(i).padStart(2, '0')} سناریو ندارد`);
  const bad = blocks.filter((b) => !b.includes('await reconciled(') || !/publishFromPage\(|seen\(/.test(b)).map((b) => b.slice(1, 7));
  assert.deepEqual(bad, [], 'سناریوی بی صفحهٔ کالا یا بی تطبیقِ دفتر');
});

test('بردارِ صفحهٔ کالا (core/test-vectors/product-page-inv-v1.json) همان پاسخ‌های زندهٔ همین سناریوهاست', () => {
  const names = ['not_synced', 'synced_unpublished', 'published', 'other_shop', 'inactive', 'deleted', 'stale_device'];
  assert.deepEqual(recorded.map((r) => r.name).sort(), [...names].sort(), 'همهٔ سناریوها اجرا نشدند (پالایشِ نام؟)');
  const doc = { v: 1, about: 'GATE-NEW-02 ⇐ server/test/catalog-stock-page.test.js؛ خواننده: ProductStorefrontInvVectorsTest', cases: recorded };
  if (process.env.UPDATE_VECTORS === '1') fs.writeFileSync(VECTORS, `${JSON.stringify(doc, null, 2)}\n`);
  assert.deepEqual(JSON.parse(fs.readFileSync(VECTORS, 'utf8')), JSON.parse(JSON.stringify(doc)), 'بردار کهنه است: UPDATE_VECTORS=1');
});
