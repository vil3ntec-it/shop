package ir.vil3ntec.tohid.ios.print

import kotlin.math.roundToInt

/**
 *  PDF ِ چندصفحه‌ای از تصویرِ صفحه‌ها — خروجیِ `PdfDocument`/`PrintedPdfDocument` ِ آیفون (‎android/graphics/pdf‎).
 *
 *  اندروید صفحهٔ PDF را با رسمِ برداری می‌سازد؛ آیفون همان رسمِ کدِ مشترک (`ReportPrint`، `LabelSheetPrint`) را
 *  روی Skia با ۳۰۰ نقطه در اینچ می‌کشد و هر صفحه یک تصویرِ خاکستری/رنگیِ فشرده (FlateDecode، zlib) می‌شود
 *  که تمامِ صفحه را می‌گیرد — همان شیوهٔ `core/receipt/ReceiptPdf` (فاکتورِ ۸۰ میلی‌متری)، برای چند صفحه.
 *  فشرده‌سازی کارِ صدازننده است (iOS: zlib ِ خودِ سیستم؛ آزمون: `Deflater` ِ JDK)، تا این‌جا خالص بماند.
 */
object RasterPdf {

  /**
   *  یک صفحه: اندازه به پوینت (۷۲ در اینچ)، تصویر به پیکسل، [flate] = پیکسل‌های سطربه‌سطر (۱ بایت خاکستری یا
   *  ۳ بایت RGB) که با zlib فشرده شده‌اند.
   */
  class Page(
    val widthPt: Double,
    val heightPt: Double,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gray: Boolean,
    val flate: ByteArray,
  )

  fun write(pages: List<Page>): ByteArray {
    require(pages.isNotEmpty()) { "PDF بی صفحه" }
    val out = Pdf()
    out.text("%PDF-1.4\n%âãÏÓ\n")
    //  ۱ کاتالوگ، ۲ فهرستِ صفحه‌ها، سپس برای هر صفحه سه شیء: صفحه، تصویر، محتوا
    val kids = pages.indices.joinToString(" ") { "${pageObj(it)} 0 R" }
    out.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
    out.obj(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
    pages.forEachIndexed { i, p ->
      val n = pageObj(i)
      val w = pt(p.widthPt)
      val h = pt(p.heightPt)
      out.obj(n, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
        "/Resources << /XObject << /Im0 ${n + 1} 0 R >> >> /Contents ${n + 2} 0 R >>")
      out.stream(n + 1, "/Type /XObject /Subtype /Image /Width ${p.pixelWidth} /Height ${p.pixelHeight} " +
        "/ColorSpace ${if (p.gray) "/DeviceGray" else "/DeviceRGB"} /BitsPerComponent 8 /Filter /FlateDecode", p.flate)
      out.stream(n + 2, "", "q $w 0 0 $h 0 0 cm /Im0 Do Q".encodeToByteArray())
    }
    return out.finish(root = 1, objects = 2 + 3 * pages.size)
  }

  /** اندازهٔ صفحهٔ `PrintedPdfDocument` ِ اندروید: هزارمِ اینچ ⇐ پوینت، گرد به پایین (A4 = ۵۹۵ × ۸۴۱) */
  fun points(mils: Int): Int = (mils.toFloat() / 1000f * 72f).toInt()

  /** پوینت ⇐ پیکسلِ تصویرِ صفحه در [dpi] */
  fun pixels(points: Int, dpi: Int): Int = (points * dpi / 72.0).roundToInt().coerceAtLeast(1)

  private fun pageObj(index: Int) = 3 + 3 * index

  /** دو رقمِ اعشار، با نقطه — مستقل از زبانِ دستگاه (همان `ReceiptPdf`) */
  internal fun pt(v: Double): String {
    val c = (v * 100).roundToInt()
    return "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
  }

  private class Pdf {
    private val buf = ArrayList<ByteArray>()
    private var size = 0
    private val offsets = HashMap<Int, Int>()

    fun bytes(b: ByteArray) { buf += b; size += b.size }

    /** فقط ASCII؛ نویسهٔ بالای ۱۲۷ یک بایتِ خام (سطرِ دومِ سرآیند، نشانِ «دودویی») */
    fun text(s: String) = bytes(ByteArray(s.length) { s[it].code.toByte() })

    fun obj(n: Int, body: String) {
      offsets[n] = size
      text("$n 0 obj\n$body\nendobj\n")
    }

    fun stream(n: Int, dict: String, data: ByteArray) {
      offsets[n] = size
      text("$n 0 obj\n<< $dict /Length ${data.size} >>\nstream\n")
      bytes(data)
      text("\nendstream\nendobj\n")
    }

    fun finish(root: Int, objects: Int): ByteArray {
      val xref = size
      text("xref\n0 ${objects + 1}\n0000000000 65535 f \n")
      for (i in 1..objects) text("${offsets.getValue(i).toString().padStart(10, '0')} 00000 n \n")
      text("trailer\n<< /Size ${objects + 1} /Root $root 0 R >>\nstartxref\n$xref\n%%EOF\n")
      val out = ByteArray(size)
      var at = 0
      for (b in buf) { b.copyInto(out, at); at += b.size }
      return out
    }
  }
}
