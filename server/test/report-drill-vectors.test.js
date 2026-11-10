'use strict';
/**
 * RPT-13 — «ریز» ِ ردیفِ گزارش در نسخهٔ وب (`report-core.invoicesOf`/`dayInvoices`، دوقلوی `ReportCore.invoicesOf`/`dayInvoices`).
 *
 *  ۱) بردارهای مرجعِ مشترک (`core/test-vectors/report-drill-v1.json`، دفترها از `report-v1.json`): همان پرونده را
 *     `ReportDrillVectorsTest.kt` روی هستهٔ Kotlin می‌دواند.
 *  ۲) قاعدهٔ `ReportRowDrillTest` ِ برنامه: جمعِ «فروش» ِ ریزِ هر ردیفِ هر تفکیک = «فروش» ِ همان ردیف و شمارش = «فاکتور»؛
 *     ریزِ هر روزِ جدولِ «امروز» = «فروش» و «فاکتور» ِ همان روز — روی هر دفترِ مرجع، با ساعت و بی ساعت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const C = require(path.join(ROOT, 'license', 'report-core.js'));
const REPORT = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'report-v1.json'), 'utf8'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'report-drill-v1.json'), 'utf8'));

test('بردارهای مرجع: هر مورد عیناً همان expect (همان ReportCore ِ Kotlin)', () => {
  assert.equal(V.format, 'report-drill-v1');
  assert.ok(V.cases.length >= 100, `cases: ${V.cases.length}`);
  for (const c of V.cases) {
    assert.ok(['invoicesOf', 'dayInvoices'].includes(c.fn), c.name);
    assert.ok(REPORT.ledgers[c.ledger], `${c.name}: دفترِ ${c.ledger}`);
    assert.deepEqual(C[c.fn](REPORT.ledgers[c.ledger], c.opts), c.expect, c.name);
  }
});

test('جمعِ ریزِ هر ردیف همان «فروش» و «فاکتور» ِ همان ردیف است — هر دفتر، هر تفکیک، هر روز', () => {
  const clocks = [null, { offsetMinutes: 270, dayStartMinute: 0 }, { offsetMinutes: 270, dayStartMinute: 240 }, { offsetMinutes: -300, dayStartMinute: 0 }];
  let rows = 0;
  for (const [name, L] of Object.entries(REPORT.ledgers)) {
    for (const clock of clocks) {
      for (const lineSeller of [false, true]) {
        const s = C.sales(L, { clock, lineSeller });
        for (const dim of ['product', 'category', 'user', 'hour', 'payment', 'channel']) {
          for (const row of s[dim]) {
            const list = C.invoicesOf(L, { dim, key: row.key, clock, lineSeller });
            assert.equal(list.reduce((a, x) => a + x.sales, 0), row.sales, `${name} ${dim} ${row.key}`);
            assert.equal(list.length, row.invoices, `${name} ${dim} ${row.key}`);
            rows++;
          }
        }
      }
      for (const sale of C.ledger(L).sales) {
        const date = clock && sale.createdAt > 0 ? C.fixedClock(clock.offsetMinutes, clock.dayStartMinute).date(sale.createdAt) : sale.date;
        if (!date) continue;
        const t = C.today(L, { date, clock }).today;
        const list = C.dayInvoices(L, { date, clock });
        assert.equal(list.reduce((a, x) => a + x.sales, 0), t.sales, `${name} ${date}`);
        assert.equal(list.length, t.invoices, `${name} ${date}`);
      }
    }
  }
  assert.ok(rows > 100, `rows: ${rows}`);
});
