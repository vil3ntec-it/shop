'use strict';
/**
 * فهرستِ سیاه و سفیدِ سفارشِ ویترین — RSK-04 (`lib/risk-lists.js`، مهاجرتِ ۱۴۶، پرچمِ `shop.order_risk`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچم خاموش ⇒ درها ۴۰۳ `order_risk_disabled` و ردیفِ موجود هیچ اثری بر ثبت ندارد.
 *   ۲) هر ردیف دلیل (۳ تا ۲۰۰) و انقضا (۱ تا ۳۶۵ روز) دارد؛ پین فقط «بسته»؛ ورودیِ ناجور ⇒ ۴۰۰.
 *   ۳) شمارهٔ بسته (‎+930…‎ هم) و پینِ درونِ شعاع ⇒ ۴۰۳ `order_blocked` با پیامِ خنثی و بی ردیف؛ بیرونِ
 *      شعاع، دریافت از دکان، شمارهٔ دیگر، ردیفِ برداشته یا گذشته ⇒ آزاد.
 *   ۴) شمارهٔ مطمئن از سقفِ مشتریِ تازه رد نمی‌شود؛ قاعدهٔ تکرار همچنان هست.
 *   ۵) یک شماره فقط در یک فهرست؛ فقط صاحب و مدیر؛ دکانِ دیگر نه می‌بیند نه برمی‌دارد؛ سابقهٔ عملیات بی
 *      شماره و دلیل؛ نشانهٔ `blocklisted`/`allowlisted` در نمای ریسکِ سفارش.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
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

const PIN = { lat: 34.531234, lng: 69.171234 };

async function liveStore(label, { risk = true, policy = { newCustomerMaxOpen: 0, duplicateWindowMin: 0 } } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `l-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (risk) await setFlag(o.shopId, R.FLAG_KEY, true);
  if (policy) await setFlag(o.shopId, R.POLICY_KEY, policy);
  assert.equal((await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-lists' },
    body: { device_id: 'dev-lists', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products',
      row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return o;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
function cart({ phone = '0791110010', qty = 2000, pickup = false, pin = PIN } = {}) {
  return {
    fulfillment: pickup ? 'pickup' : 'delivery',
    customer: { name: 'مشتری', phone },
    lines: [{ productId: 'rice', qtyMilli: qty }],
    ...(pickup ? {} : { address: { ...pin, text: 'کوچهٔ سوم' } }),
  };
}
async function checkout(o, body) {
  const ticket = await ticketFor(o.slug, body.customer.phone);
  return h.post(`/api/store/${o.slug}/checkout`, body,
    { headers: { 'Idempotency-Key': `ls-${uniq()}-key`, 'X-Store-Ticket': ticket } });
}
const addEntry = (o, body, token = o.token) => h.post('/api/shop/orders/risk/lists', body, { token });
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;

/** نقطه‌ای `m` متر شمال‌ترِ PIN (هر درجهٔ عرض ≈ ۱۱۱٬۱۹۵ متر). */
const north = (m) => ({ lat: PIN.lat + m / 111_195, lng: PIN.lng });

test('⛔ پرچم خاموش: درهای فهرست ۴۰۳ و ردیفِ موجود بر ثبت بی‌اثر', async () => {
  const o = await liveStore('خاموش', { risk: false });
  const r = await addEntry(o, { list: 'block', kind: 'phone', phone: '0791110010', reason: 'سفارشِ ساختگی', expiresInDays: 30 });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'order_risk_disabled');
  assert.equal((await h.get('/api/shop/orders/risk/lists', { token: o.token })).status, 403);
  //  ردیفی که پیش از خاموش شدن ساخته شده بود
  await query(
    `INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, reason, expires_at, created_at)
     VALUES ($1,$2,'block','phone','+93791110010','آزمون',$3,$4)`,
    [newId('rl'), o.shopId, Date.now() + 86_400_000, Date.now()]
  );
  assert.equal((await checkout(o, cart())).status, 201);
});

test('ورودیِ ناجور ⇒ ۴۰۰: بی دلیل، انقضای ۰ یا ۴۰۰ روز، پینِ مطمئن، شمارهٔ بد، شعاعِ بیرونِ بازه', async () => {
  const o = await liveStore('ورودی');
  const base = { list: 'block', kind: 'phone', phone: '0791110010', reason: 'سفارشِ ساختگی', expiresInDays: 30 };
  const cases = [
    [{ ...base, reason: ' ' }, 'reason_required'],
    [{ ...base, reason: 'x'.repeat(201) }, 'reason_required'],
    [{ ...base, expiresInDays: 0 }, 'bad_expiry'],
    [{ ...base, expiresInDays: 400 }, 'bad_expiry'],
    [{ ...base, expiresInDays: undefined }, 'bad_expiry'],
    [{ ...base, list: 'grey' }, 'bad_risk_entry'],
    [{ list: 'allow', kind: 'pin', ...PIN, reason: 'دفتر', expiresInDays: 30 }, 'bad_risk_entry'],
    [{ list: 'block', kind: 'pin', ...PIN, radiusM: 5, reason: 'جای ساختگی', expiresInDays: 30 }, 'bad_risk_entry'],
    [{ list: 'block', kind: 'pin', lat: 91, lng: 0, reason: 'جای ساختگی', expiresInDays: 30 }, 'bad_risk_entry'],
  ];
  for (const [body, code] of cases) {
    const r = await addEntry(o, body);
    assert.equal(r.status, 400, `${JSON.stringify(body)} ⇒ ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code);
  }
  const bad = await addEntry(o, { ...base, phone: 'abc' });
  assert.equal(bad.status, 400);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_risk_lists WHERE shop_id=$1', [o.shopId])).n, 0);
});

test('RSK-04: شمارهٔ بسته (‎+930…‎ هم) ⇒ ۴۰۳ order_blocked با پیامِ خنثی و بی ردیف؛ برداشتن ⇒ آزاد؛ سابقه بی شماره', async () => {
  const o = await liveStore('شماره');
  const add = await addEntry(o, { list: 'block', kind: 'phone', phone: '+930791110010', reason: 'سه بار نقدِ پرداخت‌نشده', expiresInDays: 30 });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  assert.equal(add.body.entry.phone, '+93791110010', 'شکلِ یکتا');
  const r = await checkout(o, cart());
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'order_blocked');
  assert.ok(!r.body.error.message.includes('نقد'), 'دلیل به مشتری نمی‌رسد');
  assert.deepEqual(Object.keys(r.body.error).sort(), ['code', 'message']);
  assert.equal(await count(o.shopId), 0);
  assert.equal((await checkout(o, cart({ phone: '0791110011' }))).status, 201, 'شمارهٔ دیگر آزاد');

  const del = await h.del(`/api/shop/orders/risk/lists/${add.body.entry.id}`, { token: o.token });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.equal(del.body.removed, true);
  assert.equal((await h.del(`/api/shop/orders/risk/lists/${add.body.entry.id}`, { token: o.token })).body.removed, false, 'تکرار بی‌اثر');
  assert.equal((await checkout(o, cart())).status, 201);
  const removed = await one('SELECT removed_at, removed_by FROM shop_risk_lists WHERE id=$1', [add.body.entry.id]);
  assert.ok(Number(removed.removed_at) > 0, 'برداشتنِ نرم — سابقه می‌ماند');

  const logs = await many(`SELECT action, detail FROM audit_logs WHERE shop_id=$1 AND action LIKE 'order.risk_list%' ORDER BY created_at`, [o.shopId]);
  assert.deepEqual(logs.map((l) => l.action), ['order.risk_list_add', 'order.risk_list_remove']);
  const text = JSON.stringify(logs);
  assert.ok(!text.includes('791110010') && !text.includes('نقد'), 'سابقه بی شماره و دلیل');
});

test('RSK-04: پینِ بسته با شعاع ⇒ درونِ شعاع ۴۰۳، بیرون و دریافت از دکان آزاد', async () => {
  const o = await liveStore('پین');
  const add = await addEntry(o, { list: 'block', kind: 'pin', ...PIN, radiusM: 100, reason: 'نشانیِ ساختگیِ تکراری', expiresInDays: 7 });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  assert.deepEqual(add.body.entry.pin, { ...PIN, radiusM: 100 });
  assert.equal((await checkout(o, cart({ pin: north(60) }))).body.error?.code, 'order_blocked');
  assert.equal((await checkout(o, cart({ phone: '0791110012', pin: north(300) }))).status, 201, 'بیرونِ شعاع');
  assert.equal((await checkout(o, cart({ phone: '0791110013', pickup: true }))).status, 201, 'دریافت از دکان');
});

test('RSK-04: ردیفِ گذشته بی‌اثر است و از فهرست بیرون؛ همان شماره دوباره افزوده می‌شود', async () => {
  const o = await liveStore('انقضا');
  const add = await addEntry(o, { list: 'block', kind: 'phone', phone: '0791110010', reason: 'آزمونِ انقضا', expiresInDays: 1 });
  assert.equal(add.status, 201);
  await query('UPDATE shop_risk_lists SET expires_at=$2 WHERE id=$1', [add.body.entry.id, Date.now() - 1000]);
  assert.equal((await checkout(o, cart())).status, 201);
  assert.equal((await h.get('/api/shop/orders/risk/lists', { token: o.token })).body.items.length, 0);
  const again = await addEntry(o, { list: 'allow', kind: 'phone', phone: '0791110010', reason: 'مشتریِ آشنا', expiresInDays: 90 });
  assert.equal(again.status, 201, JSON.stringify(again.body));
  assert.equal((await one('SELECT removed_by FROM shop_risk_lists WHERE id=$1', [add.body.entry.id])).removed_by, 'expired');
});

test('RSK-04: شمارهٔ مطمئن از سقفِ مشتریِ تازه رد نمی‌شود، ولی تکرار همچنان ۴۰۹؛ یک شماره فقط در یک فهرست', async () => {
  const o = await liveStore('مطمئن', { policy: { newCustomerMaxTotalMinor: 10_000, duplicateWindowMin: 10 } });
  assert.equal((await checkout(o, cart({ phone: '0791110014' }))).body.error?.code, 'new_customer_limit');
  const add = await addEntry(o, { list: 'allow', kind: 'phone', phone: '0791110014', reason: 'دفترِ همسایه، آشنا', expiresInDays: 180 });
  assert.equal(add.status, 201);
  assert.equal((await checkout(o, cart({ phone: '0791110014' }))).status, 201, 'مطمئن');
  assert.equal((await checkout(o, cart({ phone: '0791110014' }))).body.error?.code, 'duplicate_order', 'تکرار همچنان');
  const both = await addEntry(o, { list: 'block', kind: 'phone', phone: '0791110014', reason: 'دو فهرست', expiresInDays: 1 });
  assert.equal(both.status, 409);
  assert.equal(both.body.error.code, 'already_listed');
  assert.equal(both.body.error.list, 'allow');

  //  نشانه در نمای ریسکِ سفارش (بی دلیل)
  const id = (await one(`SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1`, [o.shopId])).id;
  const risk = await h.get(`/api/shop/orders/${id}/risk`, { token: o.token });
  const flag = risk.body.flags.find((f) => f.code === 'allowlisted');
  assert.ok(flag, JSON.stringify(risk.body.flags));
  assert.ok(!JSON.stringify(risk.body).includes('همسایه'));
});

test('فقط صاحب و مدیر؛ دکانِ دیگر نه می‌بیند نه برمی‌دارد', async () => {
  const o = await liveStore('دسترسی');
  const add = await addEntry(o, { list: 'block', kind: 'phone', phone: '0791110015', reason: 'آزمونِ دسترسی', expiresInDays: 3 });
  assert.equal(add.status, 201);
  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), o.shopId, staff.user.id, now()]
  );
  assert.equal((await h.get('/api/shop/orders/risk/lists', { token: staff.accessToken })).status, 403);
  assert.equal((await addEntry(o, { list: 'allow', kind: 'phone', phone: '0791110016', reason: 'شاگرد', expiresInDays: 3 }, staff.accessToken)).status, 403);

  const b = await liveStore('دسترسی ب');
  assert.equal((await h.get('/api/shop/orders/risk/lists', { token: b.token })).body.items.length, 0);
  const del = await h.del(`/api/shop/orders/risk/lists/${add.body.entry.id}`, { token: b.token });
  assert.equal(del.status, 404);
  const mine = await h.get('/api/shop/orders/risk/lists', { token: o.token });
  assert.deepEqual(mine.body.items.map((i) => i.id), [add.body.entry.id]);
  assert.equal(mine.body.items[0].reason, 'آزمونِ دسترسی');
});
