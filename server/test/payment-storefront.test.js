'use strict';
/**
 * PAY-21 روی ویترین و پنل: «پرداخت با حساب‌پی» برای سفارشِ آنلاینِ مشتری (توکنِ پیگیری)، صفحهٔ
 * پیگیری که «پرداخت‌شده» را فقط از سرور می‌گیرد، فهرست/تطبیق/بازپرداختِ مدیر و حالِ سرویس در پنل.
 */
process.env.PAYMENTS_PROVIDER = 'fake';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const P = require('../src/lib/payment-providers');

let admin = '';
let clerk = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'pay-sf-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'pay-sf-admin', password: 'Admin!12345' })).body.token;
  const made = await h.post('/api/admin/admins', { username: 'pay-clerk', password: 'Clerk!12345', role: 'admin' }, { token: admin });
  assert.ok(made.status < 300, JSON.stringify(made.body));
  clerk = (await h.post('/api/admin/login', { username: 'pay-clerk', password: 'Clerk!12345' })).body.token;
  assert.ok(admin && clerk);
});
test.after(async () => { await h.stop(); });

let n = 0;
const uniq = () => `${Date.now().toString(36)}${++n}`;
const flag = (scope, key, value = true) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

async function liveShop(label) {
  const u = await h.newUser(`صاحب-${label}`);
  const shopId = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken })).body.shop.id;
  for (const k of ['shop.orders', 'shop.storefront']) await flag(shopId, k);
  const slug = `pay-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const ops = [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 125 } }];
  const p = await h.api('POST', '/api/sync/v1/push', { token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-pay' }, body: { device_id: 'dev-pay', schema_version: 2, ops } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: u.accessToken })).status, 200);
  return { ...u, token: u.accessToken, shopId, slug };
}

async function order(s, phone = '0791230007') {
  const v = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: v.body.devCode });
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 2000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  return { token: ck.body.order.token, id: (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [s.shopId])).id };
}

const pay = (s, token, key = `pay-${uniq()}-key`) => h.post(`/api/store/${s.slug}/orders/${token}/pay`, {}, { headers: { 'Idempotency-Key': key } });

async function webhookPaid(intentId, amountMinor) {
  const sessionId = P.fake.sessionOf(intentId);
  const buf = Buffer.from(JSON.stringify({ eventId: `sf-${uniq()}`, sessionId, status: 'paid', amountMinor, currency: 'AFN', providerRef: 'HP-SF-1', settle: true }));
  return h.raw('POST', '/api/payments/webhook/fake', buf, { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(buf) } });
}

test('ویترین: دکمه فقط با پرچم + اشتراک؛ مبلغ از سرور؛ «پرداخت‌شده» فقط از وب‌هوک', async () => {
  const s = await liveShop('ویترینِ حساب‌پی');
  const o = await order(s);
  //  پیش از پرچم: نقد هنگامِ تحویل، بی دکمه
  let st = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(st.body.store.payment, { methods: ['cod'], online: { available: false, decision: 'D-08' } });
  let tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.body.order.onlinePay.available, false);
  const off = await pay(s, o.token);
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'online_payment_unavailable');

  //  پرچم بی اشتراک (دورهٔ آزمایشی) ⇒ هنوز نه
  await flag(s.shopId, 'pay.hesabpay');
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.store.payment.online.available, false);
  assert.equal((await pay(s, o.token)).body.error.code, 'online_payment_unavailable');

  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  st = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(st.body.store.payment, { methods: ['cod', 'hesabpay'], online: { available: true, provider: 'hesabpay' } });
  tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.body.order.onlinePay.available, true);
  assert.equal(tr.body.order.paymentStatus, 'unpaid');

  //  توکنِ دست‌کاری‌شده یا ویترینِ دیگر ⇒ ۴۰۴؛ بی کلید ⇒ ۴۰۰
  const flip = o.token.slice(0, -1) + (o.token.endsWith('A') ? 'B' : 'A');
  assert.equal((await pay(s, flip)).status, 404);
  assert.equal((await h.post(`/api/store/${s.slug}/orders/${o.token}/pay`, {})).body.error.code, 'idempotency_key_required');

  const key = `pay-${uniq()}-key`;
  const r = await pay(s, o.token, key);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.payment.status, 'pending');
  assert.equal(r.body.payment.amountMinor, 25000, 'دو کیلو × ۱۲۵ افغانی از دفترِ سرور');
  assert.match(r.body.payment.checkoutUrl, /^https:\/\//);
  assert.ok(!JSON.stringify(r.body).includes(o.id), 'شناسهٔ داخلیِ سفارش بیرون نمی‌رود');
  const again = await pay(s, o.token, key);
  assert.equal(again.status, 200);
  assert.equal(again.body.payment.checkoutUrl, r.body.payment.checkoutUrl);

  const intent = await one(`SELECT * FROM payment_intents WHERE ref_id=$1 AND kind='online_order'`, [o.id]);
  assert.equal(Number(intent.amount_minor), 25000);
  //  برگشتِ مرورگر از حساب‌پی چیزی را «پرداخت‌شده» نمی‌کند — فقط پیگیریِ سرور
  tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.body.order.paymentStatus, 'pending');

  const w = await webhookPaid(intent.id, 25000);
  assert.equal(w.body.outcome, 'applied', JSON.stringify(w.body));
  tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.body.order.paymentStatus, 'paid');
  assert.equal(tr.body.order.paymentMethod, 'online');
  assert.equal(tr.body.order.onlinePay.available, false);
  const paidAgain = await pay(s, o.token);
  assert.equal(paidAgain.status, 409);
});

test('پنلِ مدیر: فهرست با فیلتر، تطبیق و بازپرداخت فقط برای مدیرِ ارشد؛ حالِ سرویسِ پرداخت', async () => {
  const s = await liveShop('پنل');
  await flag(s.shopId, 'pay.hesabpay');
  const mk = (refId) => h.post('/api/shop/payments/intents',
    { kind: 'pos_sale', refId, amount: 9900, currency: 'AFN', idempotencyKey: `adm-${uniq()}-key` }, { token: s.token });
  const a = (await mk(`sale-${uniq()}`)).body;
  const b = (await mk(`sale-${uniq()}`)).body;
  assert.equal((await webhookPaid(a.id, 9900)).body.outcome, 'applied');

  const all = await h.get(`/api/admin/payments/intents?shopId=${s.shopId}`, { token: clerk });
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.deepEqual(all.body.items.map((x) => x.id).sort(), [a.id, b.id].sort());
  assert.ok(all.body.items.every((x) => !('checkoutUrl' in x) && !('qrPayload' in x)));
  const paid = await h.get(`/api/admin/payments/intents?status=paid&shopId=${s.shopId}`, { token: clerk });
  assert.deepEqual(paid.body.items.map((x) => x.id), [a.id]);
  assert.equal((await h.get('/api/admin/payments/intents?status=nope', { token: clerk })).status, 400);
  //  صفحه‌بندی
  const p1 = await h.get(`/api/admin/payments/intents?shopId=${s.shopId}&limit=1`, { token: clerk });
  assert.equal(p1.body.items.length, 1);
  const p2 = await h.get(`/api/admin/payments/intents?shopId=${s.shopId}&limit=1&before=${encodeURIComponent(p1.body.next)}`, { token: clerk });
  assert.equal(p2.body.items.length, 1);
  assert.notEqual(p2.body.items[0].id, p1.body.items[0].id);
  assert.equal((await h.get('/api/admin/payments/intents', { token: s.token })).status, 401);

  assert.equal((await h.post('/api/admin/payments/reconcile', {}, { token: clerk })).status, 403);
  const rc = await h.post('/api/admin/payments/reconcile', {}, { token: admin });
  assert.equal(rc.status, 200, JSON.stringify(rc.body));
  for (const k of ['checked', 'paid', 'closed', 'mismatched', 'errors']) assert.equal(typeof rc.body[k], 'number', k);

  assert.equal((await h.post(`/api/admin/payments/intents/${a.id}/refund`, {}, { token: clerk })).status, 403);
  const notPaid = await h.post(`/api/admin/payments/intents/${b.id}/refund`, {}, { token: admin });
  assert.equal(notPaid.status, 409);
  assert.equal(notPaid.body.error.code, 'not_refundable');
  const rf = await h.post(`/api/admin/payments/intents/${a.id}/refund`, { note: 'در حساب‌پی برگشت داده شد' }, { token: admin });
  assert.equal(rf.status, 200, JSON.stringify(rf.body));
  assert.equal(rf.body.status, 'refunded');
  assert.equal((await h.post(`/api/admin/payments/intents/${a.id}/refund`, {}, { token: admin })).body.unchanged, true);
  assert.ok(await one(`SELECT 1 AS x FROM audit_logs WHERE action='payment.refunded' AND target_id=$1`, [a.id]));
  //  برنامهٔ دکان هم «بازپرداخت» را می‌بیند
  assert.equal((await h.get(`/api/shop/payments/intents/${a.id}`, { token: s.token })).body.status, 'refunded');

  const prov = await h.get('/api/admin/system/providers', { token: admin });
  const p = prov.body.providers.find((x) => x.id === 'payment');
  assert.deepEqual([p.mode, p.state], ['fake', 'mock']);
});

test('حالِ سرویسِ پرداخت: بی کلید none، یک کلید incomplete، هر دو live (بی هیچ راز)', () => {
  const keep = { ...process.env };
  try {
    process.env.PAYMENTS_PROVIDER = '';
    delete process.env.HESABPAY_API_KEY; delete process.env.HESABPAY_WEBHOOK_SECRET;
    assert.deepEqual(P.providerState(), { mode: 'cod', state: 'none', missing: [], decision: 'D-08' });
    process.env.HESABPAY_API_KEY = 'k-secret-value';
    const half = P.providerState();
    assert.deepEqual([half.state, half.missing], ['incomplete', ['HESABPAY_WEBHOOK_SECRET']]);
    process.env.HESABPAY_WEBHOOK_SECRET = 'w-secret-value';
    const live = P.providerState();
    assert.equal(live.state, 'live');
    assert.ok(!JSON.stringify(live).includes('secret-value'));
  } finally {
    for (const k of ['PAYMENTS_PROVIDER', 'HESABPAY_API_KEY', 'HESABPAY_WEBHOOK_SECRET']) {
      if (keep[k] === undefined) delete process.env[k]; else process.env[k] = keep[k];
    }
  }
});

test('ویترین: پرداختِ رهاشده پس از زمانش با همان خواندنِ پیگیری «ناموفق» و دکمه دوباره', async () => {
  const s = await liveShop('رهاشده');
  await flag(s.shopId, 'pay.hesabpay');
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  const o = await order(s, '0791230008');
  assert.equal((await pay(s, o.token)).status, 201);
  let tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.body.order.paymentStatus, 'pending');
  assert.equal(tr.body.order.onlinePay.available, true, 'در انتظار هم دوباره پرداختنی است (همان جلسه)');
  await query(`UPDATE payment_intents SET expires_at=$2 WHERE ref_id=$1`, [o.id, Date.now() - 1000]);
  tr = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(tr.body.order.paymentStatus, 'failed');
  assert.equal(tr.body.order.onlinePay.available, true);
  const again = await pay(s, o.token);
  assert.equal(again.status, 201, JSON.stringify(again.body));
});
