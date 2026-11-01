'use strict';
/**
 * ORD-18 — هشدارِ «شروعِ سفارشِ زمان‌بندی‌شده» (lib/order-start-alert.js).
 *
 *   - پرچمِ `shop.orders.start_alert` خاموش ⇒ هیچ رویداد، سابقه یا اعلانی.
 *   - روشن ⇒ سفارشِ زمان‌بندی‌شدهٔ «تازه» یا «قبول‌شده» در ۳۰ دقیقهٔ پیش از زمانش (یا تا ۱۵ دقیقه پس از آن،
 *     هنوز شروع‌نشده) یک بار: رویدادِ `note` (`start_alert`) با بازیگرِ سیستم، سابقه، و پوش به اعضای دکان
 *     (همان پرچمِ اعلانِ سفارش). شروع‌شده، آزمایشی، دور، و خیلی دیر نه.
 *   - تکرارِ جاروب و جاروبِ هم‌زمان ⇒ یک هشدار؛ وضعیت عوض نمی‌شود؛ خطای اعلان رویداد را پس نمی‌گیرد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const alert = require('../src/lib/order-start-alert');

const MIN = 60_000;
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
test.after(async () => { alert.stop(); await h.stop(); });

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
  if (on) await setFlag(o.shopId, 'shop.orders.start_alert', true);
  return o;
}

/** سفارشِ دستیِ زمان‌بندی‌شده؛ زمان مستقیم روی ردیف گذاشته می‌شود تا مرزها دقیق باشند. */
async function scheduled(s, dueAt, { to = [], test: isTest = false } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'زمان‌دار' }, scheduledFor: Date.now() + 5 * 60 * MIN,
    lines: [{ name: 'کیک', qtyMilli: 1000, unitPriceMinor: 50000 }], ...(isTest ? { test: true } : {}),
  }, { token: s.token, headers: { 'Idempotency-Key': `sa-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const t of to) {
    const m = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to: t }, { token: s.token });
    assert.equal(m.status, 200, JSON.stringify(m.body));
  }
  await query('UPDATE online_orders SET scheduled_for=$2 WHERE id=$1', [r.body.order.id, dueAt]);
  return r.body.order.id;
}

const alerts = (shopId) => many(
  `SELECT order_id, from_status, to_status, actor_type, detail FROM online_order_events
    WHERE shop_id=$1 AND kind='note' AND detail->>'type'='start_alert' ORDER BY order_id`, [shopId]);

test('پرچم خاموش ⇒ هیچ هشدار، سابقه یا تغییری', async () => {
  const s = await shop('خاموش', { on: false });
  const t = Date.now();
  const id = await scheduled(s, t + 20 * MIN, { to: ['accepted'] });
  await alert.sweep(t);
  assert.deepEqual(await alerts(s.shopId), []);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.start_alert'`, [s.shopId])).n, 0);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [id])).status, 'accepted');
});

test('روشن: ۳۰ دقیقه پیش از زمان، یک بار؛ تازه و قبول‌شده آری، شروع‌شده/آزمایشی/دور/خیلی دیر نه؛ پوش به اعضا', async () => {
  const s = await shop('هشدار');
  await setFlag(s.shopId, 'shop.order_notify', true);
  const push = require('../src/lib/push');
  const realSend = push.sendTo;
  const pushed = [];
  push.sendTo = async (target, msg) => { pushed.push({ target, msg }); return { sent: 1, total: 1 }; };
  try {
    const t = Date.now();
    const acc = await scheduled(s, t + 20 * MIN, { to: ['accepted'] });
    const fresh = await scheduled(s, t + 30 * MIN);                       // درست سرِ مرز
    const late = await scheduled(s, t - 10 * MIN, { to: ['accepted'] });   // سرور خاموش بود؛ هنوز شروع نشده
    const far = await scheduled(s, t + 31 * MIN, { to: ['accepted'] });
    const started = await scheduled(s, t + 10 * MIN, { to: ['accepted', 'picking'] });
    const trial = await scheduled(s, t + 10 * MIN, { to: ['accepted'], test: true });
    const tooLate = await scheduled(s, t - 16 * MIN, { to: ['accepted'] });

    await alert.sweep(t);
    const got = await alerts(s.shopId);
    assert.deepEqual(got.map((e) => e.order_id).sort(), [acc, fresh, late].sort());
    for (const e of got) {
      assert.equal(e.actor_type, 'system');
      assert.equal(e.from_status, e.to_status, 'وضعیت عوض نشد');
      assert.equal(e.detail.type, 'start_alert');
    }
    assert.equal(got.find((e) => e.order_id === acc).detail.scheduledFor, t + 20 * MIN);
    for (const id of [far, started, trial, tooLate]) assert.ok(!got.some((e) => e.order_id === id), id);
    assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [acc])).status, 'accepted');
    assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.start_alert'`, [s.shopId])).n, 3);
    const mine = pushed.filter((p) => p.target.shopId === s.shopId);
    assert.equal(mine.length, 3);
    const m = mine.find((p) => p.msg.data.orderId === acc);
    assert.equal(m.msg.data.type, 'order_start_alert');
    assert.match(m.msg.body, /دقیقهٔ دیگر/);
    assert.ok(m.target.onlyUserIds.includes(s.user.id));
    assert.match(mine.find((p) => p.msg.data.orderId === late).msg.body, /رسیده/);

    //  دوباره و هم‌زمان ⇒ هیچ هشدارِ دوم
    await Promise.all([alert.sweep(t), alert.sweep(t), alert.sweep(t + 30_000)]);
    assert.equal((await alerts(s.shopId)).length, 3);
    assert.equal(pushed.filter((p) => p.target.shopId === s.shopId).length, 3);

    //  یک دقیقه بعد «دور» هم به ۳۰ دقیقه رسید
    await alert.sweep(t + 2 * MIN);
    assert.ok((await alerts(s.shopId)).some((e) => e.order_id === far));
    //  خطِ زمانیِ جزئیات همان رویداد را دارد (ORD-16)
    const d = (await h.get(`/api/shop/orders/${acc}`, { token: s.token })).body.order;
    assert.ok(d.events.some((e) => e.kind === 'note' && e.detail.type === 'start_alert' && e.actorType === 'system'));
  } finally {
    push.sendTo = realSend;
  }
});

test('خطای اعلان رویداد را پس نمی‌گیرد و دوباره هم نمی‌فرستد؛ اعلانِ خاموش ⇒ فقط رویداد', async () => {
  const s = await shop('اعلان');
  const t = Date.now();
  const quiet = await scheduled(s, t + 5 * MIN, { to: ['accepted'] });
  await alert.sweep(t);
  assert.deepEqual((await alerts(s.shopId)).map((e) => e.order_id), [quiet], 'بی پرچمِ اعلان هم رویداد');

  await setFlag(s.shopId, 'shop.order_notify', true);
  const push = require('../src/lib/push');
  const realSend = push.sendTo;
  let calls = 0;
  push.sendTo = async () => { calls++; throw new Error('FCM down'); };
  try {
    const id = await scheduled(s, t + 6 * MIN, { to: ['accepted'] });
    await alert.sweep(t);
    await alert.sweep(t + MIN);
    assert.ok((await alerts(s.shopId)).some((e) => e.order_id === id));
    assert.equal(calls, 1, 'یک بار، بی تکرارِ بی‌پایان');
  } finally {
    push.sendTo = realSend;
  }
});

test('چهار جاروبِ هم‌زمان روی سفارشِ تازه‌رسیده (چند نمونهٔ سرور) ⇒ یک هشدار', async () => {
  const s = await shop('هم‌زمان');
  const t = Date.now();
  const ids = [await scheduled(s, t + 12 * MIN, { to: ['accepted'] }), await scheduled(s, t + 14 * MIN)];
  await Promise.all([alert.sweep(t), alert.sweep(t), alert.sweep(t), alert.sweep(t)]);
  const got = await alerts(s.shopId);
  assert.deepEqual(got.map((e) => e.order_id).sort(), [...ids].sort());
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.start_alert'`, [s.shopId])).n, 2);
});

test('summary: هشدارِ هنوز شروع‌نشده برای برنامهٔ مدیریت (`startAlerts`) فقط با پرچم، فقط همین دکان؛ شروع ⇒ بیرون', async () => {
  const off = await shop('بی‌کلید', { on: false });
  const offId = await scheduled(off, Date.now() + 10 * MIN, { to: ['accepted'] });
  const sOff = await h.get('/api/shop/orders/summary', { token: off.token });
  assert.equal(sOff.status, 200);
  assert.ok(!('startAlerts' in sOff.body), 'پرچمِ خاموش ⇒ همان پاسخِ پیشین، بی کلید');

  const s = await shop('نشان');
  const other = await shop('همسایه');
  const t = Date.now();
  const a = await scheduled(s, t + 10 * MIN, { to: ['accepted'] });
  const b = await scheduled(s, t + 25 * MIN);
  const noAlertYet = await scheduled(s, t + 50 * MIN, { to: ['accepted'] });
  const theirs = await scheduled(other, t + 10 * MIN, { to: ['accepted'] });
  const before = (await h.get('/api/shop/orders/summary', { token: s.token })).body;
  assert.deepEqual(before.startAlerts, [], 'پیش از جاروب هیچ');

  await alert.sweep(t);
  const sum = (await h.get('/api/shop/orders/summary', { token: s.token })).body;
  assert.deepEqual(sum.startAlerts.map((x) => x.id), [a, b], 'به ترتیبِ زمان');
  const first = sum.startAlerts[0];
  assert.equal(first.status, 'accepted');
  assert.equal(first.scheduledFor, t + 10 * MIN);
  assert.ok(first.alertAt > 0 && Number.isSafeInteger(first.orderNo));
  assert.deepEqual(Object.keys(first).sort(), ['alertAt', 'id', 'orderNo', 'scheduledFor', 'status'], 'بی نام، شماره یا نشانی');
  for (const id of [noAlertYet, theirs, offId]) assert.ok(!sum.startAlerts.some((x) => x.id === id), id);

  //  شروعِ آماده‌سازی ⇒ از فهرست می‌رود (رویداد در خطِ زمانی می‌ماند)
  const m = await h.post(`/api/shop/orders/${a}/transition`, { to: 'picking' }, { token: s.token });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  const after = (await h.get('/api/shop/orders/summary', { token: s.token })).body;
  assert.deepEqual(after.startAlerts.map((x) => x.id), [b]);
  assert.ok((await alerts(s.shopId)).some((e) => e.order_id === a));
  //  همسایه هشدارِ خودش را می‌بیند، نه این دکان را
  await alert.sweep(t);
  assert.deepEqual((await h.get('/api/shop/orders/summary', { token: other.token })).body.startAlerts.map((x) => x.id), [theirs]);
  //  خیلی دیر (بیش از ۱۵ دقیقه پس از زمان) ⇒ بیرون
  await query('UPDATE online_orders SET scheduled_for=$2 WHERE id=$1', [b, t - 16 * MIN]);
  assert.deepEqual((await h.get('/api/shop/orders/summary', { token: s.token })).body.startAlerts, []);
});
