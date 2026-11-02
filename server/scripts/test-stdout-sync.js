'use strict';
/**
 * پیش‌بارِ فرآیندِ هر پروندهٔ آزمون — `scripts/test-watchdog.js` آن را با `--require` می‌دهد وقتی
 * `--test-force-exit` در کار است (`scripts/test-pglite.js`: کارهای PGlite و Electron).
 *
 * ⛔ `--test-force-exit` پس از آخرین آزمونِ پرونده `process.exit()` می‌زند و نوشتن‌های ناتمامِ stdout
 * (سوکتِ رو به `node --test` که گزارشِ v8 ِ هر آزمون از آن می‌رود) دور ریخته می‌شوند: نتیجهٔ
 * آزمون‌های پایانیِ پرونده هرگز به گزارشگرها (TAP، شکست‌ها، شواهد) نمی‌رسد — بی خطا و بی «not ok»،
 * فقط شمارِ کمتر. بازتولید (۱۴۰۵/۰۸/۱۱، زیرِ بار): پرونده‌ای با ۸۰ آزمونِ تهی ⇒ ۲۲ تا ۴۹ گزارش در
 * هشت اجرا (Electron 38: ۹ تا ۷۵)؛ promo-core.test.js (۷۲ آزمون) ⇒ ۳۵، ۷۲، ۲۸. با stdout ِ هم‌زمان
 * (blocking) هر گزارش پیش از بازگشتِ write در سوکت است ⇒ ۸۰ از ۸۰ در هر اجرا، در Node و Electron.
 *
 * فقط در فرآیندِ پرونده (`NODE_TEST_CONTEXT=child-v8`)؛ در خودِ `node --test` و هر جای دیگر کاری نمی‌کند.
 */
if (process.env.NODE_TEST_CONTEXT === 'child-v8') {
  const handle = process.stdout && process.stdout._handle;
  if (handle && typeof handle.setBlocking === 'function') handle.setBlocking(true);
}
