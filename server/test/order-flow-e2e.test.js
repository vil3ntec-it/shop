'use strict';
/**
 * جریانِ سرتاسریِ سفارشِ آنلاین با درهای واقعیِ سرور، پشتِ هم (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۳، #910):
 *   ویترین ⇐ `POST /store/:slug/checkout` ⇐ صفِ برنامهٔ دکان (`/shop/orders/summary`، `GET /shop/orders`) ⇐ «قبول» ⇐
 *   کارتِ «پرداخت» ِ پیگیری (نقد + حساب‌پی) ⇐ «به فاکتور» ⇐ پیک (سپردن، تحویل با نقد، تسویه).
 * `order-flow.test.js` هر در را جدا قفل می‌کند؛ این‌جا همان درها در ترتیبِ واقعی و لبه‌ها:
 *   ۱) زنجیرهٔ کامل با پیک؛ مشتری هر بار پیگیری را از نو می‌خواند (برگشتن/تازه کردن) و همان را می‌بیند؛
 *      دو بار زدنِ «قبول» و «نقد» (هم‌زمان) ⇒ یک رویداد.
 *   ۲) قیمتِ کالا میانِ ثبت و قبول عوض شد ⇒ مبلغِ پرداخت، قصدِ حساب‌پی و قلم‌های فاکتور همان قیمتِ سفارش.
 *   ۳) فروشنده رد کرد ⇒ کارتِ «پرداخت» بسته، نه نقد، نه حساب‌پی، نه فاکتور.
 *   ۴) دو بار زدنِ حساب‌پی (هم‌زمان، دو کلید) ⇒ یک قصد.
 *   ۵) فروشندهٔ آفلاین ⇒ سفارش پایدار ثبت و هرگز خودکار رد نمی‌شود؛ با برگشتنِ دستگاه در صف است. دکانِ برخطی که
 *      ۱۵ دقیقه پاسخ نداد ⇒ «ردِ خودکار» و کارتِ «پرداخت» ِ بسته.
 * (فقط دکانِ مشترک: `orders-subscribers-only.test.js` و SUB-10 ِ `paid-routes-*`؛ حساب‌پی فقط با اشتراکِ پولی: `payment-storefront.test.js`.)
 */
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-order-e2e';
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.CATALOG_REFRESH_THROTTLE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'e2e-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'e2e-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const flag = (scope, key, value = true) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: 'dev-e2e', schema_version: 3, ops }, headers: { 'X-App': 'shop', 'X-Device': 'dev-e2e' },
});
const priceOp = (price, type = 'insert') => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type,
  fields: { name: 'برنج', unit: 'کیلو', salePrice: price, category: 'خوراکی', active: true },
});

/** دکانِ مشترک با ویترین، «پرداخت پس از پذیرش» ِ روشن، حساب‌پی (درگاهِ ساختگی) و تحویل با پیک */
async function liveShop(label) {
  const u = await h.newUser(`صاحب ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'pay.hesabpay']) await flag(s.shopId, k);
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  s.slug = `e2e-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', payAfterAccept: true,
  }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal(put.body.settings.payAfterAccept, true);
  assert.equal((await push(s.token, [priceOp(120)])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

let phoneSeq = 0;
/** مشتری از ویترین سفارش می‌دهد (تأییدِ شماره ⇐ ثبت) — همان درهای `store/*.js` */
async function storeOrder(s, { fulfillment = 'delivery', qtyMilli = 2000 } = {}) {
  const phone = `07977${String(++phoneSeq).padStart(5, '0')}`;
  const v = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(phone), code: v.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const body = { fulfillment, customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli }] };
  if (fulfillment === 'delivery') body.address = { text: 'کابل، کارتهٔ سه', landmark: 'کنارِ مسجد', lat: 34.523456, lng: 69.181234 };
  const ck = await h.post(`/api/store/${s.slug}/checkout`, body,
    { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, ck.body.order.orderNo])).id;
  return { id, token: ck.body.order.token, orderNo: ck.body.order.orderNo, totalMinor: ck.body.order.totalMinor };
}

const track = async (s, o) => {
  const r = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order;
};
const choose = (s, o, body = { method: 'cash' }) => h.post(`/api/store/${s.slug}/orders/${o.token}/pay-choice`, body);
const pay = (s, o, key = `pay-${uniq()}-key`) => h.post(`/api/store/${s.slug}/orders/${o.token}/pay`, {}, { headers: { 'Idempotency-Key': key } });
const move = (s, o, to, extra = {}) => h.post(`/api/shop/orders/${o.id}/transition`, { to, ...extra }, { token: s.token });
async function walk(s, o, steps) {
  for (const to of steps) {
    const r = await move(s, o, to);
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}
const invoice = (s, o) => h.post(`/api/shop/orders/${o.id}/invoice`, {}, { token: s.token });
const detail = async (s, o) => (await h.get(`/api/shop/orders/${o.id}`, { token: s.token })).body.order;
const statusEvents = (o, to) => many(`SELECT 1 FROM online_order_events WHERE order_id=$1 AND kind='status' AND to_status=$2`, [o.id, to]);
const notes = (o, type) => many(`SELECT 1 FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'=$2`, [o.id, type]);
const intents = (o) => many(`SELECT id, amount_minor FROM payment_intents WHERE kind='online_order' AND ref_id=$1`, [o.id]);
/** کارتِ «پرداخت» ِ پیگیری بی مبلغ و روشِ تحویل (برای سنجشِ حال) */
const stepOf = (tr) => ({ state: tr.paymentStep.state, choice: tr.paymentStep.choice, canChoose: tr.paymentStep.canChoose, online: tr.paymentStep.online });
/** صفِ «تازه» ِ برنامهٔ دکان — همان دو پرسشِ `OrdersLive` (شمارش + برگه) */
async function sellerQueue(s) {
  const sum = await h.get('/api/shop/orders/summary', { token: s.token });
  assert.equal(sum.status, 200, JSON.stringify(sum.body));
  const list = await h.get('/api/shop/orders?tab=new', { token: s.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  return { fresh: sum.body.counts.new, ids: (list.body.items || list.body.orders || []).map((x) => x.id) };
}

async function addCourier(s, name) {
  const u = await h.newUser(`پیک ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), s.shopId, u.user.id, now()]
  );
  const r = await h.post('/api/shop/delivery/couriers', { userId: u.user.id, name, phone: '0799999999' }, { token: s.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: u.accessToken, courierId: r.body.courier.id };
}
let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra });
async function courierSays(courier, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-e2e', events }, { token: courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

/* ══════════════════════════════════════════════════════════════════ */

test('سرتاسری: ویترین ⇐ صفِ فروشنده ⇐ قبول ⇐ «نقد» ِ مشتری ⇐ فاکتورِ یک‌ضرب ⇐ پیک نقد را می‌گیرد و تسویه می‌کند', async () => {
  const s = await liveShop('سرتاسری');
  const courier = await addCourier(s, 'کریم');
  const o = await storeOrder(s);
  assert.equal(o.totalMinor, 24000, 'دو کیلو × ۱۲۰ افغانی از دفترِ سرور');

  //  ۱) سفارش به فروشنده می‌رسد: «چرا نمی‌رسد؟» سبز، شمارشِ «تازه» و همان سفارش در برگه
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.enabled, true);
  assert.deepEqual(cfg.arrival.blockers, [], JSON.stringify(cfg.arrival));
  const q = await sellerQueue(s);
  assert.equal(q.fresh, 1);
  assert.deepEqual(q.ids, [o.id]);
  assert.deepEqual((await detail(s, o)).payStep, { mode: 'after_accept', state: 'awaiting_accept', choice: '' });

  //  ۲) مشتری پیش از قبول — و همان پس از تازه کردن/برگشتن
  const t0 = await track(s, o);
  assert.equal(t0.status, 'new');
  assert.deepEqual(stepOf(t0), { state: 'awaiting_accept', choice: '', canChoose: false, online: false });
  assert.equal(t0.onlinePay.available, false);
  assert.deepEqual(await track(s, o), t0, 'خواندنِ دوباره همان پاسخ (هیچ اثرِ جانبی)');

  //  ۳) فروشنده دو بار (هم‌زمان) «قبول» می‌زند ⇒ یک گذار
  const both = await Promise.all([move(s, o, 'accepted'), move(s, o, 'accepted')]);
  assert.deepEqual(both.map((r) => r.status), [200, 200], JSON.stringify(both.map((r) => r.body)));
  assert.deepEqual(both.map((r) => r.body.unchanged === true).sort(), [false, true]);
  assert.equal((await statusEvents(o, 'accepted')).length, 1);

  //  ۴) مشتری برمی‌گردد ⇒ کارتِ «پرداخت»: نقد یا حساب‌پی، مبلغ از سرور
  const t1 = await track(s, o);
  assert.deepEqual(stepOf(t1), { state: 'awaiting_choice', choice: '', canChoose: true, online: true });
  assert.equal(t1.paymentStep.amountMinor, 24000);
  assert.equal(t1.paymentStep.cash, 'delivery', 'نقد را پیک می‌گیرد');
  assert.equal(t1.onlinePay.available, true);

  //  ۵) مشتری دو بار (هم‌زمان) «نقد» ⇒ یک انتخاب
  const two = await Promise.all([choose(s, o), choose(s, o)]);
  assert.deepEqual(two.map((r) => r.status), [200, 200], JSON.stringify(two.map((r) => r.body)));
  assert.deepEqual(two.map((r) => r.body.changed).sort(), [false, true]);
  assert.equal((await notes(o, 'pay_choice')).length, 1);
  const t2 = await track(s, o);
  assert.equal(t2.paymentStep.state, 'cash');
  assert.equal(t2.paymentStep.choice, 'cash');
  assert.deepEqual((await detail(s, o)).payStep, { mode: 'after_accept', state: 'cash', choice: 'cash' });

  //  ۶) «بسته شد و فاکتور» ِ یک‌ضرب: همهٔ قلم‌ها با قیمتِ سفارش، یک فاکتور؛ پرداختِ آنلاین دیگر باز نیست
  await walk(s, o, ['picking', 'packed']);
  const inv = await invoice(s, o);
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  assert.equal(inv.body.invoice.saleId, `o-${o.id}`);
  assert.deepEqual(inv.body.order.lines.map((l) => [l.productId, l.qtyMilli, l.unitPriceMinor]), [['rice', 2000, 12000]]);
  assert.equal(inv.body.order.totalMinor, 24000);
  assert.equal((await invoice(s, o)).body.created, false, 'دو بار زدن ⇒ همان فاکتور');
  assert.equal((await pay(s, o)).body.error.code, 'order_invoiced');
  assert.equal((await track(s, o)).onlinePay.available, false);

  //  ۷) پیک: سپردن ⇐ گرفتن ⇐ رسیدن ⇐ تحویل با همهٔ نقد
  const a = await h.post(`/api/shop/delivery/orders/${o.id}/assign`,
    { mode: 'shop_courier', courierId: courier.courierId, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const aid = a.body.assignment.id;
  await courierSays(courier, [ev(aid, 'assigned'), ev(aid, 'picked_up'), ev(aid, 'moving'), ev(aid, 'arrived')]);
  assert.equal((await track(s, o)).status, 'out_for_delivery');
  await courierSays(courier, [ev(aid, 'delivered', { cashCollectedMinor: 24000 })]);
  const t3 = await track(s, o);
  assert.equal(t3.status, 'delivered');
  assert.deepEqual(stepOf(t3), { state: 'cash_collected', choice: 'cash', canChoose: false, online: false });
  const d3 = await detail(s, o);
  assert.equal(d3.codStatus, 'collected_by_courier');
  assert.equal(d3.payStep.state, 'cash_collected');
  assert.equal((await choose(s, o)).body.error.code, 'payment_closed', 'پس از تحویل انتخابی نیست');

  //  ۸) تسویهٔ پیک ⇒ «نقد به دکان رسید» برای هر دو
  const st = await h.post(`/api/shop/delivery/couriers/${courier.courierId}/settlements`,
    { assignmentIds: [aid], handedOverMinor: 24000, receivedMinor: 24000 },
    { token: s.token, headers: { 'Idempotency-Key': `st-${uniq()}-key` } });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  assert.equal((await track(s, o)).paymentStep.state, 'cash_settled');
  assert.equal((await detail(s, o)).payStep.state, 'cash_settled');
  assert.equal((await intents(o)).length, 0, 'هیچ قصدِ حساب‌پی در راهِ نقد');
  assert.equal((await notes(o, 'invoice')).length, 1);
});

test('قیمت میانِ ثبت و قبول عوض شد ⇒ پرداخت، حساب‌پی و فاکتور همان قیمتِ سفارش؛ سفارشِ تازه قیمتِ تازه', async () => {
  const s = await liveShop('قیمتِ تازه');
  const cashOrder = await storeOrder(s);
  const onlineOrder = await storeOrder(s, { fulfillment: 'pickup' });
  assert.equal(cashOrder.totalMinor, 24000);

  //  فروشنده قیمت را در دفتر بالا برد و همگام کرد
  assert.equal((await push(s.token, [priceOp(150, 'update')])).status, 200);
  const later = await storeOrder(s);
  assert.equal(later.totalMinor, 30000, 'قیمتِ تازه واقعاً رسید: سفارشِ تازه با ۱۵۰');

  //  سفارش‌های پیشین همان قیمتی را دارند که مشتری در ویترین دید و پذیرفت
  for (const o of [cashOrder, onlineOrder]) {
    await walk(s, o, ['accepted']);
    const tr = await track(s, o);
    assert.equal(tr.totalMinor, 24000);
    assert.equal(tr.paymentStep.amountMinor, 24000);
    assert.deepEqual(tr.lines.map((l) => l.unitPriceMinor), [12000]);
  }
  const p = await pay(s, onlineOrder);
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal(p.body.payment.amountMinor, 24000, 'قصدِ حساب‌پی با مبلغِ سفارش، نه قیمتِ امروزِ دفتر');
  assert.deepEqual((await intents(onlineOrder)).map((x) => Number(x.amount_minor)), [24000]);

  assert.equal((await choose(s, cashOrder)).status, 200);
  await walk(s, cashOrder, ['picking', 'packed']);
  const inv = await invoice(s, cashOrder);
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  assert.deepEqual(inv.body.order.lines.map((l) => [l.unitPriceMinor, l.lineTotalMinor]), [[12000, 24000]],
    'برنامه فروش را از همین قلم‌ها می‌نویسد (`OrderFulfillment.fulfill`)');
  assert.equal(inv.body.order.totalMinor, 24000);
});

test('فروشنده رد کرد ⇒ کارتِ «پرداخت» بسته: نه نقد، نه حساب‌پی، نه فاکتور — و همان پس از برگشتنِ مشتری', async () => {
  const s = await liveShop('رد');
  const o = await storeOrder(s);
  const bad = await move(s, o, 'rejected');
  assert.equal(bad.status, 400, 'رد بی دلیل نه');
  const r1 = await move(s, o, 'rejected', { reason: 'out_of_stock' });
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  const r2 = await move(s, o, 'rejected', { reason: 'out_of_stock' });
  assert.equal(r2.body.unchanged, true, 'دو بار زدن ⇒ یک گذار');
  assert.equal((await statusEvents(o, 'rejected')).length, 1);

  for (let i = 0; i < 2; i++) {
    const tr = await track(s, o);
    assert.equal(tr.status, 'rejected');
    assert.deepEqual(stepOf(tr), { state: 'closed', choice: '', canChoose: false, online: false });
    assert.equal(tr.onlinePay.available, false);
  }
  const c = await choose(s, o);
  assert.equal(c.status, 409, JSON.stringify(c.body));
  assert.equal(c.body.error.code, 'payment_closed');
  const p = await pay(s, o);
  assert.equal(p.status, 409, JSON.stringify(p.body));
  assert.equal(p.body.error.code, 'order_not_payable');
  assert.equal((await intents(o)).length, 0);
  assert.equal((await invoice(s, o)).body.error.code, 'invoice_not_ready');
  assert.equal((await detail(s, o)).payStep.state, 'closed');
  assert.equal((await move(s, o, 'accepted')).status, 409, 'ردشده دوباره قبول نمی‌شود');
});

test('دو بار زدنِ «پرداخت با حساب‌پی» (هم‌زمان، دو کلید) ⇒ یک قصد و یک صفحهٔ پرداخت', async () => {
  const s = await liveShop('دو ضرب');
  const o = await storeOrder(s);
  await walk(s, o, ['accepted']);
  const both = await Promise.all([pay(s, o), pay(s, o)]);
  assert.deepEqual(both.map((r) => r.status).sort(), [200, 201], JSON.stringify(both.map((r) => r.body)));
  const rows = await intents(o);
  assert.equal(rows.length, 1, 'یک قصد');
  assert.equal(Number(rows[0].amount_minor), 24000);
  //  ضربهٔ سوم پس از آرام شدن ⇒ همان صفحهٔ پرداخت
  const first = both.find((r) => r.status === 201).body.payment;
  const third = await pay(s, o);
  assert.equal(third.status, 200, JSON.stringify(third.body));
  assert.equal(third.body.payment.checkoutUrl, first.checkoutUrl);
  const tr = await track(s, o);
  assert.equal(tr.paymentStep.state, 'online_pending');
  assert.equal(tr.paymentStep.canChoose, false, 'در جریانِ حساب‌پی انتخابِ نقد بسته');
});

test('فروشندهٔ آفلاین ⇒ سفارش پایدار و هرگز خودکار رد نمی‌شود؛ با برگشتنِ دستگاه در صف است — برخطِ بی‌پاسخ ⇒ «ردِ خودکار»', async () => {
  const { offlineMs } = require('../src/config').storefront;
  const timeout = require('../src/lib/order-timeout');
  const s = await liveShop('آفلاین');
  const seen = (t) => query(`UPDATE sync_devices SET last_seen_at=$2 WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [s.shopId, t]);

  //  برخط: سفارشی که دکان ۱۵ دقیقه به آن پاسخ نداد ⇒ «ردِ خودکار» و مشتری کارتِ بسته را می‌بیند
  const live = await storeOrder(s);
  const armed = await one('SELECT accept_deadline_at FROM online_orders WHERE id=$1', [live.id]);
  assert.ok(Number(armed.accept_deadline_at) > 0, 'دکانِ برخط ⇒ مهلتِ قبول');

  //  دستگاهِ دکان یک ساعت است تماس نگرفته (گوشی خاموش/بی‌اینترنت)
  await seen(Date.now() - offlineMs - 60_000);
  const status = (await h.get(`/api/store/${s.slug}`)).body.store.status;
  assert.deepEqual([status.badge, status.connection, status.orderAcceptance], ['offline', 'lost', 'delayed'],
    'مشتری راست می‌بیند: دکان آفلاین است و سفارش با تأخیر بررسی می‌شود');
  const o = await storeOrder(s);
  assert.equal(await one('SELECT accept_deadline_at AS d FROM online_orders WHERE id=$1 AND accept_deadline_at IS NOT NULL', [o.id]), null,
    'سفارشِ «با تأخیر» مهلتِ قبول ندارد');
  await timeout.sweep(Date.now() + 2 * 60 * 60 * 1000);
  assert.equal((await track(s, live)).status, 'auto_rejected');
  assert.deepEqual(stepOf(await track(s, live)), { state: 'closed', choice: '', canChoose: false, online: false });
  const t0 = await track(s, o);
  assert.equal(t0.status, 'new', 'سفارشِ زمانِ آفلاین ماند');
  assert.equal(t0.paymentStep.state, 'awaiting_accept');

  //  فروشنده برگشت: همگام‌سازی ⇒ «برخط»، سفارش در صفِ «تازه»؛ قبول ⇒ کارتِ «پرداخت» برای مشتری
  assert.equal((await push(s.token, [priceOp(120, 'update')])).status, 200);
  assert.notEqual((await h.get(`/api/store/${s.slug}`)).body.store.status.badge, 'offline');
  const q = await sellerQueue(s);
  assert.ok(q.ids.includes(o.id), JSON.stringify(q));
  assert.ok(!q.ids.includes(live.id), 'ردشده در صفِ «تازه» نیست');
  await walk(s, o, ['accepted']);
  assert.deepEqual(stepOf(await track(s, o)), { state: 'awaiting_choice', choice: '', canChoose: true, online: true });
});
