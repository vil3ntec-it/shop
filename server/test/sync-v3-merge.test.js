'use strict';
/**
 * Sync v3 — همگراییِ ادغام (SYN-06): **هر ترتیبِ رسیدن ⇒ همان نتیجه**.
 * خالص است: `merge` بی دیتابیس، روی همهٔ جایگشت‌های چند op.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { merge, validate } = require('../src/lib/sync-v3');
const hlc = require('../src/lib/hlc');

const T = 1_760_000_000_000;
const at = (k, node) => hlc.format({ wall: T + k * 1000, counter: 0, node });

function permutations(xs) {
  if (xs.length <= 1) return [xs];
  const out = [];
  xs.forEach((x, i) => {
    for (const rest of permutations([...xs.slice(0, i), ...xs.slice(i + 1)])) out.push([x, ...rest]);
  });
  return out;
}

/** opها را به این ترتیب روی یک ردیف می‌نشاند؛ حالِ نهایی را برمی‌گرداند. */
function run(order) {
  let row = null;
  let seq = 0;
  for (const { raw, device } of order) {
    const v = validate(raw, 'v3');
    assert.equal(v.ok, true, v.reason);
    const res = merge(row, { ...v.op, client_hlc: v.op.hlc }, { seq: ++seq, deviceId: device, cursor: 0, t: 1 });
    if (res.row) row = res.row;
  }
  return { alive: !row.deleted_at, data: row.data };
}

function op(n, kind, k, device, fields, extra = {}) {
  return {
    device,
    raw: { op_id: `op-${n}`, hlc: at(k, device), table: extra.table || 'products', row_id: 'r', kind, ...(fields ? { fields } : {}), ...extra },
  };
}

test('اطلاعاتِ پایه: ۷۲۰ ترتیبِ رسیدن ⇒ یک نتیجه (ویرایشِ هم‌زمان، حذف، زنده شدن)', () => {
  const ops = [
    op(1, 'insert', 1, 'a', { name: 'a', salePriceMinor: 1 }),
    op(2, 'update', 3, 'b', { name: 'b' }),
    op(3, 'update', 2, 'c', { salePriceMinor: 5 }),
    op(4, 'delete', 4, 'a'),
    op(5, 'update', 6, 'b', { name: 'c' }),
    op(6, 'delete', 5, 'c'),
  ];
  const all = permutations(ops);
  assert.equal(all.length, 720);
  const expected = { alive: true, data: { name: 'c', salePriceMinor: 5 } };
  for (const order of all) assert.deepEqual(run(order), expected, order.map(o => o.raw.op_id).join(','));
});

test('اطلاعاتِ پایه: حذفِ آخر برنده است؛ ویرایشِ دیررسِ قدیمی زنده نمی‌کند', () => {
  const ops = [
    op(1, 'insert', 1, 'a', { name: 'a', salePriceMinor: 1 }),
    op(2, 'update', 3, 'b', { name: 'b' }),
    op(3, 'update', 2, 'c', { salePriceMinor: 5 }),
    op(4, 'delete', 4, 'a'),
  ];
  for (const order of permutations(ops)) {
    assert.deepEqual(run(order), { alive: false, data: { name: 'b', salePriceMinor: 5 } });
  }
});

test('سندِ مالی: درج، دو دلتا و لغو با هر ترتیب ⇒ همان سند', () => {
  const ops = [
    op(1, 'insert', 1, 'a', { totalMinor: 100, paidAmountMinor: 10 }, { table: 'sales' }),
    op(2, 'delta', 2, 'b', { paidAmountMinor: 5 }, { table: 'sales' }),
    op(3, 'delta', 3, 'c', { paidAmountMinor: -3 }, { table: 'sales' }),
    op(4, 'cancel', 4, 'b', null, { table: 'sales', reason: 'اشتباه' }),
  ];
  const expected = {
    alive: true,
    data: { totalMinor: 100, paidAmountMinor: 12, status: 'cancelled', cancelReason: 'اشتباه', cancelledAt: T + 4000 },
  };
  for (const order of permutations(ops)) assert.deepEqual(run(order), expected);
});

test('دلتای تکراری با op_idِ تازه دو بار جمع می‌شود — پس یکتاییِ op_id است که تکرار را می‌گیرد', () => {
  //  این آزمون قراردادِ «شمارنده‌ها: فقط دلتا با op_id یکتا» را روشن می‌کند:
  //  merge خودش تکرار را نمی‌شناسد؛ `sync_v3_ops` با کلیدِ op_id می‌شناسد.
  const ins = op(1, 'insert', 1, 'a', { totalMinor: 1, paidAmountMinor: 0 }, { table: 'sales' });
  const d1 = op(2, 'delta', 2, 'a', { paidAmountMinor: 7 }, { table: 'sales' });
  assert.equal(run([ins, d1, d1]).data.paidAmountMinor, 14);
});
