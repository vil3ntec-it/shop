package ir.vil3ntec.tohid.core.pay

/**
 *  پایانِ روز (RPT-04 «گزارشِ شیفت و پایانِ روز»، کنارِ X/Z ِ PAY-15) — جمعِ همهٔ شیفت‌های
 *  یک روزِ کاری، با تطبیق با دفتر.
 *
 *  خالص (commonMain، §۴۰.۳): نه ساعتِ دستگاه، نه دیسک. ورودی همان عددهای X/Z ِ هستهٔ شیفت
 *  (`ShiftReport`) است که برنامه برای هر شیفت می‌سازد، و رویدادهای پولیِ دفتر (`sale`، `refund`،
 *  `receipt` با ردیف‌های هر روش) که همان `PayFlow.ledgerEvents` است. روزِ هر لحظه را `dayOf`
 *  می‌گوید (ساعتِ دکان، همان ساعتِ گزارش‌ها).
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • شیفت مالِ روزِ کاریِ **باز شدنش** است (روزی که از نیمه‌شب رد شود مالِ روزِ شروعش است،
 *    همان قاعدهٔ `ReportClock`). شیفتِ باز با X ِ همین لحظه می‌آید و روز «بسته» نیست.
 *  • جمعِ هر روش = جمعِ شیفت‌ها؛ مورد انتظار، شمرده و اختلاف فقط جمعِ همان‌هایی که شیفت دارد
 *    (هیچ عددی ساخته نمی‌شود؛ «شمرده نشد» null می‌ماند، نه صفر). اختلاف هرگز اصلاح نمی‌شود.
 *  • تطبیق: رویدادهای دفترِ همان روز به تفکیکِ روش در برابرِ جمعِ شیفت‌ها. هر تفاوت پولی است که
 *    هیچ X/Z ِ این روز نمی‌شمارد (فروشِ بیرون از شیفت، شیفتی که از روز گذشت، یا رویدادی که پس از
 *    Z رسید) و با نامِ خودش نشان داده می‌شود؛ پنهان یا پخش نمی‌شود.
 *  • دو شیفتِ هم‌زمان یک فروش را دو بار می‌شمارند (هر شیفت پنجرهٔ زمانیِ دفتر است)؛ پس
 *    هم‌پوشانی جدا گزارش می‌شود و روز بسته حساب نمی‌شود.
 *  • جمعِ بیرون از ±(۲^۵۳−۱) خطا است ([ArithmeticException])، نه عددِ گردشده (§۲۸.۴).
 */
object DayClose {
  const val CASH = "cash"
  private const val MAX_SAFE = 9_007_199_254_740_991L

  private fun add(a: Long, b: Long): Long {
    val r = a + b
    if (r > MAX_SAFE || r < -MAX_SAFE || a > MAX_SAFE || a < -MAX_SAFE || b > MAX_SAFE || b < -MAX_SAFE) {
      throw ArithmeticException("جمعِ پایانِ روز بیرون از بازه است")
    }
    return r
  }

  /** فروش، برگشت و رسیدِ یک روش (واحدِ کوچکِ پول)؛ خالص = فروش + رسید − برگشت (همان `MethodTotals.net`) */
  data class Sums(val sales: Long = 0, val refunds: Long = 0, val receipts: Long = 0) {
    val net: Long get() = add(add(sales, receipts), -refunds)
    operator fun plus(o: Sums) = Sums(add(sales, o.sales), add(refunds, o.refunds), add(receipts, o.receipts))
  }

  /** یک شیفت با عددهای X (باز) یا Z (بسته) ِ هسته */
  data class Shift(
    val id: String,
    val openedAt: Long,
    val openedBy: String? = null,
    /** null ⇒ هنوز باز است */
    val closedAt: Long? = null,
    val float: Long = 0,
    /** به ترتیبِ نخستین دیدن */
    val methods: Map<String, Sums> = emptyMap(),
    val cashIn: Long = 0,
    val cashOut: Long = 0,
    /** مورد انتظارِ نقد (با موجودیِ اول و ورود/خروج) و هر روشِ شمردنی */
    val expected: Map<String, Long> = emptyMap(),
    /** فقط بسته: شمرده‌شدهٔ هر روش */
    val counted: Map<String, Long>? = null,
    /** فقط بسته؛ مقدارِ null یعنی همان روش شمرده نشد */
    val variance: Map<String, Long?>? = null,
    val needsReview: Boolean = false,
    /** بسته با صندوقِ پیش از pay v2: Z ذخیره نشده؛ عددها بازپخشِ دفتر تا لحظهٔ بستن‌اند */
    val legacy: Boolean = false,
    /** رویدادهایی از دفتر که هستهٔ شیفت در بازپخش نپذیرفت (فقط X/بازپخش) */
    val skipped: Int = 0,
    val noSale: Int = 0,
  )

  /** رویدادِ پولیِ دفتر: `sale` | `refund` | `receipt`؛ ردیف = روش ⇐ مبلغ */
  data class Event(val type: String, val at: Long, val rows: List<Pair<String, Long>>)

  data class MethodLine(
    val method: String,
    /** جمعِ شیفت‌های روز */
    val shifts: Sums,
    val expected: Long?,
    val counted: Long?,
    val variance: Long?,
    /** همان روز از دفتر */
    val ledger: Sums,
  ) {
    /** دفتر − شیفت‌ها: پولی که هیچ X/Z ِ این روز نمی‌شمارد (منفی ⇐ شیفت‌ها بیش از دفتر شمرده‌اند) */
    val outside: Long get() = add(ledger.net, -shifts.net)
    val matches: Boolean get() = ledger == shifts
  }

  data class ShiftLine(
    val id: String,
    val openedAt: Long,
    val openedBy: String?,
    val closedAt: Long?,
    /** `open` | `closed` | `legacy` */
    val status: String,
    val float: Long,
    val net: Long,
    val cashIn: Long,
    val cashOut: Long,
    val expectedCash: Long?,
    val countedCash: Long?,
    val varianceCash: Long?,
    val needsReview: Boolean,
    val skipped: Int,
    val noSale: Int,
  )

  /** به ترتیبِ اولویتِ نمایش: هر کدام که اول درست باشد */
  enum class Status { EMPTY, OVERLAP, MISMATCH, OPEN, REVIEW, CLOSED }

  data class Report(
    val date: String,
    /** به ترتیبِ باز شدن */
    val shifts: List<ShiftLine>,
    /** نقد اول، بعد به ترتیبِ نخستین دیدن در شیفت‌ها و سپس دفتر */
    val methods: List<MethodLine>,
    val total: MethodLine,
    val openShifts: Int,
    /** جفت شناسه‌های شیفت‌هایی که پنجرهٔ زمانی‌شان روی هم افتاده */
    val overlapping: List<Pair<String, String>>,
    /** رویدادهای پولیِ دفترِ همین روز */
    val ledgerEvents: Int,
    val status: Status,
  ) {
    val reconciled: Boolean get() = methods.all { it.matches }
  }

  private fun sumOrNull(values: List<Long>): Long? = if (values.isEmpty()) null else values.fold(0L) { a, b -> add(a, b) }

  /**
   *  @param shifts همهٔ شیفت‌های نامزد؛ فقط آن‌ها که `dayOf(openedAt) == date` می‌مانند.
   *  @param events رویدادهای پولیِ دفتر؛ فقط آن‌ها که `dayOf(at) == date` می‌مانند.
   *  @param now پایانِ پنجرهٔ شیفتِ باز برای سنجشِ هم‌پوشانی.
   */
  fun of(date: String, shifts: List<Shift>, events: List<Event>, dayOf: (Long) -> String, now: Long): Report {
    val day = shifts.filter { dayOf(it.openedAt) == date }.sortedWith(compareBy<Shift>({ it.openedAt }, { it.id }))
    val dayEvents = events.filter { it.type in TYPES && dayOf(it.at) == date }.sortedWith(compareBy<Event>({ it.at }, { it.type }))

    val order = LinkedHashSet<String>()
    order += CASH
    day.forEach { order += it.methods.keys }
    dayEvents.forEach { e -> e.rows.forEach { order += it.first } }

    val ledger = HashMap<String, Sums>()
    for (e in dayEvents) {
      for ((m, a) in e.rows) {
        val s = ledger[m] ?: Sums()
        ledger[m] = when (e.type) {
          "sale" -> s.copy(sales = add(s.sales, a))
          "refund" -> s.copy(refunds = add(s.refunds, a))
          else -> s.copy(receipts = add(s.receipts, a))
        }
      }
    }

    val methods = order.mapNotNull { m ->
      val sh = day.mapNotNull { it.methods[m] }.fold(Sums()) { a, x -> a + x }
      val led = ledger[m] ?: Sums()
      val expected = sumOrNull(day.mapNotNull { it.expected[m] })
      val counted = sumOrNull(day.mapNotNull { it.counted?.get(m) })
      val variance = sumOrNull(day.mapNotNull { it.variance?.get(m) })
      //  نقد همیشه ردیف دارد؛ روشی که نه شیفت دیده نه دفتر، ردیف نمی‌گیرد
      if (m != CASH && sh == Sums() && led == Sums() && expected == null && counted == null) null
      else MethodLine(m, sh, expected, counted, variance, led)
    }
    val total = MethodLine(
      "",
      methods.fold(Sums()) { a, x -> a + x.shifts },
      sumOrNull(methods.mapNotNull { it.expected }),
      sumOrNull(methods.mapNotNull { it.counted }),
      sumOrNull(methods.mapNotNull { it.variance }),
      methods.fold(Sums()) { a, x -> a + x.ledger },
    )

    val lines = day.map { s ->
      ShiftLine(
        id = s.id, openedAt = s.openedAt, openedBy = s.openedBy, closedAt = s.closedAt,
        status = when { s.closedAt == null -> "open"; s.legacy -> "legacy"; else -> "closed" },
        float = s.float, net = s.methods.values.fold(Sums()) { a, x -> a + x }.net, cashIn = s.cashIn, cashOut = s.cashOut,
        expectedCash = s.expected[CASH], countedCash = s.counted?.get(CASH), varianceCash = s.variance?.get(CASH),
        needsReview = s.needsReview, skipped = s.skipped, noSale = s.noSale,
      )
    }

    val overlapping = ArrayList<Pair<String, String>>()
    for (i in day.indices) {
      val a = day[i]
      val aEnd = a.closedAt ?: maxOf(now, a.openedAt)
      for (j in i + 1 until day.size) {
        val b = day[j]
        val bEnd = b.closedAt ?: maxOf(now, b.openedAt)
        //  پنجره‌ها بسته‌اند (رویدادِ همان لحظهٔ بستن در شیفت است)؛ شیفتی که درست در لحظهٔ بستنِ قبلی باز شود هم‌پوشان است
        if (b.openedAt <= aEnd && a.openedAt <= bEnd) overlapping += a.id to b.id
      }
    }

    val open = day.count { it.closedAt == null }
    val reconciled = methods.all { it.matches }
    val status = when {
      day.isEmpty() && dayEvents.isEmpty() -> Status.EMPTY
      overlapping.isNotEmpty() -> Status.OVERLAP
      !reconciled -> Status.MISMATCH
      open > 0 -> Status.OPEN
      day.any { it.needsReview || it.skipped > 0 } -> Status.REVIEW
      else -> Status.CLOSED
    }
    return Report(date, lines, methods, total, open, overlapping, dayEvents.size, status)
  }

  private val TYPES = setOf("sale", "refund", "receipt")
}
