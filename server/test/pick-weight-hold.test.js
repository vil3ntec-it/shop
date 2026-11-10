'use strict';
/**
 * S56 «وزنِ نهایی» (§۱۶؛ PCK-04) — وزنِ واقعیِ بیش از حدِ مجاز تا پاسخِ مشتری نگه داشته می‌شود؛ پرچمِ خاموشِ
 * `shop.orders.weight_hold` (با `shop.orders.picking`، `lib/pick-weight-hold.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ خاموش ⇒ همان ۴۰۰ ِ `over_tolerance`، هیچ ردیف، درِ کارمند ۴۰۳ و درِ مشتری ۴۰۴، `/config` بی کلید.
 *   ۲) روشن ⇒ قلم باز می‌ماند و نگه‌داشت با مبلغِ همان وزن؛ بسته‌بندی ۴۰۹ `weight_hold_pending` تا پاسخ.
 *   ۳) پذیرش (کارمند پس از تماس، یا مشتری با لینکِ پیگیری) ⇒ قلم با همان وزن و مبلغ، نقدِ موردِ انتظارِ مأموریت = جمعِ تازه.
 *   ۴) رد، یا بی‌پاسخ تا مهلت (policy) ⇒ قلم «حذف شد» و از جمع کسر؛ تصمیمِ دیر ۴۰۹ `weight_hold_expired`.
 *   ۵) وزنِ تازه، «پیدا نکردم» یا «از نو» نگه‌داشت را پس می‌گیرد؛ تکرارِ رویداد همان نگه‌داشت (نه دومی).
 *   ۶) سفارشِ پرداخت‌شده نگه‌داشت نمی‌گیرد (پولِ بیشتر گرفتنی نیست) — همان `over_tolerance`.
 *   ۷) سقفِ پولِ بسته‌بندی: جمعِ اقلام بیش از سفارش + حدِ وزن + مبلغِ پذیرفته ⇒ ۴۰۹ `over_cap`.
 *   ۸) ⛔ دکانِ دیگر، توکنِ دیگر، ویترینِ دیگر (نقش‌ها: `shop-route-inventory`).
 */
process.env.RATE_STORE_CHECKOUT_MAX = '40';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
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

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-wh' }, body: { device_id: 'dev-wh', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });
const RICE = '6260000000017';

/** دکانِ روشن با برنج (بارکددار، وزنی)، گوشت (بی‌بارکد، وزنی) و روغن (شمارشی). */
async function holdShop(label, { hold = true, delivery = false } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.orders.picking', true);
  if (hold) await setFlag(o.shopId, 'shop.orders.weight_hold', true);
  if (delivery) await setFlag(o.shopId, 'shop.delivery', true);
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی', barcode: RICE }),
    op('meat', { name: 'گوشت', unit: 'کیلو', salePrice: 500, category: 'گوشت' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  return o;
}

//  برنجِ ۱٫۵ کیلو ۱۸۰۷۵ ⇒ گرد به افغانی ۱۸۱۰۰؛ + روغن ۳۰۰۰۰ + گوشتِ ۱ کیلو ۵۰۰۰۰ = ۹۸۱۰۰
const LINES = [
  { productId: 'rice', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 12050 },
  { productId: 'oil', name: 'روغن', unit: 'بوتل', qtyMilli: 1000, unitPriceMinor: 30000 },
  { productId: 'meat', name: 'گوشت', unit: 'کیلو', qtyMilli: 1000, unitPriceMinor: 50000 },
];

async function acceptedOrder(o, lines = LINES) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' }, lines,
  }, { token: o.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal((await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to: 'accepted' }, { token: o.token })).status, 200);
  return r.body.order;
}

const start = async (o, id) => assert.equal((await h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: o.token })).status, 201);
const send = (o, id, events) => h.post(`/api/shop/orders/${id}/pick/events`, { events }, { token: o.token });
const ev = (lineNo, type, extra = {}) => ({ eventId: key('ev'), lineNo, type, ...extra });
const pack = (o, id) => h.post(`/api/shop/orders/${id}/pick/pack`, { bags: 1 }, { token: o.token, headers: { 'Idempotency-Key': key('pack') } });
const decide = (o, id, holdId, decision) => h.post(`/api/shop/orders/${id}/pick/weight-holds/decide`,
  { holdId, decision }, { token: o.token });
const sheetOf = async (o, id) => (await h.get(`/api/shop/orders/${id}/pick`, { token: o.token })).body.pick;
const lineOf = (sheet, n) => sheet.lines.find((l) => l.lineNo === n);
const holdRows = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM order_pick_weight_holds WHERE shop_id=$1', [shopId])).n;
/** برنج و روغن برداشته، گوشت ۱٫۳ کیلو (۳۰٪ بیشتر) ⇒ نگه‌داشت */
async function overweight(o, id) {
  const r = await send(o, id, [ev(1, 'scan', { barcode: RICE }), ev(1, 'weigh', { qtyMilli: 1500 }), ev(2, 'pick', { qtyMilli: 1000 }),
    ev(3, 'weigh', { qtyMilli: 1300 })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results[3];
}

test('⛔ S56 خاموش: وزنِ بیش از حد همان ۴۰۰ ِ over_tolerance، بی ردیف؛ درِ کارمند ۴۰۳ و درِ مشتری ۴۰۴؛ /config بی کلید', async () => {
  const o = await holdShop('وزن-خاموش', { hold: false });
  const cfg = await h.get('/api/shop/orders/config', { token: o.token });
  assert.equal(cfg.body.picking, true);
  assert.equal('weightHold' in cfg.body, false);
  const order = await acceptedOrder(o);
  await start(o, order.id);
  const r = await overweight(o, order.id);
  assert.equal(r.status, 'rejected');
  assert.equal(r.code, 'over_tolerance');
  assert.equal(r.maxMilli, 1100);
  assert.equal(r.hold, undefined);
  assert.equal(await holdRows(o.shopId), 0);
  assert.equal('weightHolds' in (await sheetOf(o, order.id)), false, 'برگه همان شکلِ پیشین');
  const d = await decide(o, order.id, '01ARZ3NDEKTSV4RRFFQ69G5FAV', 'accept');
  assert.equal(d.status, 403);
  assert.equal(d.body.error.code, 'weight_hold_disabled');
  //  وزنِ درست ⇒ بسته مثلِ پیش
  await send(o, order.id, [ev(3, 'weigh', { qtyMilli: 1000 })]);
  assert.equal((await pack(o, order.id)).status, 200);
});

test('S56: وزنِ بیش از حد ⇒ نگه‌داشت و بسته تا پاسخ ۴۰۹؛ پذیرشِ کارمند ⇒ همان وزن و مبلغ، نقدِ مأموریت = جمعِ تازه', async () => {
  const o = await holdShop('وزن-پذیرش', { delivery: true });
  assert.equal((await h.get('/api/shop/orders/config', { token: o.token })).body.weightHold, true);
  const order = await acceptedOrder(o);
  assert.equal(order.totalMinor, 98100);
  const a = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'pickup' }, { token: o.token, headers: { 'Idempotency-Key': key('asg') } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  await start(o, order.id);
  const r = await overweight(o, order.id);
  assert.equal(r.status, 'accepted', JSON.stringify(r));
  assert.equal(r.line.status, 'pending', 'قلم باز می‌ماند');
  assert.equal(r.hold.status, 'pending');
  assert.equal(r.hold.actualQtyMilli, 1300);
  assert.equal(r.hold.totalMinor, 65000);
  assert.equal(r.hold.extraMinor, 15000);
  assert.ok(r.hold.deadlineAt > now(), 'مهلت در آینده');
  assert.ok('customer' in r, 'لینکِ مشتری برای فرستادن');
  const sheet = await sheetOf(o, order.id);
  assert.equal(sheet.weightHolds.length, 1);

  const p1 = await pack(o, order.id);
  assert.equal(p1.status, 409, JSON.stringify(p1.body));
  assert.equal(p1.body.error.code, 'weight_hold_pending');
  assert.equal(p1.body.error.pending, 1);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [order.id])).status, 'picking');

  const d = await decide(o, order.id, r.hold.id, 'accept');
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(d.body.hold.status, 'accepted');
  assert.equal(d.body.hold.decidedBy, 'staff');
  assert.equal((await decide(o, order.id, r.hold.id, 'accept')).body.replayed, true);
  const flip = await decide(o, order.id, r.hold.id, 'reject');
  assert.equal(flip.status, 409);
  assert.equal(flip.body.error.code, 'already_decided');
  const line = lineOf(await sheetOf(o, order.id), 3);
  assert.equal(line.status, 'picked');
  assert.equal(line.pickedQtyMilli, 1300);

  const p2 = await pack(o, order.id);
  assert.equal(p2.status, 200, JSON.stringify(p2.body));
  //  ۱۸۱۰۰ + ۳۰۰۰۰ + ۶۵۰۰۰
  assert.equal(p2.body.pick.totals.totalMinor, 113100);
  const ol = await one('SELECT qty_milli::int AS q, line_total_minor::int AS t FROM online_order_lines WHERE order_id=$1 AND line_no=3', [order.id]);
  assert.deepEqual(ol, { q: 1300, t: 65000 });
  const asg = await one('SELECT expected_collect_minor FROM delivery_assignments WHERE id=$1', [a.body.assignment.id]);
  assert.equal(Number(asg.expected_collect_minor), 113100, 'نقدی که صندوق می‌گیرد = جمعِ پذیرفته');
  const note = await one(`SELECT detail FROM online_order_events WHERE order_id=$1 AND kind='note' ORDER BY seq DESC LIMIT 1`, [order.id]);
  const detail = typeof note.detail === 'string' ? JSON.parse(note.detail) : note.detail;
  assert.deepEqual(detail.pick.weightHolds, [{ lineNo: 3, totalMinor: 65000 }]);
});

test('S56: ردِ کارمند ⇒ قلم «حذف شد» و کسر؛ بی‌پاسخ تا مهلت ⇒ همان (policy)، تصمیمِ دیر ۴۰۹ weight_hold_expired', async () => {
  const o = await holdShop('وزن-رد');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  const r = await overweight(o, order.id);
  const d = await decide(o, order.id, r.hold.id, 'reject');
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(lineOf(await sheetOf(o, order.id), 3).status, 'removed');
  const p = await pack(o, order.id);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal(p.body.pick.totals.totalMinor, 48100);
  assert.equal(p.body.pick.totals.deductedMinor, 50000);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM online_order_lines WHERE order_id=$1', [order.id])).n, 2);

  //  مهلت: بسته‌بندی خودش رد را اعمال می‌کند
  const late = await acceptedOrder(o);
  await start(o, late.id);
  const lr = await overweight(o, late.id);
  await query('UPDATE order_pick_weight_holds SET deadline_at=$2 WHERE id=$1', [lr.hold.id, now() - 1000]);
  const view = (await sheetOf(o, late.id)).weightHolds[0];
  assert.equal(view.expired, true);
  assert.equal(view.willApply, 'reject');
  const lp = await pack(o, late.id);
  assert.equal(lp.status, 200, JSON.stringify(lp.body));
  assert.equal(lp.body.pick.totals.totalMinor, 48100);
  const row = await one('SELECT status, decided_by FROM order_pick_weight_holds WHERE id=$1', [lr.hold.id]);
  assert.deepEqual(row, { status: 'rejected', decided_by: 'timeout' });

  //  تصمیمِ پس از مهلت: رد اعمال و ۴۰۹ با نتیجه
  const third = await acceptedOrder(o);
  await start(o, third.id);
  const tr = await overweight(o, third.id);
  await query('UPDATE order_pick_weight_holds SET deadline_at=$2 WHERE id=$1', [tr.hold.id, now() - 1000]);
  const td = await decide(o, third.id, tr.hold.id, 'accept');
  assert.equal(td.status, 409);
  assert.equal(td.body.error.code, 'weight_hold_expired');
  assert.equal(td.body.error.status, 'rejected');
  assert.equal(lineOf(await sheetOf(o, third.id), 3).status, 'removed');
});

test('S56: وزنِ تازه، «پیدا نکردم» یا «از نو» نگه‌داشت را پس می‌گیرد؛ تکرارِ رویداد همان نگه‌داشت', async () => {
  const o = await holdShop('وزن-پس');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  const first = ev(3, 'weigh', { qtyMilli: 1300 });
  const a = await send(o, order.id, [first]);
  const hold1 = a.body.results[0].hold;
  //  همان رویداد دوباره (صفِ آفلاین) ⇒ همان نگه‌داشت، نه دومی
  const again = await send(o, order.id, [first]);
  assert.equal(again.body.results[0].replayed, true);
  assert.equal(again.body.results[0].hold.id, hold1.id);
  assert.equal(await holdRows(o.shopId), 1);
  //  «از نو» روی قلمِ وزنیِ بی‌اسکن با نگه‌داشتِ باز پذیرفته است
  const rs = await send(o, order.id, [ev(3, 'reset')]);
  assert.equal(rs.body.results[0].status, 'accepted', JSON.stringify(rs.body.results));
  assert.equal((await one('SELECT status FROM order_pick_weight_holds WHERE id=$1', [hold1.id])).status, 'withdrawn');
  //  دوباره زیاد ⇒ نگه‌داشتِ تازه؛ سپس وزنِ درست ⇒ پس گرفته و قلم برداشته
  const b = await send(o, order.id, [ev(3, 'weigh', { qtyMilli: 1250 })]);
  const hold2 = b.body.results[0].hold;
  assert.notEqual(hold2.id, hold1.id);
  const c = await send(o, order.id, [ev(3, 'weigh', { qtyMilli: 1050 })]);
  assert.equal(c.body.results[0].line.status, 'picked');
  assert.equal((await one('SELECT status, decided_by FROM order_pick_weight_holds WHERE id=$1', [hold2.id])).status, 'withdrawn');
  //  مشتری دیگر نمی‌تواند نگه‌داشتِ پس‌گرفته را بپذیرد
  const late = await decide(o, order.id, hold2.id, 'accept');
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'already_decided');
  //  «پیدا نکردم» هم پس می‌گیرد
  const other = await acceptedOrder(o);
  await start(o, other.id);
  const x = await send(o, other.id, [ev(3, 'weigh', { qtyMilli: 1300 })]);
  await send(o, other.id, [ev(3, 'not_found')]);
  assert.equal((await one('SELECT status FROM order_pick_weight_holds WHERE id=$1', [x.body.results[0].hold.id])).status, 'withdrawn');
  assert.equal(lineOf(await sheetOf(o, other.id), 3).status, 'not_found');
});

test('S56: سفارشِ پرداخت‌شده نگه‌داشت نمی‌گیرد — همان over_tolerance (پولِ بیشتر گرفتنی نیست)', async () => {
  const o = await holdShop('وزن-پرداخت');
  const order = await acceptedOrder(o);
  await query("UPDATE online_orders SET payment_status='paid', payment_method='online' WHERE id=$1", [order.id]);
  await start(o, order.id);
  const r = await overweight(o, order.id);
  assert.equal(r.status, 'rejected');
  assert.equal(r.code, 'over_tolerance');
  assert.equal(await holdRows(o.shopId), 0);
});

test('S56 سقفِ پول: جمعِ اقلام بیش از سفارش + حدِ وزن (بی رضایت) ⇒ ۴۰۹ over_cap و هیچ تغییر', async () => {
  const o = await holdShop('وزن-سقف');
  const order = await acceptedOrder(o);
  await start(o, order.id);
  const r = await send(o, order.id, [ev(1, 'scan', { barcode: RICE }), ev(1, 'weigh', { qtyMilli: 1500 }), ev(2, 'pick', { qtyMilli: 1000 }),
    ev(3, 'weigh', { qtyMilli: 1100 })]);
  assert.ok(r.body.results.every((x) => x.status === 'accepted'), JSON.stringify(r.body.results));
  //  دادهٔ خراب (باگ یا دست‌کاری): گوشت ۲ کیلو «برداشته» بی هیچ نگه‌داشتِ پذیرفته
  await query("UPDATE order_pick_lines SET picked_qty_milli=2000 WHERE order_id=$1 AND line_no=3", [order.id]);
  const p = await pack(o, order.id);
  assert.equal(p.status, 409, JSON.stringify(p.body));
  assert.equal(p.body.error.code, 'over_cap');
  //  سقف = برنج تا ۱٫۶۵ کیلو (۱۹۹۰۰) + روغن ۳۰۰۰۰ + گوشت تا ۱٫۱ کیلو (۵۵۰۰۰)
  assert.equal(p.body.error.capMinor, 104900);
  assert.equal((await one('SELECT status, total_minor::int AS t FROM online_orders WHERE id=$1', [order.id])).t, 98100);
  //  وزن در حد (۱٫۱ کیلو) ⇒ همان سقف، بسته می‌شود
  await query("UPDATE order_pick_lines SET picked_qty_milli=1100 WHERE order_id=$1 AND line_no=3", [order.id]);
  const ok = await pack(o, order.id);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.pick.totals.totalMinor, 103100);
});

/* ── مشتری با لینکِ پیگیری ─────────────────────────────────────────── */

async function storefrontOrder(label) {
  const o = await holdShop(label);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `wh-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ وزن', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  for (const id of ['meat', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  const phone = `079${String(1000000 + (seq % 8999999)).slice(-7)}`;
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const t = await h.post(`/api/store/${slug}/verify/check`, { phone, code: v.body.devCode });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  const ck = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ ویترین', phone }, lines: [{ productId: 'meat', qtyMilli: 1000 }, { productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': key('ck'), 'X-Store-Ticket': t.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [o.shopId])).id;
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: o.token })).status, 200);
  return { o, slug, id, token: ck.body.order.token };
}

test('S56: مشتری با لینکِ پیگیری وزنِ بیشتر را می‌بیند و رد می‌کند ⇒ قلم حذف و کسر؛ نمای مشتری بی دادهٔ داخلی', async () => {
  const { o, slug, id, token } = await storefrontOrder('وزن-مشتری');
  await start(o, id);
  const meat = (await sheetOf(o, id)).lines.find((l) => l.productId === 'meat').lineNo;
  const oil = meat === 1 ? 2 : 1;
  const r = await send(o, id, [ev(meat, 'weigh', { qtyMilli: 1400 }), ev(oil, 'pick', { qtyMilli: 1000 })]);
  const hold = r.body.results[0].hold;
  assert.ok(hold, JSON.stringify(r.body.results));
  assert.ok(r.body.results[0].customer.link.endsWith(`#/o/${encodeURIComponent(token)}`), r.body.results[0].customer.link);

  const view = await h.get(`/api/store/${slug}/orders/${token}/weight-holds`);
  assert.equal(view.status, 200, JSON.stringify(view.body));
  assert.equal(view.body.weightHolds.length, 1);
  const w = view.body.weightHolds[0];
  assert.equal(w.name, 'گوشت');
  assert.deepEqual([w.orderedQtyMilli, w.actualQtyMilli, w.originalTotalMinor, w.totalMinor, w.extraMinor], [1000, 1400, 50000, 70000, 20000]);
  assert.equal(w.status, 'pending');
  const raw = JSON.stringify(view.body);
  for (const leak of [o.user.id, 'event_key', 'proposed', 'decidedAt']) assert.ok(!raw.includes(leak), `نمای مشتری ${leak} دارد`);

  assert.equal((await h.post(`/api/store/${slug}/orders/${token}/weight-holds/${hold.id}`, { decision: 'maybe' })).status, 400);
  const no = await h.post(`/api/store/${slug}/orders/${token}/weight-holds/${hold.id}`, { decision: 'reject' });
  assert.equal(no.status, 200, JSON.stringify(no.body));
  assert.equal(no.body.weightHold.status, 'rejected');
  assert.equal(no.body.weightHold.decidedBy, 'customer');
  assert.equal((await h.post(`/api/store/${slug}/orders/${token}/weight-holds/${hold.id}`, { decision: 'reject' })).body.replayed, true);
  const flip = await h.post(`/api/store/${slug}/orders/${token}/weight-holds/${hold.id}`, { decision: 'accept' });
  assert.equal(flip.status, 409);
  assert.equal(flip.body.error.code, 'already_decided');

  const pk = await pack(o, id);
  assert.equal(pk.status, 200, JSON.stringify(pk.body));
  const tr = await h.get(`/api/store/${slug}/orders/${token}`);
  assert.deepEqual(tr.body.order.lines.map((l) => l.name), ['روغن']);
  assert.equal(tr.body.order.totalMinor, 30000);
});

test('⛔ S56: توکنِ نادرست، ویترینِ دیگر، دکانِ دیگر، پرچمِ خاموش ⇒ ۴۰۴ و هیچ تغییر', async () => {
  const A = await storefrontOrder('وزن-توکن-الف');
  const B = await storefrontOrder('وزن-توکن-ب');
  await start(A.o, A.id);
  const meat = (await sheetOf(A.o, A.id)).lines.find((l) => l.productId === 'meat').lineNo;
  const hold = (await send(A.o, A.id, [ev(meat, 'weigh', { qtyMilli: 1400 })])).body.results[0].hold;
  const forged = `${A.id}.${'A'.repeat(32)}`;
  for (const [slug, token] of [[A.slug, forged], [B.slug, A.token], [A.slug, B.token], [A.slug, 'nope']]) {
    assert.equal((await h.get(`/api/store/${slug}/orders/${token}/weight-holds`)).status, 404, `${slug} ${token}`);
    assert.equal((await h.post(`/api/store/${slug}/orders/${token}/weight-holds/${hold.id}`, { decision: 'accept' })).status, 404);
  }
  //  توکنِ درستِ B با نگه‌داشتِ A ⇒ پیدا نشد؛ کارمندِ B با سفارشِ A ⇒ ۴۰۴
  assert.equal((await h.post(`/api/store/${B.slug}/orders/${B.token}/weight-holds/${hold.id}`, { decision: 'accept' })).status, 404);
  assert.equal((await decide(B.o, A.id, hold.id, 'accept')).status, 404);
  assert.equal((await one('SELECT status FROM order_pick_weight_holds WHERE id=$1', [hold.id])).status, 'pending');

  await setFlag(A.o.shopId, 'shop.orders.weight_hold', false);
  assert.equal((await h.get(`/api/store/${A.slug}/orders/${A.token}/weight-holds`)).status, 404);
  assert.equal((await h.post(`/api/store/${A.slug}/orders/${A.token}/weight-holds/${hold.id}`, { decision: 'accept' })).status, 404);
  assert.equal((await one('SELECT status FROM order_pick_weight_holds WHERE id=$1', [hold.id])).status, 'pending');
  //  پیگیریِ عادی همان رفتارِ پیشین
  assert.equal((await h.get(`/api/store/${A.slug}/orders/${A.token}`)).status, 200);
});
