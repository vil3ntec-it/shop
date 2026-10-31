'use strict';
/**
 * ORD-14 — ویرایشِ سفارش پس از ثبت، با رضایتِ ثبت‌شدهٔ مشتری و تسویهٔ تفاوت (`lib/order-amend.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: درِ کارمند ۴۰۳، درِ مشتری ۴۰۴، `config`/جزئیات/پیگیری بی کلیدِ تازه، هیچ رویداد.
 *   ۲) سفارشِ کارمند: بی رضایتِ ثبت‌شده نه؛ با رضایت همان دم — مقدار، مبلغِ ردیف (همان گردکردن)،
 *      برداشتنِ قلم، جمع و شمارِ اقلام؛ تکرارِ همان کلید بی اثرِ دوم؛ همان کلید با بدنهٔ دیگر ۴۰۹.
 *   ۳) سفارشِ ویترین: پیشنهاد تا پذیرشِ خودِ مشتری هیچ چیزی را عوض نمی‌کند؛ مشتری فقط نام، مقدار و
 *      مبلغ را می‌بیند؛ پذیرش ⇒ اعمال؛ رد ⇒ همان سفارش؛ یک پیشنهادِ باز.
 *   ۴) مهلت، سفارشِ جلو رفته (کهنه)، پس گرفتن، پرداخت‌شده، تخفیفِ بیش از جمع، برداشتنِ همه.
 *   ۵) جداسازی: توکنِ سفارشِ دیگر و دکانِ دیگر هیچ پیشنهادی را نمی‌بندند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const S = require('../src/lib/order-states');

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

/** دکانِ روشن با ویترین و دو کالای منتشرشده؛ `amend` = پرچمِ ویرایش. */
async function shop(label, { amend = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `a-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(s.shopId, k, true);
  if (amend) await setFlag(s.shopId, 'shop.orders.amend', true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 333.33, category: 'خوراکی' }),
  ])).status, 200);
  for (const p of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

const key = () => `amend-${uniq()}-key`;
async function staffOrder(s, over = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'احمد', phone: '0791234567' },
    lines: [
      { name: 'برنج', qtyMilli: 2000, unitPriceMinor: 12000 },
      { name: 'روغن', qtyMilli: 1000, unitPriceMinor: 33333 },
      { name: 'نمک', qtyMilli: 1000, unitPriceMinor: 2000 },
    ], ...over,
  }, { token: s.token, headers: { 'Idempotency-Key': key() } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
async function storeOrder(s, phone = '0791112233') {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token };
}

const propose = (s, id, body, k = key()) => h.post(`/api/shop/orders/${id}/amendments`, body, { token: s.token, headers: k ? { 'Idempotency-Key': k } : {} });
const decide = (s, token, amendId, what) => h.post(`/api/store/${s.slug}/orders/${token}/amendments/${amendId}/${what}`, {});
const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const linesOf = (id) => many('SELECT line_no, qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [id]);
const orderRow = (id) => one('SELECT status, subtotal_minor, total_minor, items_count, version FROM online_orders WHERE id=$1', [id]);
const amendEvents = (id) => many(
  `SELECT detail, actor_type, note FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type' LIKE 'amend_%' ORDER BY seq`, [id]);
const move = async (s, id, to) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: درِ کارمند ۴۰۳، درِ مشتری ۴۰۴، بی کلیدِ تازه و بی رویداد', async () => {
  const s = await shop('خاموش', { amend: false });
  const o = await staffOrder(s);
  const r = await propose(s, o.id, { changes: [{ lineNo: 1, qtyMilli: 1000 }], consent: { via: 'phone' } });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'order_amend_disabled');
  const cfg = await h.get('/api/shop/orders/config', { token: s.token });
  assert.equal('amend' in cfg.body, false);
  const d = await h.get(`/api/shop/orders/${o.id}`, { token: s.token });
  assert.equal('amendment' in d.body.order, false);
  const so = await storeOrder(s);
  const t = await track(s, so.token);
  assert.equal(t.status, 200);
  assert.equal('amendment' in t.body.order, false, 'پیگیری همان شکلِ پیشین');
  const c = await decide(s, so.token, `am_${'0'.repeat(26)}`, 'accept');
  assert.equal(c.status, 404);
  assert.equal((await amendEvents(o.id)).length + (await amendEvents(so.id)).length, 0);
});

test('سفارشِ کارمند: رضایتِ ثبت‌شده لازم؛ با آن همان دم — مقدار، مبلغ، برداشتن، جمع؛ تکرار بی اثرِ دوم', async () => {
  const s = await shop('کارمند');
  const cfg = await h.get('/api/shop/orders/config', { token: s.token });
  assert.deepEqual(cfg.body.amend, { statuses: ['new', 'accepted'], reasons: ['out_of_stock', 'customer_request', 'price_error', 'other'], consentVia: ['phone', 'in_person', 'telegram', 'whatsapp'], ttlMinutes: 30 });
  const o = await staffOrder(s);
  assert.equal(o.totalMinor, 24000 + 33300 + 2000);
  const body = { changes: [{ lineNo: 1, qtyMilli: 1500 }, { lineNo: 3, qtyMilli: 0 }], reason: 'out_of_stock', note: 'نمک تمام شد', consent: { via: 'phone', note: 'تلفنی پذیرفت' } };

  const none = await propose(s, o.id, { changes: body.changes });
  assert.equal(none.status, 400);
  assert.equal(none.body.error.code, 'consent_required');
  assert.equal((await propose(s, o.id, body, null)).body.error.code, 'idempotency_key_required');
  for (const [bad, code] of [
    [{ ...body, changes: [{ lineNo: 9, qtyMilli: 1 }] }, 'bad_changes'],
    [{ ...body, changes: [{ lineNo: 1, qtyMilli: 2000 }] }, 'bad_changes'],
    [{ ...body, changes: [{ lineNo: 1, qtyMilli: -1 }] }, 'bad_changes'],
    [{ ...body, changes: [{ lineNo: 1, qtyMilli: 1.5 }] }, 'bad_changes'],
    [{ ...body, changes: [{ lineNo: 1, qtyMilli: 1 }, { lineNo: 1, qtyMilli: 2 }] }, 'bad_changes'],
    [{ ...body, consent: { via: 'telepathy' } }, 'bad_consent'],
    [{ ...body, reason: 'other', note: '' }, 'note_required'],
  ]) {
    const r = await propose(s, o.id, bad);
    assert.equal(r.status, 400, JSON.stringify(bad));
    assert.equal(r.body.error.code, code);
  }
  const all = await propose(s, o.id, { ...body, changes: [1, 2, 3].map((n) => ({ lineNo: n, qtyMilli: 0 })) });
  assert.equal(all.status, 409);
  assert.equal(all.body.error.code, 'amend_empty');
  assert.equal((await amendEvents(o.id)).length, 0, 'هیچ درخواستِ ردشده‌ای رویداد نساخت');

  const k = key();
  const r = await propose(s, o.id, body, k);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.amendment.status, 'applied');
  assert.deepEqual(r.body.amendment.consent, { via: 'phone', note: 'تلفنی پذیرفت', by: 'staff' });
  const newLine1 = S.lineTotalMinor(1500, 12000);
  assert.equal(newLine1, 18000);
  assert.equal(r.body.amendment.prevTotalMinor, 59300);
  assert.equal(r.body.amendment.totalMinor, newLine1 + 33300);
  assert.equal(r.body.amendment.differenceMinor, newLine1 + 33300 - 59300);
  assert.deepEqual((await linesOf(o.id)).map((l) => [Number(l.line_no), Number(l.qty_milli), Number(l.line_total_minor)]),
    [[1, 1500, 18000], [2, 1000, 33300]]);
  const row = await orderRow(o.id);
  assert.deepEqual([Number(row.subtotal_minor), Number(row.total_minor), Number(row.items_count)], [51300, 51300, 2]);
  assert.equal(r.body.order.totalMinor, 51300);
  assert.equal(r.body.order.lines.length, 2);

  //  تکرارِ همان کلید ⇒ همان، بی رویدادِ دوم؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹
  const again = await propose(s, o.id, body, k);
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.amendment.id, r.body.amendment.id);
  const reused = await propose(s, o.id, { ...body, note: 'دیگر' }, k);
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');
  const evs = await amendEvents(o.id);
  assert.equal(evs.length, 1);
  assert.equal(evs[0].note, 'نمک تمام شد');
  assert.equal(Number((await orderRow(o.id)).total_minor), 51300);

  //  جزئیاتِ کارمند آخرین ویرایش را دارد
  const d = await h.get(`/api/shop/orders/${o.id}`, { token: s.token });
  assert.equal(d.body.order.amendment.id, r.body.amendment.id);

  //  پس از «شروعِ جمع‌آوری» دیگر نه
  await move(s, o.id, 'accepted');
  assert.equal((await propose(s, o.id, { ...body, changes: [{ lineNo: 1, qtyMilli: 1000 }] })).status, 201, 'قبول‌شده هنوز ویرایش‌پذیر');
  await move(s, o.id, 'picking');
  const late = await propose(s, o.id, { ...body, changes: [{ lineNo: 1, qtyMilli: 500 }] });
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'amend_not_allowed');
  assert.equal(late.body.error.current, 'picking');
});

test('تخفیفِ بیش از جمعِ تازه و سفارشِ پرداخت‌شده ⇒ ۴۰۹ بی تغییر', async () => {
  const s = await shop('تخفیف');
  const o = await staffOrder(s, { discountMinor: 50000 });
  const r = await propose(s, o.id, { changes: [{ lineNo: 2, qtyMilli: 0 }], consent: { via: 'in_person' } });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'amend_discount_exceeds');
  const so = await storeOrder(s);
  await query(`UPDATE online_orders SET payment_status='paid', payment_method='online', cod_status='' WHERE id=$1`, [so.id]);
  const p = await propose(s, so.id, { changes: [{ lineNo: 1, qtyMilli: 1000 }] });
  assert.equal(p.status, 409);
  assert.equal(p.body.error.code, 'amend_paid_order');
  assert.equal((await amendEvents(o.id)).length + (await amendEvents(so.id)).length, 0);
});

test('سفارشِ ویترین: پیشنهاد تا پذیرشِ مشتری چیزی را عوض نمی‌کند؛ مشتری فقط نام و مبلغ را می‌بیند؛ پذیرش ⇒ اعمال', async () => {
  const s = await shop('ویترین');
  const so = await storeOrder(s);
  const before = await linesOf(so.id);
  const totalBefore = Number((await orderRow(so.id)).total_minor);
  assert.equal(totalBefore, 36000 + S.lineTotalMinor(1000, 33333));

  const viaStaff = await propose(s, so.id, { changes: [{ lineNo: 1, qtyMilli: 2000 }], consent: { via: 'phone' } });
  assert.equal(viaStaff.status, 400);
  assert.equal(viaStaff.body.error.code, 'consent_via_link', 'رضایتِ مشتریِ ویترین را کارمند ثبت نمی‌کند');

  const r = await propose(s, so.id, { changes: [{ lineNo: 1, qtyMilli: 2000 }], reason: 'out_of_stock', note: 'یادداشتِ داخلیِ کارمند' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const a = r.body.amendment;
  assert.equal(a.status, 'pending');
  assert.ok(a.expiresAt > Date.now() + 29 * 60_000 && a.expiresAt <= Date.now() + 30 * 60_000);
  assert.deepEqual(await linesOf(so.id), before, 'تا پذیرش هیچ قلمی عوض نشد');
  assert.equal(Number((await orderRow(so.id)).total_minor), totalBefore);

  const dup = await propose(s, so.id, { changes: [{ lineNo: 2, qtyMilli: 2000 }] });
  assert.equal(dup.status, 409);
  assert.equal(dup.body.error.code, 'amend_pending');

  const t = await track(s, so.token);
  const m = t.body.order.amendment;
  assert.deepEqual(m, {
    id: a.id, status: 'pending', reason: 'out_of_stock',
    changes: [{ name: 'برنج', unit: 'کیلو', fromQtyMilli: 3000, toQtyMilli: 2000, fromLineTotalMinor: 36000, toLineTotalMinor: 24000 }],
    prevTotalMinor: totalBefore, totalMinor: totalBefore - 12000, differenceMinor: -12000,
    expiresAt: a.expiresAt, createdAt: m.createdAt,
  });
  assert.doesNotMatch(JSON.stringify(t.body.order), /یادداشتِ داخلی|"consent"|actor/, 'نه توضیحِ کارمند، نه کننده');

  const ok = await decide(s, so.token, a.id, 'accept');
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.status, 'applied');
  assert.equal(ok.body.order.totalMinor, totalBefore - 12000);
  assert.equal(ok.body.order.amendment.status, 'applied');
  assert.equal(Number((await linesOf(so.id))[0].qty_milli), 2000);
  //  دوباره پذیرفتن ⇒ همان؛ رد کردنِ پس از پذیرش ⇒ ۴۰۹
  const again = await decide(s, so.token, a.id, 'accept');
  assert.equal(again.status, 200);
  assert.equal(again.body.unchanged, true);
  const rej = await decide(s, so.token, a.id, 'reject');
  assert.equal(rej.status, 409);
  assert.equal(rej.body.error.code, 'amend_closed');
  const evs = await amendEvents(so.id);
  assert.deepEqual(evs.map((e) => [e.detail.type, e.actor_type]), [['amend_proposed', 'user'], ['amend_closed', 'customer']]);
  assert.deepEqual(evs[1].detail.consent, { via: 'link', by: 'customer' });
});

test('سفارشِ ویترین: رد ⇒ همان سفارش؛ پس گرفتنِ کارمند ⇒ مشتری دیگر نمی‌پذیرد', async () => {
  const s = await shop('رد');
  const so = await storeOrder(s);
  const before = await linesOf(so.id);
  const a = (await propose(s, so.id, { changes: [{ lineNo: 2, qtyMilli: 0 }] })).body.amendment;
  const rej = await decide(s, so.token, a.id, 'reject');
  assert.equal(rej.status, 200);
  assert.equal(rej.body.status, 'rejected');
  assert.deepEqual(await linesOf(so.id), before);

  const b = (await propose(s, so.id, { changes: [{ lineNo: 2, qtyMilli: 2000 }] })).body.amendment;
  assert.ok(b && b.id !== a.id, 'پس از رد، پیشنهادِ تازه ممکن است');
  const w = await h.post(`/api/shop/orders/${so.id}/amendments/${b.id}/withdraw`, {}, { token: s.token });
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.status, 'withdrawn');
  const late = await decide(s, so.token, b.id, 'accept');
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'amend_closed');
  assert.deepEqual(await linesOf(so.id), before);
});

test('مهلت گذشته ⇒ «منقضی» یک بار ثبت و هیچ تغییری؛ سفارشِ جلو رفته ⇒ «کهنه»', async () => {
  const s = await shop('مهلت');
  const so = await storeOrder(s);
  const before = await linesOf(so.id);
  const a = (await propose(s, so.id, { changes: [{ lineNo: 1, qtyMilli: 1000 }] })).body.amendment;
  await query(
    `UPDATE online_order_events SET detail = jsonb_set(detail, '{expiresAt}', to_jsonb($2::bigint))
      WHERE order_id=$1 AND detail->>'type'='amend_proposed'`, [so.id, Date.now() - 1000]);
  assert.equal((await track(s, so.token)).body.order.amendment.status, 'expired');
  for (let i = 0; i < 2; i++) {
    const r = await decide(s, so.token, a.id, 'accept');
    assert.equal(r.status, 409);
    assert.equal(r.body.error.code, 'amend_expired');
  }
  assert.deepEqual(await linesOf(so.id), before);
  assert.equal((await amendEvents(so.id)).filter((e) => e.detail.outcome === 'expired').length, 1, 'یک بار');

  //  کهنه: سفارش به «جمع‌آوری» رفت ⇒ پذیرفتن هیچ چیزی را عوض نمی‌کند
  const b = (await propose(s, so.id, { changes: [{ lineNo: 1, qtyMilli: 1000 }] })).body.amendment;
  await move(s, so.id, 'accepted');
  await move(s, so.id, 'picking');
  const r = await decide(s, so.token, b.id, 'accept');
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'order_changed');
  assert.equal(r.body.order.amendment.status, 'stale');
  assert.deepEqual(await linesOf(so.id), before);
});

test('جداسازی: توکنِ سفارشِ دیگر، دکانِ دیگر و شناسهٔ ساختگی هیچ پیشنهادی را نمی‌بندند', async () => {
  const s = await shop('الف');
  const x = await storeOrder(s, '0791110001');
  const y = await storeOrder(s, '0791110002');
  const a = (await propose(s, x.id, { changes: [{ lineNo: 1, qtyMilli: 1000 }] })).body.amendment;
  const wrongOrder = await decide(s, y.token, a.id, 'accept');
  assert.equal(wrongOrder.status, 404);
  assert.equal(wrongOrder.body.error.code, 'amend_not_found');
  const b = await shop('ب');
  assert.equal((await h.post(`/api/store/${b.slug}/orders/${x.token}/amendments/${a.id}/accept`, {})).status, 404);
  assert.equal((await propose(b, x.id, { changes: [{ lineNo: 1, qtyMilli: 500 }] })).status, 404, 'دکانِ «ب» سفارشِ «الف» را نمی‌بیند');
  assert.equal((await decide(s, `${x.token}x`, a.id, 'accept')).status, 404);
  assert.equal((await decide(s, x.token, 'am_bad', 'accept')).status, 404);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM online_order_events WHERE order_id=$1 AND detail->>'type'='amend_closed'`, [x.id])).n, 0);
  assert.equal(Number((await linesOf(x.id))[0].qty_milli), 3000);
});
