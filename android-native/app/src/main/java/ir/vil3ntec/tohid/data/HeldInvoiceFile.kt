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

  /** OPS-07: فاکتورهای نگه‌داشتهٔ حالتِ آموزشی — پروندهٔ جدا در `files/training`، بی سریِ سندِ دستگاه */
  @Volatile private var training: HeldInvoiceStore? = null

  /** ساختن چیزی نمی‌خواند؛ فایل و سریِ دستگاه با نخستین کار روی `Dispatchers.IO` خوانده می‌شوند */
  fun of(context: Context): HeldInvoiceStore {
    val inTraining = TrainingMode.on
    (if (inTraining) training else store)?.let { return it }
    return synchronized(this) {
      val app = context.applicationContext ?: context
      if (inTraining) training ?: HeldInvoiceStore(FileHeldDisk(File(TrainingLedger.filesDir(app), HeldInvoiceStore.FILE)), device = { "" })
        .also { training = it }
      else store ?: HeldInvoiceStore(
        FileHeldDisk(File(app.filesDir, HeldInvoiceStore.FILE)),
        device = { ir.vil3ntec.tohid.sync.DocSeries.current(app) },
      //  S09: پشتیبانِ سرور (پرچمِ خاموشِ `shop.held_sync`) — فقط دفترِ واقعی، هرگز آموزشی
      ).also { HeldSync.attach(app, it); store = it }
    }
  }

  /** خروج از آموزش (`TrainingLedger.exit`): ورودِ بعدی پرونده را از نو می‌خواند (شاید پاک شده باشد) */
  fun forgetTraining() { training = null }

  /** آزمون (`BackupRestoreDrillTest`): «برنامه از نو باز شد» — نمونهٔ بعدی پرونده را از نو می‌خواند */
  internal fun reset() { store = null; training = null }
}
