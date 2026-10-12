package ir.vil3ntec.tohid.data

import kotlin.math.max

/**
 *  عددها و هشدارهای داشبوردِ کامپیوتر — همه از خودِ دفتر، هیچ چیزِ ذخیره‌شده.
 *
 *  خواستهٔ صاحب مخزن: «هشدارها را نشان بدهد، کم‌بودی‌ها، تاریخ‌هایی که
 *  می‌خواهم بروند (انقضا)، کدام کالا فروش داشته یا نداشته، قرض‌دارهایی که
 *  زیاد شده‌اند…». بازه (`days`) را کاربر انتخاب می‌کند و هر عدد با بازهٔ
 *  پیش از آن مقایسه می‌شود.
 *
 *  فاکتورِ لغوشده هیچ‌جا حساب نمی‌شود؛ برگشتی‌ها از فروش و سود کم می‌شوند.
 *  تاریخ‌ها همان `yyyy-MM-dd`ِ دفترند و فاصله با `ExpiryEngine.epochDay`.
 */
object DashboardEngine {

  data class Kpi(val value: Double, val previous: Double) {
    /** درصدِ تغییر نسبت به بازهٔ قبل؛ null وقتی بازهٔ قبل صفر بوده */
    val changePct: Double? get() = if (previous <= 0.0) null else (value - previous) / previous * 100.0
  }

  data class Day(val iso: String, val sales: Double, val profit: Double)
  data class ProductLine(val product: Product, val quantity: Double, val revenue: Double)
  data class StockAlert(val product: Product, val stock: Double)
  data class DebtLine(val debtor: Debtor, val balance: Double, val change: Double, val days: Long?)
  data class Share(val name: String, val revenue: Double)

  data class Board(
    val days: Int,
    val sales: Kpi,
    val profit: Kpi,
    val invoices: Kpi,
    val avgTicket: Kpi,
    val todaySales: Double,
    val receivables: Double,
    val stockValue: Double,
    val series: List<Day>,
    val top: List<ProductLine>,
    /** کالای موجودِ فعال که در این بازه هیچ فروشی نداشته — بیشترین موجودی اول */
    val unsold: List<StockAlert>,
    val outOfStock: List<StockAlert>,
    val lowStock: List<StockAlert>,
    val expiring: List<ExpiryEngine.Lot>,
    /** بدهی‌شان در این بازه بالا رفته — بیشترین افزایش اول */
    val debtRising: List<DebtLine>,
    /** بیش از ۶۰ روز است که قدیمی‌ترین قرضشان پس داده نشده */
    val debtOverdue: List<DebtLine>,
    val categories: List<Share>,
    val recent: List<Sale>,
    val productCount: Int,
    val activeCount: Int,
  ) {
    val alertCount: Int get() = outOfStock.size + lowStock.size + expiring.size + debtOverdue.size
  }

  private fun day(iso: String): Long? = runCatching { ExpiryEngine.epochDay(iso) }.getOrNull()

  fun board(d: ShopData, today: String, days: Int, now: Long = System.currentTimeMillis()): Board {
    val t = day(today) ?: 0L
    val from = t - days + 1
    val prevFrom = from - days
    val live = d.sales.filter { it.status != "cancelled" }
    val saleDay = live.associate { it.id to day(it.date) }
    val itemsBySale = d.saleItems.groupBy { it.saleId }
    val returnedBySale = d.saleReturns.groupBy { it.saleId }

    fun inRange(s: Sale, a: Long, b: Long) = saleDay[s.id]?.let { it in a..b } == true
    fun net(s: Sale) = s.finalTotal - (returnedBySale[s.id]?.sumOf { it.amount } ?: 0.0)
    fun profit(s: Sale): Double {
      val cost = itemsBySale[s.id].orEmpty().sumOf { it.purchasePrice * max(0.0, it.quantity - it.returnedQty) }
      return net(s) - cost
    }

    val cur = live.filter { inRange(it, from, t) }
    val prev = live.filter { inRange(it, prevFrom, from - 1) }
    val salesNow = cur.sumOf(::net)
    val salesPrev = prev.sumOf(::net)

    val series = (0 until days).map { i ->
      val dn = from + i
      val list = cur.filter { saleDay[it.id] == dn }
      Day(isoOf(dn), list.sumOf(::net), list.sumOf(::profit))
    }

    //  فروشِ هر کالا در بازه
    val curIds = cur.map { it.id }.toSet()
    val sold = d.saleItems.filter { it.saleId in curIds && it.productId.isNotBlank() }
      .groupBy { it.productId }
      .mapValues { (_, xs) -> xs.sumOf { max(0.0, it.quantity - it.returnedQty) } to xs.sumOf { it.totalPrice } }
    val byId = d.products.associateBy { it.id }
    val top = sold.mapNotNull { (id, qr) -> byId[id]?.let { ProductLine(it, qr.first, qr.second) } }
      .sortedByDescending { it.revenue }.take(8)

    val index = ShopStore.index(d)
    val stockOf = d.products.associate { it.id to index.stock(it.id) }
    val active = d.products.filter { it.active }
    val out = active.filter { (stockOf[it.id] ?: 0.0) <= 0.0 }.map { StockAlert(it, stockOf[it.id] ?: 0.0) }
    val low = active.filter { val s = stockOf[it.id] ?: 0.0; s > 0.0 && it.minStock > 0 && s <= it.minStock }
      .map { StockAlert(it, stockOf[it.id] ?: 0.0) }.sortedBy { it.stock / max(1.0, it.product.minStock) }
    val unsold = active.filter { (stockOf[it.id] ?: 0.0) > 0.0 && it.id !in sold.keys }
      .map { StockAlert(it, stockOf[it.id] ?: 0.0) }
      .sortedByDescending { it.stock * it.product.purchasePrice }

    val expiring = runCatching { ExpiryEngine.alerts(d, today) }.getOrDefault(emptyList())

    //  قرض‌داران — تغییرِ بدهی در همین بازه
    val txByDebtor = d.transactions.groupBy { it.debtorId }
    val debtLines = d.debtors.map { db ->
      val txs = txByDebtor[db.id].orEmpty()
      val balance = txs.sumOf { if (it.type == "give") it.amount else -it.amount }
      val change = txs.filter { day(it.date)?.let { x -> x in from..t } == true }
        .sumOf { if (it.type == "give") it.amount else -it.amount }
      DebtLine(db, balance, change, DebtorEngine.debtDays(d, db.id, now))
    }
    val receivables = debtLines.sumOf { it.balance.coerceAtLeast(0.0) }

    val catRevenue = d.saleItems.filter { it.saleId in curIds }
      .groupBy { byId[it.productId]?.category?.ifBlank { null } ?: "بی‌دسته" }
      .map { (k, xs) -> Share(k, xs.sumOf { it.totalPrice }) }
      .sortedByDescending { it.revenue }

    val stockValue = d.products.sumOf { (stockOf[it.id] ?: 0.0).coerceAtLeast(0.0) * it.purchasePrice }

    return Board(
      days = days,
      sales = Kpi(salesNow, salesPrev),
      profit = Kpi(cur.sumOf(::profit), prev.sumOf(::profit)),
      invoices = Kpi(cur.size.toDouble(), prev.size.toDouble()),
      avgTicket = Kpi(if (cur.isEmpty()) 0.0 else salesNow / cur.size, if (prev.isEmpty()) 0.0 else salesPrev / prev.size),
      todaySales = live.filter { saleDay[it.id] == t }.sumOf(::net),
      receivables = receivables,
      stockValue = stockValue,
      series = series,
      top = top,
      unsold = unsold,
      outOfStock = out,
      lowStock = low,
      expiring = expiring,
      debtRising = debtLines.filter { it.change > 0.5 }.sortedByDescending { it.change }.take(8),
      debtOverdue = debtLines.filter { it.balance > 0.5 && (it.days ?: 0) > 60 }.sortedByDescending { it.days ?: 0 },
      categories = catRevenue,
      recent = live.sortedByDescending { it.createdAt }.take(8),
      productCount = d.products.size,
      activeCount = active.size,
    )
  }

  /** روزِ شمارهٔ n از ۱۹۷۰ ⇐ `yyyy-MM-dd` (وارونهٔ `epochDay`، «civil from days») */
  fun isoOf(n: Long): String {
    val z = n + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val dd = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val yy = if (m <= 2) y + 1 else y
    return "%04d-%02d-%02d".format(yy, m, dd)
  }
}
