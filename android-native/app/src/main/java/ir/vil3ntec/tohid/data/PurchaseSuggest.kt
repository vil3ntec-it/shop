package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.DemandForecast
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.core.purchase.SupplierPricing

/*
 *  «پیشنهادِ سفارش» ِ برنامهٔ مدیریت — INV-13 و INV-14 (§۶.۵، S23): دفترِ دکان ⇐ ورودیِ هسته.
 *
 *  همهٔ حساب (روزِ هفته، فصل، زمانِ تأمین، چند روز کافی است، مقدار، دلیل و اطمینان) در
 *  `core/purchase/DemandForecast` است؛ این‌جا فقط فروشِ خالصِ هر روز از فاکتورهای لغونشده، نخستین روزِ
 *  هر کالا (فروش یا خرید)، تأمین‌کننده (همان کالا، وگرنه تازه‌ترین خرید)، زمانِ تأمینِ سنجیدهٔ او و
 *  «در راه» ِ سفارش‌های باز ساخته می‌شود.
 *
 *  ⛔ «سفارش با یک کلید» فقط **پیش‌نویس** می‌سازد (همان `POST /api/shop/purchase-orders`)؛ فرستادن به
 *  تأمین‌کننده همان دکمهٔ جدای جزئیاتِ سفارش است. پس هیچ سفارشی بی تأییدِ آدم فرستاده نمی‌شود.
 */
object PurchaseSuggest {

  /** پنجرهٔ امسال + همان پنجره در پارسال (فصل) */
  const val HISTORY_DAYS = DemandForecast.YEAR_DAYS + DemandForecast.WINDOW_DAYS

  data class Row(
    val product: Product,
    /** `null` ⇒ نه تأمین‌کننده‌ای روی کالا هست و نه خریدی؛ «با یک کلید» ندارد */
    val supplierId: String?,
    val forecast: DemandForecast.Forecast,
    val priceMinor: Long,
  ) {
    val due: Boolean get() = forecast.qtyMilli > 0
  }

  /**
   *  پیشنهادِ هر کالای فعال؛ نیازمندِ سفارش بالا، بعد کم‌روزتر. [smart] (AI-07، تنظیمِ خاموشِ `SmartForecastPref`) ⇒
   *  میانگینِ متحرک یا Croston با نوسان و پیش‌بینیِ هر روز (`SmartDemand`)؛ خاموش ⇒ همان حسابِ پیشین.
   */
  fun rows(d: ShopData, orders: List<PurchaseOrderDto>, today: String, smart: Boolean = false): List<Row> {
    val t = CivilDays.jdnOfIso(today) ?: return emptyList()
    val from = t - HISTORY_DAYS + 1
    val live = HashMap<String, Int>()
    for (s in d.sales) {
      if (s.status == "cancelled") continue
      live[s.id] = CivilDays.jdnOfIso(s.date) ?: continue
    }
    val history = HashMap<String, ArrayList<DemandForecast.Day>>()
    val first = HashMap<String, Int>()
    fun seen(productId: String, jdn: Int) {
      val old = first[productId]
      if (old == null || jdn < old) first[productId] = jdn
    }
    for (i in d.saleItems) {
      if (i.productId.isBlank()) continue
      val j = live[i.saleId] ?: continue
      seen(i.productId, j)
      if (j in from..t) history.getOrPut(i.productId) { ArrayList() } += DemandForecast.Day(j, PurchaseOrderBook.milli(i.quantity - i.returnedQty))
    }
    for (p in d.purchases) CivilDays.jdnOfIso(p.date)?.let { seen(p.productId, it) }

    val buys = PurchaseOrderBook.buys(d)
    val known = d.suppliers.mapTo(HashSet()) { it.id }
    val onOrder = PurchaseOrderBook.onOrderMilli(orders)
    val leads = HashMap<String, ReorderCore.Lead>()
    return d.products.filter { it.active }.mapNotNull { p ->
      val sid = p.supplierId.takeIf { it in known } ?: SupplierPricing.latestSupplier(buys, p.id)?.takeIf { it in known }
      val lead = sid?.let { s -> leads.getOrPut(s) { PurchaseOrderBook.leadTime(orders, s) } }
      val f = try {
        DemandForecast.forecast(
          DemandForecast.Input(
            todayJdn = t,
            history = history[p.id].orEmpty(),
            onHandMilli = PurchaseOrderBook.milli(ShopStore.stock(d, p.id)),
            onOrderMilli = onOrder[p.id] ?: 0L,
            minStockMilli = PurchaseOrderBook.milli(p.minStock),
            leadDays = lead?.days,
            firstJdn = first[p.id],
            wholeUnits = p.unit !in PurchaseOrderBook.WEIGHED_UNITS,
            smart = smart,
          ),
        )
      } catch (e: ArithmeticException) {
        null
      } ?: return@mapNotNull null
      val price = sid?.let { PurchaseOrderBook.lastPriceMinor(d, it, p, buys) } ?: PurchaseOrderBook.minor(p.purchasePrice)
      Row(p, sid, f, price)
    }.sortedWith(compareBy<Row>({ !it.due }, { it.forecast.daysLeft ?: Int.MAX_VALUE }, { it.product.name }))
  }

  /** ردیف‌های پیش‌نویسِ «با یک کلید» برای یک تأمین‌کننده: فقط نیازمندها، با مقدارِ پیشنهادی و آخرین فیِ او */
  fun draftLines(rows: List<Row>, supplierId: String): List<PoLineDto> =
    rows.filter { it.supplierId == supplierId && it.due }.mapIndexed { i, r ->
      PoLineDto(id = "l${i + 1}", productId = r.product.id, name = r.product.name, unit = r.product.unit, qtyMilli = r.forecast.qtyMilli, priceMinor = r.priceMinor)
    }

  /** همان سنجشِ هسته پیش از فرستادن به سرور؛ `null` = درست */
  fun check(supplierId: String, lines: List<PoLineDto>): PurchaseOrderCore.Failure? =
    PurchaseOrderCore.checkOrder(
      PurchaseOrderCore.Order(supplierId, PurchaseOrderCore.State.DRAFT, lines.map { PurchaseOrderCore.Line(it.id, it.productId, it.qtyMilli, it.priceMinor) }),
    )
}
