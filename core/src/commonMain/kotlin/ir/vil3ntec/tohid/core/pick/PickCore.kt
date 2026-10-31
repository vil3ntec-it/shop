package ir.vil3ntec.tohid.core.pick

/**
 *  منطقِ خالصِ جمع‌آوری و بسته‌بندی (PCK، v3 §۶.۱۷) — دوقلوی `server/src/lib/pick-core.js`.
 *
 *  ⛔ هر دو با بردارِ مرجعِ `core/test-vectors/pick-v1.json` سنجیده می‌شوند (`PickCoreVectorsTest` و
 *  `server/test/pick-core.test.js`). یکی را بی دیگری و بردار عوض نکن. سرور تصمیمِ نهایی را می‌گیرد؛
 *  برنامه همین را برای ترتیبِ برگه، بازخوردِ فوریِ اسکن/وزن (حتی بی‌اینترنت) و پیش‌نمایشِ جمع می‌خواند.
 *
 *  - ترتیب (PCK-01): راهرو ⇐ قفسه ⇐ طبقه (عدد عددی، متن نویسه‌به‌نویسه)؛ جای خالی پس از پر؛ بی‌جا آخر؛
 *    سپس نام و شمارهٔ قلم.
 *  - بارکد (PCK-02): رقمِ فارسی/عربی، فاصله و خطِ تیره پاک؛ GTINهای هم‌ارز (UPC-A ⇔ EAN-13) یکی.
 *  - وزن (PCK-04): ±حدِ مجاز (پیش‌فرض ۱۰٪) ⇒ `OK`؛ بیشتر ⇒ `OVER` (رد)؛ کمتر ⇒ `SHORT`.
 *  - حساب (PCK-07): پیدانشده/حذف ⇒ صفر؛ «کم» ⇒ مقدارِ برداشته؛ تخفیف تا جمعِ تازه؛ ارسال همان.
 *  - شاخص‌ها (PCK-13): نرخ به ده‌هزارم؛ بی داده ⇒ null.
 *
 *  commonMain، بی `java.*` (راهِ آیفون، §۴۰.۳).
 */
object PickCore {

  const val DEFAULT_TOLERANCE_BP = 1000
  const val MAX_TOLERANCE_BP = 5000
  private const val MAX_LOC = 20
  private const val MAX_BARCODE = 64

  /** واحدهایی که «وزنی»اند — همان فهرستِ سرور */
  val WEIGHT_UNITS: List<String> = listOf(
    "کیلو", "کیلوگرم", "کیلوگرام", "کیلو گرم", "گرم", "kg", "kgs", "kilo", "kilogram", "g", "gr", "gram",
  )

  // ═══════════════════════════ گردکردن ═══════════════════════════

  /** قاعدهٔ یگانهٔ §۳.۴: `floorDiv(qty.milli × unit.minor + 50_000, 100_000) × 100` */
  fun lineTotalMinor(qtyMilli: Long, unitPriceMinor: Long): Long {
    if (qtyMilli != 0L && unitPriceMinor != 0L) {
      val limit = (Long.MAX_VALUE - 50_000L) / kotlin.math.abs(qtyMilli)
      require(kotlin.math.abs(unitPriceMinor) <= limit) { "مبلغِ ردیف بیش از حد بزرگ است" }
    }
    val out = (qtyMilli * unitPriceMinor + 50_000L).floorDiv(100_000L) * 100L
    require(out in -MAX_SAFE..MAX_SAFE) { "مبلغِ ردیف بیش از حد بزرگ است" }
    return out
  }

  /** همان سقفِ `Number.MAX_SAFE_INTEGER` ِ سرور */
  private const val MAX_SAFE = 9_007_199_254_740_991L

  /** گردکردنِ `Math.round(a / b)` ِ JS (نیمه به سمتِ +∞)، بی ممیزِ شناور */
  internal fun roundDiv(a: Long, b: Long): Long = (2 * a + b).floorDiv(2 * b)

  // ═══════════════════════════ متن ═══════════════════════════

  /** رقمِ فارسی (۰–۹) و عربی (٠–٩) ⇒ لاتین */
  fun latin(s: String): String = buildString(s.length) {
    for (ch in s) append(
      when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰')
        in '٠'..'٩' -> '0' + (ch - '٠')
        else -> ch
      },
    )
  }

  /** همان `\s` ِ JS (فاصله‌های یونیکد هم) */
  private val SPACES = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

  private fun squash(s: String): String = s.replace(SPACES, " ").trim { it == ' ' }

  // ═══════════════════════════ PCK-01 ═══════════════════════════

  /** یک تکهٔ جا (راهرو/قفسه/طبقه): رقمِ لاتین، بی فاصلهٔ دو سر، حداکثر ۲۰ نویسه */
  fun locPart(v: String): String = squash(latin(v)).take(MAX_LOC)

  private val TOKENS = Regex("[0-9]+|[^0-9]+")

  /** «۱۰» پس از «۹»؛ «A2» پیش از «A10»؛ متن نویسه‌به‌نویسه */
  fun compareNatural(a: String, b: String): Int {
    val ta = TOKENS.findAll(a).map { it.value }.toList()
    val tb = TOKENS.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(ta.size, tb.size)) {
      val x = ta[i]
      val y = tb[i]
      val dx = x[0] in '0'..'9'
      val dy = y[0] in '0'..'9'
      if (dx && dy) {
        val sx = x.trimStart('0').ifEmpty { "0" }
        val sy = y.trimStart('0').ifEmpty { "0" }
        if (sx.length != sy.length) return if (sx.length < sy.length) -1 else 1
        if (sx != sy) return if (sx < sy) -1 else 1
      } else if (x != y) {
        return if (x < y) -1 else 1
      }
    }
    return if (ta.size == tb.size) 0 else if (ta.size < tb.size) -1 else 1
  }

  /** قلمِ برگه برای ترتیب */
  data class SheetLine(
    val lineNo: Int,
    val name: String,
    val aisle: String = "",
    val shelf: String = "",
    val level: String = "",
  )

  /** ترتیبِ برگهٔ جمع‌آوری ⇒ شمارهٔ قلم‌ها */
  fun pickOrder(lines: List<SheetLine>): List<Int> {
    val rows = lines.map { it.copy(aisle = locPart(it.aisle), shelf = locPart(it.shelf), level = locPart(it.level)) }
    fun part(x: String, y: String): Int = when {
      x == y -> 0
      x.isEmpty() -> 1
      y.isEmpty() -> -1
      else -> compareNatural(x, y)
    }
    fun hasLoc(l: SheetLine) = l.aisle.isNotEmpty() || l.shelf.isNotEmpty() || l.level.isNotEmpty()
    return rows.sortedWith { a, b ->
      val ka = hasLoc(a)
      val kb = hasLoc(b)
      when {
        ka != kb -> if (ka) -1 else 1
        else -> part(a.aisle, b.aisle).takeIf { it != 0 }
          ?: part(a.shelf, b.shelf).takeIf { it != 0 }
          ?: part(a.level, b.level).takeIf { it != 0 }
          ?: a.name.compareTo(b.name).let { if (it < 0) -1 else if (it > 0) 1 else 0 }.takeIf { it != 0 }
          ?: a.lineNo.compareTo(b.lineNo)
      }
    }.map { it.lineNo }
  }

  // ═══════════════════════════ PCK-02 ═══════════════════════════

  private val BARCODE_GAPS = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF-]+")
  private val GTIN = Regex("^[0-9]{8,14}$")

  /** بارکد ⇒ شکلِ سنجش: رقمِ لاتین، بی فاصله و خطِ تیره، حروفِ بزرگ، حداکثر ۶۴ */
  fun normBarcode(v: String): String = latin(v).replace(BARCODE_GAPS, "").uppercase().take(MAX_BARCODE)

  private fun gtinKey(code: String): String = if (GTIN.matches(code)) code.trimStart('0') else code

  /** آیا بارکدِ اسکن‌شده مالِ همین کالاست؟ کالای بی‌بارکد ⇒ هرگز (تأییدِ دستی جداست) */
  fun barcodeMatches(productBarcodes: List<String>, scanned: String): Boolean {
    val s = normBarcode(scanned)
    if (s.isEmpty()) return false
    val want = gtinKey(s)
    return productBarcodes.any { b ->
      val n = normBarcode(b)
      n.isNotEmpty() && (n == s || gtinKey(n) == want)
    }
  }

  // ═══════════════════════════ PCK-04 ═══════════════════════════

  fun isWeighedUnit(unit: String): Boolean = squash(unit).lowercase() in WEIGHT_UNITS

  enum class Weigh(val key: String) { OK("ok"), SHORT("short"), OVER("over") }

  private fun clampBp(bp: Int) = bp.coerceIn(0, MAX_TOLERANCE_BP)

  /** بیشترین وزنِ پذیرفتنی (هزارم) */
  fun maxWeighMilli(orderedMilli: Long, toleranceBp: Int = DEFAULT_TOLERANCE_BP): Long =
    orderedMilli + (orderedMilli * clampBp(toleranceBp)).floorDiv(10_000L)

  /** کمترین وزنی که هنوز «کامل» است (هزارم) */
  fun minWeighMilli(orderedMilli: Long, toleranceBp: Int = DEFAULT_TOLERANCE_BP): Long =
    orderedMilli - (orderedMilli * clampBp(toleranceBp)).floorDiv(10_000L)

  fun weighVerdict(orderedMilli: Long, actualMilli: Long, toleranceBp: Int = DEFAULT_TOLERANCE_BP): Weigh = when {
    actualMilli > maxWeighMilli(orderedMilli, toleranceBp) -> Weigh.OVER
    actualMilli < minWeighMilli(orderedMilli, toleranceBp) -> Weigh.SHORT
    else -> Weigh.OK
  }

  // ═══════════════════════════ PCK-07 ═══════════════════════════

  /** قلم برای حساب — `status` همان کلیدهای سرور (`pending`، `picked`، `short`، `not_found`، …) */
  data class SettleLine(
    val lineNo: Int,
    val status: String,
    val orderedQtyMilli: Long,
    val pickedQtyMilli: Long,
    val unitPriceMinor: Long,
    val originalTotalMinor: Long,
  )

  data class SettledLine(val lineNo: Int, val qtyMilli: Long, val totalMinor: Long)

  data class Settlement(
    val lines: List<SettledLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val deliveryFeeMinor: Long,
    val totalMinor: Long,
    val deductedMinor: Long,
    val removedLines: List<Int>,
    val changedLines: List<Int>,
    val pending: Int,
  )

  /** مقدارِ فروختنیِ یک قلم پس از جمع‌آوری */
  fun finalQty(status: String, pickedQtyMilli: Long): Long =
    if (status == "picked" || status == "short") maxOf(0L, pickedQtyMilli) else 0L

  fun settle(lines: List<SettleLine>, discountMinor: Long = 0, deliveryFeeMinor: Long = 0): Settlement {
    val out = ArrayList<SettledLine>(lines.size)
    val removed = ArrayList<Int>()
    val changed = ArrayList<Int>()
    var subtotal = 0L
    var original = 0L
    var pending = 0
    for (l in lines) {
      if (l.status == "pending") pending += 1
      val qty = finalQty(l.status, l.pickedQtyMilli)
      val total = if (qty > 0) lineTotalMinor(qty, l.unitPriceMinor) else 0L
      out += SettledLine(l.lineNo, qty, total)
      subtotal += total
      original += l.originalTotalMinor
      if (qty == 0L) removed += l.lineNo
      else if (qty != l.orderedQtyMilli || total != l.originalTotalMinor) changed += l.lineNo
    }
    val discount = discountMinor.coerceAtLeast(0).coerceAtMost(subtotal)
    val fee = deliveryFeeMinor.coerceAtLeast(0)
    return Settlement(
      lines = out,
      subtotalMinor = subtotal,
      discountMinor = discount,
      deliveryFeeMinor = fee,
      totalMinor = subtotal - discount + fee,
      deductedMinor = maxOf(0L, original - subtotal),
      removedLines = removed,
      changedLines = changed,
      pending = pending,
    )
  }

  // ═══════════════════════════ PCK-13 ═══════════════════════════

  data class Run(val startedAt: Long?, val packedAt: Long?, val statuses: List<String>)

  data class Metrics(
    val orders: Int,
    val lines: Int,
    val found: Int,
    val notFound: Int,
    val short: Int,
    val substituted: Int,
    val avgPickMs: Long?,
    val medianPickMs: Long?,
    val foundRateBp: Long?,
    val substitutionRateBp: Long?,
  )

  fun metrics(picks: List<Run>): Metrics {
    val done = picks.filter { it.packedAt != null && it.startedAt != null && it.packedAt >= it.startedAt }
    val times = done.map { it.packedAt!! - it.startedAt!! }.sorted()
    var lines = 0
    var found = 0
    var notFound = 0
    var short = 0
    var substituted = 0
    for (p in done) for (s in p.statuses) {
      lines += 1
      when (s) {
        "picked" -> found += 1
        "short" -> { found += 1; short += 1 }
        "substituted" -> substituted += 1
        "not_found", "removed" -> notFound += 1
      }
    }
    fun rate(x: Int): Long? = if (lines == 0) null else roundDiv(x * 10_000L, lines.toLong())
    val median = when {
      times.isEmpty() -> null
      times.size % 2 == 1 -> times[(times.size - 1) / 2]
      else -> roundDiv(times[times.size / 2 - 1] + times[times.size / 2], 2)
    }
    return Metrics(
      orders = done.size,
      lines = lines,
      found = found,
      notFound = notFound,
      short = short,
      substituted = substituted,
      avgPickMs = if (times.isEmpty()) null else roundDiv(times.sum(), times.size.toLong()),
      medianPickMs = median,
      foundRateBp = rate(found),
      substitutionRateBp = rate(substituted),
    )
  }
}
