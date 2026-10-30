package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale

/**
 *  عددها و متن‌های فاکتورِ صفحه (`InvoiceDialog`)، چاپِ پیشین (`Receipt`) و نسخهٔ ۲
 *  (`Receipts.input`) برای تخفیفِ خودکار — S3، پشتِ پرچمِ `shop.promo_stamp`.
 *
 *  ⛔ **پرچمِ خاموش (`stamped = false`) ⇒ دقیقاً متن و عددِ پیشین.**
 *
 *  روشن:
 *   • ستونِ قلم‌ها همان مبلغِ پرداختیِ هر قلم است (`totalPrice`) و «جمع اقلام» همان
 *     `sale.total` — یعنی جمعِ همین ستون. هیچ مبلغی دو بار کم نمی‌شود.
 *   • قلمی که تخفیفِ خودکار گرفته «مقدار × قیمتِ فهرست − تخفیفش» را نشان می‌دهد
 *     (۳ × 350 − 50 = 1,000)؛ قلمِ دیگر قیمتِ ثبت‌شده را، با دو رقمِ اعشار اگر دارد.
 *   • تخفیفِ خودکارِ فاکتور فقط یک ردیفِ **آگاهی** زیرِ «جمع اقلام» است: «شاملِ تخفیفِ
 *     خودکار (نام‌ها)» — نامِ هر قاعده همان که هنگامِ فروش روی قلم نشست (`SalePromo.name`).
 */
object SaleReceipt {

  /**
   *  @param unit قیمتِ واحدی که کنارِ مقدار می‌نشیند — فهرست اگر تخفیفِ خودکار گرفته
   *  @param promo تخفیفِ خودکارِ همین قلم به افغانی؛ ۰ = ندارد
   *  @param total مبلغِ پرداختیِ قلم (`totalPrice`) — ستونِ قلم‌ها و پایهٔ «جمع اقلام»
   */
  data class Line(val unit: Double, val promo: Double, val total: Double)

  fun line(item: SaleItem, stamped: Boolean): Line {
    val promoMinor = item.promo.sumOf { it.discountMinor }
    val list = item.listPriceMinor
    return if (stamped && promoMinor > 0 && list != null) Line(list / 100.0, promoMinor / 100.0, item.totalPrice)
    else Line(item.unitPrice, 0.0, item.totalPrice)
  }

  /**
   *  متنِ زیرِ نامِ قلم. خاموش ⇒ همان «مقدار × قیمت» ِ پیشین با همان [amount]؛ روشن ⇒
   *  مقدار با [qty]، قیمت با [unitAmount] (اعشارِ واقعی) و سهمِ تخفیفِ خودکار.
   */
  fun detail(
    item: SaleItem,
    unit: String,
    stamped: Boolean,
    amount: (Double) -> String,
    unitAmount: (Double) -> String,
    qty: (Double) -> String,
  ): String {
    val u = if (unit.isNotBlank()) " $unit" else ""
    if (!stamped) return "${amount(item.quantity)}$u × ${amount(item.unitPrice)}"
    val l = line(item, true)
    val off = if (l.promo > 0) " " + StrSale.lineAutoDiscount(amount(l.promo)) else ""
    return "${qty(item.quantity)}$u × ${unitAmount(l.unit)}$off"
  }

  /** ردیفِ آگاهیِ زیرِ «جمع اقلام» — از آن کم نمی‌شود */
  data class Included(val label: String, val amount: Double)

  /**
   *  «شاملِ تخفیفِ خودکار (نام‌ها)»؛ `null` ⇒ این ردیف نیست (پرچمِ خاموش، فاکتورِ بی
   *  `promoDiscountMinor` یا صفر). نام از خودِ قلم؛ قلمِ پیش از ثبتِ نام از [nameOf].
   */
  fun included(sale: Sale, items: List<SaleItem>, stamped: Boolean, nameOf: (String) -> String? = { null }): Included? {
    if (!stamped) return null
    val minor = sale.promoDiscountMinor ?: return null
    if (minor <= 0) return null
    val names = items.flatMap { it.promo }.filter { it.id.isNotEmpty() }
      .mapNotNull { p -> p.name.trim().ifEmpty { nameOf(p.id)?.trim().orEmpty() }.ifEmpty { null } }
      .distinct()
    return Included(StrSale.includesAutoDiscount(names(names)), minor / 100.0)
  }

  /** «تخفیفِ خودکار (ده درصد، کدِ عید)» — بی نام ⇒ فقط «تخفیفِ خودکار» (پنجرهٔ تسویه) */
  fun promoLabel(names: List<String>): String = StrPos.autoDiscount + names(names)

  private fun names(names: List<String>): String = if (names.isEmpty()) "" else " (${names.joinToString("، ")})"
}
