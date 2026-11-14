'use strict';
/**
 * S09 — «یک فاکتور در دو دستگاه» ⇒ صندوقِ تعارضِ صاحب (`lib/held-twice.js`، `POST /api/shop/held-invoices/:id/twice`).
 *
 *   ۱) پرچمِ خاموشِ `shop.held_twice_cancel` (پیش‌فرض): `GET` همان پاسخِ پیشین (بی `auto_cancel`) و `POST …/twice` ۴۰۴
 *      `held_twice_off` پیش از بدنه؛ بی `shop.held_sync` ۴۰۳.
 *   ۲) روشن: `GET` ⇒ `auto_cancel: true`؛ گزارشِ فروشِ دومِ لغوشده ⇒ ۲۰۱ و یک ردیفِ باز در صندوقِ صاحب (گونهٔ `held_twice`،
 *      فقط «کنار بگذار» یا «اصلاح و ثبت»، نه «دوباره ثبت»)؛ تکرار ⇒ ۲۰۰ همان ردیف؛ سابقهٔ `held.twice_cancelled`.
 *   ۳) شکلِ بد ۴۰۰؛ بی توکن ۴۰۱؛ دکانِ دیگر ردیف را نمی‌بیند؛ سقفِ نرخ ۴۲۹. هیچ ردیفِ دفتری (`oplog`) ساخته نمی‌شود.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const BASE = '/api/shop/held-invoices';
let owner;
let staff;
let other;
let shopId;
let otherShop;

const setFlag = (shop, key, on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = EXCLUDED.value`, [shop, key, on ? 'true' : 'false']);
const body = (extra = {}) => ({
  sale_id: 'SALE2B1', kept_id: 'SALE1A1', kept_series: 'A1', cancelled: true, number: 12, total_minor: 32000, created_at: 1761800000000,
  items: [{ productId: 'p_rice', name: 'برنج', qtyMilli: 2000, unitPriceMinor: 16000, totalMinor: 32000 }], device_id: 'dev-b', ...extra,
});
const twice = (who, id = 'HELD1', b = body()) => { resetAll(); return h.post(`${BASE}/${id}/twice`, b, { token: who && who.accessToken }); };

test.before(async () => {
  await h.start();
  owner = await h.newUser('صاحبِ دو دستگاه', 'shop');
  shopId = (await h.post('/api/shop', { name: 'دکانِ دو دستگاه' }, { token: owner.accessToken })).body.shop.id;
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  staff = await h.newUser('فروشندهٔ دو دستگاه', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  other = await h.newUser('صاحبِ دکانِ دیگر', 'shop');
  otherShop = (await h.post('/api/shop', { name: 'دکانِ دیگرِ دو دستگاه' }, { token: other.accessToken })).body.shop.id;
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: GET همان پاسخِ پیشین؛ گزارش ۴۰۴ پیش از بدنه؛ بی held_sync ۴۰۳', async () => {
  let r = await twice(staff, 'HELD1', { nonsense: true });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'held_sync_disabled');
  await setFlag(shopId, 'shop.held_sync', true);
  resetAll();
  const g = await h.get(`${BASE}?since=0`, { token: staff.accessToken });
  assert.equal(g.status, 200);
  assert.equal('auto_cancel' in g.body, false);
  r = await twice(staff, 'HELD1', { nonsense: true });
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'held_twice_off');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE kind='held_twice'")).n, 0);
});

test('روشن: auto_cancel، گزارش ⇒ یک ردیفِ صندوقِ صاحب؛ تکرار همان؛ نه «دوباره ثبت»', async () => {
  await setFlag(shopId, 'shop.held_twice_cancel', true);
  resetAll();
  const g = await h.get(`${BASE}?since=0`, { token: staff.accessToken });
  assert.equal(g.body.auto_cancel, true);
  const ops = (await one('SELECT COUNT(*)::int AS n FROM oplog')).n;

  const r = await twice(staff);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.created, true);
  const again = await twice(staff);
  assert.equal(again.status, 200);
  assert.equal(again.body.id, r.body.id);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE kind='held_twice'")).n, 1);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM oplog')).n, ops, 'سرور با گزارش هیچ پولی/ردیفی نمی‌سازد');

  resetAll();
  const box = await h.get('/api/shop/conflict-inbox', { token: owner.accessToken });
  assert.equal(box.status, 200, JSON.stringify(box.body));
  const row = box.body.conflicts.find((c) => c.id === r.body.id);
  assert.ok(row, JSON.stringify(box.body));
  assert.equal(row.kind, 'held_twice');
  assert.equal(row.reason, 'held_twice');
  assert.equal(row.doc.rowId, 'SALE2B1');
  assert.equal(row.amountMinor, 32000);
  assert.equal(row.number, 12);
  assert.equal(row.resubmittable, false);
  assert.equal(row.fixable, true);
  assert.deepEqual(row.items, [{ name: 'برنج', qtyMilli: 2000 }]);
  const audit = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='held.twice_cancelled' ORDER BY created_at DESC LIMIT 1", [shopId]);
  assert.ok(audit, 'سابقه');

  //  «دوباره ثبت» ندارد؛ «کنار بگذار» با دلیل می‌بندد
  resetAll();
  const rs = await h.post(`/api/shop/conflict-inbox/${r.body.id}/resubmit`, {}, { token: owner.accessToken });
  assert.equal(rs.status, 409, JSON.stringify(rs.body));
  assert.equal(rs.body.error.code, 'not_resubmittable');
  resetAll();
  const ds = await h.post(`/api/shop/conflict-inbox/${r.body.id}/dismiss`, { reason: 'دیدم؛ درست است' }, { token: owner.accessToken });
  assert.equal(ds.status, 200, JSON.stringify(ds.body));
  //  بسته شد ⇒ گزارشِ تازهٔ همان فروش ردیفِ تازه می‌سازد
  const third = await twice(staff, 'HELD1', body({ cancelled: false }));
  assert.equal(third.status, 201);
  assert.notEqual(third.body.id, r.body.id);
});

test('شکلِ بد ۴۰۰، بی توکن ۴۰۱، دکانِ دیگر نمی‌بیند، سقفِ نرخ ۴۲۹', async () => {
  for (const b of [
    body({ sale_id: '' }), body({ sale_id: 'a b' }), body({ cancelled: 'yes' }), body({ total_minor: -1 }), body({ items: [] }),
    body({ kept_id: 'SALE2B1' }), body({ items: [{ productId: 'p', qtyMilli: 1.5, unitPriceMinor: 1, totalMinor: 1 }] }),
    body({ items: Array.from({ length: 201 }, () => ({ productId: 'p', qtyMilli: 1, unitPriceMinor: 1, totalMinor: 1 })) }),
  ]) {
    const r = await twice(staff, 'HELD2', b);
    assert.equal(r.status, 400, JSON.stringify(b).slice(0, 80));
    assert.equal(r.body.error.code, 'bad_held_twice');
  }
  assert.equal((await twice(staff, 'bad id!')).status, 400);
  assert.equal((await twice(null)).status, 401);

  await setFlag(otherShop, 'shop.held_sync', true);
  await setFlag(otherShop, 'shop.held_twice_cancel', true);
  resetAll();
  const box = await h.get('/api/shop/conflict-inbox?status=all', { token: other.accessToken });
  assert.equal(box.status, 200);
  assert.equal(box.body.conflicts.some((c) => c.kind === 'held_twice'), false);
  //  فروشنده صندوقِ صاحب را نمی‌بیند
  resetAll();
  assert.equal((await h.get('/api/shop/conflict-inbox', { token: staff.accessToken })).status, 403);

  resetAll();
  let last;
  for (let i = 0; i < 31; i++) last = await h.post(`${BASE}/HELD3/twice`, body({ sale_id: `S${i}` }), { token: staff.accessToken });
  assert.equal(last.status, 429, JSON.stringify(last.body));
  resetAll();
});
