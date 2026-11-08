package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.ui.screens.ProductListModel

/**
 *  عددهای صفحهٔ یک کالا (`products/{id}`) — همان حساب‌های صفحهٔ پیشین، یک‌جا:
 *  موجودی از `ShopStore`، فروخته‌شده و سودِ کسب‌شده از `ReportEngine.productStat`،
 *  سودِ موجودی = سودِ هر واحد × موجودیِ نامنفی. آزمون: `ProductDetailModelTest`.
 *  PRD-15: بهای میانگینِ موزونِ همین حالا از همان بازپخشِ گزارش‌ها (`ReportBridge.stockCostOf`)؛ فقط خواندنی.
 */
object ProductDetailModel {

  data class Figures(
    val stock: Double,
    val status: ProductListModel.Status,
    val unitProfit: Double,
    /** اگر همهٔ آنچه مانده به قیمتِ امروز فروخته شود */
    val stockProfit: Double,
    val sold: Double,
    val earned: Double,
    /** PRD-15 — بهای میانگینِ موزون (null = کیت/بی‌موجودی یا هیچ ورود و فروشی) */
    val avgCost: ReportCore.StockCostRow? = null,
  )

  fun figures(d: ShopData, p: Product): Figures {
    val stock = ShopStore.stock(d, p.id)
    val (sold, earned) = ReportEngine.productStat(d, p.id)
    val unitProfit = p.salePrice - p.purchasePrice
    return Figures(
      stock = stock,
      status = ProductListModel.status(p, stock),
      unitProfit = unitProfit,
      stockProfit = unitProfit * stock.coerceAtLeast(0.0),
      sold = sold,
      earned = earned,
      avgCost = avgCostOf(d, p),
    )
  }

  /** کیت (PRD-10) و کالای بی‌موجودی بهای میانگینِ خودشان را ندارند */
  fun avgCostOf(d: ShopData, p: Product): ReportCore.StockCostRow? =
    if (!p.kit.isNullOrEmpty() || p.stockless == true) null
    else ReportBridge.stockCostOf(d, listOf(p.id)).rows.firstOrNull()

  /** حرکت‌های اخیرِ همین کالا، تازه‌ترین اول */
  fun recentMoves(d: ShopData, productId: String, limit: Int = 5): List<StockMovement> =
    d.stockMovements.filter { it.productId == productId }.sortedByDescending { it.createdAt }.take(limit)

  fun moveCount(d: ShopData, productId: String): Int = d.stockMovements.count { it.productId == productId }

  /** گونهٔ حرکت (آیکن و برچسب) از `StockMovement.type` */
  enum class MoveKind { PURCHASE_IN, SALE, CUSTOMER_RETURN, SALE_CANCEL, SUPPLIER_RETURN, ADJUSTMENT, OTHER }

  fun kindOf(type: String): MoveKind = when (type) {
    "purchase_in" -> MoveKind.PURCHASE_IN
    "sale" -> MoveKind.SALE
    "customer_return" -> MoveKind.CUSTOMER_RETURN
    "sale_cancel" -> MoveKind.SALE_CANCEL
    "supplier_return" -> MoveKind.SUPPLIER_RETURN
    "adjustment", "count_adjust" -> MoveKind.ADJUSTMENT
    else -> MoveKind.OTHER
  }
}
