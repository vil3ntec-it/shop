package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.receipt.PaperBidi
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.print.ReceiptPainter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  HW-01 روی کاغذِ واقعی (همان `ReceiptPainter` ِ گوشی روی شیمِ Java2D): نشانهٔ «−» ِ تخفیف/گردکردن/ماندهٔ منفی در
 *  کاغذِ راست‌به‌چپ چپِ رقمش کشیده می‌شود (`PaperBidi`، از راهِ `Receipt.layout`)، و LRM ِ آن هیچ نقطه‌ای نمی‌کشد
 *  (نه مربعِ «نویسهٔ ناشناخته») — در ۵۸ و ۸۰ میلی‌متری.
 */
class ReceiptPaperBidiDeskTest {

  @After fun reset() {
    LocaleChoice.setLang(DesktopContext, Lang.FA)
    I18n.source = { Lang.FA }
  }

  private fun lang(l: Lang) {
    LocaleChoice.setLang(DesktopContext, l)
    I18n.source = { LocaleChoice.lang }
  }

  private fun columns(img: MonoImage): IntRange {
    var lo = img.width
    var hi = -1
    for (y in 0 until img.height) for (x in 0 until img.width) if (img[x, y]) { lo = minOf(lo, x); hi = maxOf(hi, x) }
    return lo..hi
  }

  /** نقطه‌های ستون‌های [cols] */
  private fun slice(img: MonoImage, cols: IntRange): List<Boolean> =
    (0 until img.height).flatMap { y -> cols.map { x -> x < img.width && img[x, y] } }

  @Test fun `LRM هیچ نقطه‌ای روی کاغذ نمی‌کشد`() {
    lang(Lang.FA)
    val painter = ReceiptPainter(DesktopContext)
    for ((w, m) in listOf(384 to ReceiptMetrics.MM58, 576 to ReceiptMetrics.MM80)) {
      val blank = painter.render(ReceiptBlock.Row("", PaperBidi.LRM.toString()), w, m)
      assertTrue("$w: LRM کشیده شد ${columns(blank)}", columns(blank).isEmpty())
    }
  }

  @Test fun `نشانهٔ منفی در کاغذ راست‌به‌چپ چپِ رقمش است - رقم فارسی و لاتین`() {
    for (l in listOf(Lang.FA, Lang.PRS, Lang.PS)) {
      lang(l)
      val painter = ReceiptPainter(DesktopContext)
      for ((w, m) in listOf(384 to ReceiptMetrics.MM58, 576 to ReceiptMetrics.MM80)) for (n in listOf("۵۰", "50")) {
        //  مبلغ در کاغذِ راست‌به‌چپ چپ‌چین است: نخستین شکلِ چپ باید خودِ «−» باشد، نه رقم («۵۰−»)
        val minus = painter.render(ReceiptBlock.Row("", "−"), w, m)
        val signed = painter.render(ReceiptBlock.Row("", "−$n"), w, m)
        assertEquals("$l $w $n: ارتفاع", minus.height, signed.height)
        val cols = columns(minus)
        assertTrue("$l $w $n: «−» نخستین شکلِ چپ نیست", slice(minus, cols) == slice(signed, cols) && columns(signed).first == cols.first)
      }
    }
  }
}
