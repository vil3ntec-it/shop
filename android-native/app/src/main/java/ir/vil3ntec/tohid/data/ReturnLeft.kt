package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.money.ReturnCap
import ir.vil3ntec.tohid.qty

/**
 *  ماندهٔ قابلِ برگشتِ یک قلم از دادهٔ همگام‌شدهٔ همین دستگاه (سفرِ JB5) — حساب فقط `ReturnCap` ِ core.
 *
 *  ⛔ برگشته = بیشینهٔ شمارندهٔ قلم (`SaleItem.returnedQty`) و جمعِ ردیف‌های برگشتِ همان `saleItemId`. شمارنده با
 *  ادغامِ فیلد جلو می‌رود و برگشتِ دستگاهِ دیگر را ممکن است نشمرده باشد، ولی ردیفش با همگام‌سازی رسیده است. پس
 *  صفحهٔ برگشت و هر دو راهِ ثبت (`ReturnFlow.quote`، `SalesEngine.recordReturn`) همین را می‌خوانند و بیش از آن
 *  نمی‌پذیرند؛ سرور هم همین سقف را دارد (`server/src/lib/return-line-cap.js`) برای دستگاهی که آفلاین بود.
 *  دکمهٔ «برگشت» ِ فهرستِ فاکتورها هم همین را می‌خواند ([canReturn]، [Index]) — نه شمارندهٔ تنها.
 */
object ReturnLeft {

  fun state(d: ShopData, item: SaleItem): ReturnCap.State {
    val ratio = d.sales.find { it.id == item.saleId }?.let { SalesEngine.paidRatio(it) } ?: 1.0
    return state(item, ratio, d.saleReturns.filter { it.saleItemId == item.id })
  }

  /** همان [state] با ردیف‌های برگشتِ همین قلم ([prior]) و سهمِ پرداختِ فاکتور ([ratio]) از پیش — برای [Index] */
  fun state(item: SaleItem, ratio: Double, prior: List<SaleReturn>): ReturnCap.State =
    ReturnCap.state(
      ReturnCap.milli(item.quantity), ReturnCap.capMinor(item.totalPrice, ratio),
      prior.map { ReturnCap.Row(ReturnCap.milli(it.quantity), ReturnCap.minor(it.amount)) }, ReturnCap.milli(item.returnedQty),
    )

  /** مقدارِ قابلِ برگشت، به واحدِ دفتر */
  fun left(d: ShopData, item: SaleItem): Double = state(d, item).leftMilli / 1000.0

  /**
   *  دکمهٔ «برگشت» ِ فاکتور: زنده است و دست‌کم یک قلمش ماندهٔ قابلِ برگشت دارد — همان حسابِ پنجرهٔ برگشت
   *  (`ReturnModel.lines`، `ReturnFlowSheet`، `ReturnFlowDialog`)، تا دکمه پنجرهٔ خالی باز نکند.
   */
  fun canReturn(d: ShopData, sale: Sale): Boolean =
    sale.status != "cancelled" && d.saleItems.any { it.saleId == sale.id && left(d, it) > 0 }

  /**
   *  نمایهٔ یک‌باره برای فهرستِ فاکتورها (ده هزار فاکتور): ردیف‌های برگشت به ازای قلم، یک بار — همان معنای [canReturn]
   *  و [left]، بی گشتنِ همهٔ برگشت‌ها برای هر ردیف.
   */
  class Index(d: ShopData) {
    private val items: Map<String, List<SaleItem>> = d.saleItems.groupBy { it.saleId }
    private val returns: Map<String, List<SaleReturn>> = d.saleReturns.filter { it.saleItemId.isNotEmpty() }.groupBy { it.saleItemId }

    /** ماندهٔ مقدار به واحدِ دفتر — مقدار به سهمِ پرداخت بسته نیست (فقط سقفِ پول) */
    fun left(item: SaleItem): Double = state(item, 1.0, returns[item.id].orEmpty()).leftMilli / 1000.0

    fun canReturn(sale: Sale): Boolean = sale.status != "cancelled" && items[sale.id].orEmpty().any { left(it) > 0 }
  }

  /** مقدارِ برگشته تا امروز (همهٔ دستگاه‌ها)، به واحدِ دفتر — مبنای «پیش از این برگشت» ِ `ReturnShare` */
  fun returned(d: ShopData, item: SaleItem): Double = state(d, item).returnedMilli / 1000.0

  /** « · پیش‌تر برگشته: ۱ از ۲ عدد» — یا خالی وقتی هنوز چیزی برنگشته */
  fun note(d: ShopData, item: SaleItem, unit: String): String {
    val s = state(d, item)
    if (s.returnedMilli <= 0L) return ""
    val u = if (unit.isNotBlank()) " $unit" else ""
    return " · " + StrSale.returnedOf(qty(s.returnedMilli / 1000.0) + u, qty(s.soldMilli / 1000.0) + u)
  }
}
