'use strict';
/**
 * POS-27 گام ۲ — فروشِ امانی از دستگاهِ کارمند به سرور می‌رسد، بی هیچ تغییری در سرور.
 *
 * برنامه (`Layaways`) این ردیف‌ها را می‌فرستد: سندِ امانی روی ردیفِ سابقه (`type` = `layaway`، شیءِ تو در توی
 * `layaway`: مشتری، ردیف‌های سبد با قیمتِ ثابت، جمع، پیش‌پرداخت و جدولِ قسط)، پیش‌پرداخت و هر قسط «رسید» ِ قرض‌دار با
 * `kind` ِ `layaway_deposit`/`layaway_installment` و شناسهٔ ساخته از سند (`<سند>-p<n>`)، تغییرِ وضعیت روی
 * `layaway_status`، و هنگامِ لغو «برد» ِ `layaway_refund`. سرور `kind` ِ تراکنش را فهرست نمی‌کند و `auditLog` شیءِ
 * تو در تو را همان‌طور نگه می‌دارد — پس صندوق‌دار و شاگرد در v3 و v1 همه را می‌نشانند. همان شناسهٔ رسید دوباره
 * (تکرار پس از قطع) ردیفِ دوم نمی‌سازد.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');

let shopId = '';
const T = {};
let n = 0;
const oid = () => `01JLW${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push1 = (who, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].accessToken, body: { device_id: `l1-${who}`, schema_version: 1, ops },
});
const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `l3-${who}`, protocol: 3, ops },
});
const v1op = (table, rowId, fields) => ({ op_id: oid(), table, row_id: rowId, type: 'insert', ts: Date.now(), fields });
const v3op = (table, rowId, fields, tx = '') => ({ op_id: oid(), hlc: H('pos'), table, row_id: rowId, kind: 'insert', fields, tx });
const results = (r) => r.body.results.map((x) => x.reason || x.status);
const inV3 = async (table, rowId) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
))?.data;
const count3 = async (table, rowId) => Number((await one(
  `SELECT count(*)::int AS c FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
)).c);

/** همان شکلی که `Layaways.create` می‌نویسد: ۲۷٬۵۰۰ = ۵٬۵۰۰ پیش‌پرداخت + سه قسط */
const doc = (debtorId) => ({
  debtorId, kind: 'layaway', totalMinor: 2750000, depositMinor: 550000,
  lines: [
    { productId: 'p-fr', name: 'یخچال', qtyMilli: 1000, unitMinor: 2500000 },
    { productId: 'p-bl', name: 'پتو', qtyMilli: 2000, unitMinor: 125000 },
  ],
  schedule: [
    { no: 1, due: '2026-12-02', amountMinor: 733400 },
    { no: 2, due: '2027-01-01', amountMinor: 733300 },
    { no: 3, due: '2027-01-31', amountMinor: 733300 },
  ],
});

function create3(id) {
  return [
    v3op('auditLog', id, { type: 'layaway', date: '2026-11-02', amountMinor: 550000, notes: 'فروشِ امانی', layaway: doc('d-lw') }),
    v3op('transactions', `${id}-p0`, { debtorId: 'd-lw', type: 'receive', amountMinor: 550000, refId: id, kind: 'layaway_deposit' }),
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ امانی', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ امانی' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  const code = (await h.get('/api/shop/staff-code', { token: T.owner.accessToken })).body.code;
  const s = await h.newUser('شاگردِ امانی', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  T.staff = await h.signIn(s, 'shop');
  const inv = await h.post('/api/shop/invite', { role: 'cashier' }, { token: T.owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const c = await h.newUser('صندوق‌دارِ امانی', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: c.accessToken })).status));
  T.cashier = await h.signIn(c, 'shop');
});
test.after(async () => { await h.stop(); });

test('v3: سندِ امانی با شیءِ تو در تو و رسیدِ پیش‌پرداخت و قسط‌ها از صندوق‌دار و شاگرد می‌نشیند', async () => {
  for (const who of ['cashier', 'staff']) {
    const id = `lw-${who}`;
    const r = await push3(who, [
      ...create3(id),
      v3op('transactions', `${id}-p1`, { debtorId: 'd-lw', type: 'receive', amountMinor: 733400, refId: id, kind: 'layaway_installment' }),
    ]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(results(r), ['applied', 'applied', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
    const row = await inV3('auditLog', id);
    assert.deepEqual(row.layaway, doc('d-lw'), `${who}: سند همان‌طور ماند`);
    assert.equal((await inV3('transactions', `${id}-p0`)).kind, 'layaway_deposit');
    assert.equal((await inV3('transactions', `${id}-p1`)).kind, 'layaway_installment');
    assert.equal((await inV3('transactions', `${id}-p1`)).amountMinor, 733400);
  }
});

test('v3: همان شناسهٔ رسید دوباره ردیفِ دوم نمی‌سازد', async () => {
  const again = await push3('cashier', [
    v3op('transactions', 'lw-cashier-p1', { debtorId: 'd-lw', type: 'receive', amountMinor: 733400, refId: 'lw-cashier', kind: 'layaway_installment' }),
  ]);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(await count3('transactions', 'lw-cashier-p1'), 1);
  assert.equal((await inV3('transactions', 'lw-cashier-p1')).amountMinor, 733400);
});

test('v3: لغو (وضعیت و بردِ بازپرداخت) و تحویل (وضعیت با فاکتور) از صندوق‌دار می‌نشیند', async () => {
  const cancel = await push3('cashier', [
    v3op('auditLog', 'lw-cashier-x', {
      type: 'layaway_status', refId: 'lw-cashier', amountMinor: 1283400, reason: 'مشتری منصرف شد', layaway: { to: 'cancelled' },
    }),
    v3op('transactions', 'lw-cashier-r', { debtorId: 'd-lw', type: 'give', amountMinor: 1283400, refId: 'lw-cashier', kind: 'layaway_refund' }),
  ]);
  assert.deepEqual(results(cancel), ['applied', 'applied'], JSON.stringify(cancel.body));
  assert.equal((await inV3('transactions', 'lw-cashier-r')).kind, 'layaway_refund');
  assert.deepEqual((await inV3('auditLog', 'lw-cashier-x')).layaway, { to: 'cancelled' });
  const moved = await push3('cashier', [
    v3op('auditLog', 'lw-staff-d1-m', { type: 'layaway_status', refId: 'lw-staff', amountMinor: 2750000, layaway: { to: 'delivered', saleId: 'lw-staff-d1' } }),
  ]);
  assert.deepEqual(results(moved), ['applied'], JSON.stringify(moved.body));
});

test('v1: همان شکل‌ها با نام‌های قدیم از صندوق‌دار و شاگرد می‌نشیند', async () => {
  for (const who of ['cashier', 'staff']) {
    const id = `lw1-${who}`;
    const r = await push1(who, [
      v1op('auditLog', id, { type: 'layaway', date: '2026-11-02', amount: 5500, notes: 'فروشِ امانی', layaway: doc('d-lw') }),
      v1op('transactions', `${id}-p0`, { debtorId: 'd-lw', type: 'receive', amount: 5500, refId: id, kind: 'layaway_deposit' }),
      v1op('transactions', `${id}-p1`, { debtorId: 'd-lw', type: 'receive', amount: 7334, refId: id, kind: 'layaway_installment' }),
    ]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(results(r), ['applied', 'applied', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
  }
});
