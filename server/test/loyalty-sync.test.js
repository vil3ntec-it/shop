'use strict';
/**
 * PRM-13 — امتیازِ وفاداری از دستگاهِ کارمند به سرور می‌رسد، بی هیچ تغییری در سرور.
 *
 * برنامه مصرفِ امتیاز را `Sale.discount` نمی‌کند: قیمتِ ردیف‌ها پایین می‌آید (همان راهِ تخفیفِ خودکار) و فاکتور
 * `discount = 0` دارد؛ ردیف‌های سابقهٔ `loyalty_*` (`refId` = فاکتور، `payRef` = مشتری، `points` = شمار، `amount` =
 * ارزشِ مصرف) در همان tx می‌روند. `auditLog` برای صندوق‌دار و شاگرد نوشتنی است و قاعدهٔ ردیف ندارد، و قیمتِ ردیف
 * سنجیده نمی‌شود — پس شاگرد و صندوق‌دار در v1 و v3 هر دو را می‌نشانند. آزمونِ مرجع: همان فاکتور با تخفیفِ فاکتور
 * (`discount > 0`) از شاگرد همان امروز رد می‌شود.
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
const oid = () => `01JLP${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push1 = (who, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].accessToken, body: { device_id: `l1-${who}`, schema_version: 1, ops },
});
const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `l3-${who}`, protocol: 3, ops },
});
const v1op = (table, rowId, fields) => ({ op_id: oid(), table, row_id: rowId, type: 'insert', ts: Date.now(), fields });
const v3op = (table, rowId, fields, tx) => ({ op_id: oid(), hlc: H('pos'), table, row_id: rowId, kind: 'insert', fields, tx });
const results = (r) => r.body.results.map((x) => x.reason || x.status);
const inV3 = async (table, rowId) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
))?.data;

/** فاکتورِ ۱٬۵۰۰ ِ برنامه (v3) با ۳۰۰ امتیاز ⇒ ردیف‌های ۸۰۰ و ۴۰۰، فاکتور ۱٬۲۰۰ و ۱۲ امتیازِ کسب — در یک tx */
function sale3(id) {
  const tx = `tx.${id}`;
  return [
    v3op('sales', id, {
      totalMinor: 120000, discountMinor: 0, finalTotalMinor: 120000, paidAmountMinor: 120000, status: 'completed',
      paymentMethod: 'cash', debtorId: 'd-lp',
    }, tx),
    v3op('saleItems', `${id}-i1`, { saleId: id, productId: 'p1', quantityMilli: 1000, unitPriceMinor: 80000, totalPriceMinor: 80000, purchasePriceMinor: 70000 }, tx),
    v3op('saleItems', `${id}-i2`, { saleId: id, productId: 'p2', quantityMilli: 1000, unitPriceMinor: 40000, totalPriceMinor: 40000, purchasePriceMinor: 30000 }, tx),
    v3op('auditLog', `${id}-a`, { type: 'sale', refId: id, notes: 'ثبت فروش' }, tx),
    v3op('auditLog', `${id}-lp-s`, { type: 'loyalty_spend', refId: id, payRef: 'd-lp', points: 300, amountMinor: 30000, notes: 'مصرفِ امتیاز' }, tx),
    v3op('auditLog', `${id}-lp-e`, { type: 'loyalty_earn', refId: id, payRef: 'd-lp', points: 12, amountMinor: 0, notes: 'کسبِ امتیاز' }, tx),
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ امتیاز', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ امتیاز' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  const code = (await h.get('/api/shop/staff-code', { token: T.owner.accessToken })).body.code;
  const s = await h.newUser('شاگردِ امتیاز', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  T.staff = await h.signIn(s, 'shop');
  const inv = await h.post('/api/shop/invite', { role: 'cashier' }, { token: T.owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const c = await h.newUser('صندوق‌دارِ امتیاز', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: c.accessToken })).status));
  T.cashier = await h.signIn(c, 'shop');
});
test.after(async () => { await h.stop(); });

test('v3: فاکتورِ با مصرفِ امتیاز (قیمتِ ردیف، بی تخفیفِ فاکتور) و ردیف‌های امتیاز از صندوق‌دار و شاگرد می‌نشیند', async () => {
  for (const who of ['cashier', 'staff']) {
    const id = `s-lp-${who}`;
    const r = await push3(who, sale3(id));
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(results(r), ['applied', 'applied', 'applied', 'applied', 'applied', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
    const spend = await inV3('auditLog', `${id}-lp-s`);
    assert.equal(spend.type, 'loyalty_spend');
    assert.equal(spend.points, 300);
    assert.equal(spend.payRef, 'd-lp');
    assert.equal((await inV3('auditLog', `${id}-lp-e`)).points, 12);
    assert.equal((await inV3('saleItems', `${id}-i1`)).unitPriceMinor, 80000);
  }
});

test('v3: ردیف‌های برگشتِ امتیاز (برگشت و لغو) از صندوق‌دار می‌نشیند', async () => {
  const back = await push3('cashier', [
    v3op('auditLog', 'rd-lp-lp-s', { type: 'loyalty_spend_back', refId: 's-lp-cashier', payRef: 'd-lp', points: 100, amountMinor: 10000 }, 'tx.rd-lp'),
    v3op('auditLog', 'rd-lp-lp-e', { type: 'loyalty_earn_back', refId: 's-lp-cashier', payRef: 'd-lp', points: 4, amountMinor: 0 }, 'tx.rd-lp'),
  ]);
  assert.deepEqual(results(back), ['applied', 'applied'], JSON.stringify(back.body));
});

test('v1: همان شکل‌ها با نام‌های قدیم از صندوق‌دار و شاگرد می‌نشیند', async () => {
  for (const who of ['cashier', 'staff']) {
    const id = `s1-lp-${who}`;
    const r = await push1(who, [
      v1op('sales', id, { total: 1200, discount: 0, finalTotal: 1200, paidAmount: 1200, status: 'completed', paymentMethod: 'cash', debtorId: 'd-lp' }),
      v1op('saleItems', `${id}-i1`, { saleId: id, productId: 'p1', quantity: 1, unitPrice: 800, totalPrice: 800, purchasePrice: 700 }),
      v1op('auditLog', `${id}-lp-s`, { type: 'loyalty_spend', refId: id, payRef: 'd-lp', points: 300, amount: 300 }),
      v1op('auditLog', `${id}-lp-e`, { type: 'loyalty_earn', refId: id, payRef: 'd-lp', points: 12, amount: 0 }),
    ]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(results(r), ['applied', 'applied', 'applied', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
  }
});

test('آزمونِ مرجع: همان فاکتور با تخفیفِ فاکتور از شاگرد همان امروز رد می‌شود (امتیاز تخفیفِ فاکتور نیست)', async () => {
  const ops = sale3('s-lp-disc');
  ops[0].fields = { ...ops[0].fields, totalMinor: 150000, discountMinor: 30000 };
  const r = await push3('staff', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].reason, 'discount_not_allowed', JSON.stringify(r.body));
  assert.equal(await inV3('auditLog', 's-lp-disc-lp-s'), undefined);
});
