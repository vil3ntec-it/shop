'use strict';
/**
 * دفترِ قرض‌داران (v3 §22.2-A5، §21.11، CRM-03) — سمتِ وب: `license/debt-book.js`.
 *
 * همان فایلِ بردارِ مرجعی را می‌خواند که `DebtBookVectorsTest.kt`ِ اندروید و
 * کامپیوتر می‌خواند (`license/test-vectors/debt-book-v1.json`). یک فرق میانِ دو
 * سکو ⇒ این آزمون یا آن یکی می‌شکند. هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const B = require(path.join(__dirname, '..', '..', 'license', 'debt-book.js'));
const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'license', 'test-vectors', 'debt-book-v1.json'), 'utf8'));

/** ماندهٔ امروزِ وب (`getDebt`ِ index.html) — با Double */
const legacyDebt = (data, id) => (data.transactions || []).filter((t) => t.debtorId === id)
  .reduce((s, t) => s + (t.type === 'give' ? t.amount : -t.amount), 0);

test('بردارهای مرجع — همان عددهای کاتلین', () => {
  assert.ok(V.cases.length >= 4);
  for (const c of V.cases) {
    const b = B.book(c.ledger, c.debtorId, c.today);
    const x = c.expect;
    assert.equal(b.balanceMinor, x.balanceMinor, c.name);
    assert.equal(b.overdueMinor, x.overdueMinor, c.name);
    assert.deepEqual(b.overdue.map((e) => e.tx.id), x.overdue, c.name);
    assert.equal(b.nextDue ? b.nextDue.tx.id : null, x.nextDue, c.name);
    //  ماندهٔ آخر همان عددِ امروزِ وب است، فقط بی خطای ممیز
    assert.equal(b.balanceMinor, Math.round(legacyDebt(c.ledger, c.debtorId) * 100) + 0, c.name);
    if (x.credit === null) assert.equal(b.credit, null, c.name);
    else {
      assert.equal(b.credit.limitMinor, x.credit.limitMinor);
      assert.equal(b.credit.balanceMinor, x.credit.balanceMinor);
      assert.equal(b.credit.headroomMinor, x.credit.headroomMinor);
      assert.equal(b.credit.over, x.credit.over);
    }
    if (x.creditCheck) {
      const r = B.creditCheck(c.ledger, c.debtorId, x.creditCheck.extraMinor);
      if (x.creditCheck.result === null) assert.equal(r, null, c.name);
      else {
        for (const k of ['limitMinor', 'balanceMinor', 'afterMinor', 'over', 'excessMinor']) assert.equal(r[k], x.creditCheck.result[k], `${c.name} ${k}`);
      }
    }
    assert.deepEqual(b.entries.map((e) => e.tx.id), x.entries.map((e) => e.id), c.name);
    b.entries.forEach((e, i) => {
      const w = x.entries[i];
      const at = `${c.name} / ${w.id}`;
      assert.equal(e.row, w.row, at);
      assert.equal(e.give, w.give, at);
      assert.equal(e.balanceAfterMinor, w.balanceAfterMinor, at);
      assert.equal(e.openMinor, w.openMinor, at);
      assert.equal(e.overdue, w.overdue, at);
      assert.equal(e.dueDate, w.due, at);
      assert.equal(e.sale ? e.sale.id : null, w.sale, at);
      assert.deepEqual(e.allocations, w.allocations, at);
      assert.deepEqual(e.lines, w.lines, at);
    });
  }
});

test('شمارهٔ فاکتور در شرح — همان قاعدهٔ کاتلین', () => {
  assert.equal(B.invoiceLabelOf('فروش نسیه — فاکتور #12'), '12');
  assert.equal(B.invoiceLabelOf('لغو فروش — فاکتور #B1-7'), 'B1-7');
  assert.equal(B.invoiceLabelOf('بابت خرید'), null);
  assert.equal(B.isIsoDate('2026-10-31'), true);
  assert.equal(B.isIsoDate('2026-13-01'), false);
  assert.equal(B.isIsoDate('1405/07/01'), false);
});

test('رسیدِ برد با اقلام و رسیدِ دریافت — همان ردیف‌های DebtBook.receipt', () => {
  const data = {
    products: [{ id: 'p1', name: 'برنج', unit: 'کیلو', salePrice: 999 }],
    debtors: [{ id: 'c1', name: 'احمد', phone: '0790000000' }],
    sales: [{ id: 's1', invoiceNumber: 1, debtorId: 'c1', total: 375, discount: 0, finalTotal: 375, paidAmount: 75, remaining: 300, paymentMethod: 'credit', date: '2026-10-01' }],
    saleItems: [{ id: 'i1', saleId: 's1', productId: 'p1', quantity: 1.25, unitPrice: 300, totalPrice: 375 }],
    transactions: [
      { id: 'g', debtorId: 'c1', type: 'give', amount: 300, date: '2026-10-01', notes: 'فروش نسیه — فاکتور #1', createdAt: 1 },
      { id: 'r', debtorId: 'c1', type: 'receive', amount: 100, date: '2026-10-05', notes: '', createdAt: 5 },
    ],
  };
  const money = (m) => Math.round(m / 100).toLocaleString('en-US');
  const fmt = {
    money, date: (s) => s, qty: (n) => String(n),
    state: (m) => (m > 0 ? `${money(m)} افغانی بدهکار` : m < 0 ? `${money(-m)} افغانی موجودی دارد` : 'حساب صاف است'),
  };
  const r = B.receipt(data, 'c1', 'g', 'دکان توحید', '2026-10-17', fmt);
  assert.equal(r.title, 'دکان توحید — رسیدِ قرض');
  const rows = Object.fromEntries(r.rows);
  assert.equal(rows['برنج — 1.25 کیلو × 300'], '375');
  assert.equal(rows['جمعِ اقلام'], '375 افغانی');
  assert.equal(rows['پرداختِ همان لحظه'], '75 افغانی');
  assert.equal(rows['قرضِ این ردیف'], '300 افغانی');
  assert.equal(rows['سررسید'], 'ندارد');
  assert.equal(rows['باقیِ این قرض'], '200 افغانی');
  assert.ok(B.receiptText(r).startsWith('دکان توحید — رسیدِ قرض\nمشتری: احمد'));

  const p = Object.fromEntries(B.receipt(data, 'c1', 'r', '', '2026-10-17', fmt).rows);
  assert.equal(p['مبلغِ دریافتی'], '100 افغانی');
  assert.equal(p['مانده پیش از دریافت'], '300 افغانی بدهکار');
  assert.equal(p['مانده پس از این ردیف'], '200 افغانی بدهکار');
  assert.equal(p['بابتِ فاکتور #1'], '100 افغانی');
  assert.equal(B.receipt(data, 'c1', 'nope', '', '2026-10-17', fmt), null);
});
