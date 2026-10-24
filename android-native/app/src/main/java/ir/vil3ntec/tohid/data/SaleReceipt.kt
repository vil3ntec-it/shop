package ir.vil3ntec.tohid.data

/**
 *  عددهای فاکتورِ صفحه (`InvoiceDialog`) و چاپ (`Receipt`) — S3.
 *
 *  هر عدد از خودِ ردیفِ ثبت‌شده می‌آید، نه از ضربِ دوبارهٔ قیمتِ گردشده: قلمی که
 *  تخفیفِ خودکار گرفته قیمتِ واحدِ اعشاری دارد (۱۰۰۰ ÷ ۳ = ۳۳۳٫۳۳…) و «۳ × ۳۳۳ =
 *  ۱٬۰۰۰» می‌شد. حالا چنین قلمی «مقدار × قیمتِ فهرست» و تخفیفش را نشان می‌دهد و
 *  جمعش همان `totalPrice` است؛ قلمِ بی آن قیمتِ ثبت‌شده را (با اعشار اگر دارد).
 *
 *  جمع‌ها مثلِ خلاصهٔ صندوق (`PromoSummary.rows`): «جمع اقلام» پیش از تخفیف‌های
 *  خودکار، یک ردیفِ «تخفیفِ خودکار» با نامِ قاعده‌ها، تخفیفِ دستی و مبلغِ نهایی.
 *  فاکتورِ بی `promoDiscountMinor` (خاموش، یا پیش از این نسخه) ⇒ همان ردیف‌های امروز.
 */
object SaleReceipt {

  /**
   *  @param unit قیمتِ واحدی که کنارِ مقدار می‌نشیند — فهرست اگر تخفیفِ خودکار گرفته
   *  @param promo تخفیفِ خودکارِ همین قلم به افغانی؛ ۰ = ندارد
   *  @param total جمعِ ثبت‌شدهٔ قلم (`totalPrice`)
   */
  data class Line(val unit: Double, val promo: Double, val total: Double)

  fun line(item: SaleItem): Line {
    val promoMinor = item.promo.sumOf { it.discountMinor }
    val list = item.listPriceMinor
    return if (promoMinor > 0 && list != null) Line(list / 100.0, promoMinor / 100.0, item.totalPrice)
    else Line(item.unitPrice, 0.0, item.totalPrice)
  }

  /**
   *  @param before «جمع اقلام» — پیش از تخفیف‌های خودکار
   *  @param promo تخفیفِ خودکارِ فاکتور (افغانی)؛ ۰ ⇒ ردیفش نیست
   *  @param names نامِ قاعده‌ها به ترتیبِ نخستین قلم؛ قاعدهٔ پاک‌شده نامی ندارد
   */
  data class Totals(val before: Double, val promo: Double, val names: List<String>)

  const val PROMO_LABEL = "تخفیفِ خودکار"

  fun totals(sale: Sale, items: List<SaleItem>, nameOf: (String) -> String?): Totals {
    val promo = (sale.promoDiscountMinor ?: 0L).coerceAtLeast(0) / 100.0
    if (promo <= 0) return Totals(sale.total, 0.0, emptyList())
    val names = items.flatMap { it.promo }.map { it.id }.filter { it.isNotEmpty() }.distinct()
      .mapNotNull { id -> nameOf(id)?.trim()?.takeIf { it.isNotEmpty() } }.distinct()
    return Totals(sale.total + promo, promo, names)
  }

  /** «تخفیفِ خودکار (ده درصد، کدِ عید)» — بی نام ⇒ فقط «تخفیفِ خودکار» */
  fun promoLabel(names: List<String>): String =
    if (names.isEmpty()) PROMO_LABEL else "$PROMO_LABEL (${names.joinToString("، ")})"
}
