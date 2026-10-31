'use strict';
/**
 * جمع‌آوری و بسته‌بندی (PCK، v3 §۶.۱۷؛ سناریوهای S31 و S37) — پرچمِ خاموشِ `shop.orders.picking`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) PCK-11: پرچم پیش‌فرض خاموش؛ `all` و اولویتِ دکان؛ فقط `true`؛ هر دو `/config`.
 *   ۲) ⛔ خاموش ⇒ ۴۰۳ و هیچ ردیف؛ ویترین همان رفتارِ پیشین.
 *   ۳) PCK-01: برگه به ترتیبِ جای قفسه؛ «قبول‌شده» ⇒ «در حال جمع‌آوری» فقط از درِ سفارش.
 *   ۴) PCK-02: فقط بارکدِ همان کالا؛ اشتباه رد و شمرده؛ کالای بارکددار تأییدِ دستی ندارد.
 *   ۵) PCK-03: «پیدا نکردم» همیشه؛ PCK-14: وظیفهٔ مدیر و «تمام شده» در ویترین تا رسیدگی.
 *   ۶) PCK-04: وزنِ بیش از حد رد؛ در حد ⇒ قیمت با وزنِ واقعی.
 *   ۷) PCK-07: بسته ⇒ کسرِ خودکار در قلم‌ها، جمعِ سفارش و نقدِ موردِ انتظارِ مأموریت — یک تراکنش.
 *   ۸) PCK-09: کیسه و برچسب؛ PCK-13: شاخص‌ها.
 *   ۹) ⛔ رویداد با کلیدِ یکتا (تکرار بی‌اثر، کلیدِ دوباره با بدنهٔ دیگر رد)؛ هم‌زمانی بی شمارشِ دوباره.
 *  ۱۰) ⛔ دکانِ دیگر، نقش و اشتراک.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '30';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const key = (p = 'k') => `${p}-${uniq()}-key`;

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
test.beforeEach(() => resetAll());

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

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function pickingOf(owner) {
  const o = await h.get('/api/shop/orders/config', { token: owner.token });
  assert.equal(o.status, 200, JSON.stringify(o.body));
  const d = await h.get('/api/shop/delivery/config', { token: owner.token });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(typeof o.body.picking, 'boolean', 'orders/config.picking بولی است');
  assert.equal(o.body.picking, d.body.picking, 'دو /config یک جواب دارند');
  return o.body.picking;
}

const push = (token, ops, device = 'dev-pick') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

const OIL = '6260000000024';
const RICE = '6260000000017';

/** دکانِ روشن با سه کالا: روغن (بارکددار، شمارشی)، برنج (بارکددار، وزنی)، نمک (بی‌بارکد). */
async function pickShop(label, { picking = true, delivery = false } = {}) {
  const o = await shopOwner(label);
  await setFlag(o.shopId, 'shop.orders', true);
  if (picking) await setFlag(o.shopId, 'shop.orders.picking', true);
  if (delivery) await setFlag(o.shopId, 'shop.delivery', true);
  const p = await push(o.token, [
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی', barcodes: [OIL] }),
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی', barcode: RICE }),
    op('salt', { name: 'نمک', unit: 'بسته', salePrice: 50, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  return o;
}

const LINES = [
  { productId: 'oil', name: 'روغن', unit: 'بوتل', qtyMilli: 2000, unitPriceMinor: 30000 },
  { productId: 'rice', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 12050 },
  { productId: 'salt', name: 'نمک', unit: 'بسته', qtyMilli: 1000, unitPriceMinor: 5000 },
  { name: 'کیسهٔ اضافه', qtyMilli: 1000, unitPriceMinor: 1000 },
];

async function acceptedOrder(o, { lines = LINES, ...over } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, lines, ...over,
  }, { token: o.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to: 'accepted' }, { token: o.token });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  return r.body.order;
}

const start = (o, id) => h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: o.token });
const send = (o, id, events) => h.post(`/api/shop/orders/${id}/pick/events`, { events }, { token: o.token });
const ev = (lineNo, type, extra = {}) => ({ eventId: key('ev'), lineNo, type, ...extra });
const pack = (o, id, body, k = key('pack')) => h.post(`/api/shop/orders/${id}/pick/pack`, body, { token: o.token, headers: { 'Idempotency-Key': k } });
const lineOf = (sheet, n) => sheet.lines.find((l) => l.lineNo === n);
const pickRows = async (shopId) => (await one(
  `SELECT (SELECT COUNT(*) FROM order_picks WHERE shop_id=$1)::int + (SELECT COUNT(*) FROM order_pick_events WHERE shop_id=$1)::int
        + (SELECT COUNT(*) FROM pick_issues WHERE shop_id=$1)::int + (SELECT COUNT(*) FROM pick_locations WHERE shop_id=$1)::int AS n`,
  [shopId]
)).n;

/* ══════════════════════════════════════════════════════════════════
   ۱) پرچم — PCK-11
   ══════════════════════════════════════════════════════════════════ */

test('PCK-11: پرچمِ جمع‌آوری پیش‌فرض خاموش است؛ all روشن می‌کند و مقدارِ خودِ دکان جلو می‌افتد', async () => {
  const a = await shopOwner('جمع‌آوری-الف');
  const b = await shopOwner('جمع‌آوری-ب');
  assert.equal(await pickingOf(a), false, 'بی پرچم خاموش');

  await setFlag(a.shopId, 'shop.orders.picking', true);
  assert.equal(await pickingOf(a), true, 'پرچمِ خودِ دکان');
  assert.equal(await pickingOf(b), false, 'پرچمِ «الف» دکانِ «ب» را روشن نمی‌کند');

  await setFlag('all', 'shop.orders.picking', true);
  assert.equal(await pickingOf(b), true, 'all روشن ⇒ همه');
  await setFlag(b.shopId, 'shop.orders.picking', false);
  assert.equal(await pickingOf(b), false, 'مقدارِ خودِ دکان بر all جلو می‌افتد');
  await setFlag('all', 'shop.orders.picking', false);
});

test('PCK-11: فقط true روشن است — رشته، عدد و null خاموش‌اند', async () => {
  const a = await shopOwner('جمع‌آوری-نوع');
  for (const v of ['true', 1, 'on', null]) {
    await setFlag(a.shopId, 'shop.orders.picking', v);
    assert.equal(await pickingOf(a), false, `مقدارِ ${JSON.stringify(v)} نباید روشن کند`);
  }
  await setFlag(a.shopId, 'shop.orders.picking', true);
  assert.equal(await pickingOf(a), true);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) خاموش ⇒ هیچ
   ══════════════════════════════════════════════════════════════════ */

test('پرچمِ جمع‌آوری خاموش ⇒ ۴۰۳ picking_disabled روی هر در و هیچ ردیف؛ سفارش همان گذارِ پیشین را دارد', async () => {
  const o = await pickShop('خاموش', { picking: false });
  const order = await acceptedOrder(o);
  for (const [m, url, body] of [
    ['POST', `/api/shop/orders/${order.id}/pick/start`, {}],
    ['POST', `/api/shop/orders/${order.id}/pick/events`, { events: [ev(1, 'not_found')] }],
    ['POST', `/api/shop/orders/${order.id}/pick/pack`, { bags: 1 }],
    ['GET', `/api/shop/orders/${order.id}/pick`, null],
    ['GET', '/api/shop/orders/pick/issues', null],
    ['GET', '/api/shop/orders/pick/metrics', null],
    ['PUT', '/api/shop/orders/pick/locations/oil', { aisle: '1' }],
  ]) {
    const r = await h.api(m, url, { body, token: o.token, headers: { 'Idempotency-Key': key() } });
    assert.equal(r.status, 403, `${m} ${url} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'picking_disabled', `${m} ${url}`);
  }
  assert.equal(await pickRows(o.shopId), 0, 'هیچ ردیفی');
  //  سفارش بی برگه همان گذارِ ساده را دارد
  const t = await h.post(`/api/shop/orders/${order.id}/transition`, { to: 'picking' }, { token: o.token });
  assert.equal(t.status, 200);

  //  بی پرچمِ سفارش ⇒ همان درِ سفارش
  await setFlag(o.shopId, 'shop.orders', false);
  const off = await start(o, order.id);
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'orders_disabled');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) S31 از اول تا بسته — PCK-01، ۰۲، ۰۳، ۰۴، ۰۷، ۰۹، ۱۴
   ══════════════════════════════════════════════════════════════════ */

test('S31: برگه به ترتیبِ قفسه، اسکن، وزن، «پیدا نکردم» و بسته با کسرِ خودکار', async () => {
  const o = await pickShop('S31');
  //  PCK-01: جای قفسه — برنج راهروِ ۱، روغن راهروِ ۲؛ نمک و قلمِ دستی بی‌جا
  for (const [pid, loc] of [['rice', { aisle: '۱', shelf: '3' }], ['oil', { aisle: '2', shelf: '1', level: 'بالا' }]]) {
    const r = await h.put(`/api/shop/orders/pick/locations/${pid}`, loc, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const unknown = await h.put('/api/shop/orders/pick/locations/ghost', { aisle: '1' }, { token: o.token });
  assert.equal(unknown.status, 404, 'کالای ناموجود جا نمی‌گیرد');

  const order = await acceptedOrder(o);
  const none = await h.get(`/api/shop/orders/${order.id}/pick`, { token: o.token });
  assert.equal(none.status, 200);
  assert.equal(none.body.pick, null, 'هنوز شروع نشده ⇒ null');
  const s = await start(o, order.id);
  assert.equal(s.status, 201, JSON.stringify(s.body));
  assert.deepEqual(s.body.pick.lines.map((l) => l.lineNo), [2, 1, 3, 4], 'راهروِ ۱ ⇐ راهروِ ۲ ⇐ بی‌جاها به نام');
  assert.deepEqual(lineOf(s.body.pick, 2).location, { aisle: '1', shelf: '3', level: '' }, 'رقمِ فارسی لاتین شد');
  assert.equal(lineOf(s.body.pick, 2).weighed, true, 'برنج (کیلو) وزنی است');
  assert.equal(lineOf(s.body.pick, 1).weighed, false);
  const detail = await h.get(`/api/shop/orders/${order.id}`, { token: o.token });
  assert.equal(detail.body.order.status, 'picking', 'شروع ⇒ «در حال جمع‌آوری» از درِ سفارش');
  assert.ok(detail.body.order.events.some((e) => e.kind === 'status' && e.toStatus === 'picking'));
  const again = await start(o, order.id);
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true, 'شروعِ دوباره همان برگه');

  //  PCK-02: بارکدِ برنج روی قلمِ روغن ⇒ رد و شمرده؛ تأییدِ دستیِ کالای بارکددار ⇒ رد
  let r = await send(o, order.id, [ev(1, 'scan', { barcode: RICE }), ev(1, 'pick', { qtyMilli: 1000 })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.status, x.code]), [['rejected', 'wrong_item'], ['rejected', 'scan_required']]);
  assert.equal(lineOf(r.body.pick, 1).wrongScans, 1);
  assert.equal(lineOf(r.body.pick, 1).pickedQtyMilli, 0);
  //  دو بوتل با دو اسکن (رقمِ فارسی هم)
  r = await send(o, order.id, [ev(1, 'scan', { barcode: '۶۲۶۰۰۰۰۰۰۰۰۲۴' }), ev(1, 'scan', { barcode: OIL })]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['accepted', 'accepted']);
  assert.equal(lineOf(r.body.pick, 1).status, 'picked');
  assert.equal(lineOf(r.body.pick, 1).pickedQtyMilli, 2000);
  r = await send(o, order.id, [ev(1, 'scan', { barcode: OIL })]);
  assert.equal(r.body.results[0].code, 'line_done', 'قلمِ کامل اسکنِ سوم نمی‌گیرد');

  //  PCK-04: وزن پیش از اسکن ⇒ رد؛ ۱٫۷ کیلو (بیش از ۱۰٪) ⇒ رد با سقف؛ ۱٫۶ ⇒ برداشته با قیمتِ تازه
  r = await send(o, order.id, [ev(2, 'weigh', { qtyMilli: 1600 })]);
  assert.equal(r.body.results[0].code, 'scan_required');
  r = await send(o, order.id, [ev(2, 'scan', { barcode: RICE }), ev(2, 'weigh', { qtyMilli: 1700 }), ev(2, 'weigh', { qtyMilli: 1600 })]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['accepted', 'rejected', 'accepted']);
  assert.equal(r.body.results[1].code, 'over_tolerance');
  assert.equal(r.body.results[1].maxMilli, 1650);
  assert.equal(lineOf(r.body.pick, 2).status, 'picked');
  assert.equal(lineOf(r.body.pick, 2).pickedQtyMilli, 1600);

  //  هنوز قلمِ باز ⇒ بسته نمی‌شود
  const early = await pack(o, order.id, { bags: 1 });
  assert.equal(early.status, 409);
  assert.equal(early.body.error.code, 'lines_pending');
  assert.equal(early.body.error.pending, 2);

  //  PCK-03 + PCK-14: نمک پیدا نشد ⇒ وظیفهٔ مدیر؛ قلمِ دستیِ بی‌کالا با تأییدِ دستی
  r = await send(o, order.id, [ev(3, 'not_found'), ev(4, 'pick', { qtyMilli: 1000 })]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['accepted', 'accepted']);
  assert.equal(lineOf(r.body.pick, 3).status, 'not_found');
  const iss = await h.get('/api/shop/orders/pick/issues', { token: o.token });
  assert.equal(iss.status, 200);
  assert.deepEqual(iss.body.issues.map((x) => [x.productId, x.orderId, x.hits]), [['salt', order.id, 1]]);
  assert.equal(r.body.pick.progress.pending, 0);
  //  پیش‌نمایشِ جمع پیش از بسته
  assert.equal(r.body.pick.totals.subtotalMinor, 60000 + 19300 + 1000);

  //  PCK-07 + PCK-09: بسته با دو کیسه
  assert.equal((await pack(o, order.id, {})).status, 400, 'بی تعدادِ کیسه');
  assert.equal((await pack(o, order.id, { bags: 51 })).status, 400);
  const k = key('pack');
  const p = await pack(o, order.id, { bags: 2 }, k);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.pick.status, 'packed');
  assert.equal(p.body.pick.totals.originalTotalMinor, 60000 + 18100 + 5000 + 1000);
  assert.equal(p.body.pick.totals.totalMinor, 80300);
  assert.equal(p.body.pick.totals.deductedMinor, 3800);
  assert.deepEqual(p.body.pick.labels.map((l) => l.qr), [`VLNB-${order.orderNo}-1-2`, `VLNB-${order.orderNo}-2-2`]);

  const after = (await h.get(`/api/shop/orders/${order.id}`, { token: o.token })).body.order;
  assert.equal(after.status, 'packed');
  assert.equal(after.totalMinor, 80300, 'جمعِ سفارش = آن‌چه برداشته شد');
  assert.equal(after.subtotalMinor, 80300);
  assert.equal(after.itemsCount, 3);
  assert.deepEqual(after.lines.map((l) => [l.lineNo, l.qtyMilli, l.lineTotalMinor]),
    [[1, 2000, 60000], [2, 1600, 19300], [4, 1000, 1000]], 'نمک از قلم‌ها رفت؛ برنج با وزنِ واقعی');
  const note = after.events.find((e) => e.kind === 'note');
  assert.ok(note && /جمع‌آوری/.test(note.note), 'دلیلِ تغییرِ مبلغ در خطِ زمانیِ سفارش');
  assert.ok(after.events.some((e) => e.kind === 'status' && e.toStatus === 'packed'));

  //  تکرارِ همان بسته ⇒ همان؛ کلیدِ دیگر ⇒ ۴۰۹؛ رویداد پس از بسته ⇒ رد
  const replay = await pack(o, order.id, { bags: 2 }, k);
  assert.equal(replay.status, 200);
  assert.equal(replay.body.replayed, true);
  const other = await pack(o, order.id, { bags: 2 });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'already_packed');
  r = await send(o, order.id, [ev(1, 'reset')]);
  assert.equal(r.body.results[0].code, 'pick_closed');
  const events = (await many("SELECT kind FROM online_order_events WHERE order_id=$1 AND to_status='packed'", [order.id]));
  assert.equal(events.length, 1, 'یک گذارِ «بسته‌بندی‌شده»');
  //  دفترِ سفارش پس از تغییرِ مبلغ هنوز سالم است: جمعِ قلم‌ها، شمارِ قلم، جمعِ کل و رویدادهای پیوسته
  const rep = await require('../src/lib/verify-data').verify({ shopId: o.shopId });
  assert.deepEqual(rep.checks.filter((c) => c.count > 0).map((c) => c.id), [], 'verify-data بی خطا');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) کلیدِ یکتا و هم‌زمانی
   ══════════════════════════════════════════════════════════════════ */

test('رویدادِ تکراری بی‌اثر است؛ همان شناسه با بدنهٔ دیگر رد؛ اسکنِ هم‌زمان بیش از سفارش نمی‌شمارد', async () => {
  const o = await pickShop('یکتا');
  const order = await acceptedOrder(o);
  await start(o, order.id);

  const one1 = ev(1, 'scan', { barcode: OIL });
  let r = await send(o, order.id, [one1]);
  assert.equal(r.body.results[0].status, 'accepted');
  r = await send(o, order.id, [one1]);
  assert.equal(r.body.results[0].replayed, true);
  assert.equal(lineOf(r.body.pick, 1).pickedQtyMilli, 1000, 'دوباره فرستادن دوباره نمی‌شمارد');
  r = await send(o, order.id, [{ ...one1, barcode: RICE }]);
  assert.equal(r.body.results[0].code, 'event_key_reused');
  //  اسکنِ اشتباهِ تکراری فقط یک بار شمرده می‌شود
  const wrong = ev(1, 'scan', { barcode: RICE });
  await send(o, order.id, [wrong]);
  r = await send(o, order.id, [wrong]);
  assert.equal(r.body.results[0].status, 'rejected');
  assert.equal(r.body.results[0].replayed, true);
  assert.equal(lineOf(r.body.pick, 1).wrongScans, 1);

  //  دو دستگاه هم‌زمان هر کدام «باقیِ» روغن را اسکن می‌کنند ⇒ یکی پذیرفته، دیگری بیش از سفارش
  const both = await Promise.all([
    send(o, order.id, [ev(1, 'scan', { barcode: OIL, qtyMilli: 1000 })]),
    send(o, order.id, [ev(1, 'scan', { barcode: OIL, qtyMilli: 1000 })]),
  ]);
  const verdicts = both.map((x) => x.body.results[0].status).sort();
  assert.deepEqual(verdicts, ['accepted', 'rejected']);
  assert.ok(both.some((x) => ['over_qty', 'line_done'].includes(x.body.results[0].code)));
  const sheet = (await h.get(`/api/shop/orders/${order.id}/pick`, { token: o.token })).body.pick;
  assert.equal(lineOf(sheet, 1).pickedQtyMilli, 2000);

  //  شروعِ هم‌زمان ⇒ یک برگه، یک گذار
  const o2 = await acceptedOrder(o);
  const starts = await Promise.all([start(o, o2.id), start(o, o2.id), start(o, o2.id)]);
  assert.ok(starts.every((x) => [200, 201].includes(x.status)), JSON.stringify(starts.map((x) => x.body)));
  assert.equal(starts.filter((x) => x.status === 201).length, 1);
  const trans = await many("SELECT 1 FROM online_order_events WHERE order_id=$1 AND to_status='picking'", [o2.id]);
  assert.equal(trans.length, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) مأموریت و نقدِ موردِ انتظار — PCK-07
   ══════════════════════════════════════════════════════════════════ */

test('PCK-07: کدِ دریافتِ ساخته‌شده پیش از جمع‌آوری ⇒ نقدِ موردِ انتظارِ مأموریت همان جمعِ تازه می‌شود', async () => {
  const o = await pickShop('نقد', { delivery: true });
  const order = await acceptedOrder(o);
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'pickup' }, {
    token: o.token, headers: { 'Idempotency-Key': key('asg') },
  });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.assignment.expectedCollectMinor, order.totalMinor);
  await start(o, order.id);
  const r = await send(o, order.id, [
    ev(1, 'scan', { barcode: OIL, qtyMilli: 2000 }), ev(2, 'not_found'), ev(3, 'not_found'), ev(4, 'pick', { qtyMilli: 1000 }),
  ]);
  assert.ok(r.body.results.every((x) => x.status === 'accepted'), JSON.stringify(r.body.results));
  const p = await pack(o, order.id, { bags: 1 });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.pick.totals.totalMinor, 61000);
  const asg = await one('SELECT expected_collect_minor FROM delivery_assignments WHERE id=$1', [a.body.assignment.id]);
  assert.equal(Number(asg.expected_collect_minor), 61000, 'نقدی که صندوق می‌گیرد = جمعِ تازه');
  //  دو کالا پیدا نشد ⇒ دو وظیفه
  const iss = (await h.get('/api/shop/orders/pick/issues', { token: o.token })).body.issues;
  assert.deepEqual(iss.map((x) => x.productId).sort(), ['rice', 'salt']);
});

/** سفارشِ پرداخت‌شده (بازبینیِ پول): ۶۵۰ = روغنِ ۶۰۰ + کیسهٔ ۵۰. */
const PAID_LINES = [LINES[0], { name: 'کیسهٔ اضافه', qtyMilli: 1000, unitPriceMinor: 5000 }];
async function paidOrder(o, lines = PAID_LINES) {
  const order = await acceptedOrder(o, { lines });
  await query("UPDATE online_orders SET payment_status='paid', payment_method='online' WHERE id=$1", [order.id]);
  return order;
}
const moneyOf = async (id) => one(
  `SELECT o.status, o.total_minor::int AS total, (SELECT COUNT(*) FROM online_order_lines WHERE order_id=o.id)::int AS lines,
          (SELECT COUNT(*) FROM online_order_refunds WHERE order_id=o.id)::int AS refunds,
          (SELECT COUNT(*) FROM online_order_events WHERE order_id=o.id AND kind='note')::int AS notes
     FROM online_orders o WHERE o.id=$1`, [id]
);

test('PCK-07 (پول): سفارشِ پرداخت‌شده با کسر ⇒ ۴۰۹ paid_needs_refund و هیچ تغییری — با پرچمِ بازپرداخت یا بی آن', async () => {
  const o = await pickShop('پرداخت‌شده');
  for (const refunds of [false, true]) {
    if (refunds) await setFlag(o.shopId, 'shop.orders.refunds', true);
    const order = await paidOrder(o);
    assert.equal(order.totalMinor, 65000);
    await start(o, order.id);
    //  بازتولیدِ بازبین: روغنِ ۶۰۰ «پیدا نکردم» ⇒ جمعِ تازه ۵۰ در حالی که ۶۵۰ پرداخت شده
    const r = await send(o, order.id, [ev(1, 'not_found'), ev(2, 'pick', { qtyMilli: 1000 })]);
    assert.ok(r.body.results.every((x) => x.status === 'accepted'), JSON.stringify(r.body.results));
    const p = await pack(o, order.id, { bags: 1 });
    assert.equal(p.status, 409, JSON.stringify(p.body));
    assert.equal(p.body.error.code, 'paid_needs_refund');
    assert.equal(p.body.error.deductedMinor, 60000);
    assert.equal(p.body.error.paidMinor, 65000);
    assert.equal(p.body.error.totalMinor, 5000);
    assert.deepEqual(await moneyOf(order.id), { status: 'picking', total: 65000, lines: 2, refunds: 0, notes: 0 },
      `مبلغ، قلم‌ها و وضعیت دست‌نخورده (refunds=${refunds})`);
    const pick = await one('SELECT status, pack_key FROM order_picks WHERE order_id=$1', [order.id]);
    assert.equal(pick.status, 'picking', 'برگه باز می‌ماند');
  }
  //  پرداخت‌شده و همه برداشته شد (مبلغ همان) ⇒ بسته می‌شود
  const full = await paidOrder(o);
  await start(o, full.id);
  await send(o, full.id, [ev(1, 'scan', { barcode: OIL, qtyMilli: 2000 }), ev(2, 'pick', { qtyMilli: 1000 })]);
  const ok = await pack(o, full.id, { bags: 1 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await moneyOf(full.id)).total, 65000);
  const rep = await require('../src/lib/verify-data').verify({ shopId: o.shopId });
  assert.deepEqual(rep.checks.filter((c) => c.count > 0).map((c) => c.id), [], 'verify-data بی خطا');
});

test('PCK-04 (پول): وزنِ بیشتر روی سفارشِ پرداخت‌شده ⇒ ۴۰۹ paid_total_increase و مبلغ همان؛ نقد هنگامِ تحویل همان رفتار', async () => {
  const o = await pickShop('وزن-پرداخت');
  const order = await paidOrder(o, [LINES[1]]);
  assert.equal(order.totalMinor, 18100);
  await start(o, order.id);
  const r = await send(o, order.id, [ev(1, 'scan', { barcode: RICE }), ev(1, 'weigh', { qtyMilli: 1600 })]);
  assert.ok(r.body.results.every((x) => x.status === 'accepted'), JSON.stringify(r.body.results));
  const p = await pack(o, order.id, { bags: 1 });
  assert.equal(p.status, 409, JSON.stringify(p.body));
  assert.equal(p.body.error.code, 'paid_total_increase');
  assert.equal(p.body.error.paidMinor, 18100);
  assert.equal(p.body.error.totalMinor, 19300);
  assert.deepEqual(await moneyOf(order.id), { status: 'picking', total: 18100, lines: 1, refunds: 0, notes: 0 });
  //  وزنِ درست ⇒ بسته
  const again = await send(o, order.id, [ev(1, 'reset'), ev(1, 'scan', { barcode: RICE }), ev(1, 'weigh', { qtyMilli: 1500 })]);
  assert.ok(again.body.results.every((x) => x.status === 'accepted'), JSON.stringify(again.body.results));
  const ok = await pack(o, order.id, { bags: 1 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  //  نقد هنگامِ تحویل: افزایشِ وزن مثلِ پیش پذیرفته است (پول هنوز گرفته نشده)
  const cod = await acceptedOrder(o, { lines: [LINES[1]] });
  await start(o, cod.id);
  await send(o, cod.id, [ev(1, 'scan', { barcode: RICE }), ev(1, 'weigh', { qtyMilli: 1600 })]);
  const c = await pack(o, cod.id, { bags: 1 });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  assert.equal(c.body.pick.totals.totalMinor, 19300);
});

test('PCK-07: بسته‌ای که به پیک سپرده و برداشته شده ⇒ ۴۰۹ already_dispatched و هیچ مبلغی عوض نمی‌شود', async () => {
  const o = await pickShop('سپرده', { delivery: true });
  const order = await acceptedOrder(o);
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'pickup' }, {
    token: o.token, headers: { 'Idempotency-Key': key('asg') },
  });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  await start(o, order.id);
  await send(o, order.id, [
    ev(1, 'scan', { barcode: OIL, qtyMilli: 2000 }), ev(2, 'not_found'), ev(3, 'not_found'), ev(4, 'pick', { qtyMilli: 1000 }),
  ]);
  //  مأموریت از دکان بیرون رفته (پیک برداشته) — نقدِ موردِ انتظارش دیگر نباید عوض شود
  await query("UPDATE delivery_assignments SET status='picked_up' WHERE id=$1", [a.body.assignment.id]);
  const p = await pack(o, order.id, { bags: 1 });
  assert.equal(p.status, 409, JSON.stringify(p.body));
  assert.equal(p.body.error.code, 'already_dispatched');
  const before = order.totalMinor;
  assert.deepEqual(await moneyOf(order.id), { status: 'picking', total: before, lines: 4, refunds: 0, notes: 0 });
  const asg = await one('SELECT expected_collect_minor FROM delivery_assignments WHERE id=$1', [a.body.assignment.id]);
  assert.equal(Number(asg.expected_collect_minor), before, 'نقدِ موردِ انتظارِ پیکِ رفته همان');
});

test('PCK-07: هیچ قلمی برداشته نشد ⇒ بسته نمی‌شود (لغو با «ناموجود»)؛ لغو در میانه ⇒ برگه بسته', async () => {
  const o = await pickShop('هیچ');
  const order = await acceptedOrder(o, { lines: [LINES[2]] });
  await start(o, order.id);
  await send(o, order.id, [ev(1, 'not_found')]);
  const p = await pack(o, order.id, { bags: 1 });
  assert.equal(p.status, 409);
  assert.equal(p.body.error.code, 'nothing_picked');
  const c = await h.post(`/api/shop/orders/${order.id}/transition`, { to: 'cancelled', reason: 'out_of_stock' }, { token: o.token });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const r = await send(o, order.id, [ev(1, 'reset')]);
  assert.equal(r.body.results[0].code, 'pick_closed');
  const p2 = await pack(o, order.id, { bags: 1 });
  assert.equal(p2.status, 409);
  assert.equal(p2.body.error.code, 'order_not_picking');
  assert.equal(p2.body.error.current, 'cancelled');
});

/* ══════════════════════════════════════════════════════════════════
   ۶) ویترین — PCK-14
   ══════════════════════════════════════════════════════════════════ */

test('PCK-14: «پیدا نکردم» کالا را تا رسیدگیِ مدیر از ویترین برمی‌دارد؛ «از نو» ِ تنها برخورد کنارش می‌گذارد', async () => {
  const o = await pickShop('ویترین');
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `pk-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ جمع‌آوری', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  for (const id of ['salt', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  const stockOf = async (pid) => (await h.get(`/api/store/${slug}/products`)).body.items.find((p) => p.id === pid).stock;
  assert.notEqual((await stockOf('salt')).status, 'out_of_stock');

  const order = await acceptedOrder(o);
  await start(o, order.id);
  let r = await send(o, order.id, [ev(3, 'not_found')]);
  assert.equal(r.body.results[0].status, 'accepted');
  const held0 = await stockOf('salt');
  assert.equal(held0.status, 'out_of_stock');
  assert.equal(held0.reason, 'pick_review');
  assert.notEqual((await stockOf('oil')).status, 'out_of_stock', 'فقط همان کالا');

  //  ثبتِ سفارشِ ویترین با همان کالا ⇒ «دیگر در ویترین نیست»
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone: '0791112299' });
  const t = await h.post(`/api/store/${slug}/verify/check`, { phone: '0791112299', code: v.body.devCode });
  const ticket = t.body.ticket;
  const buy = (lines) => h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone: '0791112299' }, lines,
  }, { headers: { 'Idempotency-Key': key('ck'), 'X-Store-Ticket': ticket } });
  const held = await buy([{ productId: 'salt', qtyMilli: 1000 }]);
  assert.equal(held.status, 409, JSON.stringify(held.body));
  assert.equal(held.body.error.code, 'product_unavailable');
  assert.deepEqual(held.body.error.unavailable, ['salt']);
  assert.equal((await buy([{ productId: 'oil', qtyMilli: 1000 }])).status, 201, 'کالای دیگر می‌فروشد');

  //  «از نو» روی همان قلم ⇒ وظیفهٔ تک‌برخورد کنار می‌رود و کالا برمی‌گردد
  r = await send(o, order.id, [ev(3, 'reset')]);
  assert.equal(r.body.results[0].status, 'accepted');
  assert.notEqual((await stockOf('salt')).status, 'out_of_stock');
  const dismissed = (await h.get('/api/shop/orders/pick/issues?status=dismissed', { token: o.token })).body.issues;
  assert.equal(dismissed.length, 1);
  assert.equal(dismissed[0].resolvedBy, 'system');

  //  دوباره پیدا نشد ⇒ مدیر رسیدگی می‌کند (توضیح لازم) ⇒ کالا برمی‌گردد
  await send(o, order.id, [ev(3, 'not_found')]);
  const open = (await h.get('/api/shop/orders/pick/issues', { token: o.token })).body.issues[0];
  const bad = await h.post(`/api/shop/orders/pick/issues/${open.id}/resolve`, { action: 'resolved' }, { token: o.token });
  assert.equal(bad.status, 400);
  const ok = await h.post(`/api/shop/orders/pick/issues/${open.id}/resolve`, { action: 'resolved', note: 'در انبار بود؛ به قفسه آمد' }, { token: o.token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.issue.status, 'resolved');
  assert.notEqual((await stockOf('salt')).status, 'out_of_stock');
  const twice = await h.post(`/api/shop/orders/pick/issues/${open.id}/resolve`, { action: 'resolved', note: 'دوباره' }, { token: o.token });
  assert.equal(twice.status, 404, 'وظیفهٔ بسته دوباره بسته نمی‌شود');

  //  پرچم خاموش ⇒ وظیفهٔ باز اثری بر ویترین ندارد (رفتارِ پیشین)
  await send(o, order.id, [ev(3, 'reset'), ev(3, 'not_found')]);
  assert.equal((await stockOf('salt')).status, 'out_of_stock');
  await setFlag(o.shopId, 'shop.orders.picking', false);
  assert.notEqual((await stockOf('salt')).status, 'out_of_stock');
  assert.equal((await buy([{ productId: 'salt', qtyMilli: 1000 }])).status, 201);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) شاخص‌ها — PCK-13
   ══════════════════════════════════════════════════════════════════ */

test('PCK-13: زمانِ جمع‌آوری، درصدِ پیدا شدن و اسکنِ اشتباه از برگه‌های بسته', async () => {
  const o = await pickShop('شاخص');
  const empty = await h.get('/api/shop/orders/pick/metrics', { token: o.token });
  assert.equal(empty.status, 200);
  assert.equal(empty.body.orders, 0);
  assert.equal(empty.body.foundRateBp, null, 'بی داده ⇒ null');

  const order = await acceptedOrder(o);
  await start(o, order.id);
  await query('UPDATE order_picks SET started_at = started_at - 90000 WHERE order_id=$1', [order.id]);
  await send(o, order.id, [
    ev(1, 'scan', { barcode: RICE }), ev(1, 'scan', { barcode: OIL, qtyMilli: 2000 }),
    ev(2, 'scan', { barcode: RICE }), ev(2, 'weigh', { qtyMilli: 1500 }), ev(3, 'not_found'), ev(4, 'pick', { qtyMilli: 1000 }),
  ]);
  assert.equal((await pack(o, order.id, { bags: 1 })).status, 200);
  const m = await h.get('/api/shop/orders/pick/metrics', { token: o.token });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  assert.equal(m.body.orders, 1);
  assert.equal(m.body.lines, 4);
  assert.equal(m.body.found, 3);
  assert.equal(m.body.notFound, 1);
  assert.equal(m.body.foundRateBp, 7500);
  assert.equal(m.body.substitutionRateBp, 0);
  assert.equal(m.body.wrongScans, 1);
  assert.ok(m.body.avgPickMs >= 90000, String(m.body.avgPickMs));
  assert.equal((await h.get('/api/shop/orders/pick/metrics?from=10&to=5', { token: o.token })).status, 400);
});

/* ══════════════════════════════════════════════════════════════════
   ۸) دکانِ دیگر، نقش و اشتراک
   ══════════════════════════════════════════════════════════════════ */

test('دکانِ دیگر برگه را نمی‌بیند و دست نمی‌زند؛ شاگرد جمع می‌کند ولی وظیفه را نمی‌بندد؛ اشتراکِ تمام‌شده فقط نوشتن را می‌بندد', async () => {
  const a = await pickShop('مالِ الف');
  const b = await pickShop('مالِ ب');
  const order = await acceptedOrder(a);
  await start(a, order.id);
  await send(a, order.id, [ev(3, 'not_found')]);
  const issue = (await h.get('/api/shop/orders/pick/issues', { token: a.token })).body.issues[0];
  const before = await one('SELECT version FROM order_picks WHERE order_id=$1', [order.id]);

  for (const [m, url, body] of [
    ['GET', `/api/shop/orders/${order.id}/pick`, null],
    ['POST', `/api/shop/orders/${order.id}/pick/start`, {}],
    ['POST', `/api/shop/orders/${order.id}/pick/events`, { events: [ev(1, 'not_found')] }],
    ['POST', `/api/shop/orders/${order.id}/pick/pack`, { bags: 1 }],
    ['POST', `/api/shop/orders/pick/issues/${issue.id}/resolve`, { action: 'resolved', note: 'ب' }],
  ]) {
    const r = await h.api(m, url, { body, token: b.token, headers: { 'Idempotency-Key': key() } });
    assert.equal(r.status, 404, `${m} ${url} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.ok(!JSON.stringify(r.body).includes(order.id));
  }
  assert.equal((await h.get('/api/shop/orders/pick/issues', { token: b.token })).body.issues.length, 0);
  const after = await one('SELECT version FROM order_picks WHERE order_id=$1', [order.id]);
  assert.equal(after.version, before.version, 'دکانِ «ب» هیچ چیزِ «الف» را عوض نکرد');

  const staff = await member(a.shopId, 'staff', 'شاگردِ جمع‌آور');
  const r = await h.post(`/api/shop/orders/${order.id}/pick/events`, { events: [ev(4, 'pick', { qtyMilli: 1000 })] }, { token: staff.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].status, 'accepted');
  const res = await h.post(`/api/shop/orders/pick/issues/${issue.id}/resolve`, { action: 'resolved', note: 'شاگرد' }, { token: staff.token });
  assert.equal(res.status, 403, 'رسیدگی کارِ مدیر است (stock.adjust)');

  //  اشتراکِ تمام‌شده: نوشتن ۴۰۳ ِ اشتراکی، خواندن باز
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [a.shopId, Date.now() - 400 * 24 * 60 * 60 * 1000]);
  const w = await send(a, order.id, [ev(1, 'not_found')]);
  assert.equal(w.status, 403);
  assert.match(w.body.error.code, /^subscription_/);
  const read = await h.get(`/api/shop/orders/${order.id}/pick`, { token: a.token });
  assert.equal(read.status, 200);
});
