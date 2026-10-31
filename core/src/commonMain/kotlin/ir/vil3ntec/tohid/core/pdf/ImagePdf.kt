package ir.vil3ntec.tohid.core.pdf

import ir.vil3ntec.tohid.core.hw.MonoImage

/**
 *  RPT-11 — فایلِ PDF از صفحه‌های تصویری (سیاه‌وسفید)، بی کتابخانه و بی JDK.
 *
 *  چرا تصویر: گزارشِ دری و پشتو با همان قلمِ برنامه روی صفحه کشیده می‌شود (مثلِ رسید)؛ PDFِ متنی
 *  قلمِ جاسازی‌شده و شکل‌دهیِ حروفِ پیوسته می‌خواست. هر صفحه یک تصویرِ یک‌بیتی است با فیلترِ
 *  استانداردِ `RunLengthDecode` (بی zlib، که در commonMain نیست) — گزارشِ جدولی بیشترش سفید است و
 *  خوب فشرده می‌شود. اندازهٔ صفحه از اندازهٔ تصویر و `dpi` (A4 در ۱۵۰ ⇒ ۱۲۴۰×۱۷۵۴ نقطه).
 *
 *  ساختار: Catalog ← Pages ← (Page، Contents، XObject) برای هر صفحه، Info با عنوانِ UTF-16، جدولِ
 *  xref با جای دقیقِ هر شیء. آزمون: `ImagePdfTest` (ساختار و RLE) و `ImagePdfFileTest` (فایل برای
 *  `pdfinfo`/pypdf).
 */
object ImagePdf {

  /** صفحه‌ها به ترتیب؛ دست‌کم یکی. `title` در «مشخصاتِ سند» دیده می‌شود. */
  fun write(pages: List<MonoImage>, dpi: Int = 150, title: String = ""): ByteArray {
    require(pages.isNotEmpty()) { "PDF بی صفحه ساخته نمی‌شود" }
    require(dpi in 36..1200) { "dpi نامعقول است" }
    val out = Out()
    val offsets = ArrayList<Int>()
    fun obj(body: () -> Unit) {
      offsets += out.size
      out.text("${offsets.size} 0 obj\n")
      body()
      out.text("\nendobj\n")
    }
    out.text("%PDF-1.4\n")
    out.bytes(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
    // ۱ Catalog، ۲ Pages، ۳ Info، سپس سه شیء برای هر صفحه
    val first = 4
    obj { out.text("<< /Type /Catalog /Pages 2 0 R >>") }
    val kids = pages.indices.joinToString(" ") { "${first + it * 3} 0 R" }
    obj { out.text("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>") }
    obj { out.text("<< /Producer (VILL3N) /Title <${utf16Hex(title)}> >>") }
    pages.forEachIndexed { i, img ->
      val page = first + i * 3
      val w = pt(img.width, dpi)
      val h = pt(img.height, dpi)
      obj {
        out.text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] ")
        out.text("/Resources << /XObject << /Im0 ${page + 2} 0 R >> >> /Contents ${page + 1} 0 R >>")
      }
      val content = "q $w 0 0 $h 0 0 cm /Im0 Do Q".encodeToByteArray()
      obj { stream("<< /Length ${content.size} >>", content, out) }
      val data = runLength(img.bits)
      obj {
        stream(
          "<< /Type /XObject /Subtype /Image /Width ${img.width} /Height ${img.height} /ColorSpace /DeviceGray " +
            "/BitsPerComponent 1 /Decode [1 0] /Filter /RunLengthDecode /Length ${data.size} >>",
          data, out,
        )
      }
    }
    val xref = out.size
    out.text("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { out.text(it.toString().padStart(10, '0') + " 00000 n \n") }
    out.text("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
  }

  /** نقطه ⇒ پوینتِ PDF (۱/۷۲ اینچ) با دو رقمِ اعشار */
  internal fun pt(px: Int, dpi: Int): String {
    val hundredths = (px.toLong() * 7200 + dpi / 2) / dpi
    val frac = (hundredths % 100).toInt()
    return (hundredths / 100).toString() + if (frac != 0) "." + frac.toString().padStart(2, '0').trimEnd('0') else ""
  }

  private fun stream(dict: String, data: ByteArray, out: Out) {
    out.text("$dict\nstream\n")
    out.bytes(data)
    out.text("\nendstream")
  }

  /** عنوان به UTF-16BE با BOM (هگز) — تا نامِ دری درست دیده شود */
  internal fun utf16Hex(s: String): String {
    if (s.isEmpty()) return ""
    val sb = StringBuilder("FEFF")
    for (c in s) sb.append(c.code.toString(16).uppercase().padStart(4, '0'))
    return sb.toString()
  }

  /**
   *  `RunLengthDecode` ِ PDF (همان PackBits): بایتِ طولِ ۰ تا ۱۲۷ ⇒ n+1 بایتِ لخت؛ ۱۲۹ تا ۲۵۵ ⇒ بایتِ
   *  بعدی 257−n بار؛ ۱۲۸ = پایان.
   */
  internal fun runLength(src: ByteArray): ByteArray {
    val out = Out()
    var i = 0
    val n = src.size
    while (i < n) {
      var run = 1
      while (i + run < n && run < 128 && src[i + run] == src[i]) run++
      if (run >= 2) {
        out.bytes(byteArrayOf((257 - run).toByte(), src[i]))
        i += run
        continue
      }
      val start = i
      var len = 0
      while (i < n && len < 128) {
        if (i + 1 < n && src[i + 1] == src[i]) break
        i++; len++
      }
      if (len == 0) { i++; len = 1 }
      out.bytes(byteArrayOf((len - 1).toByte()))
      out.bytes(src.copyOfRange(start, start + len))
    }
    out.bytes(byteArrayOf(128.toByte()))
    return out.toByteArray()
  }

  /** وارونهٔ `runLength` — فقط برای آزمون */
  internal fun runLengthDecode(src: ByteArray): ByteArray {
    val out = Out()
    var i = 0
    while (i < src.size) {
      val len = src[i].toInt() and 0xFF
      i++
      when {
        len == 128 -> break
        len < 128 -> { out.bytes(src.copyOfRange(i, i + len + 1)); i += len + 1 }
        else -> { out.bytes(ByteArray(257 - len) { src[i] }); i++ }
      }
    }
    return out.toByteArray()
  }

  private class Out {
    private var buf = ByteArray(4096)
    var size = 0
      private set
    fun bytes(b: ByteArray) {
      if (size + b.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + b.size))
      b.copyInto(buf, size)
      size += b.size
    }
    fun text(s: String) = bytes(s.encodeToByteArray())
    fun toByteArray(): ByteArray = buf.copyOf(size)
  }
}
