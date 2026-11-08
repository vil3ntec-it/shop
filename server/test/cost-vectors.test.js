'use strict';
/**
 * RPT-03 · PRD-15 · PRM-16 — بردارهای مشترکِ میانگینِ موزونِ متحرک (`core/test-vectors/cost-v1.json`).
 *
 *   همین فایل را `CostVectorsTest.kt` روی هستهٔ Kotlin (`ReportCore.costs`/`stockCost`/`sales` و `PromoEffect`) می‌دواند؛
 *   این‌جا روی `license/report-core.js` (که بازپخش را با `stock-core.js` می‌کند) و روی **گزارشِ خودکارِ سرور**
 *   (`owner-report.profitOf`، همان هسته با روشِ مصوب) — پس عددِ سرور و برنامه برای یک دفتر یکی است.
 *   عددها با دست حساب شده‌اند (`about` ِ همان فایل)، نه از خروجیِ موتور.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const core = require(path.join(ROOT, 'license', 'report-core.js'));
const V = require(path.join(ROOT, 'core', 'test-vectors', 'cost-v1.json'));
const rep = require('../src/lib/owner-report');

const PICK = ['sales', 'returns', 'netSales', 'cogs', 'grossProfit', 'marginBp', 'costEstimatedLines', 'profitApproximate'];
const pick = (t) => Object.fromEntries(PICK.map((k) => [k, t[k]]));
const salesOf = (ledger, extra) => core.sales(ledger, { from: V.range.from, to: V.range.to, ...extra }).totals;

test('قالب و پوشش: دو قیمت، برگشتی، فروش پیش از خرید، شمارش، انتقال، تخفیف', () => {
  assert.equal(V.format, 'cost-v1');
  assert.deepEqual(V.cases.map((c) => c.id), ['A', 'B', 'C', 'D', 'D2', 'E', 'F']);
  const F = V.cases.find((c) => c.id === 'F').ledger;
  assert.ok(F.saleItems.some((i) => Array.isArray(i.promo) && i.promo.length === 2), 'قلمِ دوتخفیفه');
  assert.ok(F.sales.some((s) => s.status === 'cancelled'), 'فاکتورِ باطل');
  assert.ok(V.cases.find((c) => c.id === 'E').ledger.stockMovements.some((m) => m.type === 'transfer_out'));
});

for (const c of V.cases) {
  test(`${c.id} — ${c.name}`, () => {
    const costs = core.costs(core.ledger(c.ledger), 'mavg');
    assert.deepEqual(costs.line, c.expect.line, 'بهای هر قلم');
    assert.deepEqual(costs.ret, c.expect.ret, 'بهای هر برگشتی');
    assert.deepEqual(Object.keys(costs.est).sort(), c.expect.est, 'قلم‌های تخمینی');
    assert.deepEqual(core.stockCost(c.ledger), c.expect.stock, 'بهای میانگین و ارزشِ موجودی (PRD-15)');
    assert.deepEqual(pick(salesOf(c.ledger, { costing: 'mavg', costApproved: true })), c.expect.sales, 'سود با میانگینِ موزون');
    if (c.expect.lineSales) assert.deepEqual(pick(salesOf(c.ledger, {})), c.expect.lineSales, 'قیمتِ روی قلم (انتخابی)');
    //  فقط همان کالا ⇒ همان ردیف
    for (const row of c.expect.stock.rows) {
      assert.deepEqual(core.stockCost(c.ledger, { products: [row.productId] }).rows, [row]);
    }
  });

  test(`${c.id} — گزارشِ خودکارِ سرور همان عدد`, () => {
    const w = { from: V.range.from, to: V.range.to, until: Date.UTC(2026, 10, 2), frequency: 'daily' };
    const sum = rep.summarize(c.ledger, w, V.zone);
    assert.equal(sum.netSales, c.expect.sales.netSales);
    const p = rep.profitOf(c.ledger, w, V.zone, { netSales: sum.netSales });
    assert.deepEqual(
      { cogs: p.cogs, grossProfit: p.grossProfit, marginBp: p.marginBp, costEstimatedLines: p.costEstimatedLines, approximate: p.approximate },
      { cogs: c.expect.sales.cogs, grossProfit: c.expect.sales.grossProfit, marginBp: c.expect.sales.marginBp,
        costEstimatedLines: c.expect.sales.costEstimatedLines, approximate: c.expect.sales.profitApproximate },
    );
    assert.equal(p.costing, 'mavg');
    //  دفترِ ناقص یا خالصِ ناهمخوان هرگز سودِ قطعی نمی‌دهد
    assert.equal(rep.profitOf(c.ledger, w, V.zone, { partial: true }).approximate, true);
    assert.equal(rep.profitOf(c.ledger, w, V.zone, { netSales: sum.netSales + 1 }).approximate, true);
  });
}

test('هستهٔ گزارش بی stock-core بار نمی‌شود (مرورگر: پیش از report-core)', () => {
  const fs = require('node:fs');
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const a = html.indexOf('license/stock-core.js'), b = html.indexOf('license/report-core.js');
  assert.ok(a > 0 && a < b, 'index.html: stock-core.js پیش از report-core.js');
  assert.ok(fs.readFileSync(path.join(ROOT, 'sw.js'), 'utf8').includes("'./license/stock-core.js'"), 'sw.js: نصبِ آفلاین');
});
