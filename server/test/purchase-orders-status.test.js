'use strict';
/**
 * PUR-02/PUR-03 — پالایهٔ «وضعیتِ نمایشی» در فهرستِ سفارش‌های خرید روی سرور (`GET /api/shop/purchase-orders?status=…`).
 *
 * پیش از این سرور فقط وضعیتِ ذخیره‌شده (draft | sent | cancelled) را می‌پالایید و «بخشی»/«کامل» فقط در برنامه از
 * صفحهٔ نخست (۵۰ ردیف) جدا می‌شد ⇒ سفارشِ باز که پشتِ ۵۰ سفارشِ تازه‌ترِ فرستاده/کامل افتاده بود نه در پالایهٔ
 * «بخشی» دیده می‌شد و نه در «در راه» ِ پیشنهادِ سفارش (PUR-03) حساب می‌شد. حالا:
 *   • `status` ∈ draft | sent | partial | complete | cancelled | open (= فرستاده یا بخشی)، با همان `core.status`؛
 *   • `state` کنارِ `status` فقط وقتی هم‌خوان است (`state=sent&status=open` برای برنامهٔ نو روی سرورِ کهنه)؛
 *   • صفحه‌بندی درست: نشانگر = آخرین ردیفِ **پیموده‌شده**، پس صفحه‌ای کم‌تر از `limit` (حتی تهی) با `next` ممکن است؛
 *     هر درخواست حداکثر `SCAN_MAX` ردیف می‌پیماید (سقفِ کار برای دکانِ با هزاران سفارشِ کامل).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const BASE = '/api/shop/purchase-orders';

async function shop(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const shopId = made.body.shop.id;
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.purchase_orders','true',1)", [shopId]);
  return { owner: owner.accessToken, staff: staff.accessToken, shopId };
}

const ONE = (supplierId = 'sup-1') => ({
  supplierId, supplierName: 'شرکت',
  lines: [
    { productId: 'pA', name: 'A', unit: 'عدد', qtyMilli: 10000, priceMinor: 10000 },
    { productId: 'pB', name: 'B', unit: 'عدد', qtyMilli: 5000, priceMinor: 20000 },
  ],
});

let seq = 0;
const k = () => `pos-${Date.now()}-${++seq}`;

async function order(t, { send = false, receive = null, cancel = false, supplierId } = {}) {
  const c = await h.post(BASE, ONE(supplierId), { token: t.owner, headers: { 'Idempotency-Key': k() } });
  assert.equal(c.status, 201, JSON.stringify(c.body));
  const id = c.body.order.id;
  if (send) assert.equal((await h.post(`${BASE}/${id}/send`, {}, { token: t.owner })).status, 200);
  if (receive) {
    const r = await h.post(`${BASE}/${id}/receipts`, { id: k(), lines: receive }, { token: t.owner });
    assert.equal(r.status, 201, JSON.stringify(r.body));
  }
  if (cancel) assert.equal((await h.post(`${BASE}/${id}/cancel`, { reason: 'دیگر لازم نیست' }, { token: t.owner })).status, 200);
  return id;
}

const ids = (res) => res.body.orders.map((o) => o.id);

/** همهٔ صفحه‌ها با نشانگر تا `next` تهی شود (سقفِ ۵۰ دور در آزمون) */
async function all(t, qs) {
  const out = [];
  let cursor = null;
  for (let i = 0; i < 50; i++) {
    const r = await h.get(`${BASE}?${qs}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`, { token: t.staff });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    out.push(...r.body.orders);
    cursor = r.body.next;
    if (!cursor) return out;
  }
  throw new Error('صفحه‌ها تمام نشدند');
}

test('PUR-02 status: هر پنج وضعیت و «باز» روی سرور، هر ردیف همان وضعیت را دارد؛ state ِ هم‌خوان پذیرفته، ناهم‌خوان ۴۰۰', async () => {
  const t = await shop('وضعیت');
  const draft = await order(t);
  const sent = await order(t, { send: true });
  const partial = await order(t, { send: true, receive: [{ lineId: 'l1', receivedMilli: 4000 }] });
  //  کامل: همهٔ باقی‌مانده (۲ خراب ⇒ رسیدِ دوم لازم نیست چون پذیرفته = رسیده − خراب = سفارش)
  const complete = await order(t, { send: true, receive: [{ lineId: 'l1', receivedMilli: 10000 }, { lineId: 'l2', receivedMilli: 7000, damagedMilli: 2000 }] });
  //  فقط خراب ⇒ هنوز «فرستاده» (هیچ واحدی پذیرفته نشده)
  const damagedOnly = await order(t, { send: true, receive: [{ lineId: 'l1', receivedMilli: 3000, damagedMilli: 3000 }] });
  const cancelled = await order(t, { send: true, cancel: true });
  const cancelledPartial = await order(t, { send: true, receive: [{ lineId: 'l2', receivedMilli: 1000 }], cancel: true });

  const want = {
    draft: [draft], sent: [damagedOnly, sent], partial: [partial], complete: [complete],
    cancelled: [cancelledPartial, cancelled], open: [damagedOnly, partial, sent],
  };
  for (const [status, expected] of Object.entries(want)) {
    const r = await h.get(`${BASE}?status=${status}`, { token: t.staff });
    assert.equal(r.status, 200, `${status}: ${JSON.stringify(r.body)}`);
    assert.deepEqual(ids(r), expected, status);
    for (const o of r.body.orders) {
      assert.ok(status === 'open' ? ['sent', 'partial'].includes(o.status) : o.status === status, `${status}: ${o.status}`);
      assert.equal(o.receipts, undefined, 'فهرست رسیدها را نمی‌فرستد');
    }
    assert.equal(r.body.next, null, status);
  }
  //  برنامهٔ نو روی سرورِ کهنه `state=sent&status=open` می‌فرستد؛ سرورِ نو همان «باز» را می‌دهد
  assert.deepEqual(ids(await h.get(`${BASE}?state=sent&status=open`, { token: t.staff })), want.open);
  assert.deepEqual(ids(await h.get(`${BASE}?state=draft&status=draft`, { token: t.staff })), want.draft);
  for (const qs of ['status=closed', 'status=PARTIAL', 'state=draft&status=partial', 'state=cancelled&status=open', 'status=open&status=sent']) {
    const r = await h.get(`${BASE}?${qs}`, { token: t.staff });
    assert.equal(r.status, 400, `${qs}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_status', qs);
  }
  //  پالایهٔ پیشینِ `state` همان است
  assert.equal((await h.get(`${BASE}?state=partial`, { token: t.staff })).body.error.code, 'bad_state');
  assert.equal((await h.get(`${BASE}?state=sent`, { token: t.staff })).body.orders.length, 4);
  //  با تأمین‌کننده هم
  const other = await order(t, { send: true, supplierId: 'sup-2' });
  assert.deepEqual(ids(await h.get(`${BASE}?status=sent&supplierId=sup-2`, { token: t.staff })), [other]);
  assert.deepEqual(ids(await h.get(`${BASE}?status=partial&supplierId=sup-2`, { token: t.staff })), []);
});

test('PUR-02 status: صفحه‌بندی بی تکرار و بی جاافتاده با limit=1؛ دکانِ دیگر دیده نمی‌شود', async () => {
  const t = await shop('صفحه');
  const u = await shop('دیگر');
  const open = [];
  for (let i = 0; i < 4; i++) {
    open.push(await order(t, { send: true, receive: i % 2 ? [{ lineId: 'l1', receivedMilli: 1000 }] : null }));
    //  میانِ هر دو سفارشِ باز، دو سفارشِ کامل (همان «صفحهٔ نخستِ پر از کامل»)
    for (let j = 0; j < 2; j++) await order(t, { send: true, receive: [{ lineId: 'l1', receivedMilli: 10000 }, { lineId: 'l2', receivedMilli: 5000 }] });
  }
  await order(u, { send: true });
  const got = await all(t, 'status=open&limit=1');
  assert.deepEqual(got.map((o) => o.id), [...open].reverse(), 'همهٔ بازها، تازه‌ترین بالا، هر کدام یک بار');
  assert.ok(got.every((o) => ['sent', 'partial'].includes(o.status)));
  const done = await all(t, 'status=complete&limit=3');
  assert.equal(done.length, 8);
  assert.equal(new Set(done.map((o) => o.id)).size, 8);
  //  نشانگرِ ساختگی همان ۴۰۰ ِ پیشین
  assert.equal((await h.get(`${BASE}?status=open&cursor=x`, { token: t.staff })).body.error.code, 'bad_cursor');
});

test('PUR-03 status=open: سفارشِ بازِ کهنه پشتِ صدها سفارشِ کامل هم پیدا می‌شود؛ هر درخواست حداکثر SCAN_MAX ردیف می‌پیماید', async () => {
  const po = require('../src/lib/purchase-orders');
  assert.ok(Number.isSafeInteger(po.SCAN_MAX) && po.SCAN_MAX >= 100, 'سقفِ پیمایش');
  const t = await shop('سقف');
  const old = await order(t, { send: true });
  //  پیرترین ردیف: باز. سپس SCAN_MAX + ۲۰ سفارشِ «کامل» ِ تازه‌تر (مستقیم در پایگاه، با رسیدِ کامل)
  const n = po.SCAN_MAX + 20;
  const base = Date.now() + 10_000;
  const lines = JSON.stringify([{ id: 'l1', productId: 'pA', name: 'A', unit: 'عدد', qtyMilli: 1000, priceMinor: 100 }]);
  const recv = JSON.stringify([{ lineId: 'l1', receivedMilli: 1000, damagedMilli: 0, priceMinor: 100 }]);
  await query(
    `INSERT INTO purchase_orders (shop_id, id, po_no, client_key, request_hash, supplier_id, supplier_name, state, lines,
       note, version, created_by, created_at, updated_at, sent_at)
     SELECT $1, 'bulk' || lpad(g::text, 5, '0'), 1000 + g, 'bulk-key-' || g, 'h', 'sup-1', 'شرکت', 'sent', $2::jsonb,
       '', 2, '', $3::bigint + g, $3::bigint + g, $3::bigint + g
     FROM generate_series(1, $4::int) AS g`,
    [t.shopId, lines, base, n]
  );
  await query(
    `INSERT INTO purchase_receipts (shop_id, id, po_id, lines, posted, total_minor, note, created_by, created_at)
     SELECT $1, 'bulk-r' || g, 'bulk' || lpad(g::text, 5, '0'), $2::jsonb, '[]'::jsonb, 100, '', '', $3::bigint + g
     FROM generate_series(1, $4::int) AS g`,
    [t.shopId, recv, base, n]
  );
  const first = await h.get(`${BASE}?status=open&limit=50`, { token: t.staff });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.deepEqual(ids(first), [], 'سقفِ پیمایش پیش از رسیدن به سفارشِ کهنه');
  assert.ok(first.body.next, 'صفحهٔ تهی با نشانگر: هنوز ردیف هست');
  const got = await all(t, 'status=open&limit=50');
  assert.deepEqual(got.map((o) => o.id), [old]);
  //  پالایهٔ «کامل» با limit کوچک: همهٔ n، هر کدام یک بار
  const done = await all(t, 'status=complete&limit=100');
  assert.equal(done.length, n);
  assert.equal(new Set(done.map((o) => o.id)).size, n);
});
