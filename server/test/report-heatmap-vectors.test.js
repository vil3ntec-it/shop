'use strict';
/**
 * RPT-08 — نقشهٔ حرارتیِ «ساعت در برابر روزِ هفته» در نسخهٔ وب (`license/sales-heatmap-core.js`).
 *
 *  ۱) بردارهای مرجعِ مشترک (`core/test-vectors/sales-heatmap-v1.json`): همان پرونده را `SalesHeatmapVectorsTest.kt` روی
 *     `core/report/SalesHeatmap` ِ Kotlin می‌دواند؛ موردهای `SalesHeatmapTest.kt` (که در CI سبز است) عیناً در آن آمده‌اند.
 *  ۲) `events` همتای `HeatmapModel.events` ِ برنامه روی دفترِ `report-core.ledger`: فاکتورِ لغوشده و برگشتش بیرون، برگشت
 *     منفی در لحظهٔ خودش، سندِ بی لحظه بیرون، بازه با تاریخِ سند — و روی دفترِ مرجعِ `report-v1.json` (کابل) جمعِ نقشه همان
 *     شمار و خالصِ فاکتورها و برگشت‌هاست.
 *  ۳) `tables` همتای `HeatmapModel.tables`: جمعِ هر سه جدول همان جمعِ نقشه؛ و ES5.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FILE = path.join(ROOT, 'license', 'sales-heatmap-core.js');
const H = require(FILE);
const C = require(path.join(ROOT, 'license', 'report-core.js'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'sales-heatmap-v1.json'), 'utf8'));
const REPORT = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'report-v1.json'), 'utf8'));

/** همان شکلِ `expect` ِ بردار: فقط خانه‌های ناصفر */
function shape(m, levels) {
  const cells = [], lv = [];
  m.cells.forEach((line, r) => line.forEach((c, h) => {
    if (c.count || c.totalMinor) cells.push([r, h, c.count, c.totalMinor]);
    const l = H.level(m, r, h, levels);
    if (l) lv.push([r, h, l]);
  }));
  const pair = (c) => [c.count, c.totalMinor];
  return { weekdays: m.weekdays, cells, maxCount: m.maxCount, peak: m.peak, rowTotals: m.rowTotals.map(pair), hourTotals: m.hourTotals.map(pair), total: pair(m.total), levels: lv };
}

test('بردارهای مرجع: هر مورد عیناً همان expect (همان SalesHeatmap ِ Kotlin)', () => {
  assert.equal(V.format, H.VECTORS_FORMAT);
  assert.ok(V.cases.length >= 20, `cases: ${V.cases.length}`);
  for (const w of V.weekdayOf) assert.equal(H.weekdayOf(w.epochDay), w.expect, String(w.epochDay));
  for (const c of V.cases) {
    const m = H.build(c.sales, c.offsetMinutes, c.dayStartMinutes, c.weekStart);
    assert.equal(m.cells.length, H.DAYS, c.name);
    assert.ok(m.cells.every((line) => line.length === H.HOURS), c.name);
    assert.deepEqual(shape(m, c.levels), c.expect, c.name);
  }
});

//  دفترِ خامِ برنامه: سه فاکتور (یکی لغوشده)، دو برگشت (یکی از فاکتورِ لغوشده)، فاکتورِ بی لحظه و فاکتورِ بیرون از بازه
const at = (iso) => Date.parse(iso);
const RAW = {
  sales: [
    { id: 's1', finalTotal: 120.5, status: 'done', date: '2026-10-17', createdAt: at('2026-10-17T05:30:00Z') },  // شنبه ۱۰:۰۰ ِ کابل
    { id: 's2', finalTotal: 40, status: 'cancelled', date: '2026-10-17', createdAt: at('2026-10-17T06:00:00Z') },
    { id: 's3', finalTotal: 15, status: 'done', date: '2026-10-18', createdAt: 0 },
    { id: 's4', finalTotal: 99, status: 'done', date: '2026-09-30', createdAt: at('2026-09-30T05:30:00Z') },
    { id: 's5', finalTotal: 60, status: 'done', date: '2026-10-19', createdAt: at('2026-10-19T14:45:00Z') },    // دوشنبه ۱۹:۱۵
  ],
  returns: [
    { id: 'r1', saleId: 's1', amount: 20, date: '2026-10-19', createdAt: at('2026-10-19T09:30:00Z') },           // دوشنبه ۱۴:۰۰
    { id: 'r2', saleId: 's2', amount: 40, date: '2026-10-17', createdAt: at('2026-10-17T07:00:00Z') },
  ],
};

test('events: همان قاعدهٔ HeatmapModel.events (لغو، برگشت، بی لحظه، بازه)', () => {
  const L = C.ledger(RAW);
  assert.deepEqual(H.events(L, '2026-10-01', '2026-10-31'), [
    { atMs: at('2026-10-17T05:30:00Z'), amountMinor: 12050, isSale: true },
    { atMs: at('2026-10-19T14:45:00Z'), amountMinor: 6000, isSale: true },
    { atMs: at('2026-10-19T09:30:00Z'), amountMinor: -2000, isSale: false },
  ]);
  const m = H.build(H.events(L, '2026-10-01', '2026-10-31'), 270, 0, 6);
  assert.deepEqual(H.cell(m, 0, 10), { count: 1, totalMinor: 12050 }, 'شنبه ۱۰:۰۰');
  assert.deepEqual(H.cell(m, 2, 19), { count: 1, totalMinor: 6000 }, 'دوشنبه ۱۹:۰۰');
  assert.deepEqual(H.cell(m, 2, 14), { count: 0, totalMinor: -2000 }, 'برگشت در خانهٔ خودش');
  assert.deepEqual(m.total, { count: 2, totalMinor: 16050 });
  assert.equal(H.events(L, '2026-10-18', '2026-10-18').length, 0, 'فاکتورِ بی لحظه جایی ندارد');
});

test('دفترِ مرجعِ کابل: جمعِ نقشه همان شمار و خالصِ فاکتورها و برگشت‌های همان بازه', () => {
  const L = C.ledger(REPORT.ledgers.kabul);
  const cancelled = new Set(L.sales.filter((s) => s.cancelled).map((s) => s.id));
  const sold = L.sales.filter((s) => !s.cancelled && s.createdAt > 0);
  const back = L.returns.filter((r) => r.createdAt > 0 && !cancelled.has(r.saleId));
  assert.ok(sold.length >= 3 && back.length >= 1, `${sold.length} / ${back.length}`);
  const m = H.build(H.events(L, '0000-01-01', '9999-12-31'), 270, 0, 6);
  assert.equal(m.total.count, sold.length);
  assert.equal(m.total.totalMinor, sold.reduce((a, s) => a + s.finalTotal, 0) - back.reduce((a, r) => a + r.amount, 0));
  const [grid, hours, days] = H.tables(m);
  assert.deepEqual([grid.id, hours.id, days.id], ['heatmap.grid', 'heatmap.hours', 'heatmap.days']);
  assert.equal(grid.columns.length, 26);
  assert.deepEqual(grid.total, ['جمع', ...m.hourTotals.map((c) => c.count), m.total.count]);
  for (const t of [hours, days]) {
    assert.equal(t.rows.reduce((a, r) => a + r[1], 0), m.total.count, t.id);
    assert.equal(t.rows.reduce((a, r) => a + r[2], 0), m.total.totalMinor, t.id);
    assert.deepEqual(t.total, ['جمع', m.total.count, m.total.totalMinor], t.id);
  }
  assert.deepEqual(days.rows.map((r) => r[0]), ['شنبه', 'یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه']);
  assert.equal(hours.rows[9][0], '09:00');
  //  همان برگهٔ خروجی (`plain`) که چاپ، CSV، اکسل و PDF می‌گیرند
  assert.equal(C.plain(grid).head.length, 26);
});

test('ES5: بی let/const/arrow/backtick — همان قیدِ report-core', () => {
  const src = fs.readFileSync(FILE, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  assert.doesNotMatch(src, /=>|`|\blet\s|\bconst\s|\bclass\s/);
});
