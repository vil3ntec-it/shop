'use strict';
/**
 * دفترِ بازپرداختِ سفارشِ آنلاین و تطبیق — v3 §۳۸.۲، §۳۸.۷ (FIN-01).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `shop.orders.refunds` همان رفتارِ پیشین می‌ماند
 *      (گذارِ سادهٔ «بازپرداخت‌شده» بی مبلغ) و هیچ ردیفِ دفتری ساخته نمی‌شود.
 *   ۲) روشن: «بازپرداخت‌شده» فقط با مبلغ و از راهِ دفتر؛ جزئیِ چندباره،
 *      بستنِ جزئی با `final`، و کامل شدنِ خودکار وقتی جمع = کل.
 *   ۳) ⛔ کلیدِ یکتایی: تکرار ردیفِ دوم نمی‌سازد؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹؛
 *      دو درخواستِ هم‌زمان هرگز بیش از کلِ سفارش برنمی‌گردانند.
 *   ۴) FIN-01: دفترِ سفارش و گزارشِ تطبیق با هم می‌خوانند؛ اختلاف پنهان نمی‌ماند.
 *   ۵) شاگرد بازپرداخت نمی‌کند؛ دکانِ دیگر نه می‌بیند نه دست می‌زند.
 *   ۶) ⛔ ردیفِ دفتر هرگز ویرایش یا پاک نمی‌شود (هیچ کدی در src چنین نمی‌کند).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const R = require('../src/lib/order-refunds');

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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانی با سفارشِ روشن؛ `ledger` دفترِ بازپرداخت را هم روشن می‌کند. */
async function readyShop(name, { ledger = true } = {}) {
  const a = await shopOwner(name);
  await setFlag(a.shopId, 'shop.orders', true);
  if (ledger) await setFlag(a.shopId, R.FLAG_KEY, true);
  return a;
}

let keySeq = 0;
const freshKey = () => `refund-key-${++keySeq}-${Date.now()}`;

//  جمعِ سفارش: ۱٫۵ × ۳۳٫۳۳ ⇒ ۵۰ افغانی، ۲ × ۱۲٫۵ ⇒ ۲۵ افغانی ⇒ ۷۵۰۰ واحدِ کوچک
const TOTAL = 7500;
function sample(over = {}) {
  return {
    source: 'phone',
    fulfillment: 'pickup',
    paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    lines: [
      { productId: 'p1', name: 'برنج', unit: 'کیلو', qtyMilli: 1500, unitPriceMinor: 3333 },
      { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 },
    ],
    ...over,
  };
}

const create = (token, body) =>
  h.post('/api/shop/orders', body, { token, headers: { 'Idempotency-Key': freshKey() } });
const move = (token, id, body) => h.post(`/api/shop/orders/${id}/transition`, body, { token });
const refund = (token, id, body, key = freshKey()) =>
  h.post(`/api/shop/orders/${id}/refunds`, body, { token, headers: key ? { 'Idempotency-Key': key } : {} });
const detail = async (token, id) => (await h.get(`/api/shop/orders/${id}`, { token })).body.order;
const ledgerRows = async (id) => many('SELECT * FROM online_order_refunds WHERE order_id=$1 ORDER BY seq', [id]);

/** سفارشِ حضوری تا «درخواستِ برگشت» — یعنی پول گرفته شده و حالا برمی‌گردد. */
async function returned(token, over = {}) {
  const made = await create(token, sample(over));
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = made.body.order;
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']) {
    const r = await move(token, o.id, { to });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
  return o;
}

/* ══════════════════════════════════════════════════════════════════
   ۰) قاعدهٔ خالصِ تطبیق
   ══════════════════════════════════════════════════════════════════ */

test('issueOf: وضعیت و جمعِ دفتر با هم می‌خوانند یا کدِ اختلاف', () => {
  const cases = [
    ['return_requested', 7500, 0, null],
    ['return_requested', 7500, 2000, null],
    ['return_requested', 7500, 7500, 'refund_not_closed'],
    ['refunded_partial', 7500, 3500, null],
    ['refunded_partial', 7500, 0, 'refund_without_ledger'],
    ['refunded_partial', 7500, 7500, 'refund_partial_mismatch'],
    ['refunded_full', 7500, 7500, null],
    ['refunded_full', 7500, 0, 'refund_without_ledger'],
    ['refunded_full', 7500, 5000, 'refund_full_mismatch'],
    ['refunded_full', 7500, 9000, 'refund_exceeds_total'],
    ['delivered', 7500, 0, null],
    ['delivered', 7500, 100, 'refund_on_wrong_status'],
    ['cancelled', 7500, 0, null],
  ];
  for (const [status, total, refunded, want] of cases) {
    assert.equal(R.issueOf(status, total, refunded), want, `${status} ${total} ${refunded}`);
  }
});

test('ورودی: مبلغ عددِ صحیحِ مثبت، دلیل لازم، فقط نقد', () => {
  assert.throws(() => R.parse({ amountMinor: 0, reason: 'x' }), /بازهٔ مجاز/);
  assert.throws(() => R.parse({ amountMinor: 10.5, reason: 'x' }), /عددِ صحیح/);
  assert.throws(() => R.parse({ amountMinor: '100', reason: 'x' }), /عددِ صحیح/);
  assert.throws(() => R.parse({ amountMinor: 100, reason: '   ' }), /دلیل/);
  assert.throws(() => R.parse({ amountMinor: 100, reason: 'x', method: 'card' }), /نقدی/);
  assert.throws(() => R.parse({ amountMinor: 100, reason: 'x', final: 'yes' }), /final/);
  assert.deepEqual(R.parse({ amountMinor: 100, reason: ' خراب بود ' }),
    { amountMinor: 100, reason: 'خراب بود', method: 'cash', final: false });
});

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش — رفتارِ پیشین دست‌نخورده
   ══════════════════════════════════════════════════════════════════ */

test('خاموش: درِ دفتر بسته، گذارِ سادهٔ پیشین کار می‌کند، و اختلاف در تطبیق پیداست', async () => {
  const a = await readyShop('دفترخاموش', { ledger: false });
  const cfg = (await h.get('/api/shop/orders/config', { token: a.token })).body;
  assert.equal(cfg.refundLedger, false);

  const o = await returned(a.token);
  const r = await refund(a.token, o.id, { amountMinor: 1000, reason: 'خراب' });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'refund_ledger_disabled');
  assert.equal((await ledgerRows(o.id)).length, 0);

  //  رفتارِ پیشین: «بازپرداختِ کامل» بی مبلغ
  const legacy = await move(a.token, o.id, { to: 'refunded_full' });
  assert.equal(legacy.status, 200, JSON.stringify(legacy.body));
  const d = await detail(a.token, o.id);
  assert.equal(d.status, 'refunded_full');
  assert.equal(d.refund.refundedMinor, 0);
  assert.equal(d.refund.issue, 'refund_without_ledger', 'وضعیت می‌گوید برگشت، دفتر مبلغی ندارد — پنهان نمی‌ماند');

  const rec = (await h.get('/api/shop/orders/reconciliation', { token: a.token })).body;
  assert.deepEqual(rec.issues.map(x => [x.orderId, x.code]), [[o.id, 'refund_without_ledger']]);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) روشن — جزئی، بستن، کامل
   ══════════════════════════════════════════════════════════════════ */

test('روشن: «بازپرداخت‌شده» بی مبلغ رد؛ جزئیِ چندباره، سپس بستنِ جزئی با final', async () => {
  const a = await readyShop('جزئی');
  assert.equal((await h.get('/api/shop/orders/config', { token: a.token })).body.refundLedger, true);
  const o = await returned(a.token);

  for (const to of ['refunded_partial', 'refunded_full']) {
    const r = await move(a.token, o.id, { to });
    assert.equal(r.status, 409, to);
    assert.equal(r.body.error.code, 'refund_via_ledger');
  }

  const first = await refund(a.token, o.id, { amountMinor: 2000, reason: 'یک کیلو برنج خراب بود' });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  assert.equal(first.body.order.status, 'return_requested', 'هنوز «بازپرداخت در انتظار»');
  assert.equal(first.body.order.refund.refundedMinor, 2000);
  assert.equal(first.body.order.refund.remainingMinor, TOTAL - 2000);
  assert.equal(first.body.order.refund.netMinor, TOTAL - 2000);
  assert.equal(first.body.order.refund.issue, null);
  assert.equal(first.body.entry.seq, 1);

  const second = await refund(a.token, o.id, { amountMinor: 1500, reason: 'نوشابه باز شده بود', final: true });
  assert.equal(second.status, 201, JSON.stringify(second.body));
  assert.equal(second.body.order.status, 'refunded_partial');
  assert.equal(second.body.order.refund.refundedMinor, 3500);
  assert.deepEqual(second.body.order.refund.entries.map(e => [e.seq, e.amountMinor, e.final]), [[1, 2000, false], [2, 1500, true]]);

  //  خطِ زمانی: دو رویدادِ «پرداخت» و یک گذارِ وضعیت، با کننده و دلیل
  const ev = second.body.order.events.slice(-3);
  assert.deepEqual(ev.map(e => [e.kind, e.fromStatus, e.toStatus]), [
    ['payment', 'return_requested', 'return_requested'],
    ['payment', 'return_requested', 'return_requested'],
    ['status', 'return_requested', 'refunded_partial'],
  ]);
  assert.equal(ev[0].note, 'یک کیلو برنج خراب بود');
  assert.equal(ev[0].detail.refund.amountMinor, 2000);
  assert.equal(ev[1].actorId, a.user.id);

  //  بسته شد ⇒ ردیفِ تازه نه
  const after = await refund(a.token, o.id, { amountMinor: 100, reason: 'باز هم' });
  assert.equal(after.status, 409);
  assert.equal(after.body.error.code, 'refund_closed');

  //  سابقهٔ عملیات
  const logs = await many(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='order.refund' ORDER BY created_at`, [a.shopId]);
  assert.equal(logs.length, 2);
});

test('روشن: جمع = کلِ سفارش ⇒ «بازپرداختِ کامل» خودکار؛ بیش از مانده ⇒ ۴۰۹ با مانده', async () => {
  const a = await readyShop('کامل');
  const o = await returned(a.token);
  const over = await refund(a.token, o.id, { amountMinor: TOTAL + 1, reason: 'زیاد' });
  assert.equal(over.status, 409);
  assert.equal(over.body.error.code, 'refund_exceeds_remaining');
  assert.equal(over.body.error.remainingMinor, TOTAL);
  assert.equal((await ledgerRows(o.id)).length, 0);

  const full = await refund(a.token, o.id, { amountMinor: TOTAL, reason: 'همه را پس آورد' });
  assert.equal(full.status, 201, JSON.stringify(full.body));
  assert.equal(full.body.order.status, 'refunded_full');
  assert.equal(full.body.order.refund.remainingMinor, 0);
  assert.equal(full.body.order.refund.netMinor, 0);
  assert.equal(full.body.order.refund.issue, null);
});

test('روشن: سفارشی که برنگشته بازپرداخت نمی‌پذیرد', async () => {
  const a = await readyShop('نابرگشته');
  const o = (await create(a.token, sample())).body.order;
  const r = await refund(a.token, o.id, { amountMinor: 100, reason: 'زود' });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'not_refundable');
  assert.equal((await ledgerRows(o.id)).length, 0);
  assert.equal((await detail(a.token, o.id)).status, 'new');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) کلیدِ یکتایی و هم‌زمانی
   ══════════════════════════════════════════════════════════════════ */

test('کلیدِ یکتایی: بی کلید رد؛ تکرار ⇒ همان ردیف؛ بدنهٔ دیگر ⇒ ۴۰۹', async () => {
  const a = await readyShop('یکتا');
  const o = await returned(a.token);

  const none = await refund(a.token, o.id, { amountMinor: 500, reason: 'x' }, null);
  assert.equal(none.status, 400);
  assert.equal(none.body.error.code, 'idempotency_key_required');

  const key = freshKey();
  const body = { amountMinor: 500, reason: 'کمبود' };
  const one1 = await refund(a.token, o.id, body, key);
  assert.equal(one1.status, 201);
  const again = await refund(a.token, o.id, body, key);
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.entry.seq, 1);
  assert.equal(again.body.order.refund.refundedMinor, 500);

  const other = await refund(a.token, o.id, { amountMinor: 600, reason: 'کمبود' }, key);
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  assert.equal((await ledgerRows(o.id)).length, 1);
});

test('هم‌زمان: همان کلید ⇒ یک ردیف؛ دو کلیدِ جدا هرگز بیش از کل برنمی‌گردانند', async () => {
  const a = await readyShop('همزمان');
  const o = await returned(a.token);
  const key = freshKey();
  const same = await Promise.all([1, 2, 3].map(() => refund(a.token, o.id, { amountMinor: 1000, reason: 'دو بار کلیک' }, key)));
  assert.deepEqual(same.map(r => r.status).sort(), [200, 200, 201]);
  assert.equal((await ledgerRows(o.id)).length, 1);

  const race = await Promise.all([1, 2].map(() => refund(a.token, o.id, { amountMinor: 5000, reason: 'دو دستگاه' })));
  assert.deepEqual(race.map(r => r.status).sort(), [201, 409]);
  const rows = await ledgerRows(o.id);
  const sum = rows.reduce((s, r) => s + Number(r.amount_minor), 0);
  assert.equal(sum, 6000);
  assert.ok(sum <= TOTAL);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) FIN-01 — تطبیقِ دفتر و گزارش
   ══════════════════════════════════════════════════════════════════ */

test('FIN-01: بازپرداختِ جزئی؛ دفترِ سفارش و گزارشِ تطبیق با هم می‌خوانند؛ سفارشِ آزمایشی در جمع نیست', async () => {
  const a = await readyShop('تطبیق');
  const from = Date.now() - 1000;
  const o1 = await returned(a.token);
  const o2 = await returned(a.token);
  const t = await returned(a.token, { test: true });

  assert.equal((await refund(a.token, o1.id, { amountMinor: 2500, reason: 'جزئی', final: true })).status, 201);
  assert.equal((await refund(a.token, t.id, { amountMinor: 1000, reason: 'آزمایشی' })).status, 201);

  const rec = await h.get(`/api/shop/orders/reconciliation?from=${from}&to=${Date.now() + 1000}`, { token: a.token });
  assert.equal(rec.status, 200, JSON.stringify(rec.body));
  assert.deepEqual(rec.body.collected, { count: 2, totalMinor: 2 * TOTAL });
  assert.deepEqual(rec.body.refunds, { count: 1, totalMinor: 2500 });
  assert.equal(rec.body.netMinor, 2 * TOTAL - 2500);
  assert.deepEqual(rec.body.issues, [], 'دفتر و وضعیت هم‌خوان‌اند');

  //  جمعِ دفترِ هر سفارش = جمعِ گزارش
  const perOrder = (await detail(a.token, o1.id)).refund.refundedMinor + (await detail(a.token, o2.id)).refund.refundedMinor;
  assert.equal(perOrder, rec.body.refunds.totalMinor);

  //  اختلافِ ساختگی در دیتابیس (مثلاً دست‌کاریِ مستقیم) پنهان نمی‌ماند
  await query(`UPDATE online_orders SET status='refunded_full' WHERE id=$1`, [o2.id]);
  const rec2 = (await h.get('/api/shop/orders/reconciliation', { token: a.token })).body;
  assert.deepEqual(rec2.issues.map(x => [x.orderId, x.code]), [[o2.id, 'refund_without_ledger']]);
});

test('تطبیق: بازهٔ نادرست رد می‌شود', async () => {
  const a = await readyShop('بازه');
  for (const q of ['from=10&to=5', 'from=-1', `from=0&to=${100 * 24 * 3600 * 1000}`, 'to=abc']) {
    const r = await h.get(`/api/shop/orders/reconciliation?${q}`, { token: a.token });
    assert.equal(r.status, 400, q);
    assert.equal(r.body.error.code, 'bad_window');
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۵) نقش و جداییِ دکان‌ها
   ══════════════════════════════════════════════════════════════════ */

test('شاگرد نه بازپرداخت می‌کند نه تطبیق می‌بیند؛ مدیر هر دو', async () => {
  const a = await readyShop('نقشِ دفتر');
  const staff = await member(a.shopId, 'staff', 'شاگردِ دفتر');
  const mgr = await member(a.shopId, 'manager', 'مدیرِ دفتر');
  const o = await returned(a.token);

  const s = await refund(staff.token, o.id, { amountMinor: 100, reason: 'x' });
  assert.equal(s.status, 403);
  assert.equal(s.body.error.code, 'permission_denied');
  assert.equal((await h.get('/api/shop/orders/reconciliation', { token: staff.token })).status, 403);
  assert.equal((await ledgerRows(o.id)).length, 0);

  assert.equal((await refund(mgr.token, o.id, { amountMinor: 100, reason: 'x' })).status, 201);
  assert.equal((await h.get('/api/shop/orders/reconciliation', { token: mgr.token })).status, 200);
});

test('SEC-01: دکانِ دیگر نه بازپرداخت می‌کند نه دفتر و تطبیقِ این دکان را می‌بیند', async () => {
  const a = await readyShop('صاحبِ دفتر');
  const b = await readyShop('همسایه');
  const o = await returned(a.token);
  assert.equal((await refund(a.token, o.id, { amountMinor: 700, reason: 'x' })).status, 201);

  const r = await refund(b.token, o.id, { amountMinor: 100, reason: 'x' });
  assert.equal(r.status, 404);
  assert.equal((await h.get(`/api/shop/orders/${o.id}`, { token: b.token })).status, 404);
  const recB = (await h.get('/api/shop/orders/reconciliation', { token: b.token })).body;
  assert.equal(recB.refunds.count, 0);
  assert.deepEqual(recB.issues, []);
  assert.equal((await ledgerRows(o.id)).length, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) دفتر فقط افزوده می‌شود
   ══════════════════════════════════════════════════════════════════ */

test('⛔ هیچ کدی در src ردیفِ دفترِ بازپرداخت را ویرایش یا پاک نمی‌کند', () => {
  const root = path.join(__dirname, '..', 'src');
  const files = [];
  const walk = (d) => {
    for (const f of fs.readdirSync(d, { withFileTypes: true })) {
      if (f.isDirectory()) walk(path.join(d, f.name));
      else if (f.name.endsWith('.js')) files.push(path.join(d, f.name));
    }
  };
  walk(root);
  for (const f of files) {
    const src = fs.readFileSync(f, 'utf8');
    assert.doesNotMatch(src, /UPDATE\s+online_order_refunds|DELETE\s+FROM\s+online_order_refunds/i, f);
  }
  assert.ok(files.length > 10);
});

test('دفتر در جدولِ پایگاه هم مبلغِ مثبت و کلیدِ یکتا را می‌خواهد', async () => {
  const a = await readyShop('قیدها');
  const o = await returned(a.token);
  const base = [o.id, a.shopId, 'k-db', 'h', 100];
  await query(`INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, reason, created_at)
               VALUES ('r1',$1,$2,1,$3,$4,$5,'x',1)`, base);
  await assert.rejects(query(`INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, reason, created_at)
               VALUES ('r2',$1,$2,2,$3,$4,$5,'x',1)`, base), /unique|duplicate/i);
  await assert.rejects(query(`INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, reason, created_at)
               VALUES ('r3',$1,$2,3,'k-db-2','h',0,'x',1)`, [o.id, a.shopId]), /check/i);
  const n = await one('SELECT COUNT(*)::int AS n FROM online_order_refunds WHERE order_id=$1', [o.id]);
  assert.equal(n.n, 1);
});
