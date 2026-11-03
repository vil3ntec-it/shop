package ir.vil3ntec.tohid.core.receipt

/**
 *  بلندیِ تخمینیِ کاغذِ یک [ReceiptDoc] پیش از کشیدن — برای برگهٔ «چاپ» ِ تاریخچه («حدودِ ۴۵ سانتی‌متر»).
 *
 *  کشیدنِ واقعی کارِ سکوست (قلمِ برنامه، `ReceiptRaster.TextRenderer`) و برای یک سالِ فاکتور گران؛ این‌جا فقط
 *  شمارِ سطرها از پهنای کاغذ و اندازهٔ قلمِ [ReceiptMetrics] تخمین زده می‌شود. عمداً کمی **بیشتر** از واقعی است
 *  (پهنای میانگینِ نویسه و فاصلهٔ سطرِ وزیرمتن گشاده گرفته شده): کمبودِ کاغذ وسطِ چاپ بدتر از ماندنِ چند سانت است.
 *  خط، فاصله، لوگو و QR همان اندازهٔ `ReceiptRaster` را دارند.
 *
 *  خالص (commonMain). چاپگرِ حرارتیِ ۲۰۳ dpi: هشت نقطه در هر میلی‌متر.
 */
object PaperLength {
  const val DOTS_PER_MM = 8

  /** بلندیِ هر سطر نسبت به اندازهٔ قلم (وزیرمتن با بالا و پایینِ خودش) */
  private const val LINE = 1.7

  /** پهنای میانگینِ هر نویسه نسبت به اندازهٔ قلم (فارسی و رقم) */
  private const val GLYPH = 0.5

  /** سطرِ خالیِ `EscPos.feed` (فاصلهٔ پیش‌فرضِ ۱/۶ اینچ) */
  private const val FEED_LINE_DOTS = 34

  /** شمارِ سطرِ نوشته در پهنای [width] نقطه با قلمِ [size] — دست‌کم یک سطر برای هر بند */
  fun lines(text: String, size: Int, width: Int): Int {
    val perLine = (width / (size * GLYPH)).toInt().coerceAtLeast(1)
    return text.split('\n').sumOf { part -> ((part.length + perLine - 1) / perLine).coerceAtLeast(1) }
  }

  private fun textDots(text: String, size: Int, width: Int): Int = (lines(text, size, width) * size * LINE).toInt()

  /** بلندیِ یک بلوک به نقطه، با همان چیدمانِ `ReceiptPainter` (برچسب نیمی از پهنا؛ شرحِ قلم ۶۰٪) */
  fun dots(block: ReceiptBlock, widthDots: Int, m: ReceiptMetrics): Int {
    val inner = (widthDots - m.pad * 2).coerceAtLeast(1)
    return when (block) {
      is ReceiptBlock.Text -> textDots(block.text, m.size(block.style), inner)
      is ReceiptBlock.Row -> {
        val size = m.size(block.style)
        maxOf(textDots(block.label, size, inner / 2), textDots(block.value, size, inner - inner / 2))
      }
      is ReceiptBlock.Item -> {
        val detail = (inner * 0.6).toInt()
        textDots(block.name, m.body, inner) +
          maxOf(textDots(block.detail, m.small, detail), textDots(block.total, m.small, inner - detail)) +
          block.notes.sumOf { textDots(it, m.small, inner) } + m.gap / 2
      }
      is ReceiptBlock.Rule -> m.gap * 2 + m.ruleThick
      is ReceiptBlock.Gap -> block.dots.coerceAtLeast(0)
      is ReceiptBlock.Logo -> block.image.height
      is ReceiptBlock.Qr -> (block.modules.size + 8) * m.qrModule
    }
  }

  /** بلندیِ یک تکه از بلوک‌ها، بی حاشیهٔ بالا و پایینِ سند */
  fun dots(blocks: List<ReceiptBlock>, widthDots: Int, m: ReceiptMetrics = ReceiptMetrics.of(widthDots)): Int =
    blocks.sumOf { dots(it, widthDots, m) }

  /** کلِ سند، با حاشیهٔ `ReceiptRaster.compose` و سطرهای خالیِ پیش از برش */
  fun dots(doc: ReceiptDoc, feedLines: Int = 3): Int =
    doc.metrics.pad * 3 + dots(doc.blocks, doc.widthDots, doc.metrics) + feedLines.coerceIn(0, 20) * FEED_LINE_DOTS

  /** نقطه ⇒ سانتی‌متر، رو به بالا (نیم سانت هم یک سانت) */
  fun cm(dots: Int): Int = (dots + DOTS_PER_MM * 10 - 1) / (DOTS_PER_MM * 10)
}
