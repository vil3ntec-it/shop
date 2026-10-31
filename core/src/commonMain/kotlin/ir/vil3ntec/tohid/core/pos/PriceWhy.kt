package ir.vil3ntec.tohid.core.pos

/**
 *  «چرا این قیمت؟» — POS-22 (§۶.۱): قیمتِ هر ردیفِ سبد گام‌به‌گام، از قیمتِ فهرستِ کالا تا جمعی که
 *  واقعاً گرفته می‌شود:
 *
 *    ۱. قیمتِ فهرست (`Product.salePrice`) — قلمِ آزاد ندارد؛
 *    ۲. قیمتِ دستیِ همین ردیف (قیمتِ دستی یا تخفیفِ ردیف، POS-08/20) با فاصله‌اش از فهرست؛
 *    ۳. جمعِ پایه = مقدار × قیمتِ واحد، گرد به افغانیِ کامل (همان `SalesEngine.lineTotal`)؛
 *    ۴. هر تخفیفِ خودکار (PRM) با نامِ قاعده و سهمش از همین ردیف؛
 *    ۵. اگر جمعِ واقعی با «پایه − تخفیف‌ها» نخواند (گرد کردنِ قیمتِ واحدِ تخفیف‌خورده)، ردیفِ گرد کردن —
 *       هیچ فاصله‌ای پنهان نمی‌ماند؛
 *    ۶. جمعِ ردیف: همان که سبد و فاکتور نشان می‌دهند ([explain] آن را حساب نمی‌کند، می‌گیرد).
 *
 *  هسته فقط توضیح می‌دهد؛ هیچ قیمتی را عوض یا دوباره حساب نمی‌کند. هر مبلغ `Long` ِ یک‌صدمِ افغانی و
 *  مقدار به هزارم؛ بیرون از [MAX_QTY_MILLI]/[MAX_UNIT_MINOR] ⇒ `IllegalArgumentException` (صدازننده پنل را نشان نمی‌دهد).
 *  Kotlin خالص (commonMain)؛ آزمون: `PriceWhyTest`.
 */
object PriceWhy {

  enum class Kind { LIST, MANUAL, BASE, PROMO, ROUNDING, TOTAL }

  /**
   *  [amountMinor]: برای LIST و MANUAL قیمتِ هر واحد، برای بقیه مبلغِ همین ردیف (PROMO منفی).
   *  [deltaMinor]: فقط MANUAL — فاصلهٔ قیمتِ دستی از فهرست (مثبت = گران‌تر)؛ قلمِ آزاد ⇒ null.
   */
  data class Row(val kind: Kind, val amountMinor: Long, val name: String = "", val deltaMinor: Long? = null)

  /** تخفیفِ خودکارِ یک قاعده روی همین ردیف */
  data class Promo(val name: String, val discountMinor: Long)

  /** بازهٔ امنِ حاصل‌ضرب (همان سقف‌های سفارش در `lib/orders.js`): ۱۰٬۰۰۰ واحد × یک میلیارد افغانی */
  const val MAX_QTY_MILLI = 10_000_000L
  const val MAX_UNIT_MINOR = 100_000_000_000L

  /** مقدار × قیمتِ واحد، گرد به افغانیِ کامل، نیمه رو به بالا — همان `SalesEngine.lineTotal` و `lineTotalMinor` ِ سرور */
  fun lineMinor(qtyMilli: Long, unitMinor: Long): Long {
    require(qtyMilli in 0..MAX_QTY_MILLI && unitMinor in 0..MAX_UNIT_MINOR) { "out of range" }
    return (qtyMilli * unitMinor + 50_000L).floorDiv(100_000L) * 100L
  }

  /**
   *  @param listUnitMinor قیمتِ فهرستِ کالا؛ `null` برای قلمِ آزاد
   *  @param manualUnitMinor قیمتِ دستیِ ردیف؛ `null` ⇒ همان فهرست
   *  @param finalLineMinor جمعی که سبد واقعاً برای این ردیف نشان می‌دهد (پس از تخفیفِ خودکار)
   */
  fun explain(
    qtyMilli: Long,
    listUnitMinor: Long?,
    manualUnitMinor: Long?,
    promos: List<Promo>,
    finalLineMinor: Long,
  ): List<Row> {
    val rows = ArrayList<Row>()
    if (listUnitMinor != null) rows += Row(Kind.LIST, listUnitMinor)
    val manual = manualUnitMinor?.takeIf { listUnitMinor == null || it != listUnitMinor }
    if (manual != null) rows += Row(Kind.MANUAL, manual, deltaMinor = listUnitMinor?.let { manual - it })
    val unit = manual ?: listUnitMinor ?: 0L
    val base = lineMinor(qtyMilli, unit)
    val off = promos.filter { it.discountMinor > 0 }
    if (off.isNotEmpty() || base != finalLineMinor) rows += Row(Kind.BASE, base)
    off.forEach { rows += Row(Kind.PROMO, -it.discountMinor, it.name) }
    val rounding = finalLineMinor - (base - off.sumOf { it.discountMinor })
    if (rounding != 0L) rows += Row(Kind.ROUNDING, rounding)
    rows += Row(Kind.TOTAL, finalLineMinor)
    return rows
  }
}
