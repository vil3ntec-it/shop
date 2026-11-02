package ir.vil3ntec.tohid.print

import android.content.Context
import android.text.Layout
import ir.vil3ntec.tohid.core.hw.Code128
import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.i18n.StrDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  «کارتِ فرمان» روی کاغذِ حرارتی (یا روی صفحه): سه بارکدِ Code 128 — «فاکتورِ تازه»، «نقد پرداخت شد»، «حذفِ آخرین
 *  کالا» — با رازِ همین دستگاه (`ScanCashPref`). محتوا از `CommandCard.payload` (فضای‌نام + نسل + امضا).
 */
object CommandCardPrint {

  /** ترتیبِ روی کارت — «نقد» وسط و درشت‌تر نیست؛ هر سه یک‌اندازه تا اشتباه خوانده نشوند */
  val ORDER: List<CommandCard.Command> = listOf(CommandCard.Command.NEW, CommandCard.Command.CASH, CommandCard.Command.UNDO)

  fun label(c: CommandCard.Command): String = when (c) {
    CommandCard.Command.NEW -> StrDevices.cmdNew
    CommandCard.Command.CASH -> StrDevices.cmdCash
    CommandCard.Command.UNDO -> StrDevices.cmdUndo
  }

  /** ماژول‌های بارکدِ یک فرمان */
  fun modules(c: CommandCard.Command, key: CommandCard.Key): BooleanArray = Code128.modules(CommandCard.payload(c, key))

  fun image(context: Context, key: CommandCard.Key, paperDots: Int): MonoImage {
    val w = paperDots
    val parts = ArrayList<MonoImage>()
    parts += HwPages.text(context, w, listOf(HwPages.Line(StrDevices.cardTitle, HwPages.Style.TITLE), HwPages.Line(StrDevices.cardNumber(key.gen), HwPages.Style.SMALL)))
    ORDER.forEach { c ->
      parts += HwPages.text(context, w, listOf(HwPages.Line(label(c), HwPages.Style.HEAD)))
      parts += ScannerSheetPrint.barcode(modules(c, key), w, 100)
      parts += HwPages.text(context, w, listOf(HwPages.Line(CommandCard.payload(c, key), HwPages.Style.SMALL)))
    }
    parts += HwPages.text(context, w, listOf(HwPages.Line(StrDevices.cardLine, HwPages.Style.SMALL, Layout.Alignment.ALIGN_NORMAL)))
    return HwPages.stack(w, parts, gap = 14)
  }

  /** چاپ روی چاپگرِ پیش‌فرضِ رسید، بی صف؛ `null` ⇒ چاپ شد، وگرنه پیام */
  suspend fun print(context: Context, key: CommandCard.Key): String? = withContext(Dispatchers.IO) {
    val s = PrinterRegistry.defaultSaved(context) ?: return@withContext StrDevices.cardNoPrinter
    val p = s.profile()
    val bytes = runCatching { EscPos.document(image(context, key, p.paperDots), p) }.getOrElse { return@withContext StrDevices.cardDrawFailed(it.message.orEmpty()) }
    val link = ThermalPrinter.Link.parse(s.link) ?: return@withContext StrDevices.printerLinkBad
    ThermalPrinter.send(context, link, bytes)
  }
}
