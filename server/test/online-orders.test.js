'use strict';
/**
 * سفارشِ آنلاین روی سرور — پایهٔ فاز ۴ (بخشِ ۳.۹ بندِ ۲، ORD/CHK).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ **پیش‌فرض خاموش.** بی پرچمِ `shop.orders` در «تنظیماتِ زنده»
 *      هیچ دری باز نیست و هیچ ردیفی ساخته نمی‌شود (۳.۱۷).
 *   ۲) ⛔ کلیدِ یکتایی (CHK-19): تکرارِ همان درخواست — حتی هم‌زمان — هرگز
 *      سفارشِ دوم نمی‌سازد؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹.
 *   ۳) ⛔ وضعیت فقط از راهِ گذارِ مجاز عوض می‌شود؛ هر گذار رویداد دارد
 *      (زمان، کاربر، دلیل) و سابقهٔ عملیات هم می‌گیرد (ORD-16).
 *   ۴) مبلغ را سرور می‌سازد، نه کلاینت (قاعدهٔ گردکردنِ ۳.۴).
 *   ۵) دکانِ دیگر هیچ سفارشی از این دکان نمی‌بیند و دست نمی‌زند.
 *   ۶) خواندن هرگز گروگان نیست: اشتراکِ تمام‌شده فقط نوشتن را می‌بندد.
 *   ۷) برگه‌ها، شمارش‌ها و صفحه‌بندیِ بخشِ «سفارش‌ها» (ORD-01/11/12).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;
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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
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

/** همان راهی که مدیر می‌رود: «تنظیماتِ زنده»، scope = شناسهٔ دکان. */
async function setFlag(scope, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: 'shop.orders', value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let keySeq = 0;
const freshKey = () => `test-order-key-${++keySeq}-${Date.now()}`;

function sample(over = {}) {
  return {
    source: 'phone',
    fulfillment: 'pickup',
    paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    lines: [
      { productId: 'p1', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 3333, note: 'دانه‌بلند' },
      { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 },
    ],
    ...over,
  };
}

const create = (token, body, key = freshKey()) =>
  h.post('/api/shop/orders', body, { token, headers: key ? { 'Idempotency-Key': key } : {} });
const move = (token, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token });

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچم: بخشِ «سفارش‌ها» خاموش است و هیچ ردیفی ساخته نمی‌شود', async () => {
  const a = await shopOwner('خاموش');
  const cfg = await h.get('/api/shop/orders/config', { token: a.token });
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  assert.equal(cfg.body.flag, false);

  for (const r of [
    await h.get('/api/shop/orders', { token: a.token }),
    await h.get('/api/shop/orders/summary', { token: a.token }),
    await create(a.token, sample()),
  ]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'orders_disabled');
  }
  const n = await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [a.shopId]);
  assert.equal(n.n, 0);
});

test('پرچم: `all` همه را روشن می‌کند، `false` ِ خودِ دکان بر آن جلو می‌افتد', async () => {
  const a = await shopOwner('همگانی');
  await setFlag('all', true);
  try {
    assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.enabled, true);
    await setFlag(a.shopId, false);
    assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.enabled, false);
    //  مقدارِ خودِ دکان جلو می‌افتد، و فقط `true` روشن است (رشتهٔ "true" نه)
    await setFlag(a.shopId, 'true');
    assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.flag, false);
    await setFlag(a.shopId, true);
    assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.flag, true);
    await setFlag(a.shopId, false);
  } finally {
    await h.del('/api/admin/pump/live-config', { token: admin, body: { scope: 'all', key: 'shop.orders' } });
  }
  assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.flag, false);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) ساخت و کلیدِ یکتایی
   ══════════════════════════════════════════════════════════════════ */

test('ساخت: مبلغ را سرور می‌سازد، شماره از ۱، رویدادِ «ثبت» و سابقهٔ عملیات', async () => {
  const a = await shopOwner('سازنده');
  await setFlag(a.shopId, true);

  //  مبلغی که کلاینت فرستاده نادیده است
  const r = await create(a.token, sample({ totalMinor: 1, test: true, note: 'سفارشِ آزمایشی' }));
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const o = r.body.order;
  assert.match(o.id, /^[0-9A-HJKMNP-TV-Z]{26}$/, 'شناسه ULID است');
  assert.equal(o.orderNo, 1);
  assert.equal(o.status, 'new');
  assert.equal(o.tab, 'new');
  assert.equal(o.isTest, true);
  assert.equal(o.source, 'phone');
  assert.equal(o.paymentStatus, 'unpaid');
  assert.equal(o.codStatus, 'to_collect');
  assert.equal(o.itemsCount, 2);
  //  ۱.۵ × ۳۳.۳۳ = ۴۹.۹۹۵ ⇒ ۵۰ افغانی؛ ۲ × ۱۲.۵۰ = ۲۵
  assert.deepEqual(o.lines.map(l => l.lineTotalMinor), [5000, 2500]);
  assert.equal(o.subtotalMinor, 7500);
  assert.equal(o.totalMinor, 7500);
  assert.equal(o.lines[0].note, 'دانه‌بلند');
  assert.equal(o.customer.phone, '+93791234567', 'صاحب شماره را کامل می‌بیند');
  assert.equal(o.events.length, 1);
  assert.equal(o.events[0].kind, 'created');
  assert.equal(o.events[0].toStatus, 'new');
  assert.equal(o.events[0].actorId, a.user.id);

  const second = await create(a.token, sample());
  assert.equal(second.body.order.orderNo, 2, 'شمارهٔ هر دکان پشتِ هم');

  const aud = await many(`SELECT action, target_id FROM audit_logs WHERE shop_id=$1 AND action='order.create'`, [a.shopId]);
  assert.equal(aud.length, 2);
  assert.ok(aud.some(x => x.target_id === o.id));
});

test('CHK-19: بی کلید رد؛ همان کلید ⇒ همان سفارش؛ بدنهٔ دیگر ⇒ ۴۰۹؛ هم‌زمان ⇒ یکی', async () => {
  const a = await shopOwner('یکتا');
  await setFlag(a.shopId, true);

  const none = await create(a.token, sample(), null);
  assert.equal(none.status, 400);
  assert.equal(none.body.error.code, 'idempotency_key_required');

  const key = freshKey();
  const first = await create(a.token, sample(), key);
  assert.equal(first.status, 201);
  const again = await create(a.token, sample(), key);
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.order.id, first.body.order.id);

  //  کلید در بدنه هم پذیرفته است (کلاینتی که سرآیند نمی‌فرستد)
  const inBody = await create(a.token, { ...sample(), clientKey: key }, null);
  assert.equal(inBody.status, 200);
  assert.equal(inBody.body.order.id, first.body.order.id);

  const other = await create(a.token, sample({ note: 'چیزِ دیگر' }), key);
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');

  const burstKey = freshKey();
  const burst = await Promise.all(Array.from({ length: 6 }, () => create(a.token, sample(), burstKey)));
  assert.equal(burst.filter(x => x.status === 201).length, 1, JSON.stringify(burst.map(x => x.status)));
  assert.ok(burst.every(x => x.status === 201 || x.status === 200));
  assert.equal(new Set(burst.map(x => x.body.order.id)).size, 1);

  const n = await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [a.shopId]);
  assert.equal(n.n, 2);

  //  همان کلید در دکانِ دیگر سفارشِ خودِ آن دکان است
  const b = await shopOwner('یکتا-۲');
  await setFlag(b.shopId, true);
  const cross = await create(b.token, sample(), key);
  assert.equal(cross.status, 201);
  assert.notEqual(cross.body.order.id, first.body.order.id);
});

test('ورودیِ بد: بی ردیف، تعداد/قیمتِ نادرست، ارسالی بی نشانی، ویترین از درِ کارمند', async () => {
  const a = await shopOwner('ورودی');
  await setFlag(a.shopId, true);
  const bad = [
    sample({ lines: [] }),
    sample({ lines: [{ name: 'x', qtyMilli: 0, unitPriceMinor: 100 }] }),
    sample({ lines: [{ name: 'x', qtyMilli: 1000, unitPriceMinor: 12.5 }] }),
    sample({ lines: [{ name: 'x', qtyMilli: 1000, unitPriceMinor: -1 }] }),
    sample({ lines: [{ name: '', qtyMilli: 1000, unitPriceMinor: 100 }] }),
    sample({ lines: Array.from({ length: 201 }, () => ({ name: 'x', qtyMilli: 1000, unitPriceMinor: 100 })) }),
    sample({ fulfillment: 'delivery' }),
    sample({ source: 'storefront' }),
    sample({ paymentMethod: 'online' }),
    sample({ customer: { name: '' } }),
    sample({ discountMinor: 999999 }),
    sample({ scheduledFor: Date.now() - DAY }),
  ];
  for (const body of bad) {
    const r = await create(a.token, body);
    assert.equal(r.status, 400, `${JSON.stringify(body).slice(0, 120)} ⇒ ${JSON.stringify(r.body)}`);
  }
  const n = await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [a.shopId]);
  assert.equal(n.n, 0);

  //  ارسالی با پین یا نشانهٔ متنی پذیرفته است؛ هزینهٔ ارسال و تخفیف در جمع
  const ok = await create(a.token, sample({
    fulfillment: 'delivery',
    address: { lat: 34.5281, lng: 69.1723, landmark: 'کنارِ مسجدِ جامع', text: 'کارتهٔ سه' },
    delivery: { callBeforeArrival: true, leaveWith: 'دربان' },
    deliveryFeeMinor: 5000, discountMinor: 500,
  }));
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.order.totalMinor, 7500 - 500 + 5000);
  assert.equal(ok.body.order.address.landmark, 'کنارِ مسجدِ جامع');
  assert.equal(ok.body.order.delivery.callBeforeArrival, true);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) ماشینِ وضعیت از راهِ API
   ══════════════════════════════════════════════════════════════════ */

test('مسیرِ کاملِ حضوری؛ هر گذار رویداد و نسخهٔ تازه دارد', async () => {
  const a = await shopOwner('حضوری');
  await setFlag(a.shopId, true);
  const o = (await create(a.token, sample())).body.order;

  const acc = await move(a.token, o.id, { to: 'accepted', from: 'new', readyInMinutes: 20 });
  assert.equal(acc.status, 200, JSON.stringify(acc.body));
  assert.equal(acc.body.order.status, 'accepted');
  assert.equal(acc.body.order.tab, 'preparing');
  assert.ok(Math.abs(acc.body.order.readyAt - (Date.now() + 20 * 60e3)) < 60e3, 'زمانِ آماده (ORD-07)');

  for (const to of ['picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const r = await move(a.token, o.id, { to });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.order.status, to);
  }
  const done = (await h.get(`/api/shop/orders/${o.id}`, { token: a.token })).body.order;
  assert.equal(done.tab, 'done');
  assert.equal(done.version, 6);
  assert.deepEqual(done.events.map(e => e.toStatus), ['new', 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']);
  assert.deepEqual(done.events.map(e => e.seq), [1, 2, 3, 4, 5, 6]);
  assert.ok(done.events.every(e => e.actorId === a.user.id && e.at > 0));
  assert.deepEqual(done.next, ['return_requested']);

  const aud = await many(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='order.transition' ORDER BY created_at`, [a.shopId]);
  assert.equal(aud.length, 5);
  assert.equal(aud[0].detail.to, 'accepted');
});

test('گذارِ نامجاز، وضعیتِ عوض‌شده، تکرارِ همان گذار، بی دلیل', async () => {
  const a = await shopOwner('نامجاز');
  await setFlag(a.shopId, true);
  const o = (await create(a.token, sample({ fulfillment: 'delivery', address: { landmark: 'سرِ کوچه' } }))).body.order;

  const jump = await move(a.token, o.id, { to: 'delivered' });
  assert.equal(jump.status, 409);
  assert.equal(jump.body.error.code, 'invalid_transition');

  const noReason = await move(a.token, o.id, { to: 'rejected' });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');

  const longReason = await move(a.token, o.id, { to: 'rejected', reason: 'x'.repeat(500) });
  assert.equal(longReason.status, 400);
  assert.equal(longReason.body.error.code, 'bad_reason');

  const sys = await move(a.token, o.id, { to: 'auto_rejected', reason: 'timeout' });
  assert.equal(sys.status, 403);
  assert.equal(sys.body.error.code, 'system_only');

  assert.equal((await move(a.token, o.id, { to: 'accepted' })).status, 200);
  //  کارمندِ دوم هم‌زمان «رد» می‌زد، ولی سفارش دیگر «تازه» نیست
  const stale = await move(a.token, o.id, { to: 'rejected', from: 'new', reason: 'busy' });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'status_changed');
  assert.equal(stale.body.error.current, 'accepted');

  //  دو بار «قبول» (تکرارِ درخواست پس از قطعِ شبکه) رویدادِ دوم نمی‌سازد
  const twice = await move(a.token, o.id, { to: 'accepted' });
  assert.equal(twice.status, 200);
  assert.equal(twice.body.unchanged, true);

  assert.equal((await move(a.token, o.id, { to: 'picking' })).status, 200);
  assert.equal((await move(a.token, o.id, { to: 'packed' })).status, 200);
  const wrongWay = await move(a.token, o.id, { to: 'ready_for_pickup' });
  assert.equal(wrongWay.status, 409);
  assert.equal(wrongWay.body.error.code, 'fulfillment_mismatch');

  const after = (await h.get(`/api/shop/orders/${o.id}`, { token: a.token })).body.order;
  assert.deepEqual(after.events.map(e => e.toStatus), ['new', 'accepted', 'picking', 'packed']);
  assert.equal(after.status, 'packed');

  const missing = await move(a.token, 'NOPE', { to: 'accepted' });
  assert.equal(missing.status, 404);
});

test('ارسال، ناموفق، تلاشِ دوباره، برگشت به دکان و بازگشت به انبار', async () => {
  const a = await shopOwner('ارسالی');
  await setFlag(a.shopId, true);
  const o = (await create(a.token, sample({ fulfillment: 'delivery', address: { lat: 34.5, lng: 69.2 } }))).body.order;
  const steps = [
    { to: 'accepted' }, { to: 'picking' }, { to: 'packed' }, { to: 'out_for_delivery' },
    { to: 'delivery_failed', reason: 'no_answer' }, { to: 'retry_scheduled' }, { to: 'out_for_delivery' },
    { to: 'delivery_failed', reason: 'wrong_address' }, { to: 'retry_scheduled' },
    { to: 'returned_to_store' }, { to: 'restocked' },
  ];
  for (const s of steps) {
    const r = await move(a.token, o.id, s);
    assert.equal(r.status, 200, `${s.to}: ${JSON.stringify(r.body)}`);
  }
  const last = (await h.get(`/api/shop/orders/${o.id}`, { token: a.token })).body.order;
  assert.equal(last.status, 'restocked');
  assert.deepEqual(last.next, []);
  assert.equal(last.events.find(e => e.toStatus === 'delivery_failed').reason, 'no_answer');
});

test('ردِ خودکار فقط از راهِ سیستم (کتابخانه)، با رویدادِ «سیستم»', async () => {
  const a = await shopOwner('خودکار');
  await setFlag(a.shopId, true);
  const o = (await create(a.token, sample())).body.order;
  const orders = require('../src/lib/orders');
  const out = await orders.transition(a.shopId, o.id, { to: 'auto_rejected', reason: 'timeout' }, { type: 'system' });
  assert.equal(out.order.status, 'auto_rejected');
  const ev = out.order.events.at(-1);
  assert.equal(ev.actorType, 'system');
  assert.equal(ev.reason, 'timeout');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) نقش‌ها و مرزِ دکان‌ها
   ══════════════════════════════════════════════════════════════════ */

test('شاگرد: می‌سازد و جلو می‌برد؛ بازپرداخت نه؛ شماره را پوشیده می‌بیند (ORD-17)', async () => {
  const a = await shopOwner('نقش');
  await setFlag(a.shopId, true);
  const staff = await member(a.shopId, 'staff', 'شاگرد');
  const mgr = await member(a.shopId, 'manager', 'مدیر');

  const made = await create(staff.token, sample());
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = made.body.order;
  assert.equal(o.customer.phone, undefined, 'شاگرد شمارهٔ کامل را نمی‌بیند');
  assert.match(o.customer.phoneMasked, /^\+937•+567$/);
  assert.equal(o.canContact, false);

  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']) {
    assert.equal((await move(staff.token, o.id, { to })).status, 200, to);
  }
  const refund = await move(staff.token, o.id, { to: 'refunded_full' });
  assert.equal(refund.status, 403);
  assert.equal(refund.body.error.code, 'permission_denied');
  assert.equal((await move(mgr.token, o.id, { to: 'refunded_partial' })).status, 200);

  const asMgr = (await h.get(`/api/shop/orders/${o.id}`, { token: mgr.token })).body.order;
  assert.equal(asMgr.customer.phone, '+93791234567');
  //  فهرست هرگز شمارهٔ کامل ندارد
  const list = (await h.get('/api/shop/orders?tab=done', { token: mgr.token })).body;
  assert.ok(list.items.every(x => x.customer.phone === undefined));
});

test('دکانِ دیگر نه می‌بیند نه دست می‌زند', async () => {
  const a = await shopOwner('الف');
  const b = await shopOwner('ب');
  await setFlag(a.shopId, true);
  await setFlag(b.shopId, true);
  const o = (await create(a.token, sample())).body.order;

  assert.equal((await h.get(`/api/shop/orders/${o.id}`, { token: b.token })).status, 404);
  assert.equal((await move(b.token, o.id, { to: 'accepted' })).status, 404);
  const listB = (await h.get('/api/shop/orders?tab=new', { token: b.token })).body;
  assert.equal(listB.items.length, 0);
  assert.equal((await h.get(`/api/shop/orders/${o.id}`, { token: a.token })).body.order.status, 'new');

  const outsider = await h.newUser('بی‌دکان', 'shop');
  assert.equal((await h.get('/api/shop/orders/config', { token: outsider.accessToken })).status, 403);
  assert.equal((await h.get('/api/shop/orders')).status, 401);
});

test('اشتراکِ تمام‌شده: خواندن باز، نوشتن بسته', async () => {
  const a = await shopOwner('تمام‌شده');
  await setFlag(a.shopId, true);
  const o = (await create(a.token, sample())).body.order;
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [a.shopId, Date.now() - 400 * DAY]);

  const cfg = (await h.get('/api/shop/orders/config', { token: a.token })).body;
  assert.equal(cfg.flag, true);
  assert.equal(cfg.entitled, false);
  assert.equal(cfg.enabled, false);

  assert.equal((await h.get('/api/shop/orders?tab=new', { token: a.token })).status, 200);
  assert.equal((await h.get(`/api/shop/orders/${o.id}`, { token: a.token })).status, 200);
  const w = await create(a.token, sample());
  assert.equal(w.status, 403);
  assert.match(w.body.error.code, /^subscription_(required|expired)$/);
  assert.equal((await move(a.token, o.id, { to: 'accepted' })).status, 403);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) مدلِ خواندنیِ بخشِ «سفارش‌ها»
   ══════════════════════════════════════════════════════════════════ */

test('برگه‌ها، شمارش‌ها، کارت و صفحه‌بندی', async () => {
  const a = await shopOwner('برگه');
  await setFlag(a.shopId, true);
  const mk = async (over) => (await create(a.token, sample(over))).body.order;
  const n1 = await mk({});
  const n2 = await mk({ fulfillment: 'delivery', address: { landmark: 'بازار' } });
  const n3 = await mk({});
  const sch = await mk({ scheduledFor: Date.now() + 3 * 3600e3 });
  const prep = await mk({});
  const rdy = await mk({});
  await move(a.token, sch.id, { to: 'accepted' });
  await move(a.token, prep.id, { to: 'accepted' });
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup']) await move(a.token, rdy.id, { to });

  const sum = (await h.get('/api/shop/orders/summary', { token: a.token })).body;
  assert.deepEqual(sum.counts, { new: 3, preparing: 1, ready: 1, on_the_way: 0, scheduled: 1 });
  assert.equal(sum.oldestNewAt, n1.createdAt);
  assert.ok(sum.rev > 0);

  //  صفِ «تازه» از قدیمی به جدید، با صفحه‌بندی
  const p1 = (await h.get('/api/shop/orders?tab=new&limit=2', { token: a.token })).body;
  assert.deepEqual(p1.items.map(x => x.id), [n1.id, n2.id]);
  assert.ok(p1.nextCursor);
  const p2 = (await h.get(`/api/shop/orders?tab=new&limit=2&cursor=${encodeURIComponent(p1.nextCursor)}`, { token: a.token })).body;
  assert.deepEqual(p2.items.map(x => x.id), [n3.id]);
  assert.equal(p2.nextCursor, null);

  //  کارت (ORD-11): ارسال یا حضوری، تعداد اقلام، نقد یا پرداخت‌شده، زمان
  const card = p1.items[1];
  assert.equal(card.fulfillment, 'delivery');
  assert.equal(card.itemsCount, 2);
  assert.equal(card.paymentMethod, 'cod');
  assert.equal(card.paymentStatus, 'unpaid');
  assert.equal(card.totalMinor, 7500);
  assert.ok(card.createdAt > 0);
  assert.deepEqual(card.next, ['accepted', 'rejected', 'cancelled_by_customer']);
  assert.equal(card.lines, undefined, 'کارت سبک است؛ ردیف‌ها در جزئیات');

  const schList = (await h.get('/api/shop/orders?tab=scheduled', { token: a.token })).body;
  assert.deepEqual(schList.items.map(x => x.id), [sch.id]);
  assert.deepEqual((await h.get('/api/shop/orders?tab=preparing', { token: a.token })).body.items.map(x => x.id), [prep.id]);
  assert.deepEqual((await h.get('/api/shop/orders?tab=ready', { token: a.token })).body.items.map(x => x.id), [rdy.id]);

  const badTab = await h.get('/api/shop/orders?tab=nope', { token: a.token });
  assert.equal(badTab.status, 400);
  const badCursor = await h.get('/api/shop/orders?tab=new&cursor=@@@', { token: a.token });
  assert.equal(badCursor.status, 400);
});
