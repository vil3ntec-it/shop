'use strict';
/**
 * قفسهٔ انتظار و اسکنِ «آماده» (PCK-10) و جمع‌آوریِ چند سفارش با هم (PCK-12) — پشتِ پرچمِ خاموشِ
 * `shop.orders.picking`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ خاموش ⇒ ۴۰۳ و هیچ ردیف.
 *   ۲) PCK-10: هر کیسه با برچسبِ خودش روی یک جا؛ اسکنِ دوباره بی‌اثر؛ همه ⇒ «آماده» (دریافت از دکان ⇒
 *      ready_for_pickup فقط از درِ سفارش)؛ برچسبِ کهنه، بسته‌نشده، دکانِ دیگر و برچسبِ بد رد.
 *   ۳) PCK-12: دستهٔ ۲ تا ۱۰ سفارش با کلیدِ یکتایی؛ فهرستِ یک‌جا به ترتیبِ قفسه با «سبدِ» هر سفارش؛
 *      سنجشِ همه پیش از هر نوشتن؛ دستهٔ دیگر، دکانِ دیگر؛ بسته‌بندیِ جدای هر سفارش.
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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-shelf') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکان با سه کالای بی‌بارکد (تأییدِ دستی) و جای قفسه */
async function shelfShop(label, { picking = true } = {}) {
  const o = await shopOwner(label);
  await setFlag(o.shopId, 'shop.orders', true);
  if (picking) await setFlag(o.shopId, 'shop.orders.picking', true);
  const p = await push(o.token, [
    op('salt', { name: 'نمک', unit: 'بسته', salePrice: 50, category: 'خوراکی' }),
    op('tea', { name: 'چای', unit: 'بسته', salePrice: 200, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  if (picking) {
    for (const [id, aisle] of [['salt', '3'], ['tea', '1'], ['soap', '2']]) {
      assert.equal((await h.put(`/api/shop/orders/pick/locations/${id}`, { aisle }, { token: o.token })).status, 200);
    }
  }
  return o;
}

const L = {
  salt: { productId: 'salt', name: 'نمک', unit: 'بسته', qtyMilli: 1000, unitPriceMinor: 5000 },
  tea: { productId: 'tea', name: 'چای', unit: 'بسته', qtyMilli: 2000, unitPriceMinor: 20000 },
  soap: { productId: 'soap', name: 'صابون', unit: 'دانه', qtyMilli: 1000, unitPriceMinor: 4000 },
};

async function order(o, lines, { accept = true } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' }, lines,
  }, { token: o.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  if (accept) assert.equal((await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to: 'accepted' }, { token: o.token })).status, 200);
  return r.body.order;
}

const send = (o, id, events) => h.post(`/api/shop/orders/${id}/pick/events`, { events }, { token: o.token });
const ev = (lineNo, type, extra = {}) => ({ eventId: key('ev'), lineNo, type, ...extra });
const pack = (o, id, bags) => h.post(`/api/shop/orders/${id}/pick/pack`, { bags }, { token: o.token, headers: { 'Idempotency-Key': key('pack') } });
const scan = (o, qr, slot) => h.post('/api/shop/orders/pick/shelf/scan', { qr, slot }, { token: o.token });
const batch = (o, orderIds, k = key('batch')) => h.post('/api/shop/orders/pick/batches', { orderIds }, { token: o.token, headers: { 'Idempotency-Key': k } });
const status = async (id) => (await one('SELECT status FROM online_orders WHERE id=$1', [id])).status;

/** سفارشِ قبول‌شده ⇒ برگه ⇒ همه برداشته ⇒ بسته با `bags` کیسه */
async function packed(o, lines, bags) {
  const x = await order(o, lines);
  assert.equal((await h.post(`/api/shop/orders/${x.id}/pick/start`, {}, { token: o.token })).status, 201);
  const r = await send(o, x.id, lines.map((l, i) => ev(i + 1, 'pick', { qtyMilli: l.qtyMilli })));
  for (const y of r.body.results) assert.equal(y.status, 'accepted', JSON.stringify(y));
  const p = await pack(o, x.id, bags);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  return { ...x, orderNo: p.body.pick.orderNo };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پرچمِ خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ جمع‌آوری خاموش: قفسه و دسته ۴۰۳ picking_disabled و هیچ ردیف', async () => {
  const o = await shelfShop('قفسه-خاموش', { picking: false });
  const a = await order(o, [L.salt]);
  const b = await order(o, [L.tea]);
  for (const r of [
    await h.get('/api/shop/orders/pick/shelf', { token: o.token }),
    await scan(o, 'VLNB-1-1-1', 'A1'),
    await batch(o, [a.id, b.id]),
    await h.get(`/api/shop/orders/pick/batches/B${'0'.repeat(24)}`, { token: o.token }),
  ]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'picking_disabled');
  }
  const n = (await one(`SELECT (SELECT COUNT(*) FROM order_picks WHERE shop_id=$1)::int
    + (SELECT COUNT(*) FROM order_pick_shelf_scans WHERE shop_id=$1)::int AS n`, [o.shopId])).n;
  assert.equal(n, 0);
  assert.equal(await status(a.id), 'accepted');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) PCK-10 — قفسهٔ انتظار و اسکنِ «آماده»
   ══════════════════════════════════════════════════════════════════ */

test('PCK-10: هر کیسه روی جا اسکن می‌شود؛ تکرار بی‌اثر؛ همهٔ کیسه‌ها ⇒ «آماده» و «آمادهٔ دریافت»', async () => {
  const o = await shelfShop('قفسه-آماده');
  const x = await packed(o, [L.salt, L.tea], 2);
  let r = await scan(o, `VLNB-${x.orderNo}-1-2`, 'A2');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.scanned, r.body.ready, r.body.status], [1, false, 'packed']);
  r = await scan(o, `VLNB-${x.orderNo}-1-2`, 'A2');
  assert.deepEqual([r.body.scanned, r.body.ready], [1, false], 'اسکنِ دوبارهٔ همان کیسه');
  //  بارکدخوان با رقمِ فارسی و فاصله
  r = await scan(o, ` VLNB-${String(x.orderNo).replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d])}-۲-۲ `, ' A2 ');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.scanned, r.body.ready, r.body.readyNow, r.body.status, r.body.slot], [2, true, true, 'ready_for_pickup', 'A2']);
  assert.equal(await status(x.id), 'ready_for_pickup');
  const ev1 = await one(`SELECT actor_type FROM online_order_events WHERE order_id=$1 AND to_status='ready_for_pickup'`, [x.id]);
  assert.equal(ev1.actor_type, 'user', 'گذار از درِ سفارش با رویداد');
  r = await scan(o, `VLNB-${x.orderNo}-2-2`, 'A2');
  assert.deepEqual([r.body.ready, r.body.readyNow], [true, false], 'آماده دو بار «تازه» نمی‌شود');
  const ready1 = (await one('SELECT ready_scanned_at FROM order_picks WHERE order_id=$1', [x.id])).ready_scanned_at;

  //  دومی روی A10 — ترتیبِ طبیعی (A2 پیش از A10)؛ یک کیسه ⇒ همان دم آماده
  const y = await packed(o, [L.soap], 1);
  r = await scan(o, `VLNB-${y.orderNo}-1-1`, 'A10');
  assert.equal(r.body.ready, true);
  const sh = await h.get('/api/shop/orders/pick/shelf', { token: o.token });
  assert.equal(sh.status, 200);
  assert.deepEqual(sh.body.shelf.map((s) => [s.orderNo, s.slot, s.scanned, s.bags, s.ready]),
    [[x.orderNo, 'A2', 2, 2, true], [y.orderNo, 'A10', 1, 1, true]]);
  assert.equal(Number(sh.body.shelf[0].readyAt), Number(ready1));

  //  مشتری گرفت ⇒ از قفسه بیرون
  assert.equal((await h.post(`/api/shop/orders/${x.id}/transition`, { to: 'picked_up' }, { token: o.token })).status, 200);
  assert.deepEqual((await h.get('/api/shop/orders/pick/shelf', { token: o.token })).body.shelf.map((s) => s.orderNo), [y.orderNo]);
  const late = await scan(o, `VLNB-${x.orderNo}-1-2`, 'A2');
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'order_not_on_shelf');
});

test('PCK-10: برچسبِ کهنه، سفارشِ بسته‌نشده، برچسبِ بد، بی جا و دکانِ دیگر رد می‌شوند', async () => {
  const o = await shelfShop('قفسه-رد');
  const x = await packed(o, [L.salt], 2);
  let r = await scan(o, `VLNB-${x.orderNo}-1-3`, 'B1');
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'label_mismatch');
  assert.equal(r.body.error.bags, 2);
  for (const qr of ['VLNH-1-123456', `VLNB-${x.orderNo}-3-2`, `VLNB-${x.orderNo}-0-2`, 'hello', '']) {
    r = await scan(o, qr, 'B1');
    assert.equal(r.status, 400, qr);
    assert.equal(r.body.error.code, 'bad_label', qr);
  }
  r = await scan(o, `VLNB-${x.orderNo}-1-2`, '  ');
  assert.equal(r.body.error.code, 'slot_required');
  const open = await order(o, [L.tea]);
  await h.post(`/api/shop/orders/${open.id}/pick/start`, {}, { token: o.token });
  const no = (await one('SELECT order_no FROM online_orders WHERE id=$1', [open.id])).order_no;
  r = await scan(o, `VLNB-${no}-1-1`, 'B1');
  assert.equal(r.body.error.code, 'not_packed');
  const notStarted = await order(o, [L.tea]);
  const no2 = (await one('SELECT order_no FROM online_orders WHERE id=$1', [notStarted.id])).order_no;
  r = await scan(o, `VLNB-${no2}-1-1`, 'B1');
  assert.equal(r.status, 404);
  //  دکانِ دیگر با همان شمارهٔ سفارش ⇒ سفارشِ خودش (بسته‌نشده)، نه سفارشِ «الف»
  const b = await shelfShop('قفسه-رد-ب');
  r = await scan(b, `VLNB-${x.orderNo}-1-2`, 'B1');
  assert.ok([404, 409].includes(r.status), JSON.stringify(r.body));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_pick_shelf_scans WHERE order_id=$1', [x.id])).n, 0);
  assert.equal((await h.get('/api/shop/orders/pick/shelf', { token: b.token })).body.shelf.length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) PCK-12 — جمع‌آوریِ گروهی
   ══════════════════════════════════════════════════════════════════ */

test('PCK-12: دو سفارش با هم — فهرستِ یک‌جا به ترتیبِ قفسه با سبدِ هر سفارش؛ تکرار ⇒ همان دسته؛ بستهٔ جدا', async () => {
  const o = await shelfShop('دسته');
  const a = await order(o, [L.salt, L.tea]);
  const b = await order(o, [L.soap, L.tea]);
  const k = key('batch');
  const r = await batch(o, [b.id, a.id], k);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const bt = r.body.batch;
  assert.match(bt.batchId, /^B[0-9a-f]{24}$/);
  assert.deepEqual(bt.orders.map((x) => [x.orderId, x.bin]), [[a.id, 1], [b.id, 2]], 'سبد به ترتیبِ شمارهٔ سفارش');
  //  راهرو: چای ۱، صابون ۲، نمک ۳ — دو چای کنارِ هم
  assert.deepEqual(bt.lines.map((l) => [l.productId, l.bin]), [['tea', 1], ['tea', 2], ['soap', 2], ['salt', 1]]);
  assert.deepEqual(bt.progress, { total: 4, pending: 4, done: 0 });
  assert.equal(await status(a.id), 'picking');
  assert.equal(await status(b.id), 'picking');

  const again = await batch(o, [b.id, a.id], k);
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.batch.batchId, bt.batchId);

  //  جمع‌آور از روی فهرست برمی‌دارد (رویدادِ هر سفارش) ⇒ پیشرفتِ دسته
  for (const l of bt.lines) {
    const x = await send(o, l.orderId, [ev(l.lineNo, 'pick', { qtyMilli: l.orderedQtyMilli })]);
    assert.equal(x.body.results[0].status, 'accepted', JSON.stringify(x.body));
  }
  const g = await h.get(`/api/shop/orders/pick/batches/${bt.batchId}`, { token: o.token });
  assert.deepEqual(g.body.batch.progress, { total: 4, pending: 0, done: 4 });
  //  هر سفارش جدا بسته می‌شود (کیسه و جمعِ خودش)
  assert.equal((await pack(o, a.id, 1)).body.pick.totals.totalMinor, 5000 + 40000);
  assert.equal((await pack(o, b.id, 2)).body.pick.totals.totalMinor, 4000 + 40000);
  const g2 = await h.get(`/api/shop/orders/pick/batches/${bt.batchId}`, { token: o.token });
  assert.deepEqual(g2.body.batch.orders.map((x) => x.status), ['packed', 'packed']);
});

test('PCK-12: همه پیش از نوشتن سنجیده می‌شوند — «قبول‌نشده»، دستهٔ دیگر، تعدادِ بد و دکانِ دیگر ⇒ هیچ برگه', async () => {
  const o = await shelfShop('دسته-رد');
  const a = await order(o, [L.salt]);
  const b = await order(o, [L.tea]);
  const fresh = await order(o, [L.soap], { accept: false });
  let r = await batch(o, [a.id, fresh.id]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'order_not_pickable');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_picks WHERE shop_id=$1', [o.shopId])).n, 0, 'نیمه‌کاره نماند');
  for (const ids of [[a.id], [a.id, a.id], Array.from({ length: 11 }, () => a.id)]) {
    r = await batch(o, ids);
    assert.equal(r.status, 400, JSON.stringify(ids.length));
  }
  const other = await shelfShop('دسته-رد-ب');
  const foreign = await order(other, [L.salt]);
  r = await batch(o, [a.id, foreign.id]);
  assert.equal(r.status, 404);
  r = await batch(other, [foreign.id, a.id]);
  assert.equal(r.status, 404, 'سفارشِ «الف» برای «ب» نیست');

  r = await batch(o, [a.id, b.id]);
  assert.equal(r.status, 201);
  const c = await order(o, [L.soap]);
  r = await batch(o, [b.id, c.id]);
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'already_batched');
  assert.equal(await status(c.id), 'accepted', 'سفارشِ سوم برگه نگرفت');
  const g = await h.get(`/api/shop/orders/pick/batches/${r.body.error.orderId ? 'B' + 'f'.repeat(24) : 'x'}`, { token: o.token });
  assert.equal(g.status, 404);
  const cross = await h.get(`/api/shop/orders/pick/batches/${(await one(`SELECT batch_id FROM order_picks WHERE order_id=$1`, [a.id])).batch_id}`, { token: other.token });
  assert.equal(cross.status, 404, 'دستهٔ «الف» برای «ب» نیست');
});

test('PCK-10 pick-shelf-v1: برچسبِ کیسه و ترتیبِ جای قفسه — همان بردارِ PickDesk.kt', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const shelfLib = require('../src/lib/pick-shelf');
  const P = require('../src/lib/pick-core');
  const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'pick-shelf-v1.json'), 'utf8'));
  assert.equal(V.version, 1);
  for (const c of V.label) {
    let got = null;
    try { got = shelfLib.parseLabel(c.raw); } catch (e) { assert.equal(e.code, 'bad_label', c.raw); }
    assert.deepEqual(got, c.want, c.raw);
  }
  for (const c of V.slotOrder) assert.deepEqual([...c.slots].sort(P.compareNatural), c.want);
});
