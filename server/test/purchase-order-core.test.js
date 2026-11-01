'use strict';
/**
 * هستهٔ سفارشِ خرید (PUR-02، PUR-04، PUR-14) — بردارهای مرجعِ مشترک با Kotlin
 * (`core/test-vectors/purchase-orders-v1.json`) و چند قاعدهٔ فقط-JS (ورودیِ بدنوعِ HTTP).
 * بی پایگاه‌داده: `node --test test/purchase-order-core.test.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const core = require('../src/lib/purchase-order-core');

const V = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', '..', 'core', 'test-vectors', 'purchase-orders-v1.json'), 'utf8'));

const outcome = (r) => (r.failure ? { failure: r.failure } : r.unchanged ? { unchanged: true } : { changed: r.changed });

test('بردار: mulDivRound (نیمه به +∞، سقفِ امن)', () => {
  assert.ok(V.mulDivRound.length >= 15);
  for (const [a, b, c, want] of V.mulDivRound) {
    if (want === 'overflow') assert.throws(() => core.mulDivRound(a, b, c), RangeError, `${a}×${b}/${c}`);
    else assert.equal(core.mulDivRound(a, b, c), want, `${a}×${b}/${c}`);
  }
});

test('بردار: checkOrder', () => {
  for (const c of V.checkOrder) assert.deepEqual(core.checkOrder(c.order), c.failure, c.name);
});

test('بردار: جریان‌ها (گذار، دریافت، تکرار، پیشرفت، جمع‌ها)', () => {
  let steps = 0;
  for (const f of V.flows) {
    let order = structuredClone(f.order);
    const receipts = [];
    for (const [i, st] of f.steps.entries()) {
      const at = `${f.name} — گامِ ${i + 1} (${st.do})`;
      steps++;
      switch (st.do) {
        case 'status': assert.equal(core.status(order, receipts), st.expect, at); break;
        case 'allowed': assert.deepEqual(core.allowed(core.status(order, receipts)), st.expect, at); break;
        case 'progress': assert.deepEqual(core.progress(order, receipts), st.expect, at); break;
        case 'totals': assert.deepEqual(core.totals(order, receipts), st.expect, at); break;
        case 'send':
        case 'cancel':
        case 'edit': {
          const r = st.do === 'send' ? core.send(order, receipts)
            : st.do === 'cancel' ? core.cancel(order, receipts, st.reason)
              : core.edit(order, receipts, st.next);
          assert.deepEqual(outcome(r), st.expect, at);
          if (r.changed) order = st.do === 'edit' ? structuredClone(st.next) : { ...order, state: r.changed };
          break;
        }
        case 'receive': {
          const r = core.receive(order, receipts, st.receipt, st.allowOver === true);
          if (st.expect.failure) assert.deepEqual(r, { failure: st.expect.failure }, at);
          else if (st.expect.duplicate) {
            assert.ok(r.duplicate, `${at}: ${JSON.stringify(r)}`);
            assert.equal(r.duplicate.id, st.expect.duplicate, at);
          } else {
            assert.ok(r.ok, `${at}: ${JSON.stringify(r)}`);
            assert.deepEqual({ lines: r.ok.lines, totalMinor: r.ok.totalMinor, status: r.ok.status }, st.expect.ok, at);
            receipts.push(r.ok.receipt);
          }
          break;
        }
        default: assert.fail(`گامِ ناشناخته ${st.do}`);
      }
    }
  }
  assert.ok(steps >= 60, `فقط ${steps} گام`);
});

test('بردار: تطبیقِ سه‌طرفه', () => {
  for (const c of V.match) assert.deepEqual(core.match(c.order, c.receipts, c.bills, c.tolerance), c.expect, c.name);
});

test('سقفِ ردیف: ۵۰۰ درست، ۵۰۱ too_many_lines', () => {
  const lines = (n) => Array.from({ length: n }, (_, i) => ({ id: `l${i}`, productId: `p${i}`, qtyMilli: 1000, priceMinor: 1 }));
  assert.equal(core.checkOrder({ supplierId: 's', state: 'draft', lines: lines(500) }), null);
  assert.deepEqual(core.checkOrder({ supplierId: 's', state: 'draft', lines: lines(501) }),
    { code: 'too_many_lines', lineId: null, index: -1 });
});

test('فقط JS: ورودیِ بدنوعِ HTTP همان کدهای رد را می‌گیرد، نه استثنا', () => {
  const base = { supplierId: 's', state: 'draft', lines: [{ id: 'l1', productId: 'p', qtyMilli: 1000, priceMinor: 100 }] };
  const withLine = (patch) => ({ ...base, lines: [{ ...base.lines[0], ...patch }] });
  assert.equal(core.checkOrder(null).code, 'bad_state');
  assert.equal(core.checkOrder({ ...base, supplierId: 7 }).code, 'supplier_required');
  assert.equal(core.checkOrder({ ...base, lines: 'x' }).code, 'lines_required');
  assert.equal(core.checkOrder({ ...base, lines: [null] }).code, 'line_id_required');
  assert.equal(core.checkOrder(withLine({ id: 5 })).code, 'line_id_required');
  assert.equal(core.checkOrder(withLine({ productId: null })).code, 'product_required');
  for (const q of [1.5, '1000', NaN, Infinity, 2 ** 53]) assert.equal(core.checkOrder(withLine({ qtyMilli: q })).code, 'bad_qty', String(q));
  for (const p of [0.5, '100', NaN, -Infinity]) assert.equal(core.checkOrder(withLine({ priceMinor: p })).code, 'bad_price', String(p));

  const sent = { ...base, state: 'sent' };
  const rcv = (lines, id = 'r1') => core.receive(sent, [], { id, lines });
  assert.equal(rcv([{ lineId: 'l1', receivedMilli: 1000, damagedMilli: 0, priceMinor: 100 }], 7).failure.code, 'receipt_id_required');
  assert.equal(core.receive(sent, [], null).failure.code, 'receipt_id_required');
  assert.equal(rcv('x').failure.code, 'nothing_received');
  assert.equal(rcv([{ lineId: 1, receivedMilli: 1000, damagedMilli: 0, priceMinor: 100 }]).failure.code, 'unknown_line');
  assert.equal(rcv([{ lineId: 'l1', receivedMilli: '1000', damagedMilli: 0, priceMinor: 100 }]).failure.code, 'bad_qty');
  assert.equal(rcv([{ lineId: 'l1', receivedMilli: 1000, priceMinor: 100 }]).failure.code, 'bad_qty');
  assert.equal(rcv([{ lineId: 'l1', receivedMilli: 1000, damagedMilli: 0, priceMinor: 1.25 }]).failure.code, 'bad_price');
  //  فیلدهای اضافهٔ درخواست در رسید نمی‌نشینند
  const ok = rcv([{ lineId: 'l1', receivedMilli: 1000, damagedMilli: 0, priceMinor: 100, hack: 1 }]).ok;
  assert.deepEqual(ok.receipt.lines, [{ lineId: 'l1', receivedMilli: 1000, damagedMilli: 0, priceMinor: 100 }]);
  //  تکرارِ همان کلید با مقدارِ بدنوع ⇒ تعارض، نه «تکراری»
  assert.equal(core.receive(sent, [ok.receipt], { id: 'r1', lines: [{ lineId: 'l1', receivedMilli: '1000', damagedMilli: 0, priceMinor: 100 }] }).failure.code, 'id_conflict');
});

test('مرزِ تطبیق منفی نیست', () => {
  const o = { supplierId: 's', state: 'sent', lines: [{ id: 'l1', productId: 'p', qtyMilli: 1000, priceMinor: 100 }] };
  assert.throws(() => core.match(o, [], [], { priceBp: -1 }), RangeError);
});
