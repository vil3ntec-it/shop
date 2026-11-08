#!/usr/bin/env node
'use strict';
/**
 * فازِ تفاضلیِ هسته‌های دوقلوی JS و Kotlin — `core/test-vectors/core-fuzz-v1.json`.
 *
 *   node scripts/core-fuzz.js                ⇐ فقط می‌سنجد که فایلِ پیکره با خروجیِ امروزِ JS یکی است
 *   node scripts/core-fuzz.js --write        ⇐ پیکره را از نو می‌نویسد (پس از تغییرِ عمدیِ رفتار)
 *   CORE_FUZZ_CASES=20000 CORE_FUZZ_SEED=7 node scripts/core-fuzz.js --out /tmp/deep.json
 *                                            ⇐ اجرای ژرف: پیکرهٔ بزرگ بیرون از مخزن (هر دور N مورد)
 *   CORE_FUZZ_ONLY=perm,approval CORE_FUZZ_CASES=20000 node scripts/core-fuzz.js --out /tmp/perm.json
 *                                            ⇐ اجرای ژرفِ فقط همین خانواده‌ها (N مورد روی هم)
 *
 * روش (الف): **خروجیِ JS همان expect است**. مولّدِ دانه‌دار (mulberry32، بی Date.now و بی Math.random)
 * دفترهای واقعی می‌سازد — خرید با بها و واحدِ گوناگون، فروش با تخفیف و قاعدهٔ تخفیف، برگشتِ بخشی و
 * کامل، لغو، شمارش و انتقال، قرض و رسید، لبه‌های تقویم (پایانِ ماه و سالِ خورشیدی، کبیسه)، عددهای
 * لبهٔ بازهٔ امنِ ۲^۵۳ و ورودی‌های صفر/منفی/نادرستی که هسته باید رد کند — هر مورد را با هستهٔ JS
 * می‌دواند و `out` را کنارش می‌نویسد. دو دونده همان پرونده را می‌خوانند:
 *   • `server/test/core-fuzz.test.js` (node --test): پیکره = خروجیِ امروزِ مولّد + JS (بایت‌به‌بایت)،
 *     و چند قاعدهٔ گردکردن با BigInt ِ مستقل (نه از خودِ هسته)؛
 *   • `core/src/jvmTest/.../CoreFuzzTest.kt` (پول، سقفِ برگشت، تخفیف، تقویم، متن) و
 *     `android-native/app/src/test/.../CoreFuzzAppTest.kt` (موجودی، گزارش، دفترِ قرض؛ همتاهای Kotlin
 *     آن‌ها هنوز در `android-native` اند، پس روی کامپیوتر/اندروید دویده می‌شوند): هر `out` را با
 *     هستهٔ Kotlin می‌سنجند. با `CORE_FUZZ_CORPUS=<مسیر>` همان آزمون‌ها پیکرهٔ ژرف را می‌خوانند.
 * دورِ دوم (`core-fuzz-gen2.js` و `core-fuzz-gen3.js`، دانهٔ SEED + 1، پس از ۲۰۰ موردِ دورِ اول و بی جابه‌جا کردنِ آن‌ها): برگهٔ تأیید،
 * مجوز، زنجیرهٔ هش، پرداخت، جمع‌آوری، سفارشِ خرید و تابلوی گزارش — همه در `CoreFuzzTest` جز تابلو (`CoreFuzzAppTest`).
 * خطا (پرتابِ JS / Exception ِ Kotlin) یعنی `{"throws": true}` — دو سو باید با هم رد کنند.
 * ورودیِ اعشاری که JSON ندارد با رشته می‌آید: "NaN"، "Infinity"، "-Infinity"، "-0" (تابعِ D).
 * هر تفاوت: کمینه کن ⇐ سوی درست را از قاعده‌ها پیدا کن ⇐ همان را درست کن ⇐ موردِ کمینه را به
 * بردارِ دست‌نوشتهٔ همان هسته بیفزا. این پرونده را با دست عوض نکن: `--write`.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const CORPUS = path.join(ROOT, 'core', 'test-vectors', 'core-fuzz-v1.json');
const SEED = 20261108;
/** موردهای هر دور در پیکرهٔ مخزن (دو دور ⇒ ۴۰۰) */
const COUNT = 200;
const VERSION = 1;

/* کمک‌های دانه‌دار و خانواده‌های پول، سقفِ برگشت، موجودی و گزارش */
const {
  PROMO, DEBT, CAL, TEXT, MAX, rng, D, J, legacyDouble, BAD_DATES, EDGE_DATES, nearDate, baseDate, kabulMs, messy,
  money, runMoney, cap, runCap, stock, runStock, report, runReport,
} = require('./core-fuzz-gen.js');
const R2 = Object.assign({}, require('./core-fuzz-gen2.js'), require('./core-fuzz-gen3.js'));

/* ---------- تخفیف: license/promo-core.js ⇄ core/promo/PromoCore.kt ---------- */

const PRODUCTS = ['A', 'B', 'C', 'D'];
const CATS = ['drinks', 'snacks', 'soap'];

function promoScope(r) {
  if (r.chance(0.4)) return undefined;
  return r.weighted([
    [3, () => ({ products: [r.pick(PRODUCTS)] })],
    [2, () => ({ categories: [r.pick(CATS)] })],
    [1, () => ({ suppliers: [r.pick(['s1', 's2'])] })],
    [1, () => ({ products: [r.pick(PRODUCTS), r.pick(PRODUCTS)], exProducts: [r.pick(PRODUCTS)] })],
    [1, () => ({ brands: ['b1'], exCategories: [r.pick(CATS)] })],
  ]);
}

function promoRule(r, i) {
  const kind = r.weighted([[6, 'percent'], [3, 'amount'], [2, 'price'], [3, 'tiers'], [3, 'bxgy'], [2, 'bundle'], [3, 'threshold'], [2, 'expiry'], [1, 'bogus']]);
  const o = { id: 'r' + i, name: r.pick(['ده درصد', 'ویژه', 'x', ' ', '']), kind };
  const bp = () => r.weighted([[5, () => r.pick([500, 1000, 1500, 2500, 3333, 5000, 10000])], [2, () => r.int(1, 10000)], [1, () => r.pick([0, -100, 10001])]]);
  if (kind === 'percent') o.bp = bp();
  if (kind === 'amount') o.amountMinor = r.weighted([[5, () => r.int(1, 50) * 100], [2, () => r.int(1, 9999)], [1, () => r.pick([0, -100])]]);
  if (kind === 'price') o.prices = [{ productId: r.pick(PRODUCTS), unitMinor: r.weighted([[4, () => r.int(1, 200) * 100], [1, () => r.int(0, 99999)], [1, () => -1]]) }];
  if (kind === 'tiers') {
    const unit = r.chance(0.35);
    o.tiers = [];
    let min = r.pick([1000, 2000, 3000]);
    for (let k = r.int(1, 3); k > 0; k--) {
      o.tiers.push(unit ? { min, unitMinor: r.int(10, 200) * 100 } : { min, bp: bp() });
      min += r.pick([1000, 2000, 3000, 0]);
    }
  }
  if (kind === 'bxgy') { o.buy = r.pick([1, 2, 3, 0]); o.get = r.pick([1, 1, 2, 0]); o.bp = r.pick([10000, 5000, bp()]); }
  if (kind === 'bundle') { o.qty = r.pick([2, 3, 0]); o.priceMinor = r.int(1, 500) * 100; }
  if (kind === 'threshold') {
    o.tiers = [];
    let min = r.pick([20000, 40000, 50000]);
    for (let k = r.int(1, 2); k > 0; k--) {
      o.tiers.push(r.chance(0.5) ? { min, bp: bp() } : { min, amountMinor: r.int(1, 100) * 100 });
      min += r.pick([20000, 50000]);
    }
  }
  if (kind === 'expiry') { o.bp = bp(); o.nearDays = r.pick([7, 3, 30, 0, -1, 366]); }
  const scope = promoScope(r);
  if (scope) o.scope = scope;
  if (r.chance(0.2)) o.stack = r.pick(['exclusive', 'best', 'combine', 'other']);
  if (r.chance(0.2)) o.priority = r.pick([0, 1, 5, -1, 2000]);
  if (r.chance(0.2)) { o.code = r.pick(['SALE10', 'sale-10', '۱۲۳ABC', 'ab', 'VIP_1']); if (r.chance(0.5)) { o.maxUses = r.pick([0, 1, 5]); o.uses = r.pick([0, 1, 5]); } }
  if (r.chance(0.1)) o.active = false;
  if (r.chance(0.15)) o.allowBelowCost = true;
  if (r.chance(0.1)) o.groups = ['vip'];
  if (r.chance(0.1)) o.channels = [r.pick(['pos', 'online', 'web'])];
  if (r.chance(0.2)) o.window = promoWindow(r);
  return o;
}

function promoWindow(r) {
  const w = {};
  const base = baseDate(r);
  if (r.chance(0.5)) w.from = r.chance(0.9) ? nearDate(r, base, 5) : r.pick(BAD_DATES);
  if (r.chance(0.5)) w.to = r.chance(0.9) ? nearDate(r, base, 5) : r.pick(BAD_DATES);
  if (r.chance(0.3)) w.days = [r.int(1, 7), r.int(1, 7)].concat(r.chance(0.1) ? [8] : []);
  if (r.chance(0.4)) { w.start = r.pick([0, 600, 1020, 1320, 1439]); w.end = r.pick([0, 120, 600, 1140, 1439, 1440]); }
  if (r.chance(0.2)) w.except = [nearDate(r, base, 3)];
  return w;
}

function promoCart(r) {
  const lines = [];
  for (let i = 0, n = r.int(1, 5); i < n; i++) {
    const unit = r.weighted([[6, () => r.int(1, 500) * 100], [2, () => r.int(1, 99999)], [1, () => r.pick([0, 1, MAX])]]);
    const l = {
      key: 'k' + i, productId: r.pick(PRODUCTS), qtyMilli: r.weighted([[6, () => r.int(1, 8) * 1000], [2, () => r.int(1, 9999)], [1, () => r.pick([0, -1000])]]),
      unitMinor: unit, costMinor: Math.round(unit * r.pick([0, 0.6, 0.9, 1.1])), category: r.pick(CATS), supplierId: r.pick(['s1', 's2']),
    };
    if (r.chance(0.1)) l.amountMinor = Math.round((l.qtyMilli * l.unitMinor) / 1000) + r.pick([0, 1, -50]);
    if (r.chance(0.2)) l.brand = 'b1';
    if (r.chance(0.05)) l.noPromo = true;
    if (r.chance(0.2)) l.lots = [{ daysLeft: r.int(-2, 40), qtyMilli: r.int(1, Math.max(1, l.qtyMilli)) }];
    lines.push(l);
  }
  const cart = { lines };
  if (r.chance(0.25)) cart.coupons = [r.pick(['SALE10', 'sale-10', 'nope', '۱۲۳abc', 'VIP_1'])];
  if (r.chance(0.15)) cart.group = 'vip';
  if (r.chance(0.15)) cart.channel = r.pick(['pos', 'online']);
  if (r.chance(0.3)) cart.at = { date: r.chance(0.95) ? baseDate(r) : r.pick(BAD_DATES), minute: r.pick([0, 600, 1019, 1020, 1200, 1439, 1440, -1]) };
  if (r.chance(0.15)) cart.stepMinor = r.pick([1, 500, 0, 100]);
  return cart;
}

const promo = {
  'promo.evaluate': (r) => {
    const rules = [];
    for (let i = 0, n = r.int(0, 4); i < n; i++) rules.push(promoRule(r, i));
    return { cart: promoCart(r), rules };
  },
  'promo.validate': (r) => {
    const o = { rule: promoRule(r, 0) };
    if (r.chance(0.3)) o.step = r.pick([1, 100, 500, 0, -5]);
    return o;
  },
  'promo.inWindow': (r) => ({
    window: promoWindow(r),
    at: r.chance(0.9) ? { date: r.chance(0.9) ? baseDate(r) : r.pick(BAD_DATES), minute: r.pick([0, 1, 599, 600, 1019, 1020, 1139, 1140, 1320, 1439, 1440, -1, r.int(0, 1439)]) } : null,
  }),
  'promo.isoDow': (r) => ({ date: r.chance(0.85) ? CAL.addDays('2026-01-01', r.int(-800000, 2900000)) || '2026-01-01' : r.pick(BAD_DATES) }),
  'promo.normalizeCode': (r) => ({ s: textString(r, 12) }),
};

function runPromo(fn, x) {
  switch (fn) {
    case 'promo.evaluate': return PROMO.evaluate(x.cart, x.rules);
    case 'promo.validate': return PROMO.validate(x.rule, x.step);
    case 'promo.inWindow': return PROMO.inWindow(x.window, x.at);
    case 'promo.isoDow': return PROMO.isoDow(x.date);
    case 'promo.normalizeCode': return PROMO.normalizeCode(x.s);
    default: throw new Error('fn: ' + fn);
  }
}

/* ---------- دفترِ قرض: license/debt-book.js ⇄ android-native/.../data/DebtBook.kt ---------- */

function debtLedger(r) {
  const today = baseDate(r);
  const L = { products: [{ id: 'p1', name: 'روغن', unit: 'بوتل', salePrice: 130 }, { id: 'p2', name: 'برنج', unit: '', salePrice: 90 }], debtors: [], sales: [], saleItems: [], transactions: [] };
  const c1 = { id: 'c1', name: 'احمد', phone: '', notes: '', createdAt: 0 };
  if (r.chance(0.4)) c1.creditLimit = r.pick([0, 500, 1000, 5000, 250.5, -1]);
  L.debtors.push(c1);
  if (r.chance(0.3)) L.debtors.push({ id: 'c2', name: 'کریم' });
  let inv = 0;
  for (let i = 1, n = r.int(1, 7); i <= n; i++) {
    const give = r.chance(0.6);
    const date = nearDate(r, today, 30);
    const debtorId = L.debtors.length > 1 && r.chance(0.2) ? 'c2' : 'c1';
    const amount = r.weighted([[5, () => r.int(1, 200) * 5], [3, () => r.int(1, 99999) / 100], [1, () => r.pick([0, 0.1, 0.005, 1.005, 250.5])], [1, () => -r.int(1, 50)]]);
    const t = { id: (give ? 'g' : 'r') + i, debtorId, type: give ? 'give' : 'receive', amount, date: r.chance(0.95) ? date : '', notes: '', createdAt: r.chance(0.8) ? i : r.int(0, 3) };
    if (give && r.chance(0.5)) t.dueDate = r.pick([nearDate(r, today, 20), nearDate(r, today, 20), '', ' ' + nearDate(r, today, 5) + ' ', '\uFEFF' + nearDate(r, today, 5), nearDate(r, today, 5) + '\u001F']);
    if (give && r.chance(0.4)) {
      inv++;
      const series = r.chance(0.3) ? r.pick(['A1', 'B1', ' B1 ', '\uFEFFB1']) : undefined;
      const label = series ? series.trim() + '-' + inv : String(inv);
      t.notes = 'فروش نسیه — ' + r.weighted([[8, 'فاکتور #'], [1, 'فاکتور\u00A0#'], [1, 'فاکتور #\u2009'], [1, 'فاکتور#'], [1, 'فاکتور\uFEFF#']]) + label;
      const sale = { id: 's' + inv, invoiceNumber: inv, debtorId, total: amount, discount: 0, finalTotal: amount, paidAmount: 0, remaining: amount, paymentMethod: 'credit', status: 'completed', date, createdAt: i };
      if (series) sale.series = series;
      L.sales.push(sale);
      for (let k = r.int(1, 2); k > 0; k--) {
        const q = r.pick([1, 2, 1.5, 0.25]);
        const it = { id: 'i' + inv + '_' + k, saleId: sale.id, productId: r.pick(['p1', 'p2', '']), quantity: q, unitPrice: r.int(1, 999) / r.pick([1, 4]), totalPrice: amount / 2 };
        if (it.productId === '') it.name = 'قلم آزاد';
        if (r.chance(0.3)) it.returnedQty = r.pick([0, 1, 0.5]);
        L.saleItems.push(it);
      }
    } else if (!give && r.chance(0.2) && inv > 0) {
      t.notes = 'رسید — فاکتور #' + r.int(1, inv);
    }
    L.transactions.push(t);
  }
  return { ledger: L, debtorId: r.chance(0.95) ? 'c1' : 'zz', today, extraMinor: r.chance(0.5) ? r.int(0, 600000) : null };
}

const debt = { 'debt.book': debtLedger };

function runDebt(x) {
  const b = DEBT.book(x.ledger, x.debtorId, x.today);
  const cc = x.extraMinor === null ? null : DEBT.creditCheck(x.ledger, x.debtorId, x.extraMinor);
  const credit = (c) => (c ? { limitMinor: c.limitMinor, balanceMinor: c.balanceMinor, headroomMinor: c.headroomMinor, over: c.over } : null);
  return {
    book: b ? {
      balanceMinor: b.balanceMinor, overdueMinor: b.overdueMinor, overdue: b.overdue.map((e) => e.tx.id), nextDue: b.nextDue ? b.nextDue.tx.id : null,
      credit: credit(b.credit),
      entries: b.entries.map((e) => ({
        id: e.tx.id, row: e.row, give: e.give, amountMinor: e.amountMinor, balanceBeforeMinor: e.balanceBeforeMinor, balanceAfterMinor: e.balanceAfterMinor,
        openMinor: e.openMinor, overdue: e.overdue, paid: e.paid, due: e.dueDate, sale: e.sale ? e.sale.id : null, invoiceLabel: e.invoiceLabel,
        allocations: e.allocations, lines: e.lines,
      })),
    } : null,
    creditCheck: cc ? { limitMinor: cc.limitMinor, balanceMinor: cc.balanceMinor, afterMinor: cc.afterMinor, over: cc.over, excessMinor: cc.excessMinor } : null,
  };
}

/* ---------- تقویم: license/calendar-core.js ⇄ core/calendar/*.kt ---------- */

function anyIso(r) {
  return r.weighted([
    [6, () => nearDate(r, r.pick(EDGE_DATES), 2)],
    [3, () => CAL.isoOfJdn(r.int(1721426, 5373484))],
    [1, () => CAL.isoOfJdn(r.pick([1721426, 1721425, 5373484, 5373485, 1948439, 1948440, 2440588]))],
    [1, () => r.pick(BAD_DATES)],
  ]);
}
const calendar = {
  'cal.toSolar': (r) => ({ iso: anyIso(r) }),
  'cal.fromSolar': (r) => ({ y: r.weighted([[6, () => r.int(1390, 1420)], [2, () => r.int(1, 3177)], [1, () => r.pick([0, 1, 3177, 3178, -1])]]), m: r.weighted([[8, () => r.int(1, 12)], [1, () => r.pick([0, 13])]]), d: r.weighted([[6, () => r.int(1, 29)], [3, () => r.pick([29, 30, 31])], [1, () => r.pick([0, 32])]]) }),
  'cal.solarYear': (r) => ({ y: r.weighted([[5, () => r.int(1300, 1500)], [3, () => r.int(1, 3177)], [1, () => r.pick([0, -1, 3177, 3178, 3200])]]), q: r.int(0, 5) }),
  'cal.days': (r) => ({ iso: anyIso(r), n: r.weighted([[5, () => r.int(-400, 400)], [1, () => r.int(-800000, 800000)]]) }),
  'cal.jdn': (r) => ({ jdn: r.weighted([[5, () => r.int(1721426, 5373484)], [1, () => r.int(-1000, 1721426)], [1, () => r.int(5373484, 6000000)]]) }),
  'cal.lunar': (r) => ({ iso: anyIso(r), adjust: r.pick([0, 0, 1, -1, 2, -2, 3]) }),
  'cal.format': (r) => ({ iso: anyIso(r), lang: r.pick(['fa', 'ps', 'en', '']), digits: r.pick(['fa', 'latn', '']), adjust: r.pick([0, 1, -1]) }),
  'cal.week': (r) => {
    const o = { iso: anyIso(r) };
    if (r.chance(0.7)) {
      o.rules = {};
      if (r.chance(0.7)) o.rules.weekStart = r.int(1, 7);
      if (r.chance(0.6)) o.rules.weekend = r.pick([[5], [5, 6], [], [7], [4, 5]]);
      if (r.chance(0.5)) o.rules.holidays = [o.iso, nearDate(r, '2026-03-21', 3) || '2026-03-21'];
    }
    return o;
  },
  'cal.businessDate': (r) => {
    const date = r.chance(0.9) ? anyIso(r) : r.pick(BAD_DATES);
    const hh = String(r.weighted([[9, () => r.int(0, 23)], [1, () => r.pick([24, 25, 99])]])).padStart(2, '0');
    const mm = String(r.weighted([[9, () => r.int(0, 59)], [1, () => r.pick([60, 99])]])).padStart(2, '0');
    return { local: date + 'T' + hh + ':' + mm + (r.chance(0.3) ? ':' + String(r.int(0, 59)).padStart(2, '0') : '') + (r.chance(0.03) ? 'Z' : ''), start: r.pick([0, 0, 60, 120, 240, 299, 300]) };
  },
  'cal.businessDateAt': (r) => {
    const date = nearDate(r, r.pick(EDGE_DATES), 1);
    return { ms: kabulMs(date, r.int(-60, 1500)) + r.int(-59999, 59999), off: r.pick([270, 270, 0, -300, 330, 840, -720]), start: r.pick([0, 0, 120, 240, 299]) };
  },
};

function runCal(fn, x) {
  switch (fn) {
    case 'cal.toSolar': return { solar: CAL.toSolar(x.iso), quarter: CAL.fiscalQuarterOf(x.iso), jdn: CAL.jdnOfIso(x.iso), weekday: CAL.isoWeekday(x.iso) };
    case 'cal.fromSolar': return { iso: CAL.fromSolar(x.y, x.m, x.d), monthLength: CAL.solarMonthLength(x.y, x.m) };
    case 'cal.solarYear': return { leap: CAL.isSolarLeap(x.y), year: CAL.fiscalYearRange(x.y), quarter: CAL.fiscalQuarterRange(x.y, x.q) };
    case 'cal.days': return CAL.addDays(x.iso, x.n);
    case 'cal.jdn': return CAL.isoOfJdn(x.jdn);
    case 'cal.lunar': return CAL.toLunar(x.iso, x.adjust);
    case 'cal.format': return {
      numeric: CAL.formatSolarNumeric(x.iso, x.digits), long: CAL.formatSolarLong(x.iso, x.lang, x.digits), lunar: CAL.formatLunarLong(x.iso, x.digits, x.adjust),
    };
    case 'cal.week': return { weekStart: CAL.weekStartOf(x.iso, x.rules), kind: CAL.dayKind(x.iso, x.rules), working: CAL.isWorkingDay(x.iso, x.rules) };
    case 'cal.businessDate': return CAL.businessDate(x.local, x.start);
    case 'cal.businessDateAt': return CAL.businessDateAt(x.ms, x.off, x.start);
    default: throw new Error('fn: ' + fn);
  }
}

/* ---------- متن: license/text-core.js ⇄ core/text/{TextNormalizer,LocaleFormat}.kt ---------- */

const ALPHA = ['ی', 'ي', 'ى', 'ک', 'ك', 'ه', 'ۀ', 'ة', 'ا', 'أ', 'إ', 'آ', 'ب', 'چ', 'ژ', 'ً', 'ُ', 'ّ', 'ٰ', 'ـ', '۰', '۵', '۹', '٠', '٣', '0', '7', '٫', '٬', ',', '.',
  ' ', ' ', ' ', '\t', '\n', '\u00A0', '\u2007', '\u202F', '\u3000', '\u200C', '\u200D', '\u200E', '\u202B', '\u2066', '\uFEFF', '\u00AD', '\u061C', '\u200B', '\u0085', '\u001F',
  'A', 'Z', 'a', 'z', 'İ', 'Σ', 'ß', 'Ä', '-', '(', ')', '+', '#', '😀', '\uD83D', 'ı', 'K'];
function textString(r, max) {
  let s = '';
  for (let i = r.int(0, max); i > 0; i--) s += r.pick(ALPHA);
  return s;
}
const WORDS = ['چای سبز', 'روغن', 'برنج', 'پیپسي', 'كوكا', 'Coca Cola', 'شیر ۲ لیتری', 'آب معدنی', 'صابون'];
function phoneString(r) {
  return r.weighted([
    [4, () => r.pick(['07', '+93 7', '0093', '93', '7', '+1 ', '00 1 ']) + r.int(0, 99999999).toString().padStart(8, '0')],
    [2, () => r.pick(['۰۷۹۹ ۱۲۳ ۴۵۶', '+۹۳ ۷۹۹-۱۲۳-۴۵۶', '(0799) 123.456', '٠٧٩٩١٢٣٤٥٦', '\u200E+93799123456', '0799123456\n'])],
    [1, () => textString(r, 14)],
    [1, () => r.pick(['+930799123456', '+93 0799 123 456', '93799123456', '799123456', '0599123456', '+44', '+123456789012345', '+1234567890123456', ''])],
  ]);
}
const text = {
  'text.key': (r) => ({ s: r.chance(0.5) ? textString(r, 16) : messy(r, r.pick(WORDS)) + (r.chance(0.3) ? textString(r, 4) : '') }),
  'text.match': (r) => {
    const word = r.pick(WORDS);
    const q = r.weighted([[3, () => word.slice(r.int(0, 2), r.int(3, word.length))], [2, () => textString(r, 6)], [2, () => word.replace(/ی/g, 'ي').replace(/ک/g, 'ك')],
      [2, () => word.slice(0, -1) + r.pick(['x', 'ی', ''])], [1, () => ' ']]);
    const o = { text: r.chance(0.8) ? word + (r.chance(0.3) ? ' ' + r.pick(WORDS) : '') : textString(r, 10), query: q, max: r.int(0, 3) };
    if (r.chance(0.3)) o.synonyms = [['پیپسی', 'نوشابه', 'pepsi'], [r.pick(WORDS), q]];
    return o;
  },
  'text.digits': (r) => ({ s: textString(r, 12), style: r.pick(['fa', 'latn', '']) }),
  'text.afghani': (r) => ({ amount: J(r.weighted([[5, () => r.sign() * r.int(0, 1e9)], [3, () => legacyDouble(r)], [1, () => r.pick([-0.5, 0.5, -1.5, 999.5, 1e15, 9007199254740991, -0.4])]])), symbol: r.pick(['sign', 'word', 'none', '']), digits: r.pick(['fa', 'latn']) }),
  'text.phone': (r) => ({ s: phoneString(r) }),
};

function runText(fn, x) {
  switch (fn) {
    case 'text.key': return { search: TEXT.searchKey(x.s), compare: TEXT.compareKey(x.s), ascii: TEXT.asciiDigits(x.s) };
    case 'text.match': return {
      matches: TEXT.matches(x.text, x.query, x.synonyms), fuzzy: TEXT.fuzzyMatches(x.text, x.query),
      distance: TEXT.editDistance(x.text, x.query, x.max), budget: TEXT.typoBudget(x.query.length),
    };
    case 'text.digits': return TEXT.displayDigits(x.s, x.style);
    case 'text.afghani': return TEXT.formatAfghani(D(x.amount), x.symbol, x.digits);
    case 'text.phone': {
      const n = TEXT.normalizePhone(x.s);
      return { normalized: n, formatted: TEXT.formatPhone(n === null ? x.s : n), raw: TEXT.formatPhone(x.s) };
    }
    default: throw new Error('fn: ' + fn);
  }
}

/* ======================= پیکره ======================= */

/** [وزن، خانواده، مولّدها] — وزن ≈ پیچیدگی؛ همهٔ تابع‌های یک خانواده هم‌وزن‌اند */
const FAMILIES = [
  [26, money, runMoney],
  [10, cap, runCap],
  [34, stock, runStock],
  [18, report, (fn, x) => runReport(x)],
  [32, promo, runPromo],
  [12, debt, (fn, x) => runDebt(x)],
  [24, calendar, runCal],
  [14, text, runText],
];
/** دورِ دوم: هم‌زادهایی که دورِ اول نداشت (`core-fuzz-gen2.js`) */
const FAMILIES2 = [
  [14, R2.approval, R2.runApproval],
  [22, R2.perm, R2.runPerm],
  [16, R2.chain, R2.runChain],
  [26, R2.pay, R2.runPay],
  [24, R2.pick, R2.runPick],
  [20, R2.po, R2.runPo],
  [14, R2.board, R2.runBoard],
];
/** سهمِ بیشتر درونِ خانواده برای دفترهای چندگامی */
const BOOST = {
  'stock.ledger': 3, 'promo.evaluate': 3, 'money.allocateDiscount': 2, 'money.returnShare': 2, 'cap.check': 2,
  'perm.rows': 2, 'perm.resolve': 2, 'pay.settle': 2, 'pay.fix': 2, 'po.flow': 3, 'chain.verify': 2, 'board.build': 2,
};
const RUNNERS = {};
for (const [, gens, run] of FAMILIES.concat(FAMILIES2)) for (const fn of Object.keys(gens)) RUNNERS[fn] = run;

/** خروجیِ JS برای یک مورد؛ پرتاب ⇒ {throws: true} */
function runCase(fn, input) {
  let out;
  try { out = RUNNERS[fn](fn, input); } catch (e) { return { throws: true }; }
  //  -0 و 0 در JSON و در هر مقایسهٔ هسته یکی‌اند (Long ِ Kotlin -0 ندارد)؛ فقط NaN/Infinity رمز می‌شوند
  if (typeof out === 'number') return J(out === 0 ? 0 : out);
  return out === undefined ? null : JSON.parse(JSON.stringify(out));
}

/** `count` مورد از خانواده‌های `families` با دانهٔ `seed`؛ شناسه‌ها از `from` */
function round(families, seed, count, from, cases) {
  const r = rng(seed);
  const list = [];
  for (const [w, gens] of families) {
    const fns = Object.keys(gens);
    const parts = fns.reduce((a, fn) => a + (BOOST[fn] || 1), 0);
    for (const fn of fns) list.push([(w * (BOOST[fn] || 1)) / parts, [fn, gens[fn]]]);
  }
  for (let i = 0; i < count; i++) {
    const [fn, gen] = r.weighted(list);
    const input = JSON.parse(JSON.stringify(gen(r)));
    cases.push({ id: from + i, fn, in: input, out: runCase(fn, input) });
  }
  return cases;
}

/** نامِ خانواده‌ها (پیشوندِ fn) */
const familyOf = ([, gens]) => Object.keys(gens)[0].split('.')[0];
const FAMILY_NAMES = FAMILIES.concat(FAMILIES2).map(familyOf);

/**
 *  دورِ اول (`seed`، `count`) و پسِ آن دورِ دوم (`seed + 1`، `count`) — پس ۲۰۰ موردِ نخستِ پیکرهٔ مخزن همان دورِ اول‌اند.
 *  `only` (فهرستِ نامِ خانواده‌ها) ⇒ فقط همان‌ها، یک دور، `count` مورد روی هم (اجرای ژرفِ یک جفت).
 */
function generate(seed = SEED, count = COUNT, only = null) {
  const cases = [];
  if (only && only.length) {
    const unknown = only.filter((f) => !FAMILY_NAMES.includes(f));
    if (unknown.length) throw new Error('core-fuzz: خانوادهٔ ناشناخته ' + unknown.join(','));  // داده: خطای برنامه‌نویس
    round(FAMILIES.concat(FAMILIES2).filter((f) => only.includes(familyOf(f))), seed, count, 0, cases);
  } else {
    round(FAMILIES, seed, count, 0, cases);
    round(FAMILIES2, seed + 1, count, count, cases);
  }
  return { version: VERSION, seed, count: cases.length, cases };
}

/** نویسه‌های نادیدنی (فاصله‌ها، BOM، جهت‌نما) در پیکره با \\uXXXX، تا پرونده خوانا و بی نویسهٔ پنهان بماند */
function visible(text) {
  return text.replace(/[\u007f-\u009f\u00a0\u00ad\u061c\u1680\u2000-\u200f\u2028-\u202f\u205f-\u206f\u3000\ufeff\ufff9-\ufffb]/g,
    (ch) => '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0'));
}

function serialize(c) {
  return visible('{\n  "version": ' + c.version + ',\n  "about": ' + JSON.stringify(
    'فازِ تفاضلیِ هسته‌های دوقلوی JS و Kotlin (server/scripts/core-fuzz.js). out = خروجیِ هستهٔ JS؛ Kotlin باید عیناً همان را بدهد ' +
    '(CoreFuzzTest.kt، CoreFuzzAppTest.kt). {"throws": true} = هر دو رد می‌کنند. "NaN"/"Infinity"/"-0" = عددِ اعشاریِ ویژه. با دست عوض نکن: node server/scripts/core-fuzz.js --write',
  ) + ',\n  "seed": ' + c.seed + ',\n  "count": ' + c.count + ',\n  "cases": [\n' +
    c.cases.map((x) => '    ' + JSON.stringify(x)).join(',\n') + '\n  ]\n}\n');
}

module.exports = { CORPUS, SEED, COUNT, FAMILY_NAMES, generate, serialize, runCase, rng, D, J, R2 };

if (require.main === module) {
  const args = process.argv.slice(2);
  const outAt = args.indexOf('--out');
  const count = Number(process.env.CORE_FUZZ_CASES) || COUNT;
  const seed = Number(process.env.CORE_FUZZ_SEED) || SEED;
  const only = (process.env.CORE_FUZZ_ONLY || '').split(',').filter(Boolean);
  const text = serialize(generate(seed, count, only));
  if (outAt >= 0) {
    fs.writeFileSync(args[outAt + 1], text);
    console.log(`core-fuzz: ${only.length ? count : count * 2} مورد (دانهٔ ${seed}${only.length ? '، فقط ' + only : ''}) ⇐ ${args[outAt + 1]}`);
  } else if (args.includes('--write')) {
    fs.writeFileSync(CORPUS, text);
    console.log(`core-fuzz: ${count * 2} مورد ⇐ ${path.relative(ROOT, CORPUS)}`);
  } else {
    const same = fs.existsSync(CORPUS) && fs.readFileSync(CORPUS, 'utf8') === text;
    console.log(same ? 'core-fuzz: پیکره با خروجیِ امروز یکی است' : 'core-fuzz: پیکره کهنه است — node server/scripts/core-fuzz.js --write');
    process.exit(same ? 0 : 1);
  }
}
