package ir.vil3ntec.tohid.core.product

/**
 *  WEB-13 — «دیده‌شده‌های اخیر» ِ خریدار: فهرستی کوتاه که فقط روی همان دستگاه می‌ماند
 *  (مثلِ سبدِ مهمان، S132) و هیچ‌وقت به سرور نمی‌رود.
 *
 *  قاعده‌ها (همزادِ `server/public/store/recent.js` ِ وب؛ هر دو همین را می‌سنجند):
 *    • تازه‌ترین اول؛ دیدنِ دوبارهٔ یک کالا آن را به سر می‌برد، دو بار نمی‌آید.
 *    • حداکثر [MAX] کالا؛ کهنه‌تر از [TTL_MS] (۳۰ روز) بیرون می‌افتد.
 *    • شناسهٔ نامعتبر (همان الگوی شناسهٔ کالای ویترین) یا زمانِ آینده‌ای که ساعتِ دستگاه
 *      عقب رفته باشد، فهرست را خراب نمی‌کند: ردیفِ بد کنار می‌رود.
 *
 *  منطقِ خالص، بی `android.*`/`java.*` (§۴۰.۳) — برای برنامهٔ خریدِ جدا.
 */
object RecentlyViewed {

  const val MAX: Int = 12
  const val TTL_MS: Long = 30L * 24 * 60 * 60 * 1000

  /** همان `PRODUCT_ID_RE` ِ سرورِ ویترین */
  private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  data class Seen(val id: String, val atMs: Long)

  /** فهرستِ پاک: شناسهٔ درست، بی تکرار (تازه‌ترین می‌ماند)، تازه‌ترین اول، بی کهنه، حداکثر MAX. */
  fun fresh(list: List<Seen>, nowMs: Long): List<Seen> {
    val seen = HashSet<String>()
    return list
      .filter { ID.matches(it.id) && it.atMs > nowMs - TTL_MS && it.atMs <= nowMs + TTL_MS }
      .sortedWith(compareByDescending<Seen> { it.atMs }.thenBy { it.id })
      .filter { seen.add(it.id) }
      .take(MAX)
  }

  /** دیدنِ `id` در `nowMs`: سرِ فهرست می‌نشیند؛ شناسهٔ بد ⇒ همان فهرستِ پاک. */
  fun add(list: List<Seen>, id: String, nowMs: Long): List<Seen> =
    if (!ID.matches(id)) fresh(list, nowMs) else fresh(listOf(Seen(id, nowMs)) + list.filter { it.id != id }, nowMs)
}
