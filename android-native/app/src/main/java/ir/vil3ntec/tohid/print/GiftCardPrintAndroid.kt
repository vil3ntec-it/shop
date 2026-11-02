package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.data.GiftCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  PRM-14 — برگهٔ چاپیِ کارتِ هدیه (`GiftCards.Sheet`)، با همان ابزارِ برچسب و بارکد: نوشته با قلمِ برنامه
 *  (`HwPages.text`)، بارکدِ Code128 با `Barcodes.render` و پهنای خطِ `Labels.moduleDots` (دست‌کم ۰٫۲۵ میلی‌متر).
 *  چاپگرِ رسیدِ همین دستگاه (`PrinterRegistry.defaultSaved`، صفِ `PrintSpooler`)؛ بی چاپگر ⇒ برگهٔ A4 در نخستین
 *  خانهٔ `Labels.A4` (پنجرهٔ چاپِ سیستم، `LabelSheetPrint`). ⚠️ روی چاپگر و بارکدخوانِ واقعی آزموده نشده.
 */
object GiftCardPrint {

  /** پهنای کارت: یک خانهٔ A4 (۷۰ میلی‌متر) منهای حاشیه، و روی کاغذِ باریک‌تر همان کاغذ */
  private const val CARD_W = 544
  private const val BARS_H = 72

  fun image(context: Context, s: GiftCards.Sheet, paperDots: Int): MonoImage? {
    val w = paperDots.coerceAtMost(CARD_W)
    val quiet = 10
    val modules = BooleanArray(s.modules.length) { s.modules[it] == '1' }
    val md = Labels.moduleDots(modules.size, w - 8, quiet)
    if (md == 0) return null
    val bars = Barcodes.render(modules, md, BARS_H, quiet)
    val top = HwPages.text(
      context, w,
      listOfNotNull(
        HwPages.Line(s.title, HwPages.Style.HEAD),
        s.shop?.let { HwPages.Line(it, HwPages.Style.BODY) },
        s.amount?.let { HwPages.Line(it, HwPages.Style.HEAD) },
      ),
      scale = 1.2f,
    )
    //  کد درشت (فقط حرف و رقمِ لاتین و خط‌تیره ⇒ یک تکهٔ چپ‌به‌راست)، تا با دست هم خوانده و زده شود
    val code = HwPages.text(context, w, listOf(HwPages.Line(s.code, HwPages.Style.TITLE)), scale = 1.1f)
    val foot = HwPages.text(context, w, listOfNotNull(HwPages.Line(s.note, HwPages.Style.SMALL), s.date?.let { HwPages.Line(it, HwPages.Style.SMALL) }))
    val parts = listOf(top, bars, code, foot)
    val out = MonoImage(w, parts.sumOf { it.height } + 8)
    var y = 0
    for (p in parts) {
      out.blit(p, ((w - p.width) / 2).coerceAtLeast(0), y)
      y += p.height + if (p === bars) 4 else 0
    }
    return out
  }

  /** چاپ ⇒ پیامِ صفحه */
  suspend fun print(context: Context, s: GiftCards.Sheet): String {
    val printer = PrinterRegistry.defaultSaved(context)
    if (printer == null) {
      val page = withContext(Dispatchers.Default) {
        runCatching { image(context, s, CARD_W)?.let { Labels.A4.sheet(it, 1) } }.getOrNull()
      } ?: return StrGiftCard.printFailed("—")
      LabelSheetPrint.print(context, page, StrGiftCard.sheetTitle)
      return StrGiftCard.sheetOpened
    }
    val bytes = withContext(Dispatchers.IO) {
      runCatching {
        val p = printer.profile()
        val img = image(context, s, p.paperDots) ?: error("—")
        EscPos.document(img, p)
      }
    }.getOrElse { return StrGiftCard.printFailed(it.message ?: "—") }
    return PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT, printer.link, StrGiftCard.sheetTitle, bytes, done = StrGiftCard.printed)
  }
}
