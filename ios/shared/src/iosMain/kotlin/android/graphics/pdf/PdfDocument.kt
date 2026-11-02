package android.graphics.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import ir.vil3ntec.tohid.ios.print.RasterPdf
import java.io.IOException
import java.io.OutputStream
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_OK
import platform.zlib.compress2
import platform.zlib.compressBound

/*
 *  shimِ iOS ِ `android.graphics.pdf.PdfDocument` — گامِ ۴. هر صفحه یک `Canvas` ِ همان shimِ Skia است با واحدِ
 *  پوینت (۷۲ در اینچ) روی بیت‌مپِ [dpi]؛ `finishPage` پیکسل‌ها را خاکستری (یا RGB) و با zlib فشرده نگه می‌دارد
 *  و `writeTo` همهٔ صفحه‌ها را با `RasterPdf` (‎commonMain‎) یک PDF می‌کند. متن در PDF ِ آیفون تصویر است، نه
 *  برداری (اندروید برداری است) — برای چاپ و خواندن یکی است، فقط جست‌وجوی متن در PDF نیست.
 */
open class PdfDocument internal constructor(private val dpi: Int, private val gray: Boolean) {
  constructor() : this(DEFAULT_DPI, gray = false)

  class PageInfo internal constructor(val pageWidth: Int, val pageHeight: Int, val pageNumber: Int, val contentRect: Rect) {
    class Builder(private val pageWidth: Int, private val pageHeight: Int, private val pageNumber: Int) {
      private var content: Rect? = null
      fun setContentRect(contentRect: Rect) = apply { content = contentRect }
      fun create() = PageInfo(pageWidth, pageHeight, pageNumber, content ?: Rect(0, 0, pageWidth, pageHeight))
    }
  }

  class Page internal constructor(val info: PageInfo, internal val bitmap: Bitmap, val canvas: Canvas)

  private val done = ArrayList<RasterPdf.Page>()
  private val infos = ArrayList<PageInfo>()
  private var open: Page? = null
  private var closed = false

  val pages: List<PageInfo> get() = infos.toList()

  fun startPage(pageInfo: PageInfo): Page {
    check(!closed) { "document is closed" }
    check(open == null) { "current page not finished" }
    val unit = dpi / 72f
    val bitmap = Bitmap.blank(RasterPdf.pixels(pageInfo.pageWidth, dpi), RasterPdf.pixels(pageInfo.pageHeight, dpi))
    val canvas = Canvas(bitmap, unit)
    canvas.drawColor(Color.WHITE)
    return Page(pageInfo, bitmap, canvas).also { open = it }
  }

  fun finishPage(page: Page) {
    check(open === page) { "page not started on this document" }
    open = null
    val b = page.bitmap
    val pixels = if (gray) {
      b.sk.readPixels(ImageInfo(b.width, b.height, ColorType.GRAY_8, ColorAlphaType.OPAQUE), b.width, 0, 0)
    } else {
      b.sk.readPixels(ImageInfo(b.width, b.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), b.width * 4, 0, 0)
        ?.let { rgba -> ByteArray(b.width * b.height * 3) { i -> rgba[(i / 3) * 4 + i % 3] } }
    } ?: throw IOException("iOS: صفحهٔ PDF خوانده نشد")
    b.sk.close()
    b.recycle()
    done += RasterPdf.Page(page.info.pageWidth.toDouble(), page.info.pageHeight.toDouble(), b.width, b.height, gray, zlib(pixels))
    infos += page.info
  }

  fun writeTo(out: OutputStream) {
    check(open == null) { "current page not finished" }
    out.write(RasterPdf.write(done))
  }

  open fun close() {
    closed = true
    done.clear()
  }

  companion object {
    /** همان «۳۰۰ در ۳۰۰» ِ `ReportPrint.pdf` ِ اندروید */
    internal const val DEFAULT_DPI = 300

    /** zlib ِ خودِ iOS — همان «FlateDecode» ِ PDF (با سرآیندِ zlib) */
    internal fun zlib(input: ByteArray): ByteArray = memScoped {
      val bound = compressBound(input.size.convert())
      val out = ByteArray(bound.toInt())
      val len = alloc<ULongVar>().apply { value = bound.convert() }
      val rc = out.usePinned { o ->
        input.usePinned { i ->
          compress2(o.addressOf(0).reinterpret(), len.ptr.reinterpret(), i.addressOf(0).reinterpret(), input.size.convert(), Z_DEFAULT_COMPRESSION)
        }
      }
      if (rc != Z_OK) throw IOException("iOS: فشرده‌سازیِ صفحهٔ PDF ($rc)")
      out.copyOf(len.value.toInt())
    }
  }
}
