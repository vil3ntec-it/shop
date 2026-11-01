'use strict';
/**
 * «همان که مشتری دید» در ثبتِ ویترین — CHK-17 و سناریوی S32: «پیش از ثبت، پیامِ روشنِ
 * "قیمتِ X از ۱۲۰ به ۱۳۰ تغییر کرد" … هرگز با قیمتِ تأییدنشده ثبت نمی‌شود».
 *
 *   جمع ─── `expectedTotalMinor` (پیشین): ناهمخوان ⇒ ۴۰۹ `price_changed` با برگهٔ تازه.
 *   قلم ─── `lines[].expectedUnitPriceMinor` (اختیاری، تازه): فیِ هر قلم که مشتری دید. ناهمخوان ⇒
 *           همان ۴۰۹، حتی اگر جمع از قضا یکی مانده باشد (دو قیمت جابه‌جا)، و `changes` =
 *           [{ productId, name, fromMinor, toMinor }] تا مشتری بداند **کدام** کالا و **چقدر**.
 *
 * ⛔ مبلغِ مشتری فقط برای «تغییر کرده؟» است؛ قیمت را همیشه سرور از دفتر می‌خواند (§ ویترین).
 *    مشتری‌ای که فیِ قلم نمی‌فرستد (صفحهٔ امروزِ ویترین) همان رفتارِ پیشین را می‌بیند.
 */
const { badRequest, conflict } = require('../middleware/errors');

const MAX_PRICE_MINOR = 100_000_000_000;

/** فیِ دیده‌شدهٔ قلم از بدنه — نیامده ⇒ null. */
function expectedOf(x, i) {
  const v = x && typeof x === 'object' ? x.expectedUnitPriceMinor : undefined;
  if (v === undefined || v === null || v === '') return { expectedUnitPriceMinor: null };
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < 0 || v > MAX_PRICE_MINOR) {
    throw badRequest(`فیِ دیده‌شدهٔ قلمِ ${i + 1} معتبر نیست`, 'bad_lines');
  }
  return { expectedUnitPriceMinor: v };
}

/** قلم‌هایی که فیِ امروزشان با فیِ دیده‌شده فرق دارد (به ترتیبِ سبد). */
function changesOf(inputLines, pricedLines) {
  const seen = new Map(inputLines.map((l) => [l.productId, l.expectedUnitPriceMinor]));
  return pricedLines
    .filter((l) => seen.get(l.productId) !== null && seen.get(l.productId) !== undefined
      && seen.get(l.productId) !== l.unitPriceMinor)
    .map((l) => ({ productId: l.productId, name: l.name, fromMinor: seen.get(l.productId), toMinor: l.unitPriceMinor }));
}

/** جمع یا فیِ هر قلم با آن‌چه مشتری دید فرق دارد ⇒ ۴۰۹ با برگهٔ تازه و فهرستِ «از … به …». */
function assertSeen(input, pricedLines, totalMinor, quote) {
  const changes = changesOf(input.lines, pricedLines);
  if ((input.expectedTotalMinor !== null && input.expectedTotalMinor !== totalMinor) || changes.length) {
    throw Object.assign(conflict('قیمت‌ها عوض شده است؛ سبد را دوباره ببینید', 'price_changed'),
      { data: { quote: quote(), changes } });
  }
}

module.exports = { expectedOf, changesOf, assertSeen };
