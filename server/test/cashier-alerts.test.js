'use strict';
/**
 * RSK-10 — هشدارِ رفتارِ غیرعادیِ صندوقدار (`cashierAlerts` در license/report-board.js): همان بردارهایی که
 * core/risk/CashierAlerts.kt (commonMain) می‌گذراند (core/test-vectors/cashier-alerts-v1.json)، نگاشتِ ردیفِ
 * ضدتقلبِ هسته، و نشانِ خانه فقط وقتی هشدار هست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const ROOT = path.join(__dirname, '..', '..');
const B = require(path.join(ROOT, 'license', 'report-board.js'));
const C = require(path.join(ROOT, 'license', 'report-core.js'));
const vectors = require(path.join(ROOT, 'core', 'test-vectors', 'cashier-alerts-v1.json'));
const report = require(path.join(ROOT, 'core', 'test-vectors', 'report-v1.json'));

const rowsOf = (c) => c.rows.map(([key, invoices, voids, discounts, noSales]) => ({ key, invoices, voids, discounts, noSales }));
const fraudRow = (key, invoices, voidCount, discountCount, noSaleCount) => ({
  key, invoices, gross: 0, discountCount, discount: 0, discountBp: null, returnCount: 0, returns: 0, voidCount, voids: 0,
  lineDeleteCount: 0, lineDeletes: 0, noSaleCount, overrideCount: 0, approvalCount: 0,
});

test('RSK-10: همهٔ بردارهای هشدار (همان که Kotlin می‌گذراند)', () => {
  assert.equal(vectors.version, 1);
  assert.ok(vectors.cases.length >= 15);
  for (const c of vectors.cases) assert.deepEqual(B.cashierAlerts(rowsOf(c), c.policy), c.expect, c.name);
  //  هر شاخه دستِ کم یک بار: هشدار، بی هشدار، سه نوع، سیاست
  const all = vectors.cases.flatMap((c) => c.expect);
  assert.deepEqual([...new Set(all.map((a) => a.kind))].sort(), ['discount', 'no_sale', 'void']);
  assert.ok(vectors.cases.some((c) => c.expect.length === 0) && vectors.cases.some((c) => c.policy));
});

test('RSK-10: ردیفِ ضدتقلبِ هسته (voidCount، discountCount، noSaleCount) همان ورودیِ هشدار است', () => {
  const fraud = { from: 'a', to: 'b', rows: [fraudRow('علی', 40, 5, 0, 0), fraudRow('سارا', 160, 2, 0, 0), fraudRow('رضا', 100, 1, 0, 0)], totals: fraudRow('', 300, 8, 0, 0) };
  assert.deepEqual(B.alertsOf(fraud), vectors.cases[0].expect);
  assert.deepEqual(B.alertsOf({ rows: [] }), []);
  assert.deepEqual(B.alertsOf(null), []);
});

test('RSK-10: نشانِ «هشدارِ رفتارِ غیرعادی» در خانه فقط با هشدار، و کلیک به ضدتقلبِ امروز', () => {
  const L = C.ledger(report.ledgers.kabul), date = '2026-10-14';
  const t = C.today(L, { date }), debts = C.debts(L, { asOf: date });
  const quiet = B.home(t, debts, C.fraud(L, { from: date, to: date }), true);
  assert.ok(!quiet.pills.some((p) => p.id === 'cashier'), 'دفترِ مرجع هشداری ندارد');
  const busy = { from: date, to: date, rows: [fraudRow('علی', 40, 5, 0, 4), fraudRow('سارا', 160, 0, 0, 0)], totals: fraudRow('', 200, 5, 0, 4) };
  const home = B.home(t, debts, busy, true);
  const pill = home.pills.find((p) => p.id === 'cashier');
  assert.deepEqual(pill, { id: 'cashier', text: '2 هشدارِ رفتارِ غیرعادیِ صندوقدار امروز', tone: 'danger', target: { tab: 'fraud', span: 'today', dim: '' } });
  assert.equal(home.pills[home.pills.length - 1], pill, 'پس از نشانِ «رویدادِ حساس»');
  //  شاگرد (بی پول) زبانهٔ ضدتقلب ندارد و هشدارِ کاربران را نمی‌بیند
  assert.ok(!B.home(t, debts, busy, false).pills.some((p) => p.id === 'cashier'));
});
