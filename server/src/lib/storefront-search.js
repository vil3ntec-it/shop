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

module.exports = { SEARCH_FLAG, SEARCH_KEY, searchWhere };
