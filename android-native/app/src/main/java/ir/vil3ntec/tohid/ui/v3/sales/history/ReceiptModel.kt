package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.qty
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
  fun subtitle(s: Sale): String = listOf(formatDate(s.date), SaleTime.clock(s.createdAt), payLabel(s)).filter { it.isNotBlank() }.joinToString(" · ")

  fun lines(d: ShopData, s: Sale): List<V3InvoiceLine> {
    val products = d.products.associateBy { it.id }
    return d.saleItems.filter { it.saleId == s.id }.map { item ->
      val unit = products[item.productId]?.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
      V3InvoiceLine(
        name = SalesEngine.itemName(d, item),
        detail = "${qty(item.quantity)}$unit × ${PhoneSaleText.amount(item.unitPrice)}",
        total = PhoneSaleText.amount(item.totalPrice),
        note = item.returnedQty.takeIf { it > 0 }?.let { StrSale.returned(qty(it) + unit) },
      )
    }
  }

  fun totals(s: Sale): List<TotalRow> = buildList {
    add(TotalRow(StrSale.itemsTotal, s.total))
    if (s.discount > 0) add(TotalRow(StrSale.discountWord, -s.discount))
    add(TotalRow(StrSale.finalAmount, s.finalTotal, strong = true))
    add(TotalRow(StrSale.paid, s.paidAmount))
    add(TotalRow(StrSale.remaining, s.remaining, warn = s.remaining > 0))
    PayFlow.receiptLines(s).forEach { (label, amount) -> add(TotalRow(label, amount)) }
  }

  fun debtorName(d: ShopData, s: Sale): String? = s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }
}

/** ساعتِ روزِ یک لحظه («۸:۰۵») — برای فهرست و رسید */
object SaleTime {
  fun clock(at: Long): String {
    if (at <= 0) return ""
    val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
    return "${c.get(java.util.Calendar.HOUR_OF_DAY)}:${c.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')}"
  }
}
