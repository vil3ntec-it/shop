'use strict';
/**
 * نمای عمومی (لایهٔ ۳ِ `docs/MARKETPLACE-CONTRACT-fa.md`، کارِ S2) — همهٔ شکل‌هایی که
 * `/api/store/*` و `/api/market/*` به مشتری می‌دهند، یک‌جا و با **فهرستِ سفیدِ فیلدها**.
 *
 *   قاعده ── هر فیلدی که این‌جا نیست، برای بازار وجود ندارد: سازندهٔ هر نما (مثلِ
 *           `profile`، `productView`، `publicOrder`، `cardOf`) هر چه بسازد، پیش از
 *           رفتن به پاسخ از `view(شکل، شیء)` می‌گذرد و فقط کلیدهای همین فهرست می‌مانند
 *           (به همان ترتیبِ شیءِ ورودی ⇒ پاسخِ امروز بایت‌به‌بایت همان است).
 *   نسخه ── `SCHEMA_VERSION`. فهرستِ زیر با `core/test-vectors/market-public-v<نسخه>.json`
 *           سنجیده می‌شود (`test/public-views.test.js`)؛ افزودن/برداشتنِ فیلد بی بالا بردنِ
 *           نسخه و بی پروندهٔ تازه آزمون را سرخ می‌کند. همان پرونده را آزمونِ `core`
 *           (`ir.vil3ntec.tohid.core.market`) هم می‌خواند ⇒ برنامهٔ خرید و سرور یک تعریف دارند.
 *           پروندهٔ نسخهٔ پیشین دست نمی‌خورد و نسخهٔ تازه فقط می‌افزاید (کلاینتِ کهنه کلیدِ ناشناخته را
 *           نادیده می‌گیرد: `ignoreUnknownKeys` ِ `MarketContract.json` و `ShopperJson`).
 *           v2 (۱۴۰۵/۰۸/۲۲): `order.refund?` (NTF-08، پرچمِ پیش‌فرض‌روشنِ `shop.orders.refund_tracking`) با
 *           `receipts?` ِ درونش (`shop.orders.refund_receipt`) — شکل در `lib/order-refund-view.js`.
 *   نشانه ── `'name?'` = اختیاری (فقط وقتی سازنده گذاشته)؛ بقیه همیشه هستند.
 *   تودرتو ─ `NESTED[شکل][فیلد]` = شکلِ زیرشیء (یا آرایه‌ای از آن)؛ زیرشیءِ بی شکل همان
 *           است که سازنده با مقدارهای ثابت ساخته (مثلِ `fulfillment`).
 *   ⛔ هرگز: شناسهٔ دکان/کاربر/دستگاه/سفارش (جز توکنِ HMAC دار)، شمارهٔ مالک یا مشتری،
 *           ایمیل، مختصاتِ دقیق، قیمتِ خرید. `test/public-views.test.js` پاسخ‌های واقعی را می‌کاود.
 *   پیک ── `delivery` (S5) امروز در هیچ پاسخی نیست؛ شکلش این‌جا ثابت شده تا S5 فقط وصلش کند
 *           (با پرچمِ خاموش و نسخهٔ تازه).
 *   پرچم ── بریدنِ پاسخ‌های زنده پشتِ `market.public_views` (scope `all`، پیش‌فرض خاموش) است:
 *           سازنده‌ها `pub`/`pubList` را صدا می‌زنند که با پرچمِ خاموش همان شیء را بی دست‌زدن
 *           برمی‌گرداند (پاسخِ امروزِ main) تا فیلدِ تازهٔ شاخه‌های دیگر (M4، S3، S5، S7) پیش از
 *           رسیدن به این فهرست و نسخهٔ تازه بی‌صدا بریده نشود. `view`/`list` همیشه می‌بُرند
 *           (آزمون، پیش‌نمایشِ «مشتری چه می‌بیند»). مسیرهای `/store` و `/market` پیش از هر
 *           پاسخ `gate` را می‌گذرانند که پرچم را (با همان حافظهٔ `market.gateCacheMs`) می‌خواند.
 */

const SCHEMA_VERSION = 2;

const PRESENCE = ['badge', 'mode', 'declaredUntil', 'connection', 'lastConfirmedAt', 'orderAcceptance',
  'timezone', 'localTime', 'hoursSet', 'holiday', 'today', 'notice'];

const FIELDS = Object.freeze({
  //  §۱.۱ — `GET /api/store/:slug` ⇒ store. `place?` (`{ text, map }`، مهاجرتِ ۲۱۵) و `logo?` ِ کارتِ بازارچه: ممیزیِ ویترین
  //  ۱۴۰۵/۰۸/۱۷، اختیاری و فقط وقتی هست ⇒ افزوده به v1 (پاسخِ پیشین همان).
  shop: ['slug', 'title', 'tagline', 'contactPhone', 'accent', 'status', 'fulfillment', 'deliveryFeeMinor',
    'minOrderMinor', 'currency', 'payment', 'categories', 'delivery?', 'media?', 'ordering?', 'place?'],
  //  «سفارش می‌پذیرد؟» (#467، پرچمِ `shop.orders.subscribers_only`؛ خاموش ⇒ کلید نیست)
  ordering: ['accepting', 'code?', 'message?'],
  //  لوگو و تصویرِ سرِ ویترین (M3 #428): main همیشه می‌فرستد؛ پرچمِ خاموش ⇒ هر دو null. افزوده به v1.
  shopMedia: ['logo', 'header'],
  mediaFile: ['version', 'width', 'height', 'mime', 'url'],
  //  §۱.۲ — حضور و ساعتِ کاری
  presence: PRESENCE,
  //  کارتِ دکان در بازار: همان حضور بی `mode`
  cardPresence: PRESENCE.filter((k) => k !== 'mode'),
  shopCard: ['slug', 'title', 'tagline', 'accent', 'category', 'categoryLabel', 'area', 'status', 'fulfillment',
    'deliveryFeeMinor', 'minOrderMinor', 'currency', 'productCount', 'distanceKm', 'isNew', 'cover?', 'logo?'],
  //  ارجاعِ کوتاه به دکان روی کالای بازار
  shopRef: ['slug', 'title', 'accent', 'badge', 'orderAcceptance', 'distanceKm'],
  //  §۱.۳ — کالا در ویترین (`description`/`specs` فقط در صفحهٔ کالا)
  //  `regularPriceMinor` فقط در «ارزان‌تر از قیمتِ دکان» (`highlights.deals`، WEB-02 #626)
  //  `photoVersion` (IMG-12) فقط با عکس: نسخهٔ ۱۲-hex برای `?v=` ِ نشانیِ عکس. افزوده به v1.
  product: ['id', 'name', 'unit', 'category', 'priceMinor', 'regularPriceMinor?', 'maxQtyMilli', 'photo', 'photoVersion?',
    'stock', 'description?', 'specs?',
    //  MKT-06: «چرخهٔ مصرف» (روز) ِ فرمِ کالا — فقط وقتی دکان گذاشته باشد؛ برای یادآوریِ «وقتِ دوباره خریدن؟» ِ برنامهٔ خرید
    'consumptionDays?'],
  //  کالا در خانهٔ بازار (`regularPriceMinor` فقط با `showDeals` ِ صاحب دکان)
  marketProduct: ['id', 'name', 'unit', 'category', 'priceMinor', 'regularPriceMinor?', 'maxQtyMilli', 'photo',
    'photoVersion?', 'stock', 'shop'],
  searchProduct: ['id', 'name', 'unit', 'category', 'priceMinor', 'photo', 'photoVersion?', 'shop'],
  //  §۱.۴ — موجودی؛ عددها فقط با `show_qty = exact`
  stock: ['status', 'reason?', 'checkedAt?', 'unit?', 'prepMinutes?', 'sellableMilli?', 'readyMilli?', 'laterMilli?'],
  //  §۱.۷ — سفارش (پیگیری با توکن). افزوده به v1 از main (همان شکلِ main): `substitution` (CHK-15 #525)،
  //  `smallBasketFeeMinor`/`slotFeeMinor` (CHK-06/07 #494)، `onlinePay` فقط در پیگیری (PAY-21 #529)، `paymentStep` فقط در
  //  پیگیری و فقط با «پرداخت پس از پذیرش» ِ روشن (#910؛ از مهاجرتِ ۲۱۱ پیش‌فرضِ دکانِ تازه)
  order: ['token', 'orderNo', 'status', 'statusReason', 'substitution', 'fulfillment', 'paymentMethod', 'paymentStatus',
    'currency', 'itemsCount', 'lines', 'subtotalMinor', 'discountMinor', 'deliveryFeeMinor', 'totalMinor', 'readyAt',
    'scheduledFor', 'slotEndsAt', 'zoneName', 'smallBasketFeeMinor', 'slotFeeMinor', 'createdAt', 'updatedAt',
    'canCancel', 'timeline', 'onlinePay?', 'paymentStep?',
    //  v2 — فقط در پیگیری و فقط با پرچمِ `shop.orders.refund_tracking` (پیش‌فرض روشن)؛ `null` تا درخواستِ برگشت یا ردیفِ دفتر نیست
    'refund?'],
  //  `substitution`/`substituteName` ِ هر قلم: CHK-16 #525
  orderLine: ['productId', 'name', 'unit', 'qtyMilli', 'unitPriceMinor', 'lineTotalMinor', 'note', 'substitution',
    'substituteName'],
  //  دکمهٔ «پرداخت با حساب‌پی» در پیگیری (PAY-21 #529)
  onlinePay: ['available'],
  //  کارتِ «پرداخت» ِ پیگیری (`lib/order-flow.trackView`): حال، انتخابِ مشتری، مبلغِ سرور، نقدِ تحویل/برداشت
  paymentStep: ['state', 'choice', 'amountMinor', 'cash', 'canChoose', 'online'],
  orderEvent: ['status', 'at'],
  //  v2 — NTF-08 (`lib/order-refund-view.js`): وضعیت (`requested` · `in_progress` · `partial` · `full`)، مبلغِ برگشته و مانده،
  //  ردیف‌های دفتر بی دلیلِ کارمند و بی کننده؛ `receipts?` فقط با `shop.orders.refund_receipt` (E2E-RET-11، `order-refund-receipt`)
  orderRefund: ['status', 'requestedAt', 'refundedMinor', 'remainingMinor', 'currency', 'entries', 'updatedAt', 'receipts?'],
  refundEntry: ['amountMinor', 'method', 'at', 'final'],
  //  رسیدِ اصلاحیِ هر ردیف: شمارهٔ `<شمارهٔ سفارش>-R<seq>` (seq ِ همان سفارش، نه شناسهٔ ردیف) و مبلغِ درستِ سفارش پس از آن
  refundReceipt: ['no', 'seq', 'at', 'orderNo', 'currency', 'method', 'final', 'totalMinor', 'refundedBeforeMinor', 'amountMinor',
    'refundedAfterMinor', 'netMinor'],
  //  فهرستِ «سفارش‌های من» (ویترین، حسابِ مشتری، بازار)
  orderSummary: ['orderNo', 'status', 'fulfillment?', 'totalMinor', 'itemsCount', 'createdAt', 'scheduledFor?',
    'preview?', 'token', 'store?'],
  storeRef: ['slug', 'title', 'accent'],
  reorderLine: ['productId', 'name', 'unit', 'qtyMilli', 'previousPriceMinor', 'priceMinor', 'maxQtyMilli',
    'available', 'reason', 'stock'],
  //  §۱.۸ — پیک (S5)؛ امروز در هیچ پاسخی نیست
  delivery: ['status', 'mode'],
});

const NESTED = Object.freeze({
  shop: { status: 'presence', media: 'shopMedia', ordering: 'ordering' },
  shopMedia: { logo: 'mediaFile', header: 'mediaFile' },
  shopCard: { status: 'cardPresence' },
  product: { stock: 'stock' },
  marketProduct: { stock: 'stock', shop: 'shopRef' },
  searchProduct: { shop: 'shopRef' },
  order: { lines: 'orderLine', timeline: 'orderEvent', onlinePay: 'onlinePay', paymentStep: 'paymentStep', refund: 'orderRefund' },
  orderRefund: { entries: 'refundEntry', receipts: 'refundReceipt' },
  orderSummary: { store: 'storeRef' },
  reorderLine: { stock: 'stock' },
});

const allowed = new Map(Object.entries(FIELDS).map(([shape, list]) => [
  shape, new Map(list.map((f) => (f.endsWith('?') ? [f.slice(0, -1), false] : [f, true]))),
]));

/**
 * شیء ⇒ فقط فیلدهای مجازِ همان شکل، به ترتیبِ خودِ شیء. `undefined` نوشته نمی‌شود
 * (همان رفتارِ `JSON.stringify`). `null` همان `null` می‌ماند.
 */
function view(shape, obj) {
  const keys = allowed.get(shape);
  if (!keys) throw new Error(`شکلِ عمومیِ ناشناخته: ${shape}`);
  if (obj === null || obj === undefined) return obj;
  const nested = NESTED[shape] || {};
  const out = {};
  for (const k of Object.keys(obj)) {
    if (!keys.has(k) || obj[k] === undefined) continue;
    const sub = nested[k];
    const v = obj[k];
    out[k] = sub ? (Array.isArray(v) ? v.map((x) => view(sub, x)) : view(sub, v)) : v;
  }
  return out;
}

const list = (shape, items) => items.map((x) => view(shape, x));

const FLAG = 'market.public_views';
const state = { on: false, at: -Infinity, flight: null };

/** پرچمِ `market.public_views` را می‌خواند (یک‌پرواز، با حافظهٔ `market.gateCacheMs`). */
function sync(at = Date.now()) {
  if (state.flight) return state.flight;
  const ttl = Number(require('../config').market.gateCacheMs) || 0;
  if (ttl > 0 && at - state.at < ttl) return Promise.resolve(state.on);
  state.flight = require('./orders').flagOf('all', FLAG)
    .then((on) => { state.on = on === true; state.at = at; return state.on; })
    .finally(() => { state.flight = null; });
  return state.flight;
}

/** میان‌افزارِ مسیرهای عمومی: پیش از ساختنِ پاسخ، پرچم تازه است. */
async function gate(req, res, next) {
  try { await sync(); } catch (err) { return next(err); }
  return next();
}

/** نمای پاسخِ زنده: پرچم روشن ⇒ `view`؛ خاموش ⇒ همان شیء (رفتارِ پیش از S2). */
const pub = (shape, obj) => (state.on ? view(shape, obj) : obj);
const pubList = (shape, items) => (state.on ? list(shape, items) : items);
const enforced = () => state.on;

/** فیلدهای همیشگی و همهٔ فیلدهای مجازِ یک شکل — برای آزمون. */
function keysOf(shape) {
  const keys = allowed.get(shape);
  return { required: [...keys].filter(([, r]) => r).map(([k]) => k), all: [...keys.keys()] };
}

/** §۱.۸ — نمای پیک برای مشتری از ردیفِ `delivery_assignments` (S5 وصلش می‌کند). */
function delivery(a) {
  return a ? view('delivery', { status: a.status, mode: a.mode }) : null;
}

module.exports = { SCHEMA_VERSION, FIELDS, NESTED, FLAG, view, list, pub, pubList, sync, gate, enforced, keysOf, delivery };
