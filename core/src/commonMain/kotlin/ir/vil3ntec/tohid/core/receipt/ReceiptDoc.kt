package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.PrinterProfile

/**
 *  فاکتور به شکلِ فهرستِ بلوک‌ها — یک منبع برای کاغذِ حرارتی، صفحهٔ فاکتور (V3)، PNG/PDF
 *  و همان ترتیبی که صفحهٔ عمومیِ QR دارد. `ReceiptLayout.build` می‌سازد؛ نوشته‌ها را سکو
 *  می‌کشد (قلمِ برنامه، راست‌به‌چپ) و بقیه را `ReceiptRaster`.
 */
data class ReceiptDoc(val widthDots: Int, val metrics: ReceiptMetrics, val blocks: List<ReceiptBlock>)

enum class TextStyle { TITLE, TOTAL, BODY, SMALL }

sealed interface ReceiptBlock {
  /** یک یا چند سطرِ نوشته؛ [center] ⇒ وسط، وگرنه راست (آغازِ سطر) */
  data class Text(val text: String, val style: TextStyle, val center: Boolean = true) : ReceiptBlock

  /** برچسب راست، مقدار چپ */
  data class Row(val label: String, val value: String, val style: TextStyle = TextStyle.BODY) : ReceiptBlock

  /** نامِ قلم؛ زیرش «۳ بسته × ۱۵۰» راست و جمعِ قلم چپ؛ و یادداشت‌ها (مرجوعی، قیمتِ اصلی) */
  data class Item(val name: String, val detail: String, val total: String, val notes: List<String> = emptyList()) : ReceiptBlock

  data class Rule(val dashed: Boolean = true) : ReceiptBlock

  data class Gap(val dots: Int) : ReceiptBlock

  /** تصویرِ یک‌بیتی، وسطِ کاغذ — لوگو */
  data class Logo(val image: MonoImage) : ReceiptBlock

  /** QR، وسطِ کاغذ؛ ماتریس بی حاشیه است و `ReceiptRaster` حاشیهٔ خالی را می‌گذارد */
  data class Qr(val url: String, val modules: Array<BooleanArray>) : ReceiptBlock {
    override fun equals(other: Any?): Boolean =
      other is Qr && other.url == url && other.modules.size == modules.size &&
        other.modules.indices.all { other.modules[it].contentEquals(modules[it]) }

    override fun hashCode(): Int = url.hashCode()
  }

  val isText: Boolean get() = this is Text || this is Row || this is Item
}

/** اندازه‌ها بر حسبِ نقطه (۲۰۳ dpi) برای ۵۸ و ۸۰ میلی‌متر */
data class ReceiptMetrics(
  val pad: Int,
  val title: Int,
  val total: Int,
  val body: Int,
  val small: Int,
  val qrModule: Int,
  val ruleThick: Int,
  val dashOn: Int,
  val dashOff: Int,
  val gap: Int,
) {
  fun size(style: TextStyle): Int = when (style) {
    TextStyle.TITLE -> title
    TextStyle.TOTAL -> total
    TextStyle.BODY -> body
    TextStyle.SMALL -> small
  }

  companion object {
    val MM58 = ReceiptMetrics(pad = 12, title = 30, total = 26, body = 20, small = 17, qrModule = 6, ruleThick = 2, dashOn = 6, dashOff = 4, gap = 8)
    val MM80 = ReceiptMetrics(pad = 18, title = 40, total = 34, body = 26, small = 22, qrModule = 8, ruleThick = 2, dashOn = 8, dashOff = 5, gap = 10)

    fun of(widthDots: Int): ReceiptMetrics =
      if (PrinterProfile.paper(widthDots) >= PrinterProfile.WIDTH_80MM) MM80 else MM58
  }
}
