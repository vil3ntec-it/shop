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
 *   + نقدِ هر فاکتورِ لغونشده (`finalTotal − debtGiven`: نقدی کامل، نسیه فقط پیش‌پرداخت)
 *   + رسیدِ قرض‌داران (`receive`)
 *   − مرجوعی‌ها (کلِ مبلغ؛ بخشی که از بدهی کم شد خودش یک `receive` است، پس خالص درست است)
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

  /** فاکتورِ اینترنتیِ سرور — پولش به دخل نمی‌آید */
  fun isOnline(s: Sale): Boolean = s.channel == ONLINE || s.series == ONLINE_SERIES

  /**
   *  مرجوعیِ سرور برای سفارشِ اینترنتی. ردیف‌های پیش از نشانِ `channel` با شناسهٔ
   *  ثابتِ سرور (`w-…`، هرگز ULID ِ دستگاه) و فاکتورِ اینترنتی شناخته می‌شوند.
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
  ) {
    val expected: Double get() = opening + sales + receipts - returns - expenses - suppliers
  }

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

  fun totals(d: ShopData, from: Long, to: Long, opening: Double): Totals {
    fun inWindow(at: Long) = at in from..to
    val online = d.sales.filter(::isOnline).mapTo(HashSet()) { it.id }
    return Totals(
      opening = opening,
      sales = d.sales.filter { it.status != "cancelled" && !isOnline(it) && inWindow(it.createdAt) }
        .sumOf { (it.finalTotal - it.debtGiven).coerceAtLeast(0.0) },
      receipts = d.transactions.filter { it.type == "receive" && inWindow(it.createdAt) }.sumOf { it.amount },
      returns = d.saleReturns.filter { inWindow(it.createdAt) && !isOnlineReturn(it, online) }.sumOf { it.amount },
      expenses = d.expenses.filter { inWindow(it.createdAt) }.sumOf { it.amount },
      suppliers = d.supplierPayments.filter { inWindow(it.createdAt) }.sumOf { it.amount } +
        d.purchases.filter { inWindow(it.createdAt) }.sumOf { it.paidAmount },
    )
  }

  fun totalsOf(d: ShopData, s: Shift, now: Long): Totals =
    totals(d, s.open.createdAt, s.close?.createdAt ?: now, s.open.amount)

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
