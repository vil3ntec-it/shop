package ir.vil3ntec.tohid.core.stock

/**
 *  فروش‌های حینِ شمارش خودکار لحاظ می‌شوند — INV-10 (§۶.۵، جریانِ S20).
 *
 *  شمردنِ یک کالا یک لحظه دارد (`countedAt`: آخرین تغییرِ عددِ همان کالا در برگه). هر حرکتی که **پس از** آن
 *  لحظه روی همان کالا نشسته (فروشِ صندوق −، برگشت یا رسید +) در عددِ شمرده نیست ولی در موجودیِ امروزِ
 *  برنامه هست. پس «مورد انتظار هنگامِ شمارش» = موجودیِ امروز − جمعِ نشانه‌دارِ آن حرکت‌ها، و اختلاف =
 *  شمرده − مورد انتظار. اصلاحِ همان اختلاف روی موجودیِ امروز ⇒ موجودی = شمرده + حرکت‌های پس از شمارش
 *  (۱۰ شمرده، سپس ۳ فروش ⇒ ۷، نه ۱۰ و نه کسریِ ساختگیِ ۳). فقط همان یک حرکتِ «اصلاحِ شمارش» ساخته می‌شود.
 *
 *  حرکتِ هم‌لحظه با شمارش (`at == countedAt`) پیش از شمارش شمرده می‌شود (در عدد هست). کالای بی لحظه
 *  (`countedAt` نیامده) همان رفتارِ امروز است: مقایسه با موجودیِ امروز. برگه‌های چند دستگاه: لحظهٔ هر
 *  کالا دیرترین برگه‌ای است که آن را شمرده — فروشِ میانِ دو برگه هرگز دو بار کم نمی‌شود؛ اگر واقعاً پس از
 *  شمارشِ برگهٔ زودتر بوده، اختلافش در صفحهٔ بازبینی دیده و دوباره شمرده می‌شود.
 *
 *  مقدار هزارم (عددِ صحیح). Kotlinِ خالص در commonMain (§۴۰.۳).
 */
object CountDuringSales {

  /** یک حرکتِ موجودی: نشانه‌دار (فروش منفی، برگشت/رسید مثبت) و لحظه به میلی‌ثانیه */
  data class Move(val productId: String, val at: Long, val qtyMilli: Long)

  data class Line(
    val productId: String,
    /** موجودیِ امروزِ برنامه */
    val systemNowMilli: Long,
    val countedMilli: Long,
    /** `null` ⇒ لحظه نیامده و مقایسه با موجودیِ امروز است */
    val countedAt: Long?,
    /** جمعِ نشانه‌دارِ حرکت‌های پس از شمارش */
    val movedAfterMilli: Long,
    /** مورد انتظار هنگامِ شمارش */
    val expectedMilli: Long,
  ) {
    /** اختلافی که به‌عنوانِ «اصلاحِ شمارش» روی موجودیِ امروز می‌نشیند */
    val diffMilli: Long get() = countedMilli - expectedMilli
    /** موجودی پس از ثبت */
    val afterMilli: Long get() = systemNowMilli + diffMilli
  }

  /** جمع با سنجشِ سرریز (بی `java.*`؛ commonMain) */
  private fun plus(a: Long, b: Long): Long {
    val r = a + b
    if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("count-during-sales: overflow")
    return r
  }

  /** جمعِ حرکت‌های هر کالا پس از لحظهٔ شمارشش؛ کالای بی لحظه نیست */
  fun movedAfter(countedAt: Map<String, Long>, moves: List<Move>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (m in moves) {
      val t = countedAt[m.productId] ?: continue
      if (m.at > t) out[m.productId] = plus(out[m.productId] ?: 0L, m.qtyMilli)
    }
    return out
  }

  /** هر کالای شمرده، به ترتیبِ ورودی؛ کالای شمرده‌نشده نمی‌آید (دست نمی‌خورد، INV-09) */
  fun lines(
    systemNow: Map<String, Long>,
    counted: Map<String, Long>,
    countedAt: Map<String, Long>,
    moves: List<Move>,
  ): List<Line> {
    val after = movedAfter(countedAt, moves)
    return counted.map { (pid, c) ->
      val sys = systemNow[pid] ?: 0L
      val a = after[pid] ?: 0L
      Line(pid, sys, c, countedAt[pid], a, plus(sys, -a))
    }
  }

  /** لحظهٔ هر کالا از چند برگه: دیرترین برگه‌ای که آن را شمرده */
  fun latest(sheets: List<Pair<Long, Set<String>>>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for ((at, ids) in sheets) for (id in ids) if (at > (out[id] ?: Long.MIN_VALUE)) out[id] = at
    return out
  }
}
