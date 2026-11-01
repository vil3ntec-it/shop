'use strict';
/**
 * M4 (قراردادِ دکان ⇐ بازار، بخشِ ۳) — جزئیاتِ کالا در نمای عمومی:
 * برند، بارکد، زیردسته، واحدِ فروش و ضریب، variant، `bankProductId` و دستهٔ سراسری.
 *
 *   روشن ── پرچمِ `market.product_details` با scope = `all` (پیش‌فرض خاموش).
 *           خاموش ⇒ نمای کالا، جست‌وجوی بازار و تنظیمِ دکان همان شکلِ امروز.
 *   منبع ── کاتالوگِ واحد (`catalog_products`، ۰۸۲) اگر ردیفِ کالا آن‌جا هست؛
 *           وگرنه همان ردیفِ همگام‌سازی که کاتالوگ از آن ساخته می‌شود و با همان
 *           یکدست‌سازی (`catalog.barcodesOf`) — تا پیش از S1 که کاتالوگ فقط با
 *           `shop.catalog` پر می‌شود، فیلدها خالی نمانند.
 *   بانک ── `bankProductId` فقط از انتخابِ عکسِ مرکزیِ خودِ دکان
 *           (`shop_product_images`)؛ آن انتخاب فقط با تطبیقِ قطعی یا تأییدِ کارمندِ
 *           دکان ثبت می‌شود ⇒ «همین کالا» ادعای خودِ دکان است، نه حدسِ سرور.
 *   دسته ── ⛔ درخت ساخته نمی‌شود (پرسشِ بازِ §۶ِ قرارداد): دستهٔ آزادِ هر کالا با
 *           نگاشتِ خودِ صاحب دکان (`market_listings.category_map`) به یکی از
 *           کلیدهای ثابتِ دستهٔ دکان‌ها؛ بی نگاشت ⇒ دستهٔ خودِ دکان در بازار.
 *   حریم ── هیچ قیمتِ خرید، موجودیِ دقیق، یادداشت یا شناسهٔ داخلیِ دیگری نیست.
 */
const { one, many } = require('../db');
const { stripControl } = require('./validate');
const { toMilli } = require('./sync-v3-convert');

const FLAG_KEY = 'market.product_details';
const BANK_ID_RE = /^[A-Za-z0-9_-]{1,40}$/;
const MAX_MAP = 60;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const text = (v, max) => stripControl(str(v)).slice(0, max);
const decode = (v) => {
  if (v === null || v === undefined) return null;
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
};
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const pairKey = (shopId, id) => `${shopId}\u0000${id}`;

async function on() {
  const r = await one("SELECT value FROM live_config WHERE key = $1 AND scope = 'all'", [FLAG_KEY]);
  if (!r) return false;
  try { return JSON.parse(r.value) === true; } catch { return false; }
}

/** رقمِ فارسی/عربی ⇒ لاتین؛ فقط رقم ⇒ بارکد، وگرنه ''. */
function barcodeOf(q) {
  const s = String(q || '').replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660)).replace(/[\s-]/g, '');
  return /^[0-9]{6,20}$/.test(s) ? s : '';
}

/** ویژگی‌های variant: فقط متن ⇒ متن، حداکثر ۶. */
function attrsOf(v) {
  const out = {};
  for (const [k, x] of Object.entries(obj(decode(v))).slice(0, 6)) {
    const key = text(k, 20);
    const val = typeof x === 'number' && Number.isFinite(x) ? String(x) : text(x, 40);
    if (key && val) out[key] = val;
  }
  return out;
}

//  ستون‌های کاتالوگ و بانک کنارِ `PRODUCT_FROM` ِ ویترین (نام‌های sp و r)
const JOINS = `LEFT JOIN catalog_products cp ON cp.shop_id = sp.shop_id AND cp.product_id = sp.product_id
  LEFT JOIN shop_product_images spi ON spi.shop_id = sp.shop_id AND spi.product_id = sp.product_id`;
const COLS = `cp.product_id AS cp_id, cp.brand AS cp_brand, cp.barcodes AS cp_barcodes, cp.subcategory AS cp_sub,
  cp.sale_unit AS cp_sale_unit, cp.unit_factor_milli AS cp_factor, cp.variant AS cp_variant, cp.parent_id AS cp_parent,
  COALESCE(spi.bank_product_id, '') AS bank_product_id`;

/** جزئیاتِ یک ردیف (`r.data` + COLS): کاتالوگ اگر هست، وگرنه همان دادهٔ همگام‌سازی. */
function detailsOf(row) {
  const { barcodesOf } = require('./catalog');
  const d = obj(decode(row.data));
  const inCatalog = row.cp_id !== null && row.cp_id !== undefined;
  const factorRaw = inCatalog ? Number(row.cp_factor) : toMilli(d.unitFactor);
  const factor = Number.isSafeInteger(factorRaw) && factorRaw > 0 ? factorRaw : 1000;
  const saleUnit = inCatalog ? text(row.cp_sale_unit, 30) : text(d.saleUnit, 30);
  const attrs = attrsOf(inCatalog ? row.cp_variant : d.variant);
  const parentId = inCatalog ? text(row.cp_parent, 80) : text(d.parentId, 80);
  const bank = str(row.bank_product_id);
  return {
    brand: inCatalog ? text(row.cp_brand, 80) : text(d.brand, 80),
    barcodes: inCatalog ? barcodesOf({ barcodes: decode(row.cp_barcodes) }) : barcodesOf(d),
    subcategory: inCatalog ? text(row.cp_sub, 80) : text(d.subcategory, 80),
    //  یک «واحدِ فروش» = factorMilli/1000 «واحد» (INV-10)
    saleUnit: saleUnit ? { unit: saleUnit, factorMilli: factor } : null,
    variant: parentId || Object.keys(attrs).length ? { parentId, attrs } : null,
    bankProductId: BANK_ID_RE.test(bank) ? bank : '',
  };
}

/** نگاشتِ ذخیره‌شده ⇒ Map(دستهٔ یکدست‌شده ⇒ کلید). */
function mapOf(raw) {
  return new Map(Object.entries(obj(decode(raw))).filter(([, v]) => typeof v === 'string' && v));
}

/** دستهٔ سراسری: نگاشتِ صاحب دکان، وگرنه دستهٔ خودِ دکان در بازار، وگرنه ''. */
function globalCategoryOf(category, map, shopCategory) {
  const { normalize } = require('./storefront');
  return map.get(normalize(category)) || shopCategory || '';
}

/**
 * جزئیاتِ چند کالا از چند دکان — Map(`shop\0id` ⇒ جزئیات + `globalCategory`).
 * فقط کالای منتشرشده، زنده و بی جریمه (همان `PRODUCT_FROM`/`LIVE_PRODUCT`).
 */
async function load(pairs) {
  const sf = require('./storefront');
  const uniq = [...new Map(pairs.map((x) => [pairKey(x.shopId, x.id), x])).values()];
  const out = new Map();
  if (!uniq.length) return out;
  const rows = await many(
    `SELECT sp.shop_id, sp.product_id AS id, r.data, ${COLS},
            COALESCE(ml.category, '') AS shop_category, COALESCE(ml.category_map, '{}'::jsonb) AS category_map
       FROM ${sf.PRODUCT_FROM} ${JOINS}
       LEFT JOIN market_listings ml ON ml.shop_id = sp.shop_id
      WHERE (sp.shop_id, sp.product_id) IN (SELECT * FROM unnest($1::text[], $2::text[]))
        AND sp.published AND ${sf.LIVE_PRODUCT}`,
    [uniq.map((x) => x.shopId), uniq.map((x) => x.id)]
  );
  for (const r of rows) {
    const d = obj(decode(r.data));
    out.set(pairKey(r.shop_id, r.id), {
      ...detailsOf(r),
      globalCategory: globalCategoryOf(text(d.category, 80), mapOf(r.category_map), r.shop_category),
    });
  }
  return out;
}

/** جزئیات را روی نماهای یک دکان می‌نشاند (درجا) — فقط با پرچمِ روشن. */
async function attach(shopId, items) {
  if (!items.length || !(await on())) return items;
  const by = await load(items.map((p) => ({ shopId, id: p.id })));
  for (const p of items) Object.assign(p, by.get(pairKey(shopId, p.id)) || {});
  return items;
}

/** ورودیِ `categoryMap` ِ دکان ⇒ شیءِ ذخیره (کلیدِ یکدست‌شده ⇒ کلیدِ ثابت). '' ⇒ برداشتن. */
function parseCategoryMap(raw, current, keys, badRequest) {
  const src = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : null;
  if (!src) throw badRequest('نگاشتِ دسته باید فهرستِ «دسته ⇐ دستهٔ بازار» باشد', 'bad_category_map');
  const { normalize } = require('./storefront');
  const out = { ...obj(decode(current)) };
  for (const [k, v] of Object.entries(src)) {
    const key = normalize(text(k, 80));
    if (!key) continue;
    const val = str(v);
    if (val && !keys.has(val)) throw badRequest('دستهٔ بازار معتبر نیست', 'bad_category');
    if (val) out[key] = val; else delete out[key];
  }
  if (Object.keys(out).length > MAX_MAP) throw badRequest(`حداکثر ${MAX_MAP} نگاشتِ دسته`, 'category_map_too_large');
  return out;
}

module.exports = {
  FLAG_KEY, BANK_ID_RE, JOINS, COLS,
  on, barcodeOf, detailsOf, load, attach, globalCategoryOf, mapOf, parseCategoryMap, pairKey,
};
