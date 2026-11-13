'use strict';
/**
 * S89 + نیمهٔ سرورِ SEC-03 — فاکتورِ ثبت‌شده در سرور «سرِ جا» ویرایش و پاک نمی‌شود (§۶.۱۳، §۱۹.۱۲).
 *
 * پشتِ همان پرچمِ خاموشِ `shop.doc_chain` (SEC-02): خاموش ⇒ هیچ پرسش و هیچ تغییری؛ رفتارِ امروز.
 * روشن ⇒ هر نوشتنی که محتوای مالیِ فاکتوری را که سرور دارد عوض کند (`sales` یا قلمی از `saleItems`) سنجیده می‌شود:
 *
 *   enforce  دستگاهی که زنجیره دارد (ردیفی در `device_chain_heads` ِ همین دکان)؛ درِ مستقیمِ API (`routes/data.js`) ۱۴۰۵/۰۸/۲۲ برداشته شد
 *            ⇒ رد با `sale_finalized_edit_forbidden` (v1: همان op «rejected»؛ API: ۴۰۹ و هیچ نوشتنی) + رویداد.
 *            «دستگاهِ زنجیره‌دار» = شناسهٔ ادعاشده **یا دستگاهِ خودِ نشست** (`tokens.device_id` ⇐ `devices.device_uid`،
 *            سرور-مرجع): شناسهٔ دستگاه در بدنه از کاربر است و بی پرچمِ `sync.device_bind` فقط ثبت می‌شود ⇒ پیش‌تر
 *            نشستِ دستگاهِ زنجیره‌دار با یک `device_id` ِ تازه به `log` می‌رسید و فاکتورِ ثبت‌شده را سرِ جا عوض می‌کرد
 *            (SEC-03). راهِ قدیمِ `/api/sync` هم همین: نشست یا شناسهٔ زنجیره‌دار ⇒ `enforce` (۴۰۹، کلِ دسته).
 *   log      دستگاهِ بی زنجیره (برنامهٔ کهنه، وب) و راهِ قدیمِ `/api/sync` با نشست و شناسهٔ بی زنجیره ⇒ همان امروز
 *            می‌نشیند، فقط رویداد — «قاعدهٔ سازگاری» (F0-02: راهِ قدیم برای برنامهٔ کهنه باز می‌ماند) و «زنجیره هرگز
 *            فروش را نمی‌بندد».
 * رویداد: `security.doc_chain` با `problem = finalized_edit` (یک ردیف برای هر فاکتور در هر نوشتن).
 *
 * ── محتوای مالی (عوض نمی‌شود؛ همان مقدار دوباره = «همان») ─────────────────────────────────────
 *   فاکتور  SALE_FIXED؛ شناسهٔ سند (SALE_ONCE) فقط از «خالی» پر می‌شود؛ مشتریِ فاکتورِ نسیه (`debtorId`) جز با
 *           ادغامِ مشتری (CRM-10: ردیفِ قدیمی `mergedInto` ِ همان مقصد را دارد، در سرور یا همین دسته)؛
 *           وضعیت فقط رو به `cancelled` (لغو = سندِ معکوسِ SEC-03؛ برگشت از لغو نه)؛ حذف هرگز.
 *   قلم     ITEM_FIXED؛ حذف هرگز؛ قلمِ تازه برای فاکتورِ موجود فقط در همان ثبت (درجِ فاکتور از همین دستگاه با
 *           همان `ts` — بُرشِ دسته در `OpQueue.cut` همان `ts` را دارد).
 * ── آزاد (فهرستِ صریح، از RULES) ───────────────────────────────────────────────────────────
 *   لغو: `status → cancelled` و `cancelReason`؛ شمارنده‌های سندِ معکوس: `debtSettled` (لغو، برگشت) و
 *   `returnedQty` ِ قلم (برگشت)؛ برچسب‌ها: `seller`، `syncStatus`، نامِ قلمِ آزاد (`name`)؛ و هر فیلدِ دیگری
 *   که مالی نیست. ⛔ فیلدِ مالیِ تازهٔ فاکتور یا قلم باید به SALE_FIXED/ITEM_FIXED برسد.
 * ── تکرار و بازپخش (هرگز رد نمی‌شود) ──────────────────────────────────────────────────────
 *   همان `op_id` ⇐ «duplicate» ِ خودِ v1 (پیش از این نگهبان). درجِ دوبارهٔ همان فاکتور یا قلم با `op_id` ِ تازه
 *   (بازپخش پس از بازیابی، دستگاهِ تازه با پشتیبانِ دستگاهِ دیگر) با محتوای مالیِ یکسان ⇐ در enforce پذیرفته و
 *   **بی بازنویسی** (همان قاعدهٔ «فقط‌افزودنی» ِ Sync v3: درجِ یکسان بی اثر) — تا نسخهٔ کهنهٔ پیش از لغو، وضعیت و
 *   شمارنده‌های سندِ معکوس را برنگرداند؛ در log همان امروز. ویرایشی که همان مقدار را دوباره می‌نویسد هم «همان» است.
 *
 * ── لغو = حلقهٔ زنجیرهٔ سرور (SEC-03، همان پرچم) ────────────────────────────────────────────
 *   هر لغوی که این نگهبان آزاد می‌گذارد (`status` ِ نالغو ⇒ `cancelled`، یا درجِ فاکتورِ تازه که بی اینترنت ثبت و لغو
 *   شد) **در همان تراکنشِ نوشتن** یک حلقه در `hash_chain` ِ `void/<دکان>` می‌شود — برای هر دستگاه (زنجیره‌دار یا نه،
 *   وب هم) و هر راه (`v1`، `legacy`، `api`): دستهٔ برگشته حلقه نمی‌گذارد. یک فاکتور ⇐ یک حلقه (زیرِ قفلِ زنجیره، با
 *   نمایهٔ `sale_void_index` ِ مهاجرتِ ۳۲۶؛ تکرار، بازپخش، لغوِ دوباره و برگشت‌ولغوِ دوباره ⇒ هیچ). بدنه: شناسه، شماره و سری، مبلغ با متنِ عددِ جاوااسکریپت
 *   (همان `JsNumber` ِ کاتلین؛ زنجیره فقط عددِ صحیح می‌پذیرد)، درهمِ محتوای مالی و درهمِ دلیل — **نه متنِ دلیل**
 *   (شاید دادهٔ شخصی؛ زنجیره پاک‌شدنی نیست). ⛔ زنجیره هرگز فروش را نمی‌بندد: شکستِ حلقه با SAVEPOINT برمی‌گردد،
 *   لغو و بقیهٔ دسته می‌نشینند و رویدادِ `void_unrecorded` می‌ماند.
 *   ⚠️ درجِ دوبارهٔ فاکتورِ موجود از دستگاهِ زنجیره‌دار (`replay`) نمی‌نشیند، حتی اگر وضعیتش «لغو» باشد: لغو با ویرایشِ
 *   خودِ دستگاه می‌رسد (قاعدهٔ S89 دست نخورد).
 *
 * ── سندهای مالیِ دیگر (`DOC_FIXED`؛ همان `doc`/`moves` ِ `sync-v3-schema`) ───────────────────────
 *   برگشت، قرض/رسید (`transactions`)، خرید و حرکتِ انبار: همان دو حالت و همان پرچم، با `doc_posted_edit_forbidden`.
 *   فیلدِ مالی عوض نمی‌شود، حذف هرگز؛ آزاد: هر فیلدِ بیرونِ فهرست (یادداشت، سررسید، شیفت، برچسب‌ها)، دلتای
 *   شمارندهٔ خرید (`paidAmount`/`debt`، همان `counters` ِ v3) و مشتریِ قرض فقط با ادغام (CRM-10). درجِ دوبارهٔ همان
 *   سند مثلِ فاکتور «replay». اصلاح فقط با سندِ معکوس (رسیدِ قرینه، برگشت به تأمین‌کننده، حرکتِ تعدیل).
 *   RPT-04: ردیف‌های صندوق در سابقه (`auditLog` با نوعِ **ذخیره‌شدهٔ** `SHIFT_DOCS`: اولِ صندوق، Z ِ بسته در `z`،
 *   ورود/خروجِ نقد، کشو بی فروش، بررسی و ادامهٔ شیفت) هم همین‌اند؛ ردیف‌های دیگرِ سابقه آزاد. اصلاحِ Z فقط سندِ تازه
 *   (`shift_review`)؛ Sync v3 خودش `auditLog` را فقط‌افزودنی می‌داند (`log`).
 * ── زنده کردن ─────────────────────────────────────────────────────────────────────────────
 *   ردیفِ حذف‌شده با محتوا (`deadOf`) «ثبتِ تازه» نیست: هر نوشتنی جز حذف روی آن ویرایشِ سندِ ثبت‌شده است و
 *   `*revive*` می‌گیرد (enforce ⇒ رد و ردیف حذف‌شده می‌ماند؛ درجِ دوبارهٔ همان محتوا «replay»). پیش‌تر v1 و راهِ قدیم
 *   آن را با محتوای تازه زنده می‌کردند و این نگهبان ردیف را نمی‌دید. سنگِ قبرِ بی محتوا (حذفِ ردیفِ نادیده) همان «نبود».
 *
 * ── دیدنِ دست‌کاری بی رد (۲-۲۳ ِ صاحب‌کار: R7 ب، R8 ب؛ همان پرچم) ─────────────────────────────────
 *   آزاد می‌مانند ولی هر بار یک ردیف در «سابقهٔ عملیاتِ» دکان (`audit_logs` ⇐ `GET /api/shop/audit`، همان `security.doc_chain`):
 *   R7 `merge_undone` — `mergedInto` ِ پُرِ مشتری خالی یا به مقصدِ دیگر می‌رود (ادغام + جابه‌جاییِ قرض در یک دسته و
 *      برداشتنِ ادغام در دستهٔ بعد = همان جابه‌جاییِ قرض بی یکی شدنِ دو مشتری). رد نه: ترمیمِ `CustomerMerge.sweep` ِ
 *      برنامه خودش گاهی ادغام را برمی‌دارد. دسته‌ای که فقط مشتری دارد هم نگهبان می‌گیرد ([watches]).
 *   R8 `shift_moved` — `shiftId` ِ قرض/رسیدِ ثبت‌شده عوض می‌شود (کشوی اصلی = خالی). رد نه: اصلاحِ درستِ رسیدی که در
 *      شیفتِ اشتباه نشسته بسته نمی‌شود.
 *   ردیف فقط برای نوشتنی که واقعاً نشست: پس از تراکنش ردیفِ امروز دوباره خوانده می‌شود ([Guard.finish]) ⇒ دستهٔ برگشتهٔ
 *   راهِ قدیم و op ِ ردشده هیچ. بدنه فقط شناسه‌ها (`from`، `to`)، نه نام یا شماره.
 *
 * ⛔ بازگرداندنِ تعارض (`restoreConflict` ِ مدیرِ ارشد، `routes/admin-sync.js`) از همین نگهبان با `enforce` و راهِ `admin`
 *    می‌گذرد: مقدارِ بازندهٔ فیلدِ مالیِ سندِ ثبت‌شده و هر نوشتن روی سندِ حذف‌شده ۴۰۹. `restoreDeleted` ِ `lib/sync-v1.js`
 *    (مشترک با پمپ) هیچ درِ HTTP ندارد و نگهبان نمی‌گیرد؛ درِ تازه برایش باید پیش از آن همین `adminRestoreCheck` را
 *    بپرسد. Sync v3 خودش سند را فقط‌افزودنی می‌داند (`append_only_edit`).
 */
const db = require('../db');
const hc = require('./hash-chain');
const { conflict } = require('../middleware/errors');

const REASON = 'sale_finalized_edit_forbidden';
const PROBLEM = 'finalized_edit';
const ACTION = 'security.doc_chain';
const FLAG = 'shop.doc_chain';
const TEXT = 'ویرایش یا حذفِ سرِ جای فاکتورِ ثبت‌شده؛ اصلاح فقط با سندِ معکوس';

/** محتوای مالیِ فاکتور — همان بخش‌های سندِ زنجیره (`DocChain.saleDoc`) و پرداخت‌ها */
const SALE_FIXED = Object.freeze([
  'total', 'discountType', 'discountValue', 'discount', 'finalTotal',
  'paymentMethod', 'paidAmount', 'remaining', 'debtGiven', 'payments', 'tenderedMinor', 'changeMinor',
  'promoDiscountMinor', 'coupons', 'approval', 'createdAt', 'date', 'roundingMinor', 'taxMinor', // POS-37
]);
/** شناسهٔ سند: فقط یک بار از «خالی» پر می‌شود (فاکتورِ کهنهٔ بی شماره، F0-03/DATA-06) */
const SALE_ONCE = Object.freeze(['invoiceNumber', 'series', 'receiptToken', 'channel', 'orderId', 'orderNo']);
/** محتوای مالیِ قلم */
const ITEM_FIXED = Object.freeze([
  'saleId', 'productId', 'quantity', 'unitPrice', 'totalPrice', 'purchasePrice', 'listPriceMinor', 'promo',
]);
const SALE_TABLES = new Set(['sales', 'saleItems']);
/** S89 — محتوای مالیِ سندهای دیگر (مشتریِ قرض جدا، با ادغام) */
const DOC_FIXED = Object.freeze({
  returns: Object.freeze(['saleId', 'saleItemId', 'productId', 'quantity', 'amount', 'unitPrice', 'refund', 'toAccount',
    'toCredit', 'creditTo', 'toMethods', 'disposition', 'docId', 'exchangeSaleId', 'channel', 'approval', 'date', 'createdAt']),
  transactions: Object.freeze(['type', 'amount', 'refId', 'kind', 'date', 'createdAt']),
  purchases: Object.freeze(['productId', 'supplierId', 'quantity', 'purchasePrice', 'totalAmount', 'paidAmount', 'debt',
    'unitQtyMilli', 'unitFactorMilli', 'date', 'createdAt']),
  stockMovements: Object.freeze(['productId', 'type', 'qty', 'refId', 'locationId', 'toLocationId', 'lotId', 'date', 'createdAt']),
  //  RPT-04: فقط ردیف‌های صندوق ([SHIFT_DOCS]، با نوعِ ردیفِ ذخیره‌شده)؛ مُهر و حلقهٔ زنجیرهٔ سابقه (STF-09) بیرونِ فهرست
  auditLog: Object.freeze(['type', 'refId', 'amount', 'amountMinor', 'z', 'notes', 'note', 'reason', 'by', 'approvedBy',
    'drawer', 'date', 'createdAt']),
});
/** RPT-04 — نوع‌های سابقه که سندِ صندوق‌اند: X/Z از آن‌ها ساخته می‌شود و Z ِ بسته در `shift_close.z` است */
const SHIFT_DOCS = new Set(['shift_open', 'shift_close', 'shift_review', 'shift_resume', 'cash_in', 'cash_out', 'no_sale']);
/** شمارنده‌های سندِ معکوس: فقط دلتا (`$inc`) آزاد است */
const DOC_COUNTERS = Object.freeze({ purchases: Object.freeze(['paidAmount', 'debt']) });
const DOC_REASON = 'doc_posted_edit_forbidden';
const DOC_TEXT = 'ویرایش، حذف یا زنده کردنِ سرِ جای سندِ مالیِ ثبت‌شده؛ اصلاح فقط با سندِ معکوس';
const isDoc = (t) => typeof t === 'string' && hasOwn(DOC_FIXED, t);
const GUARDED = (t) => SALE_TABLES.has(t) || isDoc(t);
const MERGE_HOPS = 8;
const MAX_EVENTS = 20;
const VOID_PROBLEM = 'void_unrecorded';
const VOID_TEXT = 'لغوِ فاکتور نشست ولی حلقهٔ زنجیرهٔ لغو نوشته نشد';
const SHOP_PART = /^[A-Za-z0-9_.:-]{1,180}$/;
/** ۲-۲۳ (R7 ب، R8 ب) — آزاد، فقط ردیفِ سابقه: نوع ⇐ [جدول، فیلد، متن] */
const WATCH = Object.freeze({
  merge_undone: Object.freeze(['debtors', 'mergedInto',
    'ادغامِ مشتری برداشته شد؛ قرض و رسیدی که با ادغام جابه‌جا شده بود نزدِ مشتریِ دیگر می‌ماند']),
  shift_moved: Object.freeze(['transactions', 'shiftId',
    'قرض یا رسیدِ ثبت‌شده به شیفتِ دیگری رفت؛ شمارِ نقدِ هر دو شیفت عوض می‌شود']),
});
const TEXT_MAX = 80;

const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const hasOwn = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
const UNSAFE_KEY = new Set(['__proto__', 'constructor', 'prototype']);
const isInc = (v) => isPlainObject(v) && Object.keys(v).length === 1 && typeof v.$inc === 'number';
const isEmpty = (v) => v === undefined || v === null || v === '' || v === 0
  || (Array.isArray(v) && !v.length) || (isPlainObject(v) && !Object.keys(v).length);

function canonical(v) {
  if (Array.isArray(v)) return `[${v.map(canonical).join(',')}]`;
  if (isPlainObject(v)) return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${canonical(v[k])}`).join(',')}}`;
  return JSON.stringify(v === undefined ? null : v);
}

/** «همان مقدار»؟ نبودن، null، صفر و رشته/فهرستِ خالی یکی‌اند (پیش‌فرضِ مدل)؛ عدد با مقدار، نه شکلِ نوشتن */
function same(a, b) {
  if (isEmpty(a) && isEmpty(b)) return true;
  if (typeof a === 'number' && typeof b === 'number') return a === b;
  return canonical(a) === canonical(b);
}

/**
 * مقدارِ پس از op: دلتا روی مقدارِ فعلی، وگرنه خودِ مقدار. `full` (کلِ ردیف، راهِ قدیم و API): `$inc` دلتا نیست و همان
 * شیء در جدول می‌نشیند ⇒ خودِ مقدار سنجیده می‌شود (SR43-2: `{$inc:0}` «همان» و شمارندهٔ خرید «آزاد» دیده می‌شد).
 */
const after = (cur, v, full = false) => (!full && isInc(v) ? (Number(cur) || 0) + v.$inc : v);
const statusOf = (s) => (isEmpty(s) ? 'completed' : String(s));
const isCredit = (s) => Number(s.debtGiven) > 0 || Number(s.remaining) > 0;

/* ------------------------------------------------------------- زنجیرهٔ لغو */

/** نامِ زنجیرهٔ لغوِ دکان ⇐ رشته یا '' (شناسهٔ ناجور ⇒ بی زنجیره) */
const voidChainOf = (shopId) => (SHOP_PART.test(String(shopId || '')) ? `void/${shopId}` : '');

/** متنِ کوتاه و امن برای بدنهٔ زنجیره: نیم‌جفتِ تنها (که canonicalJson نمی‌پذیرد) ⇐ U+FFFD */
function text(v) {
  if (isEmpty(v) && v !== 0) return '';
  return String(v).slice(0, TEXT_MAX).replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/g, '\uFFFD');
}

/** مبلغ ⇐ متنِ `Number#toString` ِ جاوااسکریپت (همان `JsNumber.of` ِ کاتلین)؛ نبود ⇐ '' */
const numText = (v) => (typeof v === 'number' && Number.isFinite(v) ? String(v) : text(v));

/** فاکتور پس از op: کلِ ردیف (راهِ قدیم، درجِ تازه) یا ویرایش روی ردیفِ فعلی (دلتا جمع) */
function nextSale(cur, fields, full) {
  if (full || !cur) return { ...fields };
  //  کلیدِ «__proto__» ِ JSON ِ دستگاه نمونهٔ out را عوض می‌کرد و فیلدِ ساختگی ارث می‌رسید (CodeQL ۲۹۰)؛ نوشتن در
  //  Map و ساختنِ شیء با `Object.fromEntries` (ویژگیِ خودی، نه setter) ⇐ هیچ نوشتنِ پویا روی شیءِ ساده با کلیدِ دستگاه
  const out = new Map(Object.entries(cur));
  for (const [k, v] of Object.entries(fields)) {
    if (UNSAFE_KEY.has(k)) continue;
    out.set(k, after(hasOwn(cur, k) ? cur[k] : undefined, v));
  }
  return Object.fromEntries(out);
}

/** درهمِ محتوای مالیِ فاکتور (همان فیلدهایی که عوض نمی‌شوند) — خالی‌ها یکی، عدد با متنِ جاوااسکریپت */
function saleHash(sale) {
  const pick = {};
  for (const k of [...SALE_FIXED, ...SALE_ONCE, 'debtorId']) pick[k] = isEmpty(sale[k]) ? null : sale[k];
  return hc.sha256Hex(canonical(pick));
}

/** این نوشتن فاکتور را لغو می‌کند؟ (نالغو ⇒ لغو، یا ثبتِ تازهٔ لغوشده) */
function isVoid(cur, fields, { type, full }) {
  if (type === 'delete' || !(full || hasOwn(fields, 'status')) || statusOf(fields.status) !== 'cancelled') return false;
  return cur ? statusOf(cur.status) !== 'cancelled' : (full || type === 'insert');
}

/**
 * فیلدهای مالیِ فاکتورِ [cur] که [fields] عوض می‌کند (بی سنجشِ مشتری، که ادغام می‌خواهد) ⇐ نام‌ها.
 * `full`: نوشتنِ کلِ ردیف (راهِ قدیم) — کلیدِ نبوده یعنی «خالی».
 * `insert`: درجِ دوبارهٔ ردیفِ موجود — وضعیت و شمارنده‌های سندِ معکوس سنجیده نمی‌شوند (نسخهٔ کهنهٔ همان سند).
 */
function saleChanges(cur, fields, { full = false, insert = false } = {}) {
  const has = (k) => full || hasOwn(fields, k);
  const out = [];
  for (const k of SALE_FIXED) if (has(k) && !same(cur[k], after(cur[k], fields[k], full))) out.push(k);
  for (const k of SALE_ONCE) if (has(k) && !isEmpty(cur[k]) && !same(cur[k], fields[k])) out.push(k);
  if (!insert && has('status') && statusOf(fields.status) !== statusOf(cur.status) && statusOf(fields.status) !== 'cancelled') {
    out.push('status');
  }
  return out;
}

/** مشتریِ ردیف عوض می‌شود؟ ⇐ [از، به] یا null */
function debtorMove(cur, fields, { full = false } = {}) {
  if (!(full || hasOwn(fields, 'debtorId')) || same(cur.debtorId, fields.debtorId)) return null;
  return [isEmpty(cur.debtorId) ? '' : String(cur.debtorId), isEmpty(fields.debtorId) ? '' : String(fields.debtorId)];
}
/** مشتریِ فاکتورِ نسیه عوض می‌شود؟ (فاکتورِ نقد آزاد) */
const customerMove = (cur, fields, opts) => (isCredit(cur) ? debtorMove(cur, fields, opts) : null);

function itemChanges(cur, fields, { full = false } = {}) {
  const out = [];
  for (const k of ITEM_FIXED) if ((full || hasOwn(fields, k)) && !same(cur[k], after(cur[k], fields[k], full))) out.push(k);
  return out;
}

/** فیلدهای مالیِ سندِ [table] که [fields] عوض می‌کند؛ دلتای شمارنده آزاد (فقط v1؛ کلِ ردیف دلتا ندارد، SR43-2) */
function docChanges(table, cur, fields, { full = false } = {}) {
  const counters = DOC_COUNTERS[table] || [];
  const out = [];
  for (const k of DOC_FIXED[table]) {
    if (!(full || hasOwn(fields, k)) || (!full && counters.includes(k) && isInc(fields[k]))) continue;
    if (!same(cur[k], after(cur[k], fields[k], full))) out.push(k);
  }
  return out;
}

/** پرچمِ دکان — همان `orders.flagOf` (دکان بر `all` جلو؛ فقط `true`) */
const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG);

/** یکی از این شناسه‌ها (ادعاشده، دستگاهِ نشست) در همین دکان زنجیره دارد؟ خالی‌ها کنار. */
async function chainDevice(shopId, ...deviceIds) {
  const ids = [...new Set(deviceIds.filter((d) => typeof d === 'string' && d))];
  if (!ids.length) return false;
  return !!(await db.one('SELECT 1 AS ok FROM device_chain_heads WHERE shop_id=$1 AND device_id = ANY($2::text[]) LIMIT 1', [shopId, ids]));
}

/** دستگاهِ خودِ نشست (`req.sync.deviceUid`؛ سرور-مرجع، نه بدنه) ⇐ شناسه یا '' */
const boundOf = (ctx) => (ctx && typeof ctx.deviceUid === 'string' ? ctx.deviceUid : '');

/**
 * نگهبانِ یک نوشتن. `rowOf(c, table, id)` ⇐ دادهٔ ردیفِ زنده یا null، `deadOf(c, table, id)` ⇐ دادهٔ ردیفِ حذف‌شده
 * (با محتوا) یا null، `firstInsert(c, saleId)` ⇐ {device, ts} یا null.
 * `merges`: ادغام‌های همین دسته (شناسهٔ مشتری ⇐ `{to, opId?}`؛ v1 با op_id، راهِ قدیم بی آن — [claimOf]).
 */
class Guard {
  constructor({ ctx, deviceId, mode, path, merges, rowOf, deadOf, firstInsert, ip = '', voids = true }) {
    Object.assign(this, { ctx, deviceId, mode, path, merges, rowOf, deadOf, firstInsert, ip, voids });
    this.notes = new Map();
    this.newSales = new Set();
    this.voidFails = new Map();
    this.relied = [];            //  راهِ قدیم: جابه‌جایی‌هایی که به ادغامِ همین دسته تکیه کردند ([settle])
    this.confirmed = new Map();  //  راهِ قدیم: مشتری ⇐ `mergedInto` ِ نوشتنی که واقعاً به نوشتن رسید
    this.watched = [];           //  ۲-۲۳: R7/R8 ِ آزاد، تا [finish] بسنجد که نشست
  }

  get enforce() { return this.mode === 'enforce'; }

  /**
   * آیا مشتریِ [from] (با زنجیرهٔ `mergedInto`) در [to] ادغام شده (یا با همین دسته می‌شود)؟ [ref]: سندِ جابه‌جاشده،
   * برای [settle] ِ راهِ قدیم.
   */
  async merged(c, from, to, ref = null) {
    let id = from;
    const used = [];
    for (let i = 0; i < MERGE_HOPS && id; i++) {
      let next;
      const claim = await this.claimOf(c, id);
      if (claim) { next = claim.to; if (!claim.opId) used.push({ id, to: claim.to }); }
      else { const d = await this.rowOf(c, 'debtors', id); next = d ? d.mergedInto : null; }
      if (isEmpty(next)) return false;
      if (String(next) === to) {
        if (ref) for (const u of used) this.relied.push({ ...u, ...ref });
        return true;
      }
      id = String(next);
    }
    return false;
  }

  /**
   * ادغامِ همین دسته برای مشتریِ [id] که هنوز به ردیف نرسیده ⇐ `{to, opId?}` یا null (⇐ خودِ ردیف حقیقت است).
   * ⛔ SR43-1: ادغامِ دسته فقط «پیش‌بینی» است. v1: op ِ معتبر با op_id ِ یکتا ([v1Merges])، و op_id ای که همین حالا در
   * `oplog` است یا نشسته (پیش‌تر در همین دسته ⇒ ردیف همان را دارد) یا تکراری است (هرگز نمی‌نشیند) ⇒ null. راهِ قدیم:
   * تکیه ثبت می‌شود و [settle] پس از دسته می‌سنجد که همان ادغام واقعاً به نوشتن رسید.
   */
  async claimOf(c, id) {
    const cl = this.merges.get(id);
    if (!cl) return null;
    if (cl.opId && (await c.query('SELECT 1 FROM oplog WHERE op_id=$1', [cl.opId])).rows.length) return null;
    return cl;
  }

  /** نوشتنِ مشتری که به نوشتن رسید (راهِ قدیم: کلِ ردیف ⇒ بی `mergedInto` یعنی «ادغام نیست»؛ حذف ⇒ نیست) */
  confirm(id, type, fields, full) {
    if (type === 'delete') this.confirmed.set(id, null);
    else if (full || hasOwn(fields, 'mergedInto')) this.confirmed.set(id, isEmpty(fields.mergedInto) ? null : String(fields.mergedInto));
  }

  /**
   * راهِ قدیم، پس از آخرین نوشتنِ دسته و درونِ همان تراکنش ⇐ null یا {reject}: جابه‌جاییِ مشتریِ سندی که به ادغامِ همین
   * دسته تکیه کرد ولی آن ادغام همان‌طور ننشست (کهنه، کنارگذاشته، یا برداشته با ردیفِ کاملِ بعدی) ⇒ ویرایشِ سند (SR43-1).
   */
  settle() {
    let res = null;
    for (const r of this.relied) {
      const want = isEmpty(r.to) ? null : String(r.to);
      if (this.confirmed.has(r.id) && this.confirmed.get(r.id) === want) continue;
      this.note(r.row, { table: r.table, row: r.row, type: 'update', fields: ['debtorId'] }, r.kind);
      if (this.enforce && !res) res = { reject: r.reason };
    }
    this.relied = [];
    return res;
  }

  /** فاکتورِ زنده با شناسهٔ [id] ⇐ داده یا null */
  sale(c, id) { return isEmpty(id) ? null : this.rowOf(c, 'sales', String(id)); }

  /**
   * یک نوشتن روی ردیفِ [cur] (دادهٔ زنده یا null) ⇐ null (آزاد) | {replay:true} | {reject: REASON}.
   * `type`: insert | update | delete؛ `full`: کلِ ردیف (راهِ قدیم).
   */
  async check(c, op, live) {
    const { table, rowId, type, fields = {}, ts = 0, full = false } = op;
    if (table === 'debtors') {
      this.confirm(rowId, type, fields, full);
      if (live && type !== 'delete') this.watch('merge_undone', rowId, live, fields, full);
      return null;
    }
    if (!GUARDED(table)) return null;
    //  ردیفِ حذف‌شدهٔ با محتوا سندِ ثبت‌شده است، نه «ثبتِ تازه» (پیش‌تر نوشتن آن را با محتوای تازه زنده می‌کرد)
    const dead = !live && type !== 'delete' ? await this.deadOf(c, table, rowId) : null;
    const cur = live || dead;
    if (isDoc(table)) return this.checkDoc(c, op, cur, !!dead);
    let changed = [];
    let sale = '';
    if (table === 'sales') {
      if (!cur) {                                             //  ثبت، نه ویرایش
        this.newSales.add(rowId);
        if (this.voids && isVoid(null, fields, { type, full })) await this.recordVoid(c, rowId, null, fields, { full, atInsert: true });
        return null;
      }
      sale = rowId;
      if (type === 'delete') changed = ['*delete*'];
      else {
        const insert = type === 'insert';
        changed = saleChanges(cur, fields, { full, insert });
        const move = customerMove(cur, fields, { full });
        const ref = { kind: 'sale', table, row: rowId, reason: REASON };
        if (move && !(await this.merged(c, move[0], move[1], ref)) && !(insert && (await this.merged(c, move[1], move[0], ref)))) {
          changed.push('debtorId');
        }
        if (insert && !changed.length && this.enforce) return { replay: true };
        if (dead) changed.unshift('*revive*');
      }
      //  نوشتن می‌نشیند (آزاد، یا `log`) و فاکتور را لغو می‌کند ⇒ حلقهٔ لغو در همین تراکنش
      if (this.voids && (!changed.length || !this.enforce) && isVoid(cur, fields, { type, full })) {
        await this.recordVoid(c, rowId, cur, fields, { full, atInsert: false });
      }
    } else if (cur) {
      sale = String(cur.saleId || '');
      const parent = await this.sale(c, cur.saleId);
      if (parent) {
        if (type === 'delete') changed = ['*delete*'];
        else {
          changed = itemChanges(cur, fields, { full });
          if (type === 'insert' && !changed.length && this.enforce) return { replay: true };
          if (dead) changed.unshift('*revive*');
        }
      } else if (type !== 'delete' && hasOwn(fields, 'saleId') && !same(cur.saleId, fields.saleId)
                 && (await this.sale(c, fields.saleId))) {
        sale = String(fields.saleId);
        changed = ['saleId'];                                 //  قلمِ بی‌فاکتور به فاکتورِ ثبت‌شده چسبانده می‌شود
      }
    } else if (type !== 'delete' && !isEmpty(fields.saleId) && (await this.sale(c, fields.saleId))) {
      sale = String(fields.saleId);
      if (!(await this.sameSave(c, sale, ts))) changed = ['+line'];
    }
    if (!changed.length) return null;
    this.note(sale, { table, row: rowId, type, fields: changed });
    return this.enforce ? { reject: REASON } : null;
  }

  /** سندِ مالیِ دیگر (`DOC_FIXED`) روی [cur] (زنده یا حذف‌شده، [dead]) ⇐ همان سه پاسخِ `check` */
  async checkDoc(c, { table, rowId, type, fields = {}, full = false }, cur, dead) {
    if (!cur) return null;                                    //  سندِ تازه: ثبت
    if (table === 'auditLog' && !SHIFT_DOCS.has(cur.type)) return null;   //  ردیفِ دیگرِ سابقه: همان امروز
    let changed;
    if (type === 'delete') changed = ['*delete*'];
    else {
      const insert = type === 'insert';
      changed = docChanges(table, cur, fields, { full });
      const move = table === 'transactions' ? debtorMove(cur, fields, { full }) : null;
      const ref = { kind: table, table, row: rowId, reason: DOC_REASON };
      if (move && !(await this.merged(c, move[0], move[1], ref)) && !(insert && (await this.merged(c, move[1], move[0], ref)))) {
        changed.push('debtorId');
      }
      if (insert && !changed.length && this.enforce) return { replay: true };
      if (dead) changed.unshift('*revive*');
      //  R8: شیفتِ قرض/رسید آزاد است، ولی صاحب می‌بیند (فقط اگر همین نوشتن می‌نشیند)
      if (table === 'transactions' && (!changed.length || !this.enforce)) this.watch('shift_moved', rowId, cur, fields, full);
    }
    if (!changed.length) return null;
    this.note(rowId, { table, row: rowId, type, fields: changed }, table);
    return this.enforce ? { reject: DOC_REASON } : null;
  }

  /**
   * حلقهٔ لغوِ فاکتورِ [saleId] در `void/<دکان>` — درونِ تراکنشِ نوشتن ([c])، زیرِ قفلِ همان زنجیره، یک بار برای هر
   * فاکتور. ⛔ هرگز خطا نمی‌دهد: شکست با SAVEPOINT برمی‌گردد و رویدادِ `void_unrecorded` پس از تراکنش ثبت می‌شود.
   */
  async recordVoid(c, saleId, cur, fields, { full, atInsert }) {
    const shopId = this.ctx.accountId;
    const chain = voidChainOf(shopId);
    if (!chain || isEmpty(saleId)) return;
    const next = nextSale(cur, fields, full);
    const reason = isEmpty(next.cancelReason) ? '' : String(next.cancelReason);
    const doc = {
      kind: 'doc', type: 'void', v: 1,
      saleId: text(saleId), invoiceNumber: text(next.invoiceNumber), series: text(next.series),
      total: numText(isEmpty(next.finalTotal) ? next.total : next.finalTotal),
      saleHash: saleHash(next), reasonHash: reason ? hc.sha256Hex(reason) : '',
      deviceId: text(this.deviceId), by: text(this.ctx.userId), path: this.path, enforced: this.enforce,
      //  انبارِ ردیف: v1 (`sync_rows`) یا راهِ قدیم/API (`sales`) — همان شناسه در انبارِ دیگر فاکتورِ دیگری است
      store: this.path === 'v1' ? 'v1' : 'rest',
      atInsert: !!atInsert, at: db.now(),
    };
    await c.query('SAVEPOINT sale_void_chain');
    try {
      await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sale-void:${shopId}`]);
      //  یک فاکتور ⇐ یک حلقه: نمایهٔ ۳۲۶ (کلیدِ اصلی)، نه گشتنِ متنِ حلقه‌ها
      const seen = await c.query(
        'SELECT seq FROM sale_void_index WHERE shop_id=$1 AND store=$2 AND sale_id=$3', [shopId, doc.store, String(saleId)]
      );
      if (!seen.rows.length) {
        const head = (await c.query('SELECT seq, hash FROM hash_chain WHERE chain=$1 ORDER BY seq DESC LIMIT 1', [chain])).rows[0];
        const seq = head ? Number(head.seq) + 1 : 1;
        const prev = head ? head.hash : hc.GENESIS;
        const body = hc.canonicalJson({ ...doc, chain, seq });
        const r = await c.query(
          `INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at)
           VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (chain, seq) DO NOTHING RETURNING seq`,
          [chain, seq, prev, hc.sha256Hex(prev + body), body, doc.at]
        );
        if (!r.rows.length) throw new Error('void chain head moved');
        await c.query(
          'INSERT INTO sale_void_index (shop_id, store, sale_id, seq, created_at) VALUES ($1,$2,$3,$4,$5)',
          [shopId, doc.store, String(saleId), seq, doc.at]
        );
      }
      await c.query('RELEASE SAVEPOINT sale_void_chain');
    } catch (err) {
      await c.query('ROLLBACK TO SAVEPOINT sale_void_chain');
      await c.query('RELEASE SAVEPOINT sale_void_chain');
      if (this.voidFails.size < MAX_EVENTS) this.voidFails.set(String(saleId), String(err && err.message || err).slice(0, 200));
    }
  }

  /**
   * ۲-۲۳ — نوشتنِ [fields] روی ردیفِ [cur] فیلدِ [kind] را عوض می‌کند؟ ⇐ یک ردیفِ «در انتظار» (R7: فقط ادغامِ پُر).
   * فیلدِ نیامده (نه کلِ ردیف) یعنی «همان»؛ خالی‌ها یکی.
   */
  watch(kind, id, cur, fields, full) {
    const field = WATCH[kind][1];
    if (!(full || hasOwn(fields, field)) || (kind === 'merge_undone' && isEmpty(cur[field]))) return;
    const to = after(cur[field], fields[field], full);
    if (same(cur[field], to) || this.watched.length >= MAX_EVENTS) return;
    this.watched.push({ kind, id: String(id), from: text(cur[field]), to: isPlainObject(to) ? '*' : text(to) });
  }

  /** ردیف‌های ۲-۲۳ که نوشتنشان واقعاً نشست (ردیفِ امروز همان مقدارِ تازه را دارد، نه مقدارِ پیشین) */
  async landed() {
    const out = [];
    for (const w of this.watched) {
      const [table, field] = WATCH[w.kind];
      let now;
      try { now = await this.rowOf(db, table, w.id); } catch { now = null; }
      if (!now) continue;
      const v = isPlainObject(now[field]) ? '*' : text(now[field]);
      if (v === w.to && v !== w.from) out.push(w);
    }
    this.watched = [];
    return out;
  }

  /** قلمِ تازه از همان ثبتِ فاکتور است؟ */
  async sameSave(c, saleId, ts) {
    if (this.newSales.has(saleId)) return true;
    const first = await this.firstInsert(c, saleId);
    return !!first && first.device === this.deviceId && !!ts && Number(first.ts) === Number(ts);
  }

  /** یک تلاش روی سندِ [id] از نوعِ [kind] (`sale` یا نامِ جدولِ سند) — یک رویداد برای هر سند */
  note(id, entry, kind = 'sale') {
    const key = `${kind}\u0000${id}`;
    if (!this.notes.has(key)) this.notes.set(key, { kind, id, ops: [] });
    const list = this.notes.get(key).ops;
    if (list.length < 50) list.push(entry);
  }

  /** رویدادها — پس از تراکنش؛ شکستِ ثبت هیچ چیزی را نمی‌خواباند */
  async finish() {
    let n = 0;
    for (const { kind, id, ops } of this.notes.values()) {
      if (++n > MAX_EVENTS) break;
      try {
        await require('./audit').log({
          shopId: this.ctx.accountId, actorType: 'user', userId: this.ctx.userId || '', ip: this.ip,
          action: ACTION, targetType: kind, targetId: id,
          detail: {
            //  متن، نه فهرست: `audit.scrub` فهرست را شیء می‌کند
            problem: PROBLEM, enforced: this.enforce, path: this.path, deviceId: this.deviceId || '',
            edits: ops.map((o) => `${o.table}/${o.row} ${o.type}: ${o.fields.join(',')}`).join('; '),
            more: this.notes.size > MAX_EVENTS ? this.notes.size - MAX_EVENTS : undefined, text: kind === 'sale' ? TEXT : DOC_TEXT,
          },
        });
      } catch (err) {
        console.error('[finalized-sales] رویداد ثبت نشد:', err.message);
      }
    }
    this.notes.clear();
    for (const w of await this.landed()) {
      try {
        await require('./audit').log({
          shopId: this.ctx.accountId, actorType: 'user', userId: this.ctx.userId || '', ip: this.ip,
          action: ACTION, targetType: WATCH[w.kind][0], targetId: w.id,
          detail: {
            problem: w.kind, enforced: this.enforce, path: this.path, deviceId: this.deviceId || '',
            from: w.from, to: w.to, text: WATCH[w.kind][2],
          },
        });
      } catch (err) {
        console.error('[finalized-sales] ردیفِ سابقه ثبت نشد:', err.message);
      }
    }
    for (const [sale, error] of this.voidFails) {
      try {
        await require('./audit').log({
          shopId: this.ctx.accountId, actorType: 'user', userId: this.ctx.userId || '', ip: this.ip,
          action: ACTION, targetType: 'sale', targetId: sale,
          detail: { problem: VOID_PROBLEM, enforced: this.enforce, path: this.path, deviceId: this.deviceId || '', error, text: VOID_TEXT },
        });
      } catch (err) {
        console.error('[finalized-sales] رویدادِ لغو ثبت نشد:', err.message);
      }
    }
    this.voidFails.clear();
  }
}

/* ------------------------------------------------------------------ Sync v1 */

const V1_ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
//  هویتِ حسابِ دکان در v1 — یک جا، نه مقایسهٔ درجای رشته (سنجهٔ product-literals)
const SHOP_V1 = { app: 'shop', accountKind: 'shop' };

/**
 * نگهبانِ یک push ِ v1 ⇐ Guard یا null (پمپ، دسته‌ای بی فاکتور و قلم، پرچمِ خاموش ⇒ null و هیچ پرسشی).
 * `lib/sync-v1.js` آن را برای هر op پس از قفلِ ردیف صدا می‌زند (همان تراکنش، زیرِ قفلِ حساب).
 */
/** ردیفِ زندهٔ v1 ِ دکان ⇐ داده یا null (`c`: تراکنش یا خودِ `db`)؛ `dead`: ردیفِ حذف‌شده با محتوا (سنگِ قبرِ خالی ⇒ null) */
const v1RowOf = (shopId, dead = false) => async (c, table, id) => {
  const r = (await c.query(
    `SELECT data FROM sync_rows WHERE ${V1_ACC} AND table_name=$2 AND row_id=$3 AND deleted_at IS ${dead ? 'NOT NULL' : 'NULL'}`,
    [shopId, table, id]
  )).rows[0];
  return r && !(dead && isEmpty(r.data)) ? r.data : null;
};

/**
 * ادغام‌های دستهٔ v1 ⇐ Map(مشتری ⇐ {to, opId}) — فقط opی که `push` ِ v1 می‌تواند بنشاند: همان `validateOp` و op_id ِ
 * یکتا در دسته (op_id ِ دوباره ⇒ «ادغام نیست»)؛ op_id ِ نشسته یا تکراریِ `oplog` هنگامِ پرسش ([Guard.claimOf]). آخرین
 * op ِ هر مشتری = حالِ پایانِ دسته. ⛔ SR43-1: پیش‌تر op ِ خام (شناسهٔ بد، تکراری) هم «ادغام» شمرده می‌شد.
 */
function v1Merges(ops, tableOfRaw) {
  const merges = new Map();
  if (!ops.some((r) => tableOfRaw(r) === 'debtors' && isPlainObject(r.fields) && hasOwn(r.fields, 'mergedInto'))) return merges;
  const { validateOp } = require('./sync-v1');
  const valid = ops.map((r) => validateOp(SHOP_V1.app, r)).filter((v) => v.ok).map((v) => v.op);
  const times = new Map();
  for (const o of valid) times.set(o.op_id, (times.get(o.op_id) || 0) + 1);
  for (const o of valid) {
    if (o.table !== 'debtors' || !hasOwn(o.fields, 'mergedInto')) continue;
    merges.set(o.row_id, { to: times.get(o.op_id) === 1 ? o.fields.mergedInto : null, opId: o.op_id });
  }
  return merges;
}

async function v1Guard(ctx, deviceId, ops, { ip = '' } = {}) {
  if (!ctx || ctx.app !== SHOP_V1.app || ctx.accountKind !== SHOP_V1.accountKind || !ctx.accountId || !Array.isArray(ops)) return null;
  const { tableOf } = require('./sync-v1');
  const tableOfRaw = (r) => (isPlainObject(r) ? tableOf(SHOP_V1.app, r.table ?? r.table_name ?? r.tableName) : null);
  //  ۲-۲۳ (R7): دسته‌ای که فقط ادغامِ مشتری را برمی‌دارد هم — پیش‌تر بی سند نگهبان نمی‌گرفت و ردی نمی‌ماند
  const touchesMerge = (r) => tableOfRaw(r) === 'debtors' && isPlainObject(r.fields) && hasOwn(r.fields, 'mergedInto');
  if (!ops.some((r) => GUARDED(tableOfRaw(r)) || touchesMerge(r))) return null;
  if (!(await flagOn(ctx.accountId))) return null;
  const merges = v1Merges(ops, tableOfRaw);
  const shopId = ctx.accountId;
  return new Guard({
    ctx, deviceId, path: 'v1', merges, ip,
    mode: (await chainDevice(shopId, deviceId, boundOf(ctx))) ? 'enforce' : 'log',
    rowOf: v1RowOf(shopId), deadOf: v1RowOf(shopId, true),
    firstInsert: async (c, saleId) => {
      const r = (await c.query(
        `SELECT device_id, client_ts FROM oplog WHERE ${V1_ACC} AND table_name='sales' AND row_id=$2 AND op_type='insert'
          ORDER BY server_seq ASC LIMIT 1`, [shopId, saleId]
      )).rows[0];
      return r ? { device: r.device_id, ts: Number(r.client_ts) } : null;
    },
  });
}

/**
 * پیش از `restoreConflict` ِ مدیرِ ارشد (`routes/admin-sync.js`): همان op (مقدارِ بازنده روی ردیفِ امروز) از نگهبانِ
 * `enforce` با راهِ `admin` ⇐ ۴۰۹ (`sale_finalized_edit_forbidden` یا `doc_posted_edit_forbidden`، پرتاب) + رویداد، یا هیچ
 * (آزاد). سندِ حذف‌شده هم: بازگرداندن آن را سرِ جا زنده می‌کرد (`*revive*`).
 * پمپ، جدولِ دیگر، پرچمِ خاموش و تعارضِ نبوده (خودِ restore ۴۰۴ می‌دهد) ⇒ هیچ پرسشِ دیگری.
 * وضعیتِ فاکتور هم با بازگرداندن عوض نمی‌شود: برگشت از لغو ویرایش است و لغو کارِ خودِ دکان با دلیل (حلقهٔ لغو).
 */
async function adminRestoreCheck(conflictId, { adminId = '', ip = '' } = {}) {
  const cf = await db.one(
    'SELECT app, account_kind, account_id, table_name, row_id, field, loser_value FROM sync_conflicts WHERE id=$1',
    [Number(conflictId) || 0]
  );
  if (!cf || cf.app !== SHOP_V1.app || cf.account_kind !== SHOP_V1.accountKind || !GUARDED(cf.table_name)) return;
  const shopId = cf.account_id;
  if (!(await flagOn(shopId))) return;
  const rowOf = v1RowOf(shopId);
  const g = new Guard({
    ctx: { accountId: shopId, userId: adminId }, deviceId: require('./sync-v1').SERVER_DEVICE, mode: 'enforce', path: 'admin',
    merges: new Map(), rowOf, deadOf: v1RowOf(shopId, true), firstInsert: async () => null, ip, voids: false,
  });
  const fields = { [cf.field]: cf.loser_value };
  const cur = await rowOf(db, cf.table_name, cf.row_id);
  let res = await g.check(db, { table: cf.table_name, rowId: cf.row_id, type: 'update', fields }, cur);
  if (!(res && res.reject) && cur && cf.table_name === 'sales' && cf.field === 'status'
      && statusOf(cf.loser_value) !== statusOf(cur.status)) {
    g.note(cf.row_id, { table: cf.table_name, row: cf.row_id, type: 'update', fields: ['status'] });
    res = { reject: REASON };
  }
  await g.finish();
  if (res && res.reject) throw forbiddenError(res.reject);
}

/* ------------------------------------------------------------------- راهِ قدیم */

/** نامِ مجموعه در `lib/sync.js` ⇐ نامِ جدولِ v1 (همان نام‌ها) */
const LEGACY = Object.freeze({
  sales: 'sales', saleItems: 'sale_items', debtors: 'debtors',
  returns: 'sale_returns', transactions: 'payments', purchases: 'purchases', stockMovements: 'stock_movements',
  auditLog: 'shop_audit_log',
});

/** ردیفِ راهِ قدیم ⇐ داده یا null؛ `dead`: حذف‌شده با محتوا (همان `v1RowOf`) */
const legacyRowOf = (shopId, dead) => async (c, collection, id) => {
  const r = (await c.query(
    `SELECT data FROM ${LEGACY[collection]} WHERE shop_id=$1 AND id=$2 AND deleted = $3`, [shopId, id, dead]
  )).rows[0];
  return r && !(dead && isEmpty(r.data)) ? r.data : null;
};

/**
 * نگهبانِ `sync.pushChanges` ⇐ Guard یا null. `mode`: `log` برای `/api/sync` (برنامهٔ کهنه، F0-02)،
 * `enforce` ِ صریح دیگر صدازننده ندارد (درِ مستقیمِ `routes/data.js` برداشته شد)؛ نشست/شناسهٔ زنجیره‌دار همان `enforce`.
 * `boundDevice`: دستگاهِ خودِ نشست (`req.device.device_uid`). `log` با شناسهٔ ادعاشده یا نشستِ زنجیره‌دار ⇐ `enforce`
 * (راه همان `legacy`): برنامهٔ تازه از راهِ قدیم نمی‌نویسد (F0-02)، پس این فقط دورزدنِ نگهبانِ v1 است.
 */
async function legacyGuard({ shopId, userId = '', deviceId = '', boundDevice = '', ip = '' }, mode, changes) {
  if (!shopId || !mode || !Array.isArray(changes)) return null;
  //  ۲-۲۳ (R7): کلِ ردیفِ مشتری هم (بی `mergedInto` = برداشتنِ ادغام)
  if (!changes.some((r) => r && (GUARDED(String(r.collection || '')) || r.collection === 'debtors'))) return null;
  if (!(await flagOn(shopId))) return null;
  //  ⛔ SR43-1: هر نوشتنِ مشتری، آخرینش (راهِ قدیم کلِ ردیف را جایگزین می‌کند ⇒ بی `mergedInto` = «ادغام نیست»؛ حذف ⇒ نیست)؛
  //  جابه‌جایی‌ای که به آن تکیه کرد پس از دسته با [Guard.settle] سنجیده می‌شود (نوشتنِ کهنه یا کنارگذاشته نمی‌نشیند)
  const merges = new Map();
  for (const r of changes) {
    if (!r || r.collection !== 'debtors') continue;
    const live = r.deleted !== true && isPlainObject(r.data);
    merges.set(String(r.id || ''), { to: live && !isEmpty(r.data.mergedInto) ? r.data.mergedInto : null });
  }
  const path = mode === 'enforce' ? 'api' : 'legacy';
  const enforce = mode === 'enforce' || (await chainDevice(shopId, deviceId, boundDevice));
  return new Guard({
    ctx: { accountId: shopId, userId }, deviceId, mode: enforce ? 'enforce' : 'log', path, merges, ip,
    rowOf: legacyRowOf(shopId, false), deadOf: legacyRowOf(shopId, true),
    firstInsert: async () => null,                            //  راهِ قدیم `ts` ندارد: قلمِ تازه فقط با فاکتورِ همین دسته
  });
}

/** ردِ اجباری: ۴۰۹ و تراکنش برمی‌گردد (هیچ بخشی از نوشتن نمی‌نشیند) */
const forbiddenError = (reason = REASON) => (reason === DOC_REASON
  ? conflict('سندِ مالیِ ثبت‌شده ویرایش یا حذف نمی‌شود؛ اصلاح فقط با سندِ معکوس', DOC_REASON)
  : conflict('فاکتورِ ثبت‌شده ویرایش یا حذف نمی‌شود؛ اصلاح فقط با لغو یا برگشت (سندِ معکوس)', REASON));

module.exports = {
  REASON, DOC_REASON, PROBLEM, VOID_PROBLEM, SALE_FIXED, SALE_ONCE, ITEM_FIXED, DOC_FIXED, SHIFT_DOCS, voidChainOf,
  v1Guard, legacyGuard, adminRestoreCheck, forbiddenError, nextSale,
};
