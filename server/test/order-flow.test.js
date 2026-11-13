'use strict';
/**
 * جریانِ سفارشِ آنلاین (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۳): «چرا سفارش نمی‌رسد؟»، «پرداخت پس از پذیرش» و «به فاکتور».
 *
 * آن‌چه قفل می‌شود:
 *   ۱) `arrival` ِ `GET /shop/orders/config`: هر دروازهٔ میانِ ویترین و برنامهٔ دکان با کدِ پایدار — حتی وقتی بخش
 *      خاموش است (پیش‌تر فقط `enabled: false` و بخشِ پنهان) — و با برداشتنِ هر دروازه از فهرست می‌رود.
 *   ۲) «پرداخت پس از پذیرش» (مهاجرتِ ۲۱۰؛ از ۲۱۱ دکانِ تازه روشن، موجود خاموش ⇒ خاموش: پاسخِ پیگیری و `/pay` همان پیشین): روشن ⇒ پیش از
 *      قبول نه حساب‌پی و نه انتخاب؛ پس از قبول «نقد» یک بار (تکرار بی رویداد)؛ مشتری قبول نمی‌کند و مبلغ نمی‌گذارد.
 *   ۳) `POST /shop/orders/:id/invoice`: فقط از «بسته شد» به بعد («دریافت از دکان» ِ پرداخت‌نشده فقط همراهِ «مشتری گرفت»)، یک بار زیرِ قفل (تکرار و دو درخواستِ هم‌زمان ⇒
 *      همان فاکتور و همان زمان)، سفارشِ آزمایشی و دکانِ دیگر نه؛ فاکتورِ نقدی پرداختِ آنلاینِ بعدی را می‌بندد.
 *   ۴) سفارشِ فاکتورشده که پیش از بیرون رفتن لغو شود ⇒ در `reversals` ِ `GET /handover` (لغوِ همان فروش)؛ بی فروش نه.
 *   ۵) rollback ِ مهاجرتِ ۲۱۰ و ۲۱۱؛ پیش‌فرضِ روشنِ دکانِ تازه (۲۱۱) و خاموشِ دکانِ موجود.
 *   ۶) امنیت: «به فاکتور» با همان اجازهٔ ثبتِ فروش؛ فاکتورِ نقدی و قصدِ حساب‌پی زیرِ یک قفل (هیچ‌کدام از دو ترتیبِ مسابقه).
 */
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-order-flow';
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.CATALOG_REFRESH_THROTTLE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'flow-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'flow-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const flag = (scope, key, value = true) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

const push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, body: { device_id: device, schema_version: 3, ops }, headers: { 'X-App': 'shop', 'X-Device': device },
});
const product = (id, name, price) => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name, unit: 'کیلو', salePrice: price, category: 'خوراکی', active: true },
});

async function newShop(label) {
  const u = await h.newUser(`صاحب ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

/** دکانِ روشن با ویترین و کالای منتشرشده (دورهٔ آزمایشی) */
async function liveShop(label, { settings = {} } = {}) {
  const s = await newShop(label);
  for (const k of ['shop.orders', 'shop.storefront']) await flag(s.shopId, k);
  const slug = `of-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', ...settings }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(s.token, `dev-${uniq()}`, [product('rice', 'برنج', 120)]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return { ...s, slug, settings: put.body.settings };
}

let phoneSeq = 0;
async function storeOrder(s, { fulfillment = 'pickup' } = {}) {
  const phone = `07966${String(++phoneSeq).padStart(5, '0')}`;
  const v = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: v.body.devCode });
  const body = { fulfillment, customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 2000 }] };
  if (fulfillment === 'delivery') body.address = { text: 'کابل، کارتهٔ سه', landmark: 'کنارِ مسجد' };
  const ck = await h.post(`/api/store/${s.slug}/checkout`, body,
    { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1', [s.shopId])).id;
  return { token: ck.body.order.token, id };
}

async function staffOrder(s, over = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ productId: 'rice', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 12000 }], ...over,
  }, { token: s.token, headers: { 'Idempotency-Key': `flow-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

/** سفارشِ دستیِ «ارسال» ِ نقدی — فاکتورش از «بسته شد» (نقدِ پیک امانت است، نه دخل) */
const DELIVERY = { fulfillment: 'delivery', address: { landmark: 'کنارِ مسجد' } };

async function walk(s, id, steps) {
  for (const to of steps) {
    const body = to === 'cancelled' ? { to, reason: 'customer_request' } : { to };
    const r = await h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}

const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const choose = (s, token, body) => h.post(`/api/store/${s.slug}/orders/${token}/pay-choice`, body);
const pay = (s, token) => h.post(`/api/store/${s.slug}/orders/${token}/pay`, {}, { headers: { 'Idempotency-Key': `pay-${uniq()}-key` } });
const invoice = (s, id) => h.post(`/api/shop/orders/${id}/invoice`, {}, { token: s.token });
const detail = async (s, id) => (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order;
const codes = (list) => list.map((b) => b.code);

/** حساب‌پی برای همین دکان (پرچم + اشتراکِ پولی؛ ارائه‌دهندهٔ ساختگی) */
async function hesabpay(s) {
  await flag(s.shopId, 'pay.hesabpay');
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
}

test('«چرا سفارش نمی‌رسد؟» — هر دروازه با کد، حتی وقتی بخش خاموش است؛ با برداشتنِ هر کدام از فهرست می‌رود', async () => {
  const s = await newShop('تشخیص');
  let cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.enabled, false);
  assert.ok(cfg.arrival, 'بخشِ خاموش هم دلیل دارد — شکستِ بی‌صدا نه');
  assert.equal(cfg.arrival.ok, false);
  assert.deepEqual(codes(cfg.arrival.blockers), ['orders_off', 'storefront_off', 'storefront_missing']);
  assert.deepEqual(cfg.arrival.blockers.map((b) => b.fix), ['support', 'support', 'storefront']);

  for (const k of ['shop.orders', 'shop.storefront']) await flag(s.shopId, k);
  cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.enabled, true);
  assert.deepEqual(codes(cfg.arrival.blockers), ['storefront_missing']);

  const slug = `ar-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: false, title: 'ویترین' }, { token: s.token })).status, 200);
  cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.deepEqual(codes(cfg.arrival.blockers), ['storefront_hidden', 'no_products']);

  assert.equal((await h.put('/api/shop/storefront', { enabled: true }, { token: s.token })).status, 200);
  cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.deepEqual(codes(cfg.arrival.blockers), ['no_products']);
  assert.deepEqual(cfg.arrival.blockers[0].fix, 'products');

  assert.equal((await push(s.token, `dev-${uniq()}`, [product('rice', 'برنج', 120)])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.arrival.ok, true, JSON.stringify(cfg.arrival));
  assert.deepEqual(codes(cfg.arrival.warnings), ['alerts_off'], 'اعلانِ سفارشِ تازه خاموش است');

  await flag(s.shopId, 'shop.order_notify');
  cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.deepEqual(cfg.arrival, { ok: true, blockers: [], warnings: [] });

  //  تصمیمِ صاحب محصول: «فقط مشترک» (دورهٔ آزمایشی) ⇒ گفته می‌شود، باز نمی‌شود
  await flag(s.shopId, 'shop.orders.subscribers_only');
  cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.deepEqual(cfg.arrival.blockers, [{ code: 'trial_not_accepting', fix: 'subscribe' }]);
  const ck = await h.post(`/api/store/${slug}/checkout`, {}, { headers: { 'Idempotency-Key': `ck-${uniq()}-key` } });
  assert.notEqual(ck.status, 201);
});

test('«پرداخت پس از پذیرش» خاموش (صریح) ⇒ پیگیری و انتخاب همان پیشین', async () => {
  //  از مهاجرتِ ۲۱۱ دکانِ تازه پیش‌فرض روشن است ⇒ خاموش را صریح می‌گذاریم، نه با تکیه بر پیش‌فرض
  const s = await liveShop('خاموشِ صریح', { settings: { payAfterAccept: false } });
  assert.equal(s.settings.payAfterAccept, false);
  const o = await storeOrder(s);
  const tr = (await track(s, o.token)).body.order;
  assert.equal('paymentStep' in tr, false);
  assert.deepEqual(tr.onlinePay, { available: false });
  const c = await choose(s, o.token, { method: 'cash' });
  assert.equal(c.status, 404, JSON.stringify(c.body));
  const d = await detail(s, o.id);
  assert.deepEqual(d.payStep, { mode: 'any', state: 'cash', choice: '' });
  assert.equal(d.invoice, null);
});

test('«پرداخت پس از پذیرش» روشن ولی جز نقد راهی نیست (بی حساب‌پی) ⇒ هیچ «روشِ پرداخت را انتخاب کنید»؛ نقدِ ثبت همان', async () => {
  //  ممیزیِ ویترین با چشمِ مشتری (CHK-13/20): برگزیدن فقط وقتی معنا دارد که حساب‌پی هم هست
  const s = await liveShop('فقط‌نقد', { settings: { payAfterAccept: true } });
  assert.equal(s.settings.payAfterAccept, true);
  const o = await storeOrder(s, { fulfillment: 'delivery' });
  for (const steps of [[], ['accepted']]) {
    await walk(s, o.id, steps);
    const tr = (await track(s, o.token)).body.order;
    assert.equal('paymentStep' in tr, false, `بی کارتِ «پرداخت» (${tr.status})`);
    assert.deepEqual(tr.onlinePay, { available: false });
    assert.deepEqual((await detail(s, o.id)).payStep, { mode: 'any', state: 'cash', choice: '' }, 'فروشنده هم هشدارِ «برنگزیده» نمی‌گیرد');
  }
  const c = await choose(s, o.token, { method: 'cash' });
  assert.equal(c.status, 404, JSON.stringify(c.body));
  //  همان دکان با حساب‌پی ⇒ همان گامِ برگزیدن (پس از قبول)
  await hesabpay(s);
  assert.equal((await track(s, o.token)).body.order.paymentStep.state, 'awaiting_choice');
});

test('مهاجرتِ ۲۱۱: دکانِ تازه «پرداخت پس از پذیرش» را روشن می‌گیرد، دکانِ موجود خاموش؛ تنظیمِ صاحب جلو می‌افتد', async () => {
  //  دکانِ تازه (پس از ۲۱۱) ⇒ ویترینِ نخست روشن؛ پیش از قبول نه حساب‌پی، نه `/pay`
  const fresh = await liveShop('تازه');
  assert.equal(fresh.settings.payAfterAccept, true, 'پیش‌فرضِ دکانِ تازه');
  await hesabpay(fresh);
  const o = await storeOrder(fresh, { fulfillment: 'delivery' });
  assert.equal((await track(fresh, o.token)).body.order.paymentStep.state, 'awaiting_accept');
  assert.equal((await pay(fresh, o.token)).body.error.code, 'order_not_accepted');
  //  ذخیرهٔ بعدیِ ویترین بی کلید ⇒ همان مقدار (پیش‌فرض فقط در نخستین ساخت)
  assert.equal((await h.put('/api/shop/storefront', { payAfterAccept: false }, { token: fresh.token })).body.settings.payAfterAccept, false);
  assert.equal((await h.put('/api/shop/storefront', { title: 'نامِ تازه' }, { token: fresh.token })).body.settings.payAfterAccept, false);

  //  دکانِ موجود (ردیفِ پیش از ۲۱۱ ⇒ `pay_after_accept_default = false`، همان کارِ مهاجرت) ⇒ ویترینِ نخست خاموش
  const old = await newShop('موجود');
  for (const k of ['shop.orders', 'shop.storefront']) await flag(old.shopId, k);
  await query('UPDATE shops SET pay_after_accept_default = false WHERE id = $1', [old.shopId]);
  const put = await h.put('/api/shop/storefront', { slug: `ex-${uniq()}`, enabled: false, title: 'ویترین' }, { token: old.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal(put.body.settings.payAfterAccept, false, 'دکانِ موجود رفتارِ امروزش را نگه می‌دارد');
  //  و ویترینِ موجودِ روشن‌شده با دستِ صاحب، با ذخیرهٔ بعدی خاموش نمی‌شود
  assert.equal((await h.put('/api/shop/storefront', { payAfterAccept: true }, { token: old.token })).body.settings.payAfterAccept, true);
  assert.equal((await h.put('/api/shop/storefront', { title: 'دوباره' }, { token: old.token })).body.settings.payAfterAccept, true);

  //  دکانِ تازه که صاحبش در نخستین ساخت خودش «خاموش» گفت ⇒ همان
  const own = await newShop('انتخابِ صاحب');
  for (const k of ['shop.orders', 'shop.storefront']) await flag(own.shopId, k);
  const p2 = await h.put('/api/shop/storefront', { slug: `ow-${uniq()}`, enabled: false, title: 'ویترین', payAfterAccept: false }, { token: own.token });
  assert.equal(p2.body.settings.payAfterAccept, false);
});

test('«پرداخت پس از پذیرش» روشن: پیش از قبول نه حساب‌پی نه انتخاب؛ پس از قبول «نقد» یک بار؛ مشتری قبول نمی‌کند', async () => {
  const s = await liveShop('پس‌از‌پذیرش', { settings: { payAfterAccept: true } });
  assert.equal(s.settings.payAfterAccept, true);
  await hesabpay(s);
  const o = await storeOrder(s, { fulfillment: 'delivery' });

  let tr = (await track(s, o.token)).body.order;
  assert.equal(tr.onlinePay.available, false, 'پیش از قبول دکمهٔ حساب‌پی نیست');
  assert.deepEqual(tr.paymentStep, { state: 'awaiting_accept', choice: '', amountMinor: 24000, cash: 'delivery', canChoose: false, online: false });
  const early = await pay(s, o.token);
  assert.equal(early.status, 409, JSON.stringify(early.body));
  assert.equal(early.body.error.code, 'order_not_accepted');
  assert.equal(await one(`SELECT 1 AS x FROM payment_intents WHERE ref_id=$1`, [o.id]), null, 'هیچ قصدِ پرداختی پیش از قبول');
  const c0 = await choose(s, o.token, { method: 'cash', status: 'accepted', totalMinor: 1 });
  assert.equal(c0.status, 409);
  assert.equal(c0.body.error.code, 'order_not_accepted');
  assert.equal((await detail(s, o.id)).status, 'new', 'مشتری سفارشِ خودش را قبول نمی‌کند');
  assert.deepEqual((await detail(s, o.id)).payStep, { mode: 'after_accept', state: 'awaiting_accept', choice: '' });

  await walk(s, o.id, ['accepted']);
  tr = (await track(s, o.token)).body.order;
  assert.equal(tr.onlinePay.available, true, 'پس از قبول حساب‌پی باز است');
  assert.equal(tr.paymentStep.state, 'awaiting_choice');
  assert.equal(tr.paymentStep.canChoose, true);
  assert.equal(tr.paymentStep.online, true);
  assert.deepEqual((await detail(s, o.id)).payStep, { mode: 'after_accept', state: 'awaiting_choice', choice: '' });

  assert.equal((await choose(s, o.token, { method: 'hesabpay_cash' })).body.error.code, 'bad_payment_choice');
  const flip = o.token.slice(0, -1) + (o.token.endsWith('A') ? 'B' : 'A');
  assert.equal((await choose(s, flip, { method: 'cash' })).status, 404);

  const c1 = await choose(s, o.token, { method: 'cash', totalMinor: 1 });
  assert.equal(c1.status, 200, JSON.stringify(c1.body));
  assert.equal(c1.body.changed, true);
  assert.equal(c1.body.order.paymentStep.state, 'cash');
  assert.deepEqual(c1.body.order.onlinePay, { available: true }, 'همان شکلِ پیگیری: حساب‌پی هنوز باز است');
  assert.equal(c1.body.order.paymentStep.online, true);
  assert.equal(c1.body.order.totalMinor, 24000, 'مبلغ از سرور');
  const c2 = await choose(s, o.token, { method: 'cash' });
  assert.equal(c2.body.changed, false, 'تکرار ⇒ بی رویدادِ دوم');
  const notes = await many(`SELECT 1 FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='pay_choice'`, [o.id]);
  assert.equal(notes.length, 1);
  const d = await detail(s, o.id);
  assert.deepEqual(d.payStep, { mode: 'after_accept', state: 'cash', choice: 'cash' });
  assert.equal(d.paymentStatus, 'unpaid');
  assert.equal(d.codStatus, 'to_collect', 'نقد همان «گرفتنی» تا پیک بگیرد');

  //  حساب‌پی پس از قبول (مشتری نظرش را عوض کرد) ⇒ در جریان ⇒ انتخابِ دوباره و فاکتور بسته
  const p = await pay(s, o.token);
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal((await choose(s, o.token, { method: 'cash' })).body.error.code, 'payment_pending');
  assert.equal((await detail(s, o.id)).payStep.state, 'online_pending');
  await walk(s, o.id, ['picking', 'packed']);
  const inv = await invoice(s, o.id);
  assert.equal(inv.status, 409);
  assert.equal(inv.body.error.code, 'payment_pending');

  //  تنظیم را خاموش کن ⇒ درِ انتخاب ۴۰۴
  assert.equal((await h.put('/api/shop/storefront', { payAfterAccept: false }, { token: s.token })).status, 200);
  assert.equal((await choose(s, o.token, { method: 'cash' })).status, 404);
  //  ⛔ CHK-13: «خاموش» و «نیست» یک جواب — بدنهٔ بد، بدنهٔ تهی و توکنِ نادرست هم همان ۴۰۴ (پیش‌تر ۴۰۰ `bad_payment_choice`)
  for (const [tk, body] of [[o.token, { method: 'hesabpay_cash' }], [o.token, {}], [o.token, { method: 7 }], [flip, { method: 'bad' }]]) {
    const off = await choose(s, tk, body);
    assert.equal(off.status, 404, JSON.stringify(off.body));
    assert.equal(off.body.error.code, 'not_found', JSON.stringify(off.body));
  }
});

//  ⛔ «همه روشن» (`web-store-allflags.mjs`): پاسخِ این کارِ مشتری فقط `sf.track` بود و صفحه (`renderOrder(r.order)`) هر کلیدِ نیامده را
//  پنهان می‌کرد ⇒ کارتِ گفت‌وگو، دستورِ تحویل، زمان و روش تا پیگیریِ بعدی می‌رفتند. حالا همان نمای پیگیری (`lib/store-order-view.js`).
test('«پرداخت پس از پذیرش» با پرچم‌های دیگرِ پیگیری: پاسخِ «نقد» همان نمای پیگیری است (گفت‌وگو، دستورِ تحویل، زمان، روش، …)', async () => {
  const s = await liveShop('همه‌روشن', { settings: { payAfterAccept: true } });
  await hesabpay(s);   //  بی راهِ دوم «انتخاب» نیست (همان آزمونِ بالا)
  for (const k of ['shop.chat', 'shop.orders.delivery_prefs', 'shop.storefront.eta', 'shop.storefront.delivery_method', 'shop.orders.issue_reports', 'shop.orders.reviews', 'shop.orders.refund_tracking']) await flag(s.shopId, k);
  const o = await storeOrder(s, { fulfillment: 'delivery' });
  await walk(s, o.id, ['accepted']);
  const c = await choose(s, o.token, { method: 'cash' });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const tr = (await track(s, o.token)).body.order;
  assert.equal(c.body.order.chat, true, 'کارتِ گفت‌وگو با پاسخِ «نقد» نمی‌رود');
  assert.deepEqual(Object.keys(c.body.order).sort(), Object.keys(tr).sort());
  assert.deepEqual(c.body.order.deliveryPrefs, tr.deliveryPrefs);
  assert.deepEqual(c.body.order.paymentStep, tr.paymentStep);
});

test('«به فاکتور»: فقط از «بسته شد»؛ یک بار زیرِ قفل (تکرار و هم‌زمان ⇒ همان)؛ آزمایشی و دکانِ دیگر نه', async () => {
  const s = await liveShop('فاکتور');
  const other = await liveShop('فاکتورِ دیگر');
  const o = await staffOrder(s, DELIVERY);
  let r = await invoice(s, o.id);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'invoice_not_ready');
  assert.equal(r.body.error.current, 'new');
  await walk(s, o.id, ['accepted', 'picking']);
  assert.equal((await invoice(s, o.id)).body.error.code, 'invoice_not_ready', 'قلم‌ها تا «بسته شد» عوض می‌شوند');
  await walk(s, o.id, ['packed']);

  assert.equal((await h.post(`/api/shop/orders/${o.id}/invoice`, {}, { token: other.token })).status, 404, 'دکانِ دیگر');
  r = await invoice(s, o.id);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.created, true);
  assert.equal(r.body.invoice.saleId, `o-${o.id}`);
  assert.ok(r.body.invoice.at > 0);
  assert.deepEqual(r.body.order.invoice, r.body.invoice);
  assert.equal(r.body.order.lines.length, 1, 'جزئیاتِ کامل برای ثبتِ فروش در برنامه');
  const again = await invoice(s, o.id);
  assert.equal(again.status, 200);
  assert.equal(again.body.created, false);
  assert.deepEqual(again.body.invoice, r.body.invoice, 'همان فاکتور و همان زمان');
  assert.deepEqual((await detail(s, o.id)).invoice, r.body.invoice);

  //  دو دستگاه هم‌زمان ⇒ یک «فاکتور شد»
  const o2 = await staffOrder(s, DELIVERY);
  await walk(s, o2.id, ['accepted', 'picking', 'packed']);
  const both = await Promise.all([invoice(s, o2.id), invoice(s, o2.id)]);
  assert.deepEqual(both.map((x) => x.status).sort(), [200, 201]);
  assert.deepEqual(both[0].body.invoice, both[1].body.invoice);
  const n = await many(`SELECT 1 FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='invoice'`, [o2.id]);
  assert.equal(n.length, 1);

  const t = await staffOrder(s, { test: true });
  await walk(s, t.id, ['accepted', 'picking', 'packed']);
  assert.equal((await invoice(s, t.id)).body.error.code, 'test_order');

  await flag(s.shopId, 'shop.orders', false);
  assert.equal((await invoice(s, o.id)).body.error.code, 'orders_disabled');
});

test('فاکتورِ نقدی ⇒ پرداختِ آنلاینِ بعدی بسته (فاکتور با پول بخواند)', async () => {
  const s = await liveShop('فاکتورِ نقدی', { settings: { payAfterAccept: false } });
  await hesabpay(s);
  const o = await storeOrder(s, { fulfillment: 'delivery' });
  assert.equal((await track(s, o.token)).body.order.onlinePay.available, true, 'رفتارِ پیشین: از «تازه»');
  await walk(s, o.id, ['accepted', 'picking', 'packed']);
  assert.equal((await invoice(s, o.id)).status, 201);
  assert.equal((await track(s, o.token)).body.order.onlinePay.available, false);
  const p = await pay(s, o.token);
  assert.equal(p.status, 409);
  assert.equal(p.body.error.code, 'order_invoiced');
});

test('سفارشِ فاکتورشده که پیش از بیرون رفتن لغو شد ⇒ لغوِ همان فروش در reversals؛ بی فروش هیچ', async () => {
  const s = await liveShop('لغوِ فاکتور');
  const since = (await h.get('/api/shop/orders/config?caps=saleOnHandover', { token: s.token })).body.handoverSince;
  assert.ok(since > 0);
  const o = await staffOrder(s, DELIVERY);
  const bare = await staffOrder(s, DELIVERY);
  for (const x of [o, bare]) await walk(s, x.id, ['accepted', 'picking', 'packed']);
  assert.equal((await invoice(s, o.id)).status, 201);
  //  برنامه فروشِ `o-<سفارش>` را نوشت و همگام کرد (`OrderFulfillment.fulfill`)
  const at = Date.now();
  const sale = { op_id: `op-${uniq()}`, ts: at, table: 'sales', row_id: `o-${o.id}`, type: 'insert', fields: {
    invoiceNumber: o.orderNo, series: 'O', total: 180, finalTotal: 180, paymentMethod: 'cash', paidAmount: 180, remaining: 0,
    status: 'completed', createdAt: at, date: '2026-11-03', channel: 'online', orderId: o.id, orderNo: o.orderNo,
  } };
  assert.equal((await push(s.token, 'dev-flow-a', [sale])).status, 200);
  let r = (await h.get(`/api/shop/orders/handover?since=${since}`, { token: s.token })).body;
  assert.deepEqual(r.reversals, []);
  for (const x of [o, bare]) await walk(s, x.id, ['cancelled']);
  r = (await h.get(`/api/shop/orders/handover?since=${since}`, { token: s.token })).body;
  assert.deepEqual(r.reversals.map((x) => [x.id, x.action]), [[o.id, 'reverse']], 'فقط سفارشِ دارای فروش');
  assert.ok(r.reversals[0].returnedAt > 0);
});

test('مهاجرتِ ۲۱۰: rollback ستون را برمی‌دارد و دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const down = fs.readFileSync(path.join(dir, 'rollback', '210_order_pay_after_accept.down.sql'), 'utf8');
  const up = fs.readFileSync(path.join(dir, '210_order_pay_after_accept.sql'), 'utf8');
  const col = async () => (await query(
    "SELECT column_name FROM information_schema.columns WHERE table_name='storefronts' AND column_name='pay_after_accept'"
  )).rows.length;
  assert.equal(await col(), 1);
  const mark = await one("SELECT * FROM schema_migrations WHERE version='210_order_pay_after_accept.sql'");
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await col(), 0);
  assert.equal(await one("SELECT 1 AS x FROM schema_migrations WHERE version='210_order_pay_after_accept.sql'"), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await col(), 1);
});

/** عضوِ تازه با نقشِ [role] در دکانِ [s] — نشستِ واقعی */
async function member(s, role) {
  const inv = await h.post('/api/shop/invite', { role }, { token: s.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const u = await h.newUser(`کارمندِ ${role}`, 'shop');
  const j = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
  assert.ok([200, 201].includes(j.status), JSON.stringify(j.body));
  return (await h.signIn(u, 'shop')).accessToken;
}

const invoiceNotes = (id) => many(`SELECT 1 FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='invoice'`, [id]);

test('امنیت: «به فاکتور» همان اجازهٔ ثبتِ فروشِ برنامه را می‌خواهد — جمع‌آور با تماسِ مستقیم نه', async () => {
  const s = await liveShop('اجازهٔ فاکتور');
  const picker = await member(s, 'picker');
  const o = await staffOrder(s, DELIVERY);
  await walk(s, o.id, ['accepted', 'picking', 'packed']);
  const perms = (await h.get('/api/shop/orders/config', { token: picker })).body.permissions;
  assert.equal(perms.manage, true, 'جمع‌آور سفارش را می‌گرداند');
  assert.equal(perms.recordSale, false, 'ولی فروش نمی‌نویسد ⇒ دکمهٔ «به فاکتور» ِ برنامه پنهان است');
  const r = await h.post(`/api/shop/orders/${o.id}/invoice`, {}, { token: picker });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'permission_denied');
  assert.equal((await invoiceNotes(o.id)).length, 0, 'هیچ «فاکتور شد»ی ننشست');
  assert.equal((await invoice(s, o.id)).status, 201, 'صاحب همان سفارش را فاکتور می‌زند');
});

test('امنیت: قصدِ حساب‌پیِ زنده (پیش از «در انتظار» شدنِ سفارش) فاکتورِ نقدی را می‌بندد', async () => {
  const s = await liveShop('مسابقهٔ قصد', { settings: { payAfterAccept: false } });
  await hesabpay(s);
  const o = await storeOrder(s, { fulfillment: 'delivery' });
  await walk(s, o.id, ['accepted', 'picking', 'packed']);
  assert.equal((await pay(s, o.token)).status, 201);
  //  همان لحظهٔ `/pay` میانِ دو تراکنشش: قصد ساخته شده و درگاه هنوز جلسه را برنگردانده ⇒ سفارش هنوز «پرداخت‌نشده»
  await query(`UPDATE payment_intents SET status='created' WHERE ref_id=$1`, [o.id]);
  await query(`UPDATE online_orders SET payment_status='unpaid' WHERE id=$1`, [o.id]);
  const inv = await invoice(s, o.id);
  assert.equal(inv.status, 409, JSON.stringify(inv.body));
  assert.equal(inv.body.error.code, 'payment_pending');
  assert.equal((await invoiceNotes(o.id)).length, 0);
});

test('امنیت: فاکتورِ نقدی که میانِ سنجشِ `/pay` و ساختنِ قصد نشست ⇒ قصدی ساخته نمی‌شود', async () => {
  const s = await liveShop('مسابقهٔ پرداخت', { settings: { payAfterAccept: false } });
  await hesabpay(s);
  const o = await storeOrder(s, { fulfillment: 'delivery' });
  await walk(s, o.id, ['accepted', 'picking', 'packed']);
  //  `/pay` سنجشِ `guardOnlinePay` را گذراند (هنوز فاکتوری نبود)…
  const flow = require('../src/lib/order-flow');
  await flow.guardOnlinePay(s.shopId, o.id);
  //  …و همان دم فروشنده «به فاکتور» زد
  assert.equal((await invoice(s, o.id)).status, 201);
  //  ادامهٔ همان `/pay`
  await assert.rejects(
    require('../src/lib/payment-intents').createForStorefront(s.shopId, o.id, { key: `race-${uniq()}-key`, returnUrl: 'https://x.invalid/' }),
    (e) => e.code === 'order_invoiced'
  );
  assert.equal(await one('SELECT 1 AS x FROM payment_intents WHERE ref_id=$1', [o.id]), null, 'هیچ قصدِ حساب‌پی پس از فاکتورِ نقدی');
  assert.equal((await detail(s, o.id)).paymentStatus, 'unpaid');
});

test('مهاجرتِ ۲۱۱: ردیف‌های موجود خاموش، INSERT ِ تازه روشن؛ rollback ستون را برمی‌دارد و دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const down = fs.readFileSync(path.join(dir, 'rollback', '211_order_pay_after_accept_default.down.sql'), 'utf8');
  const up = fs.readFileSync(path.join(dir, '211_order_pay_after_accept_default.sql'), 'utf8');
  const col = async () => (await query(
    "SELECT column_name FROM information_schema.columns WHERE table_name='shops' AND column_name='pay_after_accept_default'"
  )).rows.length;
  const s = await liveShop('پیش از rollback');
  const sfBefore = (await one('SELECT pay_after_accept FROM storefronts WHERE shop_id=$1', [s.shopId])).pay_after_accept;
  assert.equal((await one('SELECT pay_after_accept_default AS d FROM shops WHERE id=$1', [s.shopId])).d, true);
  const mark = await one("SELECT * FROM schema_migrations WHERE version='211_order_pay_after_accept_default.sql'");
  assert.ok(mark, 'مهاجرت نشسته است');

  await query(down);
  assert.equal(await col(), 0);
  assert.equal(await one("SELECT 1 AS x FROM schema_migrations WHERE version='211_order_pay_after_accept_default.sql'"), null);

  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await col(), 1);
  assert.equal((await one('SELECT pay_after_accept_default AS d FROM shops WHERE id=$1', [s.shopId])).d, false, 'ردیفِ موجود ⇒ خاموش');
  assert.equal((await one('SELECT pay_after_accept FROM storefronts WHERE shop_id=$1', [s.shopId])).pay_after_accept, sfBefore,
    'ویترینِ موجود دست نخورد');
  const n = await newShop('پس از دوباره نشستن');
  assert.equal((await one('SELECT pay_after_accept_default AS d FROM shops WHERE id=$1', [n.shopId])).d, true, 'دکانِ تازه ⇒ روشن');
});

const handoverAt = async (id) => Number((await one(
  `SELECT MIN(created_at)::bigint AS at FROM online_order_events WHERE order_id=$1 AND kind='status' AND to_status='picked_up'`, [id]
)).at);

test('«دریافت از دکان» ِ پرداخت‌نشده: فاکتور فقط همراهِ «مشتری گرفت» (سرور، نه فقط برنامه) — زمانِ فاکتور = زمانِ تحویل', async () => {
  const s = await liveShop('تحویلِ پیشخوان');
  const o = await staffOrder(s);   //  دریافت از دکان، نقد، پرداخت‌نشده
  await walk(s, o.id, ['accepted', 'picking', 'packed']);
  let r = await invoice(s, o.id);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'invoice_at_handover');
  assert.equal(r.body.error.current, 'packed');
  assert.equal((await invoiceNotes(o.id)).length, 0, 'فاکتورِ نقدیِ پیش از رسیدنِ پول نه');

  //  «آمادهٔ دریافت» ⇒ فاکتور و «مشتری گرفت» در یک تراکنش، یک زمان
  await walk(s, o.id, ['ready_for_pickup']);
  r = await invoice(s, o.id);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.order.status, 'picked_up', 'تحویل همراهِ فاکتور');
  assert.equal(r.body.invoice.at, await handoverAt(o.id), 'زمانِ فروشِ نقدیِ پیشخوان = زمانِ تحویل');
  //  گامِ بعدیِ برنامه («مشتری گرفت») تکرار است، نه خطا
  const t = await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'picked_up' }, { token: s.token });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  assert.equal(t.body.unchanged, true);
  assert.deepEqual((await invoice(s, o.id)).body.invoice, r.body.invoice, 'تکرار ⇒ همان فاکتور');

  //  پیش‌تر تحویل‌شده و فاکتورنشده ⇒ زمانِ فاکتور همان زمانِ تحویل، نه حالا
  const o2 = await staffOrder(s);
  await walk(s, o2.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']);
  const handed = await handoverAt(o2.id);
  r = await invoice(s, o2.id);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.invoice.at, handed);
  assert.ok(r.body.order.updatedAt >= handed, 'نسخهٔ ردیف به عقب نمی‌رود (صفِ زنده)');

  //  پرداخت‌شدهٔ آنلاین ⇒ همان قاعدهٔ پیشین: از «بسته شد»
  const o3 = await staffOrder(s);
  await walk(s, o3.id, ['accepted', 'picking', 'packed']);
  await query(`UPDATE online_orders SET payment_status='paid', payment_method='online', cod_status='' WHERE id=$1`, [o3.id]);
  r = await invoice(s, o3.id);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.order.status, 'packed', 'پرداخت‌شده تحویل نمی‌خورد');
});

test('آینهٔ کاتلین (`core/orders/OrderFlow.kt`) همان وضعیت‌های فاکتورپذیرِ سرور را دارد', () => {
  const kt = fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'src', 'commonMain', 'kotlin', 'ir', 'vil3ntec', 'tohid',
    'core', 'orders', 'OrderFlow.kt'), 'utf8');
  const block = kt.match(/val INVOICE_STATUSES: List<String> = listOf\(([^)]*)\)/);
  assert.ok(block, 'فهرستِ کاتلین پیدا شد');
  const list = [...block[1].matchAll(/"([a-z_]+)"/g)].map((m) => m[1]);
  assert.deepEqual(list, require('../src/lib/order-flow').INVOICE_STATUSES);
});
