'use strict';
/**
 * ویترین — جست‌وجوی پیش‌بینی‌گر و «چیزی پیدا نشد» ِ مفید (فاز ۴؛ WEB-05، WEB-06، §۳.۱۳).
 *
 * پشتِ پرچمِ `shop.storefront.search` (دکان / `all`، پیش‌فرض خاموش). خاموش ⇒ پاسخِ
 * `GET /api/store/:slug/products` بایت‌به‌بایت همان قبلی (`storefront.catalog`).
 *
 *   رقم ─── ۰–۹ ِ فارسی و ٠–٩ ِ عربی (و «٫»/«٬») در پرس‌وجو و نامِ کالا هر دو لاتین
 *           می‌شوند؛ همان قاعدهٔ `text-core.asciiDigits` (§۳.۱۳). متنِ ذخیره‌شده دست نمی‌خورد.
 *   دسته ── پرس‌وجویی که با کلیدِ مقایسه همان نامِ یکی از دسته‌های زندهٔ دکان است ⇒ نتیجه
 *           همان دسته است و `jump.category` می‌گوید کدام.
 *   غلط ─── بی نتیجهٔ دقیق (فقط صفحهٔ اول) ⇒ حداکثر `FUZZY_SCAN` کالای زندهٔ همین دکان با
 *           بودجهٔ غلطِ تایپیِ هستهٔ مشترکِ متن (`fuzzyMatches`: ≤۳ حرف هیچ، ۴–۷ یکی، ۸+ دو)؛
 *           `match: 'fuzzy'`. سقفِ اسکن ثابت است تا پرس‌وجوی پرغلط سرور را نخورد.
 *   هیچ ─── `match: 'none'` و `suggest`: دسته‌های نزدیک، پرفروش/تازهٔ واقعی (همان
 *           `highlights` با k-گمنامی؛ ناموجود هرگز) و تلفنِ تماسِ خودِ ویترین.
 *
 * ⛔ مترادف این‌جا نیست: فرهنگِ مترادف باید از پنل بیاید (§۳.۱۳، POS-04) و هنوز ساخته
 *    نشده؛ هیچ فرهنگی در کد ثابت نوشته نمی‌شود.
 */
const T = require('./text-core');

const FLAG = 'shop.storefront.search';
const FUZZY_SCAN = 1000;
const SUGGEST_CATEGORIES = 5;
//  همان نگاشتِ `text-core.asciiDigits`، در SQL
const DIGITS_FROM = '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩٫٬';
const DIGITS_TO = '01234567890123456789.,';

function enabled(shopId) {
  return require('./orders').flagOf(String(shopId), FLAG);
}

/** نامِ نرمال‌شدهٔ کالا (`sqlName` ِ ویترین) با رقمِ لاتین. */
const sqlName = (base) => `translate(${base}, '${DIGITS_FROM}', '${DIGITS_TO}')`;

/** پرس‌وجوی نرمال‌شدهٔ ویترین با رقمِ لاتین. */
const needle = (normalized) => T.asciiDigits(normalized);

/** دسته‌ای که پرس‌وجو دقیقاً همان است (کلیدِ مقایسه) — یا ''. */
function categoryOf(q, categories) {
  const key = T.compareKey(q);
  if (!key) return '';
  return categories.find((c) => T.compareKey(c) === key) || '';
}

/** آیا پرس‌وجو اصلاً جای غلطِ تایپی دارد (دست‌کم یک کلمهٔ ۴حرفی). */
function fuzzyWorthy(q) {
  return T.searchKey(q).split(' ').some((w) => T.typoBudget(w.length) > 0);
}

/** از میانِ ردیف‌های `{ name }`، آن‌هایی که با بودجهٔ غلط جورند — به همان ترتیب. */
function fuzzyPick(rows, q, limit) {
  const out = [];
  for (const r of rows) {
    if (out.length >= limit) break;
    if (T.fuzzyMatches(r.name, q)) out.push(r);
  }
  return out;
}

/** دسته‌های نزدیک به پرس‌وجو (کلمه‌ای مشترک یا در بودجهٔ غلط). */
function nearCategories(q, categories) {
  return categories.filter((c) => T.matches(c, q) || T.fuzzyMatches(c, q) || T.matches(q, c))
    .slice(0, SUGGEST_CATEGORIES);
}

module.exports = {
  FLAG, FUZZY_SCAN, enabled, sqlName, needle, categoryOf, fuzzyWorthy, fuzzyPick, nearCategories,
};
