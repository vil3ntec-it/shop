'use strict';
/**
 * PRM-11 — گروهِ مشتری (CRM-08) در تخفیفِ ویترین، پشتِ پرچمِ خاموشِ `shop.storefront.promo_groups` (همراهِ
 * `shop.storefront.promos`؛ `lib/storefront-promos.js`).
 *
 *   گروه ───── فقط از قرض‌دارهای دفترِ همگام‌شدهٔ **همین** دکان با **همان** شمارهٔ تأییدشده (بلیتِ تازه یا نشستِ
 *              حساب؛ `store-account-history.debtorRowsOf`: تطبیقِ دقیق، بی ادغام‌شده و پاک‌شده). ⛔ هرگز از بدنه یا
 *              نشانی؛ بی شمارهٔ تأییدشده ⇒ ''.
 *   شک ─────── چند قرض‌دارِ همان شماره با گروه‌های ناهم‌سان (با `compareKey`) ⇒ '' (بی تخفیفِ گروه، نه حدس).
 *              قرض‌دارِ بی گروه کنارِ گروه‌دار شک نمی‌سازد.
 *   املا ───── نامِ گروهِ مشتری ⇐ همان املای قاعده با `compareKey` («ي/ی»، فاصله و نیم‌فاصله) — همان
 *              `PromoEngine.groupOf` ِ صندوق (`promo-loyalty-review.groupOf`).
 *
 * ⛔ پرچمِ خاموش ⇒ هیچ پرسشی جز خودِ پرچم و گروه همیشه '' ⇒ پاسخ و ثبت بایت‌به‌بایت همان پیشین.
 * ⛔ فهرستِ عمومی (`GET /promos`) قاعدهٔ گروه‌دار را هرگز نشان نمی‌دهد؛ نامِ گروه در هیچ پاسخی نیست.
 */
const orders = require('./orders');

const FLAG = 'shop.storefront.promo_groups';

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

/** گروهِ خامِ مشتریِ این شماره در این دکان، یا '' (بی قرض‌دار، بی گروه یا گروه‌های ناهم‌سان) */
async function groupOfPhone(shopId, phone) {
  const { compareKey } = require('./text-core');
  const seen = new Map();
  for (const d of await require('./store-account-history').debtorRowsOf(shopId, phone)) {
    const g = d.group.trim();
    if (g) seen.set(compareKey(g), g);
  }
  return seen.size === 1 ? [...seen.values()][0] : '';
}

/**
 * پیش از تراکنشِ ثبت/پیش‌نمایش: پرچمِ روشن و شمارهٔ تأییدشده ⇒ گروهِ خام؛ وگرنه ''.
 * @param {string|(() => Promise<string|null>)} phone شمارهٔ تأییدشده یا تابعِ دیربارِ آن (پیش‌نمایش)
 */
async function prepare(shopId, phone) {
  if (!(await flagOn(shopId))) return '';
  const p = typeof phone === 'function' ? await phone() : phone;
  return p ? groupOfPhone(shopId, p) : '';
}

/** گروهِ خام ⇒ نامِ گروهِ قاعده‌ها (همان املا)، یا '' */
function match(raw, rules) {
  return raw ? require('./promo-loyalty-review').groupOf({ group: raw }, rules) : '';
}

module.exports = { FLAG, flagOn, prepare, match, groupOfPhone };
