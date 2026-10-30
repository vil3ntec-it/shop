package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.Bitmap
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.receipt.DiscountLine
import ir.vil3ntec.tohid.core.receipt.LineView
import ir.vil3ntec.tohid.core.receipt.PaymentLine
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptFormat
import ir.vil3ntec.tohid.core.receipt.ReceiptInput
import ir.vil3ntec.tohid.core.receipt.ReceiptLabels
import ir.vil3ntec.tohid.core.receipt.ReceiptLayout
import ir.vil3ntec.tohid.core.receipt.ReceiptPdf
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import ir.vil3ntec.tohid.core.receipt.ReceiptTokens
import ir.vil3ntec.tohid.core.receipt.ShopHeader
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ReceiptFlag
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ReceiptProfileStore
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReceipt
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 *  فاکتورِ نسخهٔ ۲ — رابطِ یگانه برای چاپ، پیش‌نمایش، PNG/PDF و صفحهٔ فاکتورِ V3 (G5).
 *
 *    [doc]       بلوک‌ها برای صفحهٔ فاکتور (Compose همان‌ها را با توکن‌های V3 می‌کشد)
 *    [mono]      تصویرِ یک‌بیتیِ کاغذ — `EscPos.document` همین را می‌برد
 *    [bitmap]    پیش‌نمایش و PNG ِ «فرستادن»
 *    [pdf]       PDF ِ ۸۰ میلی‌متری برای «فرستادن»
 *    [publicUrl] نشانیِ QR (کپیِ پیوند)؛ null ⇒ فروشِ پیش از نسخهٔ ۲ یا QR ِ خاموش
 *
 *  هیچ مبلغی این‌جا حساب نمی‌شود: هر عدد همان فیلدِ ذخیره‌شدهٔ فروش است. سربرگ و لوگو
 *  فقط از `ReceiptProfileStore` (نسخهٔ نگه‌داشته)، پس چاپ بی اینترنت هم کامل است.
 */
object Receipts {

  /** پهنای PNG و PDF ِ «فرستادن» — همان کاغذِ ۸۰ میلی‌متری */
  const val SHARE_DOTS = PrinterProfile.WIDTH_80MM

  /** توکنِ QR برای فروشِ تازهٔ صندوق — فقط با پرچمِ `shop.receipt_v2`؛ خاموش ⇒ null (همان فروشِ پیشین) */
  fun newToken(context: Context): String? = if (ReceiptFlag.enabled(context)) ReceiptTokens.new() else null

  fun publicUrl(context: Context, sale: Sale): String? {
    val token = sale.receiptToken ?: return null
    if (!ReceiptFlag.enabled(context) || !ReceiptProfileStore.current(context).qr) return null
    return ReceiptTokens.url(AppConfig.baseUrl(context), LedgerOwner.currentShop(context), token)
  }

  /** نامِ دکان وقتی سربرگ نامی ندارد — همان «نام فروشگاه» ِ روی همین دستگاه (پیش از نسخهٔ ۲) */
  fun localName(context: Context): String =
    context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "").orEmpty()

  fun input(
    context: Context,
    d: ShopData,
    sale: Sale,
    widthDots: Int,
    copyMark: String? = null,
    fallbackName: String = localName(context),
    legacyQr: String? = null,
    labels: ReceiptLabels = ReceiptLabels.FA,
  ): ReceiptInput {
    val publicUrl = publicUrl(context, sale)
    val profile = ReceiptProfileStore.current(context)
    val saleItems = d.saleItems.filter { it.saleId == sale.id }
    val stamped = ir.vil3ntec.tohid.ui.PromoLive.stampOn(context)
    val items = saleItems.map { item ->
      LineView(
        name = SalesEngine.itemName(d, item),
        qty = item.quantity,
        unit = d.products.find { it.id == item.productId }?.unit.orEmpty(),
        unitPrice = item.unitPrice,
        total = item.totalPrice,
        returnedQty = item.returnedQty,
        //  پیگیریِ #307 (S3، پرچمِ `shop.promo_stamp`): قیمتِ فهرست فقط برای قلمی که تخفیفِ خودکار گرفته
        listPrice = SaleReceipt.line(item, stamped).takeIf { it.promo > 0 }?.unit,
      )
    }
    val time = if (sale.createdAt > 0) SimpleDateFormat("HH:mm", Locale.US).format(Date(sale.createdAt)) else ""
    return ReceiptInput(
      shop = ShopHeader(profile.name.ifBlank { fallbackName }, profile.address, profile.phone, profile.footer),
      invoiceNo = sale.invoiceLabel().orEmpty(),
      dateIso = sale.date,
      time = time,
      cashier = sale.seller,
      //  PAY (v3 A4): برچسب و ردیف‌های پرداخت همان `PayFlow` ِ تاریخچه و جزئیات
      kind = PayFlow.payLabel(sale, cash = labels.cash),
      cancelled = sale.status == "cancelled",
      items = items,
      subtotal = sale.total,
      discounts = listOf(DiscountLine(labels.discount, sale.discount)),
      //  «جمع اقلام» = جمعِ ستونِ قلم‌ها؛ تخفیفِ خودکار فقط آگاهی است و دوباره کم نمی‌شود
      included = listOfNotNull(
        SaleReceipt.included(sale, saleItems, stamped, ir.vil3ntec.tohid.ui.PromoLive::ruleName)?.let { DiscountLine(it.label, it.amount) },
      ),
      total = sale.finalTotal,
      //  ردیف‌های روش، «دریافتی» و «پولِ پس» — فقط فاکتوری که `payments` دارد؛ وگرنه «پرداختی»
      payments = PayFlow.receiptLines(sale).map { (label, amount) -> PaymentLine(label, amount) },
      paid = sale.paidAmount,
      remaining = sale.remaining,
      debtorName = sale.debtorId?.let { id -> d.debtors.find { it.id == id }?.name },
      //  فروشِ نسخهٔ ۲: نشانیِ عمومی؛ فروشِ کهنه با «برگشتِ تازه»: کدِ `TR1:` ِ اسکنرِ برگشت (بی متنِ «اسکن کنید»)
      qrUrl = publicUrl ?: legacyQr,
      qrCaption = publicUrl != null,
      logo = ReceiptProfileStore.logoMono(context, widthDots),
      copyMark = copyMark,
    )
  }

  fun doc(
    context: Context, d: ShopData, sale: Sale, widthDots: Int,
    copyMark: String? = null, fallbackName: String = localName(context), legacyQr: String? = null,
  ): ReceiptDoc = ReceiptLayout.build(input(context, d, sale, widthDots, copyMark, fallbackName, legacyQr), widthDots, format(ir.vil3ntec.tohid.ui.PromoLive.stampOn(context)))

  fun mono(
    context: Context, d: ShopData, sale: Sale, widthDots: Int,
    copyMark: String? = null, fallbackName: String = localName(context), legacyQr: String? = null,
  ): MonoImage = ReceiptRaster.compose(doc(context, d, sale, widthDots, copyMark, fallbackName, legacyQr), ReceiptPainter(context))

  fun bitmap(
    context: Context, d: ShopData, sale: Sale, widthDots: Int = SHARE_DOTS,
    copyMark: String? = null, fallbackName: String = localName(context), legacyQr: String? = null,
  ): Bitmap = ThermalPrinter.toBitmap(mono(context, d, sale, widthDots, copyMark, fallbackName, legacyQr))

  fun pdf(context: Context, d: ShopData, sale: Sale, legacyQr: String? = null): ByteArray =
    ReceiptPdf.single(mono(context, d, sale, SHARE_DOTS, legacyQr = legacyQr))

  /** رقم و نمادِ همین دستگاه (LOC-05، LOC-07) — همان `LocaleText` ِ همه‌جای برنامه */
  /** @param exactUnit پرچمِ `shop.promo_stamp`: قیمتِ واحدِ اعشاری با دو رقم؛ خاموش ⇒ همان پیشین */
  fun format(exactUnit: Boolean = false): ReceiptFormat {
    val digits = LocaleChoice.digits
    val symbol = LocaleChoice.symbol
    val lunar = LocaleChoice.lunar
    val lunarAdjust = LocaleChoice.lunarAdjust
    return object : ReceiptFormat {
      override fun money(afn: Double) = LocaleText.afghani(afn, digits, symbol)
      //  قیمتِ واحدِ اعشاری (قلمِ تخفیفِ خودکار) با دو رقم؛ عددِ کامل همان `LocaleText.amount`
      override fun amount(afn: Double) = if (exactUnit) LocaleText.unitAmount(afn, digits) else LocaleText.amount(afn, digits)
      override fun qty(q: Double) = LocaleText.digitsIn(ir.vil3ntec.tohid.qty(q), digits)
      override fun digits(text: String) = LocaleText.digitsIn(text, digits)
      //  شماره جدا و چپ‌به‌راست (LRE…PDF) تا در سطرِ راست‌به‌چپ رقم‌ها جابه‌جا نشوند
      override fun phone(text: String) = "\u202A" + LocaleText.digitsIn(text, digits) + "\u202C"
      override fun date(iso: String) = LocaleText.date(iso, digits)
      //  LOC-01: فقط با تنظیمِ «تاریخِ قمری» (پیش‌فرض خاموش)
      override fun lunarDate(iso: String) = if (lunar) LocaleText.lunar(iso, digits, lunarAdjust) else null
    }
  }
}
