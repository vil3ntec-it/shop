'use strict';
/**
 * بازپرداختِ سفارشِ تحویل‌شده ⇒ سندِ برگشت در دفترِ دکان (RET-11، FIN-01، DATA-04) — روی PGlite/PG،
 * از راهِ HTTP ِ واقعی. سرور هیچ ردیفی در دفترِ دکان نمی‌نویسد (تصمیمِ صاحبِ مخزن)؛ فقط می‌گوید کدام
 * بازپرداخت هنوز سندِ برگشت ندارد (`GET /shop/orders/handover` ⇒ `refunds`) و برنامه
 * (`OrderFulfillment.refund`) سند و ردیفِ سابقهٔ ثابت را می‌نویسد.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) «بازپرداختِ کامل» ِ سفارشِ دارای فروش ⇒ یک رویدادِ `full` با کلِ مبلغ؛ پس از سند و سابقهٔ
 *      برنامه دیگر نه — حتی وقتی دستگاهِ دوم همان ردیف‌ها را دوباره بفرستد (یک ردیف، یک بار).
 *   ۲) دفترِ بازپرداخت (`shop.orders.refunds`): هر ردیف یک رویدادِ `r<seq>` با همان مبلغ؛ فقط
 *      رویدادِ ننشسته سفارش را در فهرست نگه می‌دارد.
 *   ۳) بازپرداختِ درگاه (`payment_status = refunded`) و «بازپرداختِ کامل» یک کلید‌اند؛ جمعِ رویدادها
 *      هرگز از کلِ سفارش بیشتر نیست ⇒ دفتر و درگاه دو بار برنمی‌گردانند.
 *   ۴) بی فروشِ `o-…` ِ زنده، سفارشِ آزمایشی، یا دکانِ دیگر ⇒ هیچ.
 *   ۵) اجازه: شاگردِ بی اشتراکِ چنددستگاهی ⇒ `refundable` نه.
 *   ۶) ⛔ پرچمِ `shop.orders.refunds` خاموش (پیش‌فرض) ⇒ `refunds` همیشه خالی، حتی برای گذارِ سادهٔ
 *      «بازپرداختِ کامل» یا بازپرداختِ درگاه — برنامهٔ نصب‌شده بی پرچم سندِ برگشت نمی‌نویسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const ledger = require('../src/lib/order-ledger');
const { ulid } = require('../src/lib/ulid');

const DEV_A = 'dev-refund-a';
const DEV_B = 'dev-refund-b';
let admin = '';
let opSeq = 0;
const opId = () => `01R${String(Date.now()).slice(-9)}${String(++opSeq).padStart(5, '0')}`;

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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: device, schema_version: 3, ops }, headers: { 'X-App': 'shop', 'X-Device': device },
});

/** دکان + صاحب با توکنِ دستگاه، پرچمِ سفارش و کفِ «فروشِ تحویل» */
async function shop(name, { refundsOn = false } = {}) {
  let u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  u = { ...u, ...(await h.api('POST', '/api/auth/login', {
    body: { identifier: u.email, password: u.password, app: 'shop', device: { deviceId: DEV_A, name: 'A', platform: 'test' } },
  })).body };
  await setFlag(shopId, 'shop.orders', true);
  if (refundsOn) await setFlag(shopId, 'shop.orders.refunds', true);
  const cfg = await h.get('/api/shop/orders/config?caps=saleOnHandover', { token: u.accessToken });
  assert.ok(cfg.body.handoverSince > 0, JSON.stringify(cfg.body));
  return { ...u, token: u.accessToken, shopId };
}

/** «بازپرداختِ کامل» با گذارِ ساده فقط با پرچمِ خاموش ممکن است (روشن ⇒ ۴۰۹ refund_via_ledger)؛ سپس روشن */
const refundsOn = (owner) => setFlag(owner.shopId, 'shop.orders.refunds', true);

let keySeq = 0;
async function newOrder(owner, over = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد' },
    lines: [
      { productId: 'p-rice', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 12000 },
      { name: 'کیسه', qtyMilli: 1000, unitPriceMinor: 500 },
    ],
    ...over,
  }, { token: owner.token, headers: { 'Idempotency-Key': `refund-order-${++keySeq}-${Date.now()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

async function walk(owner, id, steps) {
  for (const to of steps) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: owner.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}
const TO_PICKED_UP = ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'];

/** فروشِ تحویلِ برنامه (`OrderFulfillment.fulfill`) — فقط ردیفِ فروش کافی است */
function saleOps(order, at = Date.now()) {
  return [{ op_id: opId(), ts: at, table: 'sales', row_id: ledger.saleIdOf(order.id), type: 'insert', fields: {
    invoiceNumber: order.orderNo, series: 'O', total: (order.subtotalMinor + order.deliveryFeeMinor) / 100,
    discount: order.discountMinor / 100, finalTotal: order.totalMinor / 100, paymentMethod: 'cash',
    paidAmount: order.totalMinor / 100, remaining: 0, status: 'completed', debtGiven: 0, createdAt: at,
    date: '2026-10-19', channel: 'online', orderId: order.id, orderNo: order.orderNo,
  } }];
}

/** سند و سابقهٔ برنامه برای رویدادِ [e] — همان `OrderFulfillment.refund` (شناسه‌های ثابت) */
function refundOps(order, e) {
  const docId = ledger.refundDocIdOf(order.id, e.key);
  const ops = [];
  if (e.amountMinor > 0) {
    ops.push({ op_id: opId(), ts: e.at, table: 'returns', row_id: `${docId}-0`, type: 'insert', fields: {
      saleId: ledger.saleIdOf(order.id), saleItemId: '', productId: '', quantity: 0, amount: e.amountMinor / 100,
      reason: 'بازپرداختِ سفارشِ اینترنتی', date: '2026-10-25', createdAt: e.at, docId, refund: 'original',
    } });
  }
  ops.push({ op_id: opId(), ts: e.at, table: 'auditLog', row_id: ledger.refundMarkOf(order.id, e.key), type: 'insert', fields: {
    type: 'return', date: '2026-10-25', refId: ledger.saleIdOf(order.id), notes: 'بازپرداخت', createdAt: e.at, amount: e.amountMinor / 100,
  } });
  return ops;
}

const pending = async (owner, orderId = '') => {
  const r = await h.get(`/api/shop/orders/handover?since=0${orderId ? `&orderId=${orderId}` : ''}`, { token: owner.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
};
const shopRows = (shopId, table, rowId) => one(
  `SELECT COUNT(*)::int AS n FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`,
  [shopId, table, rowId]
);

/* ══════════════════════════════════════════════════════════════════ */

test('refundEventsOf: ترتیبِ زمان؛ هم‌زمان ⇒ ردیف‌ها پیش از full؛ جمع هرگز بیش از کلِ سفارش', () => {
  const E = ledger.refundEventsOf;
  assert.deepEqual(E(9000, [], { at: 5, source: 'status' }), [{ key: 'full', amountMinor: 9000, at: 5, source: 'status' }]);
  assert.deepEqual(
    E(9000, [{ seq: 2, amountMinor: 1000, at: 7 }, { seq: 1, amountMinor: 3000, at: 7 }], { at: 7, source: 'status' })
      .map((e) => [e.key, e.amountMinor]),
    [['r1', 3000], ['r2', 1000], ['full', 5000]]
  );
  //  درگاه پیش از ردیفِ دفتر ⇒ ردیفِ دیرتر صفر (فقط سابقه)
  assert.deepEqual(
    E(9000, [{ seq: 1, amountMinor: 4000, at: 9 }], { at: 3, source: 'gateway' }).map((e) => [e.key, e.amountMinor]),
    [['full', 9000], ['r1', 0]]
  );
  assert.deepEqual(E(9000, [{ seq: 1, amountMinor: 9500, at: 1 }]).map((e) => e.amountMinor), [9000]);
  assert.deepEqual(E(0, [], null), []);
});

test('«بازپرداختِ کامل» ِ سفارشِ دارای فروش ⇒ یک رویدادِ full؛ سند و سابقهٔ برنامه ⇒ دیگر نه؛ دستگاهِ دوم ⇒ یک ردیف', async () => {
  const a = await shop('بازپرداختِ کامل');
  const order = await newOrder(a);
  await walk(a, order.id, TO_PICKED_UP);
  assert.equal((await push(a.token, DEV_A, saleOps(order))).status, 200);
  assert.deepEqual((await pending(a)).refunds, [], 'پیش از بازپرداخت هیچ');
  await walk(a, order.id, ['return_requested', 'refunded_full']);
  //  ⛔ پرچم خاموش ⇒ هیچ رویدادی، حتی برای سفارشِ بازپرداخت‌شده با فروشِ زنده
  assert.deepEqual((await pending(a)).refunds, [], 'بی shop.orders.refunds هیچ');
  await refundsOn(a);

  let r = await pending(a);
  assert.equal(r.refunds.length, 1, JSON.stringify(r));
  const row = r.refunds[0];
  assert.equal(row.id, order.id);
  assert.equal(row.action, 'refund');
  assert.equal(row.saleId, `o-${order.id}`);
  assert.equal(row.refundable, true);
  assert.equal(row.refundEvents.length, 1);
  const e = row.refundEvents[0];
  assert.equal(e.key, 'full');
  assert.equal(e.source, 'status');
  assert.equal(e.amountMinor, order.totalMinor);
  const fullAt = (await h.get(`/api/shop/orders/${order.id}`, { token: a.token })).body.order.events
    .find((x) => x.kind === 'status' && x.toStatus === 'refunded_full').at;
  assert.equal(e.at, fullAt, 'زمانِ رویداد = زمانِ همان بازپرداخت (روزِ گزارش)');
  //  ⛔ سرور خودش در دفترِ دکان چیزی ننوشته
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM oplog WHERE app='shop' AND account_id=$1 AND device_id='server'`, [a.shopId])).n, 0);

  //  برنامه سند و سابقه را می‌نویسد ⇒ دیگر در فهرست نیست
  assert.equal((await push(a.token, DEV_A, refundOps(order, e))).status, 200);
  assert.deepEqual((await pending(a)).refunds, []);
  //  دستگاهِ دوم (یا تلاشِ دوباره) همان شناسه‌ها ⇒ همان یک ردیف
  const again = await push(a.token, DEV_B, refundOps(order, e));
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal((await shopRows(a.shopId, 'returns', `${ledger.refundDocIdOf(order.id, 'full')}-0`)).n, 1);
  assert.equal((await shopRows(a.shopId, 'auditLog', ledger.refundMarkOf(order.id, 'full'))).n, 1);
  r = await pending(a, order.id);
  assert.deepEqual([r.items, r.reversals, r.refunds], [[], [], []]);
});

test('دفترِ بازپرداخت: هر ردیف یک رویدادِ r<seq> با همان مبلغ؛ فقط رویدادِ ننشسته سفارش را نگه می‌دارد', async () => {
  const a = await shop('بازپرداختِ جزئی', { refundsOn: true });
  const order = await newOrder(a);
  await walk(a, order.id, [...TO_PICKED_UP, 'return_requested']);
  assert.equal((await push(a.token, DEV_A, saleOps(order))).status, 200);
  const refund = (amountMinor, final, k) => h.post(`/api/shop/orders/${order.id}/refunds`,
    { amountMinor, reason: 'کالای خراب', final }, { token: a.token, headers: { 'Idempotency-Key': `rf-${k}-${order.id}` } });
  assert.equal((await refund(3000, false, 1)).status, 201);
  let r = await pending(a);
  assert.deepEqual(r.refunds[0].refundEvents.map((x) => [x.key, x.amountMinor, x.source]), [['r1', 3000, 'ledger']]);
  //  برنامه r1 را نشاند
  assert.equal((await push(a.token, DEV_A, refundOps(order, r.refunds[0].refundEvents[0]))).status, 200);
  assert.deepEqual((await pending(a)).refunds, []);
  //  ردیفِ دوم با «پایانی» ⇒ refunded_partial و رویدادِ r2 (r1 همچنان در فهرستِ رویدادها، برنامه ردش می‌کند)
  const second = await refund(1750, true, 2);
  assert.equal(second.status, 201, JSON.stringify(second.body));
  assert.equal(second.body.order.status, 'refunded_partial');
  r = await pending(a);
  assert.deepEqual(r.refunds[0].refundEvents.map((x) => [x.key, x.amountMinor]), [['r1', 3000], ['r2', 1750]]);
  //  همان درخواست با همان کلید ⇒ ردیفِ سوم نه
  assert.equal((await refund(1750, true, 2)).status, 200);
  assert.equal((await pending(a)).refunds[0].refundEvents.length, 2);
  assert.equal((await push(a.token, DEV_A, refundOps(order, r.refunds[0].refundEvents[1]))).status, 200);
  assert.deepEqual((await pending(a)).refunds, []);
  //  refunded_partial کالا را بیرون نگه می‌دارد ⇒ فروش می‌ماند (نه در reversals)
  assert.deepEqual((await pending(a)).reversals, []);
});

test('بازپرداختِ درگاه و «بازپرداختِ کامل» یک کلید؛ دفتر + درگاه ⇒ جمع هرگز بیش از کلِ سفارش', async () => {
  const a = await shop('درگاه', { refundsOn: true });
  const order = await newOrder(a);
  await walk(a, order.id, [...TO_PICKED_UP, 'return_requested']);
  assert.equal((await push(a.token, DEV_A, saleOps(order))).status, 200);
  const r1 = await h.post(`/api/shop/orders/${order.id}/refunds`, { amountMinor: 2000, reason: 'تأخیر' },
    { token: a.token, headers: { 'Idempotency-Key': `rf-gw-${order.id}` } });
  assert.equal(r1.status, 201, JSON.stringify(r1.body));
  //  همان کارِ `payment-intents.markRefunded` (PR ِ حساب‌پی): وضعیتِ پرداخت + رویدادِ payment
  const at = now();
  await query(`UPDATE online_orders SET payment_status='refunded', updated_at=$3 WHERE shop_id=$1 AND id=$2`, [a.shopId, order.id, at]);
  const seq = Number((await one('SELECT COALESCE(MAX(seq),0)+1 AS n FROM online_order_events WHERE order_id=$1', [order.id])).n);
  await query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status, actor_type, actor_id, reason, note, detail, created_at)
     VALUES ($1,$2,$3,$4,'payment','paid','refunded','system','','refunded_by_admin','','{}'::jsonb,$5)`,
    [ulid(at), order.id, a.shopId, seq, at]
  );
  let ev = (await pending(a)).refunds[0].refundEvents;
  assert.deepEqual(ev.map((x) => [x.key, x.amountMinor, x.source]), [['r1', 2000, 'ledger'], ['full', order.totalMinor - 2000, 'gateway']]);
  //  دکان هم «بازپرداختِ کامل» می‌زند (با دفتر: مبلغِ مانده) ⇒ هنوز همان یک کلیدِ full، جمع = کلِ سفارش
  const rest = await h.post(`/api/shop/orders/${order.id}/refunds`, { amountMinor: order.totalMinor - 2000, reason: 'بقیه' },
    { token: a.token, headers: { 'Idempotency-Key': `rf-gw2-${order.id}` } });
  assert.equal(rest.status, 201, JSON.stringify(rest.body));
  assert.equal(rest.body.order.status, 'refunded_full');
  ev = (await pending(a)).refunds[0].refundEvents;
  assert.deepEqual(ev.map((x) => x.key), ['r1', 'full', 'r2']);
  assert.equal(ev.filter((x) => x.key === 'full').length, 1, 'درگاه و گذار یک کلید');
  assert.equal(ev.reduce((s, x) => s + x.amountMinor, 0), order.totalMinor, 'دو بار برنمی‌گردد');
  assert.equal(ev.find((x) => x.key === 'r2').amountMinor, 0, 'ردیفِ دیرتر فقط سابقه');
  for (const e of ev) assert.equal((await push(a.token, DEV_A, refundOps(order, e))).status, 200);
  assert.deepEqual((await pending(a)).refunds, []);
});

test('بی فروشِ o-… ِ زنده، فروشِ لغوشده، سفارشِ آزمایشی یا دکانِ دیگر ⇒ هیچ رویدادی', async () => {
  const a = await shop('بی‌فروش');
  const b = await shop('دکانِ دیگر');
  //  بی فروش: بازپرداخت در فهرستِ بازپرداخت نیست (فروشِ تحویل اول باید ثبت شود)
  //  (گذارِ سادهٔ «بازپرداختِ کامل» فقط با پرچمِ خاموش؛ پس همه پیش از روشن کردن)
  const noSale = await newOrder(a);
  await walk(a, noSale.id, [...TO_PICKED_UP, 'return_requested', 'refunded_full']);
  //  فروشِ لغوشده (برگشت به دکان پیش‌تر) ⇒ نه
  const cancelled = await newOrder(a);
  await walk(a, cancelled.id, [...TO_PICKED_UP, 'return_requested', 'refunded_full']);
  const ops = saleOps(cancelled);
  ops[0].fields.status = 'cancelled';
  assert.equal((await push(a.token, DEV_A, ops)).status, 200);
  //  آزمایشی ⇒ هرگز
  const t = await newOrder(a, { test: true });
  await walk(a, t.id, [...TO_PICKED_UP, 'return_requested', 'refunded_full']);
  assert.equal((await push(a.token, DEV_A, saleOps(t))).status, 200);
  await refundsOn(a);
  await refundsOn(b);
  assert.deepEqual((await pending(a)).refunds.map((x) => x.id), [], 'بی فروش، فروشِ لغوشده و آزمایشی ⇒ هیچ');
  //  دکانِ دیگر: سفارش و فروشِ A را نمی‌بیند؛ ردیف‌های B فهرستِ A را عوض نمی‌کنند
  await setFlag(a.shopId, 'shop.orders.refunds', false);
  const real = await newOrder(a);
  await walk(a, real.id, [...TO_PICKED_UP, 'return_requested', 'refunded_full']);
  assert.equal((await push(a.token, DEV_A, saleOps(real))).status, 200);
  await refundsOn(a);
  assert.deepEqual((await pending(a)).refunds.map((x) => x.id), [real.id]);
  assert.deepEqual((await pending(b)).refunds, []);
  assert.deepEqual((await pending(b, real.id)).refunds, []);
  const e = (await pending(a)).refunds[0].refundEvents[0];
  assert.equal((await push(b.token, DEV_A, refundOps(real, e))).status, 200);
  assert.deepEqual((await pending(a)).refunds.map((x) => x.id), [real.id], 'سابقهٔ دکانِ دیگر حساب نیست');
});

test('اجازه: شاگرد با دورهٔ آزمایشی ⇒ refundable؛ بی اشتراکِ چنددستگاهی ⇒ نه (همان requireSyncWrite)؛ صاحب همیشه', async () => {
  const a = await shop('اجازهٔ بازپرداخت');
  const order = await newOrder(a);
  await walk(a, order.id, [...TO_PICKED_UP, 'return_requested', 'refunded_full']);
  assert.equal((await push(a.token, DEV_A, saleOps(order))).status, 200);
  await refundsOn(a);
  const u = await h.newUser('شاگردِ بازپرداخت', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), a.shopId, u.user.id, now()]
  );
  const flag = async (token) => (await h.get('/api/shop/orders/handover?since=0', { token })).body.refunds[0].refundable;
  assert.equal(await flag(u.accessToken), true, 'با دورهٔ آزمایشی');
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [a.shopId, Date.now() - 400 * 24 * 3600 * 1000]);
  assert.equal(await flag(u.accessToken), false);
  assert.equal(await flag(a.token), true, 'صاحب بی اشتراک هم می‌نویسد');
});

test('⛔ پرچم خاموش: بازپرداختِ درگاه هم سندِ برگشت نمی‌خواهد؛ روشن ⇒ همان یک رویدادِ full', async () => {
  const a = await shop('درگاهِ بی پرچم');
  const order = await newOrder(a);
  await walk(a, order.id, TO_PICKED_UP);
  assert.equal((await push(a.token, DEV_A, saleOps(order))).status, 200);
  await query(`UPDATE online_orders SET payment_status='refunded', updated_at=$3 WHERE shop_id=$1 AND id=$2`, [a.shopId, order.id, now()]);
  let r = await pending(a);
  assert.deepEqual(r.refunds, [], 'بی shop.orders.refunds هیچ');
  assert.deepEqual((await pending(a, order.id)).refunds, []);
  await refundsOn(a);
  r = await pending(a);
  assert.deepEqual(r.refunds[0].refundEvents.map((x) => [x.key, x.amountMinor, x.source]), [['full', order.totalMinor, 'gateway']]);
  //  دوباره خاموش ⇒ فهرست دوباره خالی (کلیدِ قطعِ فوری)
  await setFlag(a.shopId, 'shop.orders.refunds', false);
  assert.deepEqual((await pending(a)).refunds, []);
});
