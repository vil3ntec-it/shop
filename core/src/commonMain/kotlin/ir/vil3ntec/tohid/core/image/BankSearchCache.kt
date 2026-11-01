package ir.vil3ntec.tohid.core.image

/*
 *  کشِ نتیجه‌های «انتخاب از بانکِ تصاویر» برای بی‌اینترنتی — v3 ۳۷.۹؛ IMG-BANK-11 و ۱۵.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • با اینترنت همیشه از سرور پرسیده می‌شود؛ کش فقط جای «بی اینترنت» است و هرگز
 *    به‌جای پاسخِ زنده نمی‌نشیند.
 *  • پاسخِ کش همیشه `Stale` است (با زمانِ ذخیره و سن) تا رابط آن را «قدیمی، آخرین
 *    همگام‌سازی …» نشان دهد و ادعای جست‌وجوی زنده نکند (IMG-BANK-11).
 *  • باطل‌سازی: سرور در هر پاسخ `bank_rev` می‌دهد و هر تأیید، بایگانی یا نسخهٔ تازه آن را
 *    عوض می‌کند. دیدنِ نسخهٔ دیگر ⇒ هر صفحهٔ کش با نسخهٔ دیگر دور ریخته می‌شود (IMG-BANK-15).
 *  • کران‌دار: حداکثر `maxEntries` صفحه و `maxChars` نویسه (LRU)، و صفحهٔ کهنه‌تر از
 *    `maxAgeMs` اصلاً نشان داده نمی‌شود. ساعتِ رو به عقب سن را منفی نمی‌کند.
 *  • فقط متادیتای بانکِ مرکزی (همان برای همهٔ دکان‌ها)؛ هیچ قیمت/موجودی/دادهٔ دکانی این‌جا نیست.
 *
 *  منطقِ خالص، بی JSON و بی android.*: `payload` همان متنِ پاسخِ سرور است و برنامه
 *  نگه‌داری روی دیسک را با `entries()`/`restore()` انجام می‌دهد. آزمون: `BankSearchCacheTest`.
 */
class BankSearchCache(
  val maxEntries: Int = 40,
  val maxChars: Int = 600_000,
  val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000,
) {

  data class Entry(val key: String, val payload: String, val savedAtMs: Long, val bankRev: Long)

  /** نتیجهٔ قدیمی برای بی‌اینترنتی — هرگز «زنده» نیست */
  data class Stale(val payload: String, val savedAtMs: Long, val ageMs: Long, val bankRev: Long) {
    val live: Boolean get() = false
  }

  //  ترتیبِ درج = ترتیبِ استفاده (قدیمی‌ترین اول)؛ LRU با برداشتن و دوباره گذاشتن
  private val map = LinkedHashMap<String, Entry>()
  private var chars = 0L

  /** آخرین `bank_rev` ِ دیده‌شده از سرور؛ -1 = هنوز هیچ */
  var bankRev: Long = -1L
    private set

  val size: Int get() = map.size

  /** همان پرس‌وجو و فیلتر و صفحه ⇒ همان کلید (فاصله‌های اضافه بی‌اثر) */
  fun keyOf(query: String, hasImage: Boolean, cursor: String?): String =
    buildString {
      append(query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" "))
      append('\u0001').append(if (hasImage) '1' else '0')
      append('\u0001').append(cursor.orEmpty())
    }

  /** پاسخِ زندهٔ سرور ⇒ نسخهٔ بانک دیده شد و این صفحه ذخیره می‌شود */
  fun put(key: String, payload: String, nowMs: Long, bankRev: Long) {
    observe(bankRev)
    remove(key)
    if (payload.length > maxChars) return               //  یک صفحهٔ بیش از سقف اصلاً نمی‌ماند
    map[key] = Entry(key, payload, nowMs, bankRev)
    chars += payload.length
    trim()
  }

  /** نسخهٔ بانک عوض شد ⇒ هر صفحه با نسخهٔ دیگر باطل است (بالا یا پایین — بازگردانیِ دیتابیس هم) */
  fun observe(rev: Long) {
    if (rev < 0 || rev == bankRev) return
    bankRev = rev
    val it = map.values.iterator()
    while (it.hasNext()) {
      val e = it.next()
      if (e.bankRev != rev) { chars -= e.payload.length; it.remove() }
    }
  }

  /** بی اینترنت: صفحهٔ ذخیره‌شده، با سن؛ کهنه‌تر از `maxAgeMs` ⇒ null و پاک */
  fun offline(key: String, nowMs: Long): Stale? {
    val e = map[key] ?: return null
    val age = (nowMs - e.savedAtMs).coerceAtLeast(0)
    if (age > maxAgeMs) { remove(key); return null }
    map.remove(key); map[key] = e                      //  استفاده ⇒ تازه‌ترین
    return Stale(e.payload, e.savedAtMs, age, e.bankRev)
  }

  /** همه (قدیمی‌ترین اول) — برای نوشتن روی دیسک */
  fun entries(): List<Entry> = map.values.toList()

  /**
   *  خواندن از دیسک: کهنه‌ها کنار؛ نسخهٔ بانک همان تازه‌ترین صفحه است و صفحه‌های نسخهٔ
   *  دیگر کنار (هر `put` نسخه را می‌سنجد، پس در کشِ سالم همه یک نسخه‌اند)؛ باز همان سقف‌ها.
   */
  fun restore(saved: List<Entry>, nowMs: Long) {
    map.clear(); chars = 0; bankRev = -1
    val ordered = saved.sortedBy { it.savedAtMs }
    bankRev = ordered.lastOrNull()?.bankRev ?: -1L
    for (e in ordered) {
      if (nowMs - e.savedAtMs > maxAgeMs) continue
      if (e.bankRev != bankRev) continue
      if (e.payload.length > maxChars) continue
      map.remove(e.key)?.let { chars -= it.payload.length }
      map[e.key] = e
      chars += e.payload.length
    }
    trim()
  }

  fun clear() { map.clear(); chars = 0 }

  private fun remove(key: String) {
    map.remove(key)?.let { chars -= it.payload.length }
  }

  private fun trim() {
    val it = map.values.iterator()
    while ((map.size > maxEntries || chars > maxChars) && it.hasNext()) {
      chars -= it.next().payload.length
      it.remove()
    }
  }
}
