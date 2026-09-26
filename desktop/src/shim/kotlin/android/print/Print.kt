package android.print

import android.app.DesktopNotify
import java.io.File

class PrintDocumentAdapter internal constructor(val name: String, val html: String)

class PrintAttributes private constructor() {
  class Builder { fun build() = PrintAttributes() }
}

/**
 *  چاپِ گزارش روی کامپیوتر: صفحهٔ HTML در مرورگرِ پیش‌فرض باز می‌شود و
 *  خودش پنجرهٔ چاپ را می‌آورد — با فارسی و جدولِ درست، و «ذخیره به PDF».
 */
class PrintManager {
  fun print(name: String, adapter: PrintDocumentAdapter, attrs: PrintAttributes?) {
    val dir = File(System.getProperty("java.io.tmpdir"), "tohid-print").apply { mkdirs() }
    val safe = name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "-").take(60).ifBlank { "report" }
    val file = File(dir, "$safe-${System.currentTimeMillis()}.html")
    val html = adapter.html.replace("</body>", "<script>window.onload=function(){setTimeout(function(){window.print()},300)}</script></body>")
    file.writeText(html, Charsets.UTF_8)
    val ok = runCatching { java.awt.Desktop.getDesktop().browse(file.toURI()); true }.getOrElse {
      runCatching { ProcessBuilder("xdg-open", file.absolutePath).start(); true }.getOrDefault(false)
    }
    if (!ok) DesktopNotify.show("چاپ", "مرورگر باز نشد — فایل: ${file.absolutePath}")
  }
}
