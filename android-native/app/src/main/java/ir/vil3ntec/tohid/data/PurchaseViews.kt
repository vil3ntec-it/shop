package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  «خریدها» (e-rows-63b، خواستهٔ صاحب‌کار: «نمی‌شود خریدهایی که کردم را ببینم … بارکد، نامِ اقلام … آورد یا نیاورد، پولش داده
 *  شده یا بعداً») — یک نما برای هر خرید، خالص و زیرِ آزمون، فقط **خواندنِ** همان دفتر:
 *   • فاکتورِ خریدِ دفتر (`PurchaseBook.invoices`): همیشه «رسیده» (ثبتش همان لحظه کالا را به انبار برد)؛ مانده همان
 *     `PurchaseBook.open` (پرداخت‌های بعدی، سندهای کسر و پرداختِ نام‌دارِ همان فاکتور).
 *   • سندِ کسر (برگشت به تأمین‌کننده): ردیفِ خودش، مبلغِ منفی.
 *   • سفارشِ خریدِ باز از سرور (فرستاده/بخشی، پرچمِ `shop.purchase_orders`): «هنوز نرسیده» / «نیمه‌رسیده». ردیف‌هایی که
 *     رسیدهایش در دفتر ساخته‌اند (`PurchaseOrderBook.orderNoOf`) در همان سفارش تا می‌شوند — یک خرید، نه دو.
 *  ⛔ هیچ نوشتنی این‌جا نیست؛ هیچ دفترِ موازی‌ای هم نیست.
 */
object PurchaseViews {

  /** حالِ پرداخت: [NONE] = هنوز صورت‌حسابی نیست (سفارشِ نرسیده) یا خریدِ رایگان */
  enum class Pay { PAID, PARTIAL, CREDIT, OVERDUE, NONE }

  enum class Receipt { RECEIVED, PARTIAL, PENDING }

  enum class Kind { BILL, RETURN, ORDER }

  data class Line(
    val productId: String,
    /** نامِ کالا؛ کالای حذف‌شده ⇒ نامِ ردیفِ سفارش یا خالی (صفحه «کالای حذف‌شده» می‌نویسد) */
    val name: String,
    /** نخستین بارکد، وگرنه کدِ کالا (`sku`)؛ هیچ ⇒ null */
    val code: String?,
    /** مقدار به واحدِ همان ردیف (واحدِ خرید اگر بود: «۲ کارتن») */
    val qty: Double,
    val unit: String,
    /** فیِ یک [unit] */
    val unitCost: Double,
    val total: Double,
    /** سفارش‌داده و رسیده به همان [unit]؛ خریدِ دفتر هر دو = [qty] */
    val ordered: Double,
    val received: Double,
    val batch: String = "",
    val expiry: String = "",
    /** شناسهٔ ردیفِ خریدِ دفتر (برای سفارش: null) */
    val purchaseId: String? = null,
    /** مقدار به واحدِ پایه وقتی ردیف به واحدِ خرید است («۴۸ دانه»)؛ وگرنه null */
    val baseQty: Double? = null,
    val baseUnit: String = "",
    /** PUR-11: فی و جمعِ ردیفِ فاکتورِ ارزی به واحدِ کوچکِ همان ارز (افغانی ⇒ null) */
    val fxUnit: Long? = null,
    val fxTotal: Long? = null,
    val production: String = "",
  )

  data class View(
    /** کلیدِ مسیر: کلیدِ فاکتورِ دفتر («#۱۲»، «#۱۲·تأمین‌کننده» یا شناسهٔ خریدِ تک‌ردیفه) یا «po:<شناسهٔ سفارش>» */
    val key: String,
    val kind: Kind,
    /** شمارهٔ فاکتور یا سفارش؛ ۰ = خریدِ تک‌ردیفهٔ قدیمی */
    val no: Int,
    val supplierId: String,
    val supplierName: String,
    val date: String,
    val createdAt: Long,
    val lines: List<Line>,
    /** مبلغِ خرید (سفارش: جمعِ سفارش‌داده؛ سندِ کسر: منفی) */
    val total: Double,
    /** آنچه صورت‌حساب شده: خریدِ دفتر = [total]؛ سفارش = جمعِ رسیده */
    val billed: Double,
    val paid: Double,
    val remaining: Double,
    val dueDate: String?,
    val pay: Pay,
    val receipt: Receipt,
    val orderId: String? = null,
    /** فاکتوری که از رسیدِ سفارشِ #n آمده (پیوندِ سفارش ⇔ خرید) */
    val orderNo: Int? = null,
    val landed: Double = 0.0,
    val notes: String = "",
    /** فاکتورِ دفتر (چاپ، برگشت)؛ سفارش ⇒ null */
    val invoice: PurchaseBook.Invoice? = null,
    /**
     *  PUR-11: فاکتورِ ارزی — ارز، نرخِ ثبت‌شده، جمع و ماندهٔ همان ارز (واحدِ کوچک). [total]/[paid]/[remaining] همچنان افغانی
     *  (معادل و ارزشِ دفتری). افغانی ⇒ null.
     */
    val currency: String? = null,
    val fxRate: ir.vil3ntec.tohid.core.purchase.PurchaseFx.Rate? = null,
    val fxTotal: Long = 0,
    val fxRemaining: Long? = null,
  ) {
    val names: List<String> get() = lines.map { it.name }.filter { it.isNotBlank() }.distinct()
    val isOrder: Boolean get() = kind == Kind.ORDER
  }

  private const val CENT = 0.5

  /** حالِ پرداخت از مبلغِ صورت‌حساب، پرداخت‌شده، مانده و سررسید (`yyyy-MM-dd`) */
  fun payOf(billed: Double, paid: Double, remaining: Double, due: String?, today: String): Pay = when {
    billed <= CENT -> Pay.NONE
    remaining <= CENT -> Pay.PAID
    due != null && today.isNotBlank() && due < today -> Pay.OVERDUE
    paid > CENT -> Pay.PARTIAL
    else -> Pay.CREDIT
  }

  /* ── ساختن ───────────────────────────────────────────────────────── */

  /**
   *  همهٔ خریدها، تازه‌ترین بالا. [orders] = سفارش‌های بازِ سرور (فرستاده/بخشی)؛ خالی (بی اینترنت یا پرچمِ خاموش) ⇒ فقط دفتر،
   *  و فاکتورهای رسیدِ سفارش همان ردیف‌های «رسیده»ٔ خودشان‌اند.
   */
  fun all(d: ShopData, today: String, orders: List<PurchaseOrderDto> = emptyList()): List<View> {
    return buildViews(Ctx(d, today), orders, null)
  }

  private fun buildViews(ctx: Ctx, orders: List<PurchaseOrderDto>, supplierId: String?): List<View> {
    val invoices = if (supplierId == null) ctx.invoices else ctx.invoices.filter { it.supplierId == supplierId }
    val open = orders.filter { it.isOpen() && (supplierId == null || it.supplierId == supplierId) }
    val folded = HashSet<String>()
    val orderViews = open.map { o ->
      val mine = invoices.filter { inv -> inv.supplierId == o.supplierId && orderNoOf(inv) == o.no }
      mine.forEach { folded += it.key }
      ctx.order(o, mine)
    }
    val bills = invoices.filter { it.key !in folded }.map(ctx::bill)
    return (bills + orderViews).sortedWith(compareByDescending<View> { it.date }.thenByDescending { it.createdAt })
  }

  /** یک خرید با کلیدِ مسیرش ([View.key])؛ نبود ⇒ null */
  fun find(d: ShopData, key: String, today: String, orders: List<PurchaseOrderDto> = emptyList()): View? =
    Ctx(d, today).let { c ->
      c.invoices.firstOrNull { it.key == key }?.let(c::bill)
        ?: orders.firstOrNull { it.isOpen() && orderKey(it.id) == key }?.let { order ->
          c.order(order, c.invoices.filter { it.supplierId == order.supplierId && orderNoOf(it) == order.no })
        }
    }

  /** خریدهای یک تأمین‌کننده (صفحهٔ تأمین‌کننده) */
  fun ofSupplier(d: ShopData, supplierId: String, today: String, orders: List<PurchaseOrderDto> = emptyList()): List<View> =
    buildViews(Ctx(d, today), orders, supplierId)

  fun orderKey(orderId: String): String = "po:$orderId"

  private fun PurchaseOrderDto.isOpen(): Boolean = PurchaseOrderBook.status(this).let {
    it == PurchaseOrderCore.Status.SENT || it == PurchaseOrderCore.Status.PARTIAL
  }

  private fun orderNoOf(inv: PurchaseBook.Invoice): Int? = inv.lines.firstNotNullOfOrNull(PurchaseOrderBook::orderNoOf)

  private class Ctx(val d: ShopData, val today: String) {
    val products = ShopStore.productsById(d.products)
    val suppliers = d.suppliers.associateBy { it.id }
    val invoices = PurchaseBook.invoices(d)
    private val due = PurchaseBook.open(d)
    val remaining = due.associate { it.invoice.key to it.remaining }
    /** PUR-11: ماندهٔ ارزیِ فاکتورهای ارزی */
    val fxRemaining = due.mapNotNull { x -> x.fxRemaining?.let { x.invoice.key to it } }.toMap()
    val entries = d.warehouseEntries.filter { it.purchaseId != null }.associateBy { it.purchaseId!! }
    /** خریدِ قدیمیِ بی `purchaseId` روی ورود ⇒ با `warehouseEntryId` (یک نقشه، نه جست‌وجوی خطی برای هر ردیف) */
    val entryById by lazy { d.warehouseEntries.associateBy { it.id } }

    fun name(supplierId: String, fallback: String = "") = suppliers[supplierId]?.name ?: fallback

    fun code(p: Product?): String? = p?.barcodes?.firstOrNull { it.isNotBlank() } ?: p?.sku?.takeIf { it.isNotBlank() }

    fun bill(inv: PurchaseBook.Invoice): View {
      val lines = inv.lines.map(::line)
      if (inv.isReturn) {
        return View(
          inv.key, Kind.RETURN, inv.no, inv.supplierId, name(inv.supplierId), inv.date, inv.createdAt, lines,
          total = inv.total, billed = inv.total, paid = 0.0, remaining = 0.0, dueDate = null, pay = Pay.NONE,
          receipt = Receipt.RECEIVED, notes = notesOf(inv), invoice = inv,
          currency = inv.currency, fxRate = inv.fxRate, fxTotal = inv.fxTotal,
        )
      }
      val left = (remaining[inv.key] ?: inv.debt).coerceAtLeast(0.0)
      val paid = (inv.total - left).coerceAtLeast(0.0)
      return View(
        inv.key, Kind.BILL, inv.no, inv.supplierId, name(inv.supplierId), inv.date, inv.createdAt, lines,
        total = inv.total, billed = inv.total, paid = paid, remaining = left, dueDate = inv.dueDate,
        pay = payOf(inv.total, paid, left, inv.dueDate, today), receipt = Receipt.RECEIVED,
        orderNo = orderNoOf(inv), landed = inv.lines.sumOf { it.landedCost ?: 0.0 }, notes = notesOf(inv), invoice = inv,
        currency = inv.currency, fxRate = inv.fxRate, fxTotal = inv.fxTotal,
        fxRemaining = if (inv.currency != null) (fxRemaining[inv.key] ?: (inv.fxTotal - inv.fxPaid)).coerceAtLeast(0L) else null,
      )
    }

    /** یادداشتِ کاربر بی تکهٔ سیستمیِ «فاکتورِ خرید #n» ([PurchaseBook.record]) و «سفارشِ خرید #n — رسید» */
    fun notesOf(inv: PurchaseBook.Invoice): String = inv.lines.map { userNote(it.notes) }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")

    fun line(p: Purchase): Line {
      val product = products[p.productId]
      val e = entries[p.id] ?: p.warehouseEntryId?.let(entryById::get)
      val inUnit = p.unitName?.takeIf { it.isNotBlank() && (p.unitQtyMilli ?: 0) > 0 }
      val qty = if (inUnit != null) p.unitQtyMilli!! / 1000.0 else kotlin.math.abs(p.quantity)
      val sign = if (p.totalAmount < 0) -1.0 else 1.0
      val total = kotlin.math.abs(p.totalAmount)
      val cost = if (inUnit != null && qty > 0) total / qty else p.purchasePrice
      return Line(
        productId = p.productId, name = product?.name.orEmpty(), code = p.barcode ?: code(product),
        qty = qty, unit = inUnit ?: p.unit, unitCost = cost, total = sign * total, ordered = qty, received = qty,
        batch = e?.batch.orEmpty(), expiry = e?.expiryDate.orEmpty(), purchaseId = p.id, production = e?.productionDate.orEmpty(),
        baseQty = if (inUnit != null) kotlin.math.abs(p.quantity) else null, baseUnit = if (inUnit != null) p.unit else "",
        fxUnit = if (SupplierFx.cur(p.currency) != null) p.fxPriceMinor else null,
        fxTotal = if (SupplierFx.cur(p.currency) != null) p.fxTotalMinor else null,
      )
    }

    fun order(o: PurchaseOrderDto, posted: List<PurchaseBook.Invoice>): View {
      val progress = o.progress.associateBy { it.lineId }
      val lines = o.lines.map { l ->
        val pr = progress[l.id]
        val product = products[l.productId]
        val ordered = PurchaseOrderBook.units(pr?.orderedMilli ?: l.qtyMilli)
        val price = PurchaseOrderBook.asAfn(l.priceMinor)
        Line(
          productId = l.productId, name = product?.name ?: l.name, code = code(product),
          qty = ordered, unit = l.unit, unitCost = price, total = Math.round(ordered * price).toDouble(),
          ordered = ordered, received = PurchaseOrderBook.units(pr?.acceptedMilli ?: 0L),
        )
      }
      val billed = posted.sumOf { it.total }
      val left = posted.sumOf { (remaining[it.key] ?: it.debt).coerceAtLeast(0.0) }
      val paid = (billed - left).coerceAtLeast(0.0)
      val due = posted.mapNotNull { it.dueDate }.minOrNull()
      val status = PurchaseOrderBook.status(o)
      val at = o.sentAt?.takeIf { it > 0 } ?: o.createdAt
      return View(
        orderKey(o.id), Kind.ORDER, o.no, o.supplierId, name(o.supplierId, o.supplierName),
        date = if (at > 0) StockLedger.isoOf(at) else today, createdAt = at, lines = lines,
        total = PurchaseOrderBook.asAfn(o.totals.orderedMinor), billed = billed, paid = paid, remaining = left, dueDate = due,
        pay = payOf(billed, paid, left, due, today),
        receipt = if (status == PurchaseOrderCore.Status.PARTIAL) Receipt.PARTIAL else Receipt.PENDING,
        orderId = o.id, orderNo = o.no, notes = o.note,
      )
    }

  }

  /** یادداشتِ کاربرِ یک ردیف: تکهٔ «فاکتورِ خرید #n — » برداشته؛ ردیفِ رسیدِ سفارش یادداشتِ کاربر ندارد */
  internal fun userNote(notes: String): String = when {
    notes.startsWith(BILL_TAG) -> notes.substringAfter(" — ", "")
    notes.startsWith(ORDER_TAG) -> ""
    else -> notes
  }.trim()

  private const val BILL_TAG = "فاکتورِ خرید #"  // داده: همان تکهٔ `PurchaseBook.record`
  private const val ORDER_TAG = "سفارشِ خرید #"  // داده: همان `PurchaseOrderBook.postedNote`

  /* ── پالایه و جست‌وجو ─────────────────────────────────────────────── */

  enum class PayFilter { ALL, UNPAID, OVERDUE, PAID }

  enum class ReceiptFilter { ALL, RECEIVED, NOT_RECEIVED }

  data class Filter(
    val supplierId: String? = null,
    val pay: PayFilter = PayFilter.ALL,
    val receipt: ReceiptFilter = ReceiptFilter.ALL,
    /** کلیدِ دوره ([PurchasePeriods]); null = همهٔ زمان‌ها */
    val period: String? = null,
  ) {
    val active: Boolean get() = supplierId != null || pay != PayFilter.ALL || receipt != ReceiptFilter.ALL || period != null
  }

  fun matches(v: View, f: Filter, gregorian: Boolean, today: String): Boolean {
    if (f.supplierId != null && v.supplierId != f.supplierId) return false
    val payOk = when (f.pay) {
      PayFilter.ALL -> true
      PayFilter.UNPAID -> v.remaining > CENT
      PayFilter.OVERDUE -> v.pay == Pay.OVERDUE
      PayFilter.PAID -> v.pay == Pay.PAID
    }
    if (!payOk) return false
    val receiptOk = when (f.receipt) {
      ReceiptFilter.ALL -> true
      ReceiptFilter.RECEIVED -> v.receipt == Receipt.RECEIVED
      ReceiptFilter.NOT_RECEIVED -> v.receipt != Receipt.RECEIVED
    }
    if (!receiptOk) return false
    return f.period == null || PurchasePeriods.contains(f.period, v.date, gregorian, today)
  }

  /**
   *  جست‌وجو: نامِ کالا (فارسی، انگلیسی، پشتو)، بارکد (بسته و واحدِ خرید هم)، کدِ کالا، نامِ تأمین‌کننده یا «#شماره» ⇒ خریدهایی
   *  که آن کالا را دارند. نوشتهٔ خالی ⇒ همه.
   */
  fun search(d: ShopData, list: List<View>, query: String): List<View> {
    val raw = ir.vil3ntec.tohid.normalizeDigits(query).trim()
    if (raw.isEmpty()) return list
    val q = TextNormalizer.searchKey(raw)
    val no = raw.removePrefix("#").toIntOrNull()?.takeIf { raw.startsWith("#") }
    val hit = HashSet<String>()
    d.products.forEach { p -> if (productHit(p, q, raw)) hit += p.id }
    ShopStore.barcodeIndex(d)[raw]?.let { hit += it }
    return list.filter { v ->
      (no != null && v.no == no) ||
        TextNormalizer.searchKey(v.supplierName).contains(q) ||
        v.lines.any { it.productId in hit || TextNormalizer.searchKey(it.name).let { n -> n.isNotEmpty() && n.contains(q) } }
    }
  }

  private fun productHit(p: Product, q: String, raw: String): Boolean {
    if (listOfNotNull(p.name, p.nameEn, p.namePs).any { TextNormalizer.searchKey(it).contains(q) }) return true
    val codes = p.barcodes + listOfNotNull(p.sku) + p.packs.orEmpty().flatMap { it.barcodes } + p.purchaseUnits.orEmpty().mapNotNull { it.barcode }
    return codes.any { it.isNotBlank() && (it == raw || (raw.length >= 3 && it.contains(raw))) }
  }

  /** جمع‌های فهرستِ دیده‌شده: مبلغِ خریدها (بی سندِ کسر)، مانده، شمارِ سررسیدگذشته */
  data class Totals(val count: Int, val bought: Double, val remaining: Double, val overdue: Int)

  fun totals(list: List<View>): Totals = Totals(
    count = list.count { it.kind != Kind.RETURN },
    bought = list.filter { it.kind != Kind.RETURN }.sumOf { it.total },
    remaining = list.sumOf { it.remaining },
    overdue = list.count { it.pay == Pay.OVERDUE },
  )
}
