package ir.vil3ntec.tohid.data

/**
 *  تاریخِ انقضا و سری ساخت — حسابِ «از هر محموله چقدر مانده و کِی تمام می‌شود».
 *
 *  ── قاعده ─────────────────────────────────────────────────────────
 *  فروش محموله را نمی‌شناسد؛ فقط از کالا کم می‌کند. پس ماندهٔ هر محموله با
 *  همان سیاستی حساب می‌شود که دکان‌دار باید بفروشد: **اول آنچه زودتر تمام
 *  می‌شود** (FEFO). یعنی موجودیِ امروزِ کالا روی محموله‌هایی می‌نشیند که
 *  دیرتر منقضی می‌شوند، و محموله‌های زودتر «فروخته‌شده» حساب می‌شوند.
 *
 *  محموله‌ای که انقضا ندارد (`expiryDate` خالی) آخر از همه است؛ و هیچ
 *  تاریخِ ساختگی‌ای جای خالی نمی‌نشیند — «نامعلوم» با «دارد» یکی نیست.
 *  ردیف‌های اصلاحِ موجودی (منفی) محموله نیستند؛ اثرشان در موجودیِ کل است.
 *
 *  همان `expiryLots`ِ نسخهٔ وب، عددبه‌عدد.
 *  ──────────────────────────────────────────────────────────────────
 */
object ExpiryEngine {

  /** پیش‌فرضِ «نزدیک به انقضا» — روز */
  const val NEAR_DAYS = 30

  private val ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")

  /** خالی درست است (بی انقضا)؛ وگرنه باید `yyyy-MM-dd`ِ واقعی باشد */
  fun validDate(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return true
    if (!ISO.matches(v)) return false
    val (y, m, day) = v.split('-').map { it.toInt() }
    if (m !in 1..12 || day < 1) return false
    val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    val max = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[m - 1]
    return day <= max
  }

  /** روزهای مانده تا انقضا؛ منفی یعنی گذشته */
  fun daysLeft(expiry: String, today: String): Long = epochDay(expiry) - epochDay(today)

  /**
   *  شمارهٔ روز از ۱۹۷۰-۰۱-۰۱ — بی `java.time`، چون برنامه از اندروید ۷ کار
   *  می‌کند و `java.time` از ۸ به بعد است (الگوریتمِ «days from civil»).
   */
  internal fun epochDay(iso: String): Long {
    val (y0, m, d) = iso.trim().split('-').map { it.toLong() }
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
  }

  data class Lot(
    val entry: WarehouseEntry,
    val product: Product,
    /** ماندهٔ همین محموله به FEFO */
    val remaining: Double,
    val daysLeft: Long,
  ) {
    val expired: Boolean get() = daysLeft < 0
  }

  /** ماندهٔ هر محمولهٔ انقضادار، برای همهٔ کالاها */
  fun lots(d: ShopData, today: String): List<Lot> {
    val out = ArrayList<Lot>()
    val byProduct = d.warehouseEntries
      .filter { !it.isAdjustment && it.units > 0 }
      .groupBy { it.productId }
    val products = d.products.associateBy { it.id }
    for ((productId, entries) in byProduct) {
      if (entries.none { it.expiryDate.isNotBlank() }) continue
      val product = products[productId] ?: continue
      var left = ShopStore.stock(d, productId)
      if (left <= 0) continue
      //  از دیرترین به زودترین؛ بی‌انقضا دیرتر از همه
      val ordered = entries.sortedWith(
        compareByDescending<WarehouseEntry> { it.expiryDate.isBlank() }.thenByDescending { it.expiryDate }
      )
      for (e in ordered) {
        if (left <= 0) break
        val take = minOf(e.units, left)
        left -= take
        if (e.expiryDate.isNotBlank() && take > 0) {
          out += Lot(e, product, take, daysLeft(e.expiryDate, today))
        }
      }
    }
    return out.sortedBy { it.daysLeft }
  }

  /** ماندهٔ منقضی‌شدهٔ یک کالا (به FEFO) — برای سیاستِ «کالای منقضی فروخته نشود» */
  fun expiredRemaining(d: ShopData, productId: String, today: String): Double =
    lots(d, today).filter { it.product.id == productId && it.expired }.sumOf { it.remaining }

  /** منقضی‌شده یا تا `days` روزِ دیگر — همان چیزی که هشدار و گزارش نشان می‌دهند */
  fun alerts(d: ShopData, today: String, days: Int = NEAR_DAYS): List<Lot> =
    lots(d, today).filter { it.daysLeft <= days }
}
