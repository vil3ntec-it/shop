package ir.vil3ntec.tohid.print

import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.core.receipt.ReceiptShare
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
   *  چاپگر ندارد (گزارشِ ممیزی: «راهی برای فرستادنِ فاکتور نیست»). متنِ همراه
   *  (`ReceiptShare.caption`) پیوندِ فاکتورِ آنلاین را هم دارد اگر فروش داشته باشد (POS-32).
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

  /** همان تصویرِ [shareSale]، به PDF ِ ۸۰ میلی‌متری (`ReceiptPdf`) — با پرچمِ خاموش همان فاکتورِ پیشین */
  suspend fun sharePdf(context: Context, d: ShopData, sale: Sale): String? = share(context, sale, "pdf", "application/pdf") { file ->
    val bitmap = Receipt.render(context, d, sale, Receipts.localName(context), Receipts.SHARE_DOTS, qr = receiptQr(context, sale))
    val mono = try { ThermalPrinter.toMono(bitmap, Receipts.SHARE_DOTS) } finally { bitmap.recycle() }
    file.writeBytes(ir.vil3ntec.tohid.core.receipt.ReceiptPdf.single(mono))
  }

  private suspend fun share(
    context: Context,
    sale: Sale,
    ext: String,
    mime: String,
    write: (java.io.File) -> Unit,
  ): String? {
    //  پرچمِ خاموش ⇒ همان نامِ محلیِ پیشین در متنِ اشتراک
    val local = Receipts.localName(context)
    val storeName = if (ir.vil3ntec.tohid.data.ReceiptFlag.enabled(context)) ReceiptProfileStore.current(context).name.ifBlank { local } else local
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
        //  POS-32: پیوندِ فاکتورِ آنلاین در خطِ آخر — فقط فروشِ نسخهٔ ۲ با توکن، پرچمِ `shop.receipt_v2` و QR ِ روشن
        //  (`Receipts.publicUrl`)؛ وگرنه همان یک خطِ امروز
        putExtra(android.content.Intent.EXTRA_TEXT, ReceiptShare.caption(sale.invoiceLabel(), storeName, Receipts.publicUrl(context, sale)))
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

  /**
   *  POS-34 — چاپِ رسیدِ هدیهٔ بی قیمت (`Receipts.giftBitmap`)؛ برمی‌گرداند: پیامِ خطا، یا null.
   *  کاغذِ جداست، نه نسخهٔ دیگرِ فاکتور: نه در `PrintLog`/«چاپِ دوباره» و نه در صفِ چاپِ فاکتور.
   */
  suspend fun printGift(context: Context, d: ShopData, sale: Sale, address: String, width: Int): String? = withContext(Dispatchers.IO) {
    val storeName = context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") ?: ""
    val bitmap = runCatching { Receipts.giftBitmap(context, d, sale, width, fallbackName = storeName.ifBlank { Receipts.localName(context) }) }
      .getOrElse { return@withContext ir.vil3ntec.tohid.core.i18n.StrGift.failed(it.message ?: "—") }
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
