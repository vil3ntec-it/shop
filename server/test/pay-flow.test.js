'use strict';
/**
 * سیم‌کشیِ پرداخت و صندوق (v3 A4؛ PAY-01..07، 11..15، 19؛ S01، S06، S12) — `license/pay-flow.js`.
 *
 * همان بردارِ مرجعِ هسته (`core/test-vectors/pay-v1.json`) را می‌خواند که
 * `PayFlowTest.kt`ِ برنامه می‌خواند:
 *   - `legacyShift`: دفترِ امروز (بی فیلدِ تازه) از راهِ همین سیم‌کشی همان عددِ
 *     `shiftTotals`ِ index.html را می‌دهد؛ با فیلدِ `payments` همان `coreExpected`ِ
 *     بردار را؛ و Zِ ذخیره‌شده با لغوِ بعدی عوض نمی‌شود.
 *   - `settle`: فیلدهای فروش از هر تسویهٔ موفقِ بردار همان معنای امروز را دارند
 *     (برنامهٔ کهنه همان عدد را می‌خواند).
 * هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const P = require(path.join(ROOT, 'license', 'pay-core.js'));
const F = require(path.join(ROOT, 'license', 'pay-flow.js'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'pay-v1.json'), 'utf8'));

/** همان `shiftTotals`ِ امروزِ وب، از خودِ index.html (مثلِ pay-core.test.js) */
function legacyShiftTotals() {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const start = html.indexOf('function shiftTotals(sh, now){');
  assert.ok(start > 0, 'shiftTotals در index.html پیدا نشد');
  const end = html.indexOf('\n  }\n', start);
  // eslint-disable-next-line no-new-func
  return new Function('sales', 'transactions', 'returns', 'expenses', 'supplierPayments', 'purchases',
    html.slice(start, end + 4) + '\nreturn shiftTotals;');
}

function ledgerOf(d) {
  const ids = (arr, p) => (arr || []).map((x, i) => Object.assign({ id: p + i }, x));
  return {
    sales: ids(d.sales, 's'), transactions: ids(d.transactions, 't'), returns: ids(d.returns, 'r'),
    expenses: ids(d.expenses, 'e'), supplierPayments: ids(d.supplierPayments, 'p'), purchases: ids(d.purchases, 'u'),
    auditLog: [],
  };
}
let seq = 0;
const newId = () => 'id' + (++seq);

test('legacyShift: دفترِ بی فیلدِ تازه همان عددِ shiftTotals ِ امروز را می‌دهد', () => {
  const make = legacyShiftTotals();
  for (const c of V.legacyShift) {
    const L = ledgerOf(c.data);
    const open = { id: 'sh-1', createdAt: c.from, amount: c.opening };
    const legacy = make(L.sales, L.transactions, L.returns, L.expenses, L.supplierPayments, L.purchases)(
      { open, close: { createdAt: c.to } }, c.to).expected;
    assert.equal(legacy, c.legacyExpected, c.name + ' — موتورِ امروز');
    const r = F.report(L, open, null, c.to);
    assert.ok(r.ok, c.name);
    assert.deepEqual(r.skipped, [], c.name);
    assert.equal(r.report.expected.cash, Math.round(legacy * 100), c.name + ' — هسته از راهِ سیم‌کشی');
    if (c.same) assert.deepEqual(r.report.expected, c.coreExpected, c.name);
  }
});

test('legacyShift ۲: فروشِ کیف‌پولی با `payments` همان coreExpected ِ بردار است', () => {
  const c = V.legacyShift.find((x) => x.coreExpected.hesabpay);
  const L = ledgerOf(c.data);
  L.sales[0].payments = [{ method: 'hesabpay', kind: 'wallet', amountMinor: 40000, ref: 'R1', status: 'pending', verification: 'manual' }];
  const r = F.report(L, { id: 'sh-1', createdAt: c.from, amount: c.opening }, null, c.to);
  assert.deepEqual(r.report.expected, c.coreExpected);
});

test('legacyShift ۳: Zِ ذخیره‌شده با لغوِ بعدیِ فاکتور عوض نمی‌شود', () => {
  const c = V.legacyShift.find((x) => x.data.sales.some((s) => s.status === 'cancelled'));
  const L = ledgerOf(c.data);
  L.sales[0].status = 'completed';                 // در لحظهٔ بستن هنوز لغو نشده
  const open = { id: 'sh-1', createdAt: c.from, amount: c.opening };
  const closed = F.closeRow(L, open, { counts: { 50000: 1 } }, c.to, newId);
  assert.ok(closed.ok);
  assert.deepEqual(closed.report.expected, c.coreExpected);
  assert.equal(closed.row.amount, 500, 'amount همان معنای امروز: نقدِ شمرده به افغانی');
  L.sales[0].status = 'cancelled';                 // لغو پس از بستن
  const again = F.report(L, open, closed.row, c.to + 1);
  assert.ok(again.stored);
  assert.deepEqual(again.report.expected, c.coreExpected, 'Z ثابت است');
});

test('settle: فیلدهای فروش از هر تسویهٔ موفقِ بردار همان معنای امروز را دارند', () => {
  let n = 0;
  for (const c of V.settle) {
    if (!c.expect.ok) continue;
    const s = P.settle(c.input);
    assert.ok(s.ok, c.name);
    if (s.status === 'short') continue;            // صفحه فروشِ ناتمام را ثبت نمی‌کند
    n++;
    const f = F.saleFields(s);
    const sum = f.payments.reduce((a, p) => a + p.amountMinor, 0);
    assert.equal(sum, s.due, c.name + ' — جمعِ ردیف‌ها = مبلغِ قابلِ پرداخت');
    assert.equal(Math.round(f.debtGiven * 100), s.toAccount, c.name);
    assert.equal(Math.round(f.paidAmount * 100) + Math.round(f.debtGiven * 100), s.total, c.name + ' — paidAmount + debtGiven = finalTotal');
    assert.equal(f.paymentMethod, s.toAccount > 0 ? 'credit' : 'cash', c.name);
    assert.equal(f.changeMinor, s.change, c.name);
    for (const p of f.payments) {
      if (p.kind === 'cash' || p.kind === 'account') assert.equal(p.status, 'confirmed', c.name);
      else { assert.equal(p.status, 'pending', c.name); assert.equal(p.verification, 'manual', c.name); }
    }
  }
  assert.ok(n >= 10, 'دست‌کم ده بردارِ تسویه سنجیده شد: ' + n);
});

test('S01: نقدِ دقیق با Enterِ خالی، و باقی‌ماندهٔ پول از ۱۰۰۰', () => {
  const draft = { total: 850, cash: '', rows: [] };
  assert.deepEqual(F.exactCash(draft), { ok: true, amount: 850 });
  const r = F.checkout(Object.assign({}, draft, { cash: '1000' }));
  assert.ok(r.ok);
  assert.equal(r.settlement.change, 15000);
  assert.equal(r.fields.changeMinor, 15000);
  assert.equal(r.fields.tenderedMinor, 100000);
  assert.deepEqual(r.fields.payments, [{ method: 'cash', kind: 'cash', amountMinor: 85000, status: 'confirmed' }]);
  assert.equal(r.fields.paidAmount, 850);
  assert.equal(r.fields.paymentMethod, 'cash');
});

test('S06: پرداختِ ترکیبی ۱٬۰۰۰ نقد + ۱٬۴۵۰ HesabPay — دو ردیف، HesabPay «ثبتِ دستی»، شیفت هر دو را جدا نشان می‌دهد', () => {
  const draft = { total: 2450, cash: '1000', rows: [{ method: 'hesabpay', ref: 'HP-77', amount: '1450' }] };
  const r = F.checkout(draft);
  assert.ok(r.ok);
  assert.deepEqual(r.fields.payments, [
    { method: 'cash', kind: 'cash', amountMinor: 100000, status: 'confirmed' },
    { method: 'hesabpay', kind: 'wallet', amountMinor: 145000, status: 'pending', ref: 'HP-77', verification: 'manual' },
  ]);
  assert.equal(r.fields.payments.reduce((a, p) => a + p.amountMinor, 0), 245000, 'مجموع دقیقاً کلِ فاکتور');
  assert.equal(r.fields.paidAmount, 2450);
  assert.equal(r.fields.debtGiven, 0);
  // بی شمارهٔ مرجع رد می‌شود و ردیف را نشان می‌دهد
  const noRef = F.preview({ total: 2450, cash: '1000', rows: [{ method: 'hesabpay', ref: '', amount: '1450' }] });
  assert.equal(noRef.error, 'REF_REQUIRED');
  assert.equal(noRef.index, 0);
  assert.match(F.message(noRef), /HesabPay/);
  // گزارشِ شیفت
  const L = { sales: [Object.assign({ id: 'S06', finalTotal: 2450, debtGiven: 0, status: 'completed', createdAt: 100 }, r.fields)] };
  const x = F.report(L, { id: 'sh', createdAt: 0, amount: 0 }, null, 200);
  assert.equal(x.report.methods.cash.sales, 100000);
  assert.equal(x.report.methods.hesabpay.sales, 145000);
  assert.deepEqual(x.report.expected, { cash: 100000, hesabpay: 145000 });
});

test('پرداختِ جزئی به حسابِ مشتری؛ بی مشتری ثبت نمی‌شود؛ پولِ پس فقط از نقد', () => {
  const part = F.checkout({ total: 2000, cash: '500', rows: [], customerId: 'd1' });
  assert.ok(part.ok);
  assert.equal(part.fields.paymentMethod, 'credit');
  assert.equal(part.fields.debtGiven, 1500);
  assert.equal(part.fields.paidAmount, 500);
  assert.deepEqual(part.fields.payments.map((p) => [p.method, p.amountMinor]), [['cash', 50000], ['account', 150000]]);
  const short = F.checkout({ total: 2000, cash: '500', rows: [] });
  assert.equal(short.error, 'SHORT');
  assert.equal(short.remaining, 150000);
  const over = F.preview({ total: 1000, cash: '', rows: [{ method: 'afpay_card', ref: 'C1', amount: '1200' }] });
  assert.equal(over.error, 'NON_CASH_OVERPAY');
  // «۰» صریح یعنی همه به حساب (S07)
  const zero = F.checkout({ total: 2000, cash: '0', rows: [], customerId: 'd1' });
  assert.equal(zero.fields.debtGiven, 2000);
  // نقدِ دقیق کنارِ ردیفِ غیرنقد
  assert.equal(F.exactCash({ total: 2450, rows: [{ method: 'hawala', ref: 'H', amount: '1450' }] }).amount, 1000);
});

/** S12: موجودیِ اولیه ۲٬۰۰۰، فروشِ نقد ۱۵٬۳۰۰، خروجِ نقد ۵۰۰ («خرید شیر») */
function s12() {
  const open = { id: 'sh12', type: 'shift_open', createdAt: 1000, amount: 2000 };
  const L = { sales: [{ id: 'a', finalTotal: 15300, debtGiven: 0, status: 'completed', createdAt: 1100 }], auditLog: [open] };
  const mv = F.moveRow(L, open, { type: 'cash_out', amount: '500', reason: 'petty_expense', note: 'خرید شیر', by: 'صندوقدار' }, 1200, newId);
  assert.ok(mv.ok, JSON.stringify(mv));
  L.auditLog.push(mv.row);
  return { L, open };
}

test('S12: کسریِ ۱۰ زیرِ آستانه — دلیل اختیاری؛ شمارشِ کور؛ اختلاف اصلاح نمی‌شود', () => {
  const { L, open } = s12();
  assert.equal(F.expectedCash(L, open, 1300), 16800);
  const counts = { 100000: 10, 50000: 12, 10000: 7, 5000: 1, 2000: 2 };
  assert.equal(F.countTotal(counts), 1679000);
  const r = F.closeRow(L, open, { counts, blind: true, by: 'صندوقدار' }, 1300, newId);
  assert.ok(r.ok, JSON.stringify(r));
  assert.equal(r.report.expected.cash, 1680000);
  assert.equal(r.report.counted.cash, 1679000);
  assert.equal(r.report.variance.cash, -1000);
  assert.equal(r.report.needsReview, true, 'برای مدیر قابلِ بررسی');
  assert.equal(r.row.amount, 16790);
  assert.equal(r.row.refId, 'sh12');
  const view = P.cashierView(r.report);
  assert.equal(view.expected, undefined, 'صندوقدار مبلغِ مورد انتظار را نمی‌بیند');
  assert.equal(view.variance, undefined);
});

test('S12b: کسریِ ۱۰۰ بالای آستانه — بی دلیل بسته نمی‌شود، با دلیل بسته می‌شود', () => {
  const { L, open } = s12();
  const counts = { 100000: 10, 50000: 12, 10000: 7 };   // ۱۶٬۷۰۰
  const bad = F.closeRow(L, open, { counts, blind: true }, 1300, newId);
  assert.equal(bad.error, 'REASON_REQUIRED');
  assert.equal(bad.variance, undefined, 'شمارشِ کور اختلاف را لو نمی‌دهد');
  const ok = F.closeRow(L, open, { counts, blind: true, reason: 'پولِ خرد کم بود' }, 1300, newId);
  assert.ok(ok.ok);
  assert.equal(ok.report.variance.cash, -10000);
  assert.equal(ok.row.reason, 'پولِ خرد کم بود');
});

test('ورود/خروجِ نقد و کشو بی فروش: دلیل، توضیحِ «دیگر»، نقدِ کافی، تأییدکننده', () => {
  const open = { id: 'sh', createdAt: 0, amount: 100 };
  const L = { auditLog: [open] };
  assert.equal(F.moveRow(L, open, { type: 'cash_out', amount: 200, reason: 'owner_withdrawal' }, 10, newId).error, 'INSUFFICIENT_CASH');
  assert.equal(F.moveRow(L, open, { type: 'cash_out', amount: 50, reason: 'other' }, 10, newId).error, 'NOTE_REQUIRED');
  assert.equal(F.moveRow(L, open, { type: 'cash_out', amount: 50 }, 10, newId).error, 'REASON_REQUIRED');
  assert.equal(F.moveRow(L, open, { type: 'cash_in', amount: 0, reason: 'deposit' }, 10, newId).error, 'INVALID_AMOUNT');
  assert.equal(F.moveRow(L, open, { type: 'no_sale', reason: 'پولِ خرد' }, 10, newId).error, 'APPROVAL_REQUIRED');
  const ns = F.moveRow(L, open, { type: 'no_sale', reason: 'پولِ خرد', by: 'علی', approvedBy: 'مالک' }, 10, newId);
  assert.ok(ns.ok);
  assert.deepEqual([ns.row.type, ns.row.refId, ns.row.approvedBy, ns.row.amount], ['no_sale', 'sh', 'مالک', 0]);
  L.auditLog.push(ns.row);
  const dep = F.moveRow(L, open, { type: 'cash_in', amount: '250', reason: 'deposit' }, 20, newId);
  L.auditLog.push(dep.row);
  const x = F.report(L, open, null, 30).report;
  assert.deepEqual(x.moves, { in: 25000, out: 0 });
  assert.equal(x.events.noSale, 1);
  assert.equal(x.expected.cash, 35000);
  // ردیفِ صندوقِ دیگر (refId) حساب نمی‌شود
  L.auditLog.push(Object.assign({}, dep.row, { id: 'x', refId: 'other' }));
  assert.equal(F.report(L, open, null, 30).report.expected.cash, 35000);
});

test('مصرفی بیش از نقدِ دخل بی‌صدا گم نمی‌شود: در skipped می‌آید', () => {
  const open = { id: 'sh', createdAt: 0, amount: 0 };
  const L = { expenses: [{ id: 'e', amount: 300, createdAt: 5 }], sales: [{ id: 's', finalTotal: 100, debtGiven: 0, createdAt: 6 }] };
  const r = F.report(L, open, null, 10);
  assert.deepEqual(r.skipped.map((s) => [s.id, s.error]), [['exp:e', 'INSUFFICIENT_CASH']]);
  assert.equal(r.report.expected.cash, 10000);
});

test('پیامِ هر خطا فارسی است و پیامِ پیش‌فرض ندارد', () => {
  for (const code of ['REF_REQUIRED', 'NON_CASH_OVERPAY', 'METHOD_LIMIT', 'SHORT', 'INSUFFICIENT_CASH', 'REASON_REQUIRED',
    'NOTE_REQUIRED', 'APPROVAL_REQUIRED', 'SHIFT_CLOSED', 'INVALID_TIME', 'BAD_DENOMINATION']) {
    const m = F.message({ error: code, remaining: 100, method: 'hesabpay' });
    assert.notEqual(m, 'مبلغ درست نیست', code);
    assert.match(m, /[؀-ۿ]/, code);
  }
});

test('سیم‌کشی خالص است: نه DOM، نه ساعت، نه ذخیره', () => {
  const src = fs.readFileSync(path.join(ROOT, 'license', 'pay-flow.js'), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  for (const bad of ['document', 'localStorage', 'fetch(', 'Date', 'Math.random', 'XMLHttpRequest']) {
    assert.ok(!src.includes(bad), 'pay-flow.js نباید ' + bad + ' داشته باشد');
  }
});
