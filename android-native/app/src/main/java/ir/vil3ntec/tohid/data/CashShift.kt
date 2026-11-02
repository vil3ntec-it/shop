package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.money

/**
 *  صندوق (شیفت) — «اولِ روز چقدر در دخل بود، الان باید چقدر باشد، و چقدر هست».
 *
 *  ── چرا جدولِ تازه ندارد ──────────────────────────────────────────
 *  باز و بسته کردنِ صندوق دو ردیفِ سابقه است (`shift_open` و `shift_close`)
 *  با مبلغ در `AuditEntry.amount`. سابقه همین حالا میانِ همهٔ دستگاه‌ها
 *  همگام می‌شود، پس صندوقی که روی یک گوشی باز شد روی کامپیوتر هم باز
 *  دیده می‌شود — بی جدول و بی تغییرِ سرور.
 *
 *  ── نقدِ مورد انتظار ──────────────────────────────────────────────
 *  اول صندوق
 *   + نقدِ هر فاکتورِ لغونشده (`finalTotal − debtGiven`: نقدی کامل، نسیه فقط پیش‌پرداخت؛
 *     فاکتورِ با `payments` فقط ردیف‌های نقد — همان نقدِ ماندهٔ پس از پولِ پس — [saleCash]؛
 *     با اصلاحِ روشِ پرداختِ `pay_fix` تا پایانِ پنجره، نقدِ پس از اصلاح — POS-40، `PayFixFlow`)
 *   + رسیدِ قرض‌داران (`receive`) — جز رسیدی که سیستم هنگامِ لغوِ فروشِ نسیه ساخت
 *     ([isCashReceipt]؛ پولی نیامده، فقط بدهی بسته شد)
 *   + ورودِ نقد − خروجِ نقد (`cash_in`/`cash_out`ِ همین صندوق، `refId` = بازکردن) — داده‌اند،
 *     پس با پرچمِ pay v2 یا بی آن همیشه شمرده می‌شوند
 *   − مرجوعی‌ها (کلِ مبلغ؛ بخشی که از بدهی کم شد خودش یک `receive` است، پس خالص درست است) و بازپرداختِ
 *     پیش‌پرداختِ سفارشِ ویژهٔ لغوشده (POS-26، `SpecialOrders.REFUND`؛ پیش‌پرداخت خودش رسید است)
 *   − مصارف − پرداخت به تأمین‌کننده − پیش‌پرداختِ خریدها
 *  هر کدام اگر `createdAt`ش میانِ باز و بسته شدن باشد. «برد»ِ دستی حساب
 *  نمی‌شود چون معلوم نیست پول بوده یا جنس.
 *
 *  ⛔ فروشِ اینترنتی (فاکتورِ سریِ `W` / `channel == "online"` که سرور هنگامِ
 *  تحویلِ سفارش می‌گذارد، ORD-20) پولِ دخل نیست: پرداختِ آنلاین نزدِ درگاه است و
 *  نقدِ «هنگامِ تحویل» دستِ پیک تا تسویهٔ پیک (سندِ تسویه، نه صندوق). مرجوعیِ
 *  سرور برای همان سفارش (`channel == "online"`) هم از دخل پرداخت نمی‌شود؛ مرجوعیِ
 *  دستیِ همین دستگاه برای فاکتورِ W (پولِ نقد از دخل) مثلِ همیشه کم می‌شود.
 *  بی پرچمِ `shop.orders.ledger` فاکتورِ W ای نیست ⇒ همان حسابِ پیشین.
 *
 *  همان `cashShift*`ِ نسخهٔ وب.
 *  ──────────────────────────────────────────────────────────────────
 */
object CashShift {

  const val OPEN = "shift_open"
  const val CLOSE = "shift_close"
  const val ONLINE = "online"
  const val ONLINE_SERIES = "W"

  /**
   *  فاکتورِ اینترنتی که پولش به دخل نمی‌آید: فاکتورِ W ِ سرور همیشه؛ هر فروشِ `channel = online`
   *  (از جمله فروشِ تحویلِ سفارش در برنامه، `OrderFulfillment`) مگر «نقد سرِ پیشخوان»
   *  ([paidAtCounter]) — پرداختِ آنلاین نزدِ درگاه است و نقدِ پیک تا تسویهٔ پیک.
   */
  fun isOnline(s: Sale): Boolean =
    s.series == ONLINE_SERIES || (s.channel == ONLINE && !paidAtCounter(s))

  /**
   *  سفارشی که مشتری سرِ پیشخوان گرفت و نقد داد (`OrderFulfillment.atCounter`، حتی سفارشِ
   *  «ارسال»): ردیفِ پرداختِ نقدِ بی‌امانت
   *  (`kind = cash`، بی `custody`) — همان نقدِ عادیِ دخل. نقدِ پیک `custody = courier` دارد.
   */
  fun paidAtCounter(s: Sale): Boolean =
    s.payments?.any { it.kind == "cash" && it.custody == null && it.amountMinor > 0 } == true

  /**
   *  مرجوعیِ بیرون از دخل: ردیفی که خودش `channel = online` دارد — مرجوعیِ سرور برای سفارشِ
   *  اینترنتی، یا مرجوعیِ پیوسته به بازپرداختی که بیرون از دخل داده شد — و ردیف‌های سرورِ پیش
   *  از نشانِ `channel` (شناسهٔ ثابتِ `w-…`، هرگز ULID ِ دستگاه) بر فاکتورِ اینترنتی.
   *  ⛔ مرجوعیِ **دستیِ** همین صندوق — حتی برای فروشِ آنلاین یا پیک (`o-…`، `w-…`) — پولی است که
   *  از همین دخل رفت ⇒ کم می‌شود (تصمیمِ صاحبِ مخزن، بازبینیِ دومِ #340؛ پسِ تحویلِ ناموفق
   *  «لغو» است، نه مرجوعی ⇒ اثری بر دخل ندارد).
   *  ⛔ دفترِ بازپرداختِ سفارش (`order-refunds`) امروز فقط `cash` دارد و نشانِ «بیرون از دخل»
   *  ندارد؛ پس امروز هیچ مرجوعیِ دستی‌ای `channel = online` نمی‌گیرد.
   */
  fun isOnlineReturn(r: SaleReturn, onlineSales: Set<String>): Boolean =
    r.channel == ONLINE || (r.saleId in onlineSales && r.id.startsWith("w-"))

  sealed interface Result {
    data class Ok(val data: ShopData) : Result
    data class Failed(val message: String) : Result
  }

  data class Totals(
    val opening: Double,
    val sales: Double,
    val receipts: Double,
    val returns: Double,
    val expenses: Double,
    val suppliers: Double,
    /** ورود و خروجِ دستیِ نقد (`cash_in`/`cash_out`ِ صندوقِ pay v2) — داده است، همیشه شمرده می‌شود */
    val cashIn: Double = 0.0,
    val cashOut: Double = 0.0,
  ) {
    val expected: Double get() = opening + sales + receipts + cashIn - returns - expenses - suppliers - cashOut
  }

  /** `DebtTransaction.kind`ِ رسیدهای ساختهٔ سیستم */
  const val CANCEL_RECEIPT = "sale_cancel"
  const val RETURN_RECEIPT = "customer_return"
  const val STORE_CREDIT_RECEIPT = "store_credit"

  /** متنِ رسیدِ لغو که هر دو برنامه (این و نسخهٔ وب) پیش از نشانِ `kind` می‌نوشتند */
  private const val LEGACY_CANCEL_NOTE = "لغو فروش — فاکتور #"

  /**
   *  آیا این رسید پولِ نقد به دخل آورد؟ رسیدِ دستی و رسیدِ مرجوعی (که کمِ کلِ مرجوعی را
   *  خنثی می‌کند) آری؛ رسیدِ لغو (فقط بستنِ بدهی) و اعتبارِ فروشگاه نه. ردیفِ بی `kind`ِ
   *  برنامهٔ قدیمی با همان متنِ ثابتِ لغو شناخته می‌شود.
   */
  fun isCashReceipt(t: DebtTransaction): Boolean = when (t.kind) {
    CANCEL_RECEIPT, STORE_CREDIT_RECEIPT -> false
    null -> !t.notes.startsWith(LEGACY_CANCEL_NOTE)
    else -> true
  }

  private val MOVES = setOf("cash_in", "cash_out")

  data class Shift(val open: AuditEntry, val close: AuditEntry?) {
    val isOpen: Boolean get() = close == null
  }

  /** همهٔ صندوق‌ها، تازه‌ترین اول */
  fun list(d: ShopData): List<Shift> {
    val closes = d.auditLog.filter { it.type == CLOSE && it.refId != null }.associateBy { it.refId }
    return d.auditLog.filter { it.type == OPEN }
      .sortedByDescending { it.createdAt }
      .map { Shift(it, closes[it.id]) }
  }

  /** صندوقِ باز — اگر دو دستگاه هم‌زمان باز کرده باشند، تازه‌ترین */
  fun current(d: ShopData): Shift? = list(d).firstOrNull { it.isOpen }

  /**
   *  نقدی که این فاکتور به دخل آورد. با `payments` (PAY v3 A4) فقط ردیف‌های نوعِ نقد —
   *  مبلغِ آن‌ها خودش پس از کمِ پولِ پس است؛ کیف پول، کارت، حواله و «به حساب» نقدِ دخل نیستند.
   *  بی `payments` همان حسابِ امروز. فاکتورِ اینترنتی ([isOnline]) همیشه صفر — حتی ردیفِ
   *  `cash`ِ «نقد هنگامِ تحویل» (`custody = courier`). با [fixes] (POS-40) نقدِ همان ردیف‌ها پس از اصلاح‌های روشِ
   *  پرداختِ تا [until] (`PayFlow.saleRows`)؛ بی اصلاح همان حسابِ امروز.
   */
  fun saleCash(s: Sale, fixes: List<ir.vil3ntec.tohid.core.pay.PayFix.Fix> = emptyList(), until: Long = Long.MAX_VALUE): Double {
    //  فاکتورِ اینترنتیِ سرور (W): نه آنلاین و نه «نقد هنگامِ تحویل»ِ دستِ پیک پولِ دخل است
    if (isOnline(s)) return 0.0
    if (fixes.isNotEmpty()) return PayFlow.saleRows(s, fixes, until).filter { it.method == PayFlow.CASH }.sumOf { it.amount } / 100.0
    s.payments?.takeIf { it.isNotEmpty() }?.let { list ->
      //  ردیفِ نقدی که امانتِ کسِ دیگری است (`custody`، مثلاً پیک) هم در دخل نیست
      return list.filter { it.kind == PayFlow.CASH && it.custody == null && it.amountMinor > 0 }.sumOf { it.amountMinor } / 100.0
    }
    return (s.finalTotal - s.debtGiven).coerceAtLeast(0.0)
  }

  /**
   *  @param shiftId شناسهٔ `shift_open`؛ ورود/خروجِ نقد با `refId` = همین شمرده می‌شود
   *  (همان قاعدهٔ `PayFlow.ledgerEvents`). بی آن فقط پنجرهٔ زمانی.
   */
  fun totals(d: ShopData, from: Long, to: Long, opening: Double, shiftId: String? = null): Totals {
    fun inWindow(at: Long) = at in from..to
    val online = d.sales.filter(::isOnline).mapTo(HashSet()) { it.id }
    val moves = if (shiftId == null) emptyList() else d.auditLog.filter { it.type in MOVES && it.refId == shiftId && it.amount > 0 }
    val fixes = PayFixFlow.bySale(d)
    return Totals(
      opening = opening,
      sales = d.sales.filter { it.status != "cancelled" && !isOnline(it) && inWindow(it.createdAt) }
        .sumOf { saleCash(it, fixes[it.id].orEmpty(), to) },
      receipts = d.transactions.filter { it.type == "receive" && isCashReceipt(it) && inWindow(it.createdAt) }.sumOf { it.amount } +
        //  PAY-24: فروشِ کارتِ هدیه (نقد)؛ بی کارت ⇒ ۰
        GiftCards.issueCash(d, from, to),
      //  برگشتی که به کیف پول/کارت/حواله پس رفت (`toMethods`، فقط RET تازه) از دخل نیست؛
      //  ردیفِ قدیمی آن را ندارد ⇒ همان `amount`ِ امروز
      returns = d.saleReturns.filter { inWindow(it.createdAt) && !isOnlineReturn(it, online) }.sumOf { ReturnFlow.drawerAmount(it) } +
        //  POS-26: پیش‌پرداختِ سفارشِ ویژه که هنگامِ لغو نقد به مشتری پس رفت (`SpecialOrders.REFUND`)
        d.transactions.filter { SpecialOrders.isRefund(it) && inWindow(it.createdAt) }.sumOf { it.amount },
      expenses = d.expenses.filter { inWindow(it.createdAt) }.sumOf { it.amount },
      suppliers = d.supplierPayments.filter { inWindow(it.createdAt) }.sumOf { it.amount } +
        d.purchases.filter { inWindow(it.createdAt) }.sumOf { it.paidAmount },
      cashIn = moves.filter { it.type == "cash_in" }.sumOf { it.amount },
      cashOut = moves.filter { it.type == "cash_out" }.sumOf { it.amount },
    )
  }

  fun totalsOf(d: ShopData, s: Shift, now: Long): Totals =
    totals(d, s.open.createdAt, s.close?.createdAt ?: now, s.open.amount, s.open.id)

  fun open(d: ShopData, opening: Double, by: String, today: String, now: Long, newId: () -> String): Result {
    if (opening.isNaN() || opening < 0) return Result.Failed("مبلغِ اولِ صندوق درست نیست")
    if (current(d) != null) return Result.Failed("صندوق از قبل باز است")
    val who = by.trim()
    return Result.Ok(
      d.copy(
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = OPEN, date = today, amount = opening, createdAt = now,
          notes = "باز کردنِ صندوق با ${money(opening)} افغانی" + if (who.isNotEmpty()) " — $who" else "",
        )
      )
    )
  }

  fun close(d: ShopData, counted: Double, note: String, by: String, today: String, now: Long, newId: () -> String): Result {
    if (counted.isNaN() || counted < 0) return Result.Failed("مبلغِ شمرده‌شده درست نیست")
    val shift = current(d) ?: return Result.Failed("صندوقِ بازی نیست")
    val t = totalsOf(d, shift, now)
    val diff = counted - t.expected
    val who = by.trim()
    val extra = listOfNotNull(note.trim().ifEmpty { null }, who.ifEmpty { null }).joinToString(" — ")
    return Result.Ok(
      d.copy(
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = CLOSE, date = today, refId = shift.open.id, amount = counted, createdAt = now,
          notes = "بستنِ صندوق: مورد انتظار ${money(t.expected)}، شمرده ${money(counted)}، " +
            (if (kotlin.math.abs(diff) < 0.5) "بی اختلاف" else if (diff > 0) "اضافه ${money(diff)}" else "کسری ${money(-diff)}") +
            if (extra.isNotEmpty()) " — $extra" else "",
        )
      )
    )
  }
}
