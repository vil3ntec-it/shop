'use strict';
/**
 * ORD-19 — کارِ گروهی روی سفارش‌ها (lib/order-bulk.js، `POST /api/shop/orders/bulk/transition`).
 *
 *   - پرچمِ `shop.orders.bulk` خاموش ⇒ ۴۰۳ و هیچ تغییر.
 *   - هر سفارش از همان درِ `orders.transition`: نتیجهٔ هر کدام جدا و به همان ترتیب؛ یکی که نشد بقیه را نگه
 *     نمی‌دارد؛ رویداد و سابقهٔ عملیات برای هر سفارشِ عوض‌شده (`bulk: true`).
 *   - تکرارِ همان درخواست ⇒ `unchanged`، بی رویداد و سابقهٔ دوم؛ `from` ِ ناهمخوان ⇒ `status_changed`.
 *   - دلیلِ لازم، گذارِ نامجاز، سفارشِ دکانِ دیگر (پیدا نشد)، بازپرداخت و ردِ خودکار (گروهی نه)، ورودیِ بد.
 *   - اشتراکِ تمام‌شده (SUB-10) و بی ورود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;
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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  if (on) await setFlag(o.shopId, 'shop.orders.bulk', true);
  return o;
}

async function newOrder(s) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'گروهی' }, lines: [{ name: 'نان', qtyMilli: 1000, unitPriceMinor: 2000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `bk-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order.id;
}
const bulk = (s, body, token = s.token) => h.post('/api/shop/orders/bulk/transition', body, { token });
const statusOf = async (id) => (await one('SELECT status, ready_at FROM online_orders WHERE id=$1', [id]));
const statusEvents = async (id) => (await one(`SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1 AND kind='status'`, [id])).n;
const bulkAudits = async (s) => (await many(
  `SELECT target_id, detail FROM audit_logs WHERE shop_id=$1 AND action='order.transition'`, [s.shopId]
)).filter((r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail).bulk === true);

test('پرچم خاموش ⇒ ۴۰۳ order_bulk_disabled و هیچ تغییر', async () => {
  const s = await shop('خاموش', { on: false });
  const id = await newOrder(s);
  const r = await bulk(s, { ids: [id], to: 'accepted' });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'order_bulk_disabled');
  assert.equal((await statusOf(id)).status, 'new');
  //  `/config` بی کلیدِ `bulk` (همان پاسخِ پیشین) ⇒ برنامه انتخابِ چندتایی نمی‌سازد؛ روشن ⇒ `bulk: true`
  const cOff = await h.get('/api/shop/orders/config', { token: s.token });
  assert.equal(cOff.status, 200);
  assert.ok(!('bulk' in cOff.body), JSON.stringify(Object.keys(cOff.body)));
  await setFlag(s.shopId, 'shop.orders.bulk', true);
  assert.equal((await h.get('/api/shop/orders/config', { token: s.token })).body.bulk, true);
});

test('قبولِ گروهی: هر سفارش جدا، به همان ترتیب؛ نامجاز بقیه را نگه نمی‌دارد؛ تکرار بی اثرِ دوم', async () => {
  const s = await shop('گروهی');
  const [a, b, c, done, gone] = [await newOrder(s), await newOrder(s), await newOrder(s), await newOrder(s), await newOrder(s)];
  assert.equal((await h.post(`/api/shop/orders/${done}/transition`, { to: 'accepted' }, { token: s.token })).status, 200);
  assert.equal((await h.post(`/api/shop/orders/${gone}/transition`, { to: 'rejected', reason: 'busy' }, { token: s.token })).status, 200);
  const t0 = Date.now();
  const r = await bulk(s, { ids: [a, done, gone, b, c], to: 'accepted', readyInMinutes: 20 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.id, x.ok, x.unchanged ?? null, x.code ?? null]), [
    [a, true, false, null], [done, true, true, null], [gone, false, null, 'invalid_transition'], [b, true, false, null], [c, true, false, null],
  ]);
  assert.deepEqual([r.body.okCount, r.body.failedCount], [4, 1]);
  for (const id of [a, b, c]) {
    const row = await statusOf(id);
    assert.equal(row.status, 'accepted');
    assert.ok(Number(row.ready_at) >= t0 + 20 * 60_000);
    assert.equal(await statusEvents(id), 1);
  }
  assert.equal((await statusOf(gone)).status, 'rejected');
  assert.deepEqual((await bulkAudits(s)).map((x) => x.target_id).sort(), [a, b, c].sort());

  //  همان درخواست دوباره (پاسخِ گم‌شده) ⇒ همه unchanged، بی رویداد و سابقهٔ دوم
  const again = await bulk(s, { ids: [a, done, gone, b, c], to: 'accepted', readyInMinutes: 20 });
  assert.equal(again.status, 200);
  assert.deepEqual(again.body.results.filter((x) => x.ok).map((x) => x.unchanged), [true, true, true, true]);
  for (const id of [a, b, c]) assert.equal(await statusEvents(id), 1);
  assert.equal((await bulkAudits(s)).length, 3);

  //  `from` ِ ناهمخوان برای یکی ⇒ همان یکی status_changed با وضعیتِ جاری
  assert.equal((await h.post(`/api/shop/orders/${b}/transition`, { to: 'picking' }, { token: s.token })).status, 200);
  const pick = await bulk(s, { ids: [a, b], to: 'picking', from: 'accepted' });
  assert.deepEqual(pick.body.results.map((x) => [x.ok, x.code ?? null, x.current ?? null, x.unchanged ?? null]),
    [[true, null, null, false], [true, null, null, true]], 'b همان picking ⇒ unchanged');
  const pack = await bulk(s, { ids: [a, c], to: 'packed', from: 'picking' });
  assert.deepEqual(pack.body.results.map((x) => [x.ok, x.code ?? null, x.current ?? null]),
    [[true, null, null], [false, 'status_changed', 'accepted']]);
});

test('ردِ گروهی دلیل می‌خواهد؛ «دیگر» توضیح؛ سفارشِ دکانِ دیگر «پیدا نشد» بی نشت', async () => {
  const s = await shop('رد');
  const other = await shop('دیگر');
  const [a, b] = [await newOrder(s), await newOrder(s)];
  const foreign = await newOrder(other);
  let r = await bulk(s, { ids: [a, b], to: 'rejected' });
  assert.equal(r.status, 200);
  assert.deepEqual(r.body.results.map((x) => x.code), ['reason_required', 'reason_required']);
  r = await bulk(s, { ids: [a], to: 'rejected', reason: 'other' });
  assert.equal(r.body.results[0].code, 'note_required');
  r = await bulk(s, { ids: [a, foreign, b], to: 'rejected', reason: 'out_of_stock' });
  assert.deepEqual(r.body.results.map((x) => [x.ok, x.code ?? null]), [[true, null], [false, 'order_not_found'], [true, null]]);
  assert.ok(!JSON.stringify(r.body).includes('گروهی'), 'هیچ دادهٔ سفارشِ بیگانه');
  assert.equal((await statusOf(foreign)).status, 'new', 'سفارشِ دکانِ دیگر دست نخورد');
  assert.equal((await statusOf(a)).status, 'rejected');
});

test('ورودیِ بد ⇒ ۴۰۰ بی هیچ تغییر؛ بازپرداخت و ردِ خودکار گروهی نیستند', async () => {
  const s = await shop('ورودی');
  const id = await newOrder(s);
  const many51 = Array.from({ length: 51 }, (_, i) => `o${i}`);
  for (const [body, code] of [
    [{ to: 'accepted' }, 'bad_ids'], [{ ids: [], to: 'accepted' }, 'bad_ids'], [{ ids: many51, to: 'accepted' }, 'bad_ids'],
    [{ ids: [id, id], to: 'accepted' }, 'bad_ids'], [{ ids: ['bad id!'], to: 'accepted' }, 'bad_ids'], [{ ids: 'x', to: 'accepted' }, 'bad_ids'],
    [{ ids: [id], to: 'nope' }, 'bad_status'], [{ ids: [id], to: 'refunded_full' }, 'bulk_not_allowed'],
    [{ ids: [id], to: 'refunded_partial' }, 'bulk_not_allowed'], [{ ids: [id], to: 'auto_rejected' }, 'bulk_not_allowed'],
  ]) {
    const r = await bulk(s, body);
    assert.equal(r.status, 400, `${JSON.stringify(body).slice(0, 80)} ⇒ ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code);
  }
  assert.equal((await statusOf(id)).status, 'new');
});

test('بی ورود ۴۰۱؛ اشتراکِ تمام‌شده ⇒ ۴۰۳ subscription_* (SUB-10)', async () => {
  const s = await shop('اشتراک');
  const id = await newOrder(s);
  assert.equal((await h.post('/api/shop/orders/bulk/transition', { ids: [id], to: 'accepted' }, {})).status, 401);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]);
  const r = await bulk(s, { ids: [id], to: 'accepted' });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.ok(['subscription_expired', 'subscription_required'].includes(r.body.error.code));
  assert.equal((await statusOf(id)).status, 'new');
});
