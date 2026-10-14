'use strict';
/**
 * هستهٔ گزارش (RPT-01/02/03/06/09/11) — `license/report-core.js`.
 *
 *  ۱) آزمون‌های مرجعِ مشترک (`license/test-vectors/report-v1.json`)؛ همین
 *     فایل را `ReportCoreVectorsTest.kt` هم روی هستهٔ Kotlin می‌دواند.
 *  ۲) عددهایی که با دست حساب شده‌اند (نه از خروجیِ خودِ هسته).
 *  ۳) مرجعِ رفتارِ امروز: فرمولِ گزارشِ فروشِ `index.html` (همان
 *     `ReportEngine.sales`) و ماندهٔ قرض‌دار/تأمین‌کننده، مو به مو.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, '..', '..', 'license', 'report-core.js');
const core = require(file);
const vectors = require(path.join(__dirname, '..', '..', 'license', 'test-vectors', 'report-v1.json'));
const L = vectors.ledgers.kabul;
const kabulClock = { offsetMinutes: 270, dayStartMinute: 240 };

function run(c) {
  const ledger = vectors.ledgers[c.ledger];
  if (c.fn === 'tables') return core.tables(c.of, core[c.of](ledger, c.opts)).map(core.plain);
  return core[c.fn](ledger, c.opts);
}

test('آزمون‌های مرجع: هر مورد عیناً همان expect', () => {
  assert.equal(vectors.version, 1);
  assert.ok(vectors.cases.length >= 16);
  for (const c of vectors.cases) {
    assert.deepEqual(JSON.parse(JSON.stringify(run(c))), c.expect, c.name);
  }
});

test('ES5: بی let/const/arrow/backtick — همان قیدِ sync-core برای گوشی‌های قدیمی', () => {
  const src = fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  assert.doesNotMatch(src, /=>|`|\blet\s|\bconst\s|\bclass\s/);
});

test('RPT-01 با دست: روزِ کاریِ ۰۴:۰۰، برگشتی در روزِ خودش', () => {
  const r = core.today(L, { date: '2026-10-14', clock: kabulClock });
  // s4 (339.5) + s6 (150) + s7 (100، ردیفِ قدیمیِ بی createdAt) — s3 ساعتِ ۰۲:۳۰ مالِ دیروز است
  assert.equal(r.today.invoices, 3);
  assert.equal(r.today.sales, 58950);
  assert.equal(r.today.returns, 14000);
  assert.equal(r.today.netSales, 44950);
  // بهای ثبت‌شده: ۱.۵×۱۳۰ + ۲×۴۰ + ۱۳۰ − (برگشتیِ ۱×۱۳۰) = ۲۷۵
  assert.equal(r.today.cogs, 27500);
  assert.equal(r.today.grossProfit, 17450);
  assert.equal(r.today.marginBp, 3882); // 174.5 / 449.5 = 38.82٪
  assert.equal(r.today.avgTicket, 19650);
  assert.equal(r.yesterday.date, '2026-10-13');
  assert.equal(r.yesterday.invoices, 2); // s2 + s3
  assert.equal(r.lastWeek.date, '2026-10-07');
  assert.equal(r.lastWeek.netSales, 15000);
  assert.equal(r.vsYesterday.invoices.bp, 5000);
  assert.equal(r.vsYesterday.returns.bp, null); // دیروز برگشتی نبود
  // بی ساعت: همان date ذخیره‌شده — s3 امروز حساب می‌شود
  assert.equal(core.today(L, { date: '2026-10-14' }).today.invoices, 4);
  // ۰۳:۵۹ صبحِ ۱۵ هنوز روزِ کاریِ ۱۴ است
  const now = Date.UTC(2026, 9, 15, 3, 59) - 270 * 60000;
  assert.equal(core.today(L, { now, clock: kabulClock }).date, '2026-10-14');
  assert.throws(() => core.today(L, {}), /opts.date/);
});

test('RPT-03 با دست: میانگینِ موزونِ متحرک', () => {
  const c = core.costs(core.ledger(L), 'mavg');
  // p1: ۱۰ عدد × ۱۰۰ ⇒ فروشِ ۲ = ۲۰۰؛ برگشتِ ۱ به ۱۰۰؛ ورودِ ۱۰ × ۱۳۰ ⇒ ۱۹ عدد به ارزشِ ۲۲۰۰
  assert.equal(c.line.i1, 20000);
  assert.equal(c.ret.r2, 10000);
  assert.equal(c.line.i2, 34737); // 220000 × 3/19
  assert.equal(c.line.i5, 17368); // 185263 × 1.5/16
  assert.equal(c.ret.r1, 11579); // 34737 / 3
  assert.equal(c.line.i9, 11579); // 179474 / 15.5
  // p2: ۵۰ × ۴۰، اصلاحِ −۲ به میانگین، بعد فروش‌ها همه ۴۰
  assert.equal(c.line.i3, 20000);
  assert.equal(c.line.i7, 0); // قلمِ آزاد: بهای ثبت‌شده
  assert.equal(c.line.i8, 4000 * 10); // فاکتورِ لغوشده در بازپخش نیست ⇒ همان بهای ثبت‌شده
  // پیش از نخستین ورود: بهای ثبت‌شدهٔ قلم
  const solo = core.costs(core.ledger({
    sales: [{ id: 'x', finalTotal: 50, createdAt: 1 }],
    saleItems: [{ id: 'xi', saleId: 'x', productId: 'q', quantity: 2, purchasePrice: 7.25, totalPrice: 50 }],
  }), 'mavg');
  assert.equal(solo.line.xi, 1450);
});

test('RPT-02: تخصیصِ تخفیف با بزرگ‌ترین باقی‌مانده، جمعِ هر تفکیک = جمعِ کل', () => {
  // s4: ۱۵.۵ افغانی روی ۲۲۵/۱۲۰/۱۰ ⇒ ۹.۸۲/۵.۲۴/۰.۴۴
  assert.deepEqual(core.allocate(1550, [22500, 12000, 1000]), [982, 524, 44]);
  assert.deepEqual(core.allocate(-7, [3, 0, 1]), [-5, 0, -2]);
  assert.deepEqual(core.allocate(5, [0, 0]), [5, 0]);
  for (const costing of ['line', 'mavg']) {
    const r = core.sales(L, { from: '2026-10-01', to: '2026-10-14', clock: kabulClock, costing });
    for (const dim of ['product', 'category', 'user', 'hour', 'payment', 'channel']) {
      const sum = (k) => r[dim].reduce((s, x) => s + x[k], 0);
      assert.equal(sum('sales'), r.totals.sales, dim + ' sales');
      assert.equal(sum('netSales'), r.totals.netSales, dim + ' netSales');
      assert.equal(sum('cogs'), r.totals.cogs, dim + ' cogs');
      assert.equal(sum('returns'), r.totals.returns, dim + ' returns');
    }
  }
});

test('RPT-06 با دست: اولین‌وارد‌اولین‌پرداخت، مرزهای ۳۰/۶۰/۹۰', () => {
  const r = core.debts(L, { asOf: '2026-10-14' });
  const d1 = r.customers.rows.find((x) => x.id === 'd1');
  // ۱۰۰۰ + ۵۰۰ − ۷۰۰ ⇒ ۳۰۰ از ۰۷-۰۱ (۱۰۵ روز) + ۵۰۰ از ۰۹-۲۰ + ۴۰ امروز
  assert.deepEqual(d1.buckets, [54000, 0, 0, 30000]);
  assert.equal(d1.oldestDays, 105);
  const d3 = r.customers.rows.find((x) => x.id === 'd3');
  assert.equal(d3.balance, -6000);
  assert.equal(d3.credit, 6000);
  // ۹۹۹ِ بعد از asOf حساب نمی‌شود
  assert.equal(r.customers.rows.find((x) => x.id === 'd2').balance, 15000);
  const s1 = r.suppliers.rows.find((x) => x.id === 'sup1');
  assert.deepEqual(s1.buckets, [0, 0, 50000, 0]);
  assert.throws(() => core.debts(L, {}), /asOf/);
});

test('RPT-09: کاربرِ هر رویداد فقط از فیلدِ صریحش', () => {
  const r = core.fraud(L, { from: '2026-10-13', to: '2026-10-14', clock: kabulClock });
  const by = Object.fromEntries(r.rows.map((x) => [x.key, x]));
  assert.equal(by['کریم'].voidCount, 1); // لغو را کریم زد، نه احمدِ فروشنده
  assert.equal(by['احمد'].voidCount, 0);
  assert.equal(by['احمد'].returnCount, 1); // برگشتیِ فاکتورِ کریم را احمد گرفت
  assert.equal(by['احمد'].lineDeletes, 6000);
  assert.equal(by[''].drawerOpenCount, 1);
  assert.equal(r.totals.drawerOpenCount, 2);
});

test('RPT-11: جدول و متنِ ساده', () => {
  assert.equal(core.cellText(-1550, 'money'), '-15.50');
  assert.equal(core.cellText(5500, 'qty'), '5.5');
  assert.equal(core.cellText(2000, 'qty'), '2');
  assert.equal(core.cellText(3882, 'bp'), '38.82%');
  assert.equal(core.cellText(null, 'bp'), '');
  const t = core.tables('sales', core.sales(L, { from: '2026-10-01', to: '2026-10-14', clock: kabulClock }));
  assert.deepEqual(t.map((x) => x.id), ['sales.totals', 'sales.product', 'sales.category', 'sales.user',
    'sales.hour', 'sales.payment', 'sales.channel']);
  for (const x of t) for (const row of x.rows.concat(x.total ? [x.total] : [])) assert.equal(row.length, x.columns.length, x.id);
  assert.throws(() => core.tables('nope', {}), /unknown/);
});

/* ------------- مرجعِ رفتارِ امروز (index.html ⇐ ReportEngine.sales) ------------- */

/** رونوشتِ مو به موی فرمولِ گزارشِ فروشِ وب، با عددِ اعشاری */
function legacySales(d, fromDate, toDate) {
  const activeSales = d.sales.filter((s) => s.date >= fromDate && s.date <= toDate && s.status !== 'cancelled');
  const gross = activeSales.reduce((sum, s) => sum + (s.total || 0), 0);
  const discount = activeSales.reduce((sum, s) => sum + (s.discount || 0), 0);
  const net = activeSales.reduce((sum, s) => sum + (s.finalTotal || 0), 0);
  let cogs = 0;
  const activeIds = new Set(activeSales.map((s) => s.id));
  for (const si of d.saleItems) if (activeIds.has(si.saleId)) cogs += (si.quantity || 0) * (si.purchasePrice || 0);
  let returnAmount = 0, returnProfitImpact = 0;
  const saleById = new Map(d.sales.map((x) => [x.id, x]));
  const itemById = new Map(d.saleItems.map((x) => [x.id, x]));
  d.returns.filter((r) => r.date >= fromDate && r.date <= toDate).forEach((r) => {
    const sale = saleById.get(r.saleId);
    if (sale && sale.status === 'cancelled') return;
    returnAmount += r.amount || 0;
    const si = itemById.get(r.saleItemId);
    returnProfitImpact += (r.amount || 0) - (si ? (si.purchasePrice || 0) * (r.quantity || 0) : 0);
  });
  return { count: activeSales.length, gross, discount, net, cogs, grossProfit: net - cogs - returnProfitImpact, returnAmount };
}

test('مرجعِ امروز: بی ساعت و با بهای ثبت‌شده، همان عددهای گزارشِ فروشِ امروز', () => {
  const minor = (x) => Math.round(x * 100);
  for (const [from, to] of [['0000-01-01', '9999-12-31'], ['2026-10-14', '2026-10-14'], ['2026-10-07', '2026-10-13'], ['2026-10-08', '2026-10-12']]) {
    const old = legacySales(L, from, to);
    const t = core.sales(L, { from, to }).totals;
    assert.equal(t.invoices, old.count, from);
    assert.equal(t.gross, minor(old.gross), from);
    assert.equal(t.discount, minor(old.discount), from);
    assert.equal(t.sales, minor(old.net), from); // «net»ِ امروز = فروش پیش از برگشتی
    assert.equal(t.returns, minor(old.returnAmount), from);
    assert.equal(t.grossProfit, minor(old.grossProfit), from);
  }
});

test('مرجعِ امروز: مانده‌ها همان ReportEngine.debtors و getSupplierDebt', () => {
  const r = core.debts(L, { asOf: '9999-12-31' });
  for (const db of L.debtors) {
    const txs = L.transactions.filter((t) => t.debtorId === db.id);
    const remaining = txs.reduce((s, t) => s + (t.type === 'give' ? t.amount : -t.amount), 0);
    const row = r.customers.rows.find((x) => x.id === db.id);
    assert.equal(row ? row.balance : 0, Math.round(remaining * 100), db.id);
  }
  for (const sup of L.suppliers) {
    const p = L.purchases.filter((x) => x.supplierId === sup.id);
    const debt = p.reduce((s, x) => s + x.totalAmount, 0) - p.reduce((s, x) => s + x.paidAmount, 0)
      - L.supplierPayments.filter((x) => x.supplierId === sup.id).reduce((s, x) => s + x.amount, 0);
    const row = r.suppliers.rows.find((x) => x.id === sup.id);
    assert.equal(row ? row.balance : 0, Math.round(debt * 100), sup.id);
  }
});
