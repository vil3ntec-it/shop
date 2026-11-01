package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.text.TextNormalizer

/*
 *  برند و برچسبِ کالا — PRD-06 (§۶.۳). دسته، زیردسته و تأمین‌کنندهٔ اصلی پیش‌تر بودند.
 *
 *  • برند: فاصله‌ها یکی، حداکثر ۸۰ نویسه — همان `text(d.brand, 80)` ِ کاتالوگِ سرور
 *    (`lib/catalog.js`) و نمای عمومیِ بازار، تا آنچه این‌جا نوشته می‌شود همان باشد که مشتری می‌بیند.
 *  • برچسب‌ها: کادرِ «برچسب‌ها» با «،» یا «,» یا «؛» یا خطِ تازه؛ هر برچسب حداکثر ۳۰ نویسه، حداکثر ۱۰؛
 *    تکراری با همان کلیدِ مقایسه (`TextNormalizer.compareKey`: «ي/ی»، نیم‌فاصله، بزرگی/کوچکی) یکی.
 *  • متنِ کاربر عوض نمی‌شود جز فاصله‌های اضافه؛ نخستین املا می‌ماند.
 *  منطقِ خالص؛ آزمون: `ProductLabelsTest` (commonTest).
 */
object ProductLabels {

  const val BRAND_MAX = 80
  const val TAG_MAX = 30
  const val TAGS_MAX = 10

  private fun squeeze(s: String): String = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

  fun brand(raw: String): String = squeeze(raw).take(BRAND_MAX).trim()

  fun tags(raw: String): List<String> = tags(raw.split(',', '،', ';', '؛', '\n', '\r'))

  fun tags(list: List<String>): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (t in list) {
      val clean = squeeze(t).take(TAG_MAX).trim()
      if (clean.isEmpty()) continue
      if (!seen.add(TextNormalizer.compareKey(clean))) continue
      out += clean
      if (out.size == TAGS_MAX) break
    }
    return out
  }

  /** کالا این برچسب را دارد؟ (همان کلیدِ مقایسه) */
  fun hasTag(tags: List<String>?, tag: String): Boolean {
    val k = TextNormalizer.compareKey(tag)
    return k.isNotEmpty() && tags.orEmpty().any { TextNormalizer.compareKey(it) == k }
  }
}
