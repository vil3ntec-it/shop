package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.core.i18n.StrRows
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
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone

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
  /** عنوانِ معنادار: نامِ مشتری، وگرنه نامِ کالاها («برنج، روغن و ۲ قلمِ دیگر»)؛ شماره کوچک در پایانِ [subtitle] */
  val title: String = name,
  /** وضعیتِ آرامِ کنارِ مبلغ («• نسیه»، «• لغوشده»)؛ فروشِ نقدیِ عادی ⇒ null (هیچ) */
  val status: V3RowStatus? = null,
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
   *  @param withDate بخشِ ماه و سال و جستجو: روزِ کوتاهِ فاکتور («دیروز ۱۴:۰۵»، «۲۸ میزان ۱۴:۰۵») به‌جای ساعتِ تنها
   *  @param today امروزِ دکان برای «امروز/دیروز» و سالِ جاری ([RowWhen])
   */
  fun row(d: ShopData, s: Sale, returnsV2: Boolean, fixed: Boolean = false, look: RowLookup? = null, withDate: Boolean = false, today: String = ""): HistoryRow {
    if (look != null) return fast(d, s, returnsV2, look, withDate, today)
    val state = ReceiptModel.state(s)
    val items = d.saleItems.filter { it.saleId == s.id }
    val debtor = ReceiptModel.debtorName(d, s)
    val payLabel = payLabel(state, s, fixed)
    val payState = ir.vil3ntec.tohid.data.PayStatusFlow.chip(d, s)
    return HistoryRow(
      id = s.id,
      number = "#" + ReceiptModel.number(s),
      name = debtor ?: StrSale.cashPay,
      subtitle = subtitle(s, withDate, today, if (debtor != null) items.size else null),
      amount = s.finalTotal,
      state = state,
      payLabel = payLabel,
      credit = PayFlow.payGroup(s) == "credit",
      canReturn = canReturn(d, s),
      canCancel = s.status != "cancelled" && !s.isOnlineOrder(),
      returnDocs = if (returnsV2) returnDocs(d, s) else emptyList(),
      payState = payState,
      title = debtor ?: itemsTitle(items.map { ir.vil3ntec.tohid.data.SalesEngine.itemName(d, it) }),
      status = status(state, PayFlow.payGroup(s) == "credit", payLabel, payState),
    )
  }

  private fun fast(d: ShopData, s: Sale, returnsV2: Boolean, look: RowLookup, withDate: Boolean, today: String): HistoryRow {
    val state = ReceiptModel.state(s)
    val items = look.items[s.id].orEmpty()
    val debtor = s.debtorId?.let(look.debtors::get)
    val payLabel = payLabel(state, s, s.id in look.fixed)
    val payState = ir.vil3ntec.tohid.data.PayStatusFlow.chip(d, s, look.payChanges[s.id].orEmpty())
    return HistoryRow(
      id = s.id,
      number = "#" + ReceiptModel.number(s),
      name = debtor ?: StrSale.cashPay,
      subtitle = subtitle(s, withDate, today, if (debtor != null) items.size else null),
      amount = s.finalTotal,
      state = state,
      payLabel = payLabel,
      credit = PayFlow.payGroup(s) == "credit",
      canReturn = look.canReturn(s),
      canCancel = s.status != "cancelled" && !s.isOnlineOrder(),
      returnDocs = if (returnsV2) look.returnDocs[s.id].orEmpty() else emptyList(),
      payState = payState,
      title = debtor ?: itemsTitle(items.map(look::itemName)),
      status = status(state, PayFlow.payGroup(s) == "credit", payLabel, payState),
    )
  }

  private fun payLabel(state: SaleState, s: Sale, fixed: Boolean): String = when (state) {
    SaleState.CANCELLED -> StrSale.cancelled
    SaleState.ONLINE -> StrSale.online
    SaleState.DONE -> ir.vil3ntec.tohid.data.PayFixFlow.kindLabel(ReceiptModel.payLabel(s), fixed)
  }

  /**
   *  «۱۴:۰۵ · ۳ قلم · #۱۲۴۵» — ساعت (یا روزِ کوتاه با [withDate])، شمارِ قلم فقط وقتی عنوان نامِ مشتری است (عنوانِ نقدی
   *  خودش نامِ کالاهاست)، و شمارهٔ فاکتور کوچک در پایان (برای جستجو و برگشت با شماره).
   */
  private fun subtitle(s: Sale, withDate: Boolean, today: String, items: Int?): String {
    val clock = SaleTime.clock(s.createdAt)
    val whenText = if (withDate && s.date.isNotBlank()) RowWhen.of(s.date, today, clock) else clock
    return listOfNotNull(whenText, items?.let { StrSale.itemsCount(plain(it)) }, "#" + ReceiptModel.number(s))
      .filter { it.isNotBlank() }.joinToString(" · ")
  }

  /** «برنج»، «برنج، روغن»، «برنج، روغن و ۲ قلمِ دیگر»؛ بی قلم ⇒ «نقدی» */
  fun itemsTitle(names: List<String>): String {
    val shown = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return when {
      shown.isEmpty() -> StrSale.cashPay
      shown.size <= 2 -> shown.joinToString(StrRows.namesSep)
      else -> StrRows.andMore(shown.take(2).joinToString(StrRows.namesSep), plain(shown.size - 2))
    }
  }

  /**
   *  وضعیتِ آرامِ ردیف: لغوشده و اینترنتی خاکستری؛ نسیه کهربایی (بدهی)؛ پرداختِ غیرنقدِ تأییدنشده (PAY-07) با شدّتِ همان
   *  قرصِ پیشین («ناموفق» قرمز)؛ غیرنقدِ عادی (کارت، کیف پول) آرام؛ نقدیِ عادی ⇒ null.
   */
  internal fun status(state: SaleState, credit: Boolean, payLabel: String, payState: String?): V3RowStatus? = when {
    state == SaleState.CANCELLED -> V3RowStatus(StrSale.cancelled)
    state == SaleState.ONLINE -> V3RowStatus(StrSale.online)
    payState != null -> V3RowStatus.of(payLabel + " · " + PayStatusText.name(payState), PayStatusText.pill(payState))
    credit -> V3RowStatus(payLabel, V3RowTone.ATTENTION)
    payLabel == StrSale.cashPay -> null
    else -> V3RowStatus(payLabel)
  }

  /**
   *  «اصلاحِ فاکتور»: اقلامِ برنگشته با همان قیمت و نشان‌های ردیف به سبد؛ قلمِ آزاد شناسهٔ تازه می‌گیرد. همان راهِ کامپیوتر
   *  (`SaleReopen`)؛ تخفیف، یادداشت و لیستِ فاکتور هم با `SaleReopen` به سبد و برگهٔ تسویه می‌روند (`FixRoute`).
   */
  fun reopenLines(d: ShopData, s: Sale, today: String = "", newId: () -> String): List<SalesEngine.CartLine> =
    ir.vil3ntec.tohid.data.SaleReopen.lines(d, s, newId, today, FREE_ITEM)

  /** فاکتورِ زنده‌ای که QRِ رسید یک‌جا پیدا کرد ⇒ همان لحظه فرمِ برگشت */
  fun scanned(d: ShopData, code: String, shopTag: String? = null): Sale? =
    ReturnFlow.find(d, code, shopTag = shopTag).singleOrNull()?.takeIf { it.status != "cancelled" }
}
