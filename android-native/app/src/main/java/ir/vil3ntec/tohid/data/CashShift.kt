package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.pay.Drawers
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
 *     پیش‌پرداختِ سفارشِ ویژهٔ لغوشده (POS-26، `SpecialOrders.REFUND`؛ پیش‌پرداخت خودش رسید است) و همهٔ پرداختیِ
 *     فروشِ امانیِ لغوشده (POS-27، `Layaways.REFUND`؛ پیش‌پرداخت و هر قسط خودشان رسیدند)
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
  private const val LEGACY_CANCEL_NOTE = "لغو فروش — فاکتور #"  // داده: پیشوندِ یادداشتِ کهنهٔ سابقه برای سنجش

  /**
   *  آیا این رسید پولِ نقد به دخل آورد؟ رسیدِ دستی و رسیدِ مرجوعی (که کمِ کلِ مرجوعی را
   *  خنثی می‌کند) آری؛ رسیدِ لغو (فقط بستنِ بدهی) و اعتبارِ فروشگاه نه. ردیفِ بی `kind`ِ
   *  برنامهٔ قدیمی با همان متنِ ثابتِ لغو شناخته می‌شود.
   */
  fun isCashReceipt(t: DebtTransaction): Boolean = when (t.kind) {
    //  S89: سندِ معکوس هرگز رسیدِ نقد نیست (معکوسِ رسیدِ نقد جدا از دخلِ روزِ خودش کم می‌شود — [totals])
    CANCEL_RECEIPT, STORE_CREDIT_RECEIPT, ir.vil3ntec.tohid.core.debt.DebtReversal.KIND -> false
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

  /**
   *  صندوقِ بازِ همین دستگاه — اگر دو دستگاه هم‌زمان باز کرده باشند، تازه‌ترین.
   *  PAY-17: کشوی نام‌دارِ برگزیدهٔ همین دستگاه (`TillDrawer.picked`) اگر باز است؛ وگرنه کشوی اصلی (`shift_open` ِ بی
   *  `drawer`). بی «چند کشو» هیچ کشوی نام‌داری نیست ⇒ همان صندوقِ امروز.
   */
  fun current(d: ShopData): Shift? {
    val all = list(d)
    TillDrawer.picked?.let { id -> all.firstOrNull { it.open.id == id && it.isOpen && it.open.drawer != null }?.let { return it } }
    return all.firstOrNull { it.isOpen && it.open.drawer == null }
  }

  /** کشوی اصلی (بی نام) باز است؟ — جدا از کشوهای نام‌دار (PAY-17) */
  fun mainOpen(d: ShopData): Boolean = list(d).any { it.isOpen && it.open.drawer == null }

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
    //  PAY-10: پولِ پسِ افغانیِ ارزِ نقد (`FxTillFlow.changeOut`) از همین دخل رفت؛ ارز خودش نقدِ افغانی نیست. بی ارز ⇒ ۰
    val fxOut = FxTillFlow.changeOut(s)
    if (fixes.isNotEmpty()) return (PayFlow.saleRows(s, fixes, until).filter { it.method == PayFlow.CASH }.sumOf { it.amount } - fxOut) / 100.0
    s.payments?.takeIf { it.isNotEmpty() }?.let { list ->
      //  ردیفِ نقدی که امانتِ کسِ دیگری است (`custody`، مثلاً پیک) هم در دخل نیست
      return (list.filter { it.kind == PayFlow.CASH && it.custody == null && it.amountMinor > 0 }.sumOf { it.amountMinor } - fxOut) / 100.0
    }
    return (s.finalTotal - s.debtGiven).coerceAtLeast(0.0)
  }

  /**
   *  @param shiftId شناسهٔ `shift_open`؛ ورود/خروجِ نقد با `refId` = همین شمرده می‌شود
   *  (همان قاعدهٔ `PayFlow.ledgerEvents`). بی آن فقط پنجرهٔ زمانی.
   */
  fun totals(d: ShopData, from: Long, to: Long, opening: Double, shiftId: String? = null, named: Boolean = false): Totals {
    //  PAY-17 (`core/pay/Drawers`): کشوی اصلی ⇒ ردیف‌های بی مهرِ پنجره (بی «چند کشو» همهٔ ردیف‌ها، همان امروز)؛ کشوی
    //  نام‌دار ⇒ فقط ردیف‌های مهرِ خودش و ورود/خروجِ صریحش — مصارف، خرید، تأمین‌کننده، کارتِ هدیه و بازپرداختِ ویژه/امانی نه.
    //  بی [shiftId] (گردشِ نقدِ روز، خانه) ⇒ همهٔ کشوها با هم.
    val drawers = TillDrawer.named(d)
    val own = shiftId.takeIf { named }
    fun counts(stamp: String?, at: Long): Boolean = when {
      own != null -> Drawers.counts(stamp, at, own, isNamed = true, named = drawers, from = from, to = to)
      shiftId == null -> at in from..to
      else -> at in from..to && Drawers.owner(stamp, at, drawers) == null
    }
    fun inWindow(at: Long) = own == null && at in from..to
    val online = d.sales.filter(::isOnline).mapTo(HashSet()) { it.id }
    val moves = if (shiftId == null) emptyList() else d.auditLog.filter { it.type in MOVES && it.refId == shiftId && it.amount > 0 }
    val fixes = PayFixFlow.bySale(d)
    return Totals(
      opening = opening,
      sales = d.sales.filter { it.status != "cancelled" && !isOnline(it) && counts(it.shiftId, it.createdAt) }
        .sumOf { saleCash(it, fixes[it.id].orEmpty(), to) },
      receipts = d.transactions.filter { it.type == "receive" && isCashReceipt(it) && counts(it.shiftId, it.createdAt) }.sumOf { it.amount } -
        //  S89: رسیدِ نقدیِ معکوس‌شده از دخلِ **روزِ معکوس** (مهر و ساعتِ ردیفِ معکوس) کم می‌شود؛ روزِ اصل دست نمی‌خورد
        DebtReverse.cashBack(d.transactions).filter { counts(it.shiftId, it.createdAt) }.sumOf { it.amount } +
        //  PAY-24/PRM-14: فروش و شارژِ کارتِ هدیه (نقد)؛ بی کارت ⇒ ۰
        (if (own == null) GiftCards.cashIn(d, from, to) else 0.0),
      //  برگشتی که به کیف پول/کارت/حواله پس رفت (`toMethods`، فقط RET تازه) از دخل نیست؛
      //  ردیفِ قدیمی آن را ندارد ⇒ همان `amount`ِ امروز
      returns = d.saleReturns.filter { counts(it.shiftId, it.createdAt) && !isOnlineReturn(it, online) }.sumOf { ReturnFlow.drawerAmount(it) } +
        //  POS-26: پیش‌پرداختِ سفارشِ ویژه که هنگامِ لغو نقد به مشتری پس رفت (`SpecialOrders.REFUND`)؛ POS-27: همهٔ
        //  پرداختیِ امانیِ لغوشده (`Layaways.REFUND`؛ پیش‌پرداخت و قسط‌ها خودشان رسیدند). بی امانی ⇒ همان جمع
        d.transactions.filter { (SpecialOrders.isRefund(it) || Layaways.isRefund(it)) && inWindow(it.createdAt) }.sumOf { it.amount },
      expenses = d.expenses.filter { inWindow(it.createdAt) }.sumOf { it.amount },
      suppliers = d.supplierPayments.filter { inWindow(it.createdAt) }.sumOf { it.amount } +
        d.purchases.filter { inWindow(it.createdAt) }.sumOf { it.paidAmount },
      cashIn = moves.filter { it.type == "cash_in" }.sumOf { it.amount },
      cashOut = moves.filter { it.type == "cash_out" }.sumOf { it.amount },
    )
  }

  /**
   *  RPT-04 × PAY-17 — فروشی که در X/Z ِ شیفتِ [shiftId] شمرده می‌شود: همان مهر و پنجرهٔ [totals] و `PayFlow.ledgerEvents`
   *  (`Drawers.counts`) — برای خط‌های کنارِ گزارش (ارزِ نقد، حساب‌پی) تا کشوی نام‌دار فروشِ کشوی دیگرِ همان ساعت را نشمارد.
   *  شیفتِ ناشناخته ⇒ فقط پنجره (همان پیشین)؛ بی «چند کشو» هیچ ردیفی مهر ندارد ⇒ بایت‌به‌بایت همان پنجره.
   */
  fun ownSale(d: ShopData, shiftId: String, from: Long, to: Long): (Sale) -> Boolean {
    val open = d.auditLog.firstOrNull { it.type == OPEN && it.id == shiftId } ?: return { it.createdAt in from..to }
    val named = TillDrawer.named(d)
    val isNamed = open.drawer != null
    return { s -> Drawers.counts(s.shiftId, s.createdAt, open.id, isNamed = isNamed, named = named, from = from, to = to) }
  }

  fun totalsOf(d: ShopData, s: Shift, now: Long): Totals =
    totals(d, s.open.createdAt, s.close?.createdAt ?: now, s.open.amount, s.open.id, named = s.open.drawer != null)

  fun open(d: ShopData, opening: Double, by: String, today: String, now: Long, newId: () -> String): Result {
    if (opening.isNaN() || opening < 0) return Result.Failed(StrLedger.openingBad)
    if (mainOpen(d)) return Result.Failed(StrLedger.tillAlreadyOpen)
    val who = by.trim()
    return Result.Ok(
      d.copy(
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = OPEN, date = today, amount = opening, createdAt = now,
          notes = "باز کردنِ صندوق با ${money(opening)} افغانی" + if (who.isNotEmpty()) " — $who" else "",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        )
      )
    )
  }

  /**
   *  PAY-17 — بازکردنِ کشوی نام‌دار («چند کشو»، `TillDrawer`): همان ردیفِ `shift_open` با `drawer` = نامِ کشو یا
   *  صندوقدار. نامِ تکراریِ کشوی بازِ دیگر رد می‌شود (دو صندوقِ هم‌نام در یک لحظه گیج می‌کند).
   */
  fun openNamed(d: ShopData, opening: Double, drawer: String, by: String, today: String, now: Long, newId: () -> String): Result {
    if (opening.isNaN() || opening < 0) return Result.Failed(StrLedger.openingBad)
    val name = Drawers.label(drawer) ?: return Result.Failed(StrLedger.drawerNameNeeded(Drawers.LABEL_MAX))
    if (list(d).any { it.isOpen && it.open.drawer == name }) return Result.Failed(StrLedger.drawerAlreadyOpen(name))
    val who = by.trim()
    return Result.Ok(
      d.copy(
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = OPEN, date = today, amount = opening, createdAt = now, drawer = name, by = who.ifEmpty { null },
          notes = "باز کردنِ کشوی «$name» با ${money(opening)} افغانی" + if (who.isNotEmpty()) " — $who" else "",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        )
      )
    )
  }

  fun close(d: ShopData, counted: Double, note: String, by: String, today: String, now: Long, newId: () -> String): Result {
    if (counted.isNaN() || counted < 0) return Result.Failed(StrLedger.countedBad)
    val shift = current(d) ?: return Result.Failed(StrLedger.noOpenTill)
    val t = totalsOf(d, shift, now)
    val diff = counted - t.expected
    val who = by.trim()
    val extra = listOfNotNull(note.trim().ifEmpty { null }, who.ifEmpty { null }).joinToString(" — ")
    return Result.Ok(
      d.copy(
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = CLOSE, date = today, refId = shift.open.id, amount = counted, createdAt = now,
          notes = "بستنِ صندوق: مورد انتظار ${money(t.expected)}، شمرده ${money(counted)}، " +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
            (if (kotlin.math.abs(diff) < 0.5) "بی اختلاف" else if (diff > 0) "اضافه ${money(diff)}" else "کسری ${money(-diff)}") +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
            if (extra.isNotEmpty()) " — $extra" else "",
        )
      )
    )
  }
}
