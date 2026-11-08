'use strict';
/**
 * کاتالوگِ ویترین: نشانگرِ صفحه، «مرتب‌سازی»، شمارِ کالا و «قیمتِ پیشینِ خط‌خورده» (WEB-02/07؛ ممیزیِ ویترین
 * ۱۴۰۵/۰۸/۱۷، مشکلِ ۹)؛ و نشانی/نقشهٔ دکان در نمای عمومی (مشکلِ ۶، `placeOf`). از `storefront.js` جدا شد تا آن پرونده زیرِ سقفِ ۸۰۰ سطر بماند؛ `catalog` همان‌جاست.
 *
 *   ترتیب ── پیش‌فرض الفبا با نشانگرِ نام (`[name, id]`، همان پیشین)؛ `sort` = `price` (ارزان‌ترین)، `-price`
 *            (گران‌ترین) یا `new` (تازه‌ترین انتشار) با نشانگرِ جای‌گاه (`["o", n]`). قیمتِ ترتیب همان `priceOf`:
 *            آنلاین، وگرنه قیمتِ فروشِ دفتر. مقدارِ ناشناس ⇒ پیش‌فرض (۴۰۰ نه — پیوندِ کهنه نمی‌شکند).
 *   شمار ── «۱۵ کالا» فقط برای صفحهٔ نخستِ دیدن (دسته/فیلتر، نه جست‌وجو — پاسخِ جست‌وجو همان شکلِ پیشین): یک صفحه ⇒
 *            همان شمارِ کارت‌ها؛ بیشتر ⇒ `count(*)` ِ همان شرط؛ با پالایهٔ پسینِ فیلتر (`keep`) شمارِ SQL دقیق نیست ⇒ null.
 *   خط‌خورده ── قیمتِ آنلاینی که صاحب پایین‌تر از قیمتِ فروشِ دکان گذاشته تخفیفِ خودِ همین دکان برای مشتریِ همین
 *            دکان است ⇒ روی کارتِ ویترین بی کلیدِ جدا (پول عوض نمی‌شود). بازارچه (مقایسهٔ دکان‌ها) و ردیفِ
 *            «ارزان‌تر از قیمتِ دکان» همان رضایتِ `show_deals` را می‌خواهند (`storefront-deals.js`).
 */
const { badRequest } = require('../middleware/errors');

const SALE_SQL = "CASE WHEN (r.data->>'salePrice') ~ '^[0-9]{1,12}([.][0-9]{1,4})?$' THEN ROUND((r.data->>'salePrice')::numeric * 100) END";
const SORTS = Object.freeze({
  price: `COALESCE(sp.online_price_minor::numeric, ${SALE_SQL}) ASC NULLS LAST, `,
  '-price': `COALESCE(sp.online_price_minor::numeric, ${SALE_SQL}) DESC NULLS LAST, `,
  new: 'sp.published_at DESC NULLS LAST, ',
});
const MAX_OFFSET = 100000;

module.exports = ({ PRODUCT_ID_RE, MAX_PRICE_MINOR }) => {
  const enc = (x) => Buffer.from(JSON.stringify(x)).toString('base64url');
  function dec(raw, ok) {
    try {
      const x = JSON.parse(Buffer.from(String(raw), 'base64url').toString('utf8'));
      if (!Array.isArray(x) || !ok(x)) throw new Error('x');
      return x;
    } catch {
      throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    }
  }

  /**
   * برنامهٔ یک صفحه: `where` (شرطِ نشانگرِ نام، با پارامترِ افزوده به `params`)، `order` (پیشوندِ ORDER BY)،
   * `offset` (عددِ OFFSET؛ فراخواننده آن را پارامتری می‌فرستد، نه درونِ متنِ SQL) و `next(last)` ⇒ نشانگرِ صفحهٔ بعد.
   */
  function pageOf(sortQ, cursor, params, lim) {
    const sort = Object.prototype.hasOwnProperty.call(SORTS, String(sortQ || '')) ? String(sortQ) : '';
    if (sort) {
      const off = cursor ? dec(cursor, (x) => x[0] === 'o' && Number.isSafeInteger(x[1]) && x[1] > 0 && x[1] <= MAX_OFFSET)[1] : 0;
      return { where: '', order: SORTS[sort], offset: off, next: () => enc(['o', off + lim]) };
    }
    let where = '';
    if (cursor) {
      const [name, id] = dec(cursor, (x) => typeof x[0] === 'string' && typeof x[1] === 'string' && PRODUCT_ID_RE.test(x[1]));
      params.push(name, id);
      where = ` AND (COALESCE(r.data->>'name', ''), r.row_id) > ($${params.length - 1}, $${params.length})`;
    }
    return { where, order: '', offset: 0, next: (last) => enc([String((last.data && last.data.name) || ''), last.id]) };
  }

  /** «قیمتِ پیشینِ خط‌خورده» روی کارت‌ها از ردیف‌های همان صفحه */
  function withWas(rows, items) {
    const byId = new Map(rows.map((r) => [r.id, r]));
    return items.map((p) => {
      const r = byId.get(p.id);
      if (!r || r.online_price_minor === null || r.online_price_minor === undefined) return p;
      const d = typeof r.data === 'string' ? JSON.parse(r.data) : r.data || {};
      const sale = Math.round(Number(d.salePrice) * 100);
      return Number.isSafeInteger(sale) && sale <= MAX_PRICE_MINOR && sale > p.priceMinor ? { ...p, regularPriceMinor: sale } : p;
    });
  }

  /** شمارِ صفحهٔ نخست (شرحِ سرِ پرونده) */
  async function totalOf({ cursor, q, more, items, filters, count }) {
    if (cursor || q) return undefined;
    if (!more) return items.length;
    return filters ? null : Number((await count()).n);
  }

  /**
   * نشانی و «مسیر روی نقشه» ِ دکان (مهاجرتِ ۲۱۵) برای نمای عمومی: `{ place: { text, map } }` یا {} — پیوند از پینِ صاحب،
   * وگرنه جست‌وجوی نام و نشانی. WEB-23: فقط ستون‌های `pickup_*` که صاحب برای مشتری گذاشته، و بی کلیدِ address/lat/lng.
   */
  function placeOf(sf) {
    const text = String(sf.pickup_address || '');
    const pin = Number.isFinite(sf.pickup_lat) && Number.isFinite(sf.pickup_lng) ? `${sf.pickup_lat},${sf.pickup_lng}` : '';
    if (!text && !pin) return {};
    return { place: { text, map: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(pin || `${sf.title} ${text}`)}` } };
  }

  return { pageOf, withWas, totalOf, placeOf };
};
