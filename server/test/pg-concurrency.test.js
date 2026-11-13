'use strict';
/**
 * رقابت‌هایی که فقط روی PostgreSQL ِ واقعی دیده می‌شوند — PGlite یک اتصال دارد و هر تراکنش
 * همهٔ پرسش‌های دیگر را پشتِ خودش نگه می‌دارد، پس همین سنجه‌ها آن‌جا همیشه سبزند.
 * (همان خانوادهٔ `catalog.refresh` در `catalog-stock.test.js`: خواندنِ هم‌زمانِ ویترین از #1010.)
 *
 *   ۱) `catalog-products.refreshProducts` (S1: ویترین از کاتالوگِ واحد) — درخواستی که در پنجرهٔ
 *      [REFRESH_THROTTLE_MS] می‌رسد منتظرِ تازه‌کردنِ در جریانِ همان دکان می‌ماند، نه قیمتِ کهنه.
 *   ۲) `market.reindex` — دو نمونهٔ سرور (یا برشِ باطل‌شده و برشِ تازه) نمایهٔ جست‌وجوی همان دکان را
 *      با هم از نو می‌نویسند ⇒ نه `23505` (کلیدِ `(shop_id, product_id)`) و نه بازارِ ۵۰۰.
 *   ۳) ترتیبِ قفل «سفارش ⇐ مأموریت» — رویدادِ پیک (`delivery.applyEvent`) مأموریت را پیش از سفارش قفل
 *      می‌کرد و پرداختِ آنلاین (`payment-intents` ⇐ `delivery-due.syncIn`) سفارش را پیش از مأموریت ⇒
 *      «تحویل شد» و پرداختِ هم‌زمانِ همان سفارش بن‌بست (`40P01`) می‌شدند و یکی ۵۰۰ می‌گرفت.
 *
 * ⛔ پنجرهٔ تازه‌کردن عمداً روشن (پیش‌فرضِ ۵۰۰ms) — بی آن رقابتِ ۱ پیش نمی‌آید.
 */
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-pg-concurrency';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
delete process.env.CATALOG_REFRESH_THROTTLE_MS;
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const stock = require('../src/lib/stock');
const catalog = require('../src/lib/catalog');
const orders = require('../src/lib/orders');
const P = require('../src/lib/payment-providers');

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

const push = (o, ops) => h.api('POST', '/api/sync/v1/push', {
  token: o.token, headers: { 'X-App': 'shop', 'X-Device': o.device },
  body: { device_id: o.device, schema_version: 2, ops },
});
const op = (rowId, fields, type = 'insert') => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
});

/** دکانِ روشن با ویترین و فهرستِ بازار؛ `products` = [شناسه, نام, قیمت] — همه منتشرشده */
async function liveStore(label, products) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, device: `dev-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  o.slug = `pc-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o, products.map(([id, name, price]) => op(id, { name, unit: 'دانه', salePrice: price, category: 'خوراکی' })));
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id] of products) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const m = await h.put('/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, { token: o.token });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  return o;
}

const pause = (ms) => new Promise((r) => setTimeout(r, ms));

/* ══════════════════════════════════════════════════════════════════ */

test('S1: سه خواندنِ هم‌زمانِ ویترین پس از تغییرِ قیمت در دستگاه همه قیمتِ تازه را می‌بینند (منتظرِ همان تازه‌کردن، نه کاتالوگِ کهنه)', async () => {
  await setFlag('all', 'storefront.catalog_source', true);
  try {
    const o = await liveStore('هم‌زمان', [['rice', 'برنج', 120], ['oil', 'روغن', 300]]);
    const prices = async () => {
      const r = await h.get(`/api/store/${o.slug}/products`);
      assert.equal(r.status, 200, JSON.stringify(r.body));
      return Object.fromEntries(r.body.items.map((p) => [p.id, p.priceMinor]));
    };
    assert.deepEqual(await prices(), { oil: 30000, rice: 12000 });
    //  دستگاه قیمتِ برنج را عوض کرد
    const p = await push(o, [op('rice', { name: 'برنج', unit: 'دانه', salePrice: 150, category: 'خوراکی' }, 'update')]);
    assert.equal(p.status, 200, JSON.stringify(p.body));
    await pause(catalog.REFRESH_THROTTLE_MS + 100);   //  پنجرهٔ پیشینِ همین دکان بسته شود
    //  تازه‌کردنِ نخست کُند (دیتابیسِ کُندِ CI): قفلِ درونِ تراکنشِ `refreshProducts` دیر برمی‌گردد
    const orig = stock.lock;
    stock.lock = async (c, shopId) => {
      const out = await orig(c, shopId);
      if (shopId === o.shopId) await pause(300);
      return out;
    };
    try {
      const all = await Promise.all([1, 2, 3].map(() => prices()));
      assert.deepEqual(all.map((x) => x.rice), [15000, 15000, 15000]);
    } finally {
      stock.lock = orig;
    }
  } finally {
    await setFlag('all', 'storefront.catalog_source', false);
  }
});

test('بازار: دو نمونهٔ سرور نمایهٔ جست‌وجوی همان دکان را با هم از نو می‌نویسند — نه کلیدِ تکراری (23505) و نه ردیفِ دوتایی', async () => {
  await setFlag('all', 'market.directory', true);
  try {
    const o = await liveStore('نمایه', [['rice', 'برنج', 120], ['oil', 'روغن', 300], ['tea', 'چای', 90]]);
    //  نمونهٔ دوم = همان پرونده با حافظهٔ جدا (برشِ یک‌پرواز فقط درونِ یک نمونه است)
    const file = require.resolve('../src/lib/market');
    const a = require(file);
    delete require.cache[file];
    const b = require(file);
    assert.notEqual(a, b);
    await a.eligible();   //  نمایهٔ نخست
    for (let i = 0; i < 6; i++) {
      //  امضای دکان کهنه شد (کالا عوض شد / نمونهٔ تازه بالا آمد) ⇒ هر دو نمونه از نو می‌نویسند
      await query('DELETE FROM market_search_shops WHERE shop_id=$1', [o.shopId]);
      const out = await Promise.allSettled([a.eligible(), b.eligible()]);
      const bad = out.filter((x) => x.status === 'rejected').map((x) => `${x.reason && x.reason.code} ${x.reason && x.reason.message}`);
      assert.deepEqual(bad, [], `دور ${i}`);
      const n = await one('SELECT COUNT(*)::int AS n FROM market_search WHERE shop_id=$1', [o.shopId]);
      assert.equal(n.n, 3, `دور ${i}`);
    }
  } finally {
    await setFlag('all', 'market.directory', false);
  }
});

/** پیک با «ورود با کدِ شاگرد» (توکنِ دامنه‌دارِ پیک) — همان راهِ برنامهٔ گوشی */
async function courier(o, name) {
  const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: o.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const r = await h.post('/api/auth/staff', { code: inv.body.code, name, device: { uid: `phone-${uniq()}`, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const me = await h.get('/api/courier/me', { token: r.body.accessToken });
  assert.equal(me.status, 200, JSON.stringify(me.body));
  return { token: r.body.accessToken, id: me.body.courier.id };
}
let evN = 0;
const evt = (id, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evN}`, assignmentId: id, type, deviceTime: Date.now(), ...extra });
const says = (c, events) => h.post('/api/courier/events', { deviceId: 'pg-concurrency-1', events }, { token: c.token });

/** سفارشِ تلفنیِ نقدی (۱٬۲۴۰ افغانی) سپرده به پیک و «گرفته‌شده»، با قصدِ حساب‌پی ِ همان جمع و بدنهٔ وب‌هوکِ «پرداخت شد» */
async function departedWithIntent(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ بن‌بست' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.delivery', 'pay.hesabpay']) await setFlag(o.shopId, k, true);
  const g = await h.post('/api/admin/subscriptions', { shopId: o.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  const c = await courier(o, 'کریم');
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ name: 'برنج', qtyMilli: 2000, unitPriceMinor: 60_000 }], deliveryFeeMinor: 4_000,
  }, { token: o.token, headers: { 'Idempotency-Key': `order-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const order = r.body.order;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${order.id}/transition`, { to }, { token: o.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'shop_courier', courierId: c.id }, { token: o.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  const went = await says(c, [evt(id, 'assigned'), evt(id, 'picked_up')]);
  assert.equal(went.status, 200, JSON.stringify(went.body));
  for (const x of went.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  const it = await h.post('/api/shop/payments/intents', { kind: 'online_order', refId: order.id, amount: 124_000, currency: 'AFN' },
    { token: o.token, headers: { 'Idempotency-Key': `intent-${uniq()}` } });
  assert.equal(it.status, 201, JSON.stringify(it.body));
  const buf = Buffer.from(JSON.stringify({ eventId: `wh-${uniq()}`, sessionId: P.fake.sessionOf(it.body.id), status: 'paid', amountMinor: 124_000, currency: 'AFN', providerRef: `HP-${uniq()}`, settle: true }));
  const webhook = () => h.raw('POST', '/api/payments/webhook/fake', buf, { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(buf) } });
  return { o, c, order, id, webhook };
}

test('ترتیبِ قفل: «تحویل شد» ِ پیک و پرداختِ آنلاینِ همان سفارش در یک لحظه — هر دو می‌نشینند، نه بن‌بست (40P01)', async () => {
  const { o, c, order, id, webhook } = await departedWithIntent('بن‌بست');
  //  تراکنشِ پیک کُند (دیتابیسِ کُندِ CI): پس از قفل‌های آغازینش، پیش از گذارِ سفارش
  const orig = orders.transitionIn;
  orders.transitionIn = async (cl, shopId, ...rest) => {
    if (shopId === o.shopId) await pause(500);
    return orig(cl, shopId, ...rest);
  };
  try {
    const done = says(c, [evt(id, 'delivered', { cashCollectedMinor: 124_000 })]);
    await pause(150);   //  پیک قفل‌هایش را گرفته است
    const [d, w] = await Promise.all([done, webhook()]);
    assert.equal(d.status, 200, JSON.stringify(d.body));
    assert.equal(d.body.results[0].status, 'applied', JSON.stringify(d.body.results[0]));
    assert.equal(w.status, 200, JSON.stringify(w.body));
  } finally {
    orders.transitionIn = orig;
  }
  //  پیک پیش‌تر نشست ⇒ سفارش تحویل‌شده با نقدِ گرفته‌شده؛ پرداختِ دوم «پرداخت‌شده» نمی‌سازد
  const row = await one('SELECT status, cod_status FROM online_orders WHERE id=$1', [order.id]);
  assert.equal(row.status, 'delivered');
});

test('ترتیبِ قفل: اصلاحِ نشانیِ مأموریت و پرداختِ آنلاینِ همان سفارش در یک لحظه — هر دو می‌نشینند، نه بن‌بست (40P01)', async () => {
  const { o, order, id, webhook } = await departedWithIntent('نشانی');
  //  این بار پرداخت کُند: سفارش را گرفته و پیش از نوشتنِ «نقدِ موردِ انتظار» ِ مأموریت منتظر است
  const due = require('../src/lib/delivery-due');
  const orig = due.syncIn;
  due.syncIn = async (cl, shopId, ...rest) => {
    if (shopId === o.shopId) await pause(500);
    return orig(cl, shopId, ...rest);
  };
  try {
    const paid = webhook();
    await pause(150);   //  پرداخت قفلِ سفارش را گرفته است
    const fixed = h.post(`/api/shop/delivery/assignments/${id}/address`, { address: { landmark: 'کنارِ نانوایی', text: 'کوچهٔ دوم' } },
      { token: o.token, headers: { 'Idempotency-Key': `fix-${uniq()}` } });
    const [w, f] = await Promise.all([paid, fixed]);
    assert.equal(w.status, 200, JSON.stringify(w.body));
    assert.equal(f.status, 200, JSON.stringify(f.body));
  } finally {
    due.syncIn = orig;
  }
  const row = await one('SELECT payment_status, address FROM online_orders WHERE id=$1', [order.id]);
  assert.equal(row.payment_status, 'paid');
  assert.equal(row.address.landmark, 'کنارِ نانوایی');
  assert.equal(Number((await one('SELECT expected_collect_minor FROM delivery_assignments WHERE id=$1', [id])).expected_collect_minor), 0);
});

test('سفارش (ردِ گمان): ثبت‌ها و گذارهای هم‌زمانِ یک دکان ⇒ شمارهٔ سفارش و شمارهٔ رویداد بی تکرار، همان کلید یک سفارش', async () => {
  const u = await h.newUser('شماره', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ شماره' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.delivery']) await setFlag(o.shopId, k, true);
  const body = (i) => ({
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: `مشتری ${i}`, phone: '0791234567' }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 60_000 }], deliveryFeeMinor: 0,
  });
  const create = (i, k = `order-${uniq()}`) => h.post('/api/shop/orders', body(i), { token: o.token, headers: { 'Idempotency-Key': k } });
  const same = `order-same-${uniq()}`;
  const rs = await Promise.all([...[1, 2, 3, 4, 5, 6, 7, 8].map((i) => create(i)), create(9, same), create(9, same)]);
  for (const r of rs) assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
  const nos = (await query('SELECT order_no FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [o.shopId])).rows.map((x) => Number(x.order_no));
  assert.deepEqual(nos, [1, 2, 3, 4, 5, 6, 7, 8, 9]);
  assert.equal(rs[8].body.order.id, rs[9].body.order.id);

  const id = rs[0].body.order.id;
  const moves = await Promise.all([1, 2, 3, 4, 5].map(() => h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: o.token })));
  for (const r of moves) assert.ok([200, 409].includes(r.status), JSON.stringify(r.body));
  const evs = (await query('SELECT seq, kind, to_status FROM online_order_events WHERE order_id=$1 ORDER BY seq', [id])).rows;
  assert.deepEqual(evs.map((e) => Number(e.seq)), evs.map((_, i) => i + 1));
  assert.equal(evs.filter((e) => e.to_status === 'accepted').length, 1);
});
