package ir.vil3ntec.tohid.data

import android.content.Context
import java.io.File

/**
 *  فایلِ نگه‌داشته‌ها روی دستگاه (POS-12) — کنارِ `sale-draft.json`ِ `CartStore`.
 *  نوشتن با `DiskWrite`: فایلِ کناری + `fsync` + جابه‌جاییِ اتمی.
 */
class FileHeldDisk(private val file: File) : HeldDisk {
  override fun read(): String? = if (file.isFile) file.readText(Charsets.UTF_8) else null
  override fun write(text: String) = DiskWrite.text(file, text)
  /** فایلِ خراب پاک نمی‌شود؛ با نامِ دیگری می‌ماند تا بشود دستی بازش کرد */
  override fun quarantine() {
    if (file.isFile) file.renameTo(File(file.parentFile, "${file.name}.bad-${System.currentTimeMillis()}"))
  }
}

/** یک دفترِ نگه‌داشته برای کلِ برنامه — گوشی و کامپیوتر همان `CartStore`ِ یگانه را دارند */
object HeldInvoices {
  @Volatile private var store: HeldInvoiceStore? = null

  /** ساختن چیزی نمی‌خواند؛ فایل و سریِ دستگاه با نخستین کار روی `Dispatchers.IO` خوانده می‌شوند */
  fun of(context: Context): HeldInvoiceStore = store ?: synchronized(this) {
    val app = context.applicationContext ?: context
    store ?: HeldInvoiceStore(
      FileHeldDisk(File(app.filesDir, HeldInvoiceStore.FILE)),
      device = { ir.vil3ntec.tohid.sync.DocSeries.current(app) },
    ).also { store = it }
  }
}
