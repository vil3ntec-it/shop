'use strict';
/**
 * کاتالوگ ⇄ ویترین از سرِ دکان تا صفحهٔ مشتری — v3 §۳۲.۴ (INV-01@32، INV-02@32).
 *
 * catalog-stock.test.js همین ردیف‌ها را در خودِ کاتالوگ (`/api/shop/catalog` و
 * `catalog.publicStock`) قفل می‌کند. این‌جا همان رفتار از درهای واقعیِ دو سرِ کار
 * سنجیده می‌شود، بی هیچ میان‌بُر:
 *   • دکان کالا را یک بار در فرمِ کالای دستگاه می‌سازد (`POST /api/sync/v1/push`)؛
 *   • صندوقِ دستگاهِ دیگر همان ردیف را می‌گیرد (`GET /api/sync/v1/pull`)؛
 *   • انبار همان شناسه را می‌بیند (`/api/shop/catalog`)؛
 *   • مالک از صفحهٔ «ویترین» منتشر می‌کند — همان درخواستِ
 *     `StorefrontRepository.setProduct` ِ برنامه (`PUT /api/shop/storefront/products/:id`)؛
 *   • مشتری فهرست، صفحهٔ کالا و ثبتِ سفارش را از `/api/store/:slug` می‌بیند.
 *
 * آن‌چه قفل می‌شود:
 *   INV-01@32 — یک تعریف ⇒ یک شناسه در صندوق، انبار، ویترین و قلمِ سفارش؛ پیش از
 *              انتشار و بی اجازهٔ `settings.write` هیچ اثری در ویترین نیست.
 *   INV-02@32 — قفسه صفر و انبار ۵: ویترین (فهرست و صفحهٔ کالا) «در انبار، آماده‌سازی
 *              لازم» می‌گوید نه «تمام شده»، عددِ انبار فقط طبقِ `showQty`، و ثبتِ سفارش
 *              با همان عدد سازگار است (۲ پذیرفته، ۶ رد با مقدارِ قابلِ سفارش).
 * صفحهٔ مرورگر (متنِ همین وضعیت روی کارت) در `web-store-stock.mjs` است.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

// ── کمکی‌ها ──────────────────────────────────────────────────────────

async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکان با سفارش، ویترین و کاتالوگِ روشن؛ ویترین «دریافت از دکان» می‌پذیرد. */
async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, device: `dev-a-${uniq()}` };
  for (const key of ['shop.orders', 'shop.storefront', 'shop.catalog']) await live(s.shopId, key, true);
  const slug = `cs-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0,
  }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...s, slug };
}

async function staff(s, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), s.shopId, u.user.id, now()]
  );
  return { ...u, token: u.accessToken };
}

let opN = 0;
const opId = () => `01JCF${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
const product = (id, f = {}) => op('products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(), ...f,
});
const entry = (id, pid, units) => op('warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});
async function push(s, ops, device = s.device) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}

/** همان بدنه‌ای که `StorefrontRepository.setProduct` ِ برنامه می‌فرستد. */
const publish = (s, id, published, token = s.token) => h.put(
  `/api/shop/storefront/products/${encodeURIComponent(id)}`,
  { published, onlinePriceMinor: null, maxQtyMilli: null },
  { token },
);
const mv = (s, body) => h.post('/api/shop/catalog/moves', body, { token: s.token, headers: { 'Idempotency-Key': `cs-mv-${uniq()}` } });
const stockSettings = (s, body) => h.put('/api/shop/catalog/settings', body, { token: s.token });

const pub = {
  list: async (s) => {
    const r = await h.get(`/api/store/${s.slug}/products`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.items;
  },
  detail: (s, id) => h.get(`/api/store/${s.slug}/products/${encodeURIComponent(id)}`),
};

async function ticketFor(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}
const checkout = (s, ticket, phone, lines) => h.post(`/api/store/${s.slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتریِ آزمایشی', phone }, lines,
}, { headers: { 'Idempotency-Key': `cs-ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
const orders = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;

// ════════════════════════════════════════════════════════════════════

test('INV-01@32: یک تعریف در فرمِ کالا ⇒ همان شناسه در صندوق، انبار، ویترین و سفارش؛ فقط پس از انتشارِ مجاز', async () => {
  const s = await liveShop('یک‌تعریف');
  const ID = 'tea-green-1';

  //  ۱) فرمِ کالای دستگاهِ A: یک بار تعریف، با ورودِ انبار
  await push(s, [
    product(ID, { name: 'چای سبز', salePrice: 80, barcodes: ['6260001112223'], category: 'نوشیدنی' }),
    entry(`${ID}-e1`, ID, 8),
  ]);

  //  ۲) صندوقِ دستگاهِ B همان ردیف را با همان شناسه می‌گیرد
  const pulled = await h.api('GET', `/api/sync/v1/pull?device_id=dev-b-${uniq()}&since=0`, { token: s.token, headers: { 'X-App': 'shop' } });
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));
  const pos = pulled.body.ops.filter((o) => o.table === 'products' && o.row_id === ID);
  assert.equal(pos.length, 1, 'صندوقِ دستگاهِ دیگر همان کالا را دارد');

  //  ۳) انبار (کاتالوگِ سرور) همان شناسه، بارکد و مانده
  const wh = await h.get(`/api/shop/catalog/products/${ID}`, { token: s.token });
  assert.equal(wh.status, 200, JSON.stringify(wh.body));
  assert.equal(wh.body.product.id, ID);
  assert.deepEqual(wh.body.product.barcodes, ['6260001112223']);
  assert.equal(wh.body.product.stock.levels.shop_floor, 8000);

  //  ۴) صفحهٔ «ویترین» ِ مالک همان کالا را منتشرنشده نشان می‌دهد
  const own = await h.get('/api/shop/storefront/products?limit=100', { token: s.token });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  const mine = own.body.items.find((p) => p.id === ID);
  assert.ok(mine, 'صفحهٔ مالک کالای همان شناسه را دارد');
  assert.equal(mine.published, false);

  //  ۵) ⛔ پیش از انتشار: نه در فهرست، نه صفحهٔ کالا، نه ثبتِ سفارش
  const phone = '0791110001';
  const ticket = await ticketFor(s.slug, phone);
  assert.ok(!(await pub.list(s)).some((p) => p.id === ID), 'منتشرنشده در ویترین نیست');
  assert.equal((await pub.detail(s, ID)).status, 404);
  const early = await checkout(s, ticket, phone, [{ productId: ID, qtyMilli: 1000 }]);
  assert.equal(early.status, 409, JSON.stringify(early.body));
  assert.equal(early.body.error.code, 'product_unavailable');
  assert.equal(await orders(s.shopId), 0);

  //  ۶) ⛔ «مجوز»: کارمندِ بی `settings.write` منتشر نمی‌کند
  const clerk = await staff(s, 'فروشنده');
  const denied = await publish(s, ID, true, clerk.token);
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  assert.ok(!(await pub.list(s)).some((p) => p.id === ID), 'ردِ کارمند اثری ندارد');

  //  ۷) مالک منتشر می‌کند ⇒ ویترین با همان شناسه، نام، قیمت و موجودیِ همان کاتالوگ
  const ok = await publish(s, ID, true);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.productId, ID);
  const card = (await pub.list(s)).find((p) => p.id === ID);
  assert.ok(card, 'پس از انتشار در فهرستِ ویترین است');
  assert.deepEqual([card.name, card.priceMinor, card.stock.status], ['چای سبز', 8000, 'in_stock']);
  const page = await pub.detail(s, ID);
  assert.equal(page.status, 200, JSON.stringify(page.body));
  assert.equal(page.body.product.id, ID);
  assert.equal(page.body.product.stock.status, 'in_stock', 'صفحهٔ کالا همان وضعیتِ کاتالوگ');

  //  ۸) سفارشِ مشتری قلمی با همان شناسه می‌سازد که دکان در صفِ سفارش می‌بیند
  const placed = await checkout(s, ticket, phone, [{ productId: ID, qtyMilli: 2000 }]);
  assert.equal(placed.status, 201, JSON.stringify(placed.body));
  assert.deepEqual(placed.body.order.lines.map((l) => l.productId), [ID], 'رسیدِ مشتری');
  const row = await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, placed.body.order.orderNo]);
  const desk = await h.get(`/api/shop/orders/${row.id}`, { token: s.token });
  assert.equal(desk.status, 200, JSON.stringify(desk.body));
  assert.deepEqual(desk.body.order.lines.map((l) => l.productId), [ID], 'صفِ سفارشِ دکان');
  //  سفارش موجودی را نه رزرو می‌کند نه کم (تصمیمِ صاحبِ مخزن، catalog-stock.test.js)
  const after = await h.get(`/api/shop/catalog/products/${ID}`, { token: s.token });
  assert.equal(after.body.product.stock.sellableMilli, 8000);

  //  ۹) برداشتنِ انتشار ⇒ دوباره بیرون از ویترین
  assert.equal((await publish(s, ID, false)).status, 200);
  assert.ok(!(await pub.list(s)).some((p) => p.id === ID));
  assert.equal((await pub.detail(s, ID)).status, 404);
});

test('INV-02@32: قفسه صفر و انبار ۵ ⇒ ویترین «در انبار، آماده‌سازی لازم» می‌گوید نه «تمام شده»؛ سفارش با همان عدد', async () => {
  const s = await liveShop('انبار');
  const ID = 'flour-1';
  await push(s, [product(ID, { name: 'آرد' }), entry(`${ID}-e1`, ID, 5)]);
  assert.equal((await publish(s, ID, true)).status, 200);
  const t = await mv(s, { kind: 'transfer', productId: ID, from: 'shop_floor', to: 'warehouse', qtyMilli: 5000 });
  assert.equal(t.status, 201, JSON.stringify(t.body));

  //  سیاست: زمانِ آماده‌سازی ۴۵ دقیقه و نمایشِ عدد
  assert.equal((await stockSettings(s, { prepMinutes: 45, showQty: 'exact' })).status, 200);
  const card = (await pub.list(s)).find((p) => p.id === ID);
  assert.deepEqual(card.stock, {
    status: 'in_warehouse', checkedAt: card.stock.checkedAt, unit: 'دانه', prepMinutes: 45,
    sellableMilli: 5000, readyMilli: 0, laterMilli: 5000,
  });
  assert.ok(card.stock.checkedAt > 0, 'زمانِ آخرین دیده‌شدنِ دستگاه');
  const page = await pub.detail(s, ID);
  assert.equal(page.status, 200, JSON.stringify(page.body));
  assert.deepEqual(page.body.product.stock, card.stock, 'صفحهٔ کالا همان فهرست');

  //  سیاست: عدد پنهان ⇒ وضعیت همان «در انبار» و هیچ عددی بیرون نمی‌رود
  assert.equal((await stockSettings(s, { showQty: 'hidden' })).status, 200);
  const hidden = (await pub.list(s)).find((p) => p.id === ID).stock;
  assert.equal(hidden.status, 'in_warehouse');
  assert.equal(hidden.prepMinutes, 45);
  for (const k of ['sellableMilli', 'readyMilli', 'laterMilli']) assert.equal(hidden[k], undefined, `${k} پنهان است`);
  assert.equal((await pub.detail(s, ID)).body.product.stock.status, 'in_warehouse');

  //  ثبتِ سفارش با همان عدد سازگار است: ۲ از ۵ پذیرفته، ۶ رد با مقدارِ قابلِ سفارش
  const phone = '0791110002';
  const ticket = await ticketFor(s.slug, phone);
  const two = await checkout(s, ticket, phone, [{ productId: ID, qtyMilli: 2000 }]);
  assert.equal(two.status, 201, JSON.stringify(two.body));
  const six = await checkout(s, ticket, phone, [{ productId: ID, qtyMilli: 6000 }]);
  assert.equal(six.status, 409, JSON.stringify(six.body));
  assert.equal(six.body.error.code, 'out_of_stock');
  assert.equal(six.body.error.lines[0].availableMilli, 5000);
  assert.equal(await orders(s.shopId), 1, 'سفارشِ ردشده ردیفی نمی‌سازد');

  //  کالا به قفسه برگشت ⇒ همین ویترین بی‌درنگ «موجود»؛ «در انبار» وضعیتِ همان لحظه بود
  const back = await mv(s, { kind: 'transfer', productId: ID, from: 'warehouse', to: 'shop_floor', qtyMilli: 5000 });
  assert.equal(back.status, 201, JSON.stringify(back.body));
  assert.equal((await pub.list(s)).find((p) => p.id === ID).stock.status, 'in_stock');
});
