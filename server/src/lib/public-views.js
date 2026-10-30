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
 *   نشانه ── `'name?'` = اختیاری (فقط وقتی سازنده گذاشته)؛ بقیه همیشه هستند.
 *   تودرتو ─ `NESTED[شکل][فیلد]` = شکلِ زیرشیء (یا آرایه‌ای از آن)؛ زیرشیءِ بی شکل همان
 *           است که سازنده با مقدارهای ثابت ساخته (مثلِ `fulfillment`).
 *   ⛔ هرگز: شناسهٔ دکان/کاربر/دستگاه/سفارش (جز توکنِ HMAC دار)، شمارهٔ مالک یا مشتری،
 *           ایمیل، مختصاتِ دقیق، قیمتِ خرید. `test/public-views.test.js` پاسخ‌های واقعی را می‌کاود.
 *   پیک ── `delivery` (S5) امروز در هیچ پاسخی نیست؛ شکلش این‌جا ثابت شده تا S5 فقط وصلش کند
 *           (با پرچمِ خاموش و نسخهٔ تازه).
 */

const SCHEMA_VERSION = 1;

const PRESENCE = ['badge', 'mode', 'declaredUntil', 'connection', 'lastConfirmedAt', 'orderAcceptance',
  'timezone', 'localTime', 'hoursSet', 'holiday', 'today', 'notice'];

const FIELDS = Object.freeze({
  //  §۱.۱ — `GET /api/store/:slug` ⇒ store
  shop: ['slug', 'title', 'tagline', 'contactPhone', 'accent', 'status', 'fulfillment', 'deliveryFeeMinor',
    'minOrderMinor', 'currency', 'payment', 'categories', 'delivery?'],
  //  §۱.۲ — حضور و ساعتِ کاری
  presence: PRESENCE,
  //  کارتِ دکان در بازار: همان حضور بی `mode`
  cardPresence: PRESENCE.filter((k) => k !== 'mode'),
  shopCard: ['slug', 'title', 'tagline', 'accent', 'category', 'categoryLabel', 'area', 'status', 'fulfillment',
    'deliveryFeeMinor', 'minOrderMinor', 'currency', 'productCount', 'distanceKm', 'isNew', 'cover?'],
  //  ارجاعِ کوتاه به دکان روی کالای بازار
  shopRef: ['slug', 'title', 'accent', 'badge', 'orderAcceptance', 'distanceKm'],
  //  §۱.۳ — کالا در ویترین (`description`/`specs` فقط در صفحهٔ کالا)
  product: ['id', 'name', 'unit', 'category', 'priceMinor', 'maxQtyMilli', 'photo', 'stock', 'description?', 'specs?'],
  //  کالا در خانهٔ بازار (`regularPriceMinor` فقط با `showDeals` ِ صاحب دکان)
  marketProduct: ['id', 'name', 'unit', 'category', 'priceMinor', 'regularPriceMinor?', 'maxQtyMilli', 'photo',
    'stock', 'shop'],
  searchProduct: ['id', 'name', 'unit', 'category', 'priceMinor', 'photo', 'shop'],
  //  §۱.۴ — موجودی؛ عددها فقط با `show_qty = exact`
  stock: ['status', 'reason?', 'checkedAt?', 'unit?', 'prepMinutes?', 'sellableMilli?', 'readyMilli?', 'laterMilli?'],
  //  §۱.۷ — سفارش (پیگیری با توکن)
  order: ['token', 'orderNo', 'status', 'statusReason', 'fulfillment', 'paymentMethod', 'paymentStatus', 'currency',
    'itemsCount', 'lines', 'subtotalMinor', 'discountMinor', 'deliveryFeeMinor', 'totalMinor', 'readyAt',
    'scheduledFor', 'slotEndsAt', 'zoneName', 'createdAt', 'updatedAt', 'canCancel', 'timeline'],
  orderLine: ['productId', 'name', 'unit', 'qtyMilli', 'unitPriceMinor', 'lineTotalMinor', 'note'],
  orderEvent: ['status', 'at'],
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
  shop: { status: 'presence' },
  shopCard: { status: 'cardPresence' },
  product: { stock: 'stock' },
  marketProduct: { stock: 'stock', shop: 'shopRef' },
  searchProduct: { shop: 'shopRef' },
  order: { lines: 'orderLine', timeline: 'orderEvent' },
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

/** فیلدهای همیشگی و همهٔ فیلدهای مجازِ یک شکل — برای آزمون. */
function keysOf(shape) {
  const keys = allowed.get(shape);
  return { required: [...keys].filter(([, r]) => r).map(([k]) => k), all: [...keys.keys()] };
}

/** §۱.۸ — نمای پیک برای مشتری از ردیفِ `delivery_assignments` (S5 وصلش می‌کند). */
function delivery(a) {
  return a ? view('delivery', { status: a.status, mode: a.mode }) : null;
}

module.exports = { SCHEMA_VERSION, FIELDS, NESTED, view, list, keysOf, delivery };
