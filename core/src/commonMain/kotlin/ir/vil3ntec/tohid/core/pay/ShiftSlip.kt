package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money

/*
 *  گزارشِ X و Z برای صفحه و کاغذ، و شمارشِ هر روش در بستن — PAY-13، 15، 16 (بخشِ ۳.۷ و ۴.۲).
 *
 *  فقط نمایش و ورودیِ فرم است؛ حسابِ شیفت همان `ShiftCore` است و این‌جا هیچ مبلغی ساخته
 *  نمی‌شود، فقط از گزارشِ هسته خوانده می‌شود. یک فهرستِ ردیف ([rows]) هم روی صفحه می‌آید هم
 *  روی کاغذ، تا آنچه چاپ می‌شود همان باشد که دیده شد.
 *
 *  کاتلینِ خالص در commonMain کنارِ `Shift.kt` (گوشی، کامپیوتر و آیفون)؛ بی `java.*`/`android.*`.
 *  آزمون: `commonTest` ِ `ShiftSlipTest` (روی JVM و شبیه‌سازِ iOS).
 */
object ShiftSlip {

  /**
   *  PAY-13 — آنچه بیننده می‌بیند. بی حقِ دیدنِ «مورد انتظار» (شاگرد، شمارشِ کور) نه مورد انتظار
   *  را می‌بیند نه اختلاف را — در گزارشِ X ِ میانِ شیفت هم، نه فقط در Zِ بسته با شمارشِ کور.
   *  (`ShiftCore.cashierView` فقط Zِ `blind` را می‌پوشاند و X را دست‌نخورده برمی‌گرداند.)
   *
   *  ⛔ «مورد انتظار» جمعِ اولِ صندوق، فروش/رسید/مرجوعیِ هر روش و ورود − خروجِ نقد است؛ پس هیچ‌کدام مبلغ
   *  نمی‌مانند (ردیف‌ها فقط شمارِ رویدادها، [rows]). «نیازمند بررسی» و دلیل هم نه: یعنی «شمارشت نخواند».
   *  شمرده‌ها و اسکناس‌ها (ورودیِ خودِ او) می‌مانند.
   */
  fun forViewer(r: ShiftReport, seesExpected: Boolean): ShiftReport =
    if (seesExpected) r
    else r.copy(
      float = 0, methods = emptyMap(), moves = Moves(0, 0),
      expected = null, variance = null, reason = null, needsReview = null,
    )

  /** نوعِ ردیف؛ برچسب را برنامه می‌گذارد */
  enum class Kind {
    OPENED, AT, CLOSED, BY, FLOAT,
    SALES, RECEIPTS, REFUNDS, CASH_IN, CASH_OUT, NO_SALE,
    EXPECTED, DENOMINATION, COUNTED, VARIANCE, REASON, NEEDS_REVIEW,
  }

  /**
   *  یک ردیفِ گزارش. [amount] پول است (برای OPENED/AT/CLOSED زمان به میلی‌ثانیه)؛ [count] شمار
   *  (کشو بی فروش، تعدادِ اسکناس)؛ [note] اسکناس به پول برای DENOMINATION. VARIANCE با `amount = null`
   *  یعنی «شمرده نشد».
   */
  data class Row(
    val kind: Kind,
    val method: String? = null,
    val amount: Long? = null,
    val count: Long? = null,
    val note: Long? = null,
    val text: String? = null,
    val strong: Boolean = false,
  )

  /**
   *  PAY-15 — ردیف‌های X یا Z به همان ترتیبِ صفحه. نقد همان `Pay.CASH` است.
   *  PAY-16 — در Z، شمرده‌شدهٔ هر روشِ غیرنقد هم ردیفِ خودش را دارد (نه فقط نقد).
   *  نمای کور (`expected` = null: [forViewer] یا `ShiftCore.cashierView`) ⇒ فروش، رسید، مرجوعی و ورود/خروجِ نقد
   *  فقط با شمار (`count`، بی `amount` و بی روش)، بی اولِ صندوق، بی دلیل و بی «نیازمند بررسی».
   */
  fun rows(r: ShiftReport): List<Row> = buildList {
    val z = r.kind == "Z"
    val blind = r.expected == null
    add(Row(Kind.OPENED, amount = r.openedAt))
    add(Row(if (z) Kind.CLOSED else Kind.AT, amount = r.at))
    r.by?.let { add(Row(Kind.BY, text = it)) }
    if (blind) {
      val e = r.events
      add(Row(Kind.SALES, count = e.sales.toLong()))
      add(Row(Kind.RECEIPTS, count = e.receipts.toLong()))
      add(Row(Kind.REFUNDS, count = e.refunds.toLong()))
      add(Row(Kind.CASH_IN, count = e.cashIn.toLong()))
      add(Row(Kind.CASH_OUT, count = e.cashOut.toLong()))
    } else {
      add(Row(Kind.FLOAT, amount = r.float))
      r.methods.forEach { (id, m) ->
        if (m.sales != 0L) add(Row(Kind.SALES, id, m.sales))
        if (m.receipts != 0L) add(Row(Kind.RECEIPTS, id, m.receipts))
        if (m.refunds != 0L) add(Row(Kind.REFUNDS, id, m.refunds))
      }
      add(Row(Kind.CASH_IN, amount = r.moves.cashIn))
      add(Row(Kind.CASH_OUT, amount = r.moves.cashOut))
    }
    add(Row(Kind.NO_SALE, count = r.events.noSale.toLong()))
    r.expected?.forEach { (id, v) -> add(Row(Kind.EXPECTED, id, v, strong = id == Pay.CASH)) }
    if (!z) return@buildList
    r.denominations?.entries
      ?.mapNotNull { (k, n) -> k.toLongOrNull()?.let { it to n } }
      ?.sortedByDescending { it.first }
      ?.forEach { (d, n) -> add(Row(Kind.DENOMINATION, Pay.CASH, d * n, count = n, note = d)) }
    val counted = r.counted.orEmpty()
    add(Row(Kind.COUNTED, Pay.CASH, counted[Pay.CASH] ?: 0L, strong = true))
    counted.forEach { (id, v) -> if (id != Pay.CASH) add(Row(Kind.COUNTED, id, v)) }
    if (blind) return@buildList
    r.variance?.forEach { (id, v) -> add(Row(Kind.VARIANCE, id, v)) }
    r.reason?.let { add(Row(Kind.REASON, text = it)) }
    if (r.needsReview == true) add(Row(Kind.NEEDS_REVIEW))
  }

  /**
   *  PAY-16 — روش‌هایی که در بستن جدا شمرده می‌شوند: روش‌های غیرنقدِ شمردنی (کیف پول، کارت، حواله،
   *  سایر) که در همین شیفت گردش داشته‌اند، به ترتیبِ گزارش. از گزارشِ کاملِ X گرفته می‌شود، نه نمای
   *  بیننده — نامِ روش مبلغی را لو نمی‌دهد.
   */
  fun countable(x: ShiftReport): List<String> = x.expected.orEmpty().keys.filter { it != Pay.CASH }

  /** فقط رقم و حداکثر یک نقطه، با دستِ کم یک رقم — همان `numericInput` ِ کادر */
  private val AMOUNT = Regex("^(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)$")

  /**
   *  PAY-16 — متنِ کادرهای «شمرده‌شدهٔ هر روش» (افغانی) ⇒ پول، با `Money.fromLegacy` (همان تبدیلِ دفتر).
   *  کادرِ خالی یعنی «شمرده نشد» و در نتیجه نمی‌آید (اختلافش «شمرده نشد» می‌ماند، نه صفر). ۰ ِ نوشته‌شده
   *  یعنی «هیچ» و می‌آید. نوشتهٔ نامعتبر ⇒ `INVALID_COUNT` با نامِ روش؛ روشی که در [allowed] نیست ⇒
   *  `UNKNOWN_METHOD`. سقفِ بازهٔ دقیق را خودِ `ShiftCore.close` می‌سنجد.
   */
  fun methodCounts(texts: Map<String, String>, allowed: Collection<String>): PayResult<Map<String, Long>> {
    val out = LinkedHashMap<String, Long>()
    for ((id, raw) in texts) {
      val t = raw.trim()
      if (t.isEmpty()) continue
      if (id !in allowed) return PayResult.Err("UNKNOWN_METHOD", method = id)
      val x = if (AMOUNT.matches(t)) t.toDoubleOrNull()?.takeIf { it.isFinite() } else null
      x ?: return PayResult.Err("INVALID_COUNT", method = id)
      out[id] = Money.fromLegacy(x, Currency.AFN).minor
    }
    return PayResult.Ok(out)
  }
}
