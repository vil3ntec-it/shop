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
    /** PUR-11 — فیِ اصلیِ ارزی (صدمِ واحد)؛ فقط با `record(fx = …)`، که آن‌گاه [price] از همین ساخته می‌شود */
    val fxPriceMinor: Long? = null,
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
    /*
     *  PUR-11 — ارز و نرخِ همین فاکتور (`PurchaseFx`)؛ `null` = افغانی، همان راهِ امروز. با نرخ: فیِ هر ردیف از
     *  `fxPriceMinor` با همین نرخ به افغانی می‌رود و از آن پس همه چیز همان راهِ افغانی است؛ پرداخت و هزینهٔ جانبی افغانی‌اند.
     */
    fx: PurchaseFx.Rate? = null,
  ): Result {
    if (fx != null) {
      PurchaseFx.check(fx)?.let { return Result.Failed(it) }
      val conv = ArrayList<Line>(lines.size)
      var n = 0
      for (l in lines) {
        if (l.productId.isBlank()) { conv += l; continue }
        n++
        val fp = l.fxPriceMinor?.takeIf { it in 0..PurchaseFx.MAX_PRICE_MINOR } ?: return Result.Failed("ردیفِ $n: ${PurchaseFx.BAD_PRICE}")
        val afn = PurchaseFx.toAfnMinor(fp, fx.rateMicros) ?: return Result.Failed("ردیفِ $n: ${PurchaseFx.TOO_LARGE}")
        if (PurchaseFx.lineTotalMinor(l.quantity, fp) == null) return Result.Failed("ردیفِ $n: ${PurchaseFx.TOO_LARGE}")
        conv += l.copy(price = afn / 100.0)
      }
      val r = record(d, supplierId, conv, paid, date, notes, today, now, newId, landedTotal, locationId, userId, dueDate)
      if (r !is Result.Ok) return r
      val made = r.data.purchases.filter { it.invoiceNo == r.invoiceNo }
      val real = conv.filter { it.productId.isNotBlank() }
      if (made.size != real.size) return Result.Failed(PurchaseFx.TOO_LARGE)
      //  ردیف‌های همین فاکتور به همان ترتیبِ ثبت (`ids` ِ پایین) — هر کدام فیِ ارزیِ خودش
      val stamped = made.zip(real).associate { (p, l) -> p.id to PurchaseFx.stamp(p, fx, l.fxPriceMinor!!) }
      return Result.Ok(r.data.copy(purchases = r.data.purchases.map { stamped[it.id] ?: it }), r.invoiceNo)
    }
    if (d.suppliers.none { it.id == supplierId }) return Result.Failed("تأمین‌کننده را انتخاب کنید")
    if (landedTotal.isNaN() || landedTotal < 0) return Result.Failed("هزینهٔ جانبی درست نیست")
    val due = dueDate?.trim()?.ifEmpty { null }
    if (due != null && due < date.ifBlank { today }) return Result.Failed(DUE_BEFORE_DATE)
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
          if (share > 0) p.copy(landedCost = share / 100.0, landedCostMinor = share) else p
        },
        warehouseEntries = cur.warehouseEntries.map { w ->
          val pid = w.purchaseId
          if (pid == null || pid !in set) w
          else w.copy(
            landedCost = (landed[pid] ?: 0L).takeIf { it > 0 }?.let { it / 100.0 },
            landedCostMinor = (landed[pid] ?: 0L).takeIf { it > 0 },
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
    /** PUR-11 — ارز، نرخ و جمعِ ارزیِ سند؛ `null` = فاکتورِ افغانی */
    val fx: PurchaseFx.Summary? get() = PurchaseFx.summary(lines)
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
    val over = if (today.isBlank()) 0.0 else overdue(d, today, s.id).sumOf { it.remaining }
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

  /** فاکتورِ خرید با ماندهٔ **واقعی**اش (پس از پرداخت‌های بعدی و سندهای کسر) */
  data class Due(val invoice: Invoice, val remaining: Double) {
    val supplierId: String get() = invoice.supplierId
    val dueDate: String? get() = invoice.dueDate
  }

  /**
   *  ماندهٔ واقعیِ هر فاکتورِ خرید. `Invoice.debt` فقط پرداختِ هنگامِ خرید را می‌بیند؛
   *  پرداخت‌های بعدی به تأمین‌کننده (`supplierPayments`)، سندهای کسر (برگشت، PUR-08) و
   *  اضافه‌پرداختِ هر فاکتور به ترتیب روی **قدیمی‌ترین** فاکتورها می‌نشینند (FIFO). پس جمعِ
   *  مانده‌ها همان `max(0, supplierDebt)` است و یادآورِ سررسید با پرداخت پاک می‌شود.
   */
  fun open(d: ShopData, supplierId: String? = null): List<Due> {
    val out = ArrayList<Due>()
    invoices(d, supplierId).groupBy { it.supplierId }.forEach { (sid, list) ->
      val bills = list.filter { !it.isReturn }
      var credit = d.supplierPayments.filter { it.supplierId == sid }.sumOf { it.amount } +
        list.filter { it.isReturn }.sumOf { -it.total } +
        bills.sumOf { (-it.debt).coerceAtLeast(0.0) }
      bills.sortedWith(compareBy<Invoice> { it.date }.thenBy { it.createdAt }.thenBy { it.key }).forEach { inv ->
        val debt = inv.debt.coerceAtLeast(0.0)
        val take = minOf(debt, credit.coerceAtLeast(0.0))
        credit -= take
        out += Due(inv, debt - take)
      }
    }
    return out
  }

  /** فاکتورهای خریدِ دارای ماندهٔ واقعی که سررسیدشان پیش از امروز است، زودترین سررسید بالا */
  fun overdue(d: ShopData, today: String, supplierId: String? = null): List<Due> =
    open(d, supplierId).filter { it.remaining > 0.5 && it.dueDate.let { due -> due != null && due < today } }
      .sortedBy { it.dueDate }

  /** فاکتورهای دارای ماندهٔ واقعی که تا [days] روزِ دیگر سررسید می‌شوند (امروز هم) */
  fun dueSoon(d: ShopData, today: String, days: Int = 7): List<Due> {
    val limit = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(today, days) ?: return emptyList()
    return open(d).filter { it.remaining > 0.5 && it.dueDate.let { due -> due != null && due >= today && due <= limit } }
      .sortedBy { it.dueDate }
  }

  /* ── برگشت به تأمین‌کننده با سندِ کسر (PUR-08) ─────────────────── */

  /** آخرین قیمتِ خریدِ یک کالا از یک تأمین‌کننده (پیش‌فرضِ قیمتِ برگشت، PUR-09) */
  fun lastPrice(d: ShopData, supplierId: String, productId: String): Double? =
    d.purchases.filter { it.supplierId == supplierId && it.productId == productId && it.quantity > 0 }
      .maxByOrNull { it.createdAt }?.purchasePrice

  /**
   *  سقفِ برگشت به یک تأمین‌کننده: خریدهای همین (تأمین‌کننده، کالا) منهای برگشت‌های پیشین به
   *  او (سندهای کسر = خریدِ منفی). بی این، کالای خریده از دیگری هم به او برمی‌گشت.
   */
  fun returnable(d: ShopData, supplierId: String, productId: String): Double =
    kotlin.math.max(0.0, d.purchases.filter { it.supplierId == supplierId && it.productId == productId }.sumOf { it.quantity })

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
    /**
     *  کلیدِ یکتای همین برگشت (پنجره یک بار می‌سازد): شناسهٔ سندِ کسر و ردیف‌هایش از آن
     *  ساخته می‌شود، پس دو کلیکِ پشتِ‌سرِهم دو سند نمی‌سازد؛ سندِ همین کلید اگر هست ⇒ رد.
     */
    key: String? = null,
  ): Result {
    val k = key?.trim()?.ifEmpty { null }
    if (k != null && d.purchases.any { it.id == k }) return Result.Failed(ALREADY_RETURNED)
    val supplier = d.suppliers.find { it.id == supplierId } ?: return Result.Failed("تأمین‌کننده را انتخاب کنید")
    val product = d.products.find { it.id == productId } ?: return Result.Failed("کالا را انتخاب کنید")
    if (quantity.isNaN() || quantity <= 0) return Result.Failed("مقدار معتبر وارد کنید")
    if (unitPrice.isNaN() || unitPrice < 0) return Result.Failed("قیمتِ برگشت معتبر نیست")
    val why = reason.trim()
    if (why.isEmpty()) return Result.Failed("دلیلِ برگشت را بنویسید")
    val stock = ShopStore.stock(d, productId)
    if (quantity > stock) return Result.Failed("فقط ${ir.vil3ntec.tohid.qty(kotlin.math.max(0.0, stock))} ${product.unit} موجود است")
    val cap = returnable(d, supplierId, productId)
    if (quantity > cap) return Result.Failed("از «${supplier.name}» فقط ${ir.vil3ntec.tohid.qty(cap)} ${product.unit} خریده شده که هنوز برنگشته")
    val day = date.ifBlank { today }
    if (!ir.vil3ntec.tohid.core.stock.StockCore.isoDateOk(day)) return Result.Failed("تاریخ درست نیست")
    val amount = Math.round(quantity * unitPrice).toDouble()
    val purchaseId = k ?: newId()
    val entryId = if (k != null) "$k-w" else newId()
    val user = userId?.trim()?.ifEmpty { null }
    val text = "برگشت به «${supplier.name}» — $why"
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries + WarehouseEntry(
          id = entryId, productId = productId, units = -quantity, unit = product.unit, price = unitPrice, date = day,
          notes = text, isAdjustment = true, purchaseId = purchaseId, createdAt = now, adjKind = "supplier_return", userId = user,
        ),
        stockMovements = d.stockMovements + StockMovement(
          id = if (k != null) "$k-m" else newId(), productId = productId, type = "supplier_return", qty = -quantity, date = day, notes = text,
          refId = entryId, createdAt = now, userId = user, reason = why,
        ),
        purchases = d.purchases + Purchase(
          id = purchaseId, productId = productId, supplierId = supplierId, quantity = -quantity, unit = product.unit,
          purchasePrice = unitPrice, totalAmount = -amount, date = day, notes = "سندِ کسر — برگشت به تأمین‌کننده: $why",
          paidAmount = 0.0, debt = -amount, warehouseEntryId = entryId, createdAt = now,
        ),
        auditLog = d.auditLog + AuditEntry(
          id = if (k != null) "$k-a" else newId(), type = "supplier_return", date = day, refId = entryId, createdAt = now,
          notes = "برگشت ${ir.vil3ntec.tohid.qty(quantity)} ${product.unit} «${product.name}» به «${supplier.name}» — سندِ کسرِ ${ir.vil3ntec.tohid.money(amount)} افغانی — دلیل: $why",
        ),
      ),
      0,
    )
  }

  const val ALREADY_RETURNED = "همین برگشت پیش‌تر ثبت شده است"

  /** سررسید پیش از تاریخِ فاکتور معنی ندارد (PUR-07) */
  const val DUE_BEFORE_DATE = "سررسید نباید پیش از تاریخِ خرید باشد"

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
    //  PUR-11 — اصلِ ارزیِ سند (فقط فاکتورِ ارزی؛ افغانی ⇒ همان برگهٔ امروز)
    inv.fx?.let { fx ->
      rows += listOf("", "ارز", fx.currency, "نرخ", PurchaseFx.rateText(fx.rateMicros), PurchaseFx.minorText(fx.totalMinor))
    }
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
