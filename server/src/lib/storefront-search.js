'use strict';
/**
 * SRCH-01 (پرچمِ دکانِ `shop.storefront.search_core`، پیش‌فرض خاموش): جست‌وجوی ویترین با **همان**
 * قاعده‌های صندوق — `text-core.js` (همتای `TextNormalizer.kt`؛ بردارهای
 * `core/test-vectors/text-normalize.json`). کلیدِ مقایسهٔ نام در SQL با یک `translate`:
 *   «ي/ى ⇒ ی»، «ك ⇒ ک»، «ۀ ⇒ ه»، «أ/إ/آ ⇒ ا»، رقمِ فارسی و عربی ⇒ لاتین، «٫» و «,» ⇒ «.»،
 *   A–Z ⇒ a–z (فقط ASCII، بی‌وابستگی به زبانِ پایگاه)؛ اعراب، کشیده، نویسه‌های نامرئی و همهٔ
 *   فاصله‌ها حذف. هر کلمهٔ پرس‌وجو (`searchKey`) باید جایی در آن باشد، به هر ترتیب — همان
 *   `matches` ِ text-core بی مترادف. متنِ ذخیره‌شده عوض نمی‌شود؛ فقط کلید.
 * خاموش ⇒ همان `normalize`/`SQL_NAME` ِ پیشین، بی هیچ تفاوت.
 */
const textCore = require('./text-core');

const SEARCH_FLAG = 'shop.storefront.search_core';
const SEARCH_KEY = (() => {
  let from = '';
  let to = '';
  const map = (a, b) => { from += a; to += b; };
  map('يى', 'یی');            //  ي ى ⇒ ی
  map('ك', 'ک');                         //  ك ⇒ ک
  map('ۀ', 'ه');                         //  ۀ ⇒ ه
  map('أإآ', 'ااا'); //  أ إ آ ⇒ ا
  for (let i = 0; i < 10; i++) {
    map(String.fromCharCode(0x06F0 + i), String(i));
    map(String.fromCharCode(0x0660 + i), String(i));
  }
  map('٫,', '..');
  for (let c = 0x41; c <= 0x5A; c++) map(String.fromCharCode(c), String.fromCharCode(c + 32));
  //  حذف (نویسهٔ بی همتا در `to`): اعراب و کشیده، نامرئی‌ها، فاصله‌ها
  let drop = 'ٰـ‌‍‎‏؜﻿­       　';
  const range = (a, b) => { for (let c = a; c <= b; c++) drop += String.fromCharCode(c); };
  range(0x064B, 0x065F);
  range(0x202A, 0x202E);
  range(0x2066, 0x2069);
  range(0x09, 0x0D);
  range(0x2000, 0x200A);
  return Object.freeze({ from: from + drop, to });
})();

/** تکهٔ WHERE ِ جست‌وجو: هر واژهٔ `searchKey` جایی در کلیدِ نام، به هر ترتیب؛ `params` پر می‌شود. */
function searchWhere(raw, params) {
  let where = '';
  const key = `translate(COALESCE(r.data->>'name', ''), $${params.push(SEARCH_KEY.from)}, $${params.push(SEARCH_KEY.to)})`;
  for (const word of textCore.searchKey(raw).split(' ').filter(Boolean)) {
    params.push(`%${word.replace(/[\\%_]/g, (c) => `\\${c}`)}%`);
    where += ` AND ${key} LIKE $${params.length}`;
  }
  return where;
}

// ════════════════════════════════════════════════════════════════════
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
const SMART_FLAG = 'shop.storefront.search';
const FUZZY_SCAN = 1000;
const SUGGEST_CATEGORIES = 5;
//  همان نگاشتِ `text-core.asciiDigits`، در SQL
const DIGITS_FROM = '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩٫٬';
const DIGITS_TO = '01234567890123456789.,';

function enabled(shopId) {
  return require('./orders').flagOf(String(shopId), SMART_FLAG);
}

/** نامِ نرمال‌شدهٔ کالا (`sqlName` ِ ویترین) با رقمِ لاتین. */
const sqlName = (base) => `translate(${base}, '${DIGITS_FROM}', '${DIGITS_TO}')`;

/** پرس‌وجوی نرمال‌شدهٔ ویترین با رقمِ لاتین. */
const needle = (normalized) => textCore.asciiDigits(normalized);

/** دسته‌ای که پرس‌وجو دقیقاً همان است (کلیدِ مقایسه) — یا ''. */
function categoryOf(q, categories) {
  const key = textCore.compareKey(q);
  if (!key) return '';
  return categories.find((c) => textCore.compareKey(c) === key) || '';
}

/** آیا پرس‌وجو اصلاً جای غلطِ تایپی دارد (دست‌کم یک کلمهٔ ۴حرفی). */
function fuzzyWorthy(q) {
  return textCore.searchKey(q).split(' ').some((w) => textCore.typoBudget(w.length) > 0);
}

/** از میانِ ردیف‌های `{ name }`، آن‌هایی که با بودجهٔ غلط جورند — به همان ترتیب. */
function fuzzyPick(rows, q, limit) {
  const out = [];
  for (const r of rows) {
    if (out.length >= limit) break;
    if (textCore.fuzzyMatches(r.name, q)) out.push(r);
  }
  return out;
}

/** دسته‌های نزدیک به پرس‌وجو (کلمه‌ای مشترک یا در بودجهٔ غلط). */
function nearCategories(q, categories) {
  return categories.filter((c) => textCore.matches(c, q) || textCore.fuzzyMatches(c, q) || textCore.matches(q, c))
    .slice(0, SUGGEST_CATEGORIES);
}

/**
 * بی نتیجهٔ دقیق (صفحهٔ اول): اول غلطِ تایپی — فقط کالای پیشنهادشدنی (ناموجود هرگز، WEB-09) —
 * وگرنه «چیزی پیدا نشد» با دسته‌های نزدیک، پرفروش/تازهٔ واقعی و تلفنِ تماسِ ویترین.
 * `decorate` همان تابعِ ویترین است (عکسِ بانک و موجودی روی نماها).
 */
async function miss({ shopId, raw, cat, lim, contactPhone, decorate }) {
  const sf = require('./storefront');
  if (fuzzyWorthy(raw)) {
    const params = [shopId];
    let where = `sp.shop_id = $1 AND sp.published AND ${sf.LIVE_PRODUCT}`;
    if (cat) {
      params.push(cat);
      where += ` AND r.data->>'category' = $${params.length}`;
    }
    const rows = await require('../db').many(
      `SELECT ${sf.PRODUCT_COLS} FROM ${sf.PRODUCT_FROM} WHERE ${where}
        ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT ${FUZZY_SCAN}`,
      params
    );
    const views = rows.map(sf.productView).filter((p) => p.priceMinor !== null && p.name);
    const { suggestable } = require('./storefront-shelf');
    const hits = (await decorate(shopId, fuzzyPick(views, raw, lim * 3))).filter(suggestable).slice(0, lim);
    if (hits.length) return { items: hits, nextCursor: null, match: 'fuzzy' };
  }
  const [cats, hl] = await Promise.all([sf.categories(shopId), sf.highlights(shopId)]);
  const best = hl.bestsellers.length > 0;
  return {
    items: [], nextCursor: null, match: 'none',
    suggest: {
      categories: nearCategories(raw, cats),
      picks: best ? hl.bestsellers : hl.newArrivals, picksKind: best ? 'bestsellers' : 'newArrivals',
      contactPhone: contactPhone || '',
    },
  };
}

/**
 * پیش از پرس‌وجوی `catalog`: پرچمِ WEB-05 و پرش به دسته (پرس‌وجو همان نامِ دسته‌ای زنده و
 * دسته‌ای انتخاب نشده). `needle`/`name` همان نرمال‌سازی و کلیدِ SQL ِ ویترین‌اند، با رقمِ لاتین
 * وقتی پرچم روشن است؛ پرش ⇒ بی `needle` (نتیجه همان دسته). خاموش ⇒ دقیقاً همان قبلی.
 * ⇒ { smart, jump, needle, name }
 */
async function plan(shopId, raw, cat, { categories, normalize, SQL_NAME }) {
  const smart = raw ? await enabled(shopId) : false;
  const jump = smart && !cat ? categoryOf(raw, await categories(shopId)) : '';
  const base = jump ? '' : normalize(raw);
  return { smart, jump, needle: smart && base ? needle(base) : base, name: smart ? sqlName(SQL_NAME) : SQL_NAME };
}

/** پس از پرس‌وجو: پرچمِ خاموش ⇒ همان صفحه؛ وگرنه `match` (دسته، دقیق) یا «بی نتیجه» ِ `miss`. */
function finish(out, p, { cursor, ...ctx }) {
  if (!p.smart) return out;
  if (p.jump) return { ...out, match: 'category', jump: { category: p.jump } };
  if (out.items.length || cursor) return { ...out, match: 'exact' };
  return miss(ctx);
}

module.exports = {
  SEARCH_FLAG, SEARCH_KEY, searchWhere,
  SMART_FLAG, FUZZY_SCAN, enabled, sqlName, needle, categoryOf, fuzzyWorthy, fuzzyPick, nearCategories, miss, plan, finish,
};
