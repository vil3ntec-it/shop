package ir.vil3ntec.tohid.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.LabelSheetPrint
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  مرزِ سکوی صفحهٔ «سفارش‌های خرید» (PUR-02/04/13، §۴۰.۳ و قاعدهٔ «کدِ تازه برای آیفون»): `Context` و ساعتِ
 *  دستگاه فقط در همین پوشهٔ `platform/`؛ صفحه (`ui/screens/PurchaseOrders.kt`) و منطق
 *  (`data/PurchaseOrderBook.kt`، `core/purchase`) فقط این رابط را می‌بینند. برنامهٔ آیفون پیاده‌سازیِ خودش را
 *  می‌دهد.
 */
interface PurchaseOrderHost {
  val repo: PurchaseOrderRepository

  /** زمانِ ثبتِ ردیف‌های دفتر (میلی‌ثانیه) */
  fun now(): Long

  /** چاپگرِ «برچسبِ کالا» تعریف شده؟ (PUR-13) */
  fun hasLabelPrinter(): Boolean

  /**
   *  برچسبِ یک کالا — همان برچسبِ «دستگاه‌ها» (نام، قیمتِ فروش، بارکد) — با [copies] نسخه روی چاپگرِ برچسب؛
   *  [a4] یا بی چاپگر ⇒ برگهٔ A4 ِ ۳×۸. پیامِ نتیجه برای نمایش (`null` = پنجرهٔ چاپِ سیستم باز شد).
   */
  suspend fun printLabel(product: Product, copies: Int, a4: Boolean): String?
}

/** پیاده‌سازیِ اندروید و کامپیوتر (همان `Context` ِ صفحه) */
@Composable
fun rememberPurchaseOrderHost(): PurchaseOrderHost {
  val context = LocalContext.current
  return remember(context) {
    object : PurchaseOrderHost {
      override val repo = PurchaseOrderRepository(Backend.api(context))
      override fun now(): Long = System.currentTimeMillis()
      override fun hasLabelPrinter(): Boolean = PrinterRegistry.labelPrinter(context) != null

      override suspend fun printLabel(product: Product, copies: Int, a4: Boolean): String? {
        val res = withContext(Dispatchers.IO) {
          HwPages.label(
            context, product.name,
            LocaleText.afghani(product.salePrice, LocaleChoice.digits, LocaleChoice.symbol),
            product.barcodes.firstOrNull().orEmpty(),
          )
        }
        val img = res.image ?: return "«${product.name}»: ${res.error ?: "برچسب ساخته نشد"}"
        val printer = PrinterRegistry.labelPrinter(context)
        if (a4 || printer == null) {
          val sheet = withContext(Dispatchers.Default) { Labels.A4.sheet(img, copies) }
          LabelSheetPrint.print(context, sheet, "برچسبِ ${product.name}")
          return null
        }
        return PrintSpooler.submitAndWait(
          context, PrintQueue.Kind.LABEL, printer.link, "برچسبِ ${product.name}",
          HwPages.labelBytes(printer, img, copies), done = "برچسبِ «${product.name}» چاپ شد",
        )
      }
    }
  }
}
