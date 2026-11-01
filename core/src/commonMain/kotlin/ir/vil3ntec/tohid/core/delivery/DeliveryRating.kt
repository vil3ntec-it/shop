package ir.vil3ntec.tohid.core.delivery

/**
 *  امتیازِ دوطرفهٔ تحویل — DLV-18. منطقِ خالص، commonMain (بی `java.*`).
 *
 *  مشتری به پیک (صفحهٔ پیگیریِ ویترین) و پیک به تحویل (برنامهٔ پیک)، فقط پس از «تحویل شد»، یک بار از هر سو.
 *  فقط ستارهٔ ۱ تا ۵ و برچسب از فهرستِ ثابتِ همان سو (حداکثر ۴، بی متنِ آزاد). تصمیمِ نهایی با سرور است
 *  (`server/src/lib/delivery-ratings.js`، پشتِ پرچمِ خاموشِ `shop.delivery_ratings`). ⛔ دوقلوی همان پرونده؛ هر دو
 *  با بردارِ `core/test-vectors/delivery-ratings-v1.json` (`DeliveryRatingVectorsTest` و `server/test/delivery-ratings.test.js`).
 */
object DeliveryRating {

  const val MAX_TAGS = 4

  val TAGS: Map<String, List<String>> = mapOf(
    "customer" to listOf("on_time", "polite", "careful", "late", "rude", "damaged"),
    "courier" to listOf("easy_to_find", "ready", "hard_to_find", "not_ready", "wrong_pin"),
  )

  data class Rating(val stars: Int, val tags: List<String>)

  /** ورودی (همان شکلِ JSON ِ سرور) ⇒ `(امتیاز, "")` یا `(null, کدِ خطا)`: `bad_stars` / `bad_tags`. */
  fun parse(stars: Any?, tags: Any?, rater: String): Pair<Rating?, String> {
    val s = when (stars) {
      is Int -> stars.toLong()
      is Long -> stars
      else -> null
    }
    if (s == null || s < 1 || s > 5) return null to "bad_stars"
    val raw: List<*> = when (tags) {
      null -> emptyList<String>()
      is List<*> -> tags
      else -> return null to "bad_tags"
    }
    if (raw.size > MAX_TAGS * 2 || raw.any { it !is String }) return null to "bad_tags"
    val allowed = TAGS[rater] ?: return null to "bad_tags"
    val set = raw.map { (it as String).trim() }.toSet()
    if (set.any { it !in allowed } || set.size > MAX_TAGS) return null to "bad_tags"
    return Rating(s.toInt(), allowed.filter { it in set }) to ""
  }

  data class Summary(val count: Int, val avgTenths: Int?, val tags: Map<String, Int>)

  /** میانگین به دهم (۴٫۳ ⇒ ۴۳، نیمه به بالا) با عددِ صحیح، و شمارِ هر برچسب. */
  fun summarize(list: List<Rating>): Summary {
    val tags = linkedMapOf<String, Int>()
    var sum = 0L
    for (r in list) {
      sum += r.stars
      for (t in r.tags) tags[t] = (tags[t] ?: 0) + 1
    }
    val n = list.size.toLong()
    return Summary(list.size, if (n == 0L) null else ((sum * 20 + n) / (2 * n)).toInt(), tags)
  }
}
