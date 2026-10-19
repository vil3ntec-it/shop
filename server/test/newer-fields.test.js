'use strict';
/**
 * تورِ ایمنیِ «فیلدِ تازه» — A00 (`src/lib/newer-fields.js`).
 *
 *   ۱) نسخه: گوشی `3.2.N` و کامپیوتر `1.0.N` هر کدام با کفِ خودشان؛ وب (`3.2`)،
 *      نسخهٔ نخوانا و بی‌سرآیند «نمی‌شناسد».
 *   ۲) فقط `null` ِ صریح برای کلیدِ فهرست‌شده کنار می‌رود؛ مقدارِ غیرِ null،
 *      کلیدِ بیرونِ فهرست و نسخهٔ تازه‌تر دست نمی‌خورند.
 *   ۳) opِ پالایش‌شده با اثرِ انگشتِ تازه از `validateOp` ِ خودِ v1 می‌گذرد؛
 *      opی که چیزی برایش نماند نمی‌رود و پاسخش در جای خودش «applied» است.
 *   ۴) پمپ و حسابِ غیرِ دکان بی هیچ کاری رد می‌شوند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const N = require('../src/lib/newer-fields');
const { validateOp, hashOf } = require('../src/lib/sync-v1');

const op = (op_id, table, type, fields, rawTable = table) => {
  const o = { op_id, table: rawTable, row_id: `${table}-1`, type, fields, ts: 1 };
  o.hash = hashOf({ table: rawTable, row_id: o.row_id, type, fields });
  return o;
};

/** میان‌افزار با درخواست و پاسخِ ساختگی؛ پاسخِ «v1» برای هر op ِ رسیده applied است */
function run(ops, { version = '3.2.400', app = 'shop', kind = 'shop' } = {}) {
  const req = { sync: { app, accountKind: kind, accountId: 'shop_1' }, body: { ops }, headers: { 'x-app-version': version } };
  let sent = null;
  const res = { json: (b) => { sent = b; return b; } };
  let passed = null;
  N.v1NewerFields(req, res, (err) => { if (err) throw err; passed = req.body.ops; });
  res.json({ ok: true, results: passed.map((o, i) => ({ op_id: o.op_id, status: 'applied', server_seq: 100 + i })) });
  return { passed, results: sent.results };
}

test('نسخه: هر خطِ برنامه با کفِ خودش', () => {
  const f = N.REGISTRY.shop.sales.channel;
  assert.equal(N.knows('3.2.522', f), true);
  assert.equal(N.knows('3.2.900', f), true);
  assert.equal(N.knows('3.3.1', f), true, 'نسخهٔ پایهٔ تازه‌تر');
  assert.equal(N.knows('3.2.521', f), false);
  assert.equal(N.knows('1.0.333', f), true, 'کامپیوتر');
  assert.equal(N.knows('1.0.300', f), false);
  assert.equal(N.knows('3.2', f), false, 'وب (بی شمارهٔ ساخت)');
  assert.equal(N.knows('', f), false);
  assert.equal(N.knows('2.14.3', f), false, 'خطی که کف ندارد');
  assert.equal(N.knows('3.2.99999', null), false, 'هیچ نسخهٔ منتشرشده‌ای نمی‌شناسد');
});

test('فقط null ِ صریحِ کلیدِ فهرست‌شده', () => {
  assert.deepEqual(N.staleNulls('shop', 'sales', { orderId: null, payments: [], status: null, channel: null }, '3.2.400'),
    ['orderId', 'channel']);
  assert.deepEqual(N.staleNulls('shop', 'sales', { channel: null }, '3.2.600'), []);
  assert.deepEqual(N.staleNulls('shop', 'debtors', { creditLimit: null }, '3.2.465'), ['creditLimit']);
  assert.deepEqual(N.staleNulls('shop', 'debtors', { creditLimit: null }, '3.2.466'), []);
  assert.deepEqual(N.staleNulls('pump', 'sales', { orderId: null }, '1.0.0'), []);
});

test('opِ پالایش‌شده از validateOp می‌گذرد و پاسخ‌ها سرِ جایشان', () => {
  const ops = [
    op('a', 'products', 'insert', { name: 'برنج' }),
    op('b', 'sales', 'update', { status: 'cancelled', orderId: null, payments: null }),
    op('c', 'sales', 'update', { orderId: null, orderNo: null }),
    op('d', 'returns', 'update', { channel: null, reason: 'x' }, 'Returns'),   //  نامِ خام ⇐ اثرِ انگشت روی همان
    op('e', 'sales', 'delete', null),
  ];
  const { passed, results } = run(ops);
  assert.deepEqual(passed.map((o) => o.op_id), ['a', 'b', 'd', 'e']);
  assert.deepEqual(passed[1].fields, { status: 'cancelled' });
  assert.deepEqual(passed[2].fields, { reason: 'x' });
  for (const o of passed) assert.equal(validateOp('shop', o).ok, true, `${o.op_id}: ${validateOp('shop', o).reason}`);
  assert.equal(passed[0], ops[0], 'opِ بی null همان شیء است');
  assert.deepEqual(results.map((r) => [r.op_id, r.status]), [['a', 'applied'], ['b', 'applied'], ['c', 'applied'], ['d', 'applied'], ['e', 'applied']]);
  assert.deepEqual(results[1].ignored, ['orderId', 'payments']);
  assert.equal(results[2].reason, 'stale_null_ignored');
  assert.equal(results[2].server_seq, null, 'opِ c به v1 نرفت');
  assert.deepEqual(results[2].ignored, ['orderId', 'orderNo']);
});

test('نسخهٔ تازه‌تر می‌تواند فیلدی را که می‌شناسد پاک کند', () => {
  const ops = [op('a', 'sales', 'update', { channel: null })];
  const { passed } = run(ops, { version: '3.2.600' });
  assert.equal(passed[0], ops[0]);
});

test('پمپ و حسابِ غیرِ دکان دست نمی‌خورند', () => {
  const ops = [op('a', 'sales', 'update', { orderId: null })];
  for (const ctx of [{ app: 'pump', kind: 'station' }, { app: 'shop', kind: 'station' }]) {
    const req = { sync: { app: ctx.app, accountKind: ctx.kind }, body: { ops }, headers: {} };
    let called = false;
    N.v1NewerFields(req, { json: () => {} }, () => { called = true; });
    assert.equal(called, true);
    assert.equal(req.body.ops, ops);
  }
});
