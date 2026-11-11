'use strict';
/**
 * SR47 — بازبینیِ امنیتیِ دستهٔ ۴۷: نگه‌داشتِ وزن (S56، `lib/pick-weight-hold.js`) زیرِ درخواست‌های هم‌زمان و دسترسیِ بی‌جا.
 * روی PostgreSQL (استخرِ چند اتصالی) هم‌زمانیِ واقعی است؛ روی PGlite همان قاعده‌ها پشتِ سرِ هم.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پذیرشِ مشتری و ردِ کارمند هم‌زمان ⇒ دقیقاً یک تصمیم؛ قلم با همان تصمیم می‌خواند (پذیرفته ⇒ همان وزن، رد ⇒ حذف).
 *   ۲) پذیرشِ مشتری و بسته‌بندی هم‌زمان ⇒ یا بسته ۴۰۹ `weight_hold_pending` یا بسته با همان مبلغِ پذیرفته — هرگز بیشتر.
 *   ۳) پذیرشِ مشتری و وزنِ تازهٔ جمع‌آور هم‌زمان ⇒ یا پذیرفته و وزنِ تازه رد (`line_done`)، یا پس‌گرفته و پذیرش ۴۰۹ —
 *      هرگز دو نگه‌داشتِ زنده یا قلمِ «برداشته» با وزنی جز وزنِ پذیرفته.
 *   ۴) دو پذیرشِ هم‌زمانِ مشتری ⇒ یکی تازه، دیگری `replayed`؛ تصمیمِ پس از مهلت هم‌زمان با بسته ⇒ فقط یک رد.
 *   ۵) ⛔ توکنِ درستِ سفارشِ دیگرِ **همان** دکان با شناسهٔ نگه‌داشتِ این سفارش ⇒ ۴۰۴ و هیچ تغییر (IDOR).
 */
process.env.RATE_STORE_CHECKOUT_MAX = '400';
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
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-sr47' }, body: { device_id: 'dev-sr47', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکان با ویترینِ روشن، گوشت (وزنی، بی‌بارکد) و روغن (شمارشی) */
async function storeShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const f of ['shop.orders', 'shop.orders.picking', 'shop.orders.weight_hold', 'shop.storefront']) await setFlag(o.shopId, f, true);
  const p = await push(o.token, [
    op('meat', { name: 'گوشت', unit: 'کیلو', salePrice: 500, category: 'گوشت' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  o.slug = `sr47-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: 'ویترینِ SR47', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  for (const id of ['meat', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  return o;
}

/** سفارشِ ویترین (گوشت ۱ کیلو + روغن)، قبول و جمع‌آوری شروع */
async function pickingOrder(o) {
  const phone = `079${String(1000000 + ((seq * 7919) % 8999999)).slice(-7)}`;
  uniq();
  const v = await h.post(`/api/store/${o.slug}/verify/start`, { phone });
  const t = await h.post(`/api/store/${o.slug}/verify/check`, { phone, code: v.body.devCode });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  const ck = await h.post(`/api/store/${o.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'meat', qtyMilli: 1000 }, { productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': key('ck'), 'X-Store-Ticket': t.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [o.shopId])).id;
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: o.token })).status, 200);
  assert.equal((await h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: o.token })).status, 201);
  const lines = (await h.get(`/api/shop/orders/${id}/pick`, { token: o.token })).body.pick.lines;
  const meat = lines.find((l) => l.productId === 'meat').lineNo;
  const oil = lines.find((l) => l.productId === 'oil').lineNo;
  return { id, token: ck.body.order.token, meat, oil };
}

/** همان سفارش؛ روغن برداشته و گوشت ۱٫۳ کیلو ⇒ نگه‌داشت */
async function heldOrder(o) {
  const x = await pickingOrder(o);
  const r = await send(o, x.id, [ev(x.oil, 'pick', { qtyMilli: 1000 }), ev(x.meat, 'weigh', { qtyMilli: 1300 })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const hold = r.body.results[1].hold;
  assert.ok(hold && hold.status === 'pending', JSON.stringify(r.body.results));
  return { ...x, hold };
}

const send = (o, id, events) => h.post(`/api/shop/orders/${id}/pick/events`, { events }, { token: o.token });
const ev = (lineNo, type, extra = {}) => ({ eventId: key('ev'), lineNo, type, ...extra });
const pack = (o, id) => h.post(`/api/shop/orders/${id}/pick/pack`, { bags: 1 }, { token: o.token, headers: { 'Idempotency-Key': key('pack') } });
const staff = (o, id, holdId, decision) => h.post(`/api/shop/orders/${id}/pick/weight-holds/decide`, { holdId, decision }, { token: o.token });
const customer = (o, x, holdId, decision) => h.post(`/api/store/${o.slug}/orders/${x.token}/weight-holds/${holdId}`, { decision });
const lineRow = (id, n) => one('SELECT status, picked_qty_milli::int AS picked FROM order_pick_lines WHERE order_id=$1 AND line_no=$2', [id, n]);
const holdsOf = async (id) => (await query(
  'SELECT id, status, decided_by, actual_qty_milli::int AS actual, total_minor::int AS total FROM order_pick_weight_holds WHERE order_id=$1 ORDER BY created_at, id', [id]
)).rows;
const ok5 = (...rs) => rs.forEach((r) => assert.ok(r.status < 500, JSON.stringify(r.body)));

test('SR47 هم‌زمان: پذیرشِ مشتری و ردِ کارمند ⇒ دقیقاً یک تصمیم و قلم با همان می‌خواند', async () => {
  const o = await storeShop('هم‌زمان-تصمیم');
  for (let i = 0; i < 4; i++) {
    const x = await heldOrder(o);
    const [c, s] = await Promise.all([customer(o, x, x.hold.id, 'accept'), staff(o, x.id, x.hold.id, 'reject')]);
    ok5(c, s);
    const won = [c, s].filter((r) => r.status === 200);
    assert.equal(won.length, 1, `${c.status} ${s.status}`);
    const lost = [c, s].find((r) => r.status !== 200);
    assert.equal(lost.status, 409);
    assert.equal(lost.body.error.code, 'already_decided');
    const [row] = await holdsOf(x.id);
    const line = await lineRow(x.id, x.meat);
    if (row.status === 'accepted') {
      assert.equal(row.decided_by, 'customer');
      assert.deepEqual(line, { status: 'picked', picked: 1300 });
    } else {
      assert.deepEqual([row.status, row.decided_by], ['rejected', 'staff']);
      assert.deepEqual(line, { status: 'removed', picked: 0 });
    }
  }
});

test('SR47 هم‌زمان: پذیرشِ مشتری و بسته‌بندی ⇒ ۴۰۹ ِ صبر یا بسته با همان مبلغِ پذیرفته — هرگز بیشتر', async () => {
  const o = await storeShop('هم‌زمان-بسته');
  for (let i = 0; i < 4; i++) {
    const x = await heldOrder(o);
    const [c, p] = await Promise.all([customer(o, x, x.hold.id, 'accept'), pack(o, x.id)]);
    ok5(c, p);
    assert.equal(c.status, 200, JSON.stringify(c.body));
    if (p.status !== 200) {
      assert.equal(p.status, 409, JSON.stringify(p.body));
      assert.equal(p.body.error.code, 'weight_hold_pending');
      const again = await pack(o, x.id);
      assert.equal(again.status, 200, JSON.stringify(again.body));
    }
    //  گوشتِ ۱٫۳ کیلو ۶۵۰۰۰ + روغن ۳۰۰۰۰
    const ord = await one('SELECT status, total_minor::int AS t FROM online_orders WHERE id=$1', [x.id]);
    assert.deepEqual(ord, { status: 'packed', t: 95000 });
    assert.deepEqual((await holdsOf(x.id)).map((r) => r.status), ['accepted']);
  }
});

test('SR47 هم‌زمان: پذیرشِ مشتری و وزنِ تازهٔ جمع‌آور ⇒ هرگز دو نگه‌داشتِ زنده یا وزنِ نپذیرفته', async () => {
  const o = await storeShop('هم‌زمان-وزن');
  for (let i = 0; i < 4; i++) {
    const x = await heldOrder(o);
    const [c, w] = await Promise.all([customer(o, x, x.hold.id, 'accept'), send(o, x.id, [ev(x.meat, 'weigh', { qtyMilli: 1450 })])]);
    ok5(c, w);
    const res = w.body.results[0];
    const rows = await holdsOf(x.id);
    const line = await lineRow(x.id, x.meat);
    assert.ok(rows.filter((r) => ['pending', 'accepted'].includes(r.status)).length <= 1, JSON.stringify(rows));
    if (c.status === 200) {
      //  پذیرش اول رسید ⇒ وزنِ تازه رد شد
      assert.equal(res.status, 'rejected', JSON.stringify(res));
      assert.equal(res.code, 'line_done');
      assert.deepEqual(rows.map((r) => r.status), ['accepted']);
      assert.deepEqual(line, { status: 'picked', picked: 1300 });
    } else {
      assert.equal(c.status, 409, JSON.stringify(c.body));
      assert.equal(c.body.error.code, 'already_decided');
      assert.equal(res.status, 'accepted');
      assert.deepEqual(rows.map((r) => [r.status, r.actual]), [['withdrawn', 1300], ['pending', 1450]]);
      assert.equal(line.status, 'pending');
    }
  }
});

test('SR47 هم‌زمان: دو پذیرشِ مشتری ⇒ یکی تازه و دیگری replayed؛ تصمیمِ پس از مهلت با بسته ⇒ فقط یک رد', async () => {
  const o = await storeShop('هم‌زمان-تکرار');
  const x = await heldOrder(o);
  const pair = await Promise.all([customer(o, x, x.hold.id, 'accept'), customer(o, x, x.hold.id, 'accept')]);
  ok5(...pair);
  assert.deepEqual(pair.map((r) => r.status), [200, 200]);
  assert.deepEqual(pair.map((r) => r.body.replayed).sort(), [false, true]);

  const y = await heldOrder(o);
  await query('UPDATE order_pick_weight_holds SET deadline_at=$2 WHERE id=$1', [y.hold.id, now() - 1000]);
  const [c, p] = await Promise.all([customer(o, y, y.hold.id, 'accept'), pack(o, y.id)]);
  ok5(c, p);
  assert.equal(c.status, 409, JSON.stringify(c.body));
  assert.ok(['weight_hold_expired', 'already_decided'].includes(c.body.error.code), c.body.error.code);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.deepEqual((await holdsOf(y.id)).map((r) => [r.status, r.decided_by]), [['rejected', 'timeout']]);
  assert.deepEqual(await one('SELECT status, total_minor::int AS t FROM online_orders WHERE id=$1', [y.id]), { status: 'packed', t: 30000 });
});

test('⛔ SR47 IDOR: توکنِ درستِ سفارشِ دیگرِ همان دکان با نگه‌داشتِ این سفارش ⇒ ۴۰۴ و هیچ تغییر', async () => {
  const o = await storeShop('هم‌دکان');
  const a = await heldOrder(o);
  const b = await heldOrder(o);
  for (const decision of ['accept', 'reject']) {
    const r = await customer(o, b, a.hold.id, decision);
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'weight_hold_not_found');
  }
  //  فهرستِ b فقط نگه‌داشتِ خودش
  const list = await h.get(`/api/store/${o.slug}/orders/${b.token}/weight-holds`);
  assert.deepEqual(list.body.weightHolds.map((w) => w.id), [b.hold.id]);
  //  کارمند هم با شناسهٔ سفارشِ دیگر ⇒ پیدا نشد
  assert.equal((await staff(o, b.id, a.hold.id, 'accept')).status, 404);
  assert.deepEqual((await holdsOf(a.id)).map((r) => r.status), ['pending']);
  assert.deepEqual(await lineRow(a.id, a.meat), { status: 'pending', picked: 0 });
});

//  SR47-2: سقفِ پولِ بسته‌بندی فقط جمع را می‌سنجید ⇒ سهمِ قلمِ حذف‌شده (یا کم‌برداشته) بیشیِ قلمِ دیگرِ بی رضایت را می‌پوشاند.
test('SR47 سقفِ پول: هر قلم جدا — قلمِ حذف‌شده بیشیِ بی‌رضایتِ قلمِ دیگر را نمی‌پوشاند (۴۰۹ over_cap، هیچ تغییر)', async () => {
  const o = await storeShop('سقفِ-هر-قلم');
  const x = await pickingOrder(o);
  const r = await send(o, x.id, [ev(x.oil, 'not_found'), ev(x.meat, 'weigh', { qtyMilli: 1100 })]);
  assert.ok(r.body.results.every((e) => e.status === 'accepted'), JSON.stringify(r.body.results));
  //  دادهٔ خراب (باگ یا دست‌کاری): گوشت ۱٫۵ کیلو «برداشته» بی هیچ نگه‌داشتِ پذیرفته — جمع (۷۵۰۰۰) زیرِ سقفِ جمع (۵۵۰۰۰ + ۳۰۰۰۰)
  await query('UPDATE order_pick_lines SET picked_qty_milli=1500 WHERE order_id=$1 AND line_no=$2', [x.id, x.meat]);
  const p = await pack(o, x.id);
  assert.equal(p.status, 409, JSON.stringify(p.body));
  assert.equal(p.body.error.code, 'over_cap');
  assert.deepEqual(p.body.error.lines, [x.meat]);
  assert.deepEqual(await one('SELECT status, total_minor::int AS t FROM online_orders WHERE id=$1', [x.id]), { status: 'picking', t: 80000 });
  //  در حد (۱٫۱ کیلو) ⇒ بسته با کسرِ روغن
  await query('UPDATE order_pick_lines SET picked_qty_milli=1100 WHERE order_id=$1 AND line_no=$2', [x.id, x.meat]);
  const ok = await pack(o, x.id);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.pick.totals.totalMinor, 55000);
});
