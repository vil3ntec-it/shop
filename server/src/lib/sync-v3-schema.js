'use strict';
/**
 * Sync v3 — فهرستِ جدول‌ها و «سیاستِ ادغامِ» هر کدام (SYN-06، بخشِ ۳.۸.۲).
 * فقط `app=shop`. پمپ همان v1 است و این‌جا هیچ نامی از آن نیست.
 *
 * ── سیاست‌ها ──────────────────────────────────────────────────────
 *   master  اطلاعاتِ پایه (کالا، مشتری، تأمین‌کننده، مصارف، فهرست‌ها):
 *           «آخرین نوشتن» در سطحِ فیلد، با **HLC** نه ترتیبِ رسیدن.
 *           بازنده به صندوقِ تعارض. حذف نرم است.
 *   doc     سندِ مالی (فروش، قلمِ فروش، برگشت، پرداخت، خرید، رسیدِ انبار):
 *           فقط افزودنی. ویرایش و حذف ممنوع؛ «لغو» یک op جداست؛ فیلدهای
 *           شمارنده (`counters`) فقط دلتا می‌گیرند.
 *   log     دفترِ فقط‌افزودنیِ بی‌لغو (تاریخچهٔ قیمت، سابقهٔ عملیات).
 *   moves   حرکتِ کالا: فقط افزودنی، موجودی = جمعِ `qtyMilli`. منفی شدن
 *           یک «وظیفهٔ بررسی» می‌سازد.
 *   count   شمارشِ انبار: «مقدارِ شمرده‌شده در لحظهٔ T (HLC)»؛ سرور از
 *           رویش حرکتِ «تعدیلِ شمارش» می‌سازد.
 *
 * ── واحدها ────────────────────────────────────────────────────────
 *   مبلغ  ⇒ `<نام>Minor`  عددِ صحیح، یک‌صدمِ افغانی (بخشِ ۳.۴)
 *   مقدار ⇒ `<نام>Milli`  عددِ صحیح، هزارم
 *   op v2 همان فیلدها را با نامِ قدیم و `Double` دارد؛ مبدل
 *   (`sync-v3-convert.js`) نام و واحد را عوض می‌کند. op v3ی که نامِ قدیم
 *   یا عددِ اعشاری بفرستد رد می‌شود، تا دو واحد هرگز قاطی نشوند.
 *
 * ⛔ مانده‌ها (`derived`) هرگز همگام نمی‌شوند — از روی دفتر حساب می‌شوند.
 * ⛔ هر جدولِ Sync v1ِ دکان این‌جا هست (آزمونِ `sync-v3-convert`)؛ جدولِ
 *    تازه یعنی یک خط این‌جا و یک خط در برنامه.
 */

const POLICIES = Object.freeze(['master', 'doc', 'log', 'moves', 'count']);

const TABLES = Object.freeze({
  products: {
    policy: 'master',
    money: ['purchasePrice', 'salePrice', 'wholesalePrice'],
    qty: ['minStock'],
    derived: ['stock'],
    duplicate: 'barcodes',
  },
  debtors: { policy: 'master', derived: ['balance', 'totalDebt'], duplicate: 'phone' },
  suppliers: { policy: 'master', derived: ['balance'] },
  expenses: { policy: 'master', money: ['amount'] },
  productCategories: { policy: 'master' },
  productUnits: { policy: 'master' },
  expenseCategories: { policy: 'master' },

  sales: {
    policy: 'doc',
    money: ['total', 'discount', 'finalTotal', 'paidAmount', 'remaining', 'debtGiven', 'debtSettled'],
    counters: ['paidAmountMinor', 'remainingMinor', 'debtGivenMinor', 'debtSettledMinor'],
    cancel: 'sales.cancel',
    //  نشانِ محلیِ «فرستاده شد؟» — مالِ همان دستگاه است، نه دفتر
    localOnly: ['syncStatus'],
  },
  saleItems: {
    policy: 'doc',
    money: ['unitPrice', 'purchasePrice', 'totalPrice'],
    qty: ['quantity', 'returnedQty'],
    counters: ['returnedQtyMilli'],
  },
  returns: { policy: 'doc', money: ['amount'], qty: ['quantity'], cancel: 'data.delete.any' },
  transactions: { policy: 'doc', money: ['amount'], cancel: 'data.delete.any' },
  purchases: {
    policy: 'doc',
    money: ['purchasePrice', 'totalAmount', 'paidAmount', 'debt'],
    qty: ['quantity'],
    counters: ['paidAmountMinor', 'debtMinor'],
    cancel: 'data.delete.any',
  },
  supplierPayments: { policy: 'doc', money: ['amount'], cancel: 'data.delete.any' },
  warehouseEntries: {
    policy: 'doc', money: ['price'], qty: ['cartons', 'perCarton', 'units'], cancel: 'data.delete.any',
  },

  priceHistory: { policy: 'log', money: ['oldPrice', 'newPrice'] },
  auditLog: { policy: 'log', money: ['amount'] },
  //  گاوصندوق (PAY-18): فقط‌افزودنی؛ مبلغ از اول `amountMinor`ِ صحیح است
  safeEntries: { policy: 'log' },

  stockMovements: { policy: 'moves', qty: ['qty'] },
  //  تازهٔ v3 — در v1 نیست
  stockCounts: { policy: 'count', qty: ['countedQty'], v3only: true },
});

/** کدام کارها برای هر سیاست مجاز است (op v3ِ بومی). */
const KINDS = Object.freeze({
  master: ['insert', 'update', 'delete', 'delta'],
  doc: ['insert', 'delta', 'cancel'],
  log: ['insert'],
  moves: ['insert'],
  count: ['insert'],
});

const minorOf = (f) => `${f}Minor`;
const milliOf = (f) => `${f}Milli`;

const SPECS = new Map();
for (const [name, t] of Object.entries(TABLES)) {
  const money = t.money || [];
  const qty = t.qty || [];
  SPECS.set(name, Object.freeze({
    name,
    policy: t.policy,
    money, qty,
    counters: t.counters || [],
    derived: t.derived || [],
    localOnly: t.localOnly || [],
    cancel: t.cancel || '',
    duplicate: t.duplicate || '',
    v3only: !!t.v3only,
    kinds: KINDS[t.policy].filter(k => (k !== 'cancel' || t.cancel) && (k !== 'delta' || (t.counters || []).length)),
    //  نام‌های عددِ صحیح در v3، و نام‌های قدیمی که در v3 ممنوع‌اند
    intFields: new Set([...money.map(minorOf), ...qty.map(milliOf)]),
    legacyNames: new Set([...money, ...qty]),
  }));
}

/** @returns {object|null} */
/**
 * با `ctx` (درِ دستگاه، `sync-v3.validate`): جدولِ پشتِ پرچمِ خاموش (گاوصندوق، `safe-flag.js`) همان
 * ناشناختهٔ main است. بی `ctx` (تبدیل، پل به v1، رسید) همهٔ جدول‌ها، مثلِ پیش.
 */
function specOf(table, ctx) {
  const spec = typeof table === 'string' ? SPECS.get(table) || null : null;
  return spec && ctx !== undefined && !require('./safe-flag').allowed(spec.name, ctx) ? null : spec;
}

/** شکلِ عمومی برای `/hello` — برنامه از همین می‌خواند. با `ctx` (حتی null) بی جدولِ پشتِ پرچمِ خاموش. */
function describe(ctx) {
  const out = {};
  for (const s of SPECS.values()) {
    if (ctx !== undefined && !require('./safe-flag').allowed(s.name, ctx)) continue;
    out[s.name] = {
      policy: s.policy, kinds: s.kinds,
      money: s.money.map(minorOf), qty: s.qty.map(milliOf),
      counters: s.counters, derived: s.derived, cancel: !!s.cancel,
    };
  }
  return out;
}

module.exports = { POLICIES, TABLES, KINDS, specOf, describe, minorOf, milliOf, names: () => [...SPECS.keys()] };
