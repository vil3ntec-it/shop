package ir.vil3ntec.tohid.ios

import android.content.IosContext
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.core.i18n.StrShell
import java.io.File
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIDevice
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIUserInterfaceIdiomPad

/*
 *  چاپِ سیستمِ آیفون (AirPrint) پشتِ `PrintManager.print` ِ اندروید (‎android/print/Print.kt‎) — گامِ ۴.
 *
 *  همان راهِ پنجرهٔ چاپِ اندروید: سازگارکنندهٔ کدِ مشترک (`ReportPrint`، `LabelSheetPrint`) یک بار
 *  `onLayout` و `onWrite` می‌شود و PDF در `cache/print` می‌نشیند؛ بعد `UIPrintInteractionController` همان
 *  PDF را نشان می‌دهد (چاپگر، شمار، صفحه‌ها؛ «اشتراک» ِ همان پنجره ⇐ ذخیره در Files). دستگاهی که چاپ
 *  ندارد ⇐ برگهٔ اشتراکِ همان PDF. خرابیِ ساختن ⇐ پیامِ «چاپ نشد: …» (همان `StrPick.printFailed`)، نه سکوت.
 */
internal object IosPrint {

  /** true یعنی PDF ساخته شد و پنجرهٔ چاپ (یا اشتراک) باز می‌شود */
  fun print(job: String, adapter: PrintDocumentAdapter, attrs: PrintAttributes): Boolean {
    val dir = File(IosContext.cacheDir, "print").apply { mkdirs(); listFiles()?.forEach { it.deleteRecursively() } }
    var laid: PrintDocumentInfo? = null
    var failure: String? = null
    var written = false
    adapter.onStart()
    try {
      adapter.onLayout(null, attrs, null, object : PrintDocumentAdapter.LayoutResultCallback() {
        override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) { laid = info }
        override fun onLayoutFailed(error: CharSequence?) { failure = error?.toString().orEmpty() }
      }, null)
      val file = File(dir, fileName(laid?.name ?: job))
      if (laid != null) {
        adapter.onWrite(arrayOf(PageRange.ALL_PAGES), ParcelFileDescriptor(file), null, object : PrintDocumentAdapter.WriteResultCallback() {
          override fun onWriteFinished(pages: Array<out PageRange>) { written = true }
          override fun onWriteFailed(error: CharSequence?) { failure = error?.toString().orEmpty() }
        })
      }
      if (written && file.exists() && file.length() > 0) {
        present(file, job, attrs.colorMode == PrintAttributes.COLOR_MODE_MONOCHROME)
        return true
      }
    } catch (e: Throwable) {
      failure = e.message.orEmpty()
    } finally {
      adapter.onFinish()
    }
    //  لغوِ بی پیام (onLayoutCancelled) ⇐ هیچ؛ خرابی ⇐ پیام
    failure?.let { alert(StrPick.printFailed(it.ifBlank { "—" })) }
    return false
  }

  /** نامِ پرونده‌ای که در «اشتراک» ِ پنجرهٔ چاپ دیده می‌شود */
  private fun fileName(name: String): String {
    val base = name.removeSuffix(".pdf").replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "print" }
    return "$base.pdf"
  }

  private fun present(file: File, job: String, gray: Boolean) = IosUi.onMain {
    val url = NSURL.fileURLWithPath(file.path)
    if (!UIPrintInteractionController.printingAvailable || !UIPrintInteractionController.canPrintURL(url)) {
      IosUi.present(UIActivityViewController(activityItems = listOf(url), applicationActivities = null))
      return@onMain
    }
    val pc = UIPrintInteractionController.sharedPrintController
    pc.printInfo = UIPrintInfo.printInfo().apply {
      jobName = job
      outputType = if (gray) UIPrintInfoOutputType.UIPrintInfoOutputGrayscale else UIPrintInfoOutputType.UIPrintInfoOutputGeneral
    }
    pc.printingItem = url
    val host = IosUi.top()
    if (host != null && UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
      val (w, h) = host.view.bounds.useContents { size.width to size.height }
      pc.presentFromRect(CGRectMake(w / 2, h / 2, 1.0, 1.0), inView = host.view, animated = true, completionHandler = null)
    } else {
      pc.presentAnimated(true, completionHandler = null)
    }
  }

  private fun alert(message: String) = IosUi.onMain {
    val a = UIAlertController.alertControllerWithTitle(title = null, message = message, preferredStyle = UIAlertControllerStyleAlert)
    a.addAction(UIAlertAction.actionWithTitle(StrShell.crashOk, style = UIAlertActionStyleDefault, handler = null))
    IosUi.present(a)
  }
}
