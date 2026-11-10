package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.data.LoyaltyPoints
import ir.vil3ntec.tohid.core.i18n.StrRounding
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReceipt
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.unitMoney
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.v3.V3InvoiceLine

/** حالِ یک فاکتور — همیشه با برچسب، نه رنگ به‌تنهایی */
enum class SaleState { DONE, CANCELLED, ONLINE }

/** یک ردیفِ جمع‌های رسید */
data class TotalRow(val label: String, val amount: Double, val strong: Boolean = false, val warn: Boolean = false)

/**
 *  رسیدِ یک فاکتور (صفحهٔ `sales/{id}` و رسیدِ پس از ثبت) — خالص، از خودِ فروش و اقلامش:
 *  نامِ قلم (`SalesEngine.itemName`)، «۲ کیلوگرم × ۵۰ ؋»، مرجوعی، جمع‌ها و ردیف‌های پرداختِ
 *  ترکیبی (`PayFlow.receiptLines`)، روشِ پرداخت (`PayFlow.payLabel`). هیچ عددی ساخته نمی‌شود.
 */
object ReceiptModel {

  fun state(s: Sale): SaleState = when {
    s.status == "cancelled" -> SaleState.CANCELLED
    s.isOnlineOrder() -> SaleState.ONLINE
    else -> SaleState.DONE
  }

  fun stateLabel(s: Sale): String = when (state(s)) {
    SaleState.CANCELLED -> StrSale.cancelled
    SaleState.ONLINE -> StrSale.online
    SaleState.DONE -> StrSale.completed
  }

  fun number(s: Sale): String = s.invoiceLabel() ?: "0"

  fun title(s: Sale): String = StrSale.saleInvoiceNo(number(s))

  fun payLabel(s: Sale): String = PayFlow.payLabel(s, cash = StrSale.cashPay)

  /** «۱۴۰۵/۰۷/۲۸ · ۱۴:۰۵ · نقدی» */
  fun subtitle(s: Sale): String = listOf(displayDate(s.date), SaleTime.clock(s.createdAt), payLabel(s)).filter { it.isNotBlank() }.joinToString(" · ")

  /**
   *  `stamped` = پرچمِ `shop.promo_stamp` (#375): «مقدار × قیمتِ فهرست − تخفیفِ خودکار» از همان
   *  [SaleReceipt.detail]؛ خاموش ⇒ همان متنِ پیشین.
   */
  fun lines(d: ShopData, s: Sale, stamped: Boolean = false): List<V3InvoiceLine> {
    val products = d.products.associateBy { it.id }
    return d.saleItems.filter { it.saleId == s.id }.map { item ->
      val bare = products[item.productId]?.unit?.takeIf { it.isNotBlank() }.orEmpty()
      val unit = bare.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
      V3InvoiceLine(
        name = SalesEngine.itemName(d, item),
        detail = if (stamped) SaleReceipt.detail(item, bare, true, PhoneSaleText::amount, ::unitMoney, ::qty)
        else "${qty(item.quantity)}$unit × ${PhoneSaleText.amount(item.unitPrice)}",
        total = PhoneSaleText.amount(item.totalPrice),
        //  POS-18: «قیمتِ خاص» کنارِ «برگشت»؛ بی منبع ⇒ همان
        note = ir.vil3ntec.tohid.data.CustomerPriceCart.noteWith(item, item.returnedQty.takeIf { it > 0 }?.let { StrSale.returned(qty(it) + unit) }),
      )
    }
  }

  /**
   *  `stamped` (پرچمِ `shop.promo_stamp`): زیرِ «جمع اقلام» ردیفِ آگاهیِ «شاملِ تخفیفِ خودکار» —
   *  از جمع کم نمی‌شود ([SaleReceipt.included])؛ خاموش ⇒ همان ردیف‌ها.
   */
  fun totals(s: Sale, items: List<ir.vil3ntec.tohid.data.SaleItem> = emptyList(), stamped: Boolean = false, nameOf: (String) -> String? = { null }): List<TotalRow> = buildList {
    add(TotalRow(StrSale.itemsTotal, s.total))
    SaleReceipt.included(s, items, stamped, nameOf)?.let { add(TotalRow(it.label, it.amount)) }
    if (s.discount > 0) add(TotalRow(StrSale.discountWord, -s.discount))
    s.taxMinor?.takeIf { it > 0L }?.let { add(TotalRow(ir.vil3ntec.tohid.core.i18n.StrSaleTax.line, PayFlow.major(it))) } // POS-37
    PayFlow.rounding(s)?.let { add(TotalRow(StrRounding.line, it)) } // PAY-09: ردیفِ جدای گردکردنِ نقدی
    add(TotalRow(StrSale.finalAmount, s.finalTotal, strong = true))
    add(TotalRow(StrSale.paid, s.paidAmount))
    add(TotalRow(StrSale.remaining, s.remaining, warn = s.remaining > 0))
    PayFlow.receiptLines(s).forEach { (label, amount) -> add(TotalRow(label, amount)) }
  }

  /**
   *  زیرِ جمع‌ها، همان‌جا که نمای فاکتورِ کامپیوتر (`InvoiceDialog`) و رسیدِ چاپی (`Receipt`) دارند: امتیازِ مصرف‌شده و
   *  کسب‌شدهٔ همین فاکتور (PRM-13، `LoyaltyPoints.receiptLines`) و یادداشتِ فاکتور (POS-11). بی هیچ‌کدام ⇒ خالی.
   */
  fun extras(d: ShopData, s: Sale): List<String> = buildList {
    LoyaltyPoints.receiptLines(d, s.id).forEach { (label, amount) -> add(if (amount != null) "$label · ${PhoneSaleText.amount(amount)}" else label) }
    s.note?.takeIf { it.isNotBlank() }?.let { add("${StrSale.invoiceNote}: $it") }
  }

  fun debtorName(d: ShopData, s: Sale): String? = s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }
}

/** ساعتِ روزِ یک لحظه («۸:۰۵») — برای فهرست و رسید */
object SaleTime {
  /**
   *  فاصلهٔ منطقهٔ زمانیِ دستگاه از UTC (میلی‌ثانیه) در لحظهٔ داده‌شده — تنها درزِ پلتفرم
   *  ([deviceZoneOffset]؛ برنامهٔ آیفون همتای خودش را می‌گذارد). آزمون‌ها ثابتش می‌کنند.
   */
  var zoneOffset: (Long) -> Int = ::deviceZoneOffset

  /** حسابِ خالص: لحظه + فاصلهٔ منطقه ⇒ «ساعت:دقیقه» */
  fun clock(at: Long, offsetMs: Int = zoneOffset(at)): String {
    if (at <= 0) return ""
    val minutes = (at + offsetMs).floorDiv(60_000L).mod(1_440L).toInt()
    return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
  }

  /** روز (به زبان و تقویمِ برگزیده) و ساعت — صندوق و گزارش‌ها */
  fun stamp(at: Long): String = if (at <= 0) "—" else displayMillis(at) + " · " + clock(at)
}
