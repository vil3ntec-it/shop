'use strict';
/**
 * فاکتورِ عمومی — آن‌چه QRِ هر فاکتور باز می‌کند (فاکتورِ نسخهٔ ۲، `/r/<نشان>/<توکن>`).
 *
 * ── قاعده‌ها ─────────────────────────────────────────────────────────
 * ⛔ فقط همان دکان: فروش با `receiptToken` پیدا می‌شود و **فقط** اگر نشانِ حسابش
 *    (`receipt-token.shopTag`) همان نشانِ نشانی باشد. همان توکن در فروشِ دکانِ دیگر
 *    هرگز این‌جا نمی‌آید؛ قلم‌ها، نامِ کالاها، سربرگ و لوگو هم فقط از همان حساب.
 * ⛔ «هنوز همگام نشده»، توکن یا نشانِ ناشناخته، فروشِ پاک‌شده، دکانِ غیرفعال و
 *    «QR خاموش» همه **یک** پاسخ‌اند (۴۰۴ ِ یکسان) — هیچ پیشگویی دربارهٔ وجودِ فروش نیست.
 * ⛔ فقط آن‌چه روی کاغذ است: نامِ قرض‌دار، شناسه‌ها، دستگاه و تلفنِ مشتری هرگز.
 * • خواندن از دفترِ Sync v1 (`sync_rows`) و، اگر v3 روشن است، `sync_v3_rows`
 *   (`Minor`/`Milli` با `toLegacy` به واحدِ دفتر). نمایه‌ها: مهاجرتِ ۱۱۹.
 * • عددها همین‌جا با هستهٔ مشترکِ متن و تقویم (`text-core.js`، `calendar-core.js`)
 *   به رقم و نمادِ انتخابیِ دکان در سربرگ قالب می‌خورند؛ صفحه فقط نشان می‌دهد.
 * • مبلغ‌ها همان فیلدهای ذخیره‌شده‌اند؛ این‌جا هیچ مبلغی حساب نمی‌شود.
 * • تخفیفِ خودکار (پیگیریِ #307، فقط با پرچمِ `shop.promo_stamp`): ستونِ قلم‌ها همان مبلغِ
 *   پرداختی و «جمع اقلام» همان `sale.total` (جمعِ همان ستون) می‌مانند؛ قلمِ تخفیف‌دار «مقدار ×
 *   قیمتِ فهرست − تخفیفش» را نشان می‌دهد و تخفیفِ فاکتور فقط یک ردیفِ **آگاهی** است
 *   («شاملِ تخفیفِ خودکار …»، `included`) — هیچ مبلغی دو بار کم نمی‌شود. خاموش ⇒ همان پاسخِ پیشین.
 */
const config = require('../config');
const { query, one } = require('../db');
const token = require('./receipt-token');
const profiles = require('./receipt-profile');
const T = require('./text-core');
const C = require('./calendar-core');
const schema = require('./sync-v3-schema');
const { toLegacy } = require('./sync-v3-to-v1');
const { notFound } = require('../middleware/errors');

const STORES = Object.freeze([
  Object.freeze({ fmt: 'v1', table: 'sync_rows', order: "(field_seq->>'saleId')::bigint" }),
  Object.freeze({ fmt: 'v3', table: 'sync_v3_rows', order: "field_hlc->>'saleId'" }),
]);
const MAX_ITEMS = 500;
const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
const NOT_YET = 'هنوز ثبت نشده، کمی بعد دوباره';

const KIND_LABELS = Object.freeze({
  cash: 'نقد', credit: 'نسیه', account: 'نسیه', card: 'کارت', transfer: 'حواله', wallet: 'کیف پول',
  store_credit: 'اعتبار فروشگاه', gift_card: 'کارت هدیه', other: 'سایر', mixed: 'ترکیبی',
  online: 'پرداختِ آنلاین', cod: 'نقد هنگامِ تحویل',
});
/** پرچمِ نشانِ تخفیف روی فاکتور — همان `routes/shop-promos.js` و برنامه */
const PROMO_FLAG = 'shop.promo_stamp';
const MAX_PROMO_NAMES = 5;
const LABELS = Object.freeze({
  includesAuto: 'شاملِ تخفیفِ خودکار',
  subtotal: 'جمع اقلام', discount: 'تخفیف', total: 'مبلغ نهایی', paid: 'پرداختی',
  tendered: 'دریافتی', change: 'پولِ پس', remaining: 'باقی‌مانده (نسیه)',
  cash: 'نقدی', credit: 'نسیه', online: 'آنلاین', item: 'کالا',
});

const gone = () => notFound(NOT_YET, 'receipt_not_found');

function asObject(x) {
  if (typeof x === 'string') { try { x = JSON.parse(x); } catch { return {}; } }
  return x && typeof x === 'object' && !Array.isArray(x) ? x : {};
}

const stores = () => STORES.filter((s) => s.fmt === 'v1' || config.syncV3.enabled);

function legacy(store, table, data) {
  const d = asObject(data);
  return store.fmt === 'v3' ? toLegacy(schema.specOf(table) || { money: [], qty: [] }, d) : d;
}

/**
 * فروشِ صاحبِ این نشانی — یا ۴۰۴.
 *
 * ⛔ نشانِ دکان **درونِ پرس‌وجو** و پیش از هر سقفی سنجیده می‌شود (۵ بایتِ اولِ
 *    `sha256(نمک + account_id)`): ردیف‌های دکان‌های دیگر با همان توکن — هر چند تا و هر
 *    قدر زودتر — اصلاً نامزد نمی‌شوند و نمی‌توانند فروشِ اصلی را بیرون برانند.
 *    نشان در JS هم دوباره سنجیده می‌شود.
 * از ردیف‌های همان دکان نخستین (به ترتیبِ رسیدن به سرور)؛ نخستینِ پاک‌شده ⇒ ۴۰۴
 * (به کپیِ بعدی نمی‌افتد).
 */
async function findSale(tagRaw, tokenRaw) {
  const tag = token.normalizeTag(tagRaw);
  const tagBytes = token.decodeTag(tag);
  const tok = token.normalizeToken(tokenRaw);
  if (!tag || !tagBytes || !tok) throw gone();
  const hits = [];
  for (const store of stores()) {
    const { rows } = await query(
      `SELECT account_id, row_id, data, deleted_at, created_at FROM ${store.table}
        WHERE app='shop' AND account_kind='shop' AND table_name='sales'
          AND data ? 'receiptToken' AND data->>'receiptToken' = $1
          AND substring(sha256(convert_to($3 || account_id, 'UTF8')) from 1 for ${tagBytes.length}) = $2
        ORDER BY created_at, row_id LIMIT 1`,
      [tok, tagBytes, token.TAG_SALT]
    );
    for (const r of rows) if (token.shopTag(r.account_id) === tag) hits.push({ ...r, store });
  }
  hits.sort((a, b) => Number(a.created_at) - Number(b.created_at));
  const hit = hits[0];
  if (!hit || (hit.deleted_at !== null && hit.deleted_at !== undefined)) throw gone();
  return { shopId: hit.account_id, saleId: hit.row_id, store: hit.store, sale: legacy(hit.store, 'sales', hit.data) };
}

async function itemsOf(store, shopId, saleId) {
  const { rows } = await query(
    `SELECT row_id, data FROM ${store.table}
      WHERE ${ACC} AND table_name='saleItems' AND data->>'saleId' = $2 AND deleted_at IS NULL
      ORDER BY created_at, ${store.order} NULLS LAST LIMIT ${MAX_ITEMS}`,
    [shopId, saleId]
  );
  return rows.map((r) => legacy(store, 'saleItems', r.data));
}

async function productsOf(store, shopId, ids) {
  if (!ids.length) return new Map();
  const { rows } = await query(
    `SELECT row_id, data FROM ${store.table} WHERE ${ACC} AND table_name='products' AND row_id = ANY($2::text[])`,
    [shopId, ids]
  );
  return new Map(rows.map((r) => [r.row_id, legacy(store, 'products', r.data)]));
}

async function timezoneOf(shopId) {
  const sf = await one('SELECT timezone FROM storefronts WHERE shop_id=$1', [shopId]);
  return (sf && sf.timezone) || config.defaults.timezone;
}

// ── قالب ─────────────────────────────────────────────────────────────

const num = (x) => (Number.isFinite(Number(x)) ? Number(x) : 0);
const isIsoDate = (s) => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s);

/** مقدار: تا سه رقمِ اعشار، صفرهای آخر برداشته (۱٫۵ کیلو همان ۱٫۵ می‌ماند) */
function formatQty(q, digits) {
  const n = Math.round(num(q) * 1000) / 1000;
  const s = Number.isInteger(n) ? String(n) : n.toFixed(3).replace(/0+$/, '');
  return T.displayDigits(s, digits);
}

function localParts(at, tz) {
  try {
    const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    }).formatToParts(new Date(at)).map((x) => [x.type, x.value]));
    return { date: `${p.year}-${p.month}-${p.day}`, time: `${p.hour}:${p.minute}` };
  } catch {
    return { date: '', time: '' };
  }
}

function invoiceNo(sale) {
  const n = sale.invoiceNumber;
  if (n === null || n === undefined || n === '') return '';
  const series = typeof sale.series === 'string' ? sale.series.trim() : '';
  return series ? `${series}-${n}` : String(n);
}

function kindOf(sale) {
  if (sale.channel === 'online' || (typeof sale.series === 'string' && sale.series.trim() === 'W')) return LABELS.online;
  if (sale.paymentMethod === 'credit') return LABELS.credit;
  if (sale.paymentMethod === 'cash' || !sale.paymentMethod) return LABELS.cash;
  return KIND_LABELS[sale.paymentMethod] || KIND_LABELS.other;
}

/** ردیف‌های پرداخت: پرداختِ ترکیبی (`payments`، به پول) یا «پرداختی» ِ امروز */
function paymentRows(sale, money) {
  if (Array.isArray(sale.payments) && sale.payments.length) {
    const rows = sale.payments.map((p) => {
      const x = asObject(p);
      return { label: KIND_LABELS[x.kind] || KIND_LABELS[x.method] || KIND_LABELS.other, amount: money(num(x.amount) / 100) };
    });
    if (Number.isFinite(sale.tenderedMinor)) rows.push({ label: LABELS.tendered, amount: money(sale.tenderedMinor / 100) });
    if (Number.isFinite(sale.changeMinor)) rows.push({ label: LABELS.change, amount: money(sale.changeMinor / 100) });
    return rows;
  }
  return [{ label: LABELS.paid, amount: money(sale.paidAmount) }];
}

/**
 * یادداشتِ ردیف یا فاکتور (POS-11) — هم‌زادِ `SaleNotes.clean` ِ core: یک خط، بی نویسهٔ کنترلی، حداکثر ۱۲۰
 * نویسه؛ نبود یا خالی ⇒ null (و کلیدش در خروجی نمی‌آید، تا فاکتورِ بی یادداشت همان پاسخِ پیشین باشد).
 */
function noteOf(x) {
  if (typeof x !== 'string') return null;
  const one = x.replace(/[\s\u0000-\u001f\u007f-\u009f]+/g, ' ').trim();
  if (!one) return null;
  return one.length <= 120 ? one : one.slice(0, 120).trimEnd();
}

/** سهم‌های تخفیفِ خودکارِ یک قلم (`SaleItem.promo`)؛ فقط عددِ صحیحِ مثبت */
function promoParts(it) {
  return (Array.isArray(it.promo) ? it.promo : []).map(asObject)
    .filter((p) => Number.isSafeInteger(p.discountMinor) && p.discountMinor > 0);
}

/** ردیفِ آگاهیِ «شاملِ تخفیفِ خودکار (نام‌ها)» — از جمع کم نمی‌شود؛ null ⇒ نیست */
function includedOf(sale, items, money) {
  const minor = sale.promoDiscountMinor;
  if (!Number.isSafeInteger(minor) || minor <= 0) return null;
  const names = [];
  for (const it of items) {
    for (const p of promoParts(it)) {
      const name = typeof p.name === 'string' ? p.name.trim().slice(0, 80) : '';
      if (name && !names.includes(name) && names.length < MAX_PROMO_NAMES) names.push(name);
    }
  }
  return { label: LABELS.includesAuto + (names.length ? ` (${names.join('، ')})` : ''), amount: money(minor / 100) };
}

function render({ sale, items, products, profile, tz, stamp = false }) {
  const digits = profile.digits;
  const money = (x) => T.formatAfghani(num(x), profile.symbol, digits);
  const plain = (x) => T.formatAfghani(num(x), 'none', digits);
  const at = num(sale.createdAt);
  const local = at > 0 ? localParts(at, tz) : { date: '', time: '' };
  const iso = isIsoDate(sale.date) ? sale.date : local.date;

  const lines = items.map((it) => {
    const product = products.get(it.productId) || {};
    const name = (typeof it.name === 'string' && it.name.trim()) || (typeof product.name === 'string' && product.name.trim()) || LABELS.item;
    const returned = num(it.returnedQty);
    const line = {
      name,
      qty: formatQty(it.quantity, digits),
      unit: typeof product.unit === 'string' ? product.unit : '',
      unitPrice: plain(it.unitPrice),
      total: plain(it.totalPrice),
      returned: returned > 0 ? formatQty(returned, digits) : null,
    };
    //  پرچمِ روشن و قلمِ تخفیف‌دار: «مقدار × قیمتِ فهرست − تخفیفش» = همان مبلغِ پرداختیِ قلم
    const promo = stamp ? promoParts(it).reduce((s, p) => s + p.discountMinor, 0) : 0;
    if (promo > 0 && Number.isSafeInteger(it.listPriceMinor) && it.listPriceMinor >= 0) {
      line.unitPrice = plain(it.listPriceMinor / 100);
      line.autoDiscount = plain(promo / 100);
    }
    const note = noteOf(it.note);
    if (note) line.note = note;
    return line;
  });
  const allWhole = items.every((it) => Number.isInteger(num(it.quantity)));
  const qtySum = items.reduce((s, it) => s + num(it.quantity), 0);

  const discounts = num(sale.discount) > 0 ? [{ label: LABELS.discount, amount: money(sale.discount) }] : [];
  const included = stamp ? includedOf(sale, items, money) : null;
  const view = {
    shop: { name: profile.name, address: profile.address, phone: T.displayDigits(profile.phone, digits), footer: profile.footer },
    no: T.displayDigits(invoiceNo(sale), digits),
    date: iso ? C.formatSolarNumeric(iso, digits) : '',
    time: T.displayDigits(local.time, digits),
    cashier: typeof sale.seller === 'string' ? sale.seller.trim() : '',
    kind: kindOf(sale),
    cancelled: sale.status === 'cancelled',
    items: lines,
    count: { lines: T.displayDigits(String(lines.length), digits), qty: allWhole ? formatQty(qtySum, digits) : null },
    subtotal: money(sale.total),
    discounts,
    total: money(sale.finalTotal),
    payments: paymentRows(sale, money),
    remaining: num(sale.remaining) > 0 ? { label: LABELS.remaining, amount: money(sale.remaining) } : null,
  };
  if (included) view.included = [included];
  const note = noteOf(sale.note);
  if (note) view.note = note;
  return view;
}

/** دادهٔ صفحه — یا ۴۰۴ ِ یکسان */
async function receiptOf(tag, tok) {
  const { shopId, saleId, store, sale } = await findSale(tag, tok);
  const shop = await one('SELECT name, status FROM shops WHERE id=$1', [shopId]);
  if (!shop || shop.status !== 'active') throw gone();
  const { profile } = await profiles.read(shopId);
  if (!profile.qr) throw gone();
  const items = await itemsOf(store, shopId, saleId);
  const ids = [...new Set(items.map((i) => i.productId).filter((x) => typeof x === 'string' && x))];
  const [products, tz, stamp] = await Promise.all([
    productsOf(store, shopId, ids), timezoneOf(shopId), require('./orders').flagOf(shopId, PROMO_FLAG),
  ]);
  const view = render({ sale, items, products, profile: { ...profile, name: profile.name || shop.name || '' }, tz, stamp });
  const logo = profile.logo ? await profiles.blob(profile.logo.hash) : null;
  view.shop.logo = logo ? `data:${logo.mime};base64,${logo.data.toString('base64')}` : null;
  return view;
}

module.exports = { NOT_YET, PROMO_FLAG, receiptOf, render, formatQty, noteOf };
