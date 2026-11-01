'use strict';
/**
 * فقط بخشِ کالای کاتالوگ — برای ویترین و بازار (S1 ِ قراردادِ بازار)، مستقل از `shop.catalog`.
 * از `lib/catalog.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ نوشتنِ هر ردیف همان `upsertProduct` ِ آن‌جاست.
 *
 * ویترینِ دکانی که پرچمِ `storefront.catalog_source` دارد از `catalog_products` می‌خواند؛
 * این‌جا فقط ردیف‌های `products` ِ دفترِ دستگاه‌ها در همان جدول نوشته می‌شود — نه موجودی،
 * نه `catalog_sync_state` (که آبراههٔ موجودی است و اگر جلو برود، روشن کردنِ بعدیِ
 * `shop.catalog` ورودها و فروش‌های پیشین را نمی‌خواند). آبراهه = بیشترین `source_seq` ِ
 * کالاهای همین منبع؛ نوشتن زیرِ همان قفلِ `ingestTx` ⇒ دو خواننده دادهٔ کهنه را روی تازه نمی‌نشانند.
 */
const { one, now, tx } = require('../db');
const stock = require('./stock');
const catalog = require('./catalog');

const num = (v) => (v === null || v === undefined ? 0 : Number(v));

/**
 * منبعِ کالاهای دکان: انتخابِ صریحِ صاحب دکان (`catalog_settings.sync_source`) اگر هست؛
 * وگرنه با `shop.catalog` روشن همان پیش‌فرضِ `ingestTx` (v1)؛ وگرنه v3 اگر دستگاه‌های
 * دکان کالا در دفترِ v3 دارند (کوچ‌کرده؛ پلِ v1 ⇒ v3 کالای v1 را هم آن‌جا می‌آورد)، وگرنه v1.
 */
async function productSourceOf(shopId) {
  const s = await one('SELECT sync_source FROM catalog_settings WHERE shop_id=$1', [shopId]);
  if (s) return s.sync_source === 'v3' ? 'v3' : 'v1';
  if (await stock.flagOf(shopId)) return 'v1';
  const v3 = await one(
    `SELECT 1 AS x FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' LIMIT 1`, [shopId]
  );
  return v3 ? 'v3' : 'v1';
}

const productChecks = new Map();

/**
 * کالاهای تازهٔ دفترِ دستگاه‌ها ⇒ `catalog_products` (فقط کالا). `throttle` (خواندنِ ویترین/بازار)
 * ⇒ برای هر دکان حداکثر هر [REFRESH_THROTTLE_MS] یک پرسشِ «ردیفِ تازه؟»؛ ثبتِ سفارش بی مهلت.
 * ⇒ { products, source } یا { skipped }
 */
async function refreshProducts(shopId, { at = now(), throttle = true } = {}) {
  const { TABLES, PAGE, REFRESH_THROTTLE_MS, upsertProduct } = catalog;
  if (throttle) {
    const t = Date.now();
    if (t - (productChecks.get(shopId) || 0) < REFRESH_THROTTLE_MS) return { skipped: true, throttled: true };
    productChecks.set(shopId, t);
    if (productChecks.size > 10_000) productChecks.clear();
  }
  const fmt = await productSourceOf(shopId);
  const T = TABLES[fmt];
  const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
  const WM = "COALESCE((SELECT MAX(source_seq) FROM catalog_products WHERE shop_id=$1 AND source=$2), 0)";
  const newer = await one(
    `SELECT 1 AS x FROM ${T} WHERE ${ACC} AND table_name='products' AND updated_seq > ${WM} LIMIT 1`, [shopId, fmt]
  );
  if (!newer) return { skipped: true, source: fmt };
  return tx(async (c) => {
    await stock.lock(c, shopId);
    let wm = num((await c.query(`SELECT ${WM} AS wm`, [shopId, fmt])).rows[0].wm);
    let products = 0;
    for (;;) {
      const page = (await c.query(
        `SELECT row_id, data, deleted_at, updated_seq FROM ${T}
          WHERE ${ACC} AND table_name='products' AND updated_seq > $2
          ORDER BY updated_seq, row_id LIMIT $3`,
        [shopId, wm, PAGE]
      )).rows;
      if (!page.length) break;
      let rows = page;
      if (page.length === PAGE) {
        //  مرزِ صفحه وسطِ یک شماره نیفتد (همان قاعدهٔ `ingestTx`)
        const last = num(page[page.length - 1].updated_seq);
        const cut = page.filter((r) => num(r.updated_seq) < last);
        if (cut.length) rows = cut;
      }
      for (const r of rows) { await upsertProduct(c, shopId, r, fmt, at); products += 1; }
      wm = num(rows[rows.length - 1].updated_seq);
      if (page.length < PAGE) break;
    }
    return { products, source: fmt };
  });
}

module.exports = { productSourceOf, refreshProducts };
