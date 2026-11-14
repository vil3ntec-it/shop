'use strict';
/**
 * CHK-24 — اسکناسِ ماندهٔ مشتری پس از تغییرِ جمع (`delivery-prefs.dropStaleTenderIn`).
 *
 * پیش از این، اسکناسی که جمعِ تازه دیگر نمی‌پذیرفت (ویرایش، افزودنِ قلم، بسته‌بندی با «پیدا نشد»/جایگزین، روشِ تحویل،
 * پرداختِ آنلاین) در سفارش می‌ماند و هر ویرایشِ بعدیِ دستورِ تحویل — وبِ مشتری، برنامهٔ خرید و کارتِ کارمند — با
 * `cash_tender_*`/`bad_cash_tender` رد می‌شد تا کسی صریحاً پاکش کند؛ و پیک بر پایهٔ آن پول خردِ نادرست می‌برد.
 *
 * آن‌چه این پرونده قفل می‌کند (یک قاعده):
 *   ۱) هر راهی که جمع/روشِ پرداخت/روشِ تحویل را عوض می‌کند، اسکناسِ نادرست را در همان تراکنش برمی‌دارد، با رویدادِ
 *      `delivery_edit` ِ «سیستم» و یادداشت در خطِ زمانیِ کارمند؛ مشتری «با کدام اسکناس…» را از نو می‌بیند.
 *   ۲) ویرایشی که `cashTenderMinor` نمی‌فرستد هرگز به خاطرِ اسکناسِ مانده رد نمی‌شود (ردیفِ کهنه هم)؛ مبلغِ نادرستِ
 *      فرستاده همان ۴۰۰ ِ پیشین.
 *   ۳) تکرارِ همان درخواست رویدادِ دوم نمی‌سازد؛ توکنِ جعلی و دکانِ دیگر هیچ چیز را برنمی‌دارند؛ ویرایش و
 *      ویرایشِ سفارش هم‌زمان (PostgreSQL) هرگز اسکناسِ نادرست باقی نمی‌گذارند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-tender-stale';
process.env.RATE_PAY_CREATE_MAX = '100000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const key = (p = 'k') => `${p}-${uniq()}-key`;

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

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکان با دستورِ تحویل، ویترین (ارسالِ ۵۰ افغانی، دریافت از دکان) و سه کالا؛ `flags` پرچم‌های راهِ زیرِ آزمون. */
async function shop(label, flags = []) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `ts-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.delivery_prefs', ...flags]) await setFlag(s.shopId, k, true);
  const put = await h.put('/api/shop/storefront', {
    slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 5000, minOrderMinor: 0, acceptPickup: true,
  }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-ts' },
    body: { device_id: 'dev-ts', schema_version: 2, ops: [
      op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
      op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 333.33, category: 'خوراکی' }),
    ] },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  for (const p of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

/** سفارشِ کارمند: ۱۲٬۰۰۰ + ۲٬۰۰۰ + ارسالِ ۴۰۰ = ۱۴٬۴۰۰ افغانی؛ مشتری با ۱۵٬۰۰۰ (پول خرد ۶۰۰). */
const STAFF_TOTAL = 1_440_000;
const STAFF_TENDER = 1_500_000;
async function staffOrder(s, tender = STAFF_TENDER) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    address: { text: 'کارتهٔ چهار', landmark: 'نزدیکِ مسجد', lat: 34.5, lng: 69.1 },
    delivery: { callBeforeArrival: true, cashTenderMinor: tender },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }, { name: 'روغن', qtyMilli: 1000, unitPriceMinor: 200_000 }],
    deliveryFeeMinor: 40_000,
  }, { token: s.token, headers: { 'Idempotency-Key': key('o') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.order.totalMinor, STAFF_TOTAL);
  assert.equal(r.body.order.delivery.cashTenderMinor, tender);
  return r.body.order;
}

const PHONE = '0791112233';
const tickets = new Map();
async function ticketFor(slug, phone = PHONE) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
/** سفارشِ ویترین «ارسال» و نقد با اسکناس؛ پیش‌فرض ۱۰ کیلو برنج + روغن + ۵۰ = ۱٬۵۸۳ افغانی، با ۲٬۰۰۰. */
async function storeOrder(s, { rice = 10_000, tender = 200_000 } = {}) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone: PHONE },
    address: { text: 'کارته‌سه', landmark: 'پشتِ مسجد', lat: 34.53, lng: 69.17 },
    delivery: { cashTenderMinor: tender },
    lines: [{ productId: 'rice', qtyMilli: rice }, { productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': key('ck'), 'X-Store-Ticket': await ticketFor(s.slug) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.order.token;
  return { id: token.split('.')[0], token, totalMinor: r.body.order.totalMinor };
}

const staffEdit = (s, id, body) => h.api('POST', `/api/shop/orders/${id}/delivery`, { token: s.token, body });
const custEdit = (s, token, body) => h.api('POST', `/api/store/${s.slug}/orders/${token}/delivery`, { body });
const track = async (s, token) => (await h.get(`/api/store/${s.slug}/orders/${token}`)).body.order;
const staffView = async (s, id) => (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order;
const storedTender = async (id) => {
  const r = await one(`SELECT delivery->'cashTenderMinor' AS t FROM online_orders WHERE id=$1`, [id]);
  return r.t === null || r.t === undefined ? null : Number(r.t);
};
const drops = (id) => many(
  `SELECT note, detail FROM online_order_events WHERE order_id=$1 AND kind='delivery_edit' AND actor_type='system' ORDER BY seq`, [id]);

/** همان کارتِ کارمند (`OrderDeliveryPrefsUi`): همیشه سه کلید و نشانه، با اسکناسی که کارت نشان می‌دهد */
const staffCard = (o, over = {}) => ({
  expectedVersion: o.version,
  delivery: { callBeforeArrival: o.delivery.callBeforeArrival === true, leaveWith: 'دربان', cashTenderMinor: o.delivery.cashTenderMinor ?? null },
  address: { landmark: 'دروازهٔ سبز' }, ...over,
});

/** یک برداشتنِ «سیستم» با همان مبلغ، جمعِ تازه و یادداشتی که کارمند در خطِ زمانی می‌بیند */
async function assertDropped(s, id, tender, totalMinor) {
  assert.equal(await storedTender(id), null, 'اسکناسِ نادرست در همان تراکنش برداشته شد');
  const d = await drops(id);
  assert.equal(d.length, 1, JSON.stringify(d));
  const detail = typeof d[0].detail === 'string' ? JSON.parse(d[0].detail) : d[0].detail;
  assert.deepEqual([detail.changed, detail.before.cashTenderMinor, detail.after.cashTenderMinor], [['cashTenderMinor'], tender, null]);
  assert.equal(detail.stale.totalMinor, totalMinor);
  const ev = (await staffView(s, id)).events.find((e) => e.kind === 'delivery_edit' && e.actorType === 'system');
  assert.ok(ev && /اسکناسِ مشتری/.test(ev.note) && /پیک/.test(ev.note), JSON.stringify(ev));
}

/* ══════════════════════════════════════════════════════════════════ */

test('ORD-14 کارمند: ویرایشِ سفارش اسکناسِ نادرست را برمی‌دارد؛ کارتِ کارمند و ویرایشِ بی اسکناس ۲۰۰؛ مبلغِ نادرستِ فرستاده ۴۰۰؛ تکرار بی رویدادِ دوم', async () => {
  const s = await shop('ویرایش و اسکناس', ['shop.orders.amend']);
  const o = await staffOrder(s);
  await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'accepted' }, { token: s.token });
  //  روغن برداشته شد ⇒ ۱۲٬۴۰۰؛ اسکناسِ ۱۵٬۰۰۰ ⇒ پول خردِ ۲٬۶۰۰ (بیش از یک اسکناسِ ۱۰۰۰) ⇒ دیگر درست نیست
  const k = key('am');
  const body = { changes: [{ lineNo: 2, qtyMilli: 0 }], reason: 'out_of_stock', consent: { via: 'phone' } };
  const am = await h.post(`/api/shop/orders/${o.id}/amendments`, body, { token: s.token, headers: { 'Idempotency-Key': k } });
  assert.equal(am.status, 201, JSON.stringify(am.body));
  assert.equal(am.body.order.totalMinor, 1_240_000);
  await assertDropped(s, o.id, STAFF_TENDER, 1_240_000);
  assert.equal(am.body.order.delivery.cashTenderMinor, undefined, 'پاسخِ همان ویرایش هم بی اسکناس است');

  //  ویرایشِ بی کلیدِ اسکناس (برنامهٔ خرید/وب برای کادرِ دیگر) — پیش از این ۴۰۰ `cash_tender_excess`
  const fresh = await staffView(s, o.id);
  const a = await staffEdit(s, o.id, { expectedVersion: fresh.version, delivery: { leaveWith: 'همسایه' } });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  //  کارتِ کارمند با همان اسکناسی که نشان می‌دهد (اکنون هیچ)
  const card = await staffEdit(s, o.id, staffCard(a.body.order));
  assert.equal(card.status, 200, JSON.stringify(card.body));
  assert.deepEqual([card.body.order.delivery.leaveWith, card.body.order.address.landmark], ['دربان', 'دروازهٔ سبز']);
  //  مبلغی که خودِ درخواست می‌فرستد همان سنجشِ پیشین را دارد
  const bad = await staffEdit(s, o.id, { delivery: { cashTenderMinor: STAFF_TENDER } });
  assert.deepEqual([bad.status, bad.body.error.code], [400, 'cash_tender_excess']);
  const good = await staffEdit(s, o.id, { delivery: { cashTenderMinor: 1_300_000 } });
  assert.equal(good.status, 200, JSON.stringify(good.body));
  assert.equal(await storedTender(o.id), 1_300_000);
  //  تکرارِ همان ویرایشِ سفارش ⇒ همان نتیجه، بی برداشتنِ دوم (اسکناسِ تازه می‌ماند)
  const again = await h.post(`/api/shop/orders/${o.id}/amendments`, body, { token: s.token, headers: { 'Idempotency-Key': k } });
  assert.ok([200, 201].includes(again.status), JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal((await drops(o.id)).length, 1);
  assert.equal(await storedTender(o.id), 1_300_000);
  const rep = await require('../src/lib/verify-data').verify({ shopId: s.shopId });
  assert.deepEqual(rep.checks.filter((c) => c.count > 0).map((c) => c.id), [], 'verify-data بی خطا (رویدادهای پیوسته)');
});

test('ORD-14 ویترین: مشتری پیشنهاد را می‌پذیرد ⇒ اسکناس برداشته؛ پیگیری «با کدام اسکناس…» را از نو؛ ویرایشِ مشتری ۲۰۰', async () => {
  const s = await shop('پذیرشِ مشتری', ['shop.orders.amend']);
  const o = await storeOrder(s);
  assert.equal(o.totalMinor, 120_000 + 33_300 + 5000);
  assert.equal((await track(s, o.token)).deliveryPrefs.cashTenderMinor, 200_000);
  const p = await h.post(`/api/shop/orders/${o.id}/amendments`, { changes: [{ lineNo: 1, qtyMilli: 1000 }], reason: 'out_of_stock' },
    { token: s.token, headers: { 'Idempotency-Key': key('am') } });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal(await storedTender(o.id), 200_000, 'تا پذیرش هیچ چیز عوض نمی‌شود');
  const ok = await h.post(`/api/store/${s.slug}/orders/${o.token}/amendments/${p.body.amendment.id}/accept`, {});
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.order.totalMinor, 12_000 + 33_300 + 5000);
  await assertDropped(s, o.id, 200_000, 50_300);
  const pf = (await track(s, o.token)).deliveryPrefs;
  assert.equal(pf.cashTenderMinor, null, 'مشتری دوباره می‌گزیند');
  //  برنامهٔ خرید (بی کلیدِ اسکناس) و وب (null ِ صریح) هر دو ۲۰۰
  const app = await custEdit(s, o.token, { delivery: { callBeforeArrival: true, leaveWith: 'دربان' }, expectedVersion: pf.version });
  assert.equal(app.status, 200, JSON.stringify(app.body));
  const web = await custEdit(s, o.token, {
    delivery: { callBeforeArrival: false, leaveWith: 'دربان', cashTenderMinor: null }, expectedVersion: app.body.order.deliveryPrefs.version,
  });
  assert.equal(web.status, 200, JSON.stringify(web.body));
  const bad = await custEdit(s, o.token, { delivery: { cashTenderMinor: 200_000 } });
  assert.deepEqual([bad.status, bad.body.error.code], [400, 'cash_tender_excess']);
  //  دوباره پذیرفتن ⇒ همان، بی رویدادِ دوم
  const again = await h.post(`/api/store/${s.slug}/orders/${o.token}/amendments/${p.body.amendment.id}/accept`, {});
  assert.equal(again.body.unchanged, true);
  assert.equal((await drops(o.id)).length, 1);
});

test('CHK-21 افزودنِ قلم: جمع از اسکناس گذشت ⇒ برداشته؛ تکرارِ همان کلید بی رویدادِ دوم؛ ویرایشِ مشتری ۲۰۰', async () => {
  const s = await shop('افزودن و اسکناس', ['shop.orders.add_items']);
  //  ۳ کیلو برنج + روغن + ۵۰ = ۷۴۳ افغانی، با ۱٬۰۰۰
  const o = await storeOrder(s, { rice: 3000, tender: 100_000 });
  assert.equal(o.totalMinor, 74_300);
  const k = key('add');
  const add = () => h.post(`/api/store/${s.slug}/orders/${o.token}/items`, { lines: [{ productId: 'rice', qtyMilli: 3000 }] },
    { headers: { 'Idempotency-Key': k, 'X-Store-Ticket': tickets.get(`${s.slug}|${PHONE}`) } });
  const r = await add();
  assert.equal(r.status, 201, JSON.stringify(r.body));
  await assertDropped(s, o.id, 100_000, 110_300);
  const replay = await add();
  assert.ok(replay.status < 300, JSON.stringify(replay.body));
  assert.equal((await drops(o.id)).length, 1);
  const pf = (await track(s, o.token)).deliveryPrefs;
  assert.equal(pf.cashTenderMinor, null);
  const e = await custEdit(s, o.token, { delivery: { leaveWith: 'دربان' }, expectedVersion: pf.version });
  assert.equal(e.status, 200, JSON.stringify(e.body));
});

test('PCK بسته‌بندی: قلمِ «پیدا نشد» جمع را پایین آورد ⇒ اسکناس برداشته، همان تراکنشِ «بسته‌بندی‌شده»؛ کارتِ کارمند ۲۰۰', async () => {
  const s = await shop('جمع‌آوری و اسکناس', ['shop.orders.picking']);
  const o = await staffOrder(s);
  await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'accepted' }, { token: s.token });
  assert.equal((await h.post(`/api/shop/orders/${o.id}/pick/start`, {}, { token: s.token })).status, 201);
  const ev = (lineNo, type, extra = {}) => ({ eventId: key('ev'), lineNo, type, ...extra });
  const sent = await h.post(`/api/shop/orders/${o.id}/pick/events`, { events: [ev(1, 'pick', { qtyMilli: 1000 }), ev(2, 'not_found')] }, { token: s.token });
  assert.deepEqual(sent.body.results.map((x) => x.status), ['accepted', 'accepted'], JSON.stringify(sent.body));
  const k = key('pack');
  const pack = () => h.post(`/api/shop/orders/${o.id}/pick/pack`, { bags: 1 }, { token: s.token, headers: { 'Idempotency-Key': k } });
  const p = await pack();
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const after = await staffView(s, o.id);
  assert.deepEqual([after.status, after.totalMinor], ['packed', 1_240_000]);
  await assertDropped(s, o.id, STAFF_TENDER, 1_240_000);
  assert.equal((await pack()).body.replayed, true);
  assert.equal((await drops(o.id)).length, 1);
  const card = await staffEdit(s, o.id, staffCard(after));
  assert.equal(card.status, 200, JSON.stringify(card.body));
});

test('S72 روشِ تحویل: «دریافت از دکان» ⇒ اسکناس برداشته؛ تکرارِ همان کلید بی رویدادِ دوم', async () => {
  const s = await shop('روشِ تحویل و اسکناس', ['shop.storefront.delivery_method']);
  const o = await storeOrder(s, { rice: 3000, tender: 100_000 });
  const k = key('dm');
  const change = (b) => h.post(`/api/store/${s.slug}/orders/${o.token}/fulfillment`, b,
    { headers: { 'Idempotency-Key': k, 'X-Store-Ticket': tickets.get(`${s.slug}|${PHONE}`) } });
  const q = await h.post(`/api/store/${s.slug}/orders/${o.token}/fulfillment`, { fulfillment: 'pickup' },
    { headers: { 'Idempotency-Key': key('dm'), 'X-Store-Ticket': tickets.get(`${s.slug}|${PHONE}`) } });
  assert.equal(q.body.error && q.body.error.code, 'confirm_required', JSON.stringify(q.body));
  const r = await change({ fulfillment: 'pickup', expectedTotalMinor: q.body.error.quote.totalMinor });
  assert.ok(r.status < 300, JSON.stringify(r.body));
  await assertDropped(s, o.id, 100_000, 69_300);
  assert.ok((await change({ fulfillment: 'pickup', expectedTotalMinor: q.body.error.quote.totalMinor })).status < 300);
  assert.equal((await drops(o.id)).length, 1);
});

test('PAY-21 پرداختِ آنلاین پس از ثبت: نقد دیگر نیست ⇒ اسکناس برداشته؛ برنامهٔ خرید (بی کلید) و وب (null) ۲۰۰', async () => {
  const pay = require('../src/lib/payment-intents');
  const P = require('../src/lib/payment-providers');
  const s = await shop('حساب‌پی و اسکناس');
  await setFlag(s.shopId, pay.FLAG_KEY, true);
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  const o = await storeOrder(s);
  const it = await h.post('/api/v1/shop/payments/intents',
    { kind: 'online_order', refId: o.id, amount: o.totalMinor, currency: 'AFN', idempotencyKey: key('pay') }, { token: s.token });
  assert.equal(it.status, 201, JSON.stringify(it.body));
  const sid = (await one('SELECT provider_session_id FROM payment_intents WHERE id=$1', [it.body.id])).provider_session_id;
  P.fake.settle(sid, { status: 'paid', amountMinor: o.totalMinor, currency: 'AFN', providerRef: 'HP-TX-1' });
  const hook = (eventId) => {
    const buf = Buffer.from(JSON.stringify({ eventId, sessionId: sid, status: 'paid', amountMinor: o.totalMinor, currency: 'AFN', providerRef: 'HP-TX-1' }));
    return h.raw('POST', '/api/payments/webhook/fake', buf, { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(buf) } });
  };
  const w = await hook(`ev-${uniq()}`);
  assert.equal(w.body.outcome, 'applied', JSON.stringify(w.body));
  assert.equal((await one('SELECT payment_method FROM online_orders WHERE id=$1', [o.id])).payment_method, 'online');
  await assertDropped(s, o.id, 200_000, o.totalMinor);
  //  وب‌هوکِ تکراری ⇒ بی رویدادِ دوم
  await hook(`ev-${uniq()}`);
  assert.equal((await drops(o.id)).length, 1);
  const pf = (await track(s, o.token)).deliveryPrefs;
  assert.equal(pf.cashTenderMinor, null);
  const app = await custEdit(s, o.token, { delivery: { callBeforeArrival: true, leaveWith: 'دربان' }, expectedVersion: pf.version });
  assert.equal(app.status, 200, JSON.stringify(app.body));
  const web = await custEdit(s, o.token, { delivery: { leaveWith: 'همسایه', cashTenderMinor: null } });
  assert.equal(web.status, 200, JSON.stringify(web.body));
  const bad = await custEdit(s, o.token, { delivery: { cashTenderMinor: 200_000 } });
  assert.deepEqual([bad.status, bad.body.error.code], [400, 'bad_cash_tender']);
});

test('ردیفِ کهنه (پیش از این قاعده): ویرایشِ بی اسکناس ۲۰۰ و برداشتن؛ توکنِ جعلی، دکانِ دیگر و مبلغِ نادرست هیچ چیز را برنمی‌دارند', async () => {
  const s = await shop('ردیفِ کهنه');
  const other = await shop('دکانِ دیگر');
  const o = await storeOrder(s);
  //  اسکناسی که با جمعِ امروز نمی‌خواند، مثلِ سفارشی که پیش از این رفع جمعش عوض شده بود
  await query(`UPDATE online_orders SET delivery = delivery || '{"cashTenderMinor": 500000}'::jsonb WHERE id=$1`, [o.id]);
  const v0 = (await track(s, o.token)).deliveryPrefs.version;
  const forged = await custEdit(s, h.tampered(o.token), { delivery: { leaveWith: 'دربان' } });
  assert.equal(forged.status, 404, JSON.stringify(forged.body));
  const cross = await staffEdit(other, o.id, { delivery: { leaveWith: 'دربان' } });
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
  const bad = await custEdit(s, o.token, { delivery: { leaveWith: 'دربان', cashTenderMinor: 500_000 } });
  assert.deepEqual([bad.status, bad.body.error.code], [400, 'cash_tender_excess']);
  const stale = await custEdit(s, o.token, { delivery: { leaveWith: 'دربان' }, expectedVersion: v0 + 5 });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(await storedTender(o.id), 500_000, 'هیچ ردی پیش از سنجشِ توکن، دکان و نسخه چیزی ننوشت');
  assert.equal((await drops(o.id)).length, 0);

  const ok = await custEdit(s, o.token, { delivery: { leaveWith: 'دربان' }, expectedVersion: v0 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.order.deliveryPrefs.leaveWith, 'دربان');
  assert.equal(ok.body.order.deliveryPrefs.cashTenderMinor, null);
  await assertDropped(s, o.id, 500_000, o.totalMinor);
  //  پاسخِ گم‌شده و تکرار: نسخهٔ کهنه ⇒ ۴۰۹ (پیگیری از نو ⇒ «ثبت شد»)، بی نسخه ⇒ «همان بود»؛ هیچ برداشتنِ دوم
  const replay = await custEdit(s, o.token, { delivery: { leaveWith: 'دربان' }, expectedVersion: v0 });
  assert.deepEqual([replay.status, replay.body.error.code], [409, 'version_conflict']);
  const same = await custEdit(s, o.token, { delivery: { leaveWith: 'دربان' } });
  assert.deepEqual([same.status, same.body.unchanged], [200, true]);
  assert.equal((await drops(o.id)).length, 1);
  const own = (await staffView(s, o.id)).events.filter((e) => e.kind === 'delivery_edit').map((e) => e.actorType);
  assert.deepEqual(own, ['system', 'customer'], 'برداشتنِ سیستم پیش از ویرایشِ خودِ مشتری');
});

test('هم‌زمانی: ویرایشِ سفارش و ویرایشِ دستورِ تحویل روی یک سفارش هرگز اسکناسِ نادرست باقی نمی‌گذارند', async () => {
  const s = await shop('هم‌زمانی', ['shop.orders.amend']);
  const N = 4;
  const os = [];
  for (let i = 0; i < N; i++) {
    const o = await staffOrder(s);
    await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'accepted' }, { token: s.token });
    os.push(o);
  }
  const amendOf = (o) => h.post(`/api/shop/orders/${o.id}/amendments`, { changes: [{ lineNo: 2, qtyMilli: 0 }], consent: { via: 'phone' } },
    { token: s.token, headers: { 'Idempotency-Key': key('am') } });
  //  نیمی بی کلیدِ اسکناس (همیشه ۲۰۰)، نیمی با اسکناسی که فقط جمعِ پیشین می‌پذیرد (پیش ⇒ برداشته، پس ⇒ ۴۰۰)
  const out = await Promise.all(os.map((o, i) => Promise.all([
    amendOf(o),
    staffEdit(s, o.id, i % 2 ? { delivery: { leaveWith: `دربان ${i}` } } : { delivery: { leaveWith: `دربان ${i}`, cashTenderMinor: 1_450_000 } }),
  ])));
  for (const [i, [am, ed]] of out.entries()) {
    const o = os[i];
    assert.equal(am.status, 201, JSON.stringify(am.body));
    if (i % 2) assert.equal(ed.status, 200, JSON.stringify(ed.body));
    else assert.ok(ed.status === 200 || (ed.status === 400 && ed.body.error.code === 'cash_tender_excess'), JSON.stringify(ed.body));
    const v = await staffView(s, o.id);
    assert.equal(v.totalMinor, 1_240_000);
    assert.equal(await storedTender(o.id), null, 'اسکناسِ نادرست هرگز نماند');
    //  هر دو ترتیب دقیقاً یک برداشتن: ویرایش پیش ⇒ ویرایشِ سفارش اسکناسِ تازه را برمی‌دارد؛ پس ⇒ اسکناسِ نخست
    assert.equal((await drops(o.id)).length, 1);
    if (ed.status === 200) assert.equal(v.delivery.leaveWith, `دربان ${i}`, 'هیچ ویرایشی گم نشد');
  }
});
