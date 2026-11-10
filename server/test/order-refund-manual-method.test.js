'use strict';
/**
 * ORD-15 / PAY-23 — «حساب‌پیِ دستی» در دفترِ بازپرداختِ سفارش (تصمیمِ ۱-۶ ب، ۱۴۰۵/۰۸/۱۹؛ مهاجرتِ ۷۷۰).
 *
 * تحویلِ بخشی پس از رفتنِ پیک: مشتری دمِ در یک قلم را پس داد، یا پس از تحویل برگرداند. سفارشِ پرداخت‌شده‌ای که
 * پولش با حساب‌پی آمده بود باید با همان روش پس داده شود؛ تا حساب‌پیِ خودکار، دکان خودش پس می‌دهد و فقط **ثبت** می‌کند.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `shop.orders.manual_refund` خاموش ⇒ همان رفتارِ پیشین: `hesabpay` ۴۰۰ `bad_method`، هیچ ردیف، و
 *      `/orders/config` کلیدِ `refundMethods` ندارد.
 *   ۲) روشن + `orders.refund` ⇒ `refundMethods: ['cash','hesabpay']`؛ ردیفِ دفتر با روشِ `hesabpay`، رویدادِ `payment`
 *      با همان روش، بستنِ جزئی با `final`، تکرارِ همان کلید ⇒ همان ردیف؛ شاگرد نه کلید را می‌بیند نه ثبت می‌کند.
 *   ۳) CHECK ِ پایگاه فقط `cash`/`hesabpay`؛ rollback ِ ۷۷۰ با ردیفِ حساب‌پی شکست می‌خورد (سابقهٔ مالی پاک نمی‌شود).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const R = require('../src/lib/order-refunds');

let admin = '';

test.before(async () => {
  await h.start();
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

async function readyShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const a = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(a.shopId, 'shop.orders', true);
  await setFlag(a.shopId, R.FLAG_KEY, true);
  return a;
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}

let keySeq = 0;
const freshKey = () => `manual-refund-${++keySeq}-${Date.now()}`;
//  ۱٫۵ × ۳۳٫۳۳ ⇒ ۵۰ افغانی، ۲ × ۱۲٫۵ ⇒ ۲۵ افغانی ⇒ ۷۵۰۰ واحدِ کوچک
const TOTAL = 7500;
const create = (token) => h.post('/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
  lines: [
    { productId: 'p1', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 3333 },
    { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 },
  ],
}, { token, headers: { 'Idempotency-Key': freshKey() } });
const refund = (token, id, body, key = freshKey()) =>
  h.post(`/api/shop/orders/${id}/refunds`, body, { token, headers: { 'Idempotency-Key': key } });
const config = async (token) => (await h.get('/api/shop/orders/config', { token })).body;
const rows = (id) => many('SELECT * FROM online_order_refunds WHERE order_id=$1 ORDER BY seq', [id]);

/** سفارشی که به مشتری رسید و یک قلمش برگشت ⇒ «درخواستِ برگشت» */
async function returned(token) {
  const made = await create(token);
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = made.body.order;
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']) {
    const r = await h.post(`/api/shop/orders/${o.id}/transition`, { to }, { token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
  return o;
}

test('ورودی: «حساب‌پی» شکلِ درستِ روش است؛ روشِ ناشناخته همان ۴۰۰ ِ «فقط نقدی»', () => {
  assert.equal(R.parse({ amountMinor: 100, reason: 'قلمِ ردشده دمِ در', method: 'hesabpay' }).method, 'hesabpay');
  assert.throws(() => R.parse({ amountMinor: 100, reason: 'x', method: 'card' }), /نقدی/);
  assert.throws(() => R.parse({ amountMinor: 100, reason: 'x', method: 'HESABPAY' }), /نقدی/);
  assert.deepEqual([...R.METHODS], ['cash']);
  assert.deepEqual([...R.MANUAL_METHODS], ['cash', 'hesabpay']);
});

test('تصمیمِ ۱-۶ ب خاموش: حساب‌پی ۴۰۰ و بی ردیف؛ پیکربندی بی `refundMethods`', async () => {
  const a = await readyShop('حساب‌پیِ خاموش');
  const cfg = await config(a.token);
  assert.equal(cfg.refundLedger, true);
  assert.equal('refundMethods' in cfg, false, 'خاموش ⇒ همان پاسخِ پیشین');
  const o = await returned(a.token);
  const r = await refund(a.token, o.id, { amountMinor: 2500, reason: 'نوشابه دمِ در پس داده شد', method: 'hesabpay' });
  assert.equal(r.status, 400, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'bad_method');
  assert.equal((await rows(o.id)).length, 0);
  const cash = await refund(a.token, o.id, { amountMinor: 2500, reason: 'نقد' });
  assert.equal(cash.status, 201, 'نقد مثلِ پیش');
});

test('ORD-15: پس از رفتنِ پیک، کسرِ قلمِ ردشده با «حساب‌پیِ دستی» ثبت و سفارش جزئی بسته می‌شود', async () => {
  const a = await readyShop('حساب‌پیِ دستی');
  await setFlag(a.shopId, 'shop.orders.manual_refund', true);
  const clerk = await member(a.shopId, 'staff', 'شاگردِ حساب‌پی');
  assert.deepEqual((await config(a.token)).refundMethods, ['cash', 'hesabpay']);
  assert.equal('refundMethods' in (await config(clerk.token)), false, 'شاگرد بی `orders.refund` کلید را نمی‌بیند');

  const o = await returned(a.token);
  const denied = await refund(clerk.token, o.id, { amountMinor: 2500, reason: 'x', method: 'hesabpay' });
  assert.equal(denied.status, 403, JSON.stringify(denied.body));

  const key = freshKey();
  const body = { amountMinor: 2500, reason: 'نوشابه دمِ در پس داده شد؛ با حساب‌پی برگرداندم', method: 'hesabpay', final: true };
  const r = await refund(a.token, o.id, body, key);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.entry.method, 'hesabpay');
  assert.equal(r.body.order.status, 'refunded_partial');
  assert.equal(r.body.order.refund.refundedMinor, 2500);
  assert.equal(r.body.order.refund.netMinor, TOTAL - 2500);
  assert.equal(r.body.order.refund.issue, null);
  const pay = r.body.order.events.filter((e) => e.kind === 'payment').at(-1);
  assert.equal(pay.detail.refund.method, 'hesabpay');

  //  تکرارِ همان کلید ⇒ همان ردیف، نه دومی
  const again = await refund(a.token, o.id, body, key);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  const saved = await rows(o.id);
  assert.deepEqual(saved.map((x) => [x.method, Number(x.amount_minor), x.final]), [['hesabpay', 2500, true]]);

  //  پرچم دوباره خاموش ⇒ ردیفِ ثبت‌شده می‌ماند، حساب‌پیِ تازه نه
  await setFlag(a.shopId, 'shop.orders.manual_refund', false);
  assert.equal('refundMethods' in (await config(a.token)), false);
  //  تکرارِ همان درخواست پس از خاموشی همان پاسخِ ثبت‌شده است (یکتایی پیش از دروازهٔ روش)
  const late = await refund(a.token, o.id, body, key);
  assert.equal(late.status, 200, JSON.stringify(late.body));
  assert.equal(late.body.entry.method, 'hesabpay');
  const o2 = await returned(a.token);
  const off = await refund(a.token, o2.id, { amountMinor: 100, reason: 'x', method: 'hesabpay' });
  assert.equal(off.status, 400);
  assert.equal((await rows(o.id)).length, 1, 'سابقه ماند');
});

test('مهاجرتِ ۷۷۰: CHECK فقط نقد و حساب‌پی؛ برگشت با ردیفِ حساب‌پی شکست می‌خورد، بی آن برمی‌گردد و دوباره بالا می‌آید', async () => {
  const a = await readyShop('قیدِ روش');
  const o = await returned(a.token);
  const ins = (id, seq, method) => query(
    `INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, method, reason, created_at)
     VALUES ($1,$2,$3,$4,$1,'h',100,$5,'x',1)`, [id, o.id, a.shopId, seq, method]);
  await ins('m-1', 1, 'hesabpay');
  await assert.rejects(ins('m-2', 2, 'card'), (err) => { assert.equal(err.code, '23514', err.message); return true; });

  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '770_order_refund_manual_method.down.sql'), 'utf8');
  await assert.rejects(query(down), (err) => { assert.equal(err.code, '23514', err.message); return true; });
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM online_order_refunds WHERE order_id=$1', [o.id])).n), 1, 'سابقه ماند');
  await assert.rejects(ins('m-2b', 2, 'card'), (err) => { assert.equal(err.code, '23514', err.message); return true; }, 'برگشتِ شکست‌خورده نیمه‌کاره نماند');

  await query("DELETE FROM online_order_refunds WHERE method='hesabpay'");
  await query(down);
  await assert.rejects(ins('m-3', 3, 'hesabpay'), (err) => { assert.equal(err.code, '23514', err.message); return true; });
  const migrate = require('../src/migrate');
  assert.deepEqual(await migrate.run(), ['770_order_refund_manual_method.sql']);
  await ins('m-4', 4, 'hesabpay');
});
