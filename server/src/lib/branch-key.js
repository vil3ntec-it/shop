'use strict';
/**
 * کلیدِ «بخشِ» همگام‌سازیِ یک شعبه (`docs/BRANCHES-fa.md`) — بی وابستگی، تا کتابخانه‌های پایه (پرچم، لایه‌های مجوز)
 * بی چرخهٔ require آن را بخوانند.
 *
 *   شعبهٔ اصلی ('')  ⇐ همان شناسهٔ دکان (حسابِ امروز؛ هیچ ردیفی کوچ داده نمی‌شود)
 *   شعبهٔ دیگر        ⇐ '<دکان>#<شعبه>' — فقط در جدول‌های همگام‌سازی (`account_id`) و فقط Sync v3
 *
 * ⛔ '#' در هیچ شناسهٔ دکان/پمپی نیست (`newId`: پیشوند_هگز) و از درخواست ساخته نمی‌شود: فقط `branches.ctxFor` پس از
 *    سنجیدنِ دسترسی. پس `shopOf` روی هر شناسهٔ امروز همانی است و هیچ رفتاری عوض نمی‌شود.
 * ⛔ هر خواندنِ «سطحِ دکان» (پرچم، لایه‌های مجوز، عضویت، کلیدِ تأیید، محدودهٔ مکان) با `shopOf` می‌رود، نه با کلیدِ
 *    بخش — وگرنه پرچم/لایهٔ بسته‌کنندهٔ صاحب در شعبه پیدا نمی‌شد و نگهبان باز می‌ماند.
 */
const SEP = '#';
const MAIN = '';

/** کلیدِ بخش برای (دکان، شعبه) */
function partitionOf(shopId, branchId) {
  const s = String(shopId || '');
  const b = String(branchId || '');
  return b ? `${s}${SEP}${b}` : s;
}

/** شناسهٔ دکانِ یک کلید (بخش یا خودِ دکان) — یا بافتِ همگام‌سازی (`{shopId, accountId}`) */
function shopOf(x) {
  if (x && typeof x === 'object') return shopOf(x.shopId || x.accountId);
  const s = String(x);   //  همان `String(shopId)` ِ پیشین (null/undefined هم) ⇒ برای هر شناسهٔ امروز دقیقاً همانی
  const i = s.indexOf(SEP);
  return i < 0 ? s : s.slice(0, i);
}

/** شعبهٔ یک کلید ('' = اصلی) */
function branchOf(x) {
  if (x && typeof x === 'object') return x.branchId ? String(x.branchId) : branchOf(x.accountId);
  const s = String(x ?? '');
  const i = s.indexOf(SEP);
  return i < 0 ? MAIN : s.slice(i + 1);
}

/** آیا این کلید/بافت بخشِ یک شعبهٔ غیرِ اصلی است */
const isBranch = (x) => branchOf(x) !== MAIN;

module.exports = { SEP, MAIN, partitionOf, shopOf, branchOf, isBranch };
