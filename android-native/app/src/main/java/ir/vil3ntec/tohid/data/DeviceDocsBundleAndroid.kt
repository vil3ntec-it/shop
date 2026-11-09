package ir.vil3ntec.tohid.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 *  DATA-SAFETY §۸ — پیش‌فاکتورهای همین دستگاه (`quotes.json`، POS-25) در هر پشتیبانِ کامل (دستی، پوشه، ابریِ دوازده‌ساعته).
 *
 *  ── اشکال ────────────────────────────────────────────────────────────
 *  پیش‌فاکتور (قیمتی که به مشتری داده شد، با روزِ آخرِ اعتبار) عمداً نه در دفتر است نه در همگام‌سازی (سندِ مالی
 *  نیست، `QuoteBook`) — ولی در پشتیبان هم نبود: با پاک کردن یا نصبِ دوبارهٔ برنامه همه می‌رفت و دکان‌دار باید از نو
 *  می‌نوشت. حالا `BackupBundle.write` آن را در `device/quotes.json` ِ ZIP می‌گذارد، پس با پشتیبانِ ابری به سرور
 *  می‌رسد و `CloudSettingsRestore` پس از ورود روی گوشیِ تازه برش می‌گرداند.
 *
 *  ⛔ بازیابی فقط وقتی این دستگاه **هیچ** پیش‌فاکتوری ندارد (`QuoteStore.update`، زیرِ همان قفل و با `DiskWrite`):
 *     پیش‌فاکتورِ موجود هرگز رونویسی یا با شماره‌های پشتیبان قاطی نمی‌شود. پروندهٔ ناخوانا هیچ اثری ندارد.
 *  ⛔ فاکتورِ نگه‌داشته (`held-invoices.json`) عمداً نه: سبدِ نیمه‌کارهٔ همان روز است (حداکثر ۹، کهنه پس از ۲۴ ساعت)
 *     و `HeldInvoices` آن را یک بار در حافظه می‌خواند — نشاندنِ پرونده زیرِ پایش با نخستین نگه‌داشتنِ بعدی پاک می‌شد.
 */
object DeviceDocsBundle {

  const val QUOTES_ENTRY = "device/quotes.json"

  /** بیش از این، پروندهٔ پیش‌فاکتور نیست */
  const val MAX_BYTES = 2 * 1024 * 1024

  private fun staged(context: Context): File = File(context.cacheDir, "restore-quotes.json")

  /** `BackupBundle.write`: پیش‌فاکتورهای خوانا و ناتهی ⇒ متنِ همان پرونده؛ هیچ ⇒ null */
  fun quotes(context: Context): String? {
    val book = runCatching { QuoteStore.read(context) }.getOrNull() ?: return null
    return if (book.items.isEmpty()) null else QuoteBookCodec.encode(book)
  }

  /** `BackupBundle.read`: تا تأییدِ بازیابی کنار می‌ماند (همان قاعدهٔ عکس‌ها و تنظیمات) */
  fun stageFrom(context: Context, input: InputStream) {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (out.size() <= MAX_BYTES) {
      val n = input.read(buf, 0, buf.size)
      if (n <= 0) break
      out.write(buf, 0, n)
    }
    if (out.size() > MAX_BYTES) return
    runCatching { staged(context).writeText(out.toString(Charsets.UTF_8.name())) }
  }

  fun dropStaged(context: Context) { runCatching { staged(context).delete() } }

  /** پس از تأییدِ بازیابی (`BackupBundle.commitPhotos`) — @return چند پیش‌فاکتور نشست */
  fun commit(context: Context): Int {
    val f = staged(context)
    if (!f.isFile) return 0
    val book = runCatching { QuoteBookCodec.decode(f.readText()) }.getOrNull()
    f.delete()
    if (book == null || book.items.isEmpty()) return 0
    val wrote = QuoteStore.update(context) { mine -> if (mine.items.isEmpty()) book else null }
    return wrote?.items?.size ?: 0
  }
}
