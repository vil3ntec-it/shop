package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Quotes
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SavedQuote
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.qty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  POS-25 — پیش‌فاکتور به PDF. همان برگهٔ چاپی ([lines]: شماره، تاریخ، «اعتبار تا»، مشتری، ردیف‌ها با قیمتِ ثابت، جمع،
 *  یادداشت و «این برگه پیش‌فاکتور است، نه فاکتورِ فروش») با قلمِ برنامه روی تصویرِ یک‌بیتیِ ۸۰ میلی‌متری (`HwPages.text`)،
 *  سپس `ReceiptPdf` ِ هسته — بی `PdfDocument` ِ اندروید، پس کامپیوتر و آیفون همان بایت‌ها را می‌سازند.
 *
 *  فرستادن به‌ازای هر سکو:
 *  - گوشی ⇐ برگهٔ اشتراکِ سیستم (`ACTION_SEND`، `application/pdf`): واتس‌اپ، تلگرام، ایمیل…؛
 *  - آیفون ⇐ همان کد روی شیمِ `ACTION_SEND` ⇐ UIActivityViewController («ذخیره در Files»، AirPrint، پیام‌رسان‌ها)؛
 *  - کامپیوتر (برگهٔ اشتراک ندارد) ⇐ «ذخیرهٔ PDF» با پنجرهٔ «ذخیره در…» (`Platform.saveFile`) برای پیوستِ دستی.
 *
 *  ⛔ سندِ مالی نمی‌سازد: نه دفترِ دکان، نه همگام‌سازی؛ فقط پروندهٔ موقتِ اشتراک (`cache/share`) یا پروندهٔ انتخابیِ کاربر.
 *  آزمون: `QuotePdfDeskTest`.
 */
object QuoteFile {

  /**
   *  سطرهای برگه — چاپ (`printQuote`) و PDF همین را می‌کشند، پس هر دو یک متن‌اند. نوشته‌ها به زبانِ برنامه (`StrPos`) و
   *  جهتش (`HwPages.text`)؛ رقمِ کاغذ همان انتخابِ دکان، انگلیسی همیشه لاتین (#348، همان `Receipts.format`) — فقط عددها:
   *  نامِ کالا، مشتری و یادداشت (داده) همان‌طور که نوشته شده‌اند. رقمِ لاتین ⇒ همان برگهٔ پیشین.
   */
  fun lines(q: SavedQuote, d: ShopData): List<HwPages.Line> {
    val dg = LocaleChoice.screenDigits
    fun num(text: String) = LocaleText.digitsIn(text, dg)
    fun amount(afn: Double) = LocaleText.amount(afn, dg)
    val rows = q.lines.map { l ->
      val name = l.label.ifBlank { d.products.find { it.id == l.productId }?.name.orEmpty() }
      val price = l.unitPrice ?: 0.0
      HwPages.reportLine(StrPos.quoteSlipLine(name, num(qty(l.quantity)), amount(price), amount(SalesEngine.lineTotal(price, l.quantity))))
    }
    return listOf(HwPages.Line(StrPos.quoteSlipTitle(num(q.number.toString())), HwPages.Style.TITLE)) +
      HwPages.reportLine(StrPos.quoteSlipDate(num(displayDate(q.createdDay)))) +
      HwPages.reportLine(StrPos.quoteValidTo(num(displayDate(q.validUntil))), strong = true) +
      HwPages.reportLine(StrPos.quoteSlipCustomer(Quotes.customer(d, q))) + rows +
      HwPages.reportLine(StrPos.quoteSlipTotal(amount(PayFlow.major(q.totalMinor))), strong = true) +
      listOfNotNull(q.note?.let { HwPages.reportLine(it) }) +
      HwPages.Line(StrPos.quoteSlipFooter, HwPages.Style.SMALL)
  }

  /** بایت‌های PDF؛ هیچ چیز روی دیسک نمی‌نویسد. همان پیش‌فاکتور ⇒ همان بایت‌ها (بی ساعت و تصادف) */
  fun bytes(context: Context, d: ShopData, q: SavedQuote): ByteArray =
    ir.vil3ntec.tohid.core.receipt.ReceiptPdf.single(HwPages.text(context, Receipts.SHARE_DOTS, lines(q, d)))

  fun name(q: SavedQuote): String = "quote-${q.number}.pdf"

  /** گوشی و آیفون: برگهٔ اشتراکِ سیستم با پروندهٔ PDF و متنِ کوتاهِ همراه؛ @return پیامِ خطا یا null */
  suspend fun share(context: Context, d: ShopData, q: SavedQuote): String? {
    val file = withContext(Dispatchers.IO) {
      runCatching {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        java.io.File(dir, name(q)).also { it.writeBytes(bytes(context, d, q)) }
      }.getOrNull()
    } ?: return StrPos.quotePdfNotMade
    return runCatching {
      val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      val title = StrPos.quoteSlipTitle(q.number)
      val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, title)
        putExtra(android.content.Intent.EXTRA_TEXT, title + " — " + StrPos.quoteValidTo(displayDate(q.validUntil)))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(
        android.content.Intent.createChooser(send, StrPos.quoteShare).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      null
    }.getOrElse { StrPos.quoteNoShareApp }
  }

  /** کامپیوتر: «ذخیره در…» (`Platform.saveFile`)؛ @return پیامِ نتیجه برای کاربر (انصراف ⇒ «ذخیره نشد») */
  suspend fun save(context: Context, d: ShopData, q: SavedQuote): String {
    val data = withContext(Dispatchers.Default) { runCatching { bytes(context, d, q) }.getOrNull() }
      ?: return StrPos.quotePdfNotMade
    return ir.vil3ntec.tohid.ui.Platform.saveFile(name(q), data)?.let(StrSale::savedTo) ?: StrSale.notSaved
  }
}
