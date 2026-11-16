package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSheets
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrStock
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
    /**
     *  PRD-07 — نامِ یکی از واحدهای خریدِ کالا (`Product.purchaseUnits`): `quantity` به همان واحد (۲ کارتن) و `price`
     *  قیمتِ یک واحدِ همان (یک کارتن) است؛ موجودی مقدار × ضریب به واحدِ پایه بالا می‌رود و بهای واحدِ پایه از جمعِ دقیقِ
     *  ردیف (`LedgerEngine.InUnit`). `null` = واحدِ پایه، همان امروز.
     */
    val unitName: String? = null,
    /**
     *  PUR-11 — فیِ ردیف به واحدِ کوچکِ ارزِ سند (سنت، ریال؛ به همان واحدِ [quantity]) وقتی فاکتور به ارزِ دیگر است ([record] با
     *  `fx`)؛ آن‌جا [price] نادیده است و بهای افغانی از معادلِ سند می‌آید. فاکتورِ افغانی آن را نادیده می‌گیرد.
     */
    val fxPrice: Long? = null,
  )

  sealed interface Result {
    data class Ok(val data: ShopData, val invoiceNo: Int) : Result
    data class Failed(val message: String) : Result
  }

  /** شمارهٔ بعدیِ فاکتورِ خرید */
  fun nextNo(d: ShopData): Int = (d.purchases.maxOfOrNull { it.invoiceNo } ?: 0) + 1

  fun lineTotal(l: Line): Double = Math.round(l.quantity * l.price).toDouble()

  /**
   *  PRD-07: ردیف به واحدِ خریدِ [name] ⇐ ورودیِ موتور، یا null (واحد روی کالا نیست، یا مقدارِ پایهٔ کالای شمارشی اعشاری می‌شود).
   *  [total] جمعِ ردیف به افغانی (همان [lineTotal]) — بی تغییر به صدمِ افغانی می‌رود.
   */
  fun inUnitOf(product: Product, name: String, quantity: Double, total: Double): LedgerEngine.InUnit? {
    val u = ir.vil3ntec.tohid.core.product.PurchaseUnits.find(product.purchaseUnits?.map(ShopStore::coreOf), name) ?: return null
    if (quantity.isNaN() || quantity <= 0 || total.isNaN() || total < 0) return null
    val q = StockLedger.milli(quantity)
    if (q <= 0) return null
    val base = runCatching { ir.vil3ntec.tohid.core.product.PurchaseUnits.baseQtyMilli(q, u.factorMilli) }.getOrNull() ?: return null
    if (base <= 0 || (!wholeOk(product) && base % 1000 != 0L)) return null
    return LedgerEngine.InUnit(u.name, q, u.factorMilli, StockLedger.minor(total))
  }

  /** همان قاعدهٔ اعشارِ بسته‌ها و واحدهای خرید: واحدِ وزنی یا واحدِ فروشِ دوم */
  private fun wholeOk(p: Product) = SalesEngine.isFractionalUnit(p.unit) || p.saleUnit.isNotBlank()

  /** چرا [inUnitOf] null داد — پیامِ ردیف (همان فاکتورِ خرید و سفارشِ خرید، `PoForms.draft`) */
  internal fun unitWhy(p: Product, name: String, quantity: Double): String =
    if (ir.vil3ntec.tohid.core.product.PurchaseUnits.find(p.purchaseUnits?.map(ShopStore::coreOf), name) == null) ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits.unknownUnit
    else if (quantity.isNaN() || quantity <= 0) StrStock.errAmount
    else ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits.wholeBase(p.unit)

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
    /**
     *  کلیدِ یکتای همین فاکتور (فرم یک بار می‌سازد، PUR-05): شناسهٔ هر ردیف و همهٔ اثرهایش از آن ساخته می‌شود
     *  (`کلید-ردیف-n`)، پس دو کلیک یا F9 ِ پشتِ‌سرِهم یک فاکتور است — با دفترِ کهنه همان شناسه‌ها (ادغام یکی‌شان
     *  می‌کند)، با دفترِ تازه رد ([ALREADY_RECORDED])؛ نه موجودیِ دوم، نه بدهیِ دوم. `null` = رفتارِ پیشین.
     */
    key: String? = null,
    /**
     *  PUR-11 — فاکتور به ارزِ دیگر با نرخِ ثبت‌شدهٔ همین سند (`PurchaseFx.rate`)؛ فیِ هر ردیف [Line.fxPrice] و پرداختِ هنگامِ
     *  خرید [fxPaid] (واحدِ کوچکِ همان ارز؛ [paid] ِ افغانی نادیده). معادلِ افغانیِ سند یک بار و سهمِ ردیف‌ها دقیق
     *  (`PurchaseFx.doc`) ⇒ همان `addPurchase` ِ هر ردیف با جمعِ دقیقِ افغانی (انبار، بهای تمام‌شده، بدهی). `null` = افغانی، همان پیشین.
     */
    fx: ir.vil3ntec.tohid.core.purchase.PurchaseFx.Rate? = null,
    fxPaid: Long = 0,
  ): Result {
    val k = key?.trim()?.ifEmpty { null }
    if (k != null && d.purchases.any { it.id.startsWith("$k-") }) return Result.Failed(ALREADY_RECORDED)
    if (d.suppliers.none { it.id == supplierId }) return Result.Failed(StrPurchasePages.chooseSupplier)
    if (landedTotal.isNaN() || landedTotal < 0) return Result.Failed(StrPurchasePages.landedBad)
    val due = dueDate?.trim()?.ifEmpty { null }
    if (due != null && due < date.ifBlank { today }) return Result.Failed(DUE_BEFORE_DATE)
    val real = lines.filter { it.productId.isNotBlank() }
    if (real.isEmpty()) return Result.Failed(StrGoods.oneRowNeeded)
    real.forEachIndexed { i, l ->
      if (l.quantity.isNaN() || l.quantity <= 0) return Result.Failed(StrGoods.rowQtyBad(i + 1))
      if (fx == null && (l.price.isNaN() || l.price < 0)) return Result.Failed(StrGoods.rowPriceBad(i + 1))
      if (fx != null && (l.fxPrice == null || l.fxPrice < 0)) return Result.Failed(StrGoods.rowPriceBad(i + 1))
    }
    //  PUR-11: سندِ ارزی ⇒ جمعِ ارزیِ ردیف‌ها، معادلِ افغانیِ یک‌بارهٔ سند و سهمِ دقیقِ هر ردیف (افغانی و پرداخت)
    val doc = fx?.let { r ->
      if (fxPaid < 0) return Result.Failed(ir.vil3ntec.tohid.core.i18n.StrLedger.amountBad)
      runCatching {
        ir.vil3ntec.tohid.core.purchase.PurchaseFx.doc(r, real.map { l -> ir.vil3ntec.tohid.core.purchase.PurchaseFx.lineFx(StockLedger.milli(l.quantity), l.fxPrice ?: 0L) }, fxPaid)
      }.getOrNull() ?: return Result.Failed(ir.vil3ntec.tohid.core.i18n.StrLedger.amountBad)
    }
    /** جمعِ افغانیِ ردیفِ [i]: سندِ ارزی ⇒ سهمِ دقیقِ همان (صدمِ افغانی)، وگرنه همان [lineTotal] */
    fun afnOf(i: Int): Double = doc?.let { it.afnLines[i] / 100.0 } ?: lineTotal(real[i])
    //  PRD-07: واحدِ خریدِ هر ردیف ⇐ ضریب (پیش از هر اثر؛ ردیفِ بی واحد همان امروز)
    val inUnits = real.mapIndexed { i, l ->
      val name = l.unitName?.trim()?.ifEmpty { null } ?: return@mapIndexed null
      val product = d.products.find { it.id == l.productId } ?: return Result.Failed(StrGoods.rowOfWhy(i + 1, ir.vil3ntec.tohid.core.i18n.StrLedger.pickProduct))
      inUnitOf(product, name, l.quantity, afnOf(i)) ?: return Result.Failed(StrGoods.rowOfWhy(i + 1, unitWhy(product, name, l.quantity)))
    }
    val total = real.indices.sumOf { afnOf(it) }
    var left = if (paid.isNaN() || paid < 0) 0.0 else minOf(paid, total)

    val no = nextNo(d)
    val tag = "فاکتورِ خرید #$no"  // داده: تکهٔ یادداشتِ سابقه
    var cur = d
    val ids = mutableListOf<String>()
    for ((i, l) in real.withIndex()) {
      val lt = afnOf(i)
      //  سندِ ارزی: پرداختِ ردیف همان سهمِ دقیقِ افغانیِ پرداختِ ارزی (همان نرخ، بی اختلاف)
      val pay = doc?.let { it.afnPaidLines[i] / 100.0 } ?: minOf(left, lt)
      left -= pay
      val lineIds = if (k == null) newId else keyedIds(k, i + 1)
      val r = LedgerEngine.addPurchase(
        cur,
        LedgerEngine.PurchaseDraft(
          supplierId = supplierId, productId = l.productId, quantity = l.quantity, unit = l.unit,
          purchasePrice = if (doc != null) 0.0 else l.price, paidAmount = pay, date = date,
          notes = listOf(tag, notes.trim()).filter { it.isNotBlank() }.joinToString(" — "),
          expiryDate = l.expiryDate,
          dueDate = dueDate,
          inUnit = inUnits[i],
          exactTotalMinor = doc?.afnLines?.get(i),
        ),
        today, now, lineIds,
      )
      when (r) {
        is LedgerEngine.Result.Failed -> return Result.Failed(StrGoods.rowOfWhy(i + 1, r.message))
        is LedgerEngine.Result.Ok -> { cur = r.data; ids += r.id }
      }
    }
    val set = ids.toSet()
    val fxById = if (doc == null || fx == null) emptyMap() else ids.withIndex().associate { (i, id) -> id to i }
    cur = cur.copy(
      purchases = cur.purchases.map { p ->
        if (p.id !in set) return@map p
        val i = fxById[p.id]
        if (i == null || doc == null || fx == null) p.copy(invoiceNo = no)
        else p.copy(
          invoiceNo = no, currency = fx.currency, fxRate = fx.rate, fxPer = fx.per, fxPriceMinor = real[i].fxPrice,
          fxTotalMinor = doc.fxLines[i], fxPaidMinor = doc.fxPaidLines[i],
        )
      },
    )
    val landed = StockLedger.spreadLanded(StockLedger.minor(landedTotal), real.indices.map { StockLedger.minor(afnOf(it)) })
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
    /** PUR-11 — ارزِ سند (`null` = افغانی) و نرخِ ثبت‌شده‌اش؛ جمع و پرداختِ هنگامِ خرید به همان ارز (واحدِ کوچک) */
    val currency: String? get() = SupplierFx.cur(lines.firstOrNull()?.currency)
    val fxRate: ir.vil3ntec.tohid.core.purchase.PurchaseFx.Rate? get() = lines.firstOrNull()?.let(SupplierFx::rateOf)
    val fxTotal: Long get() = lines.sumOf { it.fxTotalMinor ?: 0L }
    val fxPaid: Long get() = lines.sumOf { it.fxPaidMinor ?: 0L }
  }

  /**
   *  همهٔ فاکتورهای خرید، تازه‌ترین بالا. هر فاکتور = (شماره، تأمین‌کننده): دو دستگاهِ بی همگام‌سازی هر کدام [nextNo]
   *  را روی دفترِ خودش می‌گیرد، پس پس از همگام‌سازی یک شماره ممکن است مالِ دو تأمین‌کننده باشد — هرگز یکی نمی‌شوند
   *  (وگرنه یادآورِ سررسید بدهیِ یکی را به نامِ دیگری می‌گذاشت). کلید «#n» است و فقط وقتی همان شماره مالِ چند
   *  تأمین‌کننده است «#n·تأمین‌کننده» — از کلِ دفتر، پس کلیدِ فهرستِ یک تأمین‌کننده همان کلیدِ فهرستِ همه است.
   */
  fun invoices(d: ShopData, supplierId: String? = null): List<Invoice> {
    val shared = d.purchases.filter { it.invoiceNo > 0 }.groupBy { it.invoiceNo }
      .filterValues { ls -> ls.mapTo(HashSet()) { it.supplierId }.size > 1 }.keys
    val list = d.purchases.filter { supplierId == null || it.supplierId == supplierId }
    val grouped = list.filter { it.invoiceNo > 0 }.groupBy { it.invoiceNo to it.supplierId }.map { (k, ls) ->
      val (no, sid) = k
      Invoice(if (no in shared) "#$no·$sid" else "#$no", no, sid, ls.minOf { it.date }, ls.sortedBy { it.createdAt })
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

  /**
   *  فاکتورِ خرید با ماندهٔ **واقعی**اش (پس از پرداخت‌های بعدی و سندهای کسر). PUR-11: سندِ ارزی ماندهٔ ارزی‌اش را هم دارد
   *  ([fxRemaining]، واحدِ کوچکِ ارز)؛ [remaining] همان ارزشِ دفتریِ افغانی.
   */
  data class Due(val invoice: Invoice, val remaining: Double, val fxRemaining: Long? = null) {
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
    //  PUR-11: هر ارز حسابِ جدا — پرداختِ حسابِ دالری فقط فاکتورهای دالری را صاف می‌کند (افغانی همان گروهِ پیشین، بیت‌به‌بیت)
    invoices(d, supplierId).groupBy { it.supplierId to it.currency }.forEach { (key, list) ->
      val (sid, cur) = key
      val pays = d.supplierPayments.filter { it.supplierId == sid && SupplierFx.cur(it.currency) == cur }
      val afn = fifo(list, pays, { it.total }, { it.debt }, { it.amount })
      val fx = if (cur == null) null
      else fifo(list, pays, { it.fxTotal.toDouble() }, { (it.fxTotal - it.fxPaid).toDouble() }, { (it.fxAmountMinor ?: 0L).toDouble() })
        .associate { (inv, left) -> inv.key to Math.round(left) }
      afn.forEach { (inv, left) -> out += Due(inv, left, fx?.get(inv.key)) }
    }
    return out
  }

  /**
   *  ماندهٔ هر فاکتورِ یک حساب ([list] و پرداخت‌هایش [pays]) با [totalOf]/[debtOf]/[payOf] ِ یک واحد (افغانی، یا واحدِ کوچکِ ارز):
   *  e-rows-63b — پرداختی که فاکتورش را نام می‌برد ([payNote]، «پرداخت» ِ جزئیاتِ خرید) اول همان فاکتور را صاف می‌کند؛ بیشِ آن و
   *  بقیهٔ پرداخت‌ها، سندهای کسر و اضافه‌پرداختِ هر فاکتور FIFO روی قدیمی‌ترین‌ها. جمعِ مانده‌ها همان `max(0, بدهی)` است.
   */
  private fun fifo(
    list: List<Invoice>,
    pays: List<SupplierPayment>,
    totalOf: (Invoice) -> Double,
    debtOf: (Invoice) -> Double,
    payOf: (SupplierPayment) -> Double,
  ): List<Pair<Invoice, Double>> {
    val bills = list.filter { !it.isReturn }
    val left = HashMap<String, Double>(bills.size * 2)
    bills.forEach { left[it.key] = debtOf(it).coerceAtLeast(0.0) }
    val byNo = bills.filter { it.no > 0 }.associateBy { it.no }
    var credit = list.filter { it.isReturn }.sumOf { -totalOf(it) } + bills.sumOf { (-debtOf(it)).coerceAtLeast(0.0) }
    pays.forEach { p ->
      var amount = payOf(p)
      val target = if (amount > 0) paidInvoiceNo(p.notes)?.let(byNo::get) else null
      if (target != null) {
        val take = minOf(amount, left[target.key] ?: 0.0)
        left[target.key] = (left[target.key] ?: 0.0) - take
        amount -= take
      }
      credit += amount
    }
    return bills.sortedWith(compareBy<Invoice> { it.date }.thenBy { it.createdAt }.thenBy { it.key }).map { inv ->
      val debt = left[inv.key] ?: 0.0
      val take = minOf(debt, credit.coerceAtLeast(0.0))
      credit -= take
      inv to (debt - take)
    }
  }

  /**
   *  یادداشتِ پرداختی که یک فاکتورِ خرید را صاف می‌کند («فاکتورِ خرید #۱۲ — …»، همان تکهٔ یادداشتِ ردیف‌های همان فاکتور
   *  در [record]) — [open] آن را اول به همان فاکتور می‌دهد. بی تغییرِ سیم: یادداشت همان فیلدِ امروزِ `SupplierPayment` است.
   */
  fun payNote(no: Int, extra: String = ""): String =
    listOf("فاکتورِ خرید #$no", extra.trim()).filter { it.isNotBlank() }.joinToString(" — ")  // داده: تکهٔ یادداشتِ پرداخت (منطقِ [open])

  /** شمارهٔ فاکتوری که یادداشتِ یک پرداخت نام می‌برد ([payNote])؛ نبود ⇒ null */
  fun paidInvoiceNo(notes: String): Int? = PAY_TAG.find(notes)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

  private val PAY_TAG = Regex("^فاکتورِ خرید #(\\d+)")  // داده: همان تکهٔ [payNote]

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
    /**
     *  PRD-07 — برگشت به یکی از واحدهای خریدِ کالا: `quantity` به همان واحد (۱ کارتن) و `unitPrice` قیمتِ یک واحدِ همان؛
     *  از انبار مقدار × ضریب به واحدِ پایه بیرون می‌رود و سندِ کسر جمعِ دقیقِ همان ردیف است. `null` = واحدِ پایه.
     */
    unitName: String? = null,
    /**
     *  PUR-11 — برگشتِ کالای خریده به ارزِ دیگر: اعتبار به همان ارز ([fxPrice] فیِ هر واحد به واحدِ کوچکِ ارز؛ [unitPrice] ِ
     *  افغانی نادیده) با نرخِ [fx] (پیش‌فرضِ صفحه: نرخِ آخرین خریدِ همین کالا از همین تأمین‌کننده، `SupplierFx.returnRate`) —
     *  معادلِ افغانی [PurchaseFx.toAfn] ِ جمعِ ارزی، دقیق. `null` = افغانی، همان پیشین.
     */
    fx: ir.vil3ntec.tohid.core.purchase.PurchaseFx.Rate? = null,
    fxPrice: Long? = null,
    /** جمعِ دقیقِ سندِ کسر به صدمِ افغانی (فقط از راهِ ارزیِ همین تابع) */
    exactMinor: Long? = null,
  ): Result {
    val k = key?.trim()?.ifEmpty { null }
    if (k != null && d.purchases.any { it.id == k }) return Result.Failed(ALREADY_RETURNED)
    if (fx != null) {
      if (quantity.isNaN() || quantity <= 0) return Result.Failed(StrStock.errAmount)
      val price = fxPrice?.takeIf { it >= 0 } ?: return Result.Failed(StrGoods.returnPriceBad2)
      val fxAmount = runCatching { ir.vil3ntec.tohid.core.purchase.PurchaseFx.lineFx(StockLedger.milli(quantity), price) }.getOrNull()
        ?: return Result.Failed(StrGoods.returnPriceBad2)
      val afn = runCatching { ir.vil3ntec.tohid.core.purchase.PurchaseFx.toAfn(fxAmount, fx) }.getOrNull() ?: return Result.Failed(StrGoods.returnPriceBad2)
      val before = d.purchases.mapTo(HashSet()) { it.id }
      val r = supplierReturn(d, supplierId, productId, quantity, afn / 100.0 / quantity, reason, date, today, now, newId, userId, key, unitName, exactMinor = afn)
      if (r !is Result.Ok) return r
      return Result.Ok(
        r.data.copy(
          purchases = r.data.purchases.map { p ->
            if (p.id in before) p
            else p.copy(currency = fx.currency, fxRate = fx.rate, fxPer = fx.per, fxPriceMinor = price, fxTotalMinor = -fxAmount)
          },
        ),
        0,
      )
    }
    val supplier = d.suppliers.find { it.id == supplierId } ?: return Result.Failed(StrPurchasePages.chooseSupplier)
    val product = d.products.find { it.id == productId } ?: return Result.Failed(StrGoods.pickItem)
    if (quantity.isNaN() || quantity <= 0) return Result.Failed(StrStock.errAmount)
    if (unitPrice.isNaN() || unitPrice < 0) return Result.Failed(StrGoods.returnPriceBad2)
    val inUnit = unitName?.trim()?.ifEmpty { null }?.let { n ->
      inUnitOf(product, n, quantity, exactMinor?.let { it / 100.0 } ?: Math.round(quantity * unitPrice).toDouble()) ?: return Result.Failed(unitWhy(product, n, quantity))
    }
    if (inUnit != null) {
      val bc = ir.vil3ntec.tohid.core.product.PurchaseUnits.baseCost(inUnit.qtyMilli, inUnit.factorMilli, inUnit.totalMinor)
      return supplierReturn(
        d, supplierId, productId, bc.baseQtyMilli / 1000.0, bc.unitCostMinor / 100.0, reason, date, today, now, newId, userId, key,
      ).let { r -> if (r is Result.Ok) Result.Ok(inUnitReturn(r.data, k, d, inUnit), 0) else r }
    }
    val why = reason.trim()
    if (why.isEmpty()) return Result.Failed(StrGoods.returnReasonNeeded)
    val stock = ShopStore.stock(d, productId)
    if (quantity > stock) return Result.Failed(StrGoods.onlyInStock(ir.vil3ntec.tohid.qty(kotlin.math.max(0.0, stock)), product.unit))
    val cap = returnable(d, supplierId, productId)
    if (quantity > cap) return Result.Failed(StrGoods.returnCap(supplier.name, ir.vil3ntec.tohid.qty(cap), product.unit))
    val day = date.ifBlank { today }
    if (!ir.vil3ntec.tohid.core.stock.StockCore.isoDateOk(day)) return Result.Failed(StrGoods.dateBad)
    val amount = exactMinor?.let { it / 100.0 } ?: Math.round(quantity * unitPrice).toDouble()
    val purchaseId = k ?: newId()
    val entryId = if (k != null) "$k-w" else newId()
    val user = userId?.trim()?.ifEmpty { null }
    val text = "برگشت به «${supplier.name}» — $why"  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
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
          purchasePrice = unitPrice, totalAmount = -amount, date = day, notes = "سندِ کسر — برگشت به تأمین‌کننده: $why",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          paidAmount = 0.0, debt = -amount, warehouseEntryId = entryId, createdAt = now,
        ),
        auditLog = d.auditLog + AuditEntry(
          id = if (k != null) "$k-a" else newId(), type = "supplier_return", date = day, refId = entryId, createdAt = now,
          notes = "برگشت ${ir.vil3ntec.tohid.qty(quantity)} ${product.unit} «${product.name}» به «${supplier.name}» — سندِ کسرِ ${ir.vil3ntec.tohid.money(amount)} افغانی — دلیل: $why",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        ),
      ),
      0,
    )
  }

  /** سندِ کسرِ تازهٔ [supplierReturn] به واحدِ خرید: جمعِ دقیق (نه مقدارِ پایه × بهای گردشده) و نام، مقدار و ضریبِ همان واحد */
  private fun inUnitReturn(next: ShopData, key: String?, before: ShopData, u: LedgerEngine.InUnit): ShopData {
    val old = before.purchases.map { it.id }.toSet()
    val amount = u.totalMinor / 100.0
    return next.copy(
      purchases = next.purchases.map { p ->
        if (p.id in old || (key != null && p.id != key)) p
        else p.copy(totalAmount = -amount, debt = -amount, unitName = u.name, unitQtyMilli = -u.qtyMilli, unitFactorMilli = u.factorMilli)
      },
    )
  }

  val ALREADY_RETURNED: String get() = StrGoods.alreadyReturned

  /**
   *  شناسه‌های ردیفِ [line] ِ فاکتوری با کلیدِ [key] (`کلید-ردیف-n`، به ترتیبِ ساختنِ `LedgerEngine.addPurchase`) — همان
   *  کلید همیشه همان شناسه‌ها؛ فرمِ تک‌ردیفهٔ گوشی هم با همین به `addPurchase` می‌دهد تا دو لمس یک خرید باشد.
   */
  fun keyedIds(key: String, line: Int = 1): () -> String {
    var n = 0
    return { "$key-$line-${n++}" }
  }

  /**
   *  PUR-05: ورودِ انبارِ یک ردیفِ خرید (فاکتورِ خرید، رسیدِ سفارشِ خرید یا سندِ کسرِ برگشت) — موجودی و بدهیِ تأمین‌کننده
   *  از همان یک سند آمده‌اند، پس `WarehouseEngine` آن را جدا حذف نمی‌کند (بی [productId]/[units]) و کالا یا مقدارش را عوض
   *  نمی‌کند (وب و برنامهٔ پیشین می‌کردند و بدهی بی کالا می‌ماند). اصلاح: «برگشت به تأمین‌کننده» که هر دو را با هم کم می‌کند.
   */
  fun fixedEntry(d: ShopData, entry: WarehouseEntry, productId: String? = null, units: Double? = null): Boolean =
    entry.purchaseId?.let { pid -> d.purchases.any { it.id == pid } } == true &&
      (productId == null || productId != entry.productId || units != entry.units)

  val PURCHASE_ENTRY: String get() = StrGoods.purchaseEntryFixed

  /** همان کلیدِ فاکتورِ خرید دوباره (دو کلیک، PUR-05) — فاکتور یک بار ثبت شده است */
  val ALREADY_RECORDED: String get() = StrGoods.purchaseRecorded

  /** سررسید پیش از تاریخِ فاکتور معنی ندارد (PUR-07) */
  val DUE_BEFORE_DATE: String get() = StrGoods.dueBeforePurchase

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
        evs += Ev(inv.date, inv.createdAt, 0, StrSheets.supplierReturnNote, 0.0, -inv.total, inv.key)
        return@forEach
      }
      val title = (if (inv.no > 0) StrSheets.purchaseInvoiceTitle(inv.no, inv.lines.size) else StrSheets.purchase) + fxNote(inv)
      evs += Ev(inv.date, inv.createdAt, 0, title, inv.total, 0.0, inv.key)
      if (inv.paid > 0) evs += Ev(inv.date, inv.createdAt, 1, StrSheets.paidAtPurchase, 0.0, inv.paid, inv.key)
    }
    d.supplierPayments.filter { it.supplierId == supplierId }.forEach { p ->
      evs += Ev(p.date, p.createdAt, 2, p.notes.ifBlank { StrSheets.payment } + fxNote(p), 0.0, p.amount, null)
    }
    var run = 0.0
    return evs.sortedWith(compareBy<Ev> { it.date }.thenBy { it.at }.thenBy { it.order })
      .mapIndexed { i, e ->
        run += e.bill - e.pay
        LedgerLine(i + 1, e.date, e.text, e.bill, e.pay, run, e.key)
      }
  }

  /** PUR-11: «— 100.00 USD · نرخ 1 USD = 70 AFN» ِ فاکتورِ ارزی؛ افغانی ⇒ "" */
  private fun fxNote(inv: Invoice): String {
    val code = inv.currency ?: return ""
    return " — " + ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.docLine(
      SafeCurrencies.format(kotlin.math.abs(inv.fxTotal), code), inv.fxRate?.let(ir.vil3ntec.tohid.core.purchase.PurchaseFx::rateLine).orEmpty(),
    )
  }

  /** PUR-11: پرداختِ حسابِ ارزی ⇒ مبلغ به همان ارز، نرخِ همین پرداخت و اختلافِ نرخ؛ پیشین ⇒ "" */
  private fun fxNote(p: SupplierPayment): String {
    val code = SupplierFx.cur(p.currency) ?: return ""
    val diff = p.fxDiffMinor?.takeIf { it != 0L }?.let { x ->
      val a = ir.vil3ntec.tohid.money(kotlin.math.abs(x) / 100.0)
      " · " + if (x > 0) ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.fxLoss(a) else ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.fxGain(a)
    }.orEmpty()
    return " — " + ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.docLine(
      SafeCurrencies.format(p.fxAmountMinor ?: 0L, code), SupplierFx.rateOf(p)?.let(ir.vil3ntec.tohid.core.purchase.PurchaseFx::rateLine).orEmpty(),
    ) + diff
  }

  /* ── برگه‌های اکسل و چاپ ─────────────────────────────────────────── */

  private fun r(v: Double) = Math.round(v).toString()
  private fun q(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
  private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "تأمین‌کننده" }  // داده: نامِ پروندهٔ خروجی

  /** عنوانِ برگهٔ چاپیِ برگشت به تأمین‌کننده (S25، PUR-08) */
  const val CREDIT_NOTE_TITLE = "سندِ کسر"  // داده: نامِ پروندهٔ خروجی

  /** [remaining] = ماندهٔ واقعی ([open]، چاپِ جزئیاتِ خرید ۶۳b)؛ null ⇒ همان ماندهٔ هنگامِ خرید، مثلِ پیشین */
  fun invoiceSheet(d: ShopData, inv: Invoice, remaining: Double? = null): ReportCsv.Sheet {
    val names = d.products.associate { it.id to it.name }
    val supplier = d.suppliers.find { it.id == inv.supplierId }?.name.orEmpty()
    val rows = mutableListOf(listOf(StrSheets.row, StrSheets.product, StrSheets.quantity, StrSheets.unit, StrSheets.unitPrice, StrSheets.total))
    //  S25: سندِ کسر برگهٔ خودش را دارد — عنوانِ «سندِ کسر»، مقدار و مبلغِ برگشتی مثبت، دلیل و کسر از بدهی (نه پرداخت/مانده)
    if (inv.isReturn) {
      inv.lines.forEachIndexed { i, p ->
        rows += listOf((i + 1).toString(), names[p.productId].orEmpty(), q(-p.quantity), p.unit, r(p.purchasePrice), r(-p.totalAmount))
      }
      rows += listOf("", StrSheets.deductedFrom(supplier), "", "", "", r(-inv.total))
      inv.lines.map { it.notes.substringAfter("برگشت به تأمین‌کننده: ", "").trim() }.filter { it.isNotEmpty() }.distinct()  // داده: پیشوندِ یادداشتِ ذخیره‌شدهٔ ردیفِ برگشت (منطق، نه متنِ برگه)
        .forEach { rows += listOf("", StrSheets.reasonLine(it), "", "", "", "") }
      return ReportCsv.Sheet("$CREDIT_NOTE_TITLE-${safe(supplier)}-${inv.date}", rows)
    }
    inv.lines.forEachIndexed { i, p ->
      rows += listOf((i + 1).toString(), names[p.productId].orEmpty(), q(p.quantity), p.unit, r(p.purchasePrice), r(p.totalAmount))
    }
    //  PUR-11: فاکتورِ ارزی ⇒ جمع به همان ارز و نرخِ ثبت‌شده (ردیف‌ها و جمع‌های افغانی همان معادل‌اند)
    inv.currency?.let { code ->
      rows += listOf("", ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.totalIn(code), "", "", "", SafeCurrencies.number(inv.fxTotal, code))
      inv.fxRate?.let { rows += listOf("", ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.rateLine(ir.vil3ntec.tohid.core.purchase.PurchaseFx.rateLine(it)), "", "", "", "") }
    }
    rows += listOf("", StrSheets.total, "", "", "", r(inv.total))
    val left = remaining?.coerceAtLeast(0.0) ?: inv.debt
    rows += listOf("", StrSheets.paidDone, "", "", "", r(if (remaining == null) inv.paid else inv.total - left))
    rows += listOf("", StrSheets.balance, "", "", "", r(left))
    val title = if (inv.no > 0) "فاکتور-خرید-${inv.no}" else "خرید"  // داده: نامِ پروندهٔ خروجی
    return ReportCsv.Sheet("$title-${safe(supplier)}-${inv.date}", rows)
  }

  fun invoicesSheet(d: ShopData, list: List<Invoice>, today: String): ReportCsv.Sheet {
    val names = d.suppliers.associate { it.id to it.name }
    //  PUR-11: ستون‌های ارز فقط وقتی فهرست فاکتورِ ارزی دارد
    val fx = list.any { it.currency != null }
    val head = listOf(StrSheets.invoice, StrSheets.date, StrSheets.supplier, StrSheets.rows, StrSheets.total, StrSheets.paidDone, StrSheets.balance)
    val rows = mutableListOf(if (fx) head + listOf(ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.currencyCol, ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.fxAmountCol) else head)
    list.forEach { v ->
      val base = listOf(if (v.no > 0) v.no.toString() else "", v.date, names[v.supplierId].orEmpty(), v.lines.size.toString(), r(v.total), r(v.paid), r(v.debt))
      val code = v.currency
      rows += if (!fx) base else base + if (code == null) listOf("AFN", "") else listOf(code, SafeCurrencies.number(v.fxTotal, code))
    }
    return ReportCsv.Sheet("فاکتورهای-خرید-$today", rows)  // داده: نامِ پروندهٔ خروجی
  }

  fun ledgerSheet(d: ShopData, supplier: Supplier, today: String): ReportCsv.Sheet {
    val rows = mutableListOf(listOf(StrSheets.row, StrSheets.date, StrSheets.description, StrSheets.purchase, StrSheets.payment, StrSheets.balance))
    ledger(d, supplier.id).forEach { l ->
      rows += listOf(l.row.toString(), l.date, l.text, if (l.bill > 0) r(l.bill) else "", if (l.pay > 0) r(l.pay) else "", r(l.balance))
    }
    return ReportCsv.Sheet("حساب-${safe(supplier.name)}-$today", rows)  // داده: نامِ پروندهٔ خروجی
  }
}
