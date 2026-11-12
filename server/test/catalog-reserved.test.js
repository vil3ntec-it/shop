'use strict';
/**
 * POS-05/INV-03 — `GET /api/shop/catalog/reserved` (`stock-reserve.posView`): نگه‌داشته‌های فعالِ سفارشِ آنلاین برای کاشیِ صندوق.
 *
 * از راهِ واقعی (همان شکلِ stock-reserve.test.js): کالا و فروش با `POST /api/sync/v1/push`، سفارش با `POST /api/shop/orders`.
 * آن‌چه قفل می‌شود:
 *   ⛔ پرچم‌ها: بی `shop.catalog` ⇒ ۴۰۳ `catalog_disabled`؛ بی `shop.stock_reserve` (و R6: ردیفِ باز با پرچمِ خاموش) ⇒ `enabled:false`، هیچ.
 *   ⛔ فقط نگه‌داشتهٔ نرمِ نگذشته: گذشته، ردشده و «انجام‌شده» (فروشِ تحویلِ `o-…` رسید) نه؛ کلیدِ هر ردیف = `o-<سفارش>-<قلم>`.
 *   ⛔ نقش: انباردار (`stock.view` بی `orders.view`) ⇒ ۴۰۳؛ صندوق‌دار و حسابدار ⇒ ۲۰۰؛ بی دکان ⇒ ۴۰۳ `no_shop`.
 *   ⛔ دکانِ دیگر: هر دکان فقط نگه‌داشته‌های خودش؛ `shopId` ِ درخواست نادیده.
 *   ⛔ سقفِ نرخ: سی‌ویکمین درخواستِ یک کاربر در دقیقه ⇒ ۴۲۹.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

let admin = '';

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
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

// ── کمکی‌ها (همان شکلِ stock-reserve.test.js) ─────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { token: s.accessToken, shopId: made.body.shop.id, device: `dev-cr-${made.body.shop.id}`.slice(0, 60) };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]
  );
  return { token: u.accessToken };
}
async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function enable(s, { reserve = true } = {}) {
  await live(s.shopId, 'shop.orders', true);
  await live(s.shopId, 'shop.catalog', true);
  if (reserve) await live(s.shopId, 'shop.stock_reserve', true);
}

let opN = 0;
const opId = () => `01JCR${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
const entry = (id, pid, units) => op('insert', 'warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});
const sale = (id, items) => [
  op('insert', 'sales', id, { status: 'completed', total: 0, createdAt: Date.now() }),
  ...items.map(([iid, pid, q]) => op('insert', 'saleItems', iid, {
    saleId: id, productId: pid, quantity: q, returnedQty: 0, unitPrice: 50, createdAt: Date.now(),
  })),
];

let keyN = 0;
const fresh = () => `cat-rsv-${++keyN}-${Date.now()}`;
const line = (pid, qtyMilli) => ({ productId: pid, name: `کالای ${pid}`, unit: 'دانه', qtyMilli, unitPriceMinor: 5000 });
async function order(s, lines) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری' }, lines,
  }, { token: s.token, headers: { 'Idempotency-Key': fresh() } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order.id;
}
const go = async (s, id, to, extra = {}) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...extra }, { token: s.token });
  assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
};
const refresh = async (s) => assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
const reserved = (token, q = '') => h.get(`/api/shop/catalog/reserved${q}`, { token });
async function stocked(s, pid, units) {
  await push(s, [product(pid), entry(`${pid}-e`, pid, units)]);
  await refresh(s);
}

// ════════════════════════════════════════════════════════════════════

test('⛔ پرچم‌ها: بی کاتالوگ ۴۰۳؛ بی `shop.stock_reserve` و R6 (ردیفِ باز، پرچمِ خاموش) ⇒ enabled:false و هیچ', async () => {
  const s = await shop('رزروِ-صندوق-پرچم');
  let r = await reserved(s.token);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'catalog_disabled');

  await enable(s, { reserve: false });
  r = await reserved(s.token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.enabled, r.body.lines, r.body.truncated], [false, [], false]);

  await live(s.shopId, 'shop.stock_reserve', true);
  await stocked(s, 'p1', 3);
  await order(s, [line('p1', 1000)]);
  assert.equal((await reserved(s.token)).body.lines.length, 1);
  await live(s.shopId, 'shop.stock_reserve', false);
  r = await reserved(s.token);
  assert.deepEqual([r.body.enabled, r.body.lines], [false, []], 'R6: نگه‌داشتهٔ باز با پرچمِ خاموش اثری ندارد');
});

test('POS-05/INV-03: فقط نگه‌داشتهٔ فعال با کلیدِ فروشِ تحویل؛ گذشته، ردشده و انجام‌شده بیرون؛ قبول ⇒ بی مهلت', async () => {
  const s = await shop('رزروِ-صندوق-روشن');
  await enable(s);
  await stocked(s, 'a1', 5);
  await stocked(s, 'b1', 5);
  const o1 = await order(s, [line('a1', 2000), line('b1', 500)]);
  const o2 = await order(s, [line('a1', 1000)]);

  const t0 = Date.now();
  let r = await reserved(s.token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.enabled, true);
  assert.ok(Math.abs(r.body.now - t0) < 60_000, 'ساعتِ سرور در پاسخ');
  const key = (l) => `${l.saleItemId}|${l.productId}|${l.qtyMilli}`;
  assert.deepEqual(r.body.lines.map(key).sort(), [`o-${o1}-1|a1|2000`, `o-${o1}-2|b1|500`, `o-${o2}-1|a1|1000`].sort());
  for (const l of r.body.lines) assert.ok(l.expiresAt > r.body.now, 'نگه‌داشتهٔ پیش از قبول مهلت دارد');

  //  گذشته (پیش از جارو) بیرون
  await query(`UPDATE stock_reservations SET expires_at=$3 WHERE shop_id=$1 AND order_id=$2 AND status='held'`, [s.shopId, o2, Date.now() - 1]);
  r = await reserved(s.token);
  assert.deepEqual(r.body.lines.map((l) => l.saleItemId).sort(), [`o-${o1}-1`, `o-${o1}-2`]);

  //  قبول ⇒ بی مهلت تا پایان؛ رد ⇒ آزاد
  await go(s, o1, 'accepted');
  r = await reserved(s.token);
  assert.deepEqual(r.body.lines.map((l) => l.expiresAt), [null, null]);
  const o3 = await order(s, [line('b1', 1000)]);
  assert.equal((await reserved(s.token)).body.lines.length, 3);
  await go(s, o3, 'rejected', { reason: 'busy' });
  assert.equal((await reserved(s.token)).body.lines.length, 2, 'رد ⇒ آزاد');

  //  تحویل + فروشِ تحویلِ برنامه (`o-<سفارش>-<قلم>`) ⇒ «انجام‌شده» ⇒ بیرون
  for (const to of ['picking', 'packed', 'ready_for_pickup', 'picked_up']) await go(s, o1, to);
  assert.equal((await reserved(s.token)).body.lines.length, 2, 'پس از «مشتری گرفت» تا رسیدنِ فروش هنوز نگه‌داشته');
  await push(s, sale(`o-${o1}`, [[`o-${o1}-1`, 'a1', 2], [`o-${o1}-2`, 'b1', 0.5]]));
  await refresh(s);
  r = await reserved(s.token);
  assert.deepEqual(r.body.lines, [], JSON.stringify(r.body));
});

test('⛔ ۴۰۳ و دکانِ دیگر: انباردار رد؛ صندوق‌دار/حسابدار بله؛ بی دکان رد؛ هر دکان فقط نگه‌داشته‌های خودش', async () => {
  const A = await shop('رزروِ-صندوق-الف');
  const B = await shop('رزروِ-صندوق-ب');
  await enable(A);
  await enable(B);
  await stocked(A, 'x1', 4);
  await stocked(B, 'y1', 4);
  const oa = await order(A, [line('x1', 1000)]);
  const ob = await order(B, [line('y1', 3000)]);

  const keeper = await member(A, 'storekeeper', 'انباردارِ الف');
  let r = await reserved(keeper.token);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'permission_denied');
  assert.equal(r.body.lines, undefined);

  for (const role of ['cashier', 'accountant', 'staff', 'manager']) {
    r = await reserved((await member(A, role, `${role}ِ الف`)).token);
    assert.equal(r.status, 200, `${role}: ${JSON.stringify(r.body)}`);
    assert.deepEqual(r.body.lines.map((l) => l.saleItemId), [`o-${oa}-1`]);
  }

  const loner = await h.newUser('بی‌دکان', 'shop');
  r = await reserved(loner.accessToken);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'no_shop');

  //  دکانِ دیگر: shopId ِ درخواست نادیده؛ هر کدام فقط خودش
  r = await reserved(B.token, `?shopId=${A.shopId}`);
  assert.deepEqual(r.body.lines.map((l) => `${l.saleItemId}|${l.productId}`), [`o-${ob}-1|y1`]);
  r = await reserved(A.token, `?shopId=${B.shopId}`);
  assert.deepEqual(r.body.lines.map((l) => `${l.saleItemId}|${l.productId}`), [`o-${oa}-1|x1`]);
  assert.equal((await reserved('')).status, 401);
});

test('⛔ سقفِ نرخ: سی‌ویکمین درخواستِ یک کاربر در دقیقه ⇒ ۴۲۹؛ کاربرِ دیگرِ همان دکان جدا', async () => {
  const s = await shop('رزروِ-صندوق-نرخ');
  await enable(s);
  const cashier = await member(s, 'cashier', 'صندوق‌دارِ نرخ');
  for (let i = 0; i < 30; i++) assert.equal((await reserved(cashier.token)).status, 200, `درخواستِ ${i + 1}`);
  const r = await reserved(cashier.token);
  assert.equal(r.status, 429, JSON.stringify(r.body));
  assert.equal((await reserved(s.token)).status, 200, 'سقف برای هر کاربر جداست');
});
