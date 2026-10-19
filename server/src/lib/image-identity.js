'use strict';
/**
 * هویتِ دقیقِ کالا و قاعدهٔ تطبیقِ عکس — v3 ۳۷.۴ (خالص، بی دیتابیس).
 *
 *   gtin(raw)            ⇒ { digits, gtin14, valid }   (رقمِ فارسی/عربی هم)
 *   size(value, unit)    ⇒ { base, dim }   g/kg ⇒ گرم، ml/l ⇒ میلی‌لیتر، عدد ⇒ دانه
 *   keysOf(product)      ⇒ کلیدهای جست‌وجو و «کلیدِ نوع» (برند/طعم/اندازه/تعداد/بسته)
 *   classify(bank, shop) ⇒ { status, reasons[], diffs[] }
 *
 * ── قانونِ تطبیق (عینِ متن) ─────────────────────────────────────────
 *  بارکدِ معتبر و مشخصاتِ همسان اولویت دارد؛ نامِ مشابه به‌تنهایی برای
 *  انتخابِ قطعی کافی نیست. وزن/طعم/اندازه/تعداد/بستهٔ متفاوت هرگز
 *  «قطعی» نمی‌شود. بی بارکد فقط پیشنهاد است و تأییدِ انسان لازم.
 *
 * ⛔ نرمال‌سازی فقط از هستهٔ مشترکِ متن (`text-core.js`، همتای
 *    `TextNormalizer.kt`) — متنِ ذخیره‌شده هرگز عوض نمی‌شود، فقط کلید.
 */
const T = require('./text-core');

/** وضعیت‌های تطبیق — همان چهارتای ۳۷.۴ */
const MATCH = ['exact', 'probable', 'ambiguous', 'unconfirmed'];

function gtin(raw) {
  const digits = T.asciiDigits(String(raw == null ? '' : raw)).replace(/[\s-]/g, '');
  if (!/^\d+$/.test(digits) || ![8, 12, 13, 14].includes(digits.length)) {
    return { digits: /^\d{1,32}$/.test(digits) ? digits : '', gtin14: '', valid: false };
  }
  const body = digits.slice(0, -1);
  let sum = 0;
  for (let i = body.length - 1, w = 3; i >= 0; i--, w = w === 3 ? 1 : 3) sum += Number(body[i]) * w;
  const check = (10 - (sum % 10)) % 10;
  const valid = check === Number(digits[digits.length - 1]);
  return { digits, gtin14: valid ? digits.padStart(14, '0') : '', valid };
}

const UNITS = {
  g: ['mass', 1], gr: ['mass', 1], gram: ['mass', 1], 'گرم': ['mass', 1], kg: ['mass', 1000], 'کیلو': ['mass', 1000], 'کیلوگرم': ['mass', 1000],
  ml: ['volume', 1], 'میلی‌لیتر': ['volume', 1], 'میلیلیتر': ['volume', 1], l: ['volume', 1000], lit: ['volume', 1000], litre: ['volume', 1000], liter: ['volume', 1000], 'لیتر': ['volume', 1000],
  pcs: ['count', 1], pc: ['count', 1], 'عدد': ['count', 1], 'دانه': ['count', 1],
};

/** اندازه به واحدِ پایه — «۱ کیلو» و «1000 g» یکی‌اند؛ ناشناخته ⇒ base=0. */
function size(value, unit) {
  const v = Number(T.asciiDigits(String(value == null ? '' : value)).replace(/,/g, '.'));
  const u = UNITS[T.compareKey(unit)] || UNITS[String(unit || '').trim().toLowerCase()];
  if (!Number.isFinite(v) || v <= 0 || !u) return { base: 0, dim: '' };
  return { base: Math.round(v * u[1] * 1000) / 1000, dim: u[0] };
}

const k = (s) => T.compareKey(s || '');

/** کلیدهای یک کالای بانک. alt: نام‌های جایگزین (دری، پشتو، انگلیسی، املای رایج). */
function keysOf(p) {
  const alt = Array.isArray(p.alt_names) ? p.alt_names : [];
  const sz = size(p.size_value, p.size_unit);
  const words = [p.name, ...alt, p.brand, p.category, p.variant, p.packaging].filter(Boolean);
  return {
    name_key: k(p.name),
    brand_key: k(p.brand),
    category_key: k(p.category),
    variant_key: k(p.variant),
    packaging_key: k(p.packaging),
    size_base: sz.base,
    size_dim: sz.dim,
    //  کلیدِ مقایسهٔ همهٔ نام‌ها با «|» میانشان — هر کلمهٔ پرس‌وجو باید در آن باشد
    search_key: words.map(k).filter(Boolean).join('|'),
  };
}

const PACK_FIELDS = [
  ['brand', (p) => k(p.brand)],
  ['variant', (p) => k(p.variant)],
  ['size', (p) => { const s = size(p.size_value, p.size_unit); return s.base ? s.dim + ':' + s.base : ''; }],
  ['pack_count', (p) => (Number(p.pack_count) > 0 ? String(Number(p.pack_count)) : '')],
  ['packaging', (p) => k(p.packaging)],
];

/**
 * تطبیقِ کالای بانک با کالای دکان (یا ردیفِ ورودی).
 *   exact        بارکدِ معتبرِ یکسان و هیچ مشخصهٔ ناهمسان
 *   probable     بی بارکد: نام و همهٔ مشخصه‌های موجود همسان ⇒ هنوز تأیید لازم
 *   ambiguous    مشخصه‌ای ناهمسان، یا بارکدِ یکسان با مشخصهٔ متناقض
 *   unconfirmed  بارکدِ متفاوت یا نامِ ناجور
 */
function classify(bank, shop) {
  const reasons = [];
  const diffs = [];
  const gb = gtin(bank.gtin), gs = gtin(shop.gtin != null ? shop.gtin : shop.barcode);
  for (const [field, fn] of PACK_FIELDS) {
    const a = fn(bank), b = fn(shop);
    if (a && b && a !== b) diffs.push(field);
  }
  if (gb.valid && gs.valid) {
    if (gb.gtin14 !== gs.gtin14) return { status: 'unconfirmed', reasons: ['barcode_different'], diffs };
    reasons.push('barcode_exact');
    return { status: diffs.length ? 'ambiguous' : 'exact', reasons, diffs };
  }
  const nameOk = k(bank.name) && k(shop.name) && (k(bank.name) === k(shop.name) ||
    (Array.isArray(bank.alt_names) && bank.alt_names.some((n) => k(n) === k(shop.name))));
  if (!nameOk) return { status: 'unconfirmed', reasons: ['name_different'], diffs };
  reasons.push('name_same');
  if (k(bank.brand) && k(bank.brand) === k(shop.brand)) reasons.push('brand_same');
  return { status: diffs.length ? 'ambiguous' : 'probable', reasons, diffs };
}

/** کلیدِ «نوع»: دو کالا با یک نام ولی این کلیدِ متفاوت، دو گزینهٔ جدا‌اند (IMG-BANK-05). */
function variantKey(p) {
  return PACK_FIELDS.map(([, fn]) => fn(p)).join('/');
}

module.exports = { MATCH, gtin, size, keysOf, classify, variantKey };
