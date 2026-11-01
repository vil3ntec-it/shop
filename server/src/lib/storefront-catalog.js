'use strict';
/**
 * کالای ویترین از کاتالوگِ واحد (S1) و انتشارِ گروهی (M2) ِ قراردادِ بازار — از `lib/storefront.js` جدا شد
 * تا زیرِ سقفِ اندازه بماند؛ رفتار همان. وابستگی‌های همان پرونده (دروازهٔ کالا، سنجنده‌ها، پایگاه‌داده)
 * تزریق می‌شوند.
 */
const { many, now, tx } = require('../db');
const orders = require('./orders');
const idempotency = require('./idempotency');
const { badRequest } = require('../middleware/errors');

module.exports = ({
  SHOP_ROWS, PRODUCT_FROM, NOT_HIDDEN, PRODUCT_ID_RE, MAX_PRICE_MINOR, MAX_QTY_MILLI, obj, int, bool, text, isActive, priceOf,
}) => {
/*
 *  S1 ِ قراردادِ بازار — کاتالوگِ واحد به‌جای `sync_rows`: پرچمِ `storefront.catalog_source`
 *  فقط با scope = `all` (پیش‌فرض خاموش؛ یک منبع برای همهٔ دکان‌ها، تا ویترین و جست‌وجوی
 *  چنددکانیِ بازار هرگز دو منبع نخوانند). روشن ⇒ کالا از `catalog_products` که از دفترِ v1
 *  یا v3 ِ همان دکان پر می‌شود (`catalog-products.refreshProducts`، مستقل از `shop.catalog`).
 *  `r` همان شکلِ ردیفِ `sync_rows` را دارد (`account_id`، `row_id`، `data`، `deleted_at`) ⇒
 *  `LIVE_PRODUCT`، `SQL_NAME`، `PRODUCT_COLS` و `productView` و قاعدهٔ «زنده» یکی می‌مانند.
 *  `salePrice` = `price_minor / 100` (دقیق، دو رقم) ⇒ `priceOf` همان عدد را برمی‌گرداند.
 */
const CATALOG_FLAG = 'storefront.catalog_source';
const CATALOG_ROWS = `(SELECT cp.shop_id AS account_id, cp.product_id AS row_id,
    jsonb_build_object('name', cp.name, 'unit', cp.unit, 'category', cp.category,
      'salePrice', ROUND(cp.price_minor::numeric / 100, 2), 'active', cp.active) AS data,
    CASE WHEN cp.deleted THEN cp.updated_at END AS deleted_at
   FROM catalog_products cp)`;
const CATALOG_FROM = `storefront_products sp
  JOIN ${CATALOG_ROWS} r ON r.account_id = sp.shop_id AND r.row_id = sp.product_id
   AND ${NOT_HIDDEN}`;

const catalogOn = () => orders.flagOf('all', CATALOG_FLAG);

/** کاتالوگِ دکان را از دفترِ دستگاه‌ها تازه می‌کند؛ خطای خواندنِ ویترین فقط ثبت می‌شود. */
async function fillCatalog(shopId, { strict = false } = {}) {
  try {
    await require('./catalog-products').refreshProducts(String(shopId), { throttle: !strict });
  } catch (err) {
    //  ثبتِ سفارش با کاتالوگِ کهنه قیمت نمی‌گذارد
    if (strict) throw err;
    console.warn('[storefront] catalog refresh failed', String(shopId), err && err.message);
  }
}

/**
 * جدولِ کالای ویترین (`sp` + `r`) — پرچم خاموش ⇒ همان `PRODUCT_FROM` ِ همیشگی.
 * با شناسهٔ دکان (یا فهرستی از آن‌ها) و پرچمِ روشن، کاتالوگِ همان دکان‌ها پیش از خواندن تازه می‌شود.
 */
async function productFrom(shops = null, { strict = false } = {}) {
  if (!(await catalogOn())) return PRODUCT_FROM;
  for (const id of [].concat(shops || [])) if (id) await fillCatalog(id, { strict });
  return CATALOG_FROM;
}

/**
 * ردیف‌های دفترِ کالا برای فهرستِ کارمند و نوشتنِ ویترین (انتشارِ تکی و گروهی) — از همان منبعی
 * که ویترین می‌خواند: پرچمِ S1 روشن ⇒ کاتالوگِ واحد، که همین‌جا و بیرون از تراکنشِ نوشتن تازه
 * می‌شود (`strict` = بی مهلت و خطا بالا؛ دکانِ v3 هم پیدا می‌شود)؛ وگرنه `sync_rows` ِ همیشگی.
 * `$1` = شناسهٔ دکان. ⇒ { from, where } با نامِ مستعارِ `r` (`row_id`، `data`)؛ حذف‌شده‌ها بیرون.
 */
async function ledgerSource(shopId, { strict = true } = {}) {
  if (!(await catalogOn())) {
    return { from: 'sync_rows r', where: `${SHOP_ROWS} AND r.account_id = $1 AND r.deleted_at IS NULL` };
  }
  await fillCatalog(shopId, { strict });
  return { from: `${CATALOG_ROWS} r`, where: 'r.account_id = $1 AND r.deleted_at IS NULL' };
}

// ════════════════════════════════════════════════════════════════════
//  انتشارِ گروهی (M2 ِ قراردادِ بازار) — پشتِ پرچمِ جدای `shop.storefront.bulk`
// ════════════════════════════════════════════════════════════════════

const BULK_FLAG = 'shop.storefront.bulk';
const MAX_BULK = 500;

/** پرچمِ جدای انتشارِ گروهی؛ پیش‌فرض خاموش. */
function bulkFlagOn(shopId) {
  return orders.flagOf(shopId, BULK_FLAG);
}

/**
 * چرا این کالا در ویترین و بازار دیده نمی‌شود — همان قاعدهٔ «زنده» (`PRODUCT_FROM`
 * + `LIVE_PRODUCT` + قیمت‌دار در `pricer`)، به زبانِ کد. خالی ⇒ دیده می‌شود.
 */
function notLiveReasons({ published, active, priceMinor, hidden }) {
  const out = [];
  if (!published) out.push('not_published');
  if (!active) out.push('inactive');
  if (priceMinor === null || priceMinor === undefined) out.push('no_price');
  if (hidden) out.push('hidden_listing');
  return out;
}

/** جریمه‌های بازِ «پنهان کردنِ کالا» برای همین شناسه‌ها. */
async function hiddenIds(shopId, ids, run = many) {
  if (!ids.length) return new Set();
  const rows = await run(
    `SELECT DISTINCT product_id FROM trust_sanctions
      WHERE shop_id = $1 AND product_id = ANY($2::text[]) AND kind = 'hide_listing' AND lifted_at IS NULL`,
    [shopId, ids]
  );
  return new Set(rows.map((r) => r.product_id));
}

/** فهرستِ «در ویترین» با دلیلِ نیامدن برای هر کالا (فقط وقتی پرچمِ گروهی روشن است). */
async function withReasons(shopId, page) {
  const hidden = await hiddenIds(shopId, page.items.map((p) => p.id));
  return {
    ...page,
    items: page.items.map((p) => ({
      ...p,
      notLive: notLiveReasons({
        published: p.published, active: p.active, hidden: hidden.has(p.id),
        priceMinor: p.onlinePriceMinor ?? p.priceMinor,
      }),
    })),
  };
}

/** بدنهٔ گروهی ⇒ فهرستِ سنجیده؛ هر خطا کلِ درخواست را رد می‌کند (هیچ چیز نیمه نمی‌نشیند). */
function parseBulk(body) {
  const items = obj(body).items;
  if (!Array.isArray(items) || items.length === 0) throw badRequest('فهرستِ کالا خالی است', 'bad_items');
  if (items.length > MAX_BULK) throw badRequest(`حداکثر ${MAX_BULK} کالا در یک درخواست`, 'too_many_items');
  const seen = new Set();
  return items.map((raw) => {
    const b = obj(raw);
    const id = typeof b.productId === 'string' ? b.productId : '';
    if (!PRODUCT_ID_RE.test(id)) throw badRequest('شناسهٔ کالا معتبر نیست', 'bad_product');
    if (seen.has(id)) throw badRequest('یک کالا دو بار در فهرست آمده است', 'duplicate_product');
    seen.add(id);
    //  فقط فیلدهای آمده عوض می‌شوند: «همه را منتشر کن» قیمت و سقفِ پیشین را پاک نمی‌کند
    const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
    const nullable = (k, field, opts) => (b[k] === null ? null : int(b[k], field, opts));
    return {
      productId: id,
      hasPublished: has('published'), published: has('published') ? bool(b.published, 'در ویترین') : false,
      hasOnline: has('onlinePriceMinor'),
      onlinePriceMinor: has('onlinePriceMinor') ? nullable('onlinePriceMinor', 'قیمتِ آنلاین', { min: 0, max: MAX_PRICE_MINOR }) : null,
      hasMax: has('maxQtyMilli'),
      maxQtyMilli: has('maxQtyMilli') ? nullable('maxQtyMilli', 'حداکثرِ هر سفارش', { min: 1, max: MAX_QTY_MILLI }) : null,
      hasDescription: has('description'), description: has('description') ? text(b.description, 2000) : '',
    };
  });
}

/**
 * گذاشتن/برداشتنِ تا ۵۰۰ کالا در یک تراکنش. کالای نبوده در دفتر کنار می‌ماند و در
 * پاسخ `not_found` می‌گیرد؛ بقیه همه با هم می‌نشینند. با کلیدِ یکتایی: همان کلید +
 * همان بدنه ⇒ پاسخِ اول بی هیچ نوشتنِ دوباره؛ بدنهٔ دیگر ⇒ ۴۰۹.
 */
async function setProducts(shopId, body, userId, idem) {
  const items = parseBulk(body);
  const prior = await idempotency.find(idem);
  if (prior) return { ...prior.response, replayed: true };
  //  همان منبعِ ویترین (S1: کاتالوگِ واحد، پیش از تراکنش تازه) — نه همیشه `sync_rows`
  const src = await ledgerSource(shopId);
  const at = now();
  return tx(async (c) => {
    await idempotency.lock(c, idem);
    const again = await idempotency.find(idem, idempotency.viaClient(c));
    if (again) return { ...again.response, replayed: true };
    const ids = items.map((i) => i.productId);
    const rows = (await c.query(
      `SELECT r.row_id AS id, r.data FROM ${src.from} WHERE ${src.where} AND r.row_id = ANY($2::text[])`,
      [shopId, ids]
    )).rows;
    const byId = new Map(rows.map((r) => [r.id, r]));
    const ok = items.filter((i) => byId.has(i.productId));
    if (ok.length) {
      const input = JSON.stringify(ok.map((i) => ({
        product_id: i.productId, has_published: i.hasPublished, published: i.published,
        has_online: i.hasOnline, online: i.onlinePriceMinor, has_max: i.hasMax, max_qty: i.maxQtyMilli,
        has_desc: i.hasDescription, description: i.description,
      })));
      await c.query(
        `INSERT INTO storefront_products (shop_id, product_id, updated_by, updated_at)
         SELECT $1, x.product_id, $3, $4 FROM jsonb_to_recordset($2::jsonb) AS x(product_id text)
         ON CONFLICT (shop_id, product_id) DO NOTHING`,
        [shopId, input, userId || '', at]
      );
      await c.query(
        `UPDATE storefront_products sp SET
           published = CASE WHEN x.has_published THEN x.published ELSE sp.published END,
           online_price_minor = CASE WHEN x.has_online THEN x.online ELSE sp.online_price_minor END,
           max_qty_milli = CASE WHEN x.has_max THEN x.max_qty ELSE sp.max_qty_milli END,
           description = CASE WHEN x.has_desc THEN x.description ELSE sp.description END,
           published_at = COALESCE(sp.published_at,
             CASE WHEN (CASE WHEN x.has_published THEN x.published ELSE sp.published END) THEN $4::bigint END),
           updated_by = $3, updated_at = $4
          FROM jsonb_to_recordset($2::jsonb) AS x(product_id text, has_published boolean, published boolean,
            has_online boolean, online bigint, has_max boolean, max_qty bigint, has_desc boolean, description text)
         WHERE sp.shop_id = $1 AND sp.product_id = x.product_id`,
        [shopId, input, userId || '', at]
      );
    }
    const saved = new Map((await c.query(
      `SELECT product_id, published, online_price_minor, max_qty_milli FROM storefront_products
        WHERE shop_id = $1 AND product_id = ANY($2::text[])`,
      [shopId, ok.map((i) => i.productId)]
    )).rows.map((r) => [r.product_id, r]));
    const hidden = await hiddenIds(shopId, ok.map((i) => i.productId), async (q, p) => (await c.query(q, p)).rows);
    const results = items.map((i) => {
      const r = byId.get(i.productId);
      if (!r) return { productId: i.productId, ok: false, error: 'product_not_found' };
      const s = saved.get(i.productId);
      const onlinePriceMinor = s.online_price_minor === null ? null : Number(s.online_price_minor);
      return {
        productId: i.productId, ok: true, published: s.published === true, onlinePriceMinor,
        maxQtyMilli: s.max_qty_milli === null ? null : Number(s.max_qty_milli),
        notLive: notLiveReasons({
          published: s.published === true, active: isActive(r.data), hidden: hidden.has(i.productId),
          priceMinor: priceOf({ online_price_minor: s.online_price_minor, data: r.data }),
        }),
      };
    });
    const response = {
      results,
      applied: ok.length,
      notFound: items.length - ok.length,
      published: results.filter((r) => r.ok && r.published).length,
    };
    await idempotency.save(c, idem, { tenantId: shopId, resultId: '', response });
    return { ...response, replayed: false };
  });
}

return {
  CATALOG_FLAG, catalogOn, fillCatalog, productFrom, ledgerSource,
  BULK_FLAG, MAX_BULK, bulkFlagOn, notLiveReasons, withReasons, parseBulk, setProducts,
};
};
