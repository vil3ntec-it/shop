'use strict';
/**
 * O-06 = ب: «سفارش به بررسی رفت» (`order_review`، طرحِ PR ‏#429) — وقتی فروشِ حضوری موجودیِ نگه‌داشتهٔ سفارش را برد
 * (`stock_reservations.short_at` و وظیفهٔ `reservation_short`)، همان صفِ پایدارِ اعلانِ سفارش (`order_notify_outbox`،
 * گیرندگانِ `orders.manage` و صاحب) یک خبر برای کارکنانِ دکان می‌نویسد. مهاجرتِ ۵۲۰ فقط CHECK ِ `event` را بازتر می‌کند.
 *
 * از راهِ واقعی: کالا و فروش با `POST /api/sync/v1/push`، سفارش با `POST /api/shop/orders`؛ پوش با `push.setDeliver`
 * (بی شبکه)، کارگر با `order-notify.tick`.
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ پشتِ همان پرچم: بی `shop.stock_reserve` (حتی با نگه‌داشتهٔ باز) یا بی `shop.order_notify` ⇒ هیچ ردیفی.
 *   ⛔ هر سفارش یک بار: یکتاییِ پایگاه (order_id + event)؛ بازپخشِ همگام‌سازی، کمبودِ دوباره پس از پوشش و خواندن‌های
 *      هم‌زمانِ دفتر ⇒ همان یک ردیف و یک پوش؛ دورِ دومِ کارگر هیچ.
 *   ⛔ نوشته نشدنِ خبر (CHECK ِ کهنه پس از rollback ِ ۵۲۰) کارِ موجودی و همگام‌سازی را پس نمی‌زند (SAVEPOINT).
 *   ⛔ بی دادهٔ مشتری: فقط شمارهٔ سفارش و همان کلیدهای دادهٔ «پرداخت شد»/«لغو» (نام، تلفن، کالا، مبلغ نه).
 *   ⛔ بررسی پیش از فرستادن تمام شد (رد/پوشش) ⇒ بی پوش. دکان‌ها جدا (گیرنده و شناسهٔ سفارشِ دکانِ دیگر).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const push = require('../src/lib/push');

let admin = '';
const N = () => require('../src/lib/order-notify');
/** هر چه پوش شد (`push.setDeliver`) */
let sent = [];

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
  push.setDeliver((row, message) => { sent.push({ row, message }); });
});
test.after(async () => {
  push.setDeliver(null);
  await h.stop();
});

// ── کمکی‌ها (همان شکلِ stock-reserve.test.js) ─────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  const shopId = made.body.shop.id;
  //  گوشیِ صاحب برای پوش (همان `push.register` ِ `/api/me/push`)
  const token = `tok-review-${shopId}`;
  await push.register({ app: 'shop', token, userId: u.user.id, shopId, deviceUid: `phone-${shopId}` });
  return { token: s.accessToken, shopId, userId: u.user.id, pushToken: token, device: `dev-rv-${shopId}`.slice(0, 60) };
}
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function enable(s, { reserve = true, notify = true } = {}) {
  await live(s.shopId, 'shop.orders', true);
  await live(s.shopId, 'shop.catalog', true);
  if (reserve) await live(s.shopId, 'shop.stock_reserve', true);
  if (notify) await live(s.shopId, 'shop.order_notify', true);
}

let opN = 0;
const opId = () => `01JRV${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function sync(s, ops) {
  const device = s.device;
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  return r.body.results;
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
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

let keyN = 0;
const fresh = () => `stock-rv-${++keyN}-${Date.now()}`;
const line = (pid, qtyMilli) => ({ productId: pid, name: `کالای ${pid}`, unit: 'دانه', qtyMilli, unitPriceMinor: 5000 });
/** مشتریِ با نام و تلفن — تا ثابت شود هیچ‌کدام در اعلان نمی‌آید */
const CUSTOMER = Object.freeze({ name: 'زرغونهٔ رازدار', phone: '0791234567' });
async function order(s, lines) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: CUSTOMER, lines,
  }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}
const go = (s, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
const getOrder = async (s, id) => (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order;
const refresh = async (s) => assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
async function stocked(s, pid, units) {
  await sync(s, [product(pid), entry(`${pid}-e`, pid, units)]);
  await refresh(s);
}
const reviewRows = (s) => many(
  `SELECT * FROM order_notify_outbox WHERE shop_id=$1 AND event='order_review' ORDER BY order_no`, [s.shopId]
);
const sentTo = (s) => sent.filter((m) => m.row.shop_id === s.shopId);
/** همهٔ ردیف‌های سررسیده را (حتی پس از چند تیک) تا آرام شدن می‌فرستد */
async function drain(at = now()) {
  for (let i = 0; i < 5; i++) if (!(await N().tick(at))) break;
}

// ════════════════════════════════════════════════════════════════════

test('⛔ پشتِ همان پرچم: بی `shop.order_notify`، یا `shop.stock_reserve` خاموش با نگه‌داشتهٔ باز ⇒ کمبود هست ولی هیچ خبری', async () => {
  //  ۱) نگه‌داشتن روشن، اعلانِ سفارش خاموش
  const a = await shop('بررسی-بی‌اعلان');
  await enable(a, { notify: false });
  await stocked(a, 'q1', 1);
  const oa = await order(a, [line('q1', 1000)]);
  await sync(a, sale('q1-s', [['q1-i', 'q1', 1]]));
  await refresh(a);
  assert.equal((await getOrder(a, oa.id)).stockHold.review, true, 'بررسی همان رفتارِ پیشین');
  assert.equal((await reviewRows(a)).length, 0);

  //  ۲) هر دو روشن، سپس `shop.stock_reserve` خاموش — ⛔ R6: نگه‌داشتهٔ باز هیچ‌جا اثر ندارد (جزئیات بی stockHold، همان main) و
  //  خبر نه؛ کمبود فقط دفترداری می‌شود ⇒ روشنِ دوباره همان دم «در بررسی»، باز هم بی خبر
  const b = await shop('بررسی-پرچم‌خاموش');
  await enable(b);
  await stocked(b, 'q2', 1);
  const ob = await order(b, [line('q2', 1000)]);
  await live(b.shopId, 'shop.stock_reserve', false);
  await sync(b, sale('q2-s', [['q2-i', 'q2', 1]]));
  await refresh(b);
  assert.equal((await getOrder(b, ob.id)).stockHold, undefined);
  assert.equal((await reviewRows(b)).length, 0);
  await live(b.shopId, 'shop.stock_reserve', true);
  assert.equal((await getOrder(b, ob.id)).stockHold.review, true);
  assert.equal((await reviewRows(b)).length, 0);
  await drain();
  assert.equal(sentTo(a).length + sentTo(b).length, 0);
});

test('فروشِ حضوری نگه‌داشته را برد ⇒ یک ردیفِ order_review در همان تراکنش و یک پوشِ بی دادهٔ مشتری به گوشیِ دکان', async () => {
  const s = await shop('بررسی-پوش');
  await enable(s);
  await stocked(s, 'r1', 1);
  const o = await order(s, [line('r1', 1000)]);
  assert.equal((await reviewRows(s)).length, 0, 'پیش از کمبود هیچ');
  await sync(s, sale('r1-s', [['r1-i', 'r1', 1]]));
  await refresh(s);
  const rows = await reviewRows(s);
  assert.equal(rows.length, 1);
  assert.deepEqual([rows[0].order_id, Number(rows[0].order_no), rows[0].status], [o.id, Number(o.orderNo), 'pending']);

  await drain();
  const got = sentTo(s);
  assert.equal(got.length, 1, JSON.stringify(got.map((g) => g.message.title)));
  const m = got[0].message;
  assert.equal(got[0].row.token, s.pushToken, 'فقط گوشیِ همین دکان');
  assert.match(m.title, /بررسی/);
  assert.equal(m.channel, 'orders');
  assert.equal(m.data.event, 'order_review');
  assert.equal(m.data.orderId, o.id);
  //  همان کلیدهای دادهٔ رویدادهای یک‌بارهٔ پیشین — هیچ کلیدِ تازه‌ای برای دادهٔ مشتری
  assert.deepEqual(Object.keys(m.data).sort(), ['count', 'event', 'notifId', 'orderId', 'orderNo', 'reminder', 'shopId', 'type']);
  const text = JSON.stringify(m);
  for (const secret of [CUSTOMER.name, CUSTOMER.phone, '791234567', 'کالای r1', '5000', '50']) {
    assert.ok(!text.includes(secret), `بی دادهٔ مشتری/کالا/مبلغ: ${secret}`);
  }
  assert.equal((await one('SELECT status FROM order_notify_outbox WHERE id=$1', [rows[0].id])).status, 'delivered');
  //  دورِ دوم و سوم ⇒ هیچ
  await drain(now() + 60 * 60 * 1000);
  assert.equal(sentTo(s).length, 1);
  //  فهرستِ «در انتظار» ِ برنامه همان «سفارش‌های تازه» می‌ماند
  assert.equal((await N().pending(s.shopId)).filter((x) => x.event === 'order_review').length, 0);
});

test('⛔ هر سفارش یک بار: بازپخشِ همان فروش، خواندن‌های هم‌زمانِ دفتر و کمبودِ دوباره پس از پوشش ⇒ همان یک ردیف و یک پوش', async () => {
  const s = await shop('بررسی-یک‌بار');
  await enable(s);
  await stocked(s, 'u1', 2);
  const a = await order(s, [line('u1', 1000)]);
  const b = await order(s, [line('u1', 1000)]);
  //  دو فروشِ صندوق، سپس دو خواندنِ هم‌زمانِ دفتر (PostgreSQL ⇒ تراکنش‌های جدا زیرِ قفلِ موجودیِ دکان)
  const s1 = sale('u1-s1', [['u1-i1', 'u1', 1]]);
  await sync(s, s1);
  await sync(s, sale('u1-s2', [['u1-i2', 'u1', 1]]));
  await Promise.all([refresh(s), refresh(s), refresh(s)]);
  assert.deepEqual([(await getOrder(s, a.id)).stockHold.review, (await getOrder(s, b.id)).stockHold.review], [true, true]);
  const first = await reviewRows(s);
  assert.deepEqual(first.map((r) => r.order_id).sort(), [a.id, b.id].sort(), 'هر سفارش یک ردیف');

  //  بازپخشِ همان عملیات (شبکه پاسخ را گم کرد) ⇒ تکراری، بی ردیفِ دوم
  const again = await sync(s, s1);
  assert.ok(again.every((x) => x.status === 'duplicate'));
  await refresh(s);
  await drain();
  assert.equal(sentTo(s).length, 2);

  //  موجودی رسید ⇒ پوشش برگشت؛ فروشِ تازه ⇒ دوباره کمبود، ولی همان یک خبر برای هر سفارش
  await sync(s, [entry('u1-e2', 'u1', 2)]);
  await refresh(s);
  assert.deepEqual([(await getOrder(s, a.id)).stockHold.review, (await getOrder(s, b.id)).stockHold.review], [false, false]);
  await sync(s, sale('u1-s3', [['u1-i3', 'u1', 2]]));
  await refresh(s);
  assert.equal((await getOrder(s, b.id)).stockHold.review, true);
  assert.equal((await reviewRows(s)).length, 2);
  await drain();
  assert.equal(sentTo(s).length, 2, 'هرگز خبرِ دوم برای همان سفارش');
  const dup = await one(
    `SELECT COUNT(*)::int AS n FROM order_notify_outbox WHERE order_id = ANY($1::text[]) AND event='order_review'`, [[a.id, b.id]]
  );
  assert.equal(dup.n, 2);
});

test('بررسی پیش از فرستادن تمام شد (سفارش رد شد) ⇒ بی پوش؛ ردیف «رسیدگی شد» از وضعیت', async () => {
  const s = await shop('بررسی-بسته');
  await enable(s);
  await stocked(s, 'w1', 1);
  const o = await order(s, [line('w1', 1000)]);
  await sync(s, sale('w1-s', [['w1-i', 'w1', 1]]));
  await refresh(s);
  assert.equal((await reviewRows(s)).length, 1);
  assert.equal((await go(s, o.id, { to: 'rejected', reason: 'out_of_stock' })).status, 200);
  await drain();
  assert.equal(sentTo(s).length, 0);
  const row = (await reviewRows(s))[0];
  assert.deepEqual([row.status, row.ack_source], ['acked', 'order_status']);
});

test('⛔ دکان‌ها جدا: کمبودِ دکانِ الف فقط به گوشی‌های الف؛ نوشتن با شناسهٔ سفارشِ دکانِ دیگر هیچ', async () => {
  const A = await shop('بررسی-الف');
  const B = await shop('بررسی-ب');
  await enable(A);
  await enable(B);
  await stocked(A, 'x1', 1);
  await stocked(B, 'x1', 1);
  const oa = await order(A, [line('x1', 1000)]);
  await order(B, [line('x1', 1000)]);
  await sync(A, sale('x1-s', [['x1-i', 'x1', 1]]));
  await refresh(A);
  assert.equal((await reviewRows(A)).length, 1);
  assert.equal((await reviewRows(B)).length, 0, 'همان شناسهٔ کالا در دکانِ ب بررسی نمی‌شود');
  await drain();
  assert.equal(sentTo(A).length, 1);
  assert.equal(sentTo(B).length, 0);
  assert.ok(sent.every((m) => m.row.shop_id === m.message.data.shopId), 'هر پوش به گوشیِ دکانِ خودش');
  //  مستقیم: سفارشِ الف با شناسهٔ دکانِ ب ⇒ هیچ ردیفی
  const run = (t, p) => query(t, p);
  assert.equal(await N().enqueueReviewIn(run, { shopId: B.shopId, orderId: oa.id, at: now() }), null);
  assert.equal((await reviewRows(B)).length, 0);
});

test('مهاجرتِ ۵۲۰: CHECK ِ رویداد order_review را می‌پذیرد؛ rollback ردیف‌ها را پاک و CHECK ِ ۲۱۳ را برمی‌گرداند (خواندنِ دفتر باز هم سالم)؛ دوباره نشستن همان', async () => {
  const s = await shop('بررسی-مهاجرت');
  await enable(s);
  await stocked(s, 'm1', 1);
  const o = await order(s, [line('m1', 1000)]);
  const dir = nodePath.join(__dirname, '..', 'migrations');
  const name = '520_order_review_notify.sql';
  const up = fs.readFileSync(nodePath.join(dir, name), 'utf8');
  const down = fs.readFileSync(nodePath.join(dir, 'rollback', '520_order_review_notify.down.sql'), 'utf8');
  const mark = await one('SELECT * FROM schema_migrations WHERE version=$1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  const check = async () => (await one(
    "SELECT pg_get_constraintdef(oid) AS d FROM pg_constraint WHERE conname='order_notify_outbox_event_check'"
  )).d;
  assert.ok((await check()).includes('order_review'));
  assert.ok((await check()).includes('order_cancelled'), 'رویدادهای پیشین می‌مانند');
  await query(down);
  assert.ok(!(await check()).includes('order_review'), 'CHECK ِ پیشین برگشت');
  assert.ok((await check()).includes('order_paid'));
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM order_notify_outbox WHERE event='order_review'`)).n, 0);
  assert.equal(await one('SELECT 1 FROM schema_migrations WHERE version=$1', [name]), null);
  //  کدِ تازه روی CHECK ِ کهنه: خبر نوشته نمی‌شود ولی فروش، بررسی و خواندنِ دفتر همان (بی ۵۰۰، بی پس زدنِ تراکنش)
  await sync(s, sale('m1-s', [['m1-i', 'm1', 1]]));
  await refresh(s);
  assert.equal((await getOrder(s, o.id)).stockHold.review, true);
  assert.equal((await reviewRows(s)).length, 0);
  await query(up);
  await query(up);   //  دوباره بی‌خطا
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.ok((await check()).includes('order_review'));
});
