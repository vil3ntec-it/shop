package ir.vil3ntec.tohid.core.storefront

/*
 *  CHK-10/S34 — فهرستِ «وقتی ارسال به این‌جا رسید خبرم کن» ِ ویترین در برنامهٔ مدیریت
 *  (`GET /api/shop/storefront/area-requests`، همان `server/src/lib/storefront-area-requests.js` و
 *  همزادِ `license/area-requests.js` ِ وبِ مدیریت).
 *
 *   • تقاضا به ازای خانهٔ ۳۰۰ متریِ شبکه **بی شماره**؛ جایی که هنوز ارسال نمی‌رسد اول (تصمیمِ
 *     «ناحیه را بزرگ‌تر کنم؟»)، بعد پرتقاضاتر؛ حداکثر [MAX_CELLS].
 *   • شماره فقط برای درخواستی که ناحیهٔ فعالِ امروز پوشانده (همان کسی که باید خبرش کرد) — سرور
 *     همین را می‌فرستد؛ این‌جا چیزی ساخته یا نگه داشته نمی‌شود.
 *   • ⛔ سرور پیامی نمی‌فرستد (D-09): دکان خودش زنگ می‌زند و «خبر دادم» ردیف را پاک می‌کند.
 *
 *  Kotlin ِ خالص (commonMain).
 */
object AreaRequests {
  const val MAX_CELLS = 20

  data class Cell(val lat: Double, val lng: Double, val count: Int, val coveredBy: String?)
  data class Contact(val id: String, val phone: String, val zone: String, val requestedAt: Long)
  data class View(val total: Int, val covered: Int, val cells: List<Cell>, val contacts: List<Contact>)

  fun view(total: Int, covered: Int, cells: List<Cell>, contacts: List<Contact>): View = View(
    total = total.coerceAtLeast(0),
    covered = covered.coerceAtLeast(0),
    cells = cells.sortedWith(compareBy<Cell>({ it.coveredBy != null }, { -it.count })).take(MAX_CELLS),
    contacts = contacts.filter { it.id.isNotBlank() && it.phone.isNotBlank() },
  )

  /** پس از «خبر دادم» تا رسیدنِ فهرستِ تازه: همان ردیف بیرون، شمارِ پوشیده یکی کم */
  fun without(v: View, id: String): View {
    val left = v.contacts.filterNot { it.id == id }
    if (left.size == v.contacts.size) return v
    return v.copy(contacts = left, covered = (v.covered - 1).coerceAtLeast(0), total = (v.total - 1).coerceAtLeast(0))
  }

  /** مختصاتِ کوتاه برای برچسبِ خانه (سه رقمِ اعشار ≈ ۱۰۰ متر) */
  fun coord(x: Double): String {
    val scaled = kotlin.math.round(x * 1000).toLong()
    val sign = if (scaled < 0) "-" else ""
    val a = kotlin.math.abs(scaled)
    return sign + (a / 1000).toString() + "." + (a % 1000).toString().padStart(3, '0')
  }
}
