'use strict';
/**
 * کالا در جمع‌آوری (PCK): بارکد و بودن — از `lib/order-picking.js` جدا شد تا زیرِ سقفِ اندازه بماند؛
 * رفتار بایت‌به‌بایت همان است.
 */
const { asJson } = require('./delivery-input');


function barcodesOfData(d) {
  const list = Array.isArray(d.barcodes) ? d.barcodes : (typeof d.barcode === 'string' ? [d.barcode] : []);
  return list.filter((b) => typeof b === 'string' && b.trim()).slice(0, 20);
}

/**
 * بارکدهای امروزِ کالا: کاتالوگِ سرور اگر هست، وگرنه دفترِ همگام‌سازی (v1، سپس v3).
 * ⇒ `null` اگر کالا هیچ‌جا نیست؛ `[]` اگر هست و بارکد ندارد.
 */
async function barcodesOf(run, shopId, productId) {
  if (!productId) return null;
  const cat = (await run(
    'SELECT barcodes FROM catalog_products WHERE shop_id=$1 AND product_id=$2 AND NOT deleted', [shopId, productId]
  )).rows[0];
  if (cat) return barcodesOfData({ barcodes: asJson(cat.barcodes) });
  for (const table of ['sync_rows', 'sync_v3_rows']) {
    const r = (await run(
      `SELECT data FROM ${table} WHERE app='shop' AND account_kind='shop' AND account_id=$1
          AND table_name='products' AND row_id=$2 AND deleted_at IS NULL`, [shopId, productId]
    )).rows[0];
    if (r) return barcodesOfData(asJson(r.data));
  }
  return null;
}

module.exports = { barcodesOfData, barcodesOf };
