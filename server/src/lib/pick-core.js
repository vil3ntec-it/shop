'use strict';
/**
 * منطقِ خالصِ جمع‌آوری و بسته‌بندی (PCK، v3 §۶.۱۷) — بی دیتابیس، بی زمانِ سیستم.
 *
 * ⛔ دوقلوی `core/src/commonMain/.../core/pick/PickCore.kt`؛ هر دو با بردارِ مرجعِ
 * `core/test-vectors/pick-v1.json` سنجیده می‌شوند (`server/test/pick-core.test.js` و
 * `PickCoreVectorsTest`). یکی را بی دیگری و بردار عوض نکن.
 *
 *   ترتیب (PCK-01) ── قلم‌ها به ترتیبِ راهرو ⇐ قفسه ⇐ طبقه (عدد عددی، متن نویسه‌به‌نویسه)؛
 *                     بی‌جا آخر؛ سپس نام و شمارهٔ قلم — هر بار همان ترتیب.
 *   بارکد (PCK-02) ─── فقط بارکدِ همان کالا؛ رقمِ فارسی/عربی و فاصله پاک؛ GTINهای هم‌ارز
 *                     (UPC-A ۱۲ رقم ⇔ EAN-13 با صفرِ آغازین) یکی‌اند. کالای بی‌بارکد ⇒ تأییدِ دستی.
 *   وزن (PCK-04) ───── وزنِ واقعی تا ±حدِ مجاز (پیش‌فرض ۱۰٪) ⇒ قیمت با وزنِ واقعی؛ بیشتر ⇒ رد
 *                     (کمتر بردارید)؛ کمتر ⇒ «کم» با همان وزن.
 *   حساب (PCK-07) ──── پیدانشده/حذف ⇒ صفر؛ «کم» ⇒ مقدارِ برداشته. کسرِ خودکار = جمعِ پیشین − جمعِ تازه؛
 *                     تخفیفِ سفارش از جمعِ تازه بیشتر نمی‌شود؛ هزینهٔ ارسال همان. مشتری هرگز بیش از
 *                     آن‌چه برداشته شده نمی‌پردازد.
 *   جایگزین (PCK-05/06) ─ فقط هم‌دسته، هم‌حلال، هم‌نوعِ واحد؛ قیمتِ واحدِ کمتر؛ پس از مهلت ترجیحِ مشتری
 *                     (فقط «بهترین مشابه» = پذیرش). بردارِ جدا: `core/test-vectors/pick-sub-v1.json`.
 */

/** همان قاعدهٔ یگانهٔ گردکردنِ §۳.۴ (`order-states.lineTotalMinor`): گرد به یک افغانی، نیمه به سمتِ +∞. */
function lineTotalMinor(qtyMilli, unitPriceMinor) {
  const n = BigInt(qtyMilli) * BigInt(unitPriceMinor) + 50000n;
  const d = 100000n;
  let q = n / d;
  if (n % d !== 0n && n < 0n) q -= 1n;
  const out = q * 100n;
  if (out > BigInt(Number.MAX_SAFE_INTEGER) || out < -BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new RangeError('مبلغِ ردیف بیش از حد بزرگ است');
  }
  return Number(out);
}

/** رقمِ فارسی (۰–۹) و عربی (٠–٩) ⇒ لاتین */
function latin(s) {
  return String(s == null ? '' : s)
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));
}

// ════════════════════════════════════════════════════════════════════
//  PCK-01 — ترتیبِ قفسه
// ════════════════════════════════════════════════════════════════════

const MAX_LOC = 20;

/** یک تکهٔ جا (راهرو/قفسه/طبقه): رقمِ لاتین، بی فاصلهٔ دو سر، حداکثر ۲۰ نویسه */
function locPart(v) {
  return latin(v).replace(/\s+/g, ' ').trim().slice(0, MAX_LOC);
}

/** «۱۰» پس از «۹»؛ «A2» پیش از «A10»؛ متن نویسه‌به‌نویسه (همان ترتیب در Kotlin) */
function compareNatural(a, b) {
  const ta = String(a).match(/\d+|\D+/g) || [];
  const tb = String(b).match(/\d+|\D+/g) || [];
  const n = Math.min(ta.length, tb.length);
  for (let i = 0; i < n; i++) {
    const x = ta[i];
    const y = tb[i];
    const dx = /^\d/.test(x);
    const dy = /^\d/.test(y);
    if (dx && dy) {
      const sx = x.replace(/^0+(?=\d)/, '');
      const sy = y.replace(/^0+(?=\d)/, '');
      if (sx.length !== sy.length) return sx.length < sy.length ? -1 : 1;
      if (sx !== sy) return sx < sy ? -1 : 1;
    } else if (x !== y) {
      return x < y ? -1 : 1;
    }
  }
  return ta.length === tb.length ? 0 : (ta.length < tb.length ? -1 : 1);
}

const hasLoc = (l) => Boolean(l.aisle || l.shelf || l.level);

/**
 * ترتیبِ برگهٔ جمع‌آوری. هر قلم: `{lineNo, name, aisle, shelf, level}` ⇒ شمارهٔ قلم‌ها به ترتیب.
 * جای خالی پس از جای پر (در همان سطح)؛ قلمِ بی هیچ جا آخرِ برگه.
 */
function pickOrder(lines) {
  const rows = (lines || []).map((l) => ({
    lineNo: Number(l.lineNo), name: String(l.name || ''),
    aisle: locPart(l.aisle), shelf: locPart(l.shelf), level: locPart(l.level),
  }));
  const part = (x, y) => {
    if (x === y) return 0;
    if (!x) return 1;
    if (!y) return -1;
    return compareNatural(x, y);
  };
  rows.sort((a, b) => {
    const ka = hasLoc(a);
    const kb = hasLoc(b);
    if (ka !== kb) return ka ? -1 : 1;
    return part(a.aisle, b.aisle) || part(a.shelf, b.shelf) || part(a.level, b.level)
      || (a.name === b.name ? 0 : (a.name < b.name ? -1 : 1)) || (a.lineNo - b.lineNo);
  });
  return rows.map((r) => r.lineNo);
}

// ════════════════════════════════════════════════════════════════════
//  PCK-02 — بارکد
// ════════════════════════════════════════════════════════════════════

/** بارکد ⇒ شکلِ سنجش: رقمِ لاتین، بی فاصله و خطِ تیره، حروفِ بزرگ، حداکثر ۶۴ */
function normBarcode(v) {
  return latin(v).replace(/[\s-]+/g, '').toUpperCase().slice(0, 64);
}

/** GTIN ِ عددیِ ۸ تا ۱۴ رقم ⇒ بی صفرِ آغازین (UPC-A ⇔ EAN-13 ⇔ GTIN-14 یکی) */
function gtinKey(code) {
  return /^\d{8,14}$/.test(code) ? code.replace(/^0+/, '') : code;
}

/** آیا بارکدِ اسکن‌شده مالِ همین کالاست؟ کالای بی‌بارکد ⇒ هرگز (تأییدِ دستی جداست). */
function barcodeMatches(productBarcodes, scanned) {
  const s = normBarcode(scanned);
  if (!s) return false;
  const want = gtinKey(s);
  return (productBarcodes || []).some((b) => {
    const n = normBarcode(b);
    return n !== '' && (n === s || gtinKey(n) === want);
  });
}

// ════════════════════════════════════════════════════════════════════
//  PCK-04 — کالای وزنی
// ════════════════════════════════════════════════════════════════════

/** واحدهایی که «وزنی»اند — مقدار با ترازو، نه با شمارش */
const WEIGHT_UNITS = Object.freeze(['کیلو', 'کیلوگرم', 'کیلوگرام', 'کیلو گرم', 'گرم', 'kg', 'kgs', 'kilo', 'kilogram', 'g', 'gr', 'gram']);
const DEFAULT_TOLERANCE_BP = 1000;   // ۱۰٪
const MAX_TOLERANCE_BP = 5000;       // ۵۰٪

function isWeighedUnit(unit) {
  const u = String(unit || '').replace(/\s+/g, ' ').trim().toLowerCase();
  return WEIGHT_UNITS.includes(u);
}

/**
 * وزنِ واقعی در برابرِ سفارش ⇒ `ok` (در حدِ مجاز)، `short` (کمتر از حد)، `over` (بیشتر از حد؛ رد).
 * حد = سفارش × bp / ۱۰۰۰۰ به پایین (هزارم).
 */
function weighVerdict(orderedMilli, actualMilli, toleranceBp = DEFAULT_TOLERANCE_BP) {
  const bp = Math.min(Math.max(Number(toleranceBp) || 0, 0), MAX_TOLERANCE_BP);
  const slack = Math.floor((orderedMilli * bp) / 10000);
  if (actualMilli > orderedMilli + slack) return 'over';
  if (actualMilli < orderedMilli - slack) return 'short';
  return 'ok';
}

// ════════════════════════════════════════════════════════════════════
//  PCK-07 — حسابِ پس از جمع‌آوری
// ════════════════════════════════════════════════════════════════════

/** مقدارِ فروختنیِ یک قلم پس از جمع‌آوری (جایگزینِ پذیرفته‌شده = مقدارِ جایگزین، PCK-05/06) */
function finalQty(l) {
  if (l.status === 'picked' || l.status === 'short' || l.status === 'substituted') return Math.max(0, Number(l.pickedQtyMilli) || 0);
  return 0;
}

/**
 * جمعِ تازهٔ سفارش. قلم‌ها: `{lineNo, status, orderedQtyMilli, pickedQtyMilli, unitPriceMinor, originalTotalMinor}`.
 * ⇒ `{lines:[{lineNo, qtyMilli, totalMinor}], subtotalMinor, discountMinor, deliveryFeeMinor, totalMinor,
 *     deductedMinor, removedLines, changedLines, pending}`
 */
function settle({ lines, discountMinor = 0, deliveryFeeMinor = 0 }) {
  const out = [];
  let subtotal = 0;
  let original = 0;
  let pending = 0;
  const removed = [];
  const changed = [];
  for (const l of lines || []) {
    if (l.status === 'pending') pending += 1;
    const qty = finalQty(l);
    const total = qty > 0 ? lineTotalMinor(qty, l.unitPriceMinor) : 0;
    out.push({ lineNo: Number(l.lineNo), qtyMilli: qty, totalMinor: total });
    subtotal += total;
    original += Number(l.originalTotalMinor) || 0;
    if (qty === 0) removed.push(Number(l.lineNo));
    //  جایگزین همیشه «تغییر» است — کالا عوض شده، حتی اگر مقدار و مبلغ همان باشد
    else if (l.status === 'substituted' || qty !== Number(l.orderedQtyMilli) || total !== Number(l.originalTotalMinor)) changed.push(Number(l.lineNo));
  }
  const discount = Math.min(Math.max(Number(discountMinor) || 0, 0), subtotal);
  const fee = Math.max(Number(deliveryFeeMinor) || 0, 0);
  return {
    lines: out,
    subtotalMinor: subtotal,
    discountMinor: discount,
    deliveryFeeMinor: fee,
    totalMinor: subtotal - discount + fee,
    //  کسرِ خودکار: فقط کمتر شدنِ جمعِ اقلام (افزایشِ وزن در حدِ مجاز کسر نیست)
    deductedMinor: Math.max(0, original - subtotal),
    removedLines: removed,
    changedLines: changed,
    pending,
  };
}

// ════════════════════════════════════════════════════════════════════
//  PCK-05/06 — جایگزینِ امن، قیمتِ شفاف و ترجیحِ مشتری پس از مهلت
//  ⛔ دوقلوی `core/.../core/pick/PickSubstitute.kt` با بردارِ `core/test-vectors/pick-sub-v1.json`.
// ════════════════════════════════════════════════════════════════════

/** حلال بودن: `halal`، `non_halal` یا '' (نامعلوم). هر چیزِ دیگر نامعلوم است. */
function halalOf(v) {
  const s = String(v == null ? '' : v).trim().toLowerCase();
  return s === 'halal' || s === 'non_halal' ? s : '';
}

/** دسته برای سنجش: رقمِ لاتین، یک فاصله، بی فاصلهٔ دو سر، حروفِ کوچک */
function normCategory(v) {
  return latin(v).replace(/\s+/g, ' ').trim().toLowerCase();
}

/**
 * آیا `cand` جایگزینِ امنِ `orig` است؟ ⇒ null (مجاز) یا کدِ دلیل. هر دو:
 * `{productId, category, subcategory, halal, unit, priceMinor}` (`priceMinor` ِ اصل = قیمتِ واحدِ سفارش).
 *   • همان کالا نه؛ دسته باید معلوم و یکی باشد؛ اگر اصل زیردسته دارد، جایگزین همان زیردسته را؛
 *   • حلال/غیرحلال/نامعلوم باید یکی باشد (نامعلوم با حلال یکی نیست — اثبات نشده)؛
 *   • وزنی با شمارشی جایگزین نمی‌شود؛ جایگزینِ بی قیمت نه.
 */
function substituteBlock(orig, cand) {
  if (!cand || !cand.productId) return 'not_found';
  if (orig.productId && String(cand.productId) === String(orig.productId)) return 'same_product';
  const ca = normCategory(orig.category);
  const cb = normCategory(cand.category);
  if (!ca || !cb) return 'category_unknown';
  if (ca !== cb) return 'category_mismatch';
  const sa = normCategory(orig.subcategory);
  if (sa && normCategory(cand.subcategory) !== sa) return 'subcategory_mismatch';
  if (halalOf(orig.halal) !== halalOf(cand.halal)) return 'halal_mismatch';
  if (isWeighedUnit(orig.unit) !== isWeighedUnit(cand.unit)) return 'unit_mismatch';
  const p = cand.priceMinor;
  if (typeof p !== 'number' || !Number.isSafeInteger(p) || p <= 0) return 'no_price';
  return null;
}

/**
 * قاعدهٔ قیمتِ شفاف: مشتری قیمتِ واحدِ **کمتر** را می‌پردازد (اصلِ سفارش یا جایگزین)، با همان
 * گردکردنِ §۳.۴. مقدار هرگز بیش از سفارش نیست (فراخواننده می‌سنجد) ⇒ جمع هرگز بیش از قلمِ اصل.
 */
function substituteCharge(origUnitPriceMinor, candUnitPriceMinor, qtyMilli) {
  const unit = Math.min(Number(origUnitPriceMinor), Number(candUnitPriceMinor));
  return { chargedUnitPriceMinor: unit, totalMinor: lineTotalMinor(qtyMilli, unit) };
}

/**
 * پیشنهادِ جایگزین‌ها (S37: دو تا): فقط امن‌ها؛ هم‌برند پیش‌تر، سپس نزدیک‌ترین قیمت، سپس نام و شناسه.
 * ⇒ شناسه‌ها به ترتیب.
 */
function rankSubstitutes(orig, cands, limit = 2) {
  const brand = normCategory(orig.brand);
  const price = Number(orig.priceMinor) || 0;
  const ok = (cands || []).filter((c) => substituteBlock(orig, c) === null).map((c) => ({
    id: String(c.productId), name: String(c.name || ''),
    same: brand !== '' && normCategory(c.brand) === brand ? 0 : 1,
    gap: Math.abs(Number(c.priceMinor) - price),
  }));
  ok.sort((a, b) => (a.same - b.same) || (a.gap - b.gap)
    || (a.name === b.name ? 0 : (a.name < b.name ? -1 : 1)) || (a.id === b.id ? 0 : (a.id < b.id ? -1 : 1)));
  const n = Math.max(0, Math.trunc(Number(limit) || 0));
  return ok.slice(0, n).map((c) => c.id);
}

/** ترجیحِ جایگزینیِ مشتری (CHK-15): ''، best_match، specific، none، refund */
const PREFERENCES = Object.freeze(['', 'best_match', 'specific', 'none', 'refund']);

/** مشتری گفته «جایگزین نه» ⇒ پیشنهاد فرستاده نمی‌شود؛ قلم حذف و کسر می‌شود (PCK-07). */
function proposable(preference) {
  return preference !== 'none' && preference !== 'refund';
}

/**
 * مشتری تا پایانِ مهلت جواب نداد ⇒ ترجیحِ ذخیره‌شده: فقط «بهترین مشابه» یعنی پذیرش؛ «کالای مشخص»
 * (سرور نمی‌داند همین است)، بی ترجیح و بقیه ⇒ رد (حذف و کسر). هرگز بی رضایت گران‌تر نمی‌شود.
 */
function timeoutDecision(preference) {
  return preference === 'best_match' ? 'accept' : 'reject';
}

// ════════════════════════════════════════════════════════════════════
//  PCK-13 — شاخص‌ها
// ════════════════════════════════════════════════════════════════════

/**
 * برگه‌های بسته‌شده ⇒ شاخص‌ها. هر برگه: `{startedAt, packedAt, lines:[{status}]}`.
 * نرخ‌ها به ده‌هزارم (bp)؛ بی داده ⇒ null (هرگز عددِ ساختگی).
 */
function metrics(picks) {
  const done = (picks || []).filter((p) => p.packedAt != null && p.startedAt != null && p.packedAt >= p.startedAt);
  const times = done.map((p) => p.packedAt - p.startedAt).sort((a, b) => a - b);
  let lines = 0;
  let found = 0;
  let notFound = 0;
  let short = 0;
  let substituted = 0;
  for (const p of done) {
    for (const l of p.lines || []) {
      lines += 1;
      if (l.status === 'picked') found += 1;
      else if (l.status === 'short') { found += 1; short += 1; }
      else if (l.status === 'substituted') substituted += 1;
      else if (l.status === 'not_found' || l.status === 'removed') notFound += 1;
    }
  }
  const rate = (x) => (lines ? Math.round((x * 10000) / lines) : null);
  const median = times.length
    ? (times.length % 2 ? times[(times.length - 1) / 2] : Math.round((times[times.length / 2 - 1] + times[times.length / 2]) / 2))
    : null;
  return {
    orders: done.length,
    lines,
    found,
    notFound,
    short,
    substituted,
    avgPickMs: times.length ? Math.round(times.reduce((s, t) => s + t, 0) / times.length) : null,
    medianPickMs: median,
    foundRateBp: rate(found),
    substitutionRateBp: rate(substituted),
  };
}

module.exports = {
  DEFAULT_TOLERANCE_BP, MAX_TOLERANCE_BP, WEIGHT_UNITS,
  lineTotalMinor, latin, locPart, compareNatural, pickOrder, normBarcode, barcodeMatches,
  isWeighedUnit, weighVerdict, finalQty, settle, metrics,
  PREFERENCES, halalOf, normCategory, substituteBlock, substituteCharge, rankSubstitutes, proposable, timeoutDecision,
};
