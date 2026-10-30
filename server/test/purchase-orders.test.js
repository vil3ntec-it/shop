'use strict';
/**
 * `/api/shop/purchase-orders` — PUR-02 (وضعیت‌ها)، PUR-04 (رسیدِ جزئی با خراب، باقی‌ماندهٔ باز)، §۶.۶ و S19.
 * پرچمِ خاموش، مجوزِ نقش‌ها، جداییِ دکان‌ها، کلیدِ یکتای ساختن و رسید، رسیدِ هم‌زمان، نسخهٔ کهنه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { many, query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const BASE = '/api/shop/purchase-orders';

async function shopWithTeam(label, { flag = true } = {}) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const manager = await h.newUser(`مدیر ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: manager.accessToken })).status));
  const shopId = made.body.shop.id;
  if (flag) await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.purchase_orders','true',1)", [shopId]);
  return { owner: owner.accessToken, staff: staff.accessToken, manager: manager.accessToken, shopId };
}

/** S19: سه قلم، هر کدام ۱۰؛ فی A ۱۰۰، B ۲۰۰، C ۵۰ افغانی */
const S19 = {
  supplierId: 'sup-1', supplierName: 'شرکتِ نمونه', expectedDate: '2026-11-05', note: 'تحویل صبح',
  lines: [
    { productId: 'pA', name: 'کالای A', unit: 'عدد', qtyMilli: 10000, priceMinor: 10000 },
    { productId: 'pB', name: 'کالای B', unit: 'عدد', qtyMilli: 10000, priceMinor: 20000 },
    { productId: 'pC', name: 'کالای C', unit: 'عدد', qtyMilli: 10000, priceMinor: 5000 },
  ],
};

let keySeq = 0;
const key = () => `po-key-${Date.now()}-${++keySeq}`;
const create = (t, body = S19, k = key()) => h.post(BASE, body, { token: t, headers: { 'Idempotency-Key': k } });

test('پرچم خاموش ⇒ فقط /config (enabled: false)؛ بقیه ۴۰۳ purchase_orders_disabled؛ بی توکن ۴۰۱', async () => {
  const t = await shopWithTeam('خاموش', { flag: false });
  const cfg = await h.get(`${BASE}/config`, { token: t.staff });
  assert.equal(cfg.status, 200, JSON.stringify(cfg.body));
  assert.equal(cfg.body.enabled, false);
  assert.deepEqual(cfg.body.permissions, { manage: false, receive: true });
  for (const r of [
    await h.get(BASE, { token: t.owner }),
    await h.get(`${BASE}/x`, { token: t.owner }),
    await create(t.owner),
    await h.post(`${BASE}/x/send`, {}, { token: t.owner }),
    await h.post(`${BASE}/x/receipts`, { id: 'r1', lines: [] }, { token: t.owner }),
  ]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'purchase_orders_disabled');
  }
  assert.equal((await h.get(`${BASE}/config`)).status, 401);
  assert.equal((await many('SELECT 1 FROM purchase_orders WHERE shop_id=$1', [t.shopId])).length, 0);
  //  پرچمِ `all` هم روشن می‌کند؛ مقدارِ خودِ دکان بر آن جلو می‌افتد
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ('all','shop.purchase_orders','true',1)");
  try {
    assert.equal((await h.get(`${BASE}/config`, { token: t.owner })).body.enabled, true);
    await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.purchase_orders','false',1)", [t.shopId]);
    assert.equal((await h.get(`${BASE}/config`, { token: t.owner })).body.enabled, false);
  } finally {
    await query("DELETE FROM live_config WHERE key='shop.purchase_orders' AND scope IN ('all',$1)", [t.shopId]);
  }
});

test('S19 سرتاسر: پیش‌نویس ⇒ فرستاده ⇒ رسیدِ جزئی با ۲ خراب (بخشی، باقی‌ماندهٔ B و C باز) ⇒ رسیدِ دوم (کامل)', async () => {
  const t = await shopWithTeam('S19');
  const c = await create(t.manager);
  assert.equal(c.status, 201, JSON.stringify(c.body));
  const o = c.body.order;
  assert.equal(o.no, 1);
  assert.equal(o.status, 'draft');
  assert.deepEqual(o.lines.map((l) => l.id), ['l1', 'l2', 'l3']);
  assert.deepEqual(o.totals, { orderedMinor: 350000, acceptedMinor: 0, remainingMinor: 350000 });
  assert.deepEqual(o.allowed, ['edit', 'send', 'cancel']);

  //  پیش‌نویس رسید نمی‌گیرد
  const early = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-early', lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: t.manager });
  assert.equal(early.status, 409);
  assert.equal(early.body.error.code, 'not_sent');

  const sent = await h.post(`${BASE}/${o.id}/send`, {}, { token: t.manager });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.equal(sent.body.changed, true);
  assert.equal(sent.body.order.status, 'sent');
  const again = await h.post(`${BASE}/${o.id}/send`, {}, { token: t.manager });
  assert.equal(again.body.changed, false, 'فرستادنِ دوباره بی اثر');

  const r1 = { id: '01JS0000000000000000000R01', lines: [
    { lineId: 'l1', receivedMilli: 10000 },
    { lineId: 'l2', receivedMilli: 7000 },
    { lineId: 'l3', receivedMilli: 10000, damagedMilli: 2000 },
  ] };
  const got = await h.post(`${BASE}/${o.id}/receipts`, r1, { token: t.staff });
  assert.equal(got.status, 201, JSON.stringify(got.body));
  assert.equal(got.body.replayed, false);
  assert.equal(got.body.order.status, 'partial');
  assert.deepEqual(got.body.receipt.posted.map((p) => p.acceptedMilli), [10000, 7000, 8000], 'موجودی: A +۱۰، B +۷، C +۸');
  assert.equal(got.body.receipt.posted[2].damagedMilli, 2000, 'اختلافِ ۲ عددِ C');
  assert.equal(got.body.receipt.totalMinor, 280000);
  assert.deepEqual(got.body.order.progress.map((p) => p.remainingMilli), [0, 3000, 2000], 'باقی‌مانده باز');

  //  تکرارِ همان رسید (قطعِ شبکه پس از ثبت) ⇒ همان پاسخ، بی ردیفِ دوم
  const dup = await h.post(`${BASE}/${o.id}/receipts`, r1, { token: t.staff });
  assert.equal(dup.status, 200, JSON.stringify(dup.body));
  assert.equal(dup.body.replayed, true);
  assert.equal(dup.headers.get('idempotent-replayed'), 'true');
  assert.equal(dup.body.receipt.id, r1.id);
  const other = await h.post(`${BASE}/${o.id}/receipts`, { ...r1, lines: [{ lineId: 'l1', receivedMilli: 1 }] }, { token: t.staff });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'id_conflict');

  const over = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-over', lines: [{ lineId: 'l2', receivedMilli: 4000 }] }, { token: t.manager });
  assert.equal(over.status, 400);
  assert.equal(over.body.error.code, 'over_ordered');
  assert.equal(over.body.error.lineId, 'l2');

  const r2 = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-two', lines: [
    { lineId: 'l2', receivedMilli: 3000 }, { lineId: 'l3', receivedMilli: 2000 },
  ] }, { token: t.manager });
  assert.equal(r2.status, 201, JSON.stringify(r2.body));
  assert.equal(r2.body.order.status, 'complete');
  assert.deepEqual(r2.body.order.totals, { orderedMinor: 350000, acceptedMinor: 350000, remainingMinor: 0 });

  const full = await h.get(`${BASE}/${o.id}`, { token: t.staff });
  assert.equal(full.status, 200);
  assert.equal(full.body.order.receipts.length, 2);
  assert.equal(full.body.order.status, 'complete');
  const cancel = await h.post(`${BASE}/${o.id}/cancel`, { reason: 'دیر' }, { token: t.owner });
  assert.equal(cancel.status, 409);
  assert.equal(cancel.body.error.code, 'complete');

  const logs = await many("SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'purchase_order.%' ORDER BY created_at", [t.shopId]);
  assert.deepEqual(logs.map((l) => l.action),
    ['purchase_order.create', 'purchase_order.send', 'purchase_order.receive', 'purchase_order.receive']);
});

test('مجوز: شاگرد نمی‌سازد/نمی‌فرستد/لغو نمی‌کند؛ رسید می‌گیرد ولی فی را عوض نمی‌کند و بیش از سفارش نمی‌پذیرد', async () => {
  const t = await shopWithTeam('مجوز');
  const denied = await create(t.staff);
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  const o = (await create(t.owner)).body.order;
  assert.equal((await h.post(`${BASE}/${o.id}/send`, {}, { token: t.staff })).status, 403);
  assert.equal((await h.put(`${BASE}/${o.id}`, { ...S19, version: 1 }, { token: t.staff })).status, 403);
  assert.equal((await h.post(`${BASE}/${o.id}/send`, {}, { token: t.owner })).status, 200);
  assert.equal((await h.post(`${BASE}/${o.id}/cancel`, { reason: 'x' }, { token: t.staff })).status, 403);

  const price = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-price', lines: [{ lineId: 'l1', receivedMilli: 1000, priceMinor: 9000 }] }, { token: t.staff });
  assert.equal(price.status, 403, JSON.stringify(price.body));
  assert.equal(price.body.error.code, 'price_change_not_allowed');
  const same = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-same', lines: [{ lineId: 'l1', receivedMilli: 1000, priceMinor: 10000 }] }, { token: t.staff });
  assert.equal(same.status, 201, 'همان فیِ سفارش برای شاگرد درست است');
  const over = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-o', allowOver: true, lines: [{ lineId: 'l1', receivedMilli: 99000 }] }, { token: t.staff });
  assert.equal(over.status, 403);
  assert.equal(over.body.error.code, 'over_not_allowed');
  //  مدیر فی را عوض می‌کند (فاکتورِ تأمین‌کننده گران‌تر) و با رضایتِ صریح بیش از سفارش می‌پذیرد
  const mgr = await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-m', allowOver: true, lines: [{ lineId: 'l1', receivedMilli: 10000, priceMinor: 10500 }] }, { token: t.manager });
  assert.equal(mgr.status, 201, JSON.stringify(mgr.body));
  assert.equal(mgr.body.receipt.posted[0].overMilli, 1000);
  assert.equal(mgr.body.receipt.posted[0].totalMinor, 105000);
  assert.equal((await h.post(`${BASE}/${o.id}/receipts`, { id: 'r-staff-none', lines: [{ lineId: 'l1', receivedMilli: 0 }] }, { token: t.staff })).body.error.code, 'nothing_received');
});

test('جداییِ دکان‌ها: سفارشِ دکانِ دیگر ۴۰۴ و در فهرست نیست؛ کلیدِ رسید در دکان یکتاست', async () => {
  const a = await shopWithTeam('الف');
  const b = await shopWithTeam('ب');
  const oa = (await create(a.owner)).body.order;
  for (const r of [
    await h.get(`${BASE}/${oa.id}`, { token: b.owner }),
    await h.post(`${BASE}/${oa.id}/send`, {}, { token: b.owner }),
    await h.post(`${BASE}/${oa.id}/receipts`, { id: 'rx', lines: [{ lineId: 'l1', receivedMilli: 1 }] }, { token: b.owner }),
  ]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'purchase_order_not_found');
  }
  assert.deepEqual((await h.get(BASE, { token: b.owner })).body.orders, []);
  const ob = (await create(b.owner)).body.order;
  assert.equal(ob.no, 1, 'شمارهٔ هر دکان جدا');

  await h.post(`${BASE}/${oa.id}/send`, {}, { token: a.owner });
  const oa2 = (await create(a.owner, { ...S19, supplierId: 'sup-2' })).body.order;
  assert.equal(oa2.no, 2);
  await h.post(`${BASE}/${oa2.id}/send`, {}, { token: a.owner });
  const first = await h.post(`${BASE}/${oa.id}/receipts`, { id: 'shared-key', lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: a.owner });
  assert.equal(first.status, 201);
  const cross = await h.post(`${BASE}/${oa2.id}/receipts`, { id: 'shared-key', lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: a.owner });
  assert.equal(cross.status, 409);
  assert.equal(cross.body.error.code, 'id_conflict');
  //  همان کلید در دکانِ دیگر آزاد است
  await h.post(`${BASE}/${ob.id}/send`, {}, { token: b.owner });
  assert.equal((await h.post(`${BASE}/${ob.id}/receipts`, { id: 'shared-key', lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: b.owner })).status, 201);
});

test('ساختن: کلیدِ یکتایی (همان بدنه ⇒ همان سفارش، دیگری ⇒ ۴۰۹)، ورودیِ نادرست با کدِ هسته و ردیف', async () => {
  const t = await shopWithTeam('کلید');
  assert.equal((await h.post(BASE, S19, { token: t.owner })).body.error.code, 'idempotency_key_required');
  assert.equal((await create(t.owner, S19, 'short')).body.error.code, 'bad_idempotency_key');
  const k = key();
  const one = await create(t.owner, S19, k);
  const two = await create(t.owner, S19, k);
  assert.equal(two.status, 200);
  assert.equal(two.body.replayed, true);
  assert.equal(two.body.order.id, one.body.order.id);
  const clash = await create(t.owner, { ...S19, note: 'دیگر' }, k);
  assert.equal(clash.status, 409);
  assert.equal(clash.body.error.code, 'idempotency_key_reused');
  assert.equal((await many('SELECT 1 FROM purchase_orders WHERE shop_id=$1', [t.shopId])).length, 1);

  const bad = async (body, code, lineId, index) => {
    const r = await create(t.owner, body);
    assert.equal(r.status, 400, `${code}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code);
    if (lineId !== undefined) { assert.equal(r.body.error.lineId, lineId); assert.equal(r.body.error.index, index); }
  };
  await bad({ ...S19, supplierId: '' }, 'supplier_required');
  await bad({ ...S19, lines: [] }, 'lines_required');
  await bad({ ...S19, lines: [S19.lines[0], S19.lines[0]] }, 'duplicate_product', 'l2', 1);
  await bad({ ...S19, lines: [{ ...S19.lines[0], qtyMilli: 0 }] }, 'bad_qty', 'l1', 0);
  await bad({ ...S19, lines: [{ ...S19.lines[0], qtyMilli: 1.5 }] }, 'bad_qty', 'l1', 0);
  await bad({ ...S19, lines: [{ ...S19.lines[0], qtyMilli: 1_000_000_001 }] }, 'bad_qty', 'l1', 0);
  await bad({ ...S19, lines: [{ ...S19.lines[0], priceMinor: -1 }] }, 'bad_price', 'l1', 0);
  await bad({ ...S19, lines: [{ ...S19.lines[0], priceMinor: '100' }] }, 'bad_price', 'l1', 0);
  await bad({ ...S19, expectedDate: '2026-02-30' }, 'bad_date');
  await bad({ ...S19, note: 'x'.repeat(501) }, 'text_too_long');
  await bad({ ...S19, lines: [{ ...S19.lines[0], id: 'bad id' }] }, 'line_id_required', 'bad id', 0);
});

test('ویرایش با نسخه: نسخهٔ کهنه ۴۰۹، پس از فرستادن not_draft؛ لغوِ فرستاده دلیل می‌خواهد و رسیدها می‌مانند', async () => {
  const t = await shopWithTeam('نسخه');
  const o = (await create(t.manager)).body.order;
  assert.equal((await h.put(`${BASE}/${o.id}`, { ...S19 }, { token: t.manager })).body.error.code, 'version_required');
  const ok = await h.put(`${BASE}/${o.id}`, { ...S19, version: 1, lines: [S19.lines[0]] }, { token: t.manager });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.order.version, 2);
  assert.equal(ok.body.order.lines.length, 1);
  const stale = await h.put(`${BASE}/${o.id}`, { ...S19, version: 1 }, { token: t.owner });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'version_conflict');

  await h.post(`${BASE}/${o.id}/send`, {}, { token: t.manager });
  const late = await h.put(`${BASE}/${o.id}`, { ...S19, version: 3 }, { token: t.manager });
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'not_draft');

  assert.equal((await h.post(`${BASE}/${o.id}/receipts`, { id: 'rp', lines: [{ lineId: 'l1', receivedMilli: 4000 }] }, { token: t.staff })).status, 201);
  const noWhy = await h.post(`${BASE}/${o.id}/cancel`, { reason: '  ' }, { token: t.manager });
  assert.equal(noWhy.status, 400);
  assert.equal(noWhy.body.error.code, 'reason_required');
  const cancelled = await h.post(`${BASE}/${o.id}/cancel`, { reason: 'تأمین‌کننده بقیه را ندارد' }, { token: t.manager });
  assert.equal(cancelled.status, 200, JSON.stringify(cancelled.body));
  assert.equal(cancelled.body.order.status, 'cancelled');
  assert.equal(cancelled.body.order.cancelReason, 'تأمین‌کننده بقیه را ندارد');
  assert.equal(cancelled.body.order.receiptCount, 1);
  assert.equal(cancelled.body.order.progress[0].acceptedMilli, 4000);
  assert.equal((await h.post(`${BASE}/${o.id}/cancel`, {}, { token: t.manager })).body.changed, false, 'لغوِ دوباره بی اثر');
  const after = await h.post(`${BASE}/${o.id}/receipts`, { id: 'rq', lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: t.staff });
  assert.equal(after.status, 409);
  assert.equal(after.body.error.code, 'cancelled');
});

test('هم‌زمانی: ده رسیدِ هم‌زمان با کلیدهای جدا باقی‌مانده را دو بار نمی‌خورند؛ ده بار همان کلید ⇒ یک رسید', async () => {
  const t = await shopWithTeam('هم‌زمان');
  const o = (await create(t.owner, { ...S19, lines: [{ ...S19.lines[0], qtyMilli: 5000 }] })).body.order;
  await h.post(`${BASE}/${o.id}/send`, {}, { token: t.owner });
  const racers = await Promise.all(Array.from({ length: 10 }, (_, i) =>
    h.post(`${BASE}/${o.id}/receipts`, { id: `race-${i}`, lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: t.staff })));
  const codes = racers.map((r) => r.status).sort();
  assert.deepEqual(codes, [201, 201, 201, 201, 201, 409, 409, 409, 409, 409], JSON.stringify(racers.map((r) => r.body.error?.code)));
  //  پشتِ قفلِ سفارش یکی‌یکی: پنجمی کامل می‌کند و بقیه «کامل است» می‌گیرند
  assert.ok(racers.filter((r) => r.status === 409).every((r) => r.body.error.code === 'complete'));
  const st = (await h.get(`${BASE}/${o.id}`, { token: t.owner })).body.order;
  assert.equal(st.status, 'complete');
  assert.equal(st.progress[0].acceptedMilli, 5000, 'هرگز بیش از سفارش');

  const o2 = (await create(t.owner)).body.order;
  await h.post(`${BASE}/${o2.id}/send`, {}, { token: t.owner });
  const same = await Promise.all(Array.from({ length: 10 }, () =>
    h.post(`${BASE}/${o2.id}/receipts`, { id: 'one-key', lines: [{ lineId: 'l2', receivedMilli: 2000 }] }, { token: t.staff })));
  assert.deepEqual(same.map((r) => r.status).sort(), [200, 200, 200, 200, 200, 200, 200, 200, 200, 201]);
  assert.equal((await many('SELECT 1 FROM purchase_receipts WHERE shop_id=$1 AND po_id=$2', [t.shopId, o2.id])).length, 1);

  //  ساختنِ هم‌زمان: شماره‌ها یکتا و پشتِ هم
  const made = await Promise.all(Array.from({ length: 6 }, () => create(t.owner)));
  assert.ok(made.every((r) => r.status === 201), JSON.stringify(made.map((r) => r.body)));
  const nos = (await many('SELECT po_no FROM purchase_orders WHERE shop_id=$1 ORDER BY po_no', [t.shopId])).map((r) => Number(r.po_no));
  assert.deepEqual(nos, [1, 2, 3, 4, 5, 6, 7, 8]);
});

test('فهرست: تازه‌ترین بالا، پالایشِ وضعیت و تأمین‌کننده، صفحه با نشانگر، وضعیتِ نمایشی', async () => {
  const t = await shopWithTeam('فهرست');
  const ids = [];
  for (let i = 0; i < 5; i++) ids.push((await create(t.owner, { ...S19, supplierId: i % 2 ? 'sup-odd' : 'sup-even' })).body.order.id);
  await h.post(`${BASE}/${ids[0]}/send`, {}, { token: t.owner });
  await h.post(`${BASE}/${ids[0]}/receipts`, { id: 'rl', lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, { token: t.owner });
  const all = await h.get(BASE, { token: t.staff });
  assert.equal(all.status, 200);
  assert.equal(all.body.orders.length, 5);
  assert.equal(all.body.orders[0].id, ids[0], 'رسید زمانِ تغییر را بالا می‌برد');
  assert.equal(all.body.orders[0].status, 'partial');
  assert.equal(all.body.orders[0].receipts, undefined, 'فهرست رسیدها را نمی‌فرستد');
  //  PUR-10: فرستادن و نخستین رسید برای سنجشِ زمانِ تأمین
  assert.ok(Number.isSafeInteger(all.body.orders[0].sentAt));
  assert.ok(all.body.orders[0].firstReceiptAt >= all.body.orders[0].sentAt);
  assert.ok(all.body.orders.slice(1).every((o) => o.firstReceiptAt === null && o.sentAt === null));
  assert.equal((await h.get(`${BASE}?state=sent`, { token: t.staff })).body.orders.length, 1);
  assert.equal((await h.get(`${BASE}?supplierId=sup-odd`, { token: t.staff })).body.orders.length, 2);
  assert.equal((await h.get(`${BASE}?state=partial`, { token: t.staff })).status, 400, 'پالایش فقط روی وضعیتِ ذخیره‌شده');
  const p1 = await h.get(`${BASE}?limit=2`, { token: t.staff });
  assert.equal(p1.body.orders.length, 2);
  assert.ok(p1.body.next);
  const p2 = await h.get(`${BASE}?limit=2&cursor=${encodeURIComponent(p1.body.next)}`, { token: t.staff });
  const p3 = await h.get(`${BASE}?limit=2&cursor=${encodeURIComponent(p2.body.next)}`, { token: t.staff });
  assert.equal(p3.body.next, null);
  const seen = [...p1.body.orders, ...p2.body.orders, ...p3.body.orders].map((o) => o.id);
  assert.deepEqual([...seen].sort(), [...ids].sort());
  assert.equal((await h.get(`${BASE}?cursor=x`, { token: t.staff })).body.error.code, 'bad_cursor');
});
