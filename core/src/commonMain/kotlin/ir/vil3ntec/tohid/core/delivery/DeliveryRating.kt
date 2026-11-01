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

  /** پیک: دکمهٔ امتیاز فقط برای «تحویل شد» و یک بار؛ مهلتِ ۷ روز را سرور می‌سنجد (`rating_closed`) */
  fun courierCanRate(status: String, assignmentId: String, rated: Set<String>): Boolean =
    status == "delivered" && assignmentId !in rated

  /** میانگین به دهم ⇒ «4.3» (رقمِ لاتین؛ رقمِ صفحه از فونت)؛ بی امتیاز ⇒ «—» */
  fun avgText(avgTenths: Int?): String = if (avgTenths == null) "—" else "${avgTenths / 10}.${avgTenths % 10}"

  /** یک ردیفِ `couriers` ِ `GET /shop/delivery/ratings` */
  data class CourierRatings(val courierId: String, val name: String, val fromCustomers: Summary, val byCourier: Summary)

  /** یک ردیفِ `recent` (بی نام و شمارهٔ مشتری) */
  data class Recent(val orderNo: Long, val rater: String, val stars: Int, val tags: List<String>, val courierId: String)

  data class Board(val couriers: List<CourierRatings>, val recent: List<Recent>)

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
