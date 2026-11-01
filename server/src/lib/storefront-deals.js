'use strict';
/**
 * WEB-02 — «پیشنهادها» ِ صفحهٔ اصلیِ ویترین: همان «ارزان‌تر از قیمتِ دکان» ِ
 * بازارچه (`lib/market.js`، `loadDeals`). قیمتِ آنلاین کمتر از قیمتِ فروشِ دفتر،
 * و ⛔ فقط وقتی صاحبِ دکان `showDeals` را روشن کرده (پیش‌فرض خاموش ⇒ قیمتِ دفتر
 * هرگز بیرون نمی‌رود). از `storefront.js` جدا شد تا آن فایل از سقفِ اندازه نگذرد.
 */

/** ردیف‌های نامزد: `product_id` و `regular_minor` (قیمتِ دفتر به واحدِ خُرد). */
function dealRows(many, { PRODUCT_FROM, LIVE_PRODUCT }, shopId) {
  return many(
    `SELECT sp.product_id, ROUND((r.data->>'salePrice')::numeric * 100)::bigint AS regular_minor
       FROM ${PRODUCT_FROM}
       JOIN market_listings ml ON ml.shop_id = sp.shop_id AND ml.show_deals
      WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND sp.online_price_minor IS NOT NULL
        AND (r.data->>'salePrice') ~ '^[0-9]{1,12}([.][0-9]{1,4})?$'
        AND sp.online_price_minor < ROUND((r.data->>'salePrice')::numeric * 100)
      ORDER BY sp.online_price_minor::numeric / ROUND((r.data->>'salePrice')::numeric * 100), sp.product_id LIMIT 40`,
    [shopId]
  );
}

/** قیمتِ امروزِ همان پاسخ دوباره سنجیده می‌شود: «تخفیف» فقط وقتی هنوز ارزان‌تر است. */
function withRegular(offered, regular) {
  return offered.filter((p) => regular.get(p.id) > p.priceMinor).map((p) => ({ ...p, regularPriceMinor: regular.get(p.id) }));
}

/** فهرستِ «پیشنهادها»: نامزدها ⇐ همان قفسهٔ `shelfOf` ِ ویترین ⇐ سنجشِ دوباره با قیمتِ دفتر. */
async function deals(many, sql, shopId, shelfOf) {
  const regular = new Map((await dealRows(many, sql, shopId)).map((r) => [r.product_id, Number(r.regular_minor)]));
  return withRegular(await shelfOf([...regular.keys()]), regular);
}

module.exports = { dealRows, withRegular, deals };
