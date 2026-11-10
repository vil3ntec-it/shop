'use strict';
/**
 * STF-03 — سقف‌های فاکتورِ کارمند: همتای دقیقِ `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/perm/SaleCaps.kt`.
 * هر دو با `core/test-vectors/sale-caps-v1.json` سنجیده می‌شوند (`test/sale-caps.test.js`، `SaleCapsVectorsTest`).
 * ⛔ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید. صندوق با همین قاعده پیش‌سنجی می‌کند و سرور با همین داوری.
 *
 * خالص: بی دیتابیس. همهٔ مبلغ‌ها Minor (یک‌صدمِ افغانی) و مقدارها Milli، عددِ صحیح. شرحِ قاعده‌ها سرِ همان پروندهٔ Kotlin.
 *
 * تصمیمِ ۱-۱۱ (۱۴۰۵/۰۸/۱۹)، هر دو فقط با `enforcePrice` (پرچمِ `shop.price_cap`):
 *   (۲) قلمِ آزاد (ردیفِ بی کارتِ کالا: `productId` خالی یا کالایی که این‌سو ندارد) ⇒ نیازِ `sales.free_line` با
 *       `amount` = بیشترین قیمتِ واحدِ قلم‌های آزاد (Minor)؛ بی قلمِ آزاد ⇒ هیچ.
 *   (۳) قیمتِ عمده فقط وقتی «قیمتِ فهرست» است که فاکتور لیستِ عمده دارد (`sale.wholesale` = مشتریِ فاکتور لیستِ پیش‌فرضِ
 *       عمده دارد)؛ وگرنه فروش به قیمتِ عمده کاهشِ ردیف (تخفیفِ پنهان) است و همان سقفِ `sales.discount` را می‌خواهد.
 */

const DISCOUNT = 'sales.discount';
const PRICE = 'sales.price';
const FREE_LINE = 'sales.free_line';
const CREDIT = 'sales.credit';
const PRICE_FLAG = 'shop.price_cap';
const CREDIT_FLAG = 'shop.credit_cap';
const ROUND_MAX_MINOR = 500;
const TOL_MINOR = 100;
const FULL_BP = 10000;
const STEP = 100n;
const MAX_EXACT = BigInt(Number.MAX_SAFE_INTEGER);
const LIM = 9223372036854775807n / BigInt(FULL_BP);

const int = (v) => { const n = Number(v); return Number.isFinite(n) ? Math.round(n) : 0; };
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** جمعِ قرضِ فاکتور پس از همین درج */
function creditAmount(debtGivenMinor, priorMinor = 0) {
  const d = int(debtGivenMinor);
  return d > 0 ? d + Math.max(0, int(priorMinor)) : 0;
}

/** مبلغِ ردیف = round(قیمت × مقدار ÷ ۱۰۰۰) به پلهٔ یک افغانی، نیمه رو به بالا — همان `LinePricing.grossMinor` */
function gross(unitMinor, qtyMilli) {
  const u = BigInt(int(unitMinor));
  const q = BigInt(int(qtyMilli));
  if (u < 0n || q < 0n || u > MAX_EXACT || q > MAX_EXACT) throw new RangeError('out of exact range');
  const p = u * q;
  const d = 1000n * STEP;
  let r = p / d;
  if ((p % d) * 2n >= d) r += 1n;
  const out = r * STEP;
  if (out > MAX_EXACT) throw new RangeError('out of exact range');
  return Number(out);
}

function divHalfUp(a, b) { return Math.floor(a / b) + ((a % b) * 2 >= b ? 1 : 0); }

/** قیمت‌های واحدِ فهرستِ کالا در روزِ `date`، صعودی و بی تکرار؛ `wholesale` نادرست ⇒ قیمتِ عمده فهرست نیست (۱-۱۱ (۳)) */
function refUnits(p, date, wholesale = true) {
  const windows = Array.isArray(p.windows) ? p.windows : [];
  const w = windows.find((x) => typeof date === 'string' && date !== '' && date >= x.from && date <= x.to);
  const base = w ? int(w.priceMinor) : int(p.saleMinor);
  const out = new Set([Math.max(0, base)]);
  if (wholesale !== false && int(p.wholesaleMinor) > 0) out.add(int(p.wholesaleMinor));
  for (const k of Array.isArray(p.packs) ? p.packs : []) {
    const size = int(k.sizeMilli);
    const price = int(k.priceMinor);
    if (size <= 0 || price <= 0 || price > Math.floor(Number.MAX_SAFE_INTEGER / 1000)) continue;
    out.add(divHalfUp(price * 1000, size));
  }
  return [...out].sort((a, b) => a - b);
}

const EXEMPT = Object.freeze({ exempt: true, listMinor: 0, soldMinor: 0, deviationBp: 0, reductionMinor: 0 });
const dist = (a, b) => Math.abs(a - b);

/** ceil(a × ۱۰۰۰۰ ÷ c) با سقفِ ۱۰۰۰۰ — همان نیم‌کردنِ Kotlin برای عددِ بسیار بزرگ */
function bpCeil(a0, c0) {
  if (a0 >= c0) return FULL_BP;
  let a = BigInt(a0);
  let c = BigInt(c0);
  while (c > LIM) { a >>= 1n; c >>= 1n; }
  if (c <= 0n) return FULL_BP;
  const n = a * BigInt(FULL_BP);
  const v = n / c + (n % c !== 0n ? 1n : 0n);
  return Math.min(FULL_BP, Number(v));
}

/** `l` = {productId, unitMinor, qtyMilli, promoMinor}؛ `p` = قیمتِ کالا ({saleMinor, wholesaleMinor, windows, packs}) یا null */
function line(l, p, date, wholesale = true) {
  if (!p || typeof l.productId !== 'string' || !l.productId.trim() || int(l.qtyMilli) <= 0 || int(l.unitMinor) < 0) return EXEMPT;
  try {
    const sold = gross(l.unitMinor, l.qtyMilli) + Math.max(0, int(l.promoMinor));
    let list = -1;
    for (const r of refUnits(p, date, wholesale)) {
      const g = gross(r, l.qtyMilli);
      if (list < 0 || dist(sold, g) < dist(sold, list)) list = g;
    }
    if (list <= 0) return EXEMPT;
    const diff = dist(sold, list);
    if (diff <= TOL_MINOR) return { exempt: false, listMinor: list, soldMinor: sold, deviationBp: 0, reductionMinor: 0 };
    return { exempt: false, listMinor: list, soldMinor: sold, deviationBp: bpCeil(diff, list), reductionMinor: sold < list ? list - sold : 0 };
  } catch {
    return { exempt: false, listMinor: 0, soldMinor: 0, deviationBp: FULL_BP, reductionMinor: 0 };
  }
}

/** `finalTotalMinor` null ⇒ نامعلوم، بی شکاف */
function basis(discountMinor, totalMinor, finalTotalMinor, roundingMinor, reductionMinor) {
  const d = Math.max(0, int(discountMinor));
  const s = Math.max(0, int(totalMinor));
  const r = Math.min(ROUND_MAX_MINOR, Math.max(-ROUND_MAX_MINOR, int(roundingMinor)));
  const gap = finalTotalMinor === null || finalTotalMinor === undefined ? 0 : Math.max(0, s - d + r - int(finalTotalMinor));
  const red = Math.max(0, int(reductionMinor));
  return { discountMinor: d + red + gap, baseMinor: s + red, reductionMinor: red, gapMinor: gap };
}

/** «بیشترین باقی‌مانده» — همتای دقیقِ `core/promo/Loyalty.split` (افغانیِ کامل؛ برابر ⇒ ردیفِ جلوتر؛ نادرست ⇒ null) */
function split(linesAfn, valueAfn) {
  const v = BigInt(valueAfn);
  const ls = linesAfn.map((x) => BigInt(x));
  if (v < 0n || ls.some((x) => x < 0n)) return null;
  const whole = ls.reduce((a, x) => a + x, 0n);
  if (v > whole) return null;
  if (v === 0n) return ls.map(() => 0);
  const base = ls.map((a) => (v * a) / whole);
  const rest = ls.map((a) => (v * a) % whole);
  let left = v - base.reduce((a, x) => a + x, 0n);
  const order = ls.map((_, i) => i).sort((a, b) => (rest[b] > rest[a] ? 1 : rest[b] < rest[a] ? -1 : a - b));
  for (const i of order) {
    if (left <= 0n) break;
    if (base[i] < ls[i]) { base[i] += 1n; left -= 1n; }
  }
  return left === 0n ? base.map(Number) : null;
}

/** سهمِ امتیازِ هر ردیف (Minor) — همان `SaleCaps.loyaltyShares` */
function loyaltyShares(list, loyaltyMinor) {
  const zero = list.map(() => 0);
  const L = int(loyaltyMinor);
  if (L <= 0 || L % 100 !== 0) return zero;
  const afn = list.map((l) => {
    if (int(l.qtyMilli) <= 0 || int(l.unitMinor) < 0) return 0;
    try { return gross(l.unitMinor, l.qtyMilli) / 100; } catch { return 0; }
  });
  const s = split(afn, L / 100);
  return s ? s.map((x) => x * 100) : zero;
}

const refOfLine = (refs, id) => (refs.get ? refs.get(id) : Object.prototype.hasOwnProperty.call(refs, id) ? refs[id] : undefined);

/**
 * قلمِ آزاد (۱-۱۱ (۲)): ردیفِ بی کارتِ کالا — `productId` ِ خالی، یا کالایی که در `refs` نیست — با مقدارِ مثبت و قیمتِ
 * نامنفی. همان «بی‌سنجش» ِ `line` که حالا مجوزِ خودش را دارد.
 */
function isFree(l, refs) {
  if (int(l.qtyMilli) <= 0 || int(l.unitMinor) < 0) return false;
  const id = typeof l.productId === 'string' ? l.productId : '';
  return !id.trim() || !refOfLine(refs, id);
}

function lines(list, refs, date, loyaltyMinor = 0, wholesale = true) {
  let dev = 0;
  let red = 0;
  let free = -1;
  const shares = loyaltyShares(list, loyaltyMinor);
  for (const [i, l0] of list.entries()) {
    const l = shares[i] > 0 ? { ...l0, promoMinor: Math.max(0, int(l0.promoMinor)) + shares[i] } : l0;
    if (isFree(l0, refs)) free = Math.max(free, int(l0.unitMinor));
    const c = line(l, refOfLine(refs, l.productId) || null, date, wholesale);
    if (c.deviationBp > dev) dev = c.deviationBp;
    red += c.reductionMinor;
  }
  return { maxDeviationBp: dev, reductionMinor: red, freeUnitMaxMinor: free };
}

/** درصدِ تخفیف به صدمِ درصد — همان `perm-core.discountBp` (نیم افغانی بخشیده) */
function discountBp(d, s) { return require('./perm-core').discountBp(d, s); }

/**
 * نیازهای فاکتور به همان ترتیبِ Kotlin: تخفیف (با تخفیفِ پنهان اگر `enforcePrice`)، تغییرِ قیمت، قلمِ آزاد، قرض.
 * ⇒ [{perm, doc, pctBp, amount}] (`pctBp`/`amount` null وقتی نیست)
 */
function needs(sale, list, refs, { enforcePrice = false, enforceCredit = false } = {}) {
  const doc = `sales/${sale.id}`;
  const out = [];
  const sum = enforcePrice ? lines(list, refs, sale.date || '', sale.loyaltyMinor, sale.wholesale === true)
    : { maxDeviationBp: 0, reductionMinor: 0, freeUnitMaxMinor: -1 };
  const b = enforcePrice
    ? basis(sale.discountMinor, sale.totalMinor, sale.finalTotalMinor, sale.roundingMinor, sum.reductionMinor)
    : { discountMinor: int(sale.discountMinor), baseMinor: int(sale.totalMinor) };
  if (b.discountMinor > 0) out.push({ perm: DISCOUNT, doc, pctBp: discountBp(b.discountMinor, b.baseMinor), amount: b.discountMinor });
  if (enforcePrice && sum.maxDeviationBp > 0) out.push({ perm: PRICE, doc, pctBp: sum.maxDeviationBp, amount: null });
  if (enforcePrice && sum.freeUnitMaxMinor >= 0) out.push({ perm: FREE_LINE, doc, pctBp: null, amount: sum.freeUnitMaxMinor });
  if (enforceCredit) {
    const a = creditAmount(sale.debtGivenMinor, sale.priorDebtMinor);
    if (a > 0) out.push({ perm: CREDIT, doc, pctBp: null, amount: a });
  }
  return out;
}

/** درخواستِ `perm-core.evaluate` */
function req(n) {
  const out = {};
  if (n.pctBp !== null && n.pctBp !== undefined) out.pct = n.pctBp / 100;
  if (n.amount !== null && n.amount !== undefined) out.amount = n.amount;
  return out;
}

// ---------- شکلِ دادهٔ سرور ⇒ ورودیِ هسته ----------

/** دادهٔ کالا (v3: `salePriceMinor`…؛ راهِ قدیم: `salePrice` ِ افغانی) ⇒ قیمتِ کالا */
function refOf(data, { legacy = false } = {}) {
  if (!isObj(data)) return null;
  const minor = (v) => int(Number(v) * 100);
  const windows = (Array.isArray(data.priceWindows) ? data.priceWindows : [])
    .filter((w) => isObj(w) && typeof w.from === 'string' && typeof w.to === 'string')
    .map((w) => ({ from: w.from, to: w.to, priceMinor: minor(w.price) }));
  const packs = (Array.isArray(data.packs) ? data.packs : [])
    .filter(isObj).map((k) => ({ sizeMilli: int(k.sizeMilli), priceMinor: k.priceMinor === null || k.priceMinor === undefined ? 0 : int(k.priceMinor) }));
  return {
    saleMinor: legacy ? minor(data.salePrice) : int(data.salePriceMinor),
    wholesaleMinor: legacy ? minor(data.wholesalePrice) : int(data.wholesalePriceMinor),
    windows, packs,
  };
}

/** قلمِ فاکتور ⇒ ردیفِ هسته */
function lineOf(f, { legacy = false } = {}) {
  const promo = (Array.isArray(f.promo) ? f.promo : []).reduce((a, x) => a + (isObj(x) ? Math.max(0, int(x.discountMinor)) : 0), 0);
  return {
    productId: typeof f.productId === 'string' ? f.productId : '',
    unitMinor: legacy ? int(Number(f.unitPrice) * 100) : int(f.unitPriceMinor),
    qtyMilli: legacy ? int(Number(f.quantity) * 1000) : int(f.quantityMilli),
    promoMinor: promo,
  };
}

/**
 * سرِ فاکتور ⇒ فاکتورِ هسته؛ تاریخ = `date` ِ فاکتور (روزِ دستگاه، همان بازهٔ قیمتِ صندوق). `wholesale` = لیستِ فاکتور عمده
 * است (مشتریِ فاکتور روی **همین سرور** لیستِ پیش‌فرضِ عمده دارد — `priceListOf`)؛ ادعای صندوق پذیرفته نمی‌شود.
 */
function saleOf(id, f, { legacy = false, priorDebtMinor = 0, loyaltyMinor = 0, wholesale = false } = {}) {
  const m = (k) => (legacy ? int(Number(f[k]) * 100) : int(f[`${k}Minor`]));
  const fin = legacy ? f.finalTotal : f.finalTotalMinor;
  return {
    id, totalMinor: m('total'), discountMinor: m('discount'),
    finalTotalMinor: fin === undefined || fin === null || !Number.isFinite(Number(fin)) ? null : m('finalTotal'),
    roundingMinor: int(f.roundingMinor), debtGivenMinor: m('debtGiven'),
    date: typeof f.date === 'string' ? f.date : '', priorDebtMinor, loyaltyMinor, wholesale: wholesale === true,
  };
}

/** لیستِ قیمتِ پیش‌فرضِ مشتری (`Debtor.priceList`، همان `PriceList.of`): فقط «wholesale» عمده است، بقیه خرده */
function isWholesaleDebtor(data) {
  return isObj(data) && typeof data.priceList === 'string' && data.priceList.trim() === 'wholesale';
}

module.exports = {
  VERSION: 1, DISCOUNT, PRICE, FREE_LINE, CREDIT, PRICE_FLAG, CREDIT_FLAG, ROUND_MAX_MINOR, TOL_MINOR, FULL_BP,
  creditAmount, gross, refUnits, line, basis, split, loyaltyShares, isFree, lines, needs, req, refOf, lineOf, saleOf, isWholesaleDebtor,
  LOYALTY_SPEND: 'loyalty_spend',
};
