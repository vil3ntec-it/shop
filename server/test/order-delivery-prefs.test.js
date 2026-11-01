'use strict';
/**
 * دستورِ تحویل — CHK-14 («با اسکناسِ ۱۰۰۰ پرداخت می‌کنم») و CHK-24 (ویرایش تا پیش از ارسال) · مهاجرتِ ۱۴۶.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `shop.orders.delivery_prefs` خاموش ⇒ همان سفارشِ امروز: اسکناس خوانده نمی‌شود،
 *      ویرایش ۴۰۳ (کارمند و مشتری)، `/config` خاموش.
 *   ۲) اسکناس: فقط ارسال + نقد؛ کمتر از جمع نه؛ پول خرد کمتر از یک اسکناسِ ۱۰۰۰؛ افغانیِ کامل.
 *   ۳) کارتِ پیک مبلغِ اسکناس و پول خرد را از مبلغِ مورد انتظارِ همان مأموریت، زنده، می‌بیند.
 *   ۴) ویرایش: فقط پیش از حرکتِ بسته؛ همان مقدار ⇒ بی رویداد؛ نسخهٔ کهنه ⇒ ۴۰۹؛ دو ویرایشِ
 *      هم‌زمان با یک نسخه ⇒ یکی؛ هر تغییر یک رویدادِ `delivery_edit` با پیش و پس.
 *   ۵) مشتری با توکنِ پیگیری همان را برای سفارشِ ویترینِ خودش؛ سفارشِ دیگر «پیدا نشد».
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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

async function shopOwner(name, { prefs }) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  if (prefs) await setFlag(o.shopId, 'shop.orders.delivery_prefs', true);
  return o;
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

/** ۱۲٬۰۰۰ + ارسالِ ۴۰۰ = ۱۲٬۴۰۰ افغانی */
const TOTAL = 1_240_000;
const order = (over = {}) => ({
  source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
  customer: { name: 'احمد', phone: '0791234567' },
  address: { landmark: 'نزدیکِ مسجدِ جامع', text: 'کارتهٔ چهار', lat: 34.5, lng: 69.1 },
  delivery: { callBeforeArrival: true },
  lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
  deliveryFeeMinor: 40_000,
  ...over,
});
const create = (s, body) => h.post('/api/shop/orders', body, { token: s.token, headers: { 'Idempotency-Key': `o-${uniq()}-key` } });
async function newOrder(s, body) {
  const r = await create(s, body);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}
async function moveOrder(s, id, ...tos) {
  for (const to of tos) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}
const patch = (s, id, body) => h.api('POST', `/api/shop/orders/${id}/delivery`, { token: s.token, body });
const editEvents = async (id) => many(
  `SELECT detail FROM online_order_events WHERE order_id=$1 AND kind='delivery_edit' ORDER BY seq`, [id]);
const tenderOf = (o) => o.delivery.cashTenderMinor ?? null;

/* ══════════════════════════════════════════════════════════════════
   ۱) ⛔ پرچمِ خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: اسکناس خوانده نمی‌شود، ویرایش ۴۰۳، /config خاموش', async () => {
  const s = await shopOwner('دستورِ خاموش', { prefs: false });
  assert.equal((await h.get('/api/shop/orders/config', { token: s.token })).body.deliveryPrefs, false);
  //  حتی مبلغِ نادرست هم سنجیده نمی‌شود — کلید دور ریخته می‌شود
  const o = await newOrder(s, order({ delivery: { callBeforeArrival: true, cashTenderMinor: 5 } }));
  assert.equal(tenderOf(o), null);
  assert.equal(o.delivery.callBeforeArrival, true);
  const r = await patch(s, o.id, { delivery: { leaveWith: 'دربان' } });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'delivery_prefs_disabled');
  assert.equal((await editEvents(o.id)).length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) CHK-14 — اسکناس
   ══════════════════════════════════════════════════════════════════ */

test('CHK-14: «با اسکناسِ …» فقط ارسال و نقد؛ نه کمتر از جمع؛ پول خرد کمتر از یک اسکناسِ ۱۰۰۰؛ افغانیِ کامل', async () => {
  const s = await shopOwner('اسکناس', { prefs: true });
  assert.equal((await h.get('/api/shop/orders/config', { token: s.token })).body.deliveryPrefs, true);
  const ok = await newOrder(s, order({ delivery: { callBeforeArrival: true, cashTenderMinor: 1_300_000 } }));
  assert.equal(ok.totalMinor, TOTAL);
  assert.equal(tenderOf(ok), 1_300_000);
  //  مبلغِ دقیق (بی پول خرد) هم پذیرفته است
  assert.equal(tenderOf(await newOrder(s, order({ delivery: { cashTenderMinor: TOTAL } }))), TOTAL);
  //  بی کلید ⇒ همان سفارشِ امروز
  assert.equal(tenderOf(await newOrder(s, order())), null);

  const n0 = (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [s.shopId])).n;
  const bad = [
    [{ delivery: { cashTenderMinor: 1_200_000 } }, 'cash_tender_short'],
    [{ delivery: { cashTenderMinor: TOTAL + 100_000 } }, 'cash_tender_excess'],
    [{ delivery: { cashTenderMinor: 1_300_050 } }, 'bad_cash_tender'],
    [{ delivery: { cashTenderMinor: '1300000' } }, 'bad_cash_tender'],
    [{ delivery: { cashTenderMinor: -100 } }, 'bad_cash_tender'],
    [{ fulfillment: 'pickup', deliveryFeeMinor: 0, delivery: { cashTenderMinor: 1_300_000 } }, 'bad_cash_tender'],
  ];
  for (const [over, code] of bad) {
    const r = await create(s, order(over));
    assert.equal(r.status, 400, JSON.stringify(over));
    assert.equal(r.body.error.code, code, JSON.stringify(r.body));
  }
  //  درست زیرِ مرز: پول خردِ ۹۹۹ افغانی پذیرفته است
  assert.equal(tenderOf(await newOrder(s, order({ delivery: { cashTenderMinor: TOTAL + 99_900 } }))), TOTAL + 99_900);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [s.shopId])).n, n0 + 1);
});

test('CHK-14: کارتِ پیک مبلغِ اسکناس و پول خرد را از مبلغِ مورد انتظارِ همان مأموریت، زنده، می‌بیند', async () => {
  const s = await shopOwner('پیک و اسکناس', { prefs: true });
  await setFlag(s.shopId, 'shop.delivery', true);
  const m = await member(s.shopId, 'staff', 'پیکِ اسکناس');
  const c = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, cashCapMinor: 5_000_000 }, { token: s.token });
  assert.equal(c.status, 201, JSON.stringify(c.body));
  const o = await newOrder(s, order({ delivery: { cashTenderMinor: 1_300_000 } }));
  await moveOrder(s, o.id, 'accepted', 'picking');
  const asg = await h.post(`/api/shop/delivery/orders/${o.id}/assign`, { mode: 'shop_courier', courierId: c.body.courier.id },
    { token: s.token, headers: { 'Idempotency-Key': `a-${uniq()}-key` } });
  assert.equal(asg.status, 201, JSON.stringify(asg.body));
  const card = async () => {
    const ms = await h.get('/api/courier/missions', { token: m.accessToken });
    assert.equal(ms.status, 200, JSON.stringify(ms.body));
    return ms.body.items.find((x) => x.orderId === o.id);
  };
  assert.deepEqual([(await card()).delivery.cashTenderMinor, (await card()).delivery.changeMinor], [1_300_000, 60_000]);
  //  دکان پیش از حرکت مبلغ را عوض کرد ⇒ کارتِ پیک همان دم
  const e = await patch(s, o.id, { delivery: { cashTenderMinor: 1_250_000 } });
  assert.equal(e.status, 200, JSON.stringify(e.body));
  assert.deepEqual([(await card()).delivery.cashTenderMinor, (await card()).delivery.changeMinor], [1_250_000, 10_000]);
  //  بی اسکناس ⇒ هیچ
  assert.equal((await patch(s, o.id, { delivery: { cashTenderMinor: null } })).status, 200);
  assert.deepEqual([(await card()).delivery.cashTenderMinor, (await card()).delivery.changeMinor], [null, null]);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) CHK-24 — ویرایش تا پیش از ارسال
   ══════════════════════════════════════════════════════════════════ */

test('CHK-24: ویرایش پیش از حرکت با رویدادِ پیش/پس؛ همان مقدار بی رویداد؛ نسخهٔ کهنه ۴۰۹؛ پس از ارسال قفل', async () => {
  const s = await shopOwner('ویرایشِ دستور', { prefs: true });
  const o = await newOrder(s, order({ delivery: { callBeforeArrival: true, cashTenderMinor: 1_300_000 } }));
  await moveOrder(s, o.id, 'accepted');
  const v0 = (await h.get(`/api/shop/orders/${o.id}`, { token: s.token })).body.order.version;

  const a = await patch(s, o.id, {
    expectedVersion: v0, delivery: { leaveWith: 'دربان', callBeforeArrival: false }, address: { landmark: 'دروازهٔ سبز' },
  });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.unchanged, false);
  assert.equal(a.body.order.version, v0 + 1);
  assert.equal(a.body.order.delivery.leaveWith, 'دربان');
  assert.ok(!('callBeforeArrival' in a.body.order.delivery));
  assert.equal(a.body.order.delivery.cashTenderMinor, 1_300_000, 'کلیدِ نیامده دست نمی‌خورد');
  assert.equal(a.body.order.address.landmark, 'دروازهٔ سبز');
  assert.equal(a.body.order.address.text, 'کارتهٔ چهار', 'نشانی و پین دست نمی‌خورند');
  assert.equal(a.body.order.address.lat, 34.5);
  const ev = a.body.order.events.find((x) => x.kind === 'delivery_edit');
  assert.ok(ev, JSON.stringify(a.body.order.events));
  assert.equal(ev.actorType, 'user');
  assert.deepEqual(ev.detail.changed.sort(), ['callBeforeArrival', 'landmark', 'leaveWith']);
  assert.equal(ev.detail.before.landmark, 'نزدیکِ مسجدِ جامع');
  assert.equal(ev.detail.after.landmark, 'دروازهٔ سبز');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='order.delivery_edit' AND target_id=$1`, [o.id])).n, 1);

  //  همان مقدار دوباره ⇒ بی رویداد و بی نسخهٔ تازه
  const same = await patch(s, o.id, { delivery: { leaveWith: 'دربان' } });
  assert.equal(same.status, 200);
  assert.equal(same.body.unchanged, true);
  assert.equal(same.body.order.version, v0 + 1);
  //  نسخهٔ کهنه ⇒ ۴۰۹ با نسخهٔ امروز؛ هیچ تغییری
  const stale = await patch(s, o.id, { expectedVersion: v0, delivery: { leaveWith: 'همسایه' } });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'version_conflict');
  assert.equal(stale.body.error.version, v0 + 1);
  //  ورودیِ بد و اسکناسِ نادرست ⇒ ۴۰۰ و هیچ تغییری
  for (const [b, code] of [
    [{}, 'bad_delivery_edit'],
    [{ delivery: { callBeforeArrival: 'yes' } }, 'bad_delivery_edit'],
    [{ delivery: { cashTenderMinor: 1_000_000 } }, 'cash_tender_short'],
    [{ delivery: { cashTenderMinor: 2_000_000 } }, 'cash_tender_excess'],
    [{ expectedVersion: 'x', delivery: { leaveWith: 'x' } }, 'bad_delivery_edit'],
  ]) {
    const r = await patch(s, o.id, b);
    assert.equal(r.status, 400, JSON.stringify(b));
    assert.equal(r.body.error.code, code, JSON.stringify(r.body));
  }
  assert.equal((await editEvents(o.id)).length, 1);

  //  دو ویرایشِ هم‌زمان با یک نسخه ⇒ فقط یکی (قفلِ ردیف)
  const v1 = v0 + 1;
  const both = await Promise.all([
    patch(s, o.id, { expectedVersion: v1, delivery: { leaveWith: 'خواهرم' } }),
    patch(s, o.id, { expectedVersion: v1, delivery: { leaveWith: 'برادرم' } }),
  ]);
  assert.deepEqual(both.map((r) => r.status).sort(), [200, 409], JSON.stringify(both.map((r) => r.body)));
  assert.equal((await editEvents(o.id)).length, 2);

  //  پس از ارسال ⇒ قفل
  await moveOrder(s, o.id, 'picking', 'packed');
  assert.equal((await patch(s, o.id, { delivery: { leaveWith: 'پیش از ارسال' } })).status, 200);
  await moveOrder(s, o.id, 'out_for_delivery');
  const late = await patch(s, o.id, { delivery: { leaveWith: 'دیر' } });
  assert.equal(late.status, 409, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'delivery_locked');
  assert.equal(late.body.error.status, 'out_for_delivery');
  //  دریافت از دکان دستورِ تحویل ندارد
  const p = await newOrder(s, order({ fulfillment: 'pickup', deliveryFeeMinor: 0, delivery: {} }));
  assert.equal((await patch(s, p.id, { delivery: { leaveWith: 'x' } })).body.error.code, 'not_delivery');
  //  عضوِ بی دسترسیِ «سفارش‌ها» (پیک) نه
  const viewer = await member(s.shopId, 'courier', 'پیکِ بی دسترسی');
  const nope = await h.api('POST', `/api/shop/orders/${o.id}/delivery`, { token: viewer.accessToken, body: { delivery: { leaveWith: 'x' } } });
  assert.equal(nope.status, 403, JSON.stringify(nope.body));
});

/* ══════════════════════════════════════════════════════════════════
   ۴) مشتری با توکنِ پیگیری
   ══════════════════════════════════════════════════════════════════ */

test('CHK-24 مشتری: ویرایشِ سفارشِ ویترینِ خودش با توکن؛ توکنِ نادرست یا سفارشِ کارمند «پیدا نشد»؛ پرچمِ خاموش ۴۰۳', async () => {
  const s = await shopOwner('ویترینِ دستور', { prefs: true });
  await setFlag(s.shopId, 'shop.storefront', true);
  const slug = `d-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', deliveryFeeMinor: 5000, minOrderMinor: 0 },
    { token: s.token })).status, 200);
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-prefs' },
    body: { device_id: 'dev-prefs', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120 } }] },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  const phone = '0791112233';
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const ticket = (await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode })).body.ticket;
  //  ۲ کیلو = ۲۴۰ + ارسالِ ۵۰ = ۲۹۰ افغانی؛ مشتری با ۵۰۰ می‌پردازد
  const ck = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone },
    address: { text: 'کارته‌سه', landmark: 'پشتِ مسجد', lat: 34.53, lng: 69.17 },
    delivery: { cashTenderMinor: 50_000 },
    lines: [{ productId: 'rice', qtyMilli: 2000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const token = ck.body.order.token;
  const id = token.split('.')[0];
  const staffView = async () => (await h.get(`/api/shop/orders/${id}`, { token: s.token })).body.order;
  assert.equal((await staffView()).delivery.cashTenderMinor, 50_000, 'ویترین هم همان قاعده');

  const url = (t) => `/api/store/${slug}/orders/${t}/delivery`;
  const r = await h.api('POST', url(token), { body: { delivery: { callBeforeArrival: true, leaveWith: 'دربان' } } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.order.token, token);
  assert.ok(!JSON.stringify(r.body).includes('0791112233'), 'نمای مشتری همان پیگیری است');
  const after = await staffView();
  assert.deepEqual([after.delivery.callBeforeArrival, after.delivery.leaveWith], [true, 'دربان']);
  assert.equal(after.events.find((x) => x.kind === 'delivery_edit').actorType, 'customer');

  assert.equal((await h.api('POST', url(`${id}.${'A'.repeat(32)}`), { body: { delivery: { leaveWith: 'x' } } })).status, 404);
  //  سفارشِ کارمندِ همین دکان با توکنِ امضاشدهٔ درست هم «پیدا نشد»
  const staffOrder = await newOrder(s, order());
  const staffToken = require('../src/lib/storefront').tokenOf(s.shopId, staffOrder.id);
  assert.equal((await h.api('POST', url(staffToken), { body: { delivery: { leaveWith: 'x' } } })).status, 404);

  await setFlag(s.shopId, 'shop.orders.delivery_prefs', false);
  const off = await h.api('POST', url(token), { body: { delivery: { leaveWith: 'همسایه' } } });
  assert.equal(off.status, 403, JSON.stringify(off.body));
  assert.equal(off.body.error.code, 'delivery_prefs_disabled');
});
