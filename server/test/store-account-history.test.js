'use strict';
/**
 * «حسابِ من» ِ ویترین (CRM-13) — `GET /api/store/:slug/account/history…` (`lib/store-account-history.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموشِ `shop.storefront.account_history` ⇒ همان پاسخِ مسیرِ ناشناخته و `GET /:slug` بی کلیدِ تازه.
 *   ۲) فقط دادهٔ خودِ مشتری: شمارهٔ دیگر هیچ نمی‌بیند؛ قرض‌دارِ همان شماره در دکانِ دیگر هم نه.
 *   ۳) تطبیقِ دقیقِ شماره پس از یکدست‌سازی (رقمِ فارسی، فاصله، 07…/0093…)؛ رقمِ آخرِ دیگر، کدِ کشورِ دیگر،
 *      شمارهٔ دوم و قرض‌دارِ ادغام‌شده نه.
 *   ۴) هیچ نامِ فروشنده، قیمتِ خرید، یادداشت، نام/شناسهٔ قرض‌دار یا شناسهٔ ردیف در پاسخ نیست.
 *   ۵) صفحه‌بندیِ خرید و دفتر؛ مانده با علامت؛ نشانگرِ بد ۴۰۰.
 *   ۶) اشتراکِ منقضی ⇒ ۴۰۴ `store_not_found` (SUB-10)، حتی با پرچمِ روشن.
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const H = require('../src/lib/store-account-history');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const DAY = 24 * 60 * 60 * 1000;
const FLAG = 'shop.storefront.account_history';

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

const op = (table, rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type: 'insert', fields });

async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device }, body: { device_id: s.device, schema_version: 2, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}

/** دکانِ روشن با ویترین؛ `on` = پرچمِ «حسابِ من» */
async function liveStore(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, device: `dev-${uniq()}` };
  await setFlag(s.shopId, 'shop.orders', true);
  await setFlag(s.shopId, 'shop.storefront', true);
  if (on) await setFlag(s.shopId, FLAG, true);
  s.slug = `h-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return s;
}

/** بلیتِ شمارهٔ تأییدشده (همان راهِ «سفارش‌های من») */
async function ticket(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return { headers: { 'X-Store-Ticket': c.body.ticket } };
}

const get = (slug, path, auth) => { resetAll(); return h.get(`/api/store/${slug}/account/history${path}`, auth); };
const AT = Date.UTC(2026, 9, 20, 8, 0);

/** قرض‌دار با دو فروشِ حضوری (یکی نسیه)، یک فروشِ لغوشده، یک فروشِ سفارشِ آنلاین و دو ردیفِ دفتر */
async function seedCustomer(s, id, phone, extra = {}) {
  await push(s, [
    op('debtors', id, { name: 'نامِ قرض‌دارِ محرمانه', phone, notes: 'یادداشتِ داخلیِ دکان', createdAt: AT, ...extra }),
    op('sales', `${id}-s1`, { invoiceNumber: 41, series: 'B1', total: 500, discount: 0, finalTotal: 500, paymentMethod: 'cash', paidAmount: 500,
      remaining: 0, status: 'completed', createdAt: AT + 1000, date: '2026-10-20', seller: 'احمدِ فروشنده', debtorId: id }),
    op('saleItems', `${id}-s1-i1`, { saleId: `${id}-s1`, productId: '', name: 'چای', quantity: 2, unitPrice: 200, purchasePrice: 77, totalPrice: 400, returnedQty: 0 }),
    op('saleItems', `${id}-s1-i2`, { saleId: `${id}-s1`, productId: '', name: 'قند', quantity: 1, unitPrice: 100, purchasePrice: 66, totalPrice: 100, returnedQty: 0 }),
    op('sales', `${id}-s2`, { invoiceNumber: 42, series: 'B1', total: 1200.5, discount: 0, finalTotal: 1200.5, paymentMethod: 'credit', paidAmount: 0,
      remaining: 1200.5, status: 'completed', createdAt: AT + 2000, date: '2026-10-21', seller: 'احمدِ فروشنده', debtorId: id }),
    op('sales', `${id}-s3`, { invoiceNumber: 43, series: 'B1', total: 90, finalTotal: 90, paymentMethod: 'cash', paidAmount: 90, status: 'cancelled',
      cancelReason: 'دلیلِ داخلی', createdAt: AT + 3000, date: '2026-10-22', seller: 'احمدِ فروشنده', debtorId: id }),
    op('sales', `o-${id}`, { invoiceNumber: 7, series: 'O', total: 300, finalTotal: 300, paymentMethod: 'cash', paidAmount: 300, status: 'completed',
      orderId: 'ord-1', createdAt: AT + 4000, date: '2026-10-23', seller: 'احمدِ فروشنده', debtorId: id }),
    op('transactions', `${id}-t1`, { debtorId: id, type: 'give', amount: 1200.5, date: '2026-10-21', notes: 'فاکتور #B1-42 — یادداشتِ داخلی',
      createdAt: AT + 2000, dueDate: '2026-11-21' }),
    op('transactions', `${id}-t2`, { debtorId: id, type: 'receive', amount: 200.25, date: '2026-10-25', notes: 'یادداشتِ داخلی', createdAt: AT + 5000 }),
  ]);
}

test('CRM-13 ⛔: پرچمِ خاموش ⇒ همان پاسخِ مسیرِ ناشناخته؛ `GET /:slug` بی کلیدِ تازه', async () => {
  const s = await liveStore('خاموش', { on: false });
  const phone = '+93799100001';
  await seedCustomer(s, 'off-d', phone);
  const auth = await ticket(s.slug, phone);
  resetAll();
  const unknown = await h.get(`/api/store/${s.slug}/account/nothing-here`, auth);
  for (const path of ['', '/purchases', '/ledger', '/online']) {
    const r = await get(s.slug, path, auth);
    assert.equal(r.status, unknown.status, path);
    assert.deepEqual(r.body, unknown.body, path);
  }
  resetAll();
  const page = await h.get(`/api/store/${s.slug}`);
  assert.equal(page.status, 200);
  assert.equal(page.body.features, undefined, 'هیچ پرچمِ دیگری روشن نیست ⇒ کلیدِ features نیست');
  await setFlag(s.shopId, FLAG, true);
  resetAll();
  const on = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(on.body.features, { accountHistory: true });
  const { features, ...rest } = on.body;
  void features;
  assert.deepEqual(Object.keys(rest), Object.keys(page.body), 'جز همان یک کلید، همان کلیدها');
});

test('CRM-13: حضوری، آنلاین و مانده — فقط مالِ همین شماره؛ شمارهٔ دیگر و دکانِ دیگر هیچ', async () => {
  const s = await liveStore('الف');
  const other = await liveStore('ب');
  const phone = '+93799100002';
  await seedCustomer(s, 'a-d', phone);
  await seedCustomer(other, 'b-d', phone);   //  همان شماره در دکانِ دیگر
  await seedCustomer(s, 'c-d', '+93799100003');   //  مشتریِ دیگرِ همین دکان

  const r = await get(s.slug, '', await ticket(s.slug, phone));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.linked, true);
  assert.equal(r.body.balanceMinor, 120050 - 20025);
  assert.deepEqual(r.body.purchases.items, [
    { invoiceNo: 'B1-42', date: '2026-10-21', at: AT + 2000, totalMinor: 120050, itemsCount: 0, kind: 'credit' },
    { invoiceNo: 'B1-41', date: '2026-10-20', at: AT + 1000, totalMinor: 50000, itemsCount: 2, kind: 'cash' },
  ], 'لغوشده و فروشِ سفارشِ آنلاین نیستند؛ مالِ مشتریِ دیگر هم نه');
  assert.equal(r.body.purchases.nextCursor, null);
  assert.deepEqual(r.body.ledger.items, [
    { kind: 'payment', amountMinor: 20025, date: '2026-10-25', at: AT + 5000, balanceAfterMinor: 100025 },
    { kind: 'debt', amountMinor: 120050, date: '2026-10-21', at: AT + 2000, dueDate: '2026-11-21', invoiceNo: 'B1-42', balanceAfterMinor: 120050 },
  ]);
  assert.deepEqual(r.body.online, { items: [], nextCursor: null });
  assert.equal('points' in r.body, false, 'امتیاز (PRM-13) هنوز نیست');

  //  شمارهٔ دیگری که دکان هیچ قرض‌داری با آن ندارد
  const stranger = await get(s.slug, '', await ticket(s.slug, '+93799100009'));
  assert.equal(stranger.status, 200);
  assert.deepEqual(stranger.body, { linked: false, balanceMinor: null, online: { items: [], nextCursor: null },
    purchases: { items: [], nextCursor: null }, ledger: { items: [], nextCursor: null } });
  for (const part of ['purchases', 'ledger']) {
    const p = await get(s.slug, `/${part}`, await ticket(s.slug, '+93799100009'));
    assert.equal(p.status, 200);
    assert.deepEqual(p.body.items, [], part);
  }
  //  بی شمارهٔ تأییدشده ⇒ ۴۰۱
  const anon = await get(s.slug, '');
  assert.equal(anon.status, 401);
  assert.equal(anon.body.error.code, 'verification_required');
  const bad = await get(s.slug, '', { headers: { 'X-Store-Ticket': 'nope', 'X-Store-Session': 'sc1.nope' } });
  assert.equal(bad.status, 401);
});

test('CRM-13: سفارشِ آنلاینِ همین شماره در «خریدهای آنلاین»', async () => {
  const s = await liveStore('آنلاین');
  const phone = '+93799100004';
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, status, fulfillment, customer_phone, total_minor, items_count, created_at, updated_at)
     VALUES ($1,$2,1,'k1','h1','storefront','new','pickup',$3,45000,2,$4,$4), ($5,$2,2,'k2','h2','storefront','new','pickup','+93799100005',9000,1,$4,$4)`,
    [newId('ord'), s.shopId, phone, now(), newId('ord')]
  ).catch((e) => { throw new Error(`ستون‌های online_orders: ${e.message}`); });
  const r = await get(s.slug, '', await ticket(s.slug, phone));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.online.items.length, 1);
  assert.equal(r.body.online.items[0].orderNo, 1);
  assert.equal(r.body.online.items[0].totalMinor, 45000);
  assert.equal(r.body.linked, false);
});

test('CRM-13: یکدست‌سازیِ شماره — دقیق، نه نزدیک', async () => {
  const s = await liveStore('شماره');
  const phone = '+93799100006';
  await push(s, [
    op('debtors', 'n-fa', { name: 'x', phone: '۰۷۹۹ ۱۰۰ ۰۰۶', createdAt: AT }),
    op('debtors', 'n-00', { name: 'x', phone: '0093-799-100-006', createdAt: AT }),
    op('debtors', 'n-near', { name: 'x', phone: '0799100007', createdAt: AT }),
    op('debtors', 'n-cc', { name: 'x', phone: '+1 799100006', createdAt: AT }),
    op('debtors', 'n-two', { name: 'x', phone: '0700000000', phone2: '0799100006', createdAt: AT }),
    op('debtors', 'n-merged', { name: 'x', phone: '0799100006', mergedInto: 'n-fa', createdAt: AT }),
    op('debtors', 'n-gone', { name: 'x', phone: '0799100006', createdAt: AT }),
  ]);
  await push(s, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'debtors', row_id: 'n-gone', type: 'delete', fields: {} }]);
  assert.deepEqual(await H.debtorsOf(s.shopId, phone), ['n-00', 'n-fa']);
  assert.deepEqual(await H.debtorsOf(s.shopId, '0799100006'), ['n-00', 'n-fa'], 'ورودیِ 07… همان');
  assert.deepEqual(await H.debtorsOf(s.shopId, '+93799100008'), []);
  assert.deepEqual(await H.debtorsOf(s.shopId, 'نامعتبر'), []);
  assert.equal(H.phoneKeyOf('+930799100006'), H.phoneKeyOf('+93799100006'));
  //  ردیف‌های هر دو قرض‌دارِ همان شماره یک حساب‌اند
  await push(s, [
    op('transactions', 'n-t1', { debtorId: 'n-fa', type: 'give', amount: 100, date: '2026-10-01', createdAt: AT }),
    op('transactions', 'n-t2', { debtorId: 'n-00', type: 'give', amount: 50, date: '2026-10-02', createdAt: AT + 1 }),
    op('transactions', 'n-t3', { debtorId: 'n-near', type: 'give', amount: 999, date: '2026-10-02', createdAt: AT + 2 }),
    op('transactions', 'n-t4', { debtorId: 'n-merged', type: 'give', amount: 999, date: '2026-10-02', createdAt: AT + 3 }),
  ]);
  const r = await get(s.slug, '/ledger', await ticket(s.slug, phone));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.balanceMinor, 15000);
  assert.deepEqual(r.body.items.map((e) => e.amountMinor), [5000, 10000]);
});

test('CRM-13 ⛔: هیچ نامِ کارمند، قیمتِ خرید، یادداشت یا شناسه‌ای بیرون نمی‌رود', async () => {
  const s = await liveStore('نشت');
  const phone = '+93799100010';
  await seedCustomer(s, 'leak-d', phone, { phone2: '0700000099', creditLimit: 5000, tags: ['برچسبِ داخلی'] });
  const auth = await ticket(s.slug, phone);
  const bodies = [await get(s.slug, '', auth), await get(s.slug, '/purchases', auth), await get(s.slug, '/ledger', auth)];
  const FORBIDDEN = new Set(['seller', 'purchasePrice', 'purchasePriceMinor', 'costMinor', 'notes', 'name', 'phone', 'phone2', 'debtorId',
    'id', 'saleId', 'deviceId', 'userId', 'shopId', 'cancelReason', 'approval', 'creditLimit', 'tags', 'receiptToken']);
  for (const r of bodies) {
    assert.equal(r.status, 200, JSON.stringify(r.body));
    const walk = (v, p) => {
      if (Array.isArray(v)) return v.forEach((x, i) => walk(x, `${p}[${i}]`));
      if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) { assert.ok(!FORBIDDEN.has(k), `${p}.${k}`); walk(x, `${p}.${k}`); }
      //  قیمتِ خرید (۷۷ و ۶۶ افغانی) به هیچ شکلی
      else assert.ok(![77, 66, 7700, 6600].includes(v), `${p} = ${v}`);
    };
    walk(r.body, '$');
    const json = JSON.stringify(r.body);
    for (const secret of ['احمد', 'فروشنده', 'محرمانه', 'یادداشت', 'داخلی', 'برچسب', 'leak-d', phone, '0700000099', s.shopId, s.device]) {
      assert.ok(!json.includes(secret), `نشت: ${secret}`);
    }
  }
});

test('CRM-13: صفحه‌بندیِ خریدِ حضوری و دفتر؛ پیوندِ فاکتور فقط با QR ِ روشن؛ نشانگرِ بد ۴۰۰', async () => {
  const s = await liveStore('صفحه');
  const phone = '+93799100011';
  const ops = [op('debtors', 'p-d', { name: 'x', phone, createdAt: AT })];
  for (let i = 1; i <= 13; i++) {
    ops.push(op('sales', `p-s${i}`, { invoiceNumber: i, finalTotal: i, total: i, paymentMethod: 'cash', paidAmount: i, status: 'completed',
      createdAt: AT + i, date: '2026-10-01', debtorId: 'p-d', receiptToken: require('../src/lib/receipt-token').encodeToken(Buffer.alloc(16, i)) }));
    ops.push(op('transactions', `p-t${i}`, { debtorId: 'p-d', type: i % 2 ? 'give' : 'receive', amount: i, date: `2026-10-${String(i).padStart(2, '0')}`, createdAt: AT + i }));
  }
  await push(s, ops);
  const auth = await ticket(s.slug, phone);
  const first = await get(s.slug, '', auth);
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.deepEqual(first.body.purchases.items.map((p) => p.invoiceNo), ['13', '12', '11', '10', '9', '8', '7', '6', '5', '4']);
  assert.equal(first.body.purchases.nextCursor, '10');
  assert.match(first.body.purchases.items[0].receipt, /^\/r\/[A-Za-z0-9]+\/[A-Za-z0-9_-]+$/);
  const next = await get(s.slug, `/purchases?cursor=${first.body.purchases.nextCursor}`, auth);
  assert.deepEqual(next.body.items.map((p) => p.invoiceNo), ['3', '2', '1']);
  assert.equal(next.body.nextCursor, null);
  const small = await get(s.slug, '/ledger?limit=5', auth);
  //  ۱ + ۳ + … + ۱۳ (give) − ۲ − ۴ − … − ۱۲ (receive) = ۴۹ − ۴۲ = ۷ افغانی
  assert.equal(small.body.balanceMinor, 700);
  assert.deepEqual(small.body.items.map((e) => e.amountMinor), [1300, 1200, 1100, 1000, 900]);
  assert.equal(small.body.items[0].balanceAfterMinor, 700);
  assert.equal(small.body.nextCursor, '5');
  const last = await get(s.slug, '/ledger?limit=5&cursor=10', auth);
  assert.deepEqual(last.body.items.map((e) => e.kind), ['give', 'receive', 'give'].map((k) => (k === 'give' ? 'debt' : 'payment')));
  assert.equal(last.body.items[2].balanceAfterMinor, 100);
  assert.equal(last.body.nextCursor, null);
  for (const c of ['abc', '-1', '1.5', '9999999']) {
    const r = await get(s.slug, `/purchases?cursor=${c}`, auth);
    assert.equal(r.status, 400, c);
    assert.equal(r.body.error.code, 'bad_cursor');
  }
  assert.equal((await get(s.slug, '/other', auth)).status, 404);
  //  QR ِ خاموشِ دکان ⇒ پیوندِ فاکتور نیست
  await query(`INSERT INTO shop_settings (shop_id, data, rev, updated_at) VALUES ($1, '{"receipt":{"qr":false}}'::jsonb, 1, 1)
    ON CONFLICT (shop_id) DO UPDATE SET data = shop_settings.data || '{"receipt":{"qr":false}}'::jsonb`, [s.shopId]);
  const noQr = await get(s.slug, '/purchases', auth);
  assert.equal(noQr.body.items.some((p) => 'receipt' in p), false);
});

test('CRM-13 ⛔ SUB-10: اشتراکِ منقضی ⇒ ۴۰۴ store_not_found، حتی با پرچمِ روشن و شمارهٔ تأییدشده', async () => {
  const s = await liveStore('اشتراک');
  const phone = '+93799100012';
  await seedCustomer(s, 'sub-d', phone);
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]);
  const auth = await ticket(s.slug, phone);
  assert.equal((await get(s.slug, '', auth)).status, 200);
  await query('UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1', [s.shopId, Date.now() - 1000]);
  for (const path of ['', '/purchases', '/ledger', '/online']) {
    const r = await get(s.slug, path, auth);
    assert.equal(r.status, 404, path);
    assert.equal(r.body.error.code, 'store_not_found', path);
  }
});
