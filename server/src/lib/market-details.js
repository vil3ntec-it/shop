'use strict';
/**
 * بازار M4 (پرچمِ `market.product_details`، جزئیات در `lib/product-details.js`) — سمتِ بازارچه: نمایهٔ
 * برند/بارکد/`bank_product_id` در `market_search`، سنجشِ زندهٔ برند و بارکد در جست‌وجو، «همین کالا در
 * دکان‌های دیگر» و نگاشتِ دستهٔ دکان. جدا از `lib/market.js` تا آن زیرِ سقفِ اندازه بماند
 * (`tools/quality`)؛ رفتار همان. کمک‌های همان پرونده (فهرستِ دکان‌ها، کارت، رتبه، …) تزریق می‌شوند.
 */
const { many, query, now } = require('../db');
const sf = require('./storefront');
const details = require('./product-details');
const units = require('./market-units');   //  S70: مقایسه فقط با واحدِ هم‌سنج
const { badRequest, notFound } = require('../middleware/errors');

/** بسته‌های دادهٔ کالا (PRD-04) ⇒ `[{ name, sizeMilli }]` — فقط برای اندازه (S70)، نه پاسخ */
function packsOf(data) {
  let d = data;
  if (typeof d === 'string') { try { d = JSON.parse(d); } catch { return []; } }
  const list = d && typeof d === 'object' && Array.isArray(d.packs) ? d.packs : [];
  return list.filter((k) => k && typeof k === 'object').map((k) => ({ name: k.name, sizeMilli: k.sizeMilli }));
}

module.exports = ({ eligible, cardOf, rank, geoOf, str, CATEGORY_KEYS, MAX_PRODUCTS, MAX_CANDIDATES }) => {
  //  امضای جزئیات: تغییرِ برند/بارکد/variant یا پیوندِ بانک ⇒ همان دکان از نو نمایه می‌شود
  const DETAIL_SIG = `COALESCE(cp.brand, r.data->>'brand', '') || chr(31)
  || COALESCE(cp.barcodes::text, r.data->>'barcodes', r.data->>'barcode', '') || chr(31) || COALESCE(spi.bank_product_id, '')`;

  /** ردیف‌های `market_search` ِ یک دکان، درونِ تراکنشِ `reindex`: برند، بارکد و `bank_product_id` کنارِ نام (همیشه پر؛ خواندنش پشتِ پرچم). */
  async function indexShop(c, shopId, from = sf.PRODUCT_FROM) {
    const rows = (await c.query(
      `SELECT sp.product_id, ${sf.SQL_NAME} AS name_norm, r.data, ${details.COLS} FROM ${from} ${details.JOINS}
        WHERE sp.shop_id = $1 AND sp.published AND ${sf.LIVE_PRODUCT} AND COALESCE(r.data->>'name', '') <> ''`,
      [shopId]
    )).rows;
    const recs = rows.map((r) => {
      const d = details.detailsOf(r);
      return { product_id: r.product_id, name_norm: r.name_norm, brand_norm: sf.normalize(d.brand), barcodes: d.barcodes, bank_product_id: d.bankProductId };
    });
    await c.query(
      `INSERT INTO market_search (shop_id, product_id, name_norm, brand_norm, barcodes, bank_product_id)
       SELECT $1, x.product_id, x.name_norm, x.brand_norm,
              ARRAY(SELECT jsonb_array_elements_text(x.barcodes)), x.bank_product_id
         FROM jsonb_to_recordset($2::jsonb) AS x(product_id text, name_norm text, brand_norm text, barcodes jsonb, bank_product_id text)`,
      [shopId, JSON.stringify(recs)]
    );
  }

  /** دکانِ کنارِ هر کالا در نتیجه‌ها. */
  function shopCard(shop) {
    return {
      slug: shop.slug, title: shop.title, accent: shop.accent,
      badge: shop.status.badge, orderAcceptance: shop.status.orderAcceptance, distanceKm: shop.distanceKm,
    };
  }

  /** یک کالا در نتیجهٔ جست‌وجو و «همین کالا…»: نمای کالا + جزئیاتِ M4 (اگر در `extra` هست) + دکان. */
  const offerView = ({ shop, shopId, p }, extra) => ({
    id: p.id, name: p.name, unit: p.unit, category: p.category, priceMinor: p.priceMinor, photo: p.photo,
    ...(extra.get(details.pairKey(shopId, p.id)) || {}),
    shop: shopCard(shop),
  });

  /** جست‌وجو: پرچمِ M4 و بارکدِ همان عبارت (برند بخشی از نام، بارکد برابرِ کامل). */
  async function searchMode(raw) {
    const more = await details.on();
    return { more, code: more ? details.barcodeOf(raw) : '' };
  }

  /** ⛔ نمایه فقط نامزد است: نام، برند یا بارکد دوباره با دادهٔ زنده. ⇐ `{ found, extra }` (جزئیاتِ هر کالا). */
  async function liveMatch(found, needle, code) {
    const extra = await details.load(found.map((x) => ({ shopId: x.shopId, id: x.p.id })));
    return {
      extra,
      found: found.filter((x) => {
        const d = extra.get(details.pairKey(x.shopId, x.p.id));
        return sf.normalize(x.p.name).includes(needle)
          || (d && ((d.brand && sf.normalize(d.brand).includes(needle)) || (code && d.barcodes.includes(code))));
      }),
    };
  }

  /**
   * «همین کالا در دکان‌های دیگر»: کالاهای زندهٔ دکان‌های در فهرست که صاحبشان همان کالای بانکِ مرکزی
   * (`bank_product_id`) را برایش برگزیده. نامزد از نمایهٔ باریک، بعد سنجشِ زنده (منتشرشده، فعال، بی
   * جریمه، هنوز همان پیوند). پرچمِ `market.product_details` خاموش ⇒ ۴۰۴ (مثلِ نشانیِ ناموجود).
   * S70: هر پیشنهاد `perUnit` (قیمتِ یک دانه/کیلو/لیتر/متر، فقط با اندازهٔ روشن؛ وگرنه null) و `cheapest`
   * (فقط درونِ همان پایه، با دست‌کم دو پیشنهاد)؛ `compare` = گروه‌های هم‌سنج روی همهٔ پیدا‌شده‌ها، نه فقط
   * صفحه. درونِ یک دکان ترتیب با قیمتِ یک پایه است، نه قیمتِ خامِ دو واحدِ ناهم‌سنج (`lib/market-units.js`).
   * واحدِ بی عدد اندازه را از `saleUnit` ِ جزئیات و `packs` ِ دادهٔ کالا می‌گیرد (`units.sizeOfItem`)؛ ⛔ خودِ بسته‌ها
   * (بارکد، قیمتِ جدا) هرگز در پاسخ نیستند.
   */
  async function sameProduct(bankProductId, { lat, lng, limit, exclude } = {}, at = now()) {
    if (!(await details.on())) throw notFound('پیدا نشد', 'not_found');
    const bank = str(bankProductId);
    if (!details.BANK_ID_RE.test(bank)) throw badRequest('شناسهٔ کالای بانک معتبر نیست', 'bad_bank_product');
    const near = geoOf(lat, lng);
    const lim = Math.min(MAX_PRODUCTS, Math.max(1, Number.parseInt(limit, 10) || 24));
    const skip = str(exclude);
    const list = (await eligible(at)).filter((e) => e.row.slug !== skip);
    if (!list.length) return { bankProductId: bank, offers: [], compare: [] };
    const bySlug = new Map(list.map((e) => [e.row.shop_id, cardOf(e, near, at)]));
    const rows = await many(
      `SELECT sp.shop_id, ${sf.PRODUCT_COLS}
         FROM market_search ms JOIN (${await sf.productFrom()}) ON sp.shop_id = ms.shop_id AND sp.product_id = ms.product_id
        WHERE ms.bank_product_id = $1 AND ms.shop_id = ANY($2::text[]) AND sp.published AND ${sf.LIVE_PRODUCT}
        ORDER BY sp.shop_id, r.row_id LIMIT $3`,
      [bank, [...bySlug.keys()], MAX_CANDIDATES]
    );
    const found = rows.map((r) => ({ shop: bySlug.get(r.shop_id), shopId: r.shop_id, p: sf.productView(r), packs: packsOf(r.data) }))
      .filter((x) => x.shop && x.p.priceMinor !== null && x.p.name);
    const extra = await details.load(found.map((x) => ({ shopId: x.shopId, id: x.p.id })));
    const linked = found
      .map((x) => ({ ...x, d: extra.get(details.pairKey(x.shopId, x.p.id)) || {} }))
      .filter((x) => x.d.bankProductId === bank)
      .map((x) => ({ ...x, per: units.perUnit(x.p.unit, x.p.priceMinor, { saleUnit: x.d.saleUnit, packs: x.packs }), priceMinor: x.p.priceMinor }));
    const compare = units.compare(linked.map((x) => x.per));
    const offers = linked
      .sort((a, b) => rank(near)(a.shop, b.shop) || units.order(a, b))
      .slice(0, lim)
      .map((x) => ({ ...offerView(x, extra), perUnit: x.per, cheapest: units.cheapest(x.per, compare) }));
    return { bankProductId: bank, offers, compare };
  }

  /** نگاشتِ دستهٔ آزادِ کالاها به دسته‌های ثابت در نمای «در بازارچه چگونه دیده شوم» — فقط با پرچم. */
  const mapView = (row, withMap) => (withMap ? { categoryMap: Object.fromEntries(details.mapOf(row ? row.category_map : null)) } : {});

  /**
   * `saveListing`: پرچم و `categoryMap` ِ بدنه روی نگاشتِ کنونی. `catMap` = null وقتی نیامده یا پرچم
   * خاموش است — آن‌وقت مثلِ هر فیلدِ ناشناختهٔ دیگر نادیده (همان رفتارِ امروز).
   */
  async function listingInput(b, has, cur) {
    const withMap = await details.on();
    const catMap = withMap && has('categoryMap')
      ? details.parseCategoryMap(b.categoryMap, cur ? cur.category_map : null, CATEGORY_KEYS, badRequest) : null;
    return { withMap, catMap };
  }

  async function saveMap(shopId, map) {
    await query('UPDATE market_listings SET category_map = $2::jsonb WHERE shop_id = $1', [shopId, JSON.stringify(map)]);
    return map;
  }

  return {
    on: details.on, JOINS: details.JOINS, DETAIL_SIG, indexShop, searchMode, liveMatch, offerView, sameProduct,
    mapView, listingInput, saveMap,
  };
};
