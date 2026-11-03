'use strict';
/**
 * CRM-07 — سفارش‌های **هنوز تحویل‌نشده یا لغوشدهٔ** یک مشتری برای پروندهٔ او (`GET /api/shop/orders/customer`،
 * `lib/orders.forCustomer`). تحویل‌شده‌ها فروشِ عادیِ دفترند و همان‌جا دیده می‌شوند، پس این‌جا نیستند.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پرچمِ `shop.orders` خاموش ⇒ ۴۰۳ `orders_disabled` (همان `/shop/orders`)؛ بی شماره یا شمارهٔ نادرست ⇒ فهرستِ خالی، نه ۴۰۰.
 *   ۲) فقط سفارش‌های باز و لغوشده/ردشدهٔ **همان** گوشی (هر شکلِ شماره: ‎07…‎، ‎+93…‎، رقمِ فارسی، شمارهٔ دوم)، تازه‌ترین
 *      بالا؛ تحویل‌گرفته و شمارهٔ دیگر نه؛ شمارهٔ کامل هرگز در پاسخ نیست.
 *   ۳) اجازه: `orders.contact` (صاحب و مدیر)؛ شاگرد ۴۰۳؛ دکانِ دیگر با همان شماره هیچ.
 *   ۴) سقفِ IP پیش از احراز (بی توکن هم شمرده می‌شود) و بی توکن ۴۰۱.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const orders = require('../src/lib/orders');

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

async function shop(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  if (on) await setFlag(s.shopId, 'shop.orders', true);
  return s;
}

const order = async (s, phone, name = 'مشتری') => {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name, phone },
    lines: [{ name: 'برنج', unit: 'کیلو', qtyMilli: 1000, unitPriceMinor: 12_000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `oc-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
};
const go = async (s, o, to, reason) => {
  const cur = (await h.get(`/api/shop/orders/${o.id}`, { token: s.token })).body.order;
  const r = await h.post(`/api/shop/orders/${o.id}/transition`, { to, from: cur.status, ...(reason ? { reason } : {}) }, { token: s.token });
  assert.equal(r.status, 200, `${cur.status} ⇒ ${to}: ${JSON.stringify(r.body)}`);
};
const lookup = (token, ...phones) => {
  const q = phones.map((p) => `phone=${encodeURIComponent(p)}`).join('&');
  return h.get(`/api/shop/orders/customer${q ? `?${q}` : ''}`, { token });
};

test('CRM-07: پرچمِ خاموش ⇒ ۴۰۳؛ بی شماره یا شمارهٔ نادرست ⇒ خالی، نه ۴۰۰', async () => {
  const off = await shop('خاموش', { on: false });
  const r = await lookup(off.token, '0700111222');
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'orders_disabled');

  const s = await shop('خالی');
  await order(s, '0700111333');
  for (const phones of [[], ['abc'], ['12'], ['x'.repeat(60)]]) {
    const e = await lookup(s.token, ...phones);
    assert.equal(e.status, 200, JSON.stringify(e.body));
    assert.deepEqual(e.body, { items: [], more: false });
  }
});

test('CRM-07: فقط باز و لغوشدهٔ همان گوشی، هر شکلِ شماره، تازه‌ترین بالا، بی شمارهٔ کامل', async () => {
  const s = await shop('مشتری');
  const open = await order(s, '0700111222', 'احمد');
  const rejected = await order(s, '+93700111222', 'احمد');
  await go(s, rejected, 'rejected', 'out_of_stock');
  const cancelled = await order(s, '0700111222', 'احمد');
  await go(s, cancelled, 'accepted');
  await go(s, cancelled, 'cancelled', 'customer_request');
  const done = await order(s, '0700111222', 'احمد');
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) await go(s, done, to);
  const other = await order(s, '0799000111', 'دیگری');
  const second = await order(s, '0788555444', 'احمد');

  const r = await lookup(s.token, '۰۷۰۰۱۱۱۲۲۲');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.items.map((x) => x.id), [cancelled.id, rejected.id, open.id], 'تازه‌ترین بالا؛ تحویل‌گرفته و شمارهٔ دیگر نه');
  assert.deepEqual(r.body.items.map((x) => x.status), ['cancelled', 'rejected', 'new']);
  assert.equal(r.body.more, false);
  assert.ok(r.body.items.every((x) => x.customer.phoneMasked && !('phone' in x.customer)));
  assert.doesNotMatch(JSON.stringify(r.body), /700111222/, 'شمارهٔ کامل هرگز');
  assert.ok(!r.body.items.some((x) => x.id === done.id || x.id === other.id));

  //  همان گوشی با ‎+93‎ و ‎0093‎، و شمارهٔ دومِ مشتری
  assert.deepEqual((await lookup(s.token, '+93700111222')).body.items.map((x) => x.id), [cancelled.id, rejected.id, open.id]);
  assert.deepEqual((await lookup(s.token, '0093700111222')).body.items.length, 3);
  assert.deepEqual((await lookup(s.token, '0700111222', '0788555444')).body.items.map((x) => x.id), [second.id, cancelled.id, rejected.id, open.id]);
  //  بیش از دو شماره نادیده (فقط دوتای اول)
  assert.deepEqual((await lookup(s.token, '0711000000', '0722000000', '0700111222')).body.items, []);
});

test('CRM-07: سقفِ فهرست و «بیشتر»', async () => {
  const s = await shop('بسیار');
  for (let i = 0; i < orders.CUSTOMER_LIMIT + 1; i++) await order(s, '0700222333');
  resetAll();
  const r = await lookup(s.token, '0700222333');
  assert.equal(r.body.items.length, orders.CUSTOMER_LIMIT);
  assert.equal(r.body.more, true);
});

test('CRM-07: فقط صاحب و مدیر (orders.contact)؛ شاگرد ۴۰۳؛ دکانِ دیگر با همان شماره هیچ', async () => {
  const a = await shop('الف');
  await order(a, '0700333444');
  const add = async (role) => {
    const u = await h.newUser(`${role}-${uniq()}`, 'shop');
    await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
      [newId('mem'), a.shopId, u.user.id, role, now()]);
    return u.accessToken;
  };
  const staff = await lookup(await add('staff'), '0700333444');
  assert.equal(staff.status, 403);
  assert.equal(staff.body.error.code, 'permission_denied');
  const manager = await lookup(await add('manager'), '0700333444');
  assert.equal(manager.status, 200, JSON.stringify(manager.body));
  assert.equal(manager.body.items.length, 1);

  const b = await shop('ب');
  const other = await lookup(b.token, '0700333444');
  assert.equal(other.status, 200);
  assert.deepEqual(other.body.items, [], 'دکانِ «ب» سفارشِ «الف» را نمی‌بیند');
});

test('CRM-07: بی توکن ۴۰۱ و سقفِ IP پیش از احراز', async () => {
  assert.equal((await lookup('', '0700111222')).status, 401);
  //  سطلِ IP ِ همین در پیش از `requireUser` سوار است (CodeQL `js/missing-rate-limiting`)
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'routes', 'order-customer.js'), 'utf8');
  const ip = src.indexOf("router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'order-customer-ip' }));");
  assert.ok(ip > 0);
  assert.ok(ip < src.indexOf('router.use(requireUser'));
  const app = fs.readFileSync(path.join(__dirname, '..', 'src', 'app.js'), 'utf8');
  assert.ok(app.indexOf("api.use('/shop/orders/customer'") < app.indexOf("api.use('/shop/orders', require('./routes/orders'))"),
    'پیش از `/shop/orders` تا با `/:id` اشتباه نشود');
});
