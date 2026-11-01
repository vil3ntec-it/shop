package ir.vil3ntec.tohid.core.sale

/**
 *  لیستِ قیمتِ فاکتور — POS-18 (§۶.۱): خرده و عمده، تغییرِ سریعِ لیستِ همین فاکتور (Ctrl+R) و اعمالِ خودکارِ
 *  لیستِ پیش‌فرضِ مشتری (`Debtor.priceList`، CRM-08).
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • پول عددِ صحیح به یک‌صدمِ افغانی. عمده فقط وقتی کالا قیمتِ عمدهٔ مثبت دارد؛ وگرنه همان خرده
 *    (همان `CustomerCrm.unitPrice` ِ برنامه). نامِ ناشناخته ⇒ خرده.
 *  • تغییرِ لیست فقط ردیف‌هایی را عوض می‌کند که **قیمتشان دستی عوض نشده**؛ ردیفِ دستی همان می‌ماند و
 *    شمرده می‌شود تا رابط بگوید «n ردیفِ دستی دست نخورد». ردیفِ بی تغییر شمرده نمی‌شود.
 *  • انتخابِ مشتری لیستِ پیش‌فرضِ او را می‌نشاند، **مگر** صندوق‌دار لیستِ همین فاکتور را خودش عوض کرده باشد
 *    (انتخابِ دستی برنده است). برداشتنِ مشتری لیست را برنمی‌گرداند (قیمتِ جلوی مشتری ناگهان عوض نشود).
 *  • Ctrl+R: خرده ⇄ عمده.
 *  «مشتریِ خاص» (لیستِ جدا برای یک مشتری) هنوز مدل ندارد؛ همین دو لیست.
 *  منطقِ خالص (commonMain)؛ بردارها: `core/test-vectors/price-list-v1.json`.
 */
object PriceList {

  const val RETAIL = "retail"
  const val WHOLESALE = "wholesale"
  val ALL: List<String> = listOf(RETAIL, WHOLESALE)

  /** قیمت‌های یک کالا؛ `wholesaleMinor ≤ 0` ⇒ عمده ندارد */
  data class Prices(val retailMinor: Long, val wholesaleMinor: Long)

  /** ردیفِ فاکتور: کلید، قیمت‌های کالا، فیِ امروزِ ردیف و این‌که صندوق‌دار فی را دستی عوض کرده */
  data class Line(val key: String, val prices: Prices, val unitMinor: Long, val manual: Boolean = false)

  data class Repriced(
    val list: String,
    val lines: List<Line>,
    /** ردیف‌هایی که فیشان عوض شد */
    val changed: Int,
    /** ردیف‌های دستی که عمداً دست نخوردند (فقط آن‌ها که با لیستِ تازه فرق داشتند) */
    val keptManual: Int,
  )

  /** نامِ لیست از ورودیِ آزاد؛ خالی یا ناشناخته ⇒ خرده */
  fun of(raw: String?): String = if (raw?.trim() == WHOLESALE) WHOLESALE else RETAIL

  /** فیِ واحدِ کالا در این لیست */
  fun unitMinor(p: Prices, list: String): Long =
    if (of(list) == WHOLESALE && p.wholesaleMinor > 0) p.wholesaleMinor else p.retailMinor

  fun reprice(lines: List<Line>, list: String): Repriced {
    val l = of(list)
    var changed = 0
    var kept = 0
    val out = lines.map { x ->
      val target = unitMinor(x.prices, l)
      when {
        x.unitMinor == target -> x
        x.manual -> { kept++; x }
        else -> { changed++; x.copy(unitMinor = target) }
      }
    }
    return Repriced(l, out, changed, kept)
  }

  /** Ctrl+R */
  fun next(list: String): String = if (of(list) == RETAIL) WHOLESALE else RETAIL

  /**
   *  لیستِ فاکتور پس از انتخابِ مشتری. [chosenByHand] = صندوق‌دار لیستِ همین فاکتور را خودش عوض کرده؛
   *  [customerList] = `null` یعنی مشتری برداشته شد ⇒ همان لیستِ امروز.
   */
  fun afterCustomer(current: String, chosenByHand: Boolean, customerList: String?): String = when {
    chosenByHand || customerList == null -> of(current)
    else -> of(customerList)
  }

  /**
   *  ردیفِ سبدِ صندوق: `unitMinor = null` یعنی همان قیمتِ کالا (خرده). فیِ برابر با خرده یا عمدهٔ کالا
   *  «از لیست» است و با لیست عوض می‌شود؛ هر فیِ دیگری دستی است و دست نمی‌خورد.
   */
  fun isManual(p: Prices, unitMinor: Long?): Boolean =
    unitMinor != null && unitMinor != p.retailMinor && !(p.wholesaleMinor > 0 && unitMinor == p.wholesaleMinor)

  /** فیِ تازهٔ ردیفِ سبد در این لیست؛ خرده ⇒ `null` (همان قیمتِ کالا)، دستی ⇒ همان */
  fun cartUnit(p: Prices, unitMinor: Long?, list: String): Long? {
    if (isManual(p, unitMinor)) return unitMinor
    val target = unitMinor(p, list)
    return if (target == p.retailMinor) null else target
  }

  /** لیستِ همین فاکتور و این‌که صندوق‌دار خودش انتخابش کرده */
  data class State(val list: String = RETAIL, val byHand: Boolean = false)

  /** Ctrl+R یا دکمهٔ لیست */
  fun toggle(s: State): State = State(next(s.list), byHand = true)

  /** مشتری عوض شد ([customerList] = `null` ⇒ برداشته شد) */
  fun onCustomer(s: State, customerList: String?): State = s.copy(list = afterCustomer(s.list, s.byHand, customerList))

  /** سبد خالی شد یا فاکتور ثبت شد ⇒ فاکتورِ بعدی از خرده و بی انتخابِ دستی */
  val FRESH = State()
}
