'use strict';
/**
 * پرداخت و صندوق (PAY-01..07، 09..16، 19، 23) — سمتِ وب: `license/pay-core.js`.
 *
 * همان فایلِ بردارِ مرجعی را می‌خواند که `PayCoreTest.kt`ِ اندروید و کامپیوتر
 * می‌خواند (`core/test-vectors/pay-v1.json`). عددهای بردار دستی و از روی
 * قاعده نوشته شده‌اند، نه از خروجیِ کد؛ پس این آزمون دوری نیست.
 *
 * بخشِ `legacyShift` رفتارِ امروزِ صندوق را می‌سنجد: خودِ `shiftTotals`ِ
 * `index.html` (از متنِ فایل بیرون کشیده و اجرا می‌شود) روی همان داده‌ها همان
 * عددی را می‌دهد که در بردار است، و هسته روی رویدادهای هم‌ارز یا همان عدد را
 * می‌دهد یا تفاوتش ثبت و مستند است. هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const P = require(path.join(ROOT, 'license', 'pay-core.js'));
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'pay-v1.json'), 'utf8'));

const sum = (a, f) => a.reduce((s, x) => s + f(x), 0);

test('ثابت‌ها با بردارها یکی‌اند (اسکناس، سکه، وضعیت، دلیل، روش‌ها، آستانه)', () => {
  const C = V.constants;
  assert.deepEqual(P.NOTES, C.notes);
  assert.deepEqual(P.COINS, C.coins);
  assert.deepEqual(P.denominations(false), C.notes);
  assert.deepEqual(P.denominations(true), C.coins.concat(C.notes));
  assert.equal(P.DEFAULT_VARIANCE_THRESHOLD, C.defaultVarianceThreshold);
  assert.deepEqual(P.STATUSES, C.statuses);
  assert.deepEqual(P.MOVE_REASONS, C.moveReasons);
  assert.deepEqual(P.DEFAULT_METHODS.map((m) => Object.assign({}, m)), C.defaultMethods);
  for (const n of C.notes) assert.ok(P.isDenomination(n, false));
  for (const c of C.coins) { assert.ok(!P.isDenomination(c, false)); assert.ok(P.isDenomination(c, true)); }
});

test('گذارِ وضعیتِ پرداخت: همهٔ ۴۹ جفت', () => {
  const C = V.constants;
  for (const from of C.statuses) {
    for (const to of C.statuses) {
      const allowed = C.transitions[from].includes(to);
      assert.equal(P.canTransition(from, to), allowed, `${from} → ${to}`);
      assert.deepEqual(P.transition(from, to), allowed ? { ok: true, status: to } : { ok: false, error: 'BAD_TRANSITION' });
    }
  }
  for (const c of V.transitionErrors) assert.deepEqual(P.transition(c.from, c.to), c.expect);
});

test('جدولِ روش‌های پرداخت', () => {
  for (const c of V.methodTables) {
    const r = P.defineMethods(c.input);
    const got = r.ok ? { ok: true, methods: r.methods.map((m) => Object.assign({}, m)) } : r;
    assert.deepEqual(got, c.expect, c.name);
  }
});

function checkSettleInvariants(r, name) {
  if (!r.ok) return;
  const paid = sum(r.payments, (p) => p.amount);
  assert.equal(r.due, r.total + r.rounding, name);
  if (r.status === 'short') assert.equal(paid + r.remaining, r.due, name);
  else assert.equal(paid, r.due, name + ' — جمعِ ردیف‌های پرداخت = مبلغِ فاکتور');
  assert.equal(r.tendered - r.change + r.toAccount + (r.status === 'short' ? r.remaining : 0), r.due, name);
  assert.ok(r.change >= 0 && r.remaining >= 0 && r.toAccount >= 0, name);
  for (const p of r.payments) assert.ok(Number.isSafeInteger(p.amount) && p.amount > 0, name);
}

test('تسویه: ترکیبی، پولِ پس، جزئی، گردکردن، ارز', () => {
  for (const c of V.settle) {
    // بیرون از ۲^۵۳−۱ (بخشِ ۲۸.۴): هر دو زبان خطای بازه می‌دهند، نه عددِ گردشده
    if (c.expect.throws) { assert.throws(() => P.settle(c.input), RangeError, c.name); continue; }
    const r = P.settle(c.input);
    assert.deepEqual(r, c.expect, c.name);
    checkSettleInvariants(r, c.name);
  }
});

test('نقدِ دقیق: با افزودنِ همان مبلغ، فاکتور بی پولِ پس بسته می‌شود', () => {
  for (const c of V.exactCash) {
    const r = P.exactCash(c.input);
    assert.deepEqual(r, c.expect, c.name);
    if (!r.ok) continue;
    const tenders = (c.input.tenders || []).concat(r.amount > 0 ? [{ method: 'cash', amount: r.amount }] : []);
    const s = P.settle(Object.assign({}, c.input, { tenders }));
    assert.equal(s.status, 'paid', c.name);
    assert.equal(s.change, 0, c.name);
  }
});

/**
 * جاروبِ تصادفیِ ثابت (MINSTD، همه‌جا زیرِ ۲^۵۳ تا JS و کاتلین یک دنباله بسازند).
 * `PayCoreTest.kt` همین دنباله را با همین ترتیبِ فراخوانی می‌سازد؛ چکیدهٔ خروجی‌ها
 * در بردار است تا هر فرقِ دو پیاده‌سازی روی هزاران ورودی دیده شود.
 */
function sweepCases(seed, count) {
  const rnd = (n) => { seed = (seed * 48271) % 2147483647; return seed % n; };
  const wallets = ['hesabpay', 'afpay_card', 'hawala'];
  const out = [];
  for (let i = 0; i < count; i++) {
    const total = rnd(500) * 100 + rnd(3) * 50;
    const tenders = [];
    for (let k = rnd(4); k > 0; k--) {
      if (rnd(2) === 1) tenders.push({ method: 'cash', amount: P.NOTES[rnd(P.NOTES.length)] });
      else {
        const method = wallets[rnd(3)];
        tenders.push({ method, amount: 100 + rnd(Math.max(1, total)), ref: 'R' + k });
      }
    }
    const cashRounding = [0, 500, 1000][rnd(3)];
    out.push({ total, tenders, cashRounding, customerId: rnd(2) === 1 ? 'c1' : null });
  }
  return out;
}
function sweepLine(r) {
  if (!r.ok) return ['E', r.error, r.index === undefined ? '' : r.index].join('|');
  return [r.status, r.total, r.rounding, r.due, r.tendered, r.change, r.remaining, r.toAccount,
    r.payments.map((p) => [p.method, p.amount, p.ref === null ? '' : p.ref, p.status].join(':')).join(',')].join('|');
}

test('تسویه: جاروبِ تصادفیِ ثابت — قاعده‌ها هیچ‌جا نمی‌شکنند و چکیده با کاتلین یکی است', () => {
  const lines = [];
  for (const input of sweepCases(V.sweep.seed, V.sweep.count)) {
    const r = P.settle(input);
    lines.push(sweepLine(r));
    if (!r.ok) { assert.equal(r.error, 'NON_CASH_OVERPAY'); continue; }
    checkSettleInvariants(r, JSON.stringify(input));
    const cash = sum(input.tenders.filter((t) => t.method === 'cash'), (t) => t.amount);
    assert.ok(r.change <= cash, 'پولِ پس هرگز بیش از نقدِ گرفته‌شده نیست');
    assert.ok(Math.abs(r.rounding) * 2 <= input.cashRounding, 'گردکردن بیش از نیمِ پله نیست');
    if (r.rounding !== 0) assert.equal(r.remaining, 0, 'گردکردن فقط وقتی فاکتور بسته می‌شود');
  }
  const digest = require('node:crypto').createHash('sha256').update(lines.join('\n')).digest('hex');
  assert.equal(digest, V.sweep.digest);
});

test('تبادلِ ارز: نرخ، منبع، زمان و تأییدکننده در خودِ رکورد', () => {
  for (const c of V.exchange) assert.deepEqual(P.exchange(c.input), c.expect, c.name);
});

test('بازپرداخت: روشِ اصلی، نقد یا اعتبار؛ با مجوز و نه بیش از پرداخت', () => {
  for (const c of V.refund) {
    const r = P.refund(c.input);
    assert.deepEqual(r, c.expect, c.name);
    if (!r.ok) continue;
    assert.equal(sum(r.payouts, (p) => p.amount), c.input.amount, c.name);
    const before = sum(c.input.payments, (p) => p.refunded || 0);
    assert.equal(sum(r.payments, (p) => p.refunded) - before, c.input.amount, c.name);
  }
});

function runSteps(steps, name) {
  let shift = null;
  steps.forEach((st, i) => {
    const label = `${name} — گامِ ${i + 1} (${st.op})`;
    let got;
    const dup = (r, extra) => Object.assign({ ok: true }, r.duplicate ? { duplicate: true } : {}, extra || {});
    if (st.expect && st.expect.throws) {
      assert.throws(() => (st.op === 'x' ? P.xReport(shift, st.input) : P.record(shift, st.input)), RangeError, label);
      return;
    }
    if (st.op === 'open' || st.op === 'record') {
      const r = st.op === 'open' ? P.openShift(st.input) : P.record(shift, st.input);
      if (r.ok) shift = r.shift;
      got = r.ok ? dup(r) : r;
    } else if (st.op === 'x' || st.op === 'close' || st.op === 'z') {
      const r = st.op === 'x' ? P.xReport(shift, st.input) : st.op === 'close' ? P.closeShift(shift, st.input) : P.zReport(shift);
      if (r.ok && st.op === 'close') shift = r.shift;
      got = r.ok ? dup(r, { report: r.report }) : r;
    } else {
      throw new Error('گامِ ناشناخته: ' + st.op);
    }
    if (st.expect) assert.deepEqual(got, st.expect, label);
  });
  return shift;
}

test('شیفت: باز کردن، ورود/خروج، کشو بی فروش، X، شمارشِ اسکناس، Z و اختلاف', () => {
  for (const c of V.shift) runSteps(c.steps, c.name);
});

test('شیفت: رویدادِ ردشده هیچ اثری نمی‌گذارد و Z بعد از بستن ثابت است', () => {
  const s0 = P.openShift({ id: 'sh', at: 0, float: 1000 }).shift;
  const bad = P.record(s0, { id: 'a', type: 'cash_out', at: 1, amount: 2000, reason: 'owner_withdrawal' });
  assert.equal(bad.error, 'INSUFFICIENT_CASH');
  assert.equal(s0.events.length, 0);
  const s1 = P.record(s0, { id: 'b', type: 'sale', at: 1, rows: [{ method: 'cash', amount: 500 }] }).shift;
  assert.equal(s0.events.length, 0, 'شیفتِ پیشین دست نخورده');
  const closed = P.closeShift(s1, { id: 'z', at: 2, counts: { 1000: 1 } });
  assert.equal(closed.report.variance.cash, -500, 'اختلاف ثبت می‌شود');
  assert.equal(closed.report.expected.cash, 1500, 'مبلغِ مورد انتظار خودکار «اصلاح» نمی‌شود');
  assert.ok(Object.isFrozen(closed.shift) && Object.isFrozen(closed.shift.z));
  assert.deepEqual(P.zReport(closed.shift).report, closed.report);
});

/** همان `shiftTotals`ِ امروزِ وب، از خودِ index.html */
function legacyShiftTotals() {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const start = html.indexOf('function shiftTotals(sh, now){');
  assert.ok(start > 0, 'shiftTotals در index.html پیدا نشد');
  const end = html.indexOf('\n  }\n', start);
  const src = html.slice(start, end + 4);
  // eslint-disable-next-line no-new-func
  return new Function('sales', 'transactions', 'returns', 'expenses', 'supplierPayments', 'purchases', src + '\nreturn shiftTotals;');
}

test('رفتارِ امروز (shiftTotals ِ index.html) و هسته روی داده‌های هم‌ارز', () => {
  const make = legacyShiftTotals();
  for (const c of V.legacyShift) {
    const d = c.data;
    const totals = make(d.sales, d.transactions, d.returns, d.expenses, d.supplierPayments, d.purchases);
    const t = totals({ open: { createdAt: c.from, amount: c.opening }, close: { createdAt: c.to } }, c.to);
    assert.equal(t.expected, c.legacyExpected, c.name + ' — موتورِ امروز');
    const shift = runSteps(c.steps.map((s) => Object.assign({ expect: { ok: true } }, s)), c.name);
    const x = P.xReport(shift, { at: c.to });
    assert.deepEqual(x.report.expected, c.coreExpected, c.name + ' — هسته');
    if (c.same) assert.equal(c.legacyExpected * 100, c.coreExpected.cash, c.name);
    else assert.notEqual(c.legacyExpected * 100, c.coreExpected.cash, c.name);
  }
});

test('هسته خالص است: نه DOM، نه ساعت، نه ذخیره، نه شبکه', () => {
  const src = fs.readFileSync(path.join(ROOT, 'license', 'pay-core.js'), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  for (const bad of ['document', 'localStorage', 'fetch(', 'Date', 'Math.random', 'XMLHttpRequest']) {
    assert.ok(!src.includes(bad), 'pay-core.js نباید ' + bad + ' داشته باشد');
  }
});

// ── POS-40: اصلاحِ روشِ پرداخت پس از فروش ─────────────────
test('POS-40 اصلاحِ روشِ پرداخت: سنجشِ سندِ تازه (بردارِ payFix.check)', () => {
  for (const c of V.payFix.check) {
    const r = P.payFix(c.input);
    assert.equal(r.ok, c.expect.ok, c.name + ' ⇐ ' + JSON.stringify(r));
    if (!c.expect.ok) {
      assert.equal(r.error, c.expect.error, c.name);
      for (const k of ['field', 'method']) if (k in c.expect) assert.equal(r[k], c.expect[k], c.name + ' — ' + k);
      continue;
    }
    assert.equal(r.duplicate, c.expect.duplicate, c.name);
    assert.deepEqual(r.fix, c.expect.fix, c.name);
  }
});

test('POS-40 اصلاحِ روشِ پرداخت: ردیف‌ها پس از اصلاح، جمعِ فاکتور همان (بردارِ payFix.apply)', () => {
  for (const c of V.payFix.apply) {
    const r = P.payFixApply(c.saleId, c.rows, c.fixes, c.until);
    assert.deepEqual(r, c.expect, c.name);
    assert.equal(sum(r.rows, (x) => x.amount), sum(c.rows, (x) => x.amount), c.name + ' — جمع');
    for (const row of r.rows) assert.ok(row.amount > 0, c.name + ' — بی ردیفِ صفر یا منفی');
  }
});

test('POS-40 اصلاحِ روشِ پرداخت: روزِ بسته (بردارِ payFix.closed)', () => {
  for (const c of V.payFix.closed) assert.equal(P.payFixClosed(c.saleAt, c.saleDay, c.shifts), c.expect, c.name);
});

test('POS-40: سندِ پذیرفته روی ردیف‌های همان فاکتور همان چیزی است که apply می‌سازد', () => {
  //  هر check ِ پذیرفته (نه تکراری) بر ردیف‌های پس از اصلاح‌های پیشین می‌نشیند و جمع را نگه می‌دارد
  for (const c of V.payFix.check) {
    if (!c.expect.ok || c.expect.duplicate) continue;
    const before = P.payFixApply(c.expect.fix.saleId, c.input.sale.rows, c.input.existing || []);
    const after = P.payFixApply(c.expect.fix.saleId, c.input.sale.rows, (c.input.existing || []).concat([c.expect.fix]));
    assert.deepEqual(after.skipped, before.skipped, c.name);
    assert.ok(after.applied.includes(c.expect.fix.id), c.name);
    assert.equal(sum(after.rows, (x) => x.amount), sum(before.rows, (x) => x.amount), c.name);
  }
});
