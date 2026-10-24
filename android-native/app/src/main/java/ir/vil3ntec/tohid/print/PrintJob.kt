package ir.vil3ntec.tohid.print

import ir.vil3ntec.tohid.data.invoiceLabel
import android.content.Context
import ir.vil3ntec.tohid.data.ReceiptProfileStore
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  چاپِ یک فاکتور: کشیدنِ تصویر و فرستادنش به چاپگر.
 *
 *  هر دو کار سنگین‌اند (یکی کشیدنِ چند هزار پیکسل، یکی حرف‌زدن با
 *  بلوتوث)، پس هیچ‌کدام روی نخِ صفحه انجام نمی‌شود — وگرنه برنامه هنگامِ
 *  چاپ لحظه‌ای می‌خشکد.
 */
object PrintJob {

  /**
   *  فرستادنِ فاکتور به مشتری — همان تصویرِ چاپِ حرارتی، پهنِ ۵۷۶ نقطه، به
   *  واتساپ یا تلگرام یا هر برنامه‌ای که کاربر بخواهد. برای دکان‌داری که
   *  چاپگر ندارد (گزارشِ ممیزی: «راهی برای فرستادنِ فاکتور نیست»).
   *
   *  @return پیامِ خطا یا null
   */
  suspend fun shareSale(context: Context, d: ShopData, sale: Sale): String? = share(context, sale, "png", "image/png") { file ->
    val bitmap = Receipt.render(context, d, sale, Receipts.localName(context), Receipts.SHARE_DOTS, qr = receiptQr(context, sale))
    try {
      file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
      bitmap.recycle()
    }
  }

  /** همان فاکتور به PDF ِ ۸۰ میلی‌متری (فاکتورِ نسخهٔ ۲) */
  suspend fun sharePdf(context: Context, d: ShopData, sale: Sale): String? = share(context, sale, "pdf", "application/pdf") { file ->
    file.writeBytes(Receipts.pdf(context, d, sale, legacyQr = receiptQr(context, sale)))
  }

  private suspend fun share(
    context: Context,
    sale: Sale,
    ext: String,
    mime: String,
    write: (java.io.File) -> Unit,
  ): String? {
    val storeName = ReceiptProfileStore.current(context).name.ifBlank { Receipts.localName(context) }
    val file = withContext(Dispatchers.IO) {
      runCatching {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        java.io.File(dir, "invoice-${sale.invoiceLabel() ?: "x"}.$ext").also(write)
      }
    }.getOrElse { return "فاکتور ساخته نشد: ${it.message ?: "دلیل نامعلوم"}" }
    return runCatching {
      val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mime
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, "فاکتور #${sale.invoiceLabel() ?: ""}${if (storeName.isNotBlank()) " — $storeName" else ""}")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(
        android.content.Intent.createChooser(send, "فرستادنِ فاکتور").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      null
    }.getOrElse { "برنامه‌ای برای فرستادن پیدا نشد" }
  }

  /** برمی‌گرداند: پیامِ خطا، یا null اگر چاپ انجام شد */
  suspend fun printSale(
    context: Context,
    d: ShopData,
    sale: Sale,
    address: String,
    width: Int,
  ): String? = withContext(Dispatchers.IO) {
    val storeName = context
      .getSharedPreferences("tohid", Context.MODE_PRIVATE)
      .getString("store_name", "") ?: ""

    val bitmap = runCatching { Receipt.render(context, d, sale, storeName, width, qr = receiptQr(context, sale)) }
      .getOrElse { return@withContext "فاکتور کشیده نشد: ${it.message ?: "دلیل نامعلوم"}" }

    try {
      ThermalPrinter.print(context, address, bitmap, width)
    } finally {
      bitmap.recycle()
    }
  }

  /** RET-01 — QRِ رسید فقط وقتی «برگشت و تعویضِ تازه» روشن است؛ خاموش ⇒ همان رسیدِ امروز */
  private fun receiptQr(context: Context, sale: Sale): String? =
    if (ir.vil3ntec.tohid.data.ReturnSettings.enabled(context) && sale.status != "cancelled") {
      ir.vil3ntec.tohid.data.ReturnFlow.receiptCode(sale)
    } else null

  /** RET-12 — چاپِ رسیدِ برگشت؛ برمی‌گرداند: پیامِ خطا، یا null */
  suspend fun printReturn(
    context: Context,
    d: ShopData,
    docId: String,
    address: String,
    width: Int,
  ): String? = withContext(Dispatchers.IO) {
    val storeName = context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") ?: ""
    val bitmap = runCatching { Receipt.renderReturn(context, d, docId, storeName, width) }
      .getOrElse { return@withContext "رسیدِ برگشت کشیده نشد: ${it.message ?: "دلیل نامعلوم"}" }
    try {
      ThermalPrinter.print(context, address, bitmap, width)
    } finally {
      bitmap.recycle()
    }
  }

  /** فرستادنِ رسیدِ برگشت به مشتری — همان راهِ `shareSale` */
  suspend fun shareReturn(context: Context, d: ShopData, docId: String): String? {
    val storeName = context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") ?: ""
    val label = ir.vil3ntec.tohid.data.ReturnFlow.label(docId)
    val file = withContext(Dispatchers.IO) {
      runCatching {
        val bitmap = Receipt.renderReturn(context, d, docId, storeName, 576)
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = java.io.File(dir, "return-$label.png")
        f.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        f
      }
    }.getOrElse { return "رسیدِ برگشت ساخته نشد: ${it.message ?: "دلیل نامعلوم"}" }
    return runCatching {
      val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, "رسیدِ برگشت $label${if (storeName.isNotBlank()) " — $storeName" else ""}")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(
        android.content.Intent.createChooser(send, "فرستادنِ رسیدِ برگشت").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      null
    }.getOrElse { "برنامه‌ای برای فرستادن پیدا نشد" }
  }
}
