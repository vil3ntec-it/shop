package ir.vil3ntec.tohid.data

import android.content.Context
import java.io.File

/**
 *  POS-25 — کلیدِ «پیش‌فاکتور» روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `quotes`). پیش‌فرض **خاموش**: نه
 *  دکمه‌ای در صندوق و نه پرونده‌ای — صندوق همان امروز.
 */
object QuotePref {
  private const val KEY = "quotes"
  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)
  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)
  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}

/**
 *  پروندهٔ پیش‌فاکتورهای همین دستگاه (`quotes.json` در `filesDir`، کنارِ نگه‌داشته‌ها). نوشتن با `DiskWrite`
 *  (پروندهٔ کناری، `fsync`، جابه‌جاییِ اتمی)؛ پروندهٔ خراب پاک نمی‌شود، با نامِ دیگری کنار می‌رود. هر کار زیرِ
 *  یک قفل: خواندن ⇐ تبدیل ⇐ نوشتن. فقط بیرون از رشتهٔ رابط (`Dispatchers.IO`).
 */
object QuoteStore {
  const val FILE = "quotes.json"

  private fun file(context: Context) = File((context.applicationContext ?: context).filesDir, FILE)

  fun read(context: Context): QuoteBook = synchronized(this) { readLocked(file(context)) }

  private fun readLocked(f: File): QuoteBook {
    if (!f.isFile) return QuoteBook()
    val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
    return QuoteBookCodec.decode(raw) ?: run {
      runCatching { f.renameTo(File(f.parentFile, "${f.name}.bad-${System.currentTimeMillis()}")) }
      QuoteBook()
    }
  }

  /** [change] روی دفترِ همین لحظهٔ پرونده؛ `null` ⇒ هیچ نوشتنی. برمی‌گرداند دفترِ نوشته‌شده، یا `null` (رد یا خطای دیسک) */
  fun update(context: Context, change: (QuoteBook) -> QuoteBook?): QuoteBook? = synchronized(this) {
    val f = file(context)
    val next = change(readLocked(f)) ?: return null
    if (runCatching { DiskWrite.text(f, QuoteBookCodec.encode(next)) }.isSuccess) next else null
  }
}
