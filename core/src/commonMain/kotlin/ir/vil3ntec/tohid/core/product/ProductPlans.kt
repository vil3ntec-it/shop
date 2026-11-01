package ir.vil3ntec.tohid.core.product

/*
 *  بسته یا کیت (PRD-10) و قیمتِ زمان‌بندی‌شده (PRD-13) — §۶.۳.
 *
 *  ── کیت ─────────────────────────────────────────────────────────────
 *  کالایی با قیمتِ خودش که از چند کالای دیگر ساخته شده («سبدِ رمضان» = ۲ روغن + ۵ کیلو برنج). موجودیِ
 *  کیت خودش نیست: «چند کیتِ کامل از اجزای موجود ساخته می‌شود» = کمینهٔ (موجودیِ جزء ÷ مقدارِ جزء)، رو به
 *  پایین. فروشِ کیت اجزا را کم می‌کند (برنامه اجزا را روی قلمِ فاکتور نگه می‌دارد تا تغییرِ بعدیِ ترکیب
 *  فاکتورِ گذشته را عوض نکند). کیتِ تو در تو و کیتی که جزءِ خودش است پذیرفته نیست.
 *  کادرِ فرم: هر خط «نام یا بارکد × مقدار» («×»، «*» یا «:»؛ مقدارِ بی‌عدد = ۱).
 *
 *  ── قیمتِ زمان‌بندی‌شده ─────────────────────────────────────────────
 *  چند بازهٔ «از … تا …» (هر دو روز شامل) با قیمتِ فروشِ خودش؛ در بازه همان قیمت، بیرون از همهٔ بازه‌ها
 *  قیمتِ عادی. بازه‌ها هم‌پوشان نیستند تا «کدام قیمت؟» یک جواب داشته باشد. تاریخ‌ها `yyyy-MM-dd` (مقایسهٔ
 *  رشته‌ای درست است)؛ خواندنِ تاریخِ خورشیدیِ کادر کارِ برنامه است (`date`).
 *  کادرِ فرم: هر خط «از تا تا = قیمت».
 *
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `ProductPlansTest`.
 */
object ProductKit {

  const val MAX_PARTS = 20

  /** یک جزء: شناسهٔ کالا و مقدار به هزارمِ واحدِ همان کالا */
  data class Part(val productId: String, val qtyMilli: Long)

  enum class Problem { UNKNOWN, SELF, NESTED, BAD_QTY, TOO_MANY }

  data class Parsed(val parts: List<Part>, val problems: List<Pair<String, Problem>>)

  /** کیتِ کامل که از اجزای موجود ساخته می‌شود؛ کیتِ بی جزء یا جزءِ بی موجودی ⇒ ۰ */
  fun available(parts: List<Part>, stockMilli: (String) -> Long): Long {
    if (parts.isEmpty()) return 0
    return parts.minOf { p -> if (p.qtyMilli <= 0) 0L else maxOf(0L, stockMilli(p.productId)) / p.qtyMilli }
  }

  /** مقدارِ هر جزء برای [kitsMilli] کیت (هزارم)، با گردِ همان `mulDivRound` ِ هسته */
  fun demand(parts: List<Part>, kitsMilli: Long): List<Pair<String, Long>> =
    parts.map { it.productId to (2 * kitsMilli * it.qtyMilli + 1000).floorDiv(2000L) }

  /**
   *  کادرِ فرم ⇒ اجزا. [resolve]: نام یا بارکد ⇒ شناسهٔ کالا؛ [isKit]: این کالا خودش کیت است؟ تکرارِ
   *  یک کالا جمع می‌شود. خطِ نادرست جدا گفته می‌شود و در اجزا نمی‌آید.
   */
  fun parse(text: String, selfId: String?, resolve: (String) -> String?, isKit: (String) -> Boolean): Parsed {
    val order = ArrayList<String>()
    val qty = HashMap<String, Long>()
    val bad = ArrayList<Pair<String, Problem>>()
    for (raw in text.split('\n', '\r')) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      val cut = line.indexOfLast { it == '×' || it == '*' || it == ':' }
      val name = (if (cut < 0) line else line.substring(0, cut)).trim()
      val amount = if (cut < 0) "1" else line.substring(cut + 1).trim()
      val q = DecimalText.scaled(amount.ifEmpty { "1" }, 3)
      val id = resolve(name)
      when {
        id == null -> bad += line to Problem.UNKNOWN
        id == selfId -> bad += line to Problem.SELF
        isKit(id) -> bad += line to Problem.NESTED
        q == null || q <= 0 -> bad += line to Problem.BAD_QTY
        id !in qty && order.size == MAX_PARTS -> bad += line to Problem.TOO_MANY
        else -> { if (id !in qty) order += id; qty[id] = (qty[id] ?: 0L) + q }
      }
    }
    return Parsed(order.map { Part(it, qty.getValue(it)) }, bad)
  }

  /** نام یا بارکد ⇒ شناسه، با همان قاعدهٔ کادرِ «جایگزین‌ها» (بارکدِ کامل، سپس نامِ دقیق با کلیدِ مقایسه) */
  fun resolver(catalog: List<ProductAlternatives.Ref>): (String) -> String? {
    val code = catalog.flatMap { r -> r.barcodes.map { it.trim() to r.id } }.asReversed().toMap()
    val name = catalog.map { ir.vil3ntec.tohid.core.text.TextNormalizer.compareKey(it.name) to it.id }.asReversed().toMap()
    return { t -> code[DigitLatin.latin(t.trim())] ?: name[ir.vil3ntec.tohid.core.text.TextNormalizer.compareKey(t)] }
  }

  /** اجزا ⇒ کادرِ فرم؛ [label] نامِ کالا، [amount] مقدارِ خوانا */
  fun format(parts: List<Part>, label: (String) -> String, amount: (Long) -> String): String =
    parts.joinToString("\n") { "${label(it.productId)} × ${amount(it.qtyMilli)}" }
}

object PriceSchedule {

  const val MAX = 10

  /** بازهٔ «از تا» (هر دو شامل، `yyyy-MM-dd`) با قیمتِ فروش به یک‌صدم */
  data class Window(val from: String, val to: String, val priceMinor: Long)

  enum class Problem { BAD_LINE, BAD_DATE, END_BEFORE_START, BAD_PRICE, OVERLAP, TOO_MANY }

  data class Parsed(val windows: List<Window>, val problems: List<Pair<String, Problem>>)

  private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")

  /** بازه‌ای که [today] در آن است؛ بازه‌ها هم‌پوشان نیستند پس حداکثر یکی */
  fun active(windows: List<Window>, today: String): Window? = windows.firstOrNull { today >= it.from && today <= it.to }

  /** قیمتِ امروز: بازهٔ فعال، وگرنه [baseMinor] */
  fun price(baseMinor: Long, windows: List<Window>, today: String): Long = active(windows, today)?.priceMinor ?: baseMinor

  /**
   *  کادرِ فرم ⇒ بازه‌ها، به ترتیبِ آغاز. [date]: متنِ تاریخ (خورشیدی یا میلادی) ⇒ `yyyy-MM-dd` یا null.
   *  هم‌پوشانی با بازهٔ پیشین ⇒ خطِ دوم ایراد دارد و نمی‌آید.
   */
  fun parse(text: String, date: (String) -> String?): Parsed {
    val got = ArrayList<Pair<String, Window>>()
    val bad = ArrayList<Pair<String, Problem>>()
    for (raw in text.split('\n', '\r')) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      val eq = line.lastIndexOf('=')
      val span = if (eq < 0) emptyList() else line.substring(0, eq).split("تا", "..", " to ").map { it.trim() }
      if (span.size != 2) { bad += line to Problem.BAD_LINE; continue }
      val from = date(span[0])?.takeIf { DAY.matches(it) }
      val to = date(span[1])?.takeIf { DAY.matches(it) }
      val price = DecimalText.scaled(line.substring(eq + 1).trim(), 2)
      when {
        from == null || to == null -> bad += line to Problem.BAD_DATE
        to < from -> bad += line to Problem.END_BEFORE_START
        price == null || price < 0 -> bad += line to Problem.BAD_PRICE
        got.size == MAX -> bad += line to Problem.TOO_MANY
        else -> got += line to Window(from, to, price)
      }
    }
    val out = ArrayList<Window>()
    for ((line, w) in got.sortedWith(compareBy({ it.second.from }, { it.second.to }))) {
      if (out.isNotEmpty() && w.from <= out.last().to) bad += line to Problem.OVERLAP else out += w
    }
    return Parsed(out, bad)
  }

  /** بازه‌ها ⇒ کادرِ فرم؛ [show] تاریخِ خوانا، [money] قیمتِ خوانا */
  fun format(windows: List<Window>, show: (String) -> String, money: (Long) -> String): String =
    windows.joinToString("\n") { "${show(it.from)} تا ${show(it.to)} = ${money(it.priceMinor)}" }
}
