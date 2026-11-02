'use strict';
/**
 * PAY-24 گام ۲ — کارتِ هدیه و اعتبارِ فروشگاه از دستگاهِ کارمند به سرور می‌رسد، بی هیچ تغییری در سرور.
 *
 * برنامه این ردیف‌ها را با همان فاکتور (یک tx) می‌فرستد: فاکتور با ردیف‌های `payments` ِ `gift_card`/`store_credit`،
 * ردیف‌های سابقهٔ `gift_card_*` (`payRef` = کد، `refId` = فاکتور یا سندِ برگشت) و «برد»/«رسید» ِ قرض‌دار با
 * `kind` = `store_credit`. سرور روشِ پرداخت را نمی‌سنجد (`pay-core.js` در سرور صدا زده نمی‌شود)، `auditLog` و
 * `transactions` برای صندوق‌دار نوشتنی‌اند و قاعدهٔ ردیف ندارند، و هیچ‌کدام تخفیف یا لغو نیست — پس شاگرد و صندوق‌دار
 * در v1 و v3 هر دو را می‌نشانند. آزمونِ مرجع: فاکتورِ تخفیف‌دارِ شاگرد همان امروز رد می‌شود.
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
const oid = () => `01JGC${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push1 = (who, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].accessToken, body: { device_id: `g1-${who}`, schema_version: 1, ops },
});
const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `g3-${who}`, protocol: 3, ops },
});
const v1op = (table, rowId, fields) => ({ op_id: oid(), table, row_id: rowId, type: 'insert', ts: Date.now(), fields });
const v3op = (table, rowId, fields, tx) => ({ op_id: oid(), hlc: H('pos'), table, row_id: rowId, kind: 'insert', fields, tx });
const results = (r) => r.body.results.map((x) => x.reason || x.status);
const inV3 = async (table, rowId) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
))?.data;

const CODE = 'ABCDEFGHJKLM';

/** فاکتورِ ۳٬۲۵۰ ِ برنامه (v3): ۱٬۰۰۰ کارت، ۵۰۰ اعتبار، باقی نقد — با ردیف‌های همراهش در یک tx */
function sale3(id) {
  const tx = `tx.${id}`;
  return [
    v3op('sales', id, {
      totalMinor: 325000, discountMinor: 0, finalTotalMinor: 325000, paidAmountMinor: 325000, status: 'completed',
      paymentMethod: 'mixed', debtorId: 'd-gc',
      payments: [
        { method: 'cash', kind: 'cash', amount: 175000, status: 'confirmed' },
        { method: 'gift_card', kind: 'gift_card', amount: 100000, status: 'confirmed', ref: CODE, verification: 'ledger' },
        { method: 'store_credit', kind: 'store_credit', amount: 50000, status: 'confirmed', verification: 'ledger' },
      ],
    }, tx),
    v3op('auditLog', `${id}-a`, { type: 'sale', refId: id, notes: 'ثبت فروش' }, tx),
    v3op('auditLog', `${id}-g0`, { type: 'gift_card_redeem', refId: id, payRef: CODE, amountMinor: 100000, notes: 'خرج از کارت' }, tx),
    v3op('transactions', `${id}-sc`, { debtorId: 'd-gc', type: 'give', amountMinor: 50000, refId: id, kind: 'store_credit' }, tx),
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ کارت', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ کارت' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  const code = (await h.get('/api/shop/staff-code', { token: T.owner.accessToken })).body.code;
  const s = await h.newUser('شاگردِ کارت', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  T.staff = await h.signIn(s, 'shop');
  const inv = await h.post('/api/shop/invite', { role: 'cashier' }, { token: T.owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const c = await h.newUser('صندوق‌دارِ کارت', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: c.accessToken })).status));
  T.cashier = await h.signIn(c, 'shop');
});
test.after(async () => { await h.stop(); });

test('v3: فاکتورِ با کارت و اعتبار و همراه‌هایش از صندوق‌دار و شاگرد می‌نشیند', async () => {
  for (const who of ['cashier', 'staff']) {
    const id = `s-gc-${who}`;
    const r = await push3(who, sale3(id));
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(results(r), ['applied', 'applied', 'applied', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
    const s = await inV3('sales', id);
    assert.deepEqual(s.payments.map((p) => p.kind), ['cash', 'gift_card', 'store_credit']);
    assert.equal((await inV3('auditLog', `${id}-g0`)).payRef, CODE);
    assert.equal((await inV3('transactions', `${id}-sc`)).kind, 'store_credit');
  }
});

test('v3: فروشِ کارت و ردیف‌های برگشت به کارت و اعتبار (سابقه و رسید) از صندوق‌دار می‌نشیند', async () => {
  const issue = await push3('cashier', [
    v3op('auditLog', 'gc-issue-1', { type: 'gift_card_issue', payRef: CODE, amountMinor: 200000, notes: 'فروشِ کارت' }, ''),
  ]);
  assert.deepEqual(results(issue), ['applied'], JSON.stringify(issue.body));
  const back = await push3('cashier', [
    v3op('auditLog', 'rd-gc-g0', { type: 'gift_card_refund', refId: 'rd-gc', payRef: CODE, amountMinor: 30000 }, 'tx.rd-gc'),
    v3op('transactions', 'rd-gc-sc', { debtorId: 'd-gc', type: 'receive', amountMinor: 50000, refId: 'rd-gc', kind: 'store_credit' }, 'tx.rd-gc'),
  ]);
  assert.deepEqual(results(back), ['applied', 'applied'], JSON.stringify(back.body));
});

test('v1: همان شکل‌ها با نام‌های قدیم از صندوق‌دار و شاگرد می‌نشیند', async () => {
  for (const who of ['cashier', 'staff']) {
    const id = `s1-gc-${who}`;
    const r = await push1(who, [
      v1op('sales', id, {
        total: 3250, discount: 0, finalTotal: 3250, paidAmount: 3250, status: 'completed', paymentMethod: 'mixed',
        payments: [
          { method: 'cash', kind: 'cash', amount: 175000, status: 'confirmed' },
          { method: 'gift_card', kind: 'gift_card', amount: 100000, status: 'confirmed', ref: CODE, verification: 'ledger' },
          { method: 'store_credit', kind: 'store_credit', amount: 50000, status: 'confirmed', verification: 'ledger' },
        ],
      }),
      v1op('auditLog', `${id}-g0`, { type: 'gift_card_redeem', refId: id, payRef: CODE, amount: 1000 }),
      v1op('transactions', `${id}-sc`, { debtorId: 'd-gc', type: 'give', amount: 500, refId: id, kind: 'store_credit' }),
    ]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(results(r), ['applied', 'applied', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
  }
});

test('آزمونِ مرجع: فاکتورِ تخفیف‌دارِ شاگرد همان امروز رد می‌شود (کارت و اعتبار تخفیف نیستند)', async () => {
  const ops = sale3('s-gc-disc');
  ops[0].fields = { ...ops[0].fields, discountMinor: 100, finalTotalMinor: 324900 };
  const r = await push3('staff', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].reason, 'discount_not_allowed', JSON.stringify(r.body));
  assert.equal(await inV3('auditLog', 's-gc-disc-g0'), undefined);
});
