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
  const val MAX_NEAR_DAYS = 365

  /**
   *  S22 — «نزدیک به انقضا» ِ همین دستگاه (`ExpiryPref`، «قابلِ تنظیم»): هشدار، انبار، داشبورد و گزارش همین را
   *  می‌خوانند. تا تنظیم نشده [NEAR_DAYS] است ⇒ همان امروز.
   */
  var nearDays: Int = NEAR_DAYS
    set(v) { field = if (v in 1..MAX_NEAR_DAYS) v else NEAR_DAYS }

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

  /**
   *  ردیفی که محموله است: ورودِ واقعیِ کالا، و (INV-21) خروجیِ بسته‌بندیِ دوباره (`Repack`: دانه‌های کارتنِ شکسته یا
   *  بستهٔ ساخته‌شده) که سری و انقضای همان کارتن/اجزا را دارد. ردیف‌های اصلاحِ دیگر محموله نیستند.
   */
  internal fun isLot(w: WarehouseEntry): Boolean = w.units > 0 && (!w.isAdjustment || w.adjKind == Repack.KIND)

  /**
   *  INV-21 — مصرفِ سری‌دارِ تولید: ردیفِ منفیِ بسته‌بندی (`Repack`) با `lotId` ِ ناتهی — فقط با «دستورِ ساخت و سریِ تولید»
   *  نوشته می‌شود — از **همان** محموله (انقضا و سری ِ همان ردیف) کم می‌شود، نه به FEFO؛ همان سری که `StockLedger` برمی‌دارد.
   */
  internal fun isLotUse(w: WarehouseEntry): Boolean = w.units < 0 && w.isAdjustment && w.adjKind == Repack.KIND && !w.lotId.isNullOrEmpty()

  /**
   *  گنجایشِ هر محموله پس از مصرفِ سری‌دار ([isLotUse]): مصرفِ هر (انقضا، سری) از نخستین ردیف‌های همان کلید کم می‌شود
   *  (هرگز زیرِ صفر). بی چنین ردیفی ⇒ همان `units` (همان حسابِ امروز، عددبه‌عدد).
   */
  private fun capacities(d: ShopData, productId: String, entries: List<WarehouseEntry>): List<Pair<WarehouseEntry, Double>> =
    capacities(d.warehouseEntries.filter { it.productId == productId && isLotUse(it) }, entries)

  /**
   *  همان [capacities] با ردیف‌های مصرفِ سری‌دارِ **همین کالا** ([uses]، به ترتیبِ دفتر) که از پیش جدا شده‌اند — [lots] برای
   *  همهٔ کالاها یک بار جدا می‌کند، نه یک گذر روی همهٔ ورودها برای هر کالا (POS-42: با ۲۰ هزار کالا و ۴۰ هزار ورود
   *  هر سبد با قاعدهٔ «نزدیکِ انقضا» ~۹ ثانیه می‌شد). همان ردیف‌ها به همان ترتیب ⇒ همان عدد.
   */
  private fun capacities(uses: List<WarehouseEntry>, entries: List<WarehouseEntry>): List<Pair<WarehouseEntry, Double>> {
    val use = HashMap<Pair<String, String>, Double>()
    for (w in uses) {
      val k = w.expiryDate to w.batch.trim()
      use[k] = (use[k] ?: 0.0) - w.units
    }
    if (use.isEmpty()) return entries.map { it to it.units }
    return entries.map { e ->
      val k = e.expiryDate to e.batch.trim()
      val u = use[k] ?: 0.0
      val cut = minOf(e.units, u)
      if (cut > 0) use[k] = u - cut
      e to (e.units - maxOf(0.0, cut))
    }
  }

  /** ماندهٔ هر محمولهٔ انقضادار، برای همهٔ کالاها */
  fun lots(d: ShopData, today: String): List<Lot> {
    val out = ArrayList<Lot>()
    val byProduct = d.warehouseEntries
      .filter(::isLot)
      .groupBy { it.productId }
    val products = d.products.associateBy { it.id }
    val uses = d.warehouseEntries.filter(::isLotUse).groupBy { it.productId }
    for ((productId, entries) in byProduct) {
      if (entries.none { it.expiryDate.isNotBlank() }) continue
      val product = products[productId] ?: continue
      var left = ShopStore.stock(d, productId)
      if (left <= 0) continue
      //  از دیرترین به زودترین؛ بی‌انقضا دیرتر از همه
      val ordered = capacities(uses[productId].orEmpty(), entries).sortedWith(
        compareByDescending<Pair<WarehouseEntry, Double>> { it.first.expiryDate.isBlank() }.thenByDescending { it.first.expiryDate }
      )
      for ((e, cap) in ordered) {
        if (left <= 0) break
        val take = minOf(cap, left)
        left -= take
        if (e.expiryDate.isNotBlank() && take > 0) {
          out += Lot(e, product, take, daysLeft(e.expiryDate, today))
        }
      }
    }
    return out.sortedBy { it.daysLeft }
  }

  /** یک تکه از برداشت: سری و انقضای محموله (خالی ⇒ بی انقضا یا بی محموله) و مقدار به هزارم */
  data class Take(val expiryDate: String, val batch: String, val qtyMilli: Long)

  /**
   *  INV-21 — برداشتنِ [qtyMilli] از [productId] به همان FEFO ِ [lots]: موجودیِ امروز روی دیرترین محموله‌ها نشسته، پس
   *  برداشت از **زودترین** ماندهٔ انقضادار است (هم‌انقضاها به ترتیبِ سری)، و بی‌انقضا و موجودیِ بی محموله آخر — یک تکهٔ
   *  خالی. جمعِ تکه‌ها همیشه [qtyMilli] است؛ تکه‌های هم‌سری‌وهم‌انقضا یکی می‌شوند. فقط‌خواندنی.
   */
  fun take(d: ShopData, productId: String, qtyMilli: Long): List<Take> {
    if (qtyMilli <= 0) return emptyList()
    val entries = capacities(d, productId, d.warehouseEntries.filter { it.productId == productId && isLot(it) })
      .sortedWith(compareByDescending<Pair<WarehouseEntry, Double>> { it.first.expiryDate.isBlank() }.thenByDescending { it.first.expiryDate })
    var left = Math.round(ShopStore.stock(d, productId) * 1000)
    val held = LinkedHashMap<Pair<String, String>, Long>()
    for ((e, cap) in entries) {
      if (left <= 0) break
      val q = minOf(Math.round(cap * 1000), left)
      left -= q
      if (e.expiryDate.isNotBlank() && q > 0) {
        val k = e.expiryDate to e.batch.trim()
        held[k] = (held[k] ?: 0L) + q
      }
    }
    val out = ArrayList<Take>()
    var need = qtyMilli
    for ((k, q) in held.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))) {
      if (need <= 0) break
      val t = minOf(q, need)
      out += Take(k.first, k.second, t)
      need -= t
    }
    if (need > 0) out += Take("", "", need)
    return out
  }

  /** ماندهٔ منقضی‌شدهٔ یک کالا (به FEFO) — برای سیاستِ «کالای منقضی فروخته نشود» */
  fun expiredRemaining(d: ShopData, productId: String, today: String): Double =
    lots(d, today).filter { it.product.id == productId && it.expired }.sumOf { it.remaining }

  /** منقضی‌شده یا تا `days` روزِ دیگر — همان چیزی که هشدار و گزارش نشان می‌دهند */
  fun alerts(d: ShopData, today: String, days: Int = nearDays): List<Lot> =
    lots(d, today).filter { it.daysLeft <= days }

  /**
   *  S22 — مقدارِ منقضی‌ای که این سبد برمی‌دارد و تأیید می‌خواهد (کالا ⇐ هزارمِ واحد): همان سنجشِ `blockExpired`
   *  (سالم = فروختنی منهای منقضی)؛ آنچه از سالم بیشتر است تا سقفِ ماندهٔ منقضی.
   */
  fun need(d: ShopData, cart: List<SalesEngine.CartLine>, today: String, ownReservation: String? = null): Map<String, Long> = buildMap {
    cart.filter { !it.free }.groupBy { it.productId }.forEach { (id, lines) ->
      val expired = expiredRemaining(d, id, today)
      if (expired <= 0) return@forEach
      val healthy = kotlin.math.max(0.0, ProductInfoMap.sellable(d, id, ownReservation) - expired)
      val take = minOf(expired, lines.sumOf { it.quantity } - healthy)
      if (take > 0.0005) put(id, Math.round(take * 1000))
    }
  }

  /** «از «شیر» ۲ واحدِ منقضی» */
  fun needText(d: ShopData, need: Map<String, Long>): String = need.entries.joinToString("، ") { (id, q) ->
    "از «${d.products.find { it.id == id }?.name.orEmpty()}» ${ir.vil3ntec.tohid.qty(q / 1000.0)} واحدِ منقضی"  // داده: جملهٔ یادداشتِ سابقه و تأییدِ منقضی
  }
}
