'use strict';
/**
 * CHK-21 — افزودنِ قلم به همان سفارشِ ویترین تا پیش از شروعِ جمع‌آوری (`lib/order-add-items.js`، S38).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش: درِ مشتری ۴۰۴، پیگیری بی کلیدِ `addItems`، هیچ قلم و رویدادی.
 *   ۲) پیش از جمع‌آوری (تازه و قبول‌شده): قلمِ تازه و افزودن به قلمِ هم‌قیمت، جمع با همان `lineTotalMinor`،
 *      شمارِ اقلام، نسخه، رویدادِ `items_added`؛ تکرارِ همان کلید بی اثرِ دوم؛ همان کلید با بدنهٔ دیگر ۴۰۹.
 *   ۳) پس از شروعِ جمع‌آوری ⇒ ۴۰۹ `order_preparing` و هیچ تغییری؛ پیگیری `preparing`.
 *   ۴) هم‌زمانیِ «افزودن» و «شروعِ جمع‌آوری»: برگه و سفارش همیشه با هم می‌خوانند.
 *   ۵) تخفیف: قاعدهٔ خودکار روی قلم‌های تازه با همان هسته؛ کدِ یک‌بارمصرف دوباره به کار نمی‌رود.
 *   ۶) فقط صاحبِ سفارش: بی تأیید ۴۰۱، شمارهٔ دیگر ۴۰۳، توکنِ دکانِ دیگر/خراب ۴۰۴؛ پرداخت‌شده ۴۰۹؛ کالای
 *      نبوده ۴۰۹؛ سقفِ هر سفارش روی مقدارِ کل؛ «جمعِ دیده‌شده» ِ دیگر ⇒ ۴۰۹ `price_changed`.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const S = require('../src/lib/order-states');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و سه کالا؛ `add` = پرچمِ CHK-21. */
async function shop(label, { add = true, flags = [] } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `ad-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', ...flags]) await setFlag(s.shopId, k, true);
  if (add) await setFlag(s.shopId, 'shop.orders.add_items', true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 333.33, category: 'خوراکی' }),
    op('salt', { name: 'نمک', unit: 'بسته', salePrice: 20, category: 'خوراکی' }),
  ])).status, 200);
  for (const p of ['rice', 'oil', 'salt']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
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
async function storeOrder(s, { phone = PHONE, body = {} } = {}) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }], ...body,
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token };
}

const key = () => `add-${uniq()}-key`;
async function addItems(s, token, body, { k = key(), phone = PHONE, ticket } = {}) {
  const headers = { 'Idempotency-Key': k };
  const t = ticket !== undefined ? ticket : await ticketFor(s.slug, phone);
  if (t) headers['X-Store-Ticket'] = t;
  return h.post(`/api/store/${s.slug}/orders/${token}/items`, body, { headers });
}
const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const linesOf = (id) => many('SELECT line_no, product_id, qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [id]);
const orderRow = (id) => one('SELECT status, subtotal_minor, discount_minor, delivery_fee_minor, total_minor, items_count, version FROM online_orders WHERE id=$1', [id]);
const addEvents = (id) => many(`SELECT detail, actor_type FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='items_added' ORDER BY seq`, [id]);
const move = async (s, id, to, extra = {}) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...extra }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const sumLines = (ls) => ls.reduce((a, l) => a + Number(l.line_total_minor), 0);

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: ۴۰۴، پیگیری بی `addItems` و هیچ تغییری', async () => {
  const s = await shop('خاموش', { add: false });
  const o = await storeOrder(s);
  const before = { lines: await linesOf(o.id), row: await orderRow(o.id) };
  const t0 = await track(s, o.token);
  assert.equal(t0.status, 200);
  assert.equal('addItems' in t0.body.order, false);
  assert.equal('apology' in t0.body.order, false);
  const r = await addItems(s, o.token, { lines: [{ productId: 'salt', qtyMilli: 1000 }] });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'not_found');
  assert.deepEqual(await linesOf(o.id), before.lines);
  assert.deepEqual(await orderRow(o.id), before.row);
  assert.equal((await addEvents(o.id)).length, 0);
  //  پیگیری بیت‌به‌بیت همان (پیش و پس از درخواستِ رد‌شده)
  assert.deepEqual((await track(s, o.token)).body, t0.body);
});

test('پیش از جمع‌آوری: قلمِ تازه و افزودن به قلمِ هم‌قیمت؛ جمع با همان lineTotalMinor؛ تکرار بی اثرِ دوم', async () => {
  const s = await shop('افزودن');
  const o = await storeOrder(s);
  const v0 = (await orderRow(o.id)).version;
  const t0 = await track(s, o.token);
  assert.deepEqual(t0.body.order.addItems, { allowed: true, preparing: false });

  const k = key();
  const body = { lines: [{ productId: 'salt', qtyMilli: 2500, note: 'کوچک' }, { productId: 'rice', qtyMilli: 1500 }] };
  const r = await addItems(s, o.token, body, { k });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.replayed, false);
  const ls = await linesOf(o.id);
  assert.deepEqual(ls.map((l) => [l.line_no, l.product_id, Number(l.qty_milli)]), [[1, 'rice', 4500], [2, 'oil', 1000], [3, 'salt', 2500]]);
  assert.equal(Number(ls[0].line_total_minor), S.lineTotalMinor(4500, 12000));
  assert.equal(Number(ls[2].line_total_minor), S.lineTotalMinor(2500, 2000));
  const row = await orderRow(o.id);
  assert.equal(Number(row.subtotal_minor), sumLines(ls));
  assert.equal(Number(row.total_minor), sumLines(ls) - Number(row.discount_minor) + Number(row.delivery_fee_minor));
  assert.equal(Number(row.items_count), 3);
  assert.ok(Number(row.version) > Number(v0));
  assert.equal(row.status, 'new', 'وضعیت و زمانِ تحویل دست نمی‌خورد');
  //  پاسخ: سفارشِ تازه و خلاصهٔ افزوده
  assert.equal(r.body.order.totalMinor, Number(row.total_minor));
  assert.equal(r.body.order.lines.length, 3);
  assert.deepEqual(r.body.added.lines.map((x) => [x.lineNo, x.merged, x.addedQtyMilli]), [[3, false, 2500], [1, true, 1500]]);
  assert.equal(r.body.added.differenceMinor, Number(row.total_minor) - t0.body.order.totalMinor);
  const evs = await addEvents(o.id);
  assert.equal(evs.length, 1);
  assert.equal(evs[0].actor_type, 'customer');

  //  تکرارِ همان کلید ⇒ همان نتیجه، بی قلمِ دوم
  const again = await addItems(s, o.token, body, { k });
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(await linesOf(o.id), ls);
  assert.equal((await addEvents(o.id)).length, 1);
  //  همان کلید با بدنهٔ دیگر ⇒ ۴۰۹
  const reused = await addItems(s, o.token, { lines: [{ productId: 'salt', qtyMilli: 1000 }] }, { k });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  //  بی کلید ⇒ ۴۰۰
  const nokey = await h.post(`/api/store/${s.slug}/orders/${o.token}/items`, body, { headers: { 'X-Store-Ticket': await ticketFor(s.slug, PHONE) } });
  assert.equal(nokey.status, 400);

  //  قبول‌شده هم (هنوز جمع‌آوری شروع نشده)
  await move(s, o.id, 'accepted');
  const r2 = await addItems(s, o.token, { lines: [{ productId: 'oil', qtyMilli: 2000 }] });
  assert.equal(r2.status, 201, JSON.stringify(r2.body));
  assert.equal(Number((await linesOf(o.id))[1].qty_milli), 3000);
  assert.equal(Number((await orderRow(o.id)).subtotal_minor), sumLines(await linesOf(o.id)));
});

//  ⛔ «همه روشن» (`web-store-allflags.mjs`): پاسخِ این کارِ مشتری فقط `sf.track` بود و صفحه (`renderOrder(r.order)`) هر کلیدِ نیامده را
//  پنهان می‌کرد ⇒ کارتِ گفت‌وگو، دستورِ تحویل، زمان و روش تا پیگیریِ بعدی می‌رفتند. حالا همان نمای پیگیری (`lib/store-order-view.js`).
test('با پرچم‌های دیگرِ پیگیری: پاسخِ «افزودن» همان نمای پیگیری است (گفت‌وگو، دستورِ تحویل، زمان، …)', async () => {
  const s = await shop('همه‌روشن', { flags: ['shop.chat', 'shop.orders.delivery_prefs', 'shop.storefront.eta', 'shop.storefront.delivery_method', 'shop.orders.issue_reports', 'shop.orders.reviews', 'shop.orders.refund_tracking'] });
  const o = await storeOrder(s);
  const r = await addItems(s, o.token, { lines: [{ productId: 'salt', qtyMilli: 1000 }] });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const tr = (await track(s, o.token)).body.order;
  assert.equal(r.body.order.chat, true, 'کارتِ گفت‌وگو با پاسخِ «افزودن» نمی‌رود');
  assert.deepEqual(Object.keys(r.body.order).sort(), Object.keys(tr).sort());
  assert.deepEqual(r.body.order.addItems, tr.addItems);
});

test('پس از شروعِ جمع‌آوری ⇒ ۴۰۹ order_preparing، هیچ تغییری؛ پیگیری «در حال آماده‌سازی»', async () => {
  const s = await shop('جمع‌آوری', { flags: ['shop.orders.picking'] });
  const o = await storeOrder(s);
  await move(s, o.id, 'accepted');
  const st = await h.post(`/api/shop/orders/${o.id}/pick/start`, {}, { token: s.token });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  const before = { lines: await linesOf(o.id), row: await orderRow(o.id) };
  const r = await addItems(s, o.token, { lines: [{ productId: 'salt', qtyMilli: 1000 }] });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'order_preparing');
  assert.deepEqual(await linesOf(o.id), before.lines);
  assert.deepEqual(await orderRow(o.id), before.row);
  const t = await track(s, o.token);
  assert.deepEqual(t.body.order.addItems, { allowed: false, preparing: true });

  //  بسته‌شده (وضعیتِ پایانی) هم نه
  const o2 = await storeOrder(s);
  await move(s, o2.id, 'accepted');
  await move(s, o2.id, 'cancelled', { reason: 'out_of_stock' });
  const r2 = await addItems(s, o2.token, { lines: [{ productId: 'salt', qtyMilli: 1000 }] });
  assert.equal(r2.status, 409);
  assert.equal(r2.body.error.code, 'order_preparing');
});

test('هم‌زمانیِ «افزودن» و «شروعِ جمع‌آوری»: برگه همیشه همان قلم‌های سفارش است', async () => {
  const s = await shop('هم‌زمان', { flags: ['shop.orders.picking'] });
  for (let i = 0; i < 4; i++) {
    const o = await storeOrder(s);
    await move(s, o.id, 'accepted');
    const [a, p] = await Promise.all([
      addItems(s, o.token, { lines: [{ productId: 'salt', qtyMilli: 1000 }] }),
      h.post(`/api/shop/orders/${o.id}/pick/start`, {}, { token: s.token }),
    ]);
    assert.equal(p.status, 201, JSON.stringify(p.body));
    assert.ok(a.status === 201 || (a.status === 409 && a.body.error.code === 'order_preparing'), JSON.stringify(a.body));
    const ls = await linesOf(o.id);
    const picks = await many('SELECT line_no, ordered_qty_milli FROM order_pick_lines WHERE order_id=$1 ORDER BY line_no', [o.id]);
    assert.deepEqual(picks.map((x) => [x.line_no, Number(x.ordered_qty_milli)]), ls.map((l) => [l.line_no, Number(l.qty_milli)]),
      'برگهٔ جمع‌آوری = قلم‌های سفارش');
    assert.equal(ls.length, a.status === 201 ? 3 : 2);
    const row = await orderRow(o.id);
    assert.equal(Number(row.subtotal_minor), sumLines(ls));
    assert.equal(row.status, 'picking');
  }
});

test('تخفیف: قاعدهٔ خودکار روی قلم‌های تازه با همان هسته؛ کدِ یک‌بارمصرف دوباره به کار نمی‌رود', async () => {
  const s = await shop('تخفیف', { flags: ['shop.storefront.promos', 'shop.promo_uses'] });
  const rule = async (id, data) => {
    const r = await h.put(`/api/shop/promos/rules/${id}`, { rule: { name: id, stack: 'combine', active: true, ...data } }, { token: s.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  };
  await rule('salt10', { kind: 'percent', bp: 1000, channels: ['online'], scope: { products: ['salt'] } });
  await rule('once', { kind: 'percent', bp: 1000, channels: ['online'], code: 'ONCE10', maxUses: 1 });
  const o = await storeOrder(s, { body: { promoCode: 'ONCE10' } });
  const r0 = await orderRow(o.id);
  assert.ok(Number(r0.discount_minor) > 0, 'کد در ثبت اعمال شد');
  const uses = async () => Number((await one(`SELECT count(*)::int AS n FROM shop_promo_redemptions WHERE shop_id=$1 AND promo_id='once'`, [s.shopId])).n);
  assert.equal(await uses(), 1);

  const r = await addItems(s, o.token, { lines: [{ productId: 'salt', qtyMilli: 5000 }, { productId: 'oil', qtyMilli: 1000 }] });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const row = await orderRow(o.id);
  const ls = await linesOf(o.id);
  //  قاعدهٔ خودکارِ نمک: ۱۰٪ ِ ۵ بسته × ۲۰ = ۱۰ افغانی (پلهٔ ۱۰۰)؛ کدِ ONCE10 روی قلم‌های تازه نه
  assert.equal(Number(row.discount_minor), Number(r0.discount_minor) + 1000);
  assert.equal(Number(row.subtotal_minor), sumLines(ls));
  assert.equal(Number(row.total_minor), sumLines(ls) - Number(row.discount_minor));
  assert.equal(await uses(), 1, 'مصرفِ تازه‌ای از کدِ یک‌بارمصرف ساخته نشد');

  //  «جمعِ دیده‌شده» ِ دیگر ⇒ ۴۰۹ با برگهٔ تازه و هیچ تغییری
  const bad = await addItems(s, o.token, { lines: [{ productId: 'rice', qtyMilli: 1000 }], expectedTotalMinor: 1 });
  assert.equal(bad.status, 409);
  assert.equal(bad.body.error.code, 'price_changed');
  assert.ok(bad.body.error.data?.quote || bad.body.quote || bad.body.error.quote, JSON.stringify(bad.body));
  assert.deepEqual(await linesOf(o.id), ls);
  const ok = await addItems(s, o.token, {
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
    expectedTotalMinor: Number(row.total_minor) + S.lineTotalMinor(1000, 12000),
  });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
});

test('فقط صاحبِ سفارش: بی تأیید ۴۰۱، شمارهٔ دیگر ۴۰۳، توکنِ دیگر ۴۰۴؛ پرداخت‌شده، نبوده و سقف', async () => {
  const s = await shop('صاحب');
  const other = await shop('دیگر');
  const o = await storeOrder(s);
  const before = { lines: await linesOf(o.id), row: await orderRow(o.id) };
  const body = { lines: [{ productId: 'salt', qtyMilli: 1000 }] };

  const anon = await addItems(s, o.token, body, { ticket: '' });
  assert.equal(anon.status, 401);
  assert.equal(anon.body.error.code, 'verification_required');
  const stranger = await addItems(s, o.token, body, { phone: '0795556677' });
  assert.equal(stranger.status, 403);
  assert.equal(stranger.body.error.code, 'not_your_order');
  //  توکنِ همین سفارش روی ویترینِ دکانِ دیگر، و توکنِ خراب ⇒ ۴۰۴
  const cross = await h.post(`/api/store/${other.slug}/orders/${o.token}/items`, body,
    { headers: { 'Idempotency-Key': key(), 'X-Store-Ticket': await ticketFor(other.slug, PHONE) } });
  assert.equal(cross.status, 404);
  const broken = await addItems(s, h.tampered(o.token, 'zz'), body);
  assert.equal(broken.status, 404);
  assert.deepEqual(await linesOf(o.id), before.lines);
  assert.deepEqual(await orderRow(o.id), before.row);

  //  کالای نبوده ⇒ ۴۰۹ product_unavailable؛ سقفِ هر سفارش روی مقدارِ کلِ همان کالا
  const gone = await addItems(s, o.token, { lines: [{ productId: 'nope', qtyMilli: 1000 }] });
  assert.equal(gone.status, 409);
  assert.equal(gone.body.error.code, 'product_unavailable');
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true, maxQtyMilli: 4000 }, { token: s.token })).status, 200);
  const over = await addItems(s, o.token, { lines: [{ productId: 'rice', qtyMilli: 1500 }] });
  assert.equal(over.status, 400);
  assert.equal(over.body.error.code, 'qty_over_limit');

  //  پرداخت‌شده ⇒ ۴۰۹ (همان دروازهٔ ORD-14)
  await query(`UPDATE online_orders SET payment_status='paid' WHERE id=$1`, [o.id]);
  const paid = await addItems(s, o.token, body);
  assert.equal(paid.status, 409);
  assert.equal(paid.body.error.code, 'amend_paid_order');
  assert.deepEqual(await linesOf(o.id), before.lines);
});
