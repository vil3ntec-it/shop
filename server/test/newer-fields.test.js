'use strict';
/**
 * تورِ ایمنیِ «فیلدی که ساختِ پیش از A00 نمی‌شناسد» — A00 (`src/lib/newer-fields.js`).
 *
 *   ۱) برنامه: نشانِ صریحِ بدنه اول — `caps: ["a00"]` (ساختِ A00 به بعد) و `client: "web"`
 *      یا `caps: ["raw"]` (وب) از تور می‌گذرند؛ بعد `X-App-Version`: گوشی `3.2.N` و کامپیوتر
 *      `1.0.N` با کفِ خودشان؛ صفحهٔ وبِ مانده در حافظه (`3.2` بی شمارهٔ ساخت)، پوستهٔ وبِ
 *      قدیمی (`2.x`) و خطِ بالاتر (`4.x`) هم می‌گذرند؛ `3.2.0`، نخوانا و بی‌سرآیند قدیمی‌ترین‌اند.
 *   ۲) ساختِ پیش از A00: فقط `null` ِ کلیدی که آن ساخت نمی‌شناسد کنار می‌رود — فیلدِ
 *      بیرونِ `PRE_A00` (تازه‌تر از A00)، یا فیلدی که کفش بالاتر از آن ساخت است (فیلدِ
 *      ناتهیِ `active` ِ ۳.۲.۳۰۰ که null اش Pull ِ ساختِ تازه را می‌انداخت).
 *   ۳) opِ پالایش‌شدهٔ v1 با اثرِ انگشتِ تازه از `validateOp` ِ خودِ v1 می‌گذرد؛ opی که
 *      چیزی برایش نماند نمی‌رود و پاسخش در جای خودش «applied» است.
 *   ۴) v3: نامِ با پسوندِ واحد (`creditLimitMinor`) همان فیلدِ دفتر است.
 *   ۵) پمپ و حسابِ غیرِ دکان بی هیچ کاری رد می‌شوند.
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
const v3 = (op_id, table, kind, fields) => ({ op_id, table, row_id: `${table}-1`, kind, fields, hlc: 'x' });

/** میان‌افزار با درخواست و پاسخِ ساختگی؛ پاسخِ «v1» برای هر op ِ رسیده applied است */
function run(ops, { version = '3.2.400', app = 'shop', kind = 'shop', extra = {} } = {}) {
  const req = { sync: { app, accountKind: kind, accountId: 'shop_1' }, body: { ops, ...extra }, headers: { 'x-app-version': version } };
  let sent = null;
  const res = { json: (b) => { sent = b; return b; } };
  let passed = null;
  N.v1NewerFields(req, res, (err) => { if (err) throw err; passed = req.body.ops; });
  res.json({ ok: true, results: passed.map((o, i) => ({ op_id: o.op_id, status: 'applied', server_seq: 100 + i })) });
  return { passed, results: sent.results };
}

test('برنامه: نشانِ صریحِ بدنه، بعد X-App-Version', () => {
  assert.deepEqual(N.clientOf('3.2.0', { caps: ['a00'] }), { bypass: 'a00' }, 'نشانِ A00 بر نسخه می‌چربد');
  assert.deepEqual(N.clientOf('', { caps: ['x', 'a00'] }), { bypass: 'a00' });
  assert.deepEqual(N.clientOf('', { client: 'web' }), { bypass: 'web' });
  assert.deepEqual(N.clientOf('3.2.100', { caps: ['raw'] }), { bypass: 'web' });
  assert.deepEqual(N.clientOf('3.2.100', { caps: 'a00' }), { line: 'phone', v: '3.2.100' }, 'caps باید فهرست باشد');
  assert.deepEqual(N.clientOf('3.2'), { bypass: 'web-cached' }, 'صفحهٔ وبِ پیش از نشان');
  assert.deepEqual(N.clientOf('2.0.5'), { bypass: 'web-wrapper' }, 'پوستهٔ وبِ قدیمی');
  assert.deepEqual(N.clientOf('4.0.12'), { bypass: 'newer-line' });
  assert.deepEqual(N.clientOf('3.2.400'), { line: 'phone', v: '3.2.400' });
  assert.deepEqual(N.clientOf('3.3.1'), { line: 'phone', v: '3.3.1' }, 'بی نشان، فقط کف‌ها');
  assert.deepEqual(N.clientOf('1.0.300'), { line: 'desktop', v: '1.0.300' });
  assert.deepEqual(N.clientOf('3.2.0'), { line: 'phone', v: '3.2.0' }, 'ساختِ آزمایشی ⇒ قدیمی');
  assert.deepEqual(N.clientOf('0.9.1'), { line: null, v: '0.9.1' });
  assert.deepEqual(N.clientOf(''), { line: null, v: '' });
  assert.deepEqual(N.clientOf('nope'), { line: null, v: '' });
});

test('ساختِ پیش از A00 چه می‌شناسد', () => {
  const at = (v) => N.clientOf(v);
  assert.equal(N.knows(at('3.2.274'), 'sales', 'total'), true, 'از نخستین ساخت');
  assert.equal(N.knows(at(''), 'sales', 'debtorId'), true, 'بی‌سرآیند هم فیلدِ نخستین را می‌شناسد');
  assert.equal(N.knows(at('3.2.521'), 'sales', 'channel'), false);
  assert.equal(N.knows(at('3.2.522'), 'sales', 'channel'), true);
  assert.equal(N.knows(at('1.0.365'), 'sales', 'channel'), false);
  assert.equal(N.knows(at('1.0.366'), 'sales', 'channel'), true);
  assert.equal(N.knows(at('3.2.614'), 'returns', 'channel'), false, 'نخستین ساختِ منتشرشده 615 است');
  assert.equal(N.knows(at('3.2.299'), 'products', 'active'), false);
  assert.equal(N.knows(at('3.2.300'), 'products', 'active'), true);
  assert.equal(N.knows(at('3.2.465'), 'transactions', 'dueDate'), false);
  assert.equal(N.knows(at('3.2.0'), 'debtors', 'creditLimit'), false);
  assert.equal(N.knows(at(''), 'debtors', 'creditLimit'), false);
  assert.equal(N.knows(at('3.2.9999'), 'sales', 'orderId'), false, 'بیرونِ PRE_A00 ⇒ هیچ ساختِ پیش از A00');
  assert.equal(N.knows(at('3.2.9999'), 'sales', 'payments'), false);
  assert.equal(N.knows(N.clientOf('3.2'), 'debtors', 'creditLimit'), true, 'وب از تور می‌گذرد');
  assert.equal(N.knows(N.clientOf('3.2.400', { caps: ['a00'] }), 'sales', 'orderId'), true, 'ساختِ A00 فیلدِ تازه را می‌شناسد');
});

test('v1: opِ پالایش‌شده از validateOp می‌گذرد و پاسخ‌ها سرِ جایشان', () => {
  const ops = [
    op('a', 'products', 'insert', { name: 'برنج' }),
    op('b', 'sales', 'update', { status: 'cancelled', orderId: null, payments: null, debtorId: null }),
    op('c', 'sales', 'update', { orderId: null, orderNo: null }),
    op('d', 'returns', 'update', { channel: null, reason: 'x' }, 'Returns'),   //  نامِ خام ⇐ اثرِ انگشت روی همان
    op('e', 'sales', 'delete', null),
    op('f', 'products', 'update', { active: null, unitFactor: null }),
  ];
  const { passed, results } = run(ops, { version: '3.2.290' });
  assert.deepEqual(passed.map((o) => o.op_id), ['a', 'b', 'd', 'e']);
  assert.deepEqual(passed[1].fields, { status: 'cancelled', debtorId: null }, 'debtorId را همهٔ ساخت‌ها می‌شناسند');
  assert.deepEqual(passed[2].fields, { reason: 'x' });
  for (const o of passed) assert.equal(validateOp('shop', o).ok, true, `${o.op_id}: ${validateOp('shop', o).reason}`);
  assert.equal(passed[0], ops[0], 'opِ بی null همان شیء است');
  assert.deepEqual(results.map((r) => [r.op_id, r.status]),
    [['a', 'applied'], ['b', 'applied'], ['c', 'applied'], ['d', 'applied'], ['e', 'applied'], ['f', 'applied']]);
  assert.deepEqual(results[1].ignored, ['orderId', 'payments']);
  assert.equal(results[2].reason, 'stale_null_ignored');
  assert.equal(results[2].server_seq, null, 'opِ c به v1 نرفت');
  assert.deepEqual(results[5].ignored, ['active', 'unitFactor'], 'null ِ فیلدِ ناتهی از ساختِ پیش از 3.2.300');
});

test('ساختی که فیلد را می‌شناسد، و وب، می‌توانند پاکش کنند', () => {
  const ops = [op('a', 'sales', 'update', { channel: null }), op('b', 'debtors', 'update', { creditLimit: null })];
  assert.equal(run(ops, { version: '3.2.600' }).passed[0], ops[0]);
  assert.equal(run(ops, { version: '3.2.600' }).passed[1], ops[1]);
  const web = run(ops, { version: '3.2' });
  assert.equal(web.passed, ops, 'وبِ مانده در حافظه: دستِ نخورده');
  assert.equal(run(ops, { version: '', extra: { client: 'web' } }).passed, ops, 'وب با نشانِ client');
  assert.equal(run(ops, { version: '3.2.100', extra: { caps: ['a00'] } }).passed, ops, 'ساختِ A00 با نشانِ caps');
  assert.deepEqual(run(ops, { version: '1.0.200' }).passed.map((o) => o.op_id), [], 'کامپیوترِ پیش از کفِ هر دو');
});

test('v3: پسوندِ واحد و opِ تهی‌شده', () => {
  const ops = [
    v3('a', 'debtors', 'update', { creditLimitMinor: null, name: 'n' }),
    v3('b', 'sales', 'update', { orderId: null }),
    v3('c', 'products', 'update', { salePriceMinor: null }),
    v3('d', 'sales', 'cancel', undefined),
  ];
  const fl = N.filterOps(ops, { version: '3.2.400', shape: 'v3' });
  assert.deepEqual(fl.ops.map((o) => o.op_id), ['a', 'c', 'd']);
  assert.deepEqual(fl.ops[0].fields, { name: 'n' });
  assert.equal(fl.ops[1], ops[2], 'salePrice را همهٔ ساخت‌ها می‌شناسند');
  const merged = N.mergeResults(fl, fl.ops.map((o) => ({ op_id: o.op_id, status: 'applied' })));
  assert.deepEqual(merged.map((r) => [r.op_id, r.reason || '', (r.ignored || []).join()]),
    [['a', '', 'creditLimitMinor'], ['b', 'stale_null_ignored', 'orderId'], ['c', '', ''], ['d', '', '']]);
  assert.equal(N.filterOps(ops, { version: '3.2.466', shape: 'v3' }).ops.length, 3, 'سقفِ اعتبار از 3.2.466');
  assert.equal(N.filterOps(ops, { version: '3.2', shape: 'v3' }).ops, ops);
  assert.equal(N.filterOps(ops, { version: '3.2.100', shape: 'v3', caps: ['a00'] }).ops, ops);
  assert.equal(N.filterOps(ops, { version: '3.2.100', shape: 'v3', client: 'web' }).ops, ops);
  //  کلیدِ ناشناخته با پسوندِ واحد همان ناشناخته است
  assert.deepEqual(N.filterOps([v3('x', 'products', 'update', { futureMinor: null, name: 'n' })],
    { version: '3.2.400', shape: 'v3' }).ops[0].fields, { name: 'n' });
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
