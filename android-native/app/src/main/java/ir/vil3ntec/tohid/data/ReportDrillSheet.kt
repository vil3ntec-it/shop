package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.ui.screens.DayEndModel

/**
 *  RPT-13 — «ریز» ِ ردیفِ برگه‌های «گزارش‌ها» ِ کامپیوتر (`ui/v3/analytics/ReportsDesk.kt` ‏`DeskReports`؛ کلیک یا Enter ِ
 *  `KeyMap.REPORTS_DRILL` روی ردیفِ جدول). ردیفِ [row] (۰ = نخستین ردیفِ پس از سرستون) **همان** ردیفِ `ReportCsv.of(key, …)` است:
 *
 *  - «فروش» (`sales`): همان فاکتور (همان صافی و ترتیبِ برگه، لغوشده هم) ⇐ قلم‌هایش: کالا، مقدار، فی، جمع؛ جمعِ «جمع» = «مبلغ کل»
 *    ِ همان ردیف.
 *  - «فروشِ هر کاربر» (`sellers`): همان فروشنده (خانهٔ اول) ⇐ فاکتورهایش در همان بازه (همان صافیِ `ReportEngine.bySeller`: بی
 *    لغوشده): روز، فاکتور، فروش؛ شمار = «تعدادِ فاکتور» و جمع = «فروشِ خالص» ِ همان ردیف. با «فروشندهٔ هر ردیف» (POS-23) یک
 *    فاکتور میانِ چند فروشنده پخش است ⇒ ریز نیست (null) تا جمعِ ریز با ردیف نخواند.
 *  - «سود و زیان» (`profit`): ردیفِ شمار/ناخالص/تخفیف/خالص/بهای تمام‌شده ⇐ فاکتورهای همان صافیِ `ReportEngine.sales` (بی لغوشده)
 *    با همان پنج ستون (جمعِ هر ستون = همان ردیف)؛ «مرجوعی» ⇐ برگشت‌های بازه (بی برگشتِ فاکتورِ لغوشده)؛ «مصارف» ⇐ همان برگهٔ
 *    «مصارف» ِ بازه. سودِ ناخالص و خالص حاصلِ ردیف‌های دیگرند ⇒ ریز نیستند (null).
 *  - ردیفِ کالا («کالاها» `products`، «تحلیلِ موجودی» `insight`، «نیاز به سفارش» `reorder`) ⇐ گردشِ موجودیِ همان کالا
 *    (`ReportCsv.moves`).
 *  - ردیفِ مشتری («قرض‌داران» `debtors`، «سنِ بدهی» `aging`، «یادآوری» `reminders`) ⇐ صورت‌حسابِ همان مشتری
 *    (`ReportCsv.statement`)؛ «مشتریانِ ارزشمند» (`rfm`) ⇐ خریدهای همان مشتری در همان پنجره (جمع = «مبلغِ خرید»).
 *
 *  برگه‌های [RECORD_ROWS] ردیفِ تک‌سند دارند (هر ردیف خودش ریز است: یک خرید، یک خرج، یک حرکت …) ⇒ null؛ ردیفِ بی‌جا هم null.
 *  فقط خواندنی، بی Context؛ آزمون: `ReportDrillSheetTest`.
 */
object ReportDrillSheet {

  /** برگه‌هایی که ردیفشان ریز دارد */
  val KEYS: Set<String> = setOf("sales", "sellers", "profit", "products", "insight", "reorder", "debtors", "aging", "reminders", "rfm")

  /** برگه‌هایی که هر ردیفشان خودش یک سند است (ریزی زیرش نیست) — با [KEYS] همهٔ برگه‌های «گزارش‌ها» */
  val RECORD_ROWS: Set<String> = setOf("purchases", "expenses", "stock", "expiry", "duplicates", "gaps", "shrink")

  private fun whole(v: Double): String = Math.round(v).toString()

  fun of(
    key: String, d: ShopData, from: String, to: String, row: Int, lineSeller: Boolean = false, today: String = to,
  ): ReportCsv.Sheet? = if (row < 0) null else when (key) {
    "sales" -> saleLines(d, from, to, row)
    "sellers" -> if (lineSeller) null else sellerInvoices(d, from, to, row)
    "profit" -> profit(d, from, to, row)
    "products" -> d.products.getOrNull(row)?.let { productMoves(d, it) }
    "insight" -> ReportEngine.stockInsight(d, from, to).rows.getOrNull(row)?.let { productMoves(d, it.product) }
    "reorder" -> WarehouseEngine.reorderList(d).getOrNull(row)?.let { productMoves(d, it.product) }
    "debtors" -> d.debtors.getOrNull(row)?.let { statement(d, it.id, it.name, today) }
    "aging" -> CustomerInsight.aging(d, today).lines.getOrNull(row)?.let { statement(d, it.id, it.name, today) }
    "reminders" -> DebtReminder.due(d, today).getOrNull(row)?.let { statement(d, it.debtor.id, it.debtor.name, today) }
    "rfm" -> CustomerInsight.rfm(d, today).getOrNull(row)?.let { rfmPurchases(d, it, today) }
    else -> null
  }

  /** همان صافی و ترتیبِ `ReportCsv.sales` */
  private fun saleLines(d: ShopData, from: String, to: String, row: Int): ReportCsv.Sheet? {
    val sale = d.sales.filter { it.date >= from && it.date <= to }.sortedBy { it.createdAt }.getOrNull(row) ?: return null
    val names = d.products.associate { it.id to it.name }
    val rows = mutableListOf(listOf("کالا", "مقدار", "فی", "جمع"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
    d.saleItems.filter { it.saleId == sale.id }.forEach { l ->
      rows += listOf(l.name.ifBlank { names[l.productId].orEmpty() }, ir.vil3ntec.tohid.qty(l.quantity), whole(l.unitPrice), whole(l.totalPrice))
    }
    return ReportCsv.Sheet(DayEndModel.docLabel(d, "sale:${sale.id}"), rows)
  }

  /** همان گروهِ `ReportEngine.bySeller` (بی «فروشندهٔ هر ردیف»): فروشنده ⇐ فاکتورهایش */
  private fun sellerInvoices(d: ShopData, from: String, to: String, row: Int): ReportCsv.Sheet? {
    val who = ReportEngine.bySeller(d, from, to).getOrNull(row)?.seller ?: return null
    val rows = mutableListOf(listOf("روز", "فاکتور", "فروش"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
    d.sales.filter { it.status != "cancelled" && it.date >= from && it.date <= to && it.seller.trim().ifEmpty { StrGoods.unknownSeller } == who }
      .sortedBy { it.createdAt }
      .forEach { s -> rows += listOf(s.date, DayEndModel.docLabel(d, "sale:${s.id}"), whole(s.finalTotal)) }
    return ReportCsv.Sheet(who, rows)
  }

  /**
   *  ردیف‌های `ReportCsv.profit` به همان ترتیب: ۰ شمار، ۱ ناخالص، ۲ تخفیف، ۳ خالص، ۴ مرجوعی، ۵ بهای تمام‌شده، ۶ سودِ ناخالص،
   *  ۷ مصارف، ۸ سودِ خالص — همان صافی‌های `ReportEngine.sales`.
   */
  private fun profit(d: ShopData, from: String, to: String, row: Int): ReportCsv.Sheet? = when (row) {
    0, 1, 2, 3, 5 -> {
      val index = ShopStore.salesIndex(d)
      val rows = mutableListOf(listOf("روز", "فاکتور", "فروشِ ناخالص", "تخفیف", "فروشِ خالص", "بهای تمام‌شدهٔ کالا"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
      d.sales.filter { it.date in from..to && it.status != "cancelled" }.sortedBy { it.createdAt }.forEach { s ->
        val cogs = index.itemsBySale[s.id].orEmpty().sumOf { it.quantity * it.purchasePrice }
        rows += listOf(s.date, DayEndModel.docLabel(d, "sale:${s.id}"), whole(s.total), whole(s.discount), whole(s.finalTotal), whole(cogs))
      }
      ReportCsv.Sheet("$from — $to", rows)
    }
    4 -> {
      val index = ShopStore.salesIndex(d)
      val names = d.products.associate { it.id to it.name }
      val rows = mutableListOf(listOf("روز", "فاکتور", "کالا", "مقدار", "مبلغ"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
      d.saleReturns.filter { it.date in from..to && index.saleById[it.saleId]?.status != "cancelled" }.sortedBy { it.createdAt }.forEach { r ->
        val item = index.itemById[r.saleItemId]
        rows += listOf(
          r.date, if (index.saleById[r.saleId] != null) DayEndModel.docLabel(d, "sale:${r.saleId}") else "",
          item?.name?.ifBlank { null } ?: names[r.productId].orEmpty(), ir.vil3ntec.tohid.qty(r.quantity), whole(r.amount),
        )
      }
      ReportCsv.Sheet("$from — $to", rows)
    }
    7 -> ReportCsv.of("expenses", d, from, to, to)
    else -> null
  }

  private fun productMoves(d: ShopData, p: Product): ReportCsv.Sheet =
    ReportCsv.moves(d, d.stockMovements.filter { it.productId == p.id }, p.name)

  private fun statement(d: ShopData, id: String, name: String, today: String): ReportCsv.Sheet =
    ReportCsv.statement(d, id, today).copy(name = name)

  /** همان صافیِ `CustomerInsight.rfm`: بی لغوشده، در پنجرهٔ ۳۶۵ روزه، مبلغ = خالص − برگشتیِ همان فاکتور */
  private fun rfmPurchases(d: ShopData, x: CustomerInsight.Rfm, today: String): ReportCsv.Sheet {
    val names = d.debtors.associateBy { it.id }
    val returned = d.saleReturns.groupBy { it.saleId }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val rows = mutableListOf(listOf("روز", "فاکتور", "مبلغِ خرید"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
    d.sales.filter { s ->
      s.status != "cancelled" && s.date >= x.firstPurchase && s.date <= today &&
        s.debtorId?.takeIf { it.isNotBlank() }?.let { CustomerCrm.resolve(names, it) } == x.id
    }.sortedBy { it.createdAt }.forEach { s ->
      rows += listOf(s.date, DayEndModel.docLabel(d, "sale:${s.id}"), whole(s.finalTotal - (returned[s.id] ?: 0.0)))
    }
    return ReportCsv.Sheet(x.name, rows)
  }
}
