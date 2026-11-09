package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.CustomerPrice
import kotlin.math.roundToLong

/**
 *  «اصلاحِ فاکتور» (لغو با سندِ معکوس و بردنِ اقلام به سبد) — یک راهِ مشترک برای گوشی (`FixRoute` ِ نسخهٔ ۳) و کامپیوتر
 *  (`SalesHistoryScreen` ِ `DeskInvoices`)؛ خالص و آزمون‌پذیر (`ConvertFidelityTest`).
 *
 *  پیش‌تر فقط کالا، مقدار و قیمت به سبد می‌رفت و این‌ها بی‌صدا می‌افتادند:
 *    • تخفیفِ کلِ فاکتور — فاکتورِ ۱۰۰۰ با ۱۰٪ (۹۰۰) دوباره ۱۰۰۰ ثبت می‌شد؛
 *    • لیستِ عمده — ردیفِ برابر با عمدهٔ کالا در سبدِ خرده (`PriceListAtPos`) دوباره خرده قیمت می‌خورد؛
 *    • یادداشتِ ردیف و فاکتور، سریال (کالای سریال‌دار اصلاً ثبت نمی‌شد)، سری، فروشندهٔ ردیف و نشانِ «قیمتِ خاص».
 *  کدهای تخفیف و امتیاز دوباره زده نمی‌شوند: قیمتِ هر ردیف همان قیمتِ پس از آن‌هاست (دستی ⇒ تخفیفِ خودکار نمی‌گیرد).
 */
object SaleReopen {

  /** اقلامِ برنگشته با همان قیمت و نشان‌های ردیف؛ قلمِ آزاد شناسهٔ تازه می‌گیرد */
  fun lines(d: ShopData, s: Sale, newId: () -> String, today: String = "", freeLabel: String = "قلم آزاد"): List<SalesEngine.CartLine> =  // داده: نامِ قلمِ آزاد در فاکتورِ تازه ذخیره می‌شود
    d.saleItems.filter { it.saleId == s.id }.mapNotNull { item ->
      val left = item.quantity - item.returnedQty
      when {
        left <= 0 -> null
        item.productId.isBlank() -> SalesEngine.CartLine(
          productId = newId(), quantity = left, unitPrice = item.unitPrice, label = item.name.ifBlank { freeLabel },
          note = item.note, seller = item.seller,
        )
        else -> SalesEngine.CartLine(
          productId = item.productId, quantity = left, unitPrice = item.unitPrice, lotId = item.lotId, note = item.note,
          //  برگشت نمی‌گوید کدام سریال برگشت (`SerialSale`) ⇒ قلمِ برگشت‌خورده سریال را از نو می‌خواهد
          serials = item.serials?.takeIf { item.returnedQty <= 0.0 },
          specialPrice = if (item.priceSource == CustomerPrice.SPECIAL) CustomerPriceCart.stillSpecial(d, s.debtorId, item.productId, item.unitPrice, today) else null,
          seller = item.seller,
        )
      }
    }

  /**
   *  تخفیفِ کلِ فاکتور برای همان اقلامِ [lines]: درصد همان درصد؛ مبلغ به سهمِ اقلامِ برنگشته
   *  (`round(تخفیف × جمعِ تازه ÷ جمعِ فاکتور)` — همان سهمی که برگشت پس داده بود، `SalesEngine.paidRatio`). بی برگشت ⇒ همان مبلغ.
   *  فروشندهٔ فاکتور هم (`Sale.seller`): پیش‌تر فاکتورِ اصلاح‌شده به نامِ کسی ثبت می‌شد که اصلاحش کرد.
   */
  fun extras(d: ShopData, s: Sale, lines: List<SalesEngine.CartLine>): HeldExtras {
    val seller = s.seller.trim()
    if (!(s.discount > 0) || !(s.discountValue > 0)) return HeldExtras(seller = seller)
    if (s.discountType == "percent") return HeldExtras(discount = plain(s.discountValue), seller = seller)
    val now = SalesEngine.cartTotal(d, lines)
    val amount = if (s.total > 0 && now < s.total) (s.discount * now / s.total).roundToLong().toDouble() else s.discount
    return if (amount > 0) HeldExtras(discount = plain(amount), discountAmount = true, seller = seller) else HeldExtras(seller = seller)
  }

  /**
   *  «اصلاح و ثبت» ِ فاکتورِ ردشده (`ConflictInboxSheet`، `ConflictInbox.cartLines` با همان قیمت‌ها): تخفیفِ همان فاکتور به
   *  مبلغ و دریافتیِ نسیه‌اش ⇒ برگهٔ تسویه (`HeldSession`). پیش‌تر می‌افتادند و فاکتورِ دوباره ثبت‌شده گران‌تر از پولی بود که
   *  مشتری داده بود؛ صندوق‌دار عدد را می‌بیند و اگر سببِ رد همان بود عوضش می‌کند (سقفِ تخفیف همان سد را دارد).
   */
  fun extras(inv: ir.vil3ntec.tohid.core.sync.ConflictInbox.Invoice): HeldExtras {
    val off = inv.discountMinor?.takeIf { it > 0 }
    val credit = inv.paymentMethod == "credit"
    return HeldExtras(
      discount = off?.let { plain(PayFlow.major(it)) }.orEmpty(),
      payment = if (credit) SalesEngine.Payment.CREDIT else null,
      paid = inv.paidMinor?.takeIf { credit && it > 0 }?.let { plain(PayFlow.major(it)) }.orEmpty(),
      discountAmount = off != null,
      //  همان فروشندهٔ فاکتورِ ردشده، نه کسی که اصلاحش کرد
      seller = inv.seller.trim(),
    )
  }

  /** یادداشتِ فاکتور */
  fun note(s: Sale): String? = ir.vil3ntec.tohid.core.pos.SaleNotes.clean(s.note)

  /** عددِ کادر: «۱۰» نه «10.0» */
  private fun plain(v: Double): String = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
}
