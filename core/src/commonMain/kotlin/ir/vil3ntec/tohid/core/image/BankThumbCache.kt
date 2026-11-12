package ir.vil3ntec.tohid.core.image

/*
 *  کشِ حافظهٔ عکس‌های کوچکِ «انتخاب از بانک» — IMG-PRO-13 (v3 ۳۶.۱۲)، IMG-11.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • کلید = نشانیِ نسخه‌دارِ سرور (`…/file/<id>/<version>/thumb`، `image-bank.fileUrl`) + اندازهٔ پیکسلیِ کادر. عکسِ تأییدشده که عوض شود
 *    سرور نشانیِ تازه می‌دهد ⇒ کلیدِ تازه ⇒ هرگز عکسِ کهنه از کش نشان داده نمی‌شود؛ نشانیِ کهنه هم با LRU بیرون می‌رود.
 *  • کران‌دار: حداکثر [max] عکس (LRU، کم‌کاربردترین اول بیرون)، نه نقشهٔ بی‌سقفِ هر برگه.
 *  • نشانیِ تهی هیچ‌وقت کلید نمی‌سازد (کالای بی عکس ⇒ جانگه‌دار)؛ اندازهٔ صفر یا منفی ⇒ ۱ پیکسل.
 *
 *  منطقِ خالص، بی android.*: برنامه (`ImageBankPicker.BankThumbs`) با قفلِ خودش آن را می‌گیرد. آزمون: `BankThumbCacheTest`.
 */
class BankThumbCache<T : Any>(val max: Int = 48) {

  init { require(max > 0) { "max > 0" } }

  //  ترتیبِ دسترسی (قدیمی‌ترین اول)؛ LRU با برداشتن و دوباره گذاشتن
  private val map = LinkedHashMap<String, T>()

  val size: Int get() = map.size

  fun get(key: String): T? {
    val v = map.remove(key) ?: return null
    map[key] = v
    return v
  }

  fun put(key: String, value: T) {
    map.remove(key)
    map[key] = value
    while (map.size > max) map.remove(map.keys.first())
  }

  companion object {
    /** کلیدِ کش؛ `null` برای نشانیِ تهی (کالای بی عکس ⇒ جانگه‌دار، نه کش) */
    fun key(url: String?, px: Int): String? {
      val u = url?.trim().orEmpty()
      if (u.isEmpty()) return null
      return "$u@${px.coerceAtLeast(1)}"
    }
  }
}
