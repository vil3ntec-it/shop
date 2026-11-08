package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.stock.CountAbsorb

/*
 *  INV-10: فروشِ آفلاینِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد — دفترِ دکان ⇐ ورودیِ `core/stock/CountAbsorb`
 *  (همزادِ `license/count-absorb-core.js` ِ سرور؛ قاعدهٔ دقیق سرِ همان پرونده).
 *
 *  ردیفِ شمارش = ردیفِ انبارِ «اصلاح» با لحظه (`countedAt`/`seenMilli`، از ثبتِ «انبارگردانی») یا ردیفِ قدیمیِ شمارش
 *  (`adjKind = count` یا یادداشتِ «انبارگردانی…» ⇐ بی لحظه، هیچ جذب نمی‌کند)؛ حرکت = هر ردیفِ `stockMovements`.
 *  ⛔ پرچمِ سرورِ `shop.count_absorb` (`StockFlag`، پیش‌فرض خاموش): خاموش ⇒ هیچ، `ShopStore.stock` بیت‌به‌بیت همان.
 */
object CountAbsorbWiring {

  /** پرچمِ `shop.count_absorb` ِ دکانِ فعال (`StockFlag.load`) */
  @Volatile var serverFlag: Boolean = false

  private const val COUNT_NOTE = "انبارگردانی" // داده: سرِ یادداشتِ ردیفِ شمارش (`WarehouseEngine.stockCount`، نسخهٔ وب)

  fun isCountLine(e: WarehouseEntry): Boolean =
    e.isAdjustment && e.productId.isNotEmpty() && (e.countedAt != null || e.adjKind == "count" || e.notes.startsWith(COUNT_NOTE))

  fun lines(d: ShopData): List<CountAbsorb.Line> = d.warehouseEntries.filter(::isCountLine).map {
    CountAbsorb.Line(it.id, it.productId, it.countedAt ?: it.createdAt, if (it.countedAt != null) it.seenMilli else null, it.createdAt, it.correctedAt)
  }

  fun events(d: ShopData): List<CountAbsorb.Event> = d.stockMovements.map {
    CountAbsorb.Event(it.id, it.productId, it.createdAt, Math.round(it.qty * 1000.0), it.type, it.refId, it.correctedAt)
  }

  /** جذب‌شدهٔ هر کالا (هزارم)؛ پرچم خاموش یا بی ردیفِ لحظه‌دار ⇒ خالی (بی پیمایشِ دفتر) */
  fun gaps(d: ShopData, on: Boolean = serverFlag): Map<String, CountAbsorb.Gap> {
    if (!on || d.warehouseEntries.none { it.countedAt != null }) return emptyMap()
    return CountAbsorb.gaps(lines(d), events(d))
  }

  /** ثبتِ شمارش: دانستهٔ همین دستگاه از [productId] (هزارم) */
  fun seen(d: ShopData, productId: String, countedAt: Long, now: Long): Long =
    CountAbsorb.seen(events(d).filter { it.productId == productId }, productId, countedAt, now)

  /** لحظهٔ قابلِ ثبت: نه پس از [now]، نه کهنه‌تر از پنجره؛ بی لحظه ⇒ null (ردیفِ بی لحظه، مثلِ پیش) */
  fun momentOf(at: Long?, now: Long): Long? = at?.takeIf { it > 0 }?.coerceAtMost(now)?.takeIf { it > now - CountAbsorb.WINDOW_MS }

  /**
   *  ردیفِ سابقهٔ «پیش از شمارش ثبت شده بود — در شمارش آمده است» (INV-10، ممیزیِ صاحب): مقدارِ جذب‌شده، با تاریخ و
   *  لحظهٔ همان ردیفِ شمارش. فقط نمایش است — در دفتر نوشته و همگام نمی‌شود.
   */
  fun historyRows(d: ShopData, productId: String): List<StockMovement> {
    val g = gaps(d)[productId] ?: return emptyList()
    val line = d.warehouseEntries.find { it.id == g.lineId } ?: return emptyList()
    return listOf(
      StockMovement(
        id = "$ABSORB_TYPE:${line.id}", productId = productId, type = ABSORB_TYPE, qty = -g.gapMilli / 1000.0,
        date = line.date, notes = StrStock.countAbsorbed, refId = line.id, createdAt = line.createdAt,
      ),
    )
  }

  const val ABSORB_TYPE = "count_absorb"
}
