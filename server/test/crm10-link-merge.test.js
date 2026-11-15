'use strict';
/**
 * CRM-10 روی Sync v3 — «ادغامِ بی ویرایشِ سند» (`CustomerMerge.merge(docs = false)` ِ برنامه)، از راهِ HTTPِ واقعی.
 *
 *   • opهای ادغام فقط `debtors` (جدولِ پایه: «آخرین نوشتن» ِ فیلد) و درجِ `auditLog`اند ⇒ همه «applied»، هیچ تعارضِ تازه‌ای
 *   • قرض و فاکتورِ مشتریِ ادغام‌شده روی سرور دست نمی‌خورند (همان `debtorId`، همان مبلغ)؛ دستگاهِ دیگر همان را Pull می‌کند
 *   • گزارشِ سنِ بدهی (`report-core.debts`، همان هستهٔ برنامه و گزارشِ صاحب) سند را زیرِ ماندگار می‌شمارد
 *   • در برابر: همان جابه‌جایی با ویرایشِ سند (راهِ v1 ِ ادغام) در v3 رد می‌شود (`append_only`) — دلیلِ این طرح
 *   • برگرداندن: خالی کردنِ `mergedInto` هم یک ویرایشِ جدولِ پایه است و مانده‌ها همان پیش از ادغام می‌شوند
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const core = require('../src/lib/report-core');

const A = 'crm10-dev-a';
const B = 'crm10-dev-b';
let owner;
let shopId;
let n = 0;
const oid = () => `01JCRM10${String(++n).padStart(18, '0')}`;
const BASE = Date.now() - 3_600_000;
let tick = 0;
const H = (node) => hlc.format({ wall: BASE + (++tick) * 10, counter: 0, node });

/** `caps: ["a00"]` همان نشانِ ساخت‌های امروزِ برنامه (`LedgerDiff.SYNC_CAPS`): null ِ صریح یعنی «پاک کرد» (`lib/newer-fields.js`) */
const push = (device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: owner.accessToken, body: { device_id: device, protocol: 3, ops, caps: ['a00'] },
});
const pull = (device, since = 0) => h.api('GET', `/api/sync/v3/pull?device_id=${device}&since=${since}`, { token: owner.accessToken });
const rowOf = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id],
);
const openConflicts = async () => (await h.api('GET', '/api/sync/v3/conflicts?status=open', { token: owner.accessToken })).body.conflicts;
/** دفترِ سرور به شکلِ دفترِ برنامه (واحدِ کوچک ⇒ افغانی) — همان ورودیِ `report-core` */
async function ledger() {
  const rows = (await query(
    `SELECT table_name, row_id, data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name IN ('debtors','transactions')`, [shopId],
  )).rows;
  const of = (t) => rows.filter((r) => r.table_name === t).map((r) => ({ id: r.row_id, ...r.data }));
  return {
    debtors: of('debtors'),
    transactions: of('transactions').map((t) => ({ ...t, amount: t.amountMinor / 100 })),
  };
}
const balances = async () => Object.fromEntries(core.debts(await ledger(), { asOf: '2026-10-20' }).customers.rows.map((r) => [r.id, r.balance]));

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ ادغام', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ ادغام' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
  const r = await push(A, [
    { op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c1', kind: 'insert', fields: { name: 'احمد', phone: '+93700123456' } },
    { op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c2', kind: 'insert', fields: { name: 'احمد کابلی', phone: '+93799555444' } },
    { op_id: oid(), hlc: H(A), table: 'transactions', row_id: 't1', kind: 'insert',
      fields: { debtorId: 'c1', type: 'give', amountMinor: 30000, date: '2026-09-01' } },
    { op_id: oid(), hlc: H(A), table: 'transactions', row_id: 't2', kind: 'insert',
      fields: { debtorId: 'c2', type: 'give', amountMinor: 100000, date: '2026-06-01' } },
    { op_id: oid(), hlc: H(A), table: 'sales', row_id: 's1', kind: 'insert',
      fields: { debtorId: 'c1', totalMinor: 30000, finalTotalMinor: 30000, paymentMethod: 'credit', status: 'completed' } },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied', 'applied', 'applied', 'applied', 'applied']);
});
test.after(async () => { await h.stop(); });

test('ادغامِ بی ویرایشِ سند: همهٔ opها applied، بی تعارض، سندها دست‌نخورده؛ دستگاهِ دیگر پیوند را می‌گیرد', async () => {
  const before = { t1: (await rowOf('transactions', 't1')).data, s1: (await rowOf('sales', 's1')).data };
  const conflicts0 = (await openConflicts()).length;
  assert.deepEqual(await balances(), { c1: 30000, c2: 100000 });

  const r = await push(A, [
    { op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c1', kind: 'update', fields: { mergedInto: 'c2' } },
    { op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c2', kind: 'update', fields: { phone2: '+93700123456' } },
    { op_id: oid(), hlc: H(A), table: 'auditLog', row_id: 'm1', kind: 'insert',
      fields: { type: 'customer_merge', refId: 'c1', date: '2026-10-19', notes: 'ادغامِ «احمد» (c1) در «احمد کابلی» (c2)' } },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied', 'applied', 'applied']);
  assert.equal((await openConflicts()).length, conflicts0, 'تعارضِ تازه‌ای ساخته نشد');
  assert.deepEqual((await rowOf('transactions', 't1')).data, before.t1);
  assert.deepEqual((await rowOf('sales', 's1')).data, before.s1);
  assert.equal((await rowOf('debtors', 'c1')).data.mergedInto, 'c2');

  //  پولِ دکان همان؛ سنِ بدهی زیرِ ماندگار (همان هستهٔ گزارشِ برنامه و صاحب)
  assert.deepEqual(await balances(), { c2: 130000 });

  const p = await pull(B, 0);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const ops = p.body.ops.filter((o) => o.table === 'debtors' && o.row_id === 'c1');
  assert.ok(ops.some((o) => o.kind === 'update' && o.effect && o.effect.set && o.effect.set.mergedInto === 'c2'), JSON.stringify(ops));
  assert.ok(!p.body.ops.some((o) => o.table === 'transactions' && o.kind !== 'insert'), 'هیچ ویرایشِ سندی به دستگاهِ دیگر نرفت');
});

test('در برابر: جابه‌جاییِ `debtorId` ِ سند (راهِ v1 ِ ادغام) در v3 رد می‌شود و سند همان می‌ماند', async () => {
  const r = await push(A, [{ op_id: oid(), hlc: H(A), table: 'transactions', row_id: 't1', kind: 'update', fields: { debtorId: 'c2' } }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.results[0].reason || r.body.results[0].status, 'append_only');
  assert.equal((await rowOf('transactions', 't1')).data.debtorId, 'c1');
});

test('برگرداندن: خالی کردنِ mergedInto (ویرایشِ جدولِ پایه) ⇒ مانده‌ها همان پیش از ادغام، بی سندِ تازه', async () => {
  const r = await push(A, [{ op_id: oid(), hlc: H(A), table: 'debtors', row_id: 'c1', kind: 'update', fields: { mergedInto: null } }]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied'], JSON.stringify(r.body));
  assert.deepEqual(await balances(), { c1: 30000, c2: 100000 });
});
