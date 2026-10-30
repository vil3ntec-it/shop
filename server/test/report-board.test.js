'use strict';
/**
 * تابلوی گزارشِ خانه (license/report-board.js) — همان آزمون‌های مرجعی که
 * core/report/ReportBoard.kt می‌گذراند (core/test-vectors/report-board-v1.json)،
 * و «عددِ کارت = جمعِ فهرستی که باز می‌کند» روی دفترِ report-v1.json.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const B = require(path.join(ROOT, 'license', 'report-board.js'));
const C = require(path.join(ROOT, 'license', 'report-core.js'));
const report = require(path.join(ROOT, 'core', 'test-vectors', 'report-v1.json'));
const board = require(path.join(ROOT, 'core', 'test-vectors', 'report-board-v1.json'));

function run(c) {
  if (c.fn === 'build') return B.build(report.ledgers[c.ledger], c.opts);
  if (c.fn === 'pct') return c.input.map(B.pct);
  if (c.fn === 'range') return c.today.map((t) => c.input.map((s) => B.range(s, t)));
  if (c.fn === 'sortRows') return B.sortRows(c.rows, c.col, c.desc);
  throw new Error('unknown fn ' + c.fn);
}

test('همهٔ آزمون‌های مرجعِ تابلو (همان که Kotlin می‌گذراند)', () => {
  assert.equal(board.version, 1);
  assert.ok(board.cases.length >= 12);
  for (const c of board.cases) assert.deepEqual(JSON.parse(JSON.stringify(run(c))), c.expect, c.name);
});

test('عددِ هر کارت همان جمعِ جدولِ مقصد است (کلیک ⇒ فهرستِ همان عدد)', () => {
  const L = report.ledgers.kabul, date = '2026-10-14';
  const home = B.build(L, { date });
  const kpi = (id) => home.kpis.find((k) => k.id === id);
  for (const id of ['netSales', 'grossProfit']) {
    const k = kpi(id), r = B.range(k.target.span, date);
    const t = C.tables('sales', C.sales(L, { from: r.from, to: r.to })).find((x) => x.id === 'sales.' + k.target.dim);
    const col = t.columns.findIndex((c) => c.key === id);
    assert.equal(t.total[col], k.value, id);
  }
  const debts = C.tables('debts', C.debts(L, { asOf: date }))[0];
  assert.equal(debts.total[1], kpi('receivable').value);
  assert.equal(kpi('invoices').value, C.today(L, { date }).today.invoices);
});

test('سود تا D-23 تقریبی است و شاگرد سود نمی‌بیند (پنهان، نه صفر)', () => {
  const L = report.ledgers.kabul;
  const home = B.build(L, { date: '2026-10-14' });
  const gp = home.kpis.find((k) => k.id === 'grossProfit');
  assert.ok(gp.approximate && gp.title.includes('تقریبی'));
  assert.equal(home.pills.find((p) => p.id === 'profit').tone, 'warning');
  //  حتی با میانگینِ موزون، بی ثبتِ D-23 سود قطعی نیست
  assert.ok(B.build(L, { date: '2026-10-14', costing: 'mavg' }).kpis.find((k) => k.id === 'grossProfit').approximate);
  const staff = B.build(L, { date: '2026-10-14', money: false });
  assert.ok(!staff.kpis.some((k) => k.id === 'grossProfit'));
  assert.ok(!staff.pills.some((p) => p.id === 'profit' || p.id === 'cost'));
});

test('دفترِ خالی: همهٔ کارت‌ها صفر، هیچ عددِ نمونه', () => {
  const home = B.build(report.ledgers.empty, { date: '2026-10-14' });
  assert.ok(home.kpis.every((k) => k.value === 0));
  assert.deepEqual(home.pills.map((p) => p.tone), ['warning', 'success', 'success']);
});
