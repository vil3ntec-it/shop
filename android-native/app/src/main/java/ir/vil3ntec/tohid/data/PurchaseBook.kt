package ir.vil3ntec.tohid.data

/**
 *  فاکتورِ خریدِ چندردیفه و حسابِ تأمین‌کننده.
 *
 *  خواستهٔ صاحب مخزن: «فاکتور خرید و گزارش‌ها… خریدها چه مدلی باشه — اکسل
 *  جدولی.» یک فاکتورِ خرید = چند ردیف (کالا، مقدار، قیمت، انقضا) از یک
 *  تأمین‌کننده، با یک «پرداخت‌شده» برای کلِ فاکتور.
 *
 *  ⛔ هر ردیف همان `LedgerEngine.addPurchase` است — همان چهار اثر (انبار،
 *  حرکتِ انبار، بدهیِ تأمین‌کننده، تاریخچهٔ قیمت). این‌جا فقط ردیف‌ها زیرِ یک
 *  `invoiceNo` جمع می‌شوند و پرداخت میانِ ردیف‌ها به ترتیب پخش می‌شود، تا
 *  `ShopStore.supplierDebt` بی هیچ تغییری درست بماند.
 */
object PurchaseBook {

  data class Line(
    val productId: String,
    val quantity: Double,
    val price: Double,
    val unit: String = "",
    val expiryDate: String = "",
  )

  sealed interface Result {
    data class Ok(val data: ShopData, val invoiceNo: Int) : Result
    data class Failed(val message: String) : Result
  }

  /** شمارهٔ بعدیِ فاکتورِ خرید */
  fun nextNo(d: ShopData): Int = (d.purchases.maxOfOrNull { it.invoiceNo } ?: 0) + 1

  fun lineTotal(l: Line): Double = Math.round(l.quantity * l.price).toDouble()

  fun record(
    d: ShopData,
    supplierId: String,
    lines: List<Line>,
    paid: Double,
    date: String,
    notes: String,
    today: String,
    now: Long,
    newId: () -> String,
    /*
     *  موتورِ تازهٔ موجودی (`StockLedger`) — پیش‌فرض‌ها همان رفتارِ امروز‌اند.
     *  هزینهٔ جانبیِ کلِ فاکتور (کرایه، گمرک) به نسبتِ جمعِ هر ردیف روی بهای همان
     *  ردیف می‌نشیند (PUR-06)؛ به بدهیِ تأمین‌کننده اضافه **نمی‌شود**.
     */
    landedTotal: Double = 0.0,
    locationId: String? = null,
    userId: String? = null,
    /** سررسیدِ پرداختِ کلِ فاکتور (`yyyy-MM-dd`، PUR-07)؛ `null` = ندارد */
    dueDate: String? = null,
  ): Result {
    if (d.suppliers.none { it.id == supplierId }) return Result.Failed("تأمین‌کننده را انتخاب کنید")
    if (landedTotal.isNaN() || landedTotal < 0) return Result.Failed("هزینهٔ جانبی درست نیست")
    val real = lines.filter { it.productId.isNotBlank() }
    if (real.isEmpty()) return Result.Failed("دست‌کم یک ردیف کالا لازم است")
    real.forEachIndexed { i, l ->
      if (l.quantity.isNaN() || l.quantity <= 0) return Result.Failed("ردیفِ ${i + 1}: مقدار معتبر نیست")
      if (l.price.isNaN() || l.price < 0) return Result.Failed("ردیفِ ${i + 1}: قیمت معتبر نیست")
    }
    val total = real.sumOf { lineTotal(it) }
    var left = if (paid.isNaN() || paid < 0) 0.0 else minOf(paid, total)

    val no = nextNo(d)
    val tag = "فاکتورِ خرید #$no"
    var cur = d
    val ids = mutableListOf<String>()
    for ((i, l) in real.withIndex()) {
      val lt = lineTotal(l)
      val pay = minOf(left, lt)
      left -= pay
      val r = LedgerEngine.addPurchase(
        cur,
        LedgerEngine.PurchaseDraft(
          supplierId = supplierId, productId = l.productId, quantity = l.quantity, unit = l.unit,
          purchasePrice = l.price, paidAmount = pay, date = date,
          notes = listOf(tag, notes.trim()).filter { it.isNotBlank() }.joinToString(" — "),
          expiryDate = l.expiryDate,
          dueDate = dueDate,
        ),
        today, now, newId,
      )
      when (r) {
        is LedgerEngine.Result.Failed -> return Result.Failed("ردیفِ ${i + 1}: ${r.message}")
        is LedgerEngine.Result.Ok -> { cur = r.data; ids += r.id }
      }
    }
    val set = ids.toSet()
    cur = cur.copy(purchases = cur.purchases.map { if (it.id in set) it.copy(invoiceNo = no) else it })
    val landed = StockLedger.spreadLanded(StockLedger.minor(landedTotal), real.map { StockLedger.minor(lineTotal(it)) })
      .let { shares -> ids.zip(shares).toMap() }
    val loc = locationId?.trim()?.ifEmpty { null }
    val user = userId?.trim()?.ifEmpty { null }
    if (landed.values.any { it > 0 } || loc != null || user != null) {
      cur = cur.copy(
        purchases = cur.purchases.map { p ->
          val share = landed[p.id] ?: 0L
          if (share > 0) p.copy(landedCost = share / 100.0) else p
        },
        warehouseEntries = cur.warehouseEntries.map { w ->
          val pid = w.purchaseId
          if (pid == null || pid !in set) w
          else w.copy(
            landedCost = (landed[pid] ?: 0L).takeIf { it > 0 }?.let { it / 100.0 },
            locationId = loc,
            userId = user,
          )
        },
      )
    }
    return Result.Ok(cur, no)
  }

  /* ── فاکتورها ───────────────────────────────────────────────────── */

  data class Invoice(
    /** کلیدِ یکتا: «#۱۲» یا شناسهٔ خریدِ تک‌ردیفهٔ قدیمی */
    val key: String,
    val no: Int,
    val supplierId: String,
    val date: String,
    val lines: List<Purchase>,
  ) {
    val total: Double get() = lines.sumOf { it.totalAmount }
    val paid: Double get() = lines.sumOf { it.paidAmount }
    val debt: Double get() = total - paid
    val createdAt: Long get() = lines.maxOfOrNull { it.createdAt } ?: 0
    /** سندِ کسر (برگشت به تأمین‌کننده، PUR-08) — ردیفِ خریدِ منفی */
    val isReturn: Boolean get() = total < 0
    /** سررسیدِ پرداخت (PUR-07) — زودترین سررسیدِ ردیف‌ها */
    val dueDate: String? get() = lines.mapNotNull { it.dueDate?.takeIf { d -> d.isNotBlank() } }.minOrNull()
  }

  /** همهٔ فاکتورهای خرید، تازه‌ترین بالا */
  fun invoices(d: ShopData, supplierId: String? = null): List<Invoice> {
    val list = d.purchases.filter { supplierId == null || it.supplierId == supplierId }
    val grouped = list.filter { it.invoiceNo > 0 }.groupBy { it.invoiceNo }.map { (no, ls) ->
      Invoice("#$no", no, ls.first().supplierId, ls.minOf { it.date }, ls.sortedBy { it.createdAt })
    }
    val singles = list.filter { it.invoiceNo <= 0 }.map { Invoice(it.id, 0, it.supplierId, it.date, listOf(it)) }
    return (grouped + singles).sortedWith(compareByDescending<Invoice> { it.date }.thenByDescending { it.createdAt })
  }

  /* ── حسابِ تأمین‌کننده ─────────────────────────────────────────── */

  data class SupplierSummary(
    val supplier: Supplier,
    /** بدهیِ ما به او — همان `ShopStore.supplierDebt` */
    val balance: Double,
    val bought: Double,
    val invoices: Int,
    val lastPurchase: String?,
    /** ماندهٔ فاکتورهایی که سررسیدشان گذشته (PUR-07) — هرگز بیش از `balance` */
    val overdue: Double = 0.0,
    /** سندهای کسر (برگشت به تأمین‌کننده، PUR-08) */
    val returned: Double = 0.0,
  )

  fun suppliers(d: ShopData, today: String = ""): List<SupplierSummary> = d.suppliers.map { s ->
    val mine = d.purchases.filter { it.supplierId == s.id }
    val balance = ShopStore.supplierDebt(d, s.id)
    val over = if (today.isBlank()) 0.0 else overdue(d, today, s.id).sumOf { it.debt }
    SupplierSummary(
      supplier = s,
      balance = balance,
      bought = mine.filter { it.totalAmount > 0 }.sumOf { it.totalAmount },
      invoices = invoices(d, s.id).count { !it.isReturn },
      lastPurchase = mine.filter { it.totalAmount > 0 }.maxOfOrNull { it.date }?.takeIf { it.isNotBlank() },
      overdue = minOf(over, balance.coerceAtLeast(0.0)),
      returned = -mine.filter { it.totalAmount < 0 }.sumOf { it.totalAmount },
    )
  }

  /* ── سررسید و یادآور (PUR-07) ──────────────────────────────────── */

  /** فاکتورهای خریدِ دارای مانده که سررسیدشان پیش از امروز است، قدیمی‌ترین بالا */
  fun overdue(d: ShopData, today: String, supplierId: String? = null): List<Invoice> =
    invoices(d, supplierId).filter { !it.isReturn && it.debt > 0.5 && it.dueDate.let { due -> due != null && due < today } }
      .sortedBy { it.dueDate }

  /** فاکتورهای دارای مانده که تا [days] روزِ دیگر سررسید می‌شوند (امروز هم) */
  fun dueSoon(d: ShopData, today: String, days: Int = 7): List<Invoice> {
    val limit = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(today, days) ?: return emptyList()
    return invoices(d).filter { !it.isReturn && it.debt > 0.5 && it.dueDate.let { due -> due != null && due >= today && due <= limit } }
      .sortedBy { it.dueDate }
  }

  /* ── برگشت به تأمین‌کننده با سندِ کسر (PUR-08) ─────────────────── */

  /** آخرین قیمتِ خریدِ یک کالا از یک تأمین‌کننده (پیش‌فرضِ قیمتِ برگشت، PUR-09) */
  fun lastPrice(d: ShopData, supplierId: String, productId: String): Double? =
    d.purchases.filter { it.supplierId == supplierId && it.productId == productId && it.quantity > 0 }
      .maxByOrNull { it.createdAt }?.purchasePrice

  /** کالاهایی که از این تأمین‌کننده خریده شده‌اند — گزینه‌های برگشت */
  fun boughtFrom(d: ShopData, supplierId: String): List<Product> {
    val ids = d.purchases.filter { it.supplierId == supplierId && it.quantity > 0 }.map { it.productId }.toSet()
    return d.products.filter { it.id in ids }.sortedBy { it.name }
  }

  /**
   *  برگشت به تأمین‌کننده: کالا از انبار بیرون می‌رود (ردیفِ اصلاحِ `supplier_return`، همان
   *  شکلِ امروز) و یک **سندِ کسر** — ردیفِ خریدِ منفی — بدهیِ ما را کم می‌کند. پس
   *  `ShopStore.supplierDebt` (و برنامهٔ قدیمی) بی هیچ تغییری مانده را درست می‌گیرد؛ صندوق
   *  دست نمی‌خورد چون پولی جابه‌جا نشده (برخلافِ «پرداخت»).
   */
  fun supplierReturn(
    d: ShopData,
    supplierId: String,
    productId: String,
    quantity: Double,
    unitPrice: Double,
    reason: String,
    date: String,
    today: String,
    now: Long,
    newId: () -> String,
    userId: String? = null,
  ): Result {
    val supplier = d.suppliers.find { it.id == supplierId } ?: return Result.Failed("تأمین‌کننده را انتخاب کنید")
    val product = d.products.find { it.id == productId } ?: return Result.Failed("کالا را انتخاب کنید")
    if (quantity.isNaN() || quantity <= 0) return Result.Failed("مقدار معتبر وارد کنید")
    if (unitPrice.isNaN() || unitPrice < 0) return Result.Failed("قیمتِ برگشت معتبر نیست")
    val why = reason.trim()
    if (why.isEmpty()) return Result.Failed("دلیلِ برگشت را بنویسید")
    val stock = ShopStore.stock(d, productId)
    if (quantity > stock) return Result.Failed("فقط ${ir.vil3ntec.tohid.qty(kotlin.math.max(0.0, stock))} ${product.unit} موجود است")
    val day = date.ifBlank { today }
    if (!ir.vil3ntec.tohid.core.stock.StockCore.isoDateOk(day)) return Result.Failed("تاریخ درست نیست")
    val amount = Math.round(quantity * unitPrice).toDouble()
    val purchaseId = newId()
    val entryId = newId()
    val user = userId?.trim()?.ifEmpty { null }
    val text = "برگشت به «${supplier.name}» — $why"
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries + WarehouseEntry(
          id = entryId, productId = productId, units = -quantity, unit = product.unit, price = unitPrice, date = day,
          notes = text, isAdjustment = true, purchaseId = purchaseId, createdAt = now, adjKind = "supplier_return", userId = user,
        ),
        stockMovements = d.stockMovements + StockMovement(
          id = newId(), productId = productId, type = "supplier_return", qty = -quantity, date = day, notes = text,
          refId = entryId, createdAt = now, userId = user, reason = why,
        ),
        purchases = d.purchases + Purchase(
          id = purchaseId, productId = productId, supplierId = supplierId, quantity = -quantity, unit = product.unit,
          purchasePrice = unitPrice, totalAmount = -amount, date = day, notes = "سندِ کسر — برگشت به تأمین‌کننده: $why",
          paidAmount = 0.0, debt = -amount, warehouseEntryId = entryId, createdAt = now,
        ),
        auditLog = d.auditLog + AuditEntry(
          id = newId(), type = "supplier_return", date = day, refId = entryId, createdAt = now,
          notes = "برگشت ${ir.vil3ntec.tohid.qty(quantity)} ${product.unit} «${product.name}» به «${supplier.name}» — سندِ کسرِ ${ir.vil3ntec.tohid.money(amount)} افغانی — دلیل: $why",
        ),
      ),
      0,
    )
  }

  data class LedgerLine(
    val row: Int,
    val date: String,
    val text: String,
    /** خرید — بدهیِ ما بالا می‌رود */
    val bill: Double,
    /** پرداخت — بدهیِ ما پایین می‌آید */
    val pay: Double,
    val balance: Double,
    val invoiceKey: String?,
  )

  /**
   *  صورت‌حسابِ تأمین‌کننده: هر فاکتور یک ردیفِ «خرید» و پرداختِ همان‌جایش یک
   *  ردیفِ «پرداخت»، و هر پرداختِ بعدی ردیفِ خودش. ماندهٔ آخر = `supplierDebt`.
   */
  fun ledger(d: ShopData, supplierId: String): List<LedgerLine> {
    data class Ev(val date: String, val at: Long, val order: Int, val text: String, val bill: Double, val pay: Double, val key: String?)
    val evs = mutableListOf<Ev>()
    invoices(d, supplierId).forEach { inv ->
      //  سندِ کسر (برگشت به تأمین‌کننده) در ستونِ «پرداخت» می‌نشیند — همان اثر روی مانده
      if (inv.isReturn) {
        evs += Ev(inv.date, inv.createdAt, 0, "برگشت به تأمین‌کننده (سندِ کسر)", 0.0, -inv.total, inv.key)
        return@forEach
      }
      val title = if (inv.no > 0) "فاکتورِ خرید #${inv.no} (${inv.lines.size} ردیف)" else "خرید"
      evs += Ev(inv.date, inv.createdAt, 0, title, inv.total, 0.0, inv.key)
      if (inv.paid > 0) evs += Ev(inv.date, inv.createdAt, 1, "پرداخت هنگامِ خرید", 0.0, inv.paid, inv.key)
    }
    d.supplierPayments.filter { it.supplierId == supplierId }.forEach { p ->
      evs += Ev(p.date, p.createdAt, 2, p.notes.ifBlank { "پرداخت" }, 0.0, p.amount, null)
    }
    var run = 0.0
    return evs.sortedWith(compareBy<Ev> { it.date }.thenBy { it.at }.thenBy { it.order })
      .mapIndexed { i, e ->
        run += e.bill - e.pay
        LedgerLine(i + 1, e.date, e.text, e.bill, e.pay, run, e.key)
      }
  }

  /* ── برگه‌های اکسل و چاپ ─────────────────────────────────────────── */

  private fun r(v: Double) = Math.round(v).toString()
  private fun q(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
  private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "تأمین‌کننده" }

  fun invoiceSheet(d: ShopData, inv: Invoice): ReportCsv.Sheet {
    val names = d.products.associate { it.id to it.name }
    val supplier = d.suppliers.find { it.id == inv.supplierId }?.name.orEmpty()
    val rows = mutableListOf(listOf("ردیف", "کالا", "مقدار", "واحد", "فی", "جمع"))
    inv.lines.forEachIndexed { i, p ->
      rows += listOf((i + 1).toString(), names[p.productId].orEmpty(), q(p.quantity), p.unit, r(p.purchasePrice), r(p.totalAmount))
    }
    rows += listOf("", "جمع", "", "", "", r(inv.total))
    rows += listOf("", "پرداخت‌شده", "", "", "", r(inv.paid))
    rows += listOf("", "مانده", "", "", "", r(inv.debt))
    val title = if (inv.no > 0) "فاکتور-خرید-${inv.no}" else "خرید"
    return ReportCsv.Sheet("$title-${safe(supplier)}-${inv.date}", rows)
  }

  fun invoicesSheet(d: ShopData, list: List<Invoice>, today: String): ReportCsv.Sheet {
    val names = d.suppliers.associate { it.id to it.name }
    val rows = mutableListOf(listOf("فاکتور", "تاریخ", "تأمین‌کننده", "ردیف‌ها", "جمع", "پرداخت‌شده", "مانده"))
    list.forEach { v ->
      rows += listOf(if (v.no > 0) v.no.toString() else "", v.date, names[v.supplierId].orEmpty(), v.lines.size.toString(), r(v.total), r(v.paid), r(v.debt))
    }
    return ReportCsv.Sheet("فاکتورهای-خرید-$today", rows)
  }

  fun ledgerSheet(d: ShopData, supplier: Supplier, today: String): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("ردیف", "تاریخ", "شرح", "خرید", "پرداخت", "مانده"))
    ledger(d, supplier.id).forEach { l ->
      rows += listOf(l.row.toString(), l.date, l.text, if (l.bill > 0) r(l.bill) else "", if (l.pay > 0) r(l.pay) else "", r(l.balance))
    }
    return ReportCsv.Sheet("حساب-${safe(supplier.name)}-$today", rows)
  }
}
