'use strict';
/**
 * تکه کردنِ فهرستِ پرونده‌های آزمون برای کارهای هم‌زمانِ CI (`TEST_SHARD=k/n`).
 *
 * کارهای «PGlite» و «زیرِ Electron» همهٔ پرونده‌ها را پشتِ‌سرِ‌هم می‌دوانند و با رشدِ آزمون‌ها
 * به سقفِ ۴۵ دقیقهٔ کار رسیدند (دستهٔ ۴۱، #1025: Electron در ۴۵:۱۵ لغو شد، همهٔ آزمون‌ها تا آن لحظه
 * سبز؛ main ِ f8134445 هم). سقف بالا نمی‌رود و هیچ پرونده‌ای کنار نمی‌رود: هر پرونده دقیقاً در یک
 * تکه است (جایگاهِ i در فهرستِ مرتب ⇒ تکهٔ i mod n)، و کارِ پایانیِ «آزمون‌های سرور» فقط وقتی سبز
 * است که همهٔ تکه‌ها سبز باشند.
 *
 * نبودِ TEST_SHARD ⇒ همهٔ پرونده‌ها (اجرای محلی همان امروز). مقدارِ ناجور ⇒ خطا، نه اجرای بی‌صدای
 * هیچ یا همه.
 */
function shardOf(files, spec) {
  if (spec === undefined || spec === null || spec === '') return files.slice();
  const m = /^([1-9]\d{0,2})\/([1-9]\d{0,2})$/.exec(String(spec));
  if (!m || Number(m[1]) > Number(m[2])) throw new Error(`TEST_SHARD نادرست: «${spec}» (شکلِ درست: k/n با ۱ ≤ k ≤ n)`);
  const k = Number(m[1]) - 1;
  const n = Number(m[2]);
  return files.filter((_, i) => i % n === k);
}

module.exports = { shardOf };
