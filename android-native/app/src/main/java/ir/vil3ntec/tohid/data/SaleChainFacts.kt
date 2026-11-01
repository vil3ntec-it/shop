package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.SaleFacts
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Qty

/**
 *  SEC-02 — فاکتورهای یک سری از دفترِ امروز ⇐ `SaleFacts` ِ زنجیرهٔ اسناد (`core/audit/DocChain`).
 *
 *  فقط بخش‌های تغییرناپذیرِ فاکتور و **بی دادهٔ شخصی**: شناسه، سری و شماره، زمانِ ثبت، شناسهٔ
 *  قرض‌دار (نه نام)، قلم‌ها (شناسهٔ کالا، مقدار، قیمت، مبلغ؛ نامِ قلمِ آزاد نه)، جمع، تخفیف، مبلغِ
 *  نهایی، پرداختِ همان لحظه و روش. مبلغ با `Money.fromLegacy` (پول) و مقدار با `Qty.fromLegacy`
 *  (هزارم) — همان یک قاعدهٔ گردکردنِ `V3Convert`. قلم‌ها به ترتیبِ شناسه (ULID، یعنی ترتیبِ ثبت)
 *  تا ترتیبِ دفتر پس از همگام‌سازی سند را عوض نکند. فاکتورِ بی سری یا بی شماره، یا با عددِ نامعتبر،
 *  کنار می‌ماند.
 */
object SaleChainFacts {

  private val AFN: Currency = Currency.AFN

  private fun minor(v: Double): Long = Money.fromLegacy(v, AFN).minor

  fun of(d: ShopData, series: String): List<SaleFacts> {
    if (series.isBlank()) return emptyList()
    val own = d.sales.filter { it.series == series && (it.invoiceNumber ?: 0) > 0 }
    if (own.isEmpty()) return emptyList()
    val ids = own.mapTo(HashSet()) { it.id }
    val items = d.saleItems.filter { it.saleId in ids }.groupBy { it.saleId }
    return own.mapNotNull { s -> runCatching { facts(s, items[s.id].orEmpty()) }.getOrNull() }
  }

  fun facts(s: Sale, items: List<SaleItem>): SaleFacts = SaleFacts(
    id = s.id,
    series = s.series.orEmpty(),
    no = (s.invoiceNumber ?: 0).toLong(),
    postedAt = s.createdAt,
    customerId = s.debtorId,
    currency = AFN.code,
    lines = items.sortedBy { it.id }.map {
      SaleFacts.Line(it.productId, Qty.fromLegacy(it.quantity).milli, minor(it.unitPrice), minor(it.totalPrice))
    },
    subtotal = minor(s.total),
    discount = minor(s.discount),
    total = minor(s.finalTotal),
    paid = minor(s.paidAmount),
    method = s.paymentMethod,
  )
}
