package ir.vil3ntec.tohid.core.product

/*
 *  چاپِ برچسبِ کالا — PRD-23 (§۶.۳، S24): کدام کالا، چند نسخه، به چه ترتیب.
 *
 *  • هر کالا ۱ تا ۹۹ نسخه (همان سقفِ `Labels.tspl`)؛ ۰ یا منفی ⇒ آن کالا چاپ نمی‌شود.
 *  • کالای وزنی (کیلو/گرم/لیتر…) یک برچسب می‌گیرد، هر چه نوشته شود: مقدارش شمارشی نیست.
 *  • کالای بی‌بارکد برچسبِ بارکددار نمی‌گیرد (`missingCode`)؛ برچسبِ قفسه بی بارکد هم چاپ می‌شود.
 *  • سقفِ یک کار ۲۴۰ برچسب (ده برگهٔ A4) — اسکنرِ چاپگر یا صفِ چاپ با یک فشارِ اشتباه پر نشود.
 *  • ترتیب همان ترتیبِ انتخاب؛ نسخه‌های هر کالا پشتِ هم (برگهٔ A4 کالا به کالا بریده می‌شود).
 *  منطقِ خالص؛ آزمون: `LabelPlanTest` (commonTest).
 */
object LabelPlan {

  const val MAX_COPIES = 99
  const val MAX_TOTAL = 240

  enum class Kind {
    /** برچسبِ ۴۰×۳۰ ِ کالا: نام، قیمت، بارکد و کد */
    BARCODE,
    /** برچسبِ قفسه: نام و قیمتِ درشت؛ بارکدِ کوچک اگر هست */
    SHELF,
  }

  data class Item(val productId: String, val copies: Int, val hasCode: Boolean, val weighed: Boolean = false)

  data class Plan(
    /** شناسهٔ کالا برای هر برچسب، به ترتیبِ چاپ */
    val labels: List<String>,
    /** کالاهای انتخاب‌شده‌ای که بارکد ندارند و برچسبِ بارکددار نگرفتند */
    val missingCode: List<String>,
    /** چند برچسب از سقف بیرون ماند */
    val dropped: Int,
  ) {
    val isEmpty: Boolean get() = labels.isEmpty()
  }

  fun plan(items: List<Item>, kind: Kind, maxTotal: Int = MAX_TOTAL): Plan {
    val out = ArrayList<String>()
    val missing = ArrayList<String>()
    var dropped = 0
    for (it in items) {
      if (it.copies <= 0) continue
      if (kind == Kind.BARCODE && !it.hasCode) { missing += it.productId; continue }
      val n = if (it.weighed) 1 else it.copies.coerceAtMost(MAX_COPIES)
      val room = (maxTotal - out.size).coerceAtLeast(0)
      repeat(minOf(n, room)) { _ -> out += it.productId }
      dropped += (n - room).coerceAtLeast(0)
    }
    return Plan(out, missing, dropped)
  }

  /** نسخه‌های پیاپیِ هر کالا: [a, a, b] ⇒ [(a, 2), (b, 1)] — برای یک فرمانِ چاپگر با `PRINT n` */
  fun runs(labels: List<String>): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    for (id in labels) {
      val last = out.lastOrNull()
      if (last != null && last.first == id) out[out.size - 1] = id to last.second + 1 else out += id to 1
    }
    return out
  }
}
