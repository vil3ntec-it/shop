package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.qr.QrCode

/**
 *  [ReceiptDoc] ⇒ یک تصویرِ یک‌بیتی به پهنای کاغذ — همان که `EscPos.document` می‌برد و
 *  پیش‌نمایش و PNG/PDF نشان می‌دهند.
 *
 *  نوشته‌ها (`Text`، `Row`، `Item`) را سکو با قلمِ برنامه و راست‌به‌چپ می‌کشد و به پهنای
 *  کاغذ برمی‌گرداند ([TextRenderer])؛ خط، فاصله، لوگو و QR همین‌جا یک‌بیتی ساخته می‌شوند —
 *  بی `drawBitmap`، تا کامپیوتر (Java2D) و گوشی بیت‌به‌بیت یکی باشند و لوگو و QR هیچ‌وقت
 *  کشیده یا نرم نشوند.
 */
object ReceiptRaster {

  fun interface TextRenderer {
    /** بلوکِ نوشته ⇒ تصویرِ یک‌بیتی به پهنای [widthDots]، حاشیهٔ کناری با خودِ سکو */
    fun render(block: ReceiptBlock, widthDots: Int, metrics: ReceiptMetrics): MonoImage
  }

  fun compose(doc: ReceiptDoc, text: TextRenderer): MonoImage {
    val w = doc.widthDots
    val m = doc.metrics
    val parts = doc.blocks.mapNotNull { block ->
      when (block) {
        is ReceiptBlock.Text, is ReceiptBlock.Row, is ReceiptBlock.Item -> text.render(block, w, m)
        is ReceiptBlock.Rule -> rule(w, m, block.dashed)
        is ReceiptBlock.Gap -> if (block.dots > 0) MonoImage(w, block.dots) else null
        is ReceiptBlock.Logo -> centered(w, block.image)
        is ReceiptBlock.Qr -> centered(w, qr(block.modules, moduleDots(block.modules.size, w, m)))
      }
    }
    val bottom = m.pad * 2
    val out = MonoImage(w, parts.sumOf { it.height } + m.pad + bottom)
    var y = m.pad
    for (p in parts) { out.blit(p, 0, y); y += p.height }
    return out
  }

  /** خطِ جداکننده با فاصلهٔ بالا و پایین؛ نقطه‌چین برای بخش‌ها، پُر پیش از مبلغِ نهایی */
  fun rule(width: Int, m: ReceiptMetrics, dashed: Boolean): MonoImage {
    val h = m.gap * 2 + m.ruleThick
    val img = MonoImage(width, h)
    var x = m.pad
    while (x < width - m.pad) {
      val run = if (dashed) minOf(m.dashOn, width - m.pad - x) else width - 2 * m.pad
      img.fill(x, m.gap, run, m.ruleThick)
      x += run + if (dashed) m.dashOff else 0
    }
    return img
  }

  /** اندازهٔ هر خانهٔ QR: همان متریک، کوچک‌تر اگر با حاشیهٔ خالی در کاغذ جا نشود */
  fun moduleDots(modules: Int, width: Int, m: ReceiptMetrics): Int {
    val span = modules + 2 * QrCode.QUIET_ZONE
    return minOf(m.qrModule, (width - 2 * m.pad) / span).coerceAtLeast(1)
  }

  /** ماتریسِ QR ⇒ تصویر با حاشیهٔ خالیِ استاندارد */
  fun qr(modules: Array<BooleanArray>, dots: Int): MonoImage {
    val n = modules.size
    val side = (n + 2 * QrCode.QUIET_ZONE) * dots
    val img = MonoImage(side, side)
    for (y in 0 until n) for (x in 0 until n) if (modules[y][x]) {
      img.fill((x + QrCode.QUIET_ZONE) * dots, (y + QrCode.QUIET_ZONE) * dots, dots, dots)
    }
    return img
  }

  private fun centered(width: Int, img: MonoImage): MonoImage {
    if (img.width >= width) return img
    val out = MonoImage(width, img.height)
    out.blit(img, (width - img.width) / 2, 0)
    return out
  }
}
