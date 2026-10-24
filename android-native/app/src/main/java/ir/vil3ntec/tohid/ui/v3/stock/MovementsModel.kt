package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/** گونهٔ حرکت (`StockMovement.type`) — برچسب و آیکنش در صفحه */
enum class MoveKind { PURCHASE, SALE, CUSTOMER_RETURN, SALE_CANCEL, SUPPLIER_RETURN, ADJUSTMENT, OTHER;
  companion object {
    fun of(type: String): MoveKind = when (type) {
      "purchase_in" -> PURCHASE
      "sale" -> SALE
      "customer_return" -> CUSTOMER_RETURN
      "sale_cancel" -> SALE_CANCEL
      "supplier_return" -> SUPPLIER_RETURN
      "adjustment" -> ADJUSTMENT
      else -> OTHER
    }
  }
}

/** یک حرکت و جایی که به آن اشاره می‌کند (فاکتور ⇐ `sales/{id}`، خرید ⇐ صفحهٔ تأمین‌کننده) */
data class MoveUi(val move: StockMovement, val kind: MoveKind, val link: String?)

/** یک روز: `yyyy-MM-dd` و حرکت‌هایش، تازه‌ترین اول */
data class MoveDay(val date: String, val moves: List<MoveUi>)

/** حالتِ خالصِ `stock/{id}/movements` (آزمون: `MovementsModelTest`) */
object MovementsModel {

  fun days(d: ShopData, productId: String): List<MoveDay> =
    d.stockMovements.filter { it.productId == productId }
      .sortedByDescending { it.createdAt }
      .map { MoveUi(it, MoveKind.of(it.type), link(d, it)) }
      .groupBy { it.move.date }
      .map { (date, moves) -> MoveDay(date, moves) }

  /**
   *  مقصدِ یک حرکت: فروش و لغوِ فروش ⇐ همان فاکتور؛ مرجوعی ⇐ فاکتورِ همان مرجوعی؛ ورودِ
   *  خرید ⇐ تأمین‌کنندهٔ همان خرید (شناسهٔ خرید یا ورودِ انبارِ خرید). بی مقصد ⇒ null.
   */
  fun link(d: ShopData, m: StockMovement): String? {
    val ref = m.refId ?: return null
    return when (MoveKind.of(m.type)) {
      MoveKind.SALE, MoveKind.SALE_CANCEL -> ref.takeIf { id -> d.sales.any { it.id == id } }?.let(SalesPaths::invoice)
      MoveKind.CUSTOMER_RETURN -> d.saleReturns.find { it.id == ref }?.saleId
        ?.takeIf { id -> d.sales.any { it.id == id } }?.let(SalesPaths::invoice)
      MoveKind.PURCHASE -> {
        val purchase = d.purchases.find { it.id == ref }
          ?: d.warehouseEntries.find { it.id == ref }?.purchaseId?.let { pid -> d.purchases.find { it.id == pid } }
        purchase?.supplierId?.takeIf { id -> id.isNotBlank() && d.suppliers.any { it.id == id } }?.let(MoneyPaths::supplier)
      }
      else -> null
    }
  }

  /** روزِ پیش از `today` (`yyyy-MM-dd`) — برای سرخطِ «دیروز» */
  fun yesterday(today: String): String {
    val parts = today.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return ""
    val c = java.util.GregorianCalendar(parts[0], parts[1] - 1, parts[2])
    c.add(java.util.Calendar.DAY_OF_MONTH, -1)
    return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
  }
}
