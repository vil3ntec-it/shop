package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.receipt.ReceiptToken
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.formatMonth
import ir.vil3ntec.tohid.plain

/** فیلترهای تاریخچه — همان پنج تراشهٔ امروز (منطقِ فیلتر دست نخورده) */
enum class HistoryFilter(val id: String) {
  ALL("all"), CASH("cash"), CREDIT("credit"), NON_CASH(PayFlow.NON_CASH), CANCELLED("cancelled");

  companion object {
    fun of(id: String?): HistoryFilter = entries.firstOrNull { it.id == id } ?: ALL
  }
}

/** یک روزِ فهرست: سرخطِ چسبان و فاکتورهایش */
data class HistoryDay(val date: String, val sales: List<Sale>)

/** ردیفِ یک فاکتور — متن‌ها آماده؛ رنگ هرگز تنها نیست (قرص برچسب دارد) */
data class HistoryRow(
  val id: String,
  val number: String,
  val name: String,
  val subtitle: String,
  val amount: Double,
  val state: SaleState,
  val payLabel: String,
  val credit: Boolean,
  val canReturn: Boolean,
  /** لغو و «اصلاح» — نه برای فاکتورِ لغوشده یا اینترنتی (ORD-20) */
  val canCancel: Boolean,
  /** سندهای برگشتِ تازهٔ همین فاکتور (RET-12) */
  val returnDocs: List<String>,
  /** PAY-07 — وضعیتِ پرداختِ غیرنقد برای تراشه؛ null ⇒ نقد، یا همه تأییدشده */
  val payState: String? = null,
)

/** کارتِ آمارِ بالای تاریخچه */
data class HistoryStats(val total: Double, val count: Int, val cancelled: Int)

/**
 *  تاریخچهٔ فروش (`sales`) — خالص، همان قاعده‌های `SalesHistoryScreen`ِ پیشین: فیلترِ گروهِ
 *  پرداخت از `PayFlow.payGroup`، ماه، جستجوی شمارهٔ فاکتور یا نامِ مشتری، QRِ رسید با
 *  «برگشت و تعویض» روشن (`ReturnFlow.find`)، تازه‌ترین اول. افزوده: گروه‌بندیِ روز.
 */
object HistoryModel {

  /** «همهٔ ماه‌ها» — کلیدی که هیچ ماهی نمی‌تواند باشد */
  const val ALL_MONTHS = "*"

  /** دلیلِ لغوِ «اصلاحِ فاکتور» و نامِ قلمِ آزادِ بی‌نام — **داده** در سابقه و فاکتور، همان فارسیِ پیشین */
  const val EDIT_IN_CART = "ویرایش در سبد"
  const val FREE_ITEM = "قلم آزاد"

  /** روزِ پیش از `today` (`yyyy-MM-dd`) — برای سرخطِ «دیروز» */
  fun yesterday(today: String): String = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(today, -1).orEmpty()

  /** ماه‌هایی که واقعاً فاکتور دارند، تازه‌ترین اول */
  fun months(d: ShopData): List<String> =
    listOf(ALL_MONTHS) + d.sales.map { it.date.take(7) }.filter { it.length == 7 }.distinct().sortedDescending()

  fun monthLabel(m: String): String = if (m == ALL_MONTHS) StrSale.allMonths else formatMonth(m)

  fun shown(d: ShopData, filter: HistoryFilter, search: String, month: String, returnsV2: Boolean, shopTag: String? = null): List<Sale> {
    val term = search.trim()
    //  RET-01 — QRِ رسید (اسکنر یا دوربین) همان فاکتور را پیدا می‌کند
    if (returnsV2 && term.startsWith(ReturnFlow.CODE_PREFIX.take(3))) return ReturnFlow.find(d, term)
    //  فاکتورِ نسخهٔ ۲: نشانیِ QRِ رسید، فقط فروشِ همین دکان
    if (returnsV2 && shopTag != null && ReceiptToken.parse(term) != null) return ReturnFlow.find(d, term, shopTag = shopTag)
    val names = d.debtors.associate { it.id to it.name }
    return d.sales
      .filter {
        when (filter) {
          //  PAY (v3 A4): گروه از `payments` اگر هست — کارت و کیف پول و حواله «نقدی» نیستند
          HistoryFilter.CASH, HistoryFilter.CREDIT, HistoryFilter.NON_CASH -> it.status != "cancelled" && PayFlow.payGroup(it) == filter.id
          HistoryFilter.CANCELLED -> it.status == "cancelled"
          HistoryFilter.ALL -> true
        }
      }
      .filter { month == ALL_MONTHS || it.date.startsWith(month) }
      .filter { s ->
        term.isBlank() || s.invoiceLabel().orEmpty().contains(term, ignoreCase = true) ||
          names[s.debtorId].orEmpty().contains(term, ignoreCase = true)
      }
      .sortedByDescending { it.createdAt }
  }

  /** روزبه‌روز، به همان ترتیبِ فهرست (تازه‌ترین روز اول) */
  fun days(sales: List<Sale>): List<HistoryDay> {
    val out = LinkedHashMap<String, MutableList<Sale>>()
    sales.forEach { out.getOrPut(it.date) { mutableListOf() } += it }
    return out.map { (date, list) -> HistoryDay(date, list) }
  }

  fun dayTitle(date: String, today: String, yesterday: String): String = when (date) {
    today -> ir.vil3ntec.tohid.core.i18n.Str.today
    yesterday -> ir.vil3ntec.tohid.core.i18n.StrKit.yesterday
    else -> displayDate(date)
  }

  fun stats(d: ShopData): HistoryStats {
    val active = d.sales.filter { it.status != "cancelled" }
    return HistoryStats(active.sumOf { it.finalTotal }, active.size, d.sales.size - active.size)
  }

  fun statsLine(s: HistoryStats): String = StrSale.invoicesLine(plain(s.count), plain(s.cancelled))

  /** همان حسابِ پنجرهٔ برگشت (`ReturnLeft`: برگشتِ دستگاه‌های دیگر هم) — نه شمارندهٔ تنها */
  fun canReturn(d: ShopData, s: Sale): Boolean = ir.vil3ntec.tohid.data.ReturnLeft.canReturn(d, s)

  fun returnDocs(d: ShopData, s: Sale): List<String> =
    d.saleReturns.filter { it.saleId == s.id }.mapNotNull { it.docId }.distinct()

  /**
   *  @param fixed POS-40: روشِ پرداختِ این فاکتور اصلاح شد (`PayFixFlow.fixedSales`) ⇒ نشان کنارِ نوع
   *  @param look نمایه‌های یک‌بارهٔ تاریخچه (`RowLookup`) — همان معنا، بی گشتنِ همهٔ دفتر برای هر ردیف
   *  @param withDate بخشِ ماه و سال: روزِ فاکتور پیش از ساعت
   */
  fun row(d: ShopData, s: Sale, returnsV2: Boolean, fixed: Boolean = false, look: RowLookup? = null, withDate: Boolean = false): HistoryRow {
    if (look != null) return fast(d, s, returnsV2, look, withDate)
    val state = ReceiptModel.state(s)
    val items = d.saleItems.count { it.saleId == s.id }
    return HistoryRow(
      id = s.id,
      number = "#" + ReceiptModel.number(s),
      name = ReceiptModel.debtorName(d, s) ?: StrSale.cashPay,
      subtitle = listOf(SaleTime.clock(s.createdAt), StrSale.itemsCount(plain(items))).filter { it.isNotBlank() }.joinToString(" · "),
      amount = s.finalTotal,
      state = state,
      payLabel = when (state) {
        SaleState.CANCELLED -> StrSale.cancelled
        SaleState.ONLINE -> StrSale.online
        SaleState.DONE -> ir.vil3ntec.tohid.data.PayFixFlow.kindLabel(ReceiptModel.payLabel(s), fixed)
      },
      credit = PayFlow.payGroup(s) == "credit",
      canReturn = canReturn(d, s),
      canCancel = s.status != "cancelled" && !s.isOnlineOrder(),
      returnDocs = if (returnsV2) returnDocs(d, s) else emptyList(),
      payState = ir.vil3ntec.tohid.data.PayStatusFlow.chip(d, s),
    )
  }

  private fun fast(d: ShopData, s: Sale, returnsV2: Boolean, look: RowLookup, withDate: Boolean): HistoryRow {
    val state = ReceiptModel.state(s)
    val day = if (withDate && s.date.isNotBlank()) displayDate(s.date) else ""
    return HistoryRow(
      id = s.id,
      number = "#" + ReceiptModel.number(s),
      name = s.debtorId?.let(look.debtors::get) ?: StrSale.cashPay,
      subtitle = listOf(day, SaleTime.clock(s.createdAt), StrSale.itemsCount(plain(look.items[s.id]?.size ?: 0)))
        .filter { it.isNotBlank() }.joinToString(" · "),
      amount = s.finalTotal,
      state = state,
      payLabel = when (state) {
        SaleState.CANCELLED -> StrSale.cancelled
        SaleState.ONLINE -> StrSale.online
        SaleState.DONE -> ir.vil3ntec.tohid.data.PayFixFlow.kindLabel(ReceiptModel.payLabel(s), s.id in look.fixed)
      },
      credit = PayFlow.payGroup(s) == "credit",
      canReturn = look.canReturn(s),
      canCancel = s.status != "cancelled" && !s.isOnlineOrder(),
      returnDocs = if (returnsV2) look.returnDocs[s.id].orEmpty() else emptyList(),
      payState = ir.vil3ntec.tohid.data.PayStatusFlow.chip(d, s, look.payChanges[s.id].orEmpty()),
    )
  }

  /** «اصلاحِ فاکتور»: اقلامِ برنگشته با همان قیمت به سبد؛ قلمِ آزاد شناسهٔ تازه می‌گیرد */
  fun reopenLines(d: ShopData, s: Sale, newId: () -> String): List<SalesEngine.CartLine> =
    d.saleItems.filter { it.saleId == s.id }.mapNotNull { item ->
      val left = item.quantity - item.returnedQty
      when {
        left <= 0 -> null
        item.productId.isBlank() -> SalesEngine.CartLine(productId = newId(), quantity = left, unitPrice = item.unitPrice, label = item.name.ifBlank { FREE_ITEM })
        else -> SalesEngine.CartLine(productId = item.productId, quantity = left, unitPrice = item.unitPrice)
      }
    }

  /** فاکتورِ زنده‌ای که QRِ رسید یک‌جا پیدا کرد ⇒ همان لحظه فرمِ برگشت */
  fun scanned(d: ShopData, code: String, shopTag: String? = null): Sale? =
    ReturnFlow.find(d, code, shopTag = shopTag).singleOrNull()?.takeIf { it.status != "cancelled" }
}
