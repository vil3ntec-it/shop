package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor

/*
 *  shimِ iOS ِ چاپِ سیستمِ اندروید (`PrintManager` + `PrintDocumentAdapter`) — گامِ ۴. همان دو فایلِ اندروید
 *  (`print/ReportPrint.kt`، `print/LabelSheetPrint.kt`) دست‌نخورده روی آیفون کار می‌کنند:
 *    `PrintManager.print` ⇐ `onLayout` و `onWrite` ِ همان سازگارکننده یک بار، PDF در cache
 *    (`PrintedPdfDocument` ِ iOS، ‎android/print/pdf‎) ⇐ پنجرهٔ AirPrint ِ iOS (‎ios/IosPrint.kt‎).
 *  اندازهٔ کاغذ همان که کدِ مشترک خواسته (A4)؛ کاربر در پنجرهٔ iOS چاپگر، شمار و صفحه‌ها را برمی‌گزیند.
 */

class PrintAttributes private constructor(
  val mediaSize: MediaSize?,
  val resolution: Resolution?,
  val minMargins: Margins?,
  val colorMode: Int,
) {
  class MediaSize(val id: String, private val label: String, val widthMils: Int, val heightMils: Int) {
    fun getLabel(packageManager: Any?): String = label
    val isPortrait: Boolean get() = heightMils >= widthMils

    override fun equals(other: Any?): Boolean = other is MediaSize && other.widthMils == widthMils && other.heightMils == heightMils
    override fun hashCode(): Int = widthMils * 31 + heightMils

    companion object {
      val ISO_A4 = MediaSize("ISO_A4", "A4", 8270, 11690)
      val ISO_A5 = MediaSize("ISO_A5", "A5", 5830, 8270)
      val NA_LETTER = MediaSize("NA_LETTER", "Letter", 8500, 11000)
    }
  }

  class Resolution(val id: String, val label: String, val horizontalDpi: Int, val verticalDpi: Int)

  class Margins(val leftMils: Int, val topMils: Int, val rightMils: Int, val bottomMils: Int) {
    companion object {
      val NO_MARGINS = Margins(0, 0, 0, 0)
    }
  }

  class Builder {
    private var media: MediaSize? = null
    private var res: Resolution? = null
    private var margins: Margins? = null
    private var color = COLOR_MODE_COLOR

    fun setMediaSize(mediaSize: MediaSize) = apply { media = mediaSize }
    fun setResolution(resolution: Resolution) = apply { res = resolution }
    fun setMinMargins(margins: Margins) = apply { this.margins = margins }
    fun setColorMode(colorMode: Int) = apply { color = colorMode }
    fun build() = PrintAttributes(media, res, margins, color)
  }

  override fun equals(other: Any?): Boolean =
    other is PrintAttributes && other.mediaSize == mediaSize && other.colorMode == colorMode &&
      other.resolution?.horizontalDpi == resolution?.horizontalDpi && other.minMargins?.leftMils == minMargins?.leftMils

  override fun hashCode(): Int = (mediaSize?.hashCode() ?: 0) * 31 + colorMode

  companion object {
    const val COLOR_MODE_MONOCHROME = 1
    const val COLOR_MODE_COLOR = 2
  }
}

class PageRange(val start: Int, val end: Int) {
  companion object {
    val ALL_PAGES = PageRange(0, Int.MAX_VALUE)
  }
}

class PrintDocumentInfo private constructor(val name: String, val contentType: Int, val pageCount: Int) {
  class Builder(private val name: String) {
    private var type = CONTENT_TYPE_UNKNOWN
    private var count = PAGE_COUNT_UNKNOWN
    fun setContentType(type: Int) = apply { this.type = type }
    fun setPageCount(pageCount: Int) = apply { count = pageCount }
    fun build() = PrintDocumentInfo(name, type, count)
  }

  companion object {
    const val PAGE_COUNT_UNKNOWN = -1
    const val CONTENT_TYPE_UNKNOWN = -1
    const val CONTENT_TYPE_DOCUMENT = 0
    const val CONTENT_TYPE_PHOTO = 1
  }
}

abstract class PrintDocumentAdapter {
  open fun onStart() {}

  abstract fun onLayout(
    oldAttributes: PrintAttributes?,
    newAttributes: PrintAttributes,
    cancellationSignal: CancellationSignal?,
    callback: LayoutResultCallback,
    extras: Bundle?,
  )

  abstract fun onWrite(
    pages: Array<out PageRange>?,
    destination: ParcelFileDescriptor,
    cancellationSignal: CancellationSignal?,
    callback: WriteResultCallback,
  )

  open fun onFinish() {}

  abstract class LayoutResultCallback {
    open fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {}
    open fun onLayoutFailed(error: CharSequence?) {}
    open fun onLayoutCancelled() {}
  }

  abstract class WriteResultCallback {
    open fun onWriteFinished(pages: Array<out PageRange>) {}
    open fun onWriteFailed(error: CharSequence?) {}
    open fun onWriteCancelled() {}
  }
}

class PrintJob internal constructor(val name: String, private val done: Boolean) {
  val isCompleted: Boolean get() = done
  val isFailed: Boolean get() = !done
  val isCancelled: Boolean get() = false
  fun cancel() {}
}

/** `context.getSystemService(Context.PRINT_SERVICE)` ِ iOS (‎android/content/Context.kt‎) */
class PrintManager internal constructor() {
  fun print(printJobName: String, documentAdapter: PrintDocumentAdapter, attributes: PrintAttributes?): PrintJob =
    PrintJob(printJobName, ir.vil3ntec.tohid.ios.IosPrint.print(printJobName, documentAdapter, attributes ?: DEFAULT))

  companion object {
    internal val ios = PrintManager()
    private val DEFAULT = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
  }
}
