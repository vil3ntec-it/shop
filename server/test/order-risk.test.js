'use strict';
/**
 * ریسکِ سفارشِ ویترین — RSK-02، RSK-05 و RSK-03 (`lib/order-risk.js`، پرچمِ `shop.order_risk`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچم خاموش ⇒ رفتارِ پیشین: سفارشِ تکراری و سبدِ بزرگِ مشتریِ تازه پذیرفته‌اند.
 *   ۲) RSK-05: همان شماره (‎+930…‎ هم)، همان اقلام و مقدار و همان جا در پنجره ⇒ ۴۰۹ `duplicate_order` با
 *      شمارهٔ سفارشِ پیشین، توکنِ پیگیری و Retry-After، بی ردیف؛ مقدارِ دیگر، نشانیِ دیگر، سفارشِ لغوشده یا
 *      پسِ پنجره ⇒ آزاد. دو ثبتِ هم‌زمان ⇒ دقیقاً یکی.
 *   ۳) RSK-02: مشتریِ تازهٔ همین دکان ⇒ سقفِ مبلغ و سقفِ سفارشِ باز با `new_customer_limit` و دلیل؛ پس از
 *      نخستین تحویل ⇒ آزاد؛ سیاست از «تنظیماتِ زنده»، عددِ نامعتبر ⇒ پیش‌فرض.
 *   ۴) ⛔ فروشِ حضوری و سفارشِ دستیِ کارمند هرگز با این قاعده‌ها رد نمی‌شوند.
 *   ۵) RSK-03: سابقه و نشانه‌های توضیح‌دار برای دکان، بی شماره؛ دکانِ دیگر ۴۰۴؛ پرچم خاموش ⇒ enabled:false.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/order-risk');

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
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-risk2' },
  body: { device_id: 'dev-risk2', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و دو کالای منتشرشده (برنج ۱۲۰٫۵، روغن ۳۰۰)؛ `risk` ⇒ پرچمِ ریسک روشن. */
async function liveStore(label, { risk = true, policy } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `r-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (risk) await setFlag(o.shopId, R.FLAG_KEY, true);
  if (policy) await setFlag(o.shopId, R.POLICY_KEY, policy);
  const put = await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  return o;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

function cart({ phone = '0791110001', qty = 2000, fulfillment = 'delivery', address, lines } = {}) {
  return {
    fulfillment,
    customer: { name: 'مشتری', phone },
    lines: lines || [{ productId: 'rice', qtyMilli: qty }, { productId: 'oil', qtyMilli: 1000 }],
    ...(fulfillment === 'delivery' ? { address: address || { lat: 34.531234, lng: 69.171234, text: 'کارتهٔ ۴، کوچهٔ دوم' } } : {}),
  };
}
async function checkout(o, body, { key = `rk-${uniq()}-key` } = {}) {
  const ticket = await ticketFor(o.slug, body.customer.phone);
  return h.post(`/api/store/${o.slug}/checkout`, body, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': ticket } });
}
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;
const STAFF = (o) => ({ type: 'user', id: o.user.id, role: 'owner' });
const orders = require('../src/lib/orders');
async function deliver(o, orderId) {
  for (const to of ['accepted', 'picking', 'packed', 'out_for_delivery', 'delivered']) {
    await orders.transition(o.shopId, orderId, { to }, STAFF(o));
  }
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچم خاموش: سفارشِ تکراری و سبدِ بزرگ از مشتریِ تازه همان رفتارِ پیشین — پذیرفته', async () => {
  const o = await liveStore('خاموش', { risk: false, policy: { newCustomerMaxTotalMinor: 1000 } });
  assert.equal((await checkout(o, cart())).status, 201);
  assert.equal((await checkout(o, cart())).status, 201, 'تکراری، ولی پرچم خاموش');
  assert.equal((await checkout(o, cart({ qty: 50_000 }))).status, 201, 'سبدِ بزرگ، ولی پرچم خاموش');
  assert.equal(await count(o.shopId), 3);
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 LIMIT 1', [o.shopId])).id;
  const r = await h.get(`/api/shop/orders/${id}/risk`, { token: o.token });
  assert.equal(r.status, 200);
  assert.deepEqual(r.body, { enabled: false });
});

/* ══════════════════════════════════════════════════════════════════
   ۲) RSK-05: سفارشِ تکراری
   ══════════════════════════════════════════════════════════════════ */

test('RSK-05: همان شماره، اقلام و جا در ۱۰ دقیقه ⇒ ۴۰۹ duplicate_order با شمارهٔ پیشین، توکن و Retry-After؛ بی ردیف', async () => {
  const o = await liveStore('تکراری');
  const first = await checkout(o, cart());
  assert.equal(first.status, 201, JSON.stringify(first.body));
  //  ترتیبِ دیگرِ اقلام و فاصلهٔ دیگرِ متن همان سفارش است
  const again = await checkout(o, cart({
    lines: [{ productId: 'oil', qtyMilli: 1000 }, { productId: 'rice', qtyMilli: 2000 }],
    address: { lat: 34.53124, lng: 69.17124, text: 'کارتهٔ ۴ ، کوچهٔ  دوم' },
  }));
  assert.equal(again.status, 409, JSON.stringify(again.body));
  const e = again.body.error;
  assert.equal(e.code, 'duplicate_order');
  assert.equal(e.reason, 'same_items_same_place');
  assert.equal(e.orderNo, first.body.order.orderNo);
  assert.equal(e.token, first.body.order.token, 'مشتری به همان سفارش برمی‌گردد');
  assert.equal(e.windowMin, 10);
  assert.ok(Number(again.headers.get('retry-after')) > 500 && Number(again.headers.get('retry-after')) <= 600);
  assert.match(e.message, /همین سفارش/);
  assert.equal(await count(o.shopId), 1);

  //  ‎+930…‎ همان شماره است
  const alias = await checkout(o, cart({ phone: '+930791110001' }));
  assert.equal(alias.status, 409, JSON.stringify(alias.body));
  //  تکرارِ همان کلیدِ یکتایی هنوز همان سفارش را برمی‌گرداند (پیش از قاعده)
  const key = `rk-${uniq()}-key`;
  const o2 = await liveStore('تکراری کلید');
  const a = await checkout(o2, cart(), { key });
  const b = await checkout(o2, cart(), { key });
  assert.equal(a.status, 201);
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal(b.body.replayed, true);
});

test('RSK-05: مقدارِ دیگر، نشانیِ دیگر، دریافت از دکان، سفارشِ لغوشده یا پسِ پنجره ⇒ تکراری نیست', async () => {
  //  سقفِ سفارشِ باز خاموش تا فقط قاعدهٔ تکرار سنجیده شود
  const o = await liveStore('نه‌تکراری', { policy: { newCustomerMaxOpen: 0 } });
  const first = await checkout(o, cart());
  assert.equal(first.status, 201);
  assert.equal((await checkout(o, cart({ qty: 3000 }))).status, 201, 'مقدارِ دیگر');
  assert.equal((await checkout(o, cart({ address: { lat: 34.6, lng: 69.2, text: 'جای دیگر' } }))).status, 201, 'نشانیِ دیگر');
  const pickup = await checkout(o, cart({ fulfillment: 'pickup' }));
  assert.equal(pickup.status, 201, 'دریافت از دکان');

  //  لغوشده دیگر حساب نمی‌شود
  const id = first.body.order.token.split('.')[0];
  await orders.transition(o.shopId, id, { to: 'rejected', reason: 'busy' }, STAFF(o));
  const p2 = await liveStore('پنجره', { policy: { duplicateWindowMin: 5, newCustomerMaxOpen: 0 } });
  const x = await checkout(p2, cart());
  assert.equal(x.status, 201);
  assert.equal((await checkout(p2, cart())).status, 409);
  //  پسِ پنجره (۵ دقیقه + یک ثانیه پیش)
  await query('UPDATE online_orders SET created_at = created_at - $2 WHERE shop_id=$1', [p2.shopId, 5 * 60_000 + 1000]);
  assert.equal((await checkout(p2, cart())).status, 201, 'پسِ پنجره');
  //  و در دکانِ نخست: پس از ردِ سفارشِ اول، همان سبد دوباره پذیرفته است
  assert.equal((await checkout(o, cart())).status, 201, 'سفارشِ ردشده تکراری نمی‌سازد');
});

test('RSK-05: دو ثبتِ هم‌زمانِ همان سبد با کلیدهای گوناگون ⇒ دقیقاً یک سفارش (قفلِ شماره)', async () => {
  const o = await liveStore('هم‌زمان');
  await ticketFor(o.slug, '0791110001');
  const rs = await Promise.all(Array.from({ length: 6 }, () => checkout(o, cart())));
  const codes = rs.map((r) => r.status).sort();
  assert.deepEqual(codes, [201, 409, 409, 409, 409, 409], JSON.stringify(rs.map((r) => r.body.error?.code || 'ok')));
  assert.equal(await count(o.shopId), 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) RSK-02: مشتریِ تازه
   ══════════════════════════════════════════════════════════════════ */

test('RSK-02: سبدِ بالای سقفِ مشتریِ تازه ⇒ new_customer_limit (amount)؛ زیرِ سقف آزاد؛ پس از نخستین تحویل آزاد', async () => {
  //  سقفِ ۵۰۰ افغانی؛ سبدِ پیش‌فرض = ۲ کیلو برنج (۲۴۱) + روغن (۳۰۰) = ۵۴۱
  const o = await liveStore('مبلغ', { policy: { newCustomerMaxTotalMinor: 50_000 } });
  const big = await checkout(o, cart());
  assert.equal(big.status, 409, JSON.stringify(big.body));
  assert.equal(big.body.error.code, 'new_customer_limit');
  assert.equal(big.body.error.reason, 'amount');
  assert.equal(big.body.error.maxTotalMinor, 50_000);
  assert.equal(await count(o.shopId), 0);

  const small = await checkout(o, cart({ lines: [{ productId: 'rice', qtyMilli: 2000 }] }));
  assert.equal(small.status, 201, JSON.stringify(small.body));
  await deliver(o, small.body.order.token.split('.')[0]);
  assert.equal((await checkout(o, cart())).status, 201, 'پس از تحویل، دیگر تازه نیست');
});

test('RSK-02: سفارشِ بازِ دومِ مشتریِ تازه ⇒ new_customer_limit (open_orders) با سقفِ ۱؛ سیاستِ نامعتبر ⇒ پیش‌فرض ۲', async () => {
  const o = await liveStore('باز', { policy: { newCustomerMaxOpen: 1 } });
  assert.equal((await checkout(o, cart())).status, 201);
  const second = await checkout(o, cart({ qty: 3000 }));
  assert.equal(second.status, 409, JSON.stringify(second.body));
  assert.deepEqual([second.body.error.code, second.body.error.reason, second.body.error.maxOpen, second.body.error.open],
    ['new_customer_limit', 'open_orders', 1, 1]);
  //  شمارهٔ دیگر همان دم آزاد است
  assert.equal((await checkout(o, cart({ phone: '0791110002' }))).status, 201);

  //  سیاستِ خراب (منفی، رشته، اعشار) ⇒ پیش‌فرض‌ها
  await setFlag(o.shopId, R.POLICY_KEY, { newCustomerMaxOpen: -1, newCustomerMaxTotalMinor: '9', duplicateWindowMin: 1.5 });
  const pol = await R.policyIn((t, p) => query(t, p), o.shopId);
  assert.deepEqual(pol, R.DEFAULTS);
  assert.equal((await checkout(o, cart({ qty: 3000 }))).status, 201, 'سقفِ پیش‌فرضِ ۲');
  const third = await checkout(o, cart({ qty: 4000 }));
  assert.equal(third.status, 409);
  assert.equal(third.body.error.maxOpen, 2);
});

test('⛔ سفارشِ دستیِ کارمند (تلفنی) و فروشِ صندوق هرگز با قاعدهٔ ریسک رد نمی‌شوند', async () => {
  const o = await liveStore('حضوری', { policy: { newCustomerMaxTotalMinor: 100, newCustomerMaxOpen: 1 } });
  const body = () => ({ source: 'phone', fulfillment: 'pickup', customer: { name: 'م', phone: '0791110003' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 900_000 }] });
  for (let i = 0; i < 3; i++) {
    const r = await h.post('/api/shop/orders', body(), { token: o.token, headers: { 'Idempotency-Key': `st-${uniq()}-key` } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
  }
  //  فروشِ صندوق از راهِ همگام‌سازی (ردیفِ فروش) — دستِ این قاعده نیست
  const sale = await push(o.token, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'sales', row_id: `sale-${uniq()}`, type: 'insert',
    fields: { total: 9_000_000, customerPhone: '0791110003', items: [] } }]);
  assert.equal(sale.status, 200, JSON.stringify(sale.body));
});

/* ══════════════════════════════════════════════════════════════════
   ۴) RSK-03: سابقه پیش از ارسال
   ══════════════════════════════════════════════════════════════════ */

test('RSK-03: سابقهٔ همین دکان با نشانه‌های توضیح‌دار، بی شماره؛ دکانِ دیگر ۴۰۴؛ سابقهٔ دکانِ دیگر شمرده نمی‌شود', async () => {
  const o = await liveStore('سابقه', { policy: { newCustomerMaxOpen: 0, newCustomerMaxTotalMinor: 0, duplicateWindowMin: 0 } });
  const phone = '0791110004';
  const ids = [];
  for (let i = 0; i < 5; i++) {
    const r = await checkout(o, cart({ phone, qty: 1000 * (i + 1) }));
    assert.equal(r.status, 201, JSON.stringify(r.body));
    ids.push(r.body.order.token.split('.')[0]);
  }
  await deliver(o, ids[0]);
  for (const to of ['accepted', 'picking', 'packed', 'out_for_delivery']) await orders.transition(o.shopId, ids[1], { to }, STAFF(o));
  await orders.transition(o.shopId, ids[1], { to: 'delivery_failed', reason: 'no_answer' }, STAFF(o));
  await orders.transition(o.shopId, ids[1], { to: 'retry_scheduled' }, STAFF(o));
  await orders.transition(o.shopId, ids[1], { to: 'returned_to_store' }, STAFF(o));
  await orders.transition(o.shopId, ids[2], { to: 'rejected', reason: 'out_of_area' }, STAFF(o));
  //  دکانِ دیگر با همان شماره: سابقه‌اش این‌جا شمرده نمی‌شود
  const other = await liveStore('سابقه دیگر', { policy: { newCustomerMaxOpen: 0, newCustomerMaxTotalMinor: 0 } });
  const elsewhere = await checkout(other, cart({ phone }));
  assert.equal(elsewhere.status, 201);

  const r = await h.get(`/api/shop/orders/${ids[4]}/risk`, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.enabled, true);
  assert.equal(r.body.known, true);
  assert.deepEqual({ ...r.body.history, firstOrderAt: 0, lastOrderAt: 0 }, {
    orders: 4, completed: 1, open: 1, rejectedByShop: 1, cancelledByCustomer: 0, cancelledByShop: 0, noShow: 0,
    deliveryFailed: 1, returnedToShop: 1, returnsOrRefunds: 0, firstOrderAt: 0, lastOrderAt: 0,
  });
  const codes = r.body.flags.map((f) => f.code).sort();
  assert.deepEqual(codes, ['delivery_failed_before', 'returned_to_shop_before']);
  for (const f of r.body.flags) assert.ok(['info', 'warn'].includes(f.severity) && Number.isInteger(f.count));
  const text = JSON.stringify(r.body);
  assert.ok(!text.includes('791110004'), 'بی شماره');

  //  مشتریِ تازه
  const fresh = await h.get(`/api/shop/orders/${elsewhere.body.order.token.split('.')[0]}/risk`, { token: other.token });
  assert.deepEqual(fresh.body.flags.map((f) => f.code), ['new_customer']);
  assert.equal(fresh.body.history.orders, 0);
  //  دکانِ دیگر سفارشِ این دکان را نمی‌بیند
  assert.equal((await h.get(`/api/shop/orders/${ids[4]}/risk`, { token: other.token })).status, 404);
});

test('RSK-03: جفتِ تکراری که پیش از روشن شدنِ پرچم ساخته شد، نشانهٔ possible_duplicate می‌گیرد', async () => {
  const o = await liveStore('جفت', { risk: false });
  const a = await checkout(o, cart({ phone: '0791110005' }));
  const b = await checkout(o, cart({ phone: '0791110005' }));
  assert.equal(a.status, 201);
  assert.equal(b.status, 201);
  await setFlag(o.shopId, R.FLAG_KEY, true);
  const r = await h.get(`/api/shop/orders/${b.body.order.token.split('.')[0]}/risk`, { token: o.token });
  const dup = r.body.flags.find((f) => f.code === 'possible_duplicate');
  assert.ok(dup, JSON.stringify(r.body.flags));
  assert.equal(dup.orderNo, a.body.order.orderNo);
  assert.ok(r.body.flags.some((f) => f.code === 'many_open') === false, 'دو سفارشِ باز = یکی جز خودش');
});
