'use strict';
/**
 * RSK-03 «کارت در صف» — `GET /api/shop/orders/risk/queue` (`lib/order-risk.queueOf`، پرچمِ `shop.order_risk`):
 * سفارش‌های بازِ پیش از ارسالِ همین دکان که دستِ کم یک **هشدار** دارند، برای کارتِ «نگاهِ دوباره» ِ صفِ برنامه.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ ریسک خاموش ⇒ `{enabled:false}` (همان `/:id/risk`)؛ پرچمِ سفارش خاموش ⇒ ۴۰۳؛ بی توکن ⇒ ۴۰۱.
 *   ۲) فقط وضعیت‌های پیش از ارسال (تازه، پذیرفته، جمع‌آوری، بسته، آمادهٔ تحویل، ارسالِ دوباره)؛ تحویل‌شده،
 *      ردشده، برگشته و آزمایشی نه؛ «فقط مشتریِ تازه» (نشانهٔ info) نه؛ نشانه‌ها همان `riskOf`.
 *   ۳) ⛔ بی شماره و بی دلیلِ فهرست؛ فقط همین دکان (دکانِ دیگر با همان شماره نه می‌بیند نه شمرده می‌شود).
 *   ۴) سقفِ پویش (تازه‌ترین‌ها اول) و `truncated`؛ هر نقشِ `orders.view` (نه فقط صاحب) می‌بیند.
 *   ۵) بدنهٔ «بستنِ این جا» ِ برنامه (`OrderRisk.pinEntryBody`، همان رشتهٔ `OrderRiskTest`) را سرور می‌پذیرد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/order-risk');
const orders = require('../src/lib/orders');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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

/** دکانِ روشن با ویترین و یک کالا؛ سیاستِ «همه خاموش» تا ثبت رد نشود و فقط نمای سابقه سنجیده شود */
async function liveStore(label, { risk = true, ordersOn = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `q-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (risk) await setFlag(o.shopId, R.FLAG_KEY, true);
  await setFlag(o.shopId, R.POLICY_KEY, { newCustomerMaxOpen: 0, newCustomerMaxTotalMinor: 0, duplicateWindowMin: 0 });
  assert.equal((await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-queue' },
    body: { device_id: 'dev-queue', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products',
      row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  if (!ordersOn) await setFlag(o.shopId, 'shop.orders', false);
  return o;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
/** ثبتِ ویترین ⇒ شناسهٔ سفارش */
async function place(o, phone, qty = 1000) {
  const body = {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: qty }],
    address: { lat: 34.531234, lng: 69.171234, text: 'کوچهٔ سوم' },
  };
  const ticket = await ticketFor(o.slug, phone);
  const r = await h.post(`/api/store/${o.slug}/checkout`, body,
    { headers: { 'Idempotency-Key': `qq-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order.token.split('.')[0];
}
const STAFF = (o) => ({ type: 'user', id: o.user.id, role: 'owner' });
const walk = async (o, id, steps) => { for (const to of steps) await orders.transition(o.shopId, id, to, STAFF(o)); };
const queue = (o, token = o.token) => h.get('/api/shop/orders/risk/queue', { token });

test('⛔ پرچمِ ریسک خاموش ⇒ {enabled:false}؛ پرچمِ سفارش خاموش ⇒ ۴۰۳ orders_disabled؛ بی توکن ⇒ ۴۰۱', async () => {
  const off = await liveStore('صف خاموش', { risk: false });
  await place(off, '0791120001');
  const r = await queue(off);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { enabled: false });

  const closed = await liveStore('صف بسته', { ordersOn: false });
  const c = await queue(closed);
  assert.equal(c.status, 403);
  assert.equal(c.body.error.code, 'orders_disabled');

  assert.equal((await h.get('/api/shop/orders/risk/queue')).status, 401);
});

test('RSK-03 صف: فقط سفارشِ بازِ پیش از ارسال با هشدار؛ نشانه‌ها همان riskOf؛ بی شماره؛ فقط همین دکان', async () => {
  const o = await liveStore('صف');
  //  الف: یک ارسالِ ناموفق که به دکان برگشت ⇒ سفارشِ تازهٔ همان شماره دو هشدار دارد
  const a1 = await place(o, '0791120002', 1000);
  await walk(o, a1, [{ to: 'accepted' }, { to: 'picking' }, { to: 'packed' }, { to: 'out_for_delivery' },
    { to: 'delivery_failed', reason: 'no_answer' }, { to: 'retry_scheduled' }, { to: 'returned_to_store' }]);
  const a2 = await place(o, '0791120002', 2000);
  //  ب: مشتریِ تازه ⇒ فقط نشانهٔ info ⇒ در صف نیست
  await place(o, '0791120003');
  //  پ: شمارهٔ بسته‌شده پس از ثبت ⇒ هشدارِ «در فهرستِ بسته»؛ دلیل هرگز در پاسخ نیست
  const c1 = await place(o, '0791120004');
  const listed = await h.post('/api/shop/orders/risk/lists',
    { list: 'block', kind: 'phone', phone: '0791120004', reason: 'رازِ دکان', expiresInDays: 30 }, { token: o.token });
  assert.equal(listed.status, 201, JSON.stringify(listed.body));
  //  ت: همان شمارهٔ الف — ردشده (بیرون از صف) و آزمایشی (بیرون از صف)
  const a3 = await place(o, '0791120002', 3000);
  await walk(o, a3, [{ to: 'rejected', reason: 'out_of_area' }]);
  const a4 = await place(o, '0791120002', 4000);
  await query('UPDATE online_orders SET is_test=true WHERE id=$1', [a4]);
  //  دکانِ دیگر با همان شماره و سابقهٔ خودش
  const other = await liveStore('صف دیگر');
  await place(other, '0791120002');

  const r = await queue(o);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.enabled, true);
  assert.equal(r.body.truncated, false);
  assert.equal(r.body.limit, R.QUEUE_LIMIT);
  assert.deepEqual(r.body.items.map((i) => i.orderId), [c1, a2], 'تازه‌ترین بالا؛ تازه/ردشده/برگشته/آزمایشی نه');
  const byId = Object.fromEntries(r.body.items.map((i) => [i.orderId, i]));
  //  هنوز هیچ تحویلی نگرفته ⇒ «تازه» (info) هم کنارِ دو هشدار
  assert.deepEqual(byId[a2].flags.map((f) => f.code).sort(), ['delivery_failed_before', 'new_customer', 'returned_to_shop_before']);
  assert.equal(byId[a2].status, 'new');
  assert.ok(Number.isInteger(byId[a2].orderNo) && byId[a2].orderNo > 0);
  assert.ok(byId[c1].flags.some((f) => f.code === 'blocklisted' && f.severity === 'warn' && Number.isInteger(f.until)));
  //  همان نشانه‌های درِ یک سفارش
  const one1 = await h.get(`/api/shop/orders/${a2}/risk`, { token: o.token });
  assert.deepEqual(byId[a2].flags, one1.body.flags);
  const text = JSON.stringify(r.body);
  for (const p of ['791120002', '791120004', 'رازِ دکان']) assert.ok(!text.includes(p), `بی ${p}`);

  //  پذیرفته و بسته هنوز «پیش از ارسال»اند؛ در راه نه
  await walk(o, a2, [{ to: 'accepted' }, { to: 'picking' }, { to: 'packed' }]);
  assert.deepEqual((await queue(o)).body.items.map((i) => i.orderId), [c1, a2]);
  await walk(o, a2, [{ to: 'out_for_delivery' }]);
  assert.deepEqual((await queue(o)).body.items.map((i) => i.orderId), [c1]);

  //  دکانِ دیگر: سابقهٔ این دکان را نمی‌شمارد و سفارشِ این دکان را نمی‌بیند
  const theirs = await queue(other);
  assert.equal(theirs.status, 200);
  assert.deepEqual(theirs.body.items, []);
});

test('RSK-03 صف: سقفِ پویش با truncated؛ شاگرد (orders.view) می‌بیند؛ بدنهٔ پینِ برنامه پذیرفته است', async () => {
  const o = await liveStore('صف سقف');
  const c1 = await place(o, '0791120005');
  const c2 = await place(o, '0791120006');
  for (const p of ['0791120005', '0791120006']) {
    const r = await h.post('/api/shop/orders/risk/lists',
      { list: 'block', kind: 'phone', phone: p, reason: 'آزمونِ سقف', expiresInDays: 3 }, { token: o.token });
    assert.equal(r.status, 201);
  }
  const all = await R.queueOf(o.shopId);
  assert.deepEqual(all.items.map((i) => i.orderId), [c2, c1]);
  assert.equal(all.truncated, false);
  const cut = await R.queueOf(o.shopId, { limit: 1 });
  assert.deepEqual(cut.items.map((i) => i.orderId), [c2], 'تازه‌ترین');
  assert.equal(cut.truncated, true);
  assert.equal(cut.scanned, 1);

  const staff = await h.newUser('شاگردِ صف', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), o.shopId, staff.user.id, now()]
  );
  const seen = await queue(o, staff.accessToken);
  assert.equal(seen.status, 200, JSON.stringify(seen.body));
  assert.deepEqual(seen.body.items.map((i) => i.orderId), [c2, c1]);

  //  همان رشتهٔ `OrderRiskTest.pinEntryBody…` (برنامه) — قراردادِ مشترک
  const body = '{"list":"block","kind":"pin","lat":34.531234,"lng":69.171234,"radiusM":150,"reason":"نشانیِ ساختگی","expiresInDays":30}';
  const pin = await h.post('/api/shop/orders/risk/lists', JSON.parse(body), { token: o.token });
  assert.equal(pin.status, 201, JSON.stringify(pin.body));
  assert.deepEqual(pin.body.entry.pin, { lat: 34.531234, lng: 69.171234, radiusM: 150 });
  //  سفارش‌هایی که همان‌جا ثبت شده بودند حالا «در فهرستِ بسته» اند (جا، نه شماره) — هنوز همان دو
  const after = await R.queueOf(o.shopId);
  assert.deepEqual(after.items.map((i) => i.orderId), [c2, c1]);
});
