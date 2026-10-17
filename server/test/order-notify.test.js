'use strict';
/**
 * اعلانِ سفارش حتی وقتی برنامهٔ گوشی بسته است — v3 §۳۰ (NOTIF).
 *
 * آن‌چه این پرونده قفل می‌کند (بخشِ سرورِ §۳۰.۵؛ دستگاهِ واقعی و FCM
 * بیرونی‌اند و این‌جا آزموده نمی‌شوند — `push.setDeliver` جای شبکه می‌نشیند):
 *   ۱) ⛔ پیش‌فرض خاموش: بی `shop.order_notify` هیچ ردیفی نوشته نمی‌شود.
 *   ۲) ⛔ سفارشِ کارمند همان رفتارِ پیشین را دارد — هیچ اعلانی.
 *   ۳) NOTIF-01: سفارشِ مشتری + ردیفِ صف در **یک** تراکنش؛ شکستِ صف ⇒ سفارش
 *      هم ثبت نمی‌شود (NOTIF-07: «ثبت شد» بی صف گفته نمی‌شود).
 *   ۴) NOTIF-09: یکتایی با شناسهٔ سفارش؛ رسیدِ تکراری اثرِ دوم ندارد.
 *   ۵) پوش فقط به دستگاه‌های همان دکان، بی دادهٔ مشتری در متن.
 *   ۶) NOTIF-11: خطای پوش ⇒ تلاشِ محدود با فاصلهٔ فزاینده، سپس `failed`؛
 *      کانالِ پشتیبان حداکثر یک بار و امروز صادقانه «تنظیم نشده».
 *   ۷) بازنشدن ⇒ یادآوریِ محدود، سپس `unacked`؛ «باز شد» زنگ را می‌بندد.
 *   ۸) NOTIF-08: کارگری که وسطِ کار مرده، بعد از STALE دوباره می‌گیرد —
 *      بی سفارشِ تکراری.
 *   ۹) سفارشی که کسی رسیدگی کرده دیگر زنگ نمی‌زند؛ پرچمِ خاموش = قطعِ فوری.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const push = require('../src/lib/push');
const orders = require('../src/lib/orders');
const N = require('../src/lib/order-notify');

/** هر چه پوش شد، این‌جا می‌نشیند؛ `failNext` ⇒ سرویسِ پوش خطا می‌دهد. */
let sent = [];
let failing = false;
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
  push.setDeliver((row, message) => {
    if (failing) throw new Error('FCM 503');
    sent.push({ row, message });
  });
});
test.after(async () => {
  push.setDeliver(null);
  N.setFallback(null);
  await h.stop();
});

const CUSTOMER = Object.freeze({ type: 'customer', id: '' });
const STAFF = (u) => ({ type: 'user', id: u.user.id, role: 'owner' });
let keySeq = 0;
const freshKey = () => `notify-key-${++keySeq}-${Date.now()}`;
const sample = () => ({
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
  customer: { name: 'احمدِ مشتری', phone: '0791234567' },
  lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 5000 }],
});

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function setFlag(scope, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: N.FLAG_KEY, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** همان فراخوانی‌ای که ویترین (سفارشِ مشتری) می‌کند. */
const customerOrder = (shopId, key = freshKey(), extra = {}) =>
  orders.create(shopId, { key, body: sample(), actor: CUSTOMER, ...extra });

const rowsOf = (shopId) => many('SELECT * FROM order_notify_outbox WHERE shop_id=$1 ORDER BY created_at', [shopId]);
const sentTo = (shopId) => sent.filter((s) => s.message.data && s.message.data.shopId === shopId);

async function readyShop(name, tokens = ['tok']) {
  const a = await shopOwner(name);
  for (const t of tokens) {
    const r = await h.post('/api/me/push', { token: `${t}-${a.shopId}` }, { token: a.token });
    assert.equal(r.status, 201);
  }
  await setFlag(a.shopId, true);
  return a;
}

/* ══════════════════════════════════════════════════════════════════
   ۱–۲) پیش‌فرض خاموش؛ سفارشِ کارمند دست نمی‌خورد
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچم: سفارشِ مشتری ثبت می‌شود ولی هیچ ردیفِ اعلانی نه', async () => {
  const a = await shopOwner('خاموش');
  const out = await customerOrder(a.shopId);
  assert.equal(out.replayed, false);
  assert.ok(await one('SELECT id FROM online_orders WHERE id=$1', [out.order.id]));
  assert.equal((await rowsOf(a.shopId)).length, 0);

  const cfg = await h.get('/api/shop/order-notify/config', { token: a.token });
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  assert.equal(cfg.body.fallback.sms, 'not_configured');
  const pend = await h.get('/api/shop/order-notify/pending', { token: a.token });
  assert.equal(pend.status, 403);
  assert.equal(pend.body.error.code, 'order_notify_disabled');
});

test('پرچم روشن: سفارشِ دستیِ کارمند (از API و از lib) هیچ اعلانی نمی‌سازد', async () => {
  const a = await readyShop('کارمند');
  await setFlag(a.shopId, true);
  await h.put('/api/admin/pump/live-config', { scope: a.shopId, key: 'shop.orders', value: true }, { token: admin });
  const viaApi = await h.post('/api/shop/orders', sample(), { token: a.token, headers: { 'Idempotency-Key': freshKey() } });
  assert.equal(viaApi.status, 201, JSON.stringify(viaApi.body));
  await orders.create(a.shopId, { key: freshKey(), body: sample(), actor: STAFF(a) });
  assert.equal((await rowsOf(a.shopId)).length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۳–۴) NOTIF-01 / NOTIF-07 / NOTIF-09
   ══════════════════════════════════════════════════════════════════ */

test('NOTIF-01: سفارشِ مشتری با پرچم ⇒ ردیفِ صف با همان سفارش؛ تکرارِ کلید ردیفِ دوم نمی‌سازد', async () => {
  const a = await readyShop('روشن');
  const key = freshKey();
  const out = await customerOrder(a.shopId, key);
  const rows = await rowsOf(a.shopId);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].order_id, out.order.id);
  assert.equal(Number(rows[0].order_no), out.order.orderNo);
  assert.equal(rows[0].status, 'pending');
  assert.equal(rows[0].event, 'order_created');

  const again = await customerOrder(a.shopId, key);
  assert.equal(again.replayed, true);
  assert.equal((await rowsOf(a.shopId)).length, 1, 'تکرارِ همان درخواست اعلانِ دوم نمی‌سازد');

  //  حتی صدا زدنِ مستقیمِ قلاب برای همان سفارش
  const { tx } = require('../src/db');
  const dup = await tx((c) => N.enqueueIn((t, p) => c.query(t, p), { shopId: a.shopId, orderId: out.order.id, orderNo: out.order.orderNo }));
  assert.equal(dup, null);
  assert.equal((await rowsOf(a.shopId)).length, 1);
});

test('NOTIF-07: شکستِ نوشتنِ صف ⇒ سفارش هم ثبت نمی‌شود (یک تراکنش)', async () => {
  const a = await readyShop('اتمی');
  const before = await one('SELECT COUNT(*)::int n FROM online_orders WHERE shop_id=$1', [a.shopId]);
  await assert.rejects(
    customerOrder(a.shopId, freshKey(), { notify: async () => { throw new Error('outbox down'); } }),
    /outbox down/
  );
  const after = await one('SELECT COUNT(*)::int n FROM online_orders WHERE shop_id=$1', [a.shopId]);
  assert.equal(after.n, before.n, 'سفارشی که صفِ اعلانش نوشته نشد، «ثبت‌شده» نیست');
  assert.equal((await rowsOf(a.shopId)).length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) فرستادن
   ══════════════════════════════════════════════════════════════════ */

test('پوش فقط به دستگاه‌های همان دکان، بی نام و تلفنِ مشتری؛ حال = provider_accepted', async () => {
  const a = await readyShop('فرستادن', ['t1', 't2']);
  const b = await readyShop('همسایه', ['tb']);
  const out = await customerOrder(a.shopId);
  sent = [];
  await N.tick(now() + 1000);

  const mine = sentTo(a.shopId);
  assert.deepEqual(mine.map((s) => s.row.token).sort(), [`t1-${a.shopId}`, `t2-${a.shopId}`]);
  assert.equal(sentTo(b.shopId).length, 0, 'دکانِ دیگر خبرِ این سفارش را نمی‌گیرد');
  const m = mine[0].message;
  assert.equal(m.channel, 'orders');
  assert.equal(m.data.type, 'order_notify');
  assert.equal(m.data.orderId, out.order.id);
  assert.equal(m.data.reminder, '0');
  const text = JSON.stringify(m);
  assert.ok(!text.includes('احمد') && !text.includes('0791234567'), 'صفحهٔ قفل جای دادهٔ مشتری نیست');

  const [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'provider_accepted');
  assert.equal(Number(row.devices_sent), 2);
  assert.equal(Number(row.attempts), 1);
  assert.ok(row.provider_accepted_at);
  assert.equal(row.acked_at, null, 'پذیرفتنِ پوش اثباتِ دیده‌شدن نیست');
});

/* ══════════════════════════════════════════════════════════════════
   ۶) NOTIF-11 — خطا ⇒ تلاشِ محدود؛ کانالِ پشتیبان حداکثر یک بار
   ══════════════════════════════════════════════════════════════════ */

test('NOTIF-11: پوشِ خراب ⇒ دقیقاً MAX_ATTEMPTS تلاش با فاصلهٔ فزاینده، سپس failed و «پیامک: تنظیم نشده»', async () => {
  const a = await readyShop('خطا');
  const out = await customerOrder(a.shopId);
  failing = true;
  let t = now() + 1000;
  const gaps = [];
  try {
    for (let i = 0; i < N.MAX_ATTEMPTS + 3; i++) {
      await N.tick(t);
      const [row] = await rowsOf(a.shopId);
      if (row.status !== 'pending') break;
      gaps.push(Number(row.next_attempt_at) - t);
      //  پیش از سررسید چیزی گرفته نمی‌شود
      await N.tick(Number(row.next_attempt_at) - 1);
      assert.equal((await rowsOf(a.shopId))[0].status, 'pending');
      t = Number(row.next_attempt_at);
    }
  } finally { failing = false; }

  const [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'failed');
  assert.equal(Number(row.attempts), N.MAX_ATTEMPTS);
  assert.equal(row.last_result, 'provider_error');
  assert.match(row.last_error, /FCM 503/);
  assert.equal(row.fallback_state, 'not_configured');
  assert.equal(row.fallback_channel, 'sms');
  assert.ok(row.fallback_at);
  assert.equal(gaps.length, N.MAX_ATTEMPTS - 1);
  for (let i = 1; i < gaps.length; i++) assert.equal(gaps[i], gaps[i - 1] * 2, 'فاصلهٔ فزاینده');

  //  بعد از failed دیگر هیچ تلاشی نیست
  sent = [];
  await N.tick(t + 24 * 3600_000);
  assert.equal(sentTo(a.shopId).length, 0);
  //  خطای اعلان سفارش را پس نمی‌گیرد (§۳۰.۱ بندِ ۶)
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [out.order.id])).status, 'new');
});

test('بی دستگاهِ ثبت‌شده ⇒ no_devices، همان سقف؛ کانالِ پشتیبانِ ثبت‌شده فقط یک بار صدا زده می‌شود', async () => {
  const a = await shopOwner('بی‌دستگاه');
  await setFlag(a.shopId, true);
  await customerOrder(a.shopId);
  const calls = [];
  N.setFallback({ channel: 'sms', send: async (x) => { calls.push(x); return { ok: true }; } });
  try {
    let t = now() + 1000;
    for (let i = 0; i < N.MAX_ATTEMPTS + 3; i++) {
      await N.tick(t);
      t += 24 * 3600_000;
    }
  } finally { N.setFallback(null); }
  const [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'failed');
  assert.equal(row.last_result, 'no_devices');
  assert.equal(row.fallback_state, 'provider_accepted');
  //  ردیف‌های بازماندهٔ آزمون‌های قبلی هم در همین تیک‌ها جلو می‌روند؛ فقط مالِ همین اعلان
  const mine = calls.filter((c) => c.notifId === row.id);
  assert.equal(mine.length, 1, 'پیامکِ تکراری نه');
  assert.equal(mine[0].reason, 'failed');
  assert.equal(mine[0].orderNo, Number(row.order_no));
});

/* ══════════════════════════════════════════════════════════════════
   ۷) یادآوری، unacked، رسید
   ══════════════════════════════════════════════════════════════════ */

test('کسی باز نکرد ⇒ MAX_REMINDERS یادآوری، سپس unacked؛ رسیدِ دیرِ «باز شد» هنوز می‌بندد', async () => {
  const a = await readyShop('بی‌پاسخ');
  await customerOrder(a.shopId);
  sent = [];
  let t = now() + 1000;
  await N.tick(t);
  for (let i = 0; i < N.MAX_REMINDERS + 3; i++) {
    t += N.ACK_WINDOW_MS;
    await N.tick(t);
  }
  const mine = sentTo(a.shopId);
  assert.equal(mine.length, 1 + N.MAX_REMINDERS, 'یک پوش + یادآوری‌های محدود');
  assert.deepEqual(mine.map((s) => s.message.data.reminder), ['0', ...Array(N.MAX_REMINDERS).fill('1')]);
  let [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'unacked');
  assert.equal(Number(row.reminders), N.MAX_REMINDERS);
  assert.equal(row.fallback_state, 'not_configured');

  const late = await h.post(`/api/shop/order-notify/${row.id}/ack`, { kind: 'opened' }, { token: a.token });
  assert.equal(late.status, 200, JSON.stringify(late.body));
  assert.equal(late.body.notification.status, 'acked');
  assert.equal(late.body.notification.orderPath, `/api/shop/orders/${row.order_id}`);
});

test('NOTIF-09: «رسید» حال را عوض نمی‌کند، «باز شد» می‌بندد، تکرار اثرِ دوم ندارد، دکانِ دیگر ۴۰۴', async () => {
  const a = await readyShop('رسید');
  const b = await readyShop('غریبه');
  await customerOrder(a.shopId);
  let t = now() + 1000;
  await N.tick(t);
  const [row] = await rowsOf(a.shopId);

  const pend = await h.get('/api/shop/order-notify/pending', { token: a.token });
  assert.equal(pend.status, 200);
  assert.deepEqual(pend.body.notifications.map((x) => x.id), [row.id]);
  assert.ok(!JSON.stringify(pend.body).includes('احمد'));

  const recv = await h.post(`/api/shop/order-notify/${row.id}/ack`, { kind: 'received' }, { token: a.token });
  assert.equal(recv.status, 200);
  assert.equal(recv.body.notification.status, 'provider_accepted');
  assert.ok(recv.body.notification.firstReceivedAt);

  const bad = await h.post(`/api/shop/order-notify/${row.id}/ack`, { kind: 'seen' }, { token: a.token });
  assert.equal(bad.status, 400);
  const other = await h.post(`/api/shop/order-notify/${row.id}/ack`, { kind: 'opened' }, { token: b.token });
  assert.equal(other.status, 404, 'اعلانِ دکانِ دیگر دیده نمی‌شود');

  const open1 = await h.post(`/api/shop/order-notify/${row.id}/ack`, { kind: 'opened' }, { token: a.token });
  assert.equal(open1.body.changed, true);
  assert.equal(open1.body.notification.status, 'acked');
  assert.equal(open1.body.notification.ackSource, 'device');
  const open2 = await h.post(`/api/shop/order-notify/${row.id}/ack`, { kind: 'opened' }, { token: a.token });
  assert.equal(open2.body.changed, false);
  assert.equal(open2.body.notification.ackedAt, open1.body.notification.ackedAt);
  const receipts = await many('SELECT kind FROM order_notify_receipts WHERE notif_id=$1 ORDER BY kind', [row.id]);
  assert.deepEqual(receipts.map((r) => r.kind), ['opened', 'received']);

  sent = [];
  await N.tick(t + 10 * N.ACK_WINDOW_MS);
  assert.equal(sentTo(a.shopId).length, 0, 'بعد از «باز شد» نه یادآوری، نه پیامک');
  assert.equal((await h.get('/api/shop/order-notify/pending', { token: a.token })).body.notifications.length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۸–۹) بازیابی، رسیدگیِ سفارش، قطعِ فوری
   ══════════════════════════════════════════════════════════════════ */

test('NOTIF-08: کارگری که وسطِ کار مرد ⇒ بعد از STALE دوباره گرفته و فرستاده می‌شود، بی سفارشِ تکراری', async () => {
  const a = await readyShop('بازیابی');
  await customerOrder(a.shopId);
  const t = now() + 1000;
  //  «گرفته شد» و سرور مرد — همان چیزی که claim می‌نویسد
  await query(
    `UPDATE order_notify_outbox SET status='sending', locked_at=$2, attempts=attempts+1 WHERE shop_id=$1`,
    [a.shopId, t]
  );
  sent = [];
  await N.tick(t + N.STALE_MS - 1);
  assert.equal(sentTo(a.shopId).length, 0, 'پیش از STALE دست نمی‌خورد');
  await N.tick(t + N.STALE_MS + 1);
  assert.equal(sentTo(a.shopId).length, 1);
  const [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'provider_accepted');
  assert.equal(Number(row.attempts), 2, 'تلاشِ نیمه‌کاره هم شمرده شد');
  const c = await one('SELECT COUNT(*)::int n FROM online_orders WHERE shop_id=$1', [a.shopId]);
  assert.equal(c.n, 1);
});

test('سفارشی که کسی قبول کرد دیگر زنگ نمی‌زند (acked از راهِ وضعیتِ سفارش)', async () => {
  const a = await readyShop('رسیدگی');
  const out = await customerOrder(a.shopId);
  await orders.transition(a.shopId, out.order.id, { to: 'accepted' }, STAFF(a));
  sent = [];
  await N.tick(now() + 1000);
  assert.equal(sentTo(a.shopId).length, 0);
  const [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'acked');
  assert.equal(row.ack_source, 'order_status');
});

test('پرچم پس از ثبت خاموش شد ⇒ cancelled و هیچ پیامی؛ config حالِ دستگاه را راست می‌گوید', async () => {
  const a = await readyShop('قطع');
  const cfg = await h.get('/api/shop/order-notify/config', { token: a.token });
  assert.equal(cfg.body.enabled, true);
  assert.equal(cfg.body.push.thisDeviceRegistered, true);
  assert.equal(cfg.body.push.shopDevices, 1);
  assert.equal(cfg.body.policy.maxAttempts, N.MAX_ATTEMPTS);

  await customerOrder(a.shopId);
  await setFlag(a.shopId, false);
  sent = [];
  await N.tick(now() + 1000);
  assert.equal(sentTo(a.shopId).length, 0);
  const [row] = await rowsOf(a.shopId);
  assert.equal(row.status, 'cancelled');
  assert.equal(row.last_result, 'flag_off');
});
