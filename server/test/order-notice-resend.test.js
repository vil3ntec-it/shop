'use strict';
/**
 * ORD-16 — «فرستادنِ دوبارهٔ» پیامِ وضعیتِ سفارش از برنامهٔ مدیریت (`lib/notice-resend.js`،
 * `POST /api/shop/orders/:id/notices/:noticeId/resend`).
 *
 *   - پرچمِ `shop.orders.notice_resend` خاموش ⇒ ۴۰۳ و هیچ تغییر؛ `/config` بی کلید.
 *   - روشن: فقط پیامِ نرفته (`no_channel`/`failed`/`rate_limited`) همان ردیف دوباره `pending` و همان زنجیره
 *     (رفتِ دوباره با سرویس‌دهنده)؛ رویدادِ `note` و سابقه؛ تکرارِ کلید ⇒ همان پاسخ بی تلاشِ دوم.
 *   - «رفت»، پیامِ کنار‌رفته با پیامِ تازه‌تر، پیرتر از مهلت، بیش از سه بار، پرچمِ اعلانِ خاموش ⇒ ۴۰۹ و هیچ.
 *   - دکانِ دیگر با شناسهٔ واقعی ⇒ ۴۰۴ بی نشت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const orders = require('../src/lib/orders');
const N = require('../src/lib/customer-notify');
const R = require('../src/lib/notice-resend');

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
test.after(async () => { N.setProviders(null); await h.stop(); });
test.beforeEach(() => N.setProviders(null));

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, { on = true, notify = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  if (notify) await setFlag(o.shopId, N.FLAG_KEY, true);
  if (on) await setFlag(o.shopId, R.FLAG_KEY, true);
  return o;
}

/** سفارشِ ویترینی (همان شکلِ قیمت‌گذار، بی OTP) که «قبول» شد ⇒ ردیفِ `order_accepted` در صندوق */
async function accepted(o) {
  const out = await orders.create(o.shopId, {
    key: `nr-${uniq()}-key`, body: { n: uniq() }, actor: { type: 'customer', id: '' },
    price: async () => ({
      source: 'storefront', fulfillment: 'pickup', paymentMethod: 'cod', customerName: 'زرمینه', customerPhone: '+93791234567',
      address: {}, delivery: {}, note: '', isTest: false,
      lines: [{ lineNo: 1, productId: 'rice', name: 'برنج', unit: 'کیلو', qtyMilli: 1000, unitPriceMinor: 5000,
        lineTotalMinor: 5000, note: '', substitution: '' }],
      subtotalMinor: 5000, discountMinor: 0, deliveryFeeMinor: 0, totalMinor: 5000, scheduledFor: null, itemsCount: 1,
    }),
  });
  await orders.transition(o.shopId, out.order.id, { to: 'accepted' }, { type: 'user', id: o.user.id, role: 'owner' });
  return out.order.id;
}

const rowOf = (orderId, event = 'order_accepted') =>
  one('SELECT * FROM customer_notify_outbox WHERE order_id=$1 AND event=$2', [orderId, event]);
const resend = (o, orderId, noticeId, key = `rs-${uniq()}-key`) =>
  h.post(`/api/shop/orders/${orderId}/notices/${noticeId}/resend`, {}, { token: o.token, headers: { 'Idempotency-Key': key } });
const events = (orderId) => many(
  `SELECT actor_type, actor_id, from_status, to_status, detail FROM online_order_events
    WHERE order_id=$1 AND kind='note' AND detail->>'type'='notice_resend' ORDER BY seq`, [orderId]);

test('پرچم خاموش ⇒ ۴۰۳ و هیچ تغییر؛ `/config` بی کلید', async () => {
  const s = await shop('خاموش', { on: false });
  const id = await accepted(s);
  await N.tick();
  const row = await rowOf(id);
  assert.equal(row.status, 'no_channel');
  const r = await resend(s, id, row.id);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'notice_resend_disabled');
  assert.equal((await rowOf(id)).status, 'no_channel');
  assert.deepEqual(await events(id), []);
  assert.ok(!('noticeResend' in (await h.get('/api/shop/orders/config', { token: s.token })).body));
});

test('روشن: پیامِ نرفته همان ردیف دوباره در صف و با سرویس‌دهنده می‌رود؛ رویداد و سابقه؛ تکرارِ کلید ⇒ همان پاسخ', async () => {
  const s = await shop('دوباره');
  assert.deepEqual((await h.get('/api/shop/orders/config', { token: s.token })).body.noticeResend,
    { statuses: ['failed', 'no_channel', 'rate_limited'], maxResends: 3 });
  const id = await accepted(s);
  await N.tick();
  const before = await rowOf(id);
  assert.equal(before.status, 'no_channel', 'امروز هیچ کانالی وصل نیست');
  const rowsBefore = (await many('SELECT id FROM customer_notify_outbox WHERE order_id=$1', [id])).length;

  const key = `rs-${uniq()}-key`;
  const r = await resend(s, id, before.id, key);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.replayed, false);
  assert.equal(r.body.notice.id, before.id, 'همان ردیف، نه پیامِ تازه (NTF-04)');
  assert.equal(r.body.notice.status, 'pending');
  assert.equal(r.body.notice.attempts, 0);
  assert.equal((await many('SELECT id FROM customer_notify_outbox WHERE order_id=$1', [id])).length, rowsBefore);
  const ev = await events(id);
  assert.equal(ev.length, 1);
  assert.equal(ev[0].actor_type, 'user');
  assert.equal(ev[0].actor_id, s.user.id);
  assert.equal(ev[0].from_status, ev[0].to_status, 'وضعیتِ سفارش عوض نشد');
  assert.equal(ev[0].detail.noticeId, before.id);
  assert.equal(ev[0].detail.prevStatus, 'no_channel');
  assert.equal(ev[0].detail.event, 'order_accepted');
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.notice_resend'`, [s.shopId])).n, 1);

  //  دو کلیک / قطعِ نت پس از ثبت ⇒ همان پاسخ، بی رویداد یا تلاشِ دوم
  const again = await resend(s, id, before.id, key);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal((await events(id)).length, 1);
  //  همان کلید برای پیامِ دیگرِ همین سفارش ⇒ ۴۰۹ (کلید در دامنهٔ همان سفارش است، مثلِ «عقب انداختن»)
  const received = await rowOf(id, 'order_received');
  const reused = await resend(s, id, received.id, key);
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');

  //  حالا یک کانال وصل است ⇒ همان ردیف می‌رود
  const sent = [];
  N.setProviders({ push: { send: async (m) => { sent.push(m); return { status: 'accepted', ref: 'r1' }; } } });
  await N.tick();
  const after = await rowOf(id);
  assert.equal(after.status, 'sent');
  assert.equal(after.channel, 'push');
  assert.ok(sent.some((m) => m.orderId === id && m.event === 'order_accepted'));
  //  خطِ زمانیِ جزئیات همان رویداد را دارد (ORD-16)؛ پیام‌ها همان `/notices`
  const d = (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order;
  assert.ok(d.events.some((e) => e.kind === 'note' && e.detail.type === 'notice_resend' && e.actorType === 'user'));
  const list = (await h.get(`/api/shop/orders/${id}/notices`, { token: s.token })).body;
  assert.equal(list.items.find((x) => x.id === before.id).status, 'sent');

  //  «رفت» دوباره نمی‌رود (پیامِ دوتایی)
  const dup = await resend(s, id, before.id);
  assert.equal(dup.status, 409);
  assert.equal(dup.body.error.code, 'notice_not_resendable');
  assert.equal(dup.body.error.current, 'sent');
});

test('حداکثر سه بار؛ پیامِ کهنه‌تر، پیرتر از مهلت و پرچمِ اعلانِ خاموش ⇒ ۴۰۹ و هیچ', async () => {
  const s = await shop('سقف');
  const id = await accepted(s);
  await N.tick();
  const row = await rowOf(id);
  for (let i = 1; i <= R.MAX_RESENDS; i++) {
    const r = await resend(s, id, row.id);
    assert.equal(r.status, 200, `${i}: ${JSON.stringify(r.body)}`);
    await N.tick();
    assert.equal((await rowOf(id)).status, 'no_channel');
  }
  const over = await resend(s, id, row.id);
  assert.equal(over.status, 409);
  assert.equal(over.body.error.code, 'resend_limit');
  assert.equal(over.body.error.max, 3);
  assert.equal((await rowOf(id)).status, 'no_channel');

  //  پیامِ تازه‌تر برای همان سفارش ⇒ کهنه دوباره نمی‌رود
  const id2 = await accepted(s);
  await N.tick();
  const older = await rowOf(id2);
  await orders.transition(s.shopId, id2, { to: 'cancelled', reason: 'out_of_stock' }, { type: 'user', id: s.user.id, role: 'owner' });
  await N.tick();
  assert.equal((await rowOf(id2, 'order_cancelled')).status, 'no_channel');
  const stale = await resend(s, id2, older.id);
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'notice_superseded');
  assert.equal((await resend(s, id2, (await rowOf(id2, 'order_cancelled')).id)).status, 200, 'تازه‌ترین آری');

  //  پیرتر از مهلتِ کارگر ⇒ دیگر به کار نمی‌آید
  const id3 = await accepted(s);
  await N.tick();
  const old = await rowOf(id3);
  await query('UPDATE customer_notify_outbox SET created_at = created_at - $2 WHERE order_id=$1', [id3, N.MAX_AGE_MS + 60_000]);
  const tooOld = await resend(s, id3, old.id);
  assert.equal(tooOld.status, 409);
  assert.equal(tooOld.body.error.code, 'notice_too_old');

  //  پیام به مشتری خاموش شد ⇒ دوباره نمی‌رود (کارگر لغوش می‌کرد)
  const id4 = await accepted(s);
  await N.tick();
  await setFlag(s.shopId, N.FLAG_KEY, false);
  const off = await resend(s, id4, (await rowOf(id4)).id);
  assert.equal(off.status, 409);
  assert.equal(off.body.error.code, 'notify_disabled');
  assert.equal((await rowOf(id4)).status, 'no_channel');
  for (const x of [id2, id3, id4]) assert.ok((await events(x)).length <= 1, x);
});

test('دکانِ دیگر با شناسه‌های واقعی ⇒ ۴۰۴ بی نشت؛ شناسهٔ پیامِ سفارشِ دیگرِ همان دکان ⇒ ۴۰۴', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  const id = await accepted(a);
  const id2 = await accepted(a);
  await N.tick();
  const row = await rowOf(id);
  const cross = await resend(b, id, row.id);
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
  assert.ok(!JSON.stringify(cross.body).includes(row.id));
  const mixed = await resend(a, id2, row.id);
  assert.equal(mixed.status, 404);
  assert.equal(mixed.body.error.code, 'notice_not_found');
  assert.equal((await resend(a, id, 'not-an-id')).status, 404);
  assert.equal((await rowOf(id)).status, 'no_channel');
  assert.deepEqual(await events(id), []);
});
