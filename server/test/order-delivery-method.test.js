'use strict';
/**
 * S71 و S72 (§۱۹.۱۲) — روش و مسئولِ تحویل؛ تغییرِ روشِ تحویل پیش از جمع‌آوری (`lib/order-delivery-method.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش: ویترین بی `features.deliveryMethod`، پیگیری بی `deliveryMethod`، درِ تغییر ۴۰۴ و هیچ تغییری.
 *   ۲) S71: روش و مسئولِ هر سفارش از دادهٔ واقعی — دریافت/مشتری، ارسال/دکان، پیکِ دکان، شرکتِ بیرونی با نامش؛
 *      مأموریتِ `platform` یا پس‌گرفته هرگز پیکِ VILL3N نمی‌شود.
 *   ۳) S72 پیش از جمع‌آوری: دریافت ⇒ ارسال (ناحیه)، ناحیه ⇒ ناحیه، ارسال ⇒ دریافت؛ هزینه و جمع **همان** ثبتِ تازه با
 *      همان روش، زمانِ تقریبی همان `GET /eta` ِ برگهٔ ثبت و پس از تغییر همان `orderEta` ِ پیگیری؛ بی تأیید هیچ تغییری.
 *   ۴) پس از شروعِ جمع‌آوری، با مأموریتِ سپرده یا پرداخت‌شده ⇒ ۴۰۹ و هیچ تغییری.
 *   ۵) هم‌زمانی با «شروعِ جمع‌آوری»: یا تغییر پیش از برگه، یا رد — هرگز تغییر پس از آن.
 *   ۶) فقط صاحبِ سفارش؛ ۷) کلیدِ یکتایی: تکرار بی اثرِ دوم، همان کلید با بدنهٔ دیگر ۴۰۹.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.STORE_ETA_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const FLAG = 'shop.storefront.delivery_method';
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

const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });
const POLY_A = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
const POLY_B = [[34.60, 69.12], [34.60, 69.22], [34.66, 69.22], [34.66, 69.12]];

/** دکانِ روشن با ویترین، دو کالا، دو ناحیه (۵۰ و ۹۰ افغانی) و زمانِ تقریبی؛ `dm` = پرچمِ S71/S72. */
async function shop(label, { dm = true, flags = [] } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `dm-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.zones', 'shop.storefront.eta', ...flags]) await setFlag(s.shopId, k, true);
  if (dm) await setFlag(s.shopId, FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true },
    { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
    body: { device_id: 'dev-store', schema_version: 2, ops: [
      op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
      op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 333.33, category: 'خوراکی' }),
    ] },
  });
  assert.equal(pushed.status, 200);
  for (const p of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
  const z = await h.put('/api/shop/storefront/delivery', { zones: [
    { name: 'کارته‌سه', kind: 'polygon', polygon: POLY_A, feeMinor: 5000, minOrderMinor: 1000 },
    { name: 'خیرخانه', kind: 'polygon', polygon: POLY_B, feeMinor: 9000, minOrderMinor: 1000 },
  ] }, { token: s.token });
  assert.equal(z.status, 200, JSON.stringify(z.body));
  [s.zoneA, s.zoneB] = z.body.zones.map((x) => x.id);
  const e = await h.put('/api/shop/storefront/eta', { travelMinutes: 20, zoneTravel: { [s.zoneB]: 45 } }, { token: s.token });
  assert.equal(e.status, 200, JSON.stringify(e.body));
  return s;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
const PHONE = '0791112233';
const LINES = [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }];
const SUB = 36000 + 33300;   //  روغن ۳۳۳٫۳۳ ⇒ ۳۳۳ افغانی
const ADDR = (zoneId) => ({ text: 'کوچهٔ دوم، خانهٔ سبز', zoneId });
async function storeOrder(s, { phone = PHONE, body = {} } = {}) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: LINES, ...body },
    { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token, order: r.body.order };
}

const key = () => `dm-${uniq()}-key`;
async function change(s, token, body, { k = key(), phone = PHONE, ticket } = {}) {
  const headers = { 'Idempotency-Key': k };
  const t = ticket !== undefined ? ticket : await ticketFor(s.slug, phone);
  if (t) headers['X-Store-Ticket'] = t;
  return h.post(`/api/store/${s.slug}/orders/${token}/fulfillment`, body, { headers });
}
/** پیش‌نمایش (۴۰۹ `confirm_required`) و سپس پذیرفتن با همان جمع */
async function confirmed(s, token, body, opts = {}) {
  const q = await change(s, token, body, opts);
  assert.equal(q.status, 409, JSON.stringify(q.body));
  assert.equal(q.body.error.code, 'confirm_required');
  return change(s, token, { ...body, expectedTotalMinor: q.body.error.quote.totalMinor }, opts);
}
const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const orderRow = (id) => one('SELECT status, fulfillment, address, delivery_fee_minor, total_minor, subtotal_minor, version FROM online_orders WHERE id=$1', [id]);
const changeEvents = (id) => many(`SELECT seq, detail, actor_type FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='fulfillment_changed' ORDER BY seq`, [id]);
const move = async (s, id, to) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const asObj = (x) => (typeof x === 'string' ? JSON.parse(x) : x);
const range = (x) => (x ? { minMinutes: x.minMinutes, maxMinutes: x.maxMinutes } : null);

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: ویترین و پیگیری بی کلیدِ تازه، درِ تغییر ۴۰۴ و هیچ تغییری', async () => {
  const s = await shop('خاموش', { dm: false });
  const o = await storeOrder(s);
  const prof = await h.get(`/api/store/${s.slug}`);
  assert.equal(prof.status, 200);
  assert.equal('deliveryMethod' in (prof.body.features || {}), false);
  const t0 = await track(s, o.token);
  assert.equal(t0.status, 200);
  assert.equal('deliveryMethod' in t0.body.order, false);
  const before = await orderRow(o.id);
  const r = await change(s, o.token, { fulfillment: 'delivery', address: ADDR(s.zoneB) });
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.deepEqual(await orderRow(o.id), before);
  assert.equal((await changeEvents(o.id)).length, 0);
  //  روشن و دوباره خاموش ⇒ بدنهٔ پیگیری همان پیشین
  await setFlag(s.shopId, FLAG, true);
  assert.ok((await track(s, o.token)).body.order.deliveryMethod);
  await setFlag(s.shopId, FLAG, false);
  const t1 = await track(s, o.token);
  delete t0.body.order.eta; delete t1.body.order.eta;   // زمانِ تقریبیِ زنده (`updatedAt`)
  assert.deepEqual(t1.body, t0.body);
});

test('S71: روش و مسئولِ هر سفارش از دادهٔ واقعی؛ هیچ حالتی پیکِ VILL3N نمی‌شود', async () => {
  const s = await shop('روش', { flags: ['shop.delivery'] });
  const prof = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(prof.body.features.deliveryMethod, {
    delivery: { method: 'shop_delivery', responsible: 'shop' }, pickup: { method: 'pickup', responsible: 'customer' },
  });
  const p = await storeOrder(s);
  const tp = (await track(s, p.token)).body.order.deliveryMethod;
  assert.equal(tp.method, 'pickup');
  assert.equal(tp.responsible, 'customer');
  assert.equal(tp.change.allowed, true);
  assert.equal(tp.change.delivery, true);
  assert.equal(tp.change.needsAddress, true);
  assert.deepEqual(tp.change.zones.map((z) => z.feeMinor), [5000, 9000]);

  const d = await storeOrder(s, { body: { fulfillment: 'delivery', address: ADDR(s.zoneA) } });
  const view = async () => (await track(s, d.token)).body.order.deliveryMethod;
  let v = await view();
  assert.deepEqual([v.method, v.responsible], ['shop_delivery', 'shop']);
  assert.deepEqual(v.change.zones.map((z) => z.id), [s.zoneB], 'ناحیهٔ دیگر');
  assert.equal(v.change.pickup, true);

  //  شرکتِ بیرونی با API ِ واقعیِ سپردن ⇒ نامِ شرکت؛ مسئول همان دکان
  await move(s, d.id, 'accepted');
  const ext = await h.post(`/api/shop/delivery/orders/${d.id}/assign`, { mode: 'external', externalName: 'پیک‌رسانِ شهر' }, { token: s.token });
  assert.equal(ext.status, 201, JSON.stringify(ext.body));
  v = await view();
  assert.deepEqual([v.method, v.responsible, v.company], ['external', 'shop', 'پیک‌رسانِ شهر']);
  assert.equal(v.change.allowed, false, 'سپرده‌شده ⇒ دیگر نه');

  //  پیکِ خودِ دکان، پس‌گرفته و `platform` (داده‌ای که نباید باشد) — مستقیم در جدول
  const put = async (attempt, mode, status) => {
    await query('UPDATE delivery_assignments SET status=$2 WHERE order_id=$1 AND status <> ALL($3::text[])', [d.id, 'withdrawn', ['delivered']]);
    await query(`INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, status, created_at, updated_at)
                 VALUES ($1,$2,$3,$4,$5,$6,$7,$7)`, [newId('dla'), s.shopId, d.id, attempt, mode, status, now()]);
  };
  await put(2, 'shop_courier', 'assigned');
  v = await view();
  assert.deepEqual([v.method, v.responsible, 'company' in v], ['shop_courier', 'shop', false]);
  await put(3, 'platform', 'assigned');
  v = await view();
  assert.deepEqual([v.method, v.responsible], ['shop_delivery', 'shop'], 'platform ⇒ هرگز پیکِ VILL3N');
  await query('UPDATE delivery_assignments SET status=$2 WHERE order_id=$1', [d.id, 'withdrawn']);
  v = await view();
  assert.deepEqual([v.method, v.responsible], ['shop_delivery', 'shop'], 'پس‌گرفته ⇒ ارسال به دستِ دکان');

  //  ویترینِ فقط «ارسال» ⇒ فقط همان روش
  assert.equal((await h.put('/api/shop/storefront', { acceptPickup: false }, { token: s.token })).status, 200);
  assert.deepEqual((await h.get(`/api/store/${s.slug}`)).body.features.deliveryMethod, { delivery: { method: 'shop_delivery', responsible: 'shop' } });
});

test('S72: پیش از جمع‌آوری — هزینه، جمع و زمانِ تقریبی دقیقاً مثلِ ثبت؛ بی تأیید هیچ تغییری', async () => {
  const s = await shop('تغییر');
  const eta = require('../src/lib/storefront-eta');
  const sfRow = await one('SELECT * FROM storefronts WHERE shop_id=$1', [s.shopId]);
  //  مرجع: ثبتِ تازهٔ همان سبد با «ارسال» به ناحیهٔ ب
  const ref = await storeOrder(s, { body: { fulfillment: 'delivery', address: ADDR(s.zoneB) } });
  assert.equal(ref.order.deliveryFeeMinor, 9000);

  const o = await storeOrder(s);
  const pickupEta = (await track(s, o.token)).body.order.eta;
  const before = await orderRow(o.id);
  const body = { fulfillment: 'delivery', address: ADDR(s.zoneB) };
  const q = await change(s, o.token, body);
  assert.equal(q.status, 409, JSON.stringify(q.body));
  assert.equal(q.body.error.code, 'confirm_required');
  const quote = q.body.error.quote;
  assert.equal(quote.deliveryFeeMinor, ref.order.deliveryFeeMinor);
  assert.equal(quote.totalMinor, ref.order.totalMinor);
  assert.equal(quote.prevTotalMinor, SUB);
  assert.equal(quote.zoneName, 'خیرخانه');
  const pub = (await h.get(`/api/store/${s.slug}/eta`)).body.eta;
  assert.ok(pub.available, JSON.stringify(pub));
  assert.deepEqual(quote.eta, range(pub.zones.find((z) => z.id === s.zoneB)), 'همان زمانِ برگهٔ ثبت برای ناحیهٔ ب');
  assert.deepEqual(await orderRow(o.id), before, 'بی تأیید هیچ تغییری');

  const wrong = await change(s, o.token, { ...body, expectedTotalMinor: quote.totalMinor - 1 });
  assert.equal(wrong.status, 409);
  assert.equal(wrong.body.error.code, 'price_changed');
  assert.deepEqual(await orderRow(o.id), before);

  const ok = await change(s, o.token, { ...body, expectedTotalMinor: quote.totalMinor });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.replayed, false);
  assert.deepEqual([ok.body.changed.from.fulfillment, ok.body.changed.to.fulfillment], ['pickup', 'delivery']);
  assert.equal(ok.body.changed.differenceMinor, 9000);
  let row = await orderRow(o.id);
  assert.equal(row.fulfillment, 'delivery');
  assert.equal(Number(row.delivery_fee_minor), 9000);
  assert.equal(Number(row.total_minor), ref.order.totalMinor);
  assert.equal(asObj(row.address).zone.id, s.zoneB);
  assert.equal(Number(row.version), Number(before.version) + 1);
  const ev = await changeEvents(o.id);
  assert.equal(ev.length, 1);
  assert.equal(ev[0].actor_type, 'customer');
  //  پیگیری: روشِ تازه، جمعِ تازه و زمانِ زندهٔ همان سفارش با همان `orderEta`
  const t = (await track(s, o.token)).body.order;
  assert.equal(t.fulfillment, 'delivery');
  assert.equal(t.zoneName, 'خیرخانه');
  assert.equal(t.deliveryMethod.method, 'shop_delivery');
  assert.deepEqual(range(t.eta), range(await eta.orderEta(sfRow, o.id)));
  assert.ok(t.eta.maxMinutes > pickupEta.maxMinutes, 'راهِ ناحیهٔ ب افزوده شد');
  //  دکان: سابقه و نمای کارمند
  const audit = await one(`SELECT action FROM audit_logs WHERE shop_id=$1 AND target_id=$2 AND action='order.fulfillment_changed'`, [s.shopId, o.id]);
  assert.ok(audit);
  const staff = await h.get(`/api/shop/orders/${o.id}`, { token: s.token });
  assert.equal(staff.body.order.fulfillment, 'delivery');

  //  ناحیه ⇒ ناحیه (بی پین): همان هزینهٔ ثبت با ناحیهٔ الف
  const z = await confirmed(s, o.token, { fulfillment: 'delivery', address: { zoneId: s.zoneA } });
  assert.equal(z.status, 200, JSON.stringify(z.body));
  row = await orderRow(o.id);
  assert.deepEqual([Number(row.delivery_fee_minor), Number(row.total_minor), asObj(row.address).zone.id], [5000, SUB + 5000, s.zoneA]);
  assert.equal(asObj(row.address).text, 'کوچهٔ دوم، خانهٔ سبز', 'نشانی همان ماند');

  //  ارسال ⇒ دریافت: بی هزینه، بی نشانی
  const back = await confirmed(s, o.token, { fulfillment: 'pickup' });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  row = await orderRow(o.id);
  assert.deepEqual([row.fulfillment, Number(row.delivery_fee_minor), Number(row.total_minor), asObj(row.address)], ['pickup', 0, SUB, {}]);
  assert.equal((await changeEvents(o.id)).length, 3);

  //  همان روش ⇒ ۴۰۹ `method_unchanged`؛ ارسال بی نشانی ⇒ ۴۰۰
  const same = await change(s, o.token, { fulfillment: 'pickup' });
  assert.equal(same.status, 409);
  assert.equal(same.body.error.code, 'method_unchanged');
  const noAddr = await change(s, o.token, { fulfillment: 'delivery', address: { zoneId: s.zoneA } });
  assert.equal(noAddr.status, 400);
  assert.equal(noAddr.body.error.code, 'bad_address');
});

test('پس از شروعِ جمع‌آوری، با مأموریتِ سپرده یا پرداخت‌شده ⇒ ۴۰۹ و هیچ تغییری', async () => {
  const s = await shop('بسته', { flags: ['shop.delivery'] });
  const body = { fulfillment: 'delivery', address: ADDR(s.zoneA), expectedTotalMinor: SUB + 5000 };

  const o = await storeOrder(s);
  for (const to of ['accepted', 'picking']) await move(s, o.id, to);
  const before = await orderRow(o.id);
  const r = await change(s, o.token, body);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'order_preparing');
  assert.deepEqual(await orderRow(o.id), before);
  const v = (await track(s, o.token)).body.order.deliveryMethod;
  assert.deepEqual(v.change, { allowed: false, preparing: true });

  const a = await storeOrder(s);
  await move(s, a.id, 'accepted');
  const pk = await h.post(`/api/shop/delivery/orders/${a.id}/assign`, { mode: 'pickup' }, { token: s.token });
  assert.equal(pk.status, 201, JSON.stringify(pk.body));
  const ra = await change(s, a.token, body);
  assert.equal(ra.status, 409);
  assert.equal(ra.body.error.code, 'delivery_assigned');

  const paid = await storeOrder(s);
  await query(`UPDATE online_orders SET payment_status='paid' WHERE id=$1`, [paid.id]);
  const rp = await change(s, paid.token, body);
  assert.equal(rp.status, 409);
  assert.equal(rp.body.error.code, 'amend_paid_order');
  for (const x of [a, paid]) assert.equal((await changeEvents(x.id)).length, 0);
});

test('هم‌زمانی با «شروعِ جمع‌آوری»: یا تغییر پیش از برگه، یا رد — هرگز پس از آن', async () => {
  const s = await shop('هم‌زمان', { flags: ['shop.orders.picking'] });
  for (let i = 0; i < 4; i++) {
    const o = await storeOrder(s);
    await move(s, o.id, 'accepted');
    const body = { fulfillment: 'delivery', address: ADDR(s.zoneA), expectedTotalMinor: SUB + 5000 };
    const [c, p] = await Promise.all([
      change(s, o.token, body),
      h.post(`/api/shop/orders/${o.id}/pick/start`, {}, { token: s.token }),
    ]);
    assert.equal(p.status, 201, JSON.stringify(p.body));
    assert.ok(c.status === 200 || (c.status === 409 && c.body.error.code === 'order_preparing'), JSON.stringify(c.body));
    const row = await orderRow(o.id);
    const ev = await changeEvents(o.id);
    const picking = await one(`SELECT MIN(seq) AS seq FROM online_order_events WHERE order_id=$1 AND kind='status' AND to_status='picking'`, [o.id]);
    assert.equal(row.status, 'picking');
    if (c.status === 200) {
      assert.deepEqual([row.fulfillment, Number(row.total_minor), ev.length], ['delivery', SUB + 5000, 1]);
      assert.ok(Number(ev[0].seq) < Number(picking.seq), 'تغییر پیش از شروعِ جمع‌آوری');
    } else {
      assert.deepEqual([row.fulfillment, Number(row.total_minor), ev.length], ['pickup', SUB, 0]);
    }
  }
});

test('فقط صاحبِ سفارش: بی تأیید ۴۰۱، شمارهٔ دیگر ۴۰۳، توکنِ دکانِ دیگر ۴۰۴', async () => {
  const s = await shop('صاحب');
  const other = await shop('دیگر');
  const o = await storeOrder(s);
  const body = { fulfillment: 'delivery', address: ADDR(s.zoneA), expectedTotalMinor: SUB + 5000 };
  const before = await orderRow(o.id);
  const anon = await change(s, o.token, body, { ticket: '' });
  assert.equal(anon.status, 401);
  assert.equal(anon.body.error.code, 'verification_required');
  const stranger = await change(s, o.token, body, { phone: '0792223344' });
  assert.equal(stranger.status, 403);
  assert.equal(stranger.body.error.code, 'not_your_order');
  const cross = await h.post(`/api/store/${other.slug}/orders/${o.token}/fulfillment`, body,
    { headers: { 'Idempotency-Key': key(), 'X-Store-Ticket': await ticketFor(other.slug, PHONE) } });
  assert.equal(cross.status, 404);
  assert.deepEqual(await orderRow(o.id), before);
  assert.equal((await changeEvents(o.id)).length, 0);
});

test('کلیدِ یکتایی: تکرار بی اثرِ دوم (حتی پس از جمع‌آوری)؛ همان کلید با بدنهٔ دیگر ۴۰۹', async () => {
  const s = await shop('تکرار');
  const o = await storeOrder(s);
  const k = key();
  const body = { fulfillment: 'delivery', address: ADDR(s.zoneA), expectedTotalMinor: SUB + 5000 };
  const a = await change(s, o.token, body, { k });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  const b = await change(s, o.token, body, { k });
  assert.equal(b.status, 200);
  assert.equal(b.body.replayed, true);
  assert.equal(b.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(b.body.changed, a.body.changed);
  const reused = await change(s, o.token, { fulfillment: 'pickup', expectedTotalMinor: SUB }, { k });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  for (const to of ['accepted', 'picking']) await move(s, o.id, to);
  const late = await change(s, o.token, body, { k });
  assert.equal(late.status, 200);
  assert.equal(late.body.replayed, true);
  assert.equal((await changeEvents(o.id)).length, 1);
  assert.equal(Number((await orderRow(o.id)).total_minor), SUB + 5000);
});
