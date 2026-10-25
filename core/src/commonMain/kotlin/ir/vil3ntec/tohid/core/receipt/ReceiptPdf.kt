package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.MonoImage
import kotlin.math.roundToInt

/**
 *  فاکتور ⇒ PDF ِ یک‌صفحه‌ای به پهنای کاغذِ ۸۰ میلی‌متری — برای «فرستادن» در گوشی و
 *  کامپیوتر (بی `PdfDocument` ِ اندروید که کامپیوتر ندارد). همان تصویرِ یک‌بیتیِ چاپ،
 *  یک‌به‌یک، بی فشرده‌سازی: فاکتور چند ده کیلوبایت است و هر خواننده‌ای بازش می‌کند.
 */
object ReceiptPdf {

  /** ۸۰ میلی‌متر به پوینت (۷۲ در اینچ) */
  private const val PAGE_WIDTH_PT = 80.0 / 25.4 * 72.0

  fun single(image: MonoImage): ByteArray {
    val w = PAGE_WIDTH_PT
    val h = PAGE_WIDTH_PT * image.height / image.width
    val out = Pdf()
    out.text("%PDF-1.4\n")
    out.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
    out.obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    out.obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pt(w)} ${pt(h)}] " +
      "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>")
    //  بیتِ ۱ ِ MonoImage سیاه است و در DeviceGray ۱ سفید ⇒ Decode [1 0]
    out.stream(4, "/Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} " +
      "/ColorSpace /DeviceGray /BitsPerComponent 1 /Decode [1 0]", image.bits)
    out.stream(5, "", "q ${pt(w)} 0 0 ${pt(h)} 0 0 cm /Im0 Do Q".encodeToByteArray())
    return out.finish(root = 1, objects = 5)
  }

  /** دو رقمِ اعشار، با نقطه — مستقل از زبانِ دستگاه */
  private fun pt(v: Double): String {
    val c = (v * 100).roundToInt()
    return "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
  }

  private class Pdf {
    private val buf = ArrayList<ByteArray>()
    private var size = 0
    private val offsets = HashMap<Int, Int>()

    fun bytes(b: ByteArray) { buf += b; size += b.size }
    fun text(s: String) = bytes(s.encodeToByteArray())

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
