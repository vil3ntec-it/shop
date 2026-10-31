package ir.vil3ntec.tohid.core.pick

/**
 *  جایگزینی هنگامِ جمع‌آوری (PCK-05/06، v3 §۶.۱۷؛ S37) — دوقلوی بخشِ «PCK-05/06» ِ
 *  `server/src/lib/pick-core.js`.
 *
 *  ⛔ هر دو با بردارِ مرجعِ `core/test-vectors/pick-sub-v1.json` سنجیده می‌شوند (`PickSubstituteVectorsTest` و
 *  `server/test/pick-sub-core.test.js`). یکی را بی دیگری و بردار عوض نکن. تصمیمِ نهایی با سرور است؛ برنامه
 *  همین را برای نشان دادنِ دلیلِ «نه» و پیش‌نمایشِ مبلغ (حتی بی‌اینترنت) می‌خواند.
 *
 *  - امن (PCK-06): همان کالا نه؛ دسته معلوم و یکی؛ اگر اصل زیردسته دارد همان زیردسته؛ حلال/غیرحلال/نامعلوم
 *    یکی (نامعلوم با حلال یکی نیست)؛ وزنی با شمارشی نه؛ جایگزینِ بی قیمت نه.
 *  - قیمتِ شفاف (PCK-06): مشتری قیمتِ واحدِ کمتر را می‌پردازد، با همان گردکردنِ §۳.۴.
 *  - پیشنهاد (PCK-05): فقط امن‌ها؛ هم‌برند، سپس نزدیک‌ترین قیمت، سپس نام و شناسه.
 *  - مهلت (PCK-05): فقط «بهترین مشابه» یعنی پذیرش؛ «جایگزین نه»/«بازپرداخت» پیشنهاد نمی‌گیرد.
 *
 *  commonMain، بی `java.*` (راهِ آیفون، §۴۰.۳).
 */
object PickSubstitute {

  /** ترجیحِ جایگزینیِ مشتری (CHK-15) — همان کلیدهای سرور */
  val PREFERENCES: List<String> = listOf("", "best_match", "specific", "none", "refund")

  /** کالا برای سنجش؛ `priceMinor` ِ اصل = قیمتِ واحدِ سفارش */
  data class Item(
    val productId: String,
    val category: String = "",
    val subcategory: String = "",
    val halal: String = "",
    val unit: String = "",
    val priceMinor: Long? = null,
    val brand: String = "",
    val name: String = "",
  )

  data class Charge(val chargedUnitPriceMinor: Long, val totalMinor: Long)

  /** همان `\s` ِ JS (فاصله‌های یونیکد هم) — مثلِ `PickCore` */
  private val SPACES = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

  /** `halal`، `non_halal` یا '' (نامعلوم) */
  fun halalOf(v: String): String = when (val s = v.trim().lowercase()) {
    "halal", "non_halal" -> s
    else -> ""
  }

  /** دسته برای سنجش: رقمِ لاتین، یک فاصله، بی فاصلهٔ دو سر، حروفِ کوچک */
  fun normCategory(v: String): String = PickCore.latin(v).replace(SPACES, " ").trim { it == ' ' }.lowercase()

  /** ⇒ null (مجاز) یا کدِ دلیل — همان کدهای سرور */
  fun block(orig: Item, cand: Item?): String? {
    if (cand == null || cand.productId.isEmpty()) return "not_found"
    if (orig.productId.isNotEmpty() && cand.productId == orig.productId) return "same_product"
    val ca = normCategory(orig.category)
    val cb = normCategory(cand.category)
    if (ca.isEmpty() || cb.isEmpty()) return "category_unknown"
    if (ca != cb) return "category_mismatch"
    val sa = normCategory(orig.subcategory)
    if (sa.isNotEmpty() && normCategory(cand.subcategory) != sa) return "subcategory_mismatch"
    if (halalOf(orig.halal) != halalOf(cand.halal)) return "halal_mismatch"
    if (PickCore.isWeighedUnit(orig.unit) != PickCore.isWeighedUnit(cand.unit)) return "unit_mismatch"
    val p = cand.priceMinor
    if (p == null || p <= 0L) return "no_price"
    return null
  }

  /** قیمتِ واحدِ کمتر × مقدار، با همان گردکردن */
  fun charge(origUnitPriceMinor: Long, candUnitPriceMinor: Long, qtyMilli: Long): Charge {
    val unit = minOf(origUnitPriceMinor, candUnitPriceMinor)
    return Charge(unit, PickCore.lineTotalMinor(qtyMilli, unit))
  }

  /** پیشنهادها به ترتیب (S37: دو تا) ⇒ شناسه‌ها */
  fun rank(orig: Item, cands: List<Item>, limit: Int = 2): List<String> {
    val brand = normCategory(orig.brand)
    val price = orig.priceMinor ?: 0L
    data class Row(val id: String, val name: String, val same: Int, val gap: Long)
    val ok = cands.filter { block(orig, it) == null }.map {
      Row(it.productId, it.name, if (brand.isNotEmpty() && normCategory(it.brand) == brand) 0 else 1,
        kotlin.math.abs((it.priceMinor ?: 0L) - price))
    }
    val sorted = ok.sortedWith(
      compareBy<Row> { it.same }.thenBy { it.gap }.thenComparator { a, b -> a.name.compareTo(b.name) }
        .thenComparator { a, b -> a.id.compareTo(b.id) },
    )
    return sorted.take(maxOf(0, limit)).map { it.id }
  }

  /** مشتری گفته «جایگزین نه» ⇒ پیشنهاد فرستاده نمی‌شود */
  fun proposable(preference: String): Boolean = preference != "none" && preference != "refund"

  /** پس از مهلت: فقط «بهترین مشابه» ⇒ `accept`؛ بقیه ⇒ `reject` */
  fun timeoutDecision(preference: String): String = if (preference == "best_match") "accept" else "reject"
}
