'use strict';
/**
 * تطبیقِ فروشِ آنلاین (E2E-ADM-15) — لبه‌ها، پس از بازبینیِ مستقلِ #411.
 * هر مورد وارونهٔ یک رفتارِ نادرستِ پیش از درمان است (آزمونِ اثباتِ بازبین):
 *
 *   ۱) نقدِ «دریافتِ حضوری» و «شرکتِ بیرونی» نقدِ پیک نیست (فقط shop_courier، هم‌سو با DLV-11).
 *   ۲) دکانی که در بازه کاری ندارد ولی نقدِ تسویه‌نشده یا اختلافِ باز دارد، در فهرست هست.
 *   ۳) اختلافِ سفارشِ آزمایشی نه در صف است نه در جمع؛ دکانِ فقط‌آزمایشی نیست.
 *   ۴) تسویهٔ سراسر آزمایشی بیرون؛ آمیخته بی سهمِ آزمایشی؛ اضافه (excess) هم.
 *   ۵) مرزِ بازه برای پیک و تسویه: آغاز شامل، پایان بیرون.
 *   ۶) رد، ردِ خودکار، لغوِ مشتری و لغو همه «لغو و رد»اند.
 *   ۷) بیش از ۲۰۰ دکان: جمعِ کل از همه، فهرست به اهمیت، «۲۰۰ از M».
 *   ۸) reportMany همان report است، دکان به دکان.
 *   ۹) دیدنِ گزارش سابقه دارد.
 *  ۱۰) بازهٔ روزانه در Asia/Kabul؛ روزِ «تا» شامل است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const R = require('../src/lib/order-refunds');
const health = require('../src/lib/system-health');

const HOUR = 3600_000;
const DAY = 24 * HOUR;
let root = '';
let seq = 0;
const key = (p) => `${p}-${++seq}-${Date.now()}`;
const recon = (q = '') => h.get(`/api/admin/system/reconciliation${q}`, { token: root });
const rowOf = (b, id) => b.shops.find((s) => s.shopId === id);

async function shop(name, { delivery = false } = {}) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const k of ['shop.orders', R.FLAG_KEY, ...(delivery ? ['shop.delivery'] : [])]) {
    const r = await h.put('/api/admin/pump/live-config', { scope: made.body.shop.id, key: k, value: true }, { token: root });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { token: u.accessToken, shopId: made.body.shop.id };
}
const body = (over = {}) => ({
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
  lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 5000 }], ...over,
});
const deliveryBody = (over = {}) => body({
  fulfillment: 'delivery', address: { landmark: 'نزدیکِ مسجد', text: 'کوچهٔ سوم', lat: 34.5, lng: 69.1 }, ...over,
});
async function order(s, b = body()) {
  const r = await h.post('/api/shop/orders', b, { token: s.token, headers: { 'Idempotency-Key': key('o') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}
async function move(s, id, ...tos) {
  for (const to of tos) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}
const assign = (s, id, b) => h.post(`/api/shop/delivery/orders/${id}/assign`, b, { token: s.token, headers: { 'Idempotency-Key': key('a') } });
const staffStatus = (s, id, b) => h.post(`/api/shop/delivery/assignments/${id}/status`, b, { token: s.token, headers: { 'Idempotency-Key': key('st') } });

/** مأموریتِ تحویل‌شدهٔ کاشته (مرجعِ دقیق) */
async function delivered(shopId, orderId, { cash, at, settlement = '', mode = 'shop_courier', fare = 0 }) {
  await query(
    `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, courier_id, status, expected_collect_minor,
        cash_collected_minor, fare_minor, delivered_at, settlement_id, created_at, updated_at)
     VALUES ($1,$2,$3,1,$4,'cour_e','delivered',$5,$5,$6,$7,$8,$7,$7)`,
    [newId('dla'), shopId, orderId, mode, cash, fare, at, settlement]
  );
}
let settleNo = 0;
async function settlement(shopId, id, { due, received, at, fareFromCash = false }) {
  const diff = received - due;
  await query(
    `INSERT INTO courier_settlements (id, shop_id, courier_id, settle_no, client_key, request_hash, assignments_count,
        expected_cash_minor, collected_cash_minor, collection_diff_minor, fare_total_minor, fare_paid_from_cash,
        handover_due_minor, handed_over_minor, received_minor, shortage_minor, excess_minor, confirmed_by, created_at)
     VALUES ($1,$2,'cour_e',$9,$1,'h',1,$3,$3,0,0,$4,$3,$5,$5,$6,$7,'o',$8)`,
    [id, shopId, due, fareFromCash, received, Math.max(0, -diff), Math.max(0, diff), at, ++settleNo]
  );
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

test('۱) نقدِ دریافتِ حضوری و شرکتِ بیرونی نقدِ پیک نیست — هم‌سو با DLV-11', async () => {
  const s = await shop('حضوری', { delivery: true });
  const p = await order(s);
  await move(s, p.id, 'accepted', 'picking', 'packed', 'ready_for_pickup');
  const pa = await assign(s, p.id, { mode: 'pickup' });
  assert.equal(pa.status, 201, JSON.stringify(pa.body));
  assert.equal((await staffStatus(s, pa.body.assignment.id, { to: 'delivered', code: pa.body.code, cashCollectedMinor: p.totalMinor })).status, 200);
  const x = await order(s, deliveryBody());
  await move(s, x.id, 'accepted', 'picking', 'packed');
  const xa = await assign(s, x.id, { mode: 'external', externalName: 'شرکتِ پیکِ شهر' });
  assert.equal(xa.status, 201, JSON.stringify(xa.body));
  assert.equal((await staffStatus(s, xa.body.assignment.id, { to: 'picked_up' })).status, 200);
  assert.equal((await staffStatus(s, xa.body.assignment.id, { to: 'delivered', cashCollectedMinor: x.totalMinor })).status, 200);

  const r = rowOf((await recon()).body, s.shopId);
  const dlv11 = (await h.get('/api/shop/delivery/couriers', { token: s.token })).body.items.reduce((a, c) => a + c.cashHeldMinor, 0);
  assert.equal(dlv11, 0);
  assert.equal(r.courier.heldMinor, dlv11, 'نقدِ صندوق و شرکتِ بیرونی «در دستِ پیک» شمرده شد');
  assert.equal(r.courier.collectedMinor, 0);
  assert.equal(r.courier.deliveries, 0);
  //  فروش همان فروش است (برده‌شده و تحویل‌شده)
  assert.equal(r.sales.count, 2);
});

test('۲) کارِ بازه نیست ولی نقدِ تسویه‌نشده و اختلافِ باز هست ⇒ در فهرست', async () => {
  const s = await shop('کهنه');
  const o = await order(s);
  await move(s, o.id, 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up');
  const old = now() - 40 * DAY;
  await query('UPDATE online_order_events SET created_at=$2 WHERE order_id=$1', [o.id, old]);
  await delivered(s.shopId, o.id, { cash: 4000, at: old });
  await query(`UPDATE online_orders SET status='refunded_full' WHERE id=$1`, [o.id]);
  const r = rowOf((await recon()).body, s.shopId);
  assert.ok(r, 'دکان با نقدِ تسویه‌نشده و اختلافِ باز نیامد');
  assert.equal(r.courier.heldMinor, 4000);
  assert.equal(r.courier.collectedMinor, 0, 'تحویلِ ۴۰ روزه در بازهٔ ۳۰ روزه نیست');
  assert.deepEqual(r.issues.map((i) => i.code), ['refund_without_ledger']);
  assert.equal(r.sales.count, 0);
});

test('۳) اختلاف و نقدِ سفارشِ آزمایشی نه در صف، نه در جمع؛ دکانِ فقط‌آزمایشی نیست', async () => {
  const s = await shop('آزمایشی');
  const real = await order(s);
  await move(s, real.id, 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up');
  const fake = await order(s, body({ test: true }));
  await move(s, fake.id, 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up');
  await query(`UPDATE online_orders SET status='refunded_full' WHERE id=$1`, [fake.id]);
  const only = await shop('فقط‌آزمایشی');
  const f2 = await order(only, body({ test: true }));
  await move(only, f2.id, 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up');
  await query(`UPDATE online_orders SET status='refunded_full' WHERE id=$1`, [f2.id]);
  await delivered(only.shopId, f2.id, { cash: 999, at: now() - HOUR });

  const b = (await recon()).body;
  const own = (await h.get('/api/shop/orders/reconciliation', { token: s.token })).body;
  assert.equal(own.issues[0].isTest, true, 'گزارشِ خودِ دکان آن را با isTest دارد');
  assert.deepEqual(rowOf(b, s.shopId).issues, []);
  assert.equal(b.testExcluded, true);
  assert.equal(rowOf(b, only.shopId), undefined, 'دکانِ فقط‌آزمایشی با ردیفِ صفر آمد');
  assert.equal(b.totals.issues, b.shops.reduce((a, r) => a + r.issues.length, 0));
});

test('۴) تسویه: سراسر آزمایشی بیرون؛ آمیخته بی سهمِ آزمایشی؛ اضافه', async () => {
  const s = await shop('تسویه');
  const t = now() - 2 * HOUR;
  const [r1, r2, f1, f2] = [await order(s), await order(s), await order(s, body({ test: true })), await order(s, body({ test: true }))];
  //  سراسر آزمایشی: ۲۰۰۰ تحویل، ۲۰۰۰ دریافت
  await delivered(s.shopId, f1.id, { cash: 2000, at: t, settlement: 'cst_all_test' });
  await settlement(s.shopId, 'cst_all_test', { due: 2000, received: 2000, at: t });
  //  آمیخته: واقعی ۳۰۰۰ + آزمایشی ۱۰۰۰ (کرایهٔ آزمایشی ۲۰۰ از نقد) ⇒ باید ۳۸۰۰، دریافت ۳۳۰۰ ⇒ کسری ۵۰۰
  await delivered(s.shopId, r1.id, { cash: 3000, at: t, settlement: 'cst_mixed' });
  await delivered(s.shopId, f2.id, { cash: 1000, fare: 200, at: t, settlement: 'cst_mixed' });
  await settlement(s.shopId, 'cst_mixed', { due: 3800, received: 3300, at: t, fareFromCash: true });
  //  اضافه: واقعی ۱۰۰۰، دریافت ۱۲۰۰
  await delivered(s.shopId, r2.id, { cash: 1000, at: t, settlement: 'cst_excess' });
  await settlement(s.shopId, 'cst_excess', { due: 1000, received: 1200, at: t });

  const c = rowOf((await recon()).body, s.shopId).courier;
  assert.deepEqual(
    [c.settlements, c.handoverDueMinor, c.receivedMinor, c.shortageMinor, c.excessMinor],
    [2, 3000 + 1000, 2500 + 1200, 500, 200]
  );
  assert.equal(c.collectedMinor, 4000, 'فقط تحویل‌های واقعی');
  assert.equal(c.heldMinor, 0);
});

test('۵) مرزِ بازه برای پیک و تسویه: آغاز شامل، پایان بیرون', async () => {
  const s = await shop('مرز');
  const from = now() - 10 * HOUR;
  const to = now() - 5 * HOUR;
  const [o1, o2, o3] = [await order(s), await order(s), await order(s)];
  await delivered(s.shopId, o1.id, { cash: 100, at: from });
  await delivered(s.shopId, o2.id, { cash: 7, at: to });
  await delivered(s.shopId, o3.id, { cash: 50, at: from, settlement: 'cst_edge_in' });
  await settlement(s.shopId, 'cst_edge_in', { due: 50, received: 50, at: from });
  await settlement(s.shopId, 'cst_edge_out', { due: 9, received: 9, at: to });
  const c = rowOf((await recon(`?from=${from}&to=${to}`)).body, s.shopId).courier;
  assert.equal(c.collectedMinor, 150);
  assert.equal(c.deliveries, 2);
  assert.equal(c.heldMinor, 107, 'تسویه‌نشده بی بازه است');
  assert.deepEqual([c.settlements, c.receivedMinor], [1, 50]);
});

test('۶) رد، ردِ خودکار، لغوِ مشتری و لغو همه شمرده می‌شوند', async () => {
  const s = await shop('لغو');
  const t = now() - HOUR;
  const statuses = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'accepted'];
  for (const [i, to] of statuses.entries()) {
    const o = await order(s);
    await query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status, actor_type, created_at)
       VALUES ($1,$2,$3,$4,'status','new',$5,'system',$6)`,
      [newId('oev'), o.id, s.shopId, 100 + i, to, t]
    );
  }
  const f = await order(s, body({ test: true }));
  await query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status, actor_type, created_at)
     VALUES ($1,$2,$3,100,'status','new','cancelled','system',$4)`,
    [newId('oev'), f.id, s.shopId, t]
  );
  assert.equal(rowOf((await recon()).body, s.shopId).cancellations, 4);
});

test('۸) reportMany همان report است، دکان به دکان', async () => {
  const ids = (await query('SELECT id FROM shops ORDER BY id')).rows.map((r) => r.id);
  const w = { from: now() - 30 * DAY, to: now() };
  const many = await R.reportMany(ids, w);
  for (const id of ids) {
    const one1 = await R.report(id, w);
    const m = many.get(id);
    assert.deepEqual({ ...m, serverTime: 0 }, { ...one1, serverTime: 0 }, id);
  }
  assert.equal((await R.reportMany([], w)).size, 0);
});

test('۹) دیدنِ گزارش سابقه دارد، با بازه و شمارِ دکان‌ها', async () => {
  const b = (await recon()).body;
  const a = await one(`SELECT detail FROM audit_logs WHERE action='admin.reconciliation_viewed' ORDER BY created_at DESC LIMIT 1`);
  const d = typeof a.detail === 'string' ? JSON.parse(a.detail) : a.detail;
  assert.deepEqual([d.from, d.to, d.shops], [b.window.from, b.window.to, b.shopsTotal]);
});

test('۱۰) بازهٔ روزانه در Asia/Kabul؛ روزِ «تا» شامل است', async () => {
  const w = health.reconWindow({ fromDate: '2026-10-01', toDate: '2026-10-24' });
  assert.equal(w.timezone, 'Asia/Kabul');
  assert.equal(new Date(w.from).toISOString(), '2026-09-30T19:30:00.000Z');
  assert.equal(new Date(w.to).toISOString(), '2026-10-24T19:30:00.000Z');
  assert.deepEqual([w.fromDate, w.toDate], ['2026-10-01', '2026-10-24']);
  //  ۲۳:۰۰ ِ کابل در روزِ «تا» شامل، ۰۰:۳۰ ِ روزِ بعد نه
  const s = await shop('کابل');
  const [o1, o2] = [await order(s), await order(s)];
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kabul' }).format(new Date(now() - 2 * DAY));
  const kabul = (ymd, hh, mm) => Date.parse(`${ymd}T00:00:00Z`) - 4.5 * HOUR + (hh * 60 + mm) * 60_000;
  const next = new Date(Date.parse(`${day}T00:00:00Z`) + DAY).toISOString().slice(0, 10);
  await delivered(s.shopId, o1.id, { cash: 11, at: kabul(day, 23, 0) });
  await delivered(s.shopId, o2.id, { cash: 22, at: kabul(next, 0, 30) });
  const r = (await recon(`?fromDate=${day}&toDate=${day}`)).body;
  assert.deepEqual([r.local.fromDate, r.local.toDate], [day, day]);
  assert.equal(rowOf(r, s.shopId).courier.collectedMinor, 11);
  for (const q of ['?fromDate=2026-13-01', '?fromDate=2026-10-02&toDate=2026-10-01', '?fromDate=2026-01-01&toDate=2026-06-01']) {
    assert.equal((await recon(q)).status, 400, q);
  }
});

test('۷) بیش از ۲۰۰ دکان: جمعِ کل از همه، فهرست به اهمیت، «۲۰۰ از M»', async () => {
  const owner = (await h.newUser('صاحبِ انبوه', 'shop')).user.id;
  const t = now() - HOUR;
  const N = 205;
  for (let i = 0; i < N; i += 1) {
    const id = `zz_${String(i).padStart(3, '0')}`;
    await query('INSERT INTO shops (id, owner_user_id, name, created_at, updated_at) VALUES ($1,$2,$3,$4,$4)', [id, owner, `انبوه ${i}`, t]);
    await settlement(id, `cst_${id}`, { due: 100, received: 0, at: t });
  }
  const b = (await recon()).body;
  const all = await one(`SELECT COALESCE(SUM(shortage_minor),0)::bigint AS s FROM courier_settlements
                           WHERE created_at >= $1 AND id NOT IN ('cst_all_test')`, [b.window.from]);
  assert.equal(b.truncated, true);
  assert.equal(b.shops.length, 200);
  assert.ok(b.shopsTotal > 200 && b.shopsTotal >= N, String(b.shopsTotal));
  assert.equal(b.totals.shortageMinor, Number(all.s), 'جمعِ کل از همهٔ دکان‌ها نیست');
  //  به اهمیت: اختلاف‌دارها اول، بعد تسویه‌نشده
  const rank = b.shops.map((r) => [r.issues.length, r.courier.heldMinor]);
  for (let i = 1; i < rank.length; i += 1) {
    assert.ok(rank[i - 1][0] > rank[i][0] || (rank[i - 1][0] === rank[i][0] && rank[i - 1][1] >= rank[i][1]), `ترتیب در ${i}`);
  }
  assert.ok(b.shops[0].issues.length >= 1);
});
