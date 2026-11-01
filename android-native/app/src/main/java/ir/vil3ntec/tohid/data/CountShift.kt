package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.CountDuringSales

/*
 *  فروش‌های حینِ شمارش (INV-10، S20): دفترِ دکان ⇐ ورودیِ `core/stock/CountDuringSales`.
 *
 *  لحظهٔ شمردنِ هر کالا (برگهٔ «انبارگردانیِ تازه») و گردشِ همان کالا در `stockMovements` پس از آن لحظه
 *  (فروش −، برگشت/رسید +؛ انتقالِ میانِ مکان‌ها جمعِ موجودی را عوض نمی‌کند و بیرون است) ⇒ مقداری که
 *  `WarehouseEngine.countLines/stockCount` از «موجودیِ امروز» کم می‌کند تا مورد انتظار هنگامِ شمارش
 *  به دست آید. لحظهٔ خالی ⇒ نقشهٔ خالی، یعنی بیت‌به‌بیت همان رفتارِ امروز.
 */
object CountShift {

  /** حرکت‌هایی که فقط مکان را عوض می‌کنند، نه جمعِ موجودی را */
  private val TRANSFER_TYPES = setOf(StockLedger.TRANSFER_OUT, StockLedger.TRANSFER_IN, StockLedger.TRANSFER_LEGACY)

  /** گردشِ هر کالا پس از لحظهٔ شمردنش، به واحد */
  fun of(d: ShopData, countedAt: Map<String, Long>): Map<String, Double> {
    if (countedAt.isEmpty()) return emptyMap()
    val moves = d.stockMovements.filter { it.productId in countedAt && it.type !in TRANSFER_TYPES }
      .map { CountDuringSales.Move(it.productId, it.createdAt, Math.round(it.qty * 1000.0)) }
    return CountDuringSales.movedAfter(countedAt, moves).mapValues { it.value / 1000.0 }
  }
}
