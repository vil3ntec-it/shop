package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.product.LabelPlan
import ir.vil3ntec.tohid.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  چاپِ برچسبِ کالا و قفسه (PRD-23، S24) — مرزِ پلتفرم: قلمِ برنامه، چاپگرِ برچسبِ تعریف‌شده و پنجرهٔ چاپِ A4.
 *
 *  • برچسبِ بارکد همان `HwPages.label` ِ «دستگاه‌ها» است (۴۰×۳۰: نامِ دری، قیمت، بارکد و خودِ کد).
 *  • برچسبِ قفسه هم ۴۰×۳۰ است تا همان رول کار کند: نام، قیمتِ درشت، «هر واحد»، و بارکدِ کوتاه اگر با خطِ
 *    ۰٫۲۵ میلی‌متر جا شود (`Labels.moduleDots`)؛ بی بارکد هم چاپ می‌شود.
 *  • چاپگرِ برچسب: یک کار در صف (`PrintSpooler`) با یک بلوکِ TSPL/ESC-POS برای هر کالا و `PRINT n`.
 *  • A4: `Labels.A4.sheets` (۳×۸، هر ۲۴ برچسب یک برگه) و پنجرهٔ چاپِ سیستم (`LabelSheetPrint`).
 *  ⚠️ روی چاپگر و بارکدخوانِ واقعی آزموده نشده (S24).
 */
object ProductLabelPrint {

  fun hasLabelPrinter(context: Context): Boolean = PrinterRegistry.labelPrinter(context) != null

  private fun price(p: Product): String = LocaleText.afghani(p.salePrice, LocaleChoice.digits, LocaleChoice.symbol)

  private fun code(p: Product): String = ir.vil3ntec.tohid.normalizeDigits(p.barcodes.firstOrNull { it.isNotBlank() }.orEmpty().trim())

  /** تصویرِ یک برچسب؛ `error` دلیلِ ساخته‌نشدن */
  fun image(context: Context, p: Product, kind: LabelPlan.Kind): HwPages.LabelResult = when (kind) {
    LabelPlan.Kind.BARCODE -> HwPages.label(context, p.name, price(p), code(p))
    LabelPlan.Kind.SHELF -> shelf(context, p)
  }

  private fun shelf(context: Context, p: Product): HwPages.LabelResult {
    val size = Labels.SIZE_40x30
    val w = size.widthDots
    val h = size.heightDots
    val name = if (p.name.length > 38) p.name.take(37) + "…" else p.name
    val parts = ArrayList<MonoImage>()
    parts += HwPages.text(context, w, listOf(HwPages.Line(name, HwPages.Style.HEAD)), scale = 1.4f)
    parts += HwPages.text(context, w, listOf(HwPages.Line(price(p), HwPages.Style.TITLE)), scale = 2.2f)
    if (p.unit.isNotBlank()) parts += HwPages.text(context, w, listOf(HwPages.Line(StrLabel.perUnit(p.unit), HwPages.Style.SMALL)), scale = 1.3f)
    val out = MonoImage(w, h)
    var y = 0
    for (part in parts) { out.blit(part, 0, y); y += part.height }
    //  بارکدِ کوتاه در جای خالیِ ته برچسب — فقط اگر خوانا جا شود
    val symbol = Barcodes.forProduct(code(p))
    val room = h - y - 2
    if (symbol != null && room >= 40) {
      val md = Labels.moduleDots(symbol.modules.size, w - 8)
      if (md > 0) {
        val bars = Barcodes.render(symbol.modules, md, room.coerceAtMost(72))
        out.blit(bars, ((w - bars.width) / 2).coerceAtLeast(0), h - bars.height)
      }
    }
    return HwPages.LabelResult(out)
  }

  /**
   *  چاپِ `plan` (ترتیب و نسخه‌ها از `LabelPlan`). `a4` یا بی چاپگرِ برچسب ⇒ برگهٔ A4.
   *  بازگشت: پیامِ نتیجه برای نمایش.
   */
  suspend fun print(context: Context, products: Map<String, Product>, plan: LabelPlan.Plan, kind: LabelPlan.Kind, a4: Boolean): String {
    if (plan.isEmpty) return StrLabel.nothingToPrint
    val images = HashMap<String, MonoImage>()
    for (id in plan.labels.distinct()) {
      val p = products[id] ?: continue
      val r = withContext(Dispatchers.Default) { image(context, p, kind) }
      images[id] = r.image ?: return StrLabel.labelFailed(p.name, r.error.orEmpty())
    }
    val printer = PrinterRegistry.labelPrinter(context)
    if (a4 || printer == null) {
      val pages = withContext(Dispatchers.Default) { Labels.A4.sheets(plan.labels.mapNotNull { images[it] }) }
      if (pages.isEmpty()) return StrLabel.nothingToPrint
      LabelSheetPrint.print(context, pages, StrLabel.printTitle)
      return StrLabel.a4Opened(pages.size)
    }
    var bytes = ByteArray(0)
    var count = 0
    for ((id, n) in LabelPlan.runs(plan.labels)) {
      val img = images[id] ?: continue
      bytes += HwPages.labelBytes(printer, img, n)
      count += n
    }
    if (count == 0) return StrLabel.nothingToPrint
    return PrintSpooler.submitAndWait(
      context, PrintQueue.Kind.LABEL, printer.link, StrLabel.printTitle, bytes, done = StrLabel.printed(count),
    )
  }
}
