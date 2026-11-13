'use strict';
/**
 * NTF-08 — پیامِ هر بازپرداخت به مشتریِ ویترین (`lib/customer-notify.onRefundIn`، `lib/order-refunds.record`،
 * مهاجرتِ ۴۹۶؛ کانالِ پوشِ وب `lib/store-push.js`).
 *
 *   ۱) ⛔ پیش‌فرض خاموش: بی `shop.customer_notify` یا بی `shop.orders.refund_tracking` ⇒ بازپرداخت ثبت می‌شود و هیچ ردیف.
 *   ۲) هر بازپرداخت (بخشی، بخشیِ پایانی، کامل) دقیقاً یک پیام با مبلغِ خودش و شمارهٔ سفارش (fa/ps/en)؛ بی شماره و نام.
 *   ۳) تکرار: همان کلیدِ یکتایی ⇒ همان بازپرداخت و هیچ پیامِ دوم؛ کلیدِ تازه ⇒ بازپرداختِ تازه، پیامِ تازه.
 *   ۴) هم‌زمانی: دو بازپرداختِ موازیِ یک سفارش ⇒ دو پیام؛ همان بازپرداخت دو بار موازی ⇒ یک.
 *   ۵) پایگاه: یکتایی و پیوند به بازپرداختِ همان سفارش در خودِ پایگاه؛ NTF-04 ِ رویدادهای دیگر و نوشتنِ نسخهٔ پیش همان.
 *   ۶) مستأجر: دکانِ دیگر سفارش را بازپرداخت نمی‌کند و پیامش را نمی‌بیند؛ سفارشِ لغوشده بازپرداخت و پیام ندارد.
 *   ۷) کلیدِ قطع: پرچمِ خاموش پس از صف ⇒ پیام نمی‌رود (`flag_off`).
 *   ۸) پوشِ وب: اشتراک پس از تحویل برای خبرِ بازپرداخت می‌ماند؛ پیامِ رمزشده با مبلغ و برچسبِ جدای هر بازپرداخت؛
 *      پس از بسته شدن پاک. بی پرچم همان رفتارِ پیشین (پس از تحویل پاک، نمای پیگیری بی push).
 *   ۹) برنامهٔ دکان نامِ هر رویداد را دارد (`OrderNoticesUi.kt`)؛ برگشتِ مهاجرت و دوباره بالا آمدنش.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const ece = require('http_ece');
const h = require('./helpers');
const db = require('../src/db');
const { query, one, many, newId, now } = db;
const N = require('../src/lib/customer-notify');
const P = require('../src/lib/store-push');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
let phoneSeq = 0;
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
test.after(async () => { P.useTransport(null); N.setProviders(null); await h.stop(); });
test.beforeEach(async () => {
  resetAll(); N.setProviders(null); P.useTransport(null);
  await query(`UPDATE customer_notify_outbox SET status='cancelled' WHERE status IN ('pending','sending')`);
});

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const syncPush = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-rfn' }, body: { device_id: 'dev-rfn', schema_version: 2, ops },
});

const TRACK = 'shop.orders.refund_tracking';
async function shop(label, { notify = true, tracking = true, push = false } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  //  شمارهٔ جدا برای هر دکان: سقفِ هر مشتری (`PER_HOUR`) میانِ آزمون‌ها پر نمی‌شود
  const phone = `0792${String(100000 + (++phoneSeq)).slice(-6)}`;
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `rn-${uniq()}`, title: `ویترینِ ${label}`, phone };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.refunds']) await setFlag(s.shopId, k, true);
  if (notify) await setFlag(s.shopId, N.FLAG_KEY, true);
  //  پیگیری پیش‌فرض‌روشن است (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) ⇒ «بی پیگیری» `false` ِ صریح است
  await setFlag(s.shopId, TRACK, tracking);
  if (push) await setFlag(s.shopId, P.FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: s.title, contactPhone: '0700000001', acceptPickup: true }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await syncPush(s.token, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' } }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

const NAME = 'مشتریِ بازپرداخت';
async function storeOrder(s, lang = 'fa') {
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(s.phone) });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(s.phone), code: st.body.devCode });
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: NAME, phone: s.phone }, lines: [{ productId: 'rice', qtyMilli: 5000 }], lang,
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token, no: r.body.order.orderNo, total: r.body.order.totalMinor };
}
const move = async (s, id, list) => {
  for (const to of list) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to, ...(to === 'cancelled' ? { reason: 'customer_request' } : {}) }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
};
const DELIVERED = ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'];
const refundReq = (s, id, body, key = `rf-${uniq()}-key`) =>
  h.post(`/api/shop/orders/${id}/refunds`, body, { token: s.token, headers: { 'Idempotency-Key': key } });
async function refund(s, id, body, key) {
  const r = await refundReq(s, id, body, key);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.entry;
}
const refundRows = (orderId) => many(
  `SELECT * FROM customer_notify_outbox WHERE order_id=$1 AND refund_id IS NOT NULL ORDER BY created_at, id`, [orderId]);
const ledgerOf = (orderId) => many('SELECT * FROM online_order_refunds WHERE order_id=$1 ORDER BY seq', [orderId]);
const code = (r) => (r.body && r.body.error && r.body.error.code) || '';
const drain = async (at = now()) => { while (await N.tick(at)) { /* تا خالی */ } };
/** سرویس‌دهندهٔ جعلیِ کانالِ پوش (همان شکلِ customer-notify.test.js) */
function fake() {
  const calls = [];
  return { calls, send: async (msg) => { calls.push(msg); return { status: 'accepted', ref: `r-${calls.length}` }; } };
}
/** پیام‌های بازپرداختِ رفته به سرویس‌دهنده */
const refundCalls = (p) => p.calls.filter((m) => m.event === 'order_refund');

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچم: بی اعلانِ مشتری یا بی پیگیریِ بازپرداخت ⇒ بازپرداخت ثبت و هیچ پیامِ بازپرداخت', async () => {
  for (const opts of [{ notify: false }, { tracking: false }]) {
    const s = await shop(`خاموش-${Object.keys(opts)[0]}`, opts);
    const o = await storeOrder(s);
    await move(s, o.id, [...DELIVERED, 'return_requested']);
    await refund(s, o.id, { amountMinor: 1000, reason: 'خراب' });
    assert.equal((await ledgerOf(o.id)).length, 1, 'دفتر همان رفتارِ پیشین');
    assert.equal((await refundRows(o.id)).length, 0, `${JSON.stringify(opts)}: هیچ ردیفِ پیامِ بازپرداخت`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۲) هر بازپرداخت یک پیام، با مبلغِ خودش
   ══════════════════════════════════════════════════════════════════ */

test('NTF-08: قالبِ order_refund به fa، ps و en با {amount}، {no} و {shop}؛ رقم و واحدِ هر زبان', () => {
  const tpl = N.EXTRA.order_refund;
  assert.ok(tpl, 'رویدادِ سیستمیِ order_refund');
  assert.deepEqual(tpl.channels, ['push', 'whatsapp', 'telegram'], 'فقط کانال‌های رایگان (پیامکِ پولی نه)');
  for (const lang of N.LANGS) {
    assert.deepEqual(tpl.text[lang].match(/\{[^}]*\}/g).sort(), ['{amount}', '{no}', '{shop}'], `${lang}: فقط مبلغ، شمارهٔ سفارش و نامِ ویترین`);
    assert.equal(/[؀-ۿ]/.test(tpl.text[lang]), lang !== 'en', `${lang}: خطِ همان زبان`);
  }
  assert.equal(N.render('order_refund', 'fa', { no: 42, shop: 'دکانِ گل', amountMinor: 12345 }),
    'بازپرداختِ ۱۲۳٫۴۵ افغانی برای سفارشِ شمارهٔ ۴۲ در «دکانِ گل» ثبت شد؛ جزئیات در صفحهٔ پیگیریِ سفارش است.');
  assert.match(N.render('order_refund', 'ps', { no: 42, shop: 'گل', amountMinor: 20000 }), /۲۰۰ افغانۍ/);
  assert.equal(N.render('order_refund', 'en', { no: 42, shop: 'Gul', amountMinor: 250000 }),
    'A refund of 2,500 AFN for order #42 at Gul was recorded. Details are on your order page.');
  assert.throws(() => N.render('order_refund', 'fa', { no: 1, shop: 'a' }), /amount/, 'بی مبلغ ⇒ خطا، نه متنِ ناقص');
  //  نامِ ویترین الگوی جایگزینی نمی‌شود (`$&`، `{amount}`)
  const odd = N.render('order_refund', 'en', { no: 3, shop: '$& {amount} $1', amountMinor: 100 });
  assert.equal(odd, 'A refund of 1 AFN for order #3 at $& {amount} $1 was recorded. Details are on your order page.');
});

test('هر بازپرداخت دقیقاً یک پیام: بخشی + بخشی + پایانی ⇒ سه پیام، هر کدام با مبلغِ خودش؛ بی شماره و نام', async () => {
  const s = await shop('سه‌تایی');
  const o = await storeOrder(s);
  assert.equal(o.total, 60000);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  const p = fake();
  N.setProviders({ push: p });
  const e1 = await refund(s, o.id, { amountMinor: 20000, reason: 'یادداشتِ داخلی: کیسه پاره' });
  const e2 = await refund(s, o.id, { amountMinor: 12345, reason: 'دوم' });
  const e3 = await refund(s, o.id, { amountMinor: 5000, reason: 'بستن', final: true });
  const ledger = await ledgerOf(o.id);
  assert.deepEqual(ledger.map((r) => Number(r.amount_minor)), [20000, 12345, 5000]);
  assert.deepEqual([e1.seq, e2.seq, e3.seq], [1, 2, 3]);

  const rows = await refundRows(o.id);
  assert.equal(rows.length, 3, 'سه ردیف، یکی برای هر بازپرداخت');
  assert.deepEqual(rows.map((r) => r.refund_id), ledger.map((r) => r.id), 'هر ردیف به بازپرداختِ خودش');
  assert.deepEqual(rows.map((r) => r.event), ledger.map((r) => `order_refund:${r.id}`));
  assert.ok(rows.every((r) => r.status === 'pending' && r.shop_id === s.shopId && r.lang === 'fa'));

  await drain();
  const calls = refundCalls(p);
  assert.equal(calls.length, 3, 'سه پیام، نه بیشتر');
  const want = ['۲۰۰ افغانی', '۱۲۳٫۴۵ افغانی', '۵۰ افغانی'];
  calls.forEach((m, i) => {
    assert.equal(m.text, N.render('order_refund', 'fa', { no: o.no, shop: s.title, amountMinor: Number(ledger[i].amount_minor) }));
    assert.ok(m.text.includes(want[i]), `پیامِ ${i + 1}: ${want[i]} — ${m.text}`);
    assert.equal(m.refundId, ledger[i].id);
    assert.equal(m.orderId, o.id);
    for (const pii of [NAME, s.phone, s.phone.slice(1), 'یادداشتِ داخلی', o.id]) assert.ok(!m.text.includes(pii), `بی دادهٔ شخصی/درونی: ${pii}`);
  });
  assert.equal(new Set(calls.map((m) => m.key)).size, 3, 'کلیدِ هر پیام جدا');
  for (const r of await refundRows(o.id)) assert.deepEqual([r.status, r.channel], ['sent', 'push']);

  //  دورِ دوم ِ کارگر ⇒ هیچ پیامِ دیگر
  await drain();
  assert.equal(refundCalls(p).length, 3);

  //  نمای دکان: نامِ رویدادِ تمیز، بی شماره، متن یا لینک
  const nv = await h.get(`/api/shop/orders/${o.id}/notices`, { token: s.token });
  assert.equal(nv.status, 200, JSON.stringify(nv.body));
  const items = nv.body.items.filter((x) => x.event === 'order_refund');
  assert.equal(items.length, 3);
  assert.ok(!JSON.stringify(nv.body).includes(s.phone.slice(1)));
  assert.ok(!JSON.stringify(nv.body).includes('افغانی'), 'نمای دکان بی متنِ پیام');
});

test('سفارشِ انگلیسی، بازپرداختِ کامل در یک گام ⇒ یک پیام به زبانِ مشتری', async () => {
  const s = await shop('کامل');
  const o = await storeOrder(s, 'en');
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  const p = fake();
  N.setProviders({ push: p });
  await refund(s, o.id, { amountMinor: o.total, reason: 'all' });
  await drain();
  const calls = refundCalls(p);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].lang, 'en');
  assert.equal(calls[0].text, `A refund of 600 AFN for order #${o.no} at ${s.title} was recorded. Details are on your order page.`);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'refunded_full');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) تکرار و ۴) هم‌زمانی
   ══════════════════════════════════════════════════════════════════ */

test('⛔ تکرار: همان کلید ⇒ همان بازپرداخت و هیچ پیامِ دوم (پیش و پس از فرستادن)؛ کلیدِ تازه ⇒ پیامِ تازه', async () => {
  const s = await shop('تکرار');
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  const p = fake();
  N.setProviders({ push: p });
  const key = `rf-${uniq()}-key`;
  const body = { amountMinor: 3000, reason: 'یک' };
  await refund(s, o.id, body, key);
  const again = await refundReq(s, o.id, body, key);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal((await refundRows(o.id)).length, 1);
  await drain();
  const late = await refundReq(s, o.id, body, key);
  assert.equal(late.body.replayed, true);
  await drain();
  assert.equal(refundCalls(p).length, 1, 'پس از فرستادن هم تکرار پیامِ دوم نمی‌سازد');
  //  همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ و هیچ
  const reused = await refundReq(s, o.id, { amountMinor: 4000, reason: 'دیگر' }, key);
  assert.equal(reused.status, 409);
  assert.equal(code(reused), 'idempotency_key_reused');
  //  همان مبلغ با کلیدِ تازه = بازپرداختِ تازه ⇒ پیامِ تازه
  await refund(s, o.id, body);
  await drain();
  assert.equal((await refundRows(o.id)).length, 2);
  assert.equal(refundCalls(p).length, 2);

  //  درونِ کد: همان بازپرداخت دو بار به صف ⇒ دومی null
  const entry = (await ledgerOf(o.id))[0];
  const order = await one('SELECT * FROM online_orders WHERE id=$1', [o.id]);
  const twice = await db.tx(async (c) => N.onRefundIn((t, x) => c.query(t, x), { shopId: s.shopId, order, refundId: entry.id }));
  assert.equal(twice, null);
  assert.equal((await refundRows(o.id)).length, 2);
});

test('هم‌زمانی: دو بازپرداختِ موازیِ یک سفارش ⇒ دو پیام؛ همان بازپرداخت دو بار موازی ⇒ یک', async () => {
  const s = await shop('هم‌زمان');
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  const p = fake();
  N.setProviders({ push: p });

  const two = await Promise.all([
    refundReq(s, o.id, { amountMinor: 7000, reason: 'الف' }),
    refundReq(s, o.id, { amountMinor: 8000, reason: 'ب' }),
  ]);
  assert.deepEqual(two.map((r) => r.status).sort(), [201, 201], JSON.stringify(two.map((r) => r.body)));
  const key = `rf-${uniq()}-same`;
  const same = await Promise.all([1, 2, 3].map(() => refundReq(s, o.id, { amountMinor: 900, reason: 'ج' }, key)));
  assert.deepEqual(same.map((r) => r.status).sort(), [200, 200, 201], JSON.stringify(same.map((r) => r.body)));

  const ledger = await ledgerOf(o.id);
  assert.equal(ledger.length, 3, 'سه بازپرداخت (دو جدا + یکِ سه‌باره)');
  const rows = await refundRows(o.id);
  assert.equal(rows.length, 3, 'یک پیام برای هر بازپرداخت');
  assert.deepEqual(new Set(rows.map((r) => r.refund_id)), new Set(ledger.map((r) => r.id)));
  await Promise.all([drain(), drain()]);
  await drain();
  const calls = refundCalls(p);
  assert.equal(calls.length, 3);
  assert.deepEqual(calls.map((m) => m.refundId).sort(), ledger.map((r) => r.id).sort(), 'هر بازپرداخت یک بار');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) پایگاه
   ══════════════════════════════════════════════════════════════════ */

async function fails(sql, params, sqlState) {
  await assert.rejects(query(sql, params), (err) => {
    assert.equal(err.code, sqlState, `${sqlState} می‌خواستیم: ${err.code} ${err.message}`);
    return true;
  });
}

test('⛔ پایگاه: یکتایی و پیوندِ پیامِ بازپرداخت در خودِ پایگاه؛ NTF-04 و نوشتنِ نسخهٔ پیش همان', async () => {
  const s = await shop('پایگاه', { notify: false });
  const o = await storeOrder(s);
  const other = await storeOrder(s);
  for (const x of [o, other]) await move(s, x.id, [...DELIVERED, 'return_requested']);
  const mine = await refund(s, o.id, { amountMinor: 1000, reason: 'یک' });
  await refund(s, other.id, { amountMinor: 1000, reason: 'دیگری' });
  const rid = (await ledgerOf(o.id))[0].id;
  const otherRid = (await ledgerOf(other.id))[0].id;
  assert.equal(mine.seq, 1);
  const at = now();
  const ins = `INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, refund_id, lang, phone_key, status,
                 next_attempt_at, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,'fa','k','pending',$6,$6,$6)`;
  const id = () => newId('cno');
  await query(ins, [id(), s.shopId, o.id, `order_refund:${rid}`, rid, at]);
  await fails(ins, [id(), s.shopId, o.id, `order_refund:${rid}`, rid, at], '23505');          // همان بازپرداخت دو بار
  await fails(ins, [id(), s.shopId, o.id, `order_refund:${rid}`, null, at], '23514');         // پیامِ بازپرداخت بی بازپرداخت
  await fails(ins, [id(), s.shopId, o.id, 'order_refund', rid, at], '23514');                 // رویدادِ بی پیوند
  await fails(ins, [id(), s.shopId, o.id, `order_refund:${otherRid}`, rid, at], '23514');     // رویداد و بازپرداختِ ناهم‌خوان
  await fails(ins, [id(), s.shopId, o.id, 'order_accepted', rid, at], '23514');               // رویدادِ وضعیت با بازپرداخت
  await fails(ins, [id(), s.shopId, o.id, `order_refund:${otherRid}`, otherRid, at], '23503'); // بازپرداختِ سفارشِ دیگر
  await fails(ins, [id(), s.shopId, o.id, `order_refund:nope`, 'nope', at], '23503');          // بازپرداختِ ناموجود
  //  NTF-04: رویدادِ وضعیت همان یکتاییِ (order_id, event)
  await query(ins, [id(), s.shopId, o.id, 'order_accepted', null, at]);
  await fails(ins, [id(), s.shopId, o.id, 'order_accepted', null, at], '23505');
  //  نسخهٔ پیش (OPS-03): همان INSERT … ON CONFLICT (order_id, event) بی ستونِ تازه
  const old = `INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, lang, phone_key, status, next_attempt_at, created_at, updated_at)
               VALUES ($1,$2,$3,$4,'fa','k','pending',$5,$5,$5) ON CONFLICT (order_id, event) DO NOTHING RETURNING id`;
  assert.equal((await query(old, [id(), s.shopId, o.id, 'order_ready', at])).rows.length, 1);
  assert.equal((await query(old, [id(), s.shopId, o.id, 'order_ready', at])).rows.length, 0);
  await query(`UPDATE customer_notify_outbox SET status='cancelled' WHERE order_id=$1`, [o.id]);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) مستأجر، سفارشِ لغوشده؛ ۷) کلیدِ قطع
   ══════════════════════════════════════════════════════════════════ */

test('⛔ مستأجر: دکانِ دیگر سفارش را بازپرداخت نمی‌کند (۴۰۴) و پیامش را نمی‌بیند؛ پرچمِ دکانِ دیگر بی‌اثر', async () => {
  const a = await shop('الف', { tracking: false });
  const b = await shop('ب');
  const o = await storeOrder(a);
  await move(a, o.id, [...DELIVERED, 'return_requested']);
  const p = fake();
  N.setProviders({ push: p });
  const cross = await refundReq(b, o.id, { amountMinor: 1000, reason: 'دزدی' });
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
  assert.equal((await ledgerOf(o.id)).length, 0);
  //  پرچمِ پیگیریِ دکانِ ب برای سفارشِ دکانِ الف هیچ نمی‌کند
  await refund(a, o.id, { amountMinor: 1000, reason: 'درست' });
  assert.equal((await refundRows(o.id)).length, 0);
  await setFlag(a.shopId, TRACK, true);
  await refund(a, o.id, { amountMinor: 1000, reason: 'دوم' });
  const rows = await refundRows(o.id);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].shop_id, a.shopId);
  assert.equal((await h.get(`/api/shop/orders/${o.id}/notices`, { token: b.token })).status, 404);
  await drain();
  assert.equal(refundCalls(p).length, 1);
  assert.equal(refundCalls(p)[0].shopId, a.shopId);
});

test('⛔ سفارشِ لغوشده یا هنوز تحویل‌نشده: بازپرداخت ۴۰۹ و هیچ پیام', async () => {
  const s = await shop('لغو');
  const cancelled = await storeOrder(s);
  await move(s, cancelled.id, ['accepted', 'cancelled']);
  const open = await storeOrder(s);
  await move(s, open.id, ['accepted']);
  for (const o of [cancelled, open]) {
    const r = await refundReq(s, o.id, { amountMinor: 1000, reason: 'نه' });
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(code(r), 'not_refundable');
    assert.equal((await refundRows(o.id)).length, 0);
  }
});

test('⛔ کلیدِ قطع: پیگیریِ بازپرداخت یا اعلان پس از صف خاموش ⇒ پیام نمی‌رود (flag_off)', async () => {
  for (const key of [TRACK, N.FLAG_KEY]) {
    const s = await shop(`قطع-${key.length}`);
    const o = await storeOrder(s);
    await move(s, o.id, [...DELIVERED, 'return_requested']);
    const p = fake();
    N.setProviders({ push: p });
    await refund(s, o.id, { amountMinor: 1000, reason: 'یک' });
    await setFlag(s.shopId, key, false);
    await drain();
    assert.equal(refundCalls(p).length, 0, key);
    const [row] = await refundRows(o.id);
    assert.deepEqual([row.status, row.last_error], ['cancelled', 'flag_off'], key);
  }
});

test('ORD-16: «فرستادنِ دوباره» ِ پیامِ بازپرداختِ نرفته همان ردیف را می‌فرستد (نه ردیفِ دوم)؛ خطِ زمانی `order_refund`', async () => {
  const s = await shop('دوباره');
  await setFlag(s.shopId, 'shop.orders.notice_resend', true);
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  await refund(s, o.id, { amountMinor: 1500, reason: 'یک' });
  await drain();
  const [row] = await refundRows(o.id);
  assert.equal(row.status, 'no_channel', 'بی هیچ کانالِ وصل');
  const p = fake();
  N.setProviders({ push: p });
  const r = await h.post(`/api/shop/orders/${o.id}/notices/${row.id}/resend`, {}, { token: s.token, headers: { 'Idempotency-Key': `rs-${uniq()}-key` } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.notice.event, 'order_refund');
  await drain();
  assert.equal(refundCalls(p).length, 1);
  assert.match(refundCalls(p)[0].text, /۱۵ افغانی/);
  const rows = await refundRows(o.id);
  assert.deepEqual(rows.map((x) => [x.id, x.status]), [[row.id, 'sent']], 'همان ردیف، نه پیامِ دوم');
  const note = await one(`SELECT detail FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='notice_resend'`, [o.id]);
  const d = typeof note.detail === 'string' ? JSON.parse(note.detail) : note.detail;
  assert.equal(d.event, 'order_refund', 'خطِ زمانیِ برنامهٔ دکان نامِ تمیز');
});

/* ══════════════════════════════════════════════════════════════════
   ۸) پوشِ وب
   ══════════════════════════════════════════════════════════════════ */

function browser() {
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.generateKeys();
  const auth = crypto.randomBytes(16).toString('base64url');
  const sub = { endpoint: `https://fcm.googleapis.com/fcm/send/t-${uniq()}`, keys: { p256dh: ecdh.getPublicKey('base64url'), auth } };
  return { sub, open: (body) => JSON.parse(ece.decrypt(Buffer.from(body), { version: 'aes128gcm', privateKey: ecdh, authSecret: auth }).toString('utf8')) };
}
function service() {
  const calls = [];
  P.useTransport(async (req) => { calls.push(req); return { status: 201 }; });
  return calls;
}
const view = (s, o) => h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}`);
const on = (s, o, body) => h.post(`/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}/push`, body);
const subsOf = (orderId) => many('SELECT * FROM store_push_subs WHERE order_id=$1', [orderId]);

test('پوشِ وب: اشتراک پس از تحویل برای بازپرداخت می‌ماند؛ هر بازپرداخت رمزشده با مبلغ و برچسبِ خودش؛ پس از بسته شدن پاک', async () => {
  const s = await shop('پوش', { push: true });
  const o = await storeOrder(s);
  const calls = service();
  const b = browser();
  assert.equal((await on(s, o, { subscription: b.sub, page: `/store/${s.slug}` })).status, 200);
  await move(s, o.id, DELIVERED);
  await drain();
  const done = calls.length;
  assert.ok(done >= 1, 'خبرِ وضعیت رفت');
  assert.equal((await subsOf(o.id)).length, 1, 'پس از «برداشته شد» اشتراک برای خبرِ بازپرداخت می‌ماند');
  const v = (await view(s, o)).body.order;
  assert.ok(v.push && v.push.key, 'نمای پیگیری پس از تحویل هنوز push دارد');
  assert.equal(v.push.refund, true, 'نشانِ «فقط خبرِ بازپرداخت»');
  //  مرورگرِ دوم هم پس از تحویل اشتراک می‌گیرد (نه ۴۰۹ order_closed)
  assert.equal((await on(s, o, { subscription: browser().sub, page: `/store/${s.slug}` })).status, 200);
  await h.api('DELETE', `/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}/push`, { body: { endpoint: (await subsOf(o.id)).find((x) => x.endpoint !== b.sub.endpoint).endpoint } });

  await move(s, o.id, ['return_requested']);
  const e1 = await refund(s, o.id, { amountMinor: 25000, reason: 'یک' });
  await drain();
  assert.equal(calls.length, done + 1);
  const m1 = b.open(calls[done].body);
  assert.equal(m1.title, s.title);
  assert.equal(m1.body, N.render('order_refund', 'fa', { no: o.no, shop: s.title, amountMinor: 25000 }));
  assert.match(m1.body, /۲۵۰ افغانی/);
  assert.equal(m1.url, `/store/${s.slug}#/o/${encodeURIComponent(o.token)}`);
  assert.equal(calls[done].headers.Urgency, 'normal');
  assert.equal((await subsOf(o.id)).length, 1, 'بازپرداختِ بخشی ⇒ اشتراک می‌ماند');
  assert.equal(e1.seq, 1);

  await refund(s, o.id, { amountMinor: o.total - 25000, reason: 'بقیه' });
  await drain();
  assert.equal(calls.length, done + 2);
  const m2 = b.open(calls[done + 1].body);
  assert.match(m2.body, /۳۵۰ افغانی/);
  assert.notEqual(m2.tag, m1.tag, 'برچسبِ جدا: پیامِ بازپرداختِ دوم اولی را از روی صفحه برنمی‌دارد');
  assert.notEqual(calls[done + 1].headers.Topic, calls[done].headers.Topic);
  assert.equal((await subsOf(o.id)).length, 0, 'بازپرداختِ کامل ⇒ خبرِ دیگری نیست ⇒ اشتراک پاک');
  assert.equal('push' in (await view(s, o)).body.order, false);
});

test('⛔ پوشِ وب بی پیگیریِ بازپرداخت: همان رفتارِ پیشین (پس از تحویل اشتراک پاک، نمای بی push، اشتراکِ تازه ۴۰۹)', async () => {
  const s = await shop('پوش-خاموش', { push: true, tracking: false });
  const o = await storeOrder(s);
  service();
  assert.equal((await on(s, o, { subscription: browser().sub, page: `/store/${s.slug}` })).status, 200);
  const live = (await view(s, o)).body.order.push;
  assert.ok(live && live.key && !('refund' in live), 'سفارشِ باز: همان شکلِ پیشین');
  await move(s, o.id, DELIVERED);
  await drain();
  assert.equal((await subsOf(o.id)).length, 0);
  assert.equal('push' in (await view(s, o)).body.order, false);
  const late = await on(s, o, { subscription: browser().sub, page: `/store/${s.slug}` });
  assert.equal(late.status, 409);
  assert.equal(code(late), 'order_closed');
});

/* ══════════════════════════════════════════════════════════════════
   ۹) برنامهٔ دکان؛ برگشتِ مهاجرت
   ══════════════════════════════════════════════════════════════════ */

test('برنامهٔ دکان: هر رویدادِ اعلانِ مشتری (وضعیت و سیستمی) در OrderNoticesUi.kt نام دارد', () => {
  const kt = fs.readFileSync(path.join(__dirname, '..', '..', 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid',
    'ui', 'screens', 'OrderNoticesUi.kt'), 'utf8');
  for (const ev of [...N.EVENTS, ...Object.keys(N.EXTRA)]) assert.match(kt, new RegExp(`"${ev}" -> StrOrderOps\\.`), ev);
});

test('مهاجرتِ ۴۹۶: برگشت ردیف‌های پیامِ بازپرداخت و ستون را برمی‌دارد، دیگر پیام‌ها و دفتر می‌مانند؛ دوباره بالا می‌آید', async () => {
  const s = await shop('برگشت');
  const o = await storeOrder(s);
  await move(s, o.id, [...DELIVERED, 'return_requested']);
  await refund(s, o.id, { amountMinor: 1000, reason: 'یک' });
  assert.equal((await refundRows(o.id)).length, 1);
  const others = Number((await one('SELECT COUNT(*)::int AS n FROM customer_notify_outbox WHERE refund_id IS NULL')).n);
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '496_customer_notify_refund.down.sql'), 'utf8');
  await query(down);
  const cols = (await many(`SELECT column_name FROM information_schema.columns WHERE table_name='customer_notify_outbox'`)).map((r) => r.column_name);
  assert.ok(!cols.includes('refund_id'));
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM customer_notify_outbox')).n), others, 'پیام‌های دیگر ماندند');
  assert.equal((await ledgerOf(o.id)).length, 1, 'دفتر ماند');
  await fails(`INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, lang, phone_key, status, next_attempt_at, created_at, updated_at)
                VALUES ($1,$2,$3,'order_refund:x','fa','k','pending',1,1,1)`, [newId('cno'), s.shopId, o.id], '23514');
  const migrate = require('../src/migrate');
  assert.deepEqual(await migrate.run(), ['496_customer_notify_refund.sql']);
  await refund(s, o.id, { amountMinor: 1000, reason: 'دو' });
  assert.equal((await refundRows(o.id)).length, 1, 'پس از دوباره بالا آمدن همان رفتار');
});
