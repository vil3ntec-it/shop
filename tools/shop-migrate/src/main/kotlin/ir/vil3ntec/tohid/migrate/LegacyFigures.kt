package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData

/**
 *  «محاسبهٔ قدیم» — همان فرمول‌های برنامهٔ امروز، با همان ترتیبِ جمع.
 *
 *  موتورهای برنامه (`ShopStore`، `ReportEngine`) به `android.content.Context`
 *  و همگام‌سازی گره خورده‌اند و روی JVMِ ساده ساخته نمی‌شوند؛ پس این‌جا
 *  کپیِ خط‌به‌خطِ همان چند تابع است:
 *
 *   stock         ⇐ ShopStore.StockIndex (entered − sold، فروشِ لغوشده بیرون)
 *   debt          ⇐ ShopStore.debt
 *   supplierDebt  ⇐ ShopStore.supplierDebt
 *   sales         ⇐ ReportEngine.sales (+ ShopStore.SalesIndex)
 *   jalaliMonth   ⇐ ReportEngine.jalaliMonth
 *
 *  ⚠️ نگهبانِ این کپی: `MigrationGoldenTest` در آزمون‌های `android-native`
 *  همین عددها را با **موتورِ واقعی** روی همان فایلِ نمونه حساب می‌کند و با
 *  `legacy-golden.txt` می‌سنجد؛ `LegacyGoldenTest`ِ این‌جا همین کپی را با همان
 *  فایل. هر تغییری در آن موتورها یکی از این دو را قرمز می‌کند.
 */
object LegacyFigures {

  const val ALL_FROM = "0000-01-01"
  const val ALL_TO = "9999-12-31"

  /* ------------------------- ShopStore.StockIndex ------------------------- */

  class StockIndex(d: ShopData) {
    private val entered = HashMap<String, Double>()
    private val sold = HashMap<String, Double>()

    init {
      d.warehouseEntries.forEach { e ->
        entered[e.productId] = (entered[e.productId] ?: 0.0) + e.units
      }
      val cancelled = HashSet<String>()
      d.sales.forEach { if (it.status == "cancelled") cancelled += it.id }
      d.saleItems.forEach { item ->
        if (item.saleId in cancelled) return@forEach
        sold[item.productId] = (sold[item.productId] ?: 0.0) + (item.quantity - item.returnedQty)
      }
    }

    fun stock(productId: String): Double = (entered[productId] ?: 0.0) - (sold[productId] ?: 0.0)
  }

  /* ---------------------------- ShopStore.debt ---------------------------- */

  fun debt(d: ShopData, debtorId: String): Double =
    d.transactions.filter { it.debtorId == debtorId }
      .sumOf { if (it.type == "give") it.amount else -it.amount }

  fun supplierDebt(d: ShopData, supplierId: String): Double {
    val mine = d.purchases.filter { it.supplierId == supplierId }
    val billed = mine.sumOf { it.totalAmount }
    val paid = mine.sumOf { it.paidAmount } +
      d.supplierPayments.filter { it.supplierId == supplierId }.sumOf { it.amount }
    return billed - paid
  }

  /* --------------------------- ReportEngine.sales --------------------------- */

  data class SalesReport(
    val count: Int,
    val gross: Double,
    val discount: Double,
    val net: Double,
    val cogs: Double,
    val grossProfit: Double,
    val expenses: Double,
    val netProfit: Double,
    val returnAmount: Double,
  ) {
    fun fields(): List<Pair<String, Any>> = listOf(
      "count" to count, "gross" to gross, "discount" to discount, "net" to net, "cogs" to cogs,
      "grossProfit" to grossProfit, "expenses" to expenses, "netProfit" to netProfit,
      "returnAmount" to returnAmount,
    )
  }

  class SalesIndex(d: ShopData) {
    val saleById: Map<String, Sale> = d.sales.associateBy { it.id }
    val itemsBySale: Map<String, List<SaleItem>> = d.saleItems.groupBy { it.saleId }
    val itemById: Map<String, SaleItem> = d.saleItems.associateBy { it.id }
  }

  fun sales(d: ShopData, from: String, to: String, index: SalesIndex = SalesIndex(d)): SalesReport {
    val active = d.sales.filter { it.date in from..to && it.status != "cancelled" }
    val gross = active.sumOf { it.total }
    val discount = active.sumOf { it.discount }
    val net = active.sumOf { it.finalTotal }

    var cogs = 0.0
    active.forEach { sale ->
      index.itemsBySale[sale.id]?.forEach { item ->
        cogs += item.quantity * item.purchasePrice
      }
    }

    var returnAmount = 0.0
    var returnProfitImpact = 0.0
    d.saleReturns.filter { it.date in from..to }.forEach { r ->
      val sale = index.saleById[r.saleId]
      if (sale != null && sale.status == "cancelled") return@forEach
      returnAmount += r.amount
      val item = index.itemById[r.saleItemId]
      val cost = if (item != null) item.purchasePrice * r.quantity else 0.0
      returnProfitImpact += r.amount - cost
    }

    val grossProfit = net - cogs - returnProfitImpact
    val expenses = d.expenses.filter { it.date in from..to }.sumOf { it.amount }
    return SalesReport(active.size, gross, discount, net, cogs, grossProfit, expenses, grossProfit - expenses, returnAmount)
  }

  fun jalaliMonth(jy: Int, jm: Int): Pair<String, String> {
    val last = (31 downTo 29).first { Jalali.isValid(jy, jm, it) }
    val from = Jalali.toIso(jy, jm, 1) ?: "0000-01-01"
    val to = Jalali.toIso(jy, jm, last) ?: "9999-12-31"
    return from to to
  }

  /** ماه‌های خورشیدی‌ای که فروش، مرجوعی یا مصرف دارند — به ترتیب */
  fun months(d: ShopData): List<Pair<Int, Int>> =
    (d.sales.map { it.date } + d.saleReturns.map { it.date } + d.expenses.map { it.date })
      .mapNotNull { Jalali.ofIso(it) }
      .map { it.year to it.month }
      .distinct()
      .sortedWith(compareBy({ it.first }, { it.second }))

  fun monthKey(jy: Int, jm: Int): String = "%04d-%02d".format(jy, jm)

  /* ------------------------------ عددهای گزارش ------------------------------ */

  /** «محاسبهٔ قدیم» برای همهٔ کلیدهای گزارشِ تطبیق (کلیدها در `Keys`) */
  fun of(d: ShopData): LinkedHashMap<String, Double> {
    val out = LinkedHashMap<String, Double>()
    rows(d).forEach { (k, v) -> out[k] = v.toDouble() }

    val index = SalesIndex(d)
    val all = sales(d, ALL_FROM, ALL_TO, index)
    out["sales/count"] = all.count.toDouble()
    out["sales/gross"] = all.gross
    out["sales/discount"] = all.discount
    out["sales/net"] = all.net
    out["sales/cogs"] = all.cogs
    out["sales/returnAmount"] = all.returnAmount
    out["sales/expenses"] = all.expenses
    out["sales/grossProfit"] = all.grossProfit
    out["sales/netProfit"] = all.netProfit

    val active = d.sales.filter { it.status != "cancelled" }
    out["pay/salesCash"] = active.sumOf { it.paidAmount }
    out["pay/salesOnAccount"] = active.sumOf { it.debtGiven }
    out["pay/customerGive"] = d.transactions.filter { it.type == "give" }.sumOf { it.amount }
    out["pay/customerReceive"] = d.transactions.filter { it.type != "give" }.sumOf { it.amount }
    out["pay/supplierPaid"] = d.purchases.sumOf { it.paidAmount } + d.supplierPayments.sumOf { it.amount }

    d.debtors.map { it.id }.distinct().forEach { out["customer/$it"] = debt(d, it) }
    d.suppliers.map { it.id }.distinct().forEach { out["supplier/$it"] = supplierDebt(d, it) }
    val stock = StockIndex(d)
    d.products.map { it.id }.distinct().forEach { out["stock/$it"] = stock.stock(it) }

    months(d).forEach { (jy, jm) ->
      val (from, to) = jalaliMonth(jy, jm)
      val r = sales(d, from, to, index)
      val key = monthKey(jy, jm)
      out["month/$key/sales"] = r.net
      out["month/$key/grossProfit"] = r.grossProfit
      out["month/$key/netProfit"] = r.netProfit
    }
    return out
  }

  /** تعدادِ ردیف‌های هر فهرستِ قدیم، با همان نامِ کلیدِ جدولِ تازه */
  fun rows(d: ShopData): LinkedHashMap<String, Int> = linkedMapOf(
    "rows/product" to d.products.size,
    "rows/customer" to d.debtors.size,
    "rows/supplier" to d.suppliers.size,
    "rows/sale" to d.sales.size,
    "rows/saleLine" to d.saleItems.size,
    "rows/saleReturn" to d.saleReturns.size,
    "rows/customerLedgerEntry" to d.transactions.size,
    "rows/expense" to d.expenses.size,
    "rows/supplierInvoice" to d.purchases.size,
    "rows/supplierPayment" to d.supplierPayments.size,
    "rows/stockReceipt" to d.warehouseEntries.size,
    "rows/auditLog" to d.auditLog.size,
    "rows/cashShift" to d.auditLog.count { it.type == "shift_open" },
    "rows/barcode" to d.products.distinctBy { it.id }
      .sumOf { p -> p.barcodes.filter { it.isNotBlank() }.distinct().size },
    "rows/priceChange" to d.priceHistory.size,
  )

  /**
   *  فقط عددهایی که موتورِ واقعی هم می‌سازد — برای `legacy-golden.txt`.
   *  قالب: `کلید=Double.toString()`، به ترتیبِ کلید. `MigrationGoldenTest`ِ
   *  android-native همین قالب را با موتورِ واقعی می‌سازد.
   */
  fun golden(d: ShopData): List<String> {
    val out = ArrayList<String>()
    val stock = StockIndex(d)
    d.products.map { it.id }.distinct().forEach { out += "stock/$it=${stock.stock(it)}" }
    d.debtors.map { it.id }.distinct().forEach { out += "debt/$it=${debt(d, it)}" }
    d.suppliers.map { it.id }.distinct().forEach { out += "supplierDebt/$it=${supplierDebt(d, it)}" }
    sales(d, ALL_FROM, ALL_TO).fields().forEach { (k, v) -> out += "report/all/$k=$v" }
    months(d).forEach { (jy, jm) ->
      val (from, to) = jalaliMonth(jy, jm)
      sales(d, from, to).fields().forEach { (k, v) -> out += "report/${monthKey(jy, jm)}/$k=$v" }
    }
    return out.sorted()
  }

  /**
   *  همان دفتر، با هر عددِ پولی گردشده به یک‌صدم و هر مقدار به هزارم — یعنی
   *  دقیقاً همان ورودی‌ای که مدلِ تازه نگه می‌دارد. «محاسبهٔ قدیم روی این
   *  ورودی» نشان می‌دهد کدام اختلاف فقط از اعشارِ اضافیِ Double است.
   */
  fun roundInputs(d: ShopData): ShopData {
    fun m(x: Double) = Scale.r2(x)
    fun q(x: Double) = Scale.r3(x)
    return d.copy(
      products = d.products.map {
        it.copy(purchasePrice = m(it.purchasePrice), salePrice = m(it.salePrice),
          wholesalePrice = m(it.wholesalePrice), minStock = q(it.minStock))
      },
      warehouseEntries = d.warehouseEntries.map {
        it.copy(units = q(it.units), cartons = q(it.cartons), perCarton = q(it.perCarton), price = m(it.price))
      },
      sales = d.sales.map {
        it.copy(total = m(it.total), discount = m(it.discount), finalTotal = m(it.finalTotal),
          paidAmount = m(it.paidAmount), remaining = m(it.remaining), debtGiven = m(it.debtGiven),
          debtSettled = m(it.debtSettled))
      },
      saleItems = d.saleItems.map {
        it.copy(quantity = q(it.quantity), unitPrice = m(it.unitPrice), purchasePrice = m(it.purchasePrice),
          totalPrice = m(it.totalPrice), returnedQty = q(it.returnedQty))
      },
      saleReturns = d.saleReturns.map { it.copy(quantity = q(it.quantity), amount = m(it.amount)) },
      transactions = d.transactions.map { it.copy(amount = m(it.amount)) },
      expenses = d.expenses.map { it.copy(amount = m(it.amount)) },
      purchases = d.purchases.map {
        it.copy(quantity = q(it.quantity), purchasePrice = m(it.purchasePrice), totalAmount = m(it.totalAmount),
          paidAmount = m(it.paidAmount), debt = m(it.debt))
      },
      supplierPayments = d.supplierPayments.map { it.copy(amount = m(it.amount)) },
      stockMovements = d.stockMovements.map { it.copy(qty = q(it.qty)) },
      priceHistory = d.priceHistory.map { it.copy(oldPrice = m(it.oldPrice), newPrice = m(it.newPrice)) },
      auditLog = d.auditLog.map { it.copy(amount = m(it.amount)) },
    )
  }
}
