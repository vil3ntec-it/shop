'use strict';
/**
 * S33 — Plus Code ِ ساخته از **پینِ** نشانی در سفارش و نشانیِ ذخیره‌شده (همتای `core/courier/PlusCode.kt`).
 *
 *   پرچم ── `shop.orders.plus_code` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ همان شکلِ نشانیِ امروز،
 *           بی هیچ کلیدِ تازه (آزمون‌ها شیءِ نشانی را دقیق می‌سنجند).
 *   روشن ── سفارش (ثبتِ کارمند و ویترین، «اصلاحِ نشانی» ِ دکان، «تغییرِ روش» ِ مشتری): نشانیِ پین‌دار `pinCode` ِ
 *           ده‌نویسه‌ای می‌گیرد که **سرور** از همان پین می‌سازد؛ `pinCode` ِ بدنه هرگز پذیرفته نیست (`parseAddress`
 *           کلیدِ ناشناس را دور می‌ریزد) و پینِ تازه کدِ پیشین را جایگزین می‌کند. بی پین ⇒ بی `pinCode`.
 *           نشانیِ ذخیره‌شدهٔ حساب در ویترینِ همان دکان: `pinCode` از پینِ ذخیره‌شده در همان پاسخ (هرگز کهنه نمی‌شود).
 *           ویترین (`features.plusCode`) کد را زیرِ پین نشان می‌دهد (`public/store/plus-code.js`، همین حساب).
 *   ⛔ `plusCode` ِ تایپ‌شدهٔ مشتری (CHK-08) دست نمی‌خورد؛ این کد جدا و فقط از پین است.
 */
const { encode } = require('../../public/store/plus-code.js');

const FLAG = 'shop.orders.plus_code';

const flagOn = (shopId, run = null) => require('./orders').flagOf(shopId, FLAG, run);

/** کدِ پینِ یک نشانی؛ بی پین یا پینِ نامعتبر ⇒ null */
function codeOf(a) {
  if (!a || typeof a !== 'object' || a.lat === undefined || a.lat === null || a.lng === undefined || a.lng === null) return null;
  return encode(Number(a.lat), Number(a.lng));
}

/** نشانی با `pinCode` ِ تازه از همان پین (پرچمِ روشن)؛ خاموش ⇒ همان شیء، دست‌نخورده */
function stamp(address, on) {
  if (!on || !address || typeof address !== 'object') return address;
  const { pinCode: _old, ...rest } = address;
  const code = codeOf(rest);
  return code ? { ...rest, pinCode: code } : rest;
}

module.exports = { FLAG, encode, flagOn, codeOf, stamp };
