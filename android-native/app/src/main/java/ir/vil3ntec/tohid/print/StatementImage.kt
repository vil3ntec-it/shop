package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.hw.MonoPng
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import ir.vil3ntec.tohid.core.receipt.ShopHeader
import ir.vil3ntec.tohid.core.receipt.StatementInput
import ir.vil3ntec.tohid.core.receipt.StatementLayout
import ir.vil3ntec.tohid.data.CustomerStatement
import ir.vil3ntec.tohid.data.ReceiptProfileStore
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  صورت‌حسابِ مشتری به شکلِ تصویر — CRM-05. چیدمان `core/receipt/StatementLayout`، ردیف‌ها
 *  `CustomerStatement`، و کشیدن همان راهِ فاکتور: `ReceiptRaster` با قلمِ برنامه
 *  (`ReceiptPainter`) به پهنای کاغذِ ۸۰ میلی‌متری و PNG ِ یک‌بیتی (`MonoPng`) — پس گوشی و
 *  کامپیوتر یک تصویر می‌دهند.
 *
 *  گوشی: فرستادن (واتساپ، تلگرام…)؛ کامپیوتر: ذخیرهٔ فایل. فقط با «گزارش‌های مشتری» روشن دیده می‌شود.
 */
object StatementImage {

  fun input(context: Context, d: ShopData, debtorId: String, today: String): StatementInput? {
    val debtor = d.debtors.find { it.id == debtorId } ?: return null
    val profile = ReceiptProfileStore.current(context)
    return StatementInput(
      //  پانویسِ فاکتور («سپاس از خرید») این‌جا نمی‌آید؛ پانویسِ صورت‌حساب از برچسب‌هاست
      shop = ShopHeader(profile.name.ifBlank { Receipts.localName(context) }, profile.address, profile.phone, footer = ""),
      customer = debtor.name,
      phone = debtor.phone,
      todayIso = today,
      lines = CustomerStatement.lines(d, debtorId),
    )
  }

  fun png(context: Context, d: ShopData, debtorId: String, today: String): ByteArray? {
    val input = input(context, d, debtorId, today) ?: return null
    val doc = StatementLayout.build(input, Receipts.SHARE_DOTS, Receipts.format())
    return MonoPng.encode(ReceiptRaster.compose(doc, ReceiptPainter(context)))
  }

  /**
   *  گوشی: پنجرهٔ «فرستادن» با فایلِ PNG؛ کامپیوتر: ذخیره در پوشهٔ انتخابی.
   *  @return پیامِ نتیجه برای کاربر (موفق یا خطا)
   */
  suspend fun share(context: Context, d: ShopData, debtorId: String, today: String): String {
    val name = CustomerStatement.fileName(d, debtorId, today)
    val bytes = withContext(Dispatchers.Default) { runCatching { png(context, d, debtorId, today) }.getOrNull() }
      ?: return "تصویرِ صورت‌حساب ساخته نشد"
    if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
      return ir.vil3ntec.tohid.ui.Platform.saveFile(name, bytes)?.let { "ذخیره شد: $it" } ?: "ذخیره نشد"
    }
    val file = withContext(Dispatchers.IO) {
      runCatching {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        java.io.File(dir, name).also { it.writeBytes(bytes) }
      }.getOrNull()
    } ?: return "تصویرِ صورت‌حساب ساخته نشد"
    return runCatching {
      val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, name.removeSuffix(".png"))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(
        android.content.Intent.createChooser(send, "فرستادنِ صورت‌حساب").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
      )
      "صورت‌حساب آمادهٔ فرستادن است"
    }.getOrElse { "برنامه‌ای برای فرستادن پیدا نشد" }
  }
}
