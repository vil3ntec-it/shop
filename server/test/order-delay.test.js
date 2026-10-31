'use strict';
/**
 * ORD-07 — «عقب انداختنِ» سفارش (lib/order-delay.js، `POST /api/shop/orders/:id/delay`).
 *
 *   - پرچمِ `shop.orders.delay` خاموش ⇒ ۴۰۳ `order_delay_disabled` و هیچ ردیفی؛ `/config` ⇒ `delay: null`.
 *   - زمان روی زمانِ ذخیره‌شدهٔ سرور جلو می‌رود (نه ساعتِ کلاینت)؛ رویدادِ `note` با زمانِ پیشین و تازه،
 *     دلیل، توضیح و کننده؛ وضعیت همان؛ نسخه یکی بالا؛ سابقهٔ عملیات.
 *   - مشتری زمانِ تازه را در پیگیری می‌بیند و خطِ زمانیِ او رویدادِ داخلی را ندارد.
 *   - تکرارِ همان کلید ⇒ دوباره عقب نمی‌رود (حتی هم‌زمان)؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹.
 *   - دو کاربر با زمانِ دیده‌شدهٔ یکسان هم‌زمان ⇒ یکی ۲۰۰، دیگری ۴۰۹ `ready_changed` با زمانِ تازه.
 *   - فقط قبول‌شده / جمع‌آوری / بسته‌بندی؛ دیرشده از اکنون؛ بی زمان از اکنون؛ سقفِ هفت روز؛ ورودیِ بد ۴۰۰.
 *   - دکانِ دیگر ⇒ ۴۰۴؛ بی اشتراک ⇒ ۴۰۳ subscription_*.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const MIN = 60_000;
const DAY = 24 * 60 * MIN;
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
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, { delay = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  if (delay) await setFlag(o.shopId, 'shop.orders.delay', true);
  return o;
}

const key = () => `delay-${uniq()}-key`;
const newOrder = async (s, over = {}) => {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 12000 }], ...over,
  }, { token: s.token, headers: { 'Idempotency-Key': key() } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
};
const move = async (s, id, to, extra = {}) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...extra }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order;
};
const delay = (s, id, body, k = key()) =>
  h.post(`/api/shop/orders/${id}/delay`, body, { token: s.token, headers: k ? { 'Idempotency-Key': k } : {} });
const delayEvents = (id) => many(
  `SELECT seq, kind, from_status, to_status, actor_type, actor_id, reason, note, detail
     FROM online_order_events WHERE order_id=$1 AND kind='note' ORDER BY seq`, [id]);
const readyOf = async (id) => Number((await one('SELECT ready_at FROM online_orders WHERE id=$1', [id])).ready_at);

test('پرچم خاموش: نه دکمه (`delay: null`) نه در؛ هیچ رویداد یا تغییری', async () => {
  const s = await shop('خاموش', { delay: false });
  const cfg = await h.get('/api/shop/orders/config', { token: s.token });
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.delay, null);
  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  const r = await delay(s, o.id, { minutes: 10 });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'order_delay_disabled');
  assert.equal((await delayEvents(o.id)).length, 0);
  assert.equal(await readyOf(o.id), o.readyAt);
});

test('عقب انداختن: روی زمانِ سرور، رویداد با زمانِ پیشین و تازه، دلیل و کننده؛ وضعیت همان؛ سابقه', async () => {
  const s = await shop('عقب');
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.deepEqual(cfg.delay, {
    statuses: ['accepted', 'picking', 'packed'],
    reasons: ['busy', 'out_of_stock', 'courier', 'customer_request', 'other'],
    maxMinutes: 1440,
  });
  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  const r = await delay(s, o.id, { minutes: 20, reason: 'busy', note: 'سه سفارشِ پیش', expectedReadyAt: o.readyAt });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.replayed, false);
  assert.equal(r.body.order.readyAt, o.readyAt + 20 * MIN, 'پایه همان زمانِ ذخیره‌شده است');
  assert.equal(r.body.order.status, 'accepted');
  assert.equal(r.body.order.version, o.version + 1);
  assert.equal(await readyOf(o.id), o.readyAt + 20 * MIN);

  const ev = await delayEvents(o.id);
  assert.equal(ev.length, 1);
  assert.deepEqual(
    [ev[0].from_status, ev[0].to_status, ev[0].actor_type, ev[0].actor_id, ev[0].reason, ev[0].note],
    ['accepted', 'accepted', 'user', s.user.id, 'busy', 'سه سفارشِ پیش']
  );
  assert.equal(ev[0].detail.type, 'ready_delay');
  assert.equal(ev[0].detail.prevReadyAt, o.readyAt);
  assert.equal(ev[0].detail.readyAt, o.readyAt + 20 * MIN);
  assert.equal(ev[0].detail.minutes, 20);
  //  همان رویداد در خطِ زمانیِ جزئیات (ORD-16)
  const last = r.body.order.events.at(-1);
  assert.equal(last.kind, 'note');
  assert.equal(last.detail.type, 'ready_delay');
  assert.ok(Number(last.seq) > 1);

  const aud = await one(`SELECT user_id, detail FROM audit_logs WHERE action='order.delay' AND target_id=$1`, [o.id]);
  assert.ok(aud, 'سابقهٔ عملیات');
  assert.equal(aud.user_id, s.user.id);
  const d = typeof aud.detail === 'string' ? JSON.parse(aud.detail) : aud.detail;
  assert.deepEqual([d.readyAt, d.prevReadyAt, d.minutes, d.reason], [o.readyAt + 20 * MIN, o.readyAt, 20, 'busy']);

  //  دوباره: از زمانِ تازه (جمعِ پیاپی)، در «جمع‌آوری» و «بسته‌بندی» هم
  await move(s, o.id, 'picking');
  const r2 = await delay(s, o.id, { minutes: 5 });
  assert.equal(r2.status, 200, JSON.stringify(r2.body));
  assert.equal(r2.body.order.readyAt, o.readyAt + 25 * MIN);
  await move(s, o.id, 'packed');
  assert.equal((await delay(s, o.id, { minutes: 5 })).status, 200);
  assert.equal(await readyOf(o.id), o.readyAt + 30 * MIN);
  assert.equal((await delayEvents(o.id)).length, 3);
});

test('فقط پیش از آماده: تازه، آماده، تمام‌شده ⇒ ۴۰۹ delay_not_allowed با وضعیتِ جاری', async () => {
  const s = await shop('وضعیت');
  const fresh = await newOrder(s);
  let r = await delay(s, fresh.id, { minutes: 10 });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'delay_not_allowed');
  assert.equal(r.body.error.current, 'new');

  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  for (const to of ['picking', 'packed', 'ready_for_pickup']) await move(s, o.id, to);
  r = await delay(s, o.id, { minutes: 10 });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.current, 'ready_for_pickup');
  await move(s, o.id, 'picked_up');
  assert.equal((await delay(s, o.id, { minutes: 10 })).status, 409);
  const rej = await move(s, (await newOrder(s)).id, 'rejected', { reason: 'busy' });
  assert.equal((await delay(s, rej.id, { minutes: 10 })).status, 409);
  assert.equal((await many(`SELECT 1 FROM online_order_events WHERE shop_id=$1 AND kind='note'`, [s.shopId])).length, 0);
});

test('تکرارِ همان کلید ⇒ یک بار عقب؛ هم‌زمان هم؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹؛ بی کلید ⇒ ۴۰۰', async () => {
  const s = await shop('تکرار');
  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 30 });
  const k = key();
  const body = { minutes: 15, reason: 'courier', expectedReadyAt: o.readyAt };
  const first = await delay(s, o.id, body, k);
  assert.equal(first.status, 200, JSON.stringify(first.body));
  //  پاسخِ گم‌شده و فرستادنِ دوباره — حتی پس از جلو رفتنِ سفارش
  await move(s, o.id, 'picking');
  const again = await delay(s, o.id, body, k);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.order.readyAt, o.readyAt + 15 * MIN);
  assert.equal((await delayEvents(o.id)).length, 1);

  const other = await delay(s, o.id, { ...body, minutes: 16 }, k);
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');

  //  هم‌زمان با یک کلید ⇒ یک رویداد
  const o2 = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 30 });
  const k2 = key();
  const rs = await Promise.all([1, 2, 3, 4].map(() => delay(s, o2.id, { minutes: 10 }, k2)));
  assert.ok(rs.every((x) => x.status === 200), JSON.stringify(rs.map((x) => x.body)));
  assert.equal(rs.filter((x) => x.body.replayed === false).length, 1);
  assert.equal((await delayEvents(o2.id)).length, 1);
  assert.equal(await readyOf(o2.id), o2.readyAt + 10 * MIN);

  const nokey = await delay(s, o2.id, { minutes: 10 }, null);
  assert.equal(nokey.status, 400);
  assert.equal(nokey.body.error.code, 'idempotency_key_required');
});

test('دو کاربر با زمانِ دیده‌شدهٔ یکسان، هم‌زمان ⇒ یکی می‌رود، دیگری ۴۰۹ ready_changed با زمانِ تازه', async () => {
  const s = await shop('هم‌زمان');
  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 20 });
  const rs = await Promise.all([10, 30].map((m) => delay(s, o.id, { minutes: m, expectedReadyAt: o.readyAt })));
  const ok = rs.filter((x) => x.status === 200);
  const late = rs.filter((x) => x.status === 409);
  assert.equal(ok.length, 1, JSON.stringify(rs.map((x) => x.body)));
  assert.equal(late.length, 1);
  assert.equal(late[0].body.error.code, 'ready_changed');
  assert.equal(late[0].body.error.current, ok[0].body.order.readyAt);
  assert.equal((await delayEvents(o.id)).length, 1);
  //  بی `expectedReadyAt` ⇒ روی زمانِ تازه جمع می‌شود (کاربر عمداً می‌خواهد)
  const r = await delay(s, o.id, { minutes: 5 });
  assert.equal(r.body.order.readyAt, ok[0].body.order.readyAt + 5 * MIN);
});

test('دیرشده ⇒ از اکنون؛ بی زمان ⇒ از اکنون (و expectedReadyAt=null)؛ سقفِ هفت روز', async () => {
  const s = await shop('پایه');
  const late = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  await query('UPDATE online_orders SET ready_at=$2 WHERE id=$1', [late.id, Date.now() - 60 * MIN]);
  const t0 = Date.now();
  const r = await delay(s, late.id, { minutes: 10 });
  const t1 = Date.now();
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.order.readyAt >= t0 + 10 * MIN && r.body.order.readyAt <= t1 + 10 * MIN, 'نه پیش از اکنون');

  const bare = await move(s, (await newOrder(s)).id, 'accepted');
  assert.equal(bare.readyAt, null);
  const stale = await delay(s, bare.id, { minutes: 10, expectedReadyAt: 123 });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'ready_changed');
  assert.equal(stale.body.error.current, null);
  const t2 = Date.now();
  const ok = await delay(s, bare.id, { minutes: 10, expectedReadyAt: null });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.order.readyAt >= t2 + 10 * MIN);
  const ev = (await delayEvents(bare.id))[0];
  assert.equal(ev.detail.prevReadyAt, null);

  //  زمان‌بندی‌شدهٔ بی زمانِ آماده ⇒ از زمانِ خودِ سفارش
  const when = Date.now() + 3 * 60 * MIN;
  const sch = await move(s, (await newOrder(s, { scheduledFor: when })).id, 'accepted');
  const rs = await delay(s, sch.id, { minutes: 15 });
  assert.equal(rs.status, 200, JSON.stringify(rs.body));
  assert.equal(rs.body.order.readyAt, Number((await one('SELECT scheduled_for FROM online_orders WHERE id=$1', [sch.id])).scheduled_for) + 15 * MIN);

  const far = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  await query('UPDATE online_orders SET ready_at=$2 WHERE id=$1', [far.id, Date.now() + 7 * DAY - 5 * MIN]);
  const over = await delay(s, far.id, { minutes: 10 });
  assert.equal(over.status, 400);
  assert.equal(over.body.error.code, 'bad_ready_time');
});

test('ورودیِ بد ⇒ ۴۰۰ بی هیچ تغییر؛ دکانِ دیگر ⇒ ۴۰۴؛ بی ورود ⇒ ۴۰۱', async () => {
  const s = await shop('ورودی');
  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  for (const [body, code] of [
    [{}, 'bad_minutes'], [{ minutes: 0 }, 'bad_minutes'], [{ minutes: 1441 }, 'bad_minutes'],
    [{ minutes: 2.5 }, 'bad_minutes'], [{ minutes: '15' }, 'bad_minutes'], [{ minutes: -5 }, 'bad_minutes'],
    [{ minutes: 10, reason: 'lazy' }, 'bad_reason'], [{ minutes: 10, reason: 'other' }, 'note_required'],
    [{ minutes: 10, reason: 'other', note: '   ' }, 'note_required'],
    [{ minutes: 10, expectedReadyAt: 'x' }, 'bad_expected_ready_at'], [{ minutes: 10, expectedReadyAt: -1 }, 'bad_expected_ready_at'],
  ]) {
    const r = await delay(s, o.id, body);
    assert.equal(r.status, 400, `${JSON.stringify(body)} ⇒ ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code, JSON.stringify(body));
  }
  assert.equal(await readyOf(o.id), o.readyAt);
  assert.equal((await delayEvents(o.id)).length, 0);
  //  «دیگر» با توضیح ⇒ رواست
  assert.equal((await delay(s, o.id, { minutes: 10, reason: 'other', note: 'برق رفت' })).status, 200);

  const b = await shop('بیگانه');
  const r = await delay(b, o.id, { minutes: 10 });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'order_not_found');
  assert.equal((await h.post(`/api/shop/orders/${o.id}/delay`, { minutes: 10 }, {})).status, 401);
  assert.equal((await delayEvents(o.id)).length, 1);
});

test('اشتراکِ تمام‌شده ⇒ ۴۰۳ subscription_* و هیچ تغییر (SUB-10)', async () => {
  const s = await shop('بی‌اشتراک');
  const o = await move(s, (await newOrder(s)).id, 'accepted', { readyInMinutes: 15 });
  //  دورهٔ آزمایشی دیرزمانی تمام شده و اشتراکی نیست (همان راهِ paid-routes-entitlement)
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]);
  assert.equal((await require('../src/lib/orders').entitledOf(s.shopId)).entitled, false);
  const r = await delay(s, o.id, { minutes: 10 });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.ok(['subscription_expired', 'subscription_required'].includes(r.body.error.code));
  assert.equal(await readyOf(o.id), o.readyAt);
});

test('مشتری زمانِ تازه را در پیگیری می‌بیند؛ رویدادِ داخلی در خطِ زمانیِ او نیست', async () => {
  const s = await shop('ویترین');
  await setFlag(s.shopId, 'shop.storefront', true);
  const slug = `d-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ عقب', contactPhone: '0700000001' },
    { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-delay' },
    body: { device_id: 'dev-delay', schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  const phone = '0791113077';
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const ck = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(ck.status, 200, JSON.stringify(ck.body));
  const co = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': key(), 'X-Store-Ticket': ck.body.ticket } });
  assert.ok(co.status === 201 || co.status === 200, JSON.stringify(co.body));
  const token = co.body.order.token;
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1', [s.shopId])).id;
  const o = await move(s, id, 'accepted', { readyInMinutes: 15 });
  const r = await delay(s, id, { minutes: 25, reason: 'busy', note: 'یادداشتِ داخلی' });
  assert.equal(r.status, 200, JSON.stringify(r.body));

  const track = await h.get(`/api/store/${slug}/orders/${token}`);
  assert.equal(track.status, 200, JSON.stringify(track.body));
  const pub = track.body.order || track.body;
  assert.equal(pub.readyAt, o.readyAt + 25 * MIN);
  assert.deepEqual(pub.timeline.map((e) => e.status), ['new', 'accepted']);
  assert.ok(!JSON.stringify(track.body).includes('یادداشتِ داخلی'), 'توضیحِ کارمند به مشتری نمی‌رسد');
});
