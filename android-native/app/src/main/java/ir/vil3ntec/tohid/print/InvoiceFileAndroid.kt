package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  POS-32، کامپیوتر — فاکتور به فایل. گوشی همان رسید را با برگهٔ اشتراکِ سیستم می‌فرستد (`PrintJob.shareSale`/`sharePdf`)؛
 *  کامپیوتر برگهٔ اشتراک ندارد و پیش‌تر فقط «کپیِ پیوند» داشت. این‌جا همان رسید — تصویرِ ۸۰ میلی‌متریِ یک‌بیتی (`MonoPng`)
 *  یا PDF ِ آن (`ReceiptPdf`) — در پوشهٔ انتخابیِ کاربر ذخیره می‌شود تا از واتس‌اپ/تلگرامِ وب فرستاده شود.
 *  فایلِ مرزیِ پلتفرم (`Context`)؛ آزمون: `InvoiceSaveDeskTest`.
 */
object InvoiceFile {

  /** بایت‌های فایل؛ هیچ چیز روی دیسک نمی‌نویسد. همان فاکتور ⇒ همان بایت‌ها */
  fun bytes(context: Context, d: ShopData, sale: Sale, pdf: Boolean): ByteArray {
    val bitmap = Receipt.render(context, d, sale, Receipts.localName(context), Receipts.SHARE_DOTS, qr = PrintJob.receiptQr(context, sale))
    val mono = try { ThermalPrinter.toMono(bitmap, Receipts.SHARE_DOTS) } finally { bitmap.recycle() }
    return if (pdf) ir.vil3ntec.tohid.core.receipt.ReceiptPdf.single(mono) else ir.vil3ntec.tohid.core.hw.MonoPng.encode(mono)
  }

  /** همان نامِ فایلِ اشتراکِ گوشی */
  fun name(sale: Sale, pdf: Boolean): String = "invoice-${sale.invoiceLabel() ?: "x"}.${if (pdf) "pdf" else "png"}"

  /** ذخیره با پنجرهٔ «ذخیره در…» ِ کامپیوتر (`Platform.saveFile`)؛ @return پیامِ نتیجه برای کاربر */
  suspend fun save(context: Context, d: ShopData, sale: Sale, pdf: Boolean): String {
    val data = withContext(Dispatchers.Default) { runCatching { bytes(context, d, sale, pdf) }.getOrNull() }
      ?: return StrSale.invoiceNotMade
    return ir.vil3ntec.tohid.ui.Platform.saveFile(name(sale, pdf), data)?.let(StrSale::savedTo) ?: StrSale.notSaved
  }
}
