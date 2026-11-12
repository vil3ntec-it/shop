'use strict';
/**
 * RSK-04 «دستگاه» در فهرستِ سیاه و سفیدِ سفارشِ ویترین (`lib/risk-device.js`، مهاجرتِ ۸۴۰، پرچمِ `shop.order_risk`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) شناسه همان رشتهٔ تصادفیِ مرورگر/برنامهٔ خرید است؛ سرور فقط چکیدهٔ «دکان + شناسه» را نگه می‌دارد (نه خودِ شناسه)،
 *      فقط با پرچمِ روشن، و هیچ پاسخی چکیده را بیرون نمی‌دهد.
 *   ۲) «بستنِ این دستگاه» فقط از راهِ سفارشِ همین دکان؛ دستگاهِ بسته ⇒ ۴۰۳ ِ خنثی حتی با شمارهٔ دیگر؛ مطمئن ⇒ بی سقفِ
 *      مشتریِ تازه؛ یک دستگاه فقط در یک فهرست؛ سفارشِ بی دستگاه ⇒ ۴۰۹ `device_unknown`.
 *   ۳) فقط صاحب و مدیر؛ دکانِ دیگر نه سفارش را می‌بندد نه فهرست را می‌بیند؛ همان شناسه در دکانِ دیگر بسته نیست؛
 *      سقفِ ۵۰۰ ردیفِ فعال.
 *   ۴) «حذفِ دادهٔ مشتری» (SEC-10) دستگاهِ همان سفارش‌ها را پاک می‌کند؛ قیدهای پایگاه شکلِ ردیف را نگه می‌دارند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/order-risk');
const D = require('../src/lib/risk-device');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const DEV_A = 'a1b2c3d4e5f60718293a4b5c6d7e8f90';
const DEV_B = 'ffeeddccbbaa99887766554433221100';

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

async function liveStore(label, { risk = true, policy = { newCustomerMaxOpen: 0, duplicateWindowMin: 0 } } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `d-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (risk) await setFlag(o.shopId, R.FLAG_KEY, true);
  if (policy) await setFlag(o.shopId, R.POLICY_KEY, policy);
  assert.equal((await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-dev' },
    body: { device_id: 'dev-dev', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products',
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
async function checkout(o, { phone = '0791120010', deviceId, qty = 2000 } = {}) {
  const body = {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: qty }],
    ...(deviceId === undefined ? {} : { deviceId }),
  };
  return h.post(`/api/store/${o.slug}/checkout`, body,
    { headers: { 'Idempotency-Key': `dv-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(o.slug, phone) } });
}
const orderIdOf = async (o, r) => (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [o.shopId, r.body.order.orderNo])).id;
const addEntry = (o, body, token = o.token) => h.post('/api/shop/orders/risk/lists', body, { token });
const devices = (shopId) => many('SELECT * FROM online_order_devices WHERE shop_id=$1', [shopId]);

test('چکیده: «دکان + شناسه»، ۳۲ هگز؛ دو دکان دو چکیده؛ شناسهٔ نادرست ⇒ نامعلوم', () => {
  const a = D.keyOf('shp_a', DEV_A);
  assert.match(a, /^[0-9a-f]{32}$/);
  assert.equal(D.keyOf('shp_a', DEV_A), a);
  assert.notEqual(D.keyOf('shp_b', DEV_A), a, 'همان دستگاه در دکانِ دیگر چکیدهٔ دیگری دارد');
  assert.ok(!a.includes(DEV_A.slice(0, 8)));
  for (const bad of ['', 'short', 'a'.repeat(129), 'bad id!', 42, null, undefined, { x: 1 }]) assert.equal(D.keyOf('shp_a', bad), '');
  assert.equal(D.keyOf('', DEV_A), '');
});

test('⛔ پرچمِ خاموش: هیچ دستگاهی نگه داشته نمی‌شود و درِ فهرست ۴۰۳', async () => {
  const o = await liveStore('خاموش', { risk: false });
  const r = await checkout(o, { deviceId: DEV_A });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal((await devices(o.shopId)).length, 0);
  const add = await addEntry(o, { list: 'block', kind: 'device', orderId: await orderIdOf(o, r), reason: 'سفارشِ ساختگی', expiresInDays: 30 });
  assert.equal(add.status, 403);
  assert.equal(add.body.error.code, 'order_risk_disabled');
});

test('RSK-04: دستگاهِ بسته ⇒ ۴۰۳ ِ خنثی حتی با شمارهٔ دیگر؛ دستگاهِ دیگر یا بی دستگاه آزاد؛ چکیده هرگز بیرون نمی‌رود', async () => {
  const o = await liveStore('بسته');
  const first = await checkout(o, { deviceId: DEV_A });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const id = await orderIdOf(o, first);
  const rows = await devices(o.shopId);
  assert.equal(rows.length, 1, 'دستگاهِ همین سفارش در همان تراکنش');
  assert.equal(rows[0].device_key, D.keyOf(o.shopId, DEV_A));
  assert.ok(!JSON.stringify(rows).includes(DEV_A), 'خودِ شناسه نگه داشته نمی‌شود');

  const risk = await h.get(`/api/shop/orders/${id}/risk`, { token: o.token });
  assert.equal(risk.status, 200);
  assert.equal(risk.body.device, true);
  assert.ok(!JSON.stringify(risk.body).includes(rows[0].device_key));

  //  همان رشتهٔ `OrderRiskTest.deviceEntriesFormAndBodyMatchTheServer` (جز شناسهٔ سفارش)
  const body = JSON.parse(`{"list":"block","kind":"device","orderId":"${id}","reason":"سه سفارشِ ساختگی","expiresInDays":30}`);
  const add = await addEntry(o, body);
  assert.equal(add.status, 201, JSON.stringify(add.body));
  assert.deepEqual(add.body.entry.device, { orderNo: first.body.order.orderNo });
  assert.equal(add.body.entry.kind, 'device');
  assert.ok(!JSON.stringify(add.body).includes(rows[0].device_key) && !JSON.stringify(add.body).includes(DEV_A));
  const list = await h.get('/api/shop/orders/risk/lists', { token: o.token });
  assert.deepEqual(list.body.items.map((i) => i.kind), ['device']);
  assert.ok(!JSON.stringify(list.body).includes(rows[0].device_key));

  const blocked = await checkout(o, { phone: '0791120011', deviceId: DEV_A });
  assert.equal(blocked.status, 403, JSON.stringify(blocked.body));
  assert.equal(blocked.body.error.code, 'order_blocked');
  assert.deepEqual(Object.keys(blocked.body.error).sort(), ['code', 'message'], 'خنثی: نه دلیل، نه این‌که دستگاه بود');
  assert.ok(!blocked.body.error.message.includes('ساختگی'));
  assert.equal((await checkout(o, { phone: '0791120012', deviceId: DEV_B })).status, 201, 'دستگاهِ دیگر آزاد');
  assert.equal((await checkout(o, { phone: '0791120013' })).status, 201, 'بی شناسه آزاد — دستگاه سدِ سخت نیست');
  assert.equal((await checkout(o, { phone: '0791120014', deviceId: 'bad id!' })).status, 201, 'شناسهٔ نادرست ⇒ نامعلوم');

  //  نشانه در نمای ریسکِ سفارشِ دیگرِ همان دستگاه (بی دلیل)
  const again = await h.get(`/api/shop/orders/${id}/risk`, { token: o.token });
  assert.ok(again.body.flags.some((f) => f.code === 'blocklisted'), JSON.stringify(again.body.flags));

  const logs = await many(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='order.risk_list_add'`, [o.shopId]);
  assert.equal(logs.length, 1);
  assert.ok(!JSON.stringify(logs).includes(rows[0].device_key) && !JSON.stringify(logs).includes('ساختگی'), 'سابقه بی چکیده و دلیل');

  //  برداشتن ⇒ آزاد
  assert.equal((await h.del(`/api/shop/orders/risk/lists/${add.body.entry.id}`, { token: o.token })).body.removed, true);
  assert.equal((await checkout(o, { phone: '0791120011', deviceId: DEV_A })).status, 201);
});

test('RSK-04: سفارشِ بی دستگاه ⇒ ۴۰۹ device_unknown؛ ورودیِ ناجور ۴۰۰؛ سفارشِ ناموجود ۴۰۴', async () => {
  const o = await liveStore('ناشناس');
  const r = await checkout(o);
  assert.equal(r.status, 201);
  const id = await orderIdOf(o, r);
  assert.equal((await h.get(`/api/shop/orders/${id}/risk`, { token: o.token })).body.device, false);
  const base = { list: 'block', kind: 'device', orderId: id, reason: 'سفارشِ ساختگی', expiresInDays: 30 };
  const unknown = await addEntry(o, base);
  assert.equal(unknown.status, 409, JSON.stringify(unknown.body));
  assert.equal(unknown.body.error.code, 'device_unknown');
  for (const [b, code] of [[{ ...base, orderId: '' }, 'bad_risk_entry'], [{ ...base, orderId: 'a b' }, 'bad_risk_entry'],
    [{ ...base, reason: 'x' }, 'reason_required'], [{ ...base, expiresInDays: 0 }, 'bad_expiry'], [{ ...base, kind: 'face' }, 'bad_risk_entry']]) {
    const x = await addEntry(o, b);
    assert.equal(x.status, 400, `${JSON.stringify(b)} ⇒ ${JSON.stringify(x.body)}`);
    assert.equal(x.body.error.code, code);
  }
  const ghost = await addEntry(o, { ...base, orderId: 'ord_nope' });
  assert.equal(ghost.status, 404);
  assert.equal(ghost.body.error.code, 'order_not_found');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_risk_lists WHERE shop_id=$1', [o.shopId])).n, 0);
});

test('RSK-04: دستگاهِ مطمئن از سقفِ مشتریِ تازه رد نمی‌شود؛ یک دستگاه فقط در یک فهرست؛ گذشته جا را باز می‌کند', async () => {
  const o = await liveStore('مطمئن', { policy: { newCustomerMaxTotalMinor: 10_000, duplicateWindowMin: 0 } });
  const small = await checkout(o, { deviceId: DEV_A, qty: 50 });
  assert.equal(small.status, 201, JSON.stringify(small.body));
  const id = await orderIdOf(o, small);
  assert.equal((await checkout(o, { phone: '0791120020', deviceId: DEV_A })).body.error?.code, 'new_customer_limit');
  const add = await addEntry(o, { list: 'allow', kind: 'device', orderId: id, reason: 'خانوادهٔ صاحبِ دکان', expiresInDays: 90 });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  assert.equal((await checkout(o, { phone: '0791120020', deviceId: DEV_A })).status, 201, 'مطمئن');
  assert.equal((await checkout(o, { phone: '0791120021', deviceId: DEV_B })).body.error?.code, 'new_customer_limit', 'دستگاهِ دیگر نه');
  const both = await addEntry(o, { list: 'block', kind: 'device', orderId: id, reason: 'دو فهرست', expiresInDays: 1 });
  assert.equal(both.status, 409);
  assert.equal(both.body.error.code, 'already_listed');
  assert.equal(both.body.error.list, 'allow');
  await query('UPDATE shop_risk_lists SET expires_at=$2 WHERE id=$1', [add.body.entry.id, Date.now() - 1000]);
  const after = await addEntry(o, { list: 'block', kind: 'device', orderId: id, reason: 'دیگر مطمئن نیست', expiresInDays: 1 });
  assert.equal(after.status, 201, JSON.stringify(after.body));
  assert.equal((await one('SELECT removed_by FROM shop_risk_lists WHERE id=$1', [add.body.entry.id])).removed_by, 'expired');
});

test('فقط صاحب و مدیر؛ دکانِ دیگر نه سفارش را می‌بندد نه می‌بیند؛ همان شناسه در دکانِ دیگر بسته نیست', async () => {
  const a = await liveStore('دسترسی الف');
  const b = await liveStore('دسترسی ب');
  const r = await checkout(a, { deviceId: DEV_A });
  const id = await orderIdOf(a, r);
  const body = { list: 'block', kind: 'device', orderId: id, reason: 'آزمونِ دسترسی', expiresInDays: 3 };
  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, staff.user.id, now()]
  );
  assert.equal((await addEntry(a, body, staff.accessToken)).status, 403, 'شاگرد');
  const cross = await addEntry(b, body);
  assert.equal(cross.status, 404, 'سفارشِ دکانِ دیگر');
  assert.equal(cross.body.error.code, 'order_not_found');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_risk_lists WHERE shop_id = ANY($1::text[])', [[a.shopId, b.shopId]])).n, 0);
  //  ⛔ ردیفِ دکانِ دیگر (حتی با همان چکیده — که در عمل ساخته نمی‌شود) بر ثبتِ این دکان اثری ندارد
  await query(`INSERT INTO shop_risk_lists (id, shop_id, list, kind, device_key, reason, expires_at, created_at)
               VALUES ($1,$2,'block','device',$3,'دکانِ دیگر',$4,$4)`, [newId('rl'), b.shopId, D.keyOf(a.shopId, DEV_A), Date.now() + 86_400_000]);
  assert.equal((await checkout(a, { phone: '0791120032', deviceId: DEV_A })).status, 201, 'فهرستِ «ب» بر «الف» اثری ندارد');
  await query(`DELETE FROM shop_risk_lists WHERE shop_id=$1`, [b.shopId]);
  assert.equal((await addEntry(a, body)).status, 201);
  assert.equal((await h.get('/api/shop/orders/risk/lists', { token: b.token })).body.items.length, 0);
  assert.equal((await checkout(b, { phone: '0791120030', deviceId: DEV_A })).status, 201, 'فهرستِ هر دکان مالِ خودش');
  assert.equal((await checkout(a, { phone: '0791120031', deviceId: DEV_A })).body.error?.code, 'order_blocked');
  //  نمای ریسکِ سفارشِ «الف» از «ب» ⇒ ۴۰۴
  assert.equal((await h.get(`/api/shop/orders/${id}/risk`, { token: b.token })).status, 404);
});

test('سقفِ ۵۰۰ ردیفِ فعال برای دستگاه هم', async () => {
  const o = await liveStore('سقف');
  const r = await checkout(o, { deviceId: DEV_A });
  const id = await orderIdOf(o, r);
  await query(
    `INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, reason, expires_at, created_at)
     SELECT 'rl' || g, $1, 'block', 'phone', '+9379' || lpad(g::text, 7, '0'), 'پرکردن', $2, $3 FROM generate_series(1, 500) g`,
    [o.shopId, Date.now() + 86_400_000, Date.now()]
  );
  const full = await addEntry(o, { list: 'block', kind: 'device', orderId: id, reason: 'سقف', expiresInDays: 1 });
  assert.equal(full.status, 409, JSON.stringify(full.body));
  assert.equal(full.body.error.code, 'risk_list_full');
});

test('قیدهای پایگاه (۸۴۰): ردیفِ دستگاه بی چکیدهٔ درست، و شماره با چکیده، رد می‌شوند', async () => {
  const o = await liveStore('قید', { risk: false, policy: null });
  const ins = (kind, phone, key) => query(
    `INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, device_key, reason, expires_at, created_at)
     VALUES ($1,$2,'block',$3,$4,$5,'آزمون',$6,$6)`, [newId('rl'), o.shopId, kind, phone, key, Date.now()]);
  await assert.rejects(() => ins('device', '', 'not-a-key'));
  await assert.rejects(() => ins('device', '+93791111111', D.keyOf(o.shopId, DEV_A)));
  await assert.rejects(() => ins('phone', '+93791111111', D.keyOf(o.shopId, DEV_A)));
  await ins('device', '', D.keyOf(o.shopId, DEV_A));
  await assert.rejects(() => ins('device', '', D.keyOf(o.shopId, DEV_A)), 'یک دستگاهِ فعال فقط یک ردیف');
  await assert.rejects(() => query(
    `INSERT INTO online_order_devices (shop_id, client_key, device_key, created_at) VALUES ($1,'k','XYZ',1)`, [o.shopId]));
});

test('SEC-10: حذفِ دادهٔ مشتری دستگاهِ همان سفارش را پاک می‌کند (پیش‌نمایش همان شمار)', async () => {
  const o = await liveStore('حذف');
  const r = await checkout(o, { phone: '0791120040', deviceId: DEV_A });
  const id = await orderIdOf(o, r);
  await checkout(o, { phone: '0791120041', deviceId: DEV_B });
  assert.equal((await devices(o.shopId)).length, 2);
  const erase = require('../src/lib/customer-erasure');
  await query(`UPDATE online_orders SET status='cancelled' WHERE id=$1`, [id]);
  const dry = await erase.erase(o.shopId, { orderId: id }, { dryRun: true, by: 'owner' });
  assert.equal(dry.counts.orderDevices, 1);
  assert.equal((await devices(o.shopId)).length, 2, 'پیش‌نمایش چیزی پاک نمی‌کند');
  const done = await erase.erase(o.shopId, { orderId: id }, { by: 'owner' });
  assert.equal(done.counts.orderDevices, 1);
  const left = await devices(o.shopId);
  assert.equal(left.length, 1);
  assert.equal(left[0].device_key, D.keyOf(o.shopId, DEV_B), 'سفارشِ مشتریِ دیگر دست نخورد');
});

test('مهاجرتِ ۸۴۰: برگشت (دستگاه می‌رود، شماره می‌ماند، نسخهٔ پیشین می‌نویسد) و اجرای دوباره', async () => {
  const fs = require('fs');
  const path = require('path');
  const dir = path.join(__dirname, '..', 'migrations');
  const o = await liveStore('برگشت', { risk: false, policy: null });
  const t = Date.now() + 86_400_000;
  await query(`INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, reason, expires_at, created_at)
               VALUES ($1,$2,'block','phone','+93791119999','آزمون',$3,$3)`, [newId('rl'), o.shopId, t]);
  await query(`INSERT INTO shop_risk_lists (id, shop_id, list, kind, device_key, reason, expires_at, created_at)
               VALUES ($1,$2,'block','device',$3,'آزمون',$4,$4)`, [newId('rl'), o.shopId, D.keyOf(o.shopId, DEV_A), t]);
  const table = async () => Boolean(await one(`SELECT 1 AS x FROM information_schema.tables WHERE table_name='online_order_devices'`));
  await query(fs.readFileSync(path.join(dir, 'rollback', '840_risk_list_devices.down.sql'), 'utf8'));
  assert.equal(await table(), false);
  assert.deepEqual((await many('SELECT kind FROM shop_risk_lists WHERE shop_id=$1', [o.shopId])).map((r) => r.kind), ['phone']);
  //  همان INSERT ِ نسخهٔ پیشین (بی ستون‌های تازه)
  await query(`INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, lat, lng, radius_m, reason, expires_at, created_by, created_at)
               VALUES ($1,$2,'block','pin','',34.5,69.2,100,'آزمون',$3,'',$3)`, [newId('rl'), o.shopId, t]);
  await assert.rejects(() => query(`INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, reason, expires_at, created_at)
               VALUES ($1,$2,'block','device','','آزمون',$3,$3)`, [newId('rl'), o.shopId, t]), 'پیش از ۸۴۰ دستگاه نیست');
  await query(fs.readFileSync(path.join(dir, '840_risk_list_devices.sql'), 'utf8'));
  await query(fs.readFileSync(path.join(dir, '840_risk_list_devices.sql'), 'utf8'));
  assert.equal(await table(), true);
  await query(`INSERT INTO shop_risk_lists (id, shop_id, list, kind, device_key, reason, expires_at, created_at)
               VALUES ($1,$2,'allow','device',$3,'آزمون',$4,$4)`, [newId('rl'), o.shopId, D.keyOf(o.shopId, DEV_B), t]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_risk_lists WHERE shop_id=$1', [o.shopId])).n, 3);
});
