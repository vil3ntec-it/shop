'use strict';
/**
 * ویترین — فیلترِ کاتالوگ بر اساسِ برند، قیمت، موجود بودن و ویژگی (WEB-07، §۶.۱۴).
 *
 *   پرچم ──── `shop.storefront.filters` (`live_config`، دکان یا `all`، پیش‌فرض خاموش). خاموش ⇒ `parse`
 *             تهی است و `GET /api/store/:slug/products` بیت‌به‌بیت همان پاسخِ پیشین (پارامترهای تازه
 *             دیده نمی‌شوند، حتی نادرست‌شان)؛ `GET /:slug/filters` همان پاسخِ مسیرِ ناشناخته.
 *   ورودی ─── `brand` (یک برند، ≤۸۰)، `minPriceMinor`/`maxPriceMinor` (عددِ صحیح، یک‌صدمِ افغانی، ۰ تا
 *             سقفِ قیمتِ ویترین، کمینه ≤ بیشینه)، `inStock` (`1`/`true`/`0`/`false`)، `attrName` + `attrValue`
 *             (یک مشخصهٔ عمومیِ کالا، با هم یا هیچ). نادرست ⇒ ۴۰۰ `bad_filter`؛ کلیدِ تکراری (آرایه) نادرست است.
 *   داده ──── فقط آن‌چه واقعاً هست: برند از کاتالوگِ واحد (`catalog_products.brand`) اگر ردیفِ کالا آن‌جاست،
 *             وگرنه `brand` ِ ردیفِ همگام‌سازی — همان قاعدهٔ `product-details.detailsOf` (M4)؛ قیمت همان
 *             `priceOf` (قیمتِ آنلاین، وگرنه قیمتِ فروشِ دفتر)؛ ویژگی فقط «مشخصات»ِ عمومیِ ویترین
 *             (`storefront_products.specs`، WEB-08) — ویژگی‌های variant فقط با `market.product_details` عمومی‌اند
 *             و این‌جا به کار نمی‌روند؛ موجودی همان `catalog.publicStock` (پرچمِ `shop.catalog`).
 *   SQL ───── هر مقدار فقط پارامتر (`$n`)؛ همان صفحه‌بندیِ کلیدیِ نام (نشانگر از آخرین ردیفِ خوانده‌شده،
 *             نه آخرین کارتِ ماندهٔ پس از پالایش) ⇒ هیچ کالایی میانِ دو صفحه گم یا تکرار نمی‌شود.
 *   موجودی ── «فقط موجود» = `in_stock` یا `in_warehouse` ِ همان نشانِ کارت. پیش‌پالایه در SQL (کالای فعالِ
 *             کاتالوگ + جمعِ ماندهٔ مکان‌های فروختنی > ۰) و پالایهٔ نهایی روی نشانِ خودِ کارت (رزروِ «پیدا
 *             نکردم» ِ جمع‌آور، PCK-14، هم). موجودیِ نامعلوم (پرچمِ `shop.catalog` خاموش یا دستگاهِ کهنه) هرگز
 *             «موجود» نیست ⇒ هیچ کالا؛ پس `facets.inStock` همان «این پالایه معنا دارد؟» است.
 */
const { many, now } = require('../db');
const orders = require('./orders');
const { stripControl } = require('./validate');
const { badRequest } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.filters';
const MAX_PRICE_MINOR = 100_000_000_000;   //  همان سقفِ قیمتِ `storefront.js`
const MAX_BRANDS = 50;
const MAX_ATTR_NAMES = 10;
const MAX_ATTR_VALUES = 20;
const SELLABLE = Object.freeze(['in_stock', 'in_warehouse']);
const KEYS = Object.freeze(['brand', 'minPriceMinor', 'maxPriceMinor', 'inStock', 'attrName', 'attrValue']);

/** برندِ کالا در SQL (`sp`، `r`): ردیفِ کاتالوگ اگر هست، وگرنه دادهٔ همگام‌سازی — همان `detailsOf` */
const BRAND_SQL = `btrim(COALESCE((SELECT cp.brand FROM catalog_products cp
    WHERE cp.shop_id = sp.shop_id AND cp.product_id = sp.product_id), r.data->>'brand', ''))`;
/** قیمتِ کالا در SQL: همان `priceOf` (قیمتِ آنلاین، وگرنه قیمتِ فروشِ دفتر × ۱۰۰؛ نادرست ⇒ NULL) */
const PRICE_SQL = `COALESCE(sp.online_price_minor, CASE WHEN (r.data->>'salePrice') ~ '^[0-9]{1,12}(\\.[0-9]+)?$'
    THEN round((r.data->>'salePrice')::numeric * 100) END)`;
const SPECS_SQL = "CASE WHEN jsonb_typeof(sp.specs) = 'array' THEN sp.specs ELSE '[]'::jsonb END";

const flagOn = (shopId) => orders.flagOf(shopId, FLAG_KEY);
const bad = (m) => badRequest(m, 'bad_filter');

function textOf(q, key, max) {
  const v = q[key];
  if (v === undefined) return '';
  if (typeof v !== 'string') throw bad(`«${key}» یک بار و متن`);
  const s = stripControl(v.trim());
  if (s.length > max) throw bad(`«${key}» حداکثر ${max} نویسه`);
  return s;
}

function priceOf(q, key) {
  const v = q[key];
  if (v === undefined || v === '') return null;
  if (typeof v !== 'string' || !/^[0-9]{1,12}$/.test(v)) throw bad(`«${key}» عددِ صحیحِ نامنفی (یک‌صدمِ افغانی)`);
  const n = Number(v);
  if (n > MAX_PRICE_MINOR) throw bad(`«${key}» بیش از سقفِ قیمت`);
  return n;
}

function boolOf(q, key) {
  const v = q[key];
  if (v === undefined || v === '' || v === '0' || v === 'false') return false;
  if (v === '1' || v === 'true') return true;
  throw bad(`«${key}» فقط 1 یا 0`);
}

/** پرس‌وجوی ناب و سنجیده (بی پایگاه) ⇒ { brand, minPrice, maxPrice, inStock, attr } — یا null اگر هیچ پالایه‌ای نیست. */
function validate(query) {
  const q = query && typeof query === 'object' ? query : {};
  if (!KEYS.some((k) => q[k] !== undefined && q[k] !== '')) return null;
  const brand = textOf(q, 'brand', 80);
  const minPrice = priceOf(q, 'minPriceMinor');
  const maxPrice = priceOf(q, 'maxPriceMinor');
  if (minPrice !== null && maxPrice !== null && minPrice > maxPrice) throw badRequest('کمینهٔ قیمت بیش از بیشینه است', 'bad_price_range');
  const name = textOf(q, 'attrName', 40);
  const value = textOf(q, 'attrValue', 120);
  if (Boolean(name) !== Boolean(value)) throw bad('«attrName» و «attrValue» با هم');
  const inStock = boolOf(q, 'inStock');
  const f = { brand, minPrice, maxPrice, inStock, attr: name ? { name, value } : null };
  return f.brand || f.minPrice !== null || f.maxPrice !== null || f.inStock || f.attr ? f : null;
}

/** شرطِ «فقط موجود» در SQL — یا `AND FALSE` وقتی موجودی نامعلوم است (هرگز «موجود» ِ ساختگی). */
async function stockWhere(shopId, params) {
  const stock = require('./stock');
  const catalog = require('./catalog');
  if (!(await stock.flagOf(shopId))) return ' AND FALSE';
  const at = now();
  await catalog.refresh(shopId, { maxAgeMs: 5000, at });
  const settings = await stock.settingsOf(shopId);
  if (!(await catalog.freshness(shopId, settings, at)).fresh) return ' AND FALSE';
  params.push(settings.sellableLocations);
  return ` AND EXISTS (SELECT 1 FROM catalog_products cpx WHERE cpx.shop_id = sp.shop_id AND cpx.product_id = sp.product_id
      AND cpx.active AND NOT cpx.deleted)
    AND (SELECT COALESCE(SUM(sl.on_hand_milli), 0) FROM stock_levels sl WHERE sl.shop_id = sp.shop_id AND sl.branch_id = ''
      AND sl.product_id = sp.product_id AND sl.location = ANY($${params.length}::text[])) > 0`;
}

/** پالایهٔ نهایی روی کارت‌ها (پس از نشانِ موجودی): قیمتِ دقیقِ `priceOf` و نشانِ «موجود» ِ همان کارت. */
const keepOf = (f) => (items) => items.filter((p) => (f.minPrice === null || p.priceMinor >= f.minPrice)
  && (f.maxPrice === null || p.priceMinor <= f.maxPrice) && (!f.inStock || (p.stock && SELLABLE.includes(p.stock.status))));

/**
 * درِ `catalog` ِ `storefront.js`: پرچم خاموش یا بی پالایه ⇒ null (همان پرس‌وجو و همان پاسخ)؛ وگرنه
 * { where(shopId, params) ⇒ پارهٔ SQL با پارامترهای افزوده، keep(items) ⇒ کارت‌های مانده }.
 */
async function parse(shopId, query) {
  if (!(await flagOn(shopId))) return null;
  const f = validate(query);
  if (!f) return null;
  return {
    ...f,
    keep: keepOf(f),
    where: async (sid, params) => {
      let sql = '';
      if (f.brand) sql += ` AND lower(${BRAND_SQL}) = lower($${params.push(f.brand)})`;
      if (f.minPrice !== null) sql += ` AND ${PRICE_SQL} >= $${params.push(f.minPrice)}`;
      if (f.maxPrice !== null) sql += ` AND ${PRICE_SQL} <= $${params.push(f.maxPrice)}`;
      if (f.attr) sql += ` AND sp.specs @> $${params.push(JSON.stringify([f.attr]))}::jsonb`;
      if (f.inStock) sql += await stockWhere(sid, params);
      return sql;
    },
  };
}

/**
 * گزینه‌های فرمِ فیلتر برای همین ویترین — فقط از کالای منتشرشده، زنده و بی جریمه (همان `productFrom`):
 * برندها (≤۵۰)، بازهٔ قیمت (یا null)، مشخصه‌ها (≤۱۰ نام × ≤۲۰ مقدار) و «فقط موجود» معنا دارد؟
 */
async function facets(shopId) {
  const sf = require('./storefront');
  const from = await sf.productFrom(shopId);
  const base = `sp.shop_id = $1 AND sp.published AND ${sf.LIVE_PRODUCT}`;
  const [brands, price, specs, stockOn] = await Promise.all([
    many(`SELECT DISTINCT ${BRAND_SQL} AS b FROM ${from} WHERE ${base} AND ${BRAND_SQL} <> '' ORDER BY 1 LIMIT ${MAX_BRANDS}`, [shopId]),
    many(`SELECT MIN(${PRICE_SQL})::bigint AS lo, MAX(${PRICE_SQL})::bigint AS hi FROM ${from} WHERE ${base} AND ${PRICE_SQL} > 0`, [shopId]),
    many(`SELECT s->>'name' AS n, s->>'value' AS v FROM ${from} CROSS JOIN LATERAL jsonb_array_elements(${SPECS_SQL}) s
      WHERE ${base} AND COALESCE(s->>'name', '') <> '' AND COALESCE(s->>'value', '') <> ''
      GROUP BY 1, 2 ORDER BY 1, 2 LIMIT ${MAX_ATTR_NAMES * MAX_ATTR_VALUES * 4}`, [shopId]),
    require('./stock').flagOf(shopId),
  ]);
  const attrs = new Map();
  for (const r of specs) {
    if (!attrs.has(r.n)) { if (attrs.size >= MAX_ATTR_NAMES) continue; attrs.set(r.n, []); }
    const list = attrs.get(r.n);
    if (list.length < MAX_ATTR_VALUES) list.push(r.v);
  }
  const p = price[0] || {};
  return {
    brands: brands.map((r) => r.b),
    price: p.lo === null || p.lo === undefined ? null : { minMinor: Number(p.lo), maxMinor: Number(p.hi) },
    attributes: [...attrs].map(([name, values]) => ({ name, values })),
    inStock: stockOn === true,
  };
}

module.exports = { FLAG_KEY, MAX_BRANDS, MAX_ATTR_NAMES, MAX_ATTR_VALUES, SELLABLE, flagOn, validate, parse, facets, BRAND_SQL, PRICE_SQL };
