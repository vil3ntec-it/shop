package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry

/** یک ورودِ انبارِ این کالا، با ماندهٔ محموله‌اش اگر انقضا دارد (`ExpiryEngine.lots`) */
data class EntryUi(val entry: WarehouseEntry, val lot: ExpiryEngine.Lot?)

/** صفحهٔ یک کالا در انبار */
data class StockItemUi(
  val product: Product,
  val stock: Double,
  val level: StockLevel,
  /** تازه‌ترین اول — همان ترتیبِ صفحهٔ پیشین */
  val entries: List<EntryUi>,
)

/** حالتِ خالصِ `stock/{id}` (آزمون: `StockItemModelTest`) */
object StockItemModel {

  /** چند ورودِ تازه پیش از «و n ورودِ قدیمی‌تر» */
  const val PREVIEW = 6

  fun of(d: ShopData, productId: String, today: String): StockItemUi? {
    val p = d.products.find { it.id == productId } ?: return null
    val lots = ExpiryEngine.lots(d, today).filter { it.product.id == p.id }.associateBy { it.entry.id }
    val entries = d.warehouseEntries.filter { it.productId == p.id }
      .sortedByDescending { it.createdAt }
      .map { EntryUi(it, lots[it.id]) }
    val level = when (ShopStore.stockStatus(d, p)) {
      "out" -> StockLevel.OUT
      "low" -> StockLevel.LOW
      else -> StockLevel.OK
    }
    return StockItemUi(p, ShopStore.stock(d, p.id), level, entries)
  }

  /** ورودهای نمایان: شش‌تای تازه، یا همه وقتی باز شده */
  fun shown(entries: List<EntryUi>, expanded: Boolean): List<EntryUi> = if (expanded) entries else entries.take(PREVIEW)

  fun hiddenCount(entries: List<EntryUi>, expanded: Boolean): Int = if (expanded) 0 else (entries.size - PREVIEW).coerceAtLeast(0)
}
